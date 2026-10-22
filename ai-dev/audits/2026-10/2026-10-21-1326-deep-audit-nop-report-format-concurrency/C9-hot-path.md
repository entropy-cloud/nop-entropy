# C9 并发高性能（热路径） — nop-report + nop-format 初审

> 审计单元：CRA WI 5.4 nop-report-format。维度：C9 并发高性能（锁争用/分配放大/串行化瓶颈，默认 P2/P3，须给量化口径）。
> R1 锚点：C9/C2 锁 25 命中（FontManager 7 synchronized / XptRuntime 3）；缓存面 FieldCodecRegistry 4 / DocumentConverterManager 4。
> 热路径定位：报表渲染（每单元格/每字段）、Excel 格式化（每单元格）、PDF 渲染（每文本段/每字形检查）、record 解码（每字段/每字节）。

## 热路径清单

| 热路径 | 频率口径 | 锁/分配点 | 判定 |
|--------|---------|-----------|------|
| ExcelFormatHelper.getFormat | 每格式化单元格 | 新建 SimpleDateFormat + ~6 次 replaceAll 正则编译 | **C9-001 P2** |
| FontManager.getFont/init/canEncode | 每文本段/每字符 | JVM 单例 synchronized(this)（init 全程 + resolveFallbackFont 内 I/O） | **C9-002 P2** |
| XptRuntime.getCache synchronized | 每字典查询 | 实例 synchronized，实际单线程调用 | 安全（P3 备注） |
| ExcelDateHelper.isADateFormat 单项 ThreadLocal 缓存 | 每单元格 | 交替格式时 5 个正则全量重匹配 | **C9-003 P3** |
| IBinaryDataReader.readBytesTerm/read() 逐字节虚分发 | 每字节 | 单字节循环 + ByteArrayOutputStream | **C9-004 P3** |
| BlockCachedBinaryDataReader.findBlockForPosition | 每字节读 | LinkedList 线性扫块（≤8 块） | 安全（有界常数） |
| FieldCodecRegistry/FixedLengthStringCodecFactory 缓存 | 每字段解码 | ConcurrentHashMap，键空间有界 | 安全（对照组） |
| DocumentConverterManager.getConverter | 每次文档转换（非每字节） | 每次链式查找 new ChainedDocumentConverter | 安全（低频） |
| JFreeChart 图表导出 BufferedImage.createPatternImage | 每图案填充 | 8×8 小图创建+dispose，正确 | 安全 |

基准缺口登记见 C9-005（无 nop-benchmark 覆盖的解析/格式化 JMH 基准）。

---

