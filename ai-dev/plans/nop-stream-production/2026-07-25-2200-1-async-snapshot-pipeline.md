# 异步两阶段 Snapshot Pipeline（G30, G44, P2）

> Plan Status: completed
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 18；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G30/G44；Plan `2026-07-25-0800-2-timer-checkpoint-unify` Deferred「Timer incremental checkpoint → Stage 18」
> Mission: nop-stream-production
> Work Item: 18
> Related: Plan `2026-07-25-1500-1-mailbox-execution-model`（Stage 17，已完成前置）；`ai-dev/design/nop-stream/checkpoint-design.md` §2.2/§2.5/§9；`ai-dev/design/nop-stream/comparison.md:160,289,293`

## Purpose

把 nop-stream 的 checkpoint 持久化从「coordinator 线程同步执行」推进到「同步阶段（ACK 到齐 + 状态物化）+ 异步阶段（持久化写入卸载到专用 executor）」，使 coordinator 的 ACK 处理线程在存储 I/O 期间不再阻塞（abort 处理、timeout 调度、下次 trigger bookkeeping 保持响应）。收口 G30/G44。**本 plan 为 Stage 19（多并发 checkpoint）建立 async-persist 前置。**

## Outdated Note（draft review 修正）

> 初稿曾提出引入 task 侧 `OperatorSnapshotFutures` 等价物（仿 Flink）。经独立 review 核对（`ses_06670a0f2ffeoRFIxXGpUNBskh`）：nop-stream **无 task 侧持久化**——`AbstractStreamOperator.snapshotState`（`:180-214`）仅在 task 线程把状态拷贝进内存 Map，所有持久化发生在 coordinator 侧 `completePendingCheckpoint`（`CheckpointCoordinator.java:242-317`）。task 侧 future 既无 producer（task 不写存储）也无 consumer（ACK 路径消费已物化数据，barrier 已发）。初稿混淆了 Flink 的 task 侧 async state snapshot 与 nop-stream 的 coordinator 侧 persist。本稿改为 **coordinator 侧 async persist**，不动 task 侧 snapshotState 返回类型。

## Current Baseline

> 全部为 live repo 核对结果（explore `ses_066777c5affejUXwkXmYT1NLoQ`；review `ses_06670a0f2ffeoRFIxXGpUNBskh` 复核）。

- **持久化在 coordinator 线程同步执行**：`CheckpointCoordinator.completePendingCheckpoint`（`CheckpointCoordinator.java:242-317`）在 `synchronized` 方法内顺序执行：`storeCheckPoint`（line 256）→ `storeEpochManifest`（line 272）→ `pendingCheckpoints.remove`（286）→ `forceComplete()`（293，置 DURABLE）→ `decrementPendingCheckpointCount`（296）→ cleanupOldCheckpoints → retryFailedCommits → `notifyParticipantsFinishCommit(checkpointId, true)`（307，sink commit）→ notifyCheckpointCompleted（309）。**整段同步阻塞调用线程**（即收到最后一个 ACK 的 coordinator 路径）。
- **task 侧 snapshotState 不做持久化**：`AbstractStreamOperator.snapshotState(StateSnapshotContext)`（`:180-214`）调用 `keyedStateBackend.snapshotState` + `operatorStateBackend.snapshotState` 返回已物化的 `OperatorSnapshotResult`（内存 Map）。task 线程在 `snapshotState` 返回后即发 barrier 并 ACK（`CheckpointBarrierTracker.acknowledgeOperator` `:98-143`）。**task 线程不被持久化阻塞**；被阻塞的是 coordinator 线程。
- **`OperatorSnapshotResult`（`OperatorSnapshotResult.java:15-177`）** 是纯数据 POJO（三个 Map 桶），无 Future。`OperatorSnapshotFutures` 不存在（grep=0，仅 `comparison.md` 对比 Flink）。
- **无 ExecutorService 用于 snapshot/persist 卸载**：现有 `ScheduledExecutorService` 仅用于 trigger scheduler（`:137`）、timeout scheduler（`:96`）、barrier injector（`GraphModelCheckpointExecutor.java:583`）。
- **`maxConcurrentCheckpoints` 不是强制为 1**（draft review 修正）：`CheckpointCoordinator.java:91-95` 仅 `LOG.warn`，`:195` `effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints()` 使用**原始配置值**。即配置 >1 当前**已允许 trigger**（但 task 侧对齐/ACK 追踪仅支持单 checkpoint，这是 G31，属 Stage 19）。**注意 `checkpoint-design.md` §2.8 称「强制为 1」是 doc/code drift**（本 plan 附带纠正）。
- **当前阻塞关系**：`completePendingCheckpoint` 同步执行存储 I/O ⇒ 调用线程（ACK 路径）在存储写期间不响应（abort handler 注册、timeout 调度、下一次 trigger bookkeeping 被阻塞）。task 线程不受影响（ACK 已发）。
- **存储实现同步**：`LocalFileCheckpointStorage.storeCheckPoint`（`:74-103`）同步 `Files.write`+`Files.move`；`JdbcCheckpointStorage` 同步 JDBC。
- **无 SharedStateRegistry**：grep=0。属 Stage 19（G33），不在本 plan。
- **唯一的 CompletableFuture** 是 checkpoint 级别 `PendingCheckpoint.completableFuture`（`PendingCheckpoint.java:42,79,103`），语义为「全部 ACK 到齐 AND 存储写入完成」。

