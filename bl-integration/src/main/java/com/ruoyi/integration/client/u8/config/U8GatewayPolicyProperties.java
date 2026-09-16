package com.ruoyi.integration.client.u8.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.configuration.u8-gateway")
public class U8GatewayPolicyProperties
{
    private List<String> allowedHosts = new ArrayList<>();
    public List<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(List<String> allowedHosts)
    {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : new ArrayList<>(allowedHosts);
    }
}
