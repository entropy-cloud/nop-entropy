# 2364 WI6 ChartModel→ECharts option 适配器与 JFreeChart 删除

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI6（M3）；依赖 plan 2363（WI5 已完成，commit b7cdfe4fa7）
> Related: plan 2362（EChartsRenderer 引擎）、plan 2365（WI7 SPI 接线将消费本计划的适配器）、plan 2367（WI9 对版验收复用适配器全链路）

## Purpose

落地 roadmap WI6：以 /nop/schema/excel/chart.xdef 为映射规范实现 ChartModel→ECharts option 适配器（10 种 ChartType 全覆盖），nop-chart-export 壳保留、内脏从 JFreeChart 换为 nop-chart-echarts 引擎；契约（IChartExporter/ChartExportOptions/ChartExportErrors/Registry/ChartDataFilter）保留；既有测试回归语义迁移；jfree 足迹全量删除。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ b7cdfe4fa7）核验（R1 审查后含实测校正）：

- **jfree 足迹实测**：src 内 22 个 main 文件 + 1 个 test 文件 import org.jfree。main 22 = ChartExporter（重写不删除）+ IChartTypeRenderer（接口签名返回 JFreeChart）+ renderer/ 15 个（Abstract/Area/Bar/Bubble/ChartAxis/ChartLegend/ChartTitle/Combo/Doughnut/Heatmap/Line/Pie/Radar/Scatter/TrendLine）+ style/ 3 个（ChartShapeStyleProcessor/ChartStyleApplier/ChartTextStyleProcessor）+ layout/ManualLayoutProcessor + utils/JFreeChartStyleAdapter。**净删除 = 21 main + 1 test + docs/jfreechart-code-examples.md**；roadmap 字面「24 个」执行时同步校正为实测口径（21 删 + 1 重写 + 1 test + 1 docs）。
- **保留契约零 jfree 耦合**：IChartExporter（5 个 default 方法：exportToImage×2/exportToStream×1/exportToFile×2 + 抽象 exportToStream）、ChartExportOptions（11 字段）、ChartExportErrors（8 个错误码 + ARG 常量）、IProgressCallback、ChartDataValidator、filter/ChartDataFilter。
- **ChartTypeRendererRegistry** 自身无 org.jfree import，但 registerDefaultRenderers() 注册 10 个 jfree renderer；存储 ConcurrentHashMap<ChartType, IChartTypeRenderer>；getRenderer(ChartType) 不支持类型抛 ERR_UNSUPPORTED_CHART_TYPE。**编译依赖**：ChartExporter.renderChart() 以 IChartTypeRenderer 调用 renderer——registry 契约切换、ChartExporter 重写、旧 renderer 删除三者必须在同一编译单元变更内原子完成（R1 B1，时序见 Phase 划分）。
- **数据面实测（R1 M1 校正，关键）**：io.nop.excel.resolver.ChartDataResolver.INSTANCE.resolveSeriesData(plotArea, resolver) → List<ChartDataSet> 只填 name/categories/values；xValues 仅当 series 级 type==SCATTER/BUBBLE 才填，而 **xlsx 解析链从不设置 series 级 type**（DrawingChartParser 只设 chart 级 type）→ 真实 fixture 的 scatter x 值落在 catCellRef→categories；**bubbleSizes/heatmapValues 全仓库无生产者**（grep setBubbleSizes/setHeatmapValues 仅测试与拷贝路径）。适配器的散点/气泡/热力数据语义必须在适配层裁定（D35）。
- **scatter 数据源实测（执行中校正）**：fixture scatter（chart3/4）的 c:xVal 为 numLit 内联字面量（非单元格引用），ChartSeriesParser.parseScatterSeriesData 仅提取 c:f 引用 → 真实链 scatter categories 为空；D35 据此增补「categories 全空 → index 兜底」裁定（非数值 category 仍 fail-fast）。
- **grouping/形态实测**：ChartBarGrouping 4 值 CLUSTERED/STANDARD/STACKED/PERCENT_STACKED（STANDARD 为 clustered 兼容别名，javadoc 自述）；fixture bar×6 = clustered×2/stacked×2/percentStacked×2，line×2 与 area×1 的 XML grouping=standard，area 另有 percentStacked×1；**现行 jfree Bar/Area renderer 完全未处理 grouping**——百分比堆积是适配器全新语义（D36，无旧语义可迁移）。fixture scatter×2 均为 scatterStyle=lineMarker（连线+标记）形态（D38）。所有 17 charts 带 dLbls。
- **消费方为零**：nop-chart-export 无任何生产调用方（pom 与代码双向 grep 核实）；报表链路 ExpandedSheetChartGenerator 只走 xlsx 原生路径（本计划零改动）。
- **引擎 API（WI4 已交付）**：EChartsRenderer.renderPng(String optionJson, int w, int h)→byte[]、renderSvg→String、normalize（剥离 toolbox/tooltip/brush/dataZoom/timeline）；无独立 OptionNormalizer 类。ECharts option.backgroundColor 缺省 transparent + Java2DCanvas TYPE_INT_ARGB → renderPng 产透明底 PNG。**EchartsEngine.configure(TextMeasurer, renderTimeoutMs, timerPumpMaxRounds) 是三参数一体全局静态**，无单独 timeout 入口、textMeasurer/pumpRounds 无 getter（R1 B2，接线裁定见 D28）；option.backgroundColor/渐变序列化经 JsonTool.stringify 可行（EChartsRenderer.normalize 同路径先例）。
- **模型层支撑核验**：barConfig/lineConfig/areaConfig.grouping、axis.type（ChartAxisType 5 值 CATEGORY/VALUE/DATE/SERIES/LOG）、legend.position/visible、title.text/textCellRef/visible、dataLabels.show*、series.smooth/markers/lineStyle/invertIfNegative、style.colors、fill.gradient（enabled/startColor/endColor/direction/angle）均存在且被 xlsx parser 填充；barConfig 等挂在 plotArea 上。
- **审计缺陷**（ai-dev/audits/check/format-pdf-svg.md）：①heatmap 不设 PaintScale 单色（L281，jfree 语义内已修复，语义迁移为 visualMap 色带）；②ChartExportOptions 死配置——审计原文 5 个为 dpi/quality/backgroundColor/timeoutMs/maxDataSize（backgroundColor 本计划接线，余者 D28 删除/接线；antiAlias 另有 ARGB/RGB 切换消费点，无可迁移语义一并删除）+ timeoutSeconds/timeoutMs 重叠（L301）；③TrendLineRenderer 空壳 + ComboChartRenderer 丢弃系列名/类型（L322）；④Bubble null→(0,0)（已修复，语义迁移）；⑤Bar 最小模型 NPE（已修复，语义迁移）。
- **jfree 版本管理**：nop-kernel/nop-dependencies/pom.xml L109 `<jfreechart.version>1.5.6` + L538-542 dependencyManagement 条目；唯一消费方 nop-format/nop-chart-export/pom.xml L25-28。
- **测试现状**：TestExcelAllChartsExporter（无 jfree import，经 ChartExporter 间接依赖；解析 fixture 全 17 charts → exportToFile）；TestChartDataFilter（纯契约，零 jfree，原样保留）；TestRenderersRegression（深度耦合 org.jfree，4 用例回归语义需迁移后删除）。

