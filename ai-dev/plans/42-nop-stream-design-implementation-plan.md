# 42 nop-stream 设计实现计划

> Plan Status: proposed
> Last Reviewed: 2026-05-23
> Source: nop-stream 设计文档全量更新（2026-05-19 ~ 2026-05-23），合并分布式 exactly-once 设计
> Related: Plan 41 (code-gap-remediation, completed), `ai-dev/design/nop-stream/architecture.md`, `ai-dev/design/nop-stream/component-roadmap.md`

## Purpose

将 `ai-dev/design/nop-stream/` 下已定稿的分布式 exactly-once 设计落地为代码。设计文档引入了 StreamComponents、StreamRequirement、StateShard、PartitionedPlan、CheckpointParticipant、ProcessingGuarantee、SourceWorkUnit、EdgeConfig、MemoryBudget、WindowingStrategy、JobTerminationMode 等全新概念，现有代码中均不存在这些类。计划按依赖关系从底层模型到运行时分阶段实施，每个阶段独立可测试。

## Current Baseline

**已成立的事实**（截至 Plan 41 completion）：

- 核心流式 API（DataStream / KeyedStream / Transformation DAG）已稳定
- 四层编译管线（Transformation → StreamGraph → JobGraph → GraphExecutionPlan）已实现
- CheckpointPlan / TaskLocation / OperatorStateMapping 已存在于 core 模块
- CheckpointCoordinator / CheckpointPlanBuilder 已存在于 runtime 模块
- MemoryStateBackend / MemoryKeyedStateBackend 已实现
- 算子链化（ChainingOutput）和 RecordWriter 分区已修复
- CEP 引擎（NFA / SharedBuffer / Pattern API）基本成熟
- 连接器适配器（BatchLoaderSource / BatchConsumerSink / MessageSource / MessageSink / DebeziumCdc）已实现

**真正剩余的 gap**（设计文档已定义，代码中不存在）：

| 概念 | 所在模块（设计） | 代码是否存在 |
|---|---|---|
| `StreamComponents` | core | 否 |
| `StreamRequirement` | core | 否 |
| `StreamModel` | core | 否 |
| `StreamModelFingerprint` | core | 否 |
| `StreamBackendCapability` | core | 否 |
| `StateShard` | core | 否 |
| `StatePath` | core | 否 |
| `State Segment` 化快照 | core | 否 |
| `PartitionedPlan` | core | 否 |
| `DeploymentPlan` | core | 否 |
| `CheckpointParticipant` | core | 否 |
| `ProcessingGuarantee` | core | 否 |
| `EpochManifest` | checkpoint | 否 |
| `SourceWorkUnit` | connector | 否 |
| `RestrictionTracker` | connector | 否 |
| `WatermarkEstimator` | connector | 否 |
| `EdgeConfig` / `FlowControlPolicy` | core | 否 |
| `MemoryBudget` | core | 否 |
| `WindowingStrategy` | core | 否 |
| `AccumulationMode` | core | 否 |
| `PaneState` | core | 否 |
| `JobTerminationMode` | core | 否 |
| `JobTerminationContext` | core | 否 |
| `CheckpointType` 扩展 | core | 部分存在 |

## Goals

- 将设计文档中的模型层概念落地为接口和数据类（StreamComponents、StreamRequirement、StateShard、StatePath 等）
- 实现 PartitionedPlan / DeploymentPlan 编译层
- 实现 CheckpointParticipant 协议和 ProcessingGuarantee 四级保证
- 实现 SourceWorkUnit / RestrictionTracker 连接器协议
- 实现 EdgeConfig / MemoryBudget 流控模型
- 实现 WindowingStrategy / AccumulationMode 窗口模型
- 实现 JobTerminationMode 四种终止模式

## Non-Goals

- 不实现分布式 runtime（RuntimeNode / TaskAttempt / NodeLease / ClusterRegistry / fencing token）
- 不实现 remote state service 或 RocksDB/Redis 后端
- 不实现 rescale / state redistribution
- 不实现 CEP operator 对接（已有计划，见 component-roadmap 阶段 4）
- 不实现 nop-stream-flow（XDSL 编排）
- 不实现 nop-stream-flink（外部后端适配）
- 不修改现有已正确的代码逻辑

