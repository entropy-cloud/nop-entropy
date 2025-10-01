# Positioning and Development Roadmap of the Nop Platform

The Nop Platform is built from the ground up based on Reversible Computation theory and supports a language-oriented programming paradigm for a next-generation low-code platform. At the level of fundamental software construction principles, it breaks through the limitations of traditional object-oriented and component theories, enabling system-level, coarse-grained software reuse.
The Nop Platform was open-sourced in March 2023 and has been available for close to a year. Gradually, some developers have begun using it in their projects or rebuilding their low-code products based on its core ideas. In this context,
some may wonder what the future development plan for the Nop Platform looks like and how sustainable it is as an open-source project. In this article, I will address some common questions.

## I. Will there be a commercial edition of the Nop Platform?

The Nop Platform itself has no commercialization goal, so there will be no commercial edition in the future; all code will remain fully open source. In terms of licensing, the frontend (nop-chaos) uses the MIT License and the backend (nop-entropy) uses the AGPL License, but with a commercial exemption for small and medium-sized enterprises (SMEs), allowing SMEs to use the Nop Platform under conditions similar to the Apache 2.0 License
(Apache 2.0 allows source modification and free commercial use, and modified parts may remain closed-source, but it requires preserving original author information in all source files). My intention is to adopt a more SME-friendly license, ideally one that encourages collaboration between SMEs.

