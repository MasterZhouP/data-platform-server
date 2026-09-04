package com.ruoyi.integration.execution.repository;

import java.util.Date;
import java.util.List;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;
import com.ruoyi.integration.execution.mapper.IntegrationExecutionMapper;
import com.ruoyi.integration.execution.mapper.IntegrationExecutionStageMapper;
import com.ruoyi.integration.execution.service.ExecutionConflictException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MyBatisExecutionRepository implements ExecutionRepository
{
    private final IntegrationExecutionMapper executionMapper;
    private final IntegrationExecutionStageMapper stageMapper;

    public MyBatisExecutionRepository(IntegrationExecutionMapper executionMapper,
            IntegrationExecutionStageMapper stageMapper)
    {
        this.executionMapper = executionMapper;
        this.stageMapper = stageMapper;
    }

    @Override
    @Transactional
    public void insert(IntegrationExecution execution)
    {
        try
        {
            executionMapper.insertExecution(execution);
        }
        catch (DuplicateKeyException ex)
        {
            throw new ExecutionConflictException("存在相同业务或相同来源的执行记录", ex);
        }
    }

    @Override
    @Transactional
    public void insertStage(IntegrationExecutionStage stage)
    {
        stageMapper.insertExecutionStage(stage);
    }

    @Override
    @Transactional
    public void updateStage(IntegrationExecutionStage stage)
    {
        stageMapper.updateExecutionStage(stage);
    }

    @Override
    public IntegrationExecution findById(Long executionId)
    {
        return executionMapper.selectExecutionById(executionId);
    }

    @Override
    public IntegrationExecution findByIdForUpdate(Long executionId)
    {
        return executionMapper.selectExecutionByIdForUpdate(executionId);
    }

    @Override
    public List<IntegrationExecution> findList(IntegrationExecution query)
    {
        return executionMapper.selectExecutionList(query);
    }

    @Override
    public List<IntegrationExecutionStage> findStages(Long executionId)
    {
        return stageMapper.selectByExecutionId(executionId);
    }

    @Override
    public boolean hasRetryChild(Long executionId)
    {
        return executionMapper.countRetryChildren(executionId) > 0;
    }

    @Override
    @Transactional
    public boolean claimPending(Long executionId, Date startTime)
    {
        return executionMapper.claimPending(executionId, startTime) == 1;
    }

    @Override
    public List<Long> findPendingIds(int limit)
    {
        return executionMapper.selectPendingIds(limit);
    }

    @Override
    @Transactional
    public void updateCurrentStage(Long executionId, String stage)
    {
        executionMapper.updateCurrentStage(executionId, stage, new Date());
    }

    @Override
    @Transactional
    public void markSuccess(Long executionId, String businessKey, String requestPayload,
            String responsePayload, Date endTime)
    {
        executionMapper.markSuccess(executionId, businessKey, requestPayload, responsePayload, endTime);
    }

    @Override
    @Transactional
    public void markFailed(Long executionId, String errorCode, String errorMessage,
            boolean retryable, boolean resultUnknown, Date endTime)
    {
        executionMapper.markFailed(executionId, errorCode, errorMessage, retryable, resultUnknown, endTime);
    }

    @Override
    @Transactional
    public int failStaleRunning(Date staleBefore, String beforeSendStage,
            String beforeSendCode, String afterSendCode)
    {
        stageMapper.failStaleRunningStages(staleBefore, beforeSendCode, afterSendCode);
        return executionMapper.failStaleRunning(staleBefore, beforeSendCode, afterSendCode);
    }
}
