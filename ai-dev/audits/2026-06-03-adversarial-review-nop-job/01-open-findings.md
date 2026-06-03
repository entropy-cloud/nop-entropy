# Adversarial Review: nop-job Module (Round 1)

**Date**: 2026-06-03
**Scope**: nop-job module — re-examination after May 2026 remediation
**Approach**: Discovery-oriented, starting from code anomalies. Focused on verifying prior fix quality and finding residual/new issues the May 18 audits missed.

**Heuristics used**: 代码生成受害者 (fix-induced bugs), 异常路径侦探, 事务边界追踪者, 未来破坏者

**Dedup**: Prior audits (2026-05-18-adversarial-review-nop-job R1+R2, 2026-05-18-deep-audit-nop-job-full) reported 31 findings. The deep audit's remediation plan fixed 11 of 13 targeted items. This report focuses on:
1. Still-open prior findings with status update
2. New issues introduced or exposed by the fixes
3. Issues missed by both prior audits

---

## Prior Finding Status Update

### FIXED (verified in current code)

| Prior ID | Sev | Description | Fix Evidence |
|----------|-----|-------------|-------------|
| F1 | P0 | Dispatch timeout dead code — startTime never set | `JobFireStoreImpl:87` now calls `fire.setStartTime(lockTime)` during dispatch |
| F2 | P1 | Overlay-cancelled fires bypass statistics | `JobScheduleStoreImpl:136-139` now updates `totalFireCount` + `failFireCount` |
| F3 | P2 | completeFireAndUpdateSchedule idempotency guard misses SUCCESS/FAILED | `JobFireStoreImpl:108-113` `TERMINAL_FIRE_STATUSES` now includes all 4 terminal states |
| F4(partial) | P2 | toTriggerSpec copy-pasted 4x | Consolidated into `TriggerSpecHelper`; `pauseCalendars` now parsed from DB column |
| F7 | P1 | CancelJobExecutionContext missing sharding attrs | `DefaultJobCancelHandler:101-106` now sets `shardingIndex`, `shardingTotal`, `targetHost` |
| F8 | P2 | Worker overwrites SUSPICIOUS task status | `JobWorkerScannerImpl:205-208` SUSPICIOUS in guard; also in `completeTaskWithFailure:255-259` |
| R2-1 | P0 | PARALLEL strategy activeFireCount drift | `JobPlannerScannerImpl:169-174` handles PARALLEL with `shouldParallel()` |
| R2-5/F11 | P1 | SUSPICIOUS invisible to resolveFinalFireStatus | `JobCompletionProcessorImpl:289-301` treats SUSPICIOUS as pending |
| R2-12 | P2 | RECOVERY with no failed fires never fires | `JobScheduleStoreImpl:161-189` creates new fire when no failed fires exist |
| R2-19 | P2 | OnceTrigger never constructed | `TriggerBuilder` now handles `repeatInterval <= 0` → `OnceTrigger` |
| F10 | P2 | NopJobTaskBizModel bare CRUD — unrestricted delete | Now overrides `delete()` throwing `ERR_JOB_TASK_DELETE_NOT_ALLOWED` |
| F15 | P2 | overlayFireAndAdvanceSchedule no try-catch | `JobScheduleStoreImpl:125-132` now wraps cancel loop in try-catch |
| F12 | P2 | insertManualFire discard path silent | Now returns `boolean` (line 242), BizModel checks return value |

### STILL EXISTS

| Prior ID | Sev | Description | Current Status |
|----------|-----|-------------|----------------|
| F9 | P1 | NopRetryJobRetryBridge returns jobFireId as retryRecordId | Still returns `event.getJobFireId()` (line 65). Design limitation of async retry API. |
| F14 | P3 | JobFireResult.CONTINUE field vs method name collision | Still present. Low priority API design smell. |
| F4(residual) | P2 | maxFailedCount still hardcoded to 0 | `TriggerSpecHelper:34` — `spec.setMaxFailedCount(0)`. No ORM column exists to source this value. |

---

## New Findings

### [AR-1] fire.startTime Stores Lock Expiry Time, Not Actual Start — Duration Calculations Wrong for Cancel/Timeout Paths

- **文件**: `JobFireStoreImpl.java:82-91`, `JobTimeoutCheckerImpl.java:248`, `JobFireStoreImpl.java:144`
- **证据片段**:
  ```java
  // JobFireStoreImpl.java:82-88
  long now = fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis();
  Timestamp lockTime = new Timestamp(now + Math.max(lockTimeoutMs, 1));
  for (NopJobFire fire : fires) {
      fire.setStartTime(lockTime);  // ← FUTURE time, not actual start
  }
  
  // JobFireStoreImpl.java:144 — cancelFire duration calculation
  fire.setDurationMs(calculateDuration(fire.getStartTime(), cancelTime));
  // startTime > cancelTime → negative → Math.max(0L) = 0ms always
  
  // JobTimeoutCheckerImpl.java:248 — dispatch timeout deadline
  long deadline = startTime.getTime() + dispatchTimeoutMs;
  // deadline = (now + 60s) + 300s = now + 360s instead of now + 300s
  ```
- **严重程度**: P1
- **现状**: The F1 fix set `fire.startTime` during dispatch, solving the null-startTime skip. But the value set is `now + lockTimeoutMs` (a future timestamp representing the lock expiry), not the actual dispatch start time. This creates two downstream bugs:
  1. **Duration always 0 for canceled DISPATCHING fires**: `cancelFire` calculates `durationMs = endTime - startTime`, but `startTime` is in the future → negative → clamped to 0.
  2. **Dispatch timeout extended by lockTimeoutMs**: The deadline becomes `lockTimeoutMs + dispatchTimeoutMs` (60s + 300s = 360s) instead of just `dispatchTimeoutMs` (300s).
- **风险**: Operators see 0ms duration for canceled dispatching fires on the dashboard. Dispatch timeout takes 20% longer than configured. The semantic confusion between "lock expiry" and "start time" may cause further bugs as the codebase evolves.
- **建议**: Set `startTime` to `new Timestamp(now)` (the actual current time) during dispatch. The lock expiry signal can be stored in `updateTime` (which is already set to `lockTime`) — this preserves the lock mechanism without corrupting `startTime` semantics.
- **信心水平**: 确定
- **发现来源视角**: 代码生成受害者 (F1 fix introduced new semantic bug)

---

### [AR-2] Recovery Fire Missing Critical Fields — triggerSource, retryPolicyId, jobParamsSnapshot, plannerInstanceId

- **文件**: `JobScheduleStoreImpl.java:165-177` (no-failed-fires branch)
- **证据片段**:
  ```java
  // JobScheduleStoreImpl.java:165-177 — recovery new-fire path
  NopJobFire newFire = new NopJobFire();
  newFire.setJobScheduleId(schedule.getJobScheduleId());
  newFire.setNamespaceId(schedule.getNamespaceId());
  newFire.setGroupId(schedule.getGroupId());
  newFire.setJobName(schedule.getJobName());
  newFire.setScheduledFireTime(fireTime);
  newFire.setFireStatus(FIRE_STATUS_WAITING);
  newFire.setCreatedBy("system");
  newFire.setCreateTime(fireTime);
  newFire.setUpdatedBy("system");
  newFire.setUpdateTime(fireTime);
  newFire.setPartitionIndex(schedule.getPartitionIndex());
  newFire.setExecutorKind(schedule.getExecutorKind());
  // MISSING: triggerSource, plannerInstanceId, retryPolicyId, jobParamsSnapshot
  
  // Compare with JobPlannerScannerImpl.java:184-204 — buildFire
  fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_SCHEDULE);  // ← missing
  fire.setPlannerInstanceId(AppConfig.hostId());                       // ← missing
  fire.setRetryPolicyId(schedule.getRetryPolicyId());                 // ← missing
  fire.getJobParamsSnapshotComponent().set_jsonValue(...);             // ← missing
  ```
- **严重程度**: P1
- **现状**: The R2-12 fix added a new-fire creation path in `recoveryFireAndAdvanceSchedule` when no failed fires exist. This path creates a fire with minimal fields, missing 4 fields that `buildFire` (planner) sets:
  1. **`triggerSource`** — `null` instead of `TRIGGER_SOURCE_RECOVERY` (value=3). This breaks `shouldAdvanceFixedDelaySchedule` in `cancelFire` (checks `fire.getTriggerSource() == TRIGGER_SOURCE_SCHEDULE`).
  2. **`retryPolicyId`** — `null`. The completion processor falls back to `schedule.getRetryPolicyId()`, so this is mitigated but loses fire-level override.
  3. **`jobParamsSnapshot`** — `null`. `DefaultJobTaskBuilder.buildTasks` puts `fire.getJobParamsSnapshotComponent().get_jsonMap()` into task payload. **If null/empty, workers receive no job parameters.**
  4. **`plannerInstanceId`** — `null`. Audit trail incomplete.
- **风险**: Recovery fires execute with no job parameters — the worker invoker receives empty payload, likely causing immediate task failure. For RECOVERY block strategy, the fire is created, dispatched, but the worker cannot execute the job because `jobParamsSnapshot` is empty.
- **建议**: Extract the common fire construction logic from `buildFire` into a shared method (or call `buildFire`-equivalent from the recovery path). At minimum, set `triggerSource=TRIGGER_SOURCE_RECOVERY`, `retryPolicyId`, `plannerInstanceId`, and `jobParamsSnapshot`.
- **信心水平**: 确定
- **发现来源视角**: 代码生成受害者 (R2-12 fix introduced fire with incomplete fields)

---

### [AR-3] insertManualFire Overlay Path Missing try-catch — Inconsistent with overlayFireAndAdvanceSchedule

- **文件**: `JobScheduleStoreImpl.java:224-229`
- **证据片段**:
  ```java
  // JobScheduleStoreImpl.java:224-229 — insertManualFire
  if (isOverlay(schedule)) {
      for (NopJobFire activeFire : activeFires) {
          cancelFire(activeFire, updateTime);          // no try-catch
          cancelTasks(activeFire.getJobFireId(), updateTime);  // no try-catch
      }
  }
  
  // JobScheduleStoreImpl.java:125-132 — overlayFireAndAdvanceSchedule (FIXED in F15)
  for (NopJobFire activeFire : activeFires) {
      try {
          cancelFire(activeFire, cancelTime);
          cancelTasks(activeFire.getJobFireId(), cancelTime);
      } catch (Exception e) {
          LOG.warn("nop.job.schedule.cancel-fire-failed:fireId={}", activeFire.getJobFireId(), e);
      }
  }
  ```
- **严重程度**: P2
- **现状**: The F15 fix added try-catch to `overlayFireAndAdvanceSchedule`'s cancel loop, but the same pattern in `insertManualFire` (triggered by `triggerNow`) was not fixed. If `cancelFire` or `cancelTasks` throws during a manual trigger (e.g., concurrent dispatcher bumps version → optimistic lock failure), the entire `REQUIRES_NEW` transaction rolls back — the manual fire is not created and the operator gets an error instead of a best-effort fire.
- **风险**: Manual trigger failures under concurrent dispatch. At 10x scale, the race window widens. Operator sees error when trying to manually trigger a schedule that has an active fire being concurrently dispatched.
- **建议**: Add the same try-catch pattern to `insertManualFire`'s overlay cancel loop.
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-4] retryRecordId Dead Write — Set After Persist, Never Flushed

