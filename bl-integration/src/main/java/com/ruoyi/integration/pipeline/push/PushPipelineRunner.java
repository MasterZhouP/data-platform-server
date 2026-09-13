package com.ruoyi.integration.pipeline.push;

import java.util.Date;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.support.SensitiveDataMasker;
import com.ruoyi.integration.task.IntegrationTaskHandler;
import com.ruoyi.integration.task.PushExecutionContext;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushHandlerRegistry;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.PushOutcome;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerSource;
import org.springframework.stereotype.Component;

@Component
public class PushPipelineRunner
{
    private final ExecutionRepository repository;
    private final PushHandlerRegistry handlerRegistry;
    private final SensitiveDataMasker masker;

    public PushPipelineRunner(ExecutionRepository repository, PushHandlerRegistry handlerRegistry,
            SensitiveDataMasker masker)
    {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
        this.masker = masker;
    }

    public void run(Long executionId)
    {
        if (!repository.claimPending(executionId, new Date()))
        {
            return;
        }

        IntegrationExecution execution = repository.findById(executionId);
        PersistentStageRecorder recorder = new PersistentStageRecorder(executionId, repository, masker);
        try
        {
            IntegrationTaskHandler handler = handlerRegistry.require(execution.getTaskCode());
            PushExecutionContext context = new PushExecutionContext(executionId, execution.getTaskCode(),
                    execution.getMasterId(), execution.getBusinessKey(), execution.getFormId(),
                    execution.getSummaryId(), execution.getRetryCount() == null ? 0 : execution.getRetryCount(),
                    TaskAction.valueOf(execution.getOperation()), TriggerSource.valueOf(execution.getTriggerSource()),
                    Boolean.TRUE.equals(execution.getForce()));
            PushResult result = handler.execute(context, recorder);
            if (PushOutcome.SKIPPED.equals(result.outcome()))
            {
                repository.markSkipped(executionId, result.businessKey(), masker.maskError(result.message()), new Date());
                return;
            }
            recorder.started("COMPLETED", null);
            recorder.succeeded("COMPLETED", null);
            repository.markSuccess(executionId, result.businessKey(), masker.mask(result.requestPayload()),
                    masker.mask(result.responsePayload()), handler.retainDedupAfterSuccess(), new Date());
        }
        catch (PushFailureException ex)
        {
            recorder.failCurrent(ex.getErrorCode(), ex.getMessage());
            if (ex.isResultUnknown())
            {
                repository.markResultUnknown(executionId, ex.getErrorCode(), masker.maskError(ex.getMessage()), new Date());
            }
            else
            {
                repository.markFailed(executionId, ex.getErrorCode(), masker.maskError(ex.getMessage()),
                        ex.isRetryable(), false, new Date());
            }
        }
        catch (RuntimeException ex)
        {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            recorder.failCurrent("UNEXPECTED_ERROR", message);
            repository.markFailed(executionId, "UNEXPECTED_ERROR", masker.maskError(message), false, false, new Date());
        }
    }
}
