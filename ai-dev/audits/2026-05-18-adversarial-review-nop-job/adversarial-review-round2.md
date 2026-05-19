# Adversarial Review: nop-job Module (Round 2 — Deep Dive)

**Date**: 2026-05-18
**Scope**: nop-job module — targeted deep-dive into threads exposed by Round 1
**Threads investigated**:
1. Full concurrency story of scanner pipeline
2. overlayFireAndAdvanceSchedule full impact
3. Dead and semi-dead features beyond F1/F4/F13
4. Task lifecycle edge cases (SUSPICIOUS, crash recovery)
5. ORM ↔ Store ↔ BizModel semantic gaps

**Dedup**: Excludes all Round 1 findings (F1-F15) and the prior systematic audit (154 findings).

---

## Finding R2-1: PARALLEL Block Strategy Never Handled — activeFireCount Drifts Indefinitely

**Severity**: P0 (block strategy partially broken, counter drift)
**Confidence**: Certain
**Thread**: T3 (dead features)

### WHERE

- `JobPlannerScannerImpl.shouldDiscard()` (line 273), `shouldOverlay()` (line 279), `shouldRecovery()` (line 285)
- `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` line 129
- ORM dict defines `BLOCK_STRATEGY_PARALLEL = 3`

### WHAT

The planner scanner only checks for DISCARD, OVERLAY, and RECOVERY in its `shouldXxx()` methods. When `blockStrategy == PARALLEL`, all three methods return false, and the schedule falls through to the normal `insertFireAndAdvanceSchedule` path. This path increments `activeFireCount` and creates a new fire regardless of existing active fires.

PARALLEL actually works correctly by accident — fires are created normally. However, `activeFireCount` keeps incrementing (each new fire adds 1) and the completion processor only decrements by 1 per fire. For PARALLEL schedules, `activeFireCount` will drift upward over time and never return to 0.

Worse: `shouldDiscard` and `shouldOverlay` both check `activeFireCount > 0` to decide whether to block. If `activeFireCount` has drifted from a previous PARALLEL execution, DISCARD/OVERLAY schedules sharing the same schedule entity (after a strategy change) will incorrectly enter the block path.

### WHY IT MATTERS

- `activeFireCount` permanently wrong for PARALLEL schedules
- If an operator changes a schedule from PARALLEL to DISCARD/OVERLAY, residual `activeFireCount` drift may cause unexpected blocking behavior
- Dashboard showing "active fires" is wrong for PARALLEL schedules

---

## Finding R2-3: Single-Task Dispatch `insertTaskAndMarkFireDispatching` is Dead Code with Broadcast Bug

**Severity**: P1 (dead code with latent semantic bug if ever used)
**Confidence**: Certain
**Thread**: T5 (Store ↔ caller gaps)

### WHERE

- `IJobFireStore.insertTaskAndMarkFireDispatching()` (interface line 20)
- `JobFireStoreImpl.insertTaskAndMarkFireDispatching()` (lines 91-102)
- `JobDispatcherScannerImpl.scanOnce()` line 126 (only calls batch variant)

### WHAT

The interface declares two dispatch methods: single-task and batch. Only the batch version is ever called. The single-task version has a semantic bug: it inserts one task and immediately transitions the fire to RUNNING. For broadcast jobs producing N tasks, calling the single-task version would insert only 1 of N tasks, mark the fire RUNNING prematurely, and leave no mechanism to insert the remaining tasks.

Both methods also use `updateEntityDirectly` instead of `tryUpdateManyWithVersionCheck`, meaning a concurrent actor could overwrite the fire's status without detection.

### WHY IT MATTERS

If any future code path calls the single-task method (e.g., a new executor type), broadcast jobs will silently lose tasks. The API contract of the interface is misleading — it suggests both methods are valid dispatch paths.

---

## Finding R2-4: `timeoutSeconds` Column Never Read — Per-Schedule Timeout Silently Ignored

