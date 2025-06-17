# 结构体编解码

record模型定义了如何对消息对象进行二级制或者文本格式的编解码，也就是指定

## prop

record生成和解析时的字段名为field，一般情况下它对应于bean上的属性名，但是也可以明确指定prop。

多个field可以对应于同一个prop

1. 存在条件判断的情况： 比如在不同条件下，多个不同的field可以映射到同一个java bean属性
2. 嵌套对象的情况下record的不同field可以分别对应一个嵌套对象的一部分。比如
   part1和part2都是对象属性，且prop都配置成refObj，则可以实现解析part1得到的字段和part得到的字段都统一存放在refObj对象属性中。
