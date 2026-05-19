# Nop AI Agent 核心架构设计

## 1. 目标

本篇定义 `nop-ai-agent` 的核心架构设计——综合现有 DSL 设计、Java 引擎层设计和外部框架对比分析，给出一个完整的架构视图。

### 1.1 核心定位

**nop-ai-agent 面向大规模无人值守自动化执行**。

这意味着：

- 不是交互式 REPL——不需要逐字符输出、不需要 console 格式化
- 所有交互（包括人机协同）通过 XML Tool 完成（如 ask-oracle）
- 引擎优先保证可靠性、可恢复性和确定性，而非交互体验
- 事件模型以结构化结果为主，流式输出为辅

**拒绝了**：交互式 console agent 定位。理由是 Nop 平台的定位是自动化基础设施，不是终端工具。交互式场景可以在自动化引擎之上构建。

本篇回答：

1. 系统整体如何分层
2. 核心对象有哪些、各自职责是什么
3. 执行流程是怎样的
4. 关键设计决策及理由

本篇是 `nop-ai-agent-engine.md` 的演进替代版本。`nop-ai-agent-engine.md` 中关于分层、核心对象、设计原则的内容已纳入本篇；`nop-ai-agent-react-engine.md` 仍然是 ReAct 引擎的详细设计文档，独立维护。

## 2. 架构分层

### 2.1 总体分层

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

### 2.2 与现有模块的关系

| 模块 | 职责 | 与 Agent Engine 的关系 |
|------|------|----------------------|
| `nop-ai-agent` | Agent DSL 模型 + 执行引擎 | 本篇设计范围 |
| `nop-ai-core` | AI 核心抽象（ChatMessage, ChatOptions 等） | Agent 引擎依赖的基础类型 |
| `nop-ai-llm` | LLM 调用抽象 | Agent 引擎通过此层调用 LLM |
| `nop-ai-llms` | 具体 LLM Provider 实现 | Agent 引擎不直接依赖 |
| `nop-ai-toolkit` | 工具 DSL（tool.xdef） | Agent 引擎通过此层发现和调用工具 |
| `nop-ai-tools` | 具体工具实现 | Agent 引擎不直接依赖 |

## 3. 核心对象职责契约

引擎层的核心对象通过职责契约定义，不通过字段列表。每个对象的字段定义属于源码范畴。

### 3.1 职责契约表

| 对象 | 职责契约 | 设计约束 |
|------|----------|---------|
| AgentModel | 从 `agent.xdef` 装载得到的纯配置对象，是 DSL 在引擎层的投影 | 不持有执行逻辑，不持有状态；可被 Delta 定制 |
| IAgentEngine | 接受配置和请求，创建执行上下文并启动执行的顶层入口 | 无状态——每次调用创建新的上下文；不感知具体执行策略 |
| AgentExecutionContext | 单次执行的全部内存态数据的容器 | 生命周期与单次执行绑定，不跨执行复用 |
| IAgentExecutor | 定义执行模式（ReAct、单轮、流式等）的策略接口 | 不持有配置——从上下文中读取；第一阶段只实现 ReAct |
| AgentEventPublisher | 将执行状态变化投影为外部可观察的事件流 | 事件类型稳定（详见 §5.1）；不修改执行状态 |

这些对象的字段定义见源码。本表只定义每个对象存在的理由、它向其他对象暴露的契约、以及它必须遵守的约束。

### 3.2 Agent 即配置，Engine 即执行

**决策**：Agent 是配置对象，不是执行器。执行由独立的引擎层负责。

**理由**：

- 与 `agent.xdef` DSL 的配置语义一致
- 参考 openai-agents 的成功实践——Agent 是数据对象，Runner 负责执行
- 便于 Delta 定制——只改配置不改执行逻辑
- 便于测试——可以独立测试引擎而不需要实例化具体 Agent

**拒绝了**：agentscope-java 的 "Agent 即执行器" 模式（Agent 类既持有配置又驱动循环）。理由是 Nop 的 DSL-First 原则要求配置和执行严格分离。Agent 不应该知道自己的执行策略。

### 3.3 会话与执行分离

**决策**：Session 和 ExecutionContext 是两个独立对象。

