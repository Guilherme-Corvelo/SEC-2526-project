package depchain.consensus;

import java.security.NoSuchAlgorithmException;
import java.util.Set;

import com.weavechain.curve25519.Scalar;
import com.weavechain.sig.ThresholdSigEd25519;

import depchain.network.AuthenticatedPerfectLinksImpl;

public class Message {
    private int type;
    private int viewNumber;
    private Node node;
    private QuorumCertificate qc = null;
    private Scalar  partialSign = null; 

    public Message(int type, int viewNumber, Node node){
        this.type = type;
        this.viewNumber = viewNumber;
        this.node = node;
    }

    private Scalar replicaComputeRi(Scalar privateShare, ThresholdSigEd25519 sigHelper , AuthenticatedPerfectLinksImpl apl){
        try {
            Scalar Ri = sigHelper.computeRi(privateShare, node.getAction());
            return Ri;
            //todo send to leader
        } catch (NoSuchAlgorithmException e) {
            System.err.print("Library bad!");
        }
        return null;        
    }

    public void Vote(int myIndex, Scalar privateShare, Scalar Ri, Scalar k, Set<Integer> nodes, ThresholdSigEd25519 sigHelper) {
        // Todo receive info from Leader
        Scalar partialSign = sigHelper.computeSignature(myIndex, privateShare, Ri, k, nodes);
        this.partialSign = partialSign;
        //Todo Send this
    }

    public void setQC(QuorumCertificate qc){
        this.qc = qc;
    }
}
