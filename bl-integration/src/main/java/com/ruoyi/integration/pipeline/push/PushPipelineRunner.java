package com.ruoyi.integration.pipeline.push;

import java.util.Date;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.execution.support.SensitiveDataMasker;
import com.ruoyi.integration.oatou8.PostProcessPendingException;
import com.ruoyi.integration.task.IntegrationTaskHandler;
import com.ruoyi.integration.task.PushExecutionContext;
import com.ruoyi.integration.task.PushFailureException;
import com.ruoyi.integration.task.PushHandlerRegistry;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.task.TaskExecutor;
import com.ruoyi.integration.task.TaskExecutorRegistry;
import com.ruoyi.integration.task.PushOutcome;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerSource;
import com.ruoyi.integration.taskdefinition.PublishedTaskRevision;
import com.ruoyi.integration.taskdefinition.TaskDefinitionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PushPipelineRunner
{
    private final ExecutionRepository repository;
    private final PushHandlerRegistry handlerRegistry;
    private final TaskExecutorRegistry executorRegistry;
    private final TaskDefinitionResolver taskDefinitionResolver;
    private final SensitiveDataMasker masker;

    @Autowired
    public PushPipelineRunner(ExecutionRepository repository, PushHandlerRegistry handlerRegistry,
            TaskExecutorRegistry executorRegistry, TaskDefinitionResolver taskDefinitionResolver, SensitiveDataMasker masker)
    {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
        this.executorRegistry = executorRegistry;
        this.taskDefinitionResolver = taskDefinitionResolver;
        this.masker = masker;
    }

    /** 与旧代码型任务并存的构造入口；Spring 容器使用完整构造器运行配置型任务。 */
    public PushPipelineRunner(ExecutionRepository repository, PushHandlerRegistry handlerRegistry,
            SensitiveDataMasker masker)
    {
        this(repository, handlerRegistry, null, null, masker);
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
            ExecutedTask executed = execute(execution, recorder);
            PushResult result = executed.result();
            if (PushOutcome.SKIPPED.equals(result.outcome()))
            {
                repository.markSkipped(executionId, result.businessKey(), masker.maskError(result.message()), new Date());
                return;
            }
            recorder.started("COMPLETED", null);
            recorder.succeeded("COMPLETED", null);
            repository.markSuccess(executionId, result.businessKey(), masker.mask(result.requestPayload()),
                    masker.mask(result.responsePayload()), executed.retainDedupAfterSuccess(), new Date());
        }
        catch (PostProcessPendingException ex)
        {
            // U8 已确认的单据不能再回到 FAILED；保存检查点后只允许操作员继续结果查询。
            recorder.failCurrent(ex.getErrorCode(), ex.getMessage());
            IntegrationExecution checkpoint = repository.findById(executionId);
            String outputs = retainCheckpointOutputs(checkpoint, ex.getResultOutputsJson());
            if (checkpoint == null || !Boolean.TRUE.equals(checkpoint.getU8Confirmed()))
            {
                // 执行器异常路径也要落下确认标记，避免后续人工点击重试时误触发完整推单。
                repository.checkpointU8Confirmed(executionId, outputs, "U8_CONFIRMED");
            }
            repository.markPartialSuccess(executionId, ex.getErrorCode(), masker.maskError(ex.getMessage()),
                    ex.getLastCompletedStage(), outputs, new Date());
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

    private ExecutedTask execute(IntegrationExecution execution, PersistentStageRecorder recorder)
    {
        if (execution.getTaskRevisionId() != null)
        {
            if (executorRegistry == null || taskDefinitionResolver == null)
            {
                throw new IllegalStateException("配置型集成任务运行依赖尚未初始化");
            }
            // 异步线程只能读取受理时锁定的修订，绝不重新按 taskCode 取当前发布版本。
            PublishedTaskRevision revision = taskDefinitionResolver.resolvePinned(execution.getTaskCode(),
                    execution.getTaskRevisionId(), execution.getTaskChecksum());
            TaskExecutor executor = executorRegistry.require(revision.taskType());
            return new ExecutedTask(executor.execute(new ResolvedExecution(execution, revision), recorder), true);
        }
        IntegrationTaskHandler handler = handlerRegistry.require(execution.getTaskCode());
        PushExecutionContext context = new PushExecutionContext(execution.getExecutionId(), execution.getTaskCode(),
                execution.getMasterId(), execution.getBusinessKey(), execution.getFormId(), execution.getSummaryId(),
                execution.getRetryCount() == null ? 0 : execution.getRetryCount(), TaskAction.valueOf(execution.getOperation()),
                TriggerSource.valueOf(execution.getTriggerSource()), Boolean.TRUE.equals(execution.getForce()));
        return new ExecutedTask(handler.execute(context, recorder), handler.retainDedupAfterSuccess());
    }

    private record ExecutedTask(PushResult result, boolean retainDedupAfterSuccess)
    {
    }

    private String retainCheckpointOutputs(IntegrationExecution checkpoint, String latestOutputs)
    {
        if ((latestOutputs == null || latestOutputs.isBlank() || "{}".equals(latestOutputs))
                && checkpoint != null && checkpoint.getResultOutputsJson() != null
                && !checkpoint.getResultOutputsJson().isBlank())
        {
            return checkpoint.getResultOutputsJson();
        }
        return latestOutputs == null ? "{}" : latestOutputs;
    }
}
