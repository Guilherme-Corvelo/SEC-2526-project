package depchain.consensus;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ByzantineQuorumCertificate: A Byzantine-fault-tolerant quorum certificate.
 * 
 * In Byzantine HotStuff, a quorum certificate requires 2f+1 signed votes from distinct nodes
 * (where f = ⌊(n-1)/3⌋ is the maximum number of faulty nodes).
 * 
 * Each vote is cryptographically signed; the QC aggregates signatures and tracks which
 * nodes contributed to establish the quorum.
 */
public class QuorumCertificate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long view;
    private final byte[] blockHash;
    private final int requiredVotes;
    private final String phase;  // "prepare", "precommit", or "commit"
    private final Map<Integer, SignedVote> signedVotes = new HashMap<>();
    
    public QuorumCertificate(long view, String phase, byte[] blockHash, int requiredVotes) {
        this.view = view;
        this.blockHash = blockHash.clone();
        this.requiredVotes = requiredVotes;
        this.phase = phase;
    }

    public QuorumCertificate(long view, String phase, byte[] blockHash, int requiredVotes,
                                                        Map<Integer, SignedVote> votes) {
        this(view, phase, blockHash, requiredVotes);

        for (SignedVote signedVote : votes.values()) {
            if (signedVote.getView() != view) {
                continue;
            }
            if (!phase.equals(signedVote.getPhase())) {
                continue;
            }
            if (!Arrays.equals(blockHash, signedVote.getBlockHash())) {
                continue;
            }
            this.addVote(signedVote.getSenderId(), signedVote);
        }
    }

    public long getView() {
        return view;
    }

    public byte[] getBlockHash() {
        return blockHash.clone();
    }

    public int getVoteCount() {
        return signedVotes.size();
    }

    public boolean isSatisfied() {
        return signedVotes.size() >= requiredVotes;
    }
    
    public String getPhase() {
        return phase;
    }
    
    /**
     * Add a signed vote to this QC.
     */
    public void addVote(int nodeId, SignedVote vote) {
        signedVotes.put(nodeId, vote);
    }
    
    /**
     * Get the signed votes that constitute this QC.
     */
    public Map<Integer, SignedVote> getSignedVotes() {
        return new HashMap<>(signedVotes);
    }
    
    @Override
    public String toString() {
        return "ByzQC[view=" + view + ", phase=" + phase +
               ", votes=" + signedVotes.size() + "/" + requiredVotes + "]";
    }
}
