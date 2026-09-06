# nop-report 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-report
- 文件数: 143（src/main/java，剔除 `_` 前缀生成文件与 `_gen/`）
- 覆盖范围声明: 深读 64 个文件（nop-report-core 的引擎/数据集/聚合函数/坐标/展开器/构建/导入/记录/渲染共 45 个，nop-report-pdf 全部 11 个，nop-report-docx 全部 4 个，service/demo 层 4 个），重点覆盖任务指定的数据集与聚合、报表引擎执行（层次展开/交叉表/分组）、excel/pdf 导出四大领域；其余 79 个文件（nop-report-api 的 Input/Output Bean、crud 接口、nop-report-dao 的实体/Biz 接口、常量/错误码类等声明式代码）仅做模式扫描（`@Inject`/Spring 依赖/`RuntimeException`/`SimpleDateFormat`/`Random`/catch 块/beans.xml 注册），未逐行深读。模式扫描（grep）覆盖 100% 文件，深读覆盖约 45%。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 10 |
| P3 | 10 |

## 发现列表

### [P0] COUNTIF/SUMIF 条件操作符解析错误，多字符操作符（>=/<=/<>）触发字典序比较导致统计结果错误

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/functions/ReportFunctions.java:483`
- **维度**: D1
- **证据**:
```java
// 第481-499行
private static boolean compareWithOperator(Object value, String condition) {
    try {
        String op = condition.substring(0, condition.indexOf(condition.replaceAll("[^<>=]", "").charAt(0)) + 1);
        String condValueStr = condition.substring(op.length()).trim();

        if (value instanceof Number && StringHelper.isNumber(condValueStr)) {
            double numValue = ((Number) value).doubleValue();
            double condValue = Double.parseDouble(condValueStr);
            ...
        }
    } catch (Exception expected) {
    }
    // 字符串比较
    String valueStr = value != null ? value.toString() : "";
    String condValueStr = condition.replaceFirst("[<>=]+", "").trim();
    int comparison = valueStr.compareTo(condValueStr);
```
- **现状**: `op` 的解析逻辑取"第一个操作符字符首次出现位置 +1"作为操作符截取长度。对 `">=10"`：`replaceAll("[^<>=]","")` 得 `">="`，`charAt(0)` 是 `'>'`，`indexOf('>')` = 0，故 `op = ">"`（只截了一个字符），`condValueStr = "=10"`。`StringHelper.isNumber("=10")` 为 false，数值比较分支被整体跳过，落到字符串字典序比较分支。
- **风险**: 数值单元格按字符串字典序比较数字：`SUMIF(A, ">=10")` 时值为 9 的单元格 `"9".compareTo("10") > 0` 判为满足 `9 >= 10`，被错误计入；反之 `COUNTIF(A, "<=9")` 时 10 会因 `"10".compareTo("9") < 0` 被计入（10 <= 9 为假却判真）。所有 `>=`、`<=`、`<>` 条件只要比较多位数与单位数（或任意长度不同的数字字符串）结果就是错的，且异常被 `catch (Exception expected)` 静默吞掉。这是导出报表聚合数据的直接错误，触发路径现实（模板中写 `SUMIF(B2:B10, ">=100")` 即触发）。
- **建议**: 用正则 `^(>=|<=|<>|=|>|<)` 显式提取操作符后再剥离数字部分；数值路径失败时不应回退到字典序比较，而应返回 false 或抛出明确的格式错误。
- **误报排除**: 已核对 `StringHelper.isNumber`（nop-commons，对 `"=10"` 返回 false）确认数值分支确实被跳过；已核对该方法唯一入口 `matchesCondition`（第 469-471 行 `startsWith(">")` 等判断保证多字符操作符会进入此方法）；`COUNTIF`/`SUMIF` 均经 `matchesCondition` 调用。单字符操作符（`>10`、`<5`、`=3`）路径正常，问题仅在多字符操作符。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`compareWithOperator` 改为显式匹配多字符操作符（`>=`/`<=`/`<>` 优先，否则取单字符）后再截取操作数，数值比较路径可正确进入。编写回归测试时发现相邻缺陷并一并修复：`SUMIF` 未指定 `sumRange` 时 `sumIt` 复用 `rangeIt` 同一迭代器导致元素错位（奇偶配对）甚至 `NoSuchElementException`，现改为对 `range` 独立迭代。测试：`TestReportFunctions#testCountIfMultiCharOperator`、`#testSumIfMultiCharOperator`。红验证：stash `ReportFunctions.java` 后 `COUNTIF([5,9,10,11,15],">=10")` 因字典序比较返回 5（期望 3），`SUMIF` 抛 `NoSuchElementException`。

### [P1] ReportDataSet.avg/avgBy 分母使用全部记录数而非非空值个数，含空值时平均值被稀释

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/dataset/ReportDataSet.java:240-262`
- **维度**: D1
- **证据**:
```java
public Number avg(String field) {
    List<Object> items = current();
    Number ret = 0;
    for (Object item : items) {
        Object value = getFieldValue(item, field);
        if (!StringHelper.isEmptyObject(value)) {   // 分子跳过空值
            ret = MathHelper.add(ret, value);
        }
    }
    return MathHelper.divide(ret, items.size());    // 分母却是全部记录数
}
```
- **现状**: 分子累加时跳过 `isEmptyObject`（null/空串）的记录，但分母固定为 `items.size()`。`avgBy`（第 252-262 行）同样实现。同文件 `AVERAGE` 风格的函数（ReportFunctions.AVERAGE 第 134-152 行）分母只统计数值个数，二者语义不一致。
- **风险**: 数据集 10 条记录中 4 条该字段为 null 时，`avg` 返回 sum/10 而不是 sum/6，报表平均值系统性偏小；空数据集时 `divide(0,0)` 返回 NaN。模板表达式 `ds.avg(field)` 直接使用该 API，产生静默数据错误。
- **建议**: 在循环中同时计数非空（或数值）个数，用该计数做分母；空集返回 null 或 0。
- **误报排除**: 已读完整类确认无其他地方对 avg 结果再除以有效数；`MathHelper.divide`（nop-commons 第 842-861 行）确认除零返回 NaN 不抛异常；`DynamicReportDataSet.current()` 返回的是过滤后的当前列表，分母与分子使用同一 `items` 引用，问题确认为计数口径而非数据不同步。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`avg`/`avgBy` 分母改为非空值计数（与分子同一口径）；空集/全空时返回 `null`（原来 `divide(0,0)` 返回 NaN），与同类 `AVERAGE` 函数及 `min/max` 的空集语义一致。测试：`TestReportDataSet#testAvgSkipsNullValues`、`#testAvgBySkipsNullValues`、`#testAvgAllNullReturnsNull`、`#testAvgEmptyReturnsNull`。红验证：stash `ReportDataSet.java` 后 `avg(5,null,1)` 返回 2（期望 3）、全空/空集分别返回 0 与 NaN（期望 null）。

### [P1] ExpandedCell.childCell 的 colDescendants 分支求值后返回 null（复制粘贴错误），列方向子格取值永远为 null

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedCell.java:735-753`
- **维度**: D1
- **证据**:
```java
public ExpandedCell childCell(String cellName, IXptRuntime xptRt) {
    if (rowDescendants != null) {
        List<ExpandedCell> cells = rowDescendants.get(cellName);
        if (cells != null && !cells.isEmpty()) {
            ExpandedCell cell = cells.get(0);
            xptRt.evaluateCell(cell);
            return cell;              // row 分支返回 cell
        }
    }
    if (colDescendants != null) {
        List<ExpandedCell> cells = colDescendants.get(cellName);
        if (cells != null && !cells.isEmpty()) {
            ExpandedCell cell = cells.get(0);
            xptRt.evaluateCell(cell); // 白白求值
            return null;              // col 分支却返回 null
        }
    }
    return null;
}
```
- **现状**: 列方向后代分支取出单元格、执行 `evaluateCell` 后返回 `null` 而不是 `cell`。
- **风险**: `childValue`（第 755-758 行）与 `@EvalMethod cv(scope, cellName)`（第 760-764 行）在只有列方向子格（交叉表列展开场景）时永远返回 null，模板表达式 `cell.cv('B')` 取不到值且无任何报错；同时 `evaluateCell` 的求值结果被丢弃。
- **建议**: 将该分支改为 `return cell;`。
- **误报排除**: 已通读 `ExpandedCell` 全类，两个分支结构完全对称仅返回值不同，且求值动作存在说明作者预期要使用该 cell；已核对 `cv`/`childValue` 调用链无其他兜底路径（`childSet` 第 721-733 行的对应实现两个分支都返回 `new ExpandedCellSet(...)`，进一步印证此处是笔误）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。col 分支改为 `return cell;`（一行笔误修正）。测试：`TestExpandedCell#testChildCellFromColDescendants`（配 row 方向对照用例 `#testChildCellFromRowDescendants`，IXptRuntime 用动态代理桩）。红验证：stash `ExpandedCell.java` 后列方向子格返回 null（期望返回子格实例）。

### [P1] ExpandedCell.getExpandableRowParent 在 row 方向父链上错误递归调用 getExpandableColParent

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedCell.java:622-630`
- **维度**: D1
- **证据**:
```java
public ExpandedCell getExpandableRowParent() {
    if (rowParent == null)
        return null;
    if (rowParent.getModel() == null)
        return null;
    if (rowParent.getModel().getExpandType() == null)
        return rowParent.getExpandableColParent();   // 应为 getExpandableRowParent()
    return rowParent;
}
```
- **现状**: 当直接 rowParent 不可展开时应沿 row 方向继续上溯，但代码转向了 `getExpandableColParent()`（列方向父链）。对比同文件 `getExpandableColParent()`（第 612-620 行）正确递归自身。
- **风险**: 唯一调用方 `CellRowExpander.isAllowReuse`（CellRowExpander.java:160-171）用它判断两个兄弟单元格是否属于同一可展开行父，从而决定行展开时能否复用已插入的行（`expandInplaceCount` 预留行场景）。转向列父链后判定结果错误：可能漏判复用（插入多余空行、布局错位），也可能误判复用（两个不同行组共享同一列祖先时复用了不该复用的行，数据覆盖）。
- **建议**: 改为 `rowParent.getExpandableRowParent()`。
- **误报排除**: 已 grep 确认该方法唯一调用方是 `CellRowExpander.isAllowReuse`；已读 `CellColExpander.isAllowReuse` 确认 col 方向使用的是正确的 `getExpandableColParent`，两个方向的递归实现不对称仅此一处。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。改为 `rowParent.getExpandableRowParent()` 沿 row 方向递归。测试：`TestExpandedCell#testGetExpandableRowParentSkipsNonExpandable`（A 可展开←B 不可展开←C，从 C 上溯应命中 A；配 col 方向对照与无父格/无 model 边界用例）。红验证：stash `ExpandedCell.java` 后返回 null（转向列父链后落空），期望返回 A。

### [P1] CellCoordinateHelper.resolveAllCellsInColParent 在列方向查找中误用 getRowDescendants

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/coordinate/CellCoordinateHelper.java:279-294`
- **维度**: D1
- **证据**:
```java
private static List<ExpandedCell> resolveAllCellsInColParent(ExpandedCell cell, String cellName) {
    ExpandedCell parent = cell.getColParent();
    if (parent == null) {
        return cell.getTable().getNamedCells(cellName);
    }
    if (cellName.equals(cell.getName())) {
        if (cell.getExpandType() == XptExpandType.c) {
            return parent.getRowDescendants().get(cellName);   // 应为 getColDescendants()
        } else {
            return Collections.singletonList(cell);
        }
    }
    ...
}
```
- **现状**: 对比 row 方向的 `resolveAllCellsInRowParent`（第 262-277 行）使用 `parent.getRowDescendants()`（正确），col 方向这里却在列父格的 **row** 后代表里查找列方向子格。
- **风险**: 层次坐标表达式中列坐标首项为当前列展开单元格自身（如 `B[B:+1]` 这类相对列引用）时，`parent.getRowDescendants().get(cellName)` 几乎必然返回 null，导致 `resolveColCoordinates` 返回 null → `resolveLayerCoordinate` 返回 null → `CellLayerCoordinateExecutable.execute` 返回 null（见下一条 P2 的连锁后果），单元格取值错误或 NPE。
- **建议**: 改为 `parent.getColDescendants().get(cellName)`。
- **误报排除**: 已读 `ExpandedCell.addRowChild/addColChild`（第 778-806 行）确认 row/col 后代表分别只在各自方向的父链上维护；已核对 row 版对应实现；该文件第 154/210 行的 `CPD-OFF/CPD-ON` 注释表明此段是复制代码，复制后方向未改。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。改为 `parent.getColDescendants().get(cellName)`。测试：`TestCellCoordinate#testResolveLayerCoordinateFromColParent`（构造 P→B(b1,b2)→X 列展开结构，从 x1 解析 `X[B:1]` 应得到 x1）。红验证：stash `CellCoordinateHelper.java` 后 `resolveLayerCoordinate` 返回 null（在列父格的 row 后代表里查列子格必然落空）。

### [P1] FontManager.registerSystemFonts 私有方法从未被调用，Standard14 字体表恒为空，PDF 字体解析全部回退 Helvetica

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/font/FontManager.java:42-52`
- **维度**: D1/D8
- **证据**:
```java
private void registerSystemFonts() {           // 全模块 grep 无任何调用
    for (Standard14Fonts.FontName fontName : Standard14Fonts.FontName.values()) {
        systemFonts.put(fontName.getName(), new PDType1Font(fontName));
    }
    systemFonts.put("Times New Roman", new PDType1Font(...TIMES_ROMAN));
    systemFonts.put("Helvetica-BoldItalic", ...);
    ...
}
...
protected synchronized void init() {           // init() 也未调用 registerSystemFonts
    ...
    defaultFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    inited = true;
}
```
- **现状**: `registerSystemFonts()` 是 private 且无任何调用点（grep 全 nop-report 模块仅定义处一处命中）；`init()` 也没有调用它。`systemFonts` 永远是空 Map，`getSystemFont()` 永远返回 null。另外 `fontNameAliases`（第 32 行）唯一写入口 `addFontAlias`（第 81-83 行）也从未被调用，且该 Map 无任何读取处（只写不读的死代码）。
- **风险**: `getFont()` 的所有 Standard14 字体（Times、Courier 等）及 bold/italic 变体（如 `Helvetica-Bold`）都无法从缓存命中，全部走 `loadFont` 尝试 `/fonts/Helvetica-Bold.ttf` 等文件（不存在）再回退 `getDefaultFont()`（常规 Helvetica）。结果是 PDF 导出中所有加粗/斜体样式静默丢失、字体族全部变成 Helvetica，只打 WARN 日志，属于功能性失效。
- **建议**: 在 `init()` 中调用 `registerSystemFonts()`；要么实现 `fontNameAliases` 的读取逻辑要么删除。
- **误报排除**: 已 grep 全仓库 nop-report 模块确认 `registerSystemFonts`、`addFontAlias` 无调用点（含 resources 下 beans.xml 也无相关配置）；已读 `getFont/getSystemFont/loadFont` 完整调用链确认 systemFonts 为空时的回退路径。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`init()` 中调用 `registerSystemFonts()` 注册 Standard14 字体表（PDFBox 3 的 PDType1Font Standard14 不绑定单个 PDDocument，可安全跨文档共享）；`getFont()` 在查找前应用 `fontNameAliases` 别名映射，使 `addFontAlias` 公共 API 真正生效。测试：`TestFontManager#testStandard14FontsRegistered`、`#testBoldVariantResolvedFromSystemFonts`、`#testFontAlias`（JunitBaseTestCase 环境，e2e 由 `TestPdfReportRenderer#testRender` 连带验证加粗渲染）。红验证：stash `FontManager.java` 后 `Helvetica` 加粗/Times New Roman/别名全部回退测试环境 default.ttf（实际得到 SourceHanSerifCN-Light，期望 Helvetica-Bold/Times-Roman/Courier）。

### [P2] CellLayerCoordinateExecutable / FilterCellSetExecutable 可返回 null，违反 ICellSetExecutable 返回值契约，下游存在 NPE 路径

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/expr/CellLayerCoordinateExecutable.java:68-77`
- **维度**: D8/D1
- **证据**:
```java
List<ExpandedCell> cells = absolute ? cell.getTable().getNamedCells(cell.getName()) :
        CellCoordinateHelper.resolveLayerCoordinate(cell, layerCoordinate);
if (cells == null)
    return null;                       // 接口方法声明返回 ExpandedCellSet
```
- **现状**: `execute` 声明返回 `ExpandedCellSet`，坐标解析失败时返回 null。对比 `CellRangeExecutable`（同目录第 59-77 行）任何情况都返回集合。`FilterCellSetExecutable.execute`（第 51-56 行）原样透传 null。
- **风险**: 消费方分化：`ReportFormulaGenerator.transformCellSet` 对 null 做了防御（打印 `''`）；但 `xptRt.cells(cellExpr)`（XptRuntime.java:276-281）直接强转返回，模板表达式进一步调用 `.evaluateAll()` 等会 NPE；`PROPORTION`/`RANK` 等 `@EvalMethod` 的 `ExpandedCellSet` 参数若绑定为 null，函数内 `cell.getValue()` 直接 NPE（ReportFunctions.java:219/247）。而坐标解析失败的现实诱因之一正是上一条 `resolveAllCellsInColParent` 的笔误。
- **建议**: 解析失败时返回空 `ExpandedCellSet`（与 CellRangeExecutable 一致），把"解析不到"语义收敛为空集合。
- **误报排除**: 已读 `resolveLayerCoordinate`（CellCoordinateHelper.java:28-44）确认其存在多个返回 null 的分支；已读 ReportFormulaGenerator 与 XptRuntime.cells 两种消费方确认防御不一致。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`CellLayerCoordinateExecutable.execute` 解析失败时返回空 `ExpandedCellSet`（null 分支改为 `cells = Collections.emptyList()` 继续走统一构造）；`FilterCellSetExecutable.execute` 对 null 同样兜底返回空集合，两者均与 `CellRangeExecutable` 的恒返回集合契约对齐。已核对唯一防御 null 的消费方 `ReportFormulaGenerator.transformCellSet` 对 null 与空集合处理完全一致（都打印 `''`），无兼容性差异。测试：`TestCellLayerCoordinateExecutable#testResolveFailureReturnsEmptySet`（坐标引用不存在的单元格，断言返回空集合而非 null）。红验证：stash 两个 Executable 后返回 null（`expected: not <null>`）。

### [P2] resolveColCoordinates 与 resolveRowCoordinates 边界检查不对称：缺 isEmpty 检查且 pos=0 时可触发 get(-1) 越界

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/coordinate/CellCoordinateHelper.java:197-201`
- **维度**: D1
- **证据**:
```java
// col 版（第197-200行）
if (pos < cells.size()) {
    resolvedCell = cells.get(pos - 1);        // pos=0 时 get(-1) -> IndexOutOfBoundsException
}
// row 版（第89-91行）
if (pos > 0 && pos <= cells.size()) {         // 有 pos>0 保护且允许 pos==size
    resolvedCell = cells.get(pos - 1);
}
```
- **现状**: row 版入口检查 `cells == null || cells.isEmpty()`（第 60 行）且数值分支有 `pos > 0` 保护；col 版入口只检查 `cells == null`（第 169 行），数值分支只检查 `pos < cells.size()`。
- **风险**: 层次坐标写 `A[B:0]`（position 显式为 0）且走 col 方向时 `cells.get(-1)` 抛 IndexOutOfBoundsException，报表生成崩溃；`pos == cells.size()` 时 row 版取最后一个元素而 col 版静默保留上一轮 resolvedCell（off-by-one 行为分叉）。此外空列表时 col 版直接进入 get 也有越界风险。
- **建议**: 将 col 版边界条件改为与 row 版完全一致：`pos > 0 && pos <= cells.size()`，入口补 `cells.isEmpty()` 检查。
- **误报排除**: 已对照阅读两个方法的完整实现（第 46-100 与 155-209 行），除 `CPD-OFF` 标注的复制区外仅上述两处不一致；`CellCoordinate`（position 默认 0，parser 第 113-117 行允许 pos=0 且不设置 relative）确认 `A[B:0]` 是可构造输入。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。col 版数值分支边界改为与 row 版完全一致的 `pos > 0 && pos <= cells.size()`，入口检查补 `cells.isEmpty()`（与 log 返回 null 分支合并）。测试：`TestCellCoordinate#testResolveColCoordinatesPositionZero`（pos=0 应返回 null 而非越界）、`#testResolveColCoordinatesPositionLast`（pos==size 取最后一个元素，对齐 row 方向）。红验证：stash `CellCoordinateHelper.java` 后 pos=0 抛 `IndexOutOfBoundsException: Index: -1, Size: 1`，pos==size 场景静默返回 null（保留上一轮 resolvedCell 的 off-by-one 分叉同时暴露）。

### [P2] PdfReportRenderer 的 PDDocument 从不关闭

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/renderer/PdfReportRenderer.java:39-55`
- **维度**: D2
- **证据**:
```java
public PdfReportRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
    ...
    this.renderer = new PdfRenderer(new PDDocument());   // 创建后无 close 路径
}

