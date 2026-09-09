# 首条物料参照：实现与环境交接

日期：2026-09-07。范围依据：原开发基线 V1.2 与 [参照接口 V1.0](api/reference-v1/README.md)。本次实现平台业务代码，OA 插件仍由同事开发。

## 已落地的代码

- `bl-integration/reference`：通用 SQL Server 查询引擎、任务/元数据目录、参数与筛选校验、同步执行日志。每页返回 SQL 全部字段，动态字段不限定为物料 14 列。
- `bl-admin`：四个 V1 服务接口、独立服务凭据认证和任务授权、若依管理员配置/字段检查/预览接口。
- `data-platform-ui/src/views/integration/reference`：任务列表、配置编辑、同结构任务新增、完整字段/类型/允许筛选配置、默认显示与排序、字段自动检查、分页查询预览。
- 物料任务 `U8_MATERIAL_REFERENCE` / 结果集 `tou`：受控 SQL 来自用户提供的原 SQL，保留原关联、分组、换算率及停用物料过滤。旧 `${whereString}` 已从执行 SQL 移除，由结构化参数化筛选替代。
- 保留前导零、空值、小数精度、同编码不同换算率的结果；默认生产部门使用正确字段 `mrscbm`。

参照同步返回，不进入 PushPipeline 的受理、调度或人工重放；复用既有执行表和阶段表。查询结果不回调 OA，插件读取整行后自行按配置回填。

## 数据库配置入口

配置位于 `bl-admin/src/main/resources/application.yml`；所有外部测试连接默认关闭。本次没有填入真实 U8/OA 账号，没有建立真实连接。

| 配置 | 默认值 / 含义 |
| --- | --- |
| INTEGRATION_U8_ENABLED | false；开启实际 U8 SQL Server 数据源 |
| INTEGRATION_U8_URL | 空；由管理员填写完整测试账套 JDBC 地址 |
| INTEGRATION_U8_USERNAME | 空；测试只读账号 |
| INTEGRATION_U8_PASSWORD | 空；通过环境或私有配置注入 |
| INTEGRATION_REFERENCE_API_ENABLED | false；开启预置 OA 插件服务身份 |
| INTEGRATION_REFERENCE_API_KEY | 空；单独生成至少 32 字符随机凭据，仅 OA 插件服务端持有 |

`integration.reference.api.clients` 支持多个服务身份及各自 `task-codes` 授权列表。新任务还需明确授予插件凭据权限，不能仅凭知道任务编码访问。

U8 数据源开启前，平台可保存配置；字段自动检查和查询会明确提示不可用。生产代码中没有“数据库未配置就返回样例”的分支，模拟数据仅位于测试资源和接口文档。

## 平台 MySQL 增量

脚本：[20260907_reference.sql](../sql/20260907_reference.sql)。目标为已确认的开发库 `data_platform`，前置脚本为 Stage 1 增量。

脚本包含任务配置表、默认停用的物料任务、参照任务菜单、查询/编辑权限，以及执行记录对未保存表单空 masterId 和最长 128 字符上下文 ID 的支持。重复执行不覆盖已有任务配置。**本轮仅交付脚本，没有实际执行该 MySQL 增量。**

执行后，菜单组件为 `integration/reference/index`；权限为 `integration:reference:list`、`integration:reference:query`、`integration:reference:edit`。普通用户还需由管理员分配角色权限。

初始元数据提供 14 个字段用于离线配置，但其中 JDBC 类型/可空性仍待真实 U8 核验，因此初始任务停用。环境就绪后执行“从已保存的 SQL 检查字段”，核对后保存并启用。该操作返回候选元数据，不会直接覆盖管理员配置。

## 管理接口

管理接口使用若依登录权限，与插件服务 API 完全分开。

| 路径 | 操作 |
| --- | --- |
| GET /integration/reference/options | 当前数据源启用状态、已部署的受控 SQL 资源 |
| GET /integration/reference/tasks | 任务配置列表 |
| GET /integration/reference/tasks/{taskCode} | 读取任务配置，编辑或查询权限皆可 |
| POST /integration/reference/tasks | 新增任务配置 |
| PUT /integration/reference/tasks/{taskCode} | 按当前 metadataVersion 保存，服务器生成新版本 |
| POST /integration/reference/tasks/{taskCode}/inspect | 读取实际 SQL 列元数据，返回候选配置 |
| POST /integration/reference/tasks/{taskCode}/preview | 使用 V1 查询体进行预览，同样记录查询执行 |

管理端沿用 AjaxResult 响应；业务失败使用可被现有前端保留错误消息的 code=500，并附 `referenceCode` 与 `httpStatus`。插件 API 则严格保留 V1 的 HTTP 状态和业务错误码，不使用管理端响应结构。

## 下一次真实联调

1. 将新增脚本应用至开发 MySQL，启动后台及前端，给测试用户分配参照菜单权限。
2. 提供 U8 测试库连接，开启数据源，检查元数据后启用物料任务；先在平台预览检验编码/名称/规格搜索和完整返回。
3. 启用 OA 插件服务身份，将地址、凭据及任务授权交给同事；按 V1 接口完成连接检测、目录、元数据、查询。
4. 同事在 OA 中绑定参照控件和目标字段，验证点击、选择、当前明细行回填，整个过程不使用 DEE。

本版每条任务配置一个平面结果集、单选，满足首条物料参照；其他同结构参照可以新增任务/SQL 资源与元数据配置，不复制引擎代码。不同 SQL 资源需要按受控资源流程部署，未引入任意在线 SQL 编辑器。

## 验证记录

- 后端 `mvn -B clean package`：8 个构建模块全部成功，77 个测试全部通过，无失败、错误或跳过；包含原 Stage 1 回归。构建用时 41.634 秒。
- 其中查询引擎 31 个测试，覆盖全部字段、精度/空值、同编码多行、结构化筛选、排序分页、命名参数、类型探测、版本冲突及整体超时。
- 前端 Node 24：5 个模型测试通过；生产构建成功，2468 个模块，用时 21.68 秒。
- 独立代码复核发现并修复字符串区间比较、中文字段别名、管理员错误提示及权限衔接问题。
- [机器可读验证记录](reference-v1-evidence.json)。SQL 测试使用 H2/JDBC 替身；仅测试适配层将 COUNT_BIG 转换为 COUNT，正式 SQL 仍使用 SQL Server 语法。未把模拟查询算作真实业务验收。

真实 MySQL 增量、真实 SQL Server 版本兼容性/性能、完整应用启动、浏览器交互及 OA 插件联调尚未验证。代码保留在前后端的 `codex/reference-v1` 开发分支；本轮未提交或部署。
