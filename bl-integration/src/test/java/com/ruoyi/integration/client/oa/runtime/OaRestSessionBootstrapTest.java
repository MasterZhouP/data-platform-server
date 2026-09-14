package com.ruoyi.integration.client.oa.runtime;

import java.util.List;
import com.ruoyi.integration.client.oa.config.OaRestCatalog;
import com.ruoyi.integration.client.oa.config.OaRestConnection;
import com.ruoyi.integration.client.oa.config.OaRestRevision;
import com.ruoyi.integration.client.oa.config.OaRestSecretStore;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRestSessionBootstrapTest {
    @Test
    void restoresAnEnabledAccountFromItsDurableActiveRevisionWithoutCallingOa() {
        OaRestCatalog catalog = mock(OaRestCatalog.class);
        OaRestSecretStore secrets = mock(OaRestSecretStore.class);
        OaRestSessionFactory sessions = mock(OaRestSessionFactory.class);
        OaRestSessionRegistry registry = mock(OaRestSessionRegistry.class);
        OaRestConnection connection = new OaRestConnection("oa-default", "OA", true, "active-2", null, 3L, "target-1");
        OaRestRevision revision = new OaRestRevision("active-2", "oa-default", "target-1", "PROD",
                "https://oa.internal", "rest", "member", 5000, 15000, "secret-2", "checksum");
        OaRestSession session = mock(OaRestSession.class);
        when(catalog.list()).thenReturn(List.of(connection));
        when(catalog.active("oa-default")).thenReturn(revision);
        when(secrets.read("oa-default", new RevisionToken("active-2"), "secret-2")).thenReturn("rest-password".toCharArray());
        when(sessions.create(eq(revision), any(char[].class))).thenReturn(session);

        new OaRestSessionBootstrap(catalog, secrets, sessions, registry).restoreActiveSessions();

        verify(registry).activate(session);
        org.mockito.Mockito.verifyNoInteractions(session);
    }
}
