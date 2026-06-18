# Nop AI Agent 高层构想：Actor 模式多 Agent 运行时

**状态**：active — Actor 心智模型已纳入 `00-vision.md` 作为设计基线。本篇定义 Platform Layer 的具体组件设计和实现方案。

## 1. 目标

本篇定义 Nop AI Agent 运行时的 Platform Layer 具体架构——基于 Java 平台特性、Actor 模型、消息队列的多 Agent 运行时。

**核心命题**：如何在分布式 actor 心智模型下，安全高效地运行多个 Agent 实例，支持多用户并发、自动恢复、资源隔离、无缝扩展到多实例部署。

本篇是 `01-architecture-baseline.md` 的**扩展**，在其上方新增 **Platform Layer**——多 Agent 运行时。Agent Engine Layer（ReAct 循环、Session、Hook/Skill、Memory）不变。

## 2. 设计原则

### 2.1 来自调研的关键洞察

基于对 12+ 个 Agent 框架（OpenCode, oh-my-opencode, agentscope-java, solon-ai, pi-agent 等）的调研分析，以下洞察驱动本设计：

| 洞察 | 来源 | 设计影响 |
|------|------|---------|
| Agent 即配置（不是类继承） | OpenCode, oh-my-opencode | Nop 已遵循：AgentModel 是 XDSL 配置对象 |
| 权限继承 + 子 Agent 派生 | OpenCode (subagent-permissions.ts) | Nop 已遵循：子 Agent 自动继承父 Agent 的 deny 规则 |
| Deferred-ack 异步通信 | oh-my-opencode Team Mode | Actor 邮箱模型，非阻塞 |
| 崩溃恢复是生产必备 | oh-my-opencode, Reasonix | 4 种异常状态的自动恢复策略 |
| 无中央编排器，LLM 自行委派 | OpenCode task tool | Nop 已遵循：call-agent 工具模型，LLM 自行决定委派 |
| Skill 驱动的团队组合 | oh-my-opencode security-research/hyperplan | Skill 定义 Agent 团队结构 |
| 文件系统不是好选择 | oh-my-opencode | Java 有 DB 事务和内存队列，不需要文件锁 |

### 2.2 Nop 平台特性利用

| Java/Nop 特性 | Agent 系统用途 |
|---------------|--------------|
| **Virtual Thread** | 每个 Agent 实例一个虚拟线程，轻量级，百万级并发 |
| **IMessageService** (nop-api-core / nop-message-core) | Agent 间通信的内存消息队列，已有 topic pub/sub |
| **ITaskStep + Decorator** (nop-task) | Agent 编排的 DAG 执行，内置 retry/timeout/rate-limit（DAG 拓扑 + 依赖序同步编排切片已落地 plan 233 / `nop-ai-agent-task-flow-integration.md`；decorator 体系接入为 successor） |
| **IBizObject + @BizAction** | Agent 能力暴露为 GraphQL/RPC，自动鉴权 |
| **XDSL + Delta** | Agent 配置的可逆计算定制 |
| **IOrmSession** | Agent 状态持久化到 DB，事务保护 |
| **ICancelToken** | Agent 执行的协作式取消 |
| **IContext (tenantId/userId)** | 多租户隔离的天然基础 |
| **CompletableFuture** | Agent 异步执行的标准异步原语 |
| **nop-stream CEP** | Phase 3+: Agent 事件的复杂事件处理（异常检测、模式匹配） |

### 2.3 拒绝的设计

| 被拒绝的方案 | 理由 |
|-------------|------|
| **多进程模型** | Java 单进程已足够强大，Virtual Thread 比进程轻量百万倍。不需要 IPC。 |
| **文件系统作状态存储** | Java 有成熟的 DB 事务、内存数据结构。文件锁是 TypeScript 生态的无奈之举。 |
| **中央编排器 Agent** | 自举问题（"谁协调协调器"），增加复杂度。LLM 通过工具调用自行决定委派更灵活。 |
| **心跳机制** | JVM 进程内可用 ICancelToken + CompletableFuture 超时，不需要跨进程心跳。 |

## 3. Actor 模型核心概念

### 3.1 AgentActor

每个 Agent 实例运行时是一个 **Actor**：

```
┌──────────────────────────────────────┐
│            AgentActor                 │
│                                      │
│  ┌──────────┐  ┌──────────────────┐  │
│  │ Mailbox  │  │  Execution Loop  │  │
│  │(IMailbox)│  │  (ReAct 循环)    │  │
│  └────┬─────┘  └────────┬─────────┘  │
│       │                 │            │
│       └─────→ dispatch ─┘            │
│                                      │
│  State:                              │
│  - actorId (UUID)                    │
│  - agentModel (配置)                  │
│  - session (持久化会话)               │
│  - mailbox (IMailbox: DeferredAck)   │
│  - status: created|ready|running|idle|  │
│           failed|recovering|stopped     │
│  - cancelToken (取消令牌)             │
│  - parentActorId (可选, 子 Agent)     │
│  - tenantId, userId (多租户)          │
│  - createdAt, lastActiveAt            │
└──────────────────────────────────────┘
```

**Mailbox 契约（L4-5 已落地）**：`IMailbox` 是 Agent 域的 deferred-ack 邮箱原语，实现 3-phase reservation 协议（`offer` 投递 → `poll` 取出并标记 in-flight → `ack`/`nack` 确认/拒绝）。`DeferredAckMailbox` 是 in-memory 功能实现（线程安全、可配置容量、nack 重投递、达 `maxDeliveryAttempts` 转 dead-letter）；`NoOpMailbox` 是显式 no-op 默认（offer=false / poll=null / ack,nack=false）。`MailboxMessageHandler` 适配器将 `IMailbox` 经现有 `IAgentMessenger.registerHandler` 接入消息流（消息到达 inbox topic 时 offer 到邮箱），不改 `IAgentMessenger` 接口。Actor 执行循环（poll → process → ack）的消费侧是 L4-8 的独立 successor。

**关键约束**：
- 每个 Actor 在**单个 Virtual Thread** 上执行，内部无并发
- Actor 状态变更通过消息驱动，不暴露可变字段
- Actor 之间通过 `IMessageService` 通信，不共享可变状态
- Actor 可挂起等待外部响应（LLM 调用、工具执行），挂起时释放 Virtual Thread

### 3.2 Actor 生命周期

```
                    submit(request)
                         │
                         ▼
  ┌──────┐    ┌──────┐    ┌──────┐    ┌──────┐
  │created│───→│ready │───→│running│───→│idle  │
  └──────┘    └──┬───┘    └──┬───┘    └──┬───┘
                 │           │           │
                 │   error   │  timeout  │  new message
                 │           ▼           │  / steering
                 │       ┌────────┐      │
                 │       │failed  │      │
                 │       └────────┘      │
                 │           │           │
                 │       recovery        │
                 │           │           │
                 │           ▼           │
                 │       ┌────────┐      │
                 │       │recovering│─────┘
                 │       └────────┘
                 │           │
                 │     completed │
                 │           ▼
                 │       ┌────────┐
                 └──────→│stopped │
                         └────────┘
```

- **created** → 接收初始请求，加载配置，初始化 Session
- **ready** → 准备就绪，进入执行
- **running** → ReAct 循环执行中（可能挂起等待 LLM/工具响应）
- **idle** → 循环结束，等待新消息（steering/followUp）
- **failed** → 不可恢复错误，等待恢复或人工干预
- **recovering** → 自动恢复中（重放消息、恢复 Session 状态）
- **stopped** → 最终状态

**状态机落地范围（plan 218 / L4-8 已落地）**：foundational slice 实现核心转换 `CREATED → READY → RUNNING ↔ IDLE → STOPPED` + `any → FAILED`（消费循环异常显式 log + 状态转换，非静默吞没）。`FAILED → RECOVERING → RUNNING/IDLE` 自动恢复转换由 RecoveryManager（Phase 4 successor）驱动，`RECOVERING` 状态已在 `AgentActorStatus` 枚举中预留并经 registry 识别，foundational slice 不实现自动恢复。`AgentActorStatus` 为 7 值枚举（`CREATED` / `READY` / `RUNNING` / `IDLE` / `FAILED` / `RECOVERING` / `STOPPED`），Javadoc 明确每个状态的进入条件与合法转换（状态转换图写入 Javadoc）。

### 3.3 Actor 间通信

```
AgentActor A (Lead)                AgentActor B (Worker)
    │                                    │
    ├── call-agent(request) ─────────────→ B.mailbox.offer(message)
    │                                    │
    │   A 挂起 (Virtual Thread park)      │ B 开始执行
    │                                    │
    │                                    ├── ... ReAct loop ...
    │                                    │
    │   B 完成后回复                       │
    │←──── B.mailbox.offer(result) ──────┘
    │                                    │
    │ A 恢复 (Virtual Thread unpark)      │
```

**通信模型**：
- **同步请求-响应**：`call-agent` 工具，Caller 挂起等待 Callee 完成。（MVP 已交付：fork+exec 模型，`CallAgentExecutor` 直接调用 `IAgentEngine.execute()` 同步执行子 Agent。上述 Actor mailbox 模型是 Actor Runtime Phase 2 的目标。）
- **异步消息**：`send-message` 工具，发后即忘，Callee 在下一轮 ReAct 循环中读取。（已交付：`SendMessageExecutor` 通过 `IAgentMessenger.send()` 向目标 inbox topic 投递 ASYNC 信封。）
- **广播**：Lead Agent 可广播给所有团队成员

**实现机制**：
- 使用 `CompletableFuture` 作为 Actor 间的同步原语
- 使用 `IMessageService` (nop-message) 的 `LocalMessageService` 作为异步消息通道
- Topic 命名：`agent.{actorId}.inbox`，支持通配符 `agent.{teamId}.*`

## 4. 分层架构

### 4.1 完整分层

