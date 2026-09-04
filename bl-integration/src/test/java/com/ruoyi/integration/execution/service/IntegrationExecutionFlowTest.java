package com.ruoyi.integration.execution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.ruoyi.integration.execution.domain.ExecutionStatus;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.support.SensitiveDataMasker;
import com.ruoyi.integration.pipeline.push.PushPipelineRunner;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.OaToU8PushHandler;
import com.ruoyi.integration.task.PushExecutionContext;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushHandlerRegistry;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TriggerCommand;

class IntegrationExecutionFlowTest
{
    private final MutableTestHandler handler = new MutableTestHandler();
    private InMemoryExecutionRepository repository;
    private ApplicationEventPublisher publisher;
    private IntegrationExecutionService service;
    private PushPipelineRunner runner;

    @BeforeEach
    void setUp()
    {
        repository = new InMemoryExecutionRepository();
        publisher = mock(ApplicationEventPublisher.class);
        PushHandlerRegistry registry = new PushHandlerRegistry(List.of(handler));
        SensitiveDataMasker masker = new SensitiveDataMasker(2000);
        service = new IntegrationExecutionService(repository, registry, publisher, masker);
        runner = new PushPipelineRunner(repository, registry, masker);
    }

    @Test
    void acceptsThenClaimsAndExecutesExactlyOnce()
    {
        AcceptanceResult accepted = service.accept(new TriggerCommand("TEST_PUSH", "M-100", "F-1", null));

        IntegrationExecution pending = repository.findById(accepted.executionId());
        assertEquals(ExecutionStatus.PENDING.name(), pending.getStatus());
        assertEquals("RECEIVED", pending.getStage());
        verify(publisher).publishEvent(any(ExecutionAcceptedEvent.class));

        runner.run(accepted.executionId());
        runner.run(accepted.executionId());

        IntegrationExecution completed = repository.findById(accepted.executionId());
        assertEquals(ExecutionStatus.SUCCESS.name(), completed.getStatus());
        assertEquals("BK-v1", completed.getBusinessKey());
        assertEquals(1, handler.invocations.get());
        assertTrue(repository.findStages(accepted.executionId()).size() >= 2);
    }

    @Test
    void scannerRecoversPendingButNeverReplaysFailed()
    {
        AcceptanceResult accepted = service.accept(new TriggerCommand("TEST_PUSH", "M-200", null, null));
        IntegrationPendingDispatcher dispatcher = new IntegrationPendingDispatcher(repository, Runnable::run, runner, 100);

        handler.failure = PushFailureException.retryable("OA_DOWN", "OA password=secret unavailable");
        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        IntegrationExecution failed = repository.findById(accepted.executionId());
        assertEquals(ExecutionStatus.FAILED.name(), failed.getStatus());
        assertTrue(failed.getRetryable());
        assertFalse(failed.getErrorMessage().contains("secret"));
        assertEquals(1, handler.invocations.get());
    }

    @Test
    void manualRetryCreatesChildAndReloadsLatestSourceData()
    {
        AcceptanceResult first = service.accept(new TriggerCommand("TEST_PUSH", "M-300", "F-3", "S-3"));
        handler.failure = PushFailureException.retryable("OA_DOWN", "temporary");
        runner.run(first.executionId());
        String originalError = repository.findById(first.executionId()).getErrorMessage();

        handler.failure = null;
        handler.sourceVersion = "v2";
        AcceptanceResult retry = service.retry(first.executionId());
        assertNotEquals(first.executionId(), retry.executionId());
        runner.run(retry.executionId());

        IntegrationExecution child = repository.findById(retry.executionId());
        assertEquals(first.executionId(), child.getRetryOfExecutionId());
        assertEquals(1, child.getRetryCount());
        assertTrue(child.getRequestPayload().contains("v2"));
        assertEquals(originalError, repository.findById(first.executionId()).getErrorMessage());
        assertThrows(RetryRejectedException.class, () -> service.retry(first.executionId()));
    }

