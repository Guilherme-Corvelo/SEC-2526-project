package depchain.consensus;

/**
 * SignedVote: A cryptographically signed vote in the Byzantine HotStuff protocol.
 * 
 * Contains the vote data (view, nodeId, blockHash, phase) and a digital signature
 * to ensure authenticity and non-repudiation.
 */
public class SignedVote extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    
    private final byte[] blockHash;
    private final String phase;  // "prepare", "precommit", or "commit"
    private final byte[] signature;
    
    public SignedVote(long view, int senderId, byte[] blockHash, String phase, byte[] signature) {
        super(view, senderId);
        this.blockHash = blockHash.clone();
        this.phase = phase;
        this.signature = signature.clone();
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    public String getPhase() {
        return phase;
    }
    
    public byte[] getSignature() {
        return signature.clone();
    }
    
    @Override
    public String toString() {
        return "SignedVote[view=" + view + ", sender=" + senderId + 
               ", phase=" + phase + ", blockHash=" + Block.hashToString(blockHash) + "]";
    }
}
