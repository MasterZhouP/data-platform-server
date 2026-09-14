package com.ruoyi.integration.execution.service;

import java.util.Date;
import java.util.List;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.integration.execution.domain.ExecutionStageStatus;
import com.ruoyi.integration.execution.domain.ExecutionStatus;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.support.SensitiveDataMasker;
import com.ruoyi.integration.task.IntegrationTaskHandler;
import com.ruoyi.integration.task.PushHandlerRegistry;
import com.ruoyi.integration.task.TaskExecutorRegistry;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.taskdefinition.PublishedTaskRevision;
import com.ruoyi.integration.taskdefinition.TaskDefinitionNotFoundException;
import com.ruoyi.integration.taskdefinition.TaskDefinitionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一受理执行请求并创建不可变执行记录。
 * <p>
 * 重试从原记录复制任务版本与检查点，而不是重新读取当前配置；这样任务管理员发布新版本也不会改变在途单据的处理语义。
 * </p>
 */
@Service
public class IntegrationExecutionService implements ExecutionAcceptor
{
    private final ExecutionRepository repository;
    private final PushHandlerRegistry handlerRegistry;
    private final TaskExecutorRegistry executorRegistry;
    private final TaskDefinitionResolver taskDefinitionResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final SensitiveDataMasker masker;

    @Autowired
    public IntegrationExecutionService(ExecutionRepository repository, PushHandlerRegistry handlerRegistry,
            TaskExecutorRegistry executorRegistry, TaskDefinitionResolver taskDefinitionResolver,
            ApplicationEventPublisher eventPublisher, SensitiveDataMasker masker)
    {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
        this.executorRegistry = executorRegistry;
        this.taskDefinitionResolver = taskDefinitionResolver;
        this.eventPublisher = eventPublisher;
        this.masker = masker;
    }

    /** 保留旧构造器供既有代码型任务测试和兼容调用使用；正式容器始终注入完整的配置型路由依赖。 */
    public IntegrationExecutionService(ExecutionRepository repository, PushHandlerRegistry handlerRegistry,
            ApplicationEventPublisher eventPublisher, SensitiveDataMasker masker)
    {
        this(repository, handlerRegistry, null, null, eventPublisher, masker);
    }

    @Transactional(noRollbackFor = ExecutionConflictException.class)
    @Override
    public AcceptanceResult accept(TriggerCommand command)
    {
        validate(command);
        TriggerCommand normalized = normalize(command);
        PublishedTaskRevision published = resolvePublished(normalized.taskCode());
        IntegrationExecution execution;
        if (published != null)
        {
            // 受理时固定修订与依赖快照，后续发布、停用都不会改变这一笔 OA 单据的执行合同。
            requireExecutor(published);
            execution = createExecution(normalized, published.dedupKey(normalized));
            pinRevision(execution, published);
        }
        else
        {
            // 未纳入配置目录的既有 U8→OA 任务继续使用原有 Handler，避免本次平台升级破坏存量链路。
            IntegrationTaskHandler handler = handlerRegistry.require(normalized.taskCode());
            execution = createExecution(normalized, handler.dedupKey(normalized));
        }
        repository.insert(execution);
        repository.insertStage(receivedStage(execution.getExecutionId()));
        eventPublisher.publishEvent(new ExecutionAcceptedEvent(execution.getExecutionId()));
        return new AcceptanceResult(execution.getExecutionId(), execution.getStatus());
    }

