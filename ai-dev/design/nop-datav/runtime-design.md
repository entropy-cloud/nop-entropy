# nop-datav 运行时设计 (D1)

> Status: **final**
> Last Reviewed: 2026-08-15

## 概述

本文记录 nop-datav 看板运行时（D1）后端能力的架构决策：面板渲染协议（D1-1）、数据绑定管线（D1-2）、刷新机制（D1-3）、批量面板查询（D1-2/D2-1 deferred follow-up）。前端集成（D1-4）依赖 nop-chaos-flux 控件族，flux 侧未落地前为 out-of-scope。本模块只做模型侧解析 + 查询委托/回传，不做前端渲染、不重建数据源/数据集管理（复用 nop-report）、不重建维度/度量建模（复用 nop-metadata）。

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

## 四、批量面板查询 getDashboardData（D1-2/D2-1 deferred follow-up）

单次调用取回整个看板（或指定 panelIds 子集）各面板数据的批量查询 API，消除「N 个面板 = N 次 GraphQL 往返」的渲染路径开销。逐面板 `getPanelData` 仍为基础查询模型，本 API 是其上层优化——两者共用同一 `PanelDataBinder` 数据绑定管线，不重复实现查询逻辑。

### 4.1 action 归属（D1 裁定）

**选择：放 `NopDatavDashboardBizModel`（看板视角），不放 `NopDatavPanelBizModel`。**

- 输入（dashboardId + 可选 panelIds）与输出（按看板聚合的面板结果列表）均以看板为单位
- 看板级筛选一次求值发生在看板上下文（paramConfig 挂在 Dashboard 主表），归属看板 BizModel 语义最直接
- 权限点 `NopDatavDashboard:getDashboardData`，角色绑定镜像 `NopDatavPanel:getPanelData`（admin,user）

### 4.2 面板集合语义（D2 裁定）

- 默认纳入**看板全部面板**（按 `sortOrder` 排序加载，与 `exportDashboard` 的加载先例一致）
- 可选 `panelIds` 参数过滤为子集；返回条目顺序跟随面板 `sortOrder`（不跟随 panelIds 传入顺序），`panelIds` 内重复 id 去重
- **panelIds 中不存在或不属于该看板的 id → 整体显式报错**（`ERR_DATAV_PANEL_NOT_IN_DASHBOARD`），禁止静默忽略不标注

### 4.3 响应形态与面板纳入集（D3 裁定）

两级失败语义必须可区分：

| 失败级别 | 场景 | 行为 |
|---------|------|------|
| 看板级失败 | 看板不存在 / panelIds 越权引用 / 面板数超上限 / 筛选参数类型不匹配 | 整体抛 `NopException`（请求前段 fail-fast，不产出部分响应） |
| 面板级失败 | datasetRef 失效 / nop-report 数据集不存在 / dsType 非 sql / SQL 执行失败 / 未知组件类型 | 该面板条目携带 `success=false` + `errorCode` + `errorMessage`，其余面板正常返回 |

- **仅 `NopException` 被捕获为面板级失败**（`PanelDataBinder` 全部错误路径均抛 NopException，含包装后的 `ERR_DATAV_QUERY_FAILED`）；非 NopException 的意外 RuntimeException 视为系统性故障按看板级失败传播（不吞掉）
- 响应包含看板全部面板，**含无数据集面板**（text/iframe 等返回 `hasDataset=false` 条目）——与逐面板语义一致，与 `exportDashboard` 的排除语义**有意区分**（导出无数据集面板无意义，批量查询需要前端拿到完整面板清单）

响应 DTO 形态（落 `nop-datav-dao` 的 `io.nop.datav.biz` 包，镜像 `PanelDataResult`/`LinkageResult` 先例，仅用 nop-datav 自有类型与平台基础类型）：

