package depchain.client;

import java.util.Scanner;
import java.math.BigInteger;

public class ClientCLI {

    static final String ID_TOTAL_SUPPLY = "18160ddd";
    static final String ID_BALANCE_OF = "70a08231";
    static final String ID_TRANSFER = "a9059cbb";
    static final String ID_APPROVE = "095ea7b3";
    static final String ID_ALLOWANCE = "dd62ed3e";
    static final String ID_TRANSFER_FROM = "23b872dd";
    static final String ID_INCREASE_ALLOWANCE = "39509351";
    static final String ID_DECREASE_ALLOWANCE = "a457c2d7";

    //TODO: HARDCORE CONFIG
    //TODO: Create Transaction builders
    //TODO: Think about key vault, server_addresses and client_addresses

    //private final String myAddress;
    private long defaultGasPrice = 1;
    private long defaultGasLimit = 100_000;

    //TODO: Create Constructor correctly
    public ClientCLI() {

    }

    public static void main(String[] args) {

        if (args.length < 2) {
            System.err.println("Usage ClientCLI <clientId> <clientPort>");
            System.err.println("Example: ClientCLI 1 20000");
        }

        int clientId   = Integer.parseInt(args[0]);
        int clientPort = Integer.parseInt(args[1]);

        System.out.println("Starting DepChain client...");
        System.out.println("  Client ID:   " + clientId);
        System.out.println("  Client Port: " + clientPort);

        //TODO: Get private key and all publics

        //TODO: Get my own address?

        //TODO: Create a new Client

        //TODO: Create Constructor correctly
        ClientCLI cli = new ClientCLI();

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
                        return;               
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
        
            default:
                System.out.println("Unknown depcoin command. Available: transfer");
                break;
        }

    }

    private void handleDepCoinTransfer(String[] parts) {
        String to = parts[2];
        long amount = parseLong(parts[3], "amount");

        // TODO: Create Transaction

        System.out.println("DepCoin transfer submitted.");
        System.out.println("  To:     " + to);
        System.out.println("  Amount: " + amount);
        //TODO System.out.println("  From:   " + myAddress);
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

    private void handleISTTransfer(String[] parts) {
        String to = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        //TODO: Contract
        //submit(buildContractCall(ID_TRANSFER + padAddress(to) + padUint256(amount)));
        System.out.println("IST transfer submitted -> to: " + to + "  amount: " + amount);
    }

    private void handleISTApprove(String[] parts) {
        String spender    = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        //TODO: Contract
        //submit(buildContractCall(ID_APPROVE + padAddress(spender) + padUint256(amount)));
        System.out.println("IST approve submitted -> spender: " + spender + "  amount: " + amount);
        if (amount.compareTo(BigInteger.ZERO) != 0) {
            System.out.println("  Warning: only safe if current allowance is 0.");
            System.out.println("  To change an existing allowance use increase/decrease-allowance.");
        }
    }

    private void handleISTIncreaseAllowance(String[] parts) {
        String spender = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        //TODO: Contract
        //submit(buildContractCall(ID_INCREASE_ALLOWANCE + padAddress(spender) + padUint256(amount)));
        System.out.println("IST increaseAllowance submitted -> spender: " + spender + "  added: " + amount);
    }

    private void handleISTDecreaseAllowance(String[] parts) {
        String spender = parts[2];
        BigInteger amount = parseBigInt(parts[3], "amount");

        //TODO: Contract
        //submit(buildContractCall(ID_DECREASE_ALLOWANCE + padAddress(spender) + padUint256(amount)));
        System.out.println("IST decreaseAllowance submitted -> spender: " + spender + "  removed: " + amount);
    }

    private void handleISTTransferFrom(String[] parts) {
        String from = parts[2];
        String to = parts[3];
        BigInteger amount = parseBigInt(parts[4], "amount");

        //TODO: Contract
        //submit(buildContractCall(ID_TRANSFER_FROM + padAddress(from) + padAddress(to) + padUint256(amount)));
        System.out.println("IST transferFrom submitted -> from: " + from + "  to: " + to + "  amount: " + amount);
    }

    private void handleISTBalance(String[] parts) {
        //TODO: Contract
        //submit(buildContractCall(ID_BALANCE_OF + padAddress(parts[2])));
        System.out.println("IST balanceOf query submitted for: " + parts[2]);
    }

    private void handleISTAllowance(String[] parts) {
        //TODO: Contract
        //submit(buildContractCall(ID_ALLOWANCE + padAddress(parts[2]) + padAddress(parts[3])));
        System.out.println("IST allowance query submitted -> owner: " + parts[2] + "  spender: " + parts[3]);
    }

    private void handleISTTotalSupply() {
        //TODO: Contract
        //submit(buildContractCall(ID_TOTAL_SUPPLY));
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
