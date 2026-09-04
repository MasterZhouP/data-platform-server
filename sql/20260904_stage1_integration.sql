-- Stage 1: 集成执行记录、阶段日志和管理菜单
-- 仅增量创建，不删除或清空任何既有对象。脚本可重复执行。

CREATE TABLE IF NOT EXISTS int_execution (
    execution_id           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '执行ID',
    task_code              VARCHAR(100)   NOT NULL COMMENT '任务编码',
    master_id              VARCHAR(100)   NOT NULL COMMENT 'OA主记录ID',
    business_key           VARCHAR(200)   DEFAULT NULL COMMENT '平台从OA数据计算的业务标识',
    form_id                VARCHAR(100)   DEFAULT NULL COMMENT 'OA表单ID',
    summary_id             VARCHAR(100)   DEFAULT NULL COMMENT 'OA流程ID，可空',
    status                 VARCHAR(20)    NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    stage                  VARCHAR(50)    NOT NULL COMMENT '当前或最终阶段',
    retryable              TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '失败是否允许人工重试',
    result_unknown         TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '外部处理结果是否未知',
    retry_count            INT            NOT NULL DEFAULT 0 COMMENT '重试次数',
    retry_of_execution_id  BIGINT         DEFAULT NULL COMMENT '来源失败执行ID',
    dedup_key              VARCHAR(255)   DEFAULT NULL COMMENT '活动/成功/未知结果去重键',
    trigger_payload        LONGTEXT       DEFAULT NULL COMMENT '脱敏后的触发上下文',
    request_payload        LONGTEXT       DEFAULT NULL COMMENT '脱敏后的外部请求',
    response_payload       LONGTEXT       DEFAULT NULL COMMENT '脱敏后的外部响应',
    error_code             VARCHAR(100)   DEFAULT NULL COMMENT '稳定错误码',
    error_message          VARCHAR(2000)  DEFAULT NULL COMMENT '管理员可读错误原因',
    start_time             DATETIME       DEFAULT NULL COMMENT '开始执行时间',
    end_time               DATETIME       DEFAULT NULL COMMENT '结束执行时间',
    create_time            DATETIME       NOT NULL COMMENT '创建时间',
    update_time            DATETIME       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (execution_id),
    UNIQUE KEY uk_int_execution_dedup_key (dedup_key),
    UNIQUE KEY uk_int_execution_retry_of (retry_of_execution_id),
    KEY idx_int_execution_status_time (status, create_time),
    KEY idx_int_execution_task_time (task_code, create_time),
    KEY idx_int_execution_create_time (create_time, execution_id),
    KEY idx_int_execution_master_id (master_id),
    KEY idx_int_execution_business_key (business_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='集成执行记录';

CREATE TABLE IF NOT EXISTS int_execution_stage (
    stage_log_id      BIGINT         NOT NULL AUTO_INCREMENT COMMENT '阶段日志ID',
    execution_id      BIGINT         NOT NULL COMMENT '执行ID',
    sequence_no       INT            NOT NULL COMMENT '执行内顺序号',
    stage             VARCHAR(50)    NOT NULL COMMENT '阶段',
    stage_status      VARCHAR(20)    NOT NULL COMMENT 'STARTED/SUCCESS/FAILED',
    request_payload   LONGTEXT       DEFAULT NULL COMMENT '脱敏后的阶段请求',
    response_payload  LONGTEXT       DEFAULT NULL COMMENT '脱敏后的阶段响应',
    error_code        VARCHAR(100)   DEFAULT NULL COMMENT '稳定错误码',
    error_message     VARCHAR(2000)  DEFAULT NULL COMMENT '管理员可读错误原因',
    start_time        DATETIME       NOT NULL COMMENT '阶段开始时间',
    end_time          DATETIME       DEFAULT NULL COMMENT '阶段结束时间',
    create_time       DATETIME       NOT NULL COMMENT '创建时间',
    update_time       DATETIME       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (stage_log_id),
    UNIQUE KEY uk_int_stage_execution_sequence (execution_id, sequence_no),
    KEY idx_int_stage_execution (execution_id, stage_log_id),
    CONSTRAINT fk_int_stage_execution FOREIGN KEY (execution_id)
        REFERENCES int_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='集成执行阶段日志';

-- 数据交换目录
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '数据交换', 0, 4, 'integration', NULL, '', '',
       1, 0, 'M', '0', '0', '', 'connection',
       'admin', NOW(), '', NULL, '数据交换管理目录'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M'
);

SET @integration_root_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = 0 AND path = 'integration' AND menu_type = 'M'
    ORDER BY menu_id LIMIT 1
);

-- 执行日志菜单
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '执行日志', @integration_root_id, 1, 'execution', 'integration/execution/index', '', '',
       1, 0, 'C', '0', '0', 'integration:execution:list', 'list',
       'admin', NOW(), '', NULL, '集成执行日志菜单'
WHERE @integration_root_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE parent_id = @integration_root_id AND path = 'execution' AND menu_type = 'C'
  );

SET @integration_execution_menu_id := (
    SELECT menu_id FROM sys_menu
    WHERE parent_id = @integration_root_id AND path = 'execution' AND menu_type = 'C'
    ORDER BY menu_id LIMIT 1
);

-- 查看详情权限
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '执行查看', @integration_execution_menu_id, 1, '#', '', '', '',
       1, 0, 'F', '0', '0', 'integration:execution:query', '#',
       'admin', NOW(), '', NULL, ''
WHERE @integration_execution_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:execution:query');

-- 人工重试权限
INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, update_by, update_time, remark
)
SELECT '执行重试', @integration_execution_menu_id, 2, '#', '', '', '',
       1, 0, 'F', '0', '0', 'integration:execution:retry', '#',
       'admin', NOW(), '', NULL, ''
WHERE @integration_execution_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'integration:execution:retry');
