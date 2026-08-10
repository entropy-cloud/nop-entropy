# 1516-2 ChatBI 大屏生成（NL → 大屏配置生成，D6-2）

> Plan Status: active
> Mission: nop-datav
> Work Item: D6-2（AI 大屏生成：MCP Tool 暴露组件/配置 + NL → 大屏配置生成）
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D6-2；DataRoom ai-generation 参考
> Related: plan `1516-1`（NL → 看板生成，建立"创作型工具 + chatToXxx 编排 + operator 传递"模式，**本计划依赖其落地**）；D6-1 ChatBI 查询基建（复用 ChatBiToolCallingLoop + 3 个 query 工具 + @Nullable 范式）；D4 大屏（NopDatavScreen/ScreenWidget/ScreenSnapshot + 自由画布 + PanelComponentRegistry 14 类组件 + getComponentTypes API）

## Purpose

将 ChatBI 创作能力从看板扩展到大屏：用户用自然语言描述想要的大屏（如"帮我建一个经营 KPI 大屏，1920x1080，顶部 KPI 卡片，中间销售趋势图，底部地区分布地图"），系统经 nop-ai tool-calling 自动发现数据集、了解可用组件类型、**生成一个草稿大屏（含画布尺寸 + 适配模式 + widget 自由画布定位 + 数据集引用）**并返回大屏 ID，用户随后可经既有 `publishScreen` 审阅发布。本计划收口 roadmap D6-2。

## Current Baseline

- **plan 1516-1（NL → 看板生成）为本计划的前置依赖**：其建立的"创作型工具（generate-from-spec IToolExecutor）+ chatToXxx 编排 action + operator 传递全链路（裁定 G）+ 草稿语义 + 校验失败显式错误 + **循环泛化（裁定 L）**"模式，本计划面向大屏复用。若 1516-1 未完成，本计划无法执行（泛化循环、operator 传递机制、创作 system prompt 范式、result 类型设计均待 1516-1 定稿）。
- **D6-1 ChatBI 查询基建已 done**：`ChatBiToolCallingLoop`（**当前查询专用，1516-1 裁定 L 将其泛化**）+ 3 个 query 工具（list/describe/query dataset）+ `NopDatavChatBiBizModel` + @Nullable 注入 + 三类型转换。本计划复用 1516-1 产出的**泛化循环**与 query 工具。
- **D4 大屏全部 done**（D4-1~D4-4）：
  - `NopDatavScreen`（**displayName mandatory** / screenWidth/screenHeight mandatory / adaptorMode dict `datav/screen-adaptor`(0=HEIGHT_FIRST/10=FULL/20=KEEP) / backgroundConfig / publishStatus / thumbnail）+ `NopDatavScreenWidget`（screenId FK / **componentType string（直接存标识，不经 panelType int 映射）** / datasetRefId 逻辑引用 / x/y/w/h/z 自由画布定位 / widgetConfig）+ `NopDatavScreenSnapshot`（独立快照表）。
  - `NopDatavScreenBizModel`（继承 CrudBizModel + `publishScreen`/`getPublishedScreen`/`rollbackScreen`/`getScreenLayout`(**读已发布快照，草稿无快照时抛 ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND**)/`getScreenSnapshotHistory`/`getScreenLayoutByVersion`/`getScreenDraftLayout`(**读编辑态，草稿可用**)/`setScreenThumbnail`）。
  - `ScreenLayoutParser`（快照 JSON → `ScreenLayoutConfig`；**§7.1 运行时校验：越界(x+w>canvas.width / y+h>canvas.height)是 mandatory throw `ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS`，重叠仅告警不阻断**）。
  - `getComponentTypes` API（D4-2，`@BizQuery @Auth`，admin/user 可读，返回 14 类组件类型清单 + 配置区域描述符）—— 本计划将其能力暴露为 AI 工具，供 LLM 选择合适组件。
  - `PanelComponentRegistry`（14 类：chart/pivot-table/stat-tile/map/table/text/iframe/container + 6 类装饰媒体 decorative-border/scroll-text/time-clock/video/stream/carousel-tab）。**大屏 widget.componentType 是 string，直接存标识，不经 PanelTypeMapping int 映射**（screen-design §8/§10.5），故大屏可使用全部 14 类（含装饰类型，与看板不同）。
