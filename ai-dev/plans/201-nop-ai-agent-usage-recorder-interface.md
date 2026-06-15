# 201 nop-ai-agent IUsageRecorder 接口 + NoOpUsageRecorder + UsageRecord 数据对象（L2-17）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-17
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-17 ❌ 未实现）；`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` §3.2 + §6 P0 优先级（design 已完成 2026-06-15）
> Related: L2-18（DbUsageRecorder 实现依赖本计划交付的接口）、L2-20（per-model 聚合查询依赖 L2-18）、L2-22（预算控制 hook 依赖 L2-20）

## Purpose

为 nop-ai-agent 引入 `IUsageRecorder` 扩展点接口、`NoOpUsageRecorder` pass-through 默认实现和 `UsageRecord` 数据对象，并将其接线到 ReAct 循环的 token 累积点，使每次 LLM 调用的用量数据可通过该接口被记录。本计划只负责这一件事：建立用量追踪的接口契约和 pass-through 接线，为 L2-18（DbUsageRecorder）和 L2-20（per-model 聚合查询）提供落地基础。当前用量追踪完全缺失——`AgentSession.totalTokensUsed` 是单个 `long` 标量，每次 LLM 调用只做 `promptTokens + completionTokens` 求和后丢失 model 维度和 input/output 分项。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IUsageRecorder` / `NoOpUsageRecorder` / `UsageRecord` / `ModelUsageSummary` 全部不存在**（grep 确认零结果）。
- **`AgentSession.totalTokensUsed` 是单个 `long` 标量**（`session/AgentSession.java:17`），无 per-model 分项。
- **ReAct 循环 token 累积点位于 `ReActAgentExecutor.java:652-660`**：在 `response.getUsage() != null` 条件内，将 `promptTokens + completionTokens` 求和后写入 `ctx.setTokensUsed()`。此处 `routedOptions`（来自 `IModelRouter.route()` 返回值，line 630）含 model 信息，`response` 含 usage 信息——构造 `UsageRecord` 所需数据在此处全部可用。
- **现有 Layer 2 扩展点接线模式（以 `IModelRouter` 为参照）**：
  - `DefaultAgentEngine` 字段声明（`engine/DefaultAgentEngine.java:97` `private final IModelRouter modelRouter;`）
  - `DefaultAgentEngine.resolveExecutor` Builder 链传递（`DefaultAgentEngine.java:1502` `.modelRouter(modelRouter)`）
  - `ReActAgentExecutor.Builder` 字段 + setter（`ReActAgentExecutor.java:243` field, `:329-330` setter）
  - `ReActAgentExecutor.Builder.build()` null 兜底（`ReActAgentExecutor.java:515`）
  - `ReActAgentExecutor` 构造器参数 + null 兜底（`ReActAgentExecutor.java:168` param, `:195` null-check）
  - `ReActAgentExecutor` 运行时调用（`ReActAgentExecutor.java:629` `modelRouter.route(...)`）
- **`DefaultAgentEngine` 新增字段采用 mutable + setter 模式**（如 `permissionMatrix` / `denialLedger` / `postDenialGuard`，lines 105-110，非 `final`，有 setter），与 `modelRouter` 的 `final` 构造器注入模式不同。IUsageRecorder 应采用 mutable + setter 模式（与近期新增的 Layer 2/3 组件一致）。
- **`DefaultAgentEngine.resolveExecutor` Builder 链当前传递 25 个组件**（`DefaultAgentEngine.java:1492-1519`），IUsageRecorder 将作为第 26 个。
- **L1-5（ReActExecutor）✅ 已完成**——本计划依赖的 ReAct 循环核心实现稳定可用。
- **设计文档 `nop-ai-agent-usage-and-billing.md` 已完成**（2026-06-15），§3.2 定义了 `IUsageRecorder` 接口形态、`UsageRecord` / `ModelUsageSummary` 数据对象字段、§6 给出实施优先级表（L2-17 = P0）。
- **设计文档与 roadmap 的差异**：设计 §3.2 将 `summarizeByModel(sessionId)` 放在 `IUsageRecorder` 接口上，但 roadmap L2-20 将 per-model 聚合查询放在 `NopAiChatResponseBizModel` 上。本计划裁定见 Scope § 设计裁定。
- **roadmap §5b**：L2-17 尚无独立 roadmap 追踪行（§4 Layer 2 表格中标 ❌）。

## Goals

- `IUsageRecorder` 接口存在，含 `record(UsageRecord)` 方法，语义为"记录一次 LLM 调用的用量数据"。
- `UsageRecord` 数据对象存在，包含设计 §3.2 定义的字段（sessionId、agentName、requestId、modelId、aiProvider、aiModel、promptTokens、completionTokens、responseDurationMs、responseTimestamp）。
- `NoOpUsageRecorder` pass-through 默认实现存在（`record()` 为 no-op，singleton 模式与现有 NoOp 组件一致）。
- `IUsageRecorder` 接线到 `DefaultAgentEngine`（mutable 字段 + setter + resolveExecutor Builder 链传递）和 `ReActAgentExecutor`（Builder 字段 + setter + 构造器 null 兜底 + 运行时调用）。
- ReAct 循环 token 累积点（`ReActAgentExecutor.java:652-660`）调用 `usageRecorder.record(usageRecord)`，NoOp 默认下为 no-op，但不影响现有行为。
- Focused 测试验证：NoOp 默认接线、`record()` 在 token 累积点被调用（使用可计数的 test-double 验证调用次数和参数正确性）、`UsageRecord` 字段正确构造。
- roadmap §4 Layer 2 表格 L2-17 从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **L2-18（DbUsageRecorder 实现）**：写 `NopAiChatResponse` 表的实际持久化逻辑是独立 successor。本计划只交付接口 + NoOp 默认 + 接线。
- **L2-19（NopAiModel 定价列）**：ORM 模型变更，无依赖，独立 work item。
- **L2-20（per-model 聚合查询 `summarizeByModel`）**：SQL GROUP BY 查询放在 `NopAiChatResponseBizModel`，独立 work item，依赖 L2-18。
- **L2-21（model-switched 消息产生）**：ReAct 循环中 IModelRouter 返回后检查模型变更写 role=80 消息，依赖 L2-10（✅），独立 work item。
- **L2-22（预算控制 hook）**：IModelRouter 查询已用预算决定降级，依赖 L2-20 + L2-10。
- **`summarizeByModel` / `ModelUsageSummary`**：设计 §3.2 将其放在 `IUsageRecorder` 接口上，但本计划裁定将其从接口移除，deferred 到 L2-20 放在 `NopAiChatResponseBizModel`（见 Scope § 设计裁定）。不在 `IUsageRecorder` 上放置未被任何实现消费的方法。
- **`warnIfInsecureDefaults` WARN 机制扩展**：IUsageRecorder 不是安全组件，用量追踪缺失不构成安全风险（NoOp 下系统正常运行，只是不记录用量数据），无需 WARN。如后续需要"用量追踪未启用"的可见性提示，是独立 enhancement。
- **`AgentSession` per-model 内存分项**：设计 §4.1 已拒绝在内存维护 per-model Map，per-model 聚合是 DB 查询职责（L2-20）。

## Scope

### 设计裁定

**`summarizeByModel` 不放在 `IUsageRecorder` 接口上**：设计 §3.2 将 `List<ModelUsageSummary> summarizeByModel(sessionId)` 放在 `IUsageRecorder` 接口，但 roadmap L2-20 的实施计划将 per-model 聚合查询放在 `NopAiChatResponseBizModel`（nop-ai-service 层的 BizModel）。理由：(1) `summarizeByModel` 是查询方法，不是运行时扩展点行为；(2) 聚合查询需要 SQL + ORM 能力，属于 service 层职责而非 agent 运行时组件；(3) 在 `IUsageRecorder` 上放置未被 NoOpUsageRecorder 之外任何实现消费的方法违反 no-hollow-contract 原则。`ModelUsageSummary` 数据对象一并 deferred 到 L2-20。

**IUsageRecorder 采用 mutable + setter 模式**：与 `permissionMatrix` / `denialLedger` / `postDenialGuard` 等近期新增的 Layer 2/3 组件一致（非 `final` 字段 + setter），而非 `modelRouter` 的 `final` 构造器注入模式。理由：集成商需要在构造后替换 recorder 实现（如测试中注入 InMemoryUsageRecorder），mutable + setter 更灵活。

### In Scope

- 新增 `IUsageRecorder` 接口（含 `record(UsageRecord)` 方法）
- 新增 `UsageRecord` 数据对象（设计 §3.2 定义的字段）
- 新增 `NoOpUsageRecorder` pass-through 默认实现（singleton）
- `DefaultAgentEngine`：新增 `usageRecorder` mutable 字段（默认 `NoOpUsageRecorder`）+ `setUsageRecorder` setter（null 兜底为 NoOp）+ `resolveExecutor` Builder 链增加 `.usageRecorder(usageRecorder)`
- `ReActAgentExecutor`：新增 `usageRecorder` 字段 + 构造器参数 + null 兜底 + Builder 字段 + setter + `build()` null 兜底
- `ReActAgentExecutor` token 累积点（lines 652-660）：新增 `usageRecorder.record(usageRecord)` 调用，UsageRecord 从 ctx + routedOptions + response 构造
- Focused 测试：NoOp 默认接线 + record() 调用验证 + UsageRecord 字段验证
- 设计文档 `nop-ai-agent-usage-and-billing.md` 更新（标注 `summarizeByModel` 裁定移至 L2-20 BizModel）
- roadmap §4 Layer 2 表格 L2-17 状态更新

### Out Of Scope

- L2-18 ~ L2-22 全部独立 work items（见 Non-Goals）
- `NopAiChatResponse` 表写入逻辑（L2-18）
- `NopAiModel` ORM 定价列变更（L2-19）
- SQL 聚合查询（L2-20）
- `model-switched` 消息产生（L2-21）

## Execution Plan

### Phase 1 - 接口定义 + 数据对象 + NoOp 默认 + 引擎接线

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/`（新包）、`engine/DefaultAgentEngine.java`、`engine/ReActAgentExecutor.java`

