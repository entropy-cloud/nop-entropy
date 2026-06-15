# 206 nop-ai-agent 预算控制 hook（L2-22）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-22
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/205-nop-ai-agent-model-switched-message.md`（Non-Goals 第一条：`L2-22（预算控制 hook）... 独立 work item`，Closure Follow-up 标 `successor work item`）；`ai-dev/plans/201`/`202`/`203`/`204` 同样在 Non-Goals 将 L2-22 切出为 `successor plan required`；`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` §3.6 + §6 P2（预算控制 hook）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-22 🟡 ❌ 未实现，line 188）
> Related: `201`（IUsageRecorder，已 completed）、`202`（DbUsageRecorder，已 completed）、`203`（per-model 聚合查询，已 completed）、`204`（NopAiModel 定价列，已 completed）、`205`（model-switched 消息，已 completed）；L2-10（IModelRouter + PassThroughModelRouter ✅ 前置依赖）；L2-19（NopAiModel 定价列 ✅，cost 计算的定价来源）

## Purpose

在 ReAct 循环中 `IModelRouter.route()` 决策之前，提供 session 级预算快照（已用 token / 估算成本 / 预算上限 / 是否超限），使功能性 router 能够基于预算做模型降级决策。本计划把 L2-22 从"无任何预算查询基础设施"收口为"router 可在每轮路由前获取预算快照并据此降级模型"——闭合 usage-tracking → per-model-billing → cost-control 链路的最后一个环节（L2-17~L2-22）。

## Current Baseline

基于 live repo 核对（2026-06-16）：

- **零预算控制代码**：grep `IBudgetProvider|BudgetSnapshot|budgetControl|BudgetControl|budgetGuard|BudgetEnforc|getUsedBudget` 在 `nop-ai/nop-ai-agent/src/main/java/` 返回 0 命中。L2-22 完全未实现。
- **token 预算已存在但不用于路由**：`AgentExecutionContext.tokensUsed`（long，line 25）+ `AgentSession.totalTokensUsed`（long，line 17，`addTokensUsed` 增量累加）已用于上下文压缩触发（reliability.md §7 token budget），但从未传递给 `IModelRouter` 做模型降级决策。
- **成本估算（estimatedCost）不存在于运行时**：`AgentExecutionContext` 无 `estimatedTotalCost` 或任何 cost 字段。设计 §3.6 提出的"在 `AgentExecutionContext` 维护一个 `estimatedTotalCost`（单一标量，每次 record 时增量更新）"尚未落地。成本计算需要定价数据（L2-19 `NopAiModel` 定价列 ✅ 已落地在 DB），但 agent 运行时层无直接 DB 定价查询能力。
- **`IModelRouter.route()` 已接收 `AgentExecutionContext ctx`**（`router/IModelRouter.java:11`）：签名 `RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx)`。router 能访问 ctx，但 ctx 当前无预算/cost 信息可供消费。
- **`RoutingResult` 无预算相关字段**：仅 `options`（ChatOptions）、`complexity`（String，nullable）、`routingReason`（String，nullable）三个字段。router 的降级决策通过返回不同的 `options`（含不同 provider/model）实现——降级机制已有（plan 205 model-switched 消息证明 router 可以改变模型），缺的是驱动降级的预算输入。
- **`PassThroughModelRouter` 是 shipped 默认**（`router/PassThroughModelRouter.java`）：直连配置模型，不做任何路由/降级。默认配置下预算控制不可观测（与 plan 205 的 model-switched 消息同理——仅在集成商注册功能性 router + 功能性 budget provider 时生效）。
- **`IUsageRecorder` 接线已就位**（plan 201 ✅）：ReAct 循环每次 LLM 调用后在 token 累积点调用 `usageRecorder.record(usageRecord)`。`DbUsageRecorder`（plan 202 ✅）将用量写入 `nop_ai_chat_response` 表。`summarizeByModel`（plan 203 ✅）在 service 层提供 SQL 聚合查询。但这些是 service/DB 层能力，agent 运行时层不能直接调用 service BizModel（依赖方向：agent 不依赖 service）。
- **roadmap §4 Layer 2**（line 188）：`L2-22 | 🟡 预算控制 hook：IModelRouter 查询已用预算决定是否降级模型 | L2-20, L2-10 | ❌`。line 199 Layer 2 验收标准列表中无 L2-22 对应行（需新增）。
- **设计 §3.6 给出两种方案**：(a) router 调 `summarizeByModel(sessionId)`——但该方法已移至 service 层 BizModel（plan 201 裁定），agent 运行时层不可直接调用；(b) 在 `AgentExecutionContext` 维护 `estimatedTotalCost` 标量。本计划裁定见 Scope § 设计裁定。

## Goals

- 引入预算查询扩展点（接口 + 数据对象 + NoOp 默认），使功能性 `IModelRouter` 能在每轮路由决策前获取当前 session 的预算快照（已用 token + 估算成本 + 上限 + 是否超限）。
- ReAct 循环在每轮 `IModelRouter.route()` 调用前刷新预算快照，使其通过 `AgentExecutionContext` 对 router 可见。
- 默认 NoOp 预算 provider 返回"无限制"快照（无 cost 追踪、永不超限）——shipped 默认行为零变化（与 `PassThroughModelRouter` 一致：默认配置下预算控制不可观测）。
- Focused 测试 + 端到端测试验证：预算未超限时不降级、预算超限时功能性 router 降级模型、NoOp 默认不干预、token-only 预算（无 cost）正常工作。
- roadmap §4 Layer 2 表格 L2-22 从 ❌ → ✅ 并标注 plan 206。

## Non-Goals

- **生产级 DB-backed budget provider**：`DbBudgetProvider`（raw JDBC 查询 `nop_ai_chat_response` + `nop_ai_model` 定价 join 计算 `estimatedTotalCost`）是独立 successor。本计划只交付接口 + NoOp 默认 + InMemory 测试 provider。理由：与 L2-17/L2-18 拆分模式一致（L2-17 交付接口 + NoOp，L2-18 交付 Db 实现）。
- **SmartModelRouter（功能性 router）**：设计 §6.3 的 6 步 Smart Router 管线（Scenario Detection → Judge Classification → Fallback Chain 等）是独立大 work item。本计划只提供预算查询 hook，使未来的 SmartModelRouter 能消费预算信息。测试中使用 test stub router 验证 hook 端到端可用。
- **per-model 预算限制**：本计划只支持 session 级总预算限制（"整个 session 花费不超过 $X"），不支持 per-model 配额（"模型 A 不超过 $Y，模型 B 不超过 $Z"）。后者是 SmartModelRouter 的内部策略。
- **预算重置/补充机制**：不实现 budget reset / refill / rollover。session 预算从执行开始到结束单调递减。
- **预算超限的硬中止**：本计划实现的是"router 可基于预算选择降级模型"（soft enforcement），不是"预算超限时中止整个 agent 执行"（hard enforcement / circuit breaker）。硬中止是 L3-1 ICircuitBreaker 的职责。
- **GraphQL/REST 预算查询接口**：预算快照是运行时内部状态，不在 service 层暴露查询 API。

## Scope

### 设计裁定

**预算快照的来源与计算责任**：设计 §3.6 提出两种方案。裁定采用 **`IBudgetProvider` 扩展点方案**（非 `AgentExecutionContext` 内联 `estimatedTotalCost` 标量方案）：

1. **引入 `IBudgetProvider` 接口**（Layer 2 扩展点，参照 `IUsageRecorder`/`IModelRouter` 模式）：`BudgetSnapshot getBudget(AgentExecutionContext ctx)`。provider 负责计算 estimatedCost（它可访问定价数据源）+ 持有预算上限配置。理由：(a) cost 计算需要定价数据（L2-19 在 DB），agent 运行时层无法自行计算——将计算责任封装在 provider 内，agent 层只消费结果；(b) 与 `IUsageRecorder`/`IModelRouter`/`IApprovalGate` 等既有扩展点模式一致（接口 + NoOp 默认 + 集成商提供功能性实现）；(c) `AgentExecutionContext` 内联标量方案需要 ctx 持有定价信息或 engine 层计算 cost，违反关注点分离。

2. **`BudgetSnapshot` 数据对象**包含：`estimatedTotalCost`（`BigDecimal`，nullable—null 表示 cost 未追踪）、`totalTokensUsed`（long，从 ctx 既有字段获取）、`budgetLimit`（`BigDecimal`，nullable—null 表示无限制）、`exceeded`（boolean—`estimatedTotalCost != null && budgetLimit != null && estimatedTotalCost.compareTo(budgetLimit) >= 0`）。nullable 字段的 graceful degradation 与 `RoutingResult.complexity`/`routingReason` 可为 null 一致。

3. **预算快照通过 `AgentExecutionContext` 传递给 router**：engine 在每轮路由前调用 `budgetProvider.getBudget(ctx)` 刷新快照，存入 ctx（新增 `budgetSnapshot` 字段）。router 通过 `ctx.getBudgetSnapshot()` 读取。不修改 `IModelRouter.route()` 签名（已含 `ctx` 参数，无需破坏既有签名）。理由：router 已接收 ctx，新增 ctx 字段是最小侵入方式；且预算快照是 per-iteration 状态（每轮可能变化），ctx 是天然的 per-execution 状态载体。

4. **预算执行位置在 router 内部**（非 decorator 包装 router）：router 读取 `ctx.getBudgetSnapshot()`，如果 `exceeded == true` 则返回降级模型的 `RoutingResult`。不引入 `BudgetEnforcingModelRouter` decorator——理由：(a) 降级到哪个模型是 router 的路由策略（不同 router 有不同降级链），不应由 decorator 统一决策；(b) decorator 方案要求所有 router 共享同一降级模型配置，违反扩展点设计哲学。测试中使用 test stub router 演示 router 如何消费预算快照做降级。

**NoOp 默认语义**：`NoOpBudgetProvider.getBudget(ctx)` 返回 `BudgetSnapshot(estimatedTotalCost=null, totalTokensUsed=ctx.getTokensUsed(), budgetLimit=null, exceeded=false)`——即"无 cost 追踪、无预算限制、永不超限"。这与 `PassThroughModelRouter`（恒不改变模型）组合后，shipped 默认行为零变化。

### In Scope

- `IBudgetProvider` 接口 + `BudgetSnapshot` 数据对象 + `NoOpBudgetProvider` 默认实现
- `AgentExecutionContext` 新增 `budgetSnapshot` 字段 + getter/setter
- `DefaultAgentEngine` 新增 `budgetProvider` 字段 + setter（参照 `usageRecorder`/`modelRouter` 接线模式）+ `warnIfInsecureDefaults` 不涉及（budget 不是安全组件）
- `ReActAgentExecutor` 在每轮 `modelRouter.route()` 调用前刷新预算快照到 ctx
- `InMemoryBudgetProvider`（测试用，可配置 limit + 手动/自动累计 cost）
- Focused 测试覆盖（NoOp 默认 / 未超限不降级 / 超限降级 / token-only 预算）
- 端到端测试：`InMemoryBudgetProvider` + test stub budget-aware router → ReAct 循环 → 预算超限时模型降级
- 设计文档 §3.6 标注实现落地
- roadmap §4 Layer 2 表格 L2-22 状态更新 + 验收标准行新增

### Out Of Scope

- `DbBudgetProvider`（生产 DB-backed 实现，successor）
- SmartModelRouter（功能性 router，独立 work item）
- per-model 预算配额
- 预算重置/补充/滚动
- 预算超限硬中止（L3-1 ICircuitBreaker 职责）
- GraphQL/REST 预算查询 API

## Execution Plan

### Phase 1 - 裁定 + 实现

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/`（新增 budget 包、修改 `engine/AgentExecutionContext.java`、`engine/DefaultAgentEngine.java`、`engine/ReActAgentExecutor.java`）

