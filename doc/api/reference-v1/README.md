# data-platform 与 OA 插件接口文档 V1.0：参照任务

版本：1.0.0 · 日期：2026-09-07 · 对接双方：数据交换平台开发 / OA 插件开发。

**交付状态：平台端参照业务源码已实现并通过本地测试，尚未部署或完成真实 OA/U8 联调；同事可据此同步开发 OA 插件。** 本文确定新平台的协议，不是致远官方接口。配套 [OpenAPI](openapi.json)、[请求示例](requests.http)、[样例目录](examples/) 与本文一同交付。示例均为模拟数据，不含环境密码。

交接阅读顺序：先读第 2 节分工、第 4 节接口、第 6 节插件流程，再按第 8～9 节进行开发与验收。接口文件可导入支持 OpenAPI 3.0 的 Apifox/Swagger 等工具；本次未在这些工具中实际进行导入测试。

## 1. 范围与基线

遵循《00-项目总方案与开发基线》V1.2 的 3.2、4.5、5.1、5.2.2、6～9 节。本次交付参照任务从配置到查询、选择、回填所需的平台业务接口。推单、审核、定时同步仍各归原有引擎，本版不定义它们的协议。

- 数据交换平台负责 `ReferenceEngine`：任务配置、数据源、受控 SQL、完整字段元数据、参数化筛选、分页、执行记录。
- 同事负责独立 OA 插件：在客户 OA 中注册扩展/服务入口，配置平台连接，绑定任务与表单控件，按元数据配置显示、筛选和回填。
- 平台与插件通过 HTTP 对接。平台不依赖 OA 私有 SDK；插件不依赖平台 Spring Boot 3 或 `bl-integration`。
- **整个运行链路不调用 DEE，不依赖 DEE 的任务目录、元数据、服务注册、筛选拼接或查询能力。** 旧 SQL、截图和导出包只用于核对迁移行为。

首条任务为“物料参照”，新平台任务编码确定为 `U8_MATERIAL_REFERENCE`，结果集编码保留 `tou`。任务编码是本协议的新配置约定，不是从旧 DEE 空白任务编号推导出来的。

本文的路径、认证头、分页限制和错误码是为实现上述基线补齐的开发约定。平台前台应支持配置任务元数据；OA 目标字段映射在插件的前台配置中维护。六个显示列、三个常用筛选项只是物料任务的初始配置，不能写死到通用引擎或通用插件。

## 2. 分工和连接闭环

```text
配置：平台发布参照任务 → 插件读取任务/元数据 → OA 管理员绑定控件及字段
运行：OA 点击字段 → 插件服务端 → 平台 ReferenceEngine → U8 SQL Server
返回：完整字段的当前页数据 → 插件弹窗 → 选择整行 → 回填当前 OA 明细行
```

| 工作 | 平台开发 | OA 插件开发 |
| --- | --- | --- |
| 连接 | 提供环境地址、服务凭据、任务授权 | 服务端保存连接配置；校验 OA 登录、表单和字段权限 |
| 任务 | 配置、发布、启停任务和结果元数据 | 用任务编码绑定 OA 入口；可读取任务列表辅助选择 |
| 数据 | 实时查 U8，保持原业务 SQL 语义，返回全部字段 | 展示、筛选交互、选中整行，不连接 U8、不计算换算率 |
| 映射 | 输出完整字段名、名称、类型和能力 | 提供配置界面，将结果字段映射到实际 OA 字段 |
| 日志 | 查询执行记录、阶段耗时、错误及关联标识 | HTTP 投递、插件交互和回填错误记录，保留相同关联标识 |
| 部署 | 独立部署平台及数据库连接 | 按客户 A8 10.0 SP1 实际补丁/SDK 打包注册插件 |

“绑定到 OA 服务商”是 OA 插件内部的注册和配置工作：可展示为“BL数据交换平台”入口，再将 `taskCode + resultSetCode` 绑定至参照控件。平台通过以下接口提供任务和数据，**不代替 OA 注册插件，也不存在平台调用 OA 的“注册服务商”接口**。实际 OA 扩展入口、配置持久化和字段回填 API，由同事在客户 SDK 中验证；不能直接复用 DEE 专属接口页签并假定修改 URL 即可运行。

