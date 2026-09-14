package com.ruoyi.integration.datasource.catalog;

import java.util.Arrays;
import java.util.UUID;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.configuration.security.EncryptedSecret;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.springframework.stereotype.Service;

/** Stores encrypted datasource passwords and re-encrypts them for each immutable revision. */
@Service
public class DatasourceSecretStore {
    private final DatasourceMapper mapper;
    private final SecretCipher cipher;

    public DatasourceSecretStore(DatasourceMapper mapper, SecretCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    public String store(String datasourceKey, RevisionToken revision, char[] password) {
        String owner = owner(datasourceKey);
        EncryptedSecret encrypted = cipher.encrypt(owner, revision.value(), password);
        String secretId = UUID.randomUUID().toString();
        mapper.insertSecret(new DatasourceSecretRow(secretId, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext(), owner, revision.value()));
        return secretId;
    }

    public String rebind(String datasourceKey, RevisionToken sourceRevision, String sourceSecretId, RevisionToken targetRevision) {
        char[] password = read(datasourceKey, sourceRevision, sourceSecretId);
        try {
            return store(datasourceKey, targetRevision, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public char[] read(String datasourceKey, RevisionToken revision, String secretId) {
        DatasourceSecretRow stored = mapper.findSecret(secretId);
        if (stored == null || !owner(datasourceKey).equals(stored.ownerKey()) || !revision.value().equals(stored.ownerRevision())) {
            throw new ConfigurationException("SECRET_CONTEXT_MISMATCH", 409, "配置密码无法用于当前修订");
        }
        return cipher.decrypt(stored.ownerKey(), stored.ownerRevision(),
                new EncryptedSecret(stored.keyId(), stored.nonce(), stored.ciphertext()));
    }

    private static String owner(String datasourceKey) {
        if (datasourceKey == null || !datasourceKey.matches("[a-z][a-z0-9_-]{0,99}")) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "数据源编码格式不正确");
        }
        return "DATASOURCE:" + datasourceKey;
    }
}