- Session 是持久化的，跨请求存在
- ExecutionContext 是临时的，与单次执行绑定

**理由**：

- 参考 solon-ai 的 `AgentSession` vs `FlowContext` 分离
- 持久化策略不应影响执行逻辑
- Session 可以被替换为不同实现（文件、数据库、内存）

## 4. 执行流程

### 4.1 双循环模型

**决策**：采用 pi-agent 风格的双循环，简化为只处理完整消息。

外层循环（followUp 循环）：

- Agent 完成一次完整执行后，检查是否有排队的后续消息
- 如果有，注入后续消息，启动新的内层循环
- 如果没有，Agent 执行结束

内层循环（steering + ReAct 循环）：

- 标准 ReAct 循环：LLM 调用 → 解析输出 → 工具执行 → 结果回灌
- 每轮工具执行后检查 steering 队列（见 §4.3）
- 循环粒度是**完整消息**——每次 LLM 调用返回完整的 assistant message（包含可能的多个工具调用），不是逐 token 处理

**"完整消息"的定义**：一次 LLM 调用返回的完整 assistant message，可能包含文本内容和/或一组工具调用。引擎在收到完整响应后才进入下一阶段（工具执行或结束判定），不在 LLM 流式输出过程中做决策。

**理由**：

- 无人值守场景不需要逐 token 交互
- 完整消息简化了引擎的状态机——不需要管理流式中间状态
- 流式输出仍然可以通过事件发布（TextChunk 事件），但不影响引擎决策

**拒绝了**：逐 token 的流式决策模式。理由是它增加了引擎复杂度（需要管理部分解析状态），而无人值守场景不需要它。

### 4.2 ReAct 主循环的行为语义

ReAct 主循环的期望行为是：

1. 引擎接收配置和请求后，初始化会话、装配 Hook/Skill/Tool、构建 system prompt
2. 进入循环：构建 LLM 请求 → 调用 LLM → 解析输出
3. 如果 LLM 输出包含工具调用 → 执行工具 → 结果写回消息 → 回到第 2 步
4. 如果 LLM 输出不包含工具调用 → 跳出内层循环
5. 循环中任何时刻，如果达到约束上限（maxIterations、token 预算、外部取消、不可恢复错误）→ 中止循环
6. 内层循环中止后，检查 steering 和 followUp 队列
7. 最终发布结果或错误

其中，Hook 在以下语义点被触发（详见 §5）：

- Agent 执行开始前和结束后
- 每次 LLM 调用前后
- 每次工具执行前后
- 错误发生时

本节的执行流程与 `nop-ai-agent-react-engine.md` 一致。后者是 ReAct 引擎的详细设计文档。

### 4.3 Steering 机制

**新增设计**（参考 pi-agent 的 steering queue）。

**期望行为**：在 ReAct 循环中，每轮工具执行后检查是否有外部注入的 steering 消息。如果有，注入消息、跳过当前剩余工具、进入下一轮推理。Steering 允许用户或系统在 agent 执行中途提供新指令。

**决策理由**：现有 Hook 机制是自动化的（按生命周期点触发），缺少人工/外部注入消息的能力。Steering 填补这个空白。

**拒绝了什么**：
- 通过 Hook 注入 steering 消息：Hook 的语义是"增强当前事件"，不是"注入新消息流"。职责不同。
- 通过修改消息历史注入：绕过引擎主循环，难以保证一致性。

**边界条件**：
- Steering 是引擎层机制，当前不需要 DSL 支持（不需要在 `agent.xdef` 中新增元素）
- Steering 消息的来源是外部调用者（API 层），不是 Hook
- 同一轮中，steering 优先于剩余工具执行
- Steering 不影响 Hook 的正常触发

**后续考虑**：如果 steering 证明有价值，未来可以在 `agent.xdef` 的 `constraints` 中增加 steering 相关配置（如是否启用、最大频率）。但这属于 Phase 2+ 范畴。

## 5. Hook 生命周期设计

### 5.1 完整生命周期点

参考 agentscope-java 的 Hook 事件模型，扩展 Nop 的生命周期。与 `nop-ai-agent-react-engine.md` 中的 5 个生命周期点相比，增加了 PRE/POST_CALL、CHUNK 和 SUMMARY。