@Override
public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
    ...
    sheetGenerator.generate(context, this::renderSheet); // 任意 sheet 抛异常时直接上抛
    renderer.saveToStream(os);                           // document.save 不关闭文档
    ...
}
```
- **现状**: `PDDocument` 实现 `Closeable`，但整个类没有调用 `document.close()`；`PdfRenderer.saveToStream` 只调 `document.save(outputStream)`。
- **风险**: 每次 PDF 生成泄漏一个 PDDocument（内含 COSDocument 结构、字体缓存、内存映像）。渲染中途抛异常时连 save 都不执行，泄漏更完整。长期运行的导出服务存在资源累积压力。
- **建议**: `generateToStream` 用 try-with-resources 管理 PDDocument（或 finally 中 close），保证异常路径也释放。
- **误报排除**: 已 grep 模块内所有 `close()`/`saveToStream` 调用点，确认无其他地方代为关闭该文档；`PdfPageRenderer` 只关闭自己的 contentStream。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`generateToStream` 主体移入 try/finally，finally 中 `renderer.getDocument().close()`，保证渲染中途抛异常时 PDDocument（字体/图像缓存、COS 结构）也释放。测试：`TestPdfReportRenderer#testDocumentClosedAfterGenerate`（生成后断言 `COSDocument.isClosed()`）。红验证：stash `PdfReportRenderer.java` 后生成成功但 `isClosed()` 为 false（`expected: <true> but was: <false>`）。

