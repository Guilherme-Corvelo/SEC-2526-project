package depchain.consensus;

/**
 * Callback interface for when the consensus node is ready to propose a new block.
 * Used by replicas to automatically propose buffered client data.
 */
public interface ProposalReadyListener {
    /**
     * Called when this node has collected a quorum of new-view messages and is ready to propose.
     */
    void onReadyToPropose();
}