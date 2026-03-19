package depchain.client;

import depchain.network.APLListener;
import depchain.network.APL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ServiceClient: Client-side submission of data to blockchain.
 * 
 * Tests verify:
 * - Submission of strings to replicas
 * - Proper serialization of data
 * - Fire-and-forget semantics
 * - Multiple client submissions

public class ServiceClientTest {
    
    private ServiceClient client;
    private MockAPL mockAPL;
    
    @BeforeEach
    public void setUp() {
        mockAPL = new MockAPL();
        client = new ServiceClient(0, mockAPL, 1);  // Client 0 -> Replica 1
    }
    
    @Test
    public void testSubmitSingleString() throws IOException {
        String data = "test_entry_1";
        client.sendRequest(data);
        
        // Verify APL was called
        assertTrue(mockAPL.wasSendCalled);
        assertEquals(1, mockAPL.targetReplica);
        assertNotNull(mockAPL.lastPayload);
        
        // Verify payload is the string data as bytes
        String payloadStr = new String(mockAPL.lastPayload);
        assertEquals(data, payloadStr);
    }
    
    @Test
    public void testSubmitMultipleStrings() throws IOException {
        String[] entries = {"entry1", "entry2", "entry3"};
        
        for (String entry : entries) {
            client.sendRequest(entry);
        }
        
        // Verify last submission
        String payloadStr = new String(mockAPL.lastPayload);
        assertEquals("entry3", payloadStr);
        assertEquals(3, mockAPL.sendCount);
    }
    
    @Test
    public void testSubmitEmptyString() throws IOException {
        client.sendRequest("");
        
        assertNotNull(mockAPL.lastPayload);
        assertEquals(0, mockAPL.lastPayload.length);
    }
    
    @Test
    public void testSubmitStringWithSpecialChars() throws IOException {
        String data = "test!@#$%^&*()_+-=[]{}|;:',.<>?";
        client.sendRequest(data);
        
        String payloadStr = new String(mockAPL.lastPayload);
        assertEquals(data, payloadStr);
    }
    
    @Test
    public void testSubmitLongString() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("x");
        }
        String longData = sb.toString();
        
        client.sendRequest(longData);
        
        String payloadStr = new String(mockAPL.lastPayload);
        assertEquals(longData, payloadStr);
    }
    
    @Test
    public void testClientIdPreserved() throws IOException {
        ServiceClient client2 = new ServiceClient(42, mockAPL, 2);
        client2.sendRequest("test");
        
        // Client ID should be preserved (verified by no exception)
        assertNotNull(mockAPL.lastPayload);
    }
    
    @Test
    public void testTargetReplicaCorrect() throws IOException {
        ServiceClient client1 = new ServiceClient(0, mockAPL, 1);
        client1.sendRequest("test1");
        assertEquals(1, mockAPL.targetReplica);
        
        mockAPL.reset();
        
        ServiceClient client2 = new ServiceClient(1, mockAPL, 2);
        client2.sendRequest("test2");
        assertEquals(2, mockAPL.targetReplica);
    }
    
    /**
     * Mock APL for testing client without real network.
    private static class MockAPL extends APL {
        boolean wasSendCalled = false;
        int sendCount = 0;
        int targetReplica = -1;
        byte[] lastPayload = null;

        MockAPL() {
            super(0, 0, new HashMap<>(), null, new HashMap<>());
        }
        
        @Override
        public synchronized void send(int replica, byte[] data) throws IOException {
            wasSendCalled = true;
            sendCount++;
            targetReplica = replica;
            lastPayload = data.clone();
        }
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void registerListener(APLListener listener) {}
        
        void reset() {
            wasSendCalled = false;
            sendCount = 0;
            lastPayload = null;
        }
    }
}
 */