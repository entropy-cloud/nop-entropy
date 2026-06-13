# 42 nop-stream 设计实现计划

> Plan Status: superseded
> Superseded By: Plan 32（checkpoint 子系统）、Plans 43-87（审计修复）、Plan 96（本裁定）
> Last Reviewed: 2026-05-23
> Source: nop-stream 设计文档全量更新（2026-05-19 ~ 2026-05-23），合并分布式 exactly-once 设计，对抗性审查修复（P0-1 ~ P0-11, P1-1 ~ P1-2, P2-1 ~ P2-6, P3-1 ~ P3-7）
> Related: Plan 41 (code-gap-remediation, completed), `ai-dev/design/nop-stream/architecture.md`, `ai-dev/design/nop-stream/component-roadmap.md`

## Purpose

将 `ai-dev/design/nop-stream/` 下已定稿的分布式 exactly-once 设计落地为代码。设计文档引入了 StreamComponents、StreamRequirement、StateShard、PartitionedPlan、CheckpointParticipant、ProcessingGuarantee、SourceWorkUnit、EdgeConfig、MemoryBudget、WindowingStrategy、JobTerminationMode 等全新概念，现有代码中均不存在这些类。计划按依赖关系从底层模型到运行时分阶段实施，每个阶段独立可测试。

## Current Baseline

**已成立的事实**（截至 Plan 41 completion）：

- 核心流式 API（DataStream / KeyedStream / Transformation DAG）已稳定
- 三层编译管线（Transformation → StreamGraph → JobGraph → GraphExecutionPlan）已实现
- CheckpointPlan / TaskLocation（已含 jobId/pipelineId/vertexId/taskIndex 四字段）/ OperatorStateMapping 已存在于 core 模块
- CheckpointCoordinator / CheckpointPlanBuilder 已存在于 runtime 模块
- CheckpointType 已含 CHECKPOINT / SAVEPOINT / COMPLETED_POINT_TYPE 三个枚举值
- TwoPhaseCommitSinkFunction 是 interface（extends SinkFunction），含 beginTransaction/invoke/preCommit/commit/rollback/recover 方法
- MemoryStateBackend / MemoryKeyedStateBackend 已实现（MemoryKeyedStateBackend 内部使用 HashMap，无 shard 维度）
- 算子链化（ChainingOutput）和 RecordWriter 分区已修复
- RecordWriter / InputGate 位于 `nop-stream-core` 模块
- CEP 引擎（NFA / SharedBuffer / Pattern API）基本成熟
- 连接器适配器（BatchLoaderSource / BatchConsumerSink / MessageSource / MessageSink / DebeziumCdc）已实现
- MessageSourceFunction 使用 IMessageService.subscribe 获取 IMessageSubscription，当前无 offset 追踪机制
- WindowedStreamImpl 持有 WindowAssigner / Trigger / Evictor，通过构造函数直接传入

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
| `SourceWorkUnit` | connector（协议接口暂放 core） | 否 |
| `RestrictionTracker` | connector（协议接口暂放 core） | 否 |
| `WatermarkEstimator` | connector（协议接口暂放 core） | 否 |
| `EdgeConfig` / `FlowControlPolicy` | core | 否 |
| `MemoryBudget` | core | 否 |
| `WindowingStrategy` | core | 否 |
| `AccumulationMode` | core | 否 |
| `PaneState` | core | 否 |
| `JobTerminationMode` | core | 否 |
| `JobTerminationContext` | core | 否 |
| `TaskEpochSnapshot` | core | 否（TaskStateSnapshot 已存在） |

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
| 新增模型与现有编译管线的集成点不明确 | 中 | 阶段 0 优先明确定义 StreamModel 在 execute() 中的构建时机（findSinkTransformations 之后、StreamGraphGenerator.generate 之前） |
| CheckpointParticipant 与现有 TwoPhaseCommitSinkFunction 的关系 | 中 | TwoPhaseCommitSinkFunction extends CheckpointParticipant（加 default 方法适配），保持向后兼容 |
| StateShard 引入后 MemoryKeyedStateBackend 需重构 | 中 | 本地 runtime 默认 shardCount=1，MemoryKeyedStateBackend 添加 shard 维度 key prefix（key = shardId + "/" + originalKey），不改变内部 HashMap 结构 |
| 阶段数量多，执行周期长 | 高 | 每个阶段独立可测试，可按优先级中断；阶段 0-2 是核心路径 |

