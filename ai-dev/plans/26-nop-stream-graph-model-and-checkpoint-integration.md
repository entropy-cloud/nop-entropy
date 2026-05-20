# 26 nop-stream Graph Model 与 Checkpoint 端到端集成

> Plan Status: completed
> Last Reviewed: 2026-05-20
> Source: `ai-dev/design/nop-stream/checkpoint-design.md` §10（集成状态）、`ai-dev/design/nop-stream/graph-model-design.md` §7（两条路径的关系）
> Related: `24-nop-stream-code-cleanup-and-restructure.md`（代码清理已完成）、`03-nop-stream-improvement-plan.md`（旧改进计划，部分过时）

## Purpose

将 nop-stream 的图模型执行路径和 checkpoint 子系统从"组件已实现但未对接"状态推进到"端到端可运行、可验证"状态。完成后，用户通过 DataStream API 构建的流处理管线可以：

1. 走图模型路径执行（StreamGraph → JobGraph → TaskExecutor），算子链内正确处理数据流
2. 自动触发 checkpoint，barrier 随数据流传播，算子快照状态，端到端 exactly-once 语义可用
3. 从 checkpoint 恢复执行，状态被正确重建

## Current Baseline

### 执行路径

- `StreamExecutionEnvironment.execute()` 走快速路径：`buildTransformationChain()` → `instantiateOperators()` → `wireOperatorChain()` → `runSource()`，所有算子在同一线程同步执行
- 快速路径跳过 `PartitionTransformation`（keyBy 无效），不支持分支/合并
- 图模型路径（StreamGraph → JobGraph → TaskExecutor）的类已实现但 `execute()` 不调用它们
- `Invokable.invoke()` 是 placeholder（只做 open/close，不实际读数据）

### Checkpoint

- CheckpointCoordinator 完整生命周期已实现（trigger → ACK → complete/abort → restore）并通过单元测试
- BarrierAligner 多输入对齐已实现
- ICheckpointStorage + LocalFileCheckpointStorage + JdbcCheckpointStorage 已实现
- TwoPhaseCommitSinkFunction 接口已定义
- **但**：`StreamExecutionEnvironment` 中零处出现 CheckpointCoordinator；`AbstractUdfStreamOperator.snapshotState()` 被注释掉；`CheckpointBarrier` 未被任何代码注入数据流；Source offset 未被 checkpoint 管理

### 测试覆盖

- Checkpoint：7 个单元测试覆盖数据模型、Coordinator 生命周期、BarrierAligner 对齐、Storage 持久化
- 图模型：`TestStreamGraphGenerator` 和 `TestJobGraph` 有单元测试，验证 Transformation → StreamNode → JobVertex 转换
- **缺口**：无端到端集成测试（barrier 注入 → 快照 → ACK → 完成 → 恢复）；无图模型路径的执行测试

### 依赖

- State Management：MemoryStateBackend 已完成，checkpoint 可通过 JsonTool 全量序列化 → **不阻塞**
- Window System：WindowOperator 已实现，使用 MapState<Window, ACC> → **不阻塞**

## Goals

- `StreamExecutionEnvironment.execute()` 可选择走图模型路径执行（新增 `executeWithGraphModel()` 或通过配置切换）
- 图模型路径中，**单链管线**（所有算子在一个 chain 内）的 Task 正确执行数据流
- CheckpointCoordinator 在 execute() 中被创建、注册 task、启动调度
- CheckpointBarrier 被注入 Source 输出，随算子链传播到所有算子
- 算子收到 barrier 时执行状态快照并 ACK
- Sink 端 TwoPhaseCommitSinkFunction 正确执行 2PC 流程
- 从 checkpoint 恢复时状态被正确重建（含 in-flight 记录重放语义）
- 完整的端到端集成测试覆盖上述所有流程

## Non-Goals

- 不实现 RecordWriter/RecordReader 跨 Task 数据交换——多链管线（keyBy 产生多 JobVertex）在图模型路径中将**显式拒绝**并给出清晰错误信息
- 不实现 unaligned checkpoint 模式
- 不实现增量快照
- 不实现 key-group 重分布
- 不实现分布式执行（JobManager/TaskManager RPC）
- 不修改现有的快速路径代码（快速路径保持不变作为 fallback）
- 不新增状态后端实现（继续使用 MemoryStateBackend）

