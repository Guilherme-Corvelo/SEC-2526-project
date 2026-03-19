package depchain.service;

import depchain.consensus.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockchainService: Service layer that maintains the append-only log.
 * 
 * Tests verify:
 * - Blocks are properly committed to the log
 * - Order of commits is preserved
 * - Read operations return correct data
 * - Multiple blocks can be committed
 * - Service properly implements ConsensusListener interface

public class BlockchainServiceTest {
    
    private BlockchainService service;
    
    @BeforeEach
    public void setUp() {
        service = new BlockchainService();
    }
    
    @Test
    public void testOnCommitSingleBlock() {
        Block block = new Block("transaction_1");
        service.onCommit(block);
        
        assertEquals(1, service.getLogSize());
        assertEquals("transaction_1", service.getEntry(0));
    }
    
    @Test
    public void testOnCommitMultipleBlocks() {
        Block block1 = new Block("tx_1");
        Block block2 = new Block("tx_2");
        Block block3 = new Block("tx_3");
        
        service.onCommit(block1);
        service.onCommit(block2);
        service.onCommit(block3);
        
        assertEquals(3, service.getLogSize());
        assertEquals("tx_1", service.getEntry(0));
        assertEquals("tx_2", service.getEntry(1));
        assertEquals("tx_3", service.getEntry(2));
    }
    
    @Test
    public void testGetEntryFromCommittedLog() {
        service.onCommit(new Block("entry_0"));
        service.onCommit(new Block("entry_1"));
        service.onCommit(new Block("entry_2"));
        
        assertEquals("entry_0", service.getEntry(0));
        assertEquals("entry_1", service.getEntry(1));
        assertEquals("entry_2", service.getEntry(2));
    }
    
    @Test
    public void testGetEntryNonexistent() {
        service.onCommit(new Block("only_entry"));
        
        assertNull(service.getEntry(1));
        assertNull(service.getEntry(-1));
        assertNull(service.getEntry(100));
    }
    
    @Test
    public void testGetAllEntries() {
        service.onCommit(new Block("tx_1"));
        service.onCommit(new Block("tx_2"));
        service.onCommit(new Block("tx_3"));
        
        List<String> all = service.getAllEntries();
        assertEquals(3, all.size());
        assertEquals("tx_1", all.get(0));
        assertEquals("tx_2", all.get(1));
        assertEquals("tx_3", all.get(2));
    }
    
    @Test
    public void testGetAllEntriesEmptyService() {
        List<String> all = service.getAllEntries();
        assertNotNull(all);
        assertEquals(0, all.size());
    }
    
    @Test
    public void testGetLogSizeInitiallyZero() {
        assertEquals(0, service.getLogSize());
    }
    
    @Test
    public void testGetLogSizeIncrementsWithCommit() {
        assertEquals(0, service.getLogSize());
        service.onCommit(new Block("tx_1"));
        assertEquals(1, service.getLogSize());
        service.onCommit(new Block("tx_2"));
        assertEquals(2, service.getLogSize());
    }
    
    @Test
    public void testBlockWithLongData() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("x");
        }
        String longData = sb.toString();
        
        service.onCommit(new Block(longData));
        
        assertEquals(longData, service.getEntry(0));
    }
    
    @Test
    public void testBlockOrderPreserved() {
        for (int i = 0; i < 100; i++) {
            service.onCommit(new Block("block_" + i));
        }
        
        List<String> all = service.getAllEntries();
        for (int i = 0; i < 100; i++) {
            assertEquals("block_" + i, all.get(i));
        }
    }
    
    @Test
    public void testMultipleServicesIndependent() {
        BlockchainService service1 = new BlockchainService();
        BlockchainService service2 = new BlockchainService();
        
        service1.onCommit(new Block("service1_tx1"));
        service2.onCommit(new Block("service2_tx1"));
        service1.onCommit(new Block("service1_tx2"));
        
        assertEquals(2, service1.getLogSize());
        assertEquals(1, service2.getLogSize());
        
        assertEquals("service1_tx1", service1.getEntry(0));
        assertEquals("service1_tx2", service1.getEntry(1));
        assertEquals("service2_tx1", service2.getEntry(0));
    }
    
    @Test
    public void testConcurrentCommits() throws InterruptedException {
        int numThreads = 10;
        int commitsPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < commitsPerThread; i++) {
                    service.onCommit(new Block("thread_" + threadId + "_tx_" + i));
                }
            });
        }
        
        for (Thread t : threads) {
            t.start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        // Verify all commits were recorded
        assertEquals(numThreads * commitsPerThread, service.getLogSize());
        
        List<String> all = service.getAllEntries();
        assertEquals(numThreads * commitsPerThread, all.size());
    }
    
    @Test
    public void testServiceIntegrationWithConsensusListener() {
        // Verify BlockchainService properly implements ConsensusListener
        Block testBlock = new Block("integration_test");
        service.onCommit(testBlock);
        
        // Block data should be available in service
        assertEquals("integration_test", service.getEntry(0));
        assertEquals(1, service.getLogSize());
    }
    
    @Test
    public void testCommitOrderMatchesSubmissionOrder() {
        String[] entries = {"first", "second", "third", "fourth", "fifth"};
        
        for (String entry : entries) {
            service.onCommit(new Block(entry));
        }
        
        List<String> all = service.getAllEntries();
        for (int i = 0; i < entries.length; i++) {
            assertEquals(entries[i], all.get(i));
        }
    }
}
 */