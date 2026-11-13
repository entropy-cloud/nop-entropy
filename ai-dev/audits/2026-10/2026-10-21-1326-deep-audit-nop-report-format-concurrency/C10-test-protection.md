# C10 测试保护力 — nop-report + nop-format 并发审计 25 条保留发现

> 审计单元：CRA WI 5.4 nop-report-format 并发审计四维最终保留项（C1×7 + C5×8 + C6×5 + C9×5 = 25 条）。
> 方法：逐条问「现有测试套是否有测试能在该缺陷被引入时变红」。对每条发现 grep 对应模块 `src/test` 下相关测试类，核实测试是否存在、断言什么，再按「断言面是否覆盖缺陷面」判定会红/不会红。判定只针对发现**立案的缺陷面**（并发面/内存界/契约面），测试偶然覆盖的功能面在现状标注中单独说明。
> 盲审声明：本审计未读 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；仅读取四维最终保留项文件（C1/C5/C6/C9）与 live 源码、测试代码。所有「无相关测试」判定均附 grep 命令与命中数。
> 判定日期：2026-10-22。

## 总体证据（贯穿性结论）

1. **全单元测试套零并发**：`grep -rln "CountDownLatch\|ExecutorService\|new Thread\|CompletableFuture\|CyclicBarrier" --include="*.java" nop-format/*/src/test nop-format/nop-ooxml/*/src/test nop-report/*/src/test`（排除 target）→ **0 个文件命中**。因此任何并发交错类缺陷面（C1 全部、C9-002 锁争用面）在现有套上不可能变红。
2. **内存界/性能面零断言**：nop-benchmark 仅 5 个子模块（`ls nop-benchmark/` → json/orm/stream/xlang/xpl），`find nop-benchmark -name "*.java" -path "*test*" | xargs grep -ln "ExcelFormat\|FontManager\|readBytes\|ExcelDate"` → **0 命中**；功能测试全部使用小 fixture（如 TestExcelResourceIO 10 行 xlsx），无行数/体积/耗时断言，因此 C5/C9 的物化与分配缺陷面不可能变红。
3. **3 个关键测试被 @Disabled**：TestPdfDoc（nop-pdf）、TestPptxToMarkdownConverter（ooxml-markdown）、TestModelBasedTcpServer（record-netty，0 个断言语句）——对应路径的仅存测试保护实际为 0。

## 保护力矩阵（25 行）

