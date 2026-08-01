# nop-ai-agent PlanReplanner 设计与最小首切（W1-4）

> Plan Status: active
> Mission: nop-ai-agent-harness-evolution
> Work Item: W1-4（PlanReplanner 运行时：停滞检测 → 阶段回退/任务拆分/失败升级，幂等决策）
> Last Reviewed: 2026-08-01
> Draft Review: 两轮独立子 agent 对抗性审查（round-1 发现 3 Major + 4 Minor 已修；round-2 verdict READY FOR ACTIVE，无 Blocker/Major）。共识达成。
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md` §14.4 Replan（`:483-490`，方向 only）、§14.5 nop-task boundary（`:492-496`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.3 三级失败升级（`:717-725`，design-only，W2-3 重叠）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W1-4
> Related: 前置 plan `2026-08-01-1440-2`（W1-1/2/3 plan 运行时门控，已 completed，本计划是其显式 deferred successor）；reliability `IGoalTracker`/`SessionGoalTracker`（ReAct-loop 级停滞，可借鉴）；W2-3 三级失败升级（与本计划"失败升级"支柱重叠，需 reconcile）

## Purpose

按 W1-1/2/3 closure 裁定（"先产出 PlanReplanner 的 design 再立 plan"），**先补齐 §14.4 design**：停滞输入信号、决策契约、状态突变语义、幂等机制，以及一个被前置 plan 暴露但未解的架构缺口——**今日不存在任何 plan 执行器/阶段推进循环**（`PlanRunner.checkGate`/`PlanScheduler.getReadyTasks` 产出无人消费）。本计划产出 design 后，落地 design 所裁定的**最小首切**：plan/phase/task 级停滞检测 + 幂等 replan 决策契约（至少 ESCALATE/CONTINUE），端到端可观测。完整回退/拆分运行时延后到 successor。

> **roadmap W1-4 勾选声明**：roadmap W1-4 = "停滞检测 → **阶段回退/任务拆分**/失败升级"。本计划只交付 design + 停滞检测 + ESCALATE/CONTINUE 决策；**ROLLBACK_PHASE（阶段回退）/SPLIT_TASK（任务拆分）运行时明确延后 successor**。故本计划 closure **不勾选** roadmap `[ ] W1-4`——W1-4 完整勾选须等 ROLLBACK/SPLIT successor。closure 时须在 roadmap与 daily log 中明示此状态。

## Current Baseline

> 已逐条核对 live repo，非沿用旧文档。事实来源：本计划起草前的独立 explore 子 agent 报告。

**已落地（W1-1/2/3，plan `2026-08-01-1440-2`，核对属实）**：

- Plan DSL 声明层完整：`agent-plan.xdef`（`nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/agent-plan.xdef`）——`<gate on-fail max-retries require-explicit-verdict verdict>`（`:81-89`）、task `triggerRule`（`:96-100`，4 值）+ `dependsOn="csv-set"`（`:96`）。
- 生成模型：`AgentPlanPhase.gate`（`_AgentPlanPhase.java:45`）、`AgentPlanTaskModel.triggerRule`（`:94`）+ `dependsOn`（`:38`）、`AgentPlanGate`（onFail/maxRetries/requireExplicitVerdict/verdict/criteria）、`AgentPlanError`（attemptNumber/blocking/encounteredAt/resolvedAt/relatedTaskNo，**纯数据，无 writer**）。
- 运行时为**四个无状态 helper**（`io.nop.ai.agent.plan.runtime`）：`PlanRunner.checkGate`（`:53`，返回 `GateCheckResult`）、`PlanScheduler.getReadyTasks`（`:50`）+ `isTerminal`（`:131`）、`PlanDagBuilder.buildDag`（`:63`，调真 `GraphStepAnalyzer` 环检测 `:150`）、`AgentPlanValidator.validate`（`:41`，经 `AgentPlan.init()`→`INeedInit` 接入 xdef 加载）。
- `GateCheckResult.Outcome` = `{PASSED, RETRY, RETRY_EXHAUSTED, BLOCKED, ESCALATED, EXPLICIT_VERDICT_REQUIRED}`（`GateCheckResult.java:25-49`）。

**架构事实（阻断项，已核实）**：

- **不存在任何 plan 执行器/阶段推进循环**。`PlanRunner`/`PlanScheduler` 产出门禁裁决/就绪集，**无任何代码消费它们驱动 phase 状态推进或 task 派发**。前置 plan 的 Non-Blocking Follow-up 明列"把 checkGate/readiness 接入 agent engine 执行主循环（若引擎无现成 phase-transition hook）是后续接线工作"（`2026-08-01-1440-2.md:160`）。W1-4 的 PlanReplanner **没有宿主可挂载**。
- **plan/phase/task 级停滞检测：0 实现**。grep `stagnation|stale_task|max_aegis|dispatch_retr|phase.?rollback|taskSplit|replanner` → 仅文档命中；`replan` 仅在 security 否认层 `DenialSuggestedStep.REPLAN`（ReAct-loop 否认恢复枚举）出现，与 plan/phase/task 级 replanning 无关。`AgentPlanError.attemptNumber/blocking` 纯数据（无 writer/consumer）。
- 现有"停滞"类比是 **ReAct-loop 级**：`IGoalTracker`/`SessionGoalTracker`（`reliability/SessionGoalTracker.java:53-55,119-141`，滑动窗口重复 tool-call 签名 → STUCK）→ `ReActAgentExecutor.java:447-451` 整 session `escalated` 中止。**不作用于 plan phase/task，不回退/拆分，动作仅 escalate-and-abort-session**。
- §14.4 Replan（`nop-ai-agent-plan-dsl.md:483-490`）**只有方向**：触发"连续失败达阈值"、策略"阶段回退/任务拆分合并/失败升级"、不变量"幂等（同状态→同决策）"。**无算法、无输入信号定义、无阈值/时间窗、无状态突变语义、无决策契约、无幂等机制**。
- **freeze() 协议**：`AgentPlan extends AbstractComponentModel`（`_gen/_AgentPlan.java:752-782` 支持 `freeze()`）。任何运行时突变（回退/状态重置）须面对 freeze 协议——可变运行时副本 vs 冻结 xdef 模板是未决 design 问题。
- reliability §13.3 三级失败升级（`nop-ai-agent-reliability.md:717-725`，`max_aegis_rejections`/`stale_task_max_retries`/`max_dispatch_retries`）**design-only**，未实现，映射 roadmap **W2-3（未勾选）**。与本计划"失败升级"支柱重叠，须 reconcile 而非重复。

**真正剩余的 gap（逐项核实）**：

1. 无 plan 执行器宿主——replanner 无处挂载（前置 plan 未接的接线）。
2. plan/phase/task 级停滞信号未定义。
3. replan 决策契约（输入状态→输出决策 schema）未定义。
4. 状态突变语义（回退/拆分如何改 `AgentPlan` + freeze 协议）未定义。
5. 幂等机制（同状态→同决策；输入状态 hash、时间类信号如何处理）未定义。
6. §14.4 design 不存在——前置 plan closure 把 design 列为**硬前置**（`:147-149`："all four pillars undefined → 直接实现是猜测且不可审计"）。

## Goals

- **补齐 §14.4 design**（W1-4 实现的硬前置）：停滞输入信号、决策契约、状态突变语义（含 freeze 裁定）、幂等机制。
- **裁定 host-runtime 架构缺口**：replanner 如何挂载——在今日无 plan 执行器的前提下，选（i）建最小 plan 执行器消费 `PlanRunner`/`PlanScheduler`，或（ii）按 §14.5 迁移到 nop-task 执行层，或（iii）挂载到现有 ReAct/引擎循环；含理由。
- **reconcile §13.3 三级失败升级**（W2-3 重叠）：明确本计划与 W2-3 的边界，避免重复/冲突。
- **落地 design 裁定的最小首切**：plan/phase/task 级停滞检测 + 幂等 replan 决策契约（至少 ESCALATE/CONTINUE 决策 wired），端到端可观测。

## Non-Goals

- **完整阶段回退 / 任务拆分运行时**——决策契约由 design 定义，但 ROLLBACK_PHASE/SPLIT_TASK 的运行时实现延后到 successor plan（本计划只实现 design 裁定的最小决策子集）。
- **plan 执行迁移到 nop-task**（§14.5）——若 host-runtime 裁定选 nop-task 迁移，迁移本身是独立 successor；本计划只裁定方向 + 最小首切。
- **W2-3 三级失败升级完整实现**——本计划只 reconcile 边界；W2-3 实现属其自身 work item。
- W2（可靠性增量）、W3+ 全部。

## Scope

### In Scope

- §14.4 design 补齐（4 pillars + freeze 裁定）。
- host-runtime 架构裁定（解决"无执行器"缺口）+ §13.3/W2-3 边界 reconcile。
- 最小首切：停滞检测 + 幂等 replan 决策契约（ESCALATE/CONTINUE wired；ROLLBACK/SPLIT 契约定义但实现延后）+ 端到端验证。

### Out Of Scope

- ROLLBACK_PHASE/SPLIT_TASK 运行时实现（successor）。
- nop-task 执行层迁移（§14.5，successor）。
- W2-3 三级失败升级实现。
- W2/W3+ work items。

## Execution Plan

### Phase 1 - §14.4 design 补齐 + host-runtime 架构裁定（Decision）

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`（§14.4 `:483-490` 补齐算法；§14.5 host 裁定）、`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（§13.3 与本计划边界 reconcile）

- Item Types: `Decision`

- [ ] **裁定 host-runtime**：在今日无 plan 执行器前提下，裁定 replanner 挂载方式（i 最小 plan 执行器消费 `PlanRunner.checkGate`+`PlanScheduler.getReadyTasks` / ii §14.5 nop-task 迁移 / iii 挂载现有 ReAct/引擎循环）。含理由 + 对 Phase 2 最小首切的影响。须正视 `PlanRunner`/`PlanScheduler` 产出今日无人消费、且引擎无 phase-transition hook（iii 实际塌缩为 i）这一事实。
- [ ] **定义停滞输入信号集**：plan/phase/task 级"无进展"的可观测信号——候选：gate `RETRY_EXHAUSTED` 计数（`GateCheckResult`）、task 状态连续 N 调度周期不推进（`PlanScheduler.isTerminal`）、同一 `relatedTaskNo` 重复 `AgentPlanError`、ReAct 级 STUCK（`SessionGoalTracker`）限定到单 task。裁定采用哪个/哪些为 plan 级信号（与 ReAct 级 STUCK 区分）。
- [ ] **定义决策契约**：输入状态 → 输出决策 schema。建议 `ReplanDecision` 枚举 `{CONTINUE, ROLLBACK_PHASE, SPLIT_TASK, ESCALATE, ABORT}` + 决策载荷（目标 phase/task、理由）。定义决策的确定性边界（哪些输入决定输出）。
- [ ] **定义状态突变语义 + freeze 裁定**：ROLLBACK/SPLIT 如何改 `AgentPlan`（currentPhase/task 状态重置/插入）；裁定可变运行时副本 vs 冻结 xdef 模板（面对 `AbstractComponentModel.freeze()`）。
- [ ] **定义幂等机制 + checkpoint 交互**：同状态→同决策。定义输入状态 hash（哪些字段参与、时间类信号如何被排除或归一化以保证幂等）。**并裁定 replan 决策/状态突变与 reliability checkpoint 系统的交互**（`reliability/` 全模块是 checkpoint/append-only 驱动）：replan 决策是否入 checkpoint、崩溃/恢复后如何复现幂等决策——否则幂等只在内存成立，crash 后发散。
- [ ] **reconcile 边界**：(a) W2-3 三级失败升级（`max_aegis_rejections`/`stale_task_max_retries`/`max_dispatch_retries`，§13.3 design-only）与 replan ESCALATE 的归属；(b) security 否认层 `DenialSuggestedStep.REPLAN`（ReAct-loop 否认恢复，与 plan-phase 无关）的区分说明，避免命名/语义混淆。明确哪个归 W1-4、哪个归 W2-3，无重复/冲突。
- [ ] 回写 design §14.4（补齐 4 pillars + freeze + 幂等 + checkpoint 交互）、§14.5（host 裁定结论）；reliability §13.3 标注与 W1-4 边界。

Exit Criteria:

- [ ] design §14.4 从"方向 only"补齐为含算法规格的 design：停滞信号集、决策契约（`ReplanDecision` schema）、状态突变+freeze 裁定、幂等+checkpoint 交互均有明确结论
- [ ] host-runtime 裁定有明确结论（i/ii/iii 之一 + 理由），且正视"`PlanRunner`/`PlanScheduler` 产出今日无人消费、iii 塌缩为 i"事实
- [ ] **host 裁定已为 Phase 2 设界**（约束见下条 Phase 1 gate）：所选 host 必须使 Phase 2 在单计划内可关闭；若所选 host 的 Phase-2 实现本身是 Out-Of-Scope successor（如 nop-task 迁移），则**禁止选择该 host**；若 i/ii/iii 三选项均需超出单计划范围的执行层，则 Phase 2 须先**再拆为独立的 executor-predecessor plan**，不得直接执行
- [ ] §13.3/W2-3 + `DenialSuggestedStep.REPLAN` 边界 reconcile 有明确归属（无重复/冲突）
- [ ] 裁定可在 live repo 验证引用准确：`PlanRunner.checkGate`（`PlanRunner.java:53`）、`PlanScheduler.getReadyTasks`（`:50`）、`GateCheckResult.Outcome`（`:25-49`）、`SessionGoalTracker` STUCK（`:119-141`）、`AbstractComponentModel.freeze()`（`_gen/_AgentPlan.java:752`）路径与行号与 live 一致
- [ ] design 已 review（独立子 agent 或人）；No owner-doc update beyond design（replanner 尚未成平台用户可见 API）
- [ ] No new test required: design-only phase（Rule #25）
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase design 裁定

### Phase 2 - 最小首切：停滞检测 + 幂等 replan 决策（design-gated）

Status: planned
Targets: 依 Phase 1 host 裁定落点（`io.nop.ai.agent.plan.runtime` 新增执行器/检测器/replanner；或挂载现有引擎循环）；`ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`（记录落地的决策子集）

- Item Types: `Fix | Proof`

> **design-gated 且 Phase 1 设界**：本 phase 具体落点由 Phase 1 host 裁定决定，且受 Phase 1 的"host 必须使 Phase 2 单计划可关闭"gate 约束。下列项以"无论 host 如何选都必须成立"的可观测结果表述。

- [ ] 落地 Phase 1 裁定的 host-runtime 落点。**关键**：host 必须**真正推进 plan/phase/task 状态并写 `AgentPlanError`（或等价记录）**——这是停滞检测的输入源；host 不能只发合成/测试事件（否则检测器空转 = hollow）。即 host 负责消费 `PlanRunner.checkGate`/`PlanScheduler.getReadyTasks` 驱动状态机推进 + 记录错误，使停滞输入真实存在
- [ ] 停滞检测器：消费 host 推进产出的真实状态/错误记录，按 Phase 1 信号集产出结构化停滞事件（含触发信号类型 + 目标 phase/task + 计数）
- [ ] replanner：消费停滞事件 → 经 Phase 1 决策契约产出 `ReplanDecision`；本 phase 至少 wire **ESCALATE + CONTINUE**（ROLLBACK_PHASE/SPLIT_TASK 决策契约已定义，运行时实现延后 successor，未实现时按 Minimum Rules #24 抛 `UnsupportedOperationException`，不静默跳过）
- [ ] 幂等：相同停滞状态 → 相同 `ReplanDecision`（按 Phase 1 输入状态 hash）；有测试固化
- [ ] 端到端测试：加载 plan → host 真实推进 → 注入停滞（如 gate 连续 RETRY_EXHAUSTED / task 不推进，经真实状态机而非合成事件）→ 停滞检测器产出事件 → replanner 产出幂等 ESCALATE → 可观测（状态变 `escalated` / 决策载荷可断言）

Exit Criteria:

- [ ] plan/phase/task 级运行经 host 落点可观测，**host 真实推进状态 + 记录错误**（非只发合成事件），停滞事件源于真实状态（有测试构造停滞并断言事件）
- [ ] replanner 存在并消费停滞事件产出 `ReplanDecision`（至少 ESCALATE/CONTINUE wired）
- [ ] **幂等验证**：相同停滞状态重复调用 replanner → 相同决策（测试固化）
- [ ] **端到端验证**：从"加载 plan → host 真实推进 → 注入停滞 → replanner 决策"完整路径可观测（见 Minimum Rules #22）
- [ ] **接线验证**：停滞检测器在运行时确实被 host 事件驱动；replanner 确实被检测器事件调用（测试计数器/标志位 verify）
- [ ] **无静默跳过**：ROLLBACK_PHASE/SPLIT_TASK 未实现时显式抛 `UnsupportedOperationException`（非空方法体/continue/吞异常），有测试断言快速失败（Minimum Rules #24）
- [ ] design §14.4 已记录本 phase 落地的决策子集（ESCALATE/CONTINUE wired；ROLLBACK/SPLIT 契约定义 + 实现延后 successor）
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase 进展

## Closure Gates

> 本计划涉及代码 + design 变更，构建验证条目保留。

- [ ] §14.4 design 补齐（4 pillars + freeze + 幂等）+ host-runtime 裁定 + §13.3/W2-3 reconcile，均有明确结论且 review
- [ ] 最小首切成立：停滞检测 + 幂等 replan 决策（ESCALATE/CONTINUE）端到端可观测
- [ ] 无静默跳过：ROLLBACK/SPLIT 未实现时显式快速失败
- [ ] 零回归：现有 `PlanRunner`/`PlanScheduler`/`AgentPlanValidator` 行为不变；ReAct 级 `SessionGoalTracker` STUCK 行为不变（本计划新增 plan 级，不改 ReAct 级）
- [ ] design §14.4/§14.5 + reliability §13.3 已同步裁定结论
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）停滞检测器在运行时被 host 事件驱动，（b）replanner 被检测器调用并产出决策，（c）端到端从 plan 加载到 replan 决策完整连通——端到端测试 + 代码追踪
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `./mvnw compile` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### ROLLBACK_PHASE / SPLIT_TASK 运行时实现

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 本计划 Phase 1 定义其决策契约，Phase 2 只 wire ESCALATE/CONTINUE。回退/拆分涉及 DAG 节点运行时增删 + freeze 突变语义，规模超出单计划；前置 plan closure 已示 W1-4 整体过大需拆。本计划交付 design（含契约）+ 最小首切，使 successor 可基于已审计 design 直接实现。
- Successor Required: yes
- Successor Path: W1-4 successor plan（ROLLBACK/SPLIT 运行时）

### nop-task 执行层迁移（§14.5）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 若 host 裁定选 nop-task 迁移，迁移本身是独立大型 successor；本计划只裁定方向 + 最小首切（可能用最小自建执行器先行）。
- Successor Required: yes
- Successor Path: §14.5 nop-task 迁移 successor

### W2-3 三级失败升级完整实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划 Phase 1 与其 reconcile 边界；实现属 W2-3 自身 work item。
- Successor Required: yes
- Successor Path: W2 roadmap work item W2-3

## Non-Blocking Follow-ups

- 停滞信号的真机/生产观测调参（阈值、时间窗）——design 留可配置项，调参非阻塞。
- replan 决策的可观测性/指标（决策分布、停滞类型分布）——observability 增强，非阻塞。

## Closure

Status Note: <<完成或关闭时填写：design 补齐 + 最小首切成立，successor 边界明确>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence: <<每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live code path / test name；check-plan-checklist.mjs 退出码 0；scan-hollow-implementations.mjs 退出码 0>>

Follow-up:

- <<完成时填写：ROLLBACK/SPLIT successor + nop-task 迁移 successor + W2-3 / 或 no remaining plan-owned work>>
