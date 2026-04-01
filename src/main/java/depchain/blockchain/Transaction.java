package depchain.blockchain;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Objects;

import org.bouncycastle.jce.exception.ExtCertPathValidatorException;

import depchain.crypto.KeyVault;

public class Transaction implements Serializable{

    private final String from;
    private final String to;
    private final String input;
    private final long value;
    private final long nonce;
    private final long gasPrice;
    private final long gasLimit;

    private byte[] signature;

    private final byte[] senderPublicKeyBytes;

    public Transaction(String from, String to, String input, long value, long nonce,
                       long gasPrice, long gasLimit, PublicKey publicKey) {

        this.from = from;
        this.to = to;
        this.input = input;
        this.value = value;
        this.nonce = nonce;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.senderPublicKeyBytes = publicKey.getEncoded();
    }


    public void sign(PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(getSignableBytes());
        this.signature = sig.sign();
    }

    private byte[] getSignableBytes() {
        String data = from + "|" + (to != null ? to : "null") + "|" + (input != null ? input : "") + "|" +
                      value + "|" + nonce + "|" + gasPrice + "|" + gasLimit;

        return data.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verifySignature() {
        
        if (signature == null || senderPublicKeyBytes == null) {
            return false;
        }

        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(KeyVault.recreatePublicKey(senderPublicKeyBytes));
            sig.update(getSignableBytes());
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isContractCall() {
        return to != null && input != null && !input.isEmpty() && value == 0;
    }

    public boolean isDepCoinTransfer() {
        return to != null && value > 0 && (input == null || input.isEmpty());
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getInput() {
        return input;
    }

    public long getValue() {
        return value;
    }

    public long getNonce() {
        return nonce;
    }

    public long getGasPrice() {
        return gasPrice;
    }

    public long getGasLimit() {
        return gasLimit;
    }

    public byte[] getSignature() {
        return signature;
    }

    public PublicKey getSenderPublicKey() {
        try {
            return KeyVault.recreatePublicKey(senderPublicKeyBytes);
        } catch (Exception e) {
            //
        }

        return null;
    }

    //For Test
    public void setSignature(byte[] newSignature) {
        this.signature = newSignature;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Transaction other)) {
            return false;
        }

        return Objects.equals(from, other.from) &&
            Objects.equals(to, other.to) &&
            Objects.equals(input, other.input) &&
            value == other.value &&
            nonce == other.nonce &&
            gasPrice == other.gasPrice &&
            gasLimit == other.gasLimit &&
            Arrays.equals(signature, other.signature) &&
            Arrays.equals(senderPublicKeyBytes, other.senderPublicKeyBytes);
    }

}
