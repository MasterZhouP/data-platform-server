package com.ruoyi.integration.execution.support;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker
{
    private static final String MASK = "***";
    private static final String TRUNCATED = "...[truncated]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "token", "secret", "appkey", "authorization");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)(?:(?:bearer|basic)\\s+)?[^\\s,;]+", Pattern.MULTILINE);
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(password|passwd|(?:access|refresh)[_-]?token|token|client[_-]?secret|app[_-]?(?:key|secret)|api[_-]?key|secret)"
            + "(\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxLength;
    private final int errorMaxLength;

    @Autowired
    public SensitiveDataMasker(
            @Value("${integration.execution.payload-max-length:65535}") int maxLength,
            @Value("${integration.execution.error-max-length:2000}") int errorMaxLength)
    {
        this.maxLength = Math.max(maxLength, TRUNCATED.length());
        this.errorMaxLength = Math.max(errorMaxLength, TRUNCATED.length());
    }

    public SensitiveDataMasker(@Value("${integration.execution.payload-max-length:65535}") int maxLength)
    {
        this(maxLength, 2000);
    }

    public String mask(String value)
    {
        if (value == null)
        {
            return null;
        }
        String masked = maskJson(value);
        if (masked == null)
        {
            masked = AUTHORIZATION.matcher(value).replaceAll("$1" + MASK);
            masked = KEY_VALUE.matcher(masked).replaceAll("$1$2" + MASK);
        }
        return truncate(masked, maxLength);
    }

    public String maskError(String value)
    {
        return truncate(mask(value), errorMaxLength);
    }

    private String maskJson(String value)
    {
        try
        {
            JsonNode root = objectMapper.readTree(value);
            redact(root);
            return objectMapper.writeValueAsString(root);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private void redact(JsonNode node)
    {
        if (node == null)
        {
            return;
        }
        if (node.isObject())
        {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey()))
                {
                    object.put(field.getKey(), MASK);
                }
                else
                {
                    redact(field.getValue());
                }
            }
        }
        else if (node.isArray())
        {
            node.forEach(this::redact);
        }
    }

    private boolean isSensitive(String key)
    {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.equals("accesstoken")
                || normalized.equals("refreshtoken")
                || normalized.equals("clientsecret")
                || normalized.equals("appsecret")
                || normalized.equals("apikey");
    }

    private String truncate(String value, int limit)
    {
        if (value == null || value.length() <= limit)
        {
            return value;
        }
        return value.substring(0, limit - TRUNCATED.length()) + TRUNCATED;
    }
}
