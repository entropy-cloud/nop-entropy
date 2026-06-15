# 205 nop-ai-agent model-switched 消息产生（L2-21）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-21
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-21 ❌ 未实现）；`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` §3.5（model-switched 消息产生设计 + 伪代码规格）；`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` §6（IModelRouter 设计，路由结果含复杂度分级 + 调整后的 ChatOptions）
> Related: `201`（IUsageRecorder 接口，已 completed）、`202`（DbUsageRecorder，已 completed）、`203`（per-model 聚合查询，已 completed）、`204`（NopAiModel 定价列，已 completed）；L2-10（IModelRouter + PassThroughModelRouter ✅ 前置依赖）；L2-22（预算控制 hook，本计划的后继依赖者）

## Purpose

在 ReAct 循环中 `IModelRouter.route()` 返回后，检查路由器选择的模型是否与上一轮不同。如果不同，产生一条 `model-switched` 审计消息（role=80 / `MESSAGE_TYPE_MODEL_SWITCHED`），记录模型切换事件（fromModel → toModel + routingReason + complexity）。这条消息是审计记录，不参与 LLM 推理上下文。本计划把 L2-21 的"常量已定义但从未产生"状态收口为"模型切换时自动产生审计消息"。

## Current Baseline

基于 live repo 核对（2026-06-16）：

- **`MESSAGE_TYPE_MODEL_SWITCHED = 80` 常量已定义但在 nop-ai-dao（不可从 nop-ai-agent 直接引用）**：此常量位于 `nop-ai-dao/src/main/java/io/nop/ai/dao/_NopAiDaoConstants.java`（生成文件）。grep 确认无任何生产代码产生 role=80 的消息——"常量已定义，但从未产生"。**关键约束**：`nop-ai-agent` 的 `pom.xml` 不依赖 `nop-ai-dao`（设计 §4.3 明确拒绝此依赖方向），因此 nop-ai-agent 代码**不能直接引用** `_NopAiDaoConstants.MESSAGE_TYPE_MODEL_SWITCHED`。Phase 1 须裁定如何获得 `80` 这个值：定义 nop-ai-agent 本地常量（如 `ROLE_MODEL_SWITCHED = 80`，附注释引用 nop-ai-dao 常量以保持可追溯性），参照 `AiAgentSessionTable` 定义本地表名/列名常量而非引用 nop-ai-dao 生成常量的既有模式。
- **`IModelRouter` 接口 + `RoutingResult` + `PassThroughModelRouter` 已就位**（L2-10 ✅）：
  - `RoutingResult` 含三个字段：`ChatOptions options`（`getOptions()`）、`String complexity`（`getComplexity()`）、`String routingReason`（`getRoutingReason()`）。**不存在** `selectedModelId` 字段——模型标识须从 `routingResult.getOptions().getProvider()` + `routingResult.getOptions().getModel()` 提取。
  - `PassThroughModelRouter` 是 pass-through 默认——返回 `new RoutingResult(options, null, "pass-through")`（complexity=null，routingReason="pass-through"），不改变模型。因此默认配置下不会产生 model-switched 消息。
