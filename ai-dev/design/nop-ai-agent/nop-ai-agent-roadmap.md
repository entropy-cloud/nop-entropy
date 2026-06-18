# nop-ai-agent 组件分解与开发路线

> Status: active
> Updated: 2026-06-15（L3-4d DB-backed session store 完成 — Plan 185；`DBSessionStore` 使任何共享 DB 的服务实例可接管恢复 session；L2-17~L2-22 用量追踪与按模型计费设计完成 — 见 `nop-ai-agent-usage-and-billing.md`）
> Parent: `ai-dev/design/nop-ai-agent/README.md`

---

## 1. 定位重申

nop-ai-agent 是 Nop 平台的 Agent 执行引擎，定位为**面向大规模无人值守自动化执行的 DSL-First Agent 框架**。

**核心取舍**：保留 Agent-as-Configuration + Actor 消息模型 + ReAct 循环，去除交互式 REPL、中央编排器、逐 token 流式决策等特性。

**与 Nop 平台的关系**：nop-ai-agent 使用 Nop 平台的标准基础设施——XLang XDEF 定义 Agent DSL，IoC 容器管理引擎组件，Delta 定制 Agent 配置，nop-ai-core（含 `llm.xdef` + `{provider}.llm.xml`）提供 LLM 调用能力，nop-ai-toolkit 抽象工具调用。

---

## 2. 当前状态

### 2.1 已完成

| # | 组件 | 状态 | 说明 |
|---|------|------|------|
| M1 | `agent.xdef` schema | ✅ 已定义 | Agent 元模型：name, chatOptions, tools, `availableSkills`, `requiredSkills`, permissions, constraints, prompt, hooks |
| M2 | `agent-plan.xdef` schema | ✅ 已定义 | Plan 元模型：完整的 phases > tasks (递归), errors, success criteria, scope, closure |
| M3 | 代码生成管线 | ✅ 已完成 | `precompile/gen-agent-xdsl.xgen` → 32 个 _gen Java model 类 + 37 个非 _gen 文件（33 stub ≤9 行 + 4 实体类） |
| M4a | agent-plan DSL 注册与加载 | ✅ 已完成 | `agent-plan.register-model.xml` 支持 xml/yaml/md 三种格式 |
| M4b | agent DSL 注册与加载 | ❌ **缺失** | 无 `agent.register-model.xml`，`.agent.xml` 文件无法被加载 |
| M5 | Markdown 字段映射 | ✅ 已完成 | `agent-plan.record-mappings.xml` 支持 Markdown → PlanModel 解析 |
| M6 | 基础数据类型 | ✅ 已完成 | `AgentExecStatus` 枚举(4 值)；`IAiMemoryStore` 全 8 方法（4 抽象 + 4 default UOE）+ `InMemoryAiMemoryStore` 功能化实现（plan 189）；`AiMemoryItem` 全字段（priority/tokenEstimate/pinned/checksum/lastAccessTime/accessCount，L1-16）——见 L1-16 + L2-15 |
| M7 | `BaseAgent` stub | ✅ 已删除 | 7 行空壳类，无引用，已删除（L1-14） |
| D1 | 设计文档体系 | ✅ 已完成 | 25 篇设计文档（排除 README 和 roadmap）覆盖 4 层架构 |

### 2.2 未实现

> 以下按 §4 的 L 层编号引用。所有运行时代码均为零实现。

| # | 组件 | 对应设计文档 | 状态 |
|---|------|-------------|------|
| — | ReAct 执行引擎 | `nop-ai-agent-react-engine.md` | ❌ 未开始 |
| — | Agent Engine (Actor 入口) | `01-architecture-baseline.md` §四 | ❌ 未开始 |
| — | AgentSession 会话管理 | `nop-ai-agent-session-engine.md` | ❌ 未开始 |
| — | AgentEventPublisher | `01-architecture-baseline.md` §四 | ❌ 未开始 |
| — | IPermissionProvider 权限派生 | `nop-ai-agent-security-and-permissions.md` | ❌ 未开始 |
| — | IToolCallRepairer 工具修复 | `nop-ai-agent-context-model.md` | ❌ 未开始 |
| — | IContextCompactor 渐进压缩 | `nop-ai-agent-context-model.md` | ❌ 未开始 |
| — | Hook/Skill 引擎 | `nop-ai-agent-hook-skill-engine.md` | ✅ 已完成（Hook via Plan 150，Skill via Plan 163） |
| — | IContentGuardrail 护栏 | `nop-ai-agent-context-model.md` | ❌ 未开始 |
| — | IModelRouter 模型路由 | `nop-ai-agent-llm-layer.md` | ❌ 未开始 |
| — | IRetryPolicy 重试策略 | `nop-ai-agent-reliability.md` | ❌ 未开始 |
| — | ICircuitBreaker 熔断 | `nop-ai-agent-reliability.md` | ✅ 已落地（plan 210：`ICircuitBreaker` + `CircuitState` + `AlwaysClosed` 默认 + `ThresholdBreaker`） |
| — | IGoalTracker 目标跟踪 | `nop-ai-agent-reliability.md` | ✅ 已落地（plan 211：`IGoalTracker` + `GoalAssessment` + `IterationSnapshot` + `NoOpGoalTracker` 默认 + `SessionGoalTracker`） |
| — | ICheckpointManager 检查点 | `nop-ai-agent-reliability.md` | ✅ 已落地 |
| — | Working Memory (工具) | `01-architecture-baseline.md` §四 | ✅ 已落地（plan 189：`InMemoryAiMemoryStore` + `IMemoryStoreProvider` + read/write/search-memory tools + dispatch-path wiring） |
| — | 单元测试 | — | ❌ 未开始 |

> 说明：本表不再使用 E-number 编号，避免与 §4 的 L-number 造成双编号混乱。具体实现优先级见 §4 工作项清单。

### 2.3 实际依赖关系（基于 pom.xml 审计）

```
nop-ai-api (ChatMessage, ChatOptions 等 AI 接口定义)
    ↑
nop-ai-core (ChatCompletion 实现, llm.xdef + {provider}.llm.xml 多模型配置)
    ↑ (直接依赖)

nop-ai-toolkit (工具 DSL, tool.xdef; 依赖 nop-ai-api + nop-xlang，不依赖 nop-ai-core)
    ↑
nop-ai-agent ← 本模块（依赖 nop-ai-toolkit + nop-ai-core）
```

**关键事实**：

1. `nop-ai-agent` 通过 `nop-ai-core` 调用 LLM。`nop-ai-core` 已包含完整的 LLM 调用能力：`llm.xdef` schema + `{provider}.llm.xml` 配置文件（ollama/deepseek/claude/gemini/azure/volcengine/bailian/lm-studio 等）+ `ChatServiceImpl` + `DefaultAiChatService`。
2. `nop-ai-toolkit` **不依赖** `nop-ai-core`。它只依赖 `nop-ai-api`（接口层）。两个模块独立发展。

---

## 3. 开发方法：审计-规划-执行循环

不在本文档中预设完整的工作项清单。采用动态规划模式：

1. 审计当前代码状态
2. 拟定一个 plan：基于审计结果，只规划**当前最紧迫的一个可交付单元**
3. 执行 plan：编码、测试、验证
4. plan 完成后，回到步骤 1

### 规划优先级指引

当有多个候选工作项时，按以下优先级排序：

1. **构建/测试失败** — 最高优先级
2. **主链路未通** — ReAct 循环是所有上层能力的基础
3. **接口定义缺失** — 无法 mock/test 的接口优先
4. **Pass-through 默认实现** — 需要存在才能跑通主链路
5. **功能实现** — 替换 pass-through 为实际功能
6. **测试覆盖** — 功能工作但缺少测试

### 前置阻塞项

Layer 1 之前必须先解决 §4 Layer 0 的 2 个阻塞项（L0-1 agent.register-model.xml、L0-2 枚举不一致）。L0-3（LLM 调用路径）已确认：通过 nop-ai-core 调用。详情见 §4 Layer 0 表格。

---

## 4. 按层组织的工作项清单

> 以下清单标注状态，但不预设执行顺序。实际执行由 goal driver 的路线图检查决定。
> 编号格式：L{层号}-{序号}，唯一编号，全文通用。

### 前置层 (Layer 0): 阻塞项修复

| # | 工作项 | 依赖 | 状态 |
|---|--------|------|------|
| L0-1 | 创建 `agent.register-model.xml` | 无 | ✅ |
| L0-2 | 统一枚举：解决 `AgentExecStatus` vs `AgentTaskStatus/AgentPlanStatus` 不一致 | 无 | ✅ |
| L0-3 | 🔑 **设计决策（已解决）**：Agent 通过 `nop-ai-core` 的 `ChatServiceImpl` / `DefaultAiChatService` 调用 LLM，基于 `{name}.llm.xml` 配置区分不同 Provider | 无 | ✅ 已确认 |

### Layer 1: Core Interfaces — 系统运行的最低要求

| # | 工作项 | 依赖 | 状态 |
|---|--------|------|------|
| L1-1 | `IAgentEngine` Actor 消息入口 + `DefaultAgentEngine` | L0-3 | ✅ |
| L1-2 | `AgentExecutionContext` 执行上下文数据对象 | 无 | ✅ |
| L1-3 | `IAgentExecutor` 执行策略接口定义 | 无 | ✅ |
| L1-4 | ~~`IMessageFormat` CanonicalMessage~~ → 直接使用 `ChatMessage`（nop-ai-api 已满足） | nop-ai-core (已满足) | ✅ 已确认无需额外工作 |
| L1-5 | `ReActExecutor` ReAct 循环核心实现 | L1-2, L1-3, L1-4 | ✅ |
| L1-6 | `IPermissionProvider` 三源合并权限派生 | agent.xdef permissions | ✅ |
| L1-7 | `IToolAccessChecker` 工具 deny/allow | L1-6 | ✅ |
| L1-8 | `IPathAccessChecker` 路径 deny/allow (glob + 规范化) | L1-6 | ✅ |
| L1-8a | `IAuditLogger` 审计日志接口 + `Slf4jAuditLogger` 默认实现 | L1-6 | ✅ |
| L1-8b | `IContentTrustEvaluator` 内容可信度评估 + `DefaultContentTrustEvaluator` | L1-6 | ✅ |
| L1-9 | `AgentEventPublisher` 事件流 | 无 | ✅ |
| L1-10 | `AgentSession` 基础会话对象 | 无 | ✅ |
| L1-11 | 缺失的枚举类（如 L0-2 确认需要单独创建）：`AgentTaskStatus`, `AgentPlanStatus`；已通过 L0-2 与 `AgentExecStatus` 统一，不再单独创建（`AgentExecStatus.java` 已被全模块使用，`AgentTaskStatus`/`AgentPlanStatus` 经 `find` 确认不存在） | L0-2 | ✅ |
| L1-12 | 端到端示例：一个 `.agent.xml` + ReAct 循环 + 工具调用 | L0-1, L1-5, L1-4 | ✅ |
| L1-13 | 基础单元测试框架搭建；已落地：205 test files / 1595+ tests 全绿（JUnit 5 + Nop AutoTest + focused test patterns，多份已关闭 plan closure 引证） | 无 | ✅ |
| L1-14 | `BaseAgent` 清理（决定保留并完善 or 删除） | L1-1 | ✅ |
| L1-15 | 🔴 ISessionStore 扩展：为 fork/event/compaction/snapshot 加 default 方法（抛 UOE） | L1-10 | ✅ |
| L1-16 | 🔴 IAiMemoryStore 扩展：加 update/remove/batchAdd/readBudgeted 方法 + AiMemoryItem 补充 priority/tokenEstimate/pinned/checksum | M6 | ✅ |
| L1-17 | 🔴 IAgentEngine 扩展：加 forkSession/getSessionStatus/cancelSession default 方法 | L1-1 | ✅ |
| L1-18 | 🔴 ReActAgentExecutor Builder 模式：移除构造器链，用 Builder 替换（见 react-engine.md §3.3） | L1-5 | ✅ |
| L1-19 | 🟡 agent.xdef 加 mode 属性（react/plan/single-turn，默认 react） | 无 | ✅ |
| L1-20 | 🟡 AgentSession 补充 parentSessionId/planId/compactedAt 字段（nullable，向下兼容） | L1-10 | ✅ |
| A1 | 🌟 Budgeted Injection functional consumption: AiMemoryItem priority/tokenEstimate/pinned 字段 + IAiMemoryStore.readBudgeted() default 方法（L1-16 ✅）+ InMemoryAiMemoryStore 功能化实现（plan 189 ✅）+ system-prompt 自动注入（buildBaseExecutionContext 每轮注入 budgeted memory，plan 192 ✅） | L1-16 | ✅ |
| A2 | 🌟 Completion Gate: ReAct 循环"无 tool calls"后加 Judge 验证点（contract: Plan 159; functional `RuleBasedCompletionJudge`: Plan 162; LLM `LlmCompletionJudge`: Plan 165） | L1-5 | ✅ |
| A3 | 🌟 PreStop/PostStop ReAct 钩子: before_tool_result_processed / after_tool_result_processed（允许重入）；已落地：`ReActAgentExecutor.java:1282,1345` 触发 + re-entry 语义（`reentryCounters`，`DEFAULT_MAX_REENTRIES=3`，`:1699-1702` 校验）+ `TestHookInReActLoop.java` 3 focused tests（before/after re-entry + 计数器强制 Pass） | L2-12 | ✅ |
| A4 | 🌟 Checkpoint Journal 格式: journal.md + snapshot.json 双文件，按 watermark 恢复（plan 182 已落地；crash/restart restore = plan 183 已落地；DB persistence = plan 186 已落地，实现为 `DBCheckpointManager`；LLM-turn/compaction triggers = plan 187 已落地，三个触发点全部 ✅；compaction-aware 截断加载 = plan 188 已落地，`firstKeptEntryId` 由 COMPACTION checkpoint 位置实现） | L3-4 | ✅ |
| A5 | 🌟 Actor Cancel 两级语义: graceful（完成当前 tool）/ forced（立即中断） | L1-17 | ✅ |
| A6 | 🌟 Session Fork: forkSession 功能化实现（InMemorySessionStore + DefaultAgentEngine，SESSION_FORKED 事件，inheritContext 语义） | L1-17, A5 | ✅ |