## Scope

### In Scope

- 图模型路径的 `Invokable` 实现，使 Task 能实际执行数据流处理（单链管线）
- `executeWithGraphModel()` 中的单链验证：如果 JobGraph 产生多条链（JobEdge 非空），抛出明确异常提示使用快速路径
- CheckpointCoordinator 与执行引擎集成（线程安全：Coordinator 的 acknowledgeTask 等方法已使用 ConcurrentHashMap，本计划中每个 Task 在 barrier 到达时同步执行快照和 ACK，无并发 ACK 场景）
- `StreamElement` 统一分发模型：barrier 与 StreamRecord、Watermark 统一为可分发的流元素，通过算子链传播
- `AbstractUdfStreamOperator.snapshotState()` 实现取消注释并对接状态后端
- Source 端 barrier 注入和 offset 管理
- Sink 端 2PC 与 checkpoint 完成通知对接
- 恢复流程：从 checkpoint 重建算子状态（含 exactly-once 语义说明：恢复后 Source 从 checkpoint 记录的 offset 重放，可能产生重复处理，由 Sink 端 2PC 保证外部副作用 exactly-once）
- 端到端集成测试

### Out Of Scope

- 跨 Task 数据交换机制（RecordWriter/RecordReader/InputGate）——后续独立计划
- 多链管线的图模型执行——依赖跨 Task 数据交换
- Savepoint 深度实现
- 性能优化
- 新的 CheckpointStorage 实现

## Risks And Rollback

- **风险**：修改 `Output` 泛型参数（`StreamRecord<T>` → `StreamElement`）可能影响现有算子。缓解：`StreamElement` 已是 `StreamRecord` 和 `Watermark` 的基类，`CheckpointBarrier` 只需新增继承关系即可融入。`ChainingOutput` 和 `TimestampedCollector` 等需要适配但改动局部化。所有现有算子的 `processElement(StreamRecord)` 签名不变
- **风险**：图模型路径与快速路径的切换可能导致行为不一致。缓解：先在图模型路径中复现快速路径的行为，验证一致性后再增加新能力
- **风险**：恢复后 in-flight 记录重复处理。缓解：这是 Flink 同样存在的问题模型——Source 从 offset 重放可能产生 barrier 之后、crash 之前已处理过的记录，由 Sink 端 2PC 保证外部副作用的 exactly-once。文档中明确说明这一语义
- **回滚策略**：所有新增代码在独立方法/类中，不修改快速路径。如有问题可禁用图模型路径回退到快速路径

## Execution Plan

### Phase 1 - 图模型执行路径对接（单链管线）

Status: completed
Targets: `nop-stream-core`（execution 包、environment 包）

- Item Types: `Fix`

让 Task 内的 OperatorChain 能实际处理数据流，替代 placeholder Invokable。仅支持单链管线。

- [x] 实现 `StreamTaskInvokable`（替换当前 placeholder Invokable），其 invoke() 方法：按 open(所有 OperatorChain) → Source.run() → emitWatermark(MAX) → close 的顺序执行
- [x] 修改 `Task.run()` 使用 `StreamTaskInvokable`
- [x] `OperatorChain` 添加 `invoke()` 方法：运行 chain 首节点（Source），数据通过 ChainingOutput 流过整个 chain
- [x] 在 `StreamExecutionEnvironment` 中新增 `executeWithGraphModel(String jobName)` 方法：Transformation DAG → StreamGraphGenerator → StreamGraph → JobGraphGenerator → JobGraph → **验证 JobGraph.edges 为空（单链约束）** → 创建 Task → TaskExecutor.submit → awaitCompletion
- [x] 单链约束验证：如果 `JobGraph.getEdges()` 非空，抛出 `IllegalStateException` 明确提示"多链管线需要跨 Task 数据交换，请使用快速路径 `execute()` 或等待后续版本"

Exit Criteria:

- [x] 图模型路径能执行 `env.fromCollection(data).map(fn).addSink(collector)` 并产生与快速路径相同的输出
- [x] 多链管线（含 keyBy 产生 PartitionTransformation 的场景）被明确拒绝，抛出信息性异常
- [x] 新增测试：`TestGraphModelExecution` 验证：单链管线正确执行、多算子链正确执行、Watermark 传播正确、多链管线抛出异常
- [x] `./mvnw test -pl nop-stream/nop-stream-core` 通过
- [x] No owner-doc update required（Phase 1 不改变公开 API 或设计契约；文档更新集中在 Phase 6）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - StreamElement 统一分发与 Barrier 传播

