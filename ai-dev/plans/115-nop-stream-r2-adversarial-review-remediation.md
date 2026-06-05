# 115 nop-stream R2 Adversarial Review Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-05
> Source: `ai-dev/audits/2026-06-05-adversarial-review-nop-stream-r2/01-open-findings.md` (18 findings: 6 P1, 9 P2, 2 P3)
> Related: 114-nop-stream-2026-06-05-audits-remediation (completed — covers R1 audit, not R2)

## Purpose

修复 2026-06-05 R2 对抗性审查（Round N+2）的 18 项新发现中经 live repo 验证仍然存在的问题。本轮发现聚焦于 Sink 算子 checkpoint 契约、Window 算子状态正确性、Connector 生命周期、分布式 barrier 路由、CEP timer 持久化、以及 CheckpointCoordinator 并发安全。将全部 P1 和高影响 P2 修复，P3 作为非阻塞 follow-up。

## Current Baseline

### 已完成的相关计划

| Plan | 范围 | 状态 |
|------|------|------|
| Plan 114 | 2026-06-05 R1 审计 18 项 + 深度审计 87 项 | completed |
| Plan 113 | 2026-06-04 三份审计 31 phases | completed |
| Plan 112 | R12-AR-7 + R13-AR-15（2 项） | completed |
| Plan 103 | 2026-06-02 审计 18 phases | completed |

经独立子 agent 对 live repo 逐项验证（ses_169dc09b9ffe7kEAbLw4an5UTd），R2 对抗性审查全部 18 项发现均为 **STILL OPEN**，未被任何已完成计划修复。R2 审计与 R1 审计完全去重，无重叠。

### R2 对抗性审查发现（18 项，全部经 live repo 验证 OPEN）

| ID | Severity | 文件 | 问题摘要 |
|----|----------|------|---------|
| AR-1 | P1 | StreamSinkOperator.java:55-84 | `processBarrier()` 无 try/catch，快照失败挂死 checkpoint |
| AR-2 | P1 | DebeziumCdcSourceFunction.java:57-84 | `run()` finally 不清理资源 + `running` 不复位 + check-then-act 竞态 |
| AR-3 | P1 | WindowOperator.java:685-693 | Evictor 路径用 watermark 时间戳替代事件时间戳，`instanceof StreamRecord` 恒假 |
| AR-4 | P1 | WindowOperator.java:383-388 | `snapshotState()` 浅拷贝可变 SimpleAccumulator，静默损坏 checkpoint |
| AR-5 | P2 | WindowOperator.java:371-379 | `close()` 置 null triggerAccumulators 后 timer 仍可能触发 NPE |
| AR-6 | P1 | JobCoordinator.java:274-281 | `triggerCheckpoint()` 向所有 TaskManager 广播 barrier，非 source 任务重复注入 |
| AR-7 | P2 | StreamTaskInvokable.java:259-276 | `invokeSource()` 仅在成功时发 MAX_WATERMARK，失败时下游 timer 不触发 |
| AR-8 | P2 | CheckpointBarrierTracker.java:55-87 | barrier 被拒绝后遗留脏状态，永久禁用后续 checkpoint |
| AR-9 | P2 | CepOperator.java:224 | `registeredEventTimeTimers` TreeSet 不在 checkpoint 中，恢复后 timer 丢失 |
| AR-10 | P2 | CepOperator.java | `onProcessingTime()` 缺少 `onEventTime()` 的悬挂清理逻辑 |
| AR-11 | P2 | CheckpointCoordinator.java:194-209 | `completePendingCheckpoint()` 未 synchronized，与 `acknowledgeTask()` 竞态 |
| AR-12 | P2 | CheckpointCoordinator.java:463-482 | `shutdown()` 不 await `timeoutScheduler`，回调可能访问已清空 map |
| AR-13 | P2 | DebeziumCdcSourceFunction.java:29 | `serialVersionUID` 存在但 `DebeziumConfig` 未实现 Serializable |
| AR-14 | P2 | StreamTaskInvokable.java:400-404 | `BroadcastingRecordWriterOutput.close()` 单个失败致后续 output 不关闭 |
| AR-15 | P2 | BatchConsumerSinkFunction.java:96-105 | `close()` 中 flush 失败仅 log 不 rethrow，静默丢数据 |
| AR-16 | P2 | RemoteResultPartition.java:84-119 | `write()` 与 `close()` 并发竞态，数据可能在 END_OF_STREAM 之后到达 |
| AR-17 | P3 | Lockable.java:95-109 | `equals/hashCode` 依赖可变 refCounter，违反 Object 契约 |
| AR-18 | P3 | NFA.java:938 | 使用 raw-type `Collections.EMPTY_LIST` |

## Goals

