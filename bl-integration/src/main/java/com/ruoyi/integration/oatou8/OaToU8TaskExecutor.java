package com.ruoyi.integration.oatou8;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.function.Function;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.u8.U8CallResult;
import com.ruoyi.integration.client.u8.U8CallStatus;
import com.ruoyi.integration.client.u8.U8Gateway;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.oatou8.config.OaToU8TaskConfig;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.config.ResultQueryStep;
import com.ruoyi.integration.oatou8.template.JsonResponseEvaluator;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.oatou8.template.ResponseEvaluation;
import com.ruoyi.integration.oatou8.template.ResponseEvaluationException;
import com.ruoyi.integration.oatou8.template.TemplateRenderException;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlExecutionException;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TaskExecutor;
import com.ruoyi.integration.taskdefinition.TaskType;

/**
 * 页面配置的 OA→U8 通用执行器。
 * <p>
 * 其顺序固定为只读数据准备、JSON 渲染、一次 U8 业务调用、只读结果查询；不提供脚本、分支或写库能力，
 * 因此能复用执行底座，又不会演变成任意步骤编排工具。
 * </p>
 */
public class OaToU8TaskExecutor implements TaskExecutor
{
    private final Function<JsonNode, OaToU8TaskConfig> configParser;
    private final ReadOnlySqlExecutor sql;
    private final SqlVariableResolver sqlVariables;
    private final JsonTemplateRenderer templateRenderer;
    private final U8Gateway gateway;
    private final JsonResponseEvaluator responseEvaluator;
    private final ExecutionRepository repository;
    private final ObjectMapper json;

    public OaToU8TaskExecutor(Function<JsonNode, OaToU8TaskConfig> configParser, ReadOnlySqlExecutor sql,
            SqlVariableResolver sqlVariables, JsonTemplateRenderer templateRenderer, U8Gateway gateway,
            JsonResponseEvaluator responseEvaluator, ExecutionRepository repository, ObjectMapper json)
    {
        this.configParser = configParser;
        this.sql = sql;
        this.sqlVariables = sqlVariables;
        this.templateRenderer = templateRenderer;
        this.gateway = gateway;
        this.responseEvaluator = responseEvaluator;
        this.repository = repository;
        this.json = json;
    }

    @Override
    public TaskType taskType()
    {
        return TaskType.OA_TO_U8;
    }

    @Override
    public PushResult execute(ResolvedExecution resolved, ExecutionStageRecorder recorder)
    {
        IntegrationExecution execution = resolved.execution();
        OaToU8TaskConfig config = configParser.apply(resolved.revision().config());
        if ("POST_PROCESS".equals(execution.getResumeMode()))
        {
            // 已确认 U8 的重试永远跳过数据准备、模板渲染和业务调用，避免重复生成单据。
            ObjectNode outputs = outputsFrom(execution.getResultOutputsJson());
            runResultQueries(execution, config, Map.of(), outputs, recorder);
            return PushResult.success(execution.getMasterId(), null, write(outputs));
        }

        Map<String, Object> data = runDataSteps(execution, config, recorder);
        JsonNode request = renderRequest(execution, config, data, recorder);
        U8CallResult response = callU8(config, request, recorder);
        JsonNode responseJson = parseConfirmedResponse(response);
        ResponseEvaluation evaluation;
        try
        {
            evaluation = responseEvaluator.evaluate(responseJson, config.u8().successRule(), config.u8().outputs(),
                    config.u8().errorMessagePointer());
        }
        catch (ResponseEvaluationException ex)
        {
            throw PushFailureException.nonRetryable(ex.code(), ex.getMessage());
        }

        ObjectNode outputs = evaluation.outputs();
        // 该检查点先于结果轮询写入；任何后续失败都只能续跑，不得再次调用 U8。
        repository.checkpointU8Confirmed(execution.getExecutionId(), write(outputs), "U8_CONFIRMED");
        recorder.succeeded("U8_CONFIRMED", write(outputs));
        runResultQueries(execution, config, data, outputs, recorder);
        return PushResult.success(execution.getMasterId(), write(request), write(outputs));
    }

