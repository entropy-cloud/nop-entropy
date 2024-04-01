# 空值的处理

不同的数据库对于空字符串的处理逻辑不一致。Oracle数据库不支持空字符串，当一个字段值被设置为空字符串时，实际保存到数据库中的是null值。

NopORM为了确保在不同数据库之间的可迁移性，它对于空字符串进行了特殊识别和处理，具体代码参见DialectImpl.getDataParameterBinder函数的实现。

如果字段值设置为空字符串，则保存到数据库中的时候会自动修改为null。可以通过设置nop.orm.auto\_convert\_empty\_string\_to\_null来关闭这一行为。
