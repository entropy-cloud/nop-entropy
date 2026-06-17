# 218 nop-ai-agent Actor Runtime 平台层基础原语（AgentActor + ActorRegistry + 执行循环）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8

> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（L4-8 ❌ 未实现，line 247，依赖 L4-1~L4-6 全部 ✅）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`（Platform Layer 六大组件 + 实施路线 §10 Phase 1）；`ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §五/§六（部署模型 + 通信模型 + Actor 概念对应表 §十）；多份已完成 plan 的 Non-Blocking Follow-ups 明确将 L4-8 列为 successor plan required（`216` mailbox 消费循环 / `214` 跨进程写意图 registry / `213` 跨进程 circuit 状态 / `210` 持久化熔断状态 / `217` DB-backed 贡献复制）
> Related: `216`（L4-5 `IMailbox` deferred-ack 邮箱，本计划交付其执行循环消费者）、`168`（L4-1b `call-agent` fork+exec MVP + `send-message` fire-and-forget，本计划交付 Actor 容器原语）、`183`/`184`/`185`（L3-4b/c/d session restore，跨进程接管锁为本计划 ActorRegistry 的 successor）、`217`（L4-6 `IContributionRegistry`，确立「契约表面 + NoOp 默认 + 功能实现 + 引擎接线」范围范式）

## Purpose

把 nop-ai-agent 的运行时容器从"`DefaultAgentEngine` 直接 `supplyAsync` 分发、无 Actor 抽象、`IMailbox`（L4-5 ✅）虽有 per-session 实例但无执行循环消费者"扩展为"可选启用 `IActorRuntime` 管理 `AgentActor` 实例——每个 Actor 在专用线程上运行 mailbox 消费循环、状态可观测、经 `ActorRegistry` 注册表可查询，`IMailbox` 的 deferred-ack 邮箱首次拥有功能消费循环（observation-only：poll → 记录/日志 → ack，不注入 ReAct 上下文）"。本计划交付 vision doc §10 Phase 1 的**契约表面 + 基础执行原语 + NoOp shipped 默认 + 功能性 in-memory 实现 + 引擎接线**，闭合 L4-8 roadmap gap 的基础层。

Platform Layer 的其余组件（TeamManager / RecoveryManager / ResourceGuard / DB-backed 持久化 / 跨进程接管锁 / Fencing Token）均为显式 Non-Goal 独立 successor——本计划只交付让它们可以构建于其上的 Actor 原语基础。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`DefaultAgentEngine` 执行模型 ✅**（L1-1）：`doExecute` / `resumeSession` / `restoreSession` 三个入口点经 `supplyAsync`（ForkJoinPool）异步分发，`runningExecutions` `ConcurrentHashMap<String, CancelHandle>` 提供单进程 session 并发保护（plan 197 fail-fast + cancel-safe）。引擎层当前**无 Actor 抽象**——执行直接委托给 `ReActAgentExecutor`，不经 Actor 容器。
- **`IMailbox` deferred-ack 邮箱 ✅**（L4-5 / plan 216）：3-phase reservation 协议（`offer` → `poll` → `ack`/`nack`）+ `DeferredAckMailbox` in-memory 功能实现 + `NoOpMailbox` 显式 no-op 默认 + `MailboxMessageHandler` 适配器。**`DefaultAgentEngine.ensureSessionMailbox`（`:1966`）经 `mailboxFactory` 为每个 session 创建 per-session `IMailbox` 并存入 `sessionMailboxes` map（`:143`），三个执行入口点（`:1222`/`:1450`/`:1580`）均调用**；`getSessionMailbox(sessionId)`（`:568`）提供查询访问。**plan 216 Closure 明确声明**："邮箱的最终消费者（Actor 执行循环 poll → process → ack）是 L4-8 Actor Runtime 的独立 successor"——即 `IMailbox` 已有 per-session 实例但无执行循环消费者。本计划的 Actor 消费**已存在的 engine-created mailbox**（经 `getSessionMailbox`），不创建第二个 mailbox 或注册第二个 handler。
- **Java 编译版本 = 11**：root `pom.xml` 声明 `<maven.compiler.source>11</maven.compiler.source>`，nop-ai-agent 无覆盖。`Thread.ofVirtual()`（Java 21 API）**不可用**。nop-ai-agent 当前不引用 `GlobalExecutors`（grep 确认）。本计划 Actor 执行循环使用标准 Java 11 并发原语（`CompletableFuture.supplyAsync` + `Executors.newSingleThreadExecutor` per-actor），**不**依赖 Virtual Thread。VT 优化是独立 successor（待模块迁移到 Java 21 release）。
- **`IAgentMessenger` ✅**（L4-1 / plan 168）：Agent 域消息抽象，`send`（fire-and-forget）+ `request`（请求-响应，`CompletableFuture` 含超时）；`DefaultAgentEngine` 经 `setMessenger` 接线，默认 `NoOpAgentMessenger`（send no-op，request fail-fast）。
- **`call-agent` 工具 ✅**（L4-1b / plan 168）：`CallAgentExecutor` 采用 **fork+exec 模型**——直接调用 `IAgentEngine.execute()` 同步执行子 Agent，不经 mailbox。vision §6 + multi-agent.md §5 明确声明："基于 mailbox 的 call-agent 模型（发 REQUEST 到目标 inbox、等待 actor 响应）是 Actor Runtime Phase 2 的目标"——本计划交付 Actor 原语但**不**迁移 call-agent 到 mailbox 模型（Non-Goal）。
- **`IContributionRegistry` ✅**（L4-6 / plan 217）：插件贡献注册表，确立本模块的扩展点范围范式（契约表面 + NoOp shipped 默认 + 功能实现 + 引擎 setter 接线 + 装配期解析 + 端到端验证）。
- **session restore 链 ✅**（L3-4b/c/d / plans 183/184/185）：crash/restart restore + auto restore-on-startup + DB-backed session store 全部落地。**roadmap §4 验收标准 line 253 明确声明**："跨进程接管锁依赖 L4-8 Actor Runtime，是独立 successor"——即 `DBSessionStore` 使 session 可被任何实例加载，但"防止多实例同时恢复同一 session"的并发接管锁尚未存在。
- **Virtual Thread 可用性**：项目 Java source = 11（`Thread.ofVirtual()` 不可用，见上）。本计划使用标准线程。VT 优化 deferred 到模块迁移到 Java 21。
- **`AgentActor` / `IActorRuntime` / `ActorRegistry` 零实现**：grep `AgentActor|IActorRuntime|ActorRegistry|ActorRuntime` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中（仅 design 文档引用）。
- **vision doc §10 实施路线**：Phase 1 = ActorRuntime + ActorRegistry + Virtual Thread 调度（本计划 scope）；Phase 2 = MessageRouter + call-agent 异步模式 + 多用户隔离；Phase 3 = TeamManager + TeamSpec；Phase 4 = RecoveryManager + 跨进程接管锁；Phase 5 = ResourceGuard + 协调信道。本计划交付 Phase 1。
- **roadmap §4**：`L4-8 | Actor Runtime 平台层 | L4-1 ~ L4-6 | ❌`（line 247）。本计划将 L4-8 基础层从 ❌ → ✅（完整 Platform Layer 的 Team/Recovery/ResourceGuard 组件为 successor）。

