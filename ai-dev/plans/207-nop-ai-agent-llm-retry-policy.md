# 207 nop-ai-agent LLM 调用重试策略（IRetryPolicy + NoRetry + StandardRetryPolicy）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-2
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3（L3-2 ❌ 未实现）；`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §七（IRetryPolicy 重试策略设计契约）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §3.1（调用层：错误分类/重试/超时）+ §5（Layer 3 后续能力）
> Related: L3-1（ICircuitBreaker，独立 successor，概念上是 retry 耗尽后的升级）、Plan 154（IModelRouter 429/5xx fallback，显式 defer 到 IRetryPolicy territory）

## Purpose

把 nop-ai-agent 引擎对单次 LLM 调用的瞬态故障处理从"零重试、任何故障立即终止整个 agent 执行"收敛为"按可插拔 `IRetryPolicy` 决策重试或快速失败"。本计划交付重试契约（接口 + 错误分类 + 决策模型）、shipped 默认 `NoRetry`（行为零变化）与功能性 `StandardRetryPolicy`（最多 N 次指数退避，仅对瞬态/限流错误重试），并把重试循环接线到 ReAct 循环的单次 LLM 调用点。模块定位为"面向大规模无人值守自动化执行"，对 429 限流 / 5xx / 网络超时零重试与该定位直接冲突——这是 Layer 3 最基础、下游依赖最多的可靠性原语。

## Current Baseline

基于设计文档与 roadmap 核对（2026-06-16）：

- **`IRetryPolicy` 及全部相关类型在 nop-ai-agent 中零实现**：roadmap §4 标 L3-2 ❌。设计 `nop-ai-agent-llm-layer.md` §七完整描述了契约（三决策、重试上下文、错误分类、三种模式、流式保护、Retry-After 多源解析），但从未落地为代码。
- **ReAct 循环对单次 LLM 调用无重试**：`ReActAgentExecutor` dispatch loop 每轮通过 `chatService.call(chatRequest)` 发起一次 LLM 调用（plan 201/202 的 token 累积点确认此调用点存在并返回 `NopAiChatResponse`）。该调用点当前无 try/catch 重试包装——任何抛出的异常（限流、超时、5xx）直接传播终止整个 `execute()`。
- **已有可靠性组件装配范式可复用**：`DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 将可靠性/扩展组件透传给 `ReActAgentExecutor.Builder`（如 `usageRecorder`/`budgetProvider`/`checkpointManager`/`approvalGate` 等，见 plans 193–206）。`NoOp*` pass-through 为 shipped 默认、功能性实现为 opt-in 的模式已是模块既定惯例。
- **reliability 包归属已定**：`nop-ai-agent-reliability.md` §5.4 明确"可靠性增强（L3-1 circuit breaker / L3-2 retry / L3-3 goal tracker / L3-4 checkpoint / L3-8 sustainer）放入 `io.nop.ai.agent.reliability` 包"。L3-4 checkpoint 已落地于该包，L3-2 retry 遵循同一包边界。
- **`NopAiAgentException` 已是 `NopException` 子类**（plan 196 ✅）：重试耗尽后抛出的异常可直接使用模块异常类。
- **LLM 调用失败的分类信号仅 HTTP 状态码可达**：`nop-ai-core` 的 `ChatServiceImpl` 在非 200 响应时抛 `ERR_AI_SERVICE_HTTP_ERROR` 异常（`NopException`），携带 `ARG_HTTP_STATUS` 参数但**丢弃 HTTP headers 与响应体**。因此错误分类（429/5xx/4xx → ErrorClassification）可依据状态码实现，而 Retry-After header 解析（设计 §7.6 源 1-2）在当前异常上不可达——这是跨模块 `nop-ai-core` 限制，本计划据此裁定 429 使用指数退避而非 Retry-After（见 Non-Goals）。
- **当前 LLM 调用路径非流式**：`chatService.call()` 返回完整 `NopAiChatResponse`（非 streaming chunk 序列）。设计 §7.4 的流式保护（`hasStreamedContent`）在本计划中预留字段位但恒传 false，实际流式保护是独立 successor（见 Non-Goals）。
- **roadmap §4**：`L3-2 | ❌ 未实现 | IRetryPolicy 接口 + NoRetry 默认 + StandardRetryPolicy`。本计划关闭这一行。

## Goals

