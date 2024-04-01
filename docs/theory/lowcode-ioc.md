# 如果重写SpringBoot，我们会做哪些不同的选择？

SpringBoot是在Spring框架基础上的一次巨大的进步，它提出了动态自动装配的概念，摒弃了繁琐的XML配置，充分利用Java语言内置的注解和ServiceLoader机制，极大的减少了一般业务开发中所需要做出的配置决策的数量，重塑了Java应用程序的开发和部署工作。但是发展至今，SpringBoot也已经日渐老迈，历史上不同时刻所做的设计产生的不良影响不断的累积，使得它在应对性能优化、构建原生应用等新的挑战时困难重重。

如果我们完全从零开始重新编写SpringBoot，那么我们会明确定义哪些核心问题由底层框架来负责解决？针对这些问题我们会提出什么样的解决方案？这些解决方案与SpringBoot目前的做法又有哪些本质上的差异？Nop平台中的依赖注入容器NopIoC是基于可逆计算原理从零开始实现的一个模型驱动的依赖注入容器，它通过大约5000行代码，实现了我们所用到的SpringBoot的所有动态自动装配机制和AOP拦截机制，并且实现了GraalVM集成，可以很容易的编译为native镜像。在本文中，我将结合NopIoC的实现代码，谈一谈在可逆计算理论视角下对IoC容器设计原理的所作的一些分析。

## 一. SpringBoot所解决的核心问题

SpringBoot是逐渐发展起来的，所以它要解决哪些问题并不是一次性明确定义清楚的。早期它的目标很单纯，那就是描述式的对象装配。

### 1.1 POJO的描述式装配

作为EJB(Enterprise Java Bean，Sun公司所推行的企业级对象标准)的反叛者，Spring的初心是服务于大众化的POJO(Plain Old Java Object)
，主打所谓轻量级框架的概念。这里的轻量级不仅仅是指Spring的代码实现比较简单直接，更重要的是业务对象可以轻装上阵。我们在业务对象中只需要编写普通的get/set属性方法，\*\*
不需要具有任何高深的、专有的Spring框架的知识（所谓的非侵入性），就可以实现我们的业务\*\*
。同时在运行时，我们只需要补充一个不言自明的、描述式的beans.xml装配文件，就可以实现灵活的对象装配。在运行时原则上也不受Spring框架的束缚，我们可以选择其他装配技术实现对象装配，我们甚至可以自行编写一个Spring的优化版本，读取beans.xml配置文件并执行相关装配逻辑。

Spring 1.0中定义的XML装配格式是一种完备的对象装配DSL。它**定义了对象装配所需要的最基本的原语集合，任何复杂的对象装配过程都可以通过这个DSL进行描述**。例如，以下的例子描述了两个相互依赖的bean的装配逻辑，

```xml
<bean id="a" class="test.MyObjectA" init-method="init" >
   <property name="b" ref="b" />
   <property name="strValue" value="xxx" />
</bean>

<bean id="b" classs="test.MyObjectB">
   <property name="a" ref="a" />
</bean>
```

它们等价于如下java代码

```java
a = new MyObjectA();
scope.put("a",a);
b = new MyObjectB();
scope.put("b",b);

a.setB(scope.get("a"));
a.setStrValue("xxx");
b.setA(scope.get("a"));

a.init();
... 使用对象
a.destroy(); // 容器关闭时负责销毁所有已创建的对象
```

任何对象装配过程分解到最小的原子动作，无非是创建对象、设置属性、调用初始化方法等，因此原先写在Java代码中的对象装配过程可以用beans.xml来描述。

这里我们注意到，Bean容器本身在bean的创建过程中起到了一个**非平凡的协调作用**
。a和b之间相互依赖，需要先设置a和b的属性，然后再调用a和b上的初始化方法，在此过程中必须有一个外部scope环境来暂存临时创建的对象，并提供一种获取临时引用的机制。

Spring 1.0中只区分了bean是否是singleton（不是singleton就是prototype)，但很快人们就意识到了scope是一个需要被明确识别出来的概念，spring
2\.0中为此引入了自定义scope的扩展点。借助于scope，我们就可以把bean的依赖管理扩展到更多的动态环境中，例如Android的Activity（bean仅存在于activity运行期间），后台的批处理任务（bean仅存在于step运行期间）等。

