package com.ruoyi.integration.sync.salesoutbound.link;

import java.util.Date;

public class OaProcessLink
{
    private Long linkId;
    private String taskCode;
    private String businessKey;
    private String u8Id;
    private String summaryId;
    private String affairId;
    private String processId;
    private ProcessLinkState state;
    private Integer versionNo;
    private Long previousLinkId;
    private Date createTime;
    private Date updateTime;

    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getU8Id() { return u8Id; }
    public void setU8Id(String u8Id) { this.u8Id = u8Id; }
    public String getSummaryId() { return summaryId; }
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }
    public String getAffairId() { return affairId; }
    public void setAffairId(String affairId) { this.affairId = affairId; }
    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }
    public ProcessLinkState getState() { return state; }
    public void setState(ProcessLinkState state) { this.state = state; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Long getPreviousLinkId() { return previousLinkId; }
    public void setPreviousLinkId(Long previousLinkId) { this.previousLinkId = previousLinkId; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
