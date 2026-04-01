package depchain.API;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import depchain.blockchain.Transaction;

public class Request implements Serializable{
    private Transaction transaction = null;

    public Request(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
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

        if (this.transaction == null) {
            if (other.transaction != null) {
                return false;
            }
        } else {
            if (!this.transaction.equals(other.transaction)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "ServiceMessage[" +
        "transaction=" + (transaction != null ? transaction.toString() : "null") + "]";
    }
}
