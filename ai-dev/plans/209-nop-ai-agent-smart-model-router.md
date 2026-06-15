# 209 nop-ai-agent SmartModelRouter 功能性路由器（Complexity Routing + Budget-Aware Downgrade + Fallback Chain）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: SmartModelRouter
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/206-nop-ai-agent-budget-control-hook.md`（Non-Blocking Follow-ups: "SmartModelRouter：设计 §6.3 的 6 步 Smart Router 管线，消费 IBudgetProvider 快照做预算感知路由。Classification: successor plan required"）+ `ai-dev/plans/207-nop-ai-agent-llm-retry-policy.md`（Non-Blocking Follow-ups: "FALLBACK 决策的模型回退链消费：RetryDecision.FALLBACK → IModelRouter 切换备选模型。本计划接线层对 FALLBACK fail-loud，待模型回退链落地后接入。Classification: successor plan required"）；`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §6.3–6.5（Smart Router 六步管线 + Fallback 错误分类）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2
> Related: `205`（model-switched 消息，routingReason 来源）、`206`（IBudgetProvider 预算快照）、`207`（IRetryPolicy + RetryDecision.FALLBACK）、L2-10（IModelRouter + PassThroughModelRouter ✅ 前置依赖）

## Purpose

把 nop-ai-agent 的请求路由从"PassThroughModelRouter 直连配置模型、零路由逻辑"升级为"SmartModelRouter 功能性路由器：按复杂度路由 + 预算感知降级 + 模型回退链消费"。本计划交付 SmartModelRouter 功能实现，闭合三个 carry-over gap：(1) plan 207 的 `RetryDecision.FALLBACK` 当前 fail-loud（抛异常而非切换模型）；(2) plan 206 的 `IBudgetProvider` 已接线但无功能性 router 消费预算快照；(3) plan 205 的 model-switched 消息 routingReason 恒为 null（PassThroughModelRouter 不产生有意义的路由原因）。

## Current Baseline

基于设计文档与已完成 plans 核对（2026-06-16）：

- **`IModelRouter` 接口 + `RoutingResult` + `PassThroughModelRouter` 已落地**（L2-10 ✅）：`IModelRouter.route(List<ChatMessage>, ChatOptions, AgentExecutionContext)` 返回 `RoutingResult`（含 `options` / `complexity` / `routingReason` 三字段，后两者 nullable）。`PassThroughModelRouter` 直连配置模型、不做任何路由。grep 确认：`SmartModelRouter` 类在 `nop-ai/nop-ai-agent/src/main/java/` 零命中——功能性路由器从未实现。
- **`IBudgetProvider` + `BudgetSnapshot` 已接线**（L2-22 ✅，plan 206）：ReAct 循环在每轮 `modelRouter.route()` 调用前刷新 `ctx.setBudgetSnapshot(budgetProvider.getBudget(ctx))`。router 可通过 `ctx.getBudgetSnapshot()` 读取预算（`estimatedTotalCost` / `totalTokensUsed` / `budgetLimit` / `exceeded`）。但 `PassThroughModelRouter` 不读取预算快照——预算信息在 ctx 中但对 router 不可观测（无功能性消费者）。
- **`RetryDecision.FALLBACK` 已定义但 fail-loud**（L3-2 ✅，plan 207）：`ReActAgentExecutor` 重试循环捕获 FALLBACK 决策时抛 `NopAiAgentException`（无 fallback chain 配置，Minimum Rules #24 fail-loud）。重试循环位置：`ReActAgentExecutor` 的 `chatService.call()` 包装层。`StandardRetryPolicy` 当前不返回 FALLBACK（返回 RETRY 或 STOP），但 `RetryDecision.FALLBACK` 枚举值存在、retry 循环有对应分支——接线层就绪，等模型回退链接入。
- **`IModelSwitchedMessageWriter` + model-switched 消息已就位**（L2-21 ✅，plan 205）：ReAct 循环在 `IModelRouter.route()` 返回后检测 `provider:model` 复合键变更，变更时通过 writer 持久化 role=80 审计消息。`PassThroughModelRouter` 不改变模型，所以 model-switched 消息不产生（routingReason 不被填充）。
- **`NopAiModel` 定价列已落地**（L2-19 ✅，plan 204）：`nop_ai_model` 表有 6 个定价列（input/output/reasoning/cache-read/cache-write price + currency）。agent 运行时层不直接查询 DB 定价——定价数据通过 `BudgetSnapshot.estimatedTotalCost`（由 `IBudgetProvider` 实现，如未来的 `DbBudgetProvider`）传递给 router。
- **`NopAiAgentException` 已是 `NopException` 子类**（plan 196 ✅）：FALLBACK 无回退模型时的异常可直接使用模块异常类。
- **可靠性/扩展组件装配范式成熟**：`DefaultAgentEngine` 通过 field + setter + `resolveExecutor` 将可靠性/扩展组件透传给 `ReActAgentExecutor.Builder`（如 `usageRecorder` / `budgetProvider` / `retryPolicy` 等，均有 setter，见 plans 193–207）。`PassThrough*` / `NoOp*` 为 shipped 默认、功能性实现为 opt-in 是模块既定惯例。**注意 `modelRouter` 的装配方式不同**：`DefaultAgentEngine.modelRouter` 是 `private final` 字段（构造器注入），**没有 setter**——与 `usageRecorder`/`budgetProvider`/`retryPolicy` 的 field+setter 模式不同。SmartModelRouter 作为 opt-in 时需通过 `DefaultAgentEngine` 构造器参数注入，或直接通过 `ReActAgentExecutor.Builder.modelRouter(...)` 在 executor 层注入。本计划交付 SmartModelRouter 类 + IModelRouter 回退能力扩展，不修改 `DefaultAgentEngine` 的 modelRouter 装配机制（保持 `private final`）。
- **`PassThroughModelRouter` 是 shipped 默认**：SmartModelRouter 是 opt-in。默认配置下路由行为零变化。