### 1.2 @Autowired 自动依赖注入

描述式依赖注入最重要的价值在于，它提供了一种**向系统中延迟注入信息**
的机制。正向依赖是对象a拥有装配/获取关联对象b的全部知识，它拥有全部信息或者从外部环境中拉取（pull）信息到自身，而依赖注入（控制反转）是由环境推送（push）信息到对象a中。描述式依赖注入则是将push信息的时刻延迟到最后一刻：直到对象被使用之前。在这最后的时刻，我们\*\*
拥有运行时全部对象的相关信息，并且不再需要对对象的使用场景、使用目的做出预测\*\*，可以选择最符合我们实际需求的实现方式。

Spring
1\.0中的配置文件拥有装配相关的全部信息，装配方式非常灵活，但是我们并不总是需要这样一种完全的灵活性。在编译期我们已经知道所依赖对象的部分信息：对象的接口类型。在一定的scope范围内，很多时候只有唯一的一个对象具有指定的接口类型。通过`@Autowired`
注解，我们可以充分利用对象持有的这部分信息，根据它自动注入依赖对象，而不需要在bean.xml文件中再次手工定义对象之间的依赖关系。

在Spring 2.5中引入的`@Autowired`
注解的作用其实类似于Java语言中的import关键字。Import关键字引入外部定义的Java类，我们还需要调用构造函数、设置相关成员变量、调用初始化函数之后，才能得到一个可以正常对外服务的对象。

```java
// 声明依赖类
import test.MyObject;

// 声明依赖对象
@Autowired
MyObject a;
```

通过  `@Autowired`注解，我们可以直接导入一个处于激活状态、可以立刻被使用的Java对象，而不是一个静态的用于创建对象的模板（类可以看作是创建对象的模板）。

`@Autowired`注解只能标注在类的成员变量或者函数方法上，本质上是因为Java语言的限制。我们可以设想这样一种程序语法，它允许我们在任何临时变量处都直接注入依赖对象。

```java
public void myMethod(@Inject MyObject b){
    if(b.value > 3){
        @Inject MyObject a;
    }
}
```

我们也可以选择提供一个以类型作为参数，返回实现了该类型的对象的inject函数。例如

```java
const a = inject(MyObject);
或者
const a = inject<MyObject>(); // 如果语言内置的元编程机制可以读取泛型信息
```

目前前端框架vue3.0中的provide/inject函数就是这样的一种解决方案。

### 1.4 AOP拦截

依赖注入与AOP（Aspect Oriented Programming）的结合是水到渠成的一种自然结果。**依赖注入的本质是引入外部环境的作用（IoC容器是拥有全局知识、维护全局规则的环境对象）**
。我们所依赖和使用的永远不是裸对象，而是浸润在环境中，会被环境规则所增强的包裹对象。

> 在我们的物理世界中，所有的基本粒子，如夸克、电子本身是无质量的，但是它们与无处不在的希格斯粒子场发生相互作用，电子的运动总是会拖曳周围的希格斯粒子一起运动，从而使得我们观测到的电子总是具有质量的。

我们对环境作用的认知并不是一步到位的。在组件技术发展的早期，微软的COM组件技术是市场中占垄断地位的事实标准。当时特意强调的一个设计要点是，一旦从全局Registry中获取到依赖对象指针后，我们就直接和该对象直接交互，从而完全摆脱对全局环境的依赖，这样可以实现最高的性能。但是随着微软DCOM(
分布式COM)技术的发展，环境的持续性作用的重要性逐渐被识别出来。到了今天的云原生环境中，无处不在的服务网格使得所有服务对象的交互实际上都被间接化了。**对象是在网格（类似电磁场）中发生相互交互的**。

AOP是在程序内部对原始的裸对象进行增强的一种标准化手段。

```
Enhanced Object = Naked Object + Environment(Interceptors)
```