**Layer 1 验收标准**：

- [ ] 可以仅根据 xdef schema 写出合法的 `.agent.xml`
- [ ] `.agent.xml` 可通过 `agent.register-model.xml` 被正确加载为 `AgentModel`
- [ ] 一轮 Agent 执行可以描述为 "DSL → runtime 解释 → 工具调用 → 结果回灌" 的闭环
- [ ] `./mvnw test -pl nop-ai-agent -am -T 1C` 全部通过
- [ ] 至少 1 个端到端测试验证 ReAct 循环
- [x] L1-15 ~ L1-17 已确认 ISessionStore/IAiMemoryStore/IAgentEngine 接口 Phase 2 扩展点就绪（default UOE）
- [x] L1-18 ReActAgentExecutor 使用 Builder 模式，无新增构造器风险
- [x] L1-19 agent.xdef 已包含 mode 属性，DefaultAgentEngine 根据 mode 分发 executor

### Layer 2: Execution Extensions — 所有接口有 pass-through 默认

| # | 工作项 | 依赖 | 状态 |
|---|--------|------|------|
| L2-1 | `IToolCallRepairer` 接口 + `NoOpRepairer` pass-through | L1-5 | ✅ |
| L2-2 | `IToolCallRepairer` `ChainRepairer` (4-stage) | L2-1 | ✅ |
| L2-3 | `IContextCompactor` 接口 + `NoOpContextCompactor` | L1-5 | ✅ |
| L2-4 | `IContextCompactor` 渐进压缩初始版 (Layer 0 预截断 + Layer 1 微压缩) | L2-3 | ✅ |
| L2-7 | `IContentGuardrail` 接口 + `NoOpContentGuardrail` | L1-5 | ✅ |
| L2-8 | `ILlmDialect` 适配（已在 nop-ai-core 实现，Agent 层无需额外工作） | nop-ai-core | ✅ 已确认 |
| L2-9 | ~~Provider 适配 DashScope/OpenAI/Gemini/Ollama~~ → 已在 nop-ai-core ILlmDialect 实现 | L2-8 | ✅ 已确认 |
| L2-10 | `IModelRouter` 接口 + `PassThroughModelRouter` | L1-5 | ✅ |
| L2-11 | `ITalent` 动态准入扩展点 | L1-5 | ✅ |
| L2-12 | `IAgentLifecycleHook` 10 点生命周期 | L1-5 | ✅ |
| L2-13 | `ISecurityLevelResolver` 接口 + `NoOpSecurityLevelResolver` | L1-6 | ✅ |
| L2-13a | ✅ `IConflictStrategy` 冲突解决策略接口 + `FailFastStrategy` 默认实现 + `InMemoryWriteIntentRegistry` 写意图注册表 + dispatch-path 冲突检测接线（plan 214） | L1-1 | ✅ |
| L2-14 | `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` | L1-6 | ✅ |
| L2-14a | Dispatch-path 咨询集成（L2-13/L2-14 → ReAct 分发路径：`ILevelHintsProducer` + channel/principal 传播 + `checkLayer2Consultation`） | L2-13, L2-14 | ✅ |
| L2-15 | Working Memory 工具实现 (read-memory/write-memory/search-memory) | L1-10, L1-5 | ✅ |
| L2-16 | Token 计数 — `ILlmDialect.estimateTokens()` (default chars/4) + Provider usage 校准 | L1-4, nop-ai-core | ✅ |
| L2-17 | ✅ `IUsageRecorder` 接口 + `NoOpUsageRecorder` pass-through + `UsageRecord` 数据对象（plan 201） | L1-5 | ✅ |
| L2-18 | ✅ `DbUsageRecorder` 实现：ReAct 循环 token 累加点写 `NopAiChatResponse`（modelId + promptTokens + completionTokens + durationMs） | L2-17 | ✅（plan 202） |
| L2-19 | ✅ `NopAiModel` 加定价列（`input_price_per_1m`/`output_price_per_1m`/`reasoning_price_per_1m`/`cache_read_price_per_1m`/`cache_write_price_per_1m`/`currency`）+ `summarizeByModel` 的 `estimatedCost` 计算启用 | 无 | ✅（plan 204） |
| L2-20 | ✅ per-model 聚合查询：`NopAiChatResponseBizModel.summarizeByModel(sessionId)` → SQL GROUP BY model_id（+ ai_provider + ai_model） | L2-18 | ✅（plan 203） |
| L2-21 | ✅ `model-switched` 消息产生：ReAct 循环中 IModelRouter 返回后检查模型变更 → 写 NopAiSessionMessage(role=80) | L2-10 | ✅（plan 205） |
| L2-22 | ✅ 预算控制 hook：`IBudgetProvider` 扩展点 + `BudgetSnapshot` + `NoOpBudgetProvider` 默认 + ReAct 循环每轮 `IModelRouter.route()` 前刷新预算快照，router 可基于预算降级模型 | L2-20, L2-10 | ✅（plan 206） |
| L2-23 | ✅ `SmartModelRouter` 功能性路由器：启发式复杂度分类（simple/medium/complex）→ tier 路由 + 预算感知降级 + `RetryDecision.FALLBACK` 回退链消费（`IModelRouter.getFallback`）；`PassThroughModelRouter` 仍为 shipped 默认 | L2-10, L2-22, L3-2 | ✅（plan 209） |

**Layer 2 验收标准**：

- [ ] 能清楚说明每个扩展如何替换 pass-through 默认
- [ ] 不把运行时假设误写成新的 DSL 字段
- [ ] ContextGovernor Pipeline 可通过 Delta 配置启用
- [x] L2-17~L2-18：每次 LLM 调用产生一行 `NopAiChatResponse`，含 model_id + tokens
  - L2-17 ✅（plan 201）：`IUsageRecorder` 接口 + `NoOpUsageRecorder` pass-through + `UsageRecord` 已接线到 ReAct 循环 token 累积点（`record()` 每次调用被调用）
  - L2-18 ✅（plan 202）：`DbUsageRecorder` 写 `NopAiChatResponse` 持久化已实现（raw JDBC，不依赖 nop-ai-dao；modelId 按 provider+model_name 解析；responseDurationMs 在 ReActAgentExecutor 计量）
- [x] L2-20：SQL GROUP BY model_id 可查到 session 级 per-model token 聚合
- [x] L2-21：模型切换时产生 `model-switched` 消息（role=80）
- [x] L2-22：`IBudgetProvider` 扩展点可在每轮路由前提供预算快照，功能性 router 可基于预算超限降级模型（NoOp 默认零变化）
  - L2-22 ✅（plan 206）：`IBudgetProvider` 接口 + `BudgetSnapshot`（estimatedTotalCost/totalTokensUsed/budgetLimit/exceeded）+ `NoOpBudgetProvider` 默认已接线到 ReAct 循环 route() 调用前；端到端测试验证预算超限时 router 降级模型
- [x] L2-23：功能性 router 按复杂度路由 + 预算降级 + 回退链消费（PassThrough 默认零变化）
  - L2-23 ✅（plan 209）：`SmartModelRouter`（启发式分类 simple/medium/complex + tier 路由 + `BudgetSnapshot.exceeded` 降级 + 有意义的 routingReason）+ `IModelRouter.getFallback` default 方法 + ReAct 重试循环 `RetryDecision.FALLBACK` 分支消费回退链（有回退则切换模型重试 + attempt 重置 + usage 归属回退模型，无回退则 fail-loud）；端到端测试覆盖 `DefaultAgentEngine` → ReAct → SmartModelRouter → LLM 失败 → FALLBACK → 回退模型 → 成功

### Layer 3: Reliability Extensions — 生产环境加固

| # | 工作项 | 依赖 | 状态 |
|---|--------|------|------|
| L3-1 | `ICircuitBreaker` 接口 + `AlwaysClosed` 默认 + `ThresholdBreaker` | L1-5 | ✅ |
| L3-1b | 熔断感知路由解析（circuit-aware routing）：主模型 circuit OPEN 时主动沿 `IModelRouter.getFallback(...)` 回退链查找 circuit-closed 健康模型续跑（route() 后、model-switched 审计检测前的 post-processing 解析步骤），全部不可用 fail-fast；plan 210 的 retry 循环外层 circuit check 转为 safety-net | L2-10, L2-23, L3-1 | ✅（plan 213） |
| L3-2 | `IRetryPolicy` 接口 + `NoRetry` 默认 + `StandardRetryPolicy` | L1-5 | ✅ |
| L3-3 | `IGoalTracker` 接口 + `NoOpGoalTracker` + `SessionGoalTracker` | L1-10 | ✅ |
| L3-4 | `ICheckpointManager` 接口 + `NoOpCheckpoint` + `ToolExecutionCheckpoint` | L1-10 | ✅ |
| L3-4b | 🔴 Crash/restart durable session restore: `FileBackedSessionStore` (per-session JSON) + `ISessionStore.save` contract bridge + `IAgentEngine.restoreSession` + `SESSION_RESTORED` event + intra-execution persistence + checkpoint journal 消费（`getLatestCheckpoint` 一致性校验） — 单进程 crash/restart restore（plan 183 已落地；跨进程接管锁依赖 L4-8） | L3-4, A4 | ✅ |
| L3-4c | 🔴 Auto restore-on-startup: `ISessionStore.listAllSessions()` 磁盘发现契约（不改 `getAll()` 语义）+ `IAgentEngine.restorePendingSessions(approver, reason)` 批量入口（发现 → 筛选 running/pending 候选 → 逐个 restoreSession → 摘要）+ `SessionRestoreSummary` — 进程重启后自动扫描+批量恢复，补齐"无人值守自动化"最后一块（plan 184 已落地；并行/限流恢复 + 定时扫描 = deferred successors） | L3-4b | ✅ |
| L3-4d | 🔴 DB-backed session store: `DBSessionStore implements ISessionStore`（`ai_agent_session` 表，raw JDBC + MERGE INTO upsert + 混合列布局 + SESSION_DATA JSON CLOB 复用 SessionFileWriter/Reader 序列化）+ 跨实例存活 + `listAllSessions` SQL-based discovery — 任何共享 DB 的服务实例可接管恢复（plan 185 已落地；跨进程接管锁依赖 L4-8） | L3-4b | ✅ |
| L3-5 | `IApprovalGate` 接口 + `AutoApproveGate` | L1-6 | ✅ |
| L3-6 | `IDenialLedger` 接口 + `NoOpDenialLedger` + `DBDenialLedger` + sticky-pause 恢复协议（`IAgentEngine.resumeSession`） | L1-6 | ✅ |
| L3-7 | `IPostDenialGuard` 接口 + `PassThroughPostDenialGuard` + `FingerprintPostDenialGuard` | L3-6 | ✅ |
| L3-8 | `ISustainer` 接口 + `NoOpSustainer` + `SisypheanSustainer` | 与 L3-1 互斥（设计决策：选熔断或自愈；plan 212 裁定为部署层文档约束，非运行时 guard） | ✅ |
| L3-9 | `IContextCompactor` 完整 5 层管道 + `ICompressionStrategy` 扩展点 | L2-4, L2-16 | ✅ |

**Layer 3 验收标准**：

- [x] LLM 调用故障可以区分是否自动重试（L3-2 ✅ plan 207：`IRetryPolicy` + `LlmErrorClassifier` 把 429/5xx/超时/4xx 映射为 `ErrorClassification`，`StandardRetryPolicy` 仅对 TRANSIENT/RATE_LIMITED 重试）
- [x] 连续故障后系统可自动熔断（L3-1 ✅ plan 210：`ICircuitBreaker` + `CircuitState`(CLOSED/OPEN/HALF_OPEN) + `AlwaysClosed` 默认（恒放行零回归）+ `ThresholdBreaker`（per-model-key 连续失败阈值→OPEN、冷却→HALF_OPEN lazy、probe 成功→CLOSED/失败→OPEN）；熔断检查接线到 ReAct retry 循环外层，OPEN 抛 `NopAiAgentException` 拒绝调用）。熔断后自动降级到健康备选模型（L3-1b ✅ plan 213：circuit-aware routing 解析步骤在 `route()` 返回后、model-switched 审计检测前主动沿 `IModelRouter.getFallback(...)` 回退链查找 circuit-closed 健康模型续跑，全部不可用 fail-fast，plan 210 外层 circuit check 转为并发竞争 safety-net）
- [x] agent 行为故障（循环调用同一工具/持续无效动作）可被检测并提前 escalate（L3-3 ✅ plan 211：`IGoalTracker`（`recordIteration` 写侧 + `assessGoal` 读侧，读写分离对称 ICircuitBreaker）+ `GoalAssessment`(PROGRESSING/STUCK/GOAL_ACHIEVED) + `IterationSnapshot` reliability-local 数据载体 + `NoOpGoalTracker` 默认（恒 PROGRESSING 零回归）+ `SessionGoalTracker`（per-session 滑动窗口 tool-call 签名重复达阈值→STUCK，签名 = toolName:sortedArgsJson）；接线到 ReAct 循环 per-iteration 边界——recordIteration 在 LLM 响应后 if(!hasToolCalls) 前、assessGoal 在 forceStop 后 PRE_REASONING 前，STUCK→escalated status + break）
- [ ] 长对话能触发压缩并继续运行
- [ ] 工具调用可在执行前被验证和拦截

### Layer 4: Platform Extensions — 多 Agent / 多租户 / 分布式

