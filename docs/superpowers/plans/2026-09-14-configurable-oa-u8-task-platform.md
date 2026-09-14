# Configurable OA to U8 Task Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a versioned, UI-configurable OA-to-U8 task type that uses the existing execution ledger and asynchronous dispatcher, shares one U8 account gateway, and safely resumes only post-processing after confirmed U8 success.

**Architecture:** Add a common task catalog and immutable revisions, then resolve `taskCode` to a pinned task revision and route it by task type. `OA_TO_U8` is one typed executor: it runs validated read-only named SQL, renders JSON, calls the shared U8 gateway, extracts outputs, and optionally polls read-only result queries. Existing code-managed U8-to-OA handlers remain compatible; reference-query pagination remains a dedicated synchronous execution semantic while moving its production SQL into the common task revision model.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis, MySQL migration SQL, JDBC named parameters, Jackson, Vue 3, Element Plus, Vitest, JUnit 5 and Mockito.

**Spec:** `docs/superpowers/specs/2026-09-14-configurable-oa-u8-task-design.md`

## Implementation Status (2026-09-15)

- Completed: common task/revision catalogue, fixed OA→U8 executor, shared U8 gateway, read-only SQL/template services, execution checkpoints and safe post-processing retry, task management APIs and typed OA→U8 workbench.
- Completed in this follow-up: synchronous reference tasks now use the same published task revision as their sole runtime configuration source. The legacy reference table and its mapper are migration-only concerns; the reference menu redirects to the task-centre reference filter while preserving its dedicated SQL/field editor.
- Retained by design: U8→OA sales-outbound remains code-managed. It is not converted into a page-configured workflow or arbitrary script engine in this release.

## Global Constraints

- OA plugins keep calling one platform endpoint: `POST /integration/openapi/v1/executions`; individual tasks do not expose independent OA controllers.
- `taskCode` is a stable routing identity, not a Spring bean name and not a SQL parameter restriction.
- Task SQL is edited in the UI, persisted in drafts/revisions, bound through prepared named parameters, and limited to one read-only query; no production task reads a classpath SQL resource.
- Allow only `trigger.*`, `task.constants.*`, completed `data.*`, `u8.response.*`, and `result.*` scalar variables; resolve them to named JDBC parameters server-side.
- No Groovy/JavaScript, arbitrary HTTP hosts or headers, workflow canvas, generic condition/loop engine, or user-configured write SQL.
- U8 token, trade ID, account headers, host and credentials remain in the shared gateway configuration and must never be written to task JSON, browser state, list responses, or normal logs.
- The first release has no OA/U8 database writing or OA callback. Post-processing is direct response extraction plus bounded read-only result queries only.
- U8 timeout or unconfirmed outcome becomes `RESULT_UNKNOWN` and is never blindly replayed. Confirmed U8 success followed by required result-query failure becomes `PARTIAL_SUCCESS` and resumes only result processing.
- Acceptance pins task revision/checksum and dependency snapshots so publishing a new draft never changes an accepted execution.
- Preserve existing U8-to-OA sales-outbound behavior and synchronous reference-query behavior during migration.
- For every new or modified business-chain class, add concise Chinese comments at business boundaries: request acceptance, revision pinning, SQL preparation, U8 send/result classification, post-processing checkpoint and safe retry. Comments must explain the business reason and state transition, not restate Java syntax line by line.
- Do not modify or reset user-owned changes outside the files explicitly listed in each task.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `sql/20260914_configurable_task_platform.sql` | Idempotent schema migration for common tasks, revisions, runtime snapshots, checkpoints and menu/permissions. |
| `bl-integration/.../taskdefinition/*` | Task types, task/revision records, config DTOs, validation, persistence and published-revision resolution. |
| `bl-integration/.../sql/*` | Shared read-only SQL validation, named parameter binding, bounded execution and result-shape conversion. |
| `bl-integration/.../template/*` | JSON-template rendering, JSON Pointer success checks and response-output extraction. |
| `bl-integration/.../client/u8/*` | Shared U8 connection properties, token/trade ID caching, HTTP transport and classified business call facade. |
| `bl-integration/.../execution/*` | Pinned execution snapshots, checkpoints, `PARTIAL_SUCCESS` persistence and resume-safe retry decisions. |
| `bl-integration/.../pipeline/push/*` | Typed executor registry and generic `OA_TO_U8` executor. |
| `bl-admin/.../integration/task/*` | Admin CRUD, draft validation/preview/publication and typed task workbench API. |
| `bl-admin/.../integration/openapi/IntegrationOpenApiController.java` | Unified OA execution acceptance endpoint and stable response envelope. |
| `data-platform-ui/src/api/integration/task.js` | Task-center HTTP API. |
| `data-platform-ui/src/views/integration/task/*` | Unified task list, dedicated create/detail views and typed OA-to-U8 configuration tabs. |
| `data-platform-ui/src/views/integration/execution/*` | `PARTIAL_SUCCESS`, OA trigger and post-process resume presentation. |

## Implementation Order

### Task 1: Create the common task catalog, revision schema and immutable published resolver

**Files:**
- Create: `sql/20260914_configurable_task_platform.sql`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/TaskType.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/RevisionStatus.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/IntegrationTaskDefinition.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/TaskRevision.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/TaskDefinitionRepository.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/MyBatisTaskDefinitionRepository.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/PublishedTaskRevision.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/TaskDefinitionResolver.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/TaskDefinitionNotFoundException.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/TaskDisabledException.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/taskdefinition/mapper/IntegrationTaskMapper.java`
- Create: `bl-integration/src/main/resources/mapper/integration/IntegrationTaskMapper.xml`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/taskdefinition/TaskDefinitionResolverTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/taskdefinition/mapper/IntegrationTaskMapperTest.java`

