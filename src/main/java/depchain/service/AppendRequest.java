package depchain.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * AppendRequest
 */
public class AppendRequest {
    static final byte TYPE = 0x01;

    private final long requestId;
    private final int replyTo;
    private final String data;

    public AppendRequest(long requestId, int replyTo, String data) {
        this.requestId = requestId;
        this.replyTo = replyTo;
        this.data = data;
    }

    public long getRequestId() {
        return requestId;
    }

    /**
     * Optional "reply-to" destination id.
     * When present (>= 0), replicas should send the commit confirmation reply to this id.
     * When absent (-1), replicas may reply to the APL sender id.
     */
    public int getReplyTo() {
        return replyTo;
    }

    public boolean hasReplyTo() {
        return replyTo >= 0;
    }

    public String getData() {
        return data;
    }

    public static byte[] encode(long requestId, int replyTo, String data) {
        if (data == null) {
            data = "";
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 + 8 + 4 + 4 + dataBytes.length);
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(TYPE);
            dos.writeLong(requestId);
            dos.writeInt(replyTo);
            dos.writeInt(dataBytes.length);
            dos.write(dataBytes);
            dos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode append request", e);
        }
    }

    public static AppendRequest tryDecode(byte[] payload) {
        if (payload == null || payload.length < 1) {
            return null;
        }
        if (payload[0] != TYPE) {
            return null;
        }
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte type = dis.readByte();
            long requestId = dis.readLong();
            int replyTo = dis.readInt();
            int len = dis.readInt();
            int header = 1 + 8 + 4 + 4;
            if (type != TYPE || len < 0 || len > (payload.length - header)) {
                return null;
            }
            byte[] data = new byte[len];
            dis.readFully(data);
            return new AppendRequest(requestId, replyTo, new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
