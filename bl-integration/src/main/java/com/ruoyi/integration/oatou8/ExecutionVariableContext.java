package com.ruoyi.integration.oatou8;

import java.util.Collection;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Runtime values produced by the fixed task skeleton.
 * SQL receives only scalar values from this context; complete objects and lists remain available to JSON rendering only.
 */
public record ExecutionVariableContext(Map<String, Object> trigger, Map<String, Object> constants,
        Map<String, Object> data, Map<String, Object> u8Response, Map<String, Object> result)
{
    public VariableValue lookup(String variableName)
    {
        Map<String, Object> namespace = namespace(variableName);
        if (namespace == null)
        {
            return VariableValue.missing();
        }
        String key = key(variableName);
        if (!namespace.containsKey(key))
        {
            return VariableValue.missing();
        }
        Object value = namespace.get(key);
        return new VariableValue(true, isScalar(value), value);
    }

    private Map<String, Object> namespace(String variableName)
    {
        if (variableName.startsWith("trigger.")) return trigger;
        if (variableName.startsWith("task.constants.")) return constants;
        if (variableName.startsWith("data.")) return data;
        if (variableName.startsWith("u8.response.")) return u8Response;
        if (variableName.startsWith("result.")) return result;
        return null;
    }

    private String key(String variableName)
    {
        if (variableName.startsWith("task.constants.")) return variableName.substring("task.constants.".length());
        if (variableName.startsWith("u8.response.")) return variableName.substring("u8.response.".length());
        return variableName.substring(variableName.indexOf('.') + 1);
    }

    private boolean isScalar(Object value)
    {
        return value == null || (!(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)
                && !value.getClass().isArray() && !(value instanceof JsonNode node && node.isContainerNode()));
    }

    public record VariableValue(boolean defined, boolean scalar, Object value)
    {
        static VariableValue missing()
        {
            return new VariableValue(false, false, null);
        }
    }
}
