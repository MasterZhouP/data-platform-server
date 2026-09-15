package com.ruoyi.integration.datasource.validation;

import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;

/** Validates structured SQL Server fields before a JDBC driver ever receives them. */
public final class DatasourcePolicy {
    private static final Set<String> OPTION_NAMES = Set.of("encrypt", "trustServerCertificate", "applicationName");

    public ObjectNode validate(ObjectNode input) {
        if (input == null || !"SQLSERVER".equals(input.path("type").asText())) {
            throw rejected("数据源类型仅支持 SQLSERVER");
        }
        String host = input.path("host").asText();
        if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}") || host.contains("..")) {
            throw rejected("数据库主机格式不正确");
        }
        int port = input.path("port").asInt(0);
        if (port < 1 || port > 65535) throw rejected("数据库端口必须在 1 到 65535 之间");
        String databaseName = input.path("databaseName").asText();
        if (!databaseName.matches("[A-Za-z0-9_$-]{1,128}")) throw rejected("数据库名称格式不正确");
        String username = input.path("username").asText();
        if (!username.matches("[A-Za-z0-9_.@\\-]{1,100}")) throw rejected("数据库账户格式不正确");
        JsonNode options = input.path("connectionOptions");
        if (!options.isMissingNode() && !options.isObject()) throw rejected("连接选项格式不正确");
        if (options.isObject()) {
            for (Map.Entry<String, JsonNode> option : options.properties()) {
                if (!OPTION_NAMES.contains(option.getKey())) throw rejected("存在不支持的连接选项");
                if (("encrypt".equals(option.getKey()) || "trustServerCertificate".equals(option.getKey()))
                        && !option.getValue().isBoolean()) throw rejected("布尔连接选项格式不正确");
                if ("applicationName".equals(option.getKey())
                        && (!option.getValue().isTextual() || !option.getValue().asText().matches("[A-Za-z0-9 _.-]{1,64}"))) {
                    throw rejected("应用名称格式不正确");
                }
            }
        }
        return input.deepCopy();
    }

    public String toJdbcUrl(ObjectNode config) {
        ObjectNode valid = validate(config);
        StringBuilder url = new StringBuilder("jdbc:sqlserver://")
                .append(valid.path("host").asText()).append(':').append(valid.path("port").asInt())
                .append(";databaseName=").append(valid.path("databaseName").asText());
        ObjectNode options = valid.with("connectionOptions");
        for (String key : OPTION_NAMES.stream().sorted().toList()) {
            if (options.has(key)) url.append(';').append(key).append('=').append(options.path(key).asText());
        }
        return url.toString();
    }

    private static ConfigurationException rejected(String message) {
        return new ConfigurationException("DATASOURCE_POLICY_REJECTED", 400, message);
    }
}