| 生命周期点 | 触发时机 | 可修改内容 |
|-----------|---------|-----------|
| `PRE_CALL` | Agent 开始执行前 | 请求参数、工具列表 |
| `POST_CALL` | Agent 执行完成后 | 最终结果 |
| `PRE_REASONING` | 发起 LLM 调用前 | 输入消息、chatOptions |
| `POST_REASONING` | LLM 响应后 | LLM 输出消息 |
| `REASONING_CHUNK` | LLM 流式输出中间块 | 流式块内容 |
| `PRE_ACTING` | 工具执行前 | 工具调用参数（可 block） |
| `POST_ACTING` | 单个工具执行后 | 工具结果（可修改） |
| `PRE_SUMMARY` | 达到 maxIterations 后，生成摘要前 | 无 |
| `POST_SUMMARY` | 摘要生成后 | 摘要内容 |
| `ON_ERROR` | 发生错误时 | 错误处理策略 |

### 5.2 Hook 机制的设计原则

1. **统一事件分发**：所有 Hook 接收相同的生命周期事件类型，引擎决定事件路由。Hook 实现者根据事件类型选择处理逻辑。
2. **可修改性由事件本身决定**：部分事件允许 Hook 修改执行数据（如修改 LLM 输入消息），部分事件只读。哪些事件可修改属于运行时语义，由引擎定义。
3. **优先级排序**：Hook 按优先级排序执行，数值越小优先级越高。同优先级按注册顺序。
4. **失败传播**：Hook 执行失败时，引擎根据错误类型决定继续还是中止。不是所有 Hook 错误都应该中止整个执行。

**参考**：agentscope-java 的 Hook 统一事件模型。本设计与其方向一致，但不指定具体的 Java 方法签名。

### 5.3 与现有 DSL 的关系

`agent.xdef` 中的 `<hooks>` 声明 Hook 配置，引擎在运行时加载并排序。

## 6. 工具调用架构

### 6.1 工具发现

```
agent.xdef 中的 <tools> 声明工具名列表
  → AgentEngine 根据 tool name 加载对应的 .tool.xml
  → 构建 LLM 可见的工具 schema
  → 工具 schema 注入 LLM 请求
```

### 6.2 工具执行

```
LLM 返回工具调用（XML 格式，解析为 ToolCall 对象）
  → PRE_ACTING hook（可 block）
  → HITL 检查（如果启用人机协同）
  → 工具执行器执行
  → POST_ACTING hook（可修改结果）
  → 结果写回消息历史
```

### 6.3 并行工具执行

- `call-tools.xdef` 的 `parallel` 属性控制是否并行
- `maxConcurrency` 限制并发数
- 引擎应支持并行执行多工具调用

### 6.4 JSON Schema 兼容

**决策**：在保持 XML Tool DSL 作为主要格式的同时，增加 JSON Schema 格式作为工具参数的中间转换格式。

**理由**：

- MCP 协议使用 JSON Schema，Nop 需要 MCP 兼容能力
- 部分 LLM Provider 更擅长处理 JSON 格式的工具定义
- 可以作为 `.tool.xml` 到 LLM prompt 的中间格式，而不改变 XML DSL 本身

**阶段归属**：此能力属于 Phase 2（可插拔增强），不在 Phase 1 核心闭环中（参见 `nop-ai-agent-roadmap.md`）。Phase 1 使用现有 XML Tool DSL 即可。

**拒绝了**：完全切换到 JSON Schema。理由是 XML DSL 是 Nop XLang 生态的一部分，放弃它会破坏一致性。

## 7. 会话与存储架构

### 7.1 会话生命周期

```
新建会话:
  → 创建 sessionId
  → 初始化空消息历史
  → 持久化 session 元数据

恢复会话:
  → 加载 session.json
  → 恢复消息历史
  → 恢复 plan 状态

会话分叉:
  → 基于当前 session 创建快照
  → 新 session 的 parentSession 指向源 session

会话压缩:
  → 达到 token 阈值时触发
  → 通过 Hook 调用摘要策略
  → 保留压缩记录
```

### 7.2 存储接口

