# 213 nop-ai-agent 熔断感知路由（Circuit-Aware Routing）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: circuit-aware-routing
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/210-nop-ai-agent-circuit-breaker.md`（Non-Goals 第三条：`熔断 OPEN 时自动模型回退：router 感知 circuit 状态并主动跳过 circuit-broken 模型，路由到 circuit-closed 的备选模型。Classification: successor plan required`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §3.3（平台层：断路器 + 模型回退）+ §5.2（模型回退）；`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §6.3（Smart Router 六步管线 Step 5 回退链）+ §6.5（Fallback 错误分类）
> Related: `210`（交付 `ICircuitBreaker` + `ThresholdBreaker`，熔断检查接线到 retry 循环外层，本计划把 circuit 检查从"拒绝即终止"升级为"拒绝→回退→续跑"）、`209`（交付 `SmartModelRouter` 回退链 `getFallback`，本计划复用该链做 circuit-aware 解析）、`207`（交付 `IRetryPolicy` retry 循环，retry 处理单次调用周期瞬态故障，circuit-aware routing 处理跨调用周期连续故障模式的主动回避）

## Purpose

把 nop-ai-agent 引擎对 circuit-OPEN 模型的处理从"主模型 circuit OPEN → 抛 `NopAiAgentException` 终止整个 agent 执行"升级为"主模型 circuit OPEN → 主动沿 router 回退链查找 circuit-closed 的健康模型 → 切换到健康模型续跑"。本计划交付一个位于 `route()` 与 retry 循环之间的 **circuit-aware 路由解析步骤**：当路由返回的主模型被熔断器判定为不可调用（OPEN 或 HALF_OPEN probe 占用），解析步骤沿 `IModelRouter.getFallback(...)` 链逐个查询直到找到一个 circuit 允许调用的模型；全部回退链耗尽仍不可调用时才 fail-fast。模块定位为"面向大规模无人值守自动化执行"，设计 §5.1 明确"当某个模型或 provider 连续失败时，继续调用它只会浪费时间和 token"——plan 210 交付了熔断器（拒绝调用），本计划交付熔断后的自动降级（路由到健康模型），使单 provider 故障不再终止整个无人值守 agent 运行。

## Current Baseline

