# nop-datav AI/ChatBI 设计 (D6)

> Status: **final**（D6-1 数据集查询 + D6-1b 看板生成 + D6-2 大屏生成能力交付，
> plans `ai-dev/plans/nop-datav/2026-08-10-1300-1-chatbi-nl-dataset-query.md` +
> `ai-dev/plans/nop-datav/2026-08-10-1516-1-nl-dashboard-panel-generation.md` +
> `ai-dev/plans/nop-datav/2026-08-10-1516-2-ai-screen-generation.md`）。
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

---

## 8. D6-1b 看板生成（NL → 看板/面板配置生成）

> 来源：plan `ai-dev/plans/nop-datav/2026-08-10-1516-1-nl-dashboard-panel-generation.md`。
> 在 D6-1「只查询不创作」之上扩展「对话式创作」：用户用 NL 描述想要的看板，系统经 tool-calling
> 自动发现数据集、理解字段、**生成一个草稿看板（含面板 + 数据集引用 + 字段映射）**并返回 dashboardId。

### 8.1 关键裁定（Decision G–O）

#### 裁定 G：operator 传递全链路

**选择**：BizModel 从 `IServiceContext` 经 `NopDatavOperatorResolver.resolveOperator(context)` 解析
operator → 传入泛化循环（`run()` 新增 `operator` 入参）→ 循环构建携带 operator 的
`ChatBiToolExecuteContext`（该类新增 `operator` 字段 + getter）→ `DatavGenerateDashboardExecutor` 将
入参 `IToolExecuteContext` **强转为 `ChatBiToolExecuteContext`** 读取 operator → 手动设置
Dashboard/Panel/DatasetRef 的 `createdBy`/`updatedBy` 审计列。

**理由**：
- `IToolExecutor.executeAsync(AiToolCall, IToolExecuteContext)` 签名无 `IServiceContext`，无法直接拿到用户上下文。
- 扩展 `ChatBiToolExecuteContext`（ChatBI 自有的 context 实现）携带 operator 是最小侵入方案：既不污染
  `IToolExecuteContext` 公共接口，又让 ChatBI 自家的两个 executor（query + generate）能共享同一 context 类型。
- tool executor 无 BizModel 用户上下文，**审计列必须手动填充**（非由框架自动注入），否则 `createdBy` 为 null。

**context↔executor 强转耦合契约**：`DatavGenerateDashboardExecutor` 假定传入的 `IToolExecuteContext`
是 `ChatBiToolExecuteContext` 实例（`ClassCastException` 不可恢复时由循环保证传入正确类型）。该假定仅
在 ChatBI 循环内成立；executor 不可被其他（非 ChatBI）tool-calling 路径调用。

**owner 身份 ≡ `createdBy`**：`NopDatavDashboard` 无独立 `owner` 列（身份/归属字段即 `createdBy`，
domain="createdBy", mandatory）。RLS 按 `createdBy == $context.userName`。生成看板的归属即 operator。

**被拒方案**：executor 可变状态 setter（`setOperator(String)` 注入）。拒绝理由：executor 是 IoC 单例，
可变 setter 在并发 tool-calling 下线程不安全（多请求共享实例，operator 会被覆盖）。强转 + per-request
context 是线程安全的。

#### 裁定 H：草稿语义与发布边界

**选择**：生成工具创建的 Dashboard 为 `publishStatus=DRAFT`（0）、`publishedVersion=0`、**不写快照表**
（`NopDatavDashboardSnapshot`）。用户经既有 `publishDashboard` 审阅发布。

**理由**：人审节点不可省略。LLM 产出的配置可能有字段映射语义错误（X/Y 轴选反），自动发布会把错误配置
直接推上线。草稿语义保留人审关卡：用户在草稿上预览、修正、再手动发布。

#### 裁定 I：DatasetRef 去重策略

**选择**：同一生成规格内多个 panel 引用同一 `datasetSid` 时，创建**一个** `NopDatavDatasetRef` 复用
（而非每 panel 一个）。去重键 = `(dashboardId, refDatasetId)`，应用层去重（无 DB unique 约束）。

**paramMapping 初值**：生成阶段为空 `{}`（用户后续在草稿上补充参数映射）。

**needsDataset=false 的 panel（text/iframe/container）不参与去重**（无 DatasetRef 关联，`datasetRefId=null`）。

#### 裁定 J：校验失败语义

