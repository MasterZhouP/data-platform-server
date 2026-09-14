package com.ruoyi.integration.taskdefinition.management;

/** 未经校验的草稿不能成为生产版本。 */
public class TaskDraftNotValidatedException extends RuntimeException
{
    public TaskDraftNotValidatedException(String taskCode)
    {
        super("任务草稿尚未通过校验，不能发布: " + taskCode);
    }
}
