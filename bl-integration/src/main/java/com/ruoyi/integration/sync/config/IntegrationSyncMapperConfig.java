package com.ruoyi.integration.sync.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers integration mappers that intentionally live next to their bounded contexts. */
@Configuration
@MapperScan(basePackages = {
        "com.ruoyi.integration.sync.salesoutbound.link",
        "com.ruoyi.integration.sync.schedule"
}, annotationClass = Mapper.class)
public class IntegrationSyncMapperConfig
{
}
