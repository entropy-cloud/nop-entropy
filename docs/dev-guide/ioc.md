# NopIoC容器

NopIoC是Nop平台中使用的轻量级依赖注入容器。最开始我的目标是先定义一个兼容Spring和Quarkus的BeanContainer接口，但很快就发现Spring的原生应用支持模块spring-native非常不成熟，而Quarkus依赖注入容器的组织能力远逊于SpringBoot，一些SpringBoot中非常简单的配置在Quarkus中难以实现，并且Quarkus预编译的做法导致运行时调试变得困难，所以我最终决定还是实现一个IoC容器来作为Nop平台的缺省BeanContainer实现。

## 3.1 XDef元模型定义

**一个描述式的IoC容器一定具有一个语义定义明确的领域模型**，这个模型可以被看作是一种DSL（Domain Specific
Language）。IoC容器本身是这个DSL的解释器和执行器。如果我们把领域模型对象序列化为文本保存下来，那就成为一个IoC专用的模型文件，例如spring的beans.xml配置文件。Java注解可以看作是这个领域模型的另外一种表现形式，例如Hibernate的模型定义可以用JPA注解来表达，也可以用hbm配置文件来表达。

**定义良好的模型可以由通用的元模型（Meta-Model）来描述**。Spring 1.0的XML语法具有XML
Schema定义，但是SpringBoot中提供的条件装配等能力就缺少对应的XML语法了，所以最终导致的结果是SpringBoot没有一个清晰定义的领域模型。

XML Schema格式能力虽然比DTD强大很多，但是非常冗长，而且它的设计目标只是用于约束一般性的XML数据文件，对于约束具有执行语义的DSL模型，它也是力有未逮。

NopIoC采用了XDefinition元模型语言来定义自己的领域模型。XDef语言是Nop平台中用于取代XML Schema和JSON Schema的元模型定义语言，它专为DSL而设计，在信息表达能力上远比XML Schema和JSON
Schema要直观、高效。可以直接根据XDef定义得到可执行的领域模型，也可以根据XDef定义来生成代码，生成IDE提示信息，甚至生成可视化设计器页面等。

下面我们可以直观的对比一下用xsd和xdef所分别定义的Spring 1.0配置格式

https://www.springframework.org/schema/beans/spring-beans-4.3.xsd

[nop-xdefs/src/main/resources/\_vfs/nop/schema/beans.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef)

关于XDef元模型定义语言，我后续会写一篇专门的文章来介绍相关的技术细节。

## 3.2  Spring 1.0语法的自然扩展

NopIoC以Spring 1.0的配置语法为基础（NopIoC可以直接解析Spring 1.0的配置文件），为其补充了SpringBoot引入的条件装配等概念。所有扩展属性都以`ioc:`为前缀，用于和Spring内置的属性区分开来。

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

上面的配置对应于SpringBoot的配置

```java
@ConditionalOnProperty("xxx.enabled")
@ConditionalOnMissingBean({DataSource.class})
@ConditionalOnClass({MyObject.class})
@Bean("xx.yy")
public XXX getXx(){
}
```

### 3.3 基于源代码生成的AOP

在NopIoC中使用AOP非常简单，只要配置interceptor对应的pointcut

```xml
 <bean id="nopTransactionalMethodInterceptor"
      class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
     <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
          order="1000"/>
 </bean>
```

以上配置表示将扫描容器中所有的bean（ioc:aop属性没有设置为false），如果发现它的某个方法上具有`@Transactional`注解，则应用该interceptor。
具体的实现原理为:

1. 在`resources/_vfs/nop/aop/{模块名称}.annotations`文件中注册需要被AOP识别的注解类。

2. 工程编译的时候会通过maven插件扫描target/classes目录下的类，检查类的方法上是否具有AOP可识别的注解，如果有，则为该类生成一个\_\_aop派生类，用于插入AOP
   interceptor。这样打包好的jar包中就包含了AOP相关的生成代码，在使用AOP机制的时候就不需要动态生成字节码了。这里的实现原理其实和AspectJ类似，只是操作过程要简化很多。代码生成器的具体实现参见

   [nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java)