### [C9-001] getFormat 每单元格新建 SimpleDateFormat/DecimalFormat + 反复正则编译（分配放大）

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/format/ExcelFormatHelper.java:94-142,144-230`；热路径消费点 `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/ExpandedSheetEvaluator.java:174-180`、`nop-format/nop-excel/src/main/java/io/nop/excel/model/ExcelWorkbook.java:56-68`
- **证据片段**:
  ```java
  public static Format getFormat(String formatStr) {
      ...
      Format format = formats.get(formatStr);   // 仅 8 个内建格式命中缓存
      if (format != null) return format;
      try { format = createFormat(formatStr); return format; ...
  // createFormat：5 次 String.replaceAll（每次重新编译正则）+ specialPatternGroup/amPm/daysAsText 匹配
  //  + new SimpleDateFormat(formatStr) / new DecimalFormat(...)
  ```
- **严重程度**: P2
- **并发场景**: 报表渲染热路径（evaluateCell 对每个有 styleId 且值为 Number 的单元格调用；HTML 导出 getExcelFormatValue 同）；多线程渲染各自重复编译——纯浪费无共享争用。
- **触发时序（量化口径）**: 万行 × 5 数字列报表 = 5 万次 getFormat；每次 createFormat ≈ 5 次 replaceAll（Pattern.compile ~1-2μs each）+ SimpleDateFormat 构造（~1-3μs）≈ 10-20μs → 单次渲染 0.5-1s 纯分配/编译开销，并伴随同速率的临时对象 GC 压力。相邻同格式单元格格式串相同，缓存命中率理论 >99%。
- **现状**: formats 缓存仅覆盖 8 个内建格式（zip/phone/ssn），日期/数字格式全部走 createFormat 即弃；注释自认"这里只缓存少量规定格式的format，不需要每个format都缓存"——设计时未评估每单元格调用频率。
- **风险**: 大报表导出耗时被格式化编译吃掉；分配速率放大 GC。注意：**不能**简单把 SimpleDateFormat 缓存复用解决（其非线程安全），需按 C1 口径同步处理（ThreadLocal 或池化或换 DateTimeFormatter）。
- **建议**: 分两层：a) 格式串→"格式工厂"缓存（ConcurrentHashMap，键=格式串，值=ThreadLocal<Format> 供应器或不可变 java.time.format.DateTimeFormatter 包装）；b) ExcelWorkbook/ExpandedSheetEvaluator 按样式对象缓存 Format（styleId 在渲染内不变）。参照平台 CacheConfig.newConfig(有界)。
- **失效面枚举**: 同族每单元格分配点：ExcelWorkbook.getExcelFormatValue（消费点，同修）；ExpandedSheetEvaluator.evaluateCell L176（消费点，同修）；ExcelDateHelper.isADateFormat（已有单项 ThreadLocal 缓存，见 C9-003）；PdfStyleHelper.getFontSize（无分配，安全）；TextWrapHelper LINE_BREAK_PATTERN（static final 预编译，正确对照组）。
- **信心水平**: 确定
- **误报排除**: 非冷路径——evaluateCell 为渲染主循环；非"原语使用"——是缓存缺失的量化浪费，符合本维度"热路径可量化的分配浪费"立项口径。
- **复核状态**: 已复核 2026-10-22：保留 P2（缓存仅 8 内建格式、5-7 次 replaceAll、两个消费点 per-cell 调用全部核实，量化链成立）

---

### [C9-002] FontManager JVM 单例粗锁：init 全程 synchronized + resolveFallbackFont 持锁做多次字体文件 I/O

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/font/FontManager.java:90-108,145-146,234-296,450-462`
- **证据片段**:
  ```java
  protected synchronized void init() {          // JVM 单例 this 锁
      if (inited) return;
      registerSystemFonts();
      ...
  }
  private synchronized PDFont resolveFallbackFont(PDDocument doc) {
      ...
      for (File file : cjkPrioritySortedFonts()) {     // 逐一加载候选字体文件
          PDFont font = loadFromFile(file, doc);       // FileInputStream + TTFont 解析（毫秒-百毫秒级/个）
          if (font != null && canEncode(font, CJK_PROBE_CHAR)) { ... }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 全 JVM 的 PDF 导出并发共享 FontManager.instance()；getFont（每文本段）先 init()（synchronized 方法，即使 inited=true 也要先 acquire this）；首次 CJK 回退解析时 resolveFallbackFont 持 this 扫描全部系统字体目录候选。
- **触发时序（量化口径）**: T1 线程渲染含中文报表，命中 loadFallbackFont → synchronized resolveFallbackFont 遍历 cjkPrioritySortedFonts（macOS 补充字体目录可达数百个 ttf/ttc），每个 loadFromFile 读取数 MB-数十 MB 并解析 → 持锁时长可达秒级；T2..Tn 其他线程的全部 getFont/getDefaultFont/canEncode 调用阻塞在 init() 的 monitor enter → **并发导出吞吐塌缩为串行**，且被一个冷路径（字体探测）阻塞。inited=true 后 init() 仍要 acquire/release（无快速无锁路径）。
- **现状**: discoveredFontFiles 的 DCL 正确（读路径无锁），但 init 的 synchronized 方法形态使每次 getFont 都过一遍全局锁；resolveFallbackFont 结果有 fallbackFontFile 缓存，锁内重 I/O 仅首次/缓存失效时发生——争用为"首次渲染雪崩 + 每次调用的锁往返"两层。
- **风险**: 批量并发导出（报表服务常态负载）时 P99 延迟由最慢的字体探测决定；锁内 I/O 属本维度明示的"锁内含 I/O"定向检查项命中。
- **建议**: a) init 改 DCL（volatile inited + synchronized 块仅包首次）；b) resolveFallbackFont 移出 this 锁——fallbackFontFile 已 volatile，可改为"锁内只做缓存读写，探测在锁外，用 AtomicReference 竞写"或独立锁对象隔离；c) 探测结果落盘缓存目录供重启复用。
- **失效面枚举**: FontManager 全部 synchronized 成员逐一核对：init（本条a）；resetForTesting（测试专用，豁免）；resolveFallbackFont（本条b）；discoveredFontFiles DCL 内 synchronized(this)（首次扫描锁内 I/O，同族问题——目录扫描仅 JVM 一次，量级可接受，随 a 一并收窄）；unencodableChars 复合锁（锁内仅集合操作，安全）。
- **信心水平**: 确定（锁结构确定；时长量化为估算链，无 JMH 基准，见 C9-005）
- **误报排除**: 非"低频路径粗锁豁免"——init 锁往返在每文本段热路径上；锁内 I/O 是定向检查项而非优雅性问题。
- **复核状态**: 已复核 2026-10-22：保留 P2（附频率校准：getFont 经 PdfRenderer.java:62-69 per-document fontCache，实为每文档每字体键一次、非每文本段——第二层"锁往返"减弱为可忽略；保留依据为第一层：首次 CJK 回退解析在 JVM 单例锁内做秒级字体 I/O（FontManager.java:248-296），阻塞全部并发导出线程；fallbackFontFile 缓存后一次性，无回退字体环境 null 不缓存则每文档重复）

---

### [C9-003] ExcelDateHelper.isADateFormat 单项 ThreadLocal 缓存在交替格式下失效（反复正则重匹配）

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/format/ExcelDateHelper.java:366-395,419-514`
- **证据片段**:
  ```java
  // variables for performance optimization:
  // avoid re-checking DataUtil.isADateFormat if a given format string represents a date format
  // if the same string is passed multiple times.  (POI bugzilla 55611 同源)
  private static ThreadLocal<Integer> lastFormatIndex = ...;
  private static ThreadLocal<String> lastFormatString = new ThreadLocal<>();
  private static ThreadLocal<Boolean> lastCachedResult = new ThreadLocal<>();
  ```
- **严重程度**: P3
- **并发场景**: 渲染线程逐单元格调用（经 ExcelFormatHelper.createFormat → isADateFormat）；ThreadLocal 设计本身线程安全，无正确性问题。
- **触发时序（量化口径）**: 表格按列交替两种非内建格式串 A/B → 相邻两次调用格式串必不同 → 单项缓存 100% 失效 → 每次执行 date_ptrn4/5/1/2/3a/3b 共 6 个正则匹配（每个 ~0.5-2μs）+ 字符串规范化拷贝 → 10 万单元格 ≈ 0.3-1.2s 纯重复计算。
- **现状**: 单项缓存为 POI 同源设计（拷贝代码），对小格式集已足够；但 N 列异构格式表（报表常态）下退化为无缓存。
- **风险**: 与 C9-001 叠加构成"格式化路径重复正则"的两个来源；量级 P3。
- **建议**: 换有界 ConcurrentMap<String,Boolean>（格式串基数天然有界=模型内格式数，对照 CacheConfig.newConfig(256)）；或在 ExcelStyle 模型加载时预解析 isDate 一次性写入样式对象。
- **失效面枚举**: 同族单项缓存：ExcelDateHelper 三 ThreadLocal 字段（本条）；FontManager.lastFailedCodePoint（非缓存，正确性用途，安全）；无其他单条目缓存（grep 核对 lastXxx 命名族）。
- **信心水平**: 确定
- **误报排除**: 无正确性问题不按 C1 报；按 C9"可复用/可缓存对象未复用"口径报 P3。
- **复核状态**: 已复核 2026-10-22：保留 P3（单项 ThreadLocal 缓存 ExcelDateHelper.java:371-395、未命中 6 个预编译正则 :481-512、交替格式 100% 失效推断全部核实）

---

### [C9-004] record 解码逐字节虚分发：readBytesTerm/read() 单字节循环 + 每字段 ByteArrayOutputStream 分配

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/IBinaryDataReader.java:418-438`；`StreamBinaryDataReader.java:169-207`；`BlockCachedBinaryDataReader.java:265-293`
- **证据片段**:
  ```java
  // readBytesTerm：终止符前逐字节 read()（接口虚分发）+ buf.write(c)
  while (true) {
      int c = read();               // BlockCached 实现：findBlockForPosition 线性扫 + readByte
      ...
      buf.write(c);
  }
  // BlockCachedBinaryDataReader.read()：每次单字节读都要
  // isPositionInCache（首尾位置比较）→ findBlockForPosition（LinkedList 遍历 ≤8）→ readByte
  ```
- **严重程度**: P3
- **并发场景**: record 解码热路径（每字节）；单线程，无争用，纯分配/分发开销。
- **触发时序（量化口径）**: 10MB 终止符分隔字段 → 10^7 次 read() 虚分发 + 10^7 次 ByteArrayOutputStream.write + BlockCached 下 10^7 次链表扫块（≤8 步）≈ 数百 ms 级解码开销，比块拷贝路径慢一个数量级。
- **现状**: readBytes(byte[],off,len) 已有块读实现（BlockCached 的 read 循环按块推进），唯 readBytesTerm 停留在逐字节；可基于"在缓冲区内 indexOf(term)"改造。
- **风险**: 大字段解码吞吐瓶颈；无正确性影响。
- **建议**: readBytesTerm 在各 reader 内实现"块内查找终止符"（ByteBuffer/ByteBuf 天然支持 indexOf 类操作），BlockCached 增加 findByteInWindow；ByteArrayOutputStream 预估容量（字段 maxLength）。
- **失效面枚举**: 逐字节路径全清单：IBinaryDataReader.readBytesTerm 默认、StreamBinaryDataReader 覆写（本条）；ByteBufferBinaryDataReader.readBytesTerm（缓冲区内循环，常数小，安全）；ByteBufBinaryDataReader.forEachByte 类（netty 优化，安全）；bits 读取族（低频，豁免）。
- **信心水平**: 确定
- **误报排除**: 有界读者的实现开销可接受，仅报无界累积+逐字节两实现；对照 C9"无量化依据不报告"——已给调用频率×单次代价估算链。
- **复核状态**: 已复核 2026-10-22：驳回——引用代码属实但热路径前提不成立：readBytesTerm 全仓无生产调用方（解码走 readBytes(length) 块读，ModelBasedBinaryRecordDeserializer.java:80,117；trailer 匹配 startsWithBytes 亦为块读+seek，IBinaryDataReader.java:569-574；模型层对 field.terminator 直接抛 ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED，RecordFileMeta.java:110-114）。调用频率=0，量化链不成立，按误报校准条款驳回，登记 watch-only

---

### [C9-005] 基准缺口登记：格式化/日期解析/PDF 字体探测/record 解码无 JMH 基准

- **文件**: `nop-benchmark/`（模块级缺失登记）；关联 `nop-format/nop-excel/src/main/java/io/nop/excel/format/ExcelFormatHelper.java`、`ExcelDateHelper.java`、`nop-report/nop-report-pdf/.../font/FontManager.java`、`nop-format/nop-record/.../reader/*`
- **证据片段**: nop-benchmark 现有基准未覆盖 format/report 单元的任何热路径（C9-001/002/003/004 四条的量化口径均为静态估算链，无基准可校准）。
- **严重程度**: P3
- **并发场景**: 基准缺口使上述发现的修复效果与回归均不可度量。
- **触发时序**: 不适用（登记项）。
- **现状**: 对照 nop-benchmark 惯例（平台其余热路径已有基准先例），本单元为零覆盖。
- **风险**: C9-001 的"每单元格 10-20μs"与 C9-002 的"秒级持锁"仅是估算；修复前后无回归标尺。
- **建议**: 四个 JMH 基准形态：a) getFormat vs 缓存版（@Benchmark 参数=格式串集合）；b) isADateFormat 交替/同构两模式；c) FontManager.getFont 并发吞吐（多线程 + 冷/热字体缓存两态）；d) readBytesTerm 块读改造前后（1MB/10MB 字段）。
- **失效面枚举**: 缺口全清单：nop-excel format 包、nop-report-pdf font/renderer、nop-record reader/codec、nop-chart-export 渲染——全部无基准。
- **信心水平**: 确定
- **误报排除**: 按 C9 第 6 条"基准缺口按 P3 登记（对照 nop-benchmark 惯例，注明建议的 JMH 基准形态）"立项。
- **复核状态**: 已复核 2026-10-22：保留 P3（nop-benchmark 仅 json/orm/stream/xlang/xpl 五子模块，本单元热路径零基准覆盖，核实成立）

---

## 维度复核结论

> 复核人：C9 独立复核子代理。日期：2026-10-22。方法：逐条重读全部引用源码 file:line，按 C9 校准条款（可量化瓶颈或锁竞争窗口 = 调用频率 × 单次代价）判定。R2 发现纳入同一判定表。

| 编号 | 判定 | 理由摘要（复核证据） | 现状标注 |
|------|------|---------------------|---------|
| C9-001 | 保留 P2 | 证据全部核实：缓存仅 8 内建格式（ExcelFormatHelper.java:74、76-92）；createFormat 链 5-7 次 replaceAll（:113、146-149、153、157、162）+ new SimpleDateFormat(:222)/new DecimalFormat(:262)；两个消费点均为 per-cell 主循环（ExpandedSheetEvaluator.java:173-180 于 :176 调用、ExcelWorkbook.java:53-66 于 :59 调用）。频率×代价量化链成立 | 未修复 |
| C9-002 | 保留 P2（附校准） | 锁结构核实：synchronized init（FontManager.java:90-108）、getFont 先 init（:145-146）、synchronized resolveFallbackFont 锁内逐字体文件 I/O（:248-296，尤其 :287-293 循环内 loadFromFile）、fallbackFontFile volatile 缓存（:58）、DCL（:450-462）。校准：getFont 实际频率为每文档每字体键一次（PdfRenderer.java:62-69 per-document fontCache），初审"每文本段 init 锁往返"表述偏重，该层减弱为可忽略；保留依据=第一层：首次 CJK 回退解析在 JVM 单例锁内做秒级字体扫描/解析（一次性；配置缺失环境 null 不缓存则每文档重复扫描），阻塞全部并发导出线程，属"锁内含 I/O"定向检查项命中 | 未修复 |
| C9-003 | 保留 P3 | 核实：单项 ThreadLocal 缓存（ExcelDateHelper.java:371-395，isCached 仅比对 lastFormatString）；未命中执行 6 个正则操作（:481、487、490、493、504、512，Pattern 均 static final 预编译 :50-62，代价为匹配非编译）；交替格式 100% 失效推断成立；POI bugzilla 55611 同源注释（:366-370）；调用入口 ExcelFormatHelper.createFormat(:135) | 未修复 |
| C9-004 | 驳回（登记 watch-only） | 引用代码属实（IBinaryDataReader.java:418-438 默认实现逐字节循环+BAOS；StreamBinaryDataReader.java:169-207 覆写；BlockCachedBinaryDataReader.java:265-293 单字节 read、LinkedList :135、findBlockForPosition :283/:517），但热路径前提不成立：readBytesTerm 全仓无生产调用方——反序列化走 readBytes(length) 块读（ModelBasedBinaryRecordDeserializer.java:80、117）；trailer 匹配 startsWithBytes 亦为块读+seek（IBinaryDataReader.java:569-574）；模型层对 field.terminator 直接抛 ERR_RECORD_ATTRIBUTE_NOT_IMPLEMENTED（RecordFileMeta.java:110-114），模型驱动的解码不可能到达该路径。调用频率=0，量化链不成立，按误报校准条款驳回 | 不适用（API 预留死代码面；若未来实现 terminator 字段语义接入解码路径，需重新立项） |
| C9-005 | 保留 P3 | 核实：nop-benchmark 仅 json/orm/stream/xlang/xpl 五个子模块，无任何 excel format/report pdf font/record reader 基准；C9-001/002/003 及已驳回 C9-004 的量化口径均为静态估算链 | 缺口仍在 |
| C9-R2-001 | 保留 P3 | 核实：二进制分支 lambda 内 prefix.toByteArray()（RecordFileMeta.java:137-140），同函数文本分支已提升到闭包外（:142-145），不一致属实；消费频率确认——StreamingRecordDeserializer.java:379-390 do-while 内 :384 每条记录调 checkUntil(field.getRepeatUntil(),...) → 每记录一次分配；startsWithBytes 为块读（IBinaryDataReader.java:569-574），不放大该路径 | 未修复 |

### 复核观察（不新立项，供 C10 与合并裁定参考）

- FontManager.canEncode 正向结果不缓存（FontManager.java:329-353）：每文本段 × 每码点调用 font.encode（仅负缓存 unencodableChars），且 unencodableChars 为 synchronizedMap、每 canEncode 一次共享互斥锁读——PDF 渲染热路径上的潜在每字符开销，建议后续轮次评估。
- 命名陷阱登记：R2 初审"netty 解码热路径"核查行引用的 ModelBasedPacketCodec/DefaultFieldCodecContext 实际位于 nop-record（io.nop.record.codec.impl），不在 nop-record-netty（io.nop.netty.ext.*）——record-netty 覆盖缺口判定不受该行影响。

### 批次覆盖核验（对照 dispatch-log.md 19 源码根清单）

- 批A excel：有核查痕迹（C9-001、C9-003、R2 ExcelIOConfig 行）。
- 批B ooxml：**零痕迹**——C9 两份文件全文无 ooxml 核查记录，登记同一缺口（与既有零痕迹登记对齐，不补审）。
- 批C pdf + record + record-netty：pdf、record 有痕迹（C9-002 与 R2 PdfStyleHelper/TextWrapHelper 行；C9-004、C9-R2-001、R2 FieldCodecRegistry/netty 解码面行——注意该行实际核查对象在 nop-record）；record-netty **零痕迹**，登记同一缺口（四维重合）。
- 批D report + mermaid + svg + chart-export + converter + tablesaw + markdown-ext + office-*：report、chart-export、converter 有痕迹（C9-001 消费点与 R2 ExpandedSheetEvaluator/XptRuntime 行；R2 chart-export 行；初审 DocumentConverterManager 行）；mermaid、svg、tablesaw、markdown-ext、office-model、office-doc-model **零痕迹**，登记同一缺口（不补审）。
- C9 缺口清单（与既有零痕迹登记对齐）：ooxml（含 pptx）、record-netty、tablesaw、office-model、office-doc-model、mermaid、svg、markdown-ext；converter 对 C9 有核查痕迹，不在缺口内。

## 最终保留项

| 编号 | 严重度 | 一句话 |
|------|--------|--------|
| C9-001 | P2 | getFormat 每单元格新建 SimpleDateFormat/DecimalFormat + 反复正则编译，万行×5 数字列报表约 0.5-1s 纯分配/编译开销并放大 GC（ExcelFormatHelper.java:94-142,144-230） |
| C9-002 | P2 | FontManager JVM 单例粗锁：首次 CJK 回退解析秒级持锁做字体文件 I/O，阻塞全部并发 PDF 导出线程（FontManager.java:90-108,248-296） |
| C9-003 | P3 | ExcelDateHelper.isADateFormat 单项 ThreadLocal 缓存在交替格式下 100% 失效，每格 6 个正则重匹配（ExcelDateHelper.java:371-395,481-512） |
| C9-005 | P3 | 基准缺口：format/PDF 字体/record 解码热路径无任何 JMH 基准，修复效果与回归不可度量（nop-benchmark/） |
| C9-R2-001 | P3 | RecordFileMeta.fixBody 二进制 trailer 匹配 lambda 每记录分配 prefix.toByteArray()，同函数文本分支已示范正确写法（RecordFileMeta.java:137-145） |

驳回项：C9-004（readBytesTerm 逐字节路径无生产调用方，非热路径，watch-only）。

保留合计：5 项（P2×2、P3×3）。