- Item Types: `Fix`（接口缺失 = contract gap）

- [x] 新增 `IUsageRecorder` 接口，含 `void record(UsageRecord record)` 方法
- [x] 新增 `UsageRecord` 数据对象（sessionId、agentName、requestId、modelId、aiProvider、aiModel、promptTokens、completionTokens、responseDurationMs、responseTimestamp）
- [x] 新增 `NoOpUsageRecorder` pass-through 默认实现（singleton + `noOp()` 静态工厂，`record()` 为 no-op）
- [x] `DefaultAgentEngine` 新增 `private IUsageRecorder usageRecorder = NoOpUsageRecorder.noOp();` 字段 + `setUsageRecorder` setter（null 兜底 NoOp）
- [x] `DefaultAgentEngine.resolveExecutor` Builder 链增加 `.usageRecorder(usageRecorder)`（位于 `:1518` `.memoryStoreProvider(...)` 之后、`.build()` 之前）
- [x] `ReActAgentExecutor.Builder` 新增 `private IUsageRecorder usageRecorder;` 字段 + `usageRecorder(IUsageRecorder)` setter
- [x] `ReActAgentExecutor.Builder.build()` 新增 `usageRecorder != null ? usageRecorder : NoOpUsageRecorder.noOp()` null 兜底（位于 `:530` memoryStoreProvider 之后）
- [x] `ReActAgentExecutor` 构造器新增 `IUsageRecorder usageRecorder` 参数 + 字段赋值 + null 兜底
- [x] `ReActAgentExecutor` token 累积点（lines 652-660）：在 `ctx.setTokensUsed(...)` 之后新增 `usageRecorder.record(usageRecord)` 调用。字段来源裁定：sessionId ← ctx.getSessionId()、agentName ← ctx 或 agentName 变量、requestId ← response.getRequestId()、aiProvider ← routedOptions.getProvider()、aiModel ← routedOptions.getModel()、promptTokens ← 已计算的 promptTokens、completionTokens ← 已计算的 completionTokens、responseTimestamp ← System.currentTimeMillis()、responseDurationMs ← null（L2-17 无 LLM 调用耗时计量，见 Non-Blocking Follow-ups）、modelId ← null（agent 运行时层无 DB 查找能力，NopAiModel 实体主键由 L2-18 DbUsageRecorder 在持久化时解析填充）

