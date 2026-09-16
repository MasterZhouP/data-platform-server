package com.ruoyi.integration.client.oa.runtime;

import java.util.Map;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import com.ruoyi.integration.configuration.ConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedOaProcessOperationsTest {
    @Test
    void delegatesEveryBusinessCallToTheOnlyEnabledAccountRegardlessOfItsKey() {
        OaRestSessionRegistry registry = new OaRestSessionRegistry();
        OaRestSession session = mock(OaRestSession.class);
        OaProcessOperations operations = mock(OaProcessOperations.class);
        OaProcessRef ref = new OaProcessRef("summary-1", "affair-1", "process-1");
        when(session.connectionKey()).thenReturn("oa-test");
        when(session.operations()).thenReturn(operations);
        when(operations.start(Map.of("key", "value"))).thenReturn(ref);
        registry.activate(session);

        ManagedOaProcessOperations managed = new ManagedOaProcessOperations(registry);

        assertEquals(ref, managed.start(Map.of("key", "value")));
        managed.cancel(ref);
        verify(operations).cancel(ref);
    }

    @Test
    void reportsUnavailableWhenNoAccountIsEnabled() {
        ManagedOaProcessOperations managed = new ManagedOaProcessOperations(new OaRestSessionRegistry());

        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> managed.start(Map.of("key", "value")));

        assertEquals("OA_REST_UNAVAILABLE", error.code());
    }

    @Test
    void refusesToChooseWhenMultipleAccountsAreEnabled() {
        OaRestSessionRegistry registry = new OaRestSessionRegistry();
        OaRestSession first = mock(OaRestSession.class);
        OaRestSession second = mock(OaRestSession.class);
        when(first.connectionKey()).thenReturn("oa-test");
        when(second.connectionKey()).thenReturn("oa-prod");
        registry.activate(first);
        registry.activate(second);

        ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> new ManagedOaProcessOperations(registry).start(Map.of("key", "value")));

        assertEquals("OA_REST_CONFLICT", error.code());
        assertTrue(error.getMessage().contains("oa-test"));
        assertTrue(error.getMessage().contains("oa-prod"));
    }
}
