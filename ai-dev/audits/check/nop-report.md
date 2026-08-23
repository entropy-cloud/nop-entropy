# nop-report 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-report
- 文件数: 176（src/main/java）
- 覆盖范围声明: 实测 `nop-report/*/src/main/java` 下共 153 个 Java 文件，其中 27 个带 `__XGEN_FORCE_OVERRIDE__` 生成标记（api/beans 18 + api/crud 9），剔除后手工代码 142 个，与任务给定约数 176 的差异应为统计口径（是否含 target/ 或 `_` 前缀文件）。检查方式: (1) 全模块 grep 扫描（空 catch、bare RuntimeException、printStackTrace、Workbook 构造/close、synchronized、private @Inject、@Value、除法等）；(2) 逐文件深读 core 引擎全部关键类（ExpandedCell/ExpandedTable/ExpandedRow/ExpandedCellSet/ExpandedSheetEvaluator/ExpandedSheetGenerator/XptRuntime/ReportEngine/TableExpander + 4 个 Expander/ReportFunctions/RankCompute/ReportDataSet/DynamicReportDataSet/KeyedReportDataSet/CellCoordinateHelper + 坐标与表达式执行器 6 个/XptModelInitializer/ExcelToXptModelTransformer/ExcelTemplateToXptModelTransformer/XptModelLoader/XptConfigParseHelper/ExcelRecordInput/ExcelRecordOutput/ExcelResourceIO/ExcelIOConfig/ReportDataHelper/ExcelReportHelper/HtmlRenderHelper/initializers）；(3) 深读 pdf 模块全部 11 个文件、docx 模块全部 5 个文件、ext 全部 3 个、demo 1 个；(4) service 层 12 个文件与 dao 层实体为薄封装/生成链路产物，抽样核读（NopReportDefinitionBizModel、NopReportResultFileBizModel、ReportDemoBizModel、NopReportErrors/Configs）。api 模块 27 个 XGEN 生成文件仅抽样未逐行。每个写入发现均经过上下文通读与触发路径推演；未验证充分的可疑点（如 XptModelInitializer.checkLoop 条件抛错、ReportDataHelper.mergeDown 覆盖已有 mergeDown 等）按"宁缺毋滥"原则未列入。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 6 |
| P2 | 7 |
| P3 | 11 |

## 发现列表

### [P0] ReportDataSet.min/minBy 遇 null 值直接放弃整个聚合结果

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/dataset/ReportDataSet.java:298-328`
- **维度**: D1（聚合计算 null 处理错误）
- **证据**:
```java
public Object min(String field) {
    List<Object> items = current();
    Object ret = null;
    for (Object item : items) {
        Object value = getFieldValue(item, field);
        if (ret == null) {
            ret = value;
        } else if (value == null) {
            return value;              // <-- 非首位 null 直接返回 null，终止聚合
        } else if (MathHelper.compareWithConversion(ret, value) > 0) {
            ret = value;
        }
    }
    return ret;
}
```
`minBy`（314-328 行）同样逻辑。对照同文件 `max`（265-279 行）对 null 的处理是跳过（`else if (value != null)`）。
- **现状**: 数据集中只要在首个非空值之后出现一个 null 字段值，`min()/minBy()` 即返回 null，而非继续求最小值。`min([5, null, 1])` 返回 null，正确结果应为 1。
- **风险**: 报表表达式 `ds.min('field')` 在含空值的数据上输出空，业务依据错误。null 字段值在数据库查询结果中非常常见，触发路径现实。
- **建议**: 与 `max` 保持一致，null 值跳过（`else if (value != null && compare > 0)`）。
- **误报排除**: 非"首个 null 视为无最小值"的语义设计——首位的 null 会被后续非空值覆盖（`ret == null` 分支），说明本意是跳过 null，仅在非首位时误写为 return。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`min`/`minBy` 删除 `else if (value == null) return value;` 分支，改为与 `max`/`maxBy` 完全对称的跳过 null 写法（`else if (value != null)` 包裹比较），非首位 null 不再终止聚合。测试：`nop-report/nop-report-core` `TestReportDataSet#testMinSkipsNullValues`（修复前 `min([5,null,1])` 返回 null）、`TestReportDataSet#testMinBySkipsNullValues`（修复前 `minBy([5,null,1])` 同样返回 null）。

