# 2366 WI8 PDF/docx 出图与三格式回归

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: ai-dev/backlog/echarts-server-render-roadmap.md WI8（M4）；依赖 plan 2365（WI7 已完成，commit 50fb6113cf）
> Related: plan 2364（ChartExporter）、plan 2367（WI9 对版验收复用 pdf 渲染腿）

## Purpose

落地 roadmap WI8：nop-report-pdf PdfSheetRenderer 增 renderCharts（镜像 renderImages 路径，PdfImageHelper 增 byte[] 重载，countImageXObjects 断言）；nop-report-docx XptWordTableRenderer 表后插图（pointsToTwips/EMU 换算）；test-bar-chart.xpt.xlsx 的 xlsx/html/pdf/docx 四路断言收口，xlsx 原生路径零改动回归。

## Current Baseline

以下事实已于 2026-10-10 在 live repo（分支 echarts-server-render @ 50fb6113cf）核验：

- **PDF 渲染**：PdfSheetRenderer.renderSheet(4 参) L101-194 在 renderTable 后 L187 调 renderImages（sheet.getImages() 驱动：isPrint 过滤、anchor.getStartPosition 判空、calcSize、region 偏移 startX/startY=前置列宽/行高和、Y 轴翻转 `drawImage(..., imageX, -imageY - h, w, h, pageClipRect)`）。**PdfImageHelper 只有 ExcelImage 形态**（getData() ByteString → PdfRenderer.getImage 缓存 → PDImageXObject；clipRect 走 saveGraphicsState；失败画灰占位），无 byte[] 重载；`PdfRenderer.getImage(ByteString)` L188-196 带 imageCache。countImageXObjects 是 TestPdfRenderDefects 内 package-private 静态助手（L547-561，pdfbox 3.x Loader + PDResources/PDImageXObject），同包测试可直接复用。
- **docx 渲染（非 POI）**：XptWordTableRenderer 用 CollectXmlHandler 直接拼 OOXML 文本，renderExpandedSheet 在 endNode("w:tbl")（L74）结束——表后插图插入点。列宽已用 UnitsHelper.pointsToTwips（L59；UnitsHelper L250-252 = pt×20）。媒体部件 API = WordOfficePackage.addImage(fileExt, IResource) → OfficeRelationship（getId()）→ r:embed；wp:extent 单位 EMU（1pt=12700）。**关键接线事实（R2 核验）：WordTemplate.generateToDir 每次生成先 pkg.copy() 并把副本放入生成期 scope（VAR_OFC_PKG="ofcPkg"），outputs 在该 scope 下生成、副本被序列化——编译期与生成期 package 非同一实例；GenXptTable 宏 AST 的 $scope 即该生成期 scope，renderer 的 context.getEvalScope() 可直达 ofcPkg 副本（D49）。当前 `new XptWordTableRenderer(sheet)` 不持 pkg 也无需持。**
- **docx fixture**：test-word-report.docx（TestXptWordTemplate 冒烟用）是否含 xpt:table 注释待实现首步核实；XptWordTemplateParser.postProcessDocNode 仅当 pkg.getComments() 非空才替换 GenXptTable。
- **WI7 交付面**：IChartImageProvider.renderCharts(IExcelSheet)→List<ExcelImage>（provider 产的图**无 anchor、直接带 left/top/width/height pt**——PDF 侧不得走 calcSize/anchor 路径）；ChartImageProviderRegistry.getProvider()（三态）；nop-chart-report 测试 scope 依赖传递 GraalJS（隔离约束：仅 test classpath）。pdf/docx main 零新依赖（nop-excel 经 nop-report-core→nop-ooxml-xlsx 传递可达）。
- **依赖现状**：nop-report-pdf pom = nop-report-core + jts + pdfbox + nop-autotest-junit(test)；nop-report-docx pom = nop-report-core + nop-ooxml-docx + nop-autotest-junit(compile)。nop-bom 已有 nop-chart-report 条目（WI7 加）。
- **测试现状**：pdf 侧 TestPdfRenderDefects（countImageXObjects 仅用于程序化 workbook 用例）+ TestPdfReportRenderer（test-image.xpt.xlsx 冒烟不校验）；docx 侧 TestXptWordTemplate（冒烟）+ TestXptWordTableRenderer（XML 字符串断言先例）；html 腿 = TestHtmlChartRender（WI7）；xlsx 腿 = TestReportChart（冒烟、不经 SPI、零改动即回归）。
- **fixture**：test-bar-chart.xpt.xlsx 两份（core 与 nop-chart-report，md5 相同）；pdf/docx 测试资源无此 fixture，需复制。

