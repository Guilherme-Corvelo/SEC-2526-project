package depchain.consensus;

/**
 * NewView message sent by a replica when transitioning to a new view.
 * Carries the highest prepareQC the replica has received.
 */
public class NewView extends HotStuffMessage {
    private final QuorumCertificate prepareQC; // highest prepareQC, may be null

    private static final long serialVersionUID = 1L;
    
    public NewView(long view, int senderId, QuorumCertificate prepareQC) {
        super(view, senderId);
        this.prepareQC = prepareQC;
    }
    
    public QuorumCertificate getPrepareQC() {
        return prepareQC;
    }
    
    @Override
    public String toString() {
        String qcStr = prepareQC != null ? prepareQC.toString() : "null";
        return "NewView[view=" + view + ", sender=" + senderId + ", prepareQC=" + qcStr + "]";
    }
}
