package depchain.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * AppendResponse
 */
public class AppendResponse {
    static final byte TYPE = 0x02;

    private final long requestId;
    private final boolean committed;

    public AppendResponse(long requestId, boolean committed) {
        this.requestId = requestId;
        this.committed = committed;
    }

    public long getRequestId() {
        return requestId;
    }

    public boolean isCommitted() {
        return committed;
    }

    public static byte[] encode(long requestId, boolean committed) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 + 8 + 1);
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(TYPE);
            dos.writeLong(requestId);
            dos.writeBoolean(committed);
            dos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode append response", e);
        }
    }

    public static AppendResponse tryDecode(byte[] payload) {
        if (payload == null || payload.length < 1) {
            return null;
        }
        if (payload[0] != TYPE) {
            return null;
        }
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            dis.readByte();
            long requestId = dis.readLong();
            boolean committed = dis.readBoolean();
            return new AppendResponse(requestId, committed);
        } catch (Exception e) {
            return null;
        }
    }
}