- Item Types: `Fix`（功能缺失 = contract gap：设计 §3.6 + §6 P2 已定义预算控制 hook 但零实现）

- [x] 裁定并落档预算快照来源（`IBudgetProvider` 扩展点 vs ctx 内联标量）——裁定见 Scope § 设计裁定，Phase 1 执行时确认无新增信息后照此实施
- [x] 裁定并落档预算执行位置（router 内部 vs decorator）——裁定见 Scope § 设计裁定
- [x] 实现 `BudgetSnapshot` 数据对象（`estimatedTotalCost` BigDecimal nullable / `totalTokensUsed` long / `budgetLimit` BigDecimal nullable / `exceeded` boolean），含 `exceeded` 的计算语义（estimatedTotalCost 与 budgetLimit 均 non-null 时比较，否则 false）
- [x] 实现 `IBudgetProvider` 接口（`BudgetSnapshot getBudget(AgentExecutionContext ctx)`）
- [x] 实现 `NoOpBudgetProvider`（返回无限制快照：cost=null, limit=null, exceeded=false；`totalTokensUsed` 从 ctx 获取以保持一致）
- [x] `AgentExecutionContext` 新增 `budgetSnapshot` 字段 + getter/setter（nullable，初始 null）
- [x] `DefaultAgentEngine` 新增 `budgetProvider` 字段（默认 `NoOpBudgetProvider.INSTANCE`）+ setter + `resolveExecutor` 传递给 `ReActAgentExecutor.Builder`
- [x] `ReActAgentExecutor` 持有 `budgetProvider`，在 ReAct 循环中 `modelRouter.route()` 调用前刷新 `ctx.setBudgetSnapshot(budgetProvider.getBudget(ctx))`
- [x] 确保刷新点在 route() 之前、在 token 累积之后（即上一轮的 usage 已反映在当前快照中）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IBudgetProvider` 接口存在，含唯一方法 `getBudget(AgentExecutionContext)`
- [x] `BudgetSnapshot` 数据对象存在，含 4 个字段（estimatedTotalCost / totalTokensUsed / budgetLimit / exceeded），`exceeded` 计算语义正确
- [x] `NoOpBudgetProvider` 存在且返回无限制快照（exceeded 恒为 false）
- [x] `AgentExecutionContext` 有 `budgetSnapshot` 字段且可通过 getter/setter 访问
- [x] `DefaultAgentEngine` 有 `budgetProvider` 字段 + setter，`resolveExecutor` 将其传递给 executor builder
- [x] **接线验证**（Minimum Rules #23）：`ReActAgentExecutor` 在每轮 `modelRouter.route()` 调用前确实调用 `budgetProvider.getBudget(ctx)` 并写入 `ctx.setBudgetSnapshot(...)`——通过代码审查或测试断言确认调用链连通
- [x] **无静默跳过**（Minimum Rules #24）：`IBudgetProvider.getBudget()` 不允许返回 null（返回 NoOp 快照而非 null）；budget provider 实现异常时抛出 `NopAiAgentException` 而非吞掉
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/` 已更新；否则明确写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/`（新增测试）、`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 实现 `InMemoryBudgetProvider`（测试用）：可配置 `budgetLimit`，内部维护 `estimatedTotalCost`（可通过 `addCost(BigDecimal)` 手动累计，或从 `IUsageRecorder`/ctx 联动），`getBudget()` 返回当前快照
- [x] 新增 focused 测试，覆盖以下场景：
  - `NoOpBudgetProvider` 返回无限制快照（exceeded=false, cost=null, limit=null）
  - `BudgetSnapshot.exceeded` 计算正确：cost < limit → false；cost >= limit → true；cost=null 或 limit=null → false
  - `InMemoryBudgetProvider` 未超限时 `exceeded=false`
  - `InMemoryBudgetProvider` 超限时 `exceeded=true`
  - token-only 预算（cost=null 但 tokensUsed 用于判断）——裁定：token-only 不通过 `exceeded` 字段表达（`exceeded` 仅基于 cost），router 如需 token 预算可直接读 `ctx.getTokensUsed()`。Phase 1 须确认此裁定并落档
