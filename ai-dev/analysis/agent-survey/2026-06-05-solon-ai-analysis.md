# Solon AI 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/solon-ai — Solon 框架 AI 模块
> Conclusion:

## Context

- Solon AI 是 Solon 项目的核心子项目，面向 Java 开发者的全栈智能体开发框架
- 支持 Java 8 到 Java 26，是目前兼容性最广的 Java AI 框架
- 对 Nop 意义：同为非 Spring 的 Java 框架，IoC 哲学相似（无 private field 注入）

## Analysis

### 项目定位

- **组织**: org.noear (open source, Solon 项目)
- **许可**: Apache 2.0
- **版本**: 4.0.0-SNAPSHOT (parent: solon-parent)
- **Java 范围**: JDK 8 ~ JDK 26（业界最宽）
- **核心**: 全场景 Java AI 开发框架（Chat, RAG, MCP, Agent, Flow）

### 模块结构 (~30+ 模块)

```
核心层:
  solon-ai-core/          ChatModel, EmbeddingModel, FunctionTool, Talent, Repository
  solon-ai/               聚合 JAR (core + 全部 LLM dialect)

LLM Dialect 层:
  solon-ai-dialect-openai/    OpenAI + Responses API
  solon-ai-dialect-ollama/    Ollama 本地模型
  solon-ai-dialect-dashscope/ 阿里 DashScope (通义千问)
  solon-ai-dialect-gemini/    Google Gemini
  solon-ai-dialect-anthropic/ Anthropic Claude

Agent 层:
  solon-ai-agent/         SimpleAgent, ReActAgent, TeamAgent

Flow 层:
  solon-ai-flow/          YAML 流编排 (Dify 风格)

MCP 层:
  solon-ai-mcp/           MCP Server + Client (SSE, Streamable HTTP, STDIO)

RAG 层:
  solon-ai-rag-loaders/   PDF, Word, Excel, PPT, HTML, Markdown, DDL, Text
  solon-ai-rag-repositorys/ 15 种向量存储后端 (14 modules + InMemory)
  solon-ai-rag-searchs/   百度, Bocha, Tavily 搜索

动态技能层:
  solon-ai-talents/       17 个预构建 Talent (cli, web, file, data, lsp, text2sql...)

其他:
  solon-ai-harness/       完整 agent harness (命令系统, 多语言代码执行, HITL, 权限)
  solon-ai-a2a/           Agent-to-Agent 协议
  solon-ai-anp/           Agent Network Protocol
  solon-ai-acp/           Agent Communication Protocol
  solon-ai-ui/            AGUI 适配器, AI SDK 适配器
```

### 核心抽象

#### ChatModel (统一 LLM 接口)

```java
ChatModel chatModel = ChatModel.of("http://127.0.0.1:11434/api/chat")
    .provider("ollama").model("qwen2.5:1.5b").apiKey("...").build();
```

- Builder 模式 + 流式 API
- Provider/Dialect 通过 `ChatDialectManager` 自动发现（classpath 扫描）
- 同步 `call()` + 响应式 `stream()` (Flux<ChatResponse>)
- Session 管理 (InMemory, File, Redis)
- 工具调用 + 自动工具调用循环（递归解析）

#### Dialect 系统

所有模型类型共享统一模式：
`ChatDialect.matched(config) → buildRequestJson() → parseResponseJson()`

- SPI 注册：Solon IoC `AiPlugin` 扫描 `ChatDialect` beans
- 静态回退注册：核心 dialect 直接在 Manager static block

#### 三级 Agent 体系

| 层级 | Agent | 特征 |
|------|-------|------|
| Level 1 | **SimpleAgent** | 单次 LLM 调用，无推理循环 |
| Level 2 | **ReActAgent** | Think→Act→Observe 循环，规划模式，上下文压缩 |
| Level 3 | **TeamAgent** | 多 agent 协作容器，基于 Solon Flow 图引擎 |

**TeamAgent 8 种协议**: SEQUENTIAL, HIERARCHICAL, SWARM, MARKET_BASED, CONTRACT_NET, BLACKBOARD, A2A, NONE

- HIERARCHICAL 协议有完整的 "running dashboard"、错误追踪、负载均衡
- Agent 接口 extends `NamedTaskComponent` — agent 可作为 Flow 图节点

#### 动态 Talent 系统

```java
public interface Talent {
    boolean isSupported(Prompt prompt);     // 动态准入检查
    void onAttach(Prompt prompt);           // 生命周期钩子
    String getInstruction(Prompt prompt);   // 动态指令注入
    Collection<FunctionTool> getTools(Prompt prompt); // 动态工具注入
}
```

Talent 不是静态工具，而是：
1. **动态准入**: 仅在相关时激活（关键词匹配、上下文分析）
2. **动态指令**: 根据上下文注入不同 system prompt
3. **生命周期钩子**: 初始化、审计、上下文预处理

#### RAG 管线

`Loader → Splitter → EmbeddingModel → Repository → Search → RerankingModel → ChatModel`

