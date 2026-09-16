package com.ruoyi.integration.client.u8.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.configuration.security.KeyProvider;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.junit.jupiter.api.Test;

class U8GatewaySecretStoreTest
{
    @Test
    void storesAndReadsRevisionBoundAccountParametersAsEncryptedData()
    {
        U8GatewayMapper mapper = mock(U8GatewayMapper.class);
        U8GatewaySecretStore store = new U8GatewaySecretStore(mapper, cipher(), new com.fasterxml.jackson.databind.ObjectMapper());
        String secretId = store.store("u8-default", new RevisionToken("rev-1"), Map.of("account", "value"));
        ArgumentCaptor<U8GatewaySecret> captured = ArgumentCaptor.forClass(U8GatewaySecret.class);
        verify(mapper).insertSecret(captured.capture());
        when(mapper.findSecret(eq(secretId))).thenReturn(captured.getValue());

        assertEquals(Map.of("account", "value"), store.read("u8-default", new RevisionToken("rev-1"), secretId));
        assertThrows(ConfigurationException.class,
                () -> store.read("other", new RevisionToken("rev-1"), secretId));
    }

    private SecretCipher cipher()
    {
        KeyProvider provider = keyId -> "test-key".equals(keyId) ? new SecretKeySpec(new byte[32], "AES") : null;
        return new SecretCipher(provider, "test-key");
    }
}
