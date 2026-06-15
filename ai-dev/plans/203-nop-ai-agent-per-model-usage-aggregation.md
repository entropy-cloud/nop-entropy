# 203 nop-ai-agent per-model 用量聚合查询（L2-20）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-20
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/201-nop-ai-agent-usage-recorder-interface.md`（Follow-up: L2-20 per-model 聚合查询，标 "successor required"，deferred 到独立 plan；plan 201 裁定 `summarizeByModel` 从 `IUsageRecorder` 接口移至 `NopAiChatResponseBizModel`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` §3.2 + §3.4 + §6 P1（per-model 聚合查询：`NopAiChatResponseBizModel.summarizeByModel(sessionId)`，SQL GROUP BY）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-20 ❌ 未实现）
> Related: `201`（交付 `IUsageRecorder` 接口 + 接线，已 completed）、`202`（交付 `DbUsageRecorder` 写 `nop_ai_chat_response` 表，已 completed，本计划的直接前置依赖）、L2-19（`NopAiModel` 定价列，独立 work item，尚未实现）、L2-22（预算控制 hook，依赖本计划）

## Purpose

为 `NopAiChatResponseBizModel` 增加 `summarizeByModel(sessionId)` 聚合查询方法，通过 SQL GROUP BY 返回一个 session 内按模型维度聚合的 token 用量（promptTokens 总和、completionTokens 总和、调用次数、耗时总和）。本计划把 plan 201/202 交付的逐行 `nop_ai_chat_response` 写入升级为可查询的 per-model 聚合视图，使集成商能查询"一个 session 里每个模型各消耗了多少 token"——这是 L2-22（预算控制 hook）的唯一前置依赖。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-service/src/main`、`nop-ai/nop-ai-dao/src/main`、`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`NopAiChatResponseBizModel` 是 bare CrudBizModel**（`nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiChatResponseBizModel.java`，15 行）：仅构造器调用 `setEntityName(...)`，无任何自定义 `@BizQuery` / `@BizAction` 方法。
- **`INopAiChatResponseBiz` 是 bare `ICrudBiz<NopAiChatResponse>`**（`nop-ai/nop-ai-dao/src/main/java/io/nop/ai/biz/INopAiChatResponseBiz.java`，8 行）：无自定义方法。
- **nop-ai-service 模块当前无任何测试文件**（glob `nop-ai/nop-ai-service/src/test/**/*.java` 返回 0 结果），也无任何 `@BizQuery` / `@BizAction` 使用（grep 确认）。本计划首次为该模块引入自定义 BizModel 方法与测试。
- **`nop_ai_chat_response` 表结构已确证**（`_NopAiChatResponse.java`）：可聚合列包括 `session_id`（VARCHAR）、`model_id`（VARCHAR，nullable）、`ai_provider`（VARCHAR）、`ai_model`（VARCHAR）、`prompt_tokens`（INTEGER）、`completion_tokens`（INTEGER）、`response_duration_ms`（INTEGER）。
- **`DbUsageRecorder`（plan 202）已落地写入**：通过 raw JDBC 将每次 LLM 调用写入 `nop_ai_chat_response` 行（含 session_id、model_id、ai_provider、ai_model、prompt_tokens、completion_tokens、response_duration_ms）。model_id 可能为 null（当 `nop_ai_model` 表中按 provider+model 查不到匹配时，graceful degradation）。这意味着聚合查询的 GROUP BY 必须容忍 model_id 为 null 的行。
- **`summarizeByModel` / `ModelUsageSummary` 零实现**：grep `summarizeByModel|ModelUsageSummary` 全 nop-ai 仅 1 处命中——`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/IUsageRecorder.java:22-23` 的 javadoc 注释（"deferred to L2-20, live in NopAiChatResponseBizModel"）。无任何实现代码。
- **设计文档 §3.4 定义了聚合 SQL**（`nop-ai-agent-usage-and-billing.md:138-154`）：`SELECT model_id, ai_provider, ai_model, SUM(prompt_tokens), SUM(completion_tokens), COUNT(*), SUM(response_duration_ms) FROM nop_ai_chat_response WHERE session_id = ? GROUP BY model_id, ai_provider, ai_model`，带 `LEFT JOIN nop_ai_model` 计算 `estimated_cost`（定价列条件式）。
- **设计文档 §3.2 定义了 `ModelUsageSummary` 字段**（`nop-ai-agent-usage-and-billing.md:115-119`）：`modelId`、`aiModel`、`totalPromptTokens`、`totalCompletionTokens`、`callCount`、`totalDurationMs`、`estimatedCost`（如果 `NopAiModel` 有定价列则计算，否则 null）。
- **L2-19（`NopAiModel` 定价列）❌ 未实现**：`nop_ai_model` 表当前无定价列（`input_price_per_1m` 等均不存在，grep 确认）。因此 `estimatedCost` 在本计划中**无法计算**，须为 nullable 且恒返回 null——定价列 join 是 L2-19 落地后的独立增强（见 Non-Goals）。
- **roadmap §4 Layer 2**（line 186）：`L2-20 | 🔴 per-model 聚合查询：NopAiChatResponseBizModel.summarizeByModel(sessionId) → SQL GROUP BY model_id | 依赖 L2-18 | ❌`。line 198 验收标准：`L2-20：SQL GROUP BY model_id 可查到 session 级 per-model token 聚合`。

