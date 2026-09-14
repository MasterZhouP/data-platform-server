package com.ruoyi.integration.client.oa.runtime;

import java.util.Map;
import com.ruoyi.integration.client.oa.OaHttpResponse;
import com.ruoyi.integration.client.oa.config.OaRestRevision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRestSessionFactoryTest {
    @Test
    void authenticatesAnIsolatedRevisionWithoutExposingItsToken() {
        OaHttpTransportFactory transports = mock(OaHttpTransportFactory.class);
        var transport = mock(com.ruoyi.integration.client.oa.OaHttpTransport.class);
        when(transports.create(any())).thenReturn(transport);
        when(transport.exchange(any())).thenReturn(new OaHttpResponse(200, "{\"id\":\"test-token\"}"));
        OaRestRevision revision = new OaRestRevision("revision-2", "oa-default", "target-1", "TEST",
                "https://oa-test.internal", "rest-test", "member-test", 5000, 15000, "secret-1", "checksum");

        try (OaRestSession session = new OaRestSessionFactory(transports).create(revision, "test-only-password".toCharArray())) {
            assertEquals("oa-default", session.connectionKey());
            assertEquals("revision-2", session.revisionId());
            assertEquals("member-test", session.loginName());
            session.authenticate();
        }

        verify(transport).exchange(org.mockito.ArgumentMatchers.argThat(request -> "/seeyon/rest/token".equals(request.path())));
    }
}
