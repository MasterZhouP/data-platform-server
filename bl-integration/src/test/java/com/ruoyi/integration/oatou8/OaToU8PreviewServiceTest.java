package com.ruoyi.integration.oatou8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.config.ResultCardinality;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlVariableResolver;
import org.junit.jupiter.api.Test;

class OaToU8PreviewServiceTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preparesSqlAndJsonWithoutOwningOrCallingAU8Gateway() throws Exception
    {
        ReadOnlySqlExecutor sql = mock(ReadOnlySqlExecutor.class);
        ObjectNode header = json.createObjectNode().put("code", "V-100");
        org.mockito.Mockito.when(sql.execute(eq("oa"), org.mockito.ArgumentMatchers.contains("select"),
                eq(ResultCardinality.ONE), anyMap())).thenReturn(new ReadOnlySqlResult(ResultCardinality.ONE, header));
        OaToU8PreviewService service = new OaToU8PreviewService(
                new TaskConfigValidator(json, Set.of("oa"), Set.of("VOUCHER_ADD")), sql,
                new SqlVariableResolver(), new JsonTemplateRenderer(json), json);

        OaToU8Preview result = service.preview(config(), new OaToU8PreviewInput("M-1", "F-1", "S-1"));

        verify(sql).execute(eq("oa"), org.mockito.ArgumentMatchers.contains("select"),
                eq(ResultCardinality.ONE), anyMap());
        assertEquals("V-100", result.request().path("voucher").asText());
        assertEquals("M-1", result.request().path("masterId").asText());
    }

    private ObjectNode config()
    {
        ObjectNode root = json.createObjectNode();
        root.putObject("constants");
        ObjectNode step = root.putArray("dataSteps").addObject();
        step.put("code", "header");
        step.put("order", 1);
        step.put("datasourceKey", "oa");
        step.put("sql", "select :masterId as code");
        step.put("cardinality", "ONE");
        step.putObject("parameterBindings").put("masterId", "trigger.masterId");
        ObjectNode u8 = root.putObject("u8");
        u8.put("operationCode", "VOUCHER_ADD");
        u8.put("path", "/api/voucher/add");
        u8.put("requestJsonTemplate", "{\"voucher\":\"{{data.header.code}}\",\"masterId\":\"{{trigger.masterId}}\"}");
        ObjectNode success = u8.putObject("successRule");
        success.put("pointer", "/code");
        success.putArray("allowedValues").add("0");
        u8.putArray("outputs");
        root.putArray("resultQueries");
        return root;
    }
}