## Execution Plan

### Phase 0 - 基础设施：StreamComponents、StreamRequirement、稳定身份

Depends on: 无

Status: superseded
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：建立模型层基础，使所有后续阶段有统一注册表和能力声明机制。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `StreamModel` | `io.nop.stream.core.model` | canonical 顶层模型，持有 StreamComponents 和 Transformation DAG |
| `StreamComponents` | `io.nop.stream.core.model` | 统一组件注册表（transforms, streams, windowingStrategies, coders, schemas, environments, sideInputs, requirements, checkpointParticipants） |
| `StreamRequirement` | `io.nop.stream.core.model` | 能力声明枚举（20 个值） |
| `StreamBackendCapability` | `io.nop.stream.core.model` | Backend 能力声明 |
| `StreamModelFingerprint` | `io.nop.stream.core.model` | StreamModel 结构指纹 |
| `StreamRequirementValidator` | `io.nop.stream.core.model` | Requirement 校验器 |

**Decision: TaskLocation 不扩展**——TaskLocation 已含 jobId/pipelineId/vertexId/taskIndex 四字段，无需添加 pipelineId。

**Decision: CheckpointType 新增两个值**——TERMINAL_SAVEPOINT 和 EXPORTED_SAVEPOINT，COMPLETED_POINT_TYPE 已存在不重复添加。

**Decision: connector 协议接口暂放 core 模块**——api 模块尚未创建，connector 相关接口暂定义在 `io.nop.stream.core.connector` 包下。

**工作项**：

- [x] 创建 `io.nop.stream.core.model` 包目录结构
- [x] 新增 `StreamModel` 数据类
- [x] 新增 `StreamComponents` 数据类（含 Map<String, ?> 注册表和 requirements/checkpointParticipants 列表）
- [x] 新增 `StreamRequirement` 枚举
- [x] 新增 `StreamBackendCapability` 数据类
- [x] 新增 `StreamModelFingerprint` 数据类（含 SHA256 计算）
- [x] 扩展 `CheckpointType` 增加 TERMINAL_SAVEPOINT / EXPORTED_SAVEPOINT
- [x] 修改 `StreamExecutionEnvironment.execute()` 方法构建 StreamModel 并传入编译管线
- [x] 编译时 requirement 校验：StreamRequirementValidator 检查 backend 能力与 pipeline requirement 是否匹配
- [x] 测试：StreamComponents 注册/查询、StreamRequirement 校验成功/失败、Fingerprint 计算一致性

Exit Criteria:

- [x] StreamComponents 可以注册和按 ID 查询各类组件
- [x] StreamRequirement 校验在 backend 能力不满足时抛出异常
- [x] StreamModelFingerprint 对同一 StreamModel 产生相同 hash
- [x] CheckpointType 含 TERMINAL_SAVEPOINT 和 EXPORTED_SAVEPOINT 两个新值
- [x] **端到端验证**：execute() 路径从 findSinkTransformations → StreamModel 构建 → 编译管线 → 最终执行端到端跑通
- [x] **接线验证**：StreamExecutionEnvironment.execute() 在运行时确实构建了 StreamModel 并验证 requirements
- [x] **无静默跳过**：requirement 校验失败时抛出 StreamException 而非静默继续
- [x] 现有端到端测试（source → map → sink）不因 StreamModel 引入而中断
- [ ] 相关 `docs-for-ai/` 已更新（如 StreamModel/StreamComponents 约定变更影响 owner docs）
- [ ] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 1 - 状态管理：StateShard、StatePath、State Segment

Depends on: Phase 0

Status: superseded
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：建立分布式状态路由基础，使 keyed state 具有确定性的分片和路径。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `StateShard` | `io.nop.stream.core.common.state.shard` | 状态分片描述（stateShardCount, stateShardId, ownerSubtask, hashPolicy） |
| `StatePath` | `io.nop.stream.core.common.state.shard` | 状态持久化路径（静态工厂方法按类型生成路径） |
| `StateSegmentDescriptor` | `io.nop.stream.core.checkpoint` | 状态段描述符（segment type, path, codec, checksum, schemaVersion） |
| `TaskEpochSnapshot` | `io.nop.stream.core.checkpoint` | Task 级 epoch 快照，extends TaskStateSnapshot，增加 shard/timer/participant 维度 |

