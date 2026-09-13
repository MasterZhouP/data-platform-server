package com.ruoyi.integration.sync.salesoutbound.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.client.oa.OaProcessRef;

class OaProcessLinkMapperTest
{
    @Test
    void persistsVersionedProcessLinksAndLifecycleState() throws Exception
    {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:oa_links;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_oa_process_link (link_id BIGINT AUTO_INCREMENT PRIMARY KEY, task_code VARCHAR(100), business_key VARCHAR(200), u8_id VARCHAR(100), summary_id VARCHAR(100), affair_id VARCHAR(100), process_id VARCHAR(100), state VARCHAR(50), version_no INT, previous_link_id BIGINT, create_time TIMESTAMP, update_time TIMESTAMP, UNIQUE(task_code,business_key,version_no))");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/OaProcessLinkMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = sessions.openSession(true))
        {
            OaProcessLinkRepository repository = new MyBatisOaProcessLinkRepository(
                    session.getMapper(OaProcessLinkMapper.class));
            OaProcessLink first = repository.createCreating("TASK", "CK-001", "10001", null);
            repository.activate(first.getLinkId(), new OaProcessRef("S-1", "A-1", "P-1"));
            OaProcessLink second = repository.createCreating("TASK", "CK-001", "10001", first.getLinkId());

            assertEquals(2, second.getVersionNo());
            OaProcessLink latest = repository.findLatest("TASK", "CK-001");
            assertEquals(second.getLinkId(), latest.getLinkId());
            assertEquals(ProcessLinkState.CREATING, latest.getState());
        }
    }
}
