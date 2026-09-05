# nop-format 模块深度缺陷检查报告

> Status: resolved
> Date: 2026-09-05
> Scope: `nop-format/` 全部 14 个子模块（nop-excel、nop-record、nop-record-netty、nop-pdf、nop-converter、nop-tablesaw、nop-chart-export、nop-svg、nop-mermaid、nop-markdown-ext、nop-office-model、nop-office-doc-model、nop-chart-echarts、nop-ooxml 无源码）
> Conclusion: 确认 23 处缺陷（4×P1、12×P2、7×P3），全部在本次任务中修复并附带回归测试，修复前均以失败测试证明缺陷存在；另有 12 处发现经评估不在本轮修复（跨模块根因 / 上游继承 / 需求歧义 / 需底层支持），1 处高疑似项经下游回归证伪为承重设计约定

## Context

- 对 nop-format 全部非生成源码（约 780 个 main 文件）做系统性 bug 排查：6 个并行扫描代理逐文件审查，候选缺陷再由主代理逐一亲读源码与测试验证。
- nop-format 无专属设计文档（`ai-dev/design/` 无对应子目录），设计意图以模块自身代码、javadoc、接口契约（如 `ITextDataReader.readLine` 的注释契约）和既有测试为准。`AbstractModelBasedRecordDeserializer.readObject` 中关于"急切/惰性 reader 区域对齐公式"的注释是 nop-record 区域语义的权威依据。
- 每个确认缺陷的处理流程：亲读完整实现 → 写失败测试证明缺陷存在 → 修复 → 测试转绿。

## Analysis

### 缺陷总表（本轮修复）

