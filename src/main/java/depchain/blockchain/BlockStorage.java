package depchain.blockchain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
 
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class BlockStorage {
    
    private String BLOCKS_DIR = "blocks/";
    private String GENESIS_FILE = "genesis.json";
    private final Gson gson;

    public BlockStorage() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    
        try {
            Files.createDirectories(Paths.get(BLOCKS_DIR));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create blocks directory", e);
        }
    }

    // Used for tests to specify temp dirs
    public BlockStorage(String blocksDir, String genesisFile) {
        this.BLOCKS_DIR = blocksDir;
        this.GENESIS_FILE = genesisFile;

        this.gson = new GsonBuilder().setPrettyPrinting().create();
    
        try {
            Files.createDirectories(Paths.get(BLOCKS_DIR));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create blocks directory", e);
        }
    }

    public void saveBlock(Block block, int blockNumber) {

        String fileName = BLOCKS_DIR + "block_" + blockNumber + ".json";

        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(block, writer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save block " + blockNumber, e);
        }
    }

    public Block loadBlock(int blockNumber) {
        String filename = BLOCKS_DIR + "block_" + blockNumber + ".json";

        try (FileReader reader = new FileReader(filename)) {
            return gson.fromJson(reader, Block.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load block " + blockNumber, e);
        }
    }

    public Block loadGenesis() {

        try (FileReader reader = new FileReader(GENESIS_FILE)) {
            return gson.fromJson(reader, Block.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load genesis.json", e);
        }
        
    }

    public boolean chainExists() {
        return Files.exists(Paths.get(BLOCKS_DIR + "block_0.json"));
    }

    public int getLatestBlockNumber() {
        try {
            return (int) Files.list(Paths.get(BLOCKS_DIR)).filter(p -> p.getFileName().toString().startsWith("block_")).count() - 1;
        } catch (Exception e) {
            return -1;
        }
    }
}
