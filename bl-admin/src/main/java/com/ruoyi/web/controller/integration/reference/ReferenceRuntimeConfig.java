package com.ruoyi.web.controller.integration.reference;

import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import com.ruoyi.integration.reference.engine.ManagedReferenceDataSources;
import com.ruoyi.integration.reference.engine.ReferenceDataSources;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;

@Configuration
@MapperScan(basePackages = "com.ruoyi.integration.reference.catalog", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class ReferenceRuntimeConfig
{
    @Bean
    public ReferenceDataSources referenceDataSources(DatasourceRegistry registry)
    {
        return new ManagedReferenceDataSources(registry);
    }
}