## Goals

- PDF：PdfSheetRenderer.renderCharts（provider 发现 + hasChart 短路 + region 包含 + Y 翻转 + clipRect，直用 left/top/width/height 不走 anchor/calcSize）+ PdfImageHelper.drawImage byte[] 重载；测试 countImageXObjects ≥1 断言。
- docx：XptWordTableRenderer 生成期从 eval scope 读 ofcPkg 副本（D49）→ 表后图表段落（w:p/w:r/w:drawing 内联，extent pt→EMU），图片经生成期副本 addImage 注册关系；测试断言包内 word/media/image* + document.xml 含 w:drawing。
- 四路回归收口：xlsx（既有测试零改动即原生路径回归）+ html（WI7）+ pdf/docx（本计划）。
- provider 缺席回归：既有 pdf/docx 测试（无 chart fixture）全绿 + hasChart/registry-null 短路。

## Non-Goals

- 17 图型对版（WI9）；owner doc（WI10）；SVG 嵌入 pdf/docx（png 统一）；xlsx 渲染器任何改动。
- nop-report-core / nop-excel / nop-chart-report 的任何源码改动（SPI 面已够用；若实现发现必须改 SPI，先改本 plan 再动）。
- 浮动锚定/图文绕排（docx 用表后内联段落，与 roadmap「表后插图」一致）。

## Adjudications（延续 D 编号，接 plan 2365 的 D46）

- **D47 PDF 图表绘制路径（R1 M1 补归属规则）**：renderCharts 复用 renderImages 的 region 偏移/Y 翻转/clipRect，但**独立循环、不走 anchor/calcSize**（provider 图直接带 left/top/width/height pt，与 renderImages 同为 sheet 绝对 pt 坐标系）；**分页归属 = left/top 落入 region 的坐标范围判定**（startX ≤ left < startX+regionWidth 同理 Y，替代 renderImages 的 anchor.containsCell——无 anchor 图不可用）；跨页图归首个包含页、超出部分由 clipRect 裁剪。跳过条件 = provider null（静默，D43 同义）或 !sheet.hasChart() 或 charts 空；单图 data 为空跳过（判据在 PdfImageHelper 既有 data 判空路径，非 renderImages L213 的 isPrint）。chart 的 isPrint 默认 true → 全部绘制。**防呆：renderCharts 产物不得并入 sheet.getImages()**（renderImages 的 anchor.getStartPosition() 对无 anchor 图 NPE——PDF 侧不可照搬 html 腿的合并模式）。
- **D48 PdfImageHelper byte[] 重载**：`drawImage(..., byte[] imageData, ...)` 内部 ByteString.of 复用 PdfRenderer.getImage 缓存与灰占位失败路径；ExcelImage 形态委托新重载（消除重复）。
- **D49 docx pkg 获取（R1 B2 预裁定，替代编译期同一性探针）**：WordTemplate.generateToDir 每次生成先 `pkg.copy()` 并把副本放入生成期 eval scope（`OfficeConstants.VAR_OFC_PKG = "ofcPkg"`），outputs 生成后序列化该副本——**编译期与生成期 package 不同一，媒体部件必须写入生成期副本**。XptWordTableRenderer.generateToWriter 在 `context.getEvalScope()` 读 `ofcPkg`（强转 WordOfficePackage）后 addImage；GenXptTable 宏的 `replaceIdentifier('output', output)` 捕获编译期 renderer 实例、生成期 scope 链可达 ofcPkg（机制已核）。**scope 无 ofcPkg（直接单元调用 renderer）→ debug log 跳过图表渲染（D43 同族显式裁定，有回归测试钉住）。**
- **D50 docx 图表来源与段落形态（R1 B1 裁定）**：docx 解析链（WordTable→ExcelTable→xpt model）**不存在 chart**——图表部件只源于 xlsx 模板 drawing，docx 腿的图表由**测试程序化注入** ExcelChartModel（sheet.setCharts(...)，anchor 可缺省给 width/height pt），渲染链 = 程序化 xptModel → registry → provider → ChartExporter → png → 生成期 ofcPkg.addImage → drawing。**执行注（R2 F2/F3）**：注入 chart 必须 type+plotArea+seriesList 齐全（ExpandedSheetChartGenerator 对缺失者静默跳过）、数据用 cellRef 指向程序化 sheet 表内已填值单元格（ChartStaticDataModel 无管线消费者不可用）；xpt model 须带 VAR_XPT_NODE XNode 接线（复刻 compileTable 的 public 变换链或参照 TestXptWordTableRenderer 手写先例，compileTable 产物无法事后注入 charts）；docx 模板全链路（test-word-report.docx，已核实含 xpt:table 注释）只承担**无图表零变化回归**。段落形态：表后内联 `w:p > w:r > w:drawing > wp:inline > a:graphic > pic:pic`，extent cx/cy = pt×12700（EMU），r:embed = addImage("png", ByteArrayResource).getId()；多图按序追加段落。图片注册失败上抛（D43 语义）。
- **D51 四路断言口径**：xlsx 腿 = 既有 TestReportChart 原样绿（原生 OOXML 路径零改动回归，不新增断言不改动）；html 腿 = TestHtmlChartRender（WI7，已绿）；pdf 腿 = 新测试渲染 fixture → countImageXObjects ≥1 + provider 缺席/无 chart 时 =0 或既有回归；docx 腿 = 新测试渲染 fixture → 包含 media image 部件 + document.xml w:drawing + 字符串断言先例。
- **D52 测试位置与 provider 注入（R1 M2 校正）**：pdf/docx pom 各加 nop-chart-report test scope（bom 管理）；fixture 副本入各自 test resources。**WI7 真实先例 = CoreInitialization + BaseTestCase + nop-ioc(test) 显式依赖**（非 JunitBaseTestCase——autotest 全家桶会带入 dao/datasource beans）；pdf 模块若复用既有 JunitBaseTestCase 需在实现首步确认新增 autoconfig（provider bean）不破坏容器启动，失败则按 WI7 教训收窄测试依赖。countImageXObjects 以同包测试复用 TestPdfRenderDefects 的 package-private 静态（不提升不改既有类）。

