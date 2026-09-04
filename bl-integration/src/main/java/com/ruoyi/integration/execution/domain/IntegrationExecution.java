package com.ruoyi.integration.execution.domain;

import java.util.Date;
import java.util.List;
import com.ruoyi.common.core.domain.BaseEntity;

public class IntegrationExecution extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long executionId;
    private String taskCode;
    private String masterId;
    private String businessKey;
    private String formId;
    private String summaryId;
    private String status;
    private String stage;
    private Boolean retryable;
    private Boolean resultUnknown;
    private Integer retryCount;
    private Long retryOfExecutionId;
    private String dedupKey;
    private String triggerPayload;
    private String requestPayload;
    private String responsePayload;
    private String errorCode;
    private String errorMessage;
    private Date startTime;
    private Date endTime;
    private String retryBlockReason;
    private List<IntegrationExecutionStage> stages;

    public IntegrationExecution copy()
    {
        IntegrationExecution value = new IntegrationExecution();
        value.executionId = executionId;
        value.taskCode = taskCode;
        value.masterId = masterId;
        value.businessKey = businessKey;
        value.formId = formId;
        value.summaryId = summaryId;
        value.status = status;
        value.stage = stage;
        value.retryable = retryable;
        value.resultUnknown = resultUnknown;
        value.retryCount = retryCount;
        value.retryOfExecutionId = retryOfExecutionId;
        value.dedupKey = dedupKey;
        value.triggerPayload = triggerPayload;
        value.requestPayload = requestPayload;
        value.responsePayload = responsePayload;
        value.errorCode = errorCode;
        value.errorMessage = errorMessage;
        value.startTime = startTime == null ? null : new Date(startTime.getTime());
        value.endTime = endTime == null ? null : new Date(endTime.getTime());
        value.retryBlockReason = retryBlockReason;
        value.stages = stages;
        value.setCreateTime(getCreateTime());
        value.setUpdateTime(getUpdateTime());
        value.setParams(getParams());
        return value;
    }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public String getMasterId() { return masterId; }
    public void setMasterId(String masterId) { this.masterId = masterId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getFormId() { return formId; }
    public void setFormId(String formId) { this.formId = formId; }
    public String getSummaryId() { return summaryId; }
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }
    public Boolean getResultUnknown() { return resultUnknown; }
    public void setResultUnknown(Boolean resultUnknown) { this.resultUnknown = resultUnknown; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Long getRetryOfExecutionId() { return retryOfExecutionId; }
    public void setRetryOfExecutionId(Long retryOfExecutionId) { this.retryOfExecutionId = retryOfExecutionId; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public String getTriggerPayload() { return triggerPayload; }
    public void setTriggerPayload(String triggerPayload) { this.triggerPayload = triggerPayload; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public String getRetryBlockReason() { return retryBlockReason; }
    public void setRetryBlockReason(String retryBlockReason) { this.retryBlockReason = retryBlockReason; }
    public List<IntegrationExecutionStage> getStages() { return stages; }
    public void setStages(List<IntegrationExecutionStage> stages) { this.stages = stages; }
}
