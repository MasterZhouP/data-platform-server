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
import com.ruoyi.integration.u8tooa.U8ToOaPreview;
import com.ruoyi.integration.u8tooa.U8ToOaPreviewService;
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
    private final U8ToOaPreviewService u8ToOaPreview = mock(U8ToOaPreviewService.class);
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        mvc = MockMvcBuilders.standaloneSetup(new IntegrationTaskAdminController(tasks, preview, u8ToOaPreview)).build();
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
        when(tasks.detail("OA_EXPENSE_VOUCHER")).thenReturn(definition(1L, 11L));
        when(tasks.draftConfig("OA_EXPENSE_VOUCHER", 1L)).thenReturn(config);
        ObjectNode request = json.createObjectNode().put("voucher", "V-100");
        when(preview.preview(eq(config), any())).thenReturn(new OaToU8Preview(java.util.Map.of(), request));

        mvc.perform(post("/integration/tasks/OA_EXPENSE_VOUCHER/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configVersion\":1,\"masterId\":\"M-1\",\"formId\":\"F-1\",\"summaryId\":\"S-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.request.voucher").value("V-100"));

        verify(preview).preview(eq(config), eq(new OaToU8PreviewInput("M-1", "F-1", "S-1")));
    }

    @Test
    void copiesAConfiguredTaskIntoANewDisabledDraft() throws Exception
    {
        when(tasks.copy(eq("U8_RECEIPT"), eq("U8_RECEIPT_COPY"), eq("收货推OA副本"), eq("复制模板")))
                .thenReturn(new IntegrationTaskDefinition("U8_RECEIPT_COPY", "收货推OA副本", TaskType.U8_TO_OA,
                        false, null, 21L, 1L));

        mvc.perform(post("/integration/tasks/U8_RECEIPT/copy").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskCode\":\"U8_RECEIPT_COPY\",\"taskName\":\"收货推OA副本\",\"changeNote\":\"复制模板\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskCode").value("U8_RECEIPT_COPY"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(tasks).copy(eq("U8_RECEIPT"), eq("U8_RECEIPT_COPY"), eq("收货推OA副本"), eq("复制模板"));
    }

    @Test
    void previewsAConfiguredU8ToOaDraftWithoutCallingOa() throws Exception
    {
        ObjectNode config = json.createObjectNode();
        when(tasks.detail("U8_RECEIPT")).thenReturn(new IntegrationTaskDefinition("U8_RECEIPT", "收货推OA",
                TaskType.U8_TO_OA, false, null, 11L, 1L));
        when(tasks.draftConfig("U8_RECEIPT", 1L)).thenReturn(config);
        when(u8ToOaPreview.preview(eq(config), any())).thenReturn(new U8ToOaPreview(
                java.util.Map.of(), json.createObjectNode().put("documentNo", "DOC-1")));

        mvc.perform(post("/integration/tasks/U8_RECEIPT/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configVersion\":1,\"masterId\":\"DOC-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.request.documentNo").value("DOC-1"));

        verify(u8ToOaPreview).preview(eq(config), eq(new OaToU8PreviewInput("DOC-1", null, null)));
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