| # | 工作项 | 依赖 | 状态 |
|---|--------|------|------|
| L4-1 | `IMessageService` `LocalMessageService` (内存队列) | L1-1 | ✅ |
| L4-1b | `call-agent` 工具（fork+exec via `IAgentEngine.execute()`）+ `send-message` 工具（fire-and-forget via `IAgentMessenger.send()`）+ 工具上下文增强（`AgentToolExecuteContext`） | L4-1, L1-1 | ✅ |
| L4-1c (sec-4.4) | Sub-agent permission inheritance enforcement: `ParentPermissionConstraint` + `ParentConstrainedToolAccessChecker` — 子 Agent 工具权限 = 父权限 ∩ 子配置（fail-closed，nested delegation clamped-set 传播） | L4-1b, L1-7 | ✅ |
| L4-1d (sec-4.4) | Sub-agent path-permission inheritance: `ParentPermissionConstraint` (extended with `allowedPathRoots`) + `ParentConstrainedPathAccessChecker` + `AgentModel.workDir` (DSL) — 子 Agent 文件权限 = 父权限 ∩ 子配置（fail-closed，nested delegation clamped-root 传播，workDir-derived scope source） | L4-1c, L1-8 | ✅ |
| L4-1e (sec-4.3) | Per-agent glob path-rules: `PathRuleModel` (DSL `<path-rules>`) + `PathAccessDecision` enum + `RuleBasedPathAccessChecker` (first-match-wins) + `ParentPermissionConstraint` (extended with `allowedPathRules`) + `ParentConstrainedPathAccessChecker` (cross-level deny-wins) — per-agent glob allow/deny 规则评估 + 跨委派层 deny-wins 继承 | L4-1d, L1-8 | ✅ |
| L4-2 | `IMessageService` `DBMessageService` (跨进程路由) | L4-1, nop-dao | ✅ |
| L4-3 | `IMemoryAdapter` 三适配器 (Storage / Embedding / Vector) | L2-15 | ✅ |
| L4-4 | `ISkillCurator` `LLMCurator` (技能生命周期) | L2-11 | ✅ |
| L4-5 | `IMailbox` `DeferredAckMailbox` (3-phase reservation) | L1-1 | ✅ |
| L4-6 | `IContributionRegistry` 7 贡献类型 | L2-12 | ✅ |
| L4-7 | `ISandboxBackend` `DockerSandboxBackend` | L1-8 | ✅ |
| L4-8 | Actor Runtime 平台层 | L4-1 ~ L4-6 | ✅ |
| L4-8a | call-agent 异步 mailbox 请求-响应模型（plan 224）：`CallAgentExecutor` async gate（`instanceof NoOpAgentMessenger`）+ `CallAgentRequestPayload`/`CallAgentResponsePayload` 不可变载荷 + 引擎级 `agent.call-agent` topic handler（`setMessenger` idempotent 注册，handler `engine.execute().orTimeout().join()` try/catch 返回 failure RESPONSE）+ shipped 默认 NoOp 保留 fork+exec 零回归 | L4-8, L4-1 | ✅ |
| L4-8-team-tools | 团队通信工具 foundational（plan 225）：`team-send-message` / `team-status` / `team-task-create` 三个 IToolExecutor + `ITeamTaskStore` 契约 + `NoOpTeamTaskStore` shipped 默认 + `InMemoryTeamTaskStore` 功能实现（ConcurrentHashMap 双索引）+ `TeamTask`/`TeamTaskStatus` 数据对象 + `AgentToolExecuteContext` `teamManager`/`teamTaskStore` 接线 + `ReActAgentExecutor.Builder`/`DefaultAgentEngine.resolveExecutor` 传递 + NoOp 诚实报告（No Silent No-Op） | L4-8 | ✅ |
| L4-8-team-task-update | 任务认领状态机 + DB-backed 共享任务表（plan 227）：`team-task-update` IToolExecutor（claim/complete/abandon 三动作，大小写不敏感）+ `ITeamTaskStore.claimTask/completeTask/abandonTask` 状态转换契约（返回 Optional，CAS-empty 非异常控制流）+ `TeamTaskStatus` 状态机（CREATED→CLAIMED→COMPLETED、CREATED/CLAIMED→ABANDONED）+ `TeamTask.claimedBy` 字段（claim 写入，complete/abandon 保留）+ `InMemoryTeamTaskStore` compute CAS + `NoOpTeamTaskStore` 转换抛 UOE + `DbTeamTaskStore` raw JDBC（`AiAgentTeamTaskTable` + 构造期 initSchema 自动建表 + 条件 UPDATE on STATUS affected-row-count CAS 认领，跨进程共享）+ 39 focused/E2E 测试全绿（含跨 store 实例 CAS 竞争） | L4-8 | ✅ |
| L4-8-P4-RecoveryStrategy | 恢复模式策略 RESUME/ABORT/SKIP（plan 226）：`IOrphanRecoveryHandler` 可插拔策略契约 + `RecoveryMode` 枚举 + `RecoveryOutcome` 数据对象 + `NoOpOrphanRecoveryHandler` shipped 默认（SKIP，零回归）+ `DefaultOrphanRecoveryHandler` 功能实现（RESUME 委托 restoreSession fire-and-forget / ABORT raw JDBC UPDATE status=failed / SKIP LOG.warn）+ `ScheduledRecoveryManager` 集成（`setOrphanRecoveryHandler` setter + scanOnce handler 调用 + `RecoveryScanResult.recoveryActions`）+ 33 focused/E2E 测试全绿 | L4-8 | ✅ |
| L4-team-acl-enforcement | Team ACL 强制 — 角色权限矩阵 + ITeamAclChecker 拦截层（plan 228）：`ITeamAclChecker` 契约（`checkAccess(teamId, callerSessionId, toolName, action) → TeamAclDecision`）+ `TeamAclAction` 枚举（READ/WRITE/EXECUTE/ADMIN，vision §5.1 AclAction 子集）+ `TeamAclDecision` 不可变数据对象（allow/deny 工厂 + reason + resolvedRole）+ `NoOpTeamAclChecker` shipped 默认（恒 `allow(null)` = 不增加授权限制，零回归）+ `DefaultTeamAclChecker` 功能实现（§5.1 默认矩阵：LEAD=ADMIN 全通过 / MEMBER 通过 READ+WRITE+EXECUTE，**唯一 MEMBER 拒绝操作** = `team-task-update` abandon-unclaimed 即放弃 CREATED 未认领任务 required ADMIN）+ 引擎全链路接线（`AgentToolExecuteContext.teamAclChecker` final 字段 + 17 参 endpoint 构造器，`DefaultAgentEngine.teamAclChecker` 字段 + `setTeamAclChecker` null-safe + `resolveExecutor` Builder 传递，`ReActAgentExecutor` 字段→构造→Builder→context 构造全链路）+ 4 团队工具 executor 均在团队解析后、实际操作前调用 checker（denial 返回诚实策略反馈 status="success" + JSON body，不中断 ReAct 循环；ACL denial 实际阻止 store/messenger 操作经 e2e 测试断言）+ 26 focused/E2E 测试全绿（含 MEMBER abandon-unclaimed 被拒绝 + 任务 status 未变 Anti-Hollow 断言） | L4-8 | ✅ |
| L4-8-P4-TimeoutAbort | 超时强制中止（plan 229）：`ISessionTimeoutHandler` 可插拔策略契约（`handleTimeout(sessionId) → TimeoutOutcome`）+ `TimeoutAction` 枚举（LOCAL_CANCELLED/FORCE_FAILED/SKIPPED_REMOTE/SKIPPED）+ `TimeoutOutcome` 数据对象（sessionId/action/succeeded/message）+ `NoOpSessionTimeoutHandler` shipped 默认（SKIPPED，零回归）+ `DefaultSessionTimeoutHandler` 功能实现（三分裁定经 raw JDBC `SELECT LOCK_OWNER, LOCK_EXPIRES_AT FROM ai_agent_session_lock` 直读锁表——不注入 `ISessionTakeoverLock`：其 `isHeld` 不区分持有者；LOCAL_CANCELLED 本实例锁 → `engine.cancelSession(sessionId, "timeout", true)` forced=true / FORCE_FAILED 无活跃锁 → raw JDBC `UPDATE ai_agent_session SET STATUS='failed' WHERE SESSION_ID=? AND STATUS IN ('running','pending')` 条件 WHERE / SKIPPED_REMOTE 远端锁 → LOG.warn 不干预）+ `ScheduledRecoveryManager` 集成（`sessionTimeoutHandler` 字段默认 NoOp + `setSessionTimeoutHandler` setter + `timeoutSeconds` 字段默认 30min + `setTimeoutSeconds` + scanOnce 步骤顺序重排：stale lock cleanup → **timeout detection**（`UPDATED_AT < now - timeoutSeconds*1000`）→ orphan detection，timeout 先于 orphan 使 terminal 化的 session 被后续 orphan detection 排除）+ `RecoveryScanResult` 扩展（`timeoutActions: List<TimeoutOutcome>` + 7-arg 构造器 + `getTimeoutActions()` + `empty()` 适配）+ 22 focused/E2E 测试全绿（含 timeout-before-orphan 顺序无冲突 + LOCAL_CANCELLED daemon 接线） | L4-8 | ✅ |
| L4-team-db-persistence | DB-backed 团队持久化（plan 230）：`DbTeamManager` raw JDBC 功能实现（implements `ITeamManager` 全部 9 方法，drop-in 经 `setTeamManager` 注入，无引擎/context 代码变更）+ `AiAgentTeamTable`（`ai_agent_team` 表 DDL + 列常量，PK TEAM_ID）+ `AiAgentTeamMemberTable`（`ai_agent_team_member` 表 DDL + 列常量，唯一约束 (TEAM_ID, MEMBER_NAME)）+ 构造期 `initSchema` 自动建两张表（镜像 `DbTeamTaskStore` 模式）+ 条件 UPDATE CAS（`bindMemberSession` 首次绑定 CREATED→ACTIVE exactly-once 激活 + `disbandTeam` *→DISBANDED 幂等 + `addMember` 唯一约束 duplicate 检测）+ 快照重建读语义（每次 getTeam/getTeamBySession/getActiveTeams/getMember SELECT 团队行+成员行重建新鲜 `Team`，区别于 `InMemoryTeamManager` live 可变对象，符合 ITeamManager 契约 returned members read-only）+ `getTeamBySession` 经 SELECT member 表 SESSION_ID 反查（无内存反查索引）+ 跨进程共享语义（多 JVM 实例指向同一 DB 即团队/成员/绑定/disband 互相可见）+ `NoOpTeamManager` shipped 默认零回归 + 24 focused/E2E 测试全绿（含跨实例共享 + 并发激活 exactly-once + drop-in ACL/team-status 接线） | L4-8 | ✅ |
| L4-team-auto-binding | 声明式团队自动绑定（plan 231）：lead agent `.agent.xml` 可选 `<team>` 元素 + 成员 agent 可选 `<team-member>` 元素嵌入 `agent.xdef`（codegen 生成 mutable `TeamModel`/`TeamMemberModel`/`TeamMemberRefModel`）+ `TeamModelConverter`（mutable→不可变 `TeamSpec`/`TeamMemberSpec`，保证 leadAgentName 以 role=LEAD 进花名册）+ 引擎三入口点（doExecute/resumeSession/restoreSession）auto-bind（同步 NoOp+声明 fail-fast 预检 + 异步块 createActor 后幂等 createTeam + bindMemberSession，actorId 取 Actor 或回退 sessionId）+ member 经 getActiveTeams() 按 teamName+ACTIVE 过滤解析 teamId + bindMemberSession false-return/未找到 ACTIVE 团队 fail-fast（No Silent No-Op）+ Delta 定制经既有 xdsl-loader 天然支持 + 无声明路径零回归 + 28 focused/E2E 测试全绿（含 team-status 工具透明消费声明式团队 Anti-Hollow + Delta 覆盖） | L4-8 | ✅ |
| L4-nop-task-dag-integration | nop-task DAG 集成（plan 233）：团队任务 → nop-task 工作流 DAG 图节点 + 依赖序同步编排。`nop-ai-agent` 新增 `nop-task-core` 单向编译依赖（无环）。`TeamTaskGraphBuilder` 从团队任务集构造 nop-task 内存 `GraphTaskStepModel`（每任务一节点，`blockedBy`→`waitSteps`，enter/exit 推导），**真实经 nop-task `GraphStepAnalyzer` 环检测**（成环 blockedBy 快速失败，闭合「今日静默存储成环」gap）。`TeamTaskTopology` 暴露就绪/阻塞拓扑查询（基于图拓扑 + 任务 status）。`TeamTaskFlowOrchestrator` 依赖序同步编排器——真实经 nop-task 运行时（`ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()`）执行 `GraphTaskStep` DAG 调度，每节点委派已绑定成员 agent（`IAgentEngine.execute` 同步 join，裁定4 消费已绑定成员不 spawn），成功后 `completeTask` 标记 COMPLETED；合成 sole-exit sink 节点保证多自然 sink 下所有任务都执行；失败传播（节点失败→后继不执行→诚实失败结果，非静默成功）。未绑定成员/空任务/未知团队/成环均快速失败（No Silent No-Op #24）。编排器只读消费既有 `IAgentEngine`/`ITeamTaskStore`/`ITeamManager`，不改其契约。设计文档 `nop-ai-agent-task-flow-integration.md`。blockedBy 自动调度守护 / auto-spawn / 异步跨进程 / LLM 直面工具 / decorator 接入 / 动态改图 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-resource-guard-quota | ResourceGuard + 配额强制（plan 234）：中央 `IResourceGuard` 配额决策网关契约（`checkConcurrent(QuotaDimension, scopeKey, projectedCount, overrideLimit) → QuotaDecision`，override `> 0` 优先 / `<= 0` 回退 `QuotaConfig` 全局默认，resolved limit `<= 0` = unlimited allow）+ `QuotaDimension` 枚举（三值 `TEAM_PARALLEL_BOUND_MEMBERS` / `TEAM_MEMBERS` / `CONCURRENT_ACTORS_PER_TENANT`）+ `QuotaDecision` 不可变结果对象（allow/deny 工厂 + dimension/scopeKey/limit/projectedCount/reason）+ `QuotaConfig` 不可变配置对象（teamMaxMembers 默认 8 / tenantMaxConcurrentActors 默认 10）+ `NoOpResourceGuard` shipped 默认（恒 allow = 零回归）+ `DefaultResourceGuard` 功能实现。三维度 enforcement 接线：`InMemoryTeamManager`/`DbTeamManager` 的 `createTeam`/`addMember`（TEAM_MEMBERS config-driven）+ `bindMemberSession`（TEAM_PARALLEL_BOUND_MEMBERS per-team override `maxParallelMembers` hint→enforced 升级，消费既有持久化字段）+ `InMemoryActorRuntime.createActor`（CONCURRENT_ACTORS_PER_TENANT，scopeKey = `ITenantResolver` 解析的 tenant，经 registry tenant 标签派生活跃 Actor 计数，null tenant = 全局单桶）。denial = fail-fast 抛 `NopAiAgentException`。36 focused/E2E 测试全绿（含 NoOp 零回归 + 三维度 denial + 接线验证 spy guard + per-tenant scope 独立 + engine.execute 端到端 maxParallelMembers denial）。LLM rate-limit / Compaction 配额池 / storage 配额 / per-agent token/时间 / Fencing Token / 协调信道 / DB 持久化配额计数器 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-fencing-token | Fencing Token 并发写入防护原语（plan 235）：`FencingToken` 不可变数据对象（actorId + monotonicCounter + issuedAt，vision §5.1 lines 252-267 + glossary line 65）+ `IFencingTokenService` 中央单调计数器决策网关契约（`issue(actorId) → FencingToken` 原子递增单调计数器首次=1；`validate(token) → FencingTokenDecision` strictly-greater 校验 + 高水位更新，vision §10 line 462 引擎层依赖此接口）+ `FencingTokenDecision` 不可变结果对象（valid/stale + actorId + presentedCounter + recordedCounter + reason，valid⇒null-reason / stale⇒non-null-reason；stale reason="fencing token stale: presented X <= recorded Y"）+ `NoOpFencingTokenService` shipped 默认（singleton，validate 恒 valid = 零回归；issue 返回 placeholder counter=0 disabled-mode）+ `DefaultFencingTokenService` functional in-memory CAS 实现（per-actor 双计数器：issue 计数器 `AtomicLong.incrementAndGet` + recorded 高水位 CAS loop only-if-greater 无 lost update）。validate 返回 decision 非 throw（enforcement-point 反应为 consumer 责任，与 `IResourceGuard`/`ITeamAclChecker` 一致）。in-memory CAS 防护同 JVM 并发（vision §2.3 单 JVM 基线）。无 engine 顶层接线（无 wired consumer，setter 预留）。22 focused 测试全绿（contract / 高水位单调不回退 / 并发 issue 100 线程唯一计数 / 并发 validate 不回退 / 原语生命周期 issue→valid→issue→stale / NoOp 零回归）。scope_claim 协调信道集成 / conflict_alert 广播 / Compaction·快照写入集成（§6.2）/ DB-backed 跨进程 CAS（line 267）/ Actor 恢复后重获取 token（规则 4）/ 分支亲和调度注册集成 / engine 顶层接线 / ResourceGuard fencing 校验集成 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-blockedBy-resolution-engine | blockedBy 自动调度守护进程（plan 236）：`ITeamTaskSchedulerDaemon` 守护进程契约（`start`/`stop`/`scanOnce`，镜像 plan 222 `IRecoveryManager` 范式——deployment-layer lifecycle，引擎不调 start/stop）+ `NoOpTeamTaskSchedulerDaemon` shipped 默认（start/stop no-op + scanOnce 全零 `SchedulerScanResult` = 零回归）+ `SchedulerScanResult` 不可变快照 + `TeamTaskSchedulerDaemon` functional 实现。复用 `ScheduledRecoveryManager` 生命周期范式（`synchronized` 幂等 start/stop + `IScheduledExecutor.scheduleWithFixedDelay` + `volatile Future` 句柄 + graceful `cancel(false)`）。每周期 `scanOnce`：经 plan 233 `TeamTaskTopology.getReadyTasks()` 解析就绪集 + 过滤至 `status==CREATED`（CLAIMED 他人任务跳过不 claim 不 abandon——关键安全约束）+ `ITeamTaskStore.claimTask` CAS 认领（idempotent，CAS 失败=合法并发 claimLost 静默跳过）+ `ITeamManager` 成员解析 + `IAgentEngine.execute` 同步委派（与 plan 233 `MemberAgentTaskStep` 同一委派机制，**不复用 `TeamTaskFlowOrchestrator.execute(teamId)`**）+ 成功 `completeTask` / 失败仅对自认领任务 `abandonTask`（不静默跳过 #24，per-task failure isolation）。依赖序自动保证 = 经就绪查询天然实现（blockedBy 未完成永不在就绪集，依赖完成后下周期自动就绪，无运行时阻塞）。target team 范围可配置（全量 vs 指定 teamId 集合）。`DefaultAgentEngine.teamTaskSchedulerDaemon` 字段 + `setTeamTaskSchedulerDaemon`/`getTeamTaskSchedulerDaemon`（null-safe，部署层管理 start/stop）。24 focused/E2E 测试全绿（含线性 A→B→C 无人值守 Anti-Hollow 执行序 + 菱形 A→{B,C}→D + 生命周期 stop 后新任务不派发 + CLAIMED 他人任务不被误弃 + CAS idempotent + 未绑定成员 abandon No Silent No-Op + 接线验证 engine.execute 实际调用 + store 状态实际突变）。设计文档 `nop-ai-agent-task-scheduler-daemon.md`。auto-spawn / 异步跨进程 / LLM 直面工具 / task-reclaim / 超时自动 abandon / decorator 接入 / 并行委派 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-auto-spawn-member-agent | auto-spawn 成员 agent（plan 237）：`IMemberSpawner` 可插拔成员 spawn 扩展点契约（`spawnMember(SpawnMemberRequest) → SpawnMemberResult`，三态 DISPATCHED/NO_SPAWN/SPAWN_FAILED 显式 honest 语义，杜绝静默跳过 #24）+ `SpawnMemberRequest`/`SpawnMemberResult` 不可变输入/输出（team 快照 + task + daemonSessionId / Status + executionResult + spawnedAgentName + spawnedSessionId + reason）+ `NoOpMemberSpawner` shipped 默认（singleton，恒 `NO_SPAWN` = daemon 走 abandon 零回归，与 `NoOpTeamAclChecker`/`NoOpResourceGuard` 一致）+ `DefaultMemberSpawner` functional 实现（构造注入 `IAgentEngine`；spawn 目标解析 = 团队级单一成员策略优先 MEMBER role 回退任意 memberSpec，与 daemon `resolveBoundMember` 对称；spawn 执行 = 创建 `AgentMessageRequest`（agentModel 来自 memberSpec + task subject/description 作 prompt + 新 session `"spawned-"+UUID` per-task 非复用）+ 元数据 teamTaskId/teamId/daemonSessionId/spawnedFromMemberSpec + `IAgentEngine.execute().join()` 同步执行（与 bound-member dispatch 同一委派语义）；honest 失败映射 = 无 memberSpec → NO_SPAWN / execution 抛异常 → SPAWN_FAILED / 否则 DISPATCHED 携带 `AgentExecutionResult`（spawner 不解释 status，daemon 决定 complete/abandon，决策4 统一 bound 和 spawned 路径））+ `TeamTaskSchedulerDaemon` dispatch 路径集成（`resolveBoundMember` 返回 null 时咨询 spawner，bound-member 优先决策3）+ daemon spawner setter/构造器注入（null-safe → NoOp shipped 默认，wire-at-consumer 决策5 镜像 `IResourceGuard`→TeamManager，不经 `DefaultAgentEngine` 中转）+ 提取 `completeOrAbandonAfterExecution` 共享 bound/spawned 路径 complete/abandon 逻辑（决策4 字面同一段代码）。37 focused/E2E 测试全绿（含 13 spawner focused + 18 daemon 集成 focused + 6 端到端无人值守 spawn：线性 A→B→C + 菱形 A→{B,C}→D 自动 spawn 完成 Anti-Hollow 执行序 + 无任何手动 `bindMemberSession` + NoOp 零回归对比 abandon + bound-priority spawner 不被调用 + spawner 抛异常 contract violation 防御性 abandon + Anti-Hollow agentModel 来自 `TeamMemberSpec` 非硬编码 + 每次 spawn 新 session per-task 非复用）。设计文档 `nop-ai-agent-member-auto-spawn.md`。orchestrator auto-spawn 集成 / LLM 直面编排工具 / spawn session 复用池化 / 多成员 per-task 路由 / 异步跨进程 spawn / 重试超时 decorator 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-orchestrator-auto-spawn-integration | 程序化编排器 auto-spawn 集成（plan 238）：`TeamTaskFlowOrchestrator`（plan 233）从「每个 team task 节点必须解析到**已绑定**成员 session，否则 `resolveMember` 在图**构建期** fail-fast 抛 `nop.ai.team.flow.no-bound-member`」扩展为「无已绑定成员的节点在**图运行期**（DAG 调度器触发时）经 plan 237 `IMemberSpawner` spawn 执行」。核心约束（决策1）：spawn 必须在节点运行期而非构建期（`resolveMember` 构建期运行先于图运行，构建期 spawn 会破坏 DAG 依赖序）——实现：`resolveMember` 无 bound member 分支从 fail-fast 改为**返回 null**（构建期不执行任何 agent，bound-member 解析路径逐行不变决策2 零回归），构建 for-loop 据 null 选择新 step 类 `SpawnMemberAgentTaskStep`（节点运行期 claim→`spawnMember`→三态解释→complete/throw，失败留 CLAIMED 非 abandon 决策3，与 `MemberAgentTaskStep` 失败模型一致），真正的 spawn 执行延迟到节点运行期。复用 `IMemberSpawner` 契约原样不改接口（决策3，共享 SpawnMemberResult 三态解释）。`TeamTaskFlowOrchestrator` spawner 接线：`memberSpawner` 字段默认 `NoOpMemberSpawner.noOp()`（零回归）+ `setMemberSpawner`/`getMemberSpawner`（null-safe 回退 NoOp）+ spawner-aware 5-arg 构造器（wire-at-consumer 决策5，镜像 daemon 接线惯例不经 engine 中转）+ `orchestratorSessionId = "orchestrator-"+teamId`（claim/complete session + spawn 审计元数据）。有意 API 契约变更（决策4 显式纳入 scope）：无 bound member 失败形态从 pre-238 构建期 `execute()` 抛 `NopAiAgentException` 变为运行期 `execute()` 返回 `TeamTaskFlowResult{success=false}`（业务语义不变 throw→return-failed-result），`noBoundMemberFailsFast` 测试相应改写（仍断言失败语义 + 留 CLAIMED）。12 focused/E2E 测试全绿（含 9 focused：bound-priority spawner 不被调用 / 无 bound+functional 运行期 spawn 执行 / **运行期非构建期 spawn 证据**（反向插入序 [C,B,A] 但 spawn 序按依赖序 [A,B,C] + B spawn 严格晚于 A 完成）/ NoOp 诚实 fail 留 CLAIMED / DISPATCHED 非 completed 诚实 fail / SPAWN_FAILED 诚实 fail / spawner 抛异常诚实 fail / 接线验证构造器+setter / Anti-Hollow agentModel 来自 `TeamMemberSpec` 非硬编码 + 3 E2E：线性 A→B→C + 菱形 A→{B,C}→D 多节点依赖 DAG 自动 spawn 完成 **全程无任何手动 `bindMemberSession`** Anti-Hollow 执行序 + NoOp 零回归对比 fail + bound-priority e2e spawner 不被调用）。设计文档 `nop-ai-agent-orchestrator-auto-spawn.md`。LLM 直面编排工具 / spawn session 复用池化 / 多成员 per-task 路由 / 异步跨进程 spawn / 重试超时 decorator 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-team-execute-flow-llm-tool | LLM 直面编排工具（plan 239）：`team-execute-flow` IToolExecutor——Layer 4「无人值守多 Agent 自主编排」栈的**顶部 LLM 入口**，使 LLM agent（如 ReAct loop 内的 LEAD/MEMBER）可经工具调用驱动 plan 233 `TeamTaskFlowOrchestrator.execute(teamId)` 执行其所在团队的 task DAG，闭合 plan 233/236/237/238 之后的最后一块缺口（LLM 经工具调用进入编排链路）。消费既有契约原样不改：同步调度策略（`execute()` 同步 + 包装 `CompletableFuture.completedFuture`，async 为显式 Non-Goal successor）+ team-from-session（`getTeamBySession` 解析调用者团队，不暴露 teamId 参数，与全部既有 team 工具一致 ACL-safe）+ wire-at-consumer `IMemberSpawner`（工具 `memberSpawner` 字段 + setter，null-safe→NoOp shipped 默认；per-invocation 构造 `new TeamTaskFlowOrchestrator(engine, taskStore, teamManager, null, spawner)`，engine/taskStore/teamManager 从 context）+ 诚实三态结果映射（DAG 成功→success body 含 completedTaskIds/startOrder/completionOrder / DAG 节点失败→status="success" + failure body 含 failedTaskId/skippedTaskIds 标 `success:false` 不静默成功 / 结构异常 NopAiAgentException→errorResult）+ ACL action=`execute`→WRITE（`DefaultTeamAclChecker.buildRequiredActions` 新增矩阵条目，LEAD/MEMBER allow 非成员 deny，NoOp shipped 默认放行零回归，denial 返回 honest-denied body + orchestrator 不被调用）。唯一扩展是 `DefaultTeamAclChecker` §5.1 矩阵数据（非接口契约变更）。`AgentToolExecuteContext` 无需新增字段。`ai-agent-tools.beans.xml` 注册 bean（auto-collect 路径）。18 focused 测试全绿（含真实-checker ACL LEAD/MEMBER allow 非成员 deny + NoOp 零回归 + session 解析 + NoOp teamManager/taskStore honest not-enabled + 无团队 errorResult + ACL denial honest body orchestrator 不被调用 + 成功/失败/结构异常三态映射 + functional spawner 运行期 spawn + NoOp spawner honest failure + setter wire-at-consumer 接线验证）+ 4 端到端测试全绿（线性 A→B→C + 菱形 A→{B,C}→D 经 LLM 工具入口 → orchestrator → DAG 调度 → 运行期 spawn → COMPLETED **全程无任何手动 `bindMemberSession`** Anti-Hollow 执行序 + NoOp 零回归对比 honest failure + bound-priority spawner 不被调用）。设计文档 `nop-ai-agent-team-execute-flow.md`。异步跨进程编排 / 多团队编排 / 自定义调度策略 / 动态改图 / decorator / 多成员 per-task 路由 / spawn 池化 / task-reclaim 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-team-task-reclaim-and-timeout-abandon | 团队任务 RE-CLAIM + 超时自动 ABANDON（plan 240）：闭合 Layer 4「无人值守多 Agent 自主编排」栈的**任务生命周期自愈缺口**——把团队任务生命周期从"CLAIMED 单向不可逆（一个被成员认领后中途崩溃的任务永远卡在 CLAIMED，阻塞整个 task DAG）"扩展为"RecoveryManager daemon 的 scanOnce 新增第 4 步 team-task 卡死检测与恢复"。状态机扩展：`ITeamTaskStore.reclaimTask(taskId, reclaimedBy)` → `CLAIMED → CREATED`（clear `claimedBy`，重置可重新认领），三实现同步（`InMemoryTeamTaskStore` compute CAS / `NoOpTeamTaskStore` UOE / `DbTeamTaskStore` 条件 UPDATE CAS + `TenantSql.whereTenant` 租户守卫）；`TeamTaskStatus` Javadoc 补 `CLAIMED → CREATED` 转换消除 contract drift。handler 契约 + 实现：`ITeamTaskRecoveryHandler`（`recoverStuckTasks() → List<TeamTaskRecoveryOutcome>`，**自包含**——handler 内部做检测 + 动作，scanOnce 仅调用此方法，有意且裁定的偏差 plan 226/229 模式：team-task 不同域表封装在 handler 保持 daemon 聚焦 session 域）+ `TeamTaskRecoveryAction` 枚举（RECLAIM 重置可重新认领 / ABORT 终态标记失败 / SKIP 观测）+ `TeamTaskRecoveryOutcome` 数据对象（taskId/action/succeeded/message，镜像 RecoveryOutcome/TimeoutOutcome）+ `NoOpTeamTaskRecoveryHandler` shipped 默认（emptyList，零 DB 访问，零回归）+ `DefaultTeamTaskRecoveryHandler` functional 实现（时间基检测 `STATUS='CLAIMED' AND UPDATED_AT < now - threshold` + `TenantSql.whereTenant(COL_TENANT_ID)` 租户守卫 + RECLAIM/ABORT raw JDBC UPDATE 条件 `WHERE STATUS='CLAIMED'` CAS + affected-row 判定 succeeded + per-task 故障隔离：检测 SQLException 包裹 NopAiAgentException 抛出 / per-task UPDATE SQLException catch 返回 succeeded=false outcome 不阻断同批次 + `ITenantResolver` opt-in 默认 `NullTenantResolver` + fail-fast dataSource null/timeout<=0/action=SKIP）。daemon 集成：`ScheduledRecoveryManager` 新增 `teamTaskRecoveryHandler` 字段（默认 NoOp）+ `setTeamTaskRecoveryHandler`/`getTeamTaskRecoveryHandler`（null 拒绝）+ scanOnce 第 4 步（orphan detection 之后，最后，最小化干扰既有 session 步骤）+ `RecoveryScanResult` 8-arg 扩展（`teamTaskRecoveryActions` 字段 + getter + `empty()` 适配）。核心裁定：RECLAIM=CLAIMED→CREATED（非终态复活）/ 时间基检测（非 claimer-liveness 交叉检测）/ handler 自包含 / 单一 defaultAction / scanOnce 第 4 步 / raw JDBC 动作 / 多租户守卫 / NoOp shipped 默认。34 focused/E2E 测试全绿（含 reclaim store in-memory CAS+并发 / reclaim store DB CAS+跨实例+并发 / handler RECLAIM+ABORT+非卡死不动+终态不检测+CAS 竞争+fail-fast+跨租户隔离+per-task 故障隔离+检测 SQLException NopAiAgentException / daemon scanOnce 第 4 步+setter 注入+NoOp 零回归+步骤顺序 + E2E RECLAIM 完整路径+ABORT 终态不可逆+DAG 自愈（t2 blockedBy t1，t1 卡死后 RECLAIM→member2 claim+complete→t2 就绪）+NoOp 零回归对比）。设计文档 `nop-ai-agent-team-task-reclaim.md`。claimer-liveness 交叉检测 / per-task 超时配置 / 动态分级动作策略 / `team-task-reclaim` LLM 工具 / 终态复活 / RecoveryScanResult 构造器重构 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-async-cross-process-orchestration | **异步团队任务编排（async 半部）**（plan 241）：把团队任务 DAG 编排从「同步阻塞——`TeamTaskFlowOrchestrator.execute(teamId)` 在调用线程上阻塞到整个 DAG 完成（每节点 `agentEngine.execute(request).join()` 同步阻塞成员 agent 执行）」扩展为「异步非阻塞 + DAG 并行分支真正并发」。新增非阻塞入口 `TeamTaskFlowOrchestrator.executeAsync(teamId) → CompletableFuture<TeamTaskFlowResult>`，**消费 nop-task 既有 async 模型**（`TaskStepReturn.ASYNC_RETURN` / `getReturnPromise` / `asyncOutputs`，纠正历史 plan 236/237/238/239/240 反复标注的"async model 未落地"前提——live repo 核对表明 nop-task `TaskStepReturn` + `GraphTaskStep` 已提供完整 async 契约 + CompletableFuture 调度就绪节点并发）。`MemberAgentTaskStep.execute` async 化：claim（CREATED→CLAIMED）在节点触发期同步完成，包装 `IAgentEngine.execute()` 既有 `CompletableFuture` 为 async `TaskStepReturn`（`ASYNC_RETURN`，非 `.join()`），complete/失败在 async 回调处理（诚实失败语义与同步路径逐条对齐：claim 失败 / 成员异常 / 非 completed / complete CAS 失败 → future 异常 + 任务保留 CLAIMED 不自动 abandon；已 COMPLETED 幂等为显式成功）。独立分支（互无 `blockedBy` 依赖）的就绪节点经 nop-task `GraphTaskStep` 既有 CompletableFuture 调度真正并发执行（async 化 member step 是解锁并发的关键——同步 join 会阻塞调度线程使并发退化为串行）；依赖序（如 D blockedBy {B,C}）由 `waitSteps` 严格保证。`execute(teamId)` 保留为 sync 便捷入口（`= executeAsync(teamId).join()` 语义等价包装，零回归）。`team-execute-flow` LLM 工具接线为消费真实 `executeAsync`（消除"伪 async 包装 sync"的 hollow 模式）。**已知限制**：`SpawnMemberAgentTaskStep` 保持同步（裁定 3a——`IMemberSpawner.spawnMember()` 是同步契约，async 化需改契约 / supplyAsync+tenant-context 传播 / 绕过 spawner 三选一，均为独立结果面，切出 successor `L4-spawn-step-async`）；含 spawn 节点的图 `executeAsync` 仍正确完成，仅并发度受限。结构性问题同步 fast-fail（null teamId / 无任务 / 未知 team / 环形 blockedBy）/ 节点失败经 future 异常 → `TeamTaskFlowResult{success=false}`（No Silent No-Op #24）。**nop-task `TaskStepReturn.ASYNC` 已同步完成时 short-circuit `syncGet` 行为**：`executeAsync` 需在 `built.task.execute(taskRt)` 周围 try/catch 把同步抛出的节点失败转换为 honest failed future。25 focused/E2E 测试全绿（executeAsync 入口 7 + 并行分支 2 含真实并发证据 peakConcurrent≥2 + 区间重叠断言 + D 依赖序严格 + honest failure 5 含 claim CAS 失败 / 成员异常 / 非 completed 三态 / completeTask CAS 失败 / 已 COMPLETED 幂等 + sync 零回归 4 + LLM 工具接线 3 + E2E 4 含 diamond async + B 失败 D skipped + spawn-on-demand 正确性 + sync vs async 等价）。设计文档 `nop-ai-agent-async-team-task-orchestration.md`。cross-process daemon 协调（分布式锁 / 多实例扫描协调 / 共享调度状态，切出 successor `L4-cross-process-daemon-coordination`）/ `TeamTaskSchedulerDaemon` per-cycle async 派发 / `SpawnMemberAgentTaskStep` async 化（切出 successor `L4-spawn-step-async`）/ 多成员路由 / spawn 池化 / decorator / 动态改图 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-cross-process-daemon-coordination | **跨进程 daemon 扫描协调（team 级 scan lease 降冗余扫描优化层）**（plan 242）：把多实例 daemon 部署从"N 个实例无协调地重复扫描全部团队——每个 `TeamTaskSchedulerDaemon` 实例每 5s 全量扫描 active team（DB 读 + topology build + ready query + claim CAS 冗余负载随实例数线性增长；claimTask DB CAS 已保证无 double-dispatch 正确性地板）"扩展为"team 级 scan lease 协调：daemon 扫描每个 team 前经 `IDaemonCoordinator.tryAcquireScanLease` 获取该 team 短时 lease，若另一实例持有活跃 lease 则跳过该 team（`skippedCoordinatedTeams` 计数 + LOG.debug 显式协调信号非静默跳过），N 实例自然分担不同 team 的扫描+派发"。新增 `io.nop.ai.agent.runtime.coordination` 包：`IDaemonCoordinator` team 级 scan lease 契约（`tryAcquireScanLease(teamId, ownerId, leaseMs) → boolean` 原子 CAS + `releaseScanLease(teamId, ownerId) → boolean` 条件释放 + `isScanLeaseActive(teamId) → boolean` 查询，完整 CAS 真值表 Javadoc）+ `NoOpDaemonCoordinator` shipped 默认（恒成功零回归）+ `DbDaemonCoordinator` DB 功能实现（镜像 plan 221 `DbSessionTakeoverLock` 范式：`ai_agent_daemon_coord` 表 PK TEAM_ID + INSERT 乐观 + duplicate-key conditional UPDATE CAS + portable `isDuplicateKey` 检测 + 构造期 `initSchema` 自动建表 + `ITenantResolver` opt-in 默认 `NullTenantResolver` + `TenantSql.whereTenant` 多租户守卫 + 全部 SQLException 包裹 `NopAiAgentException` 不吞异常）+ `AiAgentDaemonCoordTable` DDL/列常量。`TeamTaskSchedulerDaemon` 接线：`daemonCoordinator` 字段（默认 NoOp）+ `setDaemonCoordinator`/`getDaemonCoordinator`（null-safe 回退 NoOp）+ `daemonOwnerId` 字段（默认 `"scheduler-daemon-"+UUID` 每实例唯一，**不复用 `daemonSessionId`** 共享固定值否则协调失效 hollow）+ `scanLeaseMs` 字段（默认 `DEFAULT_SCAN_LEASE_MS = 30_000L` = 6× 默认 5s scan interval，设计裁定 8）+ scanOnce 逐 team lease guard（acquire BEFORE topology build→扫描→finally release 主动释放；acquire fail→`skippedCoordinatedTeams++`+LOG.debug 跳过）+ `SchedulerScanResult` 扩展 `skippedCoordinatedTeams` 计数（向后兼容构造器 + 12-arg 全参构造器 + getter）。设计裁定 8 项：DB lease-table 镜像 `DbSessionTakeoverLock` 范式 / per-team scan lease 粒度（非 leader election 单点负载）/ 独立新接口（非复用 ISessionTakeoverLock 域混淆）/ 只接线 SchedulerDaemon（RecoveryManager 60s 低频冗余低成本 successor）/ NoOp 恒成功零回归 / 正确性地板不受影响（claimTask CAS 仍保证无 double-dispatch）/ daemonOwnerId 不复用 daemonSessionId / scanLeaseMs = 6× scan interval。**已知限制（诚实记录）**：RecoveryManager 协调仍为 successor（60s 低频幂等，冗余成本低）；若单 team 扫描耗时超过 scanLeaseMs lease 中途过期另一实例可抢占（降冗余减弱非正确性问题）；静态 team 分区 / load-aware work stealing 均为 successor。29 focused/E2E 测试全绿（TestDbDaemonCoordinator 10 含 lease CAS 全路径 + 同 owner renew + 异 owner 活跃 fail + 过期抢占 + release 条件 + isActive + 多租户守卫 + 并发 CAS 1 winner + portable duplicate-key + 参数校验 / TestNoOpDaemonCoordinator 4 恒成功零回归 / TestSchedulerDaemonCoordination 7 含 NoOp 零回归全量扫描 + DB 协调器 lease guard 接线 spy 验证 #23 + acquire fail 跳过计数 + release fail 不影响结果 + null-safe 回退 NoOp + ownerId 默认唯一 + scanLeaseMs 默认 / TestMultiInstanceScanCoordination 3 Anti-Hollow #22 两实例同 DB 同 team 集合 per-team lease 获取/跳过可观测证据 + 顺序 clean handoff + NoOp 零协调基线对比 / TestSchedulerDaemonCoordinationHonestFailure 5 lease fail 跳过计数非静默 + DB 异常 NopAiAgentException 传播 + NoOp 恒成功 + release CAS fail LOG.warn 不影响结果 + 参数校验）。设计文档 `nop-ai-agent-cross-process-daemon-coordination.md`。**ScheduledRecoveryManager 跨进程协调 / 静态 team 分区 / load-aware work stealing / nop-job cluster 协调集成 均为显式 Non-Goals successor。** | L4-8 | ✅ |
| L4-spawn-step-async | **`SpawnMemberAgentTaskStep` async 化（plan 243）**：把团队任务 DAG 中**剩余的唯一同步阻塞节点** `SpawnMemberAgentTaskStep`（无 bound member 的 spawn-on-demand 图节点）从"节点运行期同步阻塞 nop-task DAG 调度线程执行 `spawnMember`（`DefaultMemberSpawner` 内部 `engine.execute().join()`）"扩展为"异步非阻塞"。机制裁定 **(b) `CompletableFuture.supplyAsync` 卸载**：claim（CREATED→CLAIMED）保持同步完成（与 `MemberAgentTaskStep` 一致，保 DAG 依赖序 + claim CAS 失败同步 fast-fail + 已 COMPLETED 幂等同步成功），`spawnMember` + 三态解释（NO_SPAWN/SPAWN_FAILED/dispatched-non-completed/spawner-throws/null）+ `completeTask` 包装进 `supplyAsync(..., spawnExecutor)` 卸载到 worker 线程，返回 async `TaskStepReturn`（`ASYNC_RETURN`，与 `MemberAgentTaskStep` 同一 async 契约）。拒绝 (a) 改 `IMemberSpawner` 契约（破坏 daemon plans 236/237 跨模块回归）/ 拒绝 (c) 绕过 spawner（破坏扩展点抽象 hollow）。**executor 隔离（裁定 3）**：`TeamTaskFlowOrchestrator` 持有/创建**独立于 commonPool** 的有界 daemon 线程池（`ai-agent-spawn-worker-N`，池大小 = spawn 并发上限，wire-at-consumer `setSpawnStepExecutor`，`close()` 释放）透传给 step——`DefaultAgentEngine` 自身 supplyAsync 用 commonPool，spawn worker 经 `.join()` 阻塞等待 engine future，同池则并发 spawn ≥ parallelism 时停滞/死锁（`CompletableFuture.join()` 不参与 `managedBlock`）。**tenant-context 跨 worker 传播（裁定 2，explicit-propagation）**：orchestrator 在 `executeAsync` 入口（调用方线程）捕获 `ThreadLocalTenantResolver.current()` 注入每个 step，step supplyAsync worker 首行 `set(captured)`、finally `clear()`（不泄漏到池化线程，复用 plan 232 范式）——对所有 DAG 拓扑鲁棒（enter 与非 enter 节点都成立，菱形 `A→{B,C}→D` 中 B、C 的 `execute()` 运行在前驱完成线程，thread-based 捕获对该线程不可靠）。**async 诚实失败（裁定 7，#24）**：NO_SPAWN / SPAWN_FAILED / 非 completed / complete CAS 失败 / spawner throws / null 各路径逐条对齐同步路径（future 异常 → `TeamTaskFlowResult{success=false}`，任务保留 CLAIMED 不 abandon）。`IMemberSpawner`/`DefaultMemberSpawner`/`NoOpMemberSpawner` 契约与实现**零变更**（仅消费方变更，daemon 路径零回归）。闭合含 spawn 节点的图真正并发（菱形 spawn DAG B、C 并发 + D 依赖序严格）。22 focused/E2E 测试全绿（spawn-node 并发 2 含 peakConcurrent≥2 + 区间重叠 + D 依赖序 / async honest failure 7 各路径 / tenant 传播 5 含**非 enter 节点**关键裁定 + 多租户隔离 + 无泄漏 / sync 零回归 4 / E2E 4 含 spawn async 全路径 + 失败传播 + spawn+bound 混合图 + sync 等价）。设计文档 `nop-ai-agent-async-team-task-orchestration.md` §2.4。daemon per-cycle async 派发 / 多成员 per-task 路由 / spawn session 池化 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-multi-member-per-task-routing | **多成员 per-task 路由（fan-out + reduction，plan 244）**：把团队任务 DAG 编排中的**单一成员委派原语**（一个 team task 节点委派给**一个**成员 agent——bound 路径 `resolveMember` 单成员 / spawn 路径 `resolveSpawnTarget` 单 memberSpec）扩展为**多成员 per-task 路由**：一个 team task 节点可 fan-out 至 **N** 个成员 agent（已绑定 +/或 spawned）并发执行，经 reduction 策略把 N 个成员执行结果归约为单一 task 结果。新增 `ITaskMemberRouter` 可插拔 per-task 路由扩展点（**单一扩展点覆盖 bound + spawn 两半部**，返回 `MemberDispatchPlan` 标注每个 `DispatchTarget` 是 BOUND 还是 SPAWN + 携带 `IReductionStrategy`，design 裁定 2——避免双扩展点接线复杂度）+ `NoOpTaskMemberRouter`/`SingleTaskMemberRouter` shipped 默认（singleton plan，bound 优先 + spawn fallback，逐行复现 plans 233/238/241/243 单成员行为零回归）+ `IReductionStrategy` 可插拔归约扩展点（v1 仅 all-must-succeed 落地，quorum/majority/first-wins 接口预留 opt-in successor）+ `AllMustSucceedReduction` shipped 默认（最严格：N 成员都须 `AgentExecStatus.completed`，任一失败 fast-fail + task 保留 CLAIMED，design 裁定 3）+ `MemberExecOutcome`/`ReductionContext`/`FanOutReduceComplete` 共享归约+complete+tenant 传播 helper。三种 fan-out step 变体：`BoundMemberFanOutStep`（N 个 `engine.execute` future 直接组合，bound 半部，design 裁定 7）/ `SpawnMemberFanOutStep`（N 个 `supplyAsync(spawnMember)` 组合，spawn 半部，复用 plan 243 dedicated executor + explicit-propagation tenant）/ `MixedMemberFanOutStep`（同一 plan 含 bound + spawn target 经统一 reduction 组合）。单成员 plan → 既有 `MemberAgentTaskStep`/`SpawnMemberAgentTaskStep` 路径逐行不变（short-circuit 零回归）。`SpawnMemberRequest` 增可选 additive target 字段（向后兼容：既有三参构造 target=null = spawner 自解析 = daemon 路径零变更，design 裁定 6；`DefaultMemberSpawner.spawnMember` 优先 request.target 否则 fallback `resolveSpawnTarget`；`IMemberSpawner.spawnMember(SpawnMemberRequest)` 接口签名不变）。claim 同步单次（CREATED→CLAIMED，保 DAG 依赖序）、complete 在全部 N 成员 completed 后单次（CLAIMED→COMPLETED）、tenant 在 completeTask 处 re-apply + finally clear（reduction+complete 链运行在最后完成的成员线程上，该线程可能已 clear tenant，故 `FanOutReduceComplete` 显式 re-apply）。诚实失败语义逐条对齐（空 plan / 成员失败 / spawner 三态 / CAS 失败 / null 各路径 + task 保留 CLAIMED 不 abandon；已 COMPLETED 幂等显式成功；markFailed 经 steppedFuture.whenComplete 兜底所有异常路径；No Silent No-Op #24）。fan-out 先失败 fast-fail，在途成员不取消（run-to-completion 结果丢弃，取消为 Non-Goal successor，design 裁定 5）。23 focused/E2E 测试全绿（bound fan-out 并发含 peakConcurrent≥2 + 区间重叠 + D 依赖序 / spawn fan-out 并发含 ai-agent-spawn-worker-N 线程观测 + D 依赖序 / all-must-succeed reduction 全 completed 单次 completeTask + 任一失败留 CLAIMED / 空 plan honest throw build abort / 单成员 NoOp 零回归 + 空 team honest failure / tenant 隔离 fan-out 跨 worker 传播 + 无泄漏 / 混合 bound+spawn fan-out / CAS 失败 honest / 已 COMPLETED 幂等 / E2E 5 含 bound+spawn diamond full path + honest failure 传播 + NoOp 等价 + sync≡async）。设计文档 `nop-ai-agent-multi-member-routing.md`。task 分片（partitioning）/ 成员 pipeline / quorum-majority-first-wins reduction / 在途取消 / per-task 配额 / daemon 多成员派发 / spawn 池化 / decorator / 动态改图 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-daemon-multi-member-async-dispatch | **daemon 多成员 + async 派发 parity（plan 245）**：把无人值守自动化 dispatch 路径（`TeamTaskSchedulerDaemon`，plan 236 交付）从「单成员 + 同步 `engine.execute().join()`」提升至与程序化 orchestrator（`TeamTaskFlowOrchestrator`）对等的「per-task 多成员 fan-out + async 派发」，闭合无人值守 vs 交互式编排的能力不对称。新增共享 canonical `MemberFanOutDispatcher`（收敛 plan 244 三 step 变体的 fan-out + reduce + complete 逻辑——bound/spawn/mixed step + daemon 均经其委派，无双并行代码路径，Anti-Hollow #8/#22）+ `MemberDispatchOutcome`（COMPLETED/FAILED）。daemon 注入 `ITaskMemberRouter`（null-safe → NoOp shipped 默认 = singleton plan = bound 优先 + spawn fallback = 单成员逐行零回归）+ dedicated spawn executor（`ai-agent-daemon-spawn-worker-N`，复用 plan 243 隔离约束）+ explicit-propagation tenant（scan 入口捕获注入 dispatcher，spawn worker set/clear）。daemon per-cycle dispatch 不再 `.join()` 阻塞于单 task：fan-out future 经 async 派发，daemon 线程不阻塞（已完成 future 同步观测 = 单成员零回归；真正 async future 经 `inFlightDispatches` 队列 + `awaitInFlightDispatches` 跟踪）。诚实失败语义对齐 orchestrator + plan 240 reclaim（**有意变更**：fan-out/reduction 失败 → task 保留 CLAIMED 非 abandon，由 reclaim 恢复；既有 daemon 失败测试相应更新为 CLAIMED + 新 `SchedulerScanResult.failedTasks`/`failedTaskIds` 计数，`abandonedTasks` 保留向后兼容但 fan-out 路径恒为 0）。`IMemberSpawner.spawnMember` 接口签名零变更（plan 244 锁定）。9 focused/E2E 测试全绿（bound fan-out 真实并发 peak≥2 + async 非阻塞 scan 提前返回 + reduction completeTask 单次 + 任一失败 retain-CLAIMED + 空路由/spawner 三态/throws/null honest failure + 单成员 NoOp 零回归 + tenant 传播无泄漏 + daemon-vs-orchestrator parity + diamond fan-out 全路径依赖序）。设计文档 `nop-ai-agent-daemon-dispatch-parity.md`。跨进程 daemon 协调 / nop-job 集成 / claimer-liveness / decorator / 在途取消 / partitioning / pipeline / quorum reduction / 调度策略变更 / spawn 池化 / 动态改图 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| L4-nop-task-decorator | **nop-task 可组合 decorator 体系扩展—retry/timeout/rateLimit（plan 246）**：闭合 carry-over queue 中被 5 个 closed plans（236/241/243/244/245）反复引用的 cross-cutting reliability gap——把 nop-task 的可组合 decorator 体系（`ITaskStepDecorator` + `<decorator name="..."/>` XML 语法 + bean `nopTaskStepDecorator_<name>`）从「仅 transaction / ormSession 两枚」扩展为「retry / timeout / rateLimit 三枚可组合 reliability decorator」。位置 = `nop-task-ext`（generic 扩展层，与既有 decorator 同位置，所有 nop-task 用户可用）。三枚 decorator 委托既有 wrapper（Anti-Hollow #8）：`RetryTaskStepDecorator` 读 `retry:maxRetryCount`/`retry:retryDelay`/`retry:maxRetryDelay`/`retry:exponentialDelay` 配置 → 经 nop-commons `RetryPolicy` 公开 setter 构造 policy（配置提取非算法重实现，因 `TaskStepEnhancer.buildRetryPolicy` 为 private 且改之触 nop-task-core Non-Goal）→ 返回既有 `RetryTaskStepWrapper`；`TimeoutTaskStepDecorator` 读 `timeout:timeout` → 返回既有 `TimeoutTaskStepWrapper`；`RateLimitTaskStepDecorator` 读 `rateLimit:requestPerSecond`/`rateLimit:global`/`rateLimit:maxWait` → 返回既有 `RateLimitTaskStepWrapper`。config surface 遵循既有 transaction/ormSession namespace 约定（`prefix:attr` → `TaskDecoratorModel.extProps` → `config.prop_get(...)`）。诚实失败（#24）：无效配置（<= 0 / 负数 / 必填缺失）throw `NopException`（nop-task-ext 模块级 `TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG`），不静默返回原 step。decorator name 取 camelCase `rateLimit` 与 `RateLimitTaskStepWrapper`/first-class attr `rateLimit` 一致（bean id 逐字匹配 `nopTaskStepDecorator_<exact name>`）。设计裁定 8 项：decorator-bean 形式 vs first-class-attr / 委托既有 wrapper / nop-task-ext 位置 / config surface / 诚实失败 / 拒绝 builder 自动传播 + DB 配置面 successor / retry 经 RetryPolicy setter 构造 / decorator+first-class-attr 嵌套包装组合语义。15 focused/E2E 测试全绿（retry 真实重试 ≥2 次 + 耗尽 honest throw / timeout 真实超时 / rateLimit 真实限流第二次拒绝 / retry+timeout 组合 / decorator+first-class-attr 嵌套可观测 / 6 条 honest failure 各路径 throw / 零回归 + E2E 全链路）。设计文档 `nop-ai-agent-task-step-decorator.md`。**已知限制（诚实记录）**：nop-task in-memory `TaskStepStateBean.fail()` 为 no-op（不保存 exception 引用）→ `RetryPolicy.isRecoverableException` 因 `state.exception()=null` 跳过不可重试分类；`TaskStepHelper.retry` 同步成功分支无显式 return → loop 继续至 retryCount 耗尽（async 路径正常）。两者均为 nop-task-core 既有 quirk，不在本计划 scope 修正（Non-Goal）。team-task builder 自动传播 + DB 配置面 / throttle decorator bean / decorator 在 team-task DAG 实战接线 / nop-task-core 内部修正（state exception 保存 / retry loop 同步返回） 均为显式 Non-Goals successor。 | L4-8 | ✅ |
| nop-task-core-state-exception-persistence | **nop-task `TaskStepStateBean` exception 持久化修复（plan 247）**：闭合 plan 246 §Closure 已知限制「in-memory state 不保存 exception → 不可重试分类不生效」——修复 `TaskStepStateBean` 的两个 no-op 方法体（`fail(Throwable, ITaskRuntime)` 从空 `{}` 改为 `exception(exception);` 委托 setter；`exception(Throwable exp)` 从空 `{}` 改为 `this.exception = exp;` 直接赋值），使 `state.exception()` getter 返回真实异常。这解锁 plan 246 刚交付的 `RetryTaskStepDecorator` 的 `RetryPolicy.isRecoverableException` 异常分类：修复前 `state.exception()` 恒为 null → `RetryPolicy.getRetryDelay` 跳过分类分支 → 所有异常无条件按 retryCount 重试至耗尽（不可恢复异常如 `NopException.bizFatal(true)` 无法提前 honest throw）；修复后 `state.exception()` 返回真实异常 → 分类联动生效（bizFatal → 不可恢复 → delay=-1 → 立即抛出；非 bizFatal → 可恢复 → 按 maxRetryCount 重试）。最小手术式修复（2 行代码），scope 控制在 `TaskStepStateBean` 2 个方法体，不改 `TaskStepHelper.retry()` / `RetryTaskStepDecorator` / `RetryPolicy`（设计裁定 4，Protected Area scope control）。设计裁定 4 项：fail() 只保存异常不做生命周期终止 / fail() 委托 exception() 保持单一赋值入口 / 不改 transient 修饰符（in-memory retry 不跨 Java 序列化）/ scope 控制在 2 个方法。6 focused 单元测试 + 1 新增 E2E（16 decorator 测试全绿）+ nop-ai-agent 2714 全量零回归。**新增已知限制（诚实记录）**：xpl 函数调用（`AbstractObjFunctionExecutable.doInvoke*`）会把被调用方法抛出的 NopException 包装为 `NopEvalException(ERR_EXEC_INVOKE_METHOD_FAIL)`，丢失 bizFatal 标记（xpl 既有行为，非 plan 247 scope）。因此 bizFatal 分类的端到端 `.task.xml` 测试不可行（Java 层单元测试覆盖证明分类联动生效）；bizFatal 在 xpl 端到端路径生效为独立 successor（需改 xpl exec 包装行为或提供 bypass 机制）。retry loop 同步成功路径不 return quirk / succeed/isDone/isSuccess/result 完整生命周期 / transient 移除（跨重启 exception 持久化）均为显式 Non-Goals successor。 | L4-8 | ✅ |
| retry-sync-return-quirk | **nop-task `TaskStepHelper.retry()` 同步成功路径 return 修复（plan 248）**：闭合 plan 246 §Closure 已知限制 + plan 247 §Closure Follow-up「retry loop 同步成功路径不 return 的 quirk」——修复 `TaskStepHelper.retry()`（`nop-task-core`）同步循环路径缺少 `return result;` 的缺陷：当被 retry 包装的 step action 返回**同步（非 async）成功结果**时，`result.isAsync()` 为 false，控制流跳过 async 分支后没有 return，直接落入 `setRetryAttempt(retryAttempt + 1)` + `while(true)` 循环回顶，导致成功的 step 被反复重新执行至 retryCount 耗尽。plan 246 把 retry decorator 提升为用户可配置原语（`<decorator name="retry"/>`）后，每个 retry-wrapped 同步成功 step 都直接命中此缺陷。修复 = 同步循环路径 `action.call()` 返回非 async 结果时 `return result;`（`TaskStepHelper.java:175`）。最小手术式 1 行：sync 结果 = 完成态应立即返回，与 async 分支（已 return）对称，仅失败（catch）才进 retry 循环。失败重试路径（catch / retryAttempt>0 块 / doRetry）零变更（plan 246/247 已验证正确）。设计裁定 4 项：return = sync 成功立即返回 / scope 限于同步循环路径不触及延迟调度路径 / 不改失败重试路径语义 / 诚实失败保留（return 真实结果非吞掉）。2 focused 单元测试（`TestTaskStepHelperRetrySyncReturn`：sync 成功执行恰好一次 executeCount==1 核心证明修复前失败 + 失败重试回归 executeCount==maxRetryCount+1）+ 1 新增 E2E（`TestReliabilityDecorators.retry_syncSuccessExecutesExactlyOnce`：`.task.xml` retry decorator → sync 成功 step → executeCount==1 返回 "OK"）+ nop-task-core 34 + nop-task-ext 24（含 17 decorator 测试）+ nop-ai-agent 2714 全量零回归。延迟调度路径 sync 处理（watch-only residual）/ succeed/isDone/isSuccess/result 生命周期（successor）/ transient 移除（optimization candidate）均为显式 Non-Goals。 | L4-8 | ✅ |
**Layer 4 验收标准**：

