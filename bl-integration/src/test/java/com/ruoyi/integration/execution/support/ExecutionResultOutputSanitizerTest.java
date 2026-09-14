package com.ruoyi.integration.execution.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class ExecutionResultOutputSanitizerTest
{
    private final ExecutionResultOutputSanitizer sanitizer = new ExecutionResultOutputSanitizer(new ObjectMapper());

    @Test
    void exposesBusinessOutputsButHidesResumeOnlyU8Scalars()
    {
        assertEquals("{\"voucherNo\":\"记-001\"}",
                sanitizer.sanitize("{\"voucherNo\":\"记-001\",\"__u8Response\":{\"tradeId\":\"T-9\"}}"));
    }

    @Test
    void hidesMalformedCheckpointPayloadRatherThanReturningItAsAnOperatorOutput()
    {
        assertNull(sanitizer.sanitize("not-json"));
    }
}
