package com.ruoyi.integration.taskdefinition;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.integration.task.TriggerCommand;

/** The immutable snapshot assigned to one accepted execution. */
public record PublishedTaskRevision(String taskCode, Long revisionId, String checksum,
        TaskType taskType, JsonNode config, Map<String, String> dependencyRevisions)
{
    public String dedupKey(TriggerCommand command)
    {
        return taskCode + ":" + command.masterId() + ":" + command.action();
    }
}
