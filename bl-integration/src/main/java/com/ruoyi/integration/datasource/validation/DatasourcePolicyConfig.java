package com.ruoyi.integration.datasource.validation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DatasourcePolicyProperties.class)
public class DatasourcePolicyConfig {
    @Bean
    public DatasourcePolicy datasourcePolicy(DatasourcePolicyProperties properties) {
        return new DatasourcePolicy(properties.getAllowedHosts());
    }
}
