package com.ruoyi.integration.sync.schedule;

/** Resolves a published, configuration-backed scheduled task by its stable task code. */
public interface ScheduledTaskDefinitionProvider
{
    ScheduledTaskDefinition resolve(String taskCode);
}
