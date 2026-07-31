# nop-job 状态判断一致性与并发冲突分析

> Status: open
> Date: 2026-07-31
> Revision: v3（经两轮独立审查：v2 修正 §4.4/§4.8 推演错误与定级、补充 §4.5b；v3 补充 §4.2 的 OVERLAY 边界条件。已达成审查共识 approve）
> Scope: nop-job-core / nop-job-dao / nop-job-coordinator / nop-job-worker / nop-job-service
> Conclusion: 见 §7。核心结论：整体并发模型（乐观锁 + 预留信号）健全，但存在 1 个 P0 状态卡死缺陷（CLAIMED 无回收）、5 个 P1（SUSPICIOUS 语义割裂、completion 隐式契约、schedule 计数偏差三源）、若干 P2/P3 一致性问题。

## Context

- nop-job 是基于"乐观锁 + 预留信号 + per-item 事务"的分布式调度引擎，5 个 Scanner（Planner/Dispatcher/Completion/Timeout/Worker）+ Store 层协作推进 Schedule/Fire/Task 三级状态机。
- 本次分析目标：核查各级状态判断逻辑是否一致、并发场景下是否存在覆盖写入/计数偏差/状态卡死。
- 事实基础来自对全部 Scanner/Store/Helper 的逐方法核查（锚点见各节），以及对 nop-orm flush 版本检查机制的验证（§5.1）。

## 1. 评估框架

| 维度 | 含义 |
|------|------|
| **数据一致性** | fire/task/schedule 计数字段（activeFireCount/totalFireCount/failFireCount 等）是否会偏差 |
| **状态机完整性** | 是否存在无进/出边的死状态、非法转换 |
| **并发安全** | 乐观锁窗口内是否存在覆盖写入、终态被回退 |
| **活性（liveness）** | 故障（worker/planner 崩溃）后系统能否自动恢复，还是永久卡死 |
| **一致性（consistency of convention）** | 同一语义在不同位置判断条件是否统一 |

## 2. 状态机事实陈述

### 2.1 状态值（`_NopJobCoreConstants.java`）

| Schedule | 值 | Fire | 值 | Task | 值 |
|----------|---|------|---|------|---|
| DISABLED | 0 | WAITING | 0 | WAITING | 0 |
| ENABLED | 10 | DISPATCHING | 10 | CLAIMED | 10 |
| PAUSED | 20 | RUNNING | 20 | **SUSPICIOUS** | **15** |
| COMPLETED | 30 | SUCCESS | 30 | RUNNING | 20 |
| ARCHIVED | 40 | FAILED | 40 | SUCCESS | 30 |
| | | TIMEOUT | 50 | FAILED | 40 |
| | | CANCELED | 60 | TIMEOUT | 50 |
| | | | | CANCELED | 60 |

注意：Task 比 Fire 多一个 **SUSPICIOUS(15)** 中间态；Fire 没有 SUSPICIOUS。这个不对称是后续多处不一致的根源。

### 2.2 完整状态转换图

（详见 explore 事实清单 §7，此处仅列要点）

- **Fire**：WAITING→DISPATCHING→RUNNING→{SUCCESS/FAILED/TIMEOUT/CANCELED}；DISPATCHING 可 revert 回 WAITING（backoff）；FAILED/TIMEOUT 可 recovery 回 WAITING。
- **Task**：WAITING→CLAIMED→RUNNING→{SUCCESS/FAILED/TIMEOUT/CANCELED}；RUNNING→SUSPICIOUS→TIMEOUT；各终态可 recovery 回 WAITING。
- **Schedule**：DISABLED↔ENABLED↔PAUSED；ENABLED→COMPLETED→ARCHIVED。scheduleStatus 仅在 `NopJobScheduleBizModel` 与 `JobCompletionProcessorImpl.completeSingleFire`（`:210`）修改。

## 3. 状态判断一致性问题

### 3.1 [P1] SUSPICIOUS(15) 语义在 6 处定义且不严格一致

这是状态判断不一致的**最高风险点**。同一个 SUSPICIOUS 状态在不同流程被归入不同语义类别：

