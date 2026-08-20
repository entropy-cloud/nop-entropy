# nop-excel 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-format/nop-excel
- 文件数: 332（src/main/java，其中 `_gen/` 生成文件 196 个，按约定不在检查范围）
- 覆盖范围声明:
  - **深读（逐行）**: `imp/` 全部 11 个非生成文件（ImportExcelParser、TreeTableDataParser、ImportDataCollector、block 包、LabelData、adapter、ImportDataHelper、ImportModel/ImportSheetModel/ImportFieldModel/IFieldContainer）；`format/` 全部 3 个；`renderer/` 全部 7 个；`resolver/` 全部 3 个；`util/` 全部 6 个；`print/` 1 个；根包 ExcelConfigs/ExcelErrors/ExcelConstants；model 非生成核心文件（ExcelWorkbook、ExcelSheet、ExcelTable、ExcelRow、ExcelCell、ExcelStyle、ExcelDataValidation、ExcelImage、XptCellModel、ExcelRichText、IExcelStyleProvider）；chart 非生成 util（ChartDataSet、ChartStyleHelper）。
  - **验证性阅读（为确认本模块行为）**: nop-core 的 AbstractTable/IRowView/ICellView/CellRange/KeyedList、nop-commons 的 FastIntegerFormatter 关键片段；跨模块调用方（nop-report 的 ExcelTemplateToXptModelTransformer、nop-ooxml-xlsx 的 XlsxObjectLoader）仅做可达性确认。
  - **抽查/未逐行读**: model/constants 与 model/color 下的枚举类（仅 grep 级检查：静态 Map 均在 static block 填充后只读，FileMagic 为 POI 忠实拷贝）；chart/model 下 111 个非生成文件（绝大多数为 `extends _gen` 的空壳薄层，含 toCssStyle 等少量手写方法的未逐行审）；XptRowModel/XptSheetModel/XptWorkbookModel/XptXplModel、ExcelFont/ExcelBorder/ExcelRichTextPart 等纯数据类未逐行读。
  - **范围说明**: 本模块 pom 不依赖 POI（仅注释提及 XSSFWorkbook）。Excel 文件的物理解析（xlsx/zip 解压、Workbook close、行数/大小限制）在 nop-ooxml-xlsx / nop-report 等模块，不在本次范围。因此 D2（POI 资源泄漏）、D5 的 zip 炸弹/上传限制、公式注入写出面在本模块无对应代码，报告如实记录而非臆造。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 2 |
| P3 | 6 |

## 发现列表