- [x] 多 Agent 任务可以通过 Flow / Task 组织（**nop-task DAG 集成已落地（plan 233 / L4-nop-task-dag-integration）**：团队任务可组织为 nop-task 工作流图（`TeamTaskGraphBuilder` 真实经 `GraphStepAnalyzer` 环检测 + `TeamTaskTopology` 就绪/阻塞拓扑查询）+ 依赖序同步编排（`TeamTaskFlowOrchestrator` 真实经 `ITask.execute(...).syncGetOutputs()` 执行 `GraphTaskStep` DAG 调度，每节点委派已绑定成员 agent + `completeTask` 标记 COMPLETED，失败传播 + Anti-Hollow 端到端验证）。设计文档 `nop-ai-agent-task-flow-integration.md`。**blockedBy 自动调度守护进程已落地（plan 236 / L4-blockedBy-resolution-engine）**：`ITeamTaskSchedulerDaemon` + `TeamTaskSchedulerDaemon` 定时扫描就绪任务自动 claim/派发（经 `TeamTaskTopology.getReadyTasks()` + CAS claim + `IAgentEngine.execute` + completeTask，复用 `ScheduledRecoveryManager` 生命周期范式，依赖序经就绪查询天然保证，CLAIMED 他人任务不被误弃），闭合无人值守多 Agent 编排链路。设计文档 `nop-ai-agent-task-scheduler-daemon.md`。**auto-spawn 成员 agent 已落地（plan 237 / L4-auto-spawn-member-agent）**：`IMemberSpawner` 可插拔扩展点 + `NoOpMemberSpawner` shipped 默认零回归 + `DefaultMemberSpawner` functional 实现（基于 `TeamMemberSpec.agentModel` spawn 成员 agent 经 `IAgentEngine.execute` 同步执行），daemon dispatch 路径在无 bound member 时咨询 spawner（bound-member 优先），闭合「无人值守下未绑定成员 = abandon」最后缺口——团队只需声明 memberSpec 无需预绑定 session，任务到达时自动 spawn 对应成员执行。设计文档 `nop-ai-agent-member-auto-spawn.md`。**程序化编排器 auto-spawn 集成已落地（plan 238 / L4-orchestrator-auto-spawn-integration）**：`TeamTaskFlowOrchestrator`（plan 233）从「无已绑定成员 = 构建期 fail-fast」扩展为「无已绑定成员的节点在**图运行期**（DAG 调度器触发时）经同一 `IMemberSpawner` spawn 执行」（spawn 在节点运行期而非构建期以保持 DAG 依赖序；bound-member 路径逐行不变零回归；无 bound member + NoOp = 诚实失败 throw→return-failed-result 有意 API 变更）。设计文档 `nop-ai-agent-orchestrator-auto-spawn.md`。**团队通信 + 任务状态机 foundational 已落地**：plan 223 / L4-8-team-manager 已交付 TeamManager 注册表基础层 + plan 225 / L4-8-team-tools 已交付团队通信工具 foundational 层（`team-send-message` / `team-status` / `team-task-create` + `ITeamTaskStore` 契约 + `InMemoryTeamTaskStore`）+ plan 227 / L4-8-team-task-update 已交付 `team-task-update` 任务认领状态机（claim/complete/abandon 三动作经 `TeamTaskStatus` 状态机 CREATED→CLAIMED→COMPLETED、CREATED/CLAIMED→ABANDONED）+ DB-backed 共享任务表（`DbTeamTaskStore` raw JDBC 条件 UPDATE on STATUS CAS 认领，跨进程共享，构造期自动建表）。**自动团队绑定已落地（plan 231 / L4-team-auto-binding）**：lead `.agent.xml` `<team>` + member `<team-member>` 嵌入 `agent.xdef` + 引擎三入口点 auto-bind，团队可仅凭配置声明式 materialize。LLM 可在 ReAct 循环中通过工具操作团队任务的完整生命周期。**LLM 直面编排工具已落地（plan 239 / L4-team-execute-flow-llm-tool）**：`team-execute-flow` IToolExecutor 是 Layer 4 顶部 LLM 入口，LLM agent 经工具调用驱动 `TeamTaskFlowOrchestrator.execute` 执行其所在团队的 task DAG（同步调度 + team-from-session ACL-safe + wire-at-consumer spawner + 诚实三态结果映射），闭合 LLM 经工具调用进入编排链路的最后一块缺口。设计文档 `nop-ai-agent-team-execute-flow.md`。**剩余 successor**：异步跨进程流编排 / nop-task decorator 接入 / 运行时动态改图 仍为显式 successor）
- [x] 多用户可并发运行独立 Actor，租户间资源隔离（**团队维度 ACL 基础设施已落地**：plan 228 / L4-team-acl-enforcement 已交付角色权限矩阵 + `ITeamAclChecker` 拦截层——`DefaultTeamAclChecker` 实现 vision §5.1 默认矩阵（LEAD=ADMIN / MEMBER=READ+WRITE+EXECUTE，唯一 MEMBER 拒绝操作 = abandon-unclaimed ADMIN-only），4 团队工具 executor 均在团队解析后调用 checker，denial 实际阻止 store/messenger 操作。**团队状态跨进程 DB 共享基础设施已交付**：plan 230 / L4-team-db-persistence 已交付 `DbTeamManager` raw JDBC 持久化（`ai_agent_team` + `ai_agent_team_member` 共享表，多 JVM 实例指向同一 DB 即团队/成员/绑定/disband 互相可见，进程重启后可重建），是"多用户并发运行独立 Actor"的 foundational 共享基础设施。**完整多租户 Tenant 数据隔离已交付（plan 232 / L4-multi-tenant-isolation）**：全部 10 张 `ai_agent_*` 表新增 `TENANT_ID` 列 + ORM XML 4 实体同步；contextual tenant 解析（`ITenantResolver` + `ThreadLocalTenantResolver` + `NullTenantResolver` shipped 默认）；全部 raw-JDBC DB store 在 tenant 非空时注入 `AND (TENANT_ID = ? OR TENANT_ID IS NULL)` WHERE + INSERT 写入 tenant；`DBSessionStore` cache bypass + MERGE KEY `(SESSION_ID, TENANT_ID)`；`InMemoryActorRegistry` tenant 标签过滤；`DefaultAgentEngine` 异步入口点 supplyAsync worker lambda 内 set/clear tenant context（从 `Principal.tenantId` null-safe 解析）；null tenant = 全部可见（零回归，全量 2400 测试通过）。跨租户 session/team/task/message/checkpoint 完全不可见，端到端经 engine.execute → tenant context → DB WHERE → 隔离结果完整链路验证。**User 隔离（同租户内用户级过滤 + 团队共享例外）仍为显式 successor**，依赖 userId propagation 标准化）
- [x] 长任务中断后可以恢复（plan 183 已落地：`IAgentEngine.restoreSession` + `FileBackedSessionStore` 单进程 crash/restart restore；plan 184 已落地：`IAgentEngine.restorePendingSessions` 自动扫描+批量恢复，补齐"无人值守"最后一块；plan 185 已落地：`DBSessionStore` 使任何共享 DB 的服务实例可接管恢复 session；plan 221 已落地：跨进程 session 接管锁基础层 `ISessionTakeoverLock` + `DbSessionTakeoverLock`——防两个 JVM 实例同时恢复同一 session 的 double-execution correctness gap，lease/TTL CAS + 独立锁表 + opt-in 编程式 API + 三入口点接线 + `restorePendingSessions` `isHeld` 跳过；plan 222 已落地：RecoveryManager 定时扫描 daemon `IRecoveryManager` + `ScheduledRecoveryManager`——`IScheduledExecutor`（nop-commons）周期调度默认 60s，`scanOnce` = stale lock cleanup（幂等 DELETE）+ orphan session detection（LOG.warn），使多实例无人值守部署中 crash 后遗留的 stale lock + orphaned session 能被持续检测并清理而非仅依赖被动 lease TTL 过期或下次实例启动；引擎 `recoveryManager` 字段 + `setRecoveryManager` setter（部署层管理 start/stop）；plan 226 已落地：恢复模式策略 `IOrphanRecoveryHandler` + `RecoveryMode`(RESUME/ABORT/SKIP) + `DefaultOrphanRecoveryHandler` + daemon 集成——orphaned session 可按策略自动恢复（RESUME 委托 restoreSession）/ 标记失败（ABORT raw JDBC UPDATE）/ 观测跳过（SKIP shipped 默认），RETRY 为 successor；plan 229 已落地：超时强制中止 `ISessionTimeoutHandler` + `TimeoutAction`(LOCAL_CANCELLED/FORCE_FAILED/SKIPPED_REMOTE/SKIPPED) + `DefaultSessionTimeoutHandler`（三分裁定经 raw JDBC 直读锁表：本实例锁 → cancelSession forced / 无活跃锁 → UPDATE status=failed / 远端锁 → LOG.warn 不干预）+ daemon scanOnce timeout detection 步骤（先于 orphan，`UPDATED_AT < now - timeoutSeconds`）+ `RecoveryScanResult.timeoutActions`，使卡死/挂起的 session（LLM 永久阻塞、工具死循环、逻辑死锁）能被自动取消或强制标记失败而非无限期占用执行槽；orphan liveness / 归档清理 / 心跳续约仍为独立 successor）

