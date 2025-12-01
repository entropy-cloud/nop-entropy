# Getting Started with Nop: How to Creatively Extend GraphQL

The Nop platform does not use common GraphQL open-source libraries such as `graphql-java`; instead, it implements the NopGraphQL engine from scratch. The NopGraphQL engine introduces many novel implementation approaches that broaden GraphQL’s scope of application and enhance its practicality.

For detailed documentation, see [graphql/index.md](../../dev-guide/graphql/index.md)

## I. Simplifying GraphQL Queries with Fragment Definitions

GraphQL requires the frontend to specify the fields to be returned, which can be cumbersome when there are many fields. In such cases, you can use the Fragment feature of the GraphQL language to define common field sets and reference these fragments in queries to simplify them.

### 1.1 Add selection definitions in XMeta, prefixed with `F_`

Each backend service object in the Nop platform has an associated XMeta metadata model file, where you can augment GraphQL types with additional metadata.

```xml
<meta>
  <selections>
    <selection id="F_defaults">
      userId, userName, status, relatedRoleList{ roleName}
    </selection>
  </selections>
</meta>
```

* By convention, only fragment definitions prefixed with `F_` are accessible from the frontend. Selections have other uses as well.
* If `F_defaults` is not configured, it will be automatically inferred based on all non-lazy fields of the GraphQL type. If explicitly specified, the specified content is used.

### 1.2 Reference fragments in frontend queries

When invoking backend services via GraphQL, you can use fragments:

```graphql
query{
   NopAuthUser__findList{
     ...F_defaults, groupMappings{...F_defaults}
   }
}
```

Alternatively, when calling backend services via REST, use the `@selection` parameter to reference fragments:

```
/r/NopAuthUser__findList?@selection=...F_defaults,groupMappings
```

* When using REST, if `@selection` is not provided, it is equivalent to returning `F_defaults`.

**Under REST, if the selection only specifies object-level fields, it will be automatically expanded to nested levels.**

## II. Simplify Tree-structured Queries with the `@TreeChildren` Directive

For retrieving tree structures such as organization trees or menu trees, NopGraphQL provides an extended syntax via the directive mechanism to directly express recursive data fetching, for example:

```graphql
query {
    NopAuthDept_findList{
        value: id
        label: displayName
        children @TreeChildren(max:5)
    }
}
```

* `@TreeChildren(max:5)` indicates that, following the current level’s structure, up to 5 levels will be nested.

## III. Map Type

GraphQL is a strongly typed framework that requires all data to have explicit type definitions, which can be inconvenient in certain dynamic scenarios. For instance, sometimes you may need to return an extensible collection to the frontend.

NopGraphQL introduces a special scalar type: Map, which can be used to describe dynamic data structures. For example:

```graphql
type QueryBean{
    filter: Map
    orderBy: [OrderFieldBean]
}
```

## IV. XMeta Metadata Model

The XMeta metadata model enables many features via configuration.

### 4.1 Map to existing properties via mapToProp

```xml
<prop name="a" mapToProp="b.a">
</prop>
```

* The mapToProp attribute can specify an alias for an existing property. When the frontend accesses property a, it actually retrieves the a property on the associated object b.

### 4.2 Specify computed expressions directly via getter

In NopGraphQL, you can introduce dynamically computed fields in a BizModel service class via the `@BizLoader` annotation.

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result,
                           IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

For lightweight computed expressions, defining service functions may be overly complex; in such cases, you can define them directly in XMeta via a getter expression:

```xml
<prop name="myValue">
  <getter>
    return entity.name + 'Ext'
  </getter>
</prop>
```

### 4.3 Field-level access control

In the xmeta file, you can specify `auth` settings for a `prop`:

```xml

<prop name="xx">
    <auth permissions="NopAuthUser:query" roles="admin" for="read"/>
    <auth permissions="NopAuthUser:mutation" roles="hr" for="write"/>
</prop>
```

* This configuration enables read/write access control at the field level. `for="read"` controls read access to the field, `for="write"` controls write access, and `for="all"` allows both read and write.
* Before performing any actual actions, the NopGraphQL engine checks access permissions for each field in the selection set, ensuring you don't end up executing business operations only to discover that certain result fields are inaccessible.
* For data permissions and filter criteria for related sub-tables, see [4-complex-query.md](4-complex-query.md).

### 4.4 Automatically generate data dictionary text fields
In business development, a common requirement is to translate backend business field values into display text according to a data dictionary configuration. In the Nop platform, during the loading phase, XMeta model files use metaprogramming to dynamically determine whether a data dictionary is configured.
If so, a dictionary translation field is automatically generated.

```xml

<prop name="status">
    <schema type="Integer" dict="auth/user-status"/>
</prop>
```
After metaprogramming transformation, the following field definitions are generated:

```xml

<prop name="status" graphql:labelProp="status_label">
    <schema type="Integer" dict="auth/user-status"/>
</prop>
<prop name="status_label" internal="true" graphql:dictName="auth/user-status"
      graphql:dictValueProp="status">
    <schema type="String"/>
</prop>
```

### 4.5 Masked display
For security reasons, some sensitive user information must not be printed to log files; when returned to the frontend for display, it also needs to be masked—only showing the first few and the last few characters,
such as credit card numbers, user phone numbers, etc.

You can specify a masking display pattern via `ui:maskPattern`, and when GraphQL returns field values it will automatically apply this pattern.

```xml
<prop name="email" ui:maskPattern="3*4">

</prop>
```

* `ui:maskPattern="3*4"` means keeping the first 3 and last 4 characters, with the rest replaced by `*`.

<!-- SOURCE_MD5:759fe0800e50b054be61ffd68b25fb92-->
