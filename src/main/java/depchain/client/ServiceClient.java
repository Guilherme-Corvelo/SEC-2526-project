package depchain.client;

import depchain.Debug;
import depchain.network.APLListener;
import depchain.network.APL;
import depchain.service.ServiceMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.Map;

/**
 * Client
 */
public class ServiceClient implements APLListener{
    private APL apl;
    private int targetService;
    private int counterRequests = 0;
    private LinkedList<Integer> pending = new LinkedList<>();
    
    public ServiceClient(int clientId,
                    int port, Map<Integer, InetSocketAddress> serviceAddress,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys) throws IOException{

        this.apl = new APL(clientId, port, serviceAddress, privateKey, publicKeys);
        this.apl.registerListener(this);
        this.targetService = serviceAddress.keySet().iterator().next();
        this.apl.start();
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

        Debug.debug("[Client-" + "] Submitted: \"" + data + "\"");
    }

    public void onMessage(int senderId, byte[] data){
        ServiceMessage reply = ServiceMessage.deserialize(data);
        Debug.debug("Client received msg: " + reply.toString());
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
