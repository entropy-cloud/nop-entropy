# 210 nop-ai-agent 熔断器（ICircuitBreaker + AlwaysClosed + ThresholdBreaker）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-1
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/207-nop-ai-agent-llm-retry-policy.md`（Non-Goals：`ICircuitBreaker（L3-1）/ ISustainer（L3-8）— 独立的 Layer 3 successor，且 L3-1/L3-8 之间存在互斥弹性哲学设计决策须先裁定`）+ `ai-dev/plans/209-nop-ai-agent-smart-model-router.md`（Non-Goals / Non-Blocking Follow-ups：`ICircuitBreaker` 标 successor plan required）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §3.3（平台层：断路器）+ §5.1（断路器状态 CLOSED/OPEN/HALF_OPEN）+ §5.1a（弹性策略选择：Sisyphean vs Fast-fail 互斥）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3（L3-1 ❌ 未实现，line 210）
> Related: `207`（交付 `IRetryPolicy` 重试循环接线到 ReAct LLM 调用点，熔断器在 retry 循环外层保护同一调用路径）、`209`（交付 SmartModelRouter 回退链消费 `RetryDecision.FALLBACK`，熔断器 OPEN 时可触发回退）

## Purpose

把 nop-ai-agent 引擎对连续失败的模型/provider 的处理从"持续调用直到耗尽 retry 预算或整个 agent 执行终止"收敛为"按可插拔 `ICircuitBreaker` 决策熔断"。本计划交付熔断器契约（接口 + 三态状态机）、shipped 默认 `AlwaysClosed`（行为零变化）与功能性 `ThresholdBreaker`（连续失败达阈值 → OPEN 拒绝调用，冷却后 HALF_OPEN 试探，成功 → CLOSED 复位），并把熔断检查（retry 循环外层）+ 结果记录（retry 循环内 per-attempt 失败 + 循环后成功）接线到 ReAct 循环的单次 LLM 调用块。模块定位为"面向大规模无人值守自动化执行"，设计 §5.1 明确指出"当某个模型或 provider 连续失败时，继续调用它只会浪费时间和 token"——熔断器是 Layer 3 平台层降级的基础原语，与 plan 207 的重试机制互补（retry 处理单次调用周期内的瞬态故障，熔断器处理跨调用周期的连续故障模式）。

## Current Baseline

基于 live repo 与设计文档核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`ICircuitBreaker` 及全部相关类型在 nop-ai-agent 中零实现**：grep `ICircuitBreaker|CircuitBreaker|CircuitState|AlwaysClosed|ThresholdBreaker` 全模块零命中（仅设计文档引用）。roadmap §4 标 L3-1 ❌（line 210），Layer 3 验收标准 `连续故障后系统可自动熔断` 未勾选（line 226）。
- **plan 207 已交付 retry 循环接线**：`ReActAgentExecutor` 的单次 LLM 调用点（`chatService.call(request, null)`，line 824）已被 retry 循环包装（lines 821-903）：捕获异常 → `LlmErrorClassifier.classify` → `RetryContext` → `retryPolicy.shouldRetry` → RETRY/STOP/FALLBACK。`DefaultAgentEngine` 通过 field（line 151）+ setter（lines 753-759）+ `resolveExecutor`（line 1626）装配 retryPolicy（默认 `NoRetryPolicy`）。熔断器的接线点：circuit 检查在 retry 循环外层（进入循环前），失败记录在 retry 循环内 per-attempt（catch 块内），成功记录在循环后（详见 Phase 1 设计裁定）。
- **plan 209 已交付回退链消费**：`RetryDecision.FALLBACK` 分支（lines 850-896）向 `IModelRouter.getFallback(routedOptions)` 查询备选模型，有则切换模型 + 重置 attempt + 重试。`SmartModelRouter`（opt-in）维护 per-tier 有序回退链；`PassThroughModelRouter`（shipped 默认）与回退链耗尽返回 null → fail-loud。熔断器 OPEN 时的自然降级路径是触发模型回退（如果 router 配置了回退链）。
- **可靠性组件装配范式已成熟**：`DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 将可靠性/扩展组件透传给 `ReActAgentExecutor.Builder`（`retryPolicy`/`budgetProvider`/`usageRecorder`/`checkpointManager`/`modelRouter` 等，plans 193–209）。`NoOp*` / `Always*` pass-through 为 shipped 默认、功能性实现为 opt-in 的模式已是模块既定惯例。
- **reliability 包归属已定**：`nop-ai-agent-reliability.md` §5.4 明确"可靠性增强（L3-1 circuit breaker / L3-2 retry / L3-3 goal tracker / L3-4 checkpoint / L3-8 sustainer）放入 `io.nop.ai.agent.reliability` 包"。L3-2 retry / L3-4 checkpoint 已落地于该包（`IRetryPolicy`/`RetryDecision`/`ErrorClassification`/`RetryContext`/`RetryOutcome`/`NoRetryPolicy`/`StandardRetryPolicy`/`LlmErrorClassifier` + `ICheckpointManager`/`Checkpoint` 等 22 个文件），L3-1 circuit breaker 遵循同一包边界。
- **`NopAiAgentException` 已是 `NopException` 子类**（plan 196 ✅）：熔断 OPEN 时拒绝调用抛出的异常可直接使用模块异常类。
- **model-key 复合键已有先例**：plan 205/209 使用 `provider:model` 复合键做模型变更检测（`buildModelKey(routedOptions)`，ReActAgentExecutor line 882/893）。熔断器 per-model 追踪使用同一复合键语义，与既有代码一致。
- **设计 §5.1a 已裁定弹性哲学互斥**：`ICircuitBreaker`（快速熔断哲学）与 `ISustainer`（永不放弃哲学）是互斥配置选项，设计拒绝同时启用（§11a "拒绝：断路器和 Sisyphean 同时启用"）。Layer 1 默认 fail-fast（与 Nop 无人值守定位一致），Sisyphean 可选激活。本计划实现 fail-fast 哲学的 `ICircuitBreaker`，`ISustainer`（L3-8）为未来互斥 successor。
- **roadmap §4**：`L3-1 | ICircuitBreaker 接口 + AlwaysClosed 默认 + ThresholdBreaker | L1-5 | ❌`。本计划关闭这一行 + Layer 3 验收标准 `连续故障后系统可自动熔断`。

