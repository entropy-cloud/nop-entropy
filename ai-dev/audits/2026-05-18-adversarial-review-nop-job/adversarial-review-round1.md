# Adversarial Review: nop-job Module (Round 1)

**Date**: 2026-05-18
**Scope**: nop-job module (11 submodules)
**Approach**: Discovery-oriented review starting from code anomalies, not from a checklist. Focused on cross-cutting concerns, emergent behaviors, and issues a systematic dimension-based audit would miss.

**Heuristics used**: Composition explosion tester, Anomaly path detective, 10x scale operator, Future breaker, Dead code scavenger

**Dedup**: This report excludes the 20 dimensions of findings from the prior systematic audit (2026-05-18-deep-audit-nop-job-full). Key already-reported issues NOT repeated here:
- P0: tryLock transaction boundary race conditions, design doc inconsistency
- P1: DAO→Core reverse dependency, FK indexes, NopJobTask→NopJobFire relationship, CronExpression constructor, xbiz entityName, state field mutability, permission annotations, DefaultJobCancelHandler coverage, @SingleSession+@Transactional(REQUIRES_NEW) mixing

---

## Finding 1: Dispatch Timeout is Dead Code — `fire.startTime` is Never Set for DISPATCHING Fires

**Severity**: P0 (dead feature — no recovery path for stuck DISPATCHING fires)
**Confidence**: Certain

### WHERE

- `JobTimeoutCheckerImpl.scanDispatchTimeouts()` — lines 229-258
- `JobFireStoreImpl.tryLockFiresForDispatch()` — lines 73-89
- `JobFireStoreImpl.insertTasksAndMarkFireDispatching()` — lines 104-117

### WHAT

The dispatch timeout scanner checks `fire.getStartTime()` to calculate whether a DISPATCHING fire has exceeded the `dispatchTimeoutMs` deadline:

```java
// JobTimeoutCheckerImpl.java, line 243-246
Timestamp startTime = fire.getStartTime();
if (startTime == null) {
    continue;
}
```

However, `startTime` is **never set** during the WAITING→DISPATCHING→RUNNING state transitions:

1. **`tryLockFiresForDispatch`** (WAITING→DISPATCHING) only sets:
   ```java
   fire.setFireStatus(FIRE_STATUS_DISPATCHING);
   fire.setDispatchInstanceId(dispatchInstanceId);
   fire.setUpdateTime(lockTime);
   ```

2. **`insertTasksAndMarkFireDispatching`** (DISPATCHING→RUNNING) only sets:
   ```java
   currentFire.setFireStatus(FIRE_STATUS_RUNNING);
   ```

3. The only production code that sets `fire.startTime` is in `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` (line 165), which runs AFTER the fire is already RUNNING — too late for dispatch timeout detection.

### WHY IT MATTERS

Every DISPATCHING fire has `startTime = null`, so the scan-level `continue` at line 246 skips **all** DISPATCHING fires. The entire dispatch timeout mechanism is dead code.

If a dispatcher crashes after transitioning a fire to DISPATCHING but before transitioning it to RUNNING, that fire will remain DISPATCHING forever — the timeout scanner cannot detect or recover it. At 10x scale, dispatcher crashes become more frequent, and stuck DISPATCHING fires accumulate without any self-healing mechanism.

The `dispatchTimeoutMs` configuration parameter appears configurable (via `getDispatchTimeoutMs()`), giving operators the false impression that they can tune dispatch timeout behavior.

---

## Finding 2: Overlay-Cancelled Fires Silently Evade All Statistics Counters

**Severity**: P1 (data integrity — schedule statistics permanently wrong for overlay schedules)
**Confidence**: Certain

### WHERE

- `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` — lines 113-141
- `JobScheduleStoreImpl.cancelFire()` (private method) — lines 318-327
- `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` — lines 148-213
- `JobFireStoreImpl.cancelFire()` (public method) — lines 139-187

### WHAT

When the OVERLAY block strategy cancels existing fires, those cancelled fires bypass the completion processor entirely. The overlay path:

```java
// JobScheduleStoreImpl.java, lines 121-129
for (NopJobFire activeFire : activeFires) {
    cancelFire(activeFire, cancelTime);          // private cancelFire - no stats update
    cancelTasks(activeFire.getJobFireId(), cancelTime);
}
fireDao().saveEntityDirectly(fire);               // insert new fire
schedule.setFireCount(defaultLong(schedule.getFireCount()) + 1);  // increment fireCount
schedule.setActiveFireCount(1);                   // hardcoded to 1
```

The private `cancelFire()` at line 318 only updates fire status and error code — it does NOT update `schedule.totalFireCount`, `schedule.successFireCount`, `schedule.failFireCount`, `schedule.lastEndTime`, `schedule.lastFireStatus`.

Compare with the public `cancelFire()` in `JobFireStoreImpl` (line 178) which DOES update schedule stats:

```java
// JobFireStoreImpl.java, lines 178-186
schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
schedule.setLastEndTime(cancelTime);
schedule.setLastFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELED);
```

The completion processor only fetches RUNNING fires, so cancelled-by-overlay fires (status=CANCELED) are invisible to it. The same issue applies to `insertManualFire` when it takes the overlay path.

### WHY IT MATTERS

For schedules using OVERLAY block strategy:
- `totalFireCount` undercounts (overlay-cancelled fires never counted)
- `successFireCount` / `failFireCount` are wrong (cancelled fires counted as neither)
- `lastFireStatus` may show an old stale value rather than CANCELED
- Dashboard statistics permanently diverge from reality

---

## Finding 3: `completeFireAndUpdateSchedule` Idempotency Guard Only Covers CANCELED/TIMEOUT — Not SUCCESS/FAILED

**Severity**: P2 (latent defect, currently masked by version check)
**Confidence**: Certain

### WHERE

- `JobFireStoreImpl.completeFireAndUpdateSchedule()` — lines 119-137

### WHAT

The early-exit guard for already-completed fires only checks two terminal statuses:

```java
// JobFireStoreImpl.java, lines 119-122
private static final Set<Integer> TERMINAL_FIRE_STATUSES = Set.of(
    _NopJobCoreConstants.FIRE_STATUS_CANCELED,   // 60
    _NopJobCoreConstants.FIRE_STATUS_TIMEOUT     // 50
);
```

But the fire status dict defines five terminal states: SUCCESS (30), FAILED (40), TIMEOUT (50), CANCELED (60). The guard misses SUCCESS and FAILED.

When the completion processor and timeout checker race on the same fire:
1. Completion processor reads fire (RUNNING), sets fire to SUCCESS
2. Timeout checker reads fire (RUNNING, but now stale), sets fire to TIMEOUT, calls `completeFireAndUpdateSchedule`
3. Inside `completeFireAndUpdateSchedule`: re-reads fire from DB → sees SUCCESS → NOT in TERMINAL_FIRE_STATUSES → proceeds to version check
4. Version check fails → returns early (safe, but wasted)

### WHY IT MATTERS

1. **Performance at scale**: At 10x scale with concurrent scanners, every already-completed SUCCESS/FAILED fire generates a wasted DB re-read + failed version-check. The guard is supposed to be the cheap early-exit; the version check is the expensive fallback.
2. **Brittle correctness dependency**: The actual safety comes from the version check, not the status guard. If the version check is ever weakened (e.g., entity loaded without version field), SUCCESS/FAILED fires could be double-processed.

---

## Finding 4: `toTriggerSpec` / `toEvalContext` Copy-Pasted 4 Times — Pause Calendar and Failed Count Are Dead Configuration

**Severity**: P2 (maintenance time bomb + silent feature gap)
**Confidence**: Certain

### WHERE

- `JobPlannerScannerImpl.toTriggerSpec()` — lines 213-227
- `JobCompletionProcessorImpl.toTriggerSpec()` — lines 373-386
- `NopJobScheduleBizModel.toTriggerSpec()` — lines 239-253
- `JobFireStoreImpl.toTriggerSpec()` — lines 301-315
- ORM column `pauseCalendarSpec` — `nop-job.orm.xml` lines 134-136
- ORM column `useDefaultCalendar` — `nop-job.orm.xml` lines 131-133

### WHAT

Four identical copies of two methods exist across four packages. Each copy converts a `NopJobSchedule` entity into a `TriggerSpec` and an `ITriggerEvalContext`. All four contain the same two hardcodings that silently discard user configuration:

```java
spec.setPauseCalendars(Collections.emptyList());  // line 224 / 383 / 250 / 312
spec.setMaxFailedCount(0);                          // line 225 / 384 / 251 / 313
```

Meanwhile, the ORM model exposes user-configurable columns for calendar support (`pauseCalendarSpec`, `useDefaultCalendar`). These are stored in the database but never read by any code path — the trigger spec always uses `emptyList()` for pause calendars and `0` for max failed count.

### WHY IT MATTERS

1. **Dead configuration**: An operator can configure `pauseCalendarSpec` and `maxFailedCount` via the UI or API, and the system will accept and persist the values, but they will have zero effect. This is a silent feature gap that looks like it works but doesn't.
2. **Maintenance bomb**: When someone wants to fix this, they must update four identical copies in four different packages. If they miss one, behavior will differ depending on which code path handles the schedule.
3. **DRY violation**: Four identical method bodies is a code smell that increases the chance of divergence as the module evolves.

---

## Finding 5: `fireCount` Counts Overlay Cancellations, `totalFireCount` Doesn't — Trigger Execution Limit Reached Prematurely

**Severity**: P1 (functional impact on schedules using OVERLAY + maxExecutionCount)
**Confidence**: Likely

### WHERE

- `JobScheduleStoreImpl.insertFireAndAdvanceSchedule()` — line 103
- `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` — line 128
- `JobScheduleStoreImpl.insertManualFire()` — line 199
- `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` — lines 184-189
- All four `toEvalContext` copies — `getFireCount()` returns `schedule.getFireCount()`

### WHAT

The `NopJobSchedule` entity maintains two separate fire counters:

**`fireCount`** (propId=23): Incremented at fire **creation** time in three store methods. Every fire creation increments `fireCount` — including fires that will immediately be cancelled by overlay, fires that will be cancelled manually, and recovery fires.

**`totalFireCount`** (propId=36): Incremented at fire **completion** time only. Only fires that reach the completion processor increment `totalFireCount`. Fires cancelled by overlay, cancelled via API, or stuck in DISPATCHING status never reach this path.

**The trigger uses `fireCount` for execution limiting.** All four copies of `toEvalContext` return `schedule.getFireCount()` for the `getFireCount()` method, which is used by `LimitCountTrigger` to enforce `maxExecutionCount`.

### WHY IT MATTERS

For an OVERLAY schedule with `maxExecutionCount=100`:
- Each fire creation increments `fireCount` by 1
- Each overlay cancellation increments `fireCount` by 1 again (the replacement fire)
- So each actual execution counts as 2 toward the execution limit
- The schedule stops triggering after ~50 actual executions instead of 100

The counters also diverge over time: `fireCount` > `totalFireCount` for any schedule that experiences overlays, cancellations, or stuck fires. This makes the statistics dashboard misleading.

---

## Finding 6: `tryLockTasksForExecute` Missing `@Transactional(REQUIRES_NEW)` — Inconsistent with Analogous Lock Methods

**Severity**: P2 (correctness under concurrent worker load)
**Confidence**: Likely

### WHERE

- `JobTaskStoreImpl.tryLockTasksForExecute()` — lines 66-81 (NO `@Transactional`)
- `JobScheduleStoreImpl.tryLockSchedulesForPlan()` — line 69 (HAS `@Transactional(REQUIRES_NEW)`)
- `JobFireStoreImpl.tryLockFiresForDispatch()` — line 73 (HAS `@Transactional(REQUIRES_NEW)`)
- Caller: `JobWorkerScannerImpl.scanOnce()` — line 151

### WHAT

The store layer has three "tryLock" methods following an identical pattern: accept a list of entities, mutate each entity's in-memory state, then call `tryUpdateManyWithVersionCheck` for batch version-checked SQL UPDATEs. Two of the three have `@Transactional(REQUIRES_NEW)`. The task method does not.

```java
// JobTaskStoreImpl.java lines 66-81 — NO @Transactional
@Override
public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
    // ... mutate in-memory ...
    return taskDao().tryUpdateManyWithVersionCheck(tasks);
}
```

Compare with the analogous schedule method:
```java
// JobScheduleStoreImpl.java line 69 — HAS @Transactional
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
@Override
public List<NopJobSchedule> tryLockSchedulesForPlan(...) {
    // ... same pattern ...
}
```

