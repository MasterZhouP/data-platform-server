package com.ruoyi.web.controller.integration.reference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

public final class ReferenceApiResponses
{
    public static final String REQUEST_ID = ReferenceApiResponses.class.getName() + ".requestId";
    private ReferenceApiResponses() { }

    public static Map<String, Object> success(String requestId, String executionId, Object data)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", "OK"); result.put("message", "成功"); result.put("requestId", requestId);
        result.put("executionId", executionId); result.put("data", data);
        return result;
    }

    public static Map<String, Object> error(String requestId, String executionId, String code,
            String message, boolean retryable)
    {
        Map<String, Object> result = success(requestId, executionId, null);
        result.put("code", code); result.put("message", message);
        result.put("error", Map.of("retryable", retryable, "details", List.of()));
        return result;
    }

    public static String requestId(HttpServletRequest request)
    {
        return (String) request.getAttribute(REQUEST_ID);
    }
}
