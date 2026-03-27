package depchain.blockchain;

import depchain.crypto.KeyVault;
import org.junit.jupiter.api.*;
 
import java.math.BigInteger;
import java.security.*;
 
import static org.junit.jupiter.api.Assertions.*;

public class TransactionTest {
    
    static final String ID_TOTAL_SUPPLY = "18160ddd";
    static final String ID_BALANCE_OF = "70a08231";
    static final String ID_TRANSFER = "a9059cbb";
    static final String ID_APPROVE = "095ea7b3";
    static final String ID_ALLOWANCE = "dd62ed3e";
    static final String ID_TRANSFER_FROM = "23b872dd";
    static final String ID_INCREASE_ALLOWANCE = "39509351";
    static final String ID_DECREASE_ALLOWANCE = "a457c2d7";

    private static final String ALICE    = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BOB      = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String CONTRACT = "1234567891234567891234567891234567891234";

    private static KeyPair aliceKeys;
    private static KeyPair bobKeys;

    @BeforeAll
    static void loadKeys() throws Exception {
        if (!KeyVault.keysExist()) {
            KeyVault.generateAndSave();
        }

        aliceKeys = new KeyPair(
            KeyVault.loadPublicKey("client0"),
            KeyVault.loadPrivateKey("client0")
        );

        bobKeys = new KeyPair(
            KeyVault.loadPublicKey("client1"),
            KeyVault.loadPrivateKey("client1")
        );
    }

    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testSignAndVerify() throws Exception {
        Transaction transaction = signedContractCall(ID_TOTAL_SUPPLY, 0);
 
        System.out.println("=== sign and verify ===");
        System.out.println("  verifySignature(): " + transaction.verifySignature());
        System.out.println();
 
        assertTrue(transaction.verifySignature(), "A correctly signed transaction should verify");
    }
 
    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testUnsignedTransactionFails() {
        Transaction transaction = new Transaction(
            ALICE, CONTRACT, ID_TOTAL_SUPPLY, 0,
            0, 1, 100_000, aliceKeys.getPublic()
        );
 
        System.out.println("=== unsigned transaction ===");
        System.out.println("  verifySignature(): " + transaction.verifySignature());
        System.out.println();
 
        assertFalse(transaction.verifySignature(), "An unsigned transaction must not verify");
    }
 
    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testWrongKeyFails() throws Exception {
        Transaction transaction = new Transaction(
            ALICE, CONTRACT, ID_TOTAL_SUPPLY, 0,
            0, 1, 100_000, aliceKeys.getPublic()
        );
        transaction.sign(bobKeys.getPrivate());
 
        System.out.println("=== wrong key ===");
        System.out.println("  verifySignature(): " + transaction.verifySignature());
        System.out.println();
 
        assertFalse(transaction.verifySignature(), "A transaction signed with the wrong key must not verify");
    }
 
