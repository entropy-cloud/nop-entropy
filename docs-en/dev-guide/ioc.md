# NopIoC Container

NopIoC is a lightweight dependency injection container used in the Nop platform. Initially, my goal was to define a compatible BeanContainer interface with both Spring and Quarkus, but I quickly realized that Spring's native application support module (spring-native) is not mature, while Quarkus's dependency injection container lacks the organizational capabilities of SpringBoot. Some simple configurations in SpringBoot are difficult to implement in Quarkus, and Quarkus's preprocessing approach makes runtime debugging cumbersome. Therefore, I decided to implement an IoC container as the default BeanContainer implementation for the Nop platform.


## 3.1 XDef Meta-Model Definition

**An IoC container must have a domain model with a clearly defined semantic meaning**, which can be viewed as a Domain Specific Language (DSL). The IoC container itself serves as both the parser and executor of this DSL. If we serialize domain model objects into text for storage, it becomes an IoC-specific model file, such as Spring's beans.xml configuration file. Java annotations can also be considered another form of expressing this domain model, such as using JPA annotations to define Hibernate models or hbm configuration files.

**A well-defined model can be described by a general meta-model (Meta-Model)**. While Spring 1.0 provides XML syntax with an XSD definition, SpringBoot lacks corresponding XML syntax capabilities for conditional configurations, leading to a lack of a clear domain model in SpringBoot. Although XML Schema (XSD) is more powerful than DTDs, it is overly verbose and designed primarily for constraining general XML data files. For DSL models that require executable semantics, XSD falls short.

NopIoC adopted the XDefinition meta-model language to define its own domain model. XDef language is specifically designed as a replacement for XML Schema and JSON Schema in the Nop platform, offering more intuitive and efficient information expression compared to these formats. It allows direct generation of executable domain models, code generation, IDE tips, and even visualization of a design environment.

Below is a comparison of how XSD and XDef define Spring 1.0 configurations:

- **XSD Definition**:  
  [Spring 1.0 Configuration Format](https://www.springframework.org/schema/beans/spring-beans-4.3.xsd)

- **XDef Definition**:  
  [Nop-XDefs Configuration File](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)

I will write a separate article later to detail the technical specifics of the XDef meta-model language.


## 3.2 Natural Extension of Spring 1.0 Syntax

NopIoC builds upon Spring 1.0's configuration syntax (NopIoC can directly parse Spring 1.0 configuration files) and extends it with concepts introduced by SpringBoot, such as conditional configurations. All extended attributes in NopIoC are prefixed with `ioc:` to distinguish them from Spring's native attributes.

Below is an example of a Spring 1.0 configuration file, which corresponds to the following SpringBoot configuration:

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

SpringBoot equivalent:

```java
@ConditionalOnProperty("xxx.enabled")
@ConditionalOnMissingBean({DataSource.class})
@ConditionalOnClass({MyObject.class})
@Bean("xx.yy")
public XXX getXx() {
}
```


### 3.3 Aspect-Oriented Programming (AOP) Based on Source Code Generation

Using AOP in NopIoC is straightforward, as you only need to configure the corresponding interceptor and pointcut.

```xml
<bean id="nopTransactionalMethodInterceptor"
      class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
    <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
          order="1000"/>
</bean>
```

The above configuration indicates that the IoC container will scan all beans (provided that `ioc:aop` is not set to false) and apply the `TransactionalMethodInterceptor` to any method annotated with `@Transactional`.

### Implementation Details

1. **Registering AOP-Recognizable Annotations**  
   - The annotations that need to be recognized by AOP are registered in the file located at:
     ```
     resources/_vfs/nop/aop/{module_name}.annotations
     ```
   
2. **Scan and Compile with Maven Plugin**  
   - During the build process, a Maven plugin scans the `target/classes` directory for classes annotated with AOP-recognizable annotations.
   - If such annotations are found on methods of a class, an `_aop` derived class is generated to wrap the original class and inject the corresponding interceptor.

3. **IoC Container Behavior**  
   - When creating beans, if an interceptor is applicable to a method of the bean, an `_aop` derived class is instantiated and used instead.
   - The implementation pattern closely resembles AspectJ but with simplified logic for code generation. For detailed implementation specifics, refer to:
     ```
     nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java
     ```

4. **AOP Configuration in Nop Platform**  
   - All DSL models in the Nop platform support AOP configuration through `x:gen-extends`.
   - The process involves generating XML nodes during compilation, which are then merged using the DeltaMerge algorithm to form the final configuration node.
   - This mechanism effectively translates Spring 2.0-like syntax into custom tags and configurations within the Nop engine.

### Example Configuration

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

The Nop platform supports all DSL models through the `x:gen-extends` mechanism. This process generates XML nodes during compilation, which are then merged using the DeltaMerge algorithm to form final configuration nodes. The result is a simplified yet powerful way to define custom tags and configurations within the Nop engine.

### AOP Proxy Generation

NopIoC provides an `ioc:proxy` attribute that can be used to dynamically create proxy objects based on the current bean's interface.

```xml
<bean id="myBean" class="xx.MyInvocationHandler"
      ioc:type="xx.MyInterface" ioc:proxy="true" />
```

Using this configuration, the `myBean` object will be replaced by a proxy that implements the `MyInterface` and delegates calls to the actual bean.

### AOP Scanning

NopIoC supports scanning beans either via annotations or type-based scanning. For example:

- **Annotation-Based Scanning**: Detects classes annotated with specific AOP annotations, such as `@AopService` or `@AuditServiceImpl`.
- **Type-Based Scanning**: Automatically identifies classes that should be wrapped by interceptors based on their type.


The following configuration defines a bean "nopBizObjectManager" of type `io.nop.biz.impl.BizObjectManager`. This bean will automatically collect all classes annotated with `@BizModel` from the application context, ignoring abstract classes and interfaces.

```xml
<bean id="nopBizObjectManager" class="io.nop.biz.impl.BizObjectManager">
    <property name="bizModelBeans">
        <ioc:collect-beans by-annotation="io.nop.api.core.annotations.biz.BizModel"
                           only-concrete-classes="true"/>
    </property>
</bean>
```

---


### # 3.7 Prefix-Based Syntax in Spring 1.0

In Spring 1.0, to access built-in properties and methods of the IoC container, certain interfaces like `BeanNameAware` and `ApplicationContextAware` must be implemented. In NopIoC, however, we can achieve similar functionality using prefix-based syntax.

For example:

```xml
<bean id="xx">
    <property name="id" value="@bean:id" />
    <property name="container" value="@bean:container" />
    <property name="refB" value="@inject-ref:objB" />
    <!-- Equivalent to -->
    <property name="refB" value-ref="objB" />
</bean>
```

---


### # 3.8 Responsive Configuration Updates

NopIoC supports responsive configuration updates. We can bind individual properties to change-triggered updates.

```xml
<bean id="xx">
    <!-- `@cfg` is used at bean creation time to get the initial value -->
    <property name="configValue" value="@cfg:config.my-value" />
    <!-- `@r-cfg` triggers an update whenever the configuration changes -->
    <property name="dynValue" value="@r-cfg:config.my-dyn-value" />
</bean>
```

We can also use a similar approach to Spring's `@ConfigurationProperties` annotation, binding all properties of an object using prefix-based syntax.

```xml
<bean id="xx" class="xxx.MyConfiguration">
    <ioc:config-prefix="app.my-config" ioc:auto-refresh="true"/>
</bean>
```

---


### # 3.9 Automatic Configuration Discovery

NopIoC provides an auto-configuration mechanism similar to Spring Boot's `AutoConfiguration`. During initialization, it scans the virtual file system for files in the `/nop/autoconfig` directory that end with "beans.xml" and automatically loads them.

For example:

```xml
/nop/autoconfig/nop-auth-core.beans
```

```markdown
# Configuration File Path

The configuration file is located at `/nop/auth/beans/auth-core-defaults.beans.xml`. Typically, the filename of the beans file corresponds to the name of the Java module it represents. This ensures that when multiple modules are packaged into a single fat-jar, there is no file conflict.

## Differences from Spring Boot

 Unlike Spring Boot, NopIoC does not register bean configurations while loading the configuration files. Instead, NopIoC waits until all beans' definitions have been collected before performing the final condition logic check. This means that in NopIoC, the order of bean definition does not affect the dynamic calculation results of the IoC container.

## 3.10 Bean Retrieval by Type

 The `BeanContainer.getBeanByType(beanClass)` method allows retrieval of beans based on their type. If multiple beans exist for a specific type, you can set the `primary` attribute to true to prioritize one bean. Alternatively, you can set the `autowire-candidate` attribute of other beans to false, indicating they should not be considered as candidate beans.

## 3.11 Test Support

 NopIoC has been integrated with JUnit 5. In unit tests, we primarily use the `@NopTestConfig` annotation to control the initialization process of the IoC container.

## 3.12 Configuration Annotation

 The `@NopTestConfig` annotation is used as follows:

```java
public @interface NopTestConfig {
    /**
     * Whether to force the `nop.datasource.jdbc-url` to an H2 in-memory database.
     */
    boolean localDb() default false;

    /**
     * Whether to use a randomly generated port for the service.
     */
    boolean randomPort() default false;

    /**
     * Whether to use lazy loading mode for unit tests by default.
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    /**
     * Whether to automatically load configurations from the `/nop/auto-config/` directory.
     */
    boolean enableAutoConfig() default true;

    String autoConfigPattern() default "";
    String autoConfigSkipPattern() default "";

    /**
     * Specifies the test beans configuration file.
     */
    String testBeansFile() default "";

    /**
     * Specifies the test config files.
     */
    String testConfigFile() default "";
}
```

## Integration with JUnit

 NopIoC supports integration with JUnit 5. The `@NopTestConfig` annotation is used to control the initialization of the IoC container in unit tests.

```java
@NopTestConfig
public class MyTestCase extends JunitBaseTestCase {
    // Beans can be injected using @Inject
    @Inject
    IGraphQLEngine engine;
}
```

## NopIoC Test Support Features

1. Control whether to use an in-memory database (H2) instead of the configuration file's specified database connection.
2. Control whether auto-config is enabled and which modules' auto-config should be used.
3. Control whether to import test-specific beans configurations.
4. Control whether to import test-specific config files.
5. Support injection of beans using @Inject in test cases.

For detailed information about Nop platform automation support, refer to [autotest.md](autotest.md).

## 3.12 Cycle Detection

 By default, NopIoC allows circular dependencies among beans. For example:
- A depends on B,
- B depends on C,
- C depends on A.

 However, if `nop.ioc.bean-depends-graph.allow-cycle` is set to false, the container will check for circular dependencies during startup and report errors if any are found.

 Circular dependencies can be broken by either:
- Using the `@IgnoreDepends` annotation or
- Configuring `ioc:ignore-depends` in the configuration file

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