**Interfaces:**
- Produces `enum TaskType { REFERENCE_QUERY, OA_TO_U8, U8_TO_OA }` and `enum RevisionStatus { DRAFT, VALIDATED, PUBLISHED, ARCHIVED }`.
- Produces `PublishedTaskRevision resolvePublished(String taskCode)` where the result contains `Long revisionId()`, `String checksum()`, `TaskType taskType()`, `JsonNode config()`, `Map<String, String> dependencyRevisions()`, and `String dedupKey(TriggerCommand command)`.
- Produces `TaskDefinitionRepository` methods `findTask`, `findPublishedRevision`, `insertTask`, `insertRevision`, `replaceDraft`, `publishDraft`, and `listTasks`.

- [ ] **Step 1: Write the failing resolver tests**

```java
@Test
void resolvesOnlyAnEnabledTaskWithPublishedRevision() {
    repository.published("OA_EXPENSE_VOUCHER", TaskType.OA_TO_U8, 18L, "sha256-a");
    PublishedTaskRevision result = resolver.resolvePublished("OA_EXPENSE_VOUCHER");
    assertEquals(18L, result.revisionId());
    assertEquals(TaskType.OA_TO_U8, result.taskType());
}

@Test
void rejectsDraftOnlyAndDisabledTasks() {
    repository.draftOnly("OA_DRAFT", TaskType.OA_TO_U8);
    assertThrows(TaskDefinitionNotFoundException.class, () -> resolver.resolvePublished("OA_DRAFT"));
    repository.disabledPublished("OA_DISABLED", TaskType.OA_TO_U8);
    assertThrows(TaskDisabledException.class, () -> resolver.resolvePublished("OA_DISABLED"));
}
```

- [ ] **Step 2: Run the resolver test to verify the missing catalog fails**

Run: `mvn -pl bl-integration -Dtest=TaskDefinitionResolverTest test`

Expected: FAIL because the task-definition classes do not exist.

- [ ] **Step 3: Add idempotent task/revision tables and repository contracts**

```sql
CREATE TABLE IF NOT EXISTS int_integration_task (
  task_code VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
  task_name VARCHAR(100) NOT NULL,
  task_type VARCHAR(30) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  active_revision_id BIGINT DEFAULT NULL,
  draft_revision_id BIGINT DEFAULT NULL,
  config_version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
  PRIMARY KEY (task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS int_integration_task_revision (
  revision_id BIGINT NOT NULL AUTO_INCREMENT,
  task_code VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
  revision_no INT NOT NULL, status VARCHAR(20) NOT NULL,
  config_json LONGTEXT NOT NULL, config_checksum CHAR(64) NOT NULL,
  validation_json LONGTEXT DEFAULT NULL, change_note VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
  PRIMARY KEY (revision_id), UNIQUE KEY uk_int_task_revision (task_code, revision_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

Implement resolver lookup with an exact `task_code`, `enabled = 1`, `status = 'PUBLISHED'`, and matching active revision. Calculate `config_checksum` using UTF-8 SHA-256 of canonicalized JSON in the application before persistence.

- [ ] **Step 4: Run resolver and mapper tests**

Run: `mvn -pl bl-integration -Dtest=TaskDefinitionResolverTest,IntegrationTaskMapperTest test`

Expected: PASS; a draft-only, disabled, or non-existent task cannot produce an executable revision.

- [ ] **Step 5: Commit the catalog foundation**

```bash
git add sql/20260914_configurable_task_platform.sql bl-integration/src/main/java/com/ruoyi/integration/taskdefinition bl-integration/src/main/resources/mapper/integration/IntegrationTaskMapper.xml bl-integration/src/test/java/com/ruoyi/integration/taskdefinition
git commit -m "feat: add versioned integration task catalog"
```

### Task 2: Define and validate the OA-to-U8 configuration contract

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/OaToU8TaskConfig.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/ReadQueryStep.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/ResultCardinality.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/U8BusinessRequest.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/SuccessRule.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/ResponseOutput.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/ResultQueryStep.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/config/TaskConfigValidator.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/oatou8/config/TaskConfigValidatorTest.java`

**Interfaces:**
- Consumes `TaskType.OA_TO_U8` and Jackson `JsonNode` from Task 1.
- Produces `OaToU8TaskConfig parseAndValidate(JsonNode config)`.
- `ReadQueryStep` exposes `String code()`, `int order()`, `String datasourceKey()`, `String sql()`, `ResultCardinality cardinality()`, and `Map<String, String> parameterBindings()`.
- Produces `enum ResultCardinality { ONE, LIST, SCALAR }` and `record SuccessRule(String pointer, Set<String> allowedValues)`.
- `ResultQueryStep` adds `boolean required()`, `int initialDelayMs()`, `int intervalMs()`, `int maxAttempts()`, and `Map<String, String> outputMappings()`.

- [ ] **Step 1: Write failing validation tests for unsafe and cyclic configuration**

```java
@Test
void rejectsWriteSqlAndUnsafePlaceholderSyntax() {
    assertError("UPDATE formmain_001 SET field001='x'", "SQL_NOT_READ_ONLY");
    assertError("select * from formmain where id = ${trigger.masterId}", "SQL_PLACEHOLDER_NOT_ALLOWED");
}

@Test
void allowsOnlyEarlierStepScalarReferences() {
    assertError(configWithBinding("data.lines.amount"), "STEP_OUTPUT_NOT_SCALAR");
    assertError(configWithFirstStepBinding("data.second.code"), "STEP_REFERENCE_ORDER_INVALID");
}

@Test
void rejectsResultQueryOutsidePollLimits() {
    assertError(configWithPoll(0, 1000, 99), "RESULT_POLL_LIMIT_INVALID");
}
```