    @Test
    void resultUnknownCannotBeRetried()
    {
        AcceptanceResult accepted = service.accept(new TriggerCommand("TEST_PUSH", "M-400", null, null));
        handler.failure = PushFailureException.resultUnknown("U8_TIMEOUT", "result unknown");
        runner.run(accepted.executionId());

        IntegrationExecution failed = repository.findById(accepted.executionId());
        assertTrue(failed.getResultUnknown());
        assertFalse(failed.getRetryable());
        assertThrows(RetryRejectedException.class, () -> service.retry(accepted.executionId()));
    }

    @Test
    void rejectedExecutorLeavesExecutionPendingForLaterScan()
    {
        AcceptanceResult accepted = service.accept(new TriggerCommand("TEST_PUSH", "M-500", null, null));
        Executor rejecting = command -> { throw new RejectedExecutionException("full"); };
        IntegrationPendingDispatcher dispatcher = new IntegrationPendingDispatcher(repository, rejecting, runner, 100);

        dispatcher.dispatch(accepted.executionId());

        assertEquals(ExecutionStatus.PENDING.name(), repository.findById(accepted.executionId()).getStatus());
        assertEquals(0, handler.invocations.get());
    }

    @Test
    void normalizedIdentifiersCannotBypassDeduplication()
    {
        service.accept(new TriggerCommand(" TEST_PUSH ", " M-600 ", null, null));

        assertThrows(ExecutionConflictException.class,
                () -> service.accept(new TriggerCommand("TEST_PUSH", "M-600", null, null)));
    }

    @Test
    void listNeverReturnsDetailPayloads()
    {
        AcceptanceResult accepted = service.accept(new TriggerCommand("TEST_PUSH", "M-700", null, null));
        runner.run(accepted.executionId());

        IntegrationExecution listed = service.findList(new IntegrationExecution()).get(0);
        assertEquals(null, listed.getTriggerPayload());
        assertEquals(null, listed.getRequestPayload());
        assertEquals(null, listed.getResponsePayload());
        assertEquals(null, listed.getDedupKey());
    }

    private static final class MutableTestHandler implements OaToU8PushHandler
    {
        private final AtomicInteger invocations = new AtomicInteger();
        private String sourceVersion = "v1";
        private PushFailureException failure;

        @Override
        public String taskCode()
        {
            return "TEST_PUSH";
        }

