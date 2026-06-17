# 223 nop-ai-agent TeamManager 团队生命周期管理（Platform Layer Phase 3 Foundational）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8-team-manager

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/218-nop-ai-agent-actor-runtime.md`（Non-Blocking Follow-ups 第三条：`TeamManager + TeamSpec DSL + Team ACL + Fencing Token（vision §8 / §10 Phase 3）：团队生命周期 + 成员编排 + ACL 权限派生。Classification: successor plan required`）；`ai-dev/plans/222-nop-ai-agent-recovery-manager-daemon.md`（Non-Blocking Follow-ups：`TeamManager + ResourceGuard + Fencing Token（vision §10 Phase 3/5）。Classification: successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md` §4.2（TeamManager 组件职责）+ §8（Team Mode 完整设计）+ §10 Phase 3；`ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` §5（Agent 间通信）+ §9（演进方向：团队维度）
> Related: `218`（交付 Actor Runtime 基础原语 `IActorRuntime`/`AgentActor`/`ActorRegistry`，本计划在其上新增 TeamManager）、`216`（交付 `IMailbox` deferred-ack 邮箱，团队成员通信的底层原语）、`168`（交付 `call-agent`/`send-message` 工具 MVP）、`220`（交付 steering 注入，团队消息可注入成员 ReAct 上下文）

## Purpose

把 nop-ai-agent 的多 Agent 协同从"无团队概念——Agent 之间仅通过 `call-agent` fork+exec 或 `send-message` fire-and-forget 交互，无团队生命周期、无成员状态聚合视图"扩展为"可选启用 `ITeamManager` 管理 Agent 团队的生命周期——创建/解散/成员管理/状态查询"。本计划交付 vision doc §10 Phase 3 的**契约表面 + 数据对象 + NoOp shipped 默认 + 功能性 in-memory 实现 + 引擎接线**，闭合 TeamManager roadmap gap 的基础层。本计划只负责这一件事：让模块具备团队注册表与生命周期管理能力，作为后续团队工具（team-task-create / team-send-message / team-status）和 Team ACL 强制的基础设施。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17）：

- **Actor Runtime 基础层已落地**（plan 218 / L4-8 ✅）：`IActorRuntime` 契约（`isEnabled()`/`createActor`/`destroyActor`/`getActor`/`getActorBySession`/`getActiveActors`/`destroyAll`）+ `AgentActor`（不可变标识 actorId/sessionId/agentName + volatile status/lastActiveAt + steering queue 引用）+ `AgentActorStatus`（7 值枚举）+ `ActorRegistry`/`InMemoryActorRegistry`（双索引 ConcurrentHashMap：actorId→AgentActor + sessionId→actorId）+ `NoOpActorRuntime` shipped 默认（`isEnabled()=false`，引擎跳过 Actor 路径，零回归）+ `InMemoryActorRuntime` 功能实现（专用单线程 executor observation-only→steering-injection mailbox 消费循环）。`DefaultAgentEngine` 三入口点（doExecute/resumeSession/restoreSession）经 `isEnabled()` guard opt-in 注册/注销 Actor。
- **Steering 注入已落地**（plan 220 / L4-8-steering ✅）：`AgentExecutionContext` 新增线程安全 steering 消息队列（`ConcurrentLinkedQueue<ChatMessage>`），ReAct 循环每轮工具执行后 drain steering queue 并注入到下一轮 LLM 请求，Actor 消费循环从 observation-only 升级为 steering-injection（poll → envelope payload 转 `ChatMessage` → enqueue 到 ctx steering queue → ack）。`DefaultAgentEngine` 三入口点在 `createActor` 后关联 ctx steering queue 到 Actor。
- **`IMailbox` deferred-ack 邮箱已落地**（plan 216 / L4-5 ✅）：3-phase reservation 协议（`offer` → `poll` → `ack`/`nack`）+ `DeferredAckMailbox` in-memory 功能实现 + `NoOpMailbox` 显式 no-op 默认 + `MailboxMessageHandler` 适配器。`DefaultAgentEngine.ensureSessionMailbox`（`:1966`）经 `mailboxFactory` 为每个 session 创建 per-session `IMailbox`。
- **`IAgentMessenger` 已落地**（plan 166 / L4-1 ✅）：Agent 域 messenger 提供 `send`（fire-and-forget）/ `request`（request-response with CompletableFuture）/ `registerHandler`（topic 订阅）。`DefaultAgentEngine.messenger` 字段（默认 `NoOpAgentMessenger`）+ `setMessenger` setter。inbox topic 命名约定：`agent.{actorId/sessionId}.inbox`。
- **`call-agent` / `send-message` 工具 MVP 已交付**（plan 168 / L4-1b ✅）：`CallAgentExecutor`（fork+exec via `IAgentEngine.execute()`）+ `SendMessageExecutor`（fire-and-forget via `IAgentMessenger.send()`）。
- **跨进程接管锁已落地**（plan 221 / L4-8-P4 ✅）：`ISessionTakeoverLock` + `DbSessionTakeoverLock`（独立 `ai_agent_session_lock` 表 + lease/TTL CAS）。
- **RecoveryManager 定时扫描 daemon 已落地**（plan 222 / L4-8-P4 ✅）：`IRecoveryManager` + `ScheduledRecoveryManager`（`IScheduledExecutor` 周期调度默认 60s，scanOnce = stale lock cleanup + orphan session detection）。
- **零 TeamManager 代码存在**：grep `TeamManager|ITeamManager|TeamSpec|TeamMember|TeamStatus` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 实现命中（仅 design docs 中的设计描述）。无接口、实现或数据对象存在。
- **vision §4.2 Platform Layer 组件表**：TeamManager 标注 `successor（Phase 3）`，职责为"Agent 团队的生命周期（创建/解散/成员管理/状态查询）"，Nop 平台映射为 `@BizModel("AiTeam")` + ORM 实体持久化。
- **vision §8 Team Mode 完整设计**：§8.1 TeamSpec（name/description/leadAgent/members[]/maxParallelMembers/maxWallClockMinutes/maxMessagesPerRun）；§8.2 团队通信模型（Lead 创建任务/发送消息/更新状态/查询状态，经 IMessageService）；§8.3 与 oh-my-opencode 的关键差异（进程模型/状态存储/消息传递/任务认领/崩溃恢复/多用户/资源隔离）。
- **roadmap §4**：Layer 4 验收标准 line 251 "多 Agent 任务可以通过 Flow / Task 组织"未勾选——本计划交付 TeamManager 基础层（团队注册 + 成员管理），为该验收标准的 foundational 前置。

## Goals

- `ITeamManager` 契约定义团队生命周期操作：创建团队（从 `TeamSpec`）、解散团队、查询团队、按 session 查询所属团队、添加/移除成员、查询团队状态。
- `TeamSpec` 数据对象定义团队配置（团队名称、描述、lead agent 名称、成员规格列表、容量约束）。
- `Team` 运行时团队实例持有 teamId、spec、成员映射、状态（CREATED/ACTIVE/DISBANDED）、时间戳。
- `NoOpTeamManager` shipped 默认——所有方法返回 empty/no-op，零行为回归（与 `NoOpActorRuntime`/`NoOpMailbox` 一致的 opt-in 模式）。
- `InMemoryTeamManager` 功能实现——`ConcurrentHashMap` 支撑的线程安全团队注册表，支持完整生命周期。
- `DefaultAgentEngine.teamManager` 字段 + `setTeamManager` setter（遵循 `actorRuntime`/`messenger`/`sandboxBackend` 等已有 field+setter 模式）。
- 端到端验证：创建团队 → 添加成员 → 查询状态 → 移除成员 → 解散团队，完整路径跑通。
- roadmap §4 Layer 4 验收标准 "多 Agent 任务可以通过 Flow / Task 组织" 标注 foundational 前置已落地。

## Non-Goals

- **TeamSpec XDSL 配置化**（vision §8.1 `team-spec.xdef`）：团队定义经 XDSL 配置加载、Delta 定制。是独立 successor（optimization candidate）。本计划 TeamSpec 是程序化构造的不可变数据对象。
- **DB-backed 团队持久化**（vision §4.2 `@BizModel("AiTeam")` + ORM 实体）：团队状态写 DB 表、跨进程共享、事务保护。是独立 successor plan required。本计划仅交付 in-memory 实现。
- **Team ACL 强制**（vision §5.1 `TeamAclEntry` / `AclResource` / `AclAction`）：角色权限矩阵（LEAD=ADMIN / MEMBER=READ+WRITE+EXECUTE）、权限派生（子 Actor 继承父 ACL）、权限检查拦截。是独立 successor plan required。本计划 `MemberRole` 枚举仅做标识，不做权限强制。
- **共享任务表**（vision §8.2 `team-task-create` / `team-task-update`）：DB 事务保护的团队级共享任务表 + DB 乐观锁任务认领。是独立 successor plan required。
- **团队通信工具**（vision §8.2 `team-task-create` / `team-send-message` / `team-task-update` / `team-status` 作为 IToolExecutor）：LLM 可调用的团队操作工具集。是独立 successor plan required。本计划交付 TeamManager 注册表，工具消费侧是 successor。
- **Fencing Token**（vision §5.1）：monotonic counter 并发写入冲突防护协议。是独立 successor（属 ResourceGuard / Phase 5 范畴）。
- **call-agent 异步 mailbox 模型**（vision §10 Phase 2 剩余）：将 `call-agent` 从 fork+exec 迁移到 Actor mailbox REQUEST/RESPONSE。是独立 successor plan required。本计划不改变 `call-agent` 现有行为。
- **ResourceGuard + CoordinationBusStrategy + 资源配额**（vision §5.2 / §10 Phase 5）：协调信道、配额强制。是独立 successor plan required。
- **团队最大成员数配额强制**（vision §5.2 `团队最大成员数 (teamModel) 默认 8`）：`maxParallelMembers` 仅在 `TeamSpec` 中记录，`InMemoryTeamManager` 不强制（配额强制属 ResourceGuard 范畴）。
- **多租户 tenantId/userId 隔离维度**（vision §5.1）：团队按租户/用户隔离。是独立 successor（依赖 `AgentExecutionContext` tenant 标识标准化）。本计划团队注册表不做租户隔离。
- **XDSL 配置化**：`agent.xdef` 增加 `<team-manager>` 元素。Classification: optimization candidate。
- **nop-task / nop-stream 集成**（vision §2.2）：团队编排接入 nop-task DAG。Classification: out-of-scope improvement。

## Scope

### In Scope

- `io.nop.ai.agent.team` 包下的新文件：
  - `TeamSpec.java` — 不可变团队配置（teamName, description, leadAgentName, memberSpecs, maxParallelMembers）
  - `TeamMemberSpec.java` — 成员规格（memberName, agentModel, role）
  - `MemberRole.java` — 枚举（LEAD, MEMBER）
  - `TeamStatus.java` — 枚举（CREATED, ACTIVE, DISBANDED）
  - `Team.java` — 运行时团队实例（teamId, spec, members map, status, createdAt, disbandedAt）
  - `TeamMember.java` — 运行时成员（memberName, role, sessionId, actorId, status）
  - `ITeamManager.java` — 契约接口
  - `NoOpTeamManager.java` — shipped 默认
  - `InMemoryTeamManager.java` — 功能实现
- `DefaultAgentEngine.java` — 新增 `teamManager` 字段（默认 `NoOpTeamManager.noOp()`）+ `setTeamManager` / `getTeamManager`
- 测试文件（`io.nop.ai.agent.team` 包下）：
  - `TestTeamSpec.java` — 数据对象不可变性测试
  - `TestNoOpTeamManager.java` — NoOp 默认零行为测试
  - `TestInMemoryTeamManager.java` — 功能实现完整生命周期测试
  - `TestTeamManagerEngineWiring.java` — 引擎 field+setter 接线测试

### Out Of Scope

- `team-spec.xdef` XDSL 定义（Non-Goal: TeamSpec XDSL 配置化）
- `nop_ai_team` / `nop_ai_team_member` ORM 实体与 DB 表（Non-Goal: DB-backed 持久化）
- `TeamAclEntry` / `AclResource` / `AclAction` 权限模型（Non-Goal: Team ACL 强制）
- `team-task-create` / `team-send-message` / `team-status` IToolExecutor（Non-Goal: 团队通信工具）
- `FencingToken` 类与协议（Non-Goal: Fencing Token）
- `call-agent` 异步 mailbox 迁移（Non-Goal: call-agent 异步模型）

## Execution Plan

### Phase 1 - 契约表面 + 数据对象 + NoOp 默认

Status: completed
Targets: `io.nop.ai.agent.team`（新包）+ `NoOpTeamManager`

- Item Types: `Decision`、`Proof`

- [x] 定义 `MemberRole` 枚举（LEAD / MEMBER），Javadoc 明确语义（LEAD = 团队领导，拥有管理权限的意向标识；MEMBER = 普通成员。本计划不做权限强制，ACL enforcement 是 successor）
- [x] 定义 `TeamStatus` 枚举（CREATED / ACTIVE / DISBANDED），Javadoc 明确状态转换（CREATED → ACTIVE（首个成员绑定 session 后）→ DISBANDED（显式解散）；DISBANDED 是终态）
- [x] 定义 `TeamMemberSpec` 不可变数据对象（memberName: String, agentModel: String, role: MemberRole），全参构造 + getter，无 setter
- [x] 定义 `TeamSpec` 不可变数据对象（teamName: String, description: String, leadAgentName: String, memberSpecs: List<TeamMemberSpec>, maxParallelMembers: int），全参构造 + getter，无 setter，`maxParallelMembers <= 0` 表示无限制
- [x] 定义 `TeamMember` 运行时成员数据对象（memberName: String, role: MemberRole, sessionId: String(nullable), actorId: String(nullable), joinedAt: long），sessionId/actorId 初始为 null，在成员绑定 session 时填充；提供 `bind(String sessionId, String actorId)` 方法设置绑定（非 final 字段，方法内 null-check）
- [x] 定义 `Team` 运行时团队实例数据对象（teamId: String, spec: TeamSpec, members: Map<String, TeamMember>, status: TeamStatus, createdAt: long, disbandedAt: long），teamId/spec/createdAt 不可变；members/status/disbandedAt 可变（线程安全由 ITeamManager 实现保证，不由 Team 自身保证——与 `AgentActor` 的 volatile 模式一致：status volatile，members 由 manager 的 ConcurrentHashMap 保护）
- [x] 定义 `ITeamManager` 契约接口，方法签名：
  - `Team createTeam(TeamSpec spec)` — 从 spec 创建团队，返回带唯一 teamId 的 Team 实例
  - `Optional<Team> getTeam(String teamId)` — 按 teamId 查询
  - `Optional<Team> getTeamBySession(String sessionId)` — 按 member sessionId 反查所属团队
  - `Team disbandTeam(String teamId)` — 解散团队（status → DISBANDED），返回最终 Team 状态
  - `Collection<Team> getActiveTeams()` — 返回所有非 DISBANDED 团队的快照
  - `TeamMember addMember(String teamId, TeamMemberSpec memberSpec)` — 向团队添加成员
  - `boolean removeMember(String teamId, String memberName)` — 从团队移除成员
  - `boolean bindMemberSession(String teamId, String memberName, String sessionId, String actorId)` — 将成员绑定到运行时 session/actor
  - `Optional<TeamMember> getMember(String teamId, String memberName)` — 查询单个成员
- [x] 实现 `NoOpTeamManager` shipped 默认：`createTeam` 返回 null 或抛 `UnsupportedOperationException`？**裁定**：返回 null 违反 No Silent No-Op Rule（#24），因此 `createTeam` / `disbandTeam` / `addMember` / `removeMember` / `bindMemberSession` 抛 `UnsupportedOperationException("NoOpTeamManager: team management not enabled")`，`getTeam` / `getTeamBySession` / `getActiveTeams` / `getMember` 返回 `Optional.empty()` / 空集合。提供 `NoOpTeamManager.noOp()` 静态工厂方法返回单例
- [x] 编写 `TestNoOpTeamManager`：验证所有写操作抛 `UnsupportedOperationException`、所有读操作返回 empty/空集合、`noOp()` 返回同一单例
- [x] 编写 `TestTeamSpec`：验证 `TeamSpec`/`TeamMemberSpec` 不可变性（无 setter，全参构造 + getter）、`maxParallelMembers <= 0` 表示无限制的语义

Exit Criteria:

- [x] `MemberRole`、`TeamStatus`、`TeamMemberSpec`、`TeamSpec`、`TeamMember`、`Team`、`ITeamManager`、`NoOpTeamManager` 8 个文件存在于 `io.nop.ai.agent.team` 包
- [x] `TeamSpec` / `TeamMemberSpec` 是不可变数据对象（无 setter，全参构造 + getter）
- [x] `NoOpTeamManager` 写操作抛 `UnsupportedOperationException`（非静默返回 null/false），读操作返回 empty/空集合（**无静默跳过** #24）
- [x] `TestNoOpTeamManager` + `TestTeamSpec` 全绿
- [x] No owner-doc update required（契约表面定义不改 live baseline 行为）

### Phase 2 - 功能实现 + 引擎接线 + 端到端验证

Status: completed
Targets: `io.nop.ai.agent.team.InMemoryTeamManager` + `DefaultAgentEngine.java`

- Item Types: `Proof`、`Follow-up`

- [x] 实现 `InMemoryTeamManager` 功能实现：
  - `ConcurrentHashMap<String, Team>` teams 索引（teamId → Team）
  - `ConcurrentHashMap<String, String>` sessionIndex 索引（sessionId → teamId）用于 `getTeamBySession` 反查
  - `createTeam`：生成 UUID teamId，从 `TeamSpec.memberSpecs` 初始化 `Team.members` map（每个 memberSpec 转为 sessionId/actorId = null 的 `TeamMember`），status = CREATED，存入 teams map
  - `addMember`：验证 team 存在且非 DISBANDED，将 memberSpec 转为 TeamMember 存入 team.members（如 memberName 已存在则抛 `NopAiAgentException`）
  - `bindMemberSession`：验证 team + member 存在，设置 member.sessionId/actorId，同时更新 sessionIndex；首个成员绑定 session 时 team.status → ACTIVE
  - `removeMember`：验证 team 存在，从 team.members 移除，如有 sessionIndex 绑定则一并清理
  - `disbandTeam`：验证 team 存在，status → DISBANDED，记录 disbandedAt，保留在 teams map（可查询历史状态）
  - `getTeam` / `getTeamBySession` / `getActiveTeams` / `getMember`：只读查询，返回快照
  - teamId 生成使用 `java.util.UUID.randomUUID().toString()`
- [x] `DefaultAgentEngine` 引擎接线（遵循 `actorRuntime`/`messenger`/`sandboxBackend` field+setter 模式）：
  - 新增 `private ITeamManager teamManager = NoOpTeamManager.noOp();` 字段（默认 NoOp，零回归）
  - 新增 `public void setTeamManager(ITeamManager teamManager)` setter（null-safe：null → NoOp 默认）
  - 新增 `public ITeamManager getTeamManager()` getter
  - 字段 Javadoc 明确：TeamManager 是 opt-in 增量能力（NoOp shipped 默认），引擎当前不在三入口点自动创建团队（团队创建由集成商/工具程序化调用），自动团队绑定（从 agent config 的 TeamSpec 自动创建团队）是 successor
- [x] 编写 `TestInMemoryTeamManager`：覆盖完整生命周期（create → addMember → bindMemberSession → status 转 ACTIVE → getTeamBySession 反查 → removeMember → disbandTeam）、并发安全（多线程同时操作不同团队）、边界条件（重复 memberName 抛异常、disband 后 addMember 抛异常、getTeam 不存在返回 empty）
- [x] 编写 `TestTeamManagerEngineWiring`：验证 `DefaultAgentEngine` 默认 `teamManager` instanceof `NoOpTeamManager`、`setTeamManager` 注入 `InMemoryTeamManager` 后 `getTeamManager` 返回注入实例、null setter 回退 NoOp
- [x] 编写端到端测试：构造 `DefaultAgentEngine` + `InMemoryTeamManager` + `InMemoryActorRuntime` → 程序化创建团队 → 经 `engine.execute()` 执行 lead agent（Actor 自动注册到 ActorRegistry）→ 程序化绑定 lead session 到团队 → 查询团队状态（含成员 actor 状态）→ 解散团队。断言团队创建/绑定/查询/解散全路径
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` §4.2 TeamManager 状态从 `successor（Phase 3）` → `✅ 基础层已落地（plan 223 / L4-8-team-manager）` + §10 Phase 3 标注 foundational slice 已交付
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 "多 Agent 任务可以通过 Flow / Task 组织" 标注 foundational 前置（TeamManager 注册表）已落地

