package com.ruoyi.integration.execution.service;

import com.ruoyi.integration.execution.domain.IntegrationExecution;

public class ExecutionConflictException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final IntegrationExecution existingExecution;

    public ExecutionConflictException(String message)
    {
        super(message);
        this.existingExecution = null;
    }

    public ExecutionConflictException(String message, Throwable cause)
    {
        super(message, cause);
        this.existingExecution = null;
    }

    public ExecutionConflictException(String message, IntegrationExecution existingExecution)
    {
        super(message);
        this.existingExecution = existingExecution;
    }

    public ExecutionConflictException(String message, IntegrationExecution existingExecution, Throwable cause)
    {
        super(message, cause);
        this.existingExecution = existingExecution;
    }

    public IntegrationExecution getExistingExecution()
    {
        return existingExecution;
    }
}