```
DashboardDataResult
  ├─ dashboardId: String
  └─ panels: List<DashboardPanelDataItem>
        ├─ panelId: String
        ├─ componentType: String
        ├─ success: boolean            // 面板级失败标志（看板级失败不会走到这里）
        ├─ hasDataset: boolean         // success=true 时有意义；无数据集面板恒 false
        ├─ columns: List<String>       // success=true 且 hasDataset=true 时有意义，否则空列表
        ├─ rows: List<Map<String,Object>>
        ├─ errorCode: String           // success=false 时为 NopException 错误码字符串，否则 null
        └─ errorMessage: String        // success=false 时为异常消息，否则 null
```

### 4.4 上限与执行模式（D4 裁定）

- 面板数量上限：配置项 `nop.datav.dashboard-query.max-panels`（`NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS`，默认 50）。纳入集面板数超上限 → 显式拒绝（`ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED`），且校验发生在任何面板查询执行之前（防单请求放大为海量 SQL）
- 执行模式：原裁定「N 个面板顺序执行」**已被 §八 supersede**（plan `2026-08-15-0004-3` 落地有界并行）——现行行为：默认有界并行执行（`nop.datav.dashboard-query.parallel.enabled`，默认 true，详见 §8.1），开关关闭或面板数 ≤1 时回落本节顺序路径。两种模式下 D3/D4 语义不变式逐条等价（仅 NopException 为面板级失败、条目顺序跟随 sortOrder、无数据集面板条目保留、上限校验先于任何查询）

### 4.5 数据源与发布状态（D5 裁定）

- 批量入口读 **live 表**（getPanelData 加载 live panel，不检查 publishStatus；组合语义锚定 live 表），`requireEntity` 做看板存在性校验
- 发布（PUBLISHED）不是前置条件；读已发布快照的批量查询不属于本 API（如需另立）

### 4.6 筛选参数语义

`getDashboardData(dashboardId, params, panelIds?)` 的 `params` 即原始扁平 key 筛选值 Map（与 `resolveFilterValues` 的 `filterValues` 入参同构）。API 内部：`DashboardParamParser.parse(paramConfig)` → `DashboardFilterResolver.resolve(definitions, params)` **一次求值** → 生效参数统一作为每个面板 `PanelDataBinder.queryPanelData` 的 requestParams。与「逐面板 `resolveFilterValues` + `getPanelData` 组合」在参数流上结构等价（resolver 是纯函数，一次求值与 N 次求值结果一致）。

### 4.7 API 契约

| Action | 类型 | 行为 |
|--------|------|------|
| `getDashboardData(id, params, panelIds?, context)` | `@BizQuery` | 校验看板存活（requireEntity）→ 筛选一次求值 → 按 §4.2 纳入集加载面板（含上限校验）→ 逐面板复用 `PanelDataBinder` → 按 §4.3 形态聚合响应 |

### 4.8 错误路径（批量层新增）

| 场景 | 错误码 |
|------|--------|
| panelIds 引用不存在/不属于该看板的面板 | `ERR_DATAV_PANEL_NOT_IN_DASHBOARD` |
| 纳入集面板数超过 max-panels 上限 | `ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED` |

看板不存在由 `requireEntity` 抛平台实体缺失错误（`nop.err.dao.unknown-entity`，与既有 `getPanelData` 等 action 同模式）；筛选类型不匹配复用 `ERR_DATAV_PARAM_TYPE_MISMATCH`；面板级失败复用 `PanelDataBinder` 既有错误码（以条目内 `errorCode` 回传，不抛出）。

## 五、拒绝的替代方案

### 查询执行方式

| 方案 | 拒绝理由 |
|------|---------|
| 集成 nop-report-core 的 XPT 引擎 / IReportEngine | 绑定 xpt 模板渲染，不是 standalone 数据集查询；引入庞大依赖且执行路径不直白 |
| 新建独立的 `IDataSetExecutor` 抽象层 + 多种执行器（sql/json/http） | 过度工程：D1 scope 内仅 sql 类型有需求，非 sql 类型显式拒绝即可（后续按需扩展） |
| **采用：直接读 `NopReportDataset.dsText` + `IJdbcTemplate`** | 复用已注入的 IJdbcTemplate；查询路径简短；dsType 非 sql 时快速失败 |

### 批量查询：并行执行 vs 顺序执行

