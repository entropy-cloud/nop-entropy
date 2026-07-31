# 322 nop-job 状态一致性与并发修复

> Plan Status: completed
> Last Reviewed: 2026-07-31
> Source: `ai-dev/analysis/2026-07/2026-07-31-nop-job-state-consistency-and-concurrency-analysis.md`（v3，经两轮独立审查共识通过）
> Related: `ai-dev/plans/302-nop-job-completion-metrics-and-per-fire-transaction.md`（已完成，引入 per-fire 事务）

## Purpose

修复分析报告确认的 1 个 P0 活性缺陷（CLAIMED 任务无回收路径）和 P1 数据一致性缺陷中的可修复子集（schedule 计数偏差：overlay 强赋值 + completeFire/cancelFire schedule 静默失败），并引入 task 状态判断函数集中化（无行为变更的重构）。SUSPICIOUS 取消语义变更、completion 隐式契约等需要更深 design 的项经裁定移入 Deferred。

## Current Baseline

（基于分析报告 v3 + 计划审查复核，所有锚点已经源码核对）

- **CLAIMED 是孤儿状态**：`JobTaskStoreImpl.fetchRunningTasks`（`:95-96`）fetch 到 CLAIMED，但 `JobTimeoutCheckerImpl` 的回收路径不推进它——`tryMarkSuspiciousIfWorkerGone`（`:242-244`）仅处理 RUNNING，`tryMarkTimeout`（`:433-440`）先处理 SUSPICIOUS（经 `markSuspiciousAsTimeout`）再处理 RUNNING，`resetStaleWaitingTasks` 仅 WAITING。worker 在 CLAIMED→RUNNING 窗口崩溃后，DISCARD/PARALLEL/RECOVERY 策略下 task 永久卡死 + 配额泄漏（`countInFlightTasks:120-121` 计入 CLAIMED）。OVERLAY 可经下一轮 `cancelTasks` 间接恢复。
- **CLAIMED 回收方案可行**：CLAIMED task 的 `workerInstanceId` 已在 `tryLockTasksForExecute`（`JobTaskStoreImpl.java:86`）设置，`resolveAliveWorkerIds`（`JobTimeoutCheckerImpl:218-239`）可复用。放宽 `tryMarkSuspiciousIfWorkerGone` 的状态过滤即可。`markSuspiciousAsTimeout`（`:396-424`）处理 SUSPICIOUS 时不关心前驱状态，null-safe（CLAIMED 的 startTime 为 null → durationMs=0）。回收链 CLAIMED→SUSPICIOUS→TIMEOUT 跨两轮 scanOnce（`scanTaskTimeouts:207-210` 状态变化后 continue）。
- **schedule 计数偏差三源**：①`overlayFireAndAdvanceSchedule:143`/`insertManualFire:286` 强制 `activeFireCount=1`——私有 `cancelFire`（`:421-442`）已返回 boolean 表示是否真 cancel 成功，可直接累加 actualCancelledCount；②`JobFireStoreImpl.completeFireAndUpdateSchedule:136-138` schedule 版本失败仅 warn return true；③`JobFireStoreImpl.cancelFire:201-203` 同样 warn 吞掉。
- **@SingleSession 约束排除了 updateWithRetry 方案**（审查复核确认）：`completeFireAndUpdateSchedule`、`cancelFire` 在 `@SingleSession` caller（`tryMarkDispatchTimeout`）下被调用，传入的 schedule 实体属于 caller 的 session 缓存。`updateWithRetry` 的 unload+lazy load 机制虽能绕过 requireEntityById 缓存命中，但这些方法的 schedule 字段修改在 caller 侧施加（非 fieldSetter），updateWithRetry 的 fieldSetter 模型不适用。代码注释（`JobFireStoreImpl.java:131-135`）已明确记录此约束。
- **Bug C fix 依赖 orm_readonly**（§4.7）：`tryMarkDispatchTimeout:333-340` 忽略 `completeFireAndUpdateSchedule` 返回值，靠 `fire.orm_readonly()` 判断 fire 是否真更新。任何对 completeFire 返回语义的修改必须保持此判断路径有效。
- **SUSPICIOUS 语义在 6 处定义**（报告 §3.1）。其中 cancel 流程（`isFinishedTask(SUSPICIOUS)==true` → cancelTasks 跳过 SUSPICIOUS）是**当前已落地行为**，改变它会导致 SUSPICIOUS task 被直接 CANCELED 而非经 markSuspiciousAsTimeout→TIMEOUT，影响 fire 结果优先级链（`resolveFinalFireStatus:347-355`）和 `TestJobCompletionProcessor` 多处断言。
- **已落地基础设施**：`IOrmEntityDao.updateWithRetry`（unload+lazy load，@SingleSession 下有效）已在 `JobScheduleStoreImpl.updateScheduleWithRetry` 使用；`concurrency-and-transactions.md` 已文档化乐观锁模式。