Exit Criteria:

- [x] `InMemoryTeamManager.java` 存在于 `io.nop.ai.agent.team` 包，`ConcurrentHashMap` 双索引（teamId + sessionId）线程安全
- [x] 完整生命周期可运行：create → addMember → bindMemberSession → status ACTIVE → getTeamBySession → removeMember → disbandTeam
- [x] `DefaultAgentEngine.teamManager` 字段默认 `NoOpTeamManager.noOp()` + `setTeamManager` setter（null-safe 回退 NoOp）+ `getTeamManager` getter
- [x] **接线验证**（#23）：`TestTeamManagerEngineWiring` 断言默认 instanceof NoOp + setter 注入 InMemory + null 回退 NoOp
- [x] **端到端验证**（#22）：`TestTeamManagerEndToEnd` 从 `createTeam` 经 `engine.execute()` Actor 注册到 `disbandTeam` 完整跑通
- [x] **无静默跳过**（#24）：`InMemoryTeamManager` 所有方法有真实实现（非空方法体），不存在的方法不在本接口上定义
- [x] `TestInMemoryTeamManager` + `TestTeamManagerEngineWiring` + 端到端测试全绿
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（既有测试零回归）
- [x] `nop-ai-agent-actor-runtime-vision.md` §4.2 + §10 Phase 3 已更新
- [x] `nop-ai-agent-roadmap.md` §4 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `ITeamManager` 契约 + `TeamSpec`/`Team`/`TeamMember`/`TeamMemberSpec`/`MemberRole`/`TeamStatus` 数据对象 + `NoOpTeamManager` + `InMemoryTeamManager` 全部落地为真实（非空壳）代码
- [x] `DefaultAgentEngine.teamManager` 字段 + setter/getter 接线（遵循 field+setter 模式）
- [x] 团队生命周期完整：create / addMember / bindMemberSession / removeMember / disbandTeam / getTeam / getTeamBySession / getActiveTeams / getMember
- [x] NoOp shipped 默认零回归（写操作抛 UOE，读操作返回 empty）
- [x] 必要 focused verification 已完成（4 个测试文件覆盖 NoOp + InMemory + 接线 + 端到端）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（TeamSpec XDSL / DB 持久化 / Team ACL / 共享任务表 / 团队工具 / Fencing Token / call-agent 异步 / ResourceGuard / 多租户隔离均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（vision §4.2 / §10 Phase 3 + roadmap §4）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）InMemoryTeamManager 调用链真实（非空方法体/静默跳过），（b）引擎 field+setter 接线可被测试验证
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；TeamSpec XDSL / DB-backed 持久化 / Team ACL 强制 / 共享任务表 / 团队通信工具 / Fencing Token / call-agent 异步 / ResourceGuard / 多租户隔离 / 配额强制 / nop-task 集成 / XDSL 配置化均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **TeamSpec XDSL 配置化**（vision §8.1 `team-spec.xdef`）：团队定义经 XDSL 配置加载、Delta 定制。Classification: optimization candidate。
- **DB-backed 团队持久化**（vision §4.2 `@BizModel("AiTeam")` + ORM 实体）：团队状态写 DB 表、跨进程共享。Classification: successor plan required。
- **Team ACL 强制**（vision §5.1）：角色权限矩阵 + 权限派生 + 权限检查拦截。Classification: successor plan required。
- **共享任务表**（vision §8.2）：DB 事务保护的团队级共享任务表 + DB 乐观锁任务认领。Classification: successor plan required。
- **团队通信工具**（vision §8.2 `team-task-create` / `team-send-message` / `team-task-update` / `team-status` IToolExecutor）：LLM 可调用的团队操作工具集。Classification: successor plan required。
- **Fencing Token**（vision §5.1）：monotonic counter 并发写入冲突防护。Classification: successor plan required（属 ResourceGuard / Phase 5 范畴）。
- **call-agent 异步 mailbox 模型**（vision §10 Phase 2 剩余）：将 call-agent 从 fork+exec 迁移到 Actor mailbox REQUEST/RESPONSE。Classification: successor plan required。
- **ResourceGuard + CoordinationBusStrategy + 资源配额**（vision §5.2 / §10 Phase 5）。Classification: successor plan required。
- **多租户 tenantId/userId 隔离维度**（vision §5.1）。Classification: successor plan required。
- **团队最大成员数配额强制**（vision §5.2 `maxParallelMembers`）。Classification: successor plan required（属 ResourceGuard 范畴）。
- **自动团队绑定**（从 agent config 的 TeamSpec 自动创建团队 + 绑定成员 session）：引擎三入口点自动调用 TeamManager。Classification: successor plan required（依赖 TeamSpec XDSL 配置化）。
- **XDSL 配置化**：`agent.xdef` 增加 `<team-manager>` 元素。Classification: optimization candidate。
- **nop-task / nop-stream 集成**（vision §2.2）：团队编排接入 nop-task DAG。Classification: out-of-scope improvement。

