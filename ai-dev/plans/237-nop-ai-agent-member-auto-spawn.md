# 237 nop-ai-agent 声明式 auto-spawn 成员 agent（未绑定成员时自动 spawn 执行，闭合无人值守编排最后缺口）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-auto-spawn-member-agent

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/236-nop-ai-agent-task-scheduler-daemon.md`（Non-Goals「auto-spawn 成员 agent」+ Non-Blocking Follow-ups「auto-spawn 成员 agent（独立 carry-over `L4-auto-spawn-member-agent`）：声明式自动启动/调度成员执行。Classification: successor plan required（本计划解除其调度前置阻塞）」）；同一 carry-over 在 plans 233、232、234 中亦显式延期；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（`L4-auto-spawn-member-agent` carry-over）；`nop-ai-agent-task-scheduler-daemon.md` §5（auto-spawn successor）；`nop-ai-agent-task-flow-integration.md` 决策4 + §6（auto-spawn successor）
> Related: `236`（交付 `ITeamTaskSchedulerDaemon` 定时扫描自动 claim/派发——本计划消费其调度基础设施，在其 dispatch 路径叠加 auto-spawn）、`231`（交付声明式团队自动绑定 `<team-member>` + 引擎三入口点 auto-bind——本计划消费其 `TeamMemberSpec.agentModel` 字段作 spawn 目标）、`233`（交付 `TeamTaskFlowOrchestrator` + `MemberAgentTaskStep` 同步编排——同样的消费已绑定成员 fail-fast 模式，orchestrator auto-spawn 为 follow-up）、`225`/`227`（`ITeamTaskStore` CAS claim/complete 契约——本计划消费）

## Purpose

把 nop-ai-agent 团队任务调度从"守护进程（plan 236）只能派发任务给**已绑定**成员 agent；未绑定成员的团队 = 任务被 abandon 快速失败"扩展为"当团队没有已绑定成员时，守护进程自动 **spawn**（启动新执行）成员 agent 来执行任务，基于团队声明式成员规格（`TeamMemberSpec.agentModel`）确定 spawn 目标"。这是闭合 roadmap §4 Layer 4「无人值守多 Agent 自主编排」链路的最后关键缺口：plan 236 已交付自动调度触发，但仅消费已绑定成员；本计划交付"成员自动 spawn"，使无人值守部署中**无需预先启动/绑定所有成员 agent**，任务到达时自动 spawn 对应成员执行。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **守护进程已落地（plan 236 ✅）**：`TeamTaskSchedulerDaemon.scanOnce()` 每周期经 `TeamTaskTopology.getReadyTasks()` 解析就绪 CREATED 任务 → CAS claim → **消费已绑定成员**（`resolveBoundMember` 经 `team.getMembers()` 查找 bound member，优先 MEMBER role 回退任意 bound）→ `IAgentEngine.execute` 同步委派 → completeTask/abandonTask。未绑定成员 = `resolveBoundMember` 返回 null → `dispatchClaimedTask` 走 `UNBOUND_MEMBER` 分支 → abandonTask + LOG.warn（LOG 文本已含 "or use auto-spawn successor" 提示）。**这是本计划的直接集成点**。
- **声明式团队绑定已落地（plan 231 ✅）**：lead `.agent.xml` `<team>` + member `<team-member>` 嵌入 `agent.xdef`；`TeamModelConverter` 转换 → `TeamSpec`/`TeamMemberSpec`；引擎三入口点 auto-bind。`TeamMemberSpec.agentModel` 字段（构造期 `Objects.requireNonNull`，non-null）记录成员对应的 agent 配置名——**这是 spawn 目标的解析来源**。daemon 已有 `agentModelOf(team, memberName)` helper 从 `TeamSpec.memberSpecs` 反查 `agentModel`，证明该字段可被消费。
- **同步编排器已落地（plan 233 ✅）**：`TeamTaskFlowOrchestrator.execute(teamId)` + `MemberAgentTaskStep` 同样消费已绑定成员（决策4「不 spawn」），未绑定 = fail-fast。**orchestrator 的 auto-spawn 集成为 Non-Goal（follow-up）**，本计划只集成 daemon（无人值守路径）。
- **团队 ACL / 配额 / DB 持久化 / 多租户隔离已落地（plans 228/234/230/232 ✅）**：spawn 的成员 agent 执行经既有引擎路径（`IAgentEngine.execute`），天然继承 ACL / 配额 / 多租户隔离。
- **零 auto-spawn 代码**：`nop-ai/nop-ai-agent/src/main` 无 `IMemberSpawner` / `MemberSpawner` / `AutoSpawn` / `spawnMember` 任何相关代码。daemon 的 `dispatchClaimedTask` `member == null` 分支（`TeamTaskSchedulerDaemon.java:446-452`）是唯一的未绑定成员处理路径，当前 = abandon。
- **扩展点范式成熟**：本模块已有 `ITeamAclChecker`/`NoOpTeamAclChecker`/`DefaultTeamAclChecker`（plan 228，经 `DefaultAgentEngine.setTeamAclChecker` 注入引擎）、`IResourceGuard`/`NoOpResourceGuard`/`DefaultResourceGuard`（plan 234，**经 consumer 构造注入** `InMemoryTeamManager`/`DbTeamManager`，不经引擎）、`IFencingTokenService`/`NoOpFencingTokenService`/`DefaultFencingTokenService`（plan 235，**无 engine 顶层接线**，setter 预留在接口自身）等 NoOp-shipped-default + functional-impl 范式。本模块的接线惯例是 **wire-at-consumer**：扩展点注入到真正消费它的组件（`IResourceGuard`→TeamManager consumer，`ITeamAclChecker`→engine 因 engine 是 consumer），而非统一挂到 `DefaultAgentEngine`。spawner 的 consumer 是 `TeamTaskSchedulerDaemon`（dispatch 路径），故 spawner 注入 daemon（详见 Design Decision 5）。

## Goals

- **spawn 扩展点**：一个可插拔的成员 spawn 接口（`IMemberSpawner` 或等价契约），shipped 默认为 NoOp（不 spawn = 零回归，daemon 保持当前 abandon 行为），功能实现为 `DefaultMemberSpawner`（基于 `TeamMemberSpec.agentModel` spawn 成员 agent 经 `IAgentEngine.execute` 执行任务）。
- **daemon 集成**：守护进程的 dispatch 路径，当 `resolveBoundMember` 返回 null（无已绑定成员）时，先咨询 spawner；NoOp = 当前行为（abandon），functional = spawn 后委派（与 bound-member 委派同一执行/完成/失败语义）。
- **bound-member 优先**：如果团队已有绑定成员，**不 spawn**（直接用 bound session 委派）。spawn 是 fallback，仅当无 bound member 时触发。
- **诚实失败（No Silent No-Op #24）**：spawn 失败（agentModel 解析失败 / agent 抛异常 / 非 completed 终态 / completeTask CAS 失败）= 诚实 abandon（与 bound-member dispatch 失败同一语义），不静默跳过。无成员规格（empty team / 无 memberSpec）= 无法 spawn = abandon（诚实，无法 spawn 不存在的成员）。
- **端到端验证**（Anti-Hollow #22）：构造团队（声明成员规格但**不预绑定任何 session**）+ 创建任务 → 启动守护进程（不手动 bind 任何成员）→ 断言任务自动 spawn 成员 agent 执行并转 COMPLETED。对比：NoOp spawner 下同样场景 = abandon（零回归可观测）。
- roadmap §4 Layer 4 `L4-auto-spawn-member-agent` 标注已落地。

## Non-Goals

- **orchestrator（plan 233 `TeamTaskFlowOrchestrator`）的 auto-spawn 集成**：orchestrator 是程序化入口（调用者可预绑定成员），非无人值守路径。本计划只集成 daemon。orchestrator 集成为独立 follow-up（spawner 扩展点已就绪，只需另一处 wiring）。
- **LLM 直面编排工具（`team-execute-flow`）**：独立 carry-over，依赖本计划 + 调度策略裁定。
- **spawn 后的 session 显式绑定到团队**：spawn 的成员 agent 执行经既有引擎路径；若其 `.agent.xml` 声明 `<team-member>`，plan 231 的 auto-bind 天然生效。本计划不额外手动 bind spawned session（team-tool 访问为天然副产品，非显式目标）。
- **spawn-per-task vs spawn-once-reuse 策略裁定**：本切片按 daemon 现有 per-task dispatch 模型（每任务一次 spawn 执行），session 复用 / spawn 池化为优化 follow-up。
- **多成员路由**：daemon 当前 `resolveBoundMember` 解析团队级单一成员（优先 MEMBER role 回退任意 bound）。spawn 沿用同一"团队级单一成员"策略（优先 MEMBER role 从 spec 解析）。per-task 成员路由为 successor。
- **异步/跨进程 spawn 协调**：本切片 spawn 为同步执行（与 daemon bound-member dispatch 一致）。
- **重试 / 超时 decorator**：spawn 失败诚实 abandon（供 task-reclaim successor 消费），不内建重试。

## Scope

### In Scope

- `io.nop.ai.agent.team` 包（或等价位置）：spawn 扩展点接口 + NoOp shipped 默认 + functional 实现
- `TeamTaskSchedulerDaemon` dispatch 路径集成（`resolveBoundMember` 返回 null 时咨询 spawner）+ daemon 自身 setter/构造器注入 spawner（null-safe → NoOp）
- 测试文件：
  - spawner focused 测试（NoOp 返回 no-spawn / functional spawn 经 agentModel + task prompt 经 IAgentEngine.execute / 无 memberSpec 无法 spawn 诚实返回 / spawn 结果映射到执行结果）
  - daemon 集成 focused 测试（bound member 优先不 spawn / 无 bound member + functional spawner = spawn 委派 / 无 bound member + NoOp spawner = abandon 零回归 / spawn 失败诚实 abandon）
  - 端到端无人值守 spawn 测试（声明团队 + 不预绑定 + 创建任务 → daemon.start → 任务自动 spawn 完成）
- 设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-member-auto-spawn.md`（记录设计裁定）
- `nop-ai-agent-roadmap.md` §4 Layer 4 + `nop-ai-agent-actor-runtime-vision.md` + `nop-ai-agent-task-scheduler-daemon.md` §5 同步

