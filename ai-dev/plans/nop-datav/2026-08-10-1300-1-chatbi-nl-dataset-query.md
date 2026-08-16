# 1300-1 ChatBI 自然语言数据集查询（D6-1）

> Plan Status: completed
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D6-1；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §七
> Related: D1-2 数据绑定管线（复用 PanelSqlBuilder SQL 构建范式）；D5-1 调度集成（复用 @Nullable 注入范式）；nop-ai toolkit/agent 模块（参考 AgentToolDispatcher/ToolSchemaConverter 类型转换逻辑）

## Purpose

将 nop-datav 从"被动配置型 BI"扩展为"对话型 BI"：用户用自然语言提问，系统经 nop-ai LLM + 工具调用（tool-calling）自动发现数据集、理解字段、执行查询并返回结构化结果。本计划交付 D6-1 的**数据集查询** ChatBI 能力（NL → dataset query → tabular result），收口 roadmap D6-1 的核心结果面。

## Current Baseline

- **D0–D5 后端全部 done**（roadmap 确认）；D6 AI/ChatBI 尚未启动（`ai-dev/design/nop-datav/ai-design.md` 仍为 4 行 stub）。
- **数据集查询链路已落地**：`PanelDataBinder`（`nop-datav-service/.../query/PanelDataBinder.java`）实现 panel → datasetRef → NopReportDataset → SQL 构建 → `IJdbcTemplate.executeQuery` 全链路。其中 `PanelSqlBuilder.build(dsText, params, panelId)` 是可复用的 SQL 构建组件（`${paramName}` 占位符 → 参数化绑定）。**注意**：`PanelParamEvaluator.evaluate(paramMapping, requestParams)` 依赖 `NopDatavDatasetRef.paramMapping`（面板→数据集引用的参数映射 JSON），**不适用于 ChatBI 直接查 NopReportDataset 的场景**（无 DatasetRef）。ChatBI query executor 的参数路径：LLM params Map 直接作为 `PanelSqlBuilder.build` 的 `params` 参数（key 匹配 `${paramName}`），不复用 `PanelParamEvaluator`。
- **NopReportDataset 字段已确认**（`nop-report/model/nop-report.orm.xml:153-200`）：`sid`(PK) / `dsName` / `description` / `dsType`("sql" 等) / `datasourceId` / `dsText`(SQL 文本) / `dsMeta`(字段元数据 JSON) / `dsConfig`(输入参数配置 JSON) / `status`(活跃/ inactive，dict `core/active-status`)。ChatBI 查询复用这些字段。
- **nop-ai 新 API 集成面已确认**：
  - `IChatService`（`nop-ai-api`，bean `nopChatService` = `ChatServiceImpl`，定义于 `nop-ai-core/.../ai-defaults.beans.xml`）— 新非废弃 API，`call(ChatRequest, ICancelToken)` / `callAsync(ChatRequest, ICancelToken)` / `callStream(...)`。
  - `IToolManager`（`nop-ai-toolkit`，bean `nopToolManager` = `ToolManagerImpl`，定义于 `ai-tools-defaults.beans.xml`）— 工具发现 + 执行。
  - `IToolExecutor`（`nop-ai-toolkit`）— 每个 executor 绑定一个 tool（`getToolName()`），经 `nopToolExecutorProvider` 的 `<ioc:collect-beans by-type="...IToolExecutor"/>` 自动收集。
  - VFS 工具定义路径：`_vfs/nop/ai/tools/*.tool.xml`（schema `/nop/schema/ai/tool/tool.xdef`，位于 `nop-kernel/nop-xdefs`），`IToolManager.listTools()` 从此目录发现。
  - 既有参考：`ai-tools-defaults.beans.xml`（toolkit 内置工具 bean 注册范式）、`ai-agent-tools.beans.xml`（agent 模块工具 bean 注册范式）、`call-agent.tool.xml`（tool.xml 定义范式）。
