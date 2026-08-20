# format-pdf-svg 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-format/{nop-pdf,nop-svg,nop-chart-export}
- 文件数: 约 153（src/main/java：nop-pdf 110、nop-svg 13、nop-chart-export 30）
- 覆盖范围声明:
  - **逐行深读**: nop-pdf 解析主链路全部（ResourceDocumentParser / ExtractStripper / ExtractPageRender / ExtractPageDrawer / MemoryImageHandler / ResourceParseConfig / ResourceDocumentTool）、PdfDoc、PdfBoxHelper、全部 exporter（AbstractResourceDocumentExporter / Html / Txt / Image / DefaultResourceHtmlWriter）、ResourceDocumentHtmlParser、tabula 的 ObjectExtractor / PageIterator / Utils / Ruling(局部) / NurminenDetectionAlgorithm(重点段)；nop-svg 全部 13 个文件；nop-chart-export 主链路全部（ChartExporter / IChartExporter / ChartExportOptions / ChartTypeRendererRegistry / ChartDataValidator / IProgressCallback）+ 全部 12 个 renderer + ChartStyleApplier / JFreeChartStyleAdapter / ChartTextStyleProcessor / ManualLayoutProcessor（用法核查）。
  - **模式扫描覆盖（未逐行）**: nop-pdf 的 extract/processor（页眉页脚）、extract/data（表格定位）、extract/dashline、extract/table 内部算法，tabula 的 BasicExtractionAlgorithm / SpreadsheetExtractionAlgorithm / ProjectionProfile / QuickSort / Page / TextStripper 等算法内部（grep 空 catch、RuntimeException、printStackTrace、close、Process、字体加载均无独立命中）；chart-export 的 ChartDataFilter / ChartShapeStyleProcessor(局部)。
  - 测试代码、target/、`_` 前缀生成文件不在范围。三个模块均无 beans.xml/@Inject/@InjectValue（纯库模块，无 Nop IoC bean 定义），D7 未发现违背。D5 未发现 XXE/SSRF/命令注入：无外部进程调用（无 phantomjs/puppeteer，图表导出为进程内 JFreeChart）；HTML 回读用 nop-core 自研 XNodeParser 非 javax XML（不适用 XXE）；SVG 模块仅解析 path/transform 字符串，无外部实体解析。
  - 事实核对: PDFBox 3.0.3；JFreeChart 版本由父 pom 管理；`IResource.toFile()` 对非本地文件资源返回 null（nop-core IResource.java:149-152）；`IoHelper.readBytes` 委托 `readAllBytes()`（IoHelper.java:245-252）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 4 |
| P2 | 10 |
| P3 | 7 |

---

## 发现列表

