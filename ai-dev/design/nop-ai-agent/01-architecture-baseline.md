# Nop AI Agent 架构基线

**日期**：2026-06-06
**范围**：`nop-ai-agent` 模块
**状态**：active

---

## 一、设计结论

1. 系统分为五层：Application → Platform → Agent Engine → LLM → Tool
2. Agent 是配置对象（`AgentModel`），Engine 负责执行——配置与执行严格分离
3. 核心对象有五个：`AgentModel`、`IAgentEngine`、`AgentExecutionContext`、`IAgentExecutor`、`AgentEventPublisher`
4. Agent Engine Layer 不直接依赖具体 LLM Provider 或具体工具实现

## 二、架构分层

```
┌─────────────────────────────────────┐
│          Application Layer          │  ← 具体应用（coder, shell, web）
├─────────────────────────────────────┤
│          Platform Layer             │  ← Flow 编排、多 Agent 协作
├─────────────────────────────────────┤
│          Agent Engine Layer         │  ← 本篇焦点
│  ┌───────────┬───────────────────┐  │
│  │  Executor │  Hook/Skill/Plan  │  │
│  │  (ReAct)  │  (扩展机制)        │  │
│  └───────────┴───────────────────┘  │
│  ┌───────────┬───────────────────┐  │
│  │  Session  │  Memory/Store     │  │
│  │  (会话)    │  (状态持久化)      │  │
│  └───────────┴───────────────────┘  │
├─────────────────────────────────────┤
│          LLM Layer                  │  ← nop-ai-llm / nop-ai-llms
├─────────────────────────────────────┤
│          Tool Layer                 │  ← nop-ai-toolkit / nop-ai-tools
└─────────────────────────────────────┘
```

## 三、模块边界

| 模块 | 职责 | 与 Agent Engine 的关系 |
|------|------|----------------------|
| `nop-ai-agent` | Agent DSL 模型 + 执行引擎 | 本篇设计范围 |
| `nop-ai-core` | AI 核心抽象（ChatMessage, ChatOptions 等） | Agent 引擎依赖的基础类型 |
| `nop-ai-llm` | LLM 调用抽象 | Agent 引擎通过此层调用 LLM |
| `nop-ai-llms` | 具体 LLM Provider 实现 | Agent 引擎不直接依赖 |
| `nop-ai-toolkit` | 工具 DSL（tool.xdef） | Agent 引擎通过此层发现和调用工具 |
| `nop-ai-tools` | 具体工具实现 | Agent 引擎不直接依赖 |

**边界约束**：Agent Engine Layer 不直接依赖 `nop-ai-llms` 和 `nop-ai-tools`。所有 LLM 调用通过 `nop-ai-llm` 抽象层，所有工具调用通过 `nop-ai-toolkit` 抽象层。

## 四、核心对象职责契约

引擎层的核心对象通过职责契约定义，不通过字段列表。每个对象的字段定义属于源码范畴。

| 对象 | 职责契约 | 设计约束 |
|------|----------|---------|
| `AgentModel` | 从 `agent.xdef` 装载得到的纯配置对象，是 DSL 在引擎层的投影 | 不持有执行逻辑，不持有状态；可被 Delta 定制 |
| `IAgentEngine` | Agent 的 Actor 入口：接受消息，路由到对应 Session 的 Agent Actor | 无状态——每次调用根据 sessionId 查找或创建 AgentActor，投递消息后立即返回；执行结果通过 `AgentEventPublisher` 异步推送 |
| `Agent` | 无状态执行体，根据 AgentModel 的配置驱动执行循环 | 不持有状态——AgentSession 作为上下文传入 |
| `AgentSession` | 按 sessionId 获取的独立状态对象，持久化跨请求存在 | 状态恢复的载体；可以被任意服务实例接管 |
| `AgentExecutionContext` | 单次执行的全部内存态数据的容器 | 生命周期与单次执行绑定，不跨执行复用 |
| `IAgentExecutor` | 定义执行模式（ReAct、单轮等）的策略接口 | 不持有配置——从上下文中读取；Layer 1 只实现 ReAct；无流式模式（引擎以完整消息粒度执行） |
| `AgentEventPublisher` | 将执行状态变化投影为外部可观察的事件流 | 事件类型稳定；不修改执行状态 |
| `IAgentMemory` | Agent 的三层记忆管理 | 短期记忆（context window 管理 + compaction）是核心职责；Working Memory（Layer 2）和长期记忆（Layer 4）按层引入 |
| `IMessageFormat` | Provider 无关的统一消息格式 | 仅 2 角色(user/assistant)，6 种 ContentBlock，ToolResultReference 懒加载。见 `nop-ai-agent-llm-layer.md` |

