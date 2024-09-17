# 原生编译

## 调试配置
调试阶段在bootstrap.yaml中配置

```yaml
nop:
  profile: dev

"%dev":
  nop.codegen.trace.enabled: true
```
在调试阶段启用应用，并进行业务操作，用到的反射类会在关闭应用时输出到`reflect-config.json`文件中。
此外，还会自动生成`nop-vfs-index.txt`文件，其中包含了所有虚拟文件系统中的文件路径。Graalvm不支持类扫描和资源文件扫描，如果没有索引文件帮助，则无法实现文件遍历和查找。

## 第三方库适配

在nop-commons, nop-auth-core等模块中，对于用到的第三方库如caffeine,jsonwebtoken等增加了`reflect-config.json`配置

## 版本兼容性
https://www.graalvm.org/release-notes/JDK_21/

https://github.com/graalvm/graalvm-ce-builds/releases 下载graalvm 21.0.2

`quarkus-bom`模块的pom文件中定义了quarkus所依赖的graalvm js的版本，然后再在上面的releases页面查找对应graalvm的版本。

Truffle languages and other components version 23.1.2 are designed for use with GraalVM for JDK 21.0.2

GraalJS version 24.0.2 is designed for use with Oracle GraalVM for JDK 22.0.2 or GraalVM Community Edition for JDK 22.0.2,

GraalJS version 23.1.2 is designed for use with Oracle GraalVM for JDK 21.0.2 or GraalVM Community Edition for JDK 21.0.2,

## 类初始化


## reflect-config.json
会自动收集如下目录中的配置 `src/main/resources/META-INF/native-image/<group-id>/<artifact-id>`

```
[
  {
    "name" : "com.acme.MyClass",
    "allDeclaredConstructors" : true,
    "allPublicConstructors" : true,
    "allDeclaredMethods" : true,
    "allPublicMethods" : true,
    "allDeclaredFields" : true,
    "allPublicFields" : true
  }
]
```

## 配置说明
指定以下选项以分别启用基于 Chrome DevTools 调试器、采样探查器、跟踪探查器和内存分析器的调试器：

--inspect
--cpusampler
--cputracer
--memsampler

## Solon框架

### 1. 在pom文件中引入aot支持，引入solon-logging-logback依赖，

```xml
<project>
  <dependencies>
    <dependency>
      <groupId>org.noear</groupId>
      <artifactId>solon.aot</artifactId>
    </dependency>

    <dependency>
      <groupId>org.noear</groupId>
      <artifactId>solon.logging.logback</artifactId>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.noear</groupId>
        <artifactId>solon-maven-plugin</artifactId>
        <version>${solon.version}</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals>
              <goal>repackage</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <profiles>
    <profile>
      <id>native</id>
      <build>
        <plugins>
          <plugin>
            <groupId>org.noear</groupId>
            <artifactId>solon-maven-plugin</artifactId>
            <version>${solon.version}</version>
            <executions>
              <execution>
                <id>process-aot</id>
                <goals>
                  <goal>process-aot</goal>
                </goals>
              </execution>
            </executions>

            <dependencies>
              <dependency>
                <groupId>org.codehaus.plexus</groupId>
                <artifactId>plexus-utils</artifactId>
                <version>3.5.1</version>
              </dependency>
            </dependencies>
          </plugin>
          <plugin>
            <groupId>org.graalvm.buildtools</groupId>
            <artifactId>native-maven-plugin</artifactId>
            <version>0.9.28</version>
            <!-- 使用graalvm提供的可达性元数据，很多第三方库就直接可以构建成可执行文件了 -->
            <configuration>
              <metadataRepository>
                <enabled>true</enabled>
              </metadataRepository>
              <!--                            <buildArgs combine.children="append">-->
              <!--                                <buildArg>-H:+AddAllCharsets</buildArg>-->
              <!--                            </buildArgs>-->
            </configuration>
            <executions>
              <execution>
                <id>add-reachability-metadata</id>
                <goals>
                  <goal>add-reachability-metadata</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
    </profile>
  </profiles>
</project>
```

### 2. 在app.yml中配置logback。使用logback.xml配置时缺少反射支持，无法正常输入日志

```yaml
solon.logging.appender:
  console:
    level: DEBUG #可根据需要调整级别
    enable: true #是否启用
  cloud:
    level: INFO
    enable: true

# 记录器级别的配置示例
solon.logging.logger:
  "root": #默认记录器配置
    level: DEBUG
  "com.zaxxer.hikari":
    level: WARN
```

因为已经在`reflect-config.json`等文件中补充了graalvm编译所需的信息，所以一般情况下不需要使用solon提供的RuntimeNativeRegistrar机制。

### 3. 编译
将bootstrap.yaml中的nop.profile配置去除，然后执行如下指令

```
mvn native:compile -DskipTests -Pnative
```

## 问题诊断
1. 报错找不到`/nop/schema/register-model.xdef`
必须先以调试模式启动应用，并在`bootstrap.yaml`中配置`"nop.codegen.trace.enabled: true`和`nop.debug:true`, 这样才会自动生成`nop-vfs-index.txt`，解决native编译后无法执行类扫描的问题。
* 只要以IDEA的调试模式启动，quarkus就会自动设置`quarkus.profile=dev`, Nop平台的QuarkusIntegration中就会自动强制设置`nop.debug=true`
* 启动程序后应该在界面上使用一下应用，从而让需要被记录的反射调用都能够被调用到。在IDEA中关闭应用时会自动生成graalvm配置文件到`src/main/resources`目录下

2. graalm编译报错
需要确保graalvm版本和程序中引用的quarks版本一致。目前quarkus3.14.4使用的是graalvm js 23.1.2，对应于graalvm版本为`GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)`
