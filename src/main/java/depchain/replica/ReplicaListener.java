package depchain.replica;

public interface ReplicaListener {
    /**
     * Called when a message is reliably delivered from a remote process.
     *
     * @param srcId   identifier of the sender
     * @param payload raw payload bytes
     */
    void onMessage(int senderId ,byte[] payload);
}
