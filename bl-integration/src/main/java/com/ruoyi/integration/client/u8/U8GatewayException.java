package com.ruoyi.integration.client.u8;

/** 网关在发起 U8 业务请求前发现的确定性配置或认证错误。 */
public class U8GatewayException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final String errorCode;

    public U8GatewayException(String errorCode, String message)
    {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode()
    {
        return errorCode;
    }
}