## Goals

- **`AgentActorStatus` 枚举**：7 值（`CREATED` / `READY` / `RUNNING` / `IDLE` / `FAILED` / `RECOVERING` / `STOPPED`），与 vision doc §3.1 一致。Javadoc 明确每个状态的进入条件与合法转换。
- **`AgentActor` 数据/行为对象**：不可变标识（`actorId` UUID + `sessionId` + `agentName`）+ 可变运行时状态（`status` volatile + `createdAt` + `lastActiveAt`）；持有 `IMailbox` 引用（可选，null = 不消费 mailbox）。Javadoc 明确线程安全契约（status 读写 volatile，其余字段 Actor 单线程访问）。
- **`IActorRuntime` 接口**：`isEnabled() → boolean`（NoOp 返回 false，功能实现返回 true——引擎据此判断是否走 Actor 路径，**不**用异常控制流）、`createActor(sessionId, agentName) → AgentActor`（幂等：同 sessionId 已有 active actor 则返回既有实例）、`getActor(actorId) → Optional<AgentActor>`、`getActorBySession(sessionId) → Optional<AgentActor>`、`getActiveActors() → Collection<AgentActor>`、`destroyActor(actorId) → boolean`（优雅停止）、`destroyAll() → int`。Javadoc 明确生命周期契约与线程安全。
- **`ActorRegistry`**：维护 `actorId → AgentActor` + `sessionId → actorId` 双索引（`ConcurrentHashMap`），提供注册/注销/查询。`InMemoryActorRegistry` 功能实现。多租户 tenant/user 隔离维度（vision §5.1）为 ResourceGuard successor——foundational slice 仅跟踪 actorId/sessionId/agentName/status，不做 tenant 过滤（`AgentExecutionContext` 无 tenantId/userId 字段，仅有 `Principal`/`ChannelKind`）。
- **`NoOpActorRuntime` shipped 默认**：singleton；`isEnabled()` 返回 false；`createActor` 抛 `UnsupportedOperationException`（仅作防御——引擎在 `isEnabled()==false` 时不调用 createActor，见裁定 5）；查询返回空 Optional / 空集合；`destroyActor` / `destroyAll` no-op。引擎默认使用它，零行为回归（引擎走既有 `supplyAsync` 路径，不经 Actor 容器）。
- **`InMemoryActorRuntime` 功能实现**：`isEnabled()` 返回 true；`createActor` 创建 `AgentActor` 实例 + 注册到 registry + 获取 session 的**既有** `IMailbox`（经 `DefaultAgentEngine.getSessionMailbox(sessionId)`，若存在）+ 启动专用线程（`Executors.newSingleThreadExecutor` per-actor，Java 11 兼容）执行 observation-only 消费循环；循环 poll mailbox → 若有消息则记录到 actor 的 received-messages 列表 + 日志（**不**注入 ReAct 上下文 / session 消息——见裁定 6）→ ack → 循环；Actor 状态转换经 volatile 可见。线程安全 via `ConcurrentHashMap` + per-actor 单线程约束。
- **引擎接线**：`DefaultAgentEngine` 新增可选 `actorRuntime` 字段（默认 `NoOpActorRuntime`）+ `setActorRuntime` setter；三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）在 `supplyAsync` lambda 入口，**若 `actorRuntime.isEnabled()`**，经 `actorRuntime.createActor(sessionId, agentName)` 注册 Actor（opt-in，零回归）；`finally` 块经 `actorRuntime.getActorBySession(sessionId).ifPresent(a -> actorRuntime.destroyActor(a.getActorId()))` 注销（无需修改 `CancelHandle` 或 `AgentExecutionContext`）。shipped 默认（NoOp，`isEnabled()==false`）走既有路径不变。
- **`IMailbox` 消费接线**：`InMemoryActorRuntime` 创建的 Actor 经 `DefaultAgentEngine.getSessionMailbox(sessionId)` 获取 engine 已创建的 per-session `IMailbox`（plan 216 `ensureSessionMailbox` 产物）；Actor 执行循环 poll 消费到达 inbox topic 的消息。**Actor 不创建第二个 mailbox、不注册第二个 handler**——消费的是 engine 既有的唯一 mailbox 实例。这闭合 plan 216 的"邮箱最终消费者"successor gap。
- **设计文档更新**：`nop-ai-agent-actor-runtime-vision.md` §3.1/§4.2/§10 标注 Phase 1 已落地步骤；`01-architecture-baseline.md` 扩展点清单补 `IActorRuntime`；roadmap §4 L4-8 ❌→✅（基础层）。
- **focused 测试**：Actor 状态转换、registry 注册/注销/查询、createActor 幂等、destroyActor 优雅停止、mailbox observation-only 消费循环（offer → poll → record → ack，消息不注入 session）、NoOp 默认 `isEnabled()==false`，各有覆盖。
- **端到端验证**：配置 `InMemoryActorRuntime`（注入 engine 引用）+ `DeferredAckMailbox` + `LocalAgentMessenger` → `engine.execute()` → `ensureSessionMailbox` 创建 mailbox → Actor 创建并注册 → 消息经 `messenger.send` 到达 inbox → Actor observation-only 消费循环 poll → 消息记录到 receivedMessages + ack → execute 完成 → destroyActor → actor 从 registry 注销。
- **roadmap §4**：`L4-8` 行从 ❌ → ✅（基础层标注本 plan）并注明 Team/Recovery/ResourceGuard 为 successor。

