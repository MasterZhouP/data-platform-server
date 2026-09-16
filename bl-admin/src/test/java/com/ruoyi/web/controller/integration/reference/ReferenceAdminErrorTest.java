package com.ruoyi.web.controller.integration.reference;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.taskdefinition.management.TaskDraftNotValidatedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ReferenceAdminErrorTest
{
    @Test void permissionFailureIsNotReportedAsInternalError()
    {
        var result = new ReferenceApiExceptionHandler().denied(new org.springframework.security.access.AccessDeniedException("denied"),
                new MockHttpServletRequest("GET", "/integration/reference/tasks/A"));
        assertEquals(403, ((AjaxResult) result.getBody()).get("code"));
    }

    @Test void adminVersionConflictPreservesReadableMessageForExistingRuoyiInterceptor()
    {
        var request = new MockHttpServletRequest("PUT", "/integration/reference/tasks/A");
        var result = new ReferenceApiExceptionHandler().reference(
                new ReferenceException("METADATA_VERSION_MISMATCH", 409, "配置已更新，请刷新后再保存"), request);
        var body = (AjaxResult) result.getBody();
        assertEquals(200, result.getStatusCode().value());
        assertEquals(500, body.get("code"));
        assertEquals("METADATA_VERSION_MISMATCH", body.get("referenceCode"));
        assertEquals("配置已更新，请刷新后再保存", body.get("msg"));
    }

    @Test void draftStateFailureIsReportedAsAConflictInsteadOfAnInternalError()
    {
        var request = new MockHttpServletRequest("POST", "/integration/reference/tasks");
        var result = new ReferenceApiExceptionHandler().draftNotValidated(
                new TaskDraftNotValidatedException("A1_2"), request);
        var body = (AjaxResult) result.getBody();
        assertEquals("DRAFT_NOT_VALIDATED", body.get("referenceCode"));
        assertEquals(409, body.get("httpStatus"));
        assertNotNull(body.get("requestId"));
    }

    @Test void unexpectedAdminFailureIncludesTheRequestIdShownToTheUser()
    {
        var request = new MockHttpServletRequest("POST", "/integration/reference/tasks");
        var result = new ReferenceApiExceptionHandler().unexpected(new IllegalStateException("secret"), request);
        var body = (AjaxResult) result.getBody();
        assertNotNull(body.get("requestId"));
        assertTrue(body.get("msg").toString().contains(body.get("requestId").toString()));
    }
}