- **D4-3 主题已 done**：`ScreenThemeParser`（backgroundConfig → palette + background 结构化解析）；widget.widgetConfig.theme 命名引用解析。大屏生成可让 LLM 产出 backgroundConfig（含主题），后端经 ScreenThemeParser 解析校验。
- **大屏 datasetRefId 语义（screen-design §8）**：`ScreenWidget.datasetRefId` 是**逻辑引用（非 FK）**，"可指向任一 DatasetRef 行或外部 nop-report 数据集标识"。`NopDatavDatasetRef.dashboardId` 是 **mandatory FK → NopDatavDashboard，无 ownerType 列**，不能归属 Screen。因此**大屏生成不经 DatasetRef 中间表**：`ScreenWidget.datasetRefId` 直接存 nop-report 数据集 sid（裁定 N 定论，不需 ORM 变更）。
- **身份/归属字段**：`NopDatavScreen`/`NopDatavScreenWidget` 的归属 ≡ `createdBy`（mandatory，无独立 owner 列），与 Dashboard 同模式。
- **nop-datav-service 依赖**含 `nop-ai-api` + `nop-ai-toolkit`（D6-1 引入）；本计划不新增模块依赖。
- **roadmap 状态**：D6-2 = todo（可选）；D6-1 successor（plan 1516-1）= draft（前置）。

## Goals

- **组件发现工具**：新增 AI 工具 `datav-list-component-types`（IToolExecutor），将 `PanelComponentRegistry` 的 14 类组件 + 各自配置区域描述符 + 是否需要数据集绑定暴露给 LLM，使其能选择合适组件类型创作大屏（含装饰类型，大屏可用全部 14 类）。
- **大屏创作工具**：新增 AI 工具 `datav-generate-screen`（IToolExecutor），接收 LLM 产出的结构化大屏规格（screenName + displayName + screenWidth/screenHeight + adaptorMode + widgets[]，每个 widget 含 componentType + 可选 datasetSid + fieldMapping + x/y/w/h/z），**校验后**事务性创建草稿大屏（Screen + ScreenWidget 集合，**不经 DatasetRef 中间表，ScreenWidget.datasetRefId 直接存 nop-report 数据集 sid**），返回 screenId。校验含：componentType 已注册、需数据集的组件的 datasetSid 指向活跃 NopReportDataset、widget 定位非负 + w/h > 0 + **不越界（x+w ≤ screenWidth, y+h ≤ screenHeight，对齐 screen-design §7.1 mandatory throw）**、fieldMapping 字段在 dsMeta 存在、backgroundConfig 经 ScreenThemeParser 可解析。
- **大屏生成编排**：新增 ChatBI action `chatToScreen(description, IServiceContext)`，接收 NL 描述 → 经 `IChatService` tool-calling（LLM 复用 list-component-types 了解组件 + list/describe/query 理解数据 + generate-screen 创作）→ 返回 screenId + 摘要。nop-ai 缺席显式失败。复用 1516-1 泛化循环 + operator 传递机制。
- **设计文档更新**：`ai-dev/design/nop-datav/ai-design.md` 增补 D6-2 大屏生成最终结论（组件发现工具 + 创作工具契约 + 自由画布定位校验对齐 §7.1 + datasetRefId 直存 sid 裁定 + 草稿语义 + 被拒方案）。
- **端到端验证**：从 NL 描述 → mock LLM 触发 list-component-types + query + generate-screen 工具 → 真实创建草稿大屏 → 经 `getScreenDraftLayout`/DAO 验证 widget 自由画布定位落地且可被运行时解析的完整路径可测、可观测。

## Non-Goals

- **NL → 看板生成**：plan 1516-1（前置依赖，独立交付）。
- **自动发布**：生成的大屏是**草稿**（publishStatus=DRAFT），用户须手动 `publishScreen`。沿用 1516-1 裁定 H（人审节点不可省略）。
- **自由画布自动布局算法**（widget 自动排布、吸附对齐）：LLM 产出 x/y/w/h/z 定位；后端校验非负 + w/h > 0 + **不越界（mandatory，对齐 screen-design §7.1）**；**重叠不阻断**（仅告警，§7.3 装饰层叠合法）。不做自动布局优化。LLM 的定位能力属于其模型能力范畴。
- **NL → 裸 SQL 生成**：沿用 D6-1/1516-1 安全约束。
- **前端大屏预览/聊天 UI**：走 nop-chaos-flux。
- **会话历史持久化 / 多轮创作对话**：沿用无状态单轮 Non-Goal。
- **大屏主题/装饰组件的语义正确性保证**：后端只校验 backgroundConfig 经 ScreenThemeParser 可解析、装饰组件 componentType 已注册；"配色是否美观""装饰边框是否搭配"属 LLM 能力范畴，非后端契约。
- **媒体组件的媒体源合法性校验**（video/stream 的 src URL 是否可达）：后端不校验外部 URL 可达性（网络副作用，非模型层职责）。