### Memory 模型（三层）

基于 10 框架调研（LangGraph、CrewAI、AutoGen、OpenCode、DeepAgents、PilotDeck、Reasonix、EdgeClaw、Claude Code、Codex CLI）的源码级对比：

| 层级 | 内容 | 架构层 |
|------|------|--------|
| **短期记忆** | Context window 内的消息历史，含 5 层渐进 compaction（Layer 0-4） | Layer 1 核心 + Layer 2 扩展 |
| **Working Memory** | Per-session KV store，支持 JSON schema 验证或 Markdown 模板，session 启动时注入 system prompt，Agent 通过工具读写 | Layer 2 |
| **长期记忆** | IMessageService + 向量存储 + retain/recall/reflect 工具 + EdgeClaw 风格的 captureTurn/retrieve | Layer 4 |

Working Memory 是 Session 状态的一部分（持久化）。短期记忆是 Agent Engine 的运行时职责（compaction 触发和执行）。长期记忆是独立子系统。

### Cache-First 三区域上下文架构

短期记忆内部采用 Reasonix 的三区域划分，优化 LLM 缓存命中率：

| 区域 | 内容 | 变更策略 | 缓存特性 |
|------|------|---------|---------|
| **ImmutablePrefix** | system prompt + tool specs + few-shots | SHA-256 指纹固定后不变 | 缓存命中候选 |
| **AppendOnlyLog** | 对话消息 | 单调追加，滑动窗口 200 条 | 版本号递增 |
| **VolatileScratch** | reasoning, planState, notes | 每轮重置 | 永不上传到 LLM |

实测效果（Reasonix）：435M input tokens, 99.82% cache hit, ~80% 成本节省。

三区域划分在 `IContextGovernor`（Layer 2）的实现中体现。详见 `nop-ai-agent-llm-layer.md` §八和 `nop-ai-agent-reliability.md` §7。

### 配置、执行、状态三者分离

**决策**：AgentModel、Agent、AgentSession 三个层次各司其职。

- **AgentModel**：纯配置对象，从 `agent.xdef` 装载，不持有执行逻辑和状态
- **Agent**：无状态执行体，不持有任何跨请求的状态
- **AgentState/AgentSession**：独立状态对象，按 sessionId 获取，作为上下文传入 Agent

同一个 Agent（配置）可以被多个 Session 复用。崩溃恢复时重建 AgentSession 即可。

**理由**：
- 与 `agent.xdef` DSL 的配置语义一致
- 参考 openai-agents 的成功实践——Agent 是 data object，Runner 负责执行
- 便于 Delta 定制——只改配置不改执行逻辑
- 便于测试——可以独立测试引擎而不需要实例化具体 Agent
- 便于恢复——重建 AgentSession 即可，Agent 无需特殊处理

**拒绝了**：agentscope-java 的"Agent 即执行器"模式（Agent 类既持有配置又驱动循环）。理由是 Nop 的 DSL-First 原则要求配置和执行严格分离。

### 会话与执行分离

**决策**：AgentSession 和 ExecutionContext 是两个独立对象。

- AgentSession 是持久化的状态对象，跨请求存在，按 sessionId 获取
- ExecutionContext 是临时的，与单次执行绑定，包含本次执行的内存态数据

**理由**：
- 参考 solon-ai 的 `AgentSession` vs `FlowContext` 分离
- 持久化策略不应影响执行逻辑
- Session 可以被替换为不同实现（文件、数据库、内存）

## 五、部署模型

系统设计为**天然分布式的 actor 系统**：

