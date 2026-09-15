package com.ruoyi.integration.datasource.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.validation.DatasourcePolicy;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourcePoolFactoryTest {
    @Test
    void createsAReadOnlyCandidatePoolFromStructuredFieldsWithoutConnectingAtStartup() {
        var json = new ObjectMapper();
        var config = json.createObjectNode();
        config.put("type", "SQLSERVER");
        config.put("host", "u8.internal");
        config.put("port", 1433);
        config.put("databaseName", "U8Data");
        config.put("username", "readonly");
        config.put("maximumPoolSize", 2);
        config.put("connectionTimeoutMs", 2345);
        var factory = new DatasourcePoolFactory(new DatasourcePolicy());

        PreparedDatasource prepared = factory.create("u8", new RevisionToken("revision-1"), config, "test-only-password".toCharArray());
        HikariDataSource source = (HikariDataSource) prepared.dataSource();
        try {
            assertEquals("jdbc:sqlserver://u8.internal:1433;databaseName=U8Data", source.getJdbcUrl());
            assertTrue(source.isReadOnly());
            assertEquals(2, source.getMaximumPoolSize());
            assertEquals(2345, source.getConnectionTimeout());
        } finally {
            source.close();
        }
    }
}
