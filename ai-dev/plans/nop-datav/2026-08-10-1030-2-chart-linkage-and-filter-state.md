# 4 图表联动三件套与联动状态服务（D2-2 + D2-3）

> Plan Status: active
> Mission: nop-datav
> Work Item: D2-2 图表联动三件套 + D2-3 联动状态服务
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md`（D2 阶段，work items D2-2/D2-3）；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md`
> Related: 设计契约 `ai-dev/design/nop-datav/linkage-design.md`（本 plan Phase 1 产出 D2-2/D2-3 部分，补充前置 plan 的 D2-1 部分）；前置计划 `2026-08-10-1030-1-dashboard-global-filter-parameters.md`（D2-1 必须先完成——联动与状态服务依赖参数模型）

## Purpose

将 nop-datav 从「全局筛选参数可用」推进到「图表交互联动可用 + 联动状态可持久化」——点击图表元素可触发其他面板筛选（联动）、跳转到其他看板/外部 URL（跳转）、从外部接收参数注入（外部参数注入），且当前筛选+联动状态可保存/恢复。本 plan 收口 roadmap D2-2 和 D2-3 的全部后端验收条件，完成后 D2 后端全部就绪（D2-4 前端因 flux 侧未落地而移出 scope）。

## Current Baseline

（基于前置 plan `2026-08-10-1030-1-dashboard-global-filter-parameters.md` 完成后的预期状态编写；实际执行前须核实 D2-1 已 `completed`）

### D0 + D1 已落地（见前置 plan 的 Current Baseline，此处不重复）

### D2-1 完成后的预期状态（前置依赖 — 以下为假设，执行前须核实 D2-1 已 `completed` 且 API/存储方案与本 plan 假设一致）

> **注意**：以下 D2-1 产物在本 plan 起草时尚未落地（D2-1 plan 为 `draft`）。执行本 plan 前必须先完成 D2-1 并核实以下假设。若 D2-1 实际实现与本节假设有偏差，执行者须先修正本 plan 的引用再执行。

- Dashboard 参数定义模型已可用：Dashboard 可声明命名/类型化全局筛选参数（存储在 paramConfig JSON 列，假设 D2-1 Phase 1 裁定为方案 A），参数有类型、默认值、显示配置。
- 全局筛选应用 API 已可用：Dashboard BizModel 含一个全局筛选解析 action（D2-1 暂定名 `resolveFilterValues`，**执行前须核实实际 API 名称**），接收原始筛选值 → 按参数定义校验/归一化 → 返回扁平化生效参数值 Map。
- URL 参数同步已可用：参数值可序列化为 URL query string 并反序列化还原（扁平 key 格式）。
- `linkage-design.md` 已定稿 D2-1 部分（`Status: final`），D2-2/D2-3 部分待本 plan 补充。
- `PanelDataBinder` + `PanelParamEvaluator` + `getPanelData` 管线完整可用，面板查询通过 requestParams → paramMapping 求值。**关键**：D2-1 约定 `resolveFilterValues` 输出为扁平 key Map（复合类型如 date-range 使用 `paramName.start`/`paramName.end`），可直接作为 `getPanelData` 的 requestParams。

### D2-2 的输入面（联动配置存储现状）

- 当前 Panel 实体有 `panelConfig`（`json-4000`），D1 已在其中存储组件配置 + refresh 配置。联动配置（linkage rules）可扩展存储在 panelConfig JSON 中，或独立存储——待 Phase 1 裁定。
- 当前不存在任何联动配置模型：Panel 之间无「点击 A 的某个字段 → 设置 B 的某个筛选值」的关联定义。
- Dashboard 的 `layoutConfig`（`json-4000`）存储布局，可扩展存储看板级联动/跳转配置（如跨面板联动规则）——待 Phase 1 裁定。

### D2-3 的输入面（filter_state 存储现状）

- 当前不存在 filter_state 持久化机制。筛选状态仅存在于前端运行时内存 + URL 参数（D2-1）。
- nop-auth 用户上下文可用：`IServiceContext` / `ISecurityContext` 可获取当前用户 ID，用于 filter_state 的 per-user 隔离。
- nop-datav 当前无「用户偏好/状态持久化」的既有模式可参考——filter_state 存储方案需 Phase 1 裁定（新建实体 vs JSON 列 vs 平台 key-value 存储）。

