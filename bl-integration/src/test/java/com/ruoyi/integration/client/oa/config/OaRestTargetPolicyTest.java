package com.ruoyi.integration.client.oa.config;

import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OaRestTargetPolicyTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyAnAllowedServiceRootAndNormalizesItsTrailingSlash() {
        ObjectNode input = json.createObjectNode()
                .put("connectionName", "OA 测试")
                .put("environment", "TEST")
                .put("baseUrl", "https://oa-test.internal:8443/")
                .put("restUsername", "rest-test")
                .put("loginName", "member-test")
                .put("connectTimeoutMs", 5000)
                .put("readTimeoutMs", 15000);

        ObjectNode valid = new OaRestTargetPolicy(Set.of("oa-test.internal")).validate(input);

        assertEquals("https://oa-test.internal:8443", valid.path("baseUrl").asText());
    }

    @Test
    void rejectsCredentialsAndTokenPathsInTheConfiguredBaseUrl() {
        ObjectNode input = json.createObjectNode()
                .put("connectionName", "OA 测试")
                .put("environment", "TEST")
                .put("baseUrl", "https://rest:secret@oa-test.internal/seeyon/rest/token")
                .put("restUsername", "rest-test")
                .put("loginName", "member-test")
                .put("connectTimeoutMs", 5000)
                .put("readTimeoutMs", 15000);

        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new OaRestTargetPolicy(Set.of("oa-test.internal")).validate(input));

        assertEquals("OA_TARGET_REJECTED", error.code());
    }
}