### Out Of Scope

- orchestrator auto-spawn 集成（Non-Goal）
- LLM 直面编排工具（Non-Goal）
- spawned session 手动 bind / team-tool 访问保证（Non-Goal）
- spawn session 复用 / 池化（Non-Goal）
- 多成员路由（Non-Goal）
- 异步/跨进程 spawn（Non-Goal）
- 重试/超时 decorator（Non-Goal）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **spawn = 可插拔扩展点，NoOp shipped 默认 = 零回归**。新增成员 spawn 接口（`IMemberSpawner` 或等价），shipped 默认 NoOp 实现（不 spawn，返回 no-spawn 结果）。daemon 在 `resolveBoundMember` 返回 null 时咨询 spawner：NoOp = 当前行为（abandon，零回归）；functional = spawn 后委派。理由：(1) 镜像本模块所有 Layer 4 扩展点范式（`ITeamAclChecker` / `IResourceGuard` / `IFencingTokenService`）；(2) NoOp shipped 默认保证既有 daemon 测试零回归；(3) 部署层 opt-in（功能性 spawner 经 setter 注入）。spawner 需要 `IAgentEngine` 依赖来 spawn（构造注入或经 daemon 传入，属实现细节）。

2. **spawn 目标 = `TeamMemberSpec.agentModel`，团队级单一成员策略**。spawn 目标从团队声明式成员规格解析：优先 MEMBER role 的 memberSpec（与 daemon `resolveBoundMember` bound-member 策略对称），回退任意 memberSpec。`TeamMemberSpec.agentModel`（non-null）提供 spawn 的 agent 配置名。daemon 当前 `resolveBoundMember` 解析团队级单一 bound member（非 per-task 路由），spawn 沿用同一"团队级单一成员"策略（从 spec 解析而非从 bound roster）。无 memberSpec 的团队 = 无法 spawn = 诚实 no-spawn（daemon abandon）。