## Goals

- worker 在任何 blockStrategy 下、CLAIMED→RUNNING 窗口崩溃后，task 有自动回收路径（经 worker 失联判断→SUSPICIOUS→TIMEOUT），不永久卡死。
- schedule 的 activeFireCount 在 overlay/manual 并发 cancel 场景下基于实际成功数计算，不强制赋值。
- completeFire/cancelFire 的 schedule 更新失败从静默 warn 变为 caller 可感知，不再无声丢失。
- task 状态判断收敛到 `JobStatusHelper` 的统一分类函数（语义与现状一致，无行为变更）。

## Non-Goals

- **不改 SUSPICIOUS 的 cancel 语义**：`isFinishedTask(SUSPICIOUS)==true`（cancel 跳过）保持不变。改变它会引入 fire 结果优先级链变更和测试回归，超出"一致性修复"范畴。
- **不用 updateWithRetry 修复 @SingleSession 下的 completeFire/cancelFire schedule 更新**：上述约束确认不可行（fieldSetter 模型不适用 caller 侧字段修改）。
- **不修改 ORM 模型结构**（不新增 task.claimTime 字段）——CLAIMED 回收采用 worker 失联判断方案。
- 不改 completion processor 的 fire finalize 方式（§4.1，移入 Deferred——隐式契约，功能正确，需独立 design）。
- 不改 Bug C fix 的 orm_readonly 依赖（§4.7，需 nop-orm 契约配合）。
- 不改 fire.startTime 三义复用（§4.9）、错误码命名（§3.3）、版本失败处理风格统一（§4.10）、recovery 未清 startTime（§4.8）。
- 不重构 RESERVED_TASK_STATUSES（它是 SQL 绑定的 List 常量，非判断函数，不在状态判断统一的范围）。

## Scope

### In Scope

- P0 §4.2：CLAIMED 任务回收路径（放宽 tryMarkSuspiciousIfWorkerGone）
- P1 §4.3：overlay/manual 的 activeFireCount 基于实际 cancel 数
- P1 §4.5 / §4.5b：completeFire/cancelFire 的 schedule 失败显式化（caller 可感知）
- P1 §3.1（收窄版）：task 状态判断函数集中化到 JobStatusHelper，语义保持现状

### Out Of Scope

- P1 §4.1 completion 隐式契约（→ Deferred，需 design 决定 schedule 计数与 fire finalize 解耦方案）
- P2 §4.4 / §4.6 / §4.7 / §4.9（→ Deferred）
- P3 §4.8 / §3.3 / §4.10（→ Deferred）
- SUSPICIOUS cancel 语义变更（行为变更风险，不在本计划）

## Execution Plan

### Phase 1 - CLAIMED 任务回收

Status: completed
Targets: `nop-job/nop-job-coordinator/.../JobTimeoutCheckerImpl.java`

- Item Types: `Fix | Proof`

