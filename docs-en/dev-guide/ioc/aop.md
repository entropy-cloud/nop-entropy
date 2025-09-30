# Compile-time Code Generation

The Nop platform’s AOP mechanism relies on compile-time code generation, rather than dynamically generating bytecode at runtime. Therefore, you need to predeclare which classes will participate in the AOP mechanism. During compilation, beans that use these annotations are automatically identified, and AOP proxy classes are generated for them.

Note: You must generate the AOP wrapper classes first; only then will the AOP configuration in NopIoC take effect.

## Register Annotations that Support the AOP Mechanism
Add a file with the suffix annotations under the /nop/aop/ directory in the virtual file system, listing all annotation classes that participate in AOP processing. For example, in the nop-api-core project: /_vfs/nop/aop/nop-api-core.annotations.

```
io.nop.api.core.annotations.cache.Cache
io.nop.api.core.annotations.cache.CacheEvicts
io.nop.api.core.annotations.orm.SingleSession
io.nop.api.core.annotations.txn.TccMethod
io.nop.api.core.annotations.txn.TccTransactional
io.nop.api.core.annotations.txn.Transactional
```

## AOP Wrapper Class Generator

GenAopProxy scans the target/classes and target/test-classes directories under the specified project and generates an AOP wrapper class for each Java class therein. The generated code is output to target/generated-sources and target/generated-test-sources.

```javascript
  File projectDir = ...;
  new GenAopProxy().execute(projectDir, generateForTestFile);
```

You can add a GenAopProxy invocation in XXXCodeGen.java under the test directory.

```java
public class AuthCodeGen {
    public static void main(String[] args) {
        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(AuthCodeGen.class);
            XCodeGenerator.runPrecompile(projectDir, "/", false);

            new GenAopProxy().execute(projectDir, false);
            new GenAopProxy().execute(projectDir, true);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
```

## Generate AOP Wrapper Classes via Maven

Add an exec-maven-plugin configuration in pom.xml. For simplicity, it is generally inherited from nop-entropy’s pom.xml, which already configures AOP proxy generation for exec-maven-plugin.

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
```

## NopIoC Configuration

Configure an Interceptor in beans.xml, declaring which annotations it applies to and its application priority. After parsing the bean configuration, NopIoC will check which interceptors can be applied to beans in the container and replace the bean’s class attribute with the corresponding AOP wrapper class.

```xml
    <bean id="nopTransactionalMethodInterceptor" ioc:default="true"
          class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
        <constructor-arg index="0" ref="nopTransactionTemplate"/>
        <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
                      order="#{ApiConstants.INTERCEPTOR_PRIORITY_TRANSACTIONAL}"/>
    </bean>
```

<!-- SOURCE_MD5:e41502e3fe8d54799b3393bc103eaec4-->
