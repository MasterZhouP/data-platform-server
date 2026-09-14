package com.ruoyi.integration.client.oa.config;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OaRestReadServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void projectsOnlySafeOaConnectionStateForTheConsole() {
        OaRestCatalog catalog = mock(OaRestCatalog.class);
        OaRestConnection connection = new OaRestConnection("oa-default", "OA 测试账户", true,
                "active-1", "draft-2", 5L, "target-1");
        OaRestRevision draft = revision("draft-2", "https://oa-test.internal", "secret-draft");
        OaRestRevision active = revision("active-1", "https://oa.internal", "secret-active");
        ObjectNode lastTest = json.createObjectNode().put("status", "SUCCESS").put("checkedAt", "2026-09-14T09:00:00Z");
        when(catalog.list()).thenReturn(List.of(connection));
        when(catalog.row("oa-default")).thenReturn(connection);
        when(catalog.draft("oa-default", null)).thenReturn(draft);
        when(catalog.active("oa-default")).thenReturn(active);
        when(catalog.lastTest("oa-default", "draft-2")).thenReturn(lastTest);

        OaRestReadService service = new OaRestReadService(catalog, json);

        assertEquals("oa-default", service.list().get(0).path("connectionKey").asText());
        ObjectNode detail = service.detail("oa-default");
        assertEquals("https://oa-test.internal", detail.path("draftConfig").path("baseUrl").asText());
        assertEquals("SUCCESS", detail.path("lastTest").path("status").asText());
        assertFalse(detail.toString().contains("secret-draft"));
        assertFalse(detail.toString().contains("password"));
    }

    private static OaRestRevision revision(String revisionId, String baseUrl, String secretId) {
        return new OaRestRevision(revisionId, "oa-default", "target-1", "TEST", baseUrl,
                "rest-test", "member-test", 5000, 15000, secretId, "checksum");
    }
}
