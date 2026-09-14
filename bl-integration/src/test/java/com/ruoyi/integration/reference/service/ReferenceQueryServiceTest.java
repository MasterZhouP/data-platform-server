package com.ruoyi.integration.reference.service;

import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.reference.engine.ReferenceEngine;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReferenceQueryServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutionRepository repository = mock(ExecutionRepository.class);
    private final ReferenceEngine engine = mock(ReferenceEngine.class);
    private final ReferenceQueryService service = new ReferenceQueryService(engine, repository, json);
    private final ReferenceTask task = new ReferenceTask("DEMO", "示例", true, "u8", "SELECT 'DEMO' AS code", json.createObjectNode());

    private void assignIds() {
        AtomicLong ids = new AtomicLong(100);
        doAnswer(invocation -> { ((IntegrationExecution) invocation.getArgument(0)).setExecutionId(ids.incrementAndGet()); return null; })
            .when(repository).insert(any());
    }

    @Test void invalidRequestCreatesNoExecution() {
        var request = json.createObjectNode();
        doThrow(new ReferenceException("INVALID_ARGUMENT", 400, "参数错误")).when(engine).validate(task, request);
        assertThrows(ReferenceException.class, () -> service.query(task, request, "request-id"));
        verifyNoInteractions(repository);
    }

    @Test void synchronousQueriesHaveIndependentIdsAndDoNotStoreRowsOrFilters() throws Exception {
        assignIds();
        var request = json.readTree("{\"resultSetCode\":\"tou\",\"metadataVersion\":\"1\",\"filter\":{\"sensitive\":\"secret-value\"}}");
        var response = json.createObjectNode().put("total", 1);
        response.putArray("rows").addObject().put("secret", "secret-row");
        when(engine.query(task, request)).thenReturn(response);
        var first = service.query(task, request, "same-request");
        var second = service.query(task, request, "same-request");
        assertNotEquals(first.executionId(), second.executionId());
        var inserted = ArgumentCaptor.forClass(IntegrationExecution.class);
        verify(repository, times(2)).insert(inserted.capture());
        var execution = inserted.getAllValues().get(0);
        assertEquals("RUNNING", execution.getStatus());
        assertEquals("REFERENCE_QUERY", execution.getStage());
        assertNull(execution.getMasterId());
        assertTrue(execution.getTriggerPayload().contains("same-request"));
        assertFalse(execution.getTriggerPayload().contains("secret-value"));
        assertNotEquals(execution.getDedupKey(), inserted.getAllValues().get(1).getDedupKey());
        verify(repository, times(2)).markSuccess(anyLong(), isNull(), anyString(),
                argThat(value -> !value.contains("secret-row")), org.mockito.ArgumentMatchers.eq(true), any());
        verify(repository, never()).claimPending(anyLong(), any());
    }

    @Test void timeoutIsUserRetryableButNeverPushReplayableAndCarriesExecutionId() {
        assignIds();
        var request = json.createObjectNode();
        when(engine.query(task, request)).thenThrow(new ReferenceException("QUERY_TIMEOUT", 504, "查询超时", true));
        var error = assertThrows(ReferenceException.class, () -> service.query(task, request, "request-id"));
        assertEquals("101", error.executionId());
        assertTrue(error.retryable());
        verify(repository).markFailed(eq(101L), eq("QUERY_TIMEOUT"), anyString(), eq(false), eq(false), any());
    }

    @Test void loggingDependencyFailureIs503AndDoesNotStartTheQuery() {
        assignIds();
        doThrow(new DataAccessResourceFailureException("sensitive database detail")).when(repository).insertStage(any());
        var error = assertThrows(ReferenceException.class, () -> service.query(task, json.createObjectNode(), "request-id"));
        assertEquals("SERVICE_UNAVAILABLE", error.code());
        assertEquals(503, error.httpStatus());
        assertEquals("101", error.executionId());
        assertFalse(error.getMessage().contains("sensitive"));
        verify(engine, never()).query(any(), any());
        verify(repository).markFailed(eq(101L), eq("SERVICE_UNAVAILABLE"), anyString(), eq(false), eq(false), any());
    }
}
