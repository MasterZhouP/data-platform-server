package com.ruoyi.integration.execution.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class ExecutionTerminalStateMapperTest
{
    @Test
    void repeatableSuccessAndKnownFailureReleaseTheActiveDedupKey() throws Exception
    {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:execution_terminal;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_execution (execution_id BIGINT PRIMARY KEY, status VARCHAR(20), stage VARCHAR(50), retryable INT, result_unknown INT, business_key VARCHAR(200), request_payload CLOB, response_payload CLOB, dedup_key VARCHAR(255), error_code VARCHAR(100), error_message VARCHAR(2000), end_time TIMESTAMP, update_time TIMESTAMP)");
            statement.execute("INSERT INTO int_execution(execution_id,status,stage,dedup_key) VALUES (1,'RUNNING','START_OA_PROCESS','TASK:DOC'),(2,'RUNNING','LOAD_U8','TASK:DOC2')");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/IntegrationExecutionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true))
        {
            IntegrationExecutionMapper mapper = session.getMapper(IntegrationExecutionMapper.class);
            assertEquals(1, mapper.markSuccess(1L, "DOC", "{}", "{}", false, new Date()));
            assertEquals(1, mapper.markFailed(2L, "INVALID", "invalid", false, false, new Date()));
        }
        try (var connection = db.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT dedup_key FROM int_execution ORDER BY execution_id"))
        {
            rows.next();
            assertNull(rows.getString(1));
            rows.next();
            assertNull(rows.getString(1));
        }
    }

    @Test
    void confirmedU8OutputIsPersistedBeforeTheExecutionBecomesPartialSuccess() throws Exception
    {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:execution_checkpoint;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("""
                    CREATE TABLE int_execution (
                        execution_id BIGINT PRIMARY KEY, status VARCHAR(20), stage VARCHAR(100),
                        retryable INT, result_unknown INT, u8_confirmed INT, last_completed_stage VARCHAR(100),
                        result_outputs_json CLOB, error_code VARCHAR(100), error_message VARCHAR(2000),
                        end_time TIMESTAMP, update_time TIMESTAMP)
                    """);
            statement.execute("INSERT INTO int_execution(execution_id,status,stage,u8_confirmed) VALUES (1,'RUNNING','U8_REQUEST_SENDING',0)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/IntegrationExecutionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true))
        {
            IntegrationExecutionMapper mapper = session.getMapper(IntegrationExecutionMapper.class);
            assertEquals(1, mapper.checkpointU8Confirmed(1L, "{\"voucherNo\":\"记-001\"}", "U8_CONFIRMED", new Date()));
            assertEquals(1, mapper.markPartialSuccess(1L, "RESULT_NOT_READY", "凭证未落库",
                    "RESULT_VOUCHER_ATTEMPT_3", "{\"voucherNo\":\"记-001\"}", new Date()));
        }
        try (var connection = db.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT status,u8_confirmed,last_completed_stage,result_outputs_json FROM int_execution"))
        {
            rows.next();
            assertEquals("PARTIAL_SUCCESS", rows.getString(1));
            assertEquals(1, rows.getInt(2));
            assertEquals("RESULT_VOUCHER_ATTEMPT_3", rows.getString(3));
            assertEquals("{\"voucherNo\":\"记-001\"}", rows.getString(4));
        }
    }
}
