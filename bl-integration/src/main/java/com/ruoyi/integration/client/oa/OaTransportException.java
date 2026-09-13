package com.ruoyi.integration.client.oa;

public class OaTransportException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final boolean resultUnknown;

    public OaTransportException(String message, boolean resultUnknown)
    {
        super(message);
        this.resultUnknown = resultUnknown;
    }

    public OaTransportException(String message, boolean resultUnknown, Throwable cause)
    {
        super(message, cause);
        this.resultUnknown = resultUnknown;
    }

    public boolean isResultUnknown()
    {
        return resultUnknown;
    }
}
