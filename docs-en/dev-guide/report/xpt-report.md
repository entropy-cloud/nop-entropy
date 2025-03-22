# Report Engine

## Initialize Data

In the Excel report file, the entire report's initialization logic is set via the `XptWorkbookModel` Sheet. It corresponds to the `workbook.xdef` model in the workbook model (`XptWorkbookModel` definition).

Generally, we execute initialization code in the `【展开前】` xpl section.

```c:script
let entity = {...}
assign("entity", entity);

let ds1 = [...];
xptRt.makeDs("ds1", ds1);
```

The `assign` function will set the variable to the scope context, making it accessible within the report cell. Otherwise, the variable's scope is limited to the script context and not exposed outside.

The `xptRt.makeDs` function wraps a list of data into a `ReportDataSet` object and sets it to the scope context.

## Cell Expression

In a cell, we can set the cell value using an EL expression, such as `${entity.name}`.

Within the execution environment of the cell expression, there is a global variable `xptRt` corresponding to the `IXptRuntime` type, which provides the following properties and methods:

- `xptRt.cell`: Current cell (ExpandedCell type)
- `xptRt.row`: Current row
- `xptRt.table`: Current sheet
- `xptRt.sheet`: Current Excel sheet
- `xptRt.workbook`: Current Excel workbook
- `xptRt.field(name)`: Retrieves the field value from the nearest context object
- `cell`: Equivalent to `xptRt.cell`
- `row`: Equivalent to `xptRt.row`
- `table`: Equivalent to `xptRt.table`
- `sheet`: Equivalent to `xptRt.sheet`
- `workbook`: Equivalent to `xptRt.workbook`

## Cell Properties

### cell Object Properties

The cell corresponds to the `ExpandedCell` type.

- `rowParent` or `rp`: Row parent
- `colParent` or `cp`: Column parent
- `expandValue` or `ev`: Value returned by the cell expansion expression
- `expandIndex` or `ei`: Index in the parent row/column, starting from 0
- `value`: Current cell value

## Cell Expansion

In the cell's annotation, we can set the following properties:

- `expandType`: 'r' for row direction, 'c' for column direction
- `expandExpr`: If not empty, the expression should return a collection. The current cell will expand into multiple cells based on this collection.
- `ds`: Current data source object
- `field`: If `expandExpr` is not specified but `field` is set, aggregate the data set by this field and then expand based on the aggregation result.
- `keepExpandEmpty`: If expansion returns an empty collection, default behavior will remove the cell and its child cells. Setting `keepExpandEmpty` will preserve these cells but set their values to `null`.
- `rowParent`: Row parent, must be a cell with `expandType` set
- `colParent`: Column parent, must be a cell with `expandType` set
- `expandMinCount`: Minimum number of elements to expand; if the expansion result is smaller than this count, fill with `null`
- `expandMaxCount`: Maximum number of elements to expand; exceeding elements will be discarded
- `formatExpr`: Format expression for `formattedValue`
- `dict`: Dictionary for translating text values

The annotation can reference properties from the `workflow.xdef` model's `cell` node.

In the cell content, we can directly write expression syntax. Advantages include:
- The expression result is directly visible in the interface without expanding annotations.
- No need to expand the cell's annotations to see the expression result.

## Cell Expression Types

There are two types of text expressions:

1. EL expression: Example `${entity.myField}`
2. Expansion expression: Uses `*=` as the prefix, such as `${*:=entity.myField}`

A. `*=fieldName` is equivalent to setting the configuration `field=fieldName`.

B. `*=^ds1!fieldName` translates to a configuration where `expandType=r`, `ds=ds1`, and `field=fieldName`.

C. `*=>ds1!fieldName` corresponds to a configuration with `expandType=c`, `ds=ds1`, and `field=fieldName`.

D. `*=^fieldName@data` sets the configuration as `expandType=r`, `expandExpr=data`, and `field=fieldName`.

## Hierarchical Coordinates

