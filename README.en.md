[[中文]](README.md)   [[Tutorial]](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial_en.md)  [[开发示例]](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md) [[介绍和答疑视频]](https://www.bilibili.com/video/BV1u84y1w7kX/)


#### Introduction

Nop Platform 2.0 is a new generation of low-code platform based on the theory of reversible computation. It is committed to overcoming the dilemma that low-code platform can not get rid of the exhaustive method, transcending the component technology from the theoretical level, and effectively solving the problem of coarse-grained software reuse.

- NOP-entropy is the back-end part of the Nop platform. It is implemented in the Java language, does not rely on third-party frameworks, and can be integrated with Quarkus or Spring frameworks.

- Nop-entropy supports GraalVM technology and can be compiled into native executable programs with the help of [Quarkus](https://quarkus.io/) or [ SpringNative ](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/) frameworks. The generated executable does not need to install JDK when running, and its startup speed is increased by tens of times.

- ** Nop-entropy is designed to be an easy-to-use domain language workbench (Domain Language Workbench). **。 By adding a simple metadata definition, you can automatically get the corresponding parser, validator, IDE plug-in, debugging tools, and automatically add general language features such as module decomposition, delta customization, and metaprogramming to the DSL domain language. In this regard, it is similar to Jetbrains [MPS Product](https://www.jetbrains.com/mps/), but its design principles and technical implementation path are essentially different from MPS.
- Nop-entropy adopts cloud native design, with built-in distributed transaction and multi-tenant support. It can run on a single machine or as a distributed cluster. It can provide online API services, and can also automatically package online services for a single business object into batch tasks for batch files. Corresponding model support is provided for most business application scenarios, and the main functions can be completed with only a small amount of configuration, which greatly reduces the demand for manual coding.

- Nop-entropy can be used as ** Low-code platform for incremental development ** during the development period to automatically generate various codes and related documents, and can be used as ** Supporting Technologies for End-User-Oriented No Code Platform ** during the operation period to allow customers to adjust the functions of business modules online and iterate products in a WYSIWYG way.

At present, the open source part mainly includes the implementation of XLang language, as well as ORM, IoC, Config, GraphQL Engine, ReportEngine, JobEngine, BatchEngine and other basic frameworks. Subsequent planning includes common business development parts such as RuleEngine, WorkflowEngine and BI.

** WARNING: The code of Nop Platform 2.0 is refactored from Entropy Platform 1.0. At present, the refactoring work has not been completely completed, and it has not been used in the actual project. **

#### Source code address

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)

#### Design principle

[Reversible Computation: Next Generation Software Construction Theory] (https://zhuanlan.zhihu.com/p/64004026)

[NOP: Next Generation Software Production Paradigm](https://zhuanlan.zhihu.com/p/66548896)

[Technical Realization of Reversible Computation](https://zhuanlan.zhihu.com/p/163852896)

[Reversible Computation and Delta Oriented Programming] (https://zhuanlan.zhihu.com/p/377740576)

[Low Code Platform Design from the Perspective of Tensor Product](https://zhuanlan.zhihu.com/p/531474176)

[What ORM engine is needed for low-code platforms?] (https://zhuanlan.zhihu.com/p/543252423)

#### A quick start

[Development Example] (docs/tutorial/tutorial.md)

#### Software architecture

Nop-entropy does not use the Spring framework, and all modules are developed from scratch in a model-driven manner (much of the code for the framework itself is also generated from the model and can be customized in a declarative manner). In principle, nop-entropy can run on top of any microservices framework that supports the REST services standard. At present, we mainly support the integration of Quarkus framework and Spring framework.

[Quarkus](https://quarkus.io/)  is a new generation of cloud-native microservices framework open sourced by Redhat, and its development experience and maturity for GraalVM Native compilation are significantly better than Spring framework. With the help of the Quarkus framework, we can either compile the application as a single uber jar (run through the java-jar command), or compile the program as an exe executable program, which does not need to install JDK when running, and the startup speed is increased by tens of times. Currently, the development and debugging of nop-entropy is mainly based on the Quarkus framework, so there may be some minor problems with the support of the Spring framework.

The nop-entropy project currently consists of the following modules:

1. nop-api-support: support class for the API interface
2. nop-commons: Commonly used helper classes and helper functions
3. nop-core: Virtual file systems, reflection systems, basic tree, graph, table models, data-driven code generator frameworks, XML and JSON parsers
4. nop-xlang: XPL template language, scripting language like Type Script, generic Tree path access language like XPath, generic Tree transformation language like XSLT, XDefinition metamodel definition language, XDelta delta merge operation.
5. nop-config: configuration management
6. nop-ioc: dependency injection container
7. nop-dao: SQL management, transactions, JDBC access, database dialects
8. nop-orm: An ORM engine supporting the EQL Object Query Language
9. nop-ooxml: parsing and generation of Excel and Word template files
10. nop-graphql: GraphQL parser and execution engine
11. nop-biz: Business flow engine, which is combined with nop-graphql to provide GraphQL and REST services
12. nop-ui: view layer model
13. nop-js: The encapsulation of graalvm-js, which is used to perform JS packaging work on the Java side, getting rid of the dependence on front-end packaging tools such as Vite/Webpack.
14. nop-web: Dynamically execute the Js packaging work and dynamically generate the JSON page file required by the front end
15. nop-report: report engine
16. nop-wf: workflow engine
17. nop-rule: rule engine
18. nop-batch: batch engine
19. nop-job: distributed task scheduling engine
20. nop-tcc: Distributed Transaction Engine
21. nop-cluster: distributed cluster support
22. nop-auth: user authority management
23. nop-sys: System Configuration Management
24. nop-cli: Encapsulates the code generator as a command-line tool
25. nop-autotest: automated testing framework
26. nop-demo: integrated demo for quarkus and spring frameworks
27. nop-idea-plugin: IDEA plugin, which supports syntax prompt, link jump, breakpoint debugging, etc. For custom DSL

The front-end code for the Nop platform In [ nop-chaos project ](https://gitee.com/canonical-entropy/nop-chaos), the packaging result of nop-chaos is wrapped as the following Java module.

1. nop-web-site: the packaging result of the front-end main page frame
2. nop-web-amis-editor: The packaging result of the AMIS visual editor used by the front end. In general business development, we will only write JSON and a few JS files, and there is no need to recompile the nop-chaos project.

#### Installation tutorial

Environment preparation: JDK 11 +, Maven 3.6 +, Git


```shell
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy
mvn -T 2C clean install -DskipTests -Dquarkus.package.type=uber-jar
```

Caution:

The quarkus.package.type parameter is a parameter recognized by the quarkus framework, and specifying it as uber-jar will package projects such as nop-quarkus-demo into a single jar containing all the dependent classes. It can be run directly by means of Java -jar XXX-runner. jar.

* nop-idea-plugin:  an IDEA plug-in project that must be compiled with Gradle.


```
cd nop-idea-plugin
gradlew buildPlugin
```

The compiled plug-ins are stored in the build/distributions directory. See [Installation and Usage of Nop Idea Plugin](docs/dev-guide/ide/idea.md).

#### Instructions for use

* The platform has a built-in demo program, which can be started and run directly using the H2 memory database.


```shell
cd nop-demo/nop-quarkus-demo/target
java -Dquarkus.profile=dev -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

> If you do not specify profile = dev, it starts in prod mode. The database connection in the application. Yaml needs to be configured in the prod mode, and the MySQL database of the local machine is used by default

* Access the link,

* In IDEA, you can debug and run the QuarksDemoMain class in the nop-quarks-demo project. The quarkus framework provides the following debugging tools during development,

>  http://localhost:8080/q/dev
>  http://localhost:8080/q/graphql-ui

You can view the definitions and parameters of all the back-end service functions in the graphql-ui tool.

* For a complete development example, see [tutorial](docs/tutorial/tutorial.md)

#### Framework integration

nop-entroy doesn't depend on the spring or quarkus frameworks, nor does it depend on a specific database, so it's easy to integrate and use in third-party applications.

> The functionality of the core engine does not depend on the database and can run in a pure memory mode. All storage-related code has been stripped into separate Dao modules, such as nop-auth-dao, nop-sys-dao, and so on.

1. Use as an incremental code generation tool: when Maven is packaged, it can read the Excel model file, apply the specified template directory, and generate the code in an incremental way. See [codegen.md](docs/dev-guide/codegen.md)

2. Provide reversible computation support for existing configuration files and domain model files in XML/JSON/YAML formats: add dynamic decomposition, merging and productization customization mechanisms for model files, which are completely transparent to the application layer. For the engine layer, only a custom model file loader needs to be written. See [ delta-loader.md ](docs/dev-guide/delta-loader.md)

3. Support for developing domain specific languages (DSLs): You only need to define the xdef metamodel file to get IDE support for syntax hinting, link jumping, breakpoint debugging, and more. Visual designer customization support will be provided later. See [ idea-plugin.md ](docs/user-guide/idea/idea-plugin.md)

4. Used as a model-driven GraphQL engine: automatically generate GraphQL services according to the Excel model, and support the addition, deletion, modification, and query of complex primary-sub tables. See [graphql.md](docs/dev-guide/graphql/graphql-java.md)

5. Use as a report engine: You only need to add a small amount of annotations in the Word or Excel file to run as a report template to dynamically generate complex Chinese-style reports. See [report.md](docs/user-guide/report.md)

6. Used as a workflow engine: Combined with the timing scheduling engine, it supports the approval workflow of manual operation, and also supports the distributed DAG task flow similar to airflow. See [ workflow.md ](docs/user-guide/workflow.md)

7. Used as a batch processing engine: similar to the Spring Batch + XXL Job framework, providing support for distributed batch processing tasks. Configuration files allow you to specify how to parse and generate text or binary data files without having to write parsing and generation code. See [batch.md](docs/user-guide/batch.md)

8. Use as a rule engine: Implement complex business rule judgments through configuration. See [rule.md](docs/user-guide/rule.md)

9. As a data-driven automated testing framework: through the recording, playback mechanism to achieve automated testing. Automatically record the output data when running for the first time, and then automatically compare it with the recorded data snapshot when running, so as to reduce the amount of code that needs to be written manually. See [ autotest.md ](docs/dev-guide/autotest.md)

#### Open source license

The front-end of Nop platform adopts MIT license, and the back-end adopts AGPL3.0 open source license. However, small and medium-sized enterprises can use the code of this project under the conditions similar to Apache 2.0 license (free commercial use, modification of the code does not require open source, but the original copyright information in the source code must be retained). To facilitate third-party integration, the nop-api-support/nop-commons/nop-core packages use the Apache 2.0 license.

* The algorithm for determining whether a SME is a SME is as follows:


```javascript
switch(贵公司很有钱吗()){
  case "有的是钱":{
    console.log("都这么有钱了，还需要整天琢磨别人的知识产权是否免费吗？");
    return false;
  default:
    return true;
  }
}
```

#### Technical support

Problems or BUGs encountered in use can be found in [ Gitee Mention Issues ](https://gitee.com/canonical-entropy/nop-chaos/issues)

#### Wechat group

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/wechat-group.png)