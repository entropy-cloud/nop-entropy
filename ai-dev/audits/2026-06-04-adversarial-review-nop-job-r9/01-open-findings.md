# Adversarial Review: nop-job Module (Round 9)

**Date**: 2026-06-04
**Scope**: nop-job module — independent re-examination after 8 prior rounds (AR-1~AR-69), focused on previously unaudited code paths
**Approach**: Discovery-oriented, starting from coordinator lifecycle safety, worker instance identity semantics, planner block strategy fallthrough, and completion processor batch isolation. Three parallel deep explorations of coordinator, worker/core/dao/service/api/retry layers.

**Heuristics used**: 10x规模运维 (lifecycle, batch isolation), 异常路径侦探 (shutdown, unknown strategy), IoC 侦探 (worker identity, alarm wiring), 组合爆炸测试 (block strategy fallthrough × active fires)

**Dedup**: Prior audits AR-1~AR-69 covered: startTime semantics, recovery fire fields, retryRecordId dead write, CLAIMED task orphans, SUSPICIOUS grace period, cancelFire schedule optimistic lock, dispatch timeout task cleanup, completion malformed JSON, persistSchedule force-update, enableSchedule stale nextFireTime, Calendar build/boundary issues (DailyCalendarSpec crash, WeeklyCalendarSpec mapping, DailyCalendar midnight, AnnualCalendar NPE, HolidayCalendar loop, CronCalendar loop/performance), BizModel API protection (Fire CRUD, xmeta fields), result-driven completion gate, PeriodicTrigger drift, LimitCountTrigger semantic, HandleMisfireTrigger+OnceTrigger, RPC null timeout, missing indexes, handleExecutionResult fire state check, naming cache, GC pressure, CronExpression equals timeZone. This report focuses on lifecycle safety, worker identity, batch isolation, and block strategy completeness — areas with minimal prior coverage.

---

## Verified Fixes Since R8

The following R8 findings have been **fixed** since the last audit:

| Prior ID | Status | Evidence |
|----------|--------|---------|
| AR-57 (P1) | **Fixed**: `shouldRecovery` now checks `activeFireCount > 0` (line 245-248) | Previously missing guard |
| AR-59 (P1) | **Fixed**: `persistSchedule` now throws `NopException` instead of `updateEntityDirectly` after lock exhaustion (line 158) | Previously force-updated |
| AR-60 (P2) | **Fixed**: `enableSchedule` now always calls `recalculateNextFireTime` (line 63) | Previously conditional |

---

## New Findings

### [AR-70] `JobCoordinator.doStop()` not exception-safe — cascading stop failure leaves scanners running

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java:49-62`
- **证据片段**:
  ```java
  @Override
  protected void doStop() {
      if (timeoutChecker != null) {
          timeoutChecker.stopScanning();     // if this throws...
      }
      if (completionProcessor != null) {
          completionProcessor.stopScanning(); // ...this never runs
      }
      if (dispatcherScanner != null) {
          dispatcherScanner.stopScanning();
      }
      if (plannerScanner != null) {
          plannerScanner.stopScanning();
      }
  }
  ```
- **严重程度**: P1
- **现状**: `doStop()` calls four `stopScanning()` methods sequentially without individual try-catch blocks. If any single `stopScanning()` call throws an exception (e.g., interrupted `Future.cancel`, or an unexpected runtime exception in scanner cleanup), the remaining components are never stopped. `LifeCycleSupport.stop()` catches exceptions from `doStop()`, but the exception has already propagated out of `JobCoordinator.doStop()`, leaving the remaining scanners running.
- **风险**: A single scanner stop failure cascades into incomplete shutdown. Planners continue creating fires, dispatchers continue dispatching tasks, all while the application is shutting down. Database operations may execute after the transaction manager or IoC container has begun teardown.
- **建议**: Wrap each `stopScanning()` call in its own try-catch block, logging the error but continuing to stop remaining components.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-71] `workerInstanceId` set to coordinator's host — SUSPICIOUS task detection ineffective for non-colocated deployments

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java:83`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobTaskBuilder.java:22`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:199,222-223`
- **证据片段**:
  ```java
  // RpcBroadcastTaskBuilder.java:83
  task.setWorkerInstanceId(AppConfig.hostId()); // coordinator's own host ID

  // DefaultJobTaskBuilder.java:22
  task.setWorkerInstanceId(AppConfig.hostId()); // coordinator's own host ID

  // JobTimeoutCheckerImpl.java:199,222-223
  String svcName = AppConfig.appName(); // coordinator's own service name
  List<ServiceInstance> instances = namingService.getInstances(svcName);
  // ...
  String workerId = task.getWorkerInstanceId();
  if (workerId == null || aliveWorkerIds.contains(workerId)) {
      return; // coordinator is always alive, so this always returns
  }
  ```
