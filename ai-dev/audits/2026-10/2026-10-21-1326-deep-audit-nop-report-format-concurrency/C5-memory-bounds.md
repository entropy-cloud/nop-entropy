# C5 内存有界性 — nop-report + nop-format 初审

> 审计单元：CRA WI 5.4 nop-report-format（19 源码根，894f/102,861 行）。
> 维度：C5 内存有界性（大文件流式 vs 物化）。R1 锚点：全量物化 30 命中，record reader 族为主。
> 盲审纪律同 C1。规模口径：本单元无 owner doc 声明的规模包络（dispatch-log 已登记 drift），因此按"格式处理模块处理用户提供的输入文件"这一事实包络评估，超出流式设计意图（BlockCached 滚动窗口等自证目标）的物化按包络内缺陷计。

## 无界风险清单（路径 × 增长源 × 判定）

| 路径 | 增长源 | 上界/机制 | 判定 |
|------|--------|----------|------|
| ModelBasedBinaryRecordDeserializer.decodeString | 数据驱动 length → new byte[length] | 无 | **C5-001 P1** |
| IBinaryDataReader.readBytesTerm（default + Stream 覆写） | 终止符缺失时逐字节累积 | 无 | **C5-002 P2** |
| readAvailableBytes / StreamBinaryDataReader.detach / RandomAccess.subInput | 剩余流全量物化 | 无 | **C5-003 P2** |
| ExcelRecordInput.beforeRead | Excel 全表 → List<Map> | 无 | **C5-004 P2** |
| PdfDoc.getAllText / ResourceDocumentParser.open(非文件资源) | 全文本/全字节累积 | maxSizePerPage 可选 | C5-005 P3 |
| ChainedDocumentConverter.convertToStream | 中间结果 byte[] 全量 | 转换语义固有 | C5-006 P3 |
| BlockCachedBinary/TextDataReader 滚动窗口 | — | maxCacheBlocks×blockSize 有界 | 安全（对照组） |
| CountArrayBinaryCodec/DynCountArrayBinaryCodec | 数据驱动 count | checkCount ≤ 1,000,000 | 安全（对照组） |
| ModelBasedPacketCodec（netty 帧） | 帧长 | maxFrameLength 校验 | 安全（对照组） |
| WordTemplate.loadInMemory / OfficePackage | 模板全量内存 | 模板文件自身大小 | 声明式设计（模板可缓存），不报 |
| PdfRenderer.imageCache/fontCache | 文档内唯一图片/字体数 | 单文档边界 | 安全 |
| ImportExcelParser.cache | dict 缓存 | LocalCache newConfig(100) 有界 | 安全 |
| SimpleHtmlSplitter.split | XNode 全量 DOM | 报告 HTML 大小 | P3（离线工具，并入 C5-005 同族登记） |

---