- **ReAct 循环已在 `:649` 调用 `modelRouter.route()`**（`ReActAgentExecutor.java`）：路由结果用于 `chatService.call()`。plan 202 的 `UsageRecord` 构造（约 `:692-702`）已使用 `routedOptions.getProvider()` 和 `routedOptions.getModel()`，说明 provider/model 提取模式已建立。
- **无 lastModelId 跨轮次追踪**：当前 ReAct 循环不记录上一轮使用的模型。`AgentExecutionContext` 无模型追踪字段（已有字段：agentModel, messages, sessionId, chatOptionsModel, currentIteration, tokensUsed, status 等）。
- **`nop_ai_session_message` 表已存在**（nop-ai-dao ORM 模型 `nop-ai/model/nop-ai.orm.xml`）：含 15 列，关键约束：`ID`（VARCHAR PK）、`SESSION_ID`（NOT NULL）、`ROLE`（INTEGER NOT NULL，role=80 写入此列）、`SEQ`（BIGINT NOT NULL，唯一约束 `uk_nop_ai_session_msg_seq (sessionId, seq)`——写入时须为每个 session 计算严格递增的 seq）、`VERSION`（INTEGER NOT NULL）、`CREATED_BY`/`UPDATED_BY`（VARCHAR NOT NULL）、`CREATE_TIME`/`UPDATE_TIME`（TIMESTAMP）、内容/metadata 列。raw JDBC 写入时必须填充全部 NOT NULL 列。
- **nop-ai-agent 不依赖 nop-ai-dao**：所有 DB 写入组件使用 raw JDBC（`DataSource` + `PreparedStatement`），参照 `DbUsageRecorder`（`nop_ai_chat_response` 表）/ `DBSessionStore`（`ai_agent_session` 表）模式。**注意**：`DBMessageService` 写入的是 `ai_agent_message` 表（agent 间消息总线），不是 `nop_ai_session_message`——正确参照模式是 `DbUsageRecorder` + `NopAiChatResponseTable`（raw JDBC 写入 ORM 建模的表）。
- **roadmap §4 Layer 2**：L2-21 标 ❌ 未实现。roadmap §2 Layer 2 验收标准 line 199 "L2-21：模型切换时产生 `model-switched` 消息（role=80）" 未勾选。
- **设计文档 §3.5 伪代码规格**（行为语义，已按实际 API 修正——原设计文档 §3.5 使用了不存在的 `routingResult.selectedModelId`，本计划以实际 `RoutingResult` API 为准）：
  ```
  RoutingResult result = modelRouter.route(messages, options, ctx);
  String currentModelKey = result.getOptions().getProvider() + ":" + result.getOptions().getModel();
  if (lastModelKey != null && !currentModelKey.equals(lastModelKey)) {
      写 audit message(
          sessionId = ctx.sessionId,
          role = 80,
          metadata = {
              fromModel: lastModelKey,
              toModel: currentModelKey,
              routingReason: result.getRoutingReason(),
              complexity: result.getComplexity()
          }
      )
  }
  lastModelKey = currentModelKey;
  ```
- **L2-22（预算控制 hook）依赖本计划**：L2-22 需要 `IModelRouter` 查询已用预算决定是否降级模型，model-switched 消息是预算控制的审计基础。

## Goals

- ReAct 循环在每轮 `IModelRouter.route()` 返回后，检查当前轮使用的模型是否与上一轮不同。
- 模型变更时，产生一条 model-switched 审计消息（role=80），含 metadata：`fromModel`（上一轮模型标识）、`toModel`（当前轮模型标识）、`routingReason`（`RoutingResult.getRoutingReason()`，可能为 null）、`complexity`（`RoutingResult.getComplexity()`，可能为 null）。
- 上一轮模型状态（lastModelId）在 ReAct 循环内被正确追踪和更新，跨轮次保持一致。
- model-switched 消息不参与 LLM 推理上下文（role=80 不注入 prompt）。
- Focused 测试验证：模型不变时不产生消息、模型变更时产生正确 metadata 的消息、首轮不产生消息（无前序模型可比较）、PassThroughModelRouter（恒不切换）不产生消息。

## Non-Goals

- **L2-22（预算控制 hook）**：`IModelRouter` 查询已用预算决定是否降级模型，依赖 L2-20 ✅ + L2-10 ✅。独立 work item。
- **功能性 IModelRouter 实现**：本计划不创建 Smart Router 或任何非 PassThrough 的 router 实现。`PassThroughModelRouter` 仍是 shipped 默认。model-switched 消息仅在集成商注册功能性 router 时可观测——`PassThroughModelRouter` 恒不改变模型，因此默认配置下不产生 model-switched 消息。
- **L2-19（NopAiModel 定价列）**：已由 plan 204 交付。
- **L2-20（per-model 聚合查询）**：已由 plan 203 交付。
- **model-switched 消息的 GraphQL/REST 查询接口**：消息产生是本计划 scope，查询消费不在 scope。
- **NopAiSessionMessage ORM 实体变更**：不改 nop-ai-dao 层的实体定义。

## Scope

### In Scope

