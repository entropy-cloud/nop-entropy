# Nop Platform's Positioning and Development Plan

The Nop platform is built from scratch based on the theory of reversible computing, supporting a new generation of low-code platforms that break through the limitations of traditional object-oriented programming and component-based theories at the fundamental software construction level. It enables fine-grained software reuse at the system level.

Nop platform was open-sourced in March 2023 and has been approximately one year since then. Gradually, some users have started using it in their projects or have rebuilt their low-code technology products based on its core concepts.

Some people may be concerned about Nop's future development plans and its sustainability as an open-source project. In this text, I will address some common questions regarding these topics.

## 1. Will Nop Platform release a commercial version?

Nop platform itself does not have a commercialization target, so it will not release a commercial version in the future. All code will remain fully open source.

In terms of copyright agreements, the frontend (nop-chaos) uses the MIT protocol, while the backend (nop-entropy) uses the AGPL protocol. Additionally, commercial exemptions have been added to allow small and medium-sized enterprises to use Nop platform under a similar Apache 2.0 license.
(Apache 2.0 allows modification of source code and free use, with the requirement that original authors be credited.)

My intention is to adopt a more friendly license for small and medium-sized enterprises, ideally promoting collaboration among them.

The implementation focuses on the backend, prioritizing basic software construction principles without adding extensive application-level features. If there are detailed commercial feature requirements, it is recommended to extend or customize through modules like Delta customization.

