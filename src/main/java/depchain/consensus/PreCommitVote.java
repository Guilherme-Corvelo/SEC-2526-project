package depchain.consensus;

/**
 * PreCommitVote message from replica to leader.
 * Sent after accepting a pre-commit message.
 */
public class PreCommitVote extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final byte[] blockHash;
    
    public PreCommitVote(long view, int senderId, byte[] blockHash) {
        super(view, senderId);
        this.blockHash = blockHash.clone();
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    @Override
    public String toString() {
        return "PreCommitVote[view=" + view + ", sender=" + senderId + 
               ", blockHash=" + Block.hashToString(blockHash) + "]";
    }
}
