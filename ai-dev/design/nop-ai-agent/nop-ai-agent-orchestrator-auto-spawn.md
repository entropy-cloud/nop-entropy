# 程序化编排器 auto-spawn 集成设计（TeamTaskFlowOrchestrator 在图运行期 spawn 未绑定成员的 DAG 节点）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/238-nop-ai-agent-orchestrator-auto-spawn.md`（Work Item: `L4-orchestrator-auto-spawn-integration`）
> Related: `nop-ai-agent-member-auto-spawn.md`（plan 237 交付 `IMemberSpawner`/`NoOpMemberSpawner`/`DefaultMemberSpawner` 契约 + daemon 集成——本设计复用其 spawner 契约，在 orchestrator 侧再做一处运行期 spawn 执行路径）、`nop-ai-agent-task-flow-integration.md`（plan 233 交付 `TeamTaskFlowOrchestrator` + `MemberAgentTaskStep`——本设计修改其「未绑定成员 = 构建期 fail-fast」为「未绑定成员 + functional spawner = 运行期 spawn 执行」）、`nop-ai-agent-roadmap.md` §4 Layer 4（orchestrator auto-spawn successor）

## 1. 定位

把 nop-ai-agent 的程序化 DAG 编排器 `TeamTaskFlowOrchestrator`（plan 233）从「每个 team task 节点必须解析到一个**已绑定**成员 session，否则 `resolveMember` 在图**构建期** fail-fast 抛 `nop.ai.team.flow.no-bound-member`」扩展为「当某节点无已绑定成员时，在**图运行期**（该节点被 nop-task DAG 调度器触发时）经 plan 237 交付的 `IMemberSpawner` spawn 一个成员 agent 执行该任务」。

plan 237 已把 spawner 接入**守护进程**（`TeamTaskSchedulerDaemon.dispatchClaimedTask`，无人值守路径）；本设计把同一 spawner 契约接入**程序化编排器**（`TeamTaskFlowOrchestrator`，调用方可预绑定成员的程序化路径），闭合 roadmap §4 Layer 4「编排器侧无人值守」的对应缺口。

声明式团队绑定（plan 231）、团队 ACL / 配额 / DB 持久化（plans 228/234/230）、blockedBy 自动调度守护进程（plan 236）、同步编排器（plan 233）、成员 auto-spawn 扩展点（plan 237）在本设计之前已落地。本设计在其之上叠加 orchestrator 侧运行期 spawn，**不改 `IMemberSpawner` 公共契约**（复用原样），**不改 bound-member 解析路径**（逐行不变，零回归）。

## 2. 设计决策

### 决策 1：spawn 发生在图节点运行期，不在 `resolveMember` 构建期（核心约束）

daemon 在自己的 dispatch 循环里**内联**调 spawner（spawner 执行，daemon 解释结果）；orchestrator 则**把执行委托给图节点 step**（step 在图**运行期**才执行），而 `resolveMember` 在图**构建期**（graph build time）运行、在图运行之前就完成。若在 `resolveMember` 内调 spawner 的执行型 `spawnMember`，任务会在构建期、其 `blockedBy` 前驱完成之前被执行，**破坏 DAG 依赖顺序**（一个 `blockedBy` 另一任务的任务会在其前驱完成前被执行）。

因此 spawn 必须在节点被 nop-task DAG 调度器触发时（运行期）发生。实现：`resolveMember` 的「无已绑定成员」分支不再 fail-fast 抛异常，而是**返回 null**（构建期不执行任何 agent）；构建期为该节点选择新 step（运行期 spawn 执行型），真正的 spawn 执行延迟到节点运行期（该 step 的 `execute` 在 DAG 调度器触发时调 spawner）。这样 spawn 自然发生在依赖序正确的时间点：被依赖的任务仍在其前驱完成后才 spawn-执行。

### 决策 2：bound-member 优先，spawn 是 fallback，bound 路径逐行不变

`resolveMember` 的已绑定成员解析（claimedBy 优先 → MEMBER role → 回退任意 bound）**逐行不变**。仅当该解析落到「无已绑定成员」时（返回 null）才进入 spawn 分支。已绑定成员的团队**不 spawn**（直接用 bound session，与 plan 233 零行为差异）。spawner 仅在「无已绑定成员」分支被咨询。

### 决策 3：复用 `IMemberSpawner` 契约原样（不改接口），spawn 节点在运行期调 `spawnMember` 并解释三态，失败遵循 `MemberAgentTaskStep` 模型（非 daemon abandon）

无已绑定成员的节点在运行期构造 `SpawnMemberRequest(team, task, orchestratorSessionId)` 调 `spawner.spawnMember`（`DefaultMemberSpawner` 同步执行成员 agent），节点按结果三态处理，与 daemon 共享的**仅是 SpawnMemberResult 三态解释**（DISPATCHED 看执行状态、NO_SPAWN/SPAWN_FAILED 为失败）。

**失败后的任务状态机遵循 `MemberAgentTaskStep` 模型（非 daemon 的 abandon 模型）**：节点先 `claimTask`（CREATED→CLAIMED，用 orchestrator session id）→ spawn 执行 → 成功 `completeTask`（CLAIMED→COMPLETED）；失败（DISPATCHED 非 completed / NO_SPAWN / SPAWN_FAILED / spawner 抛异常）= **抛异常、任务保留在 CLAIMED（不 abandon）**，经 nop-task `GraphTaskStep` 短路取消后继节点，`execute()` 的既有 try/catch 捕获 → 返回 `TeamTaskFlowResult{success=false}`。这与同一 orchestrator 内 bound-member 节点（`MemberAgentTaskStep` 失败 = 抛异常 + 留 CLAIMED）**一致**，保证 orchestrator 内 bound 与 spawned 两种节点的失败后任务状态统一。daemon 的 abandon（CLAIMED→ABANDONED）是无人值守守护进程的回收模型，**不**适用于程序化一次性 DAG 编排器。

### 决策 4：NoOp shipped 默认 = bound 路径零回归；无 bound 路径诚实失败（含有意 API 契约变更）

`NoOpMemberSpawner`/null 下，无已绑定成员的节点在运行期得到 `NO_SPAWN`→诚实 fail（抛异常、留 CLAIMED、图短路、`execute()` 返回 `TeamTaskFlowResult{success=false}`）。plan 233 baseline 下无已绑定成员 = `execute()` **抛 `NopAiAgentException`**（构建期 resolveMember 抛、execute 不捕获、向上传播）。因此「该场景 = 诚实失败」的**业务语义不变**，但**可观测 API 形态有意变更**：throw → return-failed-result（因失败点从构建期 resolveMember 移到运行期 step 内、被既有 try/catch 捕获）。bound-member 路径不触发此变更（零回归）。

### 决策 5：接线目标 = `TeamTaskFlowOrchestrator`（consumer），wire-at-consumer

spawner 经 orchestrator 自身的构造器/setter 注入（null-safe→NoOp shipped 默认），镜像 plan 237 决策5 的 daemon 接线惯例与 `IResourceGuard`→TeamManager 范式。orchestrator 另需一个会话标识（`"orchestrator-" + teamId`）作为 `SpawnMemberRequest.daemonSessionId`（审计元数据）与该节点 claim/complete 的 session（与 daemon 的 `daemonSessionId` 角色对称）。

## 3. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| 把 spawner 的执行型 `spawnMember` 放进 `resolveMember`（构建期 spawn） | `resolveMember` 在图构建期运行、先于图运行。构建期 spawn 会在被依赖任务的前驱完成之前执行任务，破坏 DAG 依赖顺序。spawn 必须延迟到节点运行期。 |
| 修改 `IMemberSpawner` 公共契约加「只解析目标不执行」的 resolve-only 方法 | orchestrator 侧需要的是运行期执行，spawner 的执行型契约正好适用。复用原样不改接口，避免为单一 consumer 扩宽公共契约。 |
| spawn session 复用 / 池化 | 本切片按 per-node spawn（每节点一次 spawn 执行，新 session `"spawned-"+UUID`），与 daemon per-task spawn 一致。session 复用为优化 successor。 |
| 异步 / 跨进程 spawn 协调 | 本切片 spawn 为同步执行（与 `MemberAgentTaskStep` 同步 join、daemon 同步 join 一致）。异步为 successor。 |
| 失败后 abandon（CLAIMED→ABANDONED，复用 daemon 模型） | abandon 是无人值守守护进程的回收模型。程序化一次性 DAG 编排器的失败应遵循 `MemberAgentTaskStep` 模型（抛异常 + 留 CLAIMED），保证 orchestrator 内 bound 与 spawned 节点失败后任务状态统一。 |

## 4. 边界（Non-Goals，均为独立 successor）

- ~~**LLM 直面编排工具（`team-execute-flow`）**~~ **已落地（plan 239 / `L4-team-execute-flow-llm-tool`，详见 `nop-ai-agent-team-execute-flow.md`）**。本设计闭合 orchestrator 侧 auto-spawn 后，该工具已消费程序化编排 + auto-spawn 能力，LLM 经工具调用进入编排链路。
- **spawn session 复用 / 池化**：当前 per-node spawn；session 复用为优化 successor（与 daemon 侧同名 successor 对称）。
- **多成员 per-task 路由**：orchestrator 当前团队级单一成员策略；per-task 路由为 successor。
- **异步/跨进程 spawn 协调**：本切片同步 spawn；异步为 successor。
- **重试 / 超时 decorator**：spawn 失败诚实 fail（图短路），不内建重试。
- **修改 `IMemberSpawner` 公共契约**：复用原样。

## 5. 落地证据

- 新组件：`io.nop.ai.agent.team.flow.SpawnMemberAgentTaskStep`（节点运行期 spawn 执行型 step：claim → spawnMember → 三态解释 → complete/throw，失败留 CLAIMED 非 abandon，决策3）。
- orchestrator 集成（`TeamTaskFlowOrchestrator`）：新增 `memberSpawner` 字段（默认 `NoOpMemberSpawner.noOp()`，零回归）+ `setMemberSpawner`/`getMemberSpawner`（null-safe 回退 NoOp）+ spawner-aware 5-arg 构造器（wire-at-consumer 决策5）+ `orchestratorSessionId = "orchestrator-" + teamId`。`resolveMember` 的「无已绑定成员」分支从 fail-fast 抛异常改为**返回 null**（bound-member 解析路径逐行不变，决策2 零回归）；构建 for-loop 据 member 是否 null 选择 `MemberAgentTaskStep`（bound）或 `SpawnMemberAgentTaskStep`（unbound）。
- 端到端 orchestrator DAG auto-spawn 验证：声明团队（声明 memberSpec 但**不 `bindMemberSession`**）+ 多节点依赖 DAG（线性 A→B→C + 菱形 A→{B,C}→D）→ 注入 functional `DefaultMemberSpawner` + mock 成员 agent → `orchestrator.execute(teamId)` → 全部任务按依赖顺序 spawn-执行并转 COMPLETED，**全程无任何手动成员绑定**。Anti-Hollow 执行序证据：B spawn 严格晚于 A 完成，C spawn 严格晚于 B 完成，D spawn 晚于 B 和 C 完成。
- 运行期 vs 构建期 spawn 证据：focused 测试把任务按反向插入序 [C,B,A] 存入 store（构建 for-loop 按此序迭代），但 spawn 序按依赖序 [A,B,C]——证明 DAG 调度器驱动 spawn 非 build loop（决策1）。
- 零回归对比验证：同样 DAG + NoOp spawner = 失败结果（无 bound member 且不 spawn，诚实失败，业务语义不变）。
- bound-priority 验证：有 bound member + functional spawner = spawner 不被调用（计数器 = 0），dispatch 用 bound session（无 `spawned-` 前缀）。
- 有意 API 契约变更（决策4）：无已绑定成员的失败形态从 pre-238 构建期 `execute()` 抛 `NopAiAgentException` 变为运行期 `execute()` 返回 `TeamTaskFlowResult{success=false}`；`noBoundMemberFailsFast` 测试相应改写（仍断言「失败」语义成立 + 失败任务留 CLAIMED）。
- Anti-Hollow 断言：spawned execution 携带 teamTaskId/teamId/daemonSessionId/spawnedFromMemberSpec 元数据（审计可追溯）；spawned agentName 来自 `TeamMemberSpec.agentModel`（断言非硬编码）；每次 spawn 新 session（per-node 非复用）。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（2545 tests，零回归）。