    private Map<String, Object> runDataSteps(IntegrationExecution execution, OaToU8TaskConfig config,
            ExecutionStageRecorder recorder)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        for (ReadQueryStep step : config.dataSteps())
        {
            String stage = "DATA_" + step.code();
            recorder.started(stage, "{\"datasourceKey\":\"" + step.datasourceKey() + "\"}");
            try
            {
                ExecutionVariableContext context = context(execution, config, data, Map.of(), Map.of());
                ReadOnlySqlResult result = sql.execute(step.datasourceKey(), step.sql(), step.cardinality(),
                        sqlVariables.resolve(step.parameterBindings(), context));
                data.put(step.code(), result.value());
                recorder.succeeded(stage, "{\"cardinality\":\"" + step.cardinality() + "\"}");
            }
            catch (SqlExecutionException ex)
            {
                recorder.failed(stage, ex.code(), ex.getMessage());
                throw PushFailureException.retryable(ex.code(), ex.getMessage());
            }
        }
        return immutableContext(data);
    }

    private JsonNode renderRequest(IntegrationExecution execution, OaToU8TaskConfig config, Map<String, Object> data,
            ExecutionStageRecorder recorder)
    {
        recorder.started("U8_REQUEST_RENDERED", null);
        try
        {
            JsonNode template = json.readTree(config.u8().requestJsonTemplate());
            JsonNode rendered = templateRenderer.render(template, context(execution, config, data, Map.of(), Map.of()));
            recorder.succeeded("U8_REQUEST_RENDERED", write(rendered));
            return rendered;
        }
        catch (JsonProcessingException | TemplateRenderException ex)
        {
            recorder.failed("U8_REQUEST_RENDERED", "U8_TEMPLATE_RENDER_FAILED", ex.getMessage());
            throw PushFailureException.nonRetryable("U8_TEMPLATE_RENDER_FAILED", ex.getMessage());
        }
    }

    private U8CallResult callU8(OaToU8TaskConfig config, JsonNode request, ExecutionStageRecorder recorder)
    {
        recorder.started("U8_REQUEST_SENDING", write(request));
        U8CallResult response = gateway.postBusiness(config.u8().operationCode(), config.u8().path(), request);
        if (response.status() == U8CallStatus.SUCCESS)
        {
            recorder.succeeded("U8_REQUEST_SENDING", response.responsePayload());
            return response;
        }
        recorder.failed("U8_REQUEST_SENDING", response.errorCode(), response.errorMessage());
        if (response.status() == U8CallStatus.RESULT_UNKNOWN)
        {
            throw PushFailureException.resultUnknown(response.errorCode(), response.errorMessage());
        }
        if (response.status() == U8CallStatus.PRE_SEND_FAILURE)
        {
            throw PushFailureException.retryable(response.errorCode(), response.errorMessage());
        }
        throw PushFailureException.nonRetryable(response.errorCode(), response.errorMessage());
    }

    private JsonNode parseConfirmedResponse(U8CallResult response)
    {
        try
        {
            return json.readTree(response.responsePayload());
        }
        catch (Exception ex)
        {
            // HTTP 成功但响应无法判定业务结果时，U8 可能已写入单据，必须禁止自动重推。
            throw PushFailureException.resultUnknown("U8_RESPONSE_INVALID", "U8响应无法确认业务结果");
        }
    }

    private void runResultQueries(IntegrationExecution execution, OaToU8TaskConfig config, Map<String, Object> data,
            ObjectNode outputs, ExecutionStageRecorder recorder)
    {
        String lastStage = execution.getLastCompletedStage() == null ? "U8_CONFIRMED" : execution.getLastCompletedStage();
        for (ResultQueryStep step : config.resultQueries())
        {
            boolean ready = false;
            for (int attempt = 1; attempt <= step.maxAttempts(); attempt++)
            {
                waitBeforeAttempt(step, attempt);
                String stage = "RESULT_" + step.code() + "_ATTEMPT_" + attempt;
                lastStage = stage;
                recorder.started(stage, "{\"datasourceKey\":\"" + step.datasourceKey() + "\"}");
                try
                {
                    ExecutionVariableContext context = context(execution, config, data, map(outputs), map(outputs));
                    ReadOnlySqlResult result = sql.execute(step.datasourceKey(), step.sql(), step.cardinality(),
                            sqlVariables.resolve(step.parameterBindings(), context));
                    ready = extractResultOutputs(result.value(), step.outputMappings(), outputs);
                    if (ready)
                    {
                        recorder.succeeded(stage, write(outputs));
                        break;
                    }
                    recorder.failed(stage, "RESULT_NOT_READY", "U8结果尚未满足任务定义的输出条件");
                }
                catch (SqlExecutionException ex)
                {
                    recorder.failed(stage, ex.code(), ex.getMessage());
                }
            }
            if (!ready && step.required())
            {
                throw new PostProcessPendingException("RESULT_NOT_READY", "U8确认成功，但必需结果尚未就绪",
                        lastStage, write(outputs));
            }
        }
    }

    private boolean extractResultOutputs(JsonNode result, Map<String, String> mappings, ObjectNode outputs)
    {
        for (Map.Entry<String, String> mapping : mappings.entrySet())
        {
            JsonNode value = result.at(JsonPointer.compile(mapping.getValue()));
            if (value.isMissingNode() || value.isNull())
            {
                return false;
            }
            outputs.set(mapping.getKey(), value.deepCopy());
        }
        return true;
    }

    private void waitBeforeAttempt(ResultQueryStep step, int attempt)
    {
        int delay = attempt == 1 ? step.initialDelayMs() : step.intervalMs();
        if (delay == 0)
        {
            return;
        }
        try
        {
            Thread.sleep(delay);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new PostProcessPendingException("POST_PROCESS_INTERRUPTED", "结果查询等待被中断",
                    "RESULT_" + step.code() + "_ATTEMPT_" + attempt, "{}");
        }
    }

    private ExecutionVariableContext context(IntegrationExecution execution, OaToU8TaskConfig config,
            Map<String, Object> data, Map<String, Object> u8Response, Map<String, Object> result)
    {
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("masterId", execution.getMasterId());
        trigger.put("formId", execution.getFormId());
        trigger.put("summaryId", execution.getSummaryId());
        // 常量来自页面配置，原始类型是字符串；运行时上下文统一以 Object 承载，才能与查询结果一起安全解析。
        Map<String, Object> constants = new LinkedHashMap<>();
        constants.putAll(config.constants());
        // formId、summaryId 在统一 OA 受理协议中可选，运行上下文必须保留其 null 语义而不能在复制时抛错。
        return new ExecutionVariableContext(immutableContext(trigger), immutableContext(constants),
                immutableContext(data), immutableContext(u8Response), immutableContext(result));
    }

    private ObjectNode outputsFrom(String raw)
    {
        try
        {
            JsonNode value = raw == null ? json.createObjectNode() : json.readTree(raw);
            return value instanceof ObjectNode node ? node.deepCopy() : json.createObjectNode();
        }
        catch (JsonProcessingException ex)
        {
            throw new IllegalStateException("执行检查点输出不是有效JSON", ex);
        }
    }

    private Map<String, Object> map(JsonNode node)
    {
        if (node == null || !node.isObject())
        {
            return Map.of();
        }
        return json.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private Map<String, Object> immutableContext(Map<String, Object> values)
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private String write(JsonNode value)
    {
        try
        {
            return json.writeValueAsString(value);
        }
        catch (JsonProcessingException ex)
        {
            throw new IllegalStateException("任务运行输出无法序列化", ex);
        }
    }
}
