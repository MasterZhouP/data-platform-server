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
import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.execution.service.AcceptanceResult;
import com.ruoyi.integration.execution.service.IntegrationExecutionService;
import com.ruoyi.integration.execution.service.RetryRejectedException;

class IntegrationExecutionControllerTest
{
    private final IntegrationExecutionService service = mock(IntegrationExecutionService.class);
    private final IntegrationExecutionController controller = new IntegrationExecutionController(service);

    @Test
    void detailReturnsCompleteExecutionObject()
    {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setExecutionId(7L);
        when(service.findDetail(7L)).thenReturn(execution);

        AjaxResult result = controller.getInfo(7L);

        assertEquals(HttpStatus.SUCCESS, result.get(AjaxResult.CODE_TAG));
        assertEquals(execution, result.get(AjaxResult.DATA_TAG));
    }

    @Test
    void retryReturnsNewExecutionIdAndReadableConflict()
    {
        when(service.retry(7L)).thenReturn(new AcceptanceResult(8L, "PENDING"));
        assertEquals(8L, ((AcceptanceResult) controller.retry(7L).get(AjaxResult.DATA_TAG)).executionId());

        when(service.retry(9L)).thenThrow(new RetryRejectedException("外部结果未知"));
        AjaxResult rejected = controller.retry(9L);
        assertEquals(HttpStatus.CONFLICT, rejected.get(AjaxResult.CODE_TAG));
        assertEquals("外部结果未知", rejected.get(AjaxResult.MSG_TAG));
    }

    @Test
    void endpointsCarryTheThreeStageOnePermissions() throws Exception
    {
        assertPermission("list", new Class<?>[] { IntegrationExecution.class }, "integration:execution:list");
        assertPermission("getInfo", new Class<?>[] { Long.class }, "integration:execution:query");
        assertPermission("retry", new Class<?>[] { Long.class }, "integration:execution:retry");
    }

    private void assertPermission(String methodName, Class<?>[] parameterTypes, String permission) throws Exception
    {
        Method method = IntegrationExecutionController.class.getMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation.value().contains(permission));
    }
}
