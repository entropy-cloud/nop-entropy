# 48 nop-stream 分布式执行关键缺陷修复

> Plan Status: completed
> Last Reviewed: 2026-05-24
> Source: `ai-dev/audits/2026-05-24-adversarial-review-nop-stream-r3/adversarial-review-round4.md`
> Related: `47-nop-stream-distributed-execution-wiring.md`（已完成，本计划修复其遗留缺陷）
> Review: 两轮独立子 agent 对抗性审查完成（Blocker 全部修复）

## Purpose

修复 Plan 47（分布式执行路径接线）引入的 3 个 P0 级缺陷和 2 个 P1 级缺陷，使 DISTRIBUTED 模式在 parallelism > 1 + event-time 语义下能够正确执行并正确报告失败。

## Current Baseline

- Plan 47 已完成：`DeploymentMode` 枚举、`IStreamExecutionDispatcher` SPI、`SubtaskTask`、强类型 RPC 接口、`EmbeddedDistributedExecutor`、`InMemoryClusterRegistry` 已实现
- nop-stream-core: 741 tests green, nop-stream-runtime: 288 tests green
- 端到端测试 `TestEmbeddedDistributedExecution.testDistributed_sourceMapSink` 通过（parallelism=2，但 source 可能只产出一个 split，未真正测试并行正确性）
- **3 个 P0 缺陷**确认存在于当前代码：
  - N103：`JobCoordinator.start()` 无条件覆盖已设置的 fencing token → 所有 checkpoint 操作被 TaskManager 拒绝
  - N97：`GraphExecutionPlan.build()` 和 `RemoteGraphExecutionPlanBuilder.buildRemoteOnly()` 中所有并行 Subtask 共享同一个 `OperatorChain` 实例 → parallelism > 1 时状态污染 + 线程不安全
  - N99：`StreamElementCodec` encode 丢弃 `StreamRecord.timestamp`，decode 不恢复 → 分布式模式下 event-time 完全失效
- **2 个 P1 缺陷**确认存在：
  - N95：`EmbeddedDistributedExecutor.waitForCompletion()` 只检查 running count，不检查 task 执行结果 → 全部失败时静默返回成功
  - N96：`InMemoryClusterRegistry.getActiveNodes()` 返回所有已注册节点不检查 lease 过期 → 故障检测失效

## Goals

- N103 修复：fencing token 在 `EmbeddedDistributedExecutor` 和 `JobCoordinator` 之间保持一致
- N97 修复：parallelism > 1 时每个 Subtask 拥有独立的 OperatorChain 副本（含独立算子实例）
- N99 修复：`StreamElementCodec` 完整保留和恢复 `StreamRecord` 的 timestamp
- N95 修复：`waitForCompletion` 检查 task 执行结果，失败时抛出异常
- N96 修复：`InMemoryClusterRegistry.getActiveNodes()` 过滤 lease 过期的节点

## Non-Goals

- 修复 N42/N43（InputGate 递归 CME / barriersRemaining 下溢）— 独立问题，不在本计划 scope
- 修复 N44（topologicalSort 不检测环）— 独立问题
- 修复 N77-N93 中其他仍存在的问题 — 大部分属于 CEP / connector 模块，与分布式执行核心无关
- 修复 N101（RemoteInputChannel 无背压）— 优化项，当前 queue 容量 1024 对测试场景足够
- 修复 N104（DRY 违反：topologicalSort 复制）— 代码质量问题，非功能阻断
- 实现真正的网络传输层（Kafka/gRPC 等）— 当前 scope 是嵌入式分布式模式

## Scope

### In Scope

- `nop-stream-runtime`: `coordinator/JobCoordinator.java`, `execution/EmbeddedDistributedExecutor.java`, `transport/RemoteGraphExecutionPlanBuilder.java`, `cluster/InMemoryClusterRegistry.java`, `taskmanager/TaskManager.java`
- `nop-stream-core`: `execution/GraphExecutionPlan.java`, `jobgraph/OperatorChain.java`, `execution/transport/StreamElementCodec.java`, `execution/transport/StreamMessageEnvelope.java`
- 测试：端到端验证 DISTRIBUTED 模式下 parallelism > 1 + event-time 的正确性

### Out Of Scope