---

## 5. 技术债

| 问题 | 优先级 | 说明 |
|------|--------|------|
| 零单元测试 | P0 | 模块完全无测试覆盖（`src/test/` 零 Java 文件） → 已添加 TestAgentPlanRecordMapping（Phase 2） |
| `agent.register-model.xml` 缺失 | P0 | `.agent.xml` DSL 无法加载，等同于 DSL 死代码 |
| ~~枚举 schema 不一致~~ | ~~P1~~ | ✅ 已解决：record-mappings 统一引用 `AgentExecStatus`，field name `taskStatus` → `status` |
| 33 个空 stub 类（≤9 行） | P2 | 延伸 generated base 但无自定义逻辑，占位符（不含 `BaseAgent`，已删除）。另有 4 个实体类（AgentExecStatus, IAiMemoryStore, AiMemoryItem, AiMemoryConfig） |
| ~~`BaseAgent` 未追踪~~ | ~~P2~~ | ✅ 已解决：L1-14 判定删除，空壳类无引用，已移除 |
| Phase 1 接口锁定风险 | P1 | ISessionStore/IAiMemoryStore/IAgentEngine 接口太窄，无法承载 Phase 2+ 渐进式设计。必须 Phase 1 关闭前加 default 方法（见 L1-15~L1-17） |
| ~~ReActAgentExecutor 构造器链~~ | ~~P1~~ | ✅ 已解决：L1-18 改为 Builder 模式，6 个构造器已移除（见 plan 146） |
| AiMemoryItem 字段不足 | P2 | 4 字段缺 priority/tokenEstimate/pinned，Phase 2 Budgeted Injection 无法实现（见 L1-16） |