3. IoC容器在创建bean的时候，如果发现存在可以应用到该类上的interceptor，则使用\_\_aop派生类来新建对象，并插入interceptor。

具体生成文件示例如下：

[docs/ref/AuditServiceImpl\_\_aop.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/ref/AuditServiceImpl__aop.java)

### 3.4 基于可逆计算原理实现的分层抽象

NopIoC利用可逆计算所定义的编译期生成技术来提供类似Spring 2.0的自定义标签抽象。

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

Nop平台中所有的DSL模型都支持`x:gen-extends`机制，它在编译期运行，会输出XML节点，然后再和外部的XML节点执行DeltaMerge合并算法，合成为最终的XML配置节点。这相当于是我们使用XPL模板语言来编写Spring
2\.0的自定义标签，然后在编译期执行该标签输出Spring 1.0语法的配置内容。NopIoC引擎只需要支持最基础的Spring 1.0的语法即可免费获得自定义标签抽象。

在Nop平台中分层抽象的概念被贯穿始终，使得我们可以将尽可能多的操作放到编译期执行，减少运行时的复杂度，并提升运行时的性能。例如，执行完所有条件判断和按类型扫描之后，NopIoC会输出一个消除了所有可选条件的最终装配版本到\_dump目录下，这个版本可以由Spring
1\.0的执行引擎负责执行。在此基础上，我们可以编写一个翻译器，将Spring 1.0语法的XML配置翻译为注解配置，从而适配到其他的IoC运行时，或者翻译为Java创建代码，完全消除IoC的运行时。

### 3.5 生成Java Proxy

NopIoC内置了一个ioc:proxy属性，可以直接根据当前bean，创建实现了指定接口的Proxy对象。

```xml
<bean id="myBean" class="xx.MyInvocationHandler"
      ioc:type="xx.MyInterface" ioc:proxy="true" />
```

采用上面的配置，实际返回的myBean对象是实现了MyInterface接口的代理对象。

### 3.6 按注解或者类型扫描

NopIoC内置了根据注解来收集bean的能力。例如

```xml
 <bean id="nopBizObjectManager" class="io.nop.biz.impl.BizObjectManager">
     <property name="bizModelBeans">
        <ioc:collect-beans
           by-annotation="io.nop.api.core.annotations.biz.BizModel"
           only-concrete-classes="true"/>
     </property>
 </bean>
```

上面的配置表示在容器中查找所有具有`@BizModel`注解的类，忽略所有抽象类和接口，只考虑那些具体的实现类。

### 3.7 前缀引导语法

在Spring
1\.0的设计中，为了获取IoC容器内置的一些属性和对象，对象必须实现一些特定的接口，例如BeanNameAware，ApplicationContextAware等，在NopIoC中我们通过前缀引导语法可以获取到对应的值。例如

```xml
<bean id="xx">
   <property name="id" value="@bean:id" />
   <property name="container" value="@bean:container" />
   <property name="refB" value="@inject-ref:objB" />
  <!-- 等价于 -->
   <property name="refB" value-ref="objB" />
</bean>
```

前缀引导语法是在Nop平台中广泛使用的，非常通用的一种可扩展语法设计，详细介绍可以参见我的文章

