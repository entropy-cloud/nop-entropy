# 24 — DeploymentPlan subtask 分配 + 平台 discovery 接入

> Plan Status: completed
> Last Reviewed: 2026-07-26
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 24 (G50, G51); `ai-dev/design/nop-stream/01-architecture-baseline.md` §四/§五; `ai-dev/analysis/nop-stream/07-distributed-comparison.md`
> Mission: nop-stream-production
> Work Item: 24 (Phase 1 — 分布式运行时基础)
> Related: `2026-07-25-2200-2-partial-subtask-recovery.md` (Stage 20, restored-epoch baseline)

## Purpose

把 nop-stream 从「逻辑部署描述 + 运行时临时分配节点」推进到「DeploymentPlan 承载物理 subtask→node 分配 + 节点注册/发现通过平台 discovery」的稳定基线，为 Stage 25（per-task 故障检测）、Stage 26（buffer pool）、Stage 28（RPC 扩容）提供可观测、可持久化的部署分配底座。

## Current Baseline

经 live 仓库核对（非沿用旧 roadmap 假设）：

- `DeploymentPlan`（`nop-stream-core/.../execution/plan/DeploymentPlan.java`，64 行，`@DataBean Serializable`）当前字段：`jobId, pipelineId, partitionedPlan, transportBackend, stateBackendBinding, checkpointStorage, edgeConfigs, memoryBudget`。**不含任何 subtask→node 映射字段**。`PartitionedPlan` 的内部类 `VertexPlan`（`PartitionedPlan.java:63`）含 `vertexId, parallelism, operatorId`，**不含 node/affinity**。
- **部署 plan 生成走 `IDeploymentPlanProvider` SPI（ServiceLoader 注册）**：`IDeploymentPlanProvider`（`nop-stream-core/.../execution/IDeploymentPlanProvider.java:25`，含 `static getProvider()` `:40`）+ core 默认 `DefaultDeploymentPlanProvider`（`:33`）+ runtime 实现 `DeploymentPlanProviderImpl`（`nop-stream-runtime/.../execution/DeploymentPlanProviderImpl.java:21`，委托 `DeploymentPlanGenerator`，经 `META-INF/services/io.nop.stream.core.execution.IDeploymentPlanProvider` ServiceLoader 注册）。`DeploymentPlanGenerator.generateLocal(...)`（`DeploymentPlanGenerator.java:25`）硬编码 `"local"/"memory"/"local"` + `EdgeConfig.defaultConfig()`。**关键现状**：`StreamExecutionEnvironment.generateDeploymentPlan()`（`:402-403`）**无条件 mode-agnostically** 调用 `IDeploymentPlanProvider.getProvider().generateLocal(partitionedPlan)`——`deploymentMode` 分支（`:266`）**只用于 dispatcher 选择，不用于 provider 选择**；`IDeploymentPlanProvider` 当前**只接收 `PartitionedPlan`，无活跃节点集入参**。**因此 distributed provider 需要：(a) 扩展接口（或新增 distributed 方法）使其能接收活跃节点集；(b) 在 `generateDeploymentPlan()` 增加 mode 分支选择 distributed provider。** `IStreamExecutionDispatcher.execute(...)` 接收已生成好的 DeploymentPlan（生成发生在 environment/provider 侧，不在 `execute()` 内）。
- `DeploymentMode` 枚举 = `{LOCAL, DISTRIBUTED}`（`DeploymentMode.java`）。
- `IStreamExecutionDispatcher`（`IStreamExecutionDispatcher.java`）仅 2 方法。唯一实现 `EmbeddedDistributedExecutor`（`EmbeddedDistributedExecutor.java:47`，进程内模拟多节点）。
- **分布式运行时已大量存在**（非空壳）：
  - `JobCoordinator`（`JobCoordinator.java`，643 行）`assignTasks()`（line 195）已 round-robin 跨 `clusterRegistry.getActiveNodes()` 分配（line 221，**纯 round-robin，无容量感知**：grep `capacity` 在 JobCoordinator 内零匹配），记录进 `ClusterRegistry`（line 229）+ 内存 `taskAssignmentMap`，但 **assignment 只活在运行时，未物化进 DeploymentPlan**。
  - `TaskManager`（`TaskManager.java`，569 行）每 5s `heartbeat()`→`clusterRegistry.renewLease()`；`RunningTask` 持 `attemptId`(UUID)。
  - `ClusterRegistry` SPI + `InMemoryClusterRegistry` + `JdbcClusterRegistry`（436 行，三张表 `nop_stream_coordinator/node/task_assignment`，lease TTL 15s）= **完整的 coordinator/node/lease/task-assignment 存储**。