## Goals

- ChartModel→ECharts option 适配器（io.nop.chart.export.echarts 包），以 chart.xdef 为映射规范，覆盖 ChartType 全部 10 值（LINE/BAR/PIE/DOUGHNUT/SCATTER/BUBBLE/AREA/RADAR/HEATMAP/COMBO）。
- ChartExporter 换内脏：validate → resolve → build option → EChartsRenderer.renderPng/renderSvg → stream；IChartExporter 契约签名零改动。
- jfree 足迹全量删除：21 个 main 文件 + docs 示例 + 模块 pom jfreechart 依赖 + nop-dependencies 版本项与 BOM 条目；jfree 回归语义迁移到 ECharts 断言后删除 TestRenderersRegression。
- 审计缺陷收口：Combo 系列名/类型保留（D30）、trendLine 真实出线（D29）、heatmap 色带（visualMap）、null 点跳过、最小模型不 NPE、5 个死配置逐项实现或删除（D28）。
- fixture 17 charts 端到端出图回归（xlsx 解析 → ChartModel → 适配 → PNG），含数据形状强断言（R1 m9），为 WI9 铺路。

## Non-Goals

- Excel 截图对版验收（WI9）；SPI 与报表接线（WI7）；PDF/docx（WI8）；owner doc（WI10）。
- xlsx 原生图表路径零改动（ExpandedSheetChartGenerator → DrawingChartBuilder 不触碰）。
- 动画/交互/toolbox（引擎已剥离）；option 直通入口（EChartsRenderer.renderPng 已存在，本计划只做 ChartModel 侧）。
- 像素级对版（结构语义正确 + 出图非空白即可，样式贴近度由 WI9 判定）。
- **引擎例外（R1 B2 carve-out）**：nop-chart-echarts 引擎本体不改，唯一例外 = D28 裁定的 EchartsEngine 超时配置最小缝（configureTimeout），不触碰渲染/桥/shim 逻辑。

