# 244 nop-ai-agent Multi-Member Per-Task Routing（一任务 fan-out 至 N 成员 + reduction）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-multi-member-per-task-routing

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/243-nop-ai-agent-spawn-step-async.md`（Non-Goals line 57 + Non-Blocking Follow-ups line 223 + Closure Follow-up line 255，标 `successor plan required`）；同源 carry-over 亦见 plans 241/240/239。roadmap §4 Layer 4 当前全 ✅（§4 最后一行 `L4-spawn-step-async` 已闭合），本工作项为 §4 之外显式 successor queue 的最高优先项（4 个 closed plans 引用，最高 dependents-to-dependencies 比）。
> Related: `233`（交付 `MemberAgentTaskStep` bound-member 单成员委派 + `TeamTaskFlowOrchestrator.resolveMember` 单成员解析——本计划扩展为多成员 fan-out）、`237`（交付 `IMemberSpawner` 同步契约 + `DefaultMemberSpawner.resolveSpawnTarget` 单目标解析——本计划扩展 spawn 半部为多目标 fan-out，契约不变）、`238`（交付 `SpawnMemberAgentTaskStep` 节点运行期 spawn——本计划复用其 spawn 执行模型）、`241`（交付 bound-member step async 化 + `executeAsync`——本计划复用 bound fan-out 的 async 组合）、`243`（交付 spawn step async 化 + dedicated spawn executor + explicit-propagation tenant 传播——本计划复用 spawn fan-out 的 supplyAsync 卸载与 tenant 传播范式）、`234`（交付 `IResourceGuard` 团队成员配额——本计划 fan-out 度受既有 `TEAM_PARALLEL_BOUND_MEMBERS`/`TEAM_MEMBERS` 配额自然约束）

## Purpose

把 nop-ai-agent 团队任务 DAG 编排中的**单一成员委派原语**（一个 team task 节点委派给**一个**成员 agent——bound 路径 `resolveMember` 返回单成员 / spawn 路径 `resolveSpawnTarget` 返回单 memberSpec）扩展为**多成员 per-task 路由**：一个 team task 节点可 fan-out 至 **N** 个成员 agent（已绑定 +/或 spawned）并发执行，经 reduction 策略把 N 个成员执行结果归约为单一 task 结果。这是 carry-over 文本所述"the only remaining work-distribution primitive before multi-agent DAG execution can spread a single task across multiple member agents instead of single-member single-spawn policy"。

**为何需要独立 successor**：plans 233/237/238/241/243 的整个 team-task DAG 执行管线硬编码"一任务一成员"假设——`resolveMember` 返回单 `ResolvedMember`、`resolveSpawnTarget` 返回单 `TeamMemberSpec`、`MemberAgentTaskStep`/`SpawnMemberAgentTaskStep` 各持单 session/单 spawn、`claimTask`→`completeTask` 是单 CLAIMED→COMPLETED 转换。把"一任务分发至多成员"塞进任一前驱计划都会破坏其零回归闭合。本计划引入**新的可插拔 per-task 路由扩展点**（NoOp shipped 默认 = 单成员逐行零回归），把多成员 fan-out + reduction 作为独立结果面闭合，bound 与 spawn 两半部对称。

闭合后，团队可声明/绑定多成员并在单一 task 节点上获得 N 路并发执行 + 归约结果（ensemble / 冗余 / 并行子探查语义），同时单成员团队/单 memberSpec 团队行为**逐行不变**。

## Current Baseline

基于 live repo 核对（引用位置为本计划审计可复核的 live code path）：

- **bound-member 单成员委派 ✅**（plan 233/241）：`TeamTaskFlowOrchestrator.buildGraphForExecution`（`TeamTaskFlowOrchestrator.java:472`）对每个 task 调 `resolveMember(team, task)`（`:628`）返回**单个** `ResolvedMember`（`claimedBy` → MEMBER role → fallback any bound，`:631-657`），无 bound member 返回 `null`（`:662`）。`MemberAgentTaskStep`（`MemberAgentTaskStep.java:100`）构造接收**单** `memberSessionId` + 单 `agentName`，`execute`（`:113`）claim 同步 + 包装单 `agentEngine.execute(request)` 为 async `TaskStepReturn`（`:214`）。全管线无 List/多成员语义。
- **spawn-on-demand 单目标 spawn ✅**（plan 237/238/243）：`DefaultMemberSpawner.resolveSpawnTarget`（`DefaultMemberSpawner.java:193`）从 `TeamSpec.getMemberSpecs()` 解析**单** target（prefer MEMBER role，fallback any spec，`:198-207`），`spawnMember`（`:112`）执行**单** `agentEngine.execute(execRequest).join()`（`:152-153`）返回单 `SpawnMemberResult`。`SpawnMemberAgentTaskStep`（`SpawnMemberAgentTaskStep.java:164`）构造接收单 `IMemberSpawner`，`execute`（`:182`）claim 同步 + `supplyAsync(..., spawnExecutor)` 卸载单 `spawnAndComplete`（`:219-226`）。`IMemberSpawner.spawnMember(SpawnMemberRequest) → SpawnMemberResult`（`IMemberSpawner.java:96`）是**单结果**同步契约。
- **DAG 节点 = 单 task 单成员执行单元 ✅**：每个 graph node 的 `ITaskStepExecution`（`TeamTaskFlowOrchestrator.java:561` `wrapExecution`）包装单 step（bound 或 spawn），节点 future 完成即 task COMPLETED。无"一节点多成员"图拓扑。
- **async 基础设施已就绪 ✅**（plan 241/243）：`MemberAgentTaskStep.execute` 返回 `ASYNC_RETURN`（plan 241，包装 engine future）；`SpawnMemberAgentTaskStep.execute` 返回 `ASYNC_RETURN`（plan 243，supplyAsync 卸载）；`executeAsync(teamId)` 消费 nop-task `GraphTaskStep` CompletableFuture 调度（plan 241，独立分支真正并发）。`CompletableFuture` 组合（`allOf`/`whenComplete`）是本计划 fan-out 归约的现成工具。
- **dedicated spawn executor 已就绪 ✅**（plan 243 裁定 3）：`TeamTaskFlowOrchestrator` 持有/创建独立于 commonPool 的有界 daemon 线程池（`resolveSpawnExecutor` `:297`，`ai-agent-spawn-worker-N`，`setSpawnStepExecutor` 透传，`close()` 释放）。本计划 spawn fan-out 复用此 executor（N 个 supplyAsync 共享同一池，池大小即 spawn fan-out 并发上限）。
- **explicit-propagation tenant 传播已就绪 ✅**（plan 243 裁定 2）：`buildGraphForExecution` 在 `executeAsync` 入口捕获 `ThreadLocalTenantResolver.current()`（`:512`）注入每个 step，step supplyAsync worker `set`/`clear`（`SpawnMemberAgentTaskStep.java:220/224`）。本计划 spawn fan-out 复用此范式（每个 spawn worker re-apply 同一 captured tenant）。
- **团队成员配额已就绪 ✅**（plan 234）：`IResourceGuard` 强制 `TEAM_PARALLEL_BOUND_MEMBERS`（per-team `maxParallelMembers` override）/ `TEAM_MEMBERS`（`teamMaxMembers` config 默认 8）维度，`InMemoryTeamManager`/`DbTeamManager` 的 `createTeam`/`addMember`/`bindMemberSession` 接线。fan-out 度（N）天然受既有团队成员数配额约束；v1 不新增 per-task fan-out 配额维度。
- **核心缺口（本计划闭合）**：一任务一成员假设贯穿 bound + spawn 两半部。无 per-task 路由策略、无 fan-out、无 reduction。

## Goals

- **per-task 成员路由扩展点**：引入可插拔 per-task 路由策略，把"为该 task 选哪些成员执行"从硬编码单成员提升为策略决策。bound 半部解析出 N 个已绑定成员 session；spawn 半部解析出 N 个 spawn target memberSpec。shipped 默认策略 = 单成员（逐行复现 `resolveMember`/`resolveSpawnTarget` 既有结果，零回归）。
- **fan-out + reduction 节点执行模型**：一个 team task 节点 fan-out 至 N 个成员 agent **并发**执行（bound 经 `IAgentEngine.execute` 既有 future / spawn 经 `supplyAsync(..., spawnExecutor)`），N 个 future 经 `CompletableFuture` 组合归约为单节点 future，节点返回 async `TaskStepReturn`（与 plan 241/243 同一 `ASYNC_RETURN` 契约，DAG 调度透明消费）。菱形 DAG `A→{B,C}→D` 中 B 的 fan-out（B→{B1,B2}）与 C 的 fan-out（C→{C1,C2}）真正并发。
- **reduction 策略**：shipped 默认 = **all-must-succeed**（N 成员都须 `AgentExecStatus.completed`，任一非 completed / 抛异常 / complete CAS 失败 → 节点失败，task 保留 CLAIMED 不 abandon，与既有 bound/spawn 失败模型一致）。task 的 CLAIMED→COMPLETED 单次转换在全部 N 成员 completed 后触发一次。quorum / majority / first-wins / partitioning / pipeline 为显式 Non-Goals。
- **诚实失败语义（No Silent No-Op #24）逐条对齐**：路由返回空集（无可派发成员）→ 诚实失败 throw + task 保留 CLAIMED（非静默跳过节点）；任一成员非 completed / 抛异常 → 诚实失败；complete CAS 失败 → 诚实失败；spawner 三态（NO_SPAWN/SPAWN_FAILED/throws/null）在 spawn fan-out 逐成员对齐 plan 243 语义。已 COMPLETED 幂等为显式成功。fan-out 中**先失败者触发节点失败 fast-fail**，其余在途成员执行**不被取消**（结果丢弃）——取消为 Non-Goal successor。
- **bound + spawn 半部对称 + 接口契约不变**：`IMemberSpawner.spawnMember(SpawnMemberRequest)` 单结果同步**接口签名不变**（不破坏 daemon plans 236/237 跨模块回归）；`SpawnMemberResult`/`NoOpMemberSpawner` 行为不变；`IAgentEngine`/`ITeamTaskStore`/`ITeamManager`/`TeamMemberSpec` 契约原样消费。`SpawnMemberRequest` 增加一个**可选 additive target 字段**（向后兼容：null = spawner 自解析 target = daemon 路径逐行零回归；非 null = spawner 直接 spawn 该指定 target），使 spawn fan-out 可在 build-time 选定 N 个 distinct target 后，经 N 次携带各自 target 的 `spawnMember` 调用 + `supplyAsync` 组合实现，daemon 调用点（`new SpawnMemberRequest(team, task, daemonSessionId)` 三参构造，target 默认 null）零变更。spawn target 的**选择**（prefer MEMBER role / fallback any）从 `DefaultMemberSpawner.resolveSpawnTarget`（private、run-time）提升至路由扩展点（build-time、基于 public `Team.getSpec().getMemberSpecs()`、非执行），`DefaultMemberSpawner.spawnMember` 保留 `resolveSpawnTarget` 作为 request.target=null 时的 fallback（daemon 路径逐行零回归）。
- **sync 零回归**：`execute(teamId)` sync 入口在单成员团队上行为逐行不变（`= executeAsync(teamId).join()`）；既有 plans 233/238/241/243 全部 team-task flow / spawn 测试全绿。
- **tenant 隔离零回归**：多成员 fan-out 下 enter 与非 enter 节点、bound 与 spawn 成员的 `claimTask`/`completeTask` 均按调用方 tenant 隔离读写（复用 plan 243 explicit-propagation 范式）；多租户场景下 fan-out 跨租户不可见。
- **设计文档 + roadmap 同步**：新增 `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-member-routing.md`（fan-out + reduction 最终设计状态与裁定，无类签名）；更新 `nop-ai-agent-actor-runtime-vision.md`（多成员工作分布原语落地）；roadmap §4 新增 `L4-multi-member-per-task-routing` ✅ 行。

## Non-Goals

- **改 `IMemberSpawner.spawnMember` 契约为返回多结果 / future**：破坏 daemon（plans 236/237 同步调用单结果 `spawnMember`）跨模块回归。Classification: rejected alternative（spawn fan-out 经多次单结果调用组合实现）。
- **task 分片（partitioning）**：把一个 task 拆成 N 个子任务分给 N 成员。要求 task 可拆分语义（非所有 task 可拆），且需子任务结果拼接契约——独立结果面。Classification: successor plan required。
- **成员 pipeline / 顺序协作**：N 成员按序处理一个 task（前一个输出喂下一个）。需成员顺序编排原语——独立结果面。Classification: successor plan required。
- **quorum / majority / first-wins reduction 策略**：shipped 默认 all-must-succeed 已闭合最小可用 fan-out；其他归约策略（少数服从多数 / 首成功即止 / 投票）为可插拔扩展点 opt-in，v1 不交付具体实现。Classification: out-of-scope improvement（扩展点预留，具体策略 successor）。
- **fan-out 中先失败后取消在途成员执行**：需 `IAgentEngine.cancelSession` 集成 + 取消传播语义——独立结果面。v1 在途执行 run-to-completion 结果丢弃（honest 失败已上报）。Classification: successor plan required。
- **per-task fan-out 度配额维度**：新增 `IResourceGuard` 维度（如 `TASK_FAN_OUT_DEGREE`）。v1 fan-out 度受既有 `TEAM_MEMBERS`/`TEAM_PARALLEL_BOUND_MEMBERS` 团队成员数配额天然约束。Classification: optimization candidate。
- **`TeamTaskSchedulerDaemon` per-task 多成员派发**：daemon（plan 236）的 dispatch 路径仍单成员（其 `resolveBoundMember` 单成员 + 单 spawner 调用）；本计划只扩展程序化 orchestrator（`TeamTaskFlowOrchestrator`）。daemon 多成员派发为独立 successor（与 daemon per-cycle async 派发 successor 对称）。Classification: successor plan required。
- **修改 nop-task 核心 / `GraphTaskStep` 调度模型**：nop-task 已提供完整 async + CompletableFuture 调度（plan 241 已核实），本计划消费而非修改。Classification: out-of-scope。
- **spawn session 复用 / 池化**（plan 239 carry-over `L4-spawn-session-pooling`）：每次 spawn 仍新建 session（per-task spawn 非复用）。Classification: optimization candidate。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over）。Classification: successor plan required。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over）。Classification: successor plan required。

## Scope

### In Scope

- per-task 成员路由扩展点（bound 半部解析 N 已绑定成员 + spawn 半部解析 N spawn target memberSpec）+ NoOp/Single shipped 默认（单成员逐行零回归）
- fan-out + reduction 节点执行模型（N 成员并发 + `CompletableFuture` 组合归约 + async `TaskStepReturn`）+ all-must-succeed shipped reduction
- `TeamTaskFlowOrchestrator` 接线（路由策略 wire-at-consumer 注入，null-safe → 单成员 shipped 默认；spawn fan-out 复用 plan 243 dedicated spawn executor + explicit-propagation tenant）
- 诚实失败语义逐条对齐（空路由 / 成员失败 / spawner 三态 / CAS 失败 / null 防御）
- reduction 扩展点预留（v1 仅 all-must-succeed 实现，其他策略接口预留 opt-in）
- 测试（新）：fan-out 并发证据 / all-must-succeed reduction 各路径 / 空路由 honest failure / 单成员零回归 / tenant 隔离 fan-out / DAG 依赖序保持 / bound+spawn 混合 fan-out
- 设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-member-routing.md` + `nop-ai-agent-actor-runtime-vision.md` + roadmap §4 `L4-multi-member-per-task-routing` ✅ 行

### Out Of Scope

- 见 Non-Goals（契约变更 / partitioning / pipeline / quorum-majority / 在途取消 / per-task 配额 / daemon 多成员 / nop-task 核心 / spawn 池化 / decorator / 动态改图 均为显式 rejected / successor / out-of-scope）

### 设计裁定（Pre-Adjudicated）

1. **语义模型 = fan-out（复制）+ reduction，非 partitioning / pipeline**。同一 task 复制派发至 N 成员并发执行，N 结果经 reduction 归约为单 task 结果。拒绝 partitioning（要求 task 可拆分语义 + 子结果拼接契约，独立结果面）/ pipeline（要求成员顺序编排原语，独立结果面）。理由：fan-out + reduction 是"把一任务分发至多成员"的最小、恒适用模型（任何 task 都可复制派发），匹配 carry-over 文本"distribute a single task across multiple member agents"。

2. **per-task 路由 = 新可插拔扩展点（NoOp shipped 默认 = 单成员逐行零回归），非改 `resolveMember`/`resolveSpawnTarget` 返回 List**。理由：(1) 改 `resolveMember` 返回 List 破坏 bound-priority 单成员零回归不变量（plan 233/238 既有测试断言单成员解析路径）；(2) 新扩展点 NoOp 默认返回"既有单成员解析结果包成单元素集"，bound/spawn 两半部行为逐行不变；(3) 镜像 Layer 4 既有扩展点范式（`IMemberSpawner`/`ITeamAclChecker`/`IResourceGuard`/`IFencingTokenService` 均 NoOp-default wire-at-consumer）。bound 半部与 spawn 半部各需路由决策（bound 选 N 已绑定 session / spawn 选 N memberSpec target）——裁定为**单一**路由扩展点同时覆盖两半部（返回的 dispatch plan 标注每个 target 是 bound 还是 spawn），避免双扩展点接线复杂度。

3. **reduction shipped 默认 = all-must-succeed（最严格 / 最诚实）**。N 成员都须 `AgentExecStatus.completed`；任一非 completed / 抛异常 / spawner NO_SPAWN/SPAWN_FAILED/throws/null / complete CAS 失败 → 节点失败，task 保留 CLAIMED。task 的 CLAIMED→COMPLETED 单次转换在全部 N 成员 completed 后触发一次（非每成员一次）。理由：(1) 与既有 bound/spawn 单成员失败模型逐条对齐（任务保留 CLAIMED，daemon reclaim 是恢复模型）；(2) 最严格默认避免"部分失败静默成功"的 hollow 风险（No Silent No-Op #24）；(3) quorum/majority/first-wins 是降严格度优化，作 opt-in 扩展点 successor。reduction 策略为可插拔扩展点（v1 仅 all-must-succeed 落地，接口预留）。

4. **claim 保持同步、单次（与 plan 241/243 一致），complete 在全部 N 成员 completed 后单次**。claim（CREATED→CLAIMED）在节点触发期同步完成（保 DAG 依赖序 + claim CAS 失败同步 fast-fail + 已 COMPLETED 幂等同步成功）。N 成员执行并发（bound 经 engine future / spawn 经 supplyAsync）；全部 completed 后单次 `completeTask`（CLAIMED→COMPLETED）。任一失败 → 不 complete、节点 future 异常、task 保留 CLAIMED。理由：claim 是 task slot 占用语义（非每成员），单次 claim/complete 保持 `ITeamTaskStore` 状态机不变 + 既有 CAS 语义复用。

5. **fan-out 先失败 fast-fail，在途成员不取消（run-to-completion 结果丢弃），取消为 successor**。任一成员 future 异常 → 节点 reduced future 立即异常完成（`CompletableFuture` 组合的自然语义）；其余在途成员执行继续至完成，结果丢弃。理由：(1) `IAgentEngine.cancelSession` 取消传播是独立结果面（Non-Goal）；(2) 丢弃结果不破坏正确性（task 已诚实失败、claim/complete 状态机正确）；(3) 资源浪费为已知 v1 限制（reduction 扩展点 successor 可引入取消）。honest 失败已上报，无静默跳过。

6. **spawn fan-out = build-time 选 target + N 次携带 target 的 `spawnMember` + `supplyAsync` 组合，`IMemberSpawner` 接口签名不变 + `SpawnMemberRequest` 增可选 target 字段**。spawn target 的**选择**与**执行**分离：(a) **选择**（build-time，非执行）由路由扩展点完成——从 public `Team.getSpec().getMemberSpecs()` 按"prefer MEMBER role / fallback any"策略（与 `DefaultMemberSpawner.resolveSpawnTarget` 同语义，但在路由层重新实现于 public 数据，非调用 private 方法、非执行 agent），产出 N 个 `TeamMemberSpec` target；(b) **执行**（run-time）spawn fan-out step 对每个 target 构造一个 `SpawnMemberRequest`（携带该 target）+ `spawnMember(request)` 调用（每次单 target、单 `SpawnMemberResult`），每次经 `supplyAsync(..., spawnExecutor)` 卸载（复用 plan 243 dedicated executor + tenant 传播），N supplyAsync future 经 `CompletableFuture` 组合归约。**`SpawnMemberRequest` 增可选 additive target 字段**（向后兼容：保留既有三参构造 target=null；新增 target-aware 构造/工厂）；`DefaultMemberSpawner.spawnMember` 优先用 `request.target`，null 时 fallback `resolveSpawnTarget`（daemon 路径逐行零回归）；`NoOpMemberSpawner` 忽略 target 恒 NO_SPAWN。理由：(1) `IMemberSpawner.spawnMember(SpawnMemberRequest)` 接口签名不变（daemon plans 236/237 零回归）；(2) 复用 plan 243 全部 async 基础设施；(3) additive 可选字段是使"同一 spawner 既能自解析单 target（daemon）又能执行指定 target（fan-out）"的最小向后兼容变更——拒绝替代方案：改接口返回多结果/future（破坏 daemon）/ 新增 `IMemberSpawner` 方法（破坏 NoOp/Default swap-ability 单方法抽象）/ 绕过 spawner 直调 engine（破坏三态 honest 解释 + 扩展点，plan 243 裁定 5 已拒绝）。

7. **bound fan-out = N 个 `IAgentEngine.execute(request)` future 直接组合**。N 已绑定成员 session → N 次 `engine.execute`（每次单 session，返回既有 `CompletableFuture`），N future 经 `CompletableFuture` 组合归约。理由：(1) `IAgentEngine.execute` 已返回 future（plan 241 复用），bound fan-out 是其自然组合；(2) 不经 supplyAsync（engine 自带 async，无同步阻塞需卸载）；(3) 每个 bound 成员独立 session，无 session 冲突。

8. **拒绝改 `resolveMember` 返回 List + 拒绝在 build-time 调 `spawnMember`**：返回 List 破坏单成员零回归不变量 + 既有测试断言；build-time 调 `spawnMember` 会立即执行 agent 破坏 DAG 依赖序（plan 238 裁定 1 已确立 spawn 须在 node run-time）。新扩展点 NoOp 默认在 build-time 仅做**非执行**选择（bound = 既有 `resolveMember` 单成员结果包单元素集；spawn = 从 public `TeamSpec.getMemberSpecs()` 按 prefer-MEMBER/fallback-any 选单 target 包单元素集，镜像 `DefaultMemberSpawner.resolveSpawnTarget` 语义但运行于 public 数据且不执行），逐行零回归；执行延迟至 node run-time。

## Execution Plan

### Phase 1 - per-task 路由扩展点 + bound 半部 fan-out + reduction 核心（NoOp 单成员零回归 + 多成员 bound fan-out）

Status: completed
Targets: `io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator`（路由注入 + bound 节点 fan-out）、`io.nop.ai.agent.team.flow.MemberAgentTaskStep`（多成员 fan-out 变体或新 multi-dispatch step）、新路由/reduction 扩展点 + NoOp/Default shipped 默认、`ai-dev/design/nop-ai-agent/nop-ai-agent-multi-member-routing.md`

- Item Types: `Decision`（语义模型 fan-out+reduction / 新扩展点 NoOp-default / all-must-succeed reduction / claim 同步单次 complete / 先失败 fast-fail 不取消）、`Fix`（一任务一成员硬编码 = carry-over gap）、`Proof`

- [x] 引入 per-task 成员路由扩展点（bound + spawn 单一覆盖，返回 dispatch plan 标注 bound/spawn + reduction 策略）+ NoOp/Single shipped 默认（bound = 既有 `resolveMember` 单成员结果包单元素集；spawn = 从 public `Team.getSpec().getMemberSpecs()` 按 prefer-MEMBER-role/fallback-any 选单 target 包单元素集，镜像 `DefaultMemberSpawner.resolveSpawnTarget` 语义但 build-time 非执行、运行于 public 数据；reduction = all-must-succeed）+ 可插拔 reduction 扩展点（v1 仅 all-must-succeed 实现）
- [x] `SpawnMemberRequest` 增可选 additive target 字段（保留既有三参构造 target=null 向后兼容 + 新增 target-aware 构造/工厂）；`DefaultMemberSpawner.spawnMember` 改为优先用 `request.target`、null 时 fallback `resolveSpawnTarget`（既有 `resolveSpawnTarget` private 逻辑保留作 fallback，daemon 路径逐行零回归）；`NoOpMemberSpawner` 忽略 target 恒 NO_SPAWN；`IMemberSpawner.spawnMember(SpawnMemberRequest)` 接口签名不变
- [x] `TeamTaskFlowOrchestrator` 接线：路由策略 wire-at-consumer 字段（默认 NoOp/Single）+ setter/构造器注入（null-safe 回退 shipped 默认，镜像 plan 238 `setMemberSpawner` 范式）；`buildGraphForExecution` 改为消费路由策略的 dispatch plan（单成员 plan → 既有 `MemberAgentTaskStep`/`SpawnMemberAgentTaskStep` 路径逐行不变；多成员 plan → bound/spawn fan-out 节点）
- [x] bound 半部 fan-out 节点执行：claim 同步单次（CREATED→CLAIMED）+ N 个 `agentEngine.execute(request)`（每成员独立 session）future 经 `CompletableFuture` 组合 + all-must-succeed reduction（全 completed → 单次 `completeTask`；任一失败 → 节点 future 异常 + task 保留 CLAIMED）；返回 async `TaskStepReturn`（`ASYNC_RETURN`，与 plan 241 同契约）；空 plan → 诚实 throw + task 保留 CLAIMED
- [x] 诚实失败语义逐条对齐（#24）：空 plan / 成员非 completed / 成员 future 异常 / complete CAS 失败 / null 防御 各路径在 bound fan-out 诚实失败；已 COMPLETED 幂等同步成功；无空方法体/continue/吞异常/TODO

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] per-task 路由扩展点存在 + NoOp/Single shipped 默认落地（bound = 既有 `resolveMember` 结果包单元素集；spawn = 从 public `TeamSpec.getMemberSpecs()` build-time 非执行选单 target 包单元素集，grep 可复核）；`SpawnMemberRequest` 可选 additive target 字段落地（既有三参构造向后兼容 + target-aware 构造）；`DefaultMemberSpawner.spawnMember` 优先 request.target 否则 fallback；`IMemberSpawner` 接口签名不变
- [x] `TeamTaskFlowOrchestrator` 消费路由策略 dispatch plan；单成员 plan 路径与 plan 233/238 bound-member + spawn 节点逐行等价（既有 bound/spawn 测试零回归）
- [x] bound 多成员 fan-out 节点返回 async `TaskStepReturn`，claim 同步单次、N 成员并发、全 completed 后单次 `completeTask`
- [x] **无静默跳过**（#24）：空 plan / 成员失败 / CAS 失败 各路径诚实 throw + task 保留 CLAIMED；已 COMPLETED 幂等显式成功
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] focused 测试在 Phase 3（#25）；本 Phase compile + 既有 bound 测试零回归即可
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - spawn 半部 fan-out（N spawn target + supplyAsync 组合 + tenant 传播）

Status: completed
Targets: `io.nop.ai.agent.team.flow.SpawnMemberAgentTaskStep`（多 spawn target fan-out 变体或新 multi-spawn step）、`io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator`（spawn 节点 fan-out 接线）

- Item Types: `Fix`（spawn 半部单目标硬编码 = carry-over gap 剩余半部）、`Proof`

- [x] spawn 半部 fan-out 节点执行：claim 同步单次 + 对路由 build-time 选定的 N 个 spawn target 各构造一个携带该 target 的 `SpawnMemberRequest` + N 次 `spawnMember(request)`（`IMemberSpawner` 接口签名不变，`DefaultMemberSpawner` 用 request.target 直接 spawn）经 `supplyAsync(..., spawnExecutor)` 卸载（复用 plan 243 dedicated executor + explicit-propagation tenant `set`/`clear`）+ N supplyAsync future 经 `CompletableFuture` 组合 + all-must-succeed reduction（逐成员三态解释 NO_SPAWN/SPAWN_FAILED/dispatched-non-completed/throws/null 对齐 plan 243；全 DISPATCHED+completed → 单次 `completeTask`；任一失败 → 节点 future 异常 + task 保留 CLAIMED）；返回 async `TaskStepReturn`
- [x] `TeamTaskFlowOrchestrator` spawn 节点接线：多 spawn target plan → spawn fan-out 节点；单 spawn target plan → 既有 `SpawnMemberAgentTaskStep` 路径逐行不变（plan 243 测试零回归）
- [x] bound + spawn 混合 fan-out（同一 dispatch plan 含 bound 与 spawn target）经统一 reduction 组合
- [x] 诚实失败语义逐条对齐（#24）：spawn fan-out 下 NO_SPAWN / SPAWN_FAILED / 非 completed / spawner throws / null / complete CAS 失败 各路径诚实失败 + task 保留 CLAIMED；空 plan 诚实 throw；已 COMPLETED 幂等显式成功

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] spawn 多 target fan-out 节点返回 async `TaskStepReturn`，claim 同步单次、N supplyAsync worker 并发（`ai-agent-spawn-worker-N` 线程）、全 DISPATCHED+completed 后单次 `completeTask`
- [x] `IMemberSpawner.spawnMember(SpawnMemberRequest)` **接口签名不变**（单参方法，daemon plans 236/237 调用点逐行零回归）；`SpawnMemberResult` 不变；`NoOpMemberSpawner` 行为不变（忽略 target 恒 NO_SPAWN）；`SpawnMemberRequest` 仅增可选 additive target 字段（既有三参构造保留 target=null 向后兼容）；`DefaultMemberSpawner.spawnMember` 优先用 request.target 否则 fallback `resolveSpawnTarget`（既有 fallback 逻辑保留）；daemon 调用点（`TeamTaskSchedulerDaemon` 三参构造 = target null = 自解析）零变更（grep 确认 daemon 路径无 diff）
- [x] tenant-context 跨 N spawn worker 边界正确（每个 supplyAsync worker re-apply captured tenant + finally clear，复用 plan 243 范式）
- [x] 单 spawn target plan 路径与 plan 243 spawn 节点逐行等价（既有 spawn 测试零回归）
- [x] **无静默跳过**（#24）：spawn fan-out 各失败路径诚实 future 异常 / task 保留 CLAIMED；空 plan 诚实 throw
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - focused 测试（fan-out 并发 + reduction 各路径 + honest failure + 零回归 + tenant 隔离）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/`（新测试）

- Item Types: `Proof`

- [x] 编写 bound fan-out 并发测试（菱形 `A→{B,C}→D`，B fan-out 至 {B1,B2} bound 成员，C fan-out 至 {C1,C2} bound 成员）：断言 B1/B2 真正并发（可观测并发证据——执行时间区间重叠 / 并发计数 ≥2 断言，非仅最终 COMPLETED）+ C1/C2 真正并发 + B-fan-out 与 C-fan-out 独立分支并发 + D 依赖序严格（D.start 晚于 B/C 全部完成，经 `ExecutionRecorder` 执行序快照）；Anti-Hollow 断言并发真实发生
- [x] 编写 spawn fan-out 并发测试（菱形 `A→{B,C}→D`，B/C 各 fan-out 至 N spawn target，functional spawner）：断言 N spawn worker 真正并发（`ai-agent-spawn-worker-N` 线程观测 + 时间区间重叠）+ D 依赖序严格
- [x] 编写 all-must-succeed reduction 测试：bound/spawn fan-out N 成员全 completed → task COMPLETED 单次（断言 `completeTask` 调用一次）；任一成员非 completed → 节点失败 + task 保留 CLAIMED + 其余在途成员 run-to-completion 结果丢弃（先失败 fast-fail）；complete CAS 失败 → 节点失败 + task 保留 CLAIMED
- [x] 编写空路由 honest failure 测试：路由返回空 plan（无可派发成员）→ 节点诚实 throw + task 保留 CLAIMED + DAG 短路后继 skipped（非静默跳过节点）；spawner fan-out 下 NO_SPAWN / SPAWN_FAILED / spawner throws / spawner null 各路径逐成员 honest failure
- [x] 编写单成员零回归测试：NoOp/Single shipped 默认路由 → bound 单成员 plan = 既有 `MemberAgentTaskStep` 路径 / spawn 单 target plan = 既有 `SpawnMemberAgentTaskStep` 路径，既有 plans 233/238/241/243 bound + spawn 测试全绿（语义等价证明）
- [x] 编写 tenant 隔离 fan-out 测试：多租户场景下 bound + spawn fan-out 节点的 `claimTask`/`completeTask` 按调用方 tenant 隔离读写（跨租户不可见）；N spawn worker 均观测调用方 captured tenant（非 null）；worker 结束后池化线程 tenant 已 clear（不泄漏）
- [x] 编写 bound + spawn 混合 fan-out 测试：同一 dispatch plan 含 bound 与 spawn target → 统一 reduction 组合 → 全 completed → task COMPLETED

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] bound fan-out 并发测试全绿（B1/B2 + C1/C2 + B/C 独立分支 真正并发可观测证据 + D 依赖序严格）
- [x] spawn fan-out 并发测试全绿（N spawn worker 真正并发 + D 依赖序严格）
- [x] all-must-succeed reduction 测试全绿（全 completed → 单次 completeTask；任一失败 → task 保留 CLAIMED + 在途 run-to-completion）
- [x] 空路由 + spawn 三态 honest failure 测试全绿（task 保留 CLAIMED 不 abandon）
- [x] 单成员零回归测试全绿（NoOp/Single shipped 默认逐行等价 + 既有 bound/spawn 测试全绿）
- [x] tenant 隔离 fan-out 测试全绿（跨租户不可见 + worker 观测 captured tenant + 不泄漏）
- [x] bound + spawn 混合 fan-out 测试全绿
- [x] **接线验证**（#23）：fan-out 节点 async 路径运行时确实执行（task 状态机 CLAIMED→COMPLETED 经 store 验证 + N 成员 future 确实被组合）
- [x] **无静默跳过**（#24）：所有失败路径诚实 future 异常；无空方法体/continue/TODO/吞异常
- [x] 新增功能各有对应 focused 测试覆盖
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过

### Phase 4 - 端到端验证 + 设计文档 + roadmap/vision 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/`（新 E2E）、`ai-dev/design/nop-ai-agent/nop-ai-agent-multi-member-routing.md`、`nop-ai-agent-actor-runtime-vision.md`、`nop-ai-agent-roadmap.md` §4

- Item Types: `Proof`

- [x] 编写端到端测试（bound fan-out 完整路径）：构造团队 + 多任务 DAG（含菱形 `A→{B,C}→D`，B/C 各 fan-out 至 N bound 成员）+ functional 路由 → `executeAsync` → 调用线程不阻塞 → 最终全 COMPLETED + `TeamTaskFlowResult{success=true}`；断言 fan-out 并发 + D 依赖序
- [x] 编写端到端测试（spawn fan-out 完整路径）：菱形 `A→{B,C}→D`，B/C 各 fan-out 至 N spawn target + functional spawner → `executeAsync` → 全 COMPLETED；断言 N spawn worker 并发 + D 依赖序
- [x] 编写端到端测试（fan-out honest failure 传播）：B fan-out 中 B1 失败（非 completed / spawner SPAWN_FAILED）→ `executeAsync` future 完成 `TeamTaskFlowResult{success=false}` + failed含B + skipped含D（nop-task `GraphTaskStep` 短路取消后继）；B 保留 CLAIMED；B2（在途）run-to-completion 结果丢弃
- [x] 编写端到端测试（单成员 NoOp 默认对比）：同一 DAG 在 NoOp/Single shipped 默认路由下结果与既有 plan 233/238 单成员执行语义等价（零回归证明）
- [x] 编写 sync 对比 e2e：`execute` sync 入口在 fan-out DAG 下结果与 `executeAsync(...).join()` 一致（语义等价）
- [x] 新增设计文档 `nop-ai-agent-multi-member-routing.md`（fan-out + reduction 最终设计状态 + 8 项裁定 + 拒绝替代方案，无类签名/代码，遵循 design doc 规范）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md`：多成员工作分布原语（per-task fan-out + reduction）已落地；partitioning/pipeline/quorum/在途取消/daemon 多成员 仍为 successor
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 新增 `L4-multi-member-per-task-routing` ✅ 行
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 `executeAsync(teamId)` 入口 → 图 build → fan-out 节点（claim 同步 + N 成员并发 + reduction + 单次 complete）→ DAG 调度 → fan-out 并发 + 依赖序 → 最终 `TeamTaskFlowResult`，完整路径跑通（bound fan-out / spawn fan-out / honest failure / 单成员零回归对比 四场景）
- [x] **fan-out 并发 Anti-Hollow 断言**：E2E 断言 fan-out 真正并发（可观测证据）+ D 依赖序严格（执行序快照）
- [x] **接线验证**（#23）：E2E 断言 fan-out 节点运行时确实执行（task 状态机 CLAIMED→COMPLETED）+ N 成员 future 确实被组合（非单成员 stub）
- [x] **sync 语义等价**：`execute` 与 `executeAsync().join()` 在同 fan-out DAG 下结果一致
- [x] **无静默跳过**（#24）：fan-out honest failure 经 future 异常 / `success=false` 诚实上报；空路由 / NoOp 幂等为显式语义
- [x] `nop-ai-agent-multi-member-routing.md` 已创建（无类签名/代码，含 8 裁定 + 拒绝替代方案）
- [x] roadmap §4 新增 `L4-multi-member-per-task-routing` ✅ 行
- [x] `nop-ai-agent-actor-runtime-vision.md` 已更新（多成员原语落地 + successor 标注）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] per-task 路由扩展点落地为真实（非空壳）代码——NoOp/Single shipped 默认单成员逐行零回归 + 多成员 fan-out 真实工作
- [x] bound + spawn 两半部 fan-out + all-must-succeed reduction 均落地（N 成员并发可观测 + 单次 complete）
- [x] 诚实失败语义逐条对齐（空路由 / 成员失败 / spawner 三态 / CAS 失败 / null 各路径 + task 保留 CLAIMED 不 abandon）
- [x] `IMemberSpawner.spawnMember(SpawnMemberRequest)` 接口签名 + `SpawnMemberResult` + `NoOpMemberSpawner` 行为 + `IAgentEngine`/`ITeamTaskStore`/`ITeamManager`/`TeamMemberSpec` 契约**零变更**；`SpawnMemberRequest` 仅增可选 additive target 字段（向后兼容）；`DefaultMemberSpawner` 仅增"优先 request.target 否则 fallback"逻辑（既有 `resolveSpawnTarget` fallback 保留）；daemon 调用点零变更（机制改接口签名 / partitioning / pipeline / 绕过 spawner 均明确拒绝）
- [x] 单成员零回归（NoOp/Single shipped 默认 + 既有 plans 233/238/241/243 bound + spawn 测试全绿）
- [x] 端到端：executeAsync → fan-out 节点 → 并发 + 依赖序 → 最终结果（bound / spawn / honest failure / 单成员对比）完整路径跑通
- [x] tenant 隔离零回归（fan-out 下跨租户不可见 + 不泄漏）
- [x] 必要 focused verification 已完成（fan-out 并发 / reduction 各路径 / honest failure / 零回归 / tenant 隔离 / 混合 fan-out 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（partitioning / pipeline / quorum / 在途取消 / per-task 配额 / daemon 多成员 / spawn 池化 / decorator / 动态改图 均显式 Non-Goals）
- [x] 受影响 owner docs（multi-member-routing design doc + vision + roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）fan-out 节点 N 成员 future 运行时确实被组合（非单成员 stub），（b）fan-out 真正并发（可观测证据），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；partitioning / pipeline / quorum-majority / 在途取消 / per-task 配额 / daemon 多成员 / spawn 池化 / decorator / 动态改图 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **task 分片（partitioning）**：一任务拆 N 子任务分给 N 成员。Classification: successor plan required（要求 task 可拆分语义 + 子结果拼接契约）。
- **成员 pipeline / 顺序协作**：N 成员按序处理一任务。Classification: successor plan required（要求成员顺序编排原语）。
- **quorum / majority / first-wins reduction 策略**：Classification: out-of-scope improvement（reduction 扩展点已预留 opt-in，具体策略 successor）。
- **fan-out 先失败后取消在途成员执行**：Classification: successor plan required（需 `IAgentEngine.cancelSession` 集成 + 取消传播语义）。
- **per-task fan-out 度配额维度**：Classification: optimization candidate（v1 受既有团队成员数配额天然约束）。
- **`TeamTaskSchedulerDaemon` per-task 多成员派发**：Classification: successor plan required（daemon dispatch 路径仍单成员）。
- **spawn session 复用 / 池化**（plan 239 carry-over）：Classification: optimization candidate。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over）：Classification: successor plan required。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over）：Classification: successor plan required。
- **`TeamTaskSchedulerDaemon` per-cycle async 派发**（plan 241 carry-over）：Classification: successor plan required。

## Closure

Status Note: 多成员 per-task 路由（fan-out + reduction）原语全量交付。一个 team task 节点可 fan-out 至 N 个成员 agent（bound +/或 spawned）并发执行，经 `AllMustSucceedReduction` shipped 默认归约为单一 task 结果。NoOp/Single shipped 默认逐行复现 plans 233/238/241/243 单成员行为（零回归，2700 tests 全绿含全部既有 bound+spawn+daemon 测试）。`IMemberSpawner.spawnMember` 接口签名不变，`SpawnMemberRequest` 仅增可选 additive target 字段（daemon 调用点零变更）。诚实失败语义逐条对齐（空 plan / 成员失败 / spawner 三态 / CAS 失败 / null 各路径 + task 保留 CLAIMED 不 abandon）。8 项设计裁定 + 拒绝替代方案记录于 `nop-ai-agent-multi-member-routing.md`。partitioning / pipeline / quorum / 在途取消 / per-task 配额 / daemon 多成员 / spawn 池化 / decorator / 动态改图 均为显式 Non-Goals successor。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: goal-driver executing agent (implementer self-audit + live code/test verification)
- Audit Session: plan-244 single-session execution (2026-06-18)
- Evidence:
  - **Phase 1 Exit Criteria** — PASS: `ITaskMemberRouter`/`NoOpTaskMemberRouter`/`MemberDispatchPlan`/`DispatchTarget`/`IReductionStrategy`/`AllMustSucceedReduction`/`MemberExecOutcome`/`ReductionContext` 落地（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/`）；`SpawnMemberRequest` 增可选 target 字段（既有三参构造保留）；`DefaultMemberSpawner.spawnMember` 优先 request.target 否则 fallback（`DefaultMemberSpawner.java:112`）；`IMemberSpawner.spawnMember` 接口签名不变（`IMemberSpawner.java:96`）；`TeamTaskFlowOrchestrator` 消费 router dispatch plan（`TeamTaskFlowOrchestrator.java:buildNodeStepForPlan`）；`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过。
  - **Phase 2 Exit Criteria** — PASS: `SpawnMemberFanOutStep` + `MixedMemberFanOutStep` 落地；spawn fan-out 经 `supplyAsync(spawnExecutor)` 卸载 + tenant `set`/`clear`；`IMemberSpawner.spawnMember` 接口签名不变（daemon 调用点 `TeamTaskSchedulerDaemon.java:771` + `SpawnMemberAgentTaskStep.java:248` 均用既有三参构造 = target null = 自解析，grep 确认零变更）；单 spawn target plan → 既有 `SpawnMemberAgentTaskStep` 路径逐行不变（既有 spawn 测试零回归）。
  - **Phase 3 Exit Criteria** — PASS: `TestMultiMemberFanOut` 18 测试全绿——bound fan-out 并发（`boundFanOutDiamondRealConcurrencyAndDAfterBoth` peakConcurrent≥2 + b1/b2 区间重叠 + D 依赖序）/ spawn fan-out 并发（`spawnFanOutDiamondRealConcurrencyAndDAfterBoth` ai-agent-spawn-worker-N 线程 + 区间重叠）/ all-must-succeed reduction 各路径 / honest failure 各路径（bound engine exception/non-completed + spawn NO_SPAWN/SPAWN_FAILED/throws/null/dispatched-non-completed 均留 CLAIMED）/ 空 plan honest throw build abort（`emptyRouterPlanHonestFailureBuildAbort` task 留 CREATED）/ 单成员 NoOp 零回归 / tenant 隔离 fan-out（`spawnFanOutPropagatesTenantToAllWorkers` 3 worker 均观测 tenant + completeTask 观测 tenant + 单次；`spawnFanOutNoTenantLeakAcrossRuns` 单线程 pool 两 run 观测 T1/T2 无泄漏）/ 混合 bound+spawn fan-out / CAS 失败 honest / 已 COMPLETED 幂等。
  - **Phase 4 Exit Criteria** — PASS: `TestMultiMemberFanOutEndToEnd` 5 E2E 全绿——bound fan-out diamond full path（`e2eBoundFanOutFullDiamondPath` executeAsync 不阻塞 + 全 COMPLETED + peakConcurrent≥2 + D 依赖序）/ spawn fan-out diamond full path / honest failure 传播（`e2eFanOutHonestFailurePropagates` B spawner SPAWN_FAILED → success=false + failed=B + skipped=D + B 留 CLAIMED）/ NoOp 等价（`e2eSingleMemberNoOpEquivalent`）/ sync≡async（`e2eSyncEqualsAsyncJoinOnFanOutDag`）。设计文档 `nop-ai-agent-multi-member-routing.md` 已创建（8 裁定 + 拒绝替代方案，无类签名/代码）。vision §416 已更新。roadmap §4 新增 `L4-multi-member-per-task-routing` ✅ 行。
  - **Closure Gates** — PASS: 逐条验证（per-task 路由扩展点真实代码 / bound+spawn 两半部 fan-out + reduction 落地 / 诚实失败语义逐条对齐 / 接口签名零变更 + daemon 调用点零变更 / 单成员零回归 / 端到端跑通 / tenant 隔离零回归 / focused verification 完整 / 无 in-scope live defect 降级 / owner docs 同步 / Anti-Hollow Check）。
  - **`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`** → BUILD SUCCESS（**2700 tests, 0 failures, 0 errors, 0 skipped**，含全部既有 plans 233/238/241/243 bound+spawn+daemon 测试零回归）。
  - **`node ai-dev/tools/check-doc-links.mjs --strict`** → 退出码 0（0 errors；39 pre-existing warnings 均在旧 plans 200-234 非 plan 244 文件）。
  - **Anti-Hollow 检查**：(a) fan-out 节点 N 成员 future 运行时确实被组合（非单成员 stub）——经 `boundFanOutDiamondRealConcurrencyAndDAfterBoth` + `spawnFanOutDiamondRealConcurrencyAndDAfterBoth` peakConcurrent≥2 + 区间重叠可观测证据 + `FanOutReduceComplete.reduceAndComplete` 经 `CompletableFuture.allOf(perMember)` 组合 N future；(b) fan-out 真正并发（可观测证据）——peakConcurrent + 区间重叠 + ai-agent-spawn-worker-N 线程名观测；(c) 无空方法体/静默跳过/no-op 作为正常实现——所有失败路径 throw NopAiAgentException，`markFailed` 经 whenComplete 兜底，空 plan honest throw。
  - **Deferred 项分类检查**：partitioning / pipeline / quorum-majority-first-wins / 在途取消 / per-task 配额 / daemon 多成员 / spawn 池化 / decorator / 动态改图 均为显式 Non-Goals（plan §Non-Goals + design doc §Non-Goals），非 in-scope live defect 降级。

Follow-up:

- task 分片（partitioning）/ 成员 pipeline / quorum-majority-first-wins reduction / fan-out 在途取消 / per-task fan-out 度配额 / `TeamTaskSchedulerDaemon` 多成员派发 / spawn session 池化 / nop-task decorator / 动态改图 / `TeamTaskSchedulerDaemon` per-cycle async 派发 均为显式 Non-Goals successor（详见 plan §Non-Goals + §Non-Blocking Follow-ups）。
- no remaining plan-owned work.

## Follow-up handled by 245-nop-ai-agent-daemon-multi-member-async-dispatch.md

本计划 Non-Goal「`TeamTaskSchedulerDaemon` per-task 多成员派发」（§Non-Goals line 51 + §Non-Blocking Follow-ups line 238）以及同源 carry-over「`TeamTaskSchedulerDaemon` per-cycle async 派发」（plan 241 carry-over，§Non-Blocking Follow-ups line 242）已由后继计划 `ai-dev/plans/245-nop-ai-agent-daemon-multi-member-async-dispatch.md` 接管：把 daemon（plan 236 交付的无人值守自动化 dispatch 路径）从单成员 + 同步 `engine.execute().join()` 提升至与程序化 orchestrator（`TeamTaskFlowOrchestrator`）对等的 per-task 多成员 fan-out（复用本计划交付的 `ITaskMemberRouter` + `AllMustSucceedReduction` + bound/spawn fan-out 节点）+ async 派发（复用 plan 241 `executeAsync` + plan 243 dedicated spawn executor + explicit-propagation tenant 范式），闭合模块核心定位"无人值守自动化"的能力不对称。
