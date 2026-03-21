package depchain.client;

import depchain.Debug;
import depchain.API.DepchainAPI;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

public class Client{
    private DepchainAPI depchain;
    
    public Client(int clientId,
                    int port, Map<Integer, InetSocketAddress> serviceAddress,
                    PrivateKey privateKey, Map<Integer, PublicKey> publicKeys, int f) throws IOException{

        this.depchain = new DepchainAPI(clientId, port, serviceAddress, privateKey, publicKeys,f);
    }

    public void send(String action){
        Debug.debug("Client Sent request to append" + action);
        depchain.append(action);
        Debug.debug("Client received response");
    }
}
