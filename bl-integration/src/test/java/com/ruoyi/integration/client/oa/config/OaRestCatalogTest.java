package com.ruoyi.integration.client.oa.config;

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

class OaRestCatalogTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void savesAnImmutableDraftWithoutChangingTheActiveOaRevision() {
        OaRestMapper mapper = mock(OaRestMapper.class);
        OaRestConnection row = new OaRestConnection("oa-default", "OA 正式", true, "active-1", null, 4L, "target-1");
        when(mapper.findConnection("oa-default")).thenReturn(row);
        when(mapper.findRevision("active-1")).thenReturn(new OaRestRevision("active-1", "oa-default", "target-1", "PROD",
                "https://oa-old.internal", "rest-old", "member-old", 5000, 15000, "secret-old", "checksum"));
        when(mapper.updateDraftPointer(eq("oa-default"), any(), eq(4L))).thenReturn(1);
        ObjectNode fields = json.createObjectNode().put("connectionName", "OA 正式").put("environment", "PROD")
                .put("baseUrl", "https://oa-new.internal").put("restUsername", "rest-new").put("loginName", "member-new")
                .put("connectTimeoutMs", 5000).put("readTimeoutMs", 15000);

        RevisionToken saved = new OaRestCatalog(mapper, json).saveDraft("oa-default", null, fields, "secret-new");

        assertEquals("active-1", new OaRestCatalog(mapper, json).active("oa-default").revisionId());
        verify(mapper).insertRevision(org.mockito.ArgumentMatchers.argThat(revision ->
                revision.revisionId().equals(saved.value()) && revision.secretId().equals("secret-new")
                        && revision.targetId().equals("target-1")));
    }
}