## Scope

### In Scope

- nop-report/nop-report-pdf：PdfSheetRenderer.renderCharts、PdfImageHelper byte[] 重载、pom test 依赖、fixture 副本、新测试。
- nop-report/nop-report-docx：XptWordTableRenderer pkg 接线与表后图表段落、pom test 依赖、fixture、新测试。
- ai-dev/plans/2366-*.md、当日 ai-dev/logs/。

### Out Of Scope

- nop-excel/nop-chart-report/nop-report-core 源码；xlsx 渲染器；docs-for-ai owner doc（WI10）。

## Execution Plan

### Phase 1 - PDF 出图

Status: completed
Targets: nop-report/nop-report-pdf/

- Item Types: `Feature`

- [x] PdfImageHelper.drawImage byte[] 重载（D48，ExcelImage 形态委托之）
- [x] PdfSheetRenderer.renderCharts（D47：provider 发现/hasChart 短路/region 偏移/Y 翻转/clipRect/无 anchor 直用坐标）+ renderSheet L187 后调用点
- [x] pom 加 nop-chart-report test scope + fixture 副本 test-bar-chart.xpt.xlsx
- [x] 测试：fixture 渲染 → countImageXObjects ≥1；无 chart 既有用例不回归（renderImages 路径不变）；D47 归属规则的可观察断言（程序化单页 sheet+chart → 该页 XObject 计数 ≥1；多页场景归首含页，clipRect 裁剪不崩）

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] countImageXObjects 断言在位（fixture ≥1）
- [x] **接线验证**（Rules #23）：renderCharts 经 ChartImageProviderRegistry.getProvider() 实际调用了 provider.renderCharts（IoC 路径测试即证据）
- [x] **无静默跳过**（Rules #24）：provider null 跳过是 D43 显式裁定；provider 异常上抛
- [x] 当日 `ai-dev/logs/` 条目已更新；No owner-doc update required（WI10）

