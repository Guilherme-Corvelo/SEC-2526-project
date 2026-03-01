package depchain.consensus;

/**
 * Base class for HotStuff protocol messages.
 * Subclasses: Propose, Vote, Finish.
 */
import java.io.Serializable;

public abstract class HotStuffMessage implements Serializable {
    protected final long view;
    protected final int senderId;
    
    public HotStuffMessage(long view, int senderId) {
        this.view = view;
        this.senderId = senderId;
    }
    
    public long getView() {
        return view;
    }
    
    public int getSenderId() {
        return senderId;
    }
    
    @Override
    public abstract String toString();
}
