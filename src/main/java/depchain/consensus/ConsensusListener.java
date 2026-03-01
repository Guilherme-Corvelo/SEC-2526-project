package depchain.consensus;

/**
 * Listener interface for consensus layer upcalls.
 * Called when a block is committed (decided).
 */
public interface ConsensusListener {
    /**
     * Called when a block has been committed and is now part of the ledger.
     * 
     * @param block the committed block
     */
    void onCommit(Block block);
}
