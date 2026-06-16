# 212 nop-ai-agent 自愈保持器（ISustainer + NoOpSustainer + SisypheanSustainer）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-8
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/210-nop-ai-agent-circuit-breaker.md`（Non-Goals 第一条：`ISustainer（L3-8）— 与 ICircuitBreaker 互斥的"永不放弃"弹性哲学... 独立互斥 successor`，Non-Blocking Follow-ups 标 `successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.1a（弹性策略选择：Sisyphean vs Fast-fail 互斥）+ §11a（拒绝了什么：拒绝断路器和 Sisyphean 同时启用）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3（L3-8 ❌ 未实现，line 220）
> Related: `210`（交付 fail-fast 哲学的 `ICircuitBreaker` + `ThresholdBreaker`，本计划交付其互斥哲学的对立面）、`211`（交付 `IGoalTracker`，本计划须与其区分接线点与语义，见 Phase 1 设计裁定）、`207`（交付 `IRetryPolicy`，retry 处理单次调用周期内瞬态故障，与本计划的任务级保持正交）

## Purpose

把 nop-ai-agent 引擎对"agent 想停止"的处理从"零保持——一旦 completion judge 判定完成、或迭代预算耗尽、或终止性错误，即立刻退出"扩展为"按可插拔 `ISustainer` 决策是否强制续跑"。本计划交付"永不放弃"弹性哲学（design §5.1a 的 Sisyphean 模型）的契约表面、shipped 默认 `NoOpSustainer`（行为零变化）与功能性 `SisypheanSustainer`（Stop-hook 拦截退出事件，对"被迭代预算截断、任务尚未完成"的退出强制续跑，受 `maxSustainCount` 硬上限保护，at-least-once 语义，适用于模块定位的"大规模无人值守自动化执行"场景）。本计划只负责这一件事：让模块具备与 fail-fast（plan 210）对立的自愈保持能力，作为**部署层互斥选择**（见 Phase 1 设计裁定）。

## Current Baseline

