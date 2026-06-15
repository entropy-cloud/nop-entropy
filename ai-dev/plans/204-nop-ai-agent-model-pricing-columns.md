# 204 nop-ai-agent NopAiModel 定价列 + estimatedCost 计算启用（L2-19）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-19
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-19 ❌ 未实现，line 185）；`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` §3.3（NopAiModel 定价列扩展）+ §3.4（estimatedCost 计算的 enhanced SQL）；plan 203 Non-Blocking Follow-ups（"estimatedCost 定价计算" deferred to L2-19）
> Related: `202`（DbUsageRecorder 写 nop_ai_chat_response，定价 join 的数据来源）、`203`（summarizeByModel 聚合查询，estimatedCost 恒 null 的裁定来源，本计划解除该 null 裁定）、L2-22（预算控制 hook，本计划的下游消费者）

## Purpose

为 `NopAiModel` 实体增加 6 个定价列（`input_price_per_1m` / `output_price_per_1m` / `reasoning_price_per_1m` / `cache_read_price_per_1m` / `cache_write_price_per_1m` / `currency`），并在 `NopAiChatResponseBizModel.summarizeByModel` 中启用 `estimatedCost` 计算（解除 plan 203 的"恒 null"裁定）。本计划把 plan 203 交付的 per-model token 聚合查询从"只有 token 数"升级为"有预估成本"，使集成商能查询"一个 session 里每个模型各花了多少钱"——这是 L2-22（预算控制 hook）计算成本的基础。

## Current Baseline

基于 live repo 核对（`nop-ai/model/nop-ai.orm.xml`、`nop-ai/nop-ai-dao/`、`nop-ai/nop-ai-service/`，2026-06-16）：

- **`NopAiModel` 实体当前 10 列**（`nop-ai/model/nop-ai.orm.xml:286-320`）：id、provider、model_name、base_url、api_key、version、created_by、create_time、updated_by、update_time。propId 1–10 已占用。**无任何定价列**（grep `price_per_1m|currency` 全 `nop-ai/` 零命中，确证）。
- **生成实体 `_NopAiModel.java`**（`nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/_gen/_NopAiModel.java`）：当前 `_PROP_ID_BOUND = 11`（即 propId 0–10），10 个字段 getter/setter，1 个 `responses` 关系。这是**生成文件**，由 `./mvnw` 从 `nop-ai.orm.xml` 再生成——不可手改。
- **保留实体 `NopAiModel.java`**（`nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/NopAiModel.java`）：空壳 `extends _NopAiModel`，11 行，无需改动。
- **DECIMAL 列先例**：`NopAiChatResponse` 的 4 个 score 列（`nop-ai.orm.xml:646-653`）使用 `precision="5" scale="2" stdDataType="decimal" stdSqlType="DECIMAL"`。本计划的定价列遵循相同 ORM 写法（precision/scale/stdDataType/stdSqlType 属性）。
- **`summarizeByModel` 当前恒返回 estimatedCost=null**（`NopAiChatResponseBizModel.java:46-47,64-74`）：`buildSummarySql` 不含 `LEFT JOIN nop_ai_model`，不计算 cost。`ModelUsageSummary.estimatedCost` 字段类型为 `BigDecimal`（`ModelUsageSummary.java:43,101-107`），javadoc 注明"恒为 null"。
- **plan 203 Non-Blocking Follow-ups 明确裁定**：estimatedCost 计算依赖 L2-19 定价列，deferred 到 L2-19 落地后追加 `LEFT JOIN nop_ai_model` 定价 join（`ai-dev/plans/203-nop-ai-agent-per-model-usage-aggregation.md:146`）。
- **设计文档 §3.3 定义了定价列规格**（`nop-ai-agent-usage-and-billing.md:121-132`）：`input_price_per_1m` DECIMAL(10,4)、`output_price_per_1m` DECIMAL(10,4)、`reasoning_price_per_1m` DECIMAL(10,4) nullable、`cache_read_price_per_1m` DECIMAL(10,4) nullable、`cache_write_price_per_1m` DECIMAL(10,4) nullable、`currency` VARCHAR(3) default 'USD'。
- **设计文档 §3.4 给出了 enhanced SQL 模板**（`nop-ai-agent-usage-and-billing.md:165-181`）：`LEFT JOIN nop_ai_model m ON r.model_id = m.id` + `SUM(r.prompt_tokens * m.input_price_per_1m / 1000000 + r.completion_tokens * m.output_price_per_1m / 1000000) AS estimated_cost`。
- **`nop_ai_chat_response` 只有 prompt_tokens + completion_tokens**（`nop-ai.orm.xml:640-645`），无 reasoning/cache token 列——因此 estimatedCost 计算只能使用 input + output 定价（设计 SQL 模板与此一致，reasoning/cache 定价列在 cost 计算中暂不使用，但列本身预留以支持未来增强）。
- **roadmap §4 Layer 2**（line 185）：`L2-19 | 🔴 NopAiModel 加定价列 | 无依赖 | ❌`。

