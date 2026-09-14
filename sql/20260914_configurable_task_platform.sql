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

-- 参照任务的 SQL 从此由配置版本保存，不再让生产执行按类路径资源选择 SQL 文件。
SET @int_reference_sql_text_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_reference_task')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_reference_task' AND COLUMN_NAME = 'sql_text'),
    'ALTER TABLE int_reference_task ADD COLUMN sql_text LONGTEXT DEFAULT NULL COMMENT ''已发布参照 SQL 文本'' AFTER datasource_key',
    'SELECT 1'
);
PREPARE int_reference_sql_text_stmt FROM @int_reference_sql_text_ddl;
EXECUTE int_reference_sql_text_stmt;
DEALLOCATE PREPARE int_reference_sql_text_stmt;

-- 保留旧列仅作为升级来源，改为可空后新建/编辑任务不会再写入资源路径。
SET @int_reference_sql_resource_nullable_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_reference_task' AND COLUMN_NAME = 'sql_resource' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE int_reference_task MODIFY COLUMN sql_resource VARCHAR(255) NULL COMMENT ''仅供旧版本迁移来源，不再用于运行''',
    'SELECT 1'
);
PREPARE int_reference_sql_resource_nullable_stmt FROM @int_reference_sql_resource_nullable_ddl;
EXECUTE int_reference_sql_resource_nullable_stmt;
DEALLOCATE PREPARE int_reference_sql_resource_nullable_stmt;

SET @int_reference_material_sql := 'SELECT *
FROM (
    SELECT
        ch.iinvweight jz,
        ch.cComUnitCode zdwbm,
        ch.cSTComUnitCode fdwbm,
        ch.cInvCode,
        cInvName,
        cInvStd,
        zdw.cComUnitName zdw,
        fdw.cComUnitName fdw,
        CAST ((CASE WHEN igrouptype = 2 AND ISNULL(xcl.iNUM, 0) <> 0
            THEN ABS(ISNULL(xcl.iQuantity, 0)) / ABS(ISNULL(xcl.iNUM, 1))
            ELSE ISNULL(ComputationUnit2.iChangRate, 0) END) AS DECIMAL(20, 6)) hsl,
        cInvDefine2 zldj,
        ch.iMassDate,
        chdl.cInvCName chdl,
        ch.cAddress cd,
        cInvDefine4 mrscbm
    FROM Inventory ch
    LEFT JOIN ComputationUnit zdw ON ch.cComUnitCode = zdw.cComunitCode
    LEFT JOIN ComputationUnit fdw ON ch.cSTComUnitCode = fdw.cComunitCode
    LEFT JOIN ComputationUnit ComputationUnit2 ON ch.cSAComUnitCode = ComputationUnit2.cComunitCode
    LEFT JOIN InventoryClass chdl ON chdl.cInvCCode = LEFT(ch.cInvCCode, 2)
    LEFT JOIN CurrentStock xcl ON ch.cinvcode = xcl.cinvcode
    GROUP BY ch.iinvweight, ch.cComUnitCode, ch.cSTComUnitCode, ch.cInvCode, cInvName, cInvStd,
        zdw.cComUnitName, fdw.cComUnitName, cInvDefine4,
        CAST ((CASE WHEN igrouptype = 2 AND ISNULL(xcl.iNUM, 0) <> 0
            THEN ABS(ISNULL(xcl.iQuantity, 0)) / ABS(ISNULL(xcl.iNUM, 1))
            ELSE ISNULL(ComputationUnit2.iChangRate, 0) END) AS DECIMAL(20, 6)),
        cInvDefine2, ch.iMassDate, chdl.cInvCName, ch.cAddress, dEDate
    HAVING dEDate IS NULL
) a';
SET @int_reference_material_seed := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_reference_task' AND COLUMN_NAME = 'sql_text'),
    'UPDATE int_reference_task SET sql_text = @int_reference_material_sql WHERE task_code = ''U8_MATERIAL_REFERENCE'' AND (sql_text IS NULL OR sql_text = '''')',
    'SELECT 1'
);
PREPARE int_reference_material_seed_stmt FROM @int_reference_material_seed;
EXECUTE int_reference_material_seed_stmt;
DEALLOCATE PREPARE int_reference_material_seed_stmt;

-- 任务中心是控制面唯一入口：菜单只授予受控配置权限，不暴露脚本、任意外部地址或写库能力。
SET @integration_task_root := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '任务中心', @integration_task_root, 4, 'task', 'integration/task/index', '', '', 1, 0, 'C', '0', '0', 'integration:task:list', 'list', 'admin', NOW(), '', NULL, '版本化集成任务配置'
WHERE @integration_task_root IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = @integration_task_root AND path = 'task' AND menu_type = 'C');

SET @integration_task_menu := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @integration_task_root AND path = 'task' AND menu_type = 'C' ORDER BY menu_id LIMIT 1
);
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '任务编辑', @integration_task_menu, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:task:edit', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_task_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:task:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '任务预览', @integration_task_menu, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:task:preview', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_task_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:task:preview');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '任务发布', @integration_task_menu, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:task:publish', '#', 'admin', NOW(), '', NULL, ''
WHERE @integration_task_menu IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:task:publish');
