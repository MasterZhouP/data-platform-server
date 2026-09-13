package com.ruoyi.integration.sync.salesoutbound;

import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundLine;
import org.springframework.stereotype.Component;

@Component
public class SalesOutboundValidator
{
    public void validate(SalesOutboundDocument document)
    {
        if (document == null || document.header() == null)
        {
            throw new SourceValidationException("U8销售出库单不存在");
        }
        if (document.header().verified())
        {
            throw new SourceValidationException("U8销售出库单已审核，不允许推送");
        }
        if (blank(document.header().documentNo()) || blank(document.header().u8Id()))
        {
            throw new SourceValidationException("U8销售出库单缺少单据编号或主表ID");
        }
        if (blank(document.header().warehouseManagerIds()))
        {
            throw new SourceValidationException("OA仓库管理员映射为空，请检查仓库管理员配置");
        }
        if (document.lines().isEmpty())
        {
            throw new SourceValidationException("U8销售出库单没有明细");
        }
        for (SalesOutboundLine line : document.lines())
        {
            if (blank(line.childId()) || blank(line.parentId())
                    || !document.header().u8Id().equals(line.parentId()))
            {
                throw new SourceValidationException("U8销售出库单主子表ID不一致");
            }
        }
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }
}
