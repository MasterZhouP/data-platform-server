package com.ruoyi.integration.execution.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 面向操作员的执行输出净化器。
 * <p>
 * 结果检查点可能携带仅为安全续跑准备的 U8 标量绑定；该字段不是业务结果，详情接口返回前必须移除，
 * 避免把外部系统的运行上下文扩散到页面和普通日志中。
 * </p>
 */
public class ExecutionResultOutputSanitizer
{
    private static final String U8_RESPONSE_CHECKPOINT_FIELD = "__u8Response";

    private final ObjectMapper json;

    public ExecutionResultOutputSanitizer()
    {
        this(new ObjectMapper());
    }

    public ExecutionResultOutputSanitizer(ObjectMapper json)
    {
        this.json = json;
    }

    public String sanitize(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return raw;
        }
        try
        {
            JsonNode parsed = json.readTree(raw);
            if (!(parsed instanceof ObjectNode node))
            {
                return null;
            }
            ObjectNode result = node.deepCopy();
            result.remove(U8_RESPONSE_CHECKPOINT_FIELD);
            return json.writeValueAsString(result);
        }
        catch (Exception ex)
        {
            return null;
        }
    }
}
