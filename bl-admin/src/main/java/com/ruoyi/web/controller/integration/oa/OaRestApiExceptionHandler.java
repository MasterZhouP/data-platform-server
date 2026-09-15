package com.ruoyi.web.controller.integration.oa;

import java.util.UUID;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.integration.configuration.ConfigurationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts configuration failures into stable, non-sensitive inline errors for the OA console. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OaRestAdminController.class)
public class OaRestApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(OaRestApiExceptionHandler.class);

    @ExceptionHandler(ConfigurationException.class)
    public ResponseEntity<AjaxResult> configuration(ConfigurationException error, HttpServletRequest request) {
        return respond(request, error.httpStatus(), error.code(), error.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AjaxResult> invalid(HttpServletRequest request) {
        return respond(request, 400, "INVALID_ARGUMENT", "请求 JSON 或字段类型不合法");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AjaxResult> unavailable(DataAccessException error, HttpServletRequest request) {
        log.warn("OA REST配置存储不可用 requestId={} type={}", requestId(request), error.getClass().getSimpleName());
        return respond(request, 503, "CONFIGURATION_STORE_UNAVAILABLE", "配置存储暂不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AjaxResult> unexpected(Exception error, HttpServletRequest request) {
        log.error("OA REST管理请求异常 requestId={} type={}", requestId(request), error.getClass().getSimpleName());
        return respond(request, 500, "CONFIGURATION_OPERATION_FAILED", "OA REST账户操作失败，请根据请求标识联系管理员");
    }

    private static ResponseEntity<AjaxResult> respond(HttpServletRequest request, int status, String code, String message) {
        return ResponseEntity.ok(AjaxResult.error(message)
                .put("integrationCode", code)
                .put("httpStatus", status)
                .put("requestId", requestId(request)));
    }

    private static String requestId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Request-Id");
        return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
    }
}
