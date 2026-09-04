# Stage 1 公共执行底座设计

## 1. 目标与验收口径

Stage 1 建立 OA→U8 推送任务的公共执行底座，并提供管理员执行日志页面与手动重试能力。它不迁移任何真实 DEE 业务任务；“委外入库单-推单-new”的 SQL、Payload、U8 endpoint 和后处理属于 Stage 2。

Stage 1 完成后，测试专用 Handler 应能证明以下链路：

```text
受理命令 → MySQL 持久化 PENDING → 提交事务后异步执行
→ RUNNING → 分阶段记录 → SUCCESS / FAILED
→ 管理员查询列表与详情 → 对可重试失败发起手动重试
```

后端自动化测试使用内存仓储与替身 Handler/U8 Client，不依赖外部环境。开发环境 MySQL 用于真实执行记录；OA SQL Server 提供可选的只读数据源与 `SELECT 1` 连通性检查。U8 测试账套当前不可用，因此只交付配置边界和替身测试，不声称真实 U8 连通或接口联调通过。

## 2. 已确认需求

- OA 普通用户不等待 U8 处理结果，也不接收成功/失败提示。
- 平台持久化受理后异步执行，平台失败不回滚 OA 原事务。
- 失败由管理员在日志页面查看，需要明确失败原因和失败阶段。
- 管理员可以手动重试；Stage 1 不做自动重试或失败通知。
- 重试不得修改 `taskCode`、`masterId`、`businessKey`，并重新读取 OA 当前最新数据、重新生成请求。
- 原失败记录、原请求/响应和阶段日志不可覆盖；新执行通过 `retryOfExecutionId` 关联原记录。
- 结果未知、已产生外部副作用或被判定不可安全重放的失败不允许直接点击重试。
- MySQL 开发账号允许建表、建索引和读写执行记录；OA 测试库可取得只读连接；U8 测试账套暂不可用。

## 3. 范围

### 3.1 包含

- 任务注册表、任务 Handler 契约和 PushPipeline。
- 执行状态、阶段、错误分类、请求/响应摘要和人工重试关系。
- MyBatis 持久化、MySQL DDL 与菜单权限 SQL。
- 事务提交后异步分发，使用集成模块专用线程池。
- 管理员列表、详情、阶段日志和手动重试 API。
- Vue 管理页面，复用若依权限、分页、搜索和操作日志。
- 敏感键脱敏，至少覆盖 password、token、secret、appKey、authorization 等认证信息。
- OA/U8 外部数据源的独立配置边界；OA 支持只读连通性检查，U8 默认禁用。
- 成功、失败、不可重试、人工重试读取最新数据、重复并发受理等自动化测试。

### 3.2 不包含

- 任何真实业务 Handler、OA 业务 SQL、U8 Payload 或 U8 业务 endpoint。
- A8 插件工程及插件投递失败补送。
- U8 token/tradeId 的真实获取和 U8 环境联调。
- ReferenceEngine、SyncPipeline、自动重试、消息通知、日志自动清理和统计大屏。
- 在线编辑 SQL、任务编排器、消息队列或分布式事务。

## 4. 架构与职责

### 4.1 受理与异步执行

`IntegrationExecutionService.accept()` 校验 Handler 是否存在，创建 `PENDING/RECEIVED` 执行记录并发布受理事件。事件监听器只在 MySQL 事务提交后将执行 ID 投递到 `integrationTaskExecutor`。异步 Runner 重新加载记录，以 `UPDATE ... WHERE status = 'PENDING'` 原子转换为 `RUNNING`，再调用 PushPipeline。

MySQL 中的 `PENDING` 记录同时承担耐久待执行队列的职责。提交后即时投递用于降低延迟；应用启动及固定周期只补扫遗漏的 `PENDING` 并再次投递执行 ID。重复投递由原子认领消除。补扫是恢复同一次已受理执行，不会自动重放 `FAILED`，因此不构成 Stage 1 禁止的自动重试。线程池拒绝时记录仍保留为 `PENDING`，由后续补扫恢复。

OA 查询、U8 HTTP 调用期间不持有 MySQL 长事务。受理、认领、追加阶段以及写最终状态分别使用短事务。应用重启时，超过阈值的陈旧 `RUNNING` 转为 `FAILED` 而不自动执行；如果尚未进入 U8 发送阶段可标记人工可重试，已经进入 `U8_REQUEST_SENDING` 的记录按结果未知处理并禁止直接重试。

Stage 1 不开放匿名 OA 插件入口。管理 API 使用若依登录态和权限；后续插件入口将复用 Application Service，并增加独立的服务间认证适配，避免现在猜测 A8 协议。

### 4.2 核心接口

```java
public interface OaToU8PushHandler {
    String taskCode();
    PushResult execute(PushExecutionContext context, ExecutionStageRecorder recorder);
}

public record TriggerCommand(
    String taskCode,
    String masterId,
    String formId,
    String summaryId
) {}
```

触发方不提交也不决定 `businessKey`。平台先使用任务定义提供的去重规则计算 `dedupKey`；Stage 1 测试任务使用 `taskCode + masterId`。Stage 2 Handler 从 OA 读取最新数据后再计算并回填真实 `businessKey`。Handler 每次执行时依据稳定标识重新访问 OA，不接收首次执行的数据快照。`ExecutionStageRecorder` 负责追加阶段，不允许 Handler 直接更新主执行状态。

### 4.3 状态与阶段

主状态只使用 `PENDING`、`RUNNING`、`SUCCESS`、`FAILED`。阶段至少支持：