- [x] 放宽 `tryMarkSuspiciousIfWorkerGone`（`:242-244`）的状态过滤：从"仅 RUNNING"扩展为"RUNNING 或 CLAIMED"。worker 不在 alive 集合即转 SUSPICIOUS，后续由现有 `markSuspiciousAsTimeout` 路径（`:396-424`）推进到 TIMEOUT。CLAIMED task 的 `workerInstanceId` 已在 `tryLockTasksForExecute`（`JobTaskStoreImpl.java:86`）设置，namingService 查询可复用。
- [x] 确认 worker alive 时 CLAIMED task 不被误处理（`tryMarkSuspiciousIfWorkerGone` 仅在 worker 不在 alive 集合时改状态，正常处理中的 CLAIMED task 的 worker 仍 alive → 不动）。
- [x] 新增单元测试：复用 `TestJobTimeoutChecker` 现有的 `MockNamingService` + `setAliveInstances` 模式（参考 `testWorkerLiveness_marksSuspiciousThenTimeoutWhenWorkerGone`），构造 CLAIMED task + worker 失联，验证经**两轮 scanOnce** 后到达 SUSPICIOUS→TIMEOUT（第一轮 CLAIMED→SUSPICIOUS 因状态变化被 `:207-210` continue，第二轮 SUSPICIOUS→TIMEOUT）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `tryMarkSuspiciousIfWorkerGone` 的状态过滤条件在源码中可见包含 CLAIMED
- [x] 新增测试覆盖"CLAIMED + worker 失联 → SUSPICIOUS（第1轮）→ TIMEOUT（第2轮）"完整链路，`./mvnw test -pl nop-job/nop-job-coordinator -am` 通过
- [x] **无静默跳过**：worker 仍 alive 时 CLAIMED task 状态不变（显式条件判断）
- [x] **行为语义验证**：现有 `TestJobTimeoutChecker`（26）、`TestJobConcurrency`（6）、`TestJobE2E`（4）全部通过（无回归）
- [x] 文档更新：`No owner-doc update required`——`concurrency-and-transactions.md` 是并发模式指南，不含 nop-job 状态机图；CLAIMED→SUSPICIOUS 回收属 timeout checker 内部行为，已由新增测试 + 代码注释记录，状态机完整图在分析报告
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - schedule 计数显式化

Status: completed
Targets: `nop-job/nop-job-dao/.../JobScheduleStoreImpl.java`, `nop-job/nop-job-dao/.../JobFireStoreImpl.java`, `nop-job/nop-job-coordinator/.../JobTimeoutCheckerImpl.java`, `nop-job/nop-job-service/.../NopJobFireBizModel.java`

- Item Types: `Fix | Proof`

> **设计约束与价值定位**：本 Phase 不使用 `updateWithRetry`（@SingleSession 下 fieldSetter 模型不适用于 caller 侧字段修改，见 Current Baseline）。§4.3 是**实质计数修复**（activeFireCount 不再强制赋值）；§4.5/4.5b 是**可观测性改进**（schedule 失败从 impl 内静默 warn 提升为 caller 可感知——注意：caller 在 @SingleSession 下无法真正补偿/重试，schedule 计数偏差仍可能存在，但不再无声丢失，便于监控发现）。Phase 2 不依赖 Phase 1（CLAIMED 回收）或 Phase 3（状态判断统一）。

