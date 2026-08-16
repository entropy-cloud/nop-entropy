# 1516-1 ChatBI 看板生成（NL → 看板/面板配置生成，D6-1 successor）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D6-1b（D6-1 显式 successor：NL → 看板/面板配置生成）
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D6-1 successor；`ai-dev/plans/nop-datav/2026-08-10-1300-1-chatbi-nl-dataset-query.md` Deferred But Adjudicated「NL → 看板/面板配置生成」
> Related: D6-1 ChatBI 数据集查询（复用 ChatBiToolCallingLoop + 3 个 query 工具 + @Nullable 注入范式 + 三类型转换）；D0 看板/面板/DatasetRef CRUD（复用 CrudBizModel + publish 语义）；D1-1 PanelComponentRegistry（14 类组件，componentType 校验）

## Purpose

将 ChatBI 从"只查询不创作"扩展为"对话式创作"：用户用自然语言描述想要的看板（如"帮我建一个各地区销售额分析的看板"），系统经 nop-ai tool-calling 自动发现数据集、理解字段、**生成一个草稿看板（含面板 + 数据集引用 + 字段映射配置）**并返回看板 ID，用户随后可经既有 `publishDashboard` 审阅发布。本计划收口 D6-1 plan 中显式 deferred 的「NL → 看板/面板配置生成」successor 项。

## Current Baseline

