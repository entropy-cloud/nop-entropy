> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-datav
> Remediation: P0+P1 findings drafted into plans `ai-dev/plans/nop-datav/2026-08-10-2025-1-share-export-rbac-and-credential-protection.md`（Dim08-01, Dim13-01）✅ done 2026-08-11、`2026-08-10-2025-2-export-task-state-machine-correctness.md`（Dim14-01, Dim07-01, Dim16-01）、`2026-08-10-2025-3-service-layer-contract-correctness.md`（Dim09-01, Dim14-02）。P2 findings triaged to `ai-dev/backlog/nop-datav-audit-followups.md`。

# nop-datav Multi-Dimensional Audit Report

- **Audit target**: `nop-datav/` (15 ORM entities, 16 BizModels, ~25 service/helper classes, 42 test files)
- **Audit baseline**: LIVE CODE only (`nop-datav` worktree, 2026-08-10). Historical audits/plans/bugs/lessons deliberately NOT consulted.
- **Documented contract cross-referenced**: `ai-dev/design/nop-datav/{model,runtime,linkage,permission-sharing,screen,schedule-report,ai}-design.md` (all marked `Status: final`).
- **Methodology**: `ai-dev/skills/deep-audit-prompts.md` (22 dimensions). Dimensions 01/02/03/04/05/06/07/08/09/10/11/12/13/14/15/16/17/18/19/20/21 executed. Dim 22 (workflow) N/A (no `.xwf` files in module). Per-dimension candidate findings were independently produced by specialized subagents, then the leading-edge (security/async/contract) findings were re-verified against live source by the consolidating agent before inclusion.

## Severity distribution

| Severity | Count | Notes |
|----------|-------|-------|
| P0 | 0 | No currently-exploitable data-loss/security-failure or active-incorrect-behavior-of-critical-path found |
| P1 | 7 | Material defects / contract drifts; 2 of them security-relevant, 2 active incorrect behavior |
| P2 | 10 | Polish / latent / lower-impact; mostly error-handling consistency + test-branch gaps + ORM nits |

> Downstream remediation: P0+P1 findings drive plan drafting. P2-only items triage to backlog.

---

## C. Findings — Service layer, async/txn, security, error handling

### [Dim08-01] `[P1]` Missing action-auth.xml role bindings for NopDatavDashboardShare + NopDatavExportTask — entire share/export surface is deny-by-default once action-auth is enabled (contract drift vs design §34-49/§222/§370-377)

- **File**: `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml` (whole file) and generated baseline `_nop-datav.action-auth.xml:56-69, 112-125`
- **Evidence**:
  ```xml
  <!-- generated _nop-datav.action-auth.xml:60-67 — FNPT resources exist but NO roles= attribute -->
  <resource id="FNPT:NopDatavDashboardShare:query"  ... resourceType="FNPT">
      <permissions>NopDatavDashboardShare:query</permissions>
  </resource>
  <resource id="FNPT:NopDatavDashboardShare:mutation" ... resourceType="FNPT">
      <permissions>NopDatavDashboardShare:mutation</permissions>
  </resource>
  <!-- hand-written nop-datav.action-auth.xml has NO NopDatavDashboardShare-main / NopDatavExportTask-main
       resource block at all (only the other 14 BizObjs get roles="..." bindings) -->
  ```
  ```java
  // BizModels DO declare @Auth on the 8 custom actions, referencing permissions that are never registered:
  // NopDatavDashboardShareBizModel.java:74
  @Auth(permissions = "NopDatavDashboardShare:createShare")
  // NopDatavExportTaskBizModel.java:115
  @Auth(permissions = "NopDatavExportTask:createExportTask")
  ```
- **Severity**: P1 — with `nop.auth.enable-action-auth=true` (the documented production config, design §D3-1), `SiteCacheData.isPermitted` returns `false` for every unregistered/role-less permission → all 8 custom actions + standard CRUD for BOTH entities are denied to everyone (including admin). Functional contract drift; RBAC tests do not exercise these two entities.
- **Current**: The hand-written `nop-datav.action-auth.xml` adds `roles="admin,user"` / `roles="admin"` bindings for 14 BizObjs (Dashboard, Panel, Screen, FilterState, ReportTask, AlertRule, ChatBi, etc.) but contains no `NopDatavDashboardShare-main` or `NopDatavExportTask-main` block, and adds none of the 8 custom-action FNPT entries (`createShare/listShares/revokeShare/toggleShare`, `createExportTask/getExportTask/cancelExportTask/downloadExportFile`). The generated baseline declares the CRUD `query/mutation` FNPT resources without `roles=`.
- **Risk**: Production deployments that enable action-level RBAC (design D3-1 mandates it) will find the public share feature and the entire data-export feature inaccessible to all users. The deny-by-default is silent (no error at startup; runtime `ERR_AUTH_NO_PERMISSION`). `getSharedDashboard` is unaffected (it uses `@Auth(publicAccess=true)` which bypasses user-context check).
- **Recommendation**: Add a `NopDatavDashboardShare-main` and `NopDatavExportTask-main` resource block mirroring the `NopDatavReportTask-main` pattern: bind CRUD `query`→`admin,user` / `mutation`→`admin`, and add the 8 custom-action FNPT entries per design §222 and §374-377 (management actions `admin`; `getExportTask`/`cancelExportTask`/`downloadExportFile`/`triggerReportNow` analogue → `admin,user`). Verify with a new RBAC test that actually invokes a share/export action with `enableActionAuth=true`.
- **Confidence**: certain
- **False-positive exclusion**: Verified `getSharedDashboard` is `@Auth(publicAccess=true)` (ShareBizModel:144) and needs no role binding (design §213-215). The 4 share-management + 4 export actions are NOT publicAccess and DO need bindings. Not a platform "core packages via transitive chain" relaxation.

