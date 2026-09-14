package com.ruoyi.integration.client.u8;

/**
 * U8 调用后的可持久化结论。
 * responsePayload 只在已收到成功 HTTP 响应时保留，执行器还需依据任务版本的成功规则确认是否真正推单成功。
 */
public record U8CallResult(U8CallStatus status, String responsePayload, String errorCode, String errorMessage)
{
}
