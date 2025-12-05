# NopIoC Container

NopIoC is a lightweight dependency injection container used in the Nop platform. Initially, my goal was to define a BeanContainer interface compatible with Spring and Quarkus, but I soon found that Spring’s native application support module spring-native was very immature, Quarkus’s dependency injection container’s organizational capability was far inferior to Spring Boot, some configurations that are very simple in Spring Boot were hard to achieve in Quarkus, and Quarkus’s ahead-of-time compilation made runtime debugging difficult. So I ultimately decided to implement an IoC container as the default BeanContainer implementation for the Nop platform.

## 3.1 XDef Meta-Model Definition

A descriptive IoC container must have a domain model with a well-defined semantics. This model can be considered a DSL (Domain Specific Language). The IoC container itself is the interpreter and executor of this DSL. If we serialize domain model objects into text and save them, they become an IoC-specific model file, for example Spring’s beans.xml configuration file. Java annotations can be regarded as another form of this domain model; for instance, Hibernate’s model definition can be expressed with JPA annotations or with hbm configuration files.

A well-defined model can be described by a general meta-model. Spring 1.0’s XML syntax has an XML Schema definition, but capabilities introduced in Spring Boot such as conditional configuration lack the corresponding XML syntax, which ultimately results in Spring Boot not having a clearly defined domain model.

Although XML Schema is much more powerful than DTD, it is very verbose, and its design goal is only to constrain general XML data files. For constraining DSL models with execution semantics, it falls short.

NopIoC uses the XDefinition meta-model language to define its domain model. The XDef language is used in the Nop platform to replace XML Schema and JSON Schema. It is designed specifically for DSLs and is far more intuitive and efficient than XML Schema and JSON Schema in terms of expressive power. You can directly obtain an executable domain model from the XDef definition, or generate code, IDE hints, and even visual designer pages based on the XDef definition.

Below we can intuitively compare the Spring 1.0 configuration formats defined with xsd and xdef respectively

https://www.springframework.org/schema/beans/spring-beans-4.3.xsd

[nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)

I will write a dedicated article later to introduce the technical details of the XDef meta-model definition language.

## 3.2 Natural Extensions to Spring 1.0 Syntax

NopIoC is based on Spring 1.0’s configuration syntax (NopIoC can directly parse Spring 1.0 configuration files) and supplements it with concepts introduced by Spring Boot such as conditional wiring. All extension attributes use the `ioc:` prefix to distinguish them from Spring’s built-in attributes.

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

The configuration above corresponds to Spring Boot configuration:

```java
@ConditionalOnProperty("xxx.enabled")
@ConditionalOnMissingBean({DataSource.class})
@ConditionalOnClass({MyObject.class})
@Bean("xx.yy")
public XXX getXx(){
}
```

### 3.3 AOP Based on Source Code Generation

Using AOP in NopIoC is very simple: just configure the pointcut for the interceptor.

```xml
 <bean id="nopTransactionalMethodInterceptor"
      class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
     <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
          order="1000"/>
 </bean>
```

The above configuration means the container will scan all beans (whose ioc:aop attribute is not set to false). If it finds that some method has the `@Transactional` annotation, it applies this interceptor.
The specific implementation principles are:

1. Register annotation classes that need to be recognized by AOP in `resources/_vfs/nop/aop/{module name}.annotations`.

2. During project compilation, a Maven plugin scans the classes under the target/classes directory and checks whether methods of classes have annotations recognizable by AOP. If so, it generates a \_\_aop derived class for that class to insert the AOP interceptor. Thus, the packaged jar contains the AOP-related generated code, and there is no need to generate bytecode dynamically at runtime when using the AOP mechanism. The implementation principle is actually similar to AspectJ, but the process is greatly simplified. For the specific implementation of the code generator, see

   [nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java)

3. When the IoC container creates a bean, if it finds that there are interceptors applicable to the class, it uses the \_\_aop derived class to instantiate the object and inserts the interceptor.

An example of the generated file is as follows:

[docs/ref/AuditServiceImpl__aop.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/ref/AuditServiceImpl__aop.java)

### 3.4 Layered Abstraction Implemented Based on the Principles of Reversible Computation

NopIoC uses compile-time generation techniques defined by Reversible Computation to provide custom tag abstractions similar to Spring 2.0.

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

All DSL models in the Nop platform support the `x:gen-extends` mechanism. It runs at compile time, outputs XML nodes, and then executes the DeltaMerge merge algorithm with external XML nodes to produce the final XML configuration nodes. This is equivalent to writing Spring 2\.0 custom tags using the XPL template language, then executing these tags at compile time to output configuration content in Spring 1.0 syntax. The NopIoC engine only needs to support the most basic Spring 1.0 syntax to obtain custom tag abstractions for free.

In the Nop platform, the concept of layered abstraction is pervasive, allowing us to perform as many operations as possible at compile time, reduce runtime complexity, and improve runtime performance. For example, after completing all condition evaluation and type scanning, NopIoC outputs a final assembled version that removes all optional conditions to the \_dump directory. This version can be executed by a Spring 1\.0 execution engine. On this basis, we can write a translator that converts Spring 1.0 syntax XML configuration into annotation-based configuration to adapt to other IoC runtimes, or translate it into Java construction code, completely eliminating the IoC runtime.

### 3.5 Generate Java Proxy

NopIoC has a built-in ioc:proxy attribute, which can directly create a Proxy object that implements a specified interface based on the current bean.

```xml
<bean id="myBean" class="xx.MyInvocationHandler"
      ioc:type="xx.MyInterface" ioc:proxy="true" />
```

With the above configuration, the actual object returned for myBean is a proxy that implements the MyInterface interface.

### 3.6 Scan by Annotation or Type

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

The above configuration means to find all classes in the container that have the `@BizModel` annotation, ignore all abstract classes and interfaces, and consider only concrete implementation classes.

### 3.7 Prefix-Guided Syntax

In the design of Spring 1\.0, in order to obtain some built-in properties and objects of the IoC container, objects must implement specific interfaces, such as BeanNameAware and ApplicationContextAware. In NopIoC, we can obtain the corresponding values through prefix-guided syntax. For example:

```xml
<bean id="xx">
   <property name="id" value="@bean:id" />
   <property name="container" value="@bean:container" />
   <property name="refB" value="@inject-ref:objB" />
  <!-- Equivalent to -->
   <property name="refB" value-ref="objB" />
</bean>
```

Prefix-guided syntax is widely used in the Nop platform and is a very general and extensible syntax design. For a detailed introduction, see my article