## Goals

- `ModelUsageSummary` 数据对象存在，含设计 §3.2 定义的字段（modelId、aiProvider、aiModel、totalPromptTokens、totalCompletionTokens、callCount、totalDurationMs、estimatedCost）。
- `NopAiChatResponseBizModel.summarizeByModel(String sessionId)` 方法存在，返回 `List<ModelUsageSummary>`——通过 SQL GROUP BY 聚合 `nop_ai_chat_response` 表中指定 session 的所有行。
- 聚合查询对 `model_id` 为 null 的行正确处理（GROUP BY `model_id, ai_provider, ai_model`，null model_id 行独立成组而非丢弃）。
- `summarizeByModel` 通过 `@BizQuery` 暴露为 GraphQL 查询接口（`NopAiChatResponse__summarizeByModel`），可被集成商通过 GraphQL 调用。
- `estimatedCost` 字段存在但恒为 null（L2-19 未落地，无定价列可 join）——字段预留，定价计算 deferred。
- Focused 测试验证：聚合 SQL 对多种模型混用的 session 产生正确的分组与聚合值；空 session 返回空列表；单模型 session 返回单行。
- roadmap §4 Layer 2 表格 L2-20 从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **L2-19（`NopAiModel` 定价列）**：ORM 模型变更，独立 work item，无依赖。本计划的 `estimatedCost` 恒为 null，不 join 定价列。定价计算是 L2-19 落地后的增强（Non-Blocking Follow-up）。
- **L2-21（model-switched 消息产生）**：ReAct 循环中检查模型变更写 role=80 消息，依赖 L2-10（✅），独立 work item。
- **L2-22（预算控制 hook）**：`IModelRouter` 查询已用预算决定降级，依赖本计划（L2-20），是本计划的下游消费者。
- **`nop_ai_session_model_usage` 物化聚合表**：设计 §3.4 / §4.2 的性能 fallback——仅当单 session 超过 1000 次 LLM 调用时才需要。当前查询时 SQL 聚合足够。Classification: optimization candidate（见 Non-Blocking Follow-ups）。
- **修改 `IUsageRecorder` 接口**：plan 201 已裁定 `summarizeByModel` 不放在 `IUsageRecorder` 接口上。本计划在 `NopAiChatResponseBizModel`（service 层）实现，不回改 agent 运行时层接口。
- **跨 session 聚合 / 按时间范围聚合**：本计划只做单 session 维度的 per-model 聚合（WHERE session_id = ?）。跨 session / 按 projectId / 按时间范围聚合是独立增强。
- **`estimatedCost` 定价计算**：依赖 L2-19 定价列，本计划恒返回 null。

## Scope

### 设计裁定

**`estimatedCost` 恒为 null，不 join 定价列**：设计 §3.4 的 SQL 含 `LEFT JOIN nop_ai_model ... SUM(prompt_tokens * m.input_price_per_1m / 1000000 + ...)` 计算 `estimated_cost`，但 L2-19（定价列）❌ 未落地，`nop_ai_model` 表无 `input_price_per_1m` 等列。本计划的 SQL 不 join 定价列、不计算 cost，`ModelUsageSummary.estimatedCost` 恒为 null。当 L2-19 落地后，可在本方法内追加定价 join（Non-Blocking Follow-up）。这保证 L2-20 不依赖 L2-19，可独立交付。

