package com.ruoyi.integration.sync.salesoutbound;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundHeader;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SalesOutboundPayloadFactory
{
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String templateCode;
    private final String mainTable;
    private final String detailTable;

    @Autowired
    public SalesOutboundPayloadFactory(
            @Value("${integration.sync.sales-outbound.template-code:XSCKDCS}") String templateCode,
            @Value("${integration.sync.sales-outbound.main-table:formmain_0688}") String mainTable,
            @Value("${integration.sync.sales-outbound.detail-table:formson_0689}") String detailTable)
    {
        this.templateCode = templateCode;
        this.mainTable = mainTable;
        this.detailTable = detailTable;
    }

    public Map<String, Object> build(SalesOutboundDocument document)
    {
        SalesOutboundHeader header = document.header();
        Map<String, Object> form = new LinkedHashMap<>();
        form.put(mainTable, mainValues(header));
        form.put(detailTable, detailValues(document.lines()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateCode", templateCode);
        data.put("draft", "0");
        data.put("subject", "销售出库单-" + header.documentNo());
        data.put("data", form);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appName", "collaboration");
        request.put("data", data);
        return request;
    }

    private Map<String, Object> mainValues(SalesOutboundHeader value)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "单据编号", value.documentNo());
        put(result, "制单人", value.maker());
        put(result, "出库日期", date(value.outboundDate()));
        put(result, "仓库", value.warehouseName());
        put(result, "客户名称", value.customerName());
        put(result, "客户编码", value.customerCode());
        put(result, "部门名称", value.departmentName());
        put(result, "仓库管理员", value.warehouseManagerIds());
        put(result, "仓库编码", value.warehouseCode());
        put(result, "部门编码", value.departmentCode());
        put(result, "备注", value.remark());
        put(result, "主表主ID", value.u8Id());
        put(result, "业务单号", value.businessNo());
        return result;
    }

    private List<Map<String, Object>> detailValues(List<SalesOutboundLine> values)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SalesOutboundLine value : values)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            put(row, "存货编码", value.inventoryCode());
            put(row, "存货名称", value.inventoryName());
            put(row, "规格型号", value.specification());
            put(row, "数量", value.quantity());
            put(row, "子ID", value.childId());
            put(row, "主ID", value.parentId());
            put(row, "主单位", value.mainUnit());
            put(row, "辅单位", value.auxiliaryUnit());
            put(row, "件数", value.pieceCount());
            put(row, "批号", value.batchNo());
            put(row, "生产日期", date(value.productionDate()));
            put(row, "到期日期", date(value.expiryDate()));
            result.add(row);
        }
        return result;
    }

    private void put(Map<String, Object> target, String key, Object value)
    {
        if (value != null)
        {
            target.put(key, value);
        }
    }

    private String date(LocalDate value)
    {
        return value == null ? null : DATE.format(value);
    }
}
