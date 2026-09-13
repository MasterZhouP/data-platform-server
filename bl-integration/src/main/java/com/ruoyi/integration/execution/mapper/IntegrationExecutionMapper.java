package com.ruoyi.integration.execution.mapper;

import java.util.Date;
import java.util.List;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import org.apache.ibatis.annotations.Param;

public interface IntegrationExecutionMapper
{
    int insertExecution(IntegrationExecution execution);

    IntegrationExecution selectExecutionById(Long executionId);

    IntegrationExecution selectExecutionByIdForUpdate(Long executionId);

    IntegrationExecution selectExecutionByDedupKey(String dedupKey);

    List<IntegrationExecution> selectExecutionList(IntegrationExecution query);

    int countRetryChildren(Long executionId);

    int claimPending(@Param("executionId") Long executionId, @Param("startTime") Date startTime);

    List<Long> selectPendingIds(@Param("limit") int limit);

    int updateCurrentStage(@Param("executionId") Long executionId, @Param("stage") String stage,
            @Param("updateTime") Date updateTime);

    int markSuccess(@Param("executionId") Long executionId, @Param("businessKey") String businessKey,
            @Param("requestPayload") String requestPayload, @Param("responsePayload") String responsePayload,
            @Param("retainDedup") boolean retainDedup, @Param("endTime") Date endTime);

    int markFailed(@Param("executionId") Long executionId, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("retryable") boolean retryable,
            @Param("resultUnknown") boolean resultUnknown, @Param("endTime") Date endTime);

    int markSkipped(@Param("executionId") Long executionId, @Param("businessKey") String businessKey,
            @Param("reason") String reason, @Param("endTime") Date endTime);

    int markResultUnknown(@Param("executionId") Long executionId, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("endTime") Date endTime);

    int failStaleRunning(@Param("staleBefore") Date staleBefore,
            @Param("beforeSendCode") String beforeSendCode, @Param("afterSendCode") String afterSendCode);
}