---

### [Dim13-01] `[P1]` NopDatavDashboardShare standard CRUD (inherited CrudBizModel) bypasses the Dashboard-owner guard and exposes passwordHash — latent credential leak + horizontal privilege escalation that activates the moment Dim08-01 is fixed (contract drift vs design §223)

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardShareBizModel.java:49` (class extends `CrudBizModel<NopDatavDashboardShare>`); `nop-datav/model/nop-datav.orm.xml:509-511` (passwordHash column); `nop-datav-meta/.../NopDatavDashboardShare/NopDatavDashboardShare.xmeta` (empty `<props/>`); `nop-datav-service/.../auth/nop-datav.data-auth.xml` (no `<obj name="NopDatavDashboardShare">`)
- **Evidence**:
  ```java
  // Only the CUSTOM actions null passwordHash + check ownership; inherited CRUD does neither:
  public class NopDatavDashboardShareBizModel extends CrudBizModel<NopDatavDashboardShare> ... {
      // listShares (custom): nulls hash
      for (NopDatavDashboardShare share : shares) { share.setPasswordHash(null); ... }   // :118
      // doToggleShare (custom): nulls hash + requireDashboardOwnership                 // :171,178
      // BUT findPage/get/save/update/delete are inherited as-is — no override, no RLS
  }
  ```
  ```xml
  <!-- orm.xml:509 — passwordHash is a plain VARCHAR column with NO visibility tag -->
  <column code="PASSWORD_HASH" displayName="密码哈希" name="passwordHash" precision="200"
          propId="4" stdDataType="string" stdSqlType="VARCHAR" .../>
  <!-- data-auth.xml: NO <obj name="NopDatavDashboardShare"> (design §207: intentional, for anonymous access) -->
  ```
- **Severity**: P1 — security (sensitive credential column + horizontal privilege escalation). Currently mitigated ONLY because the inherited CRUD is deny-by-default (Dim08-01); the documented design intends CRUD `query/mutation` to be enabled (§49/§227-228), at which point the leak + bypass go live. The design's own claim §223 ("share entity has no RLS, protection comes from management actions' owner check") is incomplete — it covers only the custom actions, not the inherited CRUD.
- **Current**: `CrudBizModel` auto-exposes `findPage/findList/get/save/update/delete` for every `@BizModel` entity. For `NopDatavDashboardShare`, none of these enforce `requireDashboardOwnership`, none null `passwordHash`, and there is no RLS (`data-auth.xml` intentionally omits the entity). The `passwordHash` column carries no ORM/xmeta visibility restriction, and the hand-written xmeta is empty (`<props/>`), so the generated prop is GraphQL-readable.
- **Risk**: (a) A logged-in user holding `NopDatavDashboardShare:query` could `findPage` every share row across all dashboards and select `passwordHash` (a BCrypt(SHA256(pwd)) hash — offline-crackable). (b) A user holding `NopDatavDashboardShare:mutation` could `delete`/`update` other users' share links. The natural fix for Dim08-01 (binding roles to enable the documented CRUD) directly activates this.
- **Recommendation**: Either (a) override `findPage`/`get`/`delete` etc. in `NopDatavDashboardShareBizModel` to enforce ownership and strip `passwordHash` (mirror `listShares`), or (b) when adding the role bindings for Dim08-01, restrict `NopDatavDashboardShare:query/mutation` to `admin` only so the `user` role can reach share data ONLY through the owner-checked custom actions, and mark `passwordHash` `readable="false"` (or exclude from xmeta) as defense-in-depth.
- **Confidence**: likely (passwordHash is BCrypt-hashed, so leak is offline-crack rather than plaintext; and exploitation is gated on Dim08-01's fix)
- **False-positive exclusion**: NOT the platform "BizModel returns entity + xmeta controls fields" standard — here the xmeta does NOT restrict passwordHash (empty override), so the standard protection is absent. `NopDatavExportTask` has the same no-RLS/no-owner-check on inherited CRUD but carries no secret column, so it is a weaker concern folded into Dim08-01's recommendation, not a separate P1.

---

### [Dim07-01] `[P1]` Export-task cancel flag is checked exactly once (before RUNNING); execution body never polls it, so a cancel during a running export is silently overwritten to SUCCEEDED — direct contract break vs design §318/§310

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:204-261`; `nop-datav-service/.../export/PanelDataExporter.java:82-145`
- **Evidence**:
  ```java
  // executeTask — the ONLY cancel-flag check is here, BEFORE the RUNNING transition:
  if (Boolean.TRUE.equals(cancelFlags.get(taskId))) {   // :211
      markCancelled(dao, task, operator); return null;
  }
  task.setStatus(NopDatavExportTaskStatus.RUNNING);     // :215
  dao.updateEntityDirectly(task);
  try {
      ... file = exporter.exportDashboard(task.getSourceId(), params, maxRows);  // :228, no flag polling inside
      ...
      task.setStatus(NopDatavExportTaskStatus.SUCCEEDED); // :241  ← unconditionally overwrites CANCELLED
      dao.updateEntityDirectly(task);                     // :246
  } catch (Throwable t) { ... } finally { cancelFlags.remove(taskId); }
  ```
  `PanelDataExporter.exportPanel/exportDashboard` accept no cancel supplier and never inspect any flag inside the row-write loops (`writeCsv:160-162`, `toSheet:220-227`). Meanwhile `cancelExportTask:166-171` sets the flag AND directly writes `status=CANCELLED` to DB. `updateEntityDirectly` does not version-check, so the executor's later `SUCCEEDED` write wins.
