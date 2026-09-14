package com.ruoyi.integration.taskdefinition.mapper;

/**
 * 可写的草稿修订行。
 * 发布后的修订不会再通过这个对象更新；它只承载当前草稿在发布前的受控编辑内容。
 */
public class TaskRevisionWriteRow
{
    private Long revisionId;
    private String taskCode;
    private Integer revisionNo;
    private String status;
    private String configJson;
    private String checksum;
    private String dependencyRevisionsJson;
    private String validationJson;
    private String changeNote;

    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long revisionId) { this.revisionId = revisionId; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getDependencyRevisionsJson() { return dependencyRevisionsJson; }
    public void setDependencyRevisionsJson(String dependencyRevisionsJson) { this.dependencyRevisionsJson = dependencyRevisionsJson; }
    public String getValidationJson() { return validationJson; }
    public void setValidationJson(String validationJson) { this.validationJson = validationJson; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
}
