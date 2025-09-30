# Why Has GraphQL Struggled to Become Popular? Under-Design or Over-Design?

Years ago, discussions about GraphQL’s role already appeared on Zhihu. After all these years, we still rarely see large-scale use of GraphQL in commercial development. Does this imply a flaw in GraphQL’s design? If so, is it under-designed or over-designed? Or perhaps there’s no design flaw at all—it’s simply high in complexity and existing REST services are sufficient, leaving little motivation to migrate?

Some colleagues who keep a close eye on new technologies often raise the following questions once they get acquainted with GraphQL:

1. Using GraphQL requires learning a series of GraphQL-related interfaces and defining GraphQL types. Does this mean we need to learn a complete set of concepts and practices entirely different from REST? Is the learning curve too steep?

2. The GraphQL protocol does not specify return status codes, whereas for typical REST requests we include a code or status field in the return object to determine whether an error occurred and, if so, its specific cause. When a GraphQL request fails, can we only display the errors message and cannot handle it based on standardized error status codes?

3. GraphQL forces the frontend to specify all required field names in the request, which often results in writing more than ten fields across many objects on the frontend, while REST requests do not require this. Doesn’t this make GraphQL much more cumbersome to use than REST?

4. How is authorization controlled in GraphQL? Does a data access mechanism this flexible easily lead to security issues?

These problems do exist in common GraphQL frameworks. To solve them successfully requires some creative modifications to GraphQL.

## I. Simplifying GraphQL Service Development

If you ask Zhipu Qingyan AI to write a GraphQL example using the getBookById function, it will generate the following code:

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
                    // Add logic to fetch the book here; this is just an example
                    return Book.builder().id(bookId)
                        .title("示例书名").author("示例作者").build();
                }))
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(
                  typeDefinitionRegistry, runtimeWiring);
    }
}
```

This admittedly looks quite complex. The `graphql-java` library introduces a series of concepts such as schema parsing, type definition registration, and runtime wiring. Specific service functions must be written in the form of DataFetchers and obtain frontend parameters via DataFetchingEnvironment. However, this style exposes the underlying implementation of the GraphQL engine and is already somewhat outdated. In the latest Spring GraphQL framework, we only need a few simple annotations—the framework handles the complexity automatically.

```java
@Controller
public class BookController {

	@QueryMapping
	public Book getBookById(@Argument Long id) {
		// ...
	}
}
```

With the framework encapsulation, when writing business code we only need to know a small number of annotations and program against POJOs; generally there is no need to use internal `graphql-java` interfaces. The framework automatically analyzes Java class definitions and generates GraphQL type definitions, so there is no need to maintain schema definitions manually.

The Quarkus framework has even more mature support for GraphQL; it introduced similar annotation mechanisms before Spring.

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

The Nop Platform’s NopGraphQL framework likewise uses annotations to mark service functions,

```java
@BizModel("Book")
public class BookBizModel{
   @BizQuery
   public Book getBookById(@Name("id") Long id){
     ...
   }
}
```
On top of basic GraphQL service support, NopGraphQL integrates with the NopORM data access engine through CrudBizModel, providing comprehensive support for commonplace CRUD operations (supporting complex paginated queries, subtable data filtering, and master-detail data updates), typically without the need to write additional code.

For a detailed introduction, see [Feature Comparison Between the Nop Platform and APIJSON](https://mp.weixin.qq.com/s/vrQVGs-c0dVWcOJEsOz_nA).

GraphQL’s design is purer than traditional web frameworks; it does not introduce concepts bound to the web runtime environment such as Request and Response, making it easy to achieve a fully POJO-oriented encapsulation. However, GraphQL itself returns in a fixed JSON format and cannot implement file upload/download functionality. NopGraphQL adds the `/f/upload` extension for this, making NopGraphQL’s semantics more complete.

See the Bilibili video: [How the Nop Platform Adds File Upload/Download Support to GraphQL](https://www.bilibili.com/video/BV1y8411R7oU/)

## II. Extending GraphQL to Return Status Codes
The overall design of the GraphQL protocol is fairly complete, especially with many built-in extensibility features. The Nop Platform uses the extensions map in GraphQLResponse to store additional return status codes.

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
        return
           ConvertHelper.toPrimitiveInt(extensions.get("nop-status"),
               defaultStatus, NopException::new);
    }
}
```