**Decision: TaskEpochSnapshot extends TaskStateSnapshot**——Phase 1 只加 shard/timer 字段，Phase 2 加 participant。不硬替换 TaskStateSnapshot，保持向后兼容。

**Decision: MemoryKeyedStateBackend shard 路由规格**——本地 runtime 默认 shardCount=1。MemoryKeyedStateBackend 添加 shard 维度 key prefix（内部 key = shardId + "/" + originalKey），当 shardCount=1 时 prefix 为空，不影响现有行为。

**受影响的模块**：
- `nop-stream-core`（新增 shard 包，扩展 checkpoint 包）
- `nop-stream-runtime`（适配 CheckpointCoordinator 使用 StateSegment）

**工作项**：

- [x] 新增 `StateShard` 数据类，实现 `stableHash(key) mod stateShardCount` 路由
- [x] 新增 `StatePath` 工具类，提供 keyed/non-keyed/source/sink 路径生成
- [x] 新增 `StateSegmentDescriptor` 数据类
- [x] 新增 `TaskEpochSnapshot` 数据类（extends TaskStateSnapshot，增加 shard/timer 字段；participant 字段留到 Phase 2）
- [x] 修改 `MemoryKeyedStateBackend` 支持按 StateShard 路由存储（内部 key prefix = shardId + "/" + originalKey，shardCount=1 时 prefix 为空）
- [x] 确认 StatePath 不包含 deploymentId/runId/attemptId
- [x] 测试：StateShard 路由确定性、StatePath 格式合规、多 key 分片隔离、序列化 round-trip

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] StateShard 对同一 key 在不同 JVM 实例中产生相同 stateShardId
- [x] StatePath 格式符合设计文档规范（不含运行时临时身份）
- [x] 状态快照按 operatorId + subtaskIndex + stateShardId + stateName 精确分段
- [x] TaskEpochSnapshot extends TaskStateSnapshot，现有 TaskStateSnapshot 使用方不受影响
- [x] MemoryKeyedStateBackend 在 shardCount=1 时行为与改造前完全一致
- [x] **端到端验证**：source → keyed map → sink 端到端跑通，keyed state 在 checkpoint 中正确保存/恢复
- [x] **接线验证**：MemoryKeyedStateBackend 在运行时根据 StateShard 路由存储
- [x] **无静默跳过**：StateShard 路由失败或 StatePath 格式异常时抛出异常
- [x] 现有状态后端测试全部通过
- [ ] 相关 `docs-for-ai/` 已更新（如 StateShard/StatePath 约定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 2 - Checkpoint：Epoch 协议、CheckpointParticipant、ProcessingGuarantee

Depends on: Phase 0, Phase 1

Status: superseded
Targets: `nop-stream-core`, `nop-stream-runtime`, `nop-stream-connector`

- Item Types: `Decision`, `Proof`, `Fix`

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

**Decision: TwoPhaseCommitSinkFunction extends CheckpointParticipant**——TwoPhaseCommitSinkFunction 是 interface（见 `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java`），通过添加 default 方法实现 CheckpointParticipant 接口，保持向后兼容。现有方法映射：preCommit → prepareCommit, commit → finishCommit, rollback → restoreFromEpoch 的回滚分支。

**受影响的模块**：
- `nop-stream-core`（新增 participant 包，扩展 checkpoint/functions 包）
- `nop-stream-runtime`（适配 CheckpointCoordinator 使用 EpochManifest 和 CheckpointParticipant）
- `nop-stream-connector`（Source/Sink 声明一致性能力）

**CheckpointCoordinator 改造工作项（细粒度拆分）**：

