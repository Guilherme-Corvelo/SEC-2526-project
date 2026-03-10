package depchain.consensus;

/**
 * SignedVote: A cryptographically signed vote in the Byzantine HotStuff protocol.
 * 
 * Contains the vote data (view, nodeId, blockHash, phase) and a partial signature
 * share from the sender.
 */
public class SignedVote extends Vote {
    private static final long serialVersionUID = 1L;
    
    // Vote already carries the blockHash field via superclass
    private final String phase;  // "prepare", "precommit", or "commit"
    private final byte[] signature;
    
    public SignedVote(long view, int senderId, byte[] blockHash, String phase, byte[] signature) {
        // delegate hash storage to Vote constructor
        super(view, senderId, blockHash);
        this.phase = phase;
        this.signature = signature.clone();
    }
    
    // blockHash accessor is inherited from Vote
    
    public String getPhase() {
        return phase;
    }
    
    public byte[] getSignature() {
        return signature.clone();
    }
    
    @Override
    public String toString() {
         return "SignedVote[view=" + view + ", sender=" + senderId + 
             ", phase=" + phase + ", blockHash=" + Block.hashToString(getBlockHash()) + "]";
    }
}