**GROUP BY 包含 `ai_provider` + `ai_model`**：`nop_ai_chat_response.model_id` 可能为 null（DbUsageRecorder 按 provider+model 查 `nop_ai_model` 找不到时为 null）。GROUP BY 仅按 `model_id` 会将所有 model_id=null 的不同模型合并为一组。因此 GROUP BY 必须包含 `model_id, ai_provider, ai_model` 三列，确保即使 model_id 为 null，不同 provider/model 仍独立成组。`ModelUsageSummary` 因此须含 `aiProvider` + `aiModel` 字段（设计 §3.2 原列出了 `aiModel` 但未列 `aiProvider`，本计划补充 `aiProvider`——它是 GROUP BY 键的一部分，也是定价查找的必要维度）。

**`ModelUsageSummary` 放置位置**：作为查询结果 DTO，放在 `nop-ai-dao`（与实体同模块，作为出参数据对象），使 service 层与潜在的跨模块消费者（如 L2-22 budget hook 在 agent 层查询）均可引用。nop-ai-dao 当前**无现有 DTO 先例**（仅有 `dao/entity/`、`dao/entity/_gen/`、`biz/` 接口包），本计划新建 `io.nop.ai.dao.dto` 子包放置 `ModelUsageSummary`（该模块首个 DTO）。

### In Scope

- 新增 `ModelUsageSummary` 数据对象（modelId、aiProvider、aiModel、totalPromptTokens、totalCompletionTokens、callCount、totalDurationMs、estimatedCost(nullable)）
- `NopAiChatResponseBizModel` 新增 `summarizeByModel(String sessionId)` 方法（`@BizQuery`，返回 `List<ModelUsageSummary>`），通过 SQL GROUP BY 聚合 `nop_ai_chat_response`
- 聚合 SQL：`GROUP BY model_id, ai_provider, ai_model` + `SUM(prompt_tokens)` + `SUM(completion_tokens)` + `COUNT(*)` + `SUM(response_duration_ms)`，`WHERE session_id = ?`
- xbiz 声明 `summarizeByModel` action（`NopAiChatResponse.xbiz`）——如 `@BizQuery` 注解自动暴露则无需手写 xbiz，执行时裁定
- Focused 测试：聚合正确性（多模型混合、空 session、单模型、null model_id 行）
- 设计文档 `nop-ai-agent-usage-and-billing.md` 更新（标注 L2-20 实现落地 + estimatedCost 裁定）
- roadmap §4 Layer 2 表格 L2-20 状态更新

### Out Of Scope

- L2-19（定价列）、L2-21（model-switched）、L2-22（预算 hook）、物化聚合表（见 Non-Goals）
- `IUsageRecorder` 接口变更
- 跨 session / 按项目 / 按时间范围聚合

## Execution Plan

### Phase 1 - ModelUsageSummary 数据对象 + summarizeByModel 方法 + 聚合 SQL

Status: completed
Targets: `nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/`（新增 `ModelUsageSummary`）、`nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiChatResponseBizModel.java`（修改）、`nop-ai/nop-ai-service/src/main/resources/_vfs/nop/ai/model/NopAiChatResponse/NopAiChatResponse.xbiz`（如需声明 action）

- Item Types: `Fix`（功能缺失 = contract gap：plan 201/202 交付了逐行写入，per-model 聚合查询从未实现）

