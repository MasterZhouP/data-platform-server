package com.ruoyi.integration.client.u8;

/** U8 HTTP 传输边界，便于把“请求是否可能已送达”与业务状态明确区分。 */
public interface U8HttpTransport
{
    U8HttpResponse exchange(U8HttpRequest request);
}