- `IRetryPolicy` 契约（接口 + `RetryDecision` + `RetryContext` + `ErrorClassification` + 决策返回类型）落地于 `io.nop.ai.agent.reliability` 包，语义与设计 §7.2 一致。
- `NoRetryPolicy` 作为 shipped 默认（fail-fast，恒返回 STOP），注入后引擎行为与当前零重试**完全一致**（零行为回归）。
- `StandardRetryPolicy` 作为功能性 opt-in 实现：最多 N 次重试（默认 3）、指数退避、仅对 TRANSIENT/RATE_LIMITED 错误重试、对 NON_TRANSIENT/QUOTA_EXCEEDED 立即 STOP。
- LLM 调用错误分类能力（按异常携带的 HTTP 状态码把异常映射为 `ErrorClassification`），使 `StandardRetryPolicy` 的决策有据可依。
- 重试循环接线到 ReAct 循环单次 LLM 调用点：调用失败时按 policy 决策 RETRY（等待后重试同一调用）/ STOP（抛出）/ FALLBACK（当前无 fallback chain，fail-loud 停止并记录）。
- `DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 装配 retryPolicy（默认 `NoRetryPolicy`），与既有可靠性组件装配模式一致。
- 受影响既有测试零回归（默认 NoRetry = 行为不变）。
- roadmap §4 L3-2 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **ICircuitBreaker（L3-1）/ ISustainer（L3-8）**：独立的 Layer 3 successor，且 L3-1/L3-8 之间存在互斥弹性哲学设计决策须先裁定。概念上熔断是 retry 耗尽后的升级机制，但熔断器本身是独立 work item。
- **Persistent 重试模式（设计 §7.3 第三模式）**：无限重试、相同错误 10 次后停止，适合无人值守长时间执行场景。比 Standard 复杂，是独立 successor。
- **图片 Fallback（设计 §7.5）**：non-transient 错误时自动移除请求中图片重试。需要请求修改能力，是独立增强。
- **流式保护的实际流式接线（设计 §7.4）**：当前 LLM 调用路径非流式，`RetryContext.hasStreamedContent` 恒传 false。实际 streaming 调用 + 流式保护是独立 successor。
- **FALLBACK 决策的模型回退链消费**：`RetryDecision.FALLBACK` 的语义是"切换到备选模型"，属于 `IModelRouter` fallback chain（Plan 154）territory。本计划只定义枚举值并在接线层 fail-loud 处理（无 chain 配置时停止并记录），不实现模型切换。
- **工具调用重试（tool-call retry）**：设计 §11a 显式拒绝"统一重试策略"——LLM 调用重试与工具调用重试逻辑完全不同。本计划只覆盖 LLM 调用重试。
- **超时预算（reliability §8 timeout budget）**：单次 LLM 调用超时是独立能力，本计划的重试处理由调用本身抛出的超时异常驱动，不引入新的超时预算机制。
- **Retry-After header 解析（设计 §7.6 源 1-2）**：当前 `nop-ai-core` 的 `ChatServiceImpl` 在非 200 响应时抛 `ERR_AI_SERVICE_HTTP_ERROR` 异常，**丢弃 HTTP headers 与响应体**，仅保留 `ARG_HTTP_STATUS` 状态码参数（跨模块 `nop-ai-core`，超出本计划 `Module: nop-ai-agent` scope）。因此 Retry-After header 解析不可达，429 RATE_LIMITED 在本计划中使用指数退避而非 Retry-After 等待。待独立 successor 扩展 `ChatServiceImpl` 保留 headers 后，本计划交付的分类/退避逻辑可直接接入 Retry-After。

## Scope

### In Scope

- `io.nop.ai.agent.reliability` 包：`IRetryPolicy` 接口、`RetryDecision` 枚举、`ErrorClassification` 枚举、`RetryContext` 数据对象、policy 决策返回类型（决策 + 延迟）、`NoRetryPolicy`、`StandardRetryPolicy`。
- LLM 错误分类：按 `chatService.call()` 抛出异常携带的 HTTP 状态码（`ARG_HTTP_STATUS`）把异常映射为 `ErrorClassification`（429→RATE_LIMITED、5xx/超时→TRANSIENT、4xx 参数/权限→NON_TRANSIENT、配额→QUOTA_EXCEEDED）。
- `ReActAgentExecutor`：retryPolicy 字段 + Builder 注入 + 单次 LLM 调用点的重试循环包装。
- `DefaultAgentEngine`：retryPolicy 字段 + setter + `resolveExecutor` 透传给 Builder（默认 NoRetryPolicy）。
- 测试：NoRetry 决策、Standard 各分类决策、最大尝试次数、指数退避、FALLBACK fail-loud、接线后默认行为不变。

### Out Of Scope

- ICircuitBreaker / ISustainer / Persistent 模式 / 图片 fallback / 流式保护接线 / 模型回退链 / 工具调用重试 / 超时预算（理由见 Non-Goals）。
- XDSL 配置化启用 StandardRetryPolicy（`agent.xdef` `<retry>` 元素）：本计划交付程序化装配（Builder/setter），配置化绑定是独立增强。

## Execution Plan

### Phase 1 - 重试契约 + 错误分类 + NoRetry 默认 + 重试循环接线

Status: completed
Targets: `io.nop.ai.agent.reliability`（新类型）、`ReActAgentExecutor`、`DefaultAgentEngine`

- Item Types: `Decision`、`Fix`、`Proof`

- [x] 裁定并落档重试决策返回形态：`IRetryPolicy.shouldRetry(RetryContext)` 返回包含 `RetryDecision`（RETRY/STOP/FALLBACK）与延迟毫秒的决策对象；裁定字段命名与 package-private vs public 可见性
- [x] 实现 `RetryContext`（尝试次数、上次错误、ErrorClassification、hasStreamedContent）与 `ErrorClassification` 枚举（TRANSIENT / NON_TRANSIENT / RATE_LIMITED / QUOTA_EXCEEDED）
- [x] 实现 `IRetryPolicy` 接口与 `NoRetryPolicy`（恒返回 STOP、零延迟），语义与设计 §7.3 NoRetry 模式一致
- [x] 实现 LLM 错误分类逻辑：按 `chatService.call()` 抛出异常携带的 `ARG_HTTP_STATUS` 参数映射为 `ErrorClassification`（设计 §7.2 错误分类）。注意：当前 `nop-ai-core` 的 `ChatServiceImpl` 抛出的 `ERR_AI_SERVICE_HTTP_ERROR` 异常**丢弃 HTTP headers/body**，仅保留状态码——Retry-After header 解析（设计 §7.6 源 1-2）不可达，归入 Non-Goals/successor；本计划分类仅依据状态码
- [x] 在 `ReActAgentExecutor` 增加 `retryPolicy` 字段 + Builder 注入点；为单次 LLM 调用点包装重试循环：捕获异常 → 分类 → 构造 RetryContext → policy.shouldRetry → RETRY(等待后重试同一调用) / STOP(抛出) / FALLBACK(当前无 chain，fail-loud 记录并停止)。默认 NoRetryPolicy 使行为与零重试完全一致
- [x] 在 `DefaultAgentEngine` 增加 `retryPolicy` 字段 + setter + `resolveExecutor` 透传给 Builder（默认 `NoRetryPolicy`），与既有可靠性组件装配模式一致
- [x] 编写 NoRetry / 错误分类 / 重试循环接线（默认路径）的 focused 测试

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IRetryPolicy`/`RetryDecision`/`RetryContext`/`ErrorClassification`/`NoRetryPolicy` 类/接口存在于 `io.nop.ai.agent.reliability` 包且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ReActAgentExecutor` 的 LLM 调用点被重试循环包装；`DefaultAgentEngine` 通过 field+setter+resolveExecutor 装配 retryPolicy（默认 NoRetryPolicy）
- [x] **接线验证**：测试断言默认 NoRetryPolicy 下 LLM 调用只执行一次（无重试），异常照常传播——证明接线连通且零行为回归（见 Minimum Rules #23）
- [x] **无静默跳过**：FALLBACK 分支在无 fallback chain 时显式停止并记录（非空方法体/非吞异常），重试循环所有 decision 分支都有显式处理（见 Minimum Rules #24）
- [x] **新增功能测试**：NoRetry 恒 STOP、错误分类按 HTTP 状态码把 429/5xx/超时/参数错误映射到正确分类——每条新行为有对应测试断言（见 Minimum Rules #25）
- [x] 既有测试零回归（默认 NoRetry = 行为不变）：`./mvnw test -pl nop-ai/nop-ai-agent` 通过（1672 tests, 0 failures）
- [x] 若改变 live baseline：`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §7 / `nop-ai-agent-reliability.md` §3.1 已更新为实现状态；否则明确写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - StandardRetryPolicy 功能实现 + 端到端验证

