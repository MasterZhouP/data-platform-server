package com.ruoyi.integration.sync.salesoutbound;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundHeader;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundLine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = { "integration.datasource.u8.enabled", "integration.datasource.oa.enabled" },
        havingValue = "true")
public class JdbcSalesOutboundSource implements SalesOutboundSource
{
    private static final String HEADER_SQL = """
            SELECT a.ccode AS document_no, a.cMaker AS maker, a.dDate AS outbound_date,
                   a.cWhCode AS warehouse_code, w.cWhName AS warehouse_name,
                   a.cCusCode AS customer_code, c.cCusName AS customer_name,
                   a.cDepCode AS department_code, d.cDepName AS department_name,
                   a.cMemo AS remark, CAST(a.id AS VARCHAR(100)) AS u8_id,
                   a.cBusCode AS business_no,
                   CASE WHEN a.dVeriDate IS NULL THEN 0 ELSE 1 END AS verified
            FROM rdrecord32 a
            LEFT JOIN Warehouse w ON a.cWhCode = w.cWhCode
            LEFT JOIN Customer c ON a.cCusCode = c.cCusCode
            LEFT JOIN Department d ON a.cDepCode = d.cDepCode
            WHERE a.ccode = :documentNo
            """;

    private static final String DETAIL_SQL = """
            SELECT a.cInvCode AS inventory_code, b.cInvName AS inventory_name,
                   b.cInvStd AS specification, a.iQuantity AS quantity,
                   CAST(a.autoid AS VARCHAR(100)) AS child_id,
                   CAST(a.id AS VARCHAR(100)) AS parent_id,
                   mainUnit.cComUnitName AS main_unit,
                   auxUnit.cComUnitName AS auxiliary_unit,
                   a.iNum AS piece_count, a.cBatch AS batch_no,
                   a.dMadeDate AS production_date, a.dVDate AS expiry_date
            FROM rdrecords32 a
            LEFT JOIN Inventory b ON a.cInvCode = b.cInvCode
            LEFT JOIN ComputationUnit mainUnit ON b.cComUnitCode = mainUnit.cComUnitCode
            LEFT JOIN ComputationUnit auxUnit ON a.cAssUnit = auxUnit.cComUnitCode
            INNER JOIN rdrecord32 h ON a.id = h.id
            WHERE h.ccode = :documentNo
            ORDER BY a.autoid
            """;

    private static final String WAREHOUSE_MANAGER_SQL = """
            SELECT field0029 AS warehouse_manager_ids
            FROM ckgly
            WHERE field0031 = :warehouseCode
            """;

    private final NamedParameterJdbcTemplate u8;
    private final NamedParameterJdbcTemplate oa;

    public JdbcSalesOutboundSource(@Qualifier("u8JdbcTemplate") NamedParameterJdbcTemplate u8,
            @Qualifier("oaJdbcTemplate") NamedParameterJdbcTemplate oa)
    {
        this.u8 = u8;
        this.oa = oa;
    }

    @Override
    public SalesOutboundDocument load(String documentNo)
    {
        Map<String, ?> params = Map.of("documentNo", documentNo);
        List<SalesOutboundHeader> headers = u8.query(HEADER_SQL, params, this::mapHeader);
        if (headers.isEmpty())
        {
            return null;
        }
        SalesOutboundHeader header = withWarehouseManagers(headers.get(0));
        List<SalesOutboundLine> lines = u8.query(DETAIL_SQL, params, this::mapLine);
        return new SalesOutboundDocument(header, lines);
    }

    private SalesOutboundHeader withWarehouseManagers(SalesOutboundHeader value)
    {
        List<String> ids = oa.query(WAREHOUSE_MANAGER_SQL, Map.of("warehouseCode", value.warehouseCode()),
                (rs, rowNum) -> rs.getString("warehouse_manager_ids"));
        String managerIds = ids.isEmpty() ? null : ids.get(0);
        return new SalesOutboundHeader(value.documentNo(), value.maker(), value.outboundDate(),
                value.warehouseCode(), value.warehouseName(), value.customerCode(), value.customerName(),
                value.departmentCode(), value.departmentName(), value.remark(), value.u8Id(), value.businessNo(),
                managerIds, value.verified());
    }

    private SalesOutboundHeader mapHeader(ResultSet rs, int rowNum) throws SQLException
    {
        return new SalesOutboundHeader(rs.getString("document_no"), rs.getString("maker"),
                localDate(rs, "outbound_date"), rs.getString("warehouse_code"),
                rs.getString("warehouse_name"), rs.getString("customer_code"),
                rs.getString("customer_name"), rs.getString("department_code"),
                rs.getString("department_name"), rs.getString("remark"), rs.getString("u8_id"),
                rs.getString("business_no"), null, rs.getBoolean("verified"));
    }

    private SalesOutboundLine mapLine(ResultSet rs, int rowNum) throws SQLException
    {
        return new SalesOutboundLine(rs.getString("inventory_code"), rs.getString("inventory_name"),
                rs.getString("specification"), rs.getBigDecimal("quantity"), rs.getString("child_id"),
                rs.getString("parent_id"), rs.getString("main_unit"), rs.getString("auxiliary_unit"),
                rs.getBigDecimal("piece_count"), rs.getString("batch_no"), localDate(rs, "production_date"),
                localDate(rs, "expiry_date"));
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException
    {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