## Scope

### In Scope

- 设计文档：`ai-dev/design/nop-datav/ai-design.md` 增补 D6-2 大屏生成最终结论。
- AI 工具定义（VFS `*.tool.xml`）：
  - `datav-list-component-types.tool.xml` — 无必填输入；输出 14 类组件清单（type/displayName/needsDataset/configAreas[]）。
  - `datav-generate-screen.tool.xml` — 输入：screenName + **displayName**（ORM mandatory；缺省时 executor 可回退 screenName）+ screenWidth + screenHeight + 可选 adaptorMode + 可选 backgroundConfig + widgets[](componentType + 可选 datasetSid + 可选 fieldMapping + x/y/w/h + 可选 z)；输出：screenId + 创建的 widget 摘要。**schemaJson 必须正确表达嵌套 widgets 数组**。
- IToolExecutor 实现：
  - `DatavListComponentTypesExecutor` — 从 PanelComponentRegistry 读取组件清单 + 配置区域描述符（复用 `getComponentTypes` 逻辑）。
  - `DatavGenerateScreenExecutor` — 解析规格 → 校验 → **事务性创建 Screen(DRAFT) + ScreenWidget（不经 DatasetRef；ScreenWidget.datasetRefId 直接存 nop-report 数据集 sid）** → 返回 screenId。
- operator 传递：复用 plan 1516-1 裁定 G 的全链路方案（BizModel → IServiceContext 解析 operator → 泛化循环携带 → executor 经 context 强转读取 → 手动填充 createdBy）。本计划不重新裁定。
- ChatBI action：`chatToScreen(@BizQuery @Auth)`，签名 `chatToScreen(@Name("description") String description, IServiceContext context)` → 复用 1516-1 产出的**泛化 `ChatBiToolCallingLoop`**（大屏生成专用 system prompt）→ 返回 screenId + answer + iterations。
- 大屏生成 system prompt：描述大屏创作角色 + "先用 list-component-types 了解组件 + list/describe/query 理解数据 → 设计画布布局 → 调 generate-screen 创作"工作流 + "只能引用已有数据集 + 映射已有字段"约束 + "产出草稿不自动发布"约束 + "widget 不可越界"约束。
- 错误码：复用 `ERR_DATAV_CHATBI_*` + `ERR_DATAV_CHATBI_GENERATE_*`（1516-1）+ 大屏专用（如 `ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION` / `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS`）。
- action-auth 权限点：`chatToScreen` 经 `@Auth`，action-auth.xml 增配。
- 单元测试 + 端到端测试。

### Out Of Scope

- NL → 看板生成（plan 1516-1）。
- 自动发布 / 自由画布自动布局算法 / 主题美化保证 / 媒体源可达性校验。
- 前端 UI / 会话持久化。

## Execution Plan

### Phase 1 - 设计文档定稿 + 关键裁定

Status: planned
Targets: `ai-dev/design/nop-datav/ai-design.md`

- Item Types: `Decision`

