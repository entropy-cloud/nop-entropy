# Stage 52 — 事务型 JDBC sink（两阶段提交）

> Plan Status: active
> Last Reviewed: 2026-08-04
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 52；`ai-dev/design/nop-stream/checkpoint-design.md` §6.4（restore 不变量）、§12（commit 不变量）、`:204,:755,:894`（幂等 commit）；`ai-dev/design/nop-stream/connector-design.md §2`
> Related: Stage 48（Kafka IMessageService）、2PC 基础设施（`TwoPhaseCommitSinkFunction` + `CheckpointParticipant`，已落地并测试）

## Purpose

交付一个具体的事务型 JDBC sink（`JdbcTwoPhaseCommitSink`），复用已落地并充分测试的两阶段提交基础设施，使 nop-stream 能对 JDBC 目标做 exactly-once 输出。本 plan 只交付具体 sink 子类 + 幂等 commit 守卫 + E2E 验证，**不新增任何 2PC 框架基础设施**。

## Current Baseline

- **2PC 框架基础设施已全部落地并测试**：
  - `TwoPhaseCommitSinkFunction<IN>`（`nop-stream-core/.../sink/TwoPhaseCommitSinkFunction.java`，抽象类，实现 `SinkFunction` + `CheckpointParticipant`）。抽象方法：`beginTransaction():37`、`invoke(IN):44`、`preCommit(checkpointId):46`、`commit(checkpointId):48`、`rollback():50`；可覆盖：`abort(epochId):63`、`recover(epochId):67`、`saveState(epochId):80`、`prepareCommit(epochId):88`、`finishCommit(epochId,success):93`、`restoreFromEpoch(epochId,state):152`。
  - `restoreFromEpoch` 已实现设计 §6.4 不变量：durable-but-not-committed（`eid<=N`）的 pending tx **重提交**，non-durable in-flight（`eid>N`）**abort**。
  - 接线：`StreamSinkOperator.processBarrier`（`:69-109`）调用 `participant.prepareCommit`；`CheckpointCoordinator.notifyParticipantsFinishCommit`（`:1054`）按**逆拓扑序**调用 `finishCommit`；`CheckpointPlanBuilder` 自动识别 `TwoPhaseCommitSinkFunction` 算子为 participant。
- **无任何具体生产子类**：仅 3 个测试 stub（`TestTwoPhaseCommitSinkFunction`、`TestCheckpointParticipant`、`Mock2PC`）+ 1 个 inline anonymous（`TestE2ETwoPhaseCommitSink`）。
- **无 `JdbcSink` / `GenericWriteAheadSink` / write-ahead sink**（全仓库零命中）。
- `BatchConsumerSinkFunction`（`nop-stream-connector-batch`）声明 `IDEMPOTENT`、**非事务型**、无 `preCommit/commit/abort`，不参与 checkpoint。
- **模块放置约束 AR-2**：`nop-stream-connector` 必须保持不依赖 `nop-dao`/`nop-batch-jdbc`（否则 `NoClassDefFoundError`）。新 sink **不能放进 `nop-stream-connector`**。`nop-stream-connector-batch/pom.xml` 目前仅依赖 `nop-stream-core` + `nop-batch-core`（compile），**未含 `nop-dao`**——而新 sink 需要 `IJdbcTemplate`/`IDialect`（nop-dao），因此放置方案需裁定（见 Phase 1 D2）。
- 平台 JDBC 构建块：`IJdbcTemplate` + `IDialect`（nop-dao，多 DB）；`JdbcBatchConsumerProvider`（nop-batch-jdbc）做批量写但**无 epoch 绑定事务**。
- **关键约束（标准 JDBC 无 XA）**：JDBC `Connection` **不跨 JVM/task 死亡存活**——未提交写入在连接断开时被 DB 回滚。故 §6.4「durable-but-not-committed 必须 re-commit」要求 `pendingCommits` 的值持有**可序列化的记录批次**（非 Connection），以便 `commit(eid)` 在 restore 后重放。这是本 plan 行为语义的核心（见 Phase 1 D1）。
- commit 必须**幂等 keyed by epoch**（`checkpoint-design.md:204,:894`）。
- E2E 参考模式：`TestE2ETwoPhaseCommitSink`（`coordinator.addParticipant(tpcSink)`，断言 begin/invoke/preCommit/commit 顺序）。

## Goals

- 交付具体 `JdbcTwoPhaseCommitSink`（extends `TwoPhaseCommitSinkFunction<IN>`），对 JDBC 目标实现 exactly-once 输出。
- 每个 checkpoint epoch 映射一条 JDBC 事务：begin → 批量写 → preCommit（flush）→ commit（持久提交）→ abort/rollback。
- 幂等 commit 守卫：recover 后对 durable-but-uncommitted epoch 安全重提交，不产生重复数据。
- 经 `IJdbcTemplate`/`IDialect` 支持多 DB。

## Non-Goals

