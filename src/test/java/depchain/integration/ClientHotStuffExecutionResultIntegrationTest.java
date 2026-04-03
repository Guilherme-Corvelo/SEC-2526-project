package depchain.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import depchain.API.DepchainAPI;
import depchain.blockchain.Transaction;
import depchain.client.Client;
import depchain.consensus.HotStuffNode;
import depchain.consensus.Message;
import depchain.network.APL;

class ClientHotStuffExecutionResultIntegrationTest {

    private Client client;
    private HotStuffNode node1;
    private HotStuffNode node2;
    private HotStuffNode node3;

    @Test
    void clientRequestEventuallyReturnsExecutionResultWithRealHotStuffNodes() throws Exception {
        int clientPort = 23100;
        int portNode1 = 23101;
        int portNode2 = 23102;
        int portNode3 = 23103;

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);

        KeyPair clientKeys = gen.generateKeyPair();
        KeyPair node1Keys = gen.generateKeyPair();
        KeyPair node2Keys = gen.generateKeyPair();
        KeyPair node3Keys = gen.generateKeyPair();

        Map<Integer, InetSocketAddress> nodeAddresses = new HashMap<>();
        nodeAddresses.put(1, new InetSocketAddress("localhost", portNode1));
        nodeAddresses.put(2, new InetSocketAddress("localhost", portNode2));
        nodeAddresses.put(3, new InetSocketAddress("localhost", portNode3));

        Map<Integer, InetSocketAddress> allAddresses = new HashMap<>();
        allAddresses.put(0, new InetSocketAddress("localhost", clientPort));
        allAddresses.putAll(nodeAddresses);

        Map<Integer, PublicKey> publicKeys = new HashMap<>();
        publicKeys.put(0, clientKeys.getPublic());
        publicKeys.put(1, node1Keys.getPublic());
        publicKeys.put(2, node2Keys.getPublic());
        publicKeys.put(3, node3Keys.getPublic());

        int f = 1;
        client = new Client(0, clientPort, nodeAddresses, clientKeys.getPrivate(), publicKeys, f);
        LinkedList<Integer> replicaIds = new LinkedList<>(nodeAddresses.keySet());
        node1 = new HotStuffNode(1, portNode1, allAddresses, node1Keys.getPrivate(), publicKeys, f, replicaIds);
        node2 = new HotStuffNode(2, portNode2, allAddresses, node2Keys.getPrivate(), publicKeys, f, replicaIds);
        node3 = new HotStuffNode(3, portNode3, allAddresses, node3Keys.getPrivate(), publicKeys, f, replicaIds);

        /*
        Transaction tx = new Transaction(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "1234567891234567891234567891234567891234",
            "70a08231"+ padAddress("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            0,
            1,
            1,
            21_000,
            clientKeys.getPublic()
            );
            */
        Transaction tx = new Transaction(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            null,
            0,
            1,
            1,
            21_000,
            clientKeys.getPublic()
        );
        tx.sign(clientKeys.getPrivate());

        CompletableFuture<Message> responseFuture = CompletableFuture.supplyAsync(() -> client.send(tx));
        Message response = responseFuture.get(20, TimeUnit.SECONDS);

        assertNotNull(response);
        assertNotNull(response.getExecutionResults());
        assertFalse(response.getExecutionResults().isEmpty());
        assertTrue(response.getExecutionResults().get(0).gasUsed >= 0);
    }

    private String padAddress(String address) {
        String hex = address.startsWith("0x") ? address.substring(2) : address;
        return "0".repeat(64 - hex.length()) + hex;
    }
}