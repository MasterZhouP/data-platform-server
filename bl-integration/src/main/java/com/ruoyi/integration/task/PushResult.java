package com.ruoyi.integration.task;

/** Confirmed successful Handler result. Payloads are masked again before persistence. */
public record PushResult(String businessKey, String requestPayload, String responsePayload)
{
    public static PushResult success(String businessKey, String requestPayload, String responsePayload)
    {
        return new PushResult(businessKey, requestPayload, responsePayload);
    }
}
