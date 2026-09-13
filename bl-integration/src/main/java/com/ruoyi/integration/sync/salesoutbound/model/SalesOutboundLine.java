package com.ruoyi.integration.sync.salesoutbound.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesOutboundLine(String inventoryCode, String inventoryName, String specification,
        BigDecimal quantity, String childId, String parentId, String mainUnit, String auxiliaryUnit,
        BigDecimal pieceCount, String batchNo, LocalDate productionDate, LocalDate expiryDate)
{
}
