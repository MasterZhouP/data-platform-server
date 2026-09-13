package com.ruoyi.integration.sync.salesoutbound.model;

import java.util.List;

public record SalesOutboundDocument(SalesOutboundHeader header, List<SalesOutboundLine> lines)
{
    public SalesOutboundDocument
    {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