### [P0] ResourceDocumentParser.open() 对同一 InputStream 连续读取两次，非文件资源在默认配置下解析必然失败

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/parser/ResourceDocumentParser.java:196-204`
- **维度**: D1 / D2
- **证据**:
```java
InputStream is = mPDFFile.getInputStream();
try {
    if (this.config.isMemoryRestrictEnabled()) {
        mPDFDocument = Loader.loadPDF(IoHelper.readBytes(is)); //is, settings);
    }
    mPDFDocument = Loader.loadPDF(IoHelper.readBytes(is));
} finally {
    IoHelper.safeClose(is);
}
```
- **现状**: `if` 分支内已经用 `IoHelper.readBytes(is)` 消费完流，随后第 201 行**无条件**再执行一次 `IoHelper.readBytes(is)` 并覆盖 `mPDFDocument`。第二次读取时流已到 EOF，`readAllBytes()` 返回空数组，`Loader.loadPDF(new byte[0])` 抛 IOException（非法 PDF 头）。
- **风险**: `ResourceParseConfig.memoryRestrictEnabled` 默认 `true`（ResourceParseConfig.java:89）。因此只要 `IResource.toFile()` 返回 null（classpath/VFS/内存等非本地文件资源，Nop 中常见），`parseFromResource` 必然抛异常，PDF 解析功能整体不可用；即便手工关掉 memoryRestrict，也只是退化为"整文件读入内存一次"。同时第一次 loadPDF 加载的文档被覆盖后未 close，PDFBox 内部缓冲直接泄漏。
- **建议**: 消除重复调用：`byte[] bytes = IoHelper.readBytes(is); mPDFDocument = Loader.loadPDF(bytes, null, keystore, alias);` 单次读取；第一次赋值失败/被覆盖前先 close。
- **误报排除**: 已核对 `IoHelper.readBytes`（nop-commons IoHelper.java:245-252）在流耗尽时返回空数组而非重置流；已核对 `IResource.toFile()` 契约（非本地文件返回 null）；已核对默认配置值。非"理论问题"，是默认路径必现缺陷。

---

### [P1] PdfDoc.splitIntoPages 差一错误：最后一页永远不会被拆出

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/core/PdfDoc.java:143-151`
- **维度**: D1 / D2
- **证据**:
```java
public void splitIntoPages(File dir) {
    dir.mkdirs();
    for (int i = 1, n = this.getNumberOfPages(); i < n; i++) {
        File file = new File(dir, StringHelper.leftPad(i + "", 3, '0') + ".pdf");
        selectPage(i).save(file);
    }
}
```
- **现状**: 循环条件 `i < n` 使 i 取值 1..n-1，**第 n 页（最后一页）被跳过**；n=1 时一个文件也不生成。
- **风险**: 调用方拿到"完整"的拆分结果但缺最后一页——静默数据丢失。附带问题：`selectPage(i)` 每次新建的 PDDocument 在 save 后从未 close（PDFBox 文档对象持有 COS 缓冲，循环次数多时累积），属资源管理缺陷。
- **建议**: 条件改为 `i <= n`；`PdfDoc doc = selectPage(i); try { doc.save(file); } finally { doc.close(); }`。
- **误报排除**: 已通读整个方法确认无其他补偿逻辑；`selectPage` 内部对越界页仅跳过不报错，不掩盖该差一。

### [P1] SVGPath.toSVGString() 的 SEG_ARCTO 分支缺 break，路径含弧段时必然抛 RuntimeException；且 A 指令输出 8 个坐标

- **文件**: `nop-format/nop-svg/src/main/java/io/nop/svg/model/SVGPath.java:450-485`（重点 475-480）
- **维度**: D1 / D4
- **证据**:
```java
case SEG_ARCTO:
    sb.append("A").append(coords[0]).append(' ')...append(coords[7]);
default:
    throw new RuntimeException("Unrecognised segment type " + segType);
```
- **现状**: `case SEG_ARCTO` 拼接完字符串后**没有 break**，直接贯穿到 `default` 抛 `RuntimeException("Unrecognised segment type 4321")`。另外 `coords` 为 `new float[8]` 而 arcTo 只存 7 个值（rx/ry/angle/laf/sf/x/y，见 138-146 行与 EPI 的 7 值 arraycopy），`coords[7]` 恒为 0——即使补上 break，A 指令也会多输出一个无效参数（SVG `A` 恰好 7 参数）。
- **风险**: 任何含 `a/A` 命令的 path 调用 `toString()/toSVGString()` 即崩溃（bare RuntimeException，违反平台错误处理两档策略）。触发路径现实：`SVGPath.parse("M0 0 A5 5 0 1 1 10 10").toString()`。上游 Batik 原版对应代码（`ExtendedPathIterator.SEG_ARCTO` 分支，`float[7]`）带 break，此处为移植引入的缺陷。
- **建议**: `case SEG_ARCTO` 补 break；输出改为 7 个坐标（coords[0..6]）；异常改为 `NopException` + SVG 模块 ErrorCode。
- **误报排除**: 已确认 `arcRel/arcAbs` 经 AWTPathProducer（AWTPathProducer.java:170-177）真实写入 SEG_ARCTO 段；模块内 SVGPath 无其他仓库内调用方（外部使用者直接暴露于此缺陷），按"库公共 API 明确 bug、仓内暂无触发方"定级 P1。

