# 202 nop-ai-agent DbUsageRecorder 实现（L2-18）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-18
> Last Reviewed: 2026-06-16
> Source: carry-over from `ai-dev/plans/201-nop-ai-agent-usage-recorder-interface.md`（Follow-up: L2-18 DbUsageRecorder 标 "successor required"，deferred 到独立 plan）；`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md` §3.1 + §6 P0（`DbUsageRecorder` 实现：ReAct 循环 token 累加点写 `NopAiChatResponse`）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2（L2-18 ❌ 未实现）
> Related: `201`（交付 `IUsageRecorder` 接口 + `NoOpUsageRecorder` + `UsageRecord` + ReAct 循环接线，本计划的前置依赖，已 completed）；L2-19（`NopAiModel` 定价列，独立 work item）；L2-20（per-model 聚合查询 `summarizeByModel`，依赖本计划）

## Purpose

实现 `DbUsageRecorder`——`IUsageRecorder` 接口的生产实现，通过 raw JDBC 将每次 LLM 调用的用量数据持久化到 `nop_ai_chat_response` 表。本计划把 L2-17 交付的 pass-through 接线升级为真实的 DB 持久化，使 per-model token 用量可查询、为 L2-20（per-model 聚合查询）和 L2-22（预算控制 hook）提供数据基础。

同时补齐 plan 201 显式 deferred 的两项：(1) `modelId` 解析——从 `nop_ai_model` 表按 provider+model 查找主键填充到 `nop_ai_chat_response.model_id`；(2) `responseDurationMs`——在 ReAct 循环测量 LLM 调用耗时并填充到 `UsageRecord`。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IUsageRecorder` 接口已就位**（plan 201 交付，`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/IUsageRecorder.java`）：仅含 `void record(UsageRecord record)` 方法。Javadoc 显式标注 `DbUsageRecorder` 为 L2-18 的生产实现。
- **`UsageRecord` 数据对象已就位**（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/UsageRecord.java`）：10 字段——`sessionId`（String）、`agentName`（String）、`requestId`（String）、`modelId`（String，plan 201 留 null）、`aiProvider`（String）、`aiModel`（String）、`promptTokens`（int）、`completionTokens`（int）、`responseDurationMs`（Long，nullable，plan 201 留 null）、`responseTimestamp`（long）。Javadoc 显式标注 `modelId` 与 `responseDurationMs` 由 L2-18 recorder 填充。
- **`NoOpUsageRecorder` pass-through 默认已就位**（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/NoOpUsageRecorder.java`）：singleton + `noOp()` 静态工厂，`record()` 为显式 no-op。
- **ReAct 循环 token 累积点已调用 `usageRecorder.record()`**（`engine/ReActAgentExecutor.java:694`）。`UsageRecord` 在 `:685-693` 构造：sessionId ← `sessionId` 变量、agentName ← `agentName` 变量、requestId ← `response.getRequestId()`、aiProvider ← `routedOptions.getProvider()`、aiModel ← `routedOptions.getModel()`、promptTokens/completionTokens ← 已计算值、responseTimestamp ← `System.currentTimeMillis()`。`modelId` 与 `responseDurationMs` 未设置（留 null）。
- **LLM 调用位于 `ReActAgentExecutor.java:656`**：`ChatResponse response = chatService.call(request, null);`。当前无 LLM 调用耗时计量——`System.currentTimeMillis()` 仅在 `:693` 用于 `responseTimestamp`，LLM 调用之前无起始时间戳。
- **`nop_ai_chat_response` 表已存在**：ORM 实体 `NopAiChatResponse`（`nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/NopAiChatResponse.java` → `nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/_gen/_NopAiChatResponse.java`）。表结构含 20 列：`id`（VARCHAR PK）、`request_id`、`session_id`、`model_id`（VARCHAR FK→`nop_ai_model.id`）、`ai_provider`、`ai_model`、`response_content`、`response_timestamp`（TIMESTAMP）、`prompt_tokens`（INTEGER）、`completion_tokens`（INTEGER）、`response_duration_ms`（INTEGER）、4 个 score 列（DECIMAL，本计划不写）、`version`、`created_by`、`create_time`（TIMESTAMP）、`updated_by`、`update_time`（TIMESTAMP）。
- **`nop-ai-agent` 不依赖 `nop-ai-dao`**（`pom.xml` 确认依赖仅为 nop-ai-toolkit、nop-ai-core、nop-message-core、nop-dao 框架模块）。`nop-ai-agent` 内所有 DB 写入组件均使用 **raw JDBC**（`DataSource` + `PreparedStatement`），不引用 `nop-ai-dao` ORM 实体类。
- **现有 raw JDBC 写入模式参照**（均为 `nop-ai-agent` 内，`DbUsageRecorder` 须遵循相同模式）：
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/DBSessionStore.java` — `DataSource` 构造器注入 + `initSchema()` DDL + MERGE INTO + ConcurrentHashMap 缓存
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java` — `DataSource` 构造器注入 + SQL INSERT + 线程安全
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java` — `DataSource` 构造器注入
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DBDenialLedger.java` — `DataSource` 构造器注入
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AiAgentSessionTable.java` — 表名/列名 String 常量 + DDL CREATE TABLE 常量（DbUsageRecorder 的常量类参照此模式）
- **`DefaultAgentEngine` 已有 `usageRecorder` mutable 字段 + `setUsageRecorder` setter**（plan 201 交付，`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`），默认 `NoOpUsageRecorder.noOp()`，通过 `resolveExecutor` Builder 链传递到 `ReActAgentExecutor`。集成商通过 `engine.setUsageRecorder(new DbUsageRecorder(dataSource))` 注入。
- **`DefaultAgentEngine` 无 `DataSource` 字段**——所有 DB 组件均由集成商在外部创建并注入，DbUsageRecorder 同理。
- **roadmap §4 Layer 2**：L2-18 标 ❌ 未实现。