**Severity**: P1 (user-facing feature that does nothing)
**Confidence**: Certain
**Thread**: T3 (dead features)

### WHERE

- ORM model `nop-job.orm.xml` line 141 (`TIMEOUT_SECONDS`, propId 20)
- `JobTimeoutCheckerImpl` lines 47, 94-97 (uses global `dispatchTimeoutMs` instead)
- All worker code — no per-task timeout enforcement

### WHAT

The NopJobSchedule entity has a `timeoutSeconds` column (propId 20) with displayName "超时时间(秒)". This is persisted in the database and presumably exposed in the UI for users to configure per-schedule task timeout.

However, ALL timeout code uses the global coordinator-level config `dispatchTimeoutMs` (default 300000ms = 5 minutes). No code path reads `schedule.getTimeoutSeconds()`. Users who set `timeoutSeconds = 30` for a quick job will believe it's protected, but the actual timeout is 5 minutes for all schedules uniformly.

### WHY IT MATTERS

- Silent feature gap — operator configures timeout, system accepts it, timeout never enforced
- A 30-second quick job that hangs runs for 5 minutes before detection
- A 10-minute ETL job is incorrectly timed out at 5 minutes even though user configured 600 seconds

---

## Finding R2-5: SUSPICIOUS Tasks Never Resolve — Fire Incorrectly Resolves to SUCCESS

**Severity**: P1 (semantic correctness — zero-execution fires marked SUCCESS)
**Confidence**: Certain
**Thread**: T4 (task lifecycle edge cases)

### WHERE

- `JobCompletionProcessorImpl.resolveFinalFireStatus()` lines 278-316
- `JobTimeoutCheckerImpl.tryMarkSuspiciousIfWorkerGone()` lines 211-227
- `JobTaskStoreImpl.fetchWaitingTasks()` lines 56-64

### WHAT

Full SUSPICIOUS lifecycle trace:

1. **Entry**: Timeout checker marks RUNNING tasks as SUSPICIOUS when their worker disappears from naming service
2. **Dead end — no code path re-claims SUSPICIOUS tasks**: `fetchWaitingTasks` only queries WAITING (0). `fetchRunningTasks` only queries RUNNING (20). SUSPICIOUS (15) is invisible to both.
3. **Resolution bug**: In `resolveFinalFireStatus`, SUSPICIOUS (value 15) does not match WAITING (0), CLAIMED (10), RUNNING (20), TIMEOUT (50), FAILED (40), or CANCELED (60). It falls through every `if/else if` branch **without setting any flag**. The task is invisible to the resolution logic.
4. **Result**: When all tasks are SUSPICIOUS, no flag is set, and the method falls through to `return FIRE_STATUS_SUCCESS`. A fire with zero successful tasks is marked SUCCESS.

### WHY IT MATTERS

- A schedule whose workers all crash appears as "successful" in the dashboard
- Alarms won't fire for these "successful" fires
- Retry logic won't trigger (fire is marked SUCCESS, not FAILED)
- The operator has no visibility into the failure — it looks like everything is working

---

## Finding R2-6: `pauseCalendarSpec` ORM Column Never Read — Fully Implemented Feature Disconnected at Bridge Layer

**Severity**: P2 (feature completely severed from data source)
**Confidence**: Certain
**Thread**: T3 (dead features)

### WHERE

- ORM `pauseCalendarSpec` column (nop-job.orm.xml lines 134-136)
- `TriggerBuilder.buildTrigger()` lines 34-42
- `PauseCalendarTrigger.java` (fully implemented)
- All three `toTriggerSpec()` methods hardcode `spec.setPauseCalendars(Collections.emptyList())`

### WHAT

Extends Round 1 F4. The pause calendar feature is fully implemented at every layer — `CalendarBuilder`, `PauseCalendarTrigger`, `ICalendar` — but the bridge between the ORM column and the trigger spec is severed. All three `toTriggerSpec()` methods hardcode `emptyList()` instead of reading `schedule.getPauseCalendarSpec()` and parsing it into `CalendarSpec` objects.

