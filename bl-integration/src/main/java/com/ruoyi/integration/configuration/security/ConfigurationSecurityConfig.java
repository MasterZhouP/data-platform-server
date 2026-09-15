package com.ruoyi.integration.configuration.security;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConfigurationSecurityProperties.class)
public class ConfigurationSecurityConfig {
    @Bean
    public SecretCipher configurationSecretCipher(ConfigurationSecurityProperties properties) {
        KeyProvider provider = keyId -> decode(properties.getKeys().get(keyId));
        return new SecretCipher(provider, properties.getActiveKeyId());
    }

    private static SecretKey decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] material = Base64.getDecoder().decode(encoded);
            if (material.length != 32) return null;
            return new SecretKeySpec(material, "AES");
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
