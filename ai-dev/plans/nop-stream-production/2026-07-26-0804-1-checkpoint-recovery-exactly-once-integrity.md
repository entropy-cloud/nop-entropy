# {1} Checkpoint Recovery & Exactly-Once Data Integrity

> Plan Status: draft
> Last Reviewed: 2026-07-26
> Source: `ai-dev/audits/nop-stream-production/2026-07-25-1948-multi-audit-nop-stream-production.md` (P0-2, P0-3, P0-5, P0-6, P0-7, P0-8, P1-4, P1-5, P1-9, P1-10, P1-11); `ai-dev/design/nop-stream/checkpoint-design.md` §3.2/§3.4/§3.7/§6.3/§6.4/§8; `ai-dev/design/nop-stream/failover-design.md` §2.2; `ai-dev/design/nop-stream/mailbox-design.md` §3.5
> Mission: nop-stream-production
> Related: Stage 29 plan `2026-07-26-1000-1-serializer-fingerprint-schema-compat.md` (draft — builds the SerializerFingerprint system; P0-5 tests validate its recovery-compat contract); Plan {2} `2026-07-26-0804-2-parallel-execution-cep-correctness.md`; Plan {3} `2026-07-26-0804-3-api-type-doc-contract.md`

## Purpose

把 nop-stream 的 **checkpoint 恢复路径与端到端 exactly-once 数据完整性** 收口到「恢复后不丢数据、critical 恢复不变式有回归测试守护」。本 plan 消费 multi-audit 中所有「静默数据丢失」类 P0/P1 发现，以及「critical exactly-once 不变式零测试」类 P0 发现。

## Current Baseline

经 live 仓库核对（证据来自 2026-07-25 multi-audit，对当前 HEAD 验证）：

- **P0-2 数据丢失已确认**：`TwoPhaseCommitSinkFunction.restoreFromEpoch`（`nop-stream-core/.../sink/TwoPhaseCommitSinkFunction.java:111-127`）对全部 pending 事务盲调 `rollback()`（无参，N 次回滚同一 current tx）后 `pending.clear()`，无视 `state` 参数。违反 `checkpoint-design.md` §6.4「durable-but-not-committed 事务不得 abort」。`TestTwoPhaseCommitSinkFunction.testRestoreFromEpochClearsPendingCommits:169` 把破损行为固化成「期望」。
- **P0-3 复合数据丢失已确认**：`StreamSinkOperator.restoreState`（`core/operators/StreamSinkOperator.java:131-157`）刚 `setPendingCommits` 就调 `participant.restoreFromEpoch(-1, null)` / `tpcSink.restoreFromEpoch(-1, null)`，立即清空 pending。注意 `StreamSinkOperator extends AbstractUdfStreamOperator`，其 UDF（`TwoPhaseCommitSinkFunction`）才是 `CheckpointParticipant`，因此 `GraphModelCheckpointExecutor.restoreOperatorsFromState` 的真实 epochId 调用命中 **`:965` 的 udf 分支**（`((CheckpointParticipant) udf).restoreFromEpoch(epochId, taskState)`），而非 `:955` 的 op 分支；此时 pending 已被 `-1` 占位调用清空，no-op。
- **P1-11 静默 checkpoint 损坏已确认**：`CheckpointBarrierTracker.acknowledgeOperator`（`core/execution/CheckpointBarrierTracker.java:98-143`）从不检查 `snapshot.hasError()`，把失败 ACK 当成功。违反 `checkpoint-design.md §3.4`。
- **P1-4 lifecycle 缺失已确认**：`StreamOperator.initializeState(TaskStateSnapshot)`（`core/operators/StreamOperator.java:130-138`，override `AbstractUdfStreamOperator.java:110-134`）在 production restore 路径从未被调用（grep 生产 caller = 0，仅 test 调用）。`ICheckpointedFunction` 恢复回调静默失效。
- **P1-5 lifecycle 缺失已确认**：`StreamOperator.finish()`（`StreamOperator.java:59-78`）在 `OperatorChain`（`:99-149`）只有 open/close，无 finish 调用。文档 5 段 lifecycle 退化为 3 段。
- **P1-9 source 静默吞异常已确认**：`MessageSourceFunction`（`connector/.../MessageSourceFunction.java:122-145`）`onMessage` catch 后只置 `failed=true` 并 return，`run()` 正常退出。
- **P1-10 transport 数据丢失已确认**：`ResultPartition.close()`（`core/execution/ResultPartition.java:178-193`）queue 满时 `queue.clear()` 丢弃未消费记录。`TestResultPartitionDeadlock.testDrainedElementsLostOnClose:60-70` 固化丢失为「期望」。
- **P0-5/6/7/8 零测试/缺口已确认**：`SerializerFingerprint`/`stateFormatVersion`（per-state schema 指纹）零匹配（DAG 级 `StreamModelFingerprint` + `validateFingerprintCompatibility` 已存在）；fencing token rejection 为 warn+ignore（`TaskManager:340-343/262-266`）；savepoint vertex 集合差分——**forward 方向（current vertex 不在 checkpoint）已 reject**（`GraphModelCheckpointExecutor:917` throw），**reverse 方向（checkpoint vertex 不在 current graph）静默忽略**（`:915` 循环只遍历 current graph 顶点）；stateShardCount rescale（`StateShard.stableHash`）已实现但无跨 shardCount 测试。