**选择**：规格校验失败时工具返回**显式错误结果**（`status=failure` + 错误码 + 描述），由 LLM 在下一轮
修正，**不静默跳过**（Minimum Rules #24）。

**每类校验失败的错误码**：

| 校验失败类 | 错误码 |
|-----------|--------|
| 未知 componentType（不在 14 类注册表内） | `ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT` |
| 装饰类型（注册表内但无 panelType int 映射，用于大屏） | `ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT` |
| datasetSid 不存在 / 非活跃（status≠1） | `ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND` |
| fieldMapping 引用字段不在 dsMeta 字段名集合 | `ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC` |
| needsDataset=true 组件未提供 datasetSid | `ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC` |
| panels 为空 / dashboardName 为空 | `ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC` |

#### 裁定 K：结果类型设计

**选择**：泛化 `ChatBiResult`，新增**可选** `createdEntityId` 字段。查询路径不用（保持 null），
生成路径填 dashboardId。`answer`（LLM 最终文本）+ `iterations` 两路径共用。

**理由**：
- `columns`/`rows` 是查询专用语义；`dashboardId` 是生成专用语义。二者并存于同一 result，用可选字段区分，
  避免 fork 两个 result 类型。
- 与裁定 L 协同：泛化循环返回同一 `ChatBiResult`，两个路径各自填充自己的字段。

**被拒方案**：fork `ChatQueryResult` + `ChatDashboardResult` 两个类型 + 泛型化循环返回 `<T>`。
拒绝理由：泛型化循环签名复杂化（`<T extends ChatResultBase>`），且 result 差异仅 2 字段，不值得双类型。

#### 裁定 L：循环泛化方案

**选择**：将 D6-1 查询专用 `ChatBiToolCallingLoop` 泛化为可复用的 tool-calling 循环。**4 个泛化点**：

1. **system prompt**：从硬编码 `ChatBiSystemPrompt.buildSystemPrompt()` 改为 `run()` 入参。
   查询路径传查询 prompt，生成路径传生成 prompt。
2. **结果提取**：从硬编码 query-columns/rows 解析改为**可插拔 `ToolResultHandler` 回调**。
   查询路径注入 query handler（解析 columns/rows），生成路径注入 generate handler（解析 dashboardId）。
   handler 接收 `(toolName, toolResult, content, chatBiResult)`，按需更新 result。
3. **返回类型**：泛化 `ChatBiResult`（裁定 K），两路径共享。
4. **context 构建**：`run()` 接受 `operator` 入参，构建携带 operator 的 `ChatBiToolExecuteContext`（裁定 G）。

**回归保护**：查询路径（`chatToQuery`）迁移到泛化循环后，既有 D6-1 测试必须全绿（行为不变）。具体：
`chatToQuery` 调 `loop.run(question, maxIterations, ChatBiSystemPrompt.SYSTEM_PROMPT, null, queryHandler)`，
其中 `queryHandler` 复刻原硬编码的 query-columns/rows 解析逻辑。

**被拒方案**：fork 两个循环（`ChatBiQueryLoop` + `ChatBiDashboardLoop`）。拒绝理由：循环骨架
（listTools → call → toolCalls 循环 → callTool → 回喂 → 终止判定）完全相同，fork 会重复 ~60 行代码，
违反 DRY。泛化只增加 3 个参数（prompt + operator + handler），复杂度可控。

#### 裁定 M：看板可生成组件类型边界

**选择**：看板生成只接受 **8 类**（`PanelTypeMapping` 覆盖的：chart/table/stat-tile/text/container/
pivot-table/map/iframe）。**6 类装饰/媒体组件**（decorative-border/scroll-text/time-clock/video/
stream/carousel-tab）无 panelType int 映射（screen-design §10.5，大屏专用），看板生成拒绝。

**两类错误码区分**（让 LLM 能区分）：
- 完全未知类型（不在 14 类注册表）→ `ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT`
- 注册表内但属装饰类型（无 panelType int 映射）→ `ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT`

**needsDataset 边界**（决定是否要 datasetSid）：
- needsDataset=true（5 类）：chart / table / stat-tile / map / pivot-table —— datasetSid 必填。
- needsDataset=false（3 类）：text / iframe / container —— 不传 datasetSid，不建 DatasetRef。

#### 裁定 N：dsMeta 解析共享 helper

**选择**：从 `DatavDescribeDatasetExecutor` 提取共享 `DatasetMetaParser.parseFieldNames(String dsMeta): Set<String>`，
声明字段名 key 约定。generate-dashboard executor 与 describe executor 共用。

