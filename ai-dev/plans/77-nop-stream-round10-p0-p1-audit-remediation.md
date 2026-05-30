# 77 nop-stream Round 10 P0/P1 审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-30
> Source: ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r10/01-open-findings.md, ai-dev/audits/2026-05-30-deep-audit-nop-stream-full/summary.md
> Related: 74-nop-stream-p0-p1-audit-remediation (completed), 75-nop-stream-round9-and-p2-audit-remediation (completed), 76-nop-stream-remaining-audit-remediation (completed)

## Purpose

将 2026-05-30 对抗性审查 Round 10 中发现的全部 4 个 P0 和 7 个 P1 修复到可验证状态。Round 10 是 3 个并行探索 agent 分聚焦执行引擎、CEP、分布式运行时后的交叉验证结果，去重了 Round 1-9 的所有已有发现。P2/P3 发现将作为后续计划处理。

## Current Baseline

- Plan 76 完成后 `./mvnw test -pl nop-stream -am` 全量通过
- Plans 73-76 覆盖了 Round 1-9 + 2026-05-25/28/30 深度审计的全部发现
- Round 10 发现了 4 个全新的 P0（CheckpointCoordinator 2PC 协议 + TaskManager 信号量）和 7 个全新 P1（Window 算子恢复路径 + Timer + InputGate + Connector 数据丢失），均未被任何已有计划覆盖
- 2026-05-30 全量审计发现了 6 个 P2 + ~25 个 P3，本计划仅修复其中的 P1 级别以上；P2/P3 留作 Non-Blocking Follow-ups

## Goals

- 修复全部 4 个 P0 发现（CheckpointCoordinator 2PC 正确性、TaskManager 信号量生命周期）
- 修复全部 7 个 P1 发现（Window 算子正确性、Timer 服务、InputGate barrier、Connector 数据丢失）
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过
- 新增行为的测试覆盖

## Non-Goals

- P2/P3 发现的修复（将作为 Plan 78 处理）
- 架构级重构（WindowOperator 重叠、core 模块拆分）
- 2026-05-30 全量审计的 6 个 P2 修复

## Scope

### In Scope

- nop-stream-runtime: CheckpointCoordinator 2PC 修复（AR-1, AR-2）、TaskManager 信号量修复（AR-3, AR-4）
- nop-stream-core: WindowAggregationOperator 3 项修复（AR-5, AR-6, AR-7）、HeapInternalTimerService 修复（AR-8）、InputGate barrier 修复（AR-9）
- nop-stream-connector: BatchConsumerSinkFunction 数据丢失修复（AR-10）、DebeziumCdcSourceFunction draining 标志修复（AR-11）

### Out Of Scope

- P2 发现（AR-12~AR-15 + 全量审计 P2）
- P3 发现（AR-16~AR-18 + 全量审计 P3）
- 架构级重构
- nop-stream-fraud-example 修改

## Execution Plan

### Phase 1 - CheckpointCoordinator 2PC P0 修复