3. **bound-member 优先，spawn 是 fallback**。daemon dispatch 路径：先 `resolveBoundMember`（bound session 优先）；仅当返回 null 时咨询 spawner。已绑定成员的团队**不 spawn**（直接用 bound session，零行为变化）。这保证既有 bound-member dispatch 语义完全不变，spawn 仅填补"无 bound member"缺口。

4. **spawn 语义 = 同步执行 + 任务作输入 + 结果映射完成/失败**。functional spawner 创建 `AgentMessageRequest`（agentModel 来自 memberSpec + 任务 prompt + 新 session）经 `IAgentEngine.execute` 同步执行（与 bound-member dispatch 同一 `agentEngine.execute(request).join()` 语义）。spawn 产出的 dispatch 上下文（agentName + session）与 bound-member 的 `ResolvedMember` 同构，daemon 的 `dispatchClaimedTask` 后续 complete/abandon 逻辑不变。spawn 失败（agent 抛异常 / 非 completed 终态 / completeTask CAS 失败）= 诚实 abandon（与 bound-member dispatch 失败同一 `DISPATCH_FAILED` 语义）。无 memberSpec / agentModel 解析失败 = no-spawn → abandon（`UNBOUND_MEMBER` 语义，诚实）。

5. **接线目标 = `TeamTaskSchedulerDaemon`（consumer），非 `DefaultAgentEngine`**。spawner 的真正消费方是 daemon 的 dispatch 路径（`dispatchClaimedTask` 在 `member == null` 时咨询 spawner），而非引擎本身。`DefaultAgentEngine` 与 daemon 之间经 `ITeamTaskSchedulerDaemon` **接口**解耦（`setTeamTaskSchedulerDaemon` 持有接口，不是具体类），引擎无法也不应向 `NoOpTeamTaskSchedulerDaemon` 传播 spawner。因此 spawner 经 **daemon 自身的 setter 或构造器**注入（null-safe → NoOp shipped 默认），镜像 `IResourceGuard`→`InMemoryTeamManager` 的 wire-at-consumer 惯例（plan 234）。理由：(1) 消费方即注入方，职责一致；(2) 不扩宽 `ITeamTaskSchedulerDaemon` 公共接口（spawner 是具体类 `TeamTaskSchedulerDaemon` 的注入，非接口契约）；(3) NoOp 默认在 daemon 内部 fallback（field 初始 = NoOp 或 null→NoOp），既有 daemon 构造/测试零回归；(4) e2e 测试构造 daemon 时传入 functional spawner 即可，无需经引擎中转。

