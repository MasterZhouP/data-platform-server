# Stage 1 Execution Foundation Implementation Plan

**Goal:** Deliver the smallest durable OA-to-U8 execution foundation: persisted asynchronous acceptance, stage logs, safe manual retry, optional SQL Server connectivity boundaries, and an administrator log page. Real OA business SQL, real U8 endpoints, and the A8 plugin remain outside Stage 1.

**Architecture:** `bl-integration` owns execution state, handler registration, pipeline orchestration, persistence and external connection boundaries. `bl-admin` owns authenticated HTTP controllers. MySQL is both the audit store and the durable queue for `PENDING` work. A dedicated executor gives low-latency dispatch, while a scanner recovers missed dispatches. Every runner claims work atomically and performs external work outside long MySQL transactions.

**Stack:** Java 17, Spring Boot 3.5, MyBatis, MySQL, Spring `TaskExecutor`, Spring `RestClient`, JUnit 5, Vue 3, Vite, Element Plus.

## Scope decisions

- The trigger contract accepts `taskCode`, `masterId`, optional `formId`, and optional `summaryId`. `businessKey` is computed by platform code after OA data is loaded.
- Stage 1 has no anonymous OA endpoint and no production business Handler. Automated tests register a test Handler and prove the complete path.
- A periodic scan dispatches only existing `PENDING` records. It never executes `FAILED` records and is not business retry.
- Manual retry creates a new execution and preserves the old execution. It accepts only the old execution ID and always causes the Handler to load current source data.
- A record that may already have caused a U8 side effect is `resultUnknown=true` and cannot be retried directly.
- U8 connectivity stays disabled and unverified because no test account is available.

## Task 1: Establish the test runtime

**Files:**

- Modify `pom.xml`
- Modify `bl-integration/pom.xml`
- Create `bl-integration/src/test/java/com/ruoyi/integration/ArchitectureSmokeTest.java`

**Steps:**

1. Add a failing JUnit 5 smoke test so Maven proves that tests are discovered.
2. Add explicit Surefire configuration and the integration module test dependency.
3. Add explicit MyBatis, JDBC, SQL Server runtime and validation dependencies required by production code.
4. Run `mvn -pl bl-integration -am test` and confirm the smoke test is executed.

## Task 2: Implement the domain contract and sensitive-data protection

**Files:**

- Create `bl-integration/src/main/java/com/ruoyi/integration/task/TriggerCommand.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/task/OaToU8PushHandler.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/task/PushExecutionContext.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/task/PushResult.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/task/PushFailureException.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/task/PushHandlerRegistry.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/execution/support/SensitiveDataMasker.java`
- Create tests beside the corresponding package under `src/test/java`

**Steps:**

1. Write tests for duplicate Handler codes, missing Handler codes, safe failures, unknown-result failures, JSON masking, plain-text masking, and payload length limits.
2. Run the tests and verify they fail for missing behavior.
3. Implement the smallest contracts and registry.
4. Implement centralized masking before any payload reaches persistence.
5. Re-run the focused tests until green.

## Task 3: Add execution and stage persistence

**Files:**

- Create domain classes and enums under `bl-integration/src/main/java/com/ruoyi/integration/execution/domain/`
- Create `bl-integration/src/main/java/com/ruoyi/integration/execution/mapper/IntegrationExecutionMapper.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/execution/mapper/IntegrationExecutionStageMapper.java`
- Create mapper XML files under `bl-integration/src/main/resources/mapper/integration/`
- Create `bl-integration/src/main/java/com/ruoyi/integration/execution/repository/ExecutionRepository.java`
- Create `bl-integration/src/main/java/com/ruoyi/integration/execution/repository/MyBatisExecutionRepository.java`
- Create mapper/XML contract tests under `src/test/java`

**Steps:**

1. Write tests for initial `PENDING/RECEIVED`, conditional claim, list/detail projection, append-only stages, success, safe failure and unknown-result failure.
2. Define the domain model and persistence port needed by application tests.
3. Implement MyBatis mappings with conditional state updates; no external call is wrapped in a database transaction.
4. Verify mapper XML parses and mapped statements match the Java mapper signatures.

## Task 4: Implement durable dispatch and the PushPipeline

**Files:**

- Create application services under `bl-integration/src/main/java/com/ruoyi/integration/execution/service/`
- Create pipeline classes under `bl-integration/src/main/java/com/ruoyi/integration/pipeline/push/`
- Create async configuration under `bl-integration/src/main/java/com/ruoyi/integration/config/`
- Create application tests with an in-memory repository and test Handler

**Steps:**

1. Write tests that prove acceptance persists before dispatch, transaction rollback does not dispatch, a duplicate Runner claim executes once, and rejection leaves the record `PENDING`.
2. Write a test that simulates a missed immediate dispatch and proves the scanner later executes the same `PENDING` record.
3. Write a test that proves the scanner never executes `FAILED`.
4. Implement after-commit notification, the dedicated bounded executor, PENDING scanner and atomic Runner claim.
5. Implement short-transaction stage recording and final status updates.
6. Add stale `RUNNING` recovery classification without automatically replaying it.