### [C5-001] 数据驱动字段长度无上限直接分配 new byte[length]（record 二进制反序列化主路径）

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/serialization/ModelBasedBinaryRecordDeserializer.java:89-123`；长度来源 `AbstractModelBasedRecordDeserializer.java:208-218`；分配点 `IBinaryDataReader.java:384-392`
- **证据片段**:
  ```java
  // AbstractModelBasedRecordDeserializer.getFieldLength：长度可来自数据表达式
  protected int getFieldLength(Input in, RecordSimpleFieldMeta field, ...) {
      int length = field.getLength();
      if (field.getLengthExpr() != null) {
          Object lengthValue = field.getLengthExpr().call3(null, in, record, context, ...);
          length = ConvertHelper.toPrimitiveInt(lengthValue, field.getLength(), ...);  // 无上限校验
      }
      return length;
  }
  // ModelBasedBinaryRecordDeserializer.decodeString
  String decodeString(IBinaryDataReader in, Charset charset, int length) throws IOException {
      byte[] bytes = in.readBytes(length);      // readBytes: new byte[n] 分配先于读取
  ```
- **严重程度**: P1
- **并发场景**: 单线程内即可触发，无需并发；长度前缀协议（lengthExpr 从已读数据字段取长度）解析网络帧/文件记录时，一个损坏或恶意长度值即触发。与并发维度交叉：多连接共享堆时单请求的大分配直接挤占其他请求（nop-record-netty 的 ModelBasedPacketCodec → RpcMessageHandler 路径）。
- **触发时序**: 记录模型定义 `lengthExpr="valueField"` → 输入流中 length 字段被写为 0x7FFFFF00 → getFieldLength 返回 ~2GB → `decodeString` → `IBinaryDataReader.readBytes(n)` 第一行 `byte[] bytes = new byte[n]` 立即分配（先于任何流可用性检查）→ OOM（或堆碎片化拖垮同进程其他请求）。ByteBufBinaryDataReader（netty 帧，帧本身仅 KB 级）同样先分配后读取，损坏长度绕过帧界。
- **现状**: 同模块的 `CountArrayBinaryCodec.checkCount` 对数据驱动 count 有 1,000,000 上限（RecordConstants.DEFAULT_MAX_COLLECTION_SIZE），`ModelBasedPacketCodec` 对帧长有 maxFrameLength 校验——**同族长度值仅字段长度无界**，属保护不一致。
- **风险**: 声明包络内（正常解析用户上传文件/网络报文）一个坏长度字段 → 请求线程 OOM/长 GC；netty 场景可被单包远程触发。
- **建议**: 在 `getFieldLength`/`getObjectLength` 返回前校验 `length <= RecordConstants.DEFAULT_MAX_FIELD_LENGTH`（新增常量，建议 1MB 量级或按模型可配置），超限抛 ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG（复用现有错误码语义）；`readBytes` 内亦可增加保守上限参数。对照平台 HighWatermarkSemaphore 思路，本质是输入信任边界校验。
- **失效面枚举**: 同一"数据驱动长度→分配"族全清单：decodeString（L117，违规）；getRawDataString→peekByteString(length)→readBytes（L52-54，违规）；readString（L80，长度=期望字符串字节长，安全）；CountArrayBinaryCodec/DynCountArrayBinaryCodec（checkCount 兜底，安全）；FixedLengthStringCodec（模型声明长度，安全）；文本路径 ReaderTextDataReader.readString（模型长度，安全）；getObjectLength→subInput（惰性包装，间接经上述分配点暴露）。
- **信心水平**: 确定
- **误报排除**: 非有界声明缓存豁免——长度来自不可信输入字节而非模型配置；分配先于读取校验（`bytes.length < length` 检查在分配之后）使"读取时自然报错"的辩护不成立。
- **复核状态**: 已复核（保留 P1；生产可达性与三处同族保护补强见「维度复核结论」表）

---

### [C5-002] readBytesTerm 无最大长度限制：终止符缺失时剩余流全量累积

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/IBinaryDataReader.java:418-438`；`StreamBinaryDataReader.java:169-207`（同构覆写）；`ByteBufferBinaryDataReader.java:359-368`、`ByteBufBinaryDataReader.java:359-368`（同族）
- **证据片段**:
  ```java
  default byte[] readBytesTerm(byte term, boolean includeTerm, boolean consumeTerm, boolean eosError) throws IOException {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      while (true) {
          int c = read();
          if (c < 0) {
              if (eosError) { throw new NopException(...); }
              else { return buf.toByteArray(); }   // eosError=false：EOF 前全部字节返回
          } else if ((byte) c == term) { ... return buf.toByteArray(); }
          buf.write(c);                            // 无长度上限
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 解析以终止符分隔的字段（`EOLFieldCodec` 族）时；单线程触发；多 worker 并发解析时单个坏文件可放大为整体堆压力。
- **触发时序**: 模型字段以 `term='\n'` 分隔且模型配置 eosError=false（容错到 EOF）→ 输入文件被截断/损坏丢失终止符 → 循环把剩余全部字节（GB 级文件的剩余部分）写入 ByteArrayOutputStream → toByteArray 再复制一份（峰值 2×）→ OOM。eosError=true 时报错终止，安全。
- **现状**: 对照组 readBytes 有模型长度、readFully 有 EOF 校验，唯 readBytesTerm 循环无界；接口契约（Kaitai 拷贝源）即为无界。
- **风险**: 与 C5-001 同为"单输入挤占全堆"；截断文件是解析器必须容忍的常态输入而非罕见恶意输入。
- **建议**: readBytesTerm 增加 maxLength 参数（默认取当前 region 上限），超限抛 ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG；调用面（EOLFieldCodec、RecordMetaHelper）按模型字段 maxLength 传入。
- **失效面枚举**: 同族实现逐一核对：IBinaryDataReader 默认（违规）；StreamBinaryDataReader 覆写（违规，同构）；ByteBufferBinaryDataReader.readBytesTerm（缓冲区边界自然封顶，安全）；ByteBufBinaryDataReader（ByteBuf 边界封顶，安全）；ReaderTextDataReader/BlockCached 族经窗口封顶（安全）。调用方：EOLFieldCodec（主调用，暴露点）。
- **信心水平**: 确定（结构确定；触发需 eosError=false 或巨大剩余流的组合）
- **误报排除**: 有界读者（ByteBuffer/ByteBuf）自然封顶不计；仅报无界累积的两个实现。
- **复核状态**: 已复核（降级 P2→P3；主调用方 EOLFieldCodec 证伪，全 repo 零生产调用方，见「维度复核结论」表）

---

### [C5-003] readAvailableBytes 与 StreamBinaryDataReader.detach 剩余流全量物化

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/reader/IBinaryDataReader.java:407-416`；`StreamBinaryDataReader.java:256-272`；`RandomAccessFileBinaryDataReader.java:146-147`（subInput 为惰性视图，见 C1-005）
- **证据片段**:
  ```java
  default byte[] readAvailableBytes() throws IOException {
      byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int readCount;
      while (-1 != (readCount = read(buffer)))
          baos.write(buffer, 0, readCount);      // 剩余全部字节
      return baos.toByteArray();                 // 峰值 2×
  }
  // StreamBinaryDataReader.detach
  public IBinaryDataReader detach() throws IOException {
      ...
      byte[] remaining = IoHelper.readBytes(inputStream);   // 剩余流全量入内存
      return new ByteBufferBinaryDataReader(allData);
  }
  ```
- **严重程度**: P2
- **并发场景**: 流式解析中触发 detach（区域需要随机访问而底层不可 seek，如 `BlockCached*.detach` 委托链、`SubBinaryDataReader.detach`）；GB 级输入流时单次 detach = 全量物化。
- **触发时序**: 解析含嵌套区域的大文件（如流式读取多 GB 日志包）→ 某字段触发 detach（原始数据保存 rawVarName 或嵌套 subInput 需要 seek）→ IoHelper.readBytes 把剩余 GB 级字节读入堆 → OOM。与 C5-001 的差异：本条是**结构性全量物化**，不需要损坏输入。
- **现状**: detach 的"物化剩余"对 Stream 实现是语义必需（此后需要随机访问），但无大小保护；readAvailableBytes 语义即"读全部剩余"，调用面少（ByteBuffer/ByteBuf 实现为有界覆写）。
- **风险**: 大文件流式解析（record 模块的声明能力，BlockCached 窗口设计即为此目标）被 detach 路径击穿——有界窗口 + 无界 detach 并存，包络一致性破缺。
- **建议**: detach 增加物化上限（超过阈值改用临时文件后备 RandomAccessFileBinaryDataReader——平台已有 LocalFileLock/临时资源设施）；readAvailableBytes 调用点逐一加长度断言。
- **失效面枚举**: 全量物化族：IBinaryDataReader.readAvailableBytes 默认（违规）；StreamBinaryDataReader.detach（违规）；BlockCachedBinaryDataReader.detach→underlying.detach（传导违规）；SubBinaryDataReader.detach→underlying.detach（传导）；ByteBufferBinaryDataReader.readAvailableBytes（有界，安全）；ByteBufBinaryDataReader.readAvailableBytes（有界，安全）。
- **信心水平**: 确定
- **误报排除**: 非"声明的大数据量路径外"——BlockCached 窗口的存在证明流式大文件是声明包络；非池化/缓存误报。
- **复核状态**: 已复核（降级 P2→P3；反序列化主流程无 detach/readAvailableBytes 生产触发点，见「维度复核结论」表）

---

### [C5-004] ExcelRecordInput 名为流式接口实为全量物化：整表 List<Map<String,Object>> 驻留

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/record/ExcelRecordInput.java:49-52,97-120`
- **证据片段**:
  ```java
  private List<Map<String, Object>> list;      // 全表驻留字段
  @Override
  public void beforeRead(Map<String, Object> map) {
      HeaderListRecordOutput<Map<String, Object>> collector = new HeaderListRecordOutput<>(...);
      ...
      this.output.parseSheet(sheetName);
      list = collector.getResult();            // 一次性收齐所有行
  }
  @Override
  public T next() { ... return buildResult(list.get(nextIndex)); }   // 仅索引迭代
  ```
- **严重程度**: P2
- **并发场景**: Excel 导入（IRecordInput 语义上支持大文件分批消费）；单次导入线程内全量物化，多个并发导入请求堆占用线性叠加（并发数 × 全表物化体积）。
- **触发时序**: 用户上传 100 万行 xlsx → beforeRead 经 XlsxToRecordOutput 把全部行收集为 List<Map>（每行一个 HashMap + 装箱值，放大倍数约 5-10×于原始字节）→ 内存峰值远超文件本身 → 与其他并发导入叠加 → OOM。
- **现状**: 接口为 IRecordInput（hasNext/next 流式契约），同包 ExcelRecordOutput 写侧是真正的流式（临时文件逐 sheet 写出，见 ExcelRecordOutput.closeDataSheetWriter），读写两侧不对称；行级物化无 page/批上限配置。
- **风险**: 导入大 Excel 是 C5 锚点明示的"Excel 大文件流式 vs 物化"主战场；现状使导入侧规模上限 ≈ 堆大小/放大倍数，长尾请求可拖垮服务。
- **建议**: HeaderListRecordOutput 收集器改为分批回调（复用平台 IBlockingConsumer/批缓冲模式），或 ExcelRecordInput 增加内部批队列（容量 N 行）懒解析；至少为行数加配置上限（对照 ExcelConfigs 既有 CFG_EXCEL_MAX_SHEET_NAME_LENGTH 的配置化惯例）。
- **失效面枚举**: 同族导入路径：ImportExcelParser/ImportDataCollector（对象导入，逐 sheet 构建，物化为导入语义本身，不报）；XlsxToRecordOutput（解析层，流式回调 SheetContentsHandler，安全）；HeaderListRecordOutput（收集器，违规驻留点）；ExcelRecordOutput（导出侧流式，安全对照组）。
- **信心水平**: 确定
- **误报排除**: 非"导入语义本身需要全量"——IRecordInput 接口契约即流式迭代；非离线工具（服务端请求路径）。
- **复核状态**: 已复核（保留 P2；行号更正 :36/:87-98/:100-114，nop-batch 可达性与 1.7 同族先例见「维度复核结论」表）

---

### [C5-005] PdfDoc.getAllText 与非文件资源 PDF 加载的全量累积（文档文本/字节无界 StringBuilder）

- **文件**: `nop-format/nop-pdf/src/main/java/io/nop/pdf/core/PdfDoc.java:66-102,194-210`；`ResourceDocumentParser.java:194-210`
- **证据片段**:
  ```java
  public String getAllText(String beginPattern, String endPattern, int maxSizePerPage) {
      ...
      StringBuilder sb = new StringBuilder();       // 全文档文本累积
      for (int i = 1; i <= totalPages; i++) {
          String text = getPageText(i);
          if (maxSizePerPage > 0) { sb.append(StringHelper.limitLen(text, maxSizePerPage)); }
          else { sb.append(text); }                  // maxSizePerPage=-1 时无界
  ```
  ```java
  // ResourceDocumentParser.open：非文件资源整体读入
  InputStream is = mPDFFile.getInputStream();
  try { byte[] bytes = IoHelper.readBytes(is);   // PDF 全字节入堆
  ```
- **严重程度**: P3
- **并发场景**: 文本抽取/文档问答预处理路径单线程调用；多请求并发抽取大 PDF 时叠加。
- **触发时序**: 数千页扫描型 PDF + `getAllText()` 默认 maxSizePerPage=-1 → 每页文本（可达数十 KB）×N 页累积进 StringBuilder → 单字符串驻留数十至数百 MB → 并发 N 个请求线性叠加。ResourceDocumentParser 对 classpath/VFS 资源（用户上传经内存资源存储时）全字节读入后再交给 PDFBox（file 路径有 MemoryUsageSetting.setupMixed(memoryRestrictSize) 保护，非文件路径绕过该保护）。
- **现状**: maxSizePerPage 参数提供了可选界（默认 -1 无界）；PDFBox 自身 streamCache 机制只在 file 加载路径生效。
- **风险**: 文本抽取是长耗时操作，并发叠加下堆峰值高；属包络内可量化的物化浪费而非必然 OOM，故 P3。
- **建议**: getAllText 默认启用 per-page 上限（如 64KB）；非文件资源路径同样传入 MemoryUsageSetting（PDFBox 支持从 InputStream 加载 + streamCache）。
- **失效面枚举**: 同族累积点：PdfDoc.getAllText（两处重载）；PdfDoc.getAllTablesHtml（StringBuilder 逐页累积 HTML，同构）；ResourceDocumentParser.open 非文件分支；ResourceDocumentParser.exportAllTextToFile（先有 mAllTextContent 全量驻留——字段级同族，同修）。file 加载路径 + memoryRestrict 配置（安全对照组）。
- **信心水平**: 很可能
- **误报排除**: 文本抽取工具的输出本身即全量文本，不是"可流化而不流化"的误报场景；报告为参数默认值缺陷而非路径本身。
- **复核状态**: 已复核（保留 P3；事实修正：非文件分支同样传 settings.streamCache（ResourceDocumentParser.java:204-205），缺陷在加载前整体 byte[] 物化 :203，见「维度复核结论」表）

---

### [C5-006] ChainedDocumentConverter 链式转换中间结果双份全量物化

- **文件**: `nop-format/nop-converter/src/main/java/io/nop/converter/impl/ChainedDocumentConverter.java:30-66`
- **证据片段**:
  ```java
  public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, ...) throws IOException {
      try (ByteArrayOutputStream intermediateOut = new ByteArrayOutputStream()) {
          firstConverter.convertToStream(doc, intermediateType, intermediateOut, options);
          byte[] intermediateBytes = intermediateOut.toByteArray();   // 中间结果全量（+toByteArray 复制）
          IResource intermediateResource = new ByteArrayResource(path, intermediateBytes, -1L);
          IDocumentObject intermediateDoc = docObjBuilder.buildFromResource(intermediateType, intermediateResource);
          secondConverter.convertToStream(intermediateDoc, toFileType, out, options);
  ```
- **严重程度**: P3
- **并发场景**: 链式转换（A→B→C）请求路径；大文档（如大 xlsx→json5→json）时中间产物常驻堆。
- **触发时序**: 大型 DSL 模型经两跳转换 → 第一跳输出全部字节驻留 ByteArrayOutputStream + toByteArray 又复制一份（峰值 2×中间产物）+ buildFromResource 解析为内存对象（第 3 份）→ 大文档多请求并发叠加。
- **现状**: 中间产物物化是链式转换的简单实现；convertToText 路径同理（String intermediateResult）。
- **风险**: 中间产物可达源文件的数倍（文本序列化放大），并发下叠加；可量化收益中等，P3 性能/规模候选。
- **建议**: 中间结果落临时文件（复用 ResourceHelper.getTempResource + try-finally delete 模式，与 AbstractOfficeTemplate.generateToStream 同款），或流式管化（pipe）。
- **失效面枚举**: 同族：convertToText（String 路径，同构）；直接转换器 SameTypeDocumentConverter/单跳转换器（无中间物化，安全）。
- **信心水平**: 确定
- **误报排除**: 保留物化是简单正确实现，按 C9/C5 的"性能优化候选"口径报 P3，不升级。
- **复核状态**: 已复核（保留 P3）

---

## 维度复核结论

> 复核人：CRA WI 5.4 C5 维度独立复核子代理。方法：逐条重读 8 条发现（C5-001~006 + C5-R2-001/R2-002）引用的源码文件与行号核实证据与物化/无界语义，并对每条做全库调用方可达性核查（grep 全 repo src/main，含 nop-record-netty/nop-batch 交叉面）。复核日期：2026-10-22。本节为追加内容，不修改既有初审/round2 结论。

| 编号 | 判定 | 理由摘要（file:line 复核证据） | 现状标注 |
|------|------|------------------------------|---------|
| C5-001 | 保留 P1 | 证据全部属实：getFieldLength 数据驱动 length 无上限（AbstractModelBasedRecordDeserializer.java:208-218）、getObjectLength 同构（:220-230）；decodeString 分配先于校验（ModelBasedBinaryRecordDeserializer.java:116-123，`bytes.length < length` 检查在 :118 于分配之后）；分配点 readBytes 首行 `new byte[n]`（IBinaryDataReader.java:384-392）；getRawDataString→peekByteString→readBytes 同分配点（ModelBasedBinaryRecordDeserializer.java:52-54）；readString 安全（:80，长度=期望串字节长）。生产可达性核实：文件路径 ModelBasedResourceRecordIO.java:94-95（←nop-batch FileBatchSupport）经 BlockCachedBinaryDataReader（未覆写 readBytes(int)，走接口默认分配点）；netty 路径 ModelBasedPacketCodec.decodeFromBuf:167-179→ByteBufBinaryDataReader.readBytes:328-332 同样 `new byte[n]` 先于 `bb.readBytes(buf)`，帧界内损坏长度照样先分配。现状比初审更强：同族保护实为三处——CountArrayBinaryCodec.checkCount:57-61（≤1,000,000）、DynLVFieldBinaryCodec:34+60-65（MAX_UNBOUNDED_READ_LEN=64MB）、LVFieldBinaryCodec:37-42（相对字段长度），唯默认 decodeString 路径无任何上限，保护不一致成立；ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG 已存在（RecordErrors.java:71），修复建议可直接复用 | 协议面为内部 netty TCP RPC（nop-record-netty ModelBasedTcpServer/Client:59-64 → nop-network/nop-netty RpcMessageHandler）+ 批量文件解析（nop-batch FileBatchSupport）；「受信内网」是部署惯例而非代码保证：帧长校验为可选配置（_PacketCodecModel.java:80 默认 maxFrameLength=0，ModelBasedPacketCodec.java:115 仅 `maxFrameLength > 0` 时生效），字段长度在两条生产路径上均无默认上限 |
| C5-002 | 降级 P2→P3 | 无界实现属实：IBinaryDataReader.java:418-438 默认实现、StreamBinaryDataReader.java:169-207 覆写同构（eosError=false 时 EOF 前全量累积）；ByteBufferBinaryDataReader.java:402-421、ByteBufBinaryDataReader.java:352-371 缓冲边界封顶（安全）与失效面枚举一致。**但可达性证伪初审关键前提**：全 repo grep（src/main+src/test）显示 readBytesTerm 生产调用方为零，初审所称「主调用 EOLFieldCodec（暴露点）」不成立——EOLFieldCodec.java 全文核实仅单字节读 '\r'/'\n' 校验（decode :39-50/:66-77），从不调用 readBytesTerm 也无累积；RecordMetaHelper 同样无调用。唯一调用方为测试（TestDetachReaders 等） | 潜伏 API 缺陷：接口默认实现与 Stream 覆写均为无界累积，但当前无任何生产消费方；风险在未来调用方以 eosError=false 解析大流时才激活。建议修复（maxLength 参数）随 C5-001 同族处理，不独立立项 |
| C5-003 | 降级 P2→P3 | 全量物化实现属实：readAvailableBytes 默认实现无界累积（IBinaryDataReader.java:407-416，峰值 2×）；StreamBinaryDataReader.detach:256-272 `IoHelper.readBytes(inputStream)` 剩余流全量入堆；传导链核实 BlockCachedBinaryDataReader.java:392、SubBinaryDataReader.java:139；有界覆写 ByteBufferBinaryDataReader.java:395-398、ByteBufBinaryDataReader.java:347-349。**但触发点证伪**：反序列化主流程（serialization/codec/util/model 全目录 grep）无任何 detach()/readAvailableBytes() 生产调用——解析区域经 subInput 惰性包装（AbstractModelBasedRecordDeserializer.java:77、StreamingRecordDeserializer.java:97→SubBinaryDataReader）不物化；detach 生产调用方仅 reader 族内部委托与测试（TestDetachReaders 等） | 潜伏结构性缺陷：物化语义真实存在且与 BlockCached 有界窗口设计相悖（包络一致性破缺的判断成立），但当前生产流不含触发点；StreamBinaryDataReader detach 语义必需（detach 后需随机访问）——建议修复为阈值后备临时文件，登记为 API 契约加固项 |
| C5-004 | 保留 P2 | 证据属实（行号更正：list 字段实际 :36，beforeRead 全量物化 :87-98（collector.getResult()=:97），索引迭代 :100-114；原引 49-52/97-120 偏移，不影响实质）：HeaderListRecordOutput.records 为无界 ArrayList（nop-core HeaderListRecordOutput.java:22，write→doWriteRecord :67-104 逐行 add 无上限）；IRecordInput hasNext/next 流式契约（:101-114）与全表驻留不对称核实无误。生产可达：ExcelResourceIO.java:58 ← nop-batch FileBatchSupport/BizExportTaskBuilder/ExportDbTool（批量导入导出主路径） | 同族先例：cra-index 1.7 nop-batch「excel-reader beforeRead 全 sheet 物化 List：50 万行 1.5–3GB」已立案——本条为同一缺陷族在 nop-report-core 侧的第二实例，修复应联动（收集器改分批回调或行数配置上限） |
| C5-005 | 保留 P3（带事实修正） | PdfDoc.getAllText 证据属实：默认重载 maxSizePerPage=-1（PdfDoc.java:62-64），无界分支 :86-87，全文档 StringBuilder :71-101；getAllTablesHtml 同构 :166-176。ResourceDocumentParser.open 非文件分支 `IoHelper.readBytes(is)` 全字节物化属实（ResourceDocumentParser.java:203，分支 :198-212）；file 路径 MemoryUsageSetting.setupMixed 保护 :186-197 属实；mAllTextContent 字级驻留 :415 属实。**事实修正一处**：「PDFBox streamCache 只在 file 路径生效/非文件路径绕过该保护」不准确——非文件分支同样传入 settings.streamCache（:204-205），真实缺陷是加载前必须整体 byte[] 物化（:203），streamCache 无法避免这一次性驻留 | 库 API 暴露面：PdfDoc.getAllText/getAllTablesHtml 在本 repo 内仅测试调用（TestPdfDoc.java:18,24、TestPdfExtractHelper.java:21），生产消费方为下游嵌入方；ResourceDocumentParser 经 PdfExtractHelper/ResourceDocumentTool 内部消费。与 P3 登记级相符 |
| C5-006 | 保留 P3 | 证据逐行属实：convertToStream 中间结果 ByteArrayOutputStream→toByteArray 复制→ByteArrayResource→buildFromResource 三重驻留（ChainedDocumentConverter.java:44-56，原引 30-66 覆盖范围正确）；convertToText String 路径同构（:31-39） | 转换语义固有物化，性能/规模候选口径维持 P3 |
| C5-R2-001 | 保留 P2 | 证据全部属实：getRawDataString peek+hex 双倍放大（ModelBasedBinaryRecordDeserializer.java:52-54）；非流式驻留 context（AbstractModelBasedRecordDeserializer.java:82-85，原引 80-85 正确）；流式 STAGE_INIT 驻留（StreamingRecordDeserializer.java:101-105，原引 100-106 正确）；文本路径 substring 1× 放大（ModelBasedTextRecordDeserializer.java:49-51）。放大机制与 C5-001 正交的判断成立：rawVarName 启用时即使长度合法也额外驻留 4-5× 区域大小至请求结束 | opt-in 诊断特性（模型须声明 rawVarName），两条生产解码路径（文件/netty）可达；与 C5-001 同修最经济（同一 getFieldLength/截断点） |
| C5-R2-002 | 保留 P3 | 证据属实（行号微偏不影响实质）：PptxToMarkdownConverter.convertToPlainText 全文本 StringBuilder（实际 :466-494，原引 462-492）；DocxToMarkdownConverter.convertFromResource XNode DOM+StringBuilder（实际 :30-48+:59-70，原引 35-60）；MarkdownNormalizer.normalizeResource readText→normalizeText→writeText（实际 :66-70，原引 71-88 落在 normalizeDir）。「超出声明包络降 P3 登记」口径自洽 | 登记级：单文档物化后即释放、无跨请求驻留；OOM 需 GB 级文本输入，超出文档转换器声明场景 |

维度集外线索核对：复核中证实的 DynLVFieldBinaryCodec MAX_UNBOUNDED_READ_LEN=64MB 上限（DynLVFieldBinaryCodec.java:34）属 C5-001「保护不一致」论证内的同族事实，已并入其现状标注，不另立编号；ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG 已存在（RecordErrors.java:71）并入修复建议；无其他维度集外新线索需登记。

批次覆盖核验结论：批A excel（清单表行 ImportExcelParser.cache/行22——nop-excel/imp/ImportExcelParser.java 存在核实；SimpleHtmlSplitter 行23——nop-excel/renderer）、批B ooxml（行20 WordTemplate/OfficePackage——nop-ooxml-docx WordTemplate.java；R2 表 ooxml-xlsx 解析层行；R2-002 ooxml-markdown 转换器）、批C pdf+record（C5-005+R2 表 PdfSheetRenderer/tabula/ExtractPageDrawer 三行；C5-001/002/003/R2-001+R2 表 writer 族/RecordFileMeta 两行）、批D report+converter+markdown-ext（C5-004 report-core、行21 PdfRenderer report-pdf、C5-006 converter、R2-002 markdown-ext）均有实际核查痕迹；**覆盖缺口：nop-mermaid（39f）、nop-svg（12f）、nop-chart-export（29f）、nop-tablesaw（9f）、nop-office-model（17f）、nop-office-doc-model（17f）、nop-record-netty（9f，io.nop.netty.ext.* 本体——C5-001/清单表 L19 提及的 ModelBasedPacketCodec 属 nop-record、RpcMessageHandler 属 nop-network/nop-netty，均非该源码根）七个源码根在两份 C5 文档中零提及**；其中 tablesaw/office-model/office-doc-model/record-netty 四个与 C1 复核登记的缺口完全重合（同一缺口，本条确认在 C5 同样成立），mermaid/svg/chart-export 为 C5 维度额外缺口（C1 批D 曾覆盖此三者）——本轮不补审，仅登记，供 C10 汇总裁定是否并入后续轮次。

## 最终保留项

| 编号 | 严重度 | 一句话结论 |
|------|--------|-----------|
| C5-001 | P1 | record 二进制反序列化默认路径数据驱动字段长度无上限直接 new byte[length]（分配先于校验），文件/netty 两条生产路径可达，同族 CountArray(1M)/DynLV(64MB)/LV(相对) 三处保护反衬唯此无界 |
| C5-002 | P3 | readBytesTerm 默认实现与 Stream 覆写终止符缺失时全流累积无上限——无界实现属实但全 repo 零生产调用方（EOLFieldCodec 证伪），降级为潜伏 API 缺陷 |
| C5-003 | P3 | readAvailableBytes/detach 剩余流全量物化与 BlockCached 有界窗口设计相悖——物化语义属实但反序列化主流程无 detach 触发点，降级为潜伏结构性缺陷 |
| C5-004 | P2 | ExcelRecordInput 流式接口全表物化 List<Map>（HeaderListRecordOutput 无界 ArrayList），批量导入主路径可达，与 cra-index 1.7 nop-batch 同族先例联动修复 |
| C5-005 | P3 | PdfDoc.getAllText 默认 maxSizePerPage=-1 全文档文本累积 + 非文件资源 PDF 整体 byte[] 物化（修正：streamCache 两分支均传入，缺陷在加载前一次性驻留） |
| C5-006 | P3 | ChainedDocumentConverter 链式转换中间结果三重全量驻留（BAOS+toByteArray+内存文档对象），性能/规模候选 |
| C5-R2-001 | P2 | rawVarName 诊断路径 peek+hex 4-5× 放大且驻留 context 至请求结束，与 C5-001 同修最经济 |
| C5-R2-002 | P3 | markdown/ooxml 文本转换器全量 StringBuilder 物化，超出声明包络按登记级保留 |

最终保留合计：8 条（P1×1，P2×2，P3×5）；驳回 0、降级 2（C5-002、C5-003 均 P2→P3，理由：无界实现属实但生产调用方为零）；覆盖缺口登记 7 源码根（4 个与 C1 重合）。
