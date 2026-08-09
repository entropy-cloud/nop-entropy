# nop-datav 筛选与联动设计 (D2)

> Status: **final**（覆盖 D2-1 + D2-2 + D2-3 全部后端决策）
> Last Reviewed: 2026-08-10

## 概述

本文记录 nop-datav 筛选与联动（D2）后端能力的架构决策。D2-1（全局筛选参数）、D2-2（图表联动三件套）、D2-3（联动状态服务）三块均在本文件定稿。D2-4（前端集成）依赖 nop-chaos-flux 前端控件族，待 flux 侧落地后对接即可，不影响后端 closure。

D2 整体目标：看板可声明命名/类型化的全局筛选参数（D2-1）；面板数据点点击可触发其他面板筛选（联动）、跳转到其他看板/外部 URL（跳转）、从外部接收参数注入（外部参数注入，D2-2）；当前筛选+联动状态可保存/恢复（D2-3）。本模块只做参数定义层 + 校验层 + URL 序列化层 + 联动配置层 + 状态持久化层，**不重建查询引擎**（复用 D1 `getPanelData` + `PanelDataBinder` + `PanelParamEvaluator`）、不做前端渲染（D2-4）。

## 一、参数定义存储方案（D2-1）

### 选择：NopDatavDashboard 新增 `paramConfig` JSON 列（方案 A）

**选择：在 Dashboard 主表新增 `paramConfig` 列，存储参数定义 JSON 数组。** 使用 `clobJson` domain（CLOB JSON），与 DashboardSnapshot 的 `snapshotContent` 同 domain。

参数定义是一个 JSON 数组，每个元素描述一个全局筛选参数。不新增独立实体（不新建 `NopDatavDashboardParam` 表）。

### 参数定义结构约定

`paramConfig` JSON 结构裁定（数组，每个元素为一个参数定义）：

```json
[
  {
    "name": "region",
    "type": "string",
    "defaultValue": "all",
    "label": "区域",
    "widget": "dropdown"
  },
  {
    "name": "dateRange",
    "type": "date-range",
    "defaultValue": { "start": "2024-01-01", "end": "2024-12-31" },
    "label": "日期范围",
    "widget": "date-picker"
  }
]
```

每个参数定义包含以下配置区域（字段名约定）：

| 区域 | 用途 | 必填 | 说明 |
|------|------|------|------|
| `name` | 参数唯一标识 | 是 | 非空字符串，在同一 paramConfig 内唯一 |
| `type` | 参数类型 | 是 | 取值：`string` / `number` / `date` / `date-range` |
| `defaultValue` | 默认值 | 否 | 缺省为 null。date-range 类型使用 `{start,end}` 对象；其余类型为标量 |
| `label` | 显示标签 | 否 | 供前端消费的显示文案 |
| `widget` | 前端控件类型 | 否 | 如 `dropdown` / `date-picker`，供前端消费，后端不解析 |

字段级 JSON schema 不在本文定义（源码解析类是唯一事实）；上表是约定，运行时按此约定解析与校验。

## 二、参数值表示约定（D2-1 → D1 集成契约）

**核心裁定：全局筛选的输入和输出均使用「扁平 key」Map，与既有 `PanelParamEvaluator.evaluate()` 的 `Map.get(sourceKey)` 查找方式完全兼容。**

- **简单类型**（string / number / date）：key = 参数名，value = 标量值。如 `{"region": "East"}`。
- **复合类型**（date-range）：参数定义声明其有 `start`/`end` 子键；**输入和输出均使用扁平化 key** `paramName.start` / `paramName.end`。如 `{"dateRange.start": "2024-01-01", "dateRange.end": "2024-12-31"}`。

**`resolveFilterValues` 不做 nested→flat 转换**——输入已是扁平 key Map，resolver 仅做：按参数定义校验类型 / 填充默认值 / 过滤未定义参数。

paramMapping 的 `source` 字段引用这些扁平 key（如 `{"start_date": {"source": "dateRange.start"}}`），因此 `PanelParamEvaluator` 无需修改即可消费复合类型参数。此约定确保 `resolveFilterValues` 输出可直接作为 `getPanelData` 的 `requestParams` 使用，无需中间转换层。