- [x] **§4.3 overlay/manual 计数修正**：`overlayFireAndAdvanceSchedule`（`:143`）和 `insertManualFire`（`:286`）的 overlay 分支，把 `setActiveFireCount(1)` 改为基于私有 `cancelFire`（`:421-442`）已返回的实际成功数：`activeFireCount = defaultInt(原值) - actualCancelledCount + 1`。`overlayFireAndAdvanceSchedule` 已在 `:122,126-128` 累加 actualCancelledCount，直接用于 `:143` 即可。`insertManualFire` 的 overlay 分支同理用实际统计值（已有 `:267-275` reload 统计模式可参考，但应优先用 cancelFire 返回值）。
- [x] **§4.5 completeFire schedule 失败显式化**：`JobFireStoreImpl.completeFireAndUpdateSchedule`（`:121-140`）改为返回复合结果（fire 是否更新成功 + schedule 是否更新成功），而非当前的单一 boolean。schedule 失败不再静默 warn return true，而是如实反映在复合结果中。
- [x] **§4.5b cancelFire schedule 失败显式化**：`JobFireStoreImpl.cancelFire`（`:201-203`）同上，返回复合结果。fire 成功但 schedule 失败时 caller 可感知。
- [x] **适配 caller，保持 Bug C fix 有效**：`JobTimeoutCheckerImpl.tryMarkDispatchTimeout`（`:333-340`）适配复合结果后用 `outcome.fireUpdated()` 判断（与原 orm_readonly 等价，因 tryUpdateWithVersionCheck 失败时设 readonly 且 completeFire 内部用同一机制），schedule 失败时显式 WARN。`NopJobFireBizModel.cancelFire`（`:78`）适配复合结果，schedule 失败时告警。
- [x] 新增并发测试：构造 schedule 被并发修改版本的场景，验证 completeFire/cancelFire 的 caller 能感知 schedule 失败（不再静默），fire 仍正确更新。

Exit Criteria:

- [x] 源码中 `overlayFireAndAdvanceSchedule`、`insertManualFire` 的 overlay 分支不再出现 `setActiveFireCount(1)` 硬赋值，改为基于实际 cancel 数
- [x] `completeFireAndUpdateSchedule`、`cancelFire` 返回复合结果（fireUpdated + scheduleUpdated），schedule 失败如实反映
- [x] `tryMarkDispatchTimeout` 适配复合结果后，fire 判断路径（`outcome.fireUpdated()`）保持 Bug C fix 语义（fire 未更新则跳过 task cancel），schedule 失败时有显式 WARN
- [x] **接线验证**：`NopJobFireBizModel.cancelFire` 适配复合结果（`outcome.fireUpdated()` 判断 + schedule 失败 WARN），不再因 schedule 失败被误判为整体成功而不告警
- [x] **接口适配**：`IJobFireStore` 签名变更后，3 个 test mock 类（`TestJobCompletionProcessor`/`TestJobE2E`/`TestJobTimeoutChecker` 中的 mock fireStore）+ `TestJobFireStoreRace`/`TestJobStoreImpl` 的 cancelFire 断言已适配新返回类型
- [x] 新增并发测试覆盖 completeFire 的 schedule 版本冲突场景（`testDispatchTimeout_skipsTaskCancelWhenFireUpdateFails` 验证 Bug C 保护、`testDispatchTimeout_cancelsTasksWhenOnlyScheduleUpdateFails` 验证 schedule 失败仍 cancel task）
- [x] **行为语义验证**：`TestJobFireStoreRace`（11）、`TestJobStoreImpl`（18）、`TestJobTimeoutChecker`（26）、`TestJobE2E`（4）、`TestJobCompletionProcessor`（22）全部通过；Bug C fix 回归通过
- [x] 文档更新：`No owner-doc update required`——`FireScheduleOutcome` 是 nop-job-dao 内部 store 接口变更，`concurrency-and-transactions.md` 讲并发模式（乐观锁/预留信号/@SingleSession）不含 Store 方法签名参考；schedule 失败显式化（静默→WARN）不改变并发模式本身
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - task 状态判断函数集中化（无行为变更）

Status: completed
Targets: `nop-job/nop-job-dao/.../JobStatusHelper.java`, 以及内联 task 状态判断的调用点

- Item Types: `Fix | Proof`