- [x] 新增 `ModelUsageSummary` 数据对象（8 字段：modelId String nullable、aiProvider String、aiModel String、totalPromptTokens long、totalCompletionTokens long、callCount long、totalDurationMs long、estimatedCost BigDecimal nullable）。标注为查询结果 DTO，非持久化实体。
- [x] `NopAiChatResponseBizModel` 新增 `summarizeByModel(String sessionId)` 方法，`@BizQuery` 注解（使 GraphQL 自动暴露），返回 `List<ModelUsageSummary>`。方法内执行聚合 SQL（GROUP BY `model_id, ai_provider, ai_model`，SUM/COUNT 聚合，WHERE `session_id = ?`），将结果行映射为 `ModelUsageSummary`。具体 SQL 执行 API 按 `CrudBizModel` 既有聚合查询模式实现（参照同模块或其他 nop 业务模块的 `@BizQuery` 聚合查询先例），`estimatedCost` 恒设为 null。
- [x] 如 `@BizQuery` 注解不足以自动暴露（需 xbiz 显式声明），在 `NopAiChatResponse.xbiz` 的 `<actions>` 中声明 `summarizeByModel` query action（参数 `sessionId: String`，返回类型 `List<ModelUsageSummary>`）。执行时根据 Nop 平台 GraphQL 自动发现机制裁定是否需要手写 xbiz。
- [x] `INopAiChatResponseBiz` 接口裁定：如 Nop 平台要求 BizModel 自定义方法在接口上声明，则同步新增 `summarizeByModel` 方法签名到 `INopAiChatResponseBiz`；如 `@BizQuery` 在实现类上即可暴露，则不改接口。执行时按平台约定裁定。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ModelUsageSummary` 数据对象文件存在于 nop-ai-dao，含 8 字段（含 aiProvider 补充字段）
- [x] `NopAiChatResponseBizModel` 含 `summarizeByModel(String sessionId)` 方法，`@BizQuery` 注解存在
- [x] 聚合 SQL 含 `GROUP BY model_id, ai_provider, ai_model` + SUM/COUNT 聚合 + `WHERE session_id = ?`（在代码或 xbiz 中可验证）
- [x] `estimatedCost` 恒为 null（无定价列 join）
- [x] **无静默跳过**（Minimum Rules #24）：sessionId 为 null 或空时不静默返回空列表而吞掉错误——须显式裁定（抛 `NopException` 参数校验错误，或 javadoc 明确空字符串语义）。裁定结果写入 Exit Criteria 勾选项。
- [x] `./mvnw compile -pl nop-ai/nop-ai-service -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-service/src/test/`（新增测试）、`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 新增 `TestNopAiChatResponseSummarizeByModel`：使用内存 H2（参照 `nop-ai-agent` 的 `TestDbUsageRecorder` H2 初始化模式，建 `nop_ai_chat_response` 表），预插入多行混合模型数据（如 session-A 含 model-X 3 行 + model-Y 2 行 + null-modelId 1 行），断言：(1) `summarizeByModel("session-A")` 返回 3 行（按 provider+model 分组）；(2) model-X 行的 totalPromptTokens = 3 行 promptTokens 之和、callCount = 3、totalDurationMs = 3 行之和；(3) 不存在 session 的 sessionId 返回空列表；(4) null model_id 行独立成组（不被丢弃、不与其他模型合并）。注：nop-ai-service 当前无测试基础设施，本项须建立该模块首个测试（H2 + BizModel 或直接测 SQL 查询逻辑，执行时按可行方式裁定——核心要求是验证聚合 SQL 的正确性）。
- [x] `nop-ai-agent-usage-and-billing.md` 更新：§3.4 标注 `summarizeByModel` 已落地在 `NopAiChatResponseBizModel`（L2-20）+ `estimatedCost` 恒 null 裁定（待 L2-19）+ `aiProvider` 补充为 GROUP BY 键
- [x] roadmap §4 Layer 2 表格 L2-20 从 ❌ → ✅，标注 plan 203；line 198 验收标准勾选

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestNopAiChatResponseSummarizeByModel` 存在，覆盖 4 条核心断言（多模型分组、聚合值正确、空 session、null model_id 独立成组）
- [x] **端到端验证**（Minimum Rules #22）：从 `summarizeByModel(sessionId)` 入口 → SQL GROUP BY → `List<ModelUsageSummary>` 输出的完整路径验证通过（seeded H2 数据 → 断言聚合结果）
- [x] **新功能测试覆盖**（Minimum Rules #25）：聚合分组、SUM/COUNT 正确性、null model_id 处理、空 session 边界均有对应断言
- [x] `./mvnw test -pl nop-ai/nop-ai-service -am` 通过（含新增测试 + 既有 tests 零回归）
- [x] `nop-ai-agent-usage-and-billing.md` §3.4 标注 summarizeByModel 实现落地 + estimatedCost 裁定
- [x] roadmap §4 Layer 2 表格 L2-20 标注 ✅ + plan 203
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `ModelUsageSummary` 数据对象存在（8 字段）
- [x] `NopAiChatResponseBizModel.summarizeByModel(sessionId)` 方法存在并通过 `@BizQuery` 暴露
- [x] 聚合 SQL 正确：GROUP BY `model_id, ai_provider, ai_model` + SUM/COUNT + WHERE session_id
- [x] Focused 测试通过（聚合正确性 + null model_id + 空 session 边界）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（L2-19/L2-21/L2-22/物化聚合表均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步：`nop-ai-agent-usage-and-billing.md`（summarizeByModel 落地 + estimatedCost 裁定）、roadmap §4（L2-20 ✅）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`summarizeByModel` 在运行时确实执行 SQL 并返回聚合结果（通过 H2 seeded 数据断言，非 stub）；（b）无空方法体/静默跳过（estimatedCost=null 是显式裁定，非缺失功能隐藏，javadoc 须注明）
- [x] `./mvnw compile -pl nop-ai/nop-ai-service -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；L2-19/L2-21/L2-22/物化聚合表均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`estimatedCost` 定价计算**：当 L2-19（`NopAiModel` 定价列）落地后，在 `summarizeByModel` 内追加 `LEFT JOIN nop_ai_model` 定价 join + cost 计算（设计 §3.4 SQL 已给出 join 模板）。Classification: enhancement deferred to L2-19。
- **`nop_ai_session_model_usage` 物化聚合表**：设计 §3.4 / §4.2 的性能 fallback——仅当单 session 超过 1000 次 LLM 调用时才需要增量维护聚合表。当前查询时 SQL 聚合足够。Classification: optimization candidate。
- **跨 session / 按项目 / 按时间范围聚合**：本计划只做单 session 维度。更广泛的用量分析查询是独立增强。Classification: out-of-scope improvement。