## Non-Goals

- **Steering 注入（将 mailbox 消息注入 ReAct 上下文）**：foundational slice 的 Actor mailbox 消费为 observation-only（poll → record → ack，不注入 session/ctx 消息）。steering 注入需 `AgentExecutionContext` 新增 steering queue 机制，且 design doc `02-execution-model.md` §四显式拒绝"修改消息历史注入"模式——正确的 steering 机制需独立设计裁定。Classification: successor plan required。
- **多租户 tenantId/userId 隔离**（vision §5.1）：`AgentExecutionContext` 无一级 tenantId/userId 字段——tenantId 嵌套在 nullable `Principal`（`Principal.java:24` `private final String tenantId`）中，且 Principal 本身在 context 上可为 null（匿名执行场景），不是可靠的 registry 索引键。foundational registry 不做 tenant 过滤。tenant 维度引入需先在 context 层规范化 tenant 标识传播。Classification: successor plan required（属 ResourceGuard Phase 5 scope）。
- **TeamManager + TeamSpec DSL + 共享任务表 + Team ACL**（vision §8 / §10 Phase 3）：团队生命周期管理、成员编排、Team ACL 权限派生、Fencing Token 协议。是完整的子系统，有独立配置面（`team-spec.xdef`）。Classification: successor plan required。
- **RecoveryManager + 跨进程接管锁 + 定时扫描 + orphan 检测**（vision §6 / §10 Phase 4）：崩溃恢复扫描、orphaned actor 检测、跨进程 session 接管锁（防多实例同时恢复同一 session——L3-4b/c/d 的显式 successor）、归档清理。Classification: successor plan required（依赖 nop-job 定时任务基础设施）。
- **ResourceGuard + 协调信道 + 资源配额 + CoordinationBusStrategy**（vision §5.2 / §10 Phase 5）：scope_claim/conflict_alert 协调信道（plan 214 `CoordinationBusStrategy` 的 successor）、资源配额强制（并发 Actor 数 / LLM 频率 / token 上限）、Fencing Token 并发写入防护。Classification: successor plan required。
- **call-agent 异步 mailbox 模型**（vision §6 / §10 Phase 2 剩余）：将 `call-agent` 工具从 fork+exec 模型迁移到 Actor mailbox REQUEST/RESPONSE 模型。Classification: successor plan required（行为变更，需独立验证）。
- **DB-backed Actor 状态持久化**（vision §6.2）：Actor 状态转换写入 DB（事务保护）、跨进程 Actor 状态共享。Classification: successor plan required。
- **多租户配额强制**（vision §5.2 资源配额表）：并发 Actor 数限制、LLM 调用频率限制、单 Agent 最大时间/token 限制。本计划交付 tenantId/userId 隔离维度（registry 索引），但不强制配额。Classification: successor plan required。
- **nop-task 集成 / nop-stream CEP 集成**（vision §2.2）：将 Actor 编排接入 nop-task DAG 执行、将 Agent 事件接入 nop-stream CEP。Classification: out-of-scope improvement。
- **XDSL 配置化**：`agent.xdef` 增加 `<actor-runtime>` 元素绑定 Actor 配置。当前通过编程 API 配置。Classification: optimization candidate。
- **`warnIfInsecureDefaults` 扩展**：Actor Runtime 缺失不构成安全风险（NoOp 下引擎走既有安全路径，Actor 是执行容器而非安全组件），无需 WARN（与 `IContributionRegistry` / `IMailbox` 裁定一致）。

## Scope

### In Scope

