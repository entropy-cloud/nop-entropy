# Nop AI Agent 分层架构与实施路线

**日期**：2026-06-07
**范围**：`nop-ai-agent` 子系统
**状态**：active

---

## 1. 目标

本篇定义 `nop-ai-agent` 的**分层架构**和设计收敛原则。

**核心组织原则：系统由接口层构成，扩展通过添加接口实现，不通过阶段切换。**

每一层定义接口契约和默认实现。更高级的能力 = 添加更多接口实现，而不是进入新阶段。所有接口始终存在——低级层提供 pass-through 默认实现，高级层替换为功能实现。

> **模式来源**：本文的设计模式来自 15+ 个 Agent 框架的源码级分析（Reasonix, PilotDeck, OpenCode, nanobot, VoltAgent, Hermes, AgentScope Java, Solon AI 等），按 P0/P1/P2 三档组织。

---

## 2. 当前判断

`nop-ai-agent` 目前处于典型的"设计空间很大，但实现基础还不稳"的阶段。

这类阶段最容易出现两个问题：

1. 一次性规划过多高级能力
2. 设计文档过早进入大量实现细节

因此路线图的作用，是把系统收敛到清晰的分层结构，而不是继续横向扩张设计面。

---

## 3. 收敛原则

### 3.1 先主链路，后增强

先稳定：

- `agent.xdef` 的字段语义
- `agent-plan.xdef` 的字段语义
- `tool.xdef` / `tool-call.xdef` / `call-tools-response.xdef` 的字段语义
- `call-agent.tool.xml` 的现有契约

再做：

- runtime 对 DSL 的解释
- 会话、权限和容错策略
- 多 Agent 编排和平台级增强

### 3.2 先单 Agent，后多 Agent

如果单 Agent runtime 还没有稳定，多 Agent 编排只会放大问题。

### 3.3 先确定性能力，后智能决策能力

优先程序化的部分：

- 参数验证
- 错误分类
- 超时
- 安全限制

再引入 Advisor Agent 决策：

- retry advisor
- compression advisor
- repair advisor

### 3.4 先稳定边界，后细化实现

一篇设计文档更应该固定：

- 对象边界
- 生命周期
- 输入输出契约

而不是先固定大量实现细节。

---

## 4. 分层架构

### 4.1 总览

```
Layer 4: Platform Extensions (平台扩展层)
   IMemoryAdapter, ISkillCurator, IMailbox, IContributionRegistry
   ─── 依赖 Layer 1-3 接口 ───

Layer 3: Reliability Extensions (可靠性扩展层)
   ICircuitBreaker, ISustainer, IRetryPolicy, IGoalTracker, ICheckpointManager
   ─── 依赖 Layer 1-2 接口 ───

Layer 2: Execution Extensions (执行扩展层)
   IContextGovernor, IToolCallRepairer, ICompactor, IGuardrail,
   IHook, IRouter, ITalent, IModelDialect
   ─── 依赖 Layer 1 接口 ───

Layer 1: Core Interfaces (核心接口层)
   IAgentEngine, AgentModel, AgentSession, IAgentExecutor,
   IMessageFormat, IPermissionProvider, AgentEventPublisher
   ─── 无内部依赖，依赖 nop-ai-core / nop-ai-llm / nop-ai-toolkit ───
```

**依赖规则**：上层只依赖下层的接口，不依赖具体实现。Layer N 的接口实现在 Layer N+1 中提供。

### 4.2 Layer 1: Core Interfaces（核心接口层）

系统运行的最低要求。没有这些接口，Agent 无法执行。

| 接口 | 职责 | 默认实现 |
|------|------|---------|
| `IAgentEngine` | 顶层入口，接受配置+请求，创建上下文并启动执行 | `DefaultAgentEngine` |
| `AgentModel` | 纯配置对象，从 agent.xdef 装载，不持有逻辑和状态 | — (数据对象) |
| `AgentSession` | 按 sessionId 的独立状态对象，持久化跨请求存在 | `DefaultAgentSession` |
| `AgentExecutionContext` | 单次执行的全部内存态容器 | — (数据对象) |
| `IAgentExecutor` | 执行策略接口（ReAct、单轮等） | `ReActExecutor` |
| `AgentEventPublisher` | 执行状态→外部可观察的事件流 | `DefaultEventPublisher` |
| `IMessageFormat` | Provider 无关的消息格式 | `CanonicalMessageFormat`（2 role, 6 content block types） |
| `IPermissionProvider` | 权限派生 | `HierarchicalPermissionProvider`（3-source merge） |