参照独立控件应与基线中的推单触发动作区分。参照采用同步查询，选中后由 OA 本地回填，无需平台回调 OA、轮询执行结果或额外提交“选择成功”。未保存的 OA 表单可以查询，不要求先取得 `masterId`。

## 3. HTTP 通用规则

基础地址：`https://<平台地址>/integration/openapi/v1`。测试端口/网关前缀由部署时提供；联调文件使用变量，不假设现有应用已暴露这些路径。

| 项目 | 约定 |
| --- | --- |
| 服务认证 | 每个请求携带 `X-Integration-Key`；平台预发独立的随机不透明服务凭据，并配置可访问的任务 |
| 凭据存放 | 仅 OA 插件服务端持有；OA 浏览器不持有服务密钥，也不传平台管理端用户令牌 |
| 关联标识 | 请求头 `X-Request-Id` 必填，UUID 格式；响应头及响应体原样返回合法值 |
| 内容 | UTF-8 JSON；POST 使用 `Content-Type: application/json`；请求体最大 64 KiB |
| 缓存 | 响应 `Cache-Control: no-store`；本版插件不持久缓存查询结果，配置时读取元数据，打开弹窗时刷新元数据 |
| 超时 | 平台单次业务查询总预算 10 秒，包括计数与取页；建议插件连接超时 3 秒、总超时 15 秒 |
| 重试 | 不自动重试；用户重试生成新 requestId；查询不是推单，不以 requestId 做业务幂等去重 |
| 标识 | taskCode、版本、执行 ID、OA ID 都是字符串；不得转为 JavaScript 数字 |

平台校验服务身份及任务权限；OA 插件校验当前用户权限。`context` 只用于关联日志，不赋予访问权。若任务需要按组织限制数据，应发布明确的受控参数及系统过滤规则，不能信任浏览器传来的任意部门 ID。

所有正常进入本 API 的请求使用统一响应：

```json
{
  "code": "OK",
  "message": "成功",
  "requestId": "83c87e30-8aba-4a82-b5d4-9233f52ead48",
  "executionId": null,
  "data": {}
}
```

成功为 HTTP 200 / `code=OK`；失败使用相应 HTTP 状态和业务错误码，`data=null`，另带 `error`。查询进入执行阶段后产生字符串 executionId；连接检测、目录、元数据以及执行前校验失败为 null。请求缺少/非法 requestId 时，平台生成一个合法 UUID 用于错误响应。网关或网络自身的非 JSON 错误，插件按“连接/响应异常”处理，不当成空列表。

## 4. 接口目录

下列路径均相对于基础地址，详尽字段约束见 [openapi.json](openapi.json)。V1 不接收未声明的请求字段。

| 方法与路径 | 用途 | 必要输入 | 成功 data |
| --- | --- | --- | --- |
| `GET /status` | 验证平台协议入口与服务身份 | 公共请求头 | `status, apiVersion, clientId` |
| `GET /reference/tasks` | 获取本服务可访问、已启用的参照任务 | 可选 keyword/pageNum/pageSize | `items, total, pageNum, pageSize, totalPages` |
| `GET /reference/tasks/{taskCode}/metadata` | 取得全部结果字段及筛选/排序能力 | taskCode | `taskCode, taskName, taskType, executionMode, metadataVersion, resultSets` |
| `POST /reference/tasks/{taskCode}/query` | 实时筛选和分页查询 | taskCode + 下述查询体 | `taskCode, resultSetCode, metadataVersion, rows, total, pageNum, pageSize, totalPages` |

### 4.1 连接检测

返回 `status=UP`、`apiVersion=1.0.0`、当前服务身份 `clientId`。只表示 API 可达且认证成功，**不代表 U8 数据源、某条 SQL 或 OA 回填已经可用**。U8 连通性由真实查询验证。

### 4.2 任务目录

