# Data Types in NopGraphQL

## Extended Scalar Types

By default, GraphQL only has the basic types Float, Int, String, Boolean, and ID. NopGraphQL introduces more Scalar types to better support business data models, for example distinguishing Float, Double, Int, Double, BigDecimal, etc.

## Returning Long as String

On the front end (JS), Long values beyond a certain magnitude cannot be processed correctly; they must be converted to the String type.

1. In the Excel data model, define a domain named string-long, and set its corresponding Java type to String in the Domain Definition. This will change the generated entity property to the String type.
2. Another approach is to specify `graphql:type` as String on the prop in XMeta; then, when returning in GraphQL, it will be automatically converted to the String type.

## Mapping Java Types to GraphQL Types

Sometimes you may use weakly typed Java properties but want to assign them a specific GraphQL type. You can use an annotation like `@GraphQLReturn("MyObject")` to specify that the object type in the function’s return type is the designated type. If the return type is a list type, GraphQLReturn specifies the type of the elements in the list.



```java
class ResultBean {
      List<IOrmEntity> list;

        @GraphQLReturn(bizObjName = "MyObject")
        public List<IPropGetMissingHook> getList() {
            return list;
        }
}
```

## Defining Dynamic GraphQL Types with XBiz

You can define GraphQL types via the schema configuration of the return.

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

You can reuse existing schema definitions through `x:extends`.

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

In the above example, the definitions of rows and rows2 result in the same types at the GraphQL layer.

## Import type definitions via GraphQL files
You can specify multiple GraphQL virtual file paths via `nop.graphql.builtin-schema-path`; the type definitions in these files will be automatically imported into GraphQL.

```graphql
type UserItemData{
    name:String
    rows:[UserItemData]
}
```
Then you can directly use this type name in XBiz.

```xml
<query name="testDynamicItem">
   <return graphql:type="UserItemData" />
</query>
```
<!-- SOURCE_MD5:cf0a75451cc9b1d54626eaea720d60a5-->