**扩展方式**：
- 新 `IAgentExecutor` 实现（Plan-and-Execute、Reflexion 等）
- 新 `IMessageFormat` 适配器（非标准 Provider）
- 自定义 `IPermissionProvider`（组织特定规则）

**来源**：Pattern 1.4 (Agent-as-Schema), 1.5 (Permission Derivation), 1.8 (CanonicalMessage)

### 4.3 Layer 2: Execution Extensions（执行扩展层）

扩展执行行为，不改变核心契约。所有接口有 pass-through 默认实现——系统可以不带任何扩展运行。

| 接口 | 职责 | Pass-through 默认 | 推荐功能实现 |
|------|------|------------------|-------------|
| `IContextGovernor` | 每轮上下文治理管线 | `NoOpGovernor` | `PipelineGovernor`（5-stage: drop_orphan→backfill→microcompact→budget→snip） |
| `IToolCallRepairer` | 工具调用修复链 | `NoOpRepairer` | `ChainRepairer`（flatten→scavenge→truncation→storm） |
| `ICompactor` | 渐进上下文压缩 | `NoOpCompactor` | `ProgressiveCompactor`（MicroCompact→Snip→LLM Summary） |
| `IGuardrail` | 输入/输出内容护栏 | `NoOpGuardrail` | 用户按需添加（内容过滤、PII 检测等） |
| `IHook` | 生命周期事件处理 | `PriorityHookChain`（10 点） | 用户按需添加 |
| `IRouter` | 请求路由策略 | `PassThroughRouter`（直连配置模型） | `SmartRouter`（Judge 分类 + Fallback Chain） |
| `ITalent` | 动态行为准入 | — (空集合) | 用户按需添加（cli, web, file, data, lsp, text2sql...） |
| `IModelDialect` | Provider 消息格式转换 | `IdentityDialect`（无转换） | DashScope, OpenAI, Gemini, Anthropic, Ollama |

**扩展方式**：
- 替换 pass-through 为功能实现（零业务代码改动）
- 添加自定义 `IGuardrail`（allow/modify/block + streaming abort）
- 添加 `ITalent` 实现（基于关键词/上下文分析动态激活行为和工具集）
- 添加 Provider 特定 `IModelDialect`（Formatter pattern，与 Nop `IDialect` 一致）

**来源**：Pattern 1.1 (Cache-First), 1.2 (Tool-Call Repair), 1.7 (Context Governance), 2.1 (Smart Router), 2.2 (Three-tier Compaction), 2.5 (Guardrail), 2.8 (Contribution Types), 2.10 (Formatter), 2.11 (Talent)

### 4.4 Layer 3: Reliability Extensions（可靠性扩展层）

为生产环境加固。所有接口有最简默认实现。

| 接口 | 职责 | 最简默认 | 推荐功能实现 |
|------|------|---------|-------------|
| `ICircuitBreaker` | 连续故障后断路（fail-fast） | `AlwaysClosed` | `ThresholdBreaker`（3 连续失败 → open, 60s cooldown） |
| `ISustainer` | "永不放弃"策略（与 ICircuitBreaker 互斥选择） | `NoOpSustainer` | `SisypheanSustainer`（stop-hook + todo 检查强制继续） |
| `IRetryPolicy` | Provider 重试策略 | `NoRetry` | `StandardRetryPolicy`（3 retries + 429 语义分类 + image fallback） |
| `IGoalTracker` | 持续目标跟踪 | `NoOpGoalTracker` | `SessionGoalTracker`（超时豁免 + 透明续接最多 12 轮） |
| `ICheckpointManager` | 执行状态快照/恢复 | `NoOpCheckpoint` | `ToolExecutionCheckpoint`（工具执行前自动保存） |

**扩展方式**：
- `ICircuitBreaker` 和 `ISustainer` 可配置互斥选择（两种弹性哲学）
- `IRetryPolicy` 可选 standard（3 次后退）或 persistent（无限重试，相同错误 10 次停）
- `IGoalTracker` 让活跃 goal 获得 LLM 超时豁免和 turn 透明续接

