
# If We Rewrote SpringBoot from Scratch, What Different Choices Would We Make?

SpringBoot is a major leap forward built on the Spring framework. It introduced the concept of dynamic auto-configuration, abandoned cumbersome XML configuration, and fully leveraged Java’s built-in annotations and the ServiceLoader mechanism, greatly reducing the number of configuration decisions required in typical business development, thereby reshaping how Java applications are developed and deployed. However, as time has gone on, SpringBoot has grown old. The accumulated adverse effects of design decisions made at different historical points have made it difficult for SpringBoot to tackle new challenges such as performance optimization and building native applications.

If we were to rewrite SpringBoot entirely from scratch, which core problems would we explicitly assign to the underlying framework to solve? What solutions would we propose for those problems? How do these solutions fundamentally differ from SpringBoot’s current approach? Nop platform’s dependency injection container, NopIoC, is a model-driven DI container implemented from scratch based on the principles of Reversible Computation. With roughly 5,000 lines of code, it implements all the dynamic auto-configuration and AOP interception mechanisms we rely on in SpringBoot, and it integrates with GraalVM, making it easy to compile into native images. In this article, I will discuss some analyses of IoC container design principles from the perspective of Reversible Computation theory, using NopIoC’s implementation code as a reference.

## I. The Core Problems SpringBoot Solves

SpringBoot evolved over time, so the problems it set out to solve were not clearly defined in one shot. Early on, its goal was quite simple: declarative object assembly.

### 1.1 Declarative Assembly of POJOs

As the rebel against EJB (Enterprise Java Bean, Sun’s enterprise object standard), Spring’s original intent was to serve the masses of POJOs (Plain Old Java Objects), emphasizing the notion of so-called lightweight frameworks. Here, lightweight not only means that Spring’s code implementation is relatively straightforward, but more importantly that business objects can travel light. In our business objects, we only need to write ordinary get/set property methods,**
no advanced, proprietary Spring framework knowledge (so-called non-intrusiveness) is required to build our business**
. At runtime, we only need to supply a self-evident, declarative beans.xml assembly file to achieve flexible object assembly. In principle, runtime is not constrained by the Spring framework; we can choose other assembly technologies to wire objects, and we could even write an optimized version of Spring ourselves that reads the beans.xml configuration file and executes the related assembly logic.

The XML assembly format defined in Spring 1.0 is a complete DSL for object assembly. It **defines the most basic set of primitives needed for object assembly; any complex object assembly process can be described using this DSL**. For example, the following example describes the assembly logic of two interdependent beans:

```xml
<bean id="a" class="test.MyObjectA" init-method="init" >
   <property name="b" ref="b" />
   <property name="strValue" value="xxx" />
</bean>

<bean id="b" classs="test.MyObjectB">
   <property name="a" ref="a" />
</bean>
```

They are equivalent to the following Java code:

```java
a = new MyObjectA();
scope.put("a",a);
b = new MyObjectB();
scope.put("b",b);

a.setB(scope.get("a"));
a.setStrValue("xxx");
b.setA(scope.get("a"));

a.init();
// ... use the object
a.destroy(); // Upon container shutdown, it is responsible for destroying all created objects
```

Break down any object assembly process into its smallest atomic actions, and it’s nothing more than creating objects, setting properties, and calling initialization methods. Therefore, the object assembly process previously written in Java code can be described using beans.xml.

Here we note that the Bean container itself plays a **non-trivial coordination role** during bean creation. Since a and b depend on each other, we need to set a and b’s properties first and then invoke their initialization methods. During this process, there must be an external scope environment to temporarily hold the objects created and provide a mechanism to obtain temporary references.

Spring 1.0 only distinguished whether a bean was a singleton (if not singleton, then prototype), but people soon realized that scope is a concept that needs to be explicitly identified. Spring 2.0 introduced an extension point for custom scopes. With scope, we can extend bean dependency management to more dynamic environments, such as Android Activity (beans exist only during the activity’s lifetime), backend batch jobs (beans exist only during the step’s execution), and so on.

### 1.2 @Autowired Automatic Dependency Injection

