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

## 严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | 修复引入的语义错误 (startTime, recovery fire fields) |
| P2      | 3    | 事务边界不一致, 死写, 任务孤儿 |
| P3      | 2    | 死代码, 未实现功能残留 |
