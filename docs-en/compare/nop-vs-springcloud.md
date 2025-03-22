# null
Spring Cloud Functionality Comparison

The Nop platform is a new generation of low-code platforms designed from scratch based on reversible computation principles. Its goal is not to provide pre-built development frameworks and visualization design tools for specific scenarios, but to break down the barriers between descriptive programming and traditional imperative programming, establishing a new programming paradigm that seamlessly integrates both. This paradigm aims to continuously expand the semantic space covered by descriptive programming.

To achieve this goal with minimal technical cost, the Nop platform did not adopt the mainstream open-source frameworks used in the industry, instead choosing to rebuild its entire technology stack based on reversible computation principles. This document will briefly outline the components of the Nop platform and compare them with those found in the Spring Cloud ecosystem.


## Components Comparison

| Component | Nop Platform | Spring Cloud |
| --- | --- | --- |
| Web Framework | Nop GraphQL | Spring MVC |
| Expression Engine | XLang XScript | Spring EL |
| Template Engine | XLang Xpl | Velocity/Freemarker |
| ORM Engine | Nop ORM | JPA/Mybatis |
| IoC Container | Nop IoC | Spring IoC |
| Dynamic Configuration | Nop Config | Spring Config |
| Distributed Transactions | Nop TCC | Alibaba Seata |
| Automated Testing | Nop AutoTest | Spring Boot Test |
| Distributed RPC | Nop RPC | Feign RPC |
| Report Engine | Nop Report | JasperReport |
| Rule Engine | Nop Rule | Drools |
| Batch Processing Engine | Nop Batch | Spring Batch |
| Workflow Engine | Nop Workflow | Flowable/BPM |
| Job Scheduling | Nop Job | Quartz |
| XML/JSON Parsing | Nop Core | Jaxb/Jackson |
| Resource Abstraction | Nop Resource | Spring Resource |
| Code Generation | NopCodeGen | Various code generators |
| IDE Plugins | Nop IdeaPlugin | Mybatis/Spring plugins |

The Nop platform can directly serve as a foundation for building applications similar to those in the Spring Cloud ecosystem, significantly simplifying and improving the development process while enhancing scalability.


### 1. IoC Container

Descriptive IoC containers are essential skills for any developer who has worked with frameworks like Spring since its inception. However, as of Spring 2.0, the descriptive nature of Spring IoC began to diminish, leading to a mix of imperative and declarative logic in its operation. This shift is exemplified by changes in how beans are scanned and managed.

The Nop IoC container builds upon the principles established by Spring 1.0's IoC syntax, incorporating similar conditional configuration logic found in Spring Boot. It offers a more refined and expressive way of managing dependencies between components, allowing for a clearer separation of concerns and easier maintenance of complex applications.



```java
@ConditionOnMissingBean
@ConditionOnProperty("test.my-bean.enabled")
@Component
public class MyBean {
    @Inject
    private OtherBean other;
}
```

Corresponding configuration in Nop IoC:

```xml
<bean id="myBean" ioc:default="true" class="test.MyBean">
  <ioc:condition>
     <if-property name="test.my-bean.enabled"/>
  </ioc:condition>
</bean>
```

The Nop IoC container supports various annotations like `@Inject`, `@PostConstruct`, and `@PreDestroy`, mirroring Spring's annotation-based configuration. Unlike Spring, it does not rely on package scanning but instead requires explicit bean declarations in a configuration file.

Because the Nop platform heavily leverages model-driven development to dynamically generate code, there is a significant amount of boilerplate code that needs to be written for each application. This can be mitigated by writing plugins or extending the existing IoC container to automate these tasks further.



To extend the functionality of the Nop IoC container and better align it with specific project requirements, developers can write custom plugins or extend its configuration logic. This can include adding new annotations for dependency injection, implementing custom scanning mechanisms, or integrating third-party libraries that offer additional functionality.

The following code snippet demonstrates how to create a simple plugin for the Nop IoC container:

```java
public class CustomIoCPlugin implements Plugin {
    @Override
    public void configure(IoCContainer ioc) {
        // Add new dependencies or modify existing ones
    }
}
```

Registering this plugin within the application's configuration will enable its features and extend the capabilities of the Nop IoC container.



The comparison between the components of the Nop platform and those found in Spring Cloud highlights the innovative approach taken by the Nop platform to address the limitations of traditional frameworks. By integrating reversible computation principles, it offers a more flexible and expressive way to manage dependencies and configure applications. The ability to extend its IoC container through plugins or custom configuration logic further enhances its adaptability.

