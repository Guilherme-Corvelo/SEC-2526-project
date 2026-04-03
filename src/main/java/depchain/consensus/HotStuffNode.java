package depchain.consensus;

import depchain.network.APLListener;
import depchain.Debug;
import depchain.API.Request;
import depchain.blockchain.BlockProcessor;
import depchain.blockchain.BlockStorage;
import depchain.blockchain.EVMExecutorService;
import depchain.blockchain.Transaction;
import depchain.network.APL;
import threshsig.GroupKey;
import threshsig.KeyShare;
import threshsig.SigShare;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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
    private int numNodes;
    private Type pendingVoteType = Type.NEWVIEW;
    private ConcurrentHashMap<Integer, Message> votes = new ConcurrentHashMap<>();
    
    private Thread viewTimerThread = null;
    private boolean timerCancelled = false;
    private static final long TIMEOUT_MS = 5000;
    private static final long BLOCK_GAS_LIMIT = 1_000_000L;
    private static final long BLOCK_ASSEMBLY_TIMEOUT_MS = 2_000L;
    private volatile static ThresholdContext THRESHOLD_CONTEXT = null;
    private final BlockProcessor blockProcessor;
    private final List<Transaction> pendingTransactions = new ArrayList<>();
    private long pendingGas = 0L;
    private Thread assemblyTimerThread = null;
    private boolean assemblyTimerCancelled = false;
    private final GroupKey groupKey;
    private final KeyShare localShare;
    private final int thresholdK;

    public HotStuffNode(int id, 
                    int port, Map<Integer, InetSocketAddress> addresses,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys, int f, LinkedList<Integer> broadcastTo) throws IOException{

        this.apl = new APL(id, port, addresses, privateKey, publicKeys);
        this.apl.registerListener(this);
        this.broadcastTo = broadcastTo;
        this.numNodes = broadcastTo.size();
        this.f = f;
        this.id = id;
        this.apl.start();
        this.thresholdK = this.numNodes - this.f;

        createThresholdContext(this.thresholdK, numNodes);
  
        this.groupKey = THRESHOLD_CONTEXT.getGroupKey();
        this.localShare = THRESHOLD_CONTEXT.getShare(broadcastTo.indexOf(getId()));

        EVMExecutorService evm = new EVMExecutorService();
        BlockStorage storage = new BlockStorage("blocks/node_" + id + "/", "genesis.json");
        this.blockProcessor = new BlockProcessor(evm, storage);
        this.blockProcessor.startup();
    }

    private static void createThresholdContext (int threshold, int totalNum){
        if (THRESHOLD_CONTEXT == null) {
            synchronized (ThresholdContext.class) {
                if (THRESHOLD_CONTEXT == null) {
                    THRESHOLD_CONTEXT = new ThresholdContext(threshold, totalNum);
                }
            }
        }
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
        if (request.getTransaction() == null) {
            return;
        }
        enqueueForAssembly(senderId, request.getTransaction());
    }

    private synchronized void enqueueForAssembly(int requesterId, Transaction transaction) {
        pendingTransactions.add(transaction);
        pendingGas += Math.max(0L, transaction.getGasLimit());

        if (assemblyTimerThread == null || !assemblyTimerThread.isAlive()) {
            startAssemblyTimer(requesterId);
        }

        if (pendingGas >= BLOCK_GAS_LIMIT) {
            cancelAssemblyTimer();
            proposePendingTransactions(requesterId);
        }
    }

    private synchronized void startAssemblyTimer(int requesterId) {
        this.assemblyTimerCancelled = false;
        this.assemblyTimerThread = new Thread(() -> {
            try {
                Thread.sleep(BLOCK_ASSEMBLY_TIMEOUT_MS);
                synchronized (this) {
                    if (!assemblyTimerCancelled && !pendingTransactions.isEmpty()) {
                        proposePendingTransactions(requesterId);
                    }
                }
            } catch (InterruptedException ignored) {
                //
            }
        });
        this.assemblyTimerThread.setDaemon(true);
        this.assemblyTimerThread.start();
    }

    private synchronized void cancelAssemblyTimer() {
        this.assemblyTimerCancelled = true;
        if (this.assemblyTimerThread != null && this.assemblyTimerThread.isAlive()) {
            this.assemblyTimerThread.interrupt();
        }
        this.assemblyTimerThread = null;
    }

    private synchronized void proposePendingTransactions(int requesterId) {
        if (pendingTransactions.isEmpty()) {
            return;
        }

        List<Transaction> proposedTransactions = new ArrayList<>(pendingTransactions);
        pendingTransactions.clear();
        pendingGas = 0L;

        Node requestNode = new Node(proposedTransactions, latestNode);
        Message newViewMessage = new Message(Type.NEWVIEW, getView(), requestNode, requesterId);

        Debug.debug("At Node: " + getId() + " Assembled block candidate with " +
            proposedTransactions.size() + " txs and sent NEWVIEW to leader " + getLeaderId());
        send(getLeaderId(), newViewMessage.serialize());

        if (getId() != getLeaderId()) {
            setPendingVoteType(Type.PREPARE);
        }
        startViewTimer(newViewMessage);
    }

    public synchronized void handleMessage(int senderId, Message msg){
        if (msg.getView() != getView()) {
            return;
        }

        if(senderId==getLeaderId()){
            cancelViewTimer();
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

                BigInteger thresholdSig = combineVotes();
                if (thresholdSig == null) {
                    return;
                }
                QuorumCertificate highQC = new QuorumCertificate(Type.PREPARE, getView(), msg.getNode(), thresholdSig);
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

                if (validateQC(msg.getjustify())) {
                    setPrepareQC(msg.getjustify());
                    msg.vote(localShare);

                    Debug.debug( "At Node: " + getId() + " Sent Prepare message :" + msg.toString() + " to leader: " + getLeaderId());
                    
                    send(getLeaderId(), msg.serialize());
                    
                    if (getId() != getLeaderId()){ setPendingVoteType(Type.PRECOMMIT);}
                    startViewTimer(msg);
                }                
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

                BigInteger thresholdSig = combineVotes();
                if (thresholdSig == null) {
                    return;
                }
                QuorumCertificate highQC = new QuorumCertificate(Type.PRECOMMIT, getView(), msg.getNode(), thresholdSig);
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
            if (validateQC(msg.getjustify())) {
                setPrepareQC(msg.getjustify());
                msg.vote(localShare);
                setLockedQC(msg.getjustify());

                Debug.debug( "At Node: " + getId() + " Sent Precommit message :" + msg.toString() + " to leader: " + getLeaderId());
                send(getLeaderId(), msg.serialize());
                
                if (getId() != getLeaderId()){ setPendingVoteType(Type.COMMIT);}
                startViewTimer(msg);
            }
            
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

                BigInteger thresholdSig = combineVotes();
                if (thresholdSig == null) {
                    return;
                }
                QuorumCertificate highQC = new QuorumCertificate(Type.COMMIT, getView(), msg.getNode(), thresholdSig);
                decideMsg.setJustify(highQC);

                setPendingVoteType(Type.DECIDE);
                getVotes().clear();

                Debug.debug( "Leader: " + getId() + " Broadcast Decide message :" + decideMsg.toString());

                broadcast(decideMsg.serialize());
            }
        }
        if(senderId == getLeaderId() && msg.getPartialSign() == null){
            //TODO: Think aboiut teshdold signs
            if (validateQC(msg.getjustify())) {
                msg.vote(localShare);

                Debug.debug( "At Node: " + getId() + " Sent Commit message :" + msg.toString() + " to leader: " + getLeaderId());
                send(getLeaderId(), msg.serialize());           

                if (getId() != getLeaderId()){ setPendingVoteType(Type.DECIDE);}
                startViewTimer(msg);
            }            
        }

    }

    public void handleDecideMessage(int senderId, Message msg){
        //TODO: Think aboiut teshdold signs
        //TODO: (VALIDATE QC???)
        latestNode = msg.getNode();
        msg.setExecutionResults(blockProcessor.processBlockWithResults(latestNode.getProposedTransactions()));

        Debug.debug( "At Node: " + getId() + " Sent Decide message :" + msg.toString() + "to client " + msg.getRequesterId());

        send(msg.getRequesterId(), msg.serialize());        

        if (getId() != getLeaderId()){ setPendingVoteType(Type.NEWVIEW);}
    }

    //Think about quorum certificate not starting at null, having qc initialized with genesis node
    private boolean safeNode(Node node, QuorumCertificate qc){
        return getLockedQC() == null || node.canExtend(getLockedQC().getNode()) || qc.getView() > getLockedQC().getView();
    }
    
    private int getLeaderId(){
        return broadcastTo.get(view%numNodes);
    }

    private BigInteger combineVotes(){
        int counter = 0;
        SigShare[] sigShares = new SigShare[thresholdK];
        for (Message msg : getVotes().values()) {
            sigShares[counter] = msg.getPartialSign();;
            counter++;
        }

        return SigShare.combineSigs(sigShares, thresholdK, numNodes, groupKey.getModulus());
    }

    private boolean enoughVotes(){
        return getVotes().size() >= thresholdK;
    }

    private synchronized void startViewTimer(Message lastMessage) {
        Debug.debug("New timer started for node " + getId());

        int timedOutView = this.view;
        this.timerCancelled = false;

        this.viewTimerThread = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_MS);
                synchronized (this) {
                    if (!timerCancelled && this.view == timedOutView) {
                        onViewTimeout(lastMessage);
                    }
                }
            } catch (InterruptedException e) {
                // do nothing
            }
        });
        this.viewTimerThread.setDaemon(true);
        this.viewTimerThread.start();
    }

    private synchronized void cancelViewTimer() {
        this.timerCancelled = true;
        if (this.viewTimerThread != null && this.viewTimerThread.isAlive()) {
            this.viewTimerThread.interrupt();
            this.viewTimerThread = null;
        }
    }

    private void onViewTimeout(Message lastMessage) {
        Debug.debug("Node " + getId() + " timed out in view " + view +
                    ". Leader " + getLeaderId() + " suspected. Moving to view " + (view + 1));

        this.view++;
        this.votes.clear();
        this.pendingVoteType = Type.NEWVIEW;

        Message newViewMsg = new Message(Type.NEWVIEW, getView(), latestNode, lastMessage.getRequesterId());
        newViewMsg.setJustify(prepareQC);

        Debug.debug("Node " + getId() + " sending new-view to new leader " + getLeaderId());
        send(getLeaderId(), newViewMsg.serialize());

        startViewTimer(newViewMsg);
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

    public boolean validateQC(QuorumCertificate qc) {
        if (qc == null) {
            return true;
        }
        if (qc.getSignature() == null) {
            return false;
        }

        byte[] typeData = qc.getType().name().getBytes(StandardCharsets.UTF_8);
        byte[] viewData = ByteBuffer.allocate(Integer.BYTES).putInt(qc.getView()).array();
        byte[] nodeData = qc.getNode().serialize();
        byte[] data = ByteBuffer.allocate(typeData.length + viewData.length + nodeData.length)
                        .put(typeData).put(nodeData).put(viewData).array(); 

        return SigShare.verifySig(data, thresholdK, numNodes, groupKey.getModulus(), groupKey.getExponent(), qc.getSignature());
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
}
