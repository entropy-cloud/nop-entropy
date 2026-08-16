> Audit Status: triaged
> Audit Type: open-ended
> Mission: nop-datav
> Remediation: all findings are P2 (no P0/P1) → triaged to follow-up backlog `ai-dev/backlog/nop-datav-audit-followups.md`；no remediation plan drafted (terminal non-open state).

# nop-datav Open-Ended Adversarial Audit

- **Audit target**: `nop-datav/` (8-piece Maven module group + orphan `nop-datav-core`; 15 ORM entities; ChatBI / linkage / filter / export / report / alert / screen subsystems)
- **Audit baseline**: LIVE CODE in the `nop-entropy-feat-nop-datav` worktree (2026-08-10).
- **Methodology**: `ai-dev/skills/open-ended-adversarial-review-prompt.md` (discovery-oriented, no fixed checklist). After reading the prompt and `AGENTS.md` in full, the auditor read the module's source/config/tests/design and the sibling multi-dimensional audit (`2026-08-10-1516-multi-audit-nop-datav.md`) to **de-duplicate**; only findings NOT already covered there are reported below.
- **Perspectives leveraged**: 死代码清道夫 (dead-code scavenger), 代码生成受害者 (codegen victim), 异常路径侦探 (exception-path detective), 10x 规模运维者 (10x-scale ops), 模型攻击者 (model attacker). Stated because several findings came from these heuristics.

## Severity distribution

| Severity | Count | Main categories |
|----------|-------|-----------------|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 7 | dead/orphan modules; misleading error context; over-broad exception classification; duplicated constants with false justification; silent-swallow amplification; dead parameter; 10x-scale memory load |

> All findings this round are P2. Per the mission-driver rule, a P2-only audit triages to the follow-up backlog without a remediation plan. The most consequential thread (dead `nop-datav-core` + duplicated status constants) is a latent single-source-of-truth erosion worth scheduling even though no current behavior breaks.

---

## Findings

### [AR-1] `[P2]` `nop-datav-core` is an orphaned, never-built module carrying 261 lines of generated status constants that the service layer silently duplicates by hand — single-source-of-truth has already eroded

- **Files**:
  - `nop-datav/pom.xml:19-28` (parent `<modules>` lists chart/api/codegen/dao/meta/service/web/app — **`nop-datav-core` absent**)
  - `nop-datav/nop-datav-core/src/main/java/io/nop/datav/core/NopDatavCoreConstants.java:3` (retention shell `extends _NopDatavCoreConstants`)
  - `nop-datav/nop-datav-core/src/main/java/io/nop/datav/core/_NopDatavCoreConstants.java:1-261` (generated: `PUBLISH_STATUS_DRAFT=0`, `EXPORT_STATUS_PENDING=0/RUNNING=10/...`, `DELIVERY_STATUS_*`, `SCREEN_ADAPTOR_*`, `ALERT_*`, …)
  - Duplicating sites (service, hand-written, same values): `nop-datav-service/.../export/NopDatavExportTaskStatus.java:9-13`, `.../report/NopDatavReportDeliveryStatus.java`, `.../report/NopDatavReportTaskStatus.java`, `.../alert/NopDatavAlertStateValue.java`, `.../screen/ScreenAdaptorMode.java`, `.../chatbi/DatavGenerateDashboardExecutor.java:69-71`, `.../chatbi/DatavGenerateScreenExecutor.java:74-78`
- **Evidence**:
  ```xml
  <!-- nop-datav/pom.xml:19-28 — core is NOT a submodule -->
  <modules>
      <module>nop-datav-chart</module>
      <module>nop-datav-api</module>
      <module>nop-datav-codegen</module>
      <module>nop-datav-dao</module>
      <module>nop-datav-meta</module>
      <module>nop-datav-service</module>
      <module>nop-datav-web</module>
      <module>nop-datav-app</module>
  </modules>
  ```
  ```java
  // nop-datav-core/_NopDatavCoreConstants.java:87-109 — generated, canonical
  int EXPORT_STATUS_PENDING = 0;
  int EXPORT_STATUS_RUNNING = 10;
  int EXPORT_STATUS_SUCCEEDED = 20;
  int EXPORT_STATUS_FAILED = 30;
  int EXPORT_STATUS_CANCELLED = 40;
  ```
  ```java
  // nop-datav-service/.../NopDatavExportTaskStatus.java:9-13 — hand-written duplicate, the LIVE one
  public static final int PENDING = 0;
  public static final int RUNNING = 10;
  public static final int SUCCEEDED = 20;
  public static final int FAILED = 30;
  public static final int CANCELLED = 40;
  ```
  Cross-checks (rg): `nop-datav-core` appears in NO pom `<dependency>` (only its own pom + the parent modules list that omits it), NOT in `nop-bom/pom.xml`, and `NopDatavCoreConstants` is referenced by ZERO files under `nop-datav-service/`.
