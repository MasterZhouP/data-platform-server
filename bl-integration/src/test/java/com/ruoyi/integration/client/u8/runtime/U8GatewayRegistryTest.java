package com.ruoyi.integration.client.u8.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import java.util.concurrent.atomic.AtomicInteger;
import com.ruoyi.integration.client.u8.U8Gateway;
import org.junit.jupiter.api.Test;

class U8GatewayRegistryTest
{
    @Test
    void retiresAnOldAccountOnlyAfterItsInFlightLeaseCloses()
    {
        U8GatewayRegistry registry = new U8GatewayRegistry();
        AtomicInteger oldClosed = new AtomicInteger();
        AtomicInteger newClosed = new AtomicInteger();
        U8Gateway oldGateway = mock(U8Gateway.class);
        U8Gateway newGateway = mock(U8Gateway.class);
        U8GatewaySession oldSession = session("u8-default", "rev-1", oldGateway, oldClosed);
        U8GatewaySession newSession = session("u8-default", "rev-2", newGateway, newClosed);

        registry.activate(oldSession);
        U8GatewayLease inFlight = registry.acquire("u8-default");
        registry.activate(newSession);
        assertEquals(0, oldClosed.get());
        inFlight.close();
        assertEquals(1, oldClosed.get());
        registry.disable("u8-default");
        assertEquals(1, newClosed.get());
    }

    private U8GatewaySession session(String key, String revision, U8Gateway gateway, AtomicInteger closed)
    {
        return new U8GatewaySession(key, revision, gateway, () -> { }, closed::incrementAndGet);
    }
}
