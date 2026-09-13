package com.ruoyi.integration.client.oa;

import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class OaTokenProvider
{
    private final OaHttpTransport transport;
    private final OaRestProperties properties;
    private volatile String cachedToken;

    public OaTokenProvider(OaHttpTransport transport, OaRestProperties properties)
    {
        this.transport = transport;
        this.properties = properties;
    }

    public String getToken()
    {
        String token = cachedToken;
        if (token != null)
        {
            return token;
        }
        synchronized (this)
        {
            if (cachedToken == null)
            {
                cachedToken = authenticate();
            }
            return cachedToken;
        }
    }

    public synchronized void invalidate(String token)
    {
        if (token != null && token.equals(cachedToken))
        {
            cachedToken = null;
        }
    }

    private String authenticate()
    {
        requireCredentials();
        String body = JSON.toJSONString(Map.of(
                "userName", properties.getUsername(),
                "password", properties.getPassword(),
                "loginName", properties.getLoginName()));
        OaHttpResponse response;
        try
        {
            response = transport.exchange(new OaHttpRequest("POST", "/seeyon/rest/token", Map.of(), body));
        }
        catch (OaTransportException ex)
        {
            throw OaClientException.retryable("OA_AUTH_UNAVAILABLE", ex.getMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw OaClientException.retryable("OA_AUTH_FAILED", "OA认证失败，HTTP " + response.statusCode());
        }
        JSONObject json = parse(response.body(), "OA认证返回不是有效JSON");
        Object rawToken = json.get("id");
        if (!(rawToken instanceof String))
        {
            throw OaClientException.retryable("OA_AUTH_INVALID_RESPONSE", "OA认证返回缺少有效Token字段");
        }
        String token = (String) rawToken;
        if (token == null || token.isBlank() || "-1".equals(token))
        {
            throw OaClientException.nonRetryable("OA_AUTH_REJECTED", "OA认证未返回有效Token");
        }
        return token;
    }

    private void requireCredentials()
    {
        if (blank(properties.getUsername()) || blank(properties.getPassword()) || blank(properties.getLoginName()))
        {
            throw OaClientException.nonRetryable("OA_AUTH_NOT_CONFIGURED", "OA REST账号配置不完整");
        }
    }

    private JSONObject parse(String value, String message)
    {
        try
        {
            JSONObject parsed = JSON.parseObject(value);
            if (parsed == null)
            {
                throw new IllegalArgumentException("empty JSON");
            }
            return parsed;
        }
        catch (RuntimeException ex)
        {
            throw OaClientException.retryable("OA_AUTH_INVALID_RESPONSE", message);
        }
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