- **文件**: `JobCompletionProcessorImpl.java:202,228`
- **证据片段**:
  ```java
  // JobCompletionProcessorImpl.java:202
  fireStore.completeFireAndUpdateSchedule(fire, schedule);  // persists fire + schedule
  
  // JobCompletionProcessorImpl.java:226-229
  String retryRecordId = retryBridge.onFireFailed(event);
  if (retryRecordId != null) {
      fire.setRetryRecordId(retryRecordId);  // in-memory only, never persisted
  }
  ```
- **严重程度**: P2
- **现状**: The completion processor persists the fire entity (line 202), then calls `handleRetryAndAlarm` which sets `fire.setRetryRecordId(...)` on the in-memory object (line 228). This value is never flushed to the database — there is no subsequent `update` call. The `retryRecordId` column in the database is always `null` for failed fires that go through the retry bridge.
- **风险**: Cross-system tracing between nop-job and nop-retry is broken at the data level. The `retryRecordId` ORM column and its delta xmeta description (`由 nop-retry 创建后回填`) are misleading — the value is never actually stored. Compounded with F9 (bridge returns `jobFireId` not real retry ID), the entire retry traceability chain is non-functional.
- **建议**: Move `handleRetryAndAlarm` before `completeFireAndUpdateSchedule`, or add a separate update call after setting `retryRecordId`. Alternatively, if the async retry design makes the real ID unavailable synchronously, document that `retryRecordId` is not populated and consider a callback mechanism from nop-retry.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-5] CLAIMED-but-Never-RUNNING Tasks Are Orphaned — Never Timed Out, Never Reclaimed

- **文件**: `JobTaskStoreImpl.java:74-83`, `JobTimeoutCheckerImpl.java:144-145,211-212`
- **证据片段**:
  ```java
  // JobTaskStoreImpl.java:75-78 — fetchRunningTasks
  query.addFilter(FilterBeans.eq(PROP_NAME_taskStatus, TASK_STATUS_RUNNING));
  // Only fetches status=20 (RUNNING). CLAIMED (10) is excluded.
  
  // JobTimeoutCheckerImpl.java:211-212 — tryMarkSuspiciousIfWorkerGone
  if (task.getTaskStatus() == null || task.getTaskStatus() != _NopJobCoreConstants.TASK_STATUS_RUNNING) {
      return;  // CLAIMED tasks skipped
  }
  ```
- **严重程度**: P2
- **现状**: The task lifecycle is WAITING→CLAIMED→RUNNING→terminal. The timeout checker only fetches and processes `RUNNING` tasks. If a worker crashes after claiming a task (CLAIMED status) but before setting it to RUNNING, the task is:
  - Not found by `fetchRunningTasks` (only queries RUNNING)
  - Not found by `fetchWaitingTasks` (only queries WAITING)
  - Not marked SUSPICIOUS (check guards against non-RUNNING)
  - Not timed out (never appears in timeout scanner)
  
  The task sits in CLAIMED status indefinitely. The fire stays in RUNNING status (tasks exist that aren't terminal), and the completion processor sees CLAIMED as `hasPendingTask=true`, keeping the fire alive forever.
- **风险**: Worker crashes during task startup create permanent orphans. The fire never completes, the schedule's `activeFireCount` never decrements, and the schedule appears permanently "active" on the dashboard. For DISCARD block strategies, this blocks all future fires.
- **建议**: Either: (a) extend `fetchRunningTasks` to also query CLAIMED status and handle them in the timeout checker, or (b) add a `fetchClaimedTasks` query and a separate reclamation mechanism that resets stale CLAIMED tasks back to WAITING after a configurable timeout.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-6] Planner Parallel Path — setActiveFireCount(0) Is Dead Code After Persist

- **文件**: `JobPlannerScannerImpl.java:169-174`
- **证据片段**:
  ```java
  // JobPlannerScannerImpl.java:169-174
  if (shouldParallel(schedule)) {
      scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime,
              _NopJobCoreConstants.FIRE_STATUS_WAITING);
      schedule.setActiveFireCount(0);  // ← dead write: schedule already persisted inside store
      continue;
  }
  ```
- **严重程度**: P3
- **现状**: The R2-1 fix added `shouldParallel()` handling and `schedule.setActiveFireCount(0)` after the store call. However, `insertFireAndAdvanceSchedule` already persists the schedule (with `activeFireCount + 1`) inside its `REQUIRES_NEW` transaction. The subsequent `schedule.setActiveFireCount(0)` only modifies the in-memory entity — it's never persisted again. The DB has `activeFireCount = oldCount + 1`, but the in-memory object has `activeFireCount = 0`.
- **风险**: Currently harmless — the stale in-memory value is not read again in the same scan iteration. But it's misleading dead code that suggests a semantic intent (parallel fires shouldn't count toward activeFireCount) that doesn't match the actual DB state. If a future developer reads this code and assumes `activeFireCount` is 0 for parallel schedules, they'll build incorrect logic.
- **建议**: Either: (a) persist the `activeFireCount = 0` correction with a separate update call, or (b) remove the dead write and add a comment explaining that `insertFireAndAdvanceSchedule` correctly increments the counter, and parallel fire counting is handled differently.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-7] maxFailedCount Hardcoded to 0 in TriggerSpecHelper — No ORM Column to Source Value

- **文件**: `TriggerSpecHelper.java:34`
- **证据片段**:
  ```java
  // TriggerSpecHelper.java:34
  spec.setMaxFailedCount(0);  // always 0, no DB column for this
  ```
- **严重程度**: P3
- **现状**: The F4 fix consolidated the 4 copies of `toTriggerSpec` into `TriggerSpecHelper` and wired `pauseCalendars` to the DB column. However, `maxFailedCount` remains hardcoded to 0. There is no corresponding ORM column on `NopJobSchedule` for this field. The trigger infrastructure (`LimitCountTrigger` etc.) supports `maxFailedCount` in its API, but nop-job never provides a non-zero value.
- **风险**: Low — currently no user-facing configuration is silently ignored (unlike `pauseCalendarSpec` which was fixed). But the `TriggerSpec.maxFailedCount` field exists and suggests a "stop after N failures" feature that doesn't work. If someone adds an ORM column for this in the future, they need to also update this helper.
- **建议**: Add a comment documenting that `maxFailedCount` is not yet supported. If the feature is intended, add an ORM column. If not, consider removing the field from `TriggerSpec`.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

## Overall Assessment

### Top 3 Actionable Directions

**1. F1 fix残留 — startTime语义错误 (AR-1, P1)**

The F1 fix solved the null-startTime skip by setting a value, but chose the wrong semantic: `startTime = now + lockTimeoutMs` (future time) instead of `now` (actual start). This cascades into wrong duration calculations (always 0 for canceled dispatching fires) and extended dispatch timeout (60s longer than configured). The fix should set `startTime = new Timestamp(now)` and let `updateTime` serve as the lock expiry signal.

**2. R2-12 fix残留 — recovery fire缺失关键字段 (AR-2, P1)**

The R2-12 fix correctly creates a new fire when no failed fires exist, but the fire is missing `triggerSource`, `retryPolicyId`, `plannerInstanceId`, and most critically `jobParamsSnapshot`. Without job params, the worker receives an empty payload and the task will likely fail immediately — defeating the purpose of the recovery fire.

**3. CLAIMED任务孤儿 (AR-5, P2) + insertManualFire overlay路径 (AR-3, P2)**

Two independent issues that compound under worker instability: CLAIMED-but-never-RUNNING tasks are never timed out (AR-5), and the manual trigger overlay path can fail under concurrent dispatch (AR-3). Together they create scenarios where schedules get stuck with no self-healing mechanism.

### Blind Spot Assessment

This review likely missed:

1. **CONCURRENCY: `insertTasksAndMarkFireDispatching` uses `updateEntityDirectly` without version check** — noted but not escalated. A concurrent cancel could be silently overwritten with RUNNING. Narrow race window but worth monitoring.

2. **Retry integration depth**: The F9 issue (retryRecordId) and AR-4 (dead write) combine to make the entire retry traceability chain non-functional. The full async retry flow (retry policy selection, retry record lifecycle, retry callback into nop-job) was not deeply traced. The nop-retry adapter's `callAsync` + `whenComplete` pattern may have additional semantic gaps.

3. **Multi-coordinator HA**: The review assumed single coordinator topology. In HA deployment, the `JobPartitionResolver`'s `lastChangeTime` non-volatile field (noted in passing) and the interaction between multiple planners dispatching the same schedule's fires may have additional failure modes.

4. **Performance under load**: No profiling was done. The `fetchRunningTasks` + `fetchDispatchingFires` query patterns, batch size defaults, and lock timeout values were not evaluated against realistic data volumes.

5. **Test coverage for new fixes**: The R2-12 fix (recovery fire creation) and the F1 fix (startTime during dispatch) were not verified to have corresponding tests. The previous remediation added tests for cancel and timeout scenarios, but the new code paths may be untested.

---

## Finding Summary Table

| # | Severity | Short Description | Confidence |
|---|----------|-------------------|------------|
| AR-1 | **P1** | startTime = lock expiry (future) → cancel duration always 0; dispatch timeout +60s | Certain |
| AR-2 | **P1** | Recovery fire missing triggerSource/retryPolicyId/jobParamsSnapshot/plannerInstanceId | Certain |
| AR-3 | P2 | insertManualFire overlay cancel loop missing try-catch (inconsistent with planner path) | Certain |
| AR-4 | P2 | retryRecordId dead write — set after persist, never flushed | Certain |
| AR-5 | P2 | CLAIMED-but-never-RUNNING tasks orphaned — never timed out or reclaimed | Certain |
| AR-6 | P3 | Planner parallel path setActiveFireCount(0) is dead code after persist | Certain |
| AR-7 | P3 | maxFailedCount hardcoded to 0 — no ORM column to source value | Certain |

**Total**: 7 new findings — 2×P1, 3×P2, 2×P3

**Prior findings status**: 13 of 15 checked; 11 fixed, 2 still open (F9 P1, F14 P3), 1 partially fixed (F4 maxFailedCount residual)

---

## 严重程度分布 (Round 1)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | 修复引入的语义错误 (startTime, recovery fire fields) |
| P2      | 3    | 事务边界不一致, 死写, 任务孤儿 |
| P3      | 2    | 死代码, 未实现功能残留 |

---

## 深挖第 2 轮追加

**Date**: 2026-06-03
**Scope**: nop-job module — 第 2 轮深挖，从第 1 轮盲区出发，聚焦并发安全、数据完整性和 Worker-Coordinator 边界。
**Heuristics used**: 10x规模运维, 模型攻击者, 组合爆炸测试, GraphQL 契约考古, IoC 侦探

**Dedup**: 已排除与 AR-1~AR-7（R1）、深度审计 01-21 维度的重复。NEW-5（startTime = future time）与 AR-1 高度重叠，已排除。

---

