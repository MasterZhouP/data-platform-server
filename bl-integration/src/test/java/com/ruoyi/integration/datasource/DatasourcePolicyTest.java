package com.ruoyi.integration.datasource;

import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.datasource.validation.DatasourcePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasourcePolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DatasourcePolicy policy = new DatasourcePolicy(Set.of("u8.internal", "10.16.8.12"));

    @Test
    void acceptsStructuredSqlServerFieldsAndRejectsUnapprovedTargetsOrUrlInjection() {
        var config = json.createObjectNode();
        config.put("type", "SQLSERVER");
        config.put("host", "u8.internal");
        config.put("port", 1433);
        config.put("databaseName", "U8Data");
        config.put("username", "readonly_user");
        config.putObject("connectionOptions").put("encrypt", true).put("trustServerCertificate", false);

        assertEquals("jdbc:sqlserver://u8.internal:1433;databaseName=U8Data;encrypt=true;trustServerCertificate=false",
                policy.toJdbcUrl(policy.validate(config)));

        config.put("host", "u8.internal;databaseName=other");
        assertEquals("DATASOURCE_POLICY_REJECTED", assertThrows(ConfigurationException.class,
                () -> policy.validate(config)).code());

        config.put("host", "outside.example.test");
        assertEquals("DATASOURCE_TARGET_NOT_ALLOWED", assertThrows(ConfigurationException.class,
                () -> policy.validate(config)).code());
    }
}
