package com.ruoyi.integration.sync.salesoutbound.model;

import java.util.Map;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLink;

public record SalesOutboundPreview(String taskCode, SalesOutboundDocument document,
        Map<String, Object> oaPayload, OaProcessLink currentProcess)
{
}