- 新增 `io.nop.ai.agent.runtime` 包：`AgentActorStatus` 枚举（7 值）+ `AgentActor` 对象 + `IActorRuntime` 接口 + `ActorRegistry` + `InMemoryActorRegistry` + `NoOpActorRuntime` 默认 + `InMemoryActorRuntime` 功能实现
- `DefaultAgentEngine` 接线（可选 `actorRuntime` 字段 + setter + opt-in 执行路径）
- `IMailbox` 消费循环接线（Actor 执行循环 poll → process → ack）
- `nop-ai-agent-actor-runtime-vision.md`（§3.1/§4.2/§10 标注已落地步骤）+ `01-architecture-baseline.md`（扩展点清单补 `IActorRuntime`）设计文档更新
- roadmap §4 L4-8 ❌→✅（基础层）
- focused 测试 + 端到端测试

### Out Of Scope

- 见 Non-Goals（TeamManager / RecoveryManager / ResourceGuard / call-agent 异步迁移 / DB 持久化 / 配额强制 / nop-task/stream 集成 / XDSL 均为显式 successor）

### 设计裁定

**裁定 1：Actor 与既有引擎执行模型的关系**——`IActorRuntime` 是 **opt-in 容器**，不是执行引擎的替代。shipped 默认 `NoOpActorRuntime` 下，`DefaultAgentEngine` 走既有 `supplyAsync` 路径（ForkJoinPool），行为零变化。集成商显式 `engine.setActorRuntime(new InMemoryActorRuntime(...))` 后，引擎在启动执行时额外经 `actorRuntime.createActor(...)` 注册 Actor + 关联 mailbox——Actor 是执行**容器/观察者**，不替代 `ReActAgentExecutor` 的执行逻辑。理由：(1) 与模块既有 opt-in 扩展模式一致（`IMailbox`/`IContributionRegistry`/`IUsageRecorder` 同构）；(2) 避免破坏既有 2000+ 测试的执行路径；(3) Actor 容器叠加在既有执行之上，提供 mailbox 消费 + 状态可观测 + registry 查询能力。

**裁定 2：专用线程 per Actor（Java 11 兼容）**——每个 Actor 在专用单线程 executor（`Executors.newSingleThreadExecutor`）上运行其 mailbox 消费循环。Actor 内部无并发（vision §3.1 关键约束）。模块当前 Java source = 11，`Thread.ofVirtual()` 不可用（root pom.xml `<maven.compiler.source>11`），nop-ai-agent 不引用 `GlobalExecutors`——本计划使用标准 Java 11 并发原语。VT 优化（`Thread.ofVirtual()`）是独立 successor（待模块迁移到 Java 21 release）。Actor 的专用线程负责 mailbox 消费循环（observation-only poll → record → ack），与 ReAct 执行线程（ForkJoinPool `supplyAsync`）解耦。理由：保持 Actor 心智模型（单线程执行体）与既有 ForkJoinPool 执行解耦，避免双执行路径竞争。

**裁定 3：AgentActor 身份模型**——`actorId`（UUID，运行时实例身份）+ `sessionId`（持久化会话身份）。foundational slice 采用 **1:1 映射**（一个 session 同时对应至多一个 active actor）。`createActor(sessionId, agentName)` 幂等：同 sessionId 已有 `CREATED`/`READY`/`RUNNING`/`IDLE`/`RECOVERING` 状态的 actor → 返回既有实例（非创建新的）；已有 `FAILED`/`STOPPED` 状态 → 创建新实例替换。理由：(1) 与 `runningExecutions` 的 `putIfAbsent` 语义一致（plan 197 fail-fast 并发保护）；(2) foundational slice 不引入多 Actor per session 的复杂度（vision §8 团队模式的 successor 才需要）。

**裁定 4：Actor 状态机**——7 态（`CREATED`→`READY`→`RUNNING`↔`IDLE`、`RUNNING`/`IDLE`→`FAILED`→`RECOVERING`→`RUNNING`/`IDLE`、任意态→`STOPPED`），与 vision §3.2 一致。foundational slice 实现核心转换（CREATED→READY→RUNNING→IDLE→STOPPED + →FAILED→STOPPED），`RECOVERING` 状态预留但 foundational slice 不实现自动恢复转换（RecoveryManager 是 successor）。状态经 `volatile` 保证跨线程可见性。

**裁定 5：shipped 默认不变（isEnabled guard，非异常控制流）**——`DefaultAgentEngine.actorRuntime` 默认 `NoOpActorRuntime`。`IActorRuntime.isEnabled()` 返回 `boolean`：NoOp 返回 false，功能实现返回 true。引擎在调用 `createActor` 前**先检查 `isEnabled()`**（`if (actorRuntime.isEnabled()) actorRuntime.createActor(...)`），不依赖异常类型做控制流（避免 catch-UOE 反模式）。`NoOpActorRuntime.createActor` 仍抛 `UnsupportedOperationException` 作为防御（若被直接调用而非经 isEnabled guard），但引擎路径不触发它。不引入 WARN（Actor 缺失非安全风险，与 `IMailbox`/`IContributionRegistry` 裁定一致）。