### Phase 1 - spawn 扩展点 + functional 实现 + focused 测试

Status: completed
Targets: `io.nop.ai.agent.team`（spawn 扩展点新组件）

- Item Types: `Fix`（无人值守 auto-spawn 能力 gap）、`Decision`（spawn 范式/目标解析/bound-priority/失败语义裁定）、`Proof`

- [x] 新建成员 spawn 扩展点接口 + `NoOp` shipped 默认（不 spawn，返回 no-spawn，零回归）+ functional 实现（基于 `TeamMemberSpec.agentModel` spawn 成员 agent 经 `IAgentEngine.execute` 执行任务）
- [x] spawn 目标解析：从团队 `TeamSpec.memberSpecs` 解析目标成员（优先 MEMBER role 回退任意 memberSpec），取 `agentModel` 作 spawn agent 名；无 memberSpec = no-spawn
- [x] spawn 执行：创建任务输入（agentModel + task subject/description 作 prompt + 新 session），经 `IAgentEngine.execute` 同步执行
- [x] 诚实失败：spawn 无法解析目标 / agent 执行抛异常 / 非 completed 终态 = 诚实报告失败（no-spawn 或 execution-failure），不静默跳过（No Silent No-Op #24）
- [x] 编写 focused 测试：NoOp 返回 no-spawn / functional spawn 经正确 agentModel + task prompt 经 IAgentEngine.execute（断言 request 参数）/ 无 memberSpec 无法 spawn 诚实返回 no-spawn / spawn execution 结果正确映射 / spawn execution 失败诚实报告

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] spawn 扩展点接口存在，含 NoOp shipped 默认（no-spawn 返回）+ functional 实现
- [x] functional spawner 真实从 `TeamMemberSpec.agentModel` 解析 spawn 目标（focused 测试断言 agentModel 正确传入 `AgentMessageRequest`）
- [x] **无静默跳过**（Minimum Rules #24）：无 memberSpec / agentModel 解析失败 = 显式 no-spawn（非静默 null / 空）；spawn execution 失败 = 显式失败结果
- [x] **接线验证**（Minimum Rules #23）：focused 测试断言 functional spawner 确实调用 `IAgentEngine.execute`（非仅返回 placeholder）
- [x] focused 测试全绿
- [x] No owner-doc update required（owner doc 更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - daemon 集成 + daemon spawner 接线 + 端到端无人值守 spawn 验证 + 设计文档 + roadmap 同步 + 全量回归

