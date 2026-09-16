package com.ruoyi.web.controller.integration.u8;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.client.u8.config.U8GatewayAdminService;
import com.ruoyi.integration.client.u8.config.U8GatewayReadService;
import com.ruoyi.integration.client.u8.runtime.U8GatewayRuntimeService;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class U8GatewayAdminControllerTest
{
    private final U8GatewayAdminService editor = mock(U8GatewayAdminService.class);
    private final U8GatewayReadService reads = mock(U8GatewayReadService.class);
    private final U8GatewayRuntimeService runtime = mock(U8GatewayRuntimeService.class);
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp()
    {
        mvc = MockMvcBuilders.standaloneSetup(new U8GatewayAdminController(editor, reads, runtime)).build();
    }

    @Test
    void savesASecretFreeDraftThroughTheDedicatedEndpoint() throws Exception
    {
        ObjectNode detail = json.createObjectNode().put("connectionKey", "u8-default").put("secretConfigured", true);
        when(reads.detail("u8-default")).thenReturn(detail);
        when(editor.saveDraft(eq("u8-default"), any(ObjectNode.class))).thenReturn(new RevisionToken("rev-1"));

        mvc.perform(put("/integration/u8-account/u8-default/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"connectionName\":\"U8\",\"secretParametersUpdate\":{\"account\":\"secret\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.connectionKey").value("u8-default"));

        verify(editor).saveDraft(eq("u8-default"), any(ObjectNode.class));
    }

    @Test
    void exposesOnlyTheCurrentOperationOptionsToTaskEditors() throws Exception
    {
        ObjectNode options = json.createObjectNode().put("connectionKey", "u8-default").put("configured", true);
        options.putArray("operations").addObject().put("code", "VOUCHER_ADD").put("path", "/api/voucher/add").put("enabled", true);
        when(reads.options()).thenReturn(options);

        mvc.perform(get("/integration/u8-account/options"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.operations[0].code").value("VOUCHER_ADD"));
    }
}
