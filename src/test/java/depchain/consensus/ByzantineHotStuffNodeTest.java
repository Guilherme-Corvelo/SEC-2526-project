package depchain.consensus;

import depchain.network.APL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import threshsig.KeyShare;
import threshsig.SigShare;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ByzantineHotStuffNodeTest {

    private final List<APL> createdApls = new ArrayList<>();

    @BeforeEach
    void setup() {
        createdApls.clear();
    }

    @AfterEach
    void teardown() {
        for (APL apl : createdApls) {
            try {
                apl.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Reject QC with missing threshold signature")
    void rejectQcWithoutSignature() throws Exception {
        HotStuffNode node = createNode(0, 1, 25000, 0);
        QuorumCertificate qc = new QuorumCertificate(Type.PREPARE, 0, new Node(), null);

        assertFalse(node.validateQC(qc));
    }

    @Test
    @Order(2)
    @DisplayName("Accept QC with valid threshold signature")
    void acceptQcWithValidThresholdSignature() throws Exception {
        HotStuffNode node = createNode(0, 1, 25100, 0);

        Node proposalNode = new Node();
        int qcView = 4;
        Type qcType = Type.PRECOMMIT;
        BigInteger thresholdSignature = signForQc(node, qcType, qcView, proposalNode);

        QuorumCertificate qc = new QuorumCertificate(qcType, qcView, proposalNode, thresholdSignature);

        assertTrue(node.validateQC(qc));
    }

    @Test
    @Order(3)
    @DisplayName("Reject forged QC when signed bytes and metadata do not match")
    void rejectForgedQcWithTamperedFields() throws Exception {
        HotStuffNode node = createNode(0, 1, 25200, 0);

        Node originalNode = new Node();
        BigInteger signature = signForQc(node, Type.PREPARE, 2, originalNode);

        QuorumCertificate forgedQc = new QuorumCertificate(Type.COMMIT, 2, originalNode, signature);

        assertFalse(node.validateQC(forgedQc));
    }
    
    @Test
    @Order(4)
    @DisplayName("Leader timeout increments view and emits NEWVIEW")
    void leaderStopsReplyingTriggersNewView() throws Exception {
        HotStuffNode node = createNode(0, 4, 25400, 1);
        setField(node, "view", 3);
        setField(node, "pendingVoteType", Type.PRECOMMIT);
        getVotes(node).put(2, new Message(Type.PRECOMMIT, 3, new Node(), 8));
        
        Message lastMessage = new Message(Type.PRECOMMIT, 3, new Node(), 8);
        invokePrivate(node, "onViewTimeout", Message.class, lastMessage);

        assertEquals(4, getIntField(node, "view"));
        assertEquals(Type.NEWVIEW, node.getPendingVoteType());
        assertEquals(0, getVotes(node).size());
    }

    @Test
    @Order(5)
    @DisplayName("Ignore messages with wrong view number")
    void ignoreMessagesFromWrongView() throws Exception {
        HotStuffNode node = createNode(0, 1, 25500, 1);

        Message staleMessage = new Message(Type.NEWVIEW, 99, new Node(), 123);
        node.onMessage(0, staleMessage.serialize());

        assertNull(node.getPrepareQC());
        assertEquals(Type.NEWVIEW, node.getPendingVoteType());
    }

    @Test
    @Order(6)
    @DisplayName("Ignore wrong phase type when collecting votes")
    void ignoreWrongPhaseType() throws Exception {
        HotStuffNode node = createNode(0, 4, 25600, 1);
        setField(node, "pendingVoteType", Type.PREPARE);

        Message wrongPhaseMessage = new Message(Type.COMMIT, 0, new Node(), 9);
        setField(wrongPhaseMessage, "partialSign", new SigShare(1, new byte[] {0x01}));
        node.onMessage(1, wrongPhaseMessage.serialize());

        assertEquals(0, getVotes(node).size());
    }

    @Test
    @Order(7)
    @DisplayName("Replica failure: missing replies keeps quorum below threshold")
    void replicaFailureDoesNotReachQuorum() throws Exception {
        HotStuffNode node = createNode(0, 4, 25700, 1);
        setField(node, "pendingVoteType", Type.PREPARE);

        ConcurrentHashMap<Integer, Message> votes = getVotes(node);
        votes.clear();
        votes.put(1, new Message(Type.PREPARE, 0, new Node(), 0));
        votes.put(2, new Message(Type.PREPARE, 0, new Node(), 0));

        assertFalse(invokeEnoughVotes(node));

        votes.put(3, new Message(Type.PREPARE, 0, new Node(), 0));
        assertTrue(invokeEnoughVotes(node));
    }

    private HotStuffNode createNode(int nodeId, int nodeCount, int basePort, int f) throws Exception {
        List<Integer> ids = new ArrayList<>();
        Map<Integer, InetSocketAddress> addresses = new HashMap<>();
        Map<Integer, PublicKey> publicKeys = new HashMap<>();
        List<KeyPair> keys = new ArrayList<>();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        for (int i = 0; i < nodeCount; i++) {
            ids.add(i);
            addresses.put(i, new InetSocketAddress("localhost", basePort + i));
            KeyPair pair = generator.generateKeyPair();
            keys.add(pair);
            publicKeys.put(i, pair.getPublic());
        }

        LinkedList<Integer> broadcastTo = new LinkedList<>(ids);
        PrivateKey privateKey = keys.get(nodeId).getPrivate();

        HotStuffNode node = new HotStuffNode(nodeId, basePort + nodeId, addresses, privateKey, publicKeys, f, broadcastTo);
        createdApls.add(getApl(node));
        return node;
    }

    private BigInteger signForQc(HotStuffNode node, Type type, int view, Node consensusNode) throws Exception {
        KeyShare localShare = (KeyShare) getField(node, "localShare");

        byte[] typeData = type.name().getBytes(StandardCharsets.UTF_8);
        byte[] viewData = ByteBuffer.allocate(Integer.BYTES).putInt(view).array();
        byte[] nodeData = consensusNode.serialize();
        byte[] data = ByteBuffer.allocate(typeData.length + nodeData.length + viewData.length)
            .put(typeData)
            .put(nodeData)
            .put(viewData)
            .array();

        return localShare.sign(data).getSig();
    }

    private byte[] qcPayload(Type type, int view, Node consensusNode) {
        byte[] typeData = type.name().getBytes(StandardCharsets.UTF_8);
        byte[] viewData = ByteBuffer.allocate(Integer.BYTES).putInt(view).array();
        byte[] nodeData = consensusNode.serialize();
        return ByteBuffer.allocate(typeData.length + nodeData.length + viewData.length)
            .put(typeData)
            .put(nodeData)
            .put(viewData)
            .array();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Integer, Message> getVotes(HotStuffNode node) throws Exception {
        return (ConcurrentHashMap<Integer, Message>) getField(node, "votes");
    }

    private APL getApl(HotStuffNode node) throws Exception {
        return (APL) getField(node, "apl");
    }

    private int getIntField(HotStuffNode node, String fieldName) throws Exception {
        return (int) getField(node, fieldName);
    }

    private boolean invokeEnoughVotes(HotStuffNode node) throws Exception {
        return (boolean) invokePrivate(node, "enoughVotes");
    }

    private Object invokePrivate(HotStuffNode node, String methodName) throws Exception {
        java.lang.reflect.Method method = HotStuffNode.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(node);
    }

    private Object invokePrivate(HotStuffNode node, String methodName, Class<?> argType, Object arg) throws Exception {
        java.lang.reflect.Method method = HotStuffNode.class.getDeclaredMethod(methodName, argType);
        method.setAccessible(true);
        return method.invoke(node, arg);
    }

    private Object getField(HotStuffNode node, String fieldName) throws Exception {
        Field field = HotStuffNode.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(node);
    }

    private void setField(HotStuffNode node, String fieldName, Object value) throws Exception {
        Field field = HotStuffNode.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(node, value);
    }

    private void setField(Message msg, String fieldName, Object value) throws Exception {
        Field field = Message.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(msg, value);
    }
}
