# Compile-Time Code Generation

The Nop platform's AOP mechanism relies on compile-time code generation, not runtime dynamic byte code generation. Therefore, it requires that certain classes be explicitly declared as participating in the AOP mechanism during compilation, enabling automatic identification of classes using these annotations and generation of corresponding AOP proxy classes.

Note: **AOP proxy classes must be generated first** to ensure that NopIoC's AOP configuration becomes effective.


## Registration of Annotations Supporting AOP

In the virtual file system's `/nop/aop/` directory, add a file with the suffix `annotations`, listing all annotations participating in the AOP handling. For example, in the `nop-api-core` project, this would be located at `/_vfs/nop/aop/nop-api-core.annotations`.

Example annotations:
- `io.nop.api.core.annotations.cache.Cache`
- `io.nop.api.core.annotations.cache.CacheEvicts`
- `io.nop.api.core.annotations.orm.SingleSession`
- `io.nop.api.core.annotations.txn.TccMethod`
- `io.nop.api.core.annotations.txn.TccTransactional`
- `io.nop.api.core.annotations.Transactional`


## AOP Class Generator

The `GenAopProxy` tool scans the specified project's `target/classes` and `target/test-classes` directories, generating AOP proxy classes for each Java class found. The generated code is output to the `target/generated-sources` and `target/generated-test-sources` directories.

Example usage:
```javascript
File projectDir = ...;
new GenAopProxy().execute(projectDir, generateForTestFile);
```

You can also call `GenAopProxy` in a test directory's `XXXCodeGen.java` file.

Example Java class:
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


## Generating AOP Proxies Using Maven

In your `pom.xml`, add the `exec-maven-plugin` configuration to enable compile-time generation of AOP proxies. This can be inherited from a parent POM like `nop-entropy`.

Example Maven configuration:
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

In the `beans.xml` file, configure interceptors by declaring them and their priorities. After parsing these configurations, all applicable interceptors will be checked against container-bound beans, with their class attributes replaced by corresponding AOP proxy classes.

```xml
    <bean id="nopTransactionalMethodInterceptor" ioc:default="true"
          class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
        <constructor-arg index="0" ref="nopTransactionTemplate"/>
        <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
                      order="#{ApiConstants.INTERCEPTOR_PRIORITY_TRANSACTIONAL}"/>
    </bean>
```
