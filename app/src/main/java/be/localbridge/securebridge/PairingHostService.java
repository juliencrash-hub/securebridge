package be.localbridge.securebridge;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import java.util.Arrays;

public class PairingHostService extends HostApduService {
    static final byte[] AID = hex("F053424D53473031"); // proprietary AID: F0 + "SBMSG01"
    private static final byte[] OK = new byte[]{(byte)0x90, 0x00};
    private static final byte[] BAD = new byte[]{0x6F, 0x00};
    private static final int CHUNK = 180;

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 4) return BAD;
        if (isSelect(apdu)) {
            PairingCoordinator.beginTransaction();
            return OK;
        }
        int cla = apdu[0] & 0xff, ins = apdu[1] & 0xff, p1 = apdu[2] & 0xff;
        if (cla != 0x80) return BAD;

        if (ins == 0x10) { // reader -> host payload chunk
            if (apdu.length < 5) return BAD;
            int lc = apdu[4] & 0xff;
            if (5 + lc > apdu.length) return BAD;
            byte[] data = Arrays.copyOfRange(apdu, 5, 5 + lc);
            PairingCoordinator.appendReaderChunk(data, p1 == 1);
            return OK;
        }

        if (ins == 0x20) { // reader asks host payload chunk; p1 = sequence
            byte[] payload = PairingCoordinator.hostPayload();
            if (payload == null) return BAD;
            int start = p1 * CHUNK;
            if (start > payload.length) return BAD;
            int end = Math.min(payload.length, start + CHUNK);
            boolean more = end < payload.length;
            byte[] part = Arrays.copyOfRange(payload, start, end);
            byte[] out = new byte[1 + part.length + 2];
            out[0] = (byte)(more ? 1 : 0);
            System.arraycopy(part, 0, out, 1, part.length);
            out[out.length - 2] = (byte)0x90;
            out[out.length - 1] = 0x00;
            if (!more) PairingCoordinator.hostPayloadDelivered();
            return out;
        }
        return BAD;
    }

    @Override public void onDeactivated(int reason) { }

    private boolean isSelect(byte[] apdu) {
        if (apdu.length < 5 || (apdu[0]&0xff)!=0x00 || (apdu[1]&0xff)!=0xA4) return false;
        int lc = apdu[4] & 0xff;
        if (lc != AID.length || apdu.length < 5 + lc) return false;
        for (int i=0;i<lc;i++) if (apdu[5+i] != AID[i]) return false;
        return true;
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length()/2];
        for (int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }
}
