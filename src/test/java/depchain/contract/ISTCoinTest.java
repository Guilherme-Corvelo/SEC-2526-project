package depchain.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.*;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.junit.jupiter.api.*;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import depchain.Debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests share a single world state, each test builds on the previous one,
// just like transactions on a real blockchain.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ISTCoinTest {

    static final Address ALICE    = Address.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    static final Address BOB      = Address.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    static final Address CHARLIE  = Address.fromHexString("cccccccccccccccccccccccccccccccccccccccc");
    static final Address CONTRACT = Address.fromHexString("1234567891234567891234567891234567891234");

    static final BigInteger TOTAL_SUPPLY = BigInteger.valueOf(10_000_000_000L); // 100M * 10^2

    static final String ID_TOTAL_SUPPLY = "18160ddd";
    static final String ID_BALANCE_OF = "70a08231";
    static final String ID_TRANSFER = "a9059cbb";
    static final String ID_APPROVE = "095ea7b3";
    static final String ID_ALLOWANCE = "dd62ed3e";
    static final String ID_TRANSFER_FROM = "23b872dd";
    static final String ID_INCREASE_ALLOWANCE = "39509351";
    static final String ID_DECREASE_ALLOWANCE = "a457c2d7";


    static SimpleWorld simpleWorld;
    static EVMExecutor executor;
    static ByteArrayOutputStream byteArrayOutputStream;

    @BeforeAll
    static void deploy() {
        simpleWorld = new SimpleWorld();

        simpleWorld.createAccount(ALICE,    0, Wei.fromEth(100));
        simpleWorld.createAccount(BOB,      0, Wei.fromEth(100));
        simpleWorld.createAccount(CHARLIE,  0, Wei.fromEth(100));
        simpleWorld.createAccount(CONTRACT, 0, Wei.fromEth(0));

        MutableAccount contractAccount = (MutableAccount) simpleWorld.get(CONTRACT);

        contractAccount.setCode(Bytes.fromHexString(
            "608060405234801561000f575f5ffd5b50600436106100a7575f3560e01c8063395093511161006f578063395093511461016557806370a082311461019557806395d89b41146101c5578063a457c2d7146101e3578063a9059cbb14610213578063dd62ed3e14610243576100a7565b806306fdde03146100ab578063095ea7b3146100c957806318160ddd146100f957806323b872dd14610117578063313ce56714610147575b5f5ffd5b6100b3610273565b6040516100c09190610df5565b60405180910390f35b6100e360048036038101906100de9190610ea6565b6102ac565b6040516100f09190610efe565b60405180910390f35b61010161045d565b60405161010e9190610f26565b60405180910390f35b610131600480360381019061012c9190610f3f565b610462565b60405161013e9190610efe565b60405180910390f35b61014f610742565b60405161015c9190610faa565b60405180910390f35b61017f600480360381019061017a9190610ea6565b610747565b60405161018c9190610efe565b60405180910390f35b6101af60048036038101906101aa9190610fc3565b6108bd565b6040516101bc9190610f26565b60405180910390f35b6101cd610903565b6040516101da9190610df5565b60405180910390f35b6101fd60048036038101906101f89190610ea6565b61093c565b60405161020a9190610efe565b60405180910390f35b61022d60048036038101906102289190610ea6565b610b6d565b60405161023a9190610efe565b60405180910390f35b61025d60048036038101906102589190610fee565b610d03565b60405161026a9190610f26565b60405180910390f35b6040518060400160405280600881526020017f49535420436f696e00000000000000000000000000000000000000000000000081525081565b5f5f82148061033257505f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054145b610371576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161036890611076565b60405180910390fd5b8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b9258460405161044b9190610f26565b60405180910390a36001905092915050565b5f5481565b5f8160015f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205410156104e3576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016104da906110de565b60405180910390fd5b8160025f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054101561059e576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161059590611146565b60405180910390fd5b8160015f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546105ea9190611191565b925050819055508160025f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546106789190611191565b925050819055508160015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546106cb91906111c4565b925050819055508273ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef8460405161072f9190610f26565b60405180910390a3600190509392505050565b600281565b5f8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546107cf91906111c4565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92560025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8773ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20546040516108ab9190610f26565b60405180910390a36001905092915050565b5f60015f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050919050565b6040518060400160405280600381526020017f495354000000000000000000000000000000000000000000000000000000000081525081565b5f8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205410156109f8576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016109ef90611241565b60405180910390fd5b8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610a7f9190611191565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92560025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8773ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054604051610b5b9190610f26565b60405180910390a36001905092915050565b5f8160015f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20541015610bee576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610be5906110de565b60405180910390fd5b8160015f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610c3a9190611191565b925050819055508160015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610c8d91906111c4565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef84604051610cf19190610f26565b60405180910390a36001905092915050565b5f60025f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905092915050565b5f81519050919050565b5f82825260208201905092915050565b8281835e5f83830152505050565b5f601f19601f8301169050919050565b5f610dc782610d85565b610dd18185610d8f565b9350610de1818560208601610d9f565b610dea81610dad565b840191505092915050565b5f6020820190508181035f830152610e0d8184610dbd565b905092915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610e4282610e19565b9050919050565b610e5281610e38565b8114610e5c575f5ffd5b50565b5f81359050610e6d81610e49565b92915050565b5f819050919050565b610e8581610e73565b8114610e8f575f5ffd5b50565b5f81359050610ea081610e7c565b92915050565b5f5f60408385031215610ebc57610ebb610e15565b5b5f610ec985828601610e5f565b9250506020610eda85828601610e92565b9150509250929050565b5f8115159050919050565b610ef881610ee4565b82525050565b5f602082019050610f115f830184610eef565b92915050565b610f2081610e73565b82525050565b5f602082019050610f395f830184610f17565b92915050565b5f5f5f60608486031215610f5657610f55610e15565b5b5f610f6386828701610e5f565b9350506020610f7486828701610e5f565b9250506040610f8586828701610e92565b9150509250925092565b5f60ff82169050919050565b610fa481610f8f565b82525050565b5f602082019050610fbd5f830184610f9b565b92915050565b5f60208284031215610fd857610fd7610e15565b5b5f610fe584828501610e5f565b91505092915050565b5f5f6040838503121561100457611003610e15565b5b5f61101185828601610e5f565b925050602061102285828601610e5f565b9150509250929050565b7f526573657420616c6c6f77616e636520746f20302066697273740000000000005f82015250565b5f611060601a83610d8f565b915061106b8261102c565b602082019050919050565b5f6020820190508181035f83015261108d81611054565b9050919050565b7f496e73756666696369656e742062616c616e63650000000000000000000000005f82015250565b5f6110c8601483610d8f565b91506110d382611094565b602082019050919050565b5f6020820190508181035f8301526110f5816110bc565b9050919050565b7f496e73756666696369656e7420616c6c6f77616e6365000000000000000000005f82015250565b5f611130601683610d8f565b915061113b826110fc565b602082019050919050565b5f6020820190508181035f83015261115d81611124565b9050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f61119b82610e73565b91506111a683610e73565b92508282039050818111156111be576111bd611164565b5b92915050565b5f6111ce82610e73565b91506111d983610e73565b92508282019050808211156111f1576111f0611164565b5b92915050565b7f416c6c6f77616e63652062656c6f77207a65726f0000000000000000000000005f82015250565b5f61122b601483610d8f565b9150611236826111f7565b602082019050919050565b5f6020820190508181035f8301526112588161121f565b905091905056fea26469706673582212201fb01d0fba79bbe66753a1bcb3edb4e9777fa4f962f966b576438314102a03bb64736f6c634300081f0033"
        ));

        // slot 0 = totalSupply
        contractAccount.setStorageValue(
            UInt256.valueOf(0),
            UInt256.fromBytes(Bytes.fromHexString(convertBigIntegerToHex256Bit(TOTAL_SUPPLY)))
        );

        // _balances[Alice] = totalSupply
        String aliceBalanceSlot = Numeric.toHexStringNoPrefix(
            Hash.sha3(Numeric.hexStringToByteArray(
                padHexStringTo256Bit(ALICE.toHexString()) +
                convertBigIntegerToHex256Bit(BigInteger.valueOf(1))
            ))
        );
        contractAccount.setStorageValue(
            UInt256.fromHexString(aliceBalanceSlot),
            UInt256.fromBytes(Bytes.fromHexString(convertBigIntegerToHex256Bit(TOTAL_SUPPLY)))
        );

        simpleWorld.updater().commit();

        byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        StandardJsonTracer tracer = new StandardJsonTracer(printStream, true, true, true, true);

        executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.tracer(tracer);
        executor.receiver(CONTRACT);
        executor.code(simpleWorld.get(CONTRACT).getCode());
        executor.worldUpdater(simpleWorld.updater());
        executor.commitWorldState();
    }
    
    @Test
    @Order(1)
    @DisplayName("Test 1")
    void testTotalSupply() {
        BigInteger result = call(ALICE, ID_TOTAL_SUPPLY);
        Debug.debug(" === totalSupply() ===");
        Debug.debug("  totalSupply: " + result);
        Debug.debug("  Expected:    " + TOTAL_SUPPLY);
        System.out.println();
        assertEquals(TOTAL_SUPPLY, result,
            "totalSupply should be 100M units * 10^2 decimals = 10,000,000,000");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2")
    void testAliceInitialBalance() {
        BigInteger result = call(ALICE,
            ID_BALANCE_OF + padHexStringTo256Bit(ALICE.toHexString())
        );
        Debug.debug(" === balanceOf(Alice) ===");
        Debug.debug("  Alice balance: " + result);
        Debug.debug("  Expected:      " + TOTAL_SUPPLY);
        System.out.println();
        assertEquals(TOTAL_SUPPLY, result,
            "Alice should hold the entire supply after deployment");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3")
    void testTransfer() {
        call(ALICE,
            ID_TRANSFER +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(1000))
        );

        BigInteger aliceBalance = call(ALICE,
            ID_BALANCE_OF + padHexStringTo256Bit(ALICE.toHexString())
        );
        BigInteger bobBalance = call(ALICE,
            ID_BALANCE_OF + padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === transfer(Bob, 1000) by Alice ===");
        Debug.debug("  Alice balance: " + aliceBalance);
        Debug.debug("  Expected:      9999999000");
        Debug.debug("  Bob balance:   " + bobBalance);
        Debug.debug("  Expected:      1000");
        System.out.println();

        assertEquals(BigInteger.valueOf(9_999_999_000L), aliceBalance,
            "Alice balance should decrease by 1000");
        assertEquals(BigInteger.valueOf(1000), bobBalance,
            "Bob balance should increase by 1000");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4")
    void testApproveFirstTime() {
        call(ALICE,
            ID_APPROVE +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(500))
        );

        BigInteger allowance = call(ALICE,
            ID_ALLOWANCE +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === approve(Bob, 500) first time by Alice ===");
        Debug.debug("  allowance(Alice, Bob): " + allowance);
        Debug.debug("  Expected:              500");
        System.out.println();

        assertEquals(BigInteger.valueOf(500), allowance,
            "Bob's allowance should be 500 after first approve");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5")
    void testTransferFrom() {
        call(BOB,
            ID_TRANSFER_FROM +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(CHARLIE.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(200))
        );

        BigInteger aliceBalance = call(ALICE,
            ID_BALANCE_OF + padHexStringTo256Bit(ALICE.toHexString())
        );
        BigInteger charlieBalance = call(ALICE,
            ID_BALANCE_OF + padHexStringTo256Bit(CHARLIE.toHexString())
        );
        BigInteger allowance = call(ALICE,
            ID_ALLOWANCE +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === transferFrom(Alice, Charlie, 200) by Bob ===");
        Debug.debug("  Alice balance:         " + aliceBalance);
        Debug.debug("  Expected:              9999998800");
        Debug.debug("  Charlie balance:       " + charlieBalance);
        Debug.debug("  Expected:              200");
        Debug.debug("  allowance(Alice, Bob): " + allowance);
        Debug.debug("  Expected:              300");
        System.out.println();

        assertEquals(BigInteger.valueOf(9_999_998_800L), aliceBalance,
            "Alice balance should decrease by 200");
        assertEquals(BigInteger.valueOf(200), charlieBalance,
            "Charlie should receive 200 tokens");
        assertEquals(BigInteger.valueOf(300), allowance,
            "Bob's allowance should be 300");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6")
    void testIncreaseAllowance() {
        call(ALICE,
            ID_INCREASE_ALLOWANCE +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(200))
        );

        BigInteger allowance = call(ALICE,
            ID_ALLOWANCE +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === increaseAllowance(Bob, 200) by Alice ===");
        Debug.debug("  allowance(Alice, Bob): " + allowance);
        Debug.debug("  Expected:              500");
        System.out.println();

        assertEquals(BigInteger.valueOf(500), allowance,
            "Bob's allowance should be 300 + 200 = 500");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7")
    void testDecreaseAllowance() {
        call(ALICE,
            ID_DECREASE_ALLOWANCE +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(100))
        );

        BigInteger allowance = call(ALICE,
            ID_ALLOWANCE +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === decreaseAllowance(Bob, 100) by Alice ===");
        Debug.debug("  allowance(Alice, Bob): " + allowance);
        Debug.debug("  Expected:              400");
        System.out.println();

        assertEquals(BigInteger.valueOf(400), allowance,
            "Bob's allowance should be 400");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8")
    void testApproveRevertsWhenNonZero() {
        call(ALICE,
            ID_APPROVE +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(999))
        );

        BigInteger allowance = call(ALICE,
            ID_ALLOWANCE +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === approve(Bob, 999) with non-zero allowance by Alice ===");
        Debug.debug("  allowance(Alice, Bob): " + allowance);
        Debug.debug("  Expected:              400");
        System.out.println();

        assertEquals(BigInteger.valueOf(400), allowance,
            "Allowance must stay at 400");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9")
    void testSafeApproveReset() {
        call(ALICE,
            ID_APPROVE +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.ZERO)
        );

        call(ALICE,
            ID_APPROVE +
            padHexStringTo256Bit(BOB.toHexString()) +
            convertBigIntegerToHex256Bit(BigInteger.valueOf(999))
        );

        BigInteger allowance = call(ALICE,
            ID_ALLOWANCE +
            padHexStringTo256Bit(ALICE.toHexString()) +
            padHexStringTo256Bit(BOB.toHexString())
        );

        Debug.debug(" === approve(Bob, 0) then approve(Bob, 999) by Alice ===");
        Debug.debug("  allowance(Alice, Bob): " + allowance);
        Debug.debug("  Expected:              999");
        System.out.println();

        assertEquals(BigInteger.valueOf(999), allowance,
            "After reset to 0 then set to 999, allowance should be 999");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    static BigInteger call(Address sender, String callDataHex) {
        executor.sender(sender);
        executor.callData(Bytes.fromHexString(callDataHex));
        executor.execute();
        return extractBigIntegerFromReturnData(byteArrayOutputStream);
    }
    
    static BigInteger extractBigIntegerFromReturnData(ByteArrayOutputStream baos) {
        String[] lines = baos.toString().split("\\r?\\n");
        JsonObject jsonObject = JsonParser.parseString(
            lines[lines.length - 1]).getAsJsonObject();

        String memory = jsonObject.get("memory").getAsString();
        JsonArray stack = jsonObject.get("stack").getAsJsonArray();
        int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
        int size   = Integer.decode(stack.get(stack.size() - 2).getAsString());

        String returnData = memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2);
        return new BigInteger(returnData, 16);
    }

    static String convertBigIntegerToHex256Bit(BigInteger number) {
        return String.format("%064x", number);
    }

    static String padHexStringTo256Bit(String hexString) {
        if (hexString.startsWith("0x")) hexString = hexString.substring(2);
        int length = hexString.length();
        int targetLength = 64;
        if (length >= targetLength) return hexString.substring(0, targetLength);
        return "0".repeat(targetLength - length) + hexString;
    }
}