### [P1] ExpandedCell.childCell 的 colDescendants 分支求值后返回 null

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedCell.java:735-753`
- **维度**: D1（复制粘贴错误导致取值恒为 null）
- **证据**:
```java
public ExpandedCell childCell(String cellName, IXptRuntime xptRt) {
    if (rowDescendants != null) {
        List<ExpandedCell> cells = rowDescendants.get(cellName);
        if (cells != null && !cells.isEmpty()) {
            ExpandedCell cell = cells.get(0);
            xptRt.evaluateCell(cell);
            return cell;
        }
    }
    if (colDescendants != null) {
        List<ExpandedCell> cells = colDescendants.get(cellName);
        if (cells != null && !cells.isEmpty()) {
            ExpandedCell cell = cells.get(0);
            xptRt.evaluateCell(cell);
            return null;               // <-- 应返回 cell
        }
    }
    return null;
}
```
- **现状**: rowDescendants 分支返回 `cell`，colDescendants 分支完成求值后却 `return null`。`childValue()` 与 `@EvalMethod cv(scope, cellName)`（760-764 行）都经由该方法，列展开结构下 `cell.cv('xx')` 恒返回 null。
- **风险**: 仅存在列后代（无行后代）的单元格通过 `cv()/childValue()` 取子格值时数据错误（null），且子格被求值的副作用使问题更隐蔽。
- **建议**: colDescendants 分支改为 `return cell;`。
- **误报排除**: 已对照 row 分支确认非有意为之；`cv` 是公开 EvalMethod，报表表达式可直接调用。

### [P1] ExpandedCell.getExpandableRowParent 递归时错误调用了列方向方法

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedCell.java:622-630`
- **维度**: D1（复制粘贴错误）
- **证据**:
```java
public ExpandedCell getExpandableRowParent() {
    if (rowParent == null)
        return null;
    if (rowParent.getModel() == null)
        return null;
    if (rowParent.getModel().getExpandType() == null)
        return rowParent.getExpandableColParent();   // <-- 应为 getExpandableRowParent()
    return rowParent;
}
```
- **现状**: 行方向的"最近可展开祖先"在 rowParent 不可展开时递归进了 colParent 链（或提前返回 null），与 612-620 行的 `getExpandableColParent`（正确递归自身）不对称。
- **风险**: 唯一调用点 `CellRowExpander.isAllowReuse`（CellRowExpander.java:167）用它判断"兄弟节点是否同一父"以决定复用已生成行；返回错误祖先会导致本应复用的行被重复插入、或反之，破坏展开表结构（行数/合并区域错乱）。
- **建议**: 递归调用改为 `getExpandableRowParent()`。
- **误报排除**: 已 grep 确认调用链 `isAllowReuse -> getExpandableRowParent` 真实存在，且仅在 rowParent 链上存在"不可展开中间节点"时触发（多级嵌套报表）。

