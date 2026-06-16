# 211 nop-ai-agent 目标跟踪器（IGoalTracker + NoOpGoalTracker + SessionGoalTracker）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-3
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3（L3-3 ❌ 未实现，line 212）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §2（故障模型 #4 行为故障：循环调用同一工具、持续产出无效动作）+ §5.3（循环检测：调用签名 `toolName + normalizedArgs`，软提示/硬中断两级处理）+ §5.4（reliability 包归属：L3-3 goal tracker 放入 `io.nop.ai.agent.reliability`）+ §10（推荐实施顺序 #5 循环检测）
> Related: `210`（`ICircuitBreaker` 模型级连续故障熔断，与本计划的 session 级行为故障检测正交）、`207`（`IRetryPolicy` 调用级瞬态故障重试，同样正交）、A2（`ICompletionJudge` turn 级完成判定，与本计划的 session 级进度跟踪互补）

## Purpose

把 nop-ai-agent 引擎对 agent 行为故障（设计 §2 #4：循环调用同一工具、持续产出无效动作）的处理从"仅依赖 `maxIterations` 硬上限无差别截断"收敛为"按可插拔 `IGoalTracker` 决策检测 stuck/looping 模式"。本计划交付目标跟踪契约（接口 + 评估结果类型）、shipped 默认 `NoOpGoalTracker`（恒报告 PROGRESSING，行为零变化）与功能性 `SessionGoalTracker`（滑动窗口追踪 tool-call 签名重复，检测无进展循环），并把进度评估接线到 ReAct 循环的 per-iteration 边界。

模块定位为"面向大规模无人值守自动化执行"。设计 §2 明确列出行为故障为五类核心故障之一，§10 推荐实施顺序将循环检测列为第 5 优先项。当前引擎对 stuck agent 的唯一保护是 `maxIterations`（`AgentExecutionContext:28/47`，默认 10）——agent 可能在 10 轮内反复调用同一工具同一参数而不触发任何告警或提前终止。目标跟踪器填补这一 Layer 3 可靠性缺口，使无人值守场景下 stuck agent 可被检测并提前 escalate，而非静默耗尽全部迭代预算。

## Current Baseline

