package com.ruoyi.integration.u8tooa;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.oa.OaClientException;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.oatou8.ExecutionVariableContext;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.oatou8.template.TemplateRenderException;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlExecutionException;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLink;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkRepository;
import com.ruoyi.integration.sync.salesoutbound.link.ProcessLinkState;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TaskExecutor;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfig;

/** Fixed, retry-safe execution skeleton shared by every configured U8-to-OA task. */
public class U8ToOaTaskExecutor implements TaskExecutor
{
    private final Function<JsonNode, U8ToOaTaskConfig> configParser;
    private final ReadOnlySqlExecutor sql;
    private final SqlVariableResolver sqlVariables;
    private final JsonTemplateRenderer templateRenderer;
    private final OaProcessOperations oa;
    private final OaProcessLinkRepository links;
    private final ObjectMapper json;

    public U8ToOaTaskExecutor(Function<JsonNode, U8ToOaTaskConfig> configParser, ReadOnlySqlExecutor sql,
            SqlVariableResolver sqlVariables, JsonTemplateRenderer templateRenderer, OaProcessOperations oa,
            OaProcessLinkRepository links, ObjectMapper json)
    {
        this.configParser = configParser;
        this.sql = sql;
        this.sqlVariables = sqlVariables;
        this.templateRenderer = templateRenderer;
        this.oa = oa;
        this.links = links;
        this.json = json;
    }

    @Override
    public TaskType taskType()
    {
        return TaskType.U8_TO_OA;
    }

    @Override
    public PushResult execute(ResolvedExecution resolved, ExecutionStageRecorder recorder)
    {
        IntegrationExecution execution = resolved.execution();
        U8ToOaTaskConfig config = configParser.apply(resolved.revision().config());
        TaskAction action = TaskAction.valueOf(execution.getOperation());
        OaProcessLink latest = links.findLatest(execution.getTaskCode(), execution.getMasterId());
        if (action == TaskAction.DELETE)
        {
            return delete(execution, latest, recorder);
        }
        if (action == TaskAction.CREATE && latest != null && latest.getState() == ProcessLinkState.ACTIVE)
        {
            return PushResult.skipped(execution.getMasterId(), "已有有效OA流程");
        }

        Prepared prepared;
        if (action == TaskAction.CANCEL_RECREATE && latest != null && latest.getState() == ProcessLinkState.ACTIVE)
        {
            // Build the replacement before the irreversible cancel side effect.
            prepared = load(execution, config, recorder);
            cancel(latest, ProcessLinkState.CANCELLED_PENDING_RECREATE, recorder);
            latest = links.findLatest(execution.getTaskCode(), execution.getMasterId());
        }
        else
        {
            ensureSafeToCreate(latest);
            prepared = load(execution, config, recorder);
        }
        ensureSafeToCreate(latest);
        OaProcessLink creating = links.createCreating(execution.getTaskCode(), execution.getMasterId(), prepared.u8Id(),
                latest == null ? null : latest.getLinkId());
        return start(execution.getMasterId(), creating, prepared.payload(), recorder);
    }

