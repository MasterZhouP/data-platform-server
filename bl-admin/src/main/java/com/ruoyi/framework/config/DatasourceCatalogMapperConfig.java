package com.ruoyi.framework.config;

import com.ruoyi.integration.datasource.catalog.DatasourceMapper;
import com.ruoyi.integration.client.oa.config.OaRestMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the configuration-console mapper independently of the static business datasource beans. */
@Configuration
@MapperScan(basePackageClasses = { DatasourceMapper.class, OaRestMapper.class })
public class DatasourceCatalogMapperConfig {
}
