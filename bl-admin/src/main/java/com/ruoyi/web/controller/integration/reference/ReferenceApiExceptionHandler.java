package com.ruoyi.web.controller.integration.reference;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.web.controller.integration.openapi.IntegrationOpenApiController;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = { ReferenceOpenApiController.class, ReferenceAdminController.class,
        IntegrationOpenApiController.class })
public class ReferenceApiExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(ReferenceApiExceptionHandler.class);
    @ExceptionHandler(ReferenceException.class)
    public ResponseEntity<?> reference(ReferenceException exception, HttpServletRequest request)
    {
        return respond(request, exception.httpStatus(), exception.code(), exception.getMessage(),
                exception.retryable(), exception.executionId());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalid(HttpMessageNotReadableException exception, HttpServletRequest request)
    {
        return respond(request, 400, "INVALID_ARGUMENT", "请求 JSON 或字段类型不合法", false, null);
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> contentType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request)
    {
        return respond(request, 415, "UNSUPPORTED_MEDIA_TYPE", "请使用 application/json", false, null);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> argument(IllegalArgumentException exception, HttpServletRequest request)
    {
        return respond(request, 400, "INVALID_ARGUMENT", exception.getMessage(), false, null);
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> unavailable(DataAccessException exception, HttpServletRequest request)
    {
        log.warn("参照平台依赖不可用 requestId={} type={}", ReferenceApiResponses.requestId(request), exception.getClass().getSimpleName());
        return respond(request, 503, "SERVICE_UNAVAILABLE", "平台配置或日志数据库暂不可用，请检查数据库迁移与连接", true, null);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied(AccessDeniedException exception, HttpServletRequest request)
    {
        if (!request.getRequestURI().substring(request.getContextPath().length()).startsWith("/integration/openapi/v1/"))
            return ResponseEntity.ok(AjaxResult.error(403, "没有参照操作权限"));
        return respond(request, 403, "FORBIDDEN", "没有参照操作权限", false, null);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception exception, HttpServletRequest request)
    {
        // Full SQL/bind values can occur in JDBC exception text. Do not expose or log that payload here.
        log.error("参照请求异常 requestId={} type={}", ReferenceApiResponses.requestId(request), exception.getClass().getSimpleName());
        return respond(request, 500, "INTERNAL_ERROR", "参照服务异常，请根据请求标识联系管理员", false, null);
    }
    private ResponseEntity<?> respond(HttpServletRequest request, int status, String code,
            String message, boolean retryable, String executionId)
    {
        if (!request.getRequestURI().substring(request.getContextPath().length()).startsWith("/integration/openapi/v1/"))
            return ResponseEntity.ok(AjaxResult.error(message).put("referenceCode", code).put("httpStatus", status));
        return ResponseEntity.status(status).body(ReferenceApiResponses.error(
                ReferenceApiResponses.requestId(request), executionId, code, message, retryable));
    }
}
