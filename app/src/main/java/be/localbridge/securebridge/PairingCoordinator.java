package be.localbridge.securebridge;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class PairingCoordinator {
    interface Listener { void onRemotePayload(byte[] payload); }
    private static byte[] hostPayload;
    private static byte[] pendingRemote;
    private static Listener listener;
    private static ByteArrayOutputStream readerBuffer = new ByteArrayOutputStream();

    private PairingCoordinator() {}

    static synchronized void startHost(byte[] payload, Listener l) {
        hostPayload = payload == null ? null : Arrays.copyOf(payload, payload.length);
        pendingRemote = null;
        listener = l;
        readerBuffer = new ByteArrayOutputStream();
    }

    static synchronized void beginTransaction() {
        readerBuffer = new ByteArrayOutputStream();
        pendingRemote = null;
    }

    static synchronized void appendReaderChunk(byte[] chunk, boolean more) {
        try { if (chunk != null && chunk.length > 0) readerBuffer.write(chunk); } catch (Exception ignored) {}
        if (!more) {
            pendingRemote = readerBuffer.toByteArray();
            readerBuffer = new ByteArrayOutputStream();
        }
    }

    static synchronized byte[] hostPayload() {
        return hostPayload == null ? null : Arrays.copyOf(hostPayload, hostPayload.length);
    }

    static synchronized void hostPayloadDelivered() {
        Listener l = listener;
        byte[] remote = pendingRemote;
        pendingRemote = null;
        if (l != null && remote != null && remote.length > 0) l.onRemotePayload(Arrays.copyOf(remote, remote.length));
    }

    static synchronized void clear() {
        if (hostPayload != null) Arrays.fill(hostPayload, (byte)0);
        if (pendingRemote != null) Arrays.fill(pendingRemote, (byte)0);
        hostPayload = null;
        pendingRemote = null;
        listener = null;
        readerBuffer = new ByteArrayOutputStream();
    }
}