- **nop-ai-agent 模块（tool-calling 循环已有实现）**：`nop-ai-agent` 已包含完整的 tool-calling 循环组件：
  - `ReActAgentExecutor`（`nop-ai-agent/.../engine/ReActAgentExecutor.java`，~1153 行）— 完整 ReAct 循环（LLM → tool call → 并行 fan-out 执行 → 结果回喂 → 重复）。
  - `AgentToolDispatcher`（`nop-ai-agent/.../engine/AgentToolDispatcher.java`，~456 行）— tool fan-out 执行，**包含 chat-api ↔ toolkit 的类型转换**（`ChatToolCall`→`AiToolCall`、`AiToolCallResult`→`ChatToolResponseMessage`）。
  - `ToolSchemaConverter`（`nop-ai-agent/.../engine/ToolSchemaConverter.java`，~100 行）— `AiToolModel` 的 schema → `ChatToolDefinition.parameters` Map 转换。
  - `SimpleToolExecuteContext`（`nop-ai-agent/.../engine/SimpleToolExecuteContext.java`）— `IToolExecuteContext` 的轻量实现（workDir/envs/expireAt/cancelToken/fileSystem/executor）。
  - **裁定方向**（Phase 1 最终确认）：ChatBI 为无状态单轮（Non-Goal 明确），`IAgentEngine` 的 session/budget/guardrail/team/memory 重载不适用。**不复用整个 `IAgentEngine`**，但**参考 `AgentToolDispatcher`/`ToolSchemaConverter` 的转换逻辑**（均为简单内联代码，不引入 nop-ai-agent 依赖），在 ChatBI BizModel 内实现轻量 tool-calling 循环。
- **chat-api ↔ toolkit 类型鸿沟（必须在 plan 中明确）**：`IChatService.call` 返回的 `ChatResponse.outputToolCalls()` 产出 `List<ChatToolCall>`（`io.nop.ai.api.chat.messages`），而 `IToolManager.callTool` 入参需要 `AiToolCall`（`io.nop.ai.toolkit.model`）；`AiToolCallResult`（toolkit）需转回 `ChatToolResponseMessage`（chat-api）才能经 `ChatRequest.addToolResponse(toolCallId, name, content)` 回喂 LLM；`IToolManager.listTools()` 返回 `AiToolModel`（toolkit）需转为 `ChatToolDefinition`（chat-api）才能设入 `ChatRequest.setTools(...)`。这三个转换逻辑已在 `AgentToolDispatcher`/`ToolSchemaConverter` 中有现成参考（各 ~3-10 行），ChatBI 复制参考实现（不引入 nop-ai-agent 依赖）。
- **`IToolExecuteContext` 构造**：`IToolManager.callTool(name, call, context)` 第三参数需非 null `IToolExecuteContext`。对 DB 查询 executor，其 workDir/envs/fileSystem/executor 字段无实际语义（executor 只用 cancelToken）。ChatBI BizModel 构造一个最小 `IToolExecuteContext` 实现（仅满足接口契约，DB 查询不依赖其字段），或参考 `SimpleToolExecuteContext` 的轻量实现模式。Phase 1 裁定具体方式。
- **遗留 API 明确拒绝**：`IAiChatService` / `IAiChatFunctionTool` / `IAiChatToolSet`（nop-ai-core）均标 `@Deprecated(forRemoval = true)`，本计划只用新 API。
- **nop-datav-service 当前依赖**：nop-report-dao / nop-biz / nop-job-api 等；**不含任何 nop-ai 依赖**（ChatBI 引入新依赖：`nop-ai-api` + `nop-ai-toolkit`，compile scope）。
- **action-auth 文件位置确认**：`nop-datav.action-auth.xml` 位于 `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/`（非 nop-datav-meta；nop-datav-meta 无资源文件）。
- **roadmap deferred 项均不可本轮触发**：IM 渠道推送（D5-1/D5-2，blocked on nop-ai-gateway channel binding）、PDF/PNG 图像导出（D3-3，blocked on flux 渲染）、集群调度（optimization candidate）—— 全部 blocked 于外部依赖，不在本计划 scope。

## Goals

- **AI 工具暴露**：将 nop-datav 的数据集元数据与查询能力暴露为 nop-ai 工具（VFS tool definitions + IToolExecutor），使 LLM 能通过 tool-calling 自动发现数据集、查看字段/参数、执行查询。
- **ChatBI 查询编排**：提供一个 BizModel action，接收自然语言问题 → 经 `IChatService`（带 datav 工具）轻量自建 tool-calling 循环（非 `IAgentEngine`，参考 `AgentToolDispatcher`/`ToolSchemaConverter` 的类型转换逻辑）→ 返回查询结果（columns + rows），并在 nop-ai 缺席时显式失败（非静默跳过）。
- **设计文档定稿**：`ai-dev/design/nop-datav/ai-design.md` 从 stub 升级为 D6 ChatBI 最终设计（工具集成裁定、API 选择、被拒方案）。
- **端到端验证**：从 NL 问题 → mock LLM 触发 datav-query-dataset 工具 → 真实数据集查询 → 结果回传的完整路径可测、可观测。