- **Severity**: P2 — no current behavioral break (hand-written values match the dict today). But the platform's model-first promise is that dict→codegen yields a single shared constants interface (`_NopDatavCoreConstants`); here that artifact exists but is unreachable, so every status int is re-typed by hand in the service layer. Justification: real maintainability/drift hazard, latent only.
- **Current**: `nop-datav-core/` ships source (the generated constants + the retention shell) but is never compiled, never installed, never depended on. The service module reinvents the same constants as ad-hoc final-int classes.
- **Risk**: the next dict-value change (e.g. inserting a status) regenerates `_NopDatavCoreConstants` to the correct value, but the hand-written service enums silently keep the old magic numbers — a status-comparison bug that is invisible until a transition misfires. Future devs will also "fix" the duplication by reintroducing a dependency on `nop-datav-core`, only to find it isn't a built artifact.
- **Recommendation**: Either (a) add `nop-datav-core` to the parent `<modules>` and have service depend on it, then replace the hand-written status classes with references to `NopDatavCoreConstants` (restoring single-source-of-truth); or (b) if the constants interface was intentionally abandoned, delete `nop-datav-core/` entirely and mark the service-layer enums as the canonical source in `docs-for-ai/02-core-guides/model-first-development.md`. Do not leave both.
- **Confidence**: certain
- **Discovery source**: 死代码清道夫 + 代码生成受害者
- **De-dup note**: the multi-audit's module-overview (its §"Dimensions checked and CLEAN") did not mention `nop-datav-core` at all; it is an entirely uncovered dead artifact.

---

### [AR-2] `[P2]` `nop-datav-chart` is an empty shell (zero source, zero resources) whose only content is a BOM-bypassing hardcoded `poi:5.4.0` dependency — corrects the multi-audit's "holds chart-rendering assets" claim

- **Files**: `nop-datav/nop-datav-chart/pom.xml:1-27`; the entire `nop-datav-chart/` tree
- **Evidence**:
  ```
  $ find nop-datav/nop-datav-chart -type f -not -path "*/target/*"
  nop-datav/nop-datav-chart/pom.xml        <-- the ONLY file
  ```
  ```xml
  <!-- nop-datav-chart/pom.xml:18-24 — poi version hardcoded, bypasses nop-bom dependencyManagement -->
  <dependencies>
      <dependency>
          <groupId>org.apache.poi</groupId>
          <artifactId>poi</artifactId>
          <version>5.4.0</version>
      </dependency>
  </dependencies>
  ```
  Cross-check (rg `nop-datav-chart`): referenced ONLY by its own pom and the parent `<modules>` list. No module's `<dependency>` block pulls it in.
- **Severity**: P2 — zero runtime impact today (nothing depends on it, nothing is inside it). But the module is built on every `./mvnw install`, pulling `poi:5.4.0` + its transitive tree into the local repo at a version not pinned by `nop-bom`, and the multi-audit's blind-spot self-assessment ("chart submodule holds chart-rendering assets outside the audited surface") is factually wrong — there are no assets. Justification: dead module + BOM bypass; cosmetic-to-maintenance.
- **Current**: `nop-datav-chart` compiles to nothing, ships nothing, and is consumed by nothing. The `poi` dep is the sole reason the module exists on the classpath build graph.
- **Risk**: (a) Maintainers believe chart rendering lives here and direct future work into it, creating a phantom component; (b) the hardcoded `poi:5.4.0` drifts from whatever poi version `nop-bom`/`nop-report` standardizes, and if anyone later adds code here they inherit an unmanaged version; (c) every CI build spins up an empty module.
- **Recommendation**: Delete `nop-datav-chart` from the parent `<modules>` (and delete the directory), OR populate it with the intended chart code and move the `poi` version into `nop-bom`. If kept as a placeholder, add a one-line README stating it is intentionally empty pending future chart work.
- **Confidence**: certain
- **Discovery source**: 死代码清道夫
- **De-dup note**: directly contradicts the multi-audit's closing blind-spot statement; reported because the existing description is inaccurate and the BOM bypass is a new fact.

---

