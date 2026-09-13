package com.ruoyi.quartz.task;

import com.ruoyi.integration.sync.schedule.ScheduledSyncService;
import org.springframework.stereotype.Component;

/** Whitelisted RuoYi Quartz entry point. Invoke with integrationSyncTask.run('TASK_CODE'). */
@Component("integrationSyncTask")
public class IntegrationSyncTask
{
    private final ScheduledSyncService service;

    public IntegrationSyncTask(ScheduledSyncService service)
    {
        this.service = service;
    }

    public void run(String taskCode)
    {
        service.run(taskCode);
    }
}
