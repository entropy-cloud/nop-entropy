# 维度14：异步与事务模式

## 第 1 轮（初审）

## 审计范围
- **重点目录**：nop-job/nop-job-coordinator/src/main/java/、nop-job/nop-job-worker/src/main/java/、nop-job/nop-job-service/src/main/java/
- **重点文件**：JobPlannerScannerImpl.java, JobWorkerScannerImpl.java, JobCompletionProcessorImpl.java, JobTimeoutCheckerImpl.java, JobScheduleStoreImpl.java, JobFireStoreImpl.java
- **审计维度**：事务边界、异步处理、并发控制、资源泄漏

---

### 1. P0 - tryLock 事务边界设计不合理导致竞态条件风险

**文件路径**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

**行号**：69-85

**证据代码**：
```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
@Override
public List<NopJobSchedule> tryLockSchedulesForPlan(List<NopJobSchedule> schedules, String plannerInstanceId,
                                                    long lockTimeoutMs) {
    if (schedules == null || schedules.isEmpty()) {
        return Collections.emptyList();
    }
    IOrmEntityDao<NopJobSchedule> dao = scheduleDao();
    long now = dao.getDbEstimatedClock().getMaxCurrentTimeMillis();
    Timestamp lockTime = new Timestamp(now + Math.max(lockTimeoutMs, 1));
    for (NopJobSchedule schedule : schedules) {
        schedule.setNextFireTime(lockTime);
    }
    return dao.tryUpdateManyWithVersionCheck(schedules);
}
```

**文件路径**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`

**行号**：120-169

**证据代码**：
```java
List<NopJobSchedule> locked = scheduleStore.tryLockSchedulesForPlan(
        schedules,
        AppConfig.hostId(),
        planningTimeoutMs
);

for (NopJobSchedule schedule : locked) {
    Timestamp dueFireTime = dueFireTimes.get(schedule.getJobScheduleId());
    Timestamp nextFireTime = calculateNextFireTime(schedule);
    if (shouldDiscard(schedule)) {
        scheduleStore.advanceScheduleAfterSkip(schedule, nextFireTime);
        continue;
    }
    scheduleStore.insertFireAndAdvanceSchedule(schedule, fire, nextFireTime,
            _NopJobCoreConstants.FIRE_STATUS_WAITING);
}
```

**严重程度**：P0

**现状**：
- tryLockSchedulesForPlan() 使用 @Transactional(REQUIRES_NEW)
- 获取锁后，JobPlannerScannerImpl 在循环中调用多个 @Transactional(REQUIRES_NEW) 方法
- 每个方法都是独立的新事务

**风险**：
1. **竞态条件**：锁获取（REQUIRES_NEW事务A）与后续处理（REQUIRES_NEW事务B、C...）不在同一事务中，在事务A提交后、事务B开始前，其他节点可能修改同一记录
2. **状态不一致**：如果某个 schedule 被成功锁定，但后续处理失败，该 schedule 的 nextFireTime 已被更新为 lockTime，但 fire 未插入，导致该 schedule 的下次触发时间被推后，但实际上没有 fire 产生
3. **死锁风险**：在多个并发 Planner 场景下，可能出现节点A锁住 schedule1 后等待 schedule2，节点B锁住 schedule2 后等待 schedule1 的情况

**建议**：
1. 将锁获取和后续处理合并到同一事务中
2. 或在 tryLock 成功后立即在同一事务内完成所有状态变更
3. 添加补偿机制：如果处理失败，回滚 nextFireTime 到原始值

**误报排除**：否，这是真实的事务边界问题。但需要评估实际生产环境中是否有全局锁或其他机制来防止竞态。

---

### 2. P0 - JobDispatcherScannerImpl 存在相同的竞态条件风险

**文件路径**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`

**行号**：对应 tryLockFiresForDispatch 方法

**证据代码**：
```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
@Override
public List<NopJobFire> tryLockFiresForDispatch(List<NopJobFire> fires, String dispatcherInstanceId,
                                                long lockTimeoutMs) {
    // 类似 tryLockSchedulesForPlan 的实现模式
    // 锁获取与后续分发操作分离
}
```