| 位置 | SUSPICIOUS 归类 | 锚点 |
|------|----------------|------|
| `JobStatusHelper.isFinishedTask` | **finished**（NOT WAITING/CLAIMED/RUNNING） | `JobStatusHelper.java:51-57` |
| `JobScheduleStoreImpl.isTaskFailed` | **failed**（含 CANCELED/FAILED/TIMEOUT/SUSPICIOUS） | `JobScheduleStoreImpl.java:413-419` |
| `JobCompletionProcessorImpl.resolveFinalFireStatus` | **非 pending，独立标记 hasSuspiciousTask → 最终算 TIMEOUT** | `JobCompletionProcessorImpl.java:326-329, 343-345` |
| `NopJobCoreConstants.RESERVED_TASK_STATUSES` | **仍占资源**（与 WAITING/CLAIMED/RUNNING 并列） | `NopJobCoreConstants.java:40-44` |
| `JobTimeoutCheckerImpl.tryMarkTimeout` | **不处理**（仅 RUNNING；SUSPICIOUS 走单独的 markSuspiciousAsTimeout） | `JobTimeoutCheckerImpl.java:438-440` |
| `JobWorkerScannerImpl.handleExecutionResult` | **终态**（TIMEOUT/CANCELED/SUSPICIOUS → 不覆盖） | `JobWorkerScannerImpl.java:285-290` |

**后果**：
- `isFinishedTask(SUSPICIOUS)==true`，意味着 cancel 流程（`JobFireStoreImpl.cancelFire` `:163`、`JobScheduleStoreImpl.cancelTasks` `:456`）会跳过 SUSPICIOUS task（不取消它），但 `isTaskFailed(SUSPICIOUS)==true` 意味着 recovery 流程（`resetFailedTasks`）会把它重置回 WAITING。两者对 SUSPICIOUS 的"可重置性"判断相反——cancel 认为已完成不碰，recovery 认为已失败可重置。
- `RESERVED_TASK_STATUSES` 把 SUSPICIOUS 当作占资源（worker 容量计算），但 `isFinishedTask` 把它当已完成（不取消）。容量计了，取消却跳过，语义割裂。
- `resolveFinalFireStatus` 把含 SUSPICIOUS task 的 fire 算成 TIMEOUT，但 SUSPICIOUS task 实际可能还在 worker 上跑（只是 worker 暂时失联），把它当 TIMEOUT 并触发 alarm 可能误报。

**严重程度**：P1。当前不会直接导致数据损坏（因为 SUSPICIOUS 通常很快被 `markSuspiciousAsTimeout` 推进到 TIMEOUT），但语义割裂使代码难以推理，未来改动极易引入 bug。

**建议**：在 `JobStatusHelper` 统一定义 task 状态分类函数：`isActiveTask`（WAITING/CLAIMED/RUNNING/SUSPICIOUS，即仍可能推进）、`isTerminalTask`（SUCCESS/FAILED/TIMEOUT/CANCELED，即不可推进）、`isCancellableTask`（非终态且可被外部取消）、`isRecoverableTask`（终态中可被 recovery 重置的）。所有位置改为引用这些函数，消除散落的内联判断。

### 3.2 [P2] "是否活跃 fire" 的双数据源判断

两处判断"schedule 当前是否有活跃 fire"，数据源不同：

| 位置 | 判断方式 | 锚点 |
|------|---------|------|
| `JobPlannerScannerImpl.shouldDiscard/shouldOverlay/shouldRecovery` | 读 `schedule.getActiveFireCount()` **字段** + blockStrategy | `JobPlannerScannerImpl.java:245-261` |
| `JobScheduleStoreImpl.isDiscard/isOverlay` | 仅查 blockStrategy 字段（不查 activeFireCount）；但 `insertManualFire`/`overlayFireAndAdvanceSchedule` 调 `findActiveFires(scheduleId)`（SQL 查 WAITING/DISPATCHING/RUNNING）做实时判断 | `JobScheduleStoreImpl.java:371-379, 120, 248-252` |

**并发场景**：schedule.activeFireCount 字段与实际活跃 fire 数存在更新窗口。例如 `insertFireAndAdvanceSchedule` 在 REQUIRES_NEW 事务内 `activeFireCount++` 并提交，但若该事务刚提交、planner 的 fetchDueSchedules 用的是更新前的快照读，planner 看到的 activeFireCount=0 而 DB 实际已有 1 个活跃 fire → shouldXxx 判断错误（本该 DISCARD/OVERLAY 却走了 insertFireAndAdvanceSchedule，多产生一个 fire）。

**后果**：P2。多产生的 fire 不违反状态机（PARALLEL 本就允许多 fire；DISCARD/OVERLAY 在 blockStrategy 层面兜底），但会导致短暂违反用户的阻塞策略意图。窗口极短（一个事务提交周期），且 `insertFireAndAdvanceSchedule` 有 `hasWaitingFire` 去重兜底（`JobScheduleStoreImpl.java:92-97`）。

**建议**：planner 的 blockStrategy 决策改为在 REQUIRES_NEW 事务内基于 `findActiveFires` 实时判断（与 ScheduleStore 统一数据源），或在 schedule 实体加 lock 标记后读字段。

### 3.3 [P3] 错误码命名与"状态标记 vs 异常"混用

