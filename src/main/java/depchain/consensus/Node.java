package depchain.consensus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Node {
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
}
