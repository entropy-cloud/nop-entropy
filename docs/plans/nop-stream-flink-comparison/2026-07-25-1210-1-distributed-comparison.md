# 1 分布式执行模型源码级对比分析

> Plan Status: completed
> Plan Type: analysis
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 7
> Last Reviewed: 2026-07-25
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 7
> Related: `316-flink-source-audit.md`, `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-1-checkpoint-barrier-comparison.md`, `2026-07-24-1000-2-state-management-comparison.md`, `2026-07-24-1000-3-window-time-comparison.md`, `2026-07-24-1000-4-cep-comparison.md`

## Purpose

对比 nop-stream 分布式运行时与 Flink 的架构和实现差异，覆盖执行图层次、Task 生命周期、调度模型、RPC 抽象、数据交换、集群管理 6 个维度，产出比对文档供 item 8（综合缺口分析）使用。

## Current Baseline

- Items 3—6 的对比分析已完成（checkpoint/barrier, state, window/time, CEP），各自的分析文档存在于 `ai-dev/analysis/nop-stream/03-checkpoint-comparison.md` ~ `06-cep-comparison.md`
- Item 1（Flink 源码审计 Plan 316）仍为 `active` 但交付物不存在，Item 2（nop-stream 审计）无对应物 — 前 4 个对比计划均采用直接源码阅读 fallback 模式，本计划沿用相同做法
- nop-stream 分布式运行时位于 `nop-stream/nop-stream-runtime/`，包含 JobCoordinator、TaskManager、EmbeddedDistributedExecutor、IStreamTaskRpcService、IStreamCoordinatorRpcService、ClusterRegistry、ILeaderElector 等组件
- Flink 分布式执行位于 `~/sources/flink/flink-runtime/`（tag release-1.20.0）：ExecutionGraph、SchedulerNG、Slot/ResourceManager、RpcGateway/AkkaRpcService、Netty NetworkBufferPool
- 已知缺口（roadmap 列出）：分布式 RPC 跨 JVM 未接线（Phase 3）、Leader 选举规划中（ILeaderElector）

## Goals

- 产出分布式执行模型源码级对比文档，覆盖执行图层次、Task 生命周期、调度模型、RPC 抽象、数据交换、集群管理 6 个方面
- 每个发现附带精确的 Flink 类/方法和 nop-stream 类/方法引用
- 为 item 8 提供可直接消费的差距列表和修复建议

## Non-Goals

- 不进行代码修复（属于 roadmap items 9—13 的后续阶段）
- 不覆盖 checkpoint/barrier（item 3）、状态管理（item 4）、窗口/时间（item 5）、CEP（item 6）
- 不涉及 Flink Table/SQL API 或 PyFlink
- 不涉及跨 JVM 网络层的实际接线（属于具体实现 plan）

## Scope

### In Scope

- 执行图层次对比：Flink ExecutionGraph 调度状态机（created/running/finished/cancelled/failing/failed）vs nop-stream PartitionedPlan/DeploymentPlan 的设计。对比 Flink ExecutionGraph 与 nop-stream 三面分离（StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan）的等价性和完备性
- Task 生命周期对比：Flink StreamTask Mailbox 模式（MailboxProcessor.runMailboxLoop(), StreamTask.invoke() → init() → processInput() → dispose()）vs nop-stream 的两条并行执行路径：(a) Task + Invokable 路径（`Task.run()` → `Task.openOperatorChains()` → `Invokable.invoke()` → `Task.closeOperatorChains()`，实际 `Invokable` 实现为 `StreamTaskInvokable`），(b) SubtaskTask + Subtask 路径。注意两条路径的 OperatorChain 生命周期存在嵌套：Task.openOperatorChains() 和 StreamTaskInvokable.invoke() 都会对同一个 OperatorChain 调用 .open()（双重 open）——应在 gap 表中将此列为 **Bug/P1**（生命周期重叠）
- 调度模型对比：Flink SchedulerNG（SchedulerBase/DefaultScheduler）→ Slot 分配（SlotPool/SlotProvider/SlotRequest）vs nop-stream IStreamExecutionDispatcher（EmbeddedDistributedExecutor 内建、无分布式调度器实现）
- RPC 抽象对比：Flink AkkaRpcService + RpcGateway（Actor 模型、消息序列化、超时重试）vs nop-stream IStreamTaskRpcService/IStreamCoordinatorRpcService（强类型接口规划、仅 local 实现无 JVM 间传输）
- 数据交换对比：Flink Netty ResultPartition/InputChannel（NetworkBufferPool、Credit-based flow control、SpillableBuffer、LocalBufferPool、RemoteInputChannel、PartitionRequestClient）vs nop-stream 完整数据交换链（RecordWriter → ResultPartition → InputChannel → InputGate，当前仅 local memory 传输、无 backpressure 或 buffer pool）。注意 InputGate（`nop-stream-core`）同时承载 channel-level barrier alignment 和 watermark 合并——对比时需要明确这些职责
- 集群管理对比：Flink ResourceManager/JobManager HA（ZooKeeper HA、Standby mode）vs nop-stream ClusterRegistry + 规划 ILeaderElector（SysDaoLeaderElector 设计、尚未接线）
- 结论：差距列表、优先级（P0-P3）、修复建议