- **Severity**: P1 — incorrect user-visible behavior on a documented state-machine transition; the cancel contract (the primary reason to have cancellation) does not work for the case it exists for. Paired with Dim16-01 (the test that should catch this has zero protective power).
- **Current**: Cancel-before-RUNNING works (the single check at :211). Cancel-during-RUNNING is broken: the executor runs to completion and writes SUCCEEDED over the CANCELLED the cancel-request wrote. The user sees a brief CANCELLED then a flip to SUCCEEDED.
- **Risk**: Users cancelling a large/long export are misled; the documented `running → cancelled` transition (design §310) and "执行体内主动轮询…在取数前/写出循环每批检测，命中即抛 NopException 终止" (design §318) are both unmet.
- **Recommendation**: Pass a `BooleanSupplier` (e.g. `() -> Boolean.TRUE.equals(cancelFlags.get(taskId))`) into `PanelDataExporter.exportPanel/exportDashboard`; check it before each panel iteration and inside the row-write loops; throw `NopException(ERR_DATAV_EXPORT_FAILED).param(ARG_REASON,"cancelled by user")` when tripped. Re-check the flag immediately before the SUCCEEDED write at :241. This also revives the currently-unused `ERR_DATAV_EXPORT_FAILED` (see Dim09-04).
- **Confidence**: certain
- **False-positive exclusion**: Verified `markCancelled`/`markFailedSafe` open fresh sessions and the catch-block at :247 only writes FAILED when an exception is actually thrown — the normal-completion path at :241 is unconditional. The bug is real, not a "race that's acceptable".

---

### [Dim14-01] `[P1]` createExportTask calls recoverInterruptedTasks() on EVERY export request, marking ALL other in-flight exports FAILED — active incorrect behavior (recovery is supposed to be startup-only per design §322-326)

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:135-136`; `nop-datav-service/.../export/NopDatavExportTaskRecovery.java:80-99`
- **Evidence**:
  ```java
  // createExportTask, in the request thread, before inserting the new task:
  // 清理重启遗留的中断任务（幂等；覆盖 init 后再次出现的场景）
  recovery.recoverInterruptedTasks();                    // :136
  ...
  NopDatavExportTask task = newTaskEntity(...);
  daoProvider().daoFor(NopDatavExportTask.class).saveEntityDirectly(task);  // :140
  submitExecution(task.getTaskId(), operator);           // :143
  ```
  ```java
  // NopDatavExportTaskRecovery.doRecover — selects ALL non-terminal tasks, no owner/staleness predicate:
  query.addFilter(FilterBeans.in("status", Arrays.asList(STATUS_PENDING, STATUS_RUNNING))); // :83
  List<NopDatavExportTask> stale = dao.findAllByQuery(query);
  for (NopDatavExportTask task : stale) {
      task.setStatus(STATUS_FAILED); task.setErrorMsg(RESTART_REASON); ...   // :91-92
  }
  // @PostConstruct init() already runs this once at container startup (:52-59)
  ```
- **Severity**: P1 — active, repeatable incorrect behavior: every single export request spuriously fails every other user's (and the same user's) in-flight PENDING/RUNNING exports. The `@PostConstruct` recovery already covers process restart by design (§322-326); the per-request call's stated rationale ("cover restart-without-init") is invalid because `@PostConstruct` runs before any request is served.
- **Current**: `createExportTask` synchronously scans the whole `nop_datav_export_task` table and marks all non-terminal rows FAILED with `errorMsg="interrupted by process restart"`. The victims' executors later overwrite the spurious FAILED with their real terminal status, but during the window: (1) `getExportTask` reports FAILED/"interrupted by process restart" for tasks that are actually running, prompting re-submission; (2) `checkConcurrencyLimit` (`:369-385`) counts PENDING/RUNNING — once a running task is marked FAILED it drops out of the count, so the limit is bypassable and duplicates can pile up. (`NopDatavReportDeliveryRecovery` has NO analogous per-request call — only `@PostConstruct` — confirming the export path is the outlier.)
- **Risk**: Under concurrent exports (multi-user, or one user exporting several panels), the status table flaps between real and spurious FAILED; concurrency-limit protection is defeated; users re-submit and waste work.
- **Recommendation**: Delete the per-request `recovery.recoverInterruptedTasks()` call at `:136`. If paranoid coverage is genuinely wanted, add a staleness predicate (`updateTime < now - N minutes`) so genuinely in-flight tasks are never recovered.
- **Confidence**: certain
- **False-positive exclusion**: Verified `NopDatavExportTaskRecovery.init()` is `@PostConstruct` (`:52-59`) and runs once at startup before request serving, so the per-request call is genuinely redundant-and-harmful, not the only recovery path.

---

### [Dim09-01] `[P1]` PanelParamEvaluator throws bare IllegalArgumentException that escapes the public getPanelData GraphQL action unwrapped — violates the two-tier error strategy (AGENTS.md) for public APIs

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelParamEvaluator.java:45,51,63,69`; caller `PanelDataBinder.java:131` (OUTSIDE the try/catch at :136-150)
- **Evidence**:
  ```java
  // PanelParamEvaluator — 4 bare IllegalArgumentException throws:
  try { parsed = JsonTool.parse(paramMappingJson); }
  catch (Exception e) { throw new IllegalArgumentException("Invalid paramMapping JSON: " + ..., e); }  // :45
  if (!(parsed instanceof Map)) throw new IllegalArgumentException("paramMapping JSON must be an object"); // :51
  if (!(ruleObj instanceof Map)) throw new IllegalArgumentException(...);                              // :63
  if (sourceKey == null || ...) throw new IllegalArgumentException("...missing 'source'");             // :69
  ```
  ```java
  // PanelDataBinder.queryPanelData — evaluate() is called BEFORE the try/catch that wraps executeQuery:
  Map<String,Object> params = PanelParamEvaluator.evaluate(datasetRef.getParamMapping(), requestParams); // :131
  SQL sql = PanelSqlBuilder.build(dsText, params, panelId);                                              // :133
  try { return jdbcTemplate.executeQuery(sql, ...); }                                                    // :136
  catch (NopException e) { throw e; }
  catch (Exception e) { throw new NopException(ERR_DATAV_QUERY_FAILED).param("panelId",panelId).cause(e); }
  ```
  The `IllegalArgumentException` propagates straight through `NopDatavPanelBizModel.getPanelData` (a `@BizQuery` public GraphQL action).