- [ ] **Step 2: Run the validation test to verify it fails**

Run: `mvn -pl bl-integration -Dtest=TaskConfigValidatorTest test`

Expected: FAIL because no typed OA-to-U8 configuration validator exists.

- [ ] **Step 3: Implement the fixed config schema and validator**

```java
public record OaToU8TaskConfig(Map<String, String> constants,
        List<ReadQueryStep> dataSteps, U8BusinessRequest u8,
        List<ResultQueryStep> resultQueries) { }

public record U8BusinessRequest(String operationCode, String path,
        String requestJsonTemplate, SuccessRule successRule,
        String errorMessagePointer, List<ResponseOutput> outputs) { }
```

Validate unique codes, contiguous distinct ordering, a registered read-only datasource key, `SELECT`/`WITH ... SELECT` only, no semicolon/multiple statements, no `${`, legal variable namespaces, scalar-only predecessor dependencies, JSON object template, JSON Pointer syntax, bounded polling (`initialDelayMs` 0..30000, `intervalMs` 100..30000, `maxAttempts` 1..20), and `POST`/`application/json` as implicit fixed behavior. Reject all `UPDATE`, `INSERT`, `DELETE`, `MERGE`, `EXEC`, `CALL`, and DDL tokens after comment removal.

- [ ] **Step 4: Run the validator tests**

Run: `mvn -pl bl-integration -Dtest=TaskConfigValidatorTest test`

Expected: PASS; configuration cannot express script execution, write SQL, arbitrary HTTP settings, cyclic references, or unbounded polling.

- [ ] **Step 5: Commit the typed contract**

```bash
git add bl-integration/src/main/java/com/ruoyi/integration/oatou8/config bl-integration/src/test/java/com/ruoyi/integration/oatou8/config
git commit -m "feat: validate configurable OA to U8 task definitions"
```

### Task 3: Extract bounded read-only named-SQL execution

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/sql/ReadOnlySqlExecutor.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/sql/ReadOnlySqlResult.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/sql/SqlVariableResolver.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/sql/SqlExecutionException.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/ExecutionVariableContext.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/datasource/IntegrationExternalDataSourceConfig.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/sql/ReadOnlySqlExecutorTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/sql/SqlVariableResolverTest.java`

**Interfaces:**
- Consumes validated `ReadQueryStep`, `ResultQueryStep` and a runtime `Map<String, Object>` context.
- Produces `ReadOnlySqlResult execute(String datasourceKey, String sql, ResultCardinality cardinality, Map<String, Object> namedParameters)` with `ObjectNode one()`, `ArrayNode list()`, and `JsonNode scalar()` accessors.
- Produces `Map<String, Object> resolve(Map<String, String> bindings, ExecutionVariableContext context)`.
- Produces `ExecutionVariableContext` with `trigger`, `constants`, `data`, `u8Response`, and `result` read-only namespaces, plus `Object scalar(String variableName)` for the only values that SQL may bind.

- [ ] **Step 1: Write failing H2 tests for prepared binding and cardinality**

```java
@Test
void bindsTriggerAndPriorScalarThroughJdbcParameters() {
    var result = executor.execute("oa", "select :masterId id, :prior code", ONE,
            Map.of("masterId", "A' OR 1=1", "prior", "P-1"));
    assertEquals("A' OR 1=1", result.one().path("ID").asText());
}

@Test
void rejectsWrongResultCardinality() {
    assertThrows(SqlExecutionException.class, () -> executor.execute("oa", "select 1 union all select 2", ONE, Map.of()));
}
```

- [ ] **Step 2: Run the SQL tests to verify they fail**

Run: `mvn -pl bl-integration -Dtest=ReadOnlySqlExecutorTest,SqlVariableResolverTest test`

Expected: FAIL because no reusable read-only executor exists.

- [ ] **Step 3: Implement a backend-only read SQL service**

```java
public interface ReadOnlySqlExecutor {
    ReadOnlySqlResult execute(String datasourceKey, String sql,
            ResultCardinality cardinality, Map<String, Object> parameters);
}
```

Resolve `trigger.masterId` etc. into SQL parameter aliases declared by the task, obtain only registered `oaJdbcTemplate` or `u8JdbcTemplate`, set query timeout/max rows, bind values via `NamedParameterJdbcTemplate`, and return a bounded, JSON-safe result. Enforce database-side read-only connections and cap `ONE` at two rows, `SCALAR` at two rows/one column, and `LIST` at 1000 rows. Do not reuse ReferenceEngine pagination/filter syntax in this component.

- [ ] **Step 4: Run SQL tests and existing reference engine regression tests**

Run: `mvn -pl bl-integration -Dtest=ReadOnlySqlExecutorTest,SqlVariableResolverTest,ReferenceEngineTest test`

Expected: PASS; SQL values are bound rather than concatenated and reference query behavior is unchanged.

- [ ] **Step 5: Commit the SQL facility**

```bash
git add bl-integration/src/main/java/com/ruoyi/integration/sql bl-integration/src/main/java/com/ruoyi/integration/datasource/IntegrationExternalDataSourceConfig.java bl-integration/src/test/java/com/ruoyi/integration/sql
git commit -m "feat: add bounded read-only integration SQL executor"
```

### Task 4: Add JSON rendering, success rules and output extraction

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/template/JsonTemplateRenderer.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/template/JsonResponseEvaluator.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/template/ResponseEvaluation.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/template/TemplateRenderException.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/template/JsonTemplateRendererTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/template/JsonResponseEvaluatorTest.java`

**Interfaces:**
- Consumes legal variables produced by Task 3 and `U8BusinessRequest` from Task 2.
- Produces `JsonNode render(String jsonTemplate, ExecutionVariableContext variables)` and `ResponseEvaluation evaluate(JsonNode response, SuccessRule rule, List<ResponseOutput> outputs)`.

