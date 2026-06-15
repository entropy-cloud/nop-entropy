# nop-ai-agent 用量追踪与按模型计费设计

**日期**：2026-06-15
**范围**：`nop-ai-agent`（运行时用量记录）、`nop-ai-dao`（`NopAiChatResponse`/`NopAiSession`/`NopAiModel` 实体）、`nop-ai-service`（聚合查询 BizModel）
**状态**：草案

---

## 一、设计结论

1. **每次 LLM 调用写一行 `NopAiChatResponse`**：在 ReAct 循环的 token 累加点（`ReActAgentExecutor.java:645-648`）同步写入，记录 `modelId` + `aiProvider` + `aiModel` + `promptTokens` + `completionTokens` + `responseDurationMs` + `sessionId`。这是 per-model 计费的事实基础。

2. **`NopAiSession` 的累计字段从"标量累加"改为"按模型维度聚合"**：不维持单个 `cost` 列的增量更新，而是在查询时通过 SQL 聚合 `NopAiChatResponse` 计算。或者增加 `nop_ai_session_model_usage` 子表，按 `(session_id, model_id)` 维护累计值。

3. **给 `NopAiModel` 增加定价列**：`inputPricePer1m`（DECIMAL）、`outputPricePer1m`（DECIMAL）、`reasoningPricePer1m`（DECIMAL，nullable）、`cacheReadPricePer1m`（DECIMAL，nullable）、`cacheWritePricePer1m`（DECIMAL，nullable）。支持多币种时用 `currency` 列（默认 `USD`）。

4. **`IModelRouter` 路由决策产生 `model-switched` 消息**：当路由器选择的模型与上一轮不同时，写入 `NopAiSessionMessage`（`role=80` / `MESSAGE_TYPE_MODEL_SWITCHED`），metadata 含 `{fromModel, toModel, routingReason, complexity}`。

5. **引入 `IUsageRecorder` 接口**（Layer 2 扩展点）：解耦用量记录逻辑，默认 `NoOpUsageRecorder`（pass-through），生产用 `DbUsageRecorder`（写 `NopAiChatResponse`）。测试用 `InMemoryUsageRecorder`。

---

## 二、背景与动机

### 2.1 当前问题

`nop-ai-agent` 的 schema 定义了完整的 token/cost 字段（`NopAiChatResponse` 有 `model_id`+`prompt_tokens`+`completion_tokens`；`NopAiSession` 有 `cost`+`tokens_input`+`tokens_output`+`tokens_reasoning`+`tokens_cache_read`+`tokens_cache_write`），但 **runtime 从不写入**：

- `AgentSession.totalTokensUsed` 是单个 `long` 标量（`AgentSession.java:17`）
- `ReActAgentExecutor.java:645-648` 把 `promptTokens + completionTokens` 求和后丢失了 model 维度和 input/output 分项
- `new NopAiChatResponse(` 在业务代码中零引用
- `session.setCost` / `setTokensInput` / `setTokensOutput` 在业务代码中零引用

### 2.2 为什么必须按模型分开统计

一个 agent session 的典型执行模式：

```
Turn 1-3: deepseek-chat     ($0.14/1M input,  $0.28/1M output)
Turn 4-6: deepseek-reasoner ($0.55/1M input,  $2.19/1M output)  ← IModelRouter 升级
Turn 7:   gpt-4o             ($2.50/1M input, $10.00/1M output)  ← vision task
Turn 8-10:deepseek-chat      ($0.14/1M input,  $0.28/1M output)  ← 回退
```

- **定价差 18 倍**（$0.14 vs $2.50/1M input）。单标量累加无法计算真实成本。
- **预算控制**需要知道每个模型消耗了多少 token 才能决定是否切换。
- **成本优化分析**需要知道 Smart Router 实际节省了多少（对比全用最强模型的成本）。
- **多租户计费**需要按项目、按模型维度汇总。

### 2.3 设计约束

- 不能阻塞 ReAct 主循环——用量记录必须在 LLM 响应返回后异步或快速同步完成。
- schema 已存在，不需要改表结构（除了 `NopAiModel` 加定价列）。
- `model-switched` 消息常量已定义（`MESSAGE_TYPE_MODEL_SWITCHED = 80`），但从未产生。

---

## 三、核心设计

### 3.1 数据流

```
ReAct 循环每次 LLM 调用
  │
  ├── IModelRouter.route(messages, options, ctx)
  │     └── 返回 RoutingResult(options, complexity, routingReason)
  │           └── 如果 model 与上一轮不同 → 写 NopAiSessionMessage(role=80, model-switched)
  │
  ├── chatService.call(request) → ChatResponse
  │     └── response 含 model_id + usage(promptTokens, completionTokens)
  │
  ├── IUsageRecorder.record(UsageRecord)                          ← 新增
  │     ├── DbUsageRecorder: INSERT NopAiChatResponse
  │     │   (sessionId, modelId, aiProvider, aiModel,
  │     │    promptTokens, completionTokens, responseDurationMs,
  │     │    requestId, responseTimestamp)
  │     └── NoOpUsageRecorder: 丢弃（pass-through，测试用）
  │
  └── AgentSession 累加（保持现有 totalTokensUsed 用于内存预算控制）
        └── 不在内存维护 per-model 分项——查询时从 DB 聚合
```

