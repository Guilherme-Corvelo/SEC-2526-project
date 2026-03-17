package depchain.client;

import depchain.consensus.HotStuffMessage;
import depchain.network.APLListener;
import depchain.network.AuthenticatedPerfectLinksImpl;
import depchain.service.AppendRequest;
import depchain.service.AppendResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client
 */
public class ServiceClient implements APLListener{
    private final int clientId;
    private final AuthenticatedPerfectLinksImpl apl;
    //private final int targetService;
    private final int[] services;

    private final AtomicInteger counterRequests = new AtomicInteger(1);

    private final Map<Long, AppendRequest> pending = new ConcurrentHashMap<>();

    private Map<Long, Integer> confirmingRequest = new ConcurrentHashMap<>();

    private int f;

    
    public ServiceClient(int clientId, AuthenticatedPerfectLinksImpl apl, int[] services, int f) {
        this.clientId = clientId;
        this.apl = apl;
        this.services = services;
        this.f=f;
    }
    
    public void submitMessageBlockService(String data) {
        long requestId = nextRequestId();

        byte[] payload = AppendRequest.encode(requestId, clientId, data);

        //this so the leader eventually responds...
        for (int service : services) {
            try {
                apl.send(service, payload);
            } catch (IOException e) {
                System.err.println("Error sending to service " + service);
            }
        }

        pending.put(requestId, null);

        System.out.println("[Client-" + clientId + "] Submitted: \"" + data + "\"");

        //awaitMessageResponse(requestId);
    }

    //public void awaitMessageResponse(long requestId){

    //}

    public void onMessage(int senderId, byte[] data){

        AppendResponse reply = AppendResponse.tryDecode(data);
        if (reply != null) {
            handleServerReply(reply);
            return;
        }
        
    }

    public void handleServerReply(AppendResponse reply){
        if(checkNumberReply(reply)){
            return;
        }
        //probably check every reply

        System.out.println("The request was appended succesfully");

    }
     

    private Boolean checkNumberReply(AppendResponse reply){
        long replyID= reply.getRequestId();

        confirmingRequest.merge(replyID, 1, Integer::sum);

        if(confirmingRequest.get(replyID)>f+1){
            return true;
        }
        return false;
    }


    /**
     * Submit a string to be appended to the consensus blockchain.
     * 
     * Phase 1: Fire-and-forget submission.
     * The consensus layer will handle ordering (via ByzantineHotStuffNode)
     * and commitment (via leader election and QC collection).
     */
    /*
    public void submitAppendRequest(String data) throws IOException {
        // For phase 1, just send the data as bytes via APL
        byte[] payload = data.getBytes();
        apl.send(targetService, payload);
        System.out.println("[Client-" + clientId + "] Submitted: \"" + data + "\"");
    }
    */

    private long nextRequestId() {
        long counter = counterRequests.getAndIncrement() & 0xFFFF_FFFFL;
        return (((long) clientId) << 32) | counter;
    }
}
