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

    public byte[] serialize() {
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

    public static ServiceMessage deserialize(byte[] payload) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (ServiceMessage) ois.readObject();
        } catch (Exception e) {
            System.err.println("Failed to decode message");
        }
        return null;
    }

        public boolean equals(Object obj){
        if (!(obj instanceof ServiceMessage)) {
            return false;
        }
        
        ServiceMessage other = (ServiceMessage) obj;

        if (!(this.requestId == other.requestId) ||
            !(this.commited == other.commited )){
            return false;
        }

        if (this.data == null) {
            if (other.data != null) {
                return false;
            }
        } else {
            if (!this.data.equals(other.data)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "ServiceMessage[requestId=" + requestId +
        ", data=" + (data != null ? data.toString() : "null") +
        ", commited=" + commited + "]";
    }
}
