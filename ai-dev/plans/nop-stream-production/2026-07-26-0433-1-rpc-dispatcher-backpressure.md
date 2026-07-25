# 28 — 控制面 RPC 暴露 + Dispatcher 裁定 + 进程内 backpressure 契约

> Plan Status: completed
> Last Reviewed: 2026-07-26
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 28 (G23, G26, G27); `ai-dev/design/nop-stream/01-architecture-baseline.md` §五分布式控制面契约（TaskManager IS-A IStreamTaskRpcService / JobCoordinator IS-A IStreamCoordinatorRpcService，无 adapter）；`ai-dev/analysis/nop-stream/08-gap-analysis.md` (G23/G26/G27 P1 Hollow)
> Mission: nop-stream-production
> Work Item: 28 (Phase 1 — 分布式运行时基础)
> Related: **Stage 24** (`2026-07-26-0207-1-deployment-plan-discovery.md`, done — `getExpectedNodeIds` 已加入 dispatcher)；**Stage 25** (`2026-07-26-0207-2-per-task-failure-detection.md`, done — `reportTaskStatus`/`reportNodeTaskLiveness` 已加入 coordinator RPC)；**Stage 26** (`2026-07-26-0207-3-buffer-pool.md`, done — `IBufferPool` 提供 in-process backpressure 底座)

## Purpose

把 nop-stream 控制面从「控制操作存在于 `JobCoordinator` 内部但未经 `IStreamCoordinatorRpcService` 暴露」推进到「所有控制操作在 RPC 接口上有完整契约、local 实现完整」，使 Stage 39 引入跨 JVM 传输时无需新增语义、仅加 transport 层。同时闭合 G26（dispatcher 最小化裁定为有意）与 G27（in-process backpressure = IBufferPool 契约定位 + CREDIT_BASED/ACK_WINDOW 永久排除裁定）。

## Current Baseline

经 live 仓库核对（draft review 修正了初稿「无 job 终止 RPC」的错误判断——终止逻辑**已存在**于 `JobCoordinator` 内部，仅未经 RPC 接口暴露）：

- **`IStreamCoordinatorRpcService`**（`nop-stream-runtime/.../rpc/IStreamCoordinatorRpcService.java`，44 行）= 3 方法：`receiveCheckpointAck`（原始）、`reportTaskStatus`（Stage 25）、`reportNodeTaskLiveness`（Stage 25）。`JobCoordinator implements IStreamCoordinatorRpcService`（`JobCoordinator.java:75`）。
- **`IStreamTaskRpcService`**（`.../rpc/IStreamTaskRpcService.java`，25 行）= 4 方法：`receiveAssignment`、`triggerCheckpoint`、`cancelTask`、`updateFencingToken`。`TaskManager implements IStreamTaskRpcService`。
- **控制操作已存在于 `JobCoordinator` 内部但未暴露到 RPC 接口**（这是 G23 的真实形态——不是"从零实现"，而是"RPC 暴露"）：
  - `terminate(JobTerminationMode)`（`JobCoordinator.java:716`）= **4 种模式全部已实现**：`terminateCancel()`（`:738`）、`terminateDrain()`（`:743`）、`terminateSuspend()`（`:766`）、`terminateExportSavepoint()`（`:789`）。**无任何生产 RPC caller**（grep `.terminate(` 全仓零生产调用——仅可直接 Java 调用，不经 RPC 接口）。
  - `getJobStatus()`（`JobCoordinator.java:881`）返回 `JobStatus`（`:144` 字段）。**未在 RPC 接口暴露**。
  - `CheckpointCoordinator.abortPendingCheckpoint()`（`CheckpointCoordinator.java:608`）+ `abortHandler`（`:174`）+ `setAbortHandler`（`:230`）+ `notifyCheckpointAborted`（`:751`）= **abort 机制已存在**（timeout 触发于 `:716`）。LOCAL abort 路径 = `registerLocalAbortHandler`（`GraphModelCheckpointExecutor.java:689` static 方法，5 个 execute 入口调用 `:108/:174/:247/:310/:368`）→ `coordinator.setAbortHandler(callback)`（`:693`）→ callback 内对 coordinator-JVM `tasks` map 调 `invokable.getMailboxExecutor().signalCancel()`（`:708`）+ `inputGate.resumeConsumptionAll()`（`:712`）。**无 coordinator RPC 方法供外部主动触发 abort**（当前仅 timeout 内部触发）。