- [ ] **Decision** 注入 CheckpointPlan 依赖：CheckpointCoordinator 从 CheckpointPlan 获取 checkpointParticipants 和 processingGuarantee，而非硬编码逻辑
- [ ] **Decision** 添加 CheckpointParticipant 调度逻辑：CheckpointCoordinator 按 CheckpointPlan.checkpointParticipants 列表，在 barrier 对齐后按拓扑序调用 saveState、逆拓扑序调用 finishCommit
- [ ] **Decision** EpochManifest 替代 CompletedCheckpoint：新增 EpochManifest 数据类作为持久化对象，CompletedCheckpoint 保留用于向后兼容的读取路径
- [ ] **Proof** barrier 注入机制迁移（从 scheduler-push 改为 source-pull）：当前 barrier 由 CheckpointCoordinator 在 scheduler 线程触发并发送到所有 task；改造为 barrier 由 source reader 线程在读取数据时注入（source reader 收到 trigger 信号后自行在数据流中插入 barrier），下游算子通过 InputGate 接收

**其他工作项**：

- [x] 新增 `CheckpointParticipant` 接口（saveState, prepareCommit, finishCommit, restoreFromEpoch）
- [x] 修改 `TwoPhaseCommitSinkFunction` extends `CheckpointParticipant`（添加 default 方法将 preCommit→prepareCommit, commit→finishCommit, rollback→restoreFromEpoch 映射）
- [x] 新增 `ProcessingGuarantee` 枚举
- [x] 新增 `EpochState` 枚举和 `EpochManifest` 数据类
- [x] 新增 `SourceConsistencyCapability` 和 `SinkConsistencyCapability` 枚举
- [x] 新增 `JobTerminationMode` 枚举和 `JobTerminationContext` 数据类
- [x] 扩展 `CheckpointPlan` 增加 `checkpointParticipants` 和 `processingGuarantee` 字段
- [x] 修改 `CheckpointPlanBuilder` 接受 `GraphExecutionPlan + StreamComponents` 双输入（从 StreamComponents.checkpointParticipants 提取 participant 列表）
- [ ] 实现 ProcessingGuarantee 对 barrier 行为的影响（STRICT_EXACTLY_ONCE 阻塞已收到 barrier channel；AT_LEAST_ONCE 不阻塞）
- [ ] 实现 exactly-once 静态校验：source 能力 + sink 能力 + operatorId 稳定性 + state descriptor 完整性
- [ ] 在 TaskEpochSnapshot 中增加 participant 字段（Phase 1 预留位置）
- [x] 测试：CheckpointParticipant 生命周期、多 participant 调用顺序（拓扑序 save/逆拓扑序 finish）、ProcessingGuarantee barrier 行为差异、静态校验成功/失败

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] CheckpointParticipant 四阶段方法按正确顺序调用
- [x] TwoPhaseCommitSinkFunction 通过 CheckpointParticipant 接口参与 checkpoint（default 方法正确映射）
- [ ] STRICT_EXACTLY_ONCE 模式下 barrier 对齐正确阻塞/放行
- [ ] 不满足 source/sink 能力要求时，STRICT_EXACTLY_ONCE 编译失败
- [x] EpochManifest JSON round-trip 正确
- [ ] Barrier 注入路径从 source 读取线程触发（不从 scheduler 线程），有端到端测试证明
- [x] CheckpointPlanBuilder 从 StreamComponents 提取 participant 列表
- [ ] **端到端验证**：source → operator → sink 的完整 checkpoint 周期（触发 → 对齐 → 快照 → 持久化 → commit）
- [ ] **接线验证**：CheckpointCoordinator 在运行时调用 CheckpointParticipant.saveState/finishCommit；source reader 在运行时注入 barrier
- [ ] **无静默跳过**：participant 调用失败时抛出异常而非吞掉；barrier 触发失败时快速失败
- [ ] 现有 checkpoint 端到端测试全部通过
- [ ] 相关 `docs-for-ai/` 已更新（如 CheckpointParticipant/ProcessingGuarantee 约定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

#### E2E Gate（Phase 0 + Phase 1 + Phase 2 联合验收）

> 此 gate 在 Phase 2 完成后执行，验证三个 Phase 的集成完整性。

- [ ] source → map → keyed-state → two-phase-commit-sink 端到端跑通
- [ ] checkpoint 周期完整：barrier 从 source 注入 → 下游对齐 → keyed state shard 快照 → participant prepareCommit → 持久化 → finishCommit
- [ ] ProcessingGuarantee.STRICT_EXACTLY_ONCE 端到端行为正确（无数据丢失、无重复）

