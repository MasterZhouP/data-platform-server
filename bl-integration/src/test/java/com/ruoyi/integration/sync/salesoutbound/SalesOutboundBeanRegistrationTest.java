package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.datasource.IntegrationExternalDataSourceConfig;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkRepository;

class SalesOutboundBeanRegistrationTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    IntegrationExternalDataSourceConfig.class,
                    JdbcSalesOutboundSource.class,
                    JdbcSalesOutboundChangeSource.class,
                    SalesOutboundPayloadFactory.class,
                    SalesOutboundValidator.class,
                    SalesOutboundTaskHandler.class,
                    SalesOutboundScheduledDefinition.class)
            .withBean(OaProcessOperations.class, () -> mock(OaProcessOperations.class))
            .withBean(OaProcessLinkRepository.class, () -> mock(OaProcessLinkRepository.class));

    @Test
    void enabledDatasourcePropertiesRegisterTheCompleteTaskWithoutBeanOrderingDependency()
    {
        runner.withPropertyValues(
                "integration.datasource.oa.enabled=true",
                "integration.datasource.oa.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=oa;encrypt=false",
                "integration.datasource.oa.username=readonly",
                "integration.datasource.oa.password=placeholder",
                "integration.datasource.u8.enabled=true",
                "integration.datasource.u8.url=jdbc:sqlserver://127.0.0.1:1433;databaseName=u8;encrypt=false",
                "integration.datasource.u8.username=readonly",
                "integration.datasource.u8.password=placeholder")
                .run(context -> {
                    assertTrue(context.containsBean("jdbcSalesOutboundSource"));
                    assertTrue(context.containsBean("jdbcSalesOutboundChangeSource"));
                    assertTrue(context.containsBean("salesOutboundTaskHandler"));
                    assertTrue(context.containsBean("salesOutboundScheduledDefinition"));
                });
    }

    @Test
    void disabledDatasourcePropertiesDoNotRegisterTheTask()
    {
        runner.run(context -> {
            assertFalse(context.containsBean("jdbcSalesOutboundSource"));
            assertFalse(context.containsBean("jdbcSalesOutboundChangeSource"));
            assertFalse(context.containsBean("salesOutboundTaskHandler"));
            assertFalse(context.containsBean("salesOutboundScheduledDefinition"));
        });
    }
}
