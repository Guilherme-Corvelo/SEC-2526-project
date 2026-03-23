package depchain.consensus;

import depchain.network.APLListener;
import depchain.Debug;
import depchain.API.Request;
import depchain.network.APL;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HotStuffNode: A replica in the Byzantine Fault Tolerant HotStuff consensus protocol.
 *
 * Implements HotStuff with cryptographic signatures and Byzantine quorum certificates.
 */
public class HotStuffNode implements APLListener {

    private APL apl;
    private int view = 0;
    private int f;
    private Node latestNode = new Node();
    private QuorumCertificate prepareQC = null;
    private QuorumCertificate lockedQC = null;

    private LinkedList<Integer> broadcastTo;
    private int id;
    private int n;
    private Type pendingVoteType = Type.NEWVIEW;
    private ConcurrentHashMap<Integer, Message> votes = new ConcurrentHashMap<>();

    public HotStuffNode(int id, 
                    int port, Map<Integer, InetSocketAddress> addresses,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys, int f, LinkedList<Integer> broadcastTo) throws IOException{

        this.apl = new APL(id, port, addresses, privateKey, publicKeys);
        this.apl.registerListener(this);
        this.broadcastTo = broadcastTo;
        this.n = broadcastTo.size();
        this.f = f;
        this.id = id;
        this.apl.start();
    }

    public void onMessage(int senderId, byte[] payload){
        Message message = Message.deserialize(payload);
        if (message != null) {
            Debug.debug( "At Node: " + getId() + " Sender ID : "+ senderId +  " received message : " + message.toString());
            handleMessage(senderId, message);
            return;
        }

        Request request = Request.deserialize(payload);
        if (request != null) {
            Debug.debug( "At Node: " + getId() + " Sender ID : "+ senderId +  " received message : " + request.toString());
            handleRequest(senderId, request);
            return;
        }
    }

    //TODO:THINK about new type for hotstuff response
    public void handleRequest(int senderId, Request request){
        //Debug.debug( "At Node: " + getId() + " Handling request :" + request.toString());
        Node requestNode = new Node(request.getData(), latestNode);
        //TODO:MAybe verification to extend?
        
        Message NewViewMessage = new Message(Type.NEWVIEW, getView(), requestNode, senderId);

        Debug.debug( "At Node: " + getId() + " Sent NewView message :" + NewViewMessage.toString() + "to leader: " + getLeaderId());
        send(getLeaderId(), NewViewMessage.serialize());
        
        if (getId() != getLeaderId()){ setPendingVoteType(Type.PREPARE);}
    }

    public synchronized void handleMessage(int senderId, Message msg){
        if (msg.getView() != getView()) {
            return;
        }
        
        switch (msg.getType()) {
            case NEWVIEW:
                handleNewViewMessage(senderId, msg);
                break;
        
            case PREPARE:
                handlePrepareMessage(senderId, msg);
                break;
        
            case PRECOMMIT:
                handlePrecommitMessage(senderId, msg);
                break;
        
            case COMMIT:
                handleCommitMessage(senderId, msg);
                break;
        
            case DECIDE:
                handleDecideMessage(senderId, msg);
                break;
        
            default:
                break;
        }
    }

    public void handleNewViewMessage(int senderId, Message msg){
        if (getId() == getLeaderId()){
            if (msg.getType() == getPendingVoteType()){
                getVotes().putIfAbsent(senderId, msg);
            }

            if(enoughVotes()){
                //TODO:Maybe think about node maybe everything
                Message prepareMsg = new Message(Type.PREPARE, getView(), msg.getNode(), msg.getRequesterId());

                QuorumCertificate highQC = getHighQC();
                prepareMsg.setJustify(highQC);
                setPendingVoteType(Type.PREPARE);
                getVotes().clear();

                Debug.debug( "Leader: " + getId() + " Broadcast Prepare message :" + prepareMsg.toString());

                broadcast(prepareMsg.serialize());
            }
        }
    }

