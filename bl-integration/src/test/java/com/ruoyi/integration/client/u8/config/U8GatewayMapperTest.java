package com.ruoyi.integration.client.u8.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class U8GatewayMapperTest
{
    @Test
    void persistsTheRevisionFieldsUsedToRestoreAnActiveGateway() throws Exception
    {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:u8_gateway_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = db.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("CREATE TABLE int_u8_gateway_connection (connection_key VARCHAR(100) PRIMARY KEY, connection_name VARCHAR(100), enabled BOOLEAN, active_revision_id VARCHAR(64), draft_revision_id VARCHAR(64), row_version BIGINT)");
            statement.execute("CREATE TABLE int_u8_gateway_revision (revision_id VARCHAR(64) PRIMARY KEY, connection_key VARCHAR(100), environment VARCHAR(16), base_url VARCHAR(500), token_path VARCHAR(2000), trade_id_path VARCHAR(2000), token_pointer VARCHAR(500), trade_id_pointer VARCHAR(500), token_parameter_name VARCHAR(100), trade_id_parameter_name VARCHAR(100), token_cache_seconds INT, connect_timeout_ms INT, read_timeout_ms INT, account_parameter_names_json CLOB, operation_registry_json CLOB, secret_id VARCHAR(64), checksum VARCHAR(64), last_test_json CLOB, create_time TIMESTAMP)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
        String path = "mapper/integration/U8GatewayMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true))
        {
            U8GatewayMapper mapper = session.getMapper(U8GatewayMapper.class);
            mapper.insertConnection(new U8GatewayConnection("u8-default", "U8", true, "rev-1", null, 1L));
            mapper.insertRevision(new U8GatewayRevision("rev-1", "u8-default", "TEST", "https://u8.example.test",
                    "/system/token", "/system/tradeid", "/token/id", "/trade/id", "token", "tradeid", 300,
                    5000, 15000, "[\"account\"]", "[]", "secret-1", "checksum"));
            assertEquals("u8-default", mapper.findConnection("u8-default").connectionKey());
            assertEquals("[\"account\"]", mapper.findRevision("rev-1").accountParameterNamesJson());
        }
    }
}