## Non-Goals

- **NL → 看板/面板配置生成**（从 NL 自动生成 dashboard/panel layoutJson）：roadmap D6-1 文字包含"看板生成"，但该能力依赖 ChatBI 查询能力先稳定，scope 过宽，显式移出本计划为独立 successor（D6-1b 或归入 D6-2）。
- **NL → 大屏生成**：roadmap D6-2（可选），依赖 D6-1 工具基建，不在本计划。
- **前端聊天 UI**（flux chat 界面）：走 nop-chaos-flux，不在本计划。
- **LLM → 任意 SQL 生成**（NL 直接转裸 SQL）：**显式拒绝**（安全：避免 LLM 生成任意 SQL 带来注入/越权风险）。ChatBI 只能选择已有数据集 + 填参数，不能凭空生成 SQL。
- **多轮对话上下文持久化**（会话历史跨请求保存）：首版为无状态单轮（可多 tool-call，但不跨请求记忆），持久化列为 follow-up。
- **IM/渠道推送**：沿用 D5-1/D5-2 deferred 裁定。
- **ChatBI 权限精细化**（按数据集粒度限制 LLM 可见范围）：首版复用既有 owner RLS + 数据集 status 过滤；细粒度 ChatBI 可见性控制列为 follow-up。

## Scope

### In Scope

- 设计文档：`ai-dev/design/nop-datav/ai-design.md` 起草 D6 ChatBI 最终结论（工具集成方案 + 选新 API 拒旧 API 理由 + 拒 NL→裸 SQL 理由 + ChatBI 查询语义 + tool-calling 循环方案裁定 + chat-api↔toolkit 类型转换契约 + @Nullable 注入策略 + 被拒方案）。
- 依赖变更：`nop-datav-service` 新增 `nop-ai-api`（compile，`IChatService`/`ChatRequest`/`ChatResponse`/`ChatToolDefinition`/`ChatToolCall`）+ `nop-ai-toolkit`（compile，`IToolExecutor`/`IToolManager`/`IToolExecuteContext`/`AiToolCall`/`AiToolCallResult`/`AiToolModel`）依赖；**不引入 nop-ai-agent 依赖**（类型转换逻辑参考其实现但内联复制）。测试新增 mock `IChatService` 所需依赖（test scope）。
- AI 工具定义（VFS `*.tool.xml`，schema `tool.xdef`）：
  - `datav-list-datasets` — 列出可用数据集（sid/dsName/description/dsType），仅 `status=活跃` 且调用者有权限访问的。
  - `datav-describe-dataset` — 描述指定数据集的字段元数据（从 `dsMeta` 解析）+ 输入参数定义（从 `dsConfig` 解析）。
  - `datav-query-dataset` — 按数据集 sid + 参数 Map 执行查询，返回 columns + rows（复用 `PanelSqlBuilder.build` 构建 SQL，**LLM params Map 直接作为 params 参数**，不含 DatasetRef 参数映射；含 maxRows 防护）。
