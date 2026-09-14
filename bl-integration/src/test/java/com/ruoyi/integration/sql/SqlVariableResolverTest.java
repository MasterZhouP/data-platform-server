package com.ruoyi.integration.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.oatou8.ExecutionVariableContext;

class SqlVariableResolverTest
{
    @Test
    void resolvesOnlyScalarValuesFromTheDeclaredExecutionNamespaces()
    {
        ExecutionVariableContext context = new ExecutionVariableContext(
                Map.of("masterId", "M-100"), Map.of("bookCode", "001"), Map.of("voucherType", "记"),
                Map.of(), Map.of());

        Map<String, Object> parameters = new SqlVariableResolver().resolve(Map.of(
                "masterId", "trigger.masterId", "bookCode", "task.constants.bookCode", "voucherType", "data.voucherType"), context);

        assertEquals("M-100", parameters.get("masterId"));
        assertEquals("001", parameters.get("bookCode"));
        assertEquals("记", parameters.get("voucherType"));
    }

    @Test
    void refusesAListOrUnknownVariableAsSqlParameter()
    {
        ExecutionVariableContext context = new ExecutionVariableContext(Map.of(), Map.of(), Map.of("lines", java.util.List.of("L-1")), Map.of(), Map.of());
        SqlVariableResolver resolver = new SqlVariableResolver();

        assertThrows(SqlExecutionException.class, () -> resolver.resolve(Map.of("lines", "data.lines"), context));
        assertThrows(SqlExecutionException.class, () -> resolver.resolve(Map.of("missing", "result.voucherNo"), context));
    }
}