### [P2] ExcelRecordOutput.close() 在未完成写入或异常路径下不关闭写出流、不清理临时目录

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/record/ExcelRecordOutput.java:253-272`
- **维度**: D2
- **证据**:
```java
@Override
public void close() throws IOException {
    if (tempDir != null && genTrailer) {      // 只有正常 endWrite 之后 genTrailer 才为 true
        closeDataSheetWriter();
        ...
    }
    this.clearDir();                          // 若上方抛 IOException，此句不执行
}
```
- **现状**: `closeDataSheetWriter()`（内含 `out.close()`，第 227-235 行）仅在 `genTrailer == true`（即 `endWrite` 已被调用）时执行；`clearDir()` 不在 finally 中。
- **风险**: (1) 写入过程中发生异常、调用方直接 close 时，`ExcelSheetWriteSupport` 内部文件流不关闭，Windows 上临时目录删除也会失败；(2) `generateToDir`/`zipDir` 抛 IOException 时 `clearDir()` 被跳过，`ResourceHelper.getTempResource("xlsx")` 临时目录泄漏，反复失败累积垃圾文件。
- **建议**: close() 中无条件先 `closeDataSheetWriter()`（幂等），并将 `clearDir()` 放入 finally。
- **误报排除**: 已读全类：`out` 在 `newDataSheetWriter` 创建、仅 `closeDataSheetWriter` 关闭；`genTrailer` 仅在 `endWrite` 置 true；确认异常路径无其他清理钩子。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`close()` 重构为 try/finally：进入 try 先无条件调用幂等的 `closeDataSheetWriter()`（未调用过 `endWrite` 也关闭数据 sheet 输出流，`out` 置 null 保证幂等），`clearDir()` 移入 finally（异常路径也清理临时目录）。测试：`TestExcelResourceIO#testCloseWithoutEndWriteClosesSheetWriter`（未 endWrite 直接 close，反射断言 `out` 已清空）、`#testCloseClearsTempDirOnFailure`（目标资源设为目录强制 zip 输出失败，断言抛 NopException 且 tempDir 被清理）。红验证：stash `ExcelRecordOutput.java` 后 `out` 字段仍为 ExcelSheetWriteSupport 实例（流未关闭）、失败路径临时目录残留存在。

