# AgentScope Java 技术分析（更新版）

> Status: updated
> Date: 2026-07-16 (初始: 2026-06-05)
> Scope: ~/ai/agentscope-java — 阿里巴巴 Java AI Agent 框架
> Conclusion: AgentScope 是全响应式 Agent 运行时，与 nop-ai-agent 互补，可互相借鉴设计模式

## Context

- AgentScope Java 是阿里巴巴通义实验室推出的 Java 版 Agent 框架，2025 年 12 月发布 1.0
- 定位为 JVM 上的 Agent-oriented programming 框架，直接竞品：Spring AI, LangChain4j
- 对 Nop 意义：同为 Java 生态，可对比 IoC、配置、模型抽象等设计决策

## Analysis

### 项目定位

- **组织**: 阿里巴巴 AgentScope Team
- **许可**: Apache 2.0
- **版本**: 1.0.12 (latest release), 1.1.0-SNAPSHOT (dev)
- **Java**: 17+（刻意避免 21+ preview features）
- **核心**: 基于 Project Reactor 的全响应式 Agent 框架

### 模块结构

```
agentscope-core/          核心（Agent, Model, Tool, Memory, Hook, Pipeline, RAG, Plan）
agentscope-harness/       安全沙箱 & 运行时
agentscope-extensions/    25+ 扩展模块
  Mem0 长期记忆, Dify/Bailian/RAGFlow/Haystack RAG, A2A 协议,
  Nacos 服务发现, XXL-Job 调度, Redis/MySQL Session,
  Spring Boot/Quarkus/Micronaut Starters, Studio 调试, Kotlin DSL
agentscope-examples/      15+ 示例（werewolf, boba-tea-shop, multiagent-patterns）
```

### 核心抽象

#### Agent 体系

```
Agent (顶层接口, 三合一 Facade)
├── CallableAgent     — 同步/异步调用，生成响应
├── StreamableAgent   — 流式执行事件（@Deprecated, v1）
└── ObservableAgent   — 被动观察消息，不生成回复

AgentBase (abstract, implements Agent)
├── ReActAgent (core module — ReAct 循环实现)
└── HarnessAgent (harness module — workspace/sandbox/subagent 编排)
```

**关键设计决策**：
- **Agent 接口是三合一 Facade**：组合了 `CallableAgent`、`StreamableAgent`、`ObservableAgent` 三个能力接口
- **Memory 不属于核心接口**：Memory 管理是具体 Agent 实现（如 `ReActAgent`）的职责
- **Observable 模式**支持多 Agent 协作中的被动消息接收

**AgentBase 关键机制**：
- **Hook 生命周期管理**：`CopyOnWriteArrayList<Hook>` + 按优先级排序
- **MsgHub 订阅者管理**：`hubSubscribers: ConcurrentHashMap<String, List<AgentBase>>`
- **协作式中断**：`checkInterrupted()` 检查 `InterruptControl` 标志位
- **Per-session 调用序列化**：`callGates: ConcurrentHashMap<Object, Mono<Void>>`
- **Graceful Shutdown 集成**：每次调用在 `GracefulShutdownManager` 注册 requestId
- **Reactor Context 传播**：三个 Reactor Context key 实现 per-subscription 并发安全

**调用生命周期（runLifecycle 方法）**：
```
acquireExecution → deferContextual {
    registerRequest → callSerializationKey → build lifecycle → serializeOnKey → 
        beforeAgentExecution (returns scope) → notifyPreCall → doCallFn → notifyPostCall
} → releaseExecution
```

#### ReAct Loop

ReAct 循环位于 `CallExecution` 内部类中，核心流程：