```
┌─────────────────────────────────────────────────┐
│                 API Layer                        │
│   GraphQL/RPC (IBizObject) + REST + SSE         │
├─────────────────────────────────────────────────┤
│              Platform Layer (本篇新增)            │
│  ┌──────────────┐  ┌────────────────────────┐   │
│  │ ActorRuntime │  │ TeamManager            │   │
│  │ (生命周期管理)│  │ (团队创建/解散/状态)    │   │
│  ├──────────────┤  ├────────────────────────┤   │
│  │ ActorRegistry│  │ RecoveryManager        │   │
│  │ (实例注册表) │  │ (崩溃恢复/超时清理)     │   │
│  ├──────────────┤  ├────────────────────────┤   │
│  │ MessageRouter│  │ ResourceGuard          │   │
│  │ (消息路由)   │  │ (协调信道/资源配额)     │   │
│  └──────────────┘  └────────────────────────┘   │
├─────────────────────────────────────────────────┤
│            Agent Engine Layer (已有)             │
│   ReAct 循环 · Session · Hook/Skill · Memory    │
├─────────────────────────────────────────────────┤
│            LLM Layer (已有)                      │
│   IChatService · Dialects · Streaming           │
├─────────────────────────────────────────────────┤
│            Tool Layer (已有)                     │
│   IToolExecutor (18 种) · IToolManager          │
├─────────────────────────────────────────────────┤
│           Infrastructure (Nop 平台)              │
│   IMessageService · IOrmSession · ICancelToken   │
│   Virtual Thread · nop-task · nop-stream          │
└─────────────────────────────────────────────────┘
```

### 4.2 Platform Layer 的六大组件

| 组件 | 职责 | Nop 平台映射 | 状态 |
|------|------|-------------|------|
| **ActorRuntime** | 创建/销毁 Actor 实例，管理 Virtual Thread 生命周期 | 基于 `GlobalExecutors` 的 Virtual Thread 池 | ✅ 基础层已落地（plan 218 / L4-8）：`IActorRuntime` 契约 + `InMemoryActorRuntime` 功能实现（专用单线程 executor，Java 11 兼容；Virtual Thread 优化为 successor 待模块迁移 Java 21）+ `NoOpActorRuntime` shipped 默认（`isEnabled()=false`，引擎据此跳过 Actor 路径，零回归）+ 引擎接线（`DefaultAgentEngine` 三个执行入口点经 `isEnabled()` guard opt-in 注册/注销 Actor） |
| **ActorRegistry** | 维护所有活跃 Actor 的注册表，按 tenantId/userId 隔离 | 内存 `ConcurrentHashMap` + DB 持久化索引 | ✅ 基础层已落地（plan 218 / L4-8）：`ActorRegistry` 契约 + `InMemoryActorRegistry` 双索引功能实现（`actorId → AgentActor` + `sessionId → actorId`，`ConcurrentHashMap` 线程安全）。foundational 身份维度 = actorId + sessionId + agentName。**Tenant 隔离已落地（plan 232 / L4-multi-tenant-isolation）**：`InMemoryActorRegistry` 构造期接收 `ITenantResolver`，`register` 时按当前 tenant 打标签（内部 tenant 标签 map，不改 `AgentActor` 构造器 / `IActorRuntime.createActor` 签名），`get`/`getBySession`/`getAll` 在 tenant 非空时按标签过滤；null tenant = 全部可见（零回归）。User 隔离（同租户内用户级过滤 + 团队共享例外）仍为 successor |
| **MessageRouter** | Actor 间消息路由，topic 匹配，背压控制 | `LocalMessageService` + topic 命名约定（L4-1 已落地：以 `LocalMessageService` 为底的 Agent 域 messenger `IAgentMessenger` 已可作为 MessageRouter 的路由基底，提供 inbox 直达投递 + 共享 reply topic 请求-响应；L4-2 已落地：`DBMessageService` 已可作为多实例路由基底——消息持久化到 `ai_agent_message` 表，跨进程投递经 DB 路由，至少一次语义） | — |
| **TeamManager** | Agent 团队的生命周期（创建/解散/成员管理/状态查询） | `@BizModel("AiTeam")` + ORM 实体持久化 | 🟡 部分落地：**TeamManager 基础层已落地（plan 223 / L4-8-team-manager）+ 团队通信工具 foundational 已落地（plan 225 / L4-8-team-tools）+ team-task-update 状态机 + DB-backed 共享任务表已落地（plan 227 / L4-8-team-task-update）+ Team ACL foundational 拦截层已落地（plan 228 / L4-team-acl-enforcement）**——`ITeamManager` 契约（create/disband/getTeam/getTeamBySession/getActiveTeams/addMember/removeMember/bindMemberSession/getMember）+ `NoOpTeamManager` shipped 默认（写操作抛 `UnsupportedOperationException`、读操作返回 empty，Minimum Rules #24 无静默跳过）+ `InMemoryTeamManager` 功能实现（`ConcurrentHashMap` 双索引 teamId + sessionId 反查，完整生命周期 + 状态机 CREATED→ACTIVE→DISBANDED）+ 引擎 `teamManager` 字段 + `setTeamManager` setter（null-safe 回退 NoOp）+ 4 个团队通信 IToolExecutor（`team-send-message`/`team-status`/`team-task-create`/`team-task-update`）+ `ITeamTaskStore` 契约 + `NoOpTeamTaskStore` shipped 默认 + `InMemoryTeamTaskStore` + `DbTeamTaskStore` 功能实现 + `ITeamAclChecker` 契约 + `NoOpTeamAclChecker` shipped 默认 + `DefaultTeamAclChecker`（§5.1 默认矩阵：LEAD=ADMIN / MEMBER=READ+WRITE+EXECUTE，abandon-unclaimed 为 ADMIN-only）+ `AgentToolExecuteContext` 接线（teamManager/teamTaskStore/teamAclChecker）。TeamSpec 程序化数据对象（不可变），声明式 `<team>`/`<team-member>` 配置化已落地（plan 231，见 §8.1）——引擎三入口点按声明自动建团 + 绑定，功能性 manager 未 wire 时声明 = fail-fast。**DB-backed 团队持久化已落地（plan 230 / L4-team-db-persistence）**：`DbTeamManager` raw JDBC 功能实现（`ai_agent_team` + `ai_agent_team_member` 两张共享表 + 构造期 `initSchema` 自动建表 + 条件 UPDATE CAS——CREATED→ACTIVE 首次绑定 exactly-once 激活 + *→DISBANDED 幂等 + `addMember` 唯一约束 duplicate 检测；跨进程共享同一 DB 即团队/成员/绑定/disband 互相可见）。读语义为**快照重建**（每次 `getTeam`/`getTeamBySession`/`getActiveTeams`/`getMember` SELECT 团队行+成员行重建新鲜 `Team`，区别于 `InMemoryTeamManager` 返回 live 可变对象——调用者对返回 member map 的修改不持久化，符合 ITeamManager 契约"returned members are read-only"）。`@BizModel` + ORM 实体 + GraphQL API 自动暴露（平台集成方向） / 完整 §5.1 ACL 模型（permissions override / 权限派生 / 完整 AclResource 枚举）/ 多租户 tenantId/userId 隔离 / `blockedBy` 依赖解析 / nop-task DAG 集成均为显式 successor。**声明式团队自动绑定已落地（plan 231 / L4-team-auto-binding）**：`<team>` / `<team-member>` 嵌入 `agent.xdef` + codegen `TeamModel`/`TeamMemberModel`/`TeamMemberRefModel` + `TeamModelConverter`（mutable→不可变 `TeamSpec`）+ 引擎三入口点 auto-bind（幂等 createTeam + bindMemberSession，NoOp 冲突 fail-fast）。|
| **RecoveryManager** | 崩溃恢复、超时清理、orphan 检测、消息重放 | 定时任务（`IScheduledExecutor`）+ DB 状态机 | 🟡 部分落地：**接管锁基础层 + 定时扫描 daemon 已落地（plan 221 / plan 222 / L4-8-P4）**——`ISessionTakeoverLock` 契约（`tryAcquire`/`release`/`isHeld`/`tryRenew`）+ `NoOpSessionTakeoverLock` shipped 默认 + `DbSessionTakeoverLock` 功能实现（独立 `ai_agent_session_lock` 表 + lease/TTL CAS acquire + conditional release + stale-lock 抢占）+ `DefaultAgentEngine` 三入口点接线 + `restorePendingSessions` `isHeld` 跳过增强。**定时扫描 daemon（plan 222）**：`IRecoveryManager` 契约（`start`/`stop` 幂等 + `scanOnce → RecoveryScanResult`）+ `NoOpRecoveryManager` shipped 默认 + `ScheduledRecoveryManager` 功能实现（`IScheduledExecutor` 周期调度默认 60s，`scanOnce` = stale lock cleanup（幂等 DELETE）+ orphan session detection（LOG.warn）+ `RecoveryScanResult`）+ 引擎 `recoveryManager` 字段 + `setRecoveryManager` setter（部署层管理 start/stop）。RecoveryManager 其余能力（orphan 进程主动 liveness 检测 / 超时强制中止 / 归档清理 / 心跳自动续约）仍为 successor；**恢复模式策略已落地（plan 226 / L4-8-P4-RecoveryStrategy）**：`IOrphanRecoveryHandler` + `RecoveryMode`(RESUME/ABORT/SKIP) + `DefaultOrphanRecoveryHandler` + daemon 集成（RETRY 为 successor）|
| **ResourceGuard** | 协调信道（scope_claim/conflict_alert，见 multi-agent.md §4）、资源配额、冲突检测 | `IResourceGuard` 配额决策网关 + `NoOpResourceGuard` shipped 默认 + `DefaultResourceGuard` 功能实现（构造期注入 `QuotaConfig`）；`InMemoryTeamManager`/`DbTeamManager`/`InMemoryActorRuntime` 构造期可选接收 guard，默认 NoOp 零回归 | 🟡 部分落地：**foundational count-based 并发配额切片已落地（plan 234 / L4-resource-guard-quota）**——`IResourceGuard` 契约（`checkConcurrent(QuotaDimension, scopeKey, projectedCount, overrideLimit) → QuotaDecision`）+ `QuotaDimension` 枚举（三值 `TEAM_PARALLEL_BOUND_MEMBERS` / `TEAM_MEMBERS` / `CONCURRENT_ACTORS_PER_TENANT`）+ `QuotaDecision` 不可变结果对象（allow/deny 工厂 + dimension/scopeKey/limit/projectedCount/reason）+ `QuotaConfig` 不可变配置对象（teamMaxMembers 默认 8 / tenantMaxConcurrentActors 默认 10，`<= 0` = unlimited）+ `NoOpResourceGuard` shipped 默认（恒 allow = 零回归）+ `DefaultResourceGuard` 功能实现（override 优先 + QuotaConfig 兜底，limit `<= 0` = unlimited allow，projectedCount > limit → deny）。三类维度 enforcement 接线：`InMemoryTeamManager`/`DbTeamManager` 的 `createTeam`/`addMember`（TEAM_MEMBERS config-driven）+ `bindMemberSession`（TEAM_PARALLEL_BOUND_MEMBERS per-team override `maxParallelMembers` hint→enforced 升级）；`InMemoryActorRuntime.createActor`（CONCURRENT_ACTORS_PER_TENANT，scopeKey = `ITenantResolver` 解析的 tenant，经 registry tenant 标签派生活跃 Actor 计数，null tenant = 全局单桶）。denial = fail-fast 抛 `NopAiAgentException`（engine-internal 编排路径，英文消息含 dimension/scopeKey/limit/projectedCount）。**协调信道（scope_claim/conflict_alert）+ 时间窗 rate-limit（LLM 调用频率 / Compaction 配额池）+ storage 配额 + per-agent token/时间累积上限 + Fencing Token** 仍为显式 successor |

