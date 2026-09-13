package com.ruoyi.integration.sync.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.task.TaskAction;

class SyncEventMapperTest
{
    @Test
    void registersEachSourceEventOnceAndKeepsAcceptedEventsAsReplayLedger() throws Exception
    {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:sync_events;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_sync_event (event_id BIGINT AUTO_INCREMENT PRIMARY KEY, source_event_id CHAR(64) UNIQUE, task_code VARCHAR(100), document_no VARCHAR(200), operation VARCHAR(30), changed_at TIMESTAMP, status VARCHAR(20), execution_id BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/SyncEventMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true))
        {
            SyncEventRepository repository = new MyBatisSyncEventRepository(session.getMapper(SyncEventMapper.class));
            SyncCandidate event = new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE,
                    LocalDateTime.of(2026, 9, 13, 9, 0));
            repository.register("TASK", List.of(event, event));
            assertEquals(1, repository.findPendingForUpdate("TASK").size());
            String eventId = repository.findPendingForUpdate("TASK").get(0).sourceEventId();
            repository.markAccepted(eventId, 99L);
            repository.register("TASK", List.of(event));
            assertEquals(0, repository.findPendingForUpdate("TASK").size());

            SyncCandidate sameTimestampNewVersion = new SyncCandidate("CK-001", TaskAction.CANCEL_RECREATE,
                    event.changedAt(), "0000000000000002");
            repository.register("TASK", List.of(sameTimestampNewVersion));
            assertEquals(1, repository.findPendingForUpdate("TASK").size());

            SyncCandidate invalid = new SyncCandidate("X".repeat(201), TaskAction.CREATE,
                    event.changedAt(), "0000000000000003");
            assertThrows(RuntimeException.class, () -> repository.register("TASK", List.of(invalid)));

            SyncCandidate missingKey = new SyncCandidate(" ", TaskAction.CREATE, event.changedAt());
            assertThrows(IllegalArgumentException.class,
                    () -> repository.register("TASK", List.of(missingKey)));
        }
    }
}