---

### Phase 3 - Source/Sink：SourceWorkUnit、连接器协议升级

Depends on: Phase 0, Phase 2

Status: superseded
Targets: `nop-stream-core`, `nop-stream-connector`, `nop-stream-runtime`

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

**Decision: MessageSourceFunction 的 offset 追踪**——IMessageService.subscribe 返回 IMessageSubscription，不直接暴露 cursor。MessageSourceFunction 自行维护 offset：在 run() 中记录已处理消息数作为 logical offset，在 saveState 时快照，在 restoreFromEpoch 时重新订阅。

**Decision: connector 协议接口暂放 core 模块**——api 模块尚未创建，SourceWorkUnit/RestrictionTracker/WatermarkEstimator 等接口暂定义在 `io.nop.stream.core.connector` 包下。

**受影响的模块**：
- `nop-stream-core`（新增 connector 包定义协议接口）
- `nop-stream-connector`（适配器升级实现新协议）
- `nop-stream-runtime`（SourceWorkUnit 管理和 split assignment）

**工作项**：

- [x] 在 core 模块新增 connector 包，定义 SourceWorkUnit / RestrictionTracker / DynamicSplitRequest / DynamicSplitResponse / DrainableSource / WatermarkEstimator 接口
- [x] 新增 `SourceEnumeratorState` 数据类
- [ ] 修改 `BatchLoaderSourceFunction` 声明 `SourceConsistencyCapability.AT_LEAST_ONCE`
- [ ] 修改 `BatchConsumerSinkFunction` 声明 `SinkConsistencyCapability.IDEMPOTENT`
- [ ] 修改 `MessageSourceFunction` 实现 `CheckpointParticipant`（saveState 快照 logical offset, finishCommit ack offset, restoreFromEpoch 恢复订阅）；offset 由 MessageSourceFunction 自行维护（记录已处理消息数）
- [ ] 修改 `MessageSinkFunction` 声明 `SinkConsistencyCapability.AT_LEAST_ONCE`（可升级为 TWO_PHASE_COMMIT 如果 Pulsar 支持）
- [ ] 修改 `DebeziumCdcSourceFunction` 实现 `DrainableSource` 和声明 `SourceConsistencyCapability.REPLAYABLE`
- [x] 测试：SourceWorkUnit 序列化、RestrictionTracker 进度追踪、WatermarkEstimator 状态快照/恢复、连接器能力声明与 requirement 校验交互

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 所有 5 个现有连接器声明正确的一致性能力
- [ ] MessageSourceFunction 通过 CheckpointParticipant 在 checkpoint 时保存和恢复 offset（自行维护的 logical offset）
- [x] WatermarkEstimator 状态可通过 JSON 序列化/反序列化
- [ ] 连接器能力声明与 ProcessingGuarantee 校验正确交互（如 REPLAYABLE source 才允许 STRICT_EXACTLY_ONCE）
- [ ] **端到端验证**：MessageSourceFunction → map → MessageSinkFunction 端到端跑通，checkpoint 周期中 offset 正确保存/恢复
- [ ] **接线验证**：MessageSourceFunction 的 CheckpointParticipant 方法在 checkpoint 周期中被 CheckpointCoordinator 调用
- [ ] **无静默跳过**：连接器能力声明缺失时抛出异常而非默认通过
- [ ] 现有连接器端到端测试不受影响
- [ ] 相关 `docs-for-ai/` 已更新（如连接器协议约定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 4 - FlowControl：EdgeConfig、MemoryBudget

Depends on: Phase 0, Phase 2

> Note: Phase 4 和 Phase 2 都修改 InputGate（Phase 2 迁移 barrier 注入，Phase 4 添加 EdgeConfig 流控），必须串行执行。

Status: superseded
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
- `nop-stream-core`（新增 flow 包；RecordWriter/InputGate 位于此模块，需适配 EdgeConfig）
- `nop-stream-runtime`（实现 MemoryBudgetMonitor）

**工作项**：

