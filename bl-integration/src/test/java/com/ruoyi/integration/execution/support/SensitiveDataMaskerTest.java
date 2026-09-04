package com.ruoyi.integration.execution.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest
{
    private final SensitiveDataMasker masker = new SensitiveDataMasker(160);

    @Test
    void masksNestedJsonAuthenticationValues()
    {
        String masked = masker.mask("{\"user\":\"demo\",\"password\":\"real-password\","
                + "\"nested\":{\"appKey\":\"real-key\",\"TOKEN\":\"real-token\"}}");

        assertTrue(masked.contains("demo"));
        assertFalse(masked.contains("real-password"));
        assertFalse(masked.contains("real-key"));
        assertFalse(masked.contains("real-token"));
        assertTrue(masked.contains("***"));
    }

    @Test
    void masksPlainTextAndLimitsStoredLength()
    {
        String masked = masker.mask("Authorization: Bearer abcdef password=secret-value " + "x".repeat(300));

        assertFalse(masked.contains("abcdef"));
        assertFalse(masked.contains("secret-value"));
        assertTrue(masked.length() <= 160);
        assertTrue(masked.endsWith("...[truncated]"));
    }

    @Test
    void masksCommonTokenAndBasicAuthorizationVariants()
    {
        String json = masker.mask("{\"access_token\":\"access-value\",\"clientSecret\":\"client-value\"}");
        String text = masker.mask("Authorization: Basic dXNlcjpwYXNz");

        assertFalse(json.contains("access-value"));
        assertFalse(json.contains("client-value"));
        assertFalse(text.contains("dXNlcjpwYXNz"));
    }

    @Test
    void errorMessagesUseTheDatabaseColumnLimit()
    {
        SensitiveDataMasker largePayloadMasker = new SensitiveDataMasker(65535);

        assertTrue(largePayloadMasker.maskError("x".repeat(5000)).length() <= 2000);
    }
}