---

## 5b. 安全审计发现追踪（2026-06-15 deep audit）

来源：`ai-dev/audits/2026-06-15-deep-audit-nop-ai-agent/`。每个发现的修复状态在此追踪，修复后 ❌→✅ 并标注落地 plan。

| 发现 ID | 严重程度 | 修复状态 | 落地 plan / 说明 |
|---------|---------|---------|-----------------|
| AUDIT-13-15 | P0 | ✅ 已修复 | plan 190：`SessionIds` 两层校验（identifier + containment）接入 `DefaultAgentEngine.resolveSessionId` + `FileBackedSessionStore` / `FileBackedCheckpointManager` 全部 `rootDirectory.resolve(sessionId)` site，fail-closed |
| AUDIT-13-16 | P2 | ✅ 已修复 | plan 191：`AgentNames` allow-list 校验接入 `DefaultAgentEngine.loadAgentModel` chokepoint + `CallAgentExecutor` non-`"self"` agentId defense-in-depth，fail-closed |
| AUDIT-13-01 | P1 | ✅ 已修复 | plan 193：`DefaultAgentEngine` 短构造器 + 字段兜底 + `ReActAgentExecutor.Builder.build()` null 兜底全部从 `AllowAll*` 切换为 `Default*`；新增构造期一次性 WARN（AllowAll* 实例触发，fail-loud via `LOG.warn`）；secure-by-default 端到端验证（`TestSecureByDefault` 6 tests） |
| AUDIT-13-02 | P1 | ✅ 已修复 | plan 194：`DefaultAgentEngine` 新增 `auditLogger` 字段（默认 `Slf4jAuditLogger`）+ `setAuditLogger` setter；`resolveExecutor` Builder 链透传 `.auditLogger(this.auditLogger)`；`ReActAgentExecutor.Builder.build()` null 兜底从 `NoOpAuditLogger` 切换为 `Slf4jAuditLogger`；`warnIfInsecureDefaults` 扩展检查 `NoOpAuditLogger`（setter 注入时触发一次性 WARN，fail-loud via `LOG.warn`）；端到端审计验证（`TestAuditLoggerDefault` 5 tests） |
| AUDIT-13-04 | P1 | ✅ 已修复 | plan 199：新增 `DefaultApprovalGate`（STANDARD/ELEVATED 批准，RESTRICTED defense-in-depth 拒绝）替代 `AutoApproveGate` 作为 engine 默认 + `ReActAgentExecutor.Builder` null 兜底；`AutoApproveGate` 保留为 public opt-in；`warnIfInsecureDefaults` 扩展为 8-arg 覆盖全部 5 个 Layer 2/3 组件（AutoApproveGate 构造期+setter 检查；其他 4 个 NoOp/PassThrough 仅 setter 检查，构造期传 null 跳过以避免噪音）；全部 Layer 2/3 setter 增加赋值后 WARN 调用；focused 测试（`TestDefaultApprovalGate` 6 tests + `TestLayer23SecureDefaults` 10 tests，含端到端 defense-in-depth RESTRICTED 拒绝 + 审计事件验证 + WARN 覆盖验证） |
| AUDIT-14-01 | P1 | ✅ 已修复 | plan 197：`DefaultAgentEngine` 三个执行入口点（`doExecute` / `resumeSession` / `restoreSession`）的 `runningExecutions` 注册/注销从无条件 `put` + 按 key `remove` 收敛为 `putIfAbsent` + fail-fast（`NopAiAgentException`）+ 值比较 `remove(sessionId, handle)`；`CancelHandle.thread` 改为 `volatile` 延迟绑定（同步阶段预注册 handle 关闭 cancel 丢失窗口，lambda 入口绑定执行线程）；移除 `restoreSession` 冗余 `containsKey` 检查；`cancelSession` forced-interrupt null-safe；focused 测试 `TestDefaultAgentEngineConcurrencyGuard` 5 tests（concurrent-execute-fail-fast / finally-no-misremove / cancel-window-honored / restore-guard-consistent / no-regression-normal-path），含端到端 + 接线验证 |
| AUDIT-14-04 | P1 | ✅ 已修复 | plan 195：`SessionFileWriter.write()` + `CheckpointSnapshotWriter.write()` 收敛为 write-to-tmp + `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` + `finally { deleteIfExists(tmp) }`（POSIX rename 原子性）；focused 测试覆盖 target-intact / tmp-cleanup / stale-tmp-recovery / pre-move-failure-isolation / overwrite-write，含 `FileBackedSessionStore.save()` → `SessionFileWriter.write()` 端到端接线验证（`TestSessionFileWriterAtomicWrite` 7 tests + `TestCheckpointSnapshotWriterAtomicWrite` 6 tests） |
| AUDIT-09-01 | P1 | ✅ 已修复 | plan 196：`NopAiAgentException extends RuntimeException` → `extends NopException`，补齐 `serialVersionUID` 与四构造器 `(String)` / `(String, Throwable)` / `(ErrorCode)` / `(ErrorCode, Throwable)`（前两签名保留，100+ 抛出站点与 4 处 catch 站点零改动）；模块异常纳入框架统一异常体系（结构化 `getMessage()`、`.param(...)` 链式、`getErrorCode()`、i18n 钩子）；focused 测试 `TestNopAiAgentExceptionBaseClass` 8 tests 覆盖 `instanceof NopException` / `getErrorCode` 保留原文本 / `getMessage().contains` 兼容 / `.param` 链式 / `(ErrorCode)` ctor / cause 链保留 |
| AUDIT-09-02 | P2 | ✅ 已修复 | plan 198（吸收 AUDIT-09-03）：nop-ai-agent 全模块 49 处 `throw new IllegalArgumentException` + 1 处 `throw new IllegalStateException`（`Layer2TurnPruningStrategy:199`）统一迁移为 `throw new NopAiAgentException`，消息文本不变（构造器签名兼容）；受影响 49 处 `assertThrows(IllegalArgumentException.class,...)` + 4 处 `catch (IllegalArgumentException expected)` 测试断言同步迁移为 `NopAiAgentException`；5 处捕获 JDK `Enum.valueOf()` 异常的 `catch (IllegalArgumentException)` 翻译块保持不变（非本模块 throw 站点消费者）；`./mvnw test` 1547 tests 零回归 |
| L23-SDI | P1 | ✅ 已修复 | plan 200：新增 4 个 `Default*` secure 默认实现（`DefaultSecurityLevelResolver` trusted-by-default 变体 / `DefaultPermissionMatrix` §5.3 channel×level 矩阵 + usability-safe null channel / `DefaultDenialLedger` 纯内存 threshold=3 计数 / `DefaultPostDenialGuard` fingerprint-based 盲重试阻断），替代 4 个 NoOp/PassThrough 作为 engine 默认（field + setter null 兜底 + `ReActAgentExecutor` 构造器 null 兜底）；`warnIfInsecureDefaults` 从 conditionally-checked 迁移到 always-checked（构造期传非 null 给全部 4 个组件）；NoOp/PassThrough 保留为 public opt-in；focused 测试 32 tests（4 个 Default* 组件单元测试 + `TestLayer23SecureDefaultImpls` 端到端 dispatch 测试含 RESTRICTED Layer 2 deny / denial-pause / blind-retry-block / audit-event 验证）；1595 tests 全绿 |

