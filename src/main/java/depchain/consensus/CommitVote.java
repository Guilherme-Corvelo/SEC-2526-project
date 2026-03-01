package depchain.consensus;

/**
 * CommitVote message from replica to leader.
 * Sent after accepting a commit message.
 */
public class CommitVote extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final byte[] blockHash;
    
    public CommitVote(long view, int senderId, byte[] blockHash) {
        super(view, senderId);
        this.blockHash = blockHash.clone();
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    @Override
    public String toString() {
        return "CommitVote[view=" + view + ", sender=" + senderId + 
               ", blockHash=" + Block.hashToString(blockHash) + "]";
    }
}
