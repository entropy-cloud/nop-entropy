# 异步两阶段 Snapshot Pipeline（G30, G44, P2）

> Plan Status: draft
> Last Reviewed: 2026-07-25
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 18；`ai-dev/analysis/nop-stream/08-gap-analysis.md` G30/G44；Plan `2026-07-25-0800-2-timer-checkpoint-unify` Deferred「Timer incremental checkpoint → Stage 18」
> Mission: nop-stream-production
> Work Item: 18
> Related: Plan `2026-07-25-1500-1-mailbox-execution-model`（Stage 17，已完成前置）；`ai-dev/design/nop-stream/checkpoint-design.md` §2.2/§2.5/§9；`ai-dev/design/nop-stream/comparison.md:160,289,293`

## Purpose

把 nop-stream 的 snapshot 从「全同步」推进到「同步阶段（状态冻结）+ 异步阶段（持久化）」，降低 checkpoint 对 task 线程与 coordinator 线程的阻塞，并为 Stage 19（多并发 checkpoint）建立 async-persist 前置。收口 G30/G44，并接住 Stage 15 deferred 的「timer incremental checkpoint」方向（本 plan 实现 async persist 框架，增量优化留给 Stage 31）。

## Current Baseline

> 全部为 live repo 核对结果（explore subagent `ses_066777c5affejUXwkXmYT1NLoQ`）。

- **snapshot 全链路同步**：`AbstractStreamOperator.snapshotState(StateSnapshotContext)`（`AbstractStreamOperator.java:180-214`）在 task 线程同步调用 `keyedStateBackend.snapshotState()` + `operatorStateBackend.snapshotState()`，返回已物化的 `OperatorSnapshotResult`（内存 Map）。`WindowOperator.snapshotState`（`WindowOperator.java:471-494`）、`StreamSourceOperator.snapshotState`（`StreamSourceOperator.java:257-271`）同理。
- **无 OperatorSnapshotFutures / 无 async 句柄**：`OperatorSnapshotResult`（`OperatorSnapshotResult.java:15-177`）是纯数据 POJO（三个 `Map<String,Object>` 桶），无 Future/CompletableFuture。grep `OperatorSnapshotFutures` 在 source = 0 命中（仅 `comparison.md:160,289,293` 对比 Flink）。
- **持久化在 coordinator 线程同步执行**：`CheckpointCoordinator.completePendingCheckpoint`（`CheckpointCoordinator.java:242-317`）在 `synchronized` 方法内同步调 `checkpointStorage.storeCheckPoint(completed)`（line 256）+ `storeEpochManifest(...)`（line 272），完成后才 `pending.forceComplete()`（line 293）完成 `PendingCheckpoint.completableFuture`。
- **task ACK 与持久化解耦现状**：task 线程在 `snapshotState` 返回后即发 barrier 并 ACK（`CheckpointBarrierTracker.acknowledgeOperator` `CheckpointBarrierTracker.java:98-143`）。持久化只阻塞 coordinator 线程，不阻塞 task 线程。但 coordinator 线程被同步持久化阻塞 ⇒ checkpoint N+1 的 trigger（`tryTriggerPendingCheckpoint`）必须等 N 的 `completePendingCheckpoint` 返回（`maxConcurrentCheckpoints` 强制为 1，`CheckpointCoordinator.java:91-95/195-200`）。
- **唯一的 CompletableFuture** 是 checkpoint 级别的 `PendingCheckpoint.completableFuture`（`PendingCheckpoint.java:42,79,103`），语义为「全部 ACK 到齐 AND 存储写入完成」，非 per-operator snapshot future。
- **无 ExecutorService 用于 snapshot/persist 卸载**：现有 `ScheduledExecutorService` 仅用于 trigger scheduler（`CheckpointCoordinator.java:60,137`）、timeout scheduler（`CheckpointCoordinator.java:61`）、barrier injector（`GraphModelCheckpointExecutor.java:583`）。
- **无 SharedStateRegistry**：grep = 0。状态快照为全量拷贝（`MemoryOperatorStateBackend.snapshotState` `new HashMap` `MemoryOperatorStateBackend.java:35-40`）。SharedStateRegistry 属 Stage 19（G33），不在本 plan。
- **存储实现**：`LocalFileCheckpointStorage.storeCheckPoint`（`LocalFileCheckpointStorage.java:74-103`）同步 `Files.write` + `Files.move`；`JdbcCheckpointStorage` 同步 JDBC。两者皆 `ICheckpointStorage` 同步实现。

## Goals

