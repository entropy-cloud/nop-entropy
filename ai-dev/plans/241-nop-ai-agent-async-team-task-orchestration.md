# 241 nop-ai-agent Async Team Task Flow Orchestration（异步 DAG 编排：非阻塞 CompletableFuture 入口 + 并行分支执行）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-async-cross-process-orchestration（本计划交付 **async** 半部；**cross-process** 半部切出为显式 successor `L4-cross-process-daemon-coordination`，见 Non-Goals / 设计裁定 4）

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/240-nop-ai-agent-team-task-reclaim-and-timeout-abandon.md`（Non-Blocking Follow-ups：`异步 / 跨进程流编排执行（plan 239 carry-over L4-async-cross-process-orchestration）：Classification: successor plan required`）；同一 carry-over 在 plans `236`/`237`/`238`/`239` 的 Non-Goals / Non-Blocking Follow-ups 中亦显式延期为独立 successor；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（`L4-async-cross-process-orchestration` P1 carry-over）
> Related: `233`（交付同步 `TeamTaskFlowOrchestrator`——本计划在其上新增 `executeAsync` 非阻塞入口）、`238`（交付 `SpawnMemberAgentTaskStep`——本计划同步其 async 化）、`239`（交付 `team-execute-flow` LLM 工具——本计划把其"包装 sync 结果为 completedFuture"接线为消费真实 `executeAsync`）、`236`（交付 `TeamTaskSchedulerDaemon`——daemon 的 per-cycle async 派发为本计划 successor，见 Non-Goals）、`240`（carry-over 源）

## Purpose

把 nop-ai-agent 的团队任务 DAG 编排从"**同步阻塞单进程**——`TeamTaskFlowOrchestrator.execute(teamId)` 在调用线程上阻塞到整个 DAG 完成（`task.execute(taskRt).syncGetOutputs()`），且每个图节点的 `MemberAgentTaskStep` 经 `agentEngine.execute(request).join()` 同步阻塞成员 agent 执行"扩展为"**异步非阻塞 + DAG 并行分支真正并发**：新增 `executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>` 非阻塞入口，图节点返回 nop-task 的 async `TaskStepReturn`（包装成员 agent 既有的 `CompletableFuture` 而非 `.join()`），使独立分支在 nop-task `GraphTaskStep` 既有 CompletableFuture 调度下真正并发执行、调用线程不再被整个 DAG 阻塞"。

**关键事实修正**：plans 236/237/238/239/240 反复把本 carry-over 标注为"需 nop-task CompletableFuture async model（未落地）"作为前置阻塞。live repo 核对表明该前提**不准确**——nop-task 已提供完整 async 执行模型（`TaskStepReturn.isAsync()` / `getReturnPromise() → CompletionStage<TaskStepReturn>` / `ASYNC_RETURN(CompletionStage)` / `ASYNC(nextStepName, CompletionStage)` / `asyncOutputs()`，且 `GraphTaskStep` 已用 `CompletableFuture` 调度就绪节点并发执行）。因此本计划**消费既有的 nop-task async 模型**，不触及 nop-task 核心（Protected Area），只需在 nop-ai-agent 的 orchestrator + member step 层把 `.syncGetOutputs()` / `.join()` 改为消费 async `TaskStepReturn` / `getReturnPromise()`。

**Scope 裁定（Granularity Rule）**：原 roadmap 项 `L4-async-cross-process-orchestration` 捆绑"async（进程内非阻塞）"+"cross-process（多实例分布式协调）"两个维度。本计划只交付 **async** 半部（一个可独立收口的明确结果面），把 **cross-process daemon 协调**（分布式锁 / 多实例扫描协调 / 共享调度状态）切出为独立 successor `L4-cross-process-daemon-coordination`。理由：(1) 两个维度是不同结果面——async 是单实例非阻塞执行模型，cross-process 是多实例部署拓扑协调；(2) `claimTask` 已是 DB 级 CAS 条件 UPDATE（plan 227），多实例 double-dispatch 的正确性地板已存在，cross-process 是在此之上的优化层（分布式锁降冗余扫描），非 async 的前置；(3) Plan guide Rule 2 / Rule 5：过宽则拆 successor。cross-process successor 在本计划闭合 async 后消费同一 async 基线扩展为多实例。

## Current Baseline

基于 live repo 核对（来源：plan 233 / 238 / 239 / 240 closure audit evidence 已对照 live code path 验证；本段描述现状）：

- **`TeamTaskFlowOrchestrator.execute(teamId)` 同步阻塞 ✅**（plan 233）：`TeamTaskFlowOrchestrator.java` 的 `execute(String)` 在 `task.execute(taskRt).syncGetOutputs()`（live repo `TeamTaskFlowOrchestrator.java` execute 方法尾部）阻塞调用线程到整个 team-task DAG 完成。`syncGetOutputs()` 内部 `FutureHelper.syncGet(future).get()`（`TaskStepReturn.java`）即阻塞 join。无 `executeAsync` / 非 CompletableFuture 入口。
- **`MemberAgentTaskStep` 同步 join ✅**（plan 233）：图节点 step 在 `agentEngine.execute(request).join()`（live repo `MemberAgentTaskStep.java` execute 方法内）同步阻塞成员 agent 执行。Javadoc 自述 "synchronous join — design 裁定 3"。成员 agent 失败 / 非完成态 / `completeTask` CAS 失败 / `claimTask` 失败均抛 `NopAiAgentException`（诚实失败，任务保留 CLAIMED 不自动 abandon，与 plan 238 bound-member 失败模型一致）。
- **`SpawnMemberAgentTaskStep` ✅**（plan 238）：无 bound member 的图节点 step，节点运行期咨询 `IMemberSpawner` spawn 成员 agent 后同步执行。本计划须同步其 async 化。
- **`IAgentEngine.execute()` 已返回 `CompletableFuture<AgentExecutionResult>` ✅**：成员 agent 引擎**已是 async**（`IAgentEngine.java` execute 签名返回 `CompletableFuture<AgentExecutionResult>`）。这是 async 化的直接消费点——member step 可包装此 future 而非 join。
- **nop-task 已有完整 async 执行模型 ✅**（本计划关键依赖，**纠正历史"未落地"前提**）：
  - `TaskStepReturn`（`io.nop.task.TaskStepReturn`）：`isAsync()` / `getReturnPromise() → CompletionStage<TaskStepReturn>` / `asyncOutputs() → CompletionStage<Map>` / `ASYNC_RETURN(CompletionStage<TaskStepReturn>)` / `ASYNC(String nextStepName, CompletionStage<?>)` 工厂。async TaskStepReturn 经 `hookFuture` 保证最终归约为非 async 结果。
  - `GraphTaskStep`（`io.nop.task.step.GraphTaskStep`）：已用 `CompletableFuture<TaskStepReturn>` + `Map<String, CompletableFuture<?>> stepFutures` + `buildWaitFuture(allFutures)` 调度图节点——**就绪节点（所有 waitSteps 前驱完成）已可经 CompletableFuture 并发触发**。async 化 member step 后，独立分支将真正并发执行。
  - 即：**本计划不新增 nop-task 任何 async 能力，只消费既有契约**。
- **`team-execute-flow` LLM 工具包装 sync 为伪 async ✅**（plan 239）：`TeamExecuteFlowExecutor` 调用 `orchestrator.execute(teamId)`（同步）后将结果包装进 `CompletableFuture.completedFuture(...)`；Javadoc 自述 "async / cross-process orchestration is an explicit Non-Goal successor"。本计划可使其消费真实 `executeAsync`（接线更新，见 In Scope）。
- **`claimTask` DB 级 CAS ✅**（plan 227 / 240）：`DbTeamTaskStore.claimTask` 条件 `UPDATE ... WHERE STATUS='CREATED'` affected-row-count==1 判定；`InMemoryTeamTaskStore` 经 `ConcurrentHashMap.compute` 原子 CAS。多实例 / 多线程并发 claim 同一 task 只有 1 个胜出——cross-process double-dispatch 正确性地板已存在（支撑裁定 4）。
- **零 async 编排代码**：grep `executeAsync|AsyncTeamTask` 在 `nop-ai/nop-ai-agent/src/main/.../team/flow/` 返回 0 命中（NEXT_ITEM 已确认 carry-over 未落地）。

## Goals

- **`TeamTaskFlowOrchestrator.executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>`**：非阻塞入口。消费 nop-task 既有 async 模型——图执行结果经 `TaskStepReturn.getReturnPromise()` / `asyncOutputs()` 组合为 `CompletableFuture<TeamTaskFlowResult>`，调用线程不被整个 DAG 阻塞。结构性问题（null teamId / 无任务 / 未知 team / 环形 blockedBy）同步 fast-fail（与 `execute` 一致，抛 `NopAiAgentException`）；节点失败经 future 异常传播为 `TeamTaskFlowResult{success=false}`（诚实失败，非静默）。
- **`execute(teamId)` 保留为 sync 便捷入口（零回归）**：既有同步 API 保留为 `executeAsync(teamId).join()` 语义等价包装（或等价的同步路径），既有 `execute` 调用方（含 `team-execute-flow` 在接线前 / 既有测试）行为不变。**sync 语义不变是硬约束**——既有 plan 233/238 测试全绿。
- **`MemberAgentTaskStep` async 化**：图节点返回 async `TaskStepReturn`（包装 `agentEngine.execute(request)` 的 `CompletableFuture` 而非 `.join()`）。claim 在节点触发期同步完成（CREATED→CLAIMED）；成员 agent 执行结果在 async 回调中处理：成功且 completed → `completeTask`（CLAIMED→COMPLETED）；失败 / 非 completed / complete CAS 失败 → future 异常失败（任务保留 CLAIMED，不自动 abandon，与同步路径语义一致）。`claimTask` 空 + 已 COMPLETED 的幂等成功路径保留。
- **并行分支真正并发验证（Anti-Hollow #22）**：菱形 DAG（A→{B,C}→D，bound-member 节点）经 `executeAsync` 执行时，B 与 C（互无依赖）**真正并发**执行（非串行），D 严格在 B、C 都完成后才触发（依赖序由 nop-task `GraphTaskStep` waitSteps 保证）。验证方式：可观测的并发证据（如 B、C 执行时间区间重叠的执行序快照 / 并发计数断言），非仅最终 COMPLETED 状态。
- **`team-execute-flow` 接线为消费真实 async**：`TeamExecuteFlowExecutor` 由"包装 sync `execute` 结果为 `completedFuture`"改为消费 `executeAsync`（真实异步），消除"伪 async 包装 sync"的 hollow 模式。LLM 工具的 honest-failure / not-enabled / denied 语义不变。
- **诚实失败语义在 async 路径完整保留（No Silent No-Op #24）**：claim 失败 / 成员 agent 异常 / 非 completed 态 / complete CAS 失败 / 未知 team / 环形依赖均经 future 异常或 `success=false` 诚实上报，不静默成功、不吞异常。NoOp / 已 COMPLETED 幂等路径为显式语义（非静默跳过）。
- **设计文档**：新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`（记录核心裁定：消费既有 nop-task async 模型 / `executeAsync` 入口 / member step async / 并行分支并发 / cross-process 切出 successor / 纠正"async model 未落地"历史前提 / 拒绝替代方案）+ 更新 `nop-ai-agent-actor-runtime-vision.md`（async orchestration 状态）+ roadmap §4 Layer 4（async 半部落地，cross-process 仍 successor）。

