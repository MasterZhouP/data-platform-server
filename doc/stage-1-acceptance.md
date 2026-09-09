# Stage 1 本地实现与验收记录

验收日期：2026-09-04

> 2026-09-07 补验更新：开发库 `data_platform` 已完成增量脚本两次执行、结构/菜单核对及可回滚 SQL 读写与条件更新验证。下文 MySQL 未运行的描述为 2026-09-04 历史状态；最新证据见 [开发数据库验证记录](stage-1-database-verification.md)。应用启动、真实服务链路和 OA/U8 联调仍未验收，本更新不代表 Stage 1 整体环境验收完成。

## 结论

Stage 1 的代码、SQL、管理员页面和自动化验证已经完成。公共执行底座具备持久化受理、提交事务后异步执行、PENDING 耐久补扫、原子认领、阶段日志、失败分类、管理员查询和人工重试能力。

Stage 1 尚未完成开发环境数据库与外部数据库验收：本机 MySQL 服务未运行且当前进程无权启动；OA SQL Server 尚未配置测试账号；U8 测试账套不可用。因此不得把当前状态表述为开发库落库成功、OA 连通成功或 U8 联调成功。

## 已交付

- `OaToU8PushHandler`、`PushHandlerRegistry`、`PushPipelineRunner` 和执行阶段记录契约。
- `int_execution`、`int_execution_stage` 的 MyBatis 持久化与可重复执行建表/菜单脚本。
- 事务提交后异步投递、周期补扫、重复投递原子认领和陈旧 RUNNING 恢复。
- 失败原因、失败阶段、结果未知和人工可重试判定。
- 人工重试创建新执行，保留原记录和原标识，Handler 每次重新读取来源数据。
- 管理端执行列表、详情、阶段时间线和人工重试；列表权限不返回详情载荷。
- OA/U8 独立 SQL Server 配置边界，OA 只读连接探针，U8 类型化 Client 契约。
- 请求、响应和错误信息统一脱敏与长度限制。

## 自动化与构建证据

### 后端

执行 `mvn -B clean package`，8 个 Reactor 模块全部成功。

- `bl-integration`：18 个测试通过，0 失败、0 错误。
- `bl-admin`：3 个控制器测试通过，0 失败、0 错误。
- `bl-integration-3.9.2.jar` 包含执行服务、Pipeline、Handler 契约和两份 MyBatis XML。
- `bl-admin.jar` 包含管理员控制器，并内嵌 `bl-integration-3.9.2.jar`。

覆盖的关键行为包括：规范化后去重、重复认领只执行一次、遗漏 PENDING 补扫、FAILED 不自动重放、人工重试读取最新来源数据、结果未知禁止重试、列表载荷隔离、认证字段脱敏、错误长度、外部数据源默认关闭及控制器响应。

### 前端

使用 Node.js 24.19.0 和 Vite 6.4.1 执行生产构建：2464 个模块完成转换，构建成功，退出码 0。

项目默认 Node 版本无法解析当前依赖使用的语法，验收时使用 Codex 工作区提供的 Node 24 运行时直接执行项目内 Vite。`package.json` 已显式声明既有源码直接使用的 `sortablejs` 依赖。

## 审查修复

实现完成后进行了后端及 UI/SQL 两轮独立审查，并修复以下问题：

- 去重键改为基于规范化后的任务编号和主记录 ID 计算。
- 错误信息按数据库 2000 字符上限保存，避免失败更新再次失败。
- 扩展 `access_token`、`clientSecret`、Basic/Bearer Authorization 等认证字段脱敏。
- 列表 SQL 与服务层同时清除触发、请求、响应和去重键详情。
- masterId、businessKey 使用精确筛选，并增加创建时间索引。
- 重试成功后不越权自动请求详情。
- 应用重启恢复陈旧 RUNNING 时，同步把未结束的阶段日志置为 FAILED。
- 已存在的 businessKey 在执行完成和人工重试中保持不变。

## 数据库待验收项

具体原因和操作清单见 `doc/stage-1-database-verification.md`。环境恢复后需：

1. 启动开发 MySQL，连续执行 Stage 1 SQL 两次并核对表、索引、外键和菜单。
2. 使用应用账号验证执行记录及阶段日志的插入、查询与条件更新。
3. 配置 OA 只读测试账号，执行 `SELECT 1` 连通检查。
4. U8 测试账套可用后再验证连接与真实 Client；此项也将服务于 Stage 2 联调。

## 阶段边界

Stage 1 没有迁移真实 DEE 任务，没有包含“委外入库单-推单-new”的 OA SQL、Payload、U8 endpoint、A8 插件或真实 U8 调用。上述内容仍属于 Stage 2 及后续阶段。
