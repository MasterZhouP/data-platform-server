package com.ruoyi.integration.datasource.catalog;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatasourceMapperTest {
    @Test
    void changesTheDraftPointerWithCompareAndSetAndDoesNotRewriteHistory() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:datasource_catalog;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE int_datasource_config (datasource_key VARCHAR(100) PRIMARY KEY, datasource_name VARCHAR(100), enabled BOOLEAN, active_revision_id VARCHAR(64), draft_revision_id VARCHAR(64), row_version BIGINT)");
            statement.execute("CREATE TABLE int_datasource_revision (revision_id VARCHAR(64) PRIMARY KEY, datasource_key VARCHAR(100), config_json CLOB, secret_id VARCHAR(64), checksum VARCHAR(64), create_time TIMESTAMP)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/DatasourceMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(DatasourceMapper.class);
            mapper.insertCatalog(new DatasourceRow("u8", "U8", true, "active-1", null, 1L));
            mapper.insertRevision(new DatasourceRevision("draft-1", "u8", "{\"host\":\"db-a\"}", "secret-a", "a"));
            mapper.insertRevision(new DatasourceRevision("draft-2", "u8", "{\"host\":\"db-b\"}", "secret-b", "b"));

            assertEquals(1, mapper.updateDraftPointer("u8", "draft-1", 1L));
            assertEquals(0, mapper.updateDraftPointer("u8", "draft-2", 1L));
            assertEquals("draft-1", mapper.findCatalog("u8").draftRevisionId());
            assertEquals("{\"host\":\"db-a\"}", mapper.findRevision("draft-1").configJson());
            assertEquals("secret-a", mapper.findRevision("draft-1").secretId());
        }
    }
}