## Scope

### In Scope

- 7 个阶段的新增模型和接口定义
- 将新增模型集成到现有编译管线
- 适配现有 CheckpointCoordinator / CheckpointBarrierTracker 到新协议
- 为每个阶段编写单元测试和集成测试

### Out Of Scope

- 分布式 runtime 实现
- 外部引擎适配
- 已由 Plan 41 修复的现有 bug

## Risks

| 风险 | 影响 | 缓解 |
|---|---|---|
| 新增模型与现有编译管线的集成点不明确 | 中 | 阶段 0 优先明确定义 StreamModel 如何替代现有 Transformation DAG 入口 |
| CheckpointParticipant 与现有 TwoPhaseCommitSinkFunction 的关系 | 中 | CheckpointParticipant 是泛化接口，TwoPhaseCommitSinkFunction 实现它；保持向后兼容 |
| StateShard 引入后 MemoryKeyedStateBackend 需重构 | 中 | 先在 StateShard 层做路由映射，不改变内部 HashMap 结构 |
| 阶段数量多，执行周期长 | 高 | 每个阶段独立可测试，可按优先级中断；阶段 0-2 是核心路径 |

## Execution Plan

### Phase 0 - 基础设施：StreamComponents、StreamRequirement、稳定身份

Status: planned
Targets: `nop-stream-core`

- Item Types: `Decision`, `Proof`

**目标**：建立模型层基础，使所有后续阶段有统一注册表和能力声明机制。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `StreamModel` | `io.nop.stream.core.model` | canonical 顶层模型，持有 StreamComponents 和 Transformation DAG |
| `StreamComponents` | `io.nop.stream.core.model` | 统一组件注册表（transforms, streams, windowingStrategies, coders, schemas, environments, sideInputs, requirements, checkpointParticipants） |
| `StreamRequirement` | `io.nop.stream.core.model` | 能力声明枚举（22 个值，见 core-design.md §2.3） |
| `StreamBackendCapability` | `io.nop.stream.core.model` | Backend 能力声明 |
| `StreamModelFingerprint` | `io.nop.stream.core.model` | StreamModel 结构指纹 |
| `TaskLocation` | 已存在，需扩展 | 增加 `pipelineId` 字段支持（当前只有 jobId/vertexId/taskIndex） |
| `CheckpointType` | 已存在，需扩展 | 增加 `TERMINAL_SAVEPOINT`, `EXPORTED_SAVEPOINT`, `COMPLETED_POINT_TYPE` |

**受影响的模块**：
- `nop-stream-core`（新增 model 包，扩展 CheckpointType）
- `nop-stream-runtime`（适配 StreamExecutionEnvironment 使用 StreamModel 入口）

**工作项**：

- [ ] 新增 `io.nop.stream.core.model.StreamModel` 数据类
- [ ] 新增 `io.nop.stream.core.model.StreamComponents` 数据类（含 Map<String, ?> 注册表和 requirements/checkpointParticipants 列表）
- [ ] 新增 `io.nop.stream.core.model.StreamRequirement` 枚举
- [ ] 新增 `io.nop.stream.core.model.StreamBackendCapability` 数据类
- [ ] 新增 `io.nop.stream.core.model.StreamModelFingerprint` 数据类（含 SHA256 计算）
- [ ] 扩展 `CheckpointType` 增加 TERMINAL_SAVEPOINT / EXPORTED_SAVEPOINT / COMPLETED_POINT_TYPE
- [ ] 修改 `StreamExecutionEnvironment` 的 `execute()` 方法，在执行前构建 StreamModel（包装现有 Transformation DAG）
- [ ] 编译时 requirement 校验：StreamCompiler 检查 source/sink 能力与 pipeline requirement 是否匹配
- [ ] 测试：StreamComponents 注册/查询、StreamRequirement 校验成功/失败、Fingerprint 计算一致性

