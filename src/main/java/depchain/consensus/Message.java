package depchain.consensus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import depchain.blockchain.ExecutionResult;
import threshsig.KeyShare;
import threshsig.SigShare;

public class Message implements Serializable{
    private Type type;
    private int viewNumber;
    private Node node;
    private QuorumCertificate justify = null;
    private SigShare partialSign = null;
    private List<ExecutionResult> executionResults = null;

    private int requesterId;

    public Message(Type type, int viewNumber, Node node, int requesterId){
        this.type = type;
        this.viewNumber = viewNumber;
        this.node = node;
        this.requesterId = requesterId;
    }

    /* public Scalar replicaComputeRi(Scalar privateShare, ThresholdSigEd25519 sigHelper , APL apl){
        try {
            Scalar Ri = sigHelper.computeRi(privateShare, node.getAction());
            return Ri;
            //todo send to leader
        } catch (NoSuchAlgorithmException e) {
            System.err.print("Library bad!");
        }
        return null;        
    } */

    /* 
    public void Vote(int myIndex, Scalar privateShare, Scalar Ri, Scalar k, Set<Integer> nodes, ThresholdSigEd25519 sigHelper) {
        // Todo receive info from Leader
        Scalar partialSign = sigHelper.computeSignature(myIndex, privateShare, Ri, k, nodes);
        this.partialSign = partialSign;
        //Todo Send this
    }
    */

    public void vote(KeyShare share){
        byte[] typeData = this.getType().name().getBytes(StandardCharsets.UTF_8);
        byte[] viewData = ByteBuffer.allocate(Integer.BYTES).putInt(this.getView()).array();
        byte[] nodeData = this.getNode().serialize();
        byte[] data = ByteBuffer.allocate(typeData.length + viewData.length + nodeData.length)
                        .put(typeData).put(nodeData).put(viewData).array(); 

        this.partialSign = share.sign(data);
    }
    public void setJustify(QuorumCertificate justify){
        this.justify = justify;
    }

    public Type getType(){
        return this.type;
    }

    public int getView(){
        return this.viewNumber;
    }

    public Node getNode(){
        return this.node;
    }

    public QuorumCertificate getjustify(){
        return this.justify;
    }

    public SigShare getPartialSign() {
        return this.partialSign;
    }

    public int getRequesterId(){
        return this.requesterId;
    }

    public List<ExecutionResult> getExecutionResults() {
        return this.executionResults;
    }

    public void setExecutionResults(List<ExecutionResult> executionResults) {
        this.executionResults = executionResults;
    }

    public byte[] serialize(){
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            return bos.toByteArray();
        } catch (Exception e) {
            System.err.println("Failed to serialize msg");
        }
        return null;
    }
    
    public static  Message deserialize(byte[] bytes ) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (Message) ois.readObject();
        } catch (Exception e) {
            System.err.println("Failed to deserialize msg");
        }
        return null;
    }

    @Override
    public boolean equals(Object obj){
        if (!(obj instanceof Message)) {
            return false;
        }
        
        Message other = (Message) obj;

        if (!(this.type == other.type) ||
            !(this.viewNumber == other.viewNumber ) ||
            !(this.requesterId == other.requesterId) ||
            !this.node.equals(other.node)){
            return false;
        }

        if (this.justify == null) {
            if (other.justify != null) {
                return false;
            }
        } else {
            if (!this.justify.equals(other.justify)) {
                return false;
            }
        }

        if (this.partialSign == null) {
            if (other.partialSign != null) {
                return false;
            }
        } else {
            if (!Arrays.equals(this.partialSign.getBytes(), other.partialSign.getBytes())) {
                return false;
            }
        }

        if (this.executionResults == null) {
            if (other.executionResults != null) {
                return false;
            }
        } else if (!this.executionResults.equals(other.executionResults)) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "Message[viewNumber=" + viewNumber + ", type=" + type +
               ", node=" + node.toString() + ", justify=" + (justify != null ? justify.toString() : "null") +
               ", partialSign=" + (partialSign != null ? HexFormat.of().formatHex(partialSign.getBytes()) : "null") +
               ", executionResults=" + (executionResults != null ? executionResults.toString() : "null") + "]";
    }
}