Status: completed
Targets: `io.nop.ai.agent.reliability`（`StandardRetryPolicy`）、`ReActAgentExecutor`（重试循环行为）、测试

- Item Types: `Fix`、`Proof`

- [x] 实现 `StandardRetryPolicy`（设计 §7.3 Standard 模式）：最大尝试次数（默认 3）、指数退避（baseDelay * 2^attempt，封顶 maxDelay）、仅 TRANSIENT/RATE_LIMITED 重试、NON_TRANSIENT/QUOTA_EXCEEDED 立即 STOP；429 RATE_LIMITED 使用指数退避等待重试（当前调用路径异常不含 Retry-After header，见 Non-Goals）
- [x] 裁定并落档退避参数默认值与可配置入口（构造器参数），不引入 XDSL 配置化（Non-Goal）
- [x] 验证重试循环在配置 StandardRetryPolicy 时对瞬态故障实际重试并最终成功；对 non-transient 立即停止；达到最大尝试次数后抛出 `NopAiAgentException`
- [x] 编写 StandardRetryPolicy 各决策路径的 focused 测试 + 一个端到端测试（配置 StandardRetryPolicy，模拟前 N-1 次 LLM 调用抛瞬态异常、第 N 次成功，断言调用被执行 N 次且最终拿到正常响应）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `StandardRetryPolicy` 存在且 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `ReActAgentExecutor` 的 LLM 调用入口出发，经重试循环（瞬态失败 → 等待 → 重试 → 成功），到达正常响应消费——完整路径跑通
- [x] **接线验证**（Minimum Rules #23）：测试断言在 StandardRetryPolicy 下 LLM 调用被实际重试（调用计数 > 1），证明 policy 确实被重试循环在运行时消费
- [x] **新增功能测试**（Minimum Rules #25）：Standard 对 TRANSIENT 重试、对 NON_TRANSIENT/QUOTA_EXCEEDED 立即 STOP、429 被分类为 RATE_LIMITED 并按指数退避重试、达到 maxAttempts 抛 NopAiAgentException——每条决策路径有对应断言
- [x] **无静默跳过**（Minimum Rules #24）：达到 maxAttempts 后抛异常而非静默返回 null/空响应
- [x] `./mvnw test -pl nop-ai/nop-ai-agent` 通过（含既有测试零回归）（1689 tests, 0 failures）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §7 / `nop-ai-agent-reliability.md` 已更新为实现状态
- [x] roadmap §4 L3-2 行从 ❌ → ✅ 并标注本 plan
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IRetryPolicy` 契约 + `NoRetryPolicy` 默认 + `StandardRetryPolicy` 功能实现全部落地于 `io.nop.ai.agent.reliability`
- [x] 重试循环在运行时确实被 ReAct 循环的 LLM 调用点消费（不只类型存在）
- [x] 默认 NoRetry 下既有行为零回归
- [x] 必要 focused verification（NoRetry / Standard 各分类 / maxAttempts / 端到端重试成功）已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（Persistent/图片fallback/流式保护/模型回退链/工具重试/超时预算均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-llm-layer.md` §7、`nop-ai-agent-reliability.md` §3.1/§5、roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）retryPolicy 被重试循环在运行时调用（不只是字段存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；Persistent 模式 / 图片 fallback / 流式保护接线 / 模型回退链 / 工具重试 / 超时预算 / Retry-After header 解析 / XDSL 配置化均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Persistent 重试模式**（设计 §7.3 第三模式）：无限重试 + 相同错误 10 次后停止，适合无人值守长时间执行。Classification: successor plan required。
- **Retry-After header 解析**（设计 §7.6）：当前 `nop-ai-core` `ChatServiceImpl` 在非 200 时丢弃 HTTP headers/body（仅保留状态码）。需独立 successor 扩展 `ChatServiceImpl` 保留 headers（跨模块变更），之后本计划交付的分类/退避逻辑可接入 Retry-After 等待。Classification: successor plan required（跨模块依赖 `nop-ai-core`）。
- **图片 Fallback**（设计 §7.5）：non-transient 错误时自动移除请求中图片重试 + 记住 Provider 不支持图片。Classification: successor plan required。
- **流式保护实际接线**（设计 §7.4）：当 LLM 调用路径引入 streaming 后，`hasStreamedContent` 需反映真实流出状态，FALLBACK 在已流出时降级为 STOP。Classification: successor plan required。
- **FALLBACK 决策的模型回退链消费**：`RetryDecision.FALLBACK` → `IModelRouter` 切换备选模型（Plan 154）。本计划接线层对 FALLBACK fail-loud，待模型回退链落地后接入。Classification: successor plan required。
- **XDSL 配置化启用**：`agent.xdef` `<retry>` 元素绑定 StandardRetryPolicy。Classification: optimization candidate。