保持与 `nop-ai-agent-session-and-storage.md` 一致的存储布局。

增加事件日志（参考 pi-agent 的 JSONL 格式），用于调试和回溯。这是对现有存储布局的补充，不改变 `session.json` 的职责。

具体存储格式见 `nop-ai-agent-session-and-storage.md`。

## 8. 错误处理策略

### 8.1 错误分类

| 错误类型 | 处理策略 | 示例 |
|---------|---------|------|
| LLM 调用失败 | 自动重试（可配置次数） | 网络超时、rate limit |
| LLM 返回无效格式 | 重试 + 修正 prompt | XML 解析失败 |
| 工具执行失败 | 记录错误，继续或中止（可配置） | 文件不存在 |
| 工具超时 | 中止该工具，返回超时错误 | 长时间运行的工具 |
| Agent 预算耗尽 | 中止执行，返回部分结果 | token 超限 |
| 外部取消 | 优雅停止，保存当前状态 | 用户取消 |

### 8.2 重试策略

参考 solon-ai 的 `ToolRetryInterceptor`：

- 工具执行失败时，可以根据错误类型决定是否重试
- 重试次数和间隔可配置
- 重试策略通过 Hook/Interceptor 实现，不在引擎主循环中硬编码

## 9. 关键设计决策总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 核心定位 | 大规模无人值守自动化 | Nop 是自动化基础设施，不是终端工具 |
| Agent 是配置还是执行器 | 配置 | DSL-First，便于 Delta 定制 |
| 执行模型 | 双循环 + ReAct（完整消息粒度） | 无人值守不需要逐 token 交互；pi-agent 启发 |
| 异步模型 | `CompletionStage` + `Flow.Publisher` | Java 标准，避免引入 Reactor |
| Hook 模型 | 统一事件 + 优先级 | 参考 agentscope-java |
| 多 Agent 协作 | call-agent 工具 + Agent-as-Subprocess | 与工具 DSL 一致，进程隐喻清晰 |
| Agent 派生 | fork + exec 模型 | 操作系统进程隐喻，语义清晰可预测 |
| 内部能力 | 薄接口 + 可 Agent 化 | Phase 1 硬编码，Phase 2 逐步 Agent 化 |
| 工具参数格式 | XML 为主 + JSON Schema 兼容 | 兼顾 XLang 生态和 MCP |
| 会话存储 | 文件系统（可替换） | 第一阶段简单，保留替换空间 |
| 错误处理 | 分类 + 策略模式 | 通过 Hook/Interceptor 实现 |
| 多 Agent 并行协同 | Phase 1 fail-fast，Phase 2+ 协调 | 先检测后解决 |

## 10. 与现有设计文档的关系

本篇替代 `nop-ai-agent-engine.md` 作为引擎层总体设计文档。具体对应关系：

| 本篇内容 | 原有文档 | 状态 |
|---------|---------|------|
| §2 架构分层 | engine.md §2-§3 | 本篇替代 |
| §3 核心对象 | engine.md §4 | 本篇替代（职责契约替代字段列表） |
| §4 执行流程 | react-engine.md §5 | 语义一致，详细设计仍在 react-engine.md |
| §5 Hook 生命周期 | engine.md §5.3-5.4, hook-skill-engine.md | 本篇定义扩展点，详细设计仍在 hook-skill-engine.md |
| §6 工具调用 | tool-dsl.md | 引用，不重复 |
| §7 会话存储 | session-engine.md, session-and-storage.md | 引用，不重复 |
| §8 错误处理 | reliability.md | 引用，不重复 |
| §9 决策总结 | 本篇新增 | 无原对应文档 |
| Agent 上下文 | nop-ai-agent-context-model.md | 新增——上下文组成、继承、fork、内部 Agent 化 |
| 多 Agent 协同 | nop-ai-agent-multi-agent.md | 新增——冲突检测、资源竞争、协同原语 |

阅读顺序建议：

1. 本篇（核心架构）
2. `nop-ai-agent-architecture-comparison.md`（为什么这样选）
3. `nop-ai-agent-dsl.md`（DSL 层）
4. `nop-ai-agent-react-engine.md`（执行引擎）
5. 其余策略层文档
