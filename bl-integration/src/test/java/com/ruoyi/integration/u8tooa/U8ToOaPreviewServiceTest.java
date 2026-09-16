package com.ruoyi.integration.u8tooa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.OaToU8PreviewInput;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.config.ResultCardinality;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.u8tooa.config.IncrementalSyncConfig;
import com.ruoyi.integration.u8tooa.config.OaProcessRequest;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfig;
import com.ruoyi.integration.u8tooa.config.U8ToOaTaskConfigValidator;

class U8ToOaPreviewServiceTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rendersConfiguredOaPayloadWithoutCallingOa()
    {
        ReadOnlySqlExecutor sql = (source, statement, cardinality, parameters) -> new ReadOnlySqlResult(
                ResultCardinality.ONE, json.createObjectNode().put("id", "1001").put("amount", 18));
        U8ToOaTaskConfig config = new U8ToOaTaskConfig(Map.of(), List.of(
                new ReadQueryStep("header", 1, "u8", "SELECT id, amount", ResultCardinality.ONE, Map.of())),
                new OaProcessRequest("data.header.id", json.createObjectNode()
                        .put("documentNo", "{{trigger.masterId}}")
                        .put("amount", "{{data.header.amount}}")),
                new IncrementalSyncConfig("u8", "SELECT CURRENT_TIMESTAMP", "SELECT 1", "SELECT 1", "SELECT 1",
                        LocalDateTime.of(2026, 1, 1, 0, 0), 60));

        U8ToOaPreview result = new U8ToOaPreviewService(raw -> config, sql, new SqlVariableResolver(),
                new JsonTemplateRenderer(json), json).preview(json.createObjectNode(),
                        new OaToU8PreviewInput("DOC-1", null, null));

        assertEquals("DOC-1", result.request().path("documentNo").asText());
        assertEquals(18, result.request().path("amount").asInt());
    }
}
