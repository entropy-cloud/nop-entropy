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
| **ITaskStep + Decorator** (nop-task) | Agent 编排的 DAG 执行，内置 retry/timeout/rate-limit |
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
│  │ (无界队列) │  │  (ReAct 循环)    │  │
│  └────┬─────┘  └────────┬─────────┘  │
│       │                 │            │
│       └─────→ dispatch ─┘            │
│                                      │
│  State:                              │
│  - actorId (UUID)                    │
│  - agentModel (配置)                  │
│  - session (持久化会话)               │
│  - mailbox (待处理消息)               │
│  - status: created|ready|running|idle|  │
│           failed|recovering|stopped     │
│  - cancelToken (取消令牌)             │
│  - parentActorId (可选, 子 Agent)     │
│  - tenantId, userId (多租户)          │
│  - createdAt, lastActiveAt            │
└──────────────────────────────────────┘
```

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
- **同步请求-响应**：`call-agent` 工具，Caller 挂起等待 Callee 完成
- **异步消息**：`send-message` 工具，发后即忘，Callee 在下一轮 ReAct 循环中读取
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
│  │ (消息路由)   │  │ (文件写意图/资源声明)   │   │
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

| 组件 | 职责 | Nop 平台映射 |
|------|------|-------------|
| **ActorRuntime** | 创建/销毁 Actor 实例，管理 Virtual Thread 生命周期 | 基于 `GlobalExecutors` 的 Virtual Thread 池 |
| **ActorRegistry** | 维护所有活跃 Actor 的注册表，按 tenantId/userId 隔离 | 内存 `ConcurrentHashMap` + DB 持久化索引 |
| **MessageRouter** | Actor 间消息路由，topic 匹配，背压控制 | `LocalMessageService` + topic 命名约定 |
| **TeamManager** | Agent 团队的生命周期（创建/解散/成员管理/状态查询） | `@BizModel("AiTeam")` + ORM 实体持久化 |
| **RecoveryManager** | 崩溃恢复、超时清理、orphan 检测、消息重放 | 定时任务（nop-job）+ DB 状态机 |
| **ResourceGuard** | 文件写意图注册、资源声明、冲突检测 | `@BizAction` 拦截工具执行 |

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
- **Tenant 隔离**：不同租户的 Actor 完全不可见，DB 查询自动加 tenantId 条件（Nop ORM 已支持）
- **User 隔离**：同一租户内的不同用户，只能看到自己的 Actor（除非通过团队共享）
- **团队共享**：Team 内的 Actor 对团队成员可见，通过 Team 的 ACL 控制

### 5.2 资源配额

| 资源 | 配额维度 | 默认值 | 强制方式 |
|------|---------|--------|---------|
| 并发 Actor 数 | (tenant, user) | 10 | ActorRuntime 创建时检查 |
| 单 Agent 最大迭代 | (agentModel) | 50 | ReAct 循环内检查 |
| 单 Agent 最大 Token | (agentModel) | 200K | 每次 LLM 调用后累加检查 |
| 单 Agent 最大时间 | (agentModel) | 30min | ICancelToken 超时 |
| LLM 调用频率 | (tenant) | 100/min | `IRateLimiter` |
| 团队最大成员数 | (teamModel) | 8 | TeamManager 创建时检查 |

配额通过 Nop 配置系统管理（`@cfg:ai.agent.quota.*`），支持 Delta 定制。

## 6. 自动恢复

### 6.1 恢复策略

| 异常场景 | 检测方式 | 恢复动作 |
|----------|---------|---------|
| LLM 调用超时 | `CompletableFuture.orTimeout()` | 重试（最多 3 次，指数退避） |
| LLM 返回畸形响应 | 响应解析失败 | 标记工具调用失败，注入错误消息让 LLM 重新生成 |
| 工具执行异常 | `IToolExecutor` 抛出异常 | 取决于工具的 `critical` 标记：critical→中止，非 critical→注入错误继续 |
| Agent 循环超过最大迭代 | 步数计数器 | 强制中止，生成摘要 |
| Actor 所在 Virtual Thread 被 Interrupt | `ICancelToken` | 保存 Session 快照，标记 `interrupted`，等待恢复 |
| JVM 进程崩溃重启 | DB 中 Actor 状态为 `running` 但进程已重启 | RecoveryManager 扫描，根据策略恢复 |
| 子 Agent 崩溃 | 子 Actor 状态变为 `failed` | 通知父 Actor，父 Actor 决定重试或放弃 |

### 6.2 持久化策略

**Session 快照**（已有设计，见 `nop-ai-agent-session-and-storage.md`）：
- Phase 1 使用文件系统保存 Session 状态（见 session-and-storage.md §2），本篇描述的 DB 持久化是 Phase 2+ 的演进
- 每次 ReAct 循环迭代后保存快照到 DB
- 快照内容：消息历史（可压缩）、工具调用结果、当前状态
- 恢复时从最近快照重放

**Actor 状态持久化**：
- Actor 状态变更（status 转换）立即写入 DB（事务保护）
- `running` → `idle` / `failed` / `stopped` 都有对应的 DB 记录
- 邮箱中的未处理消息持久化到 DB（不怕进程崩溃丢消息）

### 6.3 RecoveryManager 工作流

```
RecoveryManager (定时任务，每 60 秒)
  │
  ├── 1. 扫描 DB 中 status=running 的 Actor
  │     └── 检查进程 ID 是否存活
  │           ├── 当前进程的 Actor → 检查 Virtual Thread 是否活跃
  │           └── 其他进程的 Actor → 标记 orphaned
  │
  ├── 2. 处理 orphaned Actor
  │     ├── 恢复模式=resume → 从最近快照恢复
  │     ├── 恢复模式=retry → 从头重试整个请求
  │     └── 恢复模式=abort → 标记 failed，通知调用者
  │
  ├── 3. 处理超时 Actor
  │     └── status=running 且 lastActiveAt 超过 maxWallClockMinutes
  │           → ICancelToken.cancel() → 等待优雅停止 → 强制标记 failed
  │
  └── 4. 清理已停止的 Actor
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

