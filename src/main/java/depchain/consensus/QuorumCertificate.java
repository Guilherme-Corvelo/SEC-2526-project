package depchain.consensus;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Quorum Certificate: evidence that a quorum (⌈n/2⌉ + 1) of nodes
 * have voted for a block in a given view.
 * 
 * For Step 3 (no Byzantine faults), we simplify: just track the votes.
 * In Step 5 with Byzantine faults, we would include aggregated signatures.
 */
public class QuorumCertificate implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final long view;
    private final byte[] blockHash;
    private final Map<Integer, Integer> votes; // nodeId -> dummy value (just for counting)
    private final int requiredVotes;
    
    public QuorumCertificate(long view, byte[] blockHash, int requiredVotes) {
        this.view = view;
        this.blockHash = blockHash.clone();
        this.votes = new HashMap<>();
        this.requiredVotes = requiredVotes;
    }
    
    /**
     * Add a vote from a node. Returns true if quorum is now reached.
     */
    public boolean addVote(int nodeId) {
        votes.putIfAbsent(nodeId, 1);
        return isSatisfied();
    }
    
    /**
     * Check if the quorum threshold has been reached.
     */
    public boolean isSatisfied() {
        return votes.size() >= requiredVotes;
    }
    
    public long getView() {
        return view;
    }
    
    public byte[] getBlockHash() {
        return blockHash.clone();
    }
    
    public int getVoteCount() {
        return votes.size();
    }
    
    @Override
    public String toString() {
        return "QC[view=" + view + ", blockHash=" + Block.hashToString(blockHash) +
                ", votes=" + votes.size() + "/" + requiredVotes + "]";
    }
}