- 通用 write-ahead log sink（WAL，用于非事务型目标）——successor。
- 把 `BatchConsumerSinkFunction` 改成 2PC——保留为 idempotent 选项。
- XA / 分布式 DB 协调。
- 新增 2PC 框架基础设施（已完成，本 plan 不动 `TwoPhaseCommitSinkFunction` 契约）。
- 非 JDBC 事务型 sink（Pulsar txn）——successor。

## Scope

### In Scope

- 新 `JdbcTwoPhaseCommitSink`（+ builder/factory），模块放置按 Phase 1 D2 裁定。
- epoch→事务绑定（内存缓冲模型，见 Phase 1 D1）：`invoke` 内存缓冲 → **覆盖 `saveState`** 在 `super.saveState` 前把批次转入 `pendingCommits[epochId]`（经 public `getPendingCommits()`，因 saveState 先于 preCommit）→ `preCommit` 仅校验（不触 JDBC）→ `commit(epochId)` 开**新** JDBC 事务、原子写数据+ledger、提交、移除 pending → `rollback`/`abort` 丢弃内存批次（commit 前不写 JDBC）。
- 幂等 commit 策略（见 Phase 1 D1）+ 对应 DDL。
- E2E：source → sink，checkpoint + kill + recover 后**无重复、无丢失**（exactly-once 输出），kill 时机须覆盖 durable-but-uncommitted 重提交路径。
- Anti-Hollow 验证。

### Out Of Scope

- WAL sink；`BatchConsumerSinkFunction` 改造；XA；Pulsar txn sink；`TwoPhaseCommitSinkFunction` 契约变更。

## Execution Plan

### Phase 1 - 行为语义裁定 + 模块放置 + Sink 骨架

Status: planned
Targets: `nop-stream/nop-stream-connector-batch/`（或新 `-jdbc`，按 D2）、`ai-dev/design/nop-stream/connector-design.md`

- Item Types: `Decision`、`Fix`

> 关键：标准 JDBC（无 XA）下 Connection 不跨死亡存活，故须用**内存缓冲模型**而非「preCommit flush 到 JDBC 连接」模型。后者会在每次恢复丢数据（违反 exactly-once）。
>
> **执行顺序约束（live code 实测）**：`StreamSinkOperator.processBarrier` 中 `saveState(epochId)`（`:78`）运行在 `prepareCommit(epochId)`→`preCommit`（`:88`）**之前**。故若在 `preCommit` 才把批次转入 `pendingCommits`，则 `saveState(N)` 抓不到 epoch N 的批次（落后一个 epoch，restore 时该 epoch 数据永久丢失）。解决：**覆盖 `saveState(epochId)`**——先把当前内存批次转入 `pendingCommits[epochId]` 再调 `super.saveState`（经 public `getPendingCommits()`，字段为 private）。

- [ ] **D1（行为语义 + 幂等 commit 策略）**：裁定内存缓冲模型（**注意 live 顺序：saveState 先于 preCommit**）：
  - `invoke(value)`：追加到当前 epoch 的**内存**批次缓冲（不触 JDBC）。
  - **覆盖 `saveState(epochId)`**：先把当前内存批次经 `getPendingCommits().put(epochId, batch)`（字段 private，走 public getter）转入 `pendingCommits[epochId]`（可序列化记录批次），清空内存缓冲，**再**调 `super.saveState(epochId)`——使该 epoch 批次在**本次** checkpoint 即被持久化（而非落后一个 epoch）。这是避免 restore 丢数据的关键。
  - `preCommit(epochId)`：因 `saveState` 已完成转入，`preCommit` 仅做 flush 校验/惰性准备（若内存缓冲已空则 no-op）；**不写 JDBC**。（base 类 `prepareCommit`→`preCommit`；`saveState` 序列化 `pendingCommits`。）
  - `commit(epochId)`：从 `getPendingCommits().get(epochId)` 读批次，**开一条新 JDBC 事务**（独立 connection），在同一 `connection.commit()` 内**原子**写数据 + 插入 ledger 行（`epoch_id` 主键），成功后从 `pendingCommits` 移除。
  - `rollback()`：丢弃当前内存批次。`abort(epochId)`：丢弃 `pendingCommits[epochId]`（commit 前未触 JDBC，无需 DB 清理）。
  - 幂等守卫：`commit` 前查 ledger，若该 epoch 已记录则跳过写数据（recover-safe 重提交不产生重复）。
  - **subsuming 约束**：base 类 `finishCommit(M,true)` 对每个 `eid<=M` 调 `commit(eid)`——每次 `commit(eid)` 是**独立** JDBC 事务，**不共享 connection**，保证 per-epoch 原子性与 ledger 一致性。
  裁定写入 `connector-design.md`。
