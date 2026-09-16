package com.ruoyi.integration.client.u8.runtime;

import com.ruoyi.integration.client.u8.U8Gateway;

/** One immutable U8 gateway revision with its private token cache. */
public final class U8GatewaySession
{
    private final String connectionKey;
    private final String revisionId;
    private final U8Gateway gateway;
    private final Runnable authenticateAction;
    private final Runnable closeAction;

    public U8GatewaySession(String connectionKey, String revisionId, U8Gateway gateway,
            Runnable authenticateAction, Runnable closeAction)
    {
        this.connectionKey = connectionKey;
        this.revisionId = revisionId;
        this.gateway = gateway;
        this.authenticateAction = authenticateAction;
        this.closeAction = closeAction;
    }

    public String connectionKey() { return connectionKey; }
    public String revisionId() { return revisionId; }
    public U8Gateway gateway() { return gateway; }
    public void authenticate() { authenticateAction.run(); }
    public void close() { closeAction.run(); }
}
