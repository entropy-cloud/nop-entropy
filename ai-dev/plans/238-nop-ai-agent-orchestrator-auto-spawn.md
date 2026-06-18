# 238 nop-ai-agent 程序化编排器（TeamTaskFlowOrchestrator）接入 auto-spawn：未绑定成员的 DAG 节点在图运行期 spawn 执行

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-orchestrator-auto-spawn-integration

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/237-nop-ai-agent-member-auto-spawn.md`（Non-Blocking Follow-ups「orchestrator（plan 233 `TeamTaskFlowOrchestrator`）auto-spawn 集成：spawner 扩展点就绪后，只需另一处 wiring（orchestrator member resolution 失败时咨询 spawner）。Classification: successor plan required（依赖本计划的 spawner 契约）」+ Closure Follow-up「orchestrator auto-spawn 集成（successor plan required，依赖本计划 spawner 契约）」）；同一 carry-over 在 `ai-dev/plans/233-nop-ai-agent-task-flow-dag-integration.md`（决策4「不 spawn」+ Non-Goal「orchestrator auto-spawn」）中亦显式延期；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（orchestrator auto-spawn successor）
> Related: `237`（交付 `IMemberSpawner`/`NoOpMemberSpawner`/`DefaultMemberSpawner` 契约 + daemon 集成——本计划复用其 spawner 契约，在 orchestrator 侧再做一处 wiring）、`233`（交付 `TeamTaskFlowOrchestrator` + `MemberAgentTaskStep`——本计划修改其「未绑定成员 = fail-fast」为「未绑定成员 + functional spawner = spawn 执行」）

## Purpose

把 nop-ai-agent 的程序化 DAG 编排器 `TeamTaskFlowOrchestrator`（plan 233）从「每个 team task 节点必须解析到一个**已绑定**成员 session，否则 `resolveMember` fail-fast 抛 `nop.ai.team.flow.no-bound-member`」扩展为「当某节点无已绑定成员时，在**图运行期**（该节点被 nop-task DAG 调度器触发时）经 plan 237 交付的 `IMemberSpawner` spawn 一个成员 agent 执行该任务」。plan 237 已把 spawner 接入**守护进程**（`TeamTaskSchedulerDaemon.dispatchClaimedTask`，无人值守路径）；本计划把同一 spawner 契约接入**程序化编排器**（`TeamTaskFlowOrchestrator`，调用方可预绑定成员的程序化路径），闭合 roadmap §4 Layer 4 「编排器侧无人值守」的对应缺口。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-18）：

- **spawner 契约已落地（plan 237 ✅）**：`io.nop.ai.agent.team.IMemberSpawner`（接口）+ `NoOpMemberSpawner`（shipped 默认，`spawnMember` 恒返回 `SpawnMemberResult.noSpawn`）+ `DefaultMemberSpawner`（functional，构造注入 `IAgentEngine`）。输入 `SpawnMemberRequest(team, task, daemonSessionId)`；输出 `SpawnMemberResult`（三态：`DISPATCHED` 含 `executionResult`/`spawnedAgentName`/`spawnedSessionId`、`NO_SPAWN` 含 reason、`SPAWN_FAILED` 含 reason）。`DefaultMemberSpawner.spawnMember` 从 `TeamSpec.memberSpecs` 解析目标（优先 MEMBER role 回退任意 spec）→ 取 `TeamMemberSpec.getAgentModel()` → 构造 `AgentMessageRequest` → `agentEngine.execute(...).join()` **同步执行**。**spawner 自身执行任务**（不只是解析目标）。
- **守护进程集成已落地（plan 237 ✅）**：`TeamTaskSchedulerDaemon.dispatchClaimedTask`（`TeamTaskSchedulerDaemon.java:569-631`）在 `member == null` 时构造 `SpawnMemberRequest` 调 `memberSpawner.spawnMember`，按 `NO_SPAWN`/`SPAWN_FAILED`/`DISPATCHED` 三态分别 abandon/abandon/`completeOrAbandonAfterExecution`（`TeamTaskSchedulerDaemon.java:677-714`，bound 与 spawned 共享）。daemon **拥有自己的 dispatch 循环**，spawner 在循环内**内联执行**（同步 join），daemon 随后解释结果做 complete/abandon。
- **程序化编排器已落地（plan 233 ✅）但仅消费已绑定成员**：`TeamTaskFlowOrchestrator.execute(teamId)` 从 `ITeamTaskStore` 取任务 → `TeamTaskGraphBuilder` 经 nop-task 真实环检测建图 → 每个任务一个图节点，节点的 step 为 `MemberAgentTaskStep`（`claimTask` → `agentEngine.execute(request).join()` → 非 completed 抛异常 → `completeTask`）。`resolveMember(Team, TeamTask)`（`TeamTaskFlowOrchestrator.java:201-235`）在无已绑定成员时于 `TeamTaskFlowOrchestrator.java:231-234` 抛 `NopAiAgentException("nop.ai.team.flow.no-bound-member ... (bind a member session before orchestrating, or use auto-spawn successor)")`。**`team/flow/` 包对 spawner 零引用**（grep `[Ss]pawner|spawnMember|IMemberSpawner` 在 `team/flow/` = 0 匹配）。异常文本自身已提示「use auto-spawn successor」。
- **关键架构差异（本计划的核心设计约束）**：daemon 在自己的 dispatch 循环里**内联**调 spawner（spawner 执行，daemon 解释结果）；orchestrator 则**把执行委托给图节点 step**（`MemberAgentTaskStep` 在图**运行期**才执行），而 `resolveMember` 在图**构建期**（graph build time）运行、在图运行之前就完成。因此**不能**把 spawner 的执行型 `spawnMember` 直接放进 `resolveMember`——那会在构建期（图运行之前、被依赖的前驱节点完成之前）就执行任务，**破坏 DAG 依赖顺序**（一个 `blockedBy` 另一任务的任务会在其前驱完成前被执行）。spawn 必须发生在**节点运行期**（nop-task DAG 调度器触发该节点时）。这是「daemon 侧是内联执行、orchestrator 侧必须运行期执行」的根本区别，也是本计划不是「一处 wiring」而是「一处运行期 spawn 执行路径」的原因。
- **无已绑定成员 = 构建期抛异常（当前 baseline 的精确行为）**：`resolveMember`（`TeamTaskFlowOrchestrator.java:201-235`）在图**构建期** for-loop（`TeamTaskFlowOrchestrator.java:149`）内运行，无已绑定成员时于 `:231-234` 抛 `NopAiAgentException("nop.ai.team.flow.no-bound-member ...")`。`execute()` 中**唯一**的 try/catch（`:173-182`）只包裹 `task.execute(taskRt).syncGetOutputs()`（图运行），**不**包裹构建期 for-loop。因此 `execute()` **向上抛出**该异常（不捕获、不返回失败结果）。现有测试 `TestTeamTaskFlowOrchestrator.noBoundMemberFailsFast`（`:437-454`）用 `assertThrows(NopAiAgentException.class, () -> orchestrator.execute(...))` 断言**抛异常**，并断言消息含 `no-bound-member`。本计划的新设计会让无已绑定成员的失败发生在**运行期** step 内（被 `:176-182` 的既有 try/catch 捕获 → 返回 `TeamTaskFlowResult{success=false}`），因此该路径是**有意的 API 契约变更（throw → return-failed-result）**，不是零回归——`noBoundMemberFailsFast` 须相应改写（见 Phase 1）。零回归仅适用于 bound-member 路径。
- **接线惯例（plan 237 决策5）**：本模块扩展点采用 wire-at-consumer（`IResourceGuard`→TeamManager consumer、spawner→daemon consumer）。orchestrator 是 spawner 在本计划的 consumer，故 spawner 注入 orchestrator（构造器/setter，null-safe→NoOp shipped 默认），不经 `DefaultAgentEngine` 中转。

## Goals

- **orchestrator auto-spawn**：`TeamTaskFlowOrchestrator` 在某 team task 节点无已绑定成员时，不 fail-fast，而是在**图运行期**（该节点被 DAG 调度器触发时）经注入的 `IMemberSpawner` spawn 成员 agent 执行该任务。spawn 发生在节点运行期，DAG 依赖顺序得到保持（被依赖的任务仍在其前驱完成后才 spawn-执行）。
- **复用 spawner 契约（不改契约）**：复用 plan 237 的 `IMemberSpawner`/`SpawnMemberRequest`/`SpawnMemberResult` 原样，不修改 spawner 接口。orchestrator 侧的 spawn 路径消费同一三态结果（`DISPATCHED`→按 `AgentExecutionResult` 状态 complete/fail、`NO_SPAWN`/`SPAWN_FAILED`→诚实 fail），镜像 daemon 的 `dispatchClaimedTask` spawn 分支 + `completeOrAbandonAfterExecution` 语义。
- **bound-member 优先不变**：`resolveMember` 的已绑定成员解析路径（claimedBy 优先 → MEMBER role → 回退任意 bound）**完全不变**。spawner 仅在「无已绑定成员」分支被咨询。
- **bound-member 路径零回归 + 无已绑定成员路径诚实失败契约保留（有意 API 变更）**：bound-member 路径（已绑定成员解析与执行）与 plan 233 baseline **逐行不变**（零回归）。无已绑定成员路径仍是**诚实失败**，但失败的可观测形态做**有意变更**：今天 `execute()` 对无已绑定成员**抛 `NopAiAgentException`**；新设计下（NoOp/functional spawner）该失败发生在运行期 step 内、被既有 try/catch 捕获 → `execute()` 返回 `TeamTaskFlowResult{success=false}`（诚实失败结果，不静默成功）。`noBoundMemberFailsFast` 测试相应改写为断言 `assertFalse(result.isSuccess())` + 失败内容诚实。该 throw→return-failed-result 变更显式纳入 Phase 1 scope，不在「零回归」声称范围内。
- **诚实失败（No Silent No-Op #24）**：`NO_SPAWN`/`SPAWN_FAILED`/spawner 抛异常 = 诚实 fail（任务 failed、图短路取消后继、结果如实上报），不静默跳过。
- **端到端验证（Anti-Hollow #22）**：构造团队（声明 memberSpec 但**不 `bindMemberSession`**）+ 多节点依赖 DAG → 注入 functional spawner + mock 成员 agent → `orchestrator.execute(teamId)` → 断言全部任务按依赖顺序 spawn-执行并转 COMPLETED，**全程无任何手动成员绑定**。
- roadmap §4 Layer 4 标注 orchestrator auto-spawn 已落地。

## Non-Goals

- **LLM 直面编排工具（`team-execute-flow`）**：独立 carry-over，依赖本计划 + 调度策略裁定。
- **spawn 后 session 显式绑定到团队**：spawn 的成员 agent 执行经既有引擎路径；若其 `.agent.xml` 声明 `<team-member>`，plan 231 auto-bind 天然生效。本计划不额外手动 bind spawned session。
- **spawn session 复用 / 池化**：本切片按 per-node spawn（每节点一次 spawn 执行），与 daemon per-task spawn 一致；session 复用为优化 successor。
- **多成员 per-task 路由**：orchestrator 当前 `resolveMember` 解析团队级单一成员（claimedBy 优先 → MEMBER role）。spawn 沿用「团队级单一成员」策略（从 memberSpec 解析单一目标，与 `DefaultMemberSpawner.resolveSpawnTarget` 一致）。per-task 多成员路由为 successor。
- **异步/跨进程 spawn**：本切片 spawn 为同步执行（与 `MemberAgentTaskStep` 同步 join、daemon 同步 join 一致）。
- **重试 / 超时 decorator**：spawn 失败诚实 fail（图短路），不内建重试。
- **修改 `IMemberSpawner` 公共契约**：复用原样；如需「只解析目标不执行」的能力，在本计划范围内不复用（orchestrator 侧需要的是运行期执行，spawner 的执行型契约正好适用）。

## Scope

### In Scope

- `io.nop.ai.agent.team.flow` 包：orchestrator 的「无已绑定成员 → 运行期 spawn 执行」路径（修改 `resolveMember` 的 no-bound-member 分支不再 fail-fast；为该分支的节点提供运行期 spawn 执行能力，消费 `SpawnMemberResult` 三态做 complete/fail，镜像 daemon 的 spawn 分支 + 共享 complete/abandon 解释逻辑）
- `TeamTaskFlowOrchestrator`：spawner 字段（构造器/setter 注入，null-safe→NoOp）+ 一个 orchestrator 会话标识（用于 `SpawnMemberRequest.daemonSessionId` 审计元数据与 claim/complete）
- 测试文件：
  - orchestrator spawn focused 测试（bound-member 优先不 spawn / 无 bound + functional spawner = 节点运行期 spawn 执行 / 无 bound + NoOp spawner = 诚实 fail 零回归 / spawn DISPATCHED-non-completed 诚实 fail / SPAWN_FAILED 诚实 fail / spawn 发生在运行期而非构建期——依赖顺序保持）
  - 端到端 orchestrator DAG auto-spawn 测试（多节点依赖 DAG + 不预绑定 + functional spawner → 全部 spawn-执行 COMPLETED）
- 设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-orchestrator-auto-spawn.md`（记录核心裁定：运行期 vs 构建期 spawn、契约复用、wiring 目标、拒绝替代方案）
- `nop-ai-agent-roadmap.md` §4 Layer 4 同步
- 既有 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md` §3/§6 同步（编排器现支持 auto-spawn + 无 bound member 行为变更，消除 owner-doc drift）

### Out Of Scope

- LLM 直面编排工具（Non-Goal）
- spawned session 手动 bind / team-tool 访问保证（Non-Goal）
- spawn session 复用 / 池化（Non-Goal）
- 多成员 per-task 路由（Non-Goal）
- 异步/跨进程 spawn（Non-Goal）
- 重试/超时 decorator（Non-Goal）
- 修改 `IMemberSpawner` 公共契约（Non-Goal）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **spawn 发生在图节点运行期，不在 `resolveMember` 构建期**。`resolveMember` 在图构建期（graph build time）运行，先于图运行。若在 `resolveMember` 内调 spawner 的执行型 `spawnMember`，任务会在构建期、其 `blockedBy` 前驱完成之前被执行，破坏 DAG 依赖顺序。因此 spawn 必须在节点被 nop-task DAG 调度器触发时（运行期）发生。`resolveMember` 的「无已绑定成员」分支不再 fail-fast 抛异常，而是为该节点标记「需 spawn」，真正的 spawn 执行延迟到节点运行期。具体实现（新 step 类、或在 `MemberAgentTaskStep` 增加 spawn 模式、或 step 持有 spawner 引用）属设计文档/源码层面的实现细节，不属本计划。

2. **bound-member 优先，spawn 是 fallback，bound 路径完全不变**。`resolveMember` 的已绑定成员解析（claimedBy 优先 → MEMBER role → 回退任意 bound）**逐行不变**。仅当该解析落到「无已绑定成员」时才进入 spawn 分支。已绑定成员的团队**不 spawn**（直接用 bound session，与 plan 233 零行为差异）。

3. **复用 `IMemberSpawner` 契约原样（不改接口），spawn 节点在运行期调 `spawnMember` 并解释三态**。无已绑定成员的节点在运行期构造 `SpawnMemberRequest(team, task, orchestratorSessionId)` 调 `spawner.spawnMember`（`DefaultMemberSpawner` 会同步执行成员 agent），节点按结果三态处理：`DISPATCHED`→按 `AgentExecutionResult.getStatus()` 决定 complete（`completed`）/ fail（其它）；`NO_SPAWN`/`SPAWN_FAILED`/spawner 抛异常→诚实 fail。与 daemon 共享的**仅是 SpawnMemberResult 三态解释**（DISPATCHED 看执行状态、NO_SPAWN/SPAWN_FAILED 为失败）。
   **失败后的任务状态机遵循 `MemberAgentTaskStep` 模型（非 daemon 的 abandon 模型）**：节点先 `claimTask`（CREATED→CLAIMED）→ spawn 执行 → 成功 `completeTask`（CLAIMED→COMPLETED）；失败（DISPATCHED 非 completed / NO_SPAWN / SPAWN_FAILED / spawner 抛异常）= **抛异常、任务保留在 CLAIMED（不 abandon）**，经 nop-task `GraphTaskStep` 短路取消后继节点，`execute()` 的既有 try/catch 捕获 → 返回 `TeamTaskFlowResult{success=false}`。这与同一 orchestrator 内 bound-member 节点（`MemberAgentTaskStep` 失败 = 抛异常 + 留 CLAIMED，见 `nop-ai-agent-task-flow-integration.md` §4「失败任务保留在 CLAIMED 态、不自动 abandon、重试/恢复为独立 successor」）**一致**，保证 orchestrator 内 bound 与 spawned 两种节点的失败后任务状态统一。daemon 的 abandon（CLAIMED→ABANDONED）是无人值守守护进程的回收模型，**不**适用于程序化一次性 DAG 编排器。

4. **NoOp shipped 默认 = bound 路径零回归；无 bound 路径诚实失败（含有意 API 契约变更）**。`NoOpMemberSpawner`/null 下，无已绑定成员的节点在运行期得到 `NO_SPAWN`→诚实 fail（抛异常、留 CLAIMED、图短路、`execute()` 返回 `TeamTaskFlowResult{success=false}`）。plan 233 baseline 下无已绑定成员 = `execute()` **抛 `NopAiAgentException`**（见 Current Baseline 精确行为：构建期 resolveMember 抛、execute 不捕获、向上传播）。因此「该场景 = 诚实失败」的**业务语义不变**，但**可观测 API 形态有意变更**：throw → return-failed-result（因失败点从构建期 resolveMember 移到运行期 step 内、被既有 try/catch 捕获）。现有 `noBoundMemberFailsFast` 测试断言 `assertThrows(...)`，须改写为断言 `assertFalse(result.isSuccess())` + 失败内容诚实（显式纳入 Phase 1 scope）。bound-member 路径不触发此变更（零回归）。

5. **接线目标 = `TeamTaskFlowOrchestrator`（consumer），wire-at-consumer**。spawner 经 orchestrator 自身的构造器/setter 注入（null-safe→NoOp shipped 默认），镜像 plan 237 决策5 的 daemon 接线惯例与 `IResourceGuard`→TeamManager 范式。orchestrator 另需一个会话标识作为 `SpawnMemberRequest.daemonSessionId`（审计元数据）与该节点 claim/complete 的 session（与 daemon 的 `daemonSessionId` 角色对称）；其取值（如 `"orchestrator-" + teamId` 或生成 id）属实现细节。

### Phase 1 - orchestrator 运行期 spawn 执行路径 + spawner 接线 + focused 测试

Status: completed
Targets: `io.nop.ai.agent.team.flow`（`TeamTaskFlowOrchestrator` 的 no-bound-member 分支 + 运行期 spawn 执行能力 + spawner 注入）、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/TestTeamTaskFlowOrchestratorAutoSpawn.java`（新）

- Item Types: `Fix`（程序化编排器无已绑定成员 = fail-fast 的无人值守缺口）、`Decision`（运行期 vs 构建期 spawn / 契约复用 / bound-priority / NoOp 零回归 / wiring 目标 裁定）、`Proof`

- [x] 修改 `resolveMember` 的「无已绑定成员」分支：不再 fail-fast 抛 `nop.ai.team.flow.no-bound-member`，改为为该节点标记「需 spawn」，spawn 执行延迟到节点运行期（构建期不执行任何 agent）
- [x] 为「需 spawn」节点提供运行期 spawn 执行能力：节点被 DAG 调度器触发时构造 `SpawnMemberRequest(team, task, orchestratorSessionId)` 调 `spawner.spawnMember`，按 `DISPATCHED`/`NO_SPAWN`/`SPAWN_FAILED` 三态处理（镜像 daemon 的 spawn 分支 + complete/abandon 解释），claim→...→complete 状态机语义保持
- [x] `TeamTaskFlowOrchestrator` spawner 接线：构造器/setter 注入 `IMemberSpawner`（null-safe→`NoOpMemberSpawner`），加 orchestrator 会话标识字段
- [x] 诚实失败（No Silent No-Op #24）：`NO_SPAWN`/`SPAWN_FAILED`/spawner 抛异常 = 诚实 fail（任务 failed、图短路、结果如实上报），不静默跳过、不返回 placeholder
- [x] 编写 focused 测试：bound-member 优先（有 bound member 时 spawner 不被调用）/ 无 bound + functional spawner = 节点运行期 spawn 执行（断言 `spawner.spawnMember` 被调用且发生在图运行期而非构建期）/ 无 bound + NoOp spawner = 诚实 fail（断言失败结果，含 throw→return-failed-result 有意变更）/ DISPATCHED 但非 completed = 诚实 fail / SPAWN_FAILED = 诚实 fail
- [x] 改写既有 `TestTeamTaskFlowOrchestrator.noBoundMemberFailsFast`（`:437-454`）：从 `assertThrows(NopAiAgentException.class, () -> orchestrator.execute(...))` 改为断言 `assertFalse(result.isSuccess())` + 失败内容诚实（无已绑定成员的 throw→return-failed-result 是有意 API 契约变更，见决策4；不弱化原断言的「失败」语义，只改失败的可观测形态）
- [x] 改写后保留失败后任务状态断言：无 bound + NoOp/失败路径下失败任务保留在 CLAIMED（非 ABANDONED），与 `MemberAgentTaskStep` 失败模型一致（见决策3）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `resolveMember` 的「无已绑定成员」分支不再在构建期 fail-fast / 不在构建期执行任何 agent（focused 测试断言：图构建阶段 spawner 零调用）
- [x] spawn 执行发生在节点运行期（focused 测试断言：spawner 在图 `execute` 运行后被调用，且被依赖任务在其前驱完成后才 spawn）
- [x] **接线验证**（Minimum Rules #23）：focused 测试断言 functional spawner 的 `spawnMember` 在运行期确实被 orchestrator 节点调用（非仅状态变化）；spawner 经 orchestrator 构造器/setter 注入（wire-at-consumer，不经 engine）
- [x] **无静默跳过**（Minimum Rules #24）：`NO_SPAWN`/`SPAWN_FAILED`/spawner 抛异常 = 显式 fail（有测试覆盖），无空方法体/吞异常/placeholder
- [x] bound-member 优先：有 bound member 时 spawner 不被调用（断言），bound 路径行为与 plan 233 一致
- [x] NoOp 下无 bound = 诚实失败结果（throw→return-failed-result 有意 API 变更已由改写的 `noBoundMemberFailsFast` 覆盖；bound-member 路径零回归）
- [x] 失败后任务状态一致：spawn 节点失败 = 抛异常 + 留 CLAIMED（非 ABANDONED），与 `MemberAgentTaskStep` 失败模型一致（决策3）
- [x] focused 测试全绿
- [x] No owner-doc update required（owner doc 更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 端到端 orchestrator DAG auto-spawn 验证 + 设计文档 + roadmap 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/TestTeamTaskFlowOrchestratorAutoSpawnEndToEnd.java`（新）、`ai-dev/design/nop-ai-agent/nop-ai-agent-orchestrator-auto-spawn.md`（新）、`nop-ai-agent-roadmap.md` §4、`ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`（既有，§3/§6 同步）

- Item Types: `Proof`

- [x] 编写端到端 orchestrator DAG auto-spawn 测试：构造团队（声明 memberSpec 但**不 `bindMemberSession`**）+ 多节点依赖 DAG（含 `blockedBy` 链）→ 注入 functional `DefaultMemberSpawner`（或等价 stub）+ mock 成员 agent（恒 completed）→ `orchestrator.execute(teamId)` → 断言全部任务按依赖顺序 spawn-执行并转 COMPLETED，全程无任何手动成员绑定
- [x] 编写零回归对比测试：同样 DAG + NoOp spawner = 失败结果（无 bound member 且不 spawn，诚实失败）
- [x] 编写 bound-priority e2e：有 bound member + functional spawner = 不 spawn（用 bound session 执行），断言 spawner 未被调用
- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-orchestrator-auto-spawn.md`：记录核心裁定（运行期 vs 构建期 spawn 及其 DAG 顺序理由、契约复用、wiring 目标、NoOp 零回归界定）+ 拒绝替代方案（构建期 spawn / 修改 spawner 契约加 resolve-only 方法 / spawn session 池化 / 异步 spawn）。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：orchestrator auto-spawn 标注已落地
- [x] 同步既有 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`：§3（编排器现支持无 bound member 时 functional spawner 运行期 spawn；无 bound member + NoOp = 返回 `TeamTaskFlowResult{success=false}` 而非抛异常的有意变更）+ §6（auto-spawn successor 项更新：daemon 已落地于 plan 237、orchestrator 已落地于 plan 238）—— 消除 owner-doc 与新 live baseline 的 drift
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：从 `orchestrator.execute(teamId)` → DAG 调度 → 无 bound member 节点 → 运行期 `spawner.spawnMember` → `IAgentEngine.execute` → completeTask → COMPLETED，多节点依赖 DAG 完整跑通且**无任何手动成员绑定**（证明程序化编排器侧无人值守 spawn）
- [x] **DAG 依赖顺序验证**：被依赖任务在其 `blockedBy` 前驱 COMPLETED 之后才 spawn-执行（端到端测试断言执行顺序，证明 spawn 在运行期而非构建期）
- [x] **零回归验证**：NoOp spawner 下既有 orchestrator 测试全绿（bound-member DAG 编排 / 环检测 / synthetic sink 全不变）；无 bound member 测试已按有意 API 变更（throw→return-failed-result）改写，仍断言「失败」语义成立
- [x] **bound-priority 验证**：有 bound member 时 spawner 不被调用（断言），直接用 bound session 执行
- [x] **Anti-Hollow 断言**：端到端测试断言 orchestrator 节点经 spawner → `IAgentEngine.execute` 真实调用（非仅状态变化），spawn 使用的 agentModel 来自 `TeamMemberSpec`（非硬编码）
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言 orchestrator 确实咨询注入的 spawner（functional 路径 spawn 执行 / NoOp 路径诚实 fail 零回归），spawner 经 orchestrator 构造器/setter 注入
- [x] `nop-ai-agent-orchestrator-auto-spawn.md` 存在，含核心裁定 + 拒绝替代方案，无类签名/代码
- [x] roadmap §4 已更新（orchestrator auto-spawn 已落地）
- [x] 既有 `nop-ai-agent-task-flow-integration.md` §3/§6 已同步（编排器 auto-spawn 已落地、无 bound member 行为变更），无 owner-doc drift
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] orchestrator 运行期 spawn 执行路径落地为真实（非空壳）代码，复用 plan 237 spawner 契约原样（不改接口）
- [x] spawn 在节点运行期发生（不在构建期），DAG 依赖顺序保持
- [x] bound-member 优先路径完全不变（plan 233 baseline 零行为差异）
- [x] NoOp shipped 默认：bound-member 路径零回归 + 无 bound member 诚实失败（throw→return-failed-result 有意 API 变更已显式纳入 scope 并由改写的 `noBoundMemberFailsFast` 覆盖）
- [x] 端到端 orchestrator DAG auto-spawn 验证（无手动绑定，依赖顺序保持，全部 COMPLETED）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（LLM 直面工具 / spawn session 复用 / 多成员路由 / 异步 spawn / decorator 均为显式 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（新 design doc + roadmap §4 + 既有 `nop-ai-agent-task-flow-integration.md` §3/§6）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）orchestrator 节点运行时确实调用 spawner + spawner 确实调用 `IAgentEngine.execute`（不只是类型存在），（b）端到端 DAG auto-spawn 路径完整连通且依赖顺序正确，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；LLM 直面编排工具 / spawned session 手动 bind / spawn session 复用池化 / 多成员 per-task 路由 / 异步跨进程 spawn / 重试超时 decorator 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **LLM 直面编排工具（`team-execute-flow`）**：Classification: successor plan required（依赖本计划 + 调度策略裁定）。本计划闭合 orchestrator 侧 auto-spawn 后，该工具的消费路径（程序化编排 + auto-spawn）就绪。
- **spawn session 复用 / 池化**：当前 per-node spawn；session 复用为优化 successor（与 daemon 侧同名 successor 对称）。
- **多成员 per-task 路由**：orchestrator 当前团队级单一成员策略；per-task 路由为 successor。
- **异步/跨进程 spawn 协调**：本切片同步 spawn；异步为 successor。

## Closure

Status Note: orchestrator 运行期 spawn 执行路径落地为真实代码，复用 plan 237 spawner 契约原样（不改接口）。spawn 在节点运行期发生（不在构建期），DAG 依赖顺序经反向插入序测试证明保持。bound-member 优先路径逐行不变（plan 233 baseline 零回归）。NoOp shipped 默认下无 bound member 诚实失败（throw→return-failed-result 有意 API 变更已显式纳入 scope 并由改写的 `noBoundMemberFailsFast` 覆盖）。端到端 orchestrator DAG auto-spawn 验证（无手动绑定、依赖顺序保持、全部 COMPLETED）经线性 + 菱形 e2e 测试证明。受影响 owner docs（新 design doc + roadmap §4 + 既有 task-flow-integration.md §2 决策4/§4/§6）已同步。无被静默降级的 in-scope live defect。独立 closure-audit 子 agent（fresh session）已 approve。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（read-only live-code verification，fresh session，task id `ses_12945f485ffeuKgT09Hz2dhq25`）
- Audit Session: `ses_12945f485ffeuKgT09Hz2dhq25`
- Evidence:
  - Phase 1 Exit Criteria（10/10 PASS）：`resolveMember` 无 bound member 分支返回 null（`TeamTaskFlowOrchestrator.java:365-369`）非 fail-fast；`SpawnMemberAgentTaskStep.execute()` 在运行期调 `spawnMember`（`:141`）；接线 #23（`spawnerWiredViaConstructorAndSetterWireAtConsumer` + `noBoundMemberFunctionalSpawnerSpawnsAtRunTime` 断言 invocations==1 + engine.capturedRequests==1）；无静默跳过 #24（`SpawnMemberAgentTaskStep.java:166-189` 四种失败态均显式抛异常 + spawner-throws 防御）；bound-member 优先（`boundMemberPrioritySpawnerNotConsulted` invocations==0）；NoOp 无 bound 诚实失败（`noBoundMemberFailsFast` 改写 assertFalse + CLAIMED）；失败后状态 CLAIMED 非 ABANDONED（step 从不调 abandonTask）；focused 测试全绿。
  - Phase 2 Exit Criteria（12/12 PASS）：端到端 #22（`linearDagAutoSpawnCompletesNoManualBind` 3×COMPLETED + 无 bindMemberSession）；DAG 依赖序（`completedBeforeStart.get(b).contains(a)` + 菱形 D 晚于 B/C）；零回归（8 个既有 orchestrator 测试全绿）；bound-priority e2e；Anti-Hollow（engine.capturedRequests==3 + agentModel 非硬编码）；接线 #23；design doc 存在无代码；roadmap §4 `L4-orchestrator-auto-spawn-integration` ✅（`:261`）；task-flow-integration.md §2 决策4/§4/§6 已同步；`./mvnw test -pl nop-ai/nop-ai-agent -am` BUILD SUCCESS；`check-doc-links.mjs --strict` EXIT_CODE=0（plan 238 文件零新增警告）。
  - Closure Gates（12/12 PASS）：全部满足。
  - **Anti-Hollow 检查** PASS：运行时调用链 `orchestrator.execute → build loop 选择 SpawnMemberAgentTaskStep → task.execute.syncGetOutputs (nop-task DAG scheduler) → SpawnMemberAgentTaskStep.execute → claimTask → memberSpawner.spawnMember → DefaultMemberSpawner → IAgentEngine.execute().join() → completeTask` 完整连通、被 e2e 测试实际驱动（非仅类型存在）。无空方法体/静默跳过/no-op 作为正常实现——每个失败分支（NO_SPAWN/SPAWN_FAILED/非 completed/spawner-throws/null-result/claim-CAS-loss/complete-CAS-loss）均显式抛 `NopAiAgentException` + `recorder.markFailed`。`scan-hollow-implementations` 未对本计划新增组件报 high/critical 发现。
  - **运行期 vs 构建期 spawn 证明**：focused 测试 `spawnHappensAtRunTimeNotBuildTimeDependencyOrderPreserved` 把任务按反向插入序 [C,B,A] 存入 store（构建 for-loop 按此序迭代），断言 spawn 序按依赖序 [A,B,C] + B spawn 严格晚于 A 完成——证明 DAG 调度器（运行期）驱动 spawn，非 build loop（构建期）。
  - Deferred 项分类检查：`Deferred But Adjudicated` 为空（LLM 直面工具 / spawn session 复用 / 多成员路由 / 异步 spawn / decorator 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/238-nop-ai-agent-orchestrator-auto-spawn.md --strict` 退出码为 0（确认无未勾选项 + Closure Evidence 已写入）。
  - 审计发现（cosmetic，非阻塞）：plan/log 自引用写「§3 decision 4」但 task-flow-integration.md 的 decision 4 实际位于 §2 设计决策（§3 是合成 sink）；实质内容（§2 决策4 + §4 失败传播 + §6 successor）已正确同步，无 owner-doc drift。不影响 closure。

Follow-up:

- LLM 直面编排工具（successor plan required，依赖本计划 + 调度策略裁定）
- spawn session 复用 / 池化（优化 successor）
- 多成员 per-task 路由（successor）
- 异步/跨进程 spawn（successor）

## Follow-up handled by 239-nop-ai-agent-team-execute-flow-tool.md

LLM 直面编排工具（`team-execute-flow`）的 follow-up 已由 `ai-dev/plans/239-nop-ai-agent-team-execute-flow-tool.md` 接手。该计划交付 `IToolExecutor` 实现（`team-execute-flow`），消费本计划交付的 `TeamTaskFlowOrchestrator.execute(teamId)` + auto-spawn 能力，经调度策略裁定（同步执行）+ 工具接线（wire-at-consumer spawner 注入）闭合 Layer 4 顶部 LLM 入口。
