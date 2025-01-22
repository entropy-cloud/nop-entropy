# NopGraphQL中的数据类型

## 扩展的Scalar类型

GraphQL缺省只有Float、Int、String、Boolean、ID这几种基本类型。NopGraphQL引入了更多的Scalar类型，以便于更好的支持业务数据模型。
比如区分了Float、Double、Int、Double、BigDecimal等。

## Long作为String返回

对于前台JS而言，超过一定大小的Long无法在前台正常处理，必须要转换成String类型。

1. 在Excel数据模型中定义一个domain，string-long，在【域定义中】设置它对应的Java类型为String。这样在生成实体属性的时候会修改为String类型。
2. 另外一种做法是在XMeta的prop上指定`graphql:type`为String，这样在GraphQL中返回的时候会自动转换成String类型。

## Java类型映射到GraphQL类型

有时会使用弱类型的Java属性，但是希望为它指定某个确定性的GraphQL类型。可以通过`@GraphQLReturn("MyObject")`这种注解来指定函数返回值类型中的对象类型为指定类型。如果返回类型是列表类型，则GraphQLReturn指定的是列表中的元素的类型。



```java
class ResultBean {
      List<IOrmEntity> list;

        @GraphQLReturn(bizObjName = "MyObject")
        public List<IPropGetMissingHook> getList() {
            return list;
        }
}
```

## 使用XBiz来定义动态GraphQL类型

通过return的schema配置，可以定义GraphQL类型。

```xml
 <query name="testDynamicItem">
      <arg name="id" type="String" />
      <return>
          <schema x:extends="ItemSchema.schema.xml" />
      </return>

      <source>
         import io.nop.auth.service.biz.ItemData;

         const ret = new ItemData();
         ret.name = "a";
         ret.rows = [];
         return ret;
      </source>
  </query>
```

通过`x:extends`可以复用已有的schema定义。

```xml
<schema x:schema="/nop/schema/schema/schema.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <props>
        <prop name="name">
            <schema type="String"/>
        </prop>

        <prop name="rows" graphql:type="[NopAuthUser]" />

        <prop name="rows2">
            <schema>
                <item bizObjName="NopAuthUser" />
            </schema>
        </prop>
    </props>
</schema>
```

上面的示例中rows和rows2的定义在GraphQL层面产生的类型是一样的。

## 通过graphql文件引入类型定义。
通过`nop.graphql.builtin-schema-path`可以指定多个graphql虚拟文件路径，这些文件中的类型定义会被自动引入到GraphQL中。

```graphql
type UserItemData{
    name:String
    rows:[UserItemData]
}
```
然后在xbiz中就可以直接使用这个类型名

```xml
<query name="testDynamicItem">
   <return graphql:type="UserItemData" />
</query>
```
