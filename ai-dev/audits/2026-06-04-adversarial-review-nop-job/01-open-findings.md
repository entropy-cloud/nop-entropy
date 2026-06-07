# Adversarial Review: nop-job

- **Date**: 2026-06-04
- **Scope**: `nop-job/` (all sub-modules: api, core, dao, coordinator, worker, service, web, retry-adapter, codegen, app)
- **Reviewer**: AI Adversarial Review (open-ended, discovery-oriented)
- **Perspective used**: 10x-scaling operator, exception-path detective, cross-boundary chain-effect tracker

## Summary

nop-job is a well-architected distributed job scheduler built on the Nop platform. The codebase demonstrates strong design discipline: clear separation between planner/dispatcher/completion/timeout scanners, proper use of optimistic locking, comprehensive status state machines, and good test coverage for race conditions. The findings below are mostly edge cases and cross-cutting patterns that could become problematic at scale or under specific failure combinations.

---

### [AR-1] SUSPICIOUS-to-TIMEOUT promotion skips dispatch-timeout check for the fire itself

- **File**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:405-473`
- **Evidence snippet**:
  ```java
  private void tryMarkTimeout(NopJobTask task, ...) {
      // ...
      if (taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS) {
          markSuspiciousAsTimeout(task, fireMap, scheduleMap);
          return;   // <-- returns immediately after marking single task
      }
      // ...
      // For RUNNING tasks: checks schedule-specific timeout or falls back to dispatchTimeoutMs
  }
  ```
- **Severity**: P2
- **Current state**: When a task is SUSPICIOUS (worker gone), `markSuspiciousAsTimeout` sets the task to TIMEOUT immediately without checking the schedule's `timeoutSeconds`. This means a task can be timed out the very next scan after being marked SUSPICIOUS, regardless of how long the task has actually been running or what the schedule's timeout configuration says. For RUNNING tasks, the timeout is properly bounded by `schedule.getTimeoutSeconds()` or `dispatchTimeoutMs`, but SUSPICIOUS tasks bypass this guard.
- **Risk**: If `namingService` briefly loses sight of a healthy worker (network blip), the task transitions SUSPICIOUS → TIMEOUT within two scan cycles (typically 10 seconds), even if the schedule has a 1-hour timeout configured. The worker could still be executing the job.
- **Suggestion**: Apply the same timeout calculation for SUSPICIOUS tasks as for RUNNING tasks, or at minimum respect `schedule.getTimeoutSeconds()` as a floor before promoting SUSPICIOUS → TIMEOUT.
- **Confidence**: Likely
- **Discovery perspective**: Exception-path detective

### [AR-2] Duplicate IJobExecutionContext construction between DefaultJobCancelHandler and DefaultJobExecutionContextBuilder

- **File**:
  - `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobCancelHandler.java:82-177`
  - `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/DefaultJobExecutionContextBuilder.java:52-174`
- **Evidence snippet**:
  ```java
  // DefaultJobCancelHandler.CancelJobExecutionContext (178 lines)
  private static final class CancelJobExecutionContext extends JobInstanceState implements IJobExecutionContext {
      // ... near-identical constructor logic to WorkerJobExecutionContext
  }

  // DefaultJobExecutionContextBuilder.WorkerJobExecutionContext (175 lines)
  private static final class WorkerJobExecutionContext extends JobInstanceState implements IJobExecutionContext {
      // ... same field mapping, same resolveJobParams logic, same attribute injection
  }
  ```
- **Severity**: P2
- **Current state**: `CancelJobExecutionContext` and `WorkerJobExecutionContext` are two separate inner classes (~175 lines each) that build `IJobExecutionContext` from `(NopJobSchedule, NopJobFire, NopJobTask)`. They duplicate ~90% of their logic: attribute injection, sharding propagation, targetHost handling, jobParams resolution, status mapping, and all `ITriggerEvalContext` method implementations.
- **Risk**: Any future change to the context construction (e.g., adding a new attribute, changing sharding behavior) must be replicated in both places. The `resolveJobParams` method with its `targetHost → headers` injection pattern is already duplicated. If one copy is updated but the other isn't, cancel operations will see different execution context than execution operations.
- **Suggestion**: Extract a shared `JobExecutionContextFactory` or make `CancelJobExecutionContext` delegate to the `IJobExecutionContextBuilder` interface that is already injected into the worker. The builder could accept a "cancel mode" flag.
- **Confidence**: Certain
- **Discovery perspective**: Cross-boundary pattern tracker

### [AR-3] `fetchRunningFires` only matches FIRE_STATUS_RUNNING — fires stuck in DISPATCHING are invisible to completion processor

- **File**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:62-69`
- **Evidence snippet**:
  ```java
  @Override
  public List<NopJobFire> fetchRunningFires(int limit, IntRangeSet partitions) {
      QueryBean query = new QueryBean();
      query.setLimit(limit);
      query.addFilter(FilterBeans.eq(PROP_NAME_fireStatus, _NopJobCoreConstants.FIRE_STATUS_RUNNING));
      // ...
  }
  ```
