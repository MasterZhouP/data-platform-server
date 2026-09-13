package com.ruoyi.integration.client.oa;

import java.util.Map;

public interface OaProcessOperations
{
    OaProcessRef start(Map<String, Object> payload);

    void cancel(OaProcessRef process);
}
