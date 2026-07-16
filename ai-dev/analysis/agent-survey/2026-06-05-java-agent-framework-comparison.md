# Java Agent 框架对比分析（更新版）

> Status: updated
> Date: 2026-07-16 (初始: 2026-06-05)
> Scope: ~/ai/ 下 Java AI Agent 框架横向对比
> Conclusion: 三个框架各有侧重，AgentScope 的响应式架构与 nop-ai-agent 的声明式架构互补

## Context

- ~/ai/ 下有三个主要的 Java AI Agent 框架：AgentScope Java, Solon AI, Spring AI Alibaba
- 三者定位不同但都面向 JVM 上的 Agent 开发
- 需要横向对比以确定对 Nop 平台 AI 集成的最优参考

## Analysis

### 定位对比

| 维度 | AgentScope Java | Solon AI | Spring AI Alibaba | nop-ai-agent |
|------|----------------|----------|-------------------|--------------|
| **组织** | 阿里巴巴 (通义) | noear (开源) | 阿里巴巴 (云) | Nop 平台 |
| **核心依赖** | Project Reactor | Solon Framework | Spring Boot + Spring AI | Nop IoC + 可逆计算 |
| **Java 版本** | 17+ | 8-26 | 17 | 21+ |
| **许可** | Apache 2.0 | Apache 2.0 | Apache 2.0 | Apache 2.0 |
| **定位** | Agent-oriented programming | 全场景 Java AI 开发 | Spring AI 扩展 + 图引擎 | 声明式 Agent 引擎 |
| **IoC** | 无内置（依赖 Spring/Quarkus） | Solon IoC | Spring IoC | Nop IoC |
| **模块数** | ~30+ | ~30+ | ~10 | ~20+ |

### 架构对比

| 维度 | AgentScope Java | Solon AI | Spring AI Alibaba | nop-ai-agent |
|------|----------------|----------|-------------------|--------------|
| **Agent 体系** | 单层 ReActAgent + SubAgentTool | 三级 (Simple/ReAct/Team) | 两级 (ReactAgent + FlowAgents) | 策略模式 (IAgentExecutor) |
| **编排模式** | Pipeline + MsgHub | 8 种协议 (Solon Flow) | 4 种 FlowAgent (StateGraph) | call-agent + Team 系统 |
| **LLM 接口** | Model (2 方法) | ChatModel (dialect) | Spring AI ChatModel | ChatService (策略模式) |
| **工具系统** | @Tool + AgentTool + MCP | @ToolMapping + FunctionTool + MCP | Spring AI ToolCallback + MCP | IToolManager + 工具白名单 |
| **Hook 系统** | MiddlewareBase+Chain (HookEventType deprecated) | Interceptor chain | 4 位置 + Interceptor chain | IAgentLifecycleHook (12 事件) |
| **RAG** | 5 种后端 (扩展) | 15 种向量存储 + 7 种加载器 | 委托 Spring AI | IAiMemoryStore (可插拔) |
| **Memory** | InMemory + 长期 (Mem0等) | Session (InMemory/File/Redis) | CheckpointSaver (6 后端) + Store | ISessionStore + 工作记忆 |
| **MCP** | Client | Client + Server + 全传输 | Client | IToolManager (可扩展) |
| **响应式** | Reactor 全栈 | Reactor (streaming only) | Reactor (Spring AI) | 同步 + Virtual Threads |
| **Native Image** | GraalVM 支持 | 无 | 无 | 无 |
| **安全架构** | Permission Engine (三态) | Interceptor chain | Hook + Interceptor | 四层纵深安全 |

### Agent 编排深度对比

| 特性 | AgentScope | Solon AI | Spring AI Alibaba | nop-ai-agent |
|------|-----------|----------|-------------------|--------------|
| **ReAct Loop** | ✓ (完整) | ✓ (完整) | ✓ (完整) | ✓ (双层循环) |
| **Sequential** | ✓ Pipeline | ✓ 协议 | ✓ SequentialAgent | ✓ call-agent 链 |
| **Parallel** | ✓ Fanout | ✓ 协议 | ✓ ParallelAgent | ✓ CompletableFuture |
| **Pub/Sub** | ✓ MsgHub | ✗ | ✗ | ✓ IAgentMessenger |
| **Routing** | ✗ | ✗ | ✓ LlmRoutingAgent | ✓ IModelRouter |
| **Loop** | ✗ | ✗ | ✓ LoopAgent | ✓ Sustainer 续命 |
| **Hierarchical** | ✗ | ✓ 协议 | ✓ Sub-agent nesting | ✓ call-agent 嵌套 |
| **Swarm** | ✗ | ✓ 协议 | ✗ | ✓ Team 系统 |
| **Market-Based** | ✗ | ✓ 协议 | ✗ | ✗ |
| **Contract Net** | ✗ | ✓ 协议 | ✗ | ✗ |
| **Blackboard** | ✗ | ✓ 协议 | ✗ | ✗ |
| **A2A** | ✓ 扩展 | ✓ 内置 | ✓ A2aRemoteAgent | ✓ IAgentMessenger |
| **Graph/DAG** | ✗ | Solon Flow | StateGraph (类 LangGraph) | ✗ |
| **YAML Flow** | ✗ | ✓ AiFlow | ✗ | ✗ |
| **HITL** | ✓ Interrupt | ✓ Interceptor | ✓ Hook | ✓ 四层安全 + 审批门禁 |
| **子 Agent** | ✓ SubAgentTool | ✓ (TeamAgent 嵌套) | ✓ asNode() | ✓ call-agent + Team |

### Nop 兼容性评估