### [AR-8] `rerunFire` 忽略 `insertManualFire` 返回值 — DISCARD 策略下静默丢弃

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:62-74`
- **证据片段**:
  ```java
  // NopJobFireBizModel.java:62-74
  public void rerunFire(@Name("id") String id, IServiceContext context) {
      NopJobFire sourceFire = fireStore.loadFire(id);
      if (!isRerunnableStatus(sourceFire.getFireStatus())) {
          throwRerunNotAllowed(sourceFire, "rerunFire");
      }
      NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());
      validateRerunSchedule(schedule, "rerunFire");
      NopJobFire rerunFire = buildRecoveryFire(sourceFire, context);
      scheduleStore.insertManualFire(schedule, rerunFire);  // ← 返回 boolean 被忽略!
      afterEntityChange(rerunFire, "rerunFire", context);
  }
  
  // 对比 NopJobScheduleBizModel.triggerNow（正确处理）:
  boolean created = scheduleStore.insertManualFire(schedule, fire);
  if (!created) {
      throw new NopException(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_DISCARDED)...;
  }
  ```
- **严重程度**: P1
- **现状**: `rerunFire` 复用了 `insertManualFire`，后者在 blockStrategy=DISCARD 且存在活跃 fire 时返回 `false`（不插入新 fire）。但 `rerunFire` 没有检查返回值，`afterEntityChange` 仍被调用（传入未持久化的 `rerunFire`），调用者得到 200 OK，实际 rerun 被静默丢弃。
- **风险**: 用户通过 GraphQL 发起 rerun，得到成功响应，但任务不会重新执行。审计日志/事件通知可能出现虚假的"rerun 成功"记录。
- **建议**: 与 `triggerNow` 保持一致，检查返回值并在 `false` 时抛出异常。
- **信心水平**: 确定
- **发现来源视角**: GraphQL 契约考古

---

### [AR-9] `completeFireAndUpdateSchedule` 中 schedule 更新无版本检查 — 并发 completion 丢失更新

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:115-128`
- **证据片段**:
  ```java
  // JobFireStoreImpl.java:115-128
  public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
      NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
      if (TERMINAL_FIRE_STATUSES.contains(currentFire.getFireStatus())) {
          return;
      }
      List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(
          Collections.singletonList(fire));  // ← fire 有乐观锁
      if (updated.isEmpty()) {
          return;
      }
      scheduleDao().updateEntityDirectly(schedule);  // ← schedule 无乐观锁!
  }
  ```
- **严重程度**: P1
- **现状**: Fire 使用 `tryUpdateManyWithVersionCheck`（乐观锁），但同一方法中 schedule 使用 `updateEntityDirectly`（无条件覆盖）。当 timeout checker 和 completion processor 同时处理同一 schedule 的不同 fire（例如 PARALLEL 策略下有多个活跃 fire），后执行的 `updateEntityDirectly` 会覆盖前一个对 `activeFireCount`/`totalFireCount` 等计数器的更新。
- **风险**: Schedule 的统计计数器在并发完成场景下系统性偏低。这是系统性的设计问题——planner 的 `insertFireAndAdvanceSchedule`/`advanceScheduleAfterSkip` 等也用 `updateEntityDirectly` 更新 schedule，整个 schedule 更新路径缺乏乐观锁保护。
- **建议**: Schedule 的所有更新路径应使用 `tryUpdateManyWithVersionCheck`。由于 `completeFireAndUpdateSchedule` 运行在 `REQUIRES_NEW` 事务中，版本冲突时可安全重试。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-10] Worker `updateTask` 无版本检查 — 可覆盖 TIMEOUT/CANCELED 状态

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java:41-43`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:202-238`
- **证据片段**:
  ```java
  // JobTaskStoreImpl.java:41-43
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  public void updateTask(NopJobTask task) {
      taskDao().updateEntityDirectly(task);  // ← 无版本检查
  }
  
  // JobWorkerScannerImpl.java:204-238 — handleExecutionResult
  NopJobTask task = taskStore.loadTask(jobTaskId);  // TOCTOU: 读取与写入之间有窗口
  if (task.getTaskStatus() == TASK_STATUS_TIMEOUT
          || task.getTaskStatus() == TASK_STATUS_CANCELED
          || task.getTaskStatus() == TASK_STATUS_SUSPICIOUS) {
      return;  // 应用层保护，但不是原子的
  }
  // ... 修改 task ...
  taskStore.updateTask(task);  // ← 可能覆盖 timeout checker 的 TIMEOUT 标记
  ```
- **严重程度**: P2
- **现状**: Worker 的异步回调（`whenComplete`）在不同线程执行。虽然有应用层状态检查（loadTask 后检查 status），但 `loadTask` 和 `updateTask` 之间不是原子操作。如果 timeout checker 在这个窗口内把 task 标记为 TIMEOUT，worker 的 `updateEntityDirectly` 仍然会用 SUCCESS 覆盖。对比 coordinator 端的 `tryLockTasksForExecute` 使用了 `tryUpdateManyWithVersionCheck`，worker 端缺失了同样的保护。
- **风险**: 在 10x 并发下，task 被超时标记后又被 worker 回调覆盖回 SUCCESS，导致 completion processor 认为 fire 成功，实际上任务可能只执行了一部分。
- **建议**: `updateTask` 应使用 `tryUpdateManyWithVersionCheck`，与 coordinator 端保持一致。版本冲突时检查当前 DB 状态决定是否重试。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-11] `tryMarkDispatchTimeout` 不取消关联 tasks — 产生孤儿任务

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:260-300`
- **证据片段**:
  ```java
  // JobTimeoutCheckerImpl.java:260-300 — tryMarkDispatchTimeout
  private void tryMarkDispatchTimeout(NopJobFire fire, long now) {
      // ... 设置 fire 状态为 TIMEOUT
      fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_TIMEOUT);
      fire.setEndTime(endTime);
      // ... 更新 schedule 统计 ...
      fireStore.completeFireAndUpdateSchedule(fire, schedule);
      // ← 没有 cancel 任何 task!
  }
  
  // 对比 cancelFire（JobFireStoreImpl.java:154-167）:
  for (NopJobTask task : tasks) {
      if (isTaskFinished(task.getTaskStatus())) continue;
      task.setTaskStatus(TASK_STATUS_CANCELED);
      // ...
  }
  ```
- **严重程度**: P2
- **现状**: Dispatch timeout 触发时，fire 被标记为 TIMEOUT 并完成，但关联的未完成 tasks 没有被取消或标记。如果 dispatcher 在 dispatch timeout 之前已经创建了 tasks（状态为 WAITING/CLAIMED/RUNNING），这些 tasks 会变成孤儿——其所属 fire 已终态，不会再被 completion processor 处理，worker 也不会因为 fire 终态而停止执行。
- **风险**: 在 dispatch timeout 场景下积累大量孤儿 tasks，占用 DB 空间，影响查询性能，且 worker 可能继续执行已无意义的任务。
- **建议**: 在 `tryMarkDispatchTimeout` 中，完成 fire 后应查询并取消关联的未完成 tasks，与 `cancelFire` 保持一致。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-12] Recovery 重用旧 fire 的过时 `jobParamsSnapshot` — 用户更新参数不生效

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:190-211`
- **证据片段**:
  ```java
  // JobScheduleStoreImpl.java:190-211 — recovery 有 failedFires 路径
  NopJobFire failedFire = failedFires.get(0);
  // 重置 failedFire 状态
  failedFire.setFireStatus(FIRE_STATUS_WAITING);
  failedFire.setErrorCode(null);
  failedFire.setErrorMessage(null);
  failedFire.setEndTime(null);
  // ... 但不更新 jobParamsSnapshot!
  // failedFire 保留了原始创建时的旧参数
  
  // 对比 buildFire（JobPlannerScannerImpl.java:202）:
  fire.getJobParamsSnapshotComponent().set_jsonValue(
      copyMap(schedule.getJobParamsComponent().get_jsonMap()));  // 从 schedule 取最新参数
  ```
- **严重程度**: P2
- **现状**: Recovery 有 failedFires 时，重用旧 fire（保留其 `jobParamsSnapshot`）。如果用户在 job 失败后更新了 schedule 的 `jobParams`，recovery 仍使用旧参数执行。与 AR-2（空 failedFires 路径缺少 `jobParamsSnapshot`）是同一条 recovery 路径的两个独立缺陷。
- **风险**: 用户更新参数后触发 recovery，期望使用新参数，实际使用旧参数。排障时以为参数已更新，但 recovery 执行的仍是旧版本。
- **建议**: Recovery 路径应始终从当前 schedule 刷新 `jobParamsSnapshot`/`retryPolicyId` 等字段，不依赖旧 fire 的快照。
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试

---

### [AR-13] SUSPICIOUS task 在同一次扫描周期内被转为 TIMEOUT — 无宽限期

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:144-186`
- **证据片段**:
  ```java
  // JobTimeoutCheckerImpl.java:144-186 — scanTaskTimeouts
  for (NopJobTask task : tasks) {
      try {
          if (aliveWorkerIds != null) {
              tryMarkSuspiciousIfWorkerGone(task, aliveWorkerIds);  // 步骤1: RUNNING → SUSPICIOUS
          }
          tryMarkTimeout(task, fireMap, scheduleMap);               // 步骤2: 立即检查 SUSPICIOUS → TIMEOUT
      } catch (Exception e) { ... }
  }
  
  // tryMarkTimeout:341-344
  if (taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
      markSuspiciousAsTimeout(task, fireMap, scheduleMap);  // 直接转 TIMEOUT，无宽限期
      return;
  }
  ```
- **严重程度**: P2
- **现状**: SUSPICIOUS 状态的设计意图是"worker 暂时不可达，等待恢复"。但在 `scanTaskTimeouts` 循环中，`tryMarkSuspiciousIfWorkerGone` 将 RUNNING 标记为 SUSPICIOUS 后，紧接着 `tryMarkTimeout` 在同一个循环迭代中检查到 SUSPICIOUS，直接转为 TIMEOUT。由于两者操作同一个 Java 对象实例，状态转换在同一 scan 周期内完成，SUSPICIOUS 状态从未真正"存在过"。
- **风险**: 短暂网络抖动（5秒内）导致的 SUSPICIOUS task 直接被终止，worker 恢复后发现自己正在执行的任务已被标记为 TIMEOUT。在 10x 规模下，网络抖动更频繁，这个问题的触发概率显著增加。
- **建议**: 在 `tryMarkSuspiciousIfWorkerGone` 成功标记 SUSPICIOUS 后 `continue`（跳过当前周期的 timeout 检查），让 SUSPICIOUS task 在下一次扫描周期才被转为 TIMEOUT。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-14] `copyMap` 返回原始引用而非副本 — 快照数据可能被意外修改

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:229-231`
- **证据片段**:
  ```java
  // JobPlannerScannerImpl.java:229-231
  private Map<String, Object> copyMap(Map<String, Object> map) {
      return map == null ? Collections.emptyMap() : map;
  }
  
  // 调用处（第 202 行）:
  fire.getJobParamsSnapshotComponent().set_jsonValue(
      copyMap(schedule.getJobParamsComponent().get_jsonMap()));
  
  // JsonOrmComponent.set_jsonValue 也不做深拷贝:
  public void set_jsonValue(Object jsonValue) {
      markDirty();
      this.jsonValue = jsonValue;  // 直接引用赋值
  }
  ```