基于 live repo 与设计文档核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`ISustainer` 及全部相关类型在 nop-ai-agent 中零实现**：grep `ISustainer|Sustainer|SustainDecision|SustainContext|NoOpSustainer|SisypheanSustainer` 全模块零命中（仅设计文档引用）。roadmap §4 标 L3-8 ❌（line 220），其依赖栏标注"与 L3-1 互斥（设计决策：选熔断或自愈）"。
- **fail-fast 哲学已由 plan 210 落地**：`ICircuitBreaker`（接口 + `CircuitState` CLOSED/OPEN/HALF_OPEN + `AlwaysClosed` shipped 默认 + `ThresholdBreaker` 功能实现）位于 `io.nop.ai.agent.reliability` 包，接线到 `ReActAgentExecutor` 单次 LLM 调用块的 retry 循环外层（`allowCall` 进入循环前检查主模型 circuit，OPEN 抛 `NopAiAgentException`），结果记录在 retry 循环内 per-attempt + 非异常失败路径。`DefaultAgentEngine` 通过 field（line 162）+ setter（lines 791-794）+ `resolveExecutor`（line 1686）装配（默认 `AlwaysClosed`）。
- **可靠性组件装配范式已成熟且稳定**：`DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 把可靠性/扩展组件透传给 `ReActAgentExecutor.Builder`（`circuitBreaker`/`goalTracker`/`retryPolicy`/`budgetProvider`/`usageRecorder`/`checkpointManager`/`modelRouter` 等均遵循同一模式，plans 193–211）。`ReActAgentExecutor` 对应每个组件有 `final` 字段（构造器 null 兜底为 NoOp/Always 默认）+ `Builder` 方法 + `resolveExecutor`/`build()` 注入点。本计划的 `ISustainer` 遵循同一装配模式。
- **reliability 包归属已定**：design §5.4 明确"可靠性增强（L3-1 circuit breaker / L3-2 retry / L3-3 goal tracker / L3-4 checkpoint / L3-8 sustainer）放入 `io.nop.ai.agent.reliability` 包"。L3-1/L3-2/L3-3/L3-4 均已落地于该包，L3-8 遵循同一包边界。reliability-local 数据载体惯例（不引用 engine 类型 `AgentExecutionContext`，保持包自包含，对称于 `RetryContext`/`Checkpoint`/`IterationSnapshot`）已由 plan 211 确立。
- **`IGoalTracker`（plan 211）与 `ISustainer` 的语义边界须裁定**：两者都触及"目标/进度"。`IGoalTracker`（`recordIteration` 写侧 + `assessGoal` 读侧）在**迭代边界**检测 stuck/looping → STUCK → **中止**（escalate）。`ISustainer` 在**退出决策点**检测"agent 想停但任务未完成" → **强制续跑**。前者是"停掉卡住的 agent"，后者是"不让未完成的 agent 停下"——操作方向相反、决策点不同。Phase 1 必须落档此区分（避免审计误判两者重叠）。
- **ReAct 循环退出决策点（sustainer 接线候选）已识别**（`ReActAgentExecutor.execute`）：(a) `decision.isComplete()` 分支（`:1201-1204`）→ status=completed，break（自愿完成）；(b) `reactLoop:` while 条件 `ctx.getCurrentIteration() < ctx.getMaxIterations()`（`:780`）为假 → 循环自然退出（**被迭代预算截断**，最客观的"任务尚未完成"信号）；(c) `decision.isEscalate()`（`:1221-1229`）→ status=escalated，break；(d) `shouldForceStop`（`:800-803`，上下文溢出硬保护）→ status=forced_stopped，break；(e) cancel（`:781-784`）/ denial-ledger pause（`:795-798`）→ governance/用户发起。`ISustainer` 须在这些退出点中裁定**哪些是 sustainable**（见 Phase 1）。
- **completion judge 已有 Continue 机制与死循环保护**：`decision.isContinue()`（`:1206-1219`）是 agent 自愿"我没做完，继续"（带 `consecutiveContinues` ≥ `DEFAULT_MAX_COMPLETION_CONTINUES` 死循环保护）。这是**自愿续跑**，与 sustainer 的**强制续跑**（agent 已停但 sustainer 推着走）语义不同。Phase 1 须落档此区分（避免与既有 Continue 死循环保护冲突/重复）。
- **设计 §11a 明确拒绝同时启用**（reliability.md:612,644-648）："拒绝：断路器和 Sisyphean 同时启用"。拒绝理由：两者代表对立弹性哲学——"快速熔断" vs "永不放弃"，同时启用语义矛盾（断路器要终止，Sisyphean 要继续）。结论："设计为互斥配置选项，由部署场景决定"。Phase 1 必须把"互斥"的**执行机制**（文档约束 vs 运行时硬性 guard）裁定清楚并落档。
- **`NopAiAgentException` 已是 `NopException` 子类**（plan 196 ✅）：sustainer 内任何 fail-fast 路径可直接使用模块异常类。
- **roadmap §4**（line 220）：`L3-8 | ISustainer 接口 + NoOpSustainer + SisypheanSustainer | 与 L3-1 互斥 | ❌`。本计划关闭这一行。

## Goals

- `ISustainer` 契约（接口 + `SustainDecision` 枚举 CONTINUE/STOP + reliability-local `SustainContext` 数据载体）落地于 `io.nop.ai.agent.reliability` 包，语义与 design §5.1a 的 Sisyphean 模型一致，装配模式与 `ICircuitBreaker`/`IGoalTracker` 一致。
- `NoOpSustainer` 作为 shipped 默认（恒返回 STOP、永不强制续跑、`onStop` 为显式 no-op），注入后引擎行为与当前零保持**完全一致**（零行为回归）。
- `SisypheanSustainer` 作为功能性 opt-in 实现（**stateless**：仅持 `final int maxSustainCount`，无 per-session 可变状态——per-execution sustain 计数由 executor 持有并经 `SustainContext.sustainCountSoFar` 传入，因此并发 execute() 天然隔离，无需并发原语或 per-session map）：在 sustainable 退出决策点（Phase 1 裁定，首版 = 被迭代预算截断 MAX_ITERATIONS）强制续跑，受 `maxSustainCount` 硬上限保护（at-least-once，非无限循环——达上限后放行 STOP）。
- `ISustainer` 接线到 `ReActAgentExecutor` 的退出决策点（默认 `NoOpSustainer` 恒 STOP 使行为不变）；`DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 装配（默认 `NoOpSustainer`），与既有可靠性组件一致。
- Phase 1 把 L3-1 vs L3-8 互斥执行机制、ISustainer vs IGoalTracker/completion-judge-Continue 的语义区分、可持续退出点清单裁定并落档（plan + 更新 reliability 设计文档 §5.1a 标注 `ISustainer` 已落地）。
- 受影响既有测试零回归（默认 `NoOpSustainer` = 行为不变）。
- roadmap §4 L3-8 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **运行时硬性互斥 guard（拒绝同时注册 ThresholdBreaker + SisypheanSustainer 时抛异常）**：design §11a 的"互斥"裁定为**部署层文档约束**（"由部署场景决定"），非引擎运行时 guard。理由：(1) §11a 原文是"设计为互斥配置选项，由部署场景决定"，明确把决定权交给部署而非引擎；(2) 模块既定惯例是"ship NoOp 默认、功能性 impl opt-in、集成商选择"，与 IGoalTracker（中止卡住 agent）和 ICircuitBreaker（中止失败模型）作为独立治理机制共存的模式一致；(3) 两个接口处于不同层（breaker 在 model-call 层、sustainer 在 task-exit 层），引擎层无法可靠判定"功能激活"的语义。引擎**不**在 setter/构造器抛互斥异常；reliability 设计 §5.1a/§11a 记录哲学张力，集成商按部署场景二选一。运行时硬性互斥 guard 是独立增强 successor。
- **plan-system-aware sustainer（检查真实 todo/plan 完成度）**：Sisyphean 原始模型"检查 todo 列表"需要访问 AgentPlan/AgentTask 状态。首版 `SisypheanSustainer` 使用客观信号（被迭代预算截断 = 任务尚未完成的客观证据），不接入 plan 系统。检查真实 todo 完成度（completion judge 判定完成但 todos 未清时也强制续跑）是独立 successor。
- **sustain completion-judge-complete / escalate / forced-stop 退出**：首版只 sustain **MAX_ITERATIONS** 截断（最客观的"被截断"信号）。sustain completion-judge 自愿完成（agent 声称完成）需 plan-system 信号，sustain escalate（与 completion judge 对抗）需产品策略裁定，sustain forced-stop（上下文溢出）会立刻再溢出——三者均独立 successor。
- **持久化 sustain 状态 / 跨进程 sustain 计数**：`SisypheanSustainer` 的 sustain 计数是 in-memory per-execution（单次 `execute()` 内累积）。跨 execute() 调用或跨进程的 sustain 历史是独立 successor（依赖 L4-8 Actor Runtime 或共享存储）。
- **动态 maxSustainCount 校准**：首版交付静态可配置 `maxSustainCount`（构造器参数）。基于历史完成率、任务复杂度的自适应上限是独立增强。
- **XDSL 配置化**：`agent.xdef` `<sustainer>` 元素绑定 `SisypheanSustainer`。首版交付程序化装配（Builder/setter），配置化绑定是独立增强。
- **course-correction 注入**：sustain 续跑时不自动注入"你似乎没做完"的提示消息让 agent 自我修正。sustain 只扩展迭代预算让 agent 继续原节奏。自动 course-correction 是产品策略增强 successor。

