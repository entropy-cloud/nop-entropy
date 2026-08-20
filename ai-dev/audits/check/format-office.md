# format-office 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-format/{nop-ooxml,nop-office-model,nop-office-doc-model}
- 文件数: 约 206（src/main/java，其中 `_gen/` 生成文件 23 个，按规则跳过；手写文件 176 个：nop-ooxml 126、nop-office-model 17、nop-office-doc-model 17）
- 覆盖范围声明:
  - 全量结构扫描（文件清单 + 可疑模式 grep: 空 catch、`new RuntimeException`、`printStackTrace`、`ZipEntry`、`DocumentBuilderFactory/SAXParserFactory`、静态可变状态、`toFile()`）。grep 未发现空 catch、`new RuntimeException(...)` 直接抛出点、`printStackTrace`。
  - 深读: OPC 包核心（OfficePackage、PackagingURIHelper、PackagePartName、OfficeRelsPart、ContentTypesPart、IOfficePackagePart 及其 4 个实现）、xlsx 解析链（AbstractXlsxParser、ExcelWorkbookParser、SheetNodeHandler、SimpleSheetContentsHandler、SharedStringsTableParser、StylesPartParser、XlsxToRecordOutput、CommentsPart）、xlsx 输出链（ExcelTemplate、ExcelWriteSupport、ExcelSheetWriter、ExcelCommentsWriter、GenState、WorkbookPart、ExcelOfficePackage）、docx 链（WordTemplateParser、WordTemplate、OfficeDocModelParser、OfficeDocModelWriter、WordOfficePackage、WordHyperlink、WordXmlHelper）、pptx 链（PptOfficePackage、SlidePart）、markdown 转换器 2 个、office-model/office-doc-model 全部手写文件。
  - 抽查: chart 包（单例 Parser/Builder 的线程安全性、ChartStyleProviderFactory）、WordTable/WordTableCell 等模型类。
  - 为验证 D5/D2 结论，查证了依赖侧 nop-kernel/nop-core 的 XNodeParser、ZipResourceStore/FileScanHelper、JdkZipOutput、CellReferenceHelper、CollectionHelper、StringHelper（这些不属于检查单元，仅作证据链）。
  - 测试代码、target/、`_` 前缀生成文件不在范围。
  - XXE 结论（已验证，非发现）: 三个模块不使用 JAXP/SAX 工厂（grep 无命中），所有 XML 解析走 nop-core 手写 XNodeParser；DOCTYPE 被读取后直接丢弃（XNodeParser.parseDOCTYPE），自定义实体直接报 `ERR_XML_UNKNOWN_XML_ENTITY`，不存在外部实体解析与内部实体展开，经典 XXE/billion-laughs 不可达；嵌套深度有 `CFG_XML_MAX_NESTED_LEVEL` 限制。
  - D7 结论（无发现）: 三个模块为纯类库，无 IoC 装配代码（无 @Inject/@Component/beans.xml），不涉及 private 字段注入问题；错误处理以 NopException + OfficeErrors/XlsxErrors/DocxErrors + .param(...) 为主，符合两档策略（个别裸异常见 P2/P3 发现）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 3 |
| P2 | 5 |
| P3 | 8 |

## 发现列表

