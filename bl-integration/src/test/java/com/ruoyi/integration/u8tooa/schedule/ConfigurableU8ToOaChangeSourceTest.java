package com.ruoyi.integration.u8tooa.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import com.ruoyi.integration.datasource.runtime.PreparedDatasource;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.u8tooa.config.IncrementalSyncConfig;

class ConfigurableU8ToOaChangeSourceTest
{
    private final DatasourceRegistry registry = new DatasourceRegistry();
    private final ConfigurableU8ToOaChangeSource source = new ConfigurableU8ToOaChangeSource(registry);
    private final LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 1, 1, 1, 0);

    @BeforeEach
    void setUp() throws Exception
    {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:u8tooa;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        try (var connection = database.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS sync_events");
            statement.execute("CREATE TABLE sync_events(document_no VARCHAR(40), changed_at TIMESTAMP, version_token VARCHAR(40))");
            statement.execute("INSERT INTO sync_events VALUES ('DOC-1', TIMESTAMP '2026-01-01 00:30:00', 'v1')");
        }
        registry.activate(new PreparedDatasource("u8", "rev-1", database));
    }

    @Test
    void readsUpperBoundAndMapsConfiguredChangeQueries()
    {
        IncrementalSyncConfig config = new IncrementalSyncConfig("u8",
                "SELECT CAST('2026-01-01 02:00:00' AS TIMESTAMP) AS db_time",
                "SELECT document_no, changed_at, version_token FROM sync_events WHERE changed_at > :fromTime AND changed_at <= :toTime",
                "SELECT document_no, changed_at, version_token FROM sync_events WHERE changed_at > :fromTime AND changed_at <= :toTime",
                "SELECT document_no, changed_at, version_token FROM sync_events WHERE changed_at > :fromTime AND changed_at <= :toTime",
                from, 60);

        assertEquals(LocalDateTime.of(2026, 1, 1, 2, 0), source.captureUpperBound(config));
        List<com.ruoyi.integration.sync.schedule.SyncCandidate> candidates = source.findCandidates(config, from, to);

        assertEquals(3, candidates.size());
        assertEquals("DOC-1", candidates.get(0).documentNo());
        assertEquals(TaskAction.CREATE, candidates.get(0).action());
        assertEquals("v1", candidates.get(0).versionToken());
    }
}
