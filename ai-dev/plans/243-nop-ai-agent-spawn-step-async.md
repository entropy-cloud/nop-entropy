# 243 nop-ai-agent SpawnMemberAgentTaskStep Async 化（spawn-on-demand 节点并发：supplyAsync 包装 + tenant-context 跨线程传播）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-spawn-step-async

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/241-nop-ai-agent-async-team-task-orchestration.md`（设计裁定 3a / Deferred But Adjudicated `SpawnMemberAgentTaskStep async 化` → successor `L4-spawn-step-async`；Non-Goals 与 Non-Blocking Follow-ups 同步切出）；roadmap §4 Layer 4 row `L4-spawn-step-async` ❌（`nop-ai-agent-roadmap.md:266`，§4 中唯一 ❌）
> Related: `238`（交付同步 `SpawnMemberAgentTaskStep`——本计划 async 化其执行模型）、`237`（交付 `IMemberSpawner` 同步契约 + `DefaultMemberSpawner`——本计划保持其契约不变，仅改变消费方）、`241`（交付 `MemberAgentTaskStep` async 化 + `executeAsync` 入口——本计划是其 spawn 半部 successor，复用同一 async TaskStepReturn 模型）、`232`（交付 `ThreadLocalTenantResolver` 跨 supplyAsync 线程边界 tenant-context 传播设施——本计划复用此范式）

## Purpose

把 nop-ai-agent 团队任务 DAG 编排中**剩余的唯一同步阻塞节点**——`SpawnMemberAgentTaskStep`（无已绑定成员的 spawn-on-demand 图节点）——从"节点运行期在 nop-task DAG 调度线程上**同步阻塞**执行（`claimTask` → `memberSpawner.spawnMember()`（其内部 `engine.execute().join()` 同步阻塞）→ 三态解释 → `completeTask`，全程占用调度线程）"扩展为"**异步非阻塞**：claim 在节点触发期同步完成，`spawnMember` + 三态解释 + `completeTask` 经 `CompletableFuture.supplyAsync(...)` 卸载到 worker 线程，返回 async `TaskStepReturn`（消费 nop-task 既有 async 模型，与 plan 241 的 `MemberAgentTaskStep` 同一 `TaskStepReturn.ASYNC_RETURN` 契约）"。

**为何需要独立 successor（plan 241 裁定 3a 的明确遗留）**：`MemberAgentTaskStep` async 化之所以简单，是因为它消费的 `IAgentEngine.execute()` **已经返回 `CompletableFuture`**，直接包装即可。`SpawnMemberAgentTaskStep` 消费的 `IMemberSpawner.spawnMember()` 是**同步契约**（返回 `SpawnMemberResult`，非 future），无可直接包装的既有 future。plan 241 明确把以下三选一裁定延期到本 successor：
- (a) 改 `IMemberSpawner` 契约为返回 future → 破坏 daemon（plans 236/237 同步调用 `spawnMember`）跨模块回归；
- (b) `CompletableFuture.supplyAsync` 卸载到 worker 池 → 引入 tenant-context 跨线程传播问题（`DbTeamTaskStore.claimTask/completeTask` 读 `ThreadLocalTenantResolver.current()`）；
- (c) 绕过 spawner 直调 engine → 破坏 spawner 扩展点抽象（hollow）。

本计划裁定采用 **(b)**，并复用 plan 232 已落地的 `ThreadLocalTenantResolver` 跨线程传播范式解决 (b) 的 tenant-context 子问题（见设计裁定 1）。裁定理由：(b) 是**纯局部、增量**变更（只改 step 消费方，不改 `IMemberSpawner` 契约、不破坏 daemon、不破坏 spawner 扩展点），而 (a) 跨模块回归、(c) 破坏扩展点抽象。

闭合后，roadmap §4 Layer 4 的全部工作项将 ✅，async 编排故事对 bound-member 与 spawn-on-demand 两类图节点**都**提供真正的并行并发（含 spawn 节点的菱形 DAG `A→{B,C}→D` 中 B、C 真正并发，而非被 spawn 节点串行化）。

## Current Baseline

基于 live repo 核对（来源：plan 237 / 238 / 241 / 232 closure audit evidence；本段描述现状，引用位置为本计划审计可复核的 live code path）：

- **`SpawnMemberAgentTaskStep` 全程同步 ✅**（plan 238）：`SpawnMemberAgentTaskStep.java` 的 `execute(ITaskStepRuntime)`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/SpawnMemberAgentTaskStep.java:112`）在 nop-task DAG 调度线程上顺序执行 `recorder.markStart` → `taskStore.claimTask`（CREATED→CLAIMED，同步）→ `memberSpawner.spawnMember(spawnReq)`（同步）→ 三态 `switch` 解释（NO_SPAWN/SPAWN_FAILED/dispatched-non-completed → 抛 `NopAiAgentException` 留 CLAIMED；DISPATCHED+completed → 继续）→ `taskStore.completeTask`（CLAIMED→COMPLETED，同步）→ 返回 `TaskStepReturn.RETURN_RESULT(reply)`（**同步** TaskStepReturn，非 async）。无 `CompletableFuture` / `supplyAsync` / `ASYNC_RETURN` 任何代码（grep `supplyAsync|ASYNC_RETURN|CompletableFuture` 在该文件 0 命中）。诚实失败语义完整（claim CAS 失败 / NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner 抛异常 / null 防御 各自显式 throw，留 CLAIMED 不 abandon，No Silent No-Op #24）。
- **`IMemberSpawner.spawnMember()` 同步契约 ✅**（plan 237）：`IMemberSpawner.java:96` 签名 `SpawnMemberResult spawnMember(SpawnMemberRequest request)`（同步，返回值非 future）。Javadoc 自述 "execute it synchronously"。两个消费者：`TeamTaskSchedulerDaemon`（plan 236/237 daemon dispatch 路径，同步调用）+ `SpawnMemberAgentTaskStep`（plan 238，本计划要 async 化的）。
- **`DefaultMemberSpawner` 内部 `.join()` ✅**（plan 237）：`DefaultMemberSpawner.java:152-153` `agentEngine.execute(execRequest).join()` 同步阻塞成员 agent 执行，返回 `SpawnMemberResult`（DISPATCHED/NO_SPAWN/SPAWN_FAILED 三态，honest 语义）。
- **`MemberAgentTaskStep` 已 async ✅**（plan 241，参考实现）：`MemberAgentTaskStep.java:214` 返回 `TaskStepReturn.ASYNC_RETURN(steppedFuture)`，claim（CREATED→CLAIMED）同步在节点触发期完成（line 125-136），complete/失败在 `engineFuture.whenComplete` async 回调中处理（line 161-212）。本计划对 spawn step 复用同一 async TaskStepReturn 模型，但因 `spawnMember` 非 future，需用 `supplyAsync` 而非 `whenComplete` 包装。
- **`TeamTaskFlowOrchestrator.executeAsync` 已正确处理含 spawn 节点的图（正确性不变，并发度受限）✅**（plan 241 裁定 3a）：`TeamTaskFlowOrchestrator.java:285` `executeAsync(String)` 消费 nop-task 既有 async 模型。含 spawn 节点的图仍正确完成（`built.task.execute(taskRt)` → `asyncOutputs()` 链），仅 spawn 节点同步阻塞调度线程使该节点串行。Javadoc `TeamTaskFlowOrchestrator.java:266-274` 明确记录此已知限制 + successor `L4-spawn-step-async`。
- **`ThreadLocalTenantResolver` 跨线程 tenant-context 传播设施已落地 ✅**（plan 232）：`ThreadLocalTenantResolver.java` 提供 `set(String)` / `clear()` / `current()`。`DefaultAgentEngine.doExecute` 已建立标准范式（`DefaultAgentEngine.java:1816-1819` 同步阶段捕获 `tenantId`，`line 1889-1896` supplyAsync worker lambda 内 `ThreadLocalTenantResolver.set(tenantId)` + finally `clear()`）。本计划对 spawn step 复用此范式解决 (b) 的 tenant-context 子问题。
- **`execute(teamId)` sync 入口 = `executeAsync(teamId).join()` ✅**（plan 241）：`TeamTaskFlowOrchestrator.java:233-241`。spawn step async 化后，sync 入口语义不变（join 等待 async future）。
- **含 spawn 节点的菱形 DAG 当前并发度受限（核心缺口）**：`executeAsync` 图中若 spawn 节点位于独立分支（如 `A→{B,C}→D` 且 B、C 均 spawn 节点），B、C 因各自同步阻塞调度线程而**串行**执行（B 完全结束后 C 才开始），非真正并发。本计划闭合此缺口。

