# GraphQL为什么流行不起来？是设计不足还是设计过度？

很多年以前知乎上就有人对GraphQL的作用进行过讨论，这么多年过去，在实际的商业开发中还是很少见到大规模使用GraphQL的情况。这是否意味着GraphQL的设计存在问题，如果存在，到底是设计不足还是设计过度导致的？亦或是并没有什么设计上的缺陷，仅仅是因为复杂度高，而现有的REST服务已经够用，没有迁移的动力？

有些比较关注新技术的同学，对于GraphQL有了一定的了解之后，经常会提出如下几个疑问：

1. 使用GraphQL还需要学习GraphQL相关的一系列接口，还需要定义GraphQL类型，是不是要学习一整套完全不同于REST的概念和做法？学习曲线是否过于陡峭？

2. GraphQL协议没有规定返回状态码，而一般REST请求我们会在返回对象中规定一个code或者status字段，根据它可以判断是否出错或者出错后具体的出错原因等。那么在GraphQL请求报错的时候我们只能显示errors消息，而不能根据规范化的错误状态码来进行处理吗？

3. GraphQL强制前端在请求里填写所有需要的字段名，导致前端很多时候在很多对象里写10个以上的字段，而REST请求不需要。GraphQL使用起来岂不是比REST要繁琐很多？

4. GraphQL中权限怎么控制？那么灵活的数据访问机制是不是会很容易导致安全性问题？

以上问题在常见的GraphQL框架中确实存在，要成功的解决它们需要对GraphQL进行一点创造性的改造。

## 一. 简化GraphQL服务编写

如果你要求智谱清言AI以getBookById函数为例写一个GraphQL的示例，它会生成如下代码

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
                    // 这里应该添加获取书籍的逻辑，这里只是一个示例
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

这看着确实非常复杂，这里用到的`graphql-java`库引入了Schema解析，TypeDefinition注册，运行时Wiring等一系列概念。具体的服务函数还需要写成DataFetcher形式，通过DataFetchingEnvironment来获取前台参数。但是，这种写法实际上是暴露了GraphQL引擎的底层实现，已经是属于比较过时的写法。在最新的Spring GraphQL框架中，我们只需要使用几个简单的注解，复杂的工作完全由框架自动完成。

```java
@Controller
public class BookController {

	@QueryMapping
	public Book getBookById(@Argument Long id) {
		// ...
	}
}
```

经过框架封装后，我们编写业务代码的时候只要知道少数几个注解就够了，编程的时候只需要面向POJO，一般并不需要使用`graphql-java`的内部接口。框架会自动分析Java类的定义，并自动生成GraphQL类型定义，也不需要手工维护schema定义。

Quarkus框架对于GraphQL的支持更加成熟，它早于Spring之前就引入了类似的注解机制。
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

Nop平台的NopGraphQL框架同样是使用注解来标记服务函数，

```java
@BizModel("Book")
public class BookBizModel{
   @BizQuery
   public Book getBookById(@Name("id") Long id){
     ...
   }
}
```
在基本的GraphQL服务支持基础之上，NopGraphQL还通过CrudBizModel提供了与NopORM数据访问引擎的集成，对于常见的增删改查操作（支持复杂分页查询、子表数据过滤、主子表数据更新）提供了完善的支持，一般不需要再编写相关代码。

