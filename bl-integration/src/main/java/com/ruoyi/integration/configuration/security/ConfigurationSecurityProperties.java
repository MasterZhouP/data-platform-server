package com.ruoyi.integration.configuration.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned key material. Values must be supplied outside source control. */
@ConfigurationProperties(prefix = "integration.configuration.encryption")
public class ConfigurationSecurityProperties {
    private String activeKeyId;
    private Map<String, String> keys = new HashMap<>();

    public String getActiveKeyId() { return activeKeyId; }
    public void setActiveKeyId(String activeKeyId) { this.activeKeyId = activeKeyId; }
    public Map<String, String> getKeys() { return keys; }
    public void setKeys(Map<String, String> keys) { this.keys = keys == null ? new HashMap<>() : new HashMap<>(keys); }
}