### [AR-3] `[P2]` `DatavGenerateScreenExecutor.isUniqueConstraintViolation` classifies ANY constraint error as a duplicate screen name — over-broad substring match ("constraint"/"uk_" with no anchoring) sends the LLM down a wrong retry path on unrelated failures

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateScreenExecutor.java:520-529` (fallback invoked at `:236-244`)
- **Evidence**:
  ```java
  // :520-529
  private static boolean isUniqueConstraintViolation(Exception e) {
      String msg = e.getMessage();
      if (msg == null) {
          return false;
      }
      String lower = msg.toLowerCase();
      // H2 / common JDBC UK violation patterns
      return lower.contains("unique") || lower.contains("duplicate")
              || lower.contains("uk_") || lower.contains("constraint");
  }
  ```
  ```java
  // :236-244 — any true verdict is reported to the LLM as DUPLICATE_SCREEN_NAME
  } catch (Exception e) {
      if (isUniqueConstraintViolation(e)) {
          return FutureHelper.success(errorResult(call, new ValidationError(
                  ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME,
                  ARG_SCREEN_NAME, screenName)));
      }
      return FutureHelper.success(AiToolCallResult.errorResult(call.getId(), e.toString()));
  }
  ```
- **Severity**: P2 — active incorrect classification in a failure path, but the realistic trigger set is currently narrow (the executor fills every mandatory column, so only the screenName UK is likely to fire). Justification: real defect, low present-day likelihood, but the LLM-directed feedback loop makes the user-visible symptom (infinite rename-retry until `maxIterations`) notably bad once any non-UK constraint ever fires.
- **Current**: The `existsScreenByName` pre-check (`:211`) already handles the intended UK case before save; the catch is a race/DB-driver-portability fallback. The substring `"constraint"` matches CHECK / FK / NOT NULL violation messages from H2/MySQL/Postgres alike (e.g. H2 `"Check constraint violation"`, `"Referential integrity constraint violation"`), all of which would be labelled `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME`.
- **Risk**: A future schema change (NOT NULL column, CHECK, FK) or a DB trigger causes `screenDao.saveEntityDirectly` to throw → the LLM is told "duplicate screenName" → it renames and retries → same failure → loop burns `maxIterations` → user sees `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED` with no hint of the real cause. Operators debugging from logs see the same misleading code.
- **Recommendation**: Either (a) drop the substring heuristic and rely on the pre-check + rethrow the raw exception as `errorResult(call.getId(), e.toString())` (let the LLM read the actual message); or (b) narrow the match to UK-specific SQLState codes (e.g. `23000`/`23505`) via `SQLException.getSQLState()` rather than message scraping. At minimum remove the `"constraint"` and `"uk_"` disjuncts which are the over-broad ones.
- **Confidence**: likely (the over-broad match is certain; the likelihood of hitting a non-UK constraint in the current schema is low, hence P2 not P1)
- **Discovery source**: 异常路径侦探

---

### [AR-4] `[P2]` `DatavQueryDatasetExecutor` reports dataset-query failures with `ERR_DATAV_QUERY_FAILED` whose only param is `{panelId}` — there is no panel in the ChatBI query path, so the error context is semantically wrong

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavQueryDatasetExecutor.java:163-169`; ErrorCode definition `nop-datav-service/.../NopDatavErrors.java:101-105`
- **Evidence**:
  ```java
  // DatavQueryDatasetExecutor:163-169 — "panelId" receives a datasetSid
  } catch (NopException e) {
      throw e;
  } catch (Exception e) {
      throw new NopException(ERR_DATAV_QUERY_FAILED)
              .param("panelId", datasetSid)   // <-- there is no panel here
              .cause(e);
  }
  ```
  ```java
  // NopDatavErrors:101-105 — the code's message template only knows {panelId}
  ErrorCode ERR_DATAV_QUERY_FAILED = define(
          "nop.err.datav.query-failed",
          "Dataset query execution failed for panel: {panelId}",
          ARG_PANEL_ID);
  ```
