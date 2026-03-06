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
 * For Step 4 (crash-only faults with failure detection):
 * - Implements the full protocol flow (propose, vote, commit).
 * - Uses authenticated perfect links for message delivery.
 * - Integrates timeout-based failure detection to handle leader crashes.
 * - Automatically triggers view changes when leader is suspected crashed.
 * - Maintains a log of committed blocks.
 * - Notifies listeners when blocks are committed or view changes occur.
 */
public class HotStuffNode implements APLListener {
    private final int nodeId;
    private final List<Integer> nodeIds;
    private final AuthenticatedPerfectLinks apl;
    private final ConsensusListener listener;
    private ViewChangeListener viewChangeListener;
    
    // State
    private long currentView = 0;
    //TODO:  instead of deque use list
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
    
    // Failure detection for leader crash handling
    private FailureDetector failureDetector;
    private Thread leaderMonitorThread;
    
    public HotStuffNode(int nodeId, List<Integer> nodeIds, AuthenticatedPerfectLinks apl,
                        ConsensusListener listener) {
        this.nodeId = nodeId;
        this.nodeIds = new ArrayList<>(nodeIds);
        this.apl = apl;
        this.listener = listener;
        this.quorumSize = nodeIds.size() / 2 + 1; // ⌈n/2⌉ + 1
        
        // Initialize failure detector with 2-second timeout
        this.failureDetector = new TimeoutFailureDetector(nodeIds, 2000);
        
        // add genesis block
        log.add(Block.genesis());
    }
    
    /**
     * Set a custom failure detector (mainly for testing).
     */
    public void setFailureDetector(FailureDetector fd) {
        this.failureDetector = fd;
    }
    
    /**
     * Get the new-view messages map (for subclasses).
     */
    protected Map<Long, Map<Integer, NewView>> getNewViewMessages() {
        return newViewMessages;
    }
    
    /**
     * Register a listener for view change events.
     */
    public void registerViewChangeListener(ViewChangeListener listener) {
        this.viewChangeListener = listener;
    }
    
    /**
     * Start the consensus node: register with APL and start failure detector.
     * Also begins monitoring for leader timeouts.
     */
    public void start() throws IOException {
        //TODO: understand listener what shit he does
        apl.registerListener(this);
        
        // Start failure detector
        if (failureDetector != null) {
            failureDetector.start();
            // Also record a heartbeat for all nodes (alive at startup)
            for (int id : nodeIds) {
                failureDetector.heartbeat(id);
            }
        }
        
        // Start leader monitor thread to detect leader crashes
        startLeaderMonitor();
        
        if (nodeId == getLeader(currentView)) {
            System.out.println("[" + nodeId + "] I am the initial leader (view " + currentView + 
                              "). Ready to propose.");
        } else {
            System.out.println("[" + nodeId + "] I am a replica (view " + currentView + ").");
        }
    }
    
    /**
     * Stop the consensus node: shut down failure detector and monitor.
     */
    public void stop() {
        if (failureDetector != null) {
            failureDetector.stop();
        }
        if (leaderMonitorThread != null) {
            leaderMonitorThread.interrupt();
        }
    }
    
