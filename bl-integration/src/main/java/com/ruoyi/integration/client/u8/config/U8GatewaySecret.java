package com.ruoyi.integration.client.u8.config;

/** Encrypted U8 account parameter map; plaintext never belongs in this record. */
public record U8GatewaySecret(String secretId, String keyId, byte[] nonce, byte[] ciphertext,
        String ownerKey, String ownerRevision)
{
}
