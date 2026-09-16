package com.ruoyi.integration.client.u8;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * U8 网关运行时设置值对象。生产实例由受管账户修订创建；保留该类型供传输层和迁移兼容测试使用。
 * <p>
 * 任务版本仅能引用业务操作和相对路径，不能保存主机、账户、appKey 或 token；从而确保所有 OA→U8 任务使用同一套账户。
 * </p>
 */
@ConfigurationProperties(prefix = "integration.u8")
public class U8GatewayProperties
{
    private String baseUrl;
    private String tokenPath = "/system/token";
    private String tradeIdPath = "/system/tradeid";
    private String tokenPointer = "/token/id";
    private String tradeIdPointer = "/trade/id";
    private String tokenParameterName = "token";
    private String tradeIdParameterName = "tradeid";
    private int tokenCacheSeconds = 300;
    private int connectTimeoutMillis = 5000;
    private int readTimeoutMillis = 15000;
    private Map<String, String> accountParameters = new LinkedHashMap<>();
    private Set<String> allowedOperationCodes = new LinkedHashSet<>();
    private Map<String, String> operationPaths = new LinkedHashMap<>();

    public boolean isConfigured()
    {
        return baseUrl != null && !baseUrl.isBlank() && accountParameters != null && !accountParameters.isEmpty();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getTokenPath() { return tokenPath; }
    public void setTokenPath(String tokenPath) { this.tokenPath = tokenPath; }
    public String getTradeIdPath() { return tradeIdPath; }
    public void setTradeIdPath(String tradeIdPath) { this.tradeIdPath = tradeIdPath; }
    public String getTokenPointer() { return tokenPointer; }
    public void setTokenPointer(String tokenPointer) { this.tokenPointer = tokenPointer; }
    public String getTradeIdPointer() { return tradeIdPointer; }
    public void setTradeIdPointer(String tradeIdPointer) { this.tradeIdPointer = tradeIdPointer; }
    public String getTokenParameterName() { return tokenParameterName; }
    public void setTokenParameterName(String tokenParameterName) { this.tokenParameterName = tokenParameterName; }
    public String getTradeIdParameterName() { return tradeIdParameterName; }
    public void setTradeIdParameterName(String tradeIdParameterName) { this.tradeIdParameterName = tradeIdParameterName; }
    public int getTokenCacheSeconds() { return tokenCacheSeconds; }
    public void setTokenCacheSeconds(int tokenCacheSeconds) { this.tokenCacheSeconds = tokenCacheSeconds; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
    public Map<String, String> getAccountParameters() { return Map.copyOf(accountParameters); }
    public void setAccountParameters(Map<String, String> accountParameters)
    {
        this.accountParameters = accountParameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(accountParameters);
    }
    public Set<String> getAllowedOperationCodes() { return Set.copyOf(allowedOperationCodes); }
    public void setAllowedOperationCodes(Set<String> allowedOperationCodes)
    {
        this.allowedOperationCodes = allowedOperationCodes == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedOperationCodes);
    }
    public Map<String, String> getOperationPaths() { return Map.copyOf(operationPaths); }
    public void setOperationPaths(Map<String, String> operationPaths)
    {
        this.operationPaths = operationPaths == null ? new LinkedHashMap<>() : new LinkedHashMap<>(operationPaths);
    }
}
