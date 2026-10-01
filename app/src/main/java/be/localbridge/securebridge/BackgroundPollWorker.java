package be.localbridge.securebridge;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BackgroundPollWorker extends Worker {
    // Requête de taille fixe : jusqu'à 32 contacts x 4 slots, complétée par de faux slots.
    // Le serveur voit toujours exactement 128 candidats par réveil du Worker.
    private static final int LOOKAHEAD = 4;
    private static final int MAX_CHANNELS_PER_RUN = 32;
    private static final int PAD_SLOTS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    public BackgroundPollWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        try {
            SecureStore store = new SecureStore(getApplicationContext());
            JSONObject state = new JSONObject(store.loadWakeJson());
            String apiUrl = state.optString("apiUrl", "");
            JSONArray channels = state.optJSONArray("channels");
            if (!apiUrl.startsWith("https://") || channels == null || channels.length() == 0) return Result.success();

            int count = channels.length();
            int cursor = Math.floorMod(state.optInt("cursor", 0), count);
            int checked = Math.min(count, MAX_CHANNELS_PER_RUN);
            List<String> slots = new ArrayList<>(PAD_SLOTS);

            for (int n=0;n<checked;n++) {
                int index = (cursor + n) % count;
                JSONObject c = channels.optJSONObject(index); if (c == null) continue;
                byte[] chain;
                try { chain = Base64.decode(c.optString("chain", ""), Base64.NO_WRAP); } catch (Exception e) { continue; }
                if (chain.length != 32) continue;
                byte[] k = chain.clone();
                for (int i=0;i<LOOKAHEAD;i++) {
                    slots.add(hex(hmac(k, "SLOT".getBytes(StandardCharsets.UTF_8))));
                    byte[] next = hmac(k, "NEXT".getBytes(StandardCharsets.UTF_8));
                    java.util.Arrays.fill(k, (byte)0);
                    k = next;
                }
                java.util.Arrays.fill(chain, (byte)0);
                java.util.Arrays.fill(k, (byte)0);
            }

            while (slots.size() < PAD_SLOTS) slots.add(randomSlot());
            if (slots.size() > PAD_SLOTS) slots = new ArrayList<>(slots.subList(0, PAD_SLOTS));
            Collections.shuffle(slots, RNG);

            int idx = peek(apiUrl, slots);
            boolean changed = false;
            if (count > MAX_CHANNELS_PER_RUN) {
                state.put("cursor", (cursor + checked) % count);
                changed = true;
            } else if (cursor != 0) {
                state.put("cursor", 0);
                changed = true;
            }

            if (idx >= 0 && idx < slots.size()) {
                String found = slots.get(idx);
                String previous = state.optString("lastNotifiedSlot", "");
                if (!found.equals(previous)) {
                    state.put("lastNotifiedSlot", found);
                    changed = true;
                    LocalNotifier.show(getApplicationContext());
                }
            }
            if (changed) store.saveWakeJson(state.toString());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private int peek(String apiUrl, List<String> slots) throws Exception {
        JSONObject req = new JSONObject(); req.put("action", "peek_many");
        JSONArray a = new JSONArray(); for (String s : slots) a.put(s); req.put("slots", a);
        HttpURLConnection con = (HttpURLConnection) new URL(apiUrl).openConnection();
        con.setRequestMethod("POST"); con.setConnectTimeout(7000); con.setReadTimeout(10000);
        con.setDoOutput(true); con.setUseCaches(false);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Cache-Control", "no-store");
        byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = con.getOutputStream()) { out.write(body); }
        int status = con.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) sb.append(line);
        } finally { con.disconnect(); }
        JSONObject resp = new JSONObject(sb.toString());
        if (!resp.optBoolean("ok", false)) return -1;
        return resp.optInt("index", -1);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String randomSlot() {
        byte[] b = new byte[32]; RNG.nextBytes(b); return hex(b);
    }

    private static String hex(byte[] in) {
        StringBuilder sb = new StringBuilder(in.length * 2);
        for (byte b : in) sb.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }
}
