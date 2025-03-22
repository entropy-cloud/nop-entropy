# If We Rewrite SpringBoot, What Choices Would We Make?

SpringBoot is a significant advancement built on top of the Spring framework. It introduced the concept of dynamic auto-configuration, discarded cumbersome XML configurations, and leveraged Java's built-in annotations and ServiceLoader mechanisms to drastically reduce the number of configuration decisions required in typical business development. It also reshaped Java application development and deployment processes. However, as time has progressed, SpringBoot has become somewhat aged, with design choices from different eras accumulating unfavorable impacts, particularly when addressing performance optimization, building native applications, and other emerging challenges.

If we were to completely rebuild SpringBoot from scratch, what core problems would we define for the underlying framework to solve? What solutions would we propose for these issues? How do these proposed solutions differ fundamentally from SpringBoot's current approaches? Additionally, in the Nop platform, the dependency injection container NopIoC is implemented from scratch using reversible computation principles. It incorporates approximately 5000 lines of code to replicate all of SpringBoot's dynamic auto-configuration mechanisms and AOP interception logic, while also supporting integration with GraalVM for easy compilation into native images.

This paper will analyze these issues from the perspective of reversible computing theory under the NopIoC implementation.

## 一. SpringBoot解决的核心问题

SpringBoot has been evolving over time, so its objectives were not clearly defined all at once. Initially, its sole purpose was to handle object configuration. However, as it matured, it expanded to address a broader range of challenges.

### 1.1 POJO Configuration

As a counter to Sun's Enterprise Java Bean (EJB) initiative, Spring sought to support plain old Java objects (POJOs). It promoted the lightweight framework concept. The "lightweight" here refers not just to the simplicity of Spring's codebase but also to how business objects can be made ready for deployment without deep knowledge of Spring-specific concepts. A typical POJO requires only standard getter/setter methods, not complex Spring-specific frameworks knowledge. Business logic can be encapsulated in simple classes without relying on advanced Spring features like remoting or data access objects.

The XML configuration file, beans.xml, provides a flexible and descriptive way to define object wiring. At runtime, Spring doesn't impose restrictive control over application architecture. Users have the freedom to choose alternative configuration mechanisms or even roll their own lightweight version of Spring, as long as it meets their specific requirements.

Spring 1.0 introduced XML as a comprehensive Object-to-Object (O-O) DSL. It defined the essential primitives for object wiring:
```xml
<bean id="a" class="test.MyObjectA" init-method="init">
    <property name="b" ref="b" />
    <property name="strValue" value="xxx" />
</bean>

<bean id="b" class="test.MyObjectB">
    <property name="a" ref="a" />
</bean>
```
This configuration is equivalent to:
```java
a = new MyObjectA();
scope.put("a", a);

b = new MyObjectB();
scope.put("b", b);

a.setB(scope.get("b"));
a.setStrValue("xxx");
b.setA(scope.get("a"));

a.init();
```
The essence of object wiring is reduced to object creation, property assignment, and method invocation. This simplicity enabled Spring to become the de facto standard for POJO configuration without compromising flexibility or maintainability.

The container itself plays a non-trivial role in managing object lifecycles. It provides an external scope where temporary objects can be stored during their creation. This external scope is essential for managing object dependencies and ensuring proper cleanup when the container shuts down.

### 2. Spring 1.0's XML Configuration

Spring 1.0's XML configuration provided a robust DSL for object wiring:
```xml
<bean id="a" class="test.MyObjectA" init-method="init">
    <property name="b" ref="b" />
    <property name="strValue" value="xxx" />
</bean>

<bean id="b" class="test.MyObjectB">
    <property name="a" ref="a" />
</bean>
```
This was equivalent to:
```java
a = new MyObjectA();
scope.put("a", a);

b = new MyObjectB();
scope.put("b", b);

a.setB(scope.get("b"));
a.setStrValue("xxx");
b.setA(scope.get("a"));

a.init();
```
The container's role extended beyond just wiring objects. It provided an external scope for managing object lifecycles, enabling consistent management of object creation and destruction.

Spring 1.0's XML configuration was a significant advancement in making object wiring accessible to developers without deep Spring framework knowledge. The simplicity of the XML DSL reduced the learning curve while maintaining flexibility in application architecture.

