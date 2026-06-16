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

**Layer 4 验收标准**：

- [ ] 多 Agent 任务可以通过 Flow / Task 组织
- [ ] 多用户可并发运行独立 Actor，租户间资源隔离
- [x] 长任务中断后可以恢复（plan 183 已落地：`IAgentEngine.restoreSession` + `FileBackedSessionStore` 单进程 crash/restart restore；plan 184 已落地：`IAgentEngine.restorePendingSessions` 自动扫描+批量恢复，补齐"无人值守"最后一块；plan 185 已落地：`DBSessionStore` 使任何共享 DB 的服务实例可接管恢复 session；跨进程接管锁依赖 L4-8 Actor Runtime，是独立 successor）

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
