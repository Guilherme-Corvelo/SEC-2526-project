package depchain.consensus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import depchain.Debug;

import java.io.Serializable;
import java.lang.String;
import java.nio.charset.StandardCharsets;

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
        Debug.debug(prevNode.action);
        //TODO: BYTES are fucked, do claude search for byte equals bytes are cooked prints of bytes are cooked
        Debug.debug("BBBBBBBBBBBBBBBB" + java.util.Arrays.toString(prevNode.getAction().getBytes(StandardCharsets.UTF_8)));
        md.update(prevNode.action.getBytes(StandardCharsets.UTF_8));
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
            return java.util.Arrays.equals(getParentLink(), extend(other));
        } catch (Exception e) {
            return false;
        } 
    }

    public boolean canExtend(Message msg){
        try {
            if(msg.getjustify() == null){
                return true;
            }
            Debug.debug("BYTES: "+ java.util.Arrays.toString(extend(msg.getjustify().getNode())) +" "+ java.util.Arrays.toString(getParentLink()));
            return java.util.Arrays.equals(getParentLink(), extend(msg.getjustify().getNode()));
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
        
        if (java.util.Arrays.equals(getParentLink(), other.getParentLink()) && this.action.equals(other.action)){
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Node[action=" + action + 
            ", parentLink=" + (parentLink != null ? java.util.Arrays.toString(getParentLink()) : "null") + "]";
    }
}