- [ ] **确认前置依赖**：确认 plan 1516-1 已 **completed**（不仅是 active——本计划 Phase 2/3 需复用 1516-1 的**代码产物**：泛化循环代码、operator 传递代码、共享 `DatasetMetaParser`（裁定 N）、事务机制（裁定 O）、泛化 result 类型（裁定 K））。若 1516-1 未 completed，本 Phase 暂不启动。
- [ ] **裁定 L：自由画布定位校验规则（对齐 screen-design §7.1）**。generate-screen executor 对 widget 定位的校验：(a) x/y 非负 + w/h > 0 → **mandatory throw**（`ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION`）；(b) **越界（x+w > screenWidth 或 y+h > screenHeight）→ mandatory throw**（`ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS`，对齐 screen-design §7.1 + ScreenLayoutParser 已实现行为，确保生成的草稿经 `getScreenDraftLayout` 解析不抛异常）；(c) widget 间重叠 → **watch-only 不阻断**（§7.3 装饰层叠合法，仅告警/不校验）。design doc 记录每项 + 理由（**纠正先前"越界为 watch-only"的倾向，越界必须 throw 否则生成的配置运行时不可消费**）。
- [ ] **裁定 M：装饰/媒体组件的 datasetSid 处理**。needsDataset=false 的组件（text/decorative-border/video/stream 等）的 datasetSid 字段：忽略（即便 LLM 传了也不存 datasetRefId）；needsDataset=true 的组件 datasetSid 必填。design doc 记录。
- [ ] **裁定 N：大屏数据集引用方式（定论：直存 sid，不经 DatasetRef）**。`NopDatavDatasetRef.dashboardId` 是 mandatory FK → NopDatavDashboard，无 ownerType 列，**不能归属 Screen**（候选 A 不可行）。screen-design §8 明确 ScreenWidget.datasetRefId 是逻辑引用"可指向外部 nop-report 数据集标识"。**定论：生成大屏时 ScreenWidget.datasetRefId 直接存 nop-report 数据集 sid，不创建 NopDatavDatasetRef 行，不涉及 ORM 变更。** design doc 记录选择 + 理由 + 被拒方案（新增 ownerType 列，属 plan-first ORM 变更，过重故拒）。fieldMapping 存入 ScreenWidget.widgetConfig 的子键（裁定 fieldMapping 存储位置：widgetConfig.fieldMapping）。
- [ ] **裁定 O：组件发现工具方案**。`datav-list-component-types` 为独立 IToolExecutor（直接读 PanelComponentRegistry），不经 BizModel 调用链，保持工具自包含。design doc 记录。
- [ ] **裁定 P：displayName 处理**。NopDatavScreen.displayName 是 ORM mandatory。generate-screen 工具输入 displayName（可选）；executor 在 displayName 为空时回退为 screenName，确保 mandatory 约束满足。design doc 记录。
- [ ] **裁定 Q：screenName 唯一约束冲突处理**。NopDatavScreen 有 UK_NOP_DATAV_SCREEN_NAME(screenName)。LLM 产出的 screenName 可能重名 → executor 在 save 时捕获 UK 冲突并返回显式错误 `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME`（不静默吞异常），由 LLM 下一轮换名。design doc 记录。
- [ ] 起草 ai-design.md 增补章节「D6-2 大屏生成」，包含裁定 L–Q 全部最终结论 + 组件发现 + 创作工具架构 + generate-screen 输入输出 schema。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 确认 plan 1516-1 已 **completed**（裁定 G/H/K/L/N/O 代码产物可复用）。
- [ ] `ai-dev/design/nop-datav/ai-design.md` 新增「D6-2 大屏生成」章节，含裁定 L–Q 最终结论（无 Proposed 残留）。
- [ ] design doc 记录自由画布定位校验规则（裁定 L）+ 越界 mandatory throw 对齐 §7.1 的理由 + 重叠 watch-only 理由。
- [ ] design doc 记录装饰/媒体组件 datasetSid 处理（裁定 M）。
- [ ] design doc 记录数据集引用方式（裁定 N）= 直存 sid 不经 DatasetRef + 不涉及 ORM 变更 + 被拒方案。
- [ ] design doc 记录组件发现工具方案（裁定 O）。
- [ ] design doc 记录 displayName 处理（裁定 P）。
- [ ] design doc 记录 screenName UK 冲突处理（裁定 Q）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 组件发现 + 大屏创作工具（定义 + Executor + 接线）

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/resources/_vfs/nop/ai/tools/datav-list-component-types.tool.xml`、`.../datav-generate-screen.tool.xml`、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/DatavListComponentTypesExecutor.java`、`.../DatavGenerateScreenExecutor.java`、`nop-datav/nop-datav-service/src/main/resources/_vfs/nop/datav/beans/app-service.beans.xml`

- Item Types: `Fix`

