# nop-datav 运行时设计 (D1)

> Status: **final**
> Last Reviewed: 2026-08-10

## 概述

本文记录 nop-datav 看板运行时（D1）后端三项能力的架构决策：面板渲染协议（D1-1）、数据绑定管线（D1-2）、刷新机制（D1-3）。前端集成（D1-4）依赖 nop-chaos-flux 控件族，flux 侧未落地前为 out-of-scope。本模块只做模型侧解析 + 查询委托/回传，不做前端渲染、不重建数据源/数据集管理（复用 nop-report）、不重建维度/度量建模（复用 nop-metadata）。

## 一、面板渲染协议（D1-1）

### 组件注册表方案

**选择：在 `nop-datav-service` 的 `io.nop.datav.service.component` 包内通过接口注册表 + 代码构建。**

- 每种组件类型实现统一接口（`IPanelComponent`），暴露元信息：类型标识（String，如 `"chart"`/`"pivot-table"`）、显示名、是否需要数据集绑定（boolean）
- 组件注册表（`PanelComponentRegistry`）持有「类型标识 → 组件」Map，启动时由注册表实现内部静态初始化完成所有内置类型的注册
- 不使用 Nop IoC 的 `<bean>` 注入（避免每新增一种类型都要改 `_service.beans.xml`）；注册表是普通 Java 类，由 BizModel 在使用点直接 `new` 或通过静态单例访问

### 组件类型清单

注册表覆盖 7 种组件类型，类型标识稳定不变：

| 类型标识 | 显示名 | 需要数据集绑定 |
|---------|--------|---------------|
| `chart` | 图表 | 是 |
| `pivot-table` | 透视表 | 是 |
| `stat-tile` | 指标卡片 | 是 |
| `map` | 地图 | 是 |
| `table` | 表格 | 是 |
| `text` | 文本 | 否 |
| `iframe` | 内嵌页面 | 否 |

未知类型查询抛 `NopException`（`ERR_DATAV_UNKNOWN_COMPONENT_TYPE`），不返回 null（rule #24 无静默跳过）。

### panelType dict 与组件类型的映射

**`panelType` 保持 ORM `int` 类型不变**（避免列类型迁移）。dict 值与组件类型标识的映射在代码层维护：

| dict code | int 值 | 组件类型标识 | 说明 |
|-----------|--------|-------------|------|
| `CHART` | 0 | `chart` | |
| `TABLE` | 10 | `table` | |
| `METRIC` | 20 | `stat-tile` | 语义等价（METRIC = KPI 指标卡片 = stat-tile），不新增 STAT_TILE |
| `TEXT` | 30 | `text` | |
| `CONTAINER` | 40 | `container` | 布局容器，注册为无数据集组件，不参与查询委托 |
| `PIVOT_TABLE` | 50 | `pivot-table` | **新增**（D1 扩展） |
| `MAP` | 60 | `map` | **新增**（D1 扩展） |
| `IFRAME` | 70 | `iframe` | **新增**（D1 扩展） |

D1 扩展实际新增 3 个 dict 选项（PIVOT_TABLE/MAP/IFRAME），并复用 METRIC=20 表达 stat-tile。`CONTAINER=40` 不在 7 类查询组件中，作为布局容器单独存在。

### panelConfig 配置区域约定

`panelConfig` 是 `json-4000` 字段（VARCHAR 4000），存储任意 JSON。本设计为不同组件类型定义**结构化配置区域约定**（不新增 ORM 列；结构通过文档 + 运行时校验保证）。

各组件类型的 `panelConfig` 可包含以下配置区域（按需出现，未出现的区域视为默认值）：

| 区域 | 用途 | 适用组件类型 |
|------|------|-------------|
| `title` | 面板标题文案/样式 | 全部 |
| `fieldMapping` | 数据集字段到组件视觉通道的映射（x 轴/y 轴/分组/颜色/尺寸等） | chart / pivot-table / stat-tile / map / table |
| `styleOptions` | 组件级样式（颜色主题/字体/边框/背景） | 全部 |
| `dataBinding` | 数据绑定配置（引用 datasetRefId + 参数映射覆盖项） | 全部（text/iframe 仅 placeholder） |
| `refresh` | 刷新配置（`enabled` + `intervalSeconds`，见 §3） | 全部 |
| `content` | 静态内容（text 的 markdown/html 文本、iframe 的 URL） | text / iframe |

各区域的具体字段级定义属于组件实现层细节（由 nop-chaos-flux 控件族落地时定稿），本设计仅约束区域划分。