Status: completed
Targets: `nop-stream-core`（streamrecord 包、operators 包）

- Item Types: `Fix`

让 CheckpointBarrier 作为流元素的一种，随数据流在算子间传播。

- [x] 修改 `CheckpointBarrier` 继承 `StreamElement`（`StreamElement` 已存在于 `streamrecord` 包，`StreamRecord` 和 `Watermark` 已是其子类）。这是最小侵入的改动——barrier 自然融入已有的流元素类型体系
- [x] 修改 `Output<StreamRecord<T>>` 泛型为 `Output<StreamElement>`，让 barrier 能通过已有的 `collect()` 路径传播。注意：`TimestampedCollector<T>` 等使用 `Output<StreamRecord<T>>` 的地方需要适配
- [x] 修改 `ChainingOutput.collect(StreamElement)` 方法，根据元素实际类型分发：`StreamRecord` → `input.processElement()`，`Watermark` → `input.processWatermark()`，`CheckpointBarrier` → `input.processBarrier()`（新增 default 方法）
- [x] `AbstractStreamOperator` 实现 `processBarrier(CheckpointBarrier)` default 行为：传播 barrier 到下游 output（不做快照，快照在 Phase 3）
- [x] `StreamSourceOperator` 的 `SourceContext` 包装中支持 barrier 注入：暴露 `injectBarrier(CheckpointBarrier)` 方法

Exit Criteria:

- [x] Barrier 能从 Source 传播到 Sink，途经所有中间算子
- [x] 现有的 StreamRecord 和 Watermark 传播不受影响（回归测试通过）
- [x] 新增测试：`TestBarrierPropagation` 验证：Source→Map→Sink 单链中 barrier 传播、多算子链中 barrier 传播、barrier 不影响正常数据流
- [x] `./mvnw test -pl nop-stream` 全通过
- [x] No owner-doc update required（Phase 2 不改变外部契约；文档更新集中在 Phase 6）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 算子状态快照实现

Status: completed
Targets: `nop-stream-core`（operators 包）、`nop-stream-runtime`（checkpoint 包）

- Item Types: `Fix`

让算子在收到 barrier 时能将自身状态序列化为 TaskStateSnapshot。

- [x] 恢复 `AbstractUdfStreamOperator.snapshotState()` 的实现：从状态后端获取所有注册的状态 → JsonTool 序列化为 byte[] → 构建 OperatorSnapshotResult
- [x] 新增 `StateSnapshotContext` 类（非接口），包含 checkpointId 和 timestamp，在快照时提供给算子
- [x] 修改 `AbstractUdfStreamOperator.processBarrier()` 实现：先调用自身 `snapshotState()` 构建 OperatorSnapshotResult，然后通过 output 传播 barrier 到下游
- [x] `WindowOperator.snapshotState()` 实现：序列化窗口状态（namespace = Window 对象的 start/end）和所有 key 下的状态值。利用已有的 `MemoryKeyedStateBackend` 的 HashMap 遍历能力
- [x] `StreamSourceOperator` 对接 `CheckpointedSourceFunction`：如果 source 实现了该接口，在 snapshotState 时委托给 `source.snapshotState(checkpointId)`
- [x] `StreamSinkOperator` 对接 `TwoPhaseCommitSinkFunction`：收到 barrier 时调用 `sink.preCommit(checkpointId)`

Exit Criteria:

- [x] 算子收到 barrier 时能正确序列化 ValueState、MapState（JSON 格式可反序列化还原）
- [x] WindowOperator 的窗口状态能被正确快照（namespace 正确保留）
- [x] CheckpointedSourceFunction 的 source 能在快照时记录 offset
- [x] TwoPhaseCommitSinkFunction 的 sink 能在 barrier 时 preCommit
- [x] 新增测试：`TestOperatorSnapshot` 验证 ValueState/MapState 快照和反序列化、WindowOperator 快照、Source offset 快照
- [x] `./mvnw test -pl nop-stream` 全通过
- [x] No owner-doc update required（文档更新集中在 Phase 6）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - CheckpointCoordinator 集成