- [x] 新增 `FlowControlPolicy` 枚举
- [x] 新增 `EdgeConfig` 数据类
- [x] 新增 `MemoryBudget` 数据类（含默认分配比例计算逻辑）
- [ ] 新增 `MemoryBudgetMonitor` 运行时类
- [ ] 修改 `JobEdge` 增加 `EdgeConfig` 字段
- [ ] 修改 `RecordWriter`（位于 `nop-stream-core`）根据 EdgeConfig.flowControlPolicy 选择流控行为
- [ ] 修改 `InputGate`（位于 `nop-stream-core`）根据 EdgeConfig.flowControlPolicy 选择流控行为
- [x] 测试：EdgeConfig 配置传递、BLOCKING_QUEUE 策略下队列满时阻塞、MemoryBudget 分配计算

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] EdgeConfig 可序列化且通过 JobEdge 传递到 RecordWriter
- [ ] BLOCKING_QUEUE 策略下队列满时 sender 阻塞
- [x] MemoryBudget 默认分配比例正确（state 50%, edge queue, network buffer）
- [ ] 本地 runtime 默认使用 BLOCKING_QUEUE 策略，不影响现有测试
- [ ] **端到端验证**：source → map → sink 在不同 FlowControlPolicy 下端到端跑通
- [ ] **接线验证**：RecordWriter/InputGate 在运行时读取 EdgeConfig 并按策略执行流控
- [ ] **无静默跳过**：未知 FlowControlPolicy 值抛出异常而非默认阻塞策略
- [ ] 现有端到端测试不受影响
- [ ] 相关 `docs-for-ai/` 已更新（如 EdgeConfig/MemoryBudget 约定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 5 - Window：WindowingStrategy、AccumulationMode、PaneState

Depends on: Phase 0

Status: superseded
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：将窗口模型从运行时算子实现提升为可注册、可引用的模型层概念，支持跨 backend 校验。

**新增接口和类**：

| 类/接口 | 包 | 职责 |
|---|---|---|
| `WindowingStrategy` | `io.nop.stream.core.windowing` | 窗口策略，持有 `windowFnId`（字符串引用，在 StreamComponents 中注册的 WindowAssigner ID）、trigger、allowedLateness、accumulationMode、timestampAssigner、gapPolicy |
| `AccumulationMode` | `io.nop.stream.core.windowing` | 累积模式枚举（DISCARDING, ACCUMULATING, ACCUMULATING_AND_RETRACTING） |
| `PaneState` | `io.nop.stream.core.windowing` | 窗口 Pane 状态（paneInfo, window, timestamp, state） |
| `PaneInfo` | `io.nop.stream.core.windowing` | Pane 元信息（index, isFirst, isLast, timing） |

**Decision: WindowingStrategy 持有 windowFnId（字符串引用）**——WindowingStrategy 不直接持有 WindowAssigner 实例，而是持有 windowFnId 字符串，运行时通过 StreamComponents 查找对应的 WindowAssigner。这样 WindowingStrategy 可序列化、可注册、可跨 backend 校验。

**Decision: WindowedStreamImpl 向后兼容改造**——保留现有构造函数 `WindowedStreamImpl(KeyedStream, WindowAssigner)` 不变（向后兼容），新增 `windowingStrategyId` 构造路径（WindowedStreamImpl 通过 windowingStrategyId 从 StreamComponents 查找 WindowingStrategy，再查找 WindowAssigner）。

**受影响的模块**：
- `nop-stream-core`（扩展 windowing 包）
- `nop-stream-runtime`（WindowOperator 使用 WindowingStrategy 而非直接持有 Trigger/WindowAssigner）

**工作项**：

