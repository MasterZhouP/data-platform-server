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