## Goals

- `SmartModelRouter` 作为功能性 `IModelRouter` 实现，按请求复杂度（simple/medium/complex）路由到不同模型 tier——simple 路由到便宜模型，complex 路由到强模型。
- SmartModelRouter 读取 `ctx.getBudgetSnapshot()`，当 `exceeded == true` 时主动降级到更便宜的模型 tier（预算感知降级）。
- SmartModelRouter 返回有意义的 `routingReason`（如 "complexity=medium" / "budget-exceeded→downgraded"），使 plan 205 的 model-switched 审计消息携带可读的路由原因。
- 模型回退链消费：当重试循环收到 `RetryDecision.FALLBACK` 时，向 router 查询回退模型；router 返回回退模型则用新模型重试，无回退模型则 fail-loud（保持 plan 207 的安全语义）。
- `PassThroughModelRouter` 保持 shipped 默认——SmartModelRouter 是 opt-in，默认配置下行为零变化。
- 受影响既有测试零回归。
- 设计文档 §6.4 重写 SmartModelRouter 描述以匹配启发式分类实现（消除"Judge LLM"与"heuristic"矛盾），§6.3/§6.5 标注实现状态，roadmap 更新 SmartModelRouter 行。

## Non-Goals

- **Judge 模型分类（设计 §6.3 Step 3，调用便宜 LLM 做复杂度分类）**：SmartModelRouter 首版使用启发式分类（消息长度、工具数量、是否包含代码等可观测信号），不调用额外 LLM。Judge 模型方法引入额外延迟、额外 LLM 调用成本、以及分类 prompt 工程，是独立的 latency/cost 权衡增强。Classification: successor plan required。
- **编排 prompt 注入 + 工具精简（设计 §6.3 Step 4）**：按复杂度精简工具集和注入编排 prompt。需要工具集管理和 prompt 注入基础设施，是独立增强。Classification: successor plan required。
- **Zero-usage 重试检测（设计 §6.3 Step 6）**：检测 LLM 返回空用量并自动重试。需要与 `IUsageRecorder` 联动，是独立优化。Classification: successor plan required。
- **`DbBudgetProvider`（生产 DB-backed 预算 provider）**：plan 206 的 successor。SmartModelRouter 只消费 `BudgetSnapshot`，不关心 provider 实现。
- **XDSL 配置化启用 SmartModelRouter**：`agent.xdef` 中 `<router>` 元素绑定——设计 §9.2 已显式拒绝"路由作为 DSL 字段"。程序化装配（constructor/setter）足矣。
- **Per-model 预算配额**：本计划只支持 session 级总预算触发降级，不支持 per-model 配额（"模型 A 不超过 $Y"）。后者是 router 内部策略增强。
- **图片 Fallback（设计 §7.5）**：non-transient 错误时自动移除请求中图片重试。plan 207 的 successor，与模型回退链正交。
- **`ICircuitBreaker`（L3-1）**：连续故障后自动熔断是独立 Layer 3 work item。本计划的 fallback chain 是 per-request 模型切换，不是跨 request 的熔断。
- **Intra-iteration fallback 的 model-switched 审计消息**：plan 205 的 model-switched 消息（role=80）在 `route()` 返回后、重试循环之前基于 inter-iteration 模型变更检测写入。重试循环内 FALLBACK 触发的模型切换发生在该检测窗口之后，不产生额外的 role=80 审计消息。如需追踪 intra-iteration fallback 的审计轨迹，是独立增强（可在回退分支追加 `IModelSwitchedMessageWriter` 调用，但需裁定 SEQ 语义与重复消息策略）。

