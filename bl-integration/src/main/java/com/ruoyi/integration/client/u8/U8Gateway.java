package com.ruoyi.integration.client.u8;

import com.fasterxml.jackson.databind.JsonNode;

/** 统一封装 U8 公共账户、认证和交易标识，任务执行器只可提交已渲染的业务请求。 */
public interface U8Gateway
{
    U8CallResult postBusiness(String operationCode, String relativePath, JsonNode payload);

    U8ConnectionHealth health();
}
