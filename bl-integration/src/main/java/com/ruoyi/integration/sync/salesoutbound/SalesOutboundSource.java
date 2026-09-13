package com.ruoyi.integration.sync.salesoutbound;

import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;

public interface SalesOutboundSource
{
    SalesOutboundDocument load(String documentNo);
}