- **平台 discovery SPI 形态（已核对）**：
  - `IDiscoveryClient`（`nop-cluster-core/.../discovery/IDiscoveryClient.java`）**只读**：`getInstances(serviceName)` / `getServices()`。
  - `INamingService extends IDiscoveryClient`（`naming/INamingService.java:18`）增加 `registerInstance` / `unregisterInstance`。**注册必须用 `INamingService`，不能用 `IDiscoveryClient`**。
  - `AutoRegistration`（`naming/AutoRegistration.java:35`）是平台自带的自注册 bean（`@PostConstruct` 注册 / `@PreDestroy` 注销），是 nop-job/nop-graphql-grpc 的既定注册范式（声明 bean 而非在业务类内嵌注册逻辑）。
  - `ServiceInstance`（注册载荷）字段：`serviceName, addr, port, weight, instanceId, tags, metadata`，**无 `capacity` 字段**——需把 nop-stream 的 `capacity` 映射到 `weight` 或 `metadata`。
- **nop-stream 与平台 discovery 零集成**：`nop-stream/` 内 grep `IDiscoveryClient|INamingService|AutoRegistration` = 零匹配。`nop-stream/nop-stream-runtime/pom.xml` **未依赖 `nop-cluster-core`**（需新增 Maven 依赖）。
- `RuntimeNode` 类**不存在**（纯设计概念）；节点侧运行时是 `TaskManager`。
- `EdgeConfig.queueCapacity/receiveWindow/packetSize` 声明但**未接线**（Stage 26 处理）。

### 真正剩余的 gap

- **G50**：DeploymentPlan 不承载 subtask→node 分配；分配只在运行时 `ClusterRegistry`（ephemeral）+ `JobCoordinator` 内存。无 distributed `IDeploymentPlanProvider`/生成路径。
- **G51**：nop-stream 与平台 discovery 零集成；节点注册全部走自建 `ClusterRegistry.registerNode`，未经平台 `INamingService`/`AutoRegistration` 可被发现。

## Goals

- DeploymentPlan（或其可序列化 sidecar）能承载并持久化 subtask→node 的物理分配，使部署决策可脱离 runtime 单独审计/重建。
- 新增 distributed `IDeploymentPlanProvider`/生成路径，从 `PartitionedPlan` + 已注册活跃节点集（round-robin）生成带 node 映射的 DeploymentPlan。
- `JobCoordinator.assignTasks()` 改为消费 DeploymentPlan 中已物化的映射，ClusterRegistry 仍作运行时一致视图。
- 通过声明平台 `AutoRegistration` bean（消费 `INamingService`）使 `TaskManager` 节点经平台 discovery 可被发现，与现有 `ClusterRegistry` 共存（不预判 Stage 41 的 D7 决策）。
- 端到端：DISTRIBUTED 模式下 `execute()` 走完整链路 `PartitionedPlan → distributed provider → DeploymentPlan(node 映射) → JobCoordinator 消费 → ClusterRegistry 记录`，且节点经平台 discovery 注册可被查询。

## Non-Goals

- **Stage 41 的 D7 决策（ClusterRegistry 完全替换 vs 对接平台 discovery）不在本 plan**。本 plan 让 `INamingService` 注册与 ClusterRegistry 共存；替换/收敛留给 Stage 41（需人确认）。本 plan discovery 仅做注册（write），不在 nop-stream 内消费 discovery 读取来做分配/故障检测（那是 Stage 41）。
- SlotSharingGroup / slot pool / Flink 资源管理模型（vision §十、约束 7 排除）。
- 容量感知/亲和性分配（JobCoordinator 现状是纯 round-robin，无容量逻辑可复用；容量感知作为后续优化项，见 Non-Blocking Follow-ups）。
- 跨 JVM 网络传输（Stage 39/40）——本 plan 仍基于进程内 `EmbeddedDistributedExecutor`，但 plan/registration 路径设计为跨 JVM 可复用。
- per-task 故障检测与重试（Stage 25）、buffer pool（Stage 26）、RPC 接口扩容（Stage 28）。

