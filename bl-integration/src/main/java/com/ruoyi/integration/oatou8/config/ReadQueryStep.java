package com.ruoyi.integration.oatou8.config;

import java.util.Map;

/** A named, ordered and read-only query used to prepare one U8 business request. */
public record ReadQueryStep(String code, int order, String datasourceKey, String sql,
        ResultCardinality cardinality, Map<String, String> parameterBindings)
{
}