    @Test
    @Order(4)
    @DisplayName("Test 4")
    void testTamperFrom() throws Exception {
        Transaction original = signedContractCall(ID_TOTAL_SUPPLY, 0);
        Transaction tampered = new Transaction(
            BOB,
            original.getTo(),
            original.getInput(),
            original.getValue(),
            original.getNonce(),
            original.getGasPrice(),
            original.getGasLimit(),
            aliceKeys.getPublic()
        );
        tampered.setSignature(original.getSignature());
 
        System.out.println("=== tamper 'from' ===");
        System.out.println("  verifySignature(): " + tampered.verifySignature());
        System.out.println();
 
        assertFalse(tampered.verifySignature(), "Tampering with 'from' must invalidate the signature");
    }
 

    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testDifferentNoncesProduceDifferentSignatures() throws Exception {
        Transaction transaction1 = signedContractCall(ID_TOTAL_SUPPLY, 0);
        Transaction transaction2 = signedContractCall(ID_TOTAL_SUPPLY, 1);
 
        System.out.println("=== nonce uniqueness ===");
        System.out.println("  transaction1 nonce: 0,  transaction2 nonce: 1");
        System.out.println("  Signatures differ: " + !java.util.Arrays.equals(transaction1.getSignature(), transaction2.getSignature()));
        System.out.println();
 
        assertFalse(
            java.util.Arrays.equals(transaction1.getSignature(), transaction2.getSignature()),
            "Different nonces must produce different signatures"
        );
    }
 
    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testCalldataTotalSupply() throws Exception {
        Transaction transaction = signedContractCall(ID_TOTAL_SUPPLY, 0);
 
        System.out.println("=== totalSupply() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + ID_TOTAL_SUPPLY);
        System.out.println();
 
        assertEquals(ID_TOTAL_SUPPLY, transaction.getInput());
    }
 
    @Test
    @Order(7)
    @DisplayName("Test 7")
    void testCalldataBalanceOf() throws Exception {
        String calldata = ID_BALANCE_OF + padAddress(BOB);
        Transaction transaction  = signedContractCall(calldata, 0);
 
        System.out.println("=== balanceOf() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
        assertEquals(4 + 32, transaction.getInput().length() / 2,
            "balanceOf calldata must be exactly 36 bytes");
    }
 
    @Test
    @Order(8)
    @DisplayName("Test 8")
    void testCalldataTransfer() throws Exception {
        BigInteger amount = BigInteger.valueOf(1000);
        String calldata = ID_TRANSFER + padAddress(BOB) + padUint256(amount);
        Transaction transaction    = signedContractCall(calldata, 0);
 
        System.out.println("=== transfer() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
        assertEquals(4 + 32 + 32, transaction.getInput().length() / 2,
            "transfer calldata must be exactly 68 bytes");
    }
 
    @Test
    @Order(9)
    @DisplayName("Test 9")
    void testCalldataApprove() throws Exception {
        BigInteger amount = BigInteger.valueOf(500);
        String calldata   = ID_APPROVE + padAddress(BOB) + padUint256(amount);
        Transaction transaction    = signedContractCall(calldata, 0);
 
        System.out.println("=== approve() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
    }
 
    @Test
    @Order(10)
    @DisplayName("Test 10")
    void testCalldataAllowance() throws Exception {
        String calldata = ID_ALLOWANCE + padAddress(ALICE) + padAddress(BOB);
        Transaction transaction = signedContractCall(calldata, 0);
 
        System.out.println("=== allowance() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
        assertEquals(4 + 32 + 32, transaction.getInput().length() / 2,
            "allowance calldata must be exactly 68 bytes");
    }
 
    @Test
    @Order(11)
    @DisplayName("Test 11")
    void testCalldataTransferFrom() throws Exception {
        BigInteger amount = BigInteger.valueOf(200);
        String calldata = ID_TRANSFER_FROM + padAddress(ALICE) + padAddress(BOB) + padUint256(amount);
        Transaction transaction = signedContractCall(calldata, 0);
 
        System.out.println("=== transferFrom() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
        assertEquals(4 + 32 + 32 + 32, transaction.getInput().length() / 2,
            "transferFrom calldata must be exactly 100 bytes");
    }
 
    @Test
    @Order(12)
    @DisplayName("Test 12")
    void testCalldataIncreaseAllowance() throws Exception {
        BigInteger amount = BigInteger.valueOf(200);
        String calldata = ID_INCREASE_ALLOWANCE + padAddress(BOB) + padUint256(amount);
        Transaction transaction = signedContractCall(calldata, 0);
 
        System.out.println("=== increaseAllowance() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
        assertEquals(4 + 32 + 32, transaction.getInput().length() / 2,
            "increaseAllowance calldata must be exactly 68 bytes");
    }
  
    @Test
    @Order(13)
    @DisplayName("Test 13")
    void testCalldataDecreaseAllowance() throws Exception {
        BigInteger amount = BigInteger.valueOf(100);
        String calldata = ID_DECREASE_ALLOWANCE + padAddress(BOB) + padUint256(amount);
        Transaction transaction = signedContractCall(calldata, 0);
 
        System.out.println("=== decreaseAllowance() calldata ===");
        System.out.println("  calldata: " + transaction.getInput());
        System.out.println("  Expected: " + calldata);
        System.out.println();
 
        assertEquals(calldata, transaction.getInput());
        assertEquals(4 + 32 + 32, transaction.getInput().length() / 2,
            "decreaseAllowance calldata must be exactly 68 bytes");
    }
 
    @Test
    @Order(14)
    @DisplayName("Test 14")
    void testIsContractCall() throws Exception {
        Transaction transaction = signedContractCall(ID_TRANSFER + padAddress(BOB) + padUint256(BigInteger.TEN), 0);
 
        System.out.println("=== ContractCall ===");
        System.out.println("  isContractCall():     " + transaction.isContractCall());
        System.out.println("  isDepCoinTransfer():  " + transaction.isDepCoinTransfer());
        System.out.println();
 
        assertTrue(transaction.isContractCall());
        assertFalse(transaction.isDepCoinTransfer());
    }
 
    @Test
    @Order(15)
    @DisplayName("Test 15")
    void testIsDepCoinTransfer() throws Exception {
        Transaction transaction = signedDepCoinTransfer(BOB, 500, 0);
 
        System.out.println("=== DepCoinTransfer() ===");
        System.out.println("  isDepCoinTransfer(): " + transaction.isDepCoinTransfer());
        System.out.println("  isContractCall():    " + transaction.isContractCall());
        System.out.println();
 
        assertTrue(transaction.isDepCoinTransfer());
        assertFalse(transaction.isContractCall());
    }


    private Transaction signedContractCall(String calldata, long nonce) throws Exception {
        Transaction transaction = new Transaction(
            ALICE, CONTRACT, calldata, 0,
            nonce, 1, 100_000, aliceKeys.getPublic()
        );
        transaction.sign(aliceKeys.getPrivate());
        return transaction;
    }
 
    private Transaction signedDepCoinTransfer(String to, long amount, long nonce) throws Exception {
        Transaction transaction = new Transaction(
            ALICE, to, null, amount,
            nonce, 1, 100_000, aliceKeys.getPublic()
        );
        transaction.sign(aliceKeys.getPrivate());
        return transaction;
    }
 
    private String padAddress(String address) {
        String hex = address.startsWith("0x") ? address.substring(2) : address;
        return "0".repeat(64 - hex.length()) + hex;
    }
 
    private String padUint256(BigInteger value) {
        return String.format("%064x", value);
    }
}