**字段名 key 约定**（dsMeta 三种形式的字段名提取规则）：
1. `{"fields": [{"name": "region", ...}, ...]}` → 取 `fields[].name`。
2. `{"columns": [{"name": ...}, ...]}` → 取 `columns[].name`（兼容形式）。
3. `[{...}]`（数组根）→ 取每个元素（若有 `name`）。

仅当字段对象含 `name` key 时纳入字段名集合；不含 `name` 的字段对象跳过（非报错）。dsMeta 解析失败返回空集合。

#### 裁定 O：多表创建事务机制

**选择**：executor 注入 `IOrmTemplate`，将 Dashboard + DatasetRef + Panel 创建包在 `runInSession` 事务内。
**校验全部在创建前先发生**（fail-fast），失败时不落任何行（满足"无半成品"Exit Criteria）。

**事务边界**：整个 `generate` 方法体在 `ormTemplate.runInSession(session -> { ... })` 内执行。校验阶段
（componentType / datasetSid / fieldMapping）在校验阶段完成且全部通过后，才进入创建阶段。校验阶段抛
`NopException` → 事务回滚（无行落盘）。创建阶段用 `dao.saveEntityDirectly(entity)` 逐行保存。

### 8.2 循环泛化架构

```
NopDatavChatBiBizModel.chatToQuery / chatToDashboard
   │
   │  query path:  loop.run(question, maxIters, QUERY_PROMPT,    null,    queryHandler)
   │  dash path:   loop.run(desc,    maxIters, DASHBOARD_PROMPT, operator, dashboardHandler)
   │
   ▼
ChatBiToolCallingLoop.run(prompt, userMsg, maxIters, operator, handler)
   │
   │  构建 ChatRequest(prompt + userMsg + tools) + ChatBiToolExecuteContext(operator, cancelToken)
   │
   │  循环（最多 maxIters 次）:
   │    IChatService.call → outputToolCalls()
   │      ├─ 空 → handler 收尾 → 返回 ChatBiResult(answer + 累加字段 + iterations)
   │      └─ 非空 → callTool → handler.handle(name, result, content, accumulator) → 回喂 → 继续
   ▼
ChatBiResult (answer + columns/rows[query] + createdEntityId[dash] + iterations)
```

**`ToolResultHandler` 契约**（函数式接口）：

```java
@FunctionalInterface
interface ToolResultHandler {
    void handle(String toolName, AiToolCallResult result, String content, ChatBiResult accumulator);
}
```

- **QueryHandler**：`if ("datav-query-dataset".equals(toolName) && success) { parse columns/rows → accumulator }`
- **DashboardHandler**：`if ("datav-generate-dashboard".equals(toolName) && success) { parse dashboardId → accumulator.createdEntityId }`

### 8.3 创作工具架构（datav-generate-dashboard）

```
LLM 产出结构化规格（JSON）:
{
  "dashboardName": "...",
  "description": "...",
  "panels": [
    { "title": "...", "componentType": "chart", "datasetSid": "ds-x", "fieldMapping": {...}, "sortOrder": 0 },
    { "title": "...", "componentType": "text" },   // needsDataset=false, 无 datasetSid
    ...
  ]
}
        │
        ▼
DatavGenerateDashboardExecutor.executeAsync(call, context)
   │
   │  1. 解析 AiToolCall.input JSON → 规格对象
   │  2. 校验（fail-fast，裁定 J/O）:
   │     a. componentType 属 8 类（裁定 M）
   │     b. datasetSid 存在 + status=1（needsDataset=true 时）
   │     c. fieldMapping 字段 ∈ DatasetMetaParser.parseFieldNames(dsMeta)（裁定 N）
   │  3. 去重 DatasetRef（裁定 I）: 同 dashboard + 同 refDatasetId → 一个 DatasetRef
   │  4. 事务性创建（裁定 O，ormTemplate.runInSession）:
   │     - Dashboard（DRAFT, createdBy=operator 手动填）
   │     - DatasetRef 集合（dashboardId FK, paramMapping={}, createdBy=operator）
   │     - Panel 集合（panelName=title, panelType=PanelTypeMapping, datasetRefId=去重后,
   │                    panelConfig.fieldMapping=规格.fieldMapping, createdBy=operator）
   │  5. 返回 dashboardId + 摘要 JSON
   ▼
dashboardId（写入 ChatBiResult.createdEntityId）
```

