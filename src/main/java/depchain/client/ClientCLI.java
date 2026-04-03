package depchain.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import depchain.crypto.KeyVault;
import depchain.blockchain.Transaction;
import depchain.blockchain.ExecutionResult;
import depchain.consensus.Message;
import java.security.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;

public class ClientCLI {

    static final String ID_TOTAL_SUPPLY = "18160ddd";
    static final String ID_BALANCE_OF = "70a08231";
    static final String ID_TRANSFER = "a9059cbb";
    static final String ID_APPROVE = "095ea7b3";
    static final String ID_ALLOWANCE = "dd62ed3e";
    static final String ID_TRANSFER_FROM = "23b872dd";
    static final String ID_INCREASE_ALLOWANCE = "39509351";
    static final String ID_DECREASE_ALLOWANCE = "a457c2d7";

    private static final String IST_COIN_ADDRESS = "1234567891234567891234567891234567891234";
    private static final int F = 1;
 
    private static final Map<Integer, InetSocketAddress> SERVER_ADDRESSES = new HashMap<>() {{
        put(3, new InetSocketAddress("localhost", 20003));
        put(4, new InetSocketAddress("localhost", 20004));
        put(5, new InetSocketAddress("localhost", 20005));
        put(6, new InetSocketAddress("localhost", 20006));
    }};
 
    private static final Map<Integer, String> CLIENT_ACCOUNTS_ADDRESSES = new HashMap<>() {{
        put(0, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        put(1, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        put(2, "cccccccccccccccccccccccccccccccccccccccc");
    }};
    
    private final String myAddress;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String istCoinAddress;
    private final Client client;
    private long nonce;
    private long defaultGasPrice = 1;
    private long defaultGasLimit = 100_000;

    public ClientCLI(String myAddress, PrivateKey privateKey, PublicKey publicKey, String istCoinAddress,
                     long initialNonce, Client client) {
    
        this.myAddress = myAddress;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.istCoinAddress = istCoinAddress;
        this.nonce = initialNonce;
        this.client = client;
    }

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.err.println("Usage ClientCLI <clientId> <clientPort>");
            System.err.println("Example: ClientCLI 1 20000");
            System.exit(1);
        }

        int clientId   = Integer.parseInt(args[0]);
        int clientPort = Integer.parseInt(args[1]);

        System.out.println("Starting DepChain client...");
        System.out.println("  Client ID:   " + clientId);
        System.out.println("  Client Port: " + clientPort);

        if (!KeyVault.keysExist()) {
            System.err.println("The keys do not exist yet. Run KeyVault.java to generate all client and server keys.");
            System.err.println("mvn exec:java -Dexec.mainClass=\"depchain.crypto.KeyVault\"");
            System.exit(1);
        }

        PrivateKey privateKey = loadPrivateKey(clientId);
        Map<Integer, PublicKey> publicKeys = loadAllPublicKeys();

        String myAddress = CLIENT_ACCOUNTS_ADDRESSES.get(clientId);
        if (myAddress == null) {
            System.err.println("Unknown clientId: " + clientId);
            System.exit(1);
        }

        Client client = new Client(clientId, clientPort, SERVER_ADDRESSES, privateKey, publicKeys, F);
        ClientCLI cli = null;
        if(clientId==0){
            cli = new ClientCLI(myAddress, privateKey, publicKeys.get(clientId), IST_COIN_ADDRESS, 1, client);
        }
        else{
            cli = new ClientCLI(myAddress, privateKey, publicKeys.get(clientId), IST_COIN_ADDRESS, 0, client);
        }

        cli.run();
    }