- **Severity**: P1 — public API surfaces a raw `IllegalArgumentException` (no `ErrorCode`, no `.param()`), breaking platform error-rendering and the documented two-tier strategy ("Framework core & public APIs: NopException + ErrorCode + .param()"). This is the highest-traffic public query path in the module.
- **Current**: Malformed `paramMapping` JSON (admin config error) or a rule missing `source` produces an unwrapped `IllegalArgumentException` at the GraphQL boundary — frontend/`GraphQLAuditLogger` lose the `errorCode` field used to filter/aggregate errors, and the cause chain is lost.
- **Recommendation**: Either wrap the 4 throws as `throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG).param(ARG_PANEL_ID, panelId).cause(e)` (the error code already exists in `NopDatavErrors`), or move the `PanelParamEvaluator.evaluate(...)` call at `PanelDataBinder:131` inside the existing try/catch so it gets wrapped as `ERR_DATAV_QUERY_FAILED`. Option (wrap) is cleaner and preserves the distinct error code.
- **Confidence**: certain
- **False-positive exclusion**: Verified `PanelDataBinder:131` is structurally outside the try block (which begins at :136), so this is not "already handled by the catch". The two-tier rule explicitly forbids bare RuntimeException on public APIs.

---

### [Dim14-02] `[P1]` ReportDeliveryExecutor runs the synchronous SMTP send INSIDE runInNewSession — JDBC connection held across a remote call → pool exhaustion under concurrent cron firing

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/ReportDeliveryExecutor.java:122-198`
- **Evidence**:
  ```java
  GlobalExecutors.globalWorker().submit(() -> {
      try { ormTemplate.runInNewSession(session -> doExecute(session, ...)); }   // :124 — session opened here
      ...
  });
  // inside doExecute, ALL in the SAME session (Nop ORM session holds a JDBC connection for its lifetime):
  String fileId = saveReportFile(file, reportTaskId);                 // :187  fileStore.saveFile → JDBC INSERT nop_file_record
  delivery.setGeneratedFileRecordId(fileId);                          // :188
  List<String> delivered = notificationSender.sendReport(task, delivery, null);  // :192  SMTP sendEmail (sync, slow)
  delivery.setDeliveredChannels(...); delivery.setStatus(SUCCEEDED);  // :193-194
  deliveryDao.updateEntityDirectly(delivery);                         // :198  JDBC UPDATE
  ```
- **Severity**: P1 — availability/resource. SMTP timeouts (typically 30-60s) multiplied across report tasks sharing a cron minute exhaust the JDBC pool, cascading to unrelated datav requests (panel queries, dashboards, exports). Also widens the lock window on `nop_datav_report_delivery` rows, and sends the email before the SUCCEEDED UPDATE commits (a failed UPDATE after a sent email leaves delivery showing RUNNING with email already gone).
- **Current**: The whole `doExecute` body (file write → SMTP send → delivery update) runs in one ORM session. Nop ORM sessions hold a JDBC connection for the session lifetime.
- **Risk**: Pool starvation under realistic load (many reports scheduled at top-of-hour). Distinct from `AlertEvaluator` (which does NOT wrap in `runInNewSession` — each of its DAO calls opens its own micro-session, so its `sendAlertNotification` is NOT inside a session) and from `NopDatavExportTaskBizModel.executeTask` (one session but NO remote call — only JDBC + file write).
- **Recommendation**: Split the side-effecting SMTP call out of the session-bound block: do file save + delivery status update inside `runInNewSession` and capture recipients/template data; after the session closes (or via `txn().afterCommit(...)`), call `notificationSender.sendReport`. This also fixes the email-before-commit ordering hazard.
- **Confidence**: certain
- **False-positive exclusion**: Verified the session is opened at `:124` and NOT closed before `:192`, and that the sibling executors (`AlertEvaluator`, export `executeTask`) genuinely differ as described — so this is specific to the report path, not a module-wide pattern.

---

## F. Findings — Test effectiveness

### [Dim16-01] `[P1]` Export cancel-during-execution test seeds a non-executing RUNNING row — it has zero protective power for the running-cancel contract, so the Dim07-01 regression ships green

- **File**: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/entity/TestNopDatavExportE2E.java:246-254`
- **Evidence**:
  ```java
  @Test
  public void testCancelRunningTaskTransitionsToCancelled() {
      IServiceContext ctx = ownerContext("alice");
      NopDatavExportTask task = seedTask("cancel-task", "alice", NopDatavExportTaskStatus.RUNNING); // never submitted
      NopDatavExportTask cancelled = exportBiz.cancelExportTask(task.getTaskId(), ctx);
      assertEquals(NopDatavExportTaskStatus.CANCELLED, cancelled.getStatus());
      assertEquals(NopDatavExportTaskStatus.CANCELLED,
              daoProvider.daoFor(NopDatavExportTask.class).getEntityById(task.getTaskId()).getStatus(), ...);
  }
  ```
  `seedTask` writes a row via `saveEntityDirectly` — it is never submitted to `GlobalExecutors.globalWorker()` (no `submitExecution`), so no `executeTask` body runs concurrently. Repo-wide grep finds zero references to `cancelFlags`/`isCancelFlagged` in tests.