- **严重程度**: P3
- **现状**: 方法名 `copyMap` 暗示深拷贝，但实际返回原始 Map 引用。`JsonOrmComponent.set_jsonValue` 也不做拷贝。fire 的 `jobParamsSnapshot` 与 schedule 的 `jobParams` 共享同一个 Map 对象。实际影响较低，因为 fire 创建后立即持久化（ORM flush 时做序列化），后续 schedule 的修改不影响已保存的 fire。但如果 Map 中嵌套了可变对象且在 flush 之前被修改，可能导致数据污染。
- **风险**: 方法名误导后续开发者，假设"快照"是独立的。理论上有 ORM flush 前的数据污染窗口。
- **建议**: 改为 `new HashMap<>(map)` 或重命名方法为 `unwrapMap`/`getOrEmpty`。
- **信心水平**: 很可能
- **发现来源视角**: 模型攻击者

---

### [AR-15] `findFirstErrorTask` 返回第一个非 SUCCESS task 而非最高优先级错误 — 广播场景下错误信息不准

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:328-336`
- **证据片段**:
  ```java
  // JobCompletionProcessorImpl.java:328-336
  private NopJobTask findFirstErrorTask(List<NopJobTask> tasks) {
      for (NopJobTask task : tasks) {
          Integer taskStatus = task.getTaskStatus();
          if (taskStatus != null && taskStatus != _NopJobCoreConstants.TASK_STATUS_SUCCESS) {
              return task;  // 返回第一个非 SUCCESS，可能是 CANCELED 而非 FAILED
          }
      }
      return null;
  }
  
  // resolveFinalFireStatus 的优先级: TIMEOUT > FAILED > CANCELED > SUCCESS
  // 但 findFirstErrorTask 按列表顺序，不匹配上述优先级
  ```
- **严重程度**: P3
- **现状**: 当 fire 有多个 task（广播场景），`resolveFinalFireStatus` 按优先级 TIMEOUT > FAILED > CANCELED 确定 fire 终态。但 `findFirstErrorTask` 按列表顺序返回第一个非 SUCCESS task。如果 task 列表顺序是 [CANCELED, FAILED]，fire 状态为 FAILED（优先级更高），但 errorCode 被设为 CANCELED task 的 "JOB_CANCELED" 而非 FAILED task 的实际错误码。
- **风险**: 广播场景下 fire 的 errorCode 不反映真正的失败原因，排障时误导。
- **建议**: 调整 `findFirstErrorTask` 的优先级逻辑与 `resolveFinalFireStatus` 一致（TIMEOUT > FAILED > CANCELED），或重命名为 `findFirstNonSuccessTask`。
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试

---

### [AR-16] `RpcBroadcastTaskBuilder` 不设置 taskPayload — 广播 task 缺少参数数据

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java:70-88`
- **证据片段**:
  ```java
  // RpcBroadcastTaskBuilder.java:70-88 — 创建 broadcast task
  NopJobTask task = new NopJobTask();
  task.setJobFireId(fire.getJobFireId());
  task.setTaskNo(i + 1);
  task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_WAITING);
  task.setWorkerInstanceId(AppConfig.hostId());
  task.setPartitionIndex(fire.getPartitionIndex());
  task.setTargetHost(instance.getAddr() + ":" + instance.getPort());
  task.setShardingIndex(i);
  task.setShardingTotal(total);
  // ← 没有设置 taskPayload!
  
  // 对比 DefaultJobTaskBuilder.java:25-28:
  task.getTaskPayloadComponent().set_jsonValue(Map.of(
      "jobFireId", fire.getJobFireId(),
      "jobParamsSnapshot", emptyIfNull(fire.getJobParamsSnapshotComponent().get_jsonMap())
  ));
  ```
- **严重程度**: P3
- **现状**: 广播模式的 task 不设置 `taskPayload`，与 `DefaultJobTaskBuilder` 不一致。`DefaultJobExecutionContextBuilder` 的 `resolveJobParams` 从 fire 的 `jobParamsSnapshot` 读取参数（不依赖 taskPayload），所以当前不影响运行时执行。但数据层面不一致——普通 task 有 payload，广播 task 没有。
- **风险**: 如果后续有人依赖 taskPayload 获取参数（比如数据迁移、日志分析、或者 RPC worker 的不同实现），广播 task 会丢失参数。运维排查时可能困惑于数据不一致。
- **建议**: 在广播 task 中也设置 taskPayload，与 `DefaultJobTaskBuilder` 保持一致。
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探

---

## 第 2 轮总评

### Top 3 Actionable Directions

**1. rerunFire 静默丢弃 (AR-8, P1)**

这是最直接的用户可感知 bug。GraphQL API 返回成功但实际不执行。修复简单：检查 `insertManualFire` 返回值。与 `triggerNow` 的处理方式已有正确参考。

**2. Schedule 并发更新丢失 (AR-9, P1)**

`completeFireAndUpdateSchedule` 对 fire 有乐观锁但对 schedule 没有。这是系统性的——整个 schedule 更新路径（planner/completion/cancel）都使用 `updateEntityDirectly`。在 PARALLEL 策略下多个 fire 同时完成时，计数器漂移不可避免。

**3. SUSPICIOUS → TIMEOUT 同周期转换 (AR-13, P2) + Recovery 过时参数 (AR-12, P2)**

两个独立但都影响系统自愈能力的问题：SUSPICIOUS 状态从未真正存在（被同周期吞掉），Recovery 使用旧参数而非最新 schedule 参数。前者让短暂的 worker 不可达直接变为永久失败，后者让用户参数更新在 recovery 场景下不生效。

### Blind Spot Assessment

本轮仍可能遗漏：

1. **Multi-coordinator HA**: 未验证多 coordinator 实例同时 dispatch 同一 schedule 的 fire 时，`tryLockFiresForDispatch` 的 `REQUIRES_NEW` + 乐观锁是否充分。
2. **RPC Job Invoker 的超时和重试**: `RpcJobInvoker` 的 HTTP 调用失败场景、超时配置、以及与 `NopRetryJobRetryBridge` 的集成完整性。
3. **Calendar 排除日期算法**: `HolidayCalendar`/`AnnualCalendar` 的边界条件（闰年、时区切换）。
4. **大量 schedule 的 planner 扫描性能**: `fetchSchedulesForPlan` 的查询在 10k+ schedule 下的表现。
5. **Worker 端 `executeTask` 的 CLAIMED→RUNNING 转换也有无版本检查问题**（与 AR-10 同源），但 CLAIMED 窗口极短，实际风险更低。

---

## 严重程度分布 (Round 2)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | 静默丢弃 (rerunFire), 并发更新丢失 (schedule) |
| P2      | 4    | Worker 竞态覆盖, dispatch timeout 孤儿 task, recovery 过时参数, SUSPICIOUS 无宽限期 |
| P3      | 3    | 误导性方法名 (copyMap), 错误优先级不一致, 广播 task 缺 payload |

## 深挖第 3 轮追加

**Date**: 2026-06-03
**Scope**: nop-job module — 第 3 轮独立审查，从 RPC 取消路由和手动触发统计完整性角度出发
**Heuristics used**: GraphQL 契约考古, 事务边界追踪者, IoC 侦探

**Dedup**: 已排除与 AR-1~AR-16（R1+R2）的重叠。AR-7 报告了 CancelJobExecutionContext 缺少 sharding 属性（已修复），但本报告发现的是取消上下文中 `targetHost` 路由使用了错误字段——这是完全不同的缺陷。AR-3 报告了 insertManualFire overlay 路径缺少 try-catch，但本报告发现的是该路径缺少统计计数器更新——独立问题。

---

### [AR-17] CancelJobExecutionContext `resolveJobParams` 不注入 targetHost header — RPC 广播取消无法路由到正确节点

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobCancelHandler.java:61-68,83`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobCancelHandler.java:106`
- **证据片段**:
  ```java
  // DefaultJobCancelHandler.java:61-68 — CancelJobExecutionContext 的 resolveJobParams
  private static Map<String, Object> resolveJobParams(NopJobSchedule schedule, NopJobFire fire) {
      Map<String, Object> jobParams = fire.getJobParamsSnapshotComponent().get_jsonMap();
      if (jobParams != null) {
          return jobParams;  // ← 直接返回快照，不注入 targetHost header
      }
      Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
      return scheduleParams == null ? Collections.emptyMap() : scheduleParams;
  }

  // DefaultJobCancelHandler.java:105-106 — 属性设置了但 RpcJobInvoker 不读
  if (task.getWorkerAddress() != null)
      setAttribute("targetHost", task.getWorkerAddress());

  // 对比 DefaultJobExecutionContextBuilder.java — 执行路径正确注入:
  // resolveJobParams() 中:
  if (task.getTargetHost() != null && !task.getTargetHost().isBlank()) {
      Map<String, Object> headers = (Map<String, Object>) jobParams.computeIfAbsent("headers", k -> new HashMap<>());
      headers.put(ApiConstants.HEADER_SVC_TARGET_HOST, task.getTargetHost());
  }
  ```
- **严重程度**: P1
- **现状**: RPC 广播任务的取消路径存在两个独立缺陷：
  1. `CancelJobExecutionContext.resolveJobParams()` 直接返回 `fire.getJobParamsSnapshotComponent().get_jsonMap()`，**不注入 `targetHost` header**。`RpcJobInvoker.cancelAsync()` 从 `jobCtx.getJobParams()` 中读取 `headers` 来构建 RPC 请求——没有 `targetHost` header，取消 RPC 请求发送到负载均衡器而非特定节点。
  2. 第 106 行设置 `setAttribute("targetHost", task.getWorkerAddress())`，但 (a) `RpcJobInvoker.injectFrameworkHeaders` 不读取 `targetHost` 属性，(b) `workerAddress` 与 `targetHost` 是不同字段——`targetHost` 由 `RpcBroadcastTaskBuilder` 设置为路由目标（如 `10.0.1.5:8080`），而 `workerAddress` 是执行 worker 的地址（可能不同）。

  执行路径（`DefaultJobExecutionContextBuilder.resolveJobParams`）正确地从 `task.getTargetHost()` 注入 `ApiConstants.HEADER_SVC_TARGET_HOST` header。取消路径完全没有这个注入逻辑。
- **风险**: 对 RPC 广播执行器（`executorKind=rpcBroadcast`）的取消操作发送到错误的（或随机的）节点。目标节点收到取消信号，但实际执行任务的节点可能不会。在多实例部署中，取消操作无效，任务继续执行直到超时。
- **建议**: 在 `CancelJobExecutionContext.resolveJobParams()` 中添加与 `DefaultJobExecutionContextBuilder.resolveJobParams()` 相同的 `targetHost` 注入逻辑，从 `task.getTargetHost()` 读取（而非 `task.getWorkerAddress()`）。
- **信心水平**: 确定
- **发现来源视角**: GraphQL 契约考古（追踪 cancelAsync 的完整 RPC 路径）

---

