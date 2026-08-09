# nop-datav 功能设计分析（参考 9 个开源 BI 系统）

> Status: resolved
> Date: 2026-08-09
> Scope: nop-datav（nop-entropy 可视化/大屏模块）功能域设计；参考 DataEase/Grafana/Metabase/Superset/Redash/JimuReport/AJ-Report/DataRoom/SpringReport 本地源码
> Conclusion: nop-datav 定位为"BI 看板/大屏运行时 + 管理"（数据源/数据集/查询由 nop-report + nop-metadata + EQL 覆盖）；核心功能域 = 看板模型 + 面板/组件模型 + 数据绑定 + 全局筛选联动 + 权限分享导出 + 大屏能力；参考实现聚焦 DataEase（一体）与 Grafana（面板体系）

## Context / Background

nop-entropy 的 `nop-datav` 目前是空壳（仅 pom.xml，无任何类）。BI 后端能力已大部就绪：`nop-metadata`（维度/度量/数据源/表关联建模，404 Java 文件）、`nop-report`（Xpt 报表引擎 + 数据集 + 数据源管理 API）、`nop-orm-eql`（EQL 查询）、`nop-auth`（权限）、`nop-job`（调度）、`nop-batch`（批处理）。本分析回答：nop-datav 应该实现哪些功能域、边界在哪、参考哪些系统的做法。

## 功能域分析（9 系统调研结论）

### 核心对象链路（心智模型）

| 系统 | 数据链路 | 核心存储模型 |
| --- | --- | --- |
| DataEase | 数据源 → 数据集(直连/抽取) → 图表视图 → 仪表板/大屏 | `core_datasource`/`core_dataset_*`/`core_chart_view`/`data_visualization_info` |
| Grafana | 数据源 → 面板 targets → 面板插件 → Dashboard | Dashboard JSON（面板 `type+targets+gridPos`） |
| Metabase | 数据源 → Question(Card) → DashboardCard → Dashboard | `report_card`/`report_dashboard`/`report_dashboardcard` |
| Superset | 数据库 → Dataset(物理/虚拟) → Chart → Dashboard | `tables`/`slices`/`dashboards(position_json)` |
| Redash | 数据源 → Query → Visualization → Widget → Dashboard | `queries`/`visualizations`/`widgets`/`dashboards` |
| JimuReport/AJ-Report/DataRoom | 数据源 → 数据集 → Widget → 大屏页面 | 数据源/数据集/组件 JSON |

### 一、数据源管理（nop-datav 不做——已覆盖）

- 参考广度：Redash 插件化 ~70 种；AJ-Report/DataRoom 国产库覆盖最全（达梦/金仓/GBase/OceanBase/TiDB/StarRocks）；DataEase 11 JDBC + API + Excel + 自定义驱动上传。
- 安全做法（参考）：DataEase `JdbcUrlSecurityPolicy` 防 SSRF、Redash Fernet 加密存储、JimuReport AES 密码 + 连接失败计数。
- **结论**：nop-report 已有 `NopReportDatasourceApi` 数据源管理，nop-datav 直接复用，不重复建设。

### 二、数据集/查询层（nop-datav 复用 + 轻扩展）

- 参考模型：Superset 物理/虚拟表 + `sql_metrics`；Metabase Card(model) + `result_metadata`；DataEase 三级树（分组→表→字段）+ 直连/抽取双模式 + 跨源 union；DataRoom 类型化数据集（sql/json/http/excel/es/websocket/mqtt，流式独有）；AJ-Report 数据集参数（必填/校验/样例值）+ transform。
- 参数化：Metabase template-tag（`{{var}}`）、Redash `{{param}}`、Grafana 时间宏 + 模板变量、Superset jinja。
- 缓存：Metabase ttl/kb、Redash query_hash 复用、Superset 双层缓存。
- **结论**：nop-report 数据集 + EQL 已覆盖查询；nop-datav 需要的是"数据集引用 + 面板级参数传递"的**绑定层**（面板 → 数据集 → 参数），参考 AJ-Report `data_set_param` 与 Metabase template-tag。

### 三、图表/可视化（nop-datav 编排，不重造渲染）

- 参考图表族：DataEase 分族（柱 8/线 3/饼/散点/地图 5/混合 4/表格 4/其他 8）；Grafana 29 面板插件；Metabase 20+；DataRoom 组件库 ~40（含 3D/视频/装饰）。
- 配置方式：DataEase `core_chart_view` xAxis/yAxis 字段映射 + drillFields 下钻；JimuReport echarts option JSON 透传 + dataMapping；Grafana 数据转换管线。
- **结论**：渲染层走前端（nop-chaos-flux renderers：chart/pivot-table/stat-tile/map 等）；nop-datav 提供**组件注册表 + 字段映射 + 数据绑定配置**（模型侧），参考 JimuReport 的 option 透传 + DataEase 字段映射。

### 四、仪表盘/看板（nop-datav 核心域）

- 布局：Grafana `GridPos{x,y,w,h}` + RowPanel + 面板时间覆盖；Metabase grid + dashboard_tab 页签；Superset position_json；DataEase 自由画布 JSON + 快照发布；大屏系 x/y/w/h + 辅助线/标尺/对齐/分组（DataRoom 设计器最全）。
- 筛选联动（三件套）：Metabase parameters → parameter_mappings（映射到卡片字段）；DataEase LinkageService（图表联动）+ LinkJump（跳转）+ OuterParams（外部参数）；Superset filter_state 保存恢复 + cross-filter；Grafana templating + URL 同步；DataRoom 全局变量 + 行为事件 + 组件动作。
- 刷新：Grafana dashboard refresh + interval；DataEase refreshViewEnable/Unit/Time；AJ-Report 组件级 refreshSeconds；Redash 查询级 schedule。
- **结论**：nop-datav 看板模型 = 布局（网格+自由双模式）+ 面板 + 页签 + 全局筛选参数 + 面板级刷新 + 发布/快照。参考 Grafana 模型清晰度 + Metabase 参数映射 + DataEase 联动三件套。

