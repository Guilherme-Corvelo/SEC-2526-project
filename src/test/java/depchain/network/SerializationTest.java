package depchain.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import depchain.Debug;
import depchain.API.Request;
import depchain.consensus.Message;
import depchain.consensus.Node;
import depchain.consensus.Type;

public class SerializationTest {
    
    @Test
    void messageSerialization(){
        Node n1 = new Node();
        Message msg1 = new Message(Type.COMMIT, 0, n1, 0);

        Debug.debug("" + msg1.serialize().length);

        Message msg2 = Message.deserialize(msg1.serialize());

        Debug.debug("[Message 1] " + msg1.toString());
        Debug.debug("[Message 2] " + msg2.toString());

        assertTrue(msg1.equals(msg2));
    }
    
    @Test
    void serviceMessageSerialization(){
        Request msg1 = new Request("Test");

        Debug.debug("" + msg1.serialize().length);

        Request msg2 = Request.deserialize(msg1.serialize());
        
        Debug.debug("[Message 1] " + msg1.toString());
        Debug.debug("[Message 2] " + msg2.toString());

        assertTrue(msg1.equals(msg2));
    }
}
