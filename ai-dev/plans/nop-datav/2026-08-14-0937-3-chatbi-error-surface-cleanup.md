# 3 ChatBI Error Surface Cleanup

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Source: `ai-dev/backlog/nop-datav-audit-followups.md` items #13, #14, #15, #16 (AR-3, AR-4, AR-5, AR-6) + item #5 chatbi side (Dim09-04); original audit `ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md`
> Mission: nop-datav
> Work Item: ChatBI error contract & observability remediation (P2 audit backlog sweep — ChatBI subsystem)
> Related: `2026-08-14-0937-2-d5-report-alert-reliability-defects.md` (sibling plan, D5 subsystem)

## Purpose

Fix 5 confirmed audit findings in the ChatBI subsystem that affect error observability (unhandled CompletionException leak, double-silent-swallow), error-code contract correctness (wrong param name, dead ErrorCode), and code duplication (inlined constant with false justification). All items are backend-only, not blocked on flux.

## Current Baseline

Live code verified 2026-08-14:

- **AR-6 (CONFIRMED CONTRACT DRIFT + DEAD CODE)**: `ChatBiToolCallingLoop.java:139-140` — `toolManager.callTool(name, aiToolCall, context).join()` has no try/catch. A failed CompletableFuture stage throws `CompletionException` which escapes `chatToQuery`/`chatToDashboard`/`chatToScreen` (public GraphQL actions) unwrapped. Additionally, `.join()` on a cancelled future throws `CancellationException` directly (not wrapped in `CompletionException`). At `:151`, handler failures are swallowed with `catch (Exception ignore)` and no logging. **The class has no `Logger LOG` field** (verified: fields at `:64-66` are `chatService/toolManager/cancelToken` only; no `org.slf4j` import). `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` is defined at `NopDatavErrors.java:467-471` with params `ARG_TOOL_NAME` (`:450`) and `ARG_REASON` (`:18`) but referenced by zero call sites (Dim09-04 dead code).

- **AR-4 (CONFIRMED CONTRACT DRIFT)**: `DatavQueryDatasetExecutor.java:166-168` — catch block throws `new NopException(ERR_DATAV_QUERY_FAILED).param("panelId", datasetSid).cause(e)`. There is no panel in the ChatBI dataset-query path. `ERR_DATAV_QUERY_FAILED` (`NopDatavErrors.java:103-107`) message reads "Dataset query execution failed for panel: {panelId}" and is **shared by 3 other callers in the panel-data path** (`PanelDataBinder.java:155`, `PanelSqlBuilder.java:44/53`) where `panelId` is semantically correct. Generalizing the ErrorCode to accept both `panelId` and `datasetSid` would pollute those 3 callers' error messages. `ARG_DATASET_SID` exists at `NopDatavErrors.java:453` and is already used by sibling ChatBI ErrorCodes (`:482/:488/:505`).

- **AR-5 (CONFIRMED CODE DUPLICATION)**: `DatavGenerateDashboardExecutor.java:472-474` and `DatavGenerateScreenExecutor.java:612-614` both define inner class `NopOperatorFallback { static final String SYSTEM_OPERATOR = "system"; }` with a comment claiming "避免循环依赖 NopDatavOperatorResolver." The canonical constant `NopDatavOperatorResolver.SYSTEM_OPERATOR` (`:11`) is in the same module (`io.nop.datav.service`), a stateless static utility with no incoming dependency from the chatbi package — circular dependency is impossible. Both executors are in package `io.nop.datav.service.chatbi`, so adding `import io.nop.datav.service.NopDatavOperatorResolver;` is straightforward.

- **AR-3 (CONFIRMED DEFECT)**: `DatavGenerateScreenExecutor.java:520-529` — `isUniqueConstraintViolation` is `private static` and matches `lower.contains("constraint")` which also matches CHECK/FK/NOT NULL violation messages from H2/MySQL/Postgres. A non-UK constraint failure would be misreported to the LLM as `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME`, causing rename-retry loops until `maxIterations`. Verified: H2 UK message contains "Unique index or primary key violation" (matches "unique"), MySQL contains "Duplicate entry" (matches "duplicate"), PG contains "duplicate key value violates unique constraint" (matches both) — so retaining only `"unique"`/`"duplicate"` disjuncts covers UK detection across dialects.