    public void handlePrepareMessage(int senderId, Message msg){
        if (getId() == getLeaderId()){
            if (msg.getType() == getPendingVoteType() && msg.getPartialSign() != null){
                getVotes().putIfAbsent(senderId, msg);
            }
            //add to votes
            if(enoughVotes()){


                Message precommitMsg = new Message(Type.PRECOMMIT, getView(), msg.getNode(), msg.getRequesterId());

                QuorumCertificate highQC = new QuorumCertificate(Type.PRECOMMIT, getView(), msg.getNode(), combineVotes());
                precommitMsg.setJustify(highQC);

                setPendingVoteType(Type.PRECOMMIT);
                getVotes().clear();
                
                Debug.debug( "Leader: " + getId() + " Broadcast PreCommit message :" + precommitMsg.toString());
                broadcast(precommitMsg.serialize());
            }
        }
        if(senderId == getLeaderId() && msg.getPartialSign() == null){
            if (msg.getNode().canExtend(msg) &&
                safeNode(msg.getNode(), msg.getjustify())){

                setPrepareQC(msg.getjustify());
                //TODO: Think aboiut teshdold signs
                msg.Vote();

                Debug.debug( "At Node: " + getId() + " Sent Prepare message :" + msg.toString() + " to leader: " + getLeaderId());
                
                send(getLeaderId(), msg.serialize());
                
                if (getId() != getLeaderId()){ setPendingVoteType(Type.PRECOMMIT);}
            }

        }
    }

    public void handlePrecommitMessage(int senderId, Message msg){
        //Debug.debug("Entered handle precommit node :" + getId());
        if (getId() == getLeaderId()){
            if (msg.getType() == getPendingVoteType() && msg.getPartialSign() != null){
                getVotes().putIfAbsent(senderId, msg);
            }
            //add to votes
            if(enoughVotes()){
                Message commitMsg = new Message(Type.COMMIT, getView(), msg.getNode(), msg.getRequesterId());

                QuorumCertificate highQC = new QuorumCertificate(Type.COMMIT, getView(), msg.getNode(), combineVotes());
                commitMsg.setJustify(highQC);

                setPendingVoteType(Type.COMMIT);
                getVotes().clear();

                Debug.debug( "Leader: " + getId() + " Broadcast Commit message :" + commitMsg.toString());
                broadcast(commitMsg.serialize());
            }
        }
        if(senderId == getLeaderId() && msg.getPartialSign() == null){
            
            //Debug.debug("Extend: " + msg.getNode().canExtend(msg) + " safe node: " + safeNode(msg.getNode(), msg.getjustify()));
            //Debug.debug("Entered if precommit node :" + getId());
            setPrepareQC(msg.getjustify());
            //TODO: Think aboiut teshdold signs
            
            msg.Vote();
            setLockedQC(msg.getjustify());

            Debug.debug( "At Node: " + getId() + " Sent Precommit message :" + msg.toString() + " to leader: " + getLeaderId());
            send(getLeaderId(), msg.serialize());
            
            if (getId() != getLeaderId()){ setPendingVoteType(Type.COMMIT);}
        
        }
    }

    public void handleCommitMessage(int senderId, Message msg){
        if (getId() == getLeaderId()){
            if (msg.getType() == getPendingVoteType() && msg.getPartialSign() != null){
                getVotes().putIfAbsent(senderId, msg);
            }
            //add to votes
            if(enoughVotes()){


                Message decideMsg = new Message(Type.DECIDE, getView(), msg.getNode(), msg.getRequesterId());

                QuorumCertificate highQC = new QuorumCertificate(Type.DECIDE, getView(), msg.getNode(), combineVotes());
                decideMsg.setJustify(highQC);

                setPendingVoteType(Type.DECIDE);
                getVotes().clear();

                Debug.debug( "Leader: " + getId() + " Broadcast Decide message :" + decideMsg.toString());

                broadcast(decideMsg.serialize());
            }
        }
        if(senderId == getLeaderId() && msg.getPartialSign() == null){
            //TODO: Think aboiut teshdold signs
            msg.Vote();

            Debug.debug( "At Node: " + getId() + " Sent Commit message :" + msg.toString() + " to leader: " + getLeaderId());
            send(getLeaderId(), msg.serialize());           

            if (getId() != getLeaderId()){ setPendingVoteType(Type.DECIDE);}

        }

    }

    public void handleDecideMessage(int senderId, Message msg){
        //TODO: Think aboiut teshdold signs
        latestNode = msg.getNode();

        Debug.debug( "At Node: " + getId() + " Sent Decide message :" + msg.toString() + "to client " + msg.getRequesterId());

        send(msg.getRequesterId(), msg.serialize());        

        if (getId() != getLeaderId()){ setPendingVoteType(Type.NEWVIEW);}
    }

    //Think about quorum certificate not starting at null, having qc initialized with genesis ndoe
    private boolean safeNode(Node node, QuorumCertificate qc){
        return getLockedQC() == null || node.canExtend(getLockedQC().getNode()) || qc.getView() > getLockedQC().getView();
    }
    
    private int getLeaderId(){
        return broadcastTo.get(view%n);
    }

