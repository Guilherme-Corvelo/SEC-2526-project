package depchain.consensus;

/**
 * Finish message from leader to all replicas.
 * Signals that a quorum has been reached for the block, and it is now committed.
 */
public class Finish extends HotStuffMessage {
    private final byte[] blockHash;
    private final QuorumCertificate qc;
    
    public Finish(long view, int senderId, byte[] blockHash, QuorumCertificate qc) {
        super(view, senderId);
        this.blockHash = blockHash.clone();
        this.qc = qc;
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    public QuorumCertificate getQC() {
        return qc;
    }
    
    @Override
    public String toString() {
        return "Finish[view=" + view + ", sender=" + senderId + 
               ", blockHash=" + Block.hashToString(blockHash) + ", qc=" + qc + "]";
    }
}