- ReAct 循环中 lastModelId 跨轮次追踪机制（裁定位置：`AgentExecutionContext` 字段 或 ReAct 循环局部变量）
- role=80 本地常量定义（不引用 nop-ai-dao，参照 `AiAgentSessionTable` 本地常量模式）
- `IModelRouter.route()` 返回后模型标识比较逻辑（`provider:model` 复合键）
- model-switched 审计消息产生 + raw JDBC 持久化到 `nop_ai_session_message` 表（参照 `DbUsageRecorder` + `NopAiChatResponseTable` 模式）
- Focused 测试覆盖（模型变更/不变/首轮/PassThrough/不注入LLM上下文 全路径）
- 设计文档 §3.5 标注实现落地
- roadmap §4 Layer 2 表格 L2-21 状态更新

### Out Of Scope

- L2-22 预算控制 hook
- 功能性 IModelRouter 实现（Smart Router 等）
- model-switched 消息查询接口
- nop-ai-dao 层变更

## Execution Plan

### Phase 1 - 裁定 + 实现

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（修改）、可能新增消息写入/记录类、可能修改 `AgentExecutionContext`

- Item Types: `Fix`（功能缺失 = contract gap：常量已定义但从未产生）

- [x] 裁定 lastModelId 追踪位置：`AgentExecutionContext` 新增字段 vs ReAct 循环局部变量。裁定依据：`AgentExecutionContext` 是 per-execution 上下文，lastModelId 是 per-execution 状态；如 fork/restore 需要继承 lastModelId，字段方案更自然。裁定须写清理由。
- [x] 裁定 role=80 常量来源：nop-ai-agent 不能引用 `_NopAiDaoConstants.MESSAGE_TYPE_MODEL_SWITCHED`（在 nop-ai-dao，无依赖）。参照 `AiAgentSessionTable` 定义本地常量的既有模式，定义 `ROLE_MODEL_SWITCHED = 80`（附注释引用 nop-ai-dao 常量以保持可追溯性）。
- [x] 实现模型标识比较逻辑：模型标识为 `provider + ":" + model` 复合键（从 `RoutingResult.getOptions().getProvider()` + `.getModel()` 提取）。Phase 1 须裁定 provider 为 null 时的处理（如仅用 model name，或用空串替代 null provider）。
- [x] 实现 lastModelId 追踪：首轮为 null，每轮 router 返回后更新为当前模型标识。首轮（lastModelKey=null）不产生 model-switched 消息（无前序模型可比较）。
- [x] 实现 model-switched 消息产生与持久化：通过 raw JDBC 写入 `nop_ai_session_message` 表（参照 `DbUsageRecorder` + `NopAiChatResponseTable` 模式）。INSERT 须填充全部 NOT NULL 列：`ID`（UUID）、`SESSION_ID`、`ROLE`（=80）、`SEQ`（**裁定：内存计数器，per `AgentExecutionContext` 从 1 递增**——理由：(a) 写入器是 per-execution，不存在跨执行竞争；(b) 当前 nop-ai-agent 是 `nop_ai_session_message` 表的唯一运行时写入器（仅 CRUD BizModel 消费该实体，无其他生产代码 INSERT）；(c) 与 lastModelKey 追踪同处一个上下文，局部性一致。拒绝 `MAX(seq)+1` 方案：需额外 `SELECT` 往返且在 H2/MySQL 间 FOR UPDATE 行为不一致）、`VERSION`、`CREATED_BY`、`UPDATED_BY`、`CREATE_TIME`、`UPDATE_TIME`，以及 metadata 内容列（裁定存储方式：JSON 字符串写入内容/metadata 列，列名以 `_NopAiSessionMessage.java` 实际定义为准）。SQL 异常包装为 `NopAiAgentException`（不吞异常——Minimum Rules #24）。
- [x] 确保消息不注入 LLM 推理上下文：role=80 消息仅持久化到 DB，不加入发送给 LLM 的 chat messages 列表（`ctx.getMessages()`）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] lastModelId 追踪机制存在且裁定理由已落档
- [x] role=80 本地常量已定义（不引用 nop-ai-dao）
- [x] 模型标识比较逻辑存在：`provider:model` 复合键从 `RoutingResult.getOptions()` 提取
- [x] 模型变更时产生 role=80 审计消息写入 `nop_ai_session_message` 表，metadata 含 fromModel/toModel/routingReason/complexity
- [x] `nop_ai_session_message` INSERT 填充全部 NOT NULL 列（含 SEQ 递增管理、VERSION、审计列）
- [x] **无静默跳过**（Minimum Rules #24）：消息写入失败时抛出 `NopAiAgentException` 而非吞掉；routingReason/complexity 为 null 时是合法的 graceful degradation（`RoutingResult` 的 `PassThroughModelRouter` 返回 null complexity），不算静默跳过
- [x] **接线验证**（Minimum Rules #23）：model-switched 消息产生逻辑确实在 ReAct 循环 router 返回后被调用
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/` 已更新；否则明确写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/`（新增测试）、`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 新增 focused 测试，覆盖以下场景：
  - 首轮执行：lastModelId 为 null/初始值，不产生 model-switched 消息（无前序模型可比较）
  - 模型不变：连续两轮使用相同模型，不产生 model-switched 消息
  - 模型变更：第二轮模型与第一轮不同，产生 model-switched 消息，metadata 含正确的 fromModel/toModel
  - PassThroughModelRouter：恒不改变模型，不产生 model-switched 消息
  - 消息不注入 LLM 上下文：role=80 消息不出现在发送给 LLM 的 chat messages 中
- [x] 新增端到端测试：注入功能性 `IModelRouter`（mock，第二轮返回不同模型），运行 ReAct 循环，断言 model-switched 消息被产生且 metadata 正确
- [x] `nop-ai-agent-usage-and-billing.md` §3.5 标注 model-switched 消息产生已落地
- [x] 修正 `nop-ai-agent-usage-and-billing.md` §3.5 伪代码 API 漂移：将 `routingResult.selectedModelId` 替换为 `routingResult.getOptions().getProvider() + ':' + routingResult.getOptions().getModel()`，将 `ctx.lastModelId` 对齐为本计划实际的 lastModelKey 追踪实现
- [x] roadmap §4 Layer 2 表格 L2-21 从 ❌ → ✅，标注 plan 205

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] focused 测试存在，覆盖 5 个场景（首轮/不变/变更/PassThrough/不注入LLM上下文）
- [x] **端到端验证**（Minimum Rules #22）：从 `DefaultAgentEngine.execute()` → ReAct 循环 → `IModelRouter.route()` → model-switched 消息产生的完整路径验证通过
- [x] **接线验证**（Minimum Rules #23）：测试断言 model-switched 消息在运行时被产生（通过消息计数/内容断言），且仅在模型变更时产生
- [x] **新功能测试覆盖**（Minimum Rules #25）：模型变更检测、消息产生、metadata 正确性、消息不注入 LLM 上下文均有对应测试
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（含新增测试 + 既有 tests 零回归）
- [x] `nop-ai-agent-usage-and-billing.md` §3.5 标注实现落地
- [x] roadmap §4 Layer 2 表格 L2-21 标注 ✅ + plan 205
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] model-switched 消息在模型变更时被正确产生（role=80 + metadata）
- [x] lastModelId 跨轮次追踪正确（首轮不产生、不变不产生、变更产生）
- [x] 消息不注入 LLM 推理上下文
- [x] 端到端接线完整：ReAct 循环 → router 返回 → 模型变更检测 → 消息产生
- [x] Focused 测试通过（5 场景 + 端到端）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（L2-22 为显式 Non-Goal）
- [x] 受影响 owner docs 已同步（`nop-ai-agent-usage-and-billing.md` + roadmap），或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）model-switched 消息在运行时被产生（不只是常量/类型存在），（b）模型变更检测逻辑确实在 router 返回后被执行，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；L2-22 为显式 Non-Goal 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **model-switched 消息的 GraphQL/REST 查询接口**：本计划只负责消息产生，查询消费不在 scope。Classification: out-of-scope improvement。
- **SEQ 协调契约**：本计划采用 per-execution 内存计数器作为 `nop_ai_session_message.SEQ` 生成策略，前提是 nop-ai-agent 是该表的唯一运行时写入器。如后续有其他组件（如 service 层）向同一 session 写入消息行，须协调 SEQ 分配以避免 `uk_nop_ai_session_msg_seq` 唯一约束冲突。Classification: watch-only residual。
- **routingReason/complexity 字段的丰富化**：`PassThroughModelRouter` 返回 `routingReason="pass-through"` + `complexity=null`；功能性 router 实现时可提供有意义的 routingReason/complexity。Classification: watch-only residual。

## Closure

Status Note: Plan 205 把 L2-21 的"常量已定义但从未产生"状态收口为"模型切换时自动产生审计消息"。ReAct 循环在 `IModelRouter.route()` 返回后检测 `provider:model` 复合键变更，变更时通过 `IModelSwitchedMessageWriter` 扩展点持久化 role=80 审计消息到 `nop_ai_session_message`。默认 `NoOpModelSwitchedMessageWriter` pass-through（显式不丢弃功能），生产用 `DbModelSwitchedMessageWriter` raw JDBC。消息不注入 LLM 上下文。12 tests（5 focused + 5 DB unit + 2 e2e）全绿，既有 tests 零回归。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent (fresh session, task_id closure-audit-205)
- Audit Session: closure-audit-205
- Evidence:
  - Phase 1 Exit Criteria: all PASS — `lastModelKey` ReAct-loop local var (ReActAgentExecutor.java:608-611); `ROLE_MODEL_SWITCHED=80` local constant (DbModelSwitchedMessageWriter.java:52-57); `buildModelKey` composite key (ReActAgentExecutor.java:1308-1313); model-switch detection at ReActAgentExecutor.java:693-708; `DbModelSwitchedMessageWriter` fills all NOT NULL columns (id/sessionId/role/seq/version/createdBy/createTime/updatedBy/updateTime); SQL exceptions wrapped in `NopAiAgentException` (not swallowed); wiring verified — detection logic is after `modelRouter.route()` at line 688
  - Phase 2 Exit Criteria: all PASS — `TestModelSwitchedMessage` covers 5 scenarios (first iteration / same model / model change+metadata / PassThrough / not-injected-to-LLM); `TestDbModelSwitchedMessageWriterWiring.endToEndModelSwitchProducesAuditMessageInDb` verifies DefaultAgentEngine → ReAct loop → IModelRouter.route() → model-switch detection → DbModelSwitchedMessageWriter → JDBC INSERT → DB row; `TestDbModelSwitchedMessageWriter` covers unit-level DB writer behavior
  - Closure Gates: all PASS — model-switched messages produced at runtime (verified by CapturingWriter assertion in focused tests + DB row assertion in e2e test); lastModelKey tracking correct (first=null→no message, same→no message, changed→message); message not injected into LLM context (assertion on ctx.getMessages().size() and captured LLM request messages); no silent skips (NopAiAgentException on null sessionId and on SQL failure)
  - Anti-Hollow Check: PASS — (a) model-switched messages verified produced at runtime via CapturingWriter + DB row assertions (not just constant/type existence); (b) model-switch detection logic confirmed after router return in ReAct loop code (ReActAgentExecutor.java:688-708); (c) no empty method bodies/no-ops — `NoOpModelSwitchedMessageWriter` is explicit pass-through default (documented, not a hidden gap)
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (12 new tests + all existing tests pass, zero regression)
  - Deferred 项分类检查: L2-22 is explicit Non-Goal (independent successor); SEQ coordination + GraphQL query interface + routingReason enrichment are watch-only residuals (Non-Blocking Follow-ups section)

Follow-up:

- L2-22（预算控制 hook）为独立 successor work item（Explicit Non-Goal）
- model-switched 消息 GraphQL/REST 查询接口：out-of-scope improvement
- no remaining plan-owned work

## Follow-up handled by 206-nop-ai-agent-budget-control-hook.md

L2-22（Non-Goals 第一条，标 `独立 work item`；Closure Follow-up 标 `successor work item`）已由 successor plan `ai-dev/plans/206-nop-ai-agent-budget-control-hook.md` 接管：引入 `IBudgetProvider` 扩展点 + `BudgetSnapshot` 数据对象 + `NoOpBudgetProvider` 默认，在 ReAct 循环每轮路由前刷新预算快照到 `AgentExecutionContext`，使功能性 `IModelRouter` 能基于预算做模型降级决策。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