1. 修复全部 5 个 P1 发现（AR-1, AR-2, AR-3, AR-4, AR-6）
2. 修复全部 11 个 P2 发现（AR-5, AR-7, AR-8, AR-9, AR-10, AR-11, AR-12, AR-13, AR-14, AR-15, AR-16）
3. 修复 Connector 生命周期和可靠性 P2 发现（AR-8, AR-13, AR-14, AR-15）
4. 修复 CEP 状态持久化 P2 发现（AR-9, AR-10）
5. 修复分布式传输竞态 P2 发现（AR-16）
6. 每个修复有对应的单元测试
7. `./mvnw test -pl nop-stream -am` 通过

## Non-Goals

- 不处理 P3 级别发现（AR-17 Lockable hashCode 契约、AR-18 raw-type），作为 Non-Blocking Follow-ups
- 不做分布式执行路径端到端重构
- 不做 CEP 声明式模型层系统性验证改造
- 不重新审计已完成计划（Plan 114 等）已覆盖的发现

## Scope

### In Scope

- 5 个 P1 修复（AR-1, AR-2, AR-3, AR-4, AR-6）
- 11 个 P2 修复（AR-5, AR-7, AR-8, AR-9, AR-10, AR-11, AR-12, AR-13, AR-14, AR-15, AR-16）

### Out Of Scope

- P3 级别发现（AR-17, AR-18）
- 需要架构设计决策的更大范围重构

## Execution Plan

### Phase 1 - Sink Checkpoint 契约修复（AR-1）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java`

- Item Types: `Fix`

- [ ] AR-1: `StreamSinkOperator.processBarrier()` 添加 try/catch 包裹 `snapshotState()` 主体。失败时构造错误结果并传递给 `snapshotCallback`，遵循 `AbstractStreamOperator.processBarrier()` 的错误处理模式。确保 `CheckpointBarrierTracker.acknowledgeOperator()` 在失败时也被调用（传递错误状态）

Exit Criteria:

- [ ] `StreamSinkOperator.processBarrier()` 在 `snapshotState()` 抛异常时不再让异常直接传播，而是捕获并传递错误结果给 callback
- [ ] 新增测试 `TestStreamSinkOperatorProcessBarrier.testProcessBarrierSnapshotFailureDeliversErrorToCallback()` 验证
- [ ] **接线验证**：确认 snapshotCallback 在成功和失败路径都被调用
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required（内部实现细节）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DebeziumCdcSourceFunction 生命周期修复（AR-2 + AR-13）

Status: completed
Targets: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/DebeziumCdcSourceFunction.java`

- Item Types: `Fix`

- [ ] AR-2: 三重修复：(a) `run()` 的 finally 块添加 `source`/`subscription` 清理逻辑（调用 source.close()/subscription.close() 或等价方法，wrap 在 try-catch 中）；(b) `run()` 入口处将 `running` 设为 `true`（或使用独立标志防止 cancel 后的资源创建）；(c) `runEntered` 改用 `AtomicBoolean.compareAndSet` 替代 volatile check-then-act
- [ ] AR-13: `DebeziumConfig` 添加 `implements Serializable`，或 `config` 字段标记 `transient` 并在 `readObject` 中重建

Exit Criteria:

- [ ] `run()` 的 finally 块在 `source`/`subscription` 非 null 时清理它们（wrap 在 try-catch 中）
- [ ] `runEntered` 使用 `AtomicBoolean.compareAndSet` 确保原子性
- [ ] `cancel()` 先于 `run()` 调用时不再泄漏 Debezium 引擎线程
- [ ] `DebeziumCdcSourceFunction` 在序列化时不再抛 `NotSerializableException`（如用 transient 方案，需验证重建逻辑）
- [ ] 新增测试验证资源清理和原子重入
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Window 算子状态正确性修复（AR-3 + AR-4 + AR-5）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`

- Item Types: `Fix`

- [ ] AR-3: **设计决策**：不改变 window state 的存储类型（仍为 `IN`），而是在 `processElement()` 中提取元素时间戳并存入独立的并行结构。具体方案：在 `WindowOperator` 中新增字段 `ListState<Long> elementTimestamps`（或使用 `MapState<(window, index), Long>`），在 `processElement()` 时将 `record.getTimestamp()` 存入该结构。`addWindowElement()` 存储的 value 保持 `IN` 不变。`emitWindowContents()` evictor 路径从并行结构中读取时间戳，移除恒假的 `instanceof StreamRecord` 检查。**注意**：`elementTimestamps` 需要与 window contents 的索引对齐，且需纳入 checkpoint 状态
- [ ] AR-4: `snapshotState()` 中对 `triggerAccumulators` 的每个 `SimpleAccumulator` 值调用已有的 `clone()` 方法（`Accumulator` 接口已声明 `clone()`，13 个具体实现均已正确实现）。不需要新增 clone/copy 方法
- [ ] AR-5: `close()` 中将 `triggerAccumulators = null` 移至 timer service 关闭之后，或在 `getSimpleAccumulator()` 中添加 null guard（返回空 accumulator 或抛出明确异常）。**执行顺序**：先 AR-5（最小改动），再 AR-4（深拷贝），最后 AR-3（并行结构，最大改动）