- `ERR_JOB_FIRE_STATUS_CONFLICT`（`JobCoreErrors.java:51`）实际用于 **schedule** 更新重试失败（`JobScheduleStoreImpl.updateScheduleWithRetry:332`），命名误导（含 "fire"）。
- `ERR_JOB_TIMEOUT/CANCELED/OVERLAID` 既是"写入 errorCode 字段的状态标记"（不抛出），其字符串又被复用做异常 errorCode（`JobCoreErrors.java:27-28` 注释）。双重语义易混淆。

**严重程度**：P3（可读性/维护性）。建议拆分：状态标记用独立常量（如 `JOB_TIMEOUT_MARK`），错误码仅用于抛出场景。

## 4. 并发冲突分析

### 4.1 [P1] completion processor 的 finalize 依赖隐式 flush 版本检查

**现状**：`JobCompletionProcessorImpl.completeSingleFire`（`JobCompletionProcessorImpl.java:144-232`）直接修改 fire/schedule 实体字段，**不调用任何 `tryUpdateWithVersionCheck` / `updateWithRetry`**，注释（`:219-220`）明确："Entities are managed by @SingleSession — dirty fields flushed on @Transactional commit"。这是所有 finalize 路径中**唯一不显式做版本检查**的。

**关键验证**（`EntityPersisterImpl.java:470-520`）：普通 flush 走 `queueUpdate` → 回调 `checkUpdateResult(ret, entity)`：
```java
// EntityPersisterImpl.java:504-519
protected void checkUpdateResult(int count, IOrmEntity entity) {
    if (count > 1) {
        if (entity.orm_disableVersionCheckError()) { entity.orm_readonly(true); }
        else { throw newError(ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS, entity); }
    } else if (count == 0) {
        if (entity.orm_disableVersionCheckError()) { entity.orm_readonly(true); }
        else { throw newError(ERR_ORM_UPDATE_ENTITY_NOT_FOUND, entity); }
    }
}
```
默认 `orm_disableVersionCheckError()==false`，因此 **flush 在版本不匹配（count==0）时抛异常 → @Transactional 事务回滚**。

**结论修正**：completion processor 实际上**是并发安全的**——若 fire 在 fetch 后被 timeout checker 改成 TIMEOUT（版本号变），completion 的 fire flush 会 count==0 抛异常，整个事务回滚，不会用 RUNNING 覆盖 DB 的 TIMEOUT。这不是 P0 数据不一致。

**残留风险（P1）**：
1. **隐式契约**：安全性完全依赖"flush 默认做版本检查 + 抛异常"这一 nop-orm 行为，代码层面看不到任何防御。一旦未来有人误设 `orm_disableVersionCheckError(true)` 或实体去掉 version 字段，会退化成覆盖写入。与 §4.7 的 Bug C fix 一样属于隐式 ORM 契约依赖。
2. **活性风险**：completion 同时改 fire 和 schedule。若 fire flush 成功但 schedule flush 失败（schedule 被 planner 频繁推 nextFireTime 导致版本变），整个事务回滚——fire 的 finalize 也回滚，completion 这条 fire 本轮失败，下轮重试。若 schedule 持续被改，completion 持续失败，直到 timeout checker 把 fire 移出 RUNNING。功能正确但效率低。

**建议**：在 `completeSingleFire` 顶部对 fire 加注释说明"依赖 flush 版本检查 + 事务回滚"；或更稳妥地，把 fire 的 finalize 改为显式 `tryUpdateWithVersionCheck`（与 timeout checker 路径一致），失败则 return null 让上层重试。至少应把 schedule 的计数更新用 `updateWithRetry`（与 ScheduleStore 统一），避免 schedule 版本冲突导致整笔 fire finalize 回滚。

### 4.2 [P0] CLAIMED 任务在 worker 崩溃时无回收路径 —— 多数策略下永久卡死

**现状**：
- `JobTaskStoreImpl.fetchRunningTasks`（`:95-96`）过滤 `taskStatus IN (RUNNING, CLAIMED, SUSPICIOUS)`，即把 CLAIMED 当作"运行中"一并 fetch。
- 但 timeout checker 的回收逻辑都不推进 CLAIMED：
  - `JobTimeoutCheckerImpl.tryMarkSuspiciousIfWorkerGone`（`:242-244`）：**仅 RUNNING** → SUSPICIOUS。
  - `JobTimeoutCheckerImpl.tryMarkTimeout`（`:438-440`）：**仅 RUNNING**；SUSPICIOUS 走单独的 `markSuspiciousAsTimeout`。
  - `JobTimeoutCheckerImpl.scanStaleWaitingTasks` → `resetStaleWaitingTasks`（`JobTaskStoreImpl.java:143-168`）：**仅 WAITING**。
  - `JobTimeoutCheckerImpl.tryMarkDispatchTimeout`（`:343-369`）确实会取消 CLAIMED task（过滤包含 CLAIMED），**但仅对 DISPATCHING fire 生效**，而本场景 fire 已是 RUNNING，此路径不适用。
