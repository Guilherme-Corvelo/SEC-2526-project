package depchain.replica;

import depchain.Debug;
import depchain.network.APLListener;
import depchain.network.APL;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.Map;


/**
 * Replica: A blockchain replica that handles both consensus and client requests.
 *
 * This class integrates:
 * - Byzantine consensus protocol (HotStuffNode)
 * - Client data buffering and processing
 * - Blockchain service for committed blocks
 *
 * When client data arrives, it's buffered. When this replica becomes leader
 * and collects a quorum of new-view messages, it automatically proposes a block
 * containing all buffered client data.
 */
public class Replica implements APLListener{
    private final APL apl;
    private ReplicaListener listener = null;
    private LinkedList<Integer> broadcastTo;

    public Replica(int replicaId, 
                    int port, Map<Integer, InetSocketAddress> addresses,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys){

        this.apl = new APL(replicaId, port, addresses, privateKey, publicKeys);
        this.apl.registerListener(this);
        this.broadcastTo = new LinkedList<Integer>(addresses.keySet());

        Debug.debug("Replica was initialized\n");
    }

    public Replica(int replicaId, 
                    int port, Map<Integer, InetSocketAddress> addresses,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys,
                    LinkedList<Integer> broadcastTo){

        this.apl = new APL(replicaId, port, addresses, privateKey, publicKeys);
        this.broadcastTo = broadcastTo;

        Debug.debug("Replica was initialized\n");
    }

    public void blockingSend(int receiverId, byte[] msg){
        try {
            apl.send(receiverId, msg);
        } catch (Exception e) {
            System.err.print(e.getMessage());
        }
    }

    //TODO:WHy no work
    public void send(int receiverId, byte[] msg){
        new Thread(() -> {
            blockingSend(receiverId, msg);
        });
    }

    public void  broadcast(byte[] msg){
        broadcastTo.forEach(receiverId -> {
            send(receiverId, msg);
        });
    }

    @Override
    public void onMessage(int senderId, byte[] data) {
        Debug.debug("Replica received msg: " + data.toString());
        listener.onMessage(senderId, data);
    }

    public void setListener(ReplicaListener listener) {
        this.listener = listener;
    }
    public void start() throws IOException{
        apl.start();
    }
    /*
    private final Queue<String> clientDataBuffer;
    private volatile long readyView;

    public Replica(int replicaId, List<Integer> allReplicaIds, APL apl,
                   PrivateKey privateKey, Map<Integer, PublicKey> publicKeys) {
        this.replicaId = replicaId;
        this.clientDataBuffer = new ConcurrentLinkedQueue<>();
        this.readyView = -1;

        // Create blockchain service
        this.blockchainService = new BlockchainService();

        // Create consensus node with this replica as listener
        this.consensusNode = new HotStuffNode(
            replicaId, allReplicaIds, apl, this, privateKey, publicKeys, this
        );

        // Register this replica as APL listener to receive client data
        apl.registerListener(this);

        System.out.println("[Replica-" + replicaId + "] Initialized");
    }

    
     //Called by APL when client data arrives.
    //Buffers the data for later proposal when this replica becomes leader.
    


    
     //Called by consensus layer when a block is committed.
     // Forwards to blockchain service for persistence.
     
    @Override
    public void onCommit(Block block) {
        blockchainService.onCommit(block);
    }

    
     /Called when this replica becomes ready to propose (after collecting new-view quorum).
    //If we have buffered client data, propose it now.
    
    public void onReadyToPropose() {
        readyView = consensusNode.getCurrentView();
        System.out.println("[Replica-" + replicaId + "] Ready to propose blocks");

        if (isCurrentLeader() && isReadyForCurrentView() && !clientDataBuffer.isEmpty()) {
            proposeBufferedData();
        }
    }

    
    //Propose all buffered client data as a single block.
    
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
            // Consume readiness for this view: this simple model proposes one block per ready view.
            readyView = -1;
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

    private boolean isReadyForCurrentView() {
        return readyView == consensusNode.getCurrentView();
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
    public HotStuffNode getConsensusNode() {
        return consensusNode;
    }
    */


}