- **Severity**: P1 — "absent test for changed behavior" on a security/correctness-critical path. The cancel-during-running contract (design §310/§318) is entirely untested, and the implementation is in fact broken (Dim07-01). Removing the cancel-flag mechanism entirely would not fail any test.
- **Current**: The test only exercises the cancel-request thread's direct DB write (which works); it never runs the executor body, so it cannot detect that the executor overwrites CANCELLED with SUCCEEDED.
- **Risk**: A regression that breaks mid-execution cancel (or the existing bug) ships undetected. This is the textbook P-8 antipattern (invalid negative test) paired with a real implementation gap.
- **Recommendation**: Add a test that (1) `createExportTask`s a real task against a dataset large enough that the executor runs for a measurable duration, (2) polls `getExportTask` until `status=RUNNING`, (3) calls `cancelExportTask`, (4) polls to terminal and asserts final status is `CANCELLED` (not SUCCEEDED) and `fileRecordId` is null. This will surface Dim07-01; fix both together.
- **Confidence**: certain
- **False-positive exclusion**: NOT a "could add more tests" cosmetic note — the documented cancel contract is a dual-write (request thread + executor body) and the executor-body half has no effective test and is in fact broken.

---

## P2 findings (lower impact / polish / latent)

### [Dim14-03] `[P2]` AlertEvaluator persists TRIGGERED + lastNotifiedTime BEFORE sendAlertNotification; on SMTP failure the alert is marked "notified, no error" with no notification sent — drift vs design §269-273 state table

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/alert/AlertEvaluator.java:146-189`
- **Evidence**:
  ```java
  if (conditionMet) {                                                       // OK→TRIGGERED
      state.setState(TRIGGERED); state.setLastTriggeredTime(nowTs);
      state.setLastNotifiedTime(nowTs);                                     // :151 — set BEFORE send
      saveState(state);                                                     // :152 — committed: TRIGGERED, lastNotified=now, errorMsg=null (cleared :89)
      sendAlertNotification(rule, ...);                                     // :153 — if throws, scheduler swallows
      return ...;
  }
  ```
  Same pattern for TRIGGERED→OK (`:166-169`) and rearm (`:175-180`). `errorMsg` is cleared at `:89` and never reset on notification failure; the scheduler `catch (Throwable)` (`NopDatavAlertScheduler:207`) leaves the persisted state untouched.
- **Severity**: P2 — missed/delayed initial notification (no data corruption). With `rearmSeconds==0` the alert is never re-sent until the condition cycles; with `rearmSeconds>0` re-send waits the full cooldown; `errorMsg` stays null so operators cannot see why from the state row. Justification: real but non-blocking notification-reliability gap.
- **Current / Risk / Recommendation**: See evidence. Persist the state transition but set `lastNotifiedTime` only AFTER successful send; on notification failure set `state.setErrorMsg(reason)` and leave `lastNotifiedTime` null so the next eval reattempts immediately; or wrap `sendAlertNotification` in its own try/catch inside `evaluate`.
- **Confidence**: likely

---

### [Dim14-04] `[P2]` Schedulers catch Throwable (including Error/OOM), contradicting design §3/§9 which mandates infrastructure errors propagate

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/NopDatavReportScheduler.java:222`; `nop-datav/.../alert/NopDatavAlertScheduler.java:207`
- **Evidence**:
  ```java
  } catch (Throwable t) {   // NopDatavReportScheduler:222 — comment quotes design ("业务异常吞掉") but catches broader than Exception
      Throwable reason = NopException.adapt(t);
      LOG.error(...);  return buildResult(reportTaskId, "failed", null, safeMsg(reason));
  }
  // Identical catch (Throwable t) at NopDatavAlertScheduler:207
  ```
