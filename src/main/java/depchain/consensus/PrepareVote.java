package depchain.consensus;

/**
 * PrepareVote message from replica to leader.
 * Sent after accepting a prepare message.
 */
public class PrepareVote extends HotStuffMessage {
    private static final long serialVersionUID = 1L;
    private final byte[] blockHash;
    
    public PrepareVote(long view, int senderId, byte[] blockHash) {
        super(view, senderId);
        this.blockHash = blockHash.clone();
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    @Override
    public String toString() {
        return "PrepareVote[view=" + view + ", sender=" + senderId + 
               ", blockHash=" + Block.hashToString(blockHash) + "]";
    }
}