- [ ] 创建 `datav-list-component-types.tool.xml`（schema tool.xdef）：无必填输入；输出组件清单（type/displayName/needsDataset/configAreas[{name,description,required}]）。
- [ ] 创建 `datav-generate-screen.tool.xml`（**双 schema 段，schemaJson 表达嵌套 widgets 数组**）：输入 screenName(必填) + displayName(可选，缺省回退 screenName，裁定 P) + screenWidth(必填 int) + screenHeight(必填 int) + adaptorMode(可选 int, 默认 10) + backgroundConfig(可选 object) + widgets[](componentType 必填 + datasetSid 可选 + fieldMapping 可选 + x/y/w/h 必填 + z 可选)；输出 screenId + widgets 摘要。
- [ ] 实现 `DatavListComponentTypesExecutor`（按裁定 O，直接读 PanelComponentRegistry），返回 14 类组件 + 配置区域描述符。
- [ ] 实现 `DatavGenerateScreenExecutor`，按裁定 N（直存 sid 不经 DatasetRef），职责：
  - [ ] 解析规格 JSON。
  - [ ] **displayName 回退**（裁定 P）：displayName 为空时回退 screenName。
  - [ ] **校验 componentType**：经 `PanelComponentRegistry.requireComponent`（未知返回错误）。大屏可用全部 14 类（含装饰类型）。
  - [ ] **校验定位**（裁定 L）：x/y 非负 + w/h > 0 → 否则 `ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION`；**越界（x+w > screenWidth 或 y+h > screenHeight）→ `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS`**（mandatory throw 对齐 §7.1）；重叠不校验（watch-only）。
  - [ ] **校验 datasetSid**（裁定 M）：needsDataset=true 的组件 datasetSid 必填 + 存在 + 活跃；needsDataset=false 的组件忽略 datasetSid。
  - [ ] **校验 fieldMapping**：字段在 dsMeta 存在（复用 1516-1 共享 `DatasetMetaParser`）。
  - [ ] **校验 backgroundConfig**（若提供）：经 ScreenThemeParser 可解析（palette/background 结构合法）。
  - [ ] **事务性创建**（复用 1516-1 裁定 O 事务机制）：Screen(DRAFT, createdBy=operator, displayName 已回退) + ScreenWidget（componentType string 直存 / **datasetRefId 直存 nop-report sid（不经 DatasetRef）** / x/y/w/h/z / **widgetConfig 含 fieldMapping 子键**）。校验在创建前完成，失败不落盘。
  - [ ] **screenName UK 冲突处理**（裁定 Q）：save 捕获 UK 冲突 → `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME`（不静默吞）。
  - [ ] 返回 screenId + 摘要。