- **Severity**: P2 — explicit contract drift; `Throwable` includes `OutOfMemoryError`/`VirtualMachineError`. After an OOM the scheduler thread continues in a possibly-corrupt JVM, returns a normal result, and keeps firing subsequent cron cycles. Justification: real drift but low practical impact (the JVM usually dies soon anyway).
- **Current / Risk / Recommendation**: Design §3/§9 says "仅基础设施错误（Error/RuntimeException 非业务异常）才抛 / 如 OOM 才向外传播". Change `catch (Throwable t)` → `catch (Exception e)` in both scheduler entry methods; let `Error` propagate (or at minimum rethrow `OutOfMemoryError`/`VirtualMachineError` after logging).
- **Confidence**: certain

---

### [Dim09-02] `[P2]` AlertThresholdComparator / AlertAggregator use bare IllegalArgumentException for config errors that reach the public evaluateAlertNow action

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/alert/AlertThresholdComparator.java:48,75`; `nop-datav/.../alert/AlertAggregator.java:137`
- **Evidence**:
  ```java
  // AlertThresholdComparator:48
  if (currentValue == null || thresholdValue == null)
      throw new IllegalArgumentException("currentValue and thresholdValue must not be null (alertRuleId=...)");
  // :75
  throw new IllegalArgumentException("Unsupported operator: " + operator + " (alertRuleId=...)");
  ```
  Caller `AlertEvaluator.evaluate` catches only around `binder.queryPanelData` (`:107`); these escape to `NopDatavAlertRuleBizModel.evaluateAlertNow` (a `@BizMutation`). Note the same file's `:67` correctly uses `NopException(ERR_DATAV_ALERT_INVALID_THRESHOLD)`.
- **Severity**: P2 — same two-tier violation as Dim09-01 but lower-impact (less-frequently-hit action). Justification: convention violation, not a hot path.
- **Recommendation**: Replace with `NopException(ERR_DATAV_ALERT_INVALID_THRESHOLD).param(ARG_ALERT_OPERATOR, operator).param(ARG_ALERT_RULE_ID, alertRuleId)` (or define `ERR_DATAV_ALERT_UNSUPPORTED_OPERATOR`).
- **Confidence**: likely

---

### [Dim09-03] `[P2]` createExportTask throws ERR_DATAV_EXPORT_TASK_NOT_FOUND for empty sourceType/sourceId — wrong ErrorCode + param mismatch misleads operators

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:121-125`
- **Evidence**:
  ```java
  validateFormat(format, sourceType);
  if (StringHelper.isEmpty(sourceType) || StringHelper.isEmpty(sourceId)) {
      throw new NopException(ERR_DATAV_EXPORT_TASK_NOT_FOUND)              // code text = "Export task not found: {taskId}"
              .param(ARG_SOURCE_TYPE, sourceType).param(ARG_SOURCE_ID, sourceId);  // code expects ARG_TASK_ID
  }
  ```
- **Severity**: P2 — error code/message does not match the failure cause; audit logs and frontend banners show "Export task not found: " (empty taskId) when the real cause is "source required". Justification: misleads operators, no functional break.
- **Recommendation**: Define `ERR_DATAV_EXPORT_MISSING_SOURCE` (with `ARG_SOURCE_TYPE`/`ARG_SOURCE_ID`), or reuse `ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED`. Also move the empty-source check before `validateFormat` to avoid the `null`-source branch ordering.
- **Confidence**: certain

---

### [Dim09-04] `[P2]` Two ErrorCodes defined but never referenced — dead constants signaling unimplemented contracts (ERR_DATAV_EXPORT_FAILED, ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED)

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/NopDatavErrors.java:257,437`
- **Evidence**: `rg "ERR_DATAV_EXPORT_FAILED|ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED" --type java nop-datav/` returns only the two definition sites; no usage in main code or tests.
  - `ERR_DATAV_EXPORT_FAILED` is specified by design §318 for the cancel-polling throw (unimplemented — see Dim07-01).
  - `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` is specified by ai-design §F as "工具执行错误透传" (ChatBiToolCallingLoop uses ad-hoc Throwable handling instead).
- **Severity**: P2 — dead code; readers infer an error path exists when it doesn't; two design clauses silently unimplemented. Justification: maintenance/cleanliness, paired with the real gaps in Dim07-01.
- **Recommendation**: Either implement both paths (Dim07-01 covers the export side) or delete the unused codes and update the design docs.
- **Confidence**: certain

---

### [Dim09-05] `[P2]` Schedulers throw bare IllegalArgumentException for missing job params (mitigated by surrounding catch)

- **File**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/NopDatavReportScheduler.java:206-208`; `nop-datav/.../alert/NopDatavAlertScheduler.java:197-199`
- **Evidence**:
  ```java
  Object id = params != null ? params.get(PARAM_REPORT_TASK_ID) : null;
  if (id == null) throw new IllegalArgumentException("missing reportTaskId in job params");
  ```
  The throw is caught by the same method's outer `catch (Throwable)` and converted to a "failed" result Map — so it does not escape, but is inconsistent with module conventions and loses structure in `safeMsg`.
