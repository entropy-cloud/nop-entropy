# nop-datav AI/ChatBI 设计 (D6)

> Status: **final**（D6-1 数据集查询能力交付，plan `ai-dev/plans/nop-datav/2026-08-10-1300-1-chatbi-nl-dataset-query.md`）。
> 本文件是 D6 ChatBI 集成的权威设计契约：工具集成方案、API 选择、被拒方案、类型转换契约、安全边界。
> 相关代码：`nop-datav-service/.../chatbi/*`（executor + BizModel + tool-calling 循环）、
> `nop-datav-service/.../_vfs/nop/ai/tools/*.tool.xml`（工具定义）。

---

## 1. 背景与目标

将 nop-datav 从"被动配置型 BI"扩展为"对话型 BI"：用户用自然语言提问，系统经 nop-ai LLM + tool-calling
自动发现数据集、理解字段、执行查询并返回结构化结果。

**D6-1 交付范围**：NL → 数据集查询 → 结构化结果（columns + rows）。

**显式 Non-Goal**：NL → 看板/面板配置生成、NL → 大屏生成、前端聊天 UI、会话历史持久化、
NL → 裸 SQL 生成、ChatBI 细粒度可见性控制（见 plan Non-Goals）。

---

## 2. 关键裁定（Decision A–F）

### 裁定 A：tool-calling 循环方案 —— 轻量自建循环，不复用 `IAgentEngine`

**选择**：ChatBI BizModel 内自建轻量 tool-calling 循环（`ChatBiToolCallingLoop`），**参考但不依赖**
`nop-ai-agent` 的 `AgentToolDispatcher` / `ToolSchemaConverter` 的类型转换逻辑。

**理由**：
- ChatBI 的 Non-Goal 明确为**无状态单轮**（单次请求内可多轮 tool-call，但不跨请求记忆会话）。
- `nop-ai-agent` 的 `IAgentEngine` / `ReActAgentExecutor` 面向长生命周期 agent session，重载了
  session/budget/guardrail/team/memory/ checkpoint 等机制，对 ChatBI 单轮场景是过重依赖。
- 三个类型转换（chat-api ↔ toolkit）逻辑各 ~3-10 行，复制参考实现远低于引入整个 agent 模块的成本。
- 镜像项目内已有"参考不依赖"先例：`PanelSqlBuilder` 复用 nop-report 数据集字段而非引入
  nop-report-core 渲染引擎。

**被拒方案**：复用 `ReActAgentExecutor`（引入 nop-ai-agent compile 依赖 + session 存储重载，违反 Non-Goal）。

### 裁定 B：chat-api ↔ toolkit 类型转换契约

ChatBI 循环中存在三个跨模块类型转换，**全部以参考 `AgentToolDispatcher` / `ToolSchemaConverter`
的内联实现方式落地**（复制逻辑，不引入 nop-ai-agent 依赖）。字段映射如下：

| 转换方向 | 来源 → 目标 | 字段映射 | 参考实现 |
|---------|------------|---------|---------|
| `ChatToolCall` → `AiToolCall` | chat-api → toolkit | `name`→`toolName`、`argumentsText`(JSON)→`input` | `AgentToolDispatcher:194-196` |
| `AiToolModel` → `ChatToolDefinition` | toolkit → chat-api | `name`→`name`、`description`→`description`、`schema` XNode 经 `ToolSchemaConverter.convert()` → `parameters` Map | `ToolSchemaConverter.convert(XNode)` |
| `AiToolCallResult` → `ChatToolResponseMessage` | toolkit → chat-api | `status`/`output.body`/`error.body` 映射，**用保留的原 `ChatToolCall` 引用恢复 toolCallId** | `AgentToolDispatcher:307-324` |

**关键点（toolCallId 追踪）**：`AiToolCall`（toolkit）无 toolCallId 字段，而 `ChatToolResponseMessage`
（chat-api）的 `callId` 字段是回喂 LLM 的必需配对键。因此 BizModel 在发起每个 tool call 时**保留原
`ChatToolCall` 引用**，结果返回后经 `ChatToolResponseMessage.fromToolCall(chatToolCall, resultText)` 或
`ChatRequest.addToolResponse(toolCallId, name, content)` 恢复配对。

落地位置：`ChatBiTypeConverter`（静态工具类，位于 `io.nop.datav.service.chatbi`）。

### 裁定 C：`IToolExecuteContext` 构造方式 —— 最小实现

**选择**：ChatBI 构造一个最小 `IToolExecuteContext` 实现（`ChatBiToolExecuteContext`），仅满足接口契约。
DB 查询类 executor 不依赖 `workDir`/`envs`/`fileSystem`/`executor` 字段（只用 `cancelToken`），因此
这些字段可安全置为 null/空。