## Adjudications（延续 D 编号，接 plan 2363 的 D27）

- **D28 ChartExportOptions 死配置逐项裁定**（审计「删除或实现」二选一，全部接线，消灭死配置缺陷）：
  - 保留并接线：width/height（渲染尺寸）、format（"png"→renderPng；新增支持 "svg"→renderSvg；其它值抛 ERR_CHART_RENDER_FAILED + ARG_REASON，不复用 ARG_CHART_TYPE——R1 m8）、backgroundColor（写入 option.backgroundColor，缺省透明底与引擎行为一致）、maxDataSize（数据点数超限抛 ERR_DATA_VOLUME_EXCEEDED）、progressCallback（阶段回调保持现有语义）。
  - **timeoutSeconds（R1 B2 裁定：引擎最小缝）**：nop-chart-echarts 新增静态 EchartsEngine.configureTimeout(long renderTimeoutMs)（只改全局超时、不动 textMeasurer/pumpRounds，5 行级改动）；ChartExporter 在 options.timeoutSeconds 与引擎现值（getRenderTimeoutMs）不一致时调用。全局 last-write-wins 语义写进 javadoc；并发异值导出为已记录残余风险（报表链路单一 timeout 配置，实际影响面为零）。
  - 删除字段：dpi、quality、antiAlias、timeoutMs（ECharts 矢量/固定 AA 渲染下无可实现语义；删除是显式 contract 变更，零调用方论证见 Current Baseline，变更记录于本 plan 与 javadoc）。
