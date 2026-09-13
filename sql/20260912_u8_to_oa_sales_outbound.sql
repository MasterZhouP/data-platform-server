-- U8 -> OA 销售出库单同步底座。先执行 20260904_stage1_integration.sql。
-- 定时任务默认暂停；完成正式库联调并设置 initial-cursor 后再启用。

-- MySQL 5.7/8.0 compatible idempotent column upgrades. Each guard also makes a
-- partially applied deployment safe to rerun.
SET @int_execution_operation_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'operation'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN operation VARCHAR(30) NOT NULL DEFAULT ''CREATE'' COMMENT ''CREATE/CANCEL_RECREATE/DELETE'' AFTER summary_id'
);
PREPARE int_execution_operation_stmt FROM @int_execution_operation_ddl;
EXECUTE int_execution_operation_stmt;
DEALLOCATE PREPARE int_execution_operation_stmt;

SET @int_execution_trigger_source_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'trigger_source'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN trigger_source VARCHAR(20) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL/SCHEDULED/RETRY'' AFTER operation'
);
PREPARE int_execution_trigger_source_stmt FROM @int_execution_trigger_source_ddl;
EXECUTE int_execution_trigger_source_stmt;
DEALLOCATE PREPARE int_execution_trigger_source_stmt;

SET @int_execution_force_flag_ddl := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'int_execution' AND COLUMN_NAME = 'force_flag'),
    'SELECT 1',
    'ALTER TABLE int_execution ADD COLUMN force_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否强制重推'' AFTER trigger_source'
);
PREPARE int_execution_force_flag_stmt FROM @int_execution_force_flag_ddl;
EXECUTE int_execution_force_flag_stmt;
DEALLOCATE PREPARE int_execution_force_flag_stmt;

CREATE TABLE IF NOT EXISTS int_oa_process_link (
    link_id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    task_code        VARCHAR(100)  NOT NULL COMMENT '任务编码',
    business_key     VARCHAR(200)  NOT NULL COMMENT '业务单号，本任务为U8单据编号',
    u8_id            VARCHAR(100)  NOT NULL COMMENT 'U8主表ID',
    summary_id       VARCHAR(100)  DEFAULT NULL COMMENT 'OA summaryId',
    affair_id        VARCHAR(100)  DEFAULT NULL COMMENT 'OA affairId',
    process_id       VARCHAR(100)  DEFAULT NULL COMMENT 'OA processId',
    state            VARCHAR(40)   NOT NULL COMMENT '流程关联生命周期状态',
    version_no       INT           NOT NULL COMMENT '同业务单据推送版本',
    previous_link_id BIGINT        DEFAULT NULL COMMENT '被替换的上一版本',
    create_time      DATETIME      NOT NULL,
    update_time      DATETIME      NOT NULL,
    PRIMARY KEY (link_id),
    UNIQUE KEY uk_int_oa_link_version (task_code, business_key, version_no),
    KEY idx_int_oa_link_business (task_code, business_key, state),
    KEY idx_int_oa_link_summary (summary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='U8到OA流程关联历史';

CREATE TABLE IF NOT EXISTS int_sync_cursor (
    task_code   VARCHAR(100) NOT NULL COMMENT '任务编码',
    cursor_time DATETIME(6)  NOT NULL COMMENT '上次成功受理到的U8数据库时间',
    create_time DATETIME     NOT NULL,
    update_time DATETIME     NOT NULL,
    PRIMARY KEY (task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时增量同步游标';

CREATE TABLE IF NOT EXISTS int_sync_event (
    event_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    source_event_id CHAR(64)     NOT NULL COMMENT '任务/单据/动作/变更时间的SHA-256',
    task_code       VARCHAR(100) NOT NULL COMMENT '任务编码',
    document_no     VARCHAR(200) NOT NULL COMMENT '来源单据编号',
    operation       VARCHAR(30)  NOT NULL COMMENT 'CREATE/CANCEL_RECREATE/DELETE',
    changed_at      DATETIME(6)  NOT NULL COMMENT '来源业务变更时间',
    status          VARCHAR(20)  NOT NULL COMMENT 'PENDING/ACCEPTED/SUPERSEDED',
    execution_id    BIGINT       DEFAULT NULL COMMENT '已受理执行ID',
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_int_sync_event_source (source_event_id),
    KEY idx_int_sync_event_pending (task_code, status, changed_at),
    KEY idx_int_sync_event_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时增量事件账本';

SET @integration_root_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '销售出库推送', @integration_root_id, 3, 'sales-outbound',
       'integration/sync/sales-outbound/index', '', '',
       1, 0, 'C', '0', '0', 'integration:sync:preview', 'guide',
       'admin', NOW(), '', NULL, 'U8销售出库单推送OA'
WHERE @integration_root_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE parent_id = @integration_root_id AND path = 'sales-outbound' AND menu_type = 'C'
  );

SET @sales_outbound_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @integration_root_id AND path = 'sales-outbound' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '单据预览', @sales_outbound_menu_id, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:sync:preview', '#', 'admin', NOW(), '', NULL, ''
WHERE @sales_outbound_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:sync:preview');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '手动推送', @sales_outbound_menu_id, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:sync:push', '#', 'admin', NOW(), '', NULL, ''
WHERE @sales_outbound_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:sync:push');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '强制重推', @sales_outbound_menu_id, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'integration:sync:force', '#', 'admin', NOW(), '', NULL, ''
WHERE @sales_outbound_menu_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:sync:force');

INSERT INTO sys_job (
    job_name, job_group, invoke_target, cron_expression, misfire_policy,
    concurrent, status, create_by, create_time, update_by, update_time, remark
)
SELECT 'U8销售出库单推送OA', 'INTEGRATION',
       'integrationSyncTask.run(\'U8_TO_OA_SALES_OUTBOUND\')', '0 0/5 * * * ?', '3',
       '1', '1', 'admin', NOW(), '', NULL,
       '联调并配置 integration.sync.sales-outbound.initial-cursor 后启用'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job
    WHERE invoke_target = 'integrationSyncTask.run(\'U8_TO_OA_SALES_OUTBOUND\')'
);
