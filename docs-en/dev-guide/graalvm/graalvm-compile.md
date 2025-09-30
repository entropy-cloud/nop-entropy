# Native Compilation

## Debug Configuration
Configure the debug phase in bootstrap.yaml

```yaml
nop:
  profile: dev

"%dev":
  nop.codegen.trace.enabled: true
```
Enable the application during the debug phase and perform business operations; classes used via reflection will be output to the `reflect-config.json` file when the application is shut down.
In addition, a `nop-vfs-index.txt` file will be generated automatically, which contains all file paths in the virtual file system. GraalVM does not support classpath scanning or resource scanning; without the help of index files, file traversal and lookup cannot be implemented.

## Third-Party Library Adaptation

In modules such as nop-commons and nop-auth-core, `reflect-config.json` configurations have been added for third-party libraries used, such as caffeine and jsonwebtoken.

## Version Compatibility
https://www.graalvm.org/release-notes/JDK_21/

https://github.com/graalvm/graalvm-ce-builds/releases Download GraalVM 21.0.2

The pom file in the `quarkus-bom` module defines the version of GraalVM JS that Quarkus depends on; then find the corresponding GraalVM version on the releases page above.

Truffle languages and other components version 23.1.2 are designed for use with GraalVM for JDK 21.0.2

GraalJS version 24.0.2 is designed for use with Oracle GraalVM for JDK 22.0.2 or GraalVM Community Edition for JDK 22.0.2,

GraalJS version 23.1.2 is designed for use with Oracle GraalVM for JDK 21.0.2 or GraalVM Community Edition for JDK 21.0.2,

## Class Initialization


## reflect-config.json
Configurations under the following directory will be collected automatically: `src/main/resources/META-INF/native-image/<group-id>/<artifact-id>`

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

## Configuration Notes
Specify the following options to enable the debugger based on Chrome DevTools, the sampling profiler, the tracing profiler, and the memory profiler, respectively:

--inspect
--cpusampler
--cputracer
--memsampler

## Solon Framework

### 1. Add AOT support in the pom file and include the solon-logging-logback dependency,

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
            <!-- Use reachability metadata provided by GraalVM so many third-party libraries can be built directly into executables -->
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

### 2. Configure logback in app.yml. When using a logback.xml configuration, reflection support is missing and logs cannot be output properly.

```yaml
solon.logging.appender:
  console:
    level: DEBUG # adjust level as needed
    enable: true # whether to enable
  cloud:
    level: INFO
    enable: true

# Example of logger-level configuration
solon.logging.logger:
  "root": # default logger configuration
    level: DEBUG
  "com.zaxxer.hikari":
    level: WARN
```

Since the information required for GraalVM compilation has already been supplied in files such as `reflect-config.json`, in general you do not need to use Solon's RuntimeNativeRegistrar mechanism.

### 3. Compilation
Remove the nop.profile configuration from bootstrap.yaml, then run the following command

```
mvn native:compile -DskipTests -Pnative
```

## Issue Diagnosis
1. Error: cannot find `/nop/schema/register-model.xdef`
You must first start the application in debug mode and configure `"nop.codegen.trace.enabled: true` and `nop.debug:true`" in `bootstrap.yaml`, which will automatically generate `nop-vfs-index.txt` to solve the problem where class scanning cannot be performed after native compilation.
* As long as you start in IDEA's debug mode, Quarkus will automatically set `quarkus.profile=dev`, and QuarkusIntegration in the Nop platform will automatically force `nop.debug=true`.
* After starting the program, you should exercise the application via the UI so that the reflective calls that need to be recorded are actually invoked. When you stop the app in IDEA, GraalVM configuration files will be automatically generated in the `src/main/resources` directory.

2. GraalVM compilation error
Ensure that the GraalVM version matches the Quarkus version referenced by the program. Currently, Quarkus 3.14.4 uses GraalVM JS 23.1.2, which corresponds to `GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)`
<!-- SOURCE_MD5:a48ddd75b6eb87bc9671c29f294ecfdf-->