`keyword` 可选，最长 100 字符，按任务编码/名称包含匹配；`pageNum` 默认 1，范围 1～100000；`pageSize` 默认 20，范围 1～100。固定按 taskCode 升序。返回项为 `taskCode, taskName, metadataVersion`。只列本凭据授权且启用的参照任务，不泄露 SQL、数据源地址或密码。

### 4.3 结果元数据

每个 `resultSets` 元素包含：

| 字段 | 含义 |
| --- | --- |
| resultSetCode / resultSetName | 结果集编码、中文名称 |
| selectionMode | V1 为 `SINGLE`，首条物料参照单选 |
| fields | SQL 结果的**全部**字段；字段名区分大小写，使用 SQL 别名/列标签 |
| fields[].name / label / order | 返回属性名、显示名称、显示排序号 |
| fields[].dataType / nullable | 逻辑数据类型、是否可空，须按实际结果验证后发布 |
| fields[].filterOperators / sortable | 该字段允许的筛选操作符、是否允许排序 |
| parameters | 任务声明的受控参数；本次物料任务为空数组 |
| defaults | 初始显示列、筛选字段、排序、每页数量，插件可在允许范围内配置 |
| limits | 查询分页、筛选、排序和超时限制 |

字段元数据不是“只显示哪些字段”的投影：隐藏字段仍随每条记录返回，也可在 OA 映射配置中选择。平台 SQL 列必须有唯一且明确的列标签，发布时发现重名列应拒绝发布。运行发现字段集合/类型与已发布元数据不符时返回 `RESULT_SCHEMA_MISMATCH`，不能静默截掉新增列。

metadataVersion 是不透明字符串。任务 SQL、字段、逻辑类型、过滤规则或默认配置等影响契约的内容变更后更新版本。插件保存绑定时记录版本；打开控件时核对新版本，按字段名/类型验证原映射并刷新版本；不能按字段位置重新配对。已绑定字段删除或类型不兼容时，提示管理员修订，禁止带着错误映射回填。显示名称或顺序变化不应造成映射错位。

### 4.4 查询请求

```json
{
  "resultSetCode": "tou",
  "metadataVersion": "1",
  "pageNum": 1,
  "pageSize": 200,
  "filter": {
    "logic": "AND",
    "conditions": [
      {"field": "cInvName", "operator": "contains", "values": ["清洗"]}
    ]
  },
  "sort": [{"field": "cInvCode", "direction": "ASC"}],
  "parameters": {},
  "context": {"formId": "demo-form", "masterId": null}
}
```

必填 `resultSetCode, metadataVersion`；pageNum 默认 1，pageSize 默认 200。省略 filter 表示无用户过滤，不接受空组；省略 sort 或传空数组使用任务默认排序；省略 parameters 等同空对象；context 可省略。上下文仅接受可选字符串/null 的 `formId, masterId, summaryId`，不要求未保存表单提供不存在的标识。

`parameters` 只能使用元数据声明的参数名，值为字符串或 null；必须满足对应类型、可空与必填约束。首条物料任务未声明参数，因此只接受空对象。接口不接收 SQL、`${whereString}`、数据源 ID、任意列投影或任意 OA 字段映射。

### 4.5 筛选、排序、分页

筛选组结构为 `{logic: AND|OR, conditions: [条件或子组]}`。组不得为空，最大 3 层组嵌套、共 20 个叶子条件。每个叶子条件包含 `field, operator, values`，必须同时命中字段和操作符白名单。

| 操作符 | values 数量 | 语义 |
| --- | --- | --- |
| eq / ne | 1 | 等于 / 不等于 |
| gt / ge / lt / le | 1 | 大于 / 大于等于 / 小于 / 小于等于 |
| contains / startsWith / endsWith | 1 | 字面值包含 / 开头 / 结尾 |
| between | 2 | 闭区间，下界不大于上界 |
| in | 1～100 | 属于集合 |
| isNull / isNotNull | 0，即 `[]` | 空值 / 非空值 |

