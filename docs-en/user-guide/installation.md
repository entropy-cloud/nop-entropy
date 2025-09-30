# Installation Guide

Environment prerequisites: JDK 17+, Maven 3.9.3+, Git

```shell
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar
```

Note: **Building and running requires JDK 17 or later; JDK 8 is not supported.** **When executing in PowerShell, wrap the parameters in quotes.**

According to feedback, some JDK versions fail during compilation. For example, jdk:17.0.9-graal may throw an IndexOutOfBoundsException. If you encounter compilation issues, try OpenJDK first.

```
mvn clean install "-DskipTests" "-Dquarkus.package.type=uber-jar"
```

The quarkus.package.type parameter is recognized by the Quarkus framework. Setting it to uber-jar will package projects such as nop-quarkus-demo into a single jar containing all dependency classes. You can run it directly via java -jar XXX-runner.jar.

## Resolving Garbled Characters in PowerShell

You can set PowerShell’s encoding to UTF-8:

```
$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
```

We have upgraded to Quarkus 3.0. Running modules such as nop-auth-app with older Maven versions may fail. It is recommended to upgrade to Maven 3.9.3, or use the mvnw script in the nop-entropy root directory, which will automatically download and use Maven 3.9.3.

* nop-idea-plugin  
  nop-idea-plugin is an IDEA plugin project and must be built with Gradle.

```
cd nop-idea-plugin
gradlew buildPlugin
```

> The current IDEA packaging plugin does not support higher Gradle versions. gradlew will automatically download the required Gradle version; currently it uses 7.5.1  
> To speed up Gradle downloads, you can change in gradle-wrapper.properties to  
> distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-7.5.1-bin.zip

The compiled plugin is located in the build/distributions directory. See [Plugin installation and usage](../dev-guide/ide/idea.md).

## Usage

* The platform includes a demo application using an H2 in-memory database that can be started directly:

```shell
cd nop-demo/nop-quarkus-demo/target
java -Dquarkus.profile=dev -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

> If profile=dev is not specified, it will start in prod mode. In prod mode, you need to configure the database connection in application.yaml; by default it uses the local MySQL database.

* Visit [http://localhost:8080](http://localhost:8080), **Username: nop, Password: 123**

* In IDEA, you can debug-run the QuarksDemoMain class in the nop-quarks-demo project.  
  The Quarkus framework provides the following development-time tools:

> http://localhost:8080/q/dev
> http://localhost:8080/q/graphql-ui

In the GraphQL UI tool, you can view the definitions and parameters of all backend service functions.

<!-- SOURCE_MD5:2cc02a34e37487738e0a2cd5f087dd48-->