However, as applications grew more complex, the limitations of XML-based configurations became apparent. The nesting structure of XML can become unwieldy for large-scale applications, and the lack of a true DSL for object wiring made certain configurations cumbersome.

### 1.0 Introduction to Customizable Scope Extensions

The release of **Spring 2.0** introduced customizable scope extensions. By leveraging scopes, dependency management can be extended to more dynamic environments, such as Android Activities (available only during runtime in the Activity context) and background tasks (available only during runtime in the Task context), among others.

### 1.2 @Autowired Automatic Dependency Injection

The primary value of **descriptive dependency injection** lies in its mechanism for delayed injection of information into the system. **Forward dependencies** are objects that possess complete knowledge about their associated objects (e.g., object a knows everything about object b). In contrast, **reverse dependencies** pull information from the environment into the object itself. **Dependency injection (DI)**, driven by the container, pushes information into the object at the right moment, ensuring it is fully prepared for use.

### 1.3 Flexible Wiring in Configuration

In **Spring 2.0**, configuration files contain comprehensive wiring information. While the wiring mechanism is highly flexible, it's not always necessary to rely on such extreme flexibility during compilation. At compile time, we already know a significant portion of our dependencies: their interfaces and specific types.

### 1.4 @Autowired with Spring 2.5

With **Spring 2.5**, the `@Autowired` annotation functions similarly to Java's import statement. Just as an import statement brings a class into scope, `@Autowired` automatically injects a class that meets certain criteria (e.g., type or interface). However, to achieve full object readiness, additional steps are often required, such as calling constructors, setting properties, and invoking initializers.

```java
// Declare dependencies
import test.MyObject;

// Inject dependencies using @Autowired
@Autowired
MyObject myObject;
```

Through `@Autowired`, Spring can automatically import classes that are currently active (e.g., MyObject) rather than relying on static templates for object creation. This approach allows for dynamic injection based on the current state of the application.

```java
public void myMethod(@Inject MyObject b){
    if(b.value > 3){
        @Inject MyObject a;
    }
}
```

Additionally, Spring supports injecting by type:

```java
const a = inject(MyObject);
// Or, if the programming language supports generic metadata
const a = inject<MyObject>();
```

This mechanism is particularly useful in frontend frameworks like Vue 3.0, where `provide/inject` functions serve as a natural fit for this kind of behavior.

### 1.4 AOP Interception

The combination of **dependency injection (DI)** and **AOP (Aspect-Oriented Programming)** is almost seamless. The essence of DI is to introduce external influences (via an IoC container) into the system, which inherently aligns with the principles of AOP. While objects are never truly "naked" in a well-designed system, they are wrapped within an environment that enhances their behavior through interception.

In our physical world, even fundamental particles like quarks and electrons have no mass on their own but interact with the surrounding electromagnetic field. Similarly, in software systems, objects are rarely standalone; instead, they operate within a grid (metaphorically speaking) of interdependent services.

The concept of AOP can be understood as an enhancement layer applied over the core system:
```text
Enhanced Object = Naked Object + Environment(Interceptors)
```

This means that when an object is injected with dependencies via `@Autowired`, it also inherits certain behaviors from its surrounding environment, such as logging or profiling. The interception happens at the right moment—just before the object becomes fully active.

For instance:
```java
public void myMethod(@Inject MyObject b){
    if(b.value > 3){
        @Inject MyObject a;
    }
}
```

Here, `@Inject` is used within a method to inject `MyObject a` only when certain conditions are met. This approach allows for highly contextual injections without disrupting the core functionality of the object.

Here is the translated English version of the Chinese technical document, preserving the original Markdown format, including headers, lists, and code blocks:

---

### Interesting Problem
There's an interesting issue here. If AOP (Aspect-Oriented Programming) enhancements are intended to bind specific environment information with particular objects, then there's no need for separate AOP enhancements for each object when the necessary environment information can be globally accessed. For example, in typical Java backend development, a `Controller` object generally requires the `@Transactional` annotation over all modification operations to indicate that these operations should execute within a transactional context.

However, **if we uniformly adopt the GraphQL interface protocol and define global rules such that all mutation operations are executed within a transactional environment**, then we no longer need to apply `@Transactional` to individual objects. This can reduce unnecessary method calls and improve program performance.

---

### 1.5 @ComponentScan Dynamic Collection of Bean Definitions

