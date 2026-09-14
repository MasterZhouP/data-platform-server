package com.ruoyi.integration.reference.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.Connection;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceEngineTest {
    final ObjectMapper mapper = new ObjectMapper();
    ReferenceEngine engine;
    ReferenceTask task;
    JdbcDataSource ds;
    ReferenceTask fixture(String sql, String fieldDefinitions) throws Exception {
        ObjectNode metadata=task.metadata().deepCopy();
        ObjectNode set=(ObjectNode)metadata.path("resultSets").get(0);
        set.set("fields",mapper.readTree(fieldDefinitions));
        ((ObjectNode)set.path("defaults")).putArray("sort");
        return new ReferenceTask("GENERIC", "通用参照", true,task.datasourceKey(),sql,metadata);
    }

    @BeforeEach void setup() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MSSQLServer;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection(); var s = c.createStatement()) {
            s.execute("CREATE TABLE Inventory (iinvweight DECIMAL(20,6), cComUnitCode VARCHAR, cSTComUnitCode VARCHAR, cSAComUnitCode VARCHAR, cInvCode VARCHAR, cInvName VARCHAR, cInvStd VARCHAR, igrouptype INT, cInvDefine2 VARCHAR, iMassDate INT, cInvCCode VARCHAR, cAddress VARCHAR, cInvDefine4 VARCHAR, dEDate DATE)");
            s.execute("CREATE TABLE ComputationUnit (cComunitCode VARCHAR, cComUnitName VARCHAR, iChangRate DECIMAL(20,6))");
            s.execute("CREATE TABLE InventoryClass (cInvCCode VARCHAR, cInvCName VARCHAR)");
            s.execute("CREATE TABLE CurrentStock (cinvcode VARCHAR, iNUM DECIMAL(20,6), iQuantity DECIMAL(20,6))");
            s.execute("INSERT INTO Inventory VALUES (12345678901234.123456, '01','02','02','0001','50%_[清洗','',2,NULL,365,'AA01',NULL,'D001',NULL), (1,'01','02','02','0002','普通',NULL,2,'A',30,'AA01','北京','D002',NULL), (1,'01','02','02','0003','已停用',NULL,2,NULL,30,'AA01',NULL,NULL,DATE '2020-01-01')");
            s.execute("INSERT INTO ComputationUnit VALUES ('01','千克',1),('02','箱',10)");
            s.execute("INSERT INTO InventoryClass VALUES ('AA','原材料')");
            s.execute("INSERT INTO CurrentStock VALUES ('0001',1,2),('0001',1,3),('0002',1,10),('0003',1,5)");
        }
        ObjectNode metadata = (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/integration/reference/u8-material-metadata.json"));
        task = new ReferenceTask("U8_MATERIAL_REFERENCE", "物料参照", true, "u8-test", materialSql(), metadata);
        // H2 has no COUNT_BIG aggregate. Translate that one dialect spelling at the JDBC boundary;
        // all joins, filters, bound values, sorting, paging, and returned rows run on real JDBC.
        engine = new ReferenceEngine(key -> new DelegatingDataSource(ds) {
            @Override public Connection getConnection() throws java.sql.SQLException {
                Connection real = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) args[0] = ((String) args[0]).replace("COUNT_BIG(*)", "COUNT(*)");
                    try { return method.invoke(real, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
            }
        }, mapper);
    }

    ObjectNode request() { return mapper.createObjectNode().put("resultSetCode", "tou").put("metadataVersion", "1"); }
    ObjectNode filtered(String filter) throws Exception { ObjectNode r = request(); r.set("filter", mapper.readTree(filter)); return r; }

    @Test void returnsAllFieldsAndPreservesDuplicateCodesPrecisionAndNulls() {
        ObjectNode result = engine.query(task, request());
        assertEquals(3, result.path("total").asInt());
        assertEquals(1, result.path("totalPages").asInt());
        JsonNode rows = result.path("rows");
        assertEquals(14, rows.get(0).size());
        assertEquals("0001", rows.get(0).path("cInvCode").asText());
        assertEquals("0001", rows.get(1).path("cInvCode").asText());
        assertEquals("2.000000", rows.get(0).path("hsl").asText());
        assertEquals("3.000000", rows.get(1).path("hsl").asText());
        assertEquals("12345678901234.123456", rows.get(0).path("jz").asText());
        assertEquals("", rows.get(0).path("cInvStd").asText());
        assertTrue(rows.get(0).path("zldj").isNull());
        assertEquals("D001", rows.get(0).path("mrscbm").asText());
        rows.forEach(row -> row.forEach(value -> assertTrue(value.isTextual() || value.isNull())));
    }

    @Test void executesPersistedSqlTextRatherThanClasspathSqlResource() throws Exception {
        ObjectNode metadata = task.metadata().deepCopy();
        ObjectNode set = (ObjectNode) metadata.path("resultSets").get(0);
        set.set("fields", mapper.readTree("""
                [{"name":"cInvCode","label":"编码","order":1,"dataType":"STRING","nullable":false,
                  "filterOperators":["eq"],"sortable":true}]
                """));
        ObjectNode defaults = (ObjectNode) set.path("defaults");
        defaults.set("displayFields", mapper.readTree("[\"cInvCode\"]"));
        defaults.set("filterFields", mapper.readTree("[\"cInvCode\"]"));
        defaults.set("sort", mapper.readTree("[{\"field\":\"cInvCode\",\"direction\":\"ASC\"}]"));
        ReferenceTask persisted = new ReferenceTask("PUBLISHED", "已发布参照", true, "u8-test",
                "SELECT 'published' AS cInvCode", metadata);

        assertEquals("published", engine.query(persisted, request()).path("rows").get(0).path("cInvCode").asText());
    }

    @Test void literalWildcardsAndQuotesCannotExpandUserFilter() throws Exception {
        assertEquals(2, engine.query(task, filtered("""
            {"logic":"AND","conditions":[{"field":"cInvName","operator":"contains","values":["%_["]}]}
            """)).path("total").asInt());
        assertEquals(0, engine.query(task, filtered("""
            {"logic":"AND","conditions":[{"field":"cInvCode","operator":"eq","values":["' OR 1=1 --"]}]}
            """)).path("total").asInt());
    }

    @Test void nestedOrCannotBypassFixedSourceFilterAndCountsMatchPagination() throws Exception {
        ObjectNode r = filtered("""
            {"logic":"OR","conditions":[{"field":"cInvCode","operator":"eq","values":["0001"]},{"logic":"AND","conditions":[{"field":"cInvCode","operator":"eq","values":["0003"]}]}]}
            """);
        r.put("pageSize", 1).put("pageNum", 2);
        ObjectNode second = engine.query(task, r);
        assertEquals(2, second.path("total").asInt());
        assertEquals(2, second.path("totalPages").asInt());
        assertEquals("3.000000", second.path("rows").get(0).path("hsl").asText());
        r.put("pageNum", 3);
        assertEquals(0, engine.query(task,r).path("rows").size());
    }

    @ParameterizedTest @ValueSource(strings = {
        "{\"pageSize\":201}", "{\"pageNum\":0}", "{\"pageNum\":1.0}", "{\"pageSize\":\"20\"}",
        "{\"sql\":\"select 1\"}", "{\"filter\":null}", "{\"filter\":{\"logic\":\"AND\",\"conditions\":[]}}",
        "{\"filter\":{\"logic\":\"AND\",\"conditions\":[{\"field\":\"cInvCode\",\"operator\":\"eq\",\"values\":[null]}]}}",
        "{\"filter\":{\"logic\":\"AND\",\"conditions\":[{\"field\":\"cInvCode\",\"operator\":\"contains\",\"values\":[\"\"]}]}}",
        "{\"context\":{\"masterId\":2}}", "{\"context\":{\"masterId\":\"\"}}", "{\"context\":{\"sql\":\"bad\"}}",
        "{\"sort\":null}", "{\"parameters\":null}"
    }) void rejectsMalformedRequestsBeforeDatasourceLookup(String extra) throws Exception {
        engine = new ReferenceEngine(key -> { throw new AssertionError("must validate before execution"); }, mapper);
        ObjectNode r = request(); r.setAll((ObjectNode) mapper.readTree(extra));
        assertEquals("INVALID_ARGUMENT", assertThrows(ReferenceException.class, () -> engine.query(task, r)).code());
    }

    @Test void rejectsUnknownFieldsParametersAndStaleVersion() throws Exception {
        assertEquals("FILTER_NOT_ALLOWED", assertThrows(ReferenceException.class, () -> engine.validate(task, filtered("""
            {"logic":"AND","conditions":[{"field":"cInvCode; DROP TABLE Inventory","operator":"eq","values":["x"]}]}
            """))).code());
        ObjectNode r = request(); r.putObject("parameters").put("sql", "oops");
        assertEquals("PARAMETER_NOT_ALLOWED", assertThrows(ReferenceException.class, () -> engine.validate(task,r)).code());
        assertEquals("METADATA_VERSION_MISMATCH", assertThrows(ReferenceException.class, () -> engine.validate(task,request().put("metadataVersion","old"))).code());
        ObjectNode sort = request(); sort.putArray("sort").addObject().put("field","hsl").put("direction","ASC");
        assertEquals("SORT_NOT_ALLOWED", assertThrows(ReferenceException.class, () -> engine.validate(task,sort)).code());
    }

    @Test void verifiesAllJdbcTypesAndColumnsEvenOnEmptyPage() throws Exception {
        ((ObjectNode)task.metadata().path("resultSets").get(0).path("fields").get(0)).put("dataType", "DECIMAL");
        assertEquals("RESULT_SCHEMA_MISMATCH", assertThrows(ReferenceException.class, () -> engine.query(task,request().put("pageNum",999))).code());
    }

    @Test void introspectionKeepsLabelsAndDiscoversRealLogicalTypes() {
        ObjectNode metadata = engine.inspect(task);
        assertEquals(14, metadata.path("resultSets").get(0).path("fields").size());
        JsonNode hsl = null;
        for (JsonNode field : metadata.path("resultSets").get(0).path("fields")) if (field.path("name").asText().equals("hsl")) hsl = field;
        assertNotNull(hsl);
        assertEquals("DECIMAL", hsl.path("dataType").asText());
        assertEquals("换算率", hsl.path("label").asText());
    }

    @Test void unavailableDatasourceUsesSafeRetryableError() {
        engine = new ReferenceEngine(key -> null, mapper);
        ReferenceException error = assertThrows(ReferenceException.class, () -> engine.query(task,request()));
        assertEquals("DATASOURCE_UNAVAILABLE", error.code());
        assertEquals(503, error.httpStatus());
        assertTrue(error.retryable());
    }

    @Test void numericFiltersUseDecimalsAndIntegersRatherThanLexicalComparisons() throws Exception {
        for (JsonNode field : task.metadata().path("resultSets").get(0).path("fields")) {
            if (field.path("name").asText().equals("hsl") || field.path("name").asText().equals("iMassDate"))
                ((ObjectNode)field).putArray("filterOperators").add("gt").add("between").add("in").add("eq");
        }
        assertEquals(1, engine.query(task,filtered("""
            {"logic":"AND","conditions":[{"field":"hsl","operator":"gt","values":["3.000000"]}]}
            """)).path("total").asInt());
        assertEquals(2, engine.query(task,filtered("""
            {"logic":"AND","conditions":[{"field":"hsl","operator":"between","values":["2","3"]}]}
            """)).path("total").asInt());
        assertEquals(2, engine.query(task,filtered("""
            {"logic":"AND","conditions":[{"field":"iMassDate","operator":"in","values":["365"]}]}
            """)).path("total").asInt());
        for (String value : new String[]{"NaN", "1e3", " 3", "1;SELECT", "3.2.1"}) {
            ObjectNode r = filtered("""
                {"logic":"AND","conditions":[{"field":"hsl","operator":"gt","values":["0"]}]}
                """);
            ((com.fasterxml.jackson.databind.node.ArrayNode)r.path("filter").path("conditions").get(0).path("values")).set(0,mapper.getNodeFactory().textNode(value));
            assertEquals("INVALID_ARGUMENT", assertThrows(ReferenceException.class, () -> engine.validate(task,r)).code());
        }
        assertEquals("INVALID_ARGUMENT", assertThrows(ReferenceException.class, () -> engine.validate(task,filtered("""
            {"logic":"AND","conditions":[{"field":"hsl","operator":"between","values":["10","2"]}]}
            """))).code());
    }

    @Test void enforcesGroupDepthLeafCountUnknownKeysAndDuplicateSort() throws Exception {
        ObjectNode leaf = (ObjectNode) mapper.readTree("{\"field\":\"cInvCode\",\"operator\":\"eq\",\"values\":[\"0001\"]}");
        ObjectNode group = mapper.createObjectNode().put("logic","AND");
        group.putArray("conditions").add(leaf);
        ObjectNode depth3 = mapper.createObjectNode().put("logic","AND"); depth3.putArray("conditions").add(group);
        ObjectNode depth2 = mapper.createObjectNode().put("logic","AND"); depth2.putArray("conditions").add(depth3);
        ObjectNode r = request(); r.set("filter",depth2); engine.validate(task,r);
        ObjectNode depth4 = mapper.createObjectNode().put("logic","AND"); depth4.putArray("conditions").add(depth2); r.set("filter",depth4);
        assertEquals("INVALID_ARGUMENT",assertThrows(ReferenceException.class,()->engine.validate(task,r)).code());
        ObjectNode twenty = mapper.createObjectNode().put("logic","OR");
        var items = twenty.putArray("conditions"); for(int i=0;i<20;i++)items.add(leaf.deepCopy());
        r.set("filter",twenty); engine.validate(task,r);
        ObjectNode root = mapper.createObjectNode().put("logic","AND"); root.putArray("conditions").add(twenty).add(leaf);r.set("filter",root);
        assertEquals("INVALID_ARGUMENT",assertThrows(ReferenceException.class,()->engine.validate(task,r)).code());
        ObjectNode badLeaf = leaf.deepCopy().put("sql","OR 1=1");
        ObjectNode badGroup = mapper.createObjectNode().put("logic","AND");badGroup.putArray("conditions").add(badLeaf);r.set("filter",badGroup);
        assertEquals("INVALID_ARGUMENT",assertThrows(ReferenceException.class,()->engine.validate(task,r)).code());
        r.remove("filter");var sort=r.putArray("sort");sort.addObject().put("field","cInvCode").put("direction","ASC");sort.addObject().put("field","cInvCode").put("direction","DESC");
        assertEquals("SORT_NOT_ALLOWED",assertThrows(ReferenceException.class,()->engine.validate(task,r)).code());
    }

    @Test void rejectsWriteSqlTextAndNeverReturnsJdbcSecrets() {
        ReferenceTask bad = new ReferenceTask(task.taskCode(),task.taskName(),true,task.datasourceKey(),"SELECT 1; DELETE FROM Inventory",task.metadata());
        assertEquals("INVALID_ARGUMENT",assertThrows(ReferenceException.class,()->engine.validate(bad,request())).code());
        engine = new ReferenceEngine(key -> new DelegatingDataSource(ds) {
            @Override public Connection getConnection() throws java.sql.SQLException {
                throw new java.sql.SQLException("password=secret; jdbc:sqlserver://private-host", "08001");
            }
        },mapper);
        ReferenceException error=assertThrows(ReferenceException.class,()->engine.query(task,request()));
        assertEquals("DATASOURCE_UNAVAILABLE",error.code());
        assertFalse(error.getMessage().contains("secret"));
        assertNull(error.getCause());
    }

    private String materialSql() throws Exception {
        try (var stream = getClass().getResourceAsStream("/integration/reference/material.sql")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test @Timeout(13) void totalBudgetIncludesConnectionAcquisitionAndCancelsWaitingWork() {
        engine = new ReferenceEngine(key -> new DelegatingDataSource(ds) {
            @Override public Connection getConnection() throws java.sql.SQLException {
                try { Thread.sleep(20_000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.sql.SQLTimeoutException(); }
                return super.getConnection();
            }
        },mapper);
        long start=System.nanoTime();
        ReferenceException error=assertThrows(ReferenceException.class,()->engine.query(task,request()));
        assertEquals("QUERY_TIMEOUT",error.code());
        assertTrue(error.retryable());
        assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<11_500);
    }

    @Test void rejectsDuplicateSqlLabelsAsSchemaMismatchBeforeDerivedTableCompilation() throws Exception {
        ReferenceTask duplicate=fixture("SELECT 1 AS duplicate, 2 AS duplicate", """
            [{"name":"duplicate","label":"重复","order":1,"dataType":"INTEGER","nullable":true,"filterOperators":[],"sortable":true}]
            """);
        assertEquals("RESULT_SCHEMA_MISMATCH",assertThrows(ReferenceException.class,()->engine.inspect(duplicate)).code());
        assertEquals("RESULT_SCHEMA_MISMATCH",assertThrows(ReferenceException.class,()->engine.query(duplicate,request())).code());
    }

    @Test void unknownJdbcNullTypeRetainsPublishedLogicalTypeDuringIntrospection() throws Exception {
        ReferenceTask nullColumn=fixture("SELECT NULL AS empty", """
            [{"name":"empty","label":"空值","order":1,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":true}]
            """);
        assertEquals("STRING",engine.inspect(nullColumn).path("resultSets").get(0).path("fields").get(0).path("dataType").asText());
        assertTrue(engine.query(nullColumn,request()).path("rows").get(0).path("empty").isNull());
    }

    @Test void genericTaskSerializesEverySupportedCellTypeWithoutLoss() throws Exception {
        ReferenceTask generic=fixture("SELECT CAST('0009' AS VARCHAR) code, CAST(9007199254740993 AS BIGINT) large, CAST(12345678901234.123456 AS DECIMAL(20,6)) amount, CAST(1 AS BOOLEAN) active, CAST('2026-09-07' AS DATE) [day], CAST('2026-09-07 01:02:03.123456789' AS TIMESTAMP(9)) moment", """
            [{"name":"code","label":"编码","order":1,"dataType":"STRING","nullable":false,"filterOperators":[],"sortable":true},
             {"name":"large","label":"整数","order":2,"dataType":"INTEGER","nullable":false,"filterOperators":[],"sortable":true},
             {"name":"amount","label":"金额","order":3,"dataType":"DECIMAL","nullable":false,"filterOperators":[],"sortable":true},
             {"name":"active","label":"启用","order":4,"dataType":"BOOLEAN","nullable":false,"filterOperators":["eq"],"sortable":true},
             {"name":"day","label":"日期","order":5,"dataType":"DATE","nullable":false,"filterOperators":["eq"],"sortable":true},
             {"name":"moment","label":"时间","order":6,"dataType":"DATETIME","nullable":false,"filterOperators":["eq"],"sortable":true}]
            """);
        JsonNode row=engine.query(generic,request()).path("rows").get(0);
        assertEquals("0009",row.path("code").asText());assertEquals("9007199254740993",row.path("large").asText());
        assertEquals("12345678901234.123456",row.path("amount").asText());assertEquals("true",row.path("active").asText());
        assertEquals("2026-09-07",row.path("day").asText());assertEquals("2026-09-07T01:02:03.123456789",row.path("moment").asText());
        assertEquals(1,engine.query(generic,filtered("""
            {"logic":"AND","conditions":[{"field":"active","operator":"eq","values":["true"]},{"field":"day","operator":"eq","values":["2026-09-07"]},{"field":"moment","operator":"eq","values":["2026-09-07T01:02:03.123456789"]}]}
            """)).path("total").asInt());
    }

    @Test void stringRangeOrderingBelongsToDatasourceCollation() throws Exception {
        for(JsonNode field:task.metadata().path("resultSets").get(0).path("fields"))
            if(field.path("name").asText().equals("cInvCode"))((ObjectNode)field).putArray("filterOperators").add("between");
        assertDoesNotThrow(()->engine.validate(task,filtered("""
            {"logic":"AND","conditions":[{"field":"cInvCode","operator":"between","values":["a","B"]}]}
            """)));
    }

    @Test void introspectionAppendsNewFieldsWithoutCollidingWithPublishedDisplayOrder() throws Exception {
        ReferenceTask added=fixture("SELECT 1 old, 2 added", """
            [{"name":"old","label":"原字段","order":2,"dataType":"INTEGER","nullable":true,"filterOperators":[],"sortable":true}]
            """);
        JsonNode fields=engine.inspect(added).path("resultSets").get(0).path("fields");
        assertEquals(2,fields.get(0).path("order").asInt());
        assertEquals(3,fields.get(1).path("order").asInt());
    }

    @Test void declaredParametersAreTypedAndBoundInsideTrustedSql() throws Exception {
        ReferenceTask parameterized=fixture("SELECT CAST(:code AS VARCHAR) code, CAST(:amount AS DECIMAL(20,6)) amount", """
            [{"name":"code","label":"编码","order":1,"dataType":"STRING","nullable":false,"filterOperators":[],"sortable":true},
             {"name":"amount","label":"金额","order":2,"dataType":"DECIMAL","nullable":true,"filterOperators":[],"sortable":true}]
            """);
        var definitions=((ObjectNode)parameterized.metadata().path("resultSets").get(0)).putArray("parameters");
        definitions.addObject().put("name","code").put("label","编码").put("dataType","STRING").put("required",true).put("nullable",false);
        definitions.addObject().put("name","amount").put("label","金额").put("dataType","DECIMAL").put("required",false).put("nullable",true);
        ObjectNode r=request(); r.putObject("parameters").put("code","' OR 1=1 --").put("amount","12345678901234.123456");
        JsonNode row=engine.query(parameterized,r).path("rows").get(0);
        assertEquals("' OR 1=1 --",row.path("code").asText());
        assertEquals("12345678901234.123456",row.path("amount").asText());
        ((ObjectNode)r.path("parameters")).remove("amount");
        assertTrue(engine.query(parameterized,r).path("rows").get(0).path("amount").isNull());
        ((ObjectNode)r.path("parameters")).putNull("code");
        assertEquals("INVALID_ARGUMENT",assertThrows(ReferenceException.class,()->engine.validate(parameterized,r)).code());
        ((ObjectNode)r.path("parameters")).remove("code");
        assertEquals("INVALID_ARGUMENT",assertThrows(ReferenceException.class,()->engine.validate(parameterized,r)).code());
    }
}

