# 字段掩码

处于安全性考虑，一些敏感的用户信息不允许打印到日志文件中，返回给前台演示的时候也需要进行掩码处理，不能显示全部内容，只能显示前几位、后几位等，
例如信用卡卡号，用户的电话号码等。

## 在Excel模型中为列增加masked标签

数据模型中标注的masked标签会生成到app.orm.xml模型文件中。当ORM引擎打印SQL语句时，所有具有masked标签的字段都显示为\*\*\*XX。

> nop.core.default-masking-keep-chars 可以控制缺省掩码情况下显示最后几位字符，缺省为2

## 在meta中为prop配置ui:maskPattern，控制显示前几位以及后几位字符

```xml
<prop name="email" ui:maskPattern="3*4">
    
</prop>
```

`ui:maskPattern="3*4"` 表示保留前3位以及后4位字符，其他用\*来替换。

## 程序控制

* 在Java代码中可以通过StringHelper.maskPattern(text,"3\*4") 这种调用来进行掩码处理。
* 在sql-lib中，可以通过 `${masked(cardNo)}` 来表示需要被掩码的SQL参数。masked函数将会把value包装为MaskedValue类型，这样处理之后当框架将它打印到日志文件中时会自动进行掩码处理
