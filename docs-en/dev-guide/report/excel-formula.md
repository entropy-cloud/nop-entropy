# Support Excel Formulas When Exporting Reports

## 1. If an Excel formula is set in the report template, it will be automatically converted into an Excel formula when exporting to Excel

For example =SUM(A3:B4)

After exporting to Excel, cell range expressions like A3:B4 will be replaced with the expanded cell range, e.g., =SUM(A3:B10)

## 2. Filter cells based on conditions

Excel formulas have some special syntax that can be used for dynamic filtering. However, implementing it the Excel way would require adding a lot of special handling to the expression engine.
To reduce the workload of the report engine, the current choice is to introduce a special syntax.

NopReport specially recognizes a special format of the IF statement, IF(testExpr, rangeExpr), and then rewrites it into a ReportExpr.

For example IF("e.rp.ev.name=='%'", C2:D2)

1. The first argument is a ReportExpr in string form, which will be parsed by NopReport’s expression engine.
   In the expression, the parameter e refers to the cell being evaluated. Note that the specific expression syntax is ReportExpr, which is not the same as Excel formula syntax; for example, equality uses ==.
2. The second argument is a cell range expression.

The semantics of the entire IF function are to use reportExpr to filter the cells in the C2:D2 range, returning only those cells that satisfy the condition.

Basically equivalent to the following call  `xptRpt.cells("C2:D2").filter(e=> e.rp.ev.name == '%')`

> e.rp.ev.name means to obtain the name property of the cell’s rowParent’s expandedValue. expandedValue is the value returned by expandExpr for the expanded cell.


## 3. Export Excel formulas for dynamic fields

If you can’t directly specify the Excel formula in the report template, but instead generate the Excel formula dynamically based on some configuration, and you want the exported Excel file to retain the Excel formula, you can use the following method

* Use xptRt.evalExcelFormula(formula) in valueExpr to dynamically evaluate the Excel formula and return its value
* Execute cell.setFormula(xptRt.expandExcelFormula(formula)) in processExpr. The purpose of expandExcelFormula is to replace the Range expressions in the Excel formula with the expanded cell Range
<!-- SOURCE_MD5:0725902f25db205fddec1a05ba28b301-->
