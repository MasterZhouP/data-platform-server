package com.ruoyi.integration.client.u8.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class U8GatewayAdminServiceTest
{
    @Test
    void storesOnlySafeFieldsAndEncryptsTheExplicitSecretUpdate()
    {
        U8GatewayCatalog catalog = mock(U8GatewayCatalog.class);
        U8GatewaySecretStore secrets = mock(U8GatewaySecretStore.class);
        U8GatewayTargetPolicy policy = mock(U8GatewayTargetPolicy.class);
        ObjectMapper json = new ObjectMapper();
        ObjectNode request = json.createObjectNode().put("connectionName", "U8公共账户").put("environment", "TEST")
                .put("baseUrl", "https://u8.example.test").put("tokenPath", "/system/token").put("tradeIdPath", "/system/tradeid")
                .put("tokenPointer", "/token/id").put("tradeIdPointer", "/trade/id")
                .put("tokenParameterName", "token").put("tradeIdParameterName", "tradeid")
                .put("tokenCacheSeconds", 300).put("connectTimeoutMillis", 5000).put("readTimeoutMillis", 15000);
        request.putObject("secretParametersUpdate").put("account", "secret-value");
        request.putArray("operations").addObject().put("code", "VOUCHER_ADD").put("method", "POST")
                .put("path", "/api/voucher/add").put("enabled", true);
        when(policy.validate(any(ObjectNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(secrets.store(eq("u8-default"), any(RevisionToken.class), eq(Map.of("account", "secret-value"))))
                .thenReturn("secret-1");
        when(catalog.saveDraft(eq("u8-default"), eq(null), any(ObjectNode.class), eq("secret-1"), any(RevisionToken.class)))
                .thenReturn(new RevisionToken("rev-1"));
        when(catalog.currentSecretRevision("u8-default")).thenThrow(new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "missing"));

        new U8GatewayAdminService(catalog, secrets, policy, json).saveDraft("u8-default", request);

        ArgumentCaptor<ObjectNode> captured = ArgumentCaptor.forClass(ObjectNode.class);
        verify(catalog).saveDraft(eq("u8-default"), eq(null), captured.capture(), eq("secret-1"), any(RevisionToken.class));
        assertFalse(captured.getValue().has("secretParametersUpdate"));
        assertTrue(captured.getValue().path("accountParameterNames").isArray());
    }
}
