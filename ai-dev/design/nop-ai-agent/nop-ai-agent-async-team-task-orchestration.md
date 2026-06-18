# nop-ai-agent 异步团队任务编排（async 半部）

> Status: active design doc (plan 241 delivered)
> Last Reviewed: 2026-06-18
> Source: `ai-dev/plans/241-nop-ai-agent-async-team-task-orchestration.md`（L4-async-cross-process-orchestration, async half）
> Related: `nop-ai-agent-task-flow-integration.md`（plan 233 交付同步 `TeamTaskFlowOrchestrator`——本设计在其上新增 `executeAsync` 非阻塞入口）、`nop-ai-agent-orchestrator-auto-spawn.md`（plan 238 交付 `SpawnMemberAgentTaskStep`——本设计同步其保持同步切出 successor）、`nop-ai-agent-team-execute-flow.md`（plan 239 交付 `team-execute-flow` LLM 工具——本设计把其"包装 sync 结果为 completedFuture"接线为消费真实 `executeAsync`）、`nop-ai-agent-task-scheduler-daemon.md`（plan 236 交付 `TeamTaskSchedulerDaemon`——daemon 的 per-cycle async 派发为本计划 successor）

## 1. 范围与目的

把 nop-ai-agent 的团队任务 DAG 编排从「同步阻塞单进程——`TeamTaskFlowOrchestrator.execute(teamId)` 在调用线程上阻塞到整个 DAG 完成（每节点 `agentEngine.execute(request).join()` 同步阻塞成员 agent 执行）」扩展为「异步非阻塞 + DAG 并行分支真正并发」。

**交付物**（plan 241 / async 半部）：

- `TeamTaskFlowOrchestrator.executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>` 非阻塞入口。
- `MemberAgentTaskStep` async 化：返回 async `TaskStepReturn` 包装 `IAgentEngine.execute()` 既有 `CompletableFuture`，claim 同步、complete/失败在 async 回调。
- `execute(teamId)` 保留为 sync 便捷入口（`= executeAsync(teamId).join()` 语义等价包装，零回归）。
- `team-execute-flow` LLM 工具接线为消费真实 `executeAsync`（消除"伪 async 包装 sync"的 hollow 模式）。
- 并行分支真正并发（菱形 DAG A→{B,C}→D：B、C 真正并发执行，D 严格在 B、C 都完成后才触发）。

## 2. 核心裁定

### 2.1 消费既有 nop-task async 模型，不新增 nop-task 能力

`TeamTaskFlowOrchestrator.executeAsync` 把图执行结果经 `TaskStepReturn.getReturnPromise()` / `asyncOutputs()` 组合为 `CompletableFuture<TeamTaskFlowResult>`。

**为什么**：

- nop-task `TaskStepReturn` 已提供 `isAsync()` / `getReturnPromise()` / `ASYNC_RETURN()` / `ASYNC()` 完整 async 契约。
- nop-task `GraphTaskStep` 已用 `CompletableFuture<TaskStepReturn>` + `Map<String, CompletableFuture<?>>` + `buildWaitFuture(allFutures)` 调度图节点——**就绪节点（所有 waitSteps 前驱完成）已可经 CompletableFuture 并发触发**。
- 历史 plans 236/237/238/239/240 反复把本 carry-over 标注为"需 nop-task CompletableFuture async model（未落地）"作为前置阻塞。live repo 核对表明该前提**不准确**——nop-task 已提供完整 async 执行模型。本计划**纠正**该历史前提。
- 不触及 nop-task 核心（Protected Area）。

### 2.2 `execute(teamId)` 保留为 sync 入口 = `executeAsync(teamId).join()` 语义等价包装

既有 sync 调用方行为不变。

**为什么**：

- 零回归硬约束——plan 233/238 既有测试全绿。
- sync 便捷入口对程序化一次性调用仍有价值。
- 语义等价避免 sync/async 双语义分歧。

### 2.3 member step async 化 = 返回 async `TaskStepReturn` 包装 `agentEngine.execute(request)`，claim 同步、complete/失败在 async 回调

