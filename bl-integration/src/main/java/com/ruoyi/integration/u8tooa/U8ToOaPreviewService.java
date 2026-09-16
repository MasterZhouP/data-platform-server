package com.ruoyi.integration.u8tooa;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.ExecutionVariableContext;
import com.ruoyi.integration.oatou8.OaToU8PreviewInput;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfig;

/** Safe U8-to-OA draft preview; it never invokes OA or advances an incremental cursor. */
public class U8ToOaPreviewService
{
    private final Function<JsonNode, U8ToOaTaskConfig> parser;
    private final ReadOnlySqlExecutor sql;
    private final SqlVariableResolver variables;
    private final JsonTemplateRenderer renderer;
    private final ObjectMapper json;

    public U8ToOaPreviewService(Function<JsonNode, U8ToOaTaskConfig> parser, ReadOnlySqlExecutor sql,
            SqlVariableResolver variables, JsonTemplateRenderer renderer, ObjectMapper json)
    {
        this.parser = parser;
        this.sql = sql;
        this.variables = variables;
        this.renderer = renderer;
        this.json = json;
    }

    public U8ToOaPreview preview(JsonNode draftConfig, OaToU8PreviewInput trigger)
    {
        U8ToOaTaskConfig config = parser.apply(draftConfig);
        Map<String, Object> data = new LinkedHashMap<>();
        for (ReadQueryStep step : config.dataSteps())
        {
            ReadOnlySqlResult result = sql.execute(step.datasourceKey(), step.sql(), step.cardinality(),
                    variables.resolve(step.parameterBindings(), context(config, trigger, data)));
            data.put(step.code(), result.value());
        }
        JsonNode request = renderer.render(config.oa().payloadTemplate(), context(config, trigger, data));
        return new U8ToOaPreview(Collections.unmodifiableMap(new LinkedHashMap<>(data)), request);
    }

    private ExecutionVariableContext context(U8ToOaTaskConfig config, OaToU8PreviewInput trigger,
            Map<String, Object> data)
    {
        Map<String, Object> triggerValues = new LinkedHashMap<>();
        triggerValues.put("masterId", trigger.masterId());
        triggerValues.put("formId", trigger.formId());
        triggerValues.put("summaryId", trigger.summaryId());
        return new ExecutionVariableContext(Collections.unmodifiableMap(triggerValues), Map.copyOf(config.constants()),
                Collections.unmodifiableMap(new LinkedHashMap<>(data)), Map.of(), Map.of());
    }
}
