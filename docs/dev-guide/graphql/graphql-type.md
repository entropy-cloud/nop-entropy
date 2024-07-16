# NopGraphQL中的数据类型

## 扩展的Scalar类型
GraphQL缺省只有Float、Int、String、Boolean、ID这几种基本类型。NopGraphQL引入了更多的Scalar类型，以便于更好的支持业务数据模型。
比如区分了Float、Double、Int、Double、BigDecimal等。

## Long作为String返回
对于前台JS而言，超过一定大小的Long无法在前台正常处理，必须要转换成String类型。
1. 在Excel数据模型中定义一个domain，string-long，在【域定义中】设置它对应的Java类型为String。这样在生成实体属性的时候会修改为String类型。
2. 另外一种做法是在XMeta的prop上指定`graphql:type`为String，这样在GraphQL中返回的时候会自动转换成String类型。
