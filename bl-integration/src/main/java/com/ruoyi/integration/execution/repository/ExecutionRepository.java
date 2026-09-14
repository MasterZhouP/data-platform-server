package com.ruoyi.integration.execution.repository;

import java.util.Date;
import java.util.List;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;

/**
 * 执行状态持久化边界；涉及外部系统确认的状态更新必须原子化，防止重试时重复推送。
 */
public interface ExecutionRepository
{
    void insert(IntegrationExecution execution);

    void insertStage(IntegrationExecutionStage stage);

    void updateStage(IntegrationExecutionStage stage);

    IntegrationExecution findById(Long executionId);

    IntegrationExecution findByIdForUpdate(Long executionId);

    List<IntegrationExecution> findList(IntegrationExecution query);

    List<IntegrationExecutionStage> findStages(Long executionId);

    boolean hasRetryChild(Long executionId);

    boolean claimPending(Long executionId, Date startTime);

    List<Long> findPendingIds(int limit);

    void updateCurrentStage(Long executionId, String stage);

    void markSuccess(Long executionId, String businessKey, String requestPayload,
            String responsePayload, boolean retainDedup, Date endTime);

    void markFailed(Long executionId, String errorCode, String errorMessage,
            boolean retryable, boolean resultUnknown, Date endTime);

    void markSkipped(Long executionId, String businessKey, String reason, Date endTime);

    void markResultUnknown(Long executionId, String errorCode, String errorMessage, Date endTime);

    /**
     * U8 成功响应是不可回退的业务边界，必须先持久化最小输出，后续失败才能只补跑后处理。
     */
    void checkpointU8Confirmed(Long executionId, String resultOutputsJson, String lastCompletedStage);

    void markPartialSuccess(Long executionId, String errorCode, String errorMessage,
            String lastCompletedStage, String resultOutputsJson, Date endTime);

    int failStaleRunning(Date staleBefore, String beforeSendStage,
            String beforeSendCode, String afterSendCode);
}