### 平台依赖

- `IJdbcTemplate`、`IDaoProvider`、`JsonTool` 均已在 nop-datav-service 中可用。
- `NopDatavErrors`（`io.nop.datav.service` 包）已有 D0/D1/D2-1 错误码，本 plan 扩展。
- 前置 plan 产出的参数定义解析 + 全局筛选应用能力（`io.nop.datav.service.filter` 包）。

## Goals

- **联动（D2-2 子项 1）**：点击图表面板的某个数据点/维度值，自动将值注入目标面板的筛选参数，触发目标面板重新查询。需要联动配置模型（源面板/字段 → 目标面板/参数映射）+ 联动执行 API。
- **跳转（D2-2 子项 2）**：点击图表面板的某个数据点，跳转到另一个看板或外部 URL，并携带上下文参数。需要跳转配置模型（源面板/字段 → 目标 URL/dashboard + 参数映射）+ 跳转 URL 解析 API。
- **外部参数注入（D2-2 子项 3）**：看板可从外部源（嵌入 URL 参数、API 调用方）接收参数注入，注入的参数参与全局筛选参数求值。复用 D2-1 的参数定义 + URL 同步能力。
- **联动状态服务（D2-3）**：将当前看板的完整筛选+联动状态（全局筛选值 + 各面板的联动选择）序列化保存，并支持按用户+看板恢复。保存/恢复 API + 存储方案。
- **设计文档补充**：在 `linkage-design.md` 中补充 D2-2/D2-3 的决策记录，使设计文档覆盖完整 D2 阶段。

## Non-Goals

- **不实现前端集成（D2-4）**：flux dashboard-filter / linkage 交互控件未落地，前端不做。本 plan 仅做后端配置模型 + API + 状态服务。
- **不支持跨看板联动**：联动仅限同一看板内面板间。跨看板场景用「跳转」实现（跳转到目标看板并携带参数）。
- **不重建全局筛选参数模型**：复用 D2-1 的参数定义 + 全局筛选解析 action + URL 同步。本 plan 在其上增加联动配置层。引用 D2-1 API 时使用角色描述（「D2-1 全局筛选解析 action」），执行前须核实 D2-1 实际 API 名称。
- **不重建查询引擎**：联动触发的目标面板查询复用 D1 的 `getPanelData` + `PanelDataBinder`。
- **不实现 filter_state 的实时推送/WebSocket**：filter_state 保存/恢复为按需 API 调用，不做实时同步。
- **不做多看板 filter_state 聚合**：filter_state 以单个看板为粒度，不做跨看板状态聚合。
- **不实现大屏装饰/主题（D4）、定时报告/告警（D5）、AI/ChatBI（D6）**。
- **不处理 nop-metadata 维度/度量字段映射元数据**（D1 deferred，与联动不同关注点）。

## Scope

### In Scope

- 联动配置模型（存储方案 + 配置结构 + 解析逻辑）（D2-2）。
- 联动执行 API：接收源面板点击上下文 → 解析联动规则 → 返回目标面板应应用的筛选参数（D2-2）。
- 跳转配置模型 + 跳转 URL 解析 API（D2-2）。
- 外部参数注入流程（看板从外部接收参数 → 合入全局筛选参数求值）（D2-2）。
- filter_state 序列化/保存/恢复 API + 存储方案（D2-3）。
- `linkage-design.md` 补充 D2-2/D2-3 设计决策。
- 联动 + 跳转 + 外部参数注入 + filter_state 的单元测试。
- 端到端测试：联动触发 → 目标面板筛选生效；filter_state 保存 → 恢复 → 状态一致。

### Out Of Scope

- 前端联动交互控件渲染（D2-4）。
- 全局筛选参数定义模型（D2-1，前置 plan 已做）。
- 批量面板查询优化。
- 实时 filter_state 推送。
- 大屏（D4）、定时报告/告警（D5）、AI/ChatBI（D6）。

## Execution Plan

### Phase 1 - 设计文档补充 + 联动配置模型

Status: planned
Targets: `ai-dev/design/nop-datav/linkage-design.md`（补充 D2-2/D2-3 部分）、可能的 ORM 模型变更（联动配置存储 + filter_state 存储）、联动配置解析类（`io.nop.datav.service.linkage` 包）

