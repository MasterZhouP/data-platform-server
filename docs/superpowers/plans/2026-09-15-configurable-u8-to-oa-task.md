# Configurable U8-to-OA Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 U8→OA 定时推送从固定 Java 任务扩展为可由前端创建、复制、校验、发布并定时运行的受控配置型任务。

**Architecture:** 复用现有统一任务目录、不可变版本、只读 SQL 执行器、执行队列和 OA 流程关联表。新增 `U8_TO_OA` 配置合同：数据准备查询和 OA JSON 模板负责单据内容，增量配置负责游标扫描；一个按任务类型路由的执行器和一个按任务编码动态解析的定时定义承接任意复制任务。

**Tech Stack:** Java 17/Spring Boot/MyBatis/Maven；Vue 3/Element Plus/Vite；JUnit 5；Node 内置测试。

**Spec:** `docs/superpowers/specs/2026-09-14-configurable-oa-u8-task-design.md`，并补充本次 U8→OA 配置化垂直切片的约束。

## Global Constraints

- 页面配置只能引用已登记、启用的只读数据源；不能提交 JDBC 地址、密码、任意 Header 或脚本。
- SQL 只允许单条 `SELECT`/`WITH`，增量查询必须返回 `document_no`、`changed_at`，可选 `version_token`。
- OA 流程始终通过受管 `OaProcessOperations`，配置中只保存模板和稳定引用。
- 复制任务默认停用，不能复制游标、执行记录、OA 流程关联或敏感凭据。
- 已受理执行锁定任务修订；发布新版本不能改变队列中的执行。
- 每个生产改动先写一个会失败的行为测试，确认 RED 后再写最小实现。

---

### Task 1: U8→OA configuration contract and validation

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/config/U8ToOaTaskConfig.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/config/OaProcessRequest.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/config/IncrementalSyncConfig.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/config/U8ToOaTaskConfigValidator.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/management/TaskManagementService.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/u8tooa/config/U8ToOaTaskConfigValidatorTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/taskdefinition/management/TaskManagementServiceTest.java`

**Interfaces:**
- `U8ToOaTaskConfigValidator.parseAndValidate(JsonNode): U8ToOaTaskConfig`.
- Config root: `constants`, `dataSteps`, `oa`, `sync`.
- `oa`: `u8IdVariable`, `payloadTemplate`.
- `sync`: `datasourceKey`, `upperBoundSql`, `createSql`, `updateSql`, `deleteSql`, `initialCursor`, `overlapMinutes`.
- `TaskManagementService.prepare` accepts `TaskType.U8_TO_OA` and records read-only datasource plus `oaGateway=oa-default` dependencies.

- [ ] **Step 1: Write failing validator tests** for valid config, rejected write SQL, missing required change-query aliases, invalid cursor/overlap, and invalid template variables.
- [ ] **Step 2: Run the focused validator tests** and confirm they fail because the new type and validator are absent.
- [ ] **Step 3: Implement the records and validator** by reusing the existing read-only SQL and variable rules without adding arbitrary expressions.
- [ ] **Step 4: Extend task management** to parse `U8_TO_OA` and keep existing OA→U8/reference behavior unchanged.
- [ ] **Step 5: Run validator and task-management tests** and confirm PASS.

### Task 2: Generic U8→OA execution and dynamic scheduled source

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/U8ToOaTaskExecutor.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/U8ToOaPreviewService.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/U8ToOaRuntimeConfiguration.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/schedule/ConfigurableU8ToOaChangeSource.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/schedule/ConfigurableScheduledTaskDefinitionProvider.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/u8tooa/schedule/U8ToOaScheduledDefinition.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/sync/schedule/ScheduledTaskDefinitionProvider.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/task/TaskExecutorRegistry.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/sync/schedule/ScheduledTaskRegistry.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/u8tooa/U8ToOaTaskExecutorTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/u8tooa/schedule/ConfigurableU8ToOaChangeSourceTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/sync/schedule/ScheduledTaskRegistryTest.java`

**Interfaces:**
- `TaskExecutor.taskType()` returns `U8_TO_OA`.
- Executor runs `dataSteps`, renders `oa.payloadTemplate`, reads scalar `oa.u8IdVariable`, and calls `OaProcessOperations.start`.
- `DELETE` cancels the latest link; `CANCEL_RECREATE` prepares the replacement before cancelling; `CREATE` preserves duplicate protection.
- `ScheduledTaskDefinitionProvider.resolve(String): ScheduledTaskDefinition` resolves an enabled published `U8_TO_OA` revision at run time.

- [ ] **Step 1: Write failing executor tests** for create, duplicate skip, delete, cancel-and-recreate safety, and U8 source read failure classification.
- [ ] **Step 2: Run focused executor tests** and confirm RED.
- [ ] **Step 3: Implement the fixed U8→OA execution skeleton** with stage recording, masked payload persistence, link lifecycle, and retry-safe errors.
- [ ] **Step 4: Write failing change-source tests** for upper-bound parsing, candidate mapping and required aliases.
- [ ] **Step 5: Implement the registry-backed SQL change source** using `DatasourceRegistry` leases and bounded queries.
- [ ] **Step 6: Add provider fallback to `ScheduledTaskRegistry`** while preserving the existing fixed sales-outbound definition.
- [ ] **Step 7: Run all sync, executor and registry tests** and confirm PASS.