- **D29 trendLine 映射**：linear → markLine（最小二乘两点）；log/poly/power/exp/movingAvg → 按类别计算值生成叠加 line 系列（虚线、无符号、不进图例）——markLine 只能表达直线段，其余类型以叠加系列保真；roadmap WI6 文本「trendLine→markLine」执行时同步校正为「trendLine→markLine（linear）/叠加线系列（其余）」。trendline 计算不允许静默丢弃（无数据时抛 ERR_DATA_RESOLUTION_FAILED）。**退化点裁定（R2 N2）**：n=1 或 x 零方差（斜率无定义）→ 抛 ERR_DATA_RESOLUTION_FAILED；y 全相同 → 合法水平线（markLine y=常数），不误判为退化。
- **D30 COMBO 适配（执行修订，见当日 log 执行发现③）**：按 ChartSeriesModel.type 逐系列分派（bar/line/area/scatter/bubble），每系列保留自身 name/type/样式——消灭审计「Combo 丢弃系列名/类型」缺陷；**COMBO 图系列级 type 缺失 → ERR_UNSUPPORTED_CHART_TYPE fail-fast**（真 combo 解析链只记首个 plot-area 组的类型且从不设系列级 type，回落图表级 type=COMBO 无路可走，静默渲染所有系列同型是 silently-wrong）；非 COMBO 图系列缺失 type 时回落图表级 type 不变（cartesian 单型图常态路径）。
- **D31 Registry 契约形态**：删除 IChartTypeRenderer（JFreeChart 签名不可保留）；新增 io.nop.chart.export.IChartOptionBuilder，语义契约 = 按 ChartType 报告支持性 + 输入（ChartModel、ICellRefResolver、已解析数据集）产出 ECharts option 片段（Map/List 结构）；ChartTypeRendererRegistry 类名与 getRenderer/isSupported/getSupportedTypes API 形状保留，注册内容换为 option builder。方法签名以源码为唯一事实（guide Rule #14，契约细节不展开于 plan）。
- **D32 null/缺失数据语义迁移**：scatter/bubble null 点跳过（不产 (0,0) 占位——审计缺陷④语义）；heatmap null 跳过；bar/line/radar 最小模型（空数据）产出合法 option 不 NPE（审计缺陷⑤语义）；dispBlanksAs 三值全映射（R1 m2，**仅作用于 line/area 族**——R2 N5，scatter/bubble 的 null 跳过不受 zero 补 0 影响）：gap→connectNulls:false、zero→值补 0、span→connectNulls:true。
- **D33 适配器产物与错误面**：builder 产物 Map/List 结构 → JsonTool.stringify → EChartsRenderer；适配错误一律 NopException + ChartExportErrors 既有错误码（不新增错误码，消息 English）；**ChartExporter 外层不得二次包装 NopException（原样重抛），防 ERR_DATA_VOLUME_EXCEEDED/ERR_UNSUPPORTED_CHART_TYPE/ERR_DATA_RESOLUTION_FAILED 被catch-all吞成 ERR_CHART_RENDER_FAILED（R2 N6）**。
- **D34 ImageDiffAssert 不跨模块复用**（留在 nop-chart-echarts test 范围）；本计划断言面 = 结构化 JSON 断言 + 本地非空白像素断言（nop-chart-export test 内新建小工具，ARGB 非背景像素计数，与 ImageDiffAssert.assertNotBlank 同判据，兼容透明底 alpha>10 计数）。
- **D35 散点/气泡/热力数据面语义**（R1 M1，适配层裁定，不改 nop-excel 解析链）：scatter 的 x 取 categories 数值化（真实 xlsx 链 x 值落 catCellRef→categories）、y 取 values，数据点 = [x,y]；**非数值 category（如 "Q1"）→ 抛 ERR_DATA_RESOLUTION_FAILED + ARG_REASON，禁静默回落 index（R2 N1，对齐 resolver ConvertHelper fail-fast 先例）；categories 全空（xlsx 链 scatter xVal 为 numLit 内联字面量时 parseScatterSeriesData 只提取单元格引用、字面量值被丢弃——fixture chart3/4 实测）→ 按 index 1..n 兜底出点，javadoc 记录为解析链已知限制（与 bubbleSizes 同桶，Non-Blocking Follow-ups 已立项恢复）**；bubble 同 scatter + symbolSize 取常量缺省（bubbleSizes 无生产者，Excel 气泡大小字段在解析链中已丢弃——记录为解析链已知限制，不属本 plan 修复面）；heatmap 数据 = 行（series 序）×列（category 序）网格，value 取 values[i]，visualMap 连续色带（审计缺陷①语义迁移）。三项数据形状均有结构断言（R1 m9）。
- **D36 percentStacked 百分比堆积**（R1 M2，全新语义）：ECharts 原生不支持——适配器做数据预变换：类别内各系列值 → value/sum(category)×100，series.stack 同组，dataLabels 显示变换后百分比值（与 Excel 显示语义一致，Excel 单元格存原始值、图上显示百分比）；yAxis max 不强制。变换在 builder 内完成，结构断言验证「类别内百分比和≈100」。
- **D37 grouping 全值映射**（R1 M3）：CLUSTERED/STANDARD → ECharts 缺省并列（STANDARD 是 clustered 兼容别名）；STACKED → series.stack 同组；PERCENT_STACKED → D36 变换 + stack 同组。穷举 4 值，不留未映射分支。
- **D38 scatter 形态映射**（R1 M4）：scatterStyle=lineMarker → ECharts line 系列 + symbol 显示（Excel 视觉 = 折线+标记，WI9 结构对版面）；=marker → scatter 系列；smooth 沿用 series.smooth。scatterStyle 其它值按 marker 处理并在 javadoc 记录。
- **D39 轴类型映射**（R1 m3；R2 N3 校正）：CATEGORY→category 轴、VALUE→value 轴、DATE→time 轴（categories 为字符串日期时由 ECharts time 轴解析，fixture 无 date 轴图，格式语义记录于 javadoc）、**LOG→ECharts `type:'log'` 对数轴**（`scale:'log'` 是无效 API——scale 是 value 轴 boolean 选项，字面实现会静默渲染为线性轴）；SERIES（Excel 3D 系列轴）→ ERR_UNSUPPORTED_CHART_TYPE（显式不支持，不静默降级）。
- **D40 manualLayout 不映射**：删除 layout/ManualLayoutProcessor 后，ChartModel/legend/title/plotArea 的 manualLayout 字段不映射 ECharts 坐标（ECharts grid 由布局引擎决定）；WI9 对版如暴露布局偏差再立项。此为显式裁定，非遗漏。

## Scope

### In Scope