- snapshot 分为同步阶段（task 线程冻结/物化状态，返回句柄）与异步阶段（持久化写入卸载到专用 executor），checkpoint N 的持久化不阻塞 checkpoint N+1 的 trigger 判定。
- 引入 `OperatorSnapshotFutures` 等价物：snapshot 同步阶段产出 future 句柄（持久化句柄），coordinator 侧在 ACK 到齐后等待 future 完成才宣告 DURABLE；持久化失败时 epoch 进入 ABORTED。
- 端到端可观测：async persist 完成时间、失败传播路径有断言覆盖。
- owner-doc 同步：`checkpoint-design.md` §2.2（SNAPSHOTTING→DURABLE 间的 async persist 阶段）、§9（存储异步发布）、`comparison.md` 同步状态。

## Non-Goals

- 多并发 checkpoint（`maxConcurrentCheckpoints>1` 实际生效）——属 Stage 19（G31）。
- SharedStateRegistry / 引用计数 / 增量 SST 共享——属 Stage 19（G33）/ Stage 31。
- RocksDB / 异步 native snapshot——属 Stage 30。
- timer incremental checkpoint——本 plan 提供 async persist 框架，增量本身留给 Stage 31（Stage 15 deferred 项的「async」部分在此落地，「incremental」部分仍 defer）。
- credit-based flow control / 网络层改造（vision Non-Goal）。

## Scope

### In Scope

- snapshot 同步/异步两阶段拆分：同步阶段在 task 线程冻结状态产出 `OperatorSnapshotFutures` 等价物；异步阶段把持久化写入卸载到专用 `ExecutorService`。
- `CheckpointCoordinator.completePendingCheckpoint` 的持久化（`storeCheckPoint` + `storeEpochManifest`）改为异步执行；DURABLE 状态在 async persist 完成后才迁移。
- async persist 失败传播：失败时 epoch 进入 ABORTED，且不破坏 §12 不变量（manifest durable 前 sink 不 commit）。
- `CheckpointConfig` 新增 async persist 相关配置（线程池大小、是否启用）。
- 端到端测试：从 source→operator→sink 的 checkpoint 流程，async persist 完成后 DURABLE，失败时正确 abort。

### Out Of Scope

- `maxConcurrentCheckpoints` 解禁（Stage 19）。
- SharedStateRegistry（Stage 19/31）。
- RocksDB 状态后端（Stage 30）。
- 分布式路径的跨 JVM persist（当前 LocalFile/JDBC 同 JVM）。

## Execution Plan

### Phase 1 - Async Snapshot 框架与 OperatorSnapshotFutures 等价物

Status: planned
Targets: `nop-stream-core/.../checkpoint/OperatorSnapshotResult.java`、新增 `OperatorSnapshotFutures`（或等价 future 句柄类型）、`StreamOperator.java:125`、`AbstractStreamOperator.java:180`、`StreamSourceOperator.java:257`、`WindowOperator.java:471`

- Item Types: `Fix | Decision | Proof`

- [ ] 引入 `OperatorSnapshotFutures` 等价物：承载同步阶段已物化的 `OperatorSnapshotResult` + 一个「持久化句柄」`CompletableFuture<OperatorSnapshotResult>`（或等价），作为 snapshot 同步阶段的产出类型。决策点：新建独立类型 vs 扩展 `OperatorSnapshotResult`（在 Current Baseline 基础上裁定）。
- [ ] `AbstractStreamOperator.snapshotState` 改为返回 `OperatorSnapshotFutures` 等价物：同步阶段完成状态冻结/物化（沿用现有 `keyedStateBackend.snapshotState`/`operatorStateBackend.snapshotState`），产出 future 句柄（此时持久化尚未开始，句柄 pending）。
- [ ] `StreamSourceOperator.snapshotState`、`WindowOperator.snapshotState` 对齐新返回类型。
- [ ] **无静默跳过**：未实现分支抛 `UnsupportedOperationException`，不得返回 null/空句柄当作正常结果。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。

- [ ] `OperatorSnapshotFutures` 等价物类型存在且被 `AbstractStreamOperator`/`StreamSourceOperator`/`WindowOperator` 的 snapshot 路径产出（repo-observable：类型定义文件 + 三个 snapshotState 的返回类型）。
- [ ] 同步阶段仍正确冻结/物化状态（行为不变）：现有 `TestAbstractStreamOperatorSnapshot`（或等价单测）通过；snapshot 后状态内容与改造前一致（断言 Map 内容）。
- [ ] **无静默跳过**：新增类型的未实现路径抛异常而非返回 null（断言覆盖）。
- [ ] **接线验证**：future 句柄被 `CheckpointBarrierTracker.acknowledgeOperator` 消费路径感知（ACK 仍正确计数）。
- [ ] No owner-doc update required for Phase 1（类型引入，行为语义在 Phase 2 落地后统一更新文档）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Coordinator 侧异步持久化接线

Status: planned
Targets: `CheckpointCoordinator.java:242-317`（`completePendingCheckpoint`）、`PendingCheckpoint.java`、`GraphModelCheckpointExecutor.java:519-522`、新增持久化 `ExecutorService`

