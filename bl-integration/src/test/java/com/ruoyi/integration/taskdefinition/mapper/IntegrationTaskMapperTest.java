package com.ruoyi.integration.taskdefinition.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class IntegrationTaskMapperTest
{
    @Test
    void persistsExactTaskIdentityAndPublishedRevisionAcrossSessions() throws Exception
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:integration-task-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_integration_task (task_code VARCHAR(100) PRIMARY KEY, task_name VARCHAR(100), task_type VARCHAR(30), enabled BOOLEAN, active_revision_id BIGINT, draft_revision_id BIGINT, config_version BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)");
            statement.execute("CREATE TABLE int_integration_task_revision (revision_id BIGINT PRIMARY KEY, task_code VARCHAR(100), revision_no INT, status VARCHAR(20), config_json CLOB, config_checksum VARCHAR(64), dependency_revisions_json CLOB, create_time TIMESTAMP, update_time TIMESTAMP)");
        }

        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        String path = "mapper/integration/IntegrationTaskMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = sessions.openSession(true))
        {
            IntegrationTaskMapper mapper = session.getMapper(IntegrationTaskMapper.class);
            assertEquals(1, mapper.insertTask(new IntegrationTaskRow("OA_EXPENSE_VOUCHER", "费用报销推凭证",
                    "OA_TO_U8", true, 18L, null, 3L)));
            assertEquals(1, mapper.insertRevision(new TaskRevisionRow(18L, "OA_EXPENSE_VOUCHER", 3,
                    "PUBLISHED", "{\"operationCode\":\"VOUCHER_ADD\"}", "d".repeat(64), "{\"u8Connection\":\"7\"}")));
        }
        try (var session = sessions.openSession(true))
        {
            IntegrationTaskMapper mapper = session.getMapper(IntegrationTaskMapper.class);
            assertEquals("费用报销推凭证", mapper.findTask("OA_EXPENSE_VOUCHER").taskName());
            assertNull(mapper.findTask("oa_expense_voucher"));
            TaskRevisionRow revision = mapper.findRevision(18L);
            assertEquals("PUBLISHED", revision.status());
            assertEquals("{\"u8Connection\":\"7\"}", revision.dependencyRevisionsJson());
        }
    }
}