**验收标准**：

- StreamComponents 可以注册和按 ID 查询各类组件
- StreamRequirement 校验在 source 能力不满足时抛出异常
- StreamModelFingerprint 对同一 StreamModel 产生相同 hash
- 现有端到端测试（source → map → sink）不因 StreamModel 引入而中断
- execute() 路径从构建 StreamModel 到编译管线端到端跑通

---

### Phase 1 - 状态管理：StateShard、StatePath、State Segment

Status: planned
Targets: `nop-stream-core`

- Item Types: `Decision`, `Proof`

**目标**：建立分布式状态路由基础，使 keyed state 具有确定性的分片和路径。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `StateShard` | `io.nop.stream.core.common.state.shard` | 状态分片描述（stateShardCount, stateShardId, ownerSubtask, hashPolicy） |
| `StatePath` | `io.nop.stream.core.common.state.shard` | 状态持久化路径（静态工厂方法按类型生成路径） |
| `StateSegmentDescriptor` | `io.nop.stream.core.checkpoint` | 状态段描述符（segment type, path, codec, checksum, schemaVersion） |
| `TaskEpochSnapshot` | `io.nop.stream.core.checkpoint` | Task 级 epoch 快照（operator snapshots, keyed state shards, timer state, watermark state, source split state, sink transaction state, participant states） |

**受影响的模块**：
- `nop-stream-core`（新增 shard 包，扩展 checkpoint 包）
- `nop-stream-runtime`（适配 CheckpointCoordinator 使用 StateSegment）

**工作项**：

- [ ] 新增 `StateShard` 数据类，实现 `stableHash(key) mod stateShardCount` 路由
- [ ] 新增 `StatePath` 工具类，提供 keyed/non-keyed/source/sink 路径生成
- [ ] 新增 `StateSegmentDescriptor` 数据类
- [ ] 新增 `TaskEpochSnapshot` 数据类（替换现有 `TaskStateSnapshot` 的角色，增加 shard/timer/participant 维度）
- [ ] 修改 `MemoryKeyedStateBackend` 支持按 StateShard 路由存储
- [ ] 确认 StatePath 不包含 deploymentId/runId/attemptId
- [ ] 测试：StateShard 路由确定性、StatePath 格式合规、多 key 分片隔离、序列化 round-trip

**验收标准**：

- StateShard 对同一 key 在不同 JVM 实例中产生相同 stateShardId
- StatePath 格式符合设计文档规范（不含运行时临时身份）
- 状态快照按 operatorId + subtaskIndex + stateShardId + stateName 精确分段
- 现有状态后端测试全部通过

---

### Phase 2 - Checkpoint：Epoch 协议、CheckpointParticipant、ProcessingGuarantee

Status: planned
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：实现分布式 exactly-once 的核心协议——Epoch 生命周期、泛化事务参与、四级处理保证。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `CheckpointParticipant` | `io.nop.stream.core.checkpoint.participant` | 泛化事务参与接口（saveState, prepareCommit, finishCommit, restoreFromEpoch） |
| `ProcessingGuarantee` | `io.nop.stream.core.checkpoint` | 处理保证枚举（STRICT_EXACTLY_ONCE, AT_LEAST_ONCE, EFFECTIVELY_ONCE, BEST_EFFORT） |
| `EpochState` | `io.nop.stream.core.checkpoint` | Epoch 生命周期状态枚举（CREATED → INJECTING → ALIGNING → SNAPSHOTTING → PRECOMMITTED → DURABLE → COMMITTED） |
| `EpochManifest` | `io.nop.stream.core.checkpoint` | Epoch manifest 数据类 |
| `SourceConsistencyCapability` | `io.nop.stream.core.common.functions.source` | Source 一致性能力枚举（REPLAYABLE, TRANSACTIONAL_READ, AT_LEAST_ONCE, BEST_EFFORT） |
| `SinkConsistencyCapability` | `io.nop.stream.core.common.functions.sink` | Sink 一致性能力枚举（TWO_PHASE_COMMIT, STAGED_ATOMIC_COMMIT, OUTBOX_EPOCH_LOG, IDEMPOTENT, UPSERT_BY_KEY, AT_LEAST_ONCE, BEST_EFFORT） |
| `JobTerminationMode` | `io.nop.stream.core.checkpoint` | 作业终止模式枚举（CANCEL, DRAIN, SUSPEND, EXPORT_SAVEPOINT） |
| `JobTerminationContext` | `io.nop.stream.core.checkpoint` | 终止上下文（mode, timeout, waitForSinkCommit, savepointNamespace, abortTransactions） |

