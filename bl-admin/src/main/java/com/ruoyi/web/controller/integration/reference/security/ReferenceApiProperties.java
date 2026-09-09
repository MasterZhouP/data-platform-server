package com.ruoyi.web.controller.integration.reference.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.reference.api")
public class ReferenceApiProperties
{
    private List<Client> clients = new ArrayList<>();
    public List<Client> getClients() { return clients; }
    public void setClients(List<Client> clients) { this.clients = clients; }

    public static class Client implements java.security.Principal
    {
        private String clientId;
        private String key;
        private boolean enabled;
        private List<String> taskCodes = new ArrayList<>();
        @Override public String getName() { return clientId; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getTaskCodes() { return taskCodes; }
        public void setTaskCodes(List<String> taskCodes) { this.taskCodes = taskCodes; }
        public boolean allows(String taskCode) { return taskCodes != null && taskCodes.contains(taskCode); }
    }
}
