package com.ruoyi.integration.taskdefinition;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/** Immutable configuration content and dependency revisions captured when the revision is published. */
public record TaskRevision(Long revisionId, String taskCode, int revisionNo, RevisionStatus status,
        JsonNode config, String checksum, Map<String, String> dependencyRevisions)
{
}
