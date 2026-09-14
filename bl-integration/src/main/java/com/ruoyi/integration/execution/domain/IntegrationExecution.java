package com.ruoyi.integration.execution.domain;

import java.util.Date;
import java.util.List;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 一次集成请求的可审计执行记录。
 * <p>
 * 对配置型任务，该记录同时保存受理时锁定的修订、U8 确认输出和续跑位置，保证发布新配置不会影响在途单据。
 * </p>
 */
public class IntegrationExecution extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long executionId;
    private String taskCode;
    private Long taskRevisionId;
    private String taskChecksum;
    private String dependencySnapshot;
    private String masterId;
    private String businessKey;
    private String formId;
    private String summaryId;
    private String operation;
    private String triggerSource;
    private Boolean force;
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
    private String lastCompletedStage;
    private Boolean u8Confirmed;
    private String resumeMode;
    private String resultOutputsJson;
    private List<IntegrationExecutionStage> stages;

    public IntegrationExecution copy()
    {
        IntegrationExecution value = new IntegrationExecution();
        value.executionId = executionId;
        value.taskCode = taskCode;
        value.taskRevisionId = taskRevisionId;
        value.taskChecksum = taskChecksum;
        value.dependencySnapshot = dependencySnapshot;
        value.masterId = masterId;
        value.businessKey = businessKey;
        value.formId = formId;
        value.summaryId = summaryId;
        value.operation = operation;
        value.triggerSource = triggerSource;
        value.force = force;
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
        value.lastCompletedStage = lastCompletedStage;
        value.u8Confirmed = u8Confirmed;
        value.resumeMode = resumeMode;
        value.resultOutputsJson = resultOutputsJson;
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
    public Long getTaskRevisionId() { return taskRevisionId; }
    public void setTaskRevisionId(Long taskRevisionId) { this.taskRevisionId = taskRevisionId; }
    public String getTaskChecksum() { return taskChecksum; }
    public void setTaskChecksum(String taskChecksum) { this.taskChecksum = taskChecksum; }
    public String getDependencySnapshot() { return dependencySnapshot; }
    public void setDependencySnapshot(String dependencySnapshot) { this.dependencySnapshot = dependencySnapshot; }
    public String getMasterId() { return masterId; }
    public void setMasterId(String masterId) { this.masterId = masterId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getFormId() { return formId; }
    public void setFormId(String formId) { this.formId = formId; }
    public String getSummaryId() { return summaryId; }
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public Boolean getForce() { return force; }
    public void setForce(Boolean force) { this.force = force; }
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
    public String getLastCompletedStage() { return lastCompletedStage; }
    public void setLastCompletedStage(String lastCompletedStage) { this.lastCompletedStage = lastCompletedStage; }
    public Boolean getU8Confirmed() { return u8Confirmed; }
    public void setU8Confirmed(Boolean u8Confirmed) { this.u8Confirmed = u8Confirmed; }
    public String getResumeMode() { return resumeMode; }
    public void setResumeMode(String resumeMode) { this.resumeMode = resumeMode; }
    public String getResultOutputsJson() { return resultOutputsJson; }
    public void setResultOutputsJson(String resultOutputsJson) { this.resultOutputsJson = resultOutputsJson; }
    public List<IntegrationExecutionStage> getStages() { return stages; }
    public void setStages(List<IntegrationExecutionStage> stages) { this.stages = stages; }
}