- 即 fire=RUNNING 时，上述回收路径无一条能推进卡死的 CLAIMED task。

**并发场景**：worker 在 `JobWorkerScannerImpl` 中 `tryLockTasksForExecute`（WAITING→CLAIMED，持久化）后、`executeTask` 内 `updateTask(RUNNING)`（`:252-259`）前 JVM 崩溃。此时 task 持久化为 CLAIMED，worker 已死。

**后果（P0）**：
- 该 task **卡在 CLAIMED**，timeout checker fetch 到但不处理。
- `countInFlightTasks`（`JobTaskStoreImpl.java:117-124`）把 CLAIMED 计入 in-flight，**占用该 worker 的并发配额**。若该 worker 永不重启，配额泄漏；若重启，重启后的 `JobWorkerScannerImpl.fetchWaitingTasks` 只取 WAITING，**不会重新认领自己遗留的 CLAIMED task**。
- 整个 fire 因含此 task，`resolveFinalFireStatus` 永远 `hasPendingTask`（CLAIMED ∈ pending，`JobCompletionProcessorImpl.java:320-325`），fire 永不 finalize。

**边界条件（按 blockStrategy 区分，v3 补充）**：

| blockStrategy | P0 是否成立 | 原因 |
|---------------|------------|------|
| **DISCARD** | ✅ **成立（最严重）** | `shouldDiscard`（activeFireCount>0）→ `advanceScheduleAfterSkip`，永不建新 fire，**schedule 整体停摆** |
| **PARALLEL** | ✅ **成立** | `shouldParallel` → `insertFireAndAdvanceSchedule`（建新 fire 但不 cancel 旧 fire）；stuck task/fire 永留，activeFireCount 永久 +1 |
| **RECOVERY** | ✅ **成立** | `recoveryFireAndAdvanceSchedule` 只处理 FAILED/TIMEOUT fire（`:384-385`）；stuck fire 是 RUNNING，recovery 不碰它 |
| **OVERLAY** | ❌ **不成立（自动恢复）** | 下一轮 planning（activeFireCount>0 → shouldOverlay）→ `overlayFireAndAdvanceSchedule` 调 `cancelFire`（私有 `:421` 取消 RUNNING fire）+ `cancelTasks`（`:444-463`，因 `isFinishedTask(CLAIMED)==false` 会取消 stuck CLAIMED task）→ task 变 CANCELED → completion processor 可 finalize fire |
| 手动 cancelFire API | ❌ 全策略可恢复 | `NopJobFireBizModel.cancelFire` → `JobFireStoreImpl.cancelFire`（`:163-186` 取消 CLAIMED task） |

**严重程度**：P0（活性缺陷 + 资源泄漏）。DISCARD/PARALLEL/RECOVERY 三种策略下无自动恢复，永久卡死；OVERLAY 可经下一轮间接恢复。

**建议**：
- 方案 A（推荐）：在 `JobTimeoutCheckerImpl` 增加 CLAIMED 回收——`tryMarkTimeout`/`tryMarkSuspiciousIfWorkerGone` 把 CLAIMED 也纳入处理（CLAIMED 本质就是"已认领未执行"，worker 死后应直接 TIMEOUT 或转 SUSPICIOUS）。这能覆盖 DISCARD/PARALLEL/RECOVERY 三种策略。
- 方案 B：扩展 `resetStaleWaitingTasks` 为 `resetStaleTasks`，对超期的 CLAIMED task 重置 workerInstanceId 并转回 WAITING（或直接 TIMEOUT）。需在 ORM 模型为 task 增加 claimTime 字段以判断超期（当前 CLAIMED 复用 startTime，但 startTime 在 RUNNING 才设）。
- OVERLAY 已有间接回收，无需额外处理（但仍建议方案 A 统一覆盖，避免依赖"恰好下一轮 overlay"）。
- 配套：在 `JobStatusHelper` 明确 CLAIMED 属于"活跃可回收"状态。

### 4.3 [P1] overlay/insertManualFire 强制 activeFireCount=1，cancel 失败时计数偏低