The use of model-driven development for code generation simplifies the development process but requires careful planning and management to avoid unnecessary complexity. Overall, the Nop platform presents a compelling alternative for building scalable and maintainable applications, especially in scenarios where traditional frameworks fall short.

Configuration

```xml
<beans x:extends="super">
    <bean id="dataSource" x:override="remove" />
</beans>
```


### Dynamic Configuration

NopIoC's design includes dynamic configuration. In the `beans.xml` file, you can use a special prefix to indicate dynamic configurations.

```xml
<bean id="xx">
   <property name="poolSize" value="@r-cfg:my.pool-size|5" />
</bean>
```

The `@r-cfg:` prefix indicates that this configuration is dynamic and will be updated automatically when the underlying configuration changes.


### IoC Container Configuration Prefix

```xml
<ioc:config id="nopOrmGlobalCacheConfig" class="io.nop.commons.cache.CacheConfig"
    ioc:config-prefix="nop.orm.global-cache"  ioc:default="true"/>
```

The `ioc:config-prefix` attribute is similar to Spring's `@ConfigurationProperties` annotation, used to specify the configuration prefix.


## Interaction with Spring IoC

NopIoC can work together with Spring IoC. In general, NopIoC will be initialized after Spring IoC has been initialized. You can use `BeanContainer` to get beans managed by NopIoC or Spring IoC.

```java
BeanContainer.intance().getBean(beanName)
BeanContainer.intance().getBeanByType(beanType)
```

NopIoC's design goal is to provide a better alternative to Spring IoC, with strict declarative programming and easy integration into the Spring ecosystem.



In the context of Spring Cloud, we typically use Controller to call Service to complete specific business logic. In the Controller, you will handle some object structure transformation or composition work. To implement GraphQL service interface, we need to use graphql-java package to rewrite the interface code.

NopGraphQL engine greatly simplifies service interface design. Generally, we can directly expose domain model objects as external services without going through Controllers. For simple Web services, we only need to specify the URL pattern and parameter passing method (using `param` or `path`).

```java
@RestController
public class MyController{
    @PostMapping(value = "/echo/{id}")
    public String echo(@QueryParam("msg") String msg,
                 @PathParam("id") String id, HttpServletRequest request) {
        return "Hello Nacos Discovery " + msg + ",id=" + id;
    }
}
```

In the Nop platform, we only need to add `@BizModel` annotation on the domain model object and mark the service method with the corresponding configuration prefix.

roduction


### Overview

NopGraphQL is a powerful and flexible GraphQL engine that provides a unified interface for interacting with multiple data sources. It supports both RESTful and GraphQL protocols, allowing developers to choose the best approach for their specific use case.


### Key Features

1. **Unified Interface**: NopGraphQL provides a single interface for querying and manipulating data across multiple data sources.
2. **Flexible Protocols**: Supports both RESTful and GraphQL protocols, allowing developers to choose the best approach for their use case.
3. **Automatic Data Binding**: Automatically binds data from multiple sources into a single, unified schema.
4. **Transaction Management**: Provides automatic transaction management for concurrent operations.
5. **Service Mesh Integration**: Seamlessly integrates with service mesh technologies like Istio and Linkerd.



1. **Simplified Development**: Reduces complexity by providing a unified interface for interacting with multiple data sources.
2. **Improved Performance**: Optimizes performance by leveraging the strengths of both RESTful and GraphQL protocols.
3. **Enhanced Security**: Provides automatic transaction management and service mesh integration for enhanced security.



1. **Hybrid Architecture**: Combine RESTful APIs with GraphQL endpoints to create a hybrid architecture that leverages the strengths of both approaches.
2. **Microservices**: Integrate NopGraphQL into microservices-based architectures to simplify data exchange between services.
3. **Real-time Data Processing**: Utilize NopGraphQL's transaction management and service mesh integration features for real-time data processing and analytics.





The NopGraphQL engine is the core component of the NopGraphQL framework, responsible for parsing GraphQL queries, resolving data sources, and executing transactions.



1. **Query Parser**: Parses incoming GraphQL queries into an abstract syntax tree (AST) for efficient execution.
2. **Data Source Resolver**: Resolves data sources based on the parsed query AST, enabling automatic binding of data from multiple sources.
3. **Transaction Manager**: Manages concurrent operations using automatic transaction management.



1. **Scalability**: Optimized for high-performance and scalability in distributed systems.
2. **Flexibility**: Supports multiple data sources and protocols, making it adaptable to various use cases.