- [ ] **Step 1: Write failing template and pointer tests**

```java
@Test
void rendersOnlyWholeStringVariableTokens() {
    assertEquals("M-100", renderer.render("{\"id\":\"{{trigger.masterId}}\"}", variables).path("id").asText());
    assertThrows(TemplateRenderException.class,
        () -> renderer.render("{\"id\":\"prefix-{{trigger.masterId}}\"}", variables));
}

@Test
void extractsDeclaredOutputsAfterAllowedSuccessValue() {
    var evaluation = evaluator.evaluate(readTree("{\"code\":0,\"voucher\":\"记-001\"}"),
        new SuccessRule("/code", Set.of("0")), List.of(new ResponseOutput("voucherNo", "/voucher", true)));
    assertEquals("记-001", evaluation.outputs().path("voucherNo").asText());
}
```

- [ ] **Step 2: Run the renderer tests to verify they fail**

Run: `mvn -pl bl-integration -Dtest=JsonTemplateRendererTest,JsonResponseEvaluatorTest test`

Expected: FAIL because controlled template rendering and output extraction do not exist.

- [ ] **Step 3: Implement controlled JSON-only interpolation**

```java
public interface JsonTemplateRenderer {
    JsonNode render(String template, ExecutionVariableContext variables);
}
```

Parse source JSON before rendering. Permit a token only when an entire JSON string value is exactly `{{namespace.name}}`; preserve underlying scalar/array/object JSON type, reject missing variables and embedded expressions. Evaluate success using configured HTTP-class and JSON Pointer allowed values, extract only declared pointers into `result.*`, and turn malformed response JSON, missing required output, and business failure into typed exceptions without logging raw sensitive data.

- [ ] **Step 4: Run the renderer tests**

Run: `mvn -pl bl-integration -Dtest=JsonTemplateRendererTest,JsonResponseEvaluatorTest test`

Expected: PASS; no expression language or arbitrary template evaluation is introduced.

- [ ] **Step 5: Commit template services**

```bash
git add bl-integration/src/main/java/com/ruoyi/integration/template bl-integration/src/test/java/com/ruoyi/integration/template
git commit -m "feat: add controlled U8 JSON template processing"
```

### Task 5: Implement the shared U8 gateway boundary

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/client/u8/U8Gateway.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/client/u8/U8GatewayProperties.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/client/u8/JdkU8Gateway.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/client/u8/U8GatewayException.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/client/u8/U8Client.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/client/u8/U8CallResult.java`
- Modify: `bl-integration/src/main/resources/application.yml`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/client/u8/JdkU8GatewayTest.java`

**Interfaces:**
- Produces `U8CallResult postBusiness(String operationCode, String relativePath, JsonNode payload)` and `U8ConnectionHealth health()`.
- Keeps `U8Client.post(String endpoint, String requestPayload)` as a compatibility adapter until code-managed callers migrate.
- Consumes non-secret U8 connection references only from task config; gateway properties contain connection base URL, token/trade ID paths, account identifiers and secret-backed credentials.

- [ ] **Step 1: Write failing HTTP transport tests for cache and uncertainty classification**

```java
@Test
void reusesOneAccountTokenButObtainsATradeIdForEachBusinessCall() {
    gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", payload);
    gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", payload);
    assertEquals(1, server.requestCount("/system/token"));
    assertEquals(2, server.requestCount("/system/tradeid"));
}

@Test
void turnsTimeoutAfterSendIntoResultUnknown() {
    server.timeoutAfterReceiving("/api/voucher/add");
    assertEquals(U8CallStatus.RESULT_UNKNOWN, gateway.postBusiness("VOUCHER_ADD", "/api/voucher/add", payload).status());
}
```

- [ ] **Step 2: Run the gateway test to verify it fails**

Run: `mvn -pl bl-integration -Dtest=JdkU8GatewayTest test`

Expected: FAIL because the U8 client has only a stage-one interface.

- [ ] **Step 3: Implement one shared gateway**

```java
public interface U8Gateway {
    U8CallResult postBusiness(String operationCode, String relativePath, JsonNode payload);
    U8ConnectionHealth health();
}
```

Allow only absolute paths from a configured allow-list keyed by `operationCode`; reject a host, query string, header or credential supplied from task JSON. Cache the shared account token with its expiry/short TTL, but obtain a new `tradeId` for every actual business post because it is a transaction-unique identifier; refresh the token once on an authentication rejection. Classify connect failures before request as `PRE_SEND_FAILURE`, response timeout/connection loss after write as `RESULT_UNKNOWN`, HTTP/non-zero U8 rejection as `BUSINESS_FAILURE`, and confirmed responses as `SUCCESS`. Mask account/token values before errors or stage payloads.

- [ ] **Step 4: Run the gateway tests**

Run: `mvn -pl bl-integration -Dtest=JdkU8GatewayTest test`

Expected: PASS; all configurable tasks share token/trade ID logic and cannot override connection secrets.

- [ ] **Step 5: Commit shared U8 gateway**

```bash
git add bl-integration/src/main/java/com/ruoyi/integration/client/u8 bl-integration/src/test/java/com/ruoyi/integration/client/u8
git commit -m "feat: add shared U8 business gateway"
```

### Task 6: Pin task revisions and add post-process checkpoints to executions

**Files:**
- Modify: `sql/20260914_configurable_task_platform.sql`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/execution/domain/ExecutionStatus.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/execution/domain/IntegrationExecution.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/execution/repository/ExecutionRepository.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/execution/repository/MyBatisExecutionRepository.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/execution/service/IntegrationExecutionService.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/execution/service/ResolvedExecution.java`
- Modify: `bl-integration/src/main/resources/mapper/integration/IntegrationExecutionMapper.xml`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/task/TriggerSource.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/execution/service/IntegrationExecutionFlowTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/execution/mapper/ExecutionTerminalStateMapperTest.java`

