package com.ruoyi.integration.client.oa;

import java.util.Map;

public record OaHttpRequest(String method, String path, Map<String, String> headers, String body)
{
    public OaHttpRequest
    {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