## Scope

### In Scope

- `SmartModelRouter` 类（功能性 `IModelRouter` 实现），位于 `io.nop.ai.agent.router` 包（与 `PassThroughModelRouter` 同包）。
- 复杂度分类逻辑（启发式：基于消息长度、工具数量、上下文信号把请求分为 simple / medium / complex 三档）。
- 模型 tier 配置：SmartModelRouter 通过构造器接收 tier → model options 映射（哪个复杂度用哪个 provider+model），使集成商可配置路由策略。
- 预算感知降级：router 读取 `ctx.getBudgetSnapshot()`，当 `exceeded == true` 时降级到更便宜的 tier。
- 有意义的 `routingReason`：每次路由决策填充路由原因字符串。
- 模型回退链：IModelRouter 获得回退查询能力（default 方法返回 null = 无回退；SmartModelRouter 覆写返回 tier 配置中的下一个模型）。
- `ReActAgentExecutor` 重试循环修改：`RetryDecision.FALLBACK` 时向 router 查询回退模型，有则更新 request 并重试，无则保持 fail-loud。
- Focused 测试覆盖（分类决策 / 预算降级 / 回退链 / routingReason / 默认零回归）。
- 端到端测试：注入 SmartModelRouter → ReAct 循环 → 复杂度路由 → 预算超限降级 → LLM 调用失败 → fallback 回退。
- 设计文档 §6.3–6.5 标注实现状态。
- roadmap §4 SmartModelRouter 行状态更新。

### Out Of Scope

- Judge 模型分类 / 编排 prompt + 工具精简 / Zero-usage 检测 / DbBudgetProvider / XDSL 配置化 / per-model 配额 / 图片 Fallback / ICircuitBreaker（理由见 Non-Goals）。

## Execution Plan

### Phase 1 - SmartModelRouter 核心（复杂度分类 + 模型 tier 路由 + 预算感知降级）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/router/`（新增 `SmartModelRouter`）、`io.nop.ai.agent.router.IModelRouter`（回退查询 default 方法）

- Item Types: `Fix`（功能缺失 = 设计 §6.3–6.4 已定义 SmartModelRouter 但零实现）、`Decision`、`Proof`

