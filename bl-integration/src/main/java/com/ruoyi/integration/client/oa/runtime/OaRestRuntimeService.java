package com.ruoyi.integration.client.oa.runtime;

import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.oa.OaClientException;
import com.ruoyi.integration.client.oa.config.OaRestCatalog;
import com.ruoyi.integration.client.oa.config.OaRestRevision;
import com.ruoyi.integration.client.oa.config.OaRestSecretStore;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.springframework.stereotype.Service;

/** Performs an isolated OA token check, then promotes the exact checked session only after durable CAS succeeds. */
@Service
public class OaRestRuntimeService {
    private final OaRestCatalog catalog;
    private final OaRestSecretStore secrets;
    private final OaRestSessionFactory sessions;
    private final OaRestSessionRegistry registry;
    private final ObjectMapper json;

    public OaRestRuntimeService(OaRestCatalog catalog, OaRestSecretStore secrets, OaRestSessionFactory sessions,
                                OaRestSessionRegistry registry, ObjectMapper json) {
        this.catalog = catalog;
        this.secrets = secrets;
        this.sessions = sessions;
        this.registry = registry;
        this.json = json;
    }

    public ObjectNode testDraft(String key, String expectedRevisionId) {
        OaRestSession session = prepare(key, expectedRevisionId);
        try {
            ObjectNode result;
            try {
                result = authenticate(session);
            } catch (OaClientException failed) {
                result = failedResult(failed, session.revisionId(), System.nanoTime());
            }
            catalog.recordTest(key, new RevisionToken(session.revisionId()), result);
            return result;
        } finally {
            close(session);
        }
    }

    public ObjectNode activate(String key, String expectedRevisionId, String expectedActiveRevisionId) {
        OaRestSession session = prepare(key, expectedRevisionId);
        boolean promoted = false;
        try {
            ObjectNode result = authenticate(session);
            catalog.recordTest(key, new RevisionToken(session.revisionId()), result);
            if (!catalog.activatePointer(key, new RevisionToken(session.revisionId()), expectedActiveRevisionId)) {
                throw conflict();
            }
            registry.activate(session);
            promoted = true;
            return result.put("activeRevisionId", session.revisionId());
        } catch (OaClientException failed) {
            ObjectNode result = failedResult(failed, session.revisionId(), System.nanoTime());
            catalog.recordTest(key, new RevisionToken(session.revisionId()), result);
            throw new ConfigurationException("OA_AUTH_TEST_FAILED", 409, "OA REST 认证未通过，不能启用");
        } finally {
            if (!promoted) close(session);
        }
    }

    public void disable(String key) {
        if (!catalog.disable(key)) {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "OA REST 账户不存在");
        }
        registry.disable(key);
    }

    private OaRestSession prepare(String key, String expectedRevisionId) {
        RevisionToken expected = expectedRevisionId == null || expectedRevisionId.isBlank() ? null : new RevisionToken(expectedRevisionId);
        OaRestRevision revision = catalog.draft(key, expected);
        char[] password = secrets.read(key, new RevisionToken(revision.revisionId()), revision.secretId());
        return sessions.create(revision, password);
    }

    private ObjectNode authenticate(OaRestSession session) {
        long started = System.nanoTime();
        session.authenticate();
        return result("SUCCESS", "OA REST 认证通过", session.revisionId(), started);
    }

    private ObjectNode failedResult(OaClientException failure, String revisionId, long started) {
        return result("FAILED", "OA REST 认证失败", revisionId, started)
                .put("errorCode", failure.getErrorCode());
    }

    private ObjectNode result(String status, String message, String revisionId, long started) {
        return json.createObjectNode()
                .put("status", status)
                .put("message", message)
                .put("revisionId", revisionId)
                .put("checkedAt", Instant.now().toString())
                .put("durationMs", (System.nanoTime() - started) / 1_000_000L);
    }

    private static void close(OaRestSession session) {
        try {
            session.close();
        } catch (RuntimeException ignored) {
            // The candidate never replaced the active session, so cleanup is strictly best effort.
        }
    }

    private static ConfigurationException conflict() {
        return new ConfigurationException("CONFIGURATION_VERSION_CONFLICT", 409, "配置已更新，请重新加载后再操作");
    }
}