**现状**：`JobScheduleStoreImpl.overlayFireAndAdvanceSchedule`（`:143`）和 `insertManualFire`（`:286`）在 overlay 分支把 `schedule.activeFireCount` **强制设为 1**（假定旧活跃 fire 全部 cancel 成功）：
```java
// JobScheduleStoreImpl.java:138-143 (overlayFireAndAdvanceSchedule)
schedule.setActiveFireCount(1);  // 强制 1，而非 activeFireCount - cancelledCount + 1
```

**并发场景**：overlay 时遍历 `findActiveFires` 对每个调 `cancelFire`（私有，`:421-442`），`cancelFire` 用 `tryUpdateManyWithVersionCheck`，冲突 → warn return false。若某个 activeFire 被并发路径（如 completion 正在 finalize 它、或 timeout 在改它）改了版本，cancelFire 失败，该 fire 实际仍活跃（RUNNING），但 schedule.activeFireCount 已被强制设为 1（新建 fire）。

**后果（P1）**：schedule.activeFireCount 比真实活跃 fire 数**偏低**。后续 planner 的 `shouldDiscard/shouldOverlay/shouldRecovery`（读 activeFireCount 字段）会误判为"无活跃 fire"，可能再次 insert fire，导致实际活跃 fire 数 > 字段值，计数持续累积偏差。虽然 fire 状态机本身不损坏（每个 fire 独立 finalize），但 schedule 计数失真会影响阻塞策略决策和监控指标。

**建议**：把 `activeFireCount = 1` 改为基于实际 cancel 成功数：`activeFireCount = activeFireCount - actualCancelledCount + 1`。`insertManualFire` 已有类似统计（`:267-275` 重新加载统计已 CANCELED 数），可复用该模式。

### 4.4 [P2] handleExecutionResult 重试时不重新加载 fire（代码观察，实际风险低）

**现状**：`JobWorkerScannerImpl.handleExecutionResult`（`:281-383`）：
- 首次在 `:296-306` 加载 fire 并检查是否终态（CANCELED/TIMEOUT/FAILED/SUCCESS）→ 终态则 return 不覆盖。
- `:333` `updateTask(task)`。
- 若失败（`:334-370`）：reload freshTask，**再次检查 task 状态**（TIMEOUT/CANCELED/SUSPICIOUS 跳过，`:335-342`），重设字段，再次 updateTask。重试分支 reload 了 task 但**没有 reload fire**。

**关键事实约束**（修正初版推演）：
1. worker 执行 task 时 fire 必为 **RUNNING**（经 `insertTasksAndMarkFireDispatching` `:109` 从 DISPATCHING 转入）。timeout checker 的 `tryMarkDispatchTimeout` 仅处理 **DISPATCHING** fire（`fetchDispatchingFires` `:234-237` 过滤 DISPATCHING），**无法将 RUNNING fire 改为终态**。RUNNING fire 的终态由 completion processor 基于 task 状态决定。因此"retry 时 fire 被并发改成 TIMEOUT"的场景不成立。
2. `taskStore.updateTask`（`JobTaskStoreImpl.java:49-51`）走 `tryUpdateManyWithVersionCheck`，仅校验 **task 自身版本**，与 fire 版本无关。fire 版本变化不会导致 task update 失败。因此"首次 updateTask 失败因 fire 版本变"的推演也不成立。

**实际可发生的 fire/task 终态不一致**（通过 overlay 路径，与 retry 分支无关）：
1. worker `updateTask(task→SUCCESS)` 成功。
2. 并发的 overlay `cancelFire` 已将 fire 改为 CANCELED，随后 `cancelTasks` 尝试改该 task → task 版本冲突（worker 已改）→ task 留 SUCCESS。
3. 结果：fire=CANCELED, task=SUCCESS。但这发生在**首次 updateTask 成功**时，与 retry 分支无关。

**后果（P2）**：retry 分支的代码观察（reload task 不 reload fire）成立，但实际风险远低于初版描述。fire/task 终态不一致仅在 overlay 并发取消时可能出现，且两者都已终态不会卡死，仅监控/审计语义困惑。

**建议**：retry 分支为防御性可补 reload fire，但收益有限。更高优先级是消除 overlay 并发取消导致的 fire/task 终态不一致（worker finalize task 前校验 fire 仍 RUNNING）。

### 4.5 [P1] completeFireAndUpdateSchedule 的 schedule 失败被静默吞掉

**现状**：`JobFireStoreImpl.completeFireAndUpdateSchedule`（`:121-140`）：
- fire `tryUpdateWithVersionCheck` 失败 → return false（`:127-129`）。
- fire 成功但 schedule `tryUpdateWithVersionCheck` 失败 → **warn log，return true**（`:136-138`）。

