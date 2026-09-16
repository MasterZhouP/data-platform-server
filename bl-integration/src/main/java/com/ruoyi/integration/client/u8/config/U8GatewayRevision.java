package com.ruoyi.integration.client.u8.config;

/** Immutable non-secret U8 gateway revision. */
public record U8GatewayRevision(String revisionId, String connectionKey, String environment, String baseUrl,
        String tokenPath, String tradeIdPath, String tokenPointer, String tradeIdPointer,
        String tokenParameterName, String tradeIdParameterName, int tokenCacheSeconds,
        int connectTimeoutMillis, int readTimeoutMillis, String accountParameterNamesJson,
        String operationRegistryJson,
        String secretId, String checksum)
{
}
