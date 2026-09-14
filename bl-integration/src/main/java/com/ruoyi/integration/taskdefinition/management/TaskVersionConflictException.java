package com.ruoyi.integration.taskdefinition.management;

/** 管理员基于旧页面版本保存时必须重新加载，防止覆盖其他人的草稿。 */
public class TaskVersionConflictException extends RuntimeException
{
    public TaskVersionConflictException(String taskCode)
    {
        super("任务配置已被其他操作更新，请刷新后再保存: " + taskCode);
    }
}
