package depchain.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedPerfectLinksTest {
    private AuthenticatedPerfectLinksImpl nodeA;
    private AuthenticatedPerfectLinksImpl nodeB;
    private int portA = 20000;
    private int portB = 20001;

    private KeyPair keysA;
    private KeyPair keysB;

    @BeforeEach
    void setup() throws Exception {
        // generate RSA key pairs
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keysA = gen.generateKeyPair();
        keysB = gen.generateKeyPair();

        Map<Integer, InetSocketAddress> addresses = new HashMap<>();
        addresses.put(0, new InetSocketAddress("localhost", portA));
        addresses.put(1, new InetSocketAddress("localhost", portB));

        Map<Integer, PublicKey> pubKeys = new HashMap<>();
        pubKeys.put(0, keysA.getPublic());
        pubKeys.put(1, keysB.getPublic());

        nodeA = new AuthenticatedPerfectLinksImpl(0, portA, addresses, keysA.getPrivate(), pubKeys);
        nodeB = new AuthenticatedPerfectLinksImpl(1, portB, addresses, keysB.getPrivate(), pubKeys);

        nodeA.start();
        nodeB.start();
    }

    @AfterEach
    void tearDown() {
        nodeA.stop();
        nodeB.stop();
    }

    @Test
    void sendAndReceive() throws Exception {
        ArrayBlockingQueue<String> delivered = new ArrayBlockingQueue<>(1);
        nodeB.registerListener((src, payload) -> {
            // print the message received using toString
            APLMessage m = new APLMessage(src, 0, payload, new byte[0]);
            System.out.println("[test] B received -> " + m);
            delivered.add(new String(payload));
        });

        String msg = "hello";
        // construct a debug message for printing before send
        APLMessage debug = new APLMessage(0, 0, msg.getBytes(), new byte[0]);
        System.out.println("[test] A sending -> " + debug);
        nodeA.send(1, msg.getBytes());

        String received = delivered.poll(1, TimeUnit.SECONDS);
        assertEquals(msg, received);
    }

    @Test
    void invalidSignatureIsDropped() throws Exception {
        ArrayBlockingQueue<String> delivered = new ArrayBlockingQueue<>(1);
        nodeB.registerListener((src, payload) -> {
            APLMessage m = new APLMessage(src, 0, payload, new byte[0]);
            System.out.println("[test] B received -> " + m);
            delivered.add(new String(payload));
        });

        // send payload but tamper with signature manually
        String msg = "goodbye";
        APLMessage debug = new APLMessage(0, 0, msg.getBytes(), new byte[0]);
        System.out.println("[test] A sending (tampered) -> " + debug);
        byte[] payload = msg.getBytes();
        // compute signature using sequence 0
        long seq = 0;
        byte[] toSign = ByteBuffer.allocate(8 + payload.length)
            .putLong(seq).put(payload).array();
        byte[] sig = nodeA.sign(toSign);
        // tamper payload
        payload[0] = 'X';
        APLMessage tampered = new APLMessage(0, seq, payload, sig);
        byte[] wire = tampered.marshall();
        // send directly using socket
        nodeA.socket.send(new java.net.DatagramPacket(wire, wire.length,
                new InetSocketAddress("localhost", portB).getAddress(), portB));

        String received = delivered.poll(500, TimeUnit.MILLISECONDS);
        assertNull(received, "Message with invalid signature should not be delivered");
    }

    @Test
    void noDuplicateDelivery() throws Exception {
        ArrayBlockingQueue<String> delivered = new ArrayBlockingQueue<>(10);
        nodeB.registerListener((src, payload) -> {
            delivered.add(new String(payload));
        });

        String msg = "nodupe";
        byte[] app = msg.getBytes();
        // include data flag as the first byte when constructing raw message
        byte[] payload = new byte[1 + app.length];
        payload[0] = 0x01; // DATA_FLAG
        System.arraycopy(app, 0, payload, 1, app.length);
        long seq = 0;
        byte[] toSign = ByteBuffer.allocate(8 + payload.length)
            .putLong(seq).put(payload).array();
        byte[] sig = nodeA.sign(toSign);
        APLMessage message = new APLMessage(0, seq, payload, sig);
        byte[] wire = message.marshall();

        // send the same message 3 times
        for (int i = 0; i < 3; i++) {
            nodeA.socket.send(new java.net.DatagramPacket(wire, wire.length,
                    new InetSocketAddress("localhost", portB).getAddress(), portB));
            Thread.sleep(50);
        }

        // we should only see it delivered once
        String received1 = delivered.poll(500, TimeUnit.MILLISECONDS);
        assertEquals("nodupe", received1);
        
        String received2 = delivered.poll(200, TimeUnit.MILLISECONDS);
        assertNull(received2, "Should not deliver the same message twice");
    }

    @Test
    void retransmitOnLoss() throws Exception {
        // set a short timeout so the test runs fast
        nodeA.setResendTimeout(100);

        ArrayBlockingQueue<String> delivered = new ArrayBlockingQueue<>(1);
        nodeB.registerListener((src, payload) -> delivered.add(new String(payload)));

        // drop the first outgoing packet from A to simulate loss
        nodeA.dropNextSend = true;

        long start = System.nanoTime();
        nodeA.send(1, "retry".getBytes());
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        // expect the call to take at least one retransmission timeout
        assertTrue(elapsed >= 100, "send should wait at least one timeout before succeeding");

        String received = delivered.poll(500, TimeUnit.MILLISECONDS);
        assertEquals("retry", received);
    }

    @Test
    void giveUpAfterMaxDuration() throws Exception {
        nodeA.setResendTimeout(50);
        nodeA.setMaxSendDuration(200);

        // have B drop every outgoing packet (including ACKs) so A never receives any
        nodeB.dropAllSends = true;

        IOException thrown = assertThrows(IOException.class, () -> nodeA.send(1, "fail".getBytes()));
        assertTrue(thrown.getMessage().contains("no ack"));
    }

    @Test
    void timeoutAdaptationOnLateAck() throws Exception {
        // start with a small timeout
        nodeA.setResendTimeout(50);
        nodeA.setMaxSendDuration(-1);

        ArrayBlockingQueue<String> delivered = new ArrayBlockingQueue<>(1);
        nodeB.registerListener((src, payload) -> delivered.add(new String(payload)));

        int before = nodeA.getResendTimeout();

        // drop the first ack to force a retransmit
        nodeB.dropNextSend = true;

        nodeA.send(1, "adapt".getBytes());
        assertEquals("adapt", delivered.poll(1, TimeUnit.SECONDS));

        int after = nodeA.getResendTimeout();
        assertTrue(after >= before, "timeout should increase or remain the same after a late ack");
    }

    @Test
    void duplicateAckIgnored() throws Exception {
        // send one message normally
        ArrayBlockingQueue<String> delivered = new ArrayBlockingQueue<>(1);
        nodeB.registerListener((src, payload) -> delivered.add(new String(payload)));

        nodeA.send(1, "once".getBytes());
        assertEquals("once", delivered.poll(1, TimeUnit.SECONDS));

        // craft a fake ACK for sequence 0 from B to A and deliver twice
        long seq = 0;
        byte[] ackPayload = new byte[1 + 8];
        ackPayload[0] = 0x02; // ACK_FLAG
        ByteBuffer.wrap(ackPayload, 1, 8).putLong(seq);
        byte[] sig = nodeB.sign(ByteBuffer.allocate(8 + ackPayload.length)
                .putLong(seq).put(ackPayload).array());
        APLMessage ack = new APLMessage(1, seq, ackPayload, sig);
        byte[] wire = ack.marshall();
        // send directly to A's socket twice
        for (int i = 0; i < 2; i++) {
            nodeB.socket.send(new java.net.DatagramPacket(wire, wire.length,
                    new InetSocketAddress("localhost", portA).getAddress(), portA));
            Thread.sleep(20);
        }
        // there is no explicit observable effect, but the second ack should not crash
        // we can assert that we are still able to send another message afterwards
        nodeA.send(1, "after".getBytes());
        assertEquals("after", delivered.poll(1, TimeUnit.SECONDS));
    }
}
