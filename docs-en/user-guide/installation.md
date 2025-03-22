# Installation Guide

## Environment Preparation
- JDK 17+ (not supported in JDK 8)
- Maven 3.9.3+ 
- Git

```shell
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar
```

**Note:**  
- Compilation and execution require JDK 17 or higher.
- In PowerShell, parameters must be enclosed in quotes.
- Some JDK versions (e.g., jdk:17.0.9-graal) may cause IndexOutOfBoundsException errors during compilation. If issues arise, try using OpenJDK first.

```shell
mvn clean install "-DskipTests" "-Dquarkus.package.type=uber-jar"
```

The `quarkus.package.type` parameter is recognized by the Quarkus framework. Setting it to "uber-jar" will package projects like `nop-quarkus-demo` into a single JAR containing all dependent classes.

You can run the project directly using:
```bash
java -jar XXX-runner.jar
```

## PowerShell Encoding Issue

Set the encoding of PowerShell to UTF-8:

```powershell
$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
```

For Quarkus projects, upgrading to version 3.0 is recommended. Using older Maven versions with `nop-auth-app` and similar modules may fail. We suggest:
- Upgrading Maven to 3.9.3 or using the `mvnw` command in the project directory (which will download Maven 3.9.3).

* nop-idea-plugin
The `nop-idea-plugin` is an IDEA plugin that requires Gradle for compilation.

```shell
cd nop-idea-plugin
gradlew buildPlugin
```

**Note:**  
- IDEA currently does not support higher versions of Gradle.
- `gradlew` will automatically download the required Gradle version (currently 7.5.1).
- To improve Gradle download speed, modify the `gradle-wrapper.properties` file to use:
```bash
distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-7.5.1-bin.zip
```

The compiled plugin is stored in the `build/distributions` directory. For more details, see [Plugin Installation and Usage](../dev-guide/ide/idea.md).

## Usage Guide

* Demonstration Included
- Uses H2 In-Memory Database
- Can be started directly for testing

```shell
cd nop-demo/nop-quarkus-demo/target
java -Dquarkus.profile=dev -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

**Note:**  
- If the `quarkus.profile` parameter is not specified, it will run in production mode.
- In production mode, `application.yaml` must be configured with database settings. By default, it uses the local MySQL database.

* Access Links:
  - [http://localhost:8080](http://localhost:8080)
    - Username: nop
    - Password: 123

* Debugging in IDEA
- You can debug `nop-quarks-demo`'s `QuarksDemoMain` class.
- Quarkus provides the following debugging tools during development:
  - [http://localhost:8080/q/dev](http://localhost:8080/q/dev)
  - [http://localhost:8080/q/graphql-ui](http://localhost:8080/q/graphql-ui)

The GraphQL UI tool allows you to view all backend service definitions and parameters.
