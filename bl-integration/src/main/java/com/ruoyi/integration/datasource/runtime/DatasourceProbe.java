package com.ruoyi.integration.datasource.runtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Performs a bounded, read-only connectivity and permission probe without exposing JDBC details. */
@Component
public class DatasourceProbe {
    private final ObjectMapper json;

    @Autowired
    public DatasourceProbe(ObjectMapper json) {
        this.json = json;
    }

    public ObjectNode test(PreparedDatasource prepared) {
        long startedAt = System.nanoTime();
        ObjectNode result = json.createObjectNode()
                .put("revisionId", prepared.revisionId())
                .put("checkedAt", Instant.now().toString());
        try (Connection connection = prepared.dataSource().getConnection()) {
            return result.put("status", "SUCCESS")
                    .put("durationMs", elapsed(startedAt));
        } catch (SQLException unavailable) {
            return failed(result, "DATASOURCE_CONNECTION_FAILED", "无法建立数据库连接", startedAt);
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