基于 live repo 与设计文档核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`ICircuitBreaker` 契约已落地**（plan 210 ✅）：`reliability/ICircuitBreaker.java` 提供 `allowCall(modelKey)` / `getState(modelKey)` / `recordSuccess(modelKey)` / `recordFailure(modelKey)` 四方法，按 `provider:model` 复合键追踪 per-model circuit 状态。`AlwaysClosed`（shipped 默认，恒放行）+ `ThresholdBreaker`（功能性 opt-in，连续失败达阈值→OPEN→冷却后 HALF_OPEN→probe 成功→CLOSED）均已实现。
- **熔断检查接线到 retry 循环外层**（plan 210 ✅）：`ReActAgentExecutor.java:994-1005` 在进入 retry 循环前检查**主模型**的 circuit 状态（`primaryModelKey = buildModelKey(routedOptions)`，`routedOptions` 在此时尚未被 FALLBACK 切换）。`allowCall` 返回 false（circuit OPEN 或 HALF_OPEN probe 被占）→ 直接 `throw new NopAiAgentException(...)`，**终止整个 agent 执行**。当前异常消息已含指引（"Configure an IModelRouter fallback chain or wait for the breaker cooldown"），但引擎**不主动查询回退链**。
- **`IModelRouter` 接口 + `getFallback` 回退链已落地**（plan 209 ✅）：`router/IModelRouter.java` 提供 `route(messages, options, ctx)` + `getFallback(currentOptions)` default 方法（默认返回 null = 无回退能力）。`PassThroughModelRouter`（shipped 默认）直连配置模型、`getFallback` 返回 null。`SmartModelRouter`（opt-in）维护 per-tier 有序回退链：`getFallback` 在链中定位 `currentOptions` 的 model-key，返回后继模型的合并 options（保留 tools/settings），链尾返回 null。
- **`SmartModelRouter.getFallback` 已被 retry 循环消费**（plan 209 ✅）：`ReActAgentExecutor.java:1066` 在 `RetryDecision.FALLBACK` 分支调用 `modelRouter.getFallback(routedOptions)`——但这是**retry 循环内部**的被动消费（单次调用周期内的瞬态失败触发），**不是** circuit-OPEN 时的主动回避。circuit check 在 retry 循环**外层**（`:994-1005`），两者不在同一层。
- **router 包零引用 circuit breaker**：grep `circuitBreaker|CircuitBreaker|allowCall|CircuitState` 在 `router/` 包下零命中——路由器完全不知道 circuit 状态。当前 circuit 感知只存在于 executor 的 retry 循环外层检查点。
- **route() 调用点**：`ReActAgentExecutor.java:929` `RoutingResult routingResult = modelRouter.route(ctx.getMessages(), options, ctx)` → `:930` `ChatOptions routedOptions = routingResult.getOptions()`。随后 `:940-948` 做 model-switched 审计消息检测（plan 205），`:994-1005` 做 circuit check。route() 与 circuit check 之间**无任何 circuit-aware 解析步骤**。
- **executor 同时持有 circuitBreaker 与 modelRouter**：`ReActAgentExecutor` 有 `circuitBreaker` 字段（plan 210）和 `modelRouter` 字段（plan 205/209），两者均通过 `DefaultAgentEngine` field+setter+resolveExecutor 装配。executor 是同时访问这两个依赖的唯一位置，适合作为 circuit-aware 解析的编排点。
- **buildModelKey 复合键语义统一**：`ReActAgentExecutor.buildModelKey(ChatOptions)` 生成 `provider:model` 复合键，被 circuit breaker（`:994`）、model-switched 检测（`:939-940`）、usage attribution（plan 202）、fallback 切换日志（`:1090,1101`）共用。circuit-aware 解析复用同一方法保证 model-key 语义一致。
- **model-switched 审计消息机制已就位**（plan 205 ✅）：`:940-948` 在 `route()` 返回后比较 `currentModelKey` 与 `lastModelKey`，变更时通过 `IModelSwitchedMessageWriter` 持久化 role=80 审计消息。如果 circuit-aware 解析在 route() 之后改变了 `routedOptions`，model-switched 检测会自然捕获这个变更（只要解析在检测之前完成）。
- **设计 §5.2 模型回退为占位段落**：`nop-ai-agent-reliability.md:279-283` §5.2 仅有两句话——"当主模型不可用时，系统可以沿着有序回退链切换到备用模型。这类能力与断路器天然配套，但同样属于 Layer 3 可靠性扩展，应在 Layer 1-2 稳定后实施。"无实现状态标注（plan 209 的回退链消费标注在 llm-layer.md §6.5，不在 §5.2）。
- **roadmap 无独立行项**：circuit-aware routing 是 plan 210 的 Non-Goal successor，roadmap §4 无对应行（L2-10 IModelRouter ✅ / L2-23 SmartModelRouter ✅ / L3-1 ICircuitBreaker ✅ 均已关闭，本计划是三者交叉点的增强）。

## Goals

- 在 `ReActAgentExecutor` 的 `route()` 返回后、retry 循环外层 circuit check 前，新增一个 **circuit-aware 路由解析步骤**：当路由返回的主模型被 `circuitBreaker.allowCall(modelKey)` 判定为不可调用时，主动沿 `IModelRouter.getFallback(...)` 链逐个查询，直到找到一个 `allowCall == true` 的健康模型，将 `routedOptions` 切换到该模型。
- 全部回退链耗尽仍无 circuit-closed 模型可用时，fail-fast 抛 `NopAiAgentException`（含已检查的所有 model-key + 各自 circuit state + 指引），不静默继续、不吞异常（Minimum Rules #24）。
- circuit 解析触发的模型切换被 model-switched 审计消息机制（plan 205）自然捕获——解析必须在 model-switched 检测（`:940-948`）之前完成，使审计消息正确反映实际使用的模型。
- shipped 默认（`AlwaysClosed` + `PassThroughModelRouter`）行为**零回归**：`AlwaysClosed.allowCall` 恒返回 true → 解析步骤的首次检查即通过 → 不进入回退链查询。
- 受影响既有测试零回归（默认配置行为不变）。

## Non-Goals

