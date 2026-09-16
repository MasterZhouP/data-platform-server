package com.ruoyi.framework.config;

import com.ruoyi.integration.datasource.catalog.DatasourceMapper;
import com.ruoyi.integration.client.oa.config.OaRestMapper;
import com.ruoyi.integration.client.u8.config.U8GatewayMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the configuration-console mapper independently of the static business datasource beans. */
@Configuration
@MapperScan(basePackageClasses = { DatasourceMapper.class, OaRestMapper.class, U8GatewayMapper.class })
public class DatasourceCatalogMapperConfig {
}