## Goals

- ChatBiToolCallingLoop wraps `.join()` in try/catch, unwraps `CompletionException`/`CancellationException`, and throws `NopException(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED)` with both `ARG_TOOL_NAME` and `ARG_REASON` — retiring the dead ErrorCode
- Handler-swallow blocks log at DEBUG level so silent failures become traceable
- DatavQueryDatasetExecutor uses a dedicated `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` ErrorCode with `ARG_DATASET_SID` (not the panel-path shared `ERR_DATAV_QUERY_FAILED`)
- Both generate-executors reference `NopDatavOperatorResolver.SYSTEM_OPERATOR` instead of inlined duplicates
- `isUniqueConstraintViolation` narrowed to only match `"unique"`/`"duplicate"` substrings

## Non-Goals

- Dead/orphan module cleanup (`nop-datav-core`, `nop-datav-chart`) — separate concern (AR-1/AR-2)
- `DatavListDatasetsExecutor.findAll()` memory optimization (AR-7) — performance optimization
- Minor nits (unused `prefix` param in `DatavGenerateDashboardExecutor.generateId`, double `parseNonStrict` call in `QUERY_HANDLER`) — cosmetic
- D5 report/alert reliability defects (separate plan `2026-08-14-0937-2`)
- ChatBI functional enhancements (new tools, prompt engineering)

## Scope

### In Scope

- `ChatBiToolCallingLoop.java` — add Logger field; wrap `.join()`; add DEBUG logging to handler-swallow; use `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` with both params (AR-6, Dim09-04)
- `DatavQueryDatasetExecutor.java` — replace `ERR_DATAV_QUERY_FAILED`/`panelId` with new `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED`/`ARG_DATASET_SID` (AR-4)
- `DatavGenerateDashboardExecutor.java` — replace `NopOperatorFallback.SYSTEM_OPERATOR` with `NopDatavOperatorResolver.SYSTEM_OPERATOR`, delete inner class, add import (AR-5)
- `DatavGenerateScreenExecutor.java` — same SYSTEM_OPERATOR replacement + narrow `isUniqueConstraintViolation` (AR-5, AR-3)
- `NopDatavErrors.java` — define `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` (AR-4)

### Out Of Scope

- `nop-datav-core` orphaned module resolution (AR-1)
- `nop-datav-chart` empty module + BOM bypass (AR-2)
- `DatavListDatasetsExecutor` full-table-load optimization (AR-7)
- `NopDatavExportTaskBizModel.createExportTask` error code (Dim09-03)
- Export-side `ERR_DATAV_EXPORT_FAILED` (already retired by plan `2026-08-10-2025-2`)

## Execution Plan

### Phase 1 — ChatBiToolCallingLoop Error Handling (AR-6, Dim09-04)

Status: completed
Targets: `ChatBiToolCallingLoop.java`, `NopDatavErrors.java`

- Item Types: `Fix`

- [x] Add `private static final Logger LOG = LoggerFactory.getLogger(ChatBiToolCallingLoop.class);` field + import `org.slf4j.Logger` / `org.slf4j.LoggerFactory` (the class currently has no logger — verified)
- [x] Add static imports: `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`, `ARG_TOOL_NAME`, `ARG_REASON` from `NopDatavErrors`; add regular imports `java.util.concurrent.CompletionException`, `java.util.concurrent.CancellationException`
- [x] Wrap `toolManager.callTool(...).join()` (line ~139-140) in try/catch:
  - Catch both `CompletionException` and `CancellationException` (`.join()` throws the latter directly on cancelled futures, unwrapped)
  - For `CompletionException`: unwrap with a while-loop to the first non-`CompletionException` cause (`while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();`) to handle chained futures
  - Build reason string: `String reason = underlying.getMessage() != null ? underlying.getMessage() : underlying.getClass().getName();`
  - Throw `new NopException(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED).param(ARG_TOOL_NAME, chatToolCall.getName()).param(ARG_REASON, reason).cause(underlying)`