- **Severity**: P2 — convention violation with no external impact (swallowed immediately). Justification: minor consistency nit.
- **Recommendation**: Replace with `NopException(ERR_DATAV_REPORT_TASK_NOT_FOUND).param(ARG_REPORT_TASK_ID, "<missing>")` (and alert analogue) to preserve the catch+swallow behavior while giving operators a structured code.
- **Confidence**: likely

---

### [Dim04-01] `[P2]` NopDatavScreenSnapshot.snapshotContent missing mandatory="true" documented in screen-design §1.3

- **File**: `nop-datav/model/nop-datav.orm.xml:785-787`
- **Evidence**:
  ```xml
  <column code="SNAPSHOT_CONTENT" ... name="snapshotContent" propId="4"
          stdDataType="string" stdDomain="json" stdSqlType="CLOB" .../>  <!-- no mandatory="true" -->
  ```
  Design `screen-design.md:79`: `| snapshotContent | clobJson mandatory | 序列化的发布内容 JSON |`. Sibling key columns `screenId`/`snapshotVersion` ARE `mandatory="true"`.
- **Severity**: P2 — single missing attribute; `publishScreen` always populates it so runtime impact is minimal, but DB NOT NULL + entity validation are not enforced. Justification: doc-vs-model drift, no current bad path.
- **Recommendation**: Add `mandatory="true"`. (Note: `NopDatavDashboardSnapshot.snapshotContent:382` has the same omission but D0 `model-design.md` is silent on its mandatory-ness, so that one is not a documented drift.)
- **Confidence**: certain

---

### [Dim04-02] `[P2]` NopDatavScreenSnapshot UK name diverges from design (SCR_VER vs SCREEN_VER)

- **File**: `nop-datav/model/nop-datav.orm.xml:821`
- **Evidence**:
  ```xml
  <unique-key name="UK_NOP_DATAV_SCREEN_SNAPSHOT_SCR_VER" columns="screenId,snapshotVersion" .../>
  ```
  Design `screen-design.md:84`: `UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER(screenId, snapshotVersion)`.
- **Severity**: P2 — purely a name string; columns and uniqueness semantics are correct. Justification: doc cross-referencing by name broken for one UK; no runtime impact.
- **Recommendation**: Rename to match the design contract (longer UK names already exist, so length is not a constraint).
- **Confidence**: certain

---

### [Dim16-02] `[P2]` Alert "panel exists but queryPanelData throws" path not distinctly tested (only the panel-missing sibling is covered)

- **File**: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/alert/TestNopDatavAlertE2E.java:370-389`
- **Evidence**: `testPanelMissingFailsGracefully` covers only the `panelId not found` branch of design §19. The sibling branch ("`PanelDataBinder.queryPanelData` 抛任何异常 → 评估方法吞错落库 errorMsg，state 保持") — panel row exists but SQL fails — has no dedicated test.
- **Severity**: P2 — the shared catch/swallow logic IS exercised by the panel-missing test, so total removal of error handling would be caught; only a query-exception-specific regression would slip. Justification: minor branch gap, not an antipattern.
- **Recommendation**: Add one test binding a panel to a dataset whose `dsText` references a non-existent table; assert `errorMsg` recorded + `state` preserved + no notification.
- **Confidence**: likely

---

### [Dim16-03] `[P2]` Report delivery "export fails mid-delivery" path untested (all FAILED tests are pre-export validation failures)

- **File**: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/entity/TestNopDatavReportE2E.java:262-355`
- **Evidence**: The six FAILED-path tests (`testNoNotifiableChannelFailsExplicitly`, `testNoPublishableDashboardFailsExplicitly`, `testImChannelThrowsUnsupported`, `testSenderNotConfiguredFailsExplicitly`, `testTemplateNotFoundFailsExplicitly`, and the sub-case in `testExecuteScheduledReportSwallowsBusinessError`) all fail at the pre-export validation stage. No test makes `PanelDataExporter.exportDashboard(...)` itself raise mid-execution.
- **Severity**: P2 — executor catch block is shared with validation-failure paths so total-removal would be caught; only a mid-delivery-export-specific regression would slip. Justification: minor branch gap.
- **Recommendation**: Add one test binding a report task to a dashboard whose panel's dataset points at a non-existent table; assert delivery reaches `FAILED` with non-empty `errorMsg`.
- **Confidence**: likely

---

## Dimensions checked and CLEAN (summary)