- [x] 裁定并落档复杂度分类策略：首版使用启发式分类（非 Judge LLM），分类信号至少包含消息总长度、工具数量、是否包含代码/结构化内容等可观测特征；裁定 simple/medium/complex 三档的阈值。落档理由：Judge LLM 分类引入额外延迟与成本，首版优先交付路由基础设施
- [x] 裁定并落档模型 tier 配置形态：构造器接收复杂度 → ChatOptions（含 provider+model）映射；裁定每 tier 是否允许 fallback 模型链（本 Phase 预留配置位，Phase 2 消费）
- [x] 实现 `SmartModelRouter`：实现 `IModelRouter.route()`——对输入消息做复杂度分类，按 tier 配置选择目标模型，读取 `ctx.getBudgetSnapshot()` 并在 `exceeded==true` 时降级到更便宜的 tier，填充 `routingReason`
- [x] 在 `IModelRouter` 上增加回退查询 default 方法（返回 null = 无回退能力），使 `PassThroughModelRouter` 无需改动（继承 default），SmartModelRouter 在 Phase 2 覆写
- [x] 编写 focused 测试覆盖：(a) simple/medium/complex 三档分类正确路由到对应 tier 模型；(b) 预算 exceeded==true 时降级到更便宜 tier；(c) 预算未超限时不降级；(d) routingReason 非空且语义正确；(e) 无预算快照（NoOpBudgetProvider，exceeded==false）时不降级
- [x] 确保 `PassThroughModelRouter` 仍是 `DefaultAgentEngine` shipped 默认，SmartModelRouter 是 opt-in

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `SmartModelRouter` 类存在于 `io.nop.ai.agent.router` 包，实现 `IModelRouter`，`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] SmartModelRouter 对 simple/medium/complex 三档请求分别路由到配置的不同 tier 模型（测试断言 `RoutingResult.options` 中的 model 与 tier 配置匹配）
- [x] SmartModelRouter 在 `BudgetSnapshot.exceeded == true` 时返回的模型 tier 低于未超限时的 tier（测试断言降级行为）
- [x] SmartModelRouter 返回的 `routingReason` 非空且包含分类/降级原因（测试断言）
- [x] **接线验证**（Minimum Rules #23）：测试通过注入 SmartModelRouter 到 `DefaultAgentEngine` 并运行 ReAct 循环，断言 `route()` 被实际调用且 `RoutingResult` 中的模型选择影响后续 LLM 调用
- [x] **新增功能测试**（Minimum Rules #25）：复杂度三档分类 / 预算降级 / routingReason 每条新行为有对应测试断言
- [x] **无静默跳过**（Minimum Rules #24）：无模型配置的 tier 抛出 `NopAiAgentException` 而非返回 null/空 options；预算快照读取失败时抛出而非吞异常
- [x] `IModelRouter` 回退查询 default 方法存在且 `PassThroughModelRouter` 继承后返回 null（无回退），不改变 PassThrough 行为
- [x] 既有测试零回归（PassThroughModelRouter 仍是默认）：`./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 模型回退链消费 + 端到端验证 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/router/SmartModelRouter.java`（回退链覆写）、`engine/ReActAgentExecutor.java`（重试循环 FALLBACK 分支）、测试、`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Fix`（FALLBACK fail-loud gap = plan 207 carry-over）、`Proof`、`Follow-up`

- [x] SmartModelRouter 覆写 IModelRouter 回退查询方法：根据当前使用的模型 tier 返回配置的下一个回退模型；无更多回退模型时返回 null（保持 fail-loud 安全语义）
- [x] 修改 `ReActAgentExecutor` 重试循环的 FALLBACK 分支：当前行为是直接抛 `NopAiAgentException`——修改为先向 `modelRouter` 查询回退模型，有回退模型则更新 chatRequest 的 options 并以新模型重试 LLM 调用，无回退模型则保持 fail-loud 抛异常。**关键**：回退后必须同步更新路由结果中用于 usage record 的模型标识（`routedOptions` / model key 变量），使 `IUsageRecorder.record()` 使用的是实际执行调用的回退模型，而非原始首选模型——否则 per-model 用量聚合数据将错误归属。注意：model-switched 消息（plan 205）在 `route()` 返回后、重试循环之前写入（基于 inter-iteration 模型变更检测），intra-iteration 的 fallback 模型切换不在该检测窗口内——回退触发的模型变更不产生 role=80 审计消息（见 Non-Goals）
- [x] 裁定并落档回退链与重试策略的交互：回退到新模型后是否重置 attempt 计数器（裁定：回退视为新模型的新调用周期，attempt 重置为 0，使新模型也有自己的重试预算）
- [x] 编写回退链 focused 测试：(a) SmartModelRouter 有回退配置时，FALLBACK → 回退模型被实际用于重试调用；(b) SmartModelRouter 无回退配置（或回退链耗尽）时，FALLBACK → fail-loud 抛 NopAiAgentException（保持 plan 207 安全语义）；(c) PassThroughModelRouter（回退查询返回 null）时，FALLBACK → fail-loud（行为不变）
- [x] 编写端到端测试：注入 SmartModelRouter（配置 simple→cheap, complex→strong, fallback chain [strong→medium]）→ ReAct 循环 → 首选模型 LLM 调用抛 5xx（被分类为 TRANSIENT）→ 重试耗尽后如果 policy 返回 FALLBACK → 回退到 medium 模型 → 成功。断言：最终使用的模型是回退模型，调用计数反映回退后重试
- [x] `nop-ai-agent-llm-layer.md` §6.4 **重写** SmartModelRouter 描述以匹配启发式分类实现（当前 §6.4 写的是"使用便宜模型做 Judge 分类"，需改为"首版使用启发式分类，Judge LLM 分类为独立 successor"），消除设计文档与代码的矛盾；§6.3 标注六步管线中已落地步骤（Step 5 回退链消费）与 deferred 步骤（Steps 1/2/3/4/6）；§6.5 Fallback 错误分类表的"切换 Fallback Chain"标注已落地
- [x] roadmap §4 SmartModelRouter 行状态更新（新增行或标注现有 L2-10 扩展）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] SmartModelRouter 覆写回退查询方法，能根据当前 tier 返回下一个回退模型
- [x] `ReActAgentExecutor` 重试循环的 FALLBACK 分支在 router 提供回退模型时用新模型重试，在无回退模型时 fail-loud
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `DefaultAgentEngine.execute()` → ReAct 循环 → SmartModelRouter 路由 → LLM 调用 → FALLBACK → 回退模型重试 → 成功——完整路径跑通
- [x] **接线验证**（Minimum Rules #23）：测试断言回退后 LLM 调用使用了新模型（通过 mock chatService 验证收到的 request 中的 model 与回退模型匹配）
- [x] **Usage record 模型归属正确性**：回退成功后，`IUsageRecorder.record()` 记录的 provider/model 是回退模型而非原始首选模型（测试断言 UsageRecord 字段或 DB 行的 model 字段与回退模型一致）
- [x] **新增功能测试**（Minimum Rules #25）：回退成功 / 回退链耗尽 fail-loud / PassThrough 无回退保持 fail-loud——每条路径有对应断言
- [x] **无静默跳过**（Minimum Rules #24）：回退链耗尽时抛异常而非静默返回 null 响应；回退模型更新后不跳过重试逻辑
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（含新增测试 + 既有 tests 零回归）
- [x] `nop-ai-agent-llm-layer.md` §6.4 已重写为匹配启发式分类实现，§6.3/§6.5 已标注实现状态
- [x] roadmap §4 SmartModelRouter 行已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `SmartModelRouter` 功能实现落地于 `io.nop.ai.agent.router`，实现复杂度分类路由 + 预算感知降级 + 回退链消费
- [x] `PassThroughModelRouter` 保持 shipped 默认，SmartModelRouter 是 opt-in
- [x] `RetryDecision.FALLBACK` 在 router 提供回退模型时实际切换模型并重试（不只类型存在）
- [x] 默认配置（PassThroughModelRouter）下既有行为零回归
- [x] 必要 focused verification（分类三档 / 预算降级 / 回退成功 / 回退耗尽 fail-loud / 端到端）已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（Judge 模型 / 编排 prompt / zero-usage / DbBudgetProvider / XDSL / per-model 配额 / 图片 Fallback / ICircuitBreaker 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-llm-layer.md` §6.3–6.5、roadmap §4）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）SmartModelRouter 的 route() 确实在 ReAct 循环运行时被调用（不只是类型存在），（b）回退链消费路径从 retry loop → router → 新模型 LLM 调用完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；Judge 模型分类 / 编排 prompt + 工具精简 / Zero-usage 检测 / DbBudgetProvider / XDSL 配置化 / per-model 配额 / 图片 Fallback / ICircuitBreaker 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Judge 模型分类**（设计 §6.3 Step 3）：调用便宜 LLM 做复杂度分类，替代启发式分类。需要分类 prompt 工程 + 额外 LLM 调用配置。Classification: successor plan required。
- **编排 prompt 注入 + 工具精简**（设计 §6.3 Step 4）：按复杂度 tier 精简工具集、注入编排 prompt。Classification: successor plan required。
- **Zero-usage 重试检测**（设计 §6.3 Step 6）：检测 LLM 返回空用量自动重试。Classification: successor plan required。
- **XDSL 配置化模型 tier 配置**：通过外部配置文件（而非构造器硬编码）定义 tier → model 映射。设计 §9.2 拒绝了"路由作为 DSL 字段"，但 tier 配置的外部化不等同于 DSL 路由声明。Classification: optimization candidate。