```
doCallInner(msgs)
  ├── 检查 pending tool calls
  │   ├── 无 pending → addToContext + coreAgent()
  │   ├── 有 pending + ConfirmResults → applyConfirmResults + resumeAgent()
  │   ├── 有 pending + 无 input → resumeAgent()
  │   └── 有 pending + ToolResultBlock → validateAndAddToolResults + resumeAgent/coreAgent
  │
  ├── coreAgent() → executeIteration(0)
  │
  └── executeIteration(iter)
      └── reasoning(iter)
          ├── check maxIters → summarizing() if exceeded
          ├── checkInterrupted()
          ├── hookDispatcher.firePreReasoning()
          ├── MiddlewareChain.build(middlewares, onReasoning, reasoningCore)
          │   └── reasoningStream → modelCallStream → model.stream()
          │       ├── stream chunks → ReasoningContext.processChunk()
          │       ├── emitBlockEvents (TextBlock/ThinkingBlock/ToolUseBlock)
          │       └── ToolCallsAccumulator accumulates tool calls
          ├── build final Msg from ReasoningContext
          ├── runPostReasoningPipeline
          │   ├── HITL stop? → return
          │   ├── gotoReasoning? → reasoning(iter+1, true)
          │   ├── isFinished? → return
          │   └── checkInterrupted + acting(iter)
          │
          └── acting(iter)
              ├── extractPendingToolCalls
              ├── hookDispatcher.firePreActing()
              ├── MiddlewareChain.build(middlewares, onActing, actingCore)
              │   └── evaluatePermissions → PermissionEngine
              │       ├── pending ASKING → RequireUserConfirmEvent + RequestStopEvent
              │       ├── auto-denied → writeAutoDeniedResults
              │       └── all allowed → runToolBatch
              ├── toolkit.callTools(toolCalls, ...)
              │   └── ToolExecutor.executeAll (parallel/sequential)
              ├── hookDispatcher.firePostActing per result
              │   └── HITL stop? → return
              ├── pending/suspended tools? → buildSuspendedMsg
              └── executeIteration(iter + 1)  ← 循环
```

**关键设计模式**：
- **Reactor Flux 驱动的流式推理**：`reasoningStream()` 返回 `Flux<AgentEvent>`
- **ReasoningContext 累积器**：内部类 `ReasoningContext` 负责将流式 chunk 累积为完整的 `Msg`
- **多层中断检查**：每个 reasoning/acting 迭代都调用 `checkInterrupted()`
- **权限驱动的 HITL**：`acting()` 中的 `evaluatePermissions()` 使用 `PermissionEngine` 评估每个 tool call

#### 拦截系统（Middleware + 已废弃 Hook）

**当前机制 (v2.0)**: `MiddlewareBase` + `MiddlewareChain` 组合模式，通过 `onAgent()` 拦截 agent 执行。

**MiddlewareBase 定义 5 个拦截点**：

| 拦截点 | 模式 | 输入类型 | 说明 |
|--------|------|----------|------|
| `onAgent` | Onion | `AgentInput` | 拦截整个 Agent 调用 |
| `onReasoning` | Onion | `ReasoningInput` | 拦截推理/LLM 调用阶段 |
| `onActing` | Onion | `ActingInput` | 拦截 tool 执行阶段 |
| `onModelCall` | Onion | `ModelCallInput` | 拦截原始模型 API 调用 |
| `onSystemPrompt` | Pipeline | `String` | 变换系统 prompt 字符串 |

**MiddlewareChain 构建**：实现 **洋葱模型**，从后往前构建，最后一个 middleware 包装 core，第一个是最外层包装。

**Harness 层中间件**：15+ 个内置中间件（GracefulShutdown, Workspace, Skill, Subagents, Compaction 等）

**已废弃机制**: `HookEventType`（12 种事件：PreCall, PreReasoning, ReasoningChunk, PostReasoning, PreActing, ActingChunk, PostActing, PreSummary, SummaryChunk, PostSummary, PostCall, Error）标记为 `@Deprecated(forRemoval=true, since=2.0.0)`，正被 Middleware 体系替代。

**当前事件流**: `AgentEventType` 提供约 22 种细粒度流式事件（AGENT_START, TEXT_BLOCK_DELTA, TOOL_CALL_START 等），与旧 Hook 的粗粒度位置完全不同。

#### Tool 系统

**接口体系**：
```
AgentTool (顶层接口)
├── getName / getDescription / getParameters / getStrict / getOutputSchema / isReadOnly / callAsync
│
└── ToolBase (abstract, implements AgentTool)
    ├── ReflectiveFunctionTool  — @Tool 注解反射扫描
    ├── SchemaOnlyTool          — 纯 schema 外部工具
    ├── McpTool                 — MCP 协议工具
    ├── SubAgentTool            — 子 Agent 工具
    └── 具体工具: ShellCommandTool, ReadFileTool, WriteFileTool, TodoTools...
```