| 维度 | AgentScope | Solon AI | Spring AI Alibaba | nop-ai-agent |
|------|-----------|----------|-------------------|--------------|
| **IoC 兼容** | 需 Spring/Quarkus | Solon IoC（哲学相似） | Spring IoC（不兼容） | Nop IoC（原生兼容） |
| **代码可复用** | 低（Reactor 全栈） | 中（部分模块无 Solon 依赖） | 极低（Spring 深度耦合） | 高（Nop 平台原生） |
| **概念借鉴价值** | 高（Hook, SubAgentTool, GraalVM） | 最高（Talent, 三级Agent, Flow, Dialect） | 高（StateGraph, Checkpoint, Hook） | 最高（声明式配置, 四层安全） |
| **架构哲学相似度** | 低（响应式 vs 同步） | 中（非 Spring, IoC 相似） | 低（Spring 全栈） | 最高（可逆计算, 声明式） |

### 与 Nop 平台的可借鉴性排序

| 优先级 | 项目 | 可借鉴点 |
|--------|------|----------|
| **P0** | nop-ai-agent | 声明式配置、四层纵深安全、工具调用修复链、Sustainer 续命模型、拒绝账本 |
| **P0** | Solon AI | Dialect/SPI 模式、三级 Agent 体系、Talent 动态准入、Agent-as-Flow-Node、框架无关嵌入、MCP 深度集成 |
| **P1** | Spring AI Alibaba | StateGraph 有向图工作流、OverAllState 状态流、Checkpoint/Resume、四位置 Hook 模型 |
| **P1** | AgentScope Java | ReAct Loop 实现、Formatter 模式、SubAgentTool、Session/StateModule、GraalVM |

### 关键设计模式对比

| 模式 | AgentScope | Solon AI | Spring AI Alibaba | nop-ai-agent | Nop 现有模式 |
|------|-----------|----------|-------------------|--------------|-------------|
| **IoC** | 无内置 | Solon IoC | Spring IoC | Nop IoC | Nop IoC |
| **Provider 抽象** | Formatter per provider | Dialect per provider | Spring AI ChatModel | ChatService 策略模式 | 可映射到 Nop Dialect |
| **工具注册** | @Tool + AgentTool | @ToolMapping + FunctionTool | ToolCallback | IToolManager + 工具白名单 | 可映射到 Nop Biz |
| **扩展机制** | MiddlewareBase+Chain | Talent + Interceptor | Hook + Interceptor | IAgentLifecycleHook + Contribution Registry | 可映射到 Delta 定制 |
| **配置** | Builder only | Builder + YAML | Builder + YAML | XDSL 声明式 + 代码生成 | XML/YAML Delta |
| **状态管理** | Session/StateModule | FlowContext JSON | OverAllState + Checkpoint | ISessionStore + AgentExecutionContext | 可映射到 Nop ORM |
| **安全架构** | Permission Engine (三态) | Interceptor chain | Hook + Interceptor | 四层纵深安全 | 可映射到 Nop 权限 |

### 其他值得关注的 Java AI 框架

根据 GitHub 调研，以下框架也值得注意（但未在 ~/ai/ 下克隆）：

| 框架 | 组织 | 特点 |
|------|------|------|
| **LangChain4j** | LangChain4j | Java 版 LangChain，最流行的 Java AI 框架之一 |
| **Embabel** | Rod Johnson (Spring 作者) | JVM-native AI agent，Goal-Oriented Action Planning |
| **Koog** | JetBrains | Kotlin AI agent 框架 |
| **Google ADK** | Google | Java AI agent SDK |

## Conclusion

四个 Java AI Agent 框架各有侧重：
- **nop-ai-agent** 声明式配置最完整（XDSL + 代码生成），安全架构最严谨（四层纵深），与 Nop 平台原生兼容
- **Solon AI** 编排能力最丰富（8 种协议 + 三级 Agent + 动态 Talent），与 Nop 哲学最相似
- **Spring AI Alibaba** 图引擎最成熟（StateGraph 类 LangGraph），但 Spring 耦合最深
- **AgentScope Java** 响应式架构最纯粹，但 Reactor 全栈与 Nop 同步模型冲突最大

对 Nop 平台 AI 集成的推荐路径：
1. **nop-ai-agent 为核心** — 声明式配置、四层安全、工具调用修复链与 Nop 最对齐
2. **Solon AI 为参考** — Dialect/SPI 模式、Talent 动态准入、Agent-as-Flow-Node 可借鉴
3. **AgentScope Java 的响应式架构** — 可考虑在 LLM 调用层引入响应式，增强性能
4. **Spring AI Alibaba 的 StateGraph** — 有向图 + Checkpoint/Resume 机制可参考

## Open Questions

- [ ] nop-ai-agent 是否需要引入响应式 LLM 调用层？
- [ ] Solon AI 的 Talent 动态准入 + Delta 定制能否融合为 Nop 的 AI 扩展机制？
- [ ] AgentScope 的 Middleware 系统是否适合作为 Nop biz 拦截器的参考？
- [ ] nop-ai-agent 的声明式配置是否可移植到 AgentScope？

## References

- `ai-dev/analysis/agent-survey/2026-06-05-agentscope-java-analysis.md`
- `ai-dev/analysis/agent-survey/2026-06-05-solon-ai-analysis.md`
- `ai-dev/analysis/agent-survey/2026-06-05-spring-ai-alibaba-analysis.md`
- `ai-dev/analysis/agent-survey/2026-07-16-agentscope-vs-nop-ai-agent-deep-comparison.md`
- nop-ai/nop-ai-agent/ (Nop AI Agent 模块)
- https://codewiz.info/blog/java-ai-agent-frameworks-2026/
- https://www.cnblogs.com/noear/p/20011503 (Solon AI vs Spring AI vs LangChain4j)