### [AR-18] `insertManualFire` overlay 路径取消 fires 不更新 `totalFireCount`/`failFireCount` — 手动触发覆盖绕过统计

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:263-277`
- **证据片段**:
  ```java
  // JobScheduleStoreImpl.java:263-277 — insertManualFire overlay path
  if (isOverlay(schedule)) {
      for (NopJobFire activeFire : activeFires) {
          cancelFire(activeFire, updateTime);    // ← 取消了 fire
          cancelTasks(activeFire.getJobFireId(), updateTime);
      }
  }

  fireDao().saveEntityDirectly(fire);

  schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);
  schedule.setActiveFireCount(isOverlay(schedule) ? 1 : defaultInt(schedule.getActiveFireCount()) + 1);
  if (isOverlay(schedule) && !activeFires.isEmpty()) {
      schedule.setLastEndTime(updateTime);
      schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
  }
  // ← 没有 totalFireCount += cancelledCount
  // ← 没有 failFireCount += cancelledCount

  // 对比 overlayFireAndAdvanceSchedule（已修复）:
  int cancelledCount = activeFires.size();
  schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + cancelledCount);
  schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + cancelledCount);
  ```
- **严重程度**: P2
- **现状**: `overlayFireAndAdvanceSchedule`（planner 触发的覆盖路径）在 R1 修复后正确更新了 `totalFireCount` 和 `failFireCount`。但 `insertManualFire`（`triggerNow`/`rerunFire` 触发的覆盖路径）仍然缺少这些统计更新。通过手动触发覆盖的 fires 被取消但不计入完成统计。
- **风险**: 频繁使用"立即触发"功能的覆盖调度，`totalFireCount` 系统性偏低，Dashboard 显示的成功/失败比例不准确。随着手动触发频率增加，偏差增大。
- **建议**: 在 `insertManualFire` 的 overlay 分支中添加与 `overlayFireAndAdvanceSchedule` 一致的 `totalFireCount` 和 `failFireCount` 更新。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

## 第 3 轮总评

### Top 1 Actionable Direction

**RPC 广播取消路由断裂 (AR-17, P1)**

这是对 AR-7 修复的补充发现。AR-7 修复了取消上下文缺少 sharding 属性的问题，但修复未覆盖 `targetHost` 路由的完整链路。执行路径通过 `DefaultJobExecutionContextBuilder.resolveJobParams()` 正确注入 `HEADER_SVC_TARGET_HOST`，但取消路径的 `CancelJobExecutionContext.resolveJobParams()` 完全缺失此注入。结果是对 RPC 广播任务的取消操作无法路由到正确的执行节点。

### Blind Spot Assessment

本轮仍可能遗漏：

1. **cancelAsync 的返回值未被检查**: `DefaultJobCancelHandler.cancelRunningTask` 调用 `invoker.cancelAsync()` 但只处理异常，不处理返回值 `false`（取消失败）。
2. **RpcBroadcastTaskBuilder 的 discoveryClient 返回值一致性**: 多次调用 `getInstances` 可能返回不同列表，影响 `shardingTotal` 的一致性。
3. **`rerunFire` 的 `buildRecoveryFire` 从旧 fire 复制 `jobParamsSnapshot`** — 与 AR-12（recovery 路径使用过时参数）是同源问题，但 `rerunFire` 路径未被 AR-12 覆盖。

---

## 严重程度分布 (Round 3)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 1    | RPC 取消路由断裂 (targetHost) |
| P2      | 1    | 手动触发 overlay 统计缺失 |

## 合并严重程度分布 (Round 1 + Round 2 + Round 3)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 5    | startTime 语义错误, recovery fire 缺字段, rerunFire 静默丢弃, schedule 并发更新丢失, RPC 取消路由断裂 |
| P2      | 8    | 事务边界不一致, 死写, 任务孤儿, Worker 竞态覆盖, dispatch timeout 孤儿, recovery 过时参数, SUSPICIOUS 无宽限期, 手动触发 overlay 统计缺失 |
| P3      | 5    | 死代码, 未实现功能残留, 方法名误导, 错误优先级, 广播 task 缺 payload |

## 深挖第 4 轮追加

**Date**: 2026-06-03
**Scope**: nop-job module — 独立第 4 轮审查，从并发状态机安全、API 层防护和遗留一致性角度出发
**Heuristics used**: 事务边界追踪者, IoC 侦探, 10x规模运维, 未来破坏者

**Dedup**: 逐一验证 AR-1~AR-18 当前代码状态。排除与 NAR-7（xmeta 计数字段）——实际代码中 `NopJobSchedule.xmeta` 已将所有计数器字段标记为 `insertable="false" updatable="false"`，此问题已修复。排除 NAR-8（JobCoordinator start/stop）——`LifeCycleSupport.start()` 使用 CAS + `beginStop()` 使用 `synchronized`，已提供足够保护。

### 先前发现验证摘要

经代码验证，AR-1 至 AR-18 中 **15/18 已修复**，未修复项：

| Prior ID | 当前状态 |
|----------|---------|
| AR-6 (P3) | 仍存在：`JobPlannerScannerImpl:172` setActiveFireCount(0) 死写 |
| AR-7/Prior F4 (P3) | 仍存在：`TriggerSpecHelper:34` maxFailedCount 硬编码为 0 |
| AR-14 (P3) | 仍存在：`JobPlannerScannerImpl:230` copyMap 返回原始引用而非副本 |
| AR-15 (P3) | 仍存在：`JobCompletionProcessorImpl:329-336` findFirstErrorTask 优先级不一致 |
| AR-16 (P3) | 仍存在：`RpcBroadcastTaskBuilder` 不设置 taskPayload |
| Prior F9 (P1) | 仍存在：`NopRetryJobRetryBridge:65` 返回 jobFireId 而非 retryRecordId |

---

### [AR-19] `cancelFire` 对 schedule 使用 `updateEntityDirectly` — AR-9 修复遗漏的对称路径

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:200-210`
- **证据片段**:
  ```java
  // JobFireStoreImpl.java:202-210 — cancelFire 内部
  schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
  schedule.setLastEndTime(cancelTime);
  schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
  if (shouldAdvanceFixedDelaySchedule(schedule, fire)) {
      schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, cancelTime));
  }
  schedule.setUpdatedBy("system");
  schedule.setUpdateTime(cancelTime);
  scheduleDao().updateEntityDirectly(schedule);  // ← 无版本检查!
  
  // 对比 completeFireAndUpdateSchedule（已修复 AR-9）:
  for (int attempt = 0; attempt < 3; attempt++) {
      List<NopJobSchedule> updatedSchedules = scheduleDao().tryUpdateManyWithVersionCheck(
              Collections.singletonList(schedule));
      ...
  }
  ```
- **严重程度**: P1
- **现状**: AR-9 修复了 `completeFireAndUpdateSchedule` 中 schedule 的乐观锁问题，引入了 `tryUpdateManyWithVersionCheck` + 重试循环。但 `cancelFire` 使用完全相同的模式（加载 schedule → 修改计数器 → 持久化），却仍用 `updateEntityDirectly`。这是 AR-9 修复的遗漏——两条并发的 schedule 更新路径（completion 和 cancellation）不一致。
- **风险**: 当用户通过 GraphQL 取消一个 fire 的同时，completion processor 正在完成同 schedule 的另一个 fire（PARALLEL 策略），`cancelFire` 的 `updateEntityDirectly` 会覆盖 completion processor 已经更新的计数器，导致 `activeFireCount` 漂移。
- **建议**: 对 `cancelFire` 的 schedule 更新应用与 `completeFireAndUpdateSchedule` 相同的 `tryUpdateManyWithVersionCheck` + 重试模式。
- **信心水平**: 确定
- **发现来源视角**: 事务边界追踪者

---

### [AR-20] `insertTasksAndMarkFireDispatching` 使用 `updateEntityDirectly` 更新 fire 状态 — 可覆盖并发取消/超时

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:94-105`
- **证据片段**:
  ```java
  // JobFireStoreImpl.java:94-105
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  public void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks) {
      NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
      if (currentFire.getFireStatus() == null || currentFire.getFireStatus() != FIRE_STATUS_DISPATCHING) {
          return;  // 检查时刻的状态
      }
  
      for (NopJobTask task : tasks) {
          taskDao().saveEntityDirectly(task);
      }
      currentFire.setFireStatus(FIRE_STATUS_RUNNING);
      fireDao().updateEntityDirectly(currentFire);  // ← TOCTOU: 写入时刻可能已被取消
  }
  ```
- **严重程度**: P1
- **现状**: 方法在事务内先检查 fire 状态是否为 DISPATCHING（line 96），然后在插入 tasks 后用 `updateEntityDirectly` 将状态设为 RUNNING（line 104）。虽然两者在同一 `REQUIRES_NEW` 事务中，但 `updateEntityDirectly` 不做版本检查。如果 timeout checker 在 `requireEntityById` 和 `updateEntityDirectly` 之间（数据库层面）将 fire 标记为 TIMEOUT，dispatcher 的 `updateEntityDirectly` 会把 TIMEOUT 覆盖回 RUNNING。Tasks 已经被插入——它们成为孤儿（fire 最终被 timeout checker 或 completion processor 处理，但 tasks 可能已被 worker 拾起执行）。
- **风险**: 被取消/超时的 fire 被复活为 RUNNING，tasks 被执行但最终结果可能被忽略。在快速取消场景下（用户手动取消后立即触发）概率增加。
- **建议**: 将 `updateEntityDirectly` 改为 `tryUpdateManyWithVersionCheck`。版本冲突时跳过更新（fire 已被其他路径处理），并可考虑删除已插入的 tasks 或标记为 CANCELED。
- **信心水平**: 很可能（数据库层面的并发取决于隔离级别，但在 READ_COMMITTED 下 TOCTOU 窗口存在）
- **发现来源视角**: 事务边界追踪者

---

### [AR-21] `completeFireAndUpdateSchedule` 重试 3 次后降级为 `updateEntityDirectly` — 高并发下计数器漂移

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:140-161`
- **证据片段**:
  ```java
  // JobFireStoreImpl.java:140-161
  for (int attempt = 0; attempt < 3; attempt++) {
      List<NopJobSchedule> updatedSchedules = scheduleDao().tryUpdateManyWithVersionCheck(
              Collections.singletonList(schedule));
      if (!updatedSchedules.isEmpty()) {
          return;  // 成功
      }
      NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
      schedule.setVersion(fresh.getVersion());
      schedule.setActiveFireCount(fresh.getActiveFireCount() + activeDelta);
      // ... 重新计算计数器 ...
  }
  scheduleDao().updateEntityDirectly(schedule);  // ← 第 3 次仍失败则强制写入
  ```
