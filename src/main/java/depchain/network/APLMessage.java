package depchain.network;

import java.nio.ByteBuffer;

/**
 * Wire format for messages exchanged over authenticated perfect links.
 *
 * Layout:
 * [srcId (4 bytes)][seq (8 bytes)][payload length (4)][payload][signature length (4)][signature]
 */
public class APLMessage {
    /** sender identifier */
    public final int srcId;
    /** sequence number assigned by sender; used for deduplication */
    public final long seq;
    /** application payload */
    public final byte[] payload;
    /** signature over (seq, payload) */
    public final byte[] signature;

    public APLMessage(int srcId, long seq, byte[] payload, byte[] signature) {
        this.srcId = srcId;
        this.seq = seq;
        this.payload = payload;
        this.signature = signature;
    }

    public byte[] marshall() {
        int len = 4 + 8 + 4 + payload.length + 4 + signature.length;
        ByteBuffer buf = ByteBuffer.allocate(len);
        buf.putInt(srcId);
        buf.putLong(seq);
        buf.putInt(payload.length);
        buf.put(payload);
        buf.putInt(signature.length);
        buf.put(signature);
        return buf.array();
    }

    public static APLMessage unmarshall(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int src = buf.getInt();
        long seq = buf.getLong();
        int plen = buf.getInt();
        byte[] payload = new byte[plen];
        buf.get(payload);
        int slen = buf.getInt();
        byte[] sig = new byte[slen];
        buf.get(sig);
        return new APLMessage(src, seq, payload, sig);
    }

    @Override
    public String toString() {
        String msg = new String(payload);
        return "APLMessage[src=" + srcId + ", seq=" + seq + ", payload=" + msg + "]";
    }
}
