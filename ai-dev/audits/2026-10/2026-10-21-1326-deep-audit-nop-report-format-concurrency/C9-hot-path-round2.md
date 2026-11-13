# C9 深挖第 2 轮 — nop-report + nop-format

> 方法：不与初审 C9-001~005 重复。深挖 record 解码路径的每记录分配、netty 解码面、PdfStyleHelper/TextWrapHelper、trailer 匹配 lambda。

### [C9-R2-001] RecordFileMeta.fixBody 二进制 trailer 匹配 lambda 每记录分配 prefix.toByteArray()

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/model/RecordFileMeta.java:136-150`
- **证据片段**:
  ```java
  if (isBinary()) {
      body.setRepeatUntil((thisObj, args, scope) -> {
          return binaryMatchTrailer((IBinaryDataReader) args[0], prefix.toByteArray());  // 每次调用分配
      });
  } else {
      String str = prefix.buildString(resolvedTrailerType.getCharsetObj());   // 文本路径已提升到循环外
      body.setRepeatUntil((thisObj, args, scope) -> {
          return textMatchTrailer((ITextDataReader) args[0], str);
      });
  }
  ```
- **严重程度**: P3
- **并发场景**: repeatUntil=trailer 前缀的记录文件解析；lambda 在**每条 body 记录读取前**执行一次（repeat-until 语义），直至文件尾。
- **触发时序（量化口径）**: 100 万条记录的文件，trailer 前缀 16 字节 → 100 万次 `prefix.toByteArray()`（每次新 byte[16] + ByteString 内部拷贝），对比文本路径把 `str` 提升到 lambda 外的正确写法 → 二进制路径多出 10^6 次小分配（约数十 MB 累计 churn，TGC 压力）。功能正确的纯浪费。
- **现状**: 同方法内文本分支已示范正确写法（提升到闭包外），二进制分支遗漏——同函数内不一致，修复成本一行。
- **风险**: 高频解析吞吐损耗 + GC 压力；无正确性影响。
- **建议**: 把 `byte[] prefixBytes = prefix.toByteArray();` 提升到 lambda 外（与文本分支同构）。
- **失效面枚举**: repeatUntil 装配点全清单：RecordFileMeta.fixBody（本条，二进制/文本两分支）；RecordFieldMeta/StreamingRecordDeserializer 中 repeatUntil 消费点（processCollectionStreaming L380 起每轮调用）——消费侧无分配，仅装配侧本条。
- **信心水平**: 确定
- **误报排除**: 有量化口径（频率×代价），符合本维度立项标准；文本分支的正确写法证明非设计约束。
- **复核状态**: 已复核 2026-10-22：保留 P3（每记录分配点 RecordFileMeta.java:137-140 与消费频率 StreamingRecordDeserializer.java:384 do-while 内 checkUntil 均核实；startsWithBytes 块读不放大该路径）

---

## 继续判定（第 2 轮新增核查面，无新发现项）

| 核查面 | 结论 |
|--------|------|
| PdfStyleHelper.drawText / normalizeControlChars | 无 per-call Pattern.compile/replaceAll（grep 仅 TextWrapHelper static final 预编译，正确对照组）；drawText 每次 getStringWidth 为 PDFBox 必要调用——安全 |
| netty 解码热路径（ModelBasedPacketCodec.decodeFromBuf/determineType/DefaultFieldCodecContext） | 每消息 new context + reader 为解码语义必需；帧长校验（ERR_RECORD_FIELD_LENGTH_IS_TOO_LARGE）先行——除 C9-R2-001/C6-R2-001 已登记项外无新增分配热点 |
| BlockCached* findBlockForPosition 线性扫块 | ≤ maxCacheBlocks(8) 有界常数循环，每字节调用总代价 ~8 次比较——不构成可量化瓶颈（对照 C9 第 3 条证据要求），不立项 |
| FixedLengthStringCodecFactory.getCodec computeIfAbsent | 键=StdDataType 枚举×pad×leftPad×charset，基数 <200，锁分片良好——安全（对照组，R1 缓存面锚点核实为无误报） |
| chart-export 渲染热路径（JFreeChart dataset 构建/ChartDataFilter 排序） | 每次导出构建一次，非每记录；sortDataSetByValue O(n log n) 必要——安全 |
| ExcelIOConfig.DEFAULT 静态共享 | 不可变配置对象（static final），安全 |
| ExpandedSheetEvaluator.INSTANCE 单例 | 无状态（全部状态经参数/ExpandedCell 传递），evaluateCell 单线程每格调用——安全 |
| XptRuntime.cellExprCache/formulaCompileCache | 每渲染新建的 HashMap，键=模板内表达式文本（有界）——安全；渲染结束后整体回收 |