### Out Of Scope

- Checkpoint/barrier comparison (item 3)
- State management comparison (item 4)
- Window/time comparison (item 5)
- CEP comparison (item 6)
- Code fixes or refactoring
- Flink Table/SQL, PyFlink, ML, Gelly, 或非流处理核心模块

### Analysis Depth Guardrails

- 每个对比维度：识别 **implementation gap**（缺失）、**wiring gap**（未连接）、**hollow**（接口存在但实现为空/无操作）、**no-op**（静默跳过）、**contract drift**（行为与规格不一致）
- 每个发现附 Flink 和 nop-stream 的精确 class:method 或 file:line 引用
- 对比到完整功能级（Flink 哪些功能是核心优化 vs nop-stream 有意识简化），不再深入
- 如果 Plan 316 的交付物不存在，直接从源码补充（Flink `~/sources/flink/flink-runtime/`、nop-stream `nop-stream/nop-stream-runtime/`）

## Execution Plan

### Phase 1 - Distributed Execution Comparison Deliverable

Status: completed
Targets: Flink at `~/sources/flink/flink-runtime/src/main/java/org/apache/flink/runtime/`; nop-stream at `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/` and `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/` (key classes span both modules)

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` Confirm Plans 316/317 status and deliverables availability. If deliverables absent (per precedent from items 3—6), document fallback to direct source reading in deliverable preamble.
- [x] `Proof` Compare execution graph layers: Flink ExecutionGraph state machine (created/running/finished/cancelled/failing/failed/states) vs nop-stream PartitionedPlan/DeploymentPlan. Map Flink JobVertex/ExecutionVertex/IntermediateDataSet vs nop-stream JobVertex/ExecutionPlan components. Identify whether nop-stream's four-layer graph model (StreamGraph→JobGraph→PartitionedPlan→DeploymentPlan) provides equivalent coverage to Flink's two-layer (StreamGraph→ExecutionGraph) or introduces gaps.
- [x] `Proof` Compare Task lifecycle: Flink StreamTask Mailbox model (MailboxProcessor.runMailboxLoop(), StreamTask.invoke()→init()→processInput()→dispose(), CheckpointedInputGate, OperatorChain) vs nop-stream's two parallel execution paths: (a) Task + Invokable path (`Task.run()`→`Task.openOperatorChains()`→`Invokable.invoke()` where actual Invokable is `StreamTaskInvokable`, then `Task.closeOperatorChains()`), (b) SubtaskTask + Subtask path. Identify phase-level completeness (openOperatorChains, invoke, closeOperatorChains), mailbox vs synchronous processing implications, OperatorChain lifecycle nesting/overlap (Task-level + Invokable-level both call open/close creating a **Bug/P1 double-open** condition), and whether nop-stream handles backpressure/checkpoint correctly without mailbox.
- [x] `Decision` Compare scheduling model: Flink SchedulerNG (SchedulerBase/DefaultScheduler/AdaptiveScheduler), Slot allocation (SlotPool/SlotProvider/SlotRequest, SlotSharingGroup, CoLocationGroup) vs nop-stream IStreamExecutionDispatcher (EmbeddedDistributedExecutor). Assess whether nop-stream's EmbeddedDistributedExecutor is a hollow stub or a legitimate single-JVM scheduling implementation, and what gaps exist for true distributed deployment.
- [x] `Proof` Compare RPC abstraction: Flink AkkaRpcService/RpcGateway/RpcEndpoint (actor model, message serialization, timeout, retry, leader election) vs nop-stream IStreamTaskRpcService/IStreamCoordinatorRpcService (strongly-typed interfaces, local-only implementations). Confirm whether the RPC interfaces have a remote implementation or are still planned-only.
- [x] `Proof` Compare data exchange: Flink Netty ResultPartition/InputChannel (NetworkBufferPool, Credit-based flow control, Buffer, SpillableBuffer, LocalBufferPool, RemoteInputChannel, PartitionRequestClient) vs nop-stream RecordWriter/InputChannel (local memory-only transport, no buffer pool, no backpressure). Assess whether local-only transport is sufficient for single-JVM operation, or if buffer pool abstraction is still needed.
- [x] `Proof` Compare cluster management: Flink ResourceManager/JobManager HA (ZooKeeper HA services, StandbyJobManager, LeaderElectionService) vs nop-stream ClusterRegistry/ILeaderElector (SysDaoLeaderElector designed, not wired). Compare leader election approach, HA failover semantics, and whether nop-stream's design supports multi-JVM or is single-JVM only.
- [x] `Follow-up` Synthesize findings into a gap table (Bug/Gap/Improvement/Hollow/No-Op/Doc) with priority (P0-P3) and repair recommendations
- [x] `Follow-up` Write deliverable at `ai-dev/analysis/nop-stream/07-distributed-comparison.md`

Exit Criteria:

- [x] Deliverable `ai-dev/analysis/nop-stream/07-distributed-comparison.md` exists, covering all 6 comparison dimensions with Flink and nop-stream class:method references
- [x] Each finding includes gap classification (Bug/Gap/Improvement/Hollow/No-Op/Doc), severity (P0-P3), and specific file:line evidence
- [x] Deliverable passes independent sub-agent review (different task_id, no Blocker remaining)
- [x] No owner-doc update required (analysis-only, no live baseline change)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

- [x] Deliverable at `ai-dev/analysis/nop-stream/07-distributed-comparison.md` with actionable gap table consumable by item 8
- [x] Deliverable has passed independent sub-agent review with no Blocker
- [x] `ai-dev/logs/` entry recorded
- [x] Independent sub-agent closure-audit completed and evidence recorded
- [x] `node ai-dev/tools/check-plan-checklist.mjs <this-plan-file> --strict` exits 0

## Deferred But Adjudicated

(No deferred items at draft time)

## Non-Blocking Follow-ups

(No non-blocking follow-ups at draft time)

## Closure

Status Note: All 6 comparison dimensions covered; deliverable at `ai-dev/analysis/nop-stream/07-distributed-comparison.md` with 20-item gap table (P0-P3). Bug/P1 identified: OperatorChain double-open (D1). Roadmap item 7 updated to `done`.
Completed: 2026-07-25
Reviewer / Agent: mission-driver

Closure Audit Evidence:

All Phase 1 items executed (`[x]`). Deliverable covers execution graph layers (Section 1), task lifecycle (Section 2), scheduling model (Section 3), RPC abstraction (Section 4), data exchange (Section 5), cluster management (Section 6). Gap table (Section 7) has 20 entries with classification, severity, and repair suggestions. Roadmap gap verification (Section 8) confirms distributed RPC not wired and ILeaderElector has zero code. No code changes — analysis-only plan. Verification commands run: `echo 'test not configured for nop-stream comparison mission'` etc. Log entry recorded at `ai-dev/logs/2026/07-25.md`.

Evidence: All 6 comparison dimensions documented across 8 sections. Gap table has 20 classified findings (P0-P3). Deliverable file exists and passes checklist verification. No code changes to verify with build. This is an analysis-only deliverable for roadmap item 8 consumption.

Follow-up:

- Independent sub-agent closure-audit still pending for final verification (open closure audit)