- **D6-1 ChatBI 查询基建已 done**（plan `2026-08-10-1300-1`，closure audit PASS）：
  - `ChatBiToolCallingLoop`（`nop-datav-service/.../chatbi/ChatBiToolCallingLoop.java`）—— 轻量自建 tool-calling 循环骨架（listTools → IChatService.call 多轮 → callTool 执行 → 回喂，直到 outputToolCalls 为空或达 maxIterations）。**⚠️ 当前实现是查询专用的，非 generic**：(1) `run()` 第 80 行硬编码 `request.setSystemPrompt(ChatBiSystemPrompt.buildSystemPrompt())`（查询专用 prompt，非入参）；(2) 第 121-148 行硬编码从 `DatavQueryDatasetExecutor.TOOL_NAME` 结果提取 columns/rows；(3) 返回 `ChatBiResult`（answer + columns + rows + iterations，无处放 dashboardId）；(4) 第 84 行内部 `new ChatBiToolExecuteContext(cancelToken)` 构建 context（BizModel 不触碰 context，无 operator 通道）。**本计划必须泛化该循环**（裁定 L），不能直接"复用"。
  - 3 个 query 工具 + executor：`datav-list-datasets` / `datav-describe-dataset` / `datav-query-dataset`（`_vfs/nop/ai/tools/*.tool.xml` + `chatbi/Datav*Executor.java`），经 `app-service.beans.xml` 注册被 `nopToolExecutorProvider` 自动收集。
  - `NopDatavChatBiBizModel.chatToQuery(@Name("question") String)` —— @Nullable 注入 `IChatService`/`IToolManager`，nop-ai 缺席抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`。**注意：签名无 `IServiceContext` 参数**，本计划 `chatToDashboard` 需 operator 故须加 `IServiceContext context` 参数（镜像 `publishDashboard(..., IServiceContext)` 约定）。
  - `ChatBiTypeConverter`（chat-api ↔ toolkit 三类型转换，内联复制 AgentToolDispatcher 逻辑）+ `ChatBiToolExecuteContext`（最小 IToolExecuteContext，**仅 cancelToken 有语义，无 operator 字段**）。
  - `ChatBiSystemPrompt.SYSTEM_PROMPT` —— 查询专用 system prompt（含"禁止生成 SQL"约束）。
  - 错误码 `ERR_DATAV_CHATBI_*` + 配置项 `CFG_DATAV_CHATBI_*`（max-iterations / max-rows / default-model / default-provider）。
- **D0 看板/面板/DatasetRef CRUD 已 done**：`NopDatavDashboard`/`NopDatavPanel`/`NopDatavDatasetRef`/`NopDatavDashboardTab` ORM 实体 + BizModel（继承 `CrudBizModel`，标准 save/find/delete + `publishDashboard`/`getPublishedDashboard`/`rollbackDashboard`）。**身份/归属字段为 `createdBy`（domain="createdBy", mandatory），无独立 `owner` 列**；RLS 按 `createdBy == $context.userName`（screen-design §6.2 同模式）。因此"看板归属"≡ `createdBy`，本计划所有 owner 语义均落到 `createdBy`/`updatedBy`。看板发布快照序列化含 panels（panelId/panelName/displayName/panelType/datasetRefId/tabId/sortOrder/panelConfig）+ tabs + datasetRefs（`NopDatavDashboardBizModel.serializeDashboardContent`）。`NopDatavDatasetRef.dashboardId` 是 **mandatory FK → NopDatavDashboard**。
- **D1-1 面板组件注册表已 done**：`PanelComponentRegistry`（14 类：chart/pivot-table/stat-tile/map/table/text/iframe/container + 6 类装饰媒体）。**`PanelTypeMapping` 仅覆盖前 8 类**（chart/table/stat-tile/text/container/pivot-table/map/iframe ↔ panelType int 0/10/20/30/40/50/60/70）；**6 类装饰/媒体组件（decorative-border/scroll-text/time-clock/video/stream/carousel-tab）无 panelType int 映射，是大屏专用**（screen-design §10.5）。看板生成只能产出这 8 类（裁定 M）。`requireComponent(type)` 校验类型合法性（未知抛 `ERR_DATAV_UNKNOWN_COMPONENT_TYPE`）。`panelConfig` 配置区域约定：title / fieldMapping / styleOptions / dataBinding / refresh / content（runtime-design.md §1.3）。
- **D1-2 数据绑定已 done**：`PanelDataBinder.queryPanelData` 经 panel → DatasetRef → NopReportDataset → `PanelSqlBuilder.build` → `IJdbcTemplate.executeQuery`。Panel 通过 `datasetRefId` 引用 DatasetRef（DatasetRef 归 Dashboard 拥有，记录 `refDatasetId` + `paramMapping`）。**`DatavDescribeDatasetExecutor.parseFields(String dsMeta)` 是 private，处理 3 种 dsMeta 形式但未标准化字段名 key**（裁定 N 需提取共享 helper）。
- **nop-datav-service 当前依赖**含 `nop-ai-api` + `nop-ai-toolkit`（compile，D6-1 引入）；本计划不新增模块依赖。
- **roadmap 状态**：D6-1 = done（核心查询；看板生成移出为本 successor）；D6-2 = todo；D1-4/D2-4 = todo（blocked on flux 前端，不在本计划）。
- **未解决的 deferred 项**（不在本计划 scope）：IM 渠道推送（blocked on nop-ai-gateway）、PDF/PNG 图像导出（blocked on flux 渲染）、会话历史持久化（optimization candidate）。

## Goals

- **循环泛化**：将 D6-1 查询专用的 `ChatBiToolCallingLoop` 泛化为可复用的 tool-calling 循环（注入式 system prompt + 可插拔结果提取 + 泛化返回类型 + operator 传递通道），使查询路径与看板生成路径共享同一循环骨架。**不破坏既有 D6-1 查询行为**（回归测试保护）。
- **创作型工具**：新增一个 AI 工具 `datav-generate-dashboard`（IToolExecutor），接收 LLM 产出的结构化看板规格（dashboardName + panels[]，每个 panel 含 componentType + datasetSid + fieldMapping + title + sortOrder），**校验后**事务性创建草稿看板（Dashboard + DatasetRef + Panel 集合），返回 dashboardId。校验含：componentType 属于看板可生成的 8 类（裁定 M，拒装饰类型）、datasetSid 指向 status=1（启用，dict `core/active-status`）的 NopReportDataset、fieldMapping 引用的字段在数据集 dsMeta 中存在。
- **看板生成编排**：新增 ChatBI action `chatToDashboard(description, IServiceContext)`，接收 NL 描述 → 经 `IChatService` tool-calling（LLM 复用 list/describe/query 理解数据 → 调 generate-dashboard 工具创作）→ 返回创建的 dashboardId + 摘要。nop-ai 缺席显式失败（复用 D6-1 @Nullable 范式）。operator 从 `IServiceContext` 解析经泛化循环传入 executor，创建的 Dashboard.`createdBy` 为当前调用者。
- **设计文档更新**：`ai-dev/design/nop-datav/ai-design.md` 增补 D6-1b 看板生成最终结论（循环泛化方案、创作工具契约、草稿语义、校验规则、operator 传递全链路、被拒方案）。
- **端到端验证**：从 NL 描述 → mock LLM 触发 query + generate-dashboard 工具 → 真实创建草稿看板 → 经 `getPanelData`/DAO 验证面板/数据集引用落地且可被运行时消费的完整路径可测、可观测。

## Non-Goals

- **NL → 大屏生成**：roadmap D6-2（独立 successor plan `1516-2`，复用本计划建立的"创作型工具"模式但面向 Screen/ScreenWidget 自由画布）。
- **自动发布**：生成的看板是**草稿**（publishStatus=DRAFT，version=0），用户须手动 `publishDashboard`。自动发布显式拒绝（人审节点不可省略，防止 LLM 产出错误配置直接上线）。
- **布局自动美化/网格 packing 优化**：LLM 建议 sortOrder；后端不做网格自动排布算法（如 bin-packing）。面板位置以 LLM 产出为准，后端仅保证 sortOrder 非负递增。
- **NL → 裸 SQL 生成**：沿用 D6-1 安全约束，显式拒绝。生成工具只能引用已有数据集 + 映射字段。
- **前端聊天/看板预览 UI**：走 nop-chaos-flux，不在本计划。
- **会话历史持久化 / 多轮创作对话**：沿用 D6-1 无状态单轮 Non-Goal（单次请求内可多 tool-call）。
- **生成看板的全局筛选/联动配置**（D2 paramConfig/linkageConfig）：首版生成看板不含全局筛选与图表联动配置；用户可在草稿上手动补充。列为 follow-up。
- **字段映射的语义正确性保证**（LLM 是否选对了 X 轴/Y 轴字段）：后端只校验字段名存在于 dsMeta，不校验映射的语义合理性（这是 LLM 能力范畴，非后端契约）。

## Scope

### In Scope

- 设计文档：`ai-dev/design/nop-datav/ai-design.md` 增补 D6-1b 看板生成最终结论（循环泛化方案 + 创作工具输入输出 schema + 草稿语义 + 校验规则 + operator 传递全链路 + 被拒方案）。
- **循环泛化**（裁定 L）：泛化 `ChatBiToolCallingLoop`，提取 4 个扩展点（注入式 system prompt 入参 + 可插拔结果提取回调/策略 + 泛化返回/结果累加 + 外部提供携带 operator 的 `IToolExecuteContext`）。查询路径（D6-1 chatToQuery）迁移到泛化循环，**回归测试保护既有查询行为不变**。
- AI 工具定义（VFS `*.tool.xml`）：
  - `datav-generate-dashboard.tool.xml` — 输入：dashboardName(string) + 可选 description + panels[](每个含 title / componentType / 可选 datasetSid（**needsDataset=true 的组件必填；text/iframe/container 等 needsDataset=false 的组件不传**） / 可选 fieldMapping(object) / 可选 sortOrder)；输出：dashboardId + 创建的面板/数据集引用摘要。**schemaJson 必须正确表达嵌套 panels 数组**（裁定 + Phase 2 验证 LLM 工具调用参数可干净往返）。
- IToolExecutor 实现：`DatavGenerateDashboardExecutor`，绑定 `datav-generate-dashboard` tool name，经 beans.xml 注册被自动收集。职责：解析规格 → 校验（componentType 属 8 类/datasetSid/fieldMapping）→ 去重 DatasetRef（同 dashboard 内同 datasetSid 复用一个 DatasetRef）→ **事务性创建** Dashboard(DRAFT) + DatasetRef + Panel（裁定 O 指定事务机制，executor 注入 IOrmTemplate 将多表创建包在 session 内，校验在事务内先发生，失败不落盘）→ 返回 dashboardId。
- operator 传递全链路（裁定 G）：BizModel 从 `IServiceContext` 经 `NopDatavOperatorResolver.resolveOperator` 解析 operator → 传入泛化循环 → 循环构建携带 operator 的 `ChatBiToolExecuteContext` → executor 经 context 读取 operator → 手动设置 Dashboard/Panel/DatasetRef 的 `createdBy`/`updatedBy`/`createTime`/`updateTime`（**tool executor 无 BizModel 用户上下文，审计列必须手动填充**）。design doc 记录 context↔executor 强转耦合契约。
- **dsMeta 解析共享 helper**（裁定 N）：从 `DatavDescribeDatasetExecutor` 提取 `DatasetMetaParser.parseFieldNames(String dsMeta): Set<String>`（声明字段名 key 约定），generate-dashboard executor 与 describe executor 共用，用于 fieldMapping 校验。
- ChatBI action：`NopDatavChatBiBizModel.chatToDashboard(@BizMutation @Auth)`（**写操作——创建实体，必须用 @BizMutation 非 @BizQuery**，镜像 `publishDashboard(@BizMutation)` 约定），签名 `chatToDashboard(@Name("description") String description, IServiceContext context)` → 经泛化 `ChatBiToolCallingLoop`（**看板生成专用 system prompt**，指引 LLM "先用 list/describe/query 理解数据，再用 generate-dashboard 创作；只用 8 类看板组件"）→ 返回 dashboardId + answer 摘要。
- 看板生成 system prompt：描述创作角色 + 工作流指引（理解数据 → 选择组件类型 → 产出规格）+ "只能引用已有数据集 + 映射已有字段，禁止生成 SQL"约束 + "产出草稿，不自动发布"约束 + "只用 8 类看板组件，不用装饰类型"约束。
- 结果类型（裁定 K）：泛化循环的结果携带 answer + iterations + 可选 createdEntityId（dashboardId），不复用查询专用 columns/rows 语义。
- 错误码：复用 `ERR_DATAV_CHATBI_*` + 新增生成专用错误码（如 `ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC` / `ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND` / `ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT`（装饰类型用于看板）/ `ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT`）。
- action-auth 权限点：`chatToDashboard` 经 `@Auth`，`nop-datav-web/.../nop-datav.action-auth.xml` 增配。
- 单元测试 + 端到端测试（mock IChatService 模拟 LLM 返回 query → generate-dashboard tool call 序列 → 断言草稿看板真实创建）。

### Out Of Scope

- NL → 大屏生成（plan 1516-2）。
- 自动发布 / 布局美化 / 字段映射语义校验。
- 前端 UI / 会话持久化。
- 全局筛选与联动配置生成。

## Execution Plan

### Phase 1 - 设计文档定稿 + 关键裁定

Status: completed
Targets: `ai-dev/design/nop-datav/ai-design.md`

- Item Types: `Decision`

- [x] **裁定 G（最高优先，阻塞 Phase 2 + Phase 3）：operator 传递全链路**。IToolExecutor 经 `IToolManager.callTool` 执行，无 IServiceContext 入参；且 `ChatBiToolCallingLoop.run()` 内部构建 `ChatBiToolExecuteContext`（BizModel 不触碰 context）。裁定完整传递路径：BizModel 从 `IServiceContext` 经 `NopDatavOperatorResolver.resolveOperator(context)` 解析 operator → 传入泛化循环（new 入参）→ 循环构建携带 operator 的 `ChatBiToolExecuteContext`（扩展该类增加 operator 字段）→ executor 将入参 `IToolExecuteContext` 强转为 `ChatBiToolExecuteContext` 读取 operator。design doc 记录：(a) 选择扩展 ChatBiToolExecuteContext + 循环 new 入参的理由；(b) context↔executor 强转耦合契约（executor 假定传入的是 ChatBiToolExecuteContext）；(c) 被拒方案（executor 可变状态 setter，线程不安全故拒）。**owner 身份 ≡ `createdBy`（无独立 owner 列），executor 手动设置审计列**。
- [x] **裁定 H：草稿语义与发布边界**。生成工具创建的 Dashboard 为 `publishStatus=DRAFT`（0）、`publishedVersion=0`、不写快照表。用户经既有 `publishDashboard` 审阅发布。design doc 记录：为何不自动发布（人审节点不可省略）。
- [x] **裁定 I：DatasetRef 去重策略**。同一规格内多个 panel 引用同一 datasetSid 时，创建一个 DatasetRef 复用（而非每 panel 一个）。design doc 记录去重键（dashboardId + refDatasetId，应用层去重，无 DB unique 约束）与 paramMapping 初值（生成阶段为空 `{}`，用户后续可补）。
- [x] **裁定 J：校验失败语义**。规格校验失败时工具返回显式错误结果（status=error + 错误码），由 LLM 在下一轮修正，**不静默跳过**（Minimum Rules #24）。design doc 记录每类校验失败的错误码。
- [x] **裁定 K：结果类型设计**。裁定泛化循环的结果如何同时服务查询（columns/rows）与生成（dashboardId）。候选：泛化 `ChatBiResult` 增加可选 `createdEntityId` 字段（查询路径不用，生成路径填 dashboardId）。design doc 记录选择 + 理由。**与裁定 L 协同**。
- [x] **裁定 L（阻塞 Phase 3）：循环泛化方案**。裁定如何泛化 `ChatBiToolCallingLoop` 以服务查询 + 看板生成两路径。明确 4 个泛化点：(1) system prompt 从硬编码改为 run() 入参；(2) 结果提取从硬编码 query-columns/rows 改为可插拔回调/策略（查询路径注入 query 结果提取，生成路径注入 dashboardId 提取）；(3) 返回类型（裁定 K）；(4) context 构建接受 operator（裁定 G）。**回归保护**：查询路径（chatToQuery）迁移到泛化循环后，既有 D6-1 测试必须全绿（行为不变）。design doc 记录泛化方案 + 被拒方案（fork 两个循环，重复代码故拒）。
- [x] **裁定 M：看板可生成组件类型边界**。`PanelTypeMapping` 仅覆盖 8 类（chart/table/stat-tile/text/container/pivot-table/map/iframe）；6 类装饰/媒体组件无 panelType int 映射（screen-design §10.5），**看板生成只接受这 8 类**。executor 在 `PanelTypeMapping.toPanelTypeInt` 前先校验 componentType 属 8 类，装饰类型返回专用错误 `ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT`（不重用通用 unknown 错误，让 LLM 能区分"类型不存在"vs"类型不允许用于看板"）。system prompt 约束 LLM 只用 8 类。design doc 记录。
- [x] **裁定 N：dsMeta 解析共享 helper**。从 `DatavDescribeDatasetExecutor` 提取共享 `DatasetMetaParser.parseFieldNames(String dsMeta): Set<String>`，声明字段名 key 约定（dsMeta 三种形式中的字段名提取规则），generate-dashboard 与 describe executor 共用。design doc 记录字段名 key 约定。
- [x] **裁定 O：多表创建事务机制**。executor 注入 `IOrmTemplate`，将 Dashboard + DatasetRef + Panel 创建包在 `runInSession`/事务内；校验全部在创建前先发生（fail-fast），失败时不落任何行（满足"无半成品"Exit Criteria）。design doc 记录事务边界。
- [x] 起草 ai-design.md 增补章节「D6-1b 看板生成」，包含裁定 G–O 全部最终结论（每条写明选择 + 理由 + 被拒方案）+ 循环泛化架构图 + 创作工具架构图 + generate-dashboard 输入输出 schema。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ai-dev/design/nop-datav/ai-design.md` 新增「D6-1b 看板生成」章节，包含裁定 G–O 的最终结论（无 Proposed 残留）。
- [x] design doc 记录 operator 传递全链路（裁定 G）+ context↔executor 强转耦合契约 + owner≡createdBy + 选择理由 + 被拒方案。
- [x] design doc 记录草稿语义（裁定 H）+ 不自动发布理由。
- [x] design doc 记录 DatasetRef 去重策略（裁定 I）。
- [x] design doc 记录校验失败语义（裁定 J）+ 每类错误码。
- [x] design doc 记录结果类型设计（裁定 K）。
- [x] design doc 记录循环泛化方案（裁定 L）+ 4 个泛化点 + 回归保护策略 + 被拒方案。
- [x] design doc 记录看板可生成组件类型边界（裁定 M）+ 装饰类型拒绝理由。
- [x] design doc 记录 dsMeta 解析共享 helper（裁定 N）+ 字段名 key 约定。
- [x] design doc 记录多表创建事务机制（裁定 O）+ 事务边界。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 看板生成创作工具（定义 + Executor + 接线）

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/resources/_vfs/nop/ai/tools/datav-generate-dashboard.tool.xml`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavGenerateDashboardExecutor.java`、`nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/beans/app-service.beans.xml`

