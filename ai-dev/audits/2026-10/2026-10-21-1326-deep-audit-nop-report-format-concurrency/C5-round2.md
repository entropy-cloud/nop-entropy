# C5 深挖第 2 轮 — nop-report + nop-format

> 方法：不与初审 C5-001~006 重复，扩大到 rawVarName 诊断路径、markdown 转换器、PDF 分页渲染、ooxml 解析层、tabula 抽取层的物化面。

### [C5-R2-001] rawVarName 原始数据保存路径对区域做 peek+hex 双倍放大，叠加 C5-001 长度无界

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/serialization/ModelBasedBinaryRecordDeserializer.java:51-54`；`AbstractModelBasedRecordDeserializer.java:80-85`；`StreamingRecordDeserializer.java:100-106`
- **证据片段**:
  ```java
  @Override
  protected String getRawDataString(IBinaryDataReader in, int length) throws IOException {
      return in.peekByteString(length).hex();   // 读出 length 字节 + 转 2×length 字符的 hex 串
  }
  // StreamingRecordDeserializer STAGE_INIT:
  if (recordMeta.getRawVarName() != null) {
      String rawString = deserializer.getRawDataString(in, length);
      context.setValue(recordMeta.getRawVarName(), rawString);   // 驻留 context 至请求结束
  ```
- **严重程度**: P2
- **并发场景**: 流式/非流式解析中模型声明 rawVarName（错误诊断特性）时启用；与 C5-001 同路径但放大机制不同——即使长度合法（区域本就在帧内），诊断串仍额外驻留。
- **触发时序**: length=1MB 的嵌套对象 + rawVarName 声明 → peekByteString 物化 1MB byte[]（peek 后丢弃）→ .hex() 生成 2MB String（char[]，UTF-16 下 4MB）→ setValue 后驻留 context 到请求结束。峰值 = 1MB（临时）+4MB（驻留），是区域大小的 4-5 倍；netty 长连接高频解析时 context 串代际晋升增加 GC 压力。若叠加 C5-001 的无界 length，本路径直接把"临时分配"升级为"请求期驻留"。
- **现状**: hex 形态为可读性选择（异常信息携带）；无长度上限、无截断。
- **风险**: 诊断特性的内存放大在大区域/高频场景下成为主要增量；与 C5-001 同修最经济。
- **建议**: hex 前按上限截断（如 4KB，`StringHelper.limitLen` 同款语义）；或保存 ByteString 原引用延迟 hex 化（仅在异常构造时）。
- **失效面枚举**: rawVarName 消费点全清单：AbstractModelBasedRecordDeserializer.readObject L82-85（非流式）；StreamingRecordDeserializer STAGE_INIT L101-105（流式）；ModelBasedTextRecordDeserializer 对应 getRawDataString（文本路径 substring，放大 1×，同修）。peekByteString→readBytes 分配点已在 C5-001 F3 面登记。
- **信心水平**: 确定
- **误报排除**: 非缓存豁免——诊断串既无界也无淘汰；不是"序列化中间体积"豁免对象，恰是 F7 校准的命中形态（序列化中间体积膨胀）。
- **复核状态**: 已复核（保留 P2；证据行号 :52-54/:82-85/:101-105 逐条核实，文本路径 :49-51 核实，判定见 C5-memory-bounds.md「维度复核结论」表）

---

### [C5-R2-002] markdown/ooxml 文本转换器全量 StringBuilder 物化（登记级）

- **文件**: `nop-format/nop-ooxml/nop-ooxml-markdown/src/main/java/io/nop/ooxml/markdown/PptxToMarkdownConverter.java:462-492`；`DocxToMarkdownConverter.java:35-60`；`nop-format/nop-markdown-ext/src/main/java/io/nop/markdown/ext/MarkdownNormalizer.java:71-88`
- **证据片段**:
  ```java
  // PptxToMarkdownConverter.convertToPlainText
  StringBuilder sb = new StringBuilder();
  for (int i = 0; i < slideCount; i++) { ... sb.append(slideText.trim()); }
  return sb.toString();
  // MarkdownNormalizer.normalizeResource：readText 全文 → normalizeText → writeText
  ```
- **严重程度**: P3
- **并发场景**: 文档→markdown 离线/服务端转换；单文档物化后即释放，无跨请求驻留。
- **触发时序**: 大演示文稿（数百页全图文档）→ 全文本 StringBuilder + toString 复制（2×）→ 转换完成即回收。触发 OOM 需 GB 级文本输入，超出声明场景（文档转换器处理文档级输入）——**超出声明包络，不与包络内缺陷混排**，按口径降 P3 登记。
- **现状**: 物化为转换语义固有；无流式输出通道（convertToStream 缺失）。
- **风险**: 极端文档的峰值内存；运维可见性低。
- **建议**: 如需大文档支持，提供 Appendable/Writer 版本（AppendableTextDataWriter 平台已有同形设施）；否则维持现状并在 javadoc 声明规模包络。
- **失效面枚举**: 同族：PptxToMarkdownConverter.convertToPlainText/convertFromResource、DocxToMarkdownConverter.convertFromResource、MarkdownNormalizer.normalizeResource/normalizeText、PdfDoc.getAllText（初审 C5-005 已登记，不重复计数）。
- **信心水平**: 确定
- **误报排除**: 按"超出声明包络"口径降级登记，不冒充 P1/P2。
- **复核状态**: 已复核（保留 P3；证据实际行号 Pptx :466-494 / Docx :30-48+:59-70 / Normalizer :66-70 微偏不影响实质，判定见 C5-memory-bounds.md「维度复核结论」表）

---

## 继续判定（第 2 轮新增核查面，无新发现项）

| 核查面 | 结论 |
|--------|------|
| PdfSheetRenderer 分页渲染（TableSplitHelper.splitTable + getSubTable().clip()） | 每页 clip 副本有页面上界；splitTable 区域列表随页数增长但为轻量坐标对象（非内容拷贝）——有界，安全 |
| ooxml-xlsx 解析层（AbstractXlsxParser→SheetNodeHandler/SharedStringsTableParser/WorkbookPartParser） | XNode/事件式解析构建 ExcelWorkbook 模型——物化为模型语义本身（声明包络），SharedStringsPart 全表字符串驻留为 xlsx 格式语义，安全（登记于 C5-004 关联说明） |
| tabula 抽取层（ObjectExtractor/Page/TextStripper/NurminenDetectionAlgorithm/SpreadsheetExtractionAlgorithm） | 逐页 Page 物化后由调用方丢弃；Nurminen removeText 每页临时 PDDocument 即时关闭——按页有界，安全 |
| ExtractPageDrawer/ExtractStripper 图片缩放（BufferedImage neww×newh） | maxPixels 上限缩放（L190-200 计算 scale 封顶像素数）——有界设计，安全（对照组） |
| nop-record writer 族（StreamBinaryDataWriter/AppendableTextDataWriter/IBinaryDataWriter） | 写侧为转发，无累积缓冲（IBinaryDataWriter L23 writeBytes(bs.toByteArray()) 为调用方局部）——安全 |
| RecordFileMeta.binaryMatchTrailer 前缀匹配 | startsWithBytes 读 prefix.length 即回 seek——有界读，安全（分配面见 C9-R2-001） |
