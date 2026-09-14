package com.ruoyi.integration.oatou8;

import java.util.Collection;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 固定执行骨架产生的运行时上下文。
 * <p>
 * SQL 只能从这里取得标量值；完整对象和列表仅供 JSON 模板使用，避免把不确定的对象序列化后拼入 SQL。
 * </p>
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
        Object value = resolve(namespace, key(variableName));
        if (value == MissingValue.INSTANCE)
        {
            return VariableValue.missing();
        }
        return new VariableValue(true, isScalar(value), value);
    }

    /**
     * data.header.documentNo 这类路径必须逐层取值，才能让任务模板消费命名查询产生的对象；
     * 这里不支持反射、表达式或任意方法调用，运行时上下文始终是封闭的。
     */
    private Object resolve(Map<String, Object> namespace, String path)
    {
        if (namespace.containsKey(path))
        {
            return normalizeJsonLeaf(namespace.get(path));
        }
        Object current = namespace;
        for (String part : path.split("\\."))
        {
            if (current instanceof Map<?, ?> map)
            {
                if (!map.containsKey(part))
                {
                    return MissingValue.INSTANCE;
                }
                current = map.get(part);
            }
            else if (current instanceof JsonNode node && node.isObject() && node.has(part))
            {
                current = node.get(part);
            }
            else
            {
                return MissingValue.INSTANCE;
            }
        }
        return normalizeJsonLeaf(current);
    }

    private Object normalizeJsonLeaf(Object value)
    {
        if (!(value instanceof JsonNode node) || node.isContainerNode())
        {
            return value;
        }
        if (node.isNull()) return null;
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.numberValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        return node.asText();
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

    private enum MissingValue
    {
        INSTANCE
    }
}
