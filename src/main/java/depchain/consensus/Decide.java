package depchain.consensus;

/**
 * Decide message from leader to replicas.
 * Sent after collecting quorum of commit votes (forms commitQC).
 * This triggers the decision/commit of the block.
 */
public class Decide extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final QuorumCertificate commitQC;
    
    public Decide(long view, int senderId, QuorumCertificate commitQC) {
        super(view, senderId);
        this.commitQC = commitQC;
    }
    
    public QuorumCertificate getCommitQC() {
        return commitQC;
    }
    
    @Override
    public String toString() {
        return "Decide[view=" + view + ", sender=" + senderId + 
               ", commitQC=" + commitQC + "]";
    }
}
