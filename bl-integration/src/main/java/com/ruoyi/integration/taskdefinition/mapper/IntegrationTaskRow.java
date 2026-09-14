package com.ruoyi.integration.taskdefinition.mapper;

/** Database representation of a stable task identity. */
public record IntegrationTaskRow(String taskCode, String taskName, String taskType, boolean enabled,
        Long activeRevisionId, Long draftRevisionId, Long configVersion)
{
}