- **Dim 01 (dependency graph)**: standard 8-piece module layout (api/codegen/dao/meta/service/web/app + chart). `nop-datav-service` depends on `nop-report-dao` only (not report-core/service) per runtime-design §模块依赖; ChatBI path uses `nop-ai-api`/`nop-ai-toolkit` (not the deprecated `IAiChatService`). No reverse/cyclic deps found.
- **Dim 02 (module boundary)**: no oversized mixed-responsibility files; generated `_`-prefixed files correctly separated from hand-written; no hand-edits to generated files detected.
- **Dim 03 (API surface)**: 16 BizModels all extend `CrudBizModel<T>` + `setEntityName`; custom actions use `@BizQuery`/`@BizMutation` + `@Auth` consistently; `Map<String,Object>` params match documented data-binding contracts (not anti-patterns).
- **Dim 05 (codegen pipeline)**: source `nop-datav.orm.xml` (15 entities) → `_app.orm.xml` (15) → dao entities (15 `_gen` + 15 retained) → xmeta (15+15) → xbiz → pages chain closes. `gen-crud-api.xgen` intentionally disabled (empty api module) — consistent, not a defect.
- **Dim 06 (delta customization)**: no `_delta/` files in module; N/A.
- **Dim 10 (XDSL correctness)**: hand-written `action-auth.xml`/`data-auth.xml`/`_service.beans.xml` use correct `x:schema`/`x:extends`/`x:override` semantics; `${$context.userName}` is a registered XPL global var.
- **Dim 11 (XMeta↔BizModel)**: hand-written xmeta files are thin shells over generated; no field-permission or type mismatch beyond the passwordHash concern in Dim13-01.
- **Dim 12 (GraphQL/API)**: `PanelSqlBuilder` uses `?` positional binding via `SQL.sqlWithParams` — no SQL injection; ChatBI uses `LongRangeBean` row limiting; no manual serialization bypassing GraphQL selection.
- **Dim 13 (security) — clean areas**: `getSharedDashboard` publicAccess path matches design §194-215 exactly (token lookup → enabled/expire checks → BCrypt `passwordMatches` → direct DAO snapshot read, no RLS). `downloadExportFile`/`getExportTask`/`cancelExportTask` all owner-check (`task.createdBy == resolveOperator`). `createExportTask` source access inherits D3-1 RLS. `JumpRule`/`DashboardFilterUrlCodec` do only client-side `${field}` substitution (no SSRF/server-side fetch). SQL is parameterized everywhere.
- **Dim 15 (type safety)**: `I*Biz extends ICrudBiz<T>` properly parameterized; `@SuppressWarnings("unchecked")` only at JSON/XDSL boundaries (acceptable); no raw-type contracts.
- **Dim 17/19 (style/naming)**: imports grouped per repo convention; no `System.out/err`; bean naming uses module namespace (no `nop*` platform collision); entity/field naming consistent across ORM/BizModel/GraphQL.
- **Dim 18 (doc-code consistency)**: design docs (all `Status: final`) accurately describe implemented entities/relations/indexes/UKs/dicts/error-codes; drifts found are the specific findings above (ORM name/mandatory nits, action-auth gap).
- **Dim 20 (cross-module)**: `INopDatavPanelBiz` returns only nop-datav or platform-base types (no nop-report entity leak) — dependency direction correct per runtime-design §模块依赖.
- **Dim 21 (unit-test effectiveness)**: applied mutate-and-fail mental test across `AlertAggregator`, `AlertThresholdComparator`, `FilterStateCodec`, `LinkageConfigParser`, `DashboardFilterResolver`, `PanelRefreshConfig`, `ScreenLayoutParser`, `ScreenThemeParser`, ChatBI executors — tests assert concrete values that would fail under mutation; no P-1/P-5 antipatterns; documented security/state-machine contracts are well covered (share password/expiry/enabled 6/6 branches; RLS fail-closed; action-auth deny-by-default; alert OK↔TRIGGERED + rearm; report pending→running→succeeded/failed/skipped; process-restart recovery; misfire-grace; ChatBI max-iterations + operator-passing + validation-feedback). The only effectiveness gap is Dim16-01.

## Audit blind spots / self-assessment

- The full Maven build (`./mvnw test -pl nop-datav -am`) was NOT executed in this session (no build requested; findings are from static reading + targeted subagent cross-checks). A green build pass would strengthen confidence that no compile/test regressions hide behind these findings.
- Checkstyle not run (Dim 17 relies on visual inspection).
- i18n completeness (zh-CN vs en) for every dict/label was sampled, not exhaustively diffed.
- The `nop-datav-chart` submodule contents were not deeply inspected (it predates the D0–D6 phases and holds chart-rendering assets outside the audited service surface).

## Contract-drift summary (design-doc ↔ code)

| Drift | Design anchor | Severity |
|-------|---------------|----------|
| 8 share/export custom actions + their CRUD have no action-auth role bindings | permission-sharing §34-49/§222/§370-377 | P1 (Dim08-01) |
| Share inherited CRUD leaks passwordHash + bypasses owner (design §223 claim incomplete) | permission-sharing §223/§207 | P1 (Dim13-01) |
| Export cancel not polled during execution | permission-sharing §318/§310 | P1 (Dim07-01) |
| Per-request recovery call nukes in-flight tasks (recovery should be startup-only) | permission-sharing §322-326 | P1 (Dim14-01) |
| Report SMTP inside runInNewSession (long txn + remote call) | schedule-report §3 (runInNewSession intent) | P1 (Dim14-02) |
| Alert persist-before-notify | schedule-report §269-273 | P2 (Dim14-03) |
| Schedulers catch Throwable vs "Error should propagate" | schedule-report §3/§9 | P2 (Dim14-04) |
| Two ErrorCodes defined but their documented throw sites unimplemented | permission-sharing §318 / ai-design §F | P2 (Dim09-04) |
| ScreenSnapshot.snapshotContent mandatory / UK name | screen-design §1.3/§84 | P2 (Dim04-01/02) |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