### [P1] CellRowExpander/CellColExpander.removeCell 的展开区域起点用了减号

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/expand/CellRowExpander.java:42-47`、`CellColExpander.java:39-44`
- **维度**: D1（坐标计算符号错误）
- **证据**:
```java
// CellRowExpander.removeCell
int startIndex = cell.getRowIndex() - cell.getModel().getRowExpandOffset();  // 减号
int endIndex = startIndex + cell.getModel().getRowExpandSpan();
for (int i = startIndex; i < endIndex; i++) {
    table.getRow(i).setRemoved(true);
}
```
而同文件 `extendCells`（60 行）、`duplicateCell`（119 行）以及 `XptModelInitializer` 的全部计算（如 XptModelInitializer.java:464、521、649 行）均为 `rowIndex + rowExpandOffset`；`XptModelInitializer.java:469` 定义 `rowExpandOffset = minRowIndex - xptRowIndex`（可为负）。CellColExpander.java:39 同样用 `- getColExpandOffset()`。
- **现状**: 当展开区域起点不在展开单元格所在行/列（offset != 0，即子格位于父格上方/左侧）时，`removeCell`（空数据 + `shouldRemoveEmpty=true`）删除的是错误行/列。
- **风险**: 例: 父格在第 5 行、子格在第 3 行（offset=-2，span 覆盖 3-5 行），removeCell 实际标记删除第 7-10 行，输出报表丢失无关数据行。
- **建议**: 两处均改为 `+ getRowExpandOffset()` / `+ getColExpandOffset()`。
- **误报排除**: offset=0 时两种写法等价（多数简单模板不触发）；已核对 offset 语义来源确认 `+` 为全库一致约定。

### [P1] CellCoordinateHelper.resolveColCoordinates 绝对定位越界条件与行方向不一致

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/coordinate/CellCoordinateHelper.java:197-201`
- **维度**: D1（坐标解析边界错误 / 数组越界）
- **证据**:
```java
// resolveColCoordinates（列坐标）绝对定位分支
} else {
    if (pos < cells.size()) {
        resolvedCell = cells.get(pos - 1);      // pos==size 取不到最后一个；pos<=0 时 get(-1) 越界
    }
}
```
对照行方向 `resolveRowCoordinates`（89-92 行）: `if (pos > 0 && pos <= cells.size()) resolvedCell = cells.get(pos - 1);`。表达式解析器 `AbstractExcelFormulaParser.parseCellCoordinates`（113-117 行）允许 `pos = 0`（`sc.nextInt()` 不限制 0）。
- **现状**: (1) 列坐标绝对定位 `B[;A:n]` 取最后一个元素（pos == size）时解析为 null，而行方向可取到；(2) 用户写 `:0` 时 `0 < size` 成立，`cells.get(-1)` 抛 `IndexOutOfBoundsException`，报表生成中断。
- **风险**: 列展开报表按绝对序号取末位格静默丢失；`:0` 写法直接抛未包装的运行时异常。
- **建议**: 与行方向统一为 `pos > 0 && pos <= cells.size()`。
- **误报排除**: 两段代码被 `CPD-OFF/CPD-ON` 注释标记为刻意复制（154/210 行），差异只有条件本身，属笔误而非设计。

### [P1] CellCoordinateHelper.resolveAllCellsInColParent 读取了 rowDescendants

- **文件**: `nop-report/nop-entropy-wt/nop-entropy-master/nop-report/nop-report-core/src/main/java/io/nop/report/core/coordinate/CellCoordinateHelper.java:279-294`
- **维度**: D1（复制粘贴错误）
- **证据**:
```java
private static List<ExpandedCell> resolveAllCellsInColParent(ExpandedCell cell, String cellName) {
    ExpandedCell parent = cell.getColParent();
    if (parent == null) {
        return cell.getTable().getNamedCells(cellName);
    }
    if (cellName.equals(cell.getName())) {
        if (cell.getExpandType() == XptExpandType.c) {
            return parent.getRowDescendants().get(cellName);   // <-- 应为 getColDescendants()
        } else {
            return Collections.singletonList(cell);
        }
    }
    ...
```
- **现状**: 列方向解析中，当前格自身是列展开格且按名字引用自身时，从父格的**行**后代表中取数据，取到 null（多数情况）或错误集合（父格恰有同名行后代时）。
- **风险**: `A[;...]` 类列坐标表达式解析为 null/错误单元格集合，聚合（SUM 等）基于错误范围，结果错误或静默为空。
- **建议**: 改为 `parent.getColDescendants().get(cellName)`。
- **误报排除**: 对照 `resolveAllCellsInRowParent`（262-277 行，用 getRowDescendants）确认列版本应为 getColDescendants。