## Goals

- `ICircuitBreaker` 契约（接口 + `CircuitState` 三态枚举 CLOSED/OPEN/HALF_OPEN + 结果记录方法）落地于 `io.nop.ai.agent.reliability` 包，语义与设计 §5.1 一致。
- `AlwaysClosed` 作为 shipped 默认（恒返回 CLOSED、永不熔断、所有调用放行），注入后引擎行为与当前零熔断**完全一致**（零行为回归）。
- `ThresholdBreaker` 作为功能性 opt-in 实现：连续失败达阈值（默认可配置）→ OPEN 拒绝调用；冷却时间过后 → HALF_OPEN 放行一次试探调用；试探成功 → CLOSED 复位，试探失败 → 回到 OPEN。线程安全（多 session 并发调用同一模型时状态一致）。
- 熔断检查 + 结果记录接线到 ReAct 循环单次 LLM 调用块：circuit 检查在 retry 循环外层（进入循环前检查主模型 circuit 状态——OPEN 且不在 HALF_OPEN 试探窗口 → 拒绝调用），失败记录在 retry 循环内 per-attempt（每次 attempt 失败时 `recordFailure`，在 FALLBACK 切换 `routedOptions` 之前——保证主模型失败正确归属主模型），成功记录在循环后（最终成功模型 `recordSuccess` 复位）。另覆盖 retry 循环后的 `!response.isSuccess()` 非异常失败路径（同样 `recordFailure`）。
- `DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 装配 circuitBreaker（默认 `AlwaysClosed`），与既有可靠性组件装配模式一致。
- 受影响既有测试零回归（默认 AlwaysClosed = 行为不变）。
- roadmap §4 L3-1 行从 ❌ → ✅ + Layer 3 验收标准 `连续故障后系统可自动熔断` 勾选，并标注本 plan。

## Non-Goals

- **ISustainer（L3-8）**：与 `ICircuitBreaker` 互斥的"永不放弃"弹性哲学（设计 §5.1a）。设计 §11a 已拒绝两者同时启用。本计划实现 fail-fast 哲学（ICircuitBreaker），ISustainer 是独立互斥 successor。
- **持久化熔断状态**：`ThresholdBreaker` 的状态是 in-memory（per-breaker-instance，跨 agent execution 在同一 engine 实例内累积）。DB-backed / 跨进程共享熔断状态（如某 provider 在集群范围内被熔断）是独立 successor，依赖 L4-8 Actor Runtime 或共享存储。
- **熔断 OPEN 时自动模型回退**：熔断 OPEN 拒绝调用时，是否自动触发 `IModelRouter` 回退到 circuit-closed 的备选模型，是产品策略增强。本计划交付的语义是：circuit OPEN → 拒绝调用（抛异常 / 返回不可用信号），ReAct 循环按既有失败处理路径处理。自动回退（router 感知 circuit 状态并主动跳过 circuit-broken 模型）是独立 successor。
- **动态阈值校准**：设计 §5.1 明确"阈值和冷却时间需要真实运行数据校准"。本计划交付静态可配置阈值（构造器参数），动态自适应阈值（基于滑动窗口失败率、provider 负载信号等）是独立增强。
- **per-tool 熔断 / tool-call 级熔断**：设计 §2.5 Tool-Call Repair 的 storm 阶段是工具级去重，与模型级熔断正交。本计划只覆盖模型/provider 级熔断。
- **XDSL 配置化**：`agent.xdef` `<circuit-breaker>` 元素绑定 ThresholdBreaker。本计划交付程序化装配（Builder/setter），配置化绑定是独立增强。
- **half-open 探试探针（probe request）**：设计 §5.1 HALF_OPEN 语义是"允许一次试探调用"。本计划的 HALF_OPEN 只放行真实 ReAct LLM 调用作为试探（非独立的探针请求）。独立探针（发一个轻量请求验证 provider 恢复）是独立增强。

## Scope

### In Scope

- `io.nop.ai.agent.reliability` 包：`ICircuitBreaker` 接口、`CircuitState` 枚举（CLOSED/OPEN/HALF_OPEN）、`AlwaysClosed` 默认实现、`ThresholdBreaker` 功能实现。
- `ReActAgentExecutor`：circuitBreaker 字段 + Builder 注入 + retry 循环外层的 circuit 检查 + 结果记录接线。
- `DefaultAgentEngine`：circuitBreaker 字段 + setter + `resolveExecutor` 透传给 Builder（默认 AlwaysClosed）。
- 测试：AlwaysClosed 恒放行、ThresholdBreaker CLOSED→OPEN 阈值触发、OPEN→HALF_OPEN 冷却转换、HALF_OPEN 成功→CLOSED 复位、HALF_OPEN 失败→OPEN 回退、线程安全、接线后默认行为不变、端到端熔断验证。

### Out Of Scope

- ISustainer / 持久化熔断状态 / 自动模型回退 / 动态阈值校准 / per-tool 熔断 / XDSL 配置化 / half-open 探针（理由见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定 + 熔断契约 + AlwaysClosed 默认 + 接线（零回归）

Status: completed
Targets: `io.nop.ai.agent.reliability`（新类型）、`ReActAgentExecutor`、`DefaultAgentEngine`

- Item Types: `Decision`、`Proof`

- [x] 裁定并落档 **L3-1 vs L3-8 互斥弹性哲学设计决策**：设计 §5.1a + §11a 已裁定两者互斥（拒绝同时启用）。本计划确认实现 fail-fast 哲学（ICircuitBreaker 为默认弹性策略，ISustainer 为未来互斥 opt-in）。裁决写入 plan + 更新 reliability 设计文档 §5.1a 标注 ICircuitBreaker 已落地
- [x] 裁定并落档 **per-model 追踪语义**：熔断器按 `provider:model` 复合键（复用 `buildModelKey` 语义）独立追踪每个模型的 circuit 状态。理由：熔断模型 A 不应阻止调用健康的模型 B（回退链场景）。AlwaysClosed 默认无需追踪（恒 CLOSED）；ThresholdBreaker 维护 per-model-key 状态
- [x] 裁定并落档 **接线点 + 结果记录归属**：
  - **Circuit 检查**在 retry 循环**外层**——进入 retry 循环前检查**主模型**（进入 retry 循环时的 `routedOptions`）的 circuit 状态。理由：retry 处理单次调用周期内瞬态故障，circuit breaker 处理跨调用周期连续故障模式——两者正交分层
  - **失败记录**在 retry 循环**内部 per-attempt**——`recordFailure(当前 attempt 的 model-key)` 放置在 catch 块**入口处**（`isRetry`/`isFallback`/`isStop` 分支判断**之前**）。裁定理由：(1) 每次 attempt 失败都是模型健康状态的证据，无论该失败随后被 RETRY 还是 FALLBACK 消化；(2) 放在分支判断之前保证 RETRY-able 瞬态失败也计入 circuit 阈值——一个反复触发 retry 的不稳定模型正是 circuit 应当捕获的连续失败模式；(3) `recordSuccess` 在成功时复位计数器，因此最终成功的 retry cycle 不会留下累计负债（circuit 只在**连续失败无成功间插**时才 trip）。`recordFailure` 调用在 FALLBACK 切换 `routedOptions`（line 883）**之前**执行，保证主模型失败正确归属主模型而非切换后的 fallback 模型
  - **非异常失败路径覆盖**：retry 循环正常退出后（line 904 `response = attemptResponse`）的 `!response.isSuccess()` 分支（line 907）同样调用 `recordFailure(modelKey)`——`chatService.call` 可能返回错误响应而非抛异常，这类失败也是模型健康证据，必须计入 circuit
  - **成功记录**在 retry 循环**结束后**——记录 `recordSuccess(最终成功 attempt 的 model-key)`（可能是 fallback 模型）
  - **Circuit 检查只覆盖主模型**：进入 retry 循环前只检查主模型 circuit。FALLBACK 切换后的备选模型不做 circuit 检查（FALLBACK 本身是对失败的响应；检查备选模型 circuit 增加复杂度且无明确收益）。这是首版简化，自动 circuit-aware routing 是独立 successor
- [x] 裁定并落档 **HALF_OPEN 并发试探语义**：HALF_OPEN 采用 **lazy 进入 + 单 probe 原子占位**。具体：(1) circuit 进入 OPEN 后不启动定时器，下次 `allowCall(modelKey)` 被调用时 lazy 检查 `now - openedAt ≥ cooldownMs`，满足则转 HALF_OPEN（与模块内 lazy 惯例一致，无需后台线程）；(2) HALF_OPEN 状态下并发调用同一 model-key 时，使用内部原子标志（如 `AtomicBoolean probeInFlight`）保证**仅第一个 caller 作为 probe 放行**，后续并发 caller 看到 probe-in-flight 则被拒绝（视为仍 OPEN）。probe 完成（成功→CLOSED 复位、失败→回 OPEN）后标志复位。`CircuitState` 公开枚举保持 3 态（CLOSED/OPEN/HALF_OPEN），probe 独占性由 ThresholdBreaker 内部同步原语保证，不引入第 4 态
- [x] 裁定并落档 **circuit OPEN 时的行为**：拒绝调用，抛 `NopAiAgentException`（消息含 model-key + circuit state + 指引）。不静默返回、不吞异常（Minimum Rules #24）。ReAct 循环按既有失败处理路径处理（如触发 completion judge / 终止执行）
- [x] 实现 `CircuitState` 枚举（CLOSED / OPEN / HALF_OPEN）
- [x] 实现 `ICircuitBreaker` 接口：circuit 状态查询（给定 model-key 返回 CircuitState）+ 调用前检查（是否允许调用）+ 结果记录（成功 / 失败）方法签名
- [x] 实现 `AlwaysClosed`（恒返回 CLOSED、所有调用放行、recordSuccess/recordFailure 为 no-op），语义与设计 shipped 默认一致
- [x] 在 `ReActAgentExecutor` 增加 `circuitBreaker` 字段 + Builder 注入点；在 retry 循环外层接线 circuit 检查 + 结果记录。默认 AlwaysClosed 使行为与零熔断完全一致
- [x] 在 `DefaultAgentEngine` 增加 `circuitBreaker` 字段 + setter + `resolveExecutor` 透传给 Builder（默认 `AlwaysClosed`），与既有可靠性组件装配模式一致
- [x] 编写 AlwaysClosed 恒放行 / 接线后默认行为不变的 focused 测试

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ICircuitBreaker`/`CircuitState`/`AlwaysClosed` 类/接口存在于 `io.nop.ai.agent.reliability` 包且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ReActAgentExecutor` 的 retry 循环外层被 circuit 检查 + 结果记录包装；`DefaultAgentEngine` 通过 field+setter+resolveExecutor 装配 circuitBreaker（默认 AlwaysClosed）
- [x] **接线验证**：测试断言默认 AlwaysClosed 下 LLM 调用正常执行（circuit 检查不拒绝、结果记录不改变行为）——证明接线连通且零行为回归（见 Minimum Rules #23）
- [x] **无静默跳过**：circuit OPEN 拒绝路径抛 `NopAiAgentException`（非空方法体/非吞异常/非静默返回 null）；AlwaysClosed 的 recordSuccess/recordFailure 是显式 no-op 而非空方法体占位（见 Minimum Rules #24）
- [x] **新增功能测试**（Minimum Rules #25）：AlwaysClosed 恒 CLOSED / AlwaysClosed 所有调用放行 / recordSuccess+recordFailure 不改变状态——每条新行为有对应测试断言
- [x] 既有测试零回归（默认 AlwaysClosed = 行为不变）：`./mvnw test -pl nop-ai/nop-ai-agent` 通过
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §3.3（平台层：断路器契约 + AlwaysClosed 默认已落地）+ §5.1a（fail-fast 哲学已选 + ICircuitBreaker 契约已落地）已更新；§5.1 三态状态机的"实现状态"标注在 Phase 2 落地后才更新（Phase 1 只交付 AlwaysClosed，无功能性状态机）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ThresholdBreaker 功能实现 + 端到端熔断验证

Status: completed
Targets: `io.nop.ai.agent.reliability`（`ThresholdBreaker`）、`ReActAgentExecutor`（熔断行为）、测试

- Item Types: `Proof`

- [x] 实现 `ThresholdBreaker`（设计 §5.1）：per-model-key 状态机——CLOSED 状态下连续失败计数，达 `failureThreshold`（默认可配置，如 3）→ 转 OPEN；OPEN 状态下所有调用拒绝，经过 `cooldownMs`（默认可配置）后 → 转 HALF_OPEN；HALF_OPEN 状态下放行一次试探调用，试探成功 → CLOSED 复位（清零计数），试探失败 → 回到 OPEN（重置冷却计时）
- [x] 实现 ThresholdBreaker 线程安全：多 session 并发调用同一 model-key 时，状态转换 + 计数 + 冷却计时一致（使用并发原语，如 `AtomicInteger`/`ConcurrentHashMap`/`volatile`）
- [x] 裁定并落档阈值与冷却默认值（构造器参数，不引入 XDSL 配置化）
- [x] 验证熔断检查在配置 ThresholdBreaker 时：连续失败达阈值后拒绝调用；冷却后放行试探；试探成功复位 / 失败回 OPEN
- [x] 编写 ThresholdBreaker 各状态转换路径的 focused 测试 + 一个端到端测试（配置 ThresholdBreaker，模拟连续 LLM 调用失败 → circuit 转 OPEN → 后续调用被拒绝，断言调用计数与 circuit 状态转换序列）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ThresholdBreaker` 存在且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `ReActAgentExecutor` 的 LLM 调用入口出发，经连续失败 → circuit 转 OPEN → 后续调用被拒绝（抛 NopAiAgentException），完整路径跑通；验证冷却后 HALF_OPEN 探试探路
- [x] **接线验证**（Minimum Rules #23）：测试断言在 ThresholdBreaker 下连续失败后 circuit 状态确实为 OPEN（调用计数器 + 状态查询），证明 breaker 确实被 retry 循环外层在运行时消费
- [x] **新增功能测试**（Minimum Rules #25）：CLOSED 连续失败→OPEN（达阈值）/ OPEN 冷却→HALF_OPEN / HALF_OPEN 成功→CLOSED 复位 / HALF_OPEN 失败→OPEN 回退 / 并发调用线程安全——每条状态转换路径有对应断言
- [x] **无静默跳过**（Minimum Rules #24）：circuit OPEN 拒绝时抛异常而非静默返回 null/空响应；HALF_OPEN 试探是真实放行调用（非 placeholder）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent` 通过（含既有测试零回归）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §3.3 / §5.1 / §5.1a 已更新为实现状态
- [x] roadmap §4 L3-1 行从 ❌ → ✅ 并标注本 plan；Layer 3 验收标准 `连续故障后系统可自动熔断` 勾选
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ICircuitBreaker` 契约 + `AlwaysClosed` 默认 + `ThresholdBreaker` 功能实现全部落地于 `io.nop.ai.agent.reliability`
- [x] 熔断检查 + 结果记录在运行时确实被 ReAct 循环的 LLM 调用块消费（不只类型存在）
- [x] 默认 AlwaysClosed 下既有行为零回归
- [x] 必要 focused verification（AlwaysClosed 恒放行 / ThresholdBreaker 各状态转换 / 端到端熔断 / 线程安全）已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（ISustainer / 持久化 / 自动回退 / 动态阈值 / per-tool 熔断 / XDSL / half-open 探针均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-reliability.md` §3.3/§5.1/§5.1a、`nop-ai-agent-llm-layer.md`、roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）circuitBreaker 被 retry 循环外层在运行时调用（不只是字段存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；ISustainer / 持久化熔断状态 / 自动模型回退 / 动态阈值校准 / per-tool 熔断 / XDSL 配置化 / half-open 探针均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **ISustainer（L3-8）**：与 ICircuitBreaker 互斥的"永不放弃"弹性哲学（Stop-hook 确保任务完成，at-least-once）。设计 §5.1a + §11a 已裁定互斥。Classification: successor plan required。
- **持久化熔断状态**：DB-backed / 跨进程共享熔断状态（集群范围内某 provider 被熔断）。依赖 L4-8 Actor Runtime 或共享存储。Classification: successor plan required。
- **熔断 OPEN 时自动模型回退**：router 感知 circuit 状态并主动跳过 circuit-broken 模型，路由到 circuit-closed 的备选模型。Classification: successor plan required。
- **动态阈值校准**：基于滑动窗口失败率、provider 负载信号自适应调整阈值。Classification: optimization candidate。
- **XDSL 配置化启用**：`agent.xdef` `<circuit-breaker>` 元素绑定 ThresholdBreaker。Classification: optimization candidate。
- **half-open 独立探针**：发一个轻量请求验证 provider 恢复（非复用真实 ReAct LLM 调用作为试探）。Classification: optimization candidate。

