package com.ruoyi.framework.config;

import com.ruoyi.integration.datasource.catalog.DatasourceMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the configuration-console mapper independently of the static business datasource beans. */
@Configuration
@MapperScan(basePackageClasses = DatasourceMapper.class)
public class DatasourceCatalogMapperConfig {
}
