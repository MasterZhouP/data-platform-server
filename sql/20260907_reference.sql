-- Reference V1: apply after 20260904_stage1_integration.sql; safe to run repeatedly.
-- Seed metadata is SIMULATION ONLY: JDBC types/nullability are UNVERIFIED.
-- Keep seeded task disabled until the administrator inspects real U8 result types and publishes them.
-- This migration does not configure or connect to U8 and never overwrites existing task settings.
CREATE TABLE IF NOT EXISTS int_reference_task (
    task_code VARCHAR(100) COLLATE utf8mb4_bin NOT NULL COMMENT 'Case-sensitive task identifier',
    task_name VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    datasource_key VARCHAR(100) NOT NULL COMMENT 'Registered datasource key, never credentials',
    sql_resource VARCHAR(255) NOT NULL COMMENT 'Trusted classpath resource only',
    metadata_json LONGTEXT NOT NULL COMMENT 'Complete versioned result metadata; seed types unverified',
    metadata_version VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Reference task configuration';

-- Unsaved OA forms are legitimate reference callers; repeated MODIFY is harmless.
ALTER TABLE int_execution MODIFY COLUMN master_id VARCHAR(128) DEFAULT NULL COMMENT 'OA master ID; nullable for reference queries';
ALTER TABLE int_execution MODIFY COLUMN form_id VARCHAR(128) DEFAULT NULL COMMENT 'OA form ID; Reference V1 accepts up to 128 characters';
ALTER TABLE int_execution MODIFY COLUMN summary_id VARCHAR(128) DEFAULT NULL COMMENT 'OA summary ID; Reference V1 accepts up to 128 characters';

INSERT INTO int_reference_task (task_code, task_name, enabled, datasource_key, sql_resource, metadata_json, metadata_version, create_time, update_time)
SELECT 'U8_MATERIAL_REFERENCE', '物料参照', 0, 'u8', 'integration/reference/material.sql', '{"taskCode":"U8_MATERIAL_REFERENCE","taskName":"物料参照","taskType":"REFERENCE","executionMode":"SYNC_QUERY","metadataVersion":"1","resultSets":[{"resultSetCode":"tou","resultSetName":"物料参照","selectionMode":"SINGLE","fields":[{"name":"zldj","label":"质量等级","order":1,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"iMassDate","label":"保质期天数","order":2,"dataType":"INTEGER","nullable":true,"filterOperators":[],"sortable":false},{"name":"cInvCode","label":"编码","order":3,"dataType":"STRING","nullable":true,"filterOperators":["eq","contains","startsWith","isNull","isNotNull"],"sortable":true},{"name":"fdw","label":"辅单位","order":4,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"cInvStd","label":"规格","order":6,"dataType":"STRING","nullable":true,"filterOperators":["eq","contains","startsWith","isNull","isNotNull"],"sortable":true},{"name":"zdw","label":"主单位","order":7,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"cInvName","label":"名称","order":8,"dataType":"STRING","nullable":true,"filterOperators":["eq","contains","startsWith","isNull","isNotNull"],"sortable":true},{"name":"zdwbm","label":"主单位编码","order":9,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"fdwbm","label":"辅单位编码","order":10,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"chdl","label":"存货大类","order":11,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"hsl","label":"换算率","order":12,"dataType":"DECIMAL","nullable":true,"filterOperators":[],"sortable":false},{"name":"cd","label":"产地","order":13,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false},{"name":"jz","label":"净重","order":14,"dataType":"DECIMAL","nullable":true,"filterOperators":[],"sortable":false},{"name":"mrscbm","label":"默认生产部门","order":15,"dataType":"STRING","nullable":true,"filterOperators":[],"sortable":false}],"parameters":[],"defaults":{"displayFields":["cInvCode","cInvName","cInvStd","zdw","fdw","zldj"],"filterFields":["cInvCode","cInvName","cInvStd"],"sort":[{"field":"cInvCode","direction":"ASC"}],"pageSize":200},"limits":{"maxPageSize":200,"maxFilterConditions":20,"maxFilterDepth":3,"maxSortFields":5,"maxInValues":100,"queryTimeoutMs":10000}}]}', '1', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM int_reference_task WHERE task_code = 'U8_MATERIAL_REFERENCE');

SET @integration_reference_root := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '参照任务', @integration_reference_root, 2, 'reference', 'integration/reference/index', '', '', 1, 0, 'C', '0', '0', 'integration:reference:list', 'list', 'admin', NOW(), '', NULL, '同步参照配置与查询'
WHERE @integration_reference_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_reference_root AND path = 'reference' AND menu_type = 'C');
SET @integration_reference_menu := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_reference_root AND path = 'reference' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '参照查询', @integration_reference_menu, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:reference:query', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_reference_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:reference:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '参照编辑', @integration_reference_menu, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:reference:edit', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_reference_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:reference:edit');