Status: completed
Targets: `TeamTaskSchedulerDaemon` dispatch 路径、`TeamTaskSchedulerDaemon` spawner setter/构造器注入、端到端测试、`ai-dev/design/nop-ai-agent/nop-ai-agent-member-auto-spawn.md`（新）、`nop-ai-agent-roadmap.md` §4、`nop-ai-agent-actor-runtime-vision.md`、`nop-ai-agent-task-scheduler-daemon.md` §5

- Item Types: `Proof`

- [x] daemon dispatch 路径集成：`resolveBoundMember` 返回 null 时咨询 spawner；NoOp = abandon（当前行为零回归），functional = spawn 后委派（与 bound-member 同一 complete/abandon 语义）
- [x] bound-member 优先：有 bound member 时不 spawn（直接 bound session 委派），仅 null 时触发 spawner
- [x] daemon 接线：`TeamTaskSchedulerDaemon` 经自身 setter/构造器注入 spawner（null-safe → NoOp shipped 默认，不经 `DefaultAgentEngine` 中转——spawner consumer 是 daemon 非 engine，详见 Design Decision 5）
- [x] 编写端到端无人值守 spawn 测试：构造 `DefaultAgentEngine`（InMemoryTeamManager + InMemoryTeamTaskStore + functional spawner + mock LLM member agent）→ 程序化建团 + 声明 memberSpec（**不 `bindMemberSession`**）→ 创建任务 → **启动守护进程（不手动 bind 任何成员）** → 断言任务自动 spawn 成员 agent 执行并转 COMPLETED
- [x] 编写零回归对比测试：同样场景 + NoOp spawner = 任务 abandon（daemon 既有行为不变）
- [x] 编写 bound-priority 测试：有 bound member + functional spawner = 不 spawn（用 bound session），断言 spawner 未被调用
- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-member-auto-spawn.md`：记录 5 项裁定 + 拒绝替代方案（orchestrator 集成 / 手动 bind spawned session / spawn session 池化 / 异步 spawn）。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：`L4-auto-spawn-member-agent` 标注已落地
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` + `nop-ai-agent-task-scheduler-daemon.md` §5：auto-spawn 已落地（successor 列表移除该项）
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：从 daemon.start → 周期扫描 → CAS claim → 无 bound member → spawner.spawn → `IAgentEngine.execute` → completeTask → 任务 COMPLETED，完整路径跑通且**无任何手动成员绑定**（证明无人值守 spawn）
- [x] **零回归验证**：NoOp spawner 下既有 daemon 测试全绿（bound member dispatch / unbound abandon / CAS idempotent / CLAIMED 跳过 / 生命周期 stop 全不变）
- [x] **bound-priority 验证**：有 bound member 时 spawner 不被调用（断言），直接用 bound session 委派
- [x] **Anti-Hollow 断言**：端到端测试断言 daemon 经 spawner → `IAgentEngine.execute` 真实调用（非仅状态变化），spawn 使用的 agentModel 来自 `TeamMemberSpec`（非硬编码）
- [x] **无静默跳过**：无 memberSpec / spawn 失败 = 诚实 abandon（有测试覆盖，与合法空转区分）
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言 daemon 确实咨询注入的 spawner（NoOp 路径 = abandon 零回归）+ functional spawner 确实调用 `IAgentEngine.execute`（spawner 经 daemon setter/构造器注入，非 engine 中转）
- [x] `nop-ai-agent-member-auto-spawn.md` 存在，含 5 项裁定 + 拒绝替代方案，无类签名/代码
- [x] roadmap §4 + vision + task-scheduler-daemon §5 已更新（`L4-auto-spawn-member-agent` 已落地）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] spawn 扩展点落地为真实（非空壳）代码，NoOp shipped 默认零回归 + functional 真实 spawn 经 `IAgentEngine.execute`
- [x] daemon dispatch 路径集成（bound-member 优先 → 无 bound 时 spawner fallback）
- [x] 端到端无人值守 spawn 验证（无手动绑定，daemon.start → 任务自动 spawn 完成）
- [x] 零回归（NoOp spawner = 既有 daemon 行为不变）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（orchestrator 集成 / LLM 直面工具 / 异步 spawn / 多成员路由 / decorator 均为显式 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（design doc + roadmap §4 + vision + task-scheduler-daemon §5）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）daemon 运行时确实咨询 spawner + spawner 确实调用 `IAgentEngine.execute`（不只是类型存在），（b）端到端无人值守 spawn 路径完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；orchestrator auto-spawn 集成 / LLM 直面编排工具 / spawned session 手动 bind / spawn session 复用池化 / 多成员路由 / 异步跨进程 spawn / 重试超时 decorator 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **orchestrator（plan 233 `TeamTaskFlowOrchestrator`）auto-spawn 集成**：spawner 扩展点就绪后，只需另一处 wiring（orchestrator member resolution 失败时咨询 spawner）。Classification: successor plan required（依赖本计划的 spawner 契约）。
- **LLM 直面编排工具（`team-execute-flow`）**：Classification: successor plan required（依赖本计划 + 调度策略裁定）。
- **spawn session 复用 / 池化**：当前 per-task spawn；session 复用为优化 successor。
- **多成员 per-task 路由**：daemon 当前团队级单一成员策略；per-task 路由为 successor。
- **异步/跨进程 spawn 协调**：本切片同步 spawn；异步为 successor。

