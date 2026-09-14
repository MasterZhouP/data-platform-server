package com.ruoyi.integration.oatou8.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.ExecutionVariableContext;

class JsonTemplateRendererTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rendersDeclaredValuesWithoutConvertingJsonTypesToText() throws Exception
    {
        ExecutionVariableContext context = new ExecutionVariableContext(
                Map.of("masterId", "M-100"), Map.of("bookCode", "001"),
                Map.of("header", Map.of("documentNo", "BX-01", "amount", 18)), Map.of(), Map.of());

        JsonNode rendered = new JsonTemplateRenderer(json).render(json.readTree("""
                {"masterId":"{{trigger.masterId}}","bookCode":"{{task.constants.bookCode}}",
                 "documentNo":"{{data.header.documentNo}}","amount":"{{data.header.amount}}",
                 "header":"{{data.header}}"}
                """), context);

        assertEquals("M-100", rendered.path("masterId").asText());
        assertEquals("001", rendered.path("bookCode").asText());
        assertEquals("BX-01", rendered.path("documentNo").asText());
        assertEquals(18, rendered.path("amount").intValue());
        assertEquals("BX-01", rendered.path("header").path("documentNo").asText());
    }

    @Test
    void rejectsAnEmbeddedOrUnknownVariableInsteadOfProducingAnAmbiguousPayload() throws Exception
    {
        ExecutionVariableContext context = new ExecutionVariableContext(Map.of("masterId", "M-100"), Map.of(), Map.of(), Map.of(), Map.of());
        JsonTemplateRenderer renderer = new JsonTemplateRenderer(json);

        TemplateRenderException exception = assertThrows(TemplateRenderException.class,
                () -> renderer.render(json.readTree("{\"id\":\"prefix-{{trigger.masterId}}\"}"), context));

        assertEquals("TEMPLATE_VARIABLE_INVALID", exception.code());
    }
}