**Interfaces:**
- Consumes `PublishedTaskRevision` from Task 1 when accepting an external/configured task.
- Adds to `IntegrationExecution`: `Long taskRevisionId`, `String taskChecksum`, `String dependencySnapshot`, `String lastCompletedStage`, `Boolean u8Confirmed`, `String resumeMode`, and `String resultOutputsJson`.
- Adds repository methods `markPartialSuccess`, `checkpointU8Confirmed`, `checkpointPostProcess`, and `markPostProcessCompleted`.
- Adds `TriggerSource.OA_API` and `ExecutionStatus.PARTIAL_SUCCESS`.
- Produces `ResolvedExecution(IntegrationExecution execution, PublishedTaskRevision revision)` for the pipeline to execute a stable accepted snapshot.

- [ ] **Step 1: Add failing regression tests for the three retry classes**

```java
@Test
void confirmedU8WithMissingRequiredResultResumesPostProcessOnly() {
    var first = service.accept(oaCommand("OA_EXPENSE_VOUCHER", "M-400"));
    runner.run(first.executionId());
    assertEquals(PARTIAL_SUCCESS.name(), repository.findById(first.executionId()).getStatus());
    var retry = service.retry(first.executionId());
    assertEquals("POST_PROCESS", repository.findById(retry.executionId()).getResumeMode());
}

@Test
void resultUnknownStillHasNoRetryAction() {
    var execution = repository.resultUnknownAfterU8();
    assertThrows(RetryRejectedException.class, () -> service.retry(execution.getExecutionId()));
}
```

- [ ] **Step 2: Run the execution flow test to verify it fails**

Run: `mvn -pl bl-integration -Dtest=IntegrationExecutionFlowTest,ExecutionTerminalStateMapperTest test`

Expected: FAIL because `PARTIAL_SUCCESS` and a post-process resume checkpoint do not exist.

- [ ] **Step 3: Persist snapshots and resume state atomically**

```sql
ALTER TABLE int_execution ADD COLUMN task_revision_id BIGINT DEFAULT NULL;
ALTER TABLE int_execution ADD COLUMN task_checksum CHAR(64) DEFAULT NULL;
ALTER TABLE int_execution ADD COLUMN dependency_snapshot_json LONGTEXT DEFAULT NULL;
ALTER TABLE int_execution ADD COLUMN last_completed_stage VARCHAR(100) DEFAULT NULL;
ALTER TABLE int_execution ADD COLUMN u8_confirmed TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE int_execution ADD COLUMN resume_mode VARCHAR(20) NOT NULL DEFAULT 'FULL';
ALTER TABLE int_execution ADD COLUMN result_outputs_json LONGTEXT DEFAULT NULL;
```

At acceptance resolve and persist the published revision before writing `RECEIVED`. For a `PARTIAL_SUCCESS` retry, copy the source execution's snapshot and result outputs to a child with `resumeMode = POST_PROCESS`; retain its dedup key. For full retry, use the original pinned revision rather than the newest published revision. Allow retry for `FAILED`/`PARTIAL_SUCCESS` only if safe; continue to block `RESULT_UNKNOWN`.

- [ ] **Step 4: Run execution regression tests**

Run: `mvn -pl bl-integration -Dtest=IntegrationExecutionFlowTest,ExecutionTerminalStateMapperTest test`

Expected: PASS; U8 success is a durable boundary and only incomplete result processing resumes.

- [ ] **Step 5: Commit checkpoint persistence**

```bash
git add sql/20260914_configurable_task_platform.sql bl-integration/src/main/java/com/ruoyi/integration/execution bl-integration/src/main/java/com/ruoyi/integration/task/TriggerSource.java bl-integration/src/main/resources/mapper/integration/IntegrationExecutionMapper.xml bl-integration/src/test/java/com/ruoyi/integration/execution
git commit -m "feat: persist task snapshots and post-process checkpoints"
```

### Task 7: Route configured task types and execute generic OA-to-U8 tasks

**Files:**
- Create: `bl-integration/src/main/java/com/ruoyi/integration/task/TaskExecutor.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/task/TaskExecutorRegistry.java`
- Create: `bl-integration/src/main/java/com/ruoyi/integration/oatou8/OaToU8TaskExecutor.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/pipeline/push/PushPipelineRunner.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/task/PushHandlerRegistry.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/task/PushResult.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/oatou8/OaToU8TaskExecutorTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/task/TaskExecutorRegistryTest.java`

**Interfaces:**
- Produces `TaskExecutor supports(TaskType type)` and `PushResult execute(ResolvedExecution execution, ExecutionStageRecorder recorder)`.
- Keeps `IntegrationTaskHandler` and `PushHandlerRegistry` as the compatibility adapter selected for registered `U8_TO_OA` code tasks.
- Consumes Task 2 config, Task 3 SQL executor, Task 4 renderer/evaluator, Task 5 gateway and Task 6 checkpoints.

- [ ] **Step 1: Write failing executor tests for no per-task Spring handler and no repush**

```java
@Test
void sameExecutorRunsTwoDifferentPublishedOaToU8TaskCodes() {
    execute("OA_EXPENSE_VOUCHER");
    execute("OA_EXTERNAL_RECEIPT");
    verify(gateway, times(2)).postBusiness(anyString(), anyString(), any());
}

@Test
void postProcessResumeDoesNotCallU8Again() {
    executor.execute(postProcessExecutionWithConfirmedVoucher(), recorder);
    verifyNoInteractions(gateway);
    verify(sql).execute(eq("u8"), anyString(), any(), anyMap());
}
```

