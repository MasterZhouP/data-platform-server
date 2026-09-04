package com.ruoyi.integration.pipeline.push;

import java.util.Date;
import com.ruoyi.integration.execution.domain.ExecutionStageStatus;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.support.SensitiveDataMasker;
import com.ruoyi.integration.task.ExecutionStageRecorder;

final class PersistentStageRecorder implements ExecutionStageRecorder
{
    private final Long executionId;
    private final ExecutionRepository repository;
    private final SensitiveDataMasker masker;
    private IntegrationExecutionStage current;

    PersistentStageRecorder(Long executionId, ExecutionRepository repository, SensitiveDataMasker masker)
    {
        this.executionId = executionId;
        this.repository = repository;
        this.masker = masker;
    }

    @Override
    public void started(String stage, String requestPayload)
    {
        Date now = new Date();
        current = new IntegrationExecutionStage();
        current.setExecutionId(executionId);
        current.setSequenceNo(repository.findStages(executionId).size() + 1);
        current.setStage(stage);
        current.setStageStatus(ExecutionStageStatus.STARTED.name());
        current.setRequestPayload(masker.mask(requestPayload));
        current.setStartTime(now);
        current.setCreateTime(now);
        current.setUpdateTime(now);
        repository.updateCurrentStage(executionId, stage);
        repository.insertStage(current);
    }

    @Override
    public void succeeded(String stage, String responsePayload)
    {
        if (current == null || !stage.equals(current.getStage()))
        {
            started(stage, null);
        }
        current.setStageStatus(ExecutionStageStatus.SUCCESS.name());
        current.setResponsePayload(masker.mask(responsePayload));
        current.setEndTime(new Date());
        current.setUpdateTime(new Date());
        repository.updateStage(current);
        current = null;
    }

    @Override
    public void failed(String stage, String errorCode, String errorMessage)
    {
        if (current == null || !stage.equals(current.getStage()))
        {
            started(stage, null);
        }
        failCurrent(errorCode, errorMessage);
    }

    void failCurrent(String errorCode, String errorMessage)
    {
        if (current == null)
        {
            return;
        }
        current.setStageStatus(ExecutionStageStatus.FAILED.name());
        current.setErrorCode(errorCode);
        current.setErrorMessage(masker.maskError(errorMessage));
        current.setEndTime(new Date());
        current.setUpdateTime(new Date());
        repository.updateStage(current);
        current = null;
    }
}
