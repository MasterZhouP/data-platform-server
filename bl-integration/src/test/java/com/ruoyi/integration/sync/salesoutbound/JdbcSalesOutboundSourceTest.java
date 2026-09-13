package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcSalesOutboundSourceTest
{
    private NamedParameterJdbcTemplate u8;
    private NamedParameterJdbcTemplate oa;

    @BeforeEach
    void setUp()
    {
        u8 = database("u8");
        oa = database("oa");
        createSchema();
    }

    @Test
    void loadsHeaderDetailsAndWarehouseManagerOaIdsWithoutWritingU8()
    {
        seedDocument();
        JdbcSalesOutboundSource source = new JdbcSalesOutboundSource(u8, oa);

        var document = source.load("CK-001");

        assertEquals("10001", document.header().u8Id());
        assertEquals("成品仓", document.header().warehouseName());
        assertEquals("900000000000001", document.header().warehouseManagerIds());
        assertFalse(document.header().verified());
        assertEquals(1, document.lines().size());
        assertEquals("20001", document.lines().get(0).childId());
        assertEquals("箱", document.lines().get(0).mainUnit());
    }

    @Test
    void returnsNullWhenDocumentNumberDoesNotExist()
    {
        JdbcSalesOutboundSource source = new JdbcSalesOutboundSource(u8, oa);
        assertEquals(null, source.load("missing"));
    }

    private NamedParameterJdbcTemplate database(String name)
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + System.nanoTime() + ";MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", "");
        return new NamedParameterJdbcTemplate(dataSource);
    }

    private void createSchema()
    {
        u8.getJdbcTemplate().execute("CREATE TABLE rdrecord32 (id BIGINT, ccode VARCHAR(100), cBusCode VARCHAR(100), cMaker VARCHAR(100), ddate DATE, cWhCode VARCHAR(100), cCusCode VARCHAR(100), cDepCode VARCHAR(100), cMemo VARCHAR(1000), dVeriDate TIMESTAMP, dnmaketime TIMESTAMP, dnmodifytime TIMESTAMP)");
        u8.getJdbcTemplate().execute("CREATE TABLE Warehouse (cWhCode VARCHAR(100), cWhName VARCHAR(100))");
        u8.getJdbcTemplate().execute("CREATE TABLE Customer (cCusCode VARCHAR(100), cCusName VARCHAR(100))");
        u8.getJdbcTemplate().execute("CREATE TABLE Department (cDepCode VARCHAR(100), cDepName VARCHAR(100))");
        u8.getJdbcTemplate().execute("CREATE TABLE Inventory (cInvCode VARCHAR(100), cInvName VARCHAR(100), cInvStd VARCHAR(100), cComUnitCode VARCHAR(100))");
        u8.getJdbcTemplate().execute("CREATE TABLE ComputationUnit (cComUnitCode VARCHAR(100), cComUnitName VARCHAR(100))");
        u8.getJdbcTemplate().execute("CREATE TABLE rdrecords32 (autoid BIGINT, id BIGINT, cInvCode VARCHAR(100), iQuantity DECIMAL(24,4), iNum DECIMAL(20,4), cBatch VARCHAR(100), cAssUnit VARCHAR(100), dMadeDate DATE, dVDate DATE)");
        oa.getJdbcTemplate().execute("CREATE TABLE ckgly (field0031 VARCHAR(100), field0029 VARCHAR(4000))");
    }

    private void seedDocument()
    {
        u8.update("INSERT INTO Warehouse VALUES ('WH-01','成品仓')", Map.of());
        u8.update("INSERT INTO Customer VALUES ('C-01','客户甲')", Map.of());
        u8.update("INSERT INTO Department VALUES ('D-01','销售部')", Map.of());
        u8.update("INSERT INTO ComputationUnit VALUES ('U1','箱'),('U2','件')", Map.of());
        u8.update("INSERT INTO Inventory VALUES ('INV-1','商品A','S','U1')", Map.of());
        u8.update("INSERT INTO rdrecord32 VALUES (10001,'CK-001','SO-9','张三','2026-09-12','WH-01','C-01','D-01','备注',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", Map.of());
        u8.update("INSERT INTO rdrecords32 VALUES (20001,10001,'INV-1',12.5,2,'B-1','U2','2026-08-01','2027-08-01')", Map.of());
        oa.update("INSERT INTO ckgly VALUES ('WH-01','900000000000001')", Map.of());
    }
}