## Scope

### In Scope

- DeploymentPlan 扩展（或新增 `DeploymentAssignment` sidecar）承载 subtask→node 映射，可序列化、可持久化。
- distributed `IDeploymentPlanProvider`（+ 生成逻辑）：基于活跃节点集 round-robin 生成映射；environment 在 DISTRIBUTED 模式选择该 provider。
- `JobCoordinator.assignTasks()` 改为消费 DeploymentPlan 已物化的映射；ClusterRegistry 仍记录运行时一致视图。
- 新增 `nop-stream-runtime` → `nop-cluster-core` Maven 依赖；声明 `AutoRegistration` bean（消费 `INamingService`）注册 nop-stream 节点（nodeId/endpoint/capacity 映射到 ServiceInstance）。
- 两套节点视图（平台 discovery 注册 vs ClusterRegistry lease）的一致性约束验证。

### Out Of Scope

- ClusterRegistry 完全替换为平台 discovery（Stage 41，D7）。
- 在 nop-stream 内消费 discovery 读取做分配/故障检测（Stage 41）。
- Leader election（Stage 38）、跨 JVM RPC（Stage 39）。
- 重分布/重调度（Stage 34/37）、容量感知分配（优化项）。

## Execution Plan

### Phase 1 — DeploymentPlan 承载 subtask→node 分配（G50）

Status: completed
Targets: `nop-stream-core/.../execution/plan/DeploymentPlan.java`, `PartitionedPlan.java`（含内部类 `VertexPlan`）, `IDeploymentPlanProvider.java`, `DefaultDeploymentPlanProvider.java`; `nop-stream-runtime/.../execution/DeploymentPlanGenerator.java`, `DeploymentPlanProviderImpl.java`, `JobCoordinator.java`

- Item Types: `Fix | Decision`

- [x] 设计可序列化的 subtask→node 分配表达（在 DeploymentPlan 内新增结构或新增 `DeploymentAssignment` sidecar），承载 `(vertexId, subtaskIndex) → nodeId`；保持 DeploymentPlan 不可变性与现有 round-trip 序列化。
- [x] 新增 distributed `IDeploymentPlanProvider` 实现 + 生成逻辑：基于活跃节点集 round-robin 生成映射（**复用 JobCoordinator 现有 round-robin 语义，不引入容量感知**——容量逻辑不存在，见 Non-Goals）。**接口需扩展**：当前 `IDeploymentPlanProvider.generateLocal(partitionedPlan)` 只接收 `PartitionedPlan`、无节点集入参；distributed 生成需接收活跃节点集（新增 distributed 方法或扩展签名，实现侧从 `ClusterRegistry.getActiveNodes()` 取节点）。
- [x] `StreamExecutionEnvironment.generateDeploymentPlan()`（`:402`，当前 mode-agnostic 恒走 `generateLocal`）增加 mode 分支：DISTRIBUTED 模式选择 distributed provider 生成带映射的 DeploymentPlan（LOCAL 仍走 `generateLocal`）。
- [x] `JobCoordinator.assignTasks()` 改为优先消费 DeploymentPlan 已物化的映射（当映射存在时），ClusterRegistry 仍记录运行时一致视图（assignTask/getTaskAssignment）。明确无映射时的 fallback 语义（LOCAL 生成的 plan 无映射 → 保持现有运行时分配行为）。

Exit Criteria:

- [x] DeploymentPlan（或 sidecar）可序列化 round-trip 且含 subtask→node 映射字段，有对应单测。
- [x] distributed provider 对 N 节点 + M subtask 生成完整 round-robin 映射，有单测覆盖。
- [x] DISTRIBUTED 模式下 environment 确实选择 distributed provider（**接线验证** #23：测试断言选择分支被触发，而非恒走 generateLocal）。
- [x] `JobCoordinator.assignTasks()` 在 DeploymentPlan 已含映射时消费映射并写入 ClusterRegistry。
- [x] **端到端验证**（#22）：DISTRIBUTED 模式从 `execute()` → provider → JobCoordinator 消费 → ClusterRegistry 记录的完整路径跑通，E2E 测试断言 ClusterRegistry 中每个 subtask 的分配与 DeploymentPlan 一致。
- [x] **接线验证**（#23）：E2E 测试断言 `JobCoordinator.assignTasks()` 确实读取 DeploymentPlan 映射（mock/计数器），而非绕过它。
- [x] **无静默跳过**（#24）：distributed 生成器在无活跃节点时显式失败（抛异常带上下文），不返回空映射；LOCAL fallback 行为有单测。
- [x] owner-doc：`01-architecture-baseline.md` §四 DeploymentPlan 行 + §五控制面角色已更新；否则明确 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 — 平台 discovery 接入（G51）

