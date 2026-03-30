package depchain.blockchain;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.*;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EVMExecutorServiceTest {

    static final Address ALICE    = Address.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    static final Address BOB      = Address.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    static final Address CHARLIE  = Address.fromHexString("cccccccccccccccccccccccccccccccccccccccc");
    static final Address CONTRACT = Address.fromHexString("1234567891234567891234567891234567891234");

    static final BigInteger TOTAL_SUPPLY = BigInteger.valueOf(10_000_000_000L);

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

        evm.createAccount(ALICE, Wei.fromEth(100));
        evm.createAccount(BOB, Wei.fromEth(100));
        evm.createAccount(CHARLIE, Wei.fromEth(1));

        evm.deployISTCoin(
            CONTRACT,
            ALICE,
            TOTAL_SUPPLY
        );
    }

    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testCreateAccount() {
        Wei aliceBalance = evm.getBalance(ALICE);

        System.out.println("=== createAccount - correct balance ===");
        System.out.println("  Alice DepCoin balance: " + aliceBalance);
        System.out.println();

        assertEquals(Wei.fromEth(100), aliceBalance,
            "Alice should have 100 worth of DepCoin after createAccount");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testDeployTotalSupply() {
        BigInteger result = call(ALICE, ID_TOTAL_SUPPLY);

        System.out.println("=== deployISTCoin - totalSupply ===");
        System.out.println("  totalSupply: " + result);
        System.out.println();

        assertEquals(TOTAL_SUPPLY, result,
            "totalSupply should be 100M * 10^2 after deployment");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testDeployAliceBalance() {
        BigInteger result = call(ALICE, ID_BALANCE_OF + pad(ALICE));

        System.out.println("=== deployISTCoin - Alice IST balance ===");
        System.out.println("  Alice IST balance: " + result);
        System.out.println();

        assertEquals(TOTAL_SUPPLY, result,
            "Alice should hold the entire IST supply after deployment");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4")
    void testDeployOtherBalances() {
        BigInteger bobBalance = call(ALICE, ID_BALANCE_OF + pad(BOB));
        BigInteger charlieBalance = call(ALICE, ID_BALANCE_OF + pad(CHARLIE));

        System.out.println("=== deployISTCoin - Bob and Charlie balances ===");
        System.out.println("  Bob balance:     " + bobBalance);
        System.out.println("  Charlie balance: " + charlieBalance);
        System.out.println();

        assertEquals(BigInteger.ZERO, bobBalance,     "Bob should start with 0 IST");
        assertEquals(BigInteger.ZERO, charlieBalance, "Charlie should start with 0 IST");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testContractTransfer() {
        ExecutionResult result = evm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_TRANSFER + pad(BOB) + pad(BigInteger.valueOf(1000))));

        BigInteger aliceBalance = call(ALICE, ID_BALANCE_OF + pad(ALICE));
        BigInteger bobBalance = call(ALICE, ID_BALANCE_OF + pad(BOB));

        System.out.println("=== callContract transfer ===");
        System.out.println("  success:       " + result.success);
        System.out.println("  gasUsed:       " + result.gasUsed);
        System.out.println("  Alice balance: " + aliceBalance);
        System.out.println("  Bob balance:   " + bobBalance);
        System.out.println();

        assertTrue(result.success, "transfer should succeed");
        assertTrue(result.gasUsed > 0, "gasUsed should be > 0");
        assertEquals(BigInteger.valueOf(9_999_999_000L), aliceBalance, "Alice balance should decrease");
        assertEquals(BigInteger.valueOf(1000), bobBalance, "Bob balance should increase");
    }

    
    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testContractApprove() {
        ExecutionResult result = evm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_APPROVE + pad(BOB) + pad(BigInteger.valueOf(500)))
        );

        BigInteger allowance = call(ALICE, ID_ALLOWANCE + pad(ALICE) + pad(BOB));

        System.out.println("=== callContract approve ===");
        System.out.println("  success:              " + result.success);
        System.out.println("  allowance(Alice, Bob): " + allowance);
        System.out.println();

        assertTrue(result.success, "approve should succeed");
        assertEquals(BigInteger.valueOf(500), allowance, "Bob allowance should be 500");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7")
    void testContractTransferFrom() {
        ExecutionResult result = evm.callContract(
            BOB, CONTRACT,
            Bytes.fromHexString(ID_TRANSFER_FROM + pad(ALICE) + pad(CHARLIE) + pad(BigInteger.valueOf(200)))
        );

        BigInteger aliceBalance = call(ALICE, ID_BALANCE_OF + pad(ALICE));
        BigInteger charlieBalance = call(ALICE, ID_BALANCE_OF + pad(CHARLIE));
        BigInteger allowance = call(ALICE, ID_ALLOWANCE + pad(ALICE) + pad(BOB));

        System.out.println("=== callContract transferFrom ===");
        System.out.println("  success:               " + result.success);
        System.out.println("  Alice balance:         " + aliceBalance);
        System.out.println("  Charlie balance:       " + charlieBalance);
        System.out.println("  allowance(Alice, Bob): " + allowance);
        System.out.println();

        assertTrue(result.success, "transferFrom should succeed");
        assertEquals(BigInteger.valueOf(9_999_998_800L), aliceBalance, "Alice balance should decrease");
        assertEquals(BigInteger.valueOf(200), charlieBalance, "Charlie should receive 200");
        assertEquals(BigInteger.valueOf(300), allowance, "Bob allowance should decrease to 300");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8")
    void testContractIncreaseAllowance() {
        ExecutionResult result = evm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_INCREASE_ALLOWANCE + pad(BOB) + pad(BigInteger.valueOf(200)))
        );

        BigInteger allowance = call(ALICE, ID_ALLOWANCE + pad(ALICE) + pad(BOB));

        System.out.println("=== callContract increaseAllowance ===");
        System.out.println("  allowance(Alice, Bob): " + allowance);
        System.out.println();

        assertTrue(result.success, "increaseAllowance should succeed");
        assertEquals(BigInteger.valueOf(500), allowance, "Allowance should be 500");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9")
    void testContractDecreaseAllowance() {
        ExecutionResult result = evm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_DECREASE_ALLOWANCE + pad(BOB) + pad(BigInteger.valueOf(100)))
        );

        BigInteger allowance = call(ALICE, ID_ALLOWANCE + pad(ALICE) + pad(BOB));

        System.out.println("=== callContract decreaseAllowance ===");
        System.out.println("  allowance(Alice, Bob): " + allowance);
        System.out.println();

        assertTrue(result.success, "decreaseAllowance should succeed");
        assertEquals(BigInteger.valueOf(400), allowance, "Allowance should be 400");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10")
    void testContractApproveRevertsWhenNonZero() {
        ExecutionResult result = evm.callContract(
            ALICE, CONTRACT,
            Bytes.fromHexString(ID_APPROVE + pad(BOB) + pad(BigInteger.valueOf(999)))
        );

        BigInteger allowance = call(ALICE, ID_ALLOWANCE + pad(ALICE) + pad(BOB));

        System.out.println("=== callContract approve revert ===");
        System.out.println("  success:               " + result.success);
        System.out.println("  allowance(Alice, Bob): " + allowance);
        System.out.println();

        assertFalse(result.success, "approve with non-zero allowance should revert");
        assertEquals(BigInteger.valueOf(400), allowance, "Allowance must stay at 400");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11")
    void testTransferDepCoin() {
        Wei aliceBalanceBefore = evm.getBalance(ALICE);

        ExecutionResult result = evm.transferDepCoin(
            ALICE, BOB, Wei.of(1000)
        );

        Wei aliceBalanceAfter = evm.getBalance(ALICE);
        Wei bobBalanceAfter = evm.getBalance(BOB);

        System.out.println("=== transferDepCoin ===");
        System.out.println("  success:             " + result.success);
        System.out.println("  Alice balance before: " + aliceBalanceBefore);
        System.out.println("  Alice balance after:  " + aliceBalanceAfter);
        System.out.println("  Bob balance after:    " + bobBalanceAfter);
        System.out.println();

        assertTrue(result.success, "transfer should succeed");
        assertEquals(aliceBalanceBefore.subtract(Wei.of(1000)), aliceBalanceAfter, "Alice balance should decrease by 1000");
        assertEquals(Wei.fromEth(100).add(Wei.of(1000)), bobBalanceAfter, "Bob balance should increase by 1000");
    }

    
    @Test
    @Order(12)
    @DisplayName("Test 12")
    void testTransferDepCoinInsufficientBalance() {
        Wei hugeAmount = Wei.fromEth(99999);

        ExecutionResult result = evm.transferDepCoin(
            ALICE, BOB, hugeAmount
        );

        System.out.println("=== transferDepCoin insufficient balance ===");
        System.out.println("  success:  " + result.success);
        System.out.println();

        assertFalse(result.success, "transfer should fail when sender has insufficient balance");
    }

    @Test
    @Order(13)
    @DisplayName("Test 13")
    void testTransferDepCoinGasUsed() {
        ExecutionResult result = evm.transferDepCoin(
            ALICE, BOB, Wei.of(1)
        );

        System.out.println("=== transferDepCoin gasUsed ===");
        System.out.println("  gasUsed:  " + result.gasUsed);
        System.out.println();

        assertEquals(21000, result.gasUsed, "Native transfer base gas cost should be 21000");
    }
    
    @Test
    @Order(14)
    @DisplayName("Test 14")
    void testDeductGasFeeNormal() {
        Wei balanceBefore = evm.getBalance(ALICE);
        long gasPrice = 2;
        long gasLimit = 100_000;
        long gasUsed  = 50_000;

        evm.deductGasFee(ALICE, gasPrice, gasLimit, gasUsed);

        Wei balanceAfter = evm.getBalance(ALICE);
        Wei expectedFee  = Wei.of(gasPrice * gasUsed);

        System.out.println("=== deductGasFee when gasUsed < gasLimit ===");
        System.out.println("  balance before: " + balanceBefore);
        System.out.println("  balance after:  " + balanceAfter);
        System.out.println("  fee deducted:   " + balanceBefore.subtract(balanceAfter));
        System.out.println();

        assertEquals(balanceBefore.subtract(expectedFee), balanceAfter,
            "fee should be gasPrice * gasUsed when gasUsed < gasLimit");
    }

    @Test
    @Order(15)
    @DisplayName("Test 15 ")
    void testDeductGasFeeGasLimitExceeded() {
        Wei balanceBefore = evm.getBalance(ALICE);
        long gasPrice = 2;
        long gasLimit = 30_000;
        long gasUsed  = 50_000;

        evm.deductGasFee(ALICE, gasPrice, gasLimit, gasUsed);

        Wei balanceAfter = evm.getBalance(ALICE);
        Wei expectedFee  = Wei.of(gasPrice * gasLimit);

        System.out.println("=== deductGasFee when gasUsed > gasLimit ===");
        System.out.println("  balance before: " + balanceBefore);
        System.out.println("  balance after:  " + balanceAfter);
        System.out.println("  fee deducted:   " + balanceBefore.subtract(balanceAfter));
        System.out.println("  Expected fee:   " + expectedFee + " (capped at gasLimit)");
        System.out.println();

        assertEquals(balanceBefore.subtract(expectedFee), balanceAfter,
            "fee should be capped at gasPrice * gasLimit when gasUsed exceeds it");
    }

    @Test
    @Order(16)
    @DisplayName("Test 16")
    void testDeductGasFeeInsufficientBalance() {
        long gasPrice = Long.MAX_VALUE / 2;
        long gasLimit = 2;
        long gasUsed  = 1;

        System.out.println("=== deductGasFee insufficient balance ===");
        System.out.println();

        assertThrows(IllegalStateException.class,
            () -> evm.deductGasFee(CHARLIE, gasPrice, gasLimit, gasUsed),
            "deductGasFee should throw when account cannot afford the fee"
        );
    }

    @Test
    @Order(17)
    @DisplayName("Test 17")
    void testGetNonceInitial() {
        long nonce = evm.getNonce(CHARLIE);

        System.out.println("=== getNonce initial ===");
        System.out.println("  Charlie nonce: " + nonce);
        System.out.println();

        assertEquals(0, nonce, "Fresh account should have nonce 0");
    }

    @Test
    @Order(18)
    @DisplayName("Test 18")
    void testIncrementNonce() {
        evm.incrementNonce(CHARLIE);
        long nonce = evm.getNonce(CHARLIE);

        System.out.println("=== incrementNonce ===");
        System.out.println("  Charlie nonce after increment: " + nonce);
        System.out.println();

        assertEquals(1, nonce, "Nonce should be 1 after one increment");
    }

    @Test
    @Order(19)
    @DisplayName("Test 19")
    void testKnownAddresses() {
        System.out.println("=== knownAddresses ===");
        System.out.println("  Known addresses: " + evm.getKnownAddresses());
        System.out.println();

        assertTrue(evm.getKnownAddresses().contains(ALICE),    "ALICE should be known");
        assertTrue(evm.getKnownAddresses().contains(BOB),      "BOB should be known");
        assertTrue(evm.getKnownAddresses().contains(CHARLIE),  "CHARLIE should be known");
        assertTrue(evm.getKnownAddresses().contains(CONTRACT), "CONTRACT should be known");
    }
    
    static BigInteger call(Address sender, String callDataHex) {
        ExecutionResult result = evm.callContract(
            sender,
            CONTRACT,
            Bytes.fromHexString(callDataHex)
        );
        return result.returnValue;
    }

    static String pad(Address address) {
        String hex = address.toHexString();
        if (hex.startsWith("0x")) hex = hex.substring(2);
        return "0".repeat(64 - hex.length()) + hex;
    }

    static String pad(BigInteger value) {
        return String.format("%064x", value);
    }

}