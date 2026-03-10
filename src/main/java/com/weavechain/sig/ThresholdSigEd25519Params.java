package com.weavechain.sig;

import com.weavechain.curve25519.Scalar;

import java.util.List;

public class ThresholdSigEd25519Params {

    private final Scalar privateKey;

    private final byte[] publicKey;

    private final List<Scalar> privateShares;

    private List<Scalar> sig;

    public ThresholdSigEd25519Params(Scalar privateKey, byte[] publicKey, List<Scalar> privateShares, List<Scalar> sig) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.privateShares = privateShares;
        this.sig = sig;
    }

    public Scalar getPrivateKey() {
        return privateKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public List<Scalar> getPrivateShares() {
        return privateShares;
    }

    public List<Scalar> getSig() {
        return sig;
    }

    public void setSig(List<Scalar> sig) {
        this.sig = sig;
    }
}
