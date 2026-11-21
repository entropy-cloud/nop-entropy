# 图表静态渲染（ECharts 引擎）

> Status: active owner doc
> Last Reviewed: 2026-10-10
> Sources: 仓库 roadmap（echarts-server-render，ai-dev/backlog/）与 17 图型对版验收报告（ai-dev/analysis/2026-10/，文件名含日期前缀 wi9）——按仓库惯例 ai-dev 为开发过程文档域，本页不直接链接。

平台图表静态渲染以 **Apache ECharts 6.1.0（官方 dist，JVM 内 GraalJS 执行）+ Java2D Canvas2D shim** 替代 JFreeChart（已退役）：把 ChartModel / ECharts option JSON 渲染为 PNG/SVG（无动画、无交互）。17 图型对版验收 17/17 结构语义 PASS（对版验收报告在 ai-dev/analysis/2026-10/ 下 wi9 前缀文档）。

## 模块边界与依赖方向

| 模块 | 职责 | 依赖 |
|------|------|------|
| `nop-format/nop-canvas2d` | 纯 Java2D 实现 Canvas2D 语义（路径/文本/渐变/阴影/图像数据），供 zrender 桥接 | 无 Graal/echarts 依赖 |
| `nop-format/nop-chart-echarts` | GraalJS 引擎（EchartsEngine Context 池 + 裸环境桥）、EChartsRenderer（renderPng/renderSvg + option normalize） | nop-canvas2d + polyglot/js + org.mvnpm:echarts |
| `nop-format/nop-chart-export` | 壳契约（IChartExporter/ChartExportOptions/ChartExportErrors/Registry/ChartDataFilter）+ ChartModel→ECharts option 适配器（echarts 包，10 种 ChartType） | nop-excel + nop-chart-echarts |
| `nop-report/nop-chart-report` | 可选报表接线：IChartImageProvider 实现 bean（ExpandedSheet 图表 → PNG） | nop-report-core + nop-chart-export |

依赖单向：`nop-canvas2d` 仅依赖 nop-api-core；`nop-chart-echarts` → nop-canvas2d；`nop-chart-export` → nop-excel + nop-chart-echarts；`nop-chart-report` → nop-report-core + nop-chart-export。**GraalJS 只存在于 nop-chart-echarts**；`nop-report-core` 的依赖树不含 GraalJS（provider 不在 classpath 时报表渲染静默跳过图表）。

## 双入口

1. **option JSON 直通**：`io.nop.chart.echarts.render.EChartsRenderer.renderPng(optionJson, width, height[, timeoutMs])` / `renderSvg(...[, timeoutMs])`。入口即 ECharts option（JSON 字符串），引擎 normalize 会剥离交互件（toolbox/tooltip/brush/dataZoom/timeline）。带 `timeoutMs` 的重载为请求级超时（≤0 用引擎默认；plan 2586 起超时随调用传参，全局态不再被改写）。
2. **ChartModel 入口**：`io.nop.chart.export.ChartExporter implements IChartExporter`——`exportToImage(ChartModel, ICellRefResolver[, ChartExportOptions])`。管线：validate → ChartDataResolver 解析系列数据 → ChartTypeRendererRegistry 按 ChartType 选 option builder → JSON → 引擎。ChartExportOptions 支持 format(png|svg)/width/height/backgroundColor/timeoutSeconds/maxDataSize/progressCallback；`timeoutSeconds` 经渲染链传参生效（不触碰全局），width*height 乘积上限 `ChartExporter.MAX_CANVAS_PIXELS`（16M px，超出 `ERR_INVALID_DIMENSIONS` fail-loud）。

适配语义要点（完整裁定见仓库 ai-dev/plans/ 下 2364 计划的 D28–D40 裁定编号）：grouping 全值映射（standard=clustered 别名）、百分比堆积数据预变换、trendLine linear→markLine（其余类型叠加虚线系列）、combo 按系列级 type 分派（缺失 fail-fast）、散点线可见性由系列 spPr a:ln 的 noFill 表达（shapeStyle.border）。

## 字体配置

文本渲染经 `io.nop.canvas2d.TextMeasurer` + `CanvasFontMapper`：

- `EchartsEngine.configure(textMeasurer, renderTimeoutMs, timerPumpMaxRounds)` 全局注入（静态单例吃不到 IoC，报表装配在启动时调用一次）。
- 泛族字体（sans-serif 等）映射可注入；中文必须可覆盖，缺字体显式报错（不静默 fallback）。
- 超时不再有全局可变入口（`configureTimeout` 已移除，plan 2586）：请求级超时经 `renderPng/renderSvg/renderCanvas/renderSvg` 的 `timeoutMs` 参数传递；引擎挂 JVM shutdown hook 关闭全部存活 Context 与超时调度线程，shutdown 后调度线程自愈重建。

## 报表接入（opt-in）

- 依赖 `nop-chart-report` 即启用（其 beans 经 `_vfs/nop/autoconfig/nop-chart-report.beans` 自动装载 provider bean）。
- 报表渲染器（xlsx 除外）生成时经 `io.nop.excel.renderer.ChartImageProviderRegistry.getProvider()` 发现 provider：在位 → 图表渲染为图片层；**缺席 → 静默跳过**（输出与无图表一致）。`nop-excel` SPI 零新依赖，GraalJS 不进 nop-report-core。
- **xlsx 原生图表路径零改动**：ExpandedSheetChartGenerator → DrawingChartBuilder 照旧产出原生 OOXML 图表，与本渲染引擎互不相干（双路径边界）。

## 对版基线与已知差异

- 17 图型（fixture `nop-format/nop-chart-export/src/test/resources/xlsx/test-all-charts.xlsx`）结构语义对版 17/17 PASS；渲染证据与逐图判定报告在 ai-dev/analysis/2026-10/（wi9 前缀，纯文本指引不建链）。
- 已知样式差异（不阻断）：配色为 ECharts 默认调色板（非 Excel 主题色）、值轴范围取整与百分比刻度无 % 号、雷达辐条方向与 Excel 镜像、横向条形图系列排列方向相反、圆环扇区几何瑕疵（跟踪项）。

## 关键源码锚

见 `docs-for-ai/04-reference/source-anchors.md` 的 `CANVAS-001`、`ECHARTS-001`、`CHART-001`、`CHART-002` 条目。
