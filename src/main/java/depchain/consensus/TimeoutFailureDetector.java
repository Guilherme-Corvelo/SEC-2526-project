package depchain.consensus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TimeoutFailureDetector: Suspects a node crashed if we don't hear from it within a timeout.
 *
 * This implements an Eventually Perfect Failure Detector (◇P) suitable for crash-only faults:
 * - Eventually: After some time, all crashed processes are suspected, and no correct process is suspected.
 * - Perfect: Once a process is suspected, it remains suspected (no recovery for crashed processes).
 *
 * For this implementation, we allow suspicion to be cleared when we hear from a node again,
 * supporting temporary network partitions and node recovery.
 */
public class TimeoutFailureDetector implements FailureDetector {
    private final List<Integer> nodeIds;
    private final long timeoutMillis;

    // Track last heartbeat time for each node
    private final Map<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();

    // Track suspected nodes
    private final Set<Integer> suspected = Collections.synchronizedSet(new HashSet<>());

    // Monitor thread
    private Thread monitorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TimeoutFailureDetector(List<Integer> nodeIds, long timeoutMillis) {
        this.nodeIds = new ArrayList<>(nodeIds);
        this.timeoutMillis = timeoutMillis;

        // Initialize all nodes with current time (assume alive at start)
        long now = System.currentTimeMillis();
        for (int id : nodeIds) {
            lastHeartbeat.put(id, now);
        }
    }

    @Override
    public void heartbeat(int nodeId) {
        lastHeartbeat.put(nodeId, System.currentTimeMillis());
        // If we hear from a suspected node, clear the suspicion
        suspected.remove(nodeId);
    }

    @Override
    public boolean isSuspected(int nodeId) {
        return suspected.contains(nodeId);
    }

    @Override
    public int[] getSuspectedNodes() {
        return suspected.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void clearSuspicion(int nodeId) {
        suspected.remove(nodeId);
    }

    @Override
    public void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        monitorThread = new Thread(this::monitorLoop, "FD-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    /**
     * Monitor loop: periodically check for suspected nodes based on timeout.
     */
    private void monitorLoop() {
        while (running.get()) {
            try {
                long now = System.currentTimeMillis();
                for (int nodeId : nodeIds) {
                    Long lastHb = lastHeartbeat.get(nodeId);
                    if (lastHb != null && now - lastHb > timeoutMillis) {
                        // Timeout expired, suspect the node
                        suspected.add(nodeId);
                    }
                }
                // Check every 100ms (reasonable trade-off between responsiveness and CPU)
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // shutdown
                break;
            }
        }
    }

    /**
     * Return the timeout value (for testing).
     */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }
}
