package com.ruoyi.integration.task;

public class PushFailureException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final boolean retryable;
    private final boolean resultUnknown;

    private PushFailureException(String errorCode, String message, boolean retryable, boolean resultUnknown)
    {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.resultUnknown = resultUnknown;
    }

    public static PushFailureException retryable(String errorCode, String message)
    {
        return new PushFailureException(errorCode, message, true, false);
    }

    public static PushFailureException nonRetryable(String errorCode, String message)
    {
        return new PushFailureException(errorCode, message, false, false);
    }

    public static PushFailureException resultUnknown(String errorCode, String message)
    {
        return new PushFailureException(errorCode, message, false, true);
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public boolean isRetryable()
    {
        return retryable;
    }

    public boolean isResultUnknown()
    {
        return resultUnknown;
    }
}