- **单进程是最简部署**：一个 JVM 进程内运行多个 Agent 实例，每个实例一个 Virtual Thread，共享内存消息队列
- **多实例自动扩展为多进程**：部署多个服务实例，Agent 间通信透明切换为跨进程消息传递，无需修改业务逻辑
- **前端 Gateway**：接收用户请求，存储到后端持久化层（数据库），后端 Agent 实例从持久化层消费消息并执行

```
用户请求 → Gateway → 持久化层(DB) → Agent 实例消费并执行
                        ↑                    |
                        └── Agent 结果写回 ──┘

前端/调用方通过 IMessageService 抽象获取完成通知，
具体推送机制（轮询、SSE、GraphQL Subscription、Webhook）
是 Gateway/前端的实现选择，不暴露给 Agent 层。
```

## 六、通信模型

Agent 间通信（包括 `call-agent` 同步调用、`send-message` 异步消息）统一通过 **IMessageService 抽象层**：

- **单进程**：`LocalMessageService`（内存队列 + `CompletableFuture`），延迟极低
- **多实例**：DB-backed `MessageService`，请求/结果通过数据库传递，支持跨进程路由
- **引擎层不感知部署拓扑**：`call-agent` 工具只往 mailbox 发消息、等待响应，不知道对方在同一进程还是远程实例
- **IMessageService 是可能出错的基础设施**：所有调用都包含超时、重试、错误处理。调用方不假设底层可靠
- **`call-agent` 是运行时提供给 Agent 的能力**：Agent 通过工具调用 `call-agent`，引擎和 actor 调度系统负责实际的 session fork、消息路由、超时和恢复

**注意**：不同 IMessageService 实现的故障语义不同（内存实现崩溃丢消息，DB-backed 不丢）。这是预期行为——调用方通过超时+重试+幂等处理应对。

```
Agent A                    IMessageService                    Agent B
  ├── call-agent(request) ──→ offer to B's mailbox ──────────→ B 消费并执行
  │                            (内存 or DB)                      │
  │   A 挂起等待响应            │                                 │ B 完成后写回
  │                            │←── offer result to A's mailbox ─┘
  │   A 恢复                    │
```

## 七、存储模型

Agent 的存储分为两个独立抽象：

**1. 虚拟文件系统（VFS）** — Agent 的工作空间：

- Agent 通过统一的文件系统接口读写工作文件（代码文件、生成产物、配置文件等）
- 实现层可以是本地文件系统、数据库、对象存储，或它们的组合
- VFS 是 Agent 工具层的存储抽象，Agent 看到的是文件路径和文件内容
- 多实例部署时，VFS 后端切换为共享存储（数据库），Agent 代码无需修改

**2. 持久化接口** — Agent 的结构化状态：

- Session 状态、Plan 状态等结构化数据通过独立的持久化接口（IOrmSession）读写
- Agent 间通信（消息）走 IMessageService，不走 VFS

**边界**：VFS 管工作文件，IMessageService 管通信，持久化接口管结构化状态。三者不互相替代。

### 后端服务访问

Agent 通过 REST 调用和 GraphQL 调用访问后端服务，不引入额外的外部协议层。

## 八、Session 模型

用户提交请求后创建一个**逻辑 Session**。Session 是 Agent 活动的持久化容器：

- Session 跨请求存在，支持暂停/恢复
- Agent 实例的生命周期绑定到 Session：在 Session 内创建、在 Session 内销毁
- Session 内可以新建 Agent 实例，也可以访问该 Session 中曾经存在过的 Agent（恢复/重用）
- 子 Agent 在 Session 内派生，形成 Session Tree
- Session 状态持久化到数据库，任何服务实例都可以接管恢复。并发接管的锁机制由 actor 调度系统负责

### Agent 身份模型

- **配置名**（agent.xml name）：静态定义，描述 Agent 的行为模板
- **实例 ID**（actorId / UUID）：运行时概念，每次实例化生成唯一 ID
- 同一配置名可以在同一 Session 内被实例化为多个并行 Agent
- Agent 间通信通过实例 ID 寻址，不通过配置名

## 九、多租户与资源隔离

所有租户共享同一套资源池（Virtual Thread 调度、LLM 调用、数据库连接），通过**配额限制**防止单个租户占满资源：

