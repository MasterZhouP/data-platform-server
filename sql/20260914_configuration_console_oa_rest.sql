-- Configuration Console D: versioned OA REST account administration.
-- Apply after backing up the configuration schema. This migration never copies the legacy
-- integration.oa.rest password from YAML; an operator must explicitly save and verify an account.

CREATE TABLE IF NOT EXISTS int_oa_rest_connection (
    connection_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    connection_name VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    active_revision_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    draft_revision_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    row_version BIGINT NOT NULL DEFAULT 1,
    target_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    create_by VARCHAR(64) DEFAULT '',
    update_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (connection_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Managed OA REST account directory';

CREATE TABLE IF NOT EXISTS int_oa_rest_revision (
    revision_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    connection_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    target_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    environment VARCHAR(16) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    rest_username VARCHAR(200) NOT NULL,
    login_name VARCHAR(200) NOT NULL,
    connect_timeout_ms INT NOT NULL,
    read_timeout_ms INT NOT NULL,
    secret_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    last_test_json LONGTEXT DEFAULT NULL COMMENT 'Safe authentication result only',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL,
    PRIMARY KEY (revision_id),
    KEY idx_oa_rest_revision_key (connection_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Immutable OA REST account revisions';

CREATE TABLE IF NOT EXISTS int_oa_rest_secret (
    secret_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    key_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    nonce VARBINARY(12) NOT NULL,
    ciphertext BLOB NOT NULL,
    owner_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
    owner_revision VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (secret_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AES-GCM encrypted OA REST passwords only';

-- Navigation and least-privilege permissions. This script deliberately does not grant them to any role.
SET @integration_oa_root := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 'OA REST账户', @integration_oa_root, 2, 'oa-rest', 'integration/oa-rest/index', '', '', 1, 0, 'C', '0', '0', 'integration:oa-rest:list', 'user', 'admin', NOW(), '', NULL, '受管OA REST账户配置、认证与启用'
WHERE @integration_oa_root IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @integration_oa_root AND path = 'oa-rest' AND menu_type = 'C'
);
SET @integration_oa_menu := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_oa_root AND path = 'oa-rest' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '查看OA REST账户', @integration_oa_menu, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:oa-rest:view', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_oa_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:oa-rest:view');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '编辑OA REST账户', @integration_oa_menu, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:oa-rest:edit', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_oa_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:oa-rest:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '认证OA REST账户', @integration_oa_menu, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:oa-rest:test', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_oa_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:oa-rest:test');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '启停OA REST账户', @integration_oa_menu, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:oa-rest:activate', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_oa_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:oa-rest:activate');