- **15 种向量存储**: InMemory (core), Redis, Milvus, Qdrant, Chroma, ES, OpenSearch, PgVector, MySQL, MariaDB, DashVector, Weaviate, VectorEx, TcVectorDB (14 explicit modules + InMemory)
- **7 种文档加载器**: PDF, Word, Excel, PPT, HTML, Markdown, DDL
- **多种分词器**: Regex, TokenSize, Semantic, Json, Pipeline

#### MCP 支持

- **Client**: `McpClientProvider` 实现 ToolProvider/ResourceProvider，支持 SSE + Streamable HTTP + STDIO
- **Server**: `@McpServerEndpoint` 注解式注册
- 心跳 + 指数退避重连 + 本地缓存 + 白名单/黑名单过滤
- MCP 工具可从 `.mcp.json` 配置加载

#### AiFlow (YAML 流编排)

```yaml
layout:
  - type: "start"
  - task: "@VarInput"
  - task: "@EmbeddingModel"
  - task: "@ChatModel"
  - task: "@ConsoleOutput"
```

类似 Dify 的低代码体验，组件即 Solon Flow `TaskComponent`。

### 配置

```yaml
solon.ai:
  chat:
    default:
      apiUrl: "http://127.0.0.1:11434/api/chat"
      provider: "ollama"
      model: "qwen2.5:1.5b"
```

Properties 绑定 + Builder 模式双重支持。

### 框架无关嵌入

- 支持 Spring Boot, Vert.X, Quarkus, Micronaut 嵌入
- `mcp-core` 模块完全独立（无 Solon 依赖）
- 示例仓库: solonlab/solon-ai-mcp-embedded-examples

### 优势

1. **Java 兼容性最广**: JDK 8-26，唯一支持如此宽范围的 Java AI 框架
2. **三级 Agent 体系**: Simple → ReAct → Team，概念清晰
3. **8 种协作协议**: 远超同类框架的多 agent 拓扑选择
4. **动态 Talent 系统**: 上下文感知的技能激活，比静态工具注册更先进
5. **Agent-as-Flow-Node**: agent 可参与 Solon Flow 图计算，实现层级组合
6. **15 种向量存储**: RAG 后端支持最全面
7. **深度 MCP 集成**: Client + Server + 全传输模式
8. **框架无关**: 可嵌入 Spring Boot 等非 Solon 框架
9. **Harness 模块**: 类 Claude Code 的完整 agent harness（多语言代码执行 + HITL + 权限）
10. **Session 持久化**: FlowContext JSON 序列化/反序列化

### 劣势

1. **文档偏中文**: 限制国际采纳
2. **无 LLM Mock**: 测试需要真实后端，CI 成本高
3. **Solon 生态耦合**: Agent/Flow 层依赖 Solon Flow
4. **无内置评估框架**: 无 benchmark 或质量评估
5. **复杂度面大**: 三级 agent + 8 协议 + Talent + 拦截器 = 陡峭学习曲线
6. **注解不兼容**: `@ToolMapping` 是 Solon AI 特有，不兼容 Spring AI

### 与 Nop 平台的关联

#### 可借鉴

- **Dialect 模式**: ChatDialect + SPI 自动发现 = Strategy + SPI，Nop 已有类似模式
- **Talent 动态准入**: 与 Nop Delta 定制精神类似——上下文依赖的行为
- **Agent-as-Flow-Node**: agent 作为图节点的设计可启发 Nop 工作流引擎
- **Session 持久化**: FlowContext JSON 模式可直接应用
- **框架无关嵌入**: Nop 也可考虑类似的多框架支持策略
- **三级 Agent 体系**: 为 Nop AI 集成提供清晰的分层参考

#### 不适用

- Solon IoC 与 Nop IoC 不同（但哲学相似：无 private field 注入）
- Solon Flow 依赖
- snack4 JSON 库

## Conclusion

Solon AI 是 Java AI 框架中 Agent 原生深度最强的全场景框架。三级 Agent 体系 + 8 种协作协议 + 动态 Talent 系统 + 框架无关嵌入使其在 Java AI 生态中独树一帜。对 Nop 最有价值的借鉴是：Dialect/SPI 模式、Talent 动态准入（类似 Delta 定制）、Agent-as-Flow-Node（类似 Nop 工作流节点）。

## Open Questions

- [ ] Solon AI 的 Talent 动态准入能否映射到 Nop 的 Delta 定制机制？
- [ ] 三级 Agent 体系是否适合作为 Nop AI 集成的分层架构？
- [ ] Solon AI 的 MCP 深度集成模式是否可直接在 Nop 中实现？

## References

- ~/ai/solon-ai/README.md
- ~/ai/solon-ai/solon-ai-core/src/main/java/org/noear/solon/ai/
- ~/ai/solon-ai/solon-ai-agent/src/main/java/org/noear/solon/ai/agent/
- https://solon.noear.org/article/family-solon-ai
- https://gitee.com/opensolon/solon-ai