claim（CREATED→CLAIMED）在节点触发期同步完成（nop-task `GraphTaskStep` 在前驱完成时触发节点 execute）；成员 agent 的 `CompletableFuture` 经 `TaskStepReturn.ASYNC_RETURN(...)` 包装：成功且 completed → `completeTask`（CLAIMED→COMPLETED）；失败 / 非 completed / complete CAS 失败 → future 异常失败（任务保留 CLAIMED，不自动 abandon）。`claimTask` 空 + 已 COMPLETED → 幂等成功 future。

**为什么**：

- `IAgentEngine.execute()` 已返回 `CompletableFuture`，async 包装是自然消费。
- 诚实失败语义与同步路径逐条对齐（claim 失败 / 成员异常 / 非 completed / complete CAS 失败均诚实上报）。
- 任务保留 CLAIMED（不 abandon）与 plan 238 bound-member 失败模型一致——daemon 的 abandon 是无人值守恢复模型，不适用于一次性程序化编排器。

### 2.4 `SpawnMemberAgentTaskStep` async 化（plan 243 / `L4-spawn-step-async`，闭合 §4 最后 successor）

spawn-on-demand 节点（plan 238，无 bound member）经 `IMemberSpawner.spawnMember()`（同步契约）执行，但**执行被 offload 到独立 executor**，节点返回 async `TaskStepReturn`。

**机制裁定（plan 243 裁定 1/2/3）**：claim（CREATED→CLAIMED）在节点触发期同步完成（与 `MemberAgentTaskStep` 一致，保持 DAG 依赖序 + claim CAS 失败同步 fast-fail）；`spawnMember` 调用 + 三态解释 + `completeTask` 包装进 `CompletableFuture.supplyAsync(..., spawnExecutor)`，返回 `TaskStepReturn.ASYNC_RETURN(steppedFuture)`。三选一中采用 **(b) supplyAsync 卸载**：
- **拒绝 (a) 改 `IMemberSpawner` 契约为返回 future**：破坏 daemon（plans 236/237 同步调用 `spawnMember`）跨模块回归。
- **拒绝 (c) 绕过 spawner 直调 engine**：破坏 `IMemberSpawner` 扩展点抽象（`NoOp`/`Default`/自定义 swap-ability + 三态 honest 解释 hollow 化）。
- (b) 是纯局部、增量变更（只改 step 消费方），契约不变、daemon 不破、扩展点不破。

**executor 隔离（plan 243 裁定 3，核心正确性约束）**：spawn step 的 supplyAsync **必须运行在与 `DefaultAgentEngine` 的 supplyAsync 不同的线程池**上。`DefaultAgentEngine` 的 supplyAsync 用 one-arg 重载（= `ForkJoinPool.commonPool()`），spawn step worker 经 `spawnMember` 内部 `engine.execute(req).join()` 同步阻塞等待 engine future；若 spawn step 亦用 commonPool，并发 spawn 节点数 ≥ commonPool parallelism 时全部 commonPool 线程被 `.join()` park、engine future 无线程推进 → 停滞/死锁。故 `TeamTaskFlowOrchestrator` 持有/创建一个**独立于 commonPool** 的有界 daemon 线程池（池大小 = spawn 并发上限），透传给 `SpawnMemberAgentTaskStep`。wire-at-consumer：可经 `setSpawnStepExecutor` 注入自定义 executor；不注入则 orchestrator 懒创建 owned 池（`close()` 释放）。**禁止默认回退 commonPool**（`CompletableFuture.join()` 不参与 `ForkJoinPool.managedBlock`，不能依赖"commonPool 自愈"）。

**tenant-context 跨 worker 边界传播（plan 243 裁定 2，explicit-propagation 机制）**：标准 `ThreadLocal` 不跨 supplyAsync 边界，`completeTask`（DB store 读 `ThreadLocalTenantResolver.current()`）跨线程须 re-apply。采用 **explicit-propagation**（对所有 DAG 拓扑鲁棒，复用 plan 232 `ThreadLocalTenantResolver.set/clear` 范式）：orchestrator 在 `executeAsync`/`buildGraphForExecution` 入口（调用方线程，tenant 可靠设置）捕获一次 `ThreadLocalTenantResolver.current()`，注入每个 `SpawnMemberAgentTaskStep`；step 在 supplyAsync worker lambda 首行 `set(capturedTenant)`、finally `clear()`（不泄漏到池化线程）。该机制对 **enter 与 非 enter** spawn 节点都成立（菱形 `A→{B,C}→D` 中 B、C 的 `execute()` 运行在前驱完成线程，thread-based 捕获对该线程不可靠；explicit-propagation 不依赖线程捕获点）。

