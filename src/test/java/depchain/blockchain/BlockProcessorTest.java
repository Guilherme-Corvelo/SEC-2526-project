package depchain.blockchain;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.*;

import depchain.crypto.KeyVault;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlockProcessorTest {

    public static final Address ALICE    = Address.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    public static final Address BOB      = Address.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    public static final Address CHARLIE  = Address.fromHexString("cccccccccccccccccccccccccccccccccccccccc");
    public static final Address CONTRACT = Address.fromHexString("1234567891234567891234567891234567891234");

    public static final BigInteger TOTAL_SUPPLY = BigInteger.valueOf(10_000_000_000L);

    static final String ID_TOTAL_SUPPLY = "18160ddd";
    static final String ID_BALANCE_OF = "70a08231";
    static final String ID_TRANSFER = "a9059cbb";
    static final String ID_APPROVE = "095ea7b3";
    static final String ID_ALLOWANCE = "dd62ed3e";
    static final String ID_TRANSFER_FROM = "23b872dd";
    static final String ID_INCREASE_ALLOWANCE = "39509351";
    static final String ID_DECREASE_ALLOWANCE = "a457c2d7";

    static Path tempDir;
    static EVMExecutorService evm;
    static BlockStorage storage;
    static BlockProcessor processor;
    
    static KeyPair aliceKeys;
    static KeyPair bobKeys;

    @BeforeAll
    static void setup() throws Exception {
        aliceKeys = loadAliceKeys();
        bobKeys = loadBobKeys();

        tempDir = createTempDir();
        copyGenesis(tempDir);
        
        evm = new EVMExecutorService();
        
        storage = storageAt(tempDir);
        
        processor = new BlockProcessor(evm, storage);
        processor.initGenesis();
    }

    @AfterAll
    static void cleanup() throws Exception {
        deleteDir(tempDir);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testInitGenesisCreatesBlock() {
        System.out.println("=== initGenesis creates block_0 ===");
        System.out.println("  chainExists: " + storage.chainExists());
        System.out.println();

        assertTrue(storage.chainExists());
    }

    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testInitGenesisTotalSupply() {
        ExecutionResult result = evm.callContract(
            ALICE, CONTRACT, Bytes.fromHexString(ID_TOTAL_SUPPLY)
        );

        System.out.println("=== initGenesis totalSupply ===");
        System.out.println("  totalSupply: " + result.returnValue);
        System.out.println();

        assertEquals(TOTAL_SUPPLY, result.returnValue);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testInitGenesisBalances() {

        System.out.println("=== initGenesis DepCoin balances ===");
        System.out.println("  Alice:   " + evm.getBalance(ALICE));
        System.out.println("  Bob:     " + evm.getBalance(BOB));
        System.out.println("  Charlie: " + evm.getBalance(CHARLIE));
        System.out.println();

        assertEquals(Wei.of(100_000), evm.getBalance(ALICE));
        assertEquals(Wei.of(50_000),  evm.getBalance(BOB));
        assertEquals(Wei.of(50_000),  evm.getBalance(CHARLIE));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4")
    void testInitGenesisDeployerNonce() {
        System.out.println("=== initGenesis deployer nonce ===");
        System.out.println("  Alice nonce: " + evm.getNonce(ALICE));
        System.out.println();

        assertEquals(1, evm.getNonce(ALICE));
    }

    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testInitGenesisBlockNumber() {
        System.out.println("=== initGenesis block number ===");
        System.out.println("  currentBlockNumber: " + processor.getCurrentBlockNumber());
        System.out.println();

        assertEquals(0, processor.getCurrentBlockNumber());
    }

    
    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testProcessBlockISTTransfer() throws Exception {
        Transaction transaction = signedContractCall(
            ALICE.toHexString(),
            ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(1000)),
            1, aliceKeys
        );
        
        processor.processBlock(List.of(transaction));
        
        ExecutionResult bobBalance = evm.callContract(
            ALICE, CONTRACT, Bytes.fromHexString(ID_BALANCE_OF + pad(BOB))
        );

        System.out.println("=== IST transfer ===");
        System.out.println("  Bob IST balance: " + bobBalance.returnValue);
        System.out.println();

        assertEquals(BigInteger.valueOf(1000), bobBalance.returnValue);
    
       assertTrue(true);
    }


    @Test
    @Order(7)
    @DisplayName("Test 7")
    void testProcessBlockDepCoinTransfer() throws Exception {
        Wei bobBefore = evm.getBalance(BOB);

        Transaction transaction = signedDepCoinTransfer(
            ALICE.toHexString(), BOB.toHexString(), 500, 2, aliceKeys
        );

        processor.processBlock(List.of(transaction));

        Wei bobAfter = evm.getBalance(BOB);

        System.out.println("=== DepCoin transfer ===");
        System.out.println("  Bob before: " + bobBefore);
        System.out.println("  Bob after:  " + bobAfter);
        System.out.println();

        assertTrue(bobAfter.greaterThan(bobBefore));
        assertEquals(bobAfter, bobBefore.add(Wei.of(500)));
    }

    
    @Test
    @Order(8)
    @DisplayName("Test 8")
    void testProcessBlockIncrementsNonce() throws Exception {
        long nonceBefore = evm.getNonce(ALICE);

        Transaction transaction = signedContractCall(
            ALICE.toHexString(), ID_BALANCE_OF + pad(BOB), nonceBefore, aliceKeys
        );
        processor.processBlock(List.of(transaction));

        System.out.println("=== nonce increments ===");
        System.out.println("  nonce before: " + nonceBefore);
        System.out.println("  nonce after:  " + evm.getNonce(ALICE));
        System.out.println();

        assertEquals(nonceBefore + 1, evm.getNonce(ALICE));
    }

    
    @Test
    @Order(9)
    @DisplayName("Test 9")
    void testProcessBlockDeductsGas() throws Exception {
        Wei aliceBefore = evm.getBalance(ALICE);
        long nonce      = evm.getNonce(ALICE);

        Transaction transaction = signedContractCall(
            ALICE.toHexString(), ID_BALANCE_OF + pad(BOB), nonce, aliceKeys
        );
        processor.processBlock(List.of(transaction));

        System.out.println("=== gas deducted ===");
        System.out.println("  Alice before: " + aliceBefore);
        System.out.println("  Alice after:  " + evm.getBalance(ALICE));
        System.out.println();

        assertTrue(evm.getBalance(ALICE).lessThan(aliceBefore));
    }

    @Test
    @Order(10)
    @DisplayName("Test 10")
    void testProcessBlockDropsWrongNonce() throws Exception {
        long currentNonce = evm.getNonce(ALICE);

        Transaction transaction = signedContractCall(
            ALICE.toHexString(), ID_BALANCE_OF + pad(BOB),
            currentNonce + 99, aliceKeys
        );
        Block block = processor.processBlock(List.of(transaction));

        System.out.println("=== wrong nonce dropped ===");
        System.out.println("  transactions in block: " + block.getTransactions().size());
        System.out.println();

        assertEquals(0, block.getTransactions().size());
    }

    @Test
    @Order(11)
    @DisplayName("Test 11")
    void testProcessBlockDropsInvalidSignature() throws Exception {
        long nonce = evm.getNonce(ALICE);

        Transaction transaction = new Transaction(
            ALICE.toHexString(), CONTRACT.toHexString(),
            ID_BALANCE_OF + pad(BOB), 0,
            nonce, 1, 1000, aliceKeys.getPublic()
        );
        transaction.sign(bobKeys.getPrivate());

        Block block = processor.processBlock(List.of(transaction));

        System.out.println("=== invalid signature dropped ===");
        System.out.println("  transactions in block: " + block.getTransactions().size());
        System.out.println();

        assertEquals(0, block.getTransactions().size());
    }

    
    @Test
    @Order(12)
    @DisplayName("Test 12")
    void testProcessBlockSortsByGasPrice() throws Exception {
        long nonce = evm.getNonce(ALICE);

        Transaction lowGas = new Transaction(
            ALICE.toHexString(), CONTRACT.toHexString(),
            ID_BALANCE_OF + pad(BOB), 0,
            nonce, 1, 1000, aliceKeys.getPublic()
        );
        lowGas.sign(aliceKeys.getPrivate());

        Transaction highGas = new Transaction(
            ALICE.toHexString(), CONTRACT.toHexString(),
            ID_BALANCE_OF + pad(BOB), 0,
            nonce, 10, 1000, aliceKeys.getPublic()
        );
        highGas.sign(aliceKeys.getPrivate());

        Block block = processor.processBlock(List.of(lowGas, highGas));

        System.out.println("=== gas price ordering ===");
        System.out.println("  executed:          " + block.getTransactions().size());
        System.out.println("  first transaction gasPrice: " + block.getTransactions().get(0).gasPrice);
        System.out.println();

        assertEquals(1, block.getTransactions().size());
        assertEquals(10, block.getTransactions().get(0).gasPrice);
    }

    @Test
    @Order(13)
    @DisplayName("Test 13")
    void testProcessBlockIncrementsBlockNumber() throws Exception {
        int before = processor.getCurrentBlockNumber();
        long nonce = evm.getNonce(ALICE);

        Transaction transaction = signedContractCall(
            ALICE.toHexString(), ID_BALANCE_OF + pad(BOB), nonce, aliceKeys
        );
        processor.processBlock(List.of(transaction));

        System.out.println("=== block number increments ===");
        System.out.println("  before: " + before);
        System.out.println("  after:  " + processor.getCurrentBlockNumber());
        System.out.println();

        assertEquals(before + 1, processor.getCurrentBlockNumber());
    }



    @Test
    @Order(14)
    @DisplayName("Test 14")
    void testLoadFromDiskRestoresBalances() throws Exception {
        int latestBlock = processor.getCurrentBlockNumber();

        EVMExecutorService freshEvm = new EVMExecutorService();
        BlockProcessor freshProcessor = new BlockProcessor(freshEvm, storage);
        freshProcessor.loadFromDisk();

        System.out.println("=== loadFromDisk restores balances ===");
        System.out.println("  Bob balance after restart: " + freshEvm.getBalance(BOB));
        System.out.println("  Block number:              " + freshProcessor.getCurrentBlockNumber());
        System.out.println("  Expected block number:     " + latestBlock);
        System.out.println();

        assertEquals(latestBlock, freshProcessor.getCurrentBlockNumber());
        assertNotNull(freshEvm.getBalance(BOB));
    }
    

    @Test
    @Order(15)
    @DisplayName("Test 15")
    void testLoadFromDiskRestoresBlockNumber() {
        EVMExecutorService freshEvm = new EVMExecutorService();
        BlockProcessor freshProcessor = new BlockProcessor(freshEvm, storage);
        freshProcessor.loadFromDisk();

        System.out.println("=== loadFromDisk restores block number and hash ===");
        System.out.println("  block number: " + freshProcessor.getCurrentBlockNumber());
        System.out.println("  last hash:    " + freshProcessor.getLastBlockHash());
        System.out.println();

        assertEquals(processor.getCurrentBlockNumber(), freshProcessor.getCurrentBlockNumber());
        assertEquals(processor.getLastBlockHash(),      freshProcessor.getLastBlockHash());
    }

    // Helpers

    public static Path createTempDir() throws Exception {
        return Files.createDirectories(Paths.get("depchain_test_"));
    }
 
    public static void deleteDir(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
            
        Files.walk(dir)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    public static void copyGenesis(Path dir) throws Exception {
        Files.copy(Paths.get("genesis.json"), dir.resolve("genesis.json"));
    }

    public static BlockStorage storageAt(Path dir) {
        return new BlockStorage(
            dir.toString() + "/blocks/",
            dir.toString() + "/genesis.json"
        );
    }

    public static KeyPair loadAliceKeys() throws Exception {
        if (!KeyVault.keysExist()) {
            KeyVault.generateAndSave();
        }

        return new KeyPair(
            KeyVault.loadPublicKey("client0"),
            KeyVault.loadPrivateKey("client0")
        );
    }
 
    public static KeyPair loadBobKeys() throws Exception {
        if (!KeyVault.keysExist()) {
            KeyVault.generateAndSave();
        }

        return new KeyPair(
            KeyVault.loadPublicKey("client1"),
            KeyVault.loadPrivateKey("client1")
        );
    }

    public static Transaction signedContractCall(String from, String calldata,
                                                  long nonce, KeyPair keys) throws Exception {
        Transaction transaction = new Transaction(
            from, CONTRACT.toHexString(), calldata, 0,
            nonce, 1,100, keys.getPublic()
        );

        transaction.sign(keys.getPrivate());
        return transaction;
    }
 
    public static Transaction signedDepCoinTransfer(String from, String to,
                                                     long amount, long nonce,
                                                     KeyPair keys) throws Exception {
        Transaction transaction = new Transaction(
            from, to, null, amount,
            nonce, 1, 50_000, keys.getPublic()
        );

        transaction.sign(keys.getPrivate());
        return transaction;
    }

    public static String pad(Address address) {
        String hex = address.toHexString();
        if (hex.startsWith("0x")) hex = hex.substring(2);
        return "0".repeat(64 - hex.length()) + hex;
    }
 
    public static String pad(BigInteger value) {
        return String.format("%064x", value);
    }
} 
