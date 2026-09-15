package com.ruoyi.integration.client.oa.runtime;

import java.util.Arrays;
import com.ruoyi.integration.client.oa.OaHttpTransport;
import com.ruoyi.integration.client.oa.OaProcessClient;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaRestSettings;
import com.ruoyi.integration.client.oa.OaTokenProvider;
import com.ruoyi.integration.client.oa.config.OaRestRevision;
import org.springframework.stereotype.Service;

/** Creates a short-lived or active client whose transport, token cache and identity all share one revision. */
@Service
public class OaRestSessionFactory {
    private final OaHttpTransportFactory transports;

    public OaRestSessionFactory(OaHttpTransportFactory transports) {
        this.transports = transports;
    }

    public OaRestSession create(OaRestRevision revision, char[] password) {
        String passwordValue;
        try {
            passwordValue = new String(password);
        } finally {
            Arrays.fill(password, '\0');
        }
        OaRestSettings settings = new OaRestSettings(revision.baseUrl(), revision.restUsername(), passwordValue,
                revision.loginName(), revision.connectTimeoutMs(), revision.readTimeoutMs());
        OaHttpTransport transport = transports.create(settings);
        OaTokenProvider tokens = new OaTokenProvider(transport, settings);
        OaProcessOperations operations = new OaProcessClient(transport, tokens, settings);
        return new Session(revision, tokens, operations);
    }

    private record Session(OaRestRevision revision, OaTokenProvider tokens, OaProcessOperations operations) implements OaRestSession {
        @Override public String connectionKey() { return revision.connectionKey(); }
        @Override public String revisionId() { return revision.revisionId(); }
        @Override public String targetId() { return revision.targetId(); }
        @Override public String loginName() { return revision.loginName(); }
        @Override public void authenticate() { tokens.getToken(); }
        @Override public void close() { tokens.clear(); }
    }
}
