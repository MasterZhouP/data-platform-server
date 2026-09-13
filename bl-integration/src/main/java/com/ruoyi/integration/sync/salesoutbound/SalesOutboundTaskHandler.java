package com.ruoyi.integration.sync.salesoutbound;

import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.integration.client.oa.OaClientException;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLink;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkRepository;
import com.ruoyi.integration.sync.salesoutbound.link.ProcessLinkState;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundPreview;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.ManualSyncTask;
import com.ruoyi.integration.task.PushExecutionContext;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TaskAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = { "integration.datasource.u8.enabled", "integration.datasource.oa.enabled" },
        havingValue = "true")
public class SalesOutboundTaskHandler implements ManualSyncTask
{
    public static final String TASK_CODE = "U8_TO_OA_SALES_OUTBOUND";

    private final SalesOutboundSource source;
    private final SalesOutboundValidator validator;
    private final SalesOutboundPayloadFactory payloadFactory;
    private final OaProcessOperations oa;
    private final OaProcessLinkRepository links;

    public SalesOutboundTaskHandler(SalesOutboundSource source, SalesOutboundValidator validator,
            SalesOutboundPayloadFactory payloadFactory, OaProcessOperations oa, OaProcessLinkRepository links)
    {
        this.source = source;
        this.validator = validator;
        this.payloadFactory = payloadFactory;
        this.oa = oa;
        this.links = links;
    }

    @Override
    public String taskCode()
    {
        return TASK_CODE;
    }

    @Override
    public boolean retainDedupAfterSuccess()
    {
        return false;
    }

    @Override
    public SalesOutboundPreview preview(String masterId)
    {
        SalesOutboundDocument document = source.load(masterId);
        validator.validate(document);
        return new SalesOutboundPreview(TASK_CODE, document, payloadFactory.build(document),
                links.findLatest(TASK_CODE, masterId));
    }

    @Override
    public void validateManualAcceptance(String masterId, boolean force)
    {
        SalesOutboundPreview value = preview(masterId);
        OaProcessLink current = value.currentProcess();
        if (current != null && (current.getState() == ProcessLinkState.CREATING
                || current.getState() == ProcessLinkState.CANCELING))
        {
            throw new IllegalStateException("OA流程状态待人工核对: " + current.getState());
        }
        if (force && (current == null || current.getState() != ProcessLinkState.ACTIVE))
        {
            throw new IllegalStateException("没有有效的OA流程可供强制重推");
        }
        if (!force && current != null && current.getState() == ProcessLinkState.ACTIVE)
        {
            throw new DuplicateOaProcessException(masterId);
        }
    }

