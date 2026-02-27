package depchain.network;

/**
 * Listener for messages delivered by the authenticated perfect links abstraction.
 */
public interface APLListener {
    /**
     * Called when a message is reliably delivered from a remote process.
     *
     * @param srcId   identifier of the sender
     * @param payload raw payload bytes
     */
    void onMessage(int srcId, byte[] payload);
}
