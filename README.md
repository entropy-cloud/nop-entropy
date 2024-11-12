[[English]](README.en.md)   [[Tutorial]](./docs/tutorial/tutorial_en.md)  [[开发示例]](./docs/tutorial/tutorial.md) [[介绍和答疑视频]](https://www.bilibili.com/video/BV1u84y1w7kX/)

# Nop Platform 2.0

[![](https://img.shields.io/github/stars/entropy-cloud/nop-entropy)](https://github.com/entropy-cloud/nop-entropy/stargazers)
[![](https://gitee.com/canonical-entropy/nop-entropy/badge/star.svg?theme=white)](https://gitee.com/canonical-entropy/nop-entropy/stargazers)

## 介绍

[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)

**Nop is nOt Programming(非编程)**

Nop Platform 2.0 is a next-generation low-code development platform built from scratch based on the principles of reversible computation, adopting a language-oriented programming paradigm. It includes a suite of fully designed engines such as a GraphQL engine, ORM engine, workflow engine, reporting engine, rule engine, and batch processing engine, all developed from scratch based on new principles. It automatically generates GraphQL/REST/gRPC services according to Excel data models, allowing for customized development without modifying the source code of the basic product. It supports native compilation with GraalVM, and is free for commercial use by small and medium-sized enterprises.
Nop Platform 2.0基于可逆计算原理从零开始构建的采用面向语言编程范式的下一代低代码开发平台。包含基于全新原理从零开始研发的GraphQL引擎、ORM引擎、工作流引擎、报表引擎、规则引擎、批处理引引擎等完整设计，根据Excel数据模型自动生成GraphQL/REST/gRPC服务，定制化开发无需修改基础产品源码，支持GraalVM原生编译，中小企业可以免费商用。

- nop-entropy是Nop平台的后端部分。它采用Java语言实现，不依赖第三方框架，可以和Quarkus、Spring或者Solon框架集成在一起使用。

- nop-entropy支持GraalVM技术，可以借助于[Quarkus](https://quarkus.io/)
  或者[SpringNative](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/)
  框架编译为原生可执行程序，运行时不需要安装JDK，且启动速度提升数十倍。

- **nop-entropy的设计目标是成为简单易用的领域语言工作台（Domain Language Workbench）**
  。通过增加简单的元数据定义，就可以自动得到对应的解析器、验证器、IDE插件、调试工具等，并自动为DSL领域语言增加模块分解、差量定制、元编程等通用语言特性。在这一点上，它类似于Jetbrains公司的[MPS产品](https://www.jetbrains.com/mps/)
  ，只是它的设计原理和技术实现路径与MPS有着本质性差别。

- nop-entropy采用云原生设计，内置分布式事务和多租户支持，可以单机运行，也可以作为分布式集群运行，可以提供在线的API服务，也可以将针对单个业务对象的在线服务自动包装为针对批处理文件的批处理任务。对于大多数业务应用场景均提供相应的模型支持，只需少量配置即可完成主要功能，大大降低对手工编码的需求。

- nop-entropy在开发期可以作为**支持增量式开发的低代码平台**，自动生成各类代码以及相关文档，在运行期可以作为*
  *面向最终用户的无代码平台的支撑技术**，允许客户在线调整业务模块功能，以所见即所得的方式进行产品迭代。

目前开源的部分主要包含XLang语言的实现，以及ORM、依赖注入容器(IoC)、分布式配置（Config）、GraphQLEngine、报表引擎（Report
Engine）、任务调度引擎(Job Scheduler)、批处理引擎（Batch Prcessing Engine）、规则引擎（Rule Engine）等基础框架，
后续规划包括工作流引擎（Workflow Engine）、商业智能（BI）、流处理引擎等业务开发常用部分。

> Nop Platform 2.0的代码是由Entropy Platform 1.0重构而来
> 最近增加了对国产框架solon的集成，参见[nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon),打包后的jar包大小相比与Spring和Quarkus要降低10多M。

开发进度：

| 模块              | 说明                            | 进度   |
|-----------------|-------------------------------|------|
| nop-api-support | API接口的支持类                     | 已完成  |
| nop-codegen     | 数据驱动的代码生成器                    | 已完成  |
| nop-antlr       | Antlr的模型驱动改造                  | 已完成  |
| nop-core        | 虚拟文件系统、反射机制、XML/JSON解析        | 已完成  |
| nop-ioc         | 声明式IoC容器                      | 已完成  |
| nop-config      | 动态配置中心                        | 已完成  |
| nop-xlang       | XLang脚本语言和模板语言                | 已完成  |
| nop-dao         | JDBC访问、事务、数据库方言               | 已完成  |
| nop-orm         | 下一代ORM引擎                      | 已完成  |
| nop-graphql     | 下一代GraphQL引擎                  | 已完成  |
| nop-rpc         | 分布式RPC调用                      | 已完成  |
| nop-ooxml       | Office文件的解析和生成，取代POI。Word报表模板 | 已完成  |
| nop-report      | 中国式报表引擎                       | 已完成  |
| nop-rule        | 规则引擎                          | 已完成  |
| nop-autotest    | 模型驱动的自动化测试框架                  | 已完成  |
| nop-idea-plugin | IDEA开发插件，支持语法提示、文件跳转、断点调试     | 基本可用 |
| nop-cli         | 将代码生成器、文件监听等功能封装为命令行工具        | 基本可用 |
| nop-cluster     | 分布式集群支持                       | 50%  |
| nop-tcc         | 分布式事务                         | 50%  |
| nop-dyn         | 在线设计表单和数据模型、服务函数              | 75%  |
| nop-workflow    | 下一代工作流引擎                      | 65%  |
| nop-task        | 下一代逻辑流编排                      | 50%  |
| nop-job         | 分布式任务调度                       | 40%  |
| nop-batch       | 下一代批处理引擎                      | 70%  |
| nop-message     | Kafka/Pulsar消息队列封装            | 10%  |
| nop-dbtool      | 数据库导入导出、数据结构比较、同步工具           | 40%  |
| nop-nosql       | Redis封装                       | 0%   |
| nop-stream      | 简化的流处理，可以集成Flink              | 0%   |
| nop-netty       | TCP/IP服务处理框架                  | 0%   |
| nop-datav       | BI数据分析                        | 0%   |
| nop-ai          | 与AI大模型集成，实现AIGC               | 2%   |
| nop-js          | GraalVM Js引擎封装，在Java中运行JS     | 50%  |
| nop-integration | 邮件、短信、文件服务等外部服务封装             | 30%  |
| nop-auth        | 用户权限管理                        | 已完成  |
| nop-sys         | 系统配置管理                        | 已完成  |
| nop-ofbiz       | 将Ofbiz的模型文件转换为Nop平台的模型定义      | 0% |

## 源码地址

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- gitcode:[https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)

## 设计原理

[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)

[NOP:下一代软件生产范式](https://zhuanlan.zhihu.com/p/66548896)

[低代码平台需要什么样的ORM引擎？](https://zhuanlan.zhihu.com/p/543252423)

[写给程序员的可逆计算理论辨析](docs/theory/reversible-computation-for-programmers.md)

[写给程序员的可逆计算理论辨析补遗](docs/theory/reversible-computation-for-programmers2.md)

## 快速开始

[开发示例](./docs/tutorial/tutorial.md)

[介绍和答疑视频](https://www.bilibili.com/video/BV1u84y1w7kX/)

[开发文档导引](./docs/index.md)

[与若依Ruoyi框架集成](https://gitee.com/canonical-entropy/nop-for-ruoyi)

[更多介绍视频](https://space.bilibili.com/3493261219990250)

## 软件架构

nop-entropy没有使用Spring框架，所有模块均从零开始采用模型驱动的方式研发（框架本身的很多代码也是根据模型生成并可以通过声明式方式进行定制调整的）。原则上说，nop-entropy可以运行在任何支持REST服务标准的微服务框架之上。目前，我们主要是支持Quarkus框架以及Spring框架的集成。

[Quarkus](https://quarkus.io/)是Redhat公司所开源的新一代云原生微服务框架，它的开发体验以及针对GraalVM
Native编译的成熟程度都明显优于Spring框架。借助于Quarkus框架，我们既可以将应用程序编译为单一的uber jar(通过java
-jar指令来运行)，也可以将程序编译为exe可执行程序，在运行时不需要安装JDK，而且启动速度提升数十倍。目前，nop-entropy的开发调试主要是基于Quarkus框架进行，所以对Spring框架的支持可能会存在一些小问题。

Nop平台的前端代码在[nop-chaos项目](https://gitee.com/canonical-entropy/nop-chaos)中，nop-chaos的打包结果被包装为以下Java模块。

1. nop-web-site: 前端主页面框架的打包结果
2. nop-web-amis-editor: 前端使用的AMIS可视化编辑器的打包结果
   在一般的业务开发中我们只会编写JSON和少量JS文件，不需要重新编译nop-chaos项目。

## 安装教程

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

````
$OutputEncoding = [Console]::OutputEncoding = [Text.Encoding]::UTF8
````

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

编译出来的插件存放在build/distributions目录下。参见[插件的安装和使用](docs/dev-guide/ide/idea.md)。

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

* 完整的开发示例，参见[tutorial](docs/tutorial/tutorial.md)

## 框架集成

nop-entropy不依赖于spring或者quarkus框架，也不依赖于特定数据库，因此它很容易集成在第三方应用中使用。

> 核心引擎的功能并不依赖于数据库，可以以纯内存的方式运行。所有存储相关的代码都已经剥离到独立的dao模块中，例如nop-auth-dao，nop-sys-dao等。

1.作为增量式代码生成工具使用：maven打包时可以读取Excel模型文件，应用指定的模板目录，以增量化的方式生成代码。参见[codegen.md](docs/dev-guide/codegen.md)

2.为已有的XML/JSON/YAML格式的配置文件、领域模型文件提供可逆计算支持：为模型文件增加动态分解、合并、产品化定制机制，对应用层完全透明，对于引擎层只需要编写一个自定义的模型文件加载器。参见[delta-loader.md](docs/dev-guide/delta-loader.md)

3. 为开发领域特定语言(DSL)提供支持：只需要定义xdef元模型文件即可获得语法提示、链接跳转、断点调试等IDE支持。后续会提供可视化设计器定制支持。参见[idea-plugin.md](docs/user-guide/idea/idea-plugin.md)

4. 作为模型驱动的GraphQL引擎使用：根据Excel模型自动生成GraphQL服务，支持复杂主子表的增删改查。参见[graphql.md](docs/dev-guide/graphql/graphql-java.md)

5. 作为报表引擎使用：只需要在Word或者Excel文件中增加少量标注即可作为报表模板运行，动态生成复杂的中国式报表。参见[report.md](docs/user-guide/report.md)

6. 作为工作流引擎使用：与定时调度引擎相结合，支持人工操作的审批工作流，也支持类似airflow的分布式DAG任务流。参见[workflow.md](docs/user-guide/workflow.md)

7. 作为批处理引擎使用：类似SpringBatch+XXLJob框架，提供分布式批处理任务支持。可以通过配置文件指定如何解析、生成文本或者二进制数据文件，无需编写解析和生成代码。参见[batch.md](docs/user-guide/batch.md)

8. 作为规则引擎使用：通过配置实现复杂的业务规则判断。参见[rule.md](docs/user-guide/rule.md)

9. 作为数据驱动的自动化测试框架使用：通过录制、回放的机制实现自动化测试。第一遍运行的时候自动录制输出数据，此后运行时自动和录制的数据快照进行比较，减少手工需要编写的代码量。参见[autotest.md](docs/dev-guide/autotest.md)

## 示例页面

1. 界面框架
   ![](./docs/demo/framework.jpg)

2. 使用Excel来定义数据模型
   ![](./docs/tutorial/excel-model.png)

3. 使用Excel来定义对外发布的API模型
   ![](./docs/dev-guide/microservice/api-model.png)

4. 集成百度的前端低代码框架AMIS
   ![](./docs/tutorial/amis-editor-view.png)

5. 集成GraphQL调试工具
   ![](./docs/tutorial/graphql-ui.png)

6. 提供IDEA插件，支持自定义DSL的断点调试
   ![](./docs/tutorial/xlang-debugger.png)

7. 使用Excel作为报表设计器，支持复杂的中国式报表
   ![](./docs/user-guide/report/block-report-result.png)
   ![](./docs/user-guide/report/cross-table-report-result.png)

8. 使用Word模板来导出Word报表
   ![](./docs/dev-guide/report/word-template/word-report.png)

9. 使用Excel来设计决策表和决策矩阵
   ![](./docs/dev-guide/rule/decision-tree.png)
   ![](./docs/dev-guide/rule/decision-matrix.png)

## 开源协议

Nop平台的前端采用MIT协议，后端整体采用AGPL3.0开源协议。但是国内的中小企业可以在类似Apache2.0协议的条件下使用本项目的代码（可以免费商用，修改代码无需开源，但是要保留源码中的原始版权信息）。为了方便第三方集成，nop-api-support/nop-commons/nop-core这三个包采用Apache
2.0协议。

* 判断是否中小企业的算法如下:

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

## 技术支持

使用中遇到问题或者BUG可以在[Gitee上提Issues](https://gitee.com/canonical-entropy/nop-entropy/issues)

## 致谢

* 感谢网友[xyplayman](https://gitee.com/xyplayman)贡献的ORM测试用例，原项目地址 https://gitee.com/xyplayman/nop-orm-demo

## 社区

* 官网国际站: [https://nop-platform.github.io/](https://nop-platform.github.io/)
* 官网中国站: [https://nop-platform.gitee.io/](https://nop-platform.gitee.io/)
* 网友[Crazydan Studio](https://studio.crazydan.org/)建立的[Nop开发实践分享网站](https://nop.crazydan.io/)

## 作者微信和微信讨论群

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/wechat-group.png)

添加微信时请注明：加入Nop平台群

## 微信公众号

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/wechat-public-account.jpg)
