package com.ruoyi.integration.client.oa.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.oa.OaClientException;
import com.ruoyi.integration.client.oa.config.OaRestCatalog;
import com.ruoyi.integration.client.oa.config.OaRestRevision;
import com.ruoyi.integration.client.oa.config.OaRestSecretStore;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRestRuntimeServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void testsDraftInIsolationAndOnlyActivatesAWorkingSession() {
        OaRestCatalog catalog = mock(OaRestCatalog.class);
        OaRestSecretStore secrets = mock(OaRestSecretStore.class);
        OaRestSessionFactory sessions = mock(OaRestSessionFactory.class);
        OaRestSessionRegistry registry = mock(OaRestSessionRegistry.class);
        OaRestRevision revision = revision("draft-3");
        OaRestSession session = mock(OaRestSession.class);
        when(session.revisionId()).thenReturn("draft-3");
        when(session.connectionKey()).thenReturn("oa-default");
        when(catalog.draft("oa-default", new RevisionToken("draft-3"))).thenReturn(revision);
        when(secrets.read("oa-default", new RevisionToken("draft-3"), "secret-3")).thenReturn("test-password".toCharArray());
        when(sessions.create(eq(revision), any(char[].class))).thenReturn(session);
        when(catalog.activatePointer("oa-default", new RevisionToken("draft-3"), "active-2")).thenReturn(true);

        OaRestRuntimeService runtime = new OaRestRuntimeService(catalog, secrets, sessions, registry, json);
        ObjectNode tested = runtime.testDraft("oa-default", "draft-3");
        ObjectNode activated = runtime.activate("oa-default", "draft-3", "active-2");

        assertEquals("SUCCESS", tested.path("status").asText());
        assertEquals("draft-3", activated.path("activeRevisionId").asText());
        verify(catalog, times(2)).recordTest(eq("oa-default"), eq(new RevisionToken("draft-3")), any(ObjectNode.class));
        verify(registry).activate(session);
        verify(session).close(); // draft test is always closed; activated session remains in the registry.
    }

    @Test
    void refusesActivationWhenTheCandidateAuthenticationFailsAndKeepsTheCurrentSession() {
        OaRestCatalog catalog = mock(OaRestCatalog.class);
        OaRestSecretStore secrets = mock(OaRestSecretStore.class);
        OaRestSessionFactory sessions = mock(OaRestSessionFactory.class);
        OaRestSessionRegistry registry = mock(OaRestSessionRegistry.class);
        OaRestRevision revision = revision("draft-4");
        OaRestSession session = mock(OaRestSession.class);
        when(session.revisionId()).thenReturn("draft-4");
        when(session.connectionKey()).thenReturn("oa-default");
        when(catalog.draft("oa-default", new RevisionToken("draft-4"))).thenReturn(revision);
        when(secrets.read("oa-default", new RevisionToken("draft-4"), "secret-3")).thenReturn("wrong-password".toCharArray());
        when(sessions.create(eq(revision), any(char[].class))).thenReturn(session);
        org.mockito.Mockito.doThrow(OaClientException.nonRetryable("OA_AUTH_REJECTED", "拒绝"))
                .when(session).authenticate();

        OaRestRuntimeService runtime = new OaRestRuntimeService(catalog, secrets, sessions, registry, json);
        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> runtime.activate("oa-default", "draft-4", "active-2"));

        assertEquals("OA_AUTH_TEST_FAILED", error.code());
        verify(catalog, never()).activatePointer(any(), any(), any());
        verify(registry, never()).activate(any());
        verify(session).close();
    }

    private static OaRestRevision revision(String revisionId) {
        return new OaRestRevision(revisionId, "oa-default", "target-1", "TEST", "https://oa-test.internal",
                "rest-test", "member-test", 5000, 15000, "secret-3", "checksum");
    }
}