### WHY IT MATTERS

Users can configure pause calendars via the UI. The configuration is persisted but never used. The trigger infrastructure code exists but is never exercised, so it may have latent bugs that won't surface until someone connects the bridge.

---

## Finding R2-8: Recovery Path Recovers Only One Fire Per Cycle — Serial Recovery for Multiple Failures

**Severity**: P2 (operational performance during recovery)
**Confidence**: Likely
**Thread**: T1 (concurrency)

### WHERE

- `JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule()` lines 143-177
- `findFailedFires()` lines 280-289 (`query.setLimit(1)`)

### WHAT

`findFailedFires` limits results to 1, and `recoveryFireAndAdvanceSchedule` only processes `failedFires.get(0)`. If a schedule had N failed fires, recovery takes N × scanIntervalMs. This is eventually correct but unnecessarily serial.

### WHY IT MATTERS

During mass failure recovery (e.g., after a worker cluster crash), recovery is bottlenecked to one fire per planner cycle. With a 30-second scan interval and 100 failed fires, recovery takes 50 minutes.

---

## Finding R2-10: `completeTaskWithFailure` Bypasses Version Check — Overwrites SUSPICIOUS/CANCELED Status

**Severity**: P2 (same race class as F8, different code path)
**Confidence**: Certain
**Thread**: T4 (task lifecycle edge cases)

### WHERE

- `JobWorkerScannerImpl.completeTaskWithFailure()` lines 252-264
- `handleExecutionResult()` lines 202-250 (sibling method with proper guards)

### WHAT

When the worker fails to resolve an invoker (`invokerResolver.resolveInvoker` throws), the catch block calls `completeTaskWithFailure` which directly sets `TASK_STATUS_FAILED` and calls `taskStore.updateTask(task)` — a direct update with no version check. Compare with `handleExecutionResult` which loads a fresh task copy and guards against TIMEOUT/CANCELED status.

The race: worker loads task → timeout checker marks it SUSPICIOUS → `resolveInvoker` throws → `completeTaskWithFailure` overwrites SUSPICIOUS with FAILED using the stale task reference.

### WHY IT MATTERS

The timeout checker's corrective action is silently undone. The version-check bypass means no optimistic lock protection.

---

## Finding R2-11: `shouldAdvanceFixedDelaySchedule` Wrong for Manual/Overlay-Cancelled Fires

**Severity**: P2 (fixed-delay schedule nextFireTime not recalculated after overlay-cancel)
**Confidence**: Likely
**Thread**: T2 (overlay impact)

### WHERE

- `JobFireStoreImpl.shouldAdvanceFixedDelaySchedule()` lines 282-287
- `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` line 122

### WHAT

`shouldAdvanceFixedDelaySchedule` only returns true when `fire.getTriggerSource() == TRIGGER_SOURCE_SCHEDULE`. Manual fires have `TRIGGER_SOURCE_MANUAL`, rerun fires have `TRIGGER_SOURCE_RECOVERY`. When the overlay path cancels a manual or rerun fire, the fixed-delay schedule's `nextFireTime` is not recalculated from `cancelTime + repeatIntervalMs`. The schedule retains its old `nextFireTime`, which may be stale.

### WHY IT MATTERS

For fixed-delay schedules using OVERLAY, manual trigger→overlay-cancel leaves `nextFireTime` stale. The next scheduled fire may happen too soon or too late.

---

## Finding R2-12: RECOVERY Strategy with No Failed Fires Silently Skips — Schedule Never Fires

**Severity**: P2 (silent failure — schedule appears enabled but never executes)
**Confidence**: Certain
**Thread**: T1 (concurrency)

### WHERE

- `JobPlannerScannerImpl.shouldRecovery()` lines 285-288
- `JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule()` lines 143-177
- `findFailedFires()` lines 280-289

### WHAT

