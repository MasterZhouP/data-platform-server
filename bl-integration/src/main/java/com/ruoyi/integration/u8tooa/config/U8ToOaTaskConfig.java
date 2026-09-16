package com.ruoyi.integration.u8tooa.config;

import java.util.List;
import java.util.Map;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;

/** Immutable, deliberately small configuration contract for a U8-to-OA push task. */
public record U8ToOaTaskConfig(Map<String, String> constants, List<ReadQueryStep> dataSteps,
        OaProcessRequest oa, IncrementalSyncConfig sync)
{
}
