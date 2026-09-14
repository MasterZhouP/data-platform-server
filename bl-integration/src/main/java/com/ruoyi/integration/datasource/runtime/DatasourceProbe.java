package com.ruoyi.integration.datasource.runtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.datasource.validation.ReadonlyPermissionVerifier;
import org.springframework.stereotype.Component;

/** Performs a bounded, read-only connectivity and permission probe without exposing JDBC details. */
@Component
public class DatasourceProbe {
    private final ObjectMapper json;
    private final ReadonlyPermissionVerifier readonly;

    public DatasourceProbe(ObjectMapper json) {
        this(json, new ReadonlyPermissionVerifier());
    }

    DatasourceProbe(ObjectMapper json, ReadonlyPermissionVerifier readonly) {
        this.json = json;
        this.readonly = readonly;
    }

    public ObjectNode test(PreparedDatasource prepared, Set<String> allowedObjects) {
        long startedAt = System.nanoTime();
        ObjectNode result = json.createObjectNode()
                .put("revisionId", prepared.revisionId())
                .put("checkedAt", Instant.now().toString());
        try (Connection connection = prepared.dataSource().getConnection()) {
            readonly.verify(connection, allowedObjects);
            return result.put("status", "SUCCESS")
                    .put("durationMs", elapsed(startedAt));
        } catch (ConfigurationException rejected) {
            return failed(result, rejected.code(), rejected.getMessage(), startedAt);
        } catch (SQLException unavailable) {
            return failed(result, "DATASOURCE_CONNECTION_FAILED", "无法建立数据库连接或确认只读权限", startedAt);
        }
    }

    private static ObjectNode failed(ObjectNode result, String code, String message, long startedAt) {
        return result.put("status", "FAILED")
                .put("code", code)
                .put("message", message)
                .put("durationMs", elapsed(startedAt));
    }

    private static long elapsed(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