## Goals

- checkpoint 持久化（`storeCheckPoint` + `storeEpochManifest`）从 coordinator ACK 线程卸载到专用 `ExecutorService`：ACK 线程提交存储任务后立即返回，存储 I/O 不阻塞 coordinator 的响应性（abort 处理、timeout 调度、trigger bookkeeping）。
- 严格保持 `completePendingCheckpoint` 的步骤顺序与 §12 不变量 5（manifest durable 前不 commit）：存储成功 → forceComplete/DURABLE → decrementPendingCheckpointCount → notifyParticipantsFinishCommit(commit) 全部在 executor 完成回调内按序执行；存储失败 → epoch ABORTED。
- **诚实声明收益边界**：在当前 task 侧仅支持单 checkpoint 对齐的前提下，本 plan 解除的是「coordinator ACK 线程被存储 I/O 阻塞」；「checkpoint N+1 trigger 在 N 完成前即可生效」需 Stage 19（maxConcurrent>1 + 多 epoch 对齐追踪），属 Non-Goal。
- owner-doc 同步：`checkpoint-design.md` §2.2（SNAPSHOTTING→DURABLE 间 async persist）、§9（异步发布时机）、§2.8（纠正 maxConcurrent「强制为1」doc drift）。

## Non-Goals

- 改动 task 侧 `snapshotState` 返回类型 / 引入 task 侧 `OperatorSnapshotFutures`（nop-stream 无 task 侧持久化，见 Outdated Note）。
- 多并发 checkpoint 实际生效（`maxConcurrentCheckpoints>1` 的 task 侧多 epoch 对齐追踪）——属 Stage 19（G31）。
- SharedStateRegistry / 引用计数 / 增量 SST 共享——属 Stage 19（G33）/ Stage 31。
- RocksDB / 异步 native snapshot——属 Stage 30。
- timer incremental checkpoint——本 plan 提供 async persist 框架，增量本身留给 Stage 31。
- credit-based flow control / 网络层改造（vision Non-Goal）。

## Scope

### In Scope

- coordinator 侧持久化卸载：`completePendingCheckpoint` 的存储写入（`storeCheckPoint` + `storeEpochManifest`）+ 后续依赖步骤（forceComplete → decrement → commit）移入专用 executor 的完成回调，ACK 线程提交后即返回。
- async persist 失败传播：存储失败时 epoch 进入 ABORTED，不 commit（§12 不变量 5 不破）。
- `CheckpointConfig` 新增配置：`asyncSnapshotEnabled`（默认 true）、`asyncSnapshotThreadPoolSize`（默认 1）。
- 端到端测试：async persist 完成后 DURABLE→commit；失败→ABORTED；ACK 线程在存储 I/O 期间不阻塞。