- [x] 新增 `TestUsageRecorderWiring`：注入 test-double `RecordingUsageRecorder` 到 `DefaultAgentEngine`，运行 ReAct 循环，断言 `record()` 被调用、调用次数 = LLM 调用次数、`UsageRecord` 字段正确（sessionId 匹配、promptTokens/completionTokens 匹配 response usage、aiProvider/aiModel 匹配 routedOptions）
- [x] 新增 `TestNoOpUsageRecorder`：验证 `NoOpUsageRecorder.noOp()` singleton 语义（同一实例）、`record()` 不抛异常
- [x] 新增 `TestUsageRecord`：验证 UsageRecord 数据对象的字段读写

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `IUsageRecorder.java`、`UsageRecord.java`、`NoOpUsageRecorder.java` 文件存在于 `usage/` 包下
- [x] `DefaultAgentEngine` 含 `usageRecorder` 字段、`setUsageRecorder` setter、resolveExecutor Builder 链含 `.usageRecorder(usageRecorder)`
- [x] `ReActAgentExecutor` 构造器含 `IUsageRecorder` 参数、Builder 含 `usageRecorder` 字段 + setter + build() null 兜底
- [x] `ReActAgentExecutor` token 累积点（原 lines 652-660 区域）含 `usageRecorder.record(...)` 调用
- [x] **接线验证**：`TestUsageRecorderWiring` 通过——test-double 的 `record()` 被调用，UsageRecord 字段正确（Minimum Rules #23）
- [x] **端到端验证**：从 `DefaultAgentEngine.execute()` → ReAct 循环 → `usageRecorder.record()` 完整调用链验证通过（Minimum Rules #22）
- [x] **无静默跳过**：`NoOpUsageRecorder.record()` 虽为 no-op，但它是显式的 pass-through 设计（非缺失功能的隐藏），接口 javadoc 须注明此语义（Minimum Rules #24）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（含新增测试 + 既有 1595 tests 零回归）
- [x] `ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` 更新：§3.2 标注 `summarizeByModel` / `ModelUsageSummary` 裁定移至 L2-20 BizModel
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Roadmap 更新 + 收尾验证

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`

- [x] roadmap §4 Layer 2 表格 L2-17 从 ❌ → ✅，标注 plan 201
- [x] roadmap §4 Layer 2 验收标准更新（L2-17 相关条目标注完成）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap §4 Layer 2 表格 L2-17 标注 ✅ + plan 201
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IUsageRecorder` 接口 + `UsageRecord` 数据对象 + `NoOpUsageRecorder` pass-through 默认全部存在
- [x] 接线完整：DefaultAgentEngine → resolveExecutor Builder → ReActAgentExecutor 构造器 → token 累积点 record() 调用
- [x] Focused 测试通过（record() 调用验证 + UsageRecord 字段验证 + NoOp 默认行为验证）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（L2-18~L2-22 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步：`nop-ai-agent-usage-and-billing.md`（summarizeByModel 裁定标注）、roadmap §4（L2-17 ✅）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 record() 在运行时确实被调用（通过 test-double 计数验证），NoOp 默认是显式 pass-through 而非缺失功能隐藏
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；L2-18~L2-22 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`summarizeByModel` / `ModelUsageSummary` 接口形态裁定**：设计 §3.2 将其放在 IUsageRecorder 上，本计划裁定移至 L2-20 的 `NopAiChatResponseBizModel`。如 L2-20 执行时发现放在 BizModel 不合适，可重新裁定放回 IUsageRecorder。Classification: design-refinement deferred to L2-20。
- **`responseDurationMs` 字段填充**：L2-17 的 UsageRecord 含此字段，但当前 ReAct 循环 token 累积点没有 LLM 调用耗时计量。Phase 1 可填 0 或 null（nullable），实际计时逻辑是 L2-18 的实施细节。Classification: optimization candidate。

