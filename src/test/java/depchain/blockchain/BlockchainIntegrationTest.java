package depchain.blockchain;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.*;

import depchain.crypto.KeyVault;

import org.hyperledger.besu.datatypes.Address;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlockchainIntegrationTest {

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


    static KeyPair aliceKeys;
    static KeyPair bobKeys;

    @BeforeAll
    static void loadKeys() throws Exception {
        aliceKeys = loadAliceKeys();
        bobKeys   = loadBobKeys();
    }

    Path tempDir;
    EVMExecutorService evm;
    BlockStorage storage;
    BlockProcessor processor;

    @BeforeEach
    void setup() throws Exception {
        tempDir   = createTempDir();
        copyGenesis(tempDir);
        evm       = new EVMExecutorService();
        storage   = storageAt(tempDir);
        processor = new BlockProcessor(evm, storage);
        processor.startup();
    }

    @AfterEach
    void cleanup() throws Exception {
        deleteDir(tempDir);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testThreeBlockChain() throws Exception {
        // Alice transfers 1000 IST to Bob
        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(1000)), 1, aliceKeys)
        ));

        // Alice transfers 2000 IST to Bob
        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(2000)), 2, aliceKeys)
        ));

        // Alice transfers 500 DepCoin to Charlie
        processor.processBlock(List.of(
            signedDepCoinTransfer(ALICE.toHexString(), CHARLIE.toHexString(), 500, 3, aliceKeys)
        ));

        ExecutionResult bobIST = evm.callContract(
            ALICE, CONTRACT, Bytes.fromHexString(ID_BALANCE_OF + pad(BOB))
        );

        System.out.println("=== genesis -> 3 blocks ===");
        System.out.println("  Bob IST balance:  " + bobIST.returnValue);
        System.out.println("  Current block:    " + processor.getCurrentBlockNumber());
        System.out.println();

        assertEquals(BigInteger.valueOf(3000), bobIST.returnValue);
        assertEquals(3, processor.getCurrentBlockNumber());
    }

    
    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testBlockHashChaining() throws Exception {
        
        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_BALANCE_OF + pad(BOB), 1, aliceKeys)
        ));

        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_BALANCE_OF + pad(BOB), 2, aliceKeys)
        ));

        Block block1 = storage.loadBlock(1);
        Block block2 = storage.loadBlock(2);

        System.out.println("=== block hash chaining ===");
        System.out.println("  block1 hash:              " + block1.getBlockHash());
        System.out.println("  block2 previousBlockHash: " + block2.getPreviousBlockHash());
        System.out.println("  Match: " + block1.getBlockHash().equals(block2.getPreviousBlockHash()));
        System.out.println();

        assertEquals(block1.getBlockHash(), block2.getPreviousBlockHash());
    }

    
    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testStartupCallsLoadFromDisk() throws Exception {

        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_BALANCE_OF + pad(BOB), 1, aliceKeys)
        ));
        
        int blockBeforeRestart = processor.getCurrentBlockNumber();

        EVMExecutorService freshEvm = new EVMExecutorService();
        BlockProcessor freshProcessor = new BlockProcessor(freshEvm, storage);
        freshProcessor.startup();

        System.out.println("=== startup() after chain exists ===");
        System.out.println("  block before restart: " + blockBeforeRestart);
        System.out.println("  block after restart:  " + freshProcessor.getCurrentBlockNumber());
        System.out.println();

        assertEquals(blockBeforeRestart, freshProcessor.getCurrentBlockNumber());
    }

    
    @Test
    @Order(4)
    @DisplayName("Test 4")
    void testContinueAfterRestart() throws Exception {

        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(1000)), 1, aliceKeys)
        ));

        EVMExecutorService freshEvm = new EVMExecutorService();
        BlockProcessor freshProcessor = new BlockProcessor(freshEvm, storage);
        freshProcessor.startup();

        Block block2 = freshProcessor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(500)), 2, aliceKeys)
        ));

        System.out.println("=== continue after restart ===");
        System.out.println("  block2 number:       " + freshProcessor.getCurrentBlockNumber());
        System.out.println("  block2 transactions: " + block2.getTransactions().size());
        System.out.println();

        assertEquals(2, freshProcessor.getCurrentBlockNumber());
        assertEquals(1, block2.getTransactions().size());
    }


    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testFrontrunningProtection() throws Exception {
        // Alice approves Bob for 500 IST
        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_APPROVE + pad(BOB) + pad(BigInteger.valueOf(500)), 1, aliceKeys)
        ));

        // Alice tries to change directly to 999 (must revert)
        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_APPROVE + pad(BOB) + pad(BigInteger.valueOf(999)), 2, aliceKeys)
        ));

        ExecutionResult allowance = evm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_ALLOWANCE + pad(ALICE) + pad(BOB))
        );

        System.out.println("=== frontrunning protection ===");
        System.out.println("  allowance(Alice, Bob): " + allowance.returnValue);
        System.out.println();

        assertEquals(BigInteger.valueOf(500), allowance.returnValue);
    }

    
    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testISTCoinStateSurvivesRestart() throws Exception {

        processor.processBlock(List.of(
            signedContractCall(ALICE.toHexString(),
                ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(1000)), 1, aliceKeys)
        ));

        EVMExecutorService freshEvm = new EVMExecutorService();
        BlockProcessor freshProcessor = new BlockProcessor(freshEvm, storage);
        freshProcessor.startup();

        ExecutionResult bobIST = freshEvm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_BALANCE_OF + pad(BOB))
        );

        System.out.println("=== IST state survives restart ===");
        System.out.println("  Bob IST after restart: " + bobIST.returnValue);
        System.out.println();

        assertEquals(BigInteger.valueOf(1000), bobIST.returnValue);
    }

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
