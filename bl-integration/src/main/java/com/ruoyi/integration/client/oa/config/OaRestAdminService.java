package com.ruoyi.integration.client.oa.config;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.springframework.stereotype.Service;

/** Converts the browser's strictly whitelisted OA REST form into an encrypted immutable draft. */
@Service
public class OaRestAdminService {
    private static final Set<String> REQUEST_FIELDS = Set.of("connectionName", "environment", "baseUrl", "restUsername",
            "loginName", "connectTimeoutMs", "readTimeoutMs", "passwordUpdate", "expectedRevision", "operationId");
    private final OaRestCatalog catalog;
    private final OaRestSecretStore secrets;
    private final OaRestTargetPolicy policy;
    private final ObjectMapper json = new ObjectMapper();

    public OaRestAdminService(OaRestCatalog catalog, OaRestSecretStore secrets, OaRestTargetPolicy policy) {
        this.catalog = catalog;
        this.secrets = secrets;
        this.policy = policy;
    }

    public RevisionToken saveDraft(String key, ObjectNode request) {
        if (request == null) throw new ConfigurationException("INVALID_ARGUMENT", 400, "请求体不能为空");
        request.fieldNames().forEachRemaining(field -> {
            if (!REQUEST_FIELDS.contains(field)) throw new ConfigurationException("INVALID_ARGUMENT", 400, "存在不支持的 OA REST 配置字段");
        });
        ObjectNode fields = json.createObjectNode();
        copy(request, fields, "connectionName", "environment", "baseUrl", "restUsername", "loginName", "connectTimeoutMs", "readTimeoutMs");
        ObjectNode valid = policy.validate(fields);
        RevisionToken candidate = new RevisionToken(UUID.randomUUID().toString());
        String passwordUpdate = request.path("passwordUpdate").asText("");
        String secretId;
        if (!passwordUpdate.isEmpty()) {
            char[] password = passwordUpdate.toCharArray();
            try {
                secretId = secrets.store(key, candidate, password);
            } finally {
                Arrays.fill(password, '\0');
            }
        } else {
            OaRestRevision current = catalog.currentSecretRevision(key);
            if (current == null || current.secretId() == null || current.secretId().isBlank()) {
                throw new ConfigurationException("SECRET_REQUIRED", 400, "请首次填写 OA REST 密码");
            }
            secretId = secrets.rebind(key, new RevisionToken(current.revisionId()), current.secretId(), candidate);
        }
        return catalog.saveDraft(key, optionalText(request, "expectedRevision"), valid, secretId, candidate);
    }

    private static void copy(ObjectNode source, ObjectNode target, String... fields) {
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null && !value.isNull()) target.set(field, value.deepCopy());
        }
    }

    private static String optionalText(ObjectNode source, String key) {
        String value = source.path(key).asText("").trim();
        return value.isEmpty() ? null : value;
    }
}
