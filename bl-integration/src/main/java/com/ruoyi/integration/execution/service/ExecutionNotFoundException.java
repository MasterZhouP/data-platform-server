package com.ruoyi.integration.execution.service;

public class ExecutionNotFoundException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public ExecutionNotFoundException(Long executionId)
    {
        super("执行记录不存在: " + executionId);
    }
}
