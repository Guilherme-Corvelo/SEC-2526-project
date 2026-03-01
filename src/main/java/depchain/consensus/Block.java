package depchain.consensus;

import java.io.Serializable;
import java.security.MessageDigest;

/**
 * A block in the consensus protocol.
 * For Step 3, we simplify: a block is just a string of application data.
 * In later steps, it could include a parent hash, timestamp, etc.
 */
public class Block implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String data;
    private final byte[] hash;
    
    /**
     * Create a block with application data.
     */
    public Block(String data) {
        this.data = data;
        this.hash = computeHash(data);
    }
    
    /**
     * Create a genesis block (empty data).
     */
    public static Block genesis() {
        return new Block("GENESIS");
    }
    
    public String getData() {
        return data;
    }
    
    public byte[] getHash() {
        return hash.clone();
    }
    
    private static byte[] computeHash(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("failed to compute hash", e);
        }
    }
    
    public static String hashToString(byte[] hash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "Block[data=" + data + ", hash=" + hashToString(hash) + "]";
    }
}
