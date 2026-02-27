package depchain.network;

import java.io.IOException;

/**
 * Authenticated Perfect Links abstraction (APL).
 *
 * The abstraction provides reliable, authenticated point-to-point message
 * delivery between a fixed set of processes. Messages are signed by the
 * sender's private key and verified by the receiver using the sender's public
 * key. The underlying transport uses UDP sockets but guarantees are layered on
 * top.
 */
public interface AuthenticatedPerfectLinks {

    /**
     * Send a message to the destination process.
     *
     * @param destId  identifier of the destination process
     * @param payload application-level payload
     * @throws IOException if the underlying transport fails
     */
    void send(int destId, byte[] payload) throws IOException;

    /**
     * Register a listener that will be notified when messages arrive.
     *
     * @param listener callback to invoke on delivery
     */
    void registerListener(APLListener listener);

    /**
     * Start the service (bind socket, start receiver thread).
     *
     * @throws IOException if binding fails
     */
    void start() throws IOException;

    /**
     * Stop the service and release resources.
     */
    void stop();
}