**注解驱动的工具注册**：
- `@Tool` 注解标记方法为工具，关键属性：name, description, strict, readOnly, concurrencySafe
- `externalTool` — 框架不执行，抛 `ToolSuspendException`
- `stateInjected` — 方法参数注入 `AgentState`
- `dangerousFiles` / `dangerousDirectories` — 敏感路径保护

**Toolkit — 工具管理 Facade**：
- `ToolRegistry` — 工具注册/查找
- `ToolGroupManager` — 工具组 CRUD + 激活管理
- `ToolSchemaProvider` — JSON Schema 生成 + 组过滤
- `McpClientManager` — MCP 客户端生命周期
- `MetaToolFactory` — 动态组控制 meta 工具

**ToolGroup — 动态工具激活**：
- `active` 标志控制组是否激活
- `ToolGroupScope.META` — Agent 可通过 `reset_equipped_tools` 元工具动态管理
- `ToolGroupScope.EXTERNAL` — 仅开发者代码管理
- `SkillToolGroup` — 绑定到特定 Skill，激活 Skill 时自动激活组

**ToolExecutor — 执行引擎**：
- 单工具执行：schema 验证 → 预设参数合并 → 上下文合并 → `tool.callAsync()`
- 批量执行：并行模式下按 `concurrencySafe` 分区
- 基础设施层：`applyScheduling` → `applyTimeout` → `applyRetry` → `applyShutdownGuard`
- 外部工具短路：`isExternalTool()` → 返回 `ToolResultBlock.suspended()`

**特殊工具**：
- `SubAgentTool`（agent 作为工具）、`SchemaOnlyTool`（外部工具）、`McpTool`

#### Memory/State 管理

**State 体系**：
```
AgentState (final, immutable builder pattern)
├── sessionId, userId
├── context: List<Msg> — 对话缓冲区（mutable via contextMutable()）
├── summary: String — 滚动摘要
├── replyId, curIter
├── permissionContext: PermissionContextState
├── toolContext: ToolContextState
├── tasksContext: TaskContextState
├── planModeContext: PlanModeContextState
└── interruptControl: InterruptControl (transient, runtime-only)
```

**关键设计**：
- **防御性拷贝 + 可变句柄双模式**：`getContext()` 返回不可变副本，`contextMutable()` 返回实际 `ArrayList`
- **JSON 序列化**：`toJson()` / `fromJsonString()` 支持持久化
- **Per-session InterruptControl**：使用双重检查锁懒加载

**StateStore 持久化**：
- `AgentStateStore` 接口：InMemory, JsonFile, Redis/MySQL (extensions)
- `InMemoryAgentStateStore` — 内存存储
- `JsonFileAgentStateStore` — JSON 文件持久化

**Memory 接口（@Deprecated since 2.0.0）**：
- `InMemoryMemory` — CopyOnWriteArrayList，session 持久化
- `LongTermMemory` — 扩展（Mem0, Bailian），三种模式：STATIC_CONTROL, AGENT_CONTROL, BOTH
- v2.0 架构变更：会话上下文直接存储在 `AgentState.context` 中

**Harness 层 Memory 管理**：
- `MemoryFlushManager` — 将长消息 offload 到 workspace 文件
- `MemoryConsolidator` — 知识整合
- `ConversationCompactor` — 上下文压缩（基于 token 计数）
- `ToolResultEvictionMiddleware` — 驱逐过大的 tool result
- `CompactionMiddleware` — 上下文溢出紧急压缩

#### Pipeline / Multi-Agent

**SubAgent 工具模式**：
```
SubAgentTool (extends ToolBase)
├── SubAgentProvider<Agent> — 工厂接口，每次调用创建新 Agent 实例
├── SubAgentConfig — 配置：toolName、description、stateStore、forwardEvents
└── callAsync(ToolCallParam) — 创建子 Agent → 调用 → 返回结果
```

