package com.ruoyi.integration.oatou8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.client.u8.U8CallResult;
import com.ruoyi.integration.client.u8.U8CallStatus;
import com.ruoyi.integration.client.u8.U8Gateway;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.execution.service.ResolvedExecution;
import com.ruoyi.integration.oatou8.config.OaToU8TaskConfig;
import com.ruoyi.integration.oatou8.config.ReadQueryStep;
import com.ruoyi.integration.oatou8.config.ResponseOutput;
import com.ruoyi.integration.oatou8.config.ResultCardinality;
import com.ruoyi.integration.oatou8.config.ResultQueryStep;
import com.ruoyi.integration.oatou8.config.SuccessRule;
import com.ruoyi.integration.oatou8.config.U8BusinessRequest;
import com.ruoyi.integration.oatou8.template.JsonResponseEvaluator;
import com.ruoyi.integration.oatou8.template.JsonTemplateRenderer;
import com.ruoyi.integration.sql.ReadOnlySqlExecutor;
import com.ruoyi.integration.sql.ReadOnlySqlResult;
import com.ruoyi.integration.sql.SqlVariableResolver;
import com.ruoyi.integration.task.ExecutionStageRecorder;
import com.ruoyi.integration.task.PushResult;
import com.ruoyi.integration.taskdefinition.PublishedTaskRevision;
import com.ruoyi.integration.taskdefinition.TaskType;

class OaToU8TaskExecutorTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void executesTheConfiguredDataQueryThenPostsOneRenderedU8Document() throws Exception
    {
        U8Gateway gateway = mock(U8Gateway.class);
        org.mockito.Mockito.when(gateway.postBusiness(eq("VOUCHER_ADD"), eq("/api/voucher/add"), any()))
                .thenReturn(new U8CallResult(U8CallStatus.SUCCESS, "{\"code\":0,\"voucherNo\":\"记-001\"}", null, null));
        ExecutionRepository repository = mock(ExecutionRepository.class);
        ReadOnlySqlExecutor sql = (source, statement, cardinality, parameters) ->
                queryResult("{\"documentNo\":\"BX-1\",\"amount\":18}");

        OaToU8TaskExecutor executor = executor(config(List.of(dataStep()), List.of()), sql, gateway, repository);
        IntegrationExecution execution = execution("FULL", null);

        PushResult result = executor.execute(resolved(execution), new NoopRecorder());

        assertEquals("M-100", result.businessKey());
        assertTrue(result.requestPayload().contains("\"amount\":18"));
        verify(repository).checkpointU8Confirmed(eq(1L), eq("{\"voucherNo\":\"记-001\"}"), eq("U8_CONFIRMED"));
        verify(gateway).postBusiness(eq("VOUCHER_ADD"), eq("/api/voucher/add"), any());
    }

    @Test
    void postProcessResumeUsesSavedOutputAndNeverPostsU8Again() throws Exception
    {
        U8Gateway gateway = mock(U8Gateway.class);
        ExecutionRepository repository = mock(ExecutionRepository.class);
        ReadOnlySqlExecutor sql = (source, statement, cardinality, parameters) ->
                queryResult("{\"voucherNo\":\"记-001\"}");
        ResultQueryStep resultStep = new ResultQueryStep("voucher", 1, "u8", "select voucher", ResultCardinality.ONE,
                Map.of("voucherNo", "result.voucherNo"), true, 0, 100, 1, Map.of("voucherNo", "/voucherNo"));
        OaToU8TaskExecutor executor = executor(config(List.of(), List.of(resultStep)), sql, gateway, repository);
        IntegrationExecution execution = execution("POST_PROCESS", "{\"voucherNo\":\"记-001\"}");
        execution.setU8Confirmed(true);
        execution.setLastCompletedStage("U8_CONFIRMED");

        PushResult result = executor.execute(resolved(execution), new NoopRecorder());

        assertTrue(result.responsePayload().contains("记-001"));
        verify(gateway, never()).postBusiness(any(), any(), any());
        verify(repository, never()).checkpointU8Confirmed(any(), any(), any());
    }

    private OaToU8TaskExecutor executor(OaToU8TaskConfig config, ReadOnlySqlExecutor sql,
            U8Gateway gateway, ExecutionRepository repository)
    {
        return new OaToU8TaskExecutor(raw -> config, sql, new SqlVariableResolver(),
                new JsonTemplateRenderer(json), gateway, new JsonResponseEvaluator(), repository, json);
    }

    private OaToU8TaskConfig config(List<ReadQueryStep> dataSteps, List<ResultQueryStep> resultQueries)
    {
        return new OaToU8TaskConfig(Map.of(), dataSteps,
                new U8BusinessRequest("VOUCHER_ADD", "/api/voucher/add",
                        "{\"documentNo\":\"{{data.header.documentNo}}\",\"amount\":\"{{data.header.amount}}\"}",
                        new SuccessRule("/code", Set.of("0")), null,
                        List.of(new ResponseOutput("voucherNo", "/voucherNo", true))),
                resultQueries);
    }

    private ReadQueryStep dataStep()
    {
        return new ReadQueryStep("header", 1, "oa", "select header", ResultCardinality.ONE, Map.of());
    }

    private ReadOnlySqlResult queryResult(String value)
    {
        try
        {
            return new ReadOnlySqlResult(ResultCardinality.ONE, json.readTree(value));
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("测试数据必须是合法JSON", ex);
        }
    }

    private IntegrationExecution execution(String resumeMode, String resultOutputs)
    {
        IntegrationExecution value = new IntegrationExecution();
        value.setExecutionId(1L);
        value.setTaskCode("OA_EXPENSE_VOUCHER");
        value.setMasterId("M-100");
        value.setResumeMode(resumeMode);
        value.setResultOutputsJson(resultOutputs);
        return value;
    }

    private ResolvedExecution resolved(IntegrationExecution execution)
    {
        return new ResolvedExecution(execution, new PublishedTaskRevision("OA_EXPENSE_VOUCHER", 10L, "checksum",
                TaskType.OA_TO_U8, json.createObjectNode(), Map.of()));
    }

    private static final class NoopRecorder implements ExecutionStageRecorder
    {
        @Override public void started(String stage, String requestPayload) { }
        @Override public void succeeded(String stage, String responsePayload) { }
        @Override public void failed(String stage, String errorCode, String errorMessage) { }
    }
}