### [P2] ExcelToXptModelTransformer 解析图片/图表扩展配置时首个无 "----" 描述的元素会中断后续所有元素的解析

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/build/ExcelToXptModelTransformer.java:183-195`
- **维度**: D1
- **证据**:
```java
for (ExcelImage image : sheet.getImages()) {
    String desc = image.getDescription();
    if (desc == null)
        continue;
    int pos = desc.indexOf("----");
    if (pos < 0)
        break;                               // 应为 continue：直接放弃后续所有图片
    ...
}
```
- **现状**: `parseImageModel` 中第一个不含 `"----"` 分隔符的图片描述会 `break` 整个循环；`parseChartModel`（第 228-230 行）同样 `break`。
- **风险**: 模板中多张图片/多个图表时，只要排在前面的某一个没有扩展配置，后面所有图片的 `testExpr`/`dataExpr`、图表的动态绑定配置都被静默忽略，报表图片渲染行为与模板设计不符且无任何告警。
- **建议**: 两个循环的 `pos < 0` 分支改为 `continue`（注意 parseChartModel 中还有 `realDesc` 截断逻辑需一并保留）。
- **误报排除**: 已读两个方法完整实现确认循环结构与 break 位置；图片/图表顺序取决于 Excel 解析顺序，无法保证带配置的元素排在前面，break 语义不成立。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`parseImageModel`/`parseChartModel` 两处 `pos < 0` 分支由 `break` 改为 `continue`（chart 的 `realDesc` 截断逻辑保留）。测试：`TestExcelToXptModelTransformer#testParseImageModelSkipsNoConfigImage`（首个图片无 "----" 配置时后续图片 testExpr 仍被解析）、`#testParseChartModelSkipsNoConfigChart`（首个图表无分隔符时后续图表 dynamicBindings 仍创建、描述仍截断）。红验证：stash `ExcelToXptModelTransformer.java` 后两个断言均为 `expected: not <null>`（后续元素配置被 break 跳过）。