    /**
     * Start a monitor thread that checks if the current leader is suspected crashed.
     * If so, trigger a view change (timeout-based leader failure detection).
     */
    private void startLeaderMonitor() {
        leaderMonitorThread = new Thread(() -> {
            while (Thread.currentThread().isAlive()) {
                try {
                    Thread.sleep(500); // Check every 500ms
                    
                    if (failureDetector == null) break;
                    
                    synchronized (this) {
                        int leader = getLeader(currentView);
                        if (failureDetector.isSuspected(leader)) {
                            System.out.println("[" + nodeId + "] Detected leader " + leader +
                                             " crashed in view " + currentView + ". Triggering view change.");
                            triggerViewChange(leader);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "HS-leader-monitor-" + nodeId);
        leaderMonitorThread.setDaemon(true);
        leaderMonitorThread.start();
    }
    
    /**
     * Trigger a view change when current leader is suspected crashed.
     * Advances to the next view and sends new-view message.
     */
    private void triggerViewChange(int suspectedLeader) {
        currentView++;
        pendingBlock = null;
        prepareVotes.clear();
        precommitVotes.clear();
        commitVotes.clear();
        newViewMessages.clear();
        
        System.out.println("[" + nodeId + "] Advanced to view " + currentView + 
                          " (leader " + suspectedLeader + " suspected). New leader: " + 
                          getLeader(currentView));
        
        // Notify view change listener
        if (viewChangeListener != null) {
            viewChangeListener.onViewChange(currentView, suspectedLeader);
        }
        
        // Send new-view for the new view
        try {
            sendNewView();
        } catch (IOException e) {
            System.err.println("[" + nodeId + "] Failed to send new-view after FD timeout: " + 
                              e.getMessage());
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
     * 
     * Also records a heartbeat from the sender (for failure detection).
     */
    @Override
    public void onMessage(int srcId, byte[] payload) {
        // Record heartbeat from this sender (node is alive)
        if (failureDetector != null) {
            failureDetector.heartbeat(srcId);
        }
        
        try {
            HotStuffMessage msg = deserializeMessage(payload);
            handleMessage(msg);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to handle message from " + srcId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    protected synchronized void handleMessage(HotStuffMessage msg) {
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
    
    protected void handleNewView(NewView msg) {
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
    
    protected void handlePrepare(Prepare msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        // Check view is current
        if (msg.getView() < currentView) {
            System.out.println("[" + nodeId + "] Ignoring prepare for old view " + msg.getView());
            return;
        }
        
        // Advance to this view if needed
        //TODO: view it makes sense to propose same view number
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
    
    protected void handlePrepareVote(PrepareVote msg) {
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
    
    protected void handlePreCommit(PreCommit msg) {
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
    
    protected void handlePreCommitVote(PreCommitVote msg) {
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
    
    protected void handleCommit(Commit msg) {
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
    
    protected void handleCommitVote(CommitVote msg) {
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
    
    protected void handleDecide(Decide msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);
        
        if (msg.getView() < currentView) {
            return;
        }
        
        commitBlock(msg.getView());
    }

    /**
     * Helper to commit the pending block, advance to next view, and send new-view.
     */
    protected void commitBlock(long view) {
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
    
    protected int getLeader(long view) {
        return (int) (view % nodeIds.size());
    }

    /**
     * Broadcast a consensus message to all other replicas asynchronously.
     * No exceptions are propagated; failures are logged but the broadcast
     * proceeds to other nodes.
     */
    protected void broadcastConsensusMessage(HotStuffMessage msg) {
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
    protected void asyncSend(int dest, byte[] payload) {
        new Thread(() -> {
            try {
                apl.send(dest, payload);
            } catch (IOException e) {
                System.err.println("[" + nodeId + "] async send to " + dest + 
                                   " failed: " + e.getMessage());
            }
        }, "HS-send-" + nodeId + "-to-" + dest + "-" + System.nanoTime()).start();
    }

    
    protected byte[] serializeMessage(HotStuffMessage msg) {
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
    
    protected HotStuffMessage deserializeMessage(byte[] payload) {
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
    
    /**
     * Protected accessors for subclasses (e.g., ByzantineHotStuffNode)
     */
    protected void setCurrentView(long view) {
        this.currentView = view;
    }
    
    protected void setPendingBlock(Block block) {
        this.pendingBlock = block;
    }
    
    protected Block getPendingBlock() {
        return pendingBlock;
    }
    
    protected QuorumCertificate getHighQC() {
        return highQC;
    }
    
    protected void setHighQC(QuorumCertificate qc) {
        this.highQC = qc;
    }
    
    protected QuorumCertificate getLockedQC() {
        return lockedQC;
    }
    
    protected void setLockedQC(QuorumCertificate qc) {
        this.lockedQC = qc;
    }
    
    protected int getQuorumSize() {
        return quorumSize;
    }
    
    protected void clearViewState() {
        prepareVotes.clear();
        precommitVotes.clear();
        commitVotes.clear();
        newViewMessages.clear();
    }
}