| # | 模块 | 缺陷 | 级别 | 文件 |
|---|------|------|------|------|
| R1 | nop-record | `SubBinaryDataReader.subInput` 委托 `underlying.subInput`，自身 position 不前进，绕过自身限额 | P1 | `record/reader/SubBinaryDataReader.java:131` |
| R2 | nop-record | `RecordAggregateState.checkPageChanged` 条件 `indexInPage >= pageSize - 1`，每页实际只容纳 pageSize-1 条记录；pageSize=1 时文件开头先输出一个空页脚 | P1 | `record/resource/RecordAggregateState.java:114` |
| R3 | nop-record | `IBinaryDataWriter.writeFloat` 默认小端（与 `readFloat` 大端不对称）；`writeDouble` 参数误声明为 `float` | P2 | `record/writer/IBinaryDataWriter.java:42` |
| R4 | nop-record | `LVFieldBinaryCodec`/`DynLVFieldBinaryCodec` 用 `len >= length` 拒绝恰好等于 maxLength 的值，编解码不对称 | P2 | `record/codec/impl/LVFieldBinaryCodec.java:38`、`DynLVFieldBinaryCodec.java:55` |
| R5 | nop-record | `StreamingRecordDeserializer` fixed 集合的 subInput 从不关闭（ByteBuf 引用计数泄漏），且 `length<=0` 时行为与非流式路径分歧（读到整个输入 EOF 而非单条） | P2 | `record/serialization/StreamingRecordDeserializer.java:348` |
| R6 | nop-record | `SubTextDataReader.readLine` 子区间耗尽时返回 `""` 而非契约规定的 `null`；`skip(0)` 在区间耗尽后误抛 NO_ENOUGH_DATA | P3 | `record/reader/SubTextDataReader.java:111` |
| E2 | nop-excel | `MultiLineConfigParser` 三引号值：`nextUntil("\n\"\"\"")` 停在分隔符前但不消费，回到 `parseConfig` 后 `nextXmlName` 必抛 ERR_SCAN_INVALID_XML_NAME，`"""` 语法完全不可用 | P1 | `excel/util/MultiLineConfigParser.java:48` |
| E3 | nop-excel | `ExcelDataValidation` sqref 按 `,` 解析/拼接；OOXML ST_Sqref 是空格分隔，读 Excel 生成的多区域校验直接抛类型转换异常，写出则产生非法 xlsx | P1 | `excel/model/ExcelDataValidation.java:126` |
| E4 | nop-excel | `ColorHelper.rgb()` 不置 alpha 位，6 位色 ARGB=0x00RRGGBB，`new Color(argb,true)` 全透明；纯黑 `#000000` 与"无效色"哨兵 0 混淆 | P1 | `excel/model/color/ColorHelper.java:93` |
| E5 | nop-excel | `ExcelCell.cloneInstance` 漏拷 `linkUrl`/`name`/`protected`（生成版 `copyTo` 均包含），克隆表格丢失全部超链接 | P2 | `excel/model/ExcelCell.java:79` |
| E6 | nop-excel | `HtmlReportRendererFactory.renderStyles` 在 `.xpt-row` 规则后多输出一个 `}`，生成非法 CSS | P2 | `excel/renderer/HtmlReportRendererFactory.java:108` |
| E7 | nop-excel | `ColorHelper`/`OfficeColorHelper.toCssColor` 对 `"0x0"`（默认字体色）输出非法 CSS `#0` | P3 | `ColorHelper.java:61`、`OfficeColorHelper.java:29` |
| E8 | nop-excel | `ExcelCell.toString` 三元条件写反：名为 null 时打印 `,null`，有名时不打印 | P3 | `excel/model/ExcelCell.java:60` |
| E9 | nop-excel | `TreeTableDataParser.findEmptyRowIndex` 对 `table.getRow(i)` 越界返回 null 直接解引用（mergeDown 超出实体行时 NPE） | P3 | `excel/imp/TreeTableDataParser.java:432` |
| P1 | nop-pdf | `TableCellBlock(row,col,rowSpan,colSpan)` 构造器丢弃 span 参数硬编码 1，`DefaultTableMerger` 依赖它传参导致合并表丢失全部跨行跨列 | P1 | `pdf/extract/struct/TableCellBlock.java:32` |
| P2 | nop-pdf | `TableBlock` 改用 `BaseTable` 存储后，跨行跨列单元格的 spanned 位置是 `ProxyCell`，`getCell`/`getRowCells`/`getCellBlockByIndex` 盲转 `TableCellBlock` → 任何含合并单元格的表格触发 ClassCastException，整条 PDF 解析管线失败 | P1 | `pdf/extract/struct/TableBlock.java:129` |
| P3 | nop-pdf | `BlockPointer.nextBlock` 在最后一页末尾递归调用 `page().getSortedBlocks()` 时 `page()` 已为 null → NPE（调用方以 null 判终止） | P2 | `pdf/extract/struct/BlockPointer.java:76` |
| P4 | nop-pdf | `BlockPointer.collectPageTables` 持续用**第一个**表的页码做 +1 比较，跨 3 页以上的表格被截断 | P2 | `pdf/extract/struct/BlockPointer.java:208` |
| P5 | nop-pdf | `DualMarkerTableLocator.merge`：`srcTables` 为空时 `get(0)` 抛 IOOBE；`merge` 返回 null（不可合并）时当前表被静默丢弃 | P2 | `pdf/extract/data/DualMarkerTableLocator.java:142` |
| P6 | nop-pdf | `ExtractPageDrawer.resizeImage` 无视 `toGrayImage` 参数恒生成灰度图，`grayImageEnabled=false` 配置失效 | P2 | `pdf/extract/parser/ExtractPageDrawer.java:202` |
| P7 | nop-pdf | `DefaultResourceHtmlWriter.writeTableBlock`：空单元格占位 `&nbsp;` 经 `encodeText` 二次转义成字面 `&amp;nbsp;`；配合 P2 修复后合并 `<td>` 会在每个覆盖位置重复输出，需按 rowPos/colPos 去重 | P3 | `pdf/extract/export/DefaultResourceHtmlWriter.java:205` |
| P8 | nop-pdf | `VOverlapBasedBlockComparator` 垂直重叠计算误用 `getMinX()` 作为 y 起点（轴向复制粘贴错误）；`RCPathCellDataLocator` 边界检查用 `&&`（恒假死代码） | P3 | `cmp/VOverlapBasedBlockComparator.java:40`、`data/RCPathCellDataLocator.java:92` |
| C1 | nop-chart-export | `ChartDataFilter` 值过滤缺省 `min = Double.MIN_VALUE`（最小正数），min 未配置时 0 和负值全部被过滤 | P1 | `chart/export/filter/ChartDataFilter.java:67` |
| C2 | nop-chart-export | `RadarChartRenderer` 无 plotArea 时 NPE（Bar 渲染器同类问题已修，Radar 漏修） | P2 | `chart/export/renderer/RadarChartRenderer.java:39` |
| C3 | nop-chart-export | `ChartDataFilter` 过滤后 `setXValues(null)` 破坏 `ChartDataSet` 默认非空约定，Bubble/Scatter/Heatmap 渲染器 NPE | P2 | `chart/export/filter/ChartDataFilter.java:171` |
| C4 | nop-chart-export | Top-N 过滤是桩实现（`sortDataSetByValue` 原样返回），返回"前 N 个"而非"最大 N 个" | P2 | `chart/export/filter/ChartDataFilter.java:237` |
| S1 | nop-svg | `SVGPath.transform` 对空 path NPE；且把 `SEG_ARCTO` 的 `[rx,ry,angle,largeArc,sweep]` 当坐标点变换，弧段参数损坏 | P2 | `svg/model/SVGPath.java:497` |
| M1 | nop-markdown-ext | `MarkdownNormalizer.normalizeMathNode` 消费了不成对的起始 `$` 后丢弃，奇数个 `$` 的文本归一化后丢字符 | P2 | `markdown/ext/MarkdownNormalizer.java:158` |
| T1 | nop-tablesaw | `XlsxReader.findRowArea` 对稠密单元格列表中"未写过"的 null 单元格 `break`，行区域在中间空洞处截断（POI 行迭代器跳过未写单元格），列被静默丢弃甚至整表丢失 | P1 | `tablesaw/xlsx/XlsxReader.java:190` |
| T2 | nop-tablesaw | `XlsxReader.getCellTypes` 增量类型合并依赖顺序，`[19.99, 20]` 得到 `{DOUBLE,INT}` 降级为 STRING 列（`[20, 19.99]` 则正确得到 DOUBLE） | P1 | `tablesaw/xlsx/XlsxReader.java:384` |
| V1 | nop-converter | `DocumentConverterManager.findChainedConverter` 循环内用 `requireDocumentObjectBuilder`（抛异常）而非跳过该中间类型继续尝试 | P3 | `converter/DocumentConverterManager.java:274` |

