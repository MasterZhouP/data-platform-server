package com.ruoyi.integration.client.oa.config;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/** Projects managed OA REST state for operators without disclosing secret references or credentials. */
@Service
public class OaRestReadService {
    private final OaRestCatalog catalog;
    private final ObjectMapper json;

    public OaRestReadService(OaRestCatalog catalog, ObjectMapper json) {
        this.catalog = catalog;
        this.json = json;
    }

    public List<ObjectNode> list() {
        return catalog.list().stream().map(this::summary).toList();
    }

    public ObjectNode detail(String key) {
        OaRestConnection connection = catalog.row(key);
        ObjectNode result = summary(connection);
        if (hasText(connection.draftRevisionId())) {
            OaRestRevision draft = catalog.draft(key, null);
            result.set("draftConfig", configuration(draft));
        }
        if (hasText(connection.activeRevisionId())) {
            OaRestRevision active = catalog.active(key);
            result.set("activeConfig", configuration(active));
        }
        String testedRevision = hasText(connection.draftRevisionId()) ? connection.draftRevisionId() : connection.activeRevisionId();
        if (hasText(testedRevision)) {
            ObjectNode lastTest = catalog.lastTest(key, testedRevision);
            if (lastTest != null && lastTest.size() > 0) {
                result.set("lastTest", lastTest.deepCopy());
                result.put("health", "SUCCESS".equals(lastTest.path("status").asText()) ? "HEALTHY" : "DANGER");
                result.put("checkedAt", lastTest.path("checkedAt").asText(""));
            }
        }
        return result;
    }

    private ObjectNode summary(OaRestConnection connection) {
        return json.createObjectNode()
                .put("connectionKey", connection.connectionKey())
                .put("name", connection.connectionName())
                .put("enabled", connection.enabled())
                .put("activeRevisionId", emptyToNull(connection.activeRevisionId()))
                .put("draftRevisionId", emptyToNull(connection.draftRevisionId()))
                .put("rowVersion", connection.rowVersion());
    }

    private ObjectNode configuration(OaRestRevision revision) {
        return json.createObjectNode()
                .put("revisionId", revision.revisionId())
                .put("environment", revision.environment())
                .put("baseUrl", revision.baseUrl())
                .put("restUsername", revision.restUsername())
                .put("loginName", revision.loginName())
                .put("connectTimeoutMs", revision.connectTimeoutMs())
                .put("readTimeoutMs", revision.readTimeoutMs())
                .put("secretConfigured", hasText(revision.secretId()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }
}
