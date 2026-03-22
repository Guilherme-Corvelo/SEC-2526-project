package depchain.API;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Request implements Serializable{
    private String data = null;

    public Request(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
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

    public static Request deserialize(byte[] payload) {
        ByteArrayInputStream bis = new ByteArrayInputStream(payload);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (Request) ois.readObject();
        } catch (Exception e) {
            System.err.println("Failed to decode message");
        }
        return null;
    }

    @Override
    public boolean equals(Object obj){
        if (!(obj instanceof Request)) {
            return false;
        }
        
        Request other = (Request) obj;

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
        return "ServiceMessage[" +
        "data=" + (data != null ? data.toString() : "null") + "]";
    }
}