| 发现编号 | 严重度 | 缺陷面 | 相关测试（file:line） | 会红判定 | 现状标注 |
|---------|--------|--------|----------------------|---------|---------|
| C1-001 | P2 | DocumentConverterManager 内层裸 HashMap/TreeSet 运行期注册 vs 查找的并发损坏 | TestDocumentConverterManager.java:20-25,49；TestDocumentConverterTextConversion.java:87-96（均为单线程顺序 register+lookup） | 不会红 | registerConverter/getConverter/getToFileTypes 的功能面有测试保护；并发交错面无任何多线程测试（总体证据 1），运行期再注册场景零覆盖 |
| C1-002 | P2 | FontManager addFontAlias 无锁写 HashMap、defaultFont 锁外惰性重建非 volatile | TestFontManager.java:33-38（addFontAlias 后 getFont 断言别名解析）；TestPdfFontResolution.java:39-40（resetForTesting 隔离） | 不会红 | 别名**功能面**受 testFontAlias 保护（若 put 被删会红）；无同步**并发面**不可见（单线程）。addFontAlias 生产调用方为零，唯一调用者即该测试 |
| C1-003 | P2 | ExcelFormatHelper 静态 formats 由 public addFormat 无同步写、渲染热路径并发读 | TestExcelFormatHelper.java:24-71（getFormat 转换语义） | 不会红 | getFormat 功能面有保护；addFormat 本身零测试调用（`grep -rn "addFormat(" --include="*.java" . \| grep -v "main/java"` → **0 命中**），public 可写 API 的守卫缺失无任何测试感知 |
| C1-004 | P3 | JFreeChartStyleAdapter 预定义可变 Shape 逃逸共享 | 无 | 不会红 | `grep -rn "getPredefinedShape\|JFreeChartStyleAdapter" --include="*.java" . \| grep -v target \| grep -v "main/java"` → **0 命中**；chart-export 测试全部面向 echarts 适配层，JFreeChart shape 路径零覆盖 |
| C1-005 | P2 | RandomAccessFileBinaryDataReader.detach/duplicate 共享 RAF 指针伪独立视图 | TestDetachReaders.java:20-298（detach 矩阵仅覆盖 BlockCached/ReaderText 族）；TestSubBinaryDataReader.java:32-56（RAF 仅 subInput 顺序语义） | 不会红 | RAF 的 detach/duplicate 零测试触达（`grep -rn "RandomAccessFileBinaryDataReader" --include="*.java" nop-format/nop-record/src/test/` → 仅 1 命中 TestSubBinaryDataReader.java:32，不调 detach/duplicate）；TestDetachReaders.java:77 的 RAF 同类交错测试形态对 BlockCached 存在（:75 @Disabled）、对 RAF 缺位 |
| C1-R2-001 | P3 | ByteBufBinaryDataReader.isDetached 恒 true、detach=duplicate 语义同化（契约漂移） | 无 | 不会红 | `grep -rn "isDetached" --include="*.java" nop-format/nop-record/src/test/` → **0 命中**（repo 其余命中为 nop-dataset 无关同名接口）；isDetached/detach 契约族全族无测试 |
| C1-006 | P3 | FieldCodecRegistry.DEFAULT public static 非 final 引用可变性 | TestModelBasedPacketCodec.java:96,116,157,184（以 DEFAULT 为构造参数消费） | 不会红 | 仅作值消费——DEFAULT 被置 null 级破坏会红，但「非 final 可被替换 + 替换可见性」契约面无测试（无断言 final/无再赋值检测） |
| C5-001 | **P1** | record 反序列化数据驱动字段长度无上限直接 new byte[length]（分配先于校验） | TestRecordRegionBoundary.java:120-135（EOF 短数据 → ERR_RECORD_NO_ENOUGH_DATA）；TestBaseTypeStreaming.java:31-60；TestModelBasedPacketCodec.java:93-105,155-173（仅合法帧） | 不会红 | 测试模型全部为固定小 `length="N"`（test/*.record-file.xml，`grep "lengthExpr" src/test` → **0 命中**）；短数据读校验面受保护，但**超大长度分配面**零覆盖——无任何测试喂入损坏/恶意长度值并断言上限错误；netty 面 TestModelBasedTcpServer.java:20 @Disabled 且 0 断言 |
| C5-002 | P3 | readBytesTerm 终止符缺失时全流累积无上限 | 无 | 不会红 | `grep -rn "readBytesTerm" --include="*.java" nop-format/nop-record/src/test/` → **0 命中**；repo 全库仅 4 个 main 源文件（声明处），与 C5 复核「零生产调用方」一致，测试侧同样为零 |
| C5-003 | P3 | readAvailableBytes/StreamBinaryDataReader.detach 剩余流全量物化 | 无 | 不会红 | `grep -rn "readAvailableBytes" --include="*.java" nop-format/nop-record/src/test/` → **0 命中**；TestDetachReaders 未触及 StreamBinaryDataReader.detach（其 detach 矩阵仅 BlockCached/ReaderText） |
| C5-004 | P2 | ExcelRecordInput 流式接口全表物化 List\<Map\>（HeaderListRecordOutput 无界 ArrayList） | TestExcelResourceIO.java:47-118（10 行 fixture 功能读断言） | 不会红 | 读写功能面（含 close 清理，:250-293）保护良好；内存界面零保护——无大表 fixture、无行数上限/分批行为断言，物化缺陷被引入或移除现有实现均不可见 |
| C5-005 | P3 | PdfDoc.getAllText 默认 maxSizePerPage=-1 无界累积 + 非文件资源整体 byte[] 物化 | TestPdfExtractHelper.java:18-24（getAllText 功能面）；TestPdfDoc.java:9-26（@Disabled） | 不会红 | 文本抽取功能面有活跃测试（TestPdfExtractHelper）；内存界面无断言；直接覆盖 getAllText 的 TestPdfDoc 整类 @Disabled 且硬编码 Windows 路径 |
| C5-006 | P3 | ChainedDocumentConverter 链式转换中间结果三重全量驻留 | TestDocumentConverterTextConversion.java:87-96（链式转换功能面，微型载荷） | 不会红 | 链式转换**功能面**受保护（中间类型路由正确性会红）；中间产物物化体量面无任何断言 |
| C5-R2-001 | P2 | rawVarName 诊断路径 peek+hex 4-5× 放大且驻留至请求结束 | 无 | 不会红 | `grep -rn "rawVarName" --include="*.java" nop-format/`（排除 target）→ 仅 2 个 main 源文件命中，**测试 0 命中**；该 opt-in 特性端到端零测试 |
| C5-R2-002 | P3 | markdown/ooxml 文本转换器全量 StringBuilder 物化 | TestMarkdownNormalizer.java:9-36（功能断言×7）；TestPptxToMarkdownConverter.java:10-19（@Disabled、0 断言）；DocxToMarkdownConverter 无测试 | 不会红 | 转换功能面部分保护（markdown-ext 有断言；pptx 面 @Disabled；docx 转换器 `grep -rln "DocxToMarkdownConverter" */src/test` → 0 命中）；内存界面零断言 |
| C6-001 | P2 | RandomAccessFileBinaryDataReader 构造器 raf.length() 异常路径泄漏 fd | TestSubBinaryDataReader.java:32-56（RAF 构造仅 happy path） | 不会红 | 唯一构造点测试不触发 length() 失败，也无 fd 关闭断言；构造异常路径零覆盖 |
| C6-002 | P3 | downloadXlsx 临时文件一次性定时删除、重启泄漏、删除失败静默 | TestExcelReportHelper.java:27-30（testSave 方法体为空） | 不会红 | `grep -rn "downloadXlsx" --include="*.java" . \| grep -v target` → 仅 2 个 main 命中（定义 + nop-dyn 调用方），**测试 0 命中**；持有类的测试文件存在但为空壳 |
| C6-003 | P3 | PdfDoc.selectPages 族共享 PDPage、关闭序契约缺失 | TestPdfDocSplit.java:16-38（仅 splitIntoPages 页数断言）；TestPdfAssemble.java:127（注释明示「selectPages 静默跳过，不可依赖」） | 不会红 | splitIntoPages 功能面（页数）有保护；selectPages/selectPageRange 直接使用与父子关闭序契约零测试 |
| C6-004 | P3 | OfficePackage zip 句柄释放依赖 loadInMemory 单点、无泄漏检测钩子 | TestOfficePackageCopy.java:28-55（copy() 隔离回归，属并发污染另一面）；TestWordTemplate.java:55-123（模板渲染功能面） | 不会红 | 模板可变共享隔离面（copy 隔离）与渲染功能面有保护；「缓存登记时 store 必须已释放」守备面无断言（发现建议的 isResourceStoreOpen 钩子不存在，无可断言对象） |
| C6-R2-001 | P3 | ModelBasedPacketCodec 顶层 ByteBufBinaryDataReader 所有权不对称（close 即双重释放/不 close 契约悬空） | TestModelBasedPacketCodec.java:137-148（subInput 子视图 retain 面）；:155-173（decodeFromBuf happy path） | 不会红 | 子视图 retain 面（testSubInputRefCntAfterParentClose）有保护——但那是**另一个面**；顶层 reader 从不 close、所有权契约面零断言（decodeFromBuf 不暴露 reader，测试无从关闭） |
| C9-001 | P2 | getFormat 每单元格新建 SimpleDateFormat/DecimalFormat + 反复正则编译（分配放大） | TestExcelFormatHelper.java:24-71（格式转换语义）；TestHtmlReportRenderer.java:26-46（渲染功能面） | 不会红 | 格式语义功能面有保护（改动格式转换逻辑会红）；分配/缓存性能面零断言（无 JMH、无耗时/分配率断言），引入缓存或移除缓存均不可见 |
| C9-002 | P2 | FontManager JVM 单例粗锁：首次 CJK 回退解析秒级持锁阻塞并发导出 | TestFontManager.java:13-39；TestPdfFontResolution.java:35-40；TestPdfRenderDefects（均功能性、单线程；复核修正：原稿引用的 TestPdfFontFidelity 在测试树中不存在） | 不会红 | 字体解析**正确性**面保护良好（多测试类含 resetForTesting 隔离）；锁结构与持锁时长面零断言——init 改锁、resolveFallbackFont 移锁等重构对套件完全透明 |
| C9-003 | P3 | isADateFormat 单项 ThreadLocal 缓存交替格式 100% 失效（反复正则重匹配） | TestExcelDateHelper.java:88-103（同线程连续查询多个互异格式串并断言各自正确结果） | 不会红 | 缓存**正确性**面有保护（缓存错乱返回陈旧结果会红，测试串涵盖 true/false 交替）；立案的**性能面**（交替失效）无断言——这是性能缺陷，功能测试原理上不可见 |
| C9-005 | P3 | format/PDF 字体/record 解码热路径无 JMH 基准（基准缺口本身） | 无（缺口即发现） | 不会红 | `ls nop-benchmark/` → 仅 json/orm/stream/xlang/xpl；`find nop-benchmark -name "*.java" -path "*test*" \| xargs grep -ln "ExcelFormat\|FontManager\|readBytes\|ExcelDate"` → **0 命中**。基准缺口按定义为不可红项，只能靠新增基准闭合 |
| C9-R2-001 | P3 | RecordFileMeta.fixBody 二进制 trailer lambda 每记录 prefix.toByteArray() | 无 | 不会红 | `grep -rn "checkUntil\|fixBody" --include="*.java" nop-format/nop-record/src/test/` → **0 命中**；fixBody/checkUntil 路径零测试触达 |

## 缺保护登记

### P1（1 条，最高优先）

- **C5-001** record 反序列化数据驱动长度无上限（P1 唯一保留项完全无保护）。建议测试形态：新增含 `lengthExpr` 的 record-file 测试模型 + 喂入超大长度字段（如 0x7FFFFF00）的输入，断言抛 `ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG` 而非 OOM（修复后该测试成为回归保护）。

### P2（9 条）

- **C1-001**：两线程并发 registerConverter + getConverter 压测（jcstress 或 CountDownLatch 起跑），配合_inner 容器类型断言（白盒断言内层为并发容器）。
- **C1-002**：多线程并发 addFontAlias + getFont 测试；或白盒断言 fontNameAliases 为并发容器/defaultFont 写路径在锁内。
- **C1-003**：白盒断言 formats 为并发容器；或运行期 addFormat + 并发 getFormat 的 smoke 测试。
- **C1-005**：对 RandomAccessFileBinaryDataReader.detach() 补 TestDetachReaders 同款父子交错读矩阵（断言父 reader 位置不受 detached 视图读推进影响——按当前缺陷该测试应当先红，修复后转绿）。
- **C5-004**：大行数（如 5 万行生成式 fixture）导入测试断言峰值内存/分批行为；至少断言行数上限配置生效。
- **C5-R2-001**：声明 rawVarName 的模型解码测试，断言 raw 区域内容正确且解码后 context 无大数组驻留（弱断言：功能正确性先补齐）。
- **C6-001**：构造测试——以 length() 必抛的 File 实现/并发删除窗口触发构造异常，断言无 fd 泄漏（可用 reflection 断言raf 已关闭或借助 fd 计数）。
- **C9-001**：getFormat JMH 基准（同格式串 10 万次调用 vs 基线），或单测断言同格式串二次调用返回缓存实例（`assertSame`）。
- **C9-002**：FontManager 并发 getFont 吞吐基准（多线程 + 冷字体缓存态）；最小形态为两线程并发首渲染 + 断言总耗时有上界。
- **C6-002**（复核修正补登）：downloadXlsx 临时文件生命周期断言——TestExcelReportHelper 当前为空壳，补「调用后 temp 文件已调度删除/清理」最小断言；进程重启残留场景登记为已知限制。

### P3（15 条，聚合登记）

- 契约/守备面（建议白盒断言或 javadoc 契约 + 断言测试）：C1-004（getPredefinedShape 返回实例断言不可变性或 clone）、C1-R2-001（isDetached 语义断言）、C1-006（DEFAULT final 性断言，反射 isFinal 或编译期检查）、C6-003（selectPages 父子关闭序文档化 + 最小顺序测试）、C6-004（isResourceStoreOpen 守备钩子 + 缓存登记断言）、C6-R2-001（顶层 reader 所有权契约测试：close 幂等性断言）。
- 潜伏 API 面（零调用方，修复时随行补测试）：C5-002（readBytesTerm maxLength 超限断言）、C5-003（detach 物化上限断言）。
- 内存界/性能面（JMH 或有界 fixture）：C5-005（getAllText maxSizePerPage 默认值行为断言）、C5-006（链式转换中间产物落盘后内存峰值断言）、C5-R2-002（大文档转换超时/内存上限）、C9-003（isADateFormat 交替模式基准）。
- 缺口即发现：C9-005（新增 4 个 JMH 基准：getFormat/isADateFormat/FontManager 并发 getFont/readBytesTerm 块读，形态已在 C9-005 建议节给出）。
- 零触达路径：C9-R2-001（fixBody/checkUntil 的功能回归测试先行，性能断言随后）。

## 汇总

- **矩阵规模**：25 行（C1×7、C5×8、C6×5、C9×5），与四维最终保留项一一对应，无遗漏。
- **会红 / 不会红**：**会红 0 / 不会红 25**。无任何保留发现的立案缺陷面被现有测试套保护；12 条存在「相邻功能面保护」（矩阵现状标注列），并发/内存界/契约缺陷面本身全部不可红。
- **P1（C5-001）保护力单独结论**：**无保护**。现有 EOF 短数据测试（TestRecordRegionBoundary.java:120-135）保护的是「读不足报错」面，与立案的「分配先于校验、超大长度直接 OOM」面正交；测试模型无 lengthExpr、无恶意长度输入、netty 集成测试 @Disabled。若引入等价缺陷（或回退未来修复），测试套 100% 保持绿。建议将上节 P1 测试形态列为该缺陷修复的准入门槛。
- **缺保护登记数**：25 条全部登记——P1×1、P2×9、P3×15（P3 聚合为 5 组；复核修正：原稿漏登 C6-002，见文末复核结论）。
- **结构性根因**：全单元 0 多线程测试、0 JMH 基准、3 个关键测试 @Disabled（TestPdfDoc/TestPptxToMarkdownConverter/TestModelBasedTcpServer）；并发审计发现与测试套之间存在系统性盲区，缺保护登记应作为修复轮次的验收输入（每条修复须附使其变红的回归测试）。

## 复核结论

> 复核人：C10 独立复核子代理（C10-reviewer）。复核日期：2026-10-22。方法：抽样复核 + 一致性核对（非重做）；对抽中行独立 grep/读取 `src/test` 重演证据。未读 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；未修改产品代码与四维文件。

### 复核范围（抽中 9 行，覆盖 P1×1 + P2×4 + P3×4）

- **P1**：C5-001
- **P2**：C1-002、C1-005、C5-004、C9-002
- **P3**：C1-004、C1-R2-001、C6-002、C9-003
- 「会红」行复核条件为空集（矩阵 0/25 会红）；同时对全部抽中「不会红」行反向排查，未发现任何会红的漏判测试。

### 逐条结果

| 行 | 结果 | 证据（独立重演） |
|----|------|------------------|
| C5-001 | **PASS** | TestRecordRegionBoundary.java:119-136 两个 EOF 测试仅断言 `ERR_RECORD_NO_ENOUGH_DATA`（:125/:135），测试模型 test-eof-short-data.record-file.xml 为固定 `length="10"`；`grep -rn "lengthExpr" nop-record/src/test` → **0 命中**；生产无界路径确认存在（AbstractModelBasedRecordDeserializer.java:210-227 lengthExpr 求值无上限，下游 DynLVFieldBinaryCodec.java:73 `new byte[len]`）；TestModelBasedTcpServer.java:20 `@Disabled` 且方法体 0 断言（重读确认）。「读不足报错」面与「超大长度分配」面正交性成立，**无保护**结论成立 |
| C1-002 | **PASS** | TestFontManager.java:33-38 testFontAlias（addFontAlias→getFont 断言 "Courier"）属实；`grep -rn "addFontAlias" --include="*.java"`（排除 target）→ 全 repo **2 命中**（FontManager.java:141 定义 + 该测试 :34），「生产调用方为零、唯一调用者即该测试」成立；FontManager.java:48 `fontNameAliases` 为裸 HashMap、:141 addFontAlias 无同步；测试全单线程 → 并发面不可见，不会红成立 |
| C1-005 | **PASS** | `grep -rn "RandomAccessFileBinaryDataReader" nop-record/src/test` → **1 命中**（TestSubBinaryDataReader.java:32，重读确认仅 subInput/seek/read，不调 detach/duplicate）；TestDetachReaders 全文覆盖 BlockCached/ReaderText 族，RAF 零触达。精度补注：:77 交错测试形态存在但 :75 `@Disabled`（不影响 不会红，反而强化之） |
| C5-004 | **PASS** | TestExcelResourceIO.java:46-118 功能读断言（10 行 fixture，assert list.get(9)）；:250-293 close/临时目录清理测试属实（testCloseWithoutEndWriteClosesSheetWriter、testCloseClearsTempDirOnFailure）；全文无行数上限/内存断言 → 物化面不可见，不会红成立 |
| C9-002 | **PASS with correction** | TestFontManager.java:13-39、TestPdfFontResolution.java:38-41（resetForTesting @BeforeAll）属实且均单线程；但原稿引用的 **TestPdfFontFidelity 不存在**（`find . -name "TestPdfFontFidelity*.java"` → 0 命中），已修正为 TestPdfRenderDefects。不会红判定不受影响 |
| C1-004 | **PASS** | `find src/test -name "*.java" \| xargs grep -ln "JFreeChartStyleAdapter\|getPredefinedShape"` → **0 命中**，shape 路径零测试触达，不会红成立 |
| C1-R2-001 | **PASS** | `grep -rn "isDetached" nop-record/src/test` → **0 命中**；repo 其余命中确为 nop-dataset/nop-dao 同名接口族（含 nop-tablesaw 的 dataset 转换测试，同族无关），不会红成立 |
| C6-002 | **PASS** | `grep -rn "downloadXlsx"`（排除 target）→ **2 命中**（ExcelReportHelper.java:116 定义 + NopDynModuleBizModel.java:108 调用方），测试 0 命中；TestExcelReportHelper.testSave 方法体为空（重读全文确认）。不会红成立 |
| C9-003 | **PASS** | TestExcelDateHelper.java:85-104 testIsADateFormatDetection：true/false 交替格式串（"yyyy-mm-dd"/"h:mm:ss AM/PM"/"[h]:mm:ss" vs "0.00"/"General"/null）各自断言正确结果 → 缓存正确性面有保护、交替失效性能面无断言，不会红成立 |

### 汇总数字核对

- 矩阵 25 行、编号与四维最终保留项一一对应（C1×7+C5×8+C6×5+C9×5）：**一致**；严重度逐行与四维文件一致。
- 驳回项 C9-004、C6-R2-002 缺席：**正确**。
- 会红/不会红计数：矩阵逐行核对 0 会红 / 25 不会红，与汇总「会红 0 / 不会红 25」**一致**。
- 缺保护登记数：原稿声称 P3×15 全登记，实际 P3 聚合仅列 **14** 条（6+2+4+1+1），**C6-002 缺席**——已修正（补登至 P2 条目之后，聚合 4 组→5 组），修正后 P1×1+P2×9+P3×15=25 成立。
- P1（C5-001）无保护结论的证据链（TestRecordRegionBoundary 正交性）：**重演通过**（见逐条结果首行）。
- 总体证据 1/2/3 重演：全测试树并发原语 grep → **0 文件命中**；nop-benchmark 仅 5 子模块且基准 grep **0 命中**；@Disabled 三件套（TestPdfDoc/TestPptxToMarkdownConverter/TestModelBasedTcpServer）全部确认——**一致**。
- 附加抽查（非抽样行的 grep 声明）：C5-002 readBytesTerm、C5-003 readAvailableBytes、C1-003 addFormat、C5-R2-001 rawVarName、C9-R2-001 fixBody/checkUntil 的测试侧命中数重演均为 **0**，与矩阵一致。

### 整体判定

**PASS with corrections**。抽中 9 行的「相关测试」引用与「会红判定」全部独立成立，未发现判断性错误；发现并已修正 2 处登记/引用缺陷：

1. **[Major] 缺保护登记漏登 C6-002**：汇总声称 25 条全登记但 P3 聚合实列 14 条；已补登并将聚合组数 4→5。
2. **[Minor] C9-002 引用不存在的测试类 TestPdfFontFidelity**：已修正为 TestPdfRenderDefects（不会红判定不变）。
3. **[Watch] C1-005 精度补注**：TestDetachReaders.java:77 交错形态对 BlockCached 存在但 :75 @Disabled，已在矩阵行标注（结论不变）。
