package com.ruoyi.integration.client.u8.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores immutable U8 gateway revisions and only safe authentication metadata. */
@Service
public class U8GatewayCatalog
{
    private final U8GatewayMapper mapper;
    private final ObjectMapper json;

    public U8GatewayCatalog(U8GatewayMapper mapper, ObjectMapper json)
    {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public RevisionToken saveDraft(String key, String expectedRevision, ObjectNode safeFields, String secretId)
    {
        return saveDraft(key, expectedRevision, safeFields, secretId, new RevisionToken(UUID.randomUUID().toString()));
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public RevisionToken saveDraft(String key, String expectedRevision, ObjectNode safeFields, String secretId,
            RevisionToken candidate)
    {
        requireKey(key);
        if (safeFields == null || secretId == null || secretId.isBlank())
        {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "U8账户草稿配置不完整");
        }
        U8GatewayConnection connection = mapper.findConnection(key);
        if (connection == null)
        {
            if (hasText(expectedRevision))
            {
                throw conflict();
            }
            try
            {
                mapper.insertConnection(new U8GatewayConnection(key, safeFields.path("connectionName").asText(key),
                        false, null, null, 1L));
            }
            catch (DuplicateKeyException duplicate)
            {
                throw conflict();
            }
            connection = mapper.findConnection(key);
            if (connection == null)
            {
                connection = new U8GatewayConnection(key, safeFields.path("connectionName").asText(key),
                        false, null, null, 1L);
            }
        }
        if (hasText(expectedRevision) && !expectedRevision.equals(connection.draftRevisionId()))
        {
            throw conflict();
        }
        if (!hasText(expectedRevision) && hasText(connection.draftRevisionId()))
        {
            throw conflict();
        }
        U8GatewayRevision revision = revision(candidate.value(), key, safeFields, secretId);
        mapper.insertRevision(revision);
        if (mapper.updateDraftPointer(key, revision.revisionId(), connection.rowVersion()) != 1)
        {
            throw conflict();
        }
        return candidate;
    }

    public U8GatewayConnection row(String key)
    {
        requireKey(key);
        U8GatewayConnection connection = mapper.findConnection(key);
        if (connection == null || !key.equals(connection.connectionKey()))
        {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "U8账户不存在");
        }
        return connection;
    }

    public List<U8GatewayConnection> list()
    {
        return mapper.listConnections();
    }

    public U8GatewayRevision active(String key)
    {
        U8GatewayConnection connection = row(key);
        return requireRevision(key, connection.activeRevisionId(), "U8账户尚未启用");
    }

    public U8GatewayRevision draft(String key, RevisionToken expected)
    {
        U8GatewayConnection connection = row(key);
        U8GatewayRevision revision = requireRevision(key, connection.draftRevisionId(), "U8账户尚无草稿");
        if (expected != null && !expected.value().equals(revision.revisionId()))
        {
            throw conflict();
        }
        return revision;
    }

    public U8GatewayRevision currentSecretRevision(String key)
    {
        U8GatewayConnection connection = row(key);
        String revisionId = hasText(connection.draftRevisionId()) ? connection.draftRevisionId() : connection.activeRevisionId();
        return hasText(revisionId) ? requireRevision(key, revisionId, "U8账户尚无修订") : null;
    }

    public ObjectNode config(U8GatewayRevision revision)
    {
        try
        {
            ObjectNode result = json.createObjectNode()
                    .put("connectionName", revision.connectionKey())
                    .put("environment", revision.environment())
                    .put("baseUrl", revision.baseUrl())
                    .put("tokenPath", revision.tokenPath())
                    .put("tradeIdPath", revision.tradeIdPath())
                    .put("tokenPointer", revision.tokenPointer())
                    .put("tradeIdPointer", revision.tradeIdPointer())
                    .put("tokenParameterName", revision.tokenParameterName())
                    .put("tradeIdParameterName", revision.tradeIdParameterName())
                    .put("tokenCacheSeconds", revision.tokenCacheSeconds())
                    .put("connectTimeoutMillis", revision.connectTimeoutMillis())
                    .put("readTimeoutMillis", revision.readTimeoutMillis());
            result.set("accountParameterNames", json.readTree(revision.accountParameterNamesJson()));
            result.set("operations", json.readTree(revision.operationRegistryJson()));
            return result;
        }
        catch (Exception malformed)
        {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "U8账户配置无法读取");
        }
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void recordTest(String key, RevisionToken revisionToken, ObjectNode result)
    {
        if (revisionToken == null || result == null)
        {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "认证结果不能为空");
        }
        U8GatewayRevision revision = requireRevision(key, revisionToken.value(), "U8账户尚无待认证修订");
        ObjectNode safe = result.deepCopy();
        safe.remove("token");
        safe.remove("tradeId");
        safe.remove("accountParameters");
        safe.remove("password");
        if (mapper.updateLastTest(revision.revisionId(), safe.toString()) != 1)
        {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "认证结果无法保存");
        }
    }

    public ObjectNode lastTest(String key, String revisionId)
    {
        U8GatewayRevision revision = requireRevision(key, revisionId, "U8账户尚无修订");
        String raw = mapper.findLastTest(revision.revisionId());
        if (!hasText(raw))
        {
            return json.createObjectNode();
        }
        try
        {
            return (ObjectNode) json.readTree(raw);
        }
        catch (Exception malformed)
        {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "认证结果无法读取");
        }
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean activatePointer(String key, RevisionToken candidate, String expectedActiveId)
    {
        U8GatewayRevision revision = requireRevision(key, candidate == null ? null : candidate.value(), "U8账户尚无草稿");
        return mapper.activatePointer(key, revision.revisionId(), expectedActiveId) == 1;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean disable(String key)
    {
        return mapper.disable(key) == 1;
    }

    private U8GatewayRevision revision(String id, String key, ObjectNode fields, String secretId)
    {
        String operationJson = fields.path("operations").isArray() ? fields.path("operations").toString() : "[]";
        String body = fields.toString();
        return new U8GatewayRevision(id, key, fields.path("environment").asText(), fields.path("baseUrl").asText(),
                fields.path("tokenPath").asText(), fields.path("tradeIdPath").asText(), fields.path("tokenPointer").asText(),
                fields.path("tradeIdPointer").asText(), fields.path("tokenParameterName").asText(),
                fields.path("tradeIdParameterName").asText(), fields.path("tokenCacheSeconds").asInt(),
                fields.path("connectTimeoutMillis").asInt(), fields.path("readTimeoutMillis").asInt(),
                fields.path("accountParameterNames").isArray() ? fields.path("accountParameterNames").toString() : "[]", operationJson,
                secretId, checksum(body));
    }

    private U8GatewayRevision requireRevision(String key, String revisionId, String absent)
    {
        if (!hasText(revisionId))
        {
            throw new ConfigurationException("CONFIGURATION_NOT_READY", 409, absent);
        }
        U8GatewayRevision revision = mapper.findRevision(revisionId);
        if (revision == null || !key.equals(revision.connectionKey()))
        {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "U8账户修订无法读取");
        }
        return revision;
    }

    private static String checksum(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException unavailable)
        {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void requireKey(String key)
    {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,99}"))
        {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "U8账户编码格式不正确");
        }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static ConfigurationException conflict()
    {
        return new ConfigurationException("CONFIGURATION_VERSION_CONFLICT", 409, "配置已更新，请重新加载后再保存");
    }
}