- [ ] **D2（模块放置裁定）**：裁定放 `nop-stream-connector-batch`（新增 `nop-dao` compile 依赖）**或**新 `nop-stream-connector-jdbc` 模块。`nop-stream-connector-batch` 加 `nop-dao` 不违反 AR-2（AR-2 只约束基模块 `nop-stream-connector`），但需评估是否引入不必要的 batch 传递依赖；若评估为「sink 不应强依赖 batch」，则建新 `-jdbc` 模块。结论写入 plan + `connector-design.md`。
- [ ] 实现 `JdbcTwoPhaseCommitSink` 骨架（按 D1）：extends `TwoPhaseCommitSinkFunction<IN>`，`beginTransaction`（预留/惰性开 connection）、`invoke`（内存缓冲）、**覆盖 `saveState`**（先 `getPendingCommits().put(epochId,batch)` 清缓冲，再 `super.saveState`）、`preCommit`（校验/惰性准备，不触 JDBC）、`commit(epochId)`（新事务写数据+ledger）、`rollback`/`abort`（丢弃内存/pending）。覆盖 `getSinkConsistency()` 返回 `TWO_PHASE_COMMIT`。
- [ ] epoch ledger DDL（可移植，经 `IDialect`）：含 `epoch_id`（主键）、`committed_at` 等列；提供初始化 SQL。

Exit Criteria:

- [ ] sink 骨架编译通过，`getSinkConsistency()` 返回 `TWO_PHASE_COMMIT`，被 `CheckpointPlanBuilder` 自动识别为 participant（有测试验证识别）。
- [ ] `connector-design.md` 已记录 D1（内存缓冲模型 + subsuming 约束）与 D2（模块放置）裁定（最终设计状态）。
- [ ] **无静默跳过**：未实现分支抛异常而非空方法体/吞异常。
- [ ] 新增功能均有对应测试（骨架各路径、participant 识别各一条，Rule #25）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 集成验证 + E2E exactly-once

Status: planned
Targets: `nop-stream/nop-stream-connector-batch/src/test/`、`nop-stream/nop-stream-runtime/src/test/`

- Item Types: `Proof`、`Fix`

- [ ] 单元测试：begin/invoke/preCommit/commit/rollback/abort 各路径；**`saveState` 顺序验证**（saveState 先于 preCommit：覆盖后的 `saveState(N)` 确把 epoch N 批次落入 `pendingCommits`，而非落后一个 epoch）；幂等 commit（重复 commit 同 epoch 不产生重复，验证 ledger 跳过）；abort 清理；`pendingCommits` 经 `saveState` 可序列化。
- [ ] restore 路径测试：`restoreFromEpoch` 对 durable-but-uncommitted epoch 安全重提交（从 `pendingCommits` 重放、ledger 跳过已记录）、对 non-durable epoch abort（复用设计 §6.4 不变量）。
- [ ] E2E（参考 `TestE2ETwoPhaseCommitSink` 模式）：source → `JdbcTwoPhaseCommitSink`，H2 内存库，多 checkpoint。**kill 时机须覆盖 preCommit 之后、finishCommit/commit 之前**（durable-but-uncommitted 窗口），恢复后断言目标表行数 = 源记录数，**无重复无丢失**（exactly-once 输出）。源须 replayable 以产生潜在重复供 ledger 去重。
- [ ] 接线验证：sink 确经 `CheckpointCoordinator.notifyParticipantsFinishCommit` 逆拓扑序 finishCommit（经日志/断言）。

Exit Criteria:

- [ ] **端到端验证**（Anti-Hollow 强制项）：source → JDBC sink 全路径，**kill 在 preCommit 后/commit 前**，recover 后 exactly-once（无重复/无丢失）断言通过——验证 §6.4 重提交路径被真实触发。
- [ ] **接线验证**：sink 在运行时确被 coordinator finishCommit 调用（非仅类型存在）。
- [ ] **无静默跳过**：所有 commit/abort 分支显式行为，未实现路径抛异常。
- [ ] 新增功能均有对应测试（幂等 commit、pendingCommits 序列化、restore 重提交、E2E exactly-once 各一条）。
- [ ] `connector-design.md` 已更新；`ai-dev/logs/` 收口记录已更新。
- [ ] `./mvnw test -pl nop-stream/nop-stream-connector-batch -am` 与 `-pl nop-stream/nop-stream-runtime -am` 通过。

## Closure Gates

- [ ] `JdbcTwoPhaseCommitSink` 已落地，exactly-once JDBC 输出经 E2E 验证（recover 无重复/无丢失）。
- [ ] 幂等 commit 守卫（epoch ledger）经测试验证 recover-safe 重提交。
- [ ] 模块放置符合 AR-2（`nop-stream-connector` 不被污染）。
- [ ] 受影响 owner docs（`connector-design.md`、`source-anchors.md`）已同步到 live baseline。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：sink 确在运行时被 coordinator 调用 finishCommit；commit/abort 无空方法体。
- [ ] `./mvnw compile`
- [ ] `./mvnw test -pl nop-stream -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（执行中如有裁定填入；不得把 in-scope live defect 放入此处。）

## Non-Blocking Follow-ups

- 通用 WAL sink（用于非事务型 JDBC 目标）。
- Pulsar txn sink（非 JDBC 事务型）。
- 把 ledger 方案升级为可配置策略（stored-proc / outbox pattern）。

## Closure

Status Note: （收口时填写）
Completed:（收口时填写）

Closure Audit Evidence:（收口时填写，见 guide Closure Audit Rule）

Follow-up:（收口时填写）