    @Transactional
    public AcceptanceResult retry(Long executionId)
    {
        IntegrationExecution original = repository.findByIdForUpdate(executionId);
        if (original == null)
        {
            throw new ExecutionNotFoundException(executionId);
        }
        String blocked = retryBlockReason(original);
        if (blocked != null)
        {
            throw new RetryRejectedException(blocked);
        }
        if (repository.hasRetryChild(executionId))
        {
            throw new RetryRejectedException("已为该失败记录创建重试执行");
        }

        TriggerCommand command = new TriggerCommand(original.getTaskCode(), original.getMasterId(),
                original.getFormId(), original.getSummaryId(),
                com.ruoyi.integration.task.TaskAction.valueOf(original.getOperation()),
                com.ruoyi.integration.task.TriggerSource.RETRY, Boolean.TRUE.equals(original.getForce()));
        IntegrationExecution child = createExecution(command, dedupKeyForRetry(original, command));
        child.setBusinessKey(original.getBusinessKey());
        child.setRetryCount(valueOrZero(original.getRetryCount()) + 1);
        child.setRetryOfExecutionId(original.getExecutionId());
        copyExecutionSnapshot(original, child);
        // U8 已确认的部分成功记录要保留原记录的去重键，阻止新的完整推送；子记录不占该键，只续跑后处理。
        if (ExecutionStatus.PARTIAL_SUCCESS.name().equals(original.getStatus()))
        {
            child.setDedupKey(null);
        }
        try
        {
            repository.insert(child);
        }
        catch (ExecutionConflictException ex)
        {
            throw new RetryRejectedException("已存在同一业务的待执行、执行中、成功或结果未知记录");
        }
        repository.insertStage(receivedStage(child.getExecutionId()));
        eventPublisher.publishEvent(new ExecutionAcceptedEvent(child.getExecutionId()));
        return new AcceptanceResult(child.getExecutionId(), child.getStatus());
    }

    public List<IntegrationExecution> findList(IntegrationExecution query)
    {
        List<IntegrationExecution> values = repository.findList(query);
        values.forEach(value -> {
            value.setTriggerPayload(null);
            value.setRequestPayload(null);
            value.setResponsePayload(null);
            value.setDedupKey(null);
            decorateRetryDecision(value);
        });
        return values;
    }

    public IntegrationExecution findDetail(Long executionId)
    {
        IntegrationExecution execution = repository.findById(executionId);
        if (execution == null)
        {
            throw new ExecutionNotFoundException(executionId);
        }
        execution.setStages(repository.findStages(executionId));
        decorateRetryDecision(execution);
        return execution;
    }

    private IntegrationExecution createExecution(TriggerCommand command, String dedupKey)
    {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setTaskCode(command.taskCode().trim());
        execution.setMasterId(command.masterId().trim());
        execution.setFormId(trimToNull(command.formId()));
        execution.setSummaryId(trimToNull(command.summaryId()));
        execution.setOperation(command.action().name());
        execution.setTriggerSource(command.triggerSource().name());
        execution.setForce(command.force());
        execution.setStatus(ExecutionStatus.PENDING.name());
        execution.setStage("RECEIVED");
        execution.setRetryable(false);
        execution.setResultUnknown(false);
        execution.setRetryCount(0);
        execution.setDedupKey(dedupKey);
        execution.setU8Confirmed(false);
        execution.setResumeMode("FULL");
        execution.setTriggerPayload(masker.mask(JSON.toJSONString(command)));
        execution.setCreateTime(new Date());
        execution.setUpdateTime(new Date());
        return execution;
    }

    private PublishedTaskRevision resolvePublished(String taskCode)
    {
        if (taskDefinitionResolver == null)
        {
            return null;
        }
        try
        {
            return taskDefinitionResolver.resolvePublished(taskCode);
        }
        catch (TaskDefinitionNotFoundException ex)
        {
            // 目录中不存在时才回退到代码型 Handler；已停用任务会由解析器直接阻断，不会意外执行旧代码。
            if (taskDefinitionResolver.isCatalogTask(taskCode))
            {
                throw ex;
            }
            return null;
        }
    }

    private void requireExecutor(PublishedTaskRevision revision)
    {
        if (executorRegistry == null)
        {
            throw new IllegalStateException("配置型集成任务执行器尚未初始化");
        }
        executorRegistry.require(revision.taskType());
    }

    private void pinRevision(IntegrationExecution execution, PublishedTaskRevision revision)
    {
        execution.setTaskRevisionId(revision.revisionId());
        execution.setTaskChecksum(revision.checksum());
        execution.setDependencySnapshot(masker.mask(JSON.toJSONString(revision.dependencyRevisions())));
    }