## 三、全局筛选应用流程（D2-1）

后端 API（`resolveFilterValues`，`@BizQuery` action）接收原始扁平 key 筛选值 Map，按参数定义：

1. 加载 Dashboard 的 `paramConfig`，解析出参数定义列表。
2. 校验类型：按参数定义声明的类型（string/number/date/date-range）校验传入值。类型不匹配显式失败（抛 `NopException` + 错误码），不静默强转。
3. 填充默认值：未传入的参数使用参数定义的 `defaultValue`（date-range 默认值 `{start,end}` 展平为 `paramName.start`/`paramName.end`）。
4. 过滤未定义参数：不在参数定义中的传入 key 被丢弃（避免 SQL 注入面扩大）。
5. 返回扁平化生效参数值 Map。

调用方（前端或测试）将生效参数值传入 `getPanelData` 的 `requestParams`，经既有 paramMapping 求值后注入 SQL。**不重建查询逻辑，不引入批量查询**（前端逐面板调用 `getPanelData`）。

### API 契约（D2-1）

| Action | 类型 | 行为 |
|--------|------|------|
| `resolveFilterValues(dashboardId, filterValues)` | `@BizQuery` | 加载参数定义 → 校验/归一化/填充默认值 → 返回扁平化生效参数 Map |
| `parseFilterFromUrl(dashboardId, url)` | `@BizQuery` | 从 URL query string 反序列化为扁平参数 Map（再经 resolveFilterValues 校验） |

## 四、URL 参数同步策略（D2-1）

**选择：URL 编码使用与扁平 key 一致的格式。**

- 简单参数：`?paramName=value`
- 复合参数：`?paramName.start=v1&paramName.end=v2`（与扁平 key 命名一致，确保 URL → Map 反序列化后 key 与 `resolveFilterValues` 输出兼容）

序列化规则：仅序列化有值且非默认值的参数（减少 URL 长度）。反序列化：URL query string 的每个参数对解析为扁平 key Map，调用方可再传入 `resolveFilterValues` 做类型校验/默认值填充。

## 五、与 D1 paramMapping 的关系

全局参数定义是 paramMapping `source` 的上游——`source` 指向的参数名应对应看板参数定义中的某个参数名（含扁平化复合 key 如 `dateRange.start`）。

**宽松兼容约定**：本设计不强制要求 paramMapping source 与参数定义严格匹配——允许 paramMapping 引用未定义的 ad-hoc 参数以保持向后兼容（D1 已落地的面板可在无参数定义的情况下继续工作）。推荐用法是 paramMapping source 指向已定义参数，但不作为硬约束。

## 六、错误路径（D2-1）

| 场景 | 错误码 |
|------|--------|
| paramConfig JSON 格式错误 | `ERR_DATAV_INVALID_PARAM_CONFIG` |
| 传入未知参数名（已过滤，不报错） | — （过滤而非报错，宽松兼容） |
| 参数值类型不匹配（如 number 类型传非数字） | `ERR_DATAV_PARAM_TYPE_MISMATCH` |

均抛 `NopException` + `.param(...)`，不返回 null/空 Map/placeholder（rule #24 无静默跳过）。参数定义格式错误（JSON 解析失败、结构非数组）显式失败。

## 七、拒绝的替代方案

### 参数定义存储：独立实体 vs JSON 列

| 方案 | 拒绝理由 |
|------|---------|
| 方案 B：新建 `NopDatavDashboardParam` 独立实体（normalized，一行一参数） | ORM 变更更大（新实体 + BizModel + IBiz + codegen）；参数定义通常整体读取/整体写入，无独立 CRUD 查询需求；与既有 JSON config 模式不一致 |
| 方案 C：复用 Dashboard 现有 `layoutConfig` JSON 存储参数定义 | 零 ORM 变更，但布局与参数配置耦合，职责不清，且 layoutConfig 已有 `json-4000` 长度限制 |
| **采用：方案 A，Dashboard 新增 `paramConfig` JSON 列（clobJson）** | 最小 ORM 变更（仅加一列），与既有 config 模式一致；clobJson 避免长度限制（参数定义含显示配置可能较长）；发布/快照序列化复用既有模式 |

