# 层次坐标

层次坐标是由润乾报表所首创的一种报表数据定位方式。目前Nop平台的实现类似于帆软报表的设计：[层次坐标](https://help.fanruan.com/finereport/doc-view-3802.html)

层次坐标分为相对坐标和绝对坐标：

![](xpt-report/relative-coord.png)

![](xpt-report/absolute-coord.png)

![](xpt-report/absolute-coord-value.png)

## 常用公式

1. 在C3(占比)单元格中直接使用占比公式：=PROPORTION(B3)；占比：当前值占总值的比例

2. 比较：当前值与第一个值做比较 计算公式为：当前值减去第一个值（C2/C2[A2:1]）

3. 环比：当前值比上月份的值； 计算公式  IF(B4.expandIndex > 0 , C4 / C4[B4:-1] , '--') , B4为月份，C4为金额

4. 单元格展开位置： A2.expandIndex 从0开始，对应于帆软表达式的  &A2

5. 同期比： 今年/去年-1   坐标公式；EVAL(CONCATENATE("if(&A3>1,C3/C3[!0]{A3=",A3-1,"&&B3=$B3},0)"))

含义解释：{A3=",A3-1,"&&B3=$B3}  这里表达的意思为：

A3=",A3-1," （指上一年年份）

B3=$B3（月度相同）
