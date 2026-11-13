# C1 深挖第 2 轮 — nop-report + nop-format

> 方法：对初审已发现维度挖掘新发现，不与初审条目重复。重点扩大面：ByteBuf 读取器契约、chart-export 渲染器实例态、ooxml-xlsx chart 解析静态面、report-core 剩余静态面（ExcelTemplateToXptModelTransformer/DynamicReportDataSet/ReportFunctions/ReportFunctionProvider）、nop-svg/NormalizingReader、nop-mermaid ANTLR 面。

### [C1-R2-001] ByteBufBinaryDataReader.isDetached() 恒返回 true 且 detach()=duplicate() 语义同化（detach 契约族第二次偏离）

- **文件**: `nop-format/nop-record/src/main/java/io/nop/record/netty/ByteBufBinaryDataReader.java:345-352,440-443`
- **证据片段**:
  ```java
  @Override
  public IBinaryDataReader detach() {
      return new ByteBufBinaryDataReader(bb.duplicate());   // 与 duplicate() 实现完全相同
  }
  @Override
  public boolean isDetached() {
      return true;                                          // 无条件 true
  }
  ```
- **严重程度**: P3
- **并发场景**: 与初审 C1-005（RAF detach 共享指针）同属 detach 契约族的实现漂移：ByteBuf 实现的 detach 基于索引独立的 `bb.duplicate()`（内容共享、读写索引独立）——语义上接近契约但与 duplicate() 不可区分；`isDetached()` 对未 detach 的顶层 reader（ModelBasedPacketCodec.decodeFromBuf 中 `new ByteBufBinaryDataReader(buf)`）也返回 true。
- **触发时序**: `isDetached()` 当前在生产代码无任何消费方（grep 全模块仅实现处自身），故无具体交错——**理论风险，未具体化**，按口径降 P3。风险：未来调用方以 isDetached() 判定"可并发持有"时，顶层 netty 帧 reader 也被判定为 detached，而其内容共享 pipeline 帧缓冲，帧释放后读取即 use-after-release。
- **现状**: 三态实现（真独立/共享指针/索引独立）+ 无消费方的 isDetached —— 接口契约缺失导致实现各自漂移。
- **风险**: 契约漂移的回归地雷；与 C1-005 合并修复（在 IBinaryDataReader 上定义 detach/duplicate/isDetached 三方契约）成本最低。
- **建议**: 接口 javadoc 定义三方法语义；ByteBuf.detach 改为 `bb.copy()`（真独立）或明确 duplicate 语义；isDetached 以构造标志位实现。
- **失效面枚举**: detach 族完整矩阵（更新 C1-005 的 F3 面）：Stream（真独立-物化）/ ByteBuffer（真独立-复制）/ ByteBuf（索引独立-共享内容）/ BlockCached（真独立-复制块）/ Sub（跟随底层）/ RandomAccessFile（**伪独立-共享指针**，见 C1-005）。
- **信心水平**: 确定（代码事实）；后果为推测
- **误报排除**: 不按"原语使用"豁免——detach/isDetached 是接口语义部件；因无现网触发点按 P3 登记。
- **复核状态**: 已复核（保留）

---

## 继续判定（第 2 轮新增核查面，无新发现项）

| 核查面 | 结论 |
|--------|------|
| ExcelTemplateToXptModelTransformer 静态方法（getExpandIndexAction/getExpandFieldAction/getSheetNameExpr） | 每次调用 new 动作对象，无静态可变字段；BuildXptModelListener 实例态（parents/labelCells）为 transform 实例私有，线程封闭——安全 |
| DynamicReportDataSet/ReportDataSet 静态工厂 | 无静态可变状态；makeDataSet 走 xptRt.getCache()（XptRuntime 实例态，渲染内封闭）——安全 |
| ReportFunctionProvider/ReportFunctions | INSTANCE 启动期注册 static functions 后只读——安全 |
| s_filterTpl（ExcelFormulaParser:62） | static 静态块写入后无任何读点（grep 确认）——死字段，无并发影响（转 merge 裁定为 P3 清理候选，不占 C1 发现编号） |
| chart-export 渲染器族（Bar/Line/Pie/Heatmap/TrendLine/ChartAxis renderer + ChartDataFilter/ChartDataValidator/ChartShapeStyleProcessor/ChartTextStyleProcessor） | 均为每次导出 new 的实例态对象，无可变静态字段——线程封闭，安全 |
| ooxml-xlsx chart 解析族（DefaultChartStyleProvider 901L/ChartShapeStyleParser 822L/ChartSeriesParser/ChartTypeConfigParser/ChartAxisParser/ChartTypeConfigBuilder/ChartAxisBuilder/ThemeFileParser） | grep 无 static 可变集合；全部 per-parse 实例态——安全 |
| nop-svg NormalizingReader/parse 族 | javadoc 自述"not synchronized"为 Apache codec 拷贝惯例，实例 per-parse 封闭；无静态可变面——安全 |
| nop-mermaid ANTLR Lexer/Parser/Visitor | ANTLR 生成物静态数组只读；ANTLR4 DFA 缓存并发安全为运行时设计口径（基础设施不重审）；MermaidASTBuildVisitor per-parse 实例——安全 |
| XptRuntime.getCache synchronized（实例级） | XptRuntime 每次渲染新建、单线程消费，synchronized 无实际争用方——防御性冗余，不构成发现（按误报校准"低频粗锁不报"） |
| ImportExcelParser/ImportDataCollector 的 cache 传递 | LocalCache.newCache(newConfig(100)) 有界 + parser 实例封闭——安全 |
| FieldCodecRegistry/FixedLengthStringCodecFactory 缓存 | ConcurrentHashMap + computeIfAbsent，键空间=枚举×pad×charset 有界——安全 |