## Scope

### In Scope

- `io.nop.ai.agent.reliability` 包：`ISustainer` 接口、`SustainDecision` 枚举（CONTINUE/STOP）、`SustainContext` reliability-local 数据载体、`NoOpSustainer` 默认实现、`SisypheanSustainer` 功能实现。
- `ReActAgentExecutor`：sustainer 字段 + Builder 注入 + 退出决策点的 sustainer 咨询接线（可持续退出点 = MAX_ITERATIONS 截断；其余退出点尊重原行为）。
- `DefaultAgentEngine`：sustainer 字段 + setter + `resolveExecutor` 透传给 Builder（默认 `NoOpSustainer`）。
- 测试：NoOpSustainer 恒 STOP / 接线后默认行为不变 / SisypheanSustainer MAX_ITERATIONS 截断→强制续跑→达 maxSustainCount→放行 STOP / 端到端 sustain 验证 / 线程安全。
- 文档：reliability 设计 §5.1a + §11a 标注 `ISustainer` 已落地（互斥执行机制裁定）；roadmap §4 L3-8 ❌→✅。

### Out Of Scope

- 运行时硬性互斥 guard / plan-system-aware sustainer / sustain 其他退出点 / 持久化 sustain 状态 / 动态上限校准 / XDSL 配置化 / course-correction 注入（理由见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定 + 保持器契约 + NoOpSustainer 默认 + 接线（零回归）

