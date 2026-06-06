# Java Agent 框架对比分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/ 下 Java AI Agent 框架横向对比
> Conclusion:

## Context

- ~/ai/ 下有三个主要的 Java AI Agent 框架：AgentScope Java, Solon AI, Spring AI Alibaba
- 三者定位不同但都面向 JVM 上的 Agent 开发
- 需要横向对比以确定对 Nop 平台 AI 集成的最优参考

## Analysis

### 定位对比

| 维度 | AgentScope Java | Solon AI | Spring AI Alibaba |
|------|----------------|----------|-------------------|
| **组织** | 阿里巴巴 (通义) | noear (开源) | 阿里巴巴 (云) |
| **核心依赖** | Project Reactor | Solon Framework | Spring Boot + Spring AI |
| **Java 版本** | 17+ | 8-26 | 17 |
| **许可** | Apache 2.0 | Apache 2.0 | Apache 2.0 |
| **定位** | Agent-oriented programming | 全场景 Java AI 开发 | Spring AI 扩展 + 图引擎 |
| **IoC** | 无内置（依赖 Spring/Quarkus） | Solon IoC | Spring IoC |
| **模块数** | ~30+ | ~30+ | ~10 |

### 架构对比

| 维度 | AgentScope Java | Solon AI | Spring AI Alibaba |
|------|----------------|----------|-------------------|
| **Agent 体系** | 单层 ReActAgent + SubAgentTool | 三级 (Simple/ReAct/Team) | 两级 (ReactAgent + FlowAgents) |
| **编排模式** | Pipeline + MsgHub | 8 种协议 (Solon Flow) | 4 种 FlowAgent (StateGraph) |
| **LLM 接口** | Model (2 方法) | ChatModel (dialect) | Spring AI ChatModel |
| **工具系统** | @Tool + AgentTool + MCP | @ToolMapping + FunctionTool + MCP | Spring AI ToolCallback + MCP |
| **Hook 系统** | 11 种事件 | Interceptor chain | 4 位置 + Interceptor chain |
| **RAG** | 5 种后端 (扩展) | 15 种向量存储 + 7 种加载器 | 委托 Spring AI |
| **Memory** | InMemory + 长期 (Mem0等) | Session (InMemory/File/Redis) | CheckpointSaver (6 后端) + Store |
| **MCP** | Client | Client + Server + 全传输 | Client |
| **响应式** | Reactor 全栈 | Reactor (streaming only) | Reactor (Spring AI) |
| **Native Image** | GraalVM 支持 | 无 | 无 |

### Agent 编排深度对比

| 特性 | AgentScope | Solon AI | Spring AI Alibaba |
|------|-----------|----------|-------------------|
| **ReAct Loop** | ✓ (完整) | ✓ (完整) | ✓ (完整) |
| **Sequential** | ✓ Pipeline | ✓ 协议 | ✓ SequentialAgent |
| **Parallel** | ✓ Fanout | ✓ 协议 | ✓ ParallelAgent |
| **Pub/Sub** | ✓ MsgHub | ✗ | ✗ |
| **Routing** | ✗ | ✗ | ✓ LlmRoutingAgent |
| **Loop** | ✗ | ✗ | ✓ LoopAgent |
| **Hierarchical** | ✗ | ✓ 协议 | ✓ Sub-agent nesting |
| **Swarm** | ✗ | ✓ 协议 | ✗ |
| **Market-Based** | ✗ | ✓ 协议 | ✗ |
| **Contract Net** | ✗ | ✓ 协议 | ✗ |
| **Blackboard** | ✗ | ✓ 协议 | ✗ |
| **A2A** | ✓ 扩展 | ✓ 内置 | ✓ A2aRemoteAgent |
| **Graph/DAG** | ✗ | Solon Flow | StateGraph (类 LangGraph) |
| **YAML Flow** | ✗ | ✓ AiFlow | ✗ |
| **HITL** | ✓ Interrupt | ✓ Interceptor | ✓ Hook |
| **子 Agent** | ✓ SubAgentTool | ✓ (TeamAgent 嵌套) | ✓ asNode() |

### Nop 兼容性评估

