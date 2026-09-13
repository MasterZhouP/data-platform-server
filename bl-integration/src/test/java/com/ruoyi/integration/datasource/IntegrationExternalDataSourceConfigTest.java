package com.ruoyi.integration.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.zaxxer.hikari.HikariDataSource;

class IntegrationExternalDataSourceConfigTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IntegrationExternalDataSourceConfig.class);

    @Test
    void disabledExternalDataSourcesCreateNoBeans()
    {
        contextRunner.run(context -> {
            assertFalse(context.containsBean("oaDataSource"));
            assertFalse(context.containsBean("u8DataSource"));
        });
    }

    @Test
    void enabledOaCreatesSeparateReadOnlyJdbcTemplateWithoutConnectingAtStartup()
    {
        contextRunner.withPropertyValues(
                "integration.datasource.oa.enabled=true",
                "integration.datasource.oa.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=test;encrypt=false",
                "integration.datasource.oa.username=readonly",
                "integration.datasource.oa.password=placeholder",
                "integration.datasource.oa.read-only=true")
                .run(context -> {
                    assertTrue(context.containsBean("oaDataSource"));
                    assertTrue(context.containsBean("oaJdbcTemplate"));
                    assertFalse(context.containsBean("u8DataSource"));
                });
    }

    @Test
    void u8DatasourceIsAlwaysReadOnlyEvenIfConfigurationAttemptsToDisableIt()
    {
        contextRunner.withPropertyValues(
                "integration.datasource.u8.enabled=true",
                "integration.datasource.u8.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=test;encrypt=false",
                "integration.datasource.u8.username=readonly",
                "integration.datasource.u8.password=placeholder",
                "integration.datasource.u8.read-only=false")
                .run(context -> assertTrue(((HikariDataSource) context.getBean("u8DataSource")).isReadOnly()));
    }
}