[DSL layered syntax design and prefix-guided syntax](https://zhuanlan.zhihu.com/p/548314138)

### 3.8 Reactive Configuration Updates

NopIoC has built-in support for reactive configuration. We can specify reactive configuration binding for a single property:

```xml
<bean id="xx">
  <!--  @cfg means to obtain the configuration value when the bean is created for the first time, but it will not be reactively updated -->
  <property name="configValue" value="@cfg:config.my-value" />
  <!-- @r-cfg means that when the configuration value changes, the bean’s property value is automatically updated -->
   <property name="dynValue" value="@r-cfg:config.my-dyn-value" />
</bean>
```

Or, similar to Spring’s `@ConfigurationProperties` annotation, bind all properties on the object to configuration items via a prefix:

```xml
<!-- ioc:auto-refresh means that when the configuration changes, the bean’s properties are automatically updated -->
<bean id="xx" ioc:config-prefix="app.my-config" ioc:auto-refresh="true"
    class="xxx.MyConfiguration" />
```

NopIoC also defines a special syntax node ioc:config:

```xml
<ioc:config id="xxConfig" ioc:config-prefix="app.my-config" ... />

<bean id="xx" ioc:on-config-refresh="refreshConfig" >
  <property name="config" ref="xxConfig" />
</bean>
```

When the configuration is updated, xxConfig will be automatically updated, and this update process will propagate to all beans that use xxConfig, triggering their refreshConfig function.

### 3.9 Auto-Configuration Discovery

NopIoC provides a mechanism similar to Spring Boot’s AutoConfiguration. NopIoC will automatically search the virtual file system for all files with the suffix beans under the `/nop/autoconfig` directory during initialization and automatically load the beans.xml files defined therein. For example, the content of the file [/nop/autoconfig/nop-auth-core.beans](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/nop/autoconfig/nop-auth-core.beans) is /nop/auth/beans/auth-core-defaults.beans.xml. Generally, the file name of a beans file is the corresponding Java module name, so when multiple modules are packaged into a fat-jar, file conflicts will not occur.

Unlike Spring Boot, NopIoC does not register beans while loading configuration files. NopIoC only executes the conditional logic once after collecting all bean definitions. Therefore, in NopIoC, in principle, the order of bean definitions does not affect the result of the IoC container’s dynamic computation.

### 3.10 Get Beans by Type

`BeanContainer.getBeanByType(beanClass)` can find the corresponding bean by type. If multiple beans have the same type, you can set a bean’s primary attribute to true to prefer that bean, or set other beans’ autowire-candidate attribute to false to indicate they should not be considered as candidates.

### 3.11 Unit Test Support

NopIoC is integrated with JUnit 5. In unit tests, we mainly control the initialization process of the IoC container through the `@NopTestConfig` annotation.

```java
public @interface NopTestConfig {
    /**
     * Whether to force nop.datasource.jdbc-url to be set to an H2 in-memory database
     */
    boolean localDb() default false;

    /**
     * Use a randomly generated server port
     */
    boolean randomPort() default false;

    /**
     * By default, run unit tests in lazy mode
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    /**
     * Whether to automatically load xxx.beans configurations under /nop/auto-config/
     */
    boolean enableAutoConfig() default true;

    String autoConfigPattern() default "";

    String autoConfigSkipPattern() default "";

    /**
     * beans configuration file specified for unit tests
     */
    String testBeansFile() default "";

    /**
     * properties configuration file specified for unit tests
     */
    String testConfigFile() default "";
}


@NopTestConfig
public class MyTestCase extends JunitBaseTestCase{
    // You can inject beans managed by the container via @Inject
    @Inject
    IGraphQLEngine engine;
}
```

NopIoC’s JUnit support provides the following capabilities:

1. Control whether to use a test in-memory database to replace the database connection specified in the configuration file

2. Control whether to enable autoconfig and which modules’ autoconfig to use

3. Control whether to include beans configurations specific to tests

4. Control whether to include properties configurations specific to tests

5. Support injecting beans via @Inject in test cases

For a detailed introduction to automation support in the Nop platform, see [autotest.md](autotest.md)

### 3.11 Issue Diagnosis

#### Check Circular Dependencies
By default, NopIoC allows circular dependencies among bean definitions, such as A injects B, B injects C, and C injects A.
If `nop.ioc.bean-depends-graph.allow-cycle` is set to false, the reference relationships among beans will be checked at startup, and an error will be reported if a circular dependency is found.

You can break cycles via the `@IgnoreDepends` annotation or the `ioc:ignore-depends` attribute in ioc configuration.

```java
public class SysSequenceGenerator implements ISequenceGenerator {
  private IOrmTemplate ormTemplate;

  @Inject
  @IgnoreDepends
  public void setOrmTemplate(IOrmTemplate ormTemplate) {
    this.ormTemplate = ormTemplate;
  }
}
```

```xml
    <bean id="nopOrmSessionFactory" class="io.nop.orm.factory.OrmSessionFactoryBean"
          ioc:bean-method="getObject" ioc:default="true">

        <property name="daoListeners">
            <ioc:collect-beans by-type="io.nop.orm.IOrmDaoListener" ioc:ignore-depends="true"/>
        </property>
        ...
    </bean>
```
<!-- SOURCE_MD5:3057cef9f3ff698f07baf9aec588d45f-->
