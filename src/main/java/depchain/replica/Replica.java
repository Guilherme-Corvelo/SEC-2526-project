package depchain.replica;

import depchain.consensus.Block;
import depchain.consensus.ByzantineHotStuffNode;
import depchain.consensus.ConsensusListener;
import depchain.consensus.HotStuffNode;
import depchain.consensus.ProposalReadyListener;
import depchain.network.APLListener;
import depchain.network.AuthenticatedPerfectLinks;
import depchain.service.BlockchainService;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Replica: A blockchain replica that handles both consensus and client requests.
 *
 * This class integrates:
 * - Byzantine consensus protocol (ByzantineHotStuffNode)
 * - Client data buffering and processing
 * - Blockchain service for committed blocks
 *
 * When client data arrives, it's buffered. When this replica becomes leader
 * and collects a quorum of new-view messages, it automatically proposes a block
 * containing all buffered client data.
 */
public class Replica implements APLListener, ConsensusListener, ProposalReadyListener {
    private final int replicaId;
    private final ByzantineHotStuffNode consensusNode;
    private final BlockchainService blockchainService;
    private final Queue<String> clientDataBuffer;
    private boolean isReadyToPropose;

    public Replica(int replicaId, List<Integer> allReplicaIds, AuthenticatedPerfectLinks apl,
                   PrivateKey privateKey, Map<Integer, PublicKey> publicKeys) {
        this.replicaId = replicaId;
        this.clientDataBuffer = new ConcurrentLinkedQueue<>();
        this.isReadyToPropose = false;

        // Create blockchain service
        this.blockchainService = new BlockchainService();

        // Create consensus node with this replica as listener
        this.consensusNode = new ByzantineHotStuffNode(
            replicaId, allReplicaIds, apl, this, privateKey, publicKeys, this
        );

        // Register this replica as APL listener to receive client data
        apl.registerListener(this);

        System.out.println("[Replica-" + replicaId + "] Initialized");
    }

    /**
     * Called by APL when client data arrives.
     * Buffers the data for later proposal when this replica becomes leader.
     */
    @Override
    public void onMessage(int senderId, byte[] data) {
        try {
            // Convert bytes back to string (client sends as string bytes)
            String clientData = new String(data);
            clientDataBuffer.add(clientData);
            System.out.println("[Replica-" + replicaId + "] Buffered client data: \"" + clientData + "\"");

            // If this replica is the current leader and ready to propose, propose immediately
            if (isCurrentLeader() && isReadyToPropose && !clientDataBuffer.isEmpty()) {
                proposeBufferedData();
            }
        } catch (Exception e) {
            System.err.println("[Replica-" + replicaId + "] Error processing client data: " + e.getMessage());
        }
    }

    /**
     * Called by consensus layer when a block is committed.
     * Forwards to blockchain service for persistence.
     */
    @Override
    public void onCommit(Block block) {
        blockchainService.onCommit(block);
    }

    /**
     * Called when this replica becomes ready to propose (after collecting new-view quorum).
     * If we have buffered client data, propose it now.
     */
    public void onReadyToPropose() {
        isReadyToPropose = true;
        System.out.println("[Replica-" + replicaId + "] Ready to propose blocks");

        if (isCurrentLeader() && !clientDataBuffer.isEmpty()) {
            proposeBufferedData();
        }
    }

    /**
     * Propose all buffered client data as a single block.
     */
    private void proposeBufferedData() {
        if (clientDataBuffer.isEmpty()) {
            return;
        }

        // For Phase 1 simplicity, combine all buffered data into one string
        // In Phase 2, this could be a proper transaction batch
        StringBuilder combinedData = new StringBuilder();
        int count = 0;
        while (!clientDataBuffer.isEmpty()) {
            if (count > 0) combinedData.append(" | ");
            combinedData.append(clientDataBuffer.poll());
            count++;
        }

        try {
            consensusNode.propose(combinedData.toString());
            System.out.println("[Replica-" + replicaId + "] Proposed block with " + count + " client submissions");
        } catch (Exception e) {
            System.err.println("[Replica-" + replicaId + "] Failed to propose: " + e.getMessage());
            // Put data back in buffer for retry
            clientDataBuffer.add(combinedData.toString());
        }
    }

    private boolean isCurrentLeader() {
        return consensusNode.isCurrentLeader();
    }

    // Delegate methods to consensus node
    public void start() throws IOException {
        consensusNode.start();
    }

    public void stop() {
        consensusNode.stop();
    }

    // Delegate methods to blockchain service
    public String getEntry(int index) {
        return blockchainService.getEntry(index);
    }

    public List<String> getAllEntries() {
        return blockchainService.getAllEntries();
    }

    public int getLogSize() {
        return blockchainService.getLogSize();
    }

    // Access to consensus node for testing/advanced operations
    public ByzantineHotStuffNode getConsensusNode() {
        return consensusNode;
    }
}