- [x] 新增端到端测试：注入 `InMemoryBudgetProvider`（配置 limit）+ test stub budget-aware `IModelRouter`（读取 `ctx.getBudgetSnapshot()`，`exceeded==true` 时返回降级模型 options）→ 运行 ReAct 循环多轮 → 断言：(a) 预算未超限时 router 返回原模型；(b) 累计 cost 超过 limit 后 router 返回降级模型；(c) `ctx.getBudgetSnapshot()` 在 route() 调用前已被刷新（非 null）
- [x] 新增接线测试：验证 `DefaultAgentEngine` 默认装配 `NoOpBudgetProvider`，且 setter 注入的 provider 确实传递到 `ReActAgentExecutor`
- [x] `nop-ai-agent-usage-and-billing.md` §3.6 标注预算控制 hook 已落地（`IBudgetProvider` 扩展点方案）
- [x] roadmap §4 Layer 2 表格 L2-22 从 ❌ → ✅，标注 plan 206；Layer 2 验收标准新增 L2-22 行

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `InMemoryBudgetProvider` 存在且可配置 limit + 累计 cost
- [x] focused 测试存在，覆盖 5 个场景（NoOp 默认 / exceeded 计算 / 未超限 / 超限 / token-only 裁定落档）
- [x] **端到端验证**（Minimum Rules #22）：从 `DefaultAgentEngine.execute()` → ReAct 循环 → budget 快照刷新 → `IModelRouter.route()` 读取快照 → 预算超限时返回降级模型的完整路径验证通过
- [x] **接线验证**（Minimum Rules #23）：测试断言 `ctx.getBudgetSnapshot()` 在 route() 调用前已被刷新（非 null），且 engine 注入的 provider 确实传递到 executor
- [x] **新功能测试覆盖**（Minimum Rules #25）：budget 快照刷新、exceeded 计算、router 消费快照降级均有对应测试
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（含新增测试 + 既有 tests 零回归）
- [x] `nop-ai-agent-usage-and-billing.md` §3.6 标注实现落地
- [x] roadmap §4 Layer 2 表格 L2-22 标注 ✅ + plan 206
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IBudgetProvider` 扩展点存在，router 可通过 `ctx.getBudgetSnapshot()` 获取预算快照
- [x] 预算快照在每轮 route() 调用前被刷新
- [x] NoOp 默认返回无限制快照（shipped 默认行为零变化）
- [x] 预算超限时功能性 router 可降级模型（端到端验证通过）
- [x] Focused 测试 + 端到端测试通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（DbBudgetProvider / SmartModelRouter 均为显式 Non-Goal）
- [x] 受影响 owner docs 已同步（`nop-ai-agent-usage-and-billing.md` + roadmap），或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）预算快照在运行时确实被刷新并被 router 可读（不只是接口/类型存在），（b）端到端路径从 engine → executor → ctx → router 完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现（NoOpBudgetProvider 是显式 pass-through 默认，非隐藏 gap）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；DbBudgetProvider / SmartModelRouter / per-model 配额 / 预算重置 / 硬中止 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`DbBudgetProvider`（生产 DB-backed 实现）**：raw JDBC 查询 `nop_ai_chat_response` + `nop_ai_model` 定价 join 计算 session 级 `estimatedTotalCost`，参照 `DbUsageRecorder` + `summarizeByModel` 的 SQL 模式。集成商注册后，agent 运行时无需依赖 service 层即可获得 cost 数据。Classification: successor plan required。
- **SmartModelRouter**：设计 §6.3 的 6 步 Smart Router 管线，消费 `IBudgetProvider` 快照做预算感知路由。Classification: successor plan required（独立大 work item）。
- **token-only 预算的 `exceeded` 表达**：当前裁定 `exceeded` 仅基于 cost（estimatedTotalCost vs budgetLimit）。如后续需要"token 预算超限触发降级"（区别于"token 预算超限触发上下文压缩"），可在 `BudgetSnapshot` 增加 `tokenBudgetLimit` + `tokenExceeded` 字段，或由 router 直接读 `ctx.getTokensUsed()` 自行判断。Classification: design-refinement deferred to SmartModelRouter。

## Closure

Status Note: L2-22 预算控制 hook 已交付。`IBudgetProvider` 扩展点 + `BudgetSnapshot` 数据对象 + `NoOpBudgetProvider` 默认已落地并接线到 ReAct 循环 `route()` 调用前。shipped 默认行为零变化（NoOp 返回无限制快照 + PassThroughModelRouter 不改变模型）。功能性 router 可通过 `ctx.getBudgetSnapshot()` 读取预算并据此降级模型。
Completed: 2026-06-16

Closure Audit Evidence:

独立 closure-audit 子 agent（task explore，read-only）逐条核对 10 项 Closure Gates，结果 10/10 PASS：

1. `budget/IBudgetProvider.java:39-50` — 接口存在，唯一方法 `getBudget(AgentExecutionContext)`，契约禁止返回 null。
2. `budget/BudgetSnapshot.java:36-108` — 4 字段齐全；`exceeded` 在构造函数 line 60-61 计算（`cost != null && limit != null && cost >= limit`），构造函数仅 3 参数（无 exceeded 参数，调用方无法传入不一致值）。
3. `budget/NoOpBudgetProvider.java:29-48` — 单例，`getBudget` 返回 `(null, tokens, null)` = 无限制快照。
4. `engine/AgentExecutionContext.java:41,209-215` — `budgetSnapshot` 字段 + getter/setter。
5. `engine/DefaultAgentEngine.java:142,725-731,1597` — `budgetProvider` 字段（默认 NoOp）+ setter/getter + `resolveExecutor` 调 `.budgetProvider(this.budgetProvider)`。
6. `engine/ReActAgentExecutor.java:730-737` — `getBudget(ctx)` → null-guard（抛 NopAiAgentException）→ `setBudgetSnapshot` → `route()`，刷新在 route() 前、在上一轮 token 累积（line 787）之后。
7. `engine/ReActAgentExecutor.java:172,202,248,560-563,604` — 构造函数 + Builder 接收 `IBudgetProvider`，null 安全降级 NoOp。
8. Anti-Hollow：(a) `setBudgetSnapshot` 在 reactLoop 活路径（line 735），e2e 测试断言每轮 route() 时 ctx.getBudgetSnapshot() 非 null；(b) engine→executor→ctx→router 路径完整连通；(c) NoOpBudgetProvider 为显式 pass-through（文档化），无空方法体/静默跳过。
9. 测试：4 个测试类（TestBudgetSnapshot 8 tests / TestNoOpBudgetProvider 4 tests / TestInMemoryBudgetProvider 7 tests / TestBudgetProviderWiring 4 tests = 23 tests）。e2e `budgetExceededTriggersModelDowngradeEndToEnd` 注入 InMemoryBudgetProvider(limit=$1.00) + budget-aware router，断言 premium→premium→cheap 降级链 + 每轮 snapshot 非 null。
10. `nop-ai-agent-usage-and-billing.md` §3.6 line 212 标注 ✅ 已落地；`nop-ai-agent-roadmap.md` L2-22 line 188 标注 ✅（plan 206）+ Layer 2 验收标准 line 200 新增。

构建/测试：`./mvnw test -pl nop-ai/nop-ai-agent -am` → 1649 tests, 0 failures（1626 既有 + 23 新增，零回归）。

Follow-up:

- `DbBudgetProvider`（生产 DB-backed 实现）— successor plan required
- `SmartModelRouter`（功能性 router）— successor plan required
- token-only 预算的 `exceeded` 表达 — design-refinement deferred to SmartModelRouter
