package be.localbridge.securebridge;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStore {
    private static final String KS = "AndroidKeyStore";
    private static final String AES_ALIAS = "sb_wrap_v2";
    private static final String HMAC_ALIAS = "sb_hmac_v2";
    private static final String VAULT_FILE = "vault.bundle";
    private static final String WAKE_FILE = "wake.bundle";
    private static final SecureRandom RNG = new SecureRandom();

    private final Context context;

    SecureStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private File file(String name) { return new File(context.getFilesDir(), name); }

    private KeyStore keyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        return ks;
    }

    private SecretKey ensureAesKey() throws Exception {
        KeyStore ks = keyStore();
        if (ks.containsAlias(AES_ALIAS)) return (SecretKey) ks.getKey(AES_ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                b.setIsStrongBoxBacked(true);
                kg.init(b.build());
                return kg.generateKey();
            } catch (java.security.ProviderException ignored) {
                kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
            }
        }
        kg.init(new KeyGenParameterSpec.Builder(
                AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return kg.generateKey();
    }

    private SecretKey ensureHmacKey() throws Exception {
        KeyStore ks = keyStore();
        if (ks.containsAlias(HMAC_ALIAS)) return (SecretKey) ks.getKey(HMAC_ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KS);
        kg.init(new KeyGenParameterSpec.Builder(
                HMAC_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build());
        return kg.generateKey();
    }

    synchronized String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(ensureHmacKey());
        byte[] out = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    synchronized String loadJson() throws Exception {
        return loadEncrypted(VAULT_FILE, "SECUREBRIDGE-NATIVE-KV-v2", "{}");
    }

    synchronized void saveJson(String json) throws Exception {
        if (json == null) json = "{}";
        new JSONObject(json);
        saveEncrypted(VAULT_FILE, "SECUREBRIDGE-NATIVE-KV-v2", json);
    }

    synchronized String loadWakeJson() throws Exception {
        return loadEncrypted(WAKE_FILE, "SECUREBRIDGE-WAKE-v1", "{\"channels\":[]}");
    }

    synchronized void saveWakeJson(String json) throws Exception {
        if (json == null) json = "{\"channels\":[]}";
        new JSONObject(json);
        saveEncrypted(WAKE_FILE, "SECUREBRIDGE-WAKE-v1", json);
    }

    private String loadEncrypted(String name, String aadText, String fallback) throws Exception {
        File f = file(name);
        if (!f.exists()) return fallback;
        byte[] raw;
        try (FileInputStream in = new FileInputStream(f)) {
            raw = new byte[(int) f.length()];
            int off = 0;
            while (off < raw.length) {
                int n = in.read(raw, off, raw.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        JSONObject envelope = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] ct = Base64.decode(envelope.getString("ct"), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, ensureAesKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aadText.getBytes(StandardCharsets.UTF_8));
        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private void saveEncrypted(String name, String aadText, String json) throws Exception {
        byte[] iv = new byte[12];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, ensureAesKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aadText.getBytes(StandardCharsets.UTF_8));
        byte[] ct = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("v", 2);
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        envelope.put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
        byte[] data = envelope.toString().getBytes(StandardCharsets.UTF_8);
        File dst = file(name);
        File tmp = file(name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
        if (dst.exists() && !dst.delete()) throw new IllegalStateException("old bundle delete failed");
        if (!tmp.renameTo(dst)) throw new IllegalStateException("bundle replace failed");
    }

    synchronized long storedBytes() {
        long n = 0;
        File v = file(VAULT_FILE), w = file(WAKE_FILE);
        if (v.exists()) n += v.length();
        if (w.exists()) n += w.length();
        return n;
    }

    synchronized boolean hasBundle() {
        File f = file(VAULT_FILE);
        return f.exists() && f.length() > 0;
    }

    synchronized void clearBundle() throws Exception { saveJson("{}"); }
    synchronized void clearWake() throws Exception { saveWakeJson("{\"channels\":[]}"); }

    synchronized void destroyAll() throws Exception {
        overwriteAndDelete(file(VAULT_FILE));
        overwriteAndDelete(file(WAKE_FILE));
        //noinspection ResultOfMethodCallIgnored
        file(VAULT_FILE + ".tmp").delete();
        //noinspection ResultOfMethodCallIgnored
        file(WAKE_FILE + ".tmp").delete();
        KeyStore ks = keyStore();
        if (ks.containsAlias(AES_ALIAS)) ks.deleteEntry(AES_ALIAS);
        if (ks.containsAlias(HMAC_ALIAS)) ks.deleteEntry(HMAC_ALIAS);
        if (ks.containsAlias("sb_wrap_v1")) ks.deleteEntry("sb_wrap_v1");
        if (ks.containsAlias("sb_hmac_v1")) ks.deleteEntry("sb_hmac_v1");
    }

    private void overwriteAndDelete(File f) {
        if (!f.exists()) return;
        long len = Math.max(256, f.length());
        byte[] noise = new byte[(int) Math.min(len, 4L * 1024L * 1024L)];
        RNG.nextBytes(noise);
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(noise);
            out.flush();
            out.getFD().sync();
        } catch (Exception ignored) {}
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
