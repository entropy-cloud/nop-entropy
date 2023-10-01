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

````yaml

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

````

要开启TRACE级别的日志，必须同时配置 quarkus.log.min-level=TRACE, 否则最多只输出DEBUG级别

## 分包编译
使用了quarkus的IoC注解的bean，在打包成jar包时，必须引入jandex插件，将bean的详细信息记录在jandex索引文件中，否则quarkus只会扫描当前工程中的文件，而无法使用
jar包中的bean。

````
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
````

## 原生编译
需要使用maven 3.9.3以后版本

````
mvnw install -Dnative -DskipTests -Dquarkus.native.container-build=true
````

* windows下中文VC存在问题，配置-H:-CheckToolchain 可以跳过
* 不能使用awt模块下的Font等类
* 不能包含--report-unsupported-elements-at-runtime选项，否则通过Delete注解排除的class仍然会报错
* GraalVM 23.1无法使用quarkus3.3.3进行原生编译，必须升级到3.4.1

## 上传解析
需要引入com.sun.mail依赖，并禁用内置依赖的angus-mail模块，并且不能排除jaxb-provider

````xml
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
````