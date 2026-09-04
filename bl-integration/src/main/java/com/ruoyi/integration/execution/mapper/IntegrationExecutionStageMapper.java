package com.ruoyi.integration.execution.mapper;

import java.util.Date;
import java.util.List;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;
import org.apache.ibatis.annotations.Param;

public interface IntegrationExecutionStageMapper
{
    int insertExecutionStage(IntegrationExecutionStage stage);

    int updateExecutionStage(IntegrationExecutionStage stage);

    int failStaleRunningStages(@Param("staleBefore") Date staleBefore,
            @Param("beforeSendCode") String beforeSendCode, @Param("afterSendCode") String afterSendCode);

    List<IntegrationExecutionStage> selectByExecutionId(Long executionId);
}