## Closure

Status Note: L2-20 已交付——`NopAiChatResponseBizModel.summarizeByModel(sessionId)` 通过 SQL GROUP BY `model_id, ai_provider, ai_model` + SUM/COUNT 聚合 `nop_ai_chat_response`，返回 `List<ModelUsageSummary>`，经 `@BizQuery` 暴露为 GraphQL 查询。`estimatedCost` 恒 null（L2-19 定价列未落地，显式裁定非缺失功能）。plan 201/202 交付的逐行写入由此升级为可查询的 per-model 聚合视图，为 L2-22（预算控制 hook）提供唯一前置依赖。两个 Phase 全部完成，focused 测试覆盖聚合分组/SUM-COUNT/null-modelId/空-session/参数校验。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session），task `ses_1333a89e2ffeV0FG1rZiWVBi2o`
- Audit Session: ses_1333a89e2ffeV0FG1rZiWVBi2o
- Evidence:
  - Exit Criteria（Phase 1 + Phase 2 全部条目）：PASS — DTO 8 字段 / @BizQuery + I*Biz 同步 / SQL GROUP BY+SUM+COUNT+WHERE+bind 变量 / estimatedCost null / 参数校验抛 NopException / 测试 7 项断言 / 文档+roadmap 同步
  - Closure Gates：逐条 PASS（见上方 `[x]`）
  - `./mvnw test -pl nop-ai/nop-ai-service -am -T 1C` → BUILD SUCCESS；`TestNopAiChatResponseSummarizeByModel` 7 tests, 0 failures
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1614 tests 全绿（零回归）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（无未勾选 + Closure Evidence 已写入）
  - Anti-Hollow 检查：测试通过 H2 seeded 数据断言真实聚合结果（model-X 600/60/3/6000、model-Y 200/20/2/2000、null-modelId 独立成组），非 stub；estimatedCost=null 在 javadoc 与设计 §3.4 显式注明；无空方法体/静默跳过（blank sessionId 抛 ErrorCode）
  - Deferred 项分类检查：L2-19/L2-21/L2-22/物化聚合表均为显式 Non-Goals 独立 successor，无 in-scope live defect 被降级
  - 生成文件检查：`git status` 无 `_` 前缀文件被手改

Follow-up:

- `estimatedCost` 定价计算（Non-Blocking，待 L2-19 定价列落地）
- `nop_ai_session_model_usage` 物化聚合表（optimization candidate，单 session >1000 次 LLM 调用时）
- 跨 session / 按项目 / 按时间范围聚合（out-of-scope improvement）