### 关键验证依据（摘）

- **R1**：`readObject` 区域对齐公式 `remaining = (subStart + length) - baseIn.pos()`（`AbstractModelBasedRecordDeserializer.java:105-111` 注释明确急切/惰性两类 reader 契约）。`ByteBufferBinaryDataReader.subInput` 立即推进 `bb.position`（急切）；`StreamBinaryDataReader.subInput` 返回 `new SubBinaryDataReader(this, maxLength)`（惰性、读穿透父 reader）。而 `SubBinaryDataReader.subInput` 返回 `underlying.subInput(...)`：既不推进自身 position（对齐公式算出虚增的 remaining → 双重 skip），读取也绕过自身 maxLength 限制。嵌套区域（body length=20 内含 length=15 的对象/集合字段）经流式 reader 读取时，从第一条记录起数据错位。
- **R2**：`beforeWriteRecord` 在写记录**前**调 `checkPageChanged`；`indexInPage >= pageSize - 1` 使页中断在第 pageSize 条之前触发。pageSize=3 写 6 条 → 每页 2 条、3 个页脚。pageSize=1 首条记录触发 `flushPageFooter()` 在任何页头/数据之前。
- **E2**：`TextScanner.nextUntil(String,boolean)` 停在匹配分隔符之前且不消费（`TextScanner.java:1325-1371`）。`sc.skipBlank()` 只吃掉 `\n`，`cur` 停在 `"`，`nextXmlName` 对 `"` 抛 ERR_SCAN_INVALID_XML_NAME。消费方：`ExcelToXptModelTransformer`、`RuleTableModelParser`（批注解析）。
- **E3**：`CellRange.parseRangeList`（nop-core）只按 `,` 切分；`"A1:A10 B1:B10"` 走单区间分支，`CellPosition.fromABString("A10 B1:B10")` 行号解析失败抛异常。现有 `TestExcelDataValidation` 设置了空格分隔的 sqref 但从不调用 `getRanges()`，故未暴露。
- **P2**：`AbstractTable.doSetCell`（nop-core `AbstractTable.java:561-583`）在 spanned 位置写入 `newProxyCell(...)`；`ProxyCell` 不是 `TableCellBlock`。`DefaultTableDetector` 对每个检测出的跨行列单元格 `setRowspan/setColspan` 后 `addCell` → `rebuildTable`/`resetCellBlockIndex`/`DefaultTableMerger`/`DefaultResourceHtmlWriter` 全部经 `getCell` 盲转 → CCE。这是从自有 `Map<String,TableCellBlock>` 存储迁移到 `BaseTable` 时的移植缺陷。
- **T1/T2**：与上游 tablesaw 0.43.1 对比：POI `Row` 迭代器只访问已写入单元格、`Sheet` 迭代器跳过未写行；本移植在稠密表格模型上迭代，null 单元格/补齐空行需要显式区分处理。
- **C1**：`Double.MIN_VALUE` 是最小正 double（4.9e-324），过滤测试 `doubleValue >= minValue` 把 `<=0` 的值全部排除。