- nop-format/nop-chart-export：echarts/ 适配包、IChartOptionBuilder、ChartExporter 内脏替换、ChartTypeRendererRegistry 改造、IChartTypeRenderer 及 21 个 jfree main 文件删除、ChartExportOptions 字段裁定、pom 依赖变更、测试迁移。
- nop-format/nop-chart-echarts：仅 EchartsEngine.configureTimeout 最小缝（D28）+ 对应测试。
- nop-kernel/nop-dependencies/pom.xml：jfreechart.version 与 BOM 条目删除。
- ai-dev/plans/2364-*.md、当日 ai-dev/logs/、roadmap WI6 文本同步（D29 + jfree 计数口径）。

### Out Of Scope

- nop-chart-echarts 引擎本体（除 D28 缝）；nop-report/nop-excel 任何改动；docs-for-ai owner doc（WI10）。

## Execution Plan

### Phase 1 - 适配器核心（纯新增，不动现有编译面）

Status: completed
Targets: nop-format/nop-chart-export/src/main/java/io/nop/chart/export/echarts/

- Item Types: `Feature` + `Fix`（trendLine/heatmap 色带/null 跳过/最小模型 NPE 为审计确认缺陷的语义落地）

R1 B1 裁定：本 Phase 只新增文件（接口 + builders + 单测直接调用 builder），不触碰 IChartTypeRenderer/Registry/ChartExporter——现有代码与测试零改动保持编译绿；registry 切换与删除原子归 Phase 2。

