# 日志配置

## 日志格式

在Quarkus中，日志格式  `%d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n`  表示以下内容：

- %d{yyyy-MM-dd HH:mm:ss,SSS} : 日期和时间的格式，例如 "2022-01-01 12:34:56,789"，其中 SSS 表示毫秒。
- %h : 主机名。
- `%N[%i]` : 线程名称和线程ID。
- %-5p : 日志级别，左对齐并且最多占据5个字符的宽度。
- `[%c{3.}]` : 类名，最多显示3个字符。
- (%t) : 线程上下文。
- %s : 日志消息。
- %e : 异常信息。
- %n : 换行符。

这个日志格式的含义是，每条日志记录将包含日期、时间、主机名、线程名称和ID、日志级别、类名、线程上下文、日志消息和异常信息，并以换行符结束。

## 配置示例

```yaml

quarkus:
  log:
    level: INFO

    category:
      "io.nop":
        level: DEBUG

    file:
      enable: true
      path: log/app-demo.log
      #      # 输出格式
      #      format: %d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n
      #      # Indicates whether to log asynchronously
      async: true
      rotation:
        max-file-size: 100M
        max-backup-index: 300
        file-suffix: .yyyy-MM-dd

```

要开启TRACE级别的日志，必须同时配置 quarkus.log.min-level=TRACE, 否则最多只输出DEBUG级别

## 分包编译

使用了quarkus的IoC注解的bean，在打包成jar包时，必须引入jandex插件，将bean的详细信息记录在jandex索引文件中，否则quarkus只会扫描当前工程中的文件，而无法使用
jar包中的bean。

```
 <plugin>
    <groupId>org.jboss.jandex</groupId>
    <artifactId>jandex-maven-plugin</artifactId>
    <version>1.2.3</version>
    <executions>
        <execution>
            <id>make-index</id>
            <goals>
                <goal>jandex</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## 原生编译

需要使用maven 3.9.3以后版本

```
mvnw install -Dnative -DskipTests -Dquarkus.native.container-build=true
```

* windows下中文VC存在问题，配置-H:-CheckToolchain 可以跳过
* 不能使用awt模块下的Font等类
* 不能包含--report-unsupported-elements-at-runtime选项，否则通过Delete注解排除的class仍然会报错
* GraalVM 23.1无法使用quarkus3.3.3进行原生编译，必须升级到3.4.1

## 上传解析

需要引入com.sun.mail依赖，并禁用内置依赖的angus-mail模块，并且不能排除jaxb-provider

```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-resteasy-multipart</artifactId>
            <exclusions>
                <exclusion>
                    <artifactId>angus-mail</artifactId>
                    <groupId>org.eclipse.angus</groupId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>com.sun.mail</groupId>
            <artifactId>jakarta.mail</artifactId>
        </dependency>
```

## 仅使用Nop平台的后端

如果需要使用NopGraphQL服务，则可以引入nop-quarkus-web-starter模块

```xml

<pom>
    <!--  parent 设置为nop-entropy可以集成缺省的maven plugin，缺省的包管理配置 -->
    <parent>
        <artifactId>nop-entropy</artifactId>
        <groupId>io.github.entropy-cloud</groupId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>

    <dependencies>
        <!-- 引入 Nop平台依赖。因为设置了parent为nop-entropy，这里就不用写具体的包的版本号 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-quarkus-web-orm-starter</artifactId>
        </dependency>

        <!-- 用户、权限管理，这是可选依赖 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-auth-service</artifactId>
        </dependency>

        <!-- 可选依赖，字典表、编码规则表、扩展字段表、全局序号表等 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-sys-service</artifactId>
        </dependency>

        <!-- 需要使用Nop平台生成的AMIS页面的时候才需要依赖xxx-web模块 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-auth-web</artifactId>
        </dependency>

        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-sys-web</artifactId>
        </dependency>

        <!-- 这里包含了nop-chaos项目打包生成的js和css，它提供了前台菜单框架包括登录页面等。如果自己实现前端，可以不依赖这个模块 -->
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-web-site</artifactId>
        </dependency>

    </dependencies>
</pom>
```

## 仅使用NopReport报表引擎

```xml

<dependencies>
    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-quarkus-core-starter</artifactId>
    </dependency>

    <dependency>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-report-core</artifactId>
    </dependency>
</dependencies>
```

## 问题诊断

### 1. quarkus定义的bean找不到
quarkus的Bean定义如果被封装到库模块中，则此模块必须配置`jandex-maven-plugin`

### 2. 前端访问服务返回400，BadRequest
url中不允许包含特殊字符，必须要进行UTF8编码。例如 `http://localhost:8080/r/NopAuthUser__findPage?@selection=items{id}` 会报错，
必须采用如下编码
```
http://localhost:8080/r/NopAuthSite__findPage?%40selection=items%7Bid%7D
```
