package com.ruoyi.integration.client.oa.config;

import java.util.Arrays;
import java.util.UUID;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.configuration.security.EncryptedSecret;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.springframework.stereotype.Service;

/** Persists an OA REST password only as AES-GCM ciphertext bound to a single revision. */
@Service
public class OaRestSecretStore {
    private final OaRestMapper mapper;
    private final SecretCipher cipher;

    public OaRestSecretStore(OaRestMapper mapper, SecretCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    public String store(String connectionKey, RevisionToken revision, char[] password) {
        EncryptedSecret encrypted = cipher.encrypt(owner(connectionKey), revision.value(), password);
        String secretId = UUID.randomUUID().toString();
        mapper.insertSecret(new OaRestSecret(secretId, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext(),
                owner(connectionKey), revision.value()));
        return secretId;
    }

    public String rebind(String connectionKey, RevisionToken sourceRevision, String sourceSecretId, RevisionToken targetRevision) {
        char[] password = read(connectionKey, sourceRevision, sourceSecretId);
        try {
            return store(connectionKey, targetRevision, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public char[] read(String connectionKey, RevisionToken revision, String secretId) {
        OaRestSecret stored = mapper.findSecret(secretId);
        if (stored == null || !owner(connectionKey).equals(stored.ownerKey()) || !revision.value().equals(stored.ownerRevision())) {
            throw new ConfigurationException("SECRET_CONTEXT_MISMATCH", 409, "OA REST 密码无法用于当前修订");
        }
        return cipher.decrypt(stored.ownerKey(), stored.ownerRevision(),
                new EncryptedSecret(stored.keyId(), stored.nonce(), stored.ciphertext()));
    }

    private static String owner(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,99}")) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "OA REST 连接编码格式不正确");
        }
        return "OA_REST:" + key;
    }
}
