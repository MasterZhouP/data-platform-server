package com.ruoyi.integration.reference.catalog;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceTaskMapperTest {
    @Test void persistsAcrossSessionsWithBoundJsonAndAtomicVersionCheck() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:catalog;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE int_reference_task (task_code VARCHAR(100) PRIMARY KEY, task_name VARCHAR(100), enabled BOOLEAN, datasource_key VARCHAR(100), sql_resource VARCHAR(255), metadata_json CLOB, metadata_version VARCHAR(100), create_time TIMESTAMP, update_time TIMESTAMP)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/ReferenceTaskMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(configuration);
        String metadata = "{\"label\":\"quote ' stays literal\",\"metadataVersion\":\"1\"}";
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(ReferenceTaskMapper.class);
            assertEquals(1, mapper.insert(new ReferenceTaskRow("DEMO", "示例", false, "u8", "integration/reference/material.sql", metadata, "1")));
        }
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(ReferenceTaskMapper.class);
            assertEquals(metadata, mapper.find("DEMO").metadataJson());
            assertNull(mapper.find("demo"));
            assertEquals(1, mapper.list().size());
            var changed = new ReferenceTaskRow("DEMO", "更新", true, "u8", "integration/reference/material.sql", "{}", "2");
            assertEquals(1, mapper.update(changed, "1"));
            assertEquals(0, mapper.update(changed, "1"));
            assertEquals("2", mapper.find("DEMO").metadataVersion());
        }
    }
}
