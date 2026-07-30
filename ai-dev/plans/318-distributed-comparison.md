# 318 分布式执行模型源码级对比分析

> Plan Status: completed
> Plan Type: analysis
> Mission: nop-stream-flink-comparison
> Work Item: roadmap item 7
> Last Reviewed: 2026-07-24
> Source: `docs/backlog/nop-stream-flink-comparison-roadmap.md` Item 7
> Related: `ai-dev/plans/316-flink-source-audit.md`, `ai-dev/plans/317-nopstream-live-audit.md`, `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-1-checkpoint-barrier-comparison.md`

## Purpose

对比 nop-stream 分布式运行时与 Flink 的架构差异，评估 nop-stream 三面分离设计（PartitionedPlan/DeploymentPlan）的完备性与实现缺口，产出比对文档供 item 8（综合缺口分析）使用。

## Current Baseline

- Plans 316 (Flink 源码审计) 和 317 (nop-stream 实现审计) 为 **active** — 与 items 3/4/5/6 执行时状态一致
- Flink baseline 将产出至 `ai-dev/analysis/nop-stream/01-flink-source-audit.md`（不存在）
- nop-stream baseline 将产出至 `ai-dev/analysis/nop-stream/02-nopstream-live-audit.md`（不存在）
- Items 3/4/5/6 均已建立直接源码阅读 fallback 模式，本计划沿用相同做法
- nop-stream-runtime 模块位于 `nop-stream/nop-stream-runtime/`，包含 `execution/`、`coordinator/`、`taskmanager/`、`cluster/`、`transport/`、`rpc/`、`checkpoint/`、`operators/` 子包
- 已知规划差距（roadmap 已列出）：分布式 RPC 跨 JVM 未接线（Phase 3）、Coordinator HA（Phase 3）、Leader 选举（规划中）
- 本计划 6 个对比维度：执行图层次、Task 生命周期、调度模型、RPC 抽象、数据交换、集群管理

## Goals

- 产出分布式执行模型源码级对比文档，覆盖执行图层次、Task 生命周期、调度模型、RPC 抽象、数据交换、集群管理 6 个维度
- 每个发现附带精确的 Flink 类/方法和 nop-stream 类/方法引用
- 评估 nop-stream 图变换管线和运行时状态机（`Task.State`/`SubtaskTask.State` + `GraphExecutionPlan`）与 Flink 执行图模型的语义等价性。澄清 `PartitionedPlan`/`DeploymentPlan` 作为纯数据产物的角色与 Flink `ExecutionGraph` 状态机的异同
- 为 item 8 提供可直接消费的差距列表和修复建议

## Non-Goals

- 不进行代码修复（属于 roadmap Phase 3 及后续）
- 不覆盖 checkpoint/barrier（item 3）、状态管理（item 4）、窗口/时间（item 5）、CEP（item 6）
- 不涉及 Flink Table/SQL API 或 PyFlink
- 不涉及 standalone/Kubernetes/YARN 部署模式细节

## Scope

### In Scope

- 执行图层次对比：Flink ExecutionGraph 调度状态机（ExecutionVertex lifecycle: CREATED/SCHEDULED/DEPLOYING/RUNNING/FINISHED/CANCELING/CANCELED/FAILED/RECONCILING）vs nop-stream 的实际运行时状态机（`Task.State`: CREATED/RUNNING/COMPLETED/FAILED/CANCELED; `SubtaskTask.State` 增加 CANCELING）。检查图变换管线中语义等价性：
  - Flink: StreamGraph → JobGraph → ExecutionGraph → Execution（运行时状态机）
  - nop-stream: Transformation → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → **GraphExecutionPlan**（`GraphExecutionPlan` 是真正的运行时入口，创建 Subtask/ResultPartition/InputChannel）
  - 注意：`PartitionedPlan`/`DeploymentPlan` 是纯数据结构（`@DataBean`，无状态机），`DeploymentPlanGenerator` 仅 46 行仅支持本地模式。状态机位于 `Task.State`/`SubtaskTask.State`。分布式模式下 `EmbeddedDistributedExecutor` 绕过 `DeploymentPlanGenerator`，直接构造 `RemoteGraphExecutionPlanBuilder`
