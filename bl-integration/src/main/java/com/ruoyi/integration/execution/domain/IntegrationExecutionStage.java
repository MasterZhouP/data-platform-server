package com.ruoyi.integration.execution.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

public class IntegrationExecutionStage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long stageLogId;
    private Long executionId;
    private Integer sequenceNo;
    private String stage;
    private String stageStatus;
    private String requestPayload;
    private String responsePayload;
    private String errorCode;
    private String errorMessage;
    private Date startTime;
    private Date endTime;

    public IntegrationExecutionStage copy()
    {
        IntegrationExecutionStage value = new IntegrationExecutionStage();
        value.stageLogId = stageLogId;
        value.executionId = executionId;
        value.sequenceNo = sequenceNo;
        value.stage = stage;
        value.stageStatus = stageStatus;
        value.requestPayload = requestPayload;
        value.responsePayload = responsePayload;
        value.errorCode = errorCode;
        value.errorMessage = errorMessage;
        value.startTime = startTime == null ? null : new Date(startTime.getTime());
        value.endTime = endTime == null ? null : new Date(endTime.getTime());
        value.setCreateTime(getCreateTime());
        value.setUpdateTime(getUpdateTime());
        return value;
    }

    public Long getStageLogId() { return stageLogId; }
    public void setStageLogId(Long stageLogId) { this.stageLogId = stageLogId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public Integer getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Integer sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getStageStatus() { return stageStatus; }
    public void setStageStatus(String stageStatus) { this.stageStatus = stageStatus; }
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
}
