# record 解码信任边界与 format 并发契约（CRA 8.34 R3-04 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约）
> Last Reviewed: 2026-11-02
> Source: plan 2527（CRA Phase 8.34 R3-04 report-format 批）；审计目录 `ai-dev/audits/2026-10/2026-10-21-1326-deep-audit-nop-report-format-concurrency/`；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.3 R3-04
> Owners: 裁定①②（nop-record 解码域）、裁定③（nop-report-pdf FontManager）、裁定④（nop-excel ExcelFormatHelper）、裁定⑤（nop-record RAF reader）、裁定⑥（nop-report-core Excel 导入包络）、裁定⑦（nop-converter 注册表）、裁定⑧（测试缝总表）

本文是 plan 2527 全部 10 项成员修复的唯一设计裁决权威。每条裁定给出：决策、理由、被否决的备选、零触碰边界。行号以 live 基线 `9d90c6e070` 亲读值为锚，后续漂移以 live 为准。

## 裁定① C5-001：record 解码字段长度上界（单点校验）

**决策**：

1. 上限校验收口在 `AbstractModelBasedRecordDeserializer.getFieldLength` / `getObjectLength` 两函数**返回前**（本批唯一校验点，禁散点）：求值结果 `> RecordConstants.DEFAULT_MAX_FIELD_LENGTH` 即抛 `ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG`（`RecordErrors.java` :71 既有错误码，param `ARG_LENGTH`+`ARG_MAX_LENGTH`，零新错误码）。
2. 上限常量 `DEFAULT_MAX_FIELD_LENGTH = 64MB`（`64 * 1024 * 1024`），落 `RecordConstants.java`（`DEFAULT_MAX_COLLECTION_SIZE` :41 同文件命名族）。
3. 不做模型级可配置覆盖。理由：模型级 `maxLength` 需改 `record.xdef` + 生成管线（RecordFieldMeta 为模型生成类），触发 Protected Area plan-first，与 P1 修复不成比例；64MB 全局常量单点可逆，超大单字段需求属记录协议设计包络外，由 owner doc 声明。

**上限值论证（64MB 而非 1MB）**：缺陷面是「无界」（恶意 lengthExpr 可达 2GB+，分配随堆大小失控），不是「任何大分配」。同族既有上界：`DynLVFieldBinaryCodec` :34 = 64MB（本仓库二进制变长域的最强家族先例）、`CountArrayBinaryCodec.checkCount` :57-61 = 1M 元素、`LVFieldBinaryCodec` :37-42 相对长度。采 64MB 与 DynLV 家族先例对齐：合法大二进制字段（blob/编码文本）不受误伤，恶意 2GB+ 请求在分配前被拒；单请求最坏分配从「堆大小」收敛为「64MB 确定值」。

**netty 面单点覆盖论证**：校验位于 `getFieldLength`/`getObjectLength` 求值后、任何 reader 分配前，而全部生产路径共用该基类求值链——文件路径 `readField0`→`getFieldLength`→`decodeString`→`readBytes`（分配点 `IBinaryDataReader.readBytes` 默认实现 `new byte[n]`）；对象路径 `readObject`/流式 `STAGE_INIT`→`getObjectLength`→`subInput`+`getRawDataString`；netty 路径 `ModelBasedPacketCodec.decodeFromBuf` :167 走同一 deserializer。故单点校验结构上覆盖文件/netty 双路径，不依赖 `maxFrameLength`（:115 可选配置默认 0 不生效——帧界内损坏长度照样先分配，这正是缺陷机制）。`readBytes` 接口层参数化防御**采否**（接口签名波及全部 reader 实现；登记 Non-Blocking Follow-up）。

**零触碰边界**：`readString`（:80，长度=期望串字节长，安全对照）零改动；同族保护三处（checkCount/DynLV/LVField）零回退；`peekMatch` 条件长度为静态模型配置非数据驱动，不入本裁定面；负长度/零长度语义维持现状（校验仅针对 `> MAX`）。