- **严重程度**: P1
- **现状**: Both `DefaultJobTaskBuilder` and `RpcBroadcastTaskBuilder` set `task.workerInstanceId = AppConfig.hostId()` (the coordinator's own host ID). The timeout checker's `tryMarkSuspiciousIfWorkerGone` compares `task.getWorkerInstanceId()` against the set of alive instances resolved from `AppConfig.appName()` (the coordinator's own service name). Since the coordinator is always alive from its own perspective, `aliveWorkerIds` always contains the coordinator's host ID, and `task.getWorkerInstanceId()` is always the coordinator's host ID — the condition `aliveWorkerIds.contains(workerId)` is **always true**. The SUSPICIOUS detection mechanism is a no-op in all deployments (coordinator and worker are the same service name, and the host ID always matches).

  This is by design for colocated deployments (coordinator = worker), but it means that in any deployment topology, if a worker process dies, its tasks will **never** be proactively detected as SUSPICIOUS. They must wait the full timeout duration before being marked TIMEOUT.
- **风险**: Worker crashes result in tasks that are not proactively detected as SUSPICIOUS. The `tryMarkSuspiciousIfWorkerGone` path is effectively dead code — the SUSPICIOUS optimization provides no benefit. All worker-crashed tasks must wait the full configured timeout (default 5 minutes from `dispatchTimeoutMs`) before being resolved.
- **建议**: Either (a) set `workerInstanceId` to the actual executor's instance ID (if workers register with the naming service under a different service name), or (b) document that SUSPICIOUS detection only works for colocated deployments where the coordinator monitors itself, and consider removing the SUSPICIOUS mechanism for clarity.
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探（worker identity chain audit）

---

### [AR-72] Task execution timeout falls back to `dispatchTimeoutMs` — semantic mismatch

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:436-442`
- **证据片段**:
  ```java
  int timeoutSeconds = defaultInt(schedule.getTimeoutSeconds());
  long effectiveTimeoutMs;
  if (timeoutSeconds > 0) {
      effectiveTimeoutMs = timeoutSeconds * 1000L;
  } else {
      effectiveTimeoutMs = dispatchTimeoutMs; // reuses dispatch timeout for execution timeout
  }
  ```
- **严重程度**: P1
- **现状**: When `schedule.timeoutSeconds` is not set (null or 0), the task execution timeout falls back to `dispatchTimeoutMs` (default 300000 = 5 minutes). These are fundamentally different concepts:
  - **Dispatch timeout**: how long a fire can stay in DISPATCHING state (waiting for tasks to be created and assigned)
  - **Execution timeout**: how long a task is allowed to run after being dispatched

  A reasonable dispatch timeout might be 30-60 seconds, while execution timeout for long-running batch jobs could be hours. Using the same value for both forces operators to either set a long dispatch timeout (delaying detection of stuck dispatches) or a short execution timeout (killing long-running tasks prematurely).
- **风险**: Without explicit `timeoutSeconds` on every schedule, long-running tasks are killed after 5 minutes, or dispatch detection is delayed if operators increase the default. The fallback should use a separate configurable default.
- **建议**: Introduce a separate `executionTimeoutMs` configuration parameter (defaulting to `dispatchTimeoutMs` for backward compatibility) and use it as the fallback for task execution timeout.
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-73] `JobCompletionProcessorImpl.scanOnce` has no per-fire try-catch — one failure aborts entire batch

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:132-148`
- **证据片段**:
  ```java
  void scanOnce() {
      try {
          IntRangeSet partitions = ...;
          List<NopJobFire> fires = fireStore.fetchRunningFires(batchSize, partitions);
          int completedCount = 0;
          for (NopJobFire fire : fires) {
              if (tryCompleteFireAndGetStatus(fire) != null) {  // no individual try-catch
                  completedCount++;
              }
          }
      } catch (Exception e) {
          LOG.error("nop.job.completion.scan-failed", e);
      }
  }

  // Compare with JobTimeoutCheckerImpl which wraps each task:
  for (NopJobTask task : tasks) {
      try {
          // ... per-task processing ...
      } catch (Exception e) {
          LOG.warn("...", e);
      }
  }
  ```
- **严重程度**: P2
- **现状**: Unlike the timeout checker (which wraps each task in individual try-catch), the completion processor has no per-fire error isolation. If `tryCompleteFireAndGetStatus` throws for one fire (e.g., `completeFireAndUpdateSchedule` throws after optimistic lock retries), the exception propagates to the outer catch, and ALL remaining fires in the batch are skipped. They will be retried on the next scan cycle, but this introduces a "head-of-line blocking" effect where a single problematic fire delays completion of all subsequent fires in the batch.
- **风险**: In 10x scale deployments, a single fire with persistent completion errors (e.g., corrupted data, missing schedule) can block the entire completion scan batch. All other fires appear stuck in RUNNING until the next cycle.
- **建议**: Wrap each `tryCompleteFireAndGetStatus` call in its own try-catch, logging the error and continuing to the next fire.
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-74] `JobCoordinator.doStop()` stop order is inverted — planner (producer) stops last, creates orphaned fires

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java:33-46,49-62`
- **证据片段**:
  ```java
  // Start order: planner → dispatcher → completion → timeout
  protected void doStart() {
      plannerScanner.startScanning();    // creates fires
      dispatcherScanner.startScanning(); // dispatches fires
      completionProcessor.startScanning(); // completes fires
      timeoutChecker.startScanning();     // checks timeouts
  }

  // Stop order: timeout → completion → dispatcher → planner (last!)
  protected void doStop() {
      timeoutChecker.stopScanning();
      completionProcessor.stopScanning();
      dispatcherScanner.stopScanning();
      plannerScanner.stopScanning();      // planner stops LAST
  }
  ```
- **严重程度**: P2
- **现状**: The pipeline is: planner creates fires → dispatcher dispatches → completion/timeout handle results. The stop order should be: stop producer first (planner), then consumer (dispatcher), then allow drainers (completion, timeout) to finish. But the current stop order stops the drainers first and the producer last. Between `completionProcessor.stopScanning()` and `plannerScanner.stopScanning()`, the planner creates new fires that will never be dispatched (dispatcher already stopped) and never completed (completion already stopped). These fires sit in WAITING/DISPATCHING state until the system restarts.

  Additionally, `Future.cancel(false)` in each scanner's `stopScanning()` does NOT wait for a currently executing scan to complete — a scan may still be in progress after `stopScanning()` returns.
- **风险**: During graceful shutdown, fires are created but never processed. On restart, these fires may trigger immediately (if their `scheduledFireTime` is in the past), causing unexpected behavior.
- **建议**: Reverse the stop order: stop planner first, then dispatcher, then completion, then timeout. Also consider using `cancel(true)` or polling the Future with a timeout to await completion of in-flight scans.
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-75] Unknown/unrecognized `blockStrategy` falls through to default insert — creates concurrent fires without conflict handling

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:148-177`
- **证据片段**:
  ```java
  for (NopJobSchedule schedule : locked) {
      if (shouldDiscard(schedule)) { ... continue; }
      if (shouldRecovery(schedule)) { ... continue; }  // now checks activeFireCount

      NopJobFire fire = buildFire(schedule, dueFireTime);
      if (shouldOverlay(schedule)) { ... continue; }
      if (shouldParallel(schedule)) { ... continue; }

      // Fallthrough: unrecognized blockStrategy with activeFireCount > 0
      scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime, ...);
  }

  // Each should* checks for a specific constant:
  private boolean shouldDiscard(NopJobSchedule schedule) {
      return defaultInt(schedule.getActiveFireCount()) > 0
              && schedule.getBlockStrategy() == _NopJobCoreConstants.BLOCK_STRATEGY_DISCARD;
  }
  ```
- **严重程度**: P2
- **现状**: With AR-57 now fixed, `shouldRecovery` correctly requires `activeFireCount > 0`. However, when `activeFireCount > 0` and `blockStrategy` is a value not matching any of DISCARD (1), OVERLAY (2), RECOVERY (3), or PARALLEL (4), none of the `should*` checks match. The code falls through to `insertFireAndAdvanceSchedule`, creating a new fire alongside existing active fires with no conflict handling. This is effectively PARALLEL behavior but without the explicit intent.

  If `blockStrategy` is null (not set on the schedule entity), the fallthrough is also the default path — which creates a new fire regardless of active fires. For schedules with `activeFireCount > 0` and `blockStrategy = null`, every planner scan creates a new fire.
- **风险**: Schedules with null or unrecognized `blockStrategy` values create concurrent fires on every scan cycle. This can lead to resource exhaustion and duplicate execution. The behavior is silent — no warning is logged for unrecognized strategies.
- **建议**: Add a `shouldDefault` method or a final `else` clause that at minimum logs a WARN for schedules with `activeFireCount > 0` and unrecognized/null `blockStrategy`. Consider defaulting to DISCARD (safest) for unrecognized values.
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试

---

### [AR-76] No configuration validation for injected scanner parameters — zero/negative values cause silent malfunction

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:58-71` (and identical pattern in all 4 scanner implementations)
- **证据片段**:
  ```java
  @InjectValue("@cfg:nop.job.coordinator.planner.scan-interval-ms|5000")
  public void setScanIntervalMs(int scanIntervalMs) {
      this.scanIntervalMs = scanIntervalMs; // no range check
  }

  @InjectValue("@cfg:nop.job.coordinator.planner.batch-size|100")
  public void setBatchSize(int batchSize) {
      this.batchSize = batchSize; // no range check
  }

  @InjectValue("@cfg:nop.job.coordinator.planner.lock-timeout-ms|60000")
  public void setLockTimeoutMs(long lockTimeoutMs) {
      this.lockTimeoutMs = lockTimeoutMs; // no range check
  }
  ```
- **严重程度**: P2
- **现状**: None of the injected config values are validated. Specific risks:
  - `scanIntervalMs = 0` causes `IllegalArgumentException` from `ScheduledExecutorService.scheduleWithFixedDelay`
  - `batchSize = 0` makes the scanner a no-op — `fetchDueSchedules(0, ...)` returns empty list every cycle
  - `lockTimeoutMs = 0` means locks expire immediately, causing every lock attempt to fail
  - Negative values cause `IllegalArgumentException` from `TimeUnit.MILLISECONDS`

  In all cases, the application starts but the scanner silently does nothing or crashes on first scan.
- **风险**: A single misconfiguration value (e.g., `nop.job.coordinator.planner.batch-size=0`) makes the entire planner non-functional with no warning. Operators see no fires being created and must dig into logs to find the cause.
- **建议**: Add validation in each setter: reject values ≤ 0 (or set sensible minimums like `scanIntervalMs >= 1000`, `batchSize >= 1`). Throw `NopException` with a clear error message on invalid configuration.
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-77] `resolveFinalFireStatus` — CANCELED task determines fire status even when other tasks succeeded (broadcast)

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:315-361`
- **证据片段**:
  ```java
  private Integer resolveFinalFireStatus(List<NopJobTask> tasks) {
      // ...
      if (hasTimeoutTask)  return FIRE_STATUS_TIMEOUT;
      if (hasFailedTask)   return FIRE_STATUS_FAILED;
      if (hasCanceledTask) return FIRE_STATUS_CANCELED;
      return FIRE_STATUS_SUCCESS;
  }
  ```
- **严重程度**: P2
- **现状**: For broadcast fires with multiple tasks, the fire status is determined by the worst task status: TIMEOUT > FAILED > CANCELED > SUCCESS. If one shard is CANCELED (e.g., by overlay blocking strategy cancelling in-flight fires) but other shards succeed, the entire fire is marked CANCELED. For operators monitoring fire-level success rates, a fire with 99% successful tasks and 1% canceled tasks appears as CANCELED.

  This is a design choice (any non-success task makes the fire non-success), but it means that:
  1. Partial success in broadcast mode is invisible at the fire level
  2. Dashboard "success rate" metrics are misleading for broadcast schedules
  3. The `findFirstErrorTask` priority mismatch (AR-15) compounds this: error codes may come from a CANCELED task rather than the FAILED/TIMEOUT task
- **风险**: Broadcast schedule operators see misleading fire-level status. The actual task-level status must be inspected individually. Combined with AR-15 (error code from wrong task), fire-level error diagnostics are unreliable for broadcast schedules.
- **建议**: Consider adding a `partialSuccess` status or at minimum a `successTaskCount`/`totalTaskCount` pair on the fire entity to give operators visibility into partial success. Document the broadcast fire status aggregation semantics clearly.
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试

---

### [AR-78] `NopJobScheduleBizModel` does not override `delete` — schedule deletion orphans fires and tasks

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:38` (class declaration, 280 lines)
- **证据片段**:
  ```java
  @BizModel("NopJobSchedule")
  public class NopJobScheduleBizModel extends CrudBizModel<NopJobSchedule> implements INopJobScheduleBiz {
      // No override of delete()
      // Overrides: enableSchedule, disableSchedule, pauseSchedule, resumeSchedule,
      //            triggerNow, archiveSchedule, cancelSchedule
  }

  // Compare NopJobTaskBizModel:
  @Override
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED)...;
  }

  // Compare NopJobFireBizModel (via xmeta, per AR-65):
  // No delete restriction either (AR-65 still unfixed)
  ```
- **严重程度**: P2
- **现状**: `NopJobScheduleBizModel` provides `archiveSchedule` but does NOT override `delete()`. Through the GraphQL API, any authenticated user can call `delete__NopJobSchedule` to physically delete a schedule. The ORM model has no cascade delete from `NopJobSchedule` to `NopJobFire` or from `NopJobFire` to `NopJobTask`. Deleting a schedule with active fires/tasks:
  1. Leaves orphan fires in RUNNING/WAITING/DISPATCHING state
  2. The completion processor's `tryLoadSchedule` returns null → calls `failFireWithoutSchedule` (AR-23 fix handles this gracefully)
  3. But the orphan fires generate error logs on every scan cycle until they are force-failed
  4. Engine counters (`totalFireCount`, `successFireCount`, etc.) on the now-deleted schedule are lost

  `NopJobTaskBizModel` blocks `delete`. `NopJobFireBizModel` also doesn't block `delete` (AR-65, still unfixed). The schedule is the root entity — its deletion should be the most protected.
- **风险**: Direct schedule deletion via API orphans fires and tasks. In the worst case, a batch delete of schedules creates hundreds of orphan fires that generate error logs on every scan cycle until individually force-failed.
- **建议**: Override `delete()` to throw `ERR_JOB_SCHEDULE_DELETE_NOT_ALLOWED` (similar to `NopJobTaskBizModel`). Force users through `archiveSchedule` which properly sets `nextFireTime = null` and `scheduleStatus = ARCHIVED`.
- **信心水平**: 确定
- **发现来源视角**: GraphQL 契约考古

---

### [AR-79] Race between planner creating fires and completion processor marking schedule COMPLETED

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:200-211`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:148-177`
- **证据片段**:
  ```java
  // Completion processor:
  if (completionDecision.completed) {
      schedule.setScheduleStatus(SCHEDULE_STATUS_COMPLETED);
      schedule.setNextFireTime(null);
  }
  fireStore.completeFireAndUpdateSchedule(fire, schedule); // atomic with version check

  // Planner (runs concurrently):
  // fetchDueSchedules checks scheduleStatus = ENABLED and nextFireTime <= now
  // If planner loaded schedule BEFORE completion processor set COMPLETED:
  //   - planner has schedule with ENABLED status and valid nextFireTime
  //   - planner creates fire, advances nextFireTime
  //   - completion processor then sets COMPLETED + nextFireTime=null
  //   - but planner already created the fire
  ```
- **严重程度**: P2
- **现状**: The planner loads due schedules via `fetchDueSchedules` (query: `scheduleStatus = ENABLED AND nextFireTime <= now`), then processes them. The completion processor loads the schedule, modifies `scheduleStatus = COMPLETED` and `nextFireTime = null`, then saves via `completeFireAndUpdateSchedule`. If the planner loaded the schedule before the completion processor's update, the planner creates a new fire. The completion processor then overwrites the planner's `nextFireTime` with `null` (via optimistic lock retry, which correctly picks up the latest version). The orphaned fire will be dispatched and executed, but the schedule is now COMPLETED — no further fires will be created.

  The `allowResultCompletion` gate (AR-29 fix) requires explicit opt-in, reducing the attack surface. But for schedules that opt in, the race window exists on every fire completion.
- **风险**: Schedules with `allowResultCompletion=true` may execute one extra fire after being marked COMPLETED. The orphaned fire's results are processed normally (the completion processor handles COMPLETED schedules by force-failing the fire or logging a warning). No data corruption, but it's a semantic violation of "no more fires after COMPLETED."
- **建议**: The planner should re-check `scheduleStatus` after acquiring the lock (in `tryLockSchedulesForPlan`), or the completion processor should check for newly-created fires before marking COMPLETED. Alternatively, document this as a known edge case with minimal practical impact.
- **信心水平**: 很可能（race window is narrow — requires exact timing between planner load and completion save）
- **发现来源视角**: 组合爆炸测试

---

### [AR-80] `JobCompletionProcessorImpl` calls `getCurrentTime()` multiple times in one fire completion — inconsistent timestamps

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:170-209`
- **证据片段**:
  ```java
  Timestamp fireEndTime = latestEndTime(tasks, new Timestamp(scheduleStore.getCurrentTime())); // call 1
  // ...
  fire.setUpdateTime(new Timestamp(scheduleStore.getCurrentTime()));  // call 2
  // ...
  schedule.setUpdateTime(new Timestamp(scheduleStore.getCurrentTime())); // call 3
  ```
