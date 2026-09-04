package com.ruoyi.integration.execution.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;

class MapperXmlContractTest
{
    @Test
    void executionMapperXmlIsPackagedAndWellFormed() throws Exception
    {
        Configuration configuration = new Configuration();
        parse(configuration, "mapper/integration/IntegrationExecutionMapper.xml");
        parse(configuration, "mapper/integration/IntegrationExecutionStageMapper.xml");
        assertTrue(configuration.hasStatement(
                "com.ruoyi.integration.execution.mapper.IntegrationExecutionMapper.claimPending"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.integration.execution.mapper.IntegrationExecutionStageMapper.insertExecutionStage"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.integration.execution.mapper.IntegrationExecutionStageMapper.failStaleRunningStages"));
    }

    private void parse(Configuration configuration, String path) throws Exception
    {
        var resource = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        assertNotNull(resource, path);
        try (resource)
        {
            new XMLMapperBuilder(resource, configuration, path, configuration.getSqlFragments()).parse();
        }
    }
}
