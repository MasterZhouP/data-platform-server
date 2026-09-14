package com.ruoyi.integration.reference.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.RevisionStatus;
import com.ruoyi.integration.taskdefinition.TaskRevision;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.management.TaskDraftCommand;
import com.ruoyi.integration.taskdefinition.management.TaskManagementService;
import org.junit.jupiter.api.Test;

class ReferenceCatalogTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final TaskManagementService tasks = mock(TaskManagementService.class);
    private final ReferenceCatalog catalog = new ReferenceCatalog(tasks, new ReferenceTaskConfigValidator(), json);

    @Test
    void readsThePublishedReferenceRevisionFromTheCommonTaskCatalog() throws Exception
    {
        ReferenceTask task = task();
        when(tasks.list()).thenReturn(List.of(definition(18L, null, 4L)));
        when(tasks.revision("DEMO", 18L)).thenReturn(revision(18L, task));

        ReferenceTask result = catalog.list().get(0);

        assertEquals("DEMO", result.taskCode());
        assertEquals("SELECT 'DEMO' AS code", result.sqlText());
    }

    @Test
    void createsAndPublishesAReferenceRevisionBeforeMakingItRunnable() throws Exception
    {
        ReferenceTask input = task();
        when(tasks.create(eq("DEMO"), any(TaskDraftCommand.class))).thenReturn(definition(null, 21L, 1L));
        when(tasks.validateDraft("DEMO", 1L)).thenReturn(definition(null, 21L, 2L));
        when(tasks.publish("DEMO", 2L)).thenReturn(definition(21L, null, 3L));
        when(tasks.detail("DEMO")).thenReturn(definition(21L, null, 3L));
        when(tasks.revision("DEMO", 21L)).thenReturn(revision(21L, taskWithMetadataVersion(input, "published")));

        ReferenceTask created = catalog.create(input);

        assertNotEquals("old", created.metadata().path("metadataVersion").asText());
        verify(tasks).validateDraft("DEMO", 1L);
        verify(tasks).publish("DEMO", 2L);
    }

    @Test
    void rejectsAPersistedTaskCodeWithDifferentCase() throws Exception
    {
        when(tasks.detail("demo")).thenReturn(new IntegrationTaskDefinition("DEMO", "示例", TaskType.REFERENCE_QUERY,
                true, 18L, null, 4L));

        ReferenceException error = assertThrows(ReferenceException.class, () -> catalog.get("demo"));

        assertEquals("TASK_NOT_FOUND", error.code());
    }

    private IntegrationTaskDefinition definition(Long activeRevisionId, Long draftRevisionId, Long version)
    {
        return new IntegrationTaskDefinition("DEMO", "示例", TaskType.REFERENCE_QUERY, true,
                activeRevisionId, draftRevisionId, version);
    }

    private TaskRevision revision(Long revisionId, ReferenceTask task)
    {
        return new TaskRevision(revisionId, task.taskCode(), 1, RevisionStatus.PUBLISHED,
                config(task), "a".repeat(64), Map.of("datasource:u8", "registered-readonly"));
    }

    private ObjectNode config(ReferenceTask task)
    {
        ObjectNode result = json.createObjectNode();
        result.put("datasourceKey", task.datasourceKey());
        result.put("sqlText", task.sqlText());
        result.set("metadata", task.metadata());
        return result;
    }

    private ReferenceTask taskWithMetadataVersion(ReferenceTask source, String version)
    {
        ObjectNode metadata = source.metadata().deepCopy();
        metadata.put("metadataVersion", version);
        return new ReferenceTask(source.taskCode(), source.taskName(), source.enabled(), source.datasourceKey(),
                source.sqlText(), metadata);
    }

    private ReferenceTask task() throws Exception
    {
        ObjectNode metadata = (ObjectNode) json.readTree("""
            {"taskCode":"DEMO","taskName":"示例","taskType":"REFERENCE","executionMode":"SYNC_QUERY",
             "metadataVersion":"old","resultSets":[{"resultSetCode":"tou","resultSetName":"示例","selectionMode":"SINGLE",
             "fields":[{"name":"code","label":"编码","order":1,"dataType":"STRING","nullable":true,"filterOperators":["eq"],"sortable":true}],
             "parameters":[],"defaults":{"displayFields":["code"],"filterFields":["code"],"sort":[{"field":"code","direction":"ASC"}],"pageSize":20},
             "limits":{"maxPageSize":200,"maxFilterConditions":20,"maxFilterDepth":3,"maxSortFields":5,"maxInValues":100,"queryTimeoutMs":10000}}]}
            """);
        return new ReferenceTask("DEMO", "示例", true, "u8", "SELECT 'DEMO' AS code", metadata);
    }
}
