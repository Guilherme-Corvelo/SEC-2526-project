package depchain.consensus;

import depchain.network.AuthenticatedPerfectLinksImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HotStuffNodeTest {
    private AuthenticatedPerfectLinksImpl[] aplNodes;
    private HotStuffNode[] nodes;
    private int[] ports = {21000, 21001, 21002};
    private KeyPair[] keys;
    private Map<Integer, InetSocketAddress> addresses;
    private Map<Integer, java.security.PublicKey> pubKeys;

    @BeforeEach
    void setup() throws Exception {
        // generate RSA key pairs
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = new KeyPair[ports.length];
        for (int i = 0; i < ports.length; i++) {
            keys[i] = gen.generateKeyPair();
        }

        addresses = new HashMap<>();
        pubKeys = new HashMap<>();
        for (int i = 0; i < ports.length; i++) {
            addresses.put(i, new InetSocketAddress("localhost", ports[i]));
            pubKeys.put(i, keys[i].getPublic());
        }

        aplNodes = new AuthenticatedPerfectLinksImpl[ports.length];
        nodes = new HotStuffNode[ports.length];
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < ports.length; i++) {
            ids.add(i);
        }

        for (int i = 0; i < ports.length; i++) {
            aplNodes[i] = new AuthenticatedPerfectLinksImpl(i, ports[i], addresses, keys[i].getPrivate(), pubKeys);
            // increase timeout for asynchronous send threads
            aplNodes[i].setMaxSendDuration(5000);
            aplNodes[i].setResendTimeout(200);
            nodes[i] = new HotStuffNode(i, ids, aplNodes[i], block -> {
                // no-op here, tests will use queue
            });
            aplNodes[i].start();
            nodes[i].start();
        }
        // after all nodes have started listening, send initial new-view from replicas
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].getNodeId() != 0) {
                // non-leaders send new-view
                nodes[i].sendNewView();
            }
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Give async send threads time to complete before shutting down.
        // The protocol may spawn new-view sends during view transitions,
        // so we wait long enough for those to be acknowledged.
        Thread.sleep(500);
        
        // stop APL nodes
        if (aplNodes != null) {
            for (AuthenticatedPerfectLinksImpl apl : aplNodes) {
                if (apl != null) {
                    apl.stop();
                }
            }
        }
    }

    @Test
    void threeNodeSingleProposal() throws Exception {
        ArrayBlockingQueue<String> commits = new ArrayBlockingQueue<>(3);
        
        // recreate nodes with the commit queue listener
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new HotStuffNode(i, Arrays.asList(0,1,2), aplNodes[i], block -> {
                commits.add(block.getData());
            });
            aplNodes[i].registerListener(nodes[i]);
            nodes[i].start();
        }

        // initial leader is 0
        nodes[0].propose("tx1");

        // wait for commits from all nodes
        for (int i = 0; i < 3; i++) {
            String committed = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx1", committed, "Node should commit tx1");
        }

        // all nodes should have "tx1" in their logs
        for (HotStuffNode h : nodes) {
            List<Block> log = h.getLog();
            assertTrue(log.stream().anyMatch(b -> "tx1".equals(b.getData())), 
                      "Node " + h.getNodeId() + " should have tx1 in log");
        }
    }

    @Test
    void leaderRotationAcrossViews() throws Exception {
        ArrayBlockingQueue<Long> viewChanges = new ArrayBlockingQueue<>(10);
        
        // recreate nodes to track view changes
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new HotStuffNode(i, Arrays.asList(0,1,2), aplNodes[i], block -> {
                // no-op for commits
            });
            aplNodes[i].registerListener(nodes[i]);
            nodes[i].start();
        }

        // Verify initial leader is node 0
        assertEquals(0, nodes[0].getCurrentView(), "Node 0 should start in view 0");
        assertEquals(0, nodes[1].getCurrentView(), "Node 1 should start in view 0");
        assertEquals(0, nodes[2].getCurrentView(), "Node 2 should start in view 0");

        // Node 0 (leader of view 0) proposes tx1
        nodes[0].propose("tx1");
        
        // Wait for views to advance (commit triggers view change)
        Thread.sleep(1000);
        
        // After first commit, all should be in view 1
        assertEquals(1, nodes[0].getCurrentView(), "Node 0 should advance to view 1");
        assertEquals(1, nodes[1].getCurrentView(), "Node 1 should advance to view 1");
        assertEquals(1, nodes[2].getCurrentView(), "Node 2 should advance to view 1");
        
        // In view 1, node 1 should be the leader (view % 3 = 1)
        // Wait a bit for new leader to stabilize and could propose
        Thread.sleep(300);
        
        // Node 1 (leader of view 1) proposes tx2
        nodes[1].propose("tx2");
        
        // Wait for second commit
        Thread.sleep(1000);
        
        // After second commit, all should be in view 2
        assertEquals(2, nodes[0].getCurrentView(), "Node 0 should advance to view 2");
        assertEquals(2, nodes[1].getCurrentView(), "Node 1 should advance to view 2");
        assertEquals(2, nodes[2].getCurrentView(), "Node 2 should advance to view 2");
        
        // In view 2, node 2 should be the leader (view % 3 = 2)
        // Verify all nodes have both transactions in their logs
        for (HotStuffNode h : nodes) {
            List<Block> log = h.getLog();
            assertTrue(log.stream().anyMatch(b -> "tx1".equals(b.getData())), 
                      "Node " + h.getNodeId() + " should have tx1 in log");
            assertTrue(log.stream().anyMatch(b -> "tx2".equals(b.getData())), 
                      "Node " + h.getNodeId() + " should have tx2 in log");
        }
    }

    @Test
    void multipleSequentialProposals() throws Exception {
        ArrayBlockingQueue<String> commits = new ArrayBlockingQueue<>(10);
        
        // recreate nodes with the commit queue listener
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new HotStuffNode(i, Arrays.asList(0,1,2), aplNodes[i], block -> {
                commits.add(block.getData());
            });
            aplNodes[i].registerListener(nodes[i]);
            nodes[i].start();
        }

        // Propose three transactions from different leaders
        // View 0: node 0 proposes tx1
        nodes[0].propose("tx1");
        
        // Collect 3 commits of tx1 (one from each node)
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx1", tx, "Expected tx1 commits");
        }
        
        // Wait for view advancement and leader election
        Thread.sleep(500);
        
        // View 1: node 1 proposes tx2
        nodes[1].propose("tx2");
        
        // Collect 3 commits of tx2 (one from each node)
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx2", tx, "Expected tx2 commits");
        }
        
        // Wait for view advancement and leader election
        Thread.sleep(500);
        
        // View 2: node 2 proposes tx3
        nodes[2].propose("tx3");
        
        // Collect 3 commits of tx3 (one from each node)
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx3", tx, "Expected tx3 commits");
        }
        
        // Verify all nodes have all three transactions
        Thread.sleep(300);
        for (HotStuffNode h : nodes) {
            List<Block> log = h.getLog();
            assertTrue(log.stream().anyMatch(b -> "tx1".equals(b.getData())), 
                      "Node " + h.getNodeId() + " should have tx1");
            assertTrue(log.stream().anyMatch(b -> "tx2".equals(b.getData())), 
                      "Node " + h.getNodeId() + " should have tx2");
            assertTrue(log.stream().anyMatch(b -> "tx3".equals(b.getData())), 
                      "Node " + h.getNodeId() + " should have tx3");
            assertEquals(4, log.size(), 
                        "Node " + h.getNodeId() + " should have genesis + 3 transactions");
        }
    }

    @Test
    void leaderCrashDetectionAndRecovery() throws Exception {
        ArrayBlockingQueue<String> commits = new ArrayBlockingQueue<>(10);
        ArrayBlockingQueue<Long> viewChanges = new ArrayBlockingQueue<>(100); // Larger queue for FD events
        
        // recreate nodes with commit and view change listeners
        for (int i = 0; i < nodes.length; i++) {
            final int nodeId = i;
            nodes[i] = new HotStuffNode(i, Arrays.asList(0, 1, 2), aplNodes[i], block -> {
                try {
                    commits.offer(block.getData());  // Use offer to avoid blocking
                } catch (Exception e) {
                    // ignore
                }
            });
            
            // Register view change listener to track when view changes occur
            nodes[i].registerViewChangeListener((newView, suspectedLeader) -> {
                try {
                    viewChanges.offer(newView);  // Use offer to avoid blocking
                } catch (Exception e) {
                    // ignore
                }
                System.out.println("Node " + nodeId + " detected view change to " + newView + 
                                 " (suspected leader: " + suspectedLeader + ")");
            });
            
            // Use LONGER timeout (1.5s) to avoid false suspicions during normal operation
            // We'll shorten it later when testing actual crashes
            FailureDetector fd = new TimeoutFailureDetector(Arrays.asList(0, 1, 2), 1500);
            nodes[i].setFailureDetector(fd);
            
            aplNodes[i].registerListener(nodes[i]);
            nodes[i].start();
        }
        
        // Send initial new-view from replicas
        nodes[1].sendNewView();
        nodes[2].sendNewView();
        
        Thread.sleep(300);
        
        // Initial state: all in view 0, node 0 is leader
        assertEquals(0, nodes[0].getCurrentView(), "Node 0 should start in view 0");
        assertEquals(0, nodes[1].getCurrentView(), "Node 1 should start in view 0");
        assertEquals(0, nodes[2].getCurrentView(), "Node 2 should start in view 0");
        
        // Node 0 (leader) proposes a transaction
        nodes[0].propose("tx1");
        
        // All nodes should commit tx1 (3 commits, one from each node)
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx1", tx, "Expected tx1 commits");
        }
        
        // After commit, all should be in view 1
        Thread.sleep(500);
        long node0View = nodes[0].getCurrentView();
        long node1View = nodes[1].getCurrentView();
        long node2View = nodes[2].getCurrentView();
        
        System.out.println("\nView after first commit: Node0=" + node0View + " Node1=" + node1View + " Node2=" + node2View);
        assertEquals(1, node0View, "Node 0 should advance to view 1 after commit");
        assertEquals(1, node1View, "Node 1 should advance to view 1 after commit");
        assertEquals(1, node2View, "Node 2 should advance to view 1 after commit");
        
        // Clear view change queue before simulating crash
        viewChanges.clear();
        
        // Now simulate leader crash: stop node 1 (the new leader in view 1)
        // This makes APL unable to receive messages to node 1, simulating a crash
        System.out.println("\n=== Simulating crash of node 1 (leader in view 1) ===");
        aplNodes[1].stop();
        Thread.sleep(100);
        
        // Other nodes should detect the crash and trigger view changes
        // Wait for timeout to expire (1500ms) plus some margin
        Thread.sleep(2000);
        
        // At least some nodes should have detected the crash and moved to view 2
        long view2Node0 = nodes[0].getCurrentView();
        long view2Node2 = nodes[2].getCurrentView();
        
        System.out.println("After leader crash detection:");
        System.out.println("  Node 0 view: " + view2Node0);
        System.out.println("  Node 2 view: " + view2Node2);
        
        assertTrue(view2Node0 >= 2 || view2Node2 >= 2, 
                  "At least one node should detect leader crash and advance view");
        
        // Verify that node 1 still has committed data (crash doesn't lose committed data)
        List<Block> log1 = nodes[1].getLog();
        assertTrue(log1.stream().anyMatch(b -> "tx1".equals(b.getData())),
                  "Node 1 should retain committed tx1 even after crash");
    }

    @Test
    void multipleLeaderChangesUnderFailureDetection() throws Exception {
        ArrayBlockingQueue<String> commits = new ArrayBlockingQueue<>(10);
        
        // For this test, use longer timeouts to avoid false suspicions from message delays
        // We're testing that normal operation works with failure detection enabled
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new HotStuffNode(i, Arrays.asList(0, 1, 2), aplNodes[i], block -> {
                commits.add(block.getData());
            });
            
            // Use 3-second timeout to allow for message delays without false suspicions
            FailureDetector fd = new TimeoutFailureDetector(Arrays.asList(0, 1, 2), 3000);
            nodes[i].setFailureDetector(fd);
            
            aplNodes[i].registerListener(nodes[i]);
            nodes[i].start();
        }
        
        // Send initial new-view
        nodes[1].sendNewView();
        nodes[2].sendNewView();
        Thread.sleep(300);
        
        // View 0: Node 0 proposes tx1
        System.out.println("\n=== View 0: Node 0 proposes tx1 ===");
        nodes[0].propose("tx1");
        
        // Collect 3 commits from all nodes
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx1", tx, "Expected tx1 commits");
        }
        Thread.sleep(500);
        
        // After first proposal, view should have advanced to 1
        assertTrue(nodes[0].getCurrentView() >= 1, "Should advance after commit");
        assertTrue(nodes[1].getCurrentView() >= 1, "Should advance after commit");
        assertTrue(nodes[2].getCurrentView() >= 1, "Should advance after commit");
        
        // View 1: Node 1 should be leader (view 1 % 3 = 1)
        // Propose normally to show non-crash leader rotation still works
        System.out.println("\n=== View 1: Node 1 proposes tx2 ===");
        nodes[1].propose("tx2");
        
        // Collect 3 commits from all nodes
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx2", tx, "Expected tx2 commits");
        }
        Thread.sleep(500);
        
        // View 2: Node 2 is now leader (view 2 % 3 = 2)
        System.out.println("\n=== View 2: Node 2 proposes tx3 ===");
        nodes[2].propose("tx3");
        
        // Collect 3 commits from all nodes
        for (int i = 0; i < 3; i++) {
            String tx = commits.poll(5, TimeUnit.SECONDS);
            assertEquals("tx3", tx, "Expected tx3 commits");
        }
        
        // All nodes should have all 3 transactions
        Thread.sleep(300);
        for (HotStuffNode h : nodes) {
            List<Block> log = h.getLog();
            assertTrue(log.stream().anyMatch(b -> "tx1".equals(b.getData())));
            assertTrue(log.stream().anyMatch(b -> "tx2".equals(b.getData())));
            assertTrue(log.stream().anyMatch(b -> "tx3".equals(b.getData())));
        }
        
        System.out.println("\nTest completed successfully with failure detection enabled!");
    }
}