### [P1] SUMIF/COUNTIF 的双字符比较操作符退化为字符串比较，数值条件错判

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/functions/ReportFunctions.java:481-517`
- **维度**: D1（聚合条件计算错误）
- **证据**:
```java
String op = condition.substring(0, condition.indexOf(condition.replaceAll("[^<>=]", "").charAt(0)) + 1);
String condValueStr = condition.substring(op.length()).trim();
if (value instanceof Number && StringHelper.isNumber(condValueStr)) {
    ... // 数值比较
}
...
// 字符串比较
int comparison = valueStr.compareTo(condValueStr);
switch (condition.replaceAll("[^<>=]", "")) { case ">=": return comparison >= 0; ... }
```
- **现状**: 对 `">=10"` 这类双字符操作符，op 提取结果为单字符 `">"`（indexOf 只找第一个操作符字符），condValueStr 变成 `"=10"`，`isNumber` 为 false，数值分支永不命中，落入 `compareTo` 字典序比较。`"5".compareTo("10") > 0`，于是 `COUNTIF(range, ">=10")` 会把 5 判为满足、把 100 判为 `"100" >= "10"`（为 true，碰巧对）——多数位数不一致的数值判断错误。`<=`、`<>` 同理。
- **风险**: 报表内 `SUMIF/COUNTIF`（代码注释自述"以下函数为AI生成"）在 `>=`/`<=`/`<>` 条件下对数值数据给出错误统计结果，直接污染业务决策数据。
- **建议**: 解析操作符时按最长匹配（先判 `>=`/`<=`/`<>` 再单字符），并将数值比较分支的判断改为对去掉操作符后的剩余串做 `isNumber`。
- **误报排除**: 已按 `">=10"`、`">5"`、`"<>x"`、`"=5"` 逐一推演：单字符操作符 + Number 正常，双字符操作符必落字符串分支；空 catch（500 行）只是次要问题，主问题是分支选择逻辑。

### [P2] ExcelToXptModelTransformer 解析图片/图表配置时 break 中断后续元素

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/build/ExcelToXptModelTransformer.java:183-195、224-231`
- **维度**: D1（模板解析遗漏）
- **证据**:
```java
for (ExcelImage image : sheet.getImages()) {
    String desc = image.getDescription();
    if (desc == null)
        continue;
    int pos = desc.indexOf("----");
    if (pos < 0)
        break;                        // <-- 应为 continue
    ...
}
```
`parseChartModel`（229-231 行）对 charts 同样 `if (pos < 0) break;`。
- **现状**: 一旦某个图片/图表的 description 存在但不含 `----` 分隔符，循环终止，其后所有图片/图表的 `testExpr`/`dataExpr`/动态绑定配置均不解析。
- **风险**: 模板中在已配置元素之前放置任意带描述的图片（如普通 logo），后续所有配置化图片/图表静默失效，渲染结果与模板设计不符。
- **建议**: 两处 `break` 改为 `continue`。
- **误报排除**: `desc == null` 走 continue，而"有描述但无分隔符"走 break，处理不一致，可确认非有意设计。

### [P2] PdfReportRenderer 的 PDDocument 从不关闭

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/renderer/PdfReportRenderer.java:32-55`
- **维度**: D2（资源泄漏）
- **证据**:
```java
public PdfReportRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
    ...
    this.renderer = new PdfRenderer(new PDDocument());   // 构造时创建
}

public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
    ...
    renderer.saveToStream(os);                            // save 不等于 close
    ...
}
```
`PdfRenderer.saveToStream`（PdfRenderer.java:56-58）仅调用 `document.save(outputStream)`；全模块 grep `close()` 仅命中 `PdfPageRenderer.contentStream.close()` 与 ExcelRecord 的两个流关闭，无任何 `document.close()`。
- **现状**: 每次 PDF 导出创建的 `PDDocument` 在正常路径与异常路径（renderSheet 抛错时 save 都不执行）均不 close。
- **风险**: PDFBox 文档对象持有的缓存/字体子集缓冲依赖 GC 回收；异常路径下未保存的文档同样悬挂。高频导出场景内存压力放大，若未来启用 scratch-file 存储模式会泄漏临时文件。
- **建议**: `generateToStream` 用 try-finally 关闭 document（save 成功或失败均 close），或让 PdfReportRenderer 实现 Closeable 由模板输出框架管理。
- **误报排除**: 已通读 PdfRenderer/PdfSheetRenderer/PdfPageRenderer 确认无任何路径关闭 document；PdfPageRenderer（页级 contentStream）有 safeClose，说明作者知道需关闭，文档级属遗漏。

### [P2] evaluateImages 直接修改共享模板 ExcelImage，并发导出存在数据竞争

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/ExpandedSheetEvaluator.java:75-107`
- **维度**: D3（共享模型缓存并发写）
- **证据**:
```java
public void evaluateImages(ExpandedSheet sheet, List<ExcelImage> images, IXptRuntime xptRt) {
    ...
    for (ExcelImage image : images) {
        CellPosition pos = image.getAnchor().getStartPosition();
        map.computeIfAbsent(pos, k -> new ArrayList<>(1)).add(image);
        image.calcSize(sheet);          // 修改模板 image 的 left/top/width/height
    }
```
调用链 `ExpandedSheetGenerator.generateSheet` → `evaluateImages(expandedSheet, sheet.getImages(), xptRt)` 传入的是模板 sheet 的图片对象；`ExcelImage.calcSize`（nop-format/nop-excel/.../ExcelImage.java:83-91）直接写 `left/top/width/height` 字段。而模板 workbook 经 `ReportEngine.getXptModel` → `ResourceComponentManager.loadComponentModel` 缓存共享。
- **现状**: 每次生成报表时用**本次展开后的 sheet 几何**覆写**共享模板**图片对象的尺寸字段。
- **风险**: 同一报表被两个线程并发导出（Web 场景常见）时，对同一批 ExcelImage 的非同步读写产生竞争（double 字段撕裂、读到另一请求写入的尺寸），图片定位/大小可能错乱；单线程下重复生成因每次重算而不显现。
- **建议**: `evaluateImages` 中先 `anchor.copy()`/`image.cloneInstance()` 再 calcSize，或仅对新生成的 image 调 calcSize，模板对象保持只读。
- **误报排除**: 已确认 (1) ResourceComponentManager 返回缓存模型；(2) calcSize 确实原地写字段；(3) line 83 的 calcSize 作用对象是模板 images 列表元素而非副本（line 95-99 的新 image 是另一对象）。