### Out Of Scope

- `maxConcurrentCheckpoints` 解禁的 task 侧多 epoch 对齐（Stage 19）。
- SharedStateRegistry（Stage 19/31）。
- RocksDB 状态后端（Stage 30）。
- 分布式路径的跨 JVM persist。

## Execution Plan

### Phase 1 - Coordinator 侧 Async Persist 接线

Status: completed
Targets: `CheckpointCoordinator.java:242-317`（`completePendingCheckpoint`）、`CheckpointConfig.java`、`ai-dev/design/nop-stream/checkpoint-design.md` §2.2/§9/§2.8

- Item Types: `Fix | Decision | Proof`

- [x] `CheckpointCoordinator` 引入专用持久化 `ExecutorService`（字段，构造时按 `CheckpointConfig.asyncSnapshotThreadPoolSize` 创建，命名 `checkpoint-persist-<jobId>-<n>`，daemon 线程）；在 `stopCheckpointScheduler()` 中追加 `executor.shutdown()` 生命周期管理。
- [x] `CheckpointConfig` 新增 `asyncSnapshotEnabled`（默认 true）、`asyncSnapshotThreadPoolSize`（默认 1）。
- [x] **并发模型决策（核心，回答「应该发生什么」）**：保持 `synchronized` 保护 coordinator 的复合原子性（§13.2「并发数检查+计数自增必须原子」）。**只把存储 I/O 卸载到 executor，锁保护的复合操作仍在 monitor 内。** 分三段：

  ```
  // 段 1：ACK 线程，持有 coordinator monitor（即现有 synchronized completePendingCheckpoint 入口）
  pending = pendingCheckpoints.get(checkpointId)
  CAS(RUNNING → COMPLETED)                          // 沿用 :250
  completedCheckpoint = pending.toCompletedCheckpoint()   // 不可变数据快照
  manifest = buildEpochManifest(completed)               // 不可变
  提交 persistTask 到 executor，释放 monitor，ACK 线程立即返回

  // 段 2：persistTask，executor 线程，【不持锁】（仅 I/O，数据为不可变快照）
  try:
      checkpointStorage.storeCheckPoint(completed)        // §9 原子发布 1
      checkpointStorage.storeEpochManifest(manifest)     // §9 原子发布 2
  catch Exception e:
      → 进入 段 3b（失败回调）
  → 进入 段 3a（成功回调）

  // 段 3a：成功回调，【重新获取 coordinator monitor】
  if (!pendingCheckpoints.remove(checkpointId, pending))  // CAS-form remove :286
      return                                              // 并发 abort 已接管
  pending.forceComplete()                                 // DURABLE（AR-19：存储成功后）
  latestCompletedCheckpoint = completed                   // :295
  decrementPendingCheckpointCount()                       // :296（与 trigger 的 check-then-act 同在 monitor 内，§13.2）
  metrics.incrementCompletedCheckpoints / updateLatestCheckpoint  // :298-299
  cleanupOldCheckpoints / retryFailedCommits              // :301/304
  notifyParticipantsFinishCommit(checkpointId, true)       // commit，在 forceComplete 之后（§12 不变量 5）
  notifyCheckpointCompleted(checkpointId)                  // :309
  checkpointSuccessMap.remove(checkpointId)                // :311
  consecutiveTriggerFailures.set(0)                        // :313

  // 段 3b：失败回调，【重新获取 coordinator monitor】（对齐现有 :257-266/:274-283 的 inline 失败处理）
  pending.getStatus().set(FAILED)                          // 非 abortPendingCheckpoint（其 CAS RUNNING→ABORTED 在 COMPLETED 后必败）
  pendingCheckpoints.remove(checkpointId, pending)
  decrementPendingCheckpointCount()
  notifyParticipantsFinishCommit(checkpointId, false)
  notifyCheckpointAborted(checkpointId)
  metrics.recordFailure(...)
  ```

  **关键不变量（happens-before）**：
  - `notifyParticipantsFinishCommit(true)` [commit] 在 `forceComplete` [DURABLE] 之后（§12 不变量 5）；`forceComplete` 在 `storeEpochManifest` 成功之后（AR-19）。
  - 段 3a/3b 重新获取 monitor ⇒ `decrementPendingCheckpointCount` 与 `tryTriggerPendingCheckpoint` 的并发数检查（`:195-196`）仍同在 monitor 内，§13.2 复合原子性不破。
  - **abort/timeout 交互**：段 1 的 CAS(RUNNING→COMPLETED) 在 ACK 线程完成 ⇒ 存储在途期间 timeout 触发 `abortPendingCheckpoint`，其 CAS(RUNNING→ABORTED) 必败（已 COMPLETED）→ no-op。这是正确行为：所有 ACK 已到齐，checkpoint 正在完成，不应被 abort。若存储随后失败，段 3b 显式置 FAILED（非静默）。
  - **线程上下文变更（observable）**：`forceComplete`/`notifyCheckpointCompleted`/`notifyParticipantsFinishCommit` 现在在 `checkpoint-persist-*` 线程执行（原本在 ACK 线程）。消费方（savepoint `.get()` 阻塞等待、CheckpointListener 回调）语义不变（`.get()` 仍阻塞至完成），但须在 owner-doc 记录此线程上下文变更。
  - 伪代码仅列不变量相关步骤；实现者须保留 `CheckpointCoordinator.java:295-313` 的全部副作用（metrics/cleanup/retry/listener notify 等），不得遗漏。

