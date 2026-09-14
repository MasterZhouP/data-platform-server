package com.ruoyi.integration.taskdefinition.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void keepsPublishedRevisionImmutableWhileMovingTheTaskPointers() throws Exception
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:integration-task-management;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_integration_task (task_code VARCHAR(100) PRIMARY KEY, task_name VARCHAR(100), task_type VARCHAR(30), enabled BOOLEAN, active_revision_id BIGINT, draft_revision_id BIGINT, config_version BIGINT, create_time TIMESTAMP, update_time TIMESTAMP)");
            statement.execute("CREATE TABLE int_integration_task_revision (revision_id BIGINT AUTO_INCREMENT PRIMARY KEY, task_code VARCHAR(100), revision_no INT, status VARCHAR(20), config_json CLOB, config_checksum VARCHAR(64), dependency_revisions_json CLOB, validation_json CLOB, change_note VARCHAR(500), create_time TIMESTAMP, update_time TIMESTAMP)");
        }
        var sessions = sessionFactory(dataSource);
        try (var session = sessions.openSession(true))
        {
            IntegrationTaskMapper mapper = session.getMapper(IntegrationTaskMapper.class);
            mapper.insertTask(new IntegrationTaskRow("OA_EXPENSE_VOUCHER", "费用报销推凭证", "OA_TO_U8", true,
                    7L, null, 5L));
            mapper.insertRevision(new TaskRevisionRow(7L, "OA_EXPENSE_VOUCHER", 1, "PUBLISHED", "{}",
                    "a".repeat(64), "{}"));
            TaskRevisionWriteRow draft = new TaskRevisionWriteRow();
            draft.setTaskCode("OA_EXPENSE_VOUCHER");
            draft.setRevisionNo(mapper.nextRevisionNo("OA_EXPENSE_VOUCHER"));
            draft.setStatus("DRAFT");
            draft.setConfigJson("{\"u8\":{}}");
            draft.setChecksum("b".repeat(64));
            draft.setDependencyRevisionsJson("{\"u8Gateway\":\"shared\"}");
            mapper.insertGeneratedRevision(draft);
            assertTrue(draft.getRevisionId() > 7L);
            assertEquals(1, mapper.updateTaskDraft("OA_EXPENSE_VOUCHER", "费用报销推凭证", true,
                    draft.getRevisionId(), 5L));
            assertEquals(1, mapper.markRevisionValidated(draft.getRevisionId(), "{\"valid\":true}"));
            assertEquals(1, mapper.archiveRevision(7L));
            assertEquals(1, mapper.publishRevision(draft.getRevisionId()));
            assertEquals(1, mapper.publishTask("OA_EXPENSE_VOUCHER", draft.getRevisionId(), 6L));
            assertEquals("ARCHIVED", mapper.findRevision(7L).status());
            assertEquals("PUBLISHED", mapper.findRevision(draft.getRevisionId()).status());
            assertEquals(draft.getRevisionId(), mapper.findTask("OA_EXPENSE_VOUCHER").activeRevisionId());
            assertNull(mapper.findTask("OA_EXPENSE_VOUCHER").draftRevisionId());
        }
    }

    private org.apache.ibatis.session.SqlSessionFactory sessionFactory(JdbcDataSource dataSource) throws Exception
    {
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        String path = "mapper/integration/IntegrationTaskMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
