package com.ruoyi.integration.client.u8;

/**
 * U8 通信异常。
 * requestMayHaveReachedU8 为 true 时，业务单据可能已经写入 U8，调用方必须进入 RESULT_UNKNOWN 而不能自动重推。
 */
public class U8TransportException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final boolean requestMayHaveReachedU8;

    public U8TransportException(String message, boolean requestMayHaveReachedU8)
    {
        super(message);
        this.requestMayHaveReachedU8 = requestMayHaveReachedU8;
    }

    public U8TransportException(String message, boolean requestMayHaveReachedU8, Throwable cause)
    {
        super(message, cause);
        this.requestMayHaveReachedU8 = requestMayHaveReachedU8;
    }

    public boolean requestMayHaveReachedU8()
    {
        return requestMayHaveReachedU8;
    }
}