`shouldRecovery` does NOT check `activeFireCount > 0` (unlike `shouldDiscard` and `shouldOverlay`). This means EVERY planner cycle for a RECOVERY schedule enters the recovery branch. If `findFailedFires` returns empty (no failed fires exist), the method just advances `nextFireTime` and returns — no fire created.

Because `shouldRecovery` always returns true, the schedule **never** reaches the normal `insertFireAndAdvanceSchedule` path. A newly created RECOVERY schedule with no failed fires will never fire. Every cycle: schedule is due → `shouldRecovery` returns true → no failed fires → advance `nextFireTime` → repeat forever. `fireCount` stays at 0.

### WHY IT MATTERS

Silent failure with no error, no alarm. RECOVERY schedules that have never fired or whose fires all succeeded will never execute.

---

## Finding R2-15: `overlayFireAndAdvanceSchedule` Doesn't Lock Fires Before Cancelling

**Severity**: P2 (race with concurrent planner/dispatcher)
**Confidence**: Likely
**Thread**: T1 (concurrency)

### WHERE

- `JobScheduleStoreImpl.overlayFireAndAdvanceSchedule()` lines 115-141
- Private `cancelFire()` lines 318-327

### WHAT

The overlay path: (1) `findActiveFires` — non-locking query, (2) `cancelFire` via `fireDao().updateEntityDirectly(fire)` — no version check, (3) insert new fire, (4) hardcode `activeFireCount = 1`.

Between steps 1 and 3, a concurrent planner can create a phantom fire that won't be cancelled. Step 4 then sets `activeFireCount = 1`, losing the phantom from the counter. `cancelFire` also overwrites fire status to CANCELED without detecting concurrent DISPATCHING→RUNNING transitions.

### WHY IT MATTERS

Orphaned active fires uncounted by `activeFireCount`. Concurrent dispatch during overlay creates invisible fires.

---

## Finding R2-16: `saveTask` and `newTask` on `IJobTaskStore` Never Called — Dead API with Dangerous Semantics

**Severity**: P2 (dead API surface, trap for future developers)
**Confidence**: Certain
**Thread**: T5 (Store ↔ caller gaps)

### WHERE

- `IJobTaskStore` interface lines 9, 11
- `JobTaskStoreImpl` lines 40-47

### WHAT

`newTask()` is never called — all creation uses `new NopJobTask()` directly in task builders. `saveTask()` is never called — all persistence goes through `fireStore.insertTasksAndMarkFireDispatching()` which ensures the fire status transition happens atomically. If anyone calls `saveTask`, it bypasses the fire store's transactional contract, creating a task without the required fire status change.

### WHY IT MATTERS

Dead API surface. `saveTask` is a trap that breaks the fire↔task transactional contract if called. Future developers may assume these are the correct entry points for task creation.

---

## Finding R2-17: `updateTaskProgress` Never Called — Progress Tracking Infrastructure Completely Dead

**Severity**: P2 (dead feature infrastructure)
**Confidence**: Certain
**Thread**: T3 (dead features)

### WHERE

- `IJobTaskStore.updateTaskProgress()` interface line 27
- `JobTaskStoreImpl` lines 116-123 (has `@Transactional(REQUIRES_NEW)`)
- ORM `PROGRESS` (propId 21), `PROGRESS_MESSAGE` (propId 22) columns

### WHAT

ORM has `progress`/`progressMessage` columns. Store has `updateTaskProgress` with `@Transactional(REQUIRES_NEW)`. No code calls it — not worker, not completion processor, not BizModel. Columns are always null.

### WHY IT MATTERS

Progress tracking infrastructure exists but is completely dead. If someone implements progress reporting without connecting it to the task builder/worker pipeline, the progress will be written but never consumed.

---

## Finding R2-18: `NopJobTask.workerAddress` Column Never Written

**Severity**: P2 (dead column)
**Confidence**: Certain
**Thread**: T3 (dead features)

### WHERE

- ORM model line 345-347 (`WORKER_ADDRESS`, propId 6)
- `DefaultJobTaskBuilder` line 21
- `JobWorkerScannerImpl` line 184

