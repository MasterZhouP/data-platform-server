package com.ruoyi.integration.client.oa.config;

/** Encrypted OA REST password record. Fields are cipher material, not plaintext. */
public record OaRestSecret(String secretId, String keyId, byte[] nonce, byte[] ciphertext,
                           String ownerKey, String ownerRevision) {
}
