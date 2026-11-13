# C1 共享可变状态与可见性 — nop-report + nop-format 初审

> 审计单元：CRA WI 5.4 nop-report-format（19 源码根，894f/102,861 行）
> 维度：C1 共享可变状态与可见性。方法：`ai-dev/skills/concurrency-resource-audit-prompt.md`。
> 范围限定：盲审（未读 audits/plans/bugs/lessons/backlog 历史）；以 live code 为准；`_gen/` 不作为发现对象。
> CL 前缀：`nop-report-format-CL-0NN`。

## 共享可变状态清单表（五类载体盘点结论）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|---------|------|
| 1 | static Map formats | ExcelFormatHelper.java:74 | static init + 公开 addFormat | getFormat（每单元格热路径） | 无 | **风险（C1-003）** |
| 2 | singleton 实例字段 systemFonts/fontNameAliases/defaultFont/inited | FontManager.java:47-68 | init()/addFontAlias/getDefaultFont | getFont/getSystemFont（渲染热路径） | init 为 synchronized；addFontAlias/getDefaultFont 写路径无锁 | **风险（C1-002）** |
| 3 | singleton discoveredFontFiles/fallbackFontFile | FontManager.java:53,58 | resolveFallbackFont/discoveredFontFiles() | loadFont/loadFallbackFont | volatile + DCL(this) | 安全 |
| 4 | singleton unencodableChars | FontManager.java:64 | markUnencodable | canEncode | Collections.synchronizedMap + synchronized(map) 复合操作 | 安全 |
| 5 | volatile processors | ResourceDocumentTool.java:31 | setPostProcessors（启动期） | extractTextFromResource | volatile 快照读 + 防御性拷贝 | 安全 |
| 6 | static volatile _instance | DocumentConverterManager.java:30 | registerInstance | instance() | volatile | 安全 |
| 7 | ConcurrentHashMap 外层 + HashMap/TreeSet 内层 | DocumentConverterManager.java:154-161 | registerConverter | getDirectConverter/findChainedConverter | 仅外层并发安全 | **风险（C1-001）** |
| 8 | volatile parser/renderer + DCL | MarkdownNormalizer.java:25-55 | buildParser/buildRenderer | normalizeText | volatile + synchronized(this) DCL | 安全 |
| 9 | static HashMap SHAPE_TYPES | JFreeChartStyleAdapter.java:31 | static init 只写 | getPredefinedShape | 类初始化 HB | 值对象可变 → **风险（C1-004）** |
| 10 | static DEFAULT | FieldCodecRegistry.java:25 | 无运行时赋值 | 全模块 | 类初始化；引用非 final | 约定风险（C1-006） |
| 11 | 实例 HashMap cellExprCache/formulaCompileCache | XptRuntime.java:73-74 | 本线程渲染过程 | 本线程 | 每次渲染新建 XptRuntime，线程封闭 | 安全 |
| 12 | 实例 HashMap fontCache/imageCache/pages | PdfRenderer.java:39-42 | 渲染线程 | 渲染线程 | PdfReportRenderer 每次 new PdfRenderer(new PDDocument())，线程封闭 | 安全 |
| 13 | ThreadLocal lastFormatIndex/String/Result | ExcelDateHelper.java:371-377 | isADateFormat 同线程 | 同线程 | ThreadLocal；clear() 提供清理入口 | 安全（残留为单条目，不报） |
| 14 | ThreadLocal lastFailedCodePoint（实例字段） | FontManager.java:327 | canEncode | getLastFailedCodePoint | 每次 canEncode 先 remove() | 安全 |
| 15 | 静态 INSTANCE 单例 | ReportFunctionProvider/ReportExprStdDomainHandler/ExpandedSheetEvaluator | 启动期注册 | 渲染期 | 无状态或只读 | 安全 |
| 16 | RandomAccessFile 共享位置态 | RandomAccessFileBinaryDataReader.java:146-181 | detach/duplicate 返回的子视图 | 父 reader | 无（同一 raf 文件指针） | **风险（C1-005）** |
| 17 | ANTLR 静态 ATN/DFA | nop-mermaid parse/antlr/* | ANTLR 运行时 | 各解析线程 | ANTLR 4 内部设计（并发 DFA 缓存） | 基础设施，按口径不重审 |

R1 预注锚点核对：ExcelDateHelper 16 处细化命中经逐行核对全部为 `static final Pattern`（不可变）与 ThreadLocal 单项缓存，**无 C1 实体发现（误报排除）**；MarkdownNormalizer 2 处为 volatile DCL 正确用法；JFreeChartStyleAdapter 44 处粗口径命中主体为 static final Pattern/静态无状态方法。

---

### [C1-001] DocumentConverterManager 内层 HashMap/TreeSet 在运行时注册下非线程安全

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/DocumentConverterManager.java:154-161,208-218`
- **证据片段**:
  ```java
  private final Map<String, Map<String, IDocumentConverter>> converters = new ConcurrentHashMap<>();
  ...
  public void registerConverter(String fromFileType, String toFileType, IDocumentConverter converter) {
      Map<String, IDocumentConverter> map = converters.computeIfAbsent(fromFileType, k -> new HashMap<>());
      IDocumentConverter oldConverter = map.put(toFileType, converter);
      ...
      fromFileTypeMap.computeIfAbsent(toFileType, k -> new TreeSet<>()).add(fromFileType);
  }
  ```
- **严重程度**: P2
- **并发场景**: 启动期 ConverterRegistrationBean 批量注册（低风险窗口）与运行期任意线程的 `getConverter/requireConverter/findChainedConverter` 查找；若运行期动态调用 `registerConverter`（public API）则风险窗口常态化。
- **触发时序**: T1 线程 A 调用 registerConverter，在 inner HashMap 执行 `map.put`（触发 resize/树化）→ T2 线程 B 同时执行 `getDirectConverter` 读取同一 inner HashMap 的 `map.get(toFileType)` → B 可能读到 resize 中间态（丢失条目返回 null → 误报 ERR_NO_CONVERTER_FROM_TYPE_TO_TYPE）或 JDK8 HashMap 并发结构损坏。同理 `findChainedConverter` 对 `fromFileTypeMap` 的 inner TreeSet 迭代（`for (String intermediateType : intermediateTypes)`）与 T2 的 `.add(fromFileType)` 并发 → ConcurrentModificationException 或丢失中间类型。
- **现状**: 外层 ConcurrentHashMap 给出并发安全的表象，内层可变集合裸奔；注册点唯一（ConverterRegistrationBean，启动期），故当前仅运行期再注册才触发。
- **风险**: 运行期插件式注册转换器（平台动态模型加载场景存在此模式）时查找线程可见损坏结构或 CME。
- **建议**: 内层统一换 `ConcurrentHashMap.newKeySet()`/`ConcurrentHashMap`（fromFileTypeMap 可用 ConcurrentSkipListSet 保序），或注册改为不可变 Map 整体替换（volatile 发布）。
- **失效面枚举**: `converters`（HashMap inner，getDirectConverter/getFromFileTypes 读）；`fromFileTypeMap`（TreeSet inner，findChainedConverter 迭代）；`documentObjectBuilders`（ConcurrentHashMap，安全）。F3 清扫：同类"外层并发内层裸"模式在本单元仅此一处（grep `computeIfAbsent.*new HashMap` 全模块核对：ConverterRegistrationBean.java:140,176 为本地构建集合非共享，排除）。
- **信心水平**: 确定（结构问题确定；触发依赖运行期注册时序）
- **误报排除**: 不属"原语使用不是问题"——复合操作（内层 put/get/iterate 无同步）正是校准条款指出的误用形态；也不是低频路径豁免——convert 是文档转换入口热路径。
- **复核状态**: 已复核（保留）

---

### [C1-002] FontManager JVM 单例：addFontAlias 无锁写 HashMap、getDefaultFont 无锁惰性重建

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/font/FontManager.java:66-68,123-134,141-143`
- **证据片段**:
  ```java
  private PDFont defaultFont;            // 非volatile
  ...
  public PDFont getDefaultFont() {
      init();                             // synchronized，获得 HB 边
      if (defaultFont == null) {
          try {
              defaultFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA); // 锁外写
  ...
  public void addFontAlias(String alias, String fontName) {
      fontNameAliases.put(alias, fontName);   // 纯 HashMap，全程无同步
  }
  ```
- **严重程度**: P2
- **并发场景**: JVM 级单例 `_instance`；多 HTTP worker 线程并发渲染 PDF 报表，同时共享 `getFont()/getDefaultFont()/canEncode()` 读路径；别名注册方（若有插件运行期调 addFontAlias）为写者。
- **触发时序**: （a）alias 竞态：T1 调 `addFontAlias("宋体","SimSun")` 执行 HashMap.put → T2 线程 `getFont("宋体",...)` 同时执行 `fontNameAliases.get(fontName)`（FontManager.java:153）→ HashMap 并发 put/get，T2 可能读到损坏结构或丢失别名，落入回退字体渲染（字形错误/换字体）。（b）defaultFont 竞态：init 时 getFontResource 抛异常被吞（L104 catch 仅记日志），`defaultFont` 保持 null；T1、T2 并发进入 getDefaultFont 的 null 检查 → 各自 new PDType1Font 双写 `defaultFont`（非 volatile）→ 双重创建（浪费）+ 可见性竞态（T3 可能长期看到 null，反复重建）。
- **现状**: init() 路径有 synchronized 保障首次发布；上述两条为 init 之后的锁外写路径，无任何同步。
- **风险**: 别名注册丢失导致整文档回退字体（中文渲染为 Helvetica 缺字形错误）；defaultFont 反复重建的性能与可见性问题。
- **建议**: `fontNameAliases` 换 ConcurrentHashMap；`defaultFont` 写路径收进 synchronized(this)（或改 volatile，配合 init 已有锁）。
- **失效面枚举**: 同族字段逐一核对：`systemFonts`（仅 init 锁内写，安全）；`discoveredFontFiles`/`fallbackFontFile`（volatile+DCL，安全）；`unencodableChars`（synchronizedMap+复合锁，安全）；`inited`（锁内读写，安全）。兄弟方法：`resetForTesting`（synchronized，测试专用，安全）。
- **信心水平**: 确定（数据竞争存在；alias 写路径在当前代码库无运行期调用方，后果为条件性）
- **误报排除**: 非"低频路径粗粒度 synchronized 豁免"——此处是**该同步的地方完全没有同步**；也不是无状态单例——明确存在可变集合与可变引用。
- **复核状态**: 已复核（保留）

---

### [C1-003] ExcelFormatHelper 静态 HashMap formats 由公开 addFormat 无同步写入

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/format/ExcelFormatHelper.java:71-107,271-273`
- **证据片段**:
  ```java
  // 这里只缓存少量规定格式的format，不需要每个format都缓存。另外也不需要线程安全。
  // 假定除了系统初始化阶段不会动态设置format
  private static final Map<String, Format> formats = new HashMap<>();

  public static Format getFormat(String formatStr) { ... return formats.get(formatStr); ... }
  public static void addFormat(String excelFormatStr, Format format) {
      formats.put(excelFormatStr, format);
  }
  ```
- **严重程度**: P2
- **并发场景**: 全 JVM 共享静态表；`getFormat` 被每单元格调用（ExpandedSheetEvaluator.java:176、ExcelWorkbook.java:59 的渲染热路径）；`addFormat` 是 public API，注释以"假定"排除运行期写，但无任何运行时守卫（fail-fast）。
- **触发时序**: T1 任意代码运行期调用 `ExcelFormatHelper.addFormat("0.0%", fmt)` 执行 HashMap.put → T2 渲染线程在 evaluateCell 中执行 `formats.get(formatStr)` → JDK8 HashMap put/get 并发：get 可能读到中间态结构（树化/resize 期间）返回错误结果或抛出；put 间并发丢失更新。当前代码库 addFormat 仅 static-init 调用（类初始化 HB 保证安全），风险由"公开可写 API + 无守卫"维持。
- **现状**: 安全完全依赖约定（注释自认"假定"），无断言/异常守卫；属 F10"并发守卫缺失——依赖调用方自觉"形态。
- **风险**: 运行期扩展格式被并发渲染线程观察到损坏表；此类损坏表现为随机单元格格式化错误，难排查。
- **建议**: formats 换 ConcurrentHashMap（读路径零成本），addFormat javadoc 声明线程安全性；或在 addFormat 加 synchronized（写少读多，读写不互斥问题仍在，首选并发容器）。
- **失效面枚举**: grep 全模块 `static final Map` 可变共享表：ExcelFormatHelper.formats（本条）；JFreeChartStyleAdapter.SHAPE_TYPES（static init 只写，另行 C1-004）；FieldCodecRegistry 各 map（ConcurrentHashMap，安全）；FixedLengthStringCodecFactory.fieldCodecs（ConcurrentHashMap + computeIfAbsent，键空间=枚举×pad×charset 有界，安全）。SSNFormat/ZipPlusFourFormat/PhoneFormat 的 `static final Format df` 为 FastIntegerFormatter 不可变实现（nop-commons 源码核实：无状态+final 字段），安全。
- **信心水平**: 确定（结构问题确定）；后果条件性（需要运行期写者）
- **误报排除**: 非有界声明缓存豁免——问题是无同步而非无界；非原语使用——put/get 复合无同步是缺陷本体。
- **复核状态**: 已复核（保留）

---

### [C1-004] JFreeChartStyleAdapter 预定义 Shape 可变实例跨图表/跨线程共享

- **文件**: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/utils/JFreeChartStyleAdapter.java:31-63,119-122`；消费点 `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/style/ChartShapeStyleProcessor.java:289-292`
- **证据片段**:
  ```java
  private static final HashMap<String, Shape> SHAPE_TYPES = new HashMap<>();
  static {
      SHAPE_TYPES.put("circle", new Ellipse2D.Double(-4, -4, 8, 8));   // 可变几何对象
      SHAPE_TYPES.put("square", new Rectangle2D.Double(-4, -4, 8, 8));
      ...
  }
  public static Shape getPredefinedShape(String shapeName) {
      return SHAPE_TYPES.get(shapeName);        // 返回共享可变实例
  }
  ```
- **严重程度**: P3
- **并发场景**: 所有图表导出线程共享同一批 Shape 实例；ChartShapeStyleProcessor 将其实例注入 XYLineAndShapeRenderer.setSeriesShape，此后所有该 renderer 的绘制/图例布局读取同一实例。
- **触发时序**: 当前消费点未改写 Shape（JFreeChart 绘制路径对 shape 只读），故无具体交错序列可写——**理论风险，未具体化**，按口径降级 P3。风险链条：任何未来调用方对返回 Shape 执行 `setFrame/translate`（awt Shape 均可变）→ 全 JVM 所有图表标记变形，且随渲染并发表现为间歇性。
- **现状**: map 本身类初始化只写（发布安全），但**值对象可变且逃逸**；无 javadoc 声明"返回共享实例不得修改"。
- **风险**: 共享可变几何对象的隐性别名；调用方扩展（自定义 renderer/图例命中检测平移 shape）即触发跨图表污染。
- **建议**: getPredefinedShape 返回 `shape.clone()`（awt Shape 支持 clone），或将 SHAPE_TYPES 值改为不可变包装/每次 new。
- **失效面枚举**: SHAPE_TYPES 全部 13 个条目（circle/square/diamond/triangle/x/star/plus/dash/dot/cross/auto/none）同为共享可变实例；消费点唯一（ChartShapeStyleProcessor.applySeriesShapeStyle）。
- **信心水平**: 有趣的猜测（当前无实际变异点）
- **误报排除**: 静态 map 发布本身安全，不报；报的是可变值对象逃逸共享——属 C1 第 5 类"可变对象逃逸到其他线程的隐式共享"。
- **复核状态**: 已复核（保留）

---

### [C1-005] RandomAccessFileBinaryDataReader.detach()/duplicate() 返回共享同一 RAF 指针的伪独立视图

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/RandomAccessFileBinaryDataReader.java:173-181`
- **证据片段**:
  ```java
  @Override
  public IBinaryDataReader detach() throws IOException {
      return subInput(size());          // = new SubBinaryDataReader(this, size())
  }
  @Override
  public IBinaryDataReader duplicate() throws IOException {
      return subInput(size());          // 同上：同一 raf，同一文件指针
  }
  ```
- **严重程度**: P2
- **并发场景**: `IBinaryDataReader.detach()` 的接口契约（对照同族实现：StreamBinaryDataReader.detach 物化剩余数据、BlockCachedBinaryDataReader.detach 复制缓存块、ByteBufferBinaryDataReader.detach 包装独立缓冲）是返回**可独立推进位置**的读取器；RAF 实现返回的 SubBinaryDataReader 与父 reader 共享同一个 RandomAccessFile 及其文件指针与 `pos` 字段。
- **触发时序**: T1（逻辑上：解析流程先持 detached 视图后继续读父 reader）在 detached 上 `seek(100)+read()` → 底层同一 raf 指针移动到 100+len → T1 继续用父 reader `read()` → 从 detached 停止处而非父 reader 原位置继续 → **静默错位解析**（记录字段串位，无异常）。当前 `StreamingRecordDeserializer`/`AbstractModelBasedRecordDeserializer` 对 subIn 的使用严格"顺序使用+按 baseIn.pos() 公式回补"，共享位置反而使公式成立，故现网未触发；任何"父与子交错读"或双线程各持一份即触发。**按口径标注：交错时序可具体构造，但当前调用面为顺序使用，降为 P2。**
- **现状**: `isDetached()` 恒返回 false 也佐证该实现未真正 detach；接口 javadoc 无契约声明，五族 reader 的 detach 语义各自为政。
- **风险**: 未来任何按接口契约（独立快照）使用 detach 的新调用方在 RAF 路径上静默数据损坏；双线程各持 detached 视图并发读 = 共享 RandomAccessFile 竞态（seek/read 交错）。
- **建议**: RAF detach 按文件重新 `new RandomAccessFileBinaryDataReader(file)` 或一次读入 ByteBuffer 返回独立视图；同时在 IBinaryDataReader.detach()/duplicate() 写明独立性契约。
- **失效面枚举**: detach 实现族全清单：IBinaryDataReader 无默认实现；StreamBinaryDataReader（物化剩余，独立，安全）；ByteBufferBinaryDataReader（独立缓冲，安全）；ByteBufBinaryDataReader（netty，需独立 slice——见 round2 核对）；BlockCachedBinaryDataReader（复制块+underlying.detach，安全）；SubBinaryDataReader.detach（委托 underlying.detach，跟随底层语义）；RandomAccessFileBinaryDataReader（**违规**）；SubBinaryDataReader.duplicate（委托）；BlockCached.duplicate=detach（安全）。ReaderTextDataReader.detach（OffsetTextDataReader 包 detachedDelegate，跟随底层）。
- **信心水平**: 确定（契约违背确定；数据损坏触发条件性）
- **误报排除**: 非平台基础设施（nop-record 自有代码）；非原语误用——是接口语义/实现不匹配的结构缺陷。
- **复核状态**: 已复核（保留）

---

### [C1-006] FieldCodecRegistry.DEFAULT public static 非 final 单例引用（约定风险，复核补写立案）

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/codec/FieldCodecRegistry.java:26`
- **证据片段**:
  ```java
  @GlobalInstance
  public class FieldCodecRegistry {
      public static FieldCodecRegistry DEFAULT = new FieldCodecRegistry();
  ```
- **严重程度**: P3
- **并发场景**: JVM 级单例引用无 `final` 修饰；类初始化（static 块 L28-33）提供初始安全发布，此后全模块经 `FieldCodecRegistry.DEFAULT` 直读。清单表行 10 引用本条但初审判决未立案，本轮复核补写以消除表-实体不一致。
- **触发时序**: 当前全代码库无任何 DEFAULT 再赋值点（grep 仅声明处），无现网数据竞争。若未来代码对 DEFAULT 再赋值（无 volatile），已加载类的读线程可能长期看到旧引用或未完全发布的新实例。
- **现状**: 内部三个注册表均为 ConcurrentHashMap（L35-39），运行期 register/unregister 线程安全；round2 清扫表「安全」结论针对内部 map，与本条（引用可变性）不矛盾。
- **风险**: 契约性风险——public 可变单例引用可被任意代码替换，无不可变约定；与 C1-001 同属「全局注册表缺发布纪律」家族。
- **建议**: 声明加 `final`（一行修复）；如确需可替换实例，改 `volatile` 并收敛替换入口。
- **失效面枚举**: DEFAULT 唯一实例；写点仅 static 块内 `FieldBinaryCodecRegistrar.registerWordType(DEFAULT)` 与 3 个 register 调用（L29-32，类初始化期）。
- **信心水平**: 确定（代码事实）；后果为推测（无现网写者）
- **误报排除**: 非「原语使用不是问题」——缺陷本体是引用可变性而非容器线程安全；因无现网写者按 P3 约定风险立案。
- **复核状态**: 已复核（本轮复核补写，裁定保留 P3）

---

## 维度复核结论

> 复核人：CRA WI 5.4 C1 维度独立复核子代理。方法：逐条重读 6 条发现引用的源码文件与行号核实证据与语义；已知疑点（清单表行 10 的 C1-006 表-实体不一致）单独裁定。复核日期：2026-10-22。本节为追加内容，不修改既有初审/round2 结论。

| 编号 | 判定 | 理由摘要（file:line 复核证据） | 现状标注 |
|------|------|------------------------------|---------|
| C1-001 | 保留 P2 | 证据属实：外层 ConcurrentHashMap（DocumentConverterManager.java:32）+ 内层裸 HashMap/TreeSet（:154-161）；getDirectConverter 无同步读内层（:198-200、:205）；findChainedConverter 迭代内层 TreeSet（:263-269）；registerConverter 为 public API。F3 排除项亦核实：ConverterRegistrationBean.java:45,140,176 为本地构建集合非共享；生产注册点唯一且启动期（:58-61,151） | 竞争窗口仅在启动期注册与运行期查找交叠、或运行期再注册（public API 保留该能力）时打开；现网顺序注册未触发 |
| C1-002 | 保留 P2 | 证据属实：fontNameAliases 纯 HashMap（FontManager.java:48），addFontAlias 全程无同步（:141-143），读侧 :153；defaultFont 非 volatile（:66），init 吞异常致 defaultFont 保持 null（:104-107），getDefaultFont 锁外 null 检查+写（:123-134）。同族安全结论逐项核对无误（systemFonts 仅 init 锁内写；discoveredFontFiles/fallbackFontFile volatile+DCL :53,58,450-462；unencodableChars synchronizedMap+复合锁 :64,360；inited 锁内读写） | addFontAlias 生产代码零调用方（仅 TestFontManager.java:34）；defaultFont 兜底重建仅在 init 的 Helvetica 创建失败（罕见）后可达——数据竞争确定存在，后果条件性 |
| C1-003 | 保留 P2 | 证据属实：裸 static HashMap（ExcelFormatHelper.java:74）+ L72 注释原文一致；addFormat public 无守卫（:271-273）；渲染热路径消费确认（ExpandedSheetEvaluator.java:176、ExcelWorkbook.java:59）；当前 addFormat 仅 static 块调用（:76-92） | 写路径当前仅类初始化触达（类初始化 HB 安全）；风险由 public API 无运行时守卫维持 |
| C1-004 | 保留 P3 | 证据属实：SHAPE_TYPES static 只写（JFreeChartStyleAdapter.java:31-57），值对象可变（Ellipse2D/Rectangle2D/Polygon），getPredefinedShape 逃逸共享实例（:119-121），唯一消费点 setSeriesShape（ChartShapeStyleProcessor.java:289-292）。事实性更正：条目数为 12（11 个非 null 可变 + none=null），非发现所述 13——不影响判定 | 当前无任何变异点（JFreeChart 绘制路径对 shape 只读），理论风险；维持 P3 |
| C1-005 | 保留 P2 | 证据属实：detach/duplicate 均返回 subInput(size())=SubBinaryDataReader(this)（RandomAccessFileBinaryDataReader.java:172-179），Sub 的 seek/read 委托 underlying 共享同一 RAF 指针与 pos（SubBinaryDataReader.java:45-89）；isDetached 恒 false（:182-184）。现网未触发核实：反序列化仅经 subInput 顺序消费+pos 公式回补（AbstractModelBasedRecordDeserializer.java:73-105,262-290；StreamingRecordDeserializer.java:97,363），detach 的生产调用方全部在 reader 族内部。F3 更正一处：ByteBufferBinaryDataReader.detach 也是 bb.duplicate() 基（:473-475，索引独立-内容共享），并非「独立缓冲-复制」；不改变本条判定 | 当前调用面顺序使用，共享位置反而使对齐公式成立；父/子交错读或双线程各持一份 detached 视图即静默错位 |
| C1-R2-001 | 保留 P3 | 证据属实：detach=duplicate=`bb.duplicate()`（ByteBufBinaryDataReader.java:430-437），isDetached 无条件 true（:440-442）；生产代码 isDetached 零消费方（grep 全模块仅实现处与测试）。行号更正：证据实际位于 L430-442，原引 345-352 为 readBytesTerm 区域（440-443 正确）。复核补充：ByteBufferBinaryDataReader 同构（:473-483 duplicate 基 detach + 恒 true isDetached），契约漂移面比原矩阵更大，反向支持「接口契约缺失致实现各自漂移」结论 | 无现网触发点；未来调用方以 isDetached 判定「可并发持有」时，顶层 netty 帧 reader 也被误判 detached，而其内容共享 pipeline 帧缓冲，帧释放后读取即 use-after-release |
| C1-006（补写） | 补写并保留 P3 | 表-实体不一致裁定：清单表行 10 引用的 C1-006 对应事实真实存在——FieldCodecRegistry.java:26 `public static FieldCodecRegistry DEFAULT` 非 final（原引行号 25 为类声明行， declaration 在 :26）。复核确认：全代码库无任何再赋值点；内部三 map 均 ConcurrentHashMap（:35-39），运行期 register/unregister 线程安全——round2 清扫「安全」结论针对内部 map，与本条不矛盾、无冲突。已补写 C1-006 发现实体（P3），行 10 引用自此可解析 | 当前仅类初始化赋值（static 块 :28-33，HB 安全发布）；无运行时写者 |

维度集外线索核对：复核中发现的 ByteBufferBinaryDataReader.detach 与 ByteBuf 同构（duplicate 基 + isDetached 恒 true）属 C1 维度内 detach 契约族事实，已并入 C1-005/C1-R2-001 的族矩阵更正，不另立编号；无其他维度集外新线索需登记。

批次覆盖核验结论：批A excel（C1-003 实体+清单表行1/13+round2 s_filterTpl/ImportExcelParser 行）、批B ooxml（round2「ooxml-xlsx chart 解析族」行——仅 xlsx 有痕迹，ooxml-common/docx/markdown/pptx 无显式核查痕迹）、批C pdf+record+record-netty（C1-002+行12；C1-005/C1-R2-001+行10；注意 nop-record-netty 模块本体 io.nop.netty.ext.* 10 文件无核查痕迹，round2 所称 netty 均指 nop-record 内 io.nop.record.netty 包）、批D report+mermaid+svg+chart-export+converter+markdown-ext（行8/11/15/17+round2 多行）均有实际核查痕迹；**覆盖缺口：nop-tablesaw（14f）、nop-office-model（20f）、nop-office-doc-model（18f）、nop-record-netty（10f）四个源码根在两份 C1 文档中零提及，且批B 仅部分覆盖**——本轮不补审，仅登记。

## 最终保留项

| 编号 | 严重度 | 一句话结论 |
|------|--------|-----------|
| C1-001 | P2 | DocumentConverterManager 外层 ConcurrentHashMap 内层裸 HashMap/TreeSet，运行期再注册下查找线程可见损坏结构或 CME |
| C1-002 | P2 | FontManager JVM 单例 addFontAlias 无锁写 HashMap、getDefaultFont 锁外惰性重建非 volatile defaultFont |
| C1-003 | P2 | ExcelFormatHelper 静态 HashMap formats 由 public addFormat 无同步写入，渲染热路径并发读 |
| C1-004 | P3 | JFreeChartStyleAdapter 预定义可变 Shape 实例逃逸共享（12 条目含 11 可变），未来调用方变异即跨图表污染 |
| C1-005 | P2 | RandomAccessFileBinaryDataReader.detach/duplicate 返回共享同一 RAF 指针的伪独立视图，父/子交错读静默错位 |
| C1-R2-001 | P3 | ByteBufBinaryDataReader.isDetached 恒 true 且 detach=duplicate 语义同化，detach 契约族漂移（ByteBuffer 同构，矩阵已更正） |
| C1-006 | P3 | FieldCodecRegistry.DEFAULT public static 非 final（当前无再赋值点，内部 CHM 安全），约定风险补写立案 |

最终保留合计：7 条（P2×4，P3×3）；无驳回、无降级。