## 5. 多用户与资源隔离

### 5.1 多租户模型

```
ActorRuntime
  │
  ├── tenant: "tenant-A"
  │     ├── user: "user-1" → Actor[plan-001] (running)
  │     │                    Actor[coder-002] (idle)
  │     └── user: "user-2" → Actor[reviewer-003] (running)
  │
  └── tenant: "tenant-B"
        └── user: "user-3" → Actor[coder-004] (running)
```

**隔离维度**：
- **Tenant 隔离**：不同租户的 Actor 完全不可见，DB 查询自动加 tenantId 条件。**已 foundational 落地（plan 232 / L4-multi-tenant-isolation）**：全部 10 张 `ai_agent_*` 表新增 nullable `TENANT_ID` 列；contextual tenant 解析机制（`ITenantResolver` + thread-local backed `ThreadLocalTenantResolver` + `NullTenantResolver` shipped 默认）；全部 DB store 类（raw JDBC）在 tenant 非空时注入 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)` WHERE + INSERT 写入 tenant；`DBSessionStore` tenant 非空时 cache bypass + MERGE KEY 变为 `(SESSION_ID, TENANT_ID)`；`InMemoryActorRegistry` 内部 tenant 标签 map + 查询过滤（不改 `AgentActor` 构造器 / `IActorRuntime.createActor` 签名）；`DefaultAgentEngine` 异步入口点在 `supplyAsync` worker lambda 内 set/clear tenant context（从 `Principal.tenantId` null-safe 解析）；null tenant = 全部数据可见（零回归）。注意：模块运行时 DB 操作是 raw JDBC，不享受 Nop ORM 的 auto-tenantId 机制——WHERE 注入手工实现。Nop ORM XML（4 实体）已同步追加 `TENANT_ID` 列。
- **User 隔离**：同一租户内的不同用户，只能看到自己的 Actor（除非通过团队共享）。**仍为显式 successor**（依赖 userId propagation 标准化 + 团队共享数据例外语义）。
- **团队共享**：Team 内的 Actor 对团队成员可见，通过 Team 的 ACL 控制

**Team ACL 模型**：

```
TeamAclEntry:
  - teamId: String
  - actorRole: ActorRole (LEAD | MEMBER)
  - resource: AclResource
  - actions: Set<AclAction>

AclResource 枚举:
  SESSION, PLAN, TOOL_EXECUTION, FILE_SCOPE, MESSAGE_CHANNEL

AclAction 枚举:
  READ, WRITE, EXECUTE, ADMIN
```

**默认 ACL 规则**：

| 角色 | SESSION | PLAN | TOOL_EXECUTION | FILE_SCOPE | MESSAGE_CHANNEL |
|------|---------|------|----------------|------------|-----------------|
| LEAD | ADMIN | ADMIN | ADMIN | ADMIN | ADMIN |
| MEMBER | READ (own), WRITE (own) | READ | EXECUTE | WRITE (assigned) | READ + WRITE |

**权限派生**：子 Actor 继承父 Actor 的 ACL 规则，`LEAD` 可以通过 `permissions` override 收紧成员权限（只能收紧，不能放款）。

**Fencing Token 协议**：解决并发写入冲突：

```
FencingToken:
  - actorId: String
  - monotonicCounter: long
  - issuedAt: long (epoch ms)

使用规则：
  1. Actor 每次 scope_claim 时附带当前 fencing token
  2. ResourceGuard 校验：token.counter 必须严格递增
  3. 收到过期 token（counter <= 已记录值）→ 拒绝操作，广播 conflict_alert
  4. Actor 恢复后重新获取 fencing token（counter 重置为 DB 中最大值 + 1）