## Non-Goals

- **跨进程 daemon 协调（分布式锁 / 多实例扫描协调 / 共享调度状态）**：独立 successor `L4-cross-process-daemon-coordination`。`claimTask` DB 级 CAS 已提供多实例 double-dispatch 正确性地板；分布式锁为降冗余扫描的优化层，是不同结果面（部署拓扑协调 vs 进程内执行模型）。Classification: successor plan required。见设计裁定 4。
- **`TeamTaskSchedulerDaemon` per-cycle async 派发**：daemon（plan 236）的每周期按任务同步派发（claim → engine.execute().join() → complete/abandon）保持同步。daemon 是"无人值守周期调度器"，其 async 化是独立结果面（async daemon 派发 = 让一个慢任务不阻塞整个扫描周期）。本计划只 async 化 orchestrator（程序化一次性 DAG 执行器）。Classification: successor plan required。
- **`SpawnMemberAgentTaskStep` async 化**：无 bound member 的 spawn-on-demand 节点**保持同步**（不变更）。原因：`IMemberSpawner.spawnMember()` 是**同步契约**（Javadoc 自述 "spawn and execute it synchronously"，`DefaultMemberSpawner` 内部 `engine.execute().join()`），与 `MemberAgentTaskStep` 消费的 `IAgentEngine.execute()`（已返回 `CompletableFuture`）是**不同契约**。async 化 spawn 节点需三选一：(a) 改 `IMemberSpawner` 契约为返回 future → 破坏 daemon（plans 236/237 同步调用 spawnMember）跨模块回归；(b) `CompletableFuture.supplyAsync` 卸载到 worker 池 → 引入 tenant-context 跨线程传播问题（`DbTeamTaskStore.claimTask/completeTask` 读 `currentTenant()`，`supplyAsync` worker lambda 默认不继承 tenant context）；(c) 绕过 spawner 直调 engine → 破坏 spawner 扩展点抽象（hollow）。三者均为独立结果面，切出为 successor `L4-spawn-step-async`（随 cross-process / daemon-async successor 一并裁定）。本计划 async 化只覆盖 bound-member 路径（`MemberAgentTaskStep`）。Classification: successor plan required。
- **nop-task 核心 async 模型变更**：nop-task 已提供完整 async 模型（见 Current Baseline），本计划**消费**而非修改。nop-task 为 Protected Area（框架核心），plan-first。Classification: out-of-scope（无需变更）。
- **多成员 per-task 路由**（plan 239 carry-over `L4-multi-member-per-task-routing`）。Classification: successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over `L4-spawn-session-pooling`）。Classification: optimization candidate。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over `L4-nop-task-decorator`）。Classification: successor plan required。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over `L4-dynamic-graph-edit`）。Classification: successor plan required。
- **task RE-CLAIM / 超时自动 ABANDON**：已由 plan 240 落地。本计划不扩展。
- **`team-execute-flow` 之外的 LLM 工具 async 化**：仅接线该工具消费 `executeAsync`；其他 LLM 工具（`team-status` 等）不在 async 化范围。
- **修改 `ITeamTaskStore` / `ITeamManager` / `IAgentEngine` / `ITeamFlowManager` 契约**：消费原样契约，仅在 orchestrator / step 层内部变更。