**裁定 6：mailbox 消费循环范围 = observation-only（不注入 ReAct 上下文）**——foundational slice 的 Actor 执行循环消费 mailbox 消息为 **observation-only**：`poll` 取出消息 → 记录到 Actor 的 received-messages 列表 + `LOG.info` → `ack`。**不**注入到 `AgentSession.messages` 或 `AgentExecutionContext.messages`。理由：(1) `ReActAgentExecutor` 经 `ctx.getMessages()` 构建 LLM 请求（非 `session.getMessages()`），append 到 session 不影响 in-flight 执行；(2) `AgentSession.messages` 是非同步 `ArrayList`，Actor 线程与 ForkJoinPool executor 线程并发修改会 race；(3) design doc `02-execution-model.md` §四显式**拒绝**"通过修改消息历史注入"模式。steering 注入（需 `AgentExecutionContext` 新增 steering queue 机制）是独立 successor——foundational slice 只交付 mailbox 消费的 observation 能力，使消息不丢失且可观测。**不**处理 call-agent REQUEST/RESPONSE（Phase 2 successor）、**不**处理 team 广播（Phase 3 successor）。

**裁定 7：ActorRegistry 身份维度（foundational = actorId + sessionId + agentName）**——registry 维护 `actorId → AgentActor` + `sessionId → actorId` 双索引。foundational slice 的 `AgentActor` 记录 `actorId`（UUID）+ `sessionId` + `agentName` + `createdAt` + `status` + `lastActiveAt`。**不**记录 tenantId/userId——`AgentExecutionContext` 无一级 tenantId/userId 字段，tenantId 嵌套在 nullable `Principal`（`Principal.java:24`）中且 Principal 本身可 null（匿名执行），不是可靠的索引键。多租户隔离维度（vision §5.1）是 ResourceGuard successor（Phase 5），需要先规范化 tenant 标识传播。foundational registry 的 `getActiveActors()` 返回全部 active actor（无 tenant 过滤）。

## Execution Plan

### Phase 1 - Actor 契约表面 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.runtime` 包（新增枚举 + 对象 + 接口 + registry + NoOp 默认）、`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md`、`ai-dev/design/nop-ai-agent/01-architecture-baseline.md`

- Item Types: `Decision` | `Proof`

