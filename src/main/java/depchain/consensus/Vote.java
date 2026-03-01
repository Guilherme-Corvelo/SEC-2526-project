package depchain.consensus;

/**
 * Vote message from replica to leader.
 * Signals that the replica has accepted the block proposed in the given view.
 */
public class Vote extends HotStuffMessage {
    private final byte[] blockHash;
    
    public Vote(long view, int senderId, byte[] blockHash) {
        super(view, senderId);
        this.blockHash = blockHash.clone();
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    @Override
    public String toString() {
        return "Vote[view=" + view + ", sender=" + senderId + 
               ", blockHash=" + Block.hashToString(blockHash) + "]";
    }
}