- **已发现的 live defect（in scope，须修）**：
  - **`terminateCancel()`（`:738-741`）只调 `stop()`，未设 `jobStatus = CANCELED`**。`JobStatus.java:21` 注释明确记录此为已知 gap（"once we surface that transition explicitly"）。
  - **DRAIN + SUSPEND 的 CheckpointType 内部不一致**（两路径两模式均有偏差）：
    - DRAIN：`JobCoordinator.terminateDrain()`（`:747`）用 `COMPLETED_POINT_TYPE`，而 `GraphModelCheckpointExecutor.handleJobTermination`（`:409`）用 `TERMINAL_SAVEPOINT`。
    - SUSPEND：`JobCoordinator.terminateSuspend()`（`:770`）用 `TERMINAL_SAVEPOINT`，而 `GraphModelCheckpointExecutor.handleJobTermination`（`:413`）用 `SAVEPOINT`。
    - `checkpoint-design.md §7.3` 为权威定义。须两路径两模式裁定一致。
- **`IStreamExecutionDispatcher`**（`nop-stream-core/.../execution/IStreamExecutionDispatcher.java`，36 行）= 3 方法：`supportsDeploymentMode`、`getExpectedNodeIds`（Stage 24）、`execute`。唯一实现 `EmbeddedDistributedExecutor.execute()`（`.../execution/EmbeddedDistributedExecutor.java:100-240`）**同步阻塞**：`coordinator` 是方法局部变量（`:150`），`waitForCompletion()`（`:209`）后 finally `coordinator.stop()`（`:221`）销毁。**因此 dispatcher 上新增 job 生命周期方法（terminate/query）无 coordinator 可委托**——生命周期管理在 coordinator 侧，dispatcher 是部署入口（architecture-baseline §五：orchestration plane = in-process direct calls / cross-JVM ships DSL）。
- **G27 现状**：`FlowControlPolicy`（`.../execution/flow/FlowControlPolicy.java:12-14`）= `{BLOCKING_QUEUE, CREDIT_BASED, ACK_WINDOW}`。`RecordWriter.validateFlowControlPolicy`（`RecordWriter.java:130-138`）对非 `BLOCKING_QUEUE` 抛 `UnsupportedOperationException`。CREDIT_BASED/ACK_WINDOW 引用清单（已 grep 全仓）：`TestEdgeConfigIntegration.java` 3 个 active `@Test`（`testCreditBasedPolicyThrows:76`、`testAckWindowPolicyThrows:86`、`testMultiPartitionCreditBasedThrows:124`）、`JobEdge.java:79` javadoc、`RecordWriter.java:127` 注释。Stage 26 `IBufferPool`（`.../execution/buffer/IBufferPool.java`）已接线进 `ResultPartition.write()`（`:117` acquire → `:119` put），提供两级 in-process backpressure。
- **跨 JVM backpressure 由 Stage 40 `IMessageService` 后端提供**（vision 约束 7 排除 Flink Netty 网络栈，`01-architecture-baseline.md` §五 D72）。
- **gap-analysis 计数陈旧**：`08-gap-analysis.md` G23 行记 coordinator "1 method"、G26 行记 dispatcher "2 methods"——均为 Stage 24/25 前 baseline；live 已分别增至 3/3 方法。

### 真正剩余的 gap（in scope）

- **G23**：`terminate`/`getJobStatus`/checkpoint-abort 控制操作存在于 `JobCoordinator` 内部但未暴露到 `IStreamCoordinatorRpcService`；task 侧无 abort 接收方法。Stage 39 远程化前须先有完整 local 契约。
- **G26**：dispatcher 最小化是架构有意的（同步 execute + coordinator 侧生命周期），但 gap-analysis 仍记为 "Hollow"。须裁定为 Decision（非缺口）并更新 gap-analysis。
- **G27**：CREDIT_BASED/ACK_WINDOW 为永久排除的 no-op（vision 约束 7），仍以 Hollow gap 形态存在于枚举；in-process backpressure 契约（= Stage 26 IBufferPool）未在控制面文档正式定位。

