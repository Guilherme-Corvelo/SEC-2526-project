package depchain.consensus;

/**
 * Commit message from leader to replicas.
 * Sent after collecting quorum of pre-commit votes (forms precommitQC).
 */
public class Commit extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final QuorumCertificate precommitQC;
    
    public Commit(long view, int senderId, QuorumCertificate precommitQC) {
        super(view, senderId);
        this.precommitQC = precommitQC;
    }
    
    public QuorumCertificate getPrecommitQC() {
        return precommitQC;
    }
    
    @Override
    public String toString() {
        return "Commit[view=" + view + ", sender=" + senderId + 
               ", precommitQC=" + precommitQC + "]";
    }
}