- [ ] **Step 2: Run the executor tests to verify they fail**

Run: `mvn -pl bl-integration -Dtest=OaToU8TaskExecutorTest,TaskExecutorRegistryTest test`

Expected: FAIL because runtime selection is still a direct `taskCode` Handler lookup.

- [ ] **Step 3: Replace direct dynamic-task handler lookup with type routing**

```java
public interface TaskExecutor {
    TaskType taskType();
    PushResult execute(ResolvedExecution execution, ExecutionStageRecorder recorder);
}
```

For `FULL` execution: record each `DATA_<stepCode>` stage; run data steps in order; render and record a masked `U8_REQUEST_RENDERED` summary; record `U8_REQUEST_SENDING`; invoke U8 once; on confirmed success evaluate/extract direct outputs, call `checkpointU8Confirmed`, then run result queries. For result queries, record `RESULT_<stepCode>_ATTEMPT_<n>` stages and sleep only by the validated bounded limits. If a required output still cannot be obtained, persist outputs and mark `PARTIAL_SUCCESS`; if optional query fails, preserve existing outputs and complete with warnings. On `POST_PROCESS`, load pinned config/outputs and start at the saved result-query checkpoint without rendering or posting U8.

Classify gateway outcomes as `PushFailureException.retryable`, `nonRetryable`, or `resultUnknown`; never transform an unconfirmed call into `PARTIAL_SUCCESS`.

- [ ] **Step 4: Run executor and existing sales-outbound registry tests**

Run: `mvn -pl bl-integration -Dtest=OaToU8TaskExecutorTest,TaskExecutorRegistryTest,PushHandlerRegistryTest,SalesOutboundTaskHandlerTest test`

Expected: PASS; configurable tasks share one executor while code tasks retain their handler behavior.

- [ ] **Step 5: Commit typed execution routing**

```bash
git add bl-integration/src/main/java/com/ruoyi/integration/oatou8 bl-integration/src/main/java/com/ruoyi/integration/task bl-integration/src/main/java/com/ruoyi/integration/pipeline/push/PushPipelineRunner.java bl-integration/src/test/java/com/ruoyi/integration/oatou8 bl-integration/src/test/java/com/ruoyi/integration/task
git commit -m "feat: execute configurable OA to U8 tasks"
```

### Task 8: Expose one OA acceptance endpoint and the versioned task management API

**Files:**
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/openapi/IntegrationOpenApiController.java`
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/openapi/OpenApiExecutionRequest.java`
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/IntegrationTaskAdminController.java`
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/TaskRevisionRequest.java`
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/TaskPreviewRequest.java`
- Create: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/task/IntegrationTaskApiExceptionHandler.java`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/reference/ReferenceOpenApiController.java`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/reference/ReferenceAdminController.java`
- Test: `bl-admin/src/test/java/com/ruoyi/web/controller/integration/openapi/IntegrationOpenApiControllerTest.java`
- Test: `bl-admin/src/test/java/com/ruoyi/web/controller/integration/task/IntegrationTaskAdminControllerTest.java`

**Interfaces:**
- Produces `POST /integration/openapi/v1/executions` accepting exactly `taskCode`, `masterId`, `formId`, `summaryId` and a required valid `X-Request-Id`.
- Produces admin endpoints `GET/POST /integration/tasks`, `GET /integration/tasks/{taskCode}`, `PUT /integration/tasks/{taskCode}/draft`, `POST /integration/tasks/{taskCode}/validate`, `POST /integration/tasks/{taskCode}/preview`, `POST /integration/tasks/{taskCode}/publish`, and `GET /integration/tasks/{taskCode}/revisions`.

- [ ] **Step 1: Write failing controller contract tests**

```java
@Test
void oaEndpointAcceptsFixedContextAndReturnsPendingExecution() throws Exception {
    mvc.perform(post("/integration/openapi/v1/executions")
        .header("X-Request-Id", UUID.randomUUID().toString()).contentType(APPLICATION_JSON)
        .content("{\"taskCode\":\"OA_EXPENSE_VOUCHER\",\"masterId\":\"M-10\",\"formId\":\"F-1\",\"summaryId\":\"S-1\"}"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING"));
}

@Test
void previewRendersSqlAndJsonButNeverCallsU8() throws Exception {
    mvc.perform(post("/integration/tasks/OA_EXPENSE_VOUCHER/preview").content(previewJson))
        .andExpect(status().isOk());
    verifyNoInteractions(u8Gateway);
}
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run: `mvn -pl bl-admin -am -Dtest=IntegrationOpenApiControllerTest,IntegrationTaskAdminControllerTest test`

Expected: FAIL because common task management and OA execution acceptance APIs do not exist.

- [ ] **Step 3: Implement deliberate API boundaries**

```java
public record OpenApiExecutionRequest(String taskCode, String masterId,
        String formId, String summaryId) { }