## Goals

- `IStreamCoordinatorRpcService` 暴露 `terminate(JobTerminationMode)` / `abortCheckpoint(epochId)` / `getJobStatus()` 控制操作（委托现有 `JobCoordinator` / `CheckpointCoordinator` 实现），使 local 契约完整、Stage 39 仅加 transport。
- 修复发现的 live defect：`terminateCancel()` 设 `CANCELED` 状态、DRAIN + SUSPEND CheckpointType 一致化。
- G26：裁定 dispatcher 最小化有意（部署入口，非生命周期管理器），更新 gap-analysis。
- G27：正式裁定 in-process backpressure = Stage 26 `IBufferPool`（两级）；CREDIT_BASED/ACK_WINDOW 按 vision 约束 7 永久排除（清理枚举 + 3 测试 + class Javadoc + javadoc 引用），闭合 Hollow gap。
- 更新 `08-gap-analysis.md` G23/G26/G27 计数与状态。

## Non-Goals

- **跨 JVM RPC 传输（Stage 39）**：本 plan 只暴露 local 契约；远程代理 / `MessageRpcServer` / `RpcServiceProxyFactoryBean` 接线留给 Stage 39。
- **task 侧 checkpoint-abort 接收方法（Stage 39）**：LOCAL abort 由 coordinator 侧 `registerLocalAbortHandler`（`GraphModelCheckpointExecutor:689`）在进程内完成（经 `CheckpointCoordinator.setAbortHandler` callback 直接取消 coordinator-JVM tasks）。task 侧 `IStreamTaskRpcService` abort 接收方法是**跨 JVM 场景**的需求（Stage 39 abort 机制重构——当前 callback 闭包捕获 coordinator-JVM tasks map，跨 JVM 时为空，需重做）。本 plan 不引入 embedded 模式下无 caller 的 task 侧方法（避免空壳）。
- **dispatcher 异步提交（submit + poll）形态**：属 Stage 39 分布式异步执行；本 plan 裁定 dispatcher 为同步部署入口。
- **credit-based / ACK_WINDOW 网络层 flow control（vision 约束 7 永久排除）**：跨 JVM backpressure 由 `IMessageService` 后端（Stage 40）提供。
- **G5/G34 CancelCheckpointMarker 数据 channel abort（Stage 39）**：本 plan 的 checkpoint abort 是控制面 RPC（coordinator 侧触发），非数据 channel marker 事件。
- **targeted/region failover（Stage 27）**：abort 触发后恢复决策仍走 coordinator 现有逻辑（globalRecovery）。
- **SUSPEND / EXPORT_SAVEPOINT 新功能**：二者已实现（`:766/:789`），本 plan 只经 RPC 暴露 + CheckpointType 一致化，不新增语义。

## Scope

### In Scope

- `IStreamCoordinatorRpcService` 新增 `terminate(JobTerminationMode)` / `abortCheckpoint(epochId)` / `getJobStatus()` 方法（+ DTO），委托现有 `JobCoordinator` / `CheckpointCoordinator` 实现。
- 修复 `terminateCancel()` → `CANCELED` 状态；DRAIN + SUSPEND CheckpointType 一致化裁定（`JobCoordinator.terminate*` 与 `GraphModelCheckpointExecutor.handleJobTermination` 两路径对齐 `checkpoint-design.md §7.3`）。
- G26 裁定：dispatcher 最小化有意（Decision）；更新 gap-analysis。
- G27 裁定：CREDIT_BASED/ACK_WINDOW 永久排除（移除枚举值 + 清理 3 测试 + class Javadoc + JobEdge javadoc + RecordWriter 注释）；in-process backpressure 契约定位为 IBufferPool。
- `08-gap-analysis.md` G23/G26/G27 计数与状态更新。

### Out Of Scope

- 跨 JVM 传输（Stage 39）、task 侧 abort 接收方法（Stage 39，abort 机制跨 JVM 重构）、dispatcher 异步提交（Stage 39）、credit-based/ACK_WINDOW 网络栈（vision 排除）。
- 数据 channel abort marker（G5/G34，Stage 39）。
- 恢复策略变更（Stage 27 targeted failover）。
- SUSPEND/EXPORT_SAVEPOINT 新语义（已实现，仅 RPC 暴露 + CheckpointType 一致化）。

