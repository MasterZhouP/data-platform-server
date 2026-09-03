# 集成平台模块（第一阶段）

> **Stage 0 已完成（2026-09-03）：仅模块分层。** 后端完整构建与前端生产构建通过，admin 打包包含与独立产物哈希一致的集成 JAR。测试阶段没有测试可运行，不代表业务测试或 OA/U8 联调通过。验收详情及环境见 [Stage 0 验收记录](../doc/stage-0-acceptance.md)。

## 最小结构方案与执行步骤

目标：新增一个独立 Maven JAR 模块，为 OA/U8 集成预留边界，不实现业务逻辑。
沿用 `com.ruoyi`、父工程 `com.ruoyi:bl:3.9.2` 和 Java 17。
选择单模块内按能力分包：放入 bl-system 会混合系统管理职责，立刻拆分多个模块则增加当前不需要的依赖管理成本。

1. 检查前后端结构、Git 状态与构建基线。
2. 父 POM 注册 bl-integration 并管理版本；bl-admin 引入它；新模块只直接依赖 bl-common。
3. 添加以下 package-info.java，记录职责与依赖约束，不添加空接口、Bean 或配置。
4. 在后端根目录执行 `mvn -B clean package`，确认所有模块成功且 admin 可执行包包含 integration JAR；检查 Git 差异和前端构建结果。

## 目录与职责

```text
bl-integration/
├── pom.xml
├── README.md
└── src/main/java/com/ruoyi/integration/
    ├── package-info.java
    ├── pipeline/
    │   ├── package-info.java
    │   ├── push/package-info.java
    │   └── sync/package-info.java
    ├── reference/package-info.java
    ├── datasource/package-info.java
    ├── client/package-info.java
    ├── task/package-info.java
    └── execution/package-info.java
```

| 包（相对 com.ruoyi.integration） | 后续职责 |
| --- | --- |
| 根包 | 模块边界与能力导航 |
| pipeline | 流程编排分类，不预设公共执行基类 |
| pipeline.push | OA→U8 PushPipeline：推单、推凭证、审核共用编排 |
| pipeline.sync | U8→OA SyncPipeline：定时同步、向 OA 表单或流程推送 |
| reference | ReferenceEngine：U8 只读参照查询、分页及结果组织 |
| datasource | 外部业务数据源访问；不替代若依管理库配置 |
| client | OA/U8 协议、认证及响应转换；不承载业务编排 |
| task | 任务定义与触发上下文；不负责 Quartz 调度实现 |
| execution | 单次执行、状态、结果与执行日志；不替代系统操作审计 |

## 依赖方向

当前新增的 Maven 依赖：`bl-admin → bl-integration → bl-common`。
bl-integration 不依赖 bl-admin、bl-framework、bl-system 或 bl-quartz，其他原有依赖保持不变。
新模块通过 bl-common 继承其既有传递依赖，不新增数据库驱动或 HTTP SDK。

后续包依赖约束（当前仅文档约定，未添加自动架构检查）：

- 入口适配层 → push / sync / reference → 按需使用 datasource / client / task / execution。
- 三类核心能力互不直接依赖；公共能力不反向依赖核心引擎或入口。
- task 表示任务定义，execution 表示该任务的一次执行及其日志，避免两套重复的执行日志模型。
- bl-admin 负责应用装配及后续 HTTP/事件入口；调度入口仍属于调度适配职责，具体接入方式另行设计。本阶段不新增入口包或 bl-quartz 依赖。
- Java 包名保留在 com.ruoyi 下，兼容现有启动扫描范围；当前无可扫描 Bean，不需要改扫描配置。

## 本阶段范围

仅 POM、包说明和本文档。没有 Handler、接口签名、SQL、OA/U8 调用、Controller、配置 Bean、定时任务、Mapper、业务表或前端页面。
package-info.java 用于保留并说明包结构；没有可执行类是本阶段的预期结果。
编译打包验证不代表已验证连接真实 OA/U8、数据库或应用启动。

## 下一阶段建议（基线 Stage 1，未实施）

先选择一个代表性 OA→U8 场景并收集脱敏输入、输出和失败样例，确认任务定义与单次执行、幂等、重试及日志脱敏要求。
随后定义最小执行契约和客户端/数据源接口，使用替身验证一次执行的成功与失败路径。
在这些契约确认后，再决定首个业务 Handler、存储模型和触发适配；参照与同步分别演进，不提前统一成一个万能执行器。

A8 一期主入口为“BL数据交换平台”自定义表单触发动作，按 `taskCode` 通过 HTTP 调用平台；全局 `CollaborationFinishEvent` 仅为备选。插件独立工程及客户 A8 10.0 SP1 SDK 验证属于后续工作，SDK 不进入本模块依赖。上下文字段和事务隔离需前置验证，不能仅凭平台本地测试认定真实 OA 链路通过。