```

Reject unknown JSON keys, `force`, action overrides, empty `taskCode`/`masterId`, oversized values and absent/malformed request IDs. Construct `TriggerCommand` with `TaskAction.CREATE`, `TriggerSource.OA_API`, and `force = false`, call existing `ExecutionAcceptor`, and return the standard request-id envelope with HTTP 202. Use the same OpenAPI prefix as reference routes; do not add task-specific external controllers.

Admin draft save uses the task config validator and optimistic `configVersion`; validate returns a structured checklist; preview runs only data steps and JSON render with test trigger context and never calls U8 or result polling; publish atomically archives old active revision and activates the validated draft. Retain reference endpoints as compatibility routes while internally forwarding their task metadata/catalog operations to the unified task service.

- [ ] **Step 4: Run controller tests and current reference API tests**

Run: `mvn -pl bl-admin -am -Dtest=IntegrationOpenApiControllerTest,IntegrationTaskAdminControllerTest,ReferenceOpenApiControllerTest test`

Expected: PASS; OA uses one accept endpoint and preview is read-only.

- [ ] **Step 5: Commit HTTP APIs**

```bash
git add bl-admin/src/main/java/com/ruoyi/web/controller/integration bl-admin/src/test/java/com/ruoyi/web/controller/integration
git commit -m "feat: expose unified integration task APIs"
```

### Task 9: Migrate reference production configuration to task revisions

**Files:**
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/reference/catalog/ReferenceCatalog.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/reference/model/ReferenceTask.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/reference/engine/ReferenceEngine.java`
- Modify: `bl-integration/src/main/java/com/ruoyi/integration/reference/service/ReferenceQueryService.java`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/reference/ReferenceAdminController.java`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/reference/ReferenceOpenApiController.java`
- Modify: `sql/20260914_configurable_task_platform.sql`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/reference/catalog/ReferenceCatalogTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/reference/engine/ReferenceEngineTest.java`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/reference/service/ReferenceQueryServiceTest.java`

**Interfaces:**
- Consumes Task 1 catalogue/revision persistence and existing reference metadata contract.
- Produces `ReferenceTask` whose active SQL text is supplied from the pinned published revision, never from `sqlResource`.
- Keeps legacy `int_reference_task.sql_resource` only as an idempotent one-time migration source; it is not read by production execution after migration.

- [ ] **Step 1: Write a failing test that proves published DB SQL—not classpath resource—is executed**

```java
@Test
void executesPinnedPublishedSqlTextRatherThanClasspathSqlResource() {
    catalog.publish(referenceTask("MATERIAL", "select 'published' as cInvCode"));
    assertEquals("published", queryService.query(catalog.get("MATERIAL"), request, "r-1")
        .data().path("rows").get(0).path("cInvCode").asText());
}
```

- [ ] **Step 2: Run reference migration tests to verify they fail**

Run: `mvn -pl bl-integration -Dtest=ReferenceCatalogTest,ReferenceEngineTest,ReferenceQueryServiceTest test`

Expected: FAIL because `ReferenceEngine` currently reads `ClassPathResource`.

- [ ] **Step 3: Change only the SQL source, not reference semantics**

Replace `ReferenceTask.sqlResource` with revision-supplied `sqlText`. Move the seeded material SQL content into a draft/published task revision in the migration; keep the old SQL resource only to populate databases upgraded from earlier releases. Preserve all existing metadata version, pagination, filtering, sorting, logging and no-retry behavior.

- [ ] **Step 4: Run all reference tests**

Run: `mvn -pl bl-integration -Dtest=ReferenceCatalogTest,ReferenceTaskMapperTest,ReferenceEngineTest,ReferenceQueryServiceTest,ReferenceRecoveryTest test`

Expected: PASS; reference query behavior stays synchronous and production SQL no longer depends on classpath selection.

- [ ] **Step 5: Commit reference migration**

```bash
git add sql/20260914_configurable_task_platform.sql bl-integration/src/main/java/com/ruoyi/integration/reference bl-integration/src/test/java/com/ruoyi/integration/reference bl-admin/src/main/java/com/ruoyi/web/controller/integration/reference
git commit -m "feat: store reference SQL in task revisions"
```

### Task 10: Build the unified task-center frontend and typed OA-to-U8 editor

**Files:**
- Create: `data-platform-ui/src/api/integration/task.js`
- Create: `data-platform-ui/src/views/integration/task/model.js`
- Create: `data-platform-ui/src/views/integration/task/model.test.js`
- Create: `data-platform-ui/src/views/integration/task/index.vue`
- Create: `data-platform-ui/src/views/integration/task/create.vue`
- Create: `data-platform-ui/src/views/integration/task/detail.vue`
- Create: `data-platform-ui/src/views/integration/task/components/TaskHeader.vue`
- Create: `data-platform-ui/src/views/integration/task/components/SqlStepEditor.vue`
- Create: `data-platform-ui/src/views/integration/task/components/VariablePanel.vue`
- Create: `data-platform-ui/src/views/integration/task/components/OaToU8Editor.vue`
- Modify: `data-platform-ui/src/views/integration/reference/index.vue`
- Modify: `data-platform-ui/src/api/integration/reference.js`
- Test: `data-platform-ui/src/views/integration/task/model.test.js`

**Interfaces:**
- Consumes `GET /integration/tasks` and typed detail/draft/validate/preview/publish endpoints from Task 8.
- Produces routes/views rendered by menu components `integration/task/index`, `integration/task/create`, and `integration/task/detail`.
- Produces `createDraft(type)`, `validateTaskDraft(task)`, `serializeDraft(form)`, and `allowedVariables(stepIndex, draft)` model helpers.

- [ ] **Step 1: Write failing frontend model tests for a blank first task and variable scope**

```js
it('creates an OA-to-U8 draft when the task list is empty', () => {
  expect(createDraft('OA_TO_U8').taskType).toBe('OA_TO_U8')
  expect(createDraft('OA_TO_U8').config.dataSteps).toEqual([])
})

it('shows only trigger/constants and earlier scalar outputs in a step', () => {
  const names = allowedVariables(1, draftWithSteps())
  expect(names).toContain('trigger.masterId')
  expect(names).toContain('data.header.code')
  expect(names).not.toContain('data.lines')
  expect(names).not.toContain('data.later.code')
})
```

- [ ] **Step 2: Run the frontend model test to verify it fails**

Run: `npm run test -- --run src/views/integration/task/model.test.js`

Expected: FAIL because no unified task model exists.

