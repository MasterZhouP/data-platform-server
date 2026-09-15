package com.ruoyi.integration.client.oa.runtime;

import java.util.Map;
import com.ruoyi.integration.client.oa.OaProcessOperations;
import com.ruoyi.integration.client.oa.OaProcessRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedOaProcessOperationsTest {
    @Test
    void delegatesEveryBusinessCallToTheEnabledManagedAccount() {
        OaRestSessionRegistry registry = mock(OaRestSessionRegistry.class);
        OaRestSession session = mock(OaRestSession.class);
        OaProcessOperations operations = mock(OaProcessOperations.class);
        OaProcessRef ref = new OaProcessRef("summary-1", "affair-1", "process-1");
        when(registry.require("oa-default")).thenReturn(session);
        when(session.operations()).thenReturn(operations);
        when(operations.start(Map.of("key", "value"))).thenReturn(ref);

        ManagedOaProcessOperations managed = new ManagedOaProcessOperations(registry);

        assertEquals(ref, managed.start(Map.of("key", "value")));
        managed.cancel(ref);
        verify(operations).cancel(ref);
    }
}
