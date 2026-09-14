package com.ruoyi.integration.client.u8;

/** U8 HTTP 层已收到的响应；具体业务是否成功仍由任务版本的成功规则判断。 */
public record U8HttpResponse(int statusCode, String body)
{
}
