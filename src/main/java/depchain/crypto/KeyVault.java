package depchain.crypto;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.HashMap;
import java.util.Map;

import threshsig.Dealer;
import threshsig.GroupKey;
import threshsig.KeyShare;
import threshsig.SigShare;

public class KeyVault {
    
    public static final String PRIVATE_KEYS_DIR = "privateKeys/";
    public static final String PUBLIC_KEYS_DIR = "publicKeys/";
    public static final String THRESHOLD_KEYS_DIR = "thresholdKeys/";
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

    public static void generateAndSaveThresholdKeys(int k, int n, int keyBits) throws Exception {
        Files.createDirectories(Paths.get(THRESHOLD_KEYS_DIR));

        Dealer dealer = new Dealer(keyBits);
        dealer.generateKeys(k, n);

        GroupKey groupKey = dealer.getGroupKey();
        KeyShare[] shares = dealer.getShares();

        saveGroupKey(k, n, groupKey);

        for (int i = 0; i < shares.length; i++) {
            saveThresholdShare(k, n, i, shares[i]);
        }
    }

    public static boolean thresholdKeysExist(int k, int n) {
        if (!Files.exists(Paths.get(groupKeyPath(k, n)))) {
            return false;
        }

        for (int i = 0; i < n; i++) {
            if (!Files.exists(Paths.get(sharePath(k, n, i)))) {
                return false;
            }
        }

        return true;
    }

    public static GroupKey loadGroupKey(int k, int n) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(groupKeyPath(k, n)));

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int loadedK = in.readInt();
            int loadedL = in.readInt();
            GroupKey groupKey = new GroupKey(
                loadedK,
                loadedL,
                0,
                BigInteger.ZERO,
                readBigInteger(in),
                readBigInteger(in)
            );

            return groupKey;
        }
    }

    public static KeyShare[] loadThresholdShares(int k, int n, GroupKey groupKey) throws Exception {
        KeyShare[] shares = new KeyShare[n];
        BigInteger delta = SigShare.factorial(groupKey.getL());

        for (int i = 0; i < n; i++) {
            byte[] bytes = Files.readAllBytes(Paths.get(sharePath(k, n, i)));
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                int id = in.readInt();
                BigInteger secret = readBigInteger(in);
                BigInteger verifier = readBigInteger(in);
                BigInteger groupVerifier = readBigInteger(in);

                KeyShare share = new KeyShare(id, secret, groupKey.getModulus(), delta);
                share.setVerifiers(verifier, groupVerifier);
                shares[i] = share;
            }
        }

        return shares;
    }

    private static void saveGroupKey(int k, int n, GroupKey groupKey) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(k);
            out.writeInt(n);
            writeBigInteger(out, groupKey.getExponent());
            writeBigInteger(out, groupKey.getModulus());
            out.flush();

            Files.write(Paths.get(groupKeyPath(k, n)), bos.toByteArray());
        }
    }

    private static void saveThresholdShare(int k, int n, int index, KeyShare share) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            out.writeInt(share.getId());
            writeBigInteger(out, share.getSecret());
            writeBigInteger(out, share.getVerifier());
            writeBigInteger(out, share.getGroupVerifier());
            out.flush();

            Files.write(Paths.get(sharePath(k, n, index)), bos.toByteArray());
        }
    }

    private static void writeBigInteger(DataOutputStream out, BigInteger number) throws Exception {
        byte[] bytes = number.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static BigInteger readBigInteger(DataInputStream in) throws Exception {
        int length = in.readInt();
        byte[] bytes = in.readNBytes(length);
        return new BigInteger(bytes);
    }

    private static String groupKeyPath(int k, int n) {
        return THRESHOLD_KEYS_DIR + "group-k" + k + "-n" + n + ".key";
    }

    private static String sharePath(int k, int n, int index) {
        return THRESHOLD_KEYS_DIR + "share-k" + k + "-n" + n + "-idx" + index + ".key";
    }
}
