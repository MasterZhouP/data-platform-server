package com.ruoyi.integration.task;

import com.ruoyi.integration.taskdefinition.TaskType;

/** 已发布的配置任务没有对应类型执行器时阻止运行，避免错误回退到代码型 Handler。 */
public class UnknownTaskExecutorException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public UnknownTaskExecutorException(TaskType taskType)
    {
        super("未注册的集成任务执行器类型: " + taskType);
    }
}