详细介绍参见[Nop平台与APIJSON的功能对比](https://mp.weixin.qq.com/s/vrQVGs-c0dVWcOJEsOz_nA)。

GraphQL的设计本身比传统的Web框架更加纯粹，没有引入Request、Response等这种与Web运行时环境绑定的概念，很容易实现完全面向POJO的封装。
不过GraphQL本身固定使用JSON格式返回，无法实现文件的上传下载功能。NopGraphQL为此增加了`/f/upload`扩展，使得NopGraphQL的语义变得更加完整。

参见B站视频： [Nop平台如何为GraphQL引入文件上传下载支持](https://www.bilibili.com/video/BV1y8411R7oU/)

## 二. 通过GraphQL扩展返回状态码
GraphQL协议的整体设计还是相当完整的，特别是它内置了很多可扩展特性。Nop平台利用GraphQLResponse中的extensions集合来保存额外的返回状态码。

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

NopGraphQL框架利用GraphQL的Directive当扩展机制还引入了更多应用相关的特性，简化了一般应用服务的编写。详细介绍参见 [Nop入门：如何创造性的扩展GraphQL](https://mp.weixin.qq.com/s/X0wiEQvYRIrD0UJoYBFxVA)

## 三. GraphQL与REST的等价性
表面上看起来GraphQL提供了很多超越传统REST服务的高级功能，但是有趣的是，经过严格的理论分析，我们会发现GraphQL在数学层面上等价于在普通的REST服务基础上补充一个特殊的`@selection`参数用于结果字段选择。具体来说，Nop平台为以下的GraphQL请求建立了等价的REST请求链接，允许同时通过GraphQL协议和REST协议来访问同一个后台服务函数。
```graphql
query{
  Book__get(id: 123) { name, title}
}
```

等价于
```
/r/Book__get?id=123&@selection=name,title
```

在这种视角下，**GraphQL不过是一个pull mode的REST调用而已**。在Nop平台的实现中，NopGraphQL实现了所谓的最小化信息表达，因此它没有任何对特定运行时环境的依赖，可以适配到任意的接口协议上，同时提供了GraphQL、REST、gRPC等多种调用方式。

详细介绍参见[为什么在数学的意义上GraphQL严格的优于REST?](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw)

## 四. 利用Fragment语法简化字段选择

GraphQL内置了所谓的Fragment概念，它本质上时一种可重用的字段集，可以在多个查询中被引用。我们可以给每个类型都定义一个`F_defaults`片段，它包含所有缺省返回字段（在Nop平台中对应于非lazy加载的字段）。

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

借助于Fragment定义，就可以简化服务调用的编写。
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

NopGraphQL框架中，与上面的GraphQL等价的REST调用链接是
```
/r/Book__get?id=123&@selection=...F_defaults,author
```

采用REST调用方式时，如果不指定`@selection`参数，则相当于写`@selection=...F_defaults`，它会返回所有的缺省字段（非lazy加载的字段）。对于对象属性，如果不继续指定它的子属性，则也相当于向下取一层`F_defaults`，因此上面的author属性实际对应于`author{...F_defaults}`。

`F_defaults`并不是我们唯一可以使用的字段集合。在XMeta元数据模型中，我们还可以定义其他的字段集合。

```xml
<meta>
  <selections>
    <selection id="F_moreFields">
      userId, userName, status, relatedRoleList{ roleName}
    </selection>
  </selections>
</meta>
```

另外需要注意的一点是，**标准的GraphQL引擎实现是不允许Fragment重名的**，也就是说不允许重复定义多个F_defaults，每个Fragment的名称都是全局唯一的。因此上面为Book和Author类型都定义F_defaults字段集合是不合法的。NopGraphQL中的做法可以看作是对现有GraphQL规范的一种扩展。

## 五. 精确到字段的访问权限控制

NopGraphQL引入了auth配置，在方法层面的配置类似于SpringMVC中常见的·@Permission注解·。

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

在XMeta元数据模型中，我们还可以为每个字段指定auth配置。比如配置只允许HR人员查看人员工资等。
```xml
<prop name="salary">
    <auth permissions="Employee:query" roles="admin" for="read"/>
    <auth permissions="Employee:mutation" roles="hr" for="write"/>
</prop>
```

NopGraphQL引擎在实际执行业务函数之前会调用`GraphQLActionAuthChecker`检查结果选择集中每个字段的访问权限。

此外，内置的CrudBizModel在进行查询时还会应用IDataAuthChecker接口中的getFilter等方法，为查询条件追加数据权限过滤条件。
可以在`data-auth.xml`中配置数据权限，或者通过NopAuthRoleDataAuth服务对象在线进行配置。

## 总结
* GraphQL的原始形式比较复杂，相比于REST调用有优点也有缺点，这使得它的流行存在一定的阻力。
* NopGraphQL在数学层面上统一了GraphQL和REST的内在结构，通过一系列的扩展提升了GraphQL的易用性。
* NopGraphQL引擎的实现代码远比SpringMVC要简单，它为REST服务增加了类似GraphQL的字段选择能力，实现了GraphQL和REST的等价变换。
* 在Spring、Quarkus和Solon框架中都可以引入NopGraphQL支持，在REST服务的基础上渐进式的引入GraphQL的组合调用能力。