- [x] **裁定并落档** Actor 与既有引擎执行模型关系（opt-in 容器）+ 专用线程 per Actor（Java 11 兼容，VT successor）+ 身份模型（1:1 映射 + 幂等 createActor）+ 状态机（7 态 + foundational 核心转换）+ shipped 默认策略（isEnabled guard）+ mailbox 消费范围（observation-only，不注入 ReAct 上下文）（设计裁定 1-7 写入设计文档）
- [x] 定义 `AgentActorStatus` 枚举：7 值（CREATED / READY / RUNNING / IDLE / FAILED / RECOVERING / STOPPED），Javadoc 明确每个状态的进入条件与合法转换（状态转换图写入 Javadoc）
- [x] 定义 `AgentActor` 对象：不可变标识（`actorId` UUID + `sessionId` + `agentName` + `createdAt`）+ 可变运行时状态（`volatile AgentActorStatus status` + `volatile long lastActiveAt`）+ received-messages 列表（observation-only 消费记录）+ 可选 `IMailbox` 引用；提供 `getStatus()` / `updateStatus(newStatus)` / `getLastActiveAt()` / `touch()` / `getMailbox()` / `getReceivedMessages()` 访问器；Javadoc 明确线程安全契约（status/lastActiveAt volatile，received-messages 在 Actor 单线程内访问）。**不**含 tenantId/userId/agentModel/session/cancelToken/parentActorId（这些是 vision §3.1 的后续扩展，foundational slice 仅含基础标识——见 Non-Goals + Minor 裁定）
- [x] 定义 `IActorRuntime` 接口行为契约：`isEnabled() → boolean`（NoOp=false, 功能=true——引擎据此判断是否走 Actor 路径，非异常控制流）、`createActor(sessionId, agentName) → AgentActor`（幂等）、`getActor(actorId) → Optional<AgentActor>`、`getActorBySession(sessionId) → Optional<AgentActor>`、`getActiveActors() → Collection<AgentActor>`、`destroyActor(actorId) → boolean`（优雅停止）、`destroyAll() → int`。Javadoc 明确生命周期契约、线程安全、幂等语义
- [x] 定义 `ActorRegistry` 接口 + `InMemoryActorRegistry` 实现：`register(AgentActor)` / `unregister(actorId)` / `get(actorId)` / `getBySession(sessionId)` / `getAll()` 双索引（`ConcurrentHashMap`）线程安全实现。**不**含 tenant/user 过滤（successor）
- [x] 实现 `NoOpActorRuntime`（singleton，`isEnabled()` 返回 false；`createActor` 抛 `UnsupportedOperationException` 仅作防御——引擎经 `isEnabled()` guard 不触发此路径；查询返回空 Optional / 空集合；destroy no-op）
- [x] `nop-ai-agent-actor-runtime-vision.md` §3.1/§4.2/§10 标注 Phase 1 已落地步骤（ActorRuntime ✅ / ActorRegistry ✅ / Virtual Thread 调度 → successor 待 Java 21 迁移，TeamManager/RecoveryManager/ResourceGuard 标 successor）；§3.2 状态机图标注 foundational 核心转换 vs RECOVERING successor
- [x] `01-architecture-baseline.md` §六 通信模型 已补 `IActorRuntime` 段落（与 `IMailbox`/`IContributionRegistry` 同级 opt-in 扩展点描述）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AgentActorStatus` 枚举文件存在于 `io.nop.ai.agent.runtime` 包，恰好 7 个值且与 vision §3.1 一致
- [x] `AgentActor` + `IActorRuntime`（含 `isEnabled()`）+ `ActorRegistry` + `InMemoryActorRegistry` + `NoOpActorRuntime` 文件存在，行为契约 Javadoc 清晰（状态转换图、线程安全契约、幂等语义、lifecycle 契约）
- [x] `NoOpActorRuntime.isEnabled()` 返回 false（引擎据此跳过 Actor 路径，非异常控制流）；`createActor` 抛 `UnsupportedOperationException` 仅作防御
- [x] `nop-ai-agent-actor-runtime-vision.md` §3.1/§4.2/§10 标注 Phase 1 已落地步骤（ActorRuntime ✅ / ActorRegistry ✅ / Virtual Thread 调度 → successor 待 Java 21 迁移）；§3.2 状态机图标注 foundational 核心转换 vs RECOVERING successor
- [x] `01-architecture-baseline.md` §六 通信模型 已补 `IActorRuntime` 段落（与 `IMailbox`/`IContributionRegistry` 同级 opt-in 扩展点描述）
- [x] **No new test required for Phase 1**：Phase 1 只交付契约表面（枚举 + 接口 + NoOp 默认），无行为逻辑需测试；NoOp 行为验证在 Phase 2 focused 测试中覆盖（Minimum Rules #25）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - InMemoryActorRuntime 功能实现 + 标准线程执行循环 + 引擎接线 + IMailbox observation-only 消费 + 测试

Status: completed
Targets: `io.nop.ai.agent.runtime.InMemoryActorRuntime`、`DefaultAgentEngine`、`io.nop.ai.agent.runtime` 测试

- Item Types: `Proof` | `Follow-up`

- [x] 实现 `InMemoryActorRuntime`：依赖 `ActorRegistry`（`InMemoryActorRegistry`）+ `DefaultAgentEngine` 引用（用于 `getSessionMailbox(sessionId)` 获取既有 mailbox）；`createActor` 创建 `AgentActor`（状态 CREATED→READY）+ 注册 registry + 经 `engine.getSessionMailbox(sessionId)` 获取 engine 已创建的 per-session `IMailbox`（若存在，关联到 Actor；若 null 则 Actor 无 mailbox 消费——graceful）+ 启动专用单线程 executor（`Executors.newSingleThreadExecutor`，Java 11 兼容）执行 observation-only 消费循环
- [x] 实现 Actor observation-only 消费循环：`poll` mailbox（带超时，默认 1s）→ 有消息则记录到 Actor received-messages 列表 + `LOG.info`（**不**注入 session/ctx 消息——裁定 6）+ `ack`→ 无消息则更新 lastActiveAt 并 idle 循环；循环退出条件：actor status 为 STOPPED/FAILED 或线程被 interrupt；状态转换 READY→RUNNING（首次 poll）→IDLE（poll 超时无消息）→RUNNING（收到消息）→STOPPED（destroyActor）；循环异常（poll/ack 抛异常）→ log + 状态转 FAILED（非静默吞没）
- [x] `DefaultAgentEngine` 新增 `actorRuntime` 字段（默认 `NoOpActorRuntime`）+ `setActorRuntime` setter；三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）在 `supplyAsync` lambda 入口（session 状态设为 running 之后、ReAct 执行之前）**若 `actorRuntime.isEnabled()`** 则经 `actorRuntime.createActor(sessionId, agentName)` 注册 Actor（NoOp `isEnabled()==false` 时跳过 = 走既有路径，无异常控制流）；三个入口点的 `finally` 块经 `actorRuntime.getActorBySession(sessionId).ifPresent(a -> actorRuntime.destroyActor(a.getActorId()))` 注销（无需修改 `CancelHandle` 或 `AgentExecutionContext`——actorId 经 registry 按 sessionId 反查）
- [x] 编写 focused 测试：Actor 状态转换（CREATED→READY→RUNNING→IDLE→STOPPED + →FAILED）、registry 注册/注销/查询/双索引、createActor 幂等（同 sessionId 返回既有实例）、destroyActor 优雅停止（status→STOPPED + registry 注销 + 线程 interrupt）、NoOp 默认 `isEnabled()==false`
- [x] 编写 mailbox observation-only 消费 focused 测试：offer 消息到 engine-created mailbox → Actor 执行循环 poll → 消息记录到 actor.receivedMessages + LOG → ack（mailbox 中消息标记完成）；断言消息**未**出现在 session.messages 或 ctx.messages（observation-only 验证）；nack 重投递路径
- [x] 编写端到端测试：配置 `InMemoryActorRuntime`（注入 `DefaultAgentEngine` 引用）+ `mailboxFactory`（`DeferredAckMailbox`）+ `messenger`（`LocalAgentMessenger`）→ `engine.execute()` → `ensureSessionMailbox` 创建 mailbox → Actor `isEnabled()==true` → `createActor` 注册 Actor（registry 可查）+ 获取既有 mailbox → 消息经 `messenger.send` 到达 inbox → mailbox offer → Actor 循环 poll → 消息记录到 receivedMessages + ack → execute 完成 → finally `getActorBySession` + `destroyActor` → actor 从 registry 注销

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `InMemoryActorRuntime` 线程安全（ConcurrentHashMap registry + per-actor 单线程 executor），`isEnabled()` 返回 true，createActor 幂等，destroyActor 优雅停止（status STOPPED + registry 注销 + 线程 interrupt）
- [x] Actor observation-only 消费循环正确消费 engine-created `IMailbox`（poll → record to receivedMessages → ack），消息**不**注入 session.messages 或 ctx.messages（裁定 6 observation-only 验证）
- [x] **接线验证**（Minimum Rules #23）：三个执行入口点（doExecute/resumeSession/restoreSession）的 `supplyAsync` lambda 入口在 `isEnabled()==true` 时调用 `createActor`（断言 registry 中可查到 actor），三个 `finally` 块经 `getActorBySession` + `destroyActor` 注销（断言 registry 中 actor 已注销）；`isEnabled()==false`（NoOp 默认）时 createActor 不被调用
- [x] **端到端验证**（Minimum Rules #22）：从 `engine.setActorRuntime(new InMemoryActorRuntime(engine))` + `engine.setMailboxFactory(...)` + `engine.setMessenger(...)` 入口，经 ensureSessionMailbox → Actor 创建注册 → mailbox observation-only 消费（消息记录到 receivedMessages + ack）→ execute 完成 → Actor 注销的完整路径跑通（actor 创建断言 + observation 消费断言 + destroy 断言）
- [x] **无静默跳过**（Minimum Rules #24）：NoOp `isEnabled()` 返回 false（引擎跳过，非异常控制流）；Actor 消费循环 poll/ack 异常 log + 状态转 FAILED（非静默吞没）；循环中 poll 到 null（超时）正常 idle 循环（非异常路径）
- [x] shipped 默认（`NoOpActorRuntime`，`isEnabled()==false`）下既有测试零回归（引擎走既有 supplyAsync 路径，createActor 不被调用）
- [x] 新增功能各有对应 focused 测试覆盖（Actor 状态转换 / registry / createActor 幂等 / destroyActor / mailbox observation-only 消费 / NoOp 默认 isEnabled 各有测试）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] roadmap §4 L4-8 ❌→✅（基础层标注本 plan）已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IActorRuntime` 契约表面（AgentActorStatus 枚举 + AgentActor 对象 + 接口 + ActorRegistry + NoOp 默认）已落地
- [x] `InMemoryActorRuntime` 功能实现 + 标准线程（Java 11）observation-only 消费循环 + `IMailbox` 消费接线 + 引擎接线已落地
- [x] 端到端：engine.execute → ensureSessionMailbox → Actor 创建注册 → mailbox observation-only 消费 → execute 完成 → Actor 注销，完整路径跑通
- [x] shipped 默认（NoOp，`isEnabled()==false`）下既有测试零回归
- [x] 必要 focused verification 已完成（Actor 状态转换 / registry / createActor 幂等 / destroyActor / mailbox observation-only 消费 / NoOp isEnabled 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（steering 注入 / TeamManager / RecoveryManager / ResourceGuard / call-agent 异步迁移 / DB 持久化 / 配额强制 / 多租户隔离 / nop-task/stream 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-actor-runtime-vision.md` + `01-architecture-baseline.md`）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`IActorRuntime` 在运行时被 `DefaultAgentEngine` 三个入口点经 `isEnabled()` guard 调用（不只类型存在），（b）Actor 消费循环真实 poll/consume engine-created mailbox，（c）无空方法体/静默跳过作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；TeamManager / RecoveryManager / ResourceGuard / call-agent 异步迁移 / DB 持久化 / 配额强制 / nop-task/stream 集成 / XDSL 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Steering 注入机制**：将 mailbox 消息注入 ReAct 上下文（需 `AgentExecutionContext` steering queue + design `02-execution-model.md` §四 steering 机制设计裁定）。Classification: successor plan required。
- **Virtual Thread 优化**：当模块迁移到 Java 21 release 后，将 Actor 专用线程 executor 切换为 `Thread.ofVirtual()`。Classification: optimization candidate。
- **TeamManager + TeamSpec DSL + Team ACL + Fencing Token**（vision §8 / §10 Phase 3）：团队生命周期 + 成员编排 + ACL 权限派生。Classification: successor plan required。
- **RecoveryManager + 跨进程接管锁 + orphan 检测**（vision §6 / §10 Phase 4）：崩溃恢复扫描 + 跨进程 session 接管锁（L3-4b/c/d 的显式 successor）+ 归档清理。Classification: successor plan required（依赖 nop-job）。
- **ResourceGuard + CoordinationBusStrategy + 资源配额**（vision §5.2 / §10 Phase 5）：协调信道（plan 214 `CoordinationBusStrategy` successor）+ 配额强制 + Fencing Token 并发防护。Classification: successor plan required。
- **call-agent 异步 mailbox 模型**（vision §6 / §10 Phase 2 剩余）：将 `call-agent` 从 fork+exec 迁移到 Actor mailbox REQUEST/RESPONSE。Classification: successor plan required。
- **DB-backed Actor 状态持久化**（vision §6.2）：Actor 状态转换写 DB + 跨进程共享。Classification: successor plan required。
- **多租户配额强制**（vision §5.2）：并发 Actor 数 / LLM 频率 / token 上限强制。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 `<actor-runtime>` 元素。Classification: optimization candidate。
- **nop-task / nop-stream 集成**（vision §2.2）：Actor 编排接入 nop-task DAG + 事件接入 nop-stream CEP。Classification: out-of-scope improvement。

## Closure

Status Note: Plan 218 交付了 nop-ai-agent Actor Runtime 平台层基础原语（AgentActorStatus + AgentActor + IActorRuntime + ActorRegistry + NoOpActorRuntime shipped 默认 + InMemoryActorRuntime 功能实现 + DefaultAgentEngine 三入口点 opt-in 接线 + IMailbox observation-only 消费循环），闭合 L4-8 roadmap gap 的基础层。Platform Layer 的其余组件（TeamManager / RecoveryManager / ResourceGuard / DB-backed 持久化 / 跨进程接管锁 / Fencing Token / call-agent 异步 mailbox 模型 / steering 注入 / 多租户隔离 / 配额强制 / VT 优化 / nop-task/stream 集成 / XDSL）均为显式 Non-Goal 独立 successor。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: Independent general-purpose subagent (task ses_1302571f1ffePhQohIXS6tAhXj)
- Audit Session: ses_1302571f1ffePhQohIXS6tAhXj
- Evidence:
  - Task 1 (Contract surface): PASS — 7 files verified in `io.nop.ai.agent.runtime`: `AgentActorStatus` (7 values: CREATED/READY/RUNNING/IDLE/FAILED/RECOVERING/STOPPED), `AgentActor`, `IActorRuntime` (with `isEnabled()`), `ActorRegistry`, `InMemoryActorRegistry`, `NoOpActorRuntime` (`isEnabled()==false`), `InMemoryActorRuntime`
  - Task 2 (Engine wiring — Anti-Hollow): PASS — `DefaultAgentEngine.actorRuntime` field (default `NoOpActorRuntime.noOp()`), `setActorRuntime` setter, `actorRuntime.createActor(...)` in all 3 entry points (doExecute/resumeSession/restoreSession, count=3), `actorRuntime.getActorBySession(...).ifPresent(...destroyActor...)` in all 3 finally blocks (count=3)
  - Task 3 (Observation-only consumption loop — Anti-Hollow): PASS — `InMemoryActorRuntime.runConsumptionLoop` calls `mailbox.poll()` → `actor.addReceivedMessage(entry)` → `LOG.info(...)` → `mailbox.ack()`; poll/ack exceptions `LOG.error` + status→FAILED (not swallowed); `AgentActor` has zero session/context fields (observation-only verified at type level)
  - Task 4 (Tests): PASS — 6 test files: `TestAgentActor` (10 tests), `TestInMemoryActorRegistry` (12 tests), `TestNoOpActorRuntime` (8 tests, verifies `isEnabled()==false` + UOE), `TestInMemoryActorRuntime` (12 tests, idempotent createActor + destroyActor + state transitions), `TestActorMailboxConsumption` (5 tests, poll→record→ack + FIFO + observation-only), `TestActorRuntimeEndToEnd` (2 tests, full engine.execute E2E + NoOp zero-regression). `./mvnw test -pl nop-ai/nop-ai-agent -am`: 2024 tests, 0 failures, 0 errors
  - Task 5 (Roadmap): PASS — `nop-ai-agent-roadmap.md:247` L4-8 shows ✅
  - Task 6 (Design docs): PASS — `01-architecture-baseline.md` has "Actor Runtime 基础层已落地（L4-8 已落地）" paragraph; `nop-ai-agent-actor-runtime-vision.md` §10 Phase 1 marked ✅ + §4.2 ActorRuntime/ActorRegistry marked ✅
  - Anti-Hollow 检查结果: engine genuinely invokes createActor/destroyActor in all 3 entry points behind isEnabled() guard; consumption loop performs real poll→record→ack with error→FAILED transitions; no empty method body/silent skip as implementation
  - Deferred 项分类检查: steering 注入 / TeamManager / RecoveryManager / ResourceGuard / call-agent 异步迁移 / DB 持久化 / 配额强制 / 多租户隔离 / nop-task/stream / XDSL / VT 优化 均为显式 Non-Goal 独立 successor，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work; all Non-Goals are explicit successor plans (see Non-Blocking Follow-ups section)

## Follow-up handled by 220-nop-ai-agent-steering-injection.md

Steering 注入机制（Non-Blocking Follow-ups 第一条，标 `successor plan required`）已由 successor plan `ai-dev/plans/220-nop-ai-agent-steering-injection.md` 接管：在 `AgentExecutionContext` 新增线程安全 steering 消息队列，ReAct 循环每轮工具执行后检查队列并注入 steering 消息，Actor 消费循环从 observation-only 升级为 steering-injection（poll → enqueue 到 ctx steering queue → ack），闭合"邮箱消息 → ReAct 推理上下文"注入主路径。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。

## Follow-up handled by 221-nop-ai-agent-cross-process-session-takeover-lock.md

RecoveryManager + 跨进程接管锁（Non-Blocking Follow-ups 第四条，标 `successor plan required`）已由 successor plan `ai-dev/plans/221-nop-ai-agent-cross-process-session-takeover-lock.md` 接管：交付 `ISessionTakeoverLock` 接口 + `NoOpSessionTakeoverLock` shipped 默认 + `DbSessionTakeoverLock` 功能实现（独立 `ai_agent_session_lock` 表 + lease/TTL CAS 锁 + stale-lock 过期抢占）+ `DefaultAgentEngine` 三入口点 tryAcquire/release 接线 + `restorePendingSessions` isHeld 跳过增强，闭合多实例部署中 session 恢复的 double-execution correctness gap。nop-job 定时扫描 / orphan liveness / 恢复策略 / 超时中止 / 归档为独立 successor。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