**受影响的模块**：
- `nop-stream-core`（新增 participant 包，扩展 checkpoint/functions 包）
- `nop-stream-runtime`（适配 CheckpointCoordinator 使用 EpochManifest 和 CheckpointParticipant）
- `nop-stream-connector`（Source/Sink 声明一致性能力）

**工作项**：

- [ ] 新增 `CheckpointParticipant` 接口
- [ ] 新增 `ProcessingGuarantee` 枚举
- [ ] 新增 `EpochState` 枚举和 `EpochManifest` 数据类
- [ ] 新增 `SourceConsistencyCapability` 和 `SinkConsistencyCapability` 枚举
- [ ] 新增 `JobTerminationMode` 枚举和 `JobTerminationContext` 数据类
- [ ] 修改 `TwoPhaseCommitSinkFunction` 实现 `CheckpointParticipant`
- [ ] 修改 `CheckpointCoordinator`：使用 EpochManifest 替代 CompletedCheckpoint 作为持久化对象
- [ ] 修改 `CheckpointCoordinator`：按 CheckpointPlan.checkpointParticipants 调用 saveState/prepareCommit/finishCommit
- [ ] 实现 ProcessingGuarantee 对 barrier 行为的影响（STRICT_EXACTLY_ONCE 阻塞已收到 barrier channel；AT_LEAST_ONCE 不阻塞）
- [ ] 扩展 `CheckpointPlan` 增加 `checkpointParticipants` 和 `processingGuarantee` 字段
- [ ] 修改 `CheckpointPlanBuilder` 从 StreamComponents.checkpointParticipants 提取 participant 列表
- [ ] 实现 exactly-once 静态校验：source 能力 + sink 能力 + operatorId 稳定性 + state descriptor 完整性
- [ ] 测试：CheckpointParticipant 生命周期、多 participant 调用顺序（拓扑序 save/逆拓扑序 finish）、ProcessingGuarantee barrier 行为差异、静态校验成功/失败

**验收标准**：

- CheckpointParticipant 四阶段方法按正确顺序调用
- TwoPhaseCommitSinkFunction 通过 CheckpointParticipant 接口参与 checkpoint
- STRICT_EXACTLY_ONCE 模式下 barrier 对齐正确阻塞/放行
- 不满足 source/sink 能力要求时，STRICT_EXACTLY_ONCE 编译失败
- EpochManifest JSON round-trip 正确
- Barrier 注入路径从 source 读取线程触发（不从 scheduler 线程）
- 端到端验证：source → operator → sink 的完整 checkpoint 周期（触发 → 对齐 → 快照 → 持久化 → commit）

---

### Phase 3 - Source/Sink：SourceWorkUnit、连接器协议升级

Status: planned
Targets: `nop-stream-connector`, `nop-stream-core`

- Item Types: `Decision`, `Proof`

**目标**：将 source/sink 升级到分布式协议，支持动态拆分、进度追踪、watermark 状态恢复和 drain 截断。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `SourceWorkUnit` | `io.nop.stream.core.connector` | Source 工作单元（sourceId, splitId, restriction, owner, sizeEstimate, progress, watermarkEstimatorState） |
| `RestrictionTracker<R>` | `io.nop.stream.core.connector` | 进度追踪和游标管理（tryClaim, getRestriction, getProgress, snapshotWatermarkEstimatorState） |
| `DynamicSplitRequest` | `io.nop.stream.core.connector` | 动态拆分请求（fraction） |
| `DynamicSplitResponse<R>` | `io.nop.stream.core.connector` | 动态拆分响应（primary + residual） |
| `DrainableSource<R>` | `io.nop.stream.core.connector` | 可 drain 的 source 接口（truncateForDrain） |
| `WatermarkEstimator` | `io.nop.stream.core.connector` | Watermark 估计器（observe, getCurrentWatermark, snapshotState, restoreState） |
| `SourceEnumeratorState` | `io.nop.stream.core.checkpoint` | Source 枚举器状态（discoveredSplits, unassignedSplits, assignedSplits, finishedSplits, pendingAcknowledgements, discoveryCursor） |

