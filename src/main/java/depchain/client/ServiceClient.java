package depchain.client;

import depchain.Debug;
import depchain.network.APLListener;
import depchain.network.AuthenticatedPerfectLinksImpl;
import depchain.service.ServiceMessage;

import java.io.IOException;
import java.util.LinkedList;

/**
 * Client
 */
public class ServiceClient implements APLListener{

    private final int clientId;
    
    private final AuthenticatedPerfectLinksImpl apl;
    
    private final int targetService;

    private int counterRequests = 0;

    private LinkedList<Integer> pending;
    
    public ServiceClient(int clientId, AuthenticatedPerfectLinksImpl apl, int targetService) {
        this.clientId = clientId;
        this.apl = apl;
        this.targetService = targetService;
    }
    
    public void sendRequest(String data) {
        int requestId = nextRequestId();

        ServiceMessage msg = new ServiceMessage(requestId, data);

        byte[] payload = msg.serialize();
        try{
            apl.send(targetService, payload);
        }
        catch(IOException e){
            System.err.println(e);
        }

        pending.add(requestId);

        Debug.debug("[Client-" + clientId + "] Submitted: \"" + data + "\"");
    }


    public void onMessage(int senderId, byte[] data){

        ServiceMessage reply = ServiceMessage.deserialize(data);
        if (reply != null) {
            int replyId = reply.getRequestId();

            if(pending.remove(Integer.valueOf(replyId))){
                Debug.debug("The request was appended succesfully");
            }
            else{
                Debug.debug("Unsolicited message!");
            }

            return;
        }
    }

    private int nextRequestId() {
        return this.counterRequests++;
    }
}
