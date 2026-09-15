package com.ruoyi.integration.client.oa.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;

/** Validates an OA service root before any HTTP client is constructed. */
public final class OaRestTargetPolicy {
    private final Set<String> allowedHosts;

    public OaRestTargetPolicy(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? Set.of() : allowedHosts.stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public ObjectNode validate(ObjectNode input) {
        if (input == null) throw rejected();
        ObjectNode valid = input.deepCopy();
        valid.put("connectionName", required(input, "connectionName", "[\\p{L}0-9 _.-]{1,100}"));
        String environment = required(input, "environment", "TEST|PROD");
        valid.put("environment", environment);
        valid.put("baseUrl", normalizeBaseUrl(required(input, "baseUrl", ".{1,500}")));
        valid.put("restUsername", required(input, "restUsername", "[A-Za-z0-9_.@\\-]{1,100}"));
        valid.put("loginName", required(input, "loginName", "[A-Za-z0-9_.@\\-]{1,100}"));
        int connectTimeout = input.path("connectTimeoutMs").asInt(0);
        int readTimeout = input.path("readTimeoutMs").asInt(0);
        if (connectTimeout < 1000 || connectTimeout > 30000 || readTimeout < 1000 || readTimeout > 60000) {
            throw rejected();
        }
        valid.put("connectTimeoutMs", connectTimeout);
        valid.put("readTimeoutMs", readTimeout);
        return valid;
    }

    private String normalizeBaseUrl(String raw) {
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!("http".equals(scheme) || "https".equals(scheme)) || host.isEmpty()
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(path.isEmpty() || "/".equals(path)) || !allowedHosts.contains(host)
                    || uri.getPort() < -1 || uri.getPort() > 65535) {
                throw rejected();
            }
            return scheme + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            if (invalid instanceof ConfigurationException configuration) throw configuration;
            throw rejected();
        }
    }

    private static String required(ObjectNode input, String name, String pattern) {
        String value = input.path(name).asText("").trim();
        if (!value.matches(pattern)) throw rejected();
        return value;
    }

    private static ConfigurationException rejected() {
        return new ConfigurationException("OA_TARGET_REJECTED", 400,
                "OA 服务根地址、账户或超时配置不符合安全策略");
    }
}