- Task 生命周期对比：Flink StreamTask Mailbox 模型（StreamTask.invoke() → MailboxProcessor run-loop → InputProcessor → OperatorChain）vs nop-stream Task/SubtaskTask run-loop。检查生命周期钩子（init/invoke/cleanup）、异常处理、中断恢复
- 调度模型对比：Flink SchedulerNG + Slot 分配（SlotPool → SlotSharingManager → SlotStrategy）vs nop-stream 的实际调度逻辑（`JobCoordinator.assignTasks()` round-robin + `ClusterRegistry` 节点管理/租约 + `IStreamExecutionDispatcher.execute()` 仅是执行入口，非调度器）。检查 nop-stream 是否具备 slot 资源模型
- RPC 抽象对比：Flink Akka/RpcGateway（RpcEndpoint → RpcGateway → AkkaRpcActor）vs nop-stream IStreamTaskRpcService / IStreamCoordinatorRpcService。检查接口设计、消息序列化、异步回调模式的异同
- 数据交换对比：Flink Netty-based NetworkBufferPool（ResultPartition → InputChannel → BufferPool → Netty）vs nop-stream 数据传输（`ResultPartition`/`InputChannel` for local, `RemoteResultPartition`→`IMessageService`/`RemoteInputChannel`←`IMessageService` for distributed）。nop-stream 的数据传输已具备分布式能力（`RemoteResultPartition`/`RemoteInputChannel` 在 `EmbeddedDistributedExecutor` 中创建并以 JVM 内 IMessageService 模拟运行）。对比聚焦：反压传播协议、缓冲区管理、exactly-once 传输保证——而非是否存在分布式传输
- 集群管理对比：Flink ResourceManager / JobManager HA（LeaderContender → LeaderElectionService → ZooKeeper-based HA）vs nop-stream ClusterRegistry（`InMemoryClusterRegistry`/`JdbcClusterRegistry` + `NodeInfo`/`CoordinatorInfo`/`LeaseInfo`）。注意：roadmap 规划的 `ILeaderElector` 在当前代码库中不存在（仅 roadmap 文本引用），此维度的对比将限于设计文档和 roadmap 描述层面，无法做精确的 class:method 源码级引用
- 结论：差距列表、优先级（P0-P3）、修复建议

### Out Of Scope

- Checkpoint/barrier comparison (item 3) — 但 abort 控制通道的分布式路径与本计划交叉，仅做交叉引用
- State management comparison (item 4)
- Window/time comparison (item 5)
- CEP comparison (item 6)
- Code fixes or refactoring
- Flink standalone/YARN/Kubernetes deployment specifics
- Flink runtime metric system details

### Analysis Depth Guardrails

- Per comparison dimension: identify **Bug** (incorrect behavior), **Gap** (feature missing or unconnected), **Improvement** (enhancement opportunity), **Hollow** (interface exists but body no-op), **No-Op** (silent skip), and **Doc** (documentation/contract drift) — consistent with the gap table taxonomy used in items 3/4/5 deliverables (`03-checkpoint-comparison.md`, `04-state-comparison.md`, `05-window-comparison.md`)
- Each finding must cite exact class:method or file:line for both Flink and nop-stream
- Stop when additional dimensions add no new gap categories. Target 5-15 pages.
- If Plans 316/317 deliverable subsections lack sufficient detail for a given dimension, supplement with direct source reading of both Flink (`~/sources/flink/flink-runtime/`) and nop-stream (`nop-stream/nop-stream-runtime/`), documenting the supplementation
- Coordinate with items 3's deliverable (`03-checkpoint-comparison.md`) where checkpoint abort channel's distributed path overlaps with this comparison — cross-reference rather than duplicate

## Execution Plan

### Phase 1 - Distributed Execution Comparison Deliverable

Status: completed
Targets: Flink at `~/sources/flink/flink-runtime/src/main/java/org/apache/flink/runtime/` (executiongraph, scheduler, taskmanager, io/network, rpc, resourcemanager, jobmaster); nop-stream at `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/` (execution, coordinator, taskmanager, cluster, transport, rpc)

- Item Types: `Proof | Decision | Follow-up`

