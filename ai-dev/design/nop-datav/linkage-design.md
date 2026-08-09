# nop-datav 筛选与联动设计 (D2)

> Status: **final**（D2-1 部分；D2-2/D2-3 决策由后继 plan `2026-08-10-1030-2-chart-linkage-and-filter-state.md` 补充，当前仅占位说明）
> Last Reviewed: 2026-08-10

## 概述

本文记录 nop-datav 筛选与联动（D2）后端能力的架构决策。D2-1（全局筛选参数）已在本文件定稿；D2-2（图表联动三件套）、D2-3（联动状态服务）由后继 plan 落地时补充各自设计记录。

D2-1 的目标是：看板可声明命名/类型化的全局筛选参数，面板通过既有 D1 `paramMapping` 绑定到这些参数；改变全局筛选值即可影响所有绑定面板的查询结果；筛选状态可通过 URL 同步。本模块只做参数定义层 + 校验层 + URL 序列化层，**不重建查询引擎**（复用 D1 `getPanelData` + `PanelDataBinder` + `PanelParamEvaluator`）、不实现图表联动/跳转（D2-2）、不实现 filter_state 持久化（D2-3）、不做前端渲染（D2-4）。

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

## 八、D2-2 / D2-3 占位说明

以下设计决策由后继 plan `2026-08-10-1030-2-chart-linkage-and-filter-state.md` 补充，本文件不预先裁定：

- **D2-2 图表联动三件套**：联动（click chart → filter other panels）、跳转（click → navigate）、外部参数注入。参考 DataEase LinkageService/LinkJump/LinkOuterParams。联动触发后如何改变全局筛选值（是否复用 resolveFilterValues）待 D2-2 裁定。
- **D2-3 联动状态服务**：filter_state 保存/恢复。参考 Superset filter_state API。与 D2-1 的 URL 同步（无状态参数序列化）不同，filter_state 是持久化状态。是否复用 URL 序列化格式待 D2-3 裁定。

## 九、Non-Goals（D2-1）

- 图表联动、跳转、外部参数注入（D2-2）
- filter_state 持久化保存/恢复（D2-3）
- 前端筛选控件渲染（D2-4）
- 批量面板查询 API（优化项）
- nop-metadata 维度/度量字段映射元数据运行时解析（D1 deferred）
- 参数定义与 paramMapping source 的严格匹配校验（宽松兼容）