### 全局筛选查询：后端批量 vs 前端逐面板

| 方案 | 拒绝理由 |
|------|---------|
| 后端批量查询 API（一次查所有面板） | 优化项，非 D2-1 必需；增加后端复杂度且与 D1 逐面板查询模型不一致 |
| **采用：前端逐面板调用 getPanelData** | 复用 D1 既有管线，无新查询逻辑；resolveFilterValues 输出直接作为各面板 getPanelData 的 requestParams |

### 参数定义校验：严格 vs 宽松

| 方案 | 拒绝理由 |
|------|---------|
| 严格校验：paramMapping source 必须与参数定义匹配 | 破坏 D1 向后兼容（已落地面板无参数定义）；强制耦合两个配置层 |
| **采用：宽松兼容** | 类型校验在 resolveFilterValues 入口做（强约束类型安全）；paramMapping source 允许引用未定义参数（保持 D1 兼容） |

### paramConfig domain：json-4000 vs clobJson

| 方案 | 拒绝理由 |
|------|---------|
| json-4000（VARCHAR 4000） | 与 layoutConfig/panelConfig 一致，但参数定义含 label/widget/默认值等显示配置，数量较多时可能超长截断 |
| **采用：clobJson（CLOB）** | 与 DashboardSnapshot.snapshotContent 同 domain；避免长度限制；参数定义是看板级配置，整体读写，CLOB 无性能影响 |

## 八、D2-2 图表联动三件套

### 8.1 联动配置存储方案

**选择：联动规则存储在源 Panel 的 `panelConfig` JSON 中（`linkage` 区域），跳转规则存储在 `jump` 区域（方案 A）。**

与 D1 refresh 配置、D2-1 组件配置模式一致，零 ORM 变更。联动规则数量通常有限（一个面板通常只对少数字段配置联动目标），JSON 存储足够。`panelConfig` 当前为 `json-4000`；如执行中发现长度溢出，最小 ORM 变更是将 `panelConfig` 列从 `json-4000` 升级为 `clobJson`，仍属本设计 scope。

### 8.2 联动配置结构约定

`panelConfig` JSON 的 `linkage` 区域是一个联动规则数组。每条联动规则描述「源字段 → 目标面板 + 目标参数」的映射：

| 区域 | 用途 | 必填 | 说明 |
|------|------|------|------|
| `sourceField` | 源字段名 | 是 | 被点击的数据点对应的列名/维度名（SQL select alias） |
| `targetPanelId` | 目标面板 ID | 是 | 联动触发的目标面板（必须与源面板属于同一 dashboardId） |
| `targetParam` | 目标参数名 | 是 | 注入到目标面板的筛选参数名（对应 paramMapping 的 source key） |

联动规则匹配：用户点击源面板的某个数据点时，前端携带被点击的字段名 + 值调用联动解析 API；后端按 `sourceField` 匹配联动规则，将值注入到 `targetParam`，返回目标面板 + 应用的筛选参数 Map。调用方（前端）拿到结果后对目标面板调用 `getPanelData` 传入筛选参数（复用 D1 查询管线）。

**仅支持同一看板内联动**：源面板与目标面板必须属于同一 dashboardId。跨看板场景使用「跳转」实现。

### 8.3 跳转配置结构约定

`panelConfig` JSON 的 `jump` 区域是一个跳转规则数组。每条跳转规则描述「源字段 → 目标 dashboard 或外部 URL + 参数映射」：

| 区域 | 用途 | 必填 | 说明 |
|------|------|------|------|
| `sourceField` | 源字段名 | 是 | 被点击的数据点对应的列名/维度名 |
| `targetType` | 目标类型 | 是 | 取值：`dashboard` / `external-url` |
| `targetId` | 目标标识 | 是 | `dashboard` 类型为目标看板 ID；`external-url` 类型为 URL 模板 |
| `params` | 参数映射 | 否 | 注入到目标的参数 Map（key = 目标参数名，value = 源字段名引用 `${sourceField}`） |

