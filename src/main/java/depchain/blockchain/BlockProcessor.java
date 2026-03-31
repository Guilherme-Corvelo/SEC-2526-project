package depchain.blockchain;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import depchain.Debug;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.List;

public class BlockProcessor {

    private static final String DEPLOYER_ADDRESS  = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CONTRACT_ADDRESS  = "1234567891234567891234567891234567891234";
    private static final BigInteger TOTAL_SUPPLY  = BigInteger.valueOf(10_000_000_000L);

    private final EVMExecutorService evm;
    private final BlockStorage storage;

    private int currentBlockNumber;
    private String lastBlockHash;
    private Address istCoinAddress;

    public BlockProcessor(EVMExecutorService evm, BlockStorage storage) {
        this.evm = evm;
        this.storage = storage;
    }

    public void startup() {

        if (storage.chainExists()) {
            loadFromDisk();
        }
        else {
            initGenesis();
        }
    }

    public void initGenesis() {

        Block genesis = storage.loadGenesis();

        for (Map.Entry<String, BlockAccount> entry : genesis.getState().entrySet()) {

            if (entry.getKey().equals(Address.fromHexString(CONTRACT_ADDRESS).toHexString())) {
                continue;
            }

            Address address = Address.fromHexString(entry.getKey());
            Wei balance = Wei.of(new BigInteger(entry.getValue().balance));
            evm.createAccount(address, balance);
        }

        istCoinAddress = Address.fromHexString(CONTRACT_ADDRESS);

        evm.deployISTCoin(istCoinAddress, Address.fromHexString(DEPLOYER_ADDRESS), TOTAL_SUPPLY);

        //TODO: Not sure if deploying contract should increase nonce
        evm.incrementNonce(Address.fromHexString(DEPLOYER_ADDRESS));

        Block genesisBlock = new Block(
            0,
            null,
            new ArrayList<>(),
            evm.getWorldState(),
            evm.getKnownAddresses()
        );
    
        storage.saveBlock(genesisBlock, 0);
        this.currentBlockNumber = 0;
        this.lastBlockHash = genesisBlock.getBlockHash();

        Debug.debug(" Genesis block created - hash: " + lastBlockHash);
    }

    public void loadFromDisk() {
        int latestNumber = storage.getLatestBlockNumber();
        Block latest = storage.loadBlock(latestNumber);

        for (Map.Entry<String, BlockAccount> entry : latest.getState().entrySet()) {

            Address address = Address.fromHexString(entry.getKey());
            BlockAccount accountSnapshot = entry.getValue();

            if (entry.getKey().equals(Address.fromHexString(CONTRACT_ADDRESS).toHexString())) {
                continue;
            }

            evm.createAccount(address, Wei.of(new BigInteger(accountSnapshot.balance)));

            for (long i = 0; i < accountSnapshot.nonce; i++) {
                evm.incrementNonce(address);
            }
        }

        istCoinAddress = Address.fromHexString(CONTRACT_ADDRESS);

        //TODO: HAD TO FIX BLOCKPROCESSOR LOADFROMDISK TO ADD DEPLOYISTCOIN
        // CURRENTLY OUR BLOCK DOES NOT SAVE IST COIN BALANCE
        // ON REBOOT FROM DISK LOADING THAT IS COMPLETELY LOST EVERYONE STARTS WITH 0 AGAIN?, ALICE HAS TOTAL SUPPLY?
        evm.deployISTCoin(istCoinAddress, Address.fromHexString(DEPLOYER_ADDRESS), TOTAL_SUPPLY);

        for (Map.Entry<String, BlockAccount> entry : latest.getState().entrySet()) {
            if (entry.getKey().equals(Address.fromHexString(CONTRACT_ADDRESS).toHexString())) {
                continue;
            }

            BlockAccount accountSnapshot = entry.getValue();
            if (accountSnapshot.istBalance == null) {
                continue;
            }

            Address account = Address.fromHexString(entry.getKey());
            evm.setISTBalance(istCoinAddress, account, new BigInteger(accountSnapshot.istBalance));

            if (accountSnapshot.allowances != null) {
                for (Map.Entry<String, String> allowanceEntry : accountSnapshot.allowances.entrySet()) {
                    Address spender = Address.fromHexString(allowanceEntry.getKey());
                    BigInteger allowance = new BigInteger(allowanceEntry.getValue());
                    evm.setISTAllowance(istCoinAddress, account, spender, allowance);
                }
            }
        }

        this.currentBlockNumber = latestNumber;
        this.lastBlockHash = latest.getBlockHash();

        Debug.debug(" Chain loaded - latest block: " +  latestNumber);
    }

    public Block processBlock(List<Transaction> decidedTransactions) {

        List<Transaction> ordered = new ArrayList<>(decidedTransactions);
        ordered.sort(Comparator.comparingLong(Transaction::getGasPrice).reversed());

        List<Transaction> executed = new ArrayList<>();

        for (Transaction transaction : ordered) {

            if (!isValid(transaction)) {
                System.out.println("Dropping invalid transaction from: " + transaction.getFrom());
                continue;
            }

            Address sender = Address.fromHexString(transaction.getFrom());
            long maxFee = transaction.getGasPrice() * transaction.getGasLimit();

            if (evm.getBalance(sender).toLong() < maxFee) {
                System.out.println("Dropping transaction - insufficient gas funds: " + transaction.getFrom());
                continue;
            }

            ExecutionResult result = execute(transaction, sender);

            evm.deductGasFee(sender, transaction.getGasPrice(), transaction.getGasLimit(), result.gasUsed);
            evm.incrementNonce(sender);

            if (!result.success) {
                System.out.println("Transaction reverted (gas used): " + transaction.getFrom() + " nonce: " + transaction.getNonce());
            }

            executed.add(transaction);
        }

        int newBlockNumber = currentBlockNumber + 1;
        
        Block newBlock = new Block(
            newBlockNumber,
            lastBlockHash,
            executed,
            evm.getWorldState(),
            evm.getKnownAddresses()
        );

        storage.saveBlock(newBlock, newBlockNumber);
        this.currentBlockNumber = newBlockNumber;
        this.lastBlockHash = newBlock.getBlockHash();

        Debug.debug(" Block " + newBlockNumber + " - " + executed.size() + " transactions.");

        return newBlock;
    }

    private ExecutionResult execute(Transaction transaction, Address sender) {

        if (transaction.isContractCall()) {
            return evm.callContract(sender, Address.fromHexString(CONTRACT_ADDRESS), Bytes.fromHexString(transaction.getInput()));
        } else if (transaction.isDepCoinTransfer()) {
            return evm.transferDepCoin(sender, Address.fromHexString(transaction.getTo()), Wei.of(transaction.getValue()));
        }

        return new ExecutionResult(false, 21000, BigInteger.ZERO);
    }


    private boolean isValid(Transaction transaction) {

        if (!transaction.verifySignature()) {
            return false;
        }

        Address sender = Address.fromHexString(transaction.getFrom());

        if (transaction.getNonce() != evm.getNonce(sender)) {
            return false;
        }

        if (transaction.getGasPrice() <= 0 || transaction.getGasLimit() <= 0) {
            return false;
        }

        return true;
    }

    public int getCurrentBlockNumber() {
        return currentBlockNumber;
    }

    public String getLastBlockHash() {
        return lastBlockHash;
    }

    public Address getISTCoinAddress() {
        return istCoinAddress;
    }

}