- **严重程度**: P2
- **现状**: AR-9 修复引入了乐观锁 + 重试，但 3 次重试后降级为 `updateEntityDirectly`。此时 `schedule` 的 version 已过期（第 3 次重试用 `fresh.getVersion()` 设置后仍然失败），强制写入会覆盖其他线程的计数器更新。设计意图是"尽力而为"，但在高并发下（PARALLEL 策略 + 多 fire 同时完成）会写入过时数据。
- **风险**: 10x 规模下，大量 PARALLEL fires 同时完成，3 次重试不足以稳定拿到版本锁，频繁降级为强制写入，计数器系统性偏低或不一致。
- **建议**: (a) 增加重试次数到 5-10 次，(b) 降级时使用 SQL 原子递增 (`UPDATE ... SET active_fire_count = active_fire_count + delta`) 而非 read-modify-write，(c) 或抛出异常而非静默强制写入。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-22] `NopJobScheduleBizModel.persistSchedule` 使用 `updateEntityDirectly` — 状态变更与引擎计数器竞态

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:132-134`
- **证据片段**:
  ```java
  // NopJobScheduleBizModel.java:132-134
  private void persistSchedule(NopJobSchedule schedule, String action, IServiceContext context) {
      dao().updateEntityDirectly(schedule);  // ← 无版本检查
      afterEntityChange(schedule, action, context);
  }
  
  // 所有状态变更方法都经过这里:
  // enableSchedule → validateStatus → setStatus → persistSchedule
  // disableSchedule → validateStatus → setStatus → persistSchedule
  // pauseSchedule → ...
  // resumeSchedule → ...
  // archiveSchedule → ...
  ```
- **严重程度**: P2
- **现状**: 用户通过 GraphQL 调用 `enableSchedule`/`disableSchedule` 等操作时，实体在 `requireEntity` 加载后、`persistSchedule` 写入前，planner/completion/cancel 可能已经修改了 schedule 的计数器字段。`updateEntityDirectly` 盲写所有 dirty 字段，可能覆盖 `activeFireCount`、`totalFireCount`、`nextFireTime` 等引擎维护的字段。
- **风险**: 用户禁用 schedule 的同时 completion processor 完成了一个 fire——`disableSchedule` 的 `updateEntityDirectly` 可能用旧值覆盖 `activeFireCount`，导致计数器不准确。
- **建议**: 使用 `tryUpdateManyWithVersionCheck` + 重试，或只更新 `scheduleStatus`/`nextFireTime` 字段而非整个实体。
- **信心水平**: 很可能
- **发现来源视角**: 未来破坏者

---

### [AR-23] `JobCompletionProcessorImpl` 加载 schedule 使用 `requireEntityById` — schedule 被删除后产生扫描毒丸

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:160`
- **证据片段**:
  ```java
  // JobCompletionProcessorImpl.java:160
  NopJobSchedule schedule = scheduleStore.loadSchedule(fire.getJobScheduleId());
  // loadSchedule → scheduleDao().requireEntityById() → 实体不存在时抛异常
  
  // 外层 scanOnce 只捕获并记录:
  } catch (Exception e) {
      LOG.error("nop.job.completion.scan-failed", e);  // ← 下次扫描会重复同样的错误
  }
  ```
- **严重程度**: P2
- **现状**: 如果 schedule 在 fire 运行期间被删除（通过 `archiveSchedule` 后物理删除，或直接数据库操作），completion processor 每次扫描到这个 fire 都会因 `requireEntityById` 抛出异常而跳过。fire 永远停留在 RUNNING 状态，成为"毒丸"——每次扫描都产生错误日志但无法自愈。
- **风险**: 少量孤儿 fire 在低频场景下影响有限。但如果批量删除 schedules（迁移、清理），大量 RUNNING fires 成为毒丸，degrade 扫描性能并产生大量错误日志。
- **建议**: `loadSchedule` 改用 `tryLoadSchedule`（返回 null），null 时将 fire 强制标记为 FAILED 并记录错误码（如 `ERR_JOB_SCHEDULE_DELETED`），跳过 schedule 计数器更新。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-24] `NopRetryJobRetryBridge` 返回 `jobFireId` 作为 `retryRecordId` — 仍存在（已知未修复 F9）

- **文件**: `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java:55-65`
- **证据片段**:
  ```java
  // NopRetryJobRetryBridge.java:55-65
  task.callAsync(request, null)
          .whenComplete((resp, err) -> {
              if (err != null) {
                  LOG.error("nop.job.retry.submit-failed:fireId={}", event.getJobFireId(), err);
              } else if (resp != null && !resp.isOk()) {
                  LOG.warn("nop.job.retry.submit-error:fireId={},code={}", event.getJobFireId(),
                          resp.getCode());
              }
          });
  
  return event.getJobFireId();  // ← 返回 jobFireId 而非 retry engine 生成的 retry task ID
  ```
- **严重程度**: P2（已知未修复，降级：原 F9 为 P1）
- **现状**: 已知问题 F9 仍然存在。`callAsync` 是真正的异步调用——`return` 在回调完成之前执行，retry engine 的实际 task ID 在回调中但未被捕获。`fire.retryRecordId` 被写入 `jobFireId`，是一个语义错误的值。注释在 ORM 中写"由 nop-retry 创建后回填"，但实际回填的是 jobFireId。
- **风险**: 跨系统追踪链路断裂——通过 `retryRecordId` 无法查询 nop-retry 的重试状态。如果未来有依赖 `retryRecordId` 的功能（如取消重试、查询重试进度），将完全不可用。
- **建议**: (a) 如果 `callAsync` 的设计使得同步获取 retry ID 不可能，应返回 `null` 而非 `jobFireId`（避免写入误导数据），并在 `whenComplete` 回调中通过 `fireStore.updateRetryRecordId` 回填真实 ID。(b) 或改用同步调用 `task.call(...)` 获取真实 ID。
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探

---

## 第 4 轮总评

### Top 3 Actionable Directions

**1. cancelFire 的 schedule 更新遗漏 (AR-19, P1)**

AR-9 修复了 `completeFireAndUpdateSchedule` 的乐观锁问题，但遗漏了对称路径 `cancelFire`。两条并发路径更新同一个 schedule，一条有版本保护，一条没有，在 PARALLEL 策略下必然产生计数器漂移。修复方式已验证可行——直接复用 `completeFireAndUpdateSchedule` 的模式。

**2. insertTasksAndMarkFireDispatching 的状态机覆盖 (AR-20, P1)**

dispatcher 的 `updateEntityDirectly` 可以把一个已被 timeout/cancel 标记为终态的 fire 覆盖回 RUNNING。这是状态机的正确性违反——一旦 fire 进入终态，不应被任何路径复活。修复简单：改用 `tryUpdateManyWithVersionCheck`。

**3. 重试降级策略 (AR-21, P2) + schedule 删除防护 (AR-23, P2)**

两个独立但都影响系统自愈能力的问题：重试降级为强制写入在高并发下导致计数器不一致，schedule 删除产生无法自愈的扫描毒丸。

### Blind Spot Assessment

本轮仍可能遗漏：

1. **批量删除场景**: 没有深入分析批量删除 schedules/fires/tasks 时的级联影响。
2. **trigger 计算边界**: `CronExpression` 的时区处理、闰年、夏令时边界没有验证。
3. **多 coordinator HA**: `JobPartitionResolver` 的 `lastChangeTime` 非 volatile 字段在多线程读写时的可见性没有深入分析。
4. **Worker 重启恢复**: Worker 崩溃后重启，CLAIMED 状态的 task 如何被重新发现（fetchRunningTasks 已包含 CLAIMED），但重入执行的幂等性没有验证。
5. **`JobFireStoreImpl.cancelFire` 中 `findTasksByFireId` 的排序**：task 按 taskNo/ID 升序排列（第 281 行 `query.addOrderField(PROP_NAME_taskNo, false)`——false 是降序），但 `cancelFire` 不关心顺序，不影响正确性。

---

## 合并严重程度分布 (Round 1 + Round 2 + Round 3 + Round 4)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 7    | AR-1~AR-5(R1 已修复), AR-8~AR-9(R2 已修复), AR-17(R3 已修复), AR-19~AR-20(R4 新增) |
| P2      | 12   | AR-3~AR-5(R1 已修复), AR-10~AR-13(R2 已修复), AR-18(R3 已修复), AR-21~AR-24(R4 新增) |
| P3      | 5    | AR-6, AR-7, AR-14~AR-16 (未修复低优先级) |

### 本轮新增 (Round 4) 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | cancelFire schedule 无版本检查, dispatch 状态机覆盖 |
| P2      | 4    | 重试降级强制写入, BizModel 状态竞态, schedule 删除毒丸, retryRecordId 语义错误 |
| P3      | 0    | —       |

## 深挖第 5 轮追加

**Date**: 2026-06-03
**Scope**: nop-job module — 独立第 5 轮审查，从先前审查盲区（Calendar 边界条件、RPC 广播健康过滤、Trigger 语义映射、trust boundary）出发
**Heuristics used**: 异常路径侦探, 模型攻击者, 10x规模运维, 组合爆炸测试, 死代码清道夫

**Dedup**: 逐一验证 AR-1~AR-24 当前代码状态。发现先前 24 项中 **19/24 已修复**，未修复项保持不变（见下方验证摘要）。新发现聚焦于先前审查明确标注的盲区。

### 先前发现验证摘要 (第 5 轮)

经代码验证，AR-1 至 AR-24 中 **19/24 已修复**，未修复项与第 4 轮一致：

| Prior ID | 当前状态 |
|----------|---------|
| AR-1 (P1) | **已修复**: `JobFireStoreImpl:86` startTime = `new Timestamp(now)`（实际时间） |
| AR-2 (P1) | **已修复**: `JobScheduleStoreImpl:172-180` recovery fire 现在设置 triggerSource、retryPolicyId、jobParamsSnapshot、executorKind |
| AR-3 (P2) | **已修复**: `JobScheduleStoreImpl:230-237` insertManualFire overlay 路径现在有 try-catch |
| AR-4 (P2) | **已修复**: `JobCompletionProcessorImpl:235-239` 现在 `retryRecordId != null` 时调用 `updateRetryRecordId` |
| AR-5 (P2) | **已修复**: `JobTaskStoreImpl:79-80` fetchRunningTasks 现在包含 CLAIMED 和 SUSPICIOUS 状态 |
| AR-6 (P3) | 仍存在：`JobPlannerScannerImpl:172` setActiveFireCount(0) 死写 |
| AR-7/Prior F4 (P3) | 仍存在：`TriggerSpecHelper:34` maxFailedCount 硬编码为 0 |
| AR-8 (P1) | **已修复**: `NopJobFireBizModel:73-78` rerunFire 现在检查返回值，DISCARD 时抛异常 |
| AR-9 (P1) | **已修复**: `JobFireStoreImpl:149-172` completeFire 使用 tryUpdateManyWithVersionCheck + 5 次重试 |
| AR-10 (P2) | **已修复**: `JobTaskStoreImpl:43` updateTask 现在使用 tryUpdateManyWithVersionCheck 并返回 boolean |
| AR-11 (P2) | **已修复**: `JobTimeoutCheckerImpl:290-317` dispatch timeout 现在取消关联 tasks |
| AR-12 (P2) | **已修复**: `JobScheduleStoreImpl:174,203` recovery 路径现在从 schedule 刷新 jobParams |
| AR-13 (P2) | **已修复**: `JobTimeoutCheckerImpl:182-184` SUSPICIOUS 标记后 continue（跳过同周期 timeout） |
| AR-14 (P3) | 仍存在：`JobPlannerScannerImpl:230` copyMap 返回原始引用 |
| AR-15 (P3) | 仍存在：`JobCompletionProcessorImpl` findFirstErrorTask 优先级不一致 |
| AR-16 (P3) | 仍存在：`RpcBroadcastTaskBuilder` 不设置 taskPayload |
| AR-17 (P1) | **已修复**: `DefaultJobCancelHandler:63-79` resolveJobParams 现在注入 HEADER_SVC_TARGET_HOST |
| AR-18 (P2) | **已修复**: `JobScheduleStoreImpl:242-246` insertManualFire overlay 路径更新 totalFireCount/failFireCount |
| AR-19 (P1) | **已修复**: `JobFireStoreImpl:213-235` cancelFire schedule 使用 tryUpdateManyWithVersionCheck + 重试 |
| AR-20 (P1) | **已修复**: `JobFireStoreImpl:104-109` insertTasksAndMarkFireDispatching 使用 tryUpdateManyWithVersionCheck |
| AR-21 (P2) | **已修复**: `JobFireStoreImpl:169-172` 重试失败后抛异常而非降级为 updateEntityDirectly |
| AR-22 (P2) | **已修复**: `NopJobScheduleBizModel:145-147` persistSchedule 使用 tryUpdateManyWithVersionCheck + 重试 |
| AR-23 (P2) | **已修复**: `JobCompletionProcessorImpl:161-168` 使用 tryLoadSchedule，null 时 failFireWithoutSchedule |
| AR-24 (P2) | 仍存在：`NopRetryJobRetryBridge` 返回 null（不再是 jobFireId，但仍无真实 ID） |
| Prior F9 (P1) | 仍存在：retryRecordId 跨系统追踪链路不完整 |
| Prior F14 (P3) | 仍存在：JobFireResult.CONTINUE 字段/方法名冲突 |

