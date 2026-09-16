package com.ruoyi.integration.client.u8.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.u8.U8Gateway;
import com.ruoyi.integration.client.u8.U8GatewayException;
import com.ruoyi.integration.client.u8.config.U8GatewayCatalog;
import com.ruoyi.integration.client.u8.config.U8GatewayRevision;
import com.ruoyi.integration.client.u8.config.U8GatewaySecretStore;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;

class U8GatewayRuntimeServiceTest
{
    @Test
    void activatesOnlyTheAuthenticatedCandidate()
    {
        U8GatewayCatalog catalog = mock(U8GatewayCatalog.class);
        U8GatewaySecretStore secrets = mock(U8GatewaySecretStore.class);
        U8GatewayFactory factory = mock(U8GatewayFactory.class);
        U8GatewayRegistry registry = mock(U8GatewayRegistry.class);
        U8GatewayRevision revision = revision("rev-1");
        AtomicInteger authenticated = new AtomicInteger();
        U8GatewaySession session = new U8GatewaySession("u8-default", "rev-1", mock(U8Gateway.class),
                authenticated::incrementAndGet, () -> { });
        when(catalog.draft("u8-default", new RevisionToken("rev-1"))).thenReturn(revision);
        when(secrets.read("u8-default", new RevisionToken("rev-1"), "secret-1")).thenReturn(Map.of("account", "value"));
        when(factory.create(eq(revision), any())).thenReturn(session);
        when(catalog.activatePointer("u8-default", new RevisionToken("rev-1"), null)).thenReturn(true);

        var result = new U8GatewayRuntimeService(catalog, secrets, factory, registry, new ObjectMapper())
                .activate("u8-default", "rev-1", null);

        assertEquals("SUCCESS", result.path("status").asText());
        assertEquals(1, authenticated.get());
        verify(registry).activate(session);
    }

    @Test
    void failedAuthenticationReturnsSafeResultAndDoesNotPromoteTheCandidate()
    {
        U8GatewayCatalog catalog = mock(U8GatewayCatalog.class);
        U8GatewaySecretStore secrets = mock(U8GatewaySecretStore.class);
        U8GatewayFactory factory = mock(U8GatewayFactory.class);
        U8GatewayRegistry registry = mock(U8GatewayRegistry.class);
        U8GatewayRevision revision = revision("rev-2");
        AtomicInteger closed = new AtomicInteger();
        U8GatewaySession session = new U8GatewaySession("u8-default", "rev-2", mock(U8Gateway.class),
                () -> { throw new U8GatewayException("U8_TOKEN_FAILED", "认证失败"); }, closed::incrementAndGet);
        when(catalog.draft("u8-default", null)).thenReturn(revision);
        when(secrets.read("u8-default", new RevisionToken("rev-2"), "secret-2")).thenReturn(Map.of("account", "value"));
        when(factory.create(eq(revision), any())).thenReturn(session);

        var result = new U8GatewayRuntimeService(catalog, secrets, factory, registry, new ObjectMapper())
                .testDraft("u8-default", null);

        assertEquals("FAILED", result.path("status").asText());
        assertEquals("U8_TOKEN_FAILED", result.path("errorCode").asText());
        assertEquals(1, closed.get());
    }

    private U8GatewayRevision revision(String id)
    {
        return new U8GatewayRevision(id, "u8-default", "TEST", "https://u8.example.test", "/system/token",
                "/system/tradeid", "/token/id", "/trade/id", "token", "tradeid", 300, 5000, 15000,
                "[\"account\"]", "[{\"code\":\"VOUCHER_ADD\",\"method\":\"POST\",\"path\":\"/api/voucher/add\",\"enabled\":true}]",
                "secret-" + id.substring(id.length() - 1), "checksum");
    }
}
