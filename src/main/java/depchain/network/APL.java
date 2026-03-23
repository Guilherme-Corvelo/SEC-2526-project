package depchain.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import depchain.Debug;

/**
 * Implementation of Authenticated Perfect Links (APL) using UDP and public-key
 * signatures.
 */
public class APL {
    private final int localId;
    private final int localPort;
    private final Map<Integer, InetSocketAddress> addresses;
    private final PrivateKey privateKey;
    private final Map<Integer, PublicKey> publicKeys;

    /* package-private for testing */
    DatagramSocket socket;
    private APLListener listener;
    private Thread receiverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // track delivered (src, seq) pairs to enforce no-duplication
    private final Map<Integer, Map<Long, Boolean>> seen = new ConcurrentHashMap<>();
    
    // sequence number for outgoing messages from this node
    private long nextSeq = 0;

    // per-destination send locks and pending state (stop-and-wait)
    private final Map<Integer, Object> sendLocks = new ConcurrentHashMap<>();
    // sequence currently waiting for ack (only one per dest in stop-and-wait)
    private final Map<Integer, Long> sendPendingSeq = new ConcurrentHashMap<>();
    // whether the pending sequence has been acked
    private final Map<Integer, Boolean> sendAcked = new ConcurrentHashMap<>();

    // timestamp (ms since epoch) of last transmission for each destination's pending message
    private final Map<Integer, Long> sendTimestamp = new ConcurrentHashMap<>();

    // timeout for retransmission (milliseconds); tests may adjust this
    private volatile int resendTimeout = 500;

    // maximum duration to attempt retransmissions before giving up (milliseconds).
    // navigating a trade‑off: perfect links literature allows infinite retries, but in
    // practice callers often want a bounded wait so they can suspect a crash and
    // move on.  A positive value sets the bound; when the deadline expires a
    // send() will throw IOException.  By default we use 1000ms (one second).
    // Set to 0 or negative if you truly want “retry forever” behaviour.
    
    // Debug use
    //private volatile long maxSendDuration = 1000;
    // Infinite time period to send message
    private volatile long maxSendDuration = -1;


    // constants for message control
    private static final byte DATA_FLAG = 0x01;
    private static final byte ACK_FLAG = 0x02;

    // testing hooks: drop the next outgoing or incoming packet when true
    volatile boolean dropNextSend = false;
    volatile boolean dropNextReceive = false;
    // if true, all outgoing packets are silently discarded (testing only)
    volatile boolean dropAllSends = false;

    public APL (int localId,
                                         int localPort,
                                         Map<Integer, InetSocketAddress> addresses,
                                         PrivateKey privateKey,
                                         Map<Integer, PublicKey> publicKeys) {
        this.localId = localId;
        this.localPort = localPort;
        this.addresses = addresses;
        this.privateKey = privateKey;
        this.publicKeys = publicKeys;
    }

    public void send(int destId, byte[] payload) throws IOException {
        InetSocketAddress dst = addresses.get(destId);
        if (dst == null) {
            throw new IOException("Unknown destination id " + destId);
        }
        // stop-and-wait protocol: only one un-acked message per destination
        Object sendLock = sendLocks.computeIfAbsent(destId, k -> new Object());
        long seq;
        synchronized (sendLock) {
            seq = nextSeq++;
            sendPendingSeq.put(destId, seq);
            sendAcked.put(destId, Boolean.FALSE);

            // build data payload with control byte prefix
            byte[] dataPayload = new byte[1 + payload.length];
            dataPayload[0] = DATA_FLAG;
            System.arraycopy(payload, 0, dataPayload, 1, payload.length);

            long startTime = System.currentTimeMillis();

            while (!sendAcked.getOrDefault(destId, Boolean.FALSE)) {
                try {
                    byte[] sig = sign(serializeForSigning(seq, dataPayload));
                    APLMessage msg = new APLMessage(localId, seq, dataPayload, sig);
                    byte[] wire = msg.marshall();
                    DatagramPacket packet =
                            new DatagramPacket(wire, wire.length, dst.getAddress(), dst.getPort());
                    // record last transmission time for RTT estimation
                    sendTimestamp.put(destId, System.currentTimeMillis());
                    //Debug.debug("APL SENT: " + packet.toString() + " to: " + destId + " from :" + this.localId);
                    rawSend(packet);
                } catch (Exception e) {
                    throw new IOException("failed to sign or send", e);
                }

                // wait for ack or timeout
                try {
                    sendLock.wait(resendTimeout);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("send interrupted", ie);
                }
                // if we have a maximum send duration, give up after it
                // debug only
                if (maxSendDuration > 0 &&
                        System.currentTimeMillis() - startTime > maxSendDuration) {
                    throw new IOException("no ack from dest " + destId + " after " + maxSendDuration + "ms");
                }
            }
            // cleared by ack handler; clean up
            sendPendingSeq.remove(destId);
            sendAcked.remove(destId);
        }
    }

    public void registerListener(APLListener listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        socket = new DatagramSocket(localPort);
        running.set(true);
        receiverThread = new Thread(this::receiveLoop, "APL-receiver-" + localId);
        receiverThread.start();
    }