**HarnessAgent — 高级编排层**：
```
HarnessAgent (implements Agent)
├── delegate: ReActAgent — 核心推理引擎
├── WorkspaceManager — 工作区管理
├── SandboxContext — 沙箱隔离
├── SkillPromoter / SkillCurator / SkillUsageStore — Skill 生命周期
├── MemoryConfig — 记忆配置
├── PlanModeManager — 计划模式
├── DistributedStore — 分布式存储
└── SubagentsMiddleware / DynamicSubagentsMiddleware — 子 Agent 编排
```

**子 Agent 管理体系**：
```
SubagentFactory (functional interface)
└── create(RuntimeContext) → Agent

DefaultAgentManager
├── agentFactories: Map<String, SubagentFactory> — 注册的工厂
├── declarations: Map<String, SubagentDeclaration> — 远程配置
├── createAgent(agentId, parentRc) → Agent
└── invokeAgent(agent, sessionId, userId, prompt) → Mono<Msg>
```

**Gateway 体系（跨节点通信）**：
```
Gateway (interface)
└── HarnessGateway
    ├── ChannelManager — 通道管理
    ├── SubagentRegistry — 子 Agent 注册表（支持分布式持久化）
    ├── WakeupDispatcher — 唤醒调度
    ├── SessionTurnGate — 会话轮次控制
    └── SubagentMaterializer — 子 Agent 重建
```

**消息总线**：
- `MsgHub` — pub/sub 多 agent 对话（自动广播）
- `MessageBus` — 基于工作区的消息传递

**Pipeline 模式**：
1. **SequentialPipeline** — 链式执行
2. **FanoutPipeline** — 并行执行

#### Model 集成

`Model` 接口仅 2 个方法：`stream()` + `getModelName()`

5 个实现 + Formatter 模式（每种 provider 独立的消息格式转换器）：
DashScope (Qwen), OpenAI, Gemini, Anthropic, Ollama

#### Session & State

**Session 接口**：key-value 持久化（InMemory, Json, Redis, MySQL）
- `Session` 接口：key-value 持久化（InMemory, Json, Redis, MySQL）
- `StateModule` 接口：任何组件可 `saveTo/loadFrom(session)`
- GraalVM native image 支持（200ms 冷启动）

**Skill 系统**：
```
SkillBox
├── SkillRegistry — 技能注册表
├── AgentSkillPromptProvider — 生成技能系统 prompt
├── SkillToolFactory — 将技能转换为工具
└── SkillRegistration (fluent builder)
    ├── skill(AgentSkill) — 技能定义
    ├── tool(Object) — 关联工具对象
    ├── mcpClient(McpClientWrapper) — 关联 MCP 工具
    └── subAgent(SubAgentProvider) — 关联子 Agent
```

**技能通过 `load_skill_through_path` 工具动态加载，激活后关联的 ToolGroup 变为可用。**

### 优势

1. **全响应式架构** — Project Reactor 贯穿，高吞吐 + 自然流式
2. **Middleware 拦截架构** — MiddlewareBase + MiddlewareChain 组合模式，覆盖 agent 全生命周期，可修改事件、注入工具（旧 HookEventType 已废弃）
3. **生产级特性** — Graceful shutdown, interrupt, pending tool recovery, GraalVM, OpenTelemetry
4. **多 Provider** — DashScope, OpenAI, Gemini, Anthropic, Ollama + Formatter 模式抽象
5. **多 Agent 模式** — Sequential/Fanout Pipeline + MsgHub pub/sub + SubAgentTool
6. **25+ 扩展** — Session 存储、RAG 后端、调度、多框架 Starter
7. **安全沙箱** — Harness 模块提供不受信工具代码的隔离执行
8. **PlanNotebook** — 结构化任务分解与持久化

### 劣势

1. **Alibaba-first** — DashScope/Qwen 为主，部分 API 便利性偏向阿里云
2. **无 IoC 容器** — 依赖 Spring Boot/Quarkus/Micronaut 扩展做 DI
3. **单 Agent 实例非并发** — AgentBase 明确声明不支持并发执行
4. **Java 17 无 21 特性** — 无 virtual threads, record patterns, sealed match
5. **响应式复杂度** — Mono/Flux 全栈对同步 Java 开发者门槛高
6. **无内置工作流/状态机** — ReAct loop 为主，确定性编排需自行组合
7. **无内置 Auth/RBAC** — 依赖宿主框架提供