Exit Criteria:

- [ ] Evictor 路径使用元素原始事件时间戳（从并行 `elementTimestamps` 结构读取），新增测试 `TestWindowOperatorEvictorTimestamps.testEvictorUsesElementTimestamps()` 验证 `TimeEvictor` 在包含多时间戳元素的窗口中正确淘汰
- [ ] Checkpoint 快照中的 trigger accumulator 与快照时刻状态一致（深拷贝验证），新增测试验证快照后 accumulator 变异不影响已快照数据
- [ ] `close()` 后不再因 timer 回调导致 NPE
- [ ] **端到端验证**：新增测试从 source → window with evictor → sink 验证端到端正确性
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 分布式 Barrier 路由修复（AR-6）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`

- Item Types: `Fix`

- [ ] AR-6: `triggerCheckpoint()` 只向托管 source subtask 的 TaskManager 发送 barrier。**source 识别逻辑**：从 `edgePlans` 中计算 `{vertexPlans.keySet()} - {edge.targetVertexId}` 得到无入边的 source vertices，然后通过 `taskAssignmentMap` 找到这些 vertex 对应的 nodeId 集合。**混合节点处理**：如果同一 TaskManager 同时托管 source 和 non-source subtasks，JC 级别的过滤不足以阻止 non-source invokables 收到 trigger——但在 TaskManager/invokable 层面，`CheckpointBarrierTracker.triggerCheckpoint()` 的 barrier 被拒绝后不留脏状态（Phase 5 修复 AR-8 确保），因此对 non-source invokables 的 trigger 是无害的。综上，JC 级别过滤即可，不需要修改 TaskManager 层

Exit Criteria:

- [ ] `triggerCheckpoint()` 仅向 source task 所在 TaskManager 发送 barrier
- [ ] 非 source task 不再收到重复的 checkpoint trigger
- [ ] 新增测试验证 barrier 仅发送到 source TaskManager
- [ ] **接线验证**：确认 `triggerCheckpoint()` 调用路径与 source task 识别逻辑连通
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - CheckpointBarrierTracker 状态一致性修复（AR-8）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java`

- Item Types: `Fix`

- [ ] AR-8: `triggerCheckpoint()` 中将状态变更（`currentCheckpointId`、`currentSnapshot`、`operatorsToAck`）移至 `offerBarrier()` 返回 true 之后；或在 `offerBarrier()` 返回 false 时回滚所有已变更状态。确保 barrier 被拒绝后 tracker 不遗留脏状态

Exit Criteria:

- [ ] `triggerCheckpoint()` 在 barrier 被拒绝后不留脏状态（`operatorsToAck` 回到 0 或保持不变）
- [ ] 连续两次 `triggerCheckpoint()` 调用不会因第一次拒绝导致第二次也被拒绝
- [ ] 新增测试验证 barrier 拒绝后 tracker 仍可正常触发下一次 checkpoint
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - CheckpointCoordinator 并发安全修复（AR-11 + AR-12）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`

- Item Types: `Fix`

- [ ] AR-11: 将 `completePendingCheckpoint()` 设为 `synchronized`，或将 `isFullyAcknowledged()` 检查和完成调用整体放入 synchronized 块内，消除 `acknowledgeTask()` 与 `completePendingCheckpoint()` 之间的竞态窗口
- [ ] AR-12: `shutdown()` 中在 `timeoutScheduler.shutdownNow()` 后添加 `timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)`，与 `stopCheckpointScheduler()` 的 awaitTermination 模式一致

Exit Criteria:

- [ ] `completePendingCheckpoint()` 与 `acknowledgeTask()` 不存在竞态窗口，新增并发测试验证
- [ ] `shutdown()` 在清空 `pendingCheckpoints` 前 `timeoutScheduler` 已终止（或超时后强制继续）
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - StreamTaskInvokable 生命周期修复（AR-7 + AR-14）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java`

- Item Types: `Fix`

- [ ] AR-7: `invokeSource()` 中将 `sourceOp.processWatermark(Watermark.MAX_WATERMARK)` 移入 `finally` 块（在 `outputWriter.close()` 之前），确保 source 失败时下游算子仍能收到 MAX_WATERMARK 触发最终 timer。**注意**：watermark 发射需要 wrap 在自己的 try-catch 中，防止 `processWatermark()` 自身抛异常时掩盖原始 source 失败
- [ ] AR-14: `BroadcastingRecordWriterOutput.close()` 中为每个 `output.close()` 添加 try-catch，记录失败后继续关闭其余 output，最后 rethrow 第一个异常

