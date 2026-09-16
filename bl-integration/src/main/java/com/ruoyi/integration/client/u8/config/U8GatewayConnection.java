package com.ruoyi.integration.client.u8.config;

/** Mutable directory pointers for one managed U8 gateway account. */
public record U8GatewayConnection(String connectionKey, String connectionName, boolean enabled,
        String activeRevisionId, String draftRevisionId, long rowVersion)
{
}