- **Severity**: P2 — the error is ultimately caught by the executor's outer handler (`:114-116`) and flattened to `e.toString()` for LLM consumption, so no structured param leaks to a caller. But the message rendered ("…failed for panel: <datasetSid>") is misleading in logs and to anyone reading the ErrorCode catalog. Justification: wrong error-context semantics, no functional break.
- **Current**: The same `ERR_DATAV_QUERY_FAILED` is shared between the panel-data path (where `panelId` is correct) and the ChatBI dataset-query path (where it is not). The ChatBI path has `ARG_DATASET_SID` available (`NopDatavErrors:423`) but doesn't use it.
- **Risk**: Operators grepping logs for `panelId=<something>` will chase a phantom panel; the ErrorCode catalog implies the query-failed error is always panel-scoped, misleading future callers who want to distinguish dataset-level failures.
- **Recommendation**: Define a sibling `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED` keyed on `ARG_DATASET_SID` (mirroring the existing `ERR_DATAV_CHATBI_DATASET_NOT_FOUND` pattern at `:449-453`), or generalize `ERR_DATAV_QUERY_FAILED` to carry both optional params. Cheapest fix: at `:167` use `.param(ARG_DATASET_SID, datasetSid)` and accept that the legacy message template falls back to the datasetSid value.
- **Confidence**: certain
- **Discovery source**: 异常路径侦探

---

### [AR-5] `[P2]` Both ChatBI generate-executors inline a private `NopOperatorFallback.SYSTEM_OPERATOR="system"` with a false "circular-dependency" justification — the real constant already lives in `NopDatavOperatorResolver` in the same module with no incoming dependency from the chatbi package

- **Files**:
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateDashboardExecutor.java:155-164, 469-474`
  - `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateScreenExecutor.java:443-451, 611-614`
  - Canonical: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavOperatorResolver.java:11` (`public static final String SYSTEM_OPERATOR = "system";`)
- **Evidence**:
  ```java
  // DatavGenerateDashboardExecutor:155-164 — reads the inline fallback
  private String resolveOperator(IToolExecuteContext context) {
      // 裁定 G：ChatBI 循环传入的是 ChatBiToolExecuteContext（携带 operator）。强转耦合契约。
      if (context instanceof ChatBiToolExecuteContext) {
          String op = ((ChatBiToolExecuteContext) context).getOperator();
          if (op != null && !op.isEmpty()) {
              return op;
          }
      }
      return NopOperatorFallback.SYSTEM_OPERATOR;
  }
  ```
  ```java
  // DatavGenerateDashboardExecutor:469-474 — the inline duplicate + false justification
  /**
   * operator fallback 常量（避免循环依赖 NopDatavOperatorResolver，本 executor 内联同值）。
   */
  private static final class NopOperatorFallback {
      static final String SYSTEM_OPERATOR = "system";
  }
  ```
  ```java
  // NopDatavOperatorResolver — a stateless static utility, package io.nop.datav.service,
  // with NO field that could pull io.nop.datav.service.chatbi.* — circular dependency is impossible
  public static String resolveOperator(IServiceContext context) { ... }
  ```
  The screen executor duplicates the identical inner class at `:611-614` with the same comment.
- **Severity**: P2 — values match, no behavior break. But the "避免循环依赖" comment is a load-bearing false claim: a future maintainer who needs the fallback to diverge will reason from the wrong premise, and two copies of `"system"` can drift. Justification: misleading comment + needless duplication, maintenance-level.
- **Current**: `NopDatavOperatorResolver.SYSTEM_OPERATOR` is already the documented single source. Both executors go out of their way to avoid it for a non-existent cycle.
- **Risk**: If the platform-wide operator fallback token ever changes (e.g. to a localized "anonymous"), the resolver constant is updated but the two chatbi copies stay `"system"`, silently splitting the operator identity namespace for audit columns on ChatBI-generated dashboards/screens.
- **Recommendation**: Replace `NopOperatorFallback.SYSTEM_OPERATOR` with `NopDatavOperatorResolver.SYSTEM_OPERATOR` in both executors and delete the inner class. The chatbi package already imports `NopDatavOperatorResolver`-adjacent types and there is no cycle.
- **Confidence**: certain
- **Discovery source**: 死代码清道夫

---