```

Fencing token 的实现依赖 DB 的原子 CAS 操作（`UPDATE ... SET counter = ? WHERE counter = ?`），不需要分布式锁。

> **Fencing Token foundational 原语已落地（plan 235 / L4-fencing-token）**：`FencingToken` 不可变数据对象（actorId + monotonicCounter + issuedAt，per §5.1 lines 252-267 + glossary line 65）+ `IFencingTokenService` 中央单调计数器决策网关契约（`issue(actorId) → FencingToken` 原子递增单调计数器返回新 token，首次 = 1；`validate(token) → FencingTokenDecision` strictly greater 校验 + 高水位更新）+ `FencingTokenDecision` 不可变结果对象（valid/stale + actorId + presentedCounter + recordedCounter + reason，valid⇒null-reason / stale⇒non-null-reason 不变量；stale reason = "fencing token stale: presented X <= recorded Y"）+ `NoOpFencingTokenService` shipped 默认（singleton，validate 恒 valid = 零回归；issue 返回 placeholder token counter=0 intentional disabled-mode）+ `DefaultFencingTokenService` functional in-memory CAS 实现（per-actor 双计数器：issue 计数器 `AtomicLong.incrementAndGet` + recorded 高水位 CAS loop only-if-greater；线程安全，无 lost update）。validate 返回 decision（非 throw）——enforcement-point 反应（throw `NopAiAgentException` / 广播 conflict_alert / 拒绝操作）为 consumer 责任（Decision §6，与 `IResourceGuard.checkConcurrent → QuotaDecision` / `ITeamAclChecker.checkAccess → TeamAclDecision` 一致）。in-memory CAS 防护同 JVM 内并发 Actor 写冲突（vision §2.3 单 JVM 基线）。无 engine 顶层接线（无 wired consumer，setter 预留给首个 consumer successor）。22 focused 测试全绿（contract / 高水位单调 / 并发 issue 唯一计数 / 并发 validate 不回退 / 原语生命周期 issue→valid→issue→stale / NoOp 零回归）。**下列仍为显式 successor**：scope_claim 协调信道集成（规则 1）/ conflict_alert 广播（规则 3）/ Compaction·快照写入集成（§6.2 line 311）/ DB-backed 跨进程 CAS（line 267 `UPDATE ... SET counter = ? WHERE counter = ?`，依赖存储裁定 + ORM model plan-first）/ Actor 恢复后重新获取 token（规则 4，依赖 DB-backed impl）/ 分支亲和调度注册集成 / engine 顶层 `setFencingTokenService` 接线（首个 consumer）/ ResourceGuard fencing 校验集成（规则 2）。

> **Team ACL foundational 拦截层已落地（plan 228 / L4-team-acl-enforcement）**：`ITeamAclChecker` 契约（`checkAccess(teamId, callerSessionId, toolName, action) → TeamAclDecision`）+ `TeamAclAction` 枚举（READ/WRITE/EXECUTE/ADMIN = §5.1 `AclAction` 子集）+ `TeamAclDecision` 不可变数据对象（allow/deny 工厂 + reason + resolvedRole）+ `DefaultTeamAclChecker` 功能实现（构造期接收 `ITeamManager`，运行时 getTeam → 遍历 members 按 sessionId 匹配 → 取 role → 静态 §5.1 默认矩阵查表 → LEAD 全通过 / MEMBER 通过非 ADMIN）+ `NoOpTeamAclChecker` shipped 默认（恒 `allow(null)` = 不增加授权限制，零回归）。引擎全链路接线（`AgentToolExecuteContext.teamAclChecker` final 字段 + 17 参 endpoint 构造器，`DefaultAgentEngine.teamAclChecker` 字段 + `setTeamAclChecker` null-safe 回退 + `resolveExecutor` Builder 传递，`ReActAgentExecutor` 字段→构造→Builder→context 构造全链路）。4 个团队工具 executor（team-send-message / team-status / team-task-create / team-task-update）均在团队解析后、实际操作前调用 checker，denial 返回诚实策略反馈（status="success" + JSON body `{allowed:false, reason, resolvedRole}`，不中断 ReAct 循环）。**唯一 MEMBER 被拒绝的操作** = abandon-unclaimed（放弃 CREATED 状态未认领任务，required ADMIN——从任务池移除未开始工作是团队管理决策）。完整 §5.1 ACL 模型的下列维度仍为显式 successor：TeamSpec `permissions` override（per-team/per-member 收紧）、权限派生（父子 Actor ACL 继承）、完整 `AclResource` 枚举（SESSION/PLAN/TOOL_EXECUTION/FILE_SCOPE/MESSAGE_CHANNEL 5 资源 × action 矩阵）、多租户 tenantId/userId 隔离（DB 自动加 tenantId 条件）、DB-backed `TeamAclEntry` 表持久化、Fencing Token、ResourceGuard 配额强制。

### 5.2 资源配额

| 资源 | 配额维度 | 默认值 | 强制方式 | 状态 |
|------|---------|--------|---------|------|
| 并发 Actor 数 | (tenant) | 10 | `IResourceGuard`/`CONCURRENT_ACTORS_PER_TENANT` — `InMemoryActorRuntime.createActor` 创建前经 `ITenantResolver` 解析 tenant scopeKey + 经 registry tenant 标签派生活跃 Actor 计数检查 | ✅ foundational 已落地（plan 234） |
| 团队最大成员数 | (teamModel) | 8 | `IResourceGuard`/`TEAM_MEMBERS` — `InMemoryTeamManager`/`DbTeamManager` 的 `createTeam`/`addMember` 检查（config-driven `QuotaConfig.teamMaxMembers`） | ✅ foundational 已落地（plan 234） |
| 团队最大并行绑定成员数 | (team) | `maxParallelMembers`（per-team override，`<= 0` = unlimited） | `IResourceGuard`/`TEAM_PARALLEL_BOUND_MEMBERS` — `bindMemberSession` 绑定前计数 `isBound()==true` 成员检查（`TeamSpec.maxParallelMembers` hint→enforced 升级） | ✅ foundational 已落地（plan 234） |
| 单 Agent 最大迭代 | (agentModel) | 50 | ReAct 循环内检查（`AgentExecutionContext.maxIterations` 默认 10 已部分满足） | successor（per-agent 累积） |
| 单 Agent 最大 Token | (agentModel) | 200K | 每次 LLM 调用后累加检查 | successor（需 per-agent token 累积追踪） |
| 单 Agent 最大时间 | (agentModel) | 30min | ICancelToken 超时 | successor（需时间预算） |
| LLM 调用频率 | (tenant) | 100/min | `IRateLimiter` | successor（需时间窗语义） |
| Compaction LLM 调用 | (tenant) | 20/min | 使用便宜模型，独立配额池 | successor（依赖 rate-limit） |

配额通过 Nop 配置系统管理（`@cfg:ai.agent.quota.*`），支持 Delta 定制。

> **Foundational count-based 并发配额切片已落地（plan 234 / L4-resource-guard-quota / Phase 5）**：中央 `IResourceGuard` 配额决策网关（`checkConcurrent(QuotaDimension, scopeKey, projectedCount, overrideLimit) → QuotaDecision`，override `> 0` 优先 / `<= 0` 回退 `QuotaConfig` 全局默认，resolved limit `<= 0` = unlimited allow）+ `QuotaDimension` 枚举（三值）+ `QuotaDecision` 不可变结果对象（allow/deny 工厂）+ `QuotaConfig` 不可变配置对象（teamMaxMembers 默认 8 / tenantMaxConcurrentActors 默认 10）+ `NoOpResourceGuard` shipped 默认（恒 allow = 零回归）+ `DefaultResourceGuard` 功能实现。三类维度 enforcement：`InMemoryTeamManager`/`DbTeamManager` 的 `createTeam`/`addMember`（TEAM_MEMBERS）+ `bindMemberSession`（TEAM_PARALLEL_BOUND_MEMBERS，`maxParallelMembers` 从被动 hint 升级为 enforced，消费既有持久化字段）+ `InMemoryActorRuntime.createActor`（CONCURRENT_ACTORS_PER_TENANT，per-tenant scope 独立桶，复用 plan 232 tenant 解析 + registry 标签基础设施）。denial = fail-fast 抛 `NopAiAgentException`（engine-internal 编排路径），端到端经 `engine.execute` → auto-bind → `bindMemberSession` → `IResourceGuard.checkConcurrent` → denial 完整链路验证。**以下仍为显式 successor**：LLM 调用频率 rate-limit（时间窗 token-bucket，`IRateLimiter`）/ Compaction 配额池 / storage 配额（需 metrics 基础设施）/ per-agent token/时间累积上限 / Fencing Token（monotonic counter，独立 carry-over）/ ResourceGuard 协调信道（scope_claim/conflict_alert）/ DB 持久化配额计数器（跨进程共享）。

## 6. 自动恢复

### 6.1 恢复策略

| 异常场景 | 检测方式 | 恢复动作 |
|----------|---------|---------|
| LLM 调用超时 | `CompletableFuture.orTimeout()` | 重试（最多 3 次，指数退避） |
| LLM 返回畸形响应 | 响应解析失败 | 标记工具调用失败，注入错误消息让 LLM 重新生成 |
| 工具执行异常 | `IToolExecutor` 抛出异常 | 取决于工具的 `critical` 标记：critical→中止，非 critical→注入错误继续 |
| Agent 循环超过最大迭代 | 步数计数器 | 强制中止，生成摘要 |
| Actor 所在 Virtual Thread 被 Interrupt | `ICancelToken` | 保存 Session 快照，标记 `interrupted`，等待恢复 |
| JVM 进程崩溃重启 | DB 中 Actor 状态为 `running` 但进程已重启 | RecoveryManager 扫描，根据策略恢复（首版 RESUME/ABORT/SKIP，plan 226 已落地；RETRY 为 successor） |
| 子 Agent 崩溃 | 子 Actor 状态变为 `failed` | 通知父 Actor，父 Actor 决定重试或放弃 |

### 6.2 持久化策略

**Session 快照**（已有设计，见 `nop-ai-agent-session-and-storage.md`）：
- 初始阶段使用文件系统保存 Session 状态（见 session-and-storage.md §2），DB 持久化是后续演进
- Actor 状态变更（status 转换、compaction 边界）立即写入 DB（事务保护）
- `running` → `idle` / `failed` / `stopped` 都有对应的 DB 记录
- 消息增量以 append-only event log 写入（非每次迭代完整快照）
- 完整快照仅在 compaction 边界生成（CompactionEntry），非每次 ReAct 迭代
- 邮箱中的未处理消息持久化到 DB（不怕进程崩溃丢消息）
- **Fencing Token**：Compaction 和快照操作附带 fencing token，防止并发写入导致状态不一致

**Actor 状态持久化**：
- Actor 状态变更（status 转换）立即写入 DB（事务保护）
- `running` → `idle` / `failed` / `stopped` 都有对应的 DB 记录
- 邮箱中的未处理消息持久化到 DB（不怕进程崩溃丢消息）

### 6.3 RecoveryManager 工作流

> **接管锁基础层 + 定时扫描 daemon 已落地（plan 221 / plan 222 / L4-8-P4）**：`ISessionTakeoverLock` 契约 + `NoOpSessionTakeoverLock` shipped 默认（单进程部署零回归，依赖既有 `runningExecutions.putIfAbsent`）+ `DbSessionTakeoverLock` 功能实现（独立 `ai_agent_session_lock` 表 + lease/TTL CAS acquire + conditional release + stale-lock 抢占 + opt-in 编程式 API `tryAcquire`/`release`/`isHeld`/`tryRenew`）+ `DefaultAgentEngine` 三入口点接线（`instanceId` + `sessionTakeoverLock` + `tryAcquire` before `putIfAbsent` + 全路径 `releaseLockQuietly`）+ `restorePendingSessions` `isHeld` 跳过增强。**定时扫描 daemon 已落地（plan 222）**：`IRecoveryManager` 契约（`start`/`stop` 幂等生命周期 + `scanOnce → RecoveryScanResult` 手动触发）+ `NoOpRecoveryManager` shipped 默认（`scanOnce` 返回全零值，零回归）+ `ScheduledRecoveryManager` 功能实现（经 `IScheduledExecutor`（nop-commons）`scheduleWithFixedDelay` 注册周期任务，默认 60s fixed-delay；`scanOnce` 执行 stale lock cleanup（DELETE `ai_agent_session_lock` WHERE `LOCK_EXPIRES_AT <= now`，幂等清理）+ orphan session detection（SELECT `ai_agent_session` 中 `STATUS IN ('running','pending')` 且无活跃锁的 session，LOG.warn 记录）+ 返回 `RecoveryScanResult`）+ `DefaultAgentEngine` `recoveryManager` 字段（默认 NoOp）+ `setRecoveryManager` setter（引擎不调用 start/stop——遵循部署层生命周期管理设计契约，集成商在部署层调用）。**调度机制裁定**：采用 `IScheduledExecutor`（nop-commons，已传递可用）而非 nop-job `IJobScheduler`（重量级，本模块无 nop-job Maven 依赖）——对"每 60s 跑一次 scanOnce"的简单周期任务足够；nop-job 集成（DB-backed job persistence / cluster coordination / cron expression）为后续 successor 增强方向。RecoveryManager 的其余能力（orphan 进程主动 liveness 检测 / 恢复模式策略 resume-retry-abort / 超时强制中止 / 归档清理 / 心跳自动续约 / 自动触发恢复）均为显式 successor，各自在下表中标注。

> **恢复模式策略已落地（plan 226 / L4-8-P4-RecoveryStrategy）**：`IOrphanRecoveryHandler` 可插拔策略契约（`handleOrphan(sessionId) → RecoveryOutcome`）+ `NoOpOrphanRecoveryHandler` shipped 默认（SKIP 模式——LOG.warn orphan session ID，零行为回归，与 plan 222 shipped 默认一致）+ `DefaultOrphanRecoveryHandler` 功能实现（按配置的 `RecoveryMode` 分发：RESUME 委托 `engine.restoreSession` fire-and-forget + takeover 锁防 double-execution / ABORT raw JDBC `UPDATE ai_agent_session SET STATUS='failed' WHERE SESSION_ID=? AND STATUS IN ('running','pending')` 条件 WHERE / SKIP LOG.warn）+ `RecoveryMode` 枚举（RESUME/ABORT/SKIP）+ `RecoveryOutcome` 数据对象（sessionId/mode/succeeded/message）+ `ScheduledRecoveryManager` 集成（`orphanRecoveryHandler` 字段默认 NoOp + `setOrphanRecoveryHandler` setter + `scanOnce` orphan detection 后对每个 orphan 调 `handler.handleOrphan` 汇总到 `RecoveryScanResult.recoveryActions`）+ `RecoveryScanResult` 扩展（`recoveryActions: List<RecoveryOutcome>` 字段 + 6-arg 构造器 + `getRecoveryActions()` getter + `empty()` 返回 emptyList）。**裁定**：首版交付 RESUME + ABORT + SKIP；RETRY（清空 session 状态从头重试）为独立 successor——需要 `ISessionStore` 契约变更（reset 方法）+ 工具副作用 idempotency 裁定。**Wiring 裁定**：handler 仅 live 在 `ScheduledRecoveryManager` 上（经 `setOrphanRecoveryHandler` setter 注入），`DefaultAgentEngine` 不新增 handler 字段——handler 是 recovery manager 的内部策略，不是引擎层配置点。RESUME 模式的安全性依赖 plan 221 的跨进程接管锁（`restoreSession` 内部 `tryAcquire` 防止 double-execution，handler 捕获同步失败返回 succeeded=false outcome——非静默）。orphan 进程主动 liveness 检测 / 超时强制中止 / 归档清理 / 心跳自动续约仍为独立 successor。

> **超时强制中止已落地（plan 229 / L4-8-P4-TimeoutAbort）**：`ISessionTimeoutHandler` 可插拔策略契约（`handleTimeout(sessionId) → TimeoutOutcome`）+ `NoOpSessionTimeoutHandler` shipped 默认（SKIPPED 动作——LOG.warn 超时 session ID，零行为回归，与 plan 226 shipped 默认一致）+ `DefaultSessionTimeoutHandler` 功能实现（三分裁定经 raw JDBC `SELECT LOCK_OWNER, LOCK_EXPIRES_AT FROM ai_agent_session_lock` 直读锁表——设计裁定 1：`ISessionTakeoverLock.isHeld` 仅返回 boolean、不区分持有者，无法支撑本地/远端区分；LOCAL_CANCELLED 本实例锁 → `engine.cancelSession(sessionId, "timeout", true)` forced=true（封装 graceful + 线程 interrupt，终态 cancelled/forced_stopped）/ FORCE_FAILED 无活跃锁 → raw JDBC `UPDATE ai_agent_session SET STATUS='failed' WHERE SESSION_ID=? AND STATUS IN ('running','pending')` 条件 WHERE（与 plan 226 ABORT 一致）/ SKIPPED_REMOTE 远端实例锁 → LOG.warn 含 LOCK_OWNER 不干预，等其自身 daemon 处理或 lease 过期转 orphan）+ `TimeoutAction` 枚举（LOCAL_CANCELLED/FORCE_FAILED/SKIPPED_REMOTE/SKIPPED）+ `TimeoutOutcome` 数据对象（sessionId/action/succeeded/message）+ `ScheduledRecoveryManager` 集成（`sessionTimeoutHandler` 字段默认 NoOp + `setSessionTimeoutHandler` setter + `scanOnce` 步骤顺序重排：stale lock cleanup → **timeout detection** → orphan detection——设计裁定 3：timeout 步骤先于 orphan 使被强制标记 failed（terminal）的超时 session 自动被后续 orphan detection 排除，避免冲突）+ `RecoveryScanResult` 扩展（`timeoutActions: List<TimeoutOutcome>` 字段 + 7-arg 构造器 + `getTimeoutActions()` getter + `empty()` 返回 emptyList）。**活动时间戳列裁定（设计裁定 2）**：超时检测 SQL `SELECT SESSION_ID FROM ai_agent_session WHERE STATUS IN ('running','pending') AND UPDATED_AT < ?`（`?` = `now - timeoutSeconds`），`UPDATED_AT` 作为 lastActiveAt 代理——仍在迭代（每轮 ReAct 持久化刷新）的 session 不会误判超时，只有真正卡死/挂起的 session 才会超时，零 schema 变更。**Wiring 裁定**：handler 仅 live 在 `ScheduledRecoveryManager` 上（经 `setSessionTimeoutHandler` setter 注入），`DefaultAgentEngine` 不新增 handler 字段——handler 是 recovery manager 的内部策略，不是引擎层配置点。**不注入 `ISessionTakeoverLock`**（设计裁定 1）：其 `isHeld(sessionId)` 仅返回 boolean、Javadoc 明确"Does not distinguish owners"，无法支撑本地/远端区分；handler 经 raw JDBC 直读 `ai_agent_session_lock` 表（与 `ScheduledRecoveryManager` stale-lock-cleanup 直访锁表的既有模式一致）。per-agent / per-session 超时配置 / 跨进程取消传播（远端活跃超时 session 经 `IMessageService` 投递 cancel）/ 优雅等待 + 超时升级序列（graceful cancel → bounded wait → force，vision §6.3 三段式）/ orphan 进程主动 liveness 检测仍为独立 successor。

```
RecoveryManager (定时任务，每 60 秒)  [调度层已落地：IScheduledExecutor scheduleWithFixedDelay，plan 222]
  │
  ├── 1. 扫描 DB 中 status=running 的 Actor  [部分落地：plan 222 交付 stale lock cleanup + orphan session detection（DB 可观测信号），不检测进程存活]
  │     └── 检查进程 ID 是否存活  [successor：orphan 进程主动 liveness 检测]
  │           ├── 当前进程的 Actor → 检查 Virtual Thread 是否活跃
  │           └── 其他进程的 Actor → 标记 orphaned  [部分落地：plan 222 LOG.warn 记录 orphan session，不自动恢复]
  │
  ├── 2. 处理 orphaned Actor  [部分落地：plan 226 交付恢复模式策略 RESUME/ABORT/SKIP（IOrphanRecoveryHandler 可插拔策略 + DefaultOrphanRecoveryHandler 功能实现 + daemon 集成）；RETRY（从头重试）为 successor]
  │     ├── 恢复模式=resume → 从最近 CompactionEntry 边界恢复（summary + 保留消息）  [落地：RESUME 委托 engine.restoreSession（fire-and-forget）]
  │     ├── 恢复模式=retry → 从头重试整个请求  [successor：需 ISessionStore reset 语义 + 工具副作用 idempotency 裁定]
  │     ├── 恢复模式=abort → 标记 failed，通知调用者  [落地：ABORT raw JDBC UPDATE status=failed]
  │     └── 恢复模式=skip → LOG.warn 观测，不恢复（shipped 默认 NoOpOrphanRecoveryHandler）  [落地]
  │
  ├── 3. 处理超时 Actor  [已落地：plan 229 交付超时强制中止（ISessionTimeoutHandler 可插拔策略 + DefaultSessionTimeoutHandler 三分裁定 LOCAL_CANCELLED/FORCE_FAILED/SKIPPED_REMOTE + NoOpSessionTimeoutHandler shipped 默认 SKIPPED + daemon scanOnce timeout detection 步骤先于 orphan）]
  │     └── status=running/pending 且 UPDATED_AT 超过 timeoutSeconds（全局可配置）
  │           → 三分裁定：本实例活跃锁 → cancelSession(forced=true)；孤儿（无活跃锁）→ raw UPDATE status=failed；远端活跃锁 → LOG.warn 不干预
  │           [本地经 cancelSession 一次调用完成 graceful+interrupt；分立的"cancel → 等待优雅停止 → 强制标记"三段式为 successor]
  │           [per-agent maxWallClockMinutes / 跨进程取消传播 / orphan liveness 为 successor]
  │
  └── 4. 清理已停止的 Actor  [successor：归档清理]
        └── status=stopped 且 stoppedAt 超过 24h
              → 归档到历史表 → 从活跃表删除