- **让 router 自身 circuit-aware**（耦合 Layer 2 到 Layer 3）：解析步骤留在 executor（编排层），不改 `IModelRouter` 接口（不加新方法、不改 `route()` 签名）。router 仍按复杂度/预算路由；circuit 解析是 route() 之后的 post-processing。
- **retry 循环内 FALLBACK 模型的 circuit 检查**：plan 210 裁定 FALLBACK 切换后的备选模型不做 circuit 检查（FALLBACK 本身是对失败的响应）。本计划只覆盖 route() 返回的**主模型**的 circuit 主动回避，不改 retry 循环内的 FALLBACK 逻辑。
- **持久化 / 跨进程共享 circuit 状态**：`ThresholdBreaker` 状态仍是 in-memory per-instance（plan 210 Non-Goal）。跨进程共享依赖 L4-8 Actor Runtime，是独立 successor。
- **动态阈值校准**：滑动窗口失败率自适应阈值。plan 210 Non-Goal，optimization candidate。
- **XDSL 配置化**：`agent.xdef` `<circuit-routing>` 元素。本计划交付程序化行为（executor 内置解析逻辑），配置化是独立增强。
- **router 侧的 circuit 预判**（在 `route()` 内部就跳过 circuit-broken tier）：改变 router 路由逻辑本身（如 tier 选择时排除 circuit-OPEN 的 tier）是独立 successor。本计划只做 route() 之后的回退链解析。

## Scope

### In Scope

- `ReActAgentExecutor`：route() 与 retry 循环外层 circuit check 之间新增 circuit-aware 解析逻辑（检查主模型 circuit → 必要时沿 `getFallback` 链查找健康模型 → 更新 `routedOptions`；全部不可用则 fail-fast）。
- `ReActAgentExecutor`：解析步骤的接线点裁定（必须在 model-switched 检测之前，使审计消息正确）。
- `ReActAgentExecutor`：原有 `:994-1005` circuit check 的角色调整（解析步骤已确保模型 circuit-cleared，原检查转为 safety-net 或由解析步骤统一替代——Phase 1 裁定具体方式）。
- 测试：默认配置零回归 / circuit-closed 主模型正常通过 / circuit-OPEN 主模型→回退到健康模型 / 全部回退 circuit-OPEN→fail-fast / circuit 解析触发 model-switched 审计消息 / PassThroughModelRouter（无回退链）circuit-OPEN→fail-fast。

### Out Of Scope

- router 自身 circuit-aware / retry 循环内 FALLBACK circuit 检查 / 持久化 circuit 状态 / 动态阈值 / XDSL 配置化 / router 侧 tier 预判（理由见 Non-Goals）。

## Execution Plan

### Phase 1 - 设计裁定 + Circuit-Aware 解析步骤接线（零回归默认）

Status: completed
Targets: `ReActAgentExecutor`（解析逻辑 + 接线点）

- Item Types: `Decision`、`Proof`

