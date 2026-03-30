package depchain.blockchain;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.SimpleWorld;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Set;


public class Block {
 
    private String blockHash;
    private String previousBlockHash;
    private List<BlockTransaction> transactions;
    private Map<String, BlockAccount> state;  
    
    public Block(int blockNumber, String previousBlockHash, List<Transaction> transactions, SimpleWorld worldState, Set<Address> knownAddresses) {

        this.previousBlockHash = previousBlockHash;

        this.state = new HashMap<>();

        for (Address address : knownAddresses) {

            MutableAccount account = (MutableAccount) worldState.get(address);

            if (account == null) {
                continue;
            }

            BlockAccount blockAccount = new BlockAccount();
            blockAccount.balance = account.getBalance().toDecimalString();
            blockAccount.nonce = account.getNonce();
            
            if (account.getCode() != null && !account.getCode().isEmpty()) {
                blockAccount.code = account.getCode().toHexString();
            }
            else {
                blockAccount.code = null;
            }

            String addressKey = address.toHexString();

            if (!addressKey.startsWith("0x")) {
                addressKey = "0x" + addressKey;
            }

            this.state.put(addressKey, blockAccount);
        }

        this.transactions = transactions.stream().map(transaction -> {
            BlockTransaction blockTransaction = new BlockTransaction();
            blockTransaction.from = transaction.getFrom();
            blockTransaction.to = transaction.getTo();
            blockTransaction.input = transaction.getInput();
            blockTransaction.value = transaction.getValue();
            blockTransaction.nonce = transaction.getNonce();
            blockTransaction.gasPrice = transaction.getGasPrice();
            blockTransaction.gasLimit = transaction.getGasLimit();
            return blockTransaction;
        }).toList();

        this.blockHash = computeHash(blockNumber);
    }

    private String computeHash(int blockNumber) {

        try {

            StringBuilder sb = new StringBuilder();
            sb.append(blockNumber);
            sb.append(previousBlockHash != null ? previousBlockHash : "null");
 
            for (BlockTransaction transaction : transactions) {
                sb.append(transaction.from);
                sb.append(transaction.to != null ? transaction.to : "null");
                sb.append(transaction.input != null ? transaction.input : "");
                sb.append(transaction.value);
                sb.append(transaction.nonce);
                sb.append(transaction.gasPrice);
                sb.append(transaction.gasLimit);
            }
 
            state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    sb.append(e.getKey());
                    sb.append(e.getValue().balance);
                    sb.append(e.getValue().nonce);
                });
 
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8) );
            
            StringBuilder hex = new StringBuilder();
            
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            
            return hex.toString();
 
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute block hash", e);
        }
    }



    //Gson
    private Block() {}

    public String getBlockHash() {
        return blockHash;
    }

    public String getPreviousBlockHash() {
        return previousBlockHash;
    }

    public List<BlockTransaction> getTransactions() {
        return transactions;
    }

    public Map<String, BlockAccount> getState() {
        return state;
    }
}