**输入 schema**（`datav-generate-dashboard.tool.xml` 的 schemaJson，嵌套 panels 数组）：

```json
{
  "type": "object",
  "properties": {
    "dashboardName": { "type": "string" },
    "description": { "type": "string" },
    "panels": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "title": { "type": "string" },
          "componentType": { "type": "string" },
          "datasetSid": { "type": "string" },
          "fieldMapping": { "type": "object" },
          "sortOrder": { "type": "integer" }
        },
        "required": ["title", "componentType"]
      }
    }
  },
  "required": ["dashboardName", "panels"]
}
```

### 8.4 被拒方案汇总（D6-1b 增补）

| 方案 | 拒绝理由 |
|-----|---------|
| fork `ChatBiQueryLoop` + `ChatBiDashboardLoop` 两循环 | 循环骨架完全相同，fork 重复 ~60 行（裁定 L） |
| fork `ChatQueryResult` + `ChatDashboardResult` 两 result + 泛型化循环 | 差异仅 2 字段，双类型 + 泛型签名复杂化（裁定 K） |
| executor 可变 `setOperator` 注入 | 单例 + 并发 tool-calling 下线程不安全（裁定 G） |
| 自动发布生成看板 | 人审节点不可省略，LLM 可能产出错误配置直接上线（裁定 H） |
| LLM 产出 SQL | 沿用 D6-1 安全约束，只能引用已有数据集 + 映射字段（裁定 J/E） |
| 生成看板含全局筛选/联动配置 | D2 paramConfig/linkageConfig 语义复杂，LLM 难可靠产出；列为 follow-up |

### 8.5 D6-1b 安全边界

- 生成工具只能引用**已有数据集**（`NopReportDataset` status=1）+ **映射已有字段**（dsMeta 内），
  不能凭空造数据集/字段。
- 生成的看板为 **DRAFT**，不自动发布（裁定 H）。
- 生成看板归属 operator（`createdBy`），RLS 保护。
- system prompt 含「禁止生成 SQL」「只用 8 类看板组件」「产出草稿不自动发布」约束。
- `chatToDashboard` 经 `@BizMutation @Auth`（写操作，镜像 `publishDashboard` 约定）。

---

## 9. D6-2 大屏生成（NL → 大屏配置生成）

> plans `ai-dev/plans/nop-datav/2026-08-10-1516-2-ai-screen-generation.md`。
> 复用 §8（D6-1b）建立的「创作型工具 + chatToXxx 编排 + operator 传递」模式，面向大屏（Screen/ScreenWidget 自由画布）。
> 设计依据：`screen-design.md` §7（widget 越界/重叠校验）+ §8（datasetRefId 逻辑引用语义）+ §10.5（componentType string 直存）。

### 9.1 关键裁定（Decision L–Q）

#### 裁定 L：自由画布定位校验规则（对齐 screen-design §7.1）

**选择**：`generate-screen` executor 对 widget 定位的校验分三级：

| 级别 | 条件 | 处置 | 理由 |
|------|------|------|------|
| mandatory throw | `x < 0` 或 `y < 0` 或 `w ≤ 0` 或 `h ≤ 0` | `ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION` | 负坐标/非正宽高是非法规格 |
| **mandatory throw** | `x + w > screenWidth` 或 `y + h > screenHeight` | `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS` | **对齐 screen-design §7.1 + `ScreenLayoutParser` 已实现行为**：生成的草稿经 `getScreenDraftLayout` 解析时，越界会抛 `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`。若生成阶段允许越界，生成 → 运行时消费链路断裂 |
| watch-only | widget 间重叠 | 不阻断（不校验） | §7.3 装饰层叠合法（如装饰边框套数据组件） |

> **纠正先前倾向**：越界不是 watch-only。越界必须 throw，否则生成的配置运行时不可消费（`getScreenDraftLayout`/`getScreenLayout` 都会抛异常）。重叠才是 watch-only。

#### 裁定 M：装饰/媒体组件的 datasetSid 处理

**选择**：
- `needsDataset=false` 的组件（text/decorative-border/scroll-text/time-clock/video/stream/carousel-tab/iframe/container）→ `datasetSid` 字段**忽略**（即便 LLM 传了也不存入 `ScreenWidget.datasetRefId`）。
- `needsDataset=true` 的组件（chart/pivot-table/stat-tile/map/table）→ `datasetSid` **必填**，缺失返回 `ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC`。

