package com.ruoyi.integration.sync.schedule;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import com.ruoyi.quartz.task.IntegrationSyncTask;

class IntegrationSyncTaskTest
{
    @Test
    void delegatesQuartzInvocationToGenericScheduledService()
    {
        ScheduledSyncService service = mock(ScheduledSyncService.class);
        IntegrationSyncTask task = new IntegrationSyncTask(service);

        org.junit.jupiter.api.Assertions.assertTrue(task.getClass().getPackageName()
                .startsWith("com.ruoyi.quartz.task"));

        task.run("U8_TO_OA_SALES_OUTBOUND");

        verify(service).run("U8_TO_OA_SALES_OUTBOUND");
    }
}