```

## 7. 轻量级启动

### 7.1 冷启动优化

| 优化点 | 实现方式 |
|--------|---------|
| Agent 配置缓存 | `AgentModel` 从 XDSL 加载后缓存在内存，不重复解析 |
| LLM Dialect 缓存 | `LlmDialectFactory` 缓存已创建的 Dialect 实例 |
| Virtual Thread 调度 | 不需要预分配线程，按需创建，创建成本 ~1μs |
| Session 懒加载 | Session 快照在 Actor 首次需要时才从 DB 加载 |
| 工具注册表共享 | `IToolExecutorProvider` 是进程级单例，所有 Actor 共享 |

### 7.2 预期性能

| 指标 | 目标 | 依据 |
|------|------|------|
| Actor 创建延迟 | < 10ms | Virtual Thread 创建 + 配置加载 + DB 写入 |
| 单 JVM 并发 Actor | 1000+ | Virtual Thread 轻量级，瓶颈在 LLM API 调用频率 |
| 消息传递延迟 | < 1ms | 内存队列（`LocalMessageService`），无序列化 |
| 恢复延迟 | < 5s | DB 读取 + Session 快照加载 + ReAct 循环恢复 |

## 8. 团队模式（Team Mode）

### 8.1 TeamSpec

> **声明式 `<team>` / `<team-member>` 已落地（plan 231 / L4-team-auto-binding）**：团队定义经 lead agent 的 `.agent.xml` 可选 `<team>` 元素声明（嵌入 `agent.xdef`，非独立 `team-spec.xdef` 文件——`loadAgentModel` 是唯一 chokepoint，嵌入与既有 `<permissions>`/`<constraints>` 模式一致，Delta 定制经既有 `xdsl-loader` 天然支持）。codegen 生成 mutable `TeamModel`/`TeamMemberModel`（`io.nop.ai.agent.model` 包），经 `TeamModelConverter` 映射到既有不可变 `TeamSpec`/`TeamMemberSpec`（plan 223 契约不变；转换器保证 `leadAgentName` 以 `role=LEAD` 出现在 memberSpecs）。成员 agent 经可选 `<team-member teamName memberName/>` 声明归属。引擎三入口点（doExecute/resumeSession/restoreSession）按声明自动调用 `teamManager.createTeam` + `bindMemberSession`（幂等），无需任何程序化调用——功能性 manager 未 wire 时声明存在 = fail-fast（No Silent No-Op）。
>
> 本计划首版只交付建团 + 绑定所需的最小子集（teamName/description/leadAgentName/member{name,agentModel,role}/maxParallelMembers hint）。下方更丰富的字段（`kind=category|direct` / `category` 路由 / `prompt` 成员 prompt 覆盖 / `permissions` 成员权限覆盖 / `maxWallClockMinutes` / `maxMessagesPerRun`）均为独立 successor。

团队通过 XDSL 配置定义：

```
agent.xdef 的 <team> 元素（已落地子集 + successor 字段）:
  - teamName: 团队名称                              ✅ 已落地
  - description: 描述                               ✅ 已落地
  - leadAgentName: Lead Agent 的 memberName          ✅ 已落地
  - members[]: 成员列表                              ✅ 已落地
    - name: 成员名称                                 ✅ 已落地
    - agentModel: Agent 配置引用                     ✅ 已落地
    - role: LEAD | MEMBER                           ✅ 已落地
    - kind: category | direct                        🔲 successor
    - category: (当 kind=category 时路由)             🔲 successor
    - prompt: 成员特定的 prompt 覆盖                 🔲 successor
    - permissions: 成员的权限覆盖                    🔲 successor
  - maxParallelMembers: 最大并行成员数 (hint, 不强制)  ✅ 已落地
  - maxWallClockMinutes: 最大执行时间                🔲 successor
  - maxMessagesPerRun: 最大消息数                    🔲 successor
