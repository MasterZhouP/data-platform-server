package com.ruoyi.integration.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class PushHandlerRegistryTest
{
    @Test
    void findsHandlerByStableTaskCode()
    {
        IntegrationTaskHandler handler = handler("TEST_TASK");

        PushHandlerRegistry registry = new PushHandlerRegistry(List.of(handler));

        assertEquals(handler, registry.require("TEST_TASK"));
    }

    @Test
    void rejectsUnknownAndDuplicateTaskCodes()
    {
        assertThrows(UnknownTaskException.class,
                () -> new PushHandlerRegistry(List.of()).require("MISSING"));
        assertThrows(IllegalStateException.class,
                () -> new PushHandlerRegistry(List.of(handler("DUP"), handler("DUP"))));
    }

    @Test
    void failureFactoriesPreserveRetrySafety()
    {
        PushFailureException safe = PushFailureException.retryable("OA_TIMEOUT", "OA 暂时不可用");
        PushFailureException unknown = PushFailureException.resultUnknown("U8_TIMEOUT", "U8 返回超时");

        assertTrue(safe.isRetryable());
        assertFalse(safe.isResultUnknown());
        assertFalse(unknown.isRetryable());
        assertTrue(unknown.isResultUnknown());
    }

    private IntegrationTaskHandler handler(String taskCode)
    {
        IntegrationTaskHandler handler = mock(IntegrationTaskHandler.class);
        when(handler.taskCode()).thenReturn(taskCode);
        return handler;
    }
}
