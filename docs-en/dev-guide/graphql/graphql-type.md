# Data Types in NopGraphQL

## Extended Scalar Types

GraphQL has a default set of scalar types: Float, Int, String, Boolean, and ID. NopGraphQL extends these with additional Scalar Types to better support business data models. For example, distinguishing between Float, Double, Int, Long, and BigDecimal.

## Long as String Return

For JavaScript clients, large Long values cannot be handled properly in the frontend and must be converted to String type.

1. In Excel data models, define a `domain`, `string-long` within the domain definition, set the corresponding Java type to String. This ensures that entity properties are automatically converted to String during generation.
2. Another approach is to specify `graphql:type` as String in XMeta's `prop`. This ensures that when returning results in GraphQL, they are automatically converted to String.

## Mapping Java Types to GraphQL Types

Sometimes, weakly typed Java attributes are used, but you want to enforce a specific GraphQL type. Use the `@GraphQLReturn("MyObject")` annotation to specify the return type of functions and methods. If the return type is a list, the `@GraphQLReturn` specifies the element type.

## Example Using Java

```java
class ResultBean {
    List<IOrmEntity> list;

    @GraphQLReturn(bizObjName = "MyObject")
    public List<IPropGetMissingHook> getList() {
        return list;
    }
}
```

## Dynamic GraphQL Type Definition Using XBiz

Use the `return` keyword in queries to define dynamic GraphQL types.

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

Using `x:extends` allows reusing existing schema definitions.

```xml
<schema x:schema="/nop/schema/schema/schema.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <props>
        <prop name="name">
            <schema type="String" />
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

In the above example, `rows` and `rows2` have the same type definition in GraphQL.

## Type Definitions via graphql Files
Use `nop.graphql.builtin-schema-path` to import multiple virtual file paths. These files will be automatically imported into GraphQL for type definitions.

```graphql
type UserItemData {
    name: String
    rows: [UserItemData]
}
```

Then, use this type in XBiz:

```xml
<query name="testDynamicItem">
    <return graphql:type="UserItemData" />
</query>
```
