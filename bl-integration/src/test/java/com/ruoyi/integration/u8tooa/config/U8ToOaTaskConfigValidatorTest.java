package com.ruoyi.integration.u8tooa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.integration.oatou8.config.TaskConfigException;
import org.junit.jupiter.api.Test;

class U8ToOaTaskConfigValidatorTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final U8ToOaTaskConfigValidator validator = new U8ToOaTaskConfigValidator(json);

    @Test
    void acceptsReadOnlyIncrementalQueriesAndPayloadTemplate()
            throws Exception
    {
        U8ToOaTaskConfig value = validator.parseAndValidate(json.readTree(validConfig()));

        assertEquals("trigger.masterId", value.oa().u8IdVariable());
        assertEquals("u8", value.sync().datasourceKey());
        assertEquals(60, value.sync().overlapMinutes());
    }

    @Test
    void rejectsWriteStatementsInIncrementalQueries()
            throws Exception
    {
        JsonNode source = json.readTree(validConfig().replace(
                "SELECT ccode AS document_no, dnmaketime AS changed_at FROM rdrecord32",
                "UPDATE rdrecord32 SET ccode = ccode"));

        assertThrows(TaskConfigException.class, () -> validator.parseAndValidate(source));
    }

    @Test
    void rejectsIncrementalQueryWithoutDocumentAndTimestampAliases()
            throws Exception
    {
        JsonNode source = json.readTree(validConfig().replace(
                "SELECT ccode AS document_no, dnmaketime AS changed_at FROM rdrecord32",
                "SELECT ccode, dnmaketime FROM rdrecord32"));

        TaskConfigException failure = assertThrows(TaskConfigException.class,
                () -> validator.parseAndValidate(source));
        assertEquals("SYNC_QUERY_COLUMNS_INVALID", failure.code());
    }

    @Test
    void rejectsInvalidCursorAndUnknownPayloadVariable()
            throws Exception
    {
        JsonNode invalidCursor = json.readTree(validConfig().replace(
                "2026-01-01T00:00:00", "not-a-date"));
        assertThrows(TaskConfigException.class, () -> validator.parseAndValidate(invalidCursor));

        JsonNode invalidVariable = json.readTree(validConfig().replace(
                "{{trigger.masterId}}", "{{data.missing.id}}"));
        assertThrows(TaskConfigException.class, () -> validator.parseAndValidate(invalidVariable));
    }

    private String validConfig()
    {
        return """
                {
                  "constants": {},
                  "dataSteps": [],
                  "oa": {
                    "u8IdVariable": "trigger.masterId",
                    "payloadTemplate": {"masterId": "{{trigger.masterId}}"}
                  },
                  "sync": {
                    "datasourceKey": "u8",
                    "upperBoundSql": "SELECT CURRENT_TIMESTAMP AS db_time",
                    "createSql": "SELECT ccode AS document_no, dnmaketime AS changed_at FROM rdrecord32 WHERE dnmaketime > :fromTime AND dnmaketime <= :toTime",
                    "updateSql": "SELECT ccode AS document_no, dnmodifytime AS changed_at FROM rdrecord32 WHERE dnmodifytime > :fromTime AND dnmodifytime <= :toTime",
                    "deleteSql": "SELECT djbh AS document_no, sj AS changed_at FROM rdrecord32_delete_log WHERE sj > :fromTime AND sj <= :toTime",
                    "initialCursor": "2026-01-01T00:00:00",
                    "overlapMinutes": 60
                  }
                }
                """;
    }
}