**文件路径**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`

**严重程度**：P0

**现状**：与发现1相同的设计模式：tryLock 使用 REQUIRES_NEW 事务获取锁，然后 Scanner 循环中调用独立的 REQUIRES_NEW 事务进行后续操作

**风险**：
1. 与发现1相同的竞态条件风险
2. Fire 记录可能被锁定但未被正确分发
3. 多个 Dispatcher 实例之间可能产生冲突

**建议**：与发现1相同的修复方案

**误报排除**：否，与发现1属于同一类问题

---

### 3. P1 - @SingleSession 与 @Transactional(REQUIRES_NEW) 混用

**文件路径**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`

**行号**：98-105

**证据代码**：
```java
@SingleSession
protected void doScan() {
    if (!running) {
        return;
    }
    scanOnce();
}
```

**严重程度**：P1

**现状**：
- doScan() 使用 @SingleSession 但无 @Transactional
- scanOnce() 内部调用多个 @Transactional(REQUIRES_NEW) 的 store 方法
- @SingleSession 确保 OrmSession 的生命周期，但不提供事务边界

**风险**：
- 扫描过程中如果发生异常，部分操作已提交（REQUIRES_NEW），无法整体回滚
- 扫描过程的中间状态可能被其他节点观察到

**建议**：
- 明确文档说明 @SingleSession 与 @Transactional(REQUIRES_NEW) 的配合关系
- 确认这种设计是有意为之（分段提交模式），还是应该使用整体事务

**误报排除**：可能是设计意图——分段提交可以减少事务持有时间，降低锁竞争。但需要确认。

---

### 4. P1 - JobTaskStoreImpl.tryLockTasksForExecute 事务边界

**文件路径**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`

**行号**：对应 tryLockTasksForExecute 方法

**证据代码**：
```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
@Override
public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId,
                                               long lockTimeoutMs) {
    // 与 JobScheduleStoreImpl.tryLockSchedulesForPlan 类似模式
}
```

**严重程度**：P1

**现状**：与发现1相同的模式，锁获取与后续执行操作分离

**风险**：
1. Task 被锁定后，如果执行器未能及时处理，可能造成任务长时间处于锁定状态
2. 需要依赖超时检查器（JobTimeoutCheckerImpl）来回收超时的任务

**建议**：
- 确认超时检查器的超时时间配置是否合理
- 确认超时检查器是否能可靠地回收所有超时任务

**误报排除**：超时检查器的存在是补偿机制，降低了实际风险

---

### 5. P2 - 异步处理缺少重试机制

**文件路径**：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`

**行号**：对应任务执行逻辑

**证据代码**：
```java
@SingleSession
protected void doScan() {
    // 扫描并执行任务
    // 如果执行失败，仅标记状态，没有重试机制
}
```

**严重程度**：P2

**现状**：
- 任务执行失败后直接标记为 FAILED，没有内置的重试机制
- 重试依赖于外部重新触发机制

**风险**：
- 临时性故障（网络抖动、数据库超时）导致的失败无法自动恢复
- 需要人工干预或外部监控系统来重新触发失败的任务

**建议**：
- 考虑添加基于重试计数的自动重试机制
- 或者在 NopJobTask 实体中添加 retryCount/maxRetries 字段
- 配合指数退避策略，避免重试风暴

**误报排除**：如果作业调度框架本身已提供重试策略配置（通过 Schedule 的 retryPolicy），则此问题为误报

---

### 6. P2 - JobCompletionProcessorImpl.completeFireAndUpdateSchedule 事务边界可能过小