### [P1] TreeTableDataParser.parseNextFields 循环变量误用导致同行右侧字段静默丢失

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/imp/TreeTableDataParser.java:342-349`
- **维度**: D1（导入字段映射错位/数据丢失）
- **证据**:
```java
private void parseNextFields(String sheetName, IFieldContainer fieldContainer, ITableView table,
                             int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                             ITableDataEventListener listener) {
    for (int j = colIndex; j < maxColIndex; j++) {
        ICellView cell = table.getCell(rowIndex, colIndex);   // 应为 getCell(rowIndex, j)
        if (cell == null)
            continue;
        if (cell.isProxyCell()) {
            continue;
        }
        CellRange range = parseField(sheetName, fieldContainer, table, rowIndex, j, maxRowIndex, maxColIndex, listener);
```
- **现状**: 循环变量是 `j`，但空/proxy 判断读取的始终是起始列 `(rowIndex, colIndex)` 处的单元格，而非当前列 `(rowIndex, j)`。
- **风险**: 当起始列单元格为 null（字段之间隔了空列，`parseSimpleField` 的 valueCell 为 null 时 `range.getLastColIndex()+1` 落在空洞列很常见）或为 proxy cell（左侧合并区域延伸）时，循环每一次迭代都 `continue`，**同一行右侧的所有字段全部不被解析**，字段数据静默丢失，不产生任何错误。反向场景（起始列非空）下本应对 proxy/null 列做的过滤失效，仅靠 `parseField` 内 `name == null` 兜底侥幸正确。该方法是 `parseFields` 解析"值在下方"布局时向右扩展兄弟字段的核心路径（nop-report 的 ExcelTemplateToXptModelTransformer 亦经此路径）。
- **建议**: 将 `table.getCell(rowIndex, colIndex)` 改为 `table.getCell(rowIndex, j)`，并补充覆盖"字段间空列 + 右侧还有字段"布局的回归测试。
- **误报排除**: 已验证 `AbstractTable.getCell` / `AbstractRow.getCell` 越界返回 null（非异常）、proxy cell 的 `getText()` 返回 null（`_ExcelCell` 的 value 未设置，`ICellView.getText()` 对 null value 返回 null），因此"全 continue 跳过"的推演成立，非猜测。

### [P1] TreeTableDataParser.findEmptyRowIndex 恒定读取起始行，对象字段区域无法收缩

- **文件**: `nop-format/nop-entropy-wt/nop-entropy-master/nop-format/nop-excel/src/main/java/io/nop/excel/imp/TreeTableDataParser.java:428-449`
- **维度**: D1（字段映射错位）
- **证据**:
```java
private int findEmptyRowIndex(ITableView table, int rowIndex, int colIndex, int maxRowIndex, int maxColIndex) {
    for (int i = rowIndex; i <= maxRowIndex; i++) {
        IRowView row = table.getRow(rowIndex);   // 应为 table.getRow(i)
        MutableBoolean empty = new MutableBoolean();
        row.forEachRealCell(rowIndex, (cell, r, c) -> {
            ...
        });
        if (empty.get())
            return i;
    }
    return maxRowIndex;
}
```
- **现状**: 循环变量是 `i`，但每一轮取的都是起始行 `table.getRow(rowIndex)`，"从 colIndex 起第一个 real cell 是否为空"的判断永远作用在起始行上。
- **风险**: `parseObjectField`（对象字段值在下方）用它收缩 `maxRowIndex`。由于对象子字段的第一个 label 行必非空，函数实际效果退化为"起始行非空 → 恒返回外层 maxRowIndex"。本应通过空行结束的对象字段区域会一直延伸到整个解析区域底部：空行之后的**兄弟字段会被当作对象内部字段去解析**——字段名不在子模型中时抛 `ERR_IMPORT_UNKNOWN_FIELD`（导入失败），子模型恰好有同名字段时静默错绑到错误的层级。imp 模型的树形文档布局（对象字段 + 空行分隔 + 后续字段）是设计支持的形态（函数意图即"找到空行"）。
- **建议**: 改为 `table.getRow(i)`；同时 `row.forEachRealCell(rowIndex, ...)` 的第一个参数也应传 `i`（当前回传给回调的行号恒为起始行，回调未用该参数故无实际影响，但应一并修正）。
- **误报排除**: 已验证 `AbstractTable.getRow` 越界返回 null、rowIndex <= maxRowIndex 时行必存在（proxy 行由 `doSetCell`/`makeRow` 创建），代码不会 NPE 而是逻辑错误；已确认 `forEachRealCell(int rowIndex, processor)` 的 rowIndex 仅为回显参数。

### [P1] alignRight 布局下把数据区结束行号赋给列边界，list 字段区域计算错位

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/imp/TreeTableDataParser.java:392-403`（`parseListField`），同模式还有 `:92-98`（`parseListSheet`）
- **维度**: D1（单元格坐标计算/合并区域）
- **证据**:
```java
if (alignRight) {
    maxColIndex = dataRange.getLastRowIndex();   // 行索引赋给列变量
} else {
    maxRowIndex = dataRange.getLastRowIndex();
}
return new CellRange(minRowIndex, minColIndex, maxRowIndex, maxColIndex);
```
- **现状**: `alignRight`（list 字段 label 纵向合并、数据表在右侧）分支中，`dataRange.getLastRowIndex()`（一个**行号**）被赋给 `maxColIndex`（**列边界**）。类型恰好都是 int，编译期无法发现。
- **风险**: `parseListField` 的返回值被 `parseFields` 消费：`parseNextFields(..., range.getLastColIndex() + 1, ...)` 以 `lastColIndex+1` 作为右侧兄弟字段的起始列。alignRight 布局下该值实际是数据表结束行号——起始列错位：兄弟字段从错误的列开始扫描，轻则把数据表的表头/数据单元格当作字段名解析（抛 `ERR_IMPORT_UNKNOWN_FIELD`），重则字段错绑。仅当数据表行数恰好等于列数时碰巧不暴露。注：`parseListSheet`（`:92-98`）存在同样代码，但其返回值只被消费 `getLastRowIndex()`，当前无实际影响——这更说明该写法是复制粘贴笔误而非刻意设计。
- **建议**: alignRight 分支应为 `maxRowIndex = dataRange.getLastRowIndex();`（maxColIndex 保持数据表实际宽度，即 parseListData 返回的 `dataRange.getLastColIndex()`）；两处一并修正并补充竖排 label 的 list 字段 + 右侧兄弟字段的布局测试。
- **误报排除**: 已核对 `parseListData`/`parseCardListData` 返回的 `CellRange` 语义（行、列均为表格坐标），数据表为横向消费列的普通表格、不存在转置语义，`getLastRowIndex()` 确为行号；`parseFields:284` 确实以 `range.getLastColIndex() + 1` 作为 `parseNextFields` 的起始列。本模块无 alignRight 布局的测试资源（测试主要在 nop-ooxml-xlsx，未覆盖该形态），故以代码语义定级。

### [P2] ImportDataCollector 对 computed 字段与默认值使用 getName() 而非 getPropOrName()，配置 prop 时数据写错属性

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/imp/ImportDataCollector.java:312-325`
- **维度**: D1（导入字段映射错位）
- **证据**:
```java
private void addDefaults(Map<String, ImportFieldModel> fieldMap, DynamicObject obj) {
    for (ImportFieldModel field : fieldMap.values()) {
        if (field.isComputed()) {
            if (field.getValueExpr() != null) {
                ...
                Object value = field.getValueExpr().invoke(scope);
                setProp(obj, field.getName(), value);          // 其余路径均用 getPropOrName()
            }
        } else if (obj != null && !field.isVirtual() && !field.isIgnoreWhenEmpty()) {
            obj.makeComplexPropDefault(field.getName(), null); // 同上
        }
    }
}
```
- **现状**: `simpleField`（`:288`）、`popObject`（`:172,182`）、`validateMandatory`（`:330`）统一使用 `field.getPropOrName()`（prop 优先、缺省 name），而 `addDefaults` 的两处使用 `field.getName()`。
- **风险**: imp 模型为字段配置了 `prop`（显示名 name 与目标属性 prop 分离）时：computed 字段的表达式结果被写入 name 属性（目标 prop 属性缺值、结果对象多出错误属性）；未填充字段的复杂属性默认值建在 name 上而非 prop 上。属同一路径内属性名解析规则不一致。
- **建议**: 两处改为 `field.getPropOrName()`，补充带 prop 配置的导入测试。
- **误报排除**: 已确认 `ImportFieldModel.getPropOrName()`（prop 非空优先）与 `DynamicObject.makeComplexPropDefault`（prop 不存在时才 `addPropDefault`）的实现，属性名不一致的后果成立。

### [P2] KeyedListAdapter 重复 key 记录被静默丢弃，专用错误码已定义但从未使用

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/imp/KeyedListAdapter.java:28-39`；关联 `nop-format/nop-excel/src/main/java/io/nop/excel/imp/ImportDataCollector.java:175-177`
- **维度**: D1/D4（数据丢失 + 异常吞噬）
- **证据**:
```java
public boolean add(List<Object> list, Object value) {
    KeyedList<Object> keyedList = (KeyedList<Object>) list;
    String key = keyedList.getKey(value);
    if (key == null)
        return false;              // key 属性为空：静默丢弃
    if (keyedList.containsKey(key))
        return false;              // key 重复：静默丢弃
    return list.add(value);
}
// ImportDataCollector.popObject:
    IListAdapter adapter = listAdapters.get(listAdapters.size() - 1);
    adapter.add((List<Object>) last, entity);   // 返回值被忽略
```
- **现状**: 配置了 `keyProp` 的列表字段（如 `dict-support.imp.xml` 中字典项 `keyProp="value"`）导入时，key 重复或 key 为空的记录被静默丢弃，无日志、无异常。
- **风险**: 用户上传的 Excel 中两条记录 key 相同时，后者**无声消失**，导入结果缺数据且难以排查。`ExcelErrors.ERR_IMPORT_MULTIPLE_ITEM_WITH_SAME_KEY`（`nop.err.excel.import.multiple-item-with-same-key`）已定义（全仓库 grep 无任何使用处），证明设计意图是报错，实现未落地。对比 sheet 级有显式重复检查（`ERR_IMPORT_SHEET_WITH_DUPLICATE_KEY_PROP`，ImportExcelParser:217-220），行为不一致。
- **建议**: 在 `ImportDataCollector.popObject` 检查 `adapter.add(...)` 返回值，为 false 时抛 `ERR_IMPORT_MULTIPLE_ITEM_WITH_SAME_KEY`（或至少 WARN 日志）；key 为 null 的场景可考虑独立错误提示。
- **误报排除**: 已确认 `KeyedList.add` 本身对重复 key 是"新替旧"，`KeyedListAdapter` 在调用前自行短路返回 false，二者行为不同；已确认错误码定义存在且无使用点。

### [P3] ExcelCellRefResolver 多引用拆分用 split(",")，不支持含逗号的引号 sheet 名；getValue 对 parse 返回 null 无防护

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/resolver/ExcelCellRefResolver.java:39-50, 22-26`
- **维度**: D8（接口契约与实现不匹配）
- **证据**:
```java
if (cellRangeRef.startsWith("(") && cellRangeRef.endsWith(")")) {
    String multiRef = cellRangeRef.substring(1, cellRangeRef.length() - 1);
    String[] refs = multiRef.split(",");   // Excel 引用允许 'A,B'!C1 形式的 sheet 名
    ...
@Override
public Object getValue(String cellRef) {
    ExcelCellRef ref = ExcelCellRef.parse(cellRef);   // 空串/"NONE" 时返回 null
    ExcelSheet sheet = findSheet(ref);                // findSheet(null) 将 NPE
```
- **现状**: union 引用 `(Sheet1!A2,'A,B'!C1:C5)` 会被拆成非法片段；`getValue` 对 `ExcelCellRef.parse` 返回 null（空串、`CellPosition.NONE_STRING`）的输入直接 NPE。
- **风险**: 触发前提较苛刻（sheet 名含逗号；或调用方对空/"NONE" 引用调 getValue——当前唯一调用方 ChartDataResolver.resolveSeriesName 已做 `isNotEmpty` 防护），故定 P3。但作为公开接口（`ICellRefResolver` 被 nop-chart-export 多处实现/调用）契约健壮性不足。
- **建议**: 拆分时考虑引号包裹段；`getValue` 对 parse 结果为 null 时返回 null（与接口注释"可能为 null"一致）。
- **误报排除**: 已确认 `ExcelCellRef.parse` 对空串/NONE 返回 null 的代码路径，以及 nop-chart-export 调用方的防护现状。

### [P3] parseListData 异常上下文使用错误的行列坐标且未做 1-based 换算

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/imp/TreeTableDataParser.java:223-228`
- **维度**: D4（错误处理质量）
- **证据**:
```java
ICellView cell = table.getCell(i, j);
try {
    listener.simpleField(i, j, cell, header);
} catch (NopException e) {
    e.addXplStack("row=" + rowIndex + ",col=" + colIndex + ",sheet=" + sheetName + ",field=" + header);
    throw e;
}
```
- **现状**: 出错单元格是 `(i, j)`，写入错误栈的却是表头起始位置 `(rowIndex, colIndex)`；且输出 0-based 行列号，与同文件 `:59` 的 `rowIndex + 1`（1-based 展示）不一致。
- **风险**: 用户按错误提示定位不到实际出错的单元格，排错成本增加。仅影响错误信息，不影响数据。
- **建议**: 改为 `"row=" + (i + 1) + ",col=" + j`（或统一 AB 坐标 `getCellPosition(i, j)`）。

### [P3] HTML 渲染器将 reportId 未经转义写入 HTML 属性

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/renderer/HtmlReportRendererFactory.java:54-57`；`SimpleHtmlReportRendererFactory.java:51-55` 同
- **维度**: D5（XSS 防御缺失，当前不可利用）
- **证据**:
```java
String reportId = getXptReportId(context);
out.write("<div id=\"");
out.write(reportId);          // 未 escapeXmlAttr
out.write("\">\n");
```
- **现状**: 同函数内 `sheet.getName()` 写属性时用了 `StringHelper.escapeXmlAttr`（`:132`），reportId 没有。reportId 来源是 eval scope 变量 `xptReportId`。
- **风险**: 全仓库无任何 `setLocalValue(xptReportId)` 调用，当前恒为常量默认值，**不可利用**；但一旦上层把用户可控值放入该 scope 变量（接口设计上允许），即成存储型 XSS 注入点，属防御性编码缺失。
- **建议**: `out.write(StringHelper.escapeXmlAttr(reportId))`。

### [P3] ExcelDataValidationHelper 用裸 IllegalArgumentException 而非 NopException

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/util/ExcelDataValidationHelper.java:35-38`
- **维度**: D7（平台错误处理规范）
- **证据**:
```java
public static ExcelDataValidation newDataValidation(IObjPropMeta propMeta, String sqref) {
    if (StringHelper.isEmpty(sqref))
        throw new IllegalArgumentException("invalid sqref:" + sqref);
```
- **现状**: 该类其余错误路径均走 `NopException` + `ErrorCode`（如 `resolveError`），此处参数校验用了裸 `IllegalArgumentException`。
- **风险**: 违背模块错误处理两档策略（公共 API 应 NopException + ErrorCode + .param），异常无错误码、无法国际化。sqref 由调用方（导出链路）传入，触发频率低。
- **建议**: 改用 `ExcelErrors` 中合适的错误码（可新增 `ERR_EXCEL_INVALID_SQREF`）并带 `.param(ARG_CELL_REF, sqref)`。

### [P3] 未实现/不可达的代码遗留

- **文件**:
  - `nop-format/nop-excel/src/main/java/io/nop/excel/imp/block/SheetBlockBuilder.java:30-32`
  - `nop-format/nop-excel/src/main/java/io/nop/excel/model/ExcelWorkbook.java:53-66`
  - `nop-format/nop-excel/src/main/java/io/nop/excel/util/UnitsHelper.java:273-296`
- **维度**: D8/D1（契约与维护性）
- **证据**:
```java
// SheetBlockBuilder
public ExcelSheet build(Object bean, IEvalScope scope) {
    return null;                     // 公有 API 恒返回 null，全仓库无调用方
}
// ExcelWorkbook.getExcelFormatValue —— 全仓库无调用方
Format format = ExcelFormatHelper.getFormat(style.getNumberFormat());
if (format != null) {
    return format.format(cell.getValue());   // value 为 null 时 SimpleDateFormat.format(null) NPE
}
```
- **现状**: `SheetBlockBuilder.build` 是返回 null 的存根（无调用方）；`getExcelFormatValue` 是死代码且对"空单元格 + 数字格式样式"（Excel 中极常见）组合会 NPE；`UnitsHelper.main` 为调试输出残留在生产代码。
- **风险**: 死代码若被后续使用者调用即触发 null/NPE，维护性风险。
- **建议**: 删除或补实现；`getExcelFormatValue` 若保留需先判 `cell.getValue() != null`。

### [P3] ExcelCell.getColIndex/getRowIndex 每次 O(n) 线性扫描

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/model/ExcelCell.java:102-108`
- **维度**: D6（性能隐患）
- **证据**:
```java
public int getRowIndex() {
    return getRow().getRowIndex();          // table.getRows().indexOf(this)
}
public int getColIndex() {
    return getRow().getCells().indexOf(this); // O(列数)
}
```
- **现状**: 每次调用都对整行/全表做 `indexOf`；`getTopRealCell/getDownRealCell` 等导航方法（报表展开引擎常用）每次调用触发两次此类扫描。
- **风险**: 大表 + 高频单元格导航时退化为 O(rows×cols) 级别开销。属结构性性能税，非功能性 bug。
- **建议**: 维度类模型（XptCellModel）已缓存 cellPosition，可在解析期填充缓存替代运行时 indexOf。

### [P3] ExcelCellRef.parse 不支持 sheet 名内含 '!' 的引号引用（显式报错，非错绑）

- **文件**: `nop-format/nop-excel/src/main/java/io/nop/excel/util/ExcelCellRef.java:85-98`
- **维度**: D1（坐标解析边界）
- **证据**:
```java
int exclamationPos = text.indexOf('!');          // 取第一个 '!'
if (exclamationPos >= 0) {
    sheetName = text.substring(0, exclamationPos); // "'My!Sheet'!A1" → sheetName="'My"
    ...
    if (sheetName.startsWith("'") && sheetName.endsWith("'") && sheetName.length() >= 2) { ... }
```
- **现状**: Excel 允许引号包裹的 sheet 名包含 `!`（`'My!Sheet'!A1`），本实现按第一个 `!` 切分导致引号判断失败、引用解析为非法。
- **风险**: 结果是抛 `ERR_EXCEL_INVALID_CELL_REF`（显式失败），不会错绑数据；且要求 sheet 名含 `!`，现实频率低。故仅 P3。
- **建议**: 若需支持，先探测前导单引号并匹配闭合引号再定位 `!`。

## 维度小结（未单列发现的维度）

- **D2 资源管理**: 本模块无 POI/OPCPackage/InputStream 托管代码（pom 依赖仅 nop-format/nop-xlang/nop-office-model）；唯一的 I/O 是 SimpleHtmlSplitter 读取 IResource 与 FileHelper 写文件，无流泄漏点。zip 炸弹防护、Workbook close 等属于 nop-ooxml-xlsx 模块职责，本次未审。
- **D3 并发**: 静态 Map（枚举表、ExcelFormatHelper.formats）均为初始化后只读的 HashMap，并发读安全；SimpleDateFormat 每次调用新建（createDateFormat 不缓存实例），自定义 Format（SSN/Phone/Zip）内部委托无状态且不可变的 FastIntegerFormatter（已验证线程安全），未发现共享可变状态。ImportExcelParser 实例含 IEvalScope/LocalCache，按一次性使用设计，未见跨线程共享。
- **D5 安全**: 公式注入写出面（CSV/Excel 单元格以 = + - @ 开头的数据转义）不在本模块；SimpleHtmlSplitter 输出文件名经 `StringHelper.safeFileName` 防路径穿越；HTML 渲染的 XSS 点仅 reportId 一处（见 P3，当前不可利用）；XML 解析使用 nop 自研 XNodeParser，无外部实体扩展面。
- **D7 平台规范**: 未发现 private 字段注入、`@Inject`/`@InjectValue` 使用（本模块无 bean 注册需求，_vfs 下仅 registry/imp 模型/xlib，无 beans.xml）；`new RuntimeException`/`printStackTrace`/空 catch 均未发现；D7 问题仅 IllegalArgumentException 一处（P3）。
- **D8 契约**: ITableDataEventListener 事件序列在 TreeTableDataParser / ImportDataCollector / TableBlockCollector 三方实现间对称（begin/end 配对、fieldName==null 时 push/pop 条件一致）；`ICellView.getText()` 对 proxy cell 返回 null 的契约由"_ExcelCell 的 value 恒为 null"隐式满足，与接口注释一致。