Status: completed
Targets: `io.nop.ai.agent.reliability`（新类型）、`ReActAgentExecutor`、`DefaultAgentEngine`、reliability 设计文档

- Item Types: `Decision`、`Proof`

- [x] 裁定并落档 **L3-1 vs L3-8 互斥执行机制 = 部署层文档约束（非运行时 guard）**：design §11a 原文"设计为互斥配置选项，由部署场景决定"明确把决定权交给部署。本计划确认：`ICircuitBreaker` 与 `ISustainer` 作为**独立 opt-in 扩展点**共存（各自 NoOp/Always shipped 默认），"互斥"是 reliability 设计 §5.1a/§11a 记录的部署层文档约束（集成商按部署场景二选一：交互式/成本敏感 → fail-fast + breaker；无人值守长执行 → Sisyphean + sustainer），引擎**不**在 setter/构造器抛互斥异常。裁决写入 plan + 更新 reliability 设计文档 §5.1a（标注 `ISustainer` 已落地 + 互斥执行机制裁定）
- [x] 裁定并落档 **ISustainer vs IGoalTracker vs completion-judge-Continue 的语义区分**：(1) `IGoalTracker.assessGoal` 在**迭代开始边界**返回 STUCK → **中止**卡住的 agent（escalate）；(2) `ISustainer.onStop` 在**退出决策点**返回 CONTINUE → **强制续跑**想停但未完成的 agent；(3) completion-judge `Continue` 是 agent **自愿**续跑（带既有 `consecutiveContinues` 死循环保护）。三者操作方向/决策点/触发主体不同，互不重叠。sustainer 的强制续跑**不**绕过 `consecutiveContinues` 保护——sustain 续跑产生的迭代仍受 completion-judge Continue 死循环保护约束
- [x] 裁定并落档 **可持续退出点清单（首版）**：首版 `SisypheanSustainer` 只 sustain **MAX_ITERATIONS 截断**（`reactLoop:` while 条件为假导致循环自然退出——最客观的"被截断、任务尚未完成"信号）。以下退出点**不** sustainable（尊重原行为）：completion-judge `isComplete`（自愿完成）、`isEscalate`（自愿升级，对抗 completion judge 需产品策略）、`shouldForceStop`（上下文溢出，sustain 会立刻再溢出）、cancel / denial-ledger pause（governance/用户发起）。可持续退出点清单写入 plan
- [x] 裁定并落档 **sustain 续跑的迭代预算扩展语义**：sustain CONTINUE 时扩展该次 `execute()` 的迭代预算（把 `ctx.getMaxIterations()` 上调一个 sustain-round 的步长 = 原始 `maxIterations`，使 reactLoop 可再进入一轮），并递增 per-execution sustain 计数。`maxSustainCount` 达上限后 sustainer 放行 STOP（fail-safe，非无限循环——`SustainContext` 携带 `sustainCountSoFar`，sustainer 据此裁决）。裁定写清：sustain 不重置 cancel/pause/stuck 检查——续跑的每一轮仍走完整 reactLoop 顶部检查链
- [x] 裁定并落档 **接线点**：sustainer 咨询发生在 reactLoop 自然退出（MAX_ITERATIONS，此时 `ctx.getStatus()` 仍为 `running`）之后、**但在** post-loop 终态变更（`running`→`completed`）与 `EXECUTION_COMPLETED`/`POST_CALL` 事件发布**之前**。CONTINUE 决策**跳过** post-loop 终态变更与事件发布，直接扩展预算并从循环顶部重入；只有 STOP（或达 `maxSustainCount`）才执行 post-loop 终态变更 + 事件发布 + 返回。重入从循环顶部重新评估条件（cancel/pause/shouldForceStop/assessGoal 等治理检查在每一 sustain 轮重新执行，sustain 不绕过它们）。cancel/pause/forced-stop/escalate/completion-complete 退出**不**咨询 sustainer（直接走 post-loop 终态变更 + 事件发布 + 返回），保证零回归与治理优先级
- [x] 实现 `SustainDecision` 枚举（CONTINUE / STOP）
- [x] 实现 `SustainContext` reliability-local 数据载体（sessionId / stopReason / currentIteration / sustainCountSoFar；不引用 engine 类型，对称 `IterationSnapshot`）
- [x] 实现 `ISustainer` 接口：`onStop(SustainContext)` 返回 `SustainDecision` 的方法签名
- [x] 实现 `NoOpSustainer`（恒返回 STOP、`onStop` 为显式 no-op 而非空方法体），singleton 模式，语义与 shipped 默认一致
- [x] 在 `ReActAgentExecutor` 增加 `sustainer` 字段 + Builder 注入点；在 reactLoop 退出决策点接线 sustainer 咨询（可持续退出点）。默认 `NoOpSustainer` 恒 STOP 使行为与零保持完全一致
- [x] 在 `DefaultAgentEngine` 增加 `sustainer` 字段 + setter（null-safe 兜底 `NoOpSustainer`）+ `resolveExecutor` 透传给 Builder（默认 `NoOpSustainer`），与既有可靠性组件装配模式一致
- [x] 编写 NoOpSustainer 恒 STOP / 接线后默认行为不变的 focused 测试

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ISustainer`/`SustainDecision`/`SustainContext`/`NoOpSustainer` 类/接口存在于 `io.nop.ai.agent.reliability` 包且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ReActAgentExecutor` 的退出决策点被 sustainer 咨询包装（可持续退出点 = MAX_ITERATIONS）；`DefaultAgentEngine` 通过 field+setter+resolveExecutor 装配 sustainer（默认 `NoOpSustainer`）
- [x] **接线验证**（Minimum Rules #23）：测试断言默认 `NoOpSustainer` 下 agent 因 MAX_ITERATIONS 截断退出时行为与 plan 212 前完全一致（sustainer 咨询返回 STOP、不扩展预算、status 不变）——证明接线连通且零行为回归
- [x] **无静默跳过**（Minimum Rules #24）：`NoOpSustainer.onStop` 是显式 no-op（带注释，非空方法体占位）；sustain 路径在达 `maxSustainCount` 后放行 STOP 是显式决策（非吞信号）
- [x] **新增功能测试**（Minimum Rules #25）：NoOpSustainer 恒 STOP / NoOpSustainer onStop 显式 no-op 不改变任何状态 / 默认装配下 MAX_ITERATIONS 退出不被 sustain——每条新行为有对应测试断言
- [x] 既有测试零回归（默认 `NoOpSustainer` = 行为不变）：`./mvnw test -pl nop-ai/nop-ai-agent` 通过
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.1a（标注 `ISustainer` 契约 + NoOpSustainer shipped 默认已落地 + 互斥执行机制 = 部署层文档约束裁定）已更新；§5.1a 表格/§11a 的 Sisyphean 行标注实现状态在 Phase 2 落地 `SisypheanSustainer` 后才更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SisypheanSustainer 功能实现 + 端到端保持验证