    //TODO: do it
    private byte[] combineVotes(){
        String combined = "";
        for (Message m : getVotes().values()){
            //Debug.debug(m.toString());
            combined += m.getPartialSign();
        }
        
        return combined.getBytes();
    }

    private boolean enoughVotes(){
        return getVotes().size() >= n - f;
    }

    public QuorumCertificate getHighQC(){
        Message highestView = null;
        for(Message m : getVotes().values()){
            if (highestView == null || highestView.getView() < m.getView() ){
                highestView = m;
            }
        }
        return highestView.getjustify();
    }

    public QuorumCertificate getPrepareQC(){
        return this.prepareQC;
    }

    public void setPrepareQC(QuorumCertificate qc){
        this.prepareQC = qc;
    }

    public QuorumCertificate getLockedQC(){
        return this.lockedQC;
    }

    public void setLockedQC(QuorumCertificate qc){
        this.lockedQC = qc;
    }

    public void setPendingVoteType(Type type){
        this.pendingVoteType = type;
    }

    public Type getPendingVoteType(){
        return this.pendingVoteType;
    }

    public void send(int receiverId, byte[] msg){
        try {
            //Debug.debug("At Node: " + getId() + "Sending : " + msg.toString() + " to " + getLeaderId());
            apl.send(receiverId, msg);
        } catch (Exception e) {
            System.err.print(e.getMessage());
        }
    }

    public void broadcast(byte[] msg){
        broadcastTo.forEach(receiverId -> {
            send(receiverId, msg);
        });
    }

    private int getId(){
        return this.id;
    }

    private ConcurrentHashMap<Integer, Message> getVotes(){
        return this.votes;
    }

    private int getView(){
        return this.view;
    }

