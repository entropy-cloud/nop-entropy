# Overview

The Nop platform contains a large number of innovative designs, which may feel overwhelming at first. In reality, all internal designs in Nop strictly follow the principles of Reversible Computation and are highly consistent. Once you grasp the basic theory of Reversible Computation, you can directly consult the XDef meta-model to learn most of what you need about the models.

Bilibili video: [Nop Platform Development](https://www.bilibili.com/video/BV1u84y1w7kX/). You can watch it in the order of the playlist.

For first-time users, we recommend browsing the documentation once in the following order:

## [Quick Start](tutorial/tutorial.md)

Introduces the overall process of developing systems based on data models.
Refer to the video [A Complete Example of Developing a Product List Page with the Nop Platform](https://www.bilibili.com/video/BV1384y1g78L/)

For the Nop platform’s positioning and roadmap, see [why-nop.md](./why-nop.md)

## [XLang Language](dev-guide/xlang/index.md)

The XLang language is the core technology that enables Reversible Computation in the Nop platform. It comprises a suite of sub-languages including XDef, XScript, and Xpl. Having a holistic understanding makes other parts of the Nop platform easier to grasp.
In particular, [XDSL: A Generic Design for Domain-Specific Languages](dev-guide/xlang/xdsl.md) introduces the common syntactic features of XDSL domain-specific languages in the Nop platform, which is key to understanding customization development on Nop.

## [Code Generator](dev-guide/codegen.md)

How to use and extend the Nop platform’s code generator. The Nop code generator can be used outside of the platform, with customizable generation templates to generate code for other frameworks and languages.

## [Architecture Design](arch/index.md)

Introduces the dependencies among numerous internal modules. Only a handful of modules—nop-commons, nop-core, and nop-xlang—are core to implementing the principles of Reversible Computation.

For the path patterns and automatic loading order of internal model files, see [std-resource-path.md](dev-guide/vfs/std-resource-path.md)

## [Core Code Guidance](core-code-guidance.md)

Introduces the Java classes that contain the core code in each module, and the general responsibilities of each class.

## [Excel Model](dev-guide/model/index.md)

Beyond using the platform’s built-in data models and API models, we can leverage Nop mechanisms to customize and implement our own Excel models. For example, we can use Excel models to define the formats of network protocol packets, and more.

## [IoC Container](dev-guide/ioc.md)

For AOP-related principles, see [aop.md](dev-guide/ioc/aop.md)

## [Configuration Management (Config)](dev-guide/config.md)

## [GraphQL](dev-guide/graphql/graphql-java.md)

The Nop platform adopts a GraphQL engine to implement backend services, exposing both GraphQL APIs and traditional REST APIs. All REST services support GraphQL-style result field selection.
For an implementation similar to distributed RPC services in Spring Cloud and Dubbo, see [rpc.md](dev-guide/microservice/rpc.md), and for its design principles, see [rpc-design.md](dev-guide/microservice/rpc-design.md)

NopGraphQL can be exposed as a Grpc interface; see [grpc.md](dev-guide/microservice/grpc.md)

## [ORM Framework](dev-guide/orm/index.md)

## [Frontend UI Development](dev-guide/xui/index.md)

On the frontend, our approach is to generate the JSON description for the Baidu AMIS framework based on the XView view model. We have also added some extensions on top of AMIS, enabling direct use of Vue components within AMIS pages.
AMIS documentation: [AMIS Docs](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index)

## [Authorization Configuration](dev-guide/auth/auth.md)

You can control operation permissions down to the button level, and implement column-level data access control, applying different filters for different roles.
The Nop platform has built-in SSO support and can integrate with Keycloak for single sign-on.

## [Development and Debugging](dev-guide/debug.md)

How to diagnose errors and print debug information when something goes wrong.

## [Common Development Tasks](dev-guide/recipe/index.md)

Collects implementations of common development tasks, such as how to add a field and how to add filtering conditions to a list.
For common troubleshooting, see [faq.md](faq/faq.md)

## [Automated Testing](dev-guide/autotest.md)

The Nop platform includes a built-in automated testing framework that can automatically implement test cases via a record-and-playback mechanism, without manual data initialization or result verification code.

## [Reporting Engine](dev-guide/report/index.md)

Use Excel as the designer to configure Chinese-style reports: [report.md](user-guide/report.md)
Use Word as the designer to configure export reports: [word-template.md](dev-guide/report/word-template.md)

## [No-Code Development](dev-guide/nocode/index.md)

The Nop platform supports no-code development, allowing you to design data models and write backend service functions online without coding or packaging.
When no-code development reaches a certain level of complexity, you can smoothly migrate to a code-generation approach and adopt a high-code development model.

## [Comparison with Other Low-Code Platforms](compare/nop-vs-skyve.md)

The design of the Nop platform differs substantially from traditional low-code platforms, achieving flexibility and extensibility that traditional platforms cannot match.

If Nop is not used as a low-code platform, it can also serve as a development framework similar to Spring Cloud. For a comparison between Nop and the Spring framework, see [nop-vs-spring](compare/nop-vs-springcloud.md)

## [Customized Development](dev-guide/delta/delta-customization.md)

Products developed based on the Nop platform can achieve Delta-based customization without any special design. For example, when deploying a core banking application built on Nop across different banks, you can avoid modifying the base product’s source code entirely, while performing comprehensive customization of the database schema, business logic, and frontend UI to meet the most specialized customer needs. The base product delivered to customers is just a set of JAR packages; whether adjusting backend or frontend logic, there is no need to modify the source code within the JARs.

## [AI-Generated Review Article](theory/xlang-review.md)

GPT5 read the theoretical articles under the docs/theory directory (total length ~500K), then was asked to write a review and interpretation as an absolutely objective and professional software expert. GPT5 was relatively objective, but the writing quality was poor, so after the first draft, gemini-2.5-pro was used for polishing.

<!-- SOURCE_MD5:ae7888e8cd3c2e76b125738a6aad3d73-->