---

## 6. 设计决策记录

### D1：为什么 Agent 是配置对象而非执行体

**选了什么**：`AgentModel` 从 `agent.xdef` 装载，不持有执行逻辑和状态。执行由 `IAgentEngine` + `IAgentExecutor` 负责。

**为什么**：与 Nop 可逆计算原则一致。配置可被 Delta 定制，执行逻辑不需要 Delta。便于独立测试引擎。便于状态恢复（状态不绑定在 Agent 上）。

### D2：为什么采用 Actor 消息模型而非请求-响应模型

**选了什么**：`IAgentEngine.sendMessage()` 立即返回 ack，执行结果通过 `AgentEventPublisher` 异步推送（接口待 L1-1 阶段定义验证）。

**为什么**：无人值守场景需要长时间执行（分钟级到小时级）。请求-响应模型会阻塞调用线程或需要轮询。Actor 模型天然支持异步、恢复、多实例接管。

### D3：为什么 Layer 2-4 接口全部有 pass-through 默认

**选了什么**：每个扩展接口都有一个最简默认实现（NoOp 或 PassThrough），系统可以不带任何扩展运行。

**为什么**：扩展通过添加接口实现完成，不需要阶段切换。测试可以只测核心链路而不用初始化全部扩展。外部框架可以渐进式引入高级能力。

