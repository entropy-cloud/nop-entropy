# NopIoC容器开发指南

## 概述

NopIoC是Nop平台中使用的轻量级依赖注入容器，设计目标是兼容Spring和Quarkus的BeanContainer接口，同时实现了独立的IoC容器。它基于可逆计算原理，提供了高效、灵活的依赖注入能力，支持多种配置方式和高级特性。

## 核心概念

### 1. 依赖注入(DI)

依赖注入是一种设计模式，通过容器来管理对象的创建和依赖关系，使对象之间的耦合度降低。NopIoC支持构造函数注入和属性注入两种方式。

### 2. 控制反转(IoC)

控制反转是指将对象的创建和管理控制权从应用程序转移到容器，容器负责创建对象、管理对象生命周期，并处理对象之间的依赖关系。

### 3. Bean

Bean是由IoC容器管理的对象，包括单例(Singleton)和原型(Prototype)两种作用域。

### 4. 容器生命周期

容器的生命周期包括：初始化、启动、运行、停止和销毁。NopIoC支持异步启动和多种启动模式。

## 核心接口

### 1. IBeanProvider

为EL表达式提供bean注入功能，支持通过名称或类型获取bean。

### 2. IBeanContainer

容器管理接口，定义了启动、停止、获取bean等核心方法。

### 3. IBeanContainerImplementor

容器实现接口，扩展了IBeanContainer，提供了更丰富的容器管理功能。

## 核心实现类

### BeanContainerImpl

NopIoC的核心实现类，主要功能包括：

#### 容器生命周期管理
- 支持启动、停止、重启
- 支持异步启动和等待启动完成
- 支持多种启动模式（ALL_LAZY、ALL_EAGER）

#### Bean管理
- 支持按名称和类型获取bean
- 支持单例和原型作用域
- 支持bean的懒加载
- 支持bean的延迟方法执行

#### 依赖注入
- 支持构造函数注入和属性注入
- 支持注解驱动的依赖注入
- 支持自动装配到外部对象

#### 高级特性
- 支持并发启动bean
- 支持响应式配置更新
- 支持循环依赖检测和处理
- 支持AOP（基于源代码生成）

## 主要设计特点

### 1. XDef元模型定义

使用XDefinition元模型语言定义IoC领域模型，支持XML配置和注解两种表现形式。

### 2. Spring 1.0语法扩展

基于Spring 1.0的配置语法，补充了SpringBoot的条件装配等概念，所有扩展属性以`ioc:`为前缀。

### 3. 基于源代码生成的AOP

编译期生成AOP派生类，避免运行时动态生成字节码，实现原理与AspectJ类似但更简化。

### 4. 可逆计算原理的分层抽象

支持编译期执行和运行时消除，可输出消除可选条件的最终装配版本。

### 5. 自动配置发现

自动查找虚拟文件系统中`/nop/autoconfig`目录下的配置文件。

### 6. 前缀引导语法

使用前缀语法获取IoC容器内置属性和对象：
- `@bean:id` - 获取指定ID的bean
- `@cfg:config.my-value` - 获取配置值
- `@r-cfg:config.my-dyn-value` - 获取响应式配置值，配置更新时自动刷新

### 7. 响应式配置更新

支持配置更新时自动更新bean属性，或通过`ioc:config`节点传播配置变更。

## 配置示例

### 基本Bean配置

```xml
<beans>
    <bean id="myBean" class="com.example.MyBean">
        <property name="name" value="test" />
        <property name="dependency" ref="myDependency" />
    </bean>
    
    <bean id="myDependency" class="com.example.MyDependency" />
</beans>
```

### 条件装配

```xml
<beans>
    <bean id="conditionalBean" class="com.example.ConditionalBean">
        <ioc:condition>
            <if-property name="feature.enabled" />
            <on-class>com.example.RequiredClass</on-class>
            <on-missing-bean-type>com.example.MissingBeanType</on-missing-bean-type>
        </ioc:condition>
    </bean>
</beans>
```

### AOP配置

```xml
<bean id="transactionalInterceptor" 
      class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
    <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional" 
                 order="1000" />
</bean>
```

### 按注解收集bean