## Goals

- `DbUsageRecorder` 类存在，实现 `IUsageRecorder`，通过 raw JDBC 将 `UsageRecord` 持久化到 `nop_ai_chat_response` 表。
- `DbUsageRecorder.record()` 在写入前从 `nop_ai_model` 表按 `ai_provider` + `ai_model` 查找 `model_id` 并填充到 INSERT 语句（找不到时 `model_id` 为 null，不阻塞写入——`ai_provider` + `ai_model` 仍记录）。
- ReAct 循环 LLM 调用耗时被测量并填充到 `UsageRecord.responseDurationMs`（在 `chatService.call()` 之前捕获起始时间戳，在构造 UsageRecord 时计算差值设置）。
- `DbUsageRecorder` 采用 `DataSource` 构造器注入模式，与 `DBSessionStore` / `DBMessageService` 等 DB 组件一致。
- `DbUsageRecorder` 不改变 `NoOpUsageRecorder` 的默认地位——它是 opt-in 实现，集成商显式注入。
- Focused 测试验证：DB 行被正确写入（字段值匹配 UsageRecord）、modelId 解析（找到/找不到两种路径）、LLM 调用耗时计量为正值、端到端从 `engine.execute()` 到 DB 行的完整路径。

## Non-Goals

- **L2-19（`NopAiModel` 定价列）**：ORM 模型变更，独立 work item，无依赖。
- **L2-20（per-model 聚合查询 `summarizeByModel`）**：SQL GROUP BY 查询放在 `NopAiChatResponseBizModel`（nop-ai-service 层），独立 work item，依赖本计划。
- **L2-21（model-switched 消息产生）**：ReAct 循环中 IModelRouter 返回后检查模型变更写 role=80 消息，独立 work item。
- **L2-22（预算控制 hook）**：IModelRouter 查询已用预算决定降级模型，依赖 L2-20。
- **不改 `NoOpUsageRecorder` 默认地位**：`NoOpUsageRecorder` 仍为 shipped 默认，`DbUsageRecorder` 是 opt-in。
- **不加 `nop-ai-dao` 依赖到 `nop-ai-agent`**：保持模块依赖方向不变。`DbUsageRecorder` 使用 raw JDBC，不引用 `NopAiChatResponse` 实体类。
- **不改 `IUsageRecorder` 接口**：接口形态在 plan 201 已裁定，本计划只新增实现类。
- **不写 `responseContent` 列**：设计 §3.1 的 INSERT 不含 response 内容（`UsageRecord` 也无此字段），`nop_ai_chat_response` 行的 `response_content` 为 null。
- **不加 `DataSource` 字段到 `DefaultAgentEngine`**：`DbUsageRecorder` 由集成商创建并注入，与 `DBSessionStore` 等组件一致。

## Scope

### In Scope