### 发现但本轮不修复（记录待办）

1. **nop-excel `ExcelDataValidationHelper`** date/time 校验 formula1 带前导 `=`（`formulaDateForExcel` 等），与数值校验和 OOXML formula1 格式不一致，Excel 可能拒绝。P2/中等置信，需在 xlsx 导出侧实测 Excel 行为后修。
2. **nop-excel `ExcelSheet.cloneInstance`** 漏拷 defaultColumnWidth/defaultRowHeight/pageSetup/dataValidations/charts/sheetProtection（无仓内调用方，疑似刻意精简克隆；修前需明确契约）。P2。
3. **nop-excel `ImportDataCollector.addDefaults`** 用 `field.getName()` 注册缺省值而取值用 `getPropOrName()`，`prop != name` 的字段读取会抛 unknown-prop。P3。
4. **nop-excel `ExcelModelHelper.copyTable`** 漏拷 `type` 等字段，与 `ExcelCell.cloneInstance` 字段集互相矛盾（手工拷贝器漂移）。P3。
5. **nop-excel `KeyedListAdapter.add`** 空主键行被静默丢弃（sheet 级有 `ERR_IMPORT_SHEET_WITH_DUPLICATE_KEY_PROP` 对称检查，嵌套 list 没有）。P2。
6. **nop-record `readSwitch`** 类型级 `readWhen` 求值时 record 参数传 null（`AbstractModelBasedRecordDeserializer.java:336`）。P3。
7. **nop-record-netty `ProxyHandler.channelRead`** 转发失败路径不释放消息（依赖 `sendToAnyChannel` 的失败语义，在本模块外）。P3。
8. **nop-tablesaw 缺失行**：稠密行模型中未写行与空白行不可区分（`AbstractTable.makeRow` 补齐无标记），XlsxReader 无法复刻 POI 跳过未写行的语义，含行空洞的 sheet 仍会截断。需要 ExcelTable 提供行级"未写"标记后修复。P2。
9. **nop-mermaid `MermaidGenerator`**：文档头输出 `flowchart FLOWCHART`（重复词）、`style` 目标未加引号且过 `id()` 校验、flow/sequence 箭头 token 与自家文法不匹配——三者均与上游一致（继承缺陷），修复需确定目标 Mermaid 方言版本。P2。
10. **跨模块根因**：`TextScanner.nextUntilUnescaped` 输入以反斜杠结尾时 `out.accept(-1)` 被转成 `'\uFFFF'`（根因在 nop-commons，经 `MarkdownNormalizer` 显现）。本轮仅在 M1 修复中于 markdown 侧规避；nop-commons 侧修复需单独任务。
11. **nop-chart-export**：stacked LINE 渲染成 stacked AREA（语义替换，无注释标记意图）；Pie 爆炸索引与数据集键错位；Heatmap 走常规解析链路得到空图（`xValues` 仅对 SCATTER/BUBBLE 填充）。P2，涉及渲染语义决策。
12. **nop-pdf `ExtractStripper`** cropBox 左下偏移的 `transAT` 在文本主路径未应用（非零 crop 原点 PDF 的坐标整体偏移）。P2/中等置信，需视觉回归验证。