## Goals

- `NopAiModel` 实体含 6 个新定价列（`inputPricePer1m`、`outputPricePer1m`、`reasoningPricePer1m`、`cacheReadPricePer1m`、`cacheWritePricePer1m`、`currency`），通过 source `nop-ai.orm.xml` 编辑 + `./mvnw` 再生成落地到 `_NopAiModel.java`。
- `NopAiChatResponseBizModel.summarizeByModel` 的 `estimatedCost` **不再恒为 null**——当 `nop_ai_model` 行有定价数据且 `nop_ai_chat_response.model_id` 能 join 上时，返回计算后的 BigDecimal 成本值。
- 当 model_id 为 null（DbUsageRecorder 按 provider+model 找不到匹配）或 `nop_ai_model` 行定价列为 null 时，该组的 `estimatedCost` 为 null（graceful degradation，非错误）。
- Focused 测试验证：定价列存在于再生成实体中；estimatedCost 计算正确性（有定价数据 → 非零成本、无定价数据 → null、null model_id 行 → null）。
- 设计文档 §3.3 / §3.4 更新（标注 L2-19 落地 + estimatedCost 已启用）。
- roadmap §4 Layer 2 表格 L2-19 从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **L2-21（model-switched 消息产生）**：ReAct 循环中 IModelRouter 返回后检查模型变更写 role=80 消息，独立 work item，依赖 L2-10（✅）。
- **L2-22（预算控制 hook）**：IModelRouter 查询已用预算决定降级模型，依赖本计划（L2-19），是本计划的下游消费者。
- **reasoning/cache token 计入 estimatedCost**：`nop_ai_chat_response` 无 reasoning_tokens/cache_read_tokens/cache_write_tokens 列，estimatedCost 只计算 input+output 部分。reasoning/cache 定价列（`reasoningPricePer1m`/`cacheReadPricePer1m`/`cacheWritePricePer1m`）在 schema 中预留，但当前 cost 计算不使用（设计 SQL 模板一致）。如后续 `nop_ai_chat_response` 增加这些 token 列，可追加到 cost 计算公式。Classification: out-of-scope improvement。
- **`NopAiSession` 的 cost/tokens 列写入**：设计 §2.1 提到 `NopAiSession` 有 cost/tokens 字段但 runtime 从不写入。本计划不改 `NopAiSession` 的写入逻辑，per-model 聚合仍通过 SQL 查询 `nop_ai_chat_response`。Classification: out-of-scope。
- **`nop_ai_session_model_usage` 物化聚合表**：性能优化 fallback，plan 203 已裁定为 optimization candidate。
- **跨 session / 按项目 / 按时间范围成本聚合**：本计划仍只做单 session 维度（`WHERE session_id = ?`）。

## Scope

### 设计裁定

**estimatedCost 计算只使用 input + output 定价**：设计 §3.4 的 enhanced SQL 只 join `input_price_per_1m` + `output_price_per_1m`，因为 `nop_ai_chat_response` 只有 `prompt_tokens` + `completion_tokens`。reasoning/cache 定价列在 ORM 中预留（满足设计 §3.3 的 5 列规格），但当前 cost 公式不引用——这是数据可用性约束（无 reasoning/cache token 数据可乘），不是遗漏。

**estimatedCost = null 的条件**：(1) `model_id` 为 null（该组无匹配 `nop_ai_model` 行可 join）；(2) `model_id` 非 null 但对应 `nop_ai_model` 行的 `input_price_per_1m` 或 `output_price_per_1m` 为 null。两种情况均 graceful degradation，estimatedCost 为 null，不抛异常。

**currency 列暂不参与 cost 计算**：estimatedCost 返回的 BigDecimal 不附带币种信息。如需多币种汇总，集成商自行查询 `NopAiModel.currency`。currency 列在 schema 中预留，当前所有模型默认 'USD'。Classification: out-of-scope improvement。

