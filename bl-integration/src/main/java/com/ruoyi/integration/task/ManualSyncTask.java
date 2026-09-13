package com.ruoyi.integration.task;

public interface ManualSyncTask extends IntegrationTaskHandler
{
    Object preview(String masterId);

    void validateManualAcceptance(String masterId, boolean force);

    default TaskAction manualAction(boolean force)
    {
        return force ? TaskAction.CANCEL_RECREATE : TaskAction.CREATE;
    }
}