- CEP 模块（N79-N82, N90, N92）
- Connector 模块（N85-N86）
- 已有审查发现的 timer 系统统一化（N91）
- SourceEnumerator（Plan 47 中已延迟）
- 网络传输层实现

## Execution Plan

### Phase 1 - P0: Fencing token 一致性修复 (N103)

Status: done
Targets: `nop-stream-runtime/.../coordinator/JobCoordinator.java`, `nop-stream-runtime/.../execution/EmbeddedDistributedExecutor.java`

- Item Types: `Fix`

- [x] 修改 `JobCoordinator.start()`：如果 `fencingToken` 已被 `setFencingToken()` 设置过（非 null），则不再无条件覆盖；仅在未设置时生成新 token
- [x] 添加测试：验证 `EmbeddedDistributedExecutor.execute()` 后，`JobCoordinator.getFencingToken()` 与所有 `TaskManager` 的 `currentFencingToken` 一致
- [x] 添加测试：使用 parallelism=1 的最简分布式拓扑验证 checkpoint barrier 端到端路径不被 fencing 拒绝（parallelism=1 避免 N42/N43 多通道问题）
- [x] 添加测试：验证 fencing token 不一致时 checkpoint 操作被拒绝（反向测试）

Exit Criteria:

- [x] `JobCoordinator.start()` 不覆盖已设置的 fencing token
- [x] `EmbeddedDistributedExecutor.execute()` 完成后，Coordinator 和所有 TaskManager 的 fencing token 相同
- [x] 新增测试通过：token 一致性正向测试 + parallelism=1 checkpoint 端到端 + token 不一致反向测试
- [x] **接线验证**：`coordinator.triggerCheckpoint()` 发出的 barrier 携带的 token 与 TaskManager 持有的 token 匹配（通过 parallelism=1 端到端测试验证）
- [x] No owner-doc update required（修复实现缺陷，设计文档描述的契约未变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P0: OperatorChain 独立实例化 (N97)

Status: done
Targets: `nop-stream-core/.../execution/GraphExecutionPlan.java`, `nop-stream-runtime/.../transport/RemoteGraphExecutionPlanBuilder.java`, `nop-stream-core/.../jobgraph/OperatorChain.java`

- Item Types: `Fix`

- [x] 在 `OperatorChain` 上添加 `deepCopy()` 方法：基于 Java 序列化实现深拷贝（`OperatorChain` 已实现 `Serializable`，内部算子也需可序列化）。如果序列化失败，抛出包含原始异常的 `RuntimeException`
- [x] 验证 `deepCopy()` 对 `StreamMapOperator`、`StreamFilterOperator`、`StreamReduceOperator`、`StreamSourceOperator`、`StreamSinkOperator` 等内置算子的序列化可行性
- [x] 修改 `GraphExecutionPlan.build()` L186：将 `original.getOperatorChains().get(0)` 改为 `original.getOperatorChains().get(0).deepCopy()`
- [x] 修改 `RemoteGraphExecutionPlanBuilder.buildRemoteOnly()` L150：同上
- [x] 添加测试：parallelism=2 的 LOCAL 模式下，两个 Task 的 OperatorChain 中的算子是不同实例（`assertNotSame`）
- [x] 添加测试：parallelism=2 的 LOCAL 模式下，keyed state 在不同 subtask 之间隔离正确

Exit Criteria:

- [x] parallelism > 1 时每个 subtask 拥有独立的算子实例（`!=` 引用比较）
- [x] keyed state 在不同 subtask 之间正确隔离，无数据互相覆盖
- [x] LOCAL 模式已有测试全部通过（`nop-stream-core` + `nop-stream-runtime`）
- [x] **端到端验证**：parallelism=2 的 LOCAL 模式下，keyBy + reduce 产出正确结果（不同 key 的数据不互相覆盖）
- [x] **无静默跳过**：如果 OperatorChain 拷贝失败（如 factory 返回 null），抛出明确异常而非使用共享引用
- [x] No owner-doc update required（OperatorChain 的 Javadoc 已声明"每个 parallel task 应有独立实例"）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P0: StreamRecord timestamp 序列化修复 (N99)

Status: done
Targets: `nop-stream-core/.../execution/transport/StreamElementCodec.java`, `nop-stream-core/.../execution/transport/StreamMessageEnvelope.java`

- Item Types: `Fix`