### [P1] ExtractStripper 每解析一页都整页渲染 scale=4 的位图（约 32MB/页），仅为坐标换算

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/parser/ExtractStripper.java:82-83, 115-117` + `ResourceParseConfig.java:43`
- **维度**: D6 / D2
- **证据**:
```java
PDFRenderer pdfRenderer = new ExtractPageRender(this.stripperCallback, document, config);
image = pdfRenderer.renderImage(pageIndex, scale);   // scale 默认 4
...
g2d = image.createGraphics();
...
s = g2d.getTransform().createTransformedShape(s);    // writeString 中仅用于坐标换算
```
- **现状**: `stripPage` 对每一页无条件调用 `renderImage(pageIndex, scale)` 生成整页位图（A4@scale4 ≈ 2380x3368，TYPE_INT_RGB ≈ 32MB），而该位图仅被用作携带 `scale` 变换的画布来计算字符包围盒，从不用于输出（`enableImageDebug` 恒 false，见 P2-05）。`image` 字段还把最后一页位图持有到 parser 丢弃为止。
- **风险**: 大 PDF（数百页）解析时 CPU/内存放大 16 倍（4x4），OOM 与长尾延迟高发；这是服务端批量文本抽取的主链路（tryParsePage 每页调用）。
- **建议**: 位图仅承载变换，用 1x1 图或直接构造 `AffineTransform.getScaleInstance(scale, scale)` 做数学换算，删除 renderImage；确需页图时再按 `exportPageImage` 条件渲染。
- **误报排除**: 已通读 writeString 确认 g2d 只用于 draw 调试矩形 + getTransform；ExtractPageDrawer.drawImage 已被覆写为不落画面板，渲染成本主要来自位图分配与矢量绘制本身，量级结论成立。

### [P1] Bubble/Heatmap 渲染器把 null 数据点静默渲染为 (0,0) 幽灵数据

- **文件**: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/renderer/BubbleChartRenderer.java:66-78`（HeatmapChartRenderer.java:64-77 同型）
- **维度**: D1
- **证据**:
```java
double[][] data = new double[3][minSize];
for (int j = 0; j < minSize; j++) {
    Number xNum = xValues.get(j); ...
    if (xNum != null && yNum != null && zNum != null) {
        data[0][j] = xNum.doubleValue(); ...
    }   // null 时保留 double 数组默认值 0.0，仍进入 addSeries
}
dataset.addSeries(seriesName, data);
```
- **现状**: 值为 null 的数据点不跳过，而是以 `0.0` 占位进入数据集，渲染出位于原点、尺寸 0 的气泡/热块。
- **风险**: 表格数据含空单元格（ChartDataSet 由单元格引用解析而来，空单元格产 null 很常见）时，导出图出现错误的 (0,0) 数据点，扭曲坐标轴范围，属静默数据错误。同模块 ScatterChartRenderer（76-79 行）正确地跳过 null，标准不一致。
- **建议**: 收集非 null 点后按实际数量构造数组（或先计数再建数组），跳过 null 点。
- **误报排除**: 已核对 ChartDataSet（nop-excel）各 List 默认空表但元素可为 null；AbstractChartRenderer.handleMissingData 仅记日志不清理 null，null 确实能到达此处。

---

### [P2] BarChartRenderer 对 plotArea/barConfig 缺失无防护，直接 NPE