    @Override
    public PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder)
    {
        OaProcessLink latest = checkCurrent(context, recorder);
        if (context.action() == TaskAction.DELETE)
        {
            return delete(context, latest, recorder);
        }
        if (context.action() == TaskAction.CREATE && latest != null && latest.getState() == ProcessLinkState.ACTIVE)
        {
            return PushResult.skipped(context.masterId(), "已有有效OA流程");
        }
        SalesOutboundDocument document;
        Map<String, Object> payload;
        if (context.action() == TaskAction.CANCEL_RECREATE && latest != null
                && latest.getState() == ProcessLinkState.ACTIVE)
        {
            // Build the complete replacement before the irreversible cancel side effect.
            document = loadAndValidate(context.masterId(), recorder);
            payload = buildPayload(document, recorder);
            cancel(latest, ProcessLinkState.CANCELLED_PENDING_RECREATE, recorder);
            latest = links.findLatest(TASK_CODE, context.masterId());
        }
        else
        {
            ensureSafeToCreate(latest);
            document = loadAndValidate(context.masterId(), recorder);
            payload = buildPayload(document, recorder);
        }
        ensureSafeToCreate(latest);
        OaProcessLink creating = creatingLink(latest, document);
        return start(context.masterId(), creating, payload, recorder);
    }

    private OaProcessLink checkCurrent(PushExecutionContext context, ExecutionStageRecorder recorder)
    {
        recorder.started("CHECK_DUPLICATE", null);
        OaProcessLink latest = links.findLatest(TASK_CODE, context.masterId());
        recorder.succeeded("CHECK_DUPLICATE", latest == null ? "{\"state\":null}"
                : JSON.toJSONString(Map.of("state", latest.getState().name(), "version", latest.getVersionNo())));
        return latest;
    }

    private PushResult delete(PushExecutionContext context, OaProcessLink latest, ExecutionStageRecorder recorder)
    {
        if (latest == null || latest.getState() == ProcessLinkState.CANCELLED)
        {
            return PushResult.skipped(context.masterId(), "没有需要撤销的OA流程");
        }
        if (latest.getState() == ProcessLinkState.CREATE_FAILED
                || latest.getState() == ProcessLinkState.CANCELLED_PENDING_RECREATE)
        {
            links.updateState(latest.getLinkId(), ProcessLinkState.CANCELLED);
            return PushResult.skipped(context.masterId(), "没有活动的OA流程，已归一为撤销状态");
        }
        if (latest.getState() != ProcessLinkState.ACTIVE)
        {
            throw PushFailureException.resultUnknown("OA_LINK_UNCERTAIN", "OA流程关联状态不允许删除: " + latest.getState());
        }
        cancel(latest, ProcessLinkState.CANCELLED, recorder);
        return PushResult.success(context.masterId(), null,
                JSON.toJSONString(Map.of("summaryId", latest.getSummaryId(), "cancelled", true)));
    }

    private void cancel(OaProcessLink link, ProcessLinkState successState, ExecutionStageRecorder recorder)
    {
        recorder.started("CANCEL_OLD", JSON.toJSONString(Map.of("summaryId", link.getSummaryId())));
        try
        {
            links.updateState(link.getLinkId(), ProcessLinkState.CANCELING);
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.retryable("OA_LINK_UPDATE_FAILED", "写入OA流程撤销状态失败");
        }
        try
        {
            oa.cancel(reference(link));
        }
        catch (OaClientException ex)
        {
            if (!ex.isResultUnknown())
            {
                try
                {
                    links.updateState(link.getLinkId(), ProcessLinkState.ACTIVE);
                }
                catch (RuntimeException persistenceFailure)
                {
                    throw PushFailureException.resultUnknown("OA_LINK_PERSIST_FAILED",
                            "OA明确返回撤销失败，但本地流程状态恢复失败，请人工核对");
                }
            }
            throw failure(ex);
        }
        try
        {
            links.updateState(link.getLinkId(), successState);
            recorder.succeeded("CANCEL_OLD", "{\"cancelled\":true}");
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.resultUnknown("OA_LINK_PERSIST_FAILED",
                    "OA已确认撤销成功，但本地流程状态保存失败，请人工核对");
        }
    }

    private SalesOutboundDocument loadAndValidate(String documentNo, ExecutionStageRecorder recorder)
    {
        recorder.started("LOAD_U8", JSON.toJSONString(Map.of("documentNo", documentNo)));
        final SalesOutboundDocument document;
        try
        {
            document = source.load(documentNo);
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.retryable("U8_READ_FAILED", "读取U8销售出库单失败: " + ex.getMessage());
        }
        recorder.succeeded("LOAD_U8", document == null ? "{\"found\":false}" : "{\"found\":true}");
        recorder.started("VALIDATE_SOURCE", null);
        try
        {
            validator.validate(document);
        }
        catch (SourceValidationException ex)
        {
            throw PushFailureException.nonRetryable("U8_SOURCE_INVALID", ex.getMessage());
        }
        recorder.succeeded("VALIDATE_SOURCE", "{\"valid\":true}");
        return document;
    }

    private Map<String, Object> buildPayload(SalesOutboundDocument document, ExecutionStageRecorder recorder)
    {
        recorder.started("BUILD_OA_PAYLOAD", null);
        Map<String, Object> payload = payloadFactory.build(document);
        recorder.succeeded("BUILD_OA_PAYLOAD", "{\"built\":true}");
        return payload;
    }

    private OaProcessLink creatingLink(OaProcessLink latest, SalesOutboundDocument document)
    {
        if (latest != null && latest.getState() == ProcessLinkState.CREATE_FAILED)
        {
            links.updateState(latest.getLinkId(), ProcessLinkState.CREATING);
            return latest;
        }
        Long previous = latest == null ? null : latest.getLinkId();
        return links.createCreating(TASK_CODE, document.header().documentNo(), document.header().u8Id(), previous);
    }

    private PushResult start(String businessKey, OaProcessLink creating, Map<String, Object> payload,
            ExecutionStageRecorder recorder)
    {
        String request = JSON.toJSONString(payload);
        recorder.started("START_OA_PROCESS", request);
        final OaProcessRef process;
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
        try
        {
            links.activate(creating.getLinkId(), process);
        }
        catch (RuntimeException ex)
        {
            throw PushFailureException.resultUnknown("OA_LINK_PERSIST_FAILED",
                    "OA已返回发起成功，但流程标识保存失败，请人工核对");
        }
        String response = JSON.toJSONString(process);
        recorder.succeeded("START_OA_PROCESS", response);
        return PushResult.success(businessKey, request, response);
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

    private OaProcessRef reference(OaProcessLink link)
    {
        return new OaProcessRef(link.getSummaryId(), link.getAffairId(), link.getProcessId());
    }

    private PushFailureException failure(OaClientException ex)
    {
        if (ex.isResultUnknown())
        {
            return PushFailureException.resultUnknown(ex.getErrorCode(), ex.getMessage());
        }
        if (ex.isRetryable())
        {
            return PushFailureException.retryable(ex.getErrorCode(), ex.getMessage());
        }
        return PushFailureException.nonRetryable(ex.getErrorCode(), ex.getMessage());
    }
}
