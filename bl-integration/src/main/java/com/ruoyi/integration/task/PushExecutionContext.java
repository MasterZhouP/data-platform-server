package com.ruoyi.integration.task;

/** Immutable identifiers available to a Handler for one execution attempt. */
public record PushExecutionContext(Long executionId, String taskCode, String masterId,
        String businessKey, String formId, String summaryId, int retryCount,
        TaskAction action, TriggerSource triggerSource, boolean force)
{
}
