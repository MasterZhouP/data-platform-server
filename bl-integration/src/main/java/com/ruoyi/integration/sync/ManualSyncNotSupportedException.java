package com.ruoyi.integration.sync;

public class ManualSyncNotSupportedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public ManualSyncNotSupportedException(String taskCode)
    {
        super("任务不支持平台手动推送: " + taskCode);
    }
}
