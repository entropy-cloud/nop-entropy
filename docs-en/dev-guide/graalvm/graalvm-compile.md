# Native Compilation

## Debug Configuration
The debug phase is configured in `bootstrap.yaml`.

```yaml
nop:
  profile: dev

"%dev":
  nop.codegen.trace.enabled: true
```
In the debug phase, the application is launched, and reflection classes are output to the `reflect-config.json` file during application shutdown.
Additionally, a `nop-vfs-index.txt` file will be generated in the virtual file system, containing all file paths. GraalVM does not support class scanning or resource scanning if there is no index file available.

## Third-party Library Adaptation
In modules like `nop-commons`, `nop-auth-core`, etc., configurations for third-party libraries such as caffeine and jsonwebtoken are added to the `reflect-config.json` file.

## Version Compatibility
- [GraalVM JDK 21.0.2 Release Notes](https://www.graalvm.org/release-notes/JDK_21/)
- Download GraalVM 21.0.2 from [GraalVM CE Builds](https://github.com/graalvm/graalvm-ce-builds/releases)

The `quarkus-bom` module's pom file defines the version of GraalVM JS used by Quarkus, and then searches for the corresponding GraalVM version in the releases page.

## Truffle Languages and Components
- Truffle languages and other components (version 23.1.2) are designed for use with GraalVM for JDK 21.0.2.
- GraalJS version 24.0.2 is designed for use with Oracle GraalVM for JDK 22.0.2 or GraalVM Community Edition for JDK 22.0.2.
- GraalJS version 23.1.2 is designed for use with Oracle GraalVM for JDK 21.0.2 or GraalVM Community Edition for JDK 21.0.2.

## Class Initialization
The `reflect-config.json` file automatically collects configurations from the following directory:
`src/main/resources/META-INF/native-image/<group-id>/<artifact-id>`

```json
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

## Configuration Explanation
The following options are specified to enable debuggers such as Chrome DevTools:
- `--inspect`
- `--cpusampler`
- `--cputracer`
- `--memsampler`

## Solon Framework

### 1. Include AOT Support in the POM File
Add AOT support to the POM file and include dependencies like `solon-logging-logback`.


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
            <!-- Using graalvm's reachability metadata, many third-party libraries can be built into executable files -->
            <configuration>
              <metadataRepository>
                <enabled>true</enabled>
              </metadataRepository>
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

### 2. Configure Logback in app.yml

When using `logback.xml`, reflection support is missing, preventing normal logging input.

```yaml
solon.logging.appender:
  console:
    level: DEBUG # Adjust based on need
    enable: true # Whether to enable
  cloud:
    level: INFO
    enable: true

# Logging Level Configuration Example
solon.logging.logger:
  "root": # Default logger configuration
    level: DEBUG
  "com.zaxxer.hikari":
    level: WARN
```

Since additional information has been added to `reflect-config.json` for GraalVM compilation, the Solon-provided RuntimeNativeRegistrar mechanism is generally unnecessary.

### 3. Compile

Remove `nop.profile` from `bootstrap.yaml` and execute the following command:

```bash
mvn native:compile -DskipTests -Pnative
```

## Issue Diagnosis
1. Error: Cannot find `/nop/schema/register-model.xdef`
   - Start the application in debug mode.
   - Configure `"nop.codegen.trace.enabled": true` and `nop.debug:true` in `bootstrap.yaml`.
   - This will generate `nop-vfs-index.txt`, resolving native compilation issues after class scanning.
   - In IDEA, disable the application when debugging to allow automatic generation of GraalVM configuration files in the `src/main/resources` directory.

2. Graalm Compilation Error
   - Ensure the GraalVM version matches the Quarkus version used by the project (e.g., Quarkus 3.14.4 uses GraalVM JS 23.1.2, corresponding to GraalVM CE 21.0.2+13.1 (build 21.0.2+13-jvmci-23.1-b30)).
