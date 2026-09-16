package com.ruoyi.integration.client.u8.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.configuration.security.EncryptedSecret;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.springframework.stereotype.Service;

/** Persists all U8 account parameters as revision-bound AES-GCM ciphertext. */
@Service
public class U8GatewaySecretStore
{
    private final U8GatewayMapper mapper;
    private final SecretCipher cipher;
    private final ObjectMapper json;

    public U8GatewaySecretStore(U8GatewayMapper mapper, SecretCipher cipher, ObjectMapper json)
    {
        this.mapper = mapper;
        this.cipher = cipher;
        this.json = json;
    }

    public String store(String key, RevisionToken revision, Map<String, String> parameters)
    {
        if (parameters == null || parameters.isEmpty())
        {
            throw new ConfigurationException("SECRET_REQUIRED", 400, "请至少填写一个U8账户认证参数");
        }
        char[] plaintext = write(parameters).toCharArray();
        try
        {
            EncryptedSecret encrypted = cipher.encrypt(owner(key), revision.value(), plaintext);
            String secretId = UUID.randomUUID().toString();
            mapper.insertSecret(new U8GatewaySecret(secretId, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext(),
                    owner(key), revision.value()));
            return secretId;
        }
        finally
        {
            Arrays.fill(plaintext, '\0');
        }
    }

    public String rebind(String key, RevisionToken sourceRevision, String sourceSecretId, RevisionToken targetRevision)
    {
        Map<String, String> parameters = read(key, sourceRevision, sourceSecretId);
        try
        {
            return store(key, targetRevision, parameters);
        }
        finally
        {
            parameters.clear();
        }
    }

    public Map<String, String> read(String key, RevisionToken revision, String secretId)
    {
        U8GatewaySecret stored = mapper.findSecret(secretId);
        if (stored == null || !owner(key).equals(stored.ownerKey()) || !revision.value().equals(stored.ownerRevision()))
        {
            throw new ConfigurationException("SECRET_CONTEXT_MISMATCH", 409, "U8账户认证参数无法用于当前修订");
        }
        char[] plaintext = cipher.decrypt(stored.ownerKey(), stored.ownerRevision(),
                new EncryptedSecret(stored.keyId(), stored.nonce(), stored.ciphertext()));
        try
        {
            @SuppressWarnings("unchecked")
            Map<String, String> values = json.readValue(new String(plaintext), LinkedHashMap.class);
            return new LinkedHashMap<>(values);
        }
        catch (Exception malformed)
        {
            throw new ConfigurationException("SECRET_CONTEXT_MISMATCH", 409, "U8账户认证参数无法读取");
        }
        finally
        {
            Arrays.fill(plaintext, '\0');
        }
    }

    private String write(Map<String, String> parameters)
    {
        try
        {
            return json.writeValueAsString(new LinkedHashMap<>(parameters));
        }
        catch (Exception failure)
        {
            throw new ConfigurationException("SECRET_ENCRYPTION_FAILED", 503, "U8账户认证参数无法保护");
        }
    }

    private static String owner(String key)
    {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,99}"))
        {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "U8账户编码格式不正确");
        }
        return "U8_GATEWAY:" + key;
    }
}