## Scope

### In Scope

- `io.nop.ai.agent.team.flow` 包：
  - `TeamTaskFlowOrchestrator.java` — 新增 `executeAsync(String teamId) → CompletableFuture<TeamTaskFlowResult>` 非阻塞入口（消费 nop-task `getReturnPromise()`/`asyncOutputs()`）；`execute(String)` 保留为 sync 入口（`executeAsync(...).join()` 语义等价，零回归）
  - `MemberAgentTaskStep.java` — async 化：返回 async `TaskStepReturn`（包装 `agentEngine.execute(request)` 的 `CompletableFuture`，claim 同步完成、complete/失败在 async 回调），诚实失败语义不变
  - `FlowSinkStep`（orchestrator 内部 sink）/ `ExecutionRecorder` — 适配 async 执行序记录（依赖序可观测，独立分支序非确定）
  - 注：`SpawnMemberAgentTaskStep` **不在 async 化范围**（保持同步，见 Non-Goals / 设计裁定 3a）；`executeAsync` 图中含 spawn 节点时该节点仍同步阻塞调度线程（全 bound-member 图获完整并发，spawn-node 图的部分并发为 successor）
- `io.nop.ai.agent.tool` 包：
  - `TeamExecuteFlowExecutor.java` — 接线：由包装 sync `execute` 改为消费 `executeAsync`（真实 async），honest-failure/not-enabled/denied 语义不变
- 测试文件（新）：
  - `TestTeamTaskFlowOrchestratorAsync.java`（executeAsync 非阻塞返回 + 最终结果正确 + honest failure 经 future 异常 / success=false）
  - `TestAsyncMemberStepParallelBranches.java`（菱形 DAG A→{B,C}→D：B、C 并发执行证据 + D 依赖序严格）
  - `TestAsyncMemberStepHonestFailure.java`（claim 失败 / 成员异常 / 非 completed / complete CAS 失败 → 任务保留 CLAIMED + 诚实失败 future）
  - `TestExecuteSyncZeroRegression.java`（`execute` sync 入口语义不变，既有 plan 233/238 行为全绿）
  - `TestTeamExecuteFlowAsyncWiring.java`（LLM 工具消费真实 executeAsync 接线 + Anti-Hollow）
- 设计文档：`ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`（新）+ `nop-ai-agent-actor-runtime-vision.md`（async 状态更新）+ `nop-ai-agent-roadmap.md` §4 Layer 4（async 半部落地标注）

### Out Of Scope

- 见 Non-Goals（cross-process daemon 协调 / daemon async 派发 / spawn-step async / nop-task 核心变更 / 多成员路由 / spawn 池化 / decorator / 动态改图 / task-reclaim 扩展 / 其他 LLM 工具 / store/manager/engine/spawner 契约变更 均为显式 successor 或 out-of-scope）

### 设计裁定（Pre-Adjudicated）

以下裁定在 plan 撰写阶段已确定，执行时直接遵循：

1. **`executeAsync` 消费 nop-task 既有 async 模型，不新增 nop-task 能力**。`TeamTaskFlowOrchestrator.executeAsync` 把图执行结果经 `TaskStepReturn.getReturnPromise()` / `asyncOutputs()` 组合为 `CompletableFuture<TeamTaskFlowResult>`。理由：(1) nop-task `TaskStepReturn` 已提供 `isAsync()`/`getReturnPromise()`/`ASYNC_RETURN()`/`ASYNC()` 完整 async 契约，`GraphTaskStep` 已用 CompletableFuture 调度就绪节点并发；(2) 历史"async model 未落地"前提经 live repo 核对不准确，本计划纠正；(3) 不触及 nop-task Protected Area。

2. **`execute(teamId)` 保留为 sync 入口 = `executeAsync(teamId).join()` 语义等价包装**（或经同一内部 build 路径的同步执行）。既有 sync 调用方行为不变。理由：(1) 零回归硬约束——plan 233/238 既有测试全绿；(2) sync 便捷入口对程序化一次性调用仍有价值；(3) 语义等价避免 sync/async 双语义分歧。

3. **member step async 化 = 返回 async `TaskStepReturn` 包装 `agentEngine.execute(request)`，claim 同步、complete/失败在 async 回调**。claim（CREATED→CLAIMED）在节点触发期同步完成（nop-task `GraphTaskStep` 在前驱完成时触发节点 execute），成员 agent 的 `CompletableFuture` 经 `TaskStepReturn.ASYNC(...)` 包装：成功且 completed → `completeTask`（CLAIMED→COMPLETED）；失败/非 completed/complete CAS 失败 → future 异常失败（任务保留 CLAIMED，不自动 abandon）。`claimTask` 空 + 已 COMPLETED → 幂等成功 future（保留既有语义）。理由：(1) `IAgentEngine.execute()` 已返回 `CompletableFuture`，async 包装是自然消费；(2) 诚实失败语义与同步路径逐条对齐（claim 失败 / 成员异常 / 非 completed / complete CAS 失败 均诚实上报）；(3) 任务保留 CLAIMED（不 abandon）与 plan 238 bound-member 失败模型一致。

3a. **`SpawnMemberAgentTaskStep` 保持同步（不在本计划 async 化）**。spawn-on-demand 节点（plan 238，无 bound member）继续经 `IMemberSpawner.spawnMember()`（同步契约）同步执行。理由：(1) `IMemberSpawner.spawnMember()` 是同步契约（Javadoc 自述同步 spawn+execute，`DefaultMemberSpawner` 内部 `engine.execute().join()`），与 `MemberAgentTaskStep` 消费的 `IAgentEngine.execute()`（已返回 `CompletableFuture`）不同——前者无可包装的既有 future；(2) async 化需改 spawner 契约（破坏 daemon 跨模块）/ supplyAsync 卸载（tenant-context 跨线程传播）/ 绕过 spawner（hollow 抽象）三选一，均为独立结果面；(3) bound-member 路径是团队编排的主路径（团队预绑定成员），spawn-on-demand 是 opt-in 特性；(4) 本计划 async 化聚焦 bound-member 路径的清晰结果面，spawn-step async 切出为 successor `L4-spawn-step-async`。**已知限制（诚实记录）**：`executeAsync` 图中若含 spawn 节点，该节点同步阻塞 nop-task 调度线程——全 bound-member 图获完整并行并发，含 spawn 节点的图并发度受限（spawn 节点串行）；完整 spawn-node 并发为 successor。`executeAsync` 入口对含 spawn 节点的图仍正确完成（正确性不变），仅并发度受限。

4. **cross-process daemon 协调切出为独立 successor `L4-cross-process-daemon-coordination`**，不在本计划。理由：(1) async（进程内非阻塞）与 cross-process（多实例分布式协调）是不同结果面（Plan guide Rule 2）；(2) `claimTask` DB 级 CAS 已提供多实例 double-dispatch 正确性地板（affected-row-count==1），cross-process 是降冗余扫描的优化层（分布式锁 / 共享调度状态），非 async 前置；(3) cross-process 需分布式锁基础设施（DB advisory lock / lock table / fencing token），是更重的独立结果面；(4) 本计划闭合 async 后，cross-process successor 消费同一 async 基线扩展为多实例。原 roadmap 项 `L4-async-cross-process-orchestration` 的 async 半部由本计划交付，cross-process 半部由后续 successor 接管。

5. **并行分支并发 = nop-task `GraphTaskStep` 既有 CompletableFuture 调度的自然结果，非 orchestrator 新增并发逻辑**。独立分支（互无 blockedBy 依赖）的就绪节点经 `GraphTaskStep` 的 `buildWaitFuture`/`stepFutures` 并发触发；member step async 化后每个节点不阻塞调度线程，故独立分支真正并发。依赖序（D blockedBy {B,C}）由 `waitSteps` 保证 D 在 B、C 完成后才触发。理由：(1) 复用 nop-task 既有图调度，不自行实现并发控制；(2) async 化 member step 是解锁并发的关键（同步 join 会阻塞调度线程使并发退化为串行）。

6. **`team-execute-flow` 接线为消费真实 `executeAsync`**（消除"伪 async 包装 sync"的 hollow 模式）。理由：(1) plan 239 工具当前 `CompletableFuture.completedFuture(orchestrator.execute(...))` 是 hollow async（同步执行后再包装）；(2) `executeAsync` 落地后工具应消费真实异步入口；(3) 接线更新提供 Anti-Hollow 验证点（async 路径被真实入口消费）。工具的 honest-failure / not-enabled / denied 语义不变。

7. **async 路径诚实失败 = future 异常或 `TeamTaskFlowResult{success=false}`，不静默成功、不吞异常**。结构性问题（null teamId / 无任务 / 未知 team / 环形 blockedBy）同步 fast-fail（与 `execute` 一致）。节点级失败（claim 失败 / 成员异常 / 非 completed / complete CAS 失败）经 future 异常传播 → orchestrator 捕获 → `TeamTaskFlowResult{success=false}`（failed/skipped taskIds 填充）。NoOp / 已 COMPLETED 幂等为显式语义（非静默跳过）。理由：No Silent No-Op #24 在 async 路径完整保留；与同步路径逐条对齐。

## Execution Plan

### Phase 1 - async 编排核心（executeAsync + member step async 化 + sync 零回归 + 设计裁定落档）

Status: completed
Targets: `io.nop.ai.agent.team.flow`（TeamTaskFlowOrchestrator / MemberAgentTaskStep / FlowSinkStep / ExecutionRecorder）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Decision`（消费既有 nop-task async 模型 / executeAsync 入口 / member step async / claim 同步 complete 回调 / cross-process 切出 successor / 并行分支并发 / execute sync 语义等价）、`Fix`（orchestrator syncGetOutputs 阻塞 + member step join 阻塞 = carry-over gap；team-execute-flow 伪 async hollow）、`Proof`

- [x] `TeamTaskFlowOrchestrator` 新增 `executeAsync(String teamId) → CompletableFuture<TeamTaskFlowResult>`：复用既有图 build 路径（load tasks → buildGraph cycle detection → resolveMember → 构造 MemberAgentTaskStep/SpawnMemberAgentTaskStep 节点 + synthetic sink → GraphTaskStep），执行结果经 `TaskStepReturn.getReturnPromise()`/`asyncOutputs()` 组合为 `CompletableFuture<TeamTaskFlowResult>`；结构性问题（null teamId / 无任务 / 未知 team / 环形 blockedBy）同步抛 `NopAiAgentException`（fast-fail）；节点失败经 future 异常 → `TeamTaskFlowResult{success=false}`
- [x] `execute(String teamId)` 重构为 sync 便捷入口：`executeAsync(teamId).join()` 语义等价包装（或经同一 build 路径的同步 `.syncGetOutputs()` 执行），既有调用方行为不变
- [x] `MemberAgentTaskStep.execute` async 化：claim（CREATED→CLAIMED）同步完成 → 包装 `agentEngine.execute(request)` 的 `CompletableFuture` 为 async `TaskStepReturn`（`ASYNC`/`ASYNC_RETURN`）→ 成功且 completed → `completeTask`；失败/非 completed/complete CAS 失败 → future 异常；`claimTask` 空 + 已 COMPLETED → 幂等成功；任务保留 CLAIMED（不 abandon）；`ExecutionRecorder` markStart/markComplete/markFailed 在 async 回调中正确触发
- [x] `SpawnMemberAgentTaskStep` **保持同步不变更**（裁定 3a）——`executeAsync` 图中含 spawn 节点时该节点仍同步执行（正确性不变，并发度受限为已知限制）
- [x] `ExecutionRecorder` 适配 async 执行序：依赖序可观测（后继任务 start 严格晚于前驱 complete），独立分支序非确定（合法）；线程安全（并发 markStart/markComplete）
- [x] `nop-ai-agent-actor-runtime-vision.md` 标注 async team-task orchestration 为 successor 接管中（裁定理由落档：消费既有 nop-task async 模型 / executeAsync / member step async / cross-process 切出 successor / 纠正"async model 未落地"前提）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TeamTaskFlowOrchestrator` 含 `executeAsync(String) → CompletableFuture<TeamTaskFlowResult>` 方法，返回的 future 非阻塞（调用线程不被整个 DAG 阻塞，可观测：调用后 future 未完成、DAG 推进期间调用线程可继续）
- [x] `execute(String)` 保留为 sync 入口且语义等价（既有 plan 233/238 测试全绿，零回归）
- [x] `MemberAgentTaskStep` 返回 async `TaskStepReturn`（非 `.join()` 阻塞），claim 同步、complete/失败在 async 回调；任务失败保留 CLAIMED（不 abandon）
- [x] `SpawnMemberAgentTaskStep` 未变更（保持同步，裁定 3a）——含 spawn 节点的 `executeAsync` 图仍正确完成（正确性不变）
- [x] **无静默跳过**（#24）：claim 失败 / 成员异常 / 非 completed / complete CAS 失败 → 诚实失败 future（非静默成功 / 非吞异常）；已 COMPLETED 幂等为显式成功（非静默跳过）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **No new test required for pure refactor 不适用**：本 Phase 引入新公共方法 `executeAsync` + 改变 member step 执行模型，focused 测试在 Phase 2（#25）；Phase 1 compile + 既有测试零回归即可
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - async focused 测试 + 并行分支验证 + honest failure + LLM 工具接线

Status: completed
Targets: `io.nop.ai.agent.tool.TeamExecuteFlowExecutor`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/`（新测试）

- Item Types: `Proof`、`Fix`（team-execute-flow 伪 async hollow 接线）

- [x] 编写 `TestTeamTaskFlowOrchestratorAsync`：`executeAsync` 返回未完成 future（调用线程不阻塞）→ 最终 `TeamTaskFlowResult` 正确（线性 A→B→C 全 COMPLETED）；honest failure（节点失败 → `success=false` + failed/skipped taskIds）；结构 fast-fail（null teamId / 无任务 / 未知 team / 环形 blockedBy 同步抛 NopAiAgentException）
- [x] 编写 `TestAsyncMemberStepParallelBranches`（菱形 DAG A→{B,C}→D）：断言 **B、C 真正并发**（可观测并发证据——执行时间区间重叠快照 / 并发计数 ≥2 断言，非仅最终 COMPLETED 状态）；断言 **D 依赖序严格**（D.start 严格晚于 B.complete 与 C.complete，经 ExecutionRecorder 执行序快照证明）；Anti-Hollow 断言并发真实发生
- [x] 编写 `TestAsyncMemberStepHonestFailure`：claim 失败（task 已被他人 claim）→ 失败 future + 任务状态正确；成员 agent 抛异常 → 失败 future + 任务保留 CLAIMED（不 abandon）；成员 agent 非 completed 态（failed/cancelled/paused）→ 失败 future + 保留 CLAIMED；`completeTask` CAS 失败（task 已被转换）→ 失败 future；已 COMPLETED 幂等 → 成功（显式）
- [x] 编写 `TestExecuteSyncZeroRegression`：`execute` sync 入口在 linear / 菱形 / spawn（unbound member + functional spawner）/ 失败传播 各场景下行为与 plan 233/238 既有断言一致（全绿）
- [x] `TeamExecuteFlowExecutor` 接线：由 `CompletableFuture.completedFuture(orchestrator.execute(...))` 改为消费 `orchestrator.executeAsync(...)`（真实 async）；honest-failure / not-enabled / denied 语义不变
- [x] 编写 `TestTeamExecuteFlowAsyncWiring`：工具消费真实 `executeAsync`（Anti-Hollow 接线断言——工具内部确实调用 executeAsync 而非 execute+completedFuture 包装，经 spy/计数器/方法追踪验证）；结果正确 + honest failure 传播

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestTeamTaskFlowOrchestratorAsync` 全绿（非阻塞返回 + 最终结果 + honest failure + 结构 fast-fail）
- [x] **端到端 + 并行验证**（#22）：菱形 DAG 经 `executeAsync` 完整跑通，B、C 并发证据可观测，D 依赖序严格（执行序快照证明）——闭合"同步阻塞 + 串行"核心缺口
- [x] `TestAsyncMemberStepHonestFailure` 全绿（claim 失败 / 成员异常 / 非 completed / complete CAS 失败 / 已 COMPLETED 幂等 各路径 + 任务保留 CLAIMED 不 abandon）
- [x] `TestExecuteSyncZeroRegression` 全绿（既有 plan 233/238 行为零回归）
- [x] `TeamExecuteFlowExecutor` 消费 `executeAsync`（非 execute+completedFuture 包装）；honest-failure/not-enabled/denied 语义不变
- [x] **接线验证**（#23）：`TeamExecuteFlowExecutor` 运行时确实调用 `executeAsync`（spy/计数器断言，非仅类型存在）；`MemberAgentTaskStep` async 路径运行时确实执行（task 状态机 CLAIMED→COMPLETED 经 DB/in-memory 验证）
- [x] **无静默跳过**（#24）：所有失败路径诚实 future 异常 / `success=false`；无空方法体 / continue / TODO / 吞异常
- [x] 新增功能各有对应 focused 测试覆盖（executeAsync 入口 / 并行分支 / honest failure 各路径 / sync 零回归 / LLM 工具接线 各有测试）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + 设计文档 + roadmap 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/TestAsyncTeamTaskFlowEndToEnd.java`（新）、`ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`（新）、`nop-ai-agent-actor-runtime-vision.md`、`nop-ai-agent-roadmap.md` §4

- Item Types: `Proof`

- [x] 编写端到端测试（async 完整路径）：构造团队 + 多任务 DAG（含菱形 A→{B,C}→D + bound member）→ `executeAsync` → 调用线程不阻塞（future 初始未完成）→ 最终全 COMPLETED + `TeamTaskFlowResult{success=true}`；断言 B、C 并发 + D 依赖序
- [x] 编写端到端测试（async honest failure 传播）：菱形 DAG 中 B 的成员 agent 失败 → `executeAsync` future 完成 `TeamTaskFlowResult{success=false}` + failed含B + skipped含D（nop-task GraphTaskStep 短路取消后继）；B 保留 CLAIMED（不 abandon）
- [x] 编写端到端测试（spawn-on-demand 正确性）：无 bound member + functional spawner → `executeAsync` 含 spawn 节点的图仍正确完成（全 COMPLETED）——证明 spawn 节点保持同步（裁定 3a）不破坏 `executeAsync` 正确性（spawn 节点同步阻塞调度线程但图最终完成）
- [x] 编写 sync 对比 e2e：`execute` sync 入口在同一 bound-member DAG 下结果与 `executeAsync(...).join()` 一致（语义等价证明）
- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`：记录核心裁定（消费既有 nop-task async 模型 / executeAsync 入口 / member step async / 并行分支并发 / `SpawnMemberAgentTaskStep` 保持同步切出 successor / cross-process 切出 successor / 纠正"async model 未落地"历史前提）+ 拒绝替代方案（自行实现并发控制 / 触及 nop-task 核心 / 本计划含 cross-process / 本计划含 spawn-step async）。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md`：async team-task orchestration 从 successor 标注为已落地（executeAsync 非阻塞 + member step async + 并行分支并发）；标注 cross-process daemon 协调 / daemon async 派发仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：`L4-async-cross-process-orchestration` 标注 async 半部已落地 + 新增 `L4-cross-process-daemon-coordination` successor 行（cross-process 半部）
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 `executeAsync(teamId)` 入口 → 图 build → async member step（spawn 节点保持同步）→ nop-task GraphTaskStep CompletableFuture 调度 → 并行分支并发 + 依赖序 → 最终 `TeamTaskFlowResult`，完整 async 路径跑通（成功 + honest failure + spawn-on-demand 正确性 三场景）
- [x] **并行并发 Anti-Hollow 断言**：端到端测试断言 B、C 真正并发（可观测证据，非仅状态）+ D 依赖序严格（执行序快照）
- [x] **接线验证**（#23）：端到端测试断言 async member step 运行时确实执行（task 状态机 CLAIMED→COMPLETED）+ LLM 工具确实调用 executeAsync
- [x] **sync 语义等价**：`execute` 与 `executeAsync().join()` 在同 DAG 下结果一致
- [x] **无静默跳过**（#24）：async honest failure 经 future 异常 / `success=false` 诚实上报；NoOp/幂等为显式语义
- [x] `nop-ai-agent-async-team-task-orchestration.md` 存在，含核心裁定 + 拒绝替代方案，无类签名/代码
- [x] roadmap §4 已标注 async 半部落地 + 新增 `L4-cross-process-daemon-coordination` successor 行
- [x] `nop-ai-agent-actor-runtime-vision.md` 已更新（async 已落地 + cross-process/daemon-async successor 标注）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `TeamTaskFlowOrchestrator.executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>` 非阻塞入口落地为真实（非空壳）代码——调用线程不被整个 DAG 阻塞
- [x] `MemberAgentTaskStep` async 化落地（async TaskStepReturn，非 join 阻塞），诚实失败语义与同步路径逐条对齐；`SpawnMemberAgentTaskStep` 保持同步不变更（裁定 3a，spawn-step async 切出 successor）
- [x] `execute(teamId)` sync 入口语义等价保留（既有 plan 233/238 测试零回归）
- [x] 并行分支真正并发验证落地（菱形 DAG bound-member 节点 B、C 并发证据 + D 依赖序严格）
- [x] `team-execute-flow` 接线消费真实 `executeAsync`（消除伪 async hollow）
- [x] 端到端：executeAsync → async member step → 并行分支 + 依赖序 → 最终结果（成功 + honest failure + spawn-on-demand 正确性）完整路径跑通
- [x] 既有测试零回归（sync 入口 + 既有 LLM 工具语义）
- [x] 必要 focused verification 已完成（executeAsync 入口 / 并行分支 / honest failure 各路径 / sync 零回归 / LLM 工具接线 / E2E 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（cross-process daemon 协调 / daemon async 派发 / spawn-step async / nop-task 核心变更 / 多成员路由 / spawn 池化 / decorator / 动态改图 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（新 design doc + vision + roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）async member step 运行时确实执行（task 状态机推进，非仅类型存在），（b）并行分支真正并发（可观测证据），（c）LLM 工具确实调用 executeAsync（非 execute+completedFuture 包装），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### cross-process daemon 协调（分布式锁 / 多实例扫描协调 / 共享调度状态）

- Classification: `out-of-scope improvement`（切出为独立 successor plan `L4-cross-process-daemon-coordination`）
- Why Not Blocking Closure: `claimTask` DB 级 CAS（affected-row-count==1）已提供多实例 double-dispatch 正确性地板；分布式锁是降冗余扫描的优化层，非 async 编排（本计划结果面）的正确性前置。async 与 cross-process 是不同结果面（进程内执行模型 vs 多实例部署拓扑协调）。
- Successor Required: yes
- Successor Path: `ai-dev/plans/{NNN}-nop-ai-agent-cross-process-daemon-coordination.md`（待创建）

### TeamTaskSchedulerDaemon per-cycle async 派发

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: daemon（plan 236）是"无人值守周期调度器"，其 async 化（让一个慢任务不阻塞整个扫描周期）是独立结果面；本计划只 async 化 orchestrator（程序化一次性 DAG 执行器）。daemon 同步派发在单实例下行为正确。
- Successor Required: yes
- Successor Path: 随 cross-process successor 一同裁定（或独立 successor）

### SpawnMemberAgentTaskStep async 化（spawn-on-demand 节点并发）

- Classification: `out-of-scope improvement`（切出为独立 successor `L4-spawn-step-async`）
- Why Not Blocking Closure: `IMemberSpawner.spawnMember()` 是同步契约（与 `MemberAgentTaskStep` 消费的 `IAgentEngine.execute()` 既有 `CompletableFuture` 不同）；async 化需改 spawner 契约（破坏 daemon 跨模块回归）/ supplyAsync 卸载（tenant-context 跨线程传播）/ 绕过 spawner（hollow 抽象）三选一，均为独立结果面。bound-member 路径（团队编排主路径）由本计划 async 化覆盖；spawn-on-demand 是 opt-in 特性，其节点在 `executeAsync` 图中保持同步（正确性不变，仅并发度受限）。本计划结果面（进程内 bound-member async 编排）的成立不依赖 spawn-step async。
- Successor Required: yes
- Successor Path: `ai-dev/plans/{NNN}-nop-ai-agent-spawn-step-async.md`（待创建；随 cross-process / daemon-async successor 一并裁定机制——契约变更 vs supplyAsync+tenant-context 传播）

## Non-Blocking Follow-ups

- **cross-process daemon 协调**（分布式锁 / 多实例扫描协调 / 共享调度状态）：独立 successor `L4-cross-process-daemon-coordination`。Classification: successor plan required。
- **`TeamTaskSchedulerDaemon` per-cycle async 派发**：Classification: successor plan required。
- **`SpawnMemberAgentTaskStep` async 化**（spawn-on-demand 节点并发；`IMemberSpawner` 同步契约，需机制裁定）：Classification: successor plan required。
- **多成员 per-task 路由**（plan 239 carry-over）：Classification: successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over）：Classification: optimization candidate。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over）：Classification: successor plan required。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over）：Classification: successor plan required。
- **claimer-liveness 交叉检测 / per-task 超时配置 / 动态分级动作策略 / `team-task-reclaim` LLM 工具 / `RecoveryScanResult` 构造器重构**（plan 240 carry-over，均已落地 store 基础或为优化项）：Classification: 各自 successor / optimization candidate。
- **TeamManager + ResourceGuard + Fencing Token**（vision §10 Phase 3/5）：Classification: successor plan required。

## Closure

Status Note: Plan 241 关闭——async 半部交付完成。`TeamTaskFlowOrchestrator.executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>` 非阻塞入口落地（消费 nop-task 既有 async 模型，纠正历史"async model 未落地"前提）；`MemberAgentTaskStep` async 化（claim 同步、complete/失败在 async 回调，诚实失败语义与同步路径逐条对齐）；`execute(teamId)` 保留为 sync 便捷入口（`= executeAsync().join()` 语义等价零回归）；菱形 DAG bound-member 节点真正并发（peakConcurrent≥2 + 区间重叠证据）；`team-execute-flow` LLM 工具消费真实 executeAsync（消除 hollow 包装）。`SpawnMemberAgentTaskStep` 保持同步（裁定 3a——IMemberSpawner 同步契约，async 化切出 successor `L4-spawn-step-async`）；含 spawn 节点的图 executeAsync 仍正确完成，仅并发度受限（已知限制诚实记录）。cross-process daemon 协调切出 successor `L4-cross-process-daemon-coordination`（不同结果面：多实例部署拓扑协调 vs 进程内执行模型；claimTask DB CAS 已提供正确性地板）。26 focused/E2E 测试全绿（含真实并发证据 + sync 零回归 + LLM 工具接线 + spawn-on-demand 正确性 + B 失败 D skipped honest failure）。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task_id `ses_128928d8dffeNrIBqAwsI1qICt`，fresh session 非 implementation session）
- Audit Session: 2026-06-18T21:11+08:00
- Evidence:
  - **1. Code existence & semantics (a-e)**: PASS — `TeamTaskFlowOrchestrator.executeAsync(String)` at `TeamTaskFlowOrchestrator.java:285` returns `CompletableFuture<TeamTaskFlowResult>`，structural failures sync throw + node failures → success=false future via `.exceptionally(...)` at lines 325-332；`execute(String)` at line 233-241 是 `executeAsync(teamId).join()` 纯 sync wrapper；`MemberAgentTaskStep.execute` returns `TaskStepReturn.ASYNC_RETURN(steppedFuture)` at line 214（非 `.join()`），失败路径 `steppedFuture.completeExceptionally(NopAiAgentException)` at lines 170/182/195；`SpawnMemberAgentTaskStep` 未变更（用 `RETURN_RESULT` 同步 throw NopAiAgentException）；`TeamExecuteFlowExecutor.doExecuteAsync` line 242 调 `executeAsync` + line 250 `flowFuture.thenApply(this::mapResult)` 真实链式消费（非 completedFuture(execute) 包装）。
  - **2. Test existence & green**: PASS — 6 新测试文件存在（glob 确认），26 tests run, 0 failures, 0 errors, 0 skipped（`./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=...`）。并行分支测试断言 `peakConcurrent >= 2` + 区间重叠 + D 依赖序严格（不只最终 COMPLETED 状态）。
  - **3. Doc artifacts (a-c)**: PASS — `ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md` 新建设计文档（核心裁定 + 拒绝替代方案）；`nop-ai-agent-actor-runtime-vision.md:416` 含「异步团队任务编排已落地（plan 241 / L4-async-cross-process-orchestration，async 半部）」+ successor 标注；`nop-ai-agent-roadmap.md` row `L4-async-cross-process-orchestration` 状态 ✅（line 264）+ 新 successor 行 `L4-cross-process-daemon-coordination` ❌（line 265）+ `L4-spawn-step-async` ❌（line 266）。
  - **4. Anti-Hollow verification (a-b)**: PASS — TestAsyncMemberStepParallelBranches 用 ConcurrencyRecordingEngine 跟踪 `peakConcurrent.accumulateAndGet(nowActive, Math::max)`，assertion at line 179 `assertTrue(engine.peakConcurrent.get() >= 2, ...)` + 区间重叠公式断言 `[bEnter,bExit] ∩ [cEnter,cExit] ≠ ∅` lines 184-191；TeamExecuteFlowExecutor 真实链接 orchestrator.executeAsync future（非 completedFuture 包装）。
  - **5. Checklist tooling**: PASS — `check-plan-checklist.mjs --strict` exit 0 ("All plans passed checklist verification")；`scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit 0 (Critical: 0, High: 0, Medium: 0, Low: 0)。
  - **6. Deferred items honesty**: PASS — 三 deferred 项均 `out-of-scope improvement` + non-blocking 理由 + successor path：cross-process daemon 协调 / TeamTaskSchedulerDaemon per-cycle async 派发 / SpawnMemberAgentTaskStep async 化。
  - **Overall verdict**: PASS — 每条 claimed code change 在 live code 中存在且语义正确；26-test 套件全绿含真实并发证据；doc artifacts 与 live code 一致；roadmap 正确标注 ✅ + 切出 ❌ successor；两 checklist tools 均退出 0；三 deferred 项分类正确。无 hollow patterns / silent no-ops / scope regression。

Follow-up:

- **cross-process daemon 协调**（分布式锁 / 多实例扫描协调 / 共享调度状态）→ successor `L4-cross-process-daemon-coordination`（roadmap §4 已新增 ❌ 行）。
- **`TeamTaskSchedulerDaemon` per-cycle async 派发** → 独立 successor（roadmap §4 已标注）。
- **`SpawnMemberAgentTaskStep` async 化** → successor `L4-spawn-step-async`（roadmap §4 已新增 ❌ 行）。
- 多成员 per-task 路由 / spawn session 复用池化 / nop-task decorator 接入 / 运行时动态增删图节点——均为各 plan 239/236 carry-over，独立 successor。

## Follow-up handled by 242-nop-ai-agent-cross-process-daemon-coordination.md

cross-process daemon 协调（分布式锁 / 多实例扫描协调 / 共享调度状态）successor 已由 `ai-dev/plans/242-nop-ai-agent-cross-process-daemon-coordination.md` 接管（team 级 scan lease + SchedulerDaemon 接线）。本节为历史计划 → successor 可追溯链接，不改动上方既有 closure 记录。

## Follow-up handled by 243-nop-ai-agent-spawn-step-async.md

`SpawnMemberAgentTaskStep` async 化（spawn-on-demand 节点并发，设计裁定 3a / Deferred But Adjudicated 切出的 successor `L4-spawn-step-async`）已由 `ai-dev/plans/243-nop-ai-agent-spawn-step-async.md` 接管（机制 b supplyAsync 包装 + tenant-context 跨线程传播，`IMemberSpawner` 同步契约保持不变）。本节为历史计划 → successor 可追溯链接，不改动上方既有 closure 记录。
