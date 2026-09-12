package com.ruoyi.web.controller.integration.reference;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.reference.catalog.ReferenceCatalog;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import com.ruoyi.integration.reference.service.ReferenceQueryService;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiKeyFilter;
import com.ruoyi.web.controller.integration.reference.security.ReferenceApiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReferenceOpenApiControllerTest
{
    private final ReferenceCatalog catalog = mock(ReferenceCatalog.class);
    private final ReferenceQueryService queries = mock(ReferenceQueryService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;
    private static final String BASE = "/integration/openapi/v1";
    private static final String ID = "83c87e30-8aba-4a82-b5d4-9233f52ead48";
    // 鉴权暂时禁用，恢复时重新启用测试专用 Key。
    // private static final String KEY = "test-key-012345678901234567890123456789";

    @BeforeEach void setup()
    {
        var props = new ReferenceApiProperties();
        // 鉴权暂时禁用：恢复时重新配置服务身份及 taskCode 授权列表。
        // var c = new ReferenceApiProperties.Client();
        // c.setKey(KEY); c.setClientId("oa-test"); c.setEnabled(true); c.setTaskCodes(List.of("A", "B"));
        // props.setClients(List.of(c));
        mvc = MockMvcBuilders.standaloneSetup(new ReferenceOpenApiController(catalog, queries, mapper))
                .setControllerAdvice(new ReferenceApiExceptionHandler())
                .addFilters(new ReferenceApiKeyFilter(props, mapper)).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void catalogIncludesAllEnabledTasksAndSortsBeforePagingWithoutKey() throws Exception
    {
        when(catalog.list()).thenReturn(List.of(task("B", true), task("SECRET", true), task("A", true), task("OFF", false)));
        mvc.perform(get(BASE + "/reference/tasks").param("pageSize", "1").header("X-Request-Id", ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].taskCode").value("A"))
                .andExpect(jsonPath("$.data.items[0].sqlResource").doesNotExist());
    }
    @Test void unknownQueryParameterCannotBeSilentlyIgnored() throws Exception
    {
        mvc.perform(get(BASE + "/reference/tasks").param("whereString", "where 1=1")
                .header("X-Request-Id", ID))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        verifyNoInteractions(catalog);
    }
    @Test void unknownTaskCodeIsStillRejectedByCatalog() throws Exception
    {
        when(catalog.get("UNKNOWN")).thenThrow(new ReferenceException("TASK_NOT_FOUND", 404, "参照任务不存在", false));
        mvc.perform(get(BASE + "/reference/tasks/UNKNOWN/metadata").header("X-Request-Id", ID))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        verify(catalog).get("UNKNOWN");
    }
    @Test void disabledTaskCodeIsStillRejected() throws Exception
    {
        when(catalog.get("OFF")).thenReturn(task("OFF", false));
        mvc.perform(get(BASE + "/reference/tasks/OFF/metadata").header("X-Request-Id", ID))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_DISABLED"));
        verify(catalog).get("OFF");
    }
    @Test void statusReportsTemporaryAuthenticationBypassWithoutCallingDatabase() throws Exception
    {
        mvc.perform(get(BASE + "/status").header("X-Request-Id", ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.clientId").value("AUTHENTICATION_DISABLED"))
                .andExpect(jsonPath("$.data.apiVersion").value("1.0.0"));
        verifyNoInteractions(catalog, queries);
    }
    @Test void malformedJsonUsesContractErrorEnvelope() throws Exception
    {
        mvc.perform(post(BASE + "/reference/tasks/A/query").contentType("application/json").content("{")
                .header("X-Request-Id", ID))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.requestId").value(ID));
    }
    private ReferenceTask task(String code, boolean enabled)
    {
        return new ReferenceTask(code, code + "参照", enabled, "u8", "integration/reference/material.sql",
                mapper.createObjectNode().put("metadataVersion", "1"));
    }
}
