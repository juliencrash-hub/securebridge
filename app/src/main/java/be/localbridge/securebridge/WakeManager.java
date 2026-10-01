package be.localbridge.securebridge;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.TimeUnit;

final class WakeManager {
    static final String UNIQUE_WORK = "securebridge-private-poll-v1";
    private WakeManager() {}

    static synchronized void configure(Context context, String apiUrl, String channelsJson) throws Exception {
        URI uri = new URI(apiUrl == null ? "" : apiUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException("HTTPS required");
        JSONArray incoming = new JSONArray(channelsJson == null ? "[]" : channelsJson);
        if (incoming.length() > 256) throw new IllegalArgumentException("too many channels");

        SecureStore store = new SecureStore(context);
        int priorCursor = 0;
        String priorNotice = "";
        try {
            JSONObject old = new JSONObject(store.loadWakeJson());
            priorCursor = Math.max(0, old.optInt("cursor", 0));
            priorNotice = old.optString("lastNotifiedSlot", "");
        } catch (Exception ignored) {}

        JSONArray sanitized = new JSONArray();
        for (int i=0;i<incoming.length();i++) {
            JSONObject c = incoming.optJSONObject(i); if (c == null) continue;
            String id = c.optString("id", "");
            String chain = c.optString("chain", "");
            long counter = c.optLong("counter", 0L);
            if (id.length() < 8 || id.length() > 128 || chain.length() < 20 || chain.length() > 256 || counter < 0) continue;
            JSONObject x = new JSONObject();
            x.put("id", id); x.put("chain", chain); x.put("counter", counter);
            sanitized.put(x);
        }

        JSONObject state = new JSONObject();
        state.put("v", 2);
        state.put("apiUrl", apiUrl);
        state.put("channels", sanitized);
        state.put("cursor", sanitized.length() == 0 ? 0 : priorCursor % sanitized.length());
        if (!priorNotice.isEmpty()) state.put("lastNotifiedSlot", priorNotice);
        store.saveWakeJson(state.toString());

        WorkManager wm = WorkManager.getInstance(context);
        if (sanitized.length() == 0) {
            wm.cancelUniqueWork(UNIQUE_WORK);
            return;
        }
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(BackgroundPollWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        wm.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    static synchronized void disable(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK);
        try { new SecureStore(context).clearWake(); } catch (Exception ignored) {}
    }
}