## Closure

Status Note: 完成。`IUsageRecorder` 接口（仅 `record(UsageRecord)`，裁定移除 `summarizeByModel` → L2-20）、`UsageRecord` 数据对象（10 字段）、`NoOpUsageRecorder` pass-through 默认（singleton）全部交付并接线到 ReAct 循环 token 累积点。mutable field + setter 模式与近期 Layer 2/3 组件一致。Anti-hollow 验证通过：test-double 计数证明 record() 在运行时被调用（调用次数 = LLM 调用次数）。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: Independent closure-audit subagent (general agent, task_id `ses_1338d2769ffekMqq9ti8d6EZUF`, fresh session — not the implementation session). Verdict: AUDIT_PASS.
- Audit Session: ses_1338d2769ffekMqq9ti8d6EZUF
- Evidence:
  - Item 1 (files exist): PASS — `IUsageRecorder.java` 仅 `record(UsageRecord)` 单方法（无 `summarizeByModel`），`UsageRecord.java` 全 10 字段，`NoOpUsageRecorder.java` singleton；`rg "ModelUsageSummary"` nop-ai/ 零结果（裁定落实）。
  - Item 2 (DefaultAgentEngine wiring): PASS — `DefaultAgentEngine.java:125` mutable 字段默认 NoOp，`:673-675` setter null→NoOp 兜底，`:1543-1544` resolveExecutor Builder 链 `.usageRecorder(...)` 在 `.build()` 前。
  - Item 3 (ReActAgentExecutor wiring): PASS — `ReActAgentExecutor.java:160` final 字段，`:188` 构造器参数 + `:230` null→NoOp，`:265` Builder 字段 + `:510-513` setter + `:550` build() null 兜底。
  - Item 4 (token accumulation point): PASS — `:672-694`，`setTokensUsed` 在 `:675`，`record()` 在 `:694`，字段来源全部核对（sessionId/agentName/requestId←response.getRequestId()/aiProvider←routedOptions.getProvider()/aiModel←routedOptions.getModel()/promptTokens/completionTokens/responseTimestamp）。
  - Item 5 (Anti-Hollow): PASS — `TestUsageRecorderWiring.recordInvokedOncePerLlmCallWithCorrectFields` 跑完整 ReAct loop via engine.execute()，断言 2 次 LLM 调用 = 2 次 record() 调用，字段匹配（test-provider/test-model/100/20/150/30）。接线在运行时确实连通。
  - Item 6 (build/tests): PASS — `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS，1605 tests 全绿（1595 baseline + 10 new）。
  - Item 7 (docs sync): PASS — usage-and-billing.md §3.2 `:85-100` 裁定标注存在；roadmap §4 `:183` L2-17 ✅ + plan 201。
  - Item 8 (code style): PASS — imports 分组正确，javadoc 符合 NoOpCheckpoint 约定，`// Plan 201` 注释符合既有 `// Plan 192/187` 模式。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/201-nop-ai-agent-usage-recorder-interface.md --strict` 退出码为 0（all checklist items ticked + Closure Evidence written）。
  - Anti-Hollow: record() 经 test-double 计数证明在运行时被调用（非 dead wiring）；NoOpUsageRecorder 是显式 pass-through（javadoc 标注），非缺失功能隐藏。