- 新增 `DbUsageRecorder` 类（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/DbUsageRecorder.java`，implements `IUsageRecorder`，raw JDBC INSERT 到 `nop_ai_chat_response`）
- 新增 `NopAiChatResponseTable` 常量类（表名/列名 String 常量 + DDL CREATE TABLE IF NOT EXISTS，参照 `AiAgentSessionTable` 模式）
- `DbUsageRecorder` 内 modelId 解析逻辑（SELECT `nop_ai_model` 按 provider+model，找不到时 null）
- `ReActAgentExecutor` 修改：在 LLM 调用前捕获起始时间戳，在构造 UsageRecord 时设置 `responseDurationMs`
- Focused 测试：`DbUsageRecorder` 单元测试、LLM 调用耗时计量测试、端到端接线测试
- 设计文档 `nop-ai-agent-usage-and-billing.md` 更新（标注 DbUsageRecorder 实现落地）
- roadmap §4 Layer 2 表格 L2-18 状态更新

### Out Of Scope

- L2-19 ~ L2-22 全部独立 work items（见 Non-Goals）
- `nop-ai-dao` 依赖添加到 `nop-ai-agent`
- `IUsageRecorder` 接口变更
- `NopAiChatResponse` ORM 实体变更

## Execution Plan

### Phase 1 - DbUsageRecorder 实现 + LLM 调用耗时计量

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/DbUsageRecorder.java`（新增）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/NopAiChatResponseTable.java`（新增）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（修改）

- Item Types: `Fix`（功能缺失 = contract gap：plan 201 交付 pass-through 接线，实际 DB 持久化未实现）

- [x] 新增 `NopAiChatResponseTable` 常量类（参照 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AiAgentSessionTable.java` 模式）：表名 `nop_ai_chat_response` + 列名常量（id, request_id, session_id, model_id, ai_provider, ai_model, prompt_tokens, completion_tokens, response_duration_ms, response_timestamp, version, created_by, create_time, updated_by, update_time）+ DDL CREATE TABLE IF NOT EXISTS（列定义须与 `nop-ai-dao` ORM 模型 `_NopAiChatResponse.java` 一致）
- [x] 新增 `DbUsageRecorder` 类（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/DbUsageRecorder.java`，implements `IUsageRecorder`）：
  - 构造器接收 `DataSource`（`Objects.requireNonNull` 校验）
  - `record(UsageRecord)` 方法逻辑：(1) 可选 `initSchema()`（CREATE TABLE IF NOT EXISTS，防御性，参照 `DBSessionStore.initSchema()`）；(2) modelId 解析：从 `nop_ai_model` 表查找与 `UsageRecord.aiProvider` + `UsageRecord.aiModel` 匹配的记录主键——找到则填充 `model_id`，找不到则 null（不阻塞写入）。列名须以 `_NopAiModel.java`（`nop-ai/nop-ai-dao/src/main/java/io/nop/ai/dao/entity/_gen/_NopAiModel.java`）中的实际列定义为准；(3) raw JDBC INSERT 到 `nop_ai_chat_response`，写入 id（UUID）、request_id、session_id、model_id、ai_provider、ai_model、prompt_tokens、completion_tokens、response_duration_ms、response_timestamp、version、create_time、update_time；(4) SQL 异常包装为 `NopAiAgentException`（不吞异常——Minimum Rules #24）
  - 线程安全：`DataSource` 本身线程安全，每次 `record()` 使用独立 `Connection`（try-with-resources），无共享可变状态
- [x] `ReActAgentExecutor` 修改（LLM 调用耗时计量）：在 `chatService.call(request, null)`（当前 `:656`）之前新增 `long llmCallStart = System.currentTimeMillis()`；在构造 `UsageRecord` 的区域（当前 `:685-693`）新增 `usageRecord.setResponseDurationMs(System.currentTimeMillis() - llmCallStart)`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DbUsageRecorder.java` 文件存在于 `usage/` 包下，声明 `implements IUsageRecorder`
- [x] `NopAiChatResponseTable.java` 文件存在于 `usage/` 包下，含表名/列名常量 + DDL
- [x] `DbUsageRecorder` 构造器接收 `DataSource`，`record()` 方法通过 JDBC INSERT 写入 `nop_ai_chat_response` 表
- [x] `DbUsageRecorder` modelId 解析：查询 `nop_ai_model` 表按 provider+model 匹配（列名以 `_NopAiModel.java` 实际定义为准）——找到时填充 model_id，找不到时 model_id 为 null（不抛异常）
- [x] `ReActAgentExecutor` LLM 调用区域含起始时间戳捕获 + `UsageRecord.setResponseDurationMs(...)` 设置
- [x] **无静默跳过**（Minimum Rules #24）：`record()` 的 SQL 异常包装为 `NopAiAgentException`，不吞异常；modelId 查不到时为 null（这是合法的 graceful degradation，不是静默跳过——ai_provider + ai_model 仍写入，且 `model_id` nullable 在 schema 设计中已预期）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 测试 + 文档更新

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/usage/`（新增测试）、`ai-dev/design/nop-ai-agent/nop-ai-agent-usage-and-billing.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Proof`、`Follow-up`

- [x] 新增 `TestDbUsageRecorder`：使用内存 H2 `DataSource`（参照 `TestDBSessionStore` 的 H2 初始化模式），测试 setup 须创建 `nop_ai_chat_response` 表（通过 `NopAiChatResponseTable` DDL）和 `nop_ai_model` 表（通过内联 DDL，列定义以 `_NopAiModel.java` 为准）。验证：(1) `record()` 后 `nop_ai_chat_response` 表含对应行；(2) 行字段值与 `UsageRecord` 匹配（session_id、request_id、ai_provider、ai_model、prompt_tokens、completion_tokens、response_duration_ms、response_timestamp）；(3) modelId 解析找到路径（预插入 `nop_ai_model` 行）；(4) modelId 解析未找到路径（model_id 为 null，其余字段正常写入）；(5) SQL 异常包装为 `NopAiAgentException`
- [x] 新增 `TestDbUsageRecorderWiring`：端到端测试——注入 `DbUsageRecorder` 到 `DefaultAgentEngine`，运行 ReAct 循环（mock LLM 返回含 usage 的 response），断言 `nop_ai_chat_response` 表行数 = LLM 调用次数、行字段值正确（含 `response_duration_ms` > 0）
- [x] `nop-ai-agent-usage-and-billing.md` 更新：§3.1 标注 DbUsageRecorder 实现已落地（raw JDBC 模式，不依赖 nop-ai-dao）
- [x] roadmap §4 Layer 2 表格 L2-18 从 ❌ → ✅，标注 plan 202

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TestDbUsageRecorder` 存在，覆盖 5 条断言（行写入、字段匹配、modelId 找到/未找到、异常包装）
- [x] **端到端验证**（Minimum Rules #22）：`TestDbUsageRecorderWiring` 通过——从 `DefaultAgentEngine.execute()` → ReAct 循环 → `DbUsageRecorder.record()` → `nop_ai_chat_response` DB 行的完整路径验证通过
- [x] **接线验证**（Minimum Rules #23）：`TestDbUsageRecorderWiring` 断言 `DbUsageRecorder.record()` 在运行时被调用（DB 行数 = LLM 调用次数），且 `responseDurationMs` > 0
- [x] **新功能测试覆盖**（Minimum Rules #25）：DbUsageRecorder 的 record() 写入、modelId 解析（两种路径）、异常包装、端到端接线均有对应测试
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（含新增测试 + 既有 tests 零回归）
- [x] `nop-ai-agent-usage-and-billing.md` §3.1 标注 DbUsageRecorder 实现落地
- [x] roadmap §4 Layer 2 表格 L2-18 标注 ✅ + plan 202
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `DbUsageRecorder` 类存在，通过 raw JDBC 将 UsageRecord 持久化到 `nop_ai_chat_response`
- [x] modelId 解析逻辑存在（查询 `nop_ai_model` 表 + 找不到时 null）
- [x] `responseDurationMs` 在 ReAct 循环中被测量并填充
- [x] 端到端接线完整：`DefaultAgentEngine.execute()` → ReAct 循环 → `DbUsageRecorder.record()` → DB 行
- [x] Focused 测试通过（DB 行写入 + modelId 解析 + 耗时计量 + 端到端）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（L2-19~L2-22 均显式在 Non-Goals 切出）
- [x] 受影响 owner docs 已同步：`nop-ai-agent-usage-and-billing.md`（DbUsageRecorder 实现标注）、roadmap §4（L2-18 ✅）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`DbUsageRecorder.record()` 在运行时被调用（通过端到端测试 DB 行计数验证），（b）modelId 解析不是空壳（测试覆盖找到/未找到两条路径），（c）`responseDurationMs` 非空壳（端到端测试断言 > 0）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；L2-19~L2-22 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`nop_ai_session_model_usage` 物化聚合表**：设计 §3.4 / §4.2 的 fallback——仅当单 session 超过 1000 次 LLM 调用时才需要增量维护聚合表。当前 `nop_ai_chat_response` 逐行写入 + 查询时 SQL 聚合足够。Classification: optimization candidate。
- **`responseContent` 列写入**：当前 `UsageRecord` 不含 response 内容字段，`nop_ai_chat_response.response_content` 为 null。如后续需要记录 LLM 响应文本（如用于 replay/debug），需扩展 `UsageRecord` 数据对象。Classification: out-of-scope improvement。
- **异步写入**：当前 `record()` 是同步 JDBC INSERT（设计 §2.3 要求"不阻塞 ReAct 主循环——用量记录必须在 LLM 响应返回后异步或快速同步完成"）。同步写入对 H2/本地 DB 足够快；若生产环境使用远程 DB 且延迟敏感，可改为异步队列写入。Classification: optimization candidate。