- **严重程度**: P3
- **现状**: Each `getCurrentTime()` call queries the database for the current timestamp (DB estimated clock). Between calls, the time may advance (especially under load or slow DB), leading to `fire.updateTime != schedule.updateTime` or `fireEndTime != schedule.updateTime`. While the delta is typically milliseconds, it introduces timestamp inconsistency in the same logical operation.
- **风险**: Audit trails show inconsistent timestamps for the same fire completion event. Minor operational confusion.
- **建议**: Capture a single time snapshot at the beginning of `tryCompleteFireAndGetStatus` and reuse it for all timestamp assignments.
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

### [AR-81] No metric emitted for discarded fires (DISCARD block strategy)

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:152-155`
- **证据片段**:
  ```java
  if (shouldDiscard(schedule)) {
      scheduleStore.advanceScheduleAfterSkip(schedule, nextFireTime);
      continue;  // no metric recorded
  }
  ```
- **严重程度**: P3
- **现状**: When a schedule has active fires and `blockStrategy == DISCARD`, the due fire is silently skipped with no metric emitted. `plannerMetrics` only tracks `onDueSchedules` and `onLockConflicts`. Operators have no visibility into how many fires are being discarded.
- **风险**: Operators cannot detect schedules that are consistently overloaded and discarding work. A `plannerMetrics.onDiscardedFire()` counter would provide operational visibility.
- **建议**: Add a `onDiscardedFire()` counter to `IJobPlannerMetrics` and call it in the discard path.
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维

---

### [AR-82] `JobDispatcherScannerImpl` performs dynamic bean lookup on every fire dispatch

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:137-147`
- **证据片段**:
  ```java
  private IJobTaskBuilder resolveTaskBuilder(NopJobFire fire) {
      String executorKind = fire.getExecutorKind();
      if (executorKind != null && !executorKind.isBlank()) {
          String beanName = TASK_BUILDER_PREFIX + executorKind;
          Object bean = BeanContainer.tryGetBean(beanName); // lookup on every fire
          if (bean instanceof IJobTaskBuilder) {
              return (IJobTaskBuilder) bean;
          }
      }
      return defaultTaskBuilder;
  }
  ```
