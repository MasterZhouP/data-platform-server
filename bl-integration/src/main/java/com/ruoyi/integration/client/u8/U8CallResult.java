package com.ruoyi.integration.client.u8;

public record U8CallResult(U8CallStatus status, String responsePayload, String errorCode, String errorMessage)
{
}
