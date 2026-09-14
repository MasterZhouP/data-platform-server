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
}
