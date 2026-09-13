package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.client.oa.OaClientException;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLink;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkRepository;
import com.ruoyi.integration.sync.salesoutbound.link.ProcessLinkState;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundHeader;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundLine;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.PushExecutionContext;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushOutcome;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerSource;

class SalesOutboundTaskHandlerTest
{
    private InMemoryLinks links;
    private FakeOa oa;
    private SalesOutboundTaskHandler handler;

    @BeforeEach
    void setUp()
    {
        links = new InMemoryLinks();
        oa = new FakeOa();
        SalesOutboundDocument sourceDocument = document();
        handler = new SalesOutboundTaskHandler(documentNo -> sourceDocument, new SalesOutboundValidator(),
                new SalesOutboundPayloadFactory("XSCKDCS", "formmain_0688", "formson_0689"), oa, links);
    }

    @Test
    void createsOneActiveOaLinkAndSkipsARepeatedCreate()
    {
        var first = handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());
        var second = handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());

        assertEquals(PushOutcome.SUCCESS, first.outcome());
        assertEquals(PushOutcome.SKIPPED, second.outcome());
        assertEquals(1, oa.starts);
        assertEquals(ProcessLinkState.ACTIVE, links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001").getState());
    }

    @Test
    void forceRepushRequiresAnExistingActiveProcess()
    {
        assertThrows(IllegalStateException.class, () -> handler.validateManualAcceptance("CK-001", true));
    }

    @Test
    void forceRepushCancelsActiveProcessBeforeCreatingTheNextVersion()
    {
        handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());
        handler.execute(context(TaskAction.CANCEL_RECREATE, true), new NoopRecorder());

        assertEquals(1, oa.cancels);
        assertEquals(2, oa.starts);
        OaProcessLink latest = links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001");
        assertEquals(2, latest.getVersionNo());
        assertEquals(ProcessLinkState.ACTIVE, latest.getState());
    }

    @Test
    void cancellationFailurePreventsRecreation()
    {
        handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());
        oa.cancelFailure = OaClientException.retryable("OA_CANCEL_FAILED", "cancel failed");

        assertThrows(PushFailureException.class,
                () -> handler.execute(context(TaskAction.CANCEL_RECREATE, true), new NoopRecorder()));
        assertEquals(1, oa.starts);
    }

    @Test
    void invalidReplacementNeverCancelsTheExistingActiveProcess()
    {
        handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());
        SalesOutboundTaskHandler invalidReplacement = new SalesOutboundTaskHandler(documentNo -> null,
                new SalesOutboundValidator(),
                new SalesOutboundPayloadFactory("XSCKDCS", "formmain_0688", "formson_0689"), oa, links);

        assertThrows(PushFailureException.class,
                () -> invalidReplacement.execute(context(TaskAction.CANCEL_RECREATE, true), new NoopRecorder()));

        assertEquals(0, oa.cancels);
        assertEquals(1, oa.starts);
        assertEquals(ProcessLinkState.ACTIVE,
                links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001").getState());
    }

    @Test
    void deleteCancelsOnlyAndNeverLoadsOrStartsANewDocument()
    {
        handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());
        var deleted = handler.execute(context(TaskAction.DELETE, false), new NoopRecorder());

        assertEquals(PushOutcome.SUCCESS, deleted.outcome());
        assertEquals(1, oa.cancels);
        assertEquals(1, oa.starts);
        assertEquals(ProcessLinkState.CANCELLED,
                links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001").getState());
    }

    @Test
    void deleteNormalizesConfirmedNoActiveStatesWithoutCallingOa()
    {
        OaProcessLink failed = links.createCreating(SalesOutboundTaskHandler.TASK_CODE, "CK-001", "10001", null);
        links.updateState(failed.getLinkId(), ProcessLinkState.CREATE_FAILED);

        var first = handler.execute(context(TaskAction.DELETE, false), new NoopRecorder());
        assertEquals(PushOutcome.SKIPPED, first.outcome());
        assertEquals(ProcessLinkState.CANCELLED, links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001").getState());

        links.updateState(failed.getLinkId(), ProcessLinkState.CANCELLED_PENDING_RECREATE);
        var second = handler.execute(context(TaskAction.DELETE, false), new NoopRecorder());
        assertEquals(PushOutcome.SKIPPED, second.outcome());
        assertEquals(ProcessLinkState.CANCELLED, links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001").getState());
        assertEquals(0, oa.cancels);
        assertEquals(0, oa.starts);
    }

    @Test
    void persistenceFailureAfterOaStartIsResultUnknown()
    {
        links.failActivate = true;

        PushFailureException failure = assertThrows(PushFailureException.class,
                () -> handler.execute(context(TaskAction.CREATE, false), new NoopRecorder()));

        assertEquals(true, failure.isResultUnknown());
        assertEquals(1, oa.starts);
        assertEquals(ProcessLinkState.CREATING,
                links.findLatest(SalesOutboundTaskHandler.TASK_CODE, "CK-001").getState());
    }

    @Test
    void persistenceFailureAfterConfirmedCancelNeverStartsReplacement()
    {
        handler.execute(context(TaskAction.CREATE, false), new NoopRecorder());
        links.failState = ProcessLinkState.CANCELLED_PENDING_RECREATE;

        PushFailureException failure = assertThrows(PushFailureException.class,
                () -> handler.execute(context(TaskAction.CANCEL_RECREATE, true), new NoopRecorder()));

        assertEquals(true, failure.isResultUnknown());
        assertEquals(1, oa.cancels);
        assertEquals(1, oa.starts);
    }

    private PushExecutionContext context(TaskAction action, boolean force)
    {
        return new PushExecutionContext(1L, SalesOutboundTaskHandler.TASK_CODE, "CK-001", null, null, null,
                0, action, TriggerSource.MANUAL, force);
    }

    private SalesOutboundDocument document()
    {
        SalesOutboundHeader header = new SalesOutboundHeader("CK-001", "张三", LocalDate.now(), "WH-01",
                "成品仓", "C-01", "客户甲", "D-01", "销售部", null, "10001", "SO-9",
                "900000000000001", false);
        SalesOutboundLine line = new SalesOutboundLine("INV-1", "商品A", "S", BigDecimal.ONE, "20001",
                "10001", "箱", "件", BigDecimal.ONE, null, null, null);
        return new SalesOutboundDocument(header, List.of(line));
    }

    private static final class FakeOa implements OaProcessOperations
    {
        private int starts;
        private int cancels;
        private OaClientException cancelFailure;

        @Override
        public OaProcessRef start(Map<String, Object> payload)
        {
            starts++;
            return new OaProcessRef("S-" + starts, "A-" + starts, "P-" + starts);
        }

        @Override
        public void cancel(OaProcessRef process)
        {
            cancels++;
            if (cancelFailure != null)
            {
                throw cancelFailure;
            }
        }
    }

    private static final class InMemoryLinks implements OaProcessLinkRepository
    {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, OaProcessLink> values = new LinkedHashMap<>();
        private boolean failActivate;
        private ProcessLinkState failState;

        @Override
        public OaProcessLink findLatest(String taskCode, String businessKey)
        {
            return values.values().stream()
                    .filter(value -> value.getTaskCode().equals(taskCode) && value.getBusinessKey().equals(businessKey))
                    .reduce((first, second) -> second).orElse(null);
        }

        @Override
        public OaProcessLink createCreating(String taskCode, String businessKey, String u8Id, Long previousLinkId)
        {
            OaProcessLink link = new OaProcessLink();
            link.setLinkId(ids.incrementAndGet());
            link.setTaskCode(taskCode);
            link.setBusinessKey(businessKey);
            link.setU8Id(u8Id);
            link.setPreviousLinkId(previousLinkId);
            OaProcessLink previous = previousLinkId == null ? null : values.get(previousLinkId);
            link.setVersionNo(previous == null ? 1 : previous.getVersionNo() + 1);
            link.setState(ProcessLinkState.CREATING);
            values.put(link.getLinkId(), link);
            return link;
        }

        @Override
        public void updateState(Long linkId, ProcessLinkState state)
        {
            if (state == failState)
            {
                throw new IllegalStateException("write failed");
            }
            values.get(linkId).setState(state);
        }

        @Override
        public void activate(Long linkId, OaProcessRef process)
        {
            if (failActivate)
            {
                throw new IllegalStateException("write failed");
            }
            OaProcessLink link = values.get(linkId);
            link.setSummaryId(process.summaryId());
            link.setAffairId(process.affairId());
            link.setProcessId(process.processId());
            link.setState(ProcessLinkState.ACTIVE);
        }
    }

    private static final class NoopRecorder implements ExecutionStageRecorder
    {
        @Override public void started(String stage, String requestPayload) { }
        @Override public void succeeded(String stage, String responsePayload) { }
        @Override public void failed(String stage, String errorCode, String errorMessage) { }
    }
}