**本计划不修改 `NopAiChatResponseBizModel.summarizeByModel` 的方法签名**：方法仍返回 `List<ModelUsageSummary>`，`ModelUsageSummary.estimatedCost` 字段类型仍为 `BigDecimal`，只是值从"恒 null"变为"条件计算"。这是行为变更，不是 API 变更。

### In Scope

- 编辑 source `nop-ai/model/nop-ai.orm.xml` 的 `NopAiModel` 实体，追加 6 列（propId 11–16）
- `./mvnw clean install -T 1C` 触发再生成（`_NopAiModel.java` + xmeta + i18n 等派生产物自动更新）
- `NopAiChatResponseBizModel.buildSummarySql` 修改：追加 `LEFT JOIN nop_ai_model` + `estimated_cost` 计算列
- `ModelUsageSummary` javadoc 更新（estimatedCost 不再恒 null）
- `NopAiChatResponseBizModel.summarizeByModel` javadoc 更新
- Focused 测试：定价列存在于再生成实体 + estimatedCost 计算正确性
- 设计文档 `nop-ai-agent-usage-and-billing.md` §3.3 / §3.4 更新
- roadmap §4 Layer 2 表格 L2-19 状态更新

### Out Of Scope

- L2-21（model-switched）、L2-22（预算 hook）、物化聚合表、NopAiSession cost 写入（见 Non-Goals）
- reasoning/cache token 计入 cost（数据不可用）
- 跨 session 成本聚合

## Execution Plan

### Phase 1 - NopAiModel 定价列 + estimatedCost 计算

Status: completed
Targets: `nop-ai/model/nop-ai.orm.xml`（修改 source 模型）、`nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiChatResponseBizModel.java`（修改 buildSummarySql + javadoc）、`nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/dto/ModelUsageSummary.java`（javadoc 更新）

- Item Types: `Fix`（功能缺失 = contract gap：定价列从未存在，estimatedCost 计算被显式 defer）

- [x] 编辑 source `nop-ai/model/nop-ai.orm.xml` 的 `NopAiModel` 实体（`<entity name="io.nop.ai.dao.entity.NopAiModel">` 的 `<columns>` 段），在 update_time（propId=10）之后追加 6 列：
  - `input_price_per_1m`：propId=11，precision=10，scale=4，stdDataType=decimal，stdSqlType=DECIMAL，displayName="输入单价"，comment="每百万输入token单价"
  - `output_price_per_1m`：propId=12，precision=10，scale=4，stdDataType=decimal，stdSqlType=DECIMAL，displayName="输出单价"，comment="每百万输出token单价"
  - `reasoning_price_per_1m`：propId=13，precision=10，scale=4，stdDataType=decimal，stdSqlType=DECIMAL，displayName="推理单价"，comment="每百万推理token单价"，nullable（无 mandatory=true）
  - `cache_read_price_per_1m`：propId=14，precision=10，scale=4，stdDataType=decimal，stdSqlType=DECIMAL，displayName="缓存读单价"，comment="每百万缓存读token单价"，nullable
  - `cache_write_price_per_1m`：propId=15，precision=10，scale=4，stdDataType=decimal，stdSqlType=DECIMAL，displayName="缓存写单价"，comment="每百万缓存写token单价"，nullable
  - `currency`：propId=16，precision=3，stdDataType=string，stdSqlType=VARCHAR，displayName="币种"，comment="币种代码"，默认值 'USD'（按 ORM 模型约定实现 default，执行时按平台支持的 column 属性裁定——如 ORM 不支持 column 级 default 则 nullable + 逻辑层兜底）