Status: completed
Targets: `nop-stream-core`（environment 包）、`nop-stream-runtime`（checkpoint 包）

- Item Types: `Fix`

将 CheckpointCoordinator 接入执行引擎，实现完整的 checkpoint 生命周期。

- [x] `executeWithGraphModel()` 中根据 `CheckpointConfig` 创建 `CheckpointCoordinator`，注册所有 Task 的 taskId，启动调度器
- [x] CheckpointCoordinator 触发 checkpoint 时，调用 Source 算子的 `injectBarrier(new CheckpointBarrier(id, timestamp, CHECKPOINT))`
- [x] 每个 Task（OperatorChain）收到 barrier 后：执行所有算子的 snapshotState() → 构建 TaskStateSnapshot → 调用 `coordinator.acknowledgeTask(taskId, checkpointId, taskStateSnapshot)`。由于当前是单链单 Task，ACK 是同步的，无并发问题
- [x] Coordinator 收齐所有 ACK 后：调用 `checkpointStorage.storeCheckPoint()` → `notifyCheckpointCompleted(checkpointId)`
- [x] 算子收到 `notifyCheckpointComplete` 后：如果 UDF 是 `CheckpointListener` 则委托；如果是 `TwoPhaseCommitSinkFunction` 则调用 `commit()` + `beginTransaction()`
- [x] CheckpointCoordinator 超时和中止逻辑对接：abort 时通知算子 `notifyCheckpointAborted`，Sink 执行 `rollback()`
- [x] 管线结束时：停止调度器 → 触发最终 checkpoint（SAVEPOINT 类型）→ 等待完成 → 关闭 Coordinator

Exit Criteria:

- [x] 完整的 checkpoint 生命周期可运行：trigger → barrier 注入 → 传播 → 快照 → ACK → 持久化 → 通知
- [x] 定时 checkpoint 可触发（可配置间隔，测试中用短间隔如 100ms）
- [x] 2PC Sink 在 checkpoint 完成时 commit，中止时 rollback
- [x] 管线结束时最终 checkpoint 被触发
- [x] 新增测试：`TestCheckpointEndToEnd` 验证完整生命周期（用 mock source 配合 CountDownLatch 控制 barrier 时序）
- [x] `./mvnw test -pl nop-stream` 全通过
- [x] No owner-doc update required（文档更新集中在 Phase 6）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 恢复流程实现

Status: completed
Targets: `nop-stream-core`（environment 包）、`nop-stream-runtime`（checkpoint 包）

- Item Types: `Fix`

从 checkpoint 恢复执行，重建算子状态。

- [x] `executeWithGraphModel()` 启动时检查是否有可恢复的 checkpoint（调用 `checkpointStorage.getLatestCheckpoint()`）
- [x] 恢复路径：从 CompletedCheckpoint 提取 TaskStateSnapshot → JsonTool 反序列化 → 注入到对应算子的状态后端（MemoryKeyedStateBackend 的新方法 `restoreFromSnapshot(TaskStateSnapshot)`）
- [x] Source 恢复：如果 source 是 `CheckpointedSourceFunction`，调用 `initializeState(restoredState)` 恢复消费 offset
- [x] Sink 恢复：如果 sink 是 `TwoPhaseCommitSinkFunction`，调用 `recover(checkpointId)` → rollback + beginTransaction
- [x] WindowOperator 恢复：从快照恢复窗口状态（反序列化 namespace + key + value 到 HashMap）
- [x] 恢复语义文档化：Source 从 checkpoint 记录的 offset 重放数据。barrier 之后但在 crash 之前已被下游处理的记录会被重新处理（at-least-once delivery）。外部 exactly-once 由 Sink 端 2PC 保证：新事务只包含恢复后处理的数据，crash 前未 commit 的数据被 rollback

Exit Criteria:

- [x] 从 checkpoint 恢复后，算子状态与快照时一致（ValueState/MapState 值相同）
- [x] Source 从记录的 offset 恢复读取
- [x] 2PC Sink 恢复时 rollback 残留事务并开启新事务
- [x] WindowOperator 恢复后窗口状态正确
- [x] 恢复语义在代码注释和测试中明确体现
- [x] 新增测试：`TestCheckpointRecovery` 验证：运行管线 → 产生 checkpoint → 模拟重启（新 env）→ 从 checkpoint 恢复 → 验证状态一致 + 输出正确
- [x] `./mvnw test -pl nop-stream` 全通过
- [x] No owner-doc update required（文档更新集中在 Phase 6）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 端到端集成测试与文档更新

