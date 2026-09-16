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
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.u8.U8CallResult;
import com.ruoyi.integration.client.u8.U8CallStatus;
import com.ruoyi.integration.client.u8.U8Gateway;
import com.ruoyi.integration.client.u8.runtime.U8GatewayLease;
import com.ruoyi.integration.client.u8.runtime.U8GatewayRegistry;
import com.ruoyi.integration.configuration.ConfigurationException;
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
    /**
     * 仅用于执行检查点的内部字段。它保存结果查询声明引用的 U8 标量，
     * 不属于对操作员展示的业务输出，恢复时也绝不能把完整 U8 响应重新落库。
     */
    private static final String U8_RESPONSE_CHECKPOINT_FIELD = "__u8Response";

    private final Function<JsonNode, OaToU8TaskConfig> configParser;
    private final ReadOnlySqlExecutor sql;
    private final SqlVariableResolver sqlVariables;
    private final JsonTemplateRenderer templateRenderer;
    private final U8Gateway gateway;
    private final U8GatewayRegistry gatewayRegistry;
    private final JsonResponseEvaluator responseEvaluator;
    private final ExecutionRepository repository;
    private final ObjectMapper json;

    public OaToU8TaskExecutor(Function<JsonNode, OaToU8TaskConfig> configParser, ReadOnlySqlExecutor sql,
            SqlVariableResolver sqlVariables, JsonTemplateRenderer templateRenderer, U8Gateway gateway,
            JsonResponseEvaluator responseEvaluator, ExecutionRepository repository, ObjectMapper json)
    {
        this(configParser, sql, sqlVariables, templateRenderer, gateway, null, responseEvaluator, repository, json);
    }

    public OaToU8TaskExecutor(Function<JsonNode, OaToU8TaskConfig> configParser, ReadOnlySqlExecutor sql,
            SqlVariableResolver sqlVariables, JsonTemplateRenderer templateRenderer, U8GatewayRegistry gatewayRegistry,
            JsonResponseEvaluator responseEvaluator, ExecutionRepository repository, ObjectMapper json)
    {
        this(configParser, sql, sqlVariables, templateRenderer, null, gatewayRegistry, responseEvaluator, repository, json);
    }

    private OaToU8TaskExecutor(Function<JsonNode, OaToU8TaskConfig> configParser, ReadOnlySqlExecutor sql,
            SqlVariableResolver sqlVariables, JsonTemplateRenderer templateRenderer, U8Gateway gateway,
            U8GatewayRegistry gatewayRegistry, JsonResponseEvaluator responseEvaluator,
            ExecutionRepository repository, ObjectMapper json)
    {
        this.configParser = configParser;
        this.sql = sql;
        this.sqlVariables = sqlVariables;
        this.templateRenderer = templateRenderer;
        this.gateway = gateway;
        this.gatewayRegistry = gatewayRegistry;
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
            CheckpointState checkpoint = checkpointStateFrom(execution.getResultOutputsJson());
            runResultQueries(execution, config, Map.of(), checkpoint.u8Response(), checkpoint.outputs(), recorder);
            return PushResult.success(execution.getMasterId(), null, write(checkpoint.outputs()));
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
        ObjectNode u8Response = responseBindingsSnapshot(config, responseJson);
        // 该检查点先于结果轮询写入；任何后续失败都只能续跑，不得再次调用 U8。
        repository.checkpointU8Confirmed(execution.getExecutionId(), write(checkpointState(outputs, u8Response)), "U8_CONFIRMED");
        recorder.succeeded("U8_CONFIRMED", write(outputs));
        runResultQueries(execution, config, data, u8Response, outputs, recorder);
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
        U8CallResult response;
        try
        {
            if (gatewayRegistry != null)
            {
                try (U8GatewayLease lease = gatewayRegistry.acquire("u8-default"))
                {
                    response = lease.gateway().postBusiness(config.u8().operationCode(), config.u8().path(), request);
                }
            }
            else if (gateway != null)
            {
                response = gateway.postBusiness(config.u8().operationCode(), config.u8().path(), request);
            }
            else
            {
                throw new ConfigurationException("U8_GATEWAY_UNAVAILABLE", 503, "U8公共账户尚未启用");
            }
        }
        catch (ConfigurationException unavailable)
        {
            recorder.failed("U8_REQUEST_SENDING", unavailable.code(), unavailable.getMessage());
            throw PushFailureException.retryable(unavailable.code(), unavailable.getMessage());
        }
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
            ObjectNode u8Response, ObjectNode outputs, ExecutionStageRecorder recorder)
    {
        String lastStage = execution.getLastCompletedStage() == null ? "U8_CONFIRMED" : execution.getLastCompletedStage();
        for (ResultQueryStep step : config.resultQueries())
        {
            boolean ready = false;
            for (int attempt = 1; attempt <= step.maxAttempts(); attempt++)
            {
                String stage = "RESULT_" + step.code() + "_ATTEMPT_" + attempt;
                lastStage = stage;
                waitBeforeAttempt(step, attempt, stage, checkpointState(outputs, u8Response));
                recorder.started(stage, "{\"datasourceKey\":\"" + step.datasourceKey() + "\"}");
                try
                {
                    ExecutionVariableContext context = context(execution, config, data, map(u8Response), map(outputs));
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
                        lastStage, write(checkpointState(outputs, u8Response)));
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

    private void waitBeforeAttempt(ResultQueryStep step, int attempt, String stage, ObjectNode checkpoint)
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
                    stage, write(checkpoint));
        }
    }

    /**
     * U8 调用确认成功后，只保存结果查询明确声明需要的标量响应字段；
     * 这样部分成功的续跑仍能绑定 u8.response.*，又不会把整段外部响应作为长期业务数据保存。
     */
    private ObjectNode responseBindingsSnapshot(OaToU8TaskConfig config, JsonNode response)
    {
        ObjectNode snapshot = json.createObjectNode();
        for (ResultQueryStep step : config.resultQueries())
        {
            for (String variable : step.parameterBindings().values())
            {
                if (!variable.startsWith("u8.response."))
                {
                    continue;
                }
                JsonNode value = responsePath(response, variable.substring("u8.response.".length()));
                if (!value.isMissingNode() && value.isValueNode())
                {
                    putPath(snapshot, variable.substring("u8.response.".length()), value);
                }
            }
        }
        return snapshot;
    }

    private JsonNode responsePath(JsonNode response, String path)
    {
        JsonNode current = response;
        for (String segment : path.split("\\."))
        {
            if (!current.isObject())
            {
                return MissingNode.getInstance();
            }
            current = current.path(segment);
        }
        return current;
    }

    private void putPath(ObjectNode target, String path, JsonNode value)
    {
        String[] segments = path.split("\\.");
        ObjectNode current = target;
        for (int index = 0; index < segments.length - 1; index++)
        {
            JsonNode existing = current.get(segments[index]);
            if (!(existing instanceof ObjectNode))
            {
                existing = current.putObject(segments[index]);
            }
            current = (ObjectNode) existing;
        }
        current.set(segments[segments.length - 1], value.deepCopy());
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

    private CheckpointState checkpointStateFrom(String raw)
    {
        try
        {
            JsonNode value = raw == null ? json.createObjectNode() : json.readTree(raw);
            ObjectNode state = value instanceof ObjectNode node ? node.deepCopy() : json.createObjectNode();
            JsonNode savedResponse = state.remove(U8_RESPONSE_CHECKPOINT_FIELD);
            ObjectNode u8Response = savedResponse instanceof ObjectNode node ? node.deepCopy() : json.createObjectNode();
            return new CheckpointState(state, u8Response);
        }
        catch (JsonProcessingException ex)
        {
            throw new IllegalStateException("执行检查点输出不是有效JSON", ex);
        }
    }

    private ObjectNode checkpointState(ObjectNode outputs, ObjectNode u8Response)
    {
        ObjectNode state = outputs.deepCopy();
        if (!u8Response.isEmpty())
        {
            state.set(U8_RESPONSE_CHECKPOINT_FIELD, u8Response.deepCopy());
        }
        return state;
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

    private record CheckpointState(ObjectNode outputs, ObjectNode u8Response) { }
}