## Goals

- 恢复后 durable-but-not-committed 的 sink pending 事务被正确 commit（而非盲 abort），exactly-once 不被破坏
- snapshot 失败被 barrier tracker 识别并 abort checkpoint，不静默标记 complete
- `initializeState` / `finish` 两个 lifecycle hook 在 production 路径实际被调用
- source 异常与 transport EOS 不再静默丢数据
- 4 个 critical exactly-once 不变式（serializer 兼容、fencing 拒绝、savepoint 差分、shard rescale）有可观测回归测试

## Non-Goals

- 不实现 RocksDB 状态后端（Stage 30）/ 增量 checkpoint（Stage 31）/ Key-Group rescale 体系（Stage 34-35）—— P0-8 只验证**当前** StateShard 跨 shardCount 恢复正确性，不引入 KeyGroup
- 不构建完整 SerializerFingerprint schema 体系（Stage 29 plan 已覆盖）—— P0-5 只补齐**当前已落地行为**的 recovery-compat 回归测试；若某场景依赖 Stage 29 未落地的能力，明确标注并依赖 Stage 29
- 不实现跨 JVM fencing（Stage 39）—— P0-6 只验证**当前**进程内 fencing token rejection 行为
- 不修复 CEP/并行执行/分区的 correctness bug（Plan {2}）
- 不修复 API 类型/文档漂移（Plan {3}）

## Scope

### In Scope

- `TwoPhaseCommitSinkFunction.restoreFromEpoch` 重写（P0-2）
- `StreamSinkOperator.restoreState` 去除 `-1` 占位调用（P0-3）
- `CheckpointBarrierTracker.acknowledgeOperator` 错误传播（P1-11）
- `initializeState` / `finish` production 接线（P1-4, P1-5）
- `MessageSourceFunction` 异常传播（P1-9）
- `ResultPartition.close()` 不丢数据（P1-10）
- 4 个 recovery 不变式测试套（P0-5, P0-6, P0-7, P0-8）

### Out Of Scope

- Stage 29/30/31/34/35/39 的功能建设
- 把现有「固化破损行为」的测试整体重写为弱化版（按 bug-fix 规则，保留覆盖、纠正期望）

## Execution Plan

### Phase 1 - Sink 恢复数据丢失修复 + barrier 错误传播

Status: planned
Targets: `nop-stream-core/.../sink/TwoPhaseCommitSinkFunction.java`, `nop-stream-core/.../operators/StreamSinkOperator.java`, `nop-stream-core/.../execution/CheckpointBarrierTracker.java`

- Item Types: `Fix`

