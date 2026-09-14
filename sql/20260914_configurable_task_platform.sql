-- 可配置任务平台：任务身份与不可变修订。
-- 本脚本只新增对象，可重复执行；不修改现有参照、定时同步或执行记录数据。

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
