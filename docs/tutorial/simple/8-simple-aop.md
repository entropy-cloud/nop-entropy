# Nop入门: 极简AOP实现

讲解视频：[哔哩哔哩_bilibili](https://www.bilibili.com/video/BV1xS411P7xA/)

AOP（Aspect Oriented Programming)本质上是一个非常简单的概念(定位+局部逻辑修改)，但是它的实现却往往并不简单。作为Spring框架的一个核心功能，SpringAOP中引入的异常复杂的切点定义语法。但是在实际使用场景中，唯一得到广泛应用的切点（Pointcut）定义方法就是使用注解(Annotation)。
另外一方面，SpringAOP会使用动态代码生成或者动态代理类，这会影响到应用的启动速度或者运行时性能，对于GraalVM原生编译也不友好。Nop平台采用代码生成的方式实现了一个最简单的AOP机制，具有最优的性能且完全满足我们一般应用的需求，全部代码仅有不到2000行。

NopIoC容器内置了对AOP的支持，这也简化了NopIoC容器的实现，使得它不必像Spring那样为了支持AOP被迫引入多级对象缓存（NopIoC仅使用了一级对象缓存）。

本文将简单介绍一下NopAOP的实现原理和使用方法，详细介绍参见[aop.md](../../dev-guide/ioc/aop.md)

## 一. 注册用于AOP切点的注解类

NopAOP并不使用动态字节码生成技术，而是直接生成代理类的源码，因此它需要在编译的时候知道需要为哪些类生成代理类。具体的做法就是扫描所有类的方法，识别哪些方法使用了特殊的注解，并为这些类生成代理类。

例如在`nop-quarkus-demo`项目中，我们增加了一个SendEmail的注解类和`/_vfs/nop/aop/nop-quarkus-demo.annotations`文件。

```
io.nop.demo.annotations.SendEmail
```

* AOP的应用需要程序的结构空间存在一种稳定的定位坐标体系。

* 复杂的Pointcut相当于是通过动态计算得到的一种不稳定的定位坐标。例如如果pointcut是匹配函数名的前缀或者后缀，那么当程序员不按照特定的命名规范编写代码时，编译器不会报任何错误，但是AOP却拦截不到对应的方法。

* 使用自定义的注解相当于是在程序的结构空间提供一种专用于AOP的稳定坐标。对比之下，方法名可能因为各种情况而改变，无法保证稳定。

* Nop平台在生成AOP代理类时会扫描`/nop/aop/`目录下的所有后缀名为annotations的文件。一般情况下这个文件名会与所在的模块名同名，避免不同模块定义的文件名发生冲突。

## 二. 生成AOP代理类

Java生态中用于编译期代码生成的标准技术方案是APT（Annotation Processing Tool），它是 Java编译器的一个功能，用于在编译期间执行对源代码中注解（Annotations）的处理。APT 可以读取和分析源代码中的注解，并生成新的代码（如类、接口、枚举等）或者生成额外的文件（如XML、配置文件等）。但是使用这个技术需要了解一定的APT的相关知识，所编写的代码还不能独立于Java编译过程来使用，因此NopAOP没有使用APT技术，而是直接提供了一个GenAopProxy帮助类使用Java反射技术获取类的方法信息，然后生成AOP代理类。

在nop-entropy项目的根pom文件中预定义了exec-maven-plugin插件的参数，设置了aop执行阶段，它会负责执行AOP代码生成工作。

```xml
<!-- nop-entropy的pom文件 -->
<pom>
  ...
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.0.0</version>
        <executions>
          <execution>
            <id>aop</id>
            <phase>compile</phase>
            <goals>
              <goal>java</goal>
            </goals>
            <configuration>
              <arguments>
                <argument>${project.basedir}</argument>
                <argument>aop</argument>
              </arguments>
            </configuration>
          </execution>

        </executions>
        <configuration>
          <classpathScope>compile</classpathScope>
          <includePluginDependencies>true</includePluginDependencies>
          <includeProjectDependencies>true</includeProjectDependencies>
          <addResourcesToClasspath>true</addResourcesToClasspath>
          <mainClass>io.nop.codegen.task.CodeGenTask</mainClass>
          <cleanupDaemonThreads>false</cleanupDaemonThreads>
        </configuration>
      </plugin>
    </plugins>
  </pluginManagement>
</pom>
```

