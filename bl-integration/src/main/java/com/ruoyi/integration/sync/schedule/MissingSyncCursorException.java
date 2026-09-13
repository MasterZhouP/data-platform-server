package com.ruoyi.integration.sync.schedule;

public class MissingSyncCursorException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public MissingSyncCursorException(String taskCode)
    {
        super("定时任务未配置首次同步游标: " + taskCode);
    }
}
