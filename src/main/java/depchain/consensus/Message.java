package depchain.consensus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import com.weavechain.curve25519.Scalar;
import com.weavechain.sig.ThresholdSigEd25519;

import depchain.network.APL;

public class Message implements Serializable{
    private Type type;
    private int viewNumber;
    private Node node;
    private QuorumCertificate justify = null;
    private Scalar  partialSign = null; 

    public Message(Type type, int viewNumber, Node node){
        this.type = type;
        this.viewNumber = viewNumber;
        this.node = node;
    }

    public Scalar replicaComputeRi(Scalar privateShare, ThresholdSigEd25519 sigHelper , APL apl){
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

    public Scalar getPartialSign() {
        return this.partialSign;
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
            !this.node.equals(node)){
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
            if (!this.partialSign.equals(other.partialSign)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "Message[viewNumber=" + viewNumber + ", type=" + type +
               ", node=" + node.toString() + ", justify=" + (justify != null ? justify.toString() : "null") +
               ", partialSign=" + (partialSign != null ? partialSign.toString() : "null") + "]";
    }
}