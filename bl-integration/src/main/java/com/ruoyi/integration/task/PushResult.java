package com.ruoyi.integration.task;

/** Confirmed successful Handler result. Payloads are masked again before persistence. */
public record PushResult(String businessKey, String requestPayload, String responsePayload,
        PushOutcome outcome, String message)
{
    public static PushResult success(String businessKey, String requestPayload, String responsePayload)
    {
        return new PushResult(businessKey, requestPayload, responsePayload, PushOutcome.SUCCESS, null);
    }

    public static PushResult skipped(String businessKey, String message)
    {
        return new PushResult(businessKey, null, null, PushOutcome.SKIPPED, message);
    }
}
