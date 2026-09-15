package com.ruoyi.integration.datasource.validation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatasourcePolicyConfig {
    @Bean
    public DatasourcePolicy datasourcePolicy() { return new DatasourcePolicy(); }
}