#### 裁定 N：大屏数据集引用方式（定论：直存 sid，不经 DatasetRef）

**选择**：生成大屏时 `ScreenWidget.datasetRefId` **直接存 nop-report 数据集 sid**，不创建 `NopDatavDatasetRef` 行，不涉及 ORM 变更。

**理由**：
- `NopDatavDatasetRef.dashboardId` 是 **mandatory FK → NopDatavDashboard**，无 `ownerType` 列，不能归属 Screen。
- screen-design §8 明确 `ScreenWidget.datasetRefId` 是**逻辑引用**（"可指向任一 DatasetRef 行或外部 nop-report 数据集标识"）。
- 大屏 widget 本就按逻辑 sid 引用数据集，不需要 DatasetRef 中间层去重/paramMapping。

**被拒方案**：新增 `ownerType` 列到 `NopDatavDatasetRef`（区分 dashboard/screen 归属）。属 plan-first ORM 变更，过重故拒。大屏逻辑引用 sid 即可满足。

**fieldMapping 存储位置**：存入 `ScreenWidget.widgetConfig` 的子键 `widgetConfig.fieldMapping`。

#### 裁定 O：组件发现工具方案

**选择**：`datav-list-component-types` 为独立 `IToolExecutor`（直接读 `PanelComponentRegistry`），不经 BizModel 调用链，保持工具自包含。
输出 14 类组件清单（type/displayName/needsDataset/configAreas[]）。

#### 裁定 P：displayName 处理

**选择**：`NopDatavScreen.displayName` 是 ORM mandatory。`generate-screen` 工具输入 `displayName`（可选）；executor 在 `displayName` 为空时**回退为 `screenName`**，确保 mandatory 约束满足。

#### 裁定 Q：screenName 唯一约束冲突处理

**选择**：`NopDatavScreen` 有 `UK_NOP_DATAV_SCREEN_NAME(screenName)`。executor 在 save 时捕获 UK 冲突并返回显式错误 `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME`（不静默吞异常），由 LLM 下一轮换名。

### 9.2 组件发现工具架构（datav-list-component-types）

```
LLM 调用 datav-list-component-types（无输入）
        │
        ▼
DatavListComponentTypesExecutor.executeAsync(call, context)
   │
   │  1. PanelComponentRegistry.getInstance().getComponents()
   │  2. 遍历 14 类组件 → 提取 type/displayName/needsDataset/configAreas[]
   │  3. 返回 JSON: { "components": [ {type, displayName, needsDataset, configAreas}, ... ] }
   ▼
LLM 获得组件清单，选择合适组件创作大屏
```

独立 executor（不经 BizModel），直接读注册表，工具自包含。

### 9.3 大屏创作工具架构（datav-generate-screen）

```
LLM 产出结构化规格（JSON）:
{
  "screenName": "...",
  "displayName": "...",         // 可选，缺省回退 screenName（裁定 P）
  "screenWidth": 1920,
  "screenHeight": 1080,
  "adaptorMode": 10,            // 可选，默认 10（FULL）
  "backgroundConfig": {...},     // 可选
  "widgets": [
    { "componentType": "chart", "datasetSid": "ds-x", "fieldMapping": {...},
      "x": 0, "y": 0, "w": 500, "h": 300, "z": 1 },
    { "componentType": "decorative-border",               // needsDataset=false，无 datasetSid（裁定 M）
      "x": 0, "y": 0, "w": 1920, "h": 100, "z": 0 },
    ...
  ]
}
        │
        ▼
DatavGenerateScreenExecutor.executeAsync(call, context)
   │
   │  1. 解析 AiToolCall.input JSON → 规格对象
   │  2. displayName 回退（裁定 P）
   │  3. 校验（fail-fast，裁定 L/M/N/O）:
   │     a. componentType 经 PanelComponentRegistry.requireComponent（全部 14 类可用）
   │     b. widget 定位（裁定 L）: x/y 非负 + w/h > 0 → INVALID_POSITION；越界 → OUT_OF_BOUNDS
   │     c. datasetSid（裁定 M）: needsDataset=true 必填 + 存在 + status=1；needsDataset=false 忽略
   │     d. fieldMapping 字段 ∈ DatasetMetaParser.parseFieldNames(dsMeta)
   │     e. backgroundConfig（若提供）经 ScreenThemeParser.resolve 可解析
   │  4. 事务性创建（裁定 O 事务机制）:
   │     - Screen（DRAFT, createdBy=operator, displayName 已回退）
   │     - ScreenWidget 集合（componentType string 直存 / datasetRefId 直存 nop-report sid 不经 DatasetRef /
   │       x/y/w/h/z / widgetConfig.fieldMapping）
   │     - screenName UK 冲突 → DUPLICATE_SCREEN_NAME（裁定 Q）
   │  5. 返回 screenId + 摘要 JSON
   ▼
screenId（写入 ChatBiResult.createdEntityId）
```

