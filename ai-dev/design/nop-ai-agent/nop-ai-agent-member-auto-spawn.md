# 团队任务 auto-spawn 成员 agent 设计（无人值守下未绑定成员自动 spawn 执行，闭合无人值守多 Agent 编排最后缺口）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/237-nop-ai-agent-member-auto-spawn.md`（Work Item: `L4-auto-spawn-member-agent`）
> Related: `nop-ai-agent-task-scheduler-daemon.md`（plan 236 交付 daemon 调度——本设计在其 dispatch 路径叠加 auto-spawn fallback）、`nop-ai-agent-task-flow-integration.md`（plan 233 交付同步编排 + `TeamTaskTopology`——本设计消费同一拓扑/就绪查询）、`nop-ai-agent-roadmap.md` §4 Layer 4（`L4-auto-spawn-member-agent` carry-over）

## 1. 定位

把 nop-ai-agent 团队任务调度从「守护进程（plan 236）只能派发任务给**已绑定**成员 agent；未绑定成员的团队 = 任务被 abandon 快速失败」扩展为「当团队没有已绑定成员时，守护进程自动 **spawn**（启动新执行）成员 agent 来执行任务，基于团队声明式成员规格（`TeamMemberSpec.agentModel`）确定 spawn 目标」。

这是闭合 roadmap §4 Layer 4「无人值守多 Agent 自主编排」链路的**最后关键缺口**：plan 236 已交付自动调度触发，但仅消费已绑定成员；本设计交付「成员自动 spawn」，使无人值守部署中**无需预先启动/绑定所有成员 agent**，任务到达时自动 spawn 对应成员执行。

声明式团队绑定（plan 231）、团队 ACL / 配额 / DB 持久化 / 多租户隔离（plans 228/234/230/232）、blockedBy 自动调度守护进程（plan 236）、同步编排器（plan 233）在本设计之前已落地。本设计在其之上叠加 spawn fallback，**不改既有契约**（spawner 是新扩展点，daemon 是新增 dispatch 分支，不改 `IAgentEngine` / `ITeamTaskStore` / `ITeamManager` / `TeamTaskTopology` / `IScheduledExecutor` 任何契约）。

## 2. 设计决策

### 决策 1：spawn = 可插拔扩展点，NoOp shipped 默认 = 零回归

新增成员 spawn 接口（`IMemberSpawner`），shipped 默认 NoOp 实现（`NoOpMemberSpawner`，恒返回 `SpawnMemberResult.NO_SPAWN`，不 spawn）。daemon 在 `resolveBoundMember` 返回 null 时咨询 spawner：NoOp = 当前行为（abandon，零回归）；functional = spawn 后委派。

理由：
- 镜像本模块所有 Layer 4 扩展点范式（`ITeamAclChecker` / `IResourceGuard` / `IFencingTokenService` / `ITeamTaskSchedulerDaemon` 自身）。
- NoOp shipped 默认保证既有 daemon 测试零回归（23 个 plan 236 测试 + 6 个 plan 236 e2e 测试全绿）。
- 部署层 opt-in（功能性 spawner 经 setter/构造器注入 daemon）。

spawner 需要 `IAgentEngine` 依赖来 spawn（构造注入到 `DefaultMemberSpawner`，daemon 不需要再传 engine 给 spawner——spawner 自己持有 engine 引用）。

### 决策 2：spawn 目标 = `TeamMemberSpec.agentModel`，团队级单一成员策略

spawn 目标从团队声明式成员规格解析：优先 MEMBER role 的 memberSpec（与 daemon `resolveBoundMember` bound-member 策略对称），回退任意 memberSpec。`TeamMemberSpec.agentModel`（构造期 `Objects.requireNonNull`，non-null）提供 spawn 的 agent 配置名。

daemon 当前 `resolveBoundMember` 解析团队级单一 bound member（非 per-task 路由），spawn 沿用同一「团队级单一成员」策略（从 spec 解析而非从 bound roster）。**无 memberSpec 的团队 = 无法 spawn = 诚实 `NO_SPAWN`**（daemon abandon）。这是与「spawner 是 fallback，不是魔法」的诚实契约：spawner 只能 spawn 声明过的成员，不能凭空创造。

### 决策 3：bound-member 优先，spawn 是 fallback

daemon dispatch 路径：先 `resolveBoundMember`（bound session 优先）；**仅当返回 null 时**咨询 spawner。已绑定成员的团队**不 spawn**（直接用 bound session，零行为变化）。这保证既有 bound-member dispatch 语义完全不变，spawn 仅填补「无 bound member」缺口。

**反例测试（bound-priority）**：有 bound member + functional spawner = spawner 不被调用（spawner 计数器 = 0），dispatch 用 bound session（无 `spawned-` 前缀）。这是决策 3 的端到端证据。