---

### [AR-25] AnnualCalendar.excludeDays 未初始化 — 调用 isExcludedDay 时 NPE

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/AnnualCalendar.java:26,38`
- **证据片段**:
  ```java
  private List<MonthDay> excludeDays;  // null by default

  public AnnualCalendar(ICalendar baseCalendar) {
      super(baseCalendar);
      // excludeDays never initialized
  }

  private boolean isExcludedDay(LocalDate day) {
      for (MonthDay excludedDay : excludeDays) {  // NPE if excludeDays is null
  ```
- **严重程度**: P1
- **现状**: `excludeDays` 在声明时未初始化，构造函数也不初始化。只有 `setExcludeDays()` 会设置它。如果未调用 `setExcludeDays` 就调用了 `isTimeIncluded` 或 `getNextIncludedTime`，会在 `isExcludedDay` 的 for-each 循环上抛出 `NullPointerException`。
- **风险**: 任何使用 `AnnualCalendar` 但未设置排除日期列表的 Calendar 链都会导致 trigger 计算崩溃。在 `PauseCalendarTrigger`/`CalendarBuilder` 路径中，如果配置错误或部分提供了 `AnnualCalendar`，会触发此问题。特别地，`CalendarBuilder` 的 `buildAnnualCalendar` 方法可能在 `spec.getExcludeDays()` 返回 null 时跳过 `setExcludeDays` 调用。
- **建议**: 将字段初始化为 `Collections.emptyList()` 或在 `isExcludedDay` 中添加 null 检查。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（未初始化字段审计）

---

### [AR-26] HolidayCalendar/AnnualCalendar.getNextIncludedTime 排除所有未来日期时无限循环

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/HolidayCalendar.java:51-53`, `AnnualCalendar.java:69-71`
- **证据片段**:
  ```java
  // HolidayCalendar.java:51-53
  LocalDate day = DateHelper.millisToDate(timeStamp);
  while (excludedDays.contains(day)) {
      day = day.plusDays(1);
  }

  // AnnualCalendar.java:69-71
  while (isExcludedDay(day)) {
      day = day.plusDays(1);
  }
  ```
- **严重程度**: P2
- **现状**: 如果 `excludedDays` TreeSet（或 AnnualCalendar 的 excludeDays 列表）包含连续的 LocalDate 范围（例如误操作添加了一个年份的所有日期），`while` 循环没有终止条件。没有最大迭代次数限制，没有年份边界检查。
- **风险**: 配置错误（添加过多排除日期）导致 trigger 计算线程无限旋转，scheduler 停止工作。在 10x 规模下，人为配置失误的影响面扩大。
- **建议**: 添加最大迭代次数（如 366×5），超出时返回 `timeStamp` 或 0。或使用 `TreeSet.higher(day)` 做二分查找而非线性扫描。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者（无限循环边界条件）

---

### [AR-27] CronCalendar.getNextIncludedTime 毫秒级扫描 — 长排除范围下性能极差

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java:111-125`
- **证据片段**:
  ```java
  while (!isTimeIncluded(nextIncludedTime)) {
      if (cronExpression.isSatisfiedBy(nextIncludedTime)) {
          nextIncludedTime = cronExpression.getNextInvalidTimeAfter(nextIncludedTime);
      } else if ((getBaseCalendar() != null)
              && (!getBaseCalendar().isIncludedTime(nextIncludedTime))) {
          nextIncludedTime = getBaseCalendar().getNextIncludedTime(nextIncludedTime);
      } else {
          nextIncludedTime++;  // millisecond-by-millisecond scan
      }
  }
  ```
- **严重程度**: P2
- **现状**: 当 baseCalendar 排除了一个时间范围且 cron expression 也不满足时，代码退化为毫秒递增。如果下一个 included 时间是数小时后，这将执行数十亿次迭代。
- **风险**: 长时间排除的 Calendar 配置可能让 trigger 计算耗时数分钟，阻塞 scheduler timer 线程。
- **建议**: 当 cron 不满足且 baseCalendar 也不满足时，跳转到 `baseCalendar.getNextIncludedTime()` 的结果，而非逐毫秒递增。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（Calendar 配置错误下的 CPU 风暴）

---

### [AR-28] RpcBroadcastTaskBuilder 不按健康状态过滤服务实例 — 向不健康节点派发任务

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java:59-62`
- **证据片段**:
  ```java
  List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
  if (instances == null || instances.isEmpty()) {
      return fallback.buildTasks(fire);
  }
  // No filtering by isHealthy() or isEnabled()
  ```
  对比 `JobTimeoutCheckerImpl:204-205`:
  ```java
  if (inst.isHealthy() && inst.isEnabled()) {
      alive.add(inst.getInstanceId());
  }
  ```
- **严重程度**: P2
- **现状**: 超时检查器在判断 worker 存活时做了 `isHealthy() && isEnabled()` 过滤，但广播任务构建器不对实例列表做同样的过滤。不健康的实例会被创建为广播 task 的目标。
- **风险**: 滚动更新期间，不健康实例收到广播任务后必然超时失败，浪费 dispatch 延迟和 timeout 扫描周期。在多实例部署中，一次滚动更新可能导致所有广播 fire 超时。
- **建议**: 在 `instances` 列表上过滤 `instance.isHealthy() && instance.isEnabled()`，过滤后为空则 fallback。
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试（服务发现语义一致性）

---

### [AR-29] resolveCompletionDecision 信任未验证的 task result 将 schedule 标记为 COMPLETED — 任何 job 实现可终止调度

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:275-277,200-202`
- **证据片段**:
  ```java
  // JobCompletionProcessorImpl.java:275-277
  Map<String, Object> payload = JsonTool.parseMap(resultPayload);
  if (Boolean.TRUE.equals(payload.get("completed"))) {
      return new FireCompletionDecision(true, null);
  }

  // tryCompleteFireAndGetStatus:200-202
  if (completionDecision.completed) {
      schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED);
      schedule.setNextFireTime(null);
  }
  ```
- **严重程度**: P1
- **现状**: 任何 job 实现只需在 task 的 `resultPayload` 中返回 `{"completed": true}` 即可将 schedule 永久标记为 COMPLETED，立即停止所有后续 fire。在多租户环境或存在第三方 job 实现的场景下，一个编写不当或恶意的 job 可以终止任何 schedule。没有任何配置开关来控制是否启用 result-driven completion。
- **风险**: 一个 bug 导致的 `completed: true` 会永久禁用一个生产 schedule。恢复需要手动干预重置 `scheduleStatus`。在 SOR 模式下（job result 是 trust boundary），没有授权检查。
- **建议**: 至少添加 schedule 级别的配置开关（如 `allowResultCompletion` boolean 列）来控制是否启用 result-driven completion。或记录 WARN 级别日志。
- **信心水平**: 很可能（当前平台可能有意为之，但没有文档说明此行为）
- **发现来源视角**: 模型攻击者（trust boundary / 输入验证）

---

### [AR-30] LimitCountTrigger 使用 totalFireCount（已完成计数）而非 fireCount（已调度计数）— PARALLEL 策略可超出 maxExecutionCount

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java:41-43`
- **证据片段**:
  ```java
  // TriggerSpecHelper.java:41-43
  @Override
  public long getFireCount() {
      return defaultLong(schedule.getTotalFireCount());  // only counts completed fires
  }

  // LimitCountTrigger.java:25-28
  if (maxRepeatCount > 0) {
      if (evalContext.getFireCount() >= maxRepeatCount)
          return -1;
  }
  ```
  而 planner 在 PARALLEL 策略下（`JobPlannerScannerImpl:169-174`）不做 block 检查：
  ```java
  if (shouldParallel(schedule)) {
      scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime, ...);
      // 每个调度周期都会创建新 fire，不管有多少 active fire
  }
  ```
- **严重程度**: P2
- **现状**: `TriggerSpecHelper.toEvalContext` 将 `getFireCount()` 映射到 `schedule.getTotalFireCount()`，此值只在 fire 完成时递增。对于 PARALLEL 阻塞策略，planner 每个周期都会创建新 fire，不检查 active fire 数量。`LimitCountTrigger` 在 fires 完成前看不到它们，planner 会持续创建新 fire 直到 fires 开始完成。例如，`maxExecutionCount=5` 且 `blockStrategy=PARALLEL` 的 schedule 在第一个 fire 完成前可以触发远超 5 个 fire。
- **风险**: PARALLEL 策略 + maxExecutionCount 组合下，实际执行次数远超配置上限。这不是安全性问题（fire 最终会完成并计入），但在语义上违反了用户期望的"最多执行 N 次"。
- **建议**: 使用 `fireCount`（planner 在创建 fire 时递增）或 `activeFireCount + totalFireCount` 作为 limit check 的语义。
- **信心水平**: 很可能
- **发现来源视角**: 组合爆炸测试（PARALLEL 策略 × LimitCountTrigger 的组合行为）

---

### [AR-31] JobWorkerScannerImpl.handleExecutionResult — updateTask 失败后静默丢弃执行结果

- **文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java:238-246`
- **证据片段**:
  ```java
  boolean updated = taskStore.updateTask(task);
  if (!updated) {
      NopJobTask freshTask = taskStore.loadTask(jobTaskId);
      if (freshTask.getTaskStatus() == TASK_STATUS_TIMEOUT
              || freshTask.getTaskStatus() == TASK_STATUS_CANCELED
              || freshTask.getTaskStatus() == TASK_STATUS_SUSPICIOUS) {
          return;
      }
      // freshTask 仍为 RUNNING 或 CLAIMED → 静默丢弃结果，无重试，无日志
  }
  ```
- **严重程度**: P2
- **现状**: 当 `updateTask` 因版本不匹配失败时（现在使用 `tryUpdateManyWithVersionCheck`），代码重新加载 task 检查状态。如果 task 仍为 RUNNING 或 CLAIMED（例如另一个并发写入交错），worker 的成功结果被静默丢弃——没有重试，没有警告日志。task 保持 RUNNING，最终会超时。
- **风险**: Job 执行成功但结果丢失。task 在超时前显示为"运行中"，超时后报告为失败。在乐观锁争用下概率增加。
- **建议**: 为 updateTask 失败添加重试（类似 fireStore 的乐观锁重试模式），或至少在结果被静默丢弃时记录 WARN 日志。
- **信心水平**: 很可能
- **发现来源视角**: 异常路径侦探（丢失写入审计）

---

### [AR-32] RpcBroadcastTaskBuilder 和 DefaultJobTaskBuilder 使用 System.currentTimeMillis() 而非 DB 时钟 — 时钟不一致

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java:64`, `DefaultJobTaskBuilder.java:16`
- **证据片段**:
  ```java
  // RpcBroadcastTaskBuilder:64
  long now = System.currentTimeMillis();

  // DefaultJobTaskBuilder:16
  long now = System.currentTimeMillis();

  // 对比 JobPlannerScannerImpl:185
  long now = scheduleStore.getCurrentTime();  // uses DB clock
  ```
- **严重程度**: P3
- **现状**: Task builders 使用本地系统时钟创建 createTime/updateTime，而引擎其他部分使用 `scheduleStore.getCurrentTime()`（DB estimated clock）。分布式部署中 DB 时间可能与系统时间有显著差异。
- **风险**: 时间差异导致审计追踪混乱。task 的 createTime 可能早于其 fire 的 createTime（系统时钟快于 DB 时钟时）。
- **建议**: 将 DB clock provider 注入到 task builders 中，使用 `getCurrentTime()` 替代 `System.currentTimeMillis()`。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫（时钟一致性审计）

---

### [AR-33] cancelFire 中 tasks 使用 updateEntityDirectly — 可覆盖并发 timeout 状态

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:198-211`
- **证据片段**:
  ```java
  // JobFireStoreImpl.java:198-211 — cancelFire 内部
  for (NopJobTask task : tasks) {
      if (isTaskFinished(task.getTaskStatus())) {
          continue;
      }
      task.setTaskStatus(TASK_STATUS_CANCELED);
      // ...
      taskDao().updateEntityDirectly(task);  // no version check!
  }
  // 注意：fire 和 schedule 已经使用 tryUpdateManyWithVersionCheck
  // 但 tasks 没有
  ```
- **严重程度**: P2
- **现状**: `cancelFire` 对 fire 和 schedule 都使用了 `tryUpdateManyWithVersionCheck`（AR-19 修复后），但 tasks 仍然使用 `updateEntityDirectly`。如果 timeout checker 在 cancel 事务期间并发地将一个 task 标记为 TIMEOUT，cancel 的 `updateEntityDirectly` 会静默覆盖 TIMEOUT 为 CANCELED。
- **风险**: 并发 cancel + timeout 场景下 task 终态不一致。task 以 CANCELED 而非 TIMEOUT 结束，可能影响 retry/alerting 逻辑。
- **建议**: 使用 `tryUpdateManyWithVersionCheck` 更新 tasks，与 fire/schedule 保持一致。
- **信心水平**: 很可能
- **发现来源视角**: 事务边界追踪者（并发写入一致性）

---

### [AR-34] JobScheduleStoreImpl 中所有 schedule 更新仍使用 updateEntityDirectly — planner 路径缺乏乐观锁

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:98,114,153,190,215,256`
- **证据片段**:
  ```java
  // advanceScheduleAfterSkip:98
  scheduleDao().updateEntityDirectly(schedule);

  // insertFireAndAdvanceSchedule:114
  scheduleDao().updateEntityDirectly(schedule);

  // overlayFireAndAdvanceSchedule:153
  scheduleDao().updateEntityDirectly(schedule);

  // recoveryFireAndAdvanceSchedule:190,215
  scheduleDao().updateEntityDirectly(schedule);

  // insertManualFire:256
  scheduleDao().updateEntityDirectly(schedule);
  ```
- **严重程度**: P2
- **现状**: AR-9 修复了 `completeFireAndUpdateSchedule`（fireStore）的 schedule 更新，AR-19 修复了 `cancelFire`（fireStore）的 schedule 更新。但 `JobScheduleStoreImpl` 中的 5 个 schedule 更新路径全部仍然使用 `updateEntityDirectly`。这些路径运行在 `REQUIRES_NEW` 事务中，但 `updateEntityDirectly` 不检查版本号。如果用户通过 GraphQL 同时修改 schedule 状态（如 enableSchedule/disableSchedule，现在使用了乐观锁），这些引擎路径的 `updateEntityDirectly` 可以覆盖用户的修改。
- **风险**: planner 路径的 schedule 更新可能与 BizModel 层的乐观锁更新产生竞态。由于 BizModel `persistSchedule` 现在使用版本检查，如果 planner 的 `updateEntityDirectly` 先执行，BizModel 会正确检测到版本冲突并重试。但反过来，如果 BizModel 先更新，planner 的 `updateEntityDirectly` 会覆盖 BizModel 的更新。
- **建议**: 逐步将 `JobScheduleStoreImpl` 中的 `updateEntityDirectly` 改为 `tryUpdateManyWithVersionCheck`，优先修复 `insertFireAndAdvanceSchedule` 和 `overlayFireAndAdvanceSchedule`（最高频路径）。
- **信心水平**: 很可能
- **发现来源视角**: 事务边界追踪者（AR-9/AR-19 修复的系统性遗漏）

---

### [AR-35] Schedule lock 使用 nextFireTime 作为隐式锁 — 锁到期后 fetchDueSchedules 会重复拾取

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:83-88`, `59-70`
- **证据片段**:
  ```java
  // tryLockSchedulesForPlan:83-88
  Timestamp lockTime = new Timestamp(now + Math.max(lockTimeoutMs, 1));
  for (NopJobSchedule schedule : schedules) {
      schedule.setNextFireTime(lockTime);  // 锁 = 设置 nextFireTime 到未来
  }
  return dao.tryUpdateManyWithVersionCheck(schedules);

  // fetchDueSchedules:65-66
  query.addFilter(FilterBeans.eq(PROP_NAME_scheduleStatus, SCHEDULE_STATUS_ENABLED));
  query.addFilter(FilterBeans.le(PROP_NAME_nextFireTime, now));
  ```
- **严重程度**: P2
- **现状**: Planner 的锁机制将 `nextFireTime` 设置为 `now + lockTimeoutMs`（默认 60s 后）来防止其他 planner 实例拾取同一 schedule。但如果 planner 在锁期内崩溃（`lockTimeoutMs` 内未完成处理），锁到期后 `fetchDueSchedules` 会再次拾取这个 schedule，因为 `nextFireTime` 已经过去。此时 planner 会重新计算 `calculateNextFireTime` 并创建新 fire。然而，如果上一次 planner 已经成功创建了 fire 但在设置 `nextFireTime` 为实际下次触发时间之前崩溃，就会产生重复 fire。
- **风险**: 在 planner 崩溃场景下可能产生重复 fire。对于 DISCARD 策略，重复 fire 会被丢弃（无影响）。但对于 PARALLEL 策略，每个重复 fire 都会执行，导致重复执行。
- **建议**: 考虑在 `insertFireAndAdvanceSchedule` 中添加幂等性检查（检查是否已存在相同 scheduledFireTime 的 WAITING fire），或在 schedule 上增加 `plannerLockId` 字段替代 nextFireTime 锁。
- **信心水平**: 有趣的猜测（planner 崩溃窗口极窄，实际触发概率低）
- **发现来源视角**: 10x规模运维（planner 崩溃恢复）

---

## 第 5 轮总评

### Top 3 Actionable Directions

**1. Calendar 边界条件组合 (AR-25, AR-26, AR-27)**

三个独立的 Calendar 实现问题：AnnualCalendar 未初始化导致 NPE、排除日期过多导致无限循环、CronCalendar 退化为毫秒扫描。这三个问题在"Calendar 配置错误"这一个场景下可以组合爆发——一个错误的 Calendar 配置可能同时触发 NPE（AnnualCalendar）或 CPU 风暴（CronCalendar/HolidayCalendar）。建议统一增加防御性编码：null 初始化、最大迭代次数、跳过而非递增。

**2. result-driven completion 缺乏安全门 (AR-29)**

`resolveCompletionDecision` 信任 task 的 `resultPayload` 中的 `completed: true` 来永久终止 schedule。这违反了 trust boundary 原则——job 实现的输出不应直接影响调度生命周期管理。建议添加 schedule 级别的配置开关。

**3. ScheduleStore 的 updateEntityDirectly 系统性遗漏 (AR-34)**

AR-9/AR-19/AR-22 修复了 fireStore 和 BizModel 中的 schedule 更新乐观锁问题，但 `JobScheduleStoreImpl` 中的 5 个更新路径全部仍然使用 `updateEntityDirectly`。这意味着 planner 路径（最高频的 schedule 更新路径）仍然缺乏乐观锁保护。

### Blind Spot Assessment

本轮仍可能遗漏：

1. **CalendarBuilder 时区传播链**: `CalendarBuilder` 如何从 `CalendarSpec` 构建 Calendar 实例，是否正确传播时区设置，未深入验证。
2. **IRpcServiceInvoker 实现的 timeout 配置**: `RpcJobInvoker` 依赖外部注入的 `IRpcServiceInvoker`，其 timeout 行为由外部实现决定。
3. **batch query 性能**: `batchLoadFires` 和 `batchLoadSchedules` 的 IN 子句在大量 ID 时的性能表现未评估。
4. **ORM 级联删除**: Schedule 删除时 fires/tasks 的级联清理策略未深入检查。
5. **Calendar 与 trigger 的时区一致性**: Schedule 的 `cronExpr` 和 `pauseCalendarSpec` 可能使用不同时区。

---

## 合并严重程度分布 (Round 1~5)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 9    | AR-1,2,8,9,17,19,20(R1-R4 均已修复), AR-25(Calendar NPE), AR-29(result-driven completion) |
| P2      | 19   | AR-3~5,10~13,18,21~24(R1-R4 均已修复), AR-26~28,30~31,33~35(R5 新增) |
| P3      | 7    | AR-6,7,14~16(未修复低优先级), AR-32(R5 新增) |

### 本轮新增 (Round 5) 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | Calendar NPE, result-driven completion trust boundary |
| P2      | 6    | Calendar 无限循环, CronCalendar 性能, 广播健康过滤, LimitCountTrigger 语义, 结果丢失, tasks 无版本检查, ScheduleStore 无乐观锁, schedule 锁重复 |
| P3      | 1    | 时钟不一致 |