### [AR-6] `[P2]` `ChatBiToolCallingLoop` can leak a raw `CompletionException` from `toolManager.callTool(...).join()` and double-silently-swallows handler failures — the unused `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` is the visible tip of an under-instrumented error surface

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/ChatBiToolCallingLoop.java:137-157` (and `:149-153` swallow); dead ErrorCode `NopDatavErrors.java:437-441`
- **Evidence**:
  ```java
  // ChatBiToolCallingLoop:137-140 — join() throws CompletionException on a failed stage; no try/catch wraps it
  for (ChatToolCall chatToolCall : toolCalls) {
      AiToolCallCall aiToolCall = ChatBiTypeConverter.toAiToolCall(chatToolCall);
      AiToolCallResult toolResult = toolManager.callTool(
              chatToolCall.getName(), aiToolCall, context).join();   // <-- can throw, unwrapped
  ```
  ```java
  // :148-153 — handler failures swallowed with no log
  if (resultHandler != null) {
      try {
          resultHandler.handle(chatToolCall.getName(), toolResult, toolResponseContent, accumulator);
      } catch (Exception ignore) {
          // handler 失败不影响循环（与 D6-1 query 解析失败容忍一致）
      }
  }
  ```
  The built-in `QUERY_HANDLER` itself ALSO swallows (`:221-223` `catch (Exception ignore)`), so a handler bug is invisible at two layers. Meanwhile `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` (`NopDatavErrors:437`) is defined for exactly "工具执行错误透传" but referenced nowhere (multi-audit Dim09-04 noted the dead code; this finding adds the behavioral consequence).
- **Severity**: P2 — the 6 tool executors each wrap their whole `executeAsync` body in `try/catch` returning `errorResult`, so `.join()` only throws on infra-level failure (toolManager dispatch, rejected execution, bean-resolution). Low likelihood, but when it does throw, the `CompletionException` escapes `chatToQuery`/`chatToDashboard`/`chatToScreen` (public GraphQL actions) unwrapped — a two-tier error-strategy violation of the same class as the multi-audit's Dim09-01, just on a much colder path. Justification: cold-path contract drift + systematic under-logging, not a hot defect.
- **Current**: No protective try/catch around `callTool().join()`; no logging anywhere in the loop on handler/parse failure; the designated ErrorCode is unused.
- **Risk**: (a) An infra hiccup surfaces to the frontend as a stack-trace-flavored `CompletionException` rather than `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`; (b) a regression in `DASHBOARD_RESULT_HANDLER`/`SCREEN_RESULT_HANDLER` (e.g. NPE parsing the JSON) is silently swallowed and the action returns a `ChatBiResult` with a null `createdEntityId`, which the user interprets as "generation failed for unknown reasons" with no log trace.
- **Recommendation**: Wrap the `.join()` in try/catch; on `CompletionException` unwrap and throw `new NopException(ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED).param(ARG_TOOL_NAME, chatToolCall.getName()).cause(e)`. In the handler-swallow blocks, at minimum `LOG.debug` the exception so silent failures become traceable. This also retires the dead ErrorCode (multi-audit Dim09-04).
- **Confidence**: likely
- **Discovery source**: 异常路径侦探
- **De-dup note**: multi-audit Dim09-04 reported only the dead constant. This finding adds the live leak/swallow consequence and is reported separately because it changes the recommendation (wrap `.join()`, not just delete the constant).

---

### [AR-7] `[P2]` `DatavListDatasetsExecutor` loads every `NopReportDataset` row (including the full `dsText` SQL body) into memory on every ChatBI call to do a Java-side keyword substring filter — wasteful at 10x dataset scale

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavListDatasetsExecutor.java:92-113`
- **Evidence**:
  ```java
  private List<NopReportDataset> findActiveDatasets(String keyword) {
      IEntityDao<NopReportDataset> dao = daoProvider.daoFor(NopReportDataset.class);
      // 加载全部活跃数据集后在内存过滤 keyword（数据集通常数量有限，避免拼动态 LIKE 条件）。
      List<NopReportDataset> all = dao.findAll();           // <-- full entities incl. dsText + dsMeta CLOBs
      List<NopReportDataset> filtered = new ArrayList<>();
      for (NopReportDataset ds : all) {
          if (ds.getStatus() == null || ds.getStatus() != STATUS_ACTIVE) continue;
          if (keyword != null && !keyword.isEmpty()) {
              ... dsName.toLowerCase().contains(kw) ...      // <-- only dsName/description are needed
          }
          filtered.add(ds);
      }
      return filtered;
  }
  ```
  The result JSON exposes only `{sid, dsName, description, dsType}` (`:61-66`), but `findAll()` materializes every column of every row, including `dsText` (the SQL template) and `dsMeta` (field metadata CLOB).
- **Severity**: P2 — correct output, but allocates O(rows × CLOB-size) on every ChatBI "list datasets" turn, which the LLM calls at the start of every query/dashboard/screen session. Justification: 10x-scale resource concern, no current correctness break. The inline comment acknowledges the trade-off ("数据集通常数量有限").
- **Current**: Works fine at demo scale (dozens of datasets). At 10x (hundreds–thousands of datasets with large SQL/CLOB bodies), each ChatBI conversation pays a full-table fetch + CLOB materialization cost just to filter on two short string columns.
- **Risk**: Under realistic catalog size, the most-called ChatBI tool becomes the slowest; the LLM may also retry the call on timeout, amplifying load. No security issue (the LLM only sees the 4 whitelisted fields in the JSON).
- **Recommendation**: Project only the needed columns (use a `QueryBean` field projection or a dedicated finder that selects `sid, dsName, description, dsType, status`), and push the keyword filter to SQL (`like dsName or like description`) so neither the full row nor the CLOBs cross the wire. Keep the in-memory status filter only if the active-status dict is not DB-queryable.
- **Confidence**: likely
- **Discovery source**: 10x 规模运维者

---

## Minor nits (recorded, not individually remediable)

- `[P2]` `DatavGenerateDashboardExecutor.generateId(String prefix)` (`:392-397`) accepts a `prefix` argument it never uses ("prefix 参数保留为语义标记"). Dead parameter; both call sites pass `"dash"`/`"ref"`/`"panel"` for cosmetics only. Either use it or drop it.
- `[P2]` `ChatBiToolCallingLoop.ChatBiQueryResultHandlers.QUERY_HANDLER` (`:203-204`) calls `JsonTool.parseNonStrict(content)` twice in the same ternary (`instanceof Map ? (Map) parseNonStrict(content) : null`). Parse once into a local.
- `[P2]` `nop-datav-api` is an empty module (only `pom.xml`; `gen-crud-api.xgen` is fully commented out). Unlike `core`/`chart`, it IS a real dependency of `nop-datav-dao` (`nop-datav-dao/pom.xml:26-29`) and is listed in `nop-bom`, so it is a legitimate placeholder for future cross-module API types. Recorded for completeness; no action needed unless the CRUD-API generation is permanently abandoned, in which case the dependency can be dropped.

---

## Overall assessment — the 1–3 directions most worth attention

1. **Module-graph hygiene is the highest-leverage cleanup.** `nop-datav-core` (orphaned, 261 lines of generated constants) and `nop-datav-chart` (empty shell + BOM-bypassing `poi`) are both invisible to a dimension audit but obvious to a dead-code sweep. The core case is the more important one: the platform's model-first single-source-of-truth has already silently eroded into hand-written status enums scattered across the service layer. Picking source-of-truth (core-or-service) deliberately prevents a future dict-value change from desyncing half the state machines.
2. **ChatBI's error surface is systematically under-observed.** Three independent P2s converge on the same subsystem: an unused `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`, a `.join()` that can leak `CompletionException`, two layers of `catch (Exception ignore)`, and a misclassified constraint matcher that lies to the LLM. None is individually severe, but together they mean "when ChatBI misbehaves, you cannot find out why from the logs" — which is the worst possible debugging posture for an LLM-in-the-loop feature.
3. **Error-context param semantics are loosely coupled to call sites.** `ERR_DATAV_QUERY_FAILED` keyed on `panelId` is reused in a dataset-only path (AR-4); the same "shared ErrorCode, wrong param" smell exists in the multi-audit's Dim09-03. A small sweep to align ErrorCode param names with their actual call-site arguments would pay off across the module.

## Blind-spot self-assessment

- I did not run `./mvnw test -pl nop-datav -am`; findings are from static reading. A green build pass would confirm no compile/test regression hides behind the orphan-module claim (in particular, I did not prove that removing `nop-datav-core` from disk breaks nothing — I only proved nothing references it).
- The 15 generated `_gen/_*.java` entity classes and the generated `_*.xmeta` were sampled, not exhaustively diffed against the hand-written shells; field-level visibility tags beyond `passwordHash` (multi-audit Dim13-01) were not re-audited.
- `_NopDatavDashboardShare.xmeta` and the other generated xmeta baselines were not read in full; I relied on the multi-audit's Dim13-01 for the passwordHash conclusion and only confirmed the hand-written shell is `<props/>`.
- Front-end page YAMLs (`pages/*.page.yaml`, `*.view.xml`) were not audited for drift against the new ChatBI/screen APIs.
- `deploy/sql/*.sql` (the `_create` / `_drop` DDL) was not cross-checked against `model/nop-datav.orm.xml` for index/UK/constraint naming drift; the multi-audit covered two ScreenSnapshot nits (Dim04-01/02) and I did not re-sweep the rest.
- Concurrency/idempotency of the scheduler recovery paths under true parallel cron firing was reasoned about, not empirically verified.

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