## Closure

Status Note: Plan 210 delivered the ICircuitBreaker contract (interface + CircuitState three-state enum), the shipped AlwaysClosed pass-through default (zero-regression), and the functional ThresholdBreaker (per-model-key CLOSED/OPEN/HALF_OPEN state machine with configurable threshold + cooldown, lazy HALF_OPEN transition, single-probe exclusivity, thread-safe via ConcurrentHashMap + per-entry synchronized). The breaker is wired into the ReAct loop's single-LLM-call block at the retry-loop outer layer (circuit check before the loop, recordFailure in the catch entry + non-exception failure path, recordSuccess after success) and assembled via DefaultAgentEngine field+setter+resolveExecutor (default AlwaysClosed). The shipped default preserves the pre-plan-210 zero-circuit-breaking behaviour verbatim. All Non-Goals (ISustainer, persistence, auto-fallback, dynamic threshold, per-tool, XDSL, half-open probe) are explicitly deferred successors. Independent closure audit APPROVED all 12 verification items against live code.
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (session ses_1323720daffe7rUkkuC6GXxnfM, general type, fresh session)
- Audit Session: ses_1323720daffe7rUkkuC6GXxnfM
- Evidence:
  - Phase 1 Exit Criteria:
    - ICircuitBreaker/CircuitState/AlwaysClosed exist in `io.nop.ai.agent.reliability`: PASS (`ICircuitBreaker.java:1,51`, `CircuitState.java:1,27`, `AlwaysClosed.java:1,33`)
    - ReActAgentExecutor outer circuit check + result recording + DefaultAgentEngine wiring: PASS (`ReActAgentExecutor.java:873-884` allowCall before retry loop; `:913` recordFailure catch entry; `:1003` recordFailure !isSuccess; `:1020` recordSuccess; `DefaultAgentEngine.java:160,782-784,1656`)
    - Wiring verification (default AlwaysClosed LLM call executes + recording no-op): PASS (`TestCircuitBreakerWiring.defaultAlwaysClosedExecutesCallOnceAndSurfacesFailure` callCount==1 + status=failed; `defaultAlwaysClosedRecordsSuccessAndStaysClosedOnHappyPath` completed)
    - No silent skip (OPEN throws NopAiAgentException; AlwaysClosed recording explicit no-op with comments): PASS (`ReActAgentExecutor.java:878-883`; `AlwaysClosed.java:57-71`)
  - Phase 2 Exit Criteria:
    - ThresholdBreaker 4 transitions: PASS (CLOSED→OPEN `:193-198`; OPEN→HALF_OPEN lazy `:119-123`; HALF_OPEN→CLOSED `:164-170`; HALF_OPEN→OPEN `:200-206`)
    - End-to-end (execute → consecutive failures → OPEN → rejected): PASS (`TestThresholdBreakerEndToEnd.consecutiveFailuresAcrossExecutionsTripAndReject` threshold=2, 2 execute() → OPEN, 3rd rejected callCount stays 2 + error OPEN; `preTrippedBreakerRejectsBeforeChatService` 0 calls)
    - Thread safety concurrent test: PASS (`TestThresholdBreaker.concurrentFailuresTripConsistently` 100 threads; `concurrentAllowCallAdmitsAtMostOneHalfOpenProbe` 32 threads admitted<=1)
    - No silent skip (HALF_OPEN probe is real call): PASS (allowCall returns true → real chatService.call at ReActAgentExecutor.java:894)
  - Closure Gates:
    - Anti-Hollow: PASS (allowCall/recordFailure/recordSuccess all called at runtime, proven by TestThresholdBreakerEndToEnd breaker state transitions + TestCircuitBreakerWiring callCount=0 when AlwaysOpen rejects)
    - AlwaysClosed recording real explicit no-ops (not empty `{}`): PASS (multi-line `// Explicit no-op: ...` comments)
    - All 7 Non-Goals explicitly in plan (:40-46): PASS
    - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` exit code: 0 (all 44 items checked + Closure Evidence written)
    - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit code: 0 (0 findings)
    - Deferred items classification: ISustainer/persistence/auto-fallback/dynamic-threshold/per-tool/XDSL/half-open-probe all explicitly Non-Goals (not in-scope live defects downgraded)
  - Build: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1748 tests, 0 failures (1717 existing + 31 new: TestAlwaysClosed 5 + TestCircuitBreakerWiring 6 + TestThresholdBreaker 17 + TestThresholdBreakerEndToEnd 3)

Follow-up:

- ISustainer (L3-8): successor plan required (互斥弹性哲学)
- 持久化熔断状态: successor plan required (依赖 L4-8 Actor Runtime 或共享存储)
- 熔断 OPEN 时自动模型回退: successor plan required (router 感知 circuit 状态)
- 动态阈值校准: optimization candidate
- XDSL 配置化: optimization candidate
- half-open 独立探针: optimization candidate

## Follow-up handled by 212-nop-ai-agent-sustainer.md

ISustainer (L3-8)（Non-Goals 第一条，标 `successor plan required`；Closure Follow-up 标 `successor plan required (互斥弹性哲学)`）已由 successor plan `ai-dev/plans/212-nop-ai-agent-sustainer.md` 接管：交付"永不放弃"弹性哲学的 `ISustainer` 契约 + `NoOpSustainer` shipped 默认（恒 STOP 零回归）+ `SisypheanSustainer` 功能实现（退出决策点 Stop-hook，对 MAX_ITERATIONS 截断强制续跑，受 `maxSustainCount` 硬上限保护）。Phase 1 裁定 L3-1 vs L3-8 互斥执行机制为部署层文档约束（design §11a"由部署场景决定"），引擎不抛运行时互斥 guard。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。

## Follow-up handled by 213-nop-ai-agent-circuit-aware-routing.md

熔断 OPEN 时自动模型回退（Non-Goals 第三条，标 `successor plan required`；Non-Blocking Follow-ups 标 `Classification: successor plan required`；Closure Follow-up 标 `successor plan required (router 感知 circuit 状态)`）已由 successor plan `ai-dev/plans/213-nop-ai-agent-circuit-aware-routing.md` 接管：在 `ReActAgentExecutor` 的 `route()` 返回后、retry 循环外层 circuit check 前新增 circuit-aware 路由解析步骤——主模型 circuit OPEN 时主动沿 `IModelRouter.getFallback(...)` 链查找 circuit-closed 健康模型并切换续跑，全部回退链耗尽才 fail-fast。shipped 默认（AlwaysClosed + PassThroughModelRouter）零回归。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