values 中每项必须为字符串，最长 1000 字符；null 判断使用专用操作符。contains/startsWith/endsWith 的空字符串不合法；其他空字符串按字段类型校验。不会自动裁剪空格。`%`、`_`、`[` 等作为字面值处理，不作为用户可注入的 SQL 通配符。大小写和字符区间比较遵循数据源排序规则；字符串区间不使用 Java 字符顺序预判。数值比较按字段逻辑类型转换后绑定，不能按字符串比较大小。eq/ne 不包含 SQL NULL，筛空必须用空值操作符。

系统固定过滤与用户过滤用 AND 合并；用户 OR 不得绕过系统条件。所有筛选值使用参数绑定，列名及排序方向从服务端白名单取值；用户不提交 `${whereString}`。

排序最多 5 项，字段不重复且 sortable=true，方向仅 ASC/DESC。平台任务配置补充确定的排序规则，在源数据不变时使分页稳定。本次不能假定 cInvCode 唯一：同编码可能因库存换算率不同产生多行，应保留 SQL 分组结果，不按编码去重，也不能用编码重新查询一行代替用户选中的行。

查询页码范围 1～100000，每页 1～200。`total` 是过滤后的结果行数；`totalPages=ceil(total/pageSize)`，空结果为 0；超过末页正常返回空 rows 和实际 total。查询返回当前页的**全部字段**，不是一次性返回全库的全部行。计数和取页使用相同过滤语义；实时库存变化期间不承诺跨请求快照一致性。截图中的 12649 条记录不是固定总量。

### 4.6 查询返回与类型

`rows` 中每行使用动态对象，不定义固定物料 DTO，不按显示列裁剪。所有已发布字段键都必须出现，数据库 NULL 对应 JSON null，空字符串仍为 `""`。

**业务单元格统一使用 JSON 字符串或 null**；字段的 `dataType` 描述其逻辑类型。这样保留前导零、长编码和小数精度。插件按元数据转换为目标 OA 字段类型，禁止隐式把编码当数字。分页/count 使用 JSON 整数；执行/业务标识始终为字符串。

| dataType | 非空值表示 |
| --- | --- |
| STRING | 原字符串；无默认去空格或填空处理 |
| INTEGER | 十进制整数字符串，如 `"365"` |
| DECIMAL | 十进制小数字符串，如 `"1.000000"`，不使用指数、不另行四舍五入 |
| BOOLEAN | `"true"` 或 `"false"` |
| DATE | `YYYY-MM-DD` |
| DATETIME | `YYYY-MM-DDTHH:mm:ss`，可附 1～9 位小数秒；表示源库本地时间，不擅自添加 Z 或换算 UTC |

V1 不支持二进制/嵌套业务列作为参照字段；任务发布时拒绝不支持的类型，或在受控 SQL 中显式转换并发布对应元数据。排序/筛选使用逻辑类型，字符串承载不改变业务计算方式。

## 5. 首条物料任务配置

SQL 为用户提供的 Inventory、ComputationUnit、InventoryClass、CurrentStock 关联与分组查询；保持 `dEDate IS NULL`、换算率表达式和原结果粒度。`${ whereString }` 仅留在旧来源材料，新实现用结构化过滤转换为绑定参数。本接口不重新定义业务 SQL。

| 返回字段 | 中文标签 | 初始显示 | 初始筛选 |
| --- | --- | --- | --- |
| jz | 净重 | 否 | 否 |
| zdwbm | 主单位编码 | 否 | 否 |
| fdwbm | 辅单位编码 | 否 | 否 |
| cInvCode | 编码 | 是 | 是 |
| cInvName | 名称 | 是 | 是 |
| cInvStd | 规格 | 是 | 是 |
| zdw | 主单位 | 是 | 否 |
| fdw | 辅单位 | 是 | 否 |
| hsl | 换算率 | 否 | 否 |
| zldj | 质量等级 | 是 | 否 |
| iMassDate | 保质期天数 | 否 | 否 |
| chdl | 存货大类 | 否 | 否 |
| cd | 产地 | 否 | 否 |
| mrscbm | 默认生产部门 | 否 | 否 |