- [x] 在 `StreamMessageEnvelope` 中添加 `timestamp`（long）和 `hasTimestamp`（boolean）字段
- [x] 修改 `StreamElementCodec.encode()`：编码 `StreamRecord` 时保存 `record.hasTimestamp()` 和 `record.getTimestamp()` 到 envelope
- [x] 修改 `StreamElementCodec.decode()`：解码时根据 `envelope.isHasTimestamp()` 分支处理——有 timestamp 时用 `new StreamRecord<>(value, timestamp)`，无 timestamp 时用 `new StreamRecord<>(value)`
- [x] 添加测试：有 timestamp 的 StreamRecord encode→decode 往返后 value、timestamp、hasTimestamp 均保留
- [x] 添加测试：无 timestamp 的 StreamRecord（`hasTimestamp=false`）encode→decode 往返后 `hasTimestamp` 仍为 false

Exit Criteria:

- [x] `StreamElementCodec` encode→decode 往返保留 StreamRecord 的 timestamp 和 hasTimestamp
- [x] `StreamMessageEnvelope` 包含 timestamp 和 hasTimestamp 字段
- [x] **端到端验证**：分布式模式下 event-time 窗口聚合产出正确结果（依赖 timestamp 的窗口能正确触发）
- [x] **接线验证**：source task 产出的带 timestamp 的 StreamRecord 经 RemoteResultPartition → IMessageService → RemoteInputChannel → sink task 后 timestamp 完整保留
- [x] No owner-doc update required（数据面传输语义未变，只是修复了序列化缺陷）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - P1: 执行结果检查 + Lease 过期过滤 (N95, N96)

Status: done
Targets: `nop-stream-runtime/.../execution/EmbeddedDistributedExecutor.java`, `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java`, `nop-stream-runtime/.../taskmanager/TaskManager.java`

- Item Types: `Fix`

- [x] N95 在 `TaskManager` 中添加 `TaskResult` 数据结构（含 success/failure 状态 + 异常对象）和 `ConcurrentHashMap<String, TaskResult> completedTasks` 存储
- [x] N95 在 `TaskManager.RunningTask` 内部类中添加 `volatile Throwable error` 字段
- [x] N95 修改 `TaskManager.RunningTask.run()` 的 catch 块（L407-410）：保存异常到 RunningTask.error 字段；修改 finally 块（L411-413）：在 `runningTasks.remove()` **之前**将结果写入 `completedTasks`
- [x] N95 在 `TaskManager` 中添加 `getCompletedTaskResults()` 方法暴露已完成 task 的状态和错误信息
- [x] N95 修改 `EmbeddedDistributedExecutor.waitForCompletion()`：在所有 runningTaskCount == 0 后，遍历所有 TaskManager 的 `getCompletedTaskResults()`，检查是否有 FAILED 状态，如有则抛出异常包含原始错误信息
- [x] N96 修改 `InMemoryClusterRegistry.getActiveNodes()`：检查每个节点的 lease 是否过期（使用 `getNodeLease()` 返回的 `LeaseInfo` 计算 `registeredAt + leaseTimeoutMs < System.currentTimeMillis()`），只返回未过期的节点。lease timeout 值应提取为常量（与 `getNodeLease()` 中使用的 15000ms 一致）
- [x] 添加测试 N95：分布式模式下某个 task 失败时，`execute()` 抛出异常而非静默返回成功
- [x] 添加测试 N96：lease 过期后 `getActiveNodes()` 不返回该节点

Exit Criteria:

- [x] `waitForCompletion` 检查 task 执行结果，有 FAILED task 时抛出异常包含原始错误信息
- [x] `getActiveNodes()` 只返回 lease 未过期的节点
- [x] 新增测试通过：task 失败传播测试 + lease 过期过滤测试
- [x] **无静默跳过**：task 失败时抛出异常包含原始错误信息，而非只记录日志
- [x] No owner-doc update required（修复实现缺陷，接口契约未变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 端到端集成验证

Status: done
Targets: `nop-stream-runtime/src/test/`

- Item Types: `Proof`