### 排除的误报（扫描候选但亲读/回归后不成立）

- **`TreeTableDataParser.parseNextFields` 的 `j < maxColIndex`（最初判为 P1 off-by-one）**：修复为闭区间后 `TestExcelHelper.testApi` 回归失败——`test.api.xlsx` 的 `配置` sheet 以最后一列为文档化的"说明"注解列（C1='说明'），整个模板生态依赖"parseNextFields 不进入最后一列"的语义。该边界是承重设计约定而非缺陷，已回退并在代码中补充注释固化该约定（`testLastColumnTreatedAsAnnotationColumn` 锁定行为）。
- `BlockCachedTextDataReader.readLine` 空行返回 null：不成立——找到换行符的路径在 346 行正确返回 `""`，null 仅出现在 EOF 无内容时，符合契约。
- `ImportExcelParser` 的 `multipleAsMap` 对齐、`skipNonDataRow` 的 `rowIndex <= getRowCount()`、`normalizeTree/flattenTree` 等：均经亲读或已有测试确认无缺陷。

## Conclusion

- 23 处缺陷全部修复，每处均先以失败测试证明缺陷存在（修复前红、修复后绿），详见各模块 test 目录与 `ai-dev/logs/2026/09-05.md`。
- 修复策略遵循各模块既有设计契约：nop-record 区域对齐以 `readObject` 注释公式为准；nop-pdf 以 `getRealCell()` 语义穿透 ProxyCell；nop-tablesaw 以对齐上游 tablesaw 语义为准；TreeTableDataParser 的"最后一列为注解列"约定经回归验证后保留并固化注释。
- 待办项（"发现但本轮不修复"节 12 条）不在本轮范围，建议按模块拆分后续任务；其中表格行级"未写"标记（#8）与 TextScanner U+FFFF（#10）是另两处缺陷的前置条件。
- 被否决的方案：修改 nop-core `CellRange.parseRangeList/toABStringList` 的分隔符语义——该助手是通用工具，逗号语义可能被其他调用方依赖；改为仅在 `ExcelDataValidation` 内做空格分隔的解析/拼接。

## References

- `nop-format/nop-record/src/main/java/io/nop/record/serialization/AbstractModelBasedRecordDeserializer.java`（区域对齐契约）
- `nop-kernel/nop-core/src/main/java/io/nop/core/model/table/impl/AbstractTable.java`（ProxyCell 写入语义）
- `docs-for-ai/01-repo-map/module-groups.md`
- 上游参考：tablesaw 0.43.1 `XlsxReader`、tabula-java（nop-pdf.tabula 为其移植）