所以当依赖注入容器本身已经具有全局环境管理的能力的情况下，当我们向对象a中注入对象b时，如果后续交互时仍然需要使用一部分环境信息，则容器可以把这部分信息和原始的对象b打包在一起，通过AOP技术生成一个增强对象，然后再注入到对象a中。

这里有个有趣的问题。既然AOP增强是将部分环境信息与特定的对象绑定，那么对于可以根据全局知识直接获取到的环境信息，我们完全没有必要对每个对象单独进行AOP增强。比如Java后台开发中常见的Controller对象，一般情况下需要对所有修改操作标注`@Transactional`
注解，表示这个方法需要在事务环境中执行。但是**如果我们统一采用GraphQL接口协议，并定义全局规则：所有的mutation操作都在事务环境中执行，那么我们就不需要再对每个独立的对象进行Transactional增强**
，从而可以减少无谓的调用，提升程序的性能。

### 1.5 @ComponentScan 动态收集Bean的定义

对于任何一个具有一定复杂度的结构，我们必然需要设计一个分解机制，将它拆解为多个可独立识别、存放、管理的子部分，然后我们再通过某种合成机制将这些子部分组装在一起。

Spring 1.0中提出了一个内置的import语法，可以将一个复杂的beans.xml文件拆分成多个子文件。

```xml
<beans> 
   <import resource="base.beans.xml" />
   <import resource="ext.beans.xml" />
   <bean profile="dev">
      <import resource="dev.beans.xml" />
   </bean>
</beans>
```

import语法的设计比较粗糙，它的语义本质上是一种include，即相当于把外部beans.xml文件中的内容拷贝粘贴过来。如果我们多次import同一个文件，则会导致重复引入同一个bean的定义，会抛出异常BeanDefinitionOverrideException。

ComponentScan是一种更加灵活的解决方案。首先它具有**幂等性**，即多次扫描同一个包并不会导致bean重复注册，在语义上更接近程序语言中的import语义。第二，**利用已有的包结构**
作为收集的基本单位，可以灵活选择哪些包或者类需要收集。如果我们要用XML来实现如此灵活的组织，则需要为每个包都创建一个xml文件。

### 1.6 @Conditional 条件装配

Spring 1.0虽然提供了完备的装配原语，但是它并没有定义如何在装配中容纳更多的可变性。基于Spring1.0的机制，为了适应业务变化，我们唯一能做的就是手工调整beans.xml配置文件，这导致配置文件总是不断被修改，难以被复用。

Spring 从4.0开始提供`@Conditional`注解来实现条件化装配，并最终演变为SpringBoot中的`@ConditionalOnBean`、`@ConditionalOnProperty`
等具有明确领域语义的条件判断注解。基于这些条件注解，很多在编译期可以预测到的可变性被明确定义出来，常用的配置组合也可以作为缺省配置被固化下来。

没有条件注解的情况下，我们只能定义唯一的一个固化的装配过程。而借助于条件注解，我们可以**事先定义可能存在的多种装配过程，并缺省提供一个最常见的装配选择**。

> 编程所面向的总是可行的多重世界，而不仅仅是当前的确定性的世界

如果从可逆计算的角度去理解，我们可以认为Spring 1.0所提供的是从零开始构建的装配模型，而**SpringBoot所提供的是一个面向差量的装配模型**
，我们只需要增加一些相对于缺省配置的差量描述即可，从而极大降低了业务开发所需要执行的配置工作量。

### 1.7 @EnableAutoConfiguration 多入口的自动化配置

Spring
1\.0提供的是一个单一入口的静态配置方案，即我们读取固定位置处的beans.xml配置文件，然后分析该文件，递归读取它所包含的子配置文件。SpringBoot所提供的是多入口的动态配置方案，即我们每引入一个依赖模块，都自动引入了它所对应的入口配置类，这些配置类的作用相当于是动态生成配置文件，引入相关的bean。

```
Config = Registrar(ScanClassesA) + Registrar(ScanClassesB) + ...
```

多入口叠加动态扫描注册Bean使得SpringBoot极大简化了应用层缺省情况下的配置，但是它也引入了新的复杂性。