- [ ] **[P0-2]** 重写 `TwoPhaseCommitSinkFunction.restoreFromEpoch(epochId, state)`：pendingCommits 由上游 `StreamSinkOperator.restoreState` 已从 `opResult` 过滤并 `setPendingCommits`（内存 map 已等价于「从 state 重建」），故本方法应**消费该内存 map**（不重复遍历整个 taskState，因 restoreFromEpoch 收到的是整 task 的 `TaskStateSnapshot`，不持有自身 operatorIndex，无法定位自己的 key）；对 `epoch <= N` 的 pending tx 调 commit，对 `epoch > N` 的调 abort。**签名策略（避免破坏 13+ 测试子类）**：新增 `abort(long epochId)` 方法，给 `abort` 一个 `default` 实现委托给现有无参 `rollback()`（保持后向兼容），不删除/不改 `rollback()` 签名。末尾 `beginTransaction()` 恰好一次。遵守 `checkpoint-design.md §6.3/§6.4/§3.7`。
- [ ] **[P0-3]** 删除 `StreamSinkOperator.restoreState`（`:131-157`）中 `participant.restoreFromEpoch(-1, null)` 与 `tpcSink.restoreFromEpoch(-1, null)` 两处占位调用；让真实 epochId 由 `GraphModelCheckpointExecutor.restoreOperatorsFromState`（udf 分支 `:965`）唯一负责。
- [ ] **[P1-11]** 在 `CheckpointBarrierTracker.acknowledgeOperator` synchronized 入口后立即检查 `snapshot.hasError()`（该方法存在于 `OperatorSnapshotResult:77`）。**关键：tracker 当前只有成功通道 `Consumer<TaskStateSnapshot> completionCallback`，无 error/abort 出口**。需新增 error 通道：扩展 tracker 构造为可注入 `Consumer<Exception> abortCallback`（或等价），`hasError()` 命中时调用它并**不**把 `snapshotToDeliver` 设为成功。**coordinator 侧接线（round-2 review 补充）**：tracker 构造于 `GraphModelCheckpointExecutor:548`，`completionCallback` 调 `coordinator.acknowledgeTask(...)`；tracker 仅持有 `taskLocation + checkpointId`、无 `PendingCheckpoint` 句柄，故**新增 `reportTaskCheckpointFailure(taskLocation, checkpointId, error)`** 为 coordinator 入口（经 `getPendingCheckpoint(checkpointId):658` 取 pending，再调 `abortPendingCheckpoint(pending, reason):608`），使 snapshot 失败触发 checkpoint abort（而非被当成功 ACK）。不得退化为「LOG + 当成功 ACK」。
- [ ] 纠正被固化破损行为的旧测试期望：实际方法名 `testRestoreFromEpoch_successfulRollbackClearsPending`（`:65`）、`testRestoreStateRecoversPendingCommitsAndRollbacks`（`:150`）、`testTwoPhaseCommitSaveRestoreRoundTrip`（`:173`）、`testRestoreFromEpoch_pendingRollbackFailureIsCaught`（`:54`，亦断言 `rollbackCallCount>=1`，修复后 durable pending 改 commit 故需更新）——把「pending 被盲清空 + rollback」改为「durable pending 被重提交、non-durable 被 abort」。注意 `TestSavepointEndToEnd` 的 `EpochCapturingOperator` 用自定义算子且断言已正确，不受本修复影响，无需改。

Exit Criteria:

- [ ] `restoreFromEpoch` 接收 durable pending 时，恢复后这些 pending 事务被 commit（有测试断言外部 side-effect 发生），不再被盲 abort
- [ ] `restoreFromEpoch` 消费上游已 set 的 pendingCommits 内存 map（不重复遍历整 taskState）；`abort(epochId)` 为新增方法（default 委托 rollback，不破坏既有 13+ 测试子类）
- [ ] `StreamSinkOperator.restoreState` 不再调用任何 `restoreFromEpoch(-1, null)`（grep 零匹配）；真实 epochId 由 `:965` udf 分支唯一负责
- [ ] 单链 snapshot 失败 → tracker 经新增 error 通道通知 coordinator abort，checkpoint 不被标记 complete（有测试注入 `OperatorSnapshotResult.error` 验证 error 回调被调、`snapshotToDeliver` 不设为成功）
- [ ] **接线验证**：恢复端到端路径 `GraphModelCheckpointExecutor.restoreOperatorsFromState` → `restoreState` → 单一真实 `restoreFromEpoch(realEpochId, state)` 调用链连通（追踪或测试断言）
- [ ] **无静默跳过**：新增/修改分支在 pending 为空或 epoch 越界时显式处理，不靠 `continue`/空 catch
- [ ] owner-doc：`checkpoint-design.md §3.7/§6.4` 若实现语义微调则同步；否则写 `No owner-doc drift introduced`
- [ ] `ai-dev/logs/2026/07-26.md` 已更新

