# Nop Introduction: Creative Expansion of GraphQL

The Nop platform does not utilize popular open-source libraries like `graphql-java` for GraphQL implementation. Instead, we built a custom NopGraphQL engine from scratch. This engine has introduced innovative solutions and extended the capabilities of GraphQL, enhancing its practicality and versatility.

For detailed documentation, refer to [graphql/index.md](../../dev-guide/graphql/index.md).


## Section 1: Utilizing Fragments to Simplify GraphQL Queries

In GraphQL, developers must explicitly specify the fields they want to retrieve. When dealing with numerous fields, this can become cumbersome. We leverage the Fragment feature in GraphQL to define common field sets and then reference these fragments during queries, thereby simplifying the process.


### 1.1 Adding Selections in XMeta Metadata Models

In Nop's backend service objects, each is associated with an XMeta metadata model file. Within this file, we can supplement GraphQL type information by adding selections.

```xml
<meta>
  <selections>
    <selection id="F_defaults">
      userId, userName, status, relatedRoleList{ roleName }
    </selection>
  </selections>
</meta>
```

* Here, `F_` prefix ensures that only frontend-accessible Fragment definitions are considered. Selections beyond `F_defaults` have other uses.
* If `F_defaults` is not configured, it will automatically include all non-lazy fields. Specific configurations take precedence if set.


### 1.2 Referencing Fragments in Queries

When querying the backend using GraphQL, we can utilize Fragments to reference field sets:

```graphql
query {
  NopAuthUser__findList {
    ...F_defaults, groupMappings{ ...F_defaults }
  }
}
```

Alternatively, use REST calls by appending parameters like `?@selection=...F_defaults`:

```rest
/r/NopAuthUser__findList?@selection=...F_defaults,groupMappings
```

* If `@selection` is omitted in REST calls, it defaults to `F_defaults`.
* In REST queries with a single selection level, only top-level fields are included. For nested structures, `@TreeChildren(max:5)` allows recursion up to 5 levels.


## Section 2: `@TreeChildren` Directive

For tree-like data structures such as unit trees or menu trees, `@TreeChildren` simplifies hierarchical queries:

```graphql
query {
  NopAuthDept_findList {
    value: id,
    label: displayName,
    children @TreeChildren(max:5)
  }
}
```

* The `@TreeChildren(max:5)` directive limits recursion to five levels.


## Section 3: Map Type

GraphQL's strict typing can be inconvenient in dynamic scenarios. For instance, returning a collection of data might require complex structures.

NopGraphQL introduces a special Scalar type called **Map** to handle dynamic data structures:

```graphql
type QueryBean {
  filter: Map,
  orderBy: [OrderFieldBean]
}
```


## Section 4: XMeta Metadata Model

XMeta metadata models allow for various configurations through the `mapToProp` attribute.

```xml
<prop name="a" mapToProp="b.a">
</prop>
```

* This configuration maps property "a" to property "b.a".


### 4.2 Using Getters for Expressions

In NopGraphQL, `@BizLoader` can be used within BizModel services to dynamically compute fields.

```graphql

### Example in BizModel service
@BizLoader(expression: "someDynamicField")
```

# 3.2 Light Calculation Expression

For some lightweight calculations, the method of defining service functions appears too complex. In such cases, we can directly define getter expressions in XMeta.

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

### 3.2.1 Getter Expression Definition

For some lightweight calculations, instead of defining service functions, we can directly define getter expressions in XMeta.

```xml
<prop name="myValue">
  <getter>
    return entity.name + 'Ext'
  </getter>
</prop>
```

### 3.3 Field-Level Access Control

In the xmeta file, you can set the `auth` property for each `prop`.

```xml
<prop name="xx">
  <auth permissions="NopAuthUser:query" roles="admin" for="read"/>
  <auth permissions="NopAuthUser:mutation" roles="hr" for="write"/>
</prop>
```

* By setting `for="read"`, you control read access at the field level.
* By setting `for="write"`, you control write access at the field level.
* Setting `for="all"` allows both read and write access.
* The NopGraphQL engine checks access permissions for each field in the SelectionSet before executing the action, preventing scenarios where a denied field is accessed after an operation.

### 3.4 Data Dictionary Field Generation

A common requirement in business development is to translate backend field values based on a data dictionary into display text within the platform. In the Nop platform, during the loading phase, XMeta model files utilize meta-programming to dynamically determine if a data dictionary has been configured.

If a data dictionary is configured:
- A translated field will be automatically generated.

```xml
<prop name="status">
  <schema type="Integer" dict="auth/user-status"/>
</prop>
```

After meta-programming transformation, the following field definition is generated:

```xml
<prop name="status" graphql:labelProp="status_label">
  <schema type="Integer" dict="auth/user-status"/>
</prop>
```

Additionally, a reverse mapping is created for the dictionary value:

```xml
<prop name="status_label" internal="true" graphql:dictName="auth/user-status"
      graphql:dictValueProp="status">
  <schema type="String"/>
</prop>
```

### 3.5 Masking Display

Due to security considerations, sensitive user information should not be logged directly. Even during frontend display, certain data may require masking.

For example:
- Credit card numbers
- Phone numbers

You can configure `ui:maskPattern` in XMeta to specify the masking format when GraphQL returns field values.

```xml
<prop name="email" ui:maskPattern="3*4">
</prop>
```

* The pattern `ui:maskPattern="3*4"` indicates that the first 3 characters and the last 4 characters are revealed, with asterisks (*) replacing the middle characters.
