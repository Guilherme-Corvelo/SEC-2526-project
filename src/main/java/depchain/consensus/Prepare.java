package depchain.consensus;

/**
 * Prepare message from leader to replicas.
 * Carries a block proposal with justification (highQC).
 */
public class Prepare extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final Block node; // the proposed block
    private final QuorumCertificate justify; // highQC for safety
    
    public Prepare(long view, int senderId, Block node, QuorumCertificate justify) {
        super(view, senderId);
        this.node = node;
        this.justify = justify;
    }
    
    public Block getNode() {
        return node;
    }
    
    public QuorumCertificate getJustify() {
        return justify;
    }
    
    @Override
    public String toString() {
        String justStr = justify != null ? justify.toString() : "null";
        return "Prepare[view=" + view + ", sender=" + senderId + 
               ", node=" + node + ", justify=" + justStr + "]";
    }
}
