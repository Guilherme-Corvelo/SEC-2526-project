package depchain.consensus;

import java.util.HashMap;
import java.util.Map;

/**
 * ByzantineQuorumCertificate: A Byzantine-fault-tolerant quorum certificate.
 * 
 * Extends QuorumCertificate with cryptographic signatures. In Byzantine HotStuff,
 * a quorum certificate requires 2f+1 signed votes from distinct nodes
 * (where f = ⌊(n-1)/3⌋ is the maximum number of faulty nodes).
 * 
 * Each vote is cryptographically signed; the QC aggregates signatures and tracks which
 * nodes contributed to establish the quorum.
 */
public class ByzantineQuorumCertificate extends QuorumCertificate {
    private static final long serialVersionUID = 1L;
    
    private final String phase;  // "prepare", "precommit", or "commit"
    private final Map<Integer, SignedVote> signedVotes = new HashMap<>();
    
    public ByzantineQuorumCertificate(long view, String phase, byte[] blockHash, int requiredVotes) {
        super(view, blockHash, requiredVotes);
        this.phase = phase;
    }
    
    public String getPhase() {
        return phase;
    }
    
    /**
     * Add a signed vote to this QC.
     */
    public void addVote(int nodeId, SignedVote vote) {
        signedVotes.put(nodeId, vote);
        super.addVote(nodeId);  // Also track in parent
    }
    
    /**
     * Get the signed votes that constitute this QC.
     */
    public Map<Integer, SignedVote> getSignedVotes() {
        return new HashMap<>(signedVotes);
    }
    
    @Override
    public String toString() {
        return "ByzQC[view=" + getView() + ", phase=" + phase + 
               ", votes=" + getVoteCount() + "/" + super.toString().split("/")[1];
    }
}
