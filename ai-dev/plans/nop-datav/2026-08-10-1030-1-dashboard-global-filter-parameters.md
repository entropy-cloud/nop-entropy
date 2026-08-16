# 3 看板全局筛选参数（D2-1）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D2-1 全局筛选参数
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md`（D2 阶段，work item D2-1）；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md`
> Related: 设计契约 `ai-dev/design/nop-datav/linkage-design.md`（本 plan Phase 1 产出 D2-1 部分）；前置计划 `2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`（D1 已完成）；后继计划 `2026-08-10-1030-2-chart-linkage-and-filter-state.md`（D2-2/D2-3 依赖本 plan 的参数模型）

## Purpose

将 nop-datav 从「面板级 ad-hoc 请求参数查询」推进到「看板级全局筛选参数模型可用」——看板可声明命名/类型化的参数定义，面板通过 paramMapping 绑定到这些参数，改变全局筛选值即可影响所有绑定面板的查询结果，且筛选状态可通过 URL 同步。本 plan 收口 roadmap D2-1 的全部后端验收条件，并为 D2-2（图表联动）和 D2-3（联动状态服务）提供参数模型基础。

## Current Baseline

（已对照 live repo 核对，2026-08-10）

### D0 + D1 已落地（前置基础）

- `nop-datav/model/nop-datav.orm.xml` 存在 5 个实体：NopDatavDashboard、NopDatavPanel、NopDatavDashboardTab、NopDatavDatasetRef、NopDatavDashboardSnapshot。codegen 全链路通过，`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 退出码 0。
- 组件注册表覆盖 7 种组件类型（`PanelComponentRegistry`，`io.nop.datav.service.component` 包）；`panelType` dict 已扩展 8 个选项（CHART/TABLE/METRIC/TEXT/CONTAINER/PIVOT_TABLE/MAP/IFRAME）。
- 数据绑定管线完整可用（`PanelDataBinder`）：Panel → DatasetRef 解析 → paramMapping 参数求值（`PanelParamEvaluator`）→ NopReportDataset SQL 查询 → 结构化结果回传（`PanelDataResult`）。
- 面板数据查询 API 已可用：`INopDatavPanelBiz.getPanelData(id, params, context)`（`@BizQuery`）+ `refreshPanel(id, context)`（`@BizMutation`）。`getPanelData` 接收 `Map<String,Object> requestParams` 作为 ad-hoc 请求参数，经 paramMapping 求值后注入 SQL。
- 错误码类 `NopDatavErrors`（`io.nop.datav.service` 包）已有 10 个错误码。
- 38 个测试通过（`./mvnw test -pl nop-datav/nop-datav-service`）。
- 设计文档 `model-design.md`（D0）和 `runtime-design.md`（D1）均已定稿（`Status: final`）。

### 现有参数机制（D1 遗产，D2-1 输入面）

- **DatasetRef.paramMapping**（`json-4000`）：格式为 `{"sqlParamName": {"source": "requestParamKey", "defaultValue": ...}}`。`PanelParamEvaluator.evaluate()` 遍历 paramMapping，按 `source` 从请求参数 Map 取值，未取到用 `defaultValue`。**仅出现在 paramMapping 中的参数才会传入 SQL**——未声明的请求参数被忽略。
- **关键观察**：paramMapping 的 `source` 目前指向「请求参数 Map 中的 key 名」。D2-1 不需要改变 paramMapping 格式或 `PanelParamEvaluator` 的求值逻辑——只需要在 paramMapping 上游增加「看板级参数定义」，使得请求参数来源于有类型、有默认值、可校验的参数模型，而非 ad-hoc。
- **当前缺口**：不存在看板级参数定义模型——调用方传入什么参数、参数是什么类型、有什么默认值，完全没有契约约束。改变一个全局筛选值需要调用方自己逐面板调用 `getPanelData` 并传入正确参数。

### Dashboard 实体现状（D2-1 模型变更面）

- `NopDatavDashboard` 实体当前有 `layoutConfig`（`json-4000`）存储布局配置，**无参数定义字段**。
- D2-1 需要为 Dashboard 增加参数定义存储能力。存储方案（新 JSON 列 vs 新实体 vs 复用 layoutConfig）待 Phase 1 design doc 裁定（见 Goals）。

### 联动设计文档现状

- `ai-dev/design/nop-datav/linkage-design.md` 是 4 行 stub（`Status: stub`），待 Phase 1 重写。

### 平台依赖

- `IJdbcTemplate`、`IDaoProvider`、`JsonTool` 均已在 nop-datav-service 中可用。
- nop-auth 用户上下文（`IServiceContext` / `ISecurityContext`）可用于后续 D2-3 的 filter_state，但本 plan（D2-1）不依赖。

## Goals

- **看板级参数定义模型**：Dashboard 可声明命名/类型化的全局筛选参数（参数名、类型如 string/number/date/date-range、默认值、显示配置如 label/widget 类型）。参数定义的存储方案在 Phase 1 design doc 中裁定并记录。
- **面板级参数映射（复用 D1 paramMapping）**：roadmap D2-1 的「面板级参数映射」= D1 已落地的 DatasetRef.paramMapping（`source` → SQL 参数名映射）。D2-1 不新建面板级映射机制，而是让 paramMapping 的 `source` 指向看板参数定义中的参数名（含扁平化的复合 key 如 `dateRange.start`），从而将全局筛选参数连通到面板查询。此复用关系在 design doc 显式记录。
- **全局筛选应用流程**：提供一个后端 API，接收全局筛选值 → 按参数定义校验/归一化（类型检查、默认值填充）→ 返回生效参数值。调用方可将生效参数值传入已有 `getPanelData` 触发各绑定面板重新查询（复用 D1 管线，不重建查询逻辑）。
- **URL 参数同步**：提供参数值的 URL 序列化/反序列化能力（编码为 URL query string、从 URL 解析回参数值 Map），使筛选状态可通过 URL 分享/恢复。
- **设计文档定稿**：将 `linkage-design.md` 从 stub 重写为最终设计文档，记录 D2-1 的参数定义模型方案、全局筛选应用流程、URL 同步策略、与 D1 paramMapping 的关系、拒绝的替代方案。（D2-2/D2-3 的设计决策在本 plan 中仅占位，由后继 plan 补充。）

## Non-Goals

- **不实现图表联动（D2-2）**：联动（click chart → filter other panels）、跳转（click → navigate）、外部参数注入属 D2-2，由后继 plan `2026-08-10-1030-2-chart-linkage-and-filter-state.md` 处理。
- **不实现联动状态服务（D2-3）**：filter_state 保存/恢复属 D2-3，由后继 plan 处理。本 plan 的 URL 同步是「无状态」的参数序列化，不是持久化的 filter_state。
- **不实现前端集成（D2-4）**：flux dashboard-filter 控件族未落地，前端不做。
- **不重建查询引擎**：全局筛选应用复用 D1 的 `getPanelData` + `PanelDataBinder` + `PanelParamEvaluator`，本 plan 仅增加参数定义层和校验层。
- **不处理多数据源路由**：与 D1 Non-Goal 一致。
- **不处理 DatasetRef 与 nop-metadata 维度/度量的字段映射元数据运行时解析**（D1 deferred 的 `optimization candidate`）：该 deferred 项关注的是「结果列 → 维度/度量语义映射」，与 D2-1 的「筛选参数定义与求值」是不同关注点，不在本 plan scope。
- **不做批量面板查询优化**：当前为前端逐面板调用 `getPanelData`。是否提供后端批量「一次查询所有面板」API 属优化项，不在本 plan scope。

## Scope

### In Scope

- 看板级参数定义模型（存储方案 + 参数定义结构 + 解析/校验逻辑）。
- Dashboard 实体的 ORM 模型变更（如 Phase 1 裁定为新增列或新增实体）——属 plan-first Protected Area，本 plan 为授权 artifact。
- **发布/快照包含参数定义**：更新 `NopDatavDashboardBizModel.serializeDashboardContent()` / `restoreDashboardFromSnapshot()` 将 paramConfig（或裁定方案的参数定义存储）纳入快照序列化/恢复范围，确保发布后的看板保留参数定义。
- 全局筛选应用 API（Dashboard BizModel 扩展 `@BizQuery` action）：接收筛选值 → 校验/归一化 → 返回生效参数值。
- URL 参数序列化/反序列化能力。
- `linkage-design.md` 从 stub 重写为最终设计文档（D2-1 部分；D2-2/D2-3 部分占位待后继 plan 补充）。
- 参数定义模型 + 全局筛选应用 + URL 同步的单元测试。
- 端到端测试：创建带参数定义的看板 → 配置面板 paramMapping 绑定参数 → 应用全局筛选 → 验证面板查询结果受参数影响。

### Out Of Scope

- 图表联动、跳转、外部参数注入（D2-2）。
- filter_state 持久化保存/恢复（D2-3）。
- 前端页面/视图/筛选控件渲染（D2-4）。
- 批量面板查询 API（优化项）。
- nop-metadata 维度/度量字段映射元数据运行时解析。

## Execution Plan

### Phase 1 - 设计文档定稿 + 参数定义模型

Status: completed
Targets: `ai-dev/design/nop-datav/linkage-design.md`、`nop-datav/model/nop-datav.orm.xml`（可能的 Dashboard 列/实体变更）、参数定义解析类（`io.nop.datav.service.filter` 包）

- Item Types: `Decision`, `Fix`

- [x] 重写 `linkage-design.md`（从 stub 到 `Status: final`），记录以下已裁定决策（D2-1 部分；不写类签名/字段定义/伪代码——源码是唯一事实）：
  - **参数定义存储方案裁定**：在以下方案中选择并记录理由——
    - 方案 A：Dashboard 新增 `paramConfig` JSON 列（`json-4000` 或 `clobJson`），存储参数定义 JSON 数组。最小 ORM 变更（仅加一列），与 D1 panelConfig/refresh 模式一致。
    - 方案 B：新建 `NopDatavDashboardParam` 独立实体（normalized，一行一参数）。支持独立 CRUD 查询，但 ORM 变更更大（新实体 + BizModel + IBiz + codegen）。
    - 方案 C：复用 Dashboard 现有 `layoutConfig` JSON 存储参数定义。零 ORM 变更，但布局与参数配置耦合。
    - **推荐方案 A**（与既有 JSON config 模式一致，最小变更），但最终选择记录在 design doc 并写清拒绝理由。
  - **参数定义结构约定**：每个参数定义包含哪些配置区域——参数名（唯一标识）、类型（string/number/date/date-range 等）、默认值、显示配置（label、widget 类型如 dropdown/date-picker，供前端消费）。结构约定在 design doc 描述（不写字段级 JSON schema 定义）。
  - **参数值表示约定（D2-1 → D1 集成契约，必须裁定）**：`resolveFilterValues` 的**输入和输出**均使用**扁平 key** Map，与既有 `PanelParamEvaluator.evaluate()` 的 `Map.get(sourceKey)` 查找方式完全兼容。具体约定：
    - 简单类型（string/number/date）：key = 参数名，value = 标量值。如输入/输出 `{"region": "East"}`。
    - 复合类型（date-range）：参数定义声明其有 `start`/`end` 子键；**输入和输出均使用扁平化 key** `paramName.start` / `paramName.end`。如输入/输出 `{"dateRange.start": "2024-01-01", "dateRange.end": "2024-12-31"}`。
    - `resolveFilterValues` 不做 nested→flat 转换——输入已是扁平 key Map，resolver 仅做校验/默认值填充/过滤未定义参数。
    - paramMapping 的 `source` 字段引用这些扁平 key（如 `{"start_date": {"source": "dateRange.start"}}`），因此 `PanelParamEvaluator` 无需修改即可消费复合类型参数。
    - 此约定确保 `resolveFilterValues` 输出可直接作为 `getPanelData` 的 `requestParams` 使用，无需中间转换层。
  - **全局筛选应用流程**：后端 API 接收原始扁平 key 筛选值 Map → 按参数定义校验类型/填充默认值/过滤未定义参数 → 返回扁平化生效参数值 Map。调用方（前端或测试）将生效参数值传入 `getPanelData` 的 `requestParams`，经既有 paramMapping 求值后注入 SQL。**不重建查询逻辑，不引入批量查询。**
  - **URL 参数同步策略**：URL 编码使用与扁平 key 一致的格式——简单参数 `?paramName=value`，复合参数 `?paramName.start=v1&paramName.end=v2`（与扁平 key 命名一致，确保 URL → Map 反序列化后 key 与 `resolveFilterValues` 输出兼容）。仅序列化有值且非默认值的参数。
  - **与 D1 paramMapping 的关系**：全局参数定义是 paramMapping `source` 的上游——`source` 指向的参数名应对应看板参数定义中的某个参数名。本 plan 不强制要求 paramMapping source 与参数定义严格匹配（允许 paramMapping 引用未定义的 ad-hoc 参数以保持向后兼容），但在 design doc 记录推荐用法。
  - **拒绝的替代方案**：至少记录——独立实体 vs JSON 列（选择理由）、后端批量查询 vs 前端逐面板调用（选择后者的理由）、参数定义严格校验 vs 宽松兼容（选择理由）。
- [x] 实现 ORM 模型变更（按 Phase 1 裁定方案）：如选方案 A，在 `nop-datav.orm.xml` 的 NopDatavDashboard 实体新增 `paramConfig` 列（domain json-4000 或 clobJson，含 `i18n-en:displayName`），propId 递增。ORM 变更属 plan-first，本 plan 为授权 artifact
- [x] 实现 `linkage-design.md` 中 D2-1 部分的设计记录（不含 D2-2/D2-3 具体决策，但留占位说明「D2-2/D2-3 决策由后继 plan 补充」）
- [x] 运行 `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 触发 codegen，确认 ORM 变更后生成物一致、编译通过
- [x] 更新 `NopDatavDashboardBizModel` 的发布/快照逻辑：`serializeDashboardContent()` 将 paramConfig（或裁定方案的参数定义存储）纳入快照内容；`restoreDashboardFromSnapshot()` 从快照恢复 paramConfig。确保发布后的看板保留参数定义（参考现有 serialize 方法中 layoutConfig/panels/tabs/datasetRefs 的序列化模式）
- [x] 实现参数定义解析逻辑（在 `io.nop.datav.service.filter` 包）：从 Dashboard 的参数定义存储（如 paramConfig JSON）解析出参数定义列表；提供按参数名校验/归一化筛选值的能力

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `linkage-design.md` 不再是 stub（无 `Status: stub`），包含 D2-1 全部已裁定决策记录
- [x] `linkage-design.md` 不含 "Proposed Design"/"Current vs Proposed" 段落（plan guide rule #14）
- [x] ORM 模型变更已落地（如方案 A：Dashboard 新增 `paramConfig` 列），codegen 产物同步更新，`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 退出码 0
- [x] 参数定义解析逻辑存在（`io.nop.datav.service.filter` 包），可从 Dashboard 存储解析参数定义列表，可校验/归一化筛选值
- [x] **发布/快照包含参数定义**：`serializeDashboardContent` / `restoreDashboardFromSnapshot` 已更新纳入 paramConfig；发布后查看已发布版本的看板仍可解析参数定义（测试可观测断言）
- [x] **新功能测试覆盖**（rule #25）：参数定义解析的单元测试——正常解析、空参数定义、参数定义格式错误时显式失败（非返回 null）；**发布→快照序列化→快照恢复→重新解析参数定义的往返测试**（验证 serialize/restore 不会丢失 paramConfig）
- [x] **无静默跳过**（rule #24）：参数定义格式错误时抛异常（如 `NopException`），不返回 null/空列表
- [x] owner-doc 更新：`linkage-design.md` 记录 Phase 1 D2-1 决策；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 全局筛选应用 API + URL 同步

Status: completed
Targets: `NopDatavDashboardBizModel.java`（扩展自定义 action）、`INopDatavDashboardBiz.java`（声明新方法签名）、URL 参数序列化类（`io.nop.datav.service.filter` 包）、`NopDatavErrors.java`（新增错误码）

- Item Types: `Fix`

- [x] 在 `NopDatavErrors` 新增 D2-1 相关错误码：参数定义格式错误、未知参数名、参数值类型不匹配。错误消息用英文
- [x] 在 `NopDatavDashboardBizModel` 实现全局筛选应用 API（`@BizQuery` action，如 `resolveFilterValues`）：接收 dashboardId + 原始筛选值 Map → 加载 Dashboard 参数定义 → 校验类型/填充默认值/过滤未定义参数 → 返回生效参数值 Map。该方法签名**同步声明到 `INopDatavDashboardBiz` 接口**（含 `@BizQuery` + `@Name` 参数注解 + `IServiceContext` 末参）
- [x] 实现参数类型校验逻辑：按参数定义声明的类型（string/number/date/date-range）校验传入值。类型不匹配时显式失败（抛 `NopException` + 对应 ErrorCode + `.param(...)`），不静默忽略或强转
- [x] 实现 URL 参数序列化/反序列化（在 `io.nop.datav.service.filter` 包）：将生效参数值 Map 序列化为 URL query string；从 URL query string 反序列化回参数值 Map。格式与 Phase 1 裁定的扁平 key 一致——简单参数 `paramName=value`，复合参数 `paramName.start=v1&paramName.end=v2`。暴露为 Dashboard BizModel 的 `@BizQuery` action（如 `parseFilterFromUrl`），方法签名同步声明到 `INopDatavDashboardBiz` 接口

Exit Criteria:

- [x] `INopDatavDashboardBiz` 含全局筛选应用方法声明 + URL 解析方法声明，`NopDatavDashboardBizModel` 含对应实现（`@BizQuery`），方法已在接口声明（否则 I*Biz 代理调用抛 unsupported-method）
- [x] `NopDatavErrors` 含 D2-1 相关错误码（至少：参数定义格式错误、未知参数名、参数值类型不匹配）
- [x] **接线验证**（rule #23）：通过注入 `INopDatavDashboardBiz` 代理调用全局筛选应用方法，断言返回的生效参数值经过校验和默认值填充（证明 Dashboard BizModel → 参数定义解析 → 校验/归一化 调用链在运行时连通，非 mock-only）
- [x] 参数类型校验：传入正确类型值返回成功；传入不匹配类型显式失败（非静默强转/忽略）
- [x] URL 序列化/反序列化往返一致：序列化后反序列化应还原原始参数值（允许格式归一化，但语义不变）
- [x] **新功能测试覆盖**（rule #25）：显式列出——全局筛选应用正常路径（有默认值填充）、**复合类型 (date-range) 输入 → `resolveFilterValues` → 断言输出含扁平 key `dateRange.start`/`dateRange.end`**、类型校验错误路径、未知参数过滤、URL 序列化往返测试（含复合类型 URL 往返）
- [x] **无静默跳过**（rule #24）：未知参数名、类型不匹配、参数定义格式错误等分支抛异常或显式过滤，不静默忽略
- [x] owner-doc 更新：`linkage-design.md` 补充全局筛选应用流程与 URL 同步策略（Phase 1 已写入或本 Phase 补充）；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + 测试覆盖

Status: completed
Targets: `nop-datav/nop-datav-service/src/test/...`

- Item Types: `Proof`

- [x] 编写端到端测试，贯穿完整链路：「创建看板（带参数定义，如 region=string/default=all、dateRange=date-range）→ 创建测试业务数据表 + 数据（如 TEST_DATAV_SALES2，含 region/amount/sale_date）→ 创建 NopReportDataset（SQL 查询 TEST_DATAV_SALES2，使用 ${region} 和 ${start_date}/${end_date} 命名参数）→ 创建 DatasetRef（paramMapping 将看板参数映射到 SQL 参数：region→region, dateRange.start→start_date, dateRange.end→end_date）→ 创建 Panel（绑定 DatasetRef）→ 调用全局筛选应用 API 设置 region=East + dateRange（如 start=2024-01-01, end=2024-06-30）→ 将生效参数传入 getPanelData → 断言返回仅 East 区域且在日期范围内的数据 → 仅改变 dateRange（缩窄/扩宽日期范围）→ 断言查询结果随日期范围变化（证明复合类型参数端到端生效，非仅简单类型）」
- [x] 端到端测试覆盖 URL 同步：序列化筛选值 → 反序列化 → 传入全局筛选应用 → 验证结果一致
- [x] 端到端测试覆盖默认值场景：不传筛选值时，参数定义的默认值生效，查询使用默认值

Exit Criteria:

- [x] 端到端测试类存在且 `./mvnw test -pl nop-datav/nop-datav-service` 退出码 0
- [x] **端到端验证**（rule #22）：端到端测试从「创建带参数的看板」到「全局筛选改变面板查询结果」完整跑通，断言查询结果随筛选值变化（非仅无异常）
- [x] **参数化验证**：至少一条测试断言不同筛选值导致不同查询结果（证明参数定义 → paramMapping → SQL 链路真正生效）；**至少一条断言验证复合类型 (date-range) 改变导致查询结果变化**（证明复合类型参数端到端生效，非仅简单类型）
- [x] **URL 同步验证**：序列化 → 反序列化 → 查询结果一致
- [x] **新增功能测试覆盖**（rule #25）：显式列出端到端测试覆盖的场景（正常筛选、默认值、URL 往返）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划涉及 ORM 模型变更（plan-first Protected Area），构建验证条目为必填。

- [x] D2-1 work item 已落地或显式移出 scope
- [x] 看板级参数定义模型可用（参数可声明、可校验、有默认值）
- [x] 全局筛选应用 API 可用（设置筛选值 → 所有绑定面板查询受影响）
- [x] URL 参数同步可用（序列化/反序列化往返一致）
- [x] design doc `linkage-design.md` D2-1 部分与 live baseline 一致（无 drift）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（`linkage-design.md`、roadmap D2-1 状态）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）全局筛选应用 → getPanelData → paramMapping → SQL 调用链在运行时连通（端到端测试证明），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 退出码 0（含测试的完整 `-am` 构建在上游 nop-web 模块命中预存失败 `TestFluxWebCrudPage.testCrudPageGeneratesFluxJson`，与 D2-1 无关——nop-web 不依赖 nop-datav，本计划变更无法影响其测试结果）
- [x] `./mvnw test -pl nop-datav/nop-datav-service` 退出码 0（72 tests, 0 failures）
- [x] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 第三方 → java.*；包名 `io.nop.datav`）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0

## Deferred But Adjudicated

（执行中按需填写；当前无预判延期项。）

## Non-Blocking Follow-ups

- 批量面板查询 API（当前前端逐面板调用 getPanelData，批量查询为优化项）
- 参数定义与 paramMapping source 的严格匹配校验（当前宽松兼容，允许 paramMapping 引用未定义参数以保持向后兼容）
- DatasetRef 与 nop-metadata 维度/度量的字段映射元数据运行时解析（D1 deferred 的 optimization candidate，与 D2-1 不同关注点）

## Closure

Status Note: D2-1 全局筛选参数全部落地。看板级参数定义模型（paramConfig JSON 列 + 解析/校验逻辑）、全局筛选应用 API（resolveFilterValues）、URL 参数同步（DashboardFilterUrlCodec + parseFilterFromUrl action）均已实现并通过单元测试、接线测试、端到端测试验证。独立 closure audit 通过，无 Blocker。E2E 测试证明 resolveFilterValues → getPanelData → paramMapping → SQL 调用链端到端连通，且复合类型 date-range 改变导致查询结果变化。design doc linkage-design.md 已从 stub 重写为 final，D2-2/D2-3 占位留给后继 plan。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure subagent（task ses_01841aa17ffeeAVCSqRxST5hbx，read-only audit）
- Audit Session: ses_01841aa17ffeeAVCSqRxST5hbx
- Evidence:
  - 每条 Exit Criterion 验证结果（全 PASS）：
    - Phase 1: linkage-design.md `Status: final`（非 stub，无 Proposed/Current-vs-Proposed 段落）；ORM paramConfig 列 propId=18 + codegen 同步（`_NopDatavDashboard.getParamConfig()` :1011）；参数定义解析逻辑存在于 `io.nop.datav.service.filter`；serialize/restore 含 paramConfig；21 单测覆盖正常/空/格式错误显式失败/快照往返。
  - 每条 Closure Gate 验证结果（全 PASS）：
    - Anti-Hollow: 调用链 resolveFilterValues(:123) → DashboardParamParser.parse(:127) → DashboardFilterResolver.resolve(:128) 运行时连通；E2E row count 3→2→1 随 dateRange 缩窄变化；无空方法体/静默跳过。
    - `scan-hollow-implementations.mjs --module nop-datav --severity high` → EXIT_CODE 0, 0 findings。
    - `check-plan-checklist.mjs --strict` → 退出码 0（Closure Evidence 已写入）。
    - `./mvnw test -pl nop-datav/nop-datav-service` → 72 tests, 0 failures, BUILD SUCCESS。
    - `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` → BUILD SUCCESS。
  - Deferred 项分类检查：ERR_DATAV_UNKNOWN_PARAM_NAME 声明但未使用——符合 design doc §六 宽松兼容决策（过滤而非报错），预留严格匹配 follow-up，非 in-scope live defect 降级。
  - Phase 2: INopDatavDashboardBiz 含 resolveFilterValues + parseFilterFromUrl 声明（:27-34）；BizModel 实现 @BizQuery（:121-145）；3 新错误码英文消息；9 接线测试通过 BizModel 代理验证调用链连通（非 mock-only）；URL 往返一致。
  - Phase 3: E2E 测试 3 条通过，断言不同筛选值/复合类型 date-range 改变导致查询结果变化；URL 同步 E2E；默认值场景覆盖。

Follow-up:

- 批量面板查询 API（优化项，前端逐面板调用 getPanelData）
- 参数定义与 paramMapping source 严格匹配校验（宽松兼容，向后兼容 D1）
- DatasetRef 与 nop-metadata 维度/度量字段映射元数据运行时解析（D1 deferred）