- [x] `Proof` Confirm Plans 316/317 status and deliverables availability. If deliverables absent (expected per precedent), document fallback to direct source reading in deliverable preamble.
- [x] `Proof` Compare execution graph layers: Flink JobGraph → ExecutionGraph → Execution (ExecutionVertex lifecycle: CREATED/SCHEDULED/DEPLOYING/RUNNING/FINISHED/CANCELING/CANCELED/FAILED/RECONCILING) vs nop-stream's transformation pipeline (Transformation → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → **GraphExecutionPlan**) and runtime state machines (`Task.State` CREATED/RUNNING/COMPLETED/FAILED/CANCELED; `SubtaskTask.State` adds CANCELING). Note: PartitionedPlan/DeploymentPlan are pure data beans (`@DataBean`) with no state machine — they are deployment configuration artifacts, not runtime state machines. The actual state machine is in Task/SubtaskTask + GraphExecutionPlan. Check semantic equivalence of the full pipeline, identifying gaps in state machine completeness and lifecycle management.
- [x] `Proof` Compare Task lifecycle: Flink StreamTask.invoke() → MailboxProcessor run-loop → mailbox loop (processIncomingInput → processMail → runDefaultAction) → OperatorChain → operators → finish/close vs nop-stream Task/SubtaskTask run-loop. Check lifecycle hooks (init/invoke/cleanup), exception handling, interrupt recovery, and mailbox integration.
- [x] `Decision` Compare scheduling model: Flink SchedulerNG (default scheduler → slot allocation → ExecutionSlotAllocator → SlotPool → SlotSharingManager) vs nop-stream's actual scheduling logic (`JobCoordinator.assignTasks()` round-robin + `ClusterRegistry` node management/lease + `IStreamExecutionDispatcher.execute()` as execution entry point, not scheduler). Check whether nop-stream has a slot resource model at all — if not, classify as gap. Check scheduling strategy, resource request semantics, and deployment plan generation timing. Note: `IStreamExecutionDispatcher` has only `supportsDeploymentMode()` and `execute()` — it is not a scheduler; resource management is spread across `ClusterRegistry` (node leases) + `JobCoordinator` (task assignment).
- [x] `Decision` Compare RPC abstraction: Flink Akka-based RpcGateway + RpcEndpoint (AkkaRpcActor → RpcEndpoint.invokeRpc → RpcInvocationHandler) vs nop-stream IStreamTaskRpcService / IStreamCoordinatorRpcService. Check interface design, message serialization, async callback patterns, and whether the cross-JVM wiring is hollow (interface exists but local-only) or truly implemented.
- [x] `Proof` Compare data exchange: Flink Netty-based network stack (ResultPartition → Partition → BufferConsumer → BufferPool → NetworkBuffer → Netty message) vs nop-stream data transport (local: `ResultPartition`/`InputChannel`; distributed: `RemoteResultPartition`→`IMessageService`/`RemoteInputChannel`←`IMessageService`). Note: nop-stream's data transport IS distributed-capable (RemoteResultPartition/RemoteInputChannel exist and are created by EmbeddedDistributedExecutor). The real gap is (a) backpressure propagation protocol, (b) buffer pool management, (c) exactly-once transport guarantees, and (d) control channel RPC remaining local-only. Check these specific dimensions.
- [x] `Decision` Compare cluster management: Flink ResourceManager (slot management → worker registration → heartbeat) + JobManager HA (LeaderContender → LeaderElectionService → ZooKeeper HA services) vs nop-stream ClusterRegistry (`InMemoryClusterRegistry`/`JdbcClusterRegistry` + `NodeInfo`/`CoordinatorInfo`/`LeaseInfo`). Check cluster state management, node discovery, health checking, and failover mechanism. Note: `ILeaderElector` does not exist in the codebase (roadmap-only concept) — cluster management comparison for HA/leader-election is limited to design-doc and roadmap-text level, not precise class:method source reference.
- [x] `Follow-up` Synthesize findings into a gap table (Bug/Gap/Improvement/Hollow/No-Op/Doc) with priority (P0-P3) and repair recommendations
- [x] `Follow-up` Write deliverable at `ai-dev/analysis/nop-stream/07-distributed-comparison.md`

Exit Criteria:

> Each Exit Criterion must be `[x]` before Phase Status becomes `completed`.

- [x] Deliverable `ai-dev/analysis/nop-stream/07-distributed-comparison.md` exists, covering all 6 comparison dimensions with Flink and nop-stream class:method references
- [x] Each finding includes gap classification (Bug/Gap/Improvement/Hollow/No-Op/Doc), severity (P0-P3), and specific file:line evidence
- [x] nop-stream's three-separation design (PartitionedPlan/DeploymentPlan) assessed against Flink ExecutionGraph for semantic equivalence
- [x] Cross-reference with `03-checkpoint-comparison.md` for abort channel's distributed path — no duplication
- [x] Deliverable passes independent sub-agent review (different task_id, no Blocker remaining)
- [x] No owner-doc update required (analysis-only, no live baseline change)
- [x] `ai-dev/logs/` corresponding date entry updated

## Closure Gates

> All items below and all Phase Exit Criteria must be `[x]` before `Plan Status` can be `completed`.

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

Status Note: Plan completed successfully. All 6 comparison dimensions documented, gap table with 20 findings, deliverable consumable by roadmap item 8.

Reviewer / Agent: Independent sub-agent ses_04be0fc83ffe2d5rS4ApLoRITn

Closure Audit Evidence:

Evidence:
Independent sub-agent closure audit (ses_04be0fc83ffe2d5rS4ApLoRITn) verified all exit criteria PASS.
- EC1 ✅ Deliverable exists (701 lines, `ai-dev/analysis/nop-stream/07-distributed-comparison.md`)
- EC2 ✅ All 6 comparison dimensions covered with Flink/nop-stream architecture, comparison table, and classification
- EC3 ✅ Gap classification (Bug/Gap/Hollow/Improvement/Doc) + severity (P1-P3) + file:line evidence throughout
- EC4 ✅ Three-separation design assessed against Flink ExecutionGraph for semantic equivalence
- EC5 ✅ Cross-reference with checkpoint comparison — no duplication
- EC6 ✅ No owner-doc update required (analysis-only)
- EC7 ✅ Log entry exists at `ai-dev/logs/2026/07-25.md`
- CG1 ✅ Gap table: 20 entries, 7 columns, actionable repair suggestions
- CG2 ✅ No Blocker found
- CG3 ✅ Log entry recorded
- CG4 ✅ Closure audit evidence recorded here
- CG5 ✅ check-plan-checklist.mjs --strict passes (after this update)

Follow-up:

None — analysis deliverable consumable by roadmap item 8.
