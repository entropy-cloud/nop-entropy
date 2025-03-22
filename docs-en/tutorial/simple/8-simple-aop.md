# Nop Basics: Minimal AOP Implementation

Video explanation: [Bilibili](https://www.bilibili.com/video/BV1xS411P7xA/)

AOP（Aspect Oriented Programming）is essentially a very simple concept (pointing + local logic modification), but its implementation is often not simple. As one of the core features of the Spring framework, Spring AOP introduces complex pointcut definition syntax. However, in practical usage scenarios, the only widely used method for defining pointcuts is through annotations.

On the other hand, Spring AOP uses dynamic code generation or dynamic proxy classes, which can affect application startup speed and runtime performance, and are not friendly to GraalVM native compilation. The Nop platform implemented an extremely simple AOP mechanism using code generation, achieving optimal performance while fully meeting our general application requirements, with all code less than 2000 lines.

The NopIoC container natively supports AOP, which also simplifies the implementation of the NopIoC container, allowing it to avoid the need for multi-level object caching that Spring would require for supporting AOP (NopIoC only uses a single-level object cache).

This article will briefly introduce the implementation principles and usage methods of NopAOP. For detailed information, refer to [aop.md](../../dev-guide/ioc/aop.md).

## I. Registering Annotation Classes for AOP Cut Points

The NopAOP does not use dynamic bytecode generation technology but directly generates proxy class source code, which means it needs to know at compile time which classes require proxy generation. The specific approach is to scan all class methods and identify those that use special annotations, then generate proxy classes for these classes.

For example, in the `nop-quarkus-demo` project, we added a `SendEmail` annotation class and a corresponding file in the `/_vfs/nop/aop/nop-quarkus-demo.annotations` directory.

```
io.nop.demo.annotations.SendEmail
```

* AOP application requires a stable positioning coordinate system.
* Complex pointcuts are essentially unstable positioning coordinates. For example, if a pointcut matches function names' prefixes or suffixes, then when developers write code without following specific naming conventions, the compiler will not report any errors, but AOP will fail to intercept the corresponding methods.
* Using custom annotations provides a stable coordinate for AOP in the program structure. Compared to method names that may vary due to different scenarios, this ensures stability.
* The Nop platform generates AOP proxy classes by scanning all files under the `/nop/aop/` directory with suffixes `annotations`.

## II. Generating AOP Proxy Classes

In Java's ecosystem, the standard technology for generating code during compilation is APT (Annotation Processing Tool), which is a feature of the Java compiler used for processing annotations at compile time. APT can read and analyze source code annotations, generate new code (such as classes, interfaces, enums) or additional files (like XML configuration files). However, using APT requires knowledge of APT-related tools and their usage, and generated code cannot be independent of the Java compilation process. Therefore, NopAOP does not use APT technology but provides a `GenAopProxy` helper class that uses Java reflection to obtain method information and generate AOP proxy classes.

In the root pom file of the `nop-entropy` project, `exec-maven-plugin` is predefined for executing Maven plugins, with the `aop` phase defined. This phase will handle AOP code generation tasks.


```xml
<!-- nop-entropy's pom file -->
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

In the module that needs to generate proxy classes, you can inherit from nop-entropy's root pom and introduce exec-maven-plugin.

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

In the example application, we add `@SendEmail` annotation to `DemoBizModel`'s `testMethod1` method.

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

Then execute either `mvn package` or `mvn install` command, and exec-maven-plugin will automatically generate a corresponding `DemoBizModel__aop` class.

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
            $$testMethod1_0 = io.nop.core.reflect.impl.MethodModelBuilder.from(
                io.nop.demo.biz.DemoBizModel.class,
                io.nop.demo.biz.DemoBizModel.class.getDeclaredMethod("testMethod1", 
                    io.nop.demo.biz.MyRequest.class)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DemoBizModel__aop() {
        super();
    }

    @Override
    public void testMethod1(io.nop.demo.biz.MyRequest arg0) {
        if (this.$$interceptors == null || this.$$interceptors.length == 0) {
            super.testMethod1(arg0);
            return;
        }

        io.nop.core.reflect.aop.CallableMethodInvocation $$methodInv = new io.nop.core.reflect.aop.CallableMethodInvocation(
            this,
            new java.lang.Object[]{arg0},
            $$testMethod1_0,
            () -> {
                super.testMethod1(arg0);
                return null;
            }
        );

        io.nop.core.reflect.aop.AopMethodInvocation $$inv = new io.nop.core.reflect.aop.AopMethodInvocation($$methodInv, this.$$interceptors);
        try {
            $$inv.proceed();
        } catch (java.lang.Exception e) {
            throw io.nop.api.core.exceptions.NopException.adapt(e);
        }
    }
}
```

* Generated proxy class uses `@AopProxy` annotation to quickly obtain the actual used annotations
* NokAOP does not use Java's built-in Method class but instead uses IFunctionModel interface, allowing the interception mechanism to work outside of Java's reflection system.
* Add IMethodInterceptor implementation


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

## Four. Registering Interceptor and Pointcut in the IoC Container

```xml
<bean id="sendEmailInterceptor" class="io.nop.demo.interceptors.SendEmailInterceptor">
  <ioc:pointcut annotations="io.nop.demo.annotations.SendEmail"/>
</bean>
```

* NopIoC extended `<ioc:pointcut>` node from Spring1.0 syntax, representing the AOP pointcut defined by the Interceptor.
* During bean container initialization, it checks if any applicable Interceptor exists in the container and replaces the original class constructor with the proxy class constructor.
