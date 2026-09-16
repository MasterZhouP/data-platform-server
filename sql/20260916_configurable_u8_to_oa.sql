-- Configurable U8 -> OA task template.
-- Prerequisites: 20260912_u8_to_oa_sales_outbound.sql and 20260914_configurable_task_platform.sql.
-- The template is deliberately disabled and has no Quartz job. Copy it in Task Center,
-- review the initial cursor and SQL, validate, then publish the copy to create its paused/active job.

SET @u8_to_oa_template_code := 'U8_TO_OA_SALES_OUTBOUND_TEMPLATE';

SET @u8_to_oa_config := JSON_OBJECT(
  'constants', JSON_OBJECT(),
  'dataSteps', JSON_ARRAY(
    JSON_OBJECT(
      'code', 'header', 'order', 1, 'datasourceKey', 'u8', 'cardinality', 'ONE',
      'parameterBindings', JSON_OBJECT('documentNo', 'trigger.masterId'),
      'sql', 'SELECT a.ccode AS document_no, a.cMaker AS maker, a.dDate AS outbound_date,
                     a.cWhCode AS warehouse_code, w.cWhName AS warehouse_name,
                     a.cCusCode AS customer_code, c.cCusName AS customer_name,
                     a.cDepCode AS department_code, d.cDepName AS department_name,
                     a.cMemo AS remark, CAST(a.id AS VARCHAR(100)) AS u8_id,
                     a.cBusCode AS business_no, CONCAT(''销售出库单-'', a.ccode) AS subject
              FROM rdrecord32 a
              LEFT JOIN Warehouse w ON a.cWhCode = w.cWhCode
              LEFT JOIN Customer c ON a.cCusCode = c.cCusCode
              LEFT JOIN Department d ON a.cDepCode = d.cDepCode
              WHERE a.ccode = :documentNo'
    ),
    JSON_OBJECT(
      'code', 'warehouseManager', 'order', 2, 'datasourceKey', 'oa', 'cardinality', 'SCALAR',
      'parameterBindings', JSON_OBJECT('warehouseCode', 'data.header.warehouse_code'),
      'sql', 'SELECT field0029 FROM ckgly WHERE field0031 = :warehouseCode'
    ),
    JSON_OBJECT(
      'code', 'lines', 'order', 3, 'datasourceKey', 'u8', 'cardinality', 'LIST',
      'parameterBindings', JSON_OBJECT('documentNo', 'trigger.masterId'),
      'sql', 'SELECT a.cInvCode AS [存货编码], b.cInvName AS [存货名称],
                     b.cInvStd AS [规格型号], a.iQuantity AS [数量],
                     CAST(a.autoid AS VARCHAR(100)) AS [子ID],
                     CAST(a.id AS VARCHAR(100)) AS [主ID],
                     mainUnit.cComUnitName AS [主单位], auxUnit.cComUnitName AS [辅单位],
                     a.iNum AS [件数], a.cBatch AS [批号],
                     a.dMadeDate AS [生产日期], a.dVDate AS [到期日期]
              FROM rdrecords32 a
              LEFT JOIN Inventory b ON a.cInvCode = b.cInvCode
              LEFT JOIN ComputationUnit mainUnit ON b.cComUnitCode = mainUnit.cComUnitCode
              LEFT JOIN ComputationUnit auxUnit ON a.cAssUnit = auxUnit.cComUnitCode
              INNER JOIN rdrecord32 h ON a.id = h.id
              WHERE h.ccode = :documentNo
              ORDER BY a.autoid'
    )
  ),
  'oa', JSON_OBJECT(
    'u8IdVariable', 'data.header.u8_id',
    'payloadTemplate', JSON_OBJECT(
      'appName', 'collaboration',
      'data', JSON_OBJECT(
        'templateCode', 'XSCKDCS', 'draft', '0', 'subject', '{{data.header.subject}}',
        'data', JSON_OBJECT(
          'formmain_0688', JSON_OBJECT(
            '单据编号', '{{data.header.document_no}}', '制单人', '{{data.header.maker}}',
            '出库日期', '{{data.header.outbound_date}}', '仓库', '{{data.header.warehouse_name}}',
            '客户名称', '{{data.header.customer_name}}', '客户编码', '{{data.header.customer_code}}',
            '部门名称', '{{data.header.department_name}}', '仓库管理员', '{{data.warehouseManager}}',
            '仓库编码', '{{data.header.warehouse_code}}', '部门编码', '{{data.header.department_code}}',
            '备注', '{{data.header.remark}}', '主表主ID', '{{data.header.u8_id}}',
            '业务单号', '{{data.header.business_no}}'
          ),
          'formson_0689', '{{data.lines}}'
        )
      )
    )
  ),
  'sync', JSON_OBJECT(
    'datasourceKey', 'u8',
    'upperBoundSql', 'SELECT CURRENT_TIMESTAMP AS db_time',
    'createSql', 'SELECT ccode AS document_no, dnmaketime AS changed_at,
                         CAST(NULL AS VARCHAR(100)) AS version_token
                  FROM rdrecord32
                  WHERE dnmaketime > :fromTime AND dnmaketime <= :toTime
                    AND dVeriDate IS NULL AND ddate >= ''2023-09-21''
                  ORDER BY dnmaketime, ccode',
    'updateSql', 'SELECT ccode AS document_no, dnmodifytime AS changed_at,
                         CONVERT(VARCHAR(100), ufts, 1) AS version_token
                  FROM rdrecord32
                  WHERE dnmodifytime > :fromTime AND dnmodifytime <= :toTime
                    AND dVeriDate IS NULL AND ddate >= ''2023-09-21''
                  ORDER BY dnmodifytime, ccode',
    'deleteSql', 'SELECT djbh AS document_no, sj AS changed_at,
                         CAST(NULL AS VARCHAR(100)) AS version_token
                  FROM rdrecord32_delete_log
                  WHERE sj > :fromTime AND sj <= :toTime
                  ORDER BY sj, djbh',
    'initialCursor', '2026-09-16T00:00:00',
    'overlapMinutes', 1440,
    'cronExpression', '0 0/5 * * * ?'
  )
);

INSERT INTO int_integration_task (
  task_code, task_name, task_type, enabled, active_revision_id, draft_revision_id,
  config_version, create_time, update_time
)
SELECT @u8_to_oa_template_code, '销售出库推OA模板', 'U8_TO_OA', 0, NULL, NULL, 0, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM int_integration_task WHERE task_code = @u8_to_oa_template_code
);

INSERT INTO int_integration_task_revision (
  task_code, revision_no, status, config_json, config_checksum,
  dependency_revisions_json, validation_json, change_note, create_time, update_time
)
SELECT @u8_to_oa_template_code, 1, 'PUBLISHED', CAST(@u8_to_oa_config AS CHAR),
       SHA2(CAST(@u8_to_oa_config AS CHAR), 256),
       JSON_OBJECT('datasource:u8', 'registered-readonly',
                   'datasource:oa', 'registered-readonly', 'oaGateway', 'oa-default'),
       JSON_OBJECT('valid', TRUE, 'validatedBy', 'migration'),
       '销售出库代码任务迁移为可复制配置模板', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM int_integration_task_revision WHERE task_code = @u8_to_oa_template_code
);

UPDATE int_integration_task task
INNER JOIN int_integration_task_revision revision
        ON revision.task_code = task.task_code AND revision.revision_no = 1
SET task.active_revision_id = revision.revision_id,
    task.draft_revision_id = NULL,
    task.config_version = 1,
    task.update_time = NOW()
WHERE task.task_code = @u8_to_oa_template_code
  AND task.active_revision_id IS NULL;
