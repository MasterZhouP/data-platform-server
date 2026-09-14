package com.ruoyi.integration.taskdefinition.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IntegrationTaskMapper
{
    IntegrationTaskRow findTask(@Param("taskCode") String taskCode);

    TaskRevisionRow findRevision(@Param("revisionId") Long revisionId);

    int insertTask(IntegrationTaskRow row);

    int insertRevision(TaskRevisionRow row);

    Integer nextRevisionNo(@Param("taskCode") String taskCode);

    int insertGeneratedRevision(TaskRevisionWriteRow row);

    int replaceDraftRevision(TaskRevisionWriteRow row);

    int markRevisionValidated(@Param("revisionId") Long revisionId, @Param("validationJson") String validationJson);

    int archiveRevision(@Param("revisionId") Long revisionId);

    int publishRevision(@Param("revisionId") Long revisionId);

    int updateTaskDraft(@Param("taskCode") String taskCode, @Param("taskName") String taskName,
            @Param("enabled") boolean enabled, @Param("draftRevisionId") Long draftRevisionId,
            @Param("expectedVersion") Long expectedVersion);

    int advanceConfigVersion(@Param("taskCode") String taskCode, @Param("expectedVersion") Long expectedVersion);

    int publishTask(@Param("taskCode") String taskCode, @Param("activeRevisionId") Long activeRevisionId,
            @Param("expectedVersion") Long expectedVersion);

    java.util.List<IntegrationTaskRow> listTasks();

    java.util.List<TaskRevisionRow> listRevisions(@Param("taskCode") String taskCode);
}