Status: completed
Targets: `nop-stream-runtime/.../cluster/StreamNodeAutoRegistration.java`（新增 `AutoRegistration` bean 声明，消费 `INamingService`）; `nop-stream/nop-stream-runtime/pom.xml`（新增 `nop-cluster-core` 依赖）; `cluster/`

- Item Types: `Fix | Decision`

- [x] `nop-stream/nop-stream-runtime/pom.xml` 新增对 `nop-cluster-core` 的 Maven 依赖（否则编译失败，找不到 `INamingService`/`AutoRegistration`）。
- [x] 声明一个 `AutoRegistration` bean（消费 `INamingService`），将 nop-stream 节点注册到平台 discovery。**注册用 `INamingService.registerInstance`（非只读的 `IDiscoveryClient`）**；遵循平台 bean 生命周期范式（`@PostConstruct` 注册 / `@PreDestroy` 注销），而非在 `TaskManager.start()` 内嵌注册逻辑。
- [x] 定义 `ServiceInstance` 字段映射：`instanceId`=nodeId、`addr`+`port`=endpoint、`capacity`→`weight` 或 `metadata["capacity"]`（`ServiceInstance` 无 capacity 字段）。映射记录于 design doc。
- [x] 数据流裁定（**必须裁定，不留 open**）：本 plan discovery 为**单向注册**（nop-stream → 平台），不在 nop-stream 内消费 discovery 读取做分配/故障检测（Stage 41）；ClusterRegistry 仍是 nop-stream 运行时分配/故障检测的唯一消费源。二者在节点存活期应一致（注册的节点 = lease 活跃的节点）。

Exit Criteria:

- [x] `nop-stream-runtime` 编译通过且能解析 `INamingService`/`AutoRegistration`（依赖已加）。
- [x] `AutoRegistration` bean 启动后平台 `INamingService.getInstances(...)` 能查到该 nop-stream 节点（注册内容含 nodeId/endpoint/capacity 经映射），有单测/集成测试验证。
- [x] **接线验证**（#23）：测试断言 bean 启动路径确实调用了 `INamingService.registerInstance`（mock verify），且 `IDiscoveryClient`（只读）未被误用于注册。
- [x] **无静默跳过**（#24）：`INamingService` 注册失败按策略显式处理（抛异常或降级告警），不 `catch{}` 吞掉。
- [x] **两视图一致性**（#23）：测试验证注册到平台 discovery 的节点集合与 `ClusterRegistry.getActiveNodes()` 在节点存活期一致（注册→lease 活跃；注销→lease 失效），不出现静默分歧。
- [x] **共存不破坏**：现有 `ClusterRegistry`-based 分配与故障检测路径仍通过（回归测试）。
- [x] owner-doc：`01-architecture-baseline.md` §五（discovery 单向注册 + ServiceInstance 映射 + D7 留待 Stage 41）已更新。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] G50：DeploymentPlan 承载 subtask→node 映射并被 distributed provider/JobCoordinator 端到端消费（非仅类型存在）。
- [x] G51：nop-stream 节点经平台 `INamingService`/`AutoRegistration` 注册可被发现（非零集成；注册用 INamingService 非 IDiscoveryClient）。
- [x] DISTRIBUTED 模式 E2E 从 `execute()` 到 ClusterRegistry 分配记录完整跑通。
- [x] 不存在被静默降级的 in-scope gap。
- [x] 受影响 owner docs 已同步到 live baseline。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证（a）JobCoordinator 运行时确实读取 DeploymentPlan 映射（非绕过），（b）AutoRegistration bean 运行时确实调用 INamingService 注册，（c）两视图无静默分歧，（d）无空方法体/静默跳过。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（或仅含 pre-existing baseline 发现）。

## Deferred But Adjudicated