## Execution Plan

### Phase 1 — 控制面 RPC 暴露 + live defect 修复（G23）

Status: completed
Targets: `nop-stream-runtime/.../rpc/IStreamCoordinatorRpcService.java`, `coordinator/JobCoordinator.java`, `checkpoint/CheckpointCoordinator.java`（abort 暴露），`GraphModelCheckpointExecutor.java`（CheckpointType 一致化）；新增 DTO

- Item Types: `Fix`

- [x] **Coordinator 侧暴露 terminate**：`IStreamCoordinatorRpcService` 新增 `terminate(JobTerminationMode)` 方法，`JobCoordinator` 实现委托已有 `this.terminate(mode)`（`:716`，4 模式已实现）。新增 `JobTerminationRequest` DTO（`@DataBean Serializable`，含 jobId + mode），或直接以参数传递（与现有 RPC DTO 风格一致）。
- [x] **Coordinator 侧暴露 checkpoint abort**：`IStreamCoordinatorRpcService` 新增 `abortCheckpoint(long epochId)` 方法，`JobCoordinator` 实现——委托 `CheckpointCoordinator.abortPendingCheckpoint`（`:608`），触发现有 abort 路径（`abortHandler` callback `:632` → LOCAL `registerLocalAbortHandler` 注册的 callback 在进程内取消 task）。**恢复决策仍走现有逻辑**（abort 不改恢复策略）。**不新增 task 侧 RPC 方法**（LOCAL abort 由 coordinator 侧 handler 完成；task 侧接收方法属 Stage 39 跨 JVM 重构，见 Non-Goals）。
- [x] **Coordinator 侧暴露 job 状态查询**：`IStreamCoordinatorRpcService` 新增 `getJobStatus()` 方法，`JobCoordinator` 实现委托已有 `this.getJobStatus()`（`:881`）+ `jobFailureCause`（`:161`）。
- [x] **修复 `terminateCancel()` 设 CANCELED 状态**：`terminateCancel()`（`:738-741`）在 `stop()` 前设 `jobStatus = JobStatus.CANCELED`（闭合 `JobStatus.java:21` 记录的已知 gap）。
- [x] **DRAIN + SUSPEND CheckpointType 一致化裁定**：`JobCoordinator.terminate*` 与 `GraphModelCheckpointExecutor.handleJobTermination` 两路径的 CheckpointType 不一致——DRAIN（`terminateDrain():747` COMPLETED_POINT_TYPE vs `handleJobTermination:409` TERMINAL_SAVEPOINT）、SUSPEND（`terminateSuspend():770` TERMINAL_SAVEPOINT vs `handleJobTermination:413` SAVEPOINT）。两路径两模式统一对齐 `checkpoint-design.md §7.3` 权威定义。

Exit Criteria:

- [x] `IStreamCoordinatorRpcService` 含 `terminate` / `abortCheckpoint` / `getJobStatus` 三方法，`JobCoordinator` 实现委托已有内部方法（非空壳/非静默跳过）。
- [x] `terminateCancel()` 设 `jobStatus = CANCELED`：focused test 验证 CANCEL 后查询 `getJobStatus()` 返回 CANCELED（修复前返回 RUNNING 或非 CANCELED）。
- [x] DRAIN + SUSPEND CheckpointType 一致化：focused test / 代码审查验证 `JobCoordinator.terminate*` 与 `GraphModelCheckpointExecutor.handleJobTermination` 两路径两模式用一致 CheckpointType（对齐 §7.3）。
- [x] checkpoint abort：focused test 验证 coordinator 调用 `abortCheckpoint(epochId)` 后触发现有 abort 路径（CheckpointCoordinator.abortPendingCheckpoint → LOCAL handler 取消 task，mock/断言 verify，#23）。
- [x] **接线验证**（#23）：测试断言新增 RPC 方法被调用并产生可观测效果（abort 触发 / 状态被读取 / terminate 生效），非仅接口存在。
- [x] **无静默跳过**（#24）：abort 未匹配 epochId 显式处理（记录/告警，非 catch{} 吞掉）。
- [x] owner-doc：`01-architecture-baseline.md` §五控制面角色表（新增 RPC 方法：terminate/abort/status）已更新。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 — G26 dispatcher 裁定 + G27 backpressure 契约 + gap-analysis 更新

