package com.ruoyi.integration.client.oa.config;

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

/** Stores versioned, non-sensitive OA REST connection fields and immutable revision pointers. */
@Service
public class OaRestCatalog {
    private final OaRestMapper mapper;
    private final ObjectMapper json;

    public OaRestCatalog(OaRestMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public RevisionToken saveDraft(String key, String expectedRevision, ObjectNode safeFields, String secretId) {
        return saveDraft(key, expectedRevision, safeFields, secretId, new RevisionToken(UUID.randomUUID().toString()));
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public RevisionToken saveDraft(String key, String expectedRevision, ObjectNode safeFields, String secretId,
                                   RevisionToken candidate) {
        requireKey(key);
        if (safeFields == null || secretId == null || secretId.isBlank()) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "OA REST 草稿配置不完整");
        }
        OaRestConnection connection = mapper.findConnection(key);
        if (connection == null) {
            if (hasText(expectedRevision)) throw conflict();
            String targetId = UUID.randomUUID().toString();
            try {
                mapper.insertConnection(new OaRestConnection(key, safeFields.path("connectionName").asText(key), false,
                        null, null, 1L, targetId));
            } catch (DuplicateKeyException duplicate) {
                throw conflict();
            }
            connection = mapper.findConnection(key);
            if (connection == null) {
                connection = new OaRestConnection(key, safeFields.path("connectionName").asText(key), false,
                        null, null, 1L, targetId);
            }
        }
        if (hasText(expectedRevision) && !expectedRevision.equals(connection.draftRevisionId())) throw conflict();
        if (!hasText(expectedRevision) && hasText(connection.draftRevisionId())) throw conflict();
        OaRestRevision revision = revision(candidate.value(), key, connection.targetId(), safeFields, secretId);
        mapper.insertRevision(revision);
        if (mapper.updateDraftPointer(key, revision.revisionId(), connection.rowVersion()) != 1) throw conflict();
        return candidate;
    }

    public OaRestRevision active(String key) {
        OaRestConnection connection = requireConnection(key);
        return requireRevision(key, connection.activeRevisionId(), "OA REST 连接尚未启用");
    }

    public OaRestRevision draft(String key, RevisionToken expected) {
        OaRestConnection connection = requireConnection(key);
        OaRestRevision revision = requireRevision(key, connection.draftRevisionId(), "OA REST 连接尚无草稿");
        if (expected != null && !expected.value().equals(revision.revisionId())) throw conflict();
        return revision;
    }

    public List<OaRestConnection> list() { return mapper.listConnections(); }

    public OaRestConnection row(String key) { return requireConnection(key); }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean activatePointer(String key, RevisionToken candidate, String expectedActiveId) {
        if (candidate == null) throw new ConfigurationException("INVALID_ARGUMENT", 400, "待启用修订不能为空");
        OaRestRevision revision = requireRevision(key, candidate.value(), "OA REST 连接尚无草稿");
        return mapper.activatePointer(key, revision.revisionId(), expectedActiveId) == 1;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean disable(String key) {
        requireKey(key);
        return mapper.disable(key) == 1;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void recordTest(String key, RevisionToken revisionToken, ObjectNode result) {
        if (revisionToken == null || result == null) throw new ConfigurationException("INVALID_ARGUMENT", 400, "认证结果不能为空");
        OaRestRevision revision = requireRevision(key, revisionToken.value(), "OA REST 连接尚无草稿");
        ObjectNode safe = result.deepCopy();
        safe.remove("token");
        safe.remove("password");
        safe.remove("authorization");
        if (mapper.updateLastTest(revision.revisionId(), safe.toString()) != 1) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "认证结果无法保存");
        }
    }

    public ObjectNode lastTest(String key, String revisionId) {
        OaRestRevision revision = requireRevision(key, revisionId, "OA REST 连接尚无修订");
        String raw = mapper.findLastTest(revision.revisionId());
        if (!hasText(raw)) return json.createObjectNode();
        try {
            return (ObjectNode) json.readTree(raw);
        } catch (Exception malformed) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "认证结果无法读取");
        }
    }

    /** Internal-only revision used to re-encrypt an unchanged password for a new draft. */
    public OaRestRevision currentSecretRevision(String key) {
        OaRestConnection connection = requireConnection(key);
        String revisionId = hasText(connection.draftRevisionId()) ? connection.draftRevisionId() : connection.activeRevisionId();
        if (!hasText(revisionId)) return null;
        return requireRevision(key, revisionId, "OA REST 连接尚无修订");
    }

    private OaRestRevision revision(String id, String key, String targetId, ObjectNode fields, String secretId) {
        ObjectNode stored = fields.deepCopy();
        stored.remove("password");
        stored.remove("passwordUpdate");
        String body = stored.toString();
        return new OaRestRevision(id, key, targetId, stored.path("environment").asText(), stored.path("baseUrl").asText(),
                stored.path("restUsername").asText(), stored.path("loginName").asText(), stored.path("connectTimeoutMs").asInt(),
                stored.path("readTimeoutMs").asInt(), secretId, checksum(body));
    }

    private OaRestConnection requireConnection(String key) {
        requireKey(key);
        OaRestConnection connection = mapper.findConnection(key);
        if (connection == null || !key.equals(connection.connectionKey())) {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "OA REST 连接不存在");
        }
        return connection;
    }

    private OaRestRevision requireRevision(String key, String revisionId, String absent) {
        if (!hasText(revisionId)) throw new ConfigurationException("CONFIGURATION_NOT_READY", 409, absent);
        OaRestRevision revision = mapper.findRevision(revisionId);
        if (revision == null || !key.equals(revision.connectionKey())) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "OA REST 修订无法读取");
        }
        return revision;
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,99}")) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "OA REST 连接编码格式不正确");
        }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    private static ConfigurationException conflict() {
        return new ConfigurationException("CONFIGURATION_VERSION_CONFLICT", 409, "配置已更新，请重新加载后再保存");
    }
}
