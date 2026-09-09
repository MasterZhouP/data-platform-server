package com.ruoyi.integration.reference.service;

import java.util.Date;
import com.ruoyi.integration.execution.mapper.IntegrationExecutionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceRecoveryTest {
    @Test void interruptedReferenceIsNotReplayableAndPushRecoveryStillDistinguishesSending() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:recovery;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = db.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TABLE int_execution (execution_id BIGINT, stage VARCHAR(50), status VARCHAR(20), retryable INT, result_unknown INT, dedup_key VARCHAR(200), error_code VARCHAR(100), error_message VARCHAR(2000), start_time TIMESTAMP, end_time TIMESTAMP, update_time TIMESTAMP)");
            s.execute("INSERT INTO int_execution (execution_id, stage, status, dedup_key, start_time) VALUES (1,'REFERENCE_QUERY','RUNNING','ref','2000-01-01'), (2,'RECEIVED','RUNNING','push-before','2000-01-01'), (3,'U8_REQUEST_SENT','RUNNING','push-after','2000-01-01')");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/IntegrationExecutionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            assertEquals(3, session.getMapper(IntegrationExecutionMapper.class).failStaleRunning(new Date(), "BEFORE", "AFTER"));
        }
        try (var c = db.getConnection(); var s = c.createStatement(); var rows = s.executeQuery("SELECT * FROM int_execution ORDER BY execution_id")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt("retryable"));
            assertEquals(0, rows.getInt("result_unknown"));
            assertEquals("PROCESS_INTERRUPTED", rows.getString("error_code"));
            assertFalse(rows.getString("error_message").contains("U8"));
            assertTrue(rows.next());
            assertEquals(1, rows.getInt("retryable"));
            assertEquals("BEFORE", rows.getString("error_code"));
            assertTrue(rows.next());
            assertEquals(0, rows.getInt("retryable"));
            assertEquals(1, rows.getInt("result_unknown"));
            assertEquals("AFTER", rows.getString("error_code"));
        }
    }
}
