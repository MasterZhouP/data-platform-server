package com.ruoyi.integration.client.oa;

public class OaClientException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final boolean retryable;
    private final boolean resultUnknown;

    private OaClientException(String errorCode, String message, boolean retryable, boolean resultUnknown)
    {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.resultUnknown = resultUnknown;
    }

    public static OaClientException retryable(String code, String message)
    {
        return new OaClientException(code, message, true, false);
    }

    public static OaClientException nonRetryable(String code, String message)
    {
        return new OaClientException(code, message, false, false);
    }

    public static OaClientException resultUnknown(String code, String message)
    {
        return new OaClientException(code, message, false, true);
    }

    public String getErrorCode() { return errorCode; }
    public boolean isRetryable() { return retryable; }
    public boolean isResultUnknown() { return resultUnknown; }
}