| 维度 | AgentScope | Solon AI | Spring AI Alibaba |
|------|-----------|----------|-------------------|
| **IoC 兼容** | 需 Spring/Quarkus | Solon IoC（哲学相似） | Spring IoC（不兼容） |
| **代码可复用** | 低（Reactor 全栈） | 中（部分模块无 Solon 依赖） | 极低（Spring 深度耦合） |
| **概念借鉴价值** | 高（Hook, SubAgentTool, GraalVM） | 最高（Talent, 三级Agent, Flow, Dialect） | 高（StateGraph, Checkpoint, Hook） |
| **架构哲学相似度** | 低（响应式 vs 同步） | 中（非 Spring, IoC 相似） | 低（Spring 全栈） |

### 与 Nop 平台的可借鉴性排序

| 优先级 | 项目 | 可借鉴点 |
|--------|------|----------|
| **P0** | Solon AI | Dialect/SPI 模式、三级 Agent 体系、Talent 动态准入、Agent-as-Flow-Node、框架无关嵌入、MCP 深度集成 |
| **P1** | Spring AI Alibaba | StateGraph 有向图工作流、OverAllState 状态流、Checkpoint/Resume、四位置 Hook 模型 |
| **P1** | AgentScope Java | ReAct Loop 实现、Formatter 模式、SubAgentTool、Session/StateModule、GraalVM |

### 关键设计模式对比

| 模式 | AgentScope | Solon AI | Spring AI Alibaba | Nop 现有模式 |
|------|-----------|----------|-------------------|-------------|
| **IoC** | 无内置 | Solon IoC | Spring IoC | Nop IoC |
| **Provider 抽象** | Formatter per provider | Dialect per provider | Spring AI ChatModel | 可映射到 Nop Dialect |
| **工具注册** | @Tool + AgentTool | @ToolMapping + FunctionTool | ToolCallback | 可映射到 Nop Biz |
| **扩展机制** | Hook (11 事件) | Talent + Interceptor | Hook + Interceptor | 可映射到 Delta 定制 |
| **配置** | Builder only | Builder + YAML | Builder + YAML | XML/YAML Delta |
| **状态管理** | Session/StateModule | FlowContext JSON | OverAllState + Checkpoint | 可映射到 Nop ORM |

### 其他值得关注的 Java AI 框架

根据 GitHub 调研，以下框架也值得注意（但未在 ~/ai/ 下克隆）：

| 框架 | 组织 | 特点 |
|------|------|------|
| **LangChain4j** | LangChain4j | Java 版 LangChain，最流行的 Java AI 框架之一 |
| **Embabel** | Rod Johnson (Spring 作者) | JVM-native AI agent，Goal-Oriented Action Planning |
| **Koog** | JetBrains | Kotlin AI agent 框架 |
| **Google ADK** | Google | Java AI agent SDK |

## Conclusion

三个 Java AI Agent 框架各有侧重：
- **Solon AI** 编排能力最丰富（8 种协议 + 三级 Agent + 动态 Talent），与 Nop 哲学最相似
- **Spring AI Alibaba** 图引擎最成熟（StateGraph 类 LangGraph），但 Spring 耦合最深
- **AgentScope Java** 响应式架构最纯粹，但 Reactor 全栈与 Nop 同步模型冲突最大

对 Nop 平台 AI 集成的推荐路径：
1. **Solon AI 为主要参考** — Dialect/SPI 模式、Talent 动态准入、Agent-as-Flow-Node 与 Nop 最对齐
2. **Spring AI Alibaba 的 StateGraph 为工作流参考** — 有向图 + Checkpoint/Resume 机制
3. **AgentScope 的 ReAct Loop + SubAgentTool 为 agent 运行时参考**

## Open Questions

- [ ] Nop 应该构建自己的 ChatModel 抽象，还是桥接现有框架（Solon AI / Spring AI）？
- [ ] Solon AI 的 Talent 动态准入 + Delta 定制能否融合为 Nop 的 AI 扩展机制？
- [ ] StateGraph 模式是否适合作为 Nop 工作流引擎的 AI agent 扩展？

## References

- `ai-dev/analysis/agent-survey/2026-06-05-agentscope-java-analysis.md`
- `ai-dev/analysis/agent-survey/2026-06-05-solon-ai-analysis.md`
- `ai-dev/analysis/agent-survey/2026-06-05-spring-ai-alibaba-analysis.md`
- https://codewiz.info/blog/java-ai-agent-frameworks-2026/
- https://www.cnblogs.com/noear/p/20011503 (Solon AI vs Spring AI vs LangChain4j)
