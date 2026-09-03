# Stage 0 模块分层验收记录

验收日期：2026-09-03（Asia/Shanghai）。范围：现有 `bl-integration` 模块分层、构建/打包检查、基线同步及本地提交整理。后续 Stage 1 和插件编码未实施。

项目基线：[00-项目总方案与开发基线 1.1](D:/Person_knowlegebase/my-llm-wiki/01-项目/data-platform-数据交换平台/00-项目总方案与开发基线.md)。结构及产物校验数据见 [验收证据](stage-0-evidence.json)。

## 验收结论

Stage 0 验收通过：父工程登记模块，`bl-admin → bl-integration → bl-common` 依赖成立；基础模块没有反向依赖集成模块；当前 9 个 Java 文件全部为包说明，没有新增具体业务实现。

后端 8 个 Reactor 项构建成功，独立集成 JAR 与 admin 包内同名 JAR 的 SHA-256 一致；前端生产构建成功。

**验收边界：本次证明工程可以编译、打包及保持预期模块结构。Maven 测试阶段报告 `No tests to run`，不能表述为业务测试通过。未启动应用或连接 OA/U8/MySQL，未验证真实数据库权限、HTTP 接口、插件部署、幂等、重试或生产任务。**

## 环境与构建证据

| 项目 | 本次实际使用 |
| --- | --- |
| 后端目录 | `D:\ZB\data-platform\data-platform-server` |
| 前端目录 | `D:\ZB\data-platform\data-platform-ui` |
| Java | Eclipse Temurin 17.0.19 |
| Maven | 3.6.3 |
| Node | 24.20.0 |
| npm | 11.19.0 |
| Vite | 6.4.1 |

后端在后端根目录执行 `mvn -B clean package`，没有传入跳过测试的参数。执行时间为 15:47:12～15:47:27，退出码 0，Maven 报告耗时 14.111 秒。

```text
Reactor Summary for bl 3.9.2:
bl             SUCCESS
bl-common      SUCCESS
bl-system      SUCCESS
bl-framework   SUCCESS
bl-integration SUCCESS
bl-quartz      SUCCESS
bl-generator   SUCCESS
bl-admin       SUCCESS
BUILD SUCCESS
```

7 个 JAR 模块的测试阶段均报告 `No tests to run`。本阶段无可执行业务代码，因此没有为包说明新增形式化单元测试。

前端在前端根目录执行 `npm run build:prod`，执行时间为 15:48:03～15:48:26，退出码 0。Vite 报告：

```text
vite v6.4.1 building for production...
2538 modules transformed.
built in 18.23s
```

`dist/index.html` 已生成。本次没有安装或更新依赖，没有修改前端源码、package.json 或锁文件。

当前终端默认 Node 是 14.21.3，不能作为本项目构建环境。本次使用已安装的 `C:\Users\74994\AppData\Local\nvm\v24.20.0`，只在构建进程中调整 PATH，没有改变系统 Node 切换或全局设置。重现前端构建时，在前端目录使用：

```powershell
$stage0NodeDir = 'C:\Users\74994\AppData\Local\nvm\v24.20.0'
$env:PATH = $stage0NodeDir + ';' + $env:PATH
node --version
npm.cmd run build:prod
```

应先确认版本输出为 `v24.20.0`；其他机器使用对应的本地 Node 24.20.0 安装位置。

## 结构与打包检查

| 检查项 | 结果 |
| --- | --- |
| 父 POM 的模块登记 | `bl-integration` 恰好 1 次 |
| bl-admin 直接依赖 | `bl-integration` 恰好 1 次 |
| bl-integration 直接依赖 | 仅 `bl-common` |
| 其他基础模块反向依赖 | 无 |
| 集成模块 Java 源文件 | 9 个，全部为 `package-info.java` |
| 集成 JAR 中 class 文件 | 9 个，全部为 `package-info.class` |
| admin 内嵌集成模块 | 存在 `BOOT-INF/lib/bl-integration-3.9.2.jar` |
| 独立与内嵌集成 JAR | SHA-256 一致 |
| 前端构建入口文件 | `dist/index.html` 存在 |
| A8 插件目录 | 尚未创建，符合本阶段范围 |

本次集成 JAR 的 SHA-256：`A98683A3510C81B8868E674248B74DC882259FAF57DF683AE5C1AA62C88CC7D4`。重新构建时归档元数据可能变化，后续应重新比较当次独立与内嵌 JAR，而非要求永远等于此值。

实际结构与完整职责见 [模块 README](../bl-integration/README.md)。Java 包名沿用 `com.ruoyi.integration`，参照位于 `reference`，执行日志归属 `execution`；没有为匹配原示意目录而改包名、创建空接口或重复日志模型。

## 交付改动与保护范围

本地提交整理的 Stage 0 工程改动包括：

- 根 `pom.xml`：登记集成模块并管理版本。
- `bl-admin/pom.xml`：引入集成模块。
- `bl-integration/pom.xml`、9 个包说明、README：建立模块边界并登记验收状态。
- 本验收记录及 `stage-0-evidence.json`：保留构建结果与结构验证数据。

上述 POM 和包说明已在收尾开始前存在；本轮对其哈希进行前后比较，未修改其内容。前端源码和锁文件也保持不变。构建产物按现有忽略规则保留在本机，不纳入源码提交。

知识库中的基线文件独立更新为 1.1，不属于后端 Git 仓库。修订限于 Stage 0 状态、实际包结构、自定义表单触发主入口、插件/平台边界、失败定位和后续 SDK 验证安排。原附录及 58 行任务名称、历史说明、迁移状态完整保留；Stage 0 完成不代表这些任务已迁移。

## 下一阶段边界

下一阶段为 Stage 1，待另行授权。先收集“委外入库单-推单-new”的脱敏触发配置、SQL、Groovy、请求/返回及对照结果，明确任务、执行记录、Client 和 PushPipeline 的最小契约。同时前置核验客户 A8 SDK、补丁、触发动作与事务时机。

平台可以先做本地替身验证，真实 OA→U8 闭环则必须包含实际插件触发和失败隔离。当前未开发任何 Handler、业务 Controller、SQL、Mapper、业务表、配置 Bean、定时任务或业务页面。