The most important value of declarative dependency injection is that it provides a mechanism for **delaying the injection of information into the system**. Forward dependency means object a possesses all the knowledge needed to assemble/acquire the associated object b; it has all the information or pulls information from the external environment into itself, whereas dependency injection (inversion of control) means the environment pushes information into object a. Declarative dependency injection delays the moment of pushing information to the last possible instant: right up until the object is used. At this last moment, we**
have all the relevant information about runtime objects and no longer need to predict the object’s usage scenarios or purposes**
; we can choose the implementation that best fits our actual needs.

In Spring 1.0, the configuration file contains all the assembly-related information; the assembly approach is very flexible. However, we don’t always need this entire flexibility. At compile-time, we already know part of the information about dependent objects: the object’s interface type. Within a certain scope, often there is only a single object that implements the specified interface type. Via the `@Autowired` annotation, we fully leverage the portion of information the object holds, automatically injecting the dependent object based on it, without needing to manually define dependencies between objects again in bean.xml.

Introduced in Spring 2.5, the role of the `@Autowired` annotation is actually similar to the import keyword in the Java language. The import keyword brings in an externally defined Java class; we still need to invoke the constructor, set the relevant member variables, and call the initialization function to obtain an object capable of serving externally.

```java
// Declare dependent class
import test.MyObject;

// Declare dependent object
@Autowired
MyObject a;
```

With the `@Autowired` annotation, we can directly import a Java object in an active state that can be used immediately, rather than a static template for object creation (classes can be regarded as templates for creating objects).

The `@Autowired` annotation can only be applied to class member variables or methods, essentially due to restrictions of the Java language. We can imagine a program syntax that allows injecting dependent objects directly into any local variable:

```java
public void myMethod(@Inject MyObject b){
    if(b.value > 3){
        @Inject MyObject a;
    }
}
```

We could also choose to provide an inject function that takes a type as a parameter and returns an object that implements that type. For example:

```java
const a = inject(MyObject);
or
const a = inject<MyObject>(); // If the language’s built-in metaprogramming can read generic type info
```

This is the solution used in the frontend framework vue 3.0’s provide/inject.

### 1.4 AOP Interception

The conjunction of dependency injection and AOP (Aspect Oriented Programming) is a natural, inevitable result. **The essence of dependency injection is to bring in the influence of the external environment (the IoC container is an environment object with global knowledge and global rules).** We never rely on and use bare objects; rather, objects are immersed in the environment and will be enhanced by the environment’s rules as wrapped objects.

> In our physical world, all fundamental particles, such as quarks and electrons, are themselves massless, but they interact with the ubiquitous Higgs field. The motion of an electron always drags the surrounding Higgs particles along, so the electron we observe always has mass.

Our understanding of environmental influence is not completed in one step. In the early days of component technologies, Microsoft’s COM component technology was the de facto market standard. An emphasized design point then was that once you obtained a pointer to a dependent object from the global Registry, you directly interacted with that object, thereby completely escaping reliance on the global environment to achieve the highest performance. But with the development of Microsoft’s DCOM (Distributed COM), the importance of sustained environmental influence was gradually recognized. In today’s cloud-native environments, the ubiquitous service mesh makes all interactions between service objects effectively indirect. **Objects interact within a mesh (similar to an electromagnetic field).**

AOP is a standardized means of enhancing raw, bare objects within programs.

```
Enhanced Object = Naked Object + Environment(Interceptors)
```

Therefore, when a dependency injection container already has global environment management capabilities, if some environment information is still needed for subsequent interactions when we inject object b into object a, the container can bundle this environment information with the original object b, generate an enhanced object via AOP, and then inject it into object a.

Here’s an interesting question: Since AOP enhancement binds some environment information to a specific object, for environment information that can be obtained directly from global knowledge, we don’t need to perform AOP enhancement for every object separately. For instance, in Java backend development, Controller objects typically need to annotate all modification operations with `@Transactional`, indicating that the method must run within a transaction context. However, **if we uniformly adopt the GraphQL interface protocol and define a global rule: all mutation operations execute within a transactional environment, then we don’t need to apply Transactional enhancement to each individual object**, thereby reducing unnecessary calls and improving performance.

