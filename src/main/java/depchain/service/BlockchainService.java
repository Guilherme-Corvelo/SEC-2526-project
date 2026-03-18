package depchain.service;

import depchain.consensus.Block;
import depchain.consensus.ConsensusListener;
import depchain.network.APLListener;
import depchain.network.AuthenticatedPerfectLinksImpl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blockchain service
*/
public class BlockchainService implements APLListener, ConsensusListener {
    private final AppendOnlyLog log;
    private final int serviceId;
    private final AuthenticatedPerfectLinksImpl apl;
    private int serverID;

    private final Map<Integer, Integer> pendingClientByRequestId = new ConcurrentHashMap<>();

    private Map<Long, Integer> confirmingRequest = new ConcurrentHashMap<>();

    private int f=0;
    
    //Remove Later the constructor below
    public BlockchainService() {
        this.serviceId = 0;
        this.apl = null;
        this.log = null;
        this.serverID= 0;
        this.f=0;
    }
    public BlockchainService(int serviceId, AuthenticatedPerfectLinksImpl apl, int serverID, int f) {
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

    @Override
    public void onMessage(int senderId, byte[] data) {
        /*
        if (looksLikeJavaSerializedObject(data)) {
            return;
        }
        */

        ServiceMessage reply = ServiceMessage.deserialize(data);
        if (reply != null) {
            handleServerReply(reply);
            return;
        }

        ServiceMessage request = ServiceMessage.deserialize(data);
        if (request != null) {
            handleClientRequest(senderId, request);
        }
    }

    private void handleClientRequest(int clientId, ServiceMessage request) {
        pendingClientByRequestId.put(request.getRequestId(), clientId);
        byte[] forwarded = request.serialize();
        asyncSend(serverID, forwarded);
    }

    private void handleServerReply(ServiceMessage reply) {

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

    private Boolean checkNumberReply(ServiceMessage reply){
        long replyID= reply.getRequestId();

        confirmingRequest.merge(replyID, 1, Integer::sum);

        if(confirmingRequest.get(replyID)>f+1){
            return true;
        }
        return false;
    }
}
