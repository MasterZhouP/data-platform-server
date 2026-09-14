package com.ruoyi.integration.client.u8;

/** U8 网关的本地配置与认证缓存状态；健康检查不主动调用业务接口。 */
public record U8ConnectionHealth(boolean configured, boolean tokenCached, String message)
{
}
