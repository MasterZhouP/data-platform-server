package com.ruoyi.integration.sync.schedule;

/** Optional bridge from the integration control plane to the host Quartz scheduler. */
public interface IntegrationTaskScheduler
{
    void synchronize(String taskCode, String taskName, boolean enabled, String cronExpression);
}
