package com.ruoyi.integration.sync.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class SyncCursorMapperTest
{
    @Test
    void initializesLocksAndAdvancesTaskCursor() throws Exception
    {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:sync_cursor;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_sync_cursor (task_code VARCHAR(100) PRIMARY KEY, cursor_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/SyncCursorMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = sessions.openSession(true))
        {
            SyncCursorRepository repository = new MyBatisSyncCursorRepository(
                    session.getMapper(SyncCursorMapper.class));
            LocalDateTime initial = LocalDateTime.of(2026, 9, 1, 0, 0);
            LocalDateTime upper = LocalDateTime.of(2026, 9, 12, 12, 30);

            repository.initialize("TASK", initial);
            assertEquals(initial, repository.findForUpdate("TASK"));
            repository.advance("TASK", upper);
            repository.advance("TASK", initial.minusDays(1));

            assertEquals(upper, repository.findForUpdate("TASK"));
        }
    }
}