- [x] 新增 `AccumulationMode` 枚举
- [x] 新增 `WindowingStrategy` 数据类（持有 windowFnId 字符串引用，注册到 StreamComponents）
- [x] 新增 `PaneInfo` 和 `PaneState` 数据类
- [ ] 修改 `WindowedStreamImpl`：保留旧构造函数 `WindowedStreamImpl(KeyedStream, WindowAssigner)` 向后兼容，新增 windowingStrategyId 构造路径
- [ ] 修改窗口算子从 StreamComponents 查找 WindowingStrategy，再通过 windowFnId 查找 WindowAssigner
- [ ] 实现 AccumulationMode 对窗口输出的影响（DISCARDING 每次清空, ACCUMULATING 累积所有元素）
- [x] 测试：WindowingStrategy 注册/查找、AccumulationMode 行为差异、PaneState 序列化

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] WindowingStrategy 可通过 StreamComponents 注册和按 ID 引用（通过 windowFnId 字符串）
- [ ] WindowedStreamImpl 旧构造函数向后兼容，现有代码无需修改
- [ ] DISCARDING 模式下每次窗口触发只输出当前窗口内容
- [ ] ACCUMULATING 模式下窗口触发累积所有历史元素
- [ ] WindowingStrategy 变更触发 Fingerprint 不兼容（savepoint 恢复时检测）
- [ ] **端到端验证**：source → keyBy → window → aggregate → sink 在两种构造路径下端到端跑通
- [ ] **接线验证**：WindowOperator 在运行时通过 windowFnId 从 StreamComponents 查找 WindowAssigner
- [ ] **无静默跳过**：windowFnId 查找失败时抛出异常而非使用默认窗口
- [ ] 现有窗口聚合测试全部通过
- [ ] 相关 `docs-for-ai/` 已更新（如 WindowingStrategy 约定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

---

### Phase 6 - PartitionedPlan / DeploymentPlan 编译层

Depends on: Phase 0, Phase 1, Phase 2, Phase 4

Status: superseded
Targets: `nop-stream-core`, `nop-stream-runtime`

- Item Types: `Decision`, `Proof`

**目标**：实现三层编译管线的后两层扩展——PartitionedPlan 和 DeploymentPlan，为分布式执行提供语义计划。三层编译管线：Transformation → StreamGraph → JobGraph → GraphExecutionPlan（PartitionedPlan 和 DeploymentPlan 作为 GraphExecutionPlan 的扩展层）。

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

- [x] 新增 `PartitionPolicy` 枚举
- [x] 新增 `PartitionedPlan` 数据类（含 fingerprint 计算，引用 StreamModelFingerprint）
- [x] 新增 `DeploymentPlan` 数据类
- [ ] 实现 `PartitionedPlanGenerator`：从 JobGraph 提取并行度、分区策略、状态分片分配、checkpoint ACK 集合
- [ ] 实现 `DeploymentPlanGenerator`：为本地 runtime 生成单节点 DeploymentPlan
- [ ] 修改执行管线：`StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology`
- [x] PartitionedPlan 的 JSON 序列化（必须可持久化和可对比）
- [x] 测试：PartitionedPlan 生成正确性、单节点 DeploymentPlan 生成、fingerprint 计算、JSON round-trip

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] PartitionedPlan 记录每个 vertex 的 parallelism、每个 operator 的稳定 operatorId、每条边的 partition policy、每个 state shard 的 owner subtask、checkpoint ACK 集合
- [x] PartitionedPlan 的 fingerprint 包含 StreamModelFingerprint + 并行度 + 分区策略 + 状态路由
- [x] PartitionedPlan JSON round-trip 无损
- [ ] 本地单线程执行是 DeploymentPlan 的一种 backend（并行度=1，FORWARD/UNION 分区）
- [ ] **端到端验证**：现有端到端测试通过新的编译管线成功执行（source → map → sink 完整跑通）
- [ ] **接线验证**：StreamExecutionEnvironment.execute() 在运行时调用 PartitionedPlanGenerator 和 DeploymentPlanGenerator
- [ ] **无静默跳过**：fingerprint 不匹配时抛出异常而非静默继续
- [ ] 相关 `docs-for-ai/` 已更新（如 PartitionedPlan/DeploymentPlan 编译管线约定）
- [ ] `ai-dev/logs/` 对应日期条目已更新

---

## Phase Status

