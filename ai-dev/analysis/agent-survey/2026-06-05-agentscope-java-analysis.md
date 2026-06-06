# AgentScope Java 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/agentscope-java — 阿里巴巴 Java AI Agent 框架
> Conclusion:

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
Agent extends CallableAgent, StreamableAgent, ObservableAgent
  → AgentBase (abstract) → ReActAgent (concrete, 主力实现)
  → StructuredOutputCapableAgent → ReActAgent
  → UserAgent (HITL)
```

- `call(List<Msg>)` → `Mono<Msg>` — 全响应式
- `stream(List<Msg>, StreamOptions)` → `Flux<Event>` — 流式事件
- `observe(Msg)` — 接收不响应（多 agent）

#### ReAct Loop

```
reasoning(iter) → model.stream() → 累积 chunks → acting(iter)
  → toolkit.callTools() (parallel/sequential) → executeIteration(iter+1)
  → summarizing() (maxIters)
```

- 流式优先：model 始终 stream，chunks 累积
- 中断检查点：iteration start, reasoning, tool exec, streaming chunks
- Pending tool recovery：无结果的工具调用可恢复
- Graceful shutdown：通过 GracefulShutdownManager 保存状态

#### Hook 系统

统一事件拦截：`PreCall → PreReasoning → ReasoningChunk → PostReasoning → PreActing → ActingChunk → PostActing → PreSummary → SummaryChunk → PostSummary → PostCall → Error`（11 种具体事件）

Hook 可修改事件、请求停止、注入工具。优先级排序。

#### Tool 系统

两种注册路径：
1. **注解式 (`@Tool`)**: 扫描 POJO 方法，victools 生成 JSON Schema
2. **编程式 (`AgentTool` 接口)**: 直接实现

特殊工具：`SubAgentTool`（agent 作为工具）、`SchemaOnlyTool`（外部工具）、`McpTool`

#### Memory

- `InMemoryMemory` — CopyOnWriteArrayList，session 持久化
- `LongTermMemory` — 扩展（Mem0, Bailian），三种模式：STATIC_CONTROL, AGENT_CONTROL, BOTH

#### Pipeline / Multi-Agent

1. **SequentialPipeline** — 链式执行
2. **FanoutPipeline** — 并行执行
3. **MsgHub** — pub/sub 多 agent 对话（自动广播）

#### Model 集成

`Model` 接口仅 2 个方法：`stream()` + `getModelName()`

5 个实现 + Formatter 模式（每种 provider 独立的消息格式转换器）：
DashScope (Qwen), OpenAI, Gemini, Anthropic, Ollama

#### Session & State

- `Session` 接口：key-value 持久化（InMemory, Json, Redis, MySQL）
- `StateModule` 接口：任何组件可 `saveTo/loadFrom(session)`
- GraalVM native image 支持（200ms 冷启动）

### 优势

1. **全响应式架构** — Project Reactor 贯穿，高吞吐 + 自然流式
2. **全面的 Hook 系统** — 覆盖 agent 全生命周期，可修改事件、注入工具
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

#### 可借鉴

- **ReAct Loop + Hook**: 完整的 agent 生命周期拦截模式，Nop biz 层可参考
- **Formatter 模式**: per-provider 消息格式转换，与 Nop 的 dialect 模式类似
- **Session/StateModule**: key-value 持久化 + 组件自动 save/load，轻量级状态管理
- **SubAgentTool**: agent 作为工具的嵌套委托模式
- **GraalVM native image**: 200ms 冷启动，适用于 serverless 场景

#### 不适用

- Project Reactor 全栈与 Nop 同步/命令式架构冲突
- 无 IoC 容器，依赖 Spring Boot
- 无 ORM/数据库层

#### 潜在集成路径

1. Nop IoC 管理 AgentScope Agent 实例（作为 beans）
2. AgentScope tools 调用 Nop GraphQL API 或 Biz 层
3. Nop 的代码生成管线为 AgentScope 生成 tool schema / agent config

## Conclusion

AgentScope Java 是 JVM 上少有的全响应式 Agent 框架，ReAct loop + Hook + SubAgentTool 的设计成熟。与 Nop 互补而非竞争：AgentScope 是 AI agent 运行时，Nop 是应用平台。最直接的集成路径是 AgentScope agent 作为 Nop 服务的"智能用户"。

## Open Questions

- [ ] AgentScope 的 Reactor 响应式模型如何与 Nop 的同步模型桥接？
- [ ] Nop IoC 能否直接管理 AgentScope 的 Agent bean？
- [ ] AgentScope 的 Hook 系统是否适合作为 Nop biz 拦截器的参考？

## References

- ~/ai/agentscope-java/README.md, README_zh.md
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/
- https://java.agentscope.io/
- https://github.com/agentscope-ai/agentscope-java