## 裁定② C5-R2-001：rawVarName 诊断路径截断（与裁定① 同 diff）

**决策**：采**截断方案**（否决 ByteString 延迟 hex 化）：

1. `RecordConstants` 新常量 `MAX_RAW_DATA_DIAG_CHARS = 4096`（诊断上限语义单一常量）。
2. 二进制路径 `ModelBasedBinaryRecordDeserializer.getRawDataString`：peek 字节数收口为 `min(length, MAX_RAW_DATA_DIAG_CHARS / 2)`（hex 输出 ≤ 4096 字符），超长时尾部追加 `"...(truncated)"` 标记。截断后分配上界 = 2KB peek + 4KB 字符串，4-5× 放大消除。
3. 文本路径 `ModelBasedTextRecordDeserializer.getRawDataString` :49-51：peek 字符数收口为 `min(length, MAX_RAW_DATA_DIAG_CHARS)`，同标记。
4. 诊断语义契约：rawVarName 变量承载的是**诊断前缀**而非完整原文；owner doc 声明。与裁定① 同 diff 交付（叠加无界 length 时「临时分配升级为请求期驻留」的通道被两裁定共同关闭）。

**否决延迟 hex 化的理由**：保存 ByteString 原引用到请求结束仍驻留原始字节（无界 length 下驻留面不减），仅消除 hex 放大；截断方案同时消除分配与驻留两面且实现单点。

**功能钉先行**：rawVarName 特性当前端到端零测试——先补「含 rawVarName 模型解码正确性」功能钉（短内容 hex 全文断言），再叠截断断言。

## 裁定③ G4：FontManager 单设计联合闭合（C1-002 裸写 + C9-002 锁内 I/O）

**决策**（双半边同 diff，禁分开交付）：

1. **C1-002 半边**：`fontNameAliases` 换 `ConcurrentHashMap`（:48）；`defaultFont` 加 `volatile`（:66），`getDefaultFont` 的 null 兜底重试收进 `synchronized(this)` 双检块（:123-134），`addFontAlias` 因 CHM 天然线程安全无需加锁（:141-143）。同族安全字段（`systemFonts` :47 构造期写、`unencodableChars` :64 已 synchronizedMap、`fallbackFontFile` :58 已 volatile）零触碰。
2. **C9-002 半边**：`init` 改 DCL——`inited` 加 `volatile`，快速路径无锁返回，`synchronized(this)` 仅包首次初始化（:90-108）；`resolveFallbackFont` 的持锁对象从 `this` 改为**专用锁** `fallbackLock`（:248-296）——CJK 候选逐个 `loadFromFile` 探测（:287-294）仍串行（防重复探测）但不再阻塞 `getFont`/`init`/`getDefaultFont`；`fallbackFontFile` volatile 竞写语义维持（写点在 fallbackLock 内、读点无锁快路径）。
3. **discoveredFontFiles DCL 收窄**（:450-462）：扫描锁从 `this` 改专用 `fontScanLock`——init 后 `this` 锁上不再发生任何文件系统 I/O。
4. **锁序声明**：存在获取序仅 `fallbackLock → fontScanLock`（resolveFallbackFont → findDiscoveredFont → discoveredFontFiles）；`this` 锁（init/getDefaultFont 重试/resetForTesting）不获取另两把锁——无 ABBA 环。`resetForTesting` 维持 `synchronized(this)`（测试单线程语义，volatile 字段写无锁序冲突）。
5. **交叉回归验证设计**：C1-002 并发钉（addFontAlias/getFont 交错 smoke）依赖 CHM 而非锁结构，在 C9-002 改锁后保持绿；红④ 锁结构钉（resolveFallbackFont 方法无 `synchronized` 修饰符 + fallbackLock 字段存在）在 C1-002 落地后保持绿——两钉独立可跑。

