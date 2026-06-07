# Nop AI Agent 架构对比分析

## 1. 目标

本篇记录对主流 AI Agent 框架的架构对比分析，目的是为 `nop-ai-agent` 的架构决策提供外部参照。

分析框架：

| 框架 | 语言 | 模块结构 | 定位 |
|------|------|----------|------|
| agentscope-java | Java | 多模块（core/extensions） | 阿里达摩院，企业级 Agent 框架 |
| solon-ai | Java | 多模块（core/agent/flow/llm-dialects/mcp/rag） | Solon 生态 AI 模块，Flow 驱动 |
| pi-agent | TypeScript/Java | 单体 agent + ai + harness + session | 轻量级编码 Agent |
| openai-agents-python | Python | 单包（agents/handoffs/memory/mcp） | OpenAI 官方 Agent SDK |
| smolagents | Python | 单包（agents/tools/models） | HuggingFace 轻量 Agent 框架 |

本篇不重复代码细节——源码是代码层面的唯一事实。本篇聚焦于：每个框架做了什么架构决策、为什么这样选、对 Nop 有什么启发。

## 2. 核心抽象对比

### 2.1 Agent 基础接口

| 框架 | 核心接口 | 关键设计决策 |
|------|----------|-------------|
| agentscope-java | `Agent` extends `CallableAgent`, `StreamableAgent`, `ObservableAgent` | 接口组合：可调用、可流式、可观察是三个独立能力。`AgentBase` 提供基础设施（hooks, subscriptions, interrupt, state），不管理 memory。 |
| solon-ai | `Agent<Req, Resp>` extends `AgentHandler`, `NamedTaskComponent` | 泛型请求/响应。Agent 是 Solon Flow 的 `NamedTaskComponent`，天然可编排。`ReActAgent` 是内置实现。 |
| pi-agent (TS) | `Agent` class + `AgentState` | 状态对象与执行逻辑分离。两重队列（steering/followUp）支持运行时注入消息。 |
| openai-agents | `Agent` dataclass | Agent 是配置对象（name, instructions, tools, handoffs），不是执行器。Runner 负责执行。 |
| smolagents | `Agent` base class + `MultiAgent` | Agent 是执行器也是配置。CodeAgent 和 ToolCallAgent 是两种执行策略。 |

**关键发现**：

1. **Agent 是配置还是执行器**是一个根本分歧。
   - openai-agents 选择了"配置对象 + 独立 Runner"模式
   - agentscope-java 选择了"Agent 即执行器"模式
   - Nop 当前 `agent.xdef` 是 DSL 配置，`BaseAgent` 极简——与 openai-agents 更接近

2. **接口组合 vs 单一接口**。
   - agentscope-java 把 callable/streamable/observable 拆开，允许按需组合
   - 其他框架大多用单一接口
   - Nop 可以参考 agentscope-java 的拆分方式，在引擎层提供不同能力接口

### 2.2 执行模型

| 框架 | 执行模型 | 异步模型 | 流式支持 |
|------|----------|----------|----------|
| agentscope-java | ReAct loop（内嵌在 ReActAgent） | Reactor (Mono/Flux) | 通过 Flux 流式 |
| solon-ai | 基于 Solon Flow 的计算图 | 同步 + Flow 驱动 | 通过 AgentChunk 流 |
| pi-agent (TS) | 两重循环（内层 steering/tool，外层 followUp） | AsyncGenerator (async/await) | 通过事件流 |
| openai-agents | Runner.run() 单循环 | asyncio | 通过 StreamResponse |
| smolagents | 单步循环（step() 方法） | 同步 | 通过 callback |

**关键发现**：

1. **ReAct 是共识**。所有框架的核心执行模型都是某种 ReAct 变体（Thought → Action → Observation 循环）。
2. **Solon Flow 的计算图模式独特**。solon-ai 把 ReAct 的每个阶段（Plan → Reason → Action）建模为 Flow 图的节点，通过 FlowInterceptor 拦截。这与 Nop 的 XLang 流程引擎理念一致。
3. **pi-agent 的两重队列值得关注**。steering 队列允许用户在 agent 执行中途注入指令（中断当前工具执行），followUp 队列允许在 agent 完成后追加任务。这对 Nop 的 Hook 机制有启发。
4. **异步模型选择**：
   - agentscope-java 用 Reactor（响应式），适合高并发
   - pi-agent 用 async/await，简洁
   - Nop 可以用 `CompletionStage` + `Flow.Publisher`（Java 标准），与现有引擎设计一致

