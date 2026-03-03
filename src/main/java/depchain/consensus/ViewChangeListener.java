package depchain.consensus;

/**
 * ViewChangeListener: Callback for view change events triggered by failure detection.
 *
 * When a node suspects the current leader has crashed, it triggers a view change.
 * This listener is notified of such events.
 */
public interface ViewChangeListener {
    /**
     * Called when a view change is triggered (e.g., current leader suspected).
     *
     * @param newView the new view number
     * @param suspectedLeader the suspected crashed leader from the previous view
     */
    void onViewChange(long newView, int suspectedLeader);
}