```

### 8.2 团队通信模型

> **Foundational 工具已落地（plan 225 / L4-8-team-tools）**：`team-send-message` / `team-status` / `team-task-create` 三个 IToolExecutor 已交付为 in-memory foundational slice——工具经 `AgentToolExecuteContext` 访问 `ITeamManager` + `ITeamTaskStore`，`team-send-message` 经 `IAgentMessenger.send()` 投递到成员 inbox topic，`team-task-create` 在 `InMemoryTeamTaskStore` 创建任务，`team-status` 返回结构化 JSON（team + members + taskCount）。
>
> **任务状态机 + DB-backed 共享任务表已落地（plan 227 / L4-8-team-task-update）**：`team-task-update` IToolExecutor（`claim` / `complete` / `abandon` 三动作，大小写不敏感）经 `ITeamTaskStore.claimTask/completeTask/abandonTask` 驱动 `TeamTaskStatus` 状态机（CREATED→CLAIMED→COMPLETED、CREATED/CLAIMED→ABANDONED）。`InMemoryTeamTaskStore` 经 `ConcurrentHashMap.compute` CAS、`DbTeamTaskStore` 经 raw JDBC 条件 UPDATE on STATUS（affected-row-count 判定，构造期 `initSchema` 自动建表）实现并发认领 CAS——至多一个认领者胜出。`TeamTask.claimedBy` 记录认领者 sessionId（complete/abandon 保留不改写）。NoOp shipped 默认零回归（NoOp 转换抛 UOE、工具诚实报告）。
>
> **nop-task DAG 集成已落地（plan 233 / L4-nop-task-dag-integration）**：团队任务可组织为 nop-task 工作流 DAG 图——`TeamTaskGraphBuilder` 把团队任务集映射为 nop-task 内存 `GraphTaskStepModel`（每任务一节点，`blockedBy`→`waitSteps`，enter/exit 推导），真实经 nop-task `GraphStepAnalyzer` 环检测（成环 blockedBy 快速失败，闭合「今日静默存储成环」gap）；`TeamTaskTopology` 暴露就绪/阻塞拓扑查询。`TeamTaskFlowOrchestrator` 是依赖序同步编排器——真实经 nop-task 运行时（`ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()`）执行 `GraphTaskStep` DAG 调度，每个图节点委派已绑定成员 agent（经 `IAgentEngine.execute` 同步 join），成功后 `completeTask` 标记 COMPLETED；nop-task 图调度器保证 B blockedBy A 则 A 完成后 B 才执行（端到端 Anti-Hollow 验证）。这是 vision §2.2「ITaskStep + Decorator — Agent 编排的 DAG 执行」方向的 DAG 拓扑 + 同步编排切片落地（详见 `nop-ai-agent-task-flow-integration.md`）。
>
> **blockedBy 自动调度守护进程已落地（plan 236 / L4-blockedBy-resolution-engine）**：`ITeamTaskSchedulerDaemon` + `TeamTaskSchedulerDaemon` 是定时扫描就绪任务自动 claim/派发的守护进程——每周期经 `TeamTaskTopology.getReadyTasks()` 解析就绪集 + 过滤至 `status==CREATED`（CLAIMED 他人任务跳过不 claim 不 abandon）+ `ITeamTaskStore.claimTask` CAS 认领（idempotent）+ `IAgentEngine.execute` 同步委派已绑定成员 agent（与 `MemberAgentTaskStep` 同一委派机制，**不复用 `TeamTaskFlowOrchestrator.execute(teamId)`**）+ 成功 `completeTask` / 失败仅对自认领任务 `abandonTask`（不静默跳过 #24）。依赖序自动保证 = 经就绪查询天然实现（blockedBy 未完成永不在就绪集，依赖完成后下周期自动就绪，无运行时阻塞）。复用 `ScheduledRecoveryManager` 生命周期范式（`synchronized` 幂等 start/stop + `IScheduledExecutor.scheduleWithFixedDelay` + graceful `cancel(false)`），`DefaultAgentEngine.setTeamTaskSchedulerDaemon` 注入（部署层管理 start/stop）。闭合「无人值守多 Agent 编排」链路——启动守护进程后 DAG 在依赖序约束下自动推进至完成，无需手动编排调用（端到端 Anti-Hollow 验证：线性 A→B→C + 菱形 A→{B,C}→D 自动完成 + 完成顺序断言 + stop 后新任务不派发）。详见 `nop-ai-agent-task-scheduler-daemon.md`。
>
> **auto-spawn 成员 agent 已落地（plan 237 / L4-auto-spawn-member-agent）**：`IMemberSpawner` 可插拔成员 spawn 扩展点 + `NoOpMemberSpawner` shipped 默认（恒 `NO_SPAWN` = 零回归）+ `DefaultMemberSpawner` functional 实现（基于团队声明式 `TeamMemberSpec.agentModel` spawn 成员 agent 经 `IAgentEngine.execute` 同步执行）。`TeamTaskSchedulerDaemon` dispatch 路径在 `resolveBoundMember` 返回 null（无已绑定成员）时咨询 spawner：bound-member 优先（有 bound member 不 spawn）+ NoOp = abandon 零回归 + functional = spawn 后委派（与 bound-member 同一 `completeOrAbandonAfterExecution` 共享逻辑）。闭合「无人值守下未绑定成员 = abandon」最后缺口——团队只需声明 memberSpec 无需预绑定 session，任务到达时自动 spawn 对应成员执行（端到端 Anti-Hollow 验证：线性 A→B→C + 菱形 A→{B,C}→D 自动 spawn 完成 + bound-priority spawner 不被调用 + NoOp 零回归对比 abandon，**全程无任何手动 `bindMemberSession`**）。daemon 自身 setter/构造器注入 spawner（wire-at-consumer 决策，镜像 `IResourceGuard`→TeamManager，不经 `DefaultAgentEngine` 中转）。详见 `nop-ai-agent-member-auto-spawn.md`。异步跨进程流编排 / LLM 直面编排工具 / nop-task decorator（retry/timeout/rate-limit）接入 / 运行时动态改图 / 任务 RE-CLAIM / 超时自动 ABANDON / orchestrator auto-spawn 集成 / spawn session 复用池化 / 多成员 per-task 路由 仍为显式 successor。
>
> **团队任务 RE-CLAIM + 超时自动 ABANDON 已落地（plan 240 / L4-team-task-reclaim-and-timeout-abandon）**：把团队任务生命周期从"CLAIMED 单向不可逆——一个被成员认领后中途崩溃（session orphaned / timeout / 进程死亡）的任务永远卡在 CLAIMED，阻塞整个 task DAG 无法推进"扩展为"RecoveryManager daemon 的 scanOnce 新增第 4 步 team-task 卡死检测与恢复"。`ITeamTaskStore.reclaimTask`（CLAIMED→CREATED，clear `claimedBy`）补齐状态机恢复转换（in-memory compute CAS / DB 条件 UPDATE CAS + 租户守卫，与 claim/complete/abandon 同一模式）。`ITeamTaskRecoveryHandler` 可插拔 handler 契约（`recoverStuckTasks() → List<TeamTaskRecoveryOutcome>`，**自包含**——handler 内部做检测 + 动作，scanOnce 仅调用此方法，与 plan 226/229 "scanOnce 做 SELECT + handler 做 per-item 动作"模式有意且裁定的偏差：team-task 是不同域表，封装在 handler 中保持 daemon 聚焦于 session 域）+ `TeamTaskRecoveryAction` 枚举（RECLAIM 重置可重新认领 / ABORT 终态标记失败 / SKIP 观测）+ `TeamTaskRecoveryOutcome` 数据对象 + `NoOpTeamTaskRecoveryHandler` shipped 默认（emptyList，零 DB 访问，零回归）+ `DefaultTeamTaskRecoveryHandler` 功能实现（时间基检测 `STATUS='CLAIMED' AND UPDATED_AT < now - threshold` + `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫 + RECLAIM/ABORT raw JDBC UPDATE 条件 `WHERE STATUS='CLAIMED'` CAS + per-task 故障隔离 + `ITenantResolver` opt-in 默认 `NullTenantResolver`）。`ScheduledRecoveryManager.scanOnce` 第 4 步（orphan detection 之后）调用 handler + `RecoveryScanResult` 8-arg 扩展（`teamTaskRecoveryActions` 字段）。核心裁定：RECLAIM=CLAIMED→CREATED（非终态复活）/ 时间基检测（非 claimer-liveness 交叉检测）/ handler 自包含 / 单一 defaultAction / scanOnce 第 4 步 / raw JDBC 动作 / 多租户守卫 / NoOp shipped 默认。闭合无人值守 DAG 任务生命周期自愈缺口——卡死 CLAIMED task 经 daemon 自愈后可被另一 member 重新 claim+complete，DAG 可继续推进（端到端 Anti-Hollow 验证：RECLAIM + ABORT + DAG 自愈 + NoOp 零回归）。详见 `nop-ai-agent-team-task-reclaim.md`。claimer-liveness 交叉检测 / per-task 超时配置 / 动态分级动作策略 / `team-task-reclaim` LLM 工具 / 终态复活 仍为显式 successor。

> **异步团队任务编排已落地（plan 241 / L4-async-cross-process-orchestration，async 半部）**：把团队任务 DAG 编排从"同步阻塞——`TeamTaskFlowOrchestrator.execute(teamId)` 在调用线程上阻塞到整个 DAG 完成（每节点经 `agentEngine.execute(request).join()` 同步阻塞成员 agent 执行）"扩展为"异步非阻塞 + DAG 并行分支真正并发"。新增非阻塞入口 `TeamTaskFlowOrchestrator.executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>`，**消费 nop-task 既有 async 模型**（`TaskStepReturn.ASYNC_RETURN` / `getReturnPromise` / `asyncOutputs`，纠正历史"async model 未落地"前提——nop-task `TaskStepReturn` + `GraphTaskStep` 已提供完整 async 契约）。`MemberAgentTaskStep.execute` async 化：claim（CREATED→CLAIMED）在节点触发期同步完成，成员 agent 执行结果包装为 async `TaskStepReturn`（包装 `IAgentEngine.execute()` 既有 `CompletableFuture`，非 `.join()`），complete/失败在 async 回调处理（诚实失败语义与同步路径逐条对齐：claim 失败 / 成员异常 / 非 completed / complete CAS 失败 → 任务保留 CLAIMED 不自动 abandon；已 COMPLETED 幂等为显式成功）。独立分支（互无 `blockedBy` 依赖）的就绪节点经 nop-task `GraphTaskStep` 既有 CompletableFuture 调度真正并发执行（async 化 member step 是解锁并发的关键——同步 join 会阻塞调度线程使并发退化为串行）；依赖序（如 D blockedBy {B,C}）由 `waitSteps` 严格保证。`execute(teamId)` 保留为 sync 便捷入口（`= executeAsync(teamId).join()` 语义等价包装，零回归）。**spawn 半部已落地（plan 243 / `L4-spawn-step-async`）**：`SpawnMemberAgentTaskStep` 亦 async 化——`spawnMember`+三态解释+`completeTask` 经 `CompletableFuture.supplyAsync(..., spawnExecutor)` 卸载到**独立于 commonPool** 的有界 daemon 线程池（防 commonPool 嵌套 `.join()` 阻塞死锁），claim 同步完成、返回 async `TaskStepReturn`；tenant-context 经 explicit-propagation 跨 worker 边界（orchestrator 入口捕获注入每个 step，对所有 DAG 拓扑鲁棒），诚实失败语义逐条对齐（任务保留 CLAIMED 不 abandon）；含 spawn 节点的图亦真正并发（菱形 spawn DAG B、C 并发 + D 依赖序严格），闭合 §4 最后 successor。`team-execute-flow` LLM 工具接线为消费真实 `executeAsync`（消除"伪 async 包装 sync"的 hollow 模式）。结构性问题同步 fast-fail / 节点失败经 future 异常 → `TeamTaskFlowResult{success=false}`（No Silent No-Op #24）。**显式 successor**：cross-process daemon 协调（分布式锁 / 多实例扫描协调 / 共享调度状态，`L4-cross-process-daemon-coordination`）/ `TeamTaskSchedulerDaemon` per-cycle async 派发 / 多成员 per-task 路由。详见 `nop-ai-agent-async-team-task-orchestration.md`。