- [ ] **Step 3: Implement the typed workbench, not a generic workflow editor**

```js
export function createDraft(taskType) {
  return {
    taskCode: '', taskName: '', taskType, enabled: false,
    config: taskType === 'OA_TO_U8'
      ? { constants: {}, dataSteps: [], u8: defaultU8Request(), resultQueries: [] }
      : defaultConfigFor(taskType)
  }
}
```

Task list supports task-name/code, type, enabled and readiness filters, shows active/draft version and server-provided dependency health, and is allowed to create the first task. Create is a full page: task type is selected once, first save creates a draft, then route changes to detail. OA-to-U8 detail has tabs Basic, Data Preparation, U8 Interface, Result Handling, Debug & Publish, Versions. SQL cards support add/delete/up/down, manual text, declared parameter bindings and read-only preview; no resource picker, field-mapping DSL, update mode, script editor or node graph. U8 panel displays only gateway health and allows operation/path/template/success/output mappings. Result panel offers direct response extraction and read-only bounded result queries only. Use backend checklist and optimistic version conflict responses; do not persist SQL/JSON/test data in browser storage.

Convert the old reference page into a redirect/filter shortcut to `/integration/tasks?type=REFERENCE_QUERY`; remove its `!tasks.length` create guard and standalone SQL-resource options.

- [ ] **Step 4: Run frontend model test and production build**

Run: `npm run test -- --run src/views/integration/task/model.test.js`

Expected: PASS.

Run: `npm run build:prod`

Expected: PASS with the typed task pages included in the Vue build.

- [ ] **Step 5: Commit task-center UI**

```bash
git add data-platform-ui/src/api/integration/task.js data-platform-ui/src/api/integration/reference.js data-platform-ui/src/views/integration/task data-platform-ui/src/views/integration/reference/index.vue
git commit -m "feat: add configurable integration task workbench"
```

### Task 11: Expose checkpoint state in execution diagnostics and complete migration verification

**Files:**
- Modify: `data-platform-ui/src/api/integration/execution.js`
- Modify: `data-platform-ui/src/views/integration/execution/index.vue`
- Modify: `data-platform-ui/src/views/integration/execution/detail.vue`
- Modify: `bl-admin/src/main/java/com/ruoyi/web/controller/integration/IntegrationExecutionController.java`
- Modify: `sql/20260914_configurable_task_platform.sql`
- Test: `data-platform-ui/src/views/integration/execution/model.test.js`
- Test: `bl-integration/src/test/java/com/ruoyi/integration/execution/service/IntegrationExecutionFlowTest.java`

**Interfaces:**
- Consumes Task 6 execution details: revision/checksum, `lastCompletedStage`, `u8Confirmed`, `resumeMode`, and safe `resultOutputsJson`.
- Produces an explicit `continue post-process` action backed by the existing retry endpoint; the endpoint selects safe resume behavior server-side.

- [ ] **Step 1: Write failing status-label and retry-copy tests**

```js
it('labels PARTIAL_SUCCESS as U8 success awaiting result', () => {
  expect(statusLabel('PARTIAL_SUCCESS')).toBe('U8成功，结果待补全')
})

it('uses post-process wording rather than a repush warning', () => {
  expect(retryPrompt({ status: 'PARTIAL_SUCCESS' })).toContain('继续结果查询')
})
```

- [ ] **Step 2: Run the UI model test to verify it fails**

Run: `npm run test -- --run src/views/integration/execution/model.test.js`

Expected: FAIL because the UI does not know `PARTIAL_SUCCESS` or OA API trigger source.

- [ ] **Step 3: Implement safe operator diagnostics**

Show `OA_API` as `OA插件`, `PARTIAL_SUCCESS` as `U8成功，结果待补全`, direct extracted result outputs, pinned revision/checksum and the last completed stage. The retry action for partial success says `继续结果查询` and never promises a re-push; `RESULT_UNKNOWN` remains disabled. Stage payloads remain masked/truncated. Update the migration menu to add `integration:task:list`, `integration:task:edit`, `integration:task:publish`, and `integration:task:preview` under the existing integration root, plus the `任务中心` component route.

- [ ] **Step 4: Run UI build and backend end-to-end regression selection**

Run: `npm run test -- --run src/views/integration/execution/model.test.js`

Expected: PASS.

Run: `npm run build:prod`

Expected: PASS.

Run: `mvn -pl bl-integration,bl-admin -am test`

Expected: PASS; existing sales-outbound, reference, execution and new configurable-task tests all pass.

- [ ] **Step 5: Commit diagnostics and validation evidence**

```bash
git add sql/20260914_configurable_task_platform.sql bl-admin/src/main/java/com/ruoyi/web/controller/integration/IntegrationExecutionController.java data-platform-ui/src/api/integration/execution.js data-platform-ui/src/views/integration/execution
git commit -m "feat: surface resumable OA to U8 execution state"
```

## Final Verification

- [ ] Run `mvn -pl bl-integration,bl-admin -am test` from `data-platform-server`.
- [ ] Run `npm run build:prod` from `data-platform-ui`.
- [ ] Run `git status --short` in both repositories and confirm only task-related files are changed.
- [ ] Apply `sql/20260914_configurable_task_platform.sql` to a non-production clone, then verify it can be run a second time without schema or seed errors.
- [ ] Verify one OA-to-U8 task with direct response voucher output and one with a required bounded U8 read query: duplicate OA acceptance is deduplicated; U8 timeout is `RESULT_UNKNOWN`; required result-query exhaustion is `PARTIAL_SUCCESS`; retry of `PARTIAL_SUCCESS` does not emit another U8 business request.
- [ ] Verify the existing material reference remains synchronous and the existing U8-to-OA sales outbound task still runs through its code-managed handler.