### [P2] CellRowExpander 与 CellColExpander 对 minReuse/maxReuse 复用区间的记录条件不对称

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/expand/CellRowExpander.java:132-142`
- **维度**: D1
- **证据**:
```java
// CellRowExpander.duplicateCell（第132-137行）：无论是否真正复用都记录
if (needInsert) {
    needInsert = !isAllowReuse(cell, table, newIndex);
    if (counter.minReuse == Integer.MAX_VALUE)
        counter.minReuse = newIndex;
    if (counter.maxReuse < newIndex)
        counter.maxReuse = newIndex;
    ...
}
// CellColExpander.duplicateCell（第126-131行）：仅复用时记录
if (!needInsert) {
    if (counter.minReuse == Integer.MAX_VALUE)
        counter.minReuse = newIndex;
    ...
}
```
- **现状**: 两个本应对称的展开器（行/列平等对待是该包的设计声明，见 ExpandedTable 类注释）在记录"复用区间"时条件不同：row 版在新插入（未复用）时也把 newIndex 计入 minReuse..maxReuse 区间，col 版只在复用时记录。
- **风险**: `skipExtendSpan`（第 93-102 行）遍历 minReuse..maxReuse 区间抵消兄弟展开共享的 span。row 版区间偏大时，混合插入/复用场景（配置 `expandInplaceCount` 时）抵消计数可能偏大，导致 `needExtend.setMergeDown(mergeDown + span)` 的 span 偏小，合并单元格行高不足、表格布局错位。该路径组合复杂、难以单测覆盖，属潜伏的正确性隐患。
- **建议**: 统一两处逻辑（以 col 版"仅复用时记录"语义为准更符合 minReuse/maxReuse 命名），并补充混合插入/复用场景的回归测试。
- **误报排除**: 已逐行对照两个 expander 的 `duplicateCell`/`extendCells`/`skipExtendSpan` 实现，除该处外其余结构镜像对称；无法从代码断言哪一侧语义正确，故按"分叉导致的条件性错误"定级 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-25）**: 裁定暂缓。决策点：两侧语义的正确性无法从代码或现有测试断言——`skipExtendSpan` 通过 `generatorCell != cell` 排除自身插入的行，row 版把插入位置也计入 [minReuse,maxReuse] 区间的实际差异仅在"同一次展开内插入与复用交错、且区间内夹杂第三方生成行"的混合场景才分叉，需要展开引擎设计 owner 裁定正确语义，或构造混合插入/复用的 golden 模板验证后再统一（报告建议以 col 版"仅复用时记录"为准，但无测试证据支撑）。影响面：`expandInplaceCount` + 兄弟复用场景的 `mergeDown` 延展计算（现有 `test-expand-inplace-count.xpt.xlsx` 仅覆盖纯预留复用路径，两侧语义下均绿），盲改任一侧都可能引入新错位。本轮已确认全部 nop-report 测试（core 64 + demo 24 例）在现状下通过，未改动此逻辑。

### [P2] XptModelInitializer.checkLoop 列方向成环报错时使用 row 方向的参数名与取值

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/build/XptModelInitializer.java:427-436`
- **维度**: D4
- **证据**:
```java
if (!colCycles.isEmpty()) {
    ExcelCell cell = CollectionHelper.first(colCycles);
    if (cell.getModel().getColParent(cell.getModelCellName()) != null) {
        throw new NopException(ERR_XPT_COL_PARENT_CONTAINS_LOOP)
                .source(sheet)
                .param(ARG_SHEET_NAME, sheet.getName())
                .param(ARG_CELL_POS, cell.getModel().getName())
                .param(ARG_ROW_PARENT, cell.getModel().getRowParent());  // 应为 ARG_COL_PARENT + getColParent()
    }
}
```
- **现状**: 检测到列父格成环时，异常参数名用的是 `ARG_ROW_PARENT`，值取的是 `getRowParent()`。
- **风险**: 模板配置错误时用户看到的诊断信息指向错误的父格配置（显示 rowParent 而非肇事的 colParent），排查方向被误导，可维护性问题。
- **建议**: 改为 `.param(ARG_COL_PARENT, cell.getModel().getColParent())`。
- **误报排除**: 已对照同方法 rowCycles 分支（第 416-425 行）与 `resolveColParent` 中正确的 `ARG_COL_PARENT` 用法（第 366 行），确认是复制后漏改。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。改为 `.param(ARG_COL_PARENT, cell.getModel().getColParent())`。测试：`TestXptModelInitializer#testColParentLoopErrorUsesColParentParam`（程序化构造 A1/B1 互为列父格的模板，断言异常参数 colParent 指向肇事配置、rowParent 参数为 null）。红验证：stash `XptModelInitializer.java` 后 `ex.getParam(ARG_COL_PARENT)` 为 null（参数以 rowParent 之名写入）。超范围新发现（记录未修）：`XptErrors` 中 `ERR_XPT_COL_PARENT_CONTAINS_LOOP` 与 `ERR_XPT_INVALID_COL_PARENT` 的 define 字符串分别复制自 row 版（均为 `nop.err.xpt.row-parent-contains-loop`/`nop.err.xpt.invalid-row-parent`），错误码字符串与 row 版重复；修改错误码字符串会影响日志匹配与潜在前端映射，留待主会话决策。

### [P2] ExcelTemplateToXptModelTransformer.endList 对 getCell 返回值未判空（beginList 有判空），可 NPE

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/imp/ExcelTemplateToXptModelTransformer.java:368-381`
- **维度**: D1
- **证据**:
```java
public void endList(int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
    FieldRange range = parents.remove(parents.size() - 1);
    int rowIndex = range.rowIndex;
    if (range.rangeType != RangeType.cardList)
        rowIndex++;
    ExcelCell cell = (ExcelCell) getTable().getCell(rowIndex, range.colIndex);
    if (StringHelper.isNumber(cell.getText())) {    // cell 可能为 null
        ...
    }
}
```
- **现状**: `beginList`（第 255-256 行）同样调用后写了 `cell != null &&` 判空，`endList` 没有。
- **风险**: 列表区域末行之后没有单元格（稀疏表格、区域贴住表格下边界）时 `cell.getText()` NPE，模板转换崩溃且栈信息指向框架内部。
- **建议**: 补 `if (cell != null && StringHelper.isNumber(cell.getText()))`。
- **误报排除**: 已对照 beginList 第 255-262 行的判空写法；`ExcelTable.getCell` 在无单元格时返回 null（XptModelInitializer 第 262-264 行亦按可空处理）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。补 `if (cell != null && StringHelper.isNumber(cell.getText()))`，与 `beginList` 的判空写法对齐。测试：`TestExcelTemplateToXptModelTransformer#testEndListWithMissingCellAfterListRegion`（同包直接构造 `BuildXptModelListener`，单行表头的稀疏表格上 beginList/endList）。红验证：stash `ExcelTemplateToXptModelTransformer.java` 后 `endList` 抛 NPE（`Cannot invoke ExcelCell.getText() because "cell" is null`）。

### [P2] ExpandedTable.getRow 越界返回 null，多个调用方直接解引用导致 NPE

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedTable.java:275-279`
- **维度**: D1
- **证据**:
```java
public ExpandedRow getRow(int rowIndex) {
    if (rowIndex >= rows.size())
        return null;                    // 越界返回 null（负数则直接 IndexOutOfBounds）
    return rows.get(rowIndex);
}
```
- **现状**: 返回 null 的契约与 `getCol`（第 294-296 行直接 `cols.get` 越界抛异常）不一致；而调用方普遍不判空，如 `ExpandedSheetEvaluator.removeRow`（第 220-228 行）`table.getRow(i).setRemoved(true)`、`CellRowExpander.removeCell`（CellRowExpander.java:42-47 行）`table.getRow(i).setRemoved(true)`、`collectRowExtendCells`（XptModelInitializer.java:525 行）`table.getRow(beginIndex)`。
- **风险**: `rowTestExpr` 触发行删除时若 `mergeDown` 越过表格边界（模板中合并单元格超出已定义行数），或展开 span 计算与实际行数不一致，`getRow` 返回 null 后立即 NPE，掩盖真实配置错误。
- **建议**: 或者越界统一抛带上下文的异常，或者调用方判空跳过；至少 `removeRow/removeCol` 循环应钳制到 `rowCount`。
- **误报排除**: 已核对上述三个调用点均无判空；`removeRow`（Evaluator）循环上界 `rowIndex + mergeDown` 来自模板合并配置，不受表格实际行数约束。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（采用报告建议的"调用方钳制"方案）。`CellRowExpander.removeCell`、`CellColExpander.removeCell`、`ExpandedSheetEvaluator.removeRow/removeCol` 四处删除循环上界钳制到 `table.getRowCount()`/`getColCount()`（越界行/列本就不存在，标记删除语义等价且消除 NPE 与 IOOBE）；`XptModelInitializer` 第 525 行处为 ExcelTable（模板模型）且 beginIndex 由模板自身行 span 推导、必然在界内，不改。`ExpandedTable.getRow` 契约本身保持不变（见 P3 getCol/getRow 一致性条目的处置）。测试：`TestCellExpanderRemoveCell#testRemoveRowCellClampedToTableBounds`、`#testRemoveColCellClampedToTableBounds`、`TestExpandedSheetEvaluator#testRemoveRowClampedToTableBounds`、`#testRemoveColClampedToTableBounds`（后两者经反射调用私有方法，mergeDown/mergeAcross 越过表格边界）。红验证：stash 对应文件后四处均抛 NPE（`getRow(i)` 返回 null 直接 `setRemoved`）。

