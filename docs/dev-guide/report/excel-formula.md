# 报表导出时支持Excel公式

## 1. 在报表模板中如果设置了Excel公式，则导出Excel时会自动转换为Excel公式

例如 =SUM(A3:B4)

在导出到Excel后，会将A3:B4这种单元格区间表达式替换为展开后的单元格范围，例如 =SUM(A3:B10)

## 2. 根据条件对单元格进行过滤

Excel公式中存在一些特殊语法，可以用于动态过滤，但是如果要按照Excel的方式来实现需要为表达式引擎增加大量特殊处理。
为了减少报表引擎的工作量，目前的选择是引入一种特殊语法。

NopReport特殊识别一种特殊格式的IF语句，IF(testExpr, rangeExpr)，然后把它重新翻译为ReportExpr。

举例来说 IF("e.rp.ev.name=='%'", C2:D2)

1. 第一个参数是一个字符串格式的ReportExpr，会使用NopReport的表达式引擎去解析。
   表达式中e参数表示需要判断的单元格。注意，具体的表达式语法是ReportExpr，它与Excel公式语法并不相同，比如等于判断使用==。
2. 第二个参数是单元格区间表达式。

整个IF函数的语义是使用reportExpr对于C2:D2这个区间中的单元格进行过滤，只返回满足条件的单元格。

基本等价于如下调用  `xptRpt.cells("C2:D2").filter(e=> e.rp.ev.name == '%')`

> e.rp.ev.name 表示获取cell的rowParent的expandedValue的name属性。 expandedValue是展开单元格中expandExpr返回的展开值