- Item Types: `Fix | Decision | Proof`

- [ ] 引入专用持久化 `ExecutorService`（`CheckpointCoordinator` 持有），用于卸载 `storeCheckPoint` + `storeEpochManifest`。
- [ ] `completePendingCheckpoint` 改为：ACK 到齐后把 `storeCheckPoint` + `storeEpochManifest` 提交到持久化 executor；executor 完成回调中才 `pending.forceComplete()`（DURABLE）并触发 `notifyParticipantsFinishCommit`（commit 在 DURABLE 之后，§12 不变量 5 不破）。
- [ ] async persist 失败：回调捕获异常 → epoch 进入 ABORTED（`abortPendingCheckpoint` 路径），不宣告 DURABLE，不触发 commit。
- [ ] `CheckpointConfig` 新增配置项：`asyncSnapshotEnabled`（默认 true）、`asyncSnapshotThreadPoolSize`（默认 1，Stage 19 解禁并发时再调）。
- [ ] 决策点：`forceComplete()` 时机——确认「存储写入完成」是 DURABLE 的唯一前置（与 §2.6「manifest 必须先于 notifyCheckpointComplete 持久化」一致）。

Exit Criteria:

- [ ] `completePendingCheckpoint` 的存储写入不在调用线程同步执行（repo-observable：提交到 executor，调用线程不阻塞等待写入；可通过断言「调用线程 ≠ 执行存储写入线程」验证）。
- [ ] DURABLE 仅在 async persist 成功后迁移（`PendingCheckpoint` 的 forceComplete 在 executor 回调内）。
- [ ] async persist 失败时 epoch 进入 ABORTED，不 commit：测试注入存储失败，断言未触发 `finishCommit`、epoch 状态 = ABORTED。
- [ ] **端到端验证**：从 `env.addSource()` → operator → sink 的完整 checkpoint 流程，async persist 完成后 DURABLE → commit 生效；`CheckpointCoordinator.acknowledgeTask` 全 ACK 到齐后不阻塞下一个 trigger 判定。
- [ ] **接线验证**：持久化 executor 确实被 `completePendingCheckpoint` 调用（断言存储写入发生在 executor 线程，非 coordinator 调用线程）。
- [ ] **无静默跳过**：async persist 失败必须传播为 ABORTED，不得 catch-and-log 吞掉。
- [ ] `CheckpointConfig` 新增配置项有默认值且 `./mvnw test` 通过。
- [ ] owner-doc 更新：`checkpoint-design.md` §2.2（SNAPSHOTTING→DURABLE 间 async persist 阶段）、§9（异步发布时机：manifest 写入仍在 DURABLE 前原子完成）；`comparison.md` 同步 nop-stream 现已为两阶段。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：所有条目及每个 Phase 的 Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] G30/G44 收敛：snapshot 同步/异步两阶段落地，持久化卸载不阻塞 trigger 判定。
- [ ] async persist 失败正确传播为 ABORTED，§12 不变量不破（manifest durable 前 sink 不 commit）。
- [ ] 端到端 checkpoint 流程（source→operator→sink）async persist 完成后 DURABLE 并 commit。
- [ ] 必要 focused verification（async persist 线程断言、失败 abort 断言）已完成。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect。
- [ ] 受影响 owner docs（`checkpoint-design.md` §2.2/§9、`comparison.md`）已同步到 live baseline。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 验证（a）持久化 executor 在运行时确实被 `completePendingCheckpoint` 调用（非仅类型存在），（b）无空方法体/静默跳过。
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### Timer incremental checkpoint（incremental 部分）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 落地 async persist 框架（Stage 15 deferred 项的「async」方向）；timer 的增量差量序列化属 RocksDB/增量 checkpoint 范畴，依赖 Stage 30/31。当前全量 timer snapshot 经 async persist 已可降低延迟。
- Successor Required: `yes`
- Successor Path: Stage 31（incremental checkpoint）

### SharedStateRegistry 引用计数

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属 Stage 19（G33）。本 plan 的 async persist 不引入共享状态引用计数（全量拷贝语义不变）。
- Successor Required: `yes`
- Successor Path: Stage 19

## Non-Blocking Follow-ups

- `asyncSnapshotThreadPoolSize` 默认 1；Stage 19 解禁并发时需重新评估池大小与背压。
- 若 `JdbcCheckpointStorage` 的 JDBC 连接池成为 async persist 瓶颈，作为 Stage 19 性能评估项。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 验证结果（PASS/FAIL + live code path / test name）
  - 每条 Closure Gate 验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
  - Deferred 项分类检查：无 in-scope live defect 被降级

Follow-up:

- Timer incremental checkpoint → Stage 31
- SharedStateRegistry → Stage 19
- asyncSnapshotThreadPoolSize 评估 → Stage 19