- **文件**: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/renderer/BarChartRenderer.java:37, 44, 102`
- **维度**: D1
- **证据**:
```java
ChartBarConfigModel barConfig = chartModel.getPlotArea().getBarConfig();  // plotArea 可为 null
...
if (barConfig.isStackedChart()) {   // barConfig 可为 null
```
- **现状**: `getPlotOrientation(barConfig)` 做了 null 防护，但 37 行解引用 `getPlotArea()`、44 行调用 `barConfig.isStackedChart()`、102 行 `barConfig.getGapWidth()` 都未防护。plotArea 为 null 时不会在前置链路被拦截——ChartDataResolver.resolveSeriesData 对 null plotArea 返回空列表（ChartDataResolver.java:32-35）。
- **风险**: 最小化 BAR 图模型（type=bar + 数据引用，未配置 plotArea/barConfig）触发 NPE，被 ChartExporter 包装成笼统的 ERR_CHART_RENDER_FAILED("null")，排障困难。同族 LineChartRenderer（38 行）做了 `getPlotArea() != null` 判断，行为漂移。
- **建议**: 与 LineChartRenderer 对齐：`chartModel.getPlotArea() != null ? ...getBarConfig() : null`，`barConfig != null && barConfig.isStackedChart()`。
- **误报排除**: 已核对 ChartModel/PlotArea 为模型字段默认 null（无强制初始化），且渲染链路无前置非空校验（ChartDataValidator 只校验 type/width/height）。

### [P2] ResourceDocumentParser 构造器吞掉 IOException，stripper 可能为 null 导致后续 NPE

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/parser/ResourceDocumentParser.java:93-97`
- **维度**: D4
- **证据**:
```java
try {
    ExtractStripper stripper = new ExtractStripper(new ThisCallback(), config);
    this.setResourceStripper(stripper);
} catch (IOException e) {
}
```
- **现状**: 空 catch 完全吞掉构造异常，`mResourceStripper` 保持 null；首次 `tryParsePage` 调用 `this.mResourceStripper.stripPage(...)`（456-458 行）抛裸 NPE。
- **风险**: 违反平台错误处理两档策略（异常吞噬、无日志无错误码），故障时表现为难以定位的 NPE 而非原始 IOException。
- **建议**: 构造器直接声明/包装抛出（`throw NopException.adapt(e)`），或至少记录日志并快速失败。
- **误报排除**: PDFTextStripper 构造器确实声明 `throws IOException`（ExtractStripper 构造器签名 55 行），非常规不可达分支。

### [P2] memoryRestrictEnabled/memoryRestrictSize 是死配置，PDFBox MemoryUsageSetting 从未生效

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/parser/ResourceDocumentParser.java:185-194` + `ResourceParseConfig.java:87-94`
- **维度**: D8 / D6
- **证据**:
```java
MemoryUsageSetting settings = null;
if (this.config.isMemoryRestrictEnabled()) {
    settings = MemoryUsageSetting.setupMixed(this.config.getMemoryRestrictSize());
}
...
if (this.config.isMemoryRestrictEnabled()) {
    mPDFDocument = Loader.loadPDF(file);// file, settings );
} else mPDFDocument = Loader.loadPDF(file);
```
- **现状**: `settings` 计算后从未传入任何 `Loader.loadPDF` 调用（参数被注释掉），两个分支代码完全相同。配置项 `memoryRestrictEnabled`(默认 true)/`memoryRestrictSize`(默认 256MB) 对行为零影响。
- **风险**: 契约漂移：用户以为开启了 256MB 混合内存限制，实际 PDFBox 3 默认全内存/临时文件策略不受控；大文档解析内存行为与配置预期不符。
- **建议**: PDFBox 3.x 用 `Loader.loadPDF(file, memUsageSetting)` 重载（3.0.3 存在 `loadPDF(File, MemoryUsageSetting)`）真正传入，或删除死配置避免误导。
- **误报排除**: 已通读 open() 全文确认 settings 无第二处使用；grep 确认 MemoryUsageSetting 在模块内仅此一处 import 使用。

### [P2] exportPageImage 配置链路断裂：两个内部开关无 setter 恒为 false，页图永远不会导出

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/parser/ResourceDocumentParser.java:55,453-459` + `ExtractStripper.java:33,142-144`
- **维度**: D8
- **证据**:
```java
private boolean exportPageToImage = false;          // 无任何 setter/赋值
...
if (this.config.isExportPageImage()) {
    String pageImageFile = mPDFFile + "-" + pageNo + ".png";
    this.mResourceStripper.stripPage(mPDFDocument, pageNo, exportPageToImage ? pageImageFile : null);
}
// ExtractStripper:
private boolean enableImageDebug = false;           // 同样无 setter
if (enableImageDebug && pageImgFile != null) { ImageIO.write(image, "png", new File(pageImgFile)); }
```
- **现状**: 用户设置 `config.setExportPageImage(true)` 后，代码计算了 pageImageFile，但传参被恒为 false 的 `exportPageToImage` 短路为 null；即使传了，`enableImageDebug` 恒 false 也从不写出文件。
- **风险**: 公开配置项静默无效（D8）；另 pageImageFile 用 `mPDFFile + ""`（资源路径）当文件路径，即使启用也写不到预期位置。
- **建议**: 删除双层开关，直接以 `config.isExportPageImage()` 判定，并用 workDir 拼接真实输出路径。
- **误报排除**: grep 全模块确认两个布尔字段除声明与读取外无写入点。

### [P2] PageIterator.next() 吞 IOException：printStackTrace 后返回 null 页

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/tabula/PageIterator.java:29-34`
- **维度**: D4
- **证据**:
```java
try {
    nextPage = objectExtractor.extractPage(pageIndexIterator.next());
} catch (IOException e) {
    e.printStackTrace();
}
return nextPage;   // 可能为 null
```
- **现状**: 页提取失败时打印堆栈到 stdout 并返回 null，迭代不终止。
- **风险**: `for (Page p : extractor.extract())` 的调用方（tabula 迭代入口）在后续解引用时 NPE，或静默丢失后续页；printStackTrace 违反平台日志规范（应使用 SLF4J + NopException）。
- **建议**: 抛出 `NopException.adapt(e)` 或跳过并 LOG.warn，绝不返回 null 元素。
- **误报排除**: PageIterator 是 ObjectExtractor.extract(Iterable) 的公开返回类型，PdfDoc.extractTables 即经 `extract(int)` → `range(...).next()` 使用（64-66 行），非死代码。

### [P2] NurminenDetectionAlgorithm.detect() 静默吞掉渲染/去文本异常，表格检测无声降级为空

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/tabula/detectors/NurminenDetectionAlgorithm.java:102-127`
- **维度**: D4
- **证据**:
```java
try {
    image = Utils.pageConvertToImage(page.getPDDoc(), pdfPage, 144, ImageType.GRAY);
} catch (IOException e) {
    return new ArrayList<>();          // 无日志
}
...
} catch (Exception e) {
    return new ArrayList<>();          // 无日志，连 RuntimeException 一并吞掉
} finally {
    ... removeTextDocument.close(); ...
} catch (IOException e) { e.printStackTrace(); }
```
- **现状**: 页面转图片失败或 removeText 失败时直接返回空检测列表，无任何日志；finally 中 close 失败走 printStackTrace。
- **风险**: 该算法是 SpreadsheetDetectionAlgorithm 之外的主检测路径，故障时表现为"没检测到表格"而非"检测失败"，用户无从分辨输入真无表格还是系统故障。
- **建议**: 至少 LOG.warn 带页码上下文；`catch (Exception)` 收窄为 IOException。
- **误报排除**: 已确认 detect(Page) 是 DetectionAlgorithm 接口实现、被表格抽取链路调用（tabula 包内 TableDetector/算法注册），可达。

### [P2] ResourceDocumentHtmlParser 目录回读 off-by-one + 无防护解析

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/parser/ResourceDocumentHtmlParser.java:102-107, 116-123`
- **维度**: D1
- **证据**:
```java
// 写侧: href='#p' + item.getPageNo()  (1-based 打印页码, DefaultResourceHtmlWriter.java:80-84)
int pageIndex = Integer.parseInt(hrefValue.substring(2));   // 得到 1-based 页码
...
ResourcePage page = doc.getPages().get(pageIndex);          // 却按 0-based list 取
tocItem.setPageNo(page.getDisplayPageNo());
// parseBlocks:
String idParam = child.attrText("id");
int pageBlockIndex = Integer.parseInt(idParam.substring(idParam.indexOf("-") + 1));  // id 缺失/格式错即 NPE/异常
```
- **现状**: 写侧 `id='p1'` 对应第一页（pageNo 从 1 起），回读用该 1-based 值直接索引 0-based `getPages()`，目录指向的 displayPageNo 系统性错位一页；页 1 的条目在单页文档中直接 IndexOutOfBounds。头部解析 `XNode.childByTag("head").childContentText("title")`（43-44 行）对缺 head/body 的输入同样裸 NPE。
- **风险**: `ResourceDocumentTool.parseFromHtml` 回读链路产出错误页码数据；仅自产 HTML 回读时多数场景"错一页"不易察觉，属静默数据错误。
- **建议**: `doc.getPages().get(pageIndex - 1)` 或改用 pageNo→page 映射；对 head/body/id 做空防护。
- **误报排除**: 已双向核对写侧（DefaultResourceHtmlWriter.writeToc / writePage 的 id 生成）与读侧索引语义，确认 1-based→0-based 错位成立。

### [P2] HeatmapChartRenderer.setupColorMapping 构造色带但从不设置 PaintScale，热力图渲染为单色

- **文件**: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/renderer/HeatmapChartRenderer.java:104-117`
- **维度**: D1 / D8
- **证据**:
```java
private void setupColorMapping(XYBlockRenderer renderer) {
    Color[] colors = { Color.BLUE, Color.CYAN, ... Color.RED };
    // TODO: 实现更复杂的颜色映射逻辑
    LOG.debug("Setting up color mapping for heatmap");
}
```
- **现状**: 方法只创建了局部 colors 数组，没有对 renderer 做任何设置。JFreeChart `XYBlockRenderer` 需要 `setPaintScale(new LookupPaintScale(...))` 才能按 z 值着色，否则所有块用同一默认颜色。
- **风险**: HEATMAP 类型导出的图丢失全部热度信息（输出视觉上是单色网格），功能名存实亡且无任何警告。
- **建议**: 按 z 值域构建 `LookupPaintScale` 并 `renderer.setPaintScale(...)`。
- **误报排除**: 已通读该类全部 117 行，除 blockWidth/Height 外无其他渲染器配置；无其他地方补设 PaintScale。

### [P2] ChartExportOptions 大量死配置项 + 超时检查无法中断渲染

- **文件**: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/ChartExportOptions.java:6-17` + `ChartExporter.java:62-90`
- **维度**: D8
- **证据**:
```java
private int dpi = 96;              // 全模块无消费
private float quality = 0.9f;      // 无消费
private String backgroundColor;    // 无消费
private long timeoutMs = 30000;    // 无消费（ChartExporter 用的是 timeoutSeconds*1000）
private int maxDataSize = 10000;   // 无消费（ChartDataValidator.checkDataVolumeLimit 无调用方）
// ChartExporter:
options.isAntiAlias() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB   // antiAlias 只改图片类型
if (timeoutMs > 0 && (System.currentTimeMillis() - startTime) > timeoutMs) { throw ... }  // 渲染完成后才检查
```
- **现状**: dpi/quality/backgroundColor/timeoutMs/maxDataSize 五个配置项整个模块无人读取；timeoutSeconds 与 timeoutMs 双字段语义重叠且取值不一致时静默忽略后者；`antiAlias` 只影响 BufferedImage 类型（ARGB/RGB，与抗锯齿无关），从未作为 RenderingHint 应用到 JFreeChart；超时检查在 `renderChart` 返回之后才执行，无法中断已失控的渲染。
- **风险**: API 契约与实现漂移：调用方设置这些选项得到"看似成功但无效果"的结果；恶意/超大模型可无限占用渲染线程。
- **建议**: 删除或实现死配置项；渲染线程化 + 真超时（或至少在数据解析分步处检查 deadline）；antiAlias 改为 chart.setAntiAlias / RenderingHints。
- **误报排除**: 已 grep 全模块逐一确认各 getter 无调用（backgroundColor 的命中均为图表模型样式而非 options）；IChartExporter/ChartExporter 在模块外无调用方，属库 API 契约问题而非线上故障。

### [P2] TrendLineRenderer 全部为空壳实现；ComboChartRenderer 无视数据集名称与 series 类型

- **文件**: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/renderer/TrendLineRenderer.java:106-161` + `ComboChartRenderer.java:57-107`
- **维度**: D8
- **证据**:
```java
// TrendLineRenderer
private void addLinearTrendLine(CategoryPlot plot, ChartTrendLineModel trendLine, int index) {
    // 线性趋势线实现
    // 这里需要根据数据计算线性回归
    LOG.debug("Adding linear trend line to category plot");
    applyTrendLineStyle(plot.getRenderer(), trendLine, index);   // 只改样式，从不计算回归线
}
// ComboChartRenderer
String seriesName = "Bar Series " + (i + 1);      // 丢弃 dataSet.getName()
int primaryCount = Math.max(1, dataSets.size() / 2);  // 按位置前半=柱、后半=线，忽略 series 自身类型配置
```
- **现状**: 趋势线（LINEAR/MOVING_AVERAGE/EXPONENTIAL）三种实现全部只应用样式、不向 plot 添加任何数据集——配置了趋势线等于没配；组合图不读 `dataSet.getName()`，图例显示"Bar Series 1"之类的编造名称，且 series 的图表类型配置被"前一半/后一半"的固定切分覆盖。
- **风险**: 模型能力（trendLine、系列名、系列级类型）与导出结果不符，静默产出与用户配置不一致的图片。
- **建议**: 趋势线基于 dataset 计算回归/移动平均并 addDataset；Combo 按 ChartSeriesModel 的类型分组并沿用数据集名称。
- **误报排除**: 已通读 TrendLineRenderer 全部 add* 方法确认无一处向 plot 添加数据；ComboChartRenderer 全文确认 name 未使用。

---

### [P3] AbstractResourceDocumentExporter.exportToWriter 默认抛 new UnsupportedEncodingException()

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/export/AbstractResourceDocumentExporter.java:45-48`
- **维度**: D4
- **证据**:
```java
@Override
public void exportToWriter(ResourceDocument doc, Writer out, String encoding) throws IOException {
    throw new UnsupportedEncodingException();
}
```
- **现状**: 用"不支持的编码"异常表达"未实现"，无消息无 cause。
- **风险**: 上抛后错误信息为空，误导排障方向（看似编码问题实为未实现）。子类均覆写，主链路不触发，故 P3。
- **建议**: 改为 `UnsupportedOperationException("exportToWriter not implemented")` 或声明抽象。
- **误报排除**: 确认 Txt/Html 子类均覆写该方法，默认实现仅在新增子类漏写时可达。

### [P3] SVGPath.clone() 吞 CloneNotSupportedException 并返回 this

- **文件**: `nop-format/nop-svg/src/main/java/io/nop/svg/model/SVGPath.java:705-708`
- **维度**: D4
- **证据**:
```java
} catch (CloneNotSupportedException ex) {
}
return this;
```
- **现状**: 异常被吞且返回原对象，违反 clone 语义（调用方以为是副本实际是别名）。
- **风险**: 本类实现 Cloneable，该分支实际不可达，为 Batik 原版遗留写法，故 P3。
- **建议**: 直接在 catch 中抛 `NopException`/AssertionError，正常路径返回 result。
- **误报排除**: 已确认 `implements Cloneable`（45 行），分支不可达，仅作规范问题记录。

### [P3] SVGPath.transform() 对弧线参数与未用数组尾部按 2D 点变换

- **文件**: `nop-format/nop-svg/src/main/java/io/nop/svg/model/SVGPath.java:490-493`
- **维度**: D1
- **证据**:
```java
public void transform(AffineTransform at) {
    path.transform(at);
    at.transform(values, 0, values, 0, values.length);
}
```
- **现状**: `values` 中 SEG_ARCTO 段存的 7 元组是 (rx, ry, angle, flag, flag, x, y)，不是坐标点对，按点变换在数学上错误；且 `values.length` 是容量（makeRoom 2 倍扩容），大于有效数据长度 numVals，尾部垃圾槽位也被变换。
- **风险**: 对含弧段路径（或任何触发过扩容的路径）调用 transform 后，`toSVGString()`/EPI 输出的几何数据损坏。模块内无调用方，属库 API 潜在缺陷，P3。
- **建议**: 仅变换 M/L/Q/C 段的点对；弧段需按 SVG 规范重算 rx/ry/angle（旋转角仅对 affine 的线性部分敏感）。
- **误报排除**: 已核对 makeRoom 扩容逻辑（716-741 行）确认容量>有效长度；已 grep 全仓确认 transform 无调用方，降级 P3。

### [P3] tabula Utils 单参 pageConvertToImage：close 文档后再渲染 + try-with-resources 双 close

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/tabula/Utils.java:282-289`
- **维度**: D2 / D1
- **证据**:
```java
public static BufferedImage pageConvertToImage(PDPage page, int dpi, ImageType imageType) throws IOException {
    try (PDDocument document = new PDDocument()) {
        document.addPage(page);
        PDFRenderer renderer = new PDFRenderer(document);
        document.close();                                   // 渲染前关闭
        return renderer.renderImageWithDPI(0, dpi, imageType);
    }
}
```
- **现状**: 先显式 close 再 render，且 try-with-resources 会再次 close（后者无害）。close 后页内容流的懒加载源可能已释放，渲染行为不可靠。
- **风险**: 该单参重载当前无调用方（Nurminen 用的是双参版本），属死代码携带缺陷，P3。
- **建议**: 删除该重载或移除渲染前的 close。
- **误报排除**: 已 grep 确认全模块仅双参版本被调用。