**校准注记（防误升格，cra-index 收敛注与 owner doc 双落点）**：`getFont` 实际频率 = 每文档每字体键一次（PdfRenderer per-document fontCache :62-69 为既有缓解层）；P2 保留依据 = 首次 CJK 回退解析持锁做多次字体文件 I/O（毫秒-百毫秒级/个 × 数百候选）可秒级阻塞全部并发导出线程。

**否决的备选**：独立锁对象方案中的「探测结果落盘缓存」（audit 建议 c 项）不采——重启复用属优化候选，登记 Non-Blocking Follow-up；`AtomicReference` 锁外竞写探测不采——探测串行化防重复加载字体的收益大于竞写收益。

## 裁定④ G5：ExcelFormatHelper 单设计联合闭合（C1-003 守卫 + C9-001 缓存）

**决策**（双半边同 diff，禁分开交付）：

1. **C1-003 半边**：`formats` 换 `ConcurrentHashMap`（:74，删除「不需要线程安全」注释自认）；`addFormat` javadoc 补线程安全声明（:271-273，public API 运行期可并发调用）。
2. **C9-001 半边**：格式工厂缓存 `ConcurrentHashMap<String, ThreadLocal<Format>>`——键 = excel 格式串，值 = `ThreadLocal.withInitial(supplier)`，**supplier 内 `try { createFormat(fmt) } catch (Exception e) { return null }`**——保持 `getFormat` 既有失败契约（异常格式串返回 null 而非新抛：`createFormat` 对对抗性格式串可抛 IllegalArgumentException/IndexOutOfBounds（:127 replaceAll 符号引用），缓存路径必须吞到与现实现 :101-106 catch 同款语义，否则渲染路径行为变更）。**SimpleDateFormat/DecimalFormat 非线程安全，禁裸缓存共享实例**——ThreadLocal 每线程独立实例，语义与现状（每次新建）逐线程等价，零格式行为变更（不做 DateTimeFormatter 迁移，week-year/时区/宽松解析差异面整体消除）。注记：`createFormat` 返回 null 的格式串不构成负缓存（ThreadLocal null 值视同缺席、supplier 每次重跑）——与现状等价，非缺陷，禁再引入负缓存复杂度。并发首次创建安全前提核证：`createFormat` 内 `dateMatcher`/`amPmMatcher`/`m` 均为局部 Matcher（每调用 `pattern.matcher()` 新建），静态字段仅 4 个线程安全的 `Pattern`（:44/:49/:54/:59）——createFormat 本身线程安全，无需额外防护。
3. **有界性**：缓存键数上界 `MAX_CACHED_FORMAT_PATTERNS = 256`；超界后 `getFormat` 对未缓存格式串直接 `createFormat` 不再缓存（无淘汰策略复杂度；格式串基数实践中 = 工作簿样式数，256 上界外仅损失命中不损失正确性）。
4. `getFormat` 查找序：内建 8 格式既有缓存（`formats`，现为 CHM）优先——内建单例 Format 返回形态零回退；未命中走工厂缓存。消费点 `ExpandedSheetEvaluator` :176 / `ExcelWorkbook` :59 调用形态零改。
5. **钉形态**：同格式串二次调用 `assertSame`（ThreadLocal 同线程语义）；内建格式 `assertSame` 零回退；白盒断言 `formats` 为 CHM。

**否决的备选**：DateTimeFormatter 包装不采（转换语义等价断言负担 + `Format.format(Object,toAppendTo,FieldPosition)` 消费方签名不变诉求）；无界 CHM 裸缓存不采（对抗性格式串基数无界）；共享单例 Format 缓存不采（SimpleDateFormat 非线程安全——把性能缺陷换成并发正确性缺陷）。`ExcelDateHelper.isADateFormat`（C9-003 P3 面）零触碰。

## 裁定⑤ C1-005 + C6-001：RAF reader 独立视图与构造安全序（同文件同 diff）

**决策**：

