package com.ruoyi.integration.client.u8.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.u8.JdkU8Gateway;
import com.ruoyi.integration.client.u8.JdkU8HttpTransport;
import com.ruoyi.integration.client.u8.U8GatewayProperties;
import com.ruoyi.integration.client.u8.config.U8GatewayOperation;
import com.ruoyi.integration.client.u8.config.U8GatewayRevision;
import org.springframework.stereotype.Component;

/** Builds an isolated U8 gateway from one validated revision and its decrypted parameters. */
@Component
public class U8GatewayFactory
{
    private final ObjectMapper json;

    public U8GatewayFactory(ObjectMapper json)
    {
        this.json = json;
    }

    public U8GatewaySession create(U8GatewayRevision revision, Map<String, String> accountParameters)
    {
        U8GatewayProperties properties = new U8GatewayProperties();
        properties.setBaseUrl(revision.baseUrl());
        properties.setTokenPath(revision.tokenPath());
        properties.setTradeIdPath(revision.tradeIdPath());
        properties.setTokenPointer(revision.tokenPointer());
        properties.setTradeIdPointer(revision.tradeIdPointer());
        properties.setTokenParameterName(revision.tokenParameterName());
        properties.setTradeIdParameterName(revision.tradeIdParameterName());
        properties.setTokenCacheSeconds(revision.tokenCacheSeconds());
        properties.setConnectTimeoutMillis(revision.connectTimeoutMillis());
        properties.setReadTimeoutMillis(revision.readTimeoutMillis());
        properties.setAccountParameters(accountParameters);
        Map<String, String> paths = new LinkedHashMap<>();
        Set<String> allowed = new LinkedHashSet<>();
        for (U8GatewayOperation operation : operations(revision.operationRegistryJson()))
        {
            if (operation.enabled())
            {
                allowed.add(operation.code());
                paths.put(operation.code(), operation.path());
            }
        }
        properties.setAllowedOperationCodes(allowed);
        properties.setOperationPaths(paths);
        JdkU8Gateway gateway = new JdkU8Gateway(new JdkU8HttpTransport(properties), properties, json);
        return new U8GatewaySession(revision.connectionKey(), revision.revisionId(), gateway,
                gateway::authenticateWithTradeId, gateway::close);
    }

    private Set<U8GatewayOperation> operations(String raw)
    {
        try
        {
            Set<U8GatewayOperation> result = new LinkedHashSet<>();
            JsonNode values = json.readTree(raw);
            if (!values.isArray()) return Set.of();
            for (JsonNode value : values)
            {
                result.add(new U8GatewayOperation(value.path("code").asText(), value.path("method").asText(),
                        value.path("path").asText(), value.path("enabled").asBoolean()));
            }
            return result;
        }
        catch (Exception malformed)
        {
            return Set.of();
        }
    }
}
