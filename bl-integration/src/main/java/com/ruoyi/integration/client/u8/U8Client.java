package com.ruoyi.integration.client.u8;

/**
 * 旧代码型任务的 U8 调用兼容边界。
 * 新的 OA→U8 配置任务必须使用 U8Gateway，避免在每条任务链重复实现 token、tradeId 和结果不确定性控制。
 */
public interface U8Client
{
    U8CallResult post(String endpoint, String requestPayload);
}