The NopGraphQL client is a lightweight library that enables developers to interact with the NopGraphQL engine from their applications.



1. **Automatic Query Generation**: Automatically generates GraphQL queries based on the client's requirements.
2. **Data Binding**: Binds data from multiple sources into a single, unified schema.
3. **Transaction Management**: Integrates seamlessly with transaction management features of the NopGraphQL engine.



1. **Simplified Development**: Reduces complexity by providing an easy-to-use API for interacting with the NopGraphQL engine.
2. **Improved Performance**: Optimizes performance by leveraging the strengths of both RESTful and GraphQL protocols.



NopGraphQL is a powerful and flexible GraphQL engine that provides a unified interface for interacting with multiple data sources. Its key features, including automatic data binding, transaction management, and service mesh integration, make it an ideal choice for developers seeking to simplify their development workflow while improving performance and security.

orm's GraphQL engine](https://zhuanlan.zhihu.com/p/589565334)

2. [Low-code platform's distributed RPC framework (approx. 3000 lines of code)](https://zhuanlan.zhihu.com/p/631686718)

## Three. Storage Layer

NopORM engine includes most of the features from Spring Data, JPA and MyBatis, while supplementing a large number of business development common functions, such as field encryption, logical deletion, modification history tracking, extended fields, multi-tenancy, etc.

In terms of interface layer, NopORM's usage pattern is very similar to that of Spring, except it always uses XML model files instead of JPA annotations.

```java
IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);

MyEntity example = dao.newEntity();
example.setMyField("a");

// Find the first one that meets the conditions
MyEntity entity = dao.findFirstByExample(example);

QueryBean query = new QueryBean();
query.setFilter(and(eq(MyEntity.PROP_NAME_myField, "a"), gt(MyEntity.PROP_NAME_myStatus, 3)));
query.setLimit(5);

List<MyEntity> list = dao.findPageByQuery(query);
```

Generally speaking, the IEntityDao provided by NopORM is already sufficient and rich in methods, allowing for very complex queries. Therefore, it's not necessary to create a separate Dao interface for each entity.

For complex queries, you can directly use a mechanism similar to MyBatis' sql-lib, using XML models to manage dynamic SQL.

```java
@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {
    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

In the Mapper interface, you can add a @SqlLibMapper annotation to define the sql-lib model and its mapping relationship with the Java interface.

```xml
<eql name="syncCartProduct" sqlMethod="execute">
    <arg name="product"/>

    <source>
        update LitemallCart o
        set o.price = ${product.price},
          o.goodsName = ${product.goods.name},
          o.picUrl = ${product.url},
          o.goodsSn = ${product.goods.goodsSn}
        where o.productId = ${product.id}
    </source>
</eql>
```

### Excel Model Driver

Nop platform provides a very powerful model driver development pattern, which can parse Excel data models to automatically generate entity definitions, Mapper interfaces, and metadata. It even supports backend GraphQL services and frontend page generation.

![](../tutorial/excel-model.png)

For more detailed design and implementation, please refer to the following articles:

1. [Low-code platform needs what kind of ORM engine? (2)](https://zhuanlan.zhihu.com/p/545063021)
2. [Data-driven code generators](https://zhuanlan.zhihu.com/p/540022264)
3. [Nop Platform: An Open-source Low-code Platform](https://zhuanlan.zhihu.com/p/612433693)
4. [Low-code platform how to add extended fields for entities in non-table scenarios](https://zhuanlan.zhihu.com/p/618851796)

erview
### Introduction
Nop platform provides a specialized language, XLang, which includes multiple sub-languages such as XScript, Xpl, XTransform, and XDef. These languages are designed for Domain-Specific Language (DSL) development.

### Languages Supported by Nop Platform

#### 1. XScript Language
XScript is similar to JavaScript in syntax and supports similar types and expressions.

```java
// Example usage of XScript language
XNode node = XNodeParser.instance().parseFromText(loc, text);
node.getTagName(); // Get the tag name
node.getAttr(name); // Get an attribute value
node.setAttr(name, value); // Set an attribute value
```

#### 2. Xpl Template Language
Xpl is similar to FreeMarker in syntax and supports custom tags and macro expansion.

```java
// Example usage of Xpl template language
XNode node = XNodeParser.instance().parseFromText(loc, text);
node.attrText(name); // Get the text value of an attribute
node.attrInt(name); // Get the integer value of an attribute
```

#### 3. XDef Meta Language
XDef is similar to XML Schema in syntax and supports meta-modeling.

```java
// Example usage of XDef meta language
XNode node = XNodeParser.instance().parseFromText(loc, text);
node.getTagName(); // Get the tag name
node.getChildren(); // Get a list of child nodes
```

#### 4. XTransform Transformation Language
XTransform is similar to XSLT in syntax and supports tree transformations.

```java
// Example usage of XTransform transformation language
XNode node = XNodeParser.instance().parseFromText(loc, text);
node.transform(); // Apply a transformation to the node
```

### Key Features of Nop Platform

*   Supports multiple sub-languages for DSL development.
*   Provides a specialized XML parser and JSON parser.
*   Offers a simple and intuitive API for working with nodes and attributes.

### Comparison with Other Technologies

*   Compared to Spring, Nop platform provides a more consistent and robust set of features for DSL development.
*   Unlike JAXB, Nop platform does not rely on external libraries or standards.

del Definition Language: XDef

## Overview

XDef is a unified meta model definition language for defining and describing data models, business rules, and workflow processes.


## Key Features

1. [Replace XSD with XDef](https://zhuanlan.zhihu.com/p/652191061)
2. [From Inverse Computation to DSL Design Points](https://zhuanlan.zhihu.com/p/646144092)


## Import/Export
NopReport is a report engine based on reversible computation theory, starting from scratch and independently implemented. Its core code is concise, with only 3000+ lines of code (see [nop-report-core](https://link.zhihu.com/?target=https%3A//gitee.com/canonical-entropy/nop-entropy/tree/master/nop-report/nop-report-core)).

NopReport has high performance, with performance testing codes available (see [TestReportSpeed.java](https://link.zhihu.com/?target=https%3A//gitee.com/canonical-entropy/nop-entropy/blob/master/nop-report/nop-report-demo/src/test/java/io/nop/report/demo/TestReportSpeed.java)).

NopReport is located in the Nop platform ([https://link.zhihu.com/?target=https%3A//gitee.com/canonical-entropy/nop-entropy](https://link.zhihu.com/?target=https%3A//gitee.com/canonical-entropy/nop-entropy)) and is a general modeling tool for table-form data structures. All functions that need to generate table-form data can be converted into NopReport.

For example, NopCli is a command-line tool provided by Nop that can reverse-engineer database tables and generate Excel model files. This Excel model file is generated through **importing templates** and converting them into report models.

![](../user-guide/report/cross-table-report-result.png)

![](../user-guide/report/cross-table-report.png)



Compared to other report engines, NopReport has the following distinct characteristics:

1. Using Excel as a template designer
2. Directly using domain models as data objects, and DataSet only as an optional data object (general report engines can only use table data)
3. Extending expression syntax based on general expressions, rather than specialized report expressions
4. Supporting multiple sheets and cyclic generation

Config(localDb = true, initDatabaseSchema = true)
public class TestGraphQLTransaction extends JunitAutoTestCase {
    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testRollback() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("mutation { DemoAuth__testFlushError }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertTrue(response.hasError());
        assertTrue(daoProvider.daoFor(NopAuthRole.class).getEntityById("test123") == null);
    }
}
```

The Nop Test Configuration annotation allows for simple control over automated testing.

Nop AutoTest is a unique aspect of the framework, providing model-driven automation capabilities through recording and playback mechanisms. This enables complex business logic to be automatically tested without manual code writing.

For more information on how this works, see [Low-Code Platform Automated Testing](https://zhuanlan.zhihu.com/p/569315603).

## External Tools

Nop provides integration with Maven for a code generator that can be used independently of the Nop platform. This allows for incremental generation of specified code.

For more information on how to use this feature, see [Data-Driven Code Generation](https://zhuanlan.zhihu.com/p/540022264).

## IDEA Plugin

Nop IdeaPlugin provides a general-purpose IDEA plugin that automatically recognizes XML root nodes and schema attributes. This enables property hints, link navigation, and format validation.

For more information on how to use this feature, see [IDEA Plugin Documentation](../user-guide/idea/plugin-documentation.md).

## Conclusion

Nop's implementation is simpler than Spring Cloud, with a smaller codebase. However, Nop provides more features and capabilities, including:

1.  Simplified implementation principles
2.  Support for differential customization
3.  Automatic generation of models and templates
4.  Unified use of XDSL to implement DSLs
5.  Integration with DevOps tools

Nop can be used as a drop-in replacement for Spring Cloud, providing a more streamlined development experience.