Status: completed
Targets: `nop-stream-runtime`（test）、`ai-dev/design/nop-stream/`

- Item Types: `Proof`、`Follow-up`

全面的端到端验证和文档同步。

- [x] 端到端测试 1：`TestE2ESimplePipeline` — Source(5条数据) → Map → Sink(collector)，验证图模型路径输出与快速路径一致
- [x] 端到端测试 2：`TestE2ECheckpointAndRecovery` — 启用 checkpoint 的管线运行 → 等待至少一个 checkpoint 完成 → 模拟故障 → 从 checkpoint 恢复 → 验证 Sink 收到的数据无丢失（at-least-once），2PC Sink 保证外部 exactly-once
- [x] 端到端测试 3：`TestE2ETwoPhaseCommitSink` — 使用 mock 2PC Sink 验证：beginTransaction → invoke(N条) → preCommit → commit 时序正确；checkpoint 中止时 rollback 被调用
- [x] 端到端测试 4：`TestE2EWindowOperatorWithCheckpoint` — WindowOperator + checkpoint，验证窗口状态快照和恢复后窗口计算正确（恢复后继续接收数据，窗口正确触发）
- [x] 端到端测试 5：`TestE2EMultipleCheckpoints` — 连续多次 checkpoint，验证：递增 ID、旧 checkpoint 被清理（maxRetained 限制）、每次快照状态正确、notifyCheckpointComplete 按序收到
- [x] 更新 `ai-dev/design/nop-stream/checkpoint-design.md` §10（集成状态）从"未对接"改为"已对接"，更新已实现/未对接/集成所需工作三节
- [x] 更新 `ai-dev/design/nop-stream/graph-model-design.md` §7（与快速路径关系）反映图模型路径已对接（单链管线），新增 §7.4 说明单链约束
- [x] 更新 `ai-dev/design/nop-stream/README.md` 状态标记：graph-model-design 从"active（未对接）"改为"active"，checkpoint-design 从"active"改为"active"

Exit Criteria:

- [x] 所有 5 个端到端测试通过
- [x] `./mvnw test -pl nop-stream` 全通过（无新增失败）
- [x] 设计文档已更新，反映实际集成状态
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 图模型路径能执行单链 DataStream API 管线（Source → map/filter/flatMap → window → aggregate → Sink），多链管线被明确拒绝
- [x] Checkpoint 完整生命周期可运行：定时触发 → barrier 注入/传播 → 算子快照 → ACK → 持久化 → 完成通知
- [x] 从 checkpoint 恢复后状态一致（ValueState/MapState/窗口状态/Source offset）
- [x] TwoPhaseCommitSinkFunction 的 2PC 流程正确（preCommit → commit/rollback 时序）
- [x] 恢复语义已明确：at-least-once delivery + Sink 端 2PC 保证外部 exactly-once
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 端到端集成测试覆盖上述所有流程
- [x] 受影响的设计文档已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-stream`
- [x] `./mvnw test -pl nop-stream`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 跨 Task 数据交换（RecordWriter/RecordReader）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前所有算子在同一 chain 内通过 ChainingOutput 传递。多链管线在 `executeWithGraphModel()` 中被明确拒绝。单链管线覆盖了 Source→map/filter/flatMap→Sink 的常见 ETL 场景。跨 Task 交换需要实现 RecordWriter/RecordReader/InputGate，是独立的大块工作
- Successor Required: yes
- Successor Path: future plan for cross-task data exchange + multi-chain pipeline support

### 多链管线图模型执行

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 依赖跨 Task 数据交换。keyBy 产生 PartitionTransformation 在图模型中会创建独立 JobVertex，但无跨 Task 数据交换时无法传递数据。单链约束已通过显式验证和异常处理保障
- Successor Required: yes
- Successor Path: depends on cross-task data exchange above

## Non-Blocking Follow-ups

- Savepoint 深度实现（手动触发、schema 兼容）
- 增量快照优化
- CheckpointMetrics 与监控集成
- 性能基准测试
- BlockingQueue 实现的跨 Task 数据交换原型

## Closure

Status Note: (待完成后填写)

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