- [x] IChartOptionBuilder 接口（D31 语义契约）+ 10 个 ChartType builder 骨架（bar/line/area/pie/doughnut/scatter/bubble/radar/heatmap/combo 分派）
- [x] 数据面接线：ChartDataResolver.resolveSeriesData + ChartDataValidator 接入 builder 输入（D35 散点/气泡/热力数据语义在此落地）
- [x] 直角坐标族：bar/line/area（D37 grouping 全值映射、D36 percentStacked 变换、smooth/markers/lineStyle、shapeStyle 填充色、invertIfNegative、D32 dispBlanksAs 三值映射）
- [x] 饼族：pie/doughnut（radius 语义、label、legend、dataPoint 逐点颜色）
- [x] 散点族：scatter/bubble（D35 x/y 语义 + D38 scatterStyle 形态 + bubbleSizes 常量缺省 + D32 null 跳过）
- [x] radar builder（indicator 来自 categories + 值域推定，实现时实测校正并记录）
- [x] heatmap builder（D35 网格语义 + visualMap 连续色带——审计缺陷①语义迁移）
- [x] combo 分派（D30：按系列级 type 路由，缺失回落图表级）
- [x] 公共组件：legend/title/axes（D39 全类型映射）/dataLabels/trendLine（D29）/style.colors→option.color/渐变 fill→itemStyle 渐变对象/backgroundColor
- [x] 单测（新测试类，直接构造 ChartModel 调 builder）：每 ChartType 一条结构化 JSON 断言（series type/坐标系/关键组件）+ D36 百分比和≈100 断言 + D37 standard 别名断言 + D29 linear markLine 与非 linear 叠加系列断言 + D30 combo 分派断言 + D32 null 跳过断言 + D38 lineMarker 形态断言 + 调色板/渐变断言

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-chart-export -am` 全绿（新增测试通过，既有 3 测试零改动不回归）
- [x] 10 种 ChartType 均有 builder 且可独立产出 option（覆盖数断言 ≥10，防静默缺失）
- [x] git status 确认 Phase 1 零修改既有文件（B1 时序约束达成）
- [x] **无静默跳过**（Minimum Rules #24）：不支持的 format（D28）/SERIES 轴（D39）/无法计算的 trendLine（D29）显式抛 NopException，无空方法体
- [x] No owner-doc update required（WI10 统一收口）
- [x] 当日 `ai-dev/logs/` 条目已更新

### Phase 2 - 原子切换：Registry/ChartExporter 换内脏 + jfree 全量删除 + 测试迁移

Status: completed
Targets: nop-format/nop-chart-export、nop-format/nop-chart-echarts（D28 缝）、nop-kernel/nop-dependencies/pom.xml

- Item Types: `Feature` + `Fix`（死配置/审计缺陷收口 + 引擎缝）

- [x] EchartsEngine.configureTimeout(long) 最小缝 + 单测（D28；Non-Goal carve-out 已声明）
- [x] ChartExporter 重写 + ChartTypeRendererRegistry 切换 + IChartOptionBuilder 注册（同一编译变更内原子完成：validate → resolve → registry builder → option JSON → EChartsRenderer（png/svg 按 D28）→ stream；progressCallback/timeoutSeconds/maxDataSize/backgroundColor 全接线；**pom 增加 nop-chart-echarts 依赖属本项前置件，随本项先行落地——R2 N4**；NopException 原样重抛不二次包装——D33/N6）
- [x] ChartExportOptions 按 D28 删 4 死字段（dpi/quality/antiAlias/timeoutMs）+ javadoc 记录裁定
- [x] 删除 jfree 文件 21 个 main（renderer/ 15、style/ 3、layout/ManualLayoutProcessor、utils/JFreeChartStyleAdapter、IChartTypeRenderer）+ nop-format/nop-chart-export/docs/jfreechart-code-examples.md（R2 N7 全路径校正）
- [x] 模块 pom 删 org.jfree:jfreechart 依赖、加 nop-chart-echarts 依赖；nop-dependencies 删 jfreechart.version 属性与 dependencyManagement 条目
- [x] TestExcelAllChartsExporter 迁移：fixture 17 charts 全部经 IChartExporter.exportToImage 产出非空白 PNG（PNG 魔数 + D34 非空白断言）+ svg 格式路径断言 + options（width/height/backgroundColor）生效断言 + **数据形状强断言（R1 m9）**：percentStacked 图 option 内百分比变换、scatter lineMarker 图 series type=line、stacked 图 stack 分组、radar indicator 数=类别数
- [x] TestRenderersRegression → TestEchartsAdapterRegression：4 项回归语义迁移（bubble null 跳过 / heatmap visualMap 色带 / bar 最小模型无 NPE / radar 最小模型无 NPE）后删除原文件
- [x] TestChartDataFilter 原样保留并确认绿
- [x] roadmap WI6 文本同步：trendLine→markLine 表述校正（D29）+ 「24 个」→ 实测口径注记（21 删 + 1 重写 + 1 test + 1 docs）

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：从 xlsx fixture 解析 → ExcelChartModel → 适配器 → GraalJS 引擎渲染 → exportToFile PNG 落盘非空白，全链路测试通过（17/17 charts + 数据形状强断言）
- [x] **接线验证**（Minimum Rules #23）：ChartExporter 主流程经 registry.getRenderer(type) 调用 IChartOptionBuilder.buildOption 产出 option（17-chart E2E 即运行时连通证据 + registry 覆盖断言）
- [x] jfree 零足迹：`grep -ri jfree nop-format/ nop-kernel/nop-dependencies/pom.xml` 的 src 与 pom 零命中（roadmap/plan/logs 等文档命中不算）
- [x] `./mvnw test -pl nop-format/nop-chart-export -am` 全绿（含 nop-chart-echarts/canvas2d 传递构建不回归）
- [x] 既有 3 测试处置落定：filter 原样绿、exporter 迁移绿、regression 语义迁移绿
- [x] 死配置清零：ChartExportOptions 每个保留字段在 ChartExporter 中有真实消费点（审计缺陷②收口）
- [x] No owner-doc update required（WI10 统一收口）；roadmap WI6 文本同步完成
- [x] 当日 `ai-dev/logs/` 条目已更新

### Phase 3 - 全量回归与收口

Status: completed
Targets: 仓库构建 + 文档状态

- Item Types: `Proof`

- [x] `./mvnw clean install -pl nop-format/nop-chart-export -am` 全绿（BOM 变更后传递链完整构建）
- [x] jfree 删除无下游破坏核实：全仓库 grep org.jfree 零代码引用 + nop-format 聚合构建通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-chart-export --severity high` 退出码 0
- [x] 代码规范检查（check-import-order.mjs / run-java-lint.sh 或 pmd -Pqa，按仓库实际命令）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立 closure audit（新子代理）完成，证据写入本文件 Closure 段

Exit Criteria:

- [x] 上述命令全部退出码 0
- [x] 当日 `ai-dev/logs/` 收口条目与 plan/roadmap 状态一致

## Closure Gates