本任务当前 SQL 共 14 个字段，全部返回。`mrscbm` 与用户确认的元数据一致。此表是首条任务配置，不能变成引擎允许字段的固定列表。配套 metadata 示例的类型/可空值用于双方模拟开发；旧截图的 varchar 声明不能替代真实 JDBC 结果验证，上线前按 SQL 实际类型发布元数据版本。

采购请购单中由“存货编码”触发弹窗，单选后按前台映射回填当前明细行。编码、名称、规格、主辅单位等直接取选中完整记录；隐藏字段如 hsl、mrscbm 同样可以配置回填。是否将默认生产部门写入 OA 组织选择字段，取决于目标字段类型和编码体系，不能自动假定 U8 部门编码等于 OA 组织 ID；需要转换时由平台业务配置提供明确结果字段，插件不内置业务换码规则。

## 6. OA 插件配置和运行步骤

1. 插件安装后配置平台地址、服务凭据与超时，调用 `/status` 检测连接。
2. 管理员输入 taskCode，或通过任务目录选择任务；调用 metadata，选择结果集。
3. 在 OA 前台配置触发控件、显示列、允许的筛选项、默认排序和结果字段→OA 字段映射；保存任务编码、结果集编码、元数据版本和这些配置。源字段选择器必须列出全部 fields。
4. 用户打开控件时，插件校验会话/表单权限，读取 metadata、验证已有绑定，发起第一页 query。筛选条件变化后重置 pageNum=1。
5. 展示当前页；点选时保存整行对象。取消不修改原表单；确定时先校验全部映射，再一次完成当前行回填，避免类型错误造成半行写入。
6. 查询失败显示可理解的错误与 requestId；不要清空现有字段。元数据版本冲突先重新获取 metadata，完成映射验证后再查询，不能盲目重复原请求。

本包 [plugin-binding.example.json](examples/plugin-binding.example.json) 是插件侧配置模型建议，不是平台 API 请求；`field0001` 等为占位 OA 字段 ID。同事可按 OA 实际存储方式调整内部模型，但不得改变本 HTTP 契约。平台不要求先提供每张表单的最终映射才能开发。

## 7. 错误与诊断

失败例：

```json
{
  "code": "METADATA_VERSION_MISMATCH",
  "message": "参照配置已更新，请刷新配置后重试",
  "requestId": "83c87e30-8aba-4a82-b5d4-9233f52ead48",
  "executionId": null,
  "data": null,
  "error": {"retryable": false, "details": [{"field": "metadataVersion", "reason": "版本已变化"}]}
}
```

| HTTP | code | 插件处理 |
| --- | --- | --- |
| 400 | INVALID_ARGUMENT | 校验页码、版本、JSON、请求 ID、参数值类型等 |
| 400 | FILTER_NOT_ALLOWED / SORT_NOT_ALLOWED / PARAMETER_NOT_ALLOWED | 修正配置或请求，不能绕过平台白名单 |
| 401 | UNAUTHORIZED | 凭据缺失/无效；提示管理员检查服务配置 |
| 403 | FORBIDDEN | 服务身份已停用；不自动重试 |
| 404 | TASK_NOT_FOUND / RESULT_SET_NOT_FOUND | 任务不存在或无访问权限；或已授权任务中结果集不存在 |
| 409 | TASK_DISABLED | 已授权任务停用，停止查询 |
| 409 | METADATA_VERSION_MISMATCH | 读取最新元数据并验证映射 |
| 409 | RESULT_SCHEMA_MISMATCH | 平台修正并重新发布元数据，禁止猜测回填 |
| 413 | REQUEST_TOO_LARGE | 请求超过大小限制 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 使用 application/json |
| 503 | DATASOURCE_UNAVAILABLE / SERVICE_UNAVAILABLE | 数据源或平台依赖暂不可用，展示稍后重试 |
| 504 | QUERY_TIMEOUT | 查询超时，提示缩小筛选范围或稍后重试 |
| 500 | INTERNAL_ERROR | 记录 requestId，交平台定位 |

`error.retryable=true` 仅用于暂时不可用、查询超时，表示用户可稍后重试，不指示插件自动重放。平台不在错误中输出 SQL、堆栈、数据库地址或密码。日志关联使用 requestId；query 进入执行后同时记录 executionId。无 masterId 的参照仍需有执行记录，后续平台实现对现有日志约束做最小调整。

