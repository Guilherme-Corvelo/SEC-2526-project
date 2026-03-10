package depchain.client;

import depchain.network.AuthenticatedPerfectLinksImpl;
import java.io.IOException;

/**
 * Phase 1 Client Library for appending data to the consensus blockchain.
 * 
 * Simple fire-and-forget client that:
 * - Submits string data to a replica
 * - Let's the consensus layer (ByzantineHotStuffNode) handle ordering and commitment
 * 
 * In Phase 2, this will be enhanced with:
 * - Waiting for commitment confirmation
 * - Maintaining client-side append-only log
 * - Transaction tracking
 */
public class ServiceClient {
    private final int clientId;
    private final AuthenticatedPerfectLinksImpl apl;
    private final int targetReplica;
    
    public ServiceClient(int clientId, AuthenticatedPerfectLinksImpl apl, int targetReplica) {
        this.clientId = clientId;
        this.apl = apl;
        this.targetReplica = targetReplica;
    }
    
    /**
     * Submit a string to be appended to the consensus blockchain.
     * 
     * Phase 1: Fire-and-forget submission.
     * The consensus layer will handle ordering (via ByzantineHotStuffNode)
     * and commitment (via leader election and QC collection).
     */
    public void submitAppendRequest(String data) throws IOException {
        // For phase 1, just send the data as bytes via APL
        byte[] payload = data.getBytes();
        apl.send(targetReplica, payload);
        System.out.println("[Client-" + clientId + "] Submitted: \"" + data + "\"");
    }
}