Leveraging GraphQL directives as an extension mechanism, the NopGraphQL framework also introduces more application-related features to simplify the development of typical application services. For details, see [Nop Primer: How to Creatively Extend GraphQL](https://mp.weixin.qq.com/s/X0wiEQvYRIrD0UJoYBFxVA)

## III. The Equivalence of GraphQL and REST
On the surface, GraphQL provides many advanced features that go beyond traditional REST services. Interestingly, rigorous theoretical analysis reveals that GraphQL is mathematically equivalent to augmenting a conventional REST service with a special `@selection` parameter for selecting result fields. Specifically, the Nop Platform establishes the following equivalence between a GraphQL request and a REST request, allowing the same backend service function to be accessed via both GraphQL and REST protocols.
```graphql
query{
  Book__get(id: 123) { name, title}
}
```

Equivalent to
```
/r/Book__get?id=123&@selection=name,title
```

From this perspective, **GraphQL is merely a pull-mode REST call**. In the Nop Platform implementation, NopGraphQL achieves so-called minimal information expression; it has no dependency on any specific runtime environment, can be adapted to any interface protocol, and simultaneously provides multiple invocation methods including GraphQL, REST, and gRPC.

For a detailed introduction, see [Why Is GraphQL Strictly Superior to REST in the Mathematical Sense?](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw)

## IV. Simplifying Field Selection with Fragments

GraphQL has a built-in concept called fragments, which are essentially reusable field sets that can be referenced in multiple queries. We can define an `F_defaults` fragment for each type, containing all default return fields (corresponding to non-lazy-loaded fields in the Nop Platform).

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

With fragment definitions, service invocation can be simplified.
```graphql
query{
   Book__get(id:123){
      ...F_defaults,
      author {
        ...F_defaults
      }
   }
}
```

In the NopGraphQL framework, the REST call equivalent to the above GraphQL is:
```
/r/Book__get?id=123&@selection=...F_defaults,author
```

When using the REST style, if you don’t specify the `@selection` parameter, it is equivalent to `@selection=...F_defaults`, which returns all default fields (non-lazy-loaded fields). For object properties, if you don’t specify its subfields, it is also equivalent to selecting one level down of `F_defaults`. Therefore, the author property above actually corresponds to `author{...F_defaults}`.

`F_defaults` is not the only field set we can use. In the XMeta metadata model, we can define other field sets as well.

```xml
<meta>
  <selections>
    <selection id="F_moreFields">
      userId, userName, status, relatedRoleList{ roleName}
    </selection>
  </selections>
</meta>
```

One additional point to note is that **standard GraphQL engine implementations do not allow fragments to share the same name**, meaning you cannot define multiple F_defaults; each fragment name must be globally unique. Therefore, defining F_defaults for both the Book and Author types is not valid in standard GraphQL. The approach in NopGraphQL can be viewed as an extension to the existing GraphQL specification.

## V. Field-Level Access Control

NopGraphQL introduces auth configuration. At the method level, the configuration is similar to the common @Permission annotation in Spring MVC.

```java
@BizModel("Book")
public class BookBizModel{
  @BizMutation
  @Auth(roles="manager",permissions="Book:update")
  public Book update(@Name("data") Map<String,Object> data){
    ...
  }
}
```

In the XMeta metadata model, we can also specify auth configuration for each field. For example, you can configure that only HR personnel may view employee salaries.

```xml
<prop name="salary">
    <auth permissions="Employee:query" roles="admin" for="read"/>
    <auth permissions="Employee:mutation" roles="hr" for="write"/>
</prop>
```

Before actually executing business functions, the NopGraphQL engine invokes `GraphQLActionAuthChecker` to verify access permissions for each field in the selection set.

In addition, during queries the built-in CrudBizModel applies methods such as getFilter in the IDataAuthChecker interface to append data-permission filters to query conditions. You can configure data permissions in `data-auth.xml`, or configure them online via the NopAuthRoleDataAuth service object.

## Summary
* The original form of GraphQL is relatively complex; compared with REST calls it has pros and cons, which creates some resistance to its widespread adoption.
* NopGraphQL unifies the intrinsic structures of GraphQL and REST at the mathematical level and enhances GraphQL’s usability through a series of extensions.
* The NopGraphQL engine implementation is far simpler than Spring MVC; it adds GraphQL-like field selection capabilities to REST services, achieving an equivalence transformation between GraphQL and REST.
* NopGraphQL support can be introduced into the Spring, Quarkus, and Solon frameworks, gradually layering in GraphQL’s compositional calling capabilities on top of REST services.

<!-- SOURCE_MD5:16875afa29849c704d2c90e0f2b6c68a-->