> **设计约束**：本 Phase 是纯重构，分类函数语义必须与现有各处判断**完全等价**，不改变 SUSPICIOUS 的 cancel 行为（`isFinishedTask(SUSPICIOUS)==true` 保持）。Phase 3 不依赖 Phase 1/2，可独立执行。
> **执行时调整**：`tryMarkTimeout` 的 RUNNING 守卫（`:438-440`）过于简单（`!= RUNNING`），替换为分类函数收益低，按计划审查建议**移出替换目标**，保留原样。

- [x] 在 `JobStatusHelper` 新增 task 状态分类函数，语义与现有散落判断逐一等价：`isRecoverableTask`（CANCELED/FAILED/TIMEOUT/SUSPICIOUS，对应原 `isTaskFailed`）、`isConcurrentlyFinalizedTask`（TIMEOUT/CANCELED/SUSPICIOUS，对应原 worker 终态检查）。**SUSPICIOUS 在 `isFinishedTask` 中仍返回 true（保持 cancel 跳过行为），在 `isRecoverableTask` 中也返回 true（recovery 可重置）**——两个视角的设计意图已在 javadoc 说明。
- [x] 替换可安全替换的内联判断：`isTaskFailed`（`JobScheduleStoreImpl:414` → `JobStatusHelper.isRecoverableTask`）、`handleExecutionResult` 终态检查 + 重试分支 + `completeTaskWithFailure`（`JobWorkerScannerImpl:285,334,382` → `JobStatusHelper.isConcurrentlyFinalizedTask`）。**不替换** `RESERVED_TASK_STATUSES`（SQL 常量）、`resolveFinalFireStatus`（业务逻辑）、`isFinishedTask`（保持 SUSPICIOUS 语义）、`tryMarkTimeout` 守卫（收益低）。
- [x] 新增单元测试：对每个新分类函数覆盖全部 8 个 task 状态值的归属验证（`TestJobStatusHelper` 新增 6 个测试）。

Exit Criteria:

- [x] `JobStatusHelper` 源码中可见新增 `isRecoverableTask`、`isConcurrentlyFinalizedTask`，SUSPICIOUS 归属与现有判断等价（无行为变更）
- [x] `isTaskFailed`、`handleExecutionResult` 终态检查/重试/`completeTaskWithFailure` 在源码中已改为引用分类函数
- [x] `RESERVED_TASK_STATUSES`、`resolveFinalFireStatus`、`isFinishedTask`、`tryMarkTimeout` 守卫保持不变（未被替换）
- [x] 新增分类函数单元测试覆盖全部 8 个 task 状态值（`TestJobStatusHelper` 16 tests）
- [x] **行为语义验证（等价性）**：`TestJobCompletionProcessor`（22）、`TestJobTimeoutChecker`（26）、`TestJobFireStoreRace`（11）、`TestBlockStrategies`（6）、`TestJobWorkerScanner` 全部通过（证明无行为变更）
- [x] **无静默跳过**：分类函数对每个状态有明确归属，无 fallback 分支
- [x] 文档更新：`No owner-doc update required`（纯内部重构，状态机外部行为不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] P0 §4.2 CLAIMED 任务在 DISCARD/PARALLEL/RECOVERY 策略下有自动回收路径（新增测试证明 worker 崩溃后 task 可达终态）
- [x] P1 §4.3 overlay/manual 的 activeFireCount 基于实际 cancel 数（源码无硬赋值）
- [x] P1 §4.5/4.5b schedule 失败 caller 可感知（不再静默），Bug C fix 语义保持
- [x] P1 §3.1（收窄版）task 状态判断集中化，无行为变更（全部回归测试通过）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs 已同步或明确裁定无需更新
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 CLAIMED 回收路径在运行时确实被 tryMarkSuspiciousIfWorkerGone 调用（调用链 scanOnce→scanTaskTimeouts→fetchRunningTasks[SQL含CLAIMED]→tryMarkSuspiciousIfWorkerGone[条件含CLAIMED]→SUSPICIOUS→第二轮markSuspiciousAsTimeout→TIMEOUT 完整连通）；schedule 失败显式化在 caller 确实处理（FireScheduleOutcome 被 3 个 caller 实质使用，非空壳）
- [x] `./mvnw clean install -pl nop-job/nop-job-dao,nop-job/nop-job-coordinator,nop-job/nop-job-worker,nop-job/nop-job-service -am -T 1C` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### §4.1 completion processor 隐式契约

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 completion 依赖 flush 默认版本检查（`EntityPersisterImpl.checkUpdateResult` count==0 抛异常）+ 事务回滚，功能正确（分析报告 §4.1 验证）。残留的是活性风险（schedule 版本冲突导致整笔 fire finalize 回滚重试）和隐式契约依赖。修复需要 design 决策（schedule 计数与 fire finalize 解耦的方案，如异步计数/增量SQL），超出本计划"一致性修复"范畴。
- Successor Required: yes（需先出 design 文档确定 schedule 计数解耦方案，再开 successor plan）