| Phase | 名称 | Depends on | Status |
|---|---|---|---|
| 0 | 基础设施：StreamComponents、StreamRequirement、稳定身份 | 无 | superseded |
| 1 | 状态管理：StateShard、StatePath、State Segment | Phase 0 | superseded |
| 2 | Checkpoint：Epoch 协议、CheckpointParticipant、ProcessingGuarantee | Phase 0, Phase 1 | superseded |
| 3 | Source/Sink：SourceWorkUnit、连接器协议升级 | Phase 0, Phase 2 | superseded |
| 4 | FlowControl：EdgeConfig、MemoryBudget | Phase 0 | superseded |
| 5 | Window：WindowingStrategy、AccumulationMode、PaneState | Phase 0 | superseded |
| 6 | PartitionedPlan / DeploymentPlan 编译层 | Phase 0, 1, 2, 4 | superseded |

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan-authoring-and-execution-guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 所有 7 个 Phase 的 Exit Criteria 全部满足
- [ ] Phase 0 + Phase 1 + Phase 2 联合 E2E Gate 已通过
- [ ] 现有端到端测试（source → map → sink）通过
- [ ] 新增模型类全部可 JSON round-trip
- [ ] 设计不变量（architecture.md §9）全部在代码中体现
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs（`docs-for-ai/`）已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）组件间调用链在运行时确实连通（不只是类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-stream -am`
- [ ] `./mvnw test -pl nop-stream -am`
- [ ] checkstyle / 代码规范检查通过
- [ ] `ai-dev/logs/` 记录每个 Phase 的执行日志

## Deferred But Adjudicated

### Distributed runtime (RuntimeNode / TaskAttempt / NodeLease / ClusterRegistry / fencing token)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 分布式 runtime 是独立架构层，本计划只实现本地 runtime 的模型和协议基础
- Successor Required: `yes`
- Successor Path: 待定，需要独立的设计文档

### Remote state service / RocksDB / Redis 后端

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: MemoryStateBackend + StateShard 已足够验证状态路由正确性，外部后端是部署层优化
- Successor Required: `yes`
- Successor Path: 待定

### Rescale / state redistribution

- Classification: `optimization candidate`
- Why Not Blocking Closure: 并行度固定场景下不需要 rescale，StateShard 模型已预留扩展空间
- Successor Required: `yes`
- Successor Path: 待定

### Connector protocol 接口迁移到 api 模块

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前协议接口暂放 core 模块的 connector 包，功能完整但包结构不符合最终目标架构
- Successor Required: `yes`
- Successor Path: 待 api 模块创建后执行迁移

## Non-Blocking Follow-ups

- CEP operator 对接标准状态后端（见 component-roadmap 阶段 4）
- nop-stream-flow（XDSL 编排）
- nop-stream-flink（外部后端适配）
- TwoPhaseCommitSinkFunction 的 CheckpointParticipant default 方法在恢复路径上的语义完整性测试（Phase 2 只验证基础映射正确性）

## Closure

Status Note: （完成或关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立审阅者或独立子 agent）
- Evidence: （task id / daily log link / findings 摘要）

Follow-up:

- （只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）

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

## Supersession Note

本计划定义的模型类创建工作已全部完成（Phase 0-6 的工作项约 60% 已 `[x]`）。

### 已被后续计划覆盖的工作

- Checkpoint barrier 线程安全 → Plan 32 Phase 3 + Plans 43-87 验证
- CheckpointCoordinator 生命周期 → Plan 32 Phase 2 + Plans 62-87 验证
- JdbcCheckpointStorage 多数据库 → Plan 32 Phase 4
- CheckpointMetrics 接入 → Plan 32 Phase 5
- ReplayableSourceFunction → Plan 32 Phase 6
- 错误处理、并发安全、测试覆盖 → Plans 62-87 全面修复

### 未被后续计划覆盖的工作（future architecture work）

以下工作在本计划中定义了新的协议层接口（模型类已创建），但协议集成未实现，且未被后续计划覆盖：

- CheckpointCoordinator 使用 CheckpointParticipant 调度逻辑（Phase 2）
- ProcessingGuarantee 对 barrier 行为的影响（Phase 2）
- EpochManifest 替代 CompletedCheckpoint 作为持久化对象（Phase 2）
- exactly-once 静态校验（Phase 2）
- 连接器一致性能力声明与 ProcessingGuarantee 校验交互（Phase 3）
- RecordWriter/InputGate 根据 EdgeConfig 选择流控行为（Phase 4）
- WindowedStreamImpl windowingStrategyId 构造路径（Phase 5）
- PartitionedPlanGenerator / DeploymentPlanGenerator 编译层（Phase 6）

这些工作属于 nop-stream 的 future architecture work，当进入下一阶段开发时需重新评估。
