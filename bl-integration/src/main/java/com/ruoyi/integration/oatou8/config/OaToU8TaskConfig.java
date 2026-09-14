package com.ruoyi.integration.oatou8.config;

import java.util.List;
import java.util.Map;

/** Fixed OA-to-U8 execution skeleton persisted inside one immutable task revision. */
public record OaToU8TaskConfig(Map<String, String> constants, List<ReadQueryStep> dataSteps,
        U8BusinessRequest u8, List<ResultQueryStep> resultQueries)
{
}
