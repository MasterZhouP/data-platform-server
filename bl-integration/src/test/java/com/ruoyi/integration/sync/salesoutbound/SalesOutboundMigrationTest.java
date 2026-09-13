package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SalesOutboundMigrationTest
{
    @Test
    void everyExecutionColumnUpgradeIsIndependentlyGuardedForSafeReruns() throws Exception
    {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("sql/20260912_u8_to_oa_sales_outbound.sql")))
        {
            root = root.getParent();
        }
        assertTrue(root != null, "migration file not found");
        String sql = Files.readString(root.resolve("sql/20260912_u8_to_oa_sales_outbound.sql"));

        assertEquals(1, occurrences(sql, "COLUMN_NAME = 'operation'"));
        assertEquals(1, occurrences(sql, "COLUMN_NAME = 'trigger_source'"));
        assertEquals(1, occurrences(sql, "COLUMN_NAME = 'force_flag'"));
        assertEquals(3, occurrences(sql, "DEALLOCATE PREPARE int_execution_"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS int_oa_process_link"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS int_sync_cursor"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS int_sync_event"));
        assertTrue(sql.contains("UNIQUE KEY uk_int_sync_event_source"));
    }

    private int occurrences(String value, String token)
    {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
