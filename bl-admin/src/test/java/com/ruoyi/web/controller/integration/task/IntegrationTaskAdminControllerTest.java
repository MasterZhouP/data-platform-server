package com.ruoyi.web.controller.integration.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.oatou8.OaToU8Preview;
import com.ruoyi.integration.oatou8.OaToU8PreviewInput;
import com.ruoyi.integration.oatou8.OaToU8PreviewService;
import com.ruoyi.integration.taskdefinition.IntegrationTaskDefinition;
import com.ruoyi.integration.taskdefinition.TaskType;
import com.ruoyi.integration.taskdefinition.management.TaskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationTaskAdminControllerTest
{
    private final TaskManagementService tasks = mock(TaskManagementService.class);
    private final OaToU8PreviewService preview = mock(OaToU8PreviewService.class);
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        mvc = MockMvcBuilders.standaloneSetup(new IntegrationTaskAdminController(tasks, preview)).build();
    }

    @Test
    void createsAnOaToU8DraftWithItsConfigOutsideTheOaOpenApi() throws Exception
    {
        when(tasks.create(eq("OA_EXPENSE_VOUCHER"), any())).thenReturn(definition(1L, 11L));

        mvc.perform(post("/integration/tasks").contentType(MediaType.APPLICATION_JSON).content(createJson()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskCode").value("OA_EXPENSE_VOUCHER"))
                .andExpect(jsonPath("$.data.draftRevisionId").value(11));

        verify(tasks).create(eq("OA_EXPENSE_VOUCHER"), any());
    }

    @Test
    void previewsOnlyTheSavedDraftWithFixedOaTriggerContext() throws Exception
    {
        ObjectNode config = json.createObjectNode();
        when(tasks.draftConfig("OA_EXPENSE_VOUCHER", 1L)).thenReturn(config);
        ObjectNode request = json.createObjectNode().put("voucher", "V-100");
        when(preview.preview(eq(config), any())).thenReturn(new OaToU8Preview(java.util.Map.of(), request));

        mvc.perform(post("/integration/tasks/OA_EXPENSE_VOUCHER/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configVersion\":1,\"masterId\":\"M-1\",\"formId\":\"F-1\",\"summaryId\":\"S-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.request.voucher").value("V-100"));

        verify(preview).preview(eq(config), eq(new OaToU8PreviewInput("M-1", "F-1", "S-1")));
    }

    private IntegrationTaskDefinition definition(Long version, Long draftRevision)
    {
        return new IntegrationTaskDefinition("OA_EXPENSE_VOUCHER", "费用报销推凭证", TaskType.OA_TO_U8, false,
                null, draftRevision, version);
    }

    private String createJson()
    {
        return """
                {"taskCode":"OA_EXPENSE_VOUCHER","taskName":"费用报销推凭证","taskType":"OA_TO_U8","enabled":false,
                  "config":{"constants":{},"dataSteps":[],"u8":{"operationCode":"VOUCHER_ADD","path":"/api/voucher/add","requestJsonTemplate":"{\\\"voucher\\\":\\\"{{trigger.masterId}}\\\"}","successRule":{"pointer":"/code","allowedValues":["0"]},"outputs":[]},"resultQueries":[]}}
                """;
    }
}
