package com.ruoyi.integration.u8tooa.schedule;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ruoyi.integration.datasource.runtime.DatasourceLease;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import com.ruoyi.integration.sync.schedule.SyncCandidate;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.u8tooa.config.IncrementalSyncConfig;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Executes a task-owned read-only change feed against one managed datasource revision. */
@Component
public class ConfigurableU8ToOaChangeSource
{
    private final DatasourceRegistry registry;

    public ConfigurableU8ToOaChangeSource(DatasourceRegistry registry)
    {
        this.registry = registry;
    }

    public LocalDateTime captureUpperBound(IncrementalSyncConfig config)
    {
        try (DatasourceLease lease = registry.acquire(config.datasourceKey()))
        {
            return jdbc(lease).queryForObject(config.upperBoundSql(), Map.of(),
                    (rs, rowNum) -> rs.getTimestamp(1).toLocalDateTime());
        }
    }

    public List<SyncCandidate> findCandidates(IncrementalSyncConfig config,
            LocalDateTime fromExclusive, LocalDateTime toInclusive)
    {
        Map<String, Object> params = Map.of("fromTime", Timestamp.valueOf(fromExclusive),
                "toTime", Timestamp.valueOf(toInclusive));
        try (DatasourceLease lease = registry.acquire(config.datasourceKey()))
        {
            NamedParameterJdbcTemplate jdbc = jdbc(lease);
            List<SyncCandidate> result = new ArrayList<>();
            result.addAll(query(jdbc, config.createSql(), params, TaskAction.CREATE));
            result.addAll(query(jdbc, config.updateSql(), params, TaskAction.CANCEL_RECREATE));
            result.addAll(query(jdbc, config.deleteSql(), params, TaskAction.DELETE));
            return result;
        }
    }

    private List<SyncCandidate> query(NamedParameterJdbcTemplate jdbc, String sql,
            Map<String, Object> params, TaskAction action)
    {
        return jdbc.query(sql, params, (rs, rowNum) -> {
            Timestamp changedAt = rs.getTimestamp("changed_at");
            return new SyncCandidate(rs.getString("document_no"), action,
                    changedAt == null ? null : changedAt.toLocalDateTime(), nullableVersion(rs));
        });
    }

    private String nullableVersion(java.sql.ResultSet rs) throws java.sql.SQLException
    {
        try
        {
            return rs.getString("version_token");
        }
        catch (java.sql.SQLException missingColumn)
        {
            // version_token is optional for create/delete feeds; the stable document/time columns are not.
            if (missingColumn.getMessage() != null && missingColumn.getMessage().toLowerCase().contains("version_token"))
            {
                return null;
            }
            throw missingColumn;
        }
    }

    private NamedParameterJdbcTemplate jdbc(DatasourceLease lease)
    {
        return new NamedParameterJdbcTemplate(lease.dataSource());
    }
}
