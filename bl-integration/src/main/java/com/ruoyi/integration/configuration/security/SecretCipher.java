package com.ruoyi.integration.configuration.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import com.ruoyi.integration.configuration.ConfigurationException;

/** AES-GCM secret encryption with owner and revision included as authenticated data. */
public final class SecretCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final KeyProvider keys;
    private final String activeKeyId;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(KeyProvider keys, String activeKeyId) {
        this.keys = keys;
        this.activeKeyId = activeKeyId;
    }

    public EncryptedSecret encrypt(String owner, String revision, char[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new ConfigurationException("SECRET_REQUIRED", 400, "请输入密码后再保存");
        }
        byte[] bytes = null;
        try {
            SecretKey key = requireKey(activeKeyId);
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(plaintext));
            bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(owner, revision));
            return new EncryptedSecret(activeKeyId, nonce, cipher.doFinal(bytes));
        } catch (GeneralSecurityException ex) {
            throw new ConfigurationException("SECRET_ENCRYPTION_FAILED", 503, "无法保护配置密码");
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    public char[] decrypt(String owner, String revision, EncryptedSecret encrypted) {
        if (encrypted == null) {
            throw new ConfigurationException("SECRET_REQUIRED", 409, "尚未配置密码");
        }
        byte[] plaintext = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(encrypted.keyId()),
                    new GCMParameterSpec(TAG_BITS, encrypted.nonce()));
            cipher.updateAAD(aad(owner, revision));
            plaintext = cipher.doFinal(encrypted.ciphertext());
            CharBuffer chars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plaintext));
            char[] result = new char[chars.remaining()];
            chars.get(result);
            return result;
        } catch (GeneralSecurityException ex) {
            throw new ConfigurationException("SECRET_CONTEXT_MISMATCH", 409, "配置密码无法用于当前修订");
        } finally {
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private SecretKey requireKey(String keyId) {
        if (keys == null || keyId == null || keyId.isBlank()) {
            throw new ConfigurationException("CONFIGURATION_KEY_UNAVAILABLE", 503, "未配置加密密钥");
        }
        SecretKey key = keys.key(keyId);
        if (key == null) {
            throw new ConfigurationException("CONFIGURATION_KEY_UNAVAILABLE", 503, "未配置加密密钥");
        }
        return key;
    }

    private static byte[] aad(String owner, String revision) {
        if (owner == null || owner.isBlank() || revision == null || revision.isBlank()) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "秘密归属信息不完整");
        }
        return (owner + "\n" + revision).getBytes(StandardCharsets.UTF_8);
    }
}