    public void run() {

        Scanner scanner = new Scanner(System.in);
        printHelp();

        while (true) {

            System.out.print("\nDepchain> ");

            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String type = parts[0].toLowerCase();
            String command = "";

            if (parts.length > 1) {
                command = parts[1].toLowerCase();
            }

            try {
                switch (type) {
                    case "exit":
                        scanner.close();
                        System.exit(0);              
                    case "help":
                        printHelp();
                        break;
                    case "depcoin":
                        handleDepCoin(command, parts);
                        break;
                    case "ist":
                        handleIST(command, parts);
                        break;
                    case "gas":
                        handleGas(command, parts);
                        break;

                    default:
                        System.out.println("Unknown command. Type 'help' for options");
                        break;
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid arguments: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

    }

    private void handleDepCoin(String command, String[] parts) throws Exception {
        switch (command) {
            case "transfer":
                requireArgs(parts, 4, "depcoin transfer <to_address> <amount>");
                handleDepCoinTransfer(parts);
                break;
            case "balance":
                requireArgs(parts, 3, "depcoin balance <address>");
                handleDepCoinBalance(parts);
                break;
        
            default:
                System.out.println("Unknown depcoin command. Available: transfer, balance");
                break;
        }

    }

    private void handleDepCoinTransfer(String[] parts) throws Exception {
        String to = parts[2];
        long amount = parseLong(parts[3], "amount");

        Transaction transaction = buildDepCoinTransfer(to, amount);
        submit(transaction);

        System.out.println("DepCoin transfer submitted.");
        System.out.println("  To:     " + to);
        System.out.println("  Amount: " + amount);
        System.out.println("  From:   " + myAddress);
    }

    private void handleDepCoinBalance(String[] parts) throws Exception {
        String address = parts[2];
        Transaction transaction = buildDepCoinBalanceQuery(address);
        submit(transaction);
        System.out.println("DepCoin balance query submitted for: " + address);
    }

    private void handleIST(String command, String[] parts) throws Exception {
        switch (command) {
 
            case "transfer":
                requireArgs(parts, 4, "ist transfer <to_address> <amount>");
                handleISTTransfer(parts);
                break;
 
            case "approve":
                requireArgs(parts, 4, "ist approve <spender_address> <amount>");
                handleISTApprove(parts);
                break;
 
            case "increase-allowance":
                requireArgs(parts, 4, "ist increase-allowance <spender_address> <amount>");
                handleISTIncreaseAllowance(parts);
                break;
 
            case "decrease-allowance":
                requireArgs(parts, 4, "ist decrease-allowance <spender_address> <amount>");
                handleISTDecreaseAllowance(parts);
                break;
 
            case "transfer-from":
                requireArgs(parts, 5, "ist transfer-from <from_address> <to_address> <amount>");
                handleISTTransferFrom(parts);
                break;
 
            case "balance":
                requireArgs(parts, 3, "ist balance <address>");
                handleISTBalance(parts);
                break;
 
            case "allowance":
                requireArgs(parts, 4, "ist allowance <owner_address> <spender_address>");
                handleISTAllowance(parts);
                break;

            case "total-supply":
                handleISTTotalSupply();
                break;
 
            default:
                System.out.println("Unknown ist command. Type 'help' for options.");
                break;
        }
    }

    private void handleISTTransfer(String[] parts) throws Exception {
        String to = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        submit(buildContractCall(ID_TRANSFER + padAddress(to) + padUint256(amount)));
        System.out.println("IST transfer submitted -> to: " + to + "  amount: " + amount);
    }

    private void handleISTApprove(String[] parts) throws Exception {
        String spender    = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        submit(buildContractCall(ID_APPROVE + padAddress(spender) + padUint256(amount)));
        System.out.println("IST approve submitted -> spender: " + spender + "  amount: " + amount);
        if (amount.compareTo(BigInteger.ZERO) != 0) {
            System.out.println("  Warning: only safe if current allowance is 0.");
            System.out.println("  To change an existing allowance use increase/decrease-allowance.");
        }
    }

    private void handleISTIncreaseAllowance(String[] parts) throws Exception {
        String spender = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        submit(buildContractCall(ID_INCREASE_ALLOWANCE + padAddress(spender) + padUint256(amount)));
        System.out.println("IST increaseAllowance submitted -> spender: " + spender + "  added: " + amount);
    }

    private void handleISTDecreaseAllowance(String[] parts) throws Exception {
        String spender = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        submit(buildContractCall(ID_DECREASE_ALLOWANCE + padAddress(spender) + padUint256(amount)));
        System.out.println("IST decreaseAllowance submitted -> spender: " + spender + "  removed: " + amount);
    }

    private void handleISTTransferFrom(String[] parts) throws Exception {
        String from = parts[2];
        String to = parts[3];
        BigInteger amount = parseBigInt(parts[4], "amount");

        submit(buildContractCall(ID_TRANSFER_FROM + padAddress(from) + padAddress(to) + padUint256(amount)));
        System.out.println("IST transferFrom submitted -> from: " + from + "  to: " + to + "  amount: " + amount);
    }

    private void handleISTBalance(String[] parts) throws Exception {
        submit(buildContractCall(ID_BALANCE_OF + padAddress(parts[2])));
        System.out.println("IST balanceOf query submitted for: " + parts[2]);
    }

    private void handleISTAllowance(String[] parts) throws Exception {
        submit(buildContractCall(ID_ALLOWANCE + padAddress(parts[2]) + padAddress(parts[3])));
        System.out.println("IST allowance query submitted -> owner: " + parts[2] + "  spender: " + parts[3]);
    }

    private void handleISTTotalSupply() throws Exception {
        submit(buildContractCall(ID_TOTAL_SUPPLY));
        System.out.println("IST totalSupply query submitted.");
    }

    private void handleGas(String command, String[] parts) throws Exception {
        switch (command) {
            case "price":
                requireArgs(parts, 3, "gas price <value>");
                handleGasPrice(parts);
                break;

            case "limit":
                requireArgs(parts, 3, "gas limit <value>");
                handleGasLimit(parts);
                break;
            
            case "show":
                System.out.println("Gas price: " + defaultGasPrice);
                System.out.println("Gas limit: " + defaultGasLimit);
                break;

            default:
                System.out.println("Unknown gas command. Available: price, limit, show");
                break;
        }
    }

    private void handleGasPrice(String[] parts) {
        defaultGasPrice = parseLong(parts[2], "gas price");
        System.out.println("Gas price set to: " + defaultGasPrice);
    }

    private void handleGasLimit(String[] parts) {
        defaultGasLimit = parseLong(parts[2], "gas limit");
        System.out.println("Gas limit set to: " + defaultGasLimit);
    }

    private long parseLong(String s, String field) {
        try { 
            return Long.parseLong(s); 
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number, got: " + s);
        }
    }
 
    private BigInteger parseBigInt(String s, String field) {
        try { 
            return new BigInteger(s); 
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number, got: " + s);
        }
    }

    private void requireArgs(String[] parts, int required, String usage) {
        if (parts.length < required)
            throw new IllegalArgumentException("Usage: " + usage);
    }

    private static PrivateKey loadPrivateKey(int clientId) throws Exception {
        return KeyVault.loadPrivateKey("client" + clientId);
    }

    private static Map<Integer, PublicKey> loadAllPublicKeys() throws Exception {
        return KeyVault.loadAllPublicKeys();
    }

    private Transaction buildDepCoinTransfer(String to, long amount) {
        return new Transaction(myAddress, to, null, amount, nonce, defaultGasPrice, defaultGasLimit, publicKey);
    }

    private Transaction buildDepCoinBalanceQuery(String address) {
        return new Transaction(
            myAddress,
            address,
            null,
            0,
            nonce,
            defaultGasPrice,
            defaultGasLimit,
            publicKey
        );
    }

    private Transaction buildContractCall(String callData) {
        return new Transaction(myAddress, istCoinAddress, callData, 0, nonce, defaultGasPrice, defaultGasLimit, publicKey);
    }

    private void submit(Transaction transaction) throws Exception{
        transaction.sign(privateKey);
        Message response = client.send(transaction);
        printExecutionResult(response, transaction);
        nonce++;
    }

    private void printExecutionResult(Message response, Transaction transaction) {
        if (response == null || response.getExecutionResults() == null || response.getExecutionResults().isEmpty()) {
            System.out.println("No execution result was returned by the service.");
            return;
        }

        int txIndex = -1;
        if (response.getNode() != null && response.getNode().getProposedTransactions() != null) {
            txIndex = response.getNode().getProposedTransactions().indexOf(transaction);
        }

        ExecutionResult result;
        if (txIndex >= 0 && txIndex < response.getExecutionResults().size()) {
            result = response.getExecutionResults().get(txIndex);
        } else {
            result = response.getExecutionResults().get(0);
        }

        System.out.println("Execution result:");
        System.out.println("  success: " + result.success);
        System.out.println("  gasUsed: " + result.gasUsed);
        System.out.println("  returnValue: " + result.returnValue);
    }

    private String padAddress(String address) {
        String hex = address.startsWith("0x") ? address.substring(2) : address;
        return "0".repeat(64 - hex.length()) + hex;
    }
 
    private String padUint256(BigInteger value) {
        return String.format("%064x", value);
    }

    private void printHelp() {

        System.out.println("|  NATIVE DEPCOIN                              |");
        System.out.println("|    depcoin transfer <to_address> <amount>    |");
        System.out.println("|    depcoin balance <address>                  |");
        System.out.println("|                                              |");
        System.out.println("|  IST COIN (ERC-20)                           |");
        System.out.println("|    ist total-supply                          |");
        System.out.println("|    ist balance <address>                     |");
        System.out.println("|    ist transfer <to> <amount>                |");
        System.out.println("|    ist approve <spender> <amount>            |");
        System.out.println("|    ist allowance <owner> <spender>           |");
        System.out.println("|    ist increase-allowance <spender> <amount> |");
        System.out.println("|    ist decrease-allowance <spender> <amount> |");
        System.out.println("|    ist transfer-from <from> <to> <amount>    |");
        System.out.println("|                                              |");
        System.out.println("|  GAS                                         |");
        System.out.println("|    gas price <value>                         |");
        System.out.println("|    gas limit <value>                         |");
        System.out.println("|    gas show                                  |");
        System.out.println("|                                              |");
        System.out.println("|  help                                        |");
        System.out.println("|  exit                                        |");
        System.out.println("");
    }

}