## Goals

- **`SpawnMemberAgentTaskStep` async 化**：`execute(ITaskStepRuntime)` 返回 async `TaskStepReturn`（`ASYNC_RETURN`，与 `MemberAgentTaskStep` 同一 async 契约）。claim（CREATED→CLAIMED）在节点触发期同步完成（与 `MemberAgentTaskStep` 一致，保持 DAG 依赖序 + claim CAS 失败的同步 fast-fail）；`spawnMember` + 三态解释 + `completeTask` 经 `CompletableFuture.supplyAsync(...)` 卸载到 worker 线程，结果包装为 async `TaskStepReturn`。spawn 节点不再阻塞 nop-task DAG 调度线程。
- **含 spawn 节点的图真正并发验证（Anti-Hollow #22）**：菱形 DAG（`A→{B,C}→D`，B、C 均无 bound member → 均为 spawn 节点）经 `executeAsync` 执行时，B 与 C（互无依赖）**真正并发**执行（非串行），D 严格在 B、C 都完成后才触发。验证方式：可观测的并发证据（如 B、C `spawnMember` 调用时间区间重叠 / 并发计数 ≥2 断言），非仅最终 COMPLETED 状态。
- **tenant-context 跨 worker 边界正确传播**：spawn step 的 supplyAsync worker lambda 在调用 `spawnMember`（其内部 engine.execute 已自带 tenant 解析，从 request.principal，与线程无关）与 `completeTask`（读 `ThreadLocalTenantResolver.current()`）前，re-apply 节点触发期捕获的 tenant（复用 plan 232 `ThreadLocalTenantResolver.set/clear` 范式），并在 finally 清除（不泄漏 tenant context 到池化 worker 线程）。
  - **已知风险点（执行期必须聚焦验证）**：thread-based 捕获（在 `step.execute()` 时读 `ThreadLocalTenantResolver.current()`）对 **enter** spawn 节点可靠（`execute()` 在调用 `executeAsync` 的线程上运行，tenant 已由调用方设置）；但对 **非 enter** spawn 节点（菱形 `A→{B,C}→D` 中的 B、C），其 `execute()` 运行在完成前驱 stepFuture 的线程上——该线程是否仍持有调用方 tenant 取决于 nop-task `GraphTaskStep` 的 CompletableFuture 组合语义（前驱 `steppedFuture.complete(...)` 触发后继 execute 是同步发生在前驱 worker finally clear-tenant 之前，还是之后）。此问题必须在 Phase 2 经**非 enter 节点 × 多租户**专项测试裁定。
  - **裁定后备机制（若 thread-based 捕获对非 enter 节点失效）**：改用 request/explicit-propagation——orchestrator 在 `executeAsync` 入口捕获一次调用方 tenant（`ThreadLocalTenantResolver.current()`），显式注入每个 step（构造参数），step 在 supplyAsync worker 内 `ThreadLocalTenantResolver.set(注入的tenant)`。该机制不依赖线程捕获点，对任意 DAG 拓扑鲁棒。具体选用 thread-based 还是 explicit-propagation 由 Phase 2 测试裁定，二者均在本计划 scope 内（不切 successor）。
  - 多租户场景下含 spawn 节点的图经 `executeAsync` 执行时，**enter 与 非 enter** spawn 节点的 `completeTask` 均须按正确 tenant 隔离读写（跨租户不可见）——此为硬 Goal，由 Phase 2 两种拓扑（单 enter 节点 + 菱形非 enter 节点）的多租户测试共同验证。
- **executor 与 engine 池隔离（防 commonPool 嵌套阻塞）**：spawn step 的 supplyAsync **必须运行在与 `DefaultAgentEngine` 的 supplyAsync 不同的线程池**上。理由：spawn step worker（线程 W1）经 `spawnMember` 内部 `engine.execute(req).join()` **阻塞等待** engine future，而 engine 自身经 one-arg `CompletableFuture.supplyAsync`（`DefaultAgentEngine.java:1889/2175/2354`）在 **commonPool** 上运行 agent 工作。若 spawn step 亦默认用 commonPool，则 spawn worker 阻塞 commonPool 线程 W1、等待的 engine 工作又需另一 commonPool 线程 W2；并发 spawn 节点数 ≥ commonPool parallelism 时，全部 commonPool 线程被 `.join()` park、无线程推进 engine future → 停滞/死锁。故 spawn step 必须使用独立注入的 `Executor`（非 commonPool），其池大小即 spawn 并发上限。
- **诚实失败语义在 async 路径完整保留（No Silent No-Op #24）**：claim 失败（同步 fast-fail）/ NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner 抛异常 / null 防御 各路径在 async 化后**逐条对齐**同步路径语义——经 async future 异常或 `success=false` 诚实上报，任务保留 CLAIMED（不 abandon，与 `MemberAgentTaskStep` 一致），不静默成功、不吞异常。已 COMPLETED 幂等为显式成功（非静默跳过）。
- **sync 零回归**：`execute(teamId)` sync 入口在含 spawn 节点的图上行为不变（`= executeAsync(teamId).join()`）；既有 plan 238 测试（`TestTeamTaskFlowOrchestratorAutoSpawn` / `TestTeamTaskFlowOrchestratorAutoSpawnEndToEnd`）全绿。
- **daemon 路径零回归**：`IMemberSpawner.spawnMember()` 契约**不变**（保持同步）。`TeamTaskSchedulerDaemon`（plans 236/237）与 `DefaultMemberSpawner` / `NoOpMemberSpawner` 不变更。既有 daemon spawn 测试（`TestTeamTaskSchedulerDaemonMemberSpawner` / `TestTeamTaskSchedulerDaemonMemberSpawnEndToEnd`）全绿。
- **设计文档 + roadmap 同步**：更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`（spawn-step async 裁定：机制 (b) supplyAsync + tenant-context 传播 / 拒绝 (a)(c) / spawn-node 并发闭合）+ `nop-ai-agent-actor-runtime-vision.md`（async orchestration spawn 半部落地）+ roadmap §4 row `L4-spawn-step-async` ❌→✅（闭合 §4 全 ✅）。

## Non-Goals

- **改 `IMemberSpawner` 契约为返回 future（机制 a）**：破坏 daemon（plans 236/237 同步调用 `spawnMember`）跨模块回归。Classification: rejected alternative（见设计裁定 2）。
- **绕过 spawner 直调 engine（机制 c）**：破坏 `IMemberSpawner` 扩展点抽象（`NoOpMemberSpawner` / `DefaultMemberSpawner` / 自定义 spawner 的 swap-ability + 三态 honest 解释 hollow 化）。Classification: rejected alternative（见设计裁定 2）。
- **`TeamTaskSchedulerDaemon` per-cycle async 派发**：daemon 的 async 化是独立结果面（让一个慢任务不阻塞整个扫描周期），本计划只 async 化 orchestrator step。Classification: successor plan required（plan 241 已切出）。
- **cross-process daemon 协调（分布式锁 / 多实例扫描协调）**：已由 plan 242 / `L4-cross-process-daemon-coordination` 接管。Classification: 已落地 successor。
- **多成员 per-task 路由**（plan 239 carry-over `L4-multi-member-per-task-routing`）。Classification: successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over `L4-spawn-session-pooling`）。Classification: optimization candidate。
- **nop-task 核心 async 模型变更**：nop-task 已提供完整 async 模型（plan 241 已核实），本计划消费而非修改。Classification: out-of-scope（无需变更）。
- **修改 `IMemberSpawner` / `SpawnMemberResult` / `SpawnMemberRequest` / `IAgentEngine` / `ITeamTaskStore` 契约**：消费原样契约，仅变更 `SpawnMemberAgentTaskStep` 内部执行模型 + 必要的 executor 注入。
- **其他 LLM 工具 async 化**：`team-execute-flow` 已由 plan 241 接线消费 `executeAsync`；spawn step async 化后该工具自动获益（其消费的 `executeAsync` 图若含 spawn 节点现在也并发），无需额外变更。

## Scope

### In Scope

- `io.nop.ai.agent.team.flow.SpawnMemberAgentTaskStep` — async 化 `execute`：claim 同步 + `spawnMember`+三态解释+`completeTask` 经 supplyAsync 卸载，返回 async `TaskStepReturn`，tenant-context 跨 worker 传播，诚实失败语义逐条对齐同步路径
- executor 接线（step 消费一个**独立于 commonPool** 的 `Executor` 用于 supplyAsync，shipped 默认为独立有界池；具体注入点为 step 构造或 orchestrator 透传，属设计裁定 3 范畴。**禁止**默认回退 commonPool——见设计裁定 3 的 commonPool 嵌套阻塞死锁约束）
- 测试文件（新）：
  - spawn-node 并发验证（菱形 `A→{B,C}→D`，B、C 均 spawn 节点，并发证据 + D 依赖序严格）
  - async honest failure（NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner 抛异常 / null 防御 → 失败 future + 任务保留 CLAIMED）
  - tenant-context 跨 worker 传播（spawn worker 线程观测到节点触发期捕获的 tenant；多租户隔离下 spawn 节点 completeTask 按正确 tenant 读写）
  - sync 零回归（spawn 路径既有行为全绿）
  - 端到端（含 spawn 节点的 `executeAsync` 图完整路径 + spawn + bound 混合图）
- 设计文档：更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`（spawn-step async 裁定）+ `nop-ai-agent-actor-runtime-vision.md` + `nop-ai-agent-roadmap.md` §4 row `L4-spawn-step-async` ❌→✅

### Out Of Scope

- 见 Non-Goals（机制 a/c / daemon async 派发 / cross-process / 多成员路由 / spawn 池化 / nop-task 核心 / 契约变更 均为显式 rejected / successor / out-of-scope）

### 设计裁定（Pre-Adjudicated）

1. **机制 (b)：`CompletableFuture.supplyAsync` 包装 `spawnMember` + tenant-context 跨线程传播**。`SpawnMemberAgentTaskStep.execute` 在节点触发期同步完成 claim（CREATED→CLAIMED，与 `MemberAgentTaskStep` 一致，保持 DAG 依赖序 + claim CAS 失败同步 fast-fail），随后把 `spawnMember` 调用 + 三态解释 + `completeTask` 包装进 `CompletableFuture.supplyAsync(..., executor)`，返回 `TaskStepReturn.ASYNC_RETURN(steppedFuture)`。理由：(1) `IMemberSpawner.spawnMember()` 是同步契约无可包装的 future，supplyAsync 是把它异步化的标准手段；(2) 释放 DAG 调度线程使独立分支的 spawn 节点真正并发；(3) 与 `MemberAgentTaskStep` 的 `ASYNC_RETURN` 契约一致，`GraphTaskStep` CompletableFuture 调度透明消费。

2. **tenant-context 跨 worker 边界传播 = 复用 plan 232 `ThreadLocalTenantResolver.set/clear/current` 范式**。step 在节点触发期（claim 前/后，仍在调度线程）捕获 `ThreadLocalTenantResolver.current()`，在 supplyAsync worker lambda 体首行 `ThreadLocalTenantResolver.set(capturedTenant)`，finally `ThreadLocalTenantResolver.clear()`（不泄漏到池化线程）。理由：(1) `DefaultMemberSpawner.spawnMember` 内部 `engine.execute` 已自带 tenant 解析（从 request.principal，spawn 请求 principal 为 null = 全可见，行为与同步路径一致），但 step 自身的 `completeTask` 读 `ThreadLocalTenantResolver.current()`，跨线程须 re-apply；(2) plan 232 已为此建立标准范式（`DefaultAgentEngine.doExecute` 同款），本计划复用而非新造；(3) 捕获点在 step execute（调度线程），与 claim 同一 tenant，保证 claim 与 complete 同租户。

3. **executor 来源：独立注入 Executor，**禁止**默认回退 commonPool（防 commonPool 嵌套阻塞死锁）**。step 必须消费一个**独立于 commonPool** 的 `Executor`（用于 supplyAsync 第二参数）。理由（核心正确性约束）：`DefaultAgentEngine` 的 supplyAsync 使用 **one-arg 重载**（`DefaultAgentEngine.java:1889/2175/2354`，即 `ForkJoinPool.commonPool()`，**engine 没有"自己的池"**），而 spawn step 的 supplyAsync worker 经 `DefaultMemberSpawner.spawnMember` 内部 `engine.execute(req).join()` **同步阻塞**等待 engine future。若 spawn step 亦用 commonPool，则 spawn worker（commonPool 线程 W1）park 在 `.join()` 上，等待的 engine 工作又需另一 commonPool 线程 W2 推进；并发 spawn 节点数 ≥ commonPool parallelism（默认 `availableProcessors()-1`）时，全部 commonPool 线程被 `.join()` 占满、engine future 无线程推进 → **停滞/死锁**。因此 spawn step 的 executor 必须与 engine 的 commonPool 隔离：其池大小 = spawn 并发上限（有界池，溢出时 CallerRuns 或拒绝策略由注入方裁定，属部署治理）。注入方式：step 构造器 / orchestrator 透传（wire-at-consumer，镜像模块既有 spawner/guard 接线惯例）；shipped 默认为一个**独立的有界线程池**（**非** commonPool，构造期创建 / 关闭随 orchestrator 生命周期），保证开箱即用且不与 engine 竞争 commonPool 线程。`CompletableFuture.join()` 不参与 `ForkJoinPool.managedBlock`，故不能用"commonPool + managedBlock 会自愈"作为默认回退的理由。

4. **拒绝机制 (a) 改 `IMemberSpawner` 契约为返回 future**：`TeamTaskSchedulerDaemon`（plans 236/237）同步调用 `spawnMember`，改契约会迫使 daemon 也 async 化或 `join`（跨模块回归 + scope 蔓延）。机制 (b) 把异步化局限在 orchestrator step 消费方，daemon 路径零变更。

5. **拒绝机制 (c) 绕过 spawner 直调 engine**：`IMemberSpawner` 抽象了 spawn 目标解析（prefer MEMBER role / fallback）+ honest 三态结果（DISPATCHED/NO_SPAWN/SPAWN_FAILED）+ NoOp/Default/自定义 swap-ability。绕过它会在 step 内重新实现这些，破坏扩展点（hollow 抽象 + No Silent No-Op #24 的 NO_SPAWN 显式语义丢失）。

6. **claim 保持同步（与 `MemberAgentTaskStep` 一致）**：claim（CREATED→CLAIMED）在节点触发期同步完成，不进 supplyAsync。理由：(1) nop-task DAG 调度器在 `blockedBy` 前驱完成后才触发节点，claim 在此点保持 DAG 依赖序；(2) claim CAS 失败的同步 fast-fail（throw）是 honest 路径（与 `MemberAgentTaskStep` line 133-136 一致）；(3) 已 COMPLETED 幂等为同步成功 return（非 async）。仅 spawn+解释+complete 进 async。

7. **async 路径诚实失败 = future 异常，任务保留 CLAIMED（不 abandon），与 `MemberAgentTaskStep` / 同步 spawn step 逐条对齐**。NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner 抛异常 / null 防御 → async future `completeExceptionally(NopAiAgentException)` + `recorder.markFailed`（在 worker lambda 内，re-apply tenant 后），任务保留 CLAIMED。已 COMPLETED 幂等 → 同步成功 return（显式）。理由：No Silent No-Op #24 在 async 路径完整保留；与同步路径及 `MemberAgentTaskStep` 失败模型一致。

## Execution Plan

### Phase 1 - SpawnMemberAgentTaskStep async 化核心（supplyAsync + tenant-context 传播 + 诚实失败对齐 + 设计裁定落档）

Status: completed
Targets: `io.nop.ai.agent.team.flow.SpawnMemberAgentTaskStep`、`io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator`（step 构造/executor 透传，如需）、`ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`

- Item Types: `Decision`（机制 b supplyAsync + tenant-context 传播 / claim 同步 / executor 注入+默认回退 / 拒绝 a/c / async 诚实失败对齐）、`Fix`（spawn step 同步阻塞调度线程 = plan 241 裁定 3a carry-over gap）、`Proof`

- [x] `SpawnMemberAgentTaskStep.execute` async 化：claim（CREATED→CLAIMED）同步完成（保持 DAG 依赖序 + claim CAS 失败同步 fast-fail throw + 已 COMPLETED 幂等同步成功 return）；捕获 `ThreadLocalTenantResolver.current()`；把 `spawnMember` + 三态解释（NO_SPAWN/SPAWN_FAILED/dispatched-non-completed/spawner-throws/null）+ `completeTask` 包装进 `CompletableFuture.supplyAsync(..., executor)`，worker lambda 首行 `ThreadLocalTenantResolver.set(capturedTenant)`、finally `clear()`；各失败路径在 worker 内 `recorder.markFailed` + future `completeExceptionally(NopAiAgentException)` + 任务保留 CLAIMED（不 abandon）；成功 → `completeTask` → `recorder.markComplete` → future 完成 `RETURN_RESULT`；返回 `TaskStepReturn.ASYNC_RETURN(steppedFuture)`
- [x] executor 接线：step 消费一个**独立于 commonPool** 的 `Executor`（构造器/setter 注入，wire-at-consumer）；shipped 默认为一个独立的有界线程池（**非** commonPool，随 orchestrator 生命周期创建/关闭）；**禁止**默认回退 commonPool（设计裁定 3：commonPool 嵌套阻塞死锁——engine 自身用 commonPool，spawn worker 经 `.join()` 阻塞等待 engine future，同池则并发 spawn 节点 ≥ parallelism 时停滞）。orchestrator 持有/创建该独立 executor 并透传给 step
- [x] `TeamTaskFlowOrchestrator` Javadoc 更新：移除/修订"含 spawn 节点的图并发度受限"已知限制描述（`TeamTaskFlowOrchestrator.java:266-274` 区域），改为 spawn 节点已 async 化、含 spawn 节点的图亦真正并发
- [x] 设计文档 `nop-ai-agent-async-team-task-orchestration.md` 增补 spawn-step async 裁定段（机制 b + tenant-context 跨线程传播 + 拒绝 a/c + claim 同步 + executor 注入 + async 诚实失败对齐 + spawn-node 并发闭合），遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SpawnMemberAgentTaskStep.execute` 返回 async `TaskStepReturn`（`ASYNC_RETURN`，非同步 `RETURN_RESULT`），claim 同步完成、spawn+解释+complete 在 supplyAsync worker 内执行
- [x] spawn 节点不再阻塞 DAG 调度线程（可观测：节点 execute 返回后调度线程未被 spawn 阻塞，steppedFuture 初始未完成）
- [x] **无静默跳过**（#24）：NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner 抛异常 / null 防御 各路径在 async 化后仍诚实失败（future 异常 + 任务保留 CLAIMED），非静默成功 / 非吞异常；已 COMPLETED 幂等为显式同步成功
- [x] tenant-context 跨 worker 边界正确（worker lambda 内 `ThreadLocalTenantResolver.set` captured + finally `clear`，与 plan 232 范式一致）
- [x] `IMemberSpawner` / `SpawnMemberResult` / `SpawnMemberRequest` / `DefaultMemberSpawner` / `NoOpMemberSpawner` 契约与实现**未变更**（grep 确认无 diff）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **No new test required for pure refactor 不适用**：本 Phase 改变 spawn step 执行模型（同步→async），focused 测试在 Phase 2（#25）；Phase 1 compile + 既有 spawn 测试零回归即可（既有同步 spawn 测试经 `executeAsync(...).join()` / `execute(...)` 路径仍应通过）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - async focused 测试（spawn-node 并发 + honest failure + tenant 传播 + sync 零回归）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/`（新测试）

- Item Types: `Proof`

- [x] 编写 spawn-node 并发测试（菱形 `A→{B,C}→D`，B、C 均无 bound member → 均 spawn 节点，functional spawner）：断言 B、C `spawnMember` 调用**真正并发**（可观测并发证据——执行时间区间重叠快照 / 并发计数 ≥2 断言，非仅最终 COMPLETED 状态）；断言 D 依赖序严格（D.start 严格晚于 B.complete 与 C.complete，经 `ExecutionRecorder` 执行序快照证明）；Anti-Hollow 断言并发真实发生
- [x] 编写 async honest failure 测试：NO_SPAWN（NoOp shipped 默认 / 无 memberSpec）→ 失败 future + `success=false` + 任务保留 CLAIMED；SPAWN_FAILED（spawner 返回）→ 失败 future + 保留 CLAIMED；DISPATCHED 但非 completed（failed/cancelled/paused）→ 失败 future + 保留 CLAIMED；`completeTask` CAS 失败（task 已被转换）→ 失败 future；spawner 抛异常（contract violation）→ 失败 future + 保留 CLAIMED；spawner 返回 null（防御）→ 失败 future；已 COMPLETED 幂等 → 成功（显式）
- [x] 编写 tenant-context 跨 worker 传播测试（**必须覆盖两种拓扑**）：(a) **单 enter spawn 节点**——调用方设置非空 tenant → spawn worker 线程内观测到同一 tenant（经 spy spawner / spy taskStore 捕获 worker 线程的 `ThreadLocalTenantResolver.current()` 断言 = 捕获值）；(b) **菱形非 enter spawn 节点 `A→{B,C}→D`（关键）**——调用方设置非空 tenant → B、C（非 enter 节点）的 supplyAsync worker 仍观测到调用方 tenant（**非 NULL**），`completeTask` 按正确 tenant 读写；若该断言失败，则 thread-based 捕获对非 enter 节点失效，执行期切换至设计裁定 2 的 explicit-propagation 后备机制（orchestrator 入口捕获 tenant 显式注入每个 step）；worker 结束后池化线程 tenant 已 clear（不泄漏，可经同一 executor 后续任务观测 null）；多租户隔离下 enter + 非 enter spawn 节点 completeTask 跨租户不可见
- [x] 编写 sync 零回归测试：`execute(teamId)` sync 入口在含 spawn 节点的线性 / 菱形 / 失败传播 各场景下行为与 plan 238 既有断言一致（全绿）；`executeAsync(...).join()` 与 `execute(...)` 在同 spawn 图下结果一致（语义等价）
- [x] 既有 plan 238 测试（`TestTeamTaskFlowOrchestratorAutoSpawn` / `TestTeamTaskFlowOrchestratorAutoSpawnEndToEnd`）+ daemon spawn 测试（`TestTeamTaskSchedulerDaemonMemberSpawner` / `TestTeamTaskSchedulerDaemonMemberSpawnEndToEnd`）零回归（`IMemberSpawner` 契约不变，daemon 路径未触及）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] spawn-node 并发测试全绿（B、C 真正并发可观测证据 + D 依赖序严格）
- [x] async honest failure 测试全绿（NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner throws / null 防御 / 已 COMPLETED 幂等 各路径 + 任务保留 CLAIMED 不 abandon）
- [x] tenant-context 传播测试全绿（worker 观测捕获 tenant + 池化线程不泄漏 + 多租户隔离 completeTask 正确）
- [x] **接线验证**（#23）：spawn step async 路径运行时确实执行（task 状态机 CLAIMED→COMPLETED 经 store 验证）；supplyAsync worker 确实在调度线程外执行（线程断言 / 并发证据）
- [x] **无静默跳过**（#24）：所有失败路径诚实 future 异常 / `success=false`；无空方法体 / continue / TODO / 吞异常
- [x] 新增功能各有对应 focused 测试覆盖（spawn-node 并发 / async honest failure 各路径 / tenant 传播 / sync 零回归 各有测试）
- [x] 既有 plan 238 spawn 测试 + daemon spawn 测试零回归
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + roadmap/vision 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/`（新 E2E）、`ai-dev/design/nop-ai-agent/nop-ai-agent-async-team-task-orchestration.md`、`nop-ai-agent-actor-runtime-vision.md`、`nop-ai-agent-roadmap.md` §4

- Item Types: `Proof`

- [x] 编写端到端测试（spawn 异步完整路径）：构造团队 + 多任务 DAG（含菱形 `A→{B,C}→D`，B、C 均无 bound member → spawn 节点，functional spawner）→ `executeAsync` → 调用线程不阻塞（future 初始未完成）→ 最终全 COMPLETED + `TeamTaskFlowResult{success=true}`；断言 B、C 并发 + D 依赖序
- [x] 编写端到端测试（async honest failure 传播）：菱形 spawn DAG 中 B 的 spawner 返回 SPAWN_FAILED（或 spawned agent 非 completed）→ `executeAsync` future 完成 `TeamTaskFlowResult{success=false}` + failed含B + skipped含D（nop-task `GraphTaskStep` 短路取消后继）；B 保留 CLAIMED（不 abandon）
- [x] 编写端到端测试（spawn + bound 混合图）：菱形 `A→{B,C}→D` 中 B 为 bound-member 节点、C 为 spawn 节点 → `executeAsync` → B、C 仍真正并发（bound 与 spawn 两类 async 节点共存于独立分支并发）→ 全 COMPLETED
- [x] 编写 sync 对比 e2e：`execute` sync 入口在同一 spawn DAG 下结果与 `executeAsync(...).join()` 一致（语义等价证明）
- [x] 更新 `nop-ai-agent-async-team-task-orchestration.md`：spawn-step async 裁定已落地（机制 b + tenant 传播 + spawn-node 并发闭合 §4 最后 ❌）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md`：async team-task orchestration 的 spawn 半部已落地（含 spawn 节点的图亦真正并发）；daemon async 派发 / 多成员路由 仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 row `L4-spawn-step-async`（line 266）❌→✅（闭合 §4 全 ✅）
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 `executeAsync(teamId)` 入口 → 图 build → async spawn step（claim 同步 + spawn+complete 在 supplyAsync worker + tenant 传播）→ nop-task `GraphTaskStep` CompletableFuture 调度 → spawn 节点并发 + 依赖序 → 最终 `TeamTaskFlowResult`，完整 async 路径跑通（成功 + honest failure + spawn+bound 混合 三场景）
- [x] **spawn-node 并发 Anti-Hollow 断言**：端到端测试断言 B、C 真正并发（可观测证据，非仅状态）+ D 依赖序严格（执行序快照）
- [x] **接线验证**（#23）：端到端测试断言 async spawn step 运行时确实执行（task 状态机 CLAIMED→COMPLETED）+ supplyAsync worker 确实在调度线程外执行
- [x] **sync 语义等价**：`execute` 与 `executeAsync().join()` 在同 spawn DAG 下结果一致
- [x] **无静默跳过**（#24）：async honest failure 经 future 异常 / `success=false` 诚实上报；NoOp/幂等为显式语义
- [x] `nop-ai-agent-async-team-task-orchestration.md` 已增补 spawn-step async 裁定（无类签名/代码）
- [x] roadmap §4 row `L4-spawn-step-async`（line 266）已 ❌→✅（§4 全 ✅）
- [x] `nop-ai-agent-actor-runtime-vision.md` 已更新（spawn 半部落地 + daemon-async/多成员 successor 标注）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `SpawnMemberAgentTaskStep` async 化落地为真实（非空壳）代码——返回 async `TaskStepReturn`，claim 同步、spawn+complete 在 supplyAsync worker，spawn 节点不再阻塞 DAG 调度线程
- [x] 含 spawn 节点的图真正并发验证落地（菱形 spawn DAG 的 B、C 并发证据 + D 依赖序严格）
- [x] tenant-context 跨 worker 边界正确传播（复用 plan 232 `ThreadLocalTenantResolver` 范式），多租户隔离下 spawn 节点 completeTask 按正确 tenant 读写
- [x] async 诚实失败语义与同步路径逐条对齐（NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner throws / null 各路径 + 任务保留 CLAIMED 不 abandon）
- [x] `IMemberSpawner` 契约 + `DefaultMemberSpawner` / `NoOpMemberSpawner` / daemon spawn 路径**零变更**（机制 a/c 明确拒绝）
- [x] sync 零回归（`execute` sync 入口 + 既有 plan 238 spawn 测试 + daemon spawn 测试 全绿）
- [x] 端到端：executeAsync → async spawn step → 并发 + 依赖序 → 最终结果（成功 + honest failure + spawn+bound 混合）完整路径跑通
- [x] 必要 focused verification 已完成（spawn-node 并发 / async honest failure 各路径 / tenant 传播 / sync 零回归 / E2E 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（daemon async 派发 / 多成员路由 / spawn 池化 / nop-task 核心 / 契约变更 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（async design doc + vision + roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）async spawn step 运行时确实执行（task 状态机推进 + supplyAsync worker 确实在调度线程外），（b）含 spawn 节点的图真正并发（可观测证据），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### TeamTaskSchedulerDaemon per-cycle async 派发

- Classification: `out-of-scope improvement`（切出为独立 successor）
- Why Not Blocking Closure: daemon（plan 236）是"无人值守周期调度器"，其 async 化（让一个慢任务不阻塞整个扫描周期）是独立结果面；本计划只 async 化 orchestrator step（程序化一次性 DAG 执行器）。daemon 同步派发在单实例下行为正确；`IMemberSpawner` 同步契约保持不变（daemon 零回归）。
- Successor Required: yes
- Successor Path: 随 daemon async 派发 successor 一并裁定（plan 241 已切出）

### 多成员 per-task 路由

- Classification: `out-of-scope improvement`（plan 239 carry-over）
- Why Not Blocking Closure: 当前 team-level 单成员策略（bound-member 单一 / spawn 单一 memberSpec），per-task 路由是独立工作面（工作负载在多个绑定/spawn 成员间分布）。本计划闭合 spawn 节点并发，不改变单成员策略。
- Successor Required: yes
- Successor Path: `L4-multi-member-per-task-routing`（待创建）

## Non-Blocking Follow-ups

- **`TeamTaskSchedulerDaemon` per-cycle async 派发**：Classification: successor plan required（plan 241 已切出）。
- **多成员 per-task 路由**（plan 239 carry-over）：Classification: successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over）：Classification: optimization candidate。
- **独立 executor 治理**（spawn step 的 supplyAsync executor 注入点已预留，未来可统一治理线程池资源）：Classification: optimization candidate。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over）：Classification: successor plan required。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over）：Classification: successor plan required。

## Closure

Status Note: `SpawnMemberAgentTaskStep` 已 async 化（claim 同步 + spawn+complete 经 `supplyAsync(..., spawnExecutor)` 卸载到独立于 commonPool 的 dedicated daemon 线程池 + 返回 async `TaskStepReturn`），含 spawn 节点的图经 `executeAsync` 真正并发（菱形 spawn DAG B、C 并发 + D 依赖序严格），tenant-context 经 explicit-propagation 跨 worker 边界（对所有 DAG 拓扑鲁棒），诚实失败语义逐条对齐同步路径（任务保留 CLAIMED 不 abandon），`IMemberSpawner`/`DefaultMemberSpawner`/`NoOpMemberSpawner`/daemon 路径零变更，sync 零回归。roadmap §4 row `L4-spawn-step-async` ❌→✅（闭合 §4 全 ✅）。三个 Phase 全 completed，独立 closure audit PASS。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure-audit subagent（task `ses_12832cac6ffe0HYM0Djdmk7SmP`，read-only，非 implementation session）
- Audit Session: `ses_12832cac6ffe0HYM0Djdmk7SmP`
- Evidence:
  - **Phase 1 Exit Criteria（8/8 PASS）**：`SpawnMemberAgentTaskStep.java:228` `ASYNC_RETURN(steppedFuture)`；`:194` 同步 claim；`:219-226` supplyAsync worker；`:220/224` `ThreadLocalTenantResolver.set`/`clear` in finally；`:174` `Objects.requireNonNull(spawnExecutor)`（无 commonPool 回退）；各失败路径 `:258/266/279/285/304/315` throw NopAiAgentException；`:199` 已 COMPLETED 幂等同步成功。
  - **Phase 2 Exit Criteria（8/8 PASS）**：`TestAsyncSpawnStepParallelBranches:169` `peakConcurrent≥2` + `:180` 区间重叠 + `:189-192` D 依赖序；`TestAsyncSpawnStepHonestFailure` 7 路径全 `assertEquals(CLAIMED,...)`；`TestAsyncSpawnStepTenantPropagation` 5 测试（`:259` assertNotNull + `:266/268` 非 enter B、C 观测 tenant + `:402-408` 跨租户隔离）。
  - **Phase 3 Exit Criteria（12/12 PASS）**：`TestAsyncSpawnStepEndToEnd` 4 E2E（`:206` future 初始 `!isDone()` + `:221/225` B/C 并发 + `:228-231` D 序 + `:262-270` 失败传播 skipped D + spawn+bound 混合图 + `:368-374` sync 等价）；roadmap line 266 `✅`；vision `:416` "spawn 半部已落地"；design §2.4/§3.4/§5 同步。
  - **Closure Gates（15/15 PASS）**：逐项见 audit 报告。
  - **Anti-Hollow 检查**：(a) `ASYNC_RETURN` 正常路径 `:228`（已 COMPLETED 幂等为同步成功 `:199` 非静默跳过）PASS；(b) supplyAsync worker 在 `ai-agent-spawn-worker-N` 线程（非调度线程），`peakConcurrent≥2` 可观测证据 PASS；(c) 非 enter 菱形 B、C worker 观测调用方 tenant（非 null）PASS——explicit-propagation 胜出（`TeamTaskFlowOrchestrator.java:512` 捕获 + `:554-557` 注入）；(d) 无空方法体/continue/吞异常/TODO/null-as-normal PASS。
  - **Contract-unchanged**：`IMemberSpawner.java:96` 同步签名（返回 SpawnMemberResult 非 future）；`DefaultMemberSpawner.java:152-153` 仍 `engine.execute().join()`；`NoOpMemberSpawner.java:51` 不变；三者均未出现在本 plan 的 modified 列表（机制 a/c 拒绝）。
  - **`node ai-dev/tools/check-plan-checklist.mjs 243-...md --strict`**：退出码 0（Closure Evidence 已写入、无未勾选项）。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high`**：退出码 0（0 high/critical 空壳发现）。
  - **`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`**：BUILD SUCCESS（2682 tests = 2660 baseline + 22 新增 [18 Phase 2 + 4 Phase 3]，0 failures，零回归含 plan 238 spawn 测试 + daemon spawn 测试）。
  - **`node ai-dev/tools/check-doc-links.mjs --strict`**：退出码 0（0 errors）。
  - **Deferred 项分类检查**：daemon per-cycle async 派发 / 多成员 per-task 路由 / spawn session 池化 / 独立 executor 治理 / nop-task decorator / 运行时动态改图 均显式 Non-Goals（optimization candidate / successor plan required），无 in-scope live defect 被降级。
  - **Overall verdict: CLOSURE_APPROVED**（独立 audit 报告确认）。

Follow-up:

- **`TeamTaskSchedulerDaemon` per-cycle async 派发**：successor plan required（plan 241 已切出）。
- **多成员 per-task 路由**（plan 239 carry-over `L4-multi-member-per-task-routing`）：successor plan required。
- **spawn session 复用 / 池化**（plan 239 carry-over）：optimization candidate。
- **独立 executor 治理**（spawn step supplyAsync executor 注入点已预留，未来可统一治理线程池资源）：optimization candidate。
- no remaining plan-owned work.

## Follow-up handled by 244-nop-ai-agent-multi-member-per-task-routing.md

> 追加于 2026-06-18（carry-over 链接，不改动上方已关闭的 closure 记录）。
> 本计划 Non-Goals line 57 + Non-Blocking Follow-ups line 223 / Closure Follow-up line 255 中的「多成员 per-task 路由（plan 239 carry-over `L4-multi-member-per-task-routing`）」一项，已由后续计划 `ai-dev/plans/244-nop-ai-agent-multi-member-per-task-routing.md` 接管：交付 per-task 成员路由扩展点（NoOp shipped 默认 = 单成员零回归）+ fan-out（一任务分发至 N 已绑定/spawned 成员）+ reduction（shipped all-must-succeed）+ 复用 plan 241 bound-member async / plan 243 spawn async + 独立 executor 隔离。本计划交付的 `SpawnMemberAgentTaskStep` async + dedicated spawn executor + explicit-propagation tenant 传播 被 plan 244 复用为 spawn 半部的 fan-out 基元（契约不变）。
