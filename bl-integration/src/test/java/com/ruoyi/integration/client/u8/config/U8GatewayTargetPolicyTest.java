package com.ruoyi.integration.client.u8.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.junit.jupiter.api.Test;

class U8GatewayTargetPolicyTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void normalizesAnAllowedRootAndKeepsOnlyRegisteredPostOperations()
    {
        U8GatewayTargetPolicy policy = new U8GatewayTargetPolicy(Set.of("u8.example.test"));
        ObjectNode valid = policy.validate(config("https://U8.EXAMPLE.TEST", "/api/voucher/add"));

        assertEquals("https://u8.example.test", valid.path("baseUrl").asText());
        assertEquals("VOUCHER_ADD", valid.path("operations").get(0).path("code").asText());
    }

    @Test
    void rejectsAnUnapprovedHostOrNonPostOperation()
    {
        U8GatewayTargetPolicy policy = new U8GatewayTargetPolicy(Set.of("u8.example.test"));
        assertThrows(ConfigurationException.class, () -> policy.validate(config("https://other.example.test", "/api/voucher/add")));
        ObjectNode nonPost = config("https://u8.example.test", "/api/voucher/add");
        ((ObjectNode) nonPost.path("operations").get(0)).put("method", "GET");
        assertThrows(ConfigurationException.class, () -> policy.validate(nonPost));
    }

    private ObjectNode config(String baseUrl, String path)
    {
        ObjectNode value = json.createObjectNode().put("connectionName", "U8账户").put("environment", "TEST")
                .put("baseUrl", baseUrl).put("tokenPath", "/system/token").put("tradeIdPath", "/system/tradeid")
                .put("tokenPointer", "/token/id").put("tradeIdPointer", "/trade/id")
                .put("tokenParameterName", "token").put("tradeIdParameterName", "tradeid")
                .put("tokenCacheSeconds", 300).put("connectTimeoutMillis", 5000).put("readTimeoutMillis", 15000);
        value.putArray("operations").addObject().put("code", "VOUCHER_ADD").put("method", "POST").put("path", path).put("enabled", true);
        return value;
    }
}