**理由**：参考 `SimpleToolExecuteContext`（nop-ai-agent）的轻量实现模式，但 ChatBI 不引入 nop-ai-agent
依赖，故自建最小实现。`getCompactionArchiveReader()` 继承接口的默认 UOE 实现（read-ref 工具不适用于 ChatBI）。

### 裁定 D：循环终止条件语义

| 条件 | 判定 | 结果 |
|-----|------|------|
| 最终答案 | `ChatResponse.outputToolCalls()` 为**空**（LLM 未请求更多工具调用） | 返回 `ChatResponse.outputText()` + 解析的查询结果 |
| 超限 | 迭代次数 ≥ `maxIterations`（配置项 `nop.datav.chatbi.max-iterations`，默认 5） | 抛 `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED` |

每次循环计为一次 `IChatService.call` 调用（一次 LLM 往返）。

### 裁定 E：API 选择 + 查询语义 + 安全

**API 选择**：
- 用新 `IChatService`（`nop-ai-api`）+ `IToolManager`/`IToolExecutor`（`nop-ai-toolkit`）。
- **拒** `IAiChatService` / `IAiChatFunctionTool` / `IAiChatToolSet`（nop-ai-core）—— 均标
  `@Deprecated(forRemoval = true)`，是遗留 API。

**查询语义**：ChatBI 只能**选已有数据集 + 填参数**（经 tool-calling），**不能凭空生成 SQL**。

**安全**：
- **拒 NL → 裸 SQL**：LLM 生成任意 SQL 带注入/越权风险，显式拒绝。ChatBI 只能选择已注册的
  `NopReportDataset`（dsType=sql）并填入参数，数据集 SQL 由管理员预审。
- **参数化绑定**：LLM 提供的 params 值经 `PanelSqlBuilder.build` 的 `?` 占位符 + 顺序绑定
  （`SQL.SqlBuilder.sqlWithParams`），**非字符串拼接**，从机制上防 SQL 注入。
- **maxRows 防护**：查询经 `IJdbcTemplate.executeQuery` 的 `LongRangeBean` 限行（跨方言，dialect paging），
  防止 LLM 触发大结果集 OOM。

### 裁定 F：@Nullable 注入 + 工具清单 + 安全边界

**@Nullable 注入**：`IChatService` / `IToolManager` 可空注入（镜像 D5-1 `IJobScheduler` 范式，
`@Inject @Nullable` setter）。nop-ai 缺席时（宿主未注册 chat/toolkit bean）ChatBI action
**显式抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`**，非静默返回 null（Minimum Rules #24）。

**3 个工具清单**：

| 工具名 | 输入 | 输出 |
|-------|------|------|
| `datav-list-datasets` | 可选 `keyword`(string) | 数据集清单（sid/dsName/description/dsType），仅 `status=活跃` |
| `datav-describe-dataset` | 必填 `datasetSid`(string) | 字段元数据（dsMeta）+ 参数定义（dsConfig） |
| `datav-query-dataset` | 必填 `datasetSid`(string) + 可选 `params`(object) + 可选 `maxRows`(int) | columns + rows |

**默认值与配置项**（`NopDatavConfigs.CFG_DATAV_CHATBI_*`）：
- `nop.datav.chatbi.max-iterations`：默认 5（tool-calling 轮次上限）。
- `nop.datav.chatbi.max-rows`：默认 1000（单次查询行数上限）。
- `nop.datav.chatbi.default-model`：默认 ""（空时由 `IChatService` 实现决定 provider 默认）。
- `nop.datav.chatbi.default-provider`：默认 ""。

**错误码**（`NopDatavErrors.ERR_DATAV_CHATBI_*`）：
- `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`（nop-ai 缺席）
- `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED`（超 maxIterations）
- `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`（工具执行错误透传）
- `ERR_DATAV_CHATBI_NO_RESULT`（LLM 未能产出有效结果）

---

## 3. 工具集成架构

```
用户 NL 问题
   │
   ▼
NopDatavChatBiBizModel.chatToQuery(@Auth)
   │  ← @Nullable 注入 IChatService + IToolManager
   │     null → ERR_DATAV_CHATBI_AI_NOT_AVAILABLE
   ▼
ChatBiToolCallingLoop.run(question, tools, maxIterations)
   │
   │  构建 ChatRequest(systemPrompt + userPrompt + tools)
   │  tools = IToolManager.listTools() → ChatBiTypeConverter.toChatToolDefinition()
   │
   │  循环（最多 maxIterations 次）:
   │    1. IChatService.call(chatRequest) → ChatResponse
   │    2. ChatResponse.outputToolCalls()
   │       ├─ 空 → 终止，返回 outputText()
   │       └─ 非空 → 每个 ChatToolCall:
   │            ChatToolCall → AiToolCall (TypeConverter)
   │            IToolManager.callTool(name, aiToolCall, ctx).join()
   │            AiToolCallResult → ChatToolResponseMessage (TypeConverter, 保留原 ChatToolCall)
   │            ChatRequest.addToolResponse(toolCallId, name, content)
   │            继续循环
   ▼