### §4.4 handleExecutionResult retry 不 reload fire

- Classification: `watch-only residual`
- Why Not Blocking Closure: 经审查复核，初版推演的并发场景有两个事实错误，实际风险远低于描述。fire/task 终态不一致仅在 overlay 并发取消时可能出现且两者已终态不卡死。
- Successor Required: no

### §4.6 insertTasksAndMarkFireDispatching 静默 return vs 抛异常

- Classification: `optimization candidate`
- Why Not Blocking Closure: 仅影响 dispatcher 的 dispatchedCount metrics 偏高，不影响状态机正确性。
- Successor Required: no

### §4.7 Bug C fix 依赖 orm_readonly 隐式契约

- Classification: `watch-only residual`
- Why Not Blocking Closure: 依赖 nop-orm 内部行为，需 nop-orm 层提供接口契约后统一改造。本计划 Phase 2 已将 Bug C 保护显式化（`tryMarkDispatchTimeout` 现用 `outcome.fireUpdated()` 判断，原 `orm_readonly` 隐式路径已收敛为复合返回值），保护语义保持且更清晰。
- Successor Required: yes（待 nop-orm 契约明确后开 successor）

### §4.8 / §4.9 / §3.3 / §4.10

- Classification: `out-of-scope improvement` / `optimization candidate`
- Why Not Blocking Closure: §4.8 当前状态机下不可能触发；§4.9 重构成本高（需新增字段）；§3.3/§4.10 是可读性优化，不影响正确性。
- Successor Required: no

### SUSPICIOUS cancel 语义统一

- Classification: `optimization candidate`
- Why Not Blocking Closure: 改变 isFinishedTask(SUSPICIOUS) 会引入 fire 结果优先级链变更（CANCELED vs TIMEOUT）和 TestJobCompletionProcessor 回归，属于行为变更而非一致性修复。当前 cancel 跳过 SUSPICIOUS 与 recovery 重置 SUSPICIOUS 的"矛盾"是有意设计（cancel 让 timeout checker 处理 SUSPICIOUS，recovery 兜底重置）。需独立评估是否真的要统一。
- Successor Required: yes（若评估后决定统一，开 successor plan 并预先处理回归）

## Non-Blocking Follow-ups

- 评估 §4.1 schedule 计数与 fire finalize 解耦的方案（异步计数 / 增量 SQL / 独立计数表），出 design 文档
- nop-orm 层为 tryUpdateWithVersionCheck 的 readonly 行为提供显式接口契约后，revisit Bug C fix（§4.7）
- 评估 SUSPICIOUS cancel 语义统一是否真的需要（当前有意设计的矛盾 vs 真正的不一致）
- 评估是否需要为 task 增加 claimTime 字段以支持基于时间的 CLAIMED 超时（当前方案依赖 worker 失联判断）

## Closure