For any structure with sufficient complexity, it's essential to decompose the structure into multiple independently identifiable, storable, and manageable sub-components. We then need a way to synthesize these sub-components back together.

Spring 1.0 introduced an built-in import syntax that allows a complex `beans.xml` file to be split into multiple sub-files. This approach is somewhat cumbersome, as it essentially treats the import statements like copy-and-paste operations. Repeatedly importing the same file can lead to duplicate bean definitions, which may throw `BeanDefinitionOverrideException`.

---

### 1.6 @Conditional Conditional Wiring

While Spring 1.0 provides a robust configuration language, it doesn't provide a mechanism to accommodate variability within configurations. As a result, changes in business requirements force manual adjustments to the `beans.xml` file, leading to ongoing modifications and difficulty in reuse.

Starting from version 4.0, Spring introduced the `@Conditional` annotation to enable conditional wiring. This evolved into annotations like `@ConditionalOnBean`, `@ConditionalOnProperty`, etc., which carry explicit domain semantics. These annotations allow us to define clear conditional logic based on the presence or absence of certain beans or properties in the application context.

Without such conditional annotations, configurations remain rigid and static. However, using these annotations allows us to **define multiple possible wiring scenarios upfront** while providing a default configuration that can be easily adjusted if needed.

---

### 1.7 @EnableAutoConfiguration Automatic Configuration

Spring 1.0 offers a single entry point for static configuration: reading from a fixed `beans.xml` file and recursively analyzing its nested configurations. In contrast, Spring Boot provides a dynamic entry point where each dependency module automatically registers its corresponding configuration classes. This dynamic approach eliminates the need to manually manage `beans.xml` files.

The behavior can be visualized as:
```
Config = Registrar(ScanClassesA) + Registrar(ScanClassesB) + ...
```

While this simplifies default configurations, it also introduces new complexities in managing dependencies and their interactions.

---


In **Spring 1.0**, bean parsing and registration are executed in a deterministic order based on explicit XML configuration. This results in predictable outcomes and easier debugging when issues arise. However, in multi-entry configuration scenarios, dynamically merged bean configurations are combined implicitly, with their merging rules and outcomes not explicitly defined. The order of processing seemingly unrelated packages can lead to differences in execution results, even within the same IDE environment. Slight deviations from default configurations often result in configuration chaos across multiple modules during runtime.

> In everyday development, it is common to observe that new developers spend a significant amount of time debugging issues related to Spring Boot's auto-configuration not taking effect after introducing new modules or adjusting default configurations.

The best practice to resolve this issue is to avoid defining the same bean across multiple packages and to minimize complex dependency relationships between beans. Ideally, dynamic configurations should **satisfy the principle of interchangeability**, meaning that the order in which they are identified and processed does not affect the final outcome. This principle is inherently supported by descriptive programming.

---


### 1.8 Embedded Expressions and Responsive Configurations

In **Spring 1.0**, placeholder mechanisms allow for extraction of configuration parameters from XML files, such as:

```xml
<bean id="dataSource" ...>
    <property name="jdbcUrl" value="${spring.datasource.jdbc-url}" />
</bean>
```

The placeholder mechanism can be viewed as a way to glue together `application.properties` and `applicationContext.xml` configuration files. It acts as an expression that extracts values from the `application.properties` file and applies them to the current bean configuration. Conceptually, this is demonstrated by:

```java
bean.jdbcUrl = props.get('spring.datsource.jdbc-url')
```

Over time, this mechanism has been expanded in two key directions:

1. **Expression Enhancement**: Starting with **Spring 3.0**, true Expression Language (EL) support was introduced, allowing for more complex expression evaluation, such as:

```xml
<bean id="readStep">
    <property name="filePath" value="${jobParameters['filePath']}" />
    <property name="testValue" value="${T(java.lang.Math).PI}" />
</bean>
```

2. **Dynamic Configuration Variables**: Starting with **Spring 3.1**, the `Environment` concept was introduced, enabling Spring Cloud to extend simple configuration variables into a distributed, responsive configuration center.

```java
@RefreshScope
@Service
public class MyService {

    @Value("${app.user-local-cache}")
    boolean useLocalCache;
}
```

This ensures that configurations marked with `@RefreshScope` are dynamically refreshed when changes occur.

---


## II. Spring Boot's Design Flaws


### 2.1 Deviation from Descriptive Programming Principles

