package com.ruoyi.integration.reference.catalog;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class ReferenceTaskConfigValidatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final ReferenceTaskConfigValidator validator = new ReferenceTaskConfigValidator();

    @Test
    void rejectsAReferenceDatasourceThatIsNotRegisteredForTheSynchronousQueryEngine()
    {
        ObjectNode config = ReferenceCatalogTestFixture.config(json, "oa");

        assertThrows(IllegalArgumentException.class,
                () -> validator.parseAndValidate("REFERENCE_DEMO", "参照示例", true, config));
    }
}
