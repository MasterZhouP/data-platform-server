package com.ruoyi.integration.taskdefinition.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.RevisionStatus;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskMapper;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionWriteRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class TaskManagementServiceTest
{
    @Mock
    private IntegrationTaskMapper mapper;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectsAStaleDraftSaveBeforeMutatingTheRevision()
    {
        when(mapper.findTask("OA_EXPENSE_VOUCHER")).thenReturn(task(5L, 21L));
        TaskManagementService service = service();

        TaskDraftCommand command = new TaskDraftCommand("费用报销推凭证", TaskType.OA_TO_U8, true,
                4L, config(), "调整凭证摘要");

        assertThrows(TaskVersionConflictException.class,
                () -> service.saveDraft("OA_EXPENSE_VOUCHER", command));
    }

    @Test
    void publishesOnlyTheValidatedDraftAndArchivesThePreviousRevision()
    {
        when(mapper.findTask("OA_EXPENSE_VOUCHER")).thenReturn(task(5L, 21L));
        when(mapper.findRevision(21L)).thenReturn(revision(21L, "VALIDATED"));
        when(mapper.archiveRevision(5L)).thenReturn(1);
        when(mapper.publishRevision(21L)).thenReturn(1);
        when(mapper.publishTask(eq("OA_EXPENSE_VOUCHER"), eq(21L), eq(5L))).thenReturn(1);

        IntegrationTaskDefinition published = service().publish("OA_EXPENSE_VOUCHER", 5L);

        assertEquals(21L, published.activeRevisionId());
        assertEquals(6L, published.configVersion());
    }

    @Test
    void doesNotPublishADraftThatSkippedValidation()
    {
        when(mapper.findTask("OA_EXPENSE_VOUCHER")).thenReturn(task(5L, 21L));
        when(mapper.findRevision(21L)).thenReturn(revision(21L, "DRAFT"));

        assertThrows(TaskDraftNotValidatedException.class,
                () -> service().publish("OA_EXPENSE_VOUCHER", 5L));
    }

    @Test
    void createsReferenceDraftInTheSameVersionedTaskCatalog()
    {
        when(mapper.nextRevisionNo("U8_MATERIAL_REFERENCE")).thenReturn(1);
        when(mapper.updateTaskDraft(eq("U8_MATERIAL_REFERENCE"), eq("物料参照"), eq(true), any(), eq(0L)))
                .thenReturn(1);

        IntegrationTaskDefinition created = service().create("U8_MATERIAL_REFERENCE",
                new TaskDraftCommand("物料参照", TaskType.REFERENCE_QUERY, true, null, referenceConfig(), "迁移参照配置"));

        assertEquals(TaskType.REFERENCE_QUERY, created.taskType());
        assertEquals(1L, created.configVersion());
        verify(mapper).insertGeneratedRevision(org.mockito.ArgumentMatchers.argThat((TaskRevisionWriteRow revision) ->
                revision.getConfigJson().contains("datasourceKey")
                        && revision.getDependencyRevisionsJson().contains("datasource:u8")
                        && !revision.getDependencyRevisionsJson().contains("u8Gateway")));
    }

    private TaskManagementService service()
    {
        return new TaskManagementService(mapper, json,
                new TaskConfigValidator(json, java.util.Set.of("oa"), java.util.Set.of("VOUCHER_ADD")));
    }

    private ObjectNode config()
    {
        ObjectNode root = json.createObjectNode();
        root.putObject("constants");
        root.putArray("dataSteps");
        ObjectNode u8 = root.putObject("u8");
        u8.put("operationCode", "VOUCHER_ADD");
        u8.put("path", "/api/voucher/add");
        u8.put("requestJsonTemplate", "{\"voucher\":\"{{trigger.masterId}}\"}");
        ObjectNode success = u8.putObject("successRule");
        success.put("pointer", "/code");
        success.putArray("allowedValues").add("0");
        u8.putArray("outputs");
        root.putArray("resultQueries");
        return root;
    }

    private ObjectNode referenceConfig()
    {
        ObjectNode config = json.createObjectNode();
        config.put("datasourceKey", "u8");
        config.put("sqlText", "SELECT cInvCode AS code FROM Inventory");
        config.set("metadata", referenceMetadata());
        return config;
    }

    private ObjectNode referenceMetadata()
    {
        ObjectNode metadata = json.createObjectNode();
        metadata.put("taskCode", "U8_MATERIAL_REFERENCE");
        metadata.put("taskName", "物料参照");
        metadata.put("taskType", "REFERENCE");
        metadata.put("executionMode", "SYNC_QUERY");
        metadata.put("metadataVersion", "migration-1");
        ObjectNode resultSet = metadata.putArray("resultSets").addObject();
        resultSet.put("resultSetCode", "material");
        resultSet.put("resultSetName", "物料");
        resultSet.put("selectionMode", "SINGLE");
        ObjectNode field = resultSet.putArray("fields").addObject();
        field.put("name", "code");
        field.put("label", "编码");
        field.put("order", 1);
        field.put("dataType", "STRING");
        field.put("nullable", false);
        field.putArray("filterOperators").add("eq");
        field.put("sortable", true);
        resultSet.putArray("parameters");
        ObjectNode defaults = resultSet.putObject("defaults");
        defaults.putArray("displayFields").add("code");
        defaults.putArray("filterFields").add("code");
        ObjectNode sort = defaults.putArray("sort").addObject();
        sort.put("field", "code");
        sort.put("direction", "ASC");
        defaults.put("pageSize", 20);
        ObjectNode limits = resultSet.putObject("limits");
        limits.put("maxPageSize", 200);
        limits.put("maxFilterConditions", 20);
        limits.put("maxFilterDepth", 3);
        limits.put("maxSortFields", 5);
        limits.put("maxInValues", 100);
        limits.put("queryTimeoutMs", 10000);
        return metadata;
    }

    private IntegrationTaskRow task(long version, Long draftRevisionId)
    {
        return new IntegrationTaskRow("OA_EXPENSE_VOUCHER", "费用报销推凭证", "OA_TO_U8", true,
                5L, draftRevisionId, version);
    }

    private TaskRevisionRow revision(Long revisionId, String status)
    {
        return new TaskRevisionRow(revisionId, "OA_EXPENSE_VOUCHER", 2, status, config().toString(),
                "a".repeat(64), "{\"u8Gateway\":\"shared\"}");
    }
}