- [x] roadmap WI6 scope 全落地：适配器（10 ChartType）+ 契约保留 + jfree 全量删除 + 审计缺陷收口（①③④⑤语义迁移、②D28、Combo D30、trendLine D29）
- [x] 所有 in-scope confirmed live defects 已修复或随删除消灭（语义迁移到 ECharts 断言）
- [x] 无空方法体/占位实现/静默跳过（scan-hollow + 人工核查新包）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] IChartExporter/ChartExportOptions/ChartExportErrors/Registry/ChartDataFilter 契约保留且签名兼容（ChartExportOptions 按 D28 删字段属显式 contract 变更，零调用方论证 + plan 级记录已落）
- [x] xlsx 原生路径零改动（git diff 不触碰 ExpandedSheetChartGenerator/DrawingChartBuilder）
- [x] 端到端 fixture 17 charts 出图测试在位且绿（含数据形状强断言）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 ChartExporter → registry → builder → EChartsRenderer 渲染链运行时真实连通（fixture E2E 即证据）
- [x] `./mvnw clean install -pl nop-format/nop-chart-export -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2364-wi6-chartmodel-echarts-adapter-jfree-removal.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无——死配置按 D28 逐项实现或删除，不延期；样式贴近度与 manualLayout 布局偏差裁定权归 WI9（D40），非本 plan deferred 项）

## Non-Blocking Follow-ups

- jfreechart 从本地 .m2-repo 缓存目录清理（非构建路径，不影响基线）
- ChartExportOptions 如未来需要 jpeg/quality 支持，另行立项（当前无调用方需求）
- xlsx 解析链字面量数据丢弃的恢复（bubbleSizes 无生产者 + scatter c:xVal numLit 字面量 x 值被丢弃——D35 记录的解析链已知限制；无消费需求，另行立项）

## Closure

Status Note: WI6 全部交付——ChartModel→ECharts option 适配器（10 ChartType 全覆盖 + D28-D40 裁定落地）、nop-chart-export 壳保留内脏更换、jfree 足迹清零（21 main + 1 test + 1 docs + pom/BOM）、审计 5 缺陷收口（heatmap 色带/死配置实现或删除/趋势线真实出线/Combo 系列名保留/null 跳过与最小模型不 NPE 语义迁移）、fixture 17 charts 端到端出图 + 数据形状强断言。契约保留（ChartExportOptions 删 4 死字段为显式零调用方 contract 变更）。执行中 live 校正 3 项（scatter numLit x 全空 → index 兜底、空值雷达 data 项、COMBO 无系列类型 fail-fast）均已回写裁定文本。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_1fc2045a-7be2-47f0-a43a-0a6e2ee35db1（与实现者不同会话，本日第 3 个独立审查代理）
- Evidence:
  - Phase 1/2 Exit Criteria 逐条 PASS（审计报告第 1 节）：10 builder 注册、17 charts E2E 非空白 + 形状断言、jfree 零足迹（审计者本人 grep 复跑）、21+1+1 删除清单与 git status 逐一对应、死配置清零（7 保留字段各有消费点）、既有 3 测试处置对照、xlsx 原生路径零改动（git diff 空）
  - 测试独立复跑：`./mvnw test -pl nop-format/nop-chart-export -am -o` 31/31 绿 + `clean install` exit 0 + nop-chart-echarts 36 不回归
  - Anti-Hollow PASS：ChartExporter→resolver→registry→builder→JsonTool.stringify→EChartsRenderer→out.write 调用链逐环验证连通；echarts/ 包 14 文件通读无空方法体/静默跳过；scan-hollow 0 findings
  - 裁定落实抽查 7/7 PASS（D29/D30/D36/D37/D38/D39/D35 各有代码+测试证据）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2364-wi6-chartmodel-echarts-adapter-jfree-removal.md --strict` 退出码 0；check-doc-links --strict 0 错
  - Deferred 项分类检查：3 条 follow-up 均真 out-of-scope，无 in-scope defect 降级；scatter numLit 记录四处一致（baseline/D35/javadoc/log）
  - 审计 must-fix 3 项已修：D30 计划文本同步执行修订语义、Phase 3/Closure Gates 按机制勾选并引审计证据、roadmap L3 header 同步更正
  - 审计建议 3 项已采纳：follow-up 补 scatter numLit 字样、heatmap rowCategories 实例字段并发竞争修复（移入 per-build ctx）、requireSupported 死代码删除

Follow-up:

- jfreechart 从本地 .m2-repo 缓存目录清理（非构建路径）
- ChartExportOptions jpeg/quality 支持如未来有需求另行立项（D28 已显式删除）
- xlsx 解析链字面量数据丢弃的恢复（bubbleSizes + scatter numLit xVal；D35/Follow-up 记录，无消费需求）