- [x] 运行 `./mvnw clean install -T 1C`（或至少 `./mvnw clean install -pl nop-ai/nop-ai-dao,nop-ai/nop-ai-service -am -T 1C`）触发 ORM 再生成，确认 `_NopAiModel.java` 含 6 个新字段的 PROP_NAME/PROP_ID + getter/setter + `_PROP_ID_BOUND` 更新为 17
- [x] 修改 `NopAiChatResponseBizModel.buildSummarySql`（`nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/entity/NopAiChatResponseBizModel.java:64-74`）：追加 `LEFT JOIN nop_ai_model m ON model_id = m.id` + `SUM(prompt_tokens * m.input_price_per_1m / 1000000 + completion_tokens * m.output_price_per_1m / 1000000) AS estimated_cost`。SQL 须保持 `GROUP BY model_id, ai_provider, ai_model` + `WHERE session_id = ?` 不变。表别名 / 列名须与 `nop_ai_chat_response` + `nop_ai_model` 实际列名一致（`model_id` 是 nop_ai_chat_response 的列，`input_price_per_1m`/`output_price_per_1m` 是 nop_ai_model 的新列）。
- [x] 更新 `NopAiChatResponseBizModel.summarizeByModel` javadoc（移除"恒为 null"描述，改为"当 nop_ai_model 有定价数据且 model_id 可 join 时返回计算值，否则 null"）
- [x] 更新 `ModelUsageSummary.estimatedCost` 字段 javadoc（同上，移除"恒为 null"）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] source `nop-ai/model/nop-ai.orm.xml` 的 `NopAiModel` 实体含 6 个新 `<column>` 定义（propId 11–16），类型为 DECIMAL(10,4) × 5 + VARCHAR(3) × 1
- [x] `./mvnw` 再生成后 `_NopAiModel.java` 含 6 个新字段的 PROP_NAME_id/inputPricePer1m/outputPricePer1m/reasoningPricePer1m/cacheReadPricePer1m/cacheWritePricePer1m/currency 常量 + getter/setter，`_PROP_ID_BOUND` = 17
- [x] `buildSummarySql` 返回的 SQL 含 `LEFT JOIN nop_ai_model` + `estimated_cost` 计算列（在代码中可验证）
- [x] **无静默跳过**（Minimum Rules #24）：SQL 计算不吞异常；estimatedCost 为 null 是 graceful degradation（model_id 为 null 或定价列为 null 时），不是隐藏错误——javadoc 须注明 null 条件
- [x] `./mvnw compile -pl nop-ai/nop-ai-dao,nop-ai/nop-ai-service -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-service/src/test/`（新增/扩展测试；生成实体字段经由 service 端 SQL+映射测试间接验证）、`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 扩展 `TestNopAiChatResponseSummarizeByModel`（plan 203 已建立的测试类，位于 `nop-ai/nop-ai-service/src/test/`）：在 seeded H2 数据中预插入 `nop_ai_model` 行（含定价数据）+ 关联 `nop_ai_chat_response` 行，新增断言：(1) 有定价数据的模型组 estimatedCost 非 null 且 = promptTokens * inputPricePer1m / 1000000 + completionTokens * outputPricePer1m / 1000000；(2) nop_ai_model 行定价列为 null 时该组 estimatedCost 为 null；(3) model_id 为 null 的行（无 join 对象）estimatedCost 为 null。H2 表初始化 DDL 须含新定价列（`nop_ai_model` 建表语句追加 6 列）。**关键**：plan 203 留下的既有断言 `assertNull(x.getEstimatedCost(), "estimatedCost must be null until L2-19 lands")`（在 model-X 组的聚合值断言中）必须同步更新为 `assertNotNull` + 预期成本值校验——本计划解除了 plan 203 的恒-null 裁定，该既有断言在新 SQL 下必然失败，不更新会导致编译/测试失败。
- [x] 如 plan 203 测试已有 null-modelId / 空-session 断言，确认其在新 SQL（含 LEFT JOIN）下仍通过（回归）
- [x] `nop-ai-agent-usage-and-billing.md` 更新：§3.3 标注定价列已落地（L2-19，propId 11–16）；§3.4 标注 estimatedCost 计算已启用（enhanced SQL 已实现，解除 plan 203 的恒-null 裁定）；§3.2 ModelUsageSummary 字段表更新（estimatedCost 不再恒 null）
- [x] roadmap §4 Layer 2 表格 L2-19 从 ❌ → ✅，标注 plan 204

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestNopAiChatResponseSummarizeByModel` 含 estimatedCost 计算断言（有定价 → 非零、定价为 null → null、model_id 为 null → null）
- [x] **端到端验证**（Minimum Rules #22）：从 `summarizeByModel(sessionId)` 入口 → SQL GROUP BY + LEFT JOIN → `List<ModelUsageSummary>` 输出（含 estimatedCost 计算值）的完整路径验证通过
- [x] **新功能测试覆盖**（Minimum Rules #25）：estimatedCost 三条路径（有定价计算 / 定价为 null / model_id 为 null）均有对应断言
- [x] `./mvnw test -pl nop-ai/nop-ai-service -am` 通过（含新增/扩展测试 + 既有 tests 零回归）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（零回归——定价列不改动 agent 运行时层）
- [x] `nop-ai-agent-usage-and-billing.md` §3.2/§3.3/§3.4 标注 L2-19 落地
- [x] roadmap §4 Layer 2 表格 L2-19 标注 ✅ + plan 204
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `NopAiModel` 实体含 6 个定价列（source `nop-ai.orm.xml` + 再生成 `_NopAiModel.java`）
- [x] `summarizeByModel` 的 `estimatedCost` 不再恒 null（有定价数据时返回计算值）
- [x] Focused 测试通过（estimatedCost 计算正确性 + null 路径 + 回归）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（L2-21/L2-22/reasoning-cache-cost/NopAiSession-cost/物化聚合表均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步：`nop-ai-agent-usage-and-billing.md`（§3.2/§3.3/§3.4）、roadmap §4（L2-19 ✅）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）定价列在再生成实体中实际存在并可读写；（b）estimatedCost 计算在运行时确实执行 LEFT JOIN + cost 公式（通过 H2 seeded 数据断言非零值，非 stub）；（c）无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-ai/nop-ai-dao,nop-ai/nop-ai-service -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-service -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；L2-21/L2-22/reasoning-cache-cost/NopAiSession-cost/物化聚合表均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **reasoning/cache token 计入 estimatedCost**：当 `nop_ai_chat_response` 增加 reasoning_tokens/cache_read_tokens/cache_write_tokens 列后，可在 cost 公式追加 `+ reasoning_tokens * m.reasoning_price_per_1m / 1000000` 等项。当前数据不可用。Classification: out-of-scope improvement。
- **多币种 cost 汇总**：当存在多种 currency 的模型时，estimatedCost 的跨币种汇总需要汇率转换。当前所有模型默认 USD。Classification: out-of-scope improvement。
- **L2-22 预算控制 hook**：IModelRouter 查询 estimatedCost 决定降级模型，依赖本计划。Classification: successor plan required。