> **多成员 per-task 路由（fan-out + reduction）已落地（plan 244 / L4-multi-member-per-task-routing）**：把团队任务 DAG 编排中的**单一成员委派原语**（一个 team task 节点委派给**一个**成员 agent——bound 路径 `resolveMember` 单成员 / spawn 路径 `resolveSpawnTarget` 单 memberSpec）扩展为**多成员 per-task 路由**：一个 team task 节点可 fan-out 至 **N** 个成员 agent（已绑定 +/或 spawned）并发执行，经 reduction 策略把 N 个成员执行结果归约为单一 task 结果。`ITaskMemberRouter` 可插拔 per-task 路由扩展点（单一扩展点覆盖 bound + spawn 两半部，返回 `MemberDispatchPlan` 标注每个 target 是 BOUND 还是 SPAWN + reduction 策略）+ `NoOpTaskMemberRouter` shipped 默认（singleton plan，bound 优先 + spawn fallback，逐行复现 plans 233/238/241/243 单成员行为，零回归）+ `IReductionStrategy` 可插拔归约扩展点 + `AllMustSucceedReduction` shipped 默认（最严格：N 成员都须 completed，任一失败 fast-fail + task 保留 CLAIMED）+ `MemberExecOutcome`/`ReductionContext`/`FanOutReduceComplete` 共享归约+complete+tenant 传播 helper。三种 fan-out step 变体：`BoundMemberFanOutStep`（N 个 `engine.execute` future 直接组合，bound 半部）/ `SpawnMemberFanOutStep`（N 个 `supplyAsync(spawnMember)` 组合，spawn 半部，复用 plan 243 dedicated executor + explicit-propagation tenant）/ `MixedMemberFanOutStep`（同一 plan 含 bound + spawn target 经统一 reduction 组合）。单成员 plan → 既有 `MemberAgentTaskStep`/`SpawnMemberAgentTaskStep` 路径逐行不变（short-circuit）。`SpawnMemberRequest` 增可选 additive target 字段（向后兼容：既有三参构造 target=null = spawner 自解析 = daemon 路径零变更；`DefaultMemberSpawner.spawnMember` 优先 request.target 否则 fallback `resolveSpawnTarget`）。claim 同步单次、complete 在全部 N 成员 completed 后单次、tenant 在 completeTask 处 re-apply + finally clear。诚实失败语义逐条对齐（空 plan / 成员失败 / spawner 三态 / CAS 失败 / null 各路径 + task 保留 CLAIMED 不 abandon；No Silent No-Op #24）。23 focused/E2E 测试全绿（bound fan-out 并发 + spawn fan-out 并发 + all-must-succeed reduction 各路径 + honest failure 各路径 + 单成员零回归 + tenant 隔离 + 混合 fan-out + E2E 5 含 bound/spawn diamond full path + honest failure 传播 + NoOp 等价 + sync≡async）。设计文档 `nop-ai-agent-multi-member-routing.md`。task 分片（partitioning）/ 成员 pipeline / quorum-majority-first-wins reduction / 在途取消 / per-task 配额 / daemon 多成员派发 / spawn 池化 / decorator / 动态改图 均为显式 Non-Goals successor。

> **daemon 多成员 + async 派发 parity 已落地（plan 245 / L4-daemon-multi-member-async-dispatch）**：把无人值守自动化 dispatch 路径（`TeamTaskSchedulerDaemon`，plan 236 交付）从「单成员 + 同步 `engine.execute().join()`」提升至与程序化 orchestrator（`TeamTaskFlowOrchestrator`）对等的「per-task 多成员 fan-out + async 派发」，闭合无人值守 vs 交互式编排的能力不对称。daemon 的 per-task dispatch 现消费同一 `ITaskMemberRouter` dispatch plan + 同一共享 `MemberFanOutDispatcher`（plan 245 把 plan 244 三 step 变体的 fan-out + reduce + complete 逻辑收敛为单一 canonical 实现，bound/spawn/mixed step 经其委派，daemon 亦经其委派——无双并行代码路径，Anti-Hollow #8/#22）+ 同一 `AllMustSucceedReduction` + 同一 plan 243 dedicated spawn executor（`ai-agent-daemon-spawn-worker-N`）+ explicit-propagation tenant。daemon per-cycle dispatch 不再 `.join()` 阻塞于单 task：fan-out future 经 async 派发，daemon 线程不阻塞（已完成 future 同步观测零回归，真正 async future 经 in-flight 队列跟踪 + `awaitInFlightDispatches`）。诚实失败语义对齐 orchestrator + plan 240 reclaim：fan-out/reduction 失败 → task 保留 CLAIMED（**非** abandon，由 reclaim 恢复）——有意失败语义变更（pre-245 daemon abandon → retain-CLAIMED），既有 daemon 失败测试相应更新为 CLAIMED + 新 `failedTasks` 计数，单成员成功路径逐行零回归。NoOp shipped 默认 router 产 singleton plan = bound 优先 + spawn fallback = 单成员 daemon 逐行零回归。`IMemberSpawner.spawnMember` 接口签名零变更。9 focused/E2E 测试全绿（bound fan-out 真实并发 + async 非阻塞 + reduction 单次 complete + 任一失败 retain-CLAIMED + 空路由/spawner 三态 honest failure + 单成员 NoOp 零回归 + tenant 传播无泄漏 + daemon-vs-orchestrator parity + diamond fan-out 全路径依赖序）。设计文档 `nop-ai-agent-daemon-dispatch-parity.md`。跨进程 daemon 协调 / nop-job 集成 / claimer-liveness / decorator / 在途取消 / partitioning / pipeline / quorum reduction / 调度策略变更 / spawn 池化 / 动态改图 仍为显式 successor。

```
Lead Actor
  │
  ├── team-task-create(subject, description, blockedBy)  ✅ in-memory foundational (plan 225)
  │     → 写入共享任务表 (in-memory ConcurrentHashMap; DB 事务 = successor)
  │
  ├── team-send-message(to, body)  ✅ foundational (plan 225)
  │     → IMessageService.send("agent.{memberSessionId}.inbox", message)
  │     → 成员在下一轮 ReAct 循环中通过 Hook 读取
  │
  ├── team-task-update(taskId, action)  ✅ 已落地 (plan 227)
  │     → 成员认领/完成/放弃任务 (claim/complete/abandon)
  │     → InMemoryTeamTaskStore compute CAS / DbTeamTaskStore 条件 UPDATE on STATUS CAS
  │
  └── team-status()  ✅ foundational (plan 225)
        → 查询所有成员状态 + 任务摘要
```

### 8.3 与 oh-my-opencode Team Mode 的关键差异

| 维度 | oh-my-opencode | Nop Agent |
|------|---------------|-----------|
| 进程模型 | 独立 session + 可选 worktree | 单进程多 Virtual Thread |
| 状态存储 | 文件系统 (atomic write) | DB (事务 + 乐观锁) |
| 消息传递 | 文件系统 JSON | `IMessageService` 内存队列 |
| 任务认领 | 文件锁 + PID 检测 | DB 乐观锁 + CAS（✅ plan 227 已实现：`DbTeamTaskStore` 条件 UPDATE on STATUS，affected-row-count 判定；in-memory 经 `ConcurrentHashMap.compute` CAS） |
| 崩溃恢复 | 文件系统 .delivering- 扫描 | DB 状态机 + RecoveryManager |
| 多用户 | 单用户 | 多租户 + 用户隔离 |
| 资源隔离 | Git worktree | Virtual Thread + DB 隔离 |

## 9. 与现有设计文档的关系

| 现有文档 | 本篇关系 |
|---------|---------|
| `01-architecture-baseline.md` | 本篇在其上方新增 Platform Layer，不改变 Agent Engine Layer |
| `nop-ai-agent-react-engine.md` | ReAct 引擎不变，Actor 是其运行时容器 |
| `nop-ai-agent-multi-agent.md` | 本篇的 `IConflictStrategy`、Team ACL、Fencing Token 是其协调策略的具体实现方案 |
| `nop-ai-agent-session-and-storage.md` | Session 机制不变，增加 Actor 状态持久化 |
| `nop-ai-agent-reliability.md` | 本篇的恢复策略是其具体实现 |
| `nop-ai-agent-context-model.md` | 上下文模型不变，Actor 提供运行时上下文容器 |
| `nop-ai-agent-security-and-permissions.md` | 权限模型不变，增加团队级别的权限派生 |

## 10. 实施路线

| 阶段 | 内容 | 依赖 | 状态 |
|------|------|------|------|
| **Phase 1** | ActorRuntime + ActorRegistry + Virtual Thread 调度 | ReAct 引擎完成 | ✅ 基础层已落地（plan 218 / L4-8）：`IActorRuntime` + `AgentActor` + `AgentActorStatus` + `ActorRegistry`/`InMemoryActorRegistry` + `NoOpActorRuntime` shipped 默认 + `InMemoryActorRuntime` 功能实现（专用单线程 executor observation-only mailbox 消费循环，Java 11 兼容）+ 引擎接线（`DefaultAgentEngine` 三个执行入口点 opt-in 注册/注销 Actor）。Virtual Thread 调度优化（`Thread.ofVirtual()`）为 successor，待模块迁移到 Java 21 release 后切换 |
| **Phase 2** | MessageRouter + call-agent 异步模式 + 多用户隔离 + Steering 注入 | Phase 1 | 🟡 部分落地：**Steering 注入已落地（plan 220 / L4-8-steering）**——Actor mailbox 消费循环从 observation-only 升级为 steering-injection（poll → envelope payload 转 `ChatMessage` → enqueue 到 `AgentExecutionContext` 线程安全 steering queue → ack），ReAct 循环在 round 边界 drain steering queue 并注入到下一轮 LLM 请求，`DefaultAgentEngine` 三入口点在 `createActor` 后关联 ctx steering queue 到 Actor。**call-agent 异步 mailbox 模型 foundational 已落地（plan 224 / L4-8-call-agent-async）**——`CallAgentExecutor` 在功能性 `IAgentMessenger` 可用时经 `IAgentMessenger.request()` 投递 REQUEST 信封到引擎级 `agent.call-agent` topic（`CallAgentRequestPayload`/`CallAgentResponsePayload` 不可变载荷契约），引擎在 `setMessenger` 时 idempotent 注册 call-agent handler（handler 内 `engine.execute().orTimeout().join()`，try/catch 返回 failure RESPONSE 非传播），`NoOpAgentMessenger` shipped 默认保留 fork+exec 零回归。per-session inbox 路由（REQUEST 投递到 `agent.{calleeSessionId}.inbox` 而非引擎级 topic）+ 异步非阻塞 handler（handler 同步阻塞 messenger dispatch 线程，多层嵌套 + commonPool 饱和可能死锁）+ 多用户隔离仍为 successor |

