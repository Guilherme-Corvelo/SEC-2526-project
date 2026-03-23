package depchain.API;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import depchain.Debug;
import depchain.consensus.Message;
import depchain.network.APL;
import depchain.network.APLListener;

public class DepchainAPI implements APLListener{
    private APL apl;
    private LinkedList<Integer> broadcastTo;
    private Set<Integer> receivedResponses = new HashSet<>();
    private int f;
    private final Object lock = new Object();

    public DepchainAPI(int id, 
                    int port, Map<Integer, InetSocketAddress> addresses,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys, int f) throws IOException{

        this.apl = new APL(id, port, addresses, privateKey, publicKeys);
        this.broadcastTo = new LinkedList<Integer>(addresses.keySet());
        this.apl.registerListener(this);
        this.f = f;
        this.apl.start();

        Debug.debug("DepchainAPI was initialized\n");
    }

    public void onMessage(int senderId, byte[] payload){
        Debug.debug( "Client Received Message");
        Message message = Message.deserialize(payload);
        if (message != null) {
            handleResponse(senderId, message);
            return;
        }
    }

    //TODO:THINK about new type for hotstuff response
    public void handleResponse(int senderId, Message Message){
        synchronized(lock) {
            receivedResponses.add(senderId);
            Debug.debug("API RECEIVED: " + receivedResponses.size() + " " + Message.toString());

            if (receivedResponses.size() >= this.f + 1) {
                lock.notifyAll();
            }
        }
        
    }

    public void append(String action) {
        synchronized(lock) {
            this.receivedResponses.clear();

            Request request = new Request(action);
            broadcast(request.serialize());

            //Maybe timeout?
            while ((receivedResponses.size() < this.f + 1) ) { 
                try {
                    lock.wait();
                } catch (Exception e) {
                    // TODO: handle exception
                    Thread.currentThread().interrupt();
                    return;
                }
            }

        }

        System.out.println( action + " was appended successfully");
    }

    public void send(int receiverId, byte[] msg){
        try {
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
}
