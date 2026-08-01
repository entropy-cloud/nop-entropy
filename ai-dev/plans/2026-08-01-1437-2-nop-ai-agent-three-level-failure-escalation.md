# nop-ai-agent 三级失败升级（W2-3）

> Plan Status: active
> Mission: nop-ai-agent-harness-evolution
> Work Item: W2-3（三级失败升级：质量失败 max_aegis_rejections / 停滞失败 stale_task_max_retries / 基础设施失败 max_dispatch_retries；单次 task attempt 内失败升级）
> Last Reviewed: 2026-08-01
> Draft Review: round-1 独立子 agent 审查（fresh session ses_04279e0bcffe6w5qSbAJeJqC6K）发现 1 Blocker（两层断连：PlanExecutor/PlanExecutionState 是 plan 层自建 host，仅测试引用，从未 wire 进生产 engine；cross-layer 聚合不可行）+ 4 Major（Decision 集缺跨层传播裁定、Decision C stall 无信号源、Phase 2 目标跨两层未 reconcile、双计数未指定抑制规则）+ 3 Minor，全部已修：新增 Decision A（层归属裁定，正视两 layer 断连事实）+ Current Baseline 补两层断连事实 + Decision C 补 stall 候选并允许 scope-reduction + Decision E 指定抑制规则 + 措辞/计数修正。
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.3（`:761-771`，方向 only，4 行表 + reconcile note）；`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` §14.4.5（`:559-565`，方向 only，7 行边界声明）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2-3
> Related: 前置 plan `2026-08-01-1905-1`（W1-4 PlanReplanner，已 completed，本计划是其显式 Deferred But Adjudicated successor——§14.4.5 边界已 reconcile："W2-3 单 attempt 内失败升级，W1-4 attempt 之上 plan 级停滞，互补不重叠"；W1-4 在自建 PlanExecutor host 落地，生产 wiring 是 §14.5 deferred successor）；前置 plan `2026-08-01-1905-2`（W2-2，已 completed，Deferred 中列 W2-3 successor）；§13.3 表 "nop 对应" 列（quality→security/guardrail，stall→hive stall，infra→ThresholdBreaker）

## Purpose

收口 W2-3：补齐**单次 task attempt 内**的失败升级能力（§14.4.5 边界）。今日 reliability 重试**不区分失败类型**——guardrail 拒绝、dispatch/工具失败、无进展都被无差别记录为 `recordError(taskNo, attempt, errorText)` 的裸 errorText，坍缩为 W1-4 `REPEATED_ERRORS` 的无差别输入。三种失败（质量/停滞/基础设施）无各自的计数器、阈值与升级动作。本计划引入三级失败模型：每级有独立计数器 + max 阈值 + 升级动作，并按 §14.4.5 裁定的边界作为 W1-4 `REPEATED_ERRORS` 信号的**分类输入源**。

**关键架构事实（决定本计划形态）**：经 round-1 审查核实，`PlanExecutor`/`PlanExecutionState`/`PlanReplanner` 是 **plan 层自建执行 host**——三者仅在 `plan/runtime/` 包内互相引用，**从未 wire 进生产 `engine/` 包**（`ReActAgentExecutor`/`AgentToolDispatcher`/`DefaultAgentEngine` 零引用）。`TaskRunner` 是 `@FunctionalInterface` 返回不透明的 `TaskOutcome(success, errorText)`，**无生产实现**（仅测试 lambda）。即"单次 task attempt"（§14.4.5 措辞）是 **plan 层概念**，与生产 ReAct dispatch 是两个断连的执行上下文，唯一桥梁是 §14.5 nop-task 迁移（W1-4 显式 deferred）。故本计划**首要裁定 W2-3 的层归属**（Decision A），其决定聚合可行性与 scope。

## Current Baseline

> 已逐条核对 live repo（独立 explore 子 agent 报告 ses_0427f888dffeiTmKJAWxDZpUOQ + round-1 审查 ses_04279e0bcffe6w5qSbAJeJqC6K）。路径相对仓库根。

**架构事实（两层断连，核对属实——本计划核心约束）**：

