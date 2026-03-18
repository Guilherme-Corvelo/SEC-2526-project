package depchain.consensus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.Serializable;
import java.lang.String;

public class Node implements Serializable{
    private byte[] parentLink;
    private String action;

    public Node (String action, byte[] parentLink){
        this.parentLink = parentLink;
        this.action = action;
    }

    public Node (String action, Node mode ){
        try{
            this.parentLink = extend(mode);
            this.action = action;
        } catch (NoSuchAlgorithmException e ) {
            System.err.print("Couldnt hash node parent link");
        }
    }

    public Node (){
        this.parentLink = null;
        this.action = "Genesis";
    }

    private byte[] extend(Node prevNode) throws NoSuchAlgorithmException{
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(prevNode.getParentLink());
        return  md.digest();
    }

    public String getAction(){
        return this.action;
    }

    public byte[] getParentLink() {
        return this.parentLink;
    }
    
    @Override
    public boolean equals(Object obj){

        if (!(obj instanceof Node)) {
            return false;
        }
        
        Node other = (Node) obj;
        
        if (this.parentLink == other.parentLink && this.action.equals(other.action)){
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Node[action=" + action + 
            ", parentLink=" + (parentLink != null ? parentLink.toString() : "null") + "]";
    }
}