    private String dedupKeyForRetry(IntegrationExecution original, TriggerCommand command)
    {
        if (original.getTaskRevisionId() == null)
        {
            return handlerRegistry.require(original.getTaskCode()).dedupKey(command);
        }
        if (taskDefinitionResolver == null)
        {
            throw new IllegalStateException("配置型集成任务修订解析器尚未初始化");
        }
        PublishedTaskRevision pinned = taskDefinitionResolver.resolvePinned(original.getTaskCode(),
                original.getTaskRevisionId(), original.getTaskChecksum());
        requireExecutor(pinned);
        return pinned.dedupKey(command);
    }

    private IntegrationExecutionStage receivedStage(Long executionId)
    {
        Date now = new Date();
        IntegrationExecutionStage stage = new IntegrationExecutionStage();
        stage.setExecutionId(executionId);
        stage.setSequenceNo(1);
        stage.setStage("RECEIVED");
        stage.setStageStatus(ExecutionStageStatus.SUCCESS.name());
        stage.setStartTime(now);
        stage.setEndTime(now);
        stage.setCreateTime(now);
        stage.setUpdateTime(now);
        return stage;
    }

    private void validate(TriggerCommand command)
    {
        if (command == null || command.taskCode() == null || command.taskCode().isBlank())
        {
            throw new IllegalArgumentException("taskCode 不能为空");
        }
        if (command.masterId() == null || command.masterId().isBlank())
        {
            throw new IllegalArgumentException("masterId 不能为空");
        }
    }

    private TriggerCommand normalize(TriggerCommand command)
    {
        return new TriggerCommand(command.taskCode().trim(), command.masterId().trim(),
                trimToNull(command.formId()), trimToNull(command.summaryId()), command.action(),
                command.triggerSource(), command.force());
    }

    private void decorateRetryDecision(IntegrationExecution execution)
    {
        String reason = retryBlockReason(execution);
        if (reason == null && repository.hasRetryChild(execution.getExecutionId()))
        {
            reason = "已为该失败记录创建重试执行";
        }
        execution.setRetryBlockReason(reason);
        execution.setRetryable(reason == null);
    }

    private String retryBlockReason(IntegrationExecution execution)
    {
        if (ExecutionStatus.RESULT_UNKNOWN.name().equals(execution.getStatus())
                || Boolean.TRUE.equals(execution.getResultUnknown()))
        {
            return "外部处理结果未知，请先核对目标系统实际结果";
        }
        if (ExecutionStatus.PARTIAL_SUCCESS.name().equals(execution.getStatus()))
        {
            return Boolean.TRUE.equals(execution.getU8Confirmed()) ? null : "部分成功记录缺少U8确认检查点";
        }
        if (!ExecutionStatus.FAILED.name().equals(execution.getStatus()))
        {
            return "只有失败记录可以重试";
        }
        if (!Boolean.TRUE.equals(execution.getRetryable()))
        {
            return "该失败不允许安全重试";
        }
        return null;
    }

    /**
     * 对配置型任务，下面字段就是受理时固定的执行合同；子重试必须继续使用它们，不能改用最新发布的任务修订。
     */
    private void copyExecutionSnapshot(IntegrationExecution source, IntegrationExecution target)
    {
        target.setTaskRevisionId(source.getTaskRevisionId());
        target.setTaskChecksum(source.getTaskChecksum());
        target.setDependencySnapshot(source.getDependencySnapshot());
        target.setLastCompletedStage(source.getLastCompletedStage());
        target.setU8Confirmed(source.getU8Confirmed());
        target.setResultOutputsJson(source.getResultOutputsJson());
        if (ExecutionStatus.PARTIAL_SUCCESS.name().equals(source.getStatus()))
        {
            target.setResumeMode("POST_PROCESS");
        }
    }

    private int valueOrZero(Integer value)
    {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