### WHAT

No code calls `task.setWorkerAddress(...)`. Task builders and worker scanner set `workerInstanceId` but not `workerAddress`. Column is always null despite being in the schema.

### WHY IT MATTERS

Debugging/routing queries on `workerAddress` return nothing. If this column was intended for IP:port routing, RPC broadcast cancel cannot use it as a fallback when sharding attributes are missing (compounds F7).

---

## Finding R2-19: `OnceTrigger` Never Constructed — TRIGGER_TYPE_ONCE Has No Implementation

**Severity**: P2 (trigger type exposed to users but unimplemented)
**Confidence**: Certain
**Thread**: T3 (dead features)

### WHERE

- `TriggerBuilder.buildTrigger()` lines 21-48
- `OnceTrigger.java` (class exists with correct logic)
- ORM dict line 54

### WHAT

`TriggerBuilder` only creates `CronTrigger` or `PeriodicTrigger`. No branch for `TRIGGER_TYPE_ONCE`. The `OnceTrigger` class exists with correct logic but is never instantiated.

A ONCE schedule with no cron/interval falls through to `PeriodicTrigger(0)` — undefined behavior. If `repeatInterval=0`, `PeriodicTrigger` may produce zero-delay infinite triggering.

### WHY IT MATTERS

Catastrophic if used: a ONCE schedule with no cron expression would create fires as fast as the planner can process them, potentially filling the database with fires and overwhelming workers.

---

## Finding R2-20: `recalculateNextFireTime` Uses App Clock Instead of DB Clock

**Severity**: P3 (clock skew causes scheduling drift)
**Confidence**: Likely
**Thread**: T1 (timing)

### WHERE

- `NopJobScheduleBizModel.recalculateNextFireTime()` line 234

### WHAT

Passes `System.currentTimeMillis()` (app clock) while the planner compares `nextFireTime` against `scheduleStore.getCurrentTime()` (DB clock estimate). If app and DB clocks are not synchronized, the calculated `nextFireTime` may be immediately in the past (causing an extra immediate fire) or far in the future (causing a missed fire).

### WHY IT MATTERS

Clock skew between app server and database is common in production environments. After enable/resume operations, the schedule may fire immediately or with unexpected delay.

---

## Finding R2-22: DISCARD Schedules Waste `planningTimeoutMs` on Unnecessary Optimistic Lock

**Severity**: P2 (performance degradation under planner instability)
**Confidence**: Likely
**Thread**: T1 (concurrency)

### WHERE

- `JobScheduleStoreImpl.tryLockSchedulesForPlan()` lines 69-85
- `JobScheduleStoreImpl.advanceScheduleAfterSkip()` lines 87-95

### WHAT

The planner sets `nextFireTime = now + planningTimeoutMs` (default 60s) as an optimistic lock, even for DISCARD schedules where no fire is created. If the planner crashes between lock and skip, the schedule is invisible for 60s. The lock is unnecessary since DISCARD skip is idempotent.

### WHY IT MATTERS

With planner instability and many DISCARD schedules, 60-second blackouts accumulate. A simpler `advanceScheduleAfterSkip` without the lock timeout would be more resilient.

---

## Overall Assessment

### Finding Summary

