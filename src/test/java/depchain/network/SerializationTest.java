package depchain.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import depchain.Debug;
import depchain.API.Request;
import depchain.blockchain.Transaction;
import depchain.consensus.Message;
import depchain.consensus.Node;
import depchain.consensus.Type;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

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
    void serviceMessageSerialization() throws Exception{
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();

        Transaction transaction = new Transaction(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            null,
            10,
            0,
            1,
            21000,
            kp.getPublic()
        );
        transaction.sign(kp.getPrivate());
        Request msg1 = new Request(transaction);

        Debug.debug("" + msg1.serialize().length);

        Request msg2 = Request.deserialize(msg1.serialize());
        
        Debug.debug("[Message 1] " + msg1.toString());
        Debug.debug("[Message 2] " + msg2.toString());

        assertTrue(msg1.equals(msg2));
    }
}