- [x] In the handler-swallow `catch (Exception ignore)` block (line ~151), add `LOG.debug("nop.datav.chatbi.tool-handler-failed: tool={}", chatToolCall.getName(), ignore);`
- [x] In the QUERY_HANDLER internal swallow block (if present at ~221-223), apply the same `LOG.debug` treatment
- [x] Verify `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` ErrorCode definition (`NopDatavErrors.java:467-471`) already includes both `ARG_TOOL_NAME` and `ARG_REASON` in its params (verified: it does)

Exit Criteria:

- [x] `ChatBiToolCallingLoop.java` has a `Logger LOG` field with slf4j imports (repo-observable)
- [x] Line ~139-140 has try/catch around `.join()` that catches `CompletionException` and `CancellationException`, unwraps, and throws `NopException(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED)` with both `.param(ARG_TOOL_NAME, ...)` and `.param(ARG_REASON, ...)` (repo-observable)
- [x] No `catch (Exception ignore)` block without at least `LOG.debug` remains in ChatBiToolCallingLoop (repo-observable)
- [x] `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` is referenced by at least one call site (grep confirms, dead-code status retired)
- [x] New focused test in a new `TestChatBiToolCallingLoop.java` (pure unit test, mock `IChatService` + `IToolManager`): mock `toolManager.callTool` returning a failed CompletableFuture (e.g. `CompletableFuture.failedFuture(new RuntimeException("infra"))`) → assert `NopException` with `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` thrown (not raw `CompletionException`); assert `getParam(ARG_TOOL_NAME)` and `getParam(ARG_REASON)` are populated
- [x] Existing ChatBI E2E tests pass (no regression)
- [x] **无静默跳过**: the `.join()` failure now throws a structured exception (not silent); handler-swallow logs at DEBUG (not fully silent)
- [x] No owner-doc update required (error handling is internal contract; ai-design.md describes tool execution flow but not exception specifics)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — ErrorCode Param & Constant Cleanup (AR-4, AR-5)

Status: completed
Targets: `DatavQueryDatasetExecutor.java`, `DatavGenerateDashboardExecutor.java`, `DatavGenerateScreenExecutor.java`, `NopDatavErrors.java`, `NopDatavOperatorResolver.java`

- Item Types: `Fix`, `Decision`

- [x] **Decision (AR-4)**: Define a new dedicated `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` ErrorCode in `NopDatavErrors.java` with message "ChatBI dataset query execution failed for dataset: {datasetSid}, reason: {reason}" and params `ARG_DATASET_SID`, `ARG_REASON`. **Rejected alternative**: generalizing `ERR_DATAV_QUERY_FAILED` to accept both `panelId` and `datasetSid` — this would pollute the 3 panel-path callers (`PanelDataBinder:155`, `PanelSqlBuilder:44/53`) whose error messages would render `{datasetSid}` as a literal or empty string.
- [x] **Fix (AR-4)**: In `DatavQueryDatasetExecutor.java:166-168`, replace `ERR_DATAV_QUERY_FAILED` + `.param("panelId", datasetSid)` with `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` + `.param(ARG_DATASET_SID, datasetSid).param(ARG_REASON, e.getMessage() != null ? e.getMessage() : e.getClass().getName()).cause(e)`. **Preserve the existing `.cause(e)`** — the underlying exception's stacktrace is critical for debugging query failures. Add static import for `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` and `ARG_DATASET_SID`.
- [x] **Fix (AR-5)**: In `DatavGenerateDashboardExecutor.java`, replace `NopOperatorFallback.SYSTEM_OPERATOR` (line ~163) with `NopDatavOperatorResolver.SYSTEM_OPERATOR`; add `import io.nop.datav.service.NopDatavOperatorResolver;`; delete the inner class `NopOperatorFallback` (lines ~469-474)
- [x] **Fix (AR-5)**: In `DatavGenerateScreenExecutor.java`, replace `NopOperatorFallback.SYSTEM_OPERATOR` (line ~450) with `NopDatavOperatorResolver.SYSTEM_OPERATOR`; add same import; delete inner class `NopOperatorFallback` (lines ~611-614)

