package com.ruoyi.integration.taskdefinition;

/** Persistence boundary for task identity and its immutable revisions. */
public interface TaskDefinitionRepository
{
    IntegrationTaskDefinition findTask(String taskCode);

    TaskRevision findRevision(Long revisionId);
}