## Closure

Status Note: 全部完成。NopAiModel 6 定价列（propId 11–16）落地到 source + 再生成实体；`summarizeByModel` 通过 `LEFT JOIN nop_ai_model` 启用 estimatedCost 计算（解除 plan 203 恒-null 裁定），null 为 graceful degradation（model_id 为 null / 定价列为 null）。独立子 agent closure audit 7/7 PASS。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore subagent，task ses_13313ff5cffepasW1LedbkxRsf，read-only audit，未做任何编辑）
- Evidence:
  - Check 1 PASS — `nop-ai.orm.xml:310-326` 6 列 propId 11–16（DECIMAL 10,4 ×5 + VARCHAR(3) default USD）
  - Check 2 PASS — `_NopAiModel.java:63-88` PROP_ID 11–16 + `_PROP_ID_BOUND=17` + getter/setter（BigDecimal×5 / String×1），文件在 `_gen/`（生成物，未手改）
  - Check 3 PASS — `NopAiChatResponseBizModel.java:74-88` 真实 `LEFT JOIN nop_ai_model m ON r.model_id = m.id` + `estimated_cost` 计算列，GROUP BY/WHERE 不变（非 stub）
  - Check 4 PASS — javadoc 无 stale "恒为 null" 当前态描述（设计文档中残留 "恒 null" 仅在显式标 `> 历史：` 的历史段落）
  - Check 5 PASS — `TestNopAiChatResponseSummarizeByModel.java` MODEL_DDL 含 6 定价列；m-x 有定价→`assertNotNull`+`BigDecimal("0.00105")`；m-y 定价 null→`assertNull`；null-modelId→`assertNull`；旧 "until L2-19 lands" 断言已移除
  - Check 6 PASS — 设计文档 §3.2/§3.3/§3.4 标 L2-19 已落地；roadmap:185 L2-19 ✅（plan 204）
  - Check 7 PASS — `./mvnw test -pl nop-ai/nop-ai-service -am -T 1C -Dtest=TestNopAiChatResponseSummarizeByModel` → Tests run: 7, Failures: 0, Errors: 0，BUILD SUCCESS
- Overall: CLOSURE AUDIT: PASS（无 defect）

Follow-up:

- reasoning/cache token 计入 estimatedCost（数据不可用，out-of-scope improvement）
- 多币种 cost 汇总（out-of-scope improvement）
- L2-22 预算控制 hook（successor plan required，依赖本计划）