**来源**：Pattern 2.3 (Circuit Breaker), 2.6 (Sustained Goals), 3.6 (Sisyphean), 3.9 (Provider Retry)

### 4.5 Layer 4: Platform Extensions（平台扩展层）

多 Agent、多租户、分布式场景。所有接口有单进程默认实现。

| 接口 | 职责 | 单进程默认 | 分布式实现 |
|------|------|-----------|-----------|
| `IMessageService` | Agent 间通信 | `LocalMessageService`（内存队列 + CompletableFuture） | `DBMessageService`（跨进程路由） |
| `IMemoryAdapter` | 记忆持久化三适配器 | `InProcessAdapter`（本地存储） | Storage / Embedding / Vector 三适配器 |
| `ISkillCurator` | 技能生命周期管理 | `NoOpCurator` | `LLMCurator`（ACTIVE→STALE→ARCHIVED + LLM 审查聚类） |
| `IMailbox` | 崩溃安全异步消息 | — | `DeferredAckMailbox`（3-phase reservation, at-least-once） |
| `IContributionRegistry` | 插件贡献注册 | `SimpleRegistry` | 7 贡献类型（Tool, Command, Hook, MCP, Permission, Prompt, Router） |

**扩展方式**：
- `IMessageService` 从 Local → DB-backed，零业务代码改动
- 添加 `IMemoryAdapter` 实现（向量存储、语义搜索）
- 添加 `ISkillCurator` 实现（LLM 驱动的技能审查和聚类）

**来源**：Pattern 2.4 (Three-Adapter Memory), 2.7 (Deferred-Ack Mailbox), 2.8 (Contribution Types), 2.9 (Curator), 3.3 (Dual Bus)

---

## 5. 实施优先级

实施按优先级推进，但架构不按阶段切换。以下顺序反映依赖关系和业务价值。

### 5.1 必须先稳定（Layer 1 全部 + Layer 2 核心）

**DSL 语义定稿**：
1. `agent.xdef` / `agent-plan.xdef` / `tool.xdef` 语义定稿
2. `call-agent.tool.xml` 的现有契约确认

**Layer 1 核心**：
3. `IAgentEngine` + `ReActExecutor` 最小闭环
4. `IMessageFormat` (CanonicalMessage — 2 role, 6 block types)
5. `IPermissionProvider` (3-source merge 算法, ~200 行)
6. Event Sourcing session 模型（JSONL event log + CompactionEntry）

**Layer 2 核心**：
7. `IModelDialect` (Formatter — 5 Provider 适配，参考 AgentScope Java)
8. `ITalent` (动态准入扩展点，参考 Solon AI)
9. `IContextGovernor` (5-stage pipeline)
10. `IToolCallRepairer` (4-stage repair chain)
11. `ICompactor` 渐进压缩初始版（Layer 0 Tool Result 预截断 + Layer 1 零成本微压缩 + 基础 Layer 3 LLM 摘要）
12. Token 计数（Provider-reported usage + 简单字符比例估算）

### 5.2 其次加固（Layer 2 完善 + Layer 3 核心）

13. `IGuardrail` 护栏管线（allow/modify/block + streaming abort）
14. `IRouter` (Smart Router: Judge 分类 + Fallback Chain)
15. `ICircuitBreaker` (ThresholdBreaker) + `IRetryPolicy` (StandardRetryPolicy)
16. `IGoalTracker` (SessionGoalTracker: 超时豁免 + 透明续接)
17. `ICompactor` 完整 5 层管道（补充 Layer 2 中间 turn 裁剪 + Layer 4 强制退出）
18. `ICheckpointManager` (ToolExecutionCheckpoint)

### 5.3 后续扩展（Layer 3 完善 + Layer 4）

19. `ISustainer` (SisypheanSustainer — 与 ICircuitBreaker 互斥)
20. `IMemoryAdapter` (Storage / Embedding / Vector 三适配器)
21. `ISkillCurator` (LLMCurator — ACTIVE→STALE→ARCHIVED + LLM 审查)
22. `IMailbox` (DeferredAckMailbox — 3-phase reservation)
23. `IContributionRegistry` (7 贡献类型)
24. Actor Runtime 平台层（ActorRuntime, MessageRouter, TeamManager, RecoveryManager, ResourceGuard — 详见 `nop-ai-agent-actor-runtime-vision.md`）

