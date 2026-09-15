package com.ruoyi.integration.reference.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ReferenceTaskConfigValidatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final ReferenceTaskConfigValidator validator = new ReferenceTaskConfigValidator();

    @Test
    void acceptsAValidManagedDatasourceKeyInsteadOfRequiringTheLegacyU8Key()
    {
        ObjectNode config = ReferenceCatalogTestFixture.config(json, "u8-test");

        var task = assertDoesNotThrow(
                () -> validator.parseAndValidate("REFERENCE_DEMO", "参照示例", true, config));

        assertEquals("u8-test", task.datasourceKey());
    }

    @Test
    void rejectsSelectIntoBecauseItCreatesTablesOnSqlServer()
    {
        ObjectNode config = ReferenceCatalogTestFixture.config(json, "u8");
        config.put("sqlText", "SELECT code INTO reference_shadow FROM inventory");

        assertThrows(IllegalArgumentException.class,
                () -> validator.parseAndValidate("REFERENCE_DEMO", "参照示例", true, config));
    }
}
