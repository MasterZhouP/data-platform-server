package com.ruoyi.integration.execution.service;

public class RetryRejectedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public RetryRejectedException(String message)
    {
        super(message);
    }
}