在Spring
1\.0的语法下，bean的解析和注册按照明确的XML描述顺序来执行，执行结果是确定性的，出现问题时诊断也相对容易。而在多入口配置的情况下，扫描得到的bean配置会被动态合并在一起，它们的合并规则和合并结果是隐式的、不显然的。有时，看似无关紧要的包的顺序调整就会导致执行结果的不同，在IDE中的执行结果可能与运行时打包部署也存在微妙的差异。一旦我们偏离缺省配置，就很容易观察到多个模块合并运行时存在的配置混乱的问题，而且一般情况下如果不是非常熟悉源码层面的实现细节，将很难定位问题。

> 在日常开发中，经常可以观察到新手在引入新的模块或者调整缺省配置后会花费大量的时间用于诊断SpringBoot的自动配置不起作用的问题。

解决这个问题的最佳实践是避免多个包中定义同样的bean，避免bean之间发生复杂的依赖关系。本质上说，我们希望多个动态配置进行合并的时候**满足交换律**
，即无论按照什么顺序进行识别和处理都不影响最后的结果，而这个要求本来正是描述式编程所对外承诺的。

### 1.8 嵌入表达式和响应式配置

在Spring 1.0中我们可以通过placeholder机制从XML文件中抽取出配置参数，例如

```xml
<bean id="dataSource" ...>
  <property name="jdbcUrl" value="${spring.datasource.jdbc-url}" />
</bean>
```

placeholder可以看作是将application.properties配置参数文件和applicationContext.xml对象装配文件粘结在一起的一种适配机制。placeholder可以看作是一种适配表达式，它从application.properties文件中抽取参数信息，然后应用到当前的bean配置上。在概念层面上，它的工作内容为

```
bean.jdbcUrl = props.get('spring.datsource.jdbc-url')
```

在Spring后续的发展中，这一机制沿着两个方向进行了扩展。一是表达式的概念得到了增强。在Spring 3.0之后，我们可以使用真正的Expression Language来编写适配表达式。例如

```xml
<bean id="readStep">
   <property name="filePath" value="#{jobParameters['filePath']}" />
   <property name="testValue" value="#{T(java.lang.Math).PI}" />
</bean>
```

EL表达式的执行上下文中，不仅存在application.properties中定义的配置变量，同时也包含了bean容器中定义的所有的bean，并且我们可以通过类名来直接访问所有的Java类。

第二个扩展方向是动态配置变量集合的概念得到了增强。在Spring 3.1之后，引入了所谓的Environment概念，借助这一概念SpringCloud将简陋的配置变量集合扩展为可以支持响应式更新的分布式配置中心。

```java
@RefreshScope
@Service
public class MyService{

    @Value("${app.user-local-cache}")
    boolean useLocalCache;
}
```

标记了RefreshScope的bean当配置发生变化的时候会被重新创建，从而应用新的配置。

## 二. SpringBoot的设计缺陷

### 2.1 背离了描述式编程的基本原则

