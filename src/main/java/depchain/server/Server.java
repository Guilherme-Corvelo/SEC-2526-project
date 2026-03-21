package depchain.server;

import depchain.consensus.HotStuffNode;
import depchain.network.APLListener;
import depchain.network.APL;
import depchain.service.BlockchainService;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server bridge between networking, consensus node, and blockchain service.

public class Server implements APLListener, ProposalReadyListener {
    private final int serverId;
    private final HotStuffNode consensusNode;
    private final BlockchainService blockchainService;
    private final APL apl;

    private final Queue<Request> pendingRequests = new ConcurrentLinkedQueue<>();
    private final Queue<Request> inFlightRequests = new ConcurrentLinkedQueue<>();
    private volatile boolean serverReadyToPropose;
    /* 
    public Server(int serverId, List<Integer> allServerIds, APL apl,
                  PrivateKey privateKey, Map<Integer, PublicKey> publicKeys) {
        this(serverId, allServerIds, apl, privateKey, publicKeys,    }

    public Server(int serverId, List<Integer> allServerIds, APL apl,
                  PrivateKey privateKey, Map<Integer, PublicKey> publicKeys,
                  BlockchainService blockchainService) {
        this.serverId = serverId;
        this.apl = apl;
        this.blockchainService = blockchainService;
        this.serverReadyToPropose = false;

        /* 
        this.consensusNode = new HotStuffNode(
            serverId, allServerIds, apl, this, privateKey, publicKeys, this
        );

        apl.registerListener(this);
        System.out.println("[Server-" + serverId + "] Initialized");
    }

    /**
     * Routes service requests and forwards consensus traffic to the node.

    @Override
    public void onMessage(int senderId, byte[] data) {
        try {
            if (Request.deserialize(data) != null) {
                return;
            }

            Request request = Request.deserialize(data);
            if (request != null) {
                //int replyTo = request.hasReplyTo() ? request.getReplyTo() : senderId;
                Request normalized = new Request(request.getRequestId(), request.getData());

                int leaderId = consensusNode.getLeader(consensusNode.getCurrentView());
                if (leaderId != serverId) {
                    asyncSend(leaderId, normalized.serialize());
                    return;
                }

                pendingRequests.add(normalized);
                tryProposeNext();
                return;
            }

            // Not a service payload: delegate to consensus node (server-to-server traffic).
            consensusNode.onMessage(senderId, data);
        } catch (Exception e) {
            System.err.println("[Server-" + serverId + "] Error processing message: " + e.getMessage());
        }
    }

     * Persist commit and acknowledge the corresponding service request.
   
    @Override
    public void onCommit(Block block) {
        blockchainService.onCommit(block);

        Request committedRequest = inFlightRequests.poll();
        if (committedRequest == null) {
            return;
        }

        byte[] ack = committedRequest.serialize();
        //asyncSend(committedRequest, ack);
    }
  */
    /**
     * Consensus algorithm decides when leader is ready; server only reacts to that signal.

    @Override
    public void onReadyToPropose() {
        serverReadyToPropose = true;
        tryProposeNext();
    }

    private synchronized void tryProposeNext() {
        if (!serverReadyToPropose || !consensusNode.isCurrentLeader()) {
            return;
        }

        Request next = pendingRequests.poll();
        if (next == null) {
            return;
        }

        try {
            consensusNode.propose(next.getData());
            inFlightRequests.add(next);
            serverReadyToPropose = false;
        } catch (Exception e) {
            pendingRequests.add(next);
            System.err.println("[Server-" + serverId + "] Failed to propose: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        consensusNode.start();
        // Keep server as top-level receiver and forward consensus traffic explicitly.
        apl.registerListener(this);
    }

    public void stop() {
        consensusNode.stop();
    }

    public String getEntry(int index) {
        return blockchainService.getEntry(index);
    }

    public List<String> getAllEntries() {
        return blockchainService.getAllEntries();
    }

    public int getLogSize() {
        return blockchainService.getLogSize();
    }

    public HotStuffNode getConsensusNode() {
        return consensusNode;
    }

    private void asyncSend(int destId, byte[] payload) {
        new Thread(() -> {
            try {
                apl.send(destId, payload);
            } catch (IOException e) {
                System.err.println("[Server-" + serverId + "] Send failed to " + destId + ": " + e.getMessage());
            }
        }).start();
    }
}
*/