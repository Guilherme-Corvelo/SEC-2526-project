package depchain.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import depchain.Debug;
import depchain.consensus.Message;
import depchain.consensus.Node;
import depchain.consensus.PhaseType;
import depchain.service.ServiceMessage;

public class SerializationTest {
    
    @Test
    void messageSerialization(){
        Node n1 = new Node();
        Message msg1 = new Message(PhaseType.COMMIT, 0, n1);

        Debug.debug("" + msg1.serialize().length);

        Message msg2 = Message.deserialize(msg1.serialize());

        Debug.debug("[Message 1] " + msg1.toString());
        Debug.debug("[Message 2] " + msg2.toString());

        assertTrue(msg1.equals(msg2));
    }
    
    @Test
    void serviceMessageSerialization(){
        ServiceMessage msg1 = new ServiceMessage(0, "Test");

        Debug.debug("" + msg1.serialize().length);

        ServiceMessage msg2 = ServiceMessage.deserialize(msg1.serialize());
        
        Debug.debug("[Message 1] " + msg1.toString());
        Debug.debug("[Message 2] " + msg2.toString());

        assertTrue(msg1.equals(msg2));
    }
}
