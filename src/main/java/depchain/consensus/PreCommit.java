package depchain.consensus;

/**
 * PreCommit message from leader to replicas.
 * Sent after collecting quorum of prepare votes (forms prepareQC).
 */
public class PreCommit extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final QuorumCertificate prepareQC;
    
    public PreCommit(long view, int senderId, QuorumCertificate prepareQC) {
        super(view, senderId);
        this.prepareQC = prepareQC;
    }
    
    public QuorumCertificate getPrepareQC() {
        return prepareQC;
    }
    
    @Override
    public String toString() {
        return "PreCommit[view=" + view + ", sender=" + senderId + 
               ", prepareQC=" + prepareQC + "]";
    }
}