1. **C1-005 detach/duplicate 独立化**：`RandomAccessFileBinaryDataReader` 持有 `File` 引用，`detach()`/`duplicate()` 改为**按文件重建独立 reader**（`new RandomAccessFileBinaryDataReader(file)` + `seek(pos())` 保位），语义对齐家族先例——`ByteBufferBinaryDataReader.detach/duplicate` :473-480（`bb.duplicate()` 保位独立视图）、`BlockCachedBinaryDataReader.detach` :391-415（深拷贝缓存+保位）、`duplicate=detach` 先例 :419-422。
2. **否决 ByteBuffer 一次读入方案**：`detach()` 全文件读入堆与 C5-001 包络目标冲突（10GB 文件 → 10GB 堆）；RAF reader 的设计定位（超大文件 >31bit 定位、海量小文件）恰是 ByteBuffer 方案不可达场景。fd 代价（每次 detach 一个新 fd）：生产直呼 RAF detach/duplicate 当前为零（复核确认），fd 生命周期随子 reader close 独立回收。**watch 登记**：detach 独立化使 fd 代价从零变实，子 reader 的 fd 回收依赖调用方 close——所有权不对称属 P3 C6-R2-001 零触碰域，本批红⑧/红⑨ 不覆盖「子 close 释放 fd」断言，登记 watch-only。
3. **契约 javadoc**：`IBinaryDataReader.detach()`/`duplicate()` 声明独立性契约——返回 reader 与父实例读写位置互不影响、生命周期独立（close 互不连带）。`isDetached()` RAF 实现维持 `false`：其家族语义（ByteBuf 侧 isDetached 语义同化 = C1-R2-001，**P3 零触碰成员**）未定，且 `SubBinaryDataReader.isDetached` 委托 underlying——RAF 报 true 会令其 sub 视图谎报独立。
4. **现网零回归论证**：pos 回补路径（`AbstractModelBasedRecordDeserializer` readObject :105-112 / readCollection fixed :266-295、`StreamingRecordDeserializer` :179-184/:363 附近）全部经 `subInput()`（SubBinaryDataReader 共享指针语义**零改动**），不经 detach/duplicate；RAF detach/duplicate 生产直呼零。红⑧ 交错矩阵钉防「契约 javadoc 写了但实现仍共享」退化。
5. **C6-001 构造安全序**：`raf` 字段去 `final`，构造改**先建后换**——`new RandomAccessFile(file,"r")` 成功后经 `protected long initSize(RandomAccessFile)` 取 `length()`，任一步异常先 close 已打开句柄再 rethrow（`NopException.adapt` 语义保持，不吞）；`initSize` 为受控失败缝（唯一目的：测试注入 `length()` 失败——子类覆写注入，生产零影响）。`close()` 加 `closed` volatile 幂等标志（对齐 `StreamBinaryDataReader.closed` 惯例；JDK close 自带幂等，标志为家族形态统一）。
6. **fd 计数钉形态**：`TestFileDownloadSinkClose` 先例形态复用（`UnixOperatingSystemMXBean` 口径 + 预热/连发/增量断言）+ `@EnabledIf` 平台门控预登记 + 变异 fallback（拆 finally 关闭 → 红 → 恢复 → grep 确认目标行存在）。红⑨ 主形态 = **变异锚定**（天然注入缝缺失——pre-fix 无 initSize 缝不可注入）。

**零触碰边界**：`SubBinaryDataReader` 全部语义零改动；`IBinaryDataReader.subInput` 契约零改动；detach 契约族 P3 成员（C1-R2-001 ByteBuf isDetached 同化 / C5-003 readAvailableBytes / C6-R2-001 顶层 reader 所有权）零触碰。

## 裁定⑥ C5-004：Excel 导入行数上界（2516 位点 SSOT 消费方）

**决策**：

