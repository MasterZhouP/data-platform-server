package com.ruoyi.integration.configuration.security;

import javax.crypto.SecretKey;

/** Resolves encryption keys from the deployment-owned key store. */
@FunctionalInterface
public interface KeyProvider {
    SecretKey key(String keyId);
}
