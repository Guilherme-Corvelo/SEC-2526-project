package depchain.network;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import depchain.Debug;
import depchain.client.Client;
import depchain.replica.Replica;
import depchain.service.BlockchainService;

public class ClientServiceTest {
    private int portA = 20000;
    private int portB = 20001;
    Map<Integer, InetSocketAddress> Clientaddresses = new HashMap<>();
    Map<Integer, InetSocketAddress> Serviceaddresses = new HashMap<>();
    
    private KeyPair keysA;
    private KeyPair keysB;
    Client client;
    Replica replica;
    BlockchainService service;
    @BeforeEach
    void setup() throws Exception {
        // generate RSA key pairs
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keysA = gen.generateKeyPair();
        keysB = gen.generateKeyPair();

        Serviceaddresses.put(0, new InetSocketAddress("localhost", portA));
        Clientaddresses.put(1, new InetSocketAddress("localhost", portB));

        Map<Integer, PublicKey> pubKeys = new HashMap<>();
        pubKeys.put(0, keysA.getPublic());
        pubKeys.put(1, keysB.getPublic());

        client = new Client(0, portA, Clientaddresses, keysA.getPrivate(), pubKeys);

        replica = new Replica(1, portB, Serviceaddresses, keysB.getPrivate(), pubKeys);
        service = new BlockchainService(replica);
        replica.setListener(service);
        replica.start();
    }
    
    //TODO:Make threads end before test
    @Test
    void clientServiceRequest (){
        client.sendRequest("Test 1");
        Debug.debug("Sent and didnt block");
    }
}
