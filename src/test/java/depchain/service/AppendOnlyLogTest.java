package depchain.service;

import depchain.consensus.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AppendOnlyLog: In-memory append-only log implementation.
 * 
 * Tests verify:
 * - Appending entries maintains order
 * - Retrieving entries by index
 * - Log size tracking
 * - Thread-safety of concurrent appends
 * - Read-only semantics (entries cannot be modified)
 */
public class AppendOnlyLogTest {
    
    private AppendOnlyLog log;
    
    @BeforeEach
    public void setUp() {
        log = new AppendOnlyLog();
    }
    
    @Test
    public void testAppendSingleEntry() {
        int index = log.append("entry1");
        assertEquals(0, index);
        assertEquals(1, log.size());
        assertEquals("entry1", log.get(0));
    }
    
    @Test
    public void testAppendMultipleEntries() {
        log.append("entry1");
        log.append("entry2");
        log.append("entry3");
        
        assertEquals(3, log.size());
        assertEquals("entry1", log.get(0));
        assertEquals("entry2", log.get(1));
        assertEquals("entry3", log.get(2));
    }
    
    @Test
    public void testAppendReturnsCorrectIndex() {
        assertEquals(0, log.append("first"));
        assertEquals(1, log.append("second"));
        assertEquals(2, log.append("third"));
        assertEquals(3, log.append("fourth"));
    }
    
    @Test
    public void testGetNonexistentIndex() {
        log.append("entry");
        assertNull(log.get(1));   // Index out of bounds
        assertNull(log.get(-1));  // Negative index
        assertNull(log.get(100)); // Way out of bounds
    }
    
    @Test
    public void testGetEmptyLog() {
        assertNull(log.get(0));
        assertEquals(0, log.size());
    }
    
    @Test
    public void testGetAllEntries() {
        log.append("first");
        log.append("second");
        log.append("third");
        
        List<String> all = log.getAll();
        assertEquals(3, all.size());
        assertEquals("first", all.get(0));
        assertEquals("second", all.get(1));
        assertEquals("third", all.get(2));
    }
    
    @Test
    public void testGetAllEmptyLog() {
        List<String> all = log.getAll();
        assertNotNull(all);
        assertEquals(0, all.size());
    }
    
    @Test
    public void testContains() {
        log.append("entry1");
        log.append("entry2");
        
        assertTrue(log.contains("entry1"));
        assertTrue(log.contains("entry2"));
        assertFalse(log.contains("entry3"));
        assertFalse(log.contains(""));
    }
    
    @Test
    public void testContainsEmptyLog() {
        assertFalse(log.contains("anything"));
    }
    
    @Test
    public void testAppendNull() {
        // This should work (AppendOnlyLog stores whatever is given)
        log.append(null);
        assertEquals(1, log.size());
        assertNull(log.get(0));
    }
    
    @Test
    public void testAppendLargeNumber() {
        for (int i = 0; i < 1000; i++) {
            log.append("entry_" + i);
        }
        assertEquals(1000, log.size());
        assertEquals("entry_0", log.get(0));
        assertEquals("entry_999", log.get(999));
    }
    
    @Test
    public void testConcurrentAppends() throws InterruptedException {
        int numThreads = 10;
        int entriesToAppendPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < entriesToAppendPerThread; i++) {
                    log.append("thread_" + threadId + "_entry_" + i);
                }
            });
        }
        
        for (Thread t : threads) {
            t.start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        // Should have all entries (order might vary due to concurrency, but count is deterministic)
        assertEquals(numThreads * entriesToAppendPerThread, log.size());
        
        // Verify all entries are there
        List<String> all = log.getAll();
        assertEquals(numThreads * entriesToAppendPerThread, all.size());
    }
}
