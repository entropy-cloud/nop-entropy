# Overview

The Nop platform contains a vast amount of innovative designs. At first glance, it may seem overwhelming due to the sheer volume of content. However, every aspect of the Nop platform's design is grounded in the principles of reversible computation, ensuring high consistency. Once you grasp the fundamental concepts of reversible computing, you can gain a significant understanding of the Nop platform by examining its XDef meta-model.

For a more structured approach to getting started, we recommend following this order:

# Quick Start

## [Tutorial](tutorial/tutorial.md)

This tutorial provides a comprehensive overview of the development process based on data models. For a concrete example, you can refer to the video [Developing with Nop Platform: Complete Example](https://www.bilibili.com/video/BV1u84y1w7kX/).

## [Videos](tutorial/tutorial.md)

For a more visual learning experience, we suggest watching the video [Introduction to Nop Platform Development](https://www.bilibili.com/video/BV1384y1g78L/).

# Understanding Nop Platform's Position and Roadmap

The position and development roadmap of the Nop platform can be found in [why-nop.md](./why-nop.md).

# [XLang Language](dev-guide/xlang/index.md)

XLang is the core technology implemented by the Nop platform for reversible computation. It includes a series of sub-languages such as XDef, XScript, and Xpl. A comprehensive understanding of these languages is essential for grasping other components of the Nop platform. Notably, [XDSL: Domain-Specific Language Design](dev-guide/xlang/xdsl.md) elaborates on the common syntax features of XDSL, a critical component for customized development within the Nop platform.

# [Code Generator](dev-guide/codegen/index.md)

## Usage and Extension

The code generator in the Nop platform can be utilized both within and outside the platform. It allows customization of templates to generate code for various frameworks and languages.

# [Architecture Design](arch/index.md)

This section details the dependencies among numerous modules within the Nop platform, focusing on its core module, nop-commons, nop-core, and nop-xlang. Additionally, it explains how the system's file paths and auto-loading order function, as described in [std-resource-path.md](dev-guide/vfs/std-resource-path.md).

# [Core Code Guidance](core-code-guidance.md)

This section outlines the core code locations within each module, specifying the Java classes and their functionalities.

# [Excel Model](dev-guide/model/index.md)

In addition to utilizing the platform's built-in data models (API models, etc.), you can leverage Nop's mechanisms to create a custom Excel model. For instance, you can use an Excel model to define network protocol formats or other data formats.

# [IoC Container](dev-guide/ioc/index.md)

The AOP principles are covered in [aop.md](dev-guide/ioc/aop.md).

## [Config Management](dev-guide/config/index.md)

# [GraphQL](dev-guide/graphql/graphql-java.md)

The Nop platform employs a GraphQL engine to handle backend services while simultaneously exposing both GraphQL and traditional REST interfaces. All REST services support GraphQL result field selection capabilities, similar to SpringCloud and Dubbo frameworks.

For RPC implementation details, refer to [rpc.md](dev-guide/microservice/rpc.md) and its design principles in [rpc-design.md](dev-guide/microservice/rpc-design.md).

NopGraphQL is exposed via gRPC interfaces, as detailed in [grpc.md](dev-guide/microservice/grpc.md).

# [ORM Framework](dev-guide/orm/index.md)

## ORM Framework

# [Frontend Development](dev-guide/xui/index.md)

Our approach to frontend development involves generating JSON descriptions using XView view models. Additionally, we've extended AMIS with Vue components for use within its pages.

For detailed documentation on AMIS, refer to [AMIS Docs](https://aisuda.bce.baidu.com/amis/zh-CN/docs).

# [Permission Configuration](dev-guide/auth/index.md)

This section outlines button-level operation permissions and column-level data permissions. It also explains how to implement different filter conditions based on user roles.

The Nop platform supports built-in SSO, which can be integrated with Keycloak for single sign-on services.

# [Debugging](dev-guide/debug/index.md)

## Debugging

This section covers error handling during development, including how to diagnose issues and print debug information.

# [Common Development Tasks](dev-guide/recipe/index.md)

This section aggregates common development tasks and their implementation methods. For example, it details how to add a new field to a list or implement filtering for lists.

The common solutions to some issues can be found in [faq.md](faq/faq.md).


## Automated Testing
The Nop platform includes an **Automated Testing Framework**. It allows for the automated generation of test cases through a **recording and replaying mechanism**, without needing manual coding for data initialization or result validation.


## Report Engine
The Nop platform's **Report Engine** supports configuring Chinese-style reports using Excel as a configuration tool, which generates Chinese-style reports: [report.md](user-guide/report.md). Additionally, Word can be used to configure exportable reports: [word-template.md](dev-guide/report/word-template.md).


## No-Code Development
The Nop platform supports **No-Code Development**, enabling the creation of data models and backend service functions without coding. When no-code development becomes complex, it can transition to a code generation solution using a high-code approach.


## Comparison with Other Low-Code Platforms
The design of the Nop platform differs significantly from traditional low-code platforms in terms of flexibility and scalability. It can be used not only as a low-code platform but also as a framework akin to SpringCloud. A comparison between Nop and Spring frameworks is provided here: [nop-vs-springcloud](compare/nop-vs-springcloud.md).


## Customization
Nop-based products can be customized using **Delta Differential Customization** without modifying the base source code. This involves performing full-fledged customizations on database structures, business logic, and user interfaces. The final deliverable is a series of Jar packages, with neither the backend nor frontend logic requiring modification of their respective source codes.