## Closure

Status Note: plan 223 全部交付。`ITeamManager` 契约 + 6 个数据对象（TeamSpec/Team/TeamMember/TeamMemberSpec/MemberRole/TeamStatus）+ NoOpTeamManager shipped 默认 + InMemoryTeamManager 功能实现（ConcurrentHashMap 双索引，完整生命周期 + CREATED→ACTIVE→DISBANDED 状态机）+ DefaultAgentEngine.teamManager 字段接线（field+setter+getter，null-safe）。所有 in-scope successor（TeamSpec XDSL / DB 持久化 / Team ACL / 共享任务表 / 团队工具 / Fencing Token / call-agent 异步 / ResourceGuard / 多租户隔离 / 配额强制 / 自动团队绑定）均显式在 Non-Goals 切出为独立 successor，无 in-scope live defect 被静默降级。roadmap §4 Layer 4 验收标准 line 251 "多 Agent 任务可以通过 Flow / Task 组织" foundational 前置（TeamManager 注册表）已落地标注。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（explore，fresh session ses_12ea0abaeffeSG5XrjvOJlK04q）
- Evidence:
  - **Exit Criteria 验证**：
    - Phase 1 Exit Criteria（4 项）PASS：8 个文件存在于 `io.nop.ai.agent.team` 包；TeamSpec/TeamMemberSpec 不可变（无 setter，全参构造 + getter）；NoOpTeamManager 写操作抛 UOE + 读操作 empty（TestNoOpTeamManager 10 tests 全绿）；TestNoOpTeamManager + TestTeamSpec 全绿（22 tests, 0 failures）
    - Phase 2 Exit Criteria（10 项）PASS：InMemoryTeamManager ConcurrentHashMap 双索引（teamId + sessionId）；完整生命周期（TestInMemoryTeamManager `fullLifecycleCreateBindQueryRemoveDisband`）；引擎 teamManager 字段 + setTeamManager（null-safe）+ getTeamManager；接线验证 TestTeamManagerEngineWiring（3 tests：NoOp default + setter 注入 + null 回退）；端到端验证 TestTeamManagerEndToEnd（1 test：createTeam → engine.execute() Actor 注册 → bindMemberSession（CREATED→ACTIVE）→ getTeamBySession 反查 → disbandTeam → Actor 销毁）；无静默跳过；`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS **2153 tests, 0 failures**（含 49 新增 team tests，既有测试零回归）；vision §4.2 + §10 Phase 3 已更新；roadmap §4 已更新；ai-dev/logs/2026/06-17.md 已更新
  - **Closure Gates 验证（6 项 + 3 项构建）PASS**：独立子 agent READ-ONLY 审计确认（1）InMemoryTeamManager 所有方法有真实方法体（无空方法体/静默跳过/TODO/FIXME），createTeam 生成真实 UUID + 从 spec 填充 members，bindMemberSession 真实触发 CREATED→ACTIVE 转换（teams.compute 保证 exactly-once）；（2）DefaultAgentEngine 真实接线（import L97-98 + 字段 L273 + setter L1162-1164 null-safe + getter L1170-1172，publicly testable）；（3）NoOpTeamManager 写操作全部抛 UOE（notEnabled() 工厂），读操作返回 empty/emptyList；（4）9 个 main 文件全部存在；（5）5 个 test 文件全部存在；（6）vision §4.2 TeamManager 行从 `successor（Phase 3）` 变为 `🟡 部分落地`，roadmap §4 line 251 含 foundational 前置标注
  - **`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/223-nop-ai-agent-team-manager.md --strict` 退出码 0**（所有 checklist 已勾选 + Closure Evidence 已写入）
  - **Anti-Hollow 检查结果**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical / 0 high / 0 medium / 0 low findings）；独立子 agent 调用链追踪确认 InMemoryTeamManager 9 方法均有真实实现，引擎 field+setter 接线可被 TestTeamManagerEngineWiring 验证
  - **Deferred 项分类检查**：Non-Goals 中列出的 12 个 successor 均为显式独立 successor（TeamSpec XDSL / DB 持久化 / Team ACL / 共享任务表 / 团队通信工具 / Fencing Token / call-agent 异步 mailbox / ResourceGuard + 配额 / 多租户隔离 / 自动团队绑定 / XDSL 配置化 / nop-task 集成），无 in-scope live defect 被降级为 follow-up

Follow-up:

- no remaining plan-owned work（所有 Non-Goals 均为显式独立 successor，见 Non-Blocking Follow-ups 段落）

## Follow-up handled by 225-nop-ai-agent-team-communication-tools.md

团队通信工具（Non-Blocking Follow-ups 第五条，标 `successor plan required`）已由 successor plan `ai-dev/plans/225-nop-ai-agent-team-communication-tools.md` 接管：交付 3 个 LLM 可调用的团队 IToolExecutor（`team-send-message` / `team-status` / `team-task-create`）+ in-memory `ITeamTaskStore`（`NoOpTeamTaskStore` shipped 默认 + `InMemoryTeamTaskStore` 功能实现）+ `AgentToolExecuteContext` teamManager/teamTaskStore 接线 + `DefaultAgentEngine.teamTaskStore` 字段。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
