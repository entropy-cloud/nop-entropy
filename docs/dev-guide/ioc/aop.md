# 编译期代码生成

Nop平台的AOP机制依赖于编译期代码生成，并不是在运行期动态生成字节码。

注意：**必须先生成AOP包装类，然后NopIoC中的aop配置才能起作用**

## AOP包装类生成器

GenAopProxy扫描指定工程下的target/classes目录以及target/test-classes目录，对其中的每个Java类生成aop包装类，
生成代码输出到target/generated-sources目录和target/generated-test-sources目录。

```javascript
  File projectDir = ...;
  new GenAopProxy().execute(projectDir, generateForTestFile);
```

在test目录下的XXXCodeGen.java中可以增加GenAopProxy调用。

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

## 通过maven工具生成AOP包装类

在pom.xml中增加exec-maven-plugin配置，为了简单一般从nop-entropy的pom.xml继承，其中已经为exec-maven-plugin配置了aop代理生成功能。

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

## NopIoC配置

在beans.xml中配置Interceptor，声明它可以应用到哪些注解以及它的应用优先级。
NopIoC解析bean配置之后会检查所有的interceptor可以应用到容器中的哪些bean中，并将bean的class属性替换为对应的AOP包装类。

```xml
    <bean id="nopTransactionalMethodInterceptor" ioc:default="true"
          class="io.nop.dao.txn.interceptor.TransactionalMethodInterceptor">
        <constructor-arg index="0" ref="nopTransactionTemplate"/>
        <ioc:pointcut annotations="io.nop.api.core.annotations.txn.Transactional"
                      order="#{ApiConstants.INTERCEPTOR_PRIORITY_TRANSACTIONAL}"/>
    </bean>
```