Hierarchical coordinates are a concept introduced by Finer Report to locate data in a report. The current implementation in the Nop platform closely resembles that of FanSoft Report: [Hierarchical Coordinates](https://help.fanruan.com/finereport/doc-view-3802.html).

Hierarchical coordinates can be categorized into relative and absolute coordinates:

![hierarchical-coord-image](xpt-report/relative-coord.png)

![absolute-coord-image](xpt-report/absolute-coord.png)

![absolute-val-image](xpt-report/absolute-coord-value.png)

## Report Global Configuration

### In the `XptWorkbookModel` sheet, global parameters can be configured.

- **Expand Before**: This is the xpl code called by the report engine before the hierarchical expansion algorithm. Data preparation can be done here using the `assign` function to set data into the report context.
- **Delete Hidden Cells**: By default, hidden rows or columns are output. To remove them, set this option to `true`.

## Common Formulas

1. In cell C3 (Percentage), directly use the proportion formula: `=PROPORTION(B3)`. Percentage: The current value represents the proportion of the total.

2. Comparison: Between the current value and the first value. Formula: Subtract the first value or divide by it (`C2/C2[A2:1]`).

3. Month-over-Month Comparison: Compare the current month's value with the previous month's. Formula: `IF(B4.expandIndex > 0, C4 / C4[A4:-1], '--')`. Month: B4; Amount: C4.

4. Cell Expansion Position: A2.expandIndex starts from 0, corresponding to FanSoft expressions: `&A2`.

5. Same Period Comparison: Between the current period and the previous periods.

## Built-in Functions

For details, refer to [ReportFunctions](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-report/nop-report-core/src/main/java/io/nop/report/core/functions/ReportFunctions.java) in the Nop-Entropy repository. The following functions are defined:

- `SUM`
- `PRODUCT`
- `COUNT`
- `COUNTA`
- `AVERAGE`
- `MIN`
- `MAX`
- `NVL(value, defaultValue)`
- `PROPORTION`
- `RANK`
- `ACCUMULATE`

## Exporting Excel Formulas

If an Excel cell contains an Excel formula (`valueExpr`), the report will convert it to the corresponding Excel formula upon export. For example, `SUM(A3)` becomes `SUM(A3:D5)` after expansion.

For complex hierarchical expressions that cannot be directly converted into Excel formulas, you can set `valueExpr` and enable `exportFormula=true` in the cell properties.

## Integration with Finer Report

Finer Report, an open-source data processing middleware, offers a mechanism similar to report expressions for handling heterogeneous data. The Nop platform allows seamless integration of the SPL engine. For details, refer to [spl.md](spl.md).

## Common Expressions

- `xptRt.seq(seqName)`: Equivalent to `seqName++`, which retrieves the value of `seqName` and increments it. If `seqName` does not exist, it is initialized to 1.

## Debugging

In the `XptWorkbookModel` configuration, you can enable debugging by setting `dump=true`. This logs intermediate results to the `dumpDir` directory (default: `./target`). The log file name format is `{seq}-{cellPos}.html`.

![debug-file-image](xpt-report/report-dump.png)

The content of each cell in the debug file is `cellText <- cellLayerCoordinate`.

Additionally, the parent-child relationship of report cells is logged.

## Common Issues

### 1. Difference between `cell.expandValue` and `cell.value`
The expansion and computation of cell values are two separate steps. The first step involves expanding based on the hierarchical coordinate, where `expandType` is used as the expression for expansion, resulting in an expanded value stored in `expandValue`.

Only cells that have both `expandType` and `expandExpr` set will contain an expandable value.

After full expansion, the computation step follows, using `valueExpr` to compute the cell's value. If `valueExpr` is not set, the cell falls back to its default expandable value stored in `expandValue`.


### 2. Supporting Default Multi-line
* Configure `expandInplaceCount` within the cell.
* Implement multi-line insertion in the template based on `valueExpr`.
* If the number of returned items by `expandExpr` is less than the specified count, no new cells need to be added.


### 3. Handling Large Data Set
* Configure `expandExpr` in the cell to control which specific entries are expanded.
* Use `expandMinCount` to limit the number of displayed entries.
* The simplest approach is to use `expandMinCount` directly for limiting purposes.

