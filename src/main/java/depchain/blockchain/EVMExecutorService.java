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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EVMExecutorService {
    
    private final SimpleWorld worldState;
    private final EVMExecutor executor;
    private final ByteArrayOutputStream tracerOutput;

    private final String runtimeBytecode = "608060405234801561000f575f5ffd5b50600436106100a7575f3560e01c8063395093511161006f578063395093511461016557806370a082311461019557806395d89b41146101c5578063a457c2d7146101e3578063a9059cbb14610213578063dd62ed3e14610243576100a7565b806306fdde03146100ab578063095ea7b3146100c957806318160ddd146100f957806323b872dd14610117578063313ce56714610147575b5f5ffd5b6100b3610273565b6040516100c09190610df5565b60405180910390f35b6100e360048036038101906100de9190610ea6565b6102ac565b6040516100f09190610efe565b60405180910390f35b61010161045d565b60405161010e9190610f26565b60405180910390f35b610131600480360381019061012c9190610f3f565b610462565b60405161013e9190610efe565b60405180910390f35b61014f610742565b60405161015c9190610faa565b60405180910390f35b61017f600480360381019061017a9190610ea6565b610747565b60405161018c9190610efe565b60405180910390f35b6101af60048036038101906101aa9190610fc3565b6108bd565b6040516101bc9190610f26565b60405180910390f35b6101cd610903565b6040516101da9190610df5565b60405180910390f35b6101fd60048036038101906101f89190610ea6565b61093c565b60405161020a9190610efe565b60405180910390f35b61022d60048036038101906102289190610ea6565b610b6d565b60405161023a9190610efe565b60405180910390f35b61025d60048036038101906102589190610fee565b610d03565b60405161026a9190610f26565b60405180910390f35b6040518060400160405280600881526020017f49535420436f696e00000000000000000000000000000000000000000000000081525081565b5f5f82148061033257505f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054145b610371576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161036890611076565b60405180910390fd5b8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b9258460405161044b9190610f26565b60405180910390a36001905092915050565b5f5481565b5f8160015f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205410156104e3576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016104da906110de565b60405180910390fd5b8160025f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054101561059e576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161059590611146565b60405180910390fd5b8160015f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546105ea9190611191565b925050819055508160025f8673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546106789190611191565b925050819055508160015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546106cb91906111c4565b925050819055508273ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef8460405161072f9190610f26565b60405180910390a3600190509392505050565b600281565b5f8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8282546107cf91906111c4565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92560025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8773ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20546040516108ab9190610f26565b60405180910390a36001905092915050565b5f60015f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20549050919050565b6040518060400160405280600381526020017f495354000000000000000000000000000000000000000000000000000000000081525081565b5f8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205410156109f8576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016109ef90611241565b60405180910390fd5b8160025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610a7f9190611191565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92560025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8773ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054604051610b5b9190610f26565b60405180910390a36001905092915050565b5f8160015f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20541015610bee576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610be5906110de565b60405180910390fd5b8160015f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610c3a9190611191565b925050819055508160015f8573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f828254610c8d91906111c4565b925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef84604051610cf19190610f26565b60405180910390a36001905092915050565b5f60025f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f2054905092915050565b5f81519050919050565b5f82825260208201905092915050565b8281835e5f83830152505050565b5f601f19601f8301169050919050565b5f610dc782610d85565b610dd18185610d8f565b9350610de1818560208601610d9f565b610dea81610dad565b840191505092915050565b5f6020820190508181035f830152610e0d8184610dbd565b905092915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610e4282610e19565b9050919050565b610e5281610e38565b8114610e5c575f5ffd5b50565b5f81359050610e6d81610e49565b92915050565b5f819050919050565b610e8581610e73565b8114610e8f575f5ffd5b50565b5f81359050610ea081610e7c565b92915050565b5f5f60408385031215610ebc57610ebb610e15565b5b5f610ec985828601610e5f565b9250506020610eda85828601610e92565b9150509250929050565b5f8115159050919050565b610ef881610ee4565b82525050565b5f602082019050610f115f830184610eef565b92915050565b610f2081610e73565b82525050565b5f602082019050610f395f830184610f17565b92915050565b5f5f5f60608486031215610f5657610f55610e15565b5b5f610f6386828701610e5f565b9350506020610f7486828701610e5f565b9250506040610f8586828701610e92565b9150509250925092565b5f60ff82169050919050565b610fa481610f8f565b82525050565b5f602082019050610fbd5f830184610f9b565b92915050565b5f60208284031215610fd857610fd7610e15565b5b5f610fe584828501610e5f565b91505092915050565b5f5f6040838503121561100457611003610e15565b5b5f61101185828601610e5f565b925050602061102285828601610e5f565b9150509250929050565b7f526573657420616c6c6f77616e636520746f20302066697273740000000000005f82015250565b5f611060601a83610d8f565b915061106b8261102c565b602082019050919050565b5f6020820190508181035f83015261108d81611054565b9050919050565b7f496e73756666696369656e742062616c616e63650000000000000000000000005f82015250565b5f6110c8601483610d8f565b91506110d382611094565b602082019050919050565b5f6020820190508181035f8301526110f5816110bc565b9050919050565b7f496e73756666696369656e7420616c6c6f77616e6365000000000000000000005f82015250565b5f611130601683610d8f565b915061113b826110fc565b602082019050919050565b5f6020820190508181035f83015261115d81611124565b9050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f61119b82610e73565b91506111a683610e73565b92508282039050818111156111be576111bd611164565b5b92915050565b5f6111ce82610e73565b91506111d983610e73565b92508282019050808211156111f1576111f0611164565b5b92915050565b7f416c6c6f77616e63652062656c6f77207a65726f0000000000000000000000005f82015250565b5f61122b601483610d8f565b9150611236826111f7565b602082019050919050565b5f6020820190508181035f8301526112588161121f565b905091905056fea26469706673582212201fb01d0fba79bbe66753a1bcb3edb4e9777fa4f962f966b576438314102a03bb64736f6c634300081f0033";

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

    public void deployISTCoin(Address contractAddress, Address deployer, BigInteger totalSupply) {

        // Create contract account
        worldState.createAccount(contractAddress, 0, Wei.fromEth(0));
        MutableAccount contractAccount = (MutableAccount) worldState.get(contractAddress);

        // Load runtime bytecode
        contractAccount.setCode(Bytes.fromHexString(runtimeBytecode));

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

    public void deployISTCoin(Address contractAddress, BigInteger totalSupply) {

        // Create contract account
        worldState.createAccount(contractAddress, 0, Wei.fromEth(0));
        MutableAccount contractAccount = (MutableAccount) worldState.get(contractAddress);

        // Load runtime bytecode
        contractAccount.setCode(Bytes.fromHexString(runtimeBytecode));

        // slot 0 = totalSupply
        contractAccount.setStorageValue(UInt256.valueOf(0), 
                                        UInt256.fromBytes(Bytes.fromHexString(toHex256(totalSupply))));


        worldState.updater().commit();

        // executor point at contract
        executor.receiver(contractAddress);
        executor.code(worldState.get(contractAddress).getCode());

        knownAddresses.add(contractAddress);
    }

    public ExecutionResult callContract(Address sender, Address contractAddress, Bytes callData) {

        tracerOutput.reset();

        WorldUpdater updater = worldState.updater();

        executor.worldUpdater(updater);

        executor.sender(sender);
        executor.receiver(contractAddress);
        executor.code(worldState.get(contractAddress).getCode());
        executor.callData(callData);
        
        executor.execute();

        updater.commit();


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
 
            String memory = jsonObject.get("memory").getAsString();
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
