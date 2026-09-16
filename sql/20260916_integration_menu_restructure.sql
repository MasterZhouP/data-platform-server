-- Reorganize Data Exchange navigation by user intent: task definition, operation, connection.
-- Idempotent and permission-preserving: existing leaf/button menu ids are moved, not recreated.
SET NAMES utf8mb4;

SET @integration_menu_root := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '任务配置', @integration_menu_root, 1, 'tasks', NULL, '', '',
       1, 0, 'M', '0', '0', '', 'tree-table',
       'admin', NOW(), '', NULL, '按任务类型管理集成任务'
WHERE @integration_menu_root IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_menu_root AND path = 'tasks' AND menu_type = 'M');

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '运行管理', @integration_menu_root, 2, 'operations', NULL, '', '',
       1, 0, 'M', '0', '0', '', 'monitor',
       'admin', NOW(), '', NULL, '执行记录与人工处置入口'
WHERE @integration_menu_root IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_menu_root AND path = 'operations' AND menu_type = 'M');

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '连接管理', @integration_menu_root, 3, 'connections', NULL, '', '',
       1, 0, 'M', '0', '0', '', 'connection',
       'admin', NOW(), '', NULL, '数据源及外部系统账户'
WHERE @integration_menu_root IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_menu_root AND path = 'connections' AND menu_type = 'M');

SET @integration_task_group := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_menu_root AND path = 'tasks' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);
SET @integration_operation_group := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_menu_root AND path = 'operations' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);
SET @integration_connection_group := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_menu_root AND path = 'connections' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

-- Resolve existing menu identities by their stable permission, so this also works after a partial rerun.
SET @integration_task_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:task:list' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
SET @integration_reference_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:reference:list' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
SET @integration_execution_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:execution:list' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
SET @integration_sales_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:sync:preview' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
SET @integration_datasource_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:datasource:list' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
SET @integration_oa_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:oa-rest:list' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
SET @integration_u8_account_menu := (
    SELECT menu_id FROM sys_menu WHERE perms = 'integration:u8-account:list' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);

-- Some installations never ran the old reference-menu seed. Create the missing task entry
-- instead of silently losing the reference task from the new task navigation.
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '参照任务', @integration_task_group, 3, 'reference', 'integration/reference/index', '',
       'ReferenceTasks', 1, 0, 'C', '0', '0', 'integration:reference:list', 'search',
       'admin', NOW(), '', NULL, 'OA同步参照查询任务'
WHERE @integration_task_group IS NOT NULL
  AND @integration_reference_menu IS NULL;

SET @integration_reference_menu := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @integration_task_group AND path = 'reference' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '参照查询', @integration_reference_menu, 1, '#', '', '', '',
       1, 0, 'F', '0', '0', 'integration:reference:query', '#',
       'admin', NOW(), '', NULL, ''
WHERE @integration_reference_menu IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:reference:query');

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '参照编辑', @integration_reference_menu, 2, '#', '', '', '',
       1, 0, 'F', '0', '0', 'integration:reference:edit', '#',
       'admin', NOW(), '', NULL, ''
WHERE @integration_reference_menu IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:reference:edit');

UPDATE sys_menu
SET menu_name = 'OA → U8 任务', parent_id = @integration_task_group, order_num = 1,
    path = 'oa-to-u8', component = 'integration/task/index', `query` = '{"type":"OA_TO_U8"}',
    route_name = 'OaToU8Tasks', icon = 'upload', update_time = NOW(),
    remark = 'OA触发并写入U8的配置任务'
WHERE menu_id = @integration_task_menu AND @integration_task_group IS NOT NULL;

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, `query`, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT 'U8 → OA 任务', @integration_task_group, 2, 'u8-to-oa', 'integration/task/index',
       '{"type":"U8_TO_OA"}', 'U8ToOaTasks', 1, 0, 'C', '0', '0',
       'integration:task:list', 'download', 'admin', NOW(), '', NULL,
       'U8增量读取并发起OA流程的配置任务'
WHERE @integration_task_group IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_task_group AND path = 'u8-to-oa' AND menu_type = 'C');

SET @integration_u8_to_oa_menu := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_task_group AND path = 'u8-to-oa' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

UPDATE sys_menu
SET menu_name = '参照任务', parent_id = @integration_task_group, order_num = 3,
    path = 'reference', component = 'integration/reference/index', `query` = '',
    route_name = 'ReferenceTasks', icon = 'search', update_time = NOW(),
    remark = 'OA同步参照查询任务'
WHERE menu_id = @integration_reference_menu AND @integration_task_group IS NOT NULL;

UPDATE sys_menu
SET menu_name = '执行记录', parent_id = @integration_operation_group, order_num = 1,
    path = 'executions', component = 'integration/execution/index', `query` = '',
    route_name = 'IntegrationExecutions', icon = 'list', update_time = NOW()
WHERE menu_id = @integration_execution_menu AND @integration_operation_group IS NOT NULL;

UPDATE sys_menu
SET menu_name = '销售出库手工处理', parent_id = @integration_operation_group, order_num = 2,
    path = 'sales-outbound', component = 'integration/sync/sales-outbound/index', `query` = '',
    route_name = 'SalesOutboundManual', icon = 'guide', update_time = NOW(),
    remark = '存量销售出库任务的预览、手工推送和强制重推'
WHERE menu_id = @integration_sales_menu AND @integration_operation_group IS NOT NULL;

UPDATE sys_menu
SET menu_name = '数据源', parent_id = @integration_connection_group, order_num = 1,
    path = 'datasources', component = 'integration/datasource/index', `query` = '',
    route_name = 'IntegrationDatasources', icon = 'database', update_time = NOW()
WHERE menu_id = @integration_datasource_menu AND @integration_connection_group IS NOT NULL;

UPDATE sys_menu
SET menu_name = 'OA REST 账户', parent_id = @integration_connection_group, order_num = 2,
    path = 'oa-rest', component = 'integration/oa-rest/index', `query` = '',
    route_name = 'IntegrationOaAccounts', icon = 'user', update_time = NOW()
WHERE menu_id = @integration_oa_menu AND @integration_connection_group IS NOT NULL;

UPDATE sys_menu
SET menu_name = 'U8 开放平台账户', parent_id = @integration_connection_group, order_num = 3,
    path = 'u8-account', component = 'integration/u8-account/index', `query` = '',
    route_name = 'IntegrationU8Accounts', icon = 'server', update_time = NOW()
WHERE menu_id = @integration_u8_account_menu AND @integration_connection_group IS NOT NULL;

-- A role that could see the old generic task menu can see the new U8-to-OA typed entry.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, @integration_u8_to_oa_menu
FROM sys_role_menu
WHERE menu_id = @integration_task_menu AND @integration_u8_to_oa_menu IS NOT NULL;

-- Add the new directory ancestors for every role that already owns one of their leaf menus.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, @integration_task_group
FROM sys_role_menu
WHERE menu_id IN (@integration_task_menu, @integration_u8_to_oa_menu, @integration_reference_menu)
  AND @integration_task_group IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, @integration_operation_group
FROM sys_role_menu
WHERE menu_id IN (@integration_execution_menu, @integration_sales_menu)
  AND @integration_operation_group IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_id, @integration_connection_group
FROM sys_role_menu
WHERE menu_id IN (@integration_datasource_menu, @integration_oa_menu, @integration_u8_account_menu)
  AND @integration_connection_group IS NOT NULL;
