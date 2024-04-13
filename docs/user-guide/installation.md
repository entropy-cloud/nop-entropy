# 安装教程

环境准备： JDK 17+、Maven 3.9.3+、Git

```shell
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar
```

注意: **编译运行需要JDK17以上版本，不支持JDK8**, **在PowerShell中执行的时候需要用引号将参数包裹起来**

据反馈，有些JDK版本编译会报错，如jdk:17.0.9-graal会报错IndexOutOfBound异常，所以如果编译出现问题时可以先尝试一下OpenJDK。

```
mvn clean install "-DskipTests" "-Dquarkus.package.type=uber-jar"
```

quarkus.package.type参数是quarkus框架所识别的一个参数，指定它为uber-jar将会把nop-quarkus-demo等项目打包成一个包含所有依赖类的单一jar包。可以通过java
-jar XXX-runner.jar的方式直接运行。

## PowerShell乱码问题解决

可以将PowerShell的编码设置为UTF8

```
$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
```

目前已经升级到quarkus3.0版本，用低版本maven运行nop-auth-app等模块可能会失败。建议升级到maven
3.9.3版本，或者使用nop-entropy跟目录下的mvnw指令，它会自动下载并使用maven 3.9.3。

* nop-idea-plugin
  nop-idea-plugin是IDEA的插件项目，必须采用Gradle编译。

```
cd nop-idea-plugin
gradlew buildPlugin
```

> 目前使用的idea打包插件不支持高版本gradle。gradlew会自动下载所需的gradle版本，目前使用的是7.5.1
> 如果想加快gradle下载速度，可以gradle-wrapper.properties中换成
> distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-7.5.1-bin.zip

编译出来的插件存放在build/distributions目录下。参见[插件的安装和使用](../dev-guide/ide/idea.md)。

## 使用说明

* 平台内置了一个演示程序，使用H2内存数据库，可以直接启动运行

```shell
cd nop-demo/nop-quarkus-demo/target
java -Dquarkus.profile=dev -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

> 如果不指定profile=dev，则会以prod模式启动。prod模式下需要配置application.yaml中的数据库连接，缺省使用本机的MySQL数据库

* 访问链接 [http://localhost:8080](http://localhost:8080)， **用户名:nop, 密码:123**

* 在IDEA中可以调试运行nop-quarks-demo项目中的QuarksDemoMain类。
  quarkus框架在开发期提供了如下调试工具，

> http://localhost:8080/q/dev
> http://localhost:8080/q/graphql-ui

在graphql-ui工具中可以查看所有后端服务函数的定义和参数。