**受影响的模块**：
- `nop-stream-core`（新增 connector 包定义协议接口）
- `nop-stream-connector`（适配器升级实现新协议）
- `nop-stream-runtime`（SourceWorkUnit 管理和 split assignment）

**工作项**：

- [ ] 在 core 模块新增 connector 包，定义 SourceWorkUnit / RestrictionTracker / DynamicSplitRequest / DynamicSplitResponse / DrainableSource / WatermarkEstimator 接口
- [ ] 新增 `SourceEnumeratorState` 数据类
- [ ] 修改 `BatchLoaderSourceFunction` 声明 `SourceConsistencyCapability.AT_LEAST_ONCE`
- [ ] 修改 `BatchConsumerSinkFunction` 声明 `SinkConsistencyCapability.IDEMPOTENT`
- [ ] 修改 `MessageSourceFunction` 实现 `CheckpointParticipant`（saveState 快照 offset, finishCommit ack offset, restoreFromEpoch 恢复订阅）
- [ ] 修改 `MessageSinkFunction` 声明 `SinkConsistencyCapability.AT_LEAST_ONCE`（可升级为 TWO_PHASE_COMMIT 如果 Pulsar 支持）
- [ ] 修改 `DebeziumCdcSourceFunction` 实现 `DrainableSource` 和声明 `SourceConsistencyCapability.REPLAYABLE`
- [ ] 测试：SourceWorkUnit 序列化、RestrictionTracker 进度追踪、WatermarkEstimator 状态快照/恢复、连接器能力声明与 requirement 校验交互

**验收标准**：

- 所有 5 个现有连接器声明正确的一致性能力
- MessageSourceFunction 通过 CheckpointParticipant 在 checkpoint 时保存和恢复 offset
- WatermarkEstimator 状态可通过 JSON 序列化/反序列化
- 连接器能力声明与 ProcessingGuarantee 校验正确交互（如 REPLAYABLE source 才允许 STRICT_EXACTLY_ONCE）
- 现有连接器端到端测试不受影响

---

### Phase 4 - FlowControl：EdgeConfig、MemoryBudget

Status: planned
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：建立分布式 edge 流控和内存预算模型，为 DeploymentPlan 提供资源规划基础。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `EdgeConfig` | `io.nop.stream.core.execution.flow` | Edge 流控配置（flowControlPolicy, queueCapacity, receiveWindow, packetSize） |
| `FlowControlPolicy` | `io.nop.stream.core.execution.flow` | 流控策略枚举（BLOCKING_QUEUE, CREDIT_BASED, ACK_WINDOW） |
| `MemoryBudget` | `io.nop.stream.core.execution.flow` | 内存预算配置（totalBytes, componentAllocations） |
| `MemoryBudgetMonitor` | `io.nop.stream.runtime.execution.flow` | 运行时内存预算监控（actualUsage 跟踪，超预算策略） |

**受影响的模块**：
- `nop-stream-core`（新增 flow 包）
- `nop-stream-runtime`（实现 MemoryBudgetMonitor，适配 RecordWriter/InputGate 使用 EdgeConfig）

**工作项**：