**URL 模板语法**：使用 `${paramName}` 占位符（与 nop 平台 SQL 命名参数语法一致），如 `https://example.com/report?region=${region}`。跳转解析时将点击上下文中的字段值替换到模板占位符。对 `dashboard` 类型，`targetId` 为常量看板 ID（不替换），`params` 中可用 `${sourceField}` 引用源字段值。

跳转返回结构包含 `targetType`（dashboard/external-url）、`targetId`（dashboardId 或解析后 URL）、`params`（注入的参数 Map）。

### 8.4 外部参数注入策略

**外部参数即「由 URL/embed/API 传入的全局筛选参数」**——不引入独立的「外部参数」概念。外部源（嵌入 URL 参数、API 调用方）通过 API 请求参数传入值，与 D2-1 全局筛选参数同通道，参与 `resolveFilterValues` 的求值（校验/默认值填充/过滤）。复用 D2-1 URL 同步能力做嵌入场景的参数注入。

**此子项为文档约定 + 复用，不新增独立代码**：外部参数注入没有独立的 API action，调用方直接复用 `resolveFilterValues`。

### 8.5 API 契约（D2-2）

| Action | 类型 | 行为 |
|--------|------|------|
| `resolveLinkage(panelId, clickContext, context)` | `@BizQuery` | 加载源面板联动配置 → 按 `sourceField` 匹配规则 → 返回目标面板 ID + 应用的筛选参数 Map（参数值注入到 paramMapping 的 source key） |
| `resolveJump(panelId, clickContext, context)` | `@BizQuery` | 加载跳转配置 → 匹配跳转规则 → 返回跳转目标（targetType + targetId + params） |

`clickContext` 是一个 Map，至少包含 `field`（被点击的字段名）和 `value`（字段值）。返回的筛选参数 Map 格式与 `resolveFilterValues` 输出兼容，可直接作为 `getPanelData` 的 `requestParams`。

### 8.6 错误路径（D2-2）

| 场景 | 错误码 |
|------|--------|
| panelConfig JSON 格式错误（linkage/jump 区域） | `ERR_DATAV_INVALID_LINKAGE_CONFIG` / `ERR_DATAV_INVALID_JUMP_CONFIG` |
| 联动目标面板不存在 | `ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND` |
| 跳转目标无效（targetType 非法、targetId 缺失） | `ERR_DATAV_INVALID_JUMP_TARGET` |
| 源字段不匹配任何规则 | 不报错，返回 null（前端据此判断无联动/跳转可应用，属正常分支） |

均抛 `NopException` + `.param(...)`，不返回 null/placeholder 作为「正常实现」（rule #24）。「无规则匹配」是合法的运行时分支（用户点击未配置联动的字段），返回 null 让前端处理。

### 8.7 拒绝的替代方案（D2-2 联动/跳转存储）

| 方案 | 拒绝理由 |
|------|---------|
| 方案 B：新建 `NopDatavPanelLinkage` 独立实体（normalized，一行一规则） | ORM 变更更大（新实体 + BizModel + IBiz + codegen）；联动规则数量有限，JSON 存储足够；与既有 panelConfig config 模式不一致 |
| 方案 C：联动规则放在看板级 layoutConfig JSON | 联动规则是面板级属性，放在看板级 layoutConfig 会导致归属混乱 |
| **采用：方案 A，联动规则存储在源 Panel 的 panelConfig JSON** | 零 ORM 变更，与 D1 refresh + 组件配置模式一致；源面板到目标面板的联动关系是面板级配置 |

## 九、D2-3 联动状态服务

### 9.1 filter_state 存储方案

**选择：新建 `NopDatavFilterState` 独立实体（方案 A）。**

理由：
1. **平台经审查不存在 user-preference / key-value 存储机制**——`grep -r "userPreference\|UserPreference\|user_preference\|IUserPreference"` 平台模块仅在 `nop-migration` 的 Ofbiz 迁移测试资源中有遗留引用，非平台 API。方案 B（平台 key-value）已排除。
2. **roadmap D2-3 验收参考 Superset filter_state API（后端持久化语义）**，需要后端保存/恢复能力；纯前端 localStorage 方案（方案 C）无法跨设备同步、无法服务端渲染。
3. **独立实体支持按 用户 + 看板 查询/删除/覆盖**，语义最直接。

### 9.2 NopDatavFilterState 实体约定