**并发场景**：timeout checker 调此方法 finalize fire=TIMEOUT。fire 更新成功（TIMEOUT 已落库），但 schedule 被并发 planner 改了 nextFireTime（版本变），schedule 更新失败。方法 return true，但 caller `JobTimeoutCheckerImpl.tryMarkDispatchTimeout`（`:333`）**忽略返回值**，靠 `fire.orm_readonly()` 判断（fire 成功 → readonly=false → 继续 cancel tasks）。结果：fire=TIMEOUT 已落库，但 schedule 的 activeFireCount 未减、totalFireCount/failFireCount 未增。

**后果（P1）**：schedule 计数长期累积偏差（少算一次）。fire 状态正确（TIMEOUT），task 也被正确 cancel，但 schedule 的统计/监控数据失真，且 activeFireCount 偏高会让 planner 误判有活跃 fire（影响 blockStrategy）。

**建议**：schedule 更新失败时应重试（用 `updateWithRetry`）或至少让 caller 知道 schedule 未更新（return 区分 fire/schedule 各自结果），由 caller 决定是否补登记。当前 `completeFireAndUpdateSchedule` 的 fire/schedule 不对称处理（fire 严格、schedule 宽松）应统一。

### 4.5b [P1] cancelFire(JobFireStoreImpl) 同样吞掉 schedule 更新失败 — 与 §4.5 同一 anti-pattern

**现状**：`JobFireStoreImpl.cancelFire`（`:201-203`）与 §4.5 完全相同的模式：
```java
// JobFireStoreImpl.java:201-203
if (!scheduleDao().tryUpdateWithVersionCheck(schedule)) {
    LOG.warn("nop.job.cancel.schedule-update-conflict:fireId={}", fire.getJobFireId());
}
return true;  // schedule 失败仍返回 true
```
caller `NopJobFireBizModel.cancelFire`（`:78`）据返回值判断成功。

**后果（P1）**：与 §4.5 叠加——schedule 被并发改版本时，`activeFireCount` 少减 1、`totalFireCount/failFireCount` 少增 1，但 caller 以为取消成功。这是 schedule 计数偏差的**第二个来源**（第一个是 §4.5 completeFire，第三个是 §4.3 overlay 强赋值）。三条路径叠加会让 schedule 计数长期失真，影响 planner 的 blockStrategy 决策（读 activeFireCount 字段）和监控指标。

**建议**：与 §4.5 统一处理——schedule 更新纳入 `updateWithRetry` 或返回复合结果。

### 4.6 [P2] insertTasksAndMarkFireDispatching：状态前置检查失败静默 return，但版本失败抛异常

**现状**：`JobFireStoreImpl.insertTasksAndMarkFireDispatching`（`:101-119`）：
- `:105-107` 重载 currentFire，若 `fireStatus != DISPATCHING` → **静默 return**（不抛异常、不插 task）。
- `:110-114` `tryUpdateWithVersionCheck` 失败 → **抛 `ERR_JOB_FIRE_STATUS_CONFLICT`**。

两种"无法继续"路径行为不一致。caller `JobDispatcherScannerImpl.scanBatch`（`:181-182`）：
```java
fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
dispatchedCount++;  // 静默 return 场景下仍 +1
```

**后果（P2）**：静默 return 时 dispatcher 以为成功（dispatchedCount++），metrics 的 dispatched 计数偏高。不影响状态机（fire 仍 DISPATCHING，下轮会被 timeout 回收或重新处理），但监控不准。

**建议**：`insertTasksAndMarkFireDispatching` 状态前置检查失败时返回 boolean（或抛异常），caller 据此决定 dispatchedCount。统一"无法继续"的信号机制。

### 4.7 [P2] Bug C fix 依赖 orm_readonly() 这一隐式 ORM 契约

**现状**：`JobTimeoutCheckerImpl.tryMarkDispatchTimeout`（`:333-340`）：
```java
fireStore.completeFireAndUpdateSchedule(fire, schedule);
if (fire.orm_readonly()) { return; }  // 靠 readonly 判断 fire 是否真更新成功
```
依赖 `tryUpdateWithVersionCheck` 失败时 `EntityPersisterImpl.checkUpdateResult`（`:513-515`）设 `orm_readonly(true)`。

**验证**：`checkUpdateResult` 确实在 `orm_disableVersionCheckError()==true` 且 count==0 时设 readonly（`EntityPersisterImpl.java:513-515`）。`tryUpdateWithVersionCheck` 会先 `orm_disableVersionCheckError(true)`。所以契约成立——**但这是 nop-orm 内部实现细节，nop-job 层无接口契约保证**。

**后果（P2）**：若 nop-orm 未来调整 readonly 设置时机或 tryUpdateWithVersionCheck 实现，此 check 静默失效（不再 skip task cancel），可能出现 "RUNNING fire + CANCELED tasks" 不一致（正是 Bug C 原本要防的）。

