package com.ruoyi.integration.client.oa.runtime;

import com.ruoyi.integration.client.oa.config.OaRestCatalog;
import com.ruoyi.integration.client.oa.config.OaRestConnection;
import com.ruoyi.integration.client.oa.config.OaRestRevision;
import com.ruoyi.integration.client.oa.config.OaRestSecretStore;
import com.ruoyi.integration.configuration.RevisionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Rehydrates active OA sessions after restart without making an external OA request at boot. */
@Component
public class OaRestSessionBootstrap {
    private static final Logger log = LoggerFactory.getLogger(OaRestSessionBootstrap.class);
    private final OaRestCatalog catalog;
    private final OaRestSecretStore secrets;
    private final OaRestSessionFactory sessions;
    private final OaRestSessionRegistry registry;

    public OaRestSessionBootstrap(OaRestCatalog catalog, OaRestSecretStore secrets,
                                  OaRestSessionFactory sessions, OaRestSessionRegistry registry) {
        this.catalog = catalog;
        this.secrets = secrets;
        this.sessions = sessions;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreActiveSessions() {
        try {
            for (OaRestConnection connection : catalog.list()) {
                if (!connection.enabled() || connection.activeRevisionId() == null || connection.activeRevisionId().isBlank()) continue;
                restore(connection);
            }
        } catch (RuntimeException failure) {
            log.warn("无法恢复OA REST活动账户 type={}", failure.getClass().getSimpleName());
        }
    }

    private void restore(OaRestConnection connection) {
        try {
            OaRestRevision revision = catalog.active(connection.connectionKey());
            char[] password = secrets.read(connection.connectionKey(), new RevisionToken(revision.revisionId()), revision.secretId());
            registry.activate(sessions.create(revision, password));
        } catch (RuntimeException failure) {
            log.warn("无法恢复OA REST活动账户 key={} type={}", connection.connectionKey(), failure.getClass().getSimpleName());
        }
    }
}