### [P2] TextWrapHelper.wrapByWord 在单字符宽度超过可用宽度时死循环

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/utils/TextWrapHelper.java:62-106`
- **维度**: D1（死循环，潜在）
- **证据**:
```java
} else {
    // 没有空格，只能强制在当前位置折行
    lines.add(text.substring(lineStart, i));
    lineStart = i;
    i--;      // 重新处理当前字符
}
currentWidth = 0;
...
```
- **现状**: 若某单字符宽度 `font.getStringWidth(c)/1000*fontSize > maxWidth`（窄列 + 大字号 + CJK 宽字符），每次迭代都在同一 i 上触发折行分支：`substring(i,i)` 加入空串、`lineStart=i`、`i--` 后循环变量回到 i，状态完全复原——无限循环且 `lines` 无限增长直至 OOM。
- **风险**: 调用该公共工具方法（wrapMode=0/1）的任意路径可被"窄单元格 + 宽字符"输入挂死线程。当前模块内唯一调用点 `PdfStyleHelper.drawWrappedText`（PdfStyleHelper.java:124）固定传 wrapMode=2（`wrapForced` 有 `currentLine.length() > 0` 保护，不受影响），故为潜伏缺陷。
- **建议**: 无空格分支先判断 `i == lineStart`（单字符超宽），此时直接消费该字符并 continue，不再回退 i。
- **误报排除**: 已按状态机推演确认循环不变量不收敛；同时确认模块内当前无 mode 0 调用方，故降级为 P2 而非 P0。

### [P2] ReportDataSet.avg/avgBy 分母使用总行数而非有效值个数

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/dataset/ReportDataSet.java:240-262`
- **维度**: D1（聚合计算不一致）
- **证据**:
```java
public Number avg(String field) {
    List<Object> items = current();
    Number ret = 0;
    for (Object item : items) {
        Object value = getFieldValue(item, field);
        if (!StringHelper.isEmptyObject(value)) {
            ret = MathHelper.add(ret, value);
        }
    }
    return MathHelper.divide(ret, items.size());   // 分母含被跳过的空值
}
```
- **现状**: 求和时跳过空值，但分母用 `items.size()`。3 行数据含 1 个 null 时 avg = sum/3，而 `ReportFunctions.AVERAGE`（忽略非数值、按有效个数除）为 sum/2。
- **风险**: 同一报表内两种"平均"口径不一致，含空值数据集的 `ds.avg('f')` 结果系统性偏小。
- **建议**: 统计有效计数并以之作分母，或与 AVERAGE 对齐策略。
- **误报排除**: 非有意"按记录数平均"设计——若如此则求和不应跳过空值；同文件 `sum` 明确跳过。

### [P2] CellRowExpander 与 CellColExpander 的 minReuse/maxReuse 记录条件不一致

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/expand/CellRowExpander.java:129-142`、`CellColExpander.java:122-135`
- **维度**: D1（对称实现行为漂移）
- **证据**:
```java
// CellRowExpander.duplicateCell
needInsert = !isAllowReuse(cell, table, newIndex);
if (counter.minReuse == Integer.MAX_VALUE)
    counter.minReuse = newIndex;              // 无论是否真正复用都记录
if (counter.maxReuse < newIndex)
    counter.maxReuse = newIndex;
```
```java
// CellColExpander.duplicateCell
needInsert = !isAllowReuse(cell, table, newIndex);
if (!needInsert) {                            // 仅真正复用时记录
    if (counter.minReuse == Integer.MAX_VALUE) ...
```
- **现状**: 行展开器把未复用（新插入）的位置也计入 [minReuse, maxReuse] 区间，列展开器只记录实际复用位置。该区间被 `skipExtendSpan`（行:93-102 / 列:90-99）用于计算兄弟共享导致的延展扣减，两种口径计算出的 `incSpan - skipExtendSpan` 不同。
- **风险**: 行/列两个本应对称的算法在"兄弟节点共享展开空间"场景下合并区域（mergeDown/mergeAcross）计算结果不一致，至少一方向是错的，输出表格合并格式错误。
- **建议**: 以列版本（仅记录实际复用位）为准统一两处；补一个行/列对称的兄弟展开回归用例。
- **误报排除**: 两文件其余逻辑逐行对齐，仅此块存在结构性差异；无法从代码判定哪个正确，故按"契约漂移"定级 P2 而非直接断言某一方为 bug。

### [P2] ExcelRecordOutput 异常/未完成路径资源与数据处置不当

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/record/ExcelRecordOutput.java:253-272`
- **维度**: D2（资源管理）
- **证据**:
```java
public void close() throws IOException {
    if (tempDir != null && genTrailer) {     // 仅 endWrite() 之后才走完整收尾
        closeDataSheetWriter();
        ...
        ResourceHelper.zipDir(new FileResource(tempDir), resource, options);
    }
    this.clearDir();
}
```
- **现状**: (1) 写入中途出错后调用方只 `close()` 时，`genTrailer=false`，`out`（ExcelSheetWriteSupport，持有临时目录中的打开文件流）不关闭即删除 tempDir——POSIX 下可删，Windows 下删除可能失败导致临时目录残留；(2) `beginWrite` 后未 `endWrite` 即 close，目标 resource 完全不生成且无任何日志/异常提示，调用方若未检查易误认为导出成功。
- **风险**: 大文件导出中断后的句柄/临时目录残留；静默丢数据。
- **建议**: close() 中对 `out != null` 无条件 `closeDataSheetWriter()`（吞掉二次异常并记日志）；未完成即关闭时记录 warn。
- **误报排除**: 构造器异常路径已有 `clearDir()` 兜底；`writeRow` 为即时流式写出（已核 nop-ooxml ExcelSheetWriteSupport.genRow），复用 ExcelRow 无缓冲问题，均不列入。

### [P3] AVERAGE 空数值集返回 NaN 而非错误

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/functions/ReportFunctions.java:133-152`
- **维度**: D1
- **证据**: `return MathHelper.divide(ret, count);`，count=0 时 `MathHelper.divide` 返回 `NaN` 并仅记 info 日志（nop-kernel/nop-commons/.../MathHelper.java:850-853）。
- **现状**: 空集 AVERAGE 输出 NaN，导出后以 "NaN" 文本呈现；注释自知"Excel会显示除零错误"但未对齐。
- **风险**: 报表出现 NaN 字样，观感与语义均不佳。
- **建议**: count==0 时返回 null。
- **误报排除**: 已核对 MathHelper.divide 对 0 除的行为确认返回 NaN。

### [P3] compareWithOperator 吞掉全部异常

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/functions/ReportFunctions.java:482-501`
- **维度**: D4
- **证据**: `} catch (Exception expected) { }`——操作符解析段任何异常（如下标越界）都被吞掉后落入字符串比较分支。
- **现状**: 唯一的空 catch（全模块 grep 仅此一处），变量名 `expected` 暗示作者知道会异常但未收敛输入。
- **风险**: 掩盖操作符解析缺陷（与 P1-7 同源），排障困难。
- **建议**: 解析逻辑显式化后删除该 catch。
- **误报排除**: 无。

### [P3] FontManager 线程安全与空默认字体隐患

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/font/FontManager.java:58-79、81-83`
- **维度**: D3
- **证据**: `inited` 非 volatile；`init()` synchronized 但 `registerSystemFonts` 在构造外无同步保护（懒加载于首次 getFont 时执行，systemFonts 为普通 CaseInsensitiveMap）；`addFontAlias` 公开方法无同步；`init()` 失败时 `defaultFont` 保持 null，`getFont` 兜底 `return getDefaultFont()` 可能返回 null 导致渲染 NPE。
- **现状**: 首次并发 PDF 导出理论上可看到半初始化状态；默认字体创建失败仅记 error 后继续。
- **风险**: 低概率初始化竞争；字体环境异常时 NPE 代替明确报错。
- **建议**: inited 加 volatile 或 instance 采用静态 holder；getDefaultFont 为 null 时抛带上下文的 NopException。
- **误报排除**: init 自身有 synchronized 且幂等，主要风险在可见性与 addFontAlias 并发，均已在证据中限定。

### [P3] XptModelToExcelTransformer.transform 为空实现且无调用方

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/build/XptModelToExcelTransformer.java:15-19`
- **维度**: D8（契约与实现不符）
- **证据**:
```java
/** 将xpt报表模型保存为Excel格式 */
public class XptModelToExcelTransformer {
    public void transform(ExcelWorkbook wk) {
    }
}
```
- **现状**: 类注释承诺的功能未实现；全库 grep 无任何调用（实际保存路径为 `XptModelLoader.saveObjectToResource` → `ExcelHelper.saveExcel`）。
- **风险**: 死代码误导维护者；若被未来调用将静默无效果。
- **建议**: 删除该类或补齐实现。
- **误报排除**: 已 grep 全仓库确认无调用点。

### [P3] ExcelFormulaParser.s_filterTpl 静态字段未使用

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/expr/ExcelFormulaParser.java:62-66`
- **维度**: D4/D6（残留代码）
- **证据**: `static Expression s_filterTpl; static { s_filterTpl = SimpleExprParser.newDefault().parseExpr(null, "e=>body"); }`——全模块仅定义与赋值，无读取。
- **现状**: 遗留死代码。
- **风险**: 无功能风险，维护噪音。
- **建议**: 删除。
- **误报排除**: grep 确认无引用。

### [P3] ReportExtFunctions.QRCODE 自赋值无效语句

- **文件**: `nop-report/nop-report-ext/src/main/java/io/nop/report/ext/ReportExtFunctions.java:56`
- **维度**: D1（疑似笔误，无功能影响）
- **证据**: `image.setImgType(image.getImgType());`（52 行已 `image.setImgType(options.getImgType())`）。
- **现状**: 疑似想重复设置 options 的类型，实际为自赋值空操作。
- **风险**: 无（52 行已正确设置），但暗示此处逻辑曾改动且未清理。
- **建议**: 删除该行。
- **误报排除**: 确认 52 行已覆盖该意图，无行为差异。

### [P3] ExpandedRow/ExpandedCol 的 getRowIndex/getColIndex 在展开期为 O(n)

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedRow.java:265-269`、`ExpandedCol.java`
- **维度**: D6
- **证据**: `return table.getRows().indexOf(this);`——`assignedRowIndex` 仅在展开完成后由 `assignRowIndexAndColIndex` 设置；展开期间 Expander 多次调用 `cell.getRowIndex()`。
- **现状**: 大报表（数万行）展开阶段每次取行号都是线性扫描，整体近似 O(n²)。
- **风险**: 大数据量报表展开耗时显著放大。
- **建议**: 插入行时同步维护行索引或在 Expander 内缓存局部索引。
- **误报排除**: 已确认 indexOf 路径在 assignRowIndexAndColIndex 之前被 expand/duplicateCell 调用。

### [P3] ExcelRecordInput 将整个 sheet 全量载入内存

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/record/ExcelRecordInput.java:87-114`
- **维度**: D6
- **证据**: `beforeRead` 中 `list = collector.getResult()`（HeaderListRecordOutput 收集全表为 List），`next()` 从 list 顺序取。
- **现状**: 导入大 Excel 时内存占用与文件行数成正比。
- **风险**: 超大文件导入 OOM 风险；与 IRecordInput 流式语义的预期有差距。
- **建议**: 如需支持大文件，改为基于 XlsxToRecordOutput 的流式迭代。
- **误报排除**: 属设计取舍（简单实现），列为隐患而非缺陷。

### [P3] ReportEngine.getRendererForExcelData 对空模板无防护

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/ReportEngine.java:102-105`
- **维度**: D1（边界）
- **证据**: `ExcelSheet sheetTpl = tpl.getSheets().get(0);`——模板无 sheet 时抛 IndexOutOfBoundsException，无 NopException 包装与参数上下文。
- **风险**: 传错模板时报错信息无法定位。
- **建议**: 先判空并抛 ERR_XPT 类错误携带 resource path。
- **误报排除**: 无。

### [P3] PdfStyleHelper 颜色解析对非 hex 颜色串会抛未包装异常

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/utils/PdfStyleHelper.java:22-25、98-101`
- **维度**: D4/D1
- **证据**: `convertColor` 直接调用 `ColorHelper.toNormalizedRgb(color)`，后者对长度不为 6 的串抛 `IllegalArgumentException`（nop-format/nop-excel/.../ColorHelper.java:114-116）；同文件 `parseColor`（274-312 行）却支持 "red"/"rgb(...)" 等命名色。`drawUnwrappedText`（99 行）调用 convertColor 后没有像 `drawWrappedText`（141 行）那样判 null。
- **现状**: 字体色/填充色若为命名色串（与边框色解析口径不一致），PDF 导出抛裸 IllegalArgumentException。
- **风险**: 特定样式模板导出失败且报错缺上下文。
- **建议**: convertColor 内捕获并回退默认色，或统一两套颜色解析入口。
- **误报排除**: 已核对 ColorHelper 源码确认非法格式必抛。

### [P3] XptWordTableRenderer.renderCell 对无 w:p 的单元格 NPE 风险

- **文件**: `nop-report/nop-report-docx/src/main/java/io/nop/report/docx/renderer/XptWordTableRenderer.java:137-138、94`
- **维度**: D1（边界）
- **证据**: `XNode p = tc.childByTag("w:p"); XNode pPr = p.childByTag("w:pPr");`——`childByTag` 找不到返回 null 时下一行 NPE。另 `renderProxyCell` 94 行声明 `XNode p` 后未使用。
- **现状**: OOXML 规范要求 tc 含 w:p，真实 docx 解析路径基本满足；但模型经程序组装（非标准文档）时可能为空。
- **风险**: 边界输入下渲染 NPE。
- **建议**: p 为 null 时输出空段落节点；删除未用变量。
- **误报排除**: 已确认 XNode.childByTag 的 null 返回语义。

## 专项检查结论（未命中项说明）

- **D5 安全**: demo 入口 `reportName` 均有 `StringHelper.isValidVPath` 校验（ReportDemoBizModel.java:99、134、169），未发现路径遍历入口；模块内无 CSV 导出面（无公式注入面）；`XptRuntime.cells()/evalExcelFormula()` 接受字符串表达式属报表引擎设计内行为（表达式作者权限范畴）。未发现问题。
- **D7 平台规范**: 全模块 grep `@Inject private`、`@Value(` 零命中（demo 的 `@Inject IReportEngine reportEngine` 为 package-private，合规）；无 bare `RuntimeException`、无 `printStackTrace`；异常以 `NopException` + `XptErrors/ReportPdfErrors` 错误码 + `.param(...)` 为主（符合两档策略第一档）；bean 均在 `_vfs/**/beans.xml` 显式定义（report-defaults/report-pdf-defaults/report-ext/app-report-demo 等）。未发现违规。
- **D8 接口一致性**: `XptRuntime` 实现 `IXptRuntime`、`ReportEngine` 实现 `IReportEngine`（含继承 `IReportRendererRegistry`）方法齐全；demo 调用的 `getHtmlRenderer/getRenderer` 为接口 default 方法，存在。除 P3 空实现类外未发现契约不匹配。
- **D2 资源**: Excel 导出链路（ExcelRecordOutput 构造异常清理、downloadXlsx 定时删除临时文件、demo download 定时删除）总体处置得当；主要问题即前述 PDF document 与 ExcelRecordOutput 异常路径两条。
- **D3 并发**: `XptRuntime.getCache` 的 synchronized 为惰性初始化保护，可接受；`ReportFunctionProvider.INSTANCE` 静态注册仅在启动期（ReportExtInitializer @PostConstruct）执行，运行期只读；模板模型共享的主要风险点即前述 ExcelImage 一条。
