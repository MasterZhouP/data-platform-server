package com.ruoyi.web.controller.integration.openapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.ExecutionAcceptor;
import com.ruoyi.integration.task.TaskAction;
import com.ruoyi.integration.task.TriggerCommand;
import com.ruoyi.integration.task.TriggerSource;
import com.ruoyi.web.controller.integration.reference.ReferenceApiExceptionHandler;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiKeyFilter;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiProperties;

class IntegrationOpenApiControllerTest
{
    private final ExecutionAcceptor acceptor = mock(ExecutionAcceptor.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        ObjectMapper json = new ObjectMapper();
        mvc = MockMvcBuilders.standaloneSetup(new IntegrationOpenApiController(acceptor))
                .setControllerAdvice(new ReferenceApiExceptionHandler())
                .addFilters(new ReferenceApiKeyFilter(new ReferenceApiProperties(), json)).build();
    }

    @Test
    void oaEndpointAcceptsTheFixedContextAndReturnsPendingExecution() throws Exception
    {
        when(acceptor.accept(any())).thenReturn(new AcceptanceResult(81L, "PENDING"));
        String requestId = UUID.randomUUID().toString();

        mvc.perform(post("/integration/openapi/v1/executions").header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskCode\":\"OA_EXPENSE_VOUCHER\",\"masterId\":\"M-10\",\"formId\":\"F-1\",\"summaryId\":\"S-1\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.data.executionId").value(81)).andExpect(jsonPath("$.data.status").value("PENDING"));

        ArgumentCaptor<TriggerCommand> command = ArgumentCaptor.forClass(TriggerCommand.class);
        verify(acceptor).accept(command.capture());
        assertEquals(TaskAction.CREATE, command.getValue().action());
        assertEquals(TriggerSource.OA_API, command.getValue().triggerSource());
        assertFalse(command.getValue().force());
    }

    @Test
    void oaEndpointRejectsUnexpectedFieldsInsteadOfAcceptingActionOverrides() throws Exception
    {
        mvc.perform(post("/integration/openapi/v1/executions").header("X-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskCode\":\"OA_EXPENSE_VOUCHER\",\"masterId\":\"M-10\",\"force\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