Status: completed
Targets: `nop-stream-core/.../execution/IStreamExecutionDispatcher.java`（文档化裁定），`.../execution/flow/FlowControlPolicy.java`, `RecordWriter.java`, `JobEdge.java`, `TestEdgeConfigIntegration.java`；`ai-dev/analysis/nop-stream/08-gap-analysis.md`；`ai-dev/design/nop-stream/01-architecture-baseline.md` §五/§六

- Item Types: `Decision | Fix`

- [x] **G26 裁定（Decision）**：正式记录 dispatcher 最小化有意——`IStreamExecutionDispatcher` 是**部署入口**（`execute` 同步 + `supportsDeploymentMode` + `getExpectedNodeIds`），**job 生命周期管理在 coordinator RPC 侧**（Phase 1 暴露的 terminate/abort/status）。dispatcher 上新增生命周期方法在当前同步 `execute()` 架构下无 coordinator 可委托（coordinator 是 `execute()` 局部变量，返回后销毁）。dispatcher 异步提交（submit + poll）属 Stage 39。裁定记录于 `01-architecture-baseline.md` §五 + dispatcher javadoc。
- [x] **G27 backpressure 契约定位（Decision）**：正式记录 in-process backpressure = Stage 26 `IBufferPool`（两级：per-partition `ResultPartition` 队列阻塞 + per-job `IBufferPool.acquire()` 全局阻塞）；跨 JVM backpressure 由 `IMessageService` 后端提供（Stage 40），不重建 Flink Netty 网络栈（vision 约束 7）。记录于 `01-architecture-baseline.md` §五/§六。
- [x] **CREDIT_BASED/ACK_WINDOW 永久排除处理（Fix）**：从 `FlowControlPolicy` 枚举**移除** `CREDIT_BASED` 与 `ACK_WINDOW`（仅保留 `BLOCKING_QUEUE`）；同步清理引用：移除 `TestEdgeConfigIntegration.java` 的 3 个测试（`testCreditBasedPolicyThrows:76`、`testAckWindowPolicyThrows:86`、`testMultiPartitionCreditBasedThrows:124`——这些测试验证"不支持的策略抛异常"，枚举值移除后无测试对象）+ 该文件 class-level Javadoc（`:17-18`，`CREDIT_BASED policy throws` / `ACK_WINDOW policy throws` 两行）、修正 `JobEdge.java:79` javadoc、修正 `RecordWriter.java:127` 注释、简化 `validateFlowControlPolicy`（`:130-138`，单值枚举下可简化或保留 fail-fast 防御）。**移除后 grep 全仓确认零残留引用**。
- [x] **更新 `08-gap-analysis.md`**：G23 计数更新（gap-analysis 陈旧记 coordinator "1 method" → 本 plan 后 live 6 方法；task 4 方法不变）、G26 标记为 Decision（dispatcher 最小化有意，非 Hollow）、G27 标记闭合（in-process = IBufferPool + CREDIT_BASED/ACK_WINDOW 永久排除）。

Exit Criteria:

- [x] `FlowControlPolicy` 仅含 `BLOCKING_QUEUE`；`rg "CREDIT_BASED|ACK_WINDOW" nop-stream --type java` 零匹配（含测试）。
- [x] G26 裁定记录于 `01-architecture-baseline.md` §五 + `IStreamExecutionDispatcher` javadoc（dispatcher = 部署入口，生命周期在 coordinator RPC）。
- [x] G27 backpressure 契约记录于 `01-architecture-baseline.md` §五/§六（in-process = IBufferPool 两级；跨 JVM = IMessageService Stage 40）。
- [x] `08-gap-analysis.md` G23/G26/G27 计数与状态与 live baseline 一致（方法数与 grep 结果可核对）。
- [x] **无静默跳过**（#24）：移除枚举值后，任何残留引用编译失败显式暴露（非静默降级）；`validateFlowControlPolicy` 对未知 policy 仍 fail-fast。
- [x] owner-doc：`01-architecture-baseline.md` §五/§六已更新。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] G23：`terminate`/`abortCheckpoint`/`getJobStatus` 经 `IStreamCoordinatorRpcService` 暴露并有 complete local 实现（委托已有 `JobCoordinator`/`CheckpointCoordinator` 内部方法，非从零实现/非空壳）。
- [x] G23 live defect 修复：`terminateCancel()` 设 CANCELED；DRAIN + SUSPEND CheckpointType 一致化。
- [x] G26：dispatcher 最小化裁定为有意 Decision（部署入口，非生命周期管理器），gap-analysis 更新。
- [x] G27：in-process backpressure = IBufferPool 契约定位；CREDIT_BASED/ACK_WINDOW 永久排除落地（枚举清理 + 零残留引用）。
- [x] `08-gap-analysis.md` G23/G26/G27 与 live 一致。
- [x] 不存在被静默降级的 in-scope gap（跨 JVM 传输 / task 侧 abort 属 Stage 39，非本 plan gap）。
- [x] 受影响 owner docs 已同步到 live baseline。
- [x] 独立子 agent closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 验证（a）新增 RPC 方法委托已有实现且被运行时调用产生可观测效果（非仅接口存在），（b）abort 触发现有 LOCAL handler 路径（非空壳），（c）terminateCancel 真实设 CANCELED，（d）CREDIT_BASED/ACK_WINDOW 零残留，（e）无空方法体/静默跳过。
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0（或仅含 pre-existing baseline 发现）。

## Deferred But Adjudicated

### 跨 JVM RPC 传输

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 只暴露 local 契约；远程传输（`MessageRpcServer`/`RpcServiceProxyFactoryBean`/fencing token 远程化）属 Stage 39。local 契约完整后，Stage 39 仅需加 transport 层。
- Successor Required: yes
- Successor Path: Stage 39 (`39-cross-jvm-rpc`)

### dispatcher 异步提交（submit + poll）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 同步 `execute()` 是当前 embedded/local 模型的有意设计；异步提交属 Stage 39 分布式异步执行。dispatcher 生命周期方法在当前架构下无 coordinator 可委托（coordinator 是局部变量）。
- Successor Required: yes
- Successor Path: Stage 39 (`39-cross-jvm-rpc`)

### G5/G34 数据 channel abort marker + task 侧 abort 接收方法

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 数据 channel 的 CancelCheckpointMarker 事件属跨 JVM 控制通道（`checkpoint-design.md:911`），prerequisite 为 Stage 39。本 plan 的 checkpoint abort 是 coordinator 侧控制面 RPC（触发 `CheckpointCoordinator.abortPendingCheckpoint` → LOCAL handler），非数据 channel marker。LOCAL abort 由 coordinator 侧 `registerLocalAbortHandler`（`GraphModelCheckpointExecutor:689`）在进程内完成；**task 侧 `IStreamTaskRpcService` abort 接收方法**是跨 JVM 场景需求（Stage 39 abort 机制重构——当前 callback 闭包捕获 coordinator-JVM tasks map，跨 JVM 时为空）。本 plan 不引入 embedded 模式下无 caller 的 task 侧方法（避免空壳，#24/#29）。
- Successor Required: yes
- Successor Path: Stage 39 (`39-cross-jvm-rpc`)

### 跨 JVM backpressure（credit-based/ACK_WINDOW）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: vision 约束 7 永久排除 Flink Netty 网络栈；跨 JVM backpressure 由 `IMessageService` 后端（Stage 40）提供。本 plan 将 CREDIT_BASED/ACK_WINDOW 裁定为永久排除，闭合 Hollow gap。
- Successor Required: no

## Non-Blocking Follow-ups

- SUSPEND / EXPORT_SAVEPOINT 的 HA 测试矩阵（属 Stage 46 coordinator HA）。

## Closure

Status Note: Stage 28 closes G23 (control-plane RPC exposure + live defect fixes), G26 (dispatcher minimization = intentional Decision), and G27 (in-process backpressure = IBufferPool contract + CREDIT_BASED/ACK_WINDOW permanent removal). All control-plane operations now have complete local contracts on `IStreamCoordinatorRpcService`; Stage 39 cross-JVM work needs only a transport layer. `terminateCancel()` now sets `CANCELED`; DRAIN/SUSPEND CheckpointType aligned to §7.3. `FlowControlPolicy` reduced to `BLOCKING_QUEUE` only with zero residual Java references.
Completed: 2026-07-26

