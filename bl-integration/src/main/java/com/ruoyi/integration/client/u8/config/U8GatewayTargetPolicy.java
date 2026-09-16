package com.ruoyi.integration.client.u8.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;

/** Validates U8 gateway targets and server-owned operation registrations before persistence. */
public final class U8GatewayTargetPolicy
{
    private final Set<String> allowedHosts;

    public U8GatewayTargetPolicy(Set<String> allowedHosts)
    {
        this.allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public ObjectNode validate(ObjectNode input)
    {
        if (input == null) throw rejected();
        ObjectNode valid = input.deepCopy();
        valid.put("connectionName", required(input, "connectionName", "[\\p{L}0-9 _.-]{1,100}"));
        valid.put("environment", required(input, "environment", "TEST|PROD"));
        valid.put("baseUrl", normalizeBaseUrl(required(input, "baseUrl", ".{1,500}")));
        valid.put("tokenPath", path(input, "tokenPath"));
        valid.put("tradeIdPath", path(input, "tradeIdPath"));
        valid.put("tokenPointer", pointer(input, "tokenPointer"));
        valid.put("tradeIdPointer", pointer(input, "tradeIdPointer"));
        valid.put("tokenParameterName", required(input, "tokenParameterName", "[A-Za-z][A-Za-z0-9_-]{0,49}"));
        valid.put("tradeIdParameterName", required(input, "tradeIdParameterName", "[A-Za-z][A-Za-z0-9_-]{0,49}"));
        int cacheSeconds = input.path("tokenCacheSeconds").asInt(0);
        if (cacheSeconds < 30 || cacheSeconds > 86400) throw rejected();
        valid.put("tokenCacheSeconds", cacheSeconds);
        int connectTimeout = input.path("connectTimeoutMillis").asInt(0);
        int readTimeout = input.path("readTimeoutMillis").asInt(0);
        if (connectTimeout < 1000 || connectTimeout > 30000 || readTimeout < 1000 || readTimeout > 60000) throw rejected();
        valid.put("connectTimeoutMillis", connectTimeout);
        valid.put("readTimeoutMillis", readTimeout);
        valid.set("operations", operations(input.path("operations")));
        return valid;
    }

    private ArrayNode operations(JsonNode node)
    {
        if (!node.isArray()) throw rejected();
        ArrayNode result = (ArrayNode) node.deepCopy();
        Set<String> codes = new HashSet<>();
        for (JsonNode operation : result)
        {
            if (!operation.isObject()) throw rejected();
            operation.fieldNames().forEachRemaining(name -> {
                if (!Set.of("code", "method", "path", "enabled").contains(name)) throw rejected();
            });
            String code = required((ObjectNode) operation, "code", "[A-Z][A-Z0-9_]{0,49}");
            String method = required((ObjectNode) operation, "method", "POST");
            String path = path((ObjectNode) operation, "path");
            if (!codes.add(code) || !"POST".equals(method) || !operation.path("enabled").isBoolean()) throw rejected();
            ((ObjectNode) operation).put("code", code).put("method", method).put("path", path);
        }
        return result;
    }

    private String path(JsonNode input, String name)
    {
        String value = required(input, name, ".{1,2000}");
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("?") || value.contains("://")) throw rejected();
        return value;
    }

    private String pointer(JsonNode input, String name)
    {
        String value = required(input, name, ".{0,500}");
        try
        {
            com.fasterxml.jackson.core.JsonPointer.compile(value);
            return value;
        }
        catch (IllegalArgumentException invalid) { throw rejected(); }
    }

    private String normalizeBaseUrl(String raw)
    {
        try
        {
            URI uri = new URI(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!("http".equals(scheme) || "https".equals(scheme)) || host.isEmpty() || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !(path.isEmpty() || "/".equals(path))
                    || !allowedHosts.contains(host) || uri.getPort() < -1 || uri.getPort() > 65535) throw rejected();
            return scheme + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        }
        catch (URISyntaxException | IllegalArgumentException invalid)
        {
            if (invalid instanceof ConfigurationException configuration) throw configuration;
            throw rejected();
        }
    }

    private static String required(JsonNode input, String name, String pattern)
    {
        String value = input.path(name).asText("").trim();
        if (!value.matches(pattern)) throw rejected();
        return value;
    }

    private static ConfigurationException rejected()
    {
        return new ConfigurationException("U8_GATEWAY_POLICY_REJECTED", 400, "U8账户或业务接口配置不符合安全策略");
    }
}