- [ ] 新增 `FlowControlPolicy` 枚举
- [ ] 新增 `EdgeConfig` 数据类
- [ ] 新增 `MemoryBudget` 数据类（含默认分配比例计算逻辑）
- [ ] 新增 `MemoryBudgetMonitor` 运行时类
- [ ] 修改 `JobEdge` 增加 `EdgeConfig` 字段
- [ ] 修改 `RecordWriter` 和 `InputGate` 根据 EdgeConfig.flowControlPolicy 选择流控行为
- [ ] 测试：EdgeConfig 配置传递、BLOCKING_QUEUE 策略下队列满时阻塞、MemoryBudget 分配计算

**验收标准**：

- EdgeConfig 可序列化且通过 JobEdge 传递到 RecordWriter
- BLOCKING_QUEUE 策略下队列满时 sender 阻塞
- MemoryBudget 默认分配比例正确（state 50%, edge queue, network buffer）
- 本地 runtime 默认使用 BLOCKING_QUEUE 策略，不影响现有测试

---

### Phase 5 - Window：WindowingStrategy、AccumulationMode、PaneState

Status: planned
Targets: `nop-stream-core`

- Item Types: `Decision`, `Proof`

**目标**：将窗口模型从运行时算子实现提升为可注册、可引用的模型层概念，支持跨 backend 校验。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `WindowingStrategy` | `io.nop.stream.core.windowing` | 窗口策略（windowFn, trigger, allowedLateness, accumulationMode, timestampAssigner, gapPolicy） |
| `AccumulationMode` | `io.nop.stream.core.windowing` | 累积模式枚举（DISCARDING, ACCUMULATING, ACCUMULATING_AND_RETRACTING） |
| `PaneState` | `io.nop.stream.core.windowing` | 窗口 Pane 状态（paneInfo, window, timestamp, state） |
| `PaneInfo` | `io.nop.stream.core.windowing` | Pane 元信息（index, isFirst, isLast, timing） |

**受影响的模块**：
- `nop-stream-core`（扩展 windowing 包）
- `nop-stream-runtime`（WindowOperator 使用 WindowingStrategy 而非直接持有 Trigger/WindowAssigner）

**工作项**：

- [ ] 新增 `AccumulationMode` 枚举
- [ ] 新增 `WindowingStrategy` 数据类（注册到 StreamComponents）
- [ ] 新增 `PaneInfo` 和 `PaneState` 数据类
- [ ] 修改 `WindowedStreamImpl` 持有 `windowingStrategyId` 而非直接内联窗口参数
- [ ] 修改窗口算子从 StreamComponents 查找 WindowingStrategy
- [ ] 实现 AccumulationMode 对窗口输出的影响（DISCARDING 每次清空, ACCUMULATING 累积所有元素）
- [ ] 测试：WindowingStrategy 注册/查找、AccumulationMode 行为差异、PaneState 序列化

**验收标准**：

- WindowingStrategy 可通过 StreamComponents 注册和按 ID 引用
- DISCARDING 模式下每次窗口触发只输出当前窗口内容
- ACCUMULATING 模式下窗口触发累积所有历史元素
- WindowingStrategy 变更触发 Fingerprint 不兼容（savepoint 恢复时检测）
- 现有窗口聚合测试全部通过

---

### Phase 6 - PartitionedPlan / DeploymentPlan 编译层

Status: planned
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：实现五层执行管线的后两层——PartitionedPlan 和 DeploymentPlan，为分布式执行提供语义计划。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `PartitionedPlan` | `io.nop.stream.core.execution.plan` | 并行展开语义计划（vertex parallelism, operatorId, stateShard 分配, edge partition policy, checkpoint ACK 集合） |
| `DeploymentPlan` | `io.nop.stream.core.execution.plan` | 部署计划（partitioned task → runtime node 映射, transport backend, state backend binding, checkpoint storage, EdgeConfig, MemoryBudget） |
| `PartitionPolicy` | `io.nop.stream.core.execution.plan` | 分区策略枚举（FORWARD, HASH, REBALANCE, BROADCAST, UNION, SINGLETON） |
| `PartitionedPlanGenerator` | `io.nop.stream.core.graph` | JobGraph → PartitionedPlan 编译器 |
| `DeploymentPlanGenerator` | `io.nop.stream.runtime.execution` | PartitionedPlan → DeploymentPlan 编译器 |