---

## 6. 验收标准

### 6.1 Layer 1 验收

- 可以仅根据 DSL 文档写出合法的 `.agent.xml`、`plan.xml`、工具调用 XML
- 可以明确区分哪些字段是 DSL 语义，哪些是 runtime 解释
- 一轮 Agent 执行可以描述为 "DSL → runtime 解释 → 工具/结果回灌" 的闭环

### 6.2 Layer 2 验收

- 能清楚说明 Skill、Hook、Plan、Todo 如何附着在现有 DSL 之上
- 能清楚说明 Context Governance、Tool-Call Repair、Guardrail 这些扩展如何替换 pass-through 默认
- 不把运行时假设误写成新的 DSL 字段

### 6.3 Layer 3 验收

- LLM 调用故障可以区分是否自动重试
- 工具调用能在执行前被验证和拦截
- 长对话能触发压缩并继续运行
- 运行时间和工具超时可控

### 6.4 Layer 4 验收

- provider 连续故障后系统可自动降级
- 长任务中断后可以恢复
- 多 Agent 任务可以通过 Flow / Task 组织
- 多用户可并发运行独立 Actor，租户间资源隔离

---

## 7. 当前最值得固定的设计决策

建议明确固定以下决策，不再反复摇摆：

1. 以现有 `xdef` 和 `.tool.xml` 作为设计入口
2. 文档先定义 DSL 语义，再定义 runtime 解释
3. `call-agent` 以真实 `call-agent.tool.xml` 为准，不在文档里额外发明字段
4. Hook 基于 `agent.xdef` 的事件模式
5. Plan 与 Todo 独立
6. 多 Agent 编排后置
7. 扩展通过添加接口实现，不通过阶段切换
8. 所有 Layer 2-4 接口有 pass-through/最简/单进程默认实现

---

## 8. 当前最值得延期的设计决策

下面这些先不要写成当前 DSL 或当前主体架构：

- `call-agent` 的未来字段扩展
- `async/detached` 的完整行为语义
- 通用 AgentSession 消息队列
- AI repair branch 的分支合并模型
- 多 Agent 图执行的统一抽象

这些内容都依赖实现经验，应在第一轮 runtime 落地后再定。

---

## 9. 文档维护建议

后续设计文档建议遵守下面规则：

1. 总览文档只讲边界、对象和核心决策
2. 专题文档只讲一个主题
3. 研究过程和框架对照不要混进主设计文档
4. 伪代码只保留最小必要片段
5. 路线图只保留分层结构、接口列表和优先级

---

## 10. 结论

`nop-ai-agent` 的架构组织为四层接口，每层有默认实现和扩展点：

1. **Layer 1 (Core)** 定义系统运行的最低要求——Agent 配置、执行策略、消息格式、权限派生
2. **Layer 2 (Execution)** 扩展执行行为——上下文治理、工具修复、压缩、护栏、路由、Provider 适配
3. **Layer 3 (Reliability)** 加固生产环境——熔断、重试、持续目标、检查点
4. **Layer 4 (Platform)** 支持多 Agent / 多租户 / 分布式——消息服务、记忆适配器、技能管理

实施按优先级推进，但架构不按阶段切换——所有接口始终存在，高级能力 = 添加更多接口实现。

只要分层边界不乱，设计文档就不会再次滑回 Java-first 或 Phase-driven。

---

## 与其他文档的关系

- `00-vision.md` — 设计原则和约束
- `01-architecture-baseline.md` — 架构基线（Layer 1 核心对象的详细定义）
- `02-execution-model.md` — 执行模型（双循环、Hook 生命周期）
- `nop-ai-agent-reliability.md` — 可靠性增强（Layer 3 的详细设计）
- `nop-ai-agent-llm-layer.md` — LLM 层设计（IMessageFormat, IModelDialect, ITalent, IRouter, IRetryPolicy）
- `nop-ai-agent-actor-runtime-vision.md` — Platform Layer 愿景（Layer 4 的演进方向）
