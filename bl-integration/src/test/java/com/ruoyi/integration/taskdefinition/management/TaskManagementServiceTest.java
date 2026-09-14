package com.ruoyi.integration.taskdefinition.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
