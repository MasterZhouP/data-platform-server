package com.ruoyi.web.controller.integration.openapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * OA 插件调用平台的固定上下文。
 * 不接收 action、force 或任何步骤参数，任务行为只能由已发布的 taskCode 修订决定，避免调用方绕过平台安全边界。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record OpenApiExecutionRequest(String taskCode, String masterId, String formId, String summaryId)
{
    private static final Set<String> FIELDS = Set.of("taskCode", "masterId", "formId", "summaryId");

    /** 不能依赖全局 Jackson 的未知字段策略；开放接口必须自行拒绝任何动作覆盖或任务链参数。 */
    public static OpenApiExecutionRequest from(JsonNode source)
    {
        if (source == null || !source.isObject())
        {
            throw new IllegalArgumentException("请求体必须是JSON对象");
        }
        source.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field))
            {
                throw new IllegalArgumentException("请求包含未允许字段: " + field);
            }
        });
        return new OpenApiExecutionRequest(text(source, "taskCode", false), text(source, "masterId", false),
                text(source, "formId", true), text(source, "summaryId", true));
    }

    private static String text(JsonNode source, String field, boolean optional)
    {
        JsonNode value = source.get(field);
        if (value == null || value.isNull())
        {
            return optional ? null : "";
        }
        if (!value.isTextual())
        {
            throw new IllegalArgumentException(field + " 必须是文本");
        }
        return value.textValue();
    }
}
