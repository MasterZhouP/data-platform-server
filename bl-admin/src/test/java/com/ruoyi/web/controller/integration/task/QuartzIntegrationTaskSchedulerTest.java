package com.ruoyi.web.controller.integration.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;

class QuartzIntegrationTaskSchedulerTest
{
    @Test
    void createsAndResumesAnEnabledPublishedTask() throws Exception
    {
        ISysJobService jobs = org.mockito.Mockito.mock(ISysJobService.class);
        when(jobs.selectJobList(any())).thenReturn(List.of());
        when(jobs.checkCronExpressionIsValid("0 0/5 * * * ?")).thenReturn(true);

        new QuartzIntegrationTaskScheduler(jobs).synchronize(
                "U8_RECEIPT", "收货推OA", true, "0 0/5 * * * ?");

        verify(jobs).insertJob(any());
        verify(jobs).resumeJob(org.mockito.ArgumentMatchers.argThat(job ->
                "integrationSyncTask.run('U8_RECEIPT')".equals(job.getInvokeTarget())
                        && "0 0/5 * * * ?".equals(job.getCronExpression())));
    }

    @Test
    void updatesAnExistingTaskAndKeepsDisabledTaskPaused() throws Exception
    {
        ISysJobService jobs = org.mockito.Mockito.mock(ISysJobService.class);
        SysJob existing = new SysJob();
        existing.setJobId(8L);
        when(jobs.selectJobList(any())).thenReturn(List.of(existing));
        when(jobs.checkCronExpressionIsValid("0 0 2 * * ?")).thenReturn(true);

        new QuartzIntegrationTaskScheduler(jobs).synchronize(
                "U8_RECEIPT", "收货推OA", false, "0 0 2 * * ?");

        verify(jobs).updateJob(org.mockito.ArgumentMatchers.argThat(job -> {
            assertEquals("1", job.getStatus());
            assertEquals("0 0 2 * * ?", job.getCronExpression());
            return true;
        }));
        verify(jobs, never()).resumeJob(any());
    }
}