- **Severity**: P2
- **Current state**: The completion processor (`JobCompletionProcessorImpl`) calls `fetchRunningFires` which only selects fires with `FIRE_STATUS_RUNNING` (value=20). However, fires can also be in `FIRE_STATUS_DISPATCHING` (value=10) — this happens when `tryLockFiresForDispatch` succeeds but `insertTasksAndMarkFireDispatching` never completes (e.g., coordinator crashes between the two steps). The `scanDispatchTimeouts` path in `JobTimeoutCheckerImpl` handles this case and will eventually time them out, so this is not a data loss issue.
- **Risk**: A fire stuck in DISPATCHING status will only be caught by the dispatch timeout scanner (`dispatchTimeoutMs`, default 300s), not by the completion processor. If `dispatchTimeoutMs` is set very high or the timeout checker is not running, such fires remain orphaned indefinitely. This is a secondary risk — the timeout checker is the primary safety net.
- **Suggestion**: Consider having `fetchRunningFires` also include DISPATCHING fires that have passed a reasonable threshold, or document that DISPATCHING→RUNNING transition is expected to be fast and the timeout checker is the sole recovery mechanism for stuck DISPATCHING fires.
- **Confidence**: Likely
- **Discovery perspective**: 10x-scaling operator

### [AR-4] `resolveJobParams` in NopJobScheduleBizModel silently ignores overrideParams content type

- **File**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:244-261`
- **Evidence snippet**:
  ```java
  private Map<String, Object> resolveJobParams(NopJobSchedule schedule, Map<String, Object> overrideParams) {
      if (overrideParams != null) {
          return overrideParams;  // <-- returned as-is, no defensive copy
      }
      Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
      if (scheduleParams != null) {
          return scheduleParams;  // <-- no copy
      }
      // ...
  }
  ```
- **Severity**: P3
- **Current state**: When `triggerNow` is called with `overrideParams`, the map is returned directly and then stringified via `JsonTool.stringify()` for `jobParamsSnapshot`. However, the returned map is not defensively copied. If the caller mutates the map after the call, it could affect the snapshot (though `JsonTool.stringify` is called immediately, which effectively serializes it). More importantly, when `scheduleParams` is returned, it's the live internal map from `getJobParamsComponent().get_jsonMap()` — if downstream code mutates it, it would corrupt the entity's state.
- **Risk**: Low — `JsonTool.stringify` is called immediately, so the mutation window is tiny. But it violates the principle of not leaking internal mutable state.
- **Suggestion**: Wrap with `Collections.unmodifiableMap()` or `new HashMap<>()` for safety, consistent with how `copyMap` is used in `JobPlannerScannerImpl`.
- **Confidence**: Likely
- **Discovery perspective**: Exception-path detective

### [AR-5] `TriggerSpec.maxFailedCount` is hardcoded to 0, dead code in `LimitCountTrigger`

- **File**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java:34`
- **Evidence snippet**:
  ```java
  spec.setMaxFailedCount(0);  // always 0
  ```
