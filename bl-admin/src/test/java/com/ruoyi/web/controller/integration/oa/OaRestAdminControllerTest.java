package com.ruoyi.web.controller.integration.oa;

import java.lang.reflect.Method;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.client.oa.config.OaRestAdminService;
import com.ruoyi.integration.client.oa.config.OaRestReadService;
import com.ruoyi.integration.client.oa.runtime.OaRestRuntimeService;
import com.ruoyi.integration.configuration.RevisionToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaRestAdminControllerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void savesTheAccountDraftThenReturnsTheSafeReloadedView() {
        OaRestAdminService editor = mock(OaRestAdminService.class);
        OaRestReadService reads = mock(OaRestReadService.class);
        OaRestRuntimeService runtime = mock(OaRestRuntimeService.class);
        ObjectNode request = json.createObjectNode().put("connectionName", "OA 测试账户");
        ObjectNode detail = json.createObjectNode().put("connectionKey", "oa-default").put("draftRevisionId", "rev-2");
        when(editor.saveDraft(eq("oa-default"), eq(request))).thenReturn(new RevisionToken("rev-2"));
        when(reads.detail("oa-default")).thenReturn(detail);

        AjaxResult result = new OaRestAdminController(editor, reads, runtime).save("oa-default", request);

        assertEquals(detail, result.get(AjaxResult.DATA_TAG));
        verify(editor).saveDraft("oa-default", request);
    }

    @Test
    void usesGranularPermissionsAndForwardsBothRevisionTokensForActivation() throws Exception {
        assertPermission("list", "integration:oa-rest:list");
        assertPermission("detail", "integration:oa-rest:view", String.class);
        assertPermission("save", "integration:oa-rest:edit", String.class, ObjectNode.class);
        assertPermission("test", "integration:oa-rest:test", String.class, ObjectNode.class);
        assertPermission("activate", "integration:oa-rest:activate", String.class, ObjectNode.class);
        assertPermission("disable", "integration:oa-rest:activate", String.class);

        OaRestAdminService editor = mock(OaRestAdminService.class);
        OaRestReadService reads = mock(OaRestReadService.class);
        OaRestRuntimeService runtime = mock(OaRestRuntimeService.class);
        ObjectNode request = json.createObjectNode().put("expectedRevision", "draft-3").put("expectedActiveId", "active-2");
        when(runtime.activate("oa-default", "draft-3", "active-2")).thenReturn(json.createObjectNode().put("status", "SUCCESS"));

        new OaRestAdminController(editor, reads, runtime).activate("oa-default", request);

        verify(runtime).activate("oa-default", "draft-3", "active-2");
    }

    private static void assertPermission(String methodName, String permission, Class<?>... types) throws Exception {
        Method method = OaRestAdminController.class.getMethod(methodName, types);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation.value().contains(permission));
    }
}