- `PlanExecutor`/`PlanExecutionState`/`PlanReplanner`/`StagnationDetector`/`PlanScheduler` 仅在 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/` 包内互相引用。`new PlanExecutor` **仅出现在测试**（`TestPlanExecutorEndToEnd` 等）。生产 `engine/` 包（`ReActAgentExecutor`/`AgentToolDispatcher`/`DefaultAgentEngine`）**零引用** PlanExecutor/PlanExecutionState。
- `TaskRunner` 是 `@FunctionalInterface`，`TaskOutcome run(AgentPlanTaskModel task)`（单参）返回 `TaskOutcome(success, errorText)`——**不透明单 String 边界，无失败类型**。javadoc 自述"Production wiring can delegate to the agent engine"（将来时——今日未做）。**唯一生产桥梁是 §14.5 nop-task 迁移**（W1-4 plan `:55-56,:163-168` 显式 deferred）。
- **结论**：W2-3 若想把生产 dispatch 层失败（guardrail 拒绝、tool 超时）路由到 plan 层 `recordError`，**今日无运行时通道**——须建 §14.5 桥（超本计划 scope）。Decision A 须裁定 W2-3 层归属。

**plan 层失败记录（核对属实，W2-3 的直接宿主若选 plan 层）**：

- `PlanExecutionState.recordError(taskNo, attemptNumber, errorText)`(`:176-184`) 追加 `AgentPlanError`(`id="err-{taskNo}-{attemptNumber}"`，`relatedTaskNo`，`attemptNumber`，`blocking=false`，`errorText`)。**无失败类型标注**。`countUnresolvedErrors(taskNo)`(`:191-199`) 喂 `REPEATED_ERRORS`。`resolveErrorsForTask`(`:206-216`) ROLLBACK/SPLIT 时 set resolvedAt。
- 单个 `TaskRunner` 失败（`PlanExecutor.java:195-198`）产：`incrementConsecutiveFailures`(+1)+`recordError`(一条 `AgentPlanError`)+status→pending。**每个失败是无差别 error record**。
- `StagnationDetector.java`（131 行）构造 `(staleTaskCycles, maxErrorsPerTask)`(`:43`)，plan 级信号 `TASK_STALLED`(consecutiveFailures≥staleTaskCycles)/`REPEATED_ERRORS`(unresolved≥maxErrorsPerTask)/`GATE_EXHAUSTED`。`PlanReplanner.decide`(`:69-107`)：默认 `ReplanPolicy.escalateOnly()`(`ReplanPolicy.java:73-75`，空 maps)→**每非空信号 escalate**（零回归 legacy）。**无"三级失败"概念**——`ESCALATE` 单一终态， enactment 不因触发信号不同而不同。

**生产 dispatch 层（核对属实，W2-3 的直接宿主若选 dispatch 层）**：

- 内容 guardrail（质量层，最近似 aegis 但无计数器）：`IContentGuardrail.check(direction, content, ctx)`；`GuardrailResult` 是 `public abstract class`（非 sealed，3 个 nested 变体 `PassResult`/`BlockResult(reason)`/`ModifyResult(content)`），**无计数器**。执行点：输入 `ReActAgentExecutor.java:463` block→`continue`(`:478`)；输出 `:684` block→`continue`(`:686`)。**两条 block 路径都无计数器、无升级**。shipped 默认 `NoOpContentGuardrail`。
- 安全 denial ledger（defense-in-depth，**per-session** 安全，非 per-attempt 质量）：`DefaultDenialLedger.java:44` `DEFAULT_DENIAL_THRESHOLD=3`，per-session map，`handleDenialAndCheckThreshold`（`AgentSecurityConsultation` 多个 deny 检查点）阈值超 → `ctx.setStatus(paused)`+`SESSION_PAUSED`+`DENY_AND_BREAK`。**这是 per-session 安全拒绝，非 per-attempt 质量拒绝**。
- dispatch（基础设施层，一次性）：`AgentToolDispatcher.executeAllowedCalls`(`:176-415`) per `ChatToolCall` `callTool`+`orTimeout(toolTimeoutMs)`，**超时/错误 → `errorResult` 作为正常 tool-error 给 LLM，无重试、无计数器**。构造器取 `toolTimeoutMs`，**无 retry-count**。
- **grep 全 `nop-ai/`：`aegis|AegisGuard|maxAegis` 零命中**——"aegis" 是 W2-3 前瞻命名。
- LLM-call 重试层（W2e，已落地，与 W2-3 正交）：`IRetryPolicy`/`StandardRetryPolicy`(`DEFAULT_MAX_ATTEMPTS=3`)/`LlmCallCoordinator.doLlmCallWithRetry` 三通道。**仅 LLM call**；tool/task dispatch 无等价 retry-policy 抽象。

**设计文档状态**：

- §14.4.5（`nop-ai-agent-plan-dsl.md:559-565`）**方向 only**（7 行边界声明）：W2-3=单 attempt（dispatch 层质量/停滞/基础设施）、W1-4=attempt 之上（plan/phase/task 级停滞）、关系（W2-3 失败信号是 W1-4 `REPEATED_ERRORS` 聚合输入源；W1-4 不重实现单 attempt 重试）。**无 schema、无算法、无失败分类、无集成点、无状态模型、无升级动作、无层归属裁定**。
- §13.3（`nop-ai-agent-reliability.md:761-771`）**方向 only**（4 行表 + reconcile note）："nop 对应"列（quality→security/guardrail，stall→hive stall，infra→ThresholdBreaker）是**映射提示非 wiring 规格**。

**真正剩余的 gap**：

1. **W2-3 层归属未裁定（阻断项，round-1 审查 Blocker）**：plan 层（task attempt 概念 first-class，聚合到 W1-4 同层可行，但 test-only host）vs dispatch 层（生产，但"task attempt"措辞不贴合 ReAct 迭代，聚合到 plan 层须 §14.5 桥）。今日两层断连，须 Decision A 裁定。
2. **无失败类型分类**：`recordError`/`TaskOutcome` 都无 quality/stall/infra tag。三级模型须有可分类输入。
3. **无 per-attempt 质量拒绝计数器（max_aegis_rejections）**：guardrail block 无计数器；denial-ledger 是 per-session 安全。
4. **无单 attempt 停滞检测（stale_task_max_retries）且无现成信号源（round-1 审查 Major）**：`StagnationDetector` 是 plan 级多 attempt；`goalTracker STUCK` 是 per-session 且 abort 循环。单 attempt"无进展"的语义与信号源须裁定（候选：迭代内重复相同 tool 调用/输出、attempt 内无 goal 推进、重复 guardrail block），**若无轻量信号源须允许 scope-reduction**。
5. **无 dispatch 重试计数器（max_dispatch_retries）**：tool/task dispatch 一次性失败。
6. **聚合管道双计数未指定抑制规则（round-1 审查 Major）**：若 W2-3 typed failure 经 `recordError` 同时喂 W1-4 `REPEATED_ERRORS`，单次失败会被 per-attempt 计数器与 plan 级 REPEATED_ERRORS 双计。须指定抑制/贡献规则。
7. **升级动作未定义**：阈值超后做什么？

## Goals

- **W2-3 层归属裁定（首要）**：Decision A 裁定 W2-3 宿主层（plan 层同 W1-4 host / dispatch 层生产 / 混合），正视两层断连事实，使聚合可行性与 scope 明确。
- **失败类型分类**：在选定层的记录点区分三类失败（质量/停滞/基础设施），使三级模型有可分类输入。
- **三级计数器 + 阈值 + 升级动作**：质量（`max_aegis_rejections`）、停滞（`stale_task_max_retries`）、基础设施（`max_dispatch_retries`）各有独立计数器 + max 阈值 + 升级动作。
- **§14.4.5 边界落地（按层归属）**：W2-3 三级失败信号作为 W1-4 `REPEATED_ERRORS` 分类输入源（同层则聚合管道落地；跨层则 contract 声明 + §14.5 wiring deferred），W1-4 不重实现单 attempt 重试。
- **端到端**：单 attempt 内某级失败达阈值 → 触发该级升级动作 → 经聚合（若同层）影响 plan 级决策。

## Non-Goals

- **改造 W1-4 PlanReplanner 决策空间**（CONTINUE/ESCALATE/ROLLBACK/SPLIT/ABORT 已落地）——本计划只**喂** W1-4 信号，不改 replanner 决策契约。
- **§14.5 nop-task 迁移 / 生产 wiring 桥**——若 Decision A 选 plan 层，生产 dispatch 失败到达 W2-3 的桥是 §14.5 deferred successor；本计划不建跨层桥。
- **改造 LLM-call 重试层（W2e）/ 跨 provider failover（W2-4）**——保持既有行为；W2-3 与 LLM-call 层正交。
- **改造 denial-ledger（per-session 安全拒绝）**——保持既有行为（零回归）；W2-3 质量失败是 per-attempt，与 per-session 安全正交。
- **生产调参**——阈值留可配置项。
- **完整 aegis 子系统**（60+ 攻击类型语料、AttackPlugin、Grader rubric）——那是 W5-1 GuardrailTestSuite。
- W2-1 WAIT_FOR / W1-4 / W2-2 / W2-4 / W3+ 全部。

## Scope

### In Scope

- W2-3 层归属裁定（Decision A）+ 失败类型分类（选定层）。
- 三级计数器 + max 阈值 + 升级动作（质量/停滞/基础设施各一）。
- §14.4.5 边界按层归属落地（同层聚合管道 / 跨层 contract 声明）。
- 端到端验证（选定层内）+ 零回归。

### Out Of Scope

- §14.5 nop-task 迁移 / 生产 wiring 桥（独立 successor）。
- W1-4 决策契约变更 / W2e LLM-call 重试改造 / W2-4 failover 改造 / denial-ledger 行为变更。
- aegis 攻击语料库（W5-1）。
- 生产阈值调参。
- W2-1 / W2-2 / W3+ 全部。

## Risks And Rollback

- **层归属误判（最大风险）**：若 Decision A 选错层（如选 dispatch 层但聚合须 §14.5 桥），Phase 3 端到端不可行。缓解：Decision A 须正视两层断连事实 + 给出选定层的聚合可行性判定；若跨层聚合不可行，明示 contract 声明 + §14.5 deferred（诚实裁定，非静默跳过）。
- **stall 信号源缺失**：单 attempt"无进展"若无轻量信号源，stall 级可能需 scope-reduction（降为两级）。缓解：Decision C 须给候选 + 允许 Phase 1 裁定 defer stall 级（诚实，非强推不可实现的三级）。
- **双计数**：per-attempt 计数器与 plan 级 REPEATED_ERRORS 若都计同一次失败。缓解：Decision E 指定抑制/贡献规则 + 测试断言。
- **零回归红线**：无三级配置时坍缩今日无差别行为。

## Execution Plan

### Phase 1 - design elaboration：层归属 + 失败分类 + 三级计数器 + 升级 + 聚合裁定（Decision）

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.3（`:761-771` 表 → 含层归属/分类/计数器/阈值/升级动作/集成点/状态模型的 elaboration）；`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` §14.4.5（`:559-565` 边界 → 含聚合可行性/层归属 spec）

- Item Types: `Decision`

- [ ] **Decision A：W2-3 层归属裁定（核心，round-1 审查 Blocker）**。裁定 W2-3 宿主层，正视两层断连事实（plan 层 test-only host vs 生产 dispatch 层）。候选：(i) **plan 层**（task attempt 概念 first-class：`PlanExecutionState.taskAttempts`/`TaskRunner`/`recordError`/`StagnationDetector` 同层，聚合到 W1-4 `REPEATED_ERRORS` **同层可行**，与 W1-4 host 一致；生产 dispatch 失败到达 W2-3 是 §14.5 桥 deferred）；(ii) **dispatch 层**（生产 ReAct/guardrail/dispatch，但"task attempt"措辞不贴合 ReAct 迭代，聚合到 plan 层须 §14.5 桥）；(iii) 混合。裁定须给出选定层的聚合可行性判定 + 与 §14.4.5 "task attempt"措辞的 reconcile + 生产 wiring 边界（若选 plan 层，明示生产桥是 §14.5 deferred）。回写 design §13.3 + §14.4.5。
- [ ] **Decision B：失败类型分类模型 + 记录签名策略（核心 design gap）**。裁定如何在选定层记录点区分 quality/stall/infra。plan 层候选：扩 `TaskOutcome` 带 `FailureType` / `recordError(taskNo, attempt, errorText, FailureType)` 重载 / 旁路 typed 计数器。dispatch 层候选：guardrail `BlockResult`→quality / dispatch 超时-IO→infra。控制 `PlanExecutor.java:195-198`/`AgentToolDispatcher` 调用点 + 测试改造范围（类比 W2-2 裁定 D）。回写 design §13.3。
- [ ] **Decision C：停滞失败计数器（stale_task_max_retries）+ 信号源候选 + scope-reduction 许可（round-1 审查 Major）**。裁定单 attempt 内"无进展"的语义与**信号源**。候选信号源：(i) attempt 内重复相同 tool 调用/相同输出（LLM 卡loop）；(ii) attempt 内无 goal 推进（基于 `goalTracker` 细化，但 `STUCK` 今日 per-session 且 abort）；(iii) 重复 guardrail block 序列。裁定计数器作用域（per-attempt）+ 阈值 + 升级动作。**许可**：若 Phase 1 发现无轻量信号源，裁定 defer stall 级（W2-3 降为 quality+infra 两级），诚实记录，非强推不可实现的三级。须明示与 W1-4 `TASK_STALLED`（plan 级多 attempt）的区分。回写 design §13.3。
- [ ] **Decision D：基础设施失败计数器（max_dispatch_retries）+ 重试策略 + 错误分类**。裁定选定层的 infra 重试策略——plan 层：task attempt 的 infra 错误重试（TaskRunner 失败分类，仅 infra 类重试，业务错误不重试）；dispatch 层：tool dispatch retry（类比 LLM-call `IRetryPolicy` 但 for tools）。裁定 infra 错误分类（超时/IO/连接 vs 业务错误）+ 阈值 + 升级动作。须裁定重试对上层透明性 + 无配置时零回归（行为等价：同错误消息、同上层可见结果）。回写 design §13.3。
- [ ] **Decision E：质量失败计数器（max_aegis_rejections）+ 聚合抑制规则（round-1 审查 Major）**。裁定 per-attempt 质量拒绝计数形态（与 denial-ledger per-session 安全拒绝正交，明示不重复计数）+ 阈值 + 升级动作。**聚合抑制规则**：裁定 W2-3 typed failure 与 W1-4 `REPEATED_ERRORS` 的关系——候选：(i) 抑制（触发 per-attempt 升级的失败不再喂 REPEATED_ERRORS）；(ii) 贡献（同时喂，但 REPEATED_ERRORS 计 typed 聚合非裸 count）。须指定其一 + 测试断言（避免双计数）。回写 design §13.3。
- [ ] **Decision F：升级动作统一模型 + 可配置阈值 + 层归属一致性**。裁定三级升级动作统一形态（每级超阈值后的可观测行为：重试升级/上报 plan 级/终止 attempt/fail-loud）+ 阈值配置点（构造期 policy 类比 `ReplanPolicy`/`StagnationDetector(stale,maxErrors)` 既有模式，避免 codegen 级联）。须与 Decision A 层归属一致（升级动作在选定层内可观测）。回写 design §13.3。
- [ ] **Decision G：§14.4.5 边界落地形态 + 零回归边界**。按 Decision A 层归属裁定 §14.4.5 聚合的落地形态：同层（plan 层）→ 聚合管道实现；跨层 → typed-failure contract 声明 + §14.5 wiring deferred（诚实裁定）。明示无三级配置时坍缩今日无差别行为（零回归）+ 与 denial-ledger（per-session 安全）/ StagnationDetector（plan 级）/ LLM-call 重试（W2e）的去重关系。回写 design §13.3 + §14.4.5。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] design §13.3 含 7 项裁定（A-G）结论 + 理由，从 4 行表升级为可执行规格；§14.4.5 从边界声明补齐层归属 + 聚合可行性 spec
- [ ] **裁定 A 层归属与断连事实一致**：明确选定层 + 该层聚合可行性判定（同层可行 / 跨层须 §14.5）；与 §14.4.5 "task attempt" 措辞 reconcile
- [ ] **裁定 C stall 有信号源或诚实 defer**：若有信号源给候选；若无，明示 defer stall 级降为两级（非强推）
- [ ] **裁定 E 抑制规则明确**：typed failure 与 REPEATED_ERRORS 关系指定（抑制 or 贡献）+ 可测试
- [ ] 裁定已为 Phase 2/3 设界：所选层归属 + 策略须使实现单计划可关闭
- [ ] No owner-doc update beyond design（三级失败升级尚未成平台用户可见 API）
- [ ] No new test required: design-only phase（Rule #25）
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase 裁定

### Phase 2 - 失败分类 + 三级计数器 + 升级动作（选定层内）（Fix | Proof）

Status: planned
Targets: 按 Phase 1 裁定 A/B/C/D/E/F 的落点（plan 层：`PlanExecutionState.java` recordError/计数器、`TaskRunner`/`TaskOutcome` 失败类型扩展、`PlanExecutor.java:195-198`；dispatch 层：guardrail 执行点 `ReActAgentExecutor.java:463,684`、`AgentToolDispatcher.java`；升级动作组件）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（§13.3 落地决策回写）

- Item Types: `Fix | Proof`

> **design-gated**：本 phase 落点由 Phase 1 裁定 A（层归属）决定。下列项以选定层表述。

- [ ] 按 Phase 1 裁定 B 落地失败类型分类：选定层记录点区分 quality/stall/infra（签名策略按裁定 B）。
- [ ] 按 Phase 1 裁定 E 落地质量失败计数器（`max_aegis_rejections`）+ 阈值 + 升级动作，与 denial-ledger per-session 安全拒绝正交（不重复计数）。
- [ ] 按 Phase 1 裁定 C 落地停滞失败计数器（`stale_task_max_retries`，单 attempt 作用域，信号源按裁定）+ 阈值 + 升级动作；**若 Phase 1 裁定 defer stall 级，则本项 skip 并在 Deferred 记录**（诚实，非静默跳过）。
- [ ] 按 Phase 1 裁定 D 落地基础设施失败计数器（`max_dispatch_retries`）+ 重试策略 + infra 错误分类（仅 infra 类重试，业务错误不重试）+ 阈值 + 升级动作。
- [ ] 按 Phase 1 裁定 F 统一升级动作模型 + 构造期可配置阈值（类比 `ReplanPolicy`/`StagnationDetector` 既有模式）。
- [ ] 单测：每级计数器计数正确；阈值超 → 该级升级动作可观测触发；infra 错误重试而业务错误不重试；无配置时坍缩今日行为（行为等价测试：同错误消息/同上层可见结果）。

Exit Criteria:

- [ ] 失败类型分类存在，单测断言三类失败被正确标注（quality/stall/infra；若 stall deferred 则两类）
- [ ] 三级（或裁定级数）计数器 + 阈值 + 升级动作各自由单测断言（计数 → 阈值 → 升级动作可观测）
- [ ] **质量失败与 denial-ledger 正交**：per-attempt 质量计数与 per-session 安全计数不重复（单测断言）
- [ ] **停滞失败作用域正确**（若未 defer）：单 attempt stall 与 plan 级 TASK_STALLED 区分（单测断言不同作用域）
- [ ] **infra 重试行为正确 + 行为等价零回归**：infra 错误重试、业务错误不重试；无配置时与今日一次性错误行为等价（单测断言同错误消息/同上层结果）
- [ ] **无静默跳过**：阈值超时显式升级动作（非吞掉/continue）；无配置时显式坍缩今日行为；stall defer 时显式记录（非静默 skip）
- [ ] **零回归**：无三级配置时所有失败按今日无差别处理；guardrail/denial-ledger/StagnationDetector/LLM-call 重试行为不变
- [ ] design §13.3 三级计数器 + 升级动作已回写
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase

### Phase 3 - 聚合（按层归属）+ §14.4.5 边界 + 端到端（Fix | Proof）

Status: planned
Targets: 按 Phase 1 裁定 G 的聚合形态（同层：`PlanExecutionState`/`StagnationDetector`/typed 聚合；跨层：typed-failure contract 声明）、`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`（§14.4.5 聚合 spec 回写）

- Item Types: `Fix | Proof`

> **依赖 Phase 2 三级计数器 + Decision A/G 层归属**。

- [ ] 按 Phase 1 裁定 G 落地聚合：同层（plan 层）→ typed failure 聚合管道喂 W1-4 `REPEATED_ERRORS`（按裁定 E 抑制规则，无双计数）；跨层 → typed-failure contract 声明（供 §14.5 消费）+ §14.5 wiring 明示 deferred。W1-4 不重实现单 attempt 重试（§14.4.5）。
- [ ] 端到端（选定层内）：单 attempt 内某级失败达阈值 → 该级升级动作 → 经聚合（若同层）影响 plan 级决策（REPEATED_ERRORS 触发）。
- [ ] 单测：聚合正确（按裁定 E 抑制规则，无双计数）；W2-3 信号经聚合触发 W1-4 REPEATED_ERRORS（非 W1-4 重新检测单 attempt）；W1-4 决策契约不变。

Exit Criteria:

- [ ] 聚合按裁定 G 落地：同层聚合管道存在且 typed failure 正确喂入 W1-4（按裁定 E 抑制规则，单测断言无双计数）；跨层则 typed-failure contract 已声明 + §14.5 wiring 显式 deferred（诚实裁定）
- [ ] **端到端验证（选定层内）**：单 attempt 某级失败达阈值 → 升级动作 → 聚合（若同层）→ plan 级决策受影响（Minimum Rules #22：选定层内从失败到决策完整跑通）
- [ ] **接线验证**：typed failure 确实被聚合/consumed（非死信号）；W1-4 不重新实现单 attempt 重试（Minimum Rules #23）
- [ ] **无静默跳过**：聚合真实消费 typed failure（非 no-op）；跨层 contract 声明真实（非空壳）；无 typed failure 时行为不变
- [ ] **零回归**：W1-4 决策契约不变（CONTINUE/ESCALATE/ROLLBACK/SPLIT/ABORT 行为不变）；无 typed failure 时 plan 级行为不变
- [ ] design §13.3 聚合 + §14.4.5 层归属/聚合 spec 已回写
- [ ] roadmap `[ ] W2-3` 满足勾选条件—— closure 时更新 roadmap + daily log
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase

## Closure Gates

> 本计划涉及代码 + design 变更，构建验证条目保留。

- [ ] W2-3 层归属裁定落地（Decision A），与两层断连事实一致
- [ ] 三级（或裁定级数）失败模型成立：质量/停滞/基础设施各有计数器 + max 阈值 + 升级动作，测试覆盖
- [ ] 失败类型分类成立：quality/stall/infra（或裁定子集）在记录点可区分
- [ ] §14.4.5 边界按层归属落地：同层聚合管道 / 跨层 contract 声明 + §14.5 deferred；W1-4 不重实现单 attempt 重试
- [ ] 双计数抑制规则成立（裁定 E）：per-attempt 计数与 plan 级 REPEATED_ERRORS 不双计，测试断言
- [ ] 零回归：无三级配置时坍缩今日无差别行为；guardrail/denial-ledger/StagnationDetector/PlanReplanner/LLM-call 重试行为不变
- [ ] 无静默跳过：阈值超显式升级；聚合真实消费（或跨层 contract 真实声明）；stall defer 时显式记录
- [ ] design §13.3 从 4 行表升级为含层归属/分类/计数器/阈值/升级动作/集成点/状态模型的 elaboration；§14.4.5 从边界声明补齐层归属/聚合 spec
- [ ] roadmap `[ ] W2-3` 满足勾选条件—— closure 时更新 roadmap + daily log
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）三级计数器在运行时确实计数，（b）阈值超确实触发升级动作，（c）聚合（若同层）确实喂入 W1-4 REPEATED_ERRORS 并影响 plan 级决策 / 跨层则 typed-failure contract 形态良好（well-formed）且 §14.5 successor path 已声明（无运行时 consumer 时不可断言"可消费"，仅断言 contract 完整），端到端选定层内完整连通
- [ ] `./mvnw compile` 通过（`-pl nop-ai -am`）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### §14.5 nop-task 迁移 / 生产 wiring 桥

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 若 Decision A 选 plan 层，生产 dispatch 失败到达 W2-3 的运行时桥是 §14.5 nop-task 迁移（W1-4 显式 deferred 的独立大型 successor）。W2-3 在选定层内（同 W1-4 host）成立；生产 wiring 是纯执行层 successor。
- Successor Required: yes
- Successor Path: §14.5 nop-task 迁移 successor

### aegis 攻击语料库 / GuardrailTestSuite（60+ 攻击类型）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: W5-1 GuardrailTestSuite（AttackPlugin 生成 + Grader rubric + 60+ 攻击语料）是独立 work item；W2-3 只补失败升级机制（计数+阈值+升级），不补攻击语料。质量失败计数器对任何 guardrail 实现中立。
- Successor Required: yes
- Successor Path: W5 roadmap work item W5-1

## Non-Blocking Follow-ups

- 三级阈值的生产调参（design 留可配置项）。
- 失败升级可观测性指标（各级失败分布、升级触发率、聚合 → plan 决策影响分布）。
- 若 Decision A 选 plan 层：生产 dispatch 层（ReAct guardrail/dispatch）到 plan 层 TaskRunner 的 typed-failure 桥（§14.5 范畴）。
- 若 Decision C defer stall 级：stall 信号源的后续探索（progress tracker 细化）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果：<<选定层内端到端失败→升级→聚合→决策调用链追踪>>；`scan-hollow-implementations.mjs` 退出码为 0
  - Deferred 项分类检查：<<确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