The Nop Platform’s implementation is concentrated on the backend, focusing on providing fundamental support for software construction principles. It will not add a large number of application-level features. If you have detailed commercial feature requirements, it is recommended to enhance the platform through module extensions and Delta-based customizations.
The frontend of the Nop Platform currently uses Baidu AMIS as the default page rendering framework. Its overall aesthetics and usability are somewhat lacking for commercial products. If you have high frontend requirements, consider using only the Nop Platform’s backend. For specific configuration, refer to articles such as [Nop Quickstart: Minimalist Service Layer Development](https://zhuanlan.zhihu.com/p/679712079).
There are also many demonstration videos on Bilibili showing how to use only certain modules of the Nop Platform.

> Community members glennxu and 悠闲的水 contributed parts of the frontend code. After refactoring, the frontend will adopt a dynamic plugin architecture, allowing the use of Huawei’s OpenTiny and other frontend frameworks as rendering engines, and it will no longer mandate the use of Baidu AMIS.

The Nop Platform offers extensibility far beyond current framework technologies. Typical secondary development and customization needs can be implemented by adding independent Delta modules without modifying the platform’s foundational source code. For a technical overview, see [How to Implement Custom Development Without Modifying Base Product Source Code](https://zhuanlan.zhihu.com/p/628770810).

If you have requirements for general-purpose features, you can submit an issue on Gitee. For widely used common needs, I will provide free technical support. However, I personally will not engage in paid commercial development, and any features I implement will be released as open-source code.

## II. What’s the R&D team situation for the Nop Platform? Can it ensure ongoing improvement?

All frontend and backend code for NopPlatform 2.0 was written by me alone. The backend project nop-entropy currently contains about 150,000 lines of hand-written Java code (plus over 100,000 lines of auto-generated and third-party open-source code),
and is expected to reach about 200,000 lines of hand-written code and stabilize at that scale.

The technical solutions used in the Nop Platform have evolved over more than twenty years and been validated across numerous software products and projects, forming a standardized architectural pattern. The Nop Platform’s specific implementation has gone through multiple stages—including the Witrix platform, the Entropy platform, and the current Nop Platform—and has undergone multiple complete rewrites.
In previous implementations, constrained by product development time and resources, certain parts that could theoretically be handled by automated reasoning under Reversible Computation were implemented via manual coding.
Additionally, because Reversible Computation is abstract and difficult for most people to fully grasp, even those who understand it may struggle to find simple technical approaches, so the actual implementations often fell short of my expectations.
In the latest implementation, I removed all code written by others and rebuilt everything from zero. After over three years of rewriting, the current implementation adheres strictly to Reversible Computation theory and greatly surpasses mainstream open-source frameworks in conceptual consistency.

The module structure of the Nop Platform should largely stabilize in 2024, providing a solid architectural foundation for AIGC (AI integration). Going forward, I will continue to provide architectural improvements and open-source technical support. At the same time, I also support
third parties in building commercial wrappers on top of the Nop Platform to create genuinely valuable technical products.

Thanks to innovative software construction principles, the Nop Platform achieves the same functionality with an order-of-magnitude fewer lines of code than typical open-source frameworks, making it easier to understand and master. For example, the Nop Platform implements
distributed RPC calls in just over 3,000 lines of code—see [Distributed RPC Framework in a Low-Code Platform](https://zhuanlan.zhihu.com/p/631686718). It also implements a complete Chinese-style report expansion engine in just over 3,000 lines of code—
see [An Open-Source Chinese-Style Reporting Engine Using Excel as Designer: NopReport](https://zhuanlan.zhihu.com/p/620250740). Therefore, capable technical teams can fully master all the technical details of the Nop Platform.
Overall, the platform’s technical complexity is kept at a level where a single programmer can thoroughly grasp all details (after all, it was developed by a single programmer).

## III. How is the performance of the Nop Platform? Can it support high concurrency and large-scale complex applications?

The Nop Platform leads mainstream Spring frameworks in performance. This advantage stems from two aspects:

### 1. Advantages in software construction principles

Based on Reversible Computation theory, the Nop Platform extensively uses compile-time transformation and just-in-time compilation. As a result, much of the complex decision logic that traditional frameworks must execute at runtime is shifted to compile time, and the presence of rich model abstractions does not incur extra performance overhead.

### 2. Late-mover advantage in implementation

The Nop Platform is a new-generation framework built completely from scratch in recent years. It can draw on lessons learned from many years of evolution of other frameworks and focus on optimizing the few code paths that are truly commonly used. Traditional frameworks, born years ago, are burdened by backward compatibility and often perform extra actions at runtime.
For example, the NopGraphQL engine can provide both GraphQL and REST interfaces simultaneously. Under REST, it uniformly uses JSON for request and response serialization, whereas Spring MVC, due to compatibility considerations, must include multiple encoding strategies and perform many redundant checks at runtime.
Traditional web frameworks cannot natively support both GraphQL and REST. If you separately expose GraphQL via graphql-java, you inevitably introduce substantial duplicate format conversions and interface adaptation work.

On another front, existing workflow engines, IoC engines, ORM engines, web engines, rules engines, and batch processing engines are each designed and implemented by different open-source teams, then integrated into a unified framework by Spring.
This leads to extensive conceptual duplication at lower layers, and when these engines work together they require lengthy adapter interfaces. Extending the system forces us to learn differing extension mechanisms across frameworks and deal with potential implementation conflicts.
In the Nop Platform, all frameworks are described using the same XDef meta-model and employ a unified Delta-based mechanism at the XDSL layer for custom extensions. This reduces information conversion overhead and enables seamless integration of multiple engines at minimal cost.

I work at a company that provides banking core systems, and I have substantial experience in high-concurrency, high-complexity B2B software development, including development of domestically localized (“Xinchuang”) software products. Therefore, the Nop Platform comes with extensive built-in support for high performance and high complexity B2B product development, and it has been thoroughly validated in practice.

## IV. How complete is the Nop Platform’s support for various technical standards? Can it interoperate with third-party microservices?

None of the Nop Platform’s core functionality relies on third-party open-source components; everything is implemented from scratch. For example, its XML parsing does not use third-party libraries—instead it implements an XNodeParser in the nop-core module. Similarly, it does not use graphql-java to implement GraphQL;
rather, it includes a self-written GraphQLDocumentParser and a GraphQLEngine with numerous innovative designs. Some might ask: without these third-party libraries, how can you ensure your implementation is 100% compatible with external standards (e.g., the GraphQL spec)?
The Nop Platform’s answer is: it cannot be 100% compatible, nor is it necessary. There are three reasons:

### First, considerations of implementation cost

Given that I alone wrote the platform’s core code, I adopt a conservative strategy regarding standards: the Nop Platform uses only the subset of any standard that is compatible with Reversible Computation theory, and does not pursue coverage of every detail in the standard.
Take the NopGraphQL engine as an example: it exposes a standard GraphQL service, works with third-party tools like GraphiQL for debugging, and can interoperate with Spring Cloud microservices. However, NopGraphQL does not support every detail of the GraphQL spec, nor has it been audited for completeness against the spec.
Its degree of implementation is determined by what is actually needed in practice by the NopGraphQL engine. Features without demonstrated practical value are not currently implemented. Similarly, in the NopReport engine, the expression language supports only the most common Excel functions and does not provide hundreds of Excel-compatible functions like commercial products do.

If you do have such needs, consider implementing extensions via plugins yourself. (All core modules of the Nop Platform provide plugin interfaces at various abstraction levels for custom extensions.)

Overall, mainstream architectures today have mature solutions for heterogeneous system integration. The Nop Platform exposes highly consistent interfaces, making it easy to adapt to various external standards and form a complete service cluster. But features in original standards aimed at niche demands are not used by the Nop Platform, and thus are not supported.

### Second, security and best-practice considerations

Technical standards often provide multiple optional ways to implement the same feature, with varying levels of security and usability. In practice, we typically recommend only a few best-practice approaches, but for compatibility, standards retain many options that do not align with best practices, which can inadvertently introduce security vulnerabilities.

Because I have long worked on complex, high-end large-scale systems with strict security and compliance requirements, the Nop Platform generally retains only features with higher security that align with current best practices. For example, the XML standard includes custom Entities, but they are prone to security bugs. In the past, large companies such as Amazon, Google, Tencent, and Alibaba have encountered
zero-day vulnerabilities stemming from XML parsers due to these rarely used yet standardized complex features. The Nop Platform chooses to support only the most commonly used subset of the XML standard, dropping any features that load external entities to eliminate such bugs at the root.

Security has been significantly strengthened in the Nop Platform’s implementation. For example, GraphQL statement length, number of operations, and allowed nesting levels all have default limits (configurable), ensuring a high baseline security level and resistance to common injection and resource exhaustion attacks.

### Third—and most importantly—innovative designs should not be constrained by existing forms

Reversible Computation theory represents the next generation of software construction theory, and the Nop Platform is a next-generation development platform guided by that theory. Therefore, the Nop Platform focuses on design spaces that current framework technologies cannot reach—areas that remain uncharted.
Its emphasis is on exploring new laws of software structure in this “no man’s land,” proposing innovative technical solutions to problems previously unsolved by others; consequently, its implementation will not be constrained by current technical standards and, frankly, does not prioritize compatibility with them.
If features in a standard are compatible with Reversible Computation, they can be adopted directly. If they are unused or conflict with Reversible Computation in practice, such “standards” are simply not considered.
The entire R&D journey of the Nop Platform is about blasting through obstacles and forging ahead.

Traditional designs are content to solve current business problems, but the Nop Platform aims to address cross-system, cross–software lifecycle evolution as a whole.
Therefore, the Nop Platform values the completeness of information representation and local analyzability far more than pursuing so-called powerful features, and it will inevitably reshape parts of existing technical forms that do not meet Reversible Computation requirements.

## V. What is the development positioning of the Nop Platform?

First, it must be emphasized that the Nop Platform is definitely not a mass-market, trendy framework. My primary goal in developing the Nop Platform is to serve as a reference implementation of Reversible Computation theory, clarifying some of its technical ideas.
A side effect is that, based on the latest industry insights, it provides best practices for the present and near future, encourages thinking about new architectural patterns, and helps raise the overall level of software architecture design domestically. Therefore, the Nop Platform will not become an
out-of-the-box, user-friendly, meticulously polished development framework, nor will it become a mature software product that can immediately generate commercial value.

Practically speaking, the Nop Platform is positioned as a foundational technology base built on new software construction principles. If you want to develop a general-purpose low-code product comparable to Mendix or OutSystems, with broad applicability and competing with the world’s best,
consider using the Nop Platform at the base layer or borrowing its original designs to save development time. Good product design combined with the Nop Platform’s leading architectural principles can deliver disproportionate benefits.

The Nop Platform’s planned components and their development progress are detailed in the [README.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/README.md),
and they are expected to be completed and released to Maven Central between 2024 and 2025.

## A side note

Linus Torvalds famously said, “Talk is cheap, show me the code.” The Nop Platform is fully open source; the code is there. Any doubts or disputes can be answered in the source.

Interestingly, many people domestically don’t want to read code or invest effort in understanding others’ ideas—they just want “show me the money” (Can this platform make money? Has it made money?). Frankly, that’s not in the hacker spirit.
Even more curious are those who love posting on social feeds, criticizing a domestic obsession with utility, lamenting an inability to settle down and do real technical research, and claiming China’s technological gap is due to a lag in thinking—yet they’re the same group constantly talking about money.
For this sort of cognitive dissonance, I can only assume that failing to make money has left them flustered and incoherent.

Some find the term “Reversible Computation” too grandiose—like pseudoscience—and are incredulous upon hearing that the author rarely uses circumvention tools to browse foreign sites and seldom reads foreign technical papers. Some ask: “It’s 2024—does the author still not have internet at home? Is this building cars behind closed doors?”
Here’s an explanation: while I don’t browse foreign sites that way, I do read open-source code—generally seeking to understand technology directly from source rather than articles. My academic background is in theoretical physics, with formal training, and I also read mathematical and physical literature.
Reversible Computation originated from an intentional effort to bring ideas from physics into software engineering. The reason I can invent original technical solutions is precisely that my thinking isn’t confined by traditional doctrines like that of many with formal CS backgrounds.
Most programmers’ understanding of abstract theory is limited to the undergraduate level—essentially stuck in the era of Newton and Leibniz. Without actively improving one’s capacity for abstract knowledge, even if you exhaustively scrape all computer-related information on the internet,
you will still remain disconnected from the world of knowledge discovered by humanity’s best minds over the past 200 years.

Some say Reversible Computation is hard to understand, which is normal. Most people don’t truly need theoretical explanations—they work under others’ conventions and simply intuit the benefits of doing things a certain way
(just as most people don’t need to understand the abstract relational model to use relational databases proficiently). The Nop Platform creates its own conventions, so to most people it’s like reading arcane scripture. If you’re interested, just read the code.

<!-- SOURCE_MD5:4eed7403292daf1df40d58deafaa7043-->
