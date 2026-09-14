-- Configuration Console A: versioned, encrypted datasource administration.
-- Apply only after backing up the platform configuration schema. This migration never reads or
-- copies a password from YAML. Existing integration.datasource.* values remain compatibility
-- inputs until an administrator creates and activates a managed revision.

CREATE TABLE IF NOT EXISTS int_datasource_config (
    datasource_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    datasource_name VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    active_revision_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    draft_revision_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    row_version BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(64) DEFAULT '',
    update_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (datasource_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Managed datasource directory';

CREATE TABLE IF NOT EXISTS int_datasource_revision (
    revision_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    datasource_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    config_json LONGTEXT NOT NULL COMMENT 'Validated non-sensitive connection fields only',
    secret_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    last_test_json LONGTEXT DEFAULT NULL COMMENT 'Safe test summary only',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL,
    PRIMARY KEY (revision_id),
    KEY idx_datasource_revision_key (datasource_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Immutable datasource revisions';

CREATE TABLE IF NOT EXISTS int_datasource_secret (
    secret_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    key_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    nonce VARBINARY(12) NOT NULL,
    ciphertext BLOB NOT NULL,
    owner_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
    owner_revision VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (secret_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AES-GCM ciphertext only';

CREATE TABLE IF NOT EXISTS int_configuration_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_type VARCHAR(40) NOT NULL,
    resource_key VARCHAR(100) NOT NULL,
    revision_id VARCHAR(64) DEFAULT NULL,
    action VARCHAR(40) NOT NULL,
    operation_id VARCHAR(36) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) DEFAULT NULL,
    result_code VARCHAR(64) NOT NULL,
    safe_summary_json LONGTEXT DEFAULT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_configuration_audit_operation (resource_type, resource_key, action, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Safe configuration-operation audit trail';

-- Navigation and granular permissions. This is idempotent and does not grant the permissions to roles.
SET @integration_datasource_root := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '数据源管理', @integration_datasource_root, 1, 'datasource', 'integration/datasource/index', '', '', 1, 0, 'C', '0', '0', 'integration:datasource:list', 'database', 'admin', NOW(), '', NULL, '受管数据源配置、检测与启用'
WHERE @integration_datasource_root IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @integration_datasource_root AND path = 'datasource' AND menu_type = 'C'
);
SET @integration_datasource_menu := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_datasource_root AND path = 'datasource' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '查看数据源', @integration_datasource_menu, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:datasource:view', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_datasource_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:datasource:view');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '编辑数据源', @integration_datasource_menu, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:datasource:edit', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_datasource_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:datasource:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '检测数据源', @integration_datasource_menu, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:datasource:test', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_datasource_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:datasource:test');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '启停数据源', @integration_datasource_menu, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:datasource:activate', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_datasource_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:datasource:activate');
