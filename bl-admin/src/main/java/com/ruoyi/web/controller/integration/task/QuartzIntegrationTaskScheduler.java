package com.ruoyi.web.controller.integration.task;

import java.util.List;
import com.ruoyi.common.constant.ScheduleConstants;
import com.ruoyi.common.exception.job.TaskException;
import com.ruoyi.integration.sync.schedule.IntegrationTaskScheduler;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;

/** Mirrors published configurable tasks into the host Quartz job table. */
@Component
public class QuartzIntegrationTaskScheduler implements IntegrationTaskScheduler
{
    private static final String GROUP = "INTEGRATION";
    private final ISysJobService jobs;

    public QuartzIntegrationTaskScheduler(ISysJobService jobs)
    {
        this.jobs = jobs;
    }

    @Override
    public void synchronize(String taskCode, String taskName, boolean enabled, String cronExpression)
    {
        if (!jobs.checkCronExpressionIsValid(cronExpression))
        {
            throw new IllegalArgumentException("Cron表达式不合法: " + cronExpression);
        }
        String target = "integrationSyncTask.run('" + taskCode + "')";
        SysJob query = new SysJob();
        query.setInvokeTarget(target);
        List<SysJob> existing = jobs.selectJobList(query);
        SysJob job = existing.isEmpty() ? new SysJob() : existing.get(0);
        job.setJobName(taskName);
        job.setJobGroup(GROUP);
        job.setInvokeTarget(target);
        job.setCronExpression(cronExpression);
        job.setMisfirePolicy(ScheduleConstants.MISFIRE_DO_NOTHING);
        job.setConcurrent("1");
        job.setStatus(enabled ? ScheduleConstants.Status.NORMAL.getValue() : ScheduleConstants.Status.PAUSE.getValue());
        try
        {
            if (existing.isEmpty())
            {
                jobs.insertJob(job);
                if (enabled)
                {
                    jobs.resumeJob(job);
                }
            }
            else
            {
                jobs.updateJob(job);
            }
        }
        catch (SchedulerException | TaskException ex)
        {
            throw new IllegalStateException("同步集成任务定时配置失败: " + taskCode, ex);
        }
    }
}