### 2.3 工具/函数调用

| 框架 | 工具定义方式 | Schema 格式 | MCP 支持 |
|------|-------------|-------------|----------|
| agentscope-java | `AgentTool` 接口 + `@Tool`/`@ToolParam` 注解 | JSON Schema | 内置 MCP 客户端 |
| solon-ai | `FunctionTool` + 注解 | JSON Schema | 内置 solon-ai-mcp |
| pi-agent | `AgentTool` 接口 | TypeBox (JSON Schema) | 无 |
| openai-agents | `function_tool` 装饰器 | JSON Schema | 内置 MCP |
| smolagents | `Tool` 基类 + `@tool` 装饰器 | JSON Schema | 无 |

**关键发现**：

1. **JSON Schema 是工具参数的事实标准**。所有框架都用 JSON Schema 描述工具参数。
2. **Nop 的 XML Tool DSL 是独特的**。Nop 用 XML 而不是 JSON Schema 描述工具。这是差异点也是优势——与 XLang 生态一致。但需要考虑：是否需要同时支持 JSON Schema 格式以兼容 MCP？
3. **工具注册方式**：
   - agentscope-java: `Toolkit` 容器 + 注解自动发现
   - solon-ai: `ToolProvider` 接口
   - Nop: `.tool.xml` 文件 + DSL 声明

### 2.4 Hook/Interceptor 机制

| 框架 | 拦截机制 | 生命周期点 | 可修改性 |
|------|----------|-----------|----------|
| agentscope-java | `Hook` 接口 + 统一 `onEvent(HookEvent)` | PRE_CALL, POST_CALL, PRE_REASONING, POST_REASONING, REASONING_CHUNK, PRE_ACTING, POST_ACTING, ACTING_CHUNK, PRE_SUMMARY, POST_SUMMARY, SUMMARY_CHUNK, ERROR | 事件有 setter 则可修改 |
| solon-ai | `ReActInterceptor` + `ChatInterceptor` | onAgentStart, onModelStart, onModelEnd, onPlan, onReason, onThought, onAction, onObservation, onAgentEnd | 通过 trace 修改 |
| pi-agent | `beforeToolCall` / `afterToolCall` 回调 | before/after tool call | 可 block 工具、可修改结果 |
| openai-agents | 无显式 Hook | 通过 Runner config callback | 有限 |

**关键发现**：

1. **agentscope-java 的 Hook 设计最完整**。统一事件模型（`onEvent(HookEvent)` + switch pattern matching）+ 优先级 + 可修改性标记。这比 Nop 当前设计更灵活。
2. **solon-ai 的 Interceptor 拆分更细**。把 Chat 层的拦截（onModelStart/End）和 Agent 层的拦截（onReason/onAction）分开了。
3. **Nop 当前 Hook 生命周期**（before_reasoning, after_reasoning, before_acting, after_acting, on_error）与 agentscope-java 对比缺少：
   - PRE_CALL / POST_CALL（整个 agent 调用的起止）
   - REASONING_CHUNK / ACTING_CHUNK（流式中间结果）
   - PRE_SUMMARY / POST_SUMMARY（总结阶段）
4. **Hook 优先级**是重要特性。agentscope-java 和 Nop 都支持优先级排序。

### 2.5 Memory / 会话管理

| 框架 | Memory 抽象 | 会话持久化 | 压缩/摘要 |
|------|-------------|-----------|-----------|
| agentscope-java | `Memory` extends `StateModule` + `InMemoryMemory` | 通过 StateModule 的 session 机制 | LongTermMemory + summary hook |
| solon-ai | `AgentSession` extends `ChatSession` + FlowContext | FlowContext snapshot | SummarizationInterceptor（多种策略） |
| pi-agent | JSONL 文件存储 + MemoryRepository | JSONL 文件 + session 目录 | Branch summarization + compaction |
| openai-agents | `Runner` 管理历史列表 | 无内置持久化 | 无内置压缩 |
| smolagents | Agent 内部 List | 无内置持久化 | 无内置压缩 |

