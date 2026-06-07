# Nop AI Agent 高层设计原则

**日期**：2026-06-06
**范围**：`nop-ai-agent` 子系统
**状态**：active

---

## 一、产品定位

**nop-ai-agent 面向大规模无人值守自动化执行。**

这意味着系统优先保证可靠性、可恢复性和确定性，而非交互体验。所有交互（包括人机协同）通过工具调用完成（如 `ask-oracle`），不是逐字符 REPL。

## 二、成功标准

1. 单 Agent 可以仅根据 DSL 文档写出合法的 `.agent.xml`、`plan.xml`、工具调用 XML，并稳定完成一次完整 ReAct 循环
2. DSL 语义与 runtime 解释可明确区分，不存在"只在 runtime 存在但被误写成 DSL 字段"的情况
3. 断路器、模型回退、会话恢复、多 Agent 并行均可独立验证
4. 外部框架的成熟实践（openai-agents、agentscope-java 等）经过对比分析后有明确采纳或拒绝理由

## 三、不可违反的约束

| # | 约束 | 含义 |
|---|------|------|
| 1 | **DSL-First** | 先定义 `xdef` schema 的字段语义，再定义 runtime 如何解释。不把 runtime 假设伪装成 DSL 字段。 |
| 2 | **Agent 即配置，Engine 即执行** | Agent 是 `agent.xdef` 装载得到的纯配置对象，不持有执行逻辑和状态。执行由独立引擎层负责。 |
| 3 | **配置、执行、状态三者分离** | AgentModel 是静态配置，Agent 是无状态执行体，AgentState/AgentSession 是独立状态对象（按 sessionId 获取，作为上下文传入 Agent）。与 Nop 可逆计算原则一致。便于 Delta 定制、便于独立测试、便于状态恢复。 |
| 4 | **权限默认收敛** | 子 Agent 只能继承或收缩权限，不能提升。程序校验优先于 prompt 约束。 |
| 5 | **先确定性能力，后智能决策** | 参数验证、错误分类、超时、安全限制优先于 Advisor Agent 决策（retry/compression/repair advisor）。 |
| 6 | **不把未来设想伪装成当前设计** | Platform Layer 的具体组件（ActorRuntime、TeamManager 等）实现方案可以后置，但 Actor 心智模型本身是当前基线。 |
| 7 | **架构决策有外部参照** | 每个关键决策（Agent 模型、Hook 机制、会话设计）需有对比分析支撑，拒绝理由同样需要记录。 |

## 四、显式 Non-Goals

本系统**不做**以下事情：

| Non-Goal | 理由 |
|----------|------|
| 交互式 REPL / 终端 Agent | Nop 是自动化基础设施，不是终端工具。交互式场景可以在自动化引擎之上构建。 |
| 中央编排器 Agent | 自举问题（"谁协调协调器"），增加复杂度。LLM 通过工具调用自行决定委派更灵活。 |
| 逐 token 流式决策 | 无人值守场景不需要，且增加引擎状态机复杂度。流式输出仍可通过事件发布提供。 |
| 文件系统作为唯一状态存储 | Agent 存储分为 VFS（工作文件）、IMessageService（通信）、持久化接口（结构化状态）三个独立抽象，不绑定单一物理存储。 |
| MCP (Model Context Protocol) | Nop 有自己的工具 DSL（`tool.xdef`）和完整的 XLang 生态。引入 MCP 会增加协议转换层且无独特收益。外部工具通过标准 REST/GraphQL 集成。 |

## 五、设计收敛路径

设计按以下顺序收敛，不可逆序：

1. **先定义现有 DSL 语义**（`agent.xdef`、`agent-plan.xdef`、`tool.xdef` 等）
2. **再定义 runtime 如何解释这些 DSL**（引擎层）
3. **再补会话、安全、可靠性等策略层约束**
4. **最后讨论未来 schema 扩展和多 Agent 编排**

只要这条顺序不乱，设计就不会滑回 Java-first。

## 六、必须由人决策的决策点

以下决策不可由 AI 自行发明，必须经过显式确认：

1. `agent.xdef` schema 的字段增减
2. 核心定位的变更（从"无人值守"改为其他定位）
3. 异步模型的选择（`CompletionStage` vs Reactor vs 其他）
4. 会话存储后端的变更（文件 → DB → 分布式）

## 七、核心隐喻

**Agent 组合是 Actor，系统即分布式 Actor 集群**。

一个运行中的"Actor"实际上是三个对象的组合：`AgentModel`（配置模板）+ `Agent`（无状态执行体）+ `AgentSession`（独立状态）。Agent 本身不持有状态——状态由 AgentSession 管理，按 sessionId 独立获取，作为上下文传入 Agent。sessionId 是运行时生成的标识符，不涉及 DSL——DSL 描述静态结构，运行时概念由引擎管理。这意味着：

- 同一个 Agent（配置）可以被多个 Session 复用
- 状态可以独立于执行体被持久化、恢复、迁移
- 崩溃恢复时，重建 AgentSession 即可，Agent 无需特殊处理

Agent 类似操作系统中的独立进程——fork（派生子 Agent，Copy-on-Write 快照）、exec（替换执行配置）、pipe（通过工具调用和消息队列通信）、独立地址空间（Agent 间不共享可变状态，每个 Agent 拥有自己的 Plan 和 Todo）。

人机协同是**工具层面的选择**（`ask-oracle`），不是引擎架构的内置特性。系统基本假定全自动执行。

---

## 与其他文档的关系

- `01-architecture-baseline.md` — 架构基线：部署模型、通信模型、存储模型、Session 模型、多租户
- `02-execution-model.md` — 执行模型：双循环、Hook、Steering、执行控制
- `nop-ai-agent-reliability.md` — 可靠性：恢复模型、容错策略
- `nop-ai-agent-context-model.md` — 上下文模型：fork 继承、权限继承
- `nop-ai-agent-roadmap.md` — 实施阶段和验收标准
