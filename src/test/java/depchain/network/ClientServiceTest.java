package depchain.network;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import depchain.Debug;
import depchain.blockchain.Transaction;
import depchain.client.Client;
import depchain.consensus.HotStuffNode;


public class ClientServiceTest {
    private int portA = 20000;
    private int portB = 20001;
    private int portC = 20002;
    private int portD = 20003;

    Map<Integer, InetSocketAddress> NodeAddresses = new HashMap<>();
    Map<Integer, InetSocketAddress> Clientaddresses = new HashMap<>();
    Map<Integer, InetSocketAddress> Addresses = new HashMap<>();
    
    private KeyPair keysA;
    private KeyPair keysB;
    private KeyPair keysC;
    private KeyPair keysD;
    Client client;
    private int f =1;
    HotStuffNode node1;
    HotStuffNode node2;
    HotStuffNode node3;

    @BeforeEach
    void setup() throws Exception {
        // generate RSA key pairs
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keysA = gen.generateKeyPair();
        keysB = gen.generateKeyPair();
        keysC = gen.generateKeyPair();
        keysD = gen.generateKeyPair();

        NodeAddresses.put(1, new InetSocketAddress("localhost", portB));
        NodeAddresses.put(2, new InetSocketAddress("localhost", portC));
        NodeAddresses.put(3, new InetSocketAddress("localhost", portD));

        Addresses.put(0, new InetSocketAddress("localhost", portA));
        Addresses.put(1, new InetSocketAddress("localhost", portB));
        Addresses.put(2, new InetSocketAddress("localhost", portC));
        Addresses.put(3, new InetSocketAddress("localhost", portD));
        
        Clientaddresses.put(0, new InetSocketAddress("localhost", portA));

        Map<Integer, PublicKey> pubKeys = new HashMap<>();
        pubKeys.put(0, keysA.getPublic());
        pubKeys.put(1, keysB.getPublic());
        pubKeys.put(2, keysC.getPublic());
        pubKeys.put(3, keysD.getPublic());

        client = new Client(0, portA, NodeAddresses, keysA.getPrivate(), pubKeys, f);

        node1 = new HotStuffNode(1, portB, Addresses, keysB.getPrivate(), pubKeys, f, new LinkedList<Integer>(NodeAddresses.keySet()));
        node2 = new HotStuffNode(2, portC, Addresses, keysC.getPrivate(), pubKeys, f, new LinkedList<Integer>(NodeAddresses.keySet()));
        node3 = new HotStuffNode(3, portD, Addresses, keysD.getPrivate(), pubKeys, f, new LinkedList<Integer>(NodeAddresses.keySet()));
    }
    
    //TODO:Make threads end before test
    @Test
    void clientServiceRequest () throws Exception{
        Transaction transaction = new Transaction(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            null,
            10,
            0,
            1,
            21000,
            keysA.getPublic()
        );
        transaction.sign(keysA.getPrivate());
        client.send(transaction);
    }
}