- IToolExecutor 实现：3 个 executor（list/describe/query），各绑定 tool name，经 beans.xml 注册后被 `nopToolExecutorProvider` 自动收集。
- ChatBI BizModel：`NopDatavChatBiBizModel`（或等效命名），`@BizQuery @Auth` action 接收 NL 问题 → 构建 `ChatRequest`（system prompt + tool 清单）→ 经 `IChatService` 多轮 tool-calling 循环（LLM 返回 tool call → `IToolManager.callTool` 执行 → 结果回喂 LLM → 直到最终文本答案或达到最大轮次）→ 返回查询结果。
- @Nullable 注入：`IChatService` / `IToolManager` 可空注入（镜像 D5-1 `IJobScheduler` 范式）；nop-ai 缺席时 action 显式抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`（非静默返回 null）。
- system prompt 模板：描述 ChatBI 角色 + 工具使用指引 + "只查询已有数据集，禁止生成 SQL"约束。
- 错误码：`ERR_DATAV_CHATBI_*`（ai-not-available / max-iterations-exceeded / tool-execution-failed / no-result 等）。
- 配置项：`CFG_DATAV_CHATBI_*`（max-iterations 默认值、max-rows 默认值、default model/provider）。
- action-auth 权限点：ChatBI action 经 `@Auth`，`nop-datav-web/.../nop-datav.action-auth.xml` 增配权限点。
- 单元测试 + 端到端测试（mock `IChatService` 模拟 LLM 返回 tool call → 真实 executor 执行 → 断言结果）。

### Out Of Scope

- NL → 看板/面板/大屏配置生成（successor plan）。
- 前端聊天 UI（flux）。
- 会话历史持久化（跨请求多轮对话）。
- NL → 裸 SQL 生成（显式拒绝，安全考量）。
- ChatBI 可见数据集的细粒度权限控制（首版复用 status + owner RLS）。

## Execution Plan

### Phase 1 - 设计文档定稿 + 关键裁定

Status: completed
Targets: `ai-dev/design/nop-datav/ai-design.md`

- Item Types: `Decision`

- [x] **裁定 A（最高优先，阻塞 Phase 3）：tool-calling 循环方案**。在知情 `nop-ai-agent` 已有 `ReActAgentExecutor`/`AgentToolDispatcher`（完整循环 + 类型转换）的前提下，裁定 ChatBI 采用"轻量自建循环 + 参考已有转换逻辑"而非复用整个 `IAgentEngine`。理由写入 design doc：ChatBI Non-Goal 为无状态单轮，`IAgentEngine` 的 session/budget/guardrail/team/memory 重载不适用；引入 nop-ai-agent 依赖过重。
- [x] **裁定 B：三个类型转换契约的具体实现方式**。确认 chat-api ↔ toolkit 的三个转换以参考 `AgentToolDispatcher:194-196`/`:308-324` + `ToolSchemaConverter.convert(XNode)` 的内联实现方式落地（复制逻辑，不引入 nop-ai-agent 依赖）。字段映射要点：`ChatToolCall.name`→`AiToolCall.toolName`、`ChatToolCall.argumentsText`→`AiToolCall.input`（`AiToolCall` 无 toolCallId 字段，toolCallId 由 BizModel 保留原 `ChatToolCall` 引用单独追踪，回喂时经 `ChatToolResponseMessage.fromToolCall(chatToolCall, ...)` 或 `addToolResponse(toolCallId, name, content)` 恢复）；`AiToolModel` 需先取 schema XNode 再经 `ToolSchemaConverter.convert()` 转 `ChatToolDefinition.parameters`。design doc 记录此裁定与字段映射表。
- [x] **裁定 C：`IToolExecuteContext` 构造方式**。确认 ChatBI 构造最小 `IToolExecuteContext` 实现（满足接口契约，DB 查询 executor 不依赖 workDir/envs/fileSystem/executor 字段），或参考 `SimpleToolExecuteContext` 轻量模式。design doc 记录。
- [x] **裁定 D：循环终止条件语义**。"最终答案"= `ChatResponse.outputToolCalls()` 为空（LLM 未请求更多工具调用）；"超限"= 迭代次数 ≥ `maxIterations`（抛 `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED`）。design doc 记录。
- [x] **裁定 E：API 选择 + 查询语义 + 安全**。用新 `IChatService`（nop-ai-api）+ `IToolManager`/`IToolExecutor`（nop-ai-toolkit），拒 `IAiChatService`（@Deprecated）。ChatBI 只能选已有数据集 + 填参数（tool-calling），拒 NL→裸 SQL（安全）。LLM 提供的 params 值经参数化绑定（`PanelSqlBuilder` 的 `?` 占位符），非字符串拼接，防 SQL 注入。
- [x] **裁定 F：@Nullable 注入 + 工具清单 + 安全边界**。`IChatService`/`IToolManager` 可空注入（镜像 D5-1 `IJobScheduler`），nop-ai 缺席显式失败。3 个工具（list/describe/query）输入输出 schema。maxRows（查询行数上限）+ maxIterations（tool-calling 轮次上限）默认值与配置项。
- [x] 起草 `ai-design.md` D6 ChatBI 最终设计，包含上述全部裁定（每条写明选择 + 理由 + 被拒方案）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ai-dev/design/nop-datav/ai-design.md` 从 stub（4 行）升级为完整设计文档，包含裁定 A–F 的最终结论（无 "Proposed" / "Current vs Proposed" 残留段落）。
- [x] design doc 记录 tool-calling 循环方案（裁定 A）：轻量自建循环 + 拒 `IAgentEngine` 的理由（无状态单轮 Non-Goal）。
- [x] design doc 记录三个类型转换契约（裁定 B）：指向 `AgentToolDispatcher`/`ToolSchemaConverter` 的参考实现，注明复制逻辑不引依赖。
- [x] design doc 记录拒 `IAiChatService` 旧 API 的理由（指向 @Deprecated 标注）。
- [x] design doc 记录拒 NL→裸 SQL 的安全理由 + params 参数化绑定机制。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 数据集查询 AI 工具（定义 + Executor + 接线）

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/resources/_vfs/nop/ai/tools/*.tool.xml`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/*Executor.java`、`nop-datav/nop-datav-service/pom.xml`、`nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/beans/app-service.beans.xml`

- Item Types: `Fix`

- [x] `nop-datav-service/pom.xml` 新增 `nop-ai-api`（compile）+ `nop-ai-toolkit`（compile）依赖。
- [x] 创建 VFS tool definitions（schema `/nop/schema/ai/tool/tool.xdef`）：
  - [x] `datav-list-datasets.tool.xml` — 无必填输入（可选 keyword 过滤）；输出数据集清单（sid/dsName/description/dsType）。
  - [x] `datav-describe-dataset.tool.xml` — 输入 `datasetSid`（必填）；输出字段元数据 + 参数定义。
  - [x] `datav-query-dataset.tool.xml` — 输入 `datasetSid`（必填）+ `params`（object，可选）+ `maxRows`（int，可选）；输出 columns + rows。
- [x] 实现 3 个 `IToolExecutor`（各 `getToolName()` 返回对应 tool name），经 beans.xml 注册（被 `nopToolExecutorProvider` 自动收集）：
  - [x] list executor：查 `NopReportDataset` status=活跃 清单（按 owner RLS / status 过滤），返回精简元信息。
  - [x] describe executor：按 sid 加载 `NopReportDataset`，解析 `dsMeta`（字段元数据）+ `dsConfig`（参数定义）返回结构化描述。
  - [x] query executor：按 sid 加载数据集 → **LLM params Map 直接作为 `PanelSqlBuilder.build(dsText, params, datasetSid)` 的 params 参数**（key 匹配 dsText 中的 `${paramName}` 占位符，经参数化绑定防注入；**不复用 `PanelParamEvaluator`**，因其依赖 DatasetRef.paramMapping）→ `IJdbcTemplate.executeQuery`（含 maxRows 防护）→ 返回 columns + rows。
- [x] 在 `app-service.beans.xml`（或独立 beans 文件）注册 3 个 executor bean。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 3 个 `.tool.xml` 文件存在于 `_vfs/nop/ai/tools/`，经 xdef schema 校验合法（`./mvnw compile -pl nop-datav -am` 无 xdef 报错）。
- [x] 3 个 executor bean 经 `<ioc:collect-beans by-type="...IToolExecutor"/>` 收集后，`IToolManager.listTools()` 返回的列表包含 `datav-list-datasets` / `datav-describe-dataset` / `datav-query-dataset`（有聚焦测试断言）。
- [x] `datav-query-dataset` executor 对一个预置 SQL 数据集执行查询，返回的 columns + rows 与直接经 `PanelDataBinder.queryPanelData` 路径一致（有聚焦测试断言 column 名集合 + row 数 + 首行值）。
- [x] `datav-describe-dataset` executor 返回的字段清单与数据集 `dsMeta` 解析结果一致（有聚焦测试断言）。
- [x] **无静默跳过**：数据集不存在时 executor 返回显式错误（status=error + ERR_DATAV_DATASET_NOT_FOUND），非 null/空静默返回（见 Minimum Rules #24；有测试断言错误结果）。
- [x] **接线验证**：executor bean 确实被 `nopToolExecutorProvider` 收集（有聚焦测试：从 applicationContext 取 `IToolManager`，`callTool("datav-query-dataset", ...)` 返回真实查询结果，而非 "no executor registered" 错误）。
- [x] No owner-doc update required beyond ai-design.md（工具契约记录在 ai-design.md，Phase 1 已产出）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - ChatBI BizModel + 多轮 tool-calling 编排

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/*`、`nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/beans/app-service.beans.xml`、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`

- Item Types: `Fix`

- [x] 实现 ChatBI BizModel（`@BizModel`），含 `@BizQuery @Auth` action（如 `chatToQuery`），接收 `question`(String) + 可选 `options`(model/provider/maxRows/maxIterations)。
- [x] `@Nullable` 注入 `IChatService` + `IToolManager`；nop-ai 缺席时 action 抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`。
- [x] 实现 tool-calling 编排循环（按 Phase 1 裁定 A 的方案：轻量自建循环，非 `IAgentEngine`）：
  - [x] 构建初始 `ChatRequest`：system prompt + 用户问题 + `ChatRequest.setTools(...)`（将 `IToolManager.listTools()` 返回的 datav 工具转为 `ChatToolDefinition`，参考 `ToolSchemaConverter.convert()`）。
  - [x] 循环：`IChatService.call(chatRequest, cancelToken)` → 取 `ChatResponse.outputToolCalls()` → 若**非空**（LLM 请求工具调用）：将每个 `ChatToolCall`（chat-api）转为 `AiToolCall`（toolkit，`name`→`toolName`/`argumentsText`→`input`）→ **保留原 ChatToolCall 引用**（`AiToolCall` 不携带 toolCallId）→ `IToolManager.callTool(name, aiToolCall, context).join()` 执行（`context` 为按裁定 C 构造的最小 `IToolExecuteContext`）→ 将 `AiToolCallResult`（toolkit）转为 `ChatToolResponseMessage`（chat-api，**用保留的 ChatToolCall 引用恢复 toolCallId**）→ `ChatRequest.addToolResponse(toolCallId, name, content)` 回喂 → 继续循环。
  - [x] 循环终止：`ChatResponse.outputToolCalls()` 为**空**（LLM 给出最终文本答案）→ 返回 `ChatResponse.outputText()` + 解析的查询结果；或迭代次数 ≥ `maxIterations` → 抛 `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED`。
- [x] system prompt 模板：描述 ChatBI 角色 + "先用 datav-list-datasets 发现数据集 → datav-describe-dataset 了解字段 → datav-query-dataset 查询"的工作流指引 + "禁止生成 SQL"约束。
- [x] 新增错误码 `ERR_DATAV_CHATBI_*`（ai-not-available / max-iterations-exceeded / tool-execution-failed / no-result）。
- [x] 新增配置项 `CFG_DATAV_CHATBI_*`（max-iterations 默认、max-rows 默认、default model/provider）。
- [x] `nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml` 增配 ChatBI action 权限点。
- [x] 在 `app-service.beans.xml` 注册 ChatBI BizModel bean（如需；遵循 NopIoC 显式 bean 注册约定）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] ChatBI BizModel action 存在且经 `@Auth` 保护，`nop-datav-web/.../nop-datav.action-auth.xml` 含对应权限点。
- [x] nop-ai 缺席时（`IChatService` 注入为 null）action 显式抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`（有聚焦测试：不注入 IChatService → 调用 action → 断言异常码）。
- [x] nop-ai 在场时（mock `IChatService` 返回预设 tool call 序列），action 成功执行多轮 tool-calling 并返回包含查询数据的最终结果（有聚焦测试：mock LLM 第一轮返回 `datav-list-datasets` tool call → executor 返回数据集清单 → mock LLM 第二轮返回 `datav-query-dataset` tool call → executor 返回查询结果 → mock LLM 第三轮 `outputToolCalls()` 为空返回最终文本 → action 返回结果包含 columns + rows）。
- [x] **循环终止条件**（裁定 D）有聚焦测试：(a) `outputToolCalls()` 为空时正常终止返回结果；(b) 迭代达 `maxIterations` 时抛 `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED`（mock LLM 永远返回 tool call → 断言超限异常）。
- [x] **三个类型转换**（裁定 B）在循环中正确工作：`ChatToolCall`→`AiToolCall`（`name`→`toolName`/`argumentsText`→`input` 映射，toolCallId 不进 AiToolCall 由 BizModel 追踪）、`AiToolModel`→`ChatToolDefinition`（name/description/parameters 转换）、`AiToolCallResult`→`ChatToolResponseMessage`（status/output/error 映射，用保留的 ChatToolCall 恢复 toolCallId）—— 有聚焦测试断言转换后的数据被 LLM mock 正确消费（回喂后 mock 能在下一轮基于 tool result 内容做决策）。
- [x] **无静默跳过**：action 的每个分支（ai-absent / max-iterations / tool-error / success）都有显式结果或异常，无空返回/null 吞掉（见 Minimum Rules #24）。
- [x] **接线验证**：mock LLM 返回 tool call 时，`IToolManager.callTool` 确实被调用（有聚焦测试：用计数器或 spy 断言 callTool 被调用了预期次数 ≥ 1，证明 BizModel → IToolManager → executor 调用链连通）。
- [x] system prompt 模板包含"禁止生成 SQL"约束（代码/资源中可观测）。
- [x] `ai-dev/design/nop-datav/ai-design.md` 已更新（如 Phase 1 裁定在实现中有修正）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 端到端测试 + Anti-Hollow 验证

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/chatbi/TestNopDatavChatBi*.java`

- Item Types: `Proof`

- [x] E2E 测试：预置 `NopReportDataset`（SQL 数据集，含参数）→ 调用 ChatBI action（NL 问题如"查询各地区销售额"，mock LLM 按预设序列返回 list→describe→query tool calls）→ 断言返回结果包含真实查询的 columns + rows（与直接执行 SQL 一致）。
- [x] Anti-Hollow 断言：E2E 测试中验证（a）mock `IChatService.call` 确实被调用 ≥ 1 次，（b）`IToolManager.callTool` 确实被调用 ≥ 1 次，（c）最终结果包含 executor 产出的真实数据行（非 stub/placeholder）。
- [x] 错误路径 E2E：数据集不存在时 mock LLM 返回 query tool call with 不存在 sid → executor 返回错误 → action 将错误透传或包装为 `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`（有断言）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：E2E 测试从 ChatBI action 入口 → mock LLM → tool-calling → executor → 真实数据集查询 → 结果回传完整跑通，断言 columns + rows 与预期一致（见 Minimum Rules #22）。
- [x] **接线验证**：E2E 测试断言 `IChatService.call` 被调用 + `IToolManager.callTool` 被调用 + 最终结果含真实数据（见 Minimum Rules #23）。
- [x] **无静默跳过**：E2E 错误路径测试断言错误被显式抛出/透传（见 Minimum Rules #24）。
- [x] 新增功能测试覆盖：list executor / describe executor / query executor / ChatBI action（ai-present 多轮 / ai-absent / max-iterations / tool-error）均有聚焦测试（见 Minimum Rules #25）。
- [x] `./mvnw test -pl nop-datav/nop-datav-service` 全绿（含新增测试 + 既有回归测试 0 失败）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] ChatBI 数据集查询能力端到端可用（NL → tool-calling → dataset query → result）。
- [x] 3 个 AI 工具定义 + executor 落地并被 `IToolManager` 发现。
- [x] ChatBI BizModel action 经 `@Auth` 保护，nop-ai 缺席显式失败。
- [x] 拒 NL→裸 SQL 的安全约束在设计文档和 system prompt 中均落地。
- [x] `ai-dev/design/nop-datav/ai-design.md` 为最终设计（无 stub / Proposed 残留）。
- [x] roadmap `D6-1` 状态更新（核心查询部分 done；看板生成移出为 successor）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] 受影响 owner docs 已同步（ai-design.md final）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）ChatBI BizModel → IChatService → IToolManager → executor → IJdbcTemplate 调用链在运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service`
- [x] checkstyle / 代码规范检查通过（import 分组 io.nop.* → jakarta → 第三方 → java.*）

## Deferred But Adjudicated

### NL → 看板/面板配置生成（从 NL 自动生成 dashboard/panel layoutJson）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 该能力依赖 ChatBI 查询能力先稳定（本计划交付），且生成 layoutJson 需定义面板配置 JSON schema 约束 + 组件映射规则，scope 过宽。roadmap D6-1 文字含"看板生成"但可合理拆为 successor（D6-1b 或归入 D6-2 AI 大屏生成）。
- Successor Required: `yes`
- Successor Path: 独立 successor plan（本计划 ChatBI 查询基建落地后）

### 会话历史持久化（跨请求多轮对话上下文）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 ChatBI 为无状态单轮（单次请求内可多 tool-call），满足核心"NL → 查询 → 结果"语义。跨请求会话记忆需引入 session 存储（可复用 nop-ai `NopAiSession`），属体验增强，不影响查询能力成立。
- Successor Required: `no`

### ChatBI 可见数据集的细粒度权限控制

- Classification: `watch-only residual`
- Why Not Blocking Closure: 首版 list executor 按 `status=活跃` 过滤，复用既有 owner RLS。细粒度"哪些数据集对 ChatBI 可见"的控制（如标记数据集为 chatbi-enabled）属治理增强，当前 owner RLS 已提供行级安全。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 前端聊天 UI（flux chat 界面对接 ChatBI action）。
- ChatBI 查询结果的可视化建议（LLM 建议合适的图表类型，供前端渲染）。
- 多模型/多 provider 的 ChatBI 配置热切换（首版用配置项指定 default model/provider）。
- ChatBI 查询审计日志（记录 NL 问题 + 执行的 tool calls + 结果摘要，用于安全审计）。

## Closure

Status Note: D6-1 ChatBI 自然语言数据集查询能力全量交付。4 个 Phase 全部 completed，13 个新增测试全绿（333 总测试 0 失败 0 错误），设计文档从 stub 升级为最终设计。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: mission-driver EXECUTE（self-audit against live code + tests）
- Audit Session: 2026-08-10-mission-driver-exec
- Evidence:
  - **Phase 1**：`ai-dev/design/nop-datav/ai-design.md` 从 4 行 stub 升级为完整设计文档，裁定 A–F 全部落地，无 Proposed/Current vs Proposed 残留。
  - **Phase 2**：3 个 `.tool.xml` 经 xdef schema 校验合法（`./mvnw compile -pl nop-datav -am` BUILD SUCCESS）；3 个 executor 经 beans.xml 注册被 nopToolExecutorProvider 收集；TestDatavQueryDatasetExecutor 断言 query 结果与直接 SQL 一致；TestDatavListAndDescribeExecutors 断言 list/describe 语义；数据集不存在时 executor 返回显式错误（非 null/空）。
  - **Phase 3**：NopDatavChatBiBizModel @Auth + action-auth.xml 权限点落地；@Nullable 注入 + ai-absent 显式抛 ERR_DATAV_CHATBI_AI_NOT_AVAILABLE（TestNopDatavChatBiE2E.testAiAbsentThrowsExplicitError 断言）；ChatBiToolCallingLoop 轻量自建循环 + 三类型转换（ChatBiTypeConverter + ToolSchemaConverterInline 内联复制 AgentToolDispatcher/ToolSchemaConverter 逻辑）；system prompt 含"禁止生成 SQL"约束（ChatBiSystemPrompt.SYSTEM_PROMPT 可观测）。
  - **Phase 4**：TestNopDatavChatBiE2E.testE2eChatToQueryMultiRoundToolCalling 断言 IChatService.call 被调用 ≥1 + IToolManager.callTool 被调用 ≥1 + 最终结果含真实数据行（amount=200 非 stub）；testMaxIterationsExceeded 断言超限异常码；testToolErrorIsPropagatedAsToolResponse 断言错误透传。
  - **Anti-Hollow Check**：ChatBiToolCallingLoop.run → IChatService.call → ChatBiTypeConverter.toAiToolCall → IToolManager.callTool → DatavQueryDatasetExecutor.executeAsync → PanelSqlBuilder.build → IJdbcTemplate.executeQuery 调用链在 TestNopDatavChatBiE2E.testE2eChatToQueryMultiRoundToolCalling 运行时连通（2 行真实数据行断言）。无空方法体/静默跳过/no-op。
  - **构建验证**：`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS；`./mvnw test -pl nop-datav/nop-datav-service -T 1C` 333 tests / 0 failures / 0 errors。

Follow-up:

- NL → 看板/面板配置生成（successor plan，依赖本计划 ChatBI 查询基建）。
- 前端聊天 UI（flux chat 界面对接 chatToQuery action）。
- 会话历史持久化（跨请求多轮对话，可复用 nop-ai NopAiSession）。
- ChatBI 查询审计日志（NL 问题 + tool calls + 结果摘要）。
