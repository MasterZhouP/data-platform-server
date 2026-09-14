package com.ruoyi.integration.client.oa.config;

import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRestAdminServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyTheWhitelistedAccountFieldsAndEncryptsAnExplicitPasswordUpdate() {
        OaRestCatalog catalog = mock(OaRestCatalog.class);
        OaRestSecretStore secrets = mock(OaRestSecretStore.class);
        OaRestTargetPolicy policy = new OaRestTargetPolicy(Set.of("oa-test.internal"));
        ObjectNode request = json.createObjectNode()
                .put("connectionName", "OA 测试")
                .put("environment", "TEST")
                .put("baseUrl", "https://oa-test.internal")
                .put("restUsername", "rest-test")
                .put("loginName", "member-test")
                .put("connectTimeoutMs", 5000)
                .put("readTimeoutMs", 15000)
                .put("passwordUpdate", "test-only-password");
        when(secrets.store(eq("oa-default"), any(), any())).thenReturn("secret-1");
        when(catalog.saveDraft(eq("oa-default"), eq(null), any(), eq("secret-1"), any()))
                .thenAnswer(call -> call.getArgument(4));

        RevisionToken saved = new OaRestAdminService(catalog, secrets, policy).saveDraft("oa-default", request);

        assertEquals(36, saved.value().length());
        verify(secrets).store(eq("oa-default"), any(), any());
    }
}