### ClusterRegistry 完全替换为平台 discovery

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属于 Stage 41 决策点 D7（完全替换 vs 对接），需人确认；本 plan 让二者共存且 discovery 单向注册已满足 G51「节点注册/发现 WIRE 平台」目标，不预判 D7。
- Successor Required: yes
- Successor Path: Stage 41 (`41-cluster-registry`)

### 容量感知/亲和性分配

- Classification: `optimization candidate`
- Why Not Blocking Closure: JobCoordinator 现状是纯 round-robin，无容量逻辑可复用；当前 round-robin 对 supported baseline 足够。引入容量感知是新功能而非修缺口。
- Successor Required: no

## Non-Blocking Follow-ups

- 调度亲和性/负载感知分配（依赖上一条容量感知）。
- 节点容量动态变化的重平衡（Stage 34/37）。

## Closure

Status Note: G50 和 G51 均已端到端落地。DeploymentPlan 通过 DeploymentAssignment 承载物化的 subtask→node 映射，distributed provider 生成 round-robin 映射，JobCoordinator.assignTasks() 运行时消费该映射（非绕过），Environment 在 DISTRIBUTED 模式选择 distributed provider。平台 discovery 通过 StreamNodeAutoRegistration（INamingService.registerInstance）单向注册节点，与 ClusterRegistry 共存。两视图一致性、接线、无静默跳过、共存回归均有测试覆盖。
Completed: 2026-07-26

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task ses_06558b091ffe4Q5E2r5fEMEMoP)
- Audit Session: ses_06558b091ffe4Q5E2r5fEMEMoP
- Evidence:
  - **G50 DeploymentAssignment**: PASS — `DeploymentAssignment.java:33` `@DataBean Serializable`, `getNodeForSubtask()` at `:63`. `DeploymentPlan.java:83` `getAssignment()`.
  - **G50 round-robin generation**: PASS — `DeploymentPlanGenerator.generateDistributed()` at `:57`, `buildRoundRobinAssignment()` at `:97-116` has real loop logic (globalIndex % activeNodeIds.size()).
  - **G50 JobCoordinator consumes mapping (NOT bypassed)**: PASS — `JobCoordinator.assignTasks()` at `:209-210` reads `deploymentPlan.getAssignment()`, `:233-240` materialized path uses `assignment.getNodeForSubtask()`, `:241-245` fallback path uses runtime round-robin, `:254-256` writes to ClusterRegistry in both paths.
  - **G50 Environment mode branch**: PASS — `StreamExecutionEnvironment.generateDeploymentPlan()` at `:407-416`: DISTRIBUTED → `getExpectedNodeIds()` + `generateDistributed()`; LOCAL → `generateLocal()`.
  - **G51 StreamNodeAutoRegistration uses INamingService.registerInstance**: PASS — `StreamNodeAutoRegistration.java:100` `namingService.registerInstance(svc)`. No `IDiscoveryClient.getInstances()` call. Registration failure propagates (no catch{}).
  - **Two-view consistency**: PASS — `TaskManager.start()` registers with ClusterRegistry (`:143`), `StreamNodeAutoRegistration.start()` registers with INamingService (`:100`). Both cleaned up on stop. Test `TestDiscoveryRegistration.testTwoViewConsistencyDiscoveryVsClusterRegistry` validates.
  - **Anti-Hollow (no empty methods/stubs in new code)**: PASS — All new methods have full implementations. No empty bodies, no `continue` skipping, no swallowed exceptions. Missing assignment/RPC throws with context.
  - **Test coverage**: PASS — 5 test files with 32 tests total: `TestPlanModels` (9 tests), `TestDeploymentPlanGenerator` (8 tests), `TestJobCoordinatorAssignmentFromPlan` (4 tests), `TestDeploymentAssignmentE2E` (4 tests), `TestDiscoveryRegistration` (7 tests). All have real assertions.
  - **Deferred items**: Both valid non-blocking — ClusterRegistry→discovery convergence = Stage 41 D7 (out-of-scope improvement); capacity-aware = optimization candidate (no existing capacity logic to reuse).
  - `./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS (core 1055 + runtime 515 tests, 0 failures).
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → all items checked.
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` → 12 findings, all pre-existing (none in changed files).

Follow-up:

- Stage 25 (per-task failure detection), Stage 26 (buffer pool), Stage 28 (RPC 扩容) build on this deployment assignment baseline.
- Stage 41 D7: ClusterRegistry 完全替换 vs 对接平台 discovery — 需人确认。
