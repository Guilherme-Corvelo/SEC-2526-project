package depchain.consensus;

/**
 * Propose message from leader to all replicas.
 * Contains: view, block, and the prepared QC from the previous view.
 */
public class Propose extends HotStuffMessage {
    private final Block block;
    private final QuorumCertificate preparedQC; // may be null for genesis
    
    public Propose(long view, int senderId, Block block, QuorumCertificate preparedQC) {
        super(view, senderId);
        this.block = block;
        this.preparedQC = preparedQC;
    }
    
    public Block getBlock() {
        return block;
    }
    
    public QuorumCertificate getPreparedQC() {
        return preparedQC;
    }
    
    @Override
    public String toString() {
        String qcStr = preparedQC != null ? preparedQC.toString() : "null";
        return "Propose[view=" + view + ", sender=" + senderId + 
               ", block=" + block + ", preparedQC=" + qcStr + "]";
    }
}