### Phase 2 - docx 出图

Status: completed
Targets: nop-report/nop-report-docx/

- Item Types: `Feature`

- [x] XptWordTableRenderer 生成期 hook（D49）：endNode("w:tbl") 后从 eval scope 读 ofcPkg → registry.getProvider()（null 或 !hasChart 或 scope 无 pkg → debug log 跳过）→ renderCharts → 每图生成期副本 addImage("png", ByteArrayResource) → 表后内联图表段落（D50：extent EMU，r:embed 关系 id）
- [x] pom 加 nop-chart-report test scope + fixture（test-word-report.docx 已核实含 xpt:table 注释，直接用）
- [x] **图表来源（D50）**：测试程序化构造 ExcelSheet + ExcelChartModel（setCharts 注入，anchor 缺省给 width/height pt）→ 直接调 renderer.generateToWriter → 断言 document.xml 含 w:drawing、r:embed 关系在 word/_rels/document.xml.rels、media 含 png 部件
- [x] **零变化回归**：docx 模板全链路（test-word-report.docx 经 XptWordTemplateParser）输出与基线一致（无 chart → 无 drawing，golden/字符串断言先例）；scope 无 ofcPkg 分支断言（debug log 跳过，输出无图表段落）

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿
- [x] **端到端验证**（Rules #22）：程序化注入图表的 xptModel → renderer → registry → provider → GraalJS 引擎 → 生成期 ofcPkg media 部件 + w:drawing XML，全链路测试通过
- [x] **接线验证**（Rules #23）：hook 经 ChartImageProviderRegistry.getProvider() 实际调用 provider.renderCharts（注入测试即证据）
- [x] **无静默跳过**（Rules #24）：scope 无 pkg / provider null / 无 chart 三种跳过均为显式裁定且有断言钉住；provider 异常上抛
- [x] docx 模板全链路零变化回归在位（无 chart → 输出与基线一致）
- [x] 当日 `ai-dev/logs/` 条目已更新；No owner-doc update required（WI10）

### Phase 3 - 四路回归收口

Status: completed
Targets: 仓库构建 + 测试矩阵

- Item Types: `Proof`

- [x] 四路矩阵核对：xlsx = TestReportChart 原样绿（零改动）；html = TestHtmlChartRender 绿；pdf = Phase 1 新测试绿；docx = Phase 2 新测试绿
- [x] `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-docx -am` 全绿（或分次）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 与 docx 同款退出码 0
- [x] check-import-order 两模块 0 新增错误；check-doc-links --strict 退出码 0
- [x] 独立 closure audit（新子代理）完成，证据写入本文件 Closure 段

Exit Criteria:

- [x] 上述命令全部退出码 0
- [x] 当日 `ai-dev/logs/` 收口条目与 plan/roadmap 状态一致

## Closure Gates

