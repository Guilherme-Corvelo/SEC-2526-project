package depchain.consensus;

/**
 * FailureDetector: Interface for detecting crashed processes.
 *
 * In a crash-only model, a process is either alive or crashed (no Byzantine behavior).
 * A failure detector tracks suspected crashed nodes and provides:
 * - Suspicion mechanism: Report if a node is suspected crashed
 * - Recovery mechanism: Clear suspicion when node becomes active again
 */
public interface FailureDetector {
    /**
     * Report that we've heard from a node (reset its timeout).
     *
     * @param nodeId the node ID
     */
    void heartbeat(int nodeId);

    /**
     * Check if a node is currently suspected to be crashed.
     *
     * @param nodeId the node ID
     * @return true if suspected crashed, false otherwise
     */
    boolean isSuspected(int nodeId);

    /**
     * Get the IDs of all suspected crashed nodes.
     *
     * @return array of suspected node IDs (may be empty)
     */
    int[] getSuspectedNodes();

    /**
     * Clear suspicion on a node (e.g., when we hear from it again).
     *
     * @param nodeId the node ID
     */
    void clearSuspicion(int nodeId);

    /**
     * Start the failure detector (begins timeout checking).
     */
    void start();

    /**
     * Stop the failure detector.
     */
    void stop();
}