基于 live repo 与设计文档核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IGoalTracker` 及全部相关类型在 nop-ai-agent 中零实现**：grep `IGoalTracker|GoalTracker|GoalAssessment|NoOpGoalTracker|SessionGoalTracker` 全模块零命中（仅 roadmap §4 line 212 标 L3-3 ❌ + reliability 设计 §5.4 line 308 提及包归属）。
- **reliability 设计文档有 §5.3 循环检测作为设计锚点**：`nop-ai-agent-reliability.md` §5.3（line 279）提出"调用签名做检测：`toolName + normalizedArgs`，处理方式分两级：软提示 / 硬中断"——这是 SessionGoalTracker 签名检测策略的直接设计来源。但 §5.3 是概念级描述（无接口签名、无接线点、无数据载体），本计划把 §5.3 的概念规格化为完整 `IGoalTracker` 契约。§2 故障模型 #4 + §5.4 包归属 + §10 #5 是补充锚点。Phase 1 的文档更新裁定为**改写 §5.3** 为 `IGoalTracker` 契约小节（记录最终设计：接口签名 + GoalAssessment 枚举 + NoOp 默认 + SessionGoalTracker 策略 + 接线点），而非新增并列小节（避免概念碎片化）。
- **ReAct 循环已有 per-iteration 检查链**（`ReActAgentExecutor.java:741` `while (ctx.getCurrentIteration() < ctx.getMaxIterations())`）：循环顶部依次检查 `denialLedger.isPaused`（:756）、`shouldForceStop`（:761，上下文溢出）、`shouldTriggerCompaction`（:766）、`PRE_REASONING` hook（:770）、input guardrail（:776）、budget snapshot（:801，plan 206）、model route（:808）。循环底部在 LLM 响应后检查 `if (!assistantMsg.hasToolCalls())`（:1125）——有 tool calls 走 dispatch 分支（:1198），无 tool calls 走 completion judge（:1126，turn 级完成判定），两者互斥。目标跟踪器的接线点：`recordIteration` 在 LLM 响应后、`if (!hasToolCalls)` 前（单一调用点覆盖两种场景），`assessGoal` 在循环顶部（force-stop 后）。
- **`maxIterations` 是当前唯一 stuck 保护**：`AgentExecutionContext.java:28/47` 默认 10，可通过 `constraints.getMaxIterations()` 覆盖（:63-64）。agent 在 maxIterations 内反复调用同一工具同一参数不触发任何告警——`consecutiveContinues` 死循环保护（:1134，`DEFAULT_MAX_COMPLETION_CONTINUES`）只覆盖 completion judge 的 Continue 决策（no-tool-call 场景），不覆盖 tool-call 循环。
- **可靠性组件装配范式已成熟**：`DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 将可靠性/扩展组件透传给 `ReActAgentExecutor.Builder`。L3-1 `ICircuitBreaker`（:160 field / :782 setter / :1656 resolveExecutor）、L3-2 `IRetryPolicy`（:153 / :762 / :1655）、L3-4 `ICheckpointManager` 等均遵循此模式。L3-3 `IGoalTracker` 遵循同一装配模式。
- **reliability 包归属已定**：设计 §5.4 明确"可靠性增强（L3-1 circuit breaker / L3-2 retry / L3-3 goal tracker / L3-4 checkpoint / L3-8 sustainer）放入 `io.nop.ai.agent.reliability` 包"。L3-1/L3-2/L3-4 已落地于该包（25 个文件），L3-3 遵循同一包边界。
- **tool-call 签名检测有先例**：设计 §2.5 Tool-Call Repair 的 storm 阶段（plan L2-2 `ChainRepairer` 4-stage pipeline）已实现"滑动窗口追踪最近调用，相同 (name, args) ≥ 3 次 → 抑制"的去重逻辑。但 storm 是 tool-dispatch 级别的去重（抑制重复 tool call 的执行），不是 session 级别的 stuck 检测（评估整体进度趋势）。两者正交：storm 在单次 dispatch 内抑制重复调用，goal tracker 在 session 级别评估跨迭代的进度模式。
- **`NopAiAgentException` 已是 `NopException` 子类**（plan 196 ✅）：STUCK 评估触发 abort/escalate 的异常可直接使用模块异常类。
- **`AgentSession`（L1-10 ✅）是依赖项**：`AgentSession.java` 提供 sessionId、messages、totalIterations、totalTokensUsed、status 等字段。目标跟踪器 per-session 追踪状态以 sessionId 为 key（同 ICircuitBreaker per-model-key、IDenialLedger per-session 模式）。
- **roadmap §4**：`L3-3 | IGoalTracker 接口 + NoOpGoalTracker + SessionGoalTracker | L1-10 | ❌`（line 212）。本计划关闭这一行。

## Goals