While Spring is built upon descriptive configuration, it gradually drifts away from the core principles of descriptive programming starting with **Spring 2.0**. In **Spring 1.0**, only basic DTD syntax was supported for constraining XML file formats. With **Spring 2.0**, more robust XML Schema support was introduced to enforce stricter formatting, while also introducing custom namespace handling through the `NamespaceHandler` interface:

```java
public interface NamespaceHandler extends BaseInterface {
    // Methods for handling custom namespaces
}
```

---

The interface is responsible for parsing and processing, which on the surface seems to provide descriptive tags for custom XML tags. However, the actual implementation consists of ordered command-line code.

1. When implementing custom tags, we cannot simply reuse existing functionality through declarative means. Adding new custom tags requires implementing a new `NamespaceHandler` and adding related configurations, which is costly and difficult to ensure proper nesting of tags across different namespaces.

2. While Spring 1.0 provides comprehensive object graph assembly, the custom tags in Spring 2.0 cannot be mapped back to Spring 1.0 tag definitions. This means that if a package implements Spring 2.0 syntax support, we cannot guarantee that it can be configured using Spring 1.0 syntax for that package. This leads to an increasing number of custom configuration tags and an unclear semantic structure, making it difficult to develop a general-purpose Spring configuration analysis tool.

3.  
SpringBoot's design and development lacks a clearly defined semantic model. The numerous annotations are complex, intertwined with global state, and lack collaborative rules for command-line identification and handling. In practical applications, the bean assembly result is closely related to command-line code execution order. This relationship becomes particularly apparent when migrating SpringBoot configurations to other IoC containers.

