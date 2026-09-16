package com.ruoyi.integration.taskdefinition.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.config.TaskConfigValidator;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskMapper;
import com.ruoyi.integration.taskdefinition.mapper.IntegrationTaskRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionRow;
import com.ruoyi.integration.taskdefinition.mapper.TaskRevisionWriteRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCopyTest
{
    @Mock
    private IntegrationTaskMapper mapper;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void copiesPublishedU8ToOaConfigIntoDisabledTargetDraft()
    {
        when(mapper.findTask("U8_RECEIPT")).thenReturn(new IntegrationTaskRow(
                "U8_RECEIPT", "收货推OA", "U8_TO_OA", true, 7L, null, 3L));
        when(mapper.findTask("U8_RECEIPT_COPY")).thenReturn(null);
        when(mapper.findRevision(7L)).thenReturn(new TaskRevisionRow(7L, "U8_RECEIPT", 2, "PUBLISHED",
                u8ToOaConfig().toString(), "a".repeat(64), "{}"));
        when(mapper.nextRevisionNo("U8_RECEIPT_COPY")).thenReturn(1);
        when(mapper.updateTaskDraft(eq("U8_RECEIPT_COPY"), eq("收货推OA副本"), eq(false), any(), eq(0L)))
                .thenReturn(1);

        IntegrationTaskDefinition result = service().copy("U8_RECEIPT", "U8_RECEIPT_COPY", "收货推OA副本", "复制模板");

        assertEquals("U8_RECEIPT_COPY", result.taskCode());
        assertEquals(TaskType.U8_TO_OA, result.taskType());
        assertEquals(false, result.enabled());
        verify(mapper).insertGeneratedRevision(org.mockito.ArgumentMatchers.argThat((TaskRevisionWriteRow row) ->
                row.getTaskCode().equals("U8_RECEIPT_COPY") && row.getConfigJson().contains("payloadTemplate")
                        && row.getDependencyRevisionsJson().contains("oaGateway")));
    }

    private TaskManagementService service()
    {
        return new TaskManagementService(mapper, json,
                new TaskConfigValidator(json, java.util.Set.of("oa"), java.util.Set.of("VOUCHER_ADD")));
    }

    private ObjectNode u8ToOaConfig()
    {
        ObjectNode root = json.createObjectNode();
        root.putObject("constants");
        root.putArray("dataSteps");
        root.putObject("oa").put("u8IdVariable", "trigger.masterId")
                .set("payloadTemplate", json.createObjectNode().put("masterId", "{{trigger.masterId}}"));
        ObjectNode sync = root.putObject("sync");
        sync.put("datasourceKey", "u8");
        sync.put("upperBoundSql", "SELECT CURRENT_TIMESTAMP");
        sync.put("createSql", "SELECT ccode AS document_no, dnmaketime AS changed_at FROM rdrecord32 WHERE dnmaketime > :fromTime AND dnmaketime <= :toTime");
        sync.put("updateSql", "SELECT ccode AS document_no, dnmodifytime AS changed_at FROM rdrecord32 WHERE dnmodifytime > :fromTime AND dnmodifytime <= :toTime");
        sync.put("deleteSql", "SELECT djbh AS document_no, sj AS changed_at FROM rdrecord32_delete_log WHERE sj > :fromTime AND sj <= :toTime");
        sync.put("initialCursor", "2026-01-01T00:00:00");
        sync.put("overlapMinutes", 60);
        return root;
    }
}