## Closure

Status Note: Plan 207 把 nop-ai-agent 引擎对单次 LLM 调用的瞬态故障处理从"零重试"收敛为"按可插拔 IRetryPolicy 决策"。Phase 1 交付重试契约（IRetryPolicy/RetryDecision/ErrorClassification/RetryContext/RetryOutcome）+ NoRetryPolicy shipped 默认（零行为回归）+ LlmErrorClassifier（按 HTTP 状态码分类）+ 重试循环接线到 ReAct 循环的单次 LLM 调用点（chatService.call）。Phase 2 交付 StandardRetryPolicy（maxAttempts=3 指数退避，仅 TRANSIENT/RATE_LIMITED 重试）。独立 closure audit（fresh session，read-only）逐条核对 10 项 criteria 全 PASS。默认 NoRetry 下 1689 tests 零回归（含 40 新增 tests）。FALLBACK 决策在无 fallback chain 时 fail-loud（非静默跳过）。roadmap §4 L3-2 ❌→✅。所有 Non-Goals（Persistent/图片fallback/流式保护接线/模型回退链/工具重试/超时预算/Retry-After header/XDSL 配置化）显式切出为独立 successor。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（task explore，fresh session ses_132a2e6fdffeXjr1bJuowP2P9s，read-only）
- Evidence:
  - CRITERION 1 (契约类型存在): PASS — 8 文件全部存在于 `io.nop.ai.agent.reliability`（IRetryPolicy.java:37 / RetryDecision.java:21-25 / ErrorClassification.java:26-31 / RetryContext.java:33-54 / RetryOutcome.java:20-77 / NoRetryPolicy.java:23-41 / StandardRetryPolicy.java:42-149 / LlmErrorClassifier.java:54-126）
  - CRITERION 2 (NoRetryPolicy 恒 STOP): PASS — NoRetryPolicy.java:38-40 无条件返回 RetryOutcome.stop()
  - CRITERION 3 (StandardRetryPolicy 决策): PASS — StandardRetryPolicy.java:100-125（NON_TRANSIENT/QUOTA_EXCEEDED→STOP；TRANSIENT/RATE_LIMITED→attempt<maxAttempts-1 时 RETRY 指数退避，else STOP）；computeBackoff.java:131-148
  - CRITERION 4 (ReActAgentExecutor 接线): PASS — retryPolicy field:187 + Builder.retryPolicy:592-595 + 构造器 null-safe:265；重试循环 wraps chatService.call:821-873（catch→classify:828→RetryContext:832-833→shouldRetry:834；RETRY:841-849 LOG.warn+attempt+++sleepBackoff+continue；FALLBACK:850-866 throw NopAiAgentException 含 "FALLBACK"；STOP:867-871 rethrow）
  - CRITERION 5 (DefaultAgentEngine 接线): PASS — field:151 + setRetryPolicy/getRetryPolicy:753-759 null-safe + resolveExecutor:.retryPolicy(this.retryPolicy):1626
  - CRITERION 6 (LlmErrorClassifier 规则): PASS — 429→RATE_LIMITED:94-96 / 5xx→TRANSIENT:97-99 / 4xx→NON_TRANSIENT:100-102 / NopTimeoutException→TRANSIENT:74-76
  - CRITERION 7 (测试覆盖): PASS — 5 测试文件全在 reliability 测试包（TestNoRetryPolicy 5 tests / TestLlmErrorClassifier 13 tests / TestRetryPolicyWiring 6 tests / TestStandardRetryPolicy 13 tests / TestStandardRetryPolicyEndToEnd 4 tests）
  - CRITERION 8 (Anti-Hollow e2e): PASS — TestStandardRetryPolicyEndToEnd.java:130 assertEquals(3, callCount.get()) 证明运行时实际重试
  - CRITERION 9 (无静默跳过): PASS — 无空方法体/无吞异常；continue 在 RETRY 分支（reissue 同一 request，非跳过）；null 返回值是 readHttpStatus 的 "no status" sentinel，由 explicit guard 消费
  - CRITERION 10 (roadmap): PASS — nop-ai-agent-roadmap.md:208 L3-2 = ✅；验收标准:222 [x]
  - **Anti-Hollow 检查**：callCount==1（NoRetry，零重试证明接线连通）/ callCount==3（Standard transient 重试成功）/ callCount==2（maxAttempts=2 耗尽）/ 429 重试 / FALLBACK fail-loud message 含 "FALLBACK" — 运行时调用链完整连通，无空壳
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → **1689 tests, 0 failures**（零回归 + 40 新增 tests）
  - Deferred 项分类检查：所有 Non-Goals（Persistent/图片fallback/流式保护接线/模型回退链/工具重试/超时预算/Retry-After header/XDSL 配置化）显式在 Non-Goals + Non-Blocking Follow-ups 切出，均为 successor plan required / optimization candidate，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（所有 in-scope 项已 landed；所有 Non-Goals 已显式切出为独立 successor）
