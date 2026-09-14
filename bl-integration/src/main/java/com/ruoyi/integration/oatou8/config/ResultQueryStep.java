package com.ruoyi.integration.oatou8.config;

import java.util.Map;

/** A bounded read-only query that runs only after U8 has confirmed business success. */
public record ResultQueryStep(String code, int order, String datasourceKey, String sql,
        ResultCardinality cardinality, Map<String, String> parameterBindings, boolean required,
        int initialDelayMs, int intervalMs, int maxAttempts, Map<String, String> outputMappings)
{
}
