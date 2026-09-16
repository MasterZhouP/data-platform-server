package com.ruoyi.integration.u8tooa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.config.ResultCardinality;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLink;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkRepository;
import com.ruoyi.integration.sync.salesoutbound.link.ProcessLinkState;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushOutcome;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerSource;
import com.ruoyi.integration.taskdefinition.PublishedTaskRevision;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.u8tooa.config.IncrementalSyncConfig;
import com.ruoyi.integration.u8tooa.config.OaProcessRequest;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfig;

class U8ToOaTaskExecutorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private InMemoryLinks links;
    private FakeOa oa;
    private U8ToOaTaskExecutor executor;

    @BeforeEach
    void setUp()
    {
        links = new InMemoryLinks();
        oa = new FakeOa();
        ReadOnlySqlExecutor sql = (source, statement, cardinality, parameters) -> result("{\"id\":\"1001\",\"amount\":18}");
        executor = new U8ToOaTaskExecutor(raw -> config(), sql, new SqlVariableResolver(),
                new JsonTemplateRenderer(json), oa, links, json);
    }

    @Test
    void createsOneActiveProcessAndSkipsRepeatedCreate()
    {
        PushResult first = executor.execute(resolved(TaskAction.CREATE), new NoopRecorder());
        PushResult second = executor.execute(resolved(TaskAction.CREATE), new NoopRecorder());

        assertEquals(PushOutcome.SUCCESS, first.outcome());
        assertEquals(PushOutcome.SKIPPED, second.outcome());
        assertEquals(1, oa.starts);
        assertEquals(ProcessLinkState.ACTIVE, links.findLatest("U8_TO_OA_RECEIPT", "DOC-1").getState());
    }

    @Test
    void preparesReplacementBeforeCancellingExistingProcess()
    {
        executor.execute(resolved(TaskAction.CREATE), new NoopRecorder());
        PushResult result = executor.execute(resolved(TaskAction.CANCEL_RECREATE), new NoopRecorder());

        assertEquals(PushOutcome.SUCCESS, result.outcome());
        assertEquals(2, oa.starts);
        assertEquals(1, oa.cancels);
        assertEquals(2, links.findLatest("U8_TO_OA_RECEIPT", "DOC-1").getVersionNo());
    }

    @Test
    void invalidReplacementDoesNotCancelExistingProcess()
    {
        executor.execute(resolved(TaskAction.CREATE), new NoopRecorder());
        executor = new U8ToOaTaskExecutor(raw -> config(), (source, statement, cardinality, parameters) -> {
            throw new com.ruoyi.integration.sql.SqlExecutionException("READ_QUERY_FAILED", "read failed");
        }, new SqlVariableResolver(), new JsonTemplateRenderer(json), oa, links, json);

        assertThrows(PushFailureException.class,
                () -> executor.execute(resolved(TaskAction.CANCEL_RECREATE), new NoopRecorder()));
        assertEquals(1, oa.starts);
        assertEquals(0, oa.cancels);
        assertEquals(ProcessLinkState.ACTIVE, links.findLatest("U8_TO_OA_RECEIPT", "DOC-1").getState());
    }

    @Test
    void deleteCancelsExistingProcessWithoutReadingSource()
    {
        executor.execute(resolved(TaskAction.CREATE), new NoopRecorder());
        PushResult result = executor.execute(resolved(TaskAction.DELETE), new NoopRecorder());

        assertEquals(PushOutcome.SUCCESS, result.outcome());
        assertEquals(1, oa.starts);
        assertEquals(1, oa.cancels);
        assertEquals(ProcessLinkState.CANCELLED, links.findLatest("U8_TO_OA_RECEIPT", "DOC-1").getState());
    }

    private U8ToOaTaskConfig config()
    {
        return new U8ToOaTaskConfig(Map.of(), List.of(
                new ReadQueryStep("header", 1, "u8", "SELECT id, amount", ResultCardinality.ONE, Map.of())),
                new OaProcessRequest("data.header.id", json.createObjectNode()
                        .put("documentNo", "{{trigger.masterId}}")
                        .put("amount", "{{data.header.amount}}")),
                new IncrementalSyncConfig("u8", "SELECT CURRENT_TIMESTAMP", "SELECT 1", "SELECT 1", "SELECT 1",
                        LocalDateTime.of(2026, 1, 1, 0, 0), 60));
    }

    private ReadOnlySqlResult result(String value)
    {
        try
        {
            return new ReadOnlySqlResult(ResultCardinality.ONE, json.readTree(value));
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    private ResolvedExecution resolved(TaskAction action)
    {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setExecutionId(1L);
        execution.setTaskCode("U8_TO_OA_RECEIPT");
        execution.setMasterId("DOC-1");
        execution.setOperation(action.name());
        execution.setTriggerSource(TriggerSource.SCHEDULED.name());
        execution.setResumeMode("FULL");
        return new ResolvedExecution(execution, new PublishedTaskRevision("U8_TO_OA_RECEIPT", 9L,
                "checksum", TaskType.U8_TO_OA, json.createObjectNode(), Map.of()));
    }

    private static final class FakeOa implements OaProcessOperations
    {
        private int starts;
        private int cancels;

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
        }
    }

    private static final class InMemoryLinks implements OaProcessLinkRepository
    {
        private final AtomicLong ids = new AtomicLong();
        private final Map<Long, OaProcessLink> values = new LinkedHashMap<>();

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
            link.setVersionNo(values.values().stream()
                    .filter(value -> value.getTaskCode().equals(taskCode) && value.getBusinessKey().equals(businessKey))
                    .mapToInt(OaProcessLink::getVersionNo).max().orElse(0) + 1);
            link.setState(ProcessLinkState.CREATING);
            values.put(link.getLinkId(), link);
            return link;
        }

        @Override
        public void updateState(Long linkId, ProcessLinkState state)
        {
            values.get(linkId).setState(state);
        }

        @Override
        public void activate(Long linkId, OaProcessRef process)
        {
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
