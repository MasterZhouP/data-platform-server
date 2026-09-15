package com.ruoyi.integration.datasource.admin;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRevision;
import com.ruoyi.integration.datasource.catalog.DatasourceSecretStore;
import com.ruoyi.integration.datasource.validation.DatasourcePolicy;
import org.springframework.stereotype.Service;

/** Translates a browser request into a strictly whitelisted, secret-free revision. */
@Service
public class DatasourceAdminService {
    private static final Set<String> REQUEST_FIELDS = Set.of("name", "environment", "host", "port", "databaseName", "username",
            "connectionOptions", "maximumPoolSize", "connectionTimeoutMs", "passwordUpdate", "expectedRevision", "operationId");
    private final DatasourceCatalog catalog;
    private final DatasourceSecretStore secrets;
    private final DatasourcePolicy policy;
    private final ObjectMapper json;

    public DatasourceAdminService(DatasourceCatalog catalog, DatasourceSecretStore secrets, DatasourcePolicy policy, ObjectMapper json) {
        this.catalog = catalog;
        this.secrets = secrets;
        this.policy = policy;
        this.json = json;
    }

    public RevisionToken saveDraft(String datasourceKey, ObjectNode request) {
        if (request == null) throw new ConfigurationException("INVALID_ARGUMENT", 400, "请求体不能为空");
        request.fieldNames().forEachRemaining(field -> {
            if (!REQUEST_FIELDS.contains(field)) throw new ConfigurationException("INVALID_ARGUMENT", 400, "存在不支持的配置字段");
        });
        ObjectNode config = json.createObjectNode().put("type", "SQLSERVER");
        copy(request, config, "name", "environment", "host", "port", "databaseName", "username", "connectionOptions",
                "maximumPoolSize", "connectionTimeoutMs");
        RevisionToken candidate = new RevisionToken(UUID.randomUUID().toString());
        String passwordUpdate = request.path("passwordUpdate").asText("");
        String secretId;
        if (!passwordUpdate.isEmpty()) {
            char[] password = passwordUpdate.toCharArray();
            try {
                secretId = secrets.store(datasourceKey, candidate, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        } else {
            DatasourceRevision current = catalog.currentSecretRevision(datasourceKey);
            if (current == null || current.secretId() == null || current.secretId().isBlank()) {
                throw new ConfigurationException("SECRET_REQUIRED", 400, "请首次填写数据源密码");
            }
            secretId = secrets.rebind(datasourceKey, new RevisionToken(current.revisionId()), current.secretId(), candidate);
        }
        ObjectNode safeConfig = policy.validate(config);
        return catalog.saveDraft(datasourceKey, nullableText(request, "expectedRevision"), safeConfig, secretId, candidate);
    }

    private static void copy(ObjectNode source, ObjectNode target, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            if (value != null && !value.isNull()) target.set(name, value.deepCopy());
        }
    }

    private static String nullableText(ObjectNode source, String name) {
        String value = source.path(name).asText("");
        return value.isBlank() ? null : value;
    }
}