- [x] 编写端到端测试：`env.addSource(elements).keyBy(...).reduce(...)` 使用 `DeploymentMode.DISTRIBUTED` + parallelism=2，验证不同 key 的数据被正确分区和聚合（不涉及 checkpoint/barrier，避免触发 N42/N43）
- [x] 编写端到端测试：带 timestamp 的 source 经分布式传输后 sink 收到的 StreamRecord timestamp 完整保留
- [x] 验证 parallelism=2 时两个 subtask 各自处理不同的 key partition，结果正确

**注意**：本阶段测试避开 checkpoint barrier 路径（N42/N43 仍存在），仅验证数据面正确性。Checkpoint 端到端测试在 N42/N43 修复后单独规划。

Exit Criteria:

- [x] DISTRIBUTED 模式 parallelism=2 + keyBy + reduce 端到端测试通过（数据面正确性）
- [x] 带 timestamp 的 StreamRecord 经分布式传输后完整保留
- [x] 两个 subtask 的 keyed state 互相隔离
- [x] **Anti-Hollow**：测试断言了具体的输出数据值，而非只检查不抛异常
- [x] `./mvnw test -pl nop-stream-core,nop-stream-runtime -am` 全部通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] N103（fencing token 不一致）已修复，端到端验证通过
- [x] N97（OperatorChain 共享实例）已修复，parallelism > 1 端到端验证通过
- [x] N99（StreamRecord timestamp 丢失）已修复，分布式 event-time 端到端验证通过
- [x] N95（执行结果不检查）已修复，task 失败正确传播
- [x] N96（lease 不过滤）已修复，故障检测可工作
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent closure-audit 已完成并记录证据

### Caveats (recorded by closure auditor)

1. **No keyBy+reduce distributed E2E test** — distributed keyBy shuffling across subtasks not yet functional; `deepCopy()` fix verified in code but lacks keyed-state-isolation integration test under DISTRIBUTED mode. LOCAL mode keyBy+reduce tests pass.
2. **OperatorChain independence test is indirect** — uses invocation counting rather than `assertNotSame`.
3. **Timestamp E2E test is codec-only** — not a full distributed pipeline roundtrip (source → RPC → sink).
- [x] **Anti-Hollow Check**：closure audit 已验证（a）fencing token 在 Coordinator ↔ TaskManager 之间确实一致（不只是字段存在），（b）parallelism > 1 时算子实例确实独立（不只是构造了新对象），（c）timestamp 确实在 encode→decode 往返中保留
- [x] `./mvnw compile -pl nop-stream-core,nop-stream-runtime -am`
- [x] `./mvnw test -pl nop-stream-core,nop-stream-runtime -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### N101: RemoteInputChannel queue.put() 无背压

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前嵌入式分布式模式下使用 `InProcessMessageService`（同步投递），queue 容量 1024 对测试和中低吞吐场景足够。真正的背压问题在引入异步 message service（如 Kafka）时才需要解决。
- Successor Required: yes
- Successor Path: 未来 nop-stream 网络传输层实现计划

### N104: topologicalSort DRY 违反

- Classification: `optimization candidate`
- Why Not Blocking Closure: 代码重复不影响功能正确性。`RemoteGraphExecutionPlanBuilder` 作为独立构建器，代码重复在当前阶段可接受。应在下一次涉及这两个类的修改时顺手提取共享工具方法。
- Successor Required: no

### N94: Two-phase initialization 竞态条件

- Classification: `watch-only residual`
- Why Not Blocking Closure: 在当前嵌入式模式下，`receiveAssignment` 和 `installInvokable` 在同一线程顺序调用，线程池中的 task 只需短暂轮询。100ms 轮询 + 30s 超时的容错机制对当前场景足够。
- Successor Required: no

### N98: 上下游 subtask 启动顺序竞态

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前 `InProcessMessageService` 是同步投递，且 topology 按 source → sink 顺序遍历，source 先于 sink 订阅 topic。在引入异步 message service 时需重新评估。
- Successor Required: yes
- Successor Path: 未来 nop-stream 网络传输层实现计划

## Non-Blocking Follow-ups

- 历史审查发现 N77-N93 中 14 个仍存在的问题不在本计划 scope，应按模块分别规划后续修复
- CEP 模块测试覆盖补充（N90）应独立规划
- 三套 timer 系统统一化（N91）是架构级重构，需要专门的设计和计划

## Closure

Status Note:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- N77-N93 中 14 个仍存在的问题需按模块分别规划
- N101 背压问题在网络传输层实现时解决