### 与 Nop 平台的关联

#### 与 nop-ai-agent 的对比分析

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| **架构理念** | 全响应式 Agent 运行时 | 声明式 Agent 引擎 + 可逆计算 |
| **编程模型** | Project Reactor (Mono/Flux) | 同步/命令式 + Virtual Threads |
| **配置方式** | Builder 模式 + 代码配置 | XDSL 声明式配置 + 代码生成 |
| **扩展机制** | Middleware 拦截链 | Hook + Contribution Registry |
| **状态管理** | AgentState 不可变对象 + 可变句柄 | AgentModel 不可变 + AgentExecutionContext 可变 |
| **安全架构** | Permission Engine (三态) | 四层纵深安全 |
| **多 Agent** | SubAgentTool + MsgHub | call-agent + Team 系统 |

#### 可借鉴的设计模式

**从 AgentScope Java 借鉴**：
1. **全响应式架构**：高吞吐 + 自然流式，nop-ai-agent 可考虑在 LLM 调用层引入响应式
2. **Middleware 拦截架构**：5 层拦截点，覆盖 agent 全生命周期
3. **ToolGroup 动态激活**：运行时控制工具可见性，支持 Agent 自管理
4. **Permission Engine**：三态权限（ALLOW/ASK/DENY）+ per-session 缓存

**从 nop-ai-agent 借鉴**：
1. **声明式配置**：XDSL 定义 schema，生成不可变组件模型
2. **四层纵深安全**：完整的安全链，从工具级到沙箱隔离
3. **工具调用修复链**：4 阶段确定性修复，保证工具调用正确性
4. **Sustainer 续命模型**：支持长时间运行的 agent 跨越迭代预算
5. **拒绝账本**：累计拒绝次数达到阈值自动暂停

#### 集成路径建议

**技术集成**：
1. AgentScope Agent 通过工具调用 Nop GraphQL API 或 Biz 层
2. Nop IoC 管理 AgentScope Agent 实例（作为 beans）
3. Nop 的代码生成管线为 AgentScope 生成 tool schema / agent config

**设计模式融合**：
1. Middleware + Hook 混合架构：核心拦截点使用 Middleware，细粒度事件使用 Hook
2. 声明式 + Builder 混合配置：Agent 定义使用声明式 XDSL，运行时配置使用 Builder
3. 三态权限 + 四层安全：权限评估使用三态，安全检查使用四层纵深

#### 不适用

- Project Reactor 全栈与 Nop 同步/命令式架构冲突
- 无 IoC 容器，依赖 Spring Boot
- 无 ORM/数据库层

## Conclusion

AgentScope Java 是 JVM 上少有的全响应式 Agent 框架，ReAct loop + Middleware + SubAgentTool 的设计成熟。与 nop-ai-agent 代表了两种不同的 Agent 框架设计哲学：

- **AgentScope Java**：全响应式 Agent 运行时，强调高吞吐和流式处理
- **nop-ai-agent**：声明式 Agent 引擎，强调可逆计算和增量能力

两者架构理念互补，可互相借鉴：
- AgentScope 的响应式架构和 Middleware 拦截可增强 nop-ai-agent 的性能
- nop-ai-agent 的声明式配置和四层安全可增强 AgentScope 的可配置性和安全性

最直接的集成路径是 AgentScope Agent 作为 Nop 服务的"智能用户"，通过工具调用 Nop 平台能力。

## Open Questions

- [ ] AgentScope 的 Reactor 响应式模型如何与 Nop 的同步模型桥接？
- [ ] Nop IoC 能否直接管理 AgentScope 的 Agent bean？
- [ ] AgentScope 的 Middleware 系统是否适合作为 Nop biz 拦截器的参考？
- [ ] nop-ai-agent 的声明式配置是否可移植到 AgentScope？

## References

- ~/ai/agentscope-java/README.md, README_zh.md
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/
- nop-ai/nop-ai-agent/ (Nop AI Agent 模块)
- ai-dev/analysis/agent-survey/2026-06-05-agentscope-java-analysis.md
- https://java.agentscope.io/
- https://github.com/agentscope-ai/agentscope-java