在需要生成代理类的模块中，我们可以从nop-entropy的根pom继承，并引入exec-maven-plugin插件即可。

```xml
<pom>
  <parent>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-entropy</artifactId>
    <version>2.0.0-SNAPSHOT</version>
  </parent>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</pom>
```

在示例应用中，我们为DemoBizModel的testMethod1方法增加`@SendEmail`注解。

```java
@BizModel("Demo")
public class DemoBizModel {

    @BizQuery
    @SendEmail
    public void testMethod1(@RequestBean MyRequest request) {
        System.out.println("doSomething");
    }
}
```

然后执行mvn package或者mvn install指令，通过exec-maven-plugin插件会自动生成一个对应的DemoBizModel__aop类。

```java
package io.nop.demo.biz;

@io.nop.api.core.annotations.aop.AopProxy({io.nop.demo.annotations.SendEmail.class})
public class DemoBizModel__aop extends io.nop.demo.biz.DemoBizModel implements io.nop.core.reflect.aop.IAopProxy {
  private io.nop.core.reflect.aop.IMethodInterceptor[] $$interceptors;

  @Override
  public void $$aop_interceptors(io.nop.core.reflect.aop.IMethodInterceptor[] interceptors) {
    this.$$interceptors = interceptors;
  }

  private static io.nop.core.reflect.IFunctionModel $$testMethod1_0;

  static {
    try {
      $$testMethod1_0 = io.nop.core.reflect.impl.MethodModelBuilder.from(io.nop.demo.biz.DemoBizModel.class, io.nop.demo.biz.DemoBizModel.class.getDeclaredMethod("testMethod1", io.nop.demo.biz.MyRequest.class));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public DemoBizModel__aop() {
    super();
  }

  @Override
  public void testMethod1(final io.nop.demo.biz.MyRequest arg0) {
    if (this.$$interceptors == null || this.$$interceptors.length == 0) {
      super.testMethod1(arg0);
      return;
    }

    io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(this,
      new java.lang.Object[]{arg0}, $$testMethod1_0, () -> {
      super.testMethod1(arg0);
      return null;
    });

    io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
    try {
      $$inv.proceed();
    } catch (java.lang.Exception e) {
      throw io.nop.api.core.exceptions.NopException.adapt(e);
    }
  }
}
```

* 生成的代理类上通过`@AopProxy`注解来快速获取实际用到的注解类
* NopAOP不使用Java内置的Method类，而是使用IFunctionModel接口，使得在Java反射系统之外也可以复用这些Interceptor

## 三. 增加IMethodInterceptor实现类

IMethodInterceptor接口类似于Spring中使用的MethodInterceptor接口，只是使用IFunctionModel代替Java反射模型。

```java
public class SendEmailInterceptor implements IMethodInterceptor {
  @Override
  public Object invoke(IMethodInvocation inv) throws Exception {

    if (inv.getArguments().length <= 0)
      return inv.proceed();

    Object arg = inv.getArguments()[0];
    if (arg instanceof MyRequest) {
      System.out.println("sendEmail:message=" + ((MyRequest) arg).getMessage());
    }
    return inv.proceed();
  }
}
```

## 四. 在IoC容器中注册Interceptor，并声明Pointcut

```xml
    <bean id="sendEmailInterceptor" class="io.nop.demo.interceptors.SendEmailInterceptor">
        <ioc:pointcut annotations="io.nop.demo.annotations.SendEmail"/>
    </bean>
```

* NopIoC为Spring1.0的语法扩展了`<ioc:pointcut>`节点，通过它表示Interceptor所作用的AOP切点。
* 在bean容器初始化的时候会检查容器中是否有可应用的Interceptor，如果有，则用代理类的Constructor代替原始类的Constructor。
