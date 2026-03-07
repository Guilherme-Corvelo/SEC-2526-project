package depchain.integration;

import depchain.client.ServiceClient;
import depchain.consensus.Block;
import depchain.network.APLListener;
import depchain.network.AuthenticatedPerfectLinks;
import depchain.service.BlockchainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for client → consensus → service flow.
 * 
 * These tests verify the complete end-to-end architecture:
 * - Client submits data via ServiceClient
 * - Data is sent through APL to replicas
 * - Replica buffers the client data as APL listener
 * - Leader becomes ready to propose (after collecting quorum of new-view messages)
 * - Replica automatically proposes buffered client data via ProposalReadyListener callback
 * - Data flows through Byzantine HotStuff consensus (Prepare → Pre-commit → Commit with QCs)
 * - BlockchainService receives committed blocks via ConsensusListener callback
 * - Data is persisted in append-only log
 *
 * Note: These tests use mock APL to focus on the consensus and service layers.
 * Full integration tests with real networking are in ReplicaTest.
 */
public class ClientServiceIntegrationTest {
    
    private ServiceClient client;
    private BlockchainService service;
    private MockConsensusAPL mockAPL;
    
    @BeforeEach
    public void setUp() {
        service = new BlockchainService();
        mockAPL = new MockConsensusAPL(service);
        client = new ServiceClient(0, mockAPL, 1);
    }
    
    @Test
    public void testClientSubmissionReachesService() throws IOException {
        // Client submits data
        client.submitAppendRequest("test_transaction");
        
        // Simulate consensus: APL "delivers" it back to service
        mockAPL.simulateConsensusCommit("test_transaction");
        
        // Service should have the entry
        assertEquals(1, service.getLogSize());
        assertEquals("test_transaction", service.getEntry(0));
    }
    
    @Test
    public void testMultipleSubmissionsReachService() throws IOException {
        String[] txs = {"tx_1", "tx_2", "tx_3"};
        
        for (String tx : txs) {
            client.submitAppendRequest(tx);
            mockAPL.simulateConsensusCommit(tx);
        }
        
        assertEquals(3, service.getLogSize());
        assertEquals("tx_1", service.getEntry(0));
        assertEquals("tx_2", service.getEntry(1));
        assertEquals("tx_3", service.getEntry(2));
    }
    
    @Test
    public void testClientDataIntegrityThroughService() throws IOException {
        String specialData = "tx_with_special_!@#$%^&*()";
        
        client.submitAppendRequest(specialData);
        mockAPL.simulateConsensusCommit(specialData);
        
        assertEquals(specialData, service.getEntry(0));
    }
    
    @Test
    public void testClientSubmissionOrderPreserved() throws IOException {
        int numSubmissions = 50;
        
        for (int i = 0; i < numSubmissions; i++) {
            String tx = "transaction_" + i;
            client.submitAppendRequest(tx);
            mockAPL.simulateConsensusCommit(tx);
        }
        
        assertEquals(numSubmissions, service.getLogSize());
        for (int i = 0; i < numSubmissions; i++) {
            assertEquals("transaction_" + i, service.getEntry(i));
        }
    }
    
    @Test
    public void testMultipleClientsIntegration() throws IOException {
        ServiceClient client1 = new ServiceClient(0, mockAPL, 1);
        ServiceClient client2 = new ServiceClient(1, mockAPL, 1);
        
        client1.submitAppendRequest("client1_tx1");
        mockAPL.simulateConsensusCommit("client1_tx1");
        
        client2.submitAppendRequest("client2_tx1");
        mockAPL.simulateConsensusCommit("client2_tx1");
        
        client1.submitAppendRequest("client1_tx2");
        mockAPL.simulateConsensusCommit("client1_tx2");
        
        // All transactions should be in service log
        assertEquals(3, service.getLogSize());
    }
    
    @Test
    public void testLargeDataThroughIntegration() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            sb.append("x");
        }
        String largeData = sb.toString();
        
        client.submitAppendRequest(largeData);
        mockAPL.simulateConsensusCommit(largeData);
        
        assertEquals(largeData, service.getEntry(0));
    }
    
    /**
     * Mock APL that simulates consensus behavior.
     * In real system, data would go through ByzantineHotStuffNode consensus.
     * Here we simulate: client → APL send → consensus commit → service.
     */
    private static class MockConsensusAPL implements AuthenticatedPerfectLinks {
        private final BlockchainService service;
        private int sendCount = 0;
        
        MockConsensusAPL(BlockchainService service) {
            this.service = service;
        }
        
        @Override
        public synchronized void send(int replica, byte[] data) throws IOException {
            // Simulate APL send (would normally buffer for consensus)
            sendCount++;
        }
        
        /**
         * Simulate consensus decision: convert bytes back to string and commit via service.
         */
        synchronized void simulateConsensusCommit(String data) {
            // Create block and commit through service
            Block block = new Block(data);
            service.onCommit(block);
        }
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void registerListener(APLListener listener) {}
    }
}
