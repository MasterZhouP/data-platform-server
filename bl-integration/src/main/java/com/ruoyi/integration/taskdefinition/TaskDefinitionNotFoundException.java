package com.ruoyi.integration.taskdefinition;

/** Thrown when a task has no published executable revision. */
public class TaskDefinitionNotFoundException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public TaskDefinitionNotFoundException(String taskCode)
    {
        super("集成任务不存在或没有已发布版本: " + taskCode);
    }
}