- [x] roadmap WI8 scope 全落地：renderCharts + byte[] 重载 + docx 表后插图 + 四路断言
- [x] 无空方法体/占位实现/静默跳过（scan-hollow + 人工核查；缺席跳过为 D43/D47 显式裁定）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] main 零新依赖（pdf/docx pom main scope git diff 无新增）；GraalJS 仅经 test scope nop-chart-report 进入测试 classpath
- [x] xlsx 原生路径零改动（TestReportChart 与 xlsx 渲染器零 diff）
- [x] 四路断言在位且绿（D51 口径）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 renderSheet/renderExpandedSheet → registry → provider → ChartExporter → 引擎 → pdf/docx 产物链运行时真实连通（E2E 即证据）
- [x] `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-docx -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2366-wi8-pdf-docx-chart-render.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- docx 浮动锚定图表（绝对定位叠放）——表后内联已满足 roadmap「表后插图」，浮动形态另议
- countImageXObjects 提升为共享测试工具类（当前同包复用）

## Closure

Status Note: WI8 全部交付——PdfSheetRenderer.renderCharts（D47 坐标范围分页归属/独立循环防呆）+ PdfImageHelper byte[] 重载（D48）+ XptWordTableRenderer 生成期 ofcPkg 图表段落（D49/D50）+ 四路断言收口。closure audit 实证的 Major（drawing 片段 xmlns:a/pic 缺失——装配后 XML 非法）已修复：wp:inline/a:graphic/pic:pic 节点内联声明 xmlns:wp/r/a/pic；新增装配可解析性断言（并由此抓出 a:graphicData simpleNode/endNode 错配第二缺陷，同修）；注释与 log 不实表述已更正；孤儿 javadoc 已修。4 项执行中 live 校正回写 log。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor（独立子代理，2026-10-10/11，live repo @ echarts-server-render 工作树未提交变更）
- Evidence:
  - 测试矩阵亲跑 `./mvnw test -pl nop-report/nop-report-pdf,nop-report/nop-report-docx -am -o`：全绿（surefire 报告 2026-10-11 02:31）。pdf：TestPdfChartRender 1/1（fixture→countImageXObjects≥1，provider 经 IoC assertNotNull）+ TestPdfRenderDefects 12/12 不回归；docx：TestDocxChartRender 3/3（注入 E2E w:drawing/r:embed/media+rels 反查、零变化回归、provider 缺席静默）+ TestXptWordTemplate/TestXptWordTableRenderer 不回归；xlsx 腿 TestReportChart 1/1（文件零 diff）；html 腿 TestHtmlChartRender 3/3（nop-chart-report，文件零 diff）。
  - Anti-Hollow 逐环追踪：PdfSheetRenderer.renderSheet L190→renderCharts L244-282→registry（ChartImageProviderRegistry L39-46）→ExcelChartImageProvider.renderCharts L42-54→ChartExporter.exportToImage L59→PdfImageHelper.drawImageImageData L50-81（contentStream.drawImage PDImageXObject L66）；docx：XptWordTableRenderer.generateToWriter L56→renderExpandedSheet L61→renderCharts L92-127→WordOfficePackage.addImage L117-119→emitChartParagraph L124+；生成期 pkg=WordTemplate.generateToDir L71-75（pkg.copy()→scope VAR_OFC_PKG→copy.generateToDir）。scan-hollow 双模块 exit 0。
  - D47/D48/D49/D51/D52 核实通过（坐标范围归属 L258-274+防呆注释；byte[] 重载无重复签名；ofcPkg instanceof 守卫+debug log 跳过；四路口径；JunitBaseTestCase 容器启动实测正常=符合 D52 允许条件）。pom main 零新增、xlsx 原生路径零 diff、renderExpandedSheet 全仓单调用方无子类。
  - **一项 Major 未过（阻断本次收口）**：emitChartParagraph 输出 wp:/a:/pic:/r: 前缀元素但未发射任何 xmlns 声明；fixture document.xml 根只声明 w/r/wp 等、无 xmlns:a/xmlns:pic；GenXptTable 宏（docx-gen.xlib L13）原文嵌入，全链无补全——图表经模板管线装配进 document.xml 后命名空间不合法（Word 拒开）。代码注释与当日 log「命名空间内联声明」与实现不符；测试仅对 fragment 做字符串断言、从不装配/解析整文档，故未捕获。修复建议：在 wp:inline（及 a:graphic/pic:pic）上内联声明 xmlns:a/xmlns:pic/xmlns:wp/xmlns:r 四前缀，并在测试中增加装配后 XML 可解析性断言；或显式改判 Deferred 并同步修正注释/log。修复后需重跑 docx 测试并重过 check-plan-checklist --strict。
  - 工具：check-doc-links --strict exit 0；check-import-order 双模块 0 错；check-plan-checklist --strict 于 Closure 回填前 exit 1（占位符所致，修复后须复验）。
  - **审计后修复轮（实现者，2026-10-11）**：① emitChartParagraph 内联声明 xmlns:wp/r（wp:inline）+ xmlns:a（a:graphic）+ xmlns:pic（pic:pic），常量提取 WP_NS/A_NS/PIC_NS/R_NS；② 新增装配可解析性断言（片段 + w 根包装 XNode.parse + xmlns 三前缀存在性）——该断言抓出 a:graphicData simpleNode/endNode 错配（simpleNode 自闭合 vs endNode 闭标签），改 beginNode 后通过；③ PdfImageHelper 孤儿 javadoc 合并；④ 注释与 log「命名空间内联声明」表述同步为实证后语义。修复后 TestDocxChartRender 3/3 绿，checklist --strict exit 0。