**async 路径诚实失败（plan 243 裁定 7，No Silent No-Op #24）**：claim 失败（同步 fast-fail throw）/ NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner 抛异常 / null 防御 各路径在 async 化后逐条对齐同步路径语义——经 async future 异常传播 → orchestrator `exceptionally` 捕获 → `TeamTaskFlowResult{success=false}`，任务保留 CLAIMED（不 abandon，与 `MemberAgentTaskStep` 失败模型一致）。已 COMPLETED 幂等为显式同步成功 return（非静默跳过）。

**闭合**：含 spawn 节点的图经 `executeAsync` 执行时，独立 spawn 分支（如菱形 `A→{B,C}→D` 中 B、C 均 spawn 节点）**真正并发**执行（非串行），D 严格在 B、C 都完成后才触发。§4 最后 successor `L4-spawn-step-async` 闭合。

### 2.5 cross-process daemon 协调切出为独立 successor `L4-cross-process-daemon-coordination`

不在本设计。

**为什么**：

- async（进程内非阻塞）与 cross-process（多实例分布式协调）是不同结果面。
- `claimTask` DB 级 CAS（`affected-row-count==1`）已提供多实例 double-dispatch 正确性地板（plan 227 / 240）。cross-process 是降冗余扫描的优化层（分布式锁 / 共享调度状态），非 async 前置。
- cross-process 需分布式锁基础设施（DB advisory lock / lock table / fencing token），是更重的独立结果面。
- 本设计闭合 async 后，cross-process successor 消费同一 async 基线扩展为多实例。

原 roadmap 项 `L4-async-cross-process-orchestration` 的 async 半部由本设计交付，cross-process 半部由后续 successor 接管。

### 2.6 并行分支并发 = nop-task `GraphTaskStep` 既有 CompletableFuture 调度的自然结果

独立分支（互无 `blockedBy` 依赖）的就绪节点经 `GraphTaskStep` 的 `buildWaitFuture` / `stepFutures` 并发触发；member step async 化后每个节点不阻塞调度线程，故独立分支真正并发。依赖序（如 D blockedBy {B,C}）由 `waitSteps` 保证 D 在 B、C 完成后才触发。

**为什么**：

- 复用 nop-task 既有图调度，不自行实现并发控制。
- async 化 member step 是解锁并发的关键（同步 join 会阻塞调度线程使并发退化为串行）。

### 2.7 `team-execute-flow` 接线为消费真实 `executeAsync`

消除"伪 async 包装 sync"的 hollow 模式。

**为什么**：

- plan 239 工具当前 `CompletableFuture.completedFuture(orchestrator.execute(...))` 是 hollow async（同步执行后再包装）。
- `executeAsync` 落地后工具应消费真实异步入口。
- 接线更新提供 Anti-Hollow 验证点（async 路径被真实入口消费）。

工具的 honest-failure / not-enabled / denied 语义不变。

### 2.8 async 路径诚实失败 = future 异常或 `TeamTaskFlowResult{success=false}`，不静默成功、不吞异常

结构性问题（null teamId / 无任务 / 未知 team / 环形 blockedBy）同步 fast-fail（与 `execute` 一致）。节点级失败（claim 失败 / 成员异常 / 非 completed / complete CAS 失败）经 future 异常传播 → orchestrator 捕获 → `TeamTaskFlowResult{success=false}`（failed/skipped taskIds 填充）。NoOp / 已 COMPLETED 幂等为显式语义（非静默跳过）。

**为什么**：No Silent No-Op（Minimum Rules #24）在 async 路径完整保留；与同步路径逐条对齐。

## 3. 拒绝的替代方案

### 3.1 自行实现并发控制（不消费 nop-task GraphTaskStep）