## Closure

Status Note: L2-18 `DbUsageRecorder` 已实现——通过 raw JDBC（`DataSource` + `PreparedStatement`，不依赖 nop-ai-dao、不引用 NopAiChatResponse ORM 实体）将每次 LLM 调用的用量数据持久化到 `nop_ai_chat_response`。modelId 在写入前按 provider+model_name 查 `nop_ai_model` 解析（找不到时 null，graceful degradation）；responseDurationMs 在 `ReActAgentExecutor` 的 `chatService.call()` 前后计量并填充。`NoOpUsageRecorder` 仍是 shipped 默认，`DbUsageRecorder` 是 opt-in（集成商显式 `engine.setUsageRecorder(new DbUsageRecorder(dataSource))`）。L2-19~L2-22 均为显式 Non-Goals 独立 successor。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task id: ses_133619b13ffepxbjz0RzjkrZ4g，fresh session，非实现 session）
- Audit Session: ses_133619b13ffepxbjz0RzjkrZ4g
- Evidence:
  - Phase 1 Exit Criteria（7/7 PASS）：`DbUsageRecorder.java:50` implements IUsageRecorder；`NopAiChatResponseTable.java:22-77` 常量+DDL；构造器 `Objects.requireNonNull(dataSource)` + `record()` JDBC INSERT 13 参数；modelId 解析 SQL `WHERE provider = ? AND model_name = ?`（列名对齐 `_NopAiModel.java:28,31`）；`ReActAgentExecutor.java:660` llmCallStart + `:700` setResponseDurationMs；SQL 异常包装 NopAiAgentException（L137-140），不吞异常。
  - Phase 2 Exit Criteria（8/8 PASS）：`TestDbUsageRecorder`（7 tests）覆盖行写入+字段匹配、modelId 找到/未找到、异常包装；`TestDbUsageRecorderWiring`（2 tests）端到端断言 DB 行数=2（=LLM 调用数）+ responseDurationMs>0；设计文档 §3.1 已标注；roadmap L2-18 ✅。
  - Closure Gates（12/12 PASS）：见上方已勾选项。
  - `./mvnw test -pl nop-ai/nop-ai-agent -am`：1614 tests 全绿（1605 baseline + 9 new），零回归。
  - Anti-Hollow 检查：(a) record() 运行时被调用——wiring 测试用真实 SELECT COUNT FROM nop_ai_chat_response 断言行数；(b) modelId 解析非空壳——找到/未找到两条路径均有断言；(c) responseDurationMs 非空壳——mock 含 sleepQuietly(5) + 断言 >0；(d) 无静默跳过——INSERT/initSchema/null arg 均抛 NopAiAgentException，modelId 未找到时 LOG debug + 仍写 ai_provider/ai_model。
  - Deferred 项分类检查：L2-19~L2-22 均为显式 Non-Goals（非 deferred 降级），无 in-scope live defect 被降级。

Follow-up:

- no remaining plan-owned work（L2-19~L2-22 为独立 successor plans）
- Non-Blocking Follow-ups 见上节（物化聚合表、responseContent 写入、异步写入——均 optimization candidate / out-of-scope improvement）