> 注：`call-agent`（fork+exec MVP）和 `send-message`（fire-and-forget）工具已在 plan 168 交付。Phase 2 的"call-agent 异步模式"指基于 Actor mailbox 的请求-响应模型（Caller 发 REQUEST 到 Callee inbox、Callee actor 消费并回复 RESPONSE），是 fork+exec 的 Actor Runtime 升级版。**call-agent 异步 mailbox 模型 foundational 已由 plan 224 落地**：`CallAgentExecutor` 在功能性 `IAgentMessenger` 可用时经 `IAgentMessenger.request()` 投递 REQUEST 到引擎级 `agent.call-agent` topic（`setMessenger` 时注册 handler 执行子 Agent 并返回 RESPONSE）；shipped 默认（`NoOpAgentMessenger`）保留 fork+exec 零回归。per-session inbox 路由（发 REQUEST 到 Callee inbox topic 而非引擎级 topic）+ 异步非阻塞 handler 仍为 successor。
| **Phase 3** | TeamManager + TeamSpec DSL + 共享任务表 + Team ACL | Phase 2 | 🟡 部分落地：**TeamManager foundational slice 已交付（plan 223 / L4-8-team-manager）**——`ITeamManager` 契约 + `TeamSpec`/`Team`/`TeamMember`/`TeamMemberSpec`/`MemberRole`/`TeamStatus` 数据对象 + `NoOpTeamManager` shipped 默认（No Silent No-Op）+ `InMemoryTeamManager` 功能实现（`ConcurrentHashMap` 双索引，完整生命周期 + 状态机）+ `DefaultAgentEngine` `teamManager` 字段 + `setTeamManager`/`getTeamManager`（null-safe）。**团队通信工具 foundational 已交付（plan 225 / L4-8-team-tools）**——`TeamSendMessageExecutor`/`TeamStatusExecutor`/`TeamTaskCreateExecutor` 三个 IToolExecutor + `ITeamTaskStore` 契约 + `NoOpTeamTaskStore` shipped 默认 + `InMemoryTeamTaskStore` 功能实现 + `AgentToolExecuteContext` `teamManager`/`teamTaskStore` 接线 + `ReActAgentExecutor.Builder`/`DefaultAgentEngine.resolveExecutor` 传递。TeamSpec XDSL 配置化 / 自动团队绑定**已落地（plan 231 / L4-team-auto-binding）**——`<team>`/`<team-member>` 嵌入 `agent.xdef` + `TeamModelConverter` + 引擎三入口点 auto-bind。DB-backed 持久化已落地（plan 230）。Team ACL 强制已落地（plan 228）。DB-backed 共享任务表 / `team-task-update` 状态机已落地（plan 227/225）。**nop-task DAG 集成已落地（plan 233 / L4-nop-task-dag-integration）**——团队任务→nop-task 工作流 DAG 图 + 依赖序同步编排（`TeamTaskGraphBuilder` 环检测 + `TeamTaskFlowOrchestrator` 经 `ITask.execute` 同步执行 `GraphTaskStep` DAG 调度，详见 `nop-ai-agent-task-flow-integration.md`）。**blockedBy 自动调度守护进程已落地（plan 236 / L4-blockedBy-resolution-engine）**——`ITeamTaskSchedulerDaemon` + `TeamTaskSchedulerDaemon` 定时扫描就绪任务自动 claim/派发（复用 `ScheduledRecoveryManager` 生命周期范式 + 经 `TeamTaskTopology.getReadyTasks()` + CAS claim + `IAgentEngine.execute` + completeTask，依赖序经就绪查询天然保证，CLAIMED 他人任务不被误弃，闭合无人值守多 Agent 编排链路，详见 `nop-ai-agent-task-scheduler-daemon.md`）。**auto-spawn 成员 agent 已落地（plan 237 / L4-auto-spawn-member-agent）**——`IMemberSpawner` 可插拔扩展点 + `NoOpMemberSpawner` shipped 默认零回归 + `DefaultMemberSpawner` functional 实现（基于 `TeamMemberSpec.agentModel` spawn 成员 agent），daemon dispatch 路径在无 bound member 时咨询 spawner（bound-member 优先），闭合「无人值守下未绑定成员 = abandon」最后缺口——团队只需声明 memberSpec 无需预绑定 session，详见 `nop-ai-agent-member-auto-spawn.md`。完整 §5.1 ACL 模型（permissions override / 权限派生）/ 多租户用户隔离 / 异步跨进程流编排 / nop-task decorator 接入 仍为显式 successor |
| **Phase 4** | RecoveryManager + 崩溃恢复 + 归档清理 + Fencing Token | Phase 1 | 🟡 部分落地：**接管锁基础层 + 定时扫描 daemon 已落地（plan 221 / plan 222 / L4-8-P4）**——`ISessionTakeoverLock` 契约 + `NoOpSessionTakeoverLock` shipped 默认 + `DbSessionTakeoverLock` 功能实现（独立 `ai_agent_session_lock` 表 + lease/TTL CAS acquire + conditional release + stale-lock 抢占 + opt-in 编程式 API `tryAcquire`/`release`/`isHeld`/`tryRenew`）+ `DefaultAgentEngine` 三入口点接线 + `restorePendingSessions` `isHeld` 跳过增强。**定时扫描 daemon（plan 222）**：`IRecoveryManager` 契约（`start`/`stop` 幂等 + `scanOnce → RecoveryScanResult`）+ `NoOpRecoveryManager` shipped 默认（全零值，零回归）+ `ScheduledRecoveryManager` 功能实现（`IScheduledExecutor`（nop-commons）周期调度默认 60s，`scanOnce` = stale lock cleanup（幂等 DELETE `LOCK_EXPIRES_AT <= now`）+ orphan session detection（`STATUS IN ('running','pending')` 无活跃锁，LOG.warn）+ `RecoveryScanResult`）+ `DefaultAgentEngine` `recoveryManager` 字段 + `setRecoveryManager` setter（部署层管理 start/stop，引擎不调用——遵循部署层生命周期管理设计契约）。RecoveryManager 的其余能力（orphan 进程主动 liveness 检测 / 超时强制中止 / 归档清理 / 心跳自动续约）均为显式 successor；**恢复模式策略已落地（plan 226 / L4-8-P4-RecoveryStrategy）**：`IOrphanRecoveryHandler` + `RecoveryMode`(RESUME/ABORT/SKIP) + `DefaultOrphanRecoveryHandler` + daemon 集成（RETRY 为 successor）。**Fencing Token foundational 原语已落地（plan 235 / L4-fencing-token）**：`FencingToken`（actorId + monotonicCounter + issuedAt）+ `IFencingTokenService`（`issue` 原子递增单调计数器 + `validate` strictly-greater 高水位更新）+ `FencingTokenDecision`（valid/stale）+ `NoOpFencingTokenService` shipped 默认（恒 valid 零回归）+ `DefaultFencingTokenService` functional in-memory CAS（per-actor 双计数器 + only-if-greater CAS loop，无 lost update）；scope_claim 集成 / conflict_alert 广播 / Compaction 集成 / DB-backed 跨进程 CAS / Actor 恢复后重获取 token 仍为 successor（详见 §5.1）|
| **Phase 5** | ResourceGuard + 协调信道 + 资源配额 | Phase 2 | 🟡 部分落地：**foundational count-based 并发配额切片已落地（plan 234 / L4-resource-guard-quota）**——`IResourceGuard` 中央配额决策网关（`checkConcurrent → QuotaDecision`）+ `NoOpResourceGuard` shipped 默认（恒 allow = 零回归）+ `DefaultResourceGuard` 功能实现（`QuotaConfig` teamMaxMembers 默认 8 / tenantMaxConcurrentActors 默认 10）。三维度 enforcement：`InMemoryTeamManager`/`DbTeamManager`（TEAM_MEMBERS createTeam/addMember + TEAM_PARALLEL_BOUND_MEMBERS bindMemberSession，`maxParallelMembers` hint→enforced）+ `InMemoryActorRuntime`（CONCURRENT_ACTORS_PER_TENANT createActor，per-tenant scope）。denial fail-fast 抛 `NopAiAgentException`，端到端经 engine.execute → auto-bind → guard → denial 完整链路验证。**协调信道（scope_claim/conflict_alert）+ 时间窗 rate-limit（LLM 调用频率 / Compaction 配额池）+ storage 配额 + per-agent token/时间累积** 仍为显式 successor；Fencing Token foundational 原语已由 plan 235 落地（in-memory CAS，详见 Phase 4 + §5.1） |

Phase 1 和 Phase 4 可并行开发。Phase 3 依赖 Phase 2 完成。各 Phase 通过接口隔离——引擎层只依赖 `IConflictStrategy`、`ITeamAclProvider`、`IFencingTokenService` 等接口，不直接依赖具体实现。`IFencingTokenService` 接口已存在（plan 235 / L4-fencing-token 交付 foundational 原语：issue/validate + NoOp shipped 默认 + Default in-memory CAS）；engine 顶层接线（`setFencingTokenService`）预留给首个 consumer successor（scope_claim / Compaction）。

## 11. Open Questions

- [x] ~~Actor 邮箱是无界队列还是有界队列？有界时背压策略如何？~~（**已裁定 plan 216 / L4-5**：`DeferredAckMailbox` 构造器接受 `capacity`（`<= 0` 无界，默认无界）；有界邮箱满时 `offer()` 返回 `false`（背压信号，非异常），生产者据此决策重试/降级/丢弃。Actor Runtime L4-8 将按本节倾向配置有界容量（如默认 1000）。）
- [ ] 子 Actor 的权限派生是否复用 AgentModel 的 permissions 字段，还是需要独立的 permissions override？（倾向：复用 + override 合并）
- [ ] 团队模式中的共享任务表，是用独立实体还是复用 nop-task 的 ITaskRuntime？（倾向：独立实体，更轻量）
- [ ] RecoveryManager 的恢复粒度：是恢复到最近 ReAct 步骤，还是恢复到最近 LLM 调用？（倾向：最近 ReAct 步骤，工具调用结果可重放）
- [ ] 多租户场景下，LLM API Key 是租户级还是全局级？（倾向：租户级，通过 `LlmModel` 的 Delta 定制实现）