### [P2] PDF 导出对非 WinAnsi 字符（中文等）无保护，字体回退 Helvetica 时 showText 抛异常导致整个导出失败

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/utils/PdfStyleHelper.java:103-106`
- **维度**: D4/D1
- **证据**:
```java
contentStream.beginText();
contentStream.newLineAtOffset(position[0], position[1]);
contentStream.showText(text);       // PDFBox Standard14 字体对中文抛 IllegalArgumentException
contentStream.endText();
```
- **现状**: `drawUnwrappedText`/`drawWrappedText` 直接 `showText`。当模板字体为中文字体（宋体等）且部署环境未提供对应 `/fonts/*.ttf` 时，`FontManager.getFont` 回退到 Helvetica（WinAnsi 编码），遇到中文字符 PDFBox 抛 `IllegalArgumentException: U+XXXX is not available in the font's encoding`。该异常非 IOException，`PdfSheetRenderer` 第 85 行的 `catch (IOException)` 不会包装它，直接以裸异常冒出。
- **风险**: 中文报表在未部署字体文件的实例上 PDF 导出整体失败，错误信息晦涩（编码错误而非"缺少字体文件"），且叠加 registerSystemFonts 失效问题（P1）后所有非内置字体场景都走该回退路径。
- **建议**: 在 `FontManager` 加载失败时记录字体名并给出明确的部署指引错误；`showText` 前检测字体编码能力，不可编码字符降级替换或按字符分片用可用字体渲染。
- **误报排除**: 已核对 PDFBox `PDType1Font`（Standard14）仅支持 WinAnsi/StandardEncoding；已读 `FontManager.loadFont`（第 127-149 行）失败仅 LOG.error 后返回 null 回退 Helvetica；已确认异常类型不在现有 catch 范围内。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（采用报告建议的"不可编码字符降级替换"）。`PdfStyleHelper` 新增 `sanitizeTextForFont`（先整体 `font.encode` 探测，可编码则原样返回；失败则逐字符过滤，不可编码字符替换为 `?`），在 `drawUnwrappedText`/`drawWrappedText` 入口统一净化（同时覆盖宽度计算 `getStringWidth` 的同源异常），中文等非 WinAnsi 字符不再导致整个导出失败。测试：`TestPdfStyleHelper#testSanitizeTextForFont`、`#testDrawUnwrappedTextWithNonWinAnsiChars`、`#testDrawWrappedTextWithNonWinAnsiChars`（真实 PDPageContentStream 上绘制中文）。红验证：正式测试引用了新增方法无法直接 stash（编译红），以临时 scratch 测试在 HEAD 上复现红形态：`IllegalArgumentException: U+4E2D ('.notdef') is not available in the font Helvetica, encoding: WinAnsiEncoding`。

### [P3] ExpandedRow/ExpandedCol 的 forEachCell 系列在空链表上 do-while 直接 NPE

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedRow.java:182-192`
- **维度**: D1
- **证据**:
```java
public void forEachRealCell(Consumer<ExpandedCell> action) {
    ExpandedCell cell = firstCell;
    do {                          // firstCell 为 null 时首次迭代即 NPE
        if (!cell.isProxyCell()) {
            action.accept(cell);
        }
        cell = cell.getRight();
        if (cell == null)
            break;
    } while (true);
}
```
- **现状**: `forEachRealCell`/`forEachCell` 用 do-while 假定 firstCell 非空。`ExpandedCol.forEachCell`（ExpandedCol.java:51-59）同样问题且还把 null 传给 action。表格 0 列时 `newRow()` 不创建单元格。
- **风险**: 仅空表/空列时触发，正常模板不出现，但 TableExpander 构造、dropRemoved 等路径都遍历行，边界输入下崩溃。
- **建议**: 改为 `while (cell != null)` 形式的先判断循环。
- **误报排除**: 已读 `ExpandedTable.newRow()`（第 401-418 行）确认 cols 为空时 firstCell 为 null；正常加载的模板 colCount > 0，故定级 P3。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`ExpandedRow.forEachRealCell`/`forEachCell` 与 `ExpandedCol.forEachCell` 由 do-while 改为 `while (cell != null)` 先判断循环（空链表为空操作）。测试：`TestExpandedTable#testForEachCellOnEmptyRow`（0 列表格）、`#testForEachCellOnEmptyCol`（0 行表格）。红验证：stash `ExpandedRow.java`/`ExpandedCol.java` 后抛 NPE（`cell.getRight()`/`cell.getDown()` 因 cell 为 null）。

### [P3] ExpandedRow.prop_get/prop_has/prop_set 在 model 为 null 时 NPE

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedRow.java:167-180`
- **维度**: D1
- **证据**:
```java
@Override
public Object prop_get(String propName) {
    return model.prop_get(propName);   // model 可能为 null（insertEmptyRow 创建的行）
}
```
- **现状**: `insertEmptyRow`/`makeRow` 创建的行 model 为 null，序列化或扩展属性访问时 NPE。`XptWordTableRenderer.renderExpandedSheet` 第 66 行 `row.getModel().prop_get(...)` 同样暴露于该风险。
- **风险**: 展开过程中新行未设置 model 且被扩展属性访问时崩溃；实际展开路径中 duplicateRow 会 `newRow.setModel(r.getModel())`（模板行 model 由 XptModelInitializer 保证非空），多数场景被覆盖。
- **建议**: prop_get 系列对 null model 返回默认值或抛出带上下文的异常。
- **误报排除**: 已读 ExpandedTable.init 与 CellRowExpander.duplicateRow 的 model 赋值链，确认普通路径 model 非空，仅异常构造路径可触发。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`prop_get`/`prop_has`/`prop_set` 对 `model == null` 分别返回 null / false / no-op（展开期 insertEmptyRow 创建的行被扩展属性访问时不再崩溃）。测试：`TestExpandedTable#testPropAccessOnRowWithoutModel`。红验证：stash `ExpandedRow.java` 后抛 NPE（`XptRowModel.prop_get` 因 model 为 null）。

### [P3] ExpandedTable.getCol 与 getRow 越界行为不一致

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedTable.java:294-296`
- **维度**: D1
- **证据**:
```java
public ExpandedCol getCol(int colIndex) {
    return cols.get(colIndex);      // 越界抛 IndexOutOfBoundsException；getRow 越界返回 null
}
```
- **现状**: 同类方法两种越界契约并存，调用方（如 `CellColExpander.removeCell` 第 39-44 行的 `table.getCol(i).setRemoved(true)`）无法一致防御。
- **风险**: 错误发生时一部分是 NPE 一部分是 IOOBE，诊断噪声；合并越界配置下崩溃类型不可预测。
- **建议**: 统一为同一越界策略。
- **误报排除**: 直接对照第 275-279 行 getRow 实现即可确认差异。

> **处置（fix-ai-check 分支，2026-08-25）**: 裁定不修复。理由：统一越界契约（都返回 null 或都抛带上下文异常）波及全部调用方，爆炸半径大；现实中可达的 NPE 路径已在上文 getRow 条目中通过四处删除循环钳制消除；剩余误用无论 NPE 还是 IOOBE 都会立即暴露模板配置错误，不存在静默数据错误，契约统一的长维护收益不抵回归风险。

### [P3] XptWordTableRenderer.renderCell 对 w:p 节点缺失未判空（w:r 有判空，不对称）

- **文件**: `nop-report/nop-report-docx/src/main/java/io/nop/report/docx/renderer/XptWordTableRenderer.java:137-140`
- **维度**: D1
- **证据**:
```java
XNode tc = (XNode) cell.getModel().prop_get(VAR_XPT_NODE);
XNode tcPr = tc.childByTag("w:tcPr");
XNode p = tc.childByTag("w:p");          // 可能为 null
XNode pPr = p.childByTag("w:pPr");       // 直接解引用
XNode r = p.childByTag("w:r");
XNode rPr = r == null ? null : r.childByTag("w:rPr");   // r 却做了判空
```
- **现状**: 同一方法内 `r` 判空而 `p` 不判空。Word 规范要求 tc 内至少一个 w:p，但手工构造/损坏的 docx 模板可缺。
- **风险**: 异常模板下 NPE；正常 Word 生成的文档不触发。
- **建议**: `p` 为 null 时输出空段落节点，与 renderProxyCell 第 126-129 行的兜底空 `w:p` 行为对齐。
- **误报排除**: 已对照 renderProxyCell（第 92-131 行）总是手写空 `w:p`，说明渲染器本可无段落渲染。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`renderCell` 中 `p` 判空：`p == null` 时 `pPr`/`r` 置 null（渲染兜底空 `w:p` 段落结构，后续 `pPr != null`/`rPr != null` 分支本就存在），与 `renderProxyCell` 的空段落行为对齐。测试：`TestXptWordTableRenderer#testRenderCellMissingParagraphNode`（同包子类化 + CollectXmlHandler，构造缺失 `w:p` 的 tc 节点）。红验证：stash `XptWordTableRenderer.java` 后抛 NPE（`XNode.childByTag` 因 p 为 null）。

### [P3] ReportEngine.getRendererForExcelData 对无 sheet 模板直接 get(0)

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/ReportEngine.java:102-104`
- **维度**: D1
- **证据**:
```java
ExcelWorkbook tpl = new ExcelWorkbookParser().parseFromResource(template);
ExcelSheet sheetTpl = tpl.getSheets().get(0);   // 空 workbooks 时 IndexOutOfBoundsException
```
- **现状**: 未校验模板至少包含一个 sheet。
- **风险**: 空模板资源（0 字节 xlsx 解析结果或损坏文件）下抛 IOOBE，而非带资源路径的友好错误。
- **建议**: 先校验 `tpl.getSheets()` 非空并抛 NopException 附带模板路径。
- **误报排除**: 直接审读该方法，无前置校验。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。解析后校验 `tpl.getSheets().isEmpty()` 则抛 `NopException(ERR_XPT_TEMPLATE_NO_SHEET)` 附模板路径（`XptErrors` 新增错误码常量；核对结论：模块无错误码 i18n bundle 惯例——`nop-report-meta/_vfs/i18n/` 下 bundle 为空壳占位，define 内置中文消息与邻近错误码风格一致，无需同步 bundle）。测试：`TestReportEngine#testGetRendererForExcelDataWithEmptyTemplate`（测试内手工构造 0-sheet 的合法 xlsx zip）。红验证：stash `ReportEngine.java` 后抛 `IndexOutOfBoundsException: Index: 0` 而非带路径的 NopException。

### [P3] XptModelToExcelTransformer.transform 为空实现且无任何调用者（未完成功能/死代码）

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/build/XptModelToExcelTransformer.java:16-18`
- **维度**: D8
- **证据**:
```java
/**
 * 将xpt报表模型保存为Excel格式
 */