## Closure

Status Note: SmartModelRouter 功能性路由器（启发式复杂度分类 + 预算感知降级 + RetryDecision.FALLBACK 回退链消费）已落地于 `io.nop.ai.agent.router`；闭合三个 carry-over gap（plan 205 routingReason / plan 206 预算快照消费 / plan 207 FALLBACK fail-loud）。`PassThroughModelRouter` 仍为 shipped 默认（opt-in 零回归）。Judge 模型 / 编排 prompt / zero-usage / DbBudgetProvider / XDSL / per-model 配额 / 图片 Fallback / ICircuitBreaker 均为显式 Non-Goal，已裁定为独立 successor（无 in-scope live defect 被静默降级）。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（explore，read-only，fresh session）— task `ses_13260e460ffezfOmbggNz6W0iy`
- Audit Session: ses_13260e460ffezfOmbggNz6W0iy
- Evidence:
  - 逐条 Exit Criterion 验证结果（12/12 PASS，含 file:line citation）：
    - Phase 1 复杂度分类 + tier 路由：PASS — `SmartModelRouter.java:158-186`（classify）+ `TestSmartModelRouter.java:77-150`
    - 预算感知降级：PASS — `SmartModelRouter.java:89-98` + `TestSmartModelRouter.java:172-204`
    - routingReason 非空描述性：PASS — `SmartModelRouter.java:228-239` + `TestSmartModelRouter.java:259-270`
    - IModelRouter.getFallback default + PassThrough 继承：PASS — `IModelRouter.java:36-38`、`PassThroughModelRouter` 未覆写
    - SmartModelRouter.getFallback 链消费：PASS — `SmartModelRouter.java:122-152` + `TestSmartModelRouterFallback.java:170-208`
    - 重试循环 FALLBACK 分支：PASS — `ReActAgentExecutor.java:850-895`（getFallback→切换+attempt 重置+continue / 无则 fail-loud 含 "FALLBACK"）
    - **Anti-Hollow (a)** route() 运行时被调用：PASS — 调用点 `ReActAgentExecutor.java:770` + `TestSmartModelRouterWiring.java:127-162`
    - **Anti-Hollow (b)** 回退链端到端连通：PASS — `TestSmartModelRouterFallback.java:219-245` + `:338-404`（DefaultAgentEngine→ReAct→SmartModelRouter→LLM 失败→FALLBACK→回退模型→completed）
    - **Anti-Hollow (c)** 无空方法体/静默跳过：PASS — `SmartModelRouter.java:101-109`（无配置 tier 抛 NopAiAgentException）+ Builder 校验 + `ReActAgentExecutor.java:865-869` fail-loud；测试 `TestSmartModelRouter.java:277-306`
    - Usage record 归属回退模型：PASS — `routedOptions` 在 FALLBACK 分支重赋值（`ReActAgentExecutor.java:883`）→ `usageRecord.setAiModel(routedOptions.getModel())`（`:941`）；`TestSmartModelRouterFallback.java:303-332` 断言 aiModel=回退模型
    - PassThrough 默认 + 零回归：PASS — `DefaultAgentEngine.java:196,221` / `ReActAgentExecutor.java:230,616` 默认 PassThrough
    - 文档同步：PASS — `nop-ai-agent-llm-layer.md:198-237`（§6.3/6.4/6.5）+ `nop-ai-agent-roadmap.md:189,203-204`（L2-23 ✅）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/209-nop-ai-agent-smart-model-router.md --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 critical / 0 high / 0 medium / 0 low findings）；端到端调用链 DefaultAgentEngine.execute() → ReAct 循环 → SmartModelRouter.route() → chatService.call() → 失败 → retry loop → modelRouter.getFallback() → 新模型 chatService.call() 完整连通（TestSmartModelRouterFallback.endToEndFallbackThroughDefaultAgentEngine）
  - Deferred 项分类检查：所有 deferred 项（Judge 模型 / 编排 prompt / zero-usage / DbBudgetProvider / XDSL / per-model 配额 / 图片 Fallback / ICircuitBreaker）均为显式 Non-Goal 独立 successor，无 in-scope live defect 被降级到 non-blocking 区域
  - `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（全模块零回归，含 TestRetryPolicyWiring/TestStandardRetryPolicyEndToEnd/TestBudgetProviderWiring 既有测试）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Follow-up:

- Judge 模型分类（§6.3 Step 3）/ 编排 prompt + 工具精简（Step 4）/ Zero-usage 检测（Step 6）/ XDSL 配置化 tier：均为独立 successor plan（见 Non-Blocking Follow-ups）
- 无剩余 plan-owned 工作