**受影响的模块**：
- `nop-stream-core`（新增 plan 包，新增 generator）
- `nop-stream-runtime`（新增 DeploymentPlanGenerator，适配 TaskExecutor 使用 DeploymentPlan）

**工作项**：

- [ ] 新增 `PartitionPolicy` 枚举
- [ ] 新增 `PartitionedPlan` 数据类（含 fingerprint 计算，引用 StreamModelFingerprint）
- [ ] 新增 `DeploymentPlan` 数据类
- [ ] 实现 `PartitionedPlanGenerator`：从 JobGraph 提取并行度、分区策略、状态分片分配、checkpoint ACK 集合
- [ ] 实现 `DeploymentPlanGenerator`：为本地 runtime 生成单节点 DeploymentPlan
- [ ] 修改执行管线：`StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology`
- [ ] PartitionedPlan 的 JSON 序列化（必须可持久化和可对比）
- [ ] 测试：PartitionedPlan 生成正确性、单节点 DeploymentPlan 生成、fingerprint 计算、JSON round-trip

**验收标准**：

- PartitionedPlan 记录每个 vertex 的 parallelism、每个 operator 的稳定 operatorId、每条边的 partition policy、每个 state shard 的 owner subtask、checkpoint ACK 集合
- PartitionedPlan 的 fingerprint 包含 StreamModelFingerprint + 并行度 + 分区策略 + 状态路由
- PartitionedPlan JSON round-trip 无损
- 本地单线程执行是 DeploymentPlan 的一种 backend（并行度=1，FORWARD/UNION 分区）
- 现有端到端测试通过新的编译管线成功执行

---

## Phase Status

| Phase | 名称 | Status |
|---|---|---|
| 0 | 基础设施：StreamComponents、StreamRequirement、稳定身份 | planned |
| 1 | 状态管理：StateShard、StatePath、State Segment | planned |
| 2 | Checkpoint：Epoch 协议、CheckpointParticipant、ProcessingGuarantee | planned |
| 3 | Source/Sink：SourceWorkUnit、连接器协议升级 | planned |
| 4 | FlowControl：EdgeConfig、MemoryBudget | planned |
| 5 | Window：WindowingStrategy、AccumulationMode、PaneState | planned |
| 6 | PartitionedPlan / DeploymentPlan 编译层 | planned |

## Closure Gates

- [ ] 所有 Phase 的验收标准全部满足
- [ ] 现有端到端测试（source → map → sink）通过
- [ ] 新增模型类全部可 JSON round-trip
- [ ] 设计不变量（architecture.md §9）全部在代码中体现
- [ ] `ai-dev/logs/` 记录每个 Phase 的执行日志

## 与 component-roadmap.md 的对齐

本计划与 `component-roadmap.md` 的开发路线有以下对齐和调整：

| component-roadmap 阶段 | 本计划对应 | 调整说明 |
|---|---|---|
| 阶段 1（C4 状态管理 + C3 算子修复） | 已由 Plan 37/41 完成 | 本计划跳过 |
| 阶段 2（C1 API 粘合层） | 已由 Plan 41 确认完成 | 本计划跳过 |
| 阶段 3（C5 Checkpoint 生产化） | 本计划 Phase 2 | 扩展为包含 CheckpointParticipant/ProcessingGuarantee/EpochManifest |
| 阶段 4（C6 CEP 完善） | 不在本计划范围 | CEP 对接标准状态后端是独立工作，见 component-roadmap |
| 阶段 5（C7 连接器增强） | 本计划 Phase 3 | 扩展为包含 SourceWorkUnit/RestrictionTracker/一致性能力声明 |
| 阶段 6（C2 编译管线增强） | 本计划 Phase 6 | 扩展为包含 PartitionedPlan/DeploymentPlan |
| 新增（设计文档引入的全新概念） | 本计划 Phase 0/1/4/5 | StreamComponents/StateShard/EdgeConfig/WindowingStrategy 是设计文档新增概念，在 component-roadmap 中未覆盖 |