## 8. 双方开工与联调交付

**本契约及模拟样例足以开始并行开发。** 平台实现四个接口、通用引擎、任务/元数据前台配置和日志；插件先对模拟服务开发连接、绑定、选数及回填。OA/U8 测试环境准备不阻断上述工作。

| 到达节点 | 平台提供 | 同事提供 |
| --- | --- | --- |
| 并行开发开始 | 本文、OpenAPI、完整字段模拟样例 | SDK/补丁适配结论、插件采用的扩展入口；不能用 DEE 入口代替验证 |
| HTTP 联调 | 测试基础地址、单独交付服务凭据、任务授权、部署的版本 | 插件服务端请求/响应记录、requestId、实际超时配置；不发送密钥 |
| OA 页面联调 | 正确发布的物料元数据、真实 U8 查询可用 | 可安装插件、配置入口、任务绑定/字段映射结果、真实 OA 字段 ID 与类型 |
| 业务验收 | 查询/日志证据、SQL 原有业务规则对照 | 点击→筛选→选中→回填的完整演示、错误处理证据 |

尚待环境交接的只有实际平台地址/凭据、U8 测试库连接与权限、OA 具体补丁/SDK 可用入口及测试表单。这些不需要改变接口结构。若客户 SDK 对扩展入口有约束，由同事反馈准确限制后调整 OA 适配，不能悄悄引入 DEE 依赖。

## 9. 最低验收用例

| 用例 | 通过标准 |
| --- | --- |
| DEE 不可用 | 链路从插件直达平台/U8，全部参照操作仍完成 |
| 认证与授权 | 缺失/错误凭据失败；无权限任务不在目录且不能查询 |
| 配置驱动 | 新增同构参照任务和修改字段映射无需修改通用 Java/插件代码 |
| 全字段 | 物料每行含全部 14 个字段，未显示的 hsl/mrscbm 可配置回填 |
| 搜索 | 编码/名称/规格可组合；OR 嵌套不绕过固定业务条件 |
| 注入字符 | 引号、百分号等按受控筛选处理；未知字段、SQL 字符串输入被拒绝 |
| 分页 | 首页/翻页/无匹配/超过末页返回结构和总数正确；大页长被拒绝 |
| 精度与空值 | 编码前导零、小数精度、null 和空字符串均保持正确 |
| 同编码多行 | 不被错误去重；回填用户实际选择的那行及其换算率 |
| 元数据变更 | 旧版本查询返回 409；字段删除/不兼容类型阻止错误回填 |
| 表单回填 | 新建未保存表单可查询；仅当前明细行变化；取消及失败不破坏原值 |
| 异常诊断 | U8 不可用/查询超时可理解，双方通过相同 requestId 找到日志 |

文档与 schema 校验通过仅证明交付契约自洽，不能代替上述真实接口和 OA/U8 验收。

## 10. 来源与兼容说明

- 架构依据：项目《00-项目总方案与开发基线》V1.2；本地路径 `D:\Person_knowlegebase\my-llm-wiki\01-项目\data-platform-数据交换平台\00-项目总方案与开发基线.md`。
- 业务依据：用户提供的物料 SQL、采购请购单参照截图及“返回全部字段、前台配置映射、完全替代 DEE”的确认。
- [致远自定义控件服务端开发说明](https://open.seeyoncloud.com/v5devCAP/94/355/359/373/377.html) / [前端 2.0 说明](https://open.seeyoncloud.com/v5devCAP/94/355/359/373/375.html)：供同事查验可用扩展能力，不替代客户补丁 SDK 验证。
- [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3)：配套接口文件的格式规范。

此前 `material-reference-design.md` 和 `material-reference-plugin-handoff.md` 中无版本路径、认证待定、requestId 放在请求体等早期 HTTP 示例，均由本 V1 契约替代。后续不兼容变更需更新双方协议版本；不能一侧自行改动字段、错误码或认证方式。