**关键发现**：

1. **Memory 接口设计**：agentscope-java 把 Memory 作为 StateModule（支持 save/restore），这比简单的 List 更适合 Nop 的会话恢复场景。
2. **压缩策略多样性**：solon-ai 提供了 LLM 摘要、层次化摘要、向量存储摘要等多种策略。Nop 应至少支持 LLM 摘要和窗口裁剪两种。
3. **pi-agent 的 JSONL 日志格式**适合调试和回溯。Nop 的 `.nop/sessions/` 目录设计可以参考 JSONL 格式作为事件日志。

### 2.6 多 Agent 编排

| 框架 | 多 Agent 模式 | 编排方式 |
|------|--------------|----------|
| agentscope-java | `SubagentEventBus` + 嵌套 Agent 调用 | Agent 内嵌 Agent，事件总线传递 |
| solon-ai | Solon Flow 图编排 | Agent 作为 Flow 节点，Flow 定义编排逻辑 |
| pi-agent | 无多 Agent | 单 Agent 模式 |
| openai-agents | **Handoff** 模式 | Agent 把控制权交给另一个 Agent |
| smolagents | `MultiAgent` 管理器 | 顺序/并行编排 |

**关键发现**：

1. **Handoff vs Flow vs 嵌套调用**是三种根本不同的多 Agent 编排模式：
   - openai-agents: Handoff（Agent 主动交出控制权）
   - solon-ai: Flow 图编排（外部定义编排逻辑）
   - agentscope-java: 嵌套调用（工具调用机制驱动子 Agent）
2. **Nop 当前选择**：`call-agent` 工具模式（与 agentscope-java 嵌套调用类似）。这是正确的——与 Nop 的工具 DSL 一致，且不需要发明新的编排协议。
3. **Flow 编排**是 Nop 的自然扩展方向。如果需要更复杂的多 Agent 编排，可以与 Nop Flow 集成，类似 solon-ai 的做法。

### 2.7 LLM 集成

| 框架 | LLM 抽象 | 多 Provider 支持 |
|------|----------|-----------------|
| agentscope-java | `Model` 接口 + formatter 策略（Anthropic/OpenAI） | 通过 formatter 适配不同 API |
| solon-ai | `ChatModel` 接口 + dialect 模块 | 独立 dialect 模块（OpenAI/Anthropic/Ollama/Gemini/DashScope） |
| pi-agent | `@earendil-works/pi-ai` 包 | Transport 抽象 |
| openai-agents | 绑定 OpenAI API | 仅 OpenAI |
| smolagents | `Model` 基类 | 支持 HuggingFace/OpenAI/等 |

**关键发现**：

1. **formatter/dialect 模式值得参考**。agentscope-java 和 solon-ai 都把不同 LLM API 的消息格式化抽成独立策略。Nop 已有 `nop-ai-llms` 模块，应确保与 Agent 层解耦。
2. **LLM 层与 Agent 层的边界**应该清晰：LLM 层只管请求/响应，Agent 层管循环和工具调用。

## 3. 各框架独特设计亮点

### 3.1 agentscope-java

- **统一 Hook 事件模型**：所有生命周期事件通过单一 `onEvent(HookEvent)` 方法分发，配合 switch pattern matching。简洁且类型安全。
- **StructuredOutputCapableAgent**：把结构化输出作为 Agent 的一个能力混入，而不是所有 Agent 都强制支持。
- **StateModule**：Memory 继承 StateModule，天然支持状态持久化和恢复。
- **响应式全栈**：基于 Project Reactor，全异步非阻塞。

### 3.2 solon-ai

- **Flow 驱动的 Agent**：把 ReAct 循环建模为 Solon Flow 计算图，每个阶段是图节点。这比手写循环更可扩展。
- **ReActInterceptor 双层拦截**：ChatInterceptor 处理 LLM 层，ReActInterceptor 处理 Agent 层。职责清晰。
- **SummarizationInterceptor 策略模式**：摘要压缩可以组合多种策略（LLM 摘要 + 层次化 + 向量存储）。
- **HITL（Human-in-the-Loop）**：内置人机协同拦截器，支持审批/拒绝工具调用。