> **Supersession**：下表为批量查询 plan（2026-08-14-2020-2）时点的裁定。并行执行已由 §八
> （plan `2026-08-15-0004-3`）落地，本表「拒绝并行」结论**不再有效**，保留仅为决策历史索引。
> 现行终局裁定：默认有界并行执行（§8.1），连接池占用与并发语义风险面已由 §8.2 安全边界裁定收敛。

| 方案 | 拒绝理由 |
|------|---------|
| 并行执行（线程池化面板查询） | 消除的是往返开销，顺序执行已满足 Purpose；并行引入连接池占用与并发语义新风险面，独立评估 |
| **采用：顺序迭代（对齐 exportDashboard 先例）** | 语义与逐面板调用完全一致；实现简单；无并发副作用 |

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

## 六、与 nop-report / nop-metadata 的边界

| 模块 | 关系 | 边界 |
|------|------|------|
| nop-report-dao | nop-datav-service 依赖；读取 `NopReportDataset.{sid, dsType, dsText}` | 仅消费数据集配置字段，不引入 report-core/service |
| nop-metadata | 不直接依赖；维度/度量字段映射元数据运行时解析 | D1 不实现字段映射元数据解析（Non-Goal），数据绑定管线直接返回数据集原始字段 |
| nop-chaos-flux | 不依赖；前端控件族与本设计互不感知 | 本设计仅做模型侧配置 + 数据供给；渲染走 flux renderers |

## 七、Non-Goals

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

## 八、并行执行与结果缓存（D1-2 deferred follow-up；supersede §4.4 顺序执行裁定与 §五并行拒绝裁定）

对 §四 批量查询 API `getDashboardData` 的性能收口：面板查询从「N 面板 = N 次串行 SQL」升级为有界并行执行，并对重复查询（相同数据集 + 相同求值参数 + 相同行数约束）引入进程内结果缓存。两项均由批量查询 plan（2026-08-14-2020-2）显式 defer 至本节。语义不变式（D3/D4）在并行与缓存下保持：仅 NopException 为面板级失败条目、条目顺序跟随 sortOrder、无数据集面板条目保留、上限校验先于任何查询。

### 8.1 并行执行模型（P1 裁定）

- 执行器：复用平台共享工作线程池 `GlobalExecutors.globalWorker()`（daemon 线程、平台统一生命周期管理、容量由 `nop.commons.concurrent.global-worker.maxPoolSize` 约束，默认 30）。不为本功能新建线程池——先例 `NopDatavExportTaskBizModel` / `ReportDeliveryExecutor` 的异步执行均提交 globalWorker。无每请求 executor 创建/销毁，无线程泄漏面。
- 请求内并行度：`nop.datav.dashboard-query.parallelism`（默认 4）为请求内信号量许可数；任务在执行体内进入查询前获取许可、finally 释放——排队任务不占许可，单请求同时在飞查询上界 = parallelism。
- 错误归集（并发下保持 D3 两级失败语义）：面板任务内捕获 NopException → 面板级失败条目（与顺序版同构）；非 NopException → 任务以异常完成，请求线程 join 全部任务后按面板顺序（sortOrder）重抛第一个非 NopException 异常（看板级失败传播，确定性）。执行器拒绝任务（RejectedExecutionException）与 join 中断均显式传播，无任务静默丢弃导致的条目缺失。
- 结果重排：任务结果按面板下标归位，响应条目顺序恒等于顺序版（sortOrder）。
- 测试可观测 seam（支撑确定性断言，非计时推断）：
  - 面板任务装饰器 hook（可设置，生产恒空）：测试注入 latch/并发计数器，证明 ≥2 面板查询重叠执行且最大在飞数 ≤ parallelism
  - 执行器 getter（默认返回 `GlobalExecutors.globalWorker()`）：跨请求 identity 断言证明共享复用，无每请求新建/关闭
- 开关：`nop.datav.dashboard-query.parallel.enabled`（默认 true）。关闭（或面板数 ≤1）时走原顺序路径，与现状逐条等价。

### 8.2 并行安全边界（P2 裁定）