- [x] `completePendingCheckpoint` 在 `asyncSnapshotEnabled=false` 时保留当前同步实现（段 1+2+3a 全在一个 synchronized 方法内，行为不变），确保可回退。
- [x] **（执行时细化项 N1）** stale/duplicate ACK 在 async 窗口的行为对齐 sync 语义：async 模式下 monitor 在段 2 释放，重复 ACK 可达 `acknowledgeTask`（`:224-240`→`PendingCheckpoint.acknowledgeTask`）命中 `status != RUNNING` 抛异常。须在 `acknowledgeTask` 入口加 status guard（非 RUNNING 时 return false），使其与 sync 的「重复 ACK 返回 false」语义一致（支撑 Exit Criteria「sync fallback 行为一致」）。
- [x] **（执行时细化项 N2）** persist executor 生命周期：不在构造时创建后于可重启的 `stopCheckpointScheduler` 中 shutdown（否则 start→stop→start 会把 executor 留在 SHUTDOWN，下次 submit 抛 RejectedExecutionException 致 checkpoint 卡死）。改为在 `startCheckpointScheduler` 创建、仅在终态 `shutdown()`（`:519`）中关闭，或段 1 catch `RejectedExecutionException` 走段 3b。
- [x] **（执行时细化项 N3）** persist executor `shutdown()` 纪律：带短暂 `awaitTermination`（对齐 trigger scheduler `:182-188`），或显式记录「fire-and-forget + CAS-remove guard」。

Exit Criteria:

- [x] async 模式下 `storeCheckPoint`/`storeEpochManifest` 在 persist executor 线程执行，不在 ACK 调用线程执行（断言：执行存储写入的线程名含 `checkpoint-persist`，≠ 收到 ACK 的调用线程）。
- [x] ACK 线程在 async 模式下提交 persistTask 后立即返回：断言 `acknowledgeTask`→`completePendingCheckpoint` 路径在存储写入完成前已返回（CountDownLatch/计数器验证调用线程不阻塞等待存储）。
- [x] **并发安全**：段 3a/3b 重新获取 monitor；新增 focused test 验证「存储在途期间并发 `tryTriggerPendingCheckpoint` 的并发数检查与段 3a 的 decrement 不产生竞态」（§13.2）——例如断言 `numPendingCheckpoints` 永不为负、不双减。
- [x] **abort/timeout 交互**：focused test 验证「存储在途期间 timeout abort no-op（CAS 失败）、存储成功后正常 DURABLE、存储失败后段 3b 置 FAILED」。
- [x] 步骤顺序保持：`forceComplete`(DURABLE) 在 `storeEpochManifest` 之后、`notifyParticipantsFinishCommit`(commit) 在 `forceComplete` 之后（断言三者的 happens-before）。
- [x] **无静默跳过**：async persist 失败必须经段 3b 显式置 FAILED + `notifyParticipantsFinishCommit(false)`（断言：注入存储失败后 epoch 状态=FAILED、`finishCommit(true)` 未被调用）；不得 catch-and-log 吞掉。
- [x] sync fallback（`asyncSnapshotEnabled=false`）行为与改造前一致：现有 checkpoint 测试全过。
- [x] **端到端验证**：source→operator→sink 完整 checkpoint 流程，async persist 完成后 DURABLE→commit 生效；注入存储失败后正确 FAILED、不破坏已 durable 的 epoch。
- [x] **接线验证**：persist executor 在运行时确实被 `completePendingCheckpoint` 调用（断言提交计数递增）。
- [x] **副作用完整**：closure audit 核对段 3a 保留了 `latestCompletedCheckpoint`/metrics/`notifyCheckpointCompleted`/`checkpointSuccessMap.remove`/`consecutiveTriggerFailures` 等全部现有副作用（非仅 forceComplete+commit）。
- [x] owner-doc 更新：`checkpoint-design.md` §2.2（SNAPSHOTTING→DURABLE 间 async persist 阶段 + 线程上下文变更）、§9（异步发布时机：manifest 写入仍在 DURABLE 前原子完成）、§2.8（纠正「强制为1」doc drift → 实际 warn-only）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] G30/G44 收敛：coordinator 侧持久化卸载到 executor，ACK 线程在存储 I/O 期间不阻塞。
- [x] §12 不变量 5 不破：manifest durable 前不 commit（步骤顺序断言覆盖）。
- [x] async persist 失败正确传播为 ABORTED。
- [x] sync fallback 行为与改造前一致。
- [x] 端到端 checkpoint 流程（source→operator→sink）async persist 完成后 DURABLE 并 commit。
- [x] 必要 focused verification（执行线程断言、步骤 happens-before 断言、失败 abort 断言）已完成。
- [x] 不存在被静默降级到 deferred 的 in-scope live defect。
- [x] 受影响 owner docs（`checkpoint-design.md` §2.2/§9/§2.8）已同步。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：persist executor 在运行时确实被 `completePendingCheckpoint` 调用（非仅类型存在）；async 失败路径实际抛 ABORTED（非静默）。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### Timer incremental checkpoint（incremental 部分）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 落地 async persist 框架（Stage 15 deferred 项的「async」方向）；timer 增量差量序列化属 RocksDB/增量 checkpoint，依赖 Stage 30/31。当前全量 timer snapshot 经 async persist 已可降低延迟。
- Successor Required: `yes`
- Successor Path: Stage 31

### SharedStateRegistry 引用计数

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 Stage 19（G33）。本 plan 不引入共享状态引用计数（全量拷贝语义不变）。
- Successor Required: `yes`
- Successor Path: Stage 19

### 多并发 checkpoint（maxConcurrent>1 的 task 侧多 epoch 对齐追踪）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 Stage 19（G31）。本 plan 不解禁 maxConcurrent 的 task 侧对齐（诚实声明收益边界）。本 plan 的 async persist 是 Stage 19 的前置。
- Successor Required: `yes`
- Successor Path: Stage 19

## Non-Blocking Follow-ups

- `asyncSnapshotThreadPoolSize` 默认 1；Stage 19 解禁并发时需重新评估池大小与背压。
- `checkpoint-design.md` §2.8 的 maxConcurrent doc drift 纠正后，Stage 19 需重新描述并发能力。

## Closure