### 3.2 `IUsageRecorder` 接口

```
IUsageRecorder
  ├── void record(UsageRecord record)
  ├── List<ModelUsageSummary> summarizeByModel(sessionId)
  └── NoOpUsageRecorder（默认 pass-through）
      DbUsageRecorder（生产，写 NopAiChatResponse）
      InMemoryUsageRecorder（测试）
```

`UsageRecord` 数据对象：
- `sessionId`、`agentName`、`requestId`
- `modelId`、`aiProvider`、`aiModel`
- `promptTokens`、`completionTokens`、`responseDurationMs`
- `responseTimestamp`

`ModelUsageSummary` 聚合结果：
- `modelId`、`aiModel`
- `totalPromptTokens`、`totalCompletionTokens`
- `callCount`、`totalDurationMs`
- `estimatedCost`（如果 `NopAiModel` 有定价列则计算，否则 null）

### 3.3 `NopAiModel` 定价列扩展

在 ORM 模型（`nop-ai-dao/.../model/nop-ai.orm.xml` 的 `NopAiModel` 实体）增加：

| 列名 | 类型 | 说明 |
|------|------|------|
| `input_price_per_1m` | DECIMAL(10,4) | 每百万输入 token 单价 |
| `output_price_per_1m` | DECIMAL(10,4) | 每百万输出 token 单价 |
| `reasoning_price_per_1m` | DECIMAL(10,4), nullable | 推理 token 单价（DeepSeek-reasoner/o1 等） |
| `cache_read_price_per_1m` | DECIMAL(10,4), nullable | 缓存读取 token 单价 |
| `cache_write_price_per_1m` | DECIMAL(10,4), nullable | 缓存写入 token 单价 |
| `currency` | VARCHAR(3), default 'USD' | 币种 |

### 3.4 Session 级 per-model 聚合

**不做增量维护**（避免每次 LLM 调用都 UPDATE 多行），而是在查询时聚合：

```sql
SELECT
  r.model_id,
  r.ai_provider,
  r.ai_model,
  SUM(r.prompt_tokens)      AS total_prompt,
  SUM(r.completion_tokens)   AS total_completion,
  COUNT(*)                   AS call_count,
  SUM(r.response_duration_ms) AS total_duration_ms,
  -- 如果 NopAiModel 有定价列：
  SUM(r.prompt_tokens * m.input_price_per_1m / 1000000
    + r.completion_tokens * m.output_price_per_1m / 1000000) AS estimated_cost
FROM nop_ai_chat_response r
LEFT JOIN nop_ai_model m ON r.model_id = m.id
WHERE r.session_id = #{sessionId}
GROUP BY r.model_id, r.ai_provider, r.ai_model
```

**如果查询性能成为瓶颈**（单个 session 超过 1000 次 LLM 调用），增加 `nop_ai_session_model_usage` 物化聚合表，在 `IUsageRecorder.record()` 内 upsert。

### 3.5 `model-switched` 消息产生

在 `ReActAgentExecutor` 中，`IModelRouter.route()` 返回后：

```
if (routingResult 选中的 model != ctx.lastModelId) {
    写 NopAiSessionMessage(
        sessionId = ctx.sessionId,
        role = MESSAGE_TYPE_MODEL_SWITCHED (80),
        metadata = {
            fromModel: ctx.lastModelId,
            toModel: routingResult.selectedModelId,
            routingReason: routingResult.routingReason,
            complexity: routingResult.complexity
        }
    )
    ctx.lastModelId = routingResult.selectedModelId
}
```

这条消息是审计记录，不参与 LLM 推理上下文（role=80 不注入 prompt）。

### 3.6 `AgentSession` / `AgentExecutionContext` 内存模型调整

**保持 `totalTokensUsed` 标量**（用于内存预算控制——不需要 per-model 分项来决定是否压缩上下文）。

**不增加 per-model 内存分项**——这是 DB 查询职责，不是运行时状态。如果 Smart Router 需要知道"当前 session 已用了多少 budget"来做路由决策，调 `IUsageRecorder.summarizeByModel(sessionId)` 或在 `AgentExecutionContext` 维护一个 `estimatedTotalCost`（单一标量，每次 record 时增量更新）。

---

## 四、拒绝了什么

### 4.1 拒绝：在 `AgentSession` 内存中维护 per-model Map

**方案**：`AgentSession` 增加 `Map<String, ModelUsageStats> perModelUsage`。

**拒绝理由**：
- 序列化复杂——`AgentSession` 通过 `ISessionStore` 持久化（File/DB），增加 Map 字段扩大序列化体积。
- 大部分 session 不需要 per-model 查询——增加所有人成本为少数场景买单。
- DB 已经有数据（`NopAiChatResponse`），内存重复维护是冗余。

### 4.2 拒绝：每次 LLM 调用同步 UPDATE `NopAiSession` 的 per-model 列