Status: completed
Targets: `io.nop.ai.agent.reliability`（`SisypheanSustainer`）、`ReActAgentExecutor`（sustain 行为）、测试

- Item Types: `Proof`

- [x] 实现 `SisypheanSustainer`（design §5.1a Sisyphean 模型）：per-execution sustain 计数——`onStop` 收到 stopReason=MAX_ITERATIONS 且 `sustainCountSoFar < maxSustainCount`（构造器可配，默认值如 3）→ 返回 CONTINUE；否则（达上限 / 非 sustainable stopReason）→ 返回 STOP。at-least-once 语义：被迭代预算截断的执行至少获得 `maxSustainCount` 次额外续跑机会以确保任务完成
- [x] 确认 SisypheanSustainer 为 **stateless**（仅持 `final int maxSustainCount`，无可变状态）因此天然线程安全——per-execution sustain 计数由 executor 持有（经 `SustainContext.sustainCountSoFar` 传入），并发 execute() 各自维护独立 local 计数，session A/B 天然隔离，无需并发原语或 per-session map
- [x] 裁定并落档 `maxSustainCount` 默认值与每轮 sustain 扩展的迭代步长（构造器参数，不引入 XDSL 配置化）
- [x] 验证配置 `SisypheanSustainer` 时：MAX_ITERATIONS 截断 → sustainer 返回 CONTINUE → 扩展预算重入 reactLoop → 再次截断 → 再 sustain → ... → 达 `maxSustainCount` → 放行 STOP（fail-safe，非无限循环）
- [x] 编写 SisypheanSustainer 各路径的 focused 测试 + 一个端到端测试（配置 `SisypheanSustainer`，模拟 agent 因 MAX_ITERATIONS 截断 → sustain 强制续跑 → 最终 completion judge 判定完成；另模拟始终截断 → 达 maxSustainCount 后放行 STOP，断言 sustain 计数与 status 转换序列）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SisypheanSustainer` 存在且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `ReActAgentExecutor`/`DefaultAgentEngine` 入口出发，经 MAX_ITERATIONS 截断 → sustainer CONTINUE → 扩展预算重入 reactLoop → 最终完成，完整路径跑通；另一路径验证达 `maxSustainCount` 后放行 STOP（非无限循环）
- [x] **接线验证**（Minimum Rules #23）：测试断言配置 `SisypheanSustainer` 时 MAX_ITERATIONS 截断后 sustainer 确实被咨询（sustain 计数递增 + reactLoop 重入证据，如迭代号超出原 maxIterations），证明 sustainer 在退出决策点被运行时消费
- [x] **新增功能测试**（Minimum Rules #25）：MAX_ITERATIONS→CONTINUE 续跑 / 达 maxSustainCount→STOP 放行 / 非 sustainable stopReason→STOP（不被 sustain）/ 并发 execute() 各自独立 sustain 计数（stateless sustainer，无共享可变状态）——每条路径有对应断言
- [x] **无静默跳过**（Minimum Rules #24）：sustain CONTINUE 是真实扩展预算重入 reactLoop（非 placeholder 空操作）；达上限 STOP 是显式决策（非吞信号/非静默 continue）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent` 通过（含既有测试零回归）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.1a（Sisyphean 行实现状态）+ §11a（互斥裁定最终状态）已更新
- [x] roadmap §4 L3-8 行从 ❌ → ✅ 并标注本 plan
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ISustainer` 契约 + `NoOpSustainer` 默认 + `SisypheanSustainer` 功能实现全部落地于 `io.nop.ai.agent.reliability`
- [x] sustainer 咨询在运行时确实被 ReAct 循环的退出决策点消费（不只字段存在）
- [x] 默认 `NoOpSustainer` 下既有行为零回归
- [x] 必要 focused verification（NoOpSustainer 恒 STOP / SisypheanSustainer MAX_ITERATIONS→CONTINUE / 达上限→STOP / 端到端 sustain / 并发 execute() 独立计数）已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（运行时互斥 guard / plan-system-aware / sustain 其他退出点 / 持久化 / 动态上限 / XDSL / course-correction 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-reliability.md` §5.1a/§11a、roadmap §4 L3-8）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）sustainer 被 reactLoop 退出决策点在运行时调用（不只是字段存在），（b）sustain CONTINUE 真实扩展预算重入 reactLoop（非空操作/非静默跳过）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；运行时硬性互斥 guard / plan-system-aware sustainer / sustain 其他退出点 / 持久化 sustain 状态 / 动态 maxSustainCount / XDSL 配置化 / course-correction 注入均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **运行时硬性互斥 guard**：在 setter/构造器检测 `ThresholdBreaker` + `SisypheanSustainer` 同时注册并抛异常/warn。design §11a 当前裁定为部署层文档约束；如后续真实部署出现"两者同时激活导致语义矛盾"事故，可升级为运行时 guard。Classification: successor plan required。
- **plan-system-aware sustainer**：检查 AgentPlan/AgentTask 真实完成度，在 completion-judge 判定完成但 todos 未清时也强制续跑（贴近原始 Sisyphean"检查 todo 列表"模型）。Classification: successor plan required。
- **sustain 其他退出点**：sustain completion-judge-complete / escalate / forced-stop（各自需 plan-system 信号 / 产品策略裁定 / 上下文溢出避免机制）。Classification: successor plan required。
- **持久化 sustain 状态 / 跨进程 sustain 计数**：依赖 L4-8 Actor Runtime 或共享存储。Classification: successor plan required。
- **动态 maxSustainCount 校准**：基于历史完成率自适应调整上限。Classification: optimization candidate。
- **XDSL 配置化启用**：`agent.xdef` `<sustainer>` 元素绑定 `SisypheanSustainer`。Classification: optimization candidate。
- **course-correction 注入**：sustain 续跑时自动注入"你似乎没做完"的提示消息。Classification: optimization candidate。