- **Severity**: P3
- **Current state**: `TriggerSpec.maxFailedCount` is always set to 0. The `LimitCountTrigger` that uses this field exists in the core trigger chain and checks `evalContext.getMaxFailedCount()`, but since the spec always passes 0, the trigger is effectively a no-op. The `ITriggerEvalContext.getMaxFailedCount()` in `TriggerSpecHelper.toEvalContext()` doesn't even exist — there is no `getMaxFailedCount()` method on the anonymous `ITriggerEvalContext`.
- **Risk**: The `LimitCountTrigger` is dead code — it never triggers its limit check. If someone later sets `maxFailedCount` to a non-zero value in the ORM model (there's no column for it currently), it would fail because the `ITriggerEvalContext` doesn't expose this value. This is a minor dead-code / unused-extension-point issue.
- **Suggestion**: Either remove `maxFailedCount` from `TriggerSpec` and `LimitCountTrigger`, or add the missing column and eval-context method. The current state is confusing for future maintainers who might expect `maxFailedCount` to work.
- **Confidence**: Certain
- **Discovery perspective**: Dead-code scavenger

### [AR-6] `fireStore.completeFireAndUpdateSchedule` silently swallows fire version conflicts

- **File**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:120-129`
- **Evidence snippet**:
  ```java
  public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
      NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
      if (TERMINAL_FIRE_STATUSES.contains(currentFire.getFireStatus())) {
          return;  // OK — already completed
      }
      List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(fire));
      if (updated.isEmpty()) {
          return;  // <-- silent return on version conflict, no log, no retry
      }
      // ... continues to update schedule
  }
  ```
- **Severity**: P2
- **Current state**: If the fire's version has changed since the completion processor loaded it (e.g., a concurrent cancel overlapped), `tryUpdateManyWithVersionCheck` returns empty and the method silently returns. The schedule's `activeFireCount` is never decremented, and the fire remains in RUNNING status in the database. The completion processor will pick it up again on the next scan, but if the concurrent modification keeps happening (unlikely but possible under heavy load), the fire could remain stuck.
- **Risk**: Under high concurrency, a fire's completion could be silently lost for one scan cycle. The fire would be re-fetched on the next scan and retried, so this is self-healing. But the lack of a WARN log means this condition is invisible to operators. Compare with `fireStore.cancelFire` which does 5 retries with version refresh.
- **Suggestion**: Add a WARN log when the fire version check fails, similar to the pattern in `cancelFire`. Consider adding a retry loop (1-2 attempts) for the fire update before giving up.
- **Confidence**: Likely
- **Discovery perspective**: Exception-path detective

### [AR-7] NopJobSchedule.scheduleStatus has no column-level constraint in ORM, allowing illegal values

- **File**: `nop-job/model/nop-job.orm.xml:104-106`
- **Evidence snippet**:
  ```xml
  <column code="SCHEDULE_STATUS" displayName="调度状态" mandatory="true" name="scheduleStatus" propId="7"
          stdDataType="int" stdSqlType="INTEGER" i18n-en:displayName="Schedule Status"
          ext:dict="job/schedule-status"/>
  ```
- **Severity**: P3
- **Current state**: The `scheduleStatus` column uses `ext:dict="job/schedule-status"` which provides UI-level validation and display names, but there is no `ext:validation` or database-level CHECK constraint. The Java code checks for specific status values (0, 10, 20, 30, 40) but nothing prevents a direct SQL update or a GraphQL mutation from setting it to, say, 999. The BizModel's `validateScheduleStatus` would catch this for actions routed through the service layer, but direct ORM updates (e.g., from the engine scanners) do not validate status values before persisting.
- **Risk**: Low — all engine paths set status to known constants. The risk is from direct database manipulation or future code that doesn't go through the validation layer.
- **Suggestion**: This is a minor hardening opportunity. The Nop platform convention relies on dict validation at the xmeta/GraphQL layer, which is the standard approach. No immediate action needed.
- **Confidence**: Certain
- **Discovery perspective**: Dead-code scavenger (recognized this is standard Nop pattern, not a real issue)

### [AR-8] `JobCoordinator` does not wait for scanners to fully stop before returning from `doStop()`

- **File**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java:49-61`
- **Evidence snippet**:
  ```java
  @Override
  protected void doStop() {
      if (timeoutChecker != null) timeoutChecker.stopScanning();
      if (completionProcessor != null) completionProcessor.stopScanning();
      if (dispatcherScanner != null) dispatcherScanner.stopScanning();
      if (plannerScanner != null) plannerScanner.stopScanning();
  }
  ```
  And in each scanner:
  ```java
  public synchronized void stopScanning() {
      running = false;
      if (scanFuture != null) {
          scanFuture.cancel(false);  // does not wait for completion
          scanFuture = null;
      }
  }
  ```