1. **复用既有旋钮** `ExcelIOConfig.maxCountPerSheet`（:15，默认 20_0000）**读侧接线**——读写两侧同名同义：写侧 = 每 sheet 最大**数据行**数（超出换 sheet，`ExcelRecordOutput` :123 既有，headers 独立计数 :130-134）；读侧 = 每 sheet 最大读取**数据行**数（超出**抛错**）。**计数口径 = 数据行**：包装器跳过前 `config.getHeaderRowCount()` 次 write（header 行流经 collector 的次数）后才开始计数——对齐写侧 `sheetDataRowCount` 口径，禁按物理写入行计数（否则读侧有效上界 = N − headerRowCount，与写侧语义漂移）。否决新增读侧专用键（语义重复双键分裂——写侧 20 万/读侧另一键另一默认值的配置面漂移）。`<= 0` = 不设上界（与写侧 `maxCount > 0` 判定对称，维持旧行为逃生口）。
2. **超限语义 = 显式抛错**（fail-fast，非截断）：包装器在 collector 写入点计数（数据行口径），达到 `maxCountPerSheet` 后抛 `NopException(ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT)`（`XptErrors` 新注册，param sheetName + maxCount）。异常传播链已核证：`OutputRowHandler.endRow` :91-95 仅 catch IOException，NopException（RuntimeException）原样穿出；两条 processXml 实现（默认 `IOfficePackagePart` :62 树遍历 / `ResourceOfficePackagePart` :87-90→XNodeParser :239-285 仅 finally 无 catch→`AbstractResourceParser.parseFromResource` :100-103 catch NopException 后原样重抛）均不改写错误码——抛错上行成立。否决「截断+WARN」：物化发生在 `XlsxToRecordOutput.parseSheet` 解析期，截断无法中止解析（CPU 照常耗尽全 sheet）且静默丢行是比显式报错更危险的数据损失形态；抛错同时是唯一能在 collector 层实现**真内存上界**的形态（List 增长在 maxCount+1 行即中止）。行为变更声明：>20 万行导入从「OOM 风险」变为「显式报错」，调大 `maxCountPerSheet` 或改用 csv/jdbc-reader（与 `nop-batch.md` 既有指引一致）。
3. **模块内修复零触碰 nop-core**：包装器为 `ExcelRecordInput.beforeRead` :88 本地创建 collector 的外层 `IRecordOutput` 装饰器（write 跳 header 计数+超限抛错+其余委托；泛型对齐 `HeaderListRecordOutput<R> implements IRecordOutput<List<Object>>`（:20）的既有形态），`HeaderListRecordOutput` 本体与 nop-kernel/nop-core 零触碰（PA 预设维持，条件申报①不触发）。`XlsxToRecordOutput`（nop-ooxml-xlsx）零触碰（分支 (b) 惰性解析未采，条件申报④不触发）。
4. **跨 doc 收敛**：`nop-batch.md` :265-267「全 sheet 物化为已声明包络边界 / 物化面残留为 watch-only 观察项」改写——物化残留被本批收敛（上界默认生效），保留 maxCountExpr 接线历史与行为变更声明。
5. **ExcelResourceIO 透传面**：`ExcelIOConfig` 已是 `ExcelRecordInput` 构造参数（:41-46，:43 缺省 DEFAULT），零新增透传代码——透传面零触碰。
6. **红⑩ fixture 口径**：既有 `excel-input.xlsx` 数据行 ≥10（`TestExcelResourceIO` :53 断言 `get(9)`），不充当「5 行全读」断言——测试内经写侧路径（`ExcelResourceIO.openOutput`）现造 5 数据行资源（与既有 `testWriteMultiSheet` 同构），或按既有 fixture 实际行数设 `maxCountPerSheet` 断言；两种形态均端到端（openInput→beforeRead→超限拒绝）。

## 裁定⑦ C1-001：DocumentConverterManager 内层容器化

**决策**：采**内层并发容器化**（否决不可变 Map volatile 整体替换）：`registerConverter` 的内层 `new HashMap` :154 → `new ConcurrentHashMap`，`fromFileTypeMap` 内层 `new TreeSet` :161 → `new ConcurrentSkipListSet`（保序——`findChainedConverter` :261-269 迭代中间类型的确定性序维持）。`getFromFileTypes`/`getToFileTypes`/`getDirectToFileTypes` 的 `new TreeSet<>(...)` 只读快照拷贝（:49/:77-96）零改动——快照语义本就线程安全。外层 CHM :32/:35 结构不变。

