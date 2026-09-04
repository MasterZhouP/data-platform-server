package com.ruoyi.integration.task;

public interface ExecutionStageRecorder
{
    void started(String stage, String requestPayload);

    void succeeded(String stage, String responsePayload);

    void failed(String stage, String errorCode, String errorMessage);
}