Exit Criteria:

- [x] `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` defined in `NopDatavErrors.java` with `ARG_DATASET_SID` + `ARG_REASON` params (repo-observable)
- [x] `DatavQueryDatasetExecutor.java` no longer references `ERR_DATAV_QUERY_FAILED` or passes `datasetSid` as `panelId` (repo-observable: grep for `ERR_DATAV_QUERY_FAILED` in this file returns nothing)
- [x] No `NopOperatorFallback` inner class exists in either executor file (repo-observable via grep)
- [x] Both executor files import and reference `NopDatavOperatorResolver.SYSTEM_OPERATOR` (repo-observable)
- [x] Existing ChatBI generate tests pass (operator resolution behavior unchanged — same constant value `"system"`)
- [x] **无静默跳过**: no behavioral change (same constant value, same error handling pattern) — this is a code-cleanup phase, no new silent paths
- [x] No owner-doc update required (internal constant consolidation + ErrorCode split, no public contract change)
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Constraint Violation Narrowing (AR-3)

Status: completed
Targets: `DatavGenerateScreenExecutor.java`

- Item Types: `Fix`

- [x] In `isUniqueConstraintViolation` (line ~520-529), remove the `"constraint"` and `"uk_"` disjuncts. Retain only `"unique"` and `"duplicate"` which are specific to UK violations across H2/MySQL/Postgres (verified: H2 "Unique index or primary key violation", MySQL "Duplicate entry", PG "duplicate key value violates unique constraint" — all contain "unique" or "duplicate").

Exit Criteria:

- [x] `isUniqueConstraintViolation` method body does not contain `.contains("constraint")` or `.contains("uk_")` (repo-observable)
- [x] New focused test: simulate a non-UK constraint error through the `executeAsync` public path — mock `IOrmTemplate.runInSession` (or equivalent save path) to throw an exception whose message contains "Check constraint violation" → assert the tool result error is NOT `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME` (i.e. the raw exception message is passed through for the LLM to read). This tests via the public entry point, not by calling the `private static` method directly. If mocking `IOrmTemplate` proves impractical, alternative: make `isUniqueConstraintViolation` package-private (test-visible) and test directly — document the chosen approach.
- [x] Existing screen-generation tests pass (UK case still correctly detected via "unique"/"duplicate")
- [x] **无静默跳过**: non-UK constraint errors now propagate as raw error messages to the LLM (not misclassified); the LLM can read the actual message and respond appropriately
- [x] No owner-doc update required (internal heuristic, no public contract change; ai-design.md does not document the substring matching strategy)
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-6 fixed: ChatBiToolCallingLoop wraps `.join()`, throws structured NopException with both ARG_TOOL_NAME + ARG_REASON; handler-swallow blocks log at DEBUG; Logger field added
- [x] AR-4 fixed: DatavQueryDatasetExecutor uses dedicated ERR_DATAV_CHATBI_DATASET_QUERY_FAILED with ARG_DATASET_SID (not shared panel-path ErrorCode)
- [x] AR-5 fixed: both executors reference NopDatavOperatorResolver.SYSTEM_OPERATOR; NopOperatorFallback deleted from both files
- [x] AR-3 fixed: isUniqueConstraintViolation no longer matches generic constraint errors
- [x] Dim09-04 resolved: ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED is now used (not dead code)
- [x] No raw `CompletionException`/`CancellationException` leak from ChatBiToolCallingLoop
- [x] No `NopOperatorFallback` inner class in any file
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 `.join()` try/catch actually catches and wraps (focused test with failed CompletableFuture), not just compiles; constraint narrowing verified with non-UK test case
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### nop-datav-core orphaned module (AR-1)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Module hygiene decision (add-to-build vs delete) requires broader impact analysis across the module graph and codegen pipeline. Not related to ChatBI error surface. Tracked in `ai-dev/backlog/nop-datav-audit-followups.md` item #11.
- Successor Required: `no`

