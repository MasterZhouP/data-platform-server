package com.ruoyi.integration.client.u8.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(U8GatewayPolicyProperties.class)
public class U8GatewayPolicyConfig
{
    @Bean
    public U8GatewayTargetPolicy u8GatewayTargetPolicy(U8GatewayPolicyProperties properties)
    {
        return new U8GatewayTargetPolicy(java.util.Set.copyOf(properties.getAllowedHosts()));
    }
}