- **Severity**: P2
- **Current state**: `stopScanning()` sets `running = false` and calls `cancel(false)` on the scheduled future, but does not await termination. Since `cancel(false)` only prevents future executions (does not interrupt a running task), an in-progress `doScan()` / `scanOnce()` will continue to execute after `stopScanning()` returns. The `doScan()` method checks `if (!running) return;` but this is a volatile read without synchronization against the `cancel()` call.
- **Risk**: During graceful shutdown, the coordinator returns from `doStop()` while scanner tasks may still be in-flight and accessing database connections or stores. If the IoC container proceeds to destroy beans (like `IJobScheduleStore`), the in-flight scan could get `NullPointerException` or `IllegalStateException`. In practice, the `LifeCycleSupport` ordering and the short scan intervals make this a small window, but it exists.
- **Suggestion**: After `cancel(false)`, add a brief `scanFuture.get(5, TimeUnit.SECONDS)` to wait for the current scan to complete, or use a `CountDownLatch` in the scan to signal completion. Alternatively, document that the caller should wait for all scheduled tasks to drain before destroying dependent beans.
- **Confidence**: Likely
- **Discovery perspective**: 10x-scaling operator

---

## 总评

nop-job 当前最值得关注的 1-3 个方向：

1. **SUSPICIOUS→TIMEOUT 的超时阈值缺失** (AR-1): 这是唯一一个可能导致"正确配置的超时被意外绕过"的问题。在生产环境中，命名服务的短暂网络抖动可能导致正常任务被过早超时。建议在 SUSPICIOUS 提升为 TIMEOUT 时，至少尊重 schedule 的 timeoutSeconds 作为下限。

2. **IJobExecutionContext 的重复构建** (AR-2): 两个 ~175 行的内部类几乎完全重复，这是目前维护成本最高的代码模式。每次新增上下文字段都需要同步修改两个地方，违反了 DRY 原则。建议提取共享工厂方法。

3. **completion processor 的静默失败** (AR-6): `completeFireAndUpdateSchedule` 在 fire 版本冲突时静默返回，没有日志。虽然自愈，但会给运维排查带来困扰。添加 WARN 日志即可解决。

## 本次审查的盲区自评

1. **Trigger 计算引擎的边界条件**: 我没有深入审查 `CronExpression`、`TriggerBuilder`、以及各种 calendar 实现的边界条件（如跨时区、闰秒、2位年份等）。这些是经典的复杂逻辑区域。
2. **RPC 层故障注入**: `RpcJobInvoker` 通过 `IRpcServiceInvoker` 调用远程服务，我没有验证在 RPC 超时、重试、连接池耗尽等场景下的行为是否符合预期。
3. **Web 层和 view.xml**: 我没有审查 `NopJobFire.view.xml`、`NopJobSchedule.view.xml` 等前端定义，也没有检查 action-auth 配置是否与 BizModel 方法完全对齐。
4. **Delta 定制冲突**: 我没有模拟基础产品升级后 Delta 文件（如 worker 的 `app-engine.beans.xml` delta）是否会产生合并冲突。
5. **大规模分区下的性能**: `IntRangeSet` 过滤和 `batchSize` 限制在数千个分区或数十万活跃 fire 下的表现没有被评估。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 0    | —       |
| P2      | 4    | 超时语义(AR-1), 代码重复(AR-2), 静默失败(AR-6), 生命周期(AR-8) |
| P3      | 3    | 防御性拷贝(AR-4), 死代码(AR-5), 约束(AR-7) |