- `RECEIVED`
- `SOURCE_LOADING`
- `SOURCE_LOADED`
- `TOKEN_ACQUIRING`
- `TOKEN_ACQUIRED`
- `TRADE_ID_ACQUIRING`
- `TRADE_ID_ACQUIRED`
- `U8_REQUEST_SENDING`
- `U8_REQUEST_SENT`
- `U8_CONFIRMED`
- `OA_WRITTEN_BACK`
- `COMPLETED`

任务不需要的阶段可以跳过。主记录保存当前阶段和最终错误摘要；阶段表保存完整时间线。

### 4.4 失败与重试

错误记录包含 `errorCode`、`errorMessage`、`retryable` 和 `resultUnknown`。异常默认不可重试；只有 Handler 或公共 Client 明确判定为安全的失败才设置 `retryable=true`。

手动重试规则：

1. 原执行必须为 `FAILED`、`retryable=true`、`resultUnknown=false`。
2. 新执行复制原 `taskCode/masterId/businessKey/formId/summaryId`，`retryCount + 1`，并记录 `retryOfExecutionId`；执行时仍依据稳定标识重新查询 OA 最新数据。
3. 原记录保持不变，新执行从 `RECEIVED` 开始完整记录。
4. 唯一的非空 `dedupKey` 防止同一任务/业务标识同时处于 PENDING、RUNNING、SUCCESS 或结果未知状态；仅明确未产生外部副作用且允许人工重试的失败释放原记录的锁。重试事务锁定原记录、复核规则并由新执行占用同一去重键；并发双击由唯一约束转换为明确冲突响应。
5. Handler 每次执行重新取 OA 最新数据。保存的历史请求/响应只用于审计，不作为重试输入。

## 5. 数据模型

### 5.1 `int_execution`

关键字段：

- `execution_id`：MySQL 自增 ID；接口一律按 64 位整数处理。
- `task_code`、`master_id`、`business_key`、`form_id`、`summary_id`。
- `status`、`stage`、`retryable`、`result_unknown`。
- `retry_count`、`retry_of_execution_id`。
- `dedup_key`：可空唯一键；PENDING、RUNNING、SUCCESS、结果未知记录保留，明确可安全重试的失败释放后由新执行占用。
- `trigger_payload`：受理时稳定上下文的脱敏审计副本，不作为重试数据源。
- `request_payload`、`response_payload`：脱敏后的 JSON/文本。
- `error_code`、`error_message`。
- `start_time`、`end_time`、`create_time`、`update_time`。

列表索引覆盖状态/创建时间、任务/业务标识、masterId 和重试关系。

### 5.2 `int_execution_stage`

字段包括阶段日志 ID、执行 ID、阶段、阶段结果、脱敏请求/响应、错误摘要、开始/结束时间。阶段结果使用 `STARTED`、`SUCCESS`、`FAILED`。

Stage 1 不自动删除执行记录。保留周期和归档策略在真实业务量与合规要求明确后另行决定。

## 6. 管理 API 与页面

管理 API：

- `GET /integration/execution/list`
- `GET /integration/execution/{executionId}`
- `POST /integration/execution/{executionId}/retry`

权限：

- `integration:execution:list`
- `integration:execution:query`
- `integration:execution:retry`

页面筛选 taskCode、masterId、businessKey、状态和时间范围；默认按创建时间倒序。列表展示当前阶段和错误摘要。详情一次返回执行记录、阶段时间线、后端计算的 `retryable` 与 `retryBlockReason`。只在后端判定可重试且用户有权限时启用重试按钮；前端确认框不允许编辑标识。

## 7. 数据源配置

MySQL 继续使用若依主数据源。OA/U8 使用 `integration.datasource.oa` 和 `integration.datasource.u8` 独立配置，真实密码只允许来自环境变量或未提交的本地配置。两个外部数据源默认禁用，缺少配置时应用仍可启动。

OA 启用后只提供 `SELECT 1` 连通性检查和后续只读访问底座。Stage 1 不提交 OA 真实账号、不执行 OA 业务 SQL。U8 配置结构与 OA 对称，但本阶段保持禁用并明确标记未验证。

## 8. 安全与可观察性

- 请求、响应和异常进入数据库前统一脱敏；认证值不得落库。
- 错误信息限制长度，保留稳定错误码和根因摘要，不将完整堆栈返回前端。
- 管理接口受若依权限控制；重试操作写入现有操作日志。
- 普通 OA 用户不接收执行结果；Stage 1 不发送通知。
- 线程池有明确大小、队列容量和线程名前缀；队列拒绝时，执行记录保持 `PENDING`，由耐久补扫再次投递。

## 9. 测试与验收

- Core 单元测试覆盖注册、状态机、阶段记录、脱敏和重试规则。
- Application 测试使用同步 Executor、内存仓储和测试 Handler，验证完整成功/失败链路、重复认领、补扫遗漏 PENDING、FAILED 不被补扫、结果未知禁止重试、双击重试和重试读取 OA 最新数据。
- MyBatis XML 与 Spring 上下文至少通过 Maven 测试/构建加载验证。
- Controller 测试验证权限后的列表、详情和重试响应。
- 前端执行生产构建；当前项目没有前端测试框架，不为单页引入新测试体系。
- 执行 SQL 脚本后，用开发 MySQL 验证表/索引；获得 OA 本地安全配置后执行只读连通性检查。
- U8 真实连通和接口联调明确留待测试账套可用后完成。

## 10. 本阶段完成条件

- 上述自动化测试和后端完整构建通过，前端生产构建通过。
- 数据库脚本可重复审查，开发 MySQL 中表和索引与脚本一致。
- 日志页面可查询失败原因、查看阶段并发起符合规则的手动重试。
- 代码、配置、SQL 和日志中没有真实密码、token 或 appKey。
- 基线文档登记 Stage 1 实际完成项与未验证项；Stage 2 仍保持未实施。