    /*
    private static final Map<String, ThresholdSigEd25519Params> THRESHOLD_CONTEXTS =
            new ConcurrentHashMap<>();

    private final int nodeId;
    private final List<Integer> nodeIds;
    private final APL apl;
    private final ConsensusListener listener;
    private final ProposalReadyListener proposalReadyListener;
    private final Map<Integer, Integer> nodeIndexById;
    private final ThresholdSigEd25519 thresholdSig;
    private final ThresholdSigEd25519Params thresholdParams;
    private final Scalar localPrivateShare;
    private final byte[] committeePublicKey;

    private ViewChangeListener viewChangeListener;

    // State
    private long currentView = 0;
    private final Deque<Block> log = new ArrayDeque<>();
    private Block pendingBlock = null;

    // Highest QC and lock for safety
    private QuorumCertificate highQC = null;
    private QuorumCertificate lockedQC = null;

    // New-view messages received at start of a view
    private final Map<Long, Map<Integer, NewView>> newViewMessages = new ConcurrentHashMap<>();

    // Signed vote aggregation per phase and view
    private final Map<Long, Map<Integer, SignedVote>> prepareSignedVotes = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, SignedVote>> precommitSignedVotes = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, SignedVote>> commitSignedVotes = new ConcurrentHashMap<>();

    // Equivocation detection: node -> block hash voted in a given view
    private final Map<Long, Map<Integer, byte[]>> blockVotedInView = new ConcurrentHashMap<>();

    // Guards to avoid duplicate phase broadcasts for the same view
    private final Set<Long> precommitBroadcastedViews = ConcurrentHashMap.newKeySet();
    private final Set<Long> commitBroadcastedViews = ConcurrentHashMap.newKeySet();
    private final Set<Long> decideBroadcastedViews = ConcurrentHashMap.newKeySet();

    // Byzantine quorum: 2f+1 where f = floor((n-1)/3)
    private final int byzantineQuorumSize;
    private final int maxFaults;

    // Failure detection for leader crash handling
    private FailureDetector failureDetector;
    private Thread leaderMonitorThread;

    public HotStuffNode(int nodeId, List<Integer> nodeIds, APL apl,
                                 ConsensusListener listener, PrivateKey privateKey,
                                 Map<Integer, PublicKey> publicKeys) {
        this(nodeId, nodeIds, apl, listener, privateKey, publicKeys, null);
    }

    public HotStuffNode(int nodeId, List<Integer> nodeIds, APL apl,
                                 ConsensusListener listener, PrivateKey privateKey,
                                 Map<Integer, PublicKey> publicKeys, ProposalReadyListener proposalListener) {
        this.nodeId = nodeId;
        this.nodeIds = new ArrayList<>(nodeIds);
        Collections.sort(this.nodeIds);
        this.apl = apl;
        this.listener = listener;
        this.proposalReadyListener = proposalListener;

        int n = this.nodeIds.size();
        this.maxFaults = (n - 1) / 3;
        this.byzantineQuorumSize = 2 * maxFaults + 1;
        this.nodeIndexById = new HashMap<>();
        for (int i = 0; i < this.nodeIds.size(); i++) {
            this.nodeIndexById.put(this.nodeIds.get(i), i);
        }

        this.thresholdSig = new ThresholdSigEd25519(byzantineQuorumSize, n);
        String contextKey = thresholdContextKey(this.nodeIds, byzantineQuorumSize);
        this.thresholdParams = THRESHOLD_CONTEXTS.computeIfAbsent(contextKey, k -> {
            try {
                return thresholdSig.generate();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize threshold signature context", e);
            }
        });

        Integer localIndex = this.nodeIndexById.get(nodeId);
        if (localIndex == null) {
            throw new IllegalArgumentException("Node id " + nodeId + " not in membership list");
        }
        this.localPrivateShare = this.thresholdParams.getPrivateShares().get(localIndex);
        this.committeePublicKey = this.thresholdParams.getPublicKey();

        this.failureDetector = new TimeoutFailureDetector(nodeIds, 2000);

        log.add(Block.genesis());

        System.out.println("[" + nodeId + "] Byzantine HotStuff initialized: n=" + n +
                ", f=" + maxFaults + ", quorumSize=" + byzantineQuorumSize);
    }

    public void setFailureDetector(FailureDetector fd) {
        this.failureDetector = fd;
    }

    public void registerViewChangeListener(ViewChangeListener listener) {
        this.viewChangeListener = listener;
    }

    public void start() throws IOException {
        apl.registerListener(this);

        if (failureDetector != null) {
            failureDetector.start();
            for (int id : nodeIds) {
                failureDetector.heartbeat(id);
            }
        }

        startLeaderMonitor();

        if (nodeId == getLeader(currentView)) {
            System.out.println("[" + nodeId + "] I am the initial leader (view " + currentView +
                    "). Ready to propose.");
        } else {
            System.out.println("[" + nodeId + "] I am a replica (view " + currentView + ").");
        }
    }

    public void stop() {
        if (failureDetector != null) {
            failureDetector.stop();
        }
        if (leaderMonitorThread != null) {
            leaderMonitorThread.interrupt();
        }
    }

    private void startLeaderMonitor() {
        leaderMonitorThread = new Thread(() -> {
            while (Thread.currentThread().isAlive()) {
                try {
                    Thread.sleep(500);

                    if (failureDetector == null) {
                        break;
                    }

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
        }, "BHS-leader-monitor-" + nodeId);
        leaderMonitorThread.setDaemon(true);
        leaderMonitorThread.start();
    }

    private void triggerViewChange(int suspectedLeader) {
        currentView++;
        pendingBlock = null;
        clearViewState();

        System.out.println("[" + nodeId + "] Advanced to view " + currentView +
                " (leader " + suspectedLeader + " suspected). New leader: " +
                getLeader(currentView));

        if (viewChangeListener != null) {
            viewChangeListener.onViewChange(currentView, suspectedLeader);
        }

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

    public synchronized void propose(String data) throws IOException {
        if (nodeId != getLeader(currentView)) {
            throw new IllegalStateException("Only the leader can propose");
        }

        Block block = new Block(data);
        pendingBlock = block;

        Prepare msg = new Prepare(currentView, nodeId, block, highQC);
        broadcastConsensusMessage(msg);

        System.out.println("[" + nodeId + "] Proposed (prepare phase): " + msg);
    }

    @Override
    public void onMessage(int srcId, byte[] payload) {
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
        } else if (msg instanceof PreCommit) {
            handlePreCommit((PreCommit) msg);
        } else if (msg instanceof Commit) {
            handleCommit((Commit) msg);
        } else if (msg instanceof SignedVote) {
            handleSignedVote((SignedVote) msg);
        } else if (msg instanceof Decide) {
            handleDecide((Decide) msg);
        }
    }

    protected void handleNewView(NewView msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);

        if (msg.getView() < currentView) {
            return;
        }

        if (msg.getView() > currentView) {
            advanceToView(msg.getView());
        }

        if (nodeId != getLeader(currentView)) {
            return;
        }

        Map<Integer, NewView> views = newViewMessages.computeIfAbsent(msg.getView(), k -> new ConcurrentHashMap<>());
        boolean isNewSender = views.put(msg.getSenderId(), msg) == null;

        if (isNewSender && views.size() >= byzantineQuorumSize) {
            QuorumCertificate selectedQC = highQC;
            for (NewView nv : views.values()) {
                if (nv.getPrepareQC() != null) {
                    if (selectedQC == null || nv.getPrepareQC().getView() > selectedQC.getView()) {
                        selectedQC = nv.getPrepareQC();
                    }
                }
            }
            highQC = selectedQC;

            System.out.println("[" + nodeId + "] Collected quorum of new-view messages. Selected highQC: " +
                    (highQC != null ? highQC : "null"));
            onNewViewQuorum(msg.getView());
        }
    }

    protected void onNewViewQuorum(long view) {
        if (proposalReadyListener == null) {
            return;
        }
        if (!isCurrentLeader() || view != currentView) {
            return;
        }
        proposalReadyListener.onReadyToPropose();
    }

    protected void handlePrepare(Prepare msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);

        if (msg.getView() < currentView) {
            System.out.println("[" + nodeId + "] Ignoring prepare for old view " + msg.getView());
            return;
        }

        if (msg.getView() > currentView) {
            advanceToView(msg.getView());
        }

        QuorumCertificate justify = msg.getJustify();
        if (justify != null && !validateQC(justify)) {
            System.err.println("[" + nodeId + "] Rejected prepare: invalid QC signatures");
            return;
        }

        Block block = msg.getNode();
        boolean safe;
        if (lockedQC == null) {
            safe = true;
        } else if (justify != null && justify.getView() > lockedQC.getView()) {
            safe = true;
        } else {
            safe = justify != null && Arrays.equals(justify.getBlockHash(), lockedQC.getBlockHash());
        }

        if (!safe) {
            System.out.println("[" + nodeId + "] Rejected prepare (safeNode predicate failed)");
            return;
        }

        pendingBlock = block;

        try {
            SignedVote vote = createSignedVote(currentView, block.getHash(), "prepare");
            asyncSend(msg.getSenderId(), serializeMessage(vote));
            System.out.println("[" + nodeId + "] Queued prepare vote: " + vote);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to create signed vote: " + e.getMessage());
        }
    }

    protected void handlePreCommit(PreCommit msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);

        if (msg.getView() < currentView) {
            return;
        }

        if (msg.getView() > currentView) {
            advanceToView(msg.getView());
        }

        QuorumCertificate qc = msg.getPrepareQC();
        if (qc != null && !validateQC(qc)) {
            System.err.println("[" + nodeId + "] Rejected pre-commit: invalid prepare QC");
            return;
        }

        try {
            SignedVote vote = createSignedVote(currentView, msg.getPrepareQC().getBlockHash(), "precommit");
            asyncSend(msg.getSenderId(), serializeMessage(vote));
            System.out.println("[" + nodeId + "] Queued pre-commit vote: " + vote);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to create precommit vote: " + e.getMessage());
        }
    }

    protected void handleCommit(Commit msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);

        if (msg.getView() < currentView) {
            return;
        }

        if (msg.getView() > currentView) {
            advanceToView(msg.getView());
        }

        QuorumCertificate qc = msg.getPrecommitQC();
        if (qc != null && !validateQC(qc)) {
            System.err.println("[" + nodeId + "] Rejected commit: invalid precommit QC");
            return;
        }

        lockedQC = msg.getPrecommitQC();

        try {
            SignedVote vote = createSignedVote(currentView, msg.getPrecommitQC().getBlockHash(), "commit");
            asyncSend(msg.getSenderId(), serializeMessage(vote));
            System.out.println("[" + nodeId + "] Queued commit vote: " + vote +
                    " (now locked on view " + msg.getPrecommitQC().getView() + ")");
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to create commit vote: " + e.getMessage());
        }
    }

    private SignedVote createSignedVote(long view, byte[] blockHash, String phase) throws Exception {
        byte[] message = serializeForSignature(view, blockHash, phase);
        byte[] signature = sign(message);
        return new SignedVote(view, nodeId, blockHash, phase, signature);
    }

    private byte[] serializeForSignature(long view, byte[] blockHash, String phase) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        dos.writeLong(view);
        dos.write(blockHash);
        dos.write(phase.getBytes());

        dos.flush();
        return bos.toByteArray();
    }

    private byte[] sign(byte[] message) throws Exception {
        Scalar hash = hashToScalar(message);
        Scalar partialSignature = localPrivateShare.multiply(hash);
        return partialSignature.toByteArray();
    }

    private boolean verifySignature(SignedVote vote) {
        try {
            Integer signerIndex = nodeIndexById.get(vote.getSenderId());
            if (signerIndex == null) {
                System.err.println("[" + nodeId + "] Sender " + vote.getSenderId() + " is not part of membership");
                return false;
            }

            byte[] message = serializeForSignature(vote.getView(), vote.getBlockHash(), vote.getPhase());
            Scalar hash = hashToScalar(message);
            Scalar partialSignature = scalarFromBytes(vote.getSignature());
            if (partialSignature == null) {
                return false;
            }

            Scalar signerShare = thresholdParams.getPrivateShares().get(signerIndex);
            EdwardsPoint signerPublicShare = ThresholdSigEd25519.mulBasepoint(signerShare);

            EdwardsPoint lhs = ThresholdSigEd25519.mulBasepoint(partialSignature);
            EdwardsPoint rhs = signerPublicShare.multiply(hash);

            return Arrays.equals(lhs.compress().toByteArray(), rhs.compress().toByteArray());
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    private boolean validateQC(QuorumCertificate byzQC) {
        if (byzQC == null || byzQC.getSignedVotes().isEmpty()) {
            System.err.println("[" + nodeId + "] Invalid QC: no signed votes");
            return false;
        }

        int validVotes = 0;
        Set<Integer> signers = new HashSet<>();

        for (SignedVote vote : byzQC.getSignedVotes().values()) {
            if (!verifySignature(vote)) {
                System.err.println("[" + nodeId + "] QC validation failed: signature from node " +
                        vote.getSenderId() + " is invalid");
                continue;
            }

            if (!byzQC.getPhase().equals(vote.getPhase())) {
                System.err.println("[" + nodeId + "] QC validation failed: phase mismatch " +
                        byzQC.getPhase() + " vs " + vote.getPhase());
                continue;
            }

            if (!Arrays.equals(byzQC.getBlockHash(), vote.getBlockHash())) {
                System.err.println("[" + nodeId + "] QC validation failed: block hash mismatch");
                continue;
            }

            if (byzQC.getView() != vote.getView()) {
                System.err.println("[" + nodeId + "] QC validation failed: view mismatch");
                continue;
            }

            validVotes++;
            signers.add(vote.getSenderId());
        }

        if (signers.size() != byzQC.getSignedVotes().size()) {
            System.err.println("[" + nodeId + "] QC validation failed: duplicate signers detected");
            return false;
        }

        if (validVotes < byzantineQuorumSize) {
            System.err.println("[" + nodeId + "] QC validation failed: only " + validVotes +
                    " valid votes (need " + byzantineQuorumSize + ")");
            return false;
        }

        if (!byzQC.hasAggregatedSignature()) {
            System.err.println("[" + nodeId + "] QC validation failed: missing aggregated signature");
            return false;
        }
        if (!verifyAggregatedQCSignature(byzQC)) {
            System.err.println("[" + nodeId + "] QC validation failed: invalid aggregated signature");
            return false;
        }

        System.out.println("[" + nodeId + "] QC validated: " + validVotes + "/" + byzQC.getSignedVotes().size() +
                " votes valid");
        return true;
    }

    private boolean aggregateQCSignature(QuorumCertificate qc) {
        try {
            Set<Integer> signerIndexes = new HashSet<>();
            Map<Integer, Scalar> partialByIndex = new HashMap<>();

            for (SignedVote vote : qc.getSignedVotes().values()) {
                Integer index = nodeIndexById.get(vote.getSenderId());
                Scalar partial = scalarFromBytes(vote.getSignature());
                if (index == null || partial == null) {
                    return false;
                }
                signerIndexes.add(index);
                partialByIndex.put(index, partial);
            }

            if (signerIndexes.size() < byzantineQuorumSize) {
                return false;
            }

            List<Scalar> lagrange = ThresholdSigEd25519.getLagrangeCoef(nodeIds.size(), signerIndexes);
            Scalar aggregated = Scalar.ZERO;
            for (Integer index : signerIndexes) {
                Scalar coef = lagrange.get(index);
                Scalar partial = partialByIndex.get(index);
                aggregated = aggregated.add(partial.multiply(coef));
            }

            qc.setAggregatedSignature(aggregated.toByteArray());
            return true;
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to aggregate QC signature: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyAggregatedQCSignature(QuorumCertificate qc) {
        try {
            Scalar aggregated = scalarFromBytes(qc.getAggregatedSignature());
            if (aggregated == null) {
                return false;
            }

            byte[] message = serializeForSignature(qc.getView(), qc.getBlockHash(), qc.getPhase());
            Scalar hash = hashToScalar(message);

            EdwardsPoint lhs = ThresholdSigEd25519.mulBasepoint(aggregated);
            EdwardsPoint committeeKey = new CompressedEdwardsY(committeePublicKey).decompress();
            EdwardsPoint rhs = committeeKey.multiply(hash);

            return Arrays.equals(lhs.compress().toByteArray(), rhs.compress().toByteArray());
        } catch (Exception e) {
            return false;
        }
    }

    private Scalar hashToScalar(byte[] message) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(message);
        return Scalar.fromBytesModOrderWide(md.digest());
    }

    private Scalar scalarFromBytes(byte[] data) {
        if (data == null || data.length != 32) {
            return null;
        }
        try {
            return Scalar.fromBits(data);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleSignedVote(SignedVote vote) {
        if (!verifySignature(vote)) {
            System.err.println("[" + nodeId + "] Rejected vote with invalid signature from " +
                    vote.getSenderId());
            return;
        }

        Map<Integer, byte[]> votedInView = blockVotedInView.computeIfAbsent(vote.getView(),
                k -> new ConcurrentHashMap<>());
        byte[] previousVote = votedInView.get(vote.getSenderId());
        if (previousVote != null && !Arrays.equals(previousVote, vote.getBlockHash())) {
            System.err.println("[" + nodeId + "] EQUIVOCATION DETECTED: node " + vote.getSenderId() +
                    " voted for different blocks in view " + vote.getView());
            return;
        }
        votedInView.put(vote.getSenderId(), vote.getBlockHash());

        if ("prepare".equals(vote.getPhase())) {
            handlePrepareVote(vote);
        } else if ("precommit".equals(vote.getPhase())) {
            handlePreCommitVote(vote);
        } else if ("commit".equals(vote.getPhase())) {
            handleCommitVote(vote);
        } else {
            System.err.println("[" + nodeId + "] Unknown phase: " + vote.getPhase());
        }
    }

    private void handlePrepareVote(SignedVote vote) {
        if (nodeId != getLeader(currentView)) {
            return;
        }

        if (vote.getView() != currentView) {
            return;
        }

        if (pendingBlock == null) {
            return;
        }
        if (!Arrays.equals(vote.getBlockHash(), pendingBlock.getHash())) {
            System.out.println("[" + nodeId + "] Ignoring prepare vote for non-pending block from " +
                    vote.getSenderId());
            return;
        }

        Map<Integer, SignedVote> votes =
                prepareSignedVotes.computeIfAbsent(vote.getView(), k -> new ConcurrentHashMap<>());
        boolean isNewSender = votes.putIfAbsent(vote.getSenderId(), vote) == null;
        if (!isNewSender || votes.size() < byzantineQuorumSize) {
            return;
        }
        if (!precommitBroadcastedViews.add(vote.getView())) {
            return;
        }

        QuorumCertificate prepareQC =
                new QuorumCertificate(vote.getView(), "prepare", pendingBlock.getHash(), byzantineQuorumSize, votes);
        if (prepareQC.getVoteCount() < byzantineQuorumSize) {
            return;
        }
        if (!aggregateQCSignature(prepareQC)) {
            System.err.println("[" + nodeId + "] Failed to aggregate prepare QC signature");
            return;
        }

        PreCommit precommit = new PreCommit(vote.getView(), nodeId, prepareQC);
        broadcastConsensusMessage(precommit);
        System.out.println("[" + nodeId + "] Queued pre-commit broadcast: " + precommit);
    }

    private void handlePreCommitVote(SignedVote vote) {
        if (nodeId != getLeader(currentView)) {
            return;
        }

        if (vote.getView() != currentView) {
            return;
        }

        if (pendingBlock == null) {
            return;
        }
        if (!Arrays.equals(vote.getBlockHash(), pendingBlock.getHash())) {
            System.out.println("[" + nodeId + "] Ignoring pre-commit vote for non-pending block from " +
                    vote.getSenderId());
            return;
        }

        Map<Integer, SignedVote> votes =
                precommitSignedVotes.computeIfAbsent(vote.getView(), k -> new ConcurrentHashMap<>());
        boolean isNewSender = votes.putIfAbsent(vote.getSenderId(), vote) == null;
        if (!isNewSender || votes.size() < byzantineQuorumSize) {
            return;
        }
        if (!commitBroadcastedViews.add(vote.getView())) {
            return;
        }

        QuorumCertificate precommitQC =
                new QuorumCertificate(vote.getView(), "precommit", pendingBlock.getHash(), byzantineQuorumSize, votes);
        if (precommitQC.getVoteCount() < byzantineQuorumSize) {
            return;
        }
        if (!aggregateQCSignature(precommitQC)) {
            System.err.println("[" + nodeId + "] Failed to aggregate precommit QC signature");
            return;
        }

        Commit commit = new Commit(vote.getView(), nodeId, precommitQC);
        broadcastConsensusMessage(commit);
        System.out.println("[" + nodeId + "] Queued commit broadcast: " + commit);
    }

    private void handleCommitVote(SignedVote vote) {
        if (nodeId != getLeader(currentView)) {
            return;
        }

        if (vote.getView() != currentView) {
            return;
        }

        if (pendingBlock == null) {
            return;
        }
        if (!Arrays.equals(vote.getBlockHash(), pendingBlock.getHash())) {
            System.out.println("[" + nodeId + "] Ignoring commit vote for non-pending block from " +
                    vote.getSenderId());
            return;
        }

        Map<Integer, SignedVote> votes =
                commitSignedVotes.computeIfAbsent(vote.getView(), k -> new ConcurrentHashMap<>());
        boolean isNewSender = votes.putIfAbsent(vote.getSenderId(), vote) == null;
        if (!isNewSender || votes.size() < byzantineQuorumSize) {
            return;
        }
        if (!decideBroadcastedViews.add(vote.getView())) {
            return;
        }

        QuorumCertificate commitQC =
                new QuorumCertificate(vote.getView(), "commit", pendingBlock.getHash(), byzantineQuorumSize, votes);
        if (commitQC.getVoteCount() < byzantineQuorumSize) {
            return;
        }
        if (!aggregateQCSignature(commitQC)) {
            System.err.println("[" + nodeId + "] Failed to aggregate commit QC signature");
            return;
        }

        highQC = commitQC;

        Decide decide = new Decide(vote.getView(), nodeId, commitQC);
        broadcastConsensusMessage(decide);
        System.out.println("[" + nodeId + "] Queued decide broadcast: " + decide);
        commitBlock(vote.getView());
    }

    protected void handleDecide(Decide msg) {
        System.out.println("[" + nodeId + "] Received: " + msg);

        if (msg.getView() < currentView) {
            return;
        }

        QuorumCertificate qc = msg.getCommitQC();
        if (qc != null && !validateQC(qc)) {
            System.err.println("[" + nodeId + "] Rejected decide: invalid commit QC");
            return;
        }

        if (qc != null) {
            highQC = qc;
        }

        commitBlock(msg.getView());
    }

    protected void commitBlock(long view) {
        if (pendingBlock != null) {
            log.add(pendingBlock);
            if (listener != null) {
                listener.onCommit(pendingBlock);
            }
            System.out.println("[" + nodeId + "] Committed block: " + pendingBlock);
        }

        currentView = Math.max(currentView, view) + 1;
        pendingBlock = null;
        clearViewState();

        System.out.println("[" + nodeId + "] Advanced to view " + currentView +
                ". New leader: " + getLeader(currentView));

        try {
            sendNewView();
        } catch (IOException e) {
            System.err.println("[" + nodeId + "] Failed to send new-view: " + e.getMessage());
        }
    }


    private void advanceToView(long view) {
        currentView = view;
        pendingBlock = null;
        clearViewState();
    }

    private static String thresholdContextKey(List<Integer> members, int threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("t=").append(threshold).append(";n=").append(members.size()).append(";members=");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(members.get(i));
        }
        return sb.toString();
    }

    public int getLeader(long view) {
        return (int) (view % nodeIds.size());
    }

    protected void broadcastConsensusMessage(HotStuffMessage msg) {
        byte[] payload = serializeMessage(msg);
        for (int id : nodeIds) {
            if (id == nodeId) {
                continue;
            }
            asyncSend(id, payload);
        }
    }

    protected void asyncSend(int dest, byte[] payload) {
        new Thread(() -> {
            try {
                apl.send(dest, payload);
            } catch (IOException e) {
                System.err.println("[" + nodeId + "] async send to " + dest +
                        " failed: " + e.getMessage());
            }
        }, "BHS-send-" + nodeId + "-to-" + dest + "-" + System.nanoTime()).start();
    }

    protected byte[] serializeMessage(HotStuffMessage msg) {
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

    public boolean isCurrentLeader() {
        return nodeId == getLeader(currentView);
    }

    protected int getQuorumSize() {
        return byzantineQuorumSize;
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

    protected void clearViewState() {
        newViewMessages.clear();
        prepareSignedVotes.clear();
        precommitSignedVotes.clear();
        commitSignedVotes.clear();
        blockVotedInView.clear();
        precommitBroadcastedViews.clear();
        commitBroadcastedViews.clear();
        decideBroadcastedViews.clear();
    }
*/
}
