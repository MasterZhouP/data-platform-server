package com.ruoyi.integration.client.oa.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OaRestPolicyProperties.class)
public class OaRestPolicyConfig {
    @Bean
    public OaRestTargetPolicy oaRestTargetPolicy(OaRestPolicyProperties properties) {
        return new OaRestTargetPolicy(java.util.Set.copyOf(properties.getAllowedHosts()));
    }
}