### nop-datav-chart empty module + BOM bypass (AR-2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Module deletion or population is a structural decision separate from error contract cleanup. Tracked in `ai-dev/backlog/nop-datav-audit-followups.md` item #12.
- Successor Required: `no`

### DatavListDatasetsExecutor full-table-load (AR-7)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Performance optimization for 10x dataset scale; no correctness break at current scale. Tracked in `ai-dev/backlog/nop-datav-audit-followups.md` item #17.
- Successor Required: `no`

## Non-Blocking Follow-ups

- Minor nits: unused `prefix` param in `DatavGenerateDashboardExecutor.generateId` (:392-397); double `parseNonStrict` call in `QUERY_HANDLER` (:203-204)
- `nop-datav-api` empty module legitimacy (recorded in open-audit minor nits, no action needed)

## Closure

Status Note: 5 个 ChatBI 子系统审计发现（AR-3/AR-4/AR-5/AR-6/Dim09-04）全部修复并落地。AR-6 的 `.join()` 不再泄漏原始 CompletionException/CancellationException，handler-swallow 不再双层静默吞；AR-4 不再误用 panel 路径 ErrorCode；AR-5 删除伪理由内联常量；AR-3 收窄 UK 启发式。全部 in-scope 项已 landed，deferred 项（AR-1/AR-2/AR-7）已裁定为 non-blocking out-of-scope/optimization。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，explore 类型，task_id ses_0013f56ddffeWzbt54UqBiElrZ）
- Audit Session: ses_0013f56ddffeWzbt54UqBiElrZ
- Evidence:
  - Phase 1 Exit Criteria — 全 PASS：Logger 字段 `ChatBiToolCallingLoop.java:72`；`.join()` try/catch 双重捕获 + 解包 + 三参 NopException `:150-167`；两处 swallow 均有 `LOG.debug` `:180/:251`；`ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` 接线 `:33/:163`（Dim09-04 死码退役）；`TestChatBiToolCallingLoop.java` 3 用例（failedFuture/cancelled/chained）断言 errorCode+toolName+reason。
  - Phase 2 Exit Criteria — 全 PASS：`ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` 定义 `NopDatavErrors.java:511-515`（ARG_DATASET_SID+ARG_REASON）；`DatavQueryDatasetExecutor.java` 仅注释提及旧码（无 code 引用），新码 + `.cause(e)` 保留 `:170-173`；`NopOperatorFallback` 全模块 0 引用；两 executor 引用 `NopDatavOperatorResolver.SYSTEM_OPERATOR` `:164/:451`。
  - Phase 3 Exit Criteria — 全 PASS：`isUniqueConstraintViolation` 仅含 `unique`/`duplicate` `DatavGenerateScreenExecutor.java:531`（无 `constraint`/`uk_` 可执行 disjunct）；`TestDatavGenerateScreenUkHeuristic.java` 9 用例（CHECK/FK/NOT NULL/bare 判 false；H2/MySQL/PG 判 true；null 判 false）。
  - Anti-Hollow 检查：catch 块 `:153` 以 `throw new NopException(...)` `:163-166` 结束，无静默 continue/return；`ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` 真实接线（`DatavQueryDatasetExecutor.java:34/:170`）。端到端：`TestNopDatavChatBiE2E` 4 用例 + generate/screen E2E 全绿（运行时调用链连通）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（Closure Evidence 已写入）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（1 high = NotificationSender:467 注释 false-positive，pre-existing，非本次引入）。
  - `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests`：BUILD SUCCESS。
  - `./mvnw test -pl nop-datav/nop-datav-service`：421 tests, 0 failures, 0 errors（nop-auth-service TestBeanLoader 1 失败为 pre-existing 隔离确认无关）。
  - Deferred 项分类检查：AR-1/AR-2 = out-of-scope improvement；AR-7 = optimization candidate；均附 Why Not Blocking，无 in-scope live defect 被降级。

Follow-up:

- no remaining plan-owned work（AR-1/AR-2/AR-7 已裁定为 non-blocking，记录于 `ai-dev/backlog/nop-datav-audit-followups.md` #11/#12/#17）
