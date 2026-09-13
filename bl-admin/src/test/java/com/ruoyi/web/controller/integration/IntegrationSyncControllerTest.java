package com.ruoyi.web.controller.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.sync.ManualSyncService;
import com.ruoyi.integration.sync.salesoutbound.DuplicateOaProcessException;

class IntegrationSyncControllerTest
{
    private final ManualSyncService service = mock(ManualSyncService.class);
    private final IntegrationSyncController controller = new IntegrationSyncController(service);

    @Test
    void exposesPreviewPushAndForceRepushWithSeparatePermissions() throws Exception
    {
        assertPermission("preview", "integration:sync:preview");
        assertPermission("push", "integration:sync:push");
        assertPermission("forceRepush", "integration:sync:force");
    }

    @Test
    void pushReturnsAcceptedExecutionAndDuplicateConflict()
    {
        AcceptanceResult accepted = new AcceptanceResult(88L, "PENDING");
        when(service.push("TASK", "CK-001", false)).thenReturn(accepted);
        assertEquals(accepted, controller.push("TASK", "CK-001").get(AjaxResult.DATA_TAG));

        when(service.push("TASK", "CK-002", false)).thenThrow(new DuplicateOaProcessException("CK-002"));
        AjaxResult conflict = controller.push("TASK", "CK-002");
        assertEquals(HttpStatus.CONFLICT, conflict.get(AjaxResult.CODE_TAG));
    }

    private void assertPermission(String methodName, String permission) throws Exception
    {
        Method method = IntegrationSyncController.class.getMethod(methodName, String.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation.value().contains(permission));
    }
}