Status Note: 三个 Phase 的实质工作已全部正确落地并通过独立 closure audit 核对。Phase 1 CLAIMED 回收调用链从入口点（scanOnce）到终态（TIMEOUT）完整连通（fetchRunningTasks SQL 含 CLAIMED + tryMarkSuspiciousIfWorkerGone 条件含 CLAIMED + 两轮 scanOnce 推进）；Phase 2 FireScheduleOutcome 非空壳，3 个 caller（JobTimeoutCheckerImpl / NopJobFireBizModel / TestJobFireStoreRace）实质使用返回值做分支/WARN/断言，overlay/manual 的 activeFireCount 基于实际 cancel 数，Bug C 保护保持；Phase 3 分类函数等价重构，SUSPICIOUS 语义未变，未替换项保持原样。Deferred 项分类诚实（§4.1 非 P0 活性缺陷，§4.4 诚实降级）。Anti-Hollow 检查两项均 PASS。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor（fresh session，未参与实现）
- Audit Session: 独立 closure-audit pass（2026-07-31）
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：JobTimeoutCheckerImpl.java:244-245（CLAIMED 过滤）、JobTaskStoreImpl.java:95-96（fetchRunningTasks SQL 含 CLAIMED）、TestJobTimeoutChecker.java（testClaimedTask_reclaimedWhenWorkerGone 两轮链路 + testClaimedTask_notMarkedWhenWorkerAlive 不误处理）
  - Phase 2 Exit Criteria 全部 PASS：JobFireStoreImpl.java:123-141,145-207（复合返回 FireScheduleOutcome）、JobScheduleStoreImpl.java:143,286-287（不再强赋值 1）、JobTimeoutCheckerImpl.java:336-346（outcome.fireUpdated() Bug C 保护 + schedule WARN）、NopJobFireBizModel.java:83-89（适配）、TestJobTimeoutChecker.java（testDispatchTimeout_skipsTaskCancelWhenFireUpdateFails + testDispatchTimeout_cancelsTasksWhenOnlyScheduleUpdateFails）、TestJobFireStoreRace.java（cancelFire 断言适配 .fireUpdated()）
  - Phase 3 Exit Criteria 全部 PASS：JobStatusHelper.java:66,82（isRecoverableTask + isConcurrentlyFinalizedTask）、JobScheduleStoreImpl.java:415（isTaskFailed→isRecoverableTask）、JobWorkerScannerImpl.java:285,335,382（三处→isConcurrentlyFinalizedTask）、RESERVED_TASK_STATUSES/resolveFinalFireStatus/tryMarkTimeout 守卫未被替换、TestJobStatusHelper.java（16 tests 覆盖 8 状态）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - `./mvnw clean install -pl nop-job/nop-job-dao,nop-job/nop-job-coordinator,nop-job/nop-job-worker,nop-job/nop-job-service -am -T 1C` BUILD SUCCESS（含全部测试）
  - Anti-Hollow 检查结果：CLAIMED 回收调用链完整连通（scanOnce→scanTaskTimeouts→fetchRunningTasks[SQL含CLAIMED]→tryMarkSuspiciousIfWorkerGone[条件含CLAIMED]→SUSPICIOUS→continue→第二轮 markSuspiciousAsTimeout→TIMEOUT）；FireScheduleOutcome 被 3 个 caller 实质使用（非空壳）
  - Deferred 项分类检查：§4.1 诚实 defer（optimization candidate，非 P0）；§4.4 诚实降级（说明事实错误）；§4.7 watch-only（措辞已同步为 outcome.fireUpdated()）

Follow-up:

- 评估 §4.1 schedule 计数与 fire finalize 解耦方案（需 design 文档）
- nop-orm 层为 tryUpdateWithVersionCheck 提供显式契约后 revisit Bug C fix（§4.7）
- 评估 SUSPICIOUS cancel 语义统一是否真的需要
- 评估是否需要 task.claimTime 字段以支持基于时间的 CLAIMED 超时
