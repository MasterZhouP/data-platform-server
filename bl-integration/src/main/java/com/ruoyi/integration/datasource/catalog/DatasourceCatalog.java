package com.ruoyi.integration.datasource.catalog;

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

/**
 * Stores only safe connection fields in an immutable revision. The secret is represented by
 * its internal identifier and is never copied into the JSON returned to an administrator.
 */
@Service
public class DatasourceCatalog {
    private final DatasourceMapper mapper;
    private final ObjectMapper json;

    public DatasourceCatalog(DatasourceMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public RevisionToken saveDraft(String key, String expectedRevision, ObjectNode nonSecretConfig, String secretId) {
        return saveDraft(key, expectedRevision, nonSecretConfig, secretId, new RevisionToken(UUID.randomUUID().toString()));
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public RevisionToken saveDraft(String key, String expectedRevision, ObjectNode nonSecretConfig, String secretId,
                                   RevisionToken candidate) {
        requireKey(key);
        if (nonSecretConfig == null) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "数据源配置不能为空");
        }
        DatasourceRow row = mapper.findCatalog(key);
        if (row == null) {
            if (hasText(expectedRevision)) {
                throw conflict();
            }
            String name = nonSecretConfig.path("name").asText(key);
            try {
                mapper.insertCatalog(new DatasourceRow(key, name, false, null, null, 1L));
            } catch (DuplicateKeyException duplicate) {
                throw conflict();
            }
            row = mapper.findCatalog(key);
            if (row == null) {
                row = new DatasourceRow(key, name, false, null, null, 1L);
            }
        }
        if (hasText(expectedRevision) && !expectedRevision.equals(row.draftRevisionId())) {
            throw conflict();
        }
        if (!hasText(expectedRevision) && hasText(row.draftRevisionId())) {
            throw conflict();
        }

        String revisionId = candidate.value();
        ObjectNode stored = nonSecretConfig.deepCopy();
        stored.remove("password");
        stored.remove("passwordUpdate");
        stored.remove("secretConfigured");
        String configJson = stored.toString();
        mapper.insertRevision(new DatasourceRevision(revisionId, key, configJson, secretId, checksum(configJson)));
        if (mapper.updateDraftPointer(key, revisionId, row.rowVersion()) != 1) {
            throw conflict();
        }
        return new RevisionToken(revisionId);
    }

    public ObjectNode getDraft(String key) {
        return readRevision(key, requireCatalog(key).draftRevisionId(), "数据源尚无草稿");
    }

    public ObjectNode getActive(String key) {
        return readRevision(key, requireCatalog(key).activeRevisionId(), "数据源尚未启用");
    }

    public List<DatasourceRow> list() {
        return mapper.listCatalog();
    }

    public DatasourceRow row(String key) {
        return requireCatalog(key);
    }

    public DatasourceRevision draftRevision(String key) {
        DatasourceRow row = requireCatalog(key);
        if (!hasText(row.draftRevisionId())) {
            throw new ConfigurationException("CONFIGURATION_NOT_READY", 409, "数据源尚无草稿");
        }
        DatasourceRevision revision = mapper.findRevision(row.draftRevisionId());
        if (revision == null || !key.equals(revision.datasourceKey())) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "数据源配置无法读取");
        }
        return revision;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public void recordTest(String key, RevisionToken revisionToken, ObjectNode safeResult) {
        if (revisionToken == null || safeResult == null) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "检测结果不能为空");
        }
        DatasourceRevision revision = revisionFor(key, revisionToken.value());
        ObjectNode stored = safeResult.deepCopy();
        stored.remove("password");
        stored.remove("jdbcUrl");
        stored.remove("ciphertext");
        if (mapper.updateLastTest(revision.revisionId(), stored.toString()) != 1) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "检测结果无法保存");
        }
    }

    public ObjectNode lastTest(String key, String revisionId) {
        DatasourceRevision revision = revisionFor(key, revisionId);
        String stored = mapper.findLastTest(revision.revisionId());
        if (!hasText(stored)) return json.createObjectNode();
        try {
            return (ObjectNode) json.readTree(stored);
        } catch (Exception malformed) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "检测结果无法读取");
        }
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean activatePointer(String key, RevisionToken candidate, String expectedActiveId) {
        requireKey(key);
        if (candidate == null) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "待启用修订不能为空");
        }
        DatasourceRevision revision = mapper.findRevision(candidate.value());
        if (revision == null || !key.equals(revision.datasourceKey())) {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "待启用的数据源修订不存在");
        }
        return mapper.activatePointer(key, candidate.value(), expectedActiveId) == 1;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public boolean disable(String key) {
        requireKey(key);
        return mapper.disable(key) == 1;
    }

    /** Internal only: the currently editable secret, otherwise the active secret to be rebound. */
    public DatasourceRevision currentSecretRevision(String key) {
        DatasourceRow row = requireCatalog(key);
        String revisionId = hasText(row.draftRevisionId()) ? row.draftRevisionId() : row.activeRevisionId();
        if (!hasText(revisionId)) return null;
        DatasourceRevision revision = mapper.findRevision(revisionId);
        if (revision == null || !key.equals(revision.datasourceKey())) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "数据源配置无法读取");
        }
        return revision;
    }

    private ObjectNode readRevision(String key, String revisionId, String absentMessage) {
        if (!hasText(revisionId)) {
            throw new ConfigurationException("CONFIGURATION_NOT_READY", 409, absentMessage);
        }
        DatasourceRevision revision = mapper.findRevision(revisionId);
        if (revision == null || !key.equals(revision.datasourceKey())) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "数据源配置无法读取");
        }
        try {
            ObjectNode result = (ObjectNode) json.readTree(revision.configJson());
            result.put("revisionId", revision.revisionId());
            result.put("secretConfigured", hasText(revision.secretId()));
            return result;
        } catch (Exception ex) {
            throw new ConfigurationException("CONFIGURATION_UNAVAILABLE", 503, "数据源配置无法读取");
        }
    }

    private DatasourceRevision revisionFor(String key, String revisionId) {
        requireKey(key);
        if (!hasText(revisionId)) {
            throw new ConfigurationException("CONFIGURATION_NOT_READY", 409, "数据源尚无待检测修订");
        }
        DatasourceRevision revision = mapper.findRevision(revisionId);
        if (revision == null || !key.equals(revision.datasourceKey())) {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "数据源修订不存在");
        }
        return revision;
    }

    private DatasourceRow requireCatalog(String key) {
        requireKey(key);
        DatasourceRow row = mapper.findCatalog(key);
        if (row == null || !key.equals(row.datasourceKey())) {
            throw new ConfigurationException("CONFIGURATION_NOT_FOUND", 404, "数据源不存在");
        }
        return row;
    }

    private static String checksum(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,99}")) {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "数据源编码格式不正确");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ConfigurationException conflict() {
        return new ConfigurationException("CONFIGURATION_VERSION_CONFLICT", 409, "配置已更新，请重新加载后再保存");
    }
}
