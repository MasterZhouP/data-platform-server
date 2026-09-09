# Stage 1 开发数据库验证记录

最近验证日期：2026-09-07（保留 2026-09-04 首次检查记录）

## 2026-09-07 开发库验证结果

用户确认本机 MySQL 为项目开发库、库名为 `data_platform`，并授权执行增量建表和菜单脚本。本次使用应用主数据源配置的同一账号连接本机 `data_platform`；执行前确认 `sys_menu` 及脚本所需字段已存在，两张集成表尚不存在。未记录连接凭据。

- `MySQL97` 当前为 Running，无需启动服务。
- `sql/20260904_stage1_integration.sql` 连续执行两次，均成功；第二次执行前后表结构、唯一索引、外键和菜单快照一致。
- `int_execution` 为 22 个字段，`int_execution_stage` 为 13 个字段；去重、重试来源、阶段序号唯一索引及执行 ID 外键符合脚本定义。
- 新增“数据交换”“执行日志”“执行查看”“执行重试”四条菜单/权限记录，ID 为 2000～2003；列表、查看、重试权限各一条。
- 使用同一应用账号，在事务内插入并关联执行记录和阶段日志，读取成功；第一次 PENDING → RUNNING 条件更新影响 1 行，第二次重复认领影响 0 行；RUNNING → FAILED 更新及失败结果读取成功。
- 验证事务已回滚，测试执行记录和阶段日志均未保留，验证前后两表记录数一致。回滚的自增插入可能消耗自增编号，不要求编号连续。

证据见 [2026-09-07 数据库验证结果](stage-1-database-evidence-20260907.json)。脚本 SHA256：`3A6ABFDFA04DDDD9EA3AF7618E5E57D35FB732160F0C836760CA90F4897787B8`。

结论仅覆盖开发 MySQL 增量落库、重复执行、数据库结构和直接 SQL 读写/条件更新。没有启动应用，没有通过实际 MyBatis 服务、异步事件或浏览器执行端到端验收，也没有验证并发连接认领。OA/U8 测试环境仍在准备，连接与业务联调均未通过；Stage 1 整体环境验收仍未闭合。

## 交付脚本

- `sql/20260904_stage1_integration.sql`
- 脚本只使用 `CREATE TABLE IF NOT EXISTS` 和带 `NOT EXISTS` 条件的菜单插入。
- 不包含 `DROP`、`TRUNCATE`、`DELETE` 或既有表结构修改。
- 目标对象为 `int_execution`、`int_execution_stage` 和三项权限 `integration:execution:list/query/retry`。

## 2026-09-04 首次检查记录（历史）

本次已尝试使用 `application-druid.yml` 中现有的开发 MySQL 主库配置执行脚本。首次连接在 SQL 执行前因本机 MySQL 服务未运行而失败；Windows 服务 `MySQL97` 状态为 `Stopped`。当前进程没有启动该系统服务的权限，因此本次没有取得表、索引和菜单已经实际落库的证据。

当时数据库脚本尚未标记为开发库验证通过，留下以下检查项（2026-09-07 的完成证据见本文开头）：

1. 连续执行脚本两次，第二次无重复对象或重复菜单错误。
2. `int_execution` 为 22 个字段，包含 `dedup_key`、`retry_of_execution_id` 两个唯一索引。
3. `int_execution_stage` 为 13 个字段，包含 `(execution_id, sequence_no)` 唯一索引和执行 ID 外键。
4. `sys_menu` 中列表、查看、重试三个权限各一条。
5. 使用应用相同账号完成一条执行记录和阶段日志的插入、查询与条件状态更新。

## 外部数据库

- OA SQL Server：已交付默认关闭的独立只读数据源和 `SELECT 1` 探针；当前尚未配置测试账号，因此未做真实连通验证。
- U8 SQL Server：已交付默认关闭的独立数据源配置边界；测试账套不可用，未做真实连通验证。
