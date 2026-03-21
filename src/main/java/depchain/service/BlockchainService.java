package depchain.service;

import depchain.Debug;
import depchain.API.Request;
import depchain.consensus.Message;
import depchain.network.APLListener;
import depchain.replica.Replica;
import depchain.replica.ReplicaListener;
import depchain.network.APL;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blockchain service
*/
public class BlockchainService implements ReplicaListener {
    private AppendOnlyLog log;
    private Replica replica;
    private Queue<Request> requestQueue = new LinkedList<>();
    private int f = 0;
    
    public BlockchainService(Replica replica) throws IOException{
        this.log = new AppendOnlyLog();
        this.replica = replica;
    }
    
    /* 
    public BlockchainService(int serviceId, APL apl, int serverID, int f) {
        this.serviceId = serviceId;
        this.apl = apl;
        this.log = new AppendOnlyLog();
        this.serverID= serverID;
        this.f=f;
    }

    @Override
    public void onCommit(Block block){
        String data = block.getData();
        int index = log.append(data);
        System.out.println("[BlockchainService] Committed entry " + index + ": \"" + data + "\"");
    }
    */
    @Override
    public void onMessage(int senderId, byte[] data) {
        Request request = Request.deserialize(data);
        if (request != null) {
            Debug.debug( "Sender ID : "+ senderId +  "received request : " + request.toString());
            handleRequest(senderId, request);
            return;
        }

        /*
        Message decideMessage = Message.deserialize(data);
        if (decideMessage != null) {
            handleServer(senderId, request);
        }
        */
    }

    private void handleRequest(int clientId, Request request) {
        requestQueue.add(request);
        Request reply = new Request(request.getRequestId(), true);
        Debug.debug("sending reply : " + reply.toString());
        replica.blockingSend(clientId, reply.serialize());
        //replica.broadcast(request.serialize());
    }

    /* 
    private void handleServerReply(Request reply) {

        if(checkNumberReply(reply)){
            return;
        }
        
        Integer clientId = pendingClientByRequestId.remove(reply.getRequestId());
        //suspeito
        if (clientId == null) {
            // If the mapping is absent, fall back to the client id serialized in the request id.
            clientId = (int) (reply.getRequestId() >>> 32);
        }

        asyncSend(clientId, reply.serialize());
        
        //int index = log.append(reply.getBlock().getData());
        //System.out.println("[BlockchainService] Committed entry " + index + ": \"" + reply.getBlock().getData() + "\"");
    }
    
    private void asyncSend(int destId, byte[] payload) {
        new Thread(() -> {
            try {
                apl.send(destId, payload);
            } catch (IOException ignored) {
            }
        });
    }

    /**
     * Get an entry from the committed log by index.
     */
    public String getEntry(int index) {
        return log.get(index);
    }
    
    /**
     * Get all committed entries.
     */
    public List<String> getAllEntries() {
        return log.getAll();
    }
    
    /**
     * Get the total number of committed entries.
     */
    public int getLogSize() {
        return log.size();
    }
    /*
    private Boolean checkNumberReply(Request reply){
        long replyID= reply.getRequestId();

        confirmingRequest.merge(replyID, 1, Integer::sum);

        if(confirmingRequest.get(replyID)>f+1){
            return true;
        }
        return false;
    }
    */
}