        @Override
        public PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder)
        {
            invocations.incrementAndGet();
            recorder.started("SOURCE_LOADING", "{\"masterId\":\"" + context.masterId() + "\"}");
            if (failure != null)
            {
                throw failure;
            }
            recorder.succeeded("SOURCE_LOADING", "{\"version\":\"" + sourceVersion + "\"}");
            return PushResult.success("BK-" + sourceVersion,
                    "{\"version\":\"" + sourceVersion + "\",\"token\":\"do-not-store\"}",
                    "{\"success\":true}");
        }
    }

    private static final class InMemoryExecutionRepository implements ExecutionRepository
    {
        private final Map<Long, IntegrationExecution> executions = new LinkedHashMap<>();
        private final Map<Long, List<IntegrationExecutionStage>> stages = new LinkedHashMap<>();
        private long executionSequence;
        private long stageSequence;

        @Override
        public synchronized void insert(IntegrationExecution execution)
        {
            boolean duplicateRetry = execution.getRetryOfExecutionId() != null && executions.values().stream()
                    .anyMatch(item -> execution.getRetryOfExecutionId().equals(item.getRetryOfExecutionId()));
            boolean duplicateDedup = execution.getDedupKey() != null && executions.values().stream()
                    .anyMatch(item -> execution.getDedupKey().equals(item.getDedupKey()));
            if (duplicateRetry || duplicateDedup)
            {
                throw new ExecutionConflictException("存在同一业务的执行记录");
            }
            execution.setExecutionId(++executionSequence);
            executions.put(execution.getExecutionId(), execution.copy());
        }

        @Override
        public synchronized void insertStage(IntegrationExecutionStage stage)
        {
            stage.setStageLogId(++stageSequence);
            stages.computeIfAbsent(stage.getExecutionId(), key -> new ArrayList<>()).add(stage.copy());
        }

        @Override
        public synchronized void updateStage(IntegrationExecutionStage stage)
        {
            List<IntegrationExecutionStage> values = stages.getOrDefault(stage.getExecutionId(), List.of());
            for (int i = 0; i < values.size(); i++)
            {
                if (values.get(i).getStageLogId().equals(stage.getStageLogId()))
                {
                    values.set(i, stage.copy());
                    return;
                }
            }
        }

        @Override
        public synchronized IntegrationExecution findById(Long executionId)
        {
            IntegrationExecution value = executions.get(executionId);
            return value == null ? null : value.copy();
        }

        @Override
        public synchronized IntegrationExecution findByIdForUpdate(Long executionId)
        {
            return findById(executionId);
        }

        @Override
        public synchronized List<IntegrationExecution> findList(IntegrationExecution query)
        {
            return executions.values().stream().map(IntegrationExecution::copy)
                    .sorted(Comparator.comparing(IntegrationExecution::getExecutionId).reversed()).toList();
        }

        @Override
        public synchronized List<IntegrationExecutionStage> findStages(Long executionId)
        {
            return stages.getOrDefault(executionId, List.of()).stream()
                    .map(IntegrationExecutionStage::copy).toList();
        }

        @Override
        public synchronized boolean hasRetryChild(Long executionId)
        {
            return executions.values().stream()
                    .anyMatch(item -> executionId.equals(item.getRetryOfExecutionId()));
        }

        @Override
        public synchronized boolean claimPending(Long executionId, Date startTime)
        {
            IntegrationExecution value = executions.get(executionId);
            if (value == null || !ExecutionStatus.PENDING.name().equals(value.getStatus()))
            {
                return false;
            }
            value.setStatus(ExecutionStatus.RUNNING.name());
            value.setStartTime(startTime);
            return true;
        }

        @Override
        public synchronized List<Long> findPendingIds(int limit)
        {
            return executions.values().stream()
                    .filter(item -> ExecutionStatus.PENDING.name().equals(item.getStatus()))
                    .limit(limit).map(IntegrationExecution::getExecutionId).toList();
        }

        @Override
        public synchronized void updateCurrentStage(Long executionId, String stage)
        {
            executions.get(executionId).setStage(stage);
        }

        @Override
        public synchronized void markSuccess(Long executionId, String businessKey, String requestPayload,
                String responsePayload, Date endTime)
        {
            IntegrationExecution value = executions.get(executionId);
            value.setStatus(ExecutionStatus.SUCCESS.name());
            value.setStage("COMPLETED");
            if (value.getBusinessKey() == null)
            {
                value.setBusinessKey(businessKey);
            }
            value.setRequestPayload(requestPayload);
            value.setResponsePayload(responsePayload);
            value.setEndTime(endTime);
        }

        @Override
        public synchronized void markFailed(Long executionId, String errorCode, String errorMessage,
                boolean retryable, boolean resultUnknown, Date endTime)
        {
            IntegrationExecution value = executions.get(executionId);
            value.setStatus(ExecutionStatus.FAILED.name());
            value.setErrorCode(errorCode);
            value.setErrorMessage(errorMessage);
            value.setRetryable(retryable);
            value.setResultUnknown(resultUnknown);
            value.setEndTime(endTime);
            if (retryable && !resultUnknown)
            {
                value.setDedupKey(null);
            }
        }

        @Override
        public synchronized int failStaleRunning(Date staleBefore, String beforeSendStage,
                String beforeSendCode, String afterSendCode)
        {
            return 0;
        }
    }
}
