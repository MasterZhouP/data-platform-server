package com.ruoyi.integration.oatou8.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class TaskConfigValidatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final TaskConfigValidator validator = new TaskConfigValidator(json, Set.of("oa", "u8"), Set.of("VOUCHER_ADD"));

    @Test
    void rejectsWriteSqlAndUnsafePlaceholderSyntax()
    {
        assertError(configWithSql("UPDATE formmain_001 SET field001='x'"), "SQL_NOT_READ_ONLY");
        assertError(configWithSql("SELECT source_id INTO integration_shadow FROM formmain_001"), "SQL_NOT_READ_ONLY");
        assertError(configWithSql("select * from formmain where id = ${trigger.masterId}"), "SQL_PLACEHOLDER_NOT_ALLOWED");
    }

    @Test
    void refusesNonScalarAndFutureStepBindings()
    {
        ObjectNode nonScalar = baseConfig();
        ((ObjectNode) nonScalar.withArray("dataSteps").get(0)).put("cardinality", "LIST");
        addDataStep(nonScalar, "second", 2, "SCALAR", "data.header");
        assertError(nonScalar, "STEP_OUTPUT_NOT_SCALAR");

        ObjectNode future = baseConfig();
        ((ObjectNode) future.withArray("dataSteps").get(0).path("parameterBindings")).put("previous", "data.second");
        addDataStep(future, "second", 2, "SCALAR", "trigger.masterId");
        assertError(future, "STEP_REFERENCE_ORDER_INVALID");
    }

    @Test
    void rejectsPollValuesOutsidePlatformLimits()
    {
        ObjectNode config = baseConfig();
        ObjectNode result = config.withArray("resultQueries").addObject();
        result.put("code", "voucherLookup");
        result.put("order", 1);
        result.put("datasourceKey", "u8");
        result.put("sql", "select voucher_no from gl_accvouch where source_id = :masterId");
        result.put("cardinality", "ONE");
        result.put("required", true);
        result.put("initialDelayMs", 0);
        result.put("intervalMs", 1000);
        result.put("maxAttempts", 21);
        result.putObject("parameterBindings").put("masterId", "trigger.masterId");
        result.putObject("outputMappings").put("voucherNo", "voucher_no");

        assertError(config, "RESULT_POLL_LIMIT_INVALID");
    }

    @Test
    void rejectsTemplateVariablesOutsideTheFixedExecutionContext()
    {
        ObjectNode config = baseConfig();
        ((ObjectNode) config.path("u8")).put("requestJsonTemplate", "{\"value\":\"{{system.getenv}}\"}");

        assertError(config, "VARIABLE_NOT_ALLOWED");
    }

    @Test
    void parsesAReadOnlyConfigurationWithBoundedResultQuery()
    {
        ObjectNode config = baseConfig();
        ObjectNode result = config.withArray("resultQueries").addObject();
        result.put("code", "voucherLookup");
        result.put("order", 1);
        result.put("datasourceKey", "u8");
        result.put("sql", "select voucher_no from gl_accvouch where source_id = :masterId");
        result.put("cardinality", "ONE");
        result.put("required", true);
        result.put("initialDelayMs", 0);
        result.put("intervalMs", 1000);
        result.put("maxAttempts", 3);
        result.putObject("parameterBindings").put("masterId", "trigger.masterId");
        result.putObject("outputMappings").put("voucherNo", "voucher_no");

        OaToU8TaskConfig parsed = validator.parseAndValidate(config);

        assertEquals("header", parsed.dataSteps().get(0).code());
        assertEquals(3, parsed.resultQueries().get(0).maxAttempts());
        assertEquals("VOUCHER_ADD", parsed.u8().operationCode());
    }

    @Test
    void acceptsManagedDatasourceKeysWithoutCapturingTheStartupYamlSources()
    {
        ObjectNode config = baseConfig();
        ((ObjectNode) config.withArray("dataSteps").get(0)).put("datasourceKey", "oa-test");
        TaskConfigValidator managed = new TaskConfigValidator(json, Set.of("VOUCHER_ADD"));

        OaToU8TaskConfig parsed = managed.parseAndValidate(config);

        assertEquals("oa-test", parsed.dataSteps().get(0).datasourceKey());
    }

    @Test
    void allowsNewTaskDefinitionsToOmitTheOperationPath()
    {
        ObjectNode config = baseConfig();
        ((ObjectNode) config.path("u8")).remove("path");

        OaToU8TaskConfig parsed = validator.parseAndValidate(config);

        org.junit.jupiter.api.Assertions.assertNull(parsed.u8().path());
    }

    private void assertError(ObjectNode config, String expectedCode)
    {
        TaskConfigException error = assertThrows(TaskConfigException.class, () -> validator.parseAndValidate(config));
        assertEquals(expectedCode, error.code());
    }

    private ObjectNode baseConfig()
    {
        ObjectNode config = json.createObjectNode();
        config.putObject("constants").put("bookCode", "001");
        addDataStep(config, "header", 1, "SCALAR", "trigger.masterId");
        ObjectNode u8 = config.putObject("u8");
        u8.put("operationCode", "VOUCHER_ADD");
        u8.put("path", "/api/voucher/add");
        u8.put("requestJsonTemplate", "{\"masterId\":\"{{trigger.masterId}}\"}");
        u8.putObject("successRule").put("pointer", "/code").putArray("allowedValues").add("0");
        u8.put("errorMessagePointer", "/message");
        u8.putArray("outputs");
        config.putArray("resultQueries");
        return config;
    }

    private void addDataStep(ObjectNode config, String code, int order, String cardinality, String masterIdBinding)
    {
        ArrayNode steps = config.withArray("dataSteps");
        ObjectNode step = steps.addObject();
        step.put("code", code);
        step.put("order", order);
        step.put("datasourceKey", "oa");
        step.put("sql", "select :masterId AS source_id");
        step.put("cardinality", cardinality);
        step.putObject("parameterBindings").put("masterId", masterIdBinding);
    }

    private ObjectNode configWithSql(String sql)
    {
        ObjectNode config = baseConfig();
        ((ObjectNode) config.withArray("dataSteps").get(0)).put("sql", sql);
        return config;
    }
}
