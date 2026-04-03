package depchain.blockchain;

import java.util.List;

class ProcessedBlockOutcome {
    final Block block;
    final List<ExecutionResult> executionResults;

    ProcessedBlockOutcome(Block block, List<ExecutionResult> executionResults) {
        this.block = block;
        this.executionResults = executionResults;
    }
}