### Phase 2 - Lifecycle hook 接线 + source/transport 静默丢失修复

Status: planned
Targets: `nop-stream-core/.../operators/StreamOperator.java`, `nop-stream-core/.../operators/AbstractUdfStreamOperator.java`, `nop-stream-core/.../jobgraph/OperatorChain.java`, `nop-stream-core/.../execution/ResultPartition.java`, `nop-stream-connector/.../MessageSourceFunction.java`

- Item Types: `Fix`

- [ ] **[P1-4]** 让 production restore 路径实际调用 `initializeState(TaskStateSnapshot)`（在 `restoreState(opResult)` 内显式传播，使 `ICheckpointedFunction` 回调生效），或在 Javadoc 显式 deprecate `ICheckpointedFunction.initializeState`。**首选前者**。
- [ ] **[P1-5]** 新增 `OperatorChain.finish()`，在 source 返回后、MAX_WATERMARK emit 前调用，驱动 5 段 lifecycle；或显式从文档移除 `finish()` 并折叠进 `close()`。**首选接 finish()**，因 connector `BatchConsumerSinkFunction.finish()`（connector:92-98）依赖它 flush 缓冲。
- [ ] **[P1-9]** `MessageSourceFunction`：用 `volatile Throwable pendingError`，`run()` 返回前 `if (pendingError != null) throw`；或重抛让 `IMessageService` 处理。不再静默正常退出。
- [ ] **[P1-10]** `ResultPartition.close()` queue 满时改用 blocking `queue.put(END_OF_STREAM)`（自然背压直到消费端排空），或至少不 `clear()` 丢数据；纠正 `TestResultPartitionDeadlock.testDrainedElementsLostOnClose` 期望为「不丢」。

Exit Criteria:

- [ ] production restore 路径上 `ICheckpointedFunction.initializeState` 被实际调用（有测试：UDF 实现 initializeState 设标志，恢复后断言标志置位）
- [ ] `OperatorChain.finish()` 在 source 返回后被调用，且 `BatchConsumerSinkFunction.finish()`（或等价 sink）缓冲被 flush（有测试断言 flush量）
- [ ] `MessageSourceFunction` collect 失败时 `run()` 抛出而非正常返回（有测试断言 pipeline 不被误判为成功 EOS）
- [ ] `ResultPartition.close()` 在消费端落后时不再丢弃已 emit 记录（有测试：producer emit N + close，慢消费端最终收到全部 N + EOS）
- [ ] **端到端验证**：从 source → operator → sink 在 bounded source EOS + 慢下游场景下不丢数据、异常不被吞
- [ ] **无静默跳过**：无新增空方法体/吞异常 `catch{}`
- [ ] owner-doc：`checkpoint-design.md` / 相关 lifecycle 文档若 finish() 接线则同步；否则 `No owner-doc update required`
- [ ] `ai-dev/logs/2026/07-26.md` 已更新

### Phase 3 - Critical exactly-once 不变式回归测试 + fencing 硬化

