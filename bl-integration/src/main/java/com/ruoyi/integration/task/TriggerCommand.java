package com.ruoyi.integration.task;

/** Stable context accepted from a future authenticated OA adapter. */
public record TriggerCommand(String taskCode, String masterId, String formId, String summaryId,
        TaskAction action, TriggerSource triggerSource, boolean force)
{
    public TriggerCommand(String taskCode, String masterId, String formId, String summaryId)
    {
        this(taskCode, masterId, formId, summaryId, TaskAction.CREATE, TriggerSource.MANUAL, false);
    }

    public TriggerCommand
    {
        action = action == null ? TaskAction.CREATE : action;
        triggerSource = triggerSource == null ? TriggerSource.MANUAL : triggerSource;
    }
}