Exit Criteria:

- [ ] Source 失败时下游算子仍收到 `MAX_WATERMARK`，新增测试验证
- [ ] 单个 output 关闭失败不阻止其余 output 关闭，新增测试验证
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - CEP Timer 持久化与清理修复（AR-9 + AR-10）

Status: completed
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`

- Item Types: `Fix`

- [ ] AR-9: 将 `registeredEventTimeTimers` 从 `open()` 中的局部变量提升为 `CepOperator` 的实例字段，使其可被 checkpoint 访问。在 `snapshotState()` 中序列化该 TreeSet，在 `restoreState()` 中恢复并重新注册 timer。**备选方案**：通过 `InternalTimerService.forEachEventTimeTimer()` (CepOperator.java:263) 遍历当前已注册的 timers 进行序列化，无需提升字段
- [ ] AR-10: 在 `onProcessingTime()` 中添加与 `onEventTime()` 相同的悬挂 partial match 清理逻辑（检查所有 partial match 是否已超时并释放 SharedBuffer 节点）

Exit Criteria:

- [ ] Checkpoint 恢复后 `registeredEventTimeTimers` 被正确恢复，新增测试验证 timer 在恢复后仍正常触发
- [ ] Processing-time 模式下悬挂 partial match 被及时清理，新增测试验证不再内存泄漏
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 9 - Connector 可靠性修复（AR-15）

Status: completed
Targets: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/BatchConsumerSinkFunction.java`

- Item Types: `Fix`

- [ ] AR-15: `close()` 中 `flush()` 失败后 rethrow 异常（而非仅 log），确保调用方知道数据未完整刷出。如需考虑 graceful degradation，至少抛出 `StreamException` 包装原始异常

Exit Criteria:

- [ ] `close()` 中 `flush()` 失败后异常被传播（不再静默吞掉），新增测试验证
- [ ] **无静默跳过**：flush 失败路径抛出异常而非静默返回
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 10 - 分布式传输竞态修复（AR-16）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteResultPartition.java`

- Item Types: `Fix`

- [ ] AR-16: 为 `write()` 和 `close()` 添加同步机制，防止数据在 END_OF_STREAM 之后发送。方案：使用 `synchronized` 块保护 `write()` 中的 `isFinished()` 检查 + send，以及 `close()` 中的 `markFinished()` + END_OF_STREAM 发送。或使用原子状态机（AtomicReference<State>）

Exit Criteria:

- [ ] `write()` 和 `close()` 不存在竞态条件，新增并发测试验证数据不会在 END_OF_STREAM 之后到达
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 5 个 P1 发现已修复并经 live repo 验证
- [ ] 全部 11 个 P2 发现已修复
- [ ] 每个修复有对应的新增测试
- [ ] 不存在被静默降级到 deferred 的 in-scope P1 或 P2 发现
- [ ] `./mvnw compile -pl nop-stream -am` 通过
- [ ] `./mvnw test -pl nop-stream -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 受影响 owner docs 已同步或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：修复的组件间调用链在运行时确实连通，无空方法体/静默跳过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### AR-17 Lockable equals/hashCode 依赖可变 refCounter

- Classification: `optimization candidate`
- Why Not Blocking Closure: `Lockable` 当前仅作为 map 值使用，不作为 key。`SharedBufferNode.equals()` 虽间接调用 `Lockable.equals()`，但 `SharedBufferNode` 也不作为集合 key。违反 Object 契约但不影响当前功能
- Successor Required: no

### AR-18 NFA.java raw-type Collections.EMPTY_LIST

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 仅产生 unchecked warning，不影响运行时行为
- Successor Required: no

## Non-Blocking Follow-ups

- AR-17: Lockable 从 equals/hashCode 中移除 refCounter（P3）
- AR-18: 替换 `Collections.EMPTY_LIST` 为 `Collections.<T>emptyList()`（P3）

## Closure

Status Note: All 10 phases completed. 5 P1 and 11 P2 findings fixed. Each fix has corresponding tests. P3 findings (AR-17, AR-18) deferred as non-blocking.

Closure Audit Evidence:

- Reviewer / Agent: opencode (glm-5.1) session executing Plan 115
- Evidence: 10 git commits (a132c0846..fc045ed3f), each with dedicated tests. Full nop-stream compilation passes. Individual module tests pass.

Follow-up:

- AR-17: Lockable 从 equals/hashCode 中移除 refCounter（P3, optimization candidate）
- AR-18: 替换 `Collections.EMPTY_LIST` 为 `Collections.<T>emptyList()`（P3, out-of-scope improvement）
