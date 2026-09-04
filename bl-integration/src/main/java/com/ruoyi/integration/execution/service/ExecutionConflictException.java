package com.ruoyi.integration.execution.service;

public class ExecutionConflictException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public ExecutionConflictException(String message)
    {
        super(message);
    }

    public ExecutionConflictException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
