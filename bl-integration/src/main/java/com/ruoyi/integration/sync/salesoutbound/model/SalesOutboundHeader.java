package com.ruoyi.integration.sync.salesoutbound.model;

import java.time.LocalDate;

public record SalesOutboundHeader(String documentNo, String maker, LocalDate outboundDate,
        String warehouseCode, String warehouseName, String customerCode, String customerName,
        String departmentCode, String departmentName, String remark, String u8Id,
        String businessNo, String warehouseManagerIds, boolean verified)
{
}