**拒绝**：本设计只在 orchestrator + member step 层把 `.syncGetOutputs()` / `.join()` 改为消费 async `TaskStepReturn` / `getReturnPromise()`，不触及 nop-task 核心。自行实现并发控制会：
- 重复 nop-task `GraphTaskStep` 已有的 CompletableFuture 调度逻辑。
- 增加 nop-ai-agent 与 nop-task 的耦合点（多个集成点而非一个）。
- 失去 nop-task `waitSteps` 依赖序保证的复用。

### 3.2 触及 nop-task 核心（修改 TaskStepReturn / GraphTaskStep 契约）

**拒绝**：nop-task 是 Protected Area（框架核心）。`TaskStepReturn` / `GraphTaskStep` 已提供完整 async 契约，本设计**消费**而非修改。任何 nop-task 核心变更需独立 plan-first（含跨模块回归 + 框架核心裁定）。

### 3.3 本设计含 cross-process daemon 协调

**拒绝**：cross-process 是不同结果面（多实例部署拓扑协调 vs 进程内执行模型）。`claimTask` DB 级 CAS 已提供多实例正确性地板。cross-process successor 消费本设计 async 基线扩展为多实例。

### 3.4 本设计自行实现 spawn-step async（已由 successor plan 243 闭合）

plan 241 聚焦 bound-member 路径的清晰 async 结果面，把 spawn-step async 切出为独立 successor。机制裁定（改契约 / supplyAsync+tenant-context 传播 / 绕过 spawner 三选一）是独立结果面，已由 plan 243（`L4-spawn-step-async`）裁定采用 (b) supplyAsync 卸载 + explicit tenant 传播 + 独立 executor 隔离并落地（见 §2.4）。

### 3.5 `execute(teamId)` 同步路径独立保留（不复用 executeAsync 的 build 路径）

**拒绝**：保留双路径（sync 用 `syncGetOutputs()`、async 用 `asyncOutputs()`）会引入 sync/async 双语义分歧（不同代码路径、不同 build helper）。本设计采用 `execute = executeAsync().join()` 语义等价包装，共享同一 build 路径（`buildGraphForExecution` helper），消除双语义分歧。

## 4. 关键实现约束

### 4.1 nop-task `TaskStepReturn.ASYNC` 的同步 short-circuit 行为

nop-task 的 `TaskStepReturn.ASYNC(nextStepName, future)` 在 future 已同步完成（如失败 fast-path）时 short-circuit 调用 `FutureHelper.syncGet(future)`，会同步抛出异常。`executeAsync` 需在 `built.task.execute(taskRt)` 周围加 try/catch，把同步抛出的节点失败转换为 honest failed future（保持 future-completes-with-success=false 契约）。这是本设计落地过程中发现的关键约束，非 nop-task bug——这是 nop-task 允许 async step 在已同步完成时退化为 sync 的优化。

### 4.2 `MemberAgentTaskStep` 的 async 回调不能抛同步异常

`engineFuture.whenComplete(...)` 的回调内任何 throw 都会被 `whenComplete` 自身捕获并以原异常（非回调的 throw）传播到返回的 future。本设计在回调内显式调用 `steppedFuture.completeExceptionally(...)`，回调本身不 throw，确保异常以我们选择的 NopAiAgentException 类型精确传播。

### 4.3 异步路径的 ExecutionRecorder 线程安全

`ExecutionRecorder` 已用 `ConcurrentHashMap` / `AtomicInteger`（plan 233 Phase 2 为并行分支准备）。async 化后 markStart / markComplete / markFailed 可能在不同 CompletableFuture 调度线程上并发触发，既有线程安全设施满足要求，无需扩展。

## 5. 显式 successor

- **cross-process daemon 协调**（分布式锁 / 多实例扫描协调 / 共享调度状态）→ `L4-cross-process-daemon-coordination`。
- **`TeamTaskSchedulerDaemon` per-cycle async 派发** → 独立 successor（让一个慢任务不阻塞整个扫描周期）。
- ~~**`SpawnMemberAgentTaskStep` async 化** → `L4-spawn-step-async`~~（**已闭合**，plan 243 / §2.4）。
- **多成员 per-task 路由**（plan 239 carry-over `L4-multi-member-per-task-routing`）。
- **spawn session 复用 / 池化**（plan 239 carry-over `L4-spawn-session-pooling`，optimization candidate）。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over `L4-nop-task-decorator`）。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over `L4-dynamic-graph-edit`）。