### Task 3: Task copy API and migration/template support

**Files:**
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/TaskCopyRequest.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/management/TaskManagementService.java`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/IntegrationTaskAdminController.java`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/IntegrationTaskApiExceptionHandler.java`
- Modify: `sql/20260914_configurable_task_platform.sql`
- Create: `bl-integration/src/test/java/com/ruoyi/integration/taskdefinition/management/TaskCopyTest.java`
- Create: `bl-admin/src/test/java/com/ruoyi/web/controller/integration/task/IntegrationTaskCopyControllerTest.java`

**Interfaces:**
- `TaskManagementService.copy(String sourceTaskCode, String targetTaskCode, String targetName, String changeNote): IntegrationTaskDefinition`.
- `POST /integration/tasks/{taskCode}/copy` body `{taskCode, taskName, changeNote}`.
- Copy reads the source draft first, otherwise active revision; creates target with `enabled=false`, one draft revision and no copied cursor state.

- [ ] **Step 1: Write failing service/controller tests** for deep-copy isolation, target-code validation, disabled default and no source revision error.
- [ ] **Step 2: Run the tests** and confirm RED.
- [ ] **Step 3: Implement copy service and endpoint** with existing optimistic version and permission conventions.
- [ ] **Step 4: Add/update SQL seed comments** documenting configurable task ownership and retain the legacy fixed job for compatibility.
- [ ] **Step 5: Run focused server tests** and confirm PASS.

### Task 4: Frontend U8→OA editor, create route and copy action

**Files:**
- Create: `data-platform-ui/src/views/integration/task/components/U8ToOaEditor.vue`
- Modify: `data-platform-ui/src/views/integration/task/create.vue`
- Modify: `data-platform-ui/src/views/integration/task/detail.vue`
- Modify: `data-platform-ui/src/views/integration/task/index.vue`
- Modify: `data-platform-ui/src/views/integration/task/model.js`
- Modify: `data-platform-ui/src/api/integration/task.js`
- Modify: `data-platform-ui/src/router/index.js`
- Test: `data-platform-ui/src/views/integration/task/model.test.js`

**Interfaces:**
- `createDraft('U8_TO_OA')` returns `constants`, `dataSteps`, `oa`, `sync`.
- `taskCreateRoute('U8_TO_OA')` opens the shared create page; the detail page chooses `U8ToOaEditor` by task type.
- The list exposes “新建 U8→OA” and “复制” for tasks that have a revision.
- Editor sections: read-only U8 data steps, OA payload template/u8-id variable, incremental SQL/cursor/overlap, and cron hint; no credentials or arbitrary write SQL.

- [ ] **Step 1: Write failing model tests** for U8→OA draft shape, route selection and copy payload normalization.
- [ ] **Step 2: Run the focused frontend tests** and confirm RED.
- [ ] **Step 3: Implement model/API/editor changes** with sentence-case labels, accessible form controls and existing visual language.
- [ ] **Step 4: Run frontend model tests and production build**; fix lint/compile errors.

### Task 5: Verification and handoff

**Files:**
- Modify only files required by failing tests or build diagnostics.

- [ ] **Step 1: Run the complete `bl-integration` test suite.**
- [ ] **Step 2: Run the complete server Maven verification from `data-platform-server`.**
- [ ] **Step 3: Run frontend model tests and `npm run build:prod` from `data-platform-ui`.**
- [ ] **Step 4: Review diffs for secrets, arbitrary SQL writes, accidental changes to existing work and route regressions.**
- [ ] **Step 5: Summarize implemented behavior, tests, and any deployment SQL/configuration step still required.**

### Task 6: Integration menu information architecture

**Files:**
- Create: `sql/20260916_integration_menu_restructure.sql`
- Modify: `data-platform-ui/src/router/index.js`
- Modify: `data-platform-ui/src/views/integration/task/model.js`
- Modify: task, reference, execution and connection navigation links under `data-platform-ui/src/views/integration/`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/IntegrationMenuMigrationTest.java`
- Test: `data-platform-ui/src/views/integration/task/model.test.js`

- [ ] **Step 1: Split task navigation into OA→U8, U8→OA and reference-task entries.**
- [ ] **Step 2: Group execution/manual handling under running management and external dependencies under connection management.**
- [ ] **Step 3: Preserve existing leaf menu ids, permissions and role assignments while adding required directory ancestors.**
- [ ] **Step 4: Add typed hidden editor routes, correct active-menu highlighting and legacy URL redirects.**
- [ ] **Step 5: Run menu tests, full backend tests and the frontend production build.**
