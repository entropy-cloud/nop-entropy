# 概览

Nop平台包含大量创新设计，一开始接触可能会感觉内容过多。但实际上，Nop平台内部设计全部遵循可逆计算原理，它的一致性非常高，只要掌握了可逆计算的基本理论，即可通过查看XDef元模型来直接获知关于模型的大部分知识。

B站视频： [Nop平台开发](https://www.bilibili.com/video/BV1u84y1w7kX/)，可以按照合集中的顺序观看

初次接触时建议按照以下顺序浏览一遍文档：

## [快速入门](tutorial/tutorial.md)

介绍了基于数据模型进行系统开发的整体流程。
可以参考视频 [使用Nop平台开发商品列表页面的完整示例](https://www.bilibili.com/video/BV1384y1g78L/)

关于Nop平台的定位和发展规划，参见[why-nop.md](./why-nop.md)

## [XLang语言](dev-guide/xlang/index.md)

XLang语言是Nop平台实现可逆计算的核心技术，它包含XDef, XScript, Xpl等一系列子语言，需要有一个整体认知才容易理解Nop平台的其他部分。
特别是 [XDSL: 通用的领域特定语言设计](dev-guide/xlang/xdsl.md)中介绍了Nop平台中XDSL领域特定语言的共性语法特征，是理解Nop平台定制化开发的关键。

## [代码生成器](dev-guide/codegen.md)

如何使用以及扩展Nop平台的代码生成器。Nop平台的代码生成器可以在Nop平台之外使用，可以定制生成模板，用于生成其他框架以及其他语言的代码。

## [架构设计](arch/index.md)

介绍了Nop平台内部众多模块之间的依赖关系，实现可逆计算原理的最核心模块只有nop-commons、nop-core以及nop-xlang等少数几个模块

系统内部模型文件的路径模式和自动加载顺序说明，参见[std-resource-path.md](dev-guide/vfs/std-resource-path.md)

## [核心代码导引](core-code-guidance.md)

介绍每个模块中的核心代码所在的Java类，以及每个类大致的功能

## [Excel模型](dev-guide/model/index.md)

除了使用平台内置的数据模型，API模型等，我们还可以利用Nop平台的机制定制实现专属于自己的Excel模型。比如我们可以用Excel模型来实现网络协议包的格式定义等。

## [IoC容器](dev-guide/ioc.md)

AOP相关原理，参见[aop.md](dev-guide/ioc/aop.md)

## [Config配置管理](dev-guide/config.md)

## [GraphQL](dev-guide/graphql/graphql-java.md)

Nop平台采用GraphQL引擎来实现后台服务，对外同时暴露GraphQL接口以及传统的REST接口。所有的REST服务都能够支持GraphQL的结果字段选择能力。
类似于SpringCloud以及Dubbo框架的分布式RPC服务的实现参见[rpc.md](dev-guide/microservice/rpc.md)，它的设计原理参见[rpc-design.md](dev-guide/microservice/rpc-design.md)

NopGraphQL可以暴露为Grpc接口，参见[grpc.md](dev-guide/microservice/grpc.md)

## [ORM框架](dev-guide/orm/index.md)

## [前端界面开发](dev-guide/xui/index.md)

前端我们的做法是根据XView视图模型生成百度AMIS框架的JSON描述。我们在AMIS的基础上也为它增加了一些扩展，可以在AMIS页面中直接使用Vue组件。
AMIS的文档参见 [AMIS Docs](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index)

## [权限配置](dev-guide/auth/auth.md)

可以控制到按钮级别的操作权限，也可以实现列级别的数据权限控制，可以针对不同的角色应用不同的过滤条件。
Nop平台内置了sso支持，可以集成keycloak单点认证服务。

## [开发调试](dev-guide/debug.md)

出错的时候如何进行错误诊断，如何打印调试信息。

## [常见开发任务](dev-guide/recipe/index.md)

汇集一些常见的开发任务的实现方式，例如如何增加一个字段，如何为列表增加过滤条件等。
常见的一些问题解决可以参见[faq.md](faq/faq.md)

## [自动化测试](dev-guide/autotest.md)

Nop平台内置了一个自动化测试框架，可以通过录制回放机制自动实现测试用例，无需手工编写数据初始化和结果验证代码。

## [报表引擎](dev-guide/report/index.md)

采用Excel作为设计器配置中国式报表: [report.md](user-guide/report.md)
采用Word作为设计器配置导出报表: [word-template.md](dev-guide/report/word-template.md)

## [无代码开发](dev-guide/nocode/index.md)

Nop平台支持无代码开发，无需通过编码、打包的过程，在线就可以设计数据模型，并编写后台服务函数。
当无代码开发复杂到一定程度，我们可以还可以平滑的迁移到代码生成方案，使用高代码开发的方式。

## [与其他低代码平台的对比](compare/nop-vs-skyve.md)

Nop平台的设计与传统的低代码平台有着非常大的差异，可以做到传统低代码平台无法做到的灵活性和可扩展性。

Nop平台如果不作为低代码平台来使用，也可以作为类似于SpringCloud的开发框架来使用。Nop平台与Spring框架的对比参见[nop-vs-spring](compare/nop-vs-springcloud.md)

## [定制化开发](dev-guide/delta/delta-customization.md)

基于Nop平台开发的产品，无需做任何特殊的设计，即可实现Delta差量化定制。例如基于Nop平台开发的银行核心应用产品在各个银行部署实施的时候，可以做到完全不修改基础产品的源码，
对数据库结构、业务逻辑、前端界面等进行全方位的定制化开发，满足客户最特异性的需求。交付给客户的基础产品就是一系列Jar包，无论调整后台还是前台逻辑都不需要修改Jar包中的源码。

## [OpenSpec安装指南](dev-guide/ai/openspec-installation.md)

OpenSpec是一个规范驱动的开发工作流工具，专为AI编码助手设计。通过在编写代码之前锁定需求，为AI助手提供确定性、可审查的输出。本指南详细介绍了如何在Nop平台项目中安装、配置和使用OpenSpec，并与OpenCode等AI工具完美集成。

## [AI生成的评论文章](theory/xlang-review.md)

使用GPT5阅读docs/theory目录下的理论文章（总长约500K），然后要求它作为绝对客观专业的软件专家，写的一篇评论和解读文章。GPT5相对客观，但是文笔较差，因此写完初稿后，使用gemini-2.5-pro进行了文字润色
