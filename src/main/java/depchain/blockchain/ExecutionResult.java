package depchain.blockchain;

import java.math.BigInteger;
import java.io.Serializable;
import java.util.Objects;

public class ExecutionResult implements Serializable {
    
    public final boolean success;
    public final long gasUsed;
    public final BigInteger returnValue;

    public ExecutionResult(boolean success, long gasUsed, BigInteger returnValue) {
        this.success = success;
        this.gasUsed = gasUsed;
        this.returnValue = returnValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ExecutionResult other)) {
            return false;
        }
        return this.success == other.success
            && this.gasUsed == other.gasUsed
            && Objects.equals(this.returnValue, other.returnValue);
    }

    @Override
    public String toString() {
        return "ExecutionResult[success=" + success +
                ", gasUsed=" + gasUsed +
                ", returnValue=" + returnValue + "]";
    }
}