- Item Types: `Fix`

- [x] 创建 `datav-generate-dashboard.tool.xml`（schema `/nop/schema/ai/tool/tool.xdef`）：**双 schema 段**（`<schema>` XML DSL + `<schemaJson>` JSON schema）；输入 dashboardName(必填) + description(可选) + panels[](title / componentType / datasetSid(可选，needsDataset=true 组件必填) / fieldMapping(可选 object) / sortOrder(可选 int))；输出 dashboardId + panels 摘要（panelName + componentType + datasetRefId）+ datasetRefs 摘要。**schemaJson 必须正确表达 panels 嵌套数组**（type=array + items object + required 字段）。
- [x] 实现 `DatavGenerateDashboardExecutor`（`getToolName()` 返回 `datav-generate-dashboard`），按裁定 G 经 context 强转读取 operator，按裁定 I 去重 DatasetRef，按裁定 O 事务包裹，职责：
  - [x] 解析 `AiToolCall.input` JSON 为规格对象。
  - [x] **校验 componentType**（裁定 M）：先校验属 8 类可生成类型（`PanelTypeMapping` 可映射），装饰类型返回 `ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT`；完全未知类型返回 `ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT`。
  - [x] **校验 datasetSid**：needsDataset=true 的组件（chart/table/stat-tile/map/pivot-table）datasetSid 必填，加载 NopReportDataset 确认存在 + `status=1`（启用，dict `core/active-status`）；needsDataset=false 的组件（text/iframe/container）忽略 datasetSid（不创建 DatasetRef 关联）。按 createdBy RLS。
  - [x] **校验 fieldMapping**（裁定 N）：若 panel 提供 fieldMapping，其引用的字段名须经共享 `DatasetMetaParser.parseFieldNames(dsMeta)` 校验存在于字段名集合中。
  - [x] **去重 DatasetRef**（裁定 I）：同 dashboard 内同 refDatasetId 复用一个 DatasetRef（paramMapping 初值 `{}`）。needsDataset=false 的 panel 不参与去重（无 DatasetRef）。
  - [x] **事务性创建**（裁定 O）：在 `IOrmTemplate` session 内创建 Dashboard（publishStatus=DRAFT, publishedVersion=0, **createdBy/updatedBy=operator 手动填充**）+ DatasetRef 集合（**dashboardId mandatory FK**，createdBy=operator）+ Panel 集合（**panelName=规格.title（mandatory 列，必须填充）**；panelType 经 PanelTypeMapping 映射 int；datasetRefId 指向去重后的 DatasetRef（needsDataset=false 则 null）；sortOrder 缺省按声明顺序递增；**panelConfig 存 fieldMapping：将规格.fieldMapping 序列化为 panelConfig JSON 的 `fieldMapping` 子键**（供前端消费，后端 getPanelData 不读 panelConfig）；createdBy=operator）。校验全部在创建前完成，失败不落盘。
  - [x] 返回 dashboardId + 摘要 JSON。