- **严重程度**: P3
- **现状**: On every fire dispatch, `BeanContainer.tryGetBean()` is called to look up the task builder by name. While `BeanContainer` typically uses a `ConcurrentHashMap`, the repeated lookup is unnecessary overhead on the critical dispatch path. A local `ConcurrentHashMap<String, IJobTaskBuilder>` cache with `computeIfAbsent` would eliminate repeated lookups for the same `executorKind`.
- **风险**: Minor performance overhead on the hot dispatch path. In 10x scale with thousands of dispatches per second, the repeated lookups add up.
- **建议**: Cache resolved task builders in a local `ConcurrentHashMap`, falling back to `BeanContainer.tryGetBean()` on cache miss.
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探

---

## Overall Assessment

### Top 3 Actionable Directions

**1. Coordinator lifecycle safety (AR-70, AR-74) — P1 + P2**

`doStop()` lacks per-component try-catch, risking incomplete shutdown. The stop order is inverted (producer stops last), creating orphaned fires during shutdown. Together, these mean that under any shutdown failure scenario, the coordinator may leave orphaned fires and running scanners. Fix: wrap each stop call in try-catch and reverse the stop order.

**2. Worker identity chain broken (AR-71) — P1**

The `workerInstanceId` = coordinator host + `resolveAliveWorkerIds` = coordinator's own service means the SUSPICIOUS detection mechanism is effectively a no-op. This is the most surprising finding — the entire SUSPICIOUS optimization provides no benefit in any deployment topology because the coordinator always sees itself as alive. Tasks from crashed workers always wait the full timeout. The fix requires either using the actual executor's service name/instance ID, or acknowledging the limitation.

