package com.ruoyi.integration.datasource;

import java.util.HexFormat;
import javax.crypto.spec.SecretKeySpec;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.security.KeyProvider;
import com.ruoyi.integration.configuration.security.SecretCipher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasourceSecretTest {
    @Test
    void encryptsWithRevisionBoundAadAndUsesANewNonceEveryTime() {
        KeyProvider keys = id -> new SecretKeySpec(new byte[32], "AES");
        SecretCipher cipher = new SecretCipher(keys, "test-key");

        var encrypted = cipher.encrypt("DATASOURCE:u8", "7", "test-only-secret".toCharArray());

        assertEquals("test-key", encrypted.keyId());
        assertArrayEquals("test-only-secret".toCharArray(), cipher.decrypt("DATASOURCE:u8", "7", encrypted));
        assertThrows(ConfigurationException.class, () -> cipher.decrypt("OA_REST:u8", "7", encrypted));
        assertNotEquals(HexFormat.of().formatHex(encrypted.nonce()), HexFormat.of().formatHex(
                cipher.encrypt("DATASOURCE:u8", "7", "test-only-secret".toCharArray()).nonce()));
    }
}
