# Report Export Supports Excel Formulas


## 1. If Excel formulas are set in the report template, then exporting to Excel will automatically convert them into Excel formulas.


## Example:
` =SUM(A3:B4)`

After exporting to Excel, it will replace the cell range expression `A3:B4` with the expanded cell range, such as:
` =SUM(A3:B10)`



Excel formulas contain some special syntax that can be used for dynamic filtering. However, implementing this in the same way as Excel requires adding a lot of special handling for the expression engine.

To reduce the workload of the report engine, we have decided to introduce a special syntax.


`NopReport` identifies a special format of the IF statement, `IF(testExpr, rangeExpr)`, which is then translated into `ReportExpr`.


` IF("e.rp.ev.name=='%'", C2:D2)`

1. The first parameter is a string formatted as `ReportExpr`. It uses the `NopReport` expression engine to parse it.
   - The expression engine interprets `e` as the cell whose parent is being evaluated.
   - Note: The specific syntax of the expression is `ReportExpr`, which differs from Excel's formula syntax, such as using `==` instead of `=`.

2. The second parameter is a cell range expression.

The entire IF function's purpose is to use `reportExpr` for filtering cells in the `C2:D2` range based on the condition, returning only cells that meet the criteria.

This is essentially equivalent to the following call:
```javascript
xptRpt.cells("C2:D2").filter(e => e.rp.ev.name == '%')
```


- `e.rp.ev.name` retrieves the `name` property of the cell's parent's expanded value.
- `expandExpr` returns the expanded value of a cell in the report template.