**3. Completion processor batch isolation (AR-73) + block strategy fallthrough (AR-75) — P2 + P2**

The completion processor's lack of per-fire try-catch means one problematic fire blocks the entire batch. The planner's unknown block strategy fallthrough silently creates concurrent fires. Both are silent correctness/availability issues that degrade under 10x scale.

### Blind Spot Assessment

This review likely missed:

1. **Multi-coordinator HA partition rebalancing**: The interaction between partition reassignment and in-flight fires/tasks was not analyzed.
2. **ORM codegen correctness**: `_` prefixed generated code was not systematically verified against source models.
3. **nop-job-web module**: Frontend pages and Web layer configuration were not reviewed.
4. **Calendar chain combinations**: While individual Calendar types have been extensively reviewed, the interaction of 3+ chained Calendar types with different exclusion ranges was not tested.
5. **Database-specific behavior**: All analysis assumes the ORM layer provides correct transaction isolation. Database-specific behaviors (MySQL vs PostgreSQL gap locks, MVCC behavior under high concurrency) were not considered.

---

## Finding Summary Table (New)

| # | Severity | Short Description | Confidence |
|---|----------|-------------------|------------|
| AR-70 | **P1** | doStop() cascading failure — no per-component try-catch | Certain |
| AR-71 | **P1** | workerInstanceId = coordinator host — SUSPICIOUS detection is no-op | Certain |
| AR-72 | **P1** | Task execution timeout falls back to dispatchTimeoutMs — semantic mismatch | Certain |
| AR-73 | P2 | Completion processor no per-fire try-catch — one failure aborts batch | Certain |
| AR-74 | P2 | Stop order inverted — planner creates orphaned fires during shutdown | Certain |
| AR-75 | P2 | Unknown blockStrategy falls through — creates concurrent fires silently | Certain |
| AR-76 | P2 | No config validation — zero/negative values cause silent malfunction | Certain |
| AR-77 | P2 | Broadcast fire status determined by worst task — partial success invisible | Certain |
| AR-78 | P2 | NopJobScheduleBizModel does not override delete — orphans fires/tasks | Certain |
| AR-79 | P2 | Planner vs completion processor race on schedule COMPLETED | Likely |
| AR-80 | P3 | getCurrentTime() called multiple times — inconsistent timestamps | Certain |
| AR-81 | P3 | No metric for discarded fires (DISCARD strategy) | Certain |
| AR-82 | P3 | Dynamic bean lookup on every fire dispatch | Certain |

---

## 严重程度分布 (Round 9 New)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 3    | 生命周期安全, Worker 身份链断裂, 超时语义混淆 |
| P2      | 7    | 批量隔离, 停止顺序, block strategy, 配置校验, 广播状态, schedule 删除, COMPLETED 竞态 |
| P3      | 3    | 时间戳不一致, 指标缺失, bean 查找开销 |

## 累计未修复项汇总 (AR-1~AR-82)

| 严重程度 | 数量 | 来源 |
|---------|------|------|
| P0      | 1    | AR-54 (R8, DailyCalendarSpec crash) |
| P1      | 6    | AR-55,56,58 (R8 Calendar/mapping/JSON) + AR-70,71,72 (R9 lifecycle/identity/timeout) |
| P2      | 24   | AR-40,42,43,44,46 (R6) + AR-48~AR-52 (R7) + AR-61~AR-67 (R8) + AR-73~AR-79 (R9) |
| P3      | 13   | AR-6,7,14,15,16,45,47 (R1-R6) + AR-53 (R7) + AR-68,69 (R8) + AR-80,81,82 (R9) |

**总未修复**: 44 项 (1×P0, 6×P1, 24×P2, 13×P3)
