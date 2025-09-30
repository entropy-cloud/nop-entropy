
# Getting Started with Nop: Minimal AOP Implementation

Walkthrough video: [Bilibili](https://www.bilibili.com/video/BV1xS411P7xA/)

AOP (Aspect-Oriented Programming) is, at its core, a very simple concept (locating + localized logic modification), but its implementations are often anything but simple. As a core feature of the Spring framework, Spring AOP introduces a notoriously complex pointcut definition syntax. However, in real-world usage, the only pointcut definition approach that sees widespread adoption is via annotations. On the other hand, Spring AOP relies on dynamic code generation or dynamic proxies, which can impact application startup time or runtime performance, and is unfriendly to GraalVM native compilation. The Nop platform implements a minimal AOP mechanism via code generation, delivering optimal performance while fully meeting typical application needs, with the entire implementation under 2,000 lines of code.

The NopIoC container has built-in AOP support, which also simplifies its implementation: unlike Spring, it does not need to introduce multi-level object caches to support AOP (NopIoC uses a single-level object cache only).

This article briefly introduces NopAOP’s implementation principles and usage. For a detailed discussion, see [aop.md](../../dev-guide/ioc/aop.md)

## 1. Register Annotation Classes for AOP Pointcuts

NopAOP does not use dynamic bytecode generation; instead, it directly generates proxy class source code. Therefore, it needs to know at compile time which classes should have proxies generated. Concretely, it scans all classes’ methods, identifies which methods use special annotations, and generates proxy classes for those classes.

For example, in the `nop-quarkus-demo` project, we add a SendEmail annotation class and a `/_vfs/nop/aop/nop-quarkus-demo.annotations` file.

```
io.nop.demo.annotations.SendEmail
```

* Applying AOP requires a stable coordinate system within the program’s structural space for locating targets.

* Complex pointcuts are essentially dynamically computed, unstable coordinates. For example, if a pointcut matches a function name’s prefix or suffix, the compiler won’t report errors when developers deviate from naming conventions, but AOP will fail to intercept the intended methods.

* Using custom annotations provides a stable coordinate dedicated to AOP within the program’s structural space. In contrast, method names can change for various reasons and cannot be guaranteed to be stable.

* When generating AOP proxy classes, the Nop platform scans all files under `/nop/aop/` with the `annotations` suffix. Typically, the file name matches the module name to avoid conflicts among files defined by different modules.

## 2. Generate AOP Proxy Classes

In the Java ecosystem, the standard solution for compile-time code generation is APT (Annotation Processing Tool). It’s a feature of the Java compiler used to process annotations during compilation. APT can read and analyze annotations in source code and generate new code (such as classes, interfaces, enums) or additional files (such as XML, configuration files). However, using this technology requires familiarity with APT, and the code you write cannot be used independently of the Java compilation process. Therefore, NopAOP does not use APT; instead, it provides a GenAopProxy helper class that uses Java reflection to obtain method information and then generates AOP proxy classes.

In the root pom of the nop-entropy project, parameters for the exec-maven-plugin are predefined, setting an aop execution phase that takes care of generating AOP code.

```xml
<!-- pom file of nop-entropy -->
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

In modules that need proxy generation, simply inherit from nop-entropy’s root pom and include the exec-maven-plugin.

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

In the sample application, we add the `@SendEmail` annotation to DemoBizModel’s testMethod1 method.

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

Then run mvn package or mvn install; the exec-maven-plugin will automatically generate the corresponding DemoBizModel__aop class.

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

* The generated proxy class uses the `@AopProxy` annotation to quickly retrieve the annotation classes actually in use.
* NopAOP does not use Java’s built-in Method class; it uses the IFunctionModel interface, allowing these interceptors to be reused outside the Java reflection system.

## 3. Implement IMethodInterceptor

The IMethodInterceptor interface is similar to Spring’s MethodInterceptor, but it uses IFunctionModel instead of the Java reflection model.

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

## 4. Register the Interceptor in the IoC Container and Declare the Pointcut

```xml
    <bean id="sendEmailInterceptor" class="io.nop.demo.interceptors.SendEmailInterceptor">
        <ioc:pointcut annotations="io.nop.demo.annotations.SendEmail"/>
    </bean>
```

* NopIoC extends the Spring 1.0 syntax with the `<ioc:pointcut>` element to express the AOP pointcuts an interceptor applies to.
* During bean container initialization, it checks whether applicable interceptors exist; if so, the proxy class’s constructor replaces the original class’s constructor.

<!-- SOURCE_MD5:e9490efb584f112e06f4d6dad8406c57-->