- 每个租户有最大并发 Agent 数、LLM 调用频率上限、存储配额
- 配额在 Agent 启动前检查，超限则排队或拒绝
- Virtual Thread 本身开销极小，不需要按租户分池；瓶颈在 LLM API 调用和外部资源，这些通过配额控制
- Nop 平台的 `IContext`（tenantId/userId）天然支持租户标识

## 十、关键设计决策总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 核心定位 | 大规模无人值守自动化 | Nop 是自动化基础设施，不是终端工具 |
| Agent 是配置还是执行器 | 配置 | DSL-First，便于 Delta 定制 |
| 异步模型 | `CompletionStage` + `IMessageService` | Java 标准，Actor 邮箱模型。`Flow.Publisher` 仅用于外部事件观察（`AgentEventPublisher`），不用于引擎内部决策 |
| 多 Agent 协作 | call-agent 工具 + Agent-as-Subprocess | 与工具 DSL 一致，进程隐喻清晰 |
| Agent 派生 | fork + exec 模型 | 操作系统进程隐喻，语义清晰可预测 |
| 内部能力 | 薄接口 + 可 Agent 化 | Layer 1 硬编码，Layer 2+ 逐步 Agent 化 |
| 工具参数格式 | XML 为主 + JSON Schema 兼容 | 兼顾 XLang 生态和不同 LLM Provider 的格式偏好 |
| 会话存储 | VFS `.nop/` 逻辑路径（Layer 1 文件后端，Layer 4+ DB 后端） | Event Sourcing + VFS，见 session-and-storage.md §4 §5 |
| 错误处理 | 分类 + 策略模式 | 通过 Hook/Interceptor 实现 |
| 多 Agent 并行协同 | Layer 1 fail-fast，Layer 4+ 协调 | 先检测后解决 |
| 消息格式 | CanonicalMessage（IMessageFormat） | Provider 无关，2 角色 6 Block 类型，见 `nop-ai-agent-llm-layer.md` |
| Provider 适配 | IModelDialect（Formatter pattern） | 参考 AgentScope Java，与 Nop IDialect 一致 |
| 动态行为准入 | ITalent（Solon AI pattern） | 上下文依赖的能力和工具集激活 |
| 架构组织 | 四层接口（Core → Execution → Reliability → Platform） | 扩展通过添加接口实现，不通过阶段切换 |

### Plan 与 Todo 系统

Agent 的任务管理由两个互补机制组成：

- **Plan 系统**：结构化的执行计划，定义步骤、依赖和完成条件。对应 `agent-plan.xdef`。Plan 属于单个 Agent，不传递给子 Agent
- **Todo 系统**：待办工作列表，类似 opencode 的 todo 机制。作为 Plan 的轻量级补充，同样属于单个 Agent

### 与传统 Actor 框架的对应关系

| 分布式 Actor 概念 | Nop AI Agent 对应 | 说明 |
|-------------------|-------------------|------|
| Actor | Agent + AgentSession | Agent 无状态执行，AgentSession 持有状态，组合构成完整 Actor |
| Mailbox | IMessageService（抽象层） | 内存队列或 DB-backed 实现，引擎不感知 |
| ActorSystem | AgentRuntime + IMessageService | 管理生命周期和通信基础设施 |
| ActorRef | actorId + 通信抽象 | 位置透明，不绑定具体进程 |
| Supervisor | RecoveryManager | 崩溃恢复、重启策略 |
| Dispatch | Virtual Thread 调度 | 单进程内轻量级，多进程间由 IMessageService 路由 |

---

## 与其他文档的关系

- `00-vision.md` — 本篇遵循的设计原则和约束
- `02-execution-model.md` — 执行模型详细设计（双循环、Hook、Steering）
- `nop-ai-agent-context-model.md` — 上下文模型（维度、继承、fork）
- `nop-ai-agent-multi-agent.md` — 多 Agent 协同（冲突检测、资源竞争）
- `nop-ai-agent-llm-layer.md` — LLM 层设计（IMessageFormat, IModelDialect, ITalent, IModelRouter, IRetryPolicy）
- `nop-ai-agent-roadmap.md` — 分层架构与实施路线（四层接口组织）
