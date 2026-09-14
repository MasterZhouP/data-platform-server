package com.ruoyi.integration.datasource.validation;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.configuration.datasource")
public class DatasourcePolicyProperties {
    private Set<String> allowedHosts = new LinkedHashSet<>();

    public Set<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedHosts);
    }
}