- [ ] 在 `app-service.beans.xml` 注册 2 个 executor bean。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 2 个 `.tool.xml` 存在于 `_vfs/nop/ai/tools/`，经 xdef schema 校验合法（`./mvnw compile -pl nop-datav -am` 无 xdef 报错）。
- [ ] **schemaJson 嵌套数组正确**：generate-screen 的 schemaJson 正确表达 widgets 嵌套数组（round-trip 测试）。
- [ ] 2 个 executor bean 经 collect-beans 收集后，`IToolManager.listTools()` 包含 `datav-list-component-types` + `datav-generate-screen`（有聚焦测试）。
- [ ] `datav-list-component-types` executor 返回 14 类组件（有聚焦测试：断言数量 + 含 chart/stat-tile/decorative-border 等代表 + needsDataset 标志正确）。
- [ ] 合法规格执行后，DB 真实创建 Screen(DRAFT) + ScreenWidget 行（**不创建 DatasetRef**）（有聚焦测试：执行后查 DAO 断言行数 + 字段值：publishStatus=0, **createdBy=operator**, displayName 已回退, widget x/y/w/h/z 落地正确, componentType string 正确, **datasetRefId = nop-report sid**）。
- [ ] **定位校验**（裁定 L）：x 为负 / w ≤ 0 → `ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION`（有测试）；**越界（x+w > screenWidth）→ `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS`**（有测试）。
- [ ] **装饰组件 datasetSid 处理**（裁定 M）：text/decorative-border 等组件传 datasetSid 时 widget.datasetRefId 为空（有测试断言）；chart 组件不传 datasetSid → 错误（有测试）。
- [ ] **校验失败显式错误 + 无半成品**（无静默跳过，Minimum Rules #24）：未知 componentType / datasetSid 不存在 / fieldMapping 非法 / backgroundConfig 不可解析 / **screenName UK 冲突** → 各返回对应错误码 + 事务回滚无残留（有测试）。
- [ ] **接线验证**（Minimum Rules #23）：从 applicationContext 取 IToolManager，`callTool("datav-generate-screen", ...)` 返回真实 screenId，对应 Screen 行真实落库。
- [ ] No owner-doc update required beyond ai-design.md。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - ChatBI 大屏生成 BizModel + 编排

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavChatBiBizModel.java`（或新 BizModel）、`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/chatbi/*`、`nop-datav/nop-datav-web/src/main/resources/_vfs/nop/datav/auth/nop-datav.action-auth.xml`

- Item Types: `Fix`

- [ ] 实现 `chatToScreen(@BizQuery @Auth)` action，签名 `chatToScreen(@Name("description") String description, IServiceContext context)`（含 IServiceContext 以解析 operator）。
- [ ] @Nullable 注入复用 D6-1，nop-ai 缺席抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`。
- [ ] operator 传递复用 plan 1516-1 裁定 G 全链路（BizModel → IServiceContext 解析 → 泛化循环携带 → executor 读取 → 手动填充 createdBy）。
- [ ] 复用 1516-1 产出的**泛化 `ChatBiToolCallingLoop`**（注入大屏生成专用 system prompt + 大屏结果提取策略）+ **大屏生成专用 system prompt**：大屏创作角色 + "list-component-types 了解组件 → list/describe/query 理解数据 → 设计画布布局（建议尺寸/位置，**不越界**）→ generate-screen 创作"工作流 + 安全/草稿/不越界约束。
- [ ] 返回结果类型（复用 1516-1 裁定 K 泛化 result，携带 screenId）。
- [ ] 新增大屏专用错误码（`ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION` / `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS` / `ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME`）。
- [ ] action-auth.xml 增配 `chatToScreen` 权限点（roles="admin,user"）。
- [ ] beans.xml 注册（如新增 BizModel）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `chatToScreen` action 存在且经 `@Auth` 保护，action-auth.xml 含对应权限点。
- [ ] nop-ai 缺席时 action 显式抛 `ERR_DATAV_CHATBI_AI_NOT_AVAILABLE`（有聚焦测试）。
- [ ] nop-ai 在场时（mock IChatService 返回预设序列：list-component-types → list → describe → query → generate-screen），action 成功返回真实 screenId（有聚焦测试：断言 screenId 非空 → 查 DAO 断言 Screen + ScreenWidget 真实落库 + widget 定位正确）。
- [ ] **operator 传递正确**：创建的 Screen.**createdBy** 等于测试调用者（有测试断言；owner≡createdBy）。
- [ ] **校验反馈循环**：mock LLM 第一轮产出含越界定位的规格 → generate-screen 返回 `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS` → 第二轮修正 → 成功（有测试）。
- [ ] **循环终止条件**：outputToolCalls 空时正常终止；达 maxIterations 抛超限异常（有测试）。
- [ ] **无静默跳过**（Minimum Rules #24）：每个分支显式结果或异常。
- [ ] **接线验证**（Minimum Rules #23）：mock LLM 返回 generate-screen tool call 时，`DatavGenerateScreenExecutor` 确实被调用（计数器/spy 断言 ≥ 1，证明调用链连通）。
- [ ] system prompt 含"禁止生成 SQL" + "产出草稿不自动发布" + "widget 不可越界"约束（可观测）。
- [ ] `ai-dev/design/nop-datav/ai-design.md` 已更新。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 端到端测试 + Anti-Hollow 验证

Status: planned
Targets: `nop-datav/nop-datav-service/src/test/java/io/nop/datav/service/chatbi/TestNopDatavChatBiScreen*.java`

- Item Types: `Proof`

- [ ] E2E 测试：预置 NopReportDataset → 调用 `chatToScreen`（NL 描述如"建一个经营 KPI 大屏"，mock LLM 按预设序列返回 list-component-types → list → describe → query → generate-screen tool calls）→ 断言返回 screenId → 经 DAO 加载 Screen + ScreenWidget → 断言 publishStatus=DRAFT、widget 数量、componentType、自由画布定位(x/y/w/h/z)、**createdBy 正确**、**widget.datasetRefId = nop-report sid（无 DatasetRef 行）**。
- [ ] E2E 装饰组件覆盖：规格中含 1 个 needsDataset=false 组件（如 decorative-border）+ 1 个 needsDataset=true 组件（如 chart）→ 断言装饰组件 datasetRefId 为空、chart 组件 datasetRefId = nop-report sid。
- [ ] E2E 数据正确性：对生成的草稿大屏经既有 **`getScreenDraftLayout`**（**非 getScreenLayout，草稿无快照**）加载 → 断言 ScreenLayoutParser 正确解析 widget（含 componentType + 定位）；**生成的定位不越界**（否则 getScreenDraftLayout 会抛 ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS）。
- [ ] Anti-Hollow 断言：E2E 中验证（a）`IChatService.call` 被调用 ≥ 1 次，（b）`IToolManager.callTool` 被调用 ≥ 1 次（含 generate-screen），（c）返回 screenId 指向 DB 真实 Screen 行，（d）经 **`getScreenDraftLayout`** 可解析生成的大屏配置。
- [ ] 错误路径 E2E：mock LLM 产出含越界定位规格 → executor 返回 `ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS` → action 透传/包装 → **未创建半成品大屏**（DAO 断言无残留行）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**（Minimum Rules #22）：E2E 从 `chatToScreen` 入口 → mock LLM → tool-calling → generate-screen executor → 真实 Screen/ScreenWidget 落库 → 经 **`getScreenDraftLayout`** 消费生成配置，完整链路跑通。
- [ ] **接线验证**（Minimum Rules #23）：E2E 断言 IChatService.call + IToolManager.callTool（generate-screen）被调用 + screenId 指向真实落库行。
- [ ] **无静默跳过**（Minimum Rules #24）：E2E 错误路径断言错误显式抛出 + 无半成品残留。
- [ ] **新增功能测试覆盖**（Minimum Rules #25）：list-component-types executor（数量/needsDataset）、generate-screen executor（合法规格 / 装饰+数据组件混合 / 未知 componentType / 非法定位 / **越界** / datasetSid 缺失 / backgroundConfig 非法 / **screenName UK 冲突** / **事务无半成品**）、chatToScreen action（ai-present 多轮 / ai-absent / max-iterations / spec-error-then-recover）均有聚焦测试。
- [ ] **生成的配置可被运行时消费**：对生成的草稿大屏调用 **`getScreenDraftLayout`** 返回可解析的 ScreenLayoutConfig（不抛越界异常，证明生成 → 运行时消费链路连通）。
- [ ] `./mvnw test -pl nop-datav/nop-datav-service` 全绿（含新增测试 + 既有回归 0 失败）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] ChatBI 大屏生成能力端到端可用（NL → tool-calling → generate-screen → 草稿大屏落库 → 运行时可消费）。
- [ ] 组件发现 + 大屏创作工具落地并被 IToolManager 发现。
- [ ] chatToScreen action 经 @Auth 保护，nop-ai 缺席显式失败。
- [ ] 生成的大屏为 DRAFT（不自动发布），**createdBy** 为当前调用者（owner≡createdBy）。
- [ ] 校验失败显式错误（无静默跳过），无半成品大屏残留。
- [ ] `ai-dev/design/nop-datav/ai-design.md` 含 D6-2 最终设计。
- [ ] roadmap D6-2 状态更新（done）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 受影响 owner docs 已同步。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）chatToScreen → IChatService → IToolManager → generate-screen executor → DAO 调用链运行时连通，（b）生成的大屏配置经 **getScreenDraftLayout** 可解析消费，（c）无空方法体/静默跳过/no-op。
- [ ] `./mvnw compile -pl nop-datav -am`
- [ ] `./mvnw test -pl nop-datav/nop-datav-service`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 自由画布 widget 重叠检测

- Classification: `watch-only residual`
- Why Not Blocking Closure: 大屏允许刻意重叠（如装饰边框套数据组件），screen-design §7.3 已裁定重叠仅告警不阻断。后端校验越界（mandatory throw，对齐 §7.1），但重叠不校验（watch-only），由用户审阅草稿时判断。**注意：越界不是 watch-only——越界是 mandatory throw，否则生成的配置经 getScreenDraftLayout 解析会抛异常。**
- Successor Required: `no`

### 大屏主题/装饰组件的语义正确性保证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 后端只校验 backgroundConfig 可解析 + componentType 已注册；"配色美观""装饰搭配"属 LLM 能力范畴。生成的配置契约合法即可。
- Successor Required: `no`

### 媒体源可达性校验（video/stream src URL）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 校验外部 URL 可达性是网络副作用，非模型层职责。媒体源可达性由用户审阅/运行时前端暴露。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 前端大屏预览/聊天 UI（flux 对接 chatToScreen + 草稿大屏预览）。
- 自由画布自动布局算法（widget 自动排布/吸附对齐建议）。
- 大屏生成审计日志（NL 描述 + tool calls + 产出 screenId）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<待 closure audit 填写>>

Follow-up:

- <<待 closure 时填写>>