## 二、数据绑定管线（D1-2）

### 端到端流程

```
API getPanelData(panelId, requestParams)
  → [1] 加载 NopDatavPanel(panelId)
  → [2] 查询组件类型（注册表）判断是否需要数据集
         不需要（text/iframe） → 返回 {hasDataset:false}
  → [3] 加载 NopDatavDatasetRef(datasetRefId)
  → [4] 加载 NopReportDataset(refDatasetId)（来自 nop-report-dao）
  → [5] 校验 dsType == "sql"，否则抛 ERR_DATAV_UNSUPPORTED_DATASET_TYPE
  → [6] 参数求值（paramMapping + requestParams → finalParams）
  → [7] SQL 执行（IJdbcTemplate.findAll + ColumnMapRowMapper）
  → [8] 结果回传（List<Map<String,Object>> + 字段名列表 + hasDataset:true）
```

### 查询执行方式

**选择：直接读取 `NopReportDataset.dsText` 作为 SQL 文本，通过已注入的 `IJdbcTemplate` 执行。**

- 仅支持 `dsType = "sql"`：dsText 字段在 nop-report ORM 标注 `stdDomain="json"` + 绑定 JsonOrmComponent，但对于 sql 类型数据集，`getDsText()` 返回的 String 是纯 SQL 文本。若 dsText 非纯 SQL（如解析后是 JSON 包装），运行时显式报错。
- SQL 参数化：dsText 中的 `${paramName}` 占位符被解析为顺序参数，按参数求值结果通过 `SQL.begin().sqlWithParams(processedSql, paramValues)` 注入（避免字符串拼接 SQL 注入风险）。
- 不引入 nop-report-core 运行时（XPT 报表引擎、DynamicReportDataSet、IReportEngine）——它们绑定 xpt 模板渲染，不是 standalone 数据集查询。
- **多数据源路由为 Non-Goal**：忽略 `NopReportDataset.datasourceId`，所有查询走默认 querySpace。多数据源支持作为后续 enhancement。

### 参数求值（paramMapping）

`NopDatavDatasetRef.paramMapping` JSON 结构裁定：

```json
{
  "start_date": {"source": "startDate", "defaultValue": null},
  "category":   {"source": "category",  "defaultValue": "all"}
}
```

- 顶层 key = 查询参数名（对应 SQL 中 `${paramName}` 的占位符名）
- value = 映射规则对象，含 `source`（从 API 请求参数 Map 中取值的 key）和可选 `defaultValue`

**求值规则**：遍历 paramMapping 的每个 entry，从 API 请求参数 Map 中按 `source` 取值；未取到则用 `defaultValue`；defaultValue 缺省为 null。最终合成 `Map<String, Object>` 查询参数。仅出现在 paramMapping 中的参数会被传入 SQL（未声明的请求参数被忽略，避免 SQL 注入面扩大）。

### 模块依赖

`nop-datav-service` 仅引入 `nop-report-dao`（读取 NopReportDataset 配置），不引入 `nop-report-core`/`nop-report-service`（避免拉入 XPT 引擎和渲染器）。`INopDatavPanelBiz` 接口位于 `nop-datav-dao`，其方法返回类型只用 nop-datav 自有类型或平台基础类型（`Map<String,Object>`/`List<Map>`），不引用 nop-report 实体类型——保持依赖方向正确（service → dao，不反向）。

### 错误路径

| 场景 | 错误码 |
|------|--------|
| panelId 指向的面板不存在 | `ERR_DATAV_PANEL_NOT_FOUND` |
| datasetRefId 指向的 DatasetRef 不存在 | `ERR_DATAV_DATASET_REF_NOT_FOUND` |
| refDatasetId 指向的 nop-report 数据集不存在 | `ERR_DATAV_DATASET_NOT_FOUND` |
| dsType 非 sql | `ERR_DATAV_UNSUPPORTED_DATASET_TYPE` |
| 查询执行失败（SQLException 等） | `ERR_DATAV_QUERY_FAILED` |

均抛 `NopException` + `.param(...)`，不返回 null/空列表/placeholder（rule #24）。

## 三、刷新机制（D1-3）

### 刷新配置存储

**选择：刷新配置存储在 `panelConfig` JSON 的 `refresh` 区域，不新增 ORM 列。**

格式约定：

```json
{
  "refresh": {
    "enabled": true,
    "intervalSeconds": 30
  }
}
```