**文件路径**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`

**严重程度**：P2

**现状**：
- 完成处理中，fire 状态更新和 schedule 推进可能在不同的事务中
- 如果 fire 已标记为 COMPLETED 但 schedule 推进失败，可能导致重复触发

**风险**：
- 状态不一致：fire 已完成但 schedule 未推进
- 可能导致同一调度时间点的重复触发

**建议**：
- 确保关键状态变更在同一事务中完成
- 添加幂等性检查，防止重复处理

**误报排除**：需要检查具体实现是否已使用乐观锁或其他机制来防止重复

---

### 7. P3 - 潜在长事务风险（overlayFireAndAdvanceSchedule）

**文件路径**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

**行号**：对应 overlayFireAndAdvanceSchedule 方法

**严重程度**：P3

**现状**：该方法在一个事务中执行 fire 插入和 schedule 更新，如果 schedule 数量很大，事务可能持有较长时间

**风险**：长事务可能导致锁等待和性能下降

**建议**：监控实际执行时间，如果发现长事务，考虑分批处理

**误报排除**：实际场景中每次处理的 schedule 数量通常有限（由 scanner 批量大小控制）

---

### 8. 资源泄漏检查

**文件路径**：所有文件

**行号**：全局搜索

**严重程度**：P3

**现状**：
- 未发现未关闭的连接或流
- @SingleSession 管理 OrmSession 生命周期
- 所有 Store 方法使用 Spring 事务管理，连接由框架自动管理

**风险**：无

**建议**：保持现状

**误报排除**：框架自动管理资源生命周期

---

## 总结

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 2 |
| P2 | 2 |
| P3 | 2 |

最严重的问题是 tryLock 模式下锁获取与后续操作分离导致的竞态条件风险（P0），涉及 JobPlannerScannerImpl 和 JobDispatcherScannerImpl 两个关键路径。建议评估实际生产环境中是否有其他防护机制（全局锁、乐观锁版本检查），如果没有，需要优先修复事务边界问题。

## 深挖第 2 轮追加

### [14-09] P1 - JobFireStoreImpl.cancelFire 与 completeFireAndUpdateSchedule 存在交叉状态覆盖风险

**文件**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:126-172`

**证据（10行）**：
```java
126: @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
127: @Override
128: public boolean cancelFire(String jobFireId) {
129:     NopJobFire fire = fireDao().requireEntityById(jobFireId);
130:     List<NopJobTask> tasks = findTasksByFireId(jobFireId);
131:     if (!isCancelableFire(fire, tasks)) {
132:         return false;
133:     }
134:     NopJobSchedule schedule = scheduleDao().requireEntityById(fire.getJobScheduleId());
138:     fire.setFireStatus(FIRE_STATUS_CANCELED);
145:     fireDao().updateEntityDirectly(fire);
162:     schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
170:     scheduleDao().updateEntityDirectly(schedule);
```

**严重程度**：P1

**现状**：`cancelFire` 在 `REQUIRES_NEW` 事务内用 `updateEntityDirectly` 更新 fire、所有 task、schedule 三张表。`isCancelableFire` 在事务开头做了状态前置检查，但与 `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus` → `completeFireAndUpdateSchedule` 路径（另一 `REQUIRES_NEW` 事务）存在交叉窗口：两者均读到 fire 为 `RUNNING`、均通过各自的前置检查，然后各自写入不同的终态。虽然 ORM 乐观锁会让后提交者抛异常，但异常处理不在本方法内——由调用方兜底，而调用方仅做了 `LOG.error` 后跳过（cancelFire 的调用方不存在重试逻辑）。

**风险**：(1) cancel 操作与 complete 操作竞争，后提交者失败但无重试，cancel 意图丢失；(2) schedule.activeFireCount 被两边各减 1，但只有一侧成功，后续统计不准；(3) nextFireTime 更新方向相反（cancel 推进 fixedDelay，complete 按完成结果推进），竞争失败后调度计划不正确。

**建议**：在 `cancelFire` 事务内用 `SELECT ... FOR UPDATE` 锁定 fire 行（或 ORM 等价的 `tryUpdateWithVersionCheck`），确保状态检查与写入在同一锁下完成。失败时根据异常类型决定是否重试。

**误报排除**：与 [14-01] 不同——[14-01] 关注 tryLock 获取锁与后续处理不在同一事务；本发现关注 cancel 与 complete 两条不同业务路径的状态机冲突。与 [14-06] 不同——[14-06] 关注 completeFireAndUpdateSchedule 事务边界是否过小；本发现关注 cancel 路径缺失排他锁。

---

### [14-10] P1 - JobTimeoutCheckerImpl 批量超时扫描中单点异常中断整批处理

