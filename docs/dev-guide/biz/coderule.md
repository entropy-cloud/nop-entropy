# 编码规则

业务系统中经常需要根据某种编码规则自动生成业务编码，比如自动生成订单号、卡号等。

## 内置CodeRule

1. 在模型中为字段增加code标签
2. 代码生成时会自动在meta中为字段增加biz:codeRule="对象名@属性名"这样的配置。在派生的meta中可以覆盖这个编码规则名。
3. 引入nop-sys-dao模块，在nop\_sys\_code\_rule表中增加对应记录，配置编码模板
4. 在保存或者修改实体的时候会触发meta中的autoExpr（根据biz:codeRule配置自动生成）

## 编码模板

ICodeRule接口支持定义一组变量模式，然后解析编码模板，替换其中的变量生成编码字符串。

编码模板中通过{@type:options}这种形式来表示变量，例如 D{@year}{@month}{@seq:5}表示

* 前缀为D
* 4位的年份
* 2位的月份
* 5位的顺序递增的顺序号，如果达到最大值则开始回绕

|name|pattern|description|
|---|---|---|
|year|{@year}|年份|
|month|{@month}|月份,固定两位数字|
|dayOfMonth|{@dayOfMonth}|月内的日期，1到31|
|hour|{@hour}|小时，固定两位数字|
|minute|{@minute}|分钟，固定两位数字|
|second|{@second}|秒,固定两位数字|
|randNumber|{@randNumber:3}|随机数，通过options声明生成几位的随机数字|
|seq|{@seq:3}|根据顺序号递增，取固定3位数字|
|prop|{@prop:entity.type,3}|表示从上下文对象的哪个属性中获取变量值，可以通过一个可选的长度字段来固定返回的字符串长度|

## 举例
配置NopSysCodeRule的【编码模式codePattern】为 `D{@year}{@month}{@seq:5}` 则可能生成 D20240912345，其中09对应于9月份，12345是长度为5的顺序生成的序列号。

在NopSysCodeRule对象的【序列号名称seqName】字段中配置`{@seq:5}`这个模式所用到的序列号对象名称，它对应于NopSysSequence中的配置项。

## 注册扩展变量

可以在beans.xml中定义扩展的编码规则变量。约定bean的名称为 nopCodeRuleVariable\_xxx
