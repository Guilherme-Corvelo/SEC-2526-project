package depchain.service;

import depchain.consensus.Block;
import depchain.consensus.ConsensusListener;
import java.util.List;


/**
 * Simple blockchain service for Phase 1.
 * 
 * This service:
 * - Receives consensus decisions from ByzantineHotStuffNode
 * - Appends committed blocks to the append-only log
 * - Provides read access to committed entries
 * 
 * In Phase 2, this will handle:
 * - Proper client request/reply semantics
 * - Transaction validation
 * - Persistent state machine snapshots
 */
public class BlockchainService implements ConsensusListener {
    private final AppendOnlyLog log;
    
    public BlockchainService() {
        this.log = new AppendOnlyLog();
    }
    
    /**
     * Called by consensus layer when a block is committed.
     * Appends the block data to the append-only log.
     * 
     * This method is invoked by ByzantineHotStuffNode when onCommit is called.
     */
    @Override
    public void onCommit(Block block) {
        String data = block.getData();
        int index = log.append(data);
        System.out.println("[BlockchainService] Committed entry " + index + ": \"" + data + "\"");
    }
    
    /**
     * Get an entry from the committed log by index.
     */
    public String getEntry(int index) {
        return log.get(index);
    }
    
    /**
     * Get all committed entries.
     */
    public List<String> getAllEntries() {
        return log.getAll();
    }
    
    /**
     * Get the total number of committed entries.
     */
    public int getLogSize() {
        return log.size();
    }
}
