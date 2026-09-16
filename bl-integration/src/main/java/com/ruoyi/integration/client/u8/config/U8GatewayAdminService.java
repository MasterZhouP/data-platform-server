package com.ruoyi.integration.client.u8.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.configuration.RevisionToken;
import org.springframework.stereotype.Service;

/** Converts the U8 account form into a safe immutable revision and encrypted parameter set. */
@Service
public class U8GatewayAdminService
{
    private static final Set<String> REQUEST_FIELDS = Set.of("connectionName", "environment", "baseUrl", "tokenPath",
            "tradeIdPath", "tokenPointer", "tradeIdPointer", "tokenParameterName", "tradeIdParameterName",
            "tokenCacheSeconds", "connectTimeoutMillis", "readTimeoutMillis", "operations",
            "secretParametersUpdate", "expectedRevision", "operationId");

    private final U8GatewayCatalog catalog;
    private final U8GatewaySecretStore secrets;
    private final U8GatewayTargetPolicy policy;
    private final ObjectMapper json;

    public U8GatewayAdminService(U8GatewayCatalog catalog, U8GatewaySecretStore secrets,
            U8GatewayTargetPolicy policy, ObjectMapper json)
    {
        this.catalog = catalog;
        this.secrets = secrets;
        this.policy = policy;
        this.json = json;
    }

    public RevisionToken saveDraft(String key, ObjectNode request)
    {
        if (!U8GatewayReadService.DEFAULT_KEY.equals(key))
        {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "U8公共账户仅支持唯一活动账户");
        }
        if (request == null) throw new ConfigurationException("INVALID_ARGUMENT", 400, "请求体不能为空");
        request.fieldNames().forEachRemaining(field -> {
            if (!REQUEST_FIELDS.contains(field)) throw new ConfigurationException("INVALID_ARGUMENT", 400, "存在不支持的U8账户配置字段");
        });
        ObjectNode fields = json.createObjectNode();
        copy(request, fields, "connectionName", "environment", "baseUrl", "tokenPath", "tradeIdPath", "tokenPointer",
                "tradeIdPointer", "tokenParameterName", "tradeIdParameterName", "tokenCacheSeconds",
                "connectTimeoutMillis", "readTimeoutMillis", "operations");
        ObjectNode valid = policy.validate(fields);
        RevisionToken candidate = new RevisionToken(UUID.randomUUID().toString());
        Map<String, String> updated = secretUpdates(request.path("secretParametersUpdate"));
        U8GatewayRevision current = currentSecretRevision(key);
        Map<String, String> parameters = current == null ? new LinkedHashMap<>()
                : secrets.read(key, new RevisionToken(current.revisionId()), current.secretId());
        try
        {
            if (updated.isEmpty() && parameters.isEmpty())
            {
                throw new ConfigurationException("SECRET_REQUIRED", 400, "请首次填写U8账户认证参数");
            }
            parameters.putAll(updated);
            ArrayNode names = valid.putArray("accountParameterNames");
            Set<String> allNames = new LinkedHashSet<>(parameters.keySet());
            allNames.forEach(names::add);
            String secretId = secrets.store(key, candidate, parameters);
            return catalog.saveDraft(key, optionalText(request, "expectedRevision"), valid, secretId, candidate);
        }
        finally
        {
            parameters.clear();
            updated.clear();
        }
    }

    private U8GatewayRevision currentSecretRevision(String key)
    {
        try
        {
            return catalog.currentSecretRevision(key);
        }
        catch (ConfigurationException absent) 
        {
            if ("CONFIGURATION_NOT_FOUND".equals(absent.code())) return null;
            throw absent;
        }
    }

    private Map<String, String> secretUpdates(JsonNode node)
    {
        if (node == null || node.isMissingNode() || node.isNull()) return new LinkedHashMap<>();
        if (!node.isObject()) throw new ConfigurationException("INVALID_ARGUMENT", 400, "U8账户认证参数格式不正确");
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            String value = entry.getValue().asText("");
            if (!entry.getValue().isTextual() || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}")
                    || value.isBlank() || value.length() > 2000)
            {
                throw new ConfigurationException("INVALID_ARGUMENT", 400, "U8账户认证参数格式不正确");
            }
            values.put(name, value);
        });
        return values;
    }

    private static void copy(ObjectNode source, ObjectNode target, String... fields)
    {
        for (String field : fields)
        {
            JsonNode value = source.get(field);
            if (value != null && !value.isNull()) target.set(field, value.deepCopy());
        }
    }

    private static String optionalText(ObjectNode source, String key)
    {
        String value = source.path(key).asText("").trim();
        return value.isEmpty() ? null : value;
    }
}