For example, [Quarkus](https://quarkus.io/) is a custom-built open-source Kubernetes-native Java framework designed specifically for GraalVM and OpenJDK HotSpot. Like Go, it uses Ahead Of Time (AOT) compilation to compile directly into a single executable file, freeing the application from the JDK runtime environment during execution, thereby improving startup speed and reducing memory consumption. As a new framework, Quarkus is envious of Spring's extensive community resources, so it aims to provide a migration mechanism from Spring to Quarkus. However, Quarkus faces challenges:

- The first issue with Quarkus is that Spring's various annotations have not been separated into a single dedicated annotation API package. Instead, they are mixed together with implementation code.
- The second challenge is that Spring's configuration process cannot be fully analyzed at compile time to enable runtime handling, unlike Quarkus' built-in CDI container, which uses compile-time code generation for object assembly.

Quarkus chooses to support only a subset of Spring's annotations, implementing command-line loading and scanning through command-line code. This makes its Spring compatibility feature effectively useless, serving only as a marketing tool rather than an actual migration aid.

From a non-technical perspective, SpringBoot's non-declarative design may be intentional. After transitioning to a commercial product, the Spring framework's team likely aims to increase community sunk costs, creating barriers to migration by constructing a wall of FEAR (Fear of Attempting Relocation). The idea is that compatibility becomes easier when you stay within the Spring ecosystem, while migrating to another container incurs significant costs unless you fully move the Spring container itself.


### Historical Success and Compatibility Burden
Spring's history spans over two decades since its initial release in March 2004. Over the years, it has witnessed various programming paradigms and technologies evolve. Through each era, Spring has provided corresponding encapsulation support, which now appears as an unnecessary layer of complexity.

For example, consider SpringMVC. When field names in the request do not match those in the backend, the backend may not throw a clear JSON parsing error but instead encounter other unexplained issues. This is because SpringMVC supports multiple parameter transmission mechanisms. If one fails, it tries another. However, before JSON became widely adopted, developers resorted to creative workarounds like `?a[]=1&a[]=2` for arrays and `foo[bar]=baz` for maps.

Using modern tools like [qs](https://www.npmjs.com/package/qs), such encoding is no longer necessary. In fact, most of the code in SpringMVC is unnecessary, as approximately 90% or more of it can be removed through proper optimization.

If we adopt current best practices, SpringMVC's complex object graph management becomes redundant. For instance, using GraphQL instead of multiple REST endpoints simplifies data fetching significantly. With GraphQL, you define a single endpoint like `/graphql`, send a POST request with a JSON body containing your query, and receive a structured JSON response. This approach eliminates the need for complex URL pattern matching and reduces the amount of code needed to handle object graph assembly.

In summary, while SpringMVC's historical success demonstrates its value in simplifying certain aspects of application development, its design has also introduced complexities that make it difficult to maintain and extend over time. The increasing number of custom tags and the lack of a unified semantic model are significant challenges that have driven the development of alternative frameworks like Quarkus.

### 2.3 Limitations of the "Not Inventing the Wheel" Positioning

Spring has always positioned itself as a framework that doesn't "reinvent the wheel." It claims to avoid duplicating the efforts of other mature and excellent frameworks by encapsulating their functionality according to its own design style. This positioning leads to Spring often finding itself in an uncomfortable position when dealing with many common issues. It either has to hide the underlying implementation behind its own interfaces or needs to expose and manage the details of the underlying implementations, which are often inconsistent with Spring Boot's configuration styles. This creates significant challenges for upper layers that rely on Spring's encapsulation.

For example, looking at Spring's classic transaction encapsulation: To unify JDBC operations and Hibernate operations, Spring internally defines various thread-local objects like SessionHolder and ConnectionHolder, using TransactionSynchronization to synchronize them. However, if Hibernate could seamlessly integrate with Spring, it would be possible to directly use JdbcTemplate for database access without additional encapsulation. In fact, starting from Hibernate 5.3 onwards, Hibernate explicitly introduced the [BeanContainer](https://docs.jboss.org/hibernate/stable/core/javadocs/org/hibernate/resource/beans/container/spi/BeanContainer.html) interface, signaling that it assumes the presence of an IoC container. This makes some traditional Spring encapsulation patterns obsolete.

Sometimes, the technology to be encapsulated itself becomes overly complex compared to what is needed. For example, AspectJ's AOP mechanism is inherently complex. It provides a powerful aspect interception model that can match method names and package structures using regular expressions but also requires managing complex interdependencies between aspects.

### 3.1 XDef Model Definition

A custom IoC container like NopIoC has its own domain model. This model can be seen as a Domain-Specific Language (DSL). The container itself acts as both the interpreter and executor of this DSL. If you serialize domain objects into text, you get an IoC-specific configuration file, similar to Spring's beans.xml or Hibernate's hbm.xml. Java annotations can also represent the domain model in another form, such as using JPA annotations for Hibernate models or hbm files.

A well-defined model can be described by a general meta-model (Meta-Model). For example, Spring 1.0's XML syntax uses XML
# Schema Definition vs. Spring Boot Configuration

In the context of Spring Boot applications, **Schema Definition** (XSD) is often used to define domain models and constraints for XML data. However, Spring Boot lacks a built-in XML syntax for configuration, which can lead to complex and less intuitive configurations.

While XSD provides powerful validation capabilities, it is verbose and not designed for dynamic or executable Domain Specific Languages (DSLs). This limitation makes it difficult to create reusable and modular configurations directly within XML files.

# Comparison of Schema Definition and XDefinition

The following section compares **XSD** and **XDefinition**:

- **XSD**: XML Schema Definition is a powerful but verbose tool for defining data constraints. It is primarily designed for validating general-purpose XML data.
- **XDefinition**: XDefinition is a lightweight and intuitive meta-language specifically designed to define domain models. It provides an executable DSL, making it easier to create clear and maintainable configurations.

# Example: Spring 1.0 Configuration Syntax

Here’s an example of how Spring 1.0 configuration can be extended using NopIoC:

```xml
<beans>
    <bean id="xx.yy">
        <ioc:condition>
            <if-property name="xxx.enabled" />
            <on-missing-bean-type>java.sql.DataSource</on-missing-bean-type>
            <on-class>test.MyObject</on-class>
        </ioc:condition>
    </bean>
</beans>
```

This configuration corresponds to the following Java annotation-based configuration in Spring Boot:

```java
@ConditionalOnProperty("xxx.enabled")
@ConditionalOnMissingBean({DataSource.class})
@ConditionalOnClass({MyObject.class})
@Bean("xx.yy")
public XXX getXx() {
}
```

# Example: AOP Configuration

Here’s an example of how to configure an interceptor using NopIoC:

```xml
<bean id="nopTransactionalMethodInterceptor"
      class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
    <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
                   order="1000"/>
</bean>
```

This configuration indicates that any method annotated with `@Transactional` will be intercepted by the `TransactionalMethodInterceptor`.

The implementation logic is as follows:

1. **Registering AOP-Scanning Classes**:
   - Place classes to be scanned by AOP in the `resources/_vfs/nop/aop/{module_name}.annotations` directory.

2. **Maven Plugin for Compilation**:
   - During compilation, a Maven plugin scans the `target/classes` directory for classes annotated with AOP-recognizable annotations.
   - If an annotation is detected on a method, an AOP class (`_aop`) is generated and included in the JAR.

3. **Dynamic Bytecode Generation**:
   - At runtime, the generated AOP classes are dynamically loaded into the application container.
   - This eliminates the need for dynamic bytecode generation during compilation, similar to AspectJ's approach but simplified.

# Summary

The key difference between XSD and XDefinition lies in their intended use cases. While XSD is excellent for static data validation, XDefinition provides a more flexible and intuitive way to define domain models and configurations within Spring Boot applications.


Below is the translation of a Chinese technical document into English, maintaining the original Markdown format, including headers, lists, and code blocks.

---


### 3.1 Based on Reversible Calculation Principle for Layered Abstraction

The NopIoC framework implements layered abstraction based on the reversible calculation principle. During compilation, it generates code similar to Spring 2.0's custom tags using defined compilation techniques.

```xml
<beans>
  <x:gen-extends>
    <my:MyTask xpl:lib="my.xlib">
      <reader bean="myReader" />
      <writer bean="myWriter" />
    </my:MyTask>
  </x:gen-extends>
</beans>
```

In the Nop platform, all DSL models support the `x:gen-extends` mechanism. This mechanism generates XML nodes during compilation and then applies a DeltaMerge algorithm to merge these nodes with external XML nodes, resulting in final XML configuration nodes. This process is equivalent to using the XPL template language to write custom tags for Spring 2.0, which are then executed at compile time. The NopIoC engine only needs to support basic Spring 1.0 syntax to provide custom tag abstraction.

The layered abstraction concept is consistently implemented in the Nop platform, allowing as many operations as possible to be moved to the compilation phase, reducing runtime complexity and improving performance. For example, after executing all conditional checks and type scans, NopIoC outputs a version of the configuration that excludes all optional conditions. This output is placed in the `_dump` directory, which can then be processed by Spring 1.0's execution engine. On this basis, a translator can convert Spring 1.0 syntax into annotations, making it compatible with other IOC runtimes or translating it directly into Java code to eliminate the need for runtime IoC.

---


### 3.2 Generating Java Proxy

The NopIoC framework includes an `ioc:proxy` attribute that allows direct creation of a proxy object based on the current bean and its specified interfaces.

```xml
<bean id="myBean" class="xx.MyInvocationHandler"
      ioc:type="xx.MyInterface"
      ioc:proxy="true" />
```

Using this configuration, the `myBean` object is replaced by a proxy that implements the `MyInterface` interface.

---


### 3.3 By Annotation or Type Scan

NopIoC supports collection of beans based on annotations. For example:

```xml
<bean id="nopBizObjectManager" class="io.nop.biz.impl.BizObjectManager">
  <property name="bizModelBeans">
    <ioc:collect-beans
       by-annotation="io.nop.api.core.annotations.biz.BizModel"
       only-concrete-classes="true"/>
  </property>
</bean>
```

This configuration collects all classes annotated with `@BizModel`, ignoring abstract classes and interfaces, and focusing solely on concrete implementations.

---


### 3.4 Prefix-Based Syntax

In Spring 1.0, to access certain properties or behaviors provided by the IOC container, specific interfaces (e.g., `BeanNameAware` or `ApplicationContextAware`) must be implemented by the bean. NopIoC supports a prefix-based syntax to achieve this without manual implementation.

---

Here is the translation of the Chinese technical document into English, maintaining the original Markdown format including headers, lists, and code blocks:

---

# Prefix Guided Syntax

The prefix syntax is widely used in the Nop platform as a highly flexible and extensible design. For detailed explanations, please refer to my article on [DSL分层语法设计与前缀引导语法](https://zhuanlan.zhihu.com/p/548314138).

---

## 3.8 Responsive Configuration Updates

The NopIoC framework includes built-in knowledge of responsive configuration updates. We can bind individual properties to responsive configuration bindings.

```xml
<bean id="xx">
  <!-- @cfg indicates that a value is obtained when the bean is first created, but it does not perform responsive updates -->
  <property name="configValue" value="@cfg:config.my-value" />
  <!-- @r-cfg indicates that when the configuration value changes, the bean's property will automatically update -->
  <property name="dynValue" value="@r-cfg:config.my-dyn-value" />
</bean>
```

Additionally, it is possible to use an annotation similar to Spring's `@ConfigurationProperties` to bind all properties of an object to configuration values using the prefix syntax.

```xml
<!-- ioc:auto-refresh indicates that when the configuration changes, the bean's properties will automatically update -->
<bean id="xx" ioc:config-prefix="app.my-config" ioc:auto-refresh="true"
    class="xxx.MyConfiguration" />
```

The NopIoC framework also defines a special syntax node `ioc:config`.

```xml
<ioc:config id="xxConfig" ioc:config-prefix="app.my-config" ... />

<bean id="xx" ioc:on-config-refresh="refreshConfig">
  <property name="config" ref="xxConfig" />
</bean>
```

When the configuration updates, `xxConfig` will automatically update, and this propagation will trigger `refreshConfig` in all beans that have referenced `xxConfig`.

---

## 3.9 Automatic Configuration Discovery

The NopIoC framework provides an auto-configuration mechanism similar to Spring Boot's AutoConfiguration. During initialization, NopIoC automatically searches for files in the virtual file system under the `/nop/autoconfig` directory with filenames ending in `.beans`, and it loads any `beans.xml` files found there. For example:

```xml
/nop/autoconfig/nop-auth-core.beans
```

This typically resolves to:
```xml
/nop/auth/beans/auth-core-defaults.beans.xml
```

In general, the filename of a beans file corresponds to the name of the Java module it represents. This avoids conflicts when multiple modules are packaged into a single Fat-JAR.

---

The key difference from Spring Boot is that NopIoC does not load configuration files or register beans on the fly while loading. Instead, it waits until all bean definitions have been collected before performing any conditional logic. Thus, the order of bean definition in NopIoC generally does not affect the container's dynamic calculations.

---

## 3.10 Unit Test Support

The NopIoC framework has been integrated with JUnit 5. In unit tests, we primarily use the `@NopTestConfig` annotation to control the initialization of the IoC container.
```java
public @interface NopTestConfig {
    /**
     * Whether to enforce setting nop.datasource.jdbc-url to an H2 in-memory database
     */
    boolean localDb() default false;

    /**
     * Whether to use a random port for the service
     */
    boolean randomPort() default false;

    /**
     * Whether to use lazy loading mode for unit tests
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    /**
     * Whether to automatically load configurations from/nop/auto-config/ directories
     */
    boolean enableAutoConfig() default true;

    String autoConfigPattern() default "";

    String autoConfigSkipPattern() default "";

    /**
     * Configuration file for unit test beans
     */
    String testBeansFile() default "";

    /**
     * Configuration file for unit test configurations
     */
    String testConfigFile() default "";
}


@NopTestConfig
public class MyTestCase extends JunitBaseTestCase {
    // Can be injected into the Bean container using @Inject
    @Inject
    IGraphQLEngine engine;
}
```

NopIoC's JUnit support provides the following features:

1. Whether to use an in-memory database for testing instead of the database connection specified in the configuration files  
2. Whether to enable auto-config, controlling which modules are enabled for auto-config  
3. Whether to import test-specific beans configurations  
4. Whether to import test-specific properties configurations  
5. Support for injecting beans into test cases using @Inject  

For detailed information about Nop platform's automation support, please refer to my previous articles.

[Automation in Low-Code Platforms](https://zhuanlan.zhihu.com/p/569315603)

## Summary

The Nop platform is built from scratch based on reversible computation principles. NopIoC is a modular component of the Nop platform. All other modules in the Nop platform do not directly depend on NopIoC. In principle, any implementation that satisfies the IBeanContainer interface can replace NopIoC's implementation. However, compared to frameworks like Spring/Quarkus, NopIoC has some unique design aspects, particularly based on reversible computation principles for layered abstraction, allowing it to provide a rich and complex feature set while maintaining simplicity.

For detailed information about reversible computing theory, please refer to my previous articles.

[Reversible Computing: The Next Generation of Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)

[Technical Implementation of Reversible Computing](https://zhuanlan.zhihu.com/p/163852896)

[Exploring Nop Platform's Design from Tensor's Perspective](https://zhuanlan.zhihu.com/p/531474176)