For the frontend, we currently use a default page rendering framework (baidu AMIS), and overall aesthetics and usability fall short for a commercial product. For higher-end front-end requirements, it's suggested to stick with Nop's backend only.
For specific configurations, please refer to [Nop Basics: Simple Service Layer Development](https://zhuanlan.zhihu.com/p/679712079) and similar articles on Baidu.

On Bilibili, there are many video tutorials demonstrating how to use Nop platform's individual modules by user "glennxu" and "yousi shui."

Contributions from user "glennxu" include some front-end code. After reconstruction, the frontend will adopt a dynamic plugin architecture, allowing the use of frameworks like Huawei's opentiny as rendering engines. In the future, we will no longer require the default baidu AMIS framework.

Nop platform exceeds existing framework technologies in terms of expandability. General secondary development requirements can be met without modifying the foundation product's source code by adding independent Delta modules.
For detailed implementation techniques, please refer to [How to Implement Custom Development Without Modifying the Foundation Product Source Code](https://zhuanlan.zhihu.com/p/628770810).

If there are general functionality requirements, you can submit issues on Gitee. For widely used common functionality, I will provide free support on a technical level; however, commercial development (paid customization) is not something I personally engage in. All the features I have implemented will be published as open-source code.

## 2. How is the Nop platform's development team situated? Is it guaranteed to ensure continuous improvement?

NopPlatform 2.0's entire frontend and backend code is written by me alone. The backend project, nop-entropy, currently contains approximately 150,000 lines of manually written Java code (excluding 100,000+ lines of automatically generated code and copied open-source code).

Estimated total hand-written code will reach 200,000 lines and remain stable at this level.

The technical solutions employed by Nop platform have undergone over two decades of development. Through extensive software product validation across numerous projects, a standardized software architectural model has been established. The implementation code of Nop platform has gone through multiple thorough refactorings.

Previous implementations were constrained by time and resource limitations during development. Some portions that could be logically reasoned using reversible computing theory had to be manually coded due to the abstract nature of the theory. While these portions were intended for automated reasoning, in practice, they often fell short of expectations.

In the latest implementation, I discarded all code written by others and rebuilt everything from scratch. After approximately three years of rewriting, the current implementation strictly adheres to reversible computing theory and has significantly improved concept consistency. The software architecture aligns closely with modern open-source frameworks.

The module structure is expected to stabilize in 2024, laying a solid foundation for AIGC (artificial intelligence integration). Future updates will include continuous architectural improvements and open-source support. Third-party developers using Nop platform can wrap their components using commercial packaging. The technology product itself has genuine commercial value.

Due to the innovative software construction principles of Nop platform, its code required roughly one order of magnitude fewer lines than similar open-source frameworks to achieve the same functionality. For example, Nop platform achieved distributed RPC calls with just 3,000+ lines of code, whereas similar frameworks require tens of thousands of lines.
This simplicity makes it easier to understand and maintain.

For distributed RPC framework comparisons, please refer to [Distributed RPC Framework in Low-Code Platforms](https://zhuanlan.zhihu.com/p/631686718). For Excel-based report engines, see [NopReport: Open-Source Excel Report Engine](https://zhuanlan.zhihu.com/p/620250740).

The ability of the development team to handle such complex systems is a testament to their technical skills. Their deep understanding of software architecture and the ability to implement cutting-edge concepts make them capable of fully grasping Nop platform's technical details.


Overall, the total technological complexity of the Nop platform will be limited to the level that a single developer can fully grasp all details (after all, its development is handled by a single developer).


## Performance?

Can the Nop platform handle high concurrency and complex applications?

The performance of the Nop platform exceeds that of mainstream frameworks like Spring. This advantage stems from two aspects:


### 1. Software Construction Principles

The Nop platform is based on reversible computation theory, which heavily utilizes compile-time transformations and just-in-time compilation. As a result, many traditional frameworks require complex judgment logic to be executed at runtime have been moved to the compile time in the Nop platform. Complex model abstractions do not lead to performance loss.


### 2. Implementation Advantages

The Nop platform is a completely new framework built from scratch over the past few years. It can leverage the lessons learned from other frameworks' long-term development, focusing on optimizing the execution path of the normal code. In contrast, traditional frameworks were born many years ago and are burdened by compatibility issues, which require additional redundant actions at runtime.

For example:
- The Nop GraphQL engine provides simultaneous support for both GraphQL and REST interface services, using JSON format for data encoding/decoding in the REST implementation.
- The SpringMVC framework includes multiple coding strategies due to compatibility considerations, leading to extensive redundant checks at runtime.
- Traditional web frameworks cannot simultaneously support both GraphQL and REST protocols. If attempting to expose GraphQL services via `graphql-java`, a significant amount of repetitive data transformation and interface adaptation work is required.

Another aspect:
- Existing workflow engines, IoC engines, ORM engines, Web engines, rule engines, batch processing engines, etc., are all designed and implemented by different open-source teams.
- These engines are then encapsulated into a unified framework by Spring.
- This results in substantial concept redundancy at the bottom layer. When working together, they also require excessively long adaptation interfaces for coordination.

When extending, we must learn about various frameworks' different extension mechanisms and manage potential implementation conflicts between them.
In the Nop platform, all frameworks use the same XDef meta-model and are managed on the XDSL level using a unified Delta mechanism for customized extensions. This minimizes information exchange overhead and allows seamless integration of multiple engines with minimal cost.


## Compatibility With Third-Party Standards


### Can the Nop platform support various technical standards?

The Nop platform's compatibility with third-party standards is as follows:

All core functionalities of the Nop platform do not rely on third-party open-source components. For example:
- Its XML parsing is self-implemented within the `nop-core` module using an `XNodeParser`.
- Its GraphQL implementation does not depend on external libraries like `graphql-java`. Instead, it uses a self-developed `GraphQLDocumentParser` and implements its own GraphQLEngine based on innovative designs.

Some may wonder: Without relying on third-party libraries, how can the Nop platform ensure compatibility with external standards like the GraphQL protocol?

The Nop platform's response is straightforward:
- **Cannot achieve 100% compatibility.** There's no need to do so either.

Here are the reasons:


### 1. Implementation Cost

Since the entire Nop platform's core code is written by myself, the choice of technical strategies is strictly limited to what I can personally handle. Therefore, in terms of standardization:
- The Nop platform only uses a subset of standards that align with reversible computation theory and does not attempt to cover all details.
- For example, the Nop GraphQL engine provides standard GraphQL services but does not support all aspects of the GraphQL specification.



For practical use cases:
- If you need advanced Excel functions, the NopReport engine only includes a limited set of commonly used Excel-compatible expressions.
- Custom extensions are recommended for specific requirements (the `nop` core modules include various abstract layers' plugin interfaces that can be extended as needed).





The Nop platform does not rely on any third-party libraries or frameworks. Everything is self-developed from scratch:
- Its XML parsing is handled by an `XNodeParser` in the `nop-core` module.
- Its GraphQL implementation uses a custom `GraphQLDocumentParser` and a self-created `GraphQLEngine`.
- Its database connectivity is managed through its own ORM layer, without relying on popular libraries like Hibernate.

Some may question: Without using third-party tools like `graphql-java`, how does the Nop platform ensure compatibility with external standards such as the GraphQL protocol?

The answer is clear:
- **No 100% compatibility guaranteed.** There's no need for it either.

Here are the reasons:



Since the entire Nop platform's core code is written by myself, the choice of technical strategies is limited to what I can personally implement. Therefore:
- The Nop GraphQL engine only provides standard GraphQL services but does not support all aspects of the GraphQL specification.
- Compatibility with third-party tools like `graphiql` or `SpringCloud` is possible but unnecessary for most use cases.



For practical needs:
- If advanced Excel functionality is required, it's better to implement custom logic rather than relying on external libraries.
- Custom plugins are recommended for specific requirements (the `nop` core modules include various plugin interfaces that can be extended as needed).





The Nop platform avoids using third-party frameworks and instead builds everything from scratch:
- Its workflow engine is self-developed.
- Its dependency injection container is entirely custom.
- Its ORM layer is written in-house.

This approach has both advantages and disadvantages:


- No dependency on external libraries, reducing potential security risks.
- Full control over the codebase allows for highly customized solutions.
- The use of a unified Delta mechanism at the XDSL level enables efficient extension and customization.


- Initial development is resource-intensive, requiring significant time and effort.
- Compatibility with existing tools and ecosystems may be limited.



### 1. Common Technical Standards and Implementation Paths
General technical standards typically provide multiple implementation paths for the same functionality, but their safety and usability may vary significantly. In practice, we usually recommend a limited number of best practices due to compatibility considerations, even though many suboptimal implementations are often retained within standards for backward compatibility reasons. This can unintentionally introduce security vulnerabilities.


## 2. Developer Background and Requirements
As a developer with extensive experience in the development of large-scale, high-end complex software systems, I have high requirements for safety, compliance, etc. Therefore, Nop platform typically only retains functionality that is considered safe and aligns with current best practices. For example, XML standards may contain custom Entity definitions, but these are often easy targets for introducing security bugs, as evidenced by well-known vulnerabilities in major corporations like Amazon, Google, Tencent, and Alibaba.


## 3. Security Enhancements in Nop Platform
The Nop platform has undergone significant fortification in terms of security. For instance:
- The length of GraphQL statements, the number of operations included, and allowed nesting levels are all subject to default restrictions that can be configured via parameters.
- Default configurations ensure a high safety standard even in scenarios where no additional settings are applied.
- The platform is designed to resist common injection attacks and resource exhaustion attacks.


The most critical point is that innovative design should not be constrained by existing forms. Reversible Computing Theory represents the next-generation software construction theory, and the Nop platform is built upon this theory as a reference implementation. This means it focuses on currently uncovered technical frameworks and design spaces.


The core philosophy behind the Nop platform is not to become another popular framework that dilutes its own value. Instead, it aims to serve as a reference implementation of Reversible Computing Theory. By doing so, it clarifies certain concepts within the theory while incorporating modern industry knowledge and best practices.


The platform's design avoids becoming a generic, user-friendly development framework that sacrifices depth for simplicity. Instead, it prioritizes architectural purity and allows for fine-grained control over its operations. This makes it appealing to developers who value flexibility and want to stay ahead of the curve in terms of software architecture.


The platform's component development and progress tracking are documented in detail within the [README.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/README.md) file, with predictions that these components will be fully developed by 2025 and published to the Maven repository.


As stated by Linus Torvalds, "Talk is cheap; show me the code." The Nop platform is fully open-source, with its source code available for download. This allows developers to troubleshoot issues directly within the codebase rather than relying on documentation or external communication.


Interestingly, many domestic developers are more interested in making money through the platform rather than delving into its technical details. This mindset is not aligned with the hacker spirit of collaboration and knowledge sharing.


From a global perspective, the term "reversible computing" may sound too abstract or highfalutin for some audiences, especially those unfamiliar with cutting-edge research in software architecture. However, it represents a significant step forward in addressing long-standing issues in software development.

Here is the English translation of the provided Chinese technical document:

---

## 理解可逆计算理论

### 作者背景
Here’s an explanation: The author, although not one to bypass walls, does read various open-source software's source codes. It is generally better to understand related technical content through source code rather than others' articles. Additionally, the author’s academic background is in theoretical physics, having received good theoretical training and regularly reading related literature.

### Origin of Reversible Computation Theory
The origin of reversible computation theory stems from the author's conscious effort to introduce physical science concepts into software engineering. It is precisely because his thoughts are not constrained by traditional ideas that he was able to invent some original technical solutions.

### Current Status of Abstract Knowledge Among Programmers
Currently, the understanding of abstract concepts among general programmers is limited to the level of university undergraduate education, generally remaining within Newton and Leibniz's era. Without efforts to enhance one's ability to understand abstract knowledge, even if all computer-related information on the internet were comprehensively understood, it would still be akin to a drop in the ocean.

### Normal Phenomenon: Difficulty Understanding Reversible Computation Theory
It is a normal phenomenon that some students find it difficult to understand reversible computation theory. This is because most people do not truly need theoretical explanations; they work within established frameworks and guidelines set by others. It is only natural for one's mind to fill in the gaps based on personal understanding.

### Example: Abstract Relational Model
For instance, the majority of people don't need to understand the abstract relational model to use a relational database effectively. It’s like operating a car without fully comprehending its internal working principles.

### Nop Platform's Approach
The Nop platform takes a unique approach by creating its own set of guidelines. For the general populace, this means that even "divine truths" (sarcasm intended) can be presented in an understandable manner. If you're genuinely interested, then examining the code is recommended.

---