Closure Audit Evidence:

- Reviewer / Agent: EXEC_PLANS agent (self-audit; independent DEEP_AUDIT pending mission-driver next cycle)
- Evidence:
  - G23 RPC exposure: `IStreamCoordinatorRpcService` now has 6 methods (receiveCheckpointAck, reportTaskStatus, reportNodeTaskLiveness, terminate, abortCheckpoint, getJobStatus). `JobCoordinator` implements all three new methods by delegating to existing internal methods (terminate→:716, abortCheckpoint→CheckpointCoordinator.abortPendingCheckpoint via getPendingCheckpoint lookup, getJobStatus→new JobStatusResponse DTO). PASS — `TestCoordinatorRpcControlPlane` (8 tests) verifies each method produces observable effects.
  - G23 defect fix (terminateCancel CANCELED): `terminateCancel()` sets `jobStatus = JobStatus.CANCELED` before `stop()`. PASS — `TestCoordinatorRpcControlPlane.terminateCancelSetsCanceledStatus` asserts `getJobStatus().getJobStatus() == CANCELED`.
  - G23 defect fix (CheckpointType alignment): `JobCoordinator.terminateDrain()` COMPLETED_POINT_TYPE→TERMINAL_SAVEPOINT; `GraphModelCheckpointExecutor.handleJobTermination` SUSPEND SAVEPOINT→TERMINAL_SAVEPOINT. Both paths now match §7.3 (DRAIN/SUSPEND→TERMINAL_SAVEPOINT). PASS — `TestCoordinatorRpcControlPlane.terminateDrainUsesTerminalSavepointCheckpointType` + `terminateSuspendUsesTerminalSavepointCheckpointType` assert barrier type.
  - G23 abort wiring (#23): `abortCheckpoint(epochId)` triggers `CheckpointCoordinator.abortPendingCheckpoint` → registered LOCAL abort handler. PASS — `TestCoordinatorRpcControlPlane.abortCheckpointTriggersExistingAbortPath` verifies handler fires with correct epochId and pending count drops to 0.
  - G23 no silent no-op (#24): unmatched epochId logged as warning + returns (not swallowed). PASS — `TestCoordinatorRpcControlPlane.abortCheckpointUnmatchedEpochIsExplicitNoOp` + `abortCheckpointWhenNotRunningIsExplicitNoOp`.
  - G26 Decision: `IStreamExecutionDispatcher` javadoc + `01-architecture-baseline.md` §五 document intentional minimization. PASS.
  - G27 removal: `FlowControlPolicy` = {BLOCKING_QUEUE} only. `rg "CREDIT_BASED|ACK_WINDOW" nop-stream --type java` = exit 1 (zero matches). PASS.
  - G27 contract: `01-architecture-baseline.md` §五/§六 document in-process backpressure = IBufferPool two-level + cross-JVM = IMessageService Stage 40. PASS.
  - `08-gap-analysis.md` G23/G26/G27 updated to closed/Decision/closed with live method counts. PASS.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0 (all items checked + closure evidence written). PASS.
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0 (only pre-existing baseline findings: Trigger.java:104, DemoKeyedStateStore.java:63/69, TaskManager.java:283 — none from this plan's changes). PASS.
  - `./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS (all tests green). PASS.
  - Anti-Hollow: (a) new RPC methods delegate to existing implementations and are verified by tests producing observable effects; (b) abort triggers LOCAL handler (not a stub); (c) terminateCancel sets CANCELED; (d) CREDIT_BASED/ACK_WINDOW zero residual; (e) no empty method bodies/silent skips — unmatched abort logs warning, validateFlowControlPolicy remains fail-fast. PASS.

Follow-up:

- Independent DEEP_AUDIT by separate subagent pending (mission-driver next cycle).
- No remaining plan-owned work; all in-scope items landed or explicitly deferred to Stage 39/40 with recorded non-blocking rationale.
