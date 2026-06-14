# nop-ai-agent 组件分解与开发路线

> Status: active
> Updated: 2026-06-14（A2 Phase-2 LLM Judge `LlmCompletionJudge` 完成 — Plan 165；rule-based + LLM 双策略均已交付）
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
| M6 | 基础数据类型 | ⚠️ 需扩展 | `AgentExecStatus` 枚举(4 值)；`IAiMemoryStore` 仅 4 方法（缺 update/delete/readBudgeted），`AiMemoryItem` 仅 4 字段（缺 priority/tokenEstimate/pinned/checksum）——见 L1-16 |
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
| — | ICircuitBreaker 熔断 | `nop-ai-agent-reliability.md` | ❌ 未开始 |
| — | IGoalTracker 目标跟踪 | `nop-ai-agent-reliability.md` | ❌ 未开始 |
| — | ICheckpointManager 检查点 | `nop-ai-agent-reliability.md` | ✅ 已落地 |
| — | Working Memory (工具) | `01-architecture-baseline.md` §四 | ❌ 未开始 |
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
| L1-11 | 缺失的枚举类（如 L0-2 确认需要单独创建）：`AgentTaskStatus`, `AgentPlanStatus` | L0-2 | ❌ |
| L1-12 | 端到端示例：一个 `.agent.xml` + ReAct 循环 + 工具调用 | L0-1, L1-5, L1-4 | ✅ |
| L1-13 | 基础单元测试框架搭建 | 无 | ❌ |
| L1-14 | `BaseAgent` 清理（决定保留并完善 or 删除） | L1-1 | ✅ |
| L1-15 | 🔴 ISessionStore 扩展：为 fork/event/compaction/snapshot 加 default 方法（抛 UOE） | L1-10 | ✅ |
| L1-16 | 🔴 IAiMemoryStore 扩展：加 update/remove/batchAdd/readBudgeted 方法 + AiMemoryItem 补充 priority/tokenEstimate/pinned/checksum | M6 | ✅ |
| L1-17 | 🔴 IAgentEngine 扩展：加 forkSession/getSessionStatus/cancelSession default 方法 | L1-1 | ✅ |
| L1-18 | 🔴 ReActAgentExecutor Builder 模式：移除构造器链，用 Builder 替换（见 react-engine.md §3.3） | L1-5 | ✅ |
| L1-19 | 🟡 agent.xdef 加 mode 属性（react/plan/single-turn，默认 react） | 无 | ✅ |
| L1-20 | 🟡 AgentSession 补充 parentSessionId/planId/compactedAt 字段（nullable，向下兼容） | L1-10 | ✅ |
| A1 | 🌟 Budgeted Injection: AiMemoryItem 补充 priority/tokenEstimate/pinned 等字段 + IAiMemoryStore.readBudgeted() default 方法 | L1-16 | ❌ |
| A2 | 🌟 Completion Gate: ReAct 循环"无 tool calls"后加 Judge 验证点（contract: Plan 159; functional `RuleBasedCompletionJudge`: Plan 162; LLM `LlmCompletionJudge`: Plan 165） | L1-5 | ✅ |
| A3 | 🌟 PreStop/PostStop ReAct 钩子: before_tool_result_processed / after_tool_result_processed（允许重入） | L2-12 | ❌ |
| A4 | 🌟 Checkpoint Journal 格式: journal.md + snapshot.json 双文件，按 watermark 恢复 | L3-4 | ❌ |
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
| L2-13a | `IConflictStrategy` 冲突解决策略接口 + `FailFastStrategy` 默认实现 | L1-1 | ❌ |
| L2-14 | `IPermissionMatrix` 接口 + `PassThroughPermissionMatrix` | L1-6 | ✅ |
| L2-14a | Dispatch-path 咨询集成（L2-13/L2-14 → ReAct 分发路径：`ILevelHintsProducer` + channel/principal 传播 + `checkLayer2Consultation`） | L2-13, L2-14 | ✅ |
| L2-15 | Working Memory 工具实现 (read-memory/write-memory/search-memory) | L1-10, L1-5 | ❌ |
| L2-16 | Token 计数 — `ILlmDialect.estimateTokens()` (default chars/4) + Provider usage 校准 | L1-4, nop-ai-core | ✅ |

**Layer 2 验收标准**：

- [ ] 能清楚说明每个扩展如何替换 pass-through 默认
- [ ] 不把运行时假设误写成新的 DSL 字段
- [ ] ContextGovernor Pipeline 可通过 Delta 配置启用

### Layer 3: Reliability Extensions — 生产环境加固

| # | 工作项 | 依赖 | 状态 |
|---|--------|------|------|
| L3-1 | `ICircuitBreaker` 接口 + `AlwaysClosed` 默认 + `ThresholdBreaker` | L1-5 | ❌ |
| L3-2 | `IRetryPolicy` 接口 + `NoRetry` 默认 + `StandardRetryPolicy` | L1-5 | ❌ |
| L3-3 | `IGoalTracker` 接口 + `NoOpGoalTracker` + `SessionGoalTracker` | L1-10 | ❌ |
| L3-4 | `ICheckpointManager` 接口 + `NoOpCheckpoint` + `ToolExecutionCheckpoint` | L1-10 | ✅ |
| L3-5 | `IApprovalGate` 接口 + `AutoApproveGate` | L1-6 | ✅ |
| L3-6 | `IDenialLedger` 接口 + `NoOpDenialLedger` + `DBDenialLedger` + sticky-pause 恢复协议（`IAgentEngine.resumeSession`） | L1-6 | ✅ |
| L3-7 | `IPostDenialGuard` 接口 + `PassThroughPostDenialGuard` + `FingerprintPostDenialGuard` | L3-6 | ✅ |
| L3-8 | `ISustainer` 接口 + `NoOpSustainer` + `SisypheanSustainer` | 与 L3-1 互斥（设计决策：选熔断或自愈） | ❌ |
| L3-9 | `IContextCompactor` 完整 5 层管道 + `ICompressionStrategy` 扩展点 | L2-4, L2-16 | ✅ |

**Layer 3 验收标准**：

- [ ] LLM 调用故障可以区分是否自动重试
- [ ] 连续故障后系统可自动熔断
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
| L4-3 | `IMemoryAdapter` 三适配器 (Storage / Embedding / Vector) | L2-15 | ❌ |
| L4-4 | `ISkillCurator` `LLMCurator` (技能生命周期) | L2-11 | ✅ |
| L4-5 | `IMailbox` `DeferredAckMailbox` (3-phase reservation) | L1-1 | ❌ |
| L4-6 | `IContributionRegistry` 7 贡献类型 | L2-12 | ❌ |
| L4-7 | `ISandboxBackend` `DockerSandboxBackend` | L1-8 | ❌ |
| L4-8 | Actor Runtime 平台层 | L4-1 ~ L4-6 | ❌ |

**Layer 4 验收标准**：

- [ ] 多 Agent 任务可以通过 Flow / Task 组织
- [ ] 多用户可并发运行独立 Actor，租户间资源隔离
- [ ] 长任务中断后可以恢复

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
- `nop-ai-agent-reliability.md` — 可靠性增强详细设计
- `nop-ai-agent-security-and-permissions.md` — 安全权限详细设计