**链式查找自环剪枝**：`findChainedConverter` 跳过与源类型相同的中间类型。并发注册 `from→to` 与链式查找交错时（直连查找未命中后注册完成），`fromFileTypeMap[to]` 会包含 `from` 本身，自环链 `from→from→to` 无意义且会触发 `getDirectConverter` 的 from≠to 守卫异常；单线程下直连查找先命中，该剪枝不改变任何非并发行为。

**否决不可变替换的理由**：每次注册重建双 Map + volatile 发布需原子性跨双字段（两 volatile 无法原子联合发布），引入读偏序新形态；并发容器化是 FontManager/ExcelFormatHelper 同族最小形态。`getFileTypes` 只读面兼容声明：三处快照拷贝行为与返回类型不变。钉 = 白盒内层容器类型断言 + 并发 register/lookup smoke。

## 裁定⑧ 测试缝总表（红①–红⑩ 驱动器 + 零回退钉）

### 红绿判据（先红后绿或变异锚定，E2）

| 钉 | 成员 | 驱动器形态 | 缺陷态红判据 |
|---|---|---|---|
| 红① | C5-001 | 新增含 `lengthExpr` 测试模型（`test-length-expr-bound.record-file.xml`，非 `_` 前缀源模型）+ 数据驱动超大长度（128MB 档断言错误码 / 0x7FFFFF00 档断言分配前拒绝）；经 `ModelBasedResourceRecordIO` 文件路径端到端（模型入口→错误码出口） | **构造性当前即红**：pre-fix 返回 `ERR_RECORD_NO_ENOUGH_DATA`（分配后 EOF）或 OOME，≠ 断言的 `ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG` |
| 红② | C5-R2-001 | rawVarName 功能钉（短内容 hex 全文断言）+ 截断钉（>4096 字符内容断言变量长度 ≤ 上界+标记）；二进制+文本双路径 | 构造性当前即红：pre-fix 变量为全文 hex（长度断言红） |
| 红③ | C1-002 | 并发 `addFontAlias`×N + `getFont` 交错 smoke（多线程 Executor）+ 白盒断言 `fontNameAliases` 为 CHM | 构造性当前即红：pre-fix 白盒断言失败 |
| 红④ | C9-002 | 结构钉：反射断言 `resolveFallbackFont` 方法无 `ACC_SYNCHRONIZED`、`fallbackLock`/`fontScanLock` 字段存在 + `inited` volatile；辅助 = 两线程并发首渲染耗时上界（最小形态） | 构造性当前即红：pre-fix 方法为 synchronized |
| 红⑤ | C1-003 | 白盒断言 `formats` 为 CHM + 运行期 `addFormat` 并发 `getFormat` smoke | 构造性当前即红 |
| 红⑥ | C9-001 | 同格式串二次调用 `assertSame`（ThreadLocal 语义）；内建 8 格式 `assertSame` 零回退对照 | 构造性当前即红：pre-fix 每次新建实例 |
| 红⑦ | C1-001 | 白盒断言内层容器类型（CHM/ConcurrentSkipListSet）+ 并发 registerConverter/getConverter smoke + 链式查找自环剪枝确定性钉（反射调用 findChainedConverter） | 构造性当前即红（自环钉为变异锚定：摘除剪枝→守卫异常） |
| 红⑧ | C1-005 | 父子交错读矩阵：parent 读 n 字节 → child=parent.detach() 读推进 → 断言 parent.pos() 不变；duplicate 同矩阵（`TestDetachReaders` :75 @Disabled 形态参照，本钉**不 @Disabled**） | 构造性当前即红：pre-fix 共享指针 parent 位置被推进 |
| 红⑨ | C6-001 | 子类覆写 `initSize` 抛 IOException → 断言构造抛出且 fd 计数增量 = 0（`UnixOperatingSystemMXBean` 口径 + `@EnabledIf` unix 门控 + close 幂等断言） | **变异锚定主形态**（pre-fix 无注入缝）：fix 后拆 finally 关闭 → 红 → 恢复 → grep 确认恢复行存在 |
| 红⑩ | C5-004 | `ExcelIOConfig.maxCountPerSheet(3)` + 5 行 xlsx → `ExcelResourceIO.openInput`→`beforeRead` 端到端断言抛 `ERR_XPT_EXCEL_READ_ROW_COUNT_EXCEED_LIMIT`；`maxCountPerSheet(0)` 断言不设上界（5 行全读） | 构造性当前即红：pre-fix 无上界 5 行全读不抛 |

