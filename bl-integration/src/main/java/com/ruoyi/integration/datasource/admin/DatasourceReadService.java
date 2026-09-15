package com.ruoyi.integration.datasource.admin;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.datasource.catalog.DatasourceCatalog;
import com.ruoyi.integration.datasource.catalog.DatasourceRow;
import org.springframework.stereotype.Service;

/** Projects datasource state for administration without exposing credentials or encrypted material. */
@Service
public class DatasourceReadService {
    private final DatasourceCatalog catalog;
    private final ObjectMapper json;

    public DatasourceReadService(DatasourceCatalog catalog, ObjectMapper json) {
        this.catalog = catalog;
        this.json = json;
    }

    public List<ObjectNode> list() {
        return catalog.list().stream().map(this::summary).toList();
    }

    public ObjectNode detail(String key) {
        DatasourceRow row = catalog.row(key);
        ObjectNode result = summary(row);
        if (hasText(row.draftRevisionId())) {
            result.set("draftConfig", catalog.getDraft(key));
        }
        if (hasText(row.activeRevisionId())) {
            result.set("activeConfig", catalog.getActive(key));
        }
        String testedRevision = hasText(row.draftRevisionId()) ? row.draftRevisionId() : row.activeRevisionId();
        if (hasText(testedRevision)) {
            ObjectNode lastTest = catalog.lastTest(key, testedRevision);
            if (lastTest.size() > 0) {
                result.set("lastTest", lastTest);
                result.put("health", "SUCCESS".equals(lastTest.path("status").asText()) ? "HEALTHY" : "DANGER");
                result.put("checkedAt", lastTest.path("checkedAt").asText(""));
            }
        }
        return result;
    }

    private ObjectNode summary(DatasourceRow row) {
        return json.createObjectNode()
                .put("datasourceKey", row.datasourceKey())
                .put("name", row.datasourceName())
                .put("enabled", row.enabled())
                .put("activeRevisionId", emptyToNull(row.activeRevisionId()))
                .put("draftRevisionId", emptyToNull(row.draftRevisionId()))
                .put("rowVersion", row.rowVersion());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }
}