### 五、大屏能力（nop-datav 差异化域）

- DataEase：装饰组件（de-decoration/video/time-clock/stream-media/scroll-text）+ Tab 轮播 + 屏幕适配（heightFirst/full/keep）+ 模板市场。
- JimuReport：多端设计（PC/手机/平板）+ 主题色板 + 轮播 + 水印 + 模板 + 访问计数。
- DataRoom：标尺/对齐/分组/快捷键设计器 + 3D + AI 生成大屏（MCP Tool）+ 发布/暂存/历史/缩略图。
- **结论**：大屏 = 自由画布 + 装饰/媒体组件族 + 轮播 + 屏幕适配 + 主题。nop-datav 命名含 datav，大屏是重要差异化，但优先级低于看板核心。

### 六、权限与协作（复用 nop-auth）

- 参考：Metabase 权限图谱（细到表/行）+ Superset RLS（行级安全）；AJ-Report target+action 细粒度；SpringReport 报表区域权限（单元格级）。
- 分享：AJ-Report `report_share`（shareCode/有效期/密码/token）最标准；Metabase public_sharing + embedding；DataEase share 模块（token/密码/有效期）。
- 导出：DataEase 导出中心（异步任务 + 限额）；Superset PDF/PNG/CSV 报告；JimuReport 导出 PDF/Excel/Word 按钮级。
- **结论**：看板级权限（角色/数据权限/行级）+ 分享（链接/密码/有效期）+ 导出（异步，复用 nop-report 导出管线）。权限体系复用 nop-auth，仅新增资源模型。

### 七、其他

- 告警/调度（参考 Grafana ngalert/Redash Alert/Superset 定时报告）：nop-datav 可复用 nop-job 做"定时刷新/定时报告"，告警做轻量版（阈值条件 + 通知）。
- AI/ChatBI（DataEase SQL 助手/Metabase Metabot/DataRoom MCP）：**后置**，AI 生态已有 nop-ai，接入路径现成。
- 填报/协同/流式（JimuReport/SpringReport/DataRoom）：锦上添花，不做首版。

## 核心必备 vs 锦上添花（nop-datav 取舍）

**核心必备（M1+M2 范围）**：
1. 看板模型：布局（网格/自由）+ 面板 + 页签 + 发布/快照
2. 面板模型：组件注册表 + 数据绑定（数据集 + 参数）+ 字段映射 + 刷新
3. 全局筛选参数 + 面板联动（参数映射三件套）
4. 权限（角色 + 数据权限）+ 分享（链接/密码/有效期）+ 导出（异步）
5. 与 nop-report 数据集、nop-metadata 维度/度量打通

**锦上添花（M3+）**：
1. 大屏：自由画布设计器 + 装饰/媒体组件 + 轮播 + 屏幕适配 + 主题
2. 定时报告/轻量告警（nop-job 复用）
3. AI/ChatBI（nop-ai 接入）
4. 流式数据集、模板市场、填报

## 与既有模块的边界（复用 vs 新建）

| 能力 | 归属 | 说明 |
| --- | --- | --- |
| 数据源管理 | nop-report（复用） | `NopReportDatasourceApi` 已有 |
| 数据集/查询 | nop-report + EQL（复用） | 数据集 + 参数化 + 缓存 |
| 维度/度量建模 | nop-metadata（复用） | 面板字段映射的元数据来源 |
| 渲染（图表/透视/KPI/地图） | nop-chaos-flux（前端） | nop-datav 不重造渲染 |
| **看板/面板/组件模型 + 数据绑定 + 联动** | **nop-datav（新建）** | 本模块核心 |
| 权限/分享/导出 | nop-auth + nop-report 导出（复用） | 仅新增资源模型 |
| 调度 | nop-job（复用） | 定时刷新/报告 |
| 大屏设计器 | nop-datav（新建，M3） | 自由画布 + 装饰组件 |

## 结论

nop-datav 的合理边界 = **BI 看板/大屏的模型层 + 运行时编排**，不做数据源/数据集/渲染引擎（均已覆盖）。参考优先级：核心模型照 Grafana/Metabase（清晰 + 参数映射），一体功能照 DataEase（联动三件套 + 大屏），实现细节照 AJ-Report/DataRoom（组件 JSON + 设计器）。分 M1（看板核心模型+运行时）→ M2（联动+权限分享导出）→ M3（大屏+报告+AI）三期推进，详见同日 roadmap。

## References

- 本地源码：`~/sources/bi/{dataease,grafana,metabase,superset,redash,JimuReport,springreport}`、`~/sources/bi/aj-report`、`~/sources/bi/dataroom`
- nop-entropy：`nop-report/nop-report-api`（数据集/数据源 API）、`nop-metadata`（维度/度量模型）、`nop-persistence/nop-orm-eql`
- 前端配套：`nop-chaos-flux` 的 `docs/analysis/2026-08-09-bi-control-support-analysis.md`（BI 控件缺口）、dashboard editor/pivot-table/map/sparkline 计划
