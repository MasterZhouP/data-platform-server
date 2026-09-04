# Stage 1 开发数据库验证记录

验证日期：2026-09-04

## 交付脚本

- `sql/20260904_stage1_integration.sql`
- 脚本只使用 `CREATE TABLE IF NOT EXISTS` 和带 `NOT EXISTS` 条件的菜单插入。
- 不包含 `DROP`、`TRUNCATE`、`DELETE` 或既有表结构修改。
- 目标对象为 `int_execution`、`int_execution_stage` 和三项权限 `integration:execution:list/query/retry`。

## 当前环境执行结果

本次已尝试使用 `application-druid.yml` 中现有的开发 MySQL 主库配置执行脚本。首次连接在 SQL 执行前因本机 MySQL 服务未运行而失败；Windows 服务 `MySQL97` 状态为 `Stopped`。当前进程没有启动该系统服务的权限，因此本次没有取得表、索引和菜单已经实际落库的证据。

数据库脚本尚未标记为开发库验证通过。待有权限的操作者启动开发 MySQL 后，需要完成以下检查：

1. 连续执行脚本两次，第二次无重复对象或重复菜单错误。
2. `int_execution` 为 22 个字段，包含 `dedup_key`、`retry_of_execution_id` 两个唯一索引。
3. `int_execution_stage` 为 13 个字段，包含 `(execution_id, sequence_no)` 唯一索引和执行 ID 外键。
4. `sys_menu` 中列表、查看、重试三个权限各一条。
5. 使用应用相同账号完成一条执行记录和阶段日志的插入、查询与条件状态更新。

## 外部数据库

- OA SQL Server：已交付默认关闭的独立只读数据源和 `SELECT 1` 探针；当前尚未配置测试账号，因此未做真实连通验证。
- U8 SQL Server：已交付默认关闭的独立数据源配置边界；测试账套不可用，未做真实连通验证。
