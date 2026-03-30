package depchain.blockchain;

import org.junit.jupiter.api.*;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlockStorageTest {
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
    static BlockStorage storage;
    static EVMExecutorService evm;

    @BeforeAll
    static void setup() throws Exception {
        tempDir = createTempDir();
        copyGenesis(tempDir);
        storage = storageAt(tempDir);
        
        evm = new EVMExecutorService();
        evm.createAccount(ALICE, Wei.of(100_000));
        evm.createAccount(BOB, Wei.of(50_000));
        evm.createAccount(CHARLIE, Wei.of(50_000));
        evm.deployISTCoin(CONTRACT, ALICE, TOTAL_SUPPLY);
    }

    @AfterAll
    static void cleanup() throws Exception {
        deleteDir(tempDir);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testChainDoesNotExist() {
        System.out.println("=== chainExists() before saving ===");
        System.out.println("  exists: " + storage.chainExists());
        System.out.println();

        assertFalse(storage.chainExists());
    }

    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testChainExistsAfterSave() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
        
        storage.saveBlock(block, 0);

        System.out.println("=== chainExists() after saving block 0 ===");
        System.out.println("  exists: " + storage.chainExists());
        System.out.println();

        assertTrue(storage.chainExists());
    }

    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testSaveAndLoadHash() {
        Block original = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
        
        storage.saveBlock(original, 0);

        Block loaded = storage.loadBlock(0);

        System.out.println("=== save/load preserves hash ===");
        System.out.println("  original: " + original.getBlockHash());
        System.out.println("  loaded:   " + loaded.getBlockHash());
        System.out.println();

        assertEquals(original.getBlockHash(), loaded.getBlockHash());
    }

    @Test
    @Order(4)
    @DisplayName("Test 4 — saveBlock and loadBlock preserves previousBlockHash")
    void testSaveAndLoadPreviousHash() {
        Block block0 = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());

        storage.saveBlock(block0, 0);

        Block block1 = new Block(1, block0.getBlockHash(), new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());

        storage.saveBlock(block1, 1);

        Block loaded = storage.loadBlock(1);

        System.out.println("=== save/load preserves previousBlockHash ===");
        System.out.println("  expected: " + block0.getBlockHash());
        System.out.println("  loaded:   " + loaded.getPreviousBlockHash());
        System.out.println();

        assertEquals(block0.getBlockHash(), loaded.getPreviousBlockHash());
    }

    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testLoadRestoresBalances() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
       
        storage.saveBlock(block, 0);

        Block loaded = storage.loadBlock(0);

        BlockAccount alice = loaded.getState().get(ALICE.toHexString());
        BlockAccount bob = loaded.getState().get(BOB.toHexString());
        BlockAccount charlie = loaded.getState().get(CHARLIE.toHexString());

        System.out.println("=== loadBlock restores balances ===");
        System.out.println("  Alice balance: " + alice.balance);
        System.out.println("  Bob balance: " + bob.balance);
        System.out.println("  Charlie balance: " + charlie.balance);
        System.out.println();

        assertEquals("100000", alice.balance);
        assertEquals("50000", bob.balance);
        assertEquals("50000", charlie.balance);
    }

    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testLoadRestoresAllAccounts() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());

        storage.saveBlock(block, 0);

        Block loaded = storage.loadBlock(0);

        System.out.println("=== loadBlock restores all accounts ===");
        System.out.println("  accounts: " + loaded.getState().keySet());
        System.out.println();

        assertTrue(loaded.getState().containsKey(ALICE.toString()));
        assertTrue(loaded.getState().containsKey(BOB.toString()));
        assertTrue(loaded.getState().containsKey(CHARLIE.toString()));
        assertTrue(loaded.getState().containsKey(CONTRACT.toString()));
    }

    @Test
    @Order(7)
    @DisplayName("Test 7")
    void testLatestBlockNumber() {
        for (int i = 2; i <= 4; i++) {
            Block block = new Block(i, "prevhash",
                new ArrayList<>(), evm.getWorldState(), evm.getKnownAddresses());
            
            storage.saveBlock(block, i);
        }

        int latest = storage.getLatestBlockNumber();

        System.out.println("=== getLatestBlockNumber ===");
        System.out.println("  latest: " + latest);
        System.out.println();

        assertEquals(4, latest);
    }

    @Test
    @Order(8)
    @DisplayName("Test 8")
    void testLoadGenesis() {
        Block genesis = storage.loadGenesis();

        System.out.println("=== loadGenesis ===");
        System.out.println("  previousBlockHash: " + genesis.getPreviousBlockHash());
        System.out.println("  accounts:          " + genesis.getState().keySet());
        System.out.println();

        assertNull(genesis.getPreviousBlockHash());
        assertFalse(genesis.getState().isEmpty());
        assertTrue(genesis.getState().containsKey(ALICE.toString()));
        assertTrue(genesis.getState().containsKey(BOB.toString()));
        assertTrue(genesis.getState().containsKey(CHARLIE.toString()));
        assertTrue(genesis.getState().containsKey(CONTRACT.toString()));
    }

    public static Path createTempDir() throws Exception {
        return Files.createTempDirectory("depchain_test_");
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
}