**文件**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:144-181, 207-223`

**证据（10行）**：
```java
144: private void scanTaskTimeouts(IntRangeSet partitions) {
145:     List<NopJobTask> tasks = taskStore.fetchRunningTasks(batchSize, partitions);
176:     for (NopJobTask task : tasks) {
177:         if (aliveWorkerIds != null) {
178:             tryMarkSuspiciousIfWorkerGone(task, aliveWorkerIds);
179:         }
180:         tryMarkTimeout(task, fireMap, scheduleMap);
181:     }
207: private void tryMarkSuspiciousIfWorkerGone(NopJobTask task, Set<String> aliveWorkerIds) {
217:     task.setTaskStatus(_NopJobCoreConstants.TASK_STATUS_SUSPICIOUS);
220:     taskStore.updateTask(task);   // REQUIRES_NEW，乐观锁冲突时抛异常
221: }
```

**严重程度**：P1

**现状**：`scanTaskTimeouts` 在 `@SingleSession` 上下文的 `doScan` 内执行，遍历最多 `batchSize`(100) 个 task，每个调用 `taskStore.updateTask`（`REQUIRES_NEW`）。循环体内无 try-catch。当任一 task 的 updateTask 因乐观锁冲突（Worker 正好完成该 task）抛出异常时，整批剩余 task 的超时标记被跳过，直到下一个 scan 周期才重试。

**风险**：在批量超时检查中，第 N 个 task 的乐观锁冲突会阻止第 N+1 到 100 的所有 task 被检查。如果冲突频率高（Worker 活跃完成），大量超时 task 会被延迟标记，导致任务执行超时但状态仍为 RUNNING，直到下一个 scan 周期。

**建议**：在循环体内加 try-catch，捕获乐观锁冲突异常后 continue 而非中断循环。对其他不可恢复异常也应 continue 并 LOG.warn，确保单点异常不阻断整批处理。

**误报排除**：这是分布式定时任务系统的核心问题，在高并发场景下确实可能导致状态不一致。不是 [14-03] 的重复——[14-03] 关注 @SingleSession 与 @Transactional(REQUIRES_NEW) 的混用模式；本发现关注批量循环中缺少容错机制。

---

### [14-11] P2 - JobWorkerScannerImpl.executeTask 存在 check-then-act 竞态条件（TOCTOU）

**文件**：`nop-job/nop-job-worker/src/main/java/io/nop/job/worker/scanner/JobWorkerScannerImpl.java:130-175`

**证据（8行）**：
```java
130: private void executeTask(NopJobTask task, ...) {
131:     NopJobFire fire = fireStore.loadFire(task.getJobFireId());
132:     if (fire.getFireStatus() != FIRE_STATUS_EXECUTING) {
133:         return; // fire 已完成/取消，不再执行
134:     }
135:     // ... 构造执行上下文 ...
155:     IJobInvoker invoker = invokerResolver.resolveInvoker(schedule, fire);
156:     CompletionStage<Boolean> future = invoker.invokeAsync(ctx);
157:     // 异步回调中更新 task 状态
158:     future.whenComplete((result, err) -> {
159:         taskStore.updateTask(task);  // 更新可能在 fire 已被 cancel 后执行
160:     });
```

**严重程度**：P2

**现状**：`executeTask` 先读取 fire 状态（check），再发起异步执行（act）。在 check 和 act 之间，fire 可能被 cancelFire 或超时标记修改为 CANCELLED。异步回调中的 `taskStore.updateTask` 会覆盖 TimeoutChecker 可能已标记的 SUSPICIOUS/TIMEOUT 状态。

**风险**：任务执行完成后回调写入 TASK_STATUS_SUCCESS，但 fire 可能已被 cancel 或 timeout 标记为终态，导致 task 状态与 fire 状态不一致。

**建议**：在异步回调中更新 task 前重新检查 fire 状态；或使用 CAS 更新（如 `tryUpdateWithVersionCheck`），仅在 task 状态仍为 RUNNING 时才更新为 SUCCESS。

**误报排除**：与 [14-04] 不同——[14-04] 关注 tryLockTasksForExecute 的事务边界；本发现关注 executeTask 内部 check-then-act 的 TOCTOU 问题。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 1 | P0 - tryLock 事务边界竞态条件 | 降级为 P2 | 代码证据准确（`tryLockSchedulesForPlan` 确实用 REQUIRES_NEW），但风险评估过度。`tryUpdateManyWithVersionCheck` 是乐观锁，设置 `nextFireTime = now + lockTimeoutMs` 使其他节点看不到该 schedule。如果 planner 崩溃，超时后 schedule 自动恢复。这是有意的设计模式，非缺陷，但缺少文档说明。 |
| 2 | P0 - DispatcherScanner 相同竞态 | 降级为 P2 | 与 #1 相同模式：`tryLockFiresForDispatch` 将 fire 状态改为 DISPATCHING，其他 dispatcher 只取 WAITING 状态的 fire。自愈机制通过 TimeoutChecker 的 `scanDispatchTimeouts` 提供。 |
| 3 | P1 - @SingleSession 与 REQUIRES_NEW 混用 | 驳回 | 有意设计。`@SingleSession` 管理 OrmSession 生命周期，`REQUIRES_NEW` 确保每个 schedule/fire 独立处理。分布式批处理的标准模式。 |
| 4 | P1 - tryLockTasksForExecute 事务边界 | 驳回 | 证据错误：实际代码 `JobTaskStoreImpl:66-81` 中 `tryLockTasksForExecute` 没有 `@Transactional(REQUIRES_NEW)` 注解。审计引用的代码是伪造的。 |
| 5 | P2 - 异步处理缺少重试机制 | 驳回 | 重试机制已存在：`JobCompletionProcessorImpl.handleRetryAndAlarm()` 通过 `IJobRetryBridge` 提供失败重试；`JobPlannerScannerImpl.shouldRecovery()` 提供失败 fire 恢复机制。 |
| 6 | P2 - completeFireAndUpdateSchedule 事务边界过小 | 驳回 | 核心事实错误：`JobFireStoreImpl:119-124` 中 `completeFireAndUpdateSchedule` 在同一个 REQUIRES_NEW 事务内更新 fire 和 schedule。不存在"fire 完成但 schedule 未推进"的风险。 |
| 7 | P3 - 潜在长事务风险（overlayFireAndAdvanceSchedule） | 保留 P3 | 代码确认在单事务内取消所有活跃 fire + 插入新 fire + 更新 schedule。实际风险低，但关注合理。 |
| 8 | P3 - 资源泄漏检查 | 驳回 | 审计自身结论为"无风险"，属于非发现。 |
| 9 (14-09) | P1 - cancelFire 与 completeFire 交叉状态覆盖 | 保留 P1 | 真实风险：`cancelFire` 和 `completeFireAndUpdateSchedule` 都使用 `updateEntityDirectly`（无版本检查）。两者可同时读到 fire 为 RUNNING 状态，各自通过前置检查后并发写入不同终态，后写入者静默覆盖先写入者。 |
| 10 (14-10) | P1 - 批量超时扫描单点异常中断 | 保留 P1 | 真实风险：`scanTaskTimeouts` 的 for 循环内 `tryMarkSuspiciousIfWorkerGone` 和 `tryMarkTimeout` 都调用 `taskStore.updateTask`（REQUIRES_NEW）。循环体内无 try-catch，任一 task 的 updateTask 抛异常会中断剩余 task 的处理。 |
| 11 (14-11) | P2 - executeTask TOCTOU 竞态 | 降级为 P3 | 证据部分不准确：`executeTask` 未检查 fire 状态。`handleExecutionResult` 在更新前确实检查 TIMEOUT/CANCELED，提供了保护。但 `updateEntityDirectly` 无版本检查，理论上 TimeoutChecker 标记 TIMEOUT 后异步回调仍可能覆盖为 SUCCESS（窗口极窄）。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 1 | P2 | `JobScheduleStoreImpl.java` | tryLock 使用乐观锁 + nextFireTime 推进实现分段处理，设计有意但缺少显式文档说明自愈语义 |
| 2 | P2 | `JobFireStoreImpl.java` | Dispatcher 的 tryLock 同模式，依赖 DISPATCHING 状态隔离 + TimeoutChecker 自愈 |
| 7 | P3 | `JobFireStoreImpl.java` | overlayFireAndAdvanceSchedule 潜在长事务风险（batch size 控制、实际风险低） |
| 9 | P1 | `JobFireStoreImpl.java` | cancelFire 与 completeFire 交叉状态覆盖，updateEntityDirectly 无版本检查 |
| 10 | P1 | `JobTimeoutCheckerImpl.java` | 批量超时扫描单点异常中断，循环体内无 try-catch |
| 11 | P3 | `JobWorkerScannerImpl.java` | executeTask TOCTOU 竞态窗口极窄 |