- [x] 裁定并落档 **解析步骤的编排位置**（**Resolved (a) — 位置**）：circuit-aware 解析在 `route()` 返回（`:929-930`）之后、model-switched 审计检测（`:940-948`）之前执行。理由：(1) 解析可能改变 `routedOptions`，model-switched 检测必须看到解析后的最终模型才能正确产生审计消息；(2) 解析在 retry 循环 circuit check（`:994-1005`）之前，使到 check 执行时模型已被解析为 circuit-cleared（解析 + check 共享同一 `routedOptions` 引用）。解析逻辑封装为 executor 内私有方法 `resolveCircuitAware(...)`（不新增公共类型）。
- [x] 裁定并落档 **解析算法**（**Resolved**）：(1) 取 `routedOptions` 的 model-key，调 `circuitBreaker.allowCall(key)`；(2) 若 `true` → 主模型可用，解析结束（零开销路径，default AlwaysClosed 即此路径）；(3) 若 `false` → 进入回退链扫描：`current = routedOptions`，循环调 `modelRouter.getFallback(current)`，每次返回非 null 时检查 `allowCall(getFallback 的 model-key)`，找到 `true` 则返回该 fallback（解析完成）；(4) `getFallback` 返回 null（链耗尽）或回退模型全部 `allowCall == false` → 全部不可用，fail-fast。回退链扫描有上限（回退链长度，SmartModelRouter 的配置决定，通常 ≤5），无死循环风险；额外防御性硬上限（MAX_FALLBACK_SCAN=64）防 buggy 自定义 router 形成循环。
- [x] 裁定并落档 **全部不可用时的 fail-fast 行为**（**Resolved**）：抛 `NopAiAgentException`，消息含被检查的主 model-key + 其 circuit state + 回退链中检查过的 model-key 列表（key=state 形式）+ 指引（等待冷却 / 配置更多回退模型）。不静默继续、不吞异常（Minimum Rules #24）。
- [x] 裁定并落档 **原 `:994-1005` circuit check 的角色**（**Resolved (a) safety-net**）：解析步骤完成后 `routedOptions` 已是 circuit-cleared 模型。采用方案 (a) ——保留 `:994` check 作为 safety-net（解析已保证通过，此 check 理论上永不拒绝；若拒绝说明并发竞争导致 circuit 在解析后、check 前 trip——保留 fail-fast）。更新其上注释，说明解析步骤已保证 circuit-cleared，此 check 现为并发竞争 safety-net。
- [x] 裁定并落档 **回退链扫描的 routingReason 富化**（**Resolved**）：当 circuit 解析切换了模型，`RoutingResult.routingReason` 仍反映 route() 的原始决策（如 `complexity=complex`）。circuit 解析切换不修改 `RoutingResult`（它是 route() 的输出，不可变值对象），而是在 `LOG.warn` 中记录 circuit-induced switch（原 model-key → 新 model-key + circuit state），并在 model-switched 审计消息（plan 205）中自然体现（`routingReason` 字段保留原始路由原因，审计消息的 `fromModel`/`toModel` 反映实际切换）。不新增 routingReason 枚举值。
- [x] 实现 circuit-aware 解析步骤（executor 私有方法 `resolveCircuitAware(...)` + route() 后接线）
- [x] 确保 model-switched 审计检测（`:940-948`）使用解析后的 `routedOptions`（验证接线顺序正确——解析在检测之前）
- [x] 编写 focused 测试：默认配置（AlwaysClosed + PassThrough）零回归 / AlwaysClosed + SmartModelRouter 零回归（allowCall 恒 true，不进入回退扫描）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] circuit-aware 解析步骤存在于 `ReActAgentExecutor`，位于 `route()` 之后、model-switched 检测之前；`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] **接线验证**（Minimum Rules #23）：测试断言默认配置（AlwaysClosed + PassThroughModelRouter）下解析步骤不改变 `routedOptions`（主模型 `allowCall` 返回 true → 解析立即结束），LLM 调用正常执行——证明接线连通且零行为回归（`TestCircuitAwareRouting.defaultConfigResolutionIsNoOp` / `alwaysClosedWithSmartModelRouterDoesNotScanFallback`）
- [x] **无静默跳过**（Minimum Rules #24）：全部回退链 circuit-OPEN 时抛 `NopAiAgentException`（含已检查 model-key + circuit state + 指引），非静默返回 null/空/原模型（`TestCircuitAwareRouting.allModelsCircuitOpenFailsFastWithDiagnostic` / `passThroughRouterCircuitOpenFailsFast`）
- [x] **新增功能测试**（Minimum Rules #25）：默认配置零回归 / circuit-closed 主模型正常通过——每条新行为路径有对应测试断言（`TestCircuitAwareRouting` 8 tests）
- [x] 既有测试零回归：`./mvnw test -pl nop-ai/nop-ai-agent` 通过（1803 tests, 0 failures）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.2（模型回退）更新为实现状态（circuit-aware 路由解析已落地于 executor）；§3.3 同步更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 功能性 Circuit-Aware 路由验证（ThresholdBreaker + SmartModelRouter + 端到端）

Status: completed
Targets: `ReActAgentExecutor`（circuit 解析行为）、测试

- Item Types: `Proof`

- [x] 编写端到端测试：配置 `ThresholdBreaker`（failureThreshold=低值如 2）+ `SmartModelRouter`，主模型 circuit 预先 trip 到 OPEN（2 次 recordFailure）→ `route()` 返回主模型 → circuit 解析检测 OPEN → 沿回退链找到 circuit-closed 的 fallback → 切换到 fallback 续跑 → 断言 LLM 调用使用 fallback 的 model-key（`TestCircuitAwareRouting.endToEndRealThresholdBreakerPreTrippedResolvesToFallback`：pre-trip 真实 `ThresholdBreaker` + `SmartModelRouter` 三 tier 配置 + `DefaultAgentEngine` 端到端，断言 primaryCallCount=0、fallbackCallCount≥1）
- [x] 编写全部回退链 circuit-OPEN 的测试：配置 breaker 使所有模型 circuit 均 OPEN → route() 返回主模型 → 解析扫描全部回退 → 全部不可用 → fail-fast 抛 `NopAiAgentException` → 断言异常消息含所有检查过的 model-key + circuit state（`TestCircuitAwareRouting.allModelsCircuitOpenFailsFastWithDiagnostic`）
- [x] 编写 circuit 解析触发 model-switched 审计消息的测试：SmartModelRouter + capturing test writer + 主模型 circuit 在 iteration 1 trip（`PrimaryTripsAfterTwoAllowCalls` 状态ful breaker）→ 解析切换到 fallback → 断言 role=80 审计消息被写入（`fromModel` = 主模型 key `p:primary`，`toModel` = fallback key `p:fallback1`）（`TestCircuitAwareRouting.circuitResolutionTriggersModelSwitchedAuditMessage`）
- [x] 编写 PassThroughModelRouter（无回退链）circuit-OPEN 的测试：配置 breaker 使主模型 OPEN + PassThroughModelRouter → 解析检查 `getFallback` 返回 null → fail-fast → 断言行为与 plan 210 原 circuit check 一致（向后兼容）（`TestCircuitAwareRouting.passThroughRouterCircuitOpenFailsFast`）
- [x] 编写 HALF_OPEN probe 占用场景的测试：主模型 HALF_OPEN（`allowCall` 返回 false，probe slot 被占）→ 解析沿回退链找到 CLOSED fallback → 切换续跑（`TestCircuitAwareRouting.halfOpenProbeBusyResolvesToFallback`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `DefaultAgentEngine.execute()` 出发，经 ReAct 循环 → route() 返回 circuit-OPEN 主模型 → circuit 解析 → 回退到健康模型 → LLM 调用成功 → agent 继续执行，完整路径跑通；验证主模型未被调用（callCount 对主模型为 0）、fallback 模型被调用（`endToEndRealThresholdBreakerPreTrippedResolvesToFallback`）
- [x] **接线验证**（Minimum Rules #23）：测试断言 circuit 解析确实在运行时被调用——通过断言主模型 chatService callCount=0 + fallback 模型 callCount≥1 证明解析生效（而非直接调用 circuit-OPEN 模型后失败）（`circuitOpenPrimaryResolvesToCircuitClosedFallback` + 端到端测试）
- [x] **新增功能测试**（Minimum Rules #25）：circuit-OPEN→回退续跑 / 全部回退 OPEN→fail-fast / circuit 解析触发 model-switched 审计 / PassThrough 无回退→fail-fast / HALF_OPEN probe 占用→回退——每条路径有对应断言（`TestCircuitAwareRouting` 8 tests 全覆盖）
- [x] **无静默跳过**（Minimum Rules #24）：全部不可用时抛异常而非静默用 OPEN 模型；回退模型 circuit-closed 时真实调用（非 placeholder）（`allModelsCircuitOpenFailsFastWithDiagnostic` 断言 callCount=0 + 0 行为）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent` 通过（含既有测试零回归）（1803 tests, 0 failures, 0 errors）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §5.2 + §3.3 已更新为 circuit-aware routing 实现状态
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] circuit-aware 路由解析步骤落地于 `ReActAgentExecutor`（route() 之后、retry 循环之前）
- [x] 解析步骤在运行时确实被调用（不只代码存在）——主模型 circuit-OPEN 时确实触发回退链扫描
- [x] 默认配置（AlwaysClosed + PassThroughModelRouter）既有行为零回归
- [x] 必要 focused verification（零回归 / circuit-OPEN→回退续跑 / 全部 OPEN→fail-fast / model-switched 审计 / PassThrough 兼容 / HALF_OPEN probe）已完成
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（router 自身 circuit-aware / retry 内 FALLBACK circuit 检查 / 持久化 / 动态阈值 / XDSL / router tier 预判均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-reliability.md` §3.3/§5.2）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）circuit 解析在运行时确实被调用（主模型 OPEN 时回退链被扫描、fallback 模型被实际调用），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；router 自身 circuit-aware / retry 内 FALLBACK circuit 检查 / 持久化 / 动态阈值 / XDSL / router tier 预判均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **router 侧 tier 预判**：在 `SmartModelRouter.route()` 内部就跳过 circuit-OPEN 的 tier（而非 route() 之后再解析回退链）。Classification: successor plan required。当前 post-routing 解析已覆盖功能需求，tier 预判是路由决策层面的优化（减少不必要的 route → resolve 往返）。
- **持久化 / 跨进程 circuit 状态**：DB-backed 或集群范围共享 circuit 状态（某 provider 在全集群被熔断）。依赖 L4-8 Actor Runtime 或共享存储。Classification: successor plan required。
- **动态阈值校准**：基于滑动窗口失败率自适应调整 `ThresholdBreaker` 阈值。Classification: optimization candidate。
- **XDSL 配置化**：`agent.xdef` 声明 circuit-aware routing 开关。Classification: optimization candidate。
- **routingReason 富化**：circuit-induced switch 在 routingReason 中标注 `circuit-open->fallback`（当前 routingReason 保留 route() 原始决策，circuit 切换只在 LOG.warn + model-switched 审计消息中体现）。Classification: optimization candidate。

## Closure

Status Note: circuit-aware 路由解析步骤已落地于 `ReActAgentExecutor`（route() 之后、model-switched 审计检测之前的 post-processing 私有方法 `resolveCircuitAware`），把主模型 circuit OPEN 的处理从"拒绝即终止整个 agent 执行"（plan 210）升级为"拒绝→主动沿 `IModelRouter.getFallback(...)` 回退链查找健康模型→切换续跑"。两个 Phase 全部完成，默认配置（AlwaysClosed + PassThroughModelRouter）零回归（allowCall 恒 true → 解析 no-op）。plan 210 外层 circuit check 转为并发竞争 safety-net。全不可用时 fail-fast（Minimum Rules #24）。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session, task_id `ses_131724ecaffe1pOhnj37hsNqpI`，general agent type）
- Audit Session: `ses_131724ecaffe1pOhnj37hsNqpI`
- Evidence:
  - **每条 Exit Criterion 验证结果**：Phase 1 / Phase 2 所有 Exit Criteria PASS——解析步骤存在于 `ReActAgentExecutor:1918-1964`（私有方法，4 步算法，fail-fast 抛异常非返回 null，MAX_FALLBACK_SCAN=64 循环上限）；接线顺序经代码追踪证明 route()→resolveCircuitAware(`:952`)→model-switched 检测(`:962-971`)→safety-net check(`:1031-1042`)→retry loop；默认配置零回归（`AlwaysClosed.allowCall` 恒 true）
  - **每条 Closure Gate 验证结果**：11/11 PASS
  - **端到端验证（Minimum Rules #22）**：`TestCircuitAwareRouting.endToEndRealThresholdBreakerPreTrippedResolvesToFallback`——真实 `ThresholdBreaker(2,60s)` pre-trip 经 `DefaultAgentEngine.execute()` 端到端，断言 `primaryCallCount==0` + `fallbackCallCount>=1`
  - **接线验证（Minimum Rules #23）**：runtime 调用经 primaryCallCount==0 + fallbackCallCount>=1 断言证明（circuit-OPEN primary 在 LLM 调用前被解析拦截切换）
  - **无静默跳过（Minimum Rules #24）**：fail-fast 抛 NopAiAgentException 非 null；`allModelsCircuitOpenFailsFastWithDiagnostic` 断言 callCount==0
  - **`check-plan-checklist.mjs --strict`**：所有 checklist 已勾选（Closure Gates 全 `[x]`），Closure Evidence 已写入
  - **Anti-Hollow 检查结果**：GENUINELY WIRED AND RUNTIME-INVOKED（非 hollow）——解析在正确编排点（post-route/pre-audit/pre-safety-net），经正常 executor 装配可达（无 opt-in flag），端到端测试用真实 pre-tripped ThresholdBreaker 在 runtime 证明生效；`scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码为 0（发现的 high findings 均为 pre-existing 显式 pass-through no-op：AlwaysClosed/NoOpGoalTracker/NoOpSustainer，非 plan 213 新代码）
  - **Deferred 项分类检查**：无 in-scope live defect 被降级——router 自身 circuit-aware / retry 内 FALLBACK circuit 检查 / 持久化 / 动态阈值 / XDSL / router tier 预判均显式在 Non-Goals + Non-Blocking Follow-ups 切出并附 non-blocking 理由
- Closure Recommendation: APPROVE CLOSURE（独立 audit 10/10 items PASS）

Follow-up:

- router 侧 tier 预判（successor plan required）、持久化/跨进程 circuit 状态（successor plan required，依赖 L4-8）、动态阈值校准（optimization candidate）、XDSL 配置化（optimization candidate）、routingReason 富化（optimization candidate）——均为 Non-Blocking Follow-ups，无 plan-owned 剩余工作