- `PanelDataBinder` 每请求单实例、全面板任务共享：字段全 final、无可变实例状态，并发调用安全；`PanelComponentRegistry` / `PanelTypeMapping` 静态初始化后只读（不可变 Map），线程安全。
- dao 读路径在 worker 线程安全：面板查询链路仅含读操作（DatasetRef / NopReportDataset 的 `getEntityById` + `jdbcTemplate.executeQuery`，无 session 写）。**worker 任务整体以 `ormTemplate.runInNewSession` 包裹**——平台 worker 线程 DB 访问先例（`NopDatavExportTaskBizModel.submitExecution` / `ReportDeliveryExecutor.execute`）：session 及其事务注册随任务开闭，杜绝 worker 线程残留事务状态；任务内全部 dao/jdbcTemplate 调用复用同一 session（避免逐 dao 调用反复开闭 session）。
- thread-local 上下文传播（必须）：dao 层消费 `ContextProvider.currentTenantId()`（`GenSqlHelper` 租户列过滤、`TenantAwareOrmModelProvider`、`TenantOrmSessionEntityCache`），`JdbcHelper.getQueryTimeout` 消费 callExpireTime（`JdbcHelper.java:250`）。裁定：**每面板任务新建 context 并从调用方 context 拷贝 tenant/locale 等属性**（`ContextProvider.propagateContext`），经 `IContext.executeWithContext`（`IContext.java:132`）绑定执行——tenant/locale/超时语义与顺序版一致；**禁止多 worker 共享调用方同一 context 对象**：平台 `TransactionRegistry` 挂在 context 上（`TransactionRegistry.instance()` 经 `getOrCreateContext()` 定位），并发共享同一 context 会导致事务注册表交错损坏（平台对同 context 并发执行有显式 WARN `nop.warn.context.concurrent-execute-with-same-context`）。
- 资源上界：单请求并发连接占用 = parallelism（默认 4）× 并发请求数，且受 globalWorker 容量（默认 30）总约束；`max-panels`（默认 50）约束任务总数（排队任务不占连接/许可）。

### 8.3 查询结果缓存形态（P3 裁定）

- 事实：nop-report 无数据集查询缓存抽象（全 nop-report 仅有 `XptRuntime` 公式缓存，与数据集查询无关，已核实）；nop-datav-service 依赖集刻意收窄为 nop-report-dao。roadmap 原「数据集查询缓存复用 nop-report」为空洞前提，已纠正为：nop-datav 批量路径查询结果缓存按 nop 标准缓存抽象（`io.nop.commons.cache`）实现，不复用也不重建 nop-report 缓存内核。
- 形态：平台 `LocalCache` + `CacheConfig`（进程内、Caffeine 内核、单节点语义；模块先例 `NopDatavShareAccessGuard`）。缓存组件位于 nop-datav-service 查询包，容量/TTL/准入由 §8.5 配置驱动。
- 接入点：固定为 `getDashboardData` 批量路径。实现为 `PanelDataBinder` 增加「可选缓存参数」重载（不传 = 不缓存），既有调用方（getPanelData / refreshPanel / exportDashboard / AlertEvaluator）不传缓存、行为逐字节等价；缓存键在 binder 参数求值之后构造，不复制求值逻辑（与 §四「共用同一 PanelDataBinder 管线」契约一致）。
- 集群边界：进程内缓存为单节点语义——每节点独立缓存与 TTL 计时，无跨节点失效/一致性；集群部署下 staleness 上界仍为各节点本地 TTL。分布式缓存为 Non-Goal。

### 8.4 缓存键与失效契约（P4 裁定）

