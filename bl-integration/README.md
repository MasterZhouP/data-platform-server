# 集成平台模块

`bl-integration` 承载 data-platform 的公共集成能力。Stage 0 已完成模块分层；Stage 1 已实现公共执行底座，尚未迁移真实 OA→U8 业务任务。

## Stage 1 已实现

- `task`：`TriggerCommand`、任务 Handler 契约、任务注册表和失败分类。
- `pipeline.push`：执行 Runner 和追加式阶段记录。
- `execution`：PENDING/RUNNING/SUCCESS/FAILED 状态、MyBatis 持久化、执行详情、人工重试和敏感数据脱敏。
- 耐久异步执行：受理事务提交后即时派发，周期补扫遗漏的 PENDING，条件更新保证同一执行只被一个 Runner 认领。
- 安全恢复：FAILED 不自动执行；启动时把陈旧 RUNNING 转为 FAILED，已进入 U8 发送阶段的记录标为结果未知并禁止直接重试。
- `datasource`：OA/U8 独立 SQL Server 数据源，默认关闭，不加入若依 MySQL 动态数据源。
- `client`：OA `SELECT 1` 连通探针和 U8 类型化 Client 边界。
- 管理端入口位于 `bl-admin`：列表、详情和人工重试 API；前端页面位于 `data-platform-ui/src/views/integration/execution`。

## 执行语义

```text
受理命令
  → MySQL 短事务写 PENDING + RECEIVED
  → 提交后派发执行 ID
  → Runner 条件更新 PENDING → RUNNING
  → Handler 每次依据稳定标识重新读取 OA 最新数据
  → 分阶段短事务记录
  → SUCCESS 或 FAILED
```

MySQL 的 PENDING 记录是耐久待执行队列。补扫只恢复同一次已受理执行，不会重放 FAILED，所以不属于自动重试。人工重试只接收原执行 ID，复制固定标识并创建新执行；原记录和历史请求、响应、阶段日志保持不变。

## 配置

外部数据源通过以下环境变量启用，仓库不保存实际凭据：

- `INTEGRATION_OA_ENABLED`、`INTEGRATION_OA_URL`、`INTEGRATION_OA_USERNAME`、`INTEGRATION_OA_PASSWORD`
- `INTEGRATION_U8_ENABLED`、`INTEGRATION_U8_URL`、`INTEGRATION_U8_USERNAME`、`INTEGRATION_U8_PASSWORD`

OA 启用后应使用只读账号。U8 在取得测试账套并确认真实接口前保持关闭。

## Stage 1 边界

本阶段没有真实业务 Handler、OA 业务 SQL、U8 token/tradeId、U8 endpoint 或 A8 插件入口。测试使用替身 Handler 验证成功、失败、结果未知、遗漏 PENDING 恢复和读取最新 OA 数据的重试路径。

数据库增量脚本为 `sql/20260904_stage1_integration.sql`。设计、执行计划与验收材料位于 `doc/`。
