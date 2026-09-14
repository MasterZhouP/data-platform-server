package com.ruoyi.integration.configuration.security;

/** Ciphertext envelope. The arrays are defensively copied on every boundary. */
public record EncryptedSecret(String keyId, byte[] nonce, byte[] ciphertext) {
    public EncryptedSecret {
        nonce = nonce == null ? null : nonce.clone();
        ciphertext = ciphertext == null ? null : ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext == null ? null : ciphertext.clone();
    }
}
