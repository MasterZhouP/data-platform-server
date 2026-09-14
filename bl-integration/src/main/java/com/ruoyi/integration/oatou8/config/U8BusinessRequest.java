package com.ruoyi.integration.oatou8.config;

import java.util.List;

/** Task-owned business interface details; connection, account and authentication stay in the U8 gateway. */
public record U8BusinessRequest(String operationCode, String path, String requestJsonTemplate,
        SuccessRule successRule, String errorMessagePointer, List<ResponseOutput> outputs)
{
}
