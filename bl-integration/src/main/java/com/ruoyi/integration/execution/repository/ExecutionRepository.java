package com.ruoyi.integration.execution.repository;

import java.util.Date;
import java.util.List;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;

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
            String responsePayload, Date endTime);

    void markFailed(Long executionId, String errorCode, String errorMessage,
            boolean retryable, boolean resultUnknown, Date endTime);

    int failStaleRunning(Date staleBefore, String beforeSendStage,
            String beforeSendCode, String afterSendCode);
}
