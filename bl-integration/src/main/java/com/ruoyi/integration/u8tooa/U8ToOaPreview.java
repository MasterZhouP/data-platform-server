package com.ruoyi.integration.u8tooa;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/** Read-only preview of U8 source data and the OA process payload. */
public record U8ToOaPreview(Map<String, Object> data, JsonNode request)
{
}
