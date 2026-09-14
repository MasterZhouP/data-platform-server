package com.ruoyi.integration.taskdefinition.mapper;

/** Database representation of immutable revision content and non-secret dependency versions. */
public record TaskRevisionRow(Long revisionId, String taskCode, int revisionNo, String status,
        String configJson, String checksum, String dependencyRevisionsJson)
{
}
