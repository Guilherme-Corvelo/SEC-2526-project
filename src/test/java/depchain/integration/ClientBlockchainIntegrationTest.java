package depchain.aa_integration;

import depchain.blockchain.Block;
import depchain.blockchain.BlockStorage;
import depchain.blockchain.Transaction;
import depchain.client.Client;
import depchain.consensus.HotStuffNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientBlockchainIntegrationTest {

    private static final int F = 1;
    private static final int CLIENT_ID = 0;
    private static final int NODE_1_ID = 1;
    private static final int NODE_2_ID = 2;
    private static final int NODE_3_ID = 3;

    private static final int CLIENT_PORT = 23000;
    private static final int NODE_1_PORT = 23001;
    private static final int NODE_2_PORT = 23002;
    private static final int NODE_3_PORT = 23003;

    private KeyPair keysClient;
    private KeyPair keysNode1;
    private KeyPair keysNode2;
    private KeyPair keysNode3;

    private Client client;
    @SuppressWarnings("unused")
    private HotStuffNode node1;
    @SuppressWarnings("unused")
    private HotStuffNode node2;
    @SuppressWarnings("unused")
    private HotStuffNode node3;

    @BeforeEach
    void setup() throws Exception {
        cleanupNodeBlocks(NODE_1_ID);
        cleanupNodeBlocks(NODE_2_ID);
        cleanupNodeBlocks(NODE_3_ID);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keysClient = gen.generateKeyPair();
        keysNode1 = gen.generateKeyPair();
        keysNode2 = gen.generateKeyPair();
        keysNode3 = gen.generateKeyPair();

        Map<Integer, InetSocketAddress> nodeAddresses = new HashMap<>();
        nodeAddresses.put(NODE_1_ID, new InetSocketAddress("localhost", NODE_1_PORT));
        nodeAddresses.put(NODE_2_ID, new InetSocketAddress("localhost", NODE_2_PORT));
        nodeAddresses.put(NODE_3_ID, new InetSocketAddress("localhost", NODE_3_PORT));

        Map<Integer, InetSocketAddress> allAddresses = new HashMap<>();
        allAddresses.put(CLIENT_ID, new InetSocketAddress("localhost", CLIENT_PORT));
        allAddresses.put(NODE_1_ID, new InetSocketAddress("localhost", NODE_1_PORT));
        allAddresses.put(NODE_2_ID, new InetSocketAddress("localhost", NODE_2_PORT));
        allAddresses.put(NODE_3_ID, new InetSocketAddress("localhost", NODE_3_PORT));

        Map<Integer, PublicKey> publicKeys = new HashMap<>();
        publicKeys.put(CLIENT_ID, keysClient.getPublic());
        publicKeys.put(NODE_1_ID, keysNode1.getPublic());
        publicKeys.put(NODE_2_ID, keysNode2.getPublic());
        publicKeys.put(NODE_3_ID, keysNode3.getPublic());

        client = new Client(CLIENT_ID, CLIENT_PORT, nodeAddresses, keysClient.getPrivate(), publicKeys, F);

        LinkedList<Integer> broadcastTo = new LinkedList<>(nodeAddresses.keySet());

        node1 = new HotStuffNode(NODE_1_ID, NODE_1_PORT, allAddresses, keysNode1.getPrivate(), publicKeys, F, broadcastTo);
        node2 = new HotStuffNode(NODE_2_ID, NODE_2_PORT, allAddresses, keysNode2.getPrivate(), publicKeys, F, broadcastTo);
        node3 = new HotStuffNode(NODE_3_ID, NODE_3_PORT, allAddresses, keysNode3.getPrivate(), publicKeys, F, broadcastTo);
    }

    @Test
    void clientRequestFlowsThroughConsensusAndReturnsAfterAssemblyTimeout() throws Exception {
        Transaction tx = new Transaction(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            null,
            10,
            1,
            1,
            21_000,
            keysClient.getPublic()
        );
        tx.sign(keysClient.getPrivate());

        AtomicBoolean done = new AtomicBoolean(false);
        AtomicLong elapsedMillis = new AtomicLong(0L);

        Thread senderThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            client.send(tx);
            elapsedMillis.set(System.currentTimeMillis() - start);
            done.set(true);
        });
        senderThread.start();

        long waitStart = System.currentTimeMillis();
        while (!done.get() && System.currentTimeMillis() - waitStart < 15_000) {
            Thread.sleep(100);
        }

        assertTrue(done.get(), "Client should receive append confirmation from consensus");
        assertTrue(elapsedMillis.get() >= 1_800, "Request should wait for block assembly timeout path");

        Path node1Block1 = Path.of("blocks/node_1/block_1.json");
        Path node2Block1 = Path.of("blocks/node_2/block_1.json");
        Path node3Block1 = Path.of("blocks/node_3/block_1.json");

        assertTrue(Files.exists(node1Block1), "Node 1 should persist decided block");
        assertTrue(Files.exists(node2Block1), "Node 2 should persist decided block");
        assertTrue(Files.exists(node3Block1), "Node 3 should persist decided block");

        BlockStorage node1Storage = new BlockStorage("blocks/node_1/", "genesis.json");
        Block block1 = node1Storage.loadBlock(1);
        assertTrue(block1.getTransactions().size() >= 1, "Persisted block should include transactions");
    }

    private void cleanupNodeBlocks(int nodeId) throws Exception {
        Path dir = Path.of("blocks/node_" + nodeId);
        if (!Files.exists(dir)) {
            return;
        }

        Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to clean test directory: " + path, e);
                }
            });
    }
}