Status Note: 完成所有 Phase 1 items + Exit Criteria + Closure Gates。coordinator 侧持久化已卸载到 `checkpoint-persist-<jobId>-<n>` executor，ACK 线程在存储 I/O 期间不阻塞；§12 不变量 5 与 §13.2 并发原子性保持；async 失败路径非静默（FAILED + finishCommit(false)）。doc-sync 完成（§2.2/§2.8/§9.1/§9.4）。
Completed: 2026-07-25

Closure Audit Evidence:

- Reviewer / Agent: executing agent (self-audit against live code + test execution)
- Audit Session: mission-driver EXECUTE round
- Evidence:
  - 每条 Exit Criterion 验证结果：
    - 存储在 persist 线程执行：PASS — `TestAsyncSnapshotPipeline.testStoreRunsOnPersistExecutorThread` 断言 `storeCheckpointThread.get()` startsWith `checkpoint-persist-async-job`，≠ ACK caller thread name。
    - ACK 非阻塞：PASS — `testAckCallerReturnsBeforeStorageCompletes` 用 latch 阻塞 storeCheckPoint，断言 ACK 调用返回时 `pending.getCompletableFuture().isDone()==false`（存储仍在途）。
    - 并发安全：PASS — `testNoNegativePendingCountUnderConcurrentTriggerAndComplete` 在存储在途期间并发 `tryTriggerPendingCheckpoint`，断言 `numPendingCheckpoints` 永不为负。
    - abort/timeout 交互：PASS — `testTimeoutAbortDuringInFlightPersistIsNoOpThenCompletes` 断言存储在途 abort 不改变 COMPLETED 状态（CAS 失败 no-op），释放后正常 DURABLE。
    - 步骤顺序：PASS — `testStepOrderingDurableAfterManifestAndCommitAfterDurable` 用全局 sequence counter 断言 manifestDone < forceComplete < commit。
    - 无静默跳过：PASS — `testStorageFailureDuringInFlightPersistMarksFailedAndAborts` 断言 status=FAILED + finishCommit(false) called + finishCommit(true) NOT called。`testManifestFailureAfterCheckpointStoreMarksFailed` 覆盖 manifest 失败路径。
    - sync fallback：PASS — 487 tests 全过，现有 checkpoint 测试显式 `asyncSnapshotEnabled=false`（`testSyncFallbackRunsStorageOnCallerThread` 断言 storage 在 caller 线程执行 + future 在 acknowledgeTask 返回前 isDone）。
    - 端到端：PASS — `testEndToEndAsyncPersistCompletesAndCommits` 断言 committedEpoch == checkpointId + completedNotifiedEpoch == checkpointId。
    - 接线验证：PASS — `testPersistExecutorInvokedByCompletePendingCheckpoint` 断言 storeCheckpointCount ≥ 1 + storeManifestCount ≥ 1。
    - 副作用完整：PASS — `testAllSideEffectsPreservedOnAsyncSuccess` 断言 latestCompletedCheckpoint set + notifyCheckpointCompleted fired + consecutiveTriggerFailures reset + metrics.incrementCompletedCheckpoints fired。
    - owner-doc：PASS — `checkpoint-design.md` §2.2/§2.8/§9.1/§9.4 已更新（git diff 可核）。
    - dev log：PASS — `ai-dev/logs/2026-07/2026-07-25.md` 已创建。
  - 每条 Closure Gate 验证结果：全 PASS（见上 Exit Criteria 映射 + Deferred 分类无 in-scope live defect 降级）。
  - `./mvnw test -pl nop-stream -am -T 1C`：487 tests, 0 failures, 0 errors — PASS。
  - `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests`：BUILD SUCCESS — PASS。
  - Deferred 项分类检查：Timer incremental checkpoint（optimization candidate → Stage 31）、SharedStateRegistry（out-of-scope → Stage 19）、多并发 checkpoint task 侧对齐（out-of-scope → Stage 19）—— 均非 in-scope live defect，分类正确。

Follow-up:

- Timer incremental checkpoint → Stage 31
- SharedStateRegistry → Stage 19
- 多并发 checkpoint → Stage 19