## Closure

Status Note: Plan 237 已完成。两项 Phase 全部 completed，所有 Closure Gates 经独立 closure-audit 子 agent 验证 PASS。技术上闭合 roadmap §4 Layer 4「无人值守多 Agent 自主编排」链路最后关键缺口：plan 236 交付自动调度触发但仅消费已绑定成员；本计划交付「成员自动 spawn」，使无人值守部署中无需预先绑定所有成员 agent，任务到达时 daemon 自动 spawn 对应成员执行（基于声明式 `TeamMemberSpec.agentModel`）。
Completed: 2026-06-18

Closure Audit Evidence:

独立 closure-audit 子 agent (task ses_12981c4ecffe2acVr4DZ1GFl5l, 2026-06-18) verdict: **PASS**。

- **Closure Gates** (全部 PASS)：spawn 扩展点真实代码（5 文件，`DefaultMemberSpawner.spawnMember` 真实调用 `agentEngine.execute(...).join()` at L152-153）/ daemon dispatch 集成（`TeamTaskSchedulerDaemon.memberSpawner` 字段 L176 默认 NoOp + `dispatchClaimedTask` L569-631 在 `member==null` 时咨询 spawner + `completeOrAbandonAfterExecution` L677-714 共享 bound/spawned 路径）/ 端到端无人值守 spawn（`TestTeamTaskSchedulerDaemonMemberSpawnEndToEnd.linearUnattendedAutoSpawnDagCompletesNoManualBind` 不调 `bindMemberSession` 全程 spawn 完成）/ 零回归（`TestTeamTaskSchedulerDaemon` + `TestTeamTaskSchedulerDaemonEndToEnd` 共 24 测试零 spawner 引用 = bound-member path 字面不变）/ Anti-Hollow（agentModel 来自 TeamMemberSpec 断言 "very-distinctive-member-agent-model-xyz" + execute 真实调用断言 capturedRequests.size==1）/ docs 同步（roadmap §4 新增 ✅ 行 + 验收标准 + vision §8.2/§10 Phase 3 + task-scheduler-daemon §1/§4/§5）。
- **Phase 1 Exit Criteria** (全部 PASS)：13 focused 测试覆盖 NoOp NO_SPAWN / functional 真实解析 agentModel / 接线 #23 execute 调用 / 无 memberSpec 诚实 NO_SPAWN / 非 completed 仍 DISPATCHED / execution 抛异常 SPAWN_FAILED / 优先 MEMBER role / 回退 LEAD 等。
- **Phase 2 Exit Criteria** (全部 PASS)：11 daemon 集成 focused 测试 + 6 e2e 无人值守 spawn 测试覆盖 bound-priority / NoOp 零回归对比 / spawn DISPATCHED non-completed abandon / SPAWN_FAILED abandon / NO_SPAWN abandon / spawner 抛异常防御性 abandon / spawner 经 daemon setter 注入（wire-at-consumer 决策5）/ agentModel 来自 TeamMemberSpec 非硬编码（Anti-Hollow 断言）。
- **Test Run**：54 tests / 0 failures / 0 errors（TestMemberSpawner 13 + TestTeamTaskSchedulerDaemonMemberSpawner 11 + TestTeamTaskSchedulerDaemonMemberSpawnEndToEnd 6 + TestTeamTaskSchedulerDaemon 18 回归 + TestTeamTaskSchedulerDaemonEndToEnd 6 回归）。全量 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` = 2536 tests BUILD SUCCESS（plan 236 baseline 2506 + 本计划新增 30）。
- **Anti-Hollow 验证**：daemon 运行时确实咨询 spawner（dispatchClaimedTask L585-588）+ spawner 确实调用 IAgentEngine.execute（DefaultMemberSpawner L152-153）+ 端到端无人值守 spawn 路径完整连通（无 bindMemberSession 全程 spawn 完成）+ 无空方法体/静默跳过（NoOp 返回显式 SpawnMemberResult.noSpawn 非 null，daemon 防御性 null-result/throws 分支均诚实 abandon）。
- **Clerical gap**（已在本次 plan 维护中修复）：closure 时点 plan 文件 Plan Status/Closure Gates/Closure section 仍为占位符 → 已用本 audit 报告作为 Closure Audit Evidence 填写。

Follow-up:

- orchestrator auto-spawn 集成（successor plan required，依赖本计划 spawner 契约）
- LLM 直面编排工具（successor plan required）
- spawn session 复用 / 池化（优化 successor）
- 多成员 per-task 路由（successor）
- 异步/跨进程 spawn（successor）

## Follow-up handled by 238-nop-ai-agent-orchestrator-auto-spawn.md

> 追加于 2026-06-18（carry-over 链接，不改动上方历史记录）。
> 本计划 Non-Blocking Follow-ups / Closure Follow-up 中的「orchestrator（plan 233 `TeamTaskFlowOrchestrator`）auto-spawn 集成」一项，已由后续计划 `ai-dev/plans/238-nop-ai-agent-orchestrator-auto-spawn.md` 接管（carry-over `L4-orchestrator-auto-spawn-integration`）。该计划复用本计划交付的 `IMemberSpawner`/`NoOpMemberSpawner`/`DefaultMemberSpawner` 契约，在 orchestrator 侧的图节点运行期接入 spawn 执行。