[DSL分层语法设计与前缀引导语法](https://zhuanlan.zhihu.com/p/548314138)

### 3.8 响应式配置更新

NopIoC内置了响应式配置的知识。我们可以为单个属性指定响应式配置绑定

```xml
<bean id="xx">
  <!--  @cfg表示第一次创建bean的时候获取配置值，但是不会进行响应式更 -->
  <property name="configValue" value="@cfg:config.my-value" />
  <!-- @r-cfg表示当配置值发生改变的时候自动更新bean的属性值-->
   <property name="dynValue" value="@r-cfg:config.my-dyn-value" />
</bean>
```

也可以类似Spring的`@ConfigurationProperties`注解，通过配置前缀将对象上的所有属性与配置项绑定

```xml
<!-- ioc:auto-refresh表示当配置发生变换的时候会自动更新bean的属性 -->
<bean id="xx" ioc:config-prefix="app.my-config" ioc:auto-refresh="true"
    class="xxx.MyConfiguration" />
```

NopIoc还规定了一个特殊的语法节点ioc:config

```xml
<ioc:config id="xxConfig" ioc:config-prefix="app.my-config" ... />

<bean id="xx" ioc:on-config-refresh="refreshConfig" >
  <property name="config" ref="xxConfig" />
</bean>
```

当配置更新的时候， xxConfig会被自动更新，同时这种更新过程会传播到所有使用了xxConfig的bean，触发它们的refreshConfig函数。

### 3.9 自动配置发现

NopIoC提供了类似SpringBoot的AutoConfiguration的机制。NopIoC在初始化的时候会自动查找虚拟文件系统中`/nop/autoconfig`
目录下所有后缀为beans的文件，并自动装载其中定义的beans.xml文件。例如[/nop/autoconfig/nop-auth-core.beans](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-core/src/main/resources/_vfs/nop/autoconfig/nop-auth-core.beans)
文件中的内容为/nop/auth/beans/auth-core-defaults.beans.xml。一般情况下beans文件的文件名为对应的java模块名，这样当多个模块被打包为一个fat-jar的时候不会出现文件冲突。

与SpringBoot不同的是，NopIoC不是一边加载配置文件一边执行bean的注册过程。NopIoC只会在收集到所有bean的定义之后统一执行一次条件判断逻辑。因此，在NopIoC中bean定义的先后顺序原则上并不影响IoC容器动态计算的结果。

### 3.10 按照类型获取bean

`BeanContainer.getBeanByType(beanClass)`可以按照类型查找到对应的bean。如果存在多个bean都具有某个类型的情况，可以设置bean的primary属性为true，则会优先使用这个bean。
或者设置其他bean的autowire-candidate属性为false，表示不作为候选bean。

### 3.11 单元测试支持

NopIoC与JUnit5进行了集成。在单元测试中，我们主要通过`@NopTestConfig`注解来控制IoC容器的初始化过程。

```java
public @interface NopTestConfig {
    /**
     * 是否强制设置nop.datasource.jdbc-url为h2内存数据库
     */
    boolean localDb() default false;

    /**
     * 使用随机生成的服务端口
     */
    boolean randomPort() default false;

    /**
     * 缺省使用lazy模式来执行单元测试
     */
    BeanContainerStartMode beanContainerStartMode() default BeanContainerStartMode.ALL_LAZY;

    /**
     * 是否自动加载/nop/auto-config/目录下的xxx.beans配置
     */
    boolean enableAutoConfig() default true;

    String autoConfigPattern() default "";

    String autoConfigSkipPattern() default "";

    /**
     * 为单元测试指定的beans配置文件
     */
    String testBeansFile() default "";

    /**
     * 为单元测试指定的config配置文件
     */
    String testConfigFile() default "";
}


@NopTestConfig
public class MyTestCase extends JunitBaseTestCase{
    // 可以通过@Inject来注入Bean容器中管理
    @Inject
    IGraphQLEngine engine;
}
```

NopIoC的JUnit支持提供了如下功能：

1. 控制是否使用测试用的内存数据库来替换配置文件中指定的数据库连接

2. 控制是否启用autoconfig，控制使用哪些模块的autoconfig

3. 控制是否引入测试专用的beans配置

4. 控制是否引入测试专用的properties配置

5. 支持在测试用例中通过@Inject来注入bean

关于Nop平台中自动化支持的详细介绍，可以参见 [autotest.md](autotest.md)

### 3.11 问题诊断

#### 检查循环依赖
缺省情况下NopIoC允许bean的定义出现循环依赖，比如A注入B，B注入C，C又注入A等。
如果设置 `nop.ioc.bean-depends-graph.allow-cycle` 为false，则启动时会检查bean的引用关系，发现循环依赖会报错。

可以通过`@IgnoreDepends`注解或者ioc配置中的`ioc:ignore-depends`属性来打破循环

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