## Task 5: Implement safe manual retry

**Files:**

- Modify the execution application service, repository and mapper files from Tasks 3-4
- Add retry tests under `bl-integration/src/test/java/com/ruoyi/integration/execution/service/`

**Steps:**

1. Write tests for rejected retry of `PENDING`, `RUNNING`, `SUCCESS`, non-retryable failure and unknown-result failure.
2. Write a concurrency/idempotency test proving two retry attempts produce one child execution.
3. Write a test Handler whose source value changes between attempts and prove the retry uses the new value while the original request/response remains unchanged.
4. Implement row locking, rule validation, new child creation, retry counter and dedup-key transfer in one short transaction.
5. Dispatch the child only after commit and return its new execution ID immediately.

## Task 6: Add optional OA/U8 connection and Client boundaries

**Files:**

- Create configuration properties and conditional beans under `bl-integration/src/main/java/com/ruoyi/integration/datasource/`
- Create `OaConnectionProbe` under `bl-integration/src/main/java/com/ruoyi/integration/client/oa/`
- Create the typed U8 Client contract under `bl-integration/src/main/java/com/ruoyi/integration/client/u8/`
- Modify `bl-admin/src/main/resources/application.yml`
- Add configuration tests

**Steps:**

1. Write tests that disabled external data sources create no connection beans and missing secrets do not prevent startup.
2. Implement independent named SQL Server `DataSource` and JDBC templates, separate from the existing MySQL dynamic data source.
3. Add OA `SELECT 1` probing with a read-only configuration expectation.
4. Define typed U8 outcomes for success, business failure, pre-send failure and result unknown. Keep U8 disabled.
5. Add environment-variable placeholders only; never commit credentials.

## Task 7: Add authenticated management APIs

**Files:**

- Create `bl-admin/src/main/java/com/ruoyi/web/controller/integration/IntegrationExecutionController.java`
- Create focused controller tests under `bl-admin/src/test/java/com/ruoyi/web/controller/integration/`
- Add `spring-boot-starter-test` to `bl-admin/pom.xml` if the controller test requires it

**Steps:**

1. Write tests for list, detail/not-found, retry success and retry conflict/error mapping.
2. Implement `/integration/execution/list`, `/integration/execution/{id}`, and `/integration/execution/{id}/retry`.
3. Apply `integration:execution:list/query/retry` permissions and existing operation logging to retry.
4. Return backend-computed `retryable` and `retryBlockReason`; never accept edited identifiers in the retry request.

## Task 8: Add incremental MySQL and menu SQL

**Files:**

- Create `sql/20260904_stage1_integration.sql`
- Create `doc/stage-1-database-verification.md`

**Steps:**

1. Add non-destructive, explicit-column DDL for `int_execution` and `int_execution_stage` with required indexes and unique constraints.
2. Add idempotent menu and permission inserts for “数据交换 / 执行日志 / 查看 / 重试”.
3. Validate the script against the configured development MySQL without printing credentials.
4. Query `information_schema` to record table, column and index evidence in the verification document.

## Task 9: Build the administrator execution-log page

**Files in `data-platform-ui`:**

- Create `src/api/integration/execution.js`
- Create `src/views/integration/execution/index.vue`
- Create `src/views/integration/execution/detail.vue`

**Steps:**

1. Implement filters for task code, master ID, business key, status and date range.
2. Show newest executions first with status, current stage, error summary and retry relationship.
3. Add a wide detail drawer with identifiers, failure reason, masked request/response and stage timeline.
4. Enable the retry action only from the backend decision and with `integration:execution:retry` permission.
5. Confirm retry text states that identifiers remain fixed, current OA data is reloaded, and a new execution record is created.
6. Build with Node 24.20.0 using `npm run build:prod`.

## Task 10: Verify the complete Stage 1 deliverable and update the baseline

**Files:**

- Modify `README.md` or module README only where usage instructions are needed
- Create `doc/stage-1-acceptance.md`
- Modify `D:/Person_knowlegebase/my-llm-wiki/01-项目/data-platform-数据交换平台/00-项目总方案与开发基线.md`

**Steps:**

1. Run focused tests, then `mvn -B clean package` for all backend modules.
2. Run the front-end production build.
3. Inspect packaged JARs to confirm mapper XML and integration classes are included.
4. Review tracked changes for credentials and accidental business implementation.
5. Record exact passing evidence and limitations: OA connectivity remains pending until local credentials are supplied; U8 connectivity and real endpoints remain unverified; no A8 plugin or real business Handler exists in Stage 1.
6. Update the project baseline from “Stage 1 not implemented” to the exact verified outcome, while keeping Stage 2 unimplemented.
