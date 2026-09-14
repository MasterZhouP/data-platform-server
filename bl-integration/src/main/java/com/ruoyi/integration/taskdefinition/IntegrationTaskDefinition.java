package com.ruoyi.integration.taskdefinition;

/** Stable task identity and the revision pointers used by the control plane. */
public record IntegrationTaskDefinition(String taskCode, String taskName, TaskType taskType,
        boolean enabled, Long activeRevisionId, Long draftRevisionId, Long configVersion)
{
}
