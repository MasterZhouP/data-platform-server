package com.ruoyi.integration.client.oa;

import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

public class OaProcessClient implements OaProcessOperations
{
    private final OaHttpTransport transport;
    private final OaTokenProvider tokens;
    private final OaRestSettings settings;

    public OaProcessClient(OaHttpTransport transport, OaTokenProvider tokens, OaRestSettings settings)
    {
        this.transport = transport;
        this.tokens = tokens;
        this.settings = settings;
    }

    @Override
    public OaProcessRef start(Map<String, Object> payload)
    {
        OaHttpResponse response = authorizedPost("/seeyon/rest/bpm/process/start", JSON.toJSONString(payload),
                "OA_START_TIMEOUT");
        ensureHttpSuccess(response, "OA_START_FAILED", "OA_START_RESULT_UNKNOWN");
        JSONObject root = parseObject(response.body(), "OA发起流程返回不是有效JSON", true);
        int code = responseCode(root);
        if (code != 0)
        {
            throw OaClientException.nonRetryable("OA_START_REJECTED",
                    "OA拒绝发起流程: " + safeMessage(root));
        }
        Object rawData = root.get("data");
        if (!(rawData instanceof JSONObject data))
        {
            throw invalidStartResponse();
        }
        JSONObject business = businessData(data.get("app_bussiness_data"));
        String summaryId = business == null ? null : scalarText(business.get("summaryId"));
        String affairId = business == null ? null : scalarText(business.get("affairId"));
        String processId = scalarText(data.get("processId"));
        if (blank(summaryId) || blank(affairId) || blank(processId))
        {
            throw invalidStartResponse();
        }
        return new OaProcessRef(summaryId, affairId, processId);
    }

    private int responseCode(JSONObject root)
    {
        if (!root.containsKey("code"))
        {
            throw invalidStartResponse();
        }
        Object value = root.get("code");
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        if (value instanceof String text && text.matches("-?\\d+"))
        {
            try
            {
                return Integer.parseInt(text);
            }
            catch (NumberFormatException ex)
            {
                throw invalidStartResponse();
            }
        }
        throw invalidStartResponse();
    }

    @Override
    public void cancel(OaProcessRef process)
    {
        String body = JSON.toJSONString(Map.of(
                "summaryId", process.summaryId(),
                "affairId", process.affairId(),
                "member", settings.loginName()));
        OaHttpResponse response = authorizedPost("/seeyon/rest/affair/cancel", body, "OA_CANCEL_TIMEOUT");
        ensureHttpSuccess(response, "OA_CANCEL_FAILED", "OA_CANCEL_RESULT_UNKNOWN");
        CancelConfirmation confirmation = cancelConfirmation(response.body());
        if (confirmation == CancelConfirmation.UNKNOWN)
        {
            throw OaClientException.resultUnknown("OA_CANCEL_RESULT_UNKNOWN",
                    "OA撤销返回无法确认处理结果");
        }
        if (confirmation == CancelConfirmation.REJECTED)
        {
            throw OaClientException.nonRetryable("OA_CANCEL_REJECTED", "OA未确认流程撤销成功");
        }
    }

    private OaHttpResponse authorizedPost(String path, String body, String timeoutCode)
    {
        String token = tokens.getToken();
        OaHttpResponse response = send(path, body, token, timeoutCode);
        if (response.statusCode() == 401)
        {
            tokens.invalidate(token);
            String refreshed = tokens.getToken();
            response = send(path, body, refreshed, timeoutCode);
        }
        return response;
    }

    private OaHttpResponse send(String path, String body, String token, String timeoutCode)
    {
        try
        {
            return transport.exchange(new OaHttpRequest("POST", path, Map.of("token", token), body));
        }
        catch (OaTransportException ex)
        {
            if (ex.isResultUnknown())
            {
                throw OaClientException.resultUnknown(timeoutCode, ex.getMessage());
            }
            throw OaClientException.retryable("OA_UNAVAILABLE", ex.getMessage());
        }
    }

    private void ensureHttpSuccess(OaHttpResponse response, String errorCode, String resultUnknownCode)
    {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            if (response.statusCode() >= 500 || response.statusCode() == 408)
            {
                throw OaClientException.resultUnknown(resultUnknownCode,
                        "OA请求返回不确定状态，HTTP " + response.statusCode());
            }
            if (response.statusCode() == 401 || response.statusCode() == 429)
            {
                throw OaClientException.retryable(errorCode, "OA请求失败，HTTP " + response.statusCode());
            }
            throw OaClientException.nonRetryable(errorCode, "OA请求失败，HTTP " + response.statusCode());
        }
    }

    private JSONObject businessData(Object value)
    {
        if (value instanceof JSONObject object)
        {
            return object;
        }
        if (value instanceof String text && !text.isBlank())
        {
            return parseObject(text, "OA流程业务标识不是有效JSON", true);
        }
        return null;
    }

    private CancelConfirmation cancelConfirmation(String body)
    {
        if (body == null)
        {
            return CancelConfirmation.UNKNOWN;
        }
        if ("true".equalsIgnoreCase(body.trim()))
        {
            return CancelConfirmation.CONFIRMED;
        }
        if ("false".equalsIgnoreCase(body.trim()))
        {
            return CancelConfirmation.REJECTED;
        }
        try
        {
            JSONObject json = JSON.parseObject(body);
            if (json.containsKey("success"))
            {
                return Boolean.TRUE.equals(json.getBoolean("success"))
                        ? CancelConfirmation.CONFIRMED : CancelConfirmation.REJECTED;
            }
            if (json.containsKey("code"))
            {
                return json.getIntValue("code") == 0
                        ? CancelConfirmation.CONFIRMED : CancelConfirmation.REJECTED;
            }
            return CancelConfirmation.UNKNOWN;
        }
        catch (RuntimeException ex)
        {
            return CancelConfirmation.UNKNOWN;
        }
    }

    private JSONObject parseObject(String body, String message, boolean resultUnknown)
    {
        try
        {
            JSONObject value = JSON.parseObject(body);
            if (value == null)
            {
                throw new IllegalArgumentException("empty JSON");
            }
            return value;
        }
        catch (RuntimeException ex)
        {
            if (resultUnknown)
            {
                throw OaClientException.resultUnknown("OA_INVALID_RESPONSE", message);
            }
            throw OaClientException.nonRetryable("OA_INVALID_RESPONSE", message);
        }
    }

    private OaClientException invalidStartResponse()
    {
        return OaClientException.resultUnknown("OA_START_INVALID_RESPONSE",
                "OA返回成功但缺少summaryId、affairId或processId");
    }

    private String safeMessage(JSONObject value)
    {
        String message = scalarText(value.get("message"));
        return message == null || message.isBlank() ? "code=" + value.get("code") : message;
    }

    private String scalarText(Object value)
    {
        return value instanceof String || value instanceof Number ? String.valueOf(value) : null;
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private enum CancelConfirmation
    {
        CONFIRMED,
        REJECTED,
        UNKNOWN
    }
}