### 决策 4：spawn 语义 = 同步执行 + 任务作输入 + 结果映射完成/失败

functional spawner 创建 `AgentMessageRequest`（agentModel 来自 memberSpec + 任务 prompt + 新 session `"spawned-"+UUID`，per-task spawn 非复用）经 `IAgentEngine.execute` 同步执行（与 bound-member dispatch 同一 `execute(request).join()` 语义）。

**关键统一**：spawner 不解释 `AgentExecutionResult.getStatus()`——只区分「拿到结果」（`DISPATCHED` 携带 result）vs「执行抛异常」（`SPAWN_FAILED`）。daemon 收到 `DISPATCHED` 后用自己的 `completeOrAbandonAfterExecution` 统一逻辑（与 bound-member path 共享同一方法）决定 complete / abandon。这保证 spawned 路径与 bound 路径的 complete/abandon 语义**字面上同一段代码**，不存在「spawned 失败语义不同于 bound 失败语义」的歧义。

spawn 失败（agent 抛异常 → SPAWN_FAILED / 非 completed 终态 → DISPATCHED 后 daemon abandon / completeTask CAS 失败 → daemon abandon）= 诚实 abandon（与 bound-member dispatch 失败同一 `DISPATCH_FAILED` 语义）。无 memberSpec / agentModel 解析失败 = `NO_SPAWN` → daemon `UNBOUND_MEMBER` abandon（诚实）。

### 决策 5：接线目标 = `TeamTaskSchedulerDaemon`（consumer），非 `DefaultAgentEngine`

spawner 的真正消费方是 daemon 的 dispatch 路径（`dispatchClaimedTask` 在 `member == null` 时咨询 spawner），而非引擎本身。`DefaultAgentEngine` 与 daemon 之间经 `ITeamTaskSchedulerDaemon` **接口**解耦（`setTeamTaskSchedulerDaemon` 持有接口，不是具体类），引擎无法也不应向 `NoOpTeamTaskSchedulerDaemon` 传播 spawner。

因此 spawner 经 **daemon 自身的 setter 或构造器**注入（null-safe → NoOp shipped 默认），镜像 `IResourceGuard`→`InMemoryTeamManager` 的 wire-at-consumer 惯例（plan 234）。

理由：
- 消费方即注入方，职责一致（与 `IResourceGuard`/`ITeamAclChecker` 范式对齐）。
- 不扩宽 `ITeamTaskSchedulerDaemon` 公共接口（spawner 是具体类 `TeamTaskSchedulerDaemon` 的注入，非接口契约）。
- NoOp 默认在 daemon 内部 fallback（field 初始 = NoOp，setter/构造器 null → NoOp），既有 daemon 构造/测试零回归。
- e2e 测试构造 daemon 时传入 functional spawner 即可，无需经引擎中转。

## 3. 失败语义（诚实契约，No Silent No-Op #24）

| 场景 | spawner 返回 | daemon 行为 | outcome |
|------|--------------|-------------|---------|
| 有 bound member | （不咨询） | dispatch bound session | COMPLETED / DISPATCH_FAILED |
| 无 bound member + functional spawner + execution 成功 | DISPATCHED(completed) | completeTask | COMPLETED |
| 无 bound member + functional spawner + execution 非 completed | DISPATCHED(non-completed) | abandonTask | DISPATCH_FAILED |
| 无 bound member + functional spawner + execution 抛异常 | SPAWN_FAILED | abandonTask | DISPATCH_FAILED |
| 无 bound member + functional spawner + 无 memberSpec | NO_SPAWN | abandonTask | UNBOUND_MEMBER |
| 无 bound member + NoOp spawner shipped 默认 | NO_SPAWN | abandonTask | UNBOUND_MEMBER（零回归） |
| 无 bound member + spawner 抛异常（contract violation） | （不返回） | abandonTask | DISPATCH_FAILED |

`UNBOUND_MEMBER` 不计入 `dispatchedTasks`（execute 未调用），`DISPATCH_FAILED` 计入（execute 被调用过）。计数语义与 plan 236 一致，spawner 接入不破坏。