    public void stop() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[64 * 1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (running.get()) {
            try {
                socket.receive(packet);
                if (dropNextReceive) {
                    dropNextReceive = false;
                    continue;
                }
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                APLMessage msg = APLMessage.unmarshall(data);
                if (verify(msg)) {
                    // check control byte in payload (must have at least one byte)
                    if (msg.payload.length > 0 && msg.payload[0] == ACK_FLAG) {
                        // acknowledgment message
                        long acked = ByteBuffer.wrap(msg.payload, 1, 8).getLong();
                        handleAck(msg.srcId, acked);
                    } else {
                        // regular data message
                        // APL2: deduplication by (src, seq)
                        seen.computeIfAbsent(msg.srcId, k -> new ConcurrentHashMap<>());
                        if (seen.get(msg.srcId).putIfAbsent(msg.seq, Boolean.TRUE) == null) {
                            // first time seeing this message
                            // **IMPORTANT**: send ACK before delivering to application layer
                            // to avoid deadlocks where the listener thread blocks while
                            // attempting a send (which itself waits for an ACK).  By
                            // moving the ack here we ensure the sender is notified
                            // immediately and cannot be stuck waiting on this side's
                            // processing of the message.
                            sendAck(msg.srcId, msg.seq);

                            if (listener != null) {
                                // deliver payload without control byte
                                byte[] app = new byte[msg.payload.length - 1];
                                System.arraycopy(msg.payload, 1, app, 0, app.length);
                                
                                new Thread(() -> {
                                    listener.onMessage(msg.srcId, app);
                                }).start();
                            }
                        } else {
                            // duplicate: still acknowledge so sender can advance
                            sendAck(msg.srcId, msg.seq);
                        }
                    }
                } else {
                    // APL3: invalid signature, discard
                }
            } catch (IOException e) {
                if (running.get()) {
                    e.printStackTrace();
                }
            }
        }
    }

    /* package-private for tests */
    byte[] sign(byte[] toSign) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(privateKey);
        s.update(toSign);
        return s.sign();
    }

    private boolean verify(APLMessage msg) {
        PublicKey pk = publicKeys.get(msg.srcId);
        if (pk == null) {
            return false;
        }
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(pk);
            s.update(serializeForSigning(msg.seq, msg.payload));
            return s.verify(msg.signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handle an incoming ACK for a sequence number from a given source.
     * If a sender is currently blocked waiting for this ack, wake it up.
     * it will have some kind of logic so that the upper layer can detect it and take the possible
     * suspect out of the suspect list (for processes that might have crashed)
    */

    private void handleAck(int srcId, long ackedSeq) {
        Long pending = sendPendingSeq.get(srcId);
        //TODO: verify if acked seq can be null
        if (pending != null && pending == ackedSeq) {
            Object lock = sendLocks.get(srcId);
            if (lock != null) {
                synchronized (lock) {
                    sendAcked.put(srcId, Boolean.TRUE);
                    lock.notifyAll();
                }
            }
        }
        // Regardless of whether we still had this seq pending, update RTT estimate
        Long ts = sendTimestamp.get(srcId);
        if (ts != null) {
            long rtt = System.currentTimeMillis() - ts;
            // simple adaptation: set timeout to max(current, 2*rtt) or average
            int newTimeout = (int) Math.max(resendTimeout, rtt * 2);
            // cap to avoid overflow
            if (newTimeout > 0) {
                resendTimeout = newTimeout;
            }
        }
        // otherwise: either we have no timestamp recorded or it's stale
    }

    /**
     * Send an acknowledgement message back to the given destination for seq.
     */
    private void sendAck(int destId, long seq) {
        try {
            InetSocketAddress dst = addresses.get(destId);
            if (dst == null) {
                return; // unknown destination
            }
            byte[] ackPayload = new byte[1 + 8];
            ackPayload[0] = ACK_FLAG;
            ByteBuffer.wrap(ackPayload, 1, 8).putLong(seq);
            byte[] sig = sign(serializeForSigning(seq, ackPayload));
            APLMessage ack = new APLMessage(localId, seq, ackPayload, sig);
            byte[] wire = ack.marshall();
            DatagramPacket packet = new DatagramPacket(wire, wire.length, dst.getAddress(), dst.getPort());
            rawSend(packet);
        } catch (Exception e) {
            // ignore failures sending ack
        }
    }

    /**
     * Wrapper around socket.send that respects the dropNextSend testing hook.
     */
    private void rawSend(DatagramPacket packet) throws IOException {
        if (dropAllSends) {
            return;
        }
        if (dropNextSend) {
            dropNextSend = false;
            return;
        }
        socket.send(packet);
    }

    /**
     * Adjust the retransmission timeout (milliseconds).
     * Useful for tests.
     */
    public void setResendTimeout(int millis) {
        this.resendTimeout = millis;
    }

    /**
     * Returns the current retransmission timeout (for testing/inspection).
     */
    public int getResendTimeout() {
        return resendTimeout;
    }

    /**
     * Set the maximum duration (in ms) that {@link #send} will retry a message
     * before giving up with an IOException.
     * A non-positive value disables the limit (default behaviour).
     */
    public void setMaxSendDuration(long millis) {
        this.maxSendDuration = millis;
    }

    /**
     * Serialize (seq, payload) for signing/verification.
     * Ensures the signature covers both the sequence number and the payload.
     */
    private byte[] serializeForSigning(long seq, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length);
        buf.putLong(seq);
        buf.put(payload);
        return buf.array();
    }
}
