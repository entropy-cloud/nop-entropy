[[中文]](README.md) [[Tutorial]](./docs/tutorial/tutorial_en.md) [[开发示例]](./docs/tutorial/tutorial.md) [[Introduction and Q & A Video]](https://www.bilibili.com/video/BV1u84y1w7kX/)

# Nop Platform 2.0

[![](https://img.shields.io/github/stars/entropy-cloud/nop-entropy)](https://github.com/entropy-cloud/nop-entropy/stargazers)
[![](https://gitee.com/canonical-entropy/nop-entropy/badge/star.svg?theme=white)](https://gitee.com/canonical-entropy/nop-entropy/stargazers)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/entropy-cloud/nop-entropy)

## Introduction

[Reversible Computing: A Theory of Next Generation Software Construction](https://zhuanlan.zhihu.com/p/64004026)

**Nop is nOt Programming (non-programming) **

Nop Platform 2.0 is a next-generation low-code development platform built from scratch based on the principles of reversible computation, adopting a language-oriented programming paradigm. It includes a suite of fully designed engines such as a GraphQL engine, ORM engine, workflow engine, reporting engine, rule engine, and batch processing engine, all developed from scratch based on new principles. It automatically generates GraphQL/REST/gRPC services according to Excel data models, allowing for customized development without modifying the source code of the basic product. It supports native compilation with GraalVM, and is free for commercial use by small and medium-sized enterprises.

The underlying architecture of the Nop platform does not utilize third-party frameworks such as Spring. Instead, it has been redesigned and implemented based on new software construction principles, greatly reducing the complexity of the framework’s internal structure while significantly enhancing its flexibility, scalability, and performance. Its design goal is to explore next-generation software production technologies, laying the technical foundation for intelligent software production in the AI era.

-nop-entropy is the back-end part of the Nop platform. It is implemented in Java language, does not rely on third-party frameworks, and can be integrated with Quarkus, Spring or Solon frameworks.

-nop-entropy support for GraalVM technology, with the help of [Quarkus](https://quarkus.io/)
  or [SpringNative](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/)
  The framework is compiled into a native executable program, no JDK installation is required at runtime, and the startup speed is increased by dozens of times.

-**nop-entropy is designed to be a simple and easy-to-use domain language workbench (Domain Language Workbench)**
  . By adding simple metadata definitions, you can automatically get the corresponding parsers, validators, IDE plug-ins, debugging tools, etc., and automatically add module decomposition, differential customization, meta-programming and other common language features for DSL domain languages. At this point, it is similar to Jetbrains company's [MPS product](https://www.jetbrains.com/mps/)
  It's just that its design principles and technical implementation paths are essentially different from MPS.

-The nop-entropy adopts a cloud-native design with built-in distributed transaction and multi-tenant support. It can run on a stand-alone machine or as a distributed cluster. It can provide online API services or automatically package online services for a single business object as batch tasks for batch files. For most business application scenarios, the corresponding model support is provided, and the main functions can be completed with a small amount of configuration, greatly reducing the need for manual coding.

-The nop-entropy can be used as a low-code platform supporting incremental development during the development period, automatically generating various codes and related documents, and can be used as a low-code platform during the runtime period *
  * Support technology for end-user codeless platform **, allowing customers to adjust business module functions online and iterate products in a WYSIWYG manner.

At present, the open source part mainly includes the implementation of XLang language, as well as ORM, dependency injection container (IoC), distributed configuration (Config), GraphQLEngine, report engine (Report.
Engine), task scheduling engine (Job Scheduler), batch Prcessing engine (Batch Engine), rule engine (Rule Engine) and other basic frameworks,
Follow-up planning includes workflow engine (Workflow Engine), business intelligence (BI), flow processing engine and other common parts of business development.

> The code of Nop Platform 2.0 is refactored by Entropy Platform 1.0
> recently, the integration of the domestic framework solon has been increased. see [nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon). the size of the packaged jar package is more than 10 m lower than that of Spring and Quarkus.

Development Progress:

| Module | Description | Progress            |
| ----------------- | -------------------------- |---------------------|
| nop-api-support | Support classes for API interface | Completed           |
| nop-codegen | Data-Driven Code Generator | Completed           |
| nop-antlr | Model-driven retrofit of Antlr | Completed           |
| nop-core | Virtual File System, Reflection Mechanism, XML/JSON Parsing | Completed           |
| nop-ioc | Declarative IoC Container | Completed           |
| nop-config | Dynamic Configuration Center | Completed           |
| nop-xlang | XLang Scripting and Template Languages | Completed           |
| nop-dao | JDBC Access, Transaction, Database Dialects | Completed           |
| nop-orm | Next Generation ORM Engine | Completed           |
| nop-graphql | GraphQL Engine | Completed           |
| nop-rpc | Distributed RPC call | Completed           |
| nop-ooxml | Parsing and generation of Office files, replacing POI. Word Report Template | Completed           |
| nop-report | Chinese Reporting Engine | Completed           |
| nop-rule | Rules Engine | Completed           |
| nop-autotest | Model-Driven Test Automation Framework | Completed           |
| nop-idea-plugin | IDEA development plug-in, supporting syntax prompt, file jump, breakpoint debugging | Basically available |
| nop-cli | Encapsulates code generators, file listening, and more as command-line tools | Basically available |
| nop-cluster | Distributed cluster support | 50%                 |
| nop-tcc | Distributed Transactions | 50%                 |
| nop-dyn | Design forms, data models, and service functions online | Basically available |
| nop-workflow | Workflow Engine | 65%                 |
| nop-task | Logical Flow Orchestration | Basically available |
| nop-job | Distributed Task Scheduling | 40%                 |
| nop-batch | Batch Engine | Basically available |
| nop-message | Kafka/Pulsar Message Queue Encapsulation | 10%                 |
| nop-dbtool | Database Import and Export, Data Structure Comparison, Synchronization Tool | 30%                 |
| nop-nosql | Redis Package | 0%                  |
| nop-stream | Simplified stream processing, which can be integrated with Flink | 0%                  |
| nop-netty | TCP/IP Service Processing Framework | 10%                 |
| nop-datav | BI Data Analysis | 0%                  |
| nop-gpt | Integration with AI Big Model for AIGC | 2%                  |
| nop-js | GraalVM Js engine encapsulation to run JS in Java | 50%                 |
| nop-integration | External service packages such as mail, SMS, and file services | 30%                 |
| nop-auth | User Rights Management | Completed           |
| nop-sys | System Configuration Management | Completed           |

## Source code address

-gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
-gitcode:[https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
-github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)

## Design principle

[Reversible Computing: A Theory of Next Generation Software Construction](https://zhuanlan.zhihu.com/p/64004026)

[NOP: The Next Generation Software Production Paradigm](https://zhuanlan.zhihu.com/p/66548896)

[What kind of ORM engine do low-code platforms need? ](https://zhuanlan.zhihu.com/p/543252423)

[Analysis of Reversible Calculation Theory Written to Programmers](docs/theory/reversible-computation-for-programmers.md)

[Addendum to Reversible Calculation Theory Discrimination for Programmers](docs/theory/reversible-computation-for-programmers2.md)

## Quick start

[Development Example](./docs/tutorial/tutorial.md)

[Introduction and Q & A Video](https://www.bilibili.com/video/BV1u84y1w7kX/)

[Development Documentation Guide](./docs/index.md)

[Integration with Ruoyi Framework](https://gitee.com/canonical-entropy/nop-for-ruoyi)

[More video](https://space.bilibili.com/3493261219990250)

## Software Architecture

nop-entropy the Spring framework is not used, all modules are developed from scratch in a model-driven manner (much of the code in the framework itself is also model-generated and can be customized and adjusted declaratively). In principle, nop-entropy can run on top of any microservices framework that supports the REST service standard. At present, we mainly support the integration of Quarkus framework and Spring framework.

[Quarkus](https://quarkus.io/) is a new generation of cloud-native microservice framework open source by Redhat. Its development experience and GraalVM
Native compilation is significantly more mature than the Spring framework. With the help of Quarkus framework, we can compile the application into a single uber jar (through java
-jar instruction to run), you can also compile the program into an exe executable program, you don't need to install JDK at runtime, and the startup speed is increased by dozens of times. At present, the development and debugging of nop-entropy is mainly based on the Quarkus framework, so there may be some minor problems with the support of Spring framework.

Front-end code for the Nop platform In the [nop-chaos project](https://gitee.com/canonical-entropy/nop-chaos), the nop-chaos packaged result is packaged as the following Java module.

1. nop-web-site: The packaging result of the front-end main page framework.
2. nop-web-amis-editor: The packaging results of the AMIS visual editor used by the front end.
   In general business development, we only write JSON and a few JS files, and do not need to recompile nop-chaos project.

## Installation Tutorial

Environment preparation: JDK 17 +, Maven 3.9.3 +, Git

```Shell
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar
```

Note: **JDK17 or above is required to compile and run. JDK8 is not supported**. **When running in PowerShell, you need to wrap the parameters in quotation marks * *

According to feedback, some JDK versions will report errors when compiling, such as jdk:17.0.9-graal will report errors IndexOutOfBound exceptions, so if there is a problem with compiling, you can try the OpenJDK first.

```
mvn clean install "-DskipTests" "-Dquarkus.package.type=uber-jar"
```

The quarkus.package.type parameter is a parameter recognized by the quarkus framework. Specifying it as a uber-jar will package items such as nop-quarkus-demo into a single jar package containing all dependent classes. You can do it through Java.
-Jar XXX-runner.jar way to run directly.

## PowerShell garbled code problem solved

You can set the encoding of the PowerShell to UTF8

```
$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
```

you have upgraded to quarkus3.0, and running nop-auth-app and other modules with an earlier version of maven may fail. We recommend upgrading to Maven.
Version 3.9.3, or use the mvnw command in the nop-entropy directory, it will automatically download and use maven 3.9.3.

* nop-idea-plugin
  nop-idea-plugin is a plug-in project for IDEA and must be compiled using Gradle.

```
cd nop-idea-plugin
gradlew buildPlugin
```

> The idea packaging plug-in currently in use does not support a higher version of gradle. gradlew will automatically download the required gradle version, currently using 7.5.1
> if you want to speed up the gradle download speed, you can change it to gradle-wrapper.properties
> distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-7.5.1-bin.zip

The compiled plug-ins are stored in the build/distributions directory. See [Plug-in Installation and Use](docs/dev-guide/ide/idea.md).

## Instructions for use

* Platform built a demo program, using H2 memory database, you can directly start running

```Shell
cd nop-demo/nop-quarkus-demo/target
java -Dquarkus.profile=dev -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

> If you do not specify profile = dev, it will start in prod mode. In the prod mode, you need to configure the database connection in the application.yaml. By default, the local MySQL database is used.

* Access link [http:// localhost:8080](http:// localhost:8080), * * User name: nop, Password: 123 * *

* In IDEA, you can debug and run QuarksDemoMain classes in nop-quarks-demo projects.
  The quarkus framework provides the following debugging tools during development,

> http://localhost:8080/q/dev
> http://localhost:8080/q/graphql-ui

In the graphql-ui tool, you can view the definitions and parameters of all backend service functions.

* For a complete development example, see [tutorial](docs/tutorial/tutorial.md)

## Framework Integration

nop-entropy does not rely on Spring or quarkus frameworks, nor does it depend on a specific database, so it is easy to integrate and use in third-party applications.

> The functionality of the core engine does not depend on the database and can be run in pure memory. All storage-related code has been stripped into separate dao modules, such as nop-auth-dao,nop-sys-dao, etc.

1. Use as an incremental code generation tool: maven can read Excel model files when packaging, apply the specified template directory, and generate code in an incremental manner. See [codegen.md](docs/dev-guide/codegen.md)

2. Provide reversible computing support for existing XML/JSON/YAML format configuration files and domain model files: add dynamic decomposition, merging, and product customization mechanisms for model files, which are completely transparent to the application layer, and only need to write a custom model file loader for the engine layer. See [delta-loader.md](docs/dev-guide/delta-loader.md)

3. Provide support for development domain-specific languages (DSL): You only need to define xdef metamodel files to obtain IDE support such as syntax prompts, link jumps, breakpoint debugging, etc. Visual designer customization support will be provided later. See [idea-plugin.md](docs/user-guide/idea/idea-plugin.md)

4. Use as a model-driven GraphQL engine: Automatically generate GraphQL services based on Excel models and support the addition, deletion, modification and query of complex main tables. See [graphql.md](docs/dev-guide/graphql/graphql-java.md)

5. Use as a report engine: only need to add a small number of labels to Word or Excel files to run as a report template and dynamically generate complex Chinese-style reports. See [report.md](docs/user-guide/report.md)

6. Use as a workflow engine: combined with a timing scheduling engine, it supports manual approval workflows and distributed DAG task flows similar to airflow. See [workflow.md](docs/user-guide/workflow.md)

7. Use as a batch engine: Similar to the SpringBatch + XXLJob framework, it provides distributed batch task support. You can specify how to parse and generate text or binary data files through configuration files without writing parsing and generation code. See [batch.md](docs/user-guide/batch.md)

8. Use as a rule engine: realize complex business rule judgment through configuration. See [rule.md](docs/user-guide/rule.md)

9. Use as a data-driven automated testing framework: Automated testing through recording and playback mechanisms. The output data is automatically recorded during the first run, and then automatically compared with the recorded data snapshot during the run, reducing the amount of code that needs to be written manually. See [autotest.md](docs/dev-guide/autotest.md)

## Sample page

1. Interface Framework
   ![](./docs/demo/framework.jpg)

2. Use Excel to define the data model.
   ![](./docs/tutorial/excel-model.png)

3. Use Excel to define the API model for external release
   ![](./docs/dev-guide/microservice/api-model.png)

4. Integrate Baidu's front-end low-code framework AMIS
   ![](./docs/tutorial/amis-editor-view.png)

5. Integrated GraphQL debugging tools
   ![](./docs/tutorial/graphql-ui.png)

6. Provide IDEA plug-in to support breakpoint debugging of custom DSL
   ![](./docs/tutorial/xlang-debugger.png)

7. Use Excel as a report designer to support complex Chinese reports.
   ![](./docs/user-guide/report/block-report-result.png)
   ![](./docs/user-guide/report/cross-table-report-result.png)

8. Use the Word template to export Word reports.
   ![](./docs/dev-guide/report/word-template/word-report.png)

9. Use Excel to design decision tables and decision matrices.
   ![](./docs/dev-guide/rule/decision-tree.png)
   ![](./docs/dev-guide/rule/decision-matrix.png)

## Open Source Protocol

The front-end of the Nop platform uses the MIT protocol, and the back-end uses the AGPL3.0 open source protocol as a whole. However, small and medium-sized enterprises in China can use the code of this project under conditions similar to the Apache2.0 agreement (it can be used for free commercial use, and the modification of the code does not need to be open source, but the original copyright information in the source code should be retained). In order to facilitate third-party integration, the 3 packages of nop-api-support/nop-commons/nop-core use Apache
2.0 agreement.

* The algorithm for judging whether a small or medium-sized enterprise is as follows:

```javascript
Switch (Is your company rich ()){
  case "plenty of money":{
    console.log("With so much money, do you still need to wonder all day long whether other people's intellectual property rights are free?");
    return false;
  default:
    return true;
  }
}
```

## Technical Support

Problems or bugs encountered in use can be mentioned on [Gitee](https://gitee.com/canonical-entropy/nop-entropy/issues) or [GitCode](https://gitcode.com/canonical-entropy/nop-entropy/issues)

## Acknowledgements

* Thanks to the ORM test case contributed by  [xyplayman](https://gitee.com/xyplayman), the original project address is https://gitee.com/xyplayman/nop-orm-demo

## Community

* official website international station: [https://nop-platform.github.io/](https://nop-platform.github.io/)
* GitCode Community:[https://gitcode.com/org/nop-platform/discussion](https://gitcode.com/org/nop-platform/discussion)
* [Nop Development Practice Sharing Website](https://studio.crazydan.org/) Established by [Crazydan Studio](https://nop.crazydan.io/)

## Author WeChat and WeChat Discussion Group

![](wechat-group.png)

Please note when adding WeChat: Join Nop Platform Group

## WeChat Public Number

![](wechat-public-account.jpg)