### 1.5 @ComponentScan Dynamically Collecting Bean Definitions

For any structure of reasonable complexity, we inevitably need a decomposition mechanism to break it down into multiple parts that can be independently identified, stored, and managed, and then a synthesis mechanism to assemble these parts back together.

Spring 1.0 introduced a built-in import syntax that can break a complex beans.xml file into multiple subfiles.

```xml
<beans> 
   <import resource="base.beans.xml" />
   <import resource="ext.beans.xml" />
   <bean profile="dev">
      <import resource="dev.beans.xml" />
   </bean>
</beans>
```

The design of the import syntax is relatively crude; its semantics are essentially an include, equivalent to copying and pasting the content from an external beans.xml file. If we import the same file multiple times, it will lead to duplicate bean definitions and throw a BeanDefinitionOverrideException.

ComponentScan is a more flexible solution. First, it has the property of **idempotency**: scanning the same package multiple times does not lead to duplicate bean registration, making its semantics closer to the import semantics in programming languages. Second, it **leverages the existing package structure** as the basic unit of collection, allowing flexible choices of which packages or classes to collect. If we were to implement such flexible organization using XML, we would need to create an xml file for each package.

### 1.6 @Conditional Conditional Assembly

Spring 1.0 provides complete assembly primitives, but it does not define how to incorporate more variability into assembly. Based on Spring 1.0 mechanisms, to accommodate business changes, the only thing we can do is manually adjust the beans.xml configuration file, which leads to frequent changes and poor reusability.

From Spring 4.0 onward, Spring provides the `@Conditional` annotation to implement conditional assembly, and it eventually evolved into SpringBoot’s `@ConditionalOnBean`, `@ConditionalOnProperty`, and other condition annotations with explicit domain semantics. Based on these condition annotations, many variabilities that can be predicted at compile time are explicitly defined, and common configuration combinations can be solidified as defaults.

Without condition annotations, we can only define a single, fixed assembly process. With condition annotations, we can **predefine multiple possible assembly processes and provide one most common choice as the default**.

> Programming always faces multiple feasible worlds, not just the current deterministic one.

From the perspective of Reversible Computation, we can consider that Spring 1.0 provides an assembly model built from scratch, while **SpringBoot provides a Delta-oriented assembly model**, where we only need to add some Delta descriptions relative to the default configuration, thereby greatly reducing the amount of configuration work required for business development.

### 1.7 @EnableAutoConfiguration Multi-Entry Auto-Configuration

Spring 1.0 provides a single-entry static configuration scheme, i.e., we read the beans.xml configuration file from a fixed location and analyze it, recursively reading its included subconfiguration files. SpringBoot provides a multi-entry dynamic configuration scheme: each time we introduce a dependency module, we automatically bring in its corresponding entry configuration class, whose role is equivalent to dynamically generating configuration files and introducing related beans.

```
Config = Registrar(ScanClassesA) + Registrar(ScanClassesB) + ...
```

Multi-entry plus dynamic bean scanning and registration greatly simplifies configurations for applications under default settings, but it also introduces new complexity.

Under Spring 1.0 syntax, bean parsing and registration are executed in the explicit XML-described order; the execution result is deterministic, making diagnostics relatively easy when issues arise. Under multi-entry configuration, the scanned bean configurations are dynamically merged together, and their merging rules and results are implicit and non-obvious. Sometimes, seemingly trivial adjustments to package order can lead to different execution results, and results in the IDE may subtly differ from those in runtime packaging and deployment. Once we deviate from default configurations, it’s easy to observe configuration chaos when multiple modules are merged at runtime, and generally, if you’re not very familiar with low-level implementation details, it’s hard to pinpoint the problem.

> In daily development, it’s common to see newcomers spend a lot of time diagnosing why SpringBoot’s auto-configuration doesn’t work after introducing new modules or adjusting default configurations.

The best practice to resolve this is to avoid defining the same bean in multiple packages and avoid complex dependencies between beans. Essentially, we hope that the merging of multiple dynamic configurations **obeys the commutative law**, i.e., no matter what order they are identified and processed, the final result remains unaffected—an expectation that declarative programming promises.

