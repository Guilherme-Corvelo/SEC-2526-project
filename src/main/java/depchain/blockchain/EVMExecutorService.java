package depchain.blockchain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EVMExecutorService {
    
    private final SimpleWorld worldState;
    private final EVMExecutor executor;
    private final ByteArrayOutputStream tracerOutput;

    private final Set<Address> knownAddresses = new HashSet<>();

    public EVMExecutorService() {
        this.worldState = new SimpleWorld();

        this.tracerOutput = new ByteArrayOutputStream();

        PrintStream printStream = new PrintStream(tracerOutput);
        StandardJsonTracer tracer = new StandardJsonTracer(
            printStream, true, true, true, true
        );
 
        this.executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        this.executor.tracer(tracer);
        this.executor.worldUpdater(worldState.updater());
        this.executor.commitWorldState();
    }

    public void createAccount(Address address, Wei initialBalance) {
        worldState.createAccount(address, 0 ,initialBalance);
        worldState.updater().commit();
        knownAddresses.add(address);
    }

    public Wei getBalance(Address address) {
        return worldState.get(address).getBalance();
    }

    public long getNonce(Address address) {
        return worldState.get(address).getNonce();
    }

    public void incrementNonce(Address address) {
        MutableAccount account = (MutableAccount) worldState.getAccount(address);
        account.incrementNonce();
        worldState.updater().commit();
    }

    public Set<Address> getKnownAddresses() {
        return knownAddresses;
    }

    public SimpleWorld getWorldState() {
        return worldState;
    }

    public void deployISTCoin(Address contractAddress, Address deployer, Bytes runtimeBytecode, BigInteger totalSupply) {

        // Create contract account
        worldState.createAccount(contractAddress, 0, Wei.fromEth(0));
        MutableAccount contractAccount = (MutableAccount) worldState.get(contractAddress);

        // Load runtime bytecode
        contractAccount.setCode(runtimeBytecode);

        // slot 0 = totalSupply
        contractAccount.setStorageValue(UInt256.valueOf(0), 
                                        UInt256.fromBytes(Bytes.fromHexString(toHex256(totalSupply))));

        // _balances[deployer] = totalSupply
        String deployerBalanceSlot = mappingSlot(deployer.toHexString(), 1);
        
        contractAccount.setStorageValue(UInt256.fromHexString(deployerBalanceSlot),
                                        UInt256.fromBytes(Bytes.fromHexString(toHex256(totalSupply))));

        worldState.updater().commit();

        // executor point at contract
        executor.receiver(contractAddress);
        executor.code(worldState.get(contractAddress).getCode());

        knownAddresses.add(contractAddress);
    }

    public ExecutionResult callContract(Address sender, Address contractAddress, Bytes callData) {

        tracerOutput.reset();

        executor.sender(sender);
        executor.receiver(contractAddress);
        executor.code(worldState.get(contractAddress).getCode());
        executor.callData(callData);
        
        executor.execute();

        if (tracerOutput.size() == 0 ) {
            return new ExecutionResult(false, 0, BigInteger.ZERO);
        }

        boolean reverted = didRevert(tracerOutput);
        long gasUsed = parseGasUsed(tracerOutput);
        
        if (reverted) {
            return new ExecutionResult(false, gasUsed, BigInteger.ZERO);
        }

        BigInteger returnValue = parseReturnValue(tracerOutput);
        return new ExecutionResult(true, gasUsed, returnValue);
    }

    public ExecutionResult transferDepCoin(Address from, Address to, Wei amount) {

        MutableAccount sender = (MutableAccount) worldState.get(from);
        MutableAccount receiver = (MutableAccount) worldState.getAccount(to);

        if (sender == null) {
            return new ExecutionResult(false, 21000, BigInteger.ZERO);
        }

        if (receiver == null) {
            return new ExecutionResult(false, 21000, BigInteger.ZERO);
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            return new ExecutionResult(false, 21000, BigInteger.ZERO);
        }

        sender.decrementBalance(amount);
        receiver.incrementBalance(amount);
        worldState.updater().commit();

        return new ExecutionResult(true, 21000, BigInteger.ZERO);
    }

    public void deductGasFee(Address sender, long gasPrice, long gasLimit, long gasUsed) {

        long fee = gasPrice * Math.min(gasLimit, gasUsed);
        Wei feeWei = Wei.of(fee);

        MutableAccount account = (MutableAccount) worldState.get(sender);

        if (account.getBalance().compareTo(feeWei) < 0) {
            throw new IllegalStateException("Insufficient DepCoin to pay gas fee");
        }

        account.decrementBalance(feeWei);
        worldState.updater().commit();
    }

    private BigInteger parseReturnValue(ByteArrayOutputStream baos) {
        try {
            String[] lines = baos.toString().split("\\r?\\n");
            JsonObject jsonObject = JsonParser.parseString(
                lines[lines.length - 1]).getAsJsonObject();
 
            String memory   = jsonObject.get("memory").getAsString();
            JsonArray stack = jsonObject.get("stack").getAsJsonArray();
            int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
            int size   = Integer.decode(stack.get(stack.size() - 2).getAsString());
 
            if (size == 0) return BigInteger.ZERO;
 
            String returnData = memory.substring(
                2 + offset * 2,
                2 + offset * 2 + size * 2
            );
            return new BigInteger(returnData, 16);
 
        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    private long parseGasUsed(ByteArrayOutputStream baos) {
        try {
            String[] lines = baos.toString().split("\\r?\\n");
 
            // First line has initial gas
            JsonObject firstLine = JsonParser.parseString(lines[0]).getAsJsonObject();
            long initialGas = Long.decode(firstLine.get("gas").getAsString());
 
            // Last line has remaining gas
            JsonObject lastLine = JsonParser.parseString(
                lines[lines.length - 1]).getAsJsonObject();
            long remainingGas = Long.decode(lastLine.get("gas").getAsString());
 
            return initialGas - remainingGas;
 
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean didRevert(ByteArrayOutputStream baos) {
        try {
            String[] lines = baos.toString().split("\\r?\\n");
            JsonObject lastLine = JsonParser.parseString(
                lines[lines.length - 1]).getAsJsonObject();

            // "op" field contains the opcode number of the last executed instruction
            // REVERT opcode = 0xfd = 253
            String op = lastLine.get("op").getAsString();            
            return op.equals("0xfd");

        } catch (Exception e) {
            return false;
        }
    }

    private String mappingSlot(String address, int slotIndex) {
        String paddedKey  = padHex(address);
        String paddedSlot = toHex256(BigInteger.valueOf(slotIndex));
        byte[] input      = Numeric.hexStringToByteArray(paddedKey + paddedSlot);
        return Numeric.toHexStringNoPrefix(Hash.sha3(input));
    }
 
    private String toHex256(BigInteger n) {
        return String.format("%064x", n);
    }
 
    private String padHex(String hex) {
        if (hex.startsWith("0x")) hex = hex.substring(2);
        return "0".repeat(64 - hex.length()) + hex;
    }

}
