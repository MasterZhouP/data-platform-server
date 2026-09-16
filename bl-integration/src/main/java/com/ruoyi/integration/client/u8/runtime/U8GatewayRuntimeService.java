package com.ruoyi.integration.client.u8.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.u8.U8GatewayException;
import com.ruoyi.integration.client.u8.U8TransportException;
import com.ruoyi.integration.client.u8.config.U8GatewayCatalog;
import com.ruoyi.integration.client.u8.config.U8GatewayRevision;
import com.ruoyi.integration.client.u8.config.U8GatewaySecretStore;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.springframework.stereotype.Service;

/** Tests and promotes U8 gateway candidates without disturbing the active account. */
@Service
public class U8GatewayRuntimeService
{
    private final U8GatewayCatalog catalog;
    private final U8GatewaySecretStore secrets;
    private final U8GatewayFactory factory;
    private final U8GatewayRegistry registry;
    private final ObjectMapper json;

    public U8GatewayRuntimeService(U8GatewayCatalog catalog, U8GatewaySecretStore secrets,
            U8GatewayFactory factory, U8GatewayRegistry registry, ObjectMapper json)
    {
        this.catalog = catalog;
        this.secrets = secrets;
        this.factory = factory;
        this.registry = registry;
        this.json = json;
    }

    public ObjectNode testDraft(String key, String expectedRevisionId)
    {
        U8GatewaySession session = prepare(key, expectedRevisionId);
        try
        {
            ObjectNode result = authenticate(session);
            catalog.recordTest(key, new RevisionToken(session.revisionId()), result);
            return result;
        }
        finally
        {
            close(session);
        }
    }

    public ObjectNode activate(String key, String expectedRevisionId, String expectedActiveRevisionId)
    {
        U8GatewaySession session = prepare(key, expectedRevisionId);
        boolean promoted = false;
        try
        {
            ObjectNode result = authenticate(session);
            catalog.recordTest(key, new RevisionToken(session.revisionId()), result);
            if (!"SUCCESS".equals(result.path("status").asText()))
            {
                throw new ConfigurationException("U8_AUTH_TEST_FAILED", 409, "U8账户认证未通过，不能启用");
            }
            if (!catalog.activatePointer(key, new RevisionToken(session.revisionId()), expectedActiveRevisionId))
            {
                throw conflict();
            }
            registry.activate(session);
            promoted = true;
            return result.put("activeRevisionId", session.revisionId());
        }
        finally
        {
            if (!promoted) close(session);
        }
    }

    public void disable(String key)
    {
        if (!catalog.disable(key))
        {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "U8账户不存在");
        }
        registry.disable(key);
    }

    private U8GatewaySession prepare(String key, String expectedRevisionId)
    {
        RevisionToken expected = expectedRevisionId == null || expectedRevisionId.isBlank()
                ? null : new RevisionToken(expectedRevisionId);
        U8GatewayRevision revision = catalog.draft(key, expected);
        Map<String, String> parameters = new LinkedHashMap<>(secrets.read(key,
                new RevisionToken(revision.revisionId()), revision.secretId()));
        try
        {
            return factory.create(revision, parameters);
        }
        finally
        {
            parameters.clear();
        }
    }

    private ObjectNode authenticate(U8GatewaySession session)
    {
        long started = System.nanoTime();
        try
        {
            session.authenticate();
            return result("SUCCESS", "U8账户认证通过", session.revisionId(), started);
        }
        catch (U8GatewayException failure)
        {
            return failed(failure.getErrorCode(), session.revisionId(), started);
        }
        catch (U8TransportException failure)
        {
            return failed("U8_AUTH_UNAVAILABLE", session.revisionId(), started);
        }
    }

    private ObjectNode result(String status, String message, String revisionId, long started)
    {
        return json.createObjectNode().put("status", status).put("message", message).put("revisionId", revisionId)
                .put("checkedAt", Instant.now().toString()).put("durationMs", elapsed(started));
    }

    private ObjectNode failed(String code, String revisionId, long started)
    {
        return result("FAILED", "U8账户认证失败", revisionId, started).put("errorCode", code);
    }

    private static long elapsed(long started) { return Math.max(0L, (System.nanoTime() - started) / 1_000_000L); }
    private static void close(U8GatewaySession session) { if (session != null) try { session.close(); } catch (RuntimeException ignored) { } }
    private static ConfigurationException conflict() { return new ConfigurationException("CONFIGURATION_VERSION_CONFLICT", 409, "配置已更新，请重新加载后再操作"); }
}