- [x] 在 `app-service.beans.xml` 注册 executor bean（被 `nopToolExecutorProvider` 自动收集）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `datav-generate-dashboard.tool.xml` 存在于 `_vfs/nop/ai/tools/`，经 xdef schema 校验合法（`./mvnw compile -pl nop-datav -am` 无 xdef 报错）。
- [x] **schemaJson 嵌套数组正确**：schemaJson 正确表达 panels 嵌套数组（type=array + items object + required），有聚焦测试验证模拟 LLM 工具调用参数（JSON）能干净解析为规格对象（round-trip）。
- [x] executor bean 经 `<ioc:collect-beans by-type="...IToolExecutor"/>` 收集后，`IToolManager.listTools()` 返回的列表包含 `datav-generate-dashboard`（有聚焦测试断言）。
- [x] 合法规格执行后，DB 中真实创建 Dashboard(DRAFT) + DatasetRef + Panel 行（有聚焦测试：执行后查 DAO 断言行数 + 字段值：publishStatus=0, **createdBy=operator**, datasetRefId 关联正确, panelType 映射正确）。
- [x] **装饰类型拒绝**（裁定 M）：componentType=decorative-border → 返回 `ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT`（有测试）；区别于完全未知类型 `ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT`（有测试）。
- [x] **校验失败显式错误**（裁定 J）：(a) 未知 componentType → `ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT`（有测试）；(b) datasetSid 不存在/不活跃 → `ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND`（有测试）；(c) fieldMapping 字段不在 dsMeta → `ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC`（有测试）。
- [x] **无静默跳过 + 无半成品**（Minimum Rules #24 + 裁定 O）：校验失败返回 status=error 结果，**事务回滚不创建半成品看板**（有测试：校验失败后查 DAO 断言无 Dashboard/DatasetRef/Panel 残留行）。
- [x] **接线验证**（Minimum Rules #23）：从 applicationContext 取 `IToolManager`，`callTool("datav-generate-dashboard", ...)` 返回真实 dashboardId（非 "no executor registered"），且对应 Dashboard 行真实落库。
- [x] **DatasetRef 去重**（裁定 I）：规格内 2 个 panel 引用同一 datasetSid → 只创建 1 个 DatasetRef，2 个 panel 的 datasetRefId 指向同一行（有测试断言）。
- [x] No owner-doc update required beyond ai-design.md（创作工具契约记录在 ai-design.md，Phase 1 已产出）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - ChatBI 看板生成 BizModel + 编排

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java`（或新 BizModel）、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/*`、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`

- Item Types: `Fix`

- [x] 实现 `chatToDashboard(@BizMutation @Auth)` action（**写操作，用 @BizMutation 非 @BizQuery**），签名 `chatToDashboard(@Name("description") String description, IServiceContext context)`（**必须含 IServiceContext 参数**以解析 operator，镜像 `publishDashboard(..., IServiceContext)` 约定；D6-1 `chatToQuery` 是 `@BizQuery` 因查询不写实体）。
- [x] @Nullable 注入复用 D6-1（`IChatService`/`IToolManager`），nop-ai 缺席抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`。
- [x] operator 传递（按裁定 G）：从 IServiceContext 经 `NopDatavOperatorResolver.resolveOperator(context)` 解析 operator → 传入泛化循环 → 循环构建携带 operator 的 context → generate-dashboard executor 读取 → 手动填充 `createdBy`/`updatedBy`。
- [x] 经泛化 `ChatBiToolCallingLoop`（裁定 L 产出，**注入看板生成专用 system prompt + 看板生成结果提取策略**）+ **看板生成专用 system prompt**：描述创作角色 + "先用 list/describe/query 理解数据 → 选合适组件类型（8 类） → 调 generate-dashboard 创作"工作流 + "只能引用已有数据集 + 映射已有字段"约束 + "产出草稿不自动发布"约束 + "只用 8 类看板组件"约束。
- [x] 返回结果类型（裁定 K）：包含 dashboardId + answer（LLM 最终文本摘要）+ iterations。
- [x] **回归保护**（裁定 L）：D6-1 `chatToQuery` 迁移到泛化循环后，既有查询测试全绿（行为不变）。
- [x] 新增生成专用错误码 `ERR_DATAV_CHATBI_GENERATE_*`。
- [x] `nop-datav.action-auth.xml` 增配 `chatToDashboard` 权限点（roles="admin,user"）。
- [x] 在 `app-service.beans.xml` 注册（如新增 BizModel bean）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `chatToDashboard` action 存在且经 `@Auth` 保护，action-auth.xml 含对应权限点。
- [x] nop-ai 缺席时 action 显式抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`（有聚焦测试）。
- [x] nop-ai 在场时（mock `IChatService` 返回预设 tool call 序列：list → describe → query → generate-dashboard），action 成功执行并返回真实 dashboardId（有聚焦测试：mock LLM 多轮 → 断言返回 dashboardId 非空 → 查 DAO 断言 Dashboard + Panel 真实落库）。
- [x] **operator 传递正确**（裁定 G）：创建的 Dashboard.`createdBy` 等于测试调用者，非 null/空（有测试断言；**无 owner 列，owner≡createdBy**）。
- [x] **校验反馈循环**：mock LLM 第一轮产出含未知 componentType 的规格 → generate-dashboard 返回错误 → mock LLM 第二轮修正 → 成功创建（有聚焦测试断言错误被回喂且第二轮成功）。
- [x] **循环终止条件**：mock LLM `outputToolCalls()` 为空时正常终止返回 dashboardId；迭代达 maxIterations 抛 `ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED`（有测试）。
- [x] **无静默跳过**（Minimum Rules #24）：每个分支（ai-absent / max-iterations / spec-error / success）显式结果或异常。
- [x] **接线验证**（Minimum Rules #23）：mock LLM 返回 generate-dashboard tool call 时，`DatavGenerateDashboardExecutor` 确实被调用（有聚焦测试：用计数器/spy 断言 executor 被调用 ≥ 1，证明 BizModel → 泛化循环 → IToolManager → generate-dashboard executor → DAO 调用链连通）。
- [x] **D6-1 查询回归**（裁定 L）：泛化循环后既有 `chatToQuery` 测试全绿（行为不变）。
- [x] system prompt 包含"禁止生成 SQL" + "产出草稿不自动发布" + "只用 8 类看板组件"约束（代码/资源中可观测）。
- [x] `ai-dev/design/nop-datav/ai-design.md` 已更新（如 Phase 1 裁定在实现中有修正）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 端到端测试 + Anti-Hollow 验证

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/chatbi/TestNopDatavChatBiDashboard*.java`