变异探针纪律：恢复路径以 grep/断言确认目标可执行行真实存在（plan guide When Executing #8）。红绿快照落 `_tmp/red-green-2527-summary.md` + 逐 trial 日志。

### 零回退钉清单

- 双聚合器基线 736 tests 0F0E（`_tmp/baseline-2527-decomposed.log`，勘误 3 逐模块计数）；7 Skipped 已知面签名维持（@Disabled 三件套 + report-demo 2 + 单例 skip），Skipped 数变化须登记归因。
- 点名既有钉族零回退：`TestRecordRegionBoundary`（EOF 面 :125/:135，与分配面正交）、`TestSubBinaryDataReader`、`TestDetachReaders` 既有用例（新钉不并入该 @Disabled 类）、`TestFontManager`、`TestPdfFontResolution`、`TestPdfRenderDefects`、`TestExcelFormatHelper`、`TestDocumentConverterManager`、`TestModelBasedPacketCodec`、`TestExcelResourceIO`（:154 maxCountPerSheet(3) 写侧多 sheet 用例零回退——读侧抛错语义不影响写侧）、`TestHtmlReportRenderer`、`TestPdfExtractHelper`。
- 同族保护三处零回退：`CountArrayBinaryCodec.checkCount` / `DynLVFieldBinaryCodec` / `LVFieldBinaryCodec`。
- nop-batch 侧 maxCountExpr 接线零回退（plan 2516 产物，`FileBatchSupport.newExcelReader` :150-155）。

### C10 缺保护 10 条同 diff 映射

红①↔C5-001、红②↔C5-R2-001、红③↔C1-002、红④↔C9-002、红⑤↔C1-003、红⑥↔C9-001、红⑦↔C1-001、红⑧↔C1-005、红⑨↔C6-001、红⑩↔C5-004——每条与本体同 diff 禁拆分交付。测试命名 `Test*_<工单>` 惯例（`C5_001`/`C5_R2_001`/`C1_002`/`C9_002`/`C1_003`/`C9_001`/`C1_001`/`C1_005`/`C6_001`/`C5_004`）。

## 评审记录

- 设计评审 R1：独立子 agent agent_353b9660（fresh session，2026-11-02，@ e676323330）——**无 Blocker，2 Major + 4 Minor 全部随评审修订闭环**：Major-1 裁定④ ThreadLocal supplier 补 `catch→null` 契约（否则 createFormat 对抗性格式串异常穿透 getFormat，违背零行为变更声明）；Major-2 裁定⑥ 读侧计数口径钉死为数据行（跳过前 headerRowCount 次写，对齐写侧 sheetDataRowCount）；Minor×4：unencodableChars 行号 ：68→:64、createFormat null 非负缓存注记、detach fd 回收 watch 登记、裁定⑥ 行号锚（ExcelRecordOutput :123 / ExcelRecordInput 构造器 :41-46）与红⑩ fixture 口径（写侧现造 5 行资源）。四个特别关注项核证：createFormat 内 Matcher 全局部变量（静态仅 4 个线程安全 Pattern）；processXml 对 NopException 逐层透传且错误码保持；SubBinaryDataReader.close 为 no-op（无 close 时序隐患）；decodeString 无旁路调用方（binary 全仓唯一调用点 :100 在 getFieldLength 之后）。**结论：设计可直接进入 Phase 2 实现。**