### WHY IT MATTERS

Without explicit transaction control, `tryLockTasksForExecute` inherits its caller's transaction context. The caller `JobWorkerScannerImpl.doScan()` uses `@SingleSession`, which means the task locking shares a session/transaction with the rest of the worker scan cycle. If the scan cycle is long (e.g., many tasks to process), the lock transaction stays open longer than necessary, reducing throughput under high concurrency.

---

## Finding 7: CancelJobExecutionContext Missing Sharding Attributes — RPC Broadcast Cancel Goes to Wrong Target

**Severity**: P1 (functional bug for RPC broadcast executor cancellation)
**Confidence**: Certain

### WHERE

- `DefaultJobCancelHandler.CancelJobExecutionContext` — lines 70-111
- `DefaultJobExecutionContextBuilder.WorkerJobExecutionContext` — lines 60-103
- `RpcJobInvoker.injectFrameworkHeaders()` — lines 93-122
- `RpcJobInvoker.cancelAsync()` — lines 65-91
- ORM columns `shardingIndex`, `shardingTotal`, `targetHost` — `nop-job.orm.xml` lines 352-360

### WHAT

Two `IJobExecutionContext` implementations exist in nop-job. One is used for normal task execution, the other for task cancellation. The cancel context is missing sharding and routing information.

**`WorkerJobExecutionContext`** (for execution):
```java
if (task.getShardingIndex() != null) {
    setAttribute("shardingIndex", task.getShardingIndex());
}
if (task.getShardingTotal() != null) {
    setAttribute("shardingTotal", task.getShardingTotal());
}
```

**`CancelJobExecutionContext`** (for cancellation):
```java
setAttribute("jobScheduleId", schedule.getJobScheduleId());
setAttribute("jobFireId", fire.getJobFireId());
setAttribute("jobTaskId", task.getJobTaskId());
setAttribute("executorKind", schedule.getExecutorKind());
// MISSING: shardingIndex, shardingTotal, targetHost
```

### WHY IT MATTERS

For RPC broadcast tasks, `RpcJobInvoker.cancelAsync()` builds the cancel RPC request using the context attributes. `injectFrameworkHeaders()` propagates `shardingIndex` and `shardingTotal` to the RPC headers (lines 93-122). Without these attributes, the cancel RPC request is sent without routing information.

In a sharded broadcast scenario, the cancel request will go to an arbitrary worker instead of the specific shard that's executing the task. The targeted worker won't receive the cancel signal, and the task continues executing despite being cancelled in the coordinator's records.

---

## Finding 8: Worker Overwrites SUSPICIOUS Task Status — Race Between Timeout Checker and Worker Result

**Severity**: P2 (data integrity under specific timing conditions)
**Confidence**: Likely

### WHERE

- `JobWorkerScannerImpl.handleExecutionResult()` — lines 202-249
- `JobTimeoutCheckerImpl.tryMarkSuspiciousIfWorkerGone()` — lines 211-227
- `JobTimeoutCheckerImpl.markSuspiciousAsTimeout()` — lines 307-335
- ORM task status dict: SUSPICIOUS=15

### WHAT

The worker's result handler guards against only two non-completing statuses:

```java
// JobWorkerScannerImpl.java, lines 202-208
if (task.getTaskStatus() == TASK_STATUS_TIMEOUT
        || task.getTaskStatus() == TASK_STATUS_CANCELED) {
    return;
}
```

SUSPICIOUS (value=15) is **not** in this guard. It falls through to the result-writing code path.

Race scenario:
1. Worker reads task (RUNNING), starts executing
2. Worker's heartbeat expires → timeout checker marks task SUSPICIOUS
3. On next scan, timeout checker transitions SUSPICIOUS → TIMEOUT immediately (no time deadline check)
4. Meanwhile, worker finishes execution, reads task (now SUSPICIOUS or TIMEOUT)
5. If worker reads SUSPICIOUS: guard doesn't catch it → worker writes SUCCESS, overwriting SUSPICIOUS
6. Optimistic lock check may catch it (version field), but if the timeout checker hasn't committed yet, the worker's write succeeds

### WHY IT MATTERS