    private Prepared load(IntegrationExecution execution, U8ToOaTaskConfig config, ExecutionStageRecorder recorder)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        for (ReadQueryStep step : config.dataSteps())
        {
            String stage = "DATA_" + step.code();
            recorder.started(stage, "{\"datasourceKey\":\"" + step.datasourceKey() + "\"}");
            try
            {
                ExecutionVariableContext context = context(execution, config, data);
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

        recorder.started("OA_REQUEST_RENDERED", null);
        try
        {
            ExecutionVariableContext context = context(execution, config, data);
            JsonNode rendered = templateRenderer.render(config.oa().payloadTemplate(), context);
            ExecutionVariableContext.VariableValue id = context.lookup(config.oa().u8IdVariable());
            if (!id.defined() || !id.scalar() || id.value() == null || String.valueOf(id.value()).isBlank())
            {
                throw PushFailureException.nonRetryable("U8_ID_NOT_FOUND", "OA流程关联所需的U8主键变量不存在或为空");
            }
            Map<String, Object> payload = json.convertValue(rendered, new TypeReference<LinkedHashMap<String, Object>>() { });
            recorder.succeeded("OA_REQUEST_RENDERED", write(rendered));
            return new Prepared(String.valueOf(id.value()), payload);
        }
        catch (PushFailureException ex)
        {
            recorder.failed("OA_REQUEST_RENDERED", ex.getErrorCode(), ex.getMessage());
            throw ex;
        }
        catch (TemplateRenderException | IllegalArgumentException ex)
        {
            recorder.failed("OA_REQUEST_RENDERED", "OA_TEMPLATE_RENDER_FAILED", ex.getMessage());
            throw PushFailureException.nonRetryable("OA_TEMPLATE_RENDER_FAILED", ex.getMessage());
        }
    }

    private PushResult delete(IntegrationExecution execution, OaProcessLink latest, ExecutionStageRecorder recorder)
    {
        if (latest == null || latest.getState() == ProcessLinkState.CANCELLED)
        {
            return PushResult.skipped(execution.getMasterId(), "没有需要撤销的OA流程");
        }
        if (latest.getState() == ProcessLinkState.CREATE_FAILED
                || latest.getState() == ProcessLinkState.CANCELLED_PENDING_RECREATE)
        {
            links.updateState(latest.getLinkId(), ProcessLinkState.CANCELLED);
            return PushResult.skipped(execution.getMasterId(), "没有活动的OA流程，已归一为撤销状态");
        }
        if (latest.getState() != ProcessLinkState.ACTIVE)
        {
            throw PushFailureException.resultUnknown("OA_LINK_UNCERTAIN", "OA流程关联状态待人工核对: " + latest.getState());
        }
        cancel(latest, ProcessLinkState.CANCELLED, recorder);
        return PushResult.success(execution.getMasterId(), null,
                write(Map.of("summaryId", latest.getSummaryId(), "cancelled", true)));
    }

    private void cancel(OaProcessLink link, ProcessLinkState successState, ExecutionStageRecorder recorder)
    {
        recorder.started("CANCEL_OLD", write(Map.of("summaryId", link.getSummaryId())));
        try
        {
            links.updateState(link.getLinkId(), ProcessLinkState.CANCELING);
            oa.cancel(new OaProcessRef(link.getSummaryId(), link.getAffairId(), link.getProcessId()));
        }
        catch (OaClientException ex)
        {
            if (!ex.isResultUnknown())
            {
                try { links.updateState(link.getLinkId(), ProcessLinkState.ACTIVE); }
                catch (RuntimeException persistenceFailure)
                {
                    throw PushFailureException.resultUnknown("OA_LINK_PERSIST_FAILED", "OA撤销失败且本地状态恢复失败，请人工核对");
                }
            }
            throw failure(ex);
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.retryable("OA_CANCEL_FAILED", "OA流程撤销失败: " + ex.getMessage());
        }
        try
        {
            links.updateState(link.getLinkId(), successState);
            recorder.succeeded("CANCEL_OLD", "{\"cancelled\":true}");
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.resultUnknown("OA_LINK_PERSIST_FAILED", "OA已确认撤销成功，但本地流程状态保存失败，请人工核对");
        }
    }

    private PushResult start(String businessKey, OaProcessLink creating, Map<String, Object> payload,
            ExecutionStageRecorder recorder)
    {
        recorder.started("START_OA_PROCESS", write(payload));
        OaProcessRef process;
        try
        {
            process = oa.start(payload);
        }
        catch (OaClientException ex)
        {
            if (!ex.isResultUnknown())
            {
                links.updateState(creating.getLinkId(), ProcessLinkState.CREATE_FAILED);
            }
            throw failure(ex);
        }
        catch (RuntimeException ex)
        {
            links.updateState(creating.getLinkId(), ProcessLinkState.CREATE_FAILED);
            throw PushFailureException.retryable("OA_START_FAILED", "OA流程发起失败: " + ex.getMessage());
        }
        try
        {
            links.activate(creating.getLinkId(), process);
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.resultUnknown("OA_LINK_PERSIST_FAILED", "OA已返回发起成功，但流程标识保存失败，请人工核对");
        }
        recorder.succeeded("START_OA_PROCESS", write(Map.of("summaryId", process.summaryId(),
                "affairId", process.affairId(), "processId", process.processId())));
        return PushResult.success(businessKey, write(payload), write(Map.of("summaryId", process.summaryId(),
                "affairId", process.affairId(), "processId", process.processId())));
    }

    private void ensureSafeToCreate(OaProcessLink latest)
    {
        if (latest == null || latest.getState() == ProcessLinkState.CANCELLED
                || latest.getState() == ProcessLinkState.CANCELLED_PENDING_RECREATE
                || latest.getState() == ProcessLinkState.CREATE_FAILED)
        {
            return;
        }
        if (latest.getState() == ProcessLinkState.ACTIVE)
        {
            throw PushFailureException.nonRetryable("OA_ACTIVE_PROCESS_EXISTS", "已有有效OA流程");
        }
        throw PushFailureException.resultUnknown("OA_LINK_UNCERTAIN", "OA流程关联状态待人工核对: " + latest.getState());
    }

    private ExecutionVariableContext context(IntegrationExecution execution, U8ToOaTaskConfig config,
            Map<String, Object> data)
    {
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("masterId", execution.getMasterId());
        trigger.put("formId", execution.getFormId());
        trigger.put("summaryId", execution.getSummaryId());
        return new ExecutionVariableContext(immutable(trigger), immutable(config.constants()), immutable(data), Map.of(), Map.of());
    }

    private PushFailureException failure(OaClientException ex)
    {
        if (ex.isResultUnknown()) return PushFailureException.resultUnknown(ex.getErrorCode(), ex.getMessage());
        if (ex.isRetryable()) return PushFailureException.retryable(ex.getErrorCode(), ex.getMessage());
        return PushFailureException.nonRetryable(ex.getErrorCode(), ex.getMessage());
    }

    private String write(Object value)
    {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("OA请求无法序列化", ex); }
    }

    private Map<String, Object> immutable(Map<String, ?> values)
    {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private record Prepared(String u8Id, Map<String, Object> payload) { }
}