ChatBiResult(answer + columns + rows)
```

**3 个 IToolExecutor 实现**：

| Executor | 工具 | 职责 |
|----------|------|------|
| `DatavListDatasetsExecutor` | datav-list-datasets | 查 `NopReportDataset` status=活跃 清单 |
| `DatavDescribeDatasetExecutor` | datav-describe-dataset | 按 sid 加载 + 解析 dsMeta/dsConfig |
| `DatavQueryDatasetExecutor` | datav-query-dataset | 按 sid 加载 + `PanelSqlBuilder.build` + `IJdbcTemplate.executeQuery` |

executor 经 `app-service.beans.xml` 注册为 `<bean>`，被 `nopToolExecutorProvider` 的
`<ioc:collect-beans by-type="...IToolExecutor"/>` 自动收集（`nopToolManager` / `nopToolExecutorProvider`
由 nop-ai-toolkit 的 `ai-tools-defaults.beans.xml` 提供，宿主引入 nop-ai 后即生效）。

**executor 输入解析**：ChatBI 路径下 `AiToolCall.input` 是 LLM arguments 的 JSON 字符串
（经 `AgentToolDispatcher:194-196` 的 `setInput(argumentsText)` 转换）。executor 用
`call.getInput()` 取该 JSON 串，经 `JsonTool.parseMap` 解析参数。

---

## 4. 数据流与查询语义

**ChatBI query executor 参数路径**（与 PanelDataBinder 路径的关键差异）：

| 路径 | params 来源 | SQL 构建 |
|-----|-----------|---------|
| `PanelDataBinder.queryPanelData` | `PanelParamEvaluator.evaluate(datasetRef.paramMapping, requestParams)` —— 依赖 DatasetRef.paramMapping | `PanelSqlBuilder.build(dsText, params, panelId)` |
| `DatavQueryDatasetExecutor` | **LLM params Map 直接作为 params 参数** —— 不含 DatasetRef，**不复用 PanelParamEvaluator** | `PanelSqlBuilder.build(dsText, params, datasetSid)` |

两条路径共用 `PanelSqlBuilder.build`（`?` 占位符 + 顺序绑定，防注入），仅参数来源不同。
ChatBI 不经 DatasetRef（无面板上下文），直接查 `NopReportDataset`。

---

## 5. 被拒方案汇总

| 方案 | 拒绝理由 |
|-----|---------|
| 复用 `IAgentEngine` / `ReActAgentExecutor` | 过重依赖（session/budget/guardrail/team/memory 重载），违反 ChatBI 无状态单轮 Non-Goal |
| `IAiChatService`（@Deprecated） | 遗留 API，标 `@Deprecated(forRemoval = true)` |
| NL → 裸 SQL 生成 | 安全风险（注入/越权）；只能选已有数据集 + 填参数 |
| 静默跳过（nop-ai 缺席时返回 null） | 违反 Minimum Rules #24；必须显式失败 |
| 引入 nop-ai-agent 依赖 | 类型转换逻辑仅 ~10 行，复制参考远低于引入整个 agent 模块的成本 |

---

## 6. 权限与安全边界

- ChatBI action 经 `@Auth(permissions = "NopDatavChatBi:chatToQuery")`，在
  `nop-datav.action-auth.xml` 增配权限点（roles="admin,user"）。
- list executor 按 `status=1`（活跃）过滤，复用既有 owner RLS（细粒度 ChatBI 可见性控制为 follow-up）。
- query executor 的 maxRows 经 `LongRangeBean` 在数据集层限行（防 OOM）。
- system prompt 含"禁止生成 SQL"约束（LLM 只能调用工具，不能产出 SQL 文本）。

---

## 7. 配置与可观测

| 配置项 | 默认值 | 说明 |
|-------|-------|------|
| `nop.datav.chatbi.max-iterations` | 5 | tool-calling 轮次上限 |
| `nop.datav.chatbi.max-rows` | 1000 | 单次查询行数上限 |
| `nop.datav.chatbi.default-model` | "" | 默认 LLM model |
| `nop.datav.chatbi.default-provider` | "" | 默认 provider |

**Follow-up（不在 D6-1 scope）**：ChatBI 查询审计日志（NL 问题 + tool calls + 结果摘要）、
多模型热切换、查询结果可视化建议、会话历史持久化。
