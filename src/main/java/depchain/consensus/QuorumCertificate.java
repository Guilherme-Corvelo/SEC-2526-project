package depchain.consensus;

import java.io.Serializable;
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

    private Type type;
    private int view;
    private Node node;
    private byte[] tresholdSig;
    
    public QuorumCertificate(Type type, int view, Node node, byte[] tresholdSig) {
        this.type = type;
        this.view = view;
        this.node = node;
        this.tresholdSig = tresholdSig;
    }

    public Type getType(){
        return this.type;
    }

    public int getView() {
        return this.view;
    }

    public Node getNode() {
        return this.node;
    }

    public byte[] getSignature() {
        return this.tresholdSig;
    }

    public boolean equals(QuorumCertificate qc){
        if (this.type == qc.type && this.view == qc.view && this.node.equals(qc.node)){
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object obj){
        if (!(obj instanceof QuorumCertificate)) {
            return false;
        }
        
        QuorumCertificate other = (QuorumCertificate) obj;

        if (!(this.type == other.type) ||
            !(this.view == other.view ) ||
            !this.node.equals(node)){
            return false;
        }

        if (this.node == null) {
            if (other.node != null) {
                return false;
            }
        } else {
            if (!this.node.equals(other.node)) {
                return false;
            }
        }

        if (this.tresholdSig == null) {
            if (other.tresholdSig != null) {
                return false;
            }
        } else {
            if (!this.tresholdSig.equals(other.tresholdSig)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "QuorumCertificate[viewNumber=" + view + ", type=" + type +
               ", node=" + node.toString() + ", treshsig=" + (tresholdSig) + "]";
    }
}
