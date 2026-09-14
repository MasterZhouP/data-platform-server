package com.ruoyi.integration.taskdefinition;

/** Thrown when an administrator has stopped a task before it enters the asynchronous pipeline. */
public class TaskDisabledException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public TaskDisabledException(String taskCode)
    {
        super("集成任务已停用: " + taskCode);
    }
}