### [P3] SVGFontMapper 为空类

- **文件**: `nop-format/nop-svg/src/main/java/io/nop/svg/font/SVGFontMapper.java:10-11`
- **维度**: D8
- **证据**:
```java
public class SVGFontMapper {
}
```
- **现状**: 只有类声明，无任何成员。SVG 模块的"字体映射"能力（类名承诺的契约）不存在。
- **风险**: 使用者按类名预期功能会落空；编译产物含僵尸类。
- **建议**: 删除或补充实现。
- **误报排除**: 已读全文（11 行）；grep 确认全仓无引用。

### [P3] ObjectExtractor.close() 关闭不属于自己的 PDDocument

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/tabula/ObjectExtractor.java:8-14, 69-71`
- **维度**: D2 / D8
- **证据**:
```java
public class ObjectExtractor implements java.io.Closeable {
    private final PDDocument pdfDocument;   // 构造器注入的外部文档
    public void close() throws IOException {
        pdfDocument.close();
    }
}
```
- **现状**: 实现 Closeable 但 close 时直接关闭构造器传入的外部文档（上游 tabula 中文档由其自行加载，此处语义已变）。`PdfDoc.extractTables` 恰好未用 try-with-resources 才没踩坑。
- **风险**: 任何调用方按 Closeable 惯例 try-with-resources 使用时，会意外关闭宿主 PdfDoc 的底层文档，后续操作全部失败。
- **建议**: close 改为空操作或文档注释明确"不拥有文档"；或改由调用方负责生命周期。
- **误报排除**: 已核对 PdfDoc.extractTables（PdfDoc.java:153-158）未关闭 extractor，当前仓内安全；风险在 API 语义层。

### [P3] ResourceDocumentTool 可变单例：_instance 非 final 且 processors 列表可并发替换

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/extract/ResourceDocumentTool.java:25-35, 43-47`
- **维度**: D3
- **证据**:
```java
static ResourceDocumentTool _instance = new ResourceDocumentTool();   // 非 final
List<IResourceDocumentProcessor> processors;                          // 无同步
public void setPostProcessors(List<IResourceDocumentProcessor> processors) { this.processors = processors; }
// extractTextFromResource 中: for (IResourceDocumentProcessor processor : this.processors) ...
```
- **现状**: 单例字段可被重设，processors 列表可被并发 set，遍历与替换无同步。
- **风险**: 运行期被并发 setPostProcessors 时可能 NPE（置 null）或遍历到不完整列表。当前为低频管理操作，P3。
- **建议**: `_instance` 加 final；processors 用 volatile 不可变列表或 CopyOnWriteArrayList。
- **误报排除**: 已 grep 确认 setPostProcessors 在 src/main 无调用（预留 API），实际触发面小，定 P3。

---

## 总结

- 最需要优先处理: P0 的 open() 双读（默认配置下非文件 PDF 解析全挂）、P1 的 splitIntoPages 差一（静默丢页）与 ExtractStripper 每页 32MB 位图（大文档 OOM）。
- nop-svg 与 nop-chart-export 作为库模块，主要问题是**契约漂移**（配置项/功能静默不生效：趋势线、热力图着色、组合图系列名、导出选项五个死配置），以及两处确定性实现缺陷（toSVGString 缺 break、Bubble null→0）。
- 未发现 D5（XXE/SSRF/命令注入）与 D7（Nop IoC/错误码规范）层面的违背；D3 方面 chart-export 渲染器均为无状态、注册表用 ConcurrentHashMap，并发安全。