### 3.3 pi-agent

- **两重消息队列**：steering（中断当前执行注入指令）+ followUp（完成后追加任务）。这对运行时交互很重要。
- **AgentHarness 封装**：把 session、compaction、skill、prompt 组装成一个完整的"agent 驾驶舱"。
- **JSONL 会话存储**：用 JSONL 文件记录每个事件，便于调试和回溯。

### 3.4 openai-agents

- **Handoff 模式**：Agent 通过 handoff 把控制权交给另一个 Agent。简洁且模型友好——LLM 只需要知道"什么时候该交给谁"。
- **Agent 即配置**：Agent 是一个数据对象，Runner 负责执行。执行逻辑不分散在各 Agent 实现中。
- **Tracing 内置**：所有执行都有 trace 支持。

### 3.5 smolagents

- **Code Agent**：Agent 直接生成 Python 代码并执行，而不是通过工具调用。这是 ReAct 的一种变体，适合需要灵活计算的场景。
- **极简设计**：Agent → Model → Tool 三层，没有过度抽象。

## 4. 对 Nop AI Agent 的架构启示

### 4.1 应采纳的设计

| 来源 | 启示 | Nop 应用方向 | 阶段归属 |
|------|------|-------------|----------|
| agentscope-java | 统一 Hook 事件模型 | 扩展现有 Hook 生命周期，增加 PRE/POST_CALL/CHUNK/SUMMARY 事件 | Phase 1（事件扩展） |
| agentscope-java | 接口组合（Callable/Streamable/Observable） | 引擎层提供不同能力接口，按需组合 | Phase 2 |
| solon-ai | Interceptor 策略模式 | Hook/Skill 通过策略模式实现不同行为（摘要、重试、审批） | Phase 2 |
| solon-ai | HITL 拦截器 | 在工具执行前增加人机审批拦截点 | Phase 2 |
| pi-agent | 两重消息队列 | 增加 steering 机制允许运行时中断 agent 注入新指令 | Phase 1 |
| pi-agent | JSONL 会话日志 | session 目录增加事件日志，便于调试和回溯 | Phase 1 |
| openai-agents | Agent 即配置 | 保持 `agent.xdef` 作为配置入口，引擎负责执行（已有设计，确认方向正确） | 已采纳 |
| 所有框架 | JSON Schema 工具参数 | 考虑在 XML Tool DSL 之外增加 JSON Schema 格式支持以兼容 MCP | Phase 2 |

### 4.2 应避免的设计

| 来源 | 问题 | Nop 应避免 |
|------|------|-----------|
| openai-agents | 绑定单一 Provider | Nop 必须保持 LLM Provider 可插拔（已有 nop-ai-llms 模块，确认方向正确） |
| smolagents | 无会话持久化 | Nop 需要从第一天就支持会话恢复（参见 session-and-storage.md） |
| agentscope-java | 全 Reactor 响应式 | 引入 Reactor 框架会增加复杂度。应考虑 Java 标准异步 API（`CompletionStage` + `Flow.Publisher`） |

### 4.3 Nop 的独特优势

1. **DSL-First**：Nop 是唯一一个用 xdef 定义 Agent DSL 的框架。这意味着 Agent 配置可以被验证、转换、delta 定制。
2. **XML Tool Calling**：Nop 的 XML 格式工具调用比 JSON Schema 更适合结构化表达和 XLang 生态。
3. **call-agent 即工具**：多 Agent 协作不发明新协议，复用工具调用机制。
4. **Plan DSL**：Nop 有独立的 `agent-plan.xdef`，这是其他框架没有的。

## 5. 结论

本篇对比分析为 `nop-ai-agent` 的后续设计决策提供了参照。核心结论：

1. **保持 DSL-First + Engine 分离**的四层架构——这是 Nop 相比其他框架的结构优势
2. **参考 agentscope-java 的 Hook 事件模型**扩展生命周期
3. **参考 solon-ai 的策略模式**组织 Hook/Skill/Interceptor
4. **参考 pi-agent 的消息队列**增强运行时交互能力
5. **Agent 即配置（openai-agents 模式）**，不在 DSL 层混入执行逻辑