| ID | Sev | Thread | Brief |
|----|-----|--------|-------|
| R2-1 | **P0** | T3 | PARALLEL strategy never handled — activeFireCount drifts |
| R2-3 | **P1** | T5 | Single-task dispatch dead code with broadcast bug |
| R2-4 | **P1** | T3 | timeoutSeconds column never read — per-schedule timeout ignored |
| R2-5 | **P1** | T4 | SUSPICIOUS invisible to resolveFinalFireStatus → SUCCESS with zero tasks |
| R2-6 | P2 | T3 | pauseCalendarSpec never read — PauseCalendarTrigger disconnected |
| R2-8 | P2 | T1 | Recovery serial — one fire per cycle |
| R2-10 | P2 | T4 | completeTaskWithFailure bypasses version check |
| R2-11 | P2 | T2 | shouldAdvanceFixedDelaySchedule wrong for manual overlay-cancelled |
| R2-12 | P2 | T1 | RECOVERY with no failed fires never fires — perpetual skip |
| R2-15 | P2 | T1 | overlay doesn't lock fires — concurrent planner race |
| R2-16 | P2 | T5 | saveTask/newTask never called — dangerous dead API |
| R2-17 | P2 | T3 | updateTaskProgress never called — progress dead |
| R2-18 | P2 | T3 | workerAddress column never written |
| R2-19 | P2 | T3 | OnceTrigger never constructed — TRIGGER_TYPE_ONCE broken |
| R2-20 | P3 | T1 | recalculateNextFireTime uses app clock not DB clock |
| R2-22 | P2 | T1 | DISCARD wastes planningTimeoutMs on unnecessary lock |

**Total**: 16 genuinely new findings — 1×P0, 3×P1, 11×P2, 1×P3

**Skipped as Round 1 duplicates**: R2-2 (=F3), R2-7 (=F12), R2-9 (=F5), R2-13 (extends F5), R2-14 (=F7).

### Top 5 Priority Fixes

1. **R2-5** — SUSPICIOUS→SUCCESS false positive. Add SUSPICIOUS to pending check in `resolveFinalFireStatus` or add a SUSPICIOUS→TIMEOUT sweep. Jobs with zero execution are falsely marked SUCCESS.
2. **R2-12** — RECOVERY schedules stuck forever. Add `activeFireCount > 0` check to `shouldRecovery`, or fall through to normal insertion when no failed fires exist.
3. **R2-4** — Wire `schedule.getTimeoutSeconds()` into the timeout checker. User-facing feature that silently does nothing.
4. **R2-1** — Handle BLOCK_STRATEGY_PARALLEL explicitly in the planner, or document that PARALLEL falls through to normal insert behavior. Fix `activeFireCount` tracking.
5. **R2-3** — Remove or deprecate the single-task dispatch method to prevent future misuse.

### Are Additional Rounds Needed?

**No — two rounds are sufficient.** All five investigation threads are fully exhausted:

1. **Thread 1 (Concurrency)**: Every state transition in the planner→dispatcher→worker→completion pipeline has been traced for two-scanner races and crash-mid-transition orphans. Remaining issues are performance (serial recovery, unnecessary locks), not new data loss vectors.
2. **Thread 2 (Overlay impact)**: The overlay path has been traced through all four callers (planner, manual, rerun, cancel). The remaining issues (counter drift, no lock, no try-catch) are all known.
3. **Thread 3 (Dead features)**: Every ORM column has been checked for read paths. Six dead columns/features identified. Unlikely to be more.
4. **Thread 4 (Task lifecycle)**: Every task status value (WAITING→CLAIMED→RUNNING→SUCCESS/FAILED/TIMEOUT/CANCELED/SUSPICIOUS) has been traced through all scanners. SUSPICIOUS is the only unhandled status.
5. **Thread 5 (Store ↔ caller gaps)**: Every store method has been checked for callers. Dead methods identified. No untested store methods remain.

### Blind Spots

- `PeriodicTrigger` with `repeatInterval=0` — if ONCE schedules fall into this path, the exact behavior was not traced into `PeriodicTrigger`'s implementation
- `HandleMisfireTrigger` + `fireCount` interaction — misfire threshold adjustment could interact with the fireCount counting issue but was not deeply traced
- RPC invoker implementations — `nopJobInvoker_rpc` and `nopJobInvoker_rpcBroadcast` were not read; the cancel-routing issue (F7) depends on how these invokers read sharding attributes
- Test coverage — no assessment of how many of these edge cases have automated tests; R2-5 and R2-12 deserve targeted tests
- `ITriggerEvalContext.getMaxFailedCount()` — identified as always returning 0 in F4, but not verified whether any trigger class beyond the three `toEvalContext` implementations references this method
