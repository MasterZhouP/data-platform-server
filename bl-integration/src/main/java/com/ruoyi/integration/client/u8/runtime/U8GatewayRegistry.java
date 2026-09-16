package com.ruoyi.integration.client.u8.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ruoyi.integration.configuration.ConfigurationException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** Atomically swaps U8 account revisions while allowing in-flight calls to drain. */
@Component
public class U8GatewayRegistry
{
    private final Map<String, Slot> active = new HashMap<>();

    public synchronized void activate(U8GatewaySession session)
    {
        if (session == null || session.connectionKey() == null || session.connectionKey().isBlank())
        {
            throw new ConfigurationException("INVALID_ARGUMENT", 400, "待启用U8账户会话不完整");
        }
        Slot previous = active.put(session.connectionKey(), new Slot(session));
        retire(previous);
    }

    public synchronized U8GatewayLease acquire(String connectionKey)
    {
        Slot slot = active.get(connectionKey);
        if (slot == null || slot.retired)
        {
            throw new ConfigurationException("U8_GATEWAY_UNAVAILABLE", 503, "U8公共账户尚未启用");
        }
        slot.leases++;
        return new Lease(slot);
    }

    public synchronized void disable(String connectionKey)
    {
        retire(active.remove(connectionKey));
    }

    @PreDestroy
    public synchronized void closeAll()
    {
        for (Slot slot : active.values())
        {
            slot.retired = true;
            closeWhenDrained(slot);
        }
        active.clear();
    }

    private void release(Slot slot)
    {
        synchronized (this)
        {
            if (slot.leases > 0) slot.leases--;
            closeWhenDrained(slot);
        }
    }

    private void retire(Slot slot)
    {
        if (slot == null) return;
        slot.retired = true;
        closeWhenDrained(slot);
    }

    private void closeWhenDrained(Slot slot)
    {
        if (!slot.retired || slot.leases != 0 || slot.closed) return;
        slot.closed = true;
        try { slot.session.close(); } catch (RuntimeException ignored) { }
    }

    private final class Lease implements U8GatewayLease
    {
        private final Slot slot;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(Slot slot) { this.slot = slot; }
        @Override public String connectionKey() { return slot.session.connectionKey(); }
        @Override public String revisionId() { return slot.session.revisionId(); }
        @Override public com.ruoyi.integration.client.u8.U8Gateway gateway() { return slot.session.gateway(); }
        @Override public void close() { if (closed.compareAndSet(false, true)) release(slot); }
    }

    private static final class Slot
    {
        private final U8GatewaySession session;
        private int leases;
        private boolean retired;
        private boolean closed;
        private Slot(U8GatewaySession session) { this.session = session; }
    }
}