### D4：为什么不做 MCP 支持

**选了什么**：使用 Nop 自有的 `tool.xdef` DSL，不引入 MCP 协议层。

**为什么**：Nop 有完整的 XLang 生态和 IoC 集成。引入 MCP 会增加协议转换层且无独特收益。外部工具通过标准 REST/GraphQL 集成即可。

### D5：LLM 调用路径选择

**选了什么**：Agent 通过 `nop-ai-core` 的 `ChatServiceImpl` 调用 LLM。多模型支持通过 `llm.xdef` + `{provider}.llm.xml` DSL 配置实现（ollama/deepseek/claude/gemini/azure/volcengine/bailian/lm-studio 等 provider 已有配置）。

**为什么**：`nop-ai-core` 已包含完整的 LLM 调用能力和多 Provider 支持。`llm.xml` DSL 可通过 Delta 定制新 Provider，无需引入额外模块依赖。

---

## 7. 审计检查清单

每次规划前，用以下检查清单评估各组件的真实状态：

### 前置层检查

- [ ] `agent.register-model.xml` 存在且可正确加载 `.agent.xml`
- [x] 枚举定义一致：xdef `AgentExecStatus` 与 record-mappings 引用的枚举字段名和值域匹配
- [ ] LLM 调用路径已确认：Agent 通过 `nop-ai-core` 的 `ChatServiceImpl` 调用，基于 `llm.xml` 配置区分 Provider

### Layer 1 核心

- [ ] `agent.xdef` 字段语义稳定，无 ambiguous 字段（注意：`availableSkills` / `requiredSkills`，不是 `skills`）
- [ ] `agent-plan.xdef` 字段语义稳定
- [ ] `AgentModel` 可从 `.agent.xml` 正确装载
- [ ] `AgentPlanModel` 可从 `.agent-plan.xml`/`.yaml`/`.md` 正确装载
- [x] 缺失枚举 `AgentTaskStatus`, `AgentPlanStatus` 已创建（或已与 `AgentExecStatus` 统一）
- [ ] ReAct 循环可以完整跑通：LLM 调用 → 工具调用 → 结果回灌 → 继续推理
- [ ] 至少 1 个 `.agent.xml` 示例文件可被加载执行
- [x] `BaseAgent` 的去留已决定

### Maven 依赖正确性

- [ ] `nop-ai-agent` pom.xml 的依赖列表与实际代码 import 一一致

### 整体

- [ ] `./mvnw clean install -pl nop-ai-agent -am -T 1C` 全量构建通过
- [ ] `./mvnw test -pl nop-ai-agent -am -T 1C` 全量测试通过
- [ ] 设计文档与实际代码一致（xdef 字段 vs 设计描述）

---

## 8. 与其他文档的关系

- `00-vision.md` — 设计原则和约束（不可违反）
- `01-architecture-baseline.md` — 架构基线（核心对象职责契约）
- `02-execution-model.md` — 执行模型（双循环、Hook 生命周期）
- `nop-ai-agent-roadmap.md` — **本文件**（开发路线）
- `nop-ai-agent-react-engine.md` — ReAct 引擎详细设计
- `nop-ai-agent-session-engine.md` — 会话引擎详细设计
- `nop-ai-agent-context-model.md` — 上下文治理详细设计
- `nop-ai-agent-llm-layer.md` — LLM 层详细设计
- `nop-ai-agent-usage-and-billing.md` — 用量追踪与按模型计费设计（L2-17~L2-22）
- `nop-ai-agent-reliability.md` — 可靠性增强详细设计
- `nop-ai-agent-security-and-permissions.md` — 安全权限详细设计
