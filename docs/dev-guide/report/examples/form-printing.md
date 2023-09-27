# 套打

套打的做法如下：
1. 在报表中插入图片
2. 设置图片打印的时候不显示

![](form-printing/form-printing.png)

## 动态生成图片

在图片上点击右键【查看可选文字】，然后在【替换文字】信息中通过dataExpr表达式来生成图片数据，返回格式为byte[]或者IResource对象。

注意：需要插入单独的一行`-----`表示后续是表达式部分。

![](form-printing/data-expr.png)