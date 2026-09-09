package com.ruoyi.integration.reference.service;

import java.util.Date;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.domain.IntegrationExecutionStage;
import com.ruoyi.integration.execution.repository.ExecutionRepository;
import com.ruoyi.integration.reference.engine.ReferenceEngine;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Synchronous read-only execution; never submits work to PushPipeline or its dispatcher. */
@Service
public class ReferenceQueryService {
    public record QueryResult(ObjectNode data, String executionId) { }
    private final ReferenceEngine engine;
    private final ExecutionRepository repository;
    private final ObjectMapper json;

    public ReferenceQueryService(ReferenceEngine engine, ExecutionRepository repository, ObjectMapper json) {
        this.engine = engine;
        this.repository = repository;
        this.json = json;
    }

    public QueryResult query(ReferenceTask task, JsonNode request, String requestId) {
        engine.validate(task, request);
        Date started = new Date();
        ObjectNode summary = json.createObjectNode().put("requestId", requestId).put("taskType", "REFERENCE")
            .put("resultSetCode", request.path("resultSetCode").asText())
            .put("metadataVersion", request.path("metadataVersion").asText())
            .put("pageNum", request.path("pageNum").asInt(1)).put("pageSize", request.path("pageSize").asInt(200));
        IntegrationExecution execution = new IntegrationExecution();
        execution.setTaskCode(task.taskCode());
        execution.setMasterId(context(request, "masterId"));
        execution.setFormId(context(request, "formId"));
        execution.setSummaryId(context(request, "summaryId"));
        execution.setStatus("RUNNING");
        execution.setStage("REFERENCE_QUERY");
        execution.setRetryable(false);
        execution.setResultUnknown(false);
        execution.setRetryCount(0);
        // requestId is correlation only: every explicit query is a fresh read.
        execution.setDedupKey("reference:" + UUID.randomUUID());
        execution.setTriggerPayload(summary.toString());
        execution.setStartTime(started);
        execution.setCreateTime(started);
        execution.setUpdateTime(started);
        try {
            repository.insert(execution);
        } catch (RuntimeException ex) {
            throw new ReferenceException("SERVICE_UNAVAILABLE", 503, "执行记录暂不可用，请稍后重试");
        }
        String executionId = String.valueOf(execution.getExecutionId());
        IntegrationExecutionStage stage = new IntegrationExecutionStage();
        stage.setExecutionId(execution.getExecutionId());
        stage.setSequenceNo(1);
        stage.setStage("REFERENCE_QUERY");
        stage.setStageStatus("STARTED");
        stage.setRequestPayload(summary.toString());
        stage.setStartTime(started);
        stage.setCreateTime(started);
        stage.setUpdateTime(started);
        try {
            repository.insertStage(stage);
            ObjectNode data = engine.query(task, request);
            Date ended = new Date();
            ObjectNode result = json.createObjectNode().put("total", data.path("total").asLong())
                .put("returnedRows", data.path("rows").size()).put("durationMs", ended.getTime() - started.getTime());
            stage.setStageStatus("SUCCESS");
            stage.setResponsePayload(result.toString());
            stage.setEndTime(ended);
            stage.setUpdateTime(ended);
            repository.updateStage(stage);
            repository.markSuccess(execution.getExecutionId(), null, summary.toString(), result.toString(), ended);
            return new QueryResult(data, executionId);
        } catch (RuntimeException failure) {
            ReferenceException error = failure instanceof ReferenceException reference ? reference
                : failure instanceof DataAccessException
                    ? new ReferenceException("SERVICE_UNAVAILABLE", 503, "执行记录暂不可用，请稍后重试")
                    : new ReferenceException("INTERNAL_ERROR", 500, "参照查询失败，请联系管理员", false);
            Date ended = new Date();
            stage.setStageStatus("FAILED");
            stage.setErrorCode(error.code());
            stage.setErrorMessage(error.getMessage());
            stage.setEndTime(ended);
            stage.setUpdateTime(ended);
            // Reference reads must never become eligible for PushPipeline's manual replay.
            try {
                repository.markFailed(execution.getExecutionId(), error.code(), error.getMessage(), false, false, ended);
                if (stage.getStageLogId() != null) repository.updateStage(stage);
            } catch (RuntimeException loggingFailure) {
                throw new ReferenceException("SERVICE_UNAVAILABLE", 503, "执行记录更新失败，请联系管理员")
                    .withExecutionId(executionId);
            }
            throw error.withExecutionId(executionId);
        }
    }

    private String context(JsonNode request, String key) {
        JsonNode value = request.path("context").path(key);
        return value.isTextual() ? value.textValue() : null;
    }
}