团队通过 XDSL 配置定义：

```
team-spec.xdef:
  - name: 团队名称
  - description: 描述
  - leadAgent: Lead Agent 的 agentModel 名称
  - members[]: 成员列表
    - name: 成员名称
    - agentModel: Agent 配置引用
    - kind: category | direct
    - category: (当 kind=category 时，用于路由到合适的 Agent)
    - prompt: 成员特定的 prompt 覆盖
    - permissions: 成员的权限覆盖
  - maxParallelMembers: 最大并行成员数
  - maxWallClockMinutes: 最大执行时间
  - maxMessagesPerRun: 最大消息数
```

### 8.2 团队通信模型

```
Lead Actor
  │
  ├── team-task-create(subject, description, blockedBy)
  │     → 写入共享任务表 (DB 事务)
  │
  ├── team-send-message(to, body)
  │     → IMessageService.send("agent.{memberActorId}.inbox", message)
  │     → 成员在下一轮 ReAct 循环中通过 Hook 读取
  │
  ├── team-task-update(taskId, status)
  │     → 成员认领/完成/放弃任务 (DB 乐观锁)
  │
  └── team-status()
        → 查询所有成员状态
```

### 8.3 与 oh-my-opencode Team Mode 的关键差异

| 维度 | oh-my-opencode | Nop Agent |
|------|---------------|-----------|
| 进程模型 | 独立 session + 可选 worktree | 单进程多 Virtual Thread |
| 状态存储 | 文件系统 (atomic write) | DB (事务 + 乐观锁) |
| 消息传递 | 文件系统 JSON | `IMessageService` 内存队列 |
| 任务认领 | 文件锁 + PID 检测 | DB 乐观锁 + CAS |
| 崩溃恢复 | 文件系统 .delivering- 扫描 | DB 状态机 + RecoveryManager |
| 多用户 | 单用户 | 多租户 + 用户隔离 |
| 资源隔离 | Git worktree | Virtual Thread + DB 隔离 |

## 9. 与现有设计文档的关系

| 现有文档 | 本篇关系 |
|---------|---------|
| `01-architecture-baseline.md` | 本篇在其上方新增 Platform Layer，不改变 Agent Engine Layer |
| `nop-ai-agent-react-engine.md` | ReAct 引擎不变，Actor 是其运行时容器 |
| `nop-ai-agent-multi-agent.md` | 本篇是其 Phase 2+ 的具体实现方案 |
| `nop-ai-agent-session-and-storage.md` | Session 机制不变，增加 Actor 状态持久化 |
| `nop-ai-agent-reliability.md` | 本篇的恢复策略是其具体实现 |
| `nop-ai-agent-context-model.md` | 上下文模型不变，Actor 提供运行时上下文容器 |
| `nop-ai-agent-security-and-permissions.md` | 权限模型不变，增加团队级别的权限派生 |

## 10. 实施路线

| 阶段 | 内容 | 依赖 |
|------|------|------|
| **Phase 1** | ActorRuntime + ActorRegistry + Virtual Thread 调度 | ReAct 引擎完成 |
| **Phase 2** | MessageRouter + call-agent 异步模式 + 多用户隔离 | Phase 1 |
| **Phase 3** | TeamManager + TeamSpec DSL + 共享任务表 | Phase 2 |
| **Phase 4** | RecoveryManager + 崩溃恢复 + 归档清理 | Phase 1 |
| **Phase 5** | ResourceGuard + 文件写意图 + 资源配额 | Phase 2 |

Phase 1 和 Phase 4 可并行开发。Phase 3 依赖 Phase 2 完成。

## 11. Open Questions

- [ ] Actor 邮箱是无界队列还是有界队列？有界时背压策略如何？（倾向：有界 + 背压拒绝，默认 1000 条）
- [ ] 子 Actor 的权限派生是否复用 AgentModel 的 permissions 字段，还是需要独立的 permissions override？（倾向：复用 + override 合并）
- [ ] 团队模式中的共享任务表，是用独立实体还是复用 nop-task 的 ITaskRuntime？（倾向：独立实体，更轻量）
- [ ] RecoveryManager 的恢复粒度：是恢复到最近 ReAct 步骤，还是恢复到最近 LLM 调用？（倾向：最近 ReAct 步骤，工具调用结果可重放）
- [ ] 多租户场景下，LLM API Key 是租户级还是全局级？（倾向：租户级，通过 `LlmModel` 的 Delta 定制实现）
