package com.ruoyi.integration.client.u8.config;

/** A server-registered business operation that tasks may select. */
public record U8GatewayOperation(String code, String method, String path, boolean enabled)
{
}
