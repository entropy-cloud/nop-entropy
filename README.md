[[English]](README.en.md)   [[Tutorial]](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial_en.md)  [[开发示例]](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)

#### 介绍

Nop Platform 2. 0 is a new generation of low-code platform based on the theory of reversible computation. 
It is committed to overcoming the dilemma that low-code platform can not get rid of the exhaustive method, 
transcending the component technology from the theoretical level, and effectively solving the problem of coarse-grained software reuse.

Nop Platform 2.0是基于可逆计算原理从零开始构建的新一代低代码平台，它致力于克服低代码平台无法摆脱穷举法的困境，从理论层面超越组件技术，有效的解决粗粒度软件复用的问题。

- nop-entropy是Nop平台的后端部分。它采用Java语言实现，不依赖第三方框架，可以和Quarkus或者Spring框架集成在一起使用。

- nop-entropy支持GraalVM技术，可以借助于[Quarkus](https://quarkus.io/) 或者[SpringNative](https://docs.spring.io/spring-native/docs/current/reference/htmlsingle/)框架编译为原生可执行程序，运行时不需要安装JDK，且启动速度提升数十倍。

- **nop-entropy的设计目标是成为简单易用的领域语言工作台（Domain Language Workbench）**。通过增加简单的元数据定义，就可以自动得到对应的解析器、验证器、IDE插件、调试工具等，并自动为DSL领域语言增加模块分解、差量定制、元编程等通用语言特性。在这一点上，它类似于Jetbrains公司的[MPS产品](https://www.jetbrains.com/mps/)，只是它的设计原理和技术实现路径与MPS有着本质性差别。

- nop-entropy采用云原生设计，内置分布式事务和多租户支持，可以单机运行，也可以作为分布式集群运行，可以提供在线的API服务，也可以将针对单个业务对象的在线服务自动包装为针对批处理文件的批处理任务。对于大多数业务应用场景均提供相应的模型支持，只需少量配置即可完成主要功能，大大降低对手工编码的需求。

- nop-entropy在开发期可以作为**支持增量式开发的低代码平台**，自动生成各类代码以及相关文档，在运行期可以作为**面向最终用户的无代码平台的支撑技术**，允许客户在线调整业务模块功能，以所见即所得的方式进行产品迭代。

目前开源的部分主要包含XLang语言的实现，以及ORM、IoC、Config、GraphQLEngine、ReportEngine、JobEngine、BatchEngine等基础框架，后续规划包括RuleEngine、WorkflowEngine、BI等业务开发常用部分。

**WARNING: Nop Platform 2.0的代码是由Entropy Platform 1.0重构而来，目前重构工作没有完全做完，且尚未在实际项目中使用过。**

#### 源码地址

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)

#### 设计原理

[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)

[NOP:下一代软件生产范式](https://zhuanlan.zhihu.com/p/66548896)

[可逆计算的技术实现](https://zhuanlan.zhihu.com/p/163852896)

[从可逆计算看Delta Oriented Programming](https://zhuanlan.zhihu.com/p/377740576)

[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

[低代码平台需要什么样的ORM引擎？](https://zhuanlan.zhihu.com/p/543252423)

#### 快速开始

[开发示例](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)

#### 软件架构

nop-entropy没有使用Spring框架，所有模块均从零开始采用模型驱动的方式研发（框架本身的很多代码也是根据模型生成并可以通过声明式方式进行定制调整的）。原则上说，nop-entropy可以运行在任何支持REST服务标准的微服务框架之上。目前，我们主要是支持Quarkus框架以及Spring框架的集成。

[Quarkus](https://quarkus.io/)是Redhat公司所开源的新一代云原生微服务框架，它的开发体验以及针对GraalVM Native编译的成熟程度都明显优于Spring框架。借助于Quarkus框架，我们既可以将应用程序编译为单一的uber jar(通过java -jar指令来运行)，也可以将程序编译为exe可执行程序，在运行时不需要安装JDK，而且启动速度提升数十倍。目前，nop-entropy的开发调试主要是基于Quarkus框架进行，所以对Spring框架的支持可能会存在一些小问题。

nop-entropy项目目前主要包含如下模块:

1. nop-api-support: API接口的支持类
2. nop-commons: 常用的帮助类和帮助函数
3. nop-core: 虚拟文件系统，反射系统，基本的树、图、表格模型，数据驱动的代码生成器框架，XML和JSON解析器
4. nop-xlang: XPL模板语言，类TypeScript的脚本语言，类XPath的通用Tree路径访问语言,
   类XSLT的通用Tree变换语言，XDefinition元模型定义语言，XDelta差量合并运算。
5. nop-config: 配置管理
6. nop-ioc: 依赖注入容器
7. nop-dao: SQL管理、事务、JDBC访问、数据库方言
8. nop-orm: 支持EQL对象查询语言的ORM引擎
9. nop-ooxml: Excel和Word模板文件的解析和生成
10. nop-graphql: GraphQL解析器和执行引擎
11. nop-biz: 业务流引擎，与nop-graphql结合对外提供GraphQL和REST服务
12. nop-ui: 视图层模型
13. nop-js: 对graalvm-js的封装，用于在Java端执行JS打包工作，摆脱对Vite/Webpack等前端打包工具的依赖
14. nop-web: 动态执行Js打包工作，动态生成前端所需的JSON页面文件
15. nop-report: 报表引擎
16. nop-wf: 工作流引擎
17. nop-rule: 规则引擎
18. nop-batch: 批处理引擎
19. nop-job: 分布式任务调度引擎
20. nop-tcc: 分布式事务引擎
21. nop-cluster: 分布式集群支持
22. nop-auth: 用户权限管理
23. nop-sys: 系统配置管理
24. nop-cli: 将代码生成器封装为命令行工具
25. nop-autotest: 自动化测试框架
26. nop-demo: quarkus和spring框架的集成演示程序
27. nop-idea-plugin: IDEA插件，支持对自定义DSL的语法提示、链接跳转、断点调试等

Nop平台的前端代码在[nop-chaos项目](https://gitee.com/canonical-entropy/nop-chaos)中，nop-chaos的打包结果被包装为以下Java模块。

1. nop-web-site: 前端主页面框架的打包结果
2. nop-web-amis-editor: 前端使用的AMIS可视化编辑器的打包结果
   在一般的业务开发中我们只会编写JSON和少量JS文件，不需要重新编译nop-chaos项目。

#### 安装教程

环境准备： JDK 11+、Maven 3.6+、Git

```shell
git clone https://gitee.com/canonical-entropy/nop-entropy.git
cd nop-entropy
mvn -T 2C clean install -DskipTests -Dquarkus.package.type=uber-jar
```

注意: **编译运行需要JDK11以上版本，不支持JDK8**

quarkus.package.type参数是quarkus框架所识别的一个参数，指定它为uber-jar将会把nop-quarkus-demo等项目打包成一个包含所有依赖类的单一jar包。可以通过java -jar XXX-runner.jar的方式直接运行。

* nop-idea-plugin
  nop-idea-plugin是IDEA的插件项目，必须采用Gradle编译。

```
cd nop-idea-plugin
gradlew buildPlugin
```

编译出来的插件存放在build/distributions目录下。参见[插件的安装和使用](docs/dev-guide/ide/idea.md)。

#### 使用说明

* 平台内置了一个演示程序，使用H2内存数据库，可以直接启动运行

```shell
cd nop-demo/nop-quarkus-demo/target
java -Dquarkus.profile=dev -jar nop-quarkus-demo-2.0.0-SNAPSHOT-runner.jar
```

> 如果不指定profile=dev，则会以prod模式启动。prod模式下需要配置application.yaml中的数据库连接，缺省使用本机的MySQL数据库

* 访问链接 [http://localhost:8080](http://localhost:8080)， **用户名:nop, 密码:123**

* 在IDEA中可以调试运行nop-quarks-demo项目中的QuarksDemoMain类。
  quarkus框架在开发期提供了如下调试工具，

>  http://localhost:8080/q/dev
>  http://localhost:8080/q/graphql-ui

在graphql-ui工具中可以查看所有后端服务函数的定义和参数。

* 完整的开发示例，参见[tutorial](docs/tutorial/tutorial.md)

#### 框架集成

nop-entroy不依赖于spring或者quarkus框架，也不依赖于特定数据库，因此它很容易集成在第三方应用中使用。

> 核心引擎的功能并不依赖于数据库，可以以纯内存的方式运行。所有存储相关的代码都已经剥离到独立的dao模块中，例如nop-auth-dao，nop-sys-dao等。

1. 作为增量式代码生成工具使用：maven打包时可以读取Excel模型文件，应用指定的模板目录，以增量化的方式生成代码。参见[codegen.md](docs/dev-guide/codegen.md)

2. 为已有的XML/JSON/YAML格式的配置文件、领域模型文件提供可逆计算支持：为模型文件增加动态分解、合并、产品化定制机制，对应用层完全透明，对于引擎层只需要编写一个自定义的模型文件加载器。参见[delta-loader.md](docs/dev-guide/delta-loader.md)

3. 为开发领域特定语言(DSL)提供支持：只需要定义xdef元模型文件即可获得语法提示、链接跳转、断点调试等IDE支持。后续会提供可视化设计器定制支持。参见[idea-plugin.md](docs/user-guide/idea/idea-plugin.md)

4. 作为模型驱动的GraphQL引擎使用：根据Excel模型自动生成GraphQL服务，支持复杂主子表的增删改查。参见[graphql.md](docs/dev-guide/graphql/graphql-java.md)

5. 作为报表引擎使用：只需要在Word或者Excel文件中增加少量标注即可作为报表模板运行，动态生成复杂的中国式报表。参见[report.md](docs/user-guide/report.md)

6. 作为工作流引擎使用：与定时调度引擎相结合，支持人工操作的审批工作流，也支持类似airflow的分布式DAG任务流。参见[workflow.md](docs/user-guide/workflow.md)

7. 作为批处理引擎使用：类似SpringBatch+XXLJob框架，提供分布式批处理任务支持。可以通过配置文件指定如何解析、生成文本或者二进制数据文件，无需编写解析和生成代码。参见[batch.md](docs/user-guide/batch.md)

8. 作为规则引擎使用：通过配置实现复杂的业务规则判断。参见[rule.md](docs/user-guide/rule.md)

9. 作为数据驱动的自动化测试框架使用：通过录制、回放的机制实现自动化测试。第一遍运行的时候自动录制输出数据，此后运行时自动和录制的数据快照进行比较，减少手工需要编写的代码量。参见[autotest.md](docs/dev-guide/autotest.md)

#### 开源协议

Nop平台的前端采用MIT协议，后端整体采用AGPL3.0开源协议。但是中小企业可以在类似Apache2.0协议的条件下使用本项目的代码（可以免费商用，修改代码无需开源，但是要保留源码中的原始版权信息）。为了方便第三方集成，nop-api-support/nop-commons/nop-core这三个包采用Apache 2.0协议。

* 判断是否中小企业的算法如下:

```javascript
swith(贵公司很有钱吗()){
  case "有的是钱":{
    console.log("都这么有钱了，还需要整天琢磨别人的知识产权是否免费吗？");
    return false;
  default:
    return true;
  }
}
```

#### 技术支持

使用中遇到问题或者BUG可以在[Gitee上提Issues](https://gitee.com/canonical-entropy/nop-chaos/issues)

#### 微信群

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/wechat-group.png)