# Why Isn't GraphQL becoming popular? Is it a Design Flaw or Over-engineering?

Several years ago, there were discussions about GraphQL's benefits on platforms like Zhihu. However, even after so many years, large-scale adoption of GraphQL in commercial development remains rare. Does this imply that GraphQL has design flaws? If so, are these flaws due to under-design or over-engineering? Or is it simply because the complexity outweighs the benefits, and existing REST services already meet requirements without the need for migration?

Some colleagues who keep an eye on new technologies often have the following questions after gaining some understanding of GraphQL:

1. **Using GraphQL Requires Learning a Entirely New Set of Interfaces and Schemas.**  
   Do we need to learn all the GraphQL-specific interfaces and define custom types? Is the learning curve too steep compared to REST?

2. **GraphQL Doesn't Define Standard Error Codes.**  
   Unlike REST, where you can include error codes like `200`, `404`, or even custom `code` and `status` fields in responses, GraphQL doesn't specify any standard way to handle errors. When a GraphQL query returns an error, we can only display generic `errors` messages. Is there no standardized way to return detailed error information?

3. **GraphQL Requires Frontends to List All Required Fields.**  
   With REST, you don't need to list all fields in the frontend; you just send the necessary data. However, GraphQL requires the frontend to specify all needed fields upfront. This can become cumbersome when dealing with complex objects that require multiple nested fields. Does this make GraphQL more cumbersome than REST?

4. **How to Handle Permissions in GraphQL?**  
   GraphQL offers flexibility in data access, but does this flexibility also introduce security risks? How do common frameworks handle authorization and permissions?

These issues are indeed present in popular GraphQL frameworks. To successfully address them, some creative solutions or optimizations within the GraphQL implementation might be necessary.

---


## Simplifying GraphQL Service Development

If you were to use a specific function like `getBookById` as an example, AI would generate the following code:

```java
@Configuration
public class GraphQLConfig {

    @Bean
    public GraphQLSchema graphQLSchema() {
        String schema = "type Book { id: ID!, title: String, author: String }" +
                        "type Query { getBookById(id: ID!): Book }";
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder ->
                    builder.dataFetcher("getBookById", environment -> {
                        String bookId = environment.getArgument("id");
                        return Book.builder().id(bookId)
                            .title("Example Title")
                            .author("Example Author")
                            .build();
                    })
                )
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    }
}
```

This code uses the `graphql-java` library to parse schemas and define runtime wiring. However, this approach exposes the underlying implementation of the GraphQL engine, which is considered outdated in newer versions of Spring GraphQL. The latest version allows for simpler configuration using annotations, with all complex logic handled by the framework.

---

```java
@Controller
public class BookController {

    @QueryMapping
    public Book getBookById(@Argument Long id) {
        // ...
    }
}
```

After the framework encapsulation, you only need to know a few annotations when writing business code. Programming is focused on POJOs, and generally, you do not need to use `graphql-java`'s internal interfaces. The framework will automatically analyze Java class definitions and generate GraphQL type definitions without manual schema maintenance.

The Quarkus framework has more mature support for GraphQL and introduced similar annotation mechanisms before Spring did.
```java
@ApplicationScoped
@GraphQLApi
public class BookService {

    @Query("getBookById")
    public Book getBookById(@Name("id") Long id) {
        return ...
    }
}
```

The Nop platform's NopGraphQL framework also uses annotations to mark service functions,

```java
@BizModel("Book")
public class BookBizModel {
    @BizQuery
    public Book getBookById(@Name("id") Long id) {
        return ...
    }
}
```

Building on basic GraphQL service support, NopGraphQL also provides integration with the NopORM data access engine through CrudBizModel. It supports comprehensive operations such as complex pagination, filtering of child tables, and updating main-child table relationships, with minimal code required.