Status: completed
Targets: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix`

- [x] AR-1: retryFailedCommits 记录原始 success 值，重试时使用记录值而非硬编码 true。新增字段 `ConcurrentHashMap<Long, Boolean> checkpointSuccessMap`（与 failedCommitParticipants 并行，线程安全），在 `notifyParticipantsFinishCommit` 入口处 `checkpointSuccessMap.put(checkpointId, success)`。retryFailedCommits 中用 `checkpointSuccessMap.getOrDefault(failedEpoch, true)` 替换硬编码 `true`。不修改 failedCommitParticipants 的类型，保持最小变更
- [x] AR-2: shutdown() 在清理 pendingCheckpoints 前，对每个 pending checkpoint 调用 `notifyParticipantsFinishCommit(checkpointId, false)`（通知 2PC participants abort）和 `notifyCheckpointAborted(checkpointId)`（通知 CheckpointListeners abort）。两者都需要调用，顺序为先 participants 后 listeners。调用后再 `pending.dispose()` + `pendingCheckpoints.clear()`

Exit Criteria:

- [x] retryFailedCommits 使用 checkpointSuccessMap 记录的原始 success 值，而非硬编码 true
- [x] shutdown() 对所有 pending checkpoint 先调用 notifyParticipantsFinishCommit(id, false) 再调用 notifyCheckpointAborted(id)
- [x] 新增测试：(a) checkpoint 中止后 retryFailedCommits 仍使用 false；(b) shutdown 时 participant 收到 abort 回调
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - TaskManager 信号量 P0 修复

Status: completed
Targets: `nop-stream-runtime/.../taskmanager/TaskManager.java`

- Item Types: `Fix`

- [x] AR-3: 修复 cancelTask() + RunningTask.run() finally 双重释放。方案：为 RunningTask 新增 `AtomicBoolean semaphoreReleased` 字段，cancelTask() 中 `task.semaphoreReleased.compareAndSet(false, true)` 成功才 release，RunningTask.run() finally 中同样 CAS 后释放。确保每个 task 的信号量恰好释放一次。**行序分析**：run() line 417 `if (canceled) return`（try 前，不进入 finally）时 semaphoreReleased 为 false，cancelTask 的 CAS 成功释放——正确（1 次释放）。run() 已在 try 内时 finally 的 CAS 成功释放——cancelTask 的 CAS 失败——正确（1 次释放）。所有交织均安全
- [x] AR-4: 修复 updateFencingToken() 对未运行 task 的信号量泄漏。在 removeIf 的回调中，cancel() 后 `entry.getValue().semaphoreReleased.compareAndSet(false, true)` 成功则 `capacitySemaphore.release()`。与 AR-3 共用 semaphoreReleased 字段，统一保证恰好一次释放

Exit Criteria:

- [x] cancelTask + RunningTask.run() finally 信号量恰好释放一次（CAS 保护）
- [x] updateFencingToken 对未运行 task 正确释放信号量
- [x] 新增测试：(a) cancelTask + task 完成的竞态下信号量不双重释放；(b) updateFencingToken 后信号量不泄漏；(c) N 次 cancel 后 availablePermits 不超过 capacity
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - WindowAggregationOperator P1 修复

Status: completed
Targets: `nop-stream-core/.../operators/WindowAggregationOperator.java`

- Item Types: `Fix`

- [x] AR-5: 在 processElement 的 late-element 检查中添加 `currentWatermark == Long.MIN_VALUE` 守卫。修改条件为 `if (element.hasTimestamp() && currentWatermark != Long.MIN_VALUE && timestamp < currentWatermark - allowedLateness)`。元素无 timestamp 时 `hasTimestamp()` 已短路返回 false，不受影响
- [x] AR-6: restoreState 末尾从 windowState 中提取所有 active key-window 映射并填充 activeWindowsPerKey。windowState 类型为 `LinkedHashMap<WindowKey<K, W>, ACC>`，WindowKey 有 `key()` 和 `window()` 访问器。遍历 windowState 的所有 entry，按 `entry.getKey().key()` 分组，收集 `entry.getKey().window()` 到 `activeWindowsPerKey` 对应 key 的 Set 中
- [x] AR-7: processWatermark 中非前进 watermark（`if (newWatermark <= currentWatermark)` 分支）不向下游发送。移除该 if 分支中的 `output.emitWatermark(mark)` 调用，保留 `return`。非前进 watermark 直接丢弃

Exit Criteria:

- [x] allowedLateness > 0 时 watermark 初始化前不丢弃带 timestamp 元素
- [x] restoreState 后 activeWindowsPerKey 被正确重建（非空）
- [x] 非前进 watermark 不向下游传播
- [x] 新增测试：(a) allowedLateness + Long.MIN_VALUE 初始 watermark 下的元素处理；(b) restoreState 后 Session Window 合并仍然正确；(c) watermark 回退时不向下游发送
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Timer + InputGate + Connector P1 修复

Status: completed
Targets: `nop-stream-core/.../operators/HeapInternalTimerService.java`, `nop-stream-core/.../execution/InputGate.java`, `nop-stream-connector/.../BatchConsumerSinkFunction.java`, `nop-stream-connector/.../DebeziumCdcSourceFunction.java`

- Item Types: `Fix`

- [x] AR-8: HeapInternalTimerService.advanceWatermark 修复 timer 重调度丢失。三步修复：(1) 在回调前从原始 set 中移除已触发的 timer（`originalSet.removeAll(timersToFire)`）；(2) 触发回调（回调中可能新增同时间戳 timer）；(3) 仅当 set 为空时 `eventTimeTimers.remove(entry.getKey())`。替换当前的 `eventTimeTimers.remove(entry.getKey())` 无条件删除
- [x] AR-9: InputGate AT_LEAST_ONCE 模式仅在第一个 barrier 到达时发送一次，后续 channel 的 barrier 静默消费。新增 `boolean barrierEmitted` 实例字段（与 `pendingBarrier` 同生命周期），在 handleBarrier 的 AT_LEAST_ONCE 分支中：首次到达时 `barrierEmitted = true` 并 `return Optional.of(barrier)`，后续到达时 `return Optional.empty()`。resetBarrierState() 中重置 `barrierEmitted = false`
- [x] AR-10: BatchConsumerSinkFunction.flush() 仅在 consume() 成功后清除 buffer。将 `buffer.clear()` 从 finally 块移到 try 块内 consume() 调用之后。失败时抛 StreamException 保留数据，下次 flush 触发时自动重试
- [x] AR-11: DebeziumCdcSourceFunction.run() 开头添加 `this.draining = false;`，确保 checkpoint 恢复后重新调用 run() 时 draining 被重置

Exit Criteria:

- [x] Timer 回调中注册的同时间戳 timer 被保留，已触发的 timer 被移除（三步：removeAll + 触发回调 + 条件删除）
- [x] AT_LEAST_ONCE 模式每个 barrier 对齐周期只发送一次 barrier（barrierEmitted 标记）
- [x] flush() 失败时 buffer 不被清除（数据可重试）
- [x] CDC source 恢复后 draining 标志被正确重置，run() 能正常处理数据
- [x] 新增测试覆盖每个修复项的关键行为
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 4 个 P0 发现已修复，有对应测试证明
- [x] 全部 7 个 P1 发现已修复，有对应测试证明
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope P0/P1 缺陷
- [x] No owner-doc update required（全部为代码/测试修复，不涉及平台约定变更）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: 修复未引入空壳或静默跳过
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

（无——全部 P0/P1 均在 scope 内）

## Non-Blocking Follow-ups

- Round 10 P2 发现修复（4 项：AR-12~AR-15）— 可合并为 Plan 78
- Round 10 P3 发现修复（3 项：AR-16~AR-18）— 可合并为 Plan 78
- 2026-05-30 全量审计 P2 修复（6 项：02-01, 09-01, 14-01, 14-02, 15-01, 15-04）— 可合并为 Plan 78
- 2026-05-30 全量审计 P3 修复（~25 项）— 可合并为 Plan 78
- 架构级重构设计文档（WindowOperator 重叠、core 模块拆分）

## Closure

Status Note: Plan 77 completed. All 4 P0 and 7 P1 findings from Round 10 adversarial review fixed across 4 phases (4 commits). `./mvnw test -pl nop-stream -am` BUILD SUCCESS (1422 tests). No owner-doc updates required.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent closure audit (task ses_1891df74effet9rHdCb6hgWeqU)
- Evidence:
  - Phase 1 Exit Criteria: PASS — checkpointSuccessMap at CheckpointCoordinator.java:53, shutdown abort sequence at :473-477, TestRetryFailedCommits + TestShutdownNotifiesParticipantsAbort
  - Phase 2 Exit Criteria: PASS — semaphoreReleased AtomicBoolean at TaskManager.java:412, CAS in cancelTask/run finally/updateFencingToken, 3 new tests
  - Phase 3 Exit Criteria: PASS — Long.MIN_VALUE guard at WindowAggregationOperator.java:232, activeWindowsPerKey rebuild at :201-205, non-advancing watermark fix at :364
  - Phase 4 Exit Criteria: PASS — 3-step timer fix at HeapInternalTimerService.java:117-127, barrierEmitted at InputGate.java:60, buffer.clear after consume at BatchConsumerSinkFunction.java:75, draining reset at DebeziumCdcSourceFunction.java:57
  - Anti-Hollow Check: All 11 ARs have real behavioral code with focused tests; no empty method bodies or silent no-ops
  - `./mvnw test -pl nop-stream -am` BUILD SUCCESS

Follow-up:

- Plan 78: nop-stream Round 10 + 全量审计 P2/P3 修复
- 架构级重构设计文档