| 列名 | 用途 | 类型 |
|------|------|------|
| `stateId` | 主键 | VARCHAR（uuid） |
| `userName` | 用户标识 | VARCHAR（来自 `IServiceContext.getUserContext().getUserName()`，**不是数字 userId**——平台约定） |
| `dashboardId` | 看板 ID | VARCHAR |
| `stateContent` | 状态内容 JSON | CLOB（`clobJson`） |
| 标准审计列 | created/updated by/time/version/delFlag/remark | 同其他实体 |

唯一键：`(userName, dashboardId)`——一个用户一个看板对应一条 filter_state 记录（保存时覆盖旧记录）。

### 9.3 filter_state 内容契约

`stateContent` JSON 结构（行为规格）：

| 区域 | 用途 | 说明 |
|------|------|------|
| `globalFilters` | 全局筛选值 | 对象，key = 看板参数名（含扁平化复合 key 如 `dateRange.start`），value = 参数值。与 D2-1 `resolveFilterValues` 输出格式一致 |
| `panelSelections` | 面板联动选择 | 对象，key = 源面板 ID（联动选择的发起者），value = `{field, value}` 对象（被点击的字段名 + 值，支持单个字段+值） |
| `urlState` | URL 序列化形式 | 字符串，当前筛选状态的 URL 序列化（D2-1 URL 同步输出），用于快速分享/恢复 |

序列化/反序列化保持契约结构一致（往返一致）。filter_state 以单个看板为粒度保存/恢复。

### 9.4 API 契约（D2-3）

| Action | 类型 | 行为 |
|--------|------|------|
| `saveFilterState(dashboardId, globalFilters, panelSelections, urlState, context)` | `@BizMutation` | 按当前用户 userName + dashboardId 隔离保存/覆盖；BizModel 内部按内容契约序列化为 stateContent JSON 存储 |
| `getFilterState(dashboardId, context)` | `@BizQuery` | 按当前用户 userName + dashboardId 恢复；返回反序列化后的结构化 filter_state；**若该用户+看板无保存记录，返回 null**（调用方据此判断无已保存状态，非静默返回空对象） |

### 9.5 用户隔离

filter_state 按 `userName + dashboardId` 隔离：不同用户的 filter_state 互不干扰。当前用户 userName 从 `IServiceContext.getUserContext().getUserName()` 获取（平台约定，已核实 `NopDatavDashboardBizModel.resolveOperator` 使用 getUserName）。

### 9.6 错误路径（D2-3）

| 场景 | 错误码 |
|------|--------|
| filter_state 内容格式错误（JSON 解析失败、结构非对象） | `ERR_DATAV_INVALID_FILTER_STATE` |

均抛 `NopException` + `.param(...)`。「无保存记录」是合法分支（`getFilterState` 返回 null，不报错）。

### 9.7 拒绝的替代方案（D2-3 filter_state 存储）

| 方案 | 拒绝理由 |
|------|---------|
| 方案 B：复用平台 user-preference / key-value 存储机制 | 平台经审查不存在此类机制（grep 仅命中 nop-migration Ofbiz 迁移测试资源中的遗留引用），方案无对象 |
| 方案 C：前端 localStorage 持久化 | 无法跨设备同步、无法服务端渲染；与 Superset filter_state API 后端持久化语义不一致 |
| **采用：方案 A，新建 NopDatavFilterState 独立实体** | 支持 per-userName+dashboardId 查询/覆盖；语义最直接；与既有审计列模式一致 |

## 十、Non-Goals（D2 全阶段）

- 前端筛选控件渲染、联动交互控件渲染（D2-4，依赖 nop-chaos-flux）
- 跨看板联动（用跳转实现跨看板场景）
- filter_state 实时推送/WebSocket（按需 API 调用）
- 多看板 filter_state 聚合（以单个看板为粒度）
- 批量面板查询 API（优化项）
- nop-metadata 维度/度量字段映射元数据运行时解析（D1 deferred）
- 参数定义与 paramMapping source 的严格匹配校验（宽松兼容）
- 联动配置的可视化编辑 API（前端 D2-4 范围）
- 联动规则的全局配置时校验（当前为运行时校验）