- 键组成 = `refDatasetId`（数据集身份）+ 求值后参数（paramMapping × 请求参数的求值终值，含 defaultValue 展开）+ rowLimit（行数约束；批量路径恒为不限制）。求值结果 Map 顺序由 paramMapping 决定 → 序列化确定，无键碰撞；componentType / panelId 不入键（SQL 结果与面板呈现无关；命中后以当前面板的 panelId/componentType 重建响应字段，rows/columns 复用缓存实例）。同看板多面板共享同数据集同参数 → 命中同一缓存条目（请求内去重为预期特性而非碰撞）。
- 失效策略 = TTL-only（expireAfterWrite）。nop-report 侧无数据集实体变更事件机制（已核实，平台亦无现成跨模块事件先例）→ 显式降级为 TTL 契约：**数据集配置（SQL 文本等）与业务数据在 TTL 窗口内的变更对 `getDashboardData` 不可见，staleness 上界 = TTL（默认 30s）**。
- 无数据集面板条目：不缓存（零成本重建，每次重算）。面板级失败条目：不缓存——缓存失败会把瞬时 SQL 故障在 TTL 内固化（可用性劣化）；失败面板每次直查，故障恢复即自愈，与「缓存永远不让错误固化」取向一致（失败路径 binder 抛 NopException，本就不产生可缓存值，缓存仅在成功路径写入）。
- 缓存读写异常：缓存基础设施故障显式 WARN 记录并降级直查——绝不因缓存故障返回错误数据（正确性优先于可用性增益，非吞异常）。
- 缓存值共享语义：`PanelDataResult` 按不可变契约对待（columns/rows 为 unmodifiable 包装，仓库内调用方仅序列化消费、不变更），命中返回共享实例不做深拷贝。

### 8.5 配置项与默认值（P5 裁定）

| 配置项 | 默认 | 语义 |
|--------|------|------|
| `nop.datav.dashboard-query.parallel.enabled` | true | 并行开关；关闭时走原顺序路径，与现状逐条等价 |
| `nop.datav.dashboard-query.parallelism` | 4 | 请求内并行度上界（<1 视为 1） |
| `nop.datav.dashboard-query.cache.enabled` | false | 缓存开关（保守默认关：TTL 缓存引入用户可见 staleness，是否接受属部署决策，opt-in） |
| `nop.datav.dashboard-query.cache.ttl-seconds` | 30 | 缓存 TTL（staleness 上界） |
| `nop.datav.dashboard-query.cache.max-entries` | 200 | 缓存条目容量上界（容量驱逐） |
| `nop.datav.dashboard-query.cache.max-rows-per-entry` | 1000 | 单条目行数准入上界：结果超限不缓存（每次直查），不截断——绝不从缓存返回截断数据 |

- 单条目体量上界：运行时批量路径 rowLimit=null 行数无界 → 以 max-rows-per-entry 为缓存准入门槛，内存上界 = max-entries × max-rows-per-entry × 行体量，显式有界（默认配置约 200×1000 行量级，几十 MB 量级 worst case）。
- 默认值取向：并行默认开（语义等价性有 focused tests 证明，无行为差异，直取 Purpose 收益）；缓存默认关（staleness 为用户可见语义变化，保守 opt-in）。TTL / max-entries / max-rows-per-entry 变更在缓存实例重建后生效（进程重启或测试重置钩子）。

### 8.6 拒绝的替代方案

| 方案 | 拒绝理由 |
|------|---------|
| 每请求/全局新建专用线程池 | 引入线程生命周期管理面；平台 globalWorker 已有界且被既有异步路径复用（先例 NopDatavExportTaskBizModel / ReportDeliveryExecutor） |
| 复用 nop-report 缓存抽象 | 不存在（nop-report 仅有 XPT 公式缓存，与数据集查询无关，已核实）；重建 nop-report 缓存内核为 roadmap 禁止项 |
| 缓存接入 PanelDataBinder 全路径（含 getPanelData/refreshPanel/exportDashboard/AlertEvaluator） | 改变告警路径查询语义——告警的缓存 staleness 是正确性问题而非优化（plan Non-Goal 显式排除） |
| single-flight 同键并发 miss 去重 | 会把 SQL 执行纳入缓存加载函数、同键串行化、异常传播语义复杂化；并发重复 miss 已受 parallelism 约束，代价可接受 |
| 截断超限结果入缓存 | 缓存返回截断数据 = 返回错误数据；正确性优先（裁定为超限不缓存而非截断） |
| 分布式缓存（Redis 等进程外缓存） | Non-Goal（引入部署依赖；单节点 TTL 语义已覆盖 Purpose） |