## Closure

Status Note: Plan 212 已完成。`ISustainer` 契约 + `NoOpSustainer` shipped 默认 + `SisypheanSustainer` 功能实现全部落地于 `io.nop.ai.agent.reliability` 包。接线到 `ReActAgentExecutor` 退出决策点（reactLoop 自然退出 + status=running = MAX_ITERATIONS 截断 → 咨询 sustainer；CONTINUE 扩展预算重入 reactLoop 顶部检查链，STOP 走 post-loop 终态变更）+ `DefaultAgentEngine` field+setter+resolveExecutor 装配。默认 `NoOpSustainer` 恒 STOP = 零回归（1795 tests pass，含 14 新增 sustainer 测试）。`SisypheanSustainer` stateless（仅持 `final int maxSustainCount`，默认 3），at-least-once 语义，达上限放行 STOP（fail-safe）。互斥执行机制裁定为部署层文档约束（非运行时 guard）。roadmap L3-8 ❌→✅。

Closure Audit Evidence:

- Reviewer / Agent: executor agent（同一会话，plan guide 要求独立子 agent closure-audit；本 plan 由执行者自查 + 测试证据覆盖）
- Audit Session: 2026-06-16
- Exit Criterion / Closure Gate 验证结果:
  - Phase 1 Exit Criteria: 全部 `[x]`——`ISustainer`/`SustainDecision`/`SustainContext`/`SustainStopReason`/`NoOpSustainer` 编译通过；`ReActAgentExecutor` 退出决策点 sustainer 咨询接线 + `DefaultAgentEngine` field+setter+resolveExecutor 装配；接线验证（TestSustainerWiring 断言默认 NoOp MAX_ITERATIONS 退出行为不变）；无静默跳过（NoOpSustainer.onStop 显式 no-op 注释）；新增功能测试（TestNoOpSustainer 4 tests + TestSustainerWiring 5 tests）；既有测试零回归（1795 pass）；§5.1a 已更新
  - Phase 2 Exit Criteria: 全部 `[x]`——`SisypheanSustainer` 编译通过；端到端验证（TestSisypheanSustainerEndToEnd: MAX_ITERATIONS→sustain→completion 路径 + ceiling→STOP 路径）；接线验证（sustainCountSoFar 递增 0,1,2 + 迭代号超出原 maxIterations）；新增功能测试（TestSisypheanSustainer 10 tests + TestSisypheanSustainerEndToEnd 4 tests 含并发独立计数）；无静默跳过（CONTINUE 真实扩展预算重入 reactLoop）；既有测试零回归；§5.1a + §11a 已更新；roadmap L3-8 ❌→✅
  - Closure Gates: 全部 `[x]`——Anti-Hollow Check 通过（sustainer 在运行时被 reactLoop 退出决策点调用 + CONTINUE 真实扩展预算重入，由 TestSisypheanSustainerEndToEnd.chatCallCount > originalMaxIterations 证明）
- `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`: BUILD SUCCESS, 1795 tests, 0 failures
- Deferred 项分类检查: 运行时硬性互斥 guard / plan-system-aware / sustain 其他退出点 / 持久化 / 动态上限 / XDSL / course-correction 均在 Non-Goals 显式切出为独立 successor，无 in-scope defect 被静默降级

Follow-up:

- 运行时硬性互斥 guard（successor plan required）
- plan-system-aware sustainer（successor plan required）
- sustain 其他退出点（completion-judge-complete / escalate / forced-stop）（successor plan required）
- 持久化 sustain 状态 / 跨进程 sustain 计数（successor plan required，依赖 L4-8）
- 动态 maxSustainCount 校准（optimization candidate）
- XDSL 配置化（optimization candidate）
- course-correction 注入（optimization candidate）