```xml
<bean id="bizObjectManager" class="io.nop.biz.impl.BizObjectManager">
    <property name="bizModelBeans">
        <ioc:collect-beans 
            by-annotation="io.nop.api.core.annotations.biz.BizModel" 
            only-concrete-classes="true" />
    </property>
</bean>
```

### 响应式配置绑定

```xml
<bean id="configurableBean" class="com.example.ConfigurableBean">
    <!-- 静态配置，第一次创建时获取 -->
    <property name="staticValue" value="@cfg:app.static-value" />
    <!-- 响应式配置，配置更新时自动刷新 -->
    <property name="dynamicValue" value="@r-cfg:app.dynamic-value" />
</bean>
```

### 注解驱动配置

```java
@Component
public class MyComponent {
    
    @Inject
    private MyDependency dependency;
    
    @Value("@cfg:app.config-value")
    private String configValue;
    
    // ...
}
```

## 高级特性

### 1. 循环依赖处理

NopIoC支持循环依赖检测和处理，可通过配置`nop.ioc.bean-depends-graph.allow-cycle`控制是否允许循环依赖，也可使用`@IgnoreDepends`注解或`ioc:ignore-depends`属性打破循环依赖。

### 2. 并发启动

支持并发启动bean，提高容器启动效率：

```xml
<beans ioc:concurrent-start="true">
    <!-- 并发启动的bean配置 -->
</beans>
```

### 3. 延迟方法执行

支持bean初始化后延迟执行方法：

```xml
<bean id="delayedBean" class="com.example.DelayedBean">
    <ioc:delayed-method name="initAsync" delay="1000" />
</bean>
```

### 4. 自动装配

支持自动装配到外部对象：

```java
MyObject obj = new MyObject();
beanContainer.autowireBean(obj);
```

## 单元测试支持

NopIoC与JUnit5集成，通过`@NopTestConfig`注解控制IoC容器初始化过程，支持：
- 测试用内存数据库替换
- 自动配置控制
- 测试专用配置引入
- 测试用例中通过`@Inject`注入bean

```java
@NopTestConfig(autoScan = true)
public class MyTest {
    
    @Inject
    private MyService myService;
    
    @Test
    public void testMyService() {
        // 测试代码
    }
}
```

## 问题诊断

### 1. 循环依赖检测

NopIoC会自动检测循环依赖，并在启动时抛出异常，可通过配置`nop.ioc.bean-depends-graph.allow-cycle=true`允许循环依赖。

### 2. 查看Bean依赖图

可通过配置`nop.ioc.bean-depends-graph.enabled=true`生成Bean依赖图，便于分析依赖关系。

### 3. 调试日志

启用DEBUG级别日志，可查看容器启动过程和Bean创建详情。

## 代码结构

核心代码位于`nop-core-framework/nop-ioc`模块，主要包括：

- `io.nop.ioc.api` - 核心接口定义
- `io.nop.ioc.impl` - 容器实现
- `io.nop.ioc.loader` - bean定义加载器
- `io.nop.ioc.support` - 辅助工具类

## 最佳实践

1. **优先使用注解配置**：对于简单的Bean配置，优先使用注解方式，减少XML配置量。

2. **合理使用条件装配**：根据环境和配置条件动态装配Bean，提高系统的灵活性和可扩展性。

3. **避免过度依赖注入**：只注入必要的依赖，避免创建复杂的依赖链。

4. **使用响应式配置**：对于经常变化的配置，使用`@r-cfg`前缀获取响应式配置，自动更新Bean属性。

5. **合理使用AOP**：只在必要时使用AOP，避免过度使用导致系统复杂性增加。

6. **编写测试用例**：使用`@NopTestConfig`编写单元测试，确保Bean配置和依赖关系正确。

## 总结

NopIoC是一个轻量级、高效、灵活的依赖注入容器，具有以下优势：

- 兼容Spring的配置语法，学习成本低
- 基于源代码生成的AOP，性能高效
- 支持响应式配置更新，便于动态调整系统行为
- 基于可逆计算原理，支持编译期优化
- 提供丰富的高级特性，满足复杂应用场景

通过合理使用NopIoC，可以提高系统的模块化程度，降低组件之间的耦合度，便于系统的维护和扩展。