public class XptModelToExcelTransformer {
    public void transform(ExcelWorkbook wk) {
    }
}
```
- **现状**: 类注释声明"将 xpt 报表模型保存为 Excel 格式"，方法体为空；全仓库 grep 无调用点。
- **风险**: 若未来被接入将静默无效果；当前仅误导阅读者。
- **建议**: 删除或补全实现并注明状态。
- **误报排除**: 全仓库（排除 target）grep 类名仅命中定义文件本身。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（删除死代码）。全仓库 grep（排除 target/_gen/审计文档）确认无任何引用，空实现误导阅读者且有未来被静默接入的风险，直接删除 `XptModelToExcelTransformer.java`。纯死代码删除、无行为变化，免测试。

### [P3] TextWrapHelper.wrapByWord 单字符超 maxWidth 时死循环、wrapByCharacter 同场景丢失文本（当前主路径不可达）

- **文件**: `nop-report/nop-report-pdf/src/main/java/io/nop/report/pdf/utils/TextWrapHelper.java:80-95`
- **维度**: D1
- **证据**:
```java
} else {
    // 没有空格，只能强制在当前位置折行
    lines.add(text.substring(lineStart, i));
    lineStart = i;
    i--;            // 若 i == lineStart（单字符已超宽），下一轮仍在原地 -> 死循环
    ...
}
```
- **现状**: `wrapByWord` 当单个字符宽度大于 maxWidth 时（极窄列/超大字号），`i--` 与 `lineStart = i` 使循环永不前进并不断 add 空串；`wrapByCharacter`（第 116-127 行）同场景不死循环但每字符输出空行、最终文本全部丢失。`wrapForced` 有 `currentLine.length() > 0` 保护不受影响。
- **风险**: 当前唯一调用方 `PdfStyleHelper.drawWrappedText`（第 124 行）固定传 wrapMode=2（wrapForced），故主路径不可达；该 public API 一旦被按单词/字符模式复用即触发挂死。
- **建议**: wrapByWord/wrapByCharacter 增加"当前行至少落一个字符"保护（对齐 wrapForced 的写法）。
- **误报排除**: 已 grep `splitTextIntoLines` 全模块仅 PdfStyleHelper 一处调用且 wrapMode 为常量 2；已手推 ">=" 场景确认 wrapForced 无死循环。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`wrapByWord` 无空格强制折行分支增加 `i > lineStart` 保护（i == lineStart 即单字符已超宽时保留该字符、`currentWidth = charWidth` 继续累进）；`wrapByCharacter` 增加 `i > lineStart` 保护（每行至少落一个字符），两者对齐 `wrapForced` 的 `currentLine.length() > 0` 写法，`wrapForced` 不变。测试：`TestTextWrapHelper#testWrapByWordSingleCharTooWide`（@Timeout(10)）、`#testWrapByCharacterSingleCharTooWide`、`#testWrapForcedUnaffected`。红验证：stash `TextWrapHelper.java` 后 wrapByWord 场景死循环直至 surefire fork JVM `Java heap space` OOM 崩溃；wrapByCharacter 场景产生大量空行且文本静默丢失。

### [P3] ExpandedRow.getRowIndex/ExpandedCol.getColIndex 展开期使用 indexOf(this)，O(n) 查找带来 O(n²) 风险

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/model/ExpandedRow.java:265-269`
- **维度**: D6
- **证据**:
```java
public int getRowIndex() {
    if (assignedRowIndex >= 0)
        return assignedRowIndex;
    return table.getRows().indexOf(this);   // 线性扫描
}
```
- **现状**: `assignRowIndexAndColIndex` 只在展开完成后（ExpandedSheetGenerator.generateSheet 第 160 行）执行；展开过程中 `CellRowExpander`、`removeCell`、`extendCells` 等大量调用 `getRowIndex()`，每次都是 `indexOf` 线性扫描。
- **风险**: 大数据量行展开（万行级）时整体复杂度趋近 O(n²)，报表生成明显变慢。中小报表无感知。
- **建议**: 行内缓存 index 并在 insert/remove 时增量维护，或展开期间使用临时索引 Map。
- **误报排除**: 已读 assignRowIndexAndColIndex 调用时序（generateSheet 中在 dropRemoved 之后）与展开器中的 getRowIndex 调用点，确认展开期走 indexOf 分支。

> **处置（fix-ai-check 分支，2026-08-25）**: 裁定暂缓。决策点：行/列索引缓存需要在 `insertEmptyRow`/`makeRow`/`removeRow`/`insertEmptyCol` 等所有结构变更点增量维护，或展开期间引入临时索引 Map，属于展开引擎核心数据结构的改造，需配套设计与万行级基准测试验证收益。影响面：ExpandedTable 全部增删路径 + 两个方向展开器，索引失效会直接产生错位类正确性 bug，风险高于性能收益；中小报表无感知。

### [P3] ExcelRecordInput 将整个 sheet 全量载入内存 List

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/record/ExcelRecordInput.java:87-98`
- **维度**: D6
- **证据**:
```java
public void beforeRead(Map<String, Object> map) {
    HeaderListRecordOutput<Map<String, Object>> collector = new HeaderListRecordOutput<>(...);
    ...
    this.output.parseSheet(sheetName);
    list = collector.getResult();    // 全部记录驻留内存
}
```
- **现状**: hasNext/next 基于内存 list，导入大 Excel 文件时内存占用与文件行数成正比。
- **风险**: 超大文件导入可能 OOM；与流式输出的 ExcelRecordOutput 不对称。属设计权衡而非缺陷。
- **建议**: 如需支持大文件，改用流式解析回调。
- **误报排除**: 已读全类确认无分页/流式机制。

> **处置（fix-ai-check 分支，2026-08-25）**: 裁定不修复。报告自身已认定"属设计权衡而非缺陷"：当前导入场景按整表加载语义实现，流式改造需重写 `XlsxToRecordOutput` 的收集机制（header 归一化、字段映射依赖全量上下文），属功能级改造；在无大文件导入需求证据的情况下，改造风险大于收益。若未来出现超大文件导入需求，应作为独立需求重新评估。

### [P3] 聚合函数若干 Excel 语义偏差（PROPORTION 除零 NaN、COUNT 系列对 null 输入返回 null、matchesCondition 不做类型归一）

- **文件**: `nop-report/nop-report-core/src/main/java/io/nop/report/core/functions/ReportFunctions.java:229-231`
- **维度**: D1/D8
- **证据**:
```java
Number sum = (Number) firstCell.getComputed(XptConstants.KEY_ALL_SUM,
        c -> SUM(xptRt.getNamedCellSet(cellName)));
return MathHelper.divide(v, sum);     // sum 为 0 时静默返回 NaN
```
- **现状**: (1) `PROPORTION` 在汇总值为 0 时返回 NaN 无提示（Excel 显示 #DIV/0! 语义上是显式错误）；(2) `COUNT/COUNTA/MAX/MIN/AVERAGE` 对 `values == null` 返回 null，而 Excel 对 COUNT(null) 返回 0；(3) `matchesCondition`（第 476-477 行）默认分支 `Objects.equals(value, condition)` 不做数值/字符串转换，`COUNTIF(A, 5)` 对字符串 "5" 不计数（Excel 会按文本比较匹配）。
- **风险**: 边界输入下与 Excel 行为不一致，导出结果可能令熟悉 Excel 的用户意外；无崩溃风险。
- **建议**: 明确并文档化这些语义差异；divide 除零可在结果标注为显式错误值。
- **误报排除**: 已读相关函数完整实现与 `MathHelper.divide` 除零返回 NaN 的行为；这些差异是否为有意设计无法从代码确认，按低危语义偏差汇总为一条。

> **处置（fix-ai-check 分支，2026-08-25）**: 裁定不修复。理由：三处均为边界输入下的行为偏差、无崩溃路径，且库内语义自洽（`MathHelper.divide` 除零 NaN 是 nop-commons 全局约定）；在无产品需求的情况下改变聚合函数返回值（如 COUNT 空集 0、PROPORTION 显式错误值）会破坏既有模板输出的兼容性。若需对齐 Excel 语义，应作为独立需求统一设计并同步文档（与本条"明确并文档化"的建议合并处理）。注：P0 条目中 `matchesCondition` 的操作符路径已修复，相等分支的类型归一维持现状。

## 补充说明（跨模块观察，不计入本单元发现）

- `MathHelper.min(Object,Object)`（nop-kernel/nop-commons）在 `v2 == null` 时返回 null 而非 v1（第 720-726 行，与 `max` 的 null 处理不对称）。nop-report 内 `ReportFunctions.MIN` 因前置 `instanceof Number` 过滤不会把 null 传入该函数，故本单元内不可达；但任何其他模块直接调用 `MathHelper.min(5, null)` 会得到错误的 null。建议 nop-commons 单独核查。

## 检查过程备注

- 未发现 `@Inject` 注入 private 字段、Spring `@Value`、`new RuntimeException`、`SimpleDateFormat` 共享、`Random` 误用等 D3/D5/D7 常见违规（全模块 grep 为零命中）；beans.xml 注册（report-defaults/report-pdf-defaults/report-ext/_service 等）与 BizModel/@BizQuery 结构核对无缺失。
- 敏感信息日志、反序列化、路径遍历方面：`ReportDemoBizModel.download/downloadModel` 对 reportName 有 `StringHelper.isValidVPath` 校验；未发现明文口令日志输出。