### 1.8 Embedded Expressions and Reactive Configuration

In Spring 1.0, we can extract configuration parameters from XML files through the placeholder mechanism, for example:

```xml
<bean id="dataSource" ...>
  <property name="jdbcUrl" value="${spring.datasource.jdbc-url}" />
</bean>
```

Placeholders can be viewed as an adaptation mechanism that binds the application.properties configuration parameter file and the applicationContext.xml object assembly file together. A placeholder can be regarded as an adaptation expression: it extracts parameter information from the application.properties file and applies it to the current bean configuration. Conceptually, its work is:

```
bean.jdbcUrl = props.get('spring.datsource.jdbc-url')
```

In subsequent Spring development, this mechanism was extended in two directions. First, the concept of expression was enhanced. After Spring 3.0, we can use a true Expression Language to write adaptation expressions. For example:

```xml
<bean id="readStep">
   <property name="filePath" value="#{jobParameters['filePath']}" />
   <property name="testValue" value="#{T(java.lang.Math).PI}" />
</bean>
```

In the EL expression execution context, not only do we have the configuration variables defined in application.properties, but the context also contains all beans defined in the bean container, and we can directly access all Java classes by class name.

The second direction is enhanced dynamic configuration variable collections. After Spring 3.1, the so-called Environment concept was introduced. Leveraging this concept, SpringCloud extended rudimentary configuration variable sets into a distributed configuration center with reactive updates.

```java
@RefreshScope
@Service
public class MyService{

    @Value("${app.user-local-cache}")
    boolean useLocalCache;
}
```

Beans annotated with RefreshScope are recreated when configurations change, thereby applying new configurations.

## II. Design Defects of SpringBoot

### 2.1 A Departure from the Basic Principles of Declarative Programming

