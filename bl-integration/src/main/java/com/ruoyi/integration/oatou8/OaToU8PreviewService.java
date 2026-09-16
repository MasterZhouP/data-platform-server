package com.ruoyi.integration.oatou8;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.config.OaToU8TaskConfig;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlVariableResolver;

/**
 * OA→U8 草稿的安全调试服务。
 * 预览只执行已校验的只读数据准备和 JSON 模板渲染，类中刻意没有 U8Gateway 依赖，
 * 从结构上杜绝“点击预览”意外推送 U8 单据。
 */
public class OaToU8PreviewService
{
    private final TaskConfigValidator validator;
    private final ReadOnlySqlExecutor sql;
    private final SqlVariableResolver variables;
    private final JsonTemplateRenderer renderer;
    private final ObjectMapper json;

    public OaToU8PreviewService(TaskConfigValidator validator, ReadOnlySqlExecutor sql,
            SqlVariableResolver variables, JsonTemplateRenderer renderer, ObjectMapper json)
    {
        this.validator = validator;
        this.sql = sql;
        this.variables = variables;
        this.renderer = renderer;
        this.json = json;
    }

    public OaToU8Preview preview(JsonNode draftConfig, OaToU8PreviewInput trigger)
    {
        OaToU8TaskConfig config = validator.parseAndValidate(draftConfig);
        Map<String, Object> prepared = new LinkedHashMap<>();
        for (ReadQueryStep step : config.dataSteps())
        {
            // 与正式执行使用同一套参数绑定和只读 SQL 边界，保证预览结果可解释且不会扩大权限。
            ReadOnlySqlResult result = sql.execute(step.datasourceKey(), step.sql(), step.cardinality(),
                    variables.resolve(step.parameterBindings(), context(config, trigger, prepared)));
            prepared.put(step.code(), result.value());
        }
        try
        {
            JsonNode template = json.readTree(config.u8().requestJsonTemplate());
            return new OaToU8Preview(Map.copyOf(prepared), renderer.render(template, context(config, trigger, prepared)));
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("U8请求模板无法完成预览: " + ex.getMessage(), ex);
        }
    }

    private ExecutionVariableContext context(OaToU8TaskConfig config, OaToU8PreviewInput trigger,
            Map<String, Object> prepared)
    {
        Map<String, Object> triggerValues = new LinkedHashMap<>();
        triggerValues.put("masterId", trigger.masterId());
        triggerValues.put("formId", trigger.formId());
        triggerValues.put("summaryId", trigger.summaryId());
        Map<String, Object> constants = new LinkedHashMap<>();
        constants.putAll(config.constants());
        // formId/summaryId are optional in the OA protocol; keep their null semantics without Map.copyOf rejecting them.
        return new ExecutionVariableContext(Collections.unmodifiableMap(new LinkedHashMap<>(triggerValues)), Map.copyOf(constants), Map.copyOf(prepared),
                Map.of(), Map.of());
    }
}