**方案**：`NopAiSession` 增加 `nop_ai_session_model_usage` 子表，每次 record 时 upsert。

**拒绝理由**：
- 每次 LLM 调用增加一次 DB 写（当前 NopAiSession 更新频率是每 turn 1 次，这里会变成每 LLM call 1 次）。
- 大部分场景查询频率远低于写入频率——物化视图不划算。
- 如果性能成问题再补（见 §3.4 的 fallback）。

### 4.3 拒绝：把计费逻辑放在 `nop-ai-core` 的 `ChatServiceImpl` 中

**方案**：在 LLM 调用返回后，`ChatServiceImpl` 自己写 `NopAiChatResponse`。

**拒绝理由**：
- `nop-ai-core` 不依赖 `nop-ai-dao`（依赖方向不对——`nop-ai-core` 是底层，`nop-ai-dao` 是上层）。
- `ChatServiceImpl` 是通用 LLM 调用服务，不只被 agent 使用。非 agent 场景（如 `nop-ai-coder` 直接调用）不应被强制写 agent 专属表。
- 计费是 agent 的职责，不是 LLM 调用层的职责。

### 4.4 拒绝：引入独立的 billing 微服务

**方案**：单独的计费服务消费 agent 事件流。

**拒绝理由**：
- 过度工程化。当前需求是"能 SQL SUM 查到 per-model 用量"，一个 `IUsageRecorder` 接口 + DB 表足够。
- 如果未来需要独立计费服务，可以基于 `NopAiChatResponse` 表做 CDC（Change Data Capture），不需要现在引入。

---

## 五、与已有设计的关系

| 已有设计 | 关系 |
|---------|------|
| `nop-ai-agent-llm-layer.md` §6（IModelRouter） | 本设计补充了路由决策的**记录**：router 选择模型后产生 `model-switched` 消息，使计费可追溯。 |
| `nop-ai-agent-session-and-storage.md` §17（数据流） | 已有设计提到"LLM turn 结束 → NopAiSession UPDATE（累加 cost/tokensOutput）"，但未设计 per-model 维度。本设计**修正**为：per-model 聚合通过 SQL 查询 `NopAiChatResponse`，`NopAiSession` 的累计字段可选保留（作为快速概览，非权威来源）。 |
| `nop-ai-agent-session-and-storage.md` §17.3（消息类型） | 已定义 `model-switched`（role=80），本设计补充了**产生条件**和 metadata 结构。 |
| `nop-ai-agent-roadmap.md` L2-16（Token 计数） | 已完成 `ILlmDialect.estimateTokens()` + Provider usage 校准。本设计是 L2-16 的**下游消费者**：token 估算/校准的结果通过 `IUsageRecorder` 持久化。 |
| `nop-ai-agent-roadmap.md` Layer 2 扩展点 | 本设计新增 `IUsageRecorder` 作为 Layer 2 扩展点（pass-through 默认 + Db 实现）。 |

### 对 session-and-storage.md 的修正建议

`nop-ai-agent-session-and-storage.md` §17.1 第 451-453 行：

```
LLM turn 结束
  ├── NopAiSessionMessage UPDATE (finishReason, metadata 含 cost/tokens)
  └── NopAiSession UPDATE (累加 cost/tokensOutput/tokensReasoning/totalBytes)
```

修正为：

```
LLM turn 结束
  ├── NopAiChatResponse INSERT (modelId, promptTokens, completionTokens, durationMs)   ← 新增
  ├── NopAiSessionMessage UPDATE (finishReason, metadata 含 cost/tokens/model)
  └── NopAiSession UPDATE (累加 totalTokensUsed 用于内存预算；per-model 聚合查询时算)
```

并补充 `model-switched` 消息产生时机。

---

## 六、实施优先级

| 优先级 | 工作项 | 依赖 | 说明 |
|--------|--------|------|------|
| **P0** | `IUsageRecorder` 接口 + `NoOpUsageRecorder` + `UsageRecord` 数据对象 | L1-5 (ReActExecutor) | Layer 2 pass-through，不阻塞主链路 |
| **P0** | `DbUsageRecorder` 实现：ReAct 循环 token 累加点写 `NopAiChatResponse` | P0 接口 | schema 已存在，只需加写入代码 |
| **P1** | `NopAiModel` 加定价列（`input_price_per_1m` 等） | 无 | ORM 模型变更，需重新生成 |
| **P1** | per-model 聚合查询：`NopAiChatResponseBizModel` 增加 `summarizeByModel(sessionId)` 方法 | P0 DbUsageRecorder | SQL 聚合，自动获得 GraphQL 接口 |
| **P1** | `model-switched` 消息产生逻辑 | L2-10 (IModelRouter) | 在 ReAct 循环 router 返回后检查模型变更 |
| **P2** | `nop_ai_session_model_usage` 物化聚合表（性能优化） | P1 | 仅当单 session 超过 1000 次 LLM 调用时才需要 |
| **P2** | 预算控制 hook：`IModelRouter` 查询已用预算决定是否降级模型 | P1 聚合查询 | Smart Router 的增强能力 |
