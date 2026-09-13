package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundHeader;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundLine;

class SalesOutboundPayloadFactoryTest
{
    @Test
    @SuppressWarnings("unchecked")
    void mapsExistingU8FieldsToTheV10TestFormAndOmitsNewFields()
    {
        SalesOutboundPayloadFactory factory = new SalesOutboundPayloadFactory("XSCKDCS", "formmain_0688",
                "formson_0689");

        Map<String, Object> request = factory.build(document());

        assertEquals("collaboration", request.get("appName"));
        Map<String, Object> data = (Map<String, Object>) request.get("data");
        assertEquals("XSCKDCS", data.get("templateCode"));
        assertEquals("0", data.get("draft"));
        assertEquals("销售出库单-CK-001", data.get("subject"));

        Map<String, Object> form = (Map<String, Object>) data.get("data");
        Map<String, Object> main = (Map<String, Object>) form.get("formmain_0688");
        assertEquals("CK-001", main.get("单据编号"));
        assertEquals("张三", main.get("制单人"));
        assertEquals("2026-09-12", main.get("出库日期"));
        assertEquals("WH-01", main.get("仓库编码"));
        assertEquals("成品仓", main.get("仓库"));
        assertEquals("900000000000001", main.get("仓库管理员"));
        assertEquals("10001", main.get("主表主ID"));
        assertEquals("SO-9", main.get("业务单号"));
        assertFalse(main.containsKey("U8人员编码"));
        assertFalse(main.containsKey("单据审核人"));
        assertFalse(main.containsKey("合计数量"));
        assertFalse(main.containsKey("查询来款情况"));

        List<Map<String, Object>> detail = (List<Map<String, Object>>) form.get("formson_0689");
        assertEquals(1, detail.size());
        assertEquals("INV-1", detail.get(0).get("存货编码"));
        assertEquals(new BigDecimal("12.5000"), detail.get(0).get("数量"));
        assertEquals("2026-08-01", detail.get(0).get("生产日期"));
        assertEquals("2027-08-01", detail.get(0).get("到期日期"));
        assertFalse(detail.get(0).containsKey("序号"));
        assertFalse(detail.get(0).containsKey("质量等级"));
        assertFalse(detail.get(0).containsKey("单价"));
        assertFalse(detail.get(0).containsKey("金额"));
    }

    private SalesOutboundDocument document()
    {
        SalesOutboundHeader header = new SalesOutboundHeader("CK-001", "张三", LocalDate.of(2026, 9, 12),
                "WH-01", "成品仓", "C-01", "客户甲", "D-01", "销售部", "备注", "10001",
                "SO-9", "900000000000001", false);
        SalesOutboundLine line = new SalesOutboundLine("INV-1", "商品A", "S", new BigDecimal("12.5000"),
                "20001", "10001", "箱", "件", new BigDecimal("2.0000"), "B-1",
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        return new SalesOutboundDocument(header, List.of(line));
    }
}