**建议**：`completeFireAndUpdateSchedule` 直接 return boolean（已有），caller 用返回值而非 `orm_readonly()` 判断。移除对 ORM 内部状态的依赖。

### 4.8 [P3] recovery 未清 fire.startTime（防御性，实际触发路径不存在）

**现状**：`JobScheduleStoreImpl.recoveryFireAndAdvanceSchedule`（`:212-218`）重置 failed→WAITING 的 fire 时，设了 fireStatus/清错误字段，但**未清 startTime**。

**触发路径分析**：recovery 仅处理 FAILED/TIMEOUT fire（`findFailedFires` `:384-385`）。要让 fetchWaitingFires（`startTime IS NULL OR startTime <= now`）误过滤，需 startTime 是未来时刻——即 fire 曾被 dispatcher revert 设 startTime=backoff-until（未来）。但从 WAITING(backoff) 到 FAILED/TIMEOUT **没有合法状态转换**：WAITING 必须先经 DISPATCHING→RUNNING 才可能到 FAILED/TIMEOUT，而 `tryLockFiresForDispatch`（`:96`）在 DISPATCHING 时会**重设 startTime=now（过去）**。因此到达 FAILED/TIMEOUT 时 startTime 必为过去的 dispatch 时刻，recovery 不清也无影响。

**后果（P3）**：当前状态机下实际不可能触发。仅作为防御性建议保留——未来若新增 WAITING→FAILED 直达路径，此隐患会浮现。

**建议**：recovery 重置 fire 时显式 `fire.setStartTime(null)`（防御性，零成本）。

### 4.9 [P2] fire.startTime 字段语义复用（三义）

`startTime` 在三种情况语义不同：
| 场景 | startTime 语义 | 设置点 |
|------|---------------|--------|
| 首次 WAITING（未 dispatch） | null | 实体初始未设 |
| DISPATCHING/RUNNING | 真正派发开始时间 | `tryLockFiresForDispatch:96` |
| WAITING（backoff 后） | 最早的 re-dispatch 截止时间 | `revertDispatchingFireToWaiting:253` |

fetchWaitingFires 的 `startTime IS NULL OR startTime <= now` 同时容纳"首次"和"backoff 后到期"，但与 §4.8 叠加会产生误过滤。

**建议**：引入独立 backoffUntil 字段，不复用 startTime。

### 4.10 [P3] tryUpdateWithVersionCheck 失败处理方式高度不统一

汇总（见事实清单 §5.1）：抛异常 / return false / 静默 warn / reload+retry 一次，四种方式散落 20+ 处，无统一约定。

**严重程度**：P3（可维护性）。建议确立约定：状态转换类失败（违背状态机）抛业务异常；并发冲突类失败（版本不匹配）return boolean 由 caller 决策；批量场景统计成功子集。

## 5. 严重程度分级总表

| ID | 问题 | 维度 | 严重 | 自动恢复 |
|----|------|------|------|---------|
| 4.2 | CLAIMED 任务无回收路径 | 活性/完整性 | **P0** | ❌ 永久卡死 |
| 3.1 | SUSPICIOUS 语义 6 处不一致 | 一致性 | P1 | — |
| 4.1 | completion 依赖隐式 flush 版本检查 | 并发安全(隐式)/活性 | P1 | 部分(重试) |
| 4.3 | overlay/manual 强制 activeFireCount=1 | 数据一致性 | P1 | ❌ 累积偏差 |
| 4.5 | completeFire schedule 失败静默 | 数据一致性 | P1 | ❌ 计数偏差 |
| 4.5b | cancelFire(JobFireStoreImpl) schedule 失败静默 | 数据一致性 | P1 | ❌ 计数偏差 |
| 4.4 | handleExecutionResult retry 不 reload fire | 代码质量 | P2 | overlay 并发下终态不一致 |
| 3.2 | activeFireCount 字段 vs 实时查询双源 | 并发安全 | P2 | 短窗口 |
| 4.6 | insertTasks 静默return vs 抛异常 | 一致性 | P2 | — |
| 4.7 | Bug C fix 依赖 orm_readonly 隐式契约 | 并发安全(隐式) | P2 | — |
| 4.9 | startTime 三义复用 | 可维护性 | P2 | — |
| 4.8 | recovery 未清 startTime（触发路径不存在） | 防御性 | P3 | 当前不可能触发 |
| 3.3 | 错误码命名/状态标记混用 | 可维护性 | P3 | — |
| 4.10 | 版本失败处理不统一 | 可维护性 | P3 | — |

