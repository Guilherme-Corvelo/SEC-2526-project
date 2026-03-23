package depchain.consensus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.io.Serializable;
import java.lang.String;

import depchain.Debug;

public class Node implements Serializable{
    private byte[] parentLink;
    private String action;

    public Node (String action, byte[] parentLink){
        this.parentLink = parentLink;
        this.action = action;
    }

    public Node (String action, Node parentNode ){
        try{
            this.parentLink = extend(parentNode);
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
        if (prevNode.getParentLink() != null){
            md.update(prevNode.getParentLink());
        }
        //Debug.debug(prevNode.action);
        md.update(prevNode.action.getBytes());
        return  md.digest();
    }

    public String getAction(){
        return this.action;
    }

    public byte[] getParentLink() {
        return this.parentLink;
    }

    public boolean canExtend(Node other){
        try {
            return Arrays.equals(getParentLink(), extend(other));
        } catch (Exception e) {
            return false;
        } 
    }

    public boolean canExtend(Message msg){
        try {
            if(msg.getjustify() == null){
                return true;
            }
            return Arrays.equals(getParentLink(), extend(msg.getjustify().getNode()));
        } catch (Exception e) {
            return false;
        } 
    }
    
    @Override
    public boolean equals(Object obj){

        if (!(obj instanceof Node)) {
            return false;
        }
        
        Node other = (Node) obj;
        return Arrays.equals(getParentLink(), other.getParentLink()) && this.action.equals(other.action);
    }

    @Override
    public String toString() {
        return "Node[action=" + action + 
            ", parentLink=" + (parentLink != null ? HexFormat.of().formatHex(getParentLink()) : "null") + "]";
    }
}