For detailed information, see [Nop Platform vs APIJSON Comparison](https://mp.weixin.qq.com/s/vrQVGs-c0dVWcOJEsOz_nA).

GraphQL's design is more pure compared to traditional Web frameworks, as it avoids introducing concepts like Request and Response that bind to the runtime environment. It is easier to encapsulate POJOs.

However, GraphQL itself only returns data in JSON format and does not support file uploads/downloads. The NopGraphQL framework addresses this limitation by introducing a `/f/upload` extension, enhancing its completeness.

See Bilibili video: [How Nop Platform Enhances GraphQL for File Upload/Download Support](https://www.bilibili.com/video/BV1y8411R7oU/)

## 二. Through GraphQL, return status codes
The overall design of GraphQL is quite comprehensive, especially with many built-in extensible features. The Nop platform leverages the `extensions` collection in GraphQLResponse to store additional status codes.

```java
@DataBean
public class GraphQLResponseBean {
    List<GraphQLErrorBean> errors;

    Object data;

    Map<String, Object> extensions;

    @JsonIgnore
    public String getErrorCode() {
        return (String) getExtension("nop-error-code");
    }

    @JsonIgnore
    public int getStatus() {
        if (extensions == null)
            return 0;
        int defaultStatus = hasError() ? -1 : 0;
        return ConvertHelper.toPrimitiveInt(extensions.get("nop-status"), defaultStatus, NopException::new);
    }
}
```

The NopGraphQL framework further enhances GraphQL by introducing more applicable features through its extension mechanism, simplifying the writing of general-purpose service logic. For detailed explanations, see [Nop Basics: How to Extend GraphQL Creatively](https://mp.weixin.qq.com/s/X0wiEQvYRIrD0UJoYBFxVA).

## 三. Equivalence Between GraphQL and REST
# Four. Utilize Fragment Syntax to Simplify Field Selection

In surface, GraphQL provides many advanced features beyond traditional REST services. Interestingly, through rigorous theoretical analysis, we'll discover that GraphQL is mathematically equivalent to adding a special `@selection` parameter for result field selection on top of standard REST services. Specifically, the Nop platform establishes equivalence between the following:

- A GraphQL query: 
```graphql
query{
  Book__get(id: 123) { name, title }
}
```

- An equivalent REST request:
```rest
/r/Book__get?id=123&@selection=name,title
```

From this perspective, **GraphQL is merely a pull-based mode of REST invocation**. In the implementation by Nop platform, NopGraphQL implements so-called minimal information expression, thus being independent of any specific runtime environment and adaptable to any interface protocol while supporting multiple request modes including GraphQL, REST, and gRPC.

For detailed explanation, refer to [Why is GraphQL strictly superior to REST in mathematical terms?](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw)

---

# Four. Simplify Field Selection with Fragment Syntax

GraphQL natively supports the Fragment concept, which essentially serves as reusable field collections that can be referenced across multiple queries. We can define a single `F_defaults` fragment for each type, encompassing all default returned fields (corresponding to non-lazy-loaded fields in Nop platform).

Example of `F_defaults` definition:
```graphql
fragment F_defaults on Book {
  title
  pages
}

fragment F_defaults on Author {
  name
  birthdate
}
```

With the help of Fragment definitions, service calls can be significantly simplified:
```graphql
query{
  Book__get(id: 123){
    ...F_defaults,
    author {
      ...F_defaults
    }
  }
}
```

The corresponding REST request would be:
```rest
/r/Book__get?id=123&@selection=...F_defaults,author
```

In REST mode, if `@selection` is not specified, it defaults to `@selection=...F_defaults`, returning all default fields (non-lazy-loaded). For object properties, if further child fields are not specified, it will automatically traverse down to the `F_defaults` level (`...F_defaults`). Thus, the author property corresponds to:
```rest
author{...F_defaults}
```

---

# Four. Field Access Control

NopGraphQL introduces per-field access configurations, similar to SpringMVC's `@Permission` annotation. In the implementation:

Example of BizModel and BizMutation with Auth configuration:
```java
@BizModel("Book")
public class BookBizModel {
  @BizMutation
  @Auth(roles = "manager", permissions = "Book:update")
  public Book update(@Name("data") Map<String, Object> data) {
    // Implementation logic...
  }
}
```

In XMeta metadata model, field-level access configurations can also be defined. For example:
- Only HR can access salary-related fields.
- Non-lazy-loaded fields are automatically included in the response.

---

# Five. Precision Field Access Control

NopGraphQL extends the standard GraphQL specification by introducing per-field access control via metadata. This allows for granular permissions, such as allowing HR to access salary data while restricting it from other fields.

Example of Auth configuration in XMeta:
```xml
<meta>
  <selections>
    <selection id="F_moreFields">
      userId, userName, status, relatedRoleList{ roleName }
    </selection>
  </selections>
</meta>
```

---

# Five. Field Access Control

NopGraphQL supports per-field access control through metadata configurations, allowing for fine-grained permissions. For instance:
- Only HR can view salary-related fields.
- Non-lazy-loaded fields are automatically included in the response.

```xml
<prop name="salary">
    <auth permissions="Employee:query" roles="admin" for="read"/>
    <auth permissions="Employee:mutation" roles="hr" for="write"/>
</prop>
```

The NopGraphQL engine checks the access rights of each field before executing the business function.

Additionally, the built-in CrudBizModel will apply the IDataAuthChecker interface's getFilter method to add filter conditions to the query.

You can configure data permissions in the `data-auth.xml` file or via the NopAuthRoleDataAuth service object for online configuration.

## Summary
* GraphQL has a complex structure compared to REST, with both advantages and disadvantages, which contributes to its popularity being hindered.
* NopGraphQL unifies the internal structures of GraphQL and REST on the mathematical level through a series of extensions, enhancing the usability of GraphQL.
* The implementation code of NopGraphQL is much simpler than SpringMVC. It adds a feature similar to GraphQL fields selection to REST services, implementing the equivalence transformation between GraphQL and REST.

NopGraphQL can be introduced in various frameworks such as Spring, Quarkus, and Solon. It supports REST services by progressively introducing GraphQL's combinatory capabilities.

