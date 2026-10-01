package be.localbridge.securebridge;

import android.util.Base64;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

final class CryptoSuite {
    private static final SecureRandom RNG = new SecureRandom();
    private CryptoSuite() {}

    private static String b64(byte[] in) { return Base64.encodeToString(in, Base64.NO_WRAP); }
    private static byte[] unb64(String in) { return Base64.decode(in, Base64.NO_WRAP); }

    static String argon2id(String value, String saltB64, int memoryKb, int iterations, int parallelism, int outLen) throws Exception {
        if (value == null) value = "";
        memoryKb = Math.max(8192, Math.min(memoryKb, 131072));
        iterations = Math.max(1, Math.min(iterations, 10));
        parallelism = Math.max(1, Math.min(parallelism, 4));
        outLen = Math.max(16, Math.min(outLen, 64));
        byte[] salt = unb64(saltB64);
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(params);
        byte[] input = value.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[outLen];
        gen.generateBytes(input, out);
        Arrays.fill(input, (byte)0);
        return b64(out);
    }

    static String x25519KeyPair() throws Exception {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(RNG);
        X25519PublicKeyParameters pub = priv.generatePublicKey();
        JSONObject j = new JSONObject();
        j.put("private", b64(priv.getEncoded()));
        j.put("public", b64(pub.getEncoded()));
        return j.toString();
    }

    static String x25519Shared(String privateB64, String publicB64) throws Exception {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(unb64(privateB64), 0);
        X25519PublicKeyParameters pub = new X25519PublicKeyParameters(unb64(publicB64), 0);
        byte[] out = new byte[32];
        priv.generateSecret(pub, out, 0);
        return b64(out);
    }

    static String ed25519KeyPair() throws Exception {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(RNG);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        JSONObject j = new JSONObject();
        j.put("private", b64(priv.getEncoded()));
        j.put("public", b64(pub.getEncoded()));
        return j.toString();
    }

    static String ed25519Sign(String privateB64, String messageB64) {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(unb64(privateB64), 0);
        byte[] msg = unb64(messageB64);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, priv);
        signer.update(msg, 0, msg.length);
        return b64(signer.generateSignature());
    }

    static boolean ed25519Verify(String publicB64, String messageB64, String signatureB64) {
        try {
            Ed25519PublicKeyParameters pub = new Ed25519PublicKeyParameters(unb64(publicB64), 0);
            byte[] msg = unb64(messageB64);
            byte[] sig = unb64(signatureB64);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(false, pub);
            signer.update(msg, 0, msg.length);
            return signer.verifySignature(sig);
        } catch (Exception e) {
            return false;
        }
    }
}