Status: planned
Targets: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`(`validateFingerprintCompatibility`), `nop-stream-runtime/.../taskmanager/TaskManager.java`, `nop-stream-runtime/src/test/.../checkpoint/`, `nop-stream-core/src/test/.../common/state/shard/`

> **Round-1 review 修正**：原 P0-5/6/7 假设被测 feature 已落地，但 live repo 核对显示 per-state `SerializerFingerprint`/`stateFormatVersion`/`StateMigrationFunction` 零匹配、fencing 错误码不存在、operatorId 差分检查未实现。本 Phase 据实拆分：**测试已落地行为 + 把缺失 feature 路由到正确 successor（不隐藏 gap）+ 对违反 No-Silent-No-Op 的 warn+ignore 硬化为 throw**。

- Item Types: `Fix | Proof`

- [ ] **[P0-5 — Proof]** 测试**已落地的** DAG 级 `StreamModelFingerprint` 恢复兼容：`GraphModelCheckpointExecutor.validateFingerprintCompatibility`（已存在）。新增 `TestStreamModelFingerprintRecoveryCompat`：(1) 相同 fingerprint → 恢复成功；(2) 不同 fingerprint → 抛异常拒绝恢复。**per-state schema 指纹（`SerializerFingerprint`/`stateFormatVersion`/`StateMigrationFunction`）当前零实现，属 Stage 29 feature scope，移入 Deferred（successor = Stage 29 plan）**，不在本 plan 用 `@Disabled` 伪装覆盖。
- [ ] **[P0-6 — Fix+Proof]** fencing token 当前为 **warn+ignore 静默跳过**（`TaskManager.triggerCheckpoint:340-341`、assignment `:262-264`），违反 No-Silent-No-Op 规则。**Fix**：把 stale-token 处理从 `LOG.warn + return` 硬化为抛 `StreamException`（新增错误码，如 `ERR_STREAM_FENCING_TOKEN_MISMATCH`，注册到 nop-stream error config），匹配 TaskManager Javadoc `:62/65-67`「rejects any operation carrying an old fencing token」的既定契约。**Proof**：新增 `TestFencingTokenRejection`：(1) stale token checkpoint trigger → 抛异常；(2) stale token assignment → 抛异常。**跨 JVM fencing 统一仍归 Stage 39**（本项仅硬化进程内既有检查点）。
- [ ] **[P0-7 — Fix+Proof]** savepoint 恢复的 **reverse 方向 vertex 差分静默忽略**（设计 §8.6 要求）。**经 round-2/3 review 核实**：`SavepointMetadata` 不存 operatorId 集合（仅计数），但 `CompletedCheckpoint` 以 `TaskLocation`（vertexId+taskIndex）为 key 存 `TaskStateSnapshot`。**Forward 方向（current vertex 不在 checkpoint）已 reject**（`GraphModelCheckpointExecutor:917` throw）—— 真正缺口是 **reverse 方向**（checkpoint 有、current graph 没有 → `:915` 循环只遍历 current 顶点，checkpoint-only 顶点被静默丢弃）。**Fix**：在共享恢复路径 `restoreTaskStatesFromSource`（`:890`，savepoint `:882` 与 epoch `:790` 两入口都汇入此处）加 reverse 检查——checkpoint vertex 集合 ⊄ current graph vertex 集合时 default-reject（对齐 §8.6「删除有状态=默认拒绝」）。**Proof**：新增 `TestSavepointVertexSetDifferential`：**3 个 forward 回归守护**（missing-state/new-stateful/superset-current，断言既有 `:917` throw 被保留，防止未来弱化）+ **2 个 reverse genuinely-new reject**（deleted/subset，断言新 reverse 检查拒绝）+ **1 基线**（同集合→成功）。**§8.6 对账**：reverse reject 对齐 §8.6「删除有状态=拒绝」；既有 forward-throw 比 §8.6（无状态新增=可兼容）更严，属既有行为，本 plan 保留为回归守护；§8.6 完整 state-aware 分类（区分有/无状态、initial-state fallback）依赖 operator 级元数据 → Deferred successor。
- [ ] **[P0-8 — Proof]** 新增 `TestStateShardRescale`（feature 已落地 `StateShard.stableHash` + restoreState）：(1) snapshot shardCount=2 → restore shardCount=4，全部 key 正确；(2) snapshot 4 → restore 2；(3) 验证 `stableHash` rescale 后路由等价。覆盖 `checkpoint-design.md §8.5`。

Exit Criteria:

- [ ] `TestStreamModelFingerprintRecoveryCompat` 对已落地 DAG 级 fingerprint 有真实断言（相同→成功、不同→抛异常）
- [ ] `TaskManager` stale-token 不再 warn+ignore，而是抛 `StreamException`（有测试断言抛出 + 错误码）
- [ ] reverse 方向差分检查存在于共享 `restoreTaskStatesFromSource`（savepoint + epoch 两入口都覆盖）；checkpoint-only vertex 被拒绝（有测试：deleted/subset 2 场景 reverse reject）
- [ ] forward 方向既有 `:917` throw 被保留（有 3 个回归守护测试：missing/new-stateful/superset，删除该 throw 则测试失败——满足反空壳）
- [ ] 同集合基线恢复成功（1 测试）
- [ ] `TestStateShardRescale` 跨 shardCount 恢复 key 正确（反空壳：删除 stableHash 逻辑测试失败）
- [ ] **反空壳**：每个新增测试若 weak 到「删除被测逻辑仍通过」则不合格（forward 守护测试尤其要确保删 throw 即失败）；无 `@Disabled` 伪装覆盖
- [ ] per-state SerializerFingerprint、operatorId 粒度差分 + §8.6 state-aware 分类已在 Deferred 显式路由到 successor（不隐藏 gap）
- [ ] owner-doc：`checkpoint-design.md §8.2` fencing「reject」语义与实现一致；`checkpoint-design.md §8.6`对账（reverse reject 对齐「删除有状态=拒绝」；state-aware 分类 deferred）；`docs-for-ai/02-core-guides/error-handling.md` 若新增错误码则记录
- [ ] `ai-dev/logs/2026/07-26.md` 已更新

## Closure Gates

- [ ] 所有 in-scope P0 数据丢失缺陷已修复且端到端验证（恢复不丢 durable pending、EOS 不丢已 emit 记录）
- [ ] 所有 in-scope lifecycle/异常传播 contract drift 已收敛（initializeState / finish / source / barrier tracker）
- [ ] 4 个 critical exactly-once 不变式有可观测回归测试（依赖未落地场景已显式标注）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] 受影响 owner docs 已同步或明确 `No owner-doc update required`
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证恢复调用链运行时连通（restoreState → 单一 restoreFromEpoch(realEpochId) → commit/abort），无空方法体/静默跳过
- [ ] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### P0-5 per-state SerializerFingerprint schema 兼容体系

- Classification: `moved to explicit successor ownership`（缺失 feature，非 live defect）
- Why Not Blocking Closure: 本 plan 覆盖**已落地的** DAG 级 `StreamModelFingerprint` 恢复兼容测试。per-state schema 指纹（`SerializerFingerprint`/`stateFormatVersion`/`StateMigrationFunction`）当前在 nop-stream 零实现——这是缺失 feature（设计 §8.4.1 描述），其测试无法脱离 feature 单独存在。feature 建设归属 Stage 29 plan（`2026-07-26-1000-1`，draft），其 Exit Criteria 必须包含 5 场景 schema-compat 测试。本 plan 不用 `@Disabled` 伪装覆盖。
- Successor Required: yes
- Successor Path: `ai-dev/plans/nop-stream-production/2026-07-26-1000-1-serializer-fingerprint-schema-compat.md`（Stage 29）

### P0-7 operatorId 粒度差分 + nuanced 策略

- Classification: `moved to explicit successor ownership`（需元数据架构扩展，非 live defect）
- Why Not Blocking Closure: 本 plan 实现 **vertex 集合（TaskLocation）default-reject** 检查（当前 `CompletedCheckpoint` 数据可支撑，关闭「静默用错 state」安全基线）。operatorId 粒度差分（modified/type-change）需扩展 `SavepointMetadata` 持久化 operatorId 集合（架构变更）；nuanced 策略（initial-state fallback、migration mapping）需用户确认策略 + 迁移 SPI。两者属 feature 扩展，超出审计 remediation 范围。
- Successor Required: yes
- Successor Path: 待起草（roadmap Phase 2/3 状态迁移体系，Stage 33 状态迁移接线延伸；需含 SavepointMetadata 元数据扩展）

## Non-Blocking Follow-ups

- RocksDB / Key-Group / 跨 JVM fencing 的完整测试矩阵归 Stage 29/30/34/39
- `ResultPartition.close()` 的 permit double-release race（open-audit AR-5，P2）归 backlog

## Closure

Status Note: <<完成时填写>>
Completed: <<待定>>

Closure Audit Evidence:

- Reviewer / Agent: <<待定>>
- Evidence: <<待定>>

Follow-up:

- <<待定>>
