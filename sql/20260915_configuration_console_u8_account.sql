-- Configuration Console: versioned, encrypted U8 public-account administration.
-- This migration never imports credentials from integration.u8.*; an administrator must
-- explicitly create and authenticate an account from the management page.

CREATE TABLE IF NOT EXISTS int_u8_gateway_connection (
    connection_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    connection_name VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    active_revision_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    draft_revision_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL,
    row_version BIGINT NOT NULL DEFAULT 1,
    create_by VARCHAR(64) DEFAULT '',
    update_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (connection_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Managed U8 gateway account directory';

CREATE TABLE IF NOT EXISTS int_u8_gateway_revision (
    revision_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    connection_key VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    environment VARCHAR(16) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    token_path VARCHAR(2000) NOT NULL,
    trade_id_path VARCHAR(2000) NOT NULL,
    token_pointer VARCHAR(500) NOT NULL,
    trade_id_pointer VARCHAR(500) NOT NULL,
    token_parameter_name VARCHAR(100) NOT NULL,
    trade_id_parameter_name VARCHAR(100) NOT NULL,
    token_cache_seconds INT NOT NULL,
    connect_timeout_ms INT NOT NULL,
    read_timeout_ms INT NOT NULL,
    account_parameter_names_json LONGTEXT NOT NULL,
    operation_registry_json LONGTEXT NOT NULL,
    secret_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    last_test_json LONGTEXT DEFAULT NULL COMMENT 'Safe authentication result only',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL,
    PRIMARY KEY (revision_id),
    KEY idx_u8_gateway_revision_key (connection_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Immutable U8 gateway revisions';

CREATE TABLE IF NOT EXISTS int_u8_gateway_secret (
    secret_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    key_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    nonce VARBINARY(12) NOT NULL,
    ciphertext BLOB NOT NULL,
    owner_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL,
    owner_revision VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (secret_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='AES-GCM encrypted U8 account parameters only';

SET @integration_root := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 'U8开放平台账户', @integration_root, 3, 'u8-account', 'integration/u8-account/index', '', '', 1, 0, 'C', '0', '0', 'integration:u8-account:list', 'server', 'admin', NOW(), '', NULL, '受管U8公共账户、认证与启用'
WHERE @integration_root IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_root AND path = 'u8-account' AND menu_type = 'C');
SET @integration_u8_menu := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_root AND path = 'u8-account' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '查看U8开放平台账户', @integration_u8_menu, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:u8-account:view', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_u8_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:u8-account:view');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '编辑U8开放平台账户', @integration_u8_menu, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:u8-account:edit', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_u8_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:u8-account:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '认证U8开放平台账户', @integration_u8_menu, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:u8-account:test', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_u8_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:u8-account:test');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '启停U8开放平台账户', @integration_u8_menu, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:u8-account:activate', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_u8_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:u8-account:activate');
