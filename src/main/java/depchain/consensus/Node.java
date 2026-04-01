package depchain.consensus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.io.Serializable;

import depchain.blockchain.Transaction;

public class Node implements Serializable{
    private byte[] parentLink;
    private List<Transaction> proposedTransactions;

    public Node (List<Transaction> proposedTransactions, byte[] parentLink){
        this.parentLink = parentLink;
        this.proposedTransactions = proposedTransactions;
    }

    public Node (List<Transaction> proposedTransactions, Node parentNode ){
        try{
            this.parentLink = extend(parentNode);
            this.proposedTransactions = proposedTransactions;
        } catch (NoSuchAlgorithmException e ) {
            System.err.print("Couldnt hash node parent link");
        }
    }

    public Node (){
        this.parentLink = null;
        this.proposedTransactions = List.of();
    }

    private byte[] extend(Node prevNode) throws NoSuchAlgorithmException{
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        if (prevNode.getParentLink() != null){
            md.update(prevNode.getParentLink());
        }
        md.update(serializeTransactions(prevNode.proposedTransactions));
        return  md.digest();
    }

    private byte[] serializeTransactions(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new byte[0];
        }

        StringBuilder serialized = new StringBuilder();

        for (Transaction transaction : transactions) {
            serialized
                .append(transaction.getFrom()).append("|")
                .append(transaction.getTo() != null ? transaction.getTo() : "null").append("|")
                .append(transaction.getInput() != null ? transaction.getInput() : "").append("|")
                .append(transaction.getValue()).append("|")
                .append(transaction.getNonce()).append("|")
                .append(transaction.getGasPrice()).append("|")
                .append(transaction.getGasLimit()).append(";");
        }

        return serialized.toString().getBytes();
    }

    public List<Transaction> getProposedTransactions(){
        return this.proposedTransactions;
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
        return Arrays.equals(getParentLink(), other.getParentLink()) &&
            this.proposedTransactions.equals(other.proposedTransactions);
    }

    @Override
    public String toString() {
        return "Node[proposedTransactions=" + proposedTransactions + 
            ", parentLink=" + (parentLink != null ? HexFormat.of().formatHex(getParentLink()) : "null") + "]";
    }
}