**输入 schema**（`datav-generate-screen.tool.xml` 的 schemaJson，嵌套 widgets 数组）：

```json
{
  "type": "object",
  "properties": {
    "screenName": { "type": "string" },
    "displayName": { "type": "string" },
    "screenWidth": { "type": "integer" },
    "screenHeight": { "type": "integer" },
    "adaptorMode": { "type": "integer" },
    "backgroundConfig": { "type": "object" },
    "widgets": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "componentType": { "type": "string" },
          "datasetSid": { "type": "string" },
          "fieldMapping": { "type": "object" },
          "x": { "type": "integer" }, "y": { "type": "integer" },
          "w": { "type": "integer" }, "h": { "type": "integer" },
          "z": { "type": "integer" }
        },
        "required": ["componentType", "x", "y", "w", "h"]
      }
    }
  },
  "required": ["screenName", "screenWidth", "screenHeight", "widgets"]
}
```

### 9.4 chatToScreen 编排

```
NopDatavChatBiBizModel.chatToScreen(description, IServiceContext context)
   │
   │  operator = NopDatavOperatorResolver.resolveOperator(context)
   │  loop.run(desc, SCREEN_SYSTEM_PROMPT, operator, maxIters, SCREEN_RESULT_HANDLER)
   ▼
ChatBiToolCallingLoop（复用 D6-1b 泛化循环）
   │  tools = [list-component-types, list-datasets, describe-dataset, query-dataset, generate-screen]
   │  LLM: list-component-types 了解组件 → list/describe/query 理解数据 → 设计画布布局 → generate-screen 创作
   ▼
ChatBiResult (answer + createdEntityId=screenId + iterations)
```

**ScreenHandler**：`if ("datav-generate-screen".equals(toolName) && success) { parse screenId → accumulator.createdEntityId }`

### 9.5 大屏生成 system prompt 约束

system prompt 含（可观测）：
- 大屏创作角色 + 工作流（list-component-types 了解组件 → list/describe/query 理解数据 → 设计画布布局 → generate-screen 创作）
- **禁止生成 SQL**（沿用 D6-1 安全约束）
- **产出草稿不自动发布**（沿用裁定 H，人审节点不可省略）
- **widget 不可越界**（裁定 L，x+w ≤ screenWidth, y+h ≤ screenHeight）

### 9.6 被拒方案汇总（D6-2 增补）

| 方案 | 拒绝理由 |
|-----|---------|
| 新增 `ownerType` 列到 `NopDatavDatasetRef`（区分 dashboard/screen 归属） | plan-first ORM 变更过重；大屏 datasetRefId 逻辑引用 sid 即可（裁定 N） |
| 越界为 watch-only（仅告警不阻断） | 生成的配置经 `getScreenDraftLayout` 解析会抛 `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`，运行时不可消费（裁定 L） |
| 大屏生成自动发布 | 沿用裁定 H，人审节点不可省略 |
| 自动布局算法（widget 自动排布/吸附） | 属 LLM 模型能力范畴；后端只校验非负 + w/h > 0 + 不越界（Non-Goal） |
| 媒体源可达性校验（video/stream src URL） | 网络副作用，非模型层职责（Non-Goal） |

### 9.7 D6-2 安全边界

- 生成工具只能引用**已有数据集**（`NopReportDataset` status=1）+ **映射已有字段**（dsMeta 内），不能凭空造数据集/字段。
- 生成的大屏为 **DRAFT**（publishStatus=0），不自动发布（裁定 H）。
- 生成大屏归属 operator（`createdBy`），RLS 保护。
- 大屏可用全部 14 类组件（含装饰类型，与看板不同——看板只能用 8 类）。
- `chatToScreen` 经 `@BizMutation @Auth`（写操作，镜像 `chatToDashboard` 约定）。
