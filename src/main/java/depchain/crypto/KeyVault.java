package depchain.crypto;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.HashMap;
import java.util.Map;

public class KeyVault {
    
    public static final String PRIVATE_KEYS_DIR = "privateKeys/";
    public static final String PUBLIC_KEYS_DIR = "publicKeys/";
    public static final int KEY_SIZE = 2048;
    public static final String ALGORITHM = "RSA";

    public static final int NUM_CLIENTS = 3;
    public static final int NUM_SERVERS = 4;

    public static void main(String[] args) throws Exception {
        System.out.println("Generating keys for DepChain...");
        System.out.println("  Clients: " + NUM_CLIENTS);
        System.out.println("  Servers: " + NUM_SERVERS);

        System.out.println("  Public key dir: " + PUBLIC_KEYS_DIR);
        System.out.println("  Private key dir: " + PRIVATE_KEYS_DIR);
        System.out.println();

        generateAndSave();

        System.out.println("Done.\nPublic Keys saved to: " + PUBLIC_KEYS_DIR);
        System.out.println();
        System.out.println("Files created:");
        Files.list(Paths.get(PUBLIC_KEYS_DIR))
             .sorted()
             .forEach(p -> System.out.println("  " + p.getFileName()));

        System.out.println("\nPrivate Keys saved to: " + PRIVATE_KEYS_DIR);
        System.out.println();
        System.out.println("Files created:");
        Files.list(Paths.get(PRIVATE_KEYS_DIR))
             .sorted()
             .forEach(p -> System.out.println("  " + p.getFileName()));

    }

    public static void generateAndSave() throws Exception {
        Files.createDirectories(Paths.get(PUBLIC_KEYS_DIR));
        Files.createDirectories(Paths.get(PRIVATE_KEYS_DIR));   

        KeyPairGenerator gen = KeyPairGenerator.getInstance(ALGORITHM);
        gen.initialize(KEY_SIZE);

        for (int i = 0; i < NUM_CLIENTS + NUM_SERVERS; i++) {
            KeyPair pair = gen.generateKeyPair();
            
            if (i < NUM_CLIENTS) {
                savePrivateKey("client" + i, pair.getPrivate());
                savePublicKey("client" + i, pair.getPublic());
                System.out.println("  Generated client" + i + " keys");
            } else {
                savePrivateKey("server" + i, pair.getPrivate());
                savePublicKey("server" + i, pair.getPublic());
                System.out.println("  Generated server" + i + " keys");
            }
            
        }

    }

    private static void savePrivateKey(String name, PrivateKey key) throws Exception {
        Files.write(Paths.get(PRIVATE_KEYS_DIR + name + ".priv"), key.getEncoded());
    }

    private static void savePublicKey(String name, PublicKey key) throws Exception {
        Files.write(Paths.get(PUBLIC_KEYS_DIR + name + ".pub"), key.getEncoded());
    }

    public static boolean keysExist() throws Exception {

        for (int i = 0; i < NUM_CLIENTS + NUM_SERVERS; i++) {
            
            if (i <  NUM_CLIENTS) {
                if (!(Files.exists(Paths.get(PRIVATE_KEYS_DIR + "client" + i + ".priv")) &&
                    Files.exists(Paths.get(PUBLIC_KEYS_DIR + "client" + i + ".pub")))) {
                        return false;
                    }
            }
            else {
                if (!(Files.exists(Paths.get(PRIVATE_KEYS_DIR + "server" + i + ".priv")) &&
                    Files.exists(Paths.get(PUBLIC_KEYS_DIR + "server" + i + ".pub")))) {
                        return false;
                    }
            }
        }

        return true;
    }

    public static PrivateKey loadPrivateKey(String name) throws Exception {

        byte[] keyBytes = Files.readAllBytes(Paths.get(PRIVATE_KEYS_DIR + name + ".priv")); 
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);

        return factory.generatePrivate(spec);
    }

    public static PublicKey loadPublicKey(String name) throws Exception {

        byte[] keyBytes = Files.readAllBytes(Paths.get(PUBLIC_KEYS_DIR + name + ".pub")); 
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);

        return factory.generatePublic(spec);
    }

    public static Map<Integer, PublicKey> loadAllPublicKeys() throws Exception {
        Map<Integer, PublicKey> keys = new HashMap<>();

        for (int i = 0; i <  NUM_CLIENTS + NUM_SERVERS; i++) {
            
            if (i < NUM_CLIENTS) {
                keys.put(i, loadPublicKey("client" + i));
            }
            else {
                keys.put(i, loadPublicKey("server" + i));
            }
        }

        return keys;
    }

    public static PublicKey recreatePublicKey(byte[] publicKeyBytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);

        return factory.generatePublic(spec);
    }
}