Spring虽然是靠描述式装配起家的，但是从Spring 2.0开始它就与描述式编程渐行渐远。Spring 1.0仅能通过简陋的DTD语法来约束XML文件的格式，为此在2.0中它引入了更为强大的XML
Schema来提供更加严格的格式定义，但是同时它又引入了自定义名字空间机制。自定义名字空间通过[NamespaceHandler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/xml/NamespaceHandler.html#:~:text=public%20interface%20NamespaceHandler%20Base%20interface%20used%20by%20the,custom%20namespaces%20in%20a%20Spring%20XML%20configuration%20file.)
接口负责解析并处理，表面上看起来似乎是提供了描述式的自定义XML标签，但背后的实现却是一段段具有强顺序依赖的命令式代码。

1. 我们在实现自定义标签的时候无法通过简单的声明式的方式来复用已有功能，增加新的自定义标签必须实现新的NamespaceHandlr，并增加一系列相关注册配置，成本很高，而且很难保证不同名字空间的标签能够正确的嵌套使用。

2. 虽然Spring 1.0提供了完备的对象装配原语，Spring 2.0的自定义标签却无法化归为Spring 1.0的标签定义。也就是说，如果一个软件包是提供了Spring 2.0语法的配置支持，我们无法保证一定可以使用Spring
   1\.0的语法对该软件包进行配置。这导致自定义配置标签越多，系统中语义不明的部分越多。我们难以编制一个通用的Spring配置分析工具。

3. 

SpringBoot的设计和发展过程中缺少一个明确定义的语义模型，大量注解的背后是非常复杂的、与全局状态纠缠在一起的、缺少协同规则的命令式识别和处理代码。在实际应用中，bean的装配结果与命令式代码的执行顺序存在非常微妙的关系。这一点当我们需要把SpringBoot的配置代码迁移到别的IoC容器的时候显得特别明显。

以Quarkus迁移为例。[Quarkus](https://quarkus.io/)是专为 GraalVM 和 OpenJDK HotSpot 量身定制的开源 Kubernetes 原生 JavaTM 框架。类似于Go语言，它通过AOT(
Ahead Of Time)
编译技术，支持直接编译为单一可执行文件，在运行时摆脱JDK运行环境，提高启动速度，并降低内存消耗。作为一个新生的框架，Quarkus肯定很眼馋Spring庞大的社区资源，为此它试图提供一个Spring到Quarkus的适配迁移机制。Quarkus面临的第一个问题是Spring的各类注解类并没有被剥离到一个单独的注解API包中，而是和各类实现代码混杂在一起。Quarkus不得不把部分注解类单独抽取到一个jar包中，并通过hack的方式实现对Spring依赖包的替换。Quarkus面临的第二个问题是Spring的装配过程无法通过编译期分析来进行事前处理，这样实际上就无法像Quarkus内置的CDI容器一样通过编译期代码生成来实现对象装配。Quarkus的选择是只支持Spring的部分语义明确的注解，各类通过命令式代码实现的加载扫描代码一概忽略，这直接导致它的Spring兼容迁移功能完全就是一个鸡肋，仅能用于对外宣传，而无法成为真正的迁移工具。

从非技术的角度上说，SpringBoot的非描述式设计可能是有意为之。因为Spring框架作为商业产品运营之后，Spring背后的团队肯定是希望社区在这个产品上的沉没成本越来越高，从而构造出有利于自己的迁移壁垒。所谓兼容特性容易，兼容bug很难，社区的产品之所以能够在Spring容器中顺利运行，是投入了大量人工调试成本，在容忍了各类bug和设计冲突之后的产物，当我们要迁移到新的框架或者平台上时，除非完整搬迁Spring容器的实现代码，否则我们又如何保证一个bug恰好被触发，而我们的bug防护代码又恰好起作用呢？

### 2.2 历史上的成功所带来的兼容性负担

Spring的历史非常长，从2004年3月发布1.0版本至今，已经有接近二十年时间。在整整一代人的发展历程中，它见证了各类编程模式、编程技术的发展，并在各个时期都成功提供了相应的封装支持，这些成功的经验沉淀在Spring框架底层成为了今天看起来莫名其妙的冗余设计。

以SpringMVC为例，当前端发送的JSON格式数据字段名不匹配的时候，后端服务可能并没有报出明确的JSON解析错误，而是原因不明的其他错误。因为SpringMVC支持多种传参机制，当一种机制解析不成功的时候，它会尝试下一种解析方式。而在JSON广泛流行之前，为了传递复杂结构，存在着各种脑洞大开的[花式编码方案](https://www.npmjs.com/package/qs)
，比如在url中规定`?a[]=1&a[]=2`这种方式来传递数组，通过`foo[bar]=baz`这种方式来传递Map等。现在在网上搜索SpringMVC，可以发现大把的''SpringMVC传递复杂对象的X种方式"这样的文章。

如果采用此时此刻的最佳实践，可以说SpringMVC中百分之九十以上的代码都是完全不必要的。例如，如果我们采用GraphQL接口标准，则后台只需要识别唯一的一个`/graphql`
链接，这个链接只需要接收POST方法请求，并且只接收JSON格式的Request Body，返回的Response Body也固定为JSON格式。各类编码方案，包括JAX-RS标准中定义的URL匹配规范都是不必要的冗余设计。

### 2.3 不重复造轮子的定位所带来的限制

一直以来，Spring都是以所谓封装者的面目示人，号称自己不重复生产轮子，只是大自然的打包工，对业界最成熟、最优秀的实现技术进行打包和润色。这种定位使得Spring在处理很多问题的时候处于比较尴尬的境地，是自行提出一套接口标准完全屏蔽底层的实现，还是需要保留底层实现技术的各类细节，仅仅是把它们按照SpringBoot的配置风格进行包装？底层技术来源和风格的不统一，也给Spring的上层封装工作带来很多困难。

以Spring经典的声明式事务封装为例，为了统一普通JDBC操作和Hibernate操作的事务处理，Spring内部定义了SessionHolder和ConnectionHolder等多个线程上下文对象，并通过TransactionSynchronization同步来同步去。但是如果Hibernate能够和Spring进行协同设计，那它直接调用JdbcTemplate完成数据库访问就可以了，没必要再增加额外的封装。事实上，在Hibernate5.3版本以后，Hibernate明确引入了[BeanContainer](https://docs.jboss.org/hibernate/stable/core/javadocs/org/hibernate/resource/beans/container/spi/BeanContainer.html)
接口，开始明确假定Ioc容器的存在，这使得基于Spring所做的一些传统封装模式变得[失去了意义](https://www.matez.de/index.php/2019/04/05/connecting-spring-and-hibernate-though-beancontainer)
。

有的时候，待封装的技术相比于我们的需求而言本身就是一个过分复杂的概念体系，例如AspectJ所提供的AOP机制。AspectJ提供非常强大的切面拦截技术，可以按照正则字符串语法匹配包名、方法名，可以识别复杂的嵌套调用关系等。

```java
    @Pointcut("execution(public * *(..))")
    private void anyPublicOperation() {}

    @Pointcut("within(com.xyz.someapp.trading..*)")
    private void inTrading() {}

    @Pointcut("anyPublicOperation() && inTrading()")
    private void tradingOperation() {}
```

但是，在日常业务开发中，唯一得到大规模应用的只有一种切点定义方式：拦截具有指定注解的Java方法。Spring在AOP的概念体系上总是希望向AspectJ技术靠拢，因此凭空增加了很多复杂度。在Nop平台中为了给依赖注入容器引入AOP支持，只增加了不到1000行左右的代码，此时封装额外的AOP框架的成本已经远高于直接实现。

Spring最近几年的设计风格已经有所转向。例如，SpringCloud最早是基于Netflix OSS代码库的基础上封装得来。后来随着Netflix OSS代码库的发展逐渐落后于时代，SpringCloud也逐步开始了自研的历程。

## 三. NopIoC 描述式的IoC容器

NopIoC是Nop平台中使用的轻量级依赖注入容器。最开始我的目标是先定义一个兼容Spring和Quarkus的BeanContainer接口，但很快就发现Spring的原生应用支持模块spring-native非常不成熟，而Quarkus依赖注入容器的组织能力远逊于SpringBoot，一些SpringBoot中非常简单的配置在Quarkus中难以实现，并且Quarkus预编译的做法导致运行时调试变得困难，所以我最终决定还是实现一个IoC容器来作为Nop平台的缺省BeanContainer实现。

### 3.1 XDef元模型定义

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

### 3.2  Spring 1.0语法的自然扩展

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

### 3.10 单元测试支持

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

关于Nop平台中自动化支持的详细介绍，可以参见我此前的文章

[低代码平台中的自动化测试](https://zhuanlan.zhihu.com/p/569315603)

## 总结

Nop平台是基于可逆计算原理从零开始构建的低代码开发平台。NopIoC是Nop平台的一个可选组件。Nop平台中的所有其他模块对NopIoC都没有直接依赖，原则上只要实现IBeanContainer接口都可以替换NopIoC的实现。但是，NopIoC相比于Spring/Quarkus等框架的实现，它具有一些独特的设计，特别是基于可逆计算原理所实现的分层抽象，使得它能够在保持简单结构的前提下提供非常丰富、复杂的功能特性集。

关于可逆计算理论的详细介绍，可以参见我此前的文章

[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)

[可逆计算的技术实现](https://zhuanlan.zhihu.com/p/163852896)

[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)
