package depchain.consensus;

import depchain.network.APLListener;
import depchain.network.AuthenticatedPerfectLinks;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HotStuffNode: A single replica in the Basic HotStuff consensus protocol.
 * 
 * For Step 3 (no Byzantine faults, automatic leader rotation):
 * - Implements the full protocol flow (propose, vote, finish).
 * - Uses authenticated perfect links for message delivery.
 * - Maintains a log of committed blocks.
 * - Notifies a listener when blocks are committed.
 */
public class HotStuffNode implements APLListener {
    private final int nodeId;
    private final List<Integer> nodeIds;
    private final AuthenticatedPerfectLinks apl;
    private final ConsensusListener listener;
    
    // State
    private long currentView = 0;
    private final Deque<Block> log = new ArrayDeque<>(); // committed blocks
    private Block pendingBlock = null; // block waiting for quorum
    
    // Quorum Certificates: highest prepareQC and lockedQC
    private QuorumCertificate highQC = null; // highest prepareQC seen
    private QuorumCertificate lockedQC = null; // locked QC (safety guard)
    
    // Tracking votes and messages per phase and view
    private final Map<Long, Map<Integer, byte[]>> prepareVotes = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, byte[]>> precommitVotes = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, byte[]>> commitVotes = new ConcurrentHashMap<>();
    
    // New-view messages received at start of new view
    private final Map<Long, Map<Integer, NewView>> newViewMessages = new ConcurrentHashMap<>();
    
    // Quorum size
    private final int quorumSize;
    
    public HotStuffNode(int nodeId, List<Integer> nodeIds, AuthenticatedPerfectLinks apl,
                        ConsensusListener listener) {
        this.nodeId = nodeId;
        this.nodeIds = new ArrayList<>(nodeIds);
        this.apl = apl;
        this.listener = listener;
        this.quorumSize = nodeIds.size() / 2 + 1; // ⌈n/2⌉ + 1
        
        // add genesis block
        log.add(Block.genesis());
    }
    
    /**
     * Start the consensus node: register with APL and optionally trigger first proposal
     * if this node is the first leader.
     */
    public void start() throws IOException {
        apl.registerListener(this);
        
        // Do not broadcast new-view during start; caller should ensure all nodes are
        // listening before any network traffic. This avoids race conditions in tests.
        if (nodeId == getLeader(currentView)) {
            System.out.println("[" + nodeId + "] I am the initial leader (view " + currentView + 
                              "). Ready to propose.");
        } else {
            System.out.println("[" + nodeId + "] I am a replica (view " + currentView + ").");
        }
    }
    
    public void sendNewView() throws IOException {
        NewView msg = new NewView(currentView, nodeId, highQC);
        broadcastConsensusMessage(msg);
    }
    
    /**
     * Called by the application layer to propose a new block.
     * Only the current leader should call this; others will error.
     */
    public synchronized void propose(String data) throws IOException {
        if (nodeId != getLeader(currentView)) {
            throw new IllegalStateException("Only the leader can propose");
        }
        
        Block block = new Block(data);
        pendingBlock = block;
        
        // Prepare phase: send prepare message with highQC
        Prepare msg = new Prepare(currentView, nodeId, block, highQC);
        broadcastConsensusMessage(msg);
        
        System.out.println("[" + nodeId + "] Proposed (prepare phase): " + msg);
    }
    
    /**
     * Called by APL when a consensus message arrives (as application payload).
     * We need to deserialize and dispatch.
     */
    @Override
    public void onMessage(int srcId, byte[] payload) {
        try {
            HotStuffMessage msg = deserializeMessage(payload);
            handleMessage(msg);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to handle message from " + srcId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private synchronized void handleMessage(HotStuffMessage msg) {
        if (msg instanceof NewView) {
            handleNewView((NewView) msg);
        } else if (msg instanceof Prepare) {
            handlePrepare((Prepare) msg);
        } else if (msg instanceof PrepareVote) {
            handlePrepareVote((PrepareVote) msg);
        } else if (msg instanceof PreCommit) {
            handlePreCommit((PreCommit) msg);
        } else if (msg instanceof PreCommitVote) {
            handlePreCommitVote((PreCommitVote) msg);
        } else if (msg instanceof Commit) {
            handleCommit((Commit) msg);
        } else if (msg instanceof CommitVote) {
            handleCommitVote((CommitVote) msg);
        } else if (msg instanceof Decide) {
            handleDecide((Decide) msg);
        }
    }
    
    private void handleNewView(NewView msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        // Only leader processes new-view messages
        if (nodeId != getLeader(currentView)) {
            return;
        }
        
        // Collect new-view messages for this view
        Map<Integer, NewView> views = newViewMessages.computeIfAbsent(msg.getView(), k -> new ConcurrentHashMap<>());
        views.put(msg.getSenderId(), msg);
        
        // Once we have a quorum of new-view messages, proceed with prepare phase
        if (views.size() >= quorumSize) {
            // Select highQC: the highest view QC from the new-view messages
            QuorumCertificate selectedQC = highQC;
            for (NewView nv : views.values()) {
                if (nv.getPrepareQC() != null) {
                    if (selectedQC == null || nv.getPrepareQC().getView() > selectedQC.getView()) {
                        selectedQC = nv.getPrepareQC();
                    }
                }
            }
            highQC = selectedQC;
            
            System.out.println("[" + nodeId + "] Collected quorum of new-view messages. " +
                              "Selected highQC: " + (highQC != null ? highQC.toString() : "null"));
            // Ready to propose a new block
        }
    }
    
    private void handlePrepare(Prepare msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        // Check view is current
        if (msg.getView() < currentView) {
            System.out.println("[" + nodeId + "] Ignoring prepare for old view " + msg.getView());
            return;
        }
        
        // Advance to this view if needed
        if (msg.getView() > currentView) {
            currentView = msg.getView();
            newViewMessages.clear();
            prepareVotes.clear();
            precommitVotes.clear();
            commitVotes.clear();
        }
        
        // Apply safeNode predicate: accept if extends from lockedQC or justify has higher view
        Block block = msg.getNode();
        QuorumCertificate justify = msg.getJustify();
        
        boolean safe = false;
        if (lockedQC == null) {
            // No lock yet, safe to accept
            safe = true;
        } else if (justify != null && justify.getView() > lockedQC.getView()) {
            // Justify has higher view than lock, safe
            safe = true;
        } else if (justify != null && Arrays.equals(justify.getBlockHash(), 
                   lockedQC.getBlockHash())) {
            // Same branch as lock, safe
            safe = true;
        }
        
        if (!safe) {
            System.out.println("[" + nodeId + "] Rejected prepare (safeNode predicate failed)");
            return;
        }
        
        // Accept the block
        pendingBlock = block;
        
        // Send prepare vote back to leader (asynchronously)
        PrepareVote vote = new PrepareVote(currentView, nodeId, block.getHash());
        asyncSend(msg.getSenderId(), serializeMessage(vote));
        System.out.println("[" + nodeId + "] Queued prepare vote: " + vote);
    }
    
    private void handlePrepareVote(PrepareVote msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        // Only leader processes votes
        if (nodeId != getLeader(currentView)) {
            return;
        }
        
        // Track prepare votes for this view
        Map<Integer, byte[]> votes = prepareVotes.computeIfAbsent(msg.getView(), k -> new ConcurrentHashMap<>());
        votes.put(msg.getSenderId(), msg.getBlockHash());
        
        // Check if we have a quorum for this block
        if (votes.size() >= quorumSize && pendingBlock != null) {
            // Form prepareQC and advance to pre-commit phase
            QuorumCertificate prepareQC = new QuorumCertificate(msg.getView(), pendingBlock.getHash(), quorumSize);
            for (int i = 0; i < quorumSize && i < votes.size(); i++) {
                prepareQC.addVote(i);
            }
            
            try {
                PreCommit precommit = new PreCommit(msg.getView(), nodeId, prepareQC);
                broadcastConsensusMessage(precommit);
                System.out.println("[" + nodeId + "] Queued pre-commit broadcast: " + precommit);
            } catch (Exception e) {
                System.err.println("[" + nodeId + "] Failed to queue pre-commit: " + e.getMessage());
            }
        }
    }
    
    private void handlePreCommit(PreCommit msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        // Check view
        if (msg.getView() < currentView) {
            return;
        }
        
        if (msg.getView() > currentView) {
            currentView = msg.getView();
        }
        
        // Accept and send pre-commit vote (asynchronously)
        PreCommitVote vote = new PreCommitVote(currentView, nodeId, msg.getPrepareQC().getBlockHash());
        asyncSend(msg.getSenderId(), serializeMessage(vote));
        System.out.println("[" + nodeId + "] Queued pre-commit vote: " + vote);
    }
    
    private void handlePreCommitVote(PreCommitVote msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        if (nodeId != getLeader(currentView)) {
            return;
        }
        
        Map<Integer, byte[]> votes = precommitVotes.computeIfAbsent(msg.getView(), k -> new ConcurrentHashMap<>());
        votes.put(msg.getSenderId(), msg.getBlockHash());
        
        if (votes.size() >= quorumSize && pendingBlock != null) {
            QuorumCertificate precommitQC = new QuorumCertificate(msg.getView(), pendingBlock.getHash(), quorumSize);
            for (int i = 0; i < quorumSize && i < votes.size(); i++) {
                precommitQC.addVote(i);
            }
            
            try {
                Commit commit = new Commit(msg.getView(), nodeId, precommitQC);
                broadcastConsensusMessage(commit);
                System.out.println("[" + nodeId + "] Queued commit broadcast: " + commit);
            } catch (Exception e) {
                System.err.println("[" + nodeId + "] Failed to queue commit: " + e.getMessage());
            }
        }
    }
    
    private void handleCommit(Commit msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        if (msg.getView() < currentView) {
            return;
        }
        
        if (msg.getView() > currentView) {
            currentView = msg.getView();
        }
        
        // Set lockedQC (this is critical for safety)
        lockedQC = msg.getPrecommitQC();
        
        // Send commit vote (asynchronously)
        CommitVote vote = new CommitVote(currentView, nodeId, msg.getPrecommitQC().getBlockHash());
        asyncSend(msg.getSenderId(), serializeMessage(vote));
        System.out.println("[" + nodeId + "] Queued commit vote: " + vote +
                          " (now locked on view " + lockedQC.getView() + ")");
    }
    
    private void handleCommitVote(CommitVote msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        if (nodeId != getLeader(currentView)) {
            return;
        }
        
        Map<Integer, byte[]> votes = commitVotes.computeIfAbsent(msg.getView(), k -> new ConcurrentHashMap<>());
        votes.put(msg.getSenderId(), msg.getBlockHash());
        
        if (votes.size() >= quorumSize && pendingBlock != null) {
            QuorumCertificate commitQC = new QuorumCertificate(msg.getView(), pendingBlock.getHash(), quorumSize);
            for (int i = 0; i < quorumSize && i < votes.size(); i++) {
                commitQC.addVote(i);
            }
            
            // Set highQC for next leader
            highQC = commitQC;
            
            try {
                Decide decide = new Decide(msg.getView(), nodeId, commitQC);
                broadcastConsensusMessage(decide);
                System.out.println("[" + nodeId + "] Queued decide broadcast: " + decide);
                
                // Leader also commits locally (rather than waiting for Decide to be received back)
                commitBlock(msg.getView());
            } catch (Exception e) {
                System.err.println("[" + nodeId + "] Failed to queue decide: " + e.getMessage());
            }
        }
    }
    
    private void handleDecide(Decide msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        if (msg.getView() < currentView) {
            return;
        }
        
        commitBlock(msg.getView());
    }

    /**
     * Helper to commit the pending block, advance to next view, and send new-view.
     */
    private void commitBlock(long view) {
        // Commit the block
        if (pendingBlock != null) {
            log.add(pendingBlock);
            if (listener != null) {
                listener.onCommit(pendingBlock);
            }
            System.out.println("[" + nodeId + "] Committed block: " + pendingBlock);
        }
        
        // Advance to next view
        currentView++;
        pendingBlock = null;
        prepareVotes.clear();
        precommitVotes.clear();
        commitVotes.clear();
        newViewMessages.clear();
        
        System.out.println("[" + nodeId + "] Advanced to view " + currentView + 
                          ". New leader: " + getLeader(currentView));
        
        // Send new-view for next view
        try {
            sendNewView();
        } catch (IOException e) {
            System.err.println("[" + nodeId + "] Failed to send new-view: " + e.getMessage());
        }
    }
    
    private int getLeader(long view) {
        return (int) (view % nodeIds.size());
    }

    /**
     * Broadcast a consensus message to all other replicas asynchronously.
     * No exceptions are propagated; failures are logged but the broadcast
     * proceeds to other nodes.
     */
    private void broadcastConsensusMessage(HotStuffMessage msg) {
        byte[] payload = serializeMessage(msg);
        for (int id : nodeIds) {
            if (id == nodeId) continue;
            asyncSend(id, payload);
        }
    }

    /**
     * Send a message to a destination in a separate thread so that the caller
     * (e.g., the receive loop handler) is never blocked waiting for an ACK.
     * This prevents deadlocks when message handlers need to send responses.
     */
    private void asyncSend(int dest, byte[] payload) {
        new Thread(() -> {
            try {
                apl.send(dest, payload);
            } catch (IOException e) {
                System.err.println("[" + nodeId + "] async send to " + dest + 
                                   " failed: " + e.getMessage());
            }
        }, "HS-send-" + nodeId + "-to-" + dest + "-" + System.nanoTime()).start();
    }

    
    private byte[] serializeMessage(HotStuffMessage msg) {
        // use Java serialization for simplicity
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("serialization failed", e);
        }
    }
    
    private HotStuffMessage deserializeMessage(byte[] payload) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(payload);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (HotStuffMessage) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("deserialization failed", e);
        }
    }
    
    public List<Block> getLog() {
        return new ArrayList<>(log);
    }
    
    public long getCurrentView() {
        return currentView;
    }
    
    public int getNodeId() {
        return nodeId;
    }
}