The SUSPICIOUS state was designed to be a transitional state before TIMEOUT. If the worker can overwrite it with a result, the timeout detection is unreliable — the operator sees a successful task that was supposed to be timed out.

---

## Finding 9: `NopRetryJobRetryBridge.onFireFailed()` Returns `jobFireId` as `retryRecordId` — Async Retry Record ID Lost

**Severity**: P1 (observability — cross-system tracing broken between nop-job and nop-retry)
**Confidence**: Certain

### WHERE

- `NopRetryJobRetryBridge.onFireFailed()` — lines 31-65
- `JobCompletionProcessorImpl.handleRetryAndAlarm()` — lines 216-242
- ORM `nop_job_fire` column `retryRecordId` — `nop-job.orm.xml` line 262

### WHAT

The bridge submits a retry task to the `nop-retry` engine **asynchronously** via `task.callAsync()`, but immediately returns `event.getJobFireId()` (the fire's own ID) as the retry record ID. The actual retry record ID generated by the nop-retry subsystem is only available in the async `whenComplete` callback — which only logs and takes no further action.

```java
// NopRetryJobRetryBridge.java
@Override
public String onFireFailed(JobFireFailedEvent event) {
    // ...
    task.callAsync(request, null)
        .whenComplete((result, err) -> {
            // Actual retry record ID is in result, but only logged
            LOG.info("nop.job.retry.submitted:fireId={}", event.getJobFireId());
        });
    return event.getJobFireId();  // Returns fire ID, NOT the retry record ID
}
```

The completion processor stores this as `fire.retryRecordId`:
```java
String retryRecordId = retryBridge.onFireFailed(event);
fire.setRetryRecordId(retryRecordId);  // Stores jobFireId as retryRecordId
```

### WHY IT MATTERS

1. `fire.retryRecordId` column stores the fire's own ID, not the nop-retry record ID. The column is effectively redundant with `fire.jobFireId`.
2. Cross-system tracing between nop-job and nop-retry is impossible via this field — you can't look up the retry status of a failed fire.
3. The ORM delta comment says `由 nop-retry 创建后回填` ("backfilled after nop-retry creates it"), suggesting the design intent was for nop-retry to callback with the real ID, but this callback path doesn't exist.

---

## Finding 10: `NopJobTaskBizModel` Exposes Full CRUD via `CrudBizModel` — xmeta Mitigates Update But Create and Delete Remain Unrestricted

**Severity**: P2 (xmeta protects sensitive fields on update, but task creation with arbitrary routing and unguarded delete remain open)
**Confidence**: Certain

### WHERE

- `NopJobTaskBizModel.java` — entire file (15 lines, zero method overrides)
- `NopJobTask.xmeta` (delta) — lines 1-14, protects 9 sensitive fields with `insertable="false" updatable="false"`
- `NopJobFireBizModel.java` — for comparison (has domain-specific operations)
- `NopJobScheduleBizModel.java` — for comparison (has domain-specific operations)

### WHAT

`NopJobTaskBizModel` extends `CrudBizModel<NopJobTask>` with zero method overrides — it's a bare CRUD shell:

```java
@BizModel("NopJobTask")
public class NopJobTaskBizModel extends CrudBizModel<NopJobTask> implements INopJobTaskBiz {
    public NopJobTaskBizModel(){
        setEntityName(NopJobTask.class.getName());
    }
}
```

Compare with `NopJobFireBizModel` (47+ lines, has `cancelFire`, `rerunFire` with status validation) and `NopJobScheduleBizModel` (100+ lines, has `enableSchedule`, `disableSchedule`, `triggerNow` with status validation).

The delta xmeta (`NopJobTask.xmeta`) overrides the base to make sensitive fields `insertable="false" updatable="false"`, which means CRUD save/update operations will ignore these fields. However:
- **Delete** is not restricted by xmeta — any authenticated user can delete any task
- **Create** with arbitrary `shardingIndex`, `shardingTotal`, `targetHost` is possible (these fields are insertable)

### WHY IT MATTERS

Tasks are supposed to be created and managed exclusively by the coordinator/worker pipeline. Direct API access to task CRUD could:
1. Create phantom tasks with arbitrary routing that confuse the dispatcher
2. Delete tasks that are being executed, leaving the fire in RUNNING state forever
3. Interfere with the completion processor's task counting logic (F11)

---

## Finding 11: `resolveFinalFireStatus` — SUSPICIOUS Falls Through All Checks to Default SUCCESS; CANCELED Priority Masks Successful Broadcast Shards

**Severity**: P2 (semantic correctness for edge cases)
**Confidence**: Certain

### WHERE

- `JobCompletionProcessorImpl.resolveFinalFireStatus()` — lines 278-314

### WHAT

Two semantic issues in the fire status resolution logic:

**Issue A — SUSPICIOUS (15) falls through all checks:**

```java
for (NopJobTask task : tasks) {
    Integer taskStatus = task.getTaskStatus();
    if (taskStatus == null || taskStatus == TASK_STATUS_WAITING
            || taskStatus == TASK_STATUS_CLAIMED
            || taskStatus == TASK_STATUS_RUNNING) {
        hasPendingTask = true;
        continue;
    }
    if (taskStatus == TASK_STATUS_TIMEOUT) { hasTimeoutTask = true; }
    else if (taskStatus == TASK_STATUS_FAILED) { hasFailedTask = true; }
    else if (taskStatus == TASK_STATUS_CANCELED) { hasCanceledTask = true; }
    // TASK_STATUS_SUSPICIOUS (15) — NOT matched by any condition
}
// ...
return FIRE_STATUS_SUCCESS;  // Default when no boolean flags are true
```

SUSPICIOUS doesn't set any flag, so it falls through to the default SUCCESS return. If all tasks are SUSPICIOUS (timing window from F8), the fire is marked SUCCESS with zero successful tasks.

**Issue B — CANCELED takes priority over SUCCESS in broadcast:**

The priority chain is `hasTimeoutTask > hasFailedTask > hasCanceledTask > SUCCESS`. In a broadcast scenario with 10 tasks where 9 succeed and 1 is cancelled, the fire is marked CANCELED — the 90% success rate is invisible at the fire level.

### WHY IT MATTERS

- Issue A: Combined with F8 (worker overwriting SUSPICIOUS), there's a window where the fire can be marked SUCCESS with no successful tasks
- Issue B: For broadcast executors, a single cancelled shard hides the success of all other shards in the fire's aggregate status

---

## Finding 12: `insertManualFire` DISCARD Path Silently Returns Void — Operator Gets No Feedback

**Severity**: P2 (operational usability / silent data loss)
**Confidence**: Certain

### WHERE

- `JobScheduleStoreImpl.insertManualFire()` — lines 179-198
- `NopJobScheduleBizModel.triggerNow()` — lines 97-115

### WHAT

When an operator triggers a manual fire via `triggerNow`, the store method checks whether a fire already exists in an active state. If one does, it takes a DISCARD path:

```java
if (!activeFires.isEmpty()) {
    return;  // DISCARD — silently return, no fire created, no exception, no log
}
```

The BizModel then runs `afterEntityChange` regardless of whether the fire was created or discarded, making it appear as if the operation succeeded.

### WHY IT MATTERS

1. Operator clicks "Trigger Now", sees success response, but no fire was actually created
2. No audit trail that the trigger was discarded
3. In operational incident scenarios, an operator may repeatedly trigger "now" thinking it's working, not realizing the overlay strategy is silently discarding every attempt
4. The API contract should either return a boolean/error or throw a descriptive exception

---

## Finding 13: `tryMarkDispatchTimeout` Duration Calculation Returns 0ms When `startTime` Is Null — Second Layer of F1's Dead Code

**Severity**: P2 (compounds F1's P0 dispatch timeout dead code)
**Confidence**: Certain

### WHERE

- `JobTimeoutCheckerImpl.tryMarkDispatchTimeout()` — lines 269-277
- `JobTimeoutCheckerImpl.startTimeOrNow()` — lines 279-285

### WHAT

Even if someone fixed the scan-level check (F1) by removing the `startTime == null → continue`, the underlying calculation is also broken:

```java
private long startTimeOrNow(NopJobFire fire, long now) {
    Timestamp startTime = fire.getStartTime();
    if (startTime != null) {
        return startTime.getTime();
    }
    return now;  // When startTime is null, returns now
}
```

When `startTime` is null (which it always is for DISPATCHING fires), `startTimeOrNow` returns `now`. Then:

```java
long duration = now - startTimeOrNow(fire, now);  // now - now = 0
if (duration >= dispatchTimeoutMs) { ... }  // 0 >= any_positive_value → false
```

The duration is always 0ms — the timeout threshold is never met. Two independent failure layers.

### WHY IT MATTERS

Even a partial fix (removing the null check at the scan level) would not restore dispatch timeout functionality. The fix must address both layers: set `startTime` during dispatch AND/OR change `startTimeOrNow` to use `fire.getUpdateTime()` as fallback.

---

## Finding 14: `JobFireResult.CONTINUE` Static Field vs Factory Method — Same Name, Different Semantics

**Severity**: P3 (API design / potential misuse by extension developers)
**Confidence**: Certain

### WHERE

- `JobFireResult.java` — entire file
- `IJobInvoker` interface — public extension point for third-party implementations

### WHAT

`JobFireResult` provides two ways to create a "continue" result with the same name but different semantics:

```java
public static final JobFireResult CONTINUE = new JobFireResult(-1, null, null, null);  // nextScheduleTime = -1
public static JobFireResult CONTINUE(long nextScheduleTime) {                           // nextScheduleTime = caller-specified
    return new JobFireResult(nextScheduleTime, null, null, null);
}
```

Java allows a static field and a static method with the same name. In IDE autocompletion, both appear as `CONTINUE`. A developer typing `JobFireResult.CONTINUE` might intend the factory method but accidentally select the static field.

### WHY IT MATTERS

`IJobInvoker` is a public extension point. Third-party invoker implementations may accidentally use the static field when they need the factory method, resulting in `-1` as `nextScheduleTime` instead of a specific time. The compiler won't warn because both are valid Java.

---

## Finding 15: `overlayFireAndAdvanceSchedule` Loop Has No Try-Catch — Single Fire Cancel Failure Aborts Entire Overlay Transaction

**Severity**: P2 (reliability under concurrent dispatch)
**Confidence**: Likely

### WHERE

- `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` — lines 113-170
- Private `cancelFire()` — lines 310-330

### WHAT

The overlay method runs inside a `REQUIRES_NEW` transaction. It iterates over all active fires for a schedule, cancels each one, then creates a new fire — all atomically:

```java
for (NopJobFire fire : activeFires) {
    cancelFire(fire, now);   // If this throws, entire transaction rolls back
}
// ... insert new fire, update schedule ...
```

The private `cancelFire()` calls `fireDao().updateEntityDirectly(fire)` which performs an optimistic lock check. If the dispatcher concurrently claimed a fire (bumping its version), the version check fails and throws an `OptimisticLockException` or similar. The entire `REQUIRES_NEW` transaction rolls back — no fires are cancelled, no new fire is created, the schedule's `nextFireTime` remains unchanged.

### WHY IT MATTERS

The overlay strategy's purpose is to ensure exactly one active fire exists. If the entire overlay transaction fails due to a concurrent dispatch on ONE fire out of many, the planner will retry on the next cycle, but:
1. The schedule's `nextFireTime` has already been advanced past the current time (from the planner's lock), so the planner may skip it
2. The dispatcher may have claimed the fire between the planner's scan and the overlay, creating an inconsistency between the planner's view and the dispatcher's actions
3. At 10x concurrency, this race window widens

---

## Overall Assessment

### Top 3 Actionable Directions

**1. Dispatch timeout is completely broken — two independent failure layers (F1 + F13, P0)**

`fire.startTime` is never set for DISPATCHING fires. The scan-level check (`startTime == null → skip`) prevents the timeout code from ever running. Even if that check were removed, the calculation-level fallback in `startTimeOrNow` returns `now` when `startTime` is null, producing duration=0ms — the timeout threshold is never met. Two independent mechanisms both fail.

Fix requires either: (a) setting `startTime` during dispatch in `tryLockFiresForDispatch` or `insertTasksAndMarkFireDispatching`, or (b) changing `startTimeOrNow` to use `fire.getUpdateTime()` (which IS set during dispatch) as fallback. Prefer both for defense-in-depth.

**2. Statistics integrity is unreliable under overlay/cancel (F2 + F5 + F9 + F10 + F11, cross-cutting P1)**

Five findings converge on one theme: the system's record of what happened diverges from what actually happened.
- **F2**: Overlay-cancelled fires bypass `totalFireCount`/`successFireCount`/`failFireCount`
- **F5**: `fireCount` increments on every overlay (including cancellations), while `totalFireCount` only increments on completion. `LimitCountTrigger` uses `fireCount` for `maxExecutionCount`, so overlay-heavy schedules reach execution limits prematurely
- **F9**: `retryRecordId` stores `jobFireId` instead of the actual retry record ID from nop-retry. Cross-system tracing is broken
- **F10**: `NopJobTaskBizModel` exposes unrestricted delete and task creation with arbitrary routing fields
- **F11**: `resolveFinalFireStatus` produces SUCCESS with zero successful tasks (all-SUSPICIOUS timing window) or CANCELED with 90% successful broadcast shards

Together: the operator's dashboard, the schedule's execution limit, the retry traceability, and the fire's final status are all potentially inaccurate.

**3. Copy-paste code + missing sharding in cancel context (F4 + F7, P2 maintenance + P1 functional)**

`toTriggerSpec`/`toEvalContext` are copy-pasted 4 times with identical dead configuration. The cancel context for RPC broadcast tasks is missing `shardingIndex`/`shardingTotal` attributes, so cancel RPC requests cannot be routed to the correct target instance.

### Blind Spot Assessment

This review likely missed:

1. **Concurrency stress scenarios**: Many findings involve races (F8, F15, F3), but I didn't do formal concurrency modeling. There may be additional race windows in the coordinator/worker/dispatcher interaction that only manifest under specific timing conditions.

2. **Retry engine integration depth**: F9 scratched the surface of nop-retry integration. The full retry flow (retry policy selection, retry record lifecycle, retry callback into nop-job) was not fully traced. There may be additional semantic mismatches at the nop-retry boundary.

3. **ORM-generated code correctness**: The `_`-prefixed generated code (entities, DAOs) was spot-checked but not systematically verified against the ORM model. Codegen template issues that produce subtly wrong generated code would not have been caught.

4. **Clustered deployment behavior**: The review assumed a single coordinator + multiple workers topology. In a multi-coordinator deployment (HA), the `@SingleSession` + `REQUIRES_NEW` interaction may have additional failure modes not explored here.

5. **Performance under load**: No profiling or load testing was performed. The `fetchRunningFires` query pattern, the batch size choices, and the lock timeout values were not evaluated against realistic data volumes.

---

## Finding Summary Table

| # | Severity | Short Description | Confidence |
|---|----------|-------------------|------------|
| F1 | **P0** | Dispatch timeout dead code — startTime never set for DISPATCHING fires | Certain |
| F2 | **P1** | Overlay-cancelled fires bypass all statistics counters | Certain |
| F3 | P2 | completeFireAndUpdateSchedule idempotency guard misses SUCCESS/FAILED | Certain |
| F4 | P2 | toTriggerSpec/toEvalContext copy-pasted 4x — pauseCalendar/maxFailedCount dead | Certain |
| F5 | **P1** | fireCount counts overlays → premature execution limit | Likely |
| F6 | P2 | tryLockTasksForExecute missing @Transactional(REQUIRES_NEW) | Likely |
| F7 | **P1** | CancelJobExecutionContext missing sharding attrs — RPC cancel goes to wrong target | Certain |
| F8 | P2 | Worker overwrites SUSPICIOUS task status — race with timeout checker | Likely |
| F9 | **P1** | NopRetryJobRetryBridge returns jobFireId as retryRecordId — tracing broken | Certain |
| F10 | P2 | NopJobTaskBizModel bare CRUD — unrestricted delete and create | Certain |
| F11 | P2 | resolveFinalFireStatus: SUSPICIOUS→SUCCESS, CANCELED masks broadcast success | Certain |
| F12 | P2 | insertManualFire discard path silent — operator gets no feedback | Certain |
| F13 | P2 | tryMarkDispatchTimeout returns 0ms — compounds F1's dead code | Certain |
| F14 | P3 | JobFireResult.CONTINUE field vs method — same name, different semantics | Certain |
| F15 | P2 | overlayFireAndAdvanceSchedule no try-catch — single cancel failure aborts all | Likely |

**Total**: 15 findings — 1×P0, 4×P1, 9×P2, 1×P3
