package com.ruoyi.integration.oatou8.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.config.ResponseOutput;
import com.ruoyi.integration.oatou8.config.SuccessRule;

class JsonResponseEvaluatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final JsonResponseEvaluator evaluator = new JsonResponseEvaluator();

    @Test
    void confirmsSuccessfulU8ResponseAndExtractsDeclaredOutputs() throws Exception
    {
        ResponseEvaluation evaluation = evaluator.evaluate(json.readTree("""
                {"code":0,"message":"ok","voucher":{"number":"记-0001"}}
                """), new SuccessRule("/code", Set.of("0")), 
                List.of(new ResponseOutput("voucherNo", "/voucher/number", true)));

        assertEquals("记-0001", evaluation.outputs().path("voucherNo").asText());
    }

    @Test
    void identifiesConfirmedBusinessFailureWithoutTreatingItAsTransportUnknown() throws Exception
    {
        ResponseEvaluationException exception = assertThrows(ResponseEvaluationException.class,
                () -> evaluator.evaluate(json.readTree("{\"code\":400,\"message\":\"凭证不平\"}"),
                        new SuccessRule("/code", Set.of("0")), List.of(), "/message"));

        assertEquals("U8_BUSINESS_REJECTED", exception.code());
        assertEquals("凭证不平", exception.getMessage());
    }

    @Test
    void rejectsAConfirmedResponseWhenItsRequiredOutputIsAbsent() throws Exception
    {
        ResponseEvaluationException exception = assertThrows(ResponseEvaluationException.class,
                () -> evaluator.evaluate(json.readTree("{\"code\":0}"), new SuccessRule("/code", Set.of("0")),
                        List.of(new ResponseOutput("voucherNo", "/voucherNo", true))));

        assertEquals("U8_RESPONSE_OUTPUT_MISSING", exception.code());
    }
}