- `IGoalTracker` 契约（接口 + `GoalAssessment` 评估结果类型 + `IterationSnapshot` reliability-local 数据载体）落地于 `io.nop.ai.agent.reliability` 包，语义为"session 级别进度跟踪 + 行为故障（stuck/looping）检测"。
- `NoOpGoalTracker` 作为 shipped 默认（恒返回 `PROGRESSING`，不追踪任何状态，零行为回归），注入后引擎行为与当前完全一致。
- `SessionGoalTracker` 作为功能性 opt-in 实现：per-session 滑动窗口追踪 tool-call 签名（toolName + normalizedArgs），检测无进展循环（窗口内相同签名重复达阈值 → STUCK），可配置窗口大小与重复阈值。
- 进度评估接线到 ReAct 循环 per-iteration 边界：每轮 LLM 响应后（`assistantMsg` 生成后、`if (!hasToolCalls)` 分支前）调用 `recordIteration` 更新追踪状态，在下一轮迭代开始前（循环顶部 force-stop 后）调用 `assessGoal` 检查是否 STUCK。STUCK 时 ReAct 循环 abort（设 status 为 escalated），而非静默继续耗尽迭代预算。
- `DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 装配 goalTracker（默认 `NoOpGoalTracker`），与既有可靠性组件装配模式一致。
- 受影响既有测试零回归（默认 NoOpGoalTracker = 行为不变）。
- roadmap §4 L3-3 行从 ❌ → ✅ 并标注本 plan。
- reliability 设计文档 §5.3 改写为 L3-3 IGoalTracker 小节（记录最终设计契约 + SessionGoalTracker 策略，遵循 Minimum Rules #14 只写最终状态）。

## Non-Goals

- **goal 理解 / 目标分解 / 子目标追踪**：本计划的"目标跟踪"是**行为层面的进度检测**（agent 是否在重复无效动作），不是**语义层面的目标理解**（agent 是否理解了任务目标、子目标完成度如何）。语义层目标理解需要 LLM 参与判断（类似 LlmCompletionJudge），是独立 successor。
- **LLM-based progress assessment**：`SessionGoalTracker` 首版使用 tool-call 签名重复检测（程序化、确定性、零额外 LLM 调用成本）。LLM-based 进度评估（调用便宜模型判断"agent 是否在取得进展"）引入额外 LLM 成本 + 延迟，是独立 successor（类比 RuleBasedCompletionJudge vs LlmCompletionJudge 的分层）。
- **per-tool 级别 stuck 检测**：设计 §2.5 storm 阶段已覆盖 tool-dispatch 级别的重复调用去重。本计划只覆盖 session 级别的跨迭代进度模式检测。两者正交。
- **持久化 goal tracking 状态**：`SessionGoalTracker` 的状态是 in-memory（per-tracker-instance，per-session）。跨进程共享 / DB-backed goal tracking 状态是独立 successor。
- **自动 course-correction**：STUCK 评估时自动注入"你似乎在循环，请尝试不同方法"的 course-correction 消息让 agent 自我修正，是产品策略增强。本计划交付的语义是：STUCK → abort（escalate status + 事件），不自动注入修正消息。自动 course-correction 是独立 successor。
- **XDSL 配置化**：`agent.xdef` `<goal-tracker>` 元素绑定 SessionGoalTracker。本计划交付程序化装配（Builder/setter），配置化绑定是独立增强。
- **goal achievement 主动检测**：`GoalAssessment.GOAL_ACHIEVED` 作为契约值预留，但 `SessionGoalTracker` 首版只产出 PROGRESSING / STUCK（程序化检测无法判断"目标已达成"——那是语义判断，属于 LLM-based assessment successor）。`GOAL_ACHIEVED` 在接口中预留以避免未来契约变更。

## Scope

### In Scope

- `io.nop.ai.agent.reliability` 包：`IGoalTracker` 接口、`GoalAssessment` 枚举（PROGRESSING / STUCK / GOAL_ACHIEVED）、`IterationSnapshot` 数据载体、`NoOpGoalTracker` 默认实现、`SessionGoalTracker` 功能实现。
- `ReActAgentExecutor`：goalTracker 字段 + Builder 注入 + per-iteration 进度评估接线（recordIteration + assessGoal + STUCK abort 路径）。
- `DefaultAgentEngine`：goalTracker 字段 + setter + `resolveExecutor` 透传给 Builder（默认 `NoOpGoalTracker`）。
- 测试：NoOpGoalTracker 恒 PROGRESSING、SessionGoalTracker 滑动窗口检测、STUCK 阈值触发、接线后默认行为不变、端到端 stuck 检测验证。
- reliability 设计文档 §5 新增 L3-3 小节。

### Out Of Scope

- 语义目标理解 / LLM-based progress assessment / per-tool stuck 检测 / 持久化状态 / 自动 course-correction / XDSL 配置化（理由见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定 + IGoalTracker 契约 + NoOpGoalTracker 默认 + 接线（零回归）

Status: completed
Targets: `io.nop.ai.agent.reliability`（新类型）、`ReActAgentExecutor`、`DefaultAgentEngine`

- Item Types: `Decision`、`Proof`

- [x] 裁定并落档 **IGoalTracker 契约形态 + 包依赖方向**：接口含两个方法——`recordIteration(String sessionId, IterationSnapshot snapshot)` 在每轮 LLM 响应后被调用以更新追踪状态；`assessGoal(String sessionId)` 返回 `GoalAssessment` 在下一轮迭代开始前被调用以检查是否 STUCK。两方法分离的理由：(1) `recordIteration` 是写侧（更新内部状态），`assessGoal` 是读侧（查询评估），职责清晰；(2) 与 `ICircuitBreaker` 的 `recordSuccess/recordFailure`（写）+ `allowCall/getState`（读）的读写分离模式一致。**包依赖方向裁定**：`IterationSnapshot` 是 reliability-local 数据载体（同 `RetryContext`/`Checkpoint` 模式），由 engine 在调用 `recordIteration` 前从 `assistantMsg.getToolCalls()` 填充——**不**传入 `AgentExecutionContext`（engine 类型），保持 reliability 包对 engine 包的自包含（与 `ICircuitBreaker`/`IRetryPolicy`/`ICheckpointManager` 的包边界对称一致）。`IterationSnapshot` 含本轮 tool-call 签名列表（`List<String>` 形式的 `toolName:argsHash`）+ 当前迭代号，由 engine 侧构造
- [x] 裁定并落档 **`GoalAssessment` 评估结果类型**：枚举三值——`PROGRESSING`（默认，agent 正在取得进展，正常继续）、`STUCK`（检测到 stuck/looping 模式，ReAct 循环应 abort/escalate）、`GOAL_ACHIEVED`（预留值，程序化检测不产出此值，为 LLM-based successor 预留契约空间）
- [x] 裁定并落档 **接线点 + STUCK abort 语义**：
  - **`recordIteration`** 放置在 LLM 响应生成 `assistantMsg` **之后**、`if (!assistantMsg.hasToolCalls())` 分支判断（:1125）**之前**。这是单一调用点，同时覆盖有/无 tool calls 两种场景。engine 侧从此轮 `assistantMsg.getToolCalls()` 提取签名构造 `IterationSnapshot` 传入。理由：(1) tool dispatch（:1162+）和 completion judge（:1125-1160）位于 `if (!hasToolCalls)` 的**互斥分支**，不存在"dispatch 后 judge 前"的单一代码位置；(2) 在分支判断前调用使 tracker 能看到 LLM 请求的所有 tool calls（request 级别），不论后续是否 dispatch；(3) 若本轮无 tool calls，`IterationSnapshot` 含空签名列表——SessionGoalTracker 内部处理"无 tool call"为无新签名（不构成 stuck 证据也不构成 progress 证据，窗口状态不变）
  - **`assessGoal`** 放置在 ReAct 循环顶部检查链中，在 `shouldForceStop`（上下文溢出，:761）**之后**、`PRE_REASONING` hook（:770）**之前**。理由：(1) force-stop 是上下文安全硬保护，优先级高于 stuck 检测；(2) stuck 检测在 hook 之前使 STUCK abort 避免 hook 副作用；(3) 与 denialLedger pause 检查（:756）同级——两者都是"governance/可靠性 abort"
  - **STUCK abort 语义**：`assessGoal` 返回 STUCK 时，设 `ctx.setStatus(AgentExecStatus.escalated)` + `ctx.setLastError("goal tracker detected stuck/looping behavior: sessionId=...")` + `break reactLoop`（与 denialLedger pause break 同级模式）。不静默继续、不吞异常信号（Minimum Rules #24）。NoOpGoalTracker 恒返回 PROGRESSING，此路径永不触发
- [x] 裁定并落档 **sessionId 为 null 时的行为**：anonymous execution（sessionId == null）时 `recordIteration` / `assessGoal` 仍被调用但 NoOpGoalTracker 无状态追踪；SessionGoalTracker 使用内部 "anonymous" 逻辑 key 追踪（或选择不追踪 anonymous——Phase 1 裁定为不追踪，直接返回 PROGRESSING，理由：anonymous execution 是测试场景，生产场景必有 sessionId）
- [x] 实现 `GoalAssessment` 枚举（PROGRESSING / STUCK / GOAL_ACHIEVED）
- [x] 实现 `IterationSnapshot` reliability-local 数据载体（含本轮 tool-call 签名列表 `List<String>` + 当前迭代号 `int iteration`）
- [x] 实现 `IGoalTracker` 接口：`recordIteration(String sessionId, IterationSnapshot snapshot)` + `assessGoal(String sessionId)` → `GoalAssessment`
- [x] 实现 `NoOpGoalTracker`（恒返回 PROGRESSING、recordIteration 为显式 no-op），singleton 模式（`noOp()` 静态工厂），语义与设计 shipped 默认一致
- [x] 在 `ReActAgentExecutor` 增加 `goalTracker` 字段 + Builder 注入点；在 LLM 响应后（`assistantMsg` 生成后、`:1125 if (!hasToolCalls)` 前）构造 `IterationSnapshot` 并调用 `recordIteration`；在循环顶部（force-stop 后）调用 `assessGoal` + STUCK abort 路径。默认 NoOpGoalTracker 使行为零变化
- [x] 在 `DefaultAgentEngine` 增加 `goalTracker` 字段 + setter + `resolveExecutor` 透传给 Builder（默认 `NoOpGoalTracker`），与既有可靠性组件装配模式一致
- [x] 编写 NoOpGoalTracker 恒 PROGRESSING / 接线后默认行为不变的 focused 测试

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IGoalTracker`/`GoalAssessment`/`IterationSnapshot`/`NoOpGoalTracker` 类/接口存在于 `io.nop.ai.agent.reliability` 包且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ReActAgentExecutor` 的 per-iteration 边界被 recordIteration + assessGoal 接线；`DefaultAgentEngine` 通过 field+setter+resolveExecutor 装配 goalTracker（默认 NoOpGoalTracker）
- [x] **接线验证**：测试断言默认 NoOpGoalTracker 下 ReAct 循环正常执行（assessGoal 不 abort、recordIteration 不改变行为）——证明接线连通且零行为回归（见 Minimum Rules #23）
- [x] **无静默跳过**：STUCK abort 路径设 escalated status + lastError（非空方法体/非吞异常/非静默返回）；NoOpGoalTracker 的 recordIteration 是显式 no-op 而非空方法体占位（见 Minimum Rules #24）
- [x] **新增功能测试**（Minimum Rules #25）：NoOpGoalTracker 恒 PROGRESSING / NoOpGoalTracker recordIteration 不改变状态——每条新行为有对应测试断言
- [x] 既有测试零回归（默认 NoOpGoalTracker = 行为不变）：`./mvnw test -pl nop-ai/nop-ai-agent` 通过
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.3 改写为 IGoalTracker 契约小节（记录最终设计：接口签名 + IterationSnapshot + GoalAssessment 枚举 + NoOp 默认 + 接线点；SessionGoalTracker 策略在 Phase 2 落地后追加）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SessionGoalTracker 功能实现 + 端到端 stuck 检测验证

Status: completed
Targets: `io.nop.ai.agent.reliability`（`SessionGoalTracker`）、`ReActAgentExecutor`（stuck abort 行为）、测试

- Item Types: `Proof`

- [x] 实现 `SessionGoalTracker`：per-session 滑动窗口追踪 tool-call 签名——每轮 `recordIteration` 从传入的 `IterationSnapshot` 获取本轮 tool-call 签名列表（engine 侧已从 `assistantMsg.getToolCalls()` 提取并计算为 `toolName:argsHash` 字符串），维护 per-session `LinkedHashMap`（滑动窗口，默认 windowSize=5）。窗口内相同签名重复达 `stuckThreshold`（默认 3）→ 下次 `assessGoal` 返回 STUCK
- [x] 裁定并落档 **tool-call 签名归一化策略**：签名 = `toolName + ":" + stableArgsHash(args)`。`stableArgsHash` 对 args JSON 做 key 排序后取 hash（避免 key 顺序差异导致签名不同）。窗口大小 `windowSize`（默认 5）与重复阈值 `stuckThreshold`（默认 3）为构造器参数（不引入 XDSL 配置化）
- [x] 裁定并落档 **"无 tool call" 轮次的处理**：agent 本轮无 tool calls（进入 completion judge 分支）时，`recordIteration` 不添加新签名到窗口——不构成 stuck 证据也不构成 progress 证据。窗口状态保持不变
- [x] 实现 SessionGoalTracker 线程安全：多 session 并发调用同一 tracker 实例时，per-session 状态独立（`ConcurrentHashMap<String, SessionState>`，per-session `SessionState` 含滑动窗口 + 计数器）
- [x] 验证配置 SessionGoalTracker 时：相同 tool-call 签名重复达阈值 → assessGoal 返回 STUCK → ReAct 循环 abort（escalated status）
- [x] 编写 SessionGoalTracker 各路径的 focused 测试 + 一个端到端测试（配置 SessionGoalTracker，模拟 agent 反复调用同一工具同一参数 → 达阈值 → assessGoal 返回 STUCK → ReAct 循环 abort 为 escalated，断言迭代次数 < maxIterations + status = escalated）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SessionGoalTracker` 存在且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `ReActAgentExecutor` 的 ReAct 循环入口出发，经反复同签名 tool-call → 达 stuckThreshold → assessGoal 返回 STUCK → 循环 abort（status=escalated，迭代次数 < maxIterations），完整路径跑通
- [x] **接线验证**（Minimum Rules #23）：测试断言在 SessionGoalTracker 下相同签名重复后 assessGoal 确实返回 STUCK（不是 PROGRESSING），证明 tracker 确实被 ReAct 循环 per-iteration 边界在运行时消费
- [x] **新增功能测试**（Minimum Rules #25）：PROGRESSING（新签名出现）/ STUCK（同签名重复达阈值）/ 窗口滑动（旧签名移出后不再 STUCK）/ 无-tool-call 轮次不影响评估 / 多 session 独立——每条路径有对应断言
- [x] **无静默跳过**（Minimum Rules #24）：STUCK abort 时设 escalated status + lastError（非静默继续）；SessionGoalTracker 的签名检测是真实计算（非 placeholder）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent` 通过（含既有测试零回归）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.3 追加 SessionGoalTracker 策略描述（滑动窗口 + 签名归一化 + 阈值默认值）
- [x] roadmap §4 L3-3 行从 ❌ → ✅ 并标注本 plan
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IGoalTracker` 契约 + `IterationSnapshot` 数据载体 + `NoOpGoalTracker` 默认 + `SessionGoalTracker` 功能实现全部落地于 `io.nop.ai.agent.reliability`
- [x] 进度评估（recordIteration + assessGoal）在运行时确实被 ReAct 循环 per-iteration 边界消费（不只类型存在）
- [x] 默认 NoOpGoalTracker 下既有行为零回归
- [x] 必要 focused verification（NoOpGoalTracker 恒放行 / SessionGoalTracker 各路径 / 端到端 stuck 检测 / 线程安全）已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（语义目标理解 / LLM-based assessment / per-tool 检测 / 持久化 / 自动 course-correction / XDSL 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-reliability.md` §5、roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）goalTracker 被 ReAct 循环 per-iteration 边界在运行时调用（不只是字段存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；语义目标理解 / LLM-based progress assessment / per-tool stuck 检测 / 持久化状态 / 自动 course-correction / XDSL 配置化 / GOAL_ACHIEVED 主动检测均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **LLM-based progress assessment**：调用便宜模型判断"agent 是否在取得进展"（类比 LlmCompletionJudge），替代或补充 SessionGoalTracker 的程序化签名检测。Classification: successor plan required。
- **自动 course-correction**：STUCK 评估时自动注入 course-correction 消息让 agent 自我修正（而非直接 abort/escalate）。Classification: successor plan required。
- **语义目标理解**：基于 LLM 的目标分解 / 子目标完成度追踪。Classification: successor plan required。
- **持久化 goal tracking 状态**：DB-backed / 跨进程共享 stuck 检测状态。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` `<goal-tracker>` 元素绑定 SessionGoalTracker。Classification: optimization candidate。
- **动态阈值校准**：基于 session 长度、工具数量自适应调整窗口大小与重复阈值。Classification: optimization candidate。

## Closure

Status Note: Plan 211 关闭。`IGoalTracker` 契约（`recordIteration` 写侧 + `assessGoal` 读侧，读写分离对称 ICircuitBreaker）+ `GoalAssessment`(PROGRESSING/STUCK/GOAL_ACHIEVED) + `IterationSnapshot` reliability-local 数据载体 + `NoOpGoalTracker` shipped 默认（恒 PROGRESSING 零回归）+ `SessionGoalTracker` 功能实现（per-session 滑动窗口 tool-call 签名重复达阈值→STUCK）全部落地于 `io.nop.ai.agent.reliability` 包。接线到 ReAct 循环 per-iteration 边界（recordIteration 在 LLM 响应后 if(!hasToolCalls) 前，assessGoal 在 forceStop 后 PRE_REASONING 前，STUCK→escalated status + lastError + break）经独立 closure audit 验证为运行时真实消费（非空壳）。DefaultAgentEngine 通过 field+setter+resolveExecutor 装配（默认 NoOpGoalTracker）。独立审计 APPROVED，无 blocker。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 explore subagent（fresh session，task_id ses_13204ca00ffe4OEmmR9ult30t0）
- Audit Session: ses_13204ca00ffe4OEmmR9ult30t0
- Evidence:
  - Exit Criterion（Phase 1）全部 PASS：契约类型 `GoalAssessment`/`IterationSnapshot`/`IGoalTracker`/`NoOpGoalTracker` 存在于 reliability 包（`GoalAssessment.java:26-30`、`IterationSnapshot.java:35-57`、`IGoalTracker.java:53-77`、`NoOpGoalTracker.java:32-59`）；NoOpGoalTracker recordIteration 为显式 no-op（6 行注释，非空 `{}` 占位）
  - Exit Criterion（Phase 2）全部 PASS：`SessionGoalTracker`（`SessionGoalTracker.java:47-155`）滑动窗口 addLast/removeFirst 淘汰、no-tool-call 不改窗口、assessGoal 计数达阈值 STUCK、null sessionId PROGRESSING、ConcurrentHashMap + per-session synchronized 线程安全
  - 接线验证（Anti-Hollow）：recordIteration 真实调用 `ReActAgentExecutor.java:1194-1196`（LLM 响应后、`if(!hasToolCalls)` :1198 前），assessGoal 真实调用 `:818`（shouldForceStop :800-803 后、PRE_REASONING 前），STUCK 分支 `:819-822` 调 handleGoalStuck(`:1767-1779` 设 escalated + 非空 lastError "stuck/looping" + event + warn) + break reactLoop。handleGoalStuck / buildToolCallSignatures(`:1790-1804` name+sortedArgsJson) 均为真实逻辑
  - DefaultAgentEngine：field `:169` 默认 NoOp、setGoalTracker null-safe `:812-814`、resolveExecutor `.goalTracker(this.goalTracker)` `:1687`
  - 端到端验证（Minimum Rules #22）：`TestSessionGoalTrackerEndToEnd.repeatingSameToolCallAbortsAsEscalatedBeforeMaxIterations` 从 `ReActAgentExecutor.execute()` 入口跑通——断言 callCount==3、iterations<maxIterations、status=escalated、error 含 "stuck"、tracker.assessGoal==STUCK
  - 接线验证（Minimum Rules #23）：`TestGoalTrackerWiring` RecordingGoalTracker 断言 recordCount>=1 + assessCount>=1（运行时消费证明）
  - 无静默跳过（Minimum Rules #24）：STUCK abort 设 escalated+lastError（非静默继续）；NoOpGoalTracker recordIteration 显式 no-op（非空占位）；SessionGoalTracker 计数真实计算
  - 零回归控制：`defaultNoOpTrackerLetsLoopRunToCompletionWithoutStuckAbort` 同循环场景 NoOp 默认 status=completed（证明 abort 由 SessionGoalTracker 触发而非无关 guard）
  - `node ai-dev/tools/check-plan-checklist.mjs 211 --strict` 退出码 0
  - Anti-Hollow 扫描：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（Critical/High/Medium/Low 全 0）
  - `./mvnw test -pl nop-ai/nop-ai-agent` → 1772 tests, 0 failures, 0 errors, 0 skipped（含 24 新增 goal-tracker 测试）
  - Deferred 项分类检查：语义目标理解 / LLM-based assessment / per-tool 检测 / 持久化 / 自动 course-correction / XDSL 配置化 / GOAL_ACHIEVED 主动检测均为显式 Non-Goals 独立 successor，无 in-scope live defect 被降级
  - 独立审计结论：APPROVED，无 blocker；非 blocker 观察（plan Current Baseline 引用旧行号属文档自然漂移，相对顺序正确）

Follow-up:

- no remaining plan-owned work
- 非 blocker follow-up（已在 plan Non-Blocking Follow-ups 记录）：LLM-based progress assessment、自动 course-correction、语义目标理解、持久化 goal tracking 状态、XDSL 配置化、动态阈值校准 — 均为独立 successor，不阻塞本计划关闭
