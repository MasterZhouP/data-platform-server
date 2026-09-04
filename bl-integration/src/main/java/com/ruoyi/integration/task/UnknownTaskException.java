package com.ruoyi.integration.task;

public class UnknownTaskException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public UnknownTaskException(String taskCode)
    {
        super("未注册的集成任务: " + taskCode);
    }
}