- Item Types: `Decision`, `Fix`

- [ ] 在 `linkage-design.md` 补充 D2-2/D2-3 的已裁定决策（不写类签名/字段定义/伪代码——源码是唯一事实）：
  - **联动配置存储方案裁定**：在以下方案中选择并记录理由——
    - **已裁定方案 A**：联动规则存储在源 Panel 的 `panelConfig` JSON 中（`linkage` 区域），格式为联动目标规则数组。与 D1 refresh 配置模式一致，零 ORM 变更。**容量注意**：panelConfig 当前为 `json-4000`（VARCHAR 4000），已存储组件配置 + refresh 配置。若联动+跳转规则较多可能溢出——若执行中发现 4000 不够，将 panelConfig 列的 domain 从 `json-4000` 升级为 `clobJson`（CLOB），此为最小 ORM 变更，在本 plan scope 内。
    - 方案 B（新建 `NopDatavPanelLinkage` 独立实体）已排除：normalized 但 ORM 变更大，且联动规则数量通常有限，JSON 存储足够。
    - 方案 C（看板级 layoutConfig JSON 存储联动）已排除：联动规则是面板级属性，放在看板级 layoutConfig 中会导致归属混乱。
  - **联动配置结构约定**：每条联动规则包含——源字段（点击的数据点对应的列名/维度名）、目标面板 ID、目标参数名（注入到目标面板哪个筛选参数）。结构约定在 design doc 描述。
  - **跳转配置结构约定**：每条跳转规则包含——源字段、目标类型（dashboard/external-url）、目标标识（dashboardId 或 URL 模板）、参数映射（将源字段值注入目标 URL/参数）。**URL 模板语法裁定**：使用 `${paramName}` 占位符（与 nop 平台 SQL 命名参数语法一致），如 `https://example.com/report?region=${region}`。跳转解析时将点击上下文中的字段值替换到模板占位符。跳转返回结构包含 `targetType`（dashboard/external-url）、`targetId`（dashboardId 或解析后 URL）、`params`（注入的参数 Map）。
  - **跨面板联动范围裁定**：本 plan 联动仅支持**同一看板内**的面板间联动（源面板与目标面板属于同一 dashboardId）。跨看板联动（目标面板在不同看板上）属 Out Of Scope——因为跨看板场景更适合用「跳转」实现（跳转到目标看板并携带参数）。此裁定在 design doc 记录。
  - **外部参数注入策略**：外部参数通过 API 请求参数传入（与 D2-1 全局筛选参数同通道），参与 D2-1 全局筛选解析 action 的求值。复用 D2-1 URL 同步能力做嵌入场景的参数注入。不引入独立的「外部参数」概念——外部参数即「由 URL/embed/API 传入的全局筛选参数」。**此子项为文档约定 + 复用，不新增独立代码**——在 design doc 显式说明。
  - **filter_state 存储方案裁定**：**已裁定为方案 A（新建 `NopDatavFilterState` 独立实体）**。理由：(1) 平台经审查不存在 user-preference / key-value 存储机制（grep 平台模块 `userPreference|UserPreference|user_preference|IUserPreference` 仅在 `nop-migration` 的 Ofbiz 迁移测试资源中有遗留引用，非平台 API；方案 B 已排除）；(2) roadmap D2-3 验收参考 Superset filter_state API（后端持久化语义），需要后端保存/恢复能力；(3) 纯前端 localStorage 方案（方案 C）无法跨设备同步、无法服务端渲染。独立实体支持按 用户 + 看板 查询/删除/覆盖，语义最直接。
  - **filter_state 内容契约（行为规格）**：filter_state JSON 内容结构约定为：
    - `globalFilters`：对象，key = 看板参数名（含扁平化复合 key），value = 参数值。与 D2-1 `resolveFilterValues` 输出格式一致。
    - `panelSelections`：对象，key = 源面板 ID（联动选择的发起者），value = 该面板当前的联动选择状态（被点击的字段名 + 值）。
    - `urlState`：字符串，当前筛选状态的 URL 序列化形式（D2-1 URL 同步输出），用于快速分享/恢复。
    - filter_state 以单个看板为粒度，一个用户一个看板对应一条 filter_state 记录（保存时覆盖旧记录）。
  - **拒绝的替代方案**：至少记录——联动配置 Panel JSON vs 独立实体、filter_state 后端实体 vs 前端 localStorage vs 平台 key-value（方案 B 已排除，零匹配）。