- `enabled`（boolean，缺省 false）：是否启用自动刷新
- `intervalSeconds`（int，缺省 0）：自动刷新间隔（秒）；0 或负值表示不自动刷新

参考 DataEase `refreshViewEnable`/`refreshUnit`/`refreshTime` 与 AJ-Report `refreshSeconds`。

### API 契约

| Action | 类型 | 行为 |
|--------|------|------|
| `getPanelData(panelId, requestParams)` | `@BizQuery` | 查询面板数据（首次/手动刷新共用，见 §2） |
| `refreshPanel(panelId)` | `@BizMutation` | 手动刷新：复用 getPanelData 内部管线重新执行查询并返回最新结果 |

后端不实现定时调度（自动刷新的实际触发由前端按 `intervalSeconds` 调用 `refreshPanel` 实现）。若后续需要服务端调度，归 D5 定时报告范围。

### 配置读取

提供 `PanelRefreshConfig parseRefreshConfig(String panelConfigJson)` 方法：从 panelConfig JSON 读取 `refresh.enabled` 和 `refresh.intervalSeconds`，供前端或调度层消费。

- panelConfig 为空或无 `refresh` 区域：返回 `enabled=false, intervalSeconds=0`（明确默认值，非静默忽略）
- panelConfig JSON 解析失败：抛 `NopException`（`ERR_DATAV_INVALID_PANEL_CONFIG`），不静默降级

## 四、拒绝的替代方案

### 查询执行方式

| 方案 | 拒绝理由 |
|------|---------|
| 集成 nop-report-core 的 XPT 引擎 / IReportEngine | 绑定 xpt 模板渲染，不是 standalone 数据集查询；引入庞大依赖且执行路径不直白 |
| 新建独立的 `IDataSetExecutor` 抽象层 + 多种执行器（sql/json/http） | 过度工程：D1 scope 内仅 sql 类型有需求，非 sql 类型显式拒绝即可（后续按需扩展） |
| **采用：直接读 `NopReportDataset.dsText` + `IJdbcTemplate`** | 复用已注入的 IJdbcTemplate；查询路径简短；dsType 非 sql 时快速失败 |

### 组件注册表方案

| 方案 | 拒绝理由 |
|------|---------|
| 枚举（enum）列举组件类型 | 简单但不可扩展；后续大屏/装饰组件族（D4）扩展需修改枚举源码 |
| Spring 风格 `@Component` 注解扫描 | Nop IoC 无注解扫描（见 AGENTS.md），不适用 |
| `<bean>` 注入到 `_service.beans.xml` | 每新增一种类型要改 beans.xml，与注册表自包含原则冲突 |
| **采用：接口注册表 + 代码构建（启动时静态初始化）** | 注册表自包含；新增类型只需实现接口 + 在注册表内登记一行 |

### panelType 类型变更

| 方案 | 拒绝理由 |
|------|---------|
| 改 panelType 为 string 类型 | ORM 列类型变更需迁移；现有数据兼容性差 |
| **采用：panelType 保持 int + dict 扩展** | 通过新增 dict 选项扩展类型集；int → 组件类型标识的映射在代码层 |

## 五、与 nop-report / nop-metadata 的边界

| 模块 | 关系 | 边界 |
|------|------|------|
| nop-report-dao | nop-datav-service 依赖；读取 `NopReportDataset.{sid, dsType, dsText}` | 仅消费数据集配置字段，不引入 report-core/service |
| nop-metadata | 不直接依赖；维度/度量字段映射元数据运行时解析 | D1 不实现字段映射元数据解析（Non-Goal），数据绑定管线直接返回数据集原始字段 |
| nop-chaos-flux | 不依赖；前端控件族与本设计互不感知 | 本设计仅做模型侧配置 + 数据供给；渲染走 flux renderers |

## 六、Non-Goals

- 多数据源路由（D1 假设走默认 querySpace；多数据源支持留待后续 enhancement）
- 前端渲染（chart/pivot-table/stat-tile/map 的渲染走 nop-chaos-flux renderers）
- 全局筛选与联动（D2）
- 权限/分享/导出（D3）
- 大屏自由画布/装饰组件/主题（D4）
- 定时报告/告警（D5）
- AI/ChatBI（D6）
- 重建数据源/数据集管理（复用 nop-report）
- 重建维度/度量建模（复用 nop-metadata）
- DatasetRef 与 nop-metadata 维度/度量的字段映射元数据运行时解析（预留扩展点，后续 enhancement）
- 自动定时刷新调度（前端按 interval 调用刷新 API 即可；服务端调度归 D5）
