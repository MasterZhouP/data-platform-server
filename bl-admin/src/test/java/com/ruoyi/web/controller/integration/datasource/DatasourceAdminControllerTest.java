package com.ruoyi.web.controller.integration.datasource;

import java.lang.reflect.Method;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.configuration.RevisionToken;
import com.ruoyi.integration.datasource.admin.DatasourceAdminService;
import com.ruoyi.integration.datasource.admin.DatasourceReadService;
import com.ruoyi.integration.datasource.runtime.DatasourceRuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceAdminControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void savesTheDraftThenReturnsAReloadedSafeView() {
        DatasourceAdminService editor = mock(DatasourceAdminService.class);
        DatasourceReadService reads = mock(DatasourceReadService.class);
        DatasourceRuntimeService runtime = mock(DatasourceRuntimeService.class);
        ObjectNode request = json.createObjectNode().put("name", "U8 正式库");
        ObjectNode detail = json.createObjectNode().put("datasourceKey", "u8").put("draftRevisionId", "rev-2");
        when(editor.saveDraft(eq("u8"), eq(request))).thenReturn(new RevisionToken("rev-2"));
        when(reads.detail("u8")).thenReturn(detail);

        AjaxResult result = new DatasourceAdminController(editor, reads, runtime).save("u8", request);

        assertEquals(detail, result.get(AjaxResult.DATA_TAG));
        verify(editor).saveDraft("u8", request);
    }

    @Test
    void assignsLeastPrivilegePermissionsToEachOperation() throws Exception {
        assertPermission("list", "integration:datasource:list");
        assertPermission("detail", "integration:datasource:view", String.class);
        assertPermission("save", "integration:datasource:edit", String.class, ObjectNode.class);
        assertPermission("test", "integration:datasource:test", String.class, ObjectNode.class);
        assertPermission("activate", "integration:datasource:activate", String.class, ObjectNode.class);
        assertPermission("disable", "integration:datasource:activate", String.class);
    }

    @Test
    void forwardsTheFrontendActiveRevisionTokenForCompareAndSetActivation() {
        DatasourceAdminService editor = mock(DatasourceAdminService.class);
        DatasourceReadService reads = mock(DatasourceReadService.class);
        DatasourceRuntimeService runtime = mock(DatasourceRuntimeService.class);
        ObjectNode request = json.createObjectNode().put("expectedRevision", "draft-3").put("expectedActiveId", "active-2");
        when(runtime.activate("u8", "draft-3", "active-2")).thenReturn(json.createObjectNode().put("status", "SUCCESS"));

        new DatasourceAdminController(editor, reads, runtime).activate("u8", request);

        verify(runtime).activate("u8", "draft-3", "active-2");
    }

    private static void assertPermission(String methodName, String permission, Class<?>... types) throws Exception {
        Method method = DatasourceAdminController.class.getMethod(methodName, types);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation.value().contains(permission));
    }
}