> **修订说明（v2）**：经独立审查复核，§4.4 由 P1 降为 P2（初版并发推演有两个事实错误：timeout checker 无法改 RUNNING fire、task update 不检查 fire 版本）；§4.8 由 P2 降为 P3（触发路径在当前状态机下不存在）；新增 §4.5b（审查发现 cancelFire 同样吞掉 schedule 失败，是计数偏差第三来源）。

## 6. 整体架构评估

**健全的部分**：
- 乐观锁 + 预留信号（nextFireTime/startTime 推未来）模式整体有效，fetch→tryLock 两步法的并发竞争被版本检查正确消解。
- per-item 事务隔离（`AbstractBatchScanner` + 各 Scanner 的 for-each try/catch）使单条失败不中断整批。
- 终态保护在多数路径存在（cancelFire/completeFire 的 isTerminal/isCancelable 检查）。
- completion processor 虽不显式版本检查，但 flush 默认版本检查 + 事务回滚提供了正确性兜底（§4.1 验证）。

**结构性弱点**：
- Task 状态机比 Fire 多一个 SUSPICIOUS，但缺少对应的统一判断函数，导致语义散落（§3.1）。
- CLAIMED 是状态机的"孤儿状态"——有进边（WAITING→CLAIMED）但缺出边回收（§4.2）。
- schedule 计数字段（activeFireCount 等）作为"派生数据"在多处被强制赋值而非增量计算，与并发 cancel 失败叠加产生偏差（§4.3、4.5）。
- 多处依赖 nop-orm 隐式行为（flush 版本检查、orm_readonly）而非显式契约（§4.1、4.7）。

## 7. Conclusion

- **1 个 P0**：CLAIMED 任务无回收路径（§4.2），worker 崩溃后永久卡死 + 配额泄漏，需优先修复。经独立审查复核确认无误。
- **5 个 P1**：SUSPICIOUS 语义不一致（§3.1）、completion 隐式契约（§4.1）、overlay 计数偏差（§4.3）、completeFire schedule 静默失败（§4.5）、cancelFire schedule 静默失败（§4.5b，审查补充）。后三者（§4.3/4.5/4.5b）共同构成 schedule 计数偏差的三个来源，应作为一组修复。
- **5 个 P2**：handleExecutionResult retry 不 reload fire（§4.4，审查降级）、activeFireCount 双源（§3.2）、insertTasks 行为不一致（§4.6）、Bug C 隐式契约（§4.7）、startTime 三义（§4.9）。
- **3 个 P3**：recovery 未清 startTime（§4.8，当前不可能触发）、错误码命名（§3.3）、版本失败处理不统一（§4.10）。
- 整体并发模型健全，问题集中在 Task 状态机的 SUSPICIOUS/CLAIMED 处理、schedule 计数的强赋值/静默失败模式、以及对 ORM 隐式行为的依赖。
- 后续工作：P0 修复应进入 `ai-dev/plans/`；P1 中 §3.1（SUSPICIOUS 统一）和 §4.3/4.5/4.5b（schedule 计数三源统一）应作为重构计划。

## 8. Open Questions

- [ ] CLAIMED 窗口的实际长度：`tryLockTasksForExecute`（CLAIMED 落库）到 `updateTask(RUNNING)` 之间是同步代码，窗口很短但非零。需评估 worker 在此窗口崩溃的概率，以确定 P0 修复的紧迫性。
- [ ] OVERLAY 的间接回收（下一轮 cancelTasks）是否足以替代显式 CLAIMED 回收？还是仍需为 DISCARD/PARALLEL/RECOVERY 增加独立回收路径？（§4.2 边界条件）
- [ ] completion processor 的 schedule 更新失败导致整笔 fire finalize 回滚（§4.1 活性风险），在高并发调度下的实际重试频率如何？是否需要把 schedule 计数从 fire finalize 中解耦？
- [ ] 是否存在外部对 task/fire 的直接 DB 修改（绕过 Store 层）？若有，所有版本检查假设都不成立。
- [ ] nop-orm 的 `orm_disableVersionCheckError` 默认值是否可能被全局配置翻转？若翻转，§4.1 和 §4.7 的安全性假设全部失效。

## 9. References

- `nop-job/nop-job-core/src/main/java/io/nop/job/core/_NopJobCoreConstants.java`（状态常量）
- `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:504`（checkUpdateResult，flush 版本检查）
- `docs-for-ai/02-core-guides/concurrency-and-transactions.md`（乐观锁 + 预留信号模式、@SingleSession 陷阱）
- `nop-job/nop-job-dao/src/test/java/io/nop/job/dao/store/TestJobFireStoreRace.java`（并发竞态回归测试）
- 事实清单全量锚点见配套 explore 事实清单（本次分析的事实基础）
