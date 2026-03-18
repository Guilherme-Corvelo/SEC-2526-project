package depchain.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * AppendRequest
 */
public class ServiceMessage implements Serializable{
    private final int requestId;
    private String data = null;
    private boolean commited=false;

    public ServiceMessage(int requestId, String data) {
        this.requestId = requestId;
        this.data = data;
    }
    public ServiceMessage(int requestId, boolean commited) {
        this.requestId = requestId;
        this.commited = commited;
    }

    public int getRequestId() {
        return requestId;
    }

    public String getData() {
        return data;
    }

    public boolean getCommited(){
        return commited;
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            return bos.toByteArray();
        } catch (Exception e) {
            System.err.println("Failed to encode message");
        }
        return null;
    }

    public static ServiceMessage tryDecode(byte[] payload) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (ServiceMessage) ois.readObject();
        } catch (Exception e) {
            System.err.println("Failed to decode message");
        }
        return null;
    }
}