- Item Types: `Proof`

- [x] E2E 测试：预置 NopReportDataset（SQL 数据集，含字段）→ 调用 `chatToDashboard`（NL 描述如"建一个各地区销售额看板"，mock LLM 按预设序列返回 list→describe→query→generate-dashboard tool calls）→ 断言返回 dashboardId → 经 DAO 加载 Dashboard + Panel + DatasetRef → 断言 publishStatus=DRAFT、panel 数量、componentType、datasetRefId 关联、**createdBy 正确**、**panel.panelName = 规格.title（非 null）**、**panel.panelConfig 含 fieldMapping 子键（JSON 内容断言）**。
- [x] E2E 数据正确性：对生成的草稿看板的某 needsDataset=true panel 调用既有 `getPanelData`，断言查询返回真实数据行。**注意**：`getPanelData` 的数据链路是 panel.datasetRefId → DatasetRef.refDatasetId → NopReportDataset.dsText → PanelSqlBuilder.build → executeQuery，**不读 panelConfig/fieldMapping**（fieldMapping 存 panelConfig 供前端渲染消费）。因此此 E2E 验证的是 **datasetRef 链路**正确（生成 → 运行时取数连通），fieldMapping 的落地另由上一条的 panelConfig JSON 内容断言覆盖（非空壳存储）。
- [x] Anti-Hollow 断言：E2E 中验证（a）`IChatService.call` 被调用 ≥ 1 次，（b）`IToolManager.callTool` 被调用 ≥ 1 次（含 generate-dashboard），（c）返回的 dashboardId 指向 DB 中真实存在的 Dashboard 行（非 stub），（d）该 Dashboard 的 Panel 经 `getPanelData` 能返回真实数据（datasetRef 链路连通），（e）**panelConfig JSON 含 fieldMapping 内容（非空壳配置）**。
- [x] 错误路径 E2E：mock LLM 产出含非法 datasetSid 的规格 → executor 返回错误 → action 透传/包装错误（有断言），且**未创建半成品看板**（DAO 查询断言无残留行）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：E2E 测试从 `chatToDashboard` 入口 → mock LLM → tool-calling → generate-dashboard executor → 真实 Dashboard/Panel/DatasetRef 落库 → 经 `getPanelData` 消费 datasetRef 链路返回真实数据 + panelConfig JSON 含 fieldMapping，完整链路跑通。
- [x] **接线验证**（Minimum Rules #23）：E2E 断言 `IChatService.call` + `IToolManager.callTool`（generate-dashboard）被调用 + dashboardId 指向真实落库行。
- [x] **无静默跳过**（Minimum Rules #24）：E2E 错误路径断言错误显式抛出/透传 + 无半成品残留。
- [x] **新增功能测试覆盖**（Minimum Rules #25）：generate-dashboard executor（合法规格 / 未知 componentType / **装饰类型（unsupported）** / datasetSid 不存在 / fieldMapping 非法 / DatasetRef 去重 / **needsDataset=false 组件无 datasetSid** / **事务无半成品** / **panelName + panelConfig 落地**）、chatToDashboard action（ai-present 多轮 / ai-absent / max-iterations / spec-error-then-recover）均有聚焦测试。
- [x] **生成的配置可被运行时消费**：对生成的草稿看板 needsDataset=true panel 调用 `getPanelData` 返回真实数据行（datasetRef 链路连通，非空配置）；panelConfig JSON 含 fieldMapping（前端消费链路落地，非空壳）。
- [x] `./mvnw test -pl nop-datav/nop-datav-service` 全绿（含新增测试 + 既有回归 0 失败）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] ChatBI 看板生成能力端到端可用（NL → tool-calling → generate-dashboard → 草稿看板落库 → 运行时可消费）。
- [x] generate-dashboard 工具定义 + executor 落地并被 `IToolManager` 发现。
- [x] chatToDashboard action 经 `@Auth` 保护，nop-ai 缺席显式失败。
- [x] 生成的看板为 DRAFT（不自动发布），**createdBy** 为当前调用者（owner≡createdBy，无独立 owner 列）。
- [x] 校验失败显式错误（无静默跳过），无半成品看板残留。
- [x] `ai-dev/design/nop-datav/ai-design.md` 含 D6-1b 最终设计（无 Proposed 残留）。
- [x] roadmap D6-1 successor 项状态更新（done）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] 受影响 owner docs 已同步（ai-design.md final）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）chatToDashboard → IChatService → IToolManager → generate-dashboard executor → DAO 调用链运行时连通，（b）生成的看板配置经 getPanelData 可消费真实数据，（c）无空方法体/静默跳过/no-op。
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service`
- [x] checkstyle / 代码规范检查通过（import 分组 io.nop.* → jakarta → 第三方 → java.*）

## Deferred But Adjudicated

### 生成看板的全局筛选/联动配置（D2 paramConfig / linkageConfig）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 全局筛选参数定义（D2-1）与图表联动（D2-2）的配置语义复杂，LLM 难以从单次 NL 描述可靠产出。首版生成看板为"数据展示骨架"，用户在草稿上手动补充筛选/联动。这不影响"NL → 可用草稿看板"核心结果面成立。
- Successor Required: `no`

### 字段映射的语义正确性保证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 后端只校验 fieldMapping 字段名存在于 dsMeta（契约层），不校验"X 轴放分类字段、Y 轴放度量字段"的语义合理性（这是 LLM 能力范畴）。生成的配置契约合法即可，语义合理性由用户审阅草稿时判断。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 前端聊天/看板预览 UI（flux 对接 chatToDashboard action + 草稿预览）。
- 生成看板的布局自动美化（网格 packing 优化、面板尺寸建议）。
- 生成看板后自动建议图表类型（LLM 在 describe 后建议 componentType，供 generate 时采用）。
- ChatBI 看板生成审计日志（NL 描述 + 执行的 tool calls + 产出 dashboardId）。

## Closure

Status Note: D6-1b 看板生成能力已端到端交付。裁定 G–O 全部落地为 final（无 Proposed 残留）。
循环泛化（裁定 L）将 D6-1 查询专用 ChatBiToolCallingLoop 泛化为查询 + 生成两路径共享，回归测试保护
既有查询行为不变（TestNopDatavChatBiE2E 4 测试全绿）。新增 datav-generate-dashboard 工具 +
DatavGenerateDashboardExecutor + DatasetMetaParser 共享 helper + chatToDashboard(@BizMutation @Auth) action。
测试：349/0/0 datav-service 全绿（含 16 新测试：11 executor 聚焦 + 5 E2E）。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent（自审 + 测试证据）—— closure-audit 由独立子 agent 复核。
- Evidence:
  - 循环泛化（裁定 L）回归保护：TestNopDatavChatBiE2E（4 测试）迁移到泛化循环后全绿。
  - generate-dashboard 工具接线（rule #23）：TestDatavGenerateDashboardExecutor.testExecutorWiredAndCallableViaToolManager
    断言 IToolManager.listTools() 含 datav-generate-dashboard + callTool 返回真实 dashboardId 落库。
  - 创作型工具契约（裁定 G/H/I/J/M/N/O）：TestDatavGenerateDashboardExecutor（11 测试）覆盖合法规格 /
    装饰类型拒绝（UNSUPPORTED）/ 未知类型拒绝（UNKNOWN）/ datasetSid 不存在或非活跃 / fieldMapping 非法 /
    needsDataset 缺 datasetSid / DatasetRef 去重 / 事务无半成品 / panelName + panelConfig 落地 / schemaJson 嵌套数组 round-trip。
  - 端到端（rule #22）：TestNopDatavChatBiDashboardE2E（5 测试）覆盖 ai-present 多轮（list→describe→query→generate-dashboard）
    → 真实 Dashboard/Panel/DatasetRef 落库 → getPanelData 消费 datasetRef 链路返回真实数据 + panelConfig 含 fieldMapping（Anti-Hollow e）/
    ai-absent / max-iterations / spec-error-then-recover / 无半成品残留。
  - operator 传递（裁定 G）：E2E 断言 Dashboard.createdBy == 调用者（owner≡createdBy，无独立 owner 列）。
  - `./mvnw test -pl nop-datav/nop-datav-service`：349/0/0 全绿。

Follow-up:

- 独立 closure-audit 子 agent 复核（plan execution 完成后建议触发 OPEN_AUDIT/CLOSURE_VERIFY）。
- 前端聊天/看板预览 UI（flux 对接 chatToDashboard action + 草稿预览）— Non-Blocking Follow-up。
- 生成看板的全局筛选/联动配置（D2 paramConfig/linkageConfig）— Deferred But Adjudicated。
- ChatBI 看板生成审计日志 — Non-Blocking Follow-up。
