package depchain.consensus;

import depchain.crypto.KeyVault;

import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

/**
 * ServerMain — entry point for a DepChain server node.
 *
 * Run with:
 *   mvn exec:java -Dexec.mainClass="depchain.consensus.ServerMain" 
 */
public class ServerMain {

    private static final int F = 1;

    private static final Map<Integer, InetSocketAddress> CLIENT_ADDRESSES = new HashMap<>() {{
        put(0, new InetSocketAddress("localhost", 20000));
        put(1, new InetSocketAddress("localhost", 20001));
        put(2, new InetSocketAddress("localhost", 20002));
    }};
    
    private static final Map<Integer, InetSocketAddress> SERVER_ADDRESSES = new HashMap<>() {{
        put(3, new InetSocketAddress("localhost", 20003));
        put(4, new InetSocketAddress("localhost", 20004));
        put(5, new InetSocketAddress("localhost", 20005));
        put(6, new InetSocketAddress("localhost", 20006));
    }};

    public static void main(String[] args) throws Exception {

        Map<Integer, PublicKey> allPublicKeys = KeyVault.loadAllPublicKeys();

        Map<Integer, InetSocketAddress> allAddresses = new HashMap<>();
        allAddresses.putAll(SERVER_ADDRESSES);

        allAddresses.putAll(CLIENT_ADDRESSES);
        
        LinkedList<Integer> broadcastTo = new LinkedList<>(SERVER_ADDRESSES.keySet());

        /*for(int serverId = 3; serverId < 7; serverId += 1){
            PrivateKey myPrivateKey = KeyVault.loadPrivateKey("server" + serverId);
            int myPort = SERVER_ADDRESSES.get(serverId).getPort();

            HotStuffNode node = new HotStuffNode(
            serverId,
            myPort,
            allAddresses,
            myPrivateKey,
            allPublicKeys,
            F,
            broadcastTo
            );
            
            System.out.println("Server " + serverId + " is running. Waiting for transactions...");
            System.out.println();
        }*/
        PrivateKey myPrivateKey = KeyVault.loadPrivateKey("server" + 3);
        int myPort = SERVER_ADDRESSES.get(3).getPort();

        HotStuffNode node = new HotStuffNode(
        3,
        myPort,
        allAddresses,
        myPrivateKey,
        allPublicKeys,
        F,
        broadcastTo
        );
        
        System.out.println("Server " + 3 + " is running. Waiting for transactions...");
        System.out.println();
        
        PrivateKey myPrivateKey1 = KeyVault.loadPrivateKey("server" + 4);
        int myPort1 = SERVER_ADDRESSES.get(4).getPort();

        HotStuffNode node1 = new HotStuffNode(
        4,
        myPort1,
        allAddresses,
        myPrivateKey1,
        allPublicKeys,
        F,
        broadcastTo
        );
        
        System.out.println("Server " + 4 + " is running. Waiting for transactions...");
        System.out.println();
    
        PrivateKey myPrivateKey2 = KeyVault.loadPrivateKey("server" + 5);
        int myPort2 = SERVER_ADDRESSES.get(5).getPort();

        HotStuffNode node2 = new HotStuffNode(
        5,
        myPort2,
        allAddresses,
        myPrivateKey2,
        allPublicKeys,
        F,
        broadcastTo
        );
        
        System.out.println("Server " + 5 + " is running. Waiting for transactions...");
        System.out.println();

        PrivateKey myPrivateKey3 = KeyVault.loadPrivateKey("server" + 6);
        int myPort3 = SERVER_ADDRESSES.get(6).getPort();

        HotStuffNode node3 = new HotStuffNode(
        6,
        myPort3,
        allAddresses,
        myPrivateKey3,
        allPublicKeys,
        F,
        broadcastTo
        );
        
        System.out.println("Server " + 6 + " is running. Waiting for transactions...");
        System.out.println();

    }
}