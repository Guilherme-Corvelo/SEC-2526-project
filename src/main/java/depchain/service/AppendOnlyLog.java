package depchain.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple in-memory append-only log for Phase 1.
 * 
 * Maintains a list of committed entries in the order they were committed.
 * In Phase 2, this will be backed by persistent storage.
 */
public class AppendOnlyLog {
    private final List<String> entries;
    
    public AppendOnlyLog() {
        this.entries = new ArrayList<>();
    }
    
    /**
     * Append a new entry to the log.
     * Returns the index of the appended entry.
     */
    public synchronized int append(String entry) {
        entries.add(entry);
        return entries.size() - 1;
    }
    
    /**
     * Get an entry by index.
     */
    public synchronized String get(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }
    
    /**
     * Get all entries in the log.
     */
    public synchronized List<String> getAll() {
        return new ArrayList<>(entries);
    }
    
    /**
     * Get the current size of the log.
     */
    public synchronized int size() {
        return entries.size();
    }
    
    /**
     * Check if log contains an entry.
     */
    public synchronized boolean contains(String entry) {
        return entries.contains(entry);
    }
}
