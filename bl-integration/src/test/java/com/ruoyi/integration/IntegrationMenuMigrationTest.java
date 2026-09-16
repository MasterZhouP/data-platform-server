package com.ruoyi.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IntegrationMenuMigrationTest
{
    @Test
    void migrationGroupsTaskTypesOperationsAndConnectionsWithoutChangingPermissions() throws Exception
    {
        String sql = Files.readString(Path.of("..", "sql", "20260916_integration_menu_restructure.sql"));

        assertTrue(sql.contains("'任务配置'"));
        assertTrue(sql.contains("'OA → U8 任务'"));
        assertTrue(sql.contains("'U8 → OA 任务'"));
        assertTrue(sql.contains("'参照任务'"));
        assertTrue(sql.contains("'运行管理'"));
        assertTrue(sql.contains("'连接管理'"));
        assertTrue(sql.contains("{\"type\":\"OA_TO_U8\"}"));
        assertTrue(sql.contains("{\"type\":\"U8_TO_OA\"}"));
        assertTrue(sql.contains("SET NAMES utf8mb4"));
        assertTrue(sql.contains("@integration_reference_menu IS NULL"));
        assertTrue(sql.contains("integration:reference:query"));
        assertTrue(sql.contains("integration:reference:edit"));
        assertTrue(sql.contains("INSERT IGNORE INTO sys_role_menu"));
        assertTrue(sql.contains("integration:task:list"));
        assertTrue(sql.contains("integration:reference:list"));
    }
}
