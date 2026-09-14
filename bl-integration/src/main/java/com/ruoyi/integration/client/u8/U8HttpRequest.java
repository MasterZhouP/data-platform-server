package com.ruoyi.integration.client.u8;

import java.util.Map;

/** 网关内部 HTTP 请求对象，账户参数只在内存中传递，绝不能写入任务定义或执行日志。 */
public record U8HttpRequest(String method, String path, Map<String, String> queryParameters,
        Map<String, String> headers, String body)
{
    public U8HttpRequest
    {
        queryParameters = queryParameters == null ? Map.of() : Map.copyOf(queryParameters);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