### [P0] SharedStrings 解析按不可信的 uniqueCount 属性预分配内存，极小输入即可触发 OOM

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SharedStringsTableParser.java:66-72`
- **维度**: D5（安全，zip 炸弹同级资源耗尽）+ D1
- **证据**:
```java
public void beginNode(SourceLocation loc, String localName, Map<String, ValueWithLocation> attrs) {
    if ("sst".equals(localName)) {
        this.count = getAttrInt(attrs, "count", 0);
        this.uniqueCount = getAttrInt(attrs, "uniqueCount", 0);

        this.strings = new ArrayList<>(this.uniqueCount);
```
- **现状**: `uniqueCount` 是上传 xlsx 中 `xl/sharedStrings.xml` 根元素的属性，完全由文件内容控制。`new ArrayList<>(uniqueCount)` 用该值做初始容量直接分配 Object 数组。上传一个几十字节的 sharedStrings.xml（声明 `uniqueCount="2000000000"`）即请求约 8GB 连续数组，立即 `OutOfMemoryError`；负值则抛裸 `IllegalArgumentException: Illegal Capacity`。触发路径现实：`ExcelHelper.parseExcel / ExcelWorkbookParser.parseFromResource → AbstractXlsxParser.loadFromResource`（上传导入的标准入口），文件体积上限/压缩率检查均不能阻止（属性值不占体积）。附带问题：若根标签不是 `sst`，`strings` 保持 null，后续 `endNode("si")` 的 `strings.add(...)` 与 `getItemAt` 的 `items.size()` 均为 NPE。
- **风险**: 解析不可信上传文件的服务可被单个微型文件打成 OOM，造成请求线程崩溃乃至整个 JVM 不稳定（DoS）。
- **建议**: 预分配容量做上限截断（如 `Math.min(uniqueCount, 10_000)` 或引入配置项）；或直接 `new ArrayList<>()` 让其按实际条目增长；同时校验 `uniqueCount < 0`、根标签合法性。可参考 POI ReadonlySharedStringsTable（不按 uniqueCount 预分配）。
- **误报排除**: 已确认 `AbstractXlsxParser.loadFromResource` 对存在的 sharedStrings 部件无条件调用 `new SharedStringsTableParser(true).parseFromPart(part)`（AbstractXlsxParser.java:63-64），上传解析必经；`getAttrInt` 不做范围校验。

### [P1] OfficePackage.copy() 为浅拷贝：缓存模板与每次渲染副本共享可变部件，rels 持久污染 + 并发渲染数据竞争

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/OfficePackage.java:115-117`（根因）；`nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/model/WordOfficePackage.java:84-102`；`nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/output/ExcelTemplate.java:66-71`
- **维度**: D3（并发）+ D1（状态污染）
- **证据**:
```java
// OfficePackage.java:115 —— 浅拷贝，parts 对象本身被共享
public void copyTo(OfficePackage pkg) {
    pkg.files.putAll(files);
}

// WordTemplate.java:61-63 —— 设计声明可缓存复用
// 将模板中所有内容都读入到内存中，从而不再持有外部zip文件的引用，可以被安全的缓存并重复使用。
pkg.loadInMemory();

// WordOfficePackage.addImage —— 生成期通过 ofcPkg（即 copy）调用
OfficeRelsPart part = getRels(DocxConstants.PATH_WORD_RELS); // 返回模板中缓存的共享 OfficeRelsPart 实例
...
return part.addImage(target);  // 直接修改共享实例

// ExcelTemplate.java:67-71
ExcelOfficePackage pkg = this.modelPkg.copy();
...
pkg.getWorkbook().clearSheets(); // WorkbookPart 包装模板共享的 workbook.xml XNode，clearBody 直接改共享节点
```
- **现状**: 证据链（docx rels 污染，实锤）: ① `WordTemplateParser.compile()` 解析期调用 `pkg.getRelsForPartPath(path)`，`OfficePackage.getRels()` 会把解析出的 `OfficeRelsPart` 实例 `files.put` 进**模板**包（OfficePackage.java:219-231），且 `loadInMemory()` 对其返回 `this`；② 每次渲染 `WordTemplate.generateToDir` 的 `copy()` 共享该实例，`getRels` 命中 `instanceof OfficeRelsPart` 直接返回共享对象；③ 模块自带的 `nop-ooxml-docx/src/main/resources/_vfs/nop/ooxml/xlib/docx-gen.xlib:23` 的 `<docx-gen:Drawing>` 标签在**生成期**执行 `let rel = ofcPkg.addImage(resource);`（ofcPkg 即 copy）→ `OfficeRelsPart.addRelationship` 修改共享 HashMap。每渲染一次含图片的 word，缓存模板的 `word/_rels/document.xml.rels` 就永久追加一条指向 `media/imageN.png` 的关系（media 文件本身只进 copy 的 files，不进模板），后续每个输出文件的 rels 都带着此前所有渲染的孤儿关系，且随渲染次数线性膨胀。证据链（xlsx workbook 污染）: `ExcelTemplate` 的 `xl/workbook.xml` 部件经 `loadInMemory()` 转为 `XmlOfficePackagePart`（持有共享 XNode），copy 后 `WorkbookPart.toWorkbookPart` 的 `loadXml()` 返回同一共享节点，`clearSheets()/addSheet()` 直接增删共享节点的 `<sheets>` 子节点。
- **风险**: 顺序复用时输出被污染（rels 无限增长、关系 id 空间持续消耗）；若应用按类注释约定缓存 WordTemplate/ExcelTemplate 并发渲染，多个线程并发修改同一 `OfficeRelsPart`（HashMap）与同一 XNode（ArrayList children），产生丢失更新、`ConcurrentModificationException`、输出文件交错损坏。仓库内现网调用（CliGenFileCommand、ReportEngine/XlsxReportRendererFactory）多为一次性使用，故评 P1 而非 P0；但“可安全缓存复用”是代码明示的契约，外部按契约使用即触雷。
- **建议**: `copyTo` 对模型类部件做深拷贝（`OfficeRelsPart` 至少补齐 clone，见 P3 的 cloneInstance 缺陷）；或 `getRels()/getContentTypes()` 在返回前总是解析为新鲜实例并禁止跨包共享；`WordTemplate.generateToDir` 内对 rels/contentTypes/workbook 的获取改为从 copy 重新解析。
- **误报排除**: 已核对 `[Content_Types].xml` 路径不受影响（loadInMemory 后为 XmlOfficePackagePart，copy 上 `getContentTypes()` 每次 `parseContentTypes` 生成新鲜 ContentTypesPart，且 parse 只读）；xlsx 的 `.rels` 部件（文件名以 `.rels` 结尾，不以 `.xml` 结尾）保持 ByteArrayResource，每次 copy 重新 parse，亦不受影响——问题精确限定在“模板 files 中已是模型对象实例”的部件（docx 的 OfficeRelsPart、xlsx 的 workbook.xml XNode）。

### [P1] 单元格引用列索引无上界校验，恶意 r 属性可放大内存至上亿元素

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SheetNodeHandler.java:481-484`（配合 nop-kernel `CellReferenceHelper.convertColStringToIndex` 无界、`CollectionHelper.set` 逐位补 null）
- **维度**: D5（安全/资源耗尽）
- **证据**:
```java
// SheetNodeHandler.outputCell()
CellPosition cellPos = CellPosition.fromABString(cellRef); // "ZZZZZZ1" → colIndex=321,272,406，无上限校验
output.cell(cellPos, thisStr, formulaStr, styleId);

// XlsxToRecordOutput.OutputRowHandler.cell()
CollectionHelper.set(row, cellRef.getColIndex(), value); // for(i=list.size(); i<=index; i++) list.add(null);

// SimpleSheetContentsHandler.cell()
table.setCell(cellRef.getRowIndex(), cellRef.getColIndex(), cell);
```
- **现状**: Excel 合法列上限为 XFD（16384），但 `convertColStringToIndex` 对任意长度字母串直接做 26 进制换算且不设上限（更长字母串还会 int 溢出为负/乱值）。随后 `CollectionHelper.set`（已在 nop-kernel 验证：循环 add(null) 直到 index）或表格 setCell 按该索引补齐集合。上传 xlsx 中一个 `<c r="ZZZZZZ1" t="inlineStr"><is><t>x</t></is></c>`（约 50 字节）即可让一行 ArrayList 膨胀到约 3.2 亿元素（GB 级内存）；每个此类单元格重复一次该开销。两条解析路径（ExcelWorkbookParser 全模型解析、XlsxToRecordOutput 流式读取，后者即 `ExcelHelper.readSheet/xlsxToCsv` 的上传导入入口）都受影响。
- **风险**: 小体积上传文件导致 OOM（与 P0 同为资源耗尽类，但需要构造 r 属性、且为逐行放大，故评 P1）。
- **建议**: 在 SheetNodeHandler 或 CellPosition 解析处校验列上限（POI 的 `SpreadsheetVersion.EXCEL2007.getLastColumnName()`）与行上限，越界时抛带位置的 NopException 或截断忽略。
- **误报排除**: 已确认 `CellPosition.fromABString` 仅委托 `parsePositionABString`，无任何范围检查；`CollectionHelper.set` 实现为逐元素补 null（nop-commons CollectionHelper.java:350-355）。

### [P1] WordTemplateParser/OfficeDocModelParser 直接使用 resource.toFile()，非文件型资源（如 jar 内模板）触发 NPE

- **文件**: `nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/parse/WordTemplateParser.java:58`；`nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/parse/OfficeDocModelParser.java:69`
- **维度**: D1
- **证据**:
```java
// WordTemplateParser.parseFromResource
pkg.loadFromFile(resource.toFile());   // toFile() 对非 file URL 返回 null（UrlResource/ClassPathResource 已核实）

// OfficePackage.loadFromFile(File file)
this.location = SourceLocation.fromPath(FileHelper.getFileUrl(file)); // file == null
...
if (file.isDirectory()) {             // NPE
```
- **现状**: nop-core 的 `UrlResource.toFile()/ClassPathResource.toFile()` 在非 `file:` URL（典型：docx 模板打包在 jar 内经 ClassPath/VFS 访问）时返回 null。`OfficePackage.loadFromFile(null)` 先后经过 `getFileUrl(null)`/`file.isDirectory()` 抛 NPE。同模块的 xlsx 侧 `AbstractXlsxParser.loadFromResource` 用的是 null 安全的 `OfficePackage.loadFromResource()`（其内部对 `toFile()==null` 走 `InMemoryResourceStore.addZipFile` 分支），形成对照，说明 docx 侧属遗漏而非约定。典型表现是“IDE 里 exploded classpath 正常，打成 fat jar 后模板解析 NPE 崩溃”。
- **风险**: 特定部署形态（jar 包内模板）下 docx 模板/文档模型解析直接崩溃，且以裸 NPE 形式暴露。
- **建议**: 两处改为 `pkg.loadFromResource(resource)`；或在 `OfficePackage.loadFromFile(File)` 入口对 null 参数抛带上下文的 NopException。
- **误报排除**: 已核实 `IResource.toFile` 的两类实现均在非 file URL 时显式 `return null`；`ExcelWorkbookParser` 走 `loadFromResource` 正常，排除“资源必有 file”的可能。

### [P2] OOXML 规范允许省略单元格 r 属性，当前解析直接 NPE 崩溃

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SheetNodeHandler.java:195,481`；`nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SimpleSheetContentsHandler.java:86-101`
- **维度**: D1 + D8（与被重构的 POI XSSFSheetXMLHandler 行为不一致）
- **证据**:
```java
// SheetNodeHandler.beginNode "c" 分支
cellRef = getAttr(attrs, "r");        // 规范中 r 可省略，此处可能为 null
...
// outputCell()
CellPosition cellPos = CellPosition.fromABString(cellRef);  // null → parsePositionABString 返回 null（isEmpty 短路）
output.cell(cellPos, thisStr, formulaStr, styleId);

// SimpleSheetContentsHandler.cell()
cell.setLocation(new SourceLocation(workbook.resourcePath(), 0, 0, 0, 0,
        sheet.getName(), cellRef.toABString(), null));      // NPE
```
- **现状**: ECMA-376 中 `c/@r` 与 `row/@r` 均为可选（省略时按出现顺序定位）；POI 原版通过列计数器补全缺失引用（代码注释也承认 "some sheets do not have rowNums, Excel can read them so we should try to handle them correctly as well"，行号侧做了兜底 `rowNum = nextRowNum`，但列引用侧完全没有对应处理）。`fromABString(null)` 静默返回 null 后，SimpleSheetContentsHandler 与 XlsxToRecordOutput.OutputRowHandler 两处立即解引用 NPE。部分第三方生成器/流式写出的 xlsx 不写 r 属性，属于“合法文件解析崩溃”。
- **风险**: 一类合规 xlsx 文件导入解析崩溃（NPE），功能可用性缺陷。
- **建议**: 在 SheetNodeHandler 中维护当前行列计数（与 nextRowNum 同法），r 缺失时按顺序推算 CellPosition。
- **误报排除**: 已核对 `parsePositionABString` 对空串/null 返回 null 而非异常，且两处消费方均无 null 判断。

### [P2] 不可信输入的多处裸运行时异常（NumberFormatException/SIOOBE/NPE），错误信息无文件上下文

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SheetNodeHandler.java:182,435,461-464`；`nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/model/ContentTypesPart.java:349-363`；`nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/model/CommentsPart.java:48-67`
- **维度**: D4 + D1
- **证据**:
```java
// SheetNodeHandler
rowNum = Integer.parseInt(rowNumStr) - 1;              // 182: 坏 row/@r → 裸 NumberFormatException
case BOOLEAN:
    char first = value.charAt(0);                      // 435: <c t="b"/> 无 <v> → StringIndexOutOfBounds
case SST_STRING:
    int idx = Integer.parseInt(sstIndex);              // 463: 坏 <v> → 裸 NumberFormatException

// ContentTypesPart.parseContentTypes
addDefaultContentType(extension, contentType);          // 355: <Default> 缺 Extension → extension.toLowerCase NPE
PackagePartName partName = PackagingURIHelper.createPartName(partNameStr); // 358: 缺 PartName → toURI(null) NPE

// CommentsPart.ParseHandler
cellPos = CellPosition.fromABString(getAttr(attrs, "ref")); // 50: <comment> 缺 ref → null key 入 Map
```
- **现状**: 上述位置均直接消费不可信文件的属性/文本，缺失或非法时抛 JDK 裸异常，无来源位置、无错误码。`CommentsPart` 的 null key 会延迟到 `ExcelWorkbookParser.parseSheet` 的 `cellPos.getRowIndex()` 才 NPE，更难定位。对照模块主流做法（NopException + ERR_XLSX_* + .param），这些点属于遗漏。BOOLEAN 空值分支为 POI 原版同款缺陷（refactor from POI 保留）。
- **风险**: 恶意/损坏的上传文件得到无上下文的裸异常，排障困难；不符合平台错误处理两档策略。
- **建议**: 统一改用 `ConvertHelper.toInt(..., NopException::new)` 并补充 sourceLocation/错误码；BOOLEAN 分支判空；CommentsPart 对缺 ref 的 comment 记录警告后跳过。
- **误报排除**: 已逐一核对 XNodeHandlerAdapter.getAttr 缺属性返回 null、`Integer.parseInt`/`charAt`/`toLowerCase` 的抛出路径，均为解析真实上传文件时可构造的输入。

### [P2] loadInMemory 之后调用 getStyles/getComments/getSlide 必然 ClassCastException（潜在触发面）

- **文件**: `nop-format/nop-ooxml/nop-ooxml-docx/src/main/java/io/nop/ooxml/docx/model/WordOfficePackage.java:60-67,74-82`；`nop-format/nop-ooxml/nop-ooxml-pptx/src/main/java/io/nop/ooxml/pptx/model/PptOfficePackage.java:123-136`
- **维度**: D1 + D8（接口契约与实现不匹配）
- **证据**:
```java
// WordOfficePackage.getStyles()
if (file instanceof WordStylesPart)
    return (WordStylesPart) file;
ResourceOfficePackagePart res = (ResourceOfficePackagePart) file;  // file 实际是 XmlOfficePackagePart 时 CCE

// ResourceOfficePackagePart.loadInMemory(): name.endsWith(".xml") → new XmlOfficePackagePart(path, node)
```
- **现状**: `WordTemplate`/`ExcelTemplate` 构造时调用 `pkg.loadInMemory()`，此后所有 `.xml` 部件（`word/styles.xml`、`word/comments.xml`、`ppt/slides/slideN.xml`）都变为 `XmlOfficePackagePart`。而这些 getter 仍按“Resource 或目标类型二选一”强转 `ResourceOfficePackagePart`。模块内现有调用（`DocxToMarkdownConverter`、pptx 转换器）发生在 loadInMemory 之前所以不炸；但 WordTemplate 把 pkg 暴露到生成期作用域（`VAR_OFC_PKG`），beforeGen/afterGen/自定义 XPL 若调用 `ofcPkg.getStyles()/getComments()` 即 CCE。`OfficePackage.getRels` 第 227 行同样的强转模式，对 rels 部件安全（`.rels` 文件不会被转换），但对误传入的 XmlOfficePackagePart 同样会炸。
- **风险**: 模板生成期扩展点上一个强转崩溃；API 对“已 loadInMemory 的包”这一自身引入的状态不兼容。
- **建议**: 强转前判 `instanceof ResourceOfficePackagePart`，否则直接 `file.loadXml()`（XmlOfficePackagePart 恰好实现了该方法），并归一化到目标 Part 类型。
- **误报排除**: 已核实 XmlOfficePackagePart 不是 ResourceOfficePackagePart 子类、`loadInMemory` 的 `.xml` 转换分支、以及 getStyles 的现有调用时序。

### [P2] PptxToMarkdownConverter 表格解析异常被完全吞掉且无任何日志

- **文件**: `nop-format/nop-ooxml/nop-ooxml-markdown/src/main/java/io/nop/ooxml/markdown/PptxToMarkdownConverter.java:202-227`
- **维度**: D4
- **证据**:
```java
try {
    ExcelTable table = new PptxTableParser()
            .forMarkdown(true)
            .imageUrlMapper(imageUrlMapper)
            .parseTable(tableNode);
    ...
} catch (Exception e) {
    // 表格解析失败时添加占位符
    ...
    contentBuilder.append("*[Table content could not be parsed]*");
}
```
- **现状**: `catch (Exception e)` 捕获后既不打日志也不计数，异常对象直接丢弃。转换不可信 pptx 时表格数据静默降级为占位符，生产上完全无法察觉解析缺陷（是文件问题还是代码问题无从判断）。对照同模块 `WordHyperlink.parseLinkSource` 等处均有 NopException 包装，此处属退化写法。模块 grep 未发现其他空 catch/吞异常点，这是唯一一处。
- **风险**: 排障黑洞；违反平台错误处理约定（至少应 LOG.warn 带异常）。
- **建议**: catch 内 `LOG.warn("nop.pptx.table-parse-fail", e)`（或 NopException 包装后由上层决定降级），保留占位符行为不变。
- **误报排除**: 已确认 catch 块无任何日志语句、无异常统计。

### [P2] zip 条目数与解压总大小无任何上限（防御纵深缺失）

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/OfficePackage.java:84-96,119-130`（配合 nop-kernel FileScanHelper/JdkZipOutput 实现核实）
- **维度**: D5
- **证据**:
```java
// OfficePackage.loadFromResource
InMemoryResourceStore store = new InMemoryResourceStore();
store.addZipFile("", resource);      // 全量解压入内存，无条目数/总大小上限

// OfficePackage.loadInMemory：模板路径把所有条目读入内存
byte[] bytes = ResourceHelper.readBytes(resource);
```
- **现状**: 已核实依赖侧事实: ① 常规解析路径（ZipResourceStore/FileScanHelper）条目惰性读取（ZipEntryResource 持 ZipFile 引用），加载阶段只建对象，这避免了元数据级炸弹——好的一面；② 但被实际读取的条目无单条大小、总条目数、解压总量上限（`JdkZipOutput`/`readBytes` 均直接流拷贝），`sharedStrings`（见 P0）、`styles`、图片（`imagePart.generateBytes` 全量 byte[]）、以及 `loadInMemory` 全包解压都可被高压缩比条目放大。平台对上传文件通常有外层体积限制，但 zip 高压缩比（如 1000:1）可绕过体积直觉。作为“解析不可信上传”的安全敏感面，缺少 POI ZipSecureFile 式的 min inflate ratio/entry 上限防线。
- **风险**: 高压缩比 xlsx/docx（zip 炸弹变体）导致渐进式内存耗尽；与 P0/P1 的单点放大叠加。
- **建议**: 在 OfficePackage/AbstractXlsxParser 层引入可配置的解压上限（条目数、单条字节数、累计字节数、压缩比阈值），超限抛 NopException；`loadInMemory` 仅用于可信模板资源并在 javadoc 标注。
- **误报排除**: 已核实三模块与 nop-kernel zip 读取链路确无上述任何上限参数/常量（grep MAX/LIMIT/ratio 无命中）；不将惰性加载误报为炸弹。

### [P3] OfficeRelsPart.cloneInstance 漏拷贝 relationshipsByType 与 nextId（当前无调用方的潜伏缺陷）

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/model/OfficeRelsPart.java:152-156`
- **维度**: D1
- **证据**:
```java
public OfficeRelsPart cloneInstance() {
    OfficeRelsPart ret = new OfficeRelsPart(path);
    ret.relationships.putAll(relationships);
    return ret;
}
```
- **现状**: 只复制 `relationships`，不复制 `relationshipsByType` 和 `nextId`。克隆体上 `getRelationshipByType/getRelationshipsByType/getImages` 恒为空，`newId()` 从 1 重来（靠 containsKey 兜底碰撞）。该方法当前在三模块内无调用方（grep 核实），属潜伏缺陷；若按 P1 建议把 clone 用作 copy 的深拷贝实现，缺陷会被立即激活。
- **风险**: 一旦被使用即数据丢失；与 P1 修复强相关。
- **建议**: 补齐三个字段的拷贝（relationshipsByType 需重建列表避免共享可变 List）。
- **误报排除**: 已 grep 确认模块内无调用方，如实标注为潜伏缺陷而非现行 bug。

### [P3] OfficePackage.saveToResource 的 IZipOutput 未在 finally 中关闭

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/OfficePackage.java:330-344`
- **维度**: D2
- **证据**:
```java
OutputStream os = resource.getOutputStream();
try {
    IZipOutput out = ResourceHelper.getZipTool().newZipOutput(os, options);
    generateToZip(out, scope);
    out.close();
} catch (IOException e) {
    throw NopException.adapt(e);
} finally {
    IoHelper.safeClose(os);
}
```
- **现状**: `generateToZip` 抛异常时 `out.close()` 被跳过，仅依赖 finally 关闭底层 `os`。fd 本身会释放，但 JdkZipOutput 内部 Deflater 等本地资源依赖 `out.close()` 释放，异常路径下要等 GC。保存失败时输出资源上会留下残缺 zip（无回滚/删除），调用方易误用半成品文件。
- **风险**: 轻微资源延迟释放 + 失败路径产物不干净。
- **建议**: `out` 声明提前，finally 中 `IoHelper.safeClose(out)`（其内部会关 os，可二选一）；可考虑失败时删除目标资源。
- **误报排除**: 已核实 `out.close()` 确在 try 内且异常时不执行；JdkZipOutput.close 负责收尾。

### [P3] PptOfficePackage 存在未完成 API：addSlide 不更新 presentation 引用、getSlidesXml 字典序错乱、removeCommentsFile 半成品

- **文件**: `nop-format/nop-ooxml/nop-ooxml-pptx/src/main/java/io/nop/ooxml/pptx/model/PptOfficePackage.java:106-113,182-189,194-222`
- **维度**: D8 + D1
- **证据**:
```java
private void updatePresentationSlideRefs() {
    // 更新presentation.xml中的幻灯片列表
    // 这里需要根据实际的PowerPoint文档结构来实现
}

public List<XNode> getSlidesXml() {
    this.getFiles("ppt/slides/slide").forEach(part -> { slides.add(part.buildXml(null)); });
    // files 为 TreeMap：slide1, slide10, slide2 ... 字典序而非编号序
}
```
- **现状**: ① `addSlide()` 只添加 `ppt/slides/slideN.xml` 文件，不写 `_rels/presentation.xml.rels` 关系、不写 `[Content_Types]` Override、`updatePresentationSlideRefs()` 为空实现——产出的 slide 在 pptx 中不可达，文件可能被判损坏；② `getSlidesXml()` 按 TreeMap 字典序返回（slide10 排在 slide2 前，>9 张幻灯片顺序错乱）；③ `removeCommentsFile()` 删除文件但内容类型移除逻辑被注释掉。模块内 `getSlidesXml`/`addSlide` 暂无调用方（markdown 转换走 index 寻址的 `forEachSlide`，不受②影响）。
- **风险**: API 语义误导（可调用但产物错误）；后续接入即触雷。
- **建议**: addSlide 补齐关系与内容类型注册或显式 `throw new UnsupportedOperationException`；getSlidesXml 按编号排序（可复用 PackagePartName.compare 的自然排序）。
- **误报排除**: 已核实 `files` 为 `TreeMap<String, IOfficePackagePart>` 且 addSlide 无任何 rels/contentTypes 写入。

### [P3] SimpleSheetContentsHandler.mergeCell 修改共享样式对象，边框串扰到无关单元格

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SimpleSheetContentsHandler.java:104-118`
- **维度**: D1
- **证据**:
```java
ExcelStyle style = getStyle(workbook, ec);
ExcelStyle style2 = getStyle(workbook, ec2);
if (style != null && style2 != null) {
    style.setRightBorder(style2.getRightBorder());
    style.setBottomBorder(style2.getRightBorder());
}
table.mergeCell(range);
```
- **现状**: `workbook.getStyle(styleId)` 返回样式表中共享的 ExcelStyle 实例，mergeCell 直接把右/下边框写进去。所有使用同一 styleId 的其他单元格（包括其他合并区域）边框被连带修改；多个共享样式的合并区域会级联污染（后一个 merge 读到的 style2 已是前者写过的）。且第 114 行 `setBottomBorder(style2.getRightBorder())` 疑为复制粘贴笔误（应为 getBottomBorder）。影响限于边框还原精度（外观级数据错误）。
- **风险**: 上传 xlsx 回读的边框样式在特定样式复用格局下失真。
- **建议**: 对样式做 clone 后再改（模块内 StylesPartParser.parseCellXfs 已有 cloneInstance 先例）；修正 getBottomBorder 笔误。
- **误报排除**: 已核实 `wk.getStyle(styleId)` 从共享 style 列表取同一实例、ExcelStyle 为可变对象、无 clone。

### [P3] 解析超链接时忽略 r:id，外部链接（URL 存于 rels）全部丢失

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/parse/SimpleSheetContentsHandler.java:121-130`（`SheetNodeHandler.java:279-284` 已解析并传入 rId）
- **维度**: D1（数据丢失）
- **证据**:
```java
// SheetNodeHandler 已提取 rId
String rId = getAttr(attrs, "r:id");
if (ref != null)
    output.link(ref, location, rId);

// SimpleSheetContentsHandler.link —— rId 参数被丢弃
public void link(String ref, String location, String rId) {
    if (ref.indexOf(':') > 0) return;
    ...
    if (location != null) {
        cell.setLinkUrl(ExcelConstants.REF_LINK_PREFIX + location);
    }
}
```
- **现状**: xlsx 外部超链接的 URL 存在 sheet 的 `.rels` 中，`w:hyperlink` 只有 `r:id` 引用。当前实现只用 `location`（内部锚点），带 r:id 的外链被静默丢弃（SheetNodeHandler 无 rels 访问能力，是结构性缺口）。上传文件中的超链接信息在模型中不完整。
- **风险**: 回读模型丢失外链（功能缺口，非崩溃）。
- **建议**: link 回调扩展 rels 上下文（SheetNodeHandler 持有 pkg/sheetPart 可解析 `getRelPart(sheetPart, rId)`），或至少在丢弃时 LOG.debug。
- **误报排除**: 已核实两文件的完整签名与调用链，rId 确实未透传。

### [P3] OfficePackage.generateToDir 直接拼接部件路径，缺少 generateToZip 已有的条目名校验

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/OfficePackage.java:320-324`
- **维度**: D5（路径遍历，条件触发）
- **证据**:
```java
public void generateToDir(File dir, IEvalScope scope) {
    for (IOfficePackagePart file : files.values()) {
        file.generateToFile(new File(dir, file.getPath()), scope);  // 无 isValidFilePath 校验
    }
}
```
- **现状**: zip 写出路径受 `JdkZipOutput.newZipEntry` 的 `StringHelper.isValidFilePath` 校验（含 `../` 的名字会被拒，已核实）；目录写出路径没有任何等价校验。zip 条目名经 `appendPath` 简单拼接进入资源树（不做 `..` 归一），若把加载了不可信包的 OfficePackage 以 `generateToDir` 落盘，含 `../` 的部件路径可写出 dir 之外。模块内现网链路（模板生成）部件名均为可信常量，故为条件触发。
- **风险**: “加载不可信包 → 目录物化”这一扩展用法下存在目录穿越写。
- **建议**: `generateToDir` 入口对 `file.getPath()` 做 `StringHelper.isValidFilePath` + 拒绝 `..` 校验，与 zip 路径对齐。
- **误报排除**: 已核实 zip 侧确有校验而 dir 侧没有；并确认 appendPath 不消除 `..`。

### [P3] ExcelOfficePackage.loadEmpty 每次调用都从 VFS 重新解压并解析空模板

- **文件**: `nop-format/nop-ooxml/nop-ooxml-xlsx/src/main/java/io/nop/ooxml/xlsx/model/ExcelOfficePackage.java:33-38`
- **维度**: D6
- **证据**:
```java
public static ExcelOfficePackage loadEmpty() {
    IResource resource = VirtualFileSystem.instance().getResource(EMPTY_TEMPLATE_PATH);
    ExcelOfficePackage pkg = new ExcelOfficePackage();
    pkg.loadFromResource(resource);   // 每次 unzip + collectFiles
    return pkg;
}
```
- **现状**: `new ExcelTemplate(workbook)`（ExcelHelper.saveExcel 等高频入口）每次都触发空模板 zip 的解压、条目收集与后续逐部件惰性解析。模板内容固定，无缓存。单次开销小（模板为 KB 级），但属于已知的重复解析样式表/模板类浪费。
- **风险**: 高频导出场景的无谓 CPU/分配开销。
- **建议**: 以模板字节数组为底本的进程级缓存（注意与 P1 的共享可变状态问题联动：缓存必须配合真正的深拷贝）。
- **误报排除**: 已核实无任何静态缓存字段；调用频度来自 saveExcel/ReportEngine 每次新建。

### [P3] InvalidOperationException 为继承 RuntimeException 的死代码

- **文件**: `nop-format/nop-ooxml/nop-ooxml-common/src/main/java/io/nop/ooxml/common/exceptions/InvalidOperationException.java`
- **维度**: D7/D4
- **证据**:
```java
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
```
- **现状**: 全仓库无抛出点（仅 ContentTypesPart 中被注释的 POI 移植代码引用），是 POI 移植遗留。类型上违背“禁止 bare RuntimeException”的平台约定，虽然当前不可达。
- **风险**: 未来被随手复用会引入非 NopException 异常路径。
- **建议**: 删除，或改为继承 NopException 并挂错误码。
- **误报排除**: 已 grep 全仓库确认无 throw new InvalidOperationException。

## 补充说明（非发现）

- D3 正面确认: chart 包的 `INSTANCE` 单例（DrawingChartParser/ChartShapeStyleParser/各 Builder）经核查无可变实例字段，均为无状态方法类，并发共享安全；`DefaultChartStyleProvider` 每次 `createStyleProvider` 新建实例，无共享。
- D2 正面确认: `AbstractOfficeTemplate.generateToStream` 临时目录在 finally 中 `ResourceHelper.deleteAll` 清理；`AbstractXlsxParser/WordTemplateParser/OfficeDocModelParser/DocxToMarkdownConverter/PptxToMarkdownConverter/XlsxToRecordOutput` 均在 finally/safeClose 中关闭 OfficePackage；`AbstractXmlTemplate.generateToStream` 只 flush 不关闭调用方流，职责正确。
- `IOfficePackagePart.generateToZip` 默认实现不关闭条目流，经核实 JdkZipOutput 的 `putNextEntry` 会自动收尾前一Entry、`out.close()` 统一收尾，判为安全的非问题。
