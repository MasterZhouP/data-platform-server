package com.ruoyi.integration.datasource.catalog;

/** Internal ciphertext storage row; neither the ciphertext nor its key id leave the service layer. */
public record DatasourceSecretRow(String secretId, String keyId, byte[] nonce, byte[] ciphertext,
                                  String ownerKey, String ownerRevision) {
    public DatasourceSecretRow {
        nonce = nonce == null ? null : nonce.clone();
        ciphertext = ciphertext == null ? null : ciphertext.clone();
    }

    @Override public byte[] nonce() { return nonce == null ? null : nonce.clone(); }
    @Override public byte[] ciphertext() { return ciphertext == null ? null : ciphertext.clone(); }
}
