package depchain.blockchain;
 
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.*;
 
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
 
import static org.junit.jupiter.api.Assertions.*;
 
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlockTest {
 
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

    static EVMExecutorService evm;
 
    @BeforeAll
    static void setup() {
        evm = new EVMExecutorService();
        evm.createAccount(ALICE,   Wei.of(100_000));
        evm.createAccount(BOB,     Wei.of(50_000));
        evm.createAccount(CHARLIE, Wei.of(50_000));
        evm.deployISTCoin(CONTRACT, ALICE, TOTAL_SUPPLY);
    }
 
    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testDeterministicHash() {
        Block block1 = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
        
        Block block2 = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        System.out.println("=== same content -> same hash ===");
        System.out.println("  hash1: " + block1.getBlockHash());
        System.out.println("  hash2: " + block2.getBlockHash());
        System.out.println();
 
        assertEquals(block1.getBlockHash(), block2.getBlockHash());
    }
 
    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testDifferentContentDifferentHash() {
        Block block0 = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        evm.transferDepCoin(ALICE, BOB, Wei.of(1000));
 
        Block block1 = new Block(1, block0.getBlockHash(), new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        System.out.println("=== different state -> different hash ===");
        System.out.println("  block0 hash: " + block0.getBlockHash());
        System.out.println("  block1 hash: " + block1.getBlockHash());
        System.out.println();
 
        assertNotEquals(block0.getBlockHash(), block1.getBlockHash());
    }
 
    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testStateSnapshotContainsAllAccounts() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        Map<String, BlockAccount> state = block.getState();
 
        System.out.println("=== state snapshot accounts ===");
        state.forEach((addr, acc) ->
            System.out.println("  " + addr + " → balance: " + acc.balance + " nonce: " + acc.nonce));
        System.out.println();
 
        assertTrue(state.containsKey(ALICE.toHexString()));
        assertTrue(state.containsKey(BOB.toHexString()));
        assertTrue(state.containsKey(CHARLIE.toHexString()));
        assertTrue(state.containsKey(CONTRACT.toHexString()));
    }
 
    @Test
    @Order(4)
    @DisplayName("Test 4")
    void testStateSnapshotBalances() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        BlockAccount alice = block.getState().get(ALICE.toHexString());
        BlockAccount bob = block.getState().get(BOB.toHexString());
        BlockAccount charlie = block.getState().get(CHARLIE.toHexString());
 
        System.out.println("=== state snapshot balances ===");
        System.out.println("  Alice balance: " + alice.balance);
        System.out.println("  Bob balance: " + bob.balance);
        System.out.println("  Charlie balance: " + charlie.balance);
        System.out.println();
 
        assertNotNull(alice.balance);
        assertNotNull(bob.balance);
        assertNotNull(charlie.balance);
        assertTrue(Long.parseLong(alice.balance) == 99_000);
        assertTrue(Long.parseLong(bob.balance) == 51_000);
        assertTrue(Long.parseLong(charlie.balance) == 50_000);
    }
 
    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testContractHasCode() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        BlockAccount contract = block.getState().get(CONTRACT.toHexString());
 
        System.out.println("=== contract has code ===");
        System.out.println("  code is null: " + (contract.code == null));
        System.out.println();
 
        assertNotNull(contract.code);
        assertFalse(contract.code.isEmpty());
    }
 
    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testEOAHasNullCode() {
        Block block = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        BlockAccount alice = block.getState().get(ALICE.toHexString());
        BlockAccount bob = block.getState().get(BOB.toHexString());
        BlockAccount charlie = block.getState().get(CHARLIE.toHexString());
 
        System.out.println("=== EOA has null code ===");
        System.out.println("  Alice code: " + alice.code);
        System.out.println("  Bob code: " + bob.code);
        System.out.println("  Charlie code: " + charlie.code);
        System.out.println();
 
        assertNull(alice.code);
        assertNull(bob.code);
        assertNull(charlie.code);
    }
 
    @Test
    @Order(7)
    @DisplayName("Test 7")
    void testPreviousBlockHash() {
        Block block0 = new Block(0, null, new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        Block block1 = new Block(1, block0.getBlockHash(), new ArrayList<>(),
            evm.getWorldState(), evm.getKnownAddresses());
 
        System.out.println("=== previousBlockHash preserved ===");
        System.out.println("  block0 hash:              " + block0.getBlockHash());
        System.out.println("  block1 previousBlockHash: " + block1.getPreviousBlockHash());
        System.out.println();
 
        assertEquals(block0.getBlockHash(), block1.getPreviousBlockHash());
    }
}