## 4. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| 把 spawner 接入 `TeamTaskFlowOrchestrator`（plan 233 同步编排器） | orchestrator 是程序化入口（调用者可预绑定成员），非无人值守路径。本设计只集成 daemon。orchestrator 集成为独立 successor（spawner 扩展点已就绪，只需另一处 wiring）。 |
| 把 spawner 经 `DefaultAgentEngine` 中转注入（顶层接线） | 引擎与 daemon 经接口解耦，引擎无法向 NoOp 默认 daemon 传播 spawner。且 spawner 的唯一 consumer 是 daemon dispatch 路径，wire-at-consumer 更直接。镜像 `IResourceGuard`→TeamManager 范式。 |
| spawn 后显式 bind spawned session 到团队 | spawned 成员 agent 执行经既有引擎路径；若其 `.agent.xml` 声明 `<team-member>`，plan 231 的 auto-bind 天然生效。本设计不额外手动 bind（team-tool 访问为天然副产品，非显式目标）。 |
| spawn-per-task vs spawn-once-reuse 策略裁定 | 本设计按 daemon 现有 per-task dispatch 模型（每任务一次 spawn 执行，新 session `"spawned-"+UUID`）。session 复用 / spawn 池化为优化 follow-up。 |
| 多成员路由（per-task 选 spawn 目标） | daemon 当前团队级单一成员策略，spawn 沿用同一策略（优先 MEMBER role 从 spec 解析）。per-task 成员路由为 successor。 |
| 异步/跨进程 spawn 协调 | 本设计 spawn 为同步执行（与 daemon bound-member dispatch 一致）。异步为 successor。 |
| 内建重试 / 超时 decorator | spawn 失败诚实 abandon（供 task-reclaim successor 消费），不内建重试。与 plan 236 daemon 失败语义一致。 |

## 5. 边界（Non-Goals，均为独立 successor）

- **orchestrator（plan 233 `TeamTaskFlowOrchestrator`）auto-spawn 集成**：spawner 扩展点就绪后，只需另一处 wiring（orchestrator member resolution 失败时咨询 spawner）。Classification: successor plan required（依赖本计划的 spawner 契约）。
- ~~**LLM 直面编排工具（`team-execute-flow`）**~~ **已落地（plan 239 / `L4-team-execute-flow-llm-tool`，详见 `nop-ai-agent-team-execute-flow.md`）**。
- **spawn session 复用 / 池化**：当前 per-task spawn；session 复用为优化 successor。
- **spawn 后 session 显式绑定到团队**：天然副产品，非显式目标。
- **多成员 per-task 路由**：daemon 当前团队级单一成员策略；per-task 路由为 successor。
- **异步/跨进程 spawn 协调**：本切片同步 spawn；异步为 successor。
- **重试 / 超时 decorator**：spawn 失败诚实 abandon，不内建重试。

## 6. 落地证据

- 新组件：`io.nop.ai.agent.team.IMemberSpawner`（接口）+ `NoOpMemberSpawner`（singleton shipped 默认零回归）+ `DefaultMemberSpawner`（functional 实现）；`io.nop.ai.agent.team.scheduler.SpawnMemberRequest` / `SpawnMemberResult`（不可变输入/输出 + `Status` 枚举 DISPATCHED/NO_SPAWN/SPAWN_FAILED）。
- daemon 集成：`TeamTaskSchedulerDaemon.memberSpawner` 字段（默认 `NoOpMemberSpawner.noOp()`）+ `setMemberSpawner`/`getMemberSpawner`（null-safe 回退 NoOp）+ spawner-aware 8-arg 构造器 + `dispatchClaimedTask` 在 `member == null` 时咨询 spawner + 提取 `completeOrAbandonAfterExecution` 共享 bound/spawned 路径 complete/abandon 逻辑（决策 4）。
- 端到端无人值守 spawn 验证：声明团队（声明 memberSpec 但**不 `bindMemberSession`**）+ 创建任务 → daemon.start → 周期扫描（manual tick 模拟）→ CAS claim → 无 bound member → spawner.spawnMember → `IAgentEngine.execute` → completeTask → 任务 COMPLETED。完整路径跑通且**无任何手动成员绑定**。线性 A→B→C + 菱形 A→{B,C}→D 均验证（含 Anti-Hollow 执行序：B spawn 严格晚于 A 完成，C spawn 严格晚于 B 完成）。
- 零回归对比验证：同样场景 + NoOp spawner = 任务 abandon（daemon 既有行为不变，23 plan 236 focused + 6 plan 236 e2e + 18 本计划集成 focused + 6 本计划 e2e 全绿）。
- bound-priority 验证：有 bound member + functional spawner = spawner 不被调用（计数器 = 0），dispatch 用 bound session（无 `spawned-` 前缀），与既有 bound-member path 字面同一段代码。
- Anti-Hollow 断言：spawned execution 携带 teamTaskId/teamId/daemonSessionId/spawnedFromMemberSpec 元数据（审计可追溯）；spawned agentName 来自 `TeamMemberSpec.agentModel`（断言 "very-distinctive-member-agent-model-xyz"，证明非硬编码）；每次 spawn 新 session（per-task 非复用）。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿。