Although Spring was founded on declarative assembly, it has drifted away from declarative programming since Spring 2.0. Spring 1.0 could only constrain XML file formats with rudimentary DTD syntax; Spring 2.0 introduced the more powerful XML Schema to provide stricter format definitions, but at the same time it introduced the custom namespace mechanism. Custom namespaces are parsed and processed by the [NamespaceHandler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/xml/NamespaceHandler.html#:~:text=public%20interface%20NamespaceHandler%20Base%20interface%20used%20by%20the,custom%20namespaces%20in%20a%20Spring%20XML%20configuration%20file.) interface. On the surface, this seems to provide declarative custom XML tags, but behind the scenes it is a series of imperative code blocks with strong sequencing dependencies.

1. When implementing custom tags, we cannot reuse existing functionality through a simple declarative approach. Adding a new custom tag requires implementing a new NamespaceHandler and adding a series of related registration configurations. The cost is high, and it’s difficult to ensure that tags from different namespaces can be correctly nested.

2. Although Spring 1.0 provides complete object assembly primitives, Spring 2.0’s custom tags cannot be reduced to the tags defined in Spring 1.0. In other words, if a software package provides configuration support using Spring 2.0 syntax, we cannot guarantee that it can be configured using Spring 1.0 syntax. This results in more semantic ambiguity as custom configuration tags proliferate, making it hard to compile a general Spring configuration analysis tool.

3. 

In SpringBoot’s design and evolution, there is no clearly defined semantic model. Behind many annotations lies very complex imperative identification and handling code entangled with global state and lacking coordination rules. In practice, bean assembly results exhibit very subtle relationships with the execution order of imperative code. This becomes particularly evident when we need to migrate SpringBoot configuration code to other IoC containers.

Take Quarkus migration as an example. [Quarkus](https://quarkus.io/) is an open-source Kubernetes-native JavaTM framework tailored for GraalVM and OpenJDK HotSpot. Like the Go language, it supports Ahead-of-Time (AOT) compilation to directly produce a single executable, freeing itself from the JDK runtime at execution, improving startup speed and reducing memory consumption. As a new framework, Quarkus certainly covets Spring’s vast community resources. To this end, it attempts to provide an adaptation mechanism for migrating from Spring to Quarkus. The first problem Quarkus faces is that Spring’s various annotation classes weren’t separated into a standalone annotation API package but were mixed with all sorts of implementation code. Quarkus had to extract some annotation classes into a separate jar and implement a hacky approach to replace Spring dependency packages. The second problem is that Spring’s assembly process cannot be preprocessed via compile-time analysis, making it impossible to implement object assembly through compile-time code generation as Quarkus’s built-in CDI container does. Quarkus’s choice is to support only those Spring annotations with clear semantics, while ignoring various load-and-scan implementations based on imperative code. This directly results in its Spring compatibility and migration features being merely window dressing, suitable for marketing but not a true migration tool.

From a non-technical standpoint, SpringBoot’s non-declarative design might be intentional. As a commercial product, the team behind Spring would prefer the community’s sunk cost in this product to grow over time, thereby constructing migration barriers favorable to themselves. Compatibility features are easy; compatibility with bugs is hard. The reason a community product can run smoothly within the Spring container is that massive manual debugging effort has been invested, tolerating various bugs and design conflicts. When migrating to a new framework or platform, unless we fully move Spring’s container implementation code, how can we guarantee a bug happens to be triggered and our bug-prevention code happens to kick in?

### 2.2 Compatibility Burdens from Past Successes

Spring has a very long history—since its 1.0 release in March 2004 to now, it’s been nearly twenty years. Over the course of a whole generation, it witnessed the development of various programming paradigms and techniques, and at each stage it successfully provided corresponding encapsulation support. These successful experiences sedimented at Spring’s lower levels into today’s seemingly inexplicable redundancy.

Take SpringMVC as an example. When frontend JSON field names don’t match, the backend may not throw a clear JSON parsing error but rather some other obscure error. SpringMVC supports multiple parameter-passing mechanisms; when one mechanism fails to parse, it tries the next. Before JSON gained wide popularity, various imaginative [encoding schemes](https://www.npmjs.com/package/qs) existed for transmitting complex structures—for example, `?a[]=1&a[]=2` for arrays and `foo[bar]=baz` for Maps. Search for SpringMVC online today and you’ll find many “X ways to pass complex objects in SpringMVC” articles.

If we adopt best practices for the present moment, we can say that over 90% of SpringMVC’s code is completely unnecessary. For example, if we adopt the GraphQL interface standard, the backend only needs to recognize a single `/graphql` endpoint that accepts only POST requests, receives only JSON for the request body, and returns JSON in the response body. Various encoding schemes and the URL matching spec defined in JAX-RS become redundant.

### 2.3 Constraints from the “Don’t Reinvent the Wheel” Positioning

Spring has long presented itself as a packager, claiming not to reinvent wheels—just nature’s packer—polishing and packaging the industry’s most mature and excellent implementation technologies. This positioning places Spring in an awkward spot when handling many issues: should it propose a complete interface standard that fully shields underlying implementations, or should it retain all the lower-level implementation details and merely wrap them in SpringBoot’s configuration style? The inconsistency of underlying technical sources and styles also makes Spring’s upper-layer encapsulation work challenging.

For example, consider Spring’s classic declarative transaction encapsulation. To unify transaction handling for plain JDBC and Hibernate operations, Spring internally defines multiple thread context objects like SessionHolder and ConnectionHolder and relies on TransactionSynchronization for synchronization. But if Hibernate and Spring could collaborate, Hibernate could directly call JdbcTemplate to perform database access without adding extra wrappers. In fact, after Hibernate 5.3, Hibernate explicitly introduced the [BeanContainer](https://docs.jboss.org/hibernate/stable/core/javadocs/org/hibernate/resource/beans/container/spi/BeanContainer.html) interface and assumed the presence of an IoC container, making some traditional encapsulations in Spring [lose their significance](https://www.matez.de/index.php/2019/04/05/connecting-spring-and-hibernate-though-beancontainer).

Sometimes, technologies being encapsulated are themselves overly complex concept systems relative to our needs—for example, the AOP mechanism provided by AspectJ. AspectJ offers very powerful aspect interception capabilities, can match package names and method names via regex-like syntax, and can recognize complex nested call relationships.

```java
    @Pointcut("execution(public * *(..))")
    private void anyPublicOperation() {}

    @Pointcut("within(com.xyz.someapp.trading..*)")
    private void inTrading() {}

    @Pointcut("anyPublicOperation() && inTrading()")
    private void tradingOperation() {}
```

However, in daily business development, only one pointcut definition is used widely: intercepting Java methods annotated with a specified annotation. Spring, in its AOP conceptual system, always seeks convergence to AspectJ, thereby needlessly adding complexity. In the Nop platform, to introduce AOP support into the DI container, we added fewer than about 1,000 lines of code; at this point, the cost of wrapping an extra AOP framework is already much higher than implementing it directly.

Spring’s design style in recent years has begun to shift. For example, SpringCloud was initially built on the Netflix OSS codebase. Later, as the Netflix OSS codebase fell behind the times, SpringCloud gradually embarked on a journey of self-development.

## III. NopIoC: A Declarative IoC Container

NopIoC is the lightweight dependency injection container used in the Nop platform. Initially, my goal was to define a BeanContainer interface compatible with Spring and Quarkus, but I quickly discovered that Spring’s native application support module, spring-native, is quite immature, while Quarkus’s DI container’s organizational capabilities fall far short of SpringBoot. Some configurations that are very simple in SpringBoot are hard to implement in Quarkus, and Quarkus’s precompilation approach makes runtime debugging difficult. So I ultimately decided to implement an IoC container as the default BeanContainer for the Nop platform.

### 3.1 XDef Meta-Model Definition

**A declarative IoC container must have a clearly defined semantic domain model**, which can be regarded as a DSL (Domain Specific Language). The IoC container itself is the interpreter and executor of this DSL. If we serialize domain model objects to text for storage, we get an IoC-specific model file, e.g., Spring’s beans.xml configuration file. Java annotations can be considered another manifestation of this domain model—for instance, Hibernate’s model definitions can be expressed via JPA annotations or via hbm configuration files.

**A well-defined model can be described by a general Meta-Model.** Spring 1.0’s XML syntax has an XML Schema definition, but capabilities introduced in SpringBoot—such as conditional assembly—lack corresponding XML syntax, so SpringBoot ultimately does not have a clearly defined domain model.

The XML Schema format, although far more powerful than DTD, is extremely verbose. Its design goal is merely to constrain general XML data files, and it falls short when constraining DSL models with execution semantics.

NopIoC uses the XDefinition meta-model language to define its domain model. The XDef language is the Nop platform’s replacement for XML Schema and JSON Schema. It is designed specifically for DSLs, making information expression far more intuitive and efficient than XML Schema and JSON Schema. You can directly obtain executable domain models based on XDef definitions, generate code from XDef definitions, produce IDE hints, and even generate visual designer pages, etc.

Below we can directly compare Spring 1.0’s configuration format defined with xsd versus xdef:

https://www.springframework.org/schema/beans/spring-beans-4.3.xsd

[nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)

I will write a follow-up article specifically introducing the technical details of the XDef meta-model definition language.

### 3.2 Natural Extensions of Spring 1.0 Syntax

NopIoC builds upon Spring 1.0’s configuration syntax (NopIoC can parse Spring 1.0 configuration files directly) and supplements concepts introduced in SpringBoot such as conditional assembly. All extended properties use the `ioc:` prefix to distinguish them from Spring’s built-in properties.

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

The configuration above corresponds to SpringBoot’s configuration:

```java
@ConditionalOnProperty("xxx.enabled")
@ConditionalOnMissingBean({DataSource.class})
@ConditionalOnClass({MyObject.class})
@Bean("xx.yy")
public XXX getXx(){
}
```

### 3.3 Source-Code-Generated AOP

Using AOP in NopIoC is very simple—just configure the interceptor’s pointcut:

```xml
 <bean id="nopTransactionalMethodInterceptor" 
      class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
     <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
          order="1000"/>
 </bean>
```

This configuration means: scan all beans in the container (unless ioc:aop is set to false); if a method on a bean has the `@Transactional` annotation, apply this interceptor.
The implementation principle is:

1. Register the annotations to be recognized by AOP in the `resources/_vfs/nop/aop/{module-name}.annotations` file.

2. During project compilation, a Maven plugin will scan the classes under target/classes to check whether class methods have AOP-recognizable annotations; if so, it generates a __aop derived class for that class to insert AOP interceptors. Thus, the packaged jar contains the AOP-related generated code, and no dynamic bytecode generation is needed when using AOP. The underlying principle is similar to AspectJ, but the process is much simpler. See:

   [nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java)

3. When the IoC container creates beans, if it finds an interceptor applicable to a class, it uses the __aop derived class to instantiate the object and insert the interceptor.

Example generated file:

[docs/ref/AuditServiceImpl__aop.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/ref/AuditServiceImpl__aop.java)

### 3.4 Layered Abstractions Implemented Based on the Principles of Reversible Computation

NopIoC utilizes compile-time generation techniques defined by Reversible Computation to provide abstractions similar to Spring 2.0’s custom tags.

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

All DSL models in the Nop platform support the `x:gen-extends` mechanism, which runs at compile time, outputs XML nodes, and then merges with external XML nodes via the DeltaMerge algorithm to synthesize final XML configuration nodes. This is equivalent to writing Spring 2.0 custom tags with the XPL template language, executing those tags at compile time, and outputting configuration content in Spring 1.0 syntax. The NopIoC engine only needs to support the most basic Spring 1.0 syntax to obtain custom tag abstractions for free.

In the Nop platform, the concept of layered abstraction is pervasive, allowing us to perform as many operations as possible at compile time, reducing runtime complexity and improving runtime performance. For example, after finishing all conditional evaluations and type-based scans, NopIoC dumps a final assembly version—stripped of all optional conditions—to the _dump directory. This version can be executed by a Spring 1.0 execution engine. On this basis, we can write a translator to convert Spring 1.0 XML configuration into annotation-based configuration to adapt to other IoC runtimes, or translate it into pure Java construction code, eliminating the IoC runtime entirely.

### 3.5 Generating Java Proxies

NopIoC includes a built-in ioc:proxy property that can directly create a Proxy object implementing a specified interface based on the current bean.

```xml
<bean id="myBean" class="xx.MyInvocationHandler" 
      ioc:type="xx.MyInterface" ioc:proxy="true" />
```

With the configuration above, the actual returned myBean object is a proxy object implementing the MyInterface interface.

### 3.6 Scanning by Annotation or Type

NopIoC has built-in capabilities to collect beans by annotation. For example:

```xml
 <bean id="nopBizObjectManager" class="io.nop.biz.impl.BizObjectManager">
     <property name="bizModelBeans">
        <ioc:collect-beans 
           by-annotation="io.nop.api.core.annotations.biz.BizModel"
           only-concrete-classes="true"/>
     </property>
 </bean>
```

This configuration searches the container for all classes annotated with `@BizModel`, ignores abstract classes and interfaces, and considers only concrete implementations.

### 3.7 Prefix-Guided Syntax

In Spring 1.0’s design, to obtain some built-in properties and objects from the IoC container, objects must implement certain interfaces such as BeanNameAware, ApplicationContextAware, etc. In NopIoC, we can obtain corresponding values via the prefix-guided syntax. For example:

```xml
<bean id="xx">
   <property name="id" value="@bean:id" />
   <property name="container" value="@bean:container" />
   <property name="refB" value="@inject-ref:objB" />
  <!-- Equivalent to -->
   <property name="refB" value-ref="objB" />
</bean>
```

Prefix-guided syntax is widely used in the Nop platform, a highly general and extensible syntax design. For detailed introductions, see my article:

[DSL Layered Syntax Design and Prefix-Guided Syntax](https://zhuanlan.zhihu.com/p/548314138)

### 3.8 Reactive Configuration Updates

NopIoC has built-in knowledge of reactive configurations. We can specify reactive configuration binding for individual properties:

```xml
<bean id="xx">
  <!-- @cfg means fetching the configuration value when the bean is first created, but not performing reactive updates -->
  <property name="configValue" value="@cfg:config.my-value" />
  <!-- @r-cfg means automatically updating the bean’s property when the configuration value changes -->
   <property name="dynValue" value="@r-cfg:config.my-dyn-value" />
</bean>
```

We can also bind all properties of an object to configuration items via a prefix, similar to Spring’s `@ConfigurationProperties` annotation:

```xml
<!-- ioc:auto-refresh means the bean’s properties will be automatically updated when configuration changes -->
<bean id="xx" ioc:config-prefix="app.my-config" ioc:auto-refresh="true"
    class="xxx.MyConfiguration" />
```

NopIoC also defines a special syntax node, ioc:config:

```xml
<ioc:config id="xxConfig" ioc:config-prefix="app.my-config" ... />

<bean id="xx" ioc:on-config-refresh="refreshConfig" >
  <property name="config" ref="xxConfig" />
</bean>
```

When configuration updates, xxConfig will be automatically updated, and this update process will propagate to all beans using xxConfig, triggering their refreshConfig functions.

### 3.9 Auto-Configuration Discovery

NopIoC provides a mechanism similar to SpringBoot’s AutoConfiguration. During initialization, NopIoC automatically looks for all files with a .beans suffix in the `/nop/autoconfig` directory of the virtual file system and loads the beans.xml files defined therein. For example, the content in the [/nop/autoconfig/nop-auth-core.beans](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-core/src/main/resources/_vfs/nop/autoconfig/nop-auth-core.beans) file is /nop/auth/beans/auth-core-defaults.beans.xml. Generally, the filename of the beans file is the corresponding Java module name, so when multiple modules are packaged into a fat-jar, file conflicts do not occur.

Unlike SpringBoot, NopIoC does not register beans while loading configuration files. NopIoC only executes the conditional logic once after collecting all bean definitions. Therefore, in NopIoC, the order of bean definitions does not, in principle, affect the container’s dynamic computation result.

### 3.10 Unit Test Support

NopIoC is integrated with JUnit5. In unit tests, we mainly control IoC container initialization via the `@NopTestConfig` annotation.

```java
public @interface NopTestConfig {
    /**
     * Whether to force set nop.datasource.jdbc-url to an H2 in-memory database
     */
    boolean localDb() default false;

    /**
     * Use randomly generated server ports
     */
    boolean randomPort() default false;

    /**
     * Default to lazy mode when running unit tests
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    /**
     * Whether to automatically load xxx.beans configurations under /nop/auto-config/
     */
    boolean enableAutoConfig() default true;

    String autoConfigPattern() default "";

    String autoConfigSkipPattern() default "";

    /**
     * Beans configuration file specified for unit tests
     */
    String testBeansFile() default "";

    /**
     * Properties configuration file specified for unit tests
     */
    String testConfigFile() default "";
}


@NopTestConfig
public class MyTestCase extends JunitBaseTestCase{
    // Inject beans managed by the container via @Inject
    @Inject
    IGraphQLEngine engine;
}
```

NopIoC’s JUnit support provides the following features:

1. Control whether to use a test in-memory database to replace the database connection specified in the configuration file

2. Control whether to enable autoconfig and which module autoconfigs to use

3. Control whether to introduce test-specific beans configurations

4. Control whether to introduce test-specific properties configurations

5. Support injecting beans in test cases via @Inject

For a detailed introduction to automation in the Nop platform, see my previous article:

[Automated Testing in Low-Code Platforms](https://zhuanlan.zhihu.com/p/569315603)

## Summary

The Nop platform is a low-code development platform built from scratch based on the principles of Reversible Computation. NopIoC is an optional component of the Nop platform. All other modules in the Nop platform have no direct dependency on NopIoC; in principle, as long as the IBeanContainer interface is implemented, NopIoC can be replaced. However, compared with frameworks like Spring/Quarkus, NopIoC has some unique designs—especially the layered abstractions implemented based on Reversible Computation—which allow it to provide a very rich, complex feature set while maintaining a simple structure.

For a detailed introduction to Reversible Computation theory, see my previous articles:

[Reversible Computation: Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)

[Technical Implementation of Reversible Computation](https://zhuanlan.zhihu.com/p/163852896)

[Low-Code Platform Design through the Lens of Tensor Products](https://zhuanlan.zhihu.com/p/531474176)

<!-- SOURCE_MD5:c7182078e801ed6d4e0cd39b93223291-->
