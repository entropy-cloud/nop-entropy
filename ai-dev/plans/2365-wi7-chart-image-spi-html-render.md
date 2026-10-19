# 2365 WI7 图表渲染 SPI 与 HTML 出图接线

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI7（M4）；依赖 plan 2364（WI6 已完成，commit 92e664b2cf）
> Related: plan 2366（WI8 PDF/docx 复用同一 provider）、plan 2367（WI9 对版验收复用全链路）

## Purpose

落地 roadmap WI7：nop-excel 定义 IChartImageProvider SPI + registry（IReportRendererFactory 旁），HTML 两处 hook（HtmlReportRendererFactory/SimpleHtmlReportRendererFactory，每处 ≤30 行，provider 不在 classpath 静默跳过）；新建 nop-report/nop-chart-report 模块（provider 实现 bean、IExcelSheet→图表模型 resolver、锚点→坐标），不进 nop-report-core；HTML 出图测试证明「provider 在位出图 + 缺席静默跳过」双向。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ 92e664b2cf）核验：

- **渲染器注册机制**：IReportRendererFactory（nop-format/nop-excel io.nop.excel.renderer，唯一方法 buildRenderer(ExcelWorkbook, IExcelSheetGenerator)）经 Nop IoC beans.xml `<ioc:collect-beans>` 注入 ReportEngine.renderers map；跨模块贡献 bean 先例 = report-pdf-defaults.beans.xml（nop-report-pdf）。IReportRendererRegistry 接口（getRendererForExcel）被 IReportEngine 继承。**beans 装载机制（R2 B1 校正）：AppBeanContainerLoader 只自动装载 (a) `_vfs/nop/<module>/beans/app*.beans.xml` (b) `_vfs/nop/autoconfig/*.beans` 清单所列举的 beans.xml（先例 nop-report-core/_vfs/nop/autoconfig/nop-report.beans 内容一行指向 report-defaults.beans.xml）——`-defaults.beans.xml` 不会被自动发现，新模块必须配套 autoconfig 清单文件。**
- **HTML hook 点**：HtmlReportRendererFactory.renderSheet L134-136 已有图片钩子（`renderImages(sheet, sheetId, out)`），图片以 sheet div 的 CSS 多重 background-image 呈现（renderImages L148-185 + outputBackground L188-219，全 pt 单位，dataUrl = `image.getData().toDataUrl(mimeType)`，isPrint=false 走 @media print 覆盖）；SimpleHtmlReportRendererFactory.renderSheet L96-110 **无任何图片/图表渲染**，只有 HtmlTableOutput。
- **图表数据管道**：ExpandedSheetGenerator.generateSheet L168 调 ExpandedSheetChartGenerator.INSTANCE.generateCharts 把动态求值后的 ExcelChartModel 放入 ExpandedSheet.makeCharts()；ExpandedSheet implements IExcelSheet（getCharts L97/makeCharts L105/getDefaultRowHeight/getDefaultColumnWidth/getTable）。
- **锚点→pt 公式已有先例**：ExcelImage.calcSize(IExcelSheet)（nop-excel model/ExcelImage.java L83-93）= getCellLeft(col1)+dx1 / getCellTop(row1)+dy1 / col2=getCol1()+colDelta；模型层单位统一 pt（_ExcelChartModel 注释明示），无 EMU。IExcelSheet 另有 getTotalWidth/getTotalHeight 等 default helper。
- **resolver**：ICellRefResolver（getValue/getValues/isValidRef）；现有 ExcelCellRefResolver 面向模板 ExcelSheet（sheet.getTable().getCellValue(row,col)）；ExpandedSheet 的 table 是 ExpandedTable——需核实 getCellValue 同名 API，缺失则在 provider 内适配。
- **WI6 API 就绪**：ChartExporter（nop-chart-export）exportToImage(ChartModel, ICellRefResolver[, options])；ExcelChartModel extends ChartModel 可直接传入；ChartExportOptions（png/svg/width/height/backgroundColor）；ChartTypeRendererRegistry.getDefault()。
- **依赖防环现状**：nop-excel ← nop-ooxml-xlsx ← nop-report-core（pom 仅 nop-ooxml-xlsx + junit，无 GraalJS/nop-chart-export）；nop-excel ← nop-chart-echarts ← nop-chart-export。新依赖方向 nop-chart-report → {nop-report-core, nop-chart-export} 无环。**SPI 接口签名不得引用 io.nop.chart.export.**（其位于 nop-excel 下游）；ChartModel/ExcelChartModel/ExcelImage 均在 nop-excel 内可安全入签名。GraalJS 只存在于 nop-chart-echarts。
- **BeanContainer 语义（R1 B1 校正）**：io.nop.api.core.ioc.BeanContainer（nop-api-core，所有模块可达）；`getBeanByType` 在容器未初始化时抛 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED、无该类型 bean 时抛 ERR_IOC_UNKNOWN_BEAN_FOR_TYPE（@Nonnull 契约）——**判据必须是 `BeanContainer.isInitialized()` 守卫 + `instance().tryGetBeanByType(Class)`（返回 null）**，否则 provider 缺席时全平台 HTML 渲染回归。「provider 不在 classpath 静默跳过」的运行时判据据此实现。
- **测试先例**：TestReportEngine（nop-report-core）手工装配 renderers map（L227-228 xlsx+html）+ getRendererForXptModel(wk,"html") → ITextTemplateOutput.generateText；TestReportChart 使用 fixture /nop/report/demo/test-bar-chart.xpt.xlsx（仅走 xlsx 路，未断言 HTML）；跨模块 test-resources 不可见（BaseTestCase classpath 隔离）→ nop-chart-report 测试需自带 fixture 副本。
- **模块登记点**：nop-report/pom.xml L19-32 modules；nop-bom 按 ${nop-entropy.version} 登记 nop-report-* 惯例；opt-in 先例 = nop-report-demo/pom.xml L36-40（注释 + 依赖）。beans.xml 惯例路径 src/main/resources/_vfs/nop/report/beans/*.beans.xml。

## Goals

- nop-excel 新增 IChartImageProvider SPI + ChartImageProviderRegistry（静态注册优先、BeanContainer 兜底、双空返回 null）+ ChartImage 渲染支撑（复用 ExcelImage + outputBackground 机制）。
- HtmlReportRendererFactory/SimpleHtmlReportRendererFactory 各插入 ≤30 行 chart hook：provider 在位 → 图表以 CSS background dataUrl 呈现（与既有图片渲染视觉一致）；缺席 → 输出与现状逐字节一致。
- 新模块 nop-report/nop-chart-report：ExcelChartImageProvider bean（IExcelSheet→resolver→ChartExporter 导出 + 锚点 calcSize 定位）+ beans.xml + module/BOM 登记 + nop-report-demo opt-in。
- 双向测试：provider 在位 → html 含 data:image/png 且图表区域非空白；provider 缺席 → 输出不含图表数据 URL（静默跳过回归）。

## Non-Goals

- PDF/docx 出图（WI8）；17 图型对版（WI9）；owner doc（WI10）。
- xlsx 原生图表路径（ExpandedSheetChartGenerator→DrawingChartBuilder 零改动）。
- nop-report-core 的任何 pom/beans 变更（GraalJS 不进 core 的结构保障）；SVG 格式的 HTML 呈现（png 起步，WI9 复审）。
- 图表交互/动画（引擎已剥离）。

## Adjudications（延续 D 编号，接 plan 2364 的 D40）

- **D41 SPI 语义与依赖方向**：IChartImageProvider 定义于 io.nop.excel.renderer（IReportRendererFactory 同包），语义契约 = 输入 IExcelSheet、输出该 sheet 的图表图像列表（ExcelImage，签名以源码为唯一事实）；签名只含 nop-excel 自有类型（IExcelSheet/ExcelImage），零新依赖。图表渲染产物复用 ExcelImage——HTML hook 直接镜像 renderImages 的 CSS background 机制，图表与图片视觉呈现一致。**provider 必须 setImgType("png")（R1 M3：mimeType 由 imgType 派生，缺失时 toDataUrl 静默产出 data:null 坏 URL）；chart ExcelImage 不设 print（默认 true，屏显/打印一致），图片与图表合并列表的 notPrint/@media print 过滤覆盖 charts（R1 M6）**。
- **D42 provider 发现**：ChartImageProviderRegistry.getProvider() = 静态注册（register/unregister，供单元测试与编程装配）优先；null 时且 BeanContainer.isInitialized() 时回落 instance().tryGetBeanByType(IChartImageProvider.class)（R1 B1：getBeanByType 会抛异常，不可用）；双 null → 返回 null → hook 静默跳过（roadmap 硬边界）。registry 自身不主动初始化 IoC。
- **D43 静默跳过的精确语义**：仅「provider 缺席」静默跳过（输出与现状一致）；provider 在位但渲染失败 → NopException 上抛（no-silent-noop），hook 不吞异常。
- **D44 图表尺寸与定位**：anchor 在位 → ExcelImage.calcSize 公式（getCellLeft/Top + dx/dy，端点为 col2/dx2 与 row2/dy2）；**provider 必须先判 chart.getAnchor() != null 再 calcSize（R1 M4：calcSize 的 makeAnchor 会静默造空 anchor 产出 0×0 隐形图）**；anchor 缺失 → ChartModel.width/height（pt）字段，再缺失 → ChartExportOptions 默认 800x600。渲染尺寸 = 定位尺寸。**事实记录（R1 M4）：chart anchor 为模板坐标系原值（ExpandedSheetChartGenerator 仅 cloneInstance 不重锚定），不随行列展开重算——与 xlsx 原生路径行为一致；会展开的模板中 HTML 图表定位有偏差，WI9 复审面。**
- **D45 provider 渲染管线**：ExpandedSheet/IExcelSheet → ExpandedSheetCellRefResolver（实现 ICellRefResolver；**事实（R2 问题3 校正）：ExpandedTable 经 ITableView 继承 null-safe 的 getCellValue(int,int)（default 实现 = getCell(row,col) 判空返回 null），可直接使用**）→ new ChartExporter(ChartTypeRendererRegistry.getDefault()).exportToImage(chart, resolver, options)；options.width/height 按 D44；格式 png。每 sheet 的 charts 顺序渲染，单图失败上抛。**单 sheet 限制（执行修订，见当日 log）：fixture 图表数据引用实为自限定形态（Sheet1!$A$3）——限定名等于本模板 sheet 名时本地解析（语义正确），指向其它 sheet 的引用显式拒绝（ExpandedSheet 无 workbook 引用）；已实现于 resolver isLocalSheet 并有 javadoc。完全跨 sheet 引用需求出现时另行立项。**
- **D46 HTML hook 形态**：两处 hook 共用 nop-excel 新增支撑类 HtmlSheetVisualRenderer.renderVisualOverlays（静态工具），单处插入点 ≤30 行；HtmlReportRendererFactory 在既有图片钩子后并列（图片与图表同层叠加，先图片后图表）；SimpleHtmlReportRendererFactory 为新增能力（原本无图片渲染），只渲染图表。

## Scope

### In Scope

- nop-format/nop-excel：IChartImageProvider、ChartImageProviderRegistry、renderChartImages 支撑、HtmlReportRendererFactory/SimpleHtmlReportRendererFactory hook（含 renderImages 的 List 参数化重构）、单测。
- nop-report/nop-chart-report（新模块）：pom、ExcelChartImageProvider、ExpandedSheetCellRefResolver、beans.xml、fixture 副本、E2E 测试。
- nop-report/pom.xml modules、nop-bom 条目、nop-report-demo opt-in。
- ai-dev/plans/2365-*.md、当日 ai-dev/logs/。

- **roadmap 文本回灌**：roadmap Cross-Cutting #7 把模块名写作 nop-report-chart，与 WI7 行的 nop-chart-report 不一致（R1 M9）——执行时统一为 nop-chart-report 并回灌 roadmap 文本。

### Out Of Scope

- nop-report-core 源码/资源（pom、beans、java 均零改动）；nop-report-pdf/docx；docs-for-ai owner doc（WI10）。

## Execution Plan

### Phase 1 - SPI + registry + HTML hook（nop-excel）

Status: completed
Targets: nop-format/nop-excel/src/main/java/io/nop/excel/renderer/

- Item Types: `Feature`

- [x] IChartImageProvider SPI + ChartImageProviderRegistry（D42：静态 register/unregister/getProvider 双层发现）+ 单测（注册/注销/null 三态）
- [x] renderChartImages 静态支撑（复用 outputBackground 的 dataUrl + pt 定位机制；与 renderImages 共享代码路径——renderImages List 参数化重构，既有调用行为不变）
- [x] HtmlReportRendererFactory.renderSheet hook（≤30 行，图片钩子后并列，D43/D46）
- [x] SimpleHtmlReportRendererFactory.renderSheet hook（≤30 行，新增图表渲染能力）
- [x] 单测：假 provider（返回带数据的 ExcelImage）→ html 输出含 data:image/png 与定位样式；无 provider → 输出与基线一致（逐字符断言或 contains 反证）；假 provider 抛异常 → 上抛不吞

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-excel -am` 全绿（新增测试 + 既有测试不回归）
- [x] 两处 hook 插入点各 ≤30 行（代码行数核查）
- [x] **接线验证**（Minimum Rules #23）：hook 运行时经 registry.getProvider() 调用 provider.renderCharts（假 provider 测试即证据）
- [x] **无静默跳过**（Minimum Rules #24）：provider 缺席跳过是显式裁定（D43）且有回归测试钉住；provider 在位时异常上抛
- [x] No owner-doc update required（WI10 统一收口）
- [x] 当日 `ai-dev/logs/` 条目已更新

### Phase 2 - nop-chart-report 模块 + E2E 出图

Status: completed
Targets: nop-report/nop-chart-report/、nop-report/pom.xml、nop-bom/pom.xml、nop-report/nop-report-demo/pom.xml

- Item Types: `Feature` + `Proof`

- [x] 新模块 pom（parent=nop-report；deps: nop-report-core + nop-chart-export）+ nop-report/pom.xml module 登记 + nop-bom 条目（**含 nop-chart-export 补条目——R1 M1：其原不在 nop-bom，无版本号依赖会在解析期失败**）+ nop-report-demo opt-in（照抄 L36-40 注释先例）
- [x] ExpandedSheetCellRefResolver（ICellRefResolver 实现，读 ExpandedTable 单元格值；实现时核实 getCellValue API 并记录）
- [x] ExcelChartImageProvider bean：IExcelSheet.getCharts() → D44 定位/尺寸 → D45 管线导出 PNG → ExcelImage 列表（beans.xml 注册，路径 _vfs/nop/report/beans/report-chart-defaults.beans.xml + **配套 _vfs/nop/autoconfig/nop-chart-report.beans 清单文件——R2 B1：无清单 CoreInitialization 装不进 provider，在位 E2E 必红**）
- [x] ExpandedSheetCellRefResolver 单测（R2 问题4，Rule #25）：有效 ref 出值 / 空 range 返回 null / 跨 sheet ref 显式行为（不支持 → 抛错或返回 null，按实现断言钉住）
- [x] fixture 副本 test-bar-chart.xpt.xlsx 入模块 test resources + E2E：ReportEngine 手工装配（html factory）+ provider 注册 → html 输出含 data:image/png（base64 长度下限断言）+ 输出落盘人工可查
- [x] 静默跳过回归（R1 M2 机制裁定）：**在位用例走 IoC 发现路径**（CoreInitialization 装载 beans.xml 的 provider bean → registry 经 tryGetBeanByType 找到——证明生产接线）；**缺席用例在 Phase 1 无 IoC 单测承载**（静态 registry 无注册 + 未初始化容器 → null → hook 跳过，含 StaticBeanContainer 空容器分支），Phase 2 E2E 不再重复缺席用例
- [x] GraalJS 不进 nop-report-core 结构核实：nop-report-core pom 零改动 + 依赖树核查（mvn dependency:tree 或 pom 审查）

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：xpt 模板 → ExpandedSheet（ExpandedSheetChartGenerator 图表数据管道）→ provider → ChartExporter → GraalJS 引擎 → dataUrl 进 HTML，全链路测试通过
- [x] **双向验收**：provider 在位出图（IoC 发现路径 E2E）+ 缺席静默跳过回归由 Phase 1 无 IoC 单测钉住（registry 无注册 + 未初始化容器/空 StaticBeanContainer → null → 跳过）（roadmap Cross-Cutting #7）
- [x] `./mvnw test -pl nop-report/nop-chart-report -am` 全绿（含 nop-report-core 传递构建不回归）
- [x] GraalJS/nop-chart-echarts 不出现在 nop-report-core 依赖树（结构保障核实）
- [x] No owner-doc update required（WI10 统一收口）
- [x] 当日 `ai-dev/logs/` 条目已更新

## Closure Gates

- [x] roadmap WI7 scope 全落地：SPI + registry + 两处 ≤30 行 hook + 新模块 provider/resolver/beans + opt-in + 双向测试
- [x] 无空方法体/占位实现/静默跳过（scan-hollow + 人工核查；D43 跳过语义显式且有测试钉）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] GraalJS 不进 nop-report-core（依赖树证据）；nop-excel SPI 零新依赖（pom 证据）
- [x] xlsx 原生路径零改动（git diff 不触碰 ExpandedSheetChartGenerator/DrawingChartBuilder）
- [x] 端到端 HTML 出图测试在位且绿（在位走 IoC 路径）+ 缺席回归测试绿（Phase 1 单测）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 hook→registry→provider→ChartExporter→引擎运行时链真实连通（E2E 即证据）
- [x] `./mvnw test -pl nop-report/nop-chart-report -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2365-wi7-chart-image-spi-html-render.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 代码规范检查（check-import-order 本模块 + scan-hollow --severity high 退出码 0）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- SVG 格式 HTML 呈现（data:image/svg+xml）——png 已满足对版验收面，WI9 复审后另议
- nop-report-app / nop-spring-report-demo 的 opt-in（当前仅 nop-report-demo；roadmap 允许任一）

## Closure

Status Note: WI7 全部交付——nop-excel SPI（IChartImageProvider/ChartImageProviderRegistry/HtmlSheetVisualRenderer）+ 两处一行级 hook + nop-chart-report 模块（provider bean/autoconfig/resolver/fixture E2E）+ BOM/module/demo opt-in 登记。双向验收达成：在位走 IoC 发现路径出图（CoreInitialization + autoconfig 装载），缺席静默跳过由 Phase 1 无 IoC 单测钉住。执行中 live 校正 2 项（D45 自限定引用本地解析、nop-ioc 测试依赖替代 autotest 全家桶避免 dao beans 干扰）均已回写裁定文本。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_8a991166-0943-48f3-b9f9-af48689e75a6（与实现者不同会话）
- Evidence:
  - Phase 1/2 Exit Criteria 12/12 PASS、Closure Gates 14/14 PASS（审计者独立复跑 `./mvnw test -pl nop-report/nop-chart-report -am -o` EXIT=0：nop-excel 82 / nop-report-core 84 / nop-chart-report 3 全绿）
  - 两处 hook 各 1 行（远低于 30 行约束）；既有行为逐行等价 + golden 测试（TestSimpleHtmlReportRendererGolden）通过证明 provider 缺席输出与 WI7 前逐字节一致
  - D41-D46 裁定落实抽查全 PASS（签名零新依赖 git diff 空 / setImgType / 三态发现 / anchor 判空 calcSize 公式逐项一致 / 自限定解析 + 跨 sheet 拒绝 / hook 行数）
  - Anti-Hollow PASS：renderSheet→renderVisualOverlays→registry→provider→ChartExporter→GraalJS→dataUrl 逐环 file:line 追踪；落盘 HTML 含 15,916 字符真实 PNG payload；scan-hollow 双模块 0 发现
  - GraalJS 隔离：审计者独立跑 dependency:tree，nop-report-core 树中 graal/chart-echarts/chart-export 零命中；nop-report-core pom/源码零改动（porcelain 空）
  - xlsx 原生路径零改动：DrawingChartBuilder/ExpandedSheetChartGenerator porcelain 空
  - Deferred 项分类检查：SVG 呈现（WI9 承接）与其它 app opt-in 均真 out-of-scope；2 个执行中 live 校正（D45 自限定、nop-ioc 测试依赖）已回写 plan/javadoc/log
  - 审计 Major-2（payload 断言弱于声明）已修：改为「下一个右括号前 base64 长度 >100」真实断言，3 测试重跑绿；Minor-3/4/5（D46 名称对齐、log 数字 31→84、空 range null-safe 断言）已同步修正
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2365-wi7-chart-image-spi-html-render.md --strict` 证据回填后重跑退出码 0；check-doc-links --strict 退出码 0
