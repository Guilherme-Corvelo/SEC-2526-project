package depchain.blockchain;

import java.math.BigInteger;

public class ExecutionResult {
    
    public final boolean success;
    public final long gasUsed;
    public final BigInteger returnValue;

    public ExecutionResult(boolean success, long gasUsed, BigInteger returnValue) {
        this.success = success;
        this.gasUsed = gasUsed;
        this.returnValue = returnValue;
    }

}
