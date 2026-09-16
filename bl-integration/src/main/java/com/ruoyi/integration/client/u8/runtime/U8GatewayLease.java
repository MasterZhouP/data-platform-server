package com.ruoyi.integration.client.u8.runtime;

import com.ruoyi.integration.client.u8.U8Gateway;

/** Holds one active U8 gateway revision for the duration of a business call. */
public interface U8GatewayLease extends AutoCloseable
{
    String connectionKey();
    String revisionId();
    U8Gateway gateway();
    @Override void close();
}