- [ ] 实现 ORM 模型变更：在 `nop-datav.orm.xml` 新增 `NopDatavFilterState` 实体（含 userName/dashboardId/stateContent(clobJson)/标准审计列，唯一键 (userName, dashboardId)），并为其生成保留层 BizModel + IBiz 接口 + codegen 产物。**用户标识用 `userName`（String），不是数字 userId**——平台约定从 `context.getUserContext().getUserName()` 获取（已核实 `NopDatavDashboardBizModel.resolveOperator` 使用 getUserName）。ORM 变更属 plan-first，本 plan 为授权 artifact
- [ ] 实现联动配置解析逻辑（在 `io.nop.datav.service.linkage` 包）：从 Panel.panelConfig JSON 的联动区域解析联动规则列表；从跳转区域解析跳转规则列表
- [ ] 运行 `./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 确认 ORM 变更后生成物一致、编译通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `linkage-design.md` 补充了 D2-2/D2-3 全部已裁定决策，文档完整覆盖 D2 阶段（D2-1 + D2-2 + D2-3）
- [ ] `linkage-design.md` 不含 "Proposed Design"/"Current vs Proposed" 段落（plan guide rule #14）
- [ ] 联动配置解析逻辑存在（`io.nop.datav.service.linkage` 包），可从 panelConfig 解析联动规则 + 跳转规则
- [ ] `NopDatavFilterState` ORM 实体已落地（含 userName/dashboardId/stateContent 列），保留层 BizModel + IBiz 接口存在，codegen 产物同步更新，`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` 退出码 0
- [ ] **新功能测试覆盖**（rule #25）：联动配置解析的单元测试——正常解析、无联动配置的面板（返回空列表，非报错）、配置格式错误时显式失败
- [ ] **无静默跳过**（rule #24）：联动/跳转配置格式错误时抛异常，不返回 null/空列表作为「正常」
- [ ] owner-doc 更新：`linkage-design.md` 记录 Phase 1 D2-2/D2-3 决策；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 联动执行 + 跳转 + 外部参数注入（D2-2）

Status: planned
Targets: `NopDatavPanelBizModel.java`（联动/跳转 action）、`INopDatavPanelBiz.java`（声明新方法签名）、`NopDatavErrors.java`（新增错误码）、联动执行类（`io.nop.datav.service.linkage` 包）

- Item Types: `Fix`

- [ ] 在 `NopDatavErrors` 新增 D2-2 相关错误码：联动配置格式错误、跳转配置格式错误、联动目标面板不存在、跳转目标无效。错误消息用英文
- [ ] 实现联动执行 API（在 NopDatavPanelBizModel 扩展 `@BizQuery` action，如 `resolveLinkage`）：接收源面板 ID + 点击上下文（被点击的字段名 + 值）→ 加载源面板联动配置 → 匹配联动规则 → 返回目标面板 ID + 应应用的筛选参数名/值 Map。调用方（前端）拿到结果后对目标面板调用 `getPanelData` 传入筛选参数。该方法签名同步声明到 `INopDatavPanelBiz` 接口
- [ ] 实现跳转 URL 解析 API（在 NopDatavPanelBizModel 扩展 `@BizQuery` action，如 `resolveJump`）：接收源面板 ID + 点击上下文 → 加载跳转配置 → 匹配跳转规则 → 返回跳转目标（dashboardId 或解析后的 URL，含注入的参数）。该方法签名同步声明到 `INopDatavPanelBiz` 接口
- [ ] 实现外部参数注入：确认外部参数通过 API 请求参数传入 D2-1 的全局筛选解析 action（D2-1 暂定名 `resolveFilterValues`，**执行前须核实实际名称**），在 design doc 记录外部参数注入即「由 URL/embed/API 传入的全局筛选参数」，不引入独立通道
- [ ] 联动执行返回的筛选参数应能直接作为 `getPanelData` 的 requestParams 使用（格式兼容，联动参数值注入到 paramMapping 的 source key）

Exit Criteria:

- [ ] `INopDatavPanelBiz` 含联动执行 + 跳转解析方法声明，`NopDatavPanelBizModel` 含对应实现（`@BizQuery`），方法已在接口声明
- [ ] `NopDatavErrors` 含 D2-2 相关错误码
- [ ] **接线验证**（rule #23）：通过注入 `INopDatavPanelBiz` 代理调用 `resolveLinkage`，断言返回的目标面板 + 筛选参数正确（证明 Panel BizModel → 联动配置解析 → 规则匹配 → 参数合成 调用链连通，非 mock-only）
- [ ] **联动→查询链路验证**：联动返回的筛选参数传入 `getPanelData` 后，目标面板查询结果受联动参数影响（证明联动 → getPanelData → paramMapping → SQL 链路端到端连通）
- [ ] 跳转 URL 解析：源字段值正确注入到目标 URL/参数
- [ ] 外部参数注入：通过 API 请求参数传入的值经 `resolveFilterValues` 后参与面板查询
- [ ] **新功能测试覆盖**（rule #25）：显式列出——联动正常路径、无联动配置面板、联动配置格式错误、跳转正常路径、跳转目标无效
- [ ] **无静默跳过**（rule #24）：联动/跳转配置格式错误、目标面板不存在、跳转目标无效等分支抛异常，不返回 null/placeholder
- [ ] owner-doc 更新：`linkage-design.md` 补充联动/跳转/外部参数注入执行流程；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 联动状态服务（D2-3）

Status: planned
Targets: `NopDatavFilterStateBizModel.java`（filter_state 保存/恢复 BizModel）、`INopDatavFilterStateBiz.java`（对应 IBiz 接口）、`NopDatavErrors.java`

- Item Types: `Fix`

- [ ] 根据 Phase 1 裁定的 filter_state 存储方案（方案 A：独立实体）实现保存/恢复 API：
  - 实现 `NopDatavFilterStateBizModel` 的 `saveFilterState`（`@BizMutation`，接收 dashboardId + 结构化参数：globalFilters Map + panelSelections Map + urlState 字符串，由 BizModel 内部按内容契约序列化为 stateContent JSON 存储，按当前用户 userName 隔离保存/覆盖）和 `getFilterState`（`@BizQuery`，按当前用户 userName + dashboardId 恢复，返回反序列化后的结构化 filter_state；**若该用户+看板无保存记录，返回 null**——调用方据此判断无已保存状态，非静默返回空对象）。方法签名声明到 `INopDatavFilterStateBiz` 接口
- [ ] filter_state 内容按 Phase 1 裁定的内容契约构建：`globalFilters`（看板参数名→值，扁平 key）+ `panelSelections`（源面板 ID→联动选择状态对象，含 `field` 字段名和 `value` 值，支持单个字段+值）+ `urlState`（URL 序列化形式）。序列化/反序列化保持契约结构一致
- [ ] filter_state 按 userName + 看板隔离：不同用户的 filter_state 互不干扰。当前用户 userName 从 `IServiceContext.getUserContext().getUserName()` 获取（平台约定）
- [ ] 在 `NopDatavErrors` 新增 D2-3 相关错误码（如 filter_state 格式错误）

Exit Criteria:

- [ ] filter_state 保存/恢复 API 存在并可用（`saveFilterState`/`getFilterState`），方法已在 `INopDatavFilterStateBiz` 接口声明
- [ ] **接线验证**（rule #23）：通过注入 IBiz 代理调用 saveFilterState → getFilterState，断言恢复后的 filter_state 与保存前一致（证明 序列化 → 存储 → 反序列化 链路连通，非 mock-only）
- [ ] filter_state 内容按契约结构序列化（globalFilters + panelSelections + urlState），往返一致
- [ ] filter_state 按 userName 隔离：用户 A 保存的 state 不影响用户 B
- [ ] **新功能测试覆盖**（rule #25）：显式列出——保存/恢复往返测试、空 state 处理、格式错误处理
- [ ] **无静默跳过**（rule #24）：filter_state 格式错误时显式失败，不返回 null/空作为「正常」
- [ ] owner-doc 更新：`linkage-design.md` 补充 filter_state 设计；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证（D2-2 + D2-3）

Status: planned
Targets: `nop-datav/nop-datav-service/src/test/...`

- Item Types: `Proof`

- [ ] 编写联动端到端测试：「创建看板（带全局参数 region）→ 创建源面板 A（chart，查询含 region 维度的数据）→ 创建目标面板 B（chart，查询引用 region 参数）→ 配置 A 的联动规则（点击 region 值 → 设置 B 的 region 筛选）→ 调用 resolveLinkage 模拟点击 region=East → 对 B 调用 getPanelData 传入联动参数 → 断言 B 仅返回 East 数据」
- [ ] 编写跳转端到端测试：「配置跳转规则（点击 region 值 → 跳转到目标 dashboard，携带 region 参数）→ 调用 resolveJump → 断言返回的目标 URL/dashboardId 含正确的注入参数」
- [ ] 编写 filter_state 端到端测试：「设置全局筛选 region=East + 面板 A 联动选择 → 保存 filter_state → 清除内存状态 → 恢复 filter_state → 断言恢复后的全局筛选 + 联动选择与保存前一致」
- [ ] 端到端测试覆盖「外部参数注入」：通过 API 请求参数传入 region=North → D2-1 全局筛选解析 action 归一化 → 面板查询受影响

Exit Criteria:

- [ ] 端到端测试类存在且 `./mvnw test -pl nop-datav/nop-datav-service` 退出码 0
- [ ] **端到端验证**（rule #22）：联动端到端测试从「点击源面板数据点」到「目标面板查询结果受联动影响」完整跑通
- [ ] **联动→查询链路验证**：联动返回参数传入 getPanelData 后，查询结果确实受联动值影响（非仅调用成功）
- [ ] **filter_state 往返验证**：保存 → 恢复 → 状态一致（globalFilters + panelSelections + urlState 均匹配）
- [ ] **新增功能测试覆盖**（rule #25）：显式列出端到端测试覆盖的场景（联动、跳转、外部参数注入、filter_state 往返）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划可能涉及 ORM 模型变更（filter_state 存储，plan-first Protected Area），构建验证条目为必填。

- [ ] D2-2 和 D2-3 两个 work item 已落地或显式移出 scope
- [ ] 图表联动可用（点击源面板 → 目标面板筛选生效）（D2-2 验收）
- [ ] 跳转可用（点击 → 跳转目标含注入参数）（D2-2 验收）
- [ ] 外部参数注入可用（D2-2 验收）
- [ ] 联动状态服务可用（saveFilterState/getFilterState，按 userName+dashboardId 隔离）（D2-3 验收）
- [ ] design doc `linkage-design.md` 完整覆盖 D2 阶段（D2-1 + D2-2 + D2-3），与 live baseline 一致（无 drift）
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs（`linkage-design.md`、roadmap D2-2/D2-3 状态）已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）联动 resolveLinkage → getPanelData → paramMapping → SQL 链路在运行时连通（端到端测试证明），（b）filter_state saveFilterState → NopDatavFilterState 实体存储 → getFilterState 恢复链路连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw clean install -pl nop-datav -am -T 1C` 退出码 0
- [ ] `./mvnw test -pl nop-datav -am` 退出码 0
- [ ] checkstyle / 代码规范检查通过（import 分组 io.nop.* → 第三方 → java.*；包名 `io.nop.datav`）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0

## Deferred But Adjudicated

### D2-4 前端集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D2-4 依赖 nop-chaos-flux 前端控件族（dashboard-filter / linkage 交互控件）落地，flux 侧目前未产出。后端联动/跳转/状态服务（D2-2/D2-3）可独立验证，不阻塞后端 closure。flux 侧落地后对接即可。
- Successor Required: `yes`
- Successor Path: 后续 D2-4 plan（或合入 D1-4 前端集成阶段）

## Non-Blocking Follow-ups

- 联动配置的可视化编辑 API（当前联动配置通过 panelConfig JSON 手动编写，可视化编辑为前端 D2-4 范围）
- filter_state 的多设备同步（当前方案 A 为 per-userName 单一 state，多设备同时编辑可能覆盖；属 edge case 优化）
- 联动规则的全局校验（检查联动目标面板是否存在、参数是否已定义——当前为运行时校验，可增加配置时校验）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
