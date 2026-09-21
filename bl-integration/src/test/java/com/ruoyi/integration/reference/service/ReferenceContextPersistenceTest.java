package com.ruoyi.integration.reference.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.regex.Pattern;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.mapper.IntegrationExecutionMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceContextPersistenceTest {
    @Test void incrementalMigrationPreservesFull128CharacterContextInExecutionMapper() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:reference-context;MODE=MySQL;DB_CLOSE_DELAY=-1");
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("sql/20260907_reference.sql"))) root = root.getParent();
        String migration = Files.readString(root.resolve("sql/20260907_reference.sql"));
        try (var c = db.getConnection(); var s = c.createStatement()) {
            s.execute("""
                CREATE TABLE int_execution (execution_id BIGINT AUTO_INCREMENT PRIMARY KEY, task_code VARCHAR(100),
                task_revision_id BIGINT, task_checksum CHAR(64), dependency_snapshot_json CLOB,
                master_id VARCHAR(100) NOT NULL, form_id VARCHAR(100), summary_id VARCHAR(100), business_key VARCHAR(200),
                operation VARCHAR(30) NOT NULL DEFAULT 'CREATE',
                trigger_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL', force_flag BOOLEAN NOT NULL DEFAULT FALSE,
                status VARCHAR(20), stage VARCHAR(50), retryable BOOLEAN, result_unknown BOOLEAN, retry_count INT,
                retry_of_execution_id BIGINT, last_completed_stage VARCHAR(100), u8_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
                resume_mode VARCHAR(20) NOT NULL DEFAULT 'FULL', result_outputs_json CLOB,
                dedup_key VARCHAR(255), trigger_payload CLOB, request_payload CLOB,
                response_payload CLOB, error_code VARCHAR(100), error_message VARCHAR(2000), start_time TIMESTAMP,
                end_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP)
                """);
            var alters = Pattern.compile("ALTER TABLE int_execution[^\\r\\n]+", Pattern.CASE_INSENSITIVE).matcher(migration);
            int changes = 0;
            while (alters.find()) { s.execute(alters.group()); changes++; }
            assertTrue(changes > 0);
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String resource = "mapper/integration/IntegrationExecutionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var execution = new IntegrationExecution();
        execution.setTaskCode("DEMO");
        execution.setMasterId("m".repeat(128)); execution.setFormId("f".repeat(128)); execution.setSummaryId("s".repeat(128));
        execution.setOperation("CREATE"); execution.setTriggerSource("MANUAL"); execution.setForce(false);
        execution.setStatus("RUNNING"); execution.setStage("REFERENCE_QUERY");
        execution.setU8Confirmed(false); execution.setResumeMode("FULL");
        execution.setCreateTime(new Date()); execution.setUpdateTime(new Date());
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            var mapper = session.getMapper(IntegrationExecutionMapper.class);
            assertEquals(1, mapper.insertExecution(execution));
            var persisted = mapper.selectExecutionById(execution.getExecutionId());
            assertEquals(execution.getMasterId(), persisted.getMasterId());
            assertEquals(execution.getFormId(), persisted.getFormId());
            assertEquals(execution.getSummaryId(), persisted.getSummaryId());
            execution.setExecutionId(null); execution.setMasterId(null);
            assertEquals(1, mapper.insertExecution(execution));
            assertNull(mapper.selectExecutionById(execution.getExecutionId()).getMasterId());
        }
    }
}