Follow-up:

- L2-18（`DbUsageRecorder` 写 `NopAiChatResponse` 持久化）——本计划交付的 `IUsageRecorder` 接口 + 接线是 L2-18 的直接前置依赖；L2-18 实现时填充 `modelId`（解析 NopAiModel 主键）与 `responseDurationMs`（LLM 调用计时），二者在 L2-17 agent 运行时层为 null（见 Non-Blocking Follow-ups）。
- L2-20（per-model 聚合查询 `summarizeByModel`）——裁定放在 `NopAiChatResponseBizModel`（service 层），依赖 L2-18。`ModelUsageSummary` 数据对象一并 deferred 到 L2-20。

## Follow-up handled by 202-nop-ai-agent-db-usage-recorder.md

L2-18（Follow-up 第一条）已由 successor plan `ai-dev/plans/202-nop-ai-agent-db-usage-recorder.md` 接管：实现 `DbUsageRecorder`（raw JDBC 写 `nop_ai_chat_response` 表）、补齐 `modelId` 解析（SELECT `nop_ai_model` 按 provider+model）、补齐 `responseDurationMs`（ReAct 循环 LLM 调用耗时计量）。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。

## Follow-up handled by 203-nop-ai-agent-per-model-usage-aggregation.md

L2-20（Follow-up 第二条：per-model 聚合查询 `summarizeByModel`）已由 successor plan `ai-dev/plans/203-nop-ai-agent-per-model-usage-aggregation.md` 接管：在 `NopAiChatResponseBizModel`（service 层）实现 `summarizeByModel(sessionId)` SQL GROUP BY 聚合查询 + `ModelUsageSummary` 数据对象。依赖 L2-18（plan 202 ✅）。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
