-- 可配置任务平台：任务身份与不可变修订。
-- 本脚本只新增对象，可重复执行；不修改现有参照、定时同步或执行记录数据。

-- 执行快照与后处理检查点。沿用旧迁移的 information_schema 守卫，兼容 MySQL 5.7/8.0 并支持重复执行。
SET @int_execution_task_revision_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'task_revision_id'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN task_revision_id BIGINT DEFAULT NULL COMMENT ''受理时锁定的任务修订'' AFTER task_code'
);
PREPARE int_execution_task_revision_stmt FROM @int_execution_task_revision_ddl;
EXECUTE int_execution_task_revision_stmt;
DEALLOCATE PREPARE int_execution_task_revision_stmt;

SET @int_execution_task_checksum_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'task_checksum'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN task_checksum CHAR(64) DEFAULT NULL COMMENT ''锁定修订校验和'' AFTER task_revision_id'
);
PREPARE int_execution_task_checksum_stmt FROM @int_execution_task_checksum_ddl;
EXECUTE int_execution_task_checksum_stmt;
DEALLOCATE PREPARE int_execution_task_checksum_stmt;

SET @int_execution_dependency_snapshot_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'dependency_snapshot_json'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN dependency_snapshot_json LONGTEXT DEFAULT NULL COMMENT ''受理时依赖快照'' AFTER task_checksum'
);
PREPARE int_execution_dependency_snapshot_stmt FROM @int_execution_dependency_snapshot_ddl;
EXECUTE int_execution_dependency_snapshot_stmt;
DEALLOCATE PREPARE int_execution_dependency_snapshot_stmt;

SET @int_execution_last_stage_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'last_completed_stage'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN last_completed_stage VARCHAR(100) DEFAULT NULL COMMENT ''最后完成检查点'' AFTER stage'
);
PREPARE int_execution_last_stage_stmt FROM @int_execution_last_stage_ddl;
EXECUTE int_execution_last_stage_stmt;
DEALLOCATE PREPARE int_execution_last_stage_stmt;

SET @int_execution_u8_confirmed_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'u8_confirmed'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN u8_confirmed TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''U8业务是否已确认成功'' AFTER last_completed_stage'
);
PREPARE int_execution_u8_confirmed_stmt FROM @int_execution_u8_confirmed_ddl;
EXECUTE int_execution_u8_confirmed_stmt;
DEALLOCATE PREPARE int_execution_u8_confirmed_stmt;

SET @int_execution_resume_mode_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'resume_mode'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN resume_mode VARCHAR(20) NOT NULL DEFAULT ''FULL'' COMMENT ''FULL/POST_PROCESS'' AFTER u8_confirmed'
);
PREPARE int_execution_resume_mode_stmt FROM @int_execution_resume_mode_ddl;
EXECUTE int_execution_resume_mode_stmt;
DEALLOCATE PREPARE int_execution_resume_mode_stmt;

SET @int_execution_result_outputs_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'result_outputs_json'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN result_outputs_json LONGTEXT DEFAULT NULL COMMENT ''U8确认及后处理输出快照'' AFTER resume_mode'
);
PREPARE int_execution_result_outputs_stmt FROM @int_execution_result_outputs_ddl;
EXECUTE int_execution_result_outputs_stmt;
DEALLOCATE PREPARE int_execution_result_outputs_stmt;

CREATE TABLE IF NOT EXISTS int_integration_task (
    task_code          VARCHAR(100) COLLATE utf8mb4_bin NOT NULL COMMENT '稳定任务编码，大小写敏感',
    task_name          VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_type          VARCHAR(30) NOT NULL COMMENT 'REFERENCE_QUERY/OA_TO_U8/U8_TO_OA',
    enabled            TINYINT(1) NOT NULL DEFAULT 0 COMMENT '受理新请求时是否启用',
    active_revision_id BIGINT DEFAULT NULL COMMENT '当前生产修订ID',
    draft_revision_id  BIGINT DEFAULT NULL COMMENT '当前可编辑草稿修订ID',
    config_version     BIGINT NOT NULL DEFAULT 0 COMMENT '控制面并发版本',
    create_time        DATETIME NOT NULL COMMENT '创建时间',
    update_time        DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (task_code),
    KEY idx_int_task_type_enabled (task_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='集成任务目录';

CREATE TABLE IF NOT EXISTS int_integration_task_revision (
    revision_id                 BIGINT NOT NULL AUTO_INCREMENT COMMENT '修订ID',
    task_code                   VARCHAR(100) COLLATE utf8mb4_bin NOT NULL COMMENT '所属任务编码',
    revision_no                 INT NOT NULL COMMENT '同一任务内递增版本',
    status                      VARCHAR(20) NOT NULL COMMENT 'DRAFT/VALIDATED/PUBLISHED/ARCHIVED',
    config_json                 LONGTEXT NOT NULL COMMENT '经后端校验的受控任务配置',
    config_checksum             CHAR(64) NOT NULL COMMENT '配置SHA-256校验和',
    dependency_revisions_json   LONGTEXT DEFAULT NULL COMMENT '数据源和U8连接等依赖版本快照',
    validation_json             LONGTEXT DEFAULT NULL COMMENT '最近校验清单',
    change_note                 VARCHAR(500) DEFAULT NULL COMMENT '修订说明',
    create_time                 DATETIME NOT NULL COMMENT '创建时间',
    update_time                 DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (revision_id),
    UNIQUE KEY uk_int_task_revision_no (task_code, revision_no),
    KEY idx_int_task_revision_status (task_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='集成任务不可变修订';
