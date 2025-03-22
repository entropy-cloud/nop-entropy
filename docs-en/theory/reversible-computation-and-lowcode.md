# From Reversible Computing to Low-Code

In 2020, the term "Low-Code" became a frequent buzzword in mainstream tech media. Behind this trend, major companies like Microsoft/Azure, Amazon, Alibaba, and Huawei have been actively entering the low-code development space, each launching their own products. While smaller players may not be household names, every company with a technical footprint is making moves in this area.

According to Gartner's 2019 Low-Code Report, the future looks bright for Low-Code:

> By 2024, 75% of large enterprises will use at least four different low-code development tools for IT application development and citizen development (non-IT backgrounds). By 2024, low-code application development will handle 65%+ of all application development activities.

Transitioning to a Low-Code platform means that software is moving into an industrialized production phase. Since the mid-20th century, attempts at this kind of shift have become increasingly common but also increasingly criticized for their lack of success. Why does the current wave of Low-Code development offer something different? What gives it the ability to achieve what previous generations could not? This article aims to explore these questions from a perspective rooted in Reversible Computing.

For those unfamiliar, Reversible Computing refers to a theoretical framework that explores the concept of reversibility—operations that can be reversed—in computing processes. For more on this topic, please refer to [Reversible Computing: The Next Generation of Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026).

---

## 一. What is Low-Code?

Low-Code, at its core, refers to "Low Code" in both name and concept. In the software development process, it significantly reduces the proportion of code-related activities. Clearly, the term "Low-Code" is merely a wishful thinking expression, reflecting our desire to drastically reduce the amount of code involved. This reduction translates into less work, faster delivery, and more stable systems. From the business perspective, it also leads to lower costs, higher profits, and broader market opportunities. As a naive yet beautiful concept, **Low-Code's essence is similar to Fast-Code/Clean-Code**, in that it essentially lacks any real substance—or perhaps its essence is simply a description of an ideal phenomenon. While other indicators may vary, Fast-Code will always outperform Slow-Code, and under the premise of offering identical functionality, Low-Code will always be preferred over High-Code.

Compared to Case tools, model-driven development, and generative programming—terms that may seem grand and complex—the concept of Low-Code is more concrete and approachable. This is especially true when combined with visualization drag-and-drop capabilities for developers, end-to-end automation, and other marketing-driven benefits that emphasize economic efficiency. These aspects are far easier to sell to non-technical stakeholders.

In essence, Low-Code resembles a flag. Everything related to Code—both its practices and its philosophy—can be seen as falling under its influence. We can confidently assert that Low-Code is moving in the right direction along the path of technological advancement. Imagine 500 years into the future: will people still be writing code? Probably not, assuming humanity hasn't gone extinct by then. Moreover, even if they were, they likely wouldn't be writing business logic. Belief in Code being replaced by Low-Code is a clear indicator of someone lacking future vision. In the era of smart societies, intelligence will increasingly shift from human to machine, and Code will gradually cede its role. Is Low-Code then the key to this transformation?

The current state of Low-Code platforms still lacks definitive characteristics. Existing products developed by Low-Code vendors are rarely considered best practices, even by themselves. To a certain extent, the marketing promises of Low-Code may be overblown. If Low-Code truly has the potential to revolutionize both technology and business fields, then we must delve deeper and make more revolutionary explorations.

---

## 二. Does Low-Code Require Visualization?

Low-Code reduces coding work in two primary ways:

1. **Substantially reducing code volume for specific business needs**  
   By focusing on non-coding tools and environments, the amount of code that needs to be written is minimized.

2. **Translating traditional coding tasks into non-coding operations**  
   For example, transforming repetitive, manual coding tasks into automated workflows or using visual drag-and-drop interfaces for development.

Visualization plays a crucial role in Low-Code platforms, particularly in the second aspect mentioned above. Visualization design has two main advantages:

1. **Reduced programming knowledge required**: Users can design applications without needing deep programming expertise.  
2. **Stronger constraints and reduced error rates**: Visualization tools inherently impose structure, making it easier to avoid mistakes.

Visualization is undeniably a key differentiator for Low-Code platforms today. While it may not always be necessary—depending on the simplicity of the task—it provides significant advantages in terms of usability and efficiency. For instance, using specialized domain languages (like Markdown for content creation) can often achieve desired results without traditional coding.

However, this is not to say that visualization is an absolute requirement. If a straightforward abstraction layer suffices, there's no need for full-fledged visualization tools. Non-technical users can still develop basic applications with minimal or even no programming involved.

---

## 三. The Current State of Low-Code

The current state of Low-Code development can be summarized as follows:

1. **Lack of clear definition**: While Low-Code platforms are increasingly popular, their exact nature and capabilities remain ambiguous.
2. **Overly inflated marketing claims**: Many promises made by vendors lack substance or deliver only partial benefits.
3. **Challenges in implementation**: Even when organizations adopt Low-Code, realizing its potential can be difficult.

The question remains: What sets Low-Code apart from previous attempts at automation and abstraction? What makes it capable of achieving what those efforts could not?

Low-Code's potential lies in its ability to abstract the complexities of software development. It reduces the need for deep technical expertise, enabling non-technical users to build applications while maintaining a degree of control over their functionality.

However, the gap between marketing hype and actual delivery continues to widen. If Low-Code truly wants to revolutionize both technology and business, it needs to move beyond mere abstraction and automation toward a more comprehensive transformation.

---

## 四. Revolution or Evolution?

The term "Low-Code" has become synonymous with both revolution and evolution in the software development landscape. On one hand, it represents a radical shift away from traditional coding practices, promising to empower non-technical users and democratize application development.

On the other hand, it is often viewed as an incremental improvement over existing tools, offering visual interfaces and automation without fundamentally changing the way software is built or managed. In this sense, Low-Code may be less about revolution and more about evolution.

The ultimate question is whether Low-Code can deliver on its promises of efficiency, scalability, and innovation while maintaining its core principles of simplicity and usability.

---

# 结语


3. Cannot fix like code's inheritance and interface injection mechanisms for design tools and products.

Reversible computation theory proposes its own solution to the above issues and points out that visual design is a natural consequence of reversibility:

```
DSL <=> Visual Interface
```

Domain-specific language (DSL) has multiple expression forms (textual and graphical), which can be bidirectionally converted. DSL includes standard encapsulation and delta mechanisms for second-generation encapsulation. The designer itself is not fixed but is dynamically generated by a meta-designer combined with DSL's structural information, enabling co-evolution between the designer and the application description.


## 三. LowCode要怎么Low?

If we view software production as an information processing task, then the strategy discussed in the previous section for LowCode (Low-code) can be summarized as:

1. Reduce information expression  
2. Use forms other than Code

By examining existing LowCode products on the market, we can observe several interesting phenomena. For example, take ivx.cn as a case: it targets non-professional programmers and allows them to design interfaces and backend logic without programming. This so-called No-code approach indeed reduces the technical requirements for users, relying not only on the visualization provided by the designer but also on **actively restricting the user's design space** and abandoning many of the conveniences and flexibilities present in traditional programming. This is achieved by **lowering the information density of the design space**, thereby reducing the technical capability required.

For instance, in standard programming practices, we deal with various variables' scopes and relationships between them. However, in ivx, none of these concepts exist; it resembles global variables. In programming, we can write complex chained calls like `a.b(g(3)).f('abc')`, but in ivx, each line represents a single action. Traditional programming's numerous features, such as writing complex logic in a single line, are absent in ivx, making each line express a single operation.

From this perspective, many of the features we are accustomed to in programming seem unnecessary. The idea that "doing one thing leads to one outcome" simplifies entry but also limits the platform's capabilities, restricting it from participating in more complex and high-value production activities.

However, this LowCode approach also has its limitations. If LowCode is supposed to assist professional development, it must, at some level, **essentially reduce information expression**. To achieve this, several strategies are known:

1. Systematic component reuse  
2. Model-driven approaches based on domain abstraction  
3. Holistic design from start to end-to-end lifecycle


## 3.1 Component Reuse

Component reuse is a general strategy for reducing system complexity: decompose complex objects and identify reusable components. LowCode differs in that it advocates for systematic component reuse, such as providing a complete, ready-to-use component library without requiring developers to collect components from various sources. It ensures that all components can be properly combined and provides adequate documentation and support, while also allowing the production and consumption processes of components to be managed within the LowCode platform, such as through component markets like those provided by companies like JetBrains (e.g., web-types standard: [https://github.com/JetBrains/web-types](https://github.com/JetBrains/web-types)).

Component reuse is an application of reductionism. Reductionism's most successful example is atomism. The world of atoms, as described by atomism, is composed of countless atoms, each with specific properties that together explain the world. However, knowing atoms doesn't mean we fully understand the world, as much information resides in atomic interactions and combinations.

The challenge faced by current component technology lies in demands for dynamicity, combinability, and adaptability to environments, which require more advanced solutions than simple static encapsulation. For example, a component library might need to support multiple runtime environments (desktop, mobile, etc.), and LowCode platforms must provide standardization for such component descriptions.


## 3.2 Model-Driven Development

Quantum mechanics teaches us that in our universe, information is conserved. If we invest minimal information into a system, it can generate substantial valuable outcomes. For instance:

1. The apparent diversity of results hides a simple logical core, and  
2. Combining diverse information sources (global knowledge) allows for **automatic derivation** of numerous derived outcomes.

To construct such a non-linear (input-output ratio not proportional), we first need domain abstraction and then build a system driven by this abstraction. This system can be minimally information-driven, allowing it to produce maximally valuable outputs.

> Component reuse belongs to the application of reductionism. Reductionism's most successful example is atomism. The world of atoms, as described by atomism, is composed of countless atoms, each with specific properties that together explain the world. However, knowing atoms doesn't mean we fully understand the world, as much information resides in atomic interactions and combinations.

> Component reuse belongs to the application of reductionism. Reductionism's most successful example is atomism. The world of atoms, as described by atomism, is composed of countless atoms, each with specific properties that together explain the world. However, knowing atoms doesn't mean we fully understand the world, as much information resides in atomic interactions and combinations.

> Component reuse belongs to the application of reductionism. Reductionism's most successful example is atomism. The world of atoms, as described by atomism, is composed of countless atoms, each with specific properties that together explain the world. However, knowing atoms doesn't mean we fully understand the world, as much information resides in atomic interactions and combinations.

# Domain Abstraction

Domain abstraction is about discovering the essential elements of a domain and their relationships, which essentially means **reducing unnecessary requirements**. After spending many years in a specific domain and accumulating sufficient domain knowledge, we realize that many requirements are unnecessary details. We can then choose to focus on high-value tasks.

By codifying domain knowledge into a domain-specific language, we can significantly reduce the amount of information expressed. For example, the Bootstrap CSS framework essentially reifies the front-end structure by limiting colors to success, info, warning, and danger, sizes to sm, md, lg, and layouts to just 12 possible options. After getting used to Bootstrap, we often find that we don't need as many color combinations or size variations in most cases. By actively restricting our capabilities, we actually gain freedom—reducing unnecessary decisions while achieving style consistency.

# Model Abstraction

Model abstraction typically follows a multiplicative approach for simplifying complexity. For instance, if 100 possible scenarios can be described by combining two basic elements through cross-combination (10x10), defining 20 basic elements and one combination rule is sufficient. The true value of a model lies not only in its ability to simplify but also in the **global knowledge** it provides about the system. For example, in finite state machines, `a->b->c->a` ensures that the system doesn't diverge but inevitably returns to a known state.

In self-similar nested models, the whole is composed of parts, and magnifying the structure brings us back to familiar descriptions.

# Global Knowledge

Global knowledge significantly facilitates information propagation and deduction. For example, if all component instances at runtime have unique names and stable location paths, we can leverage this for **data auto-binding** (aligning data items with Store's data items), tracking, etc. Traditional programming activities heavily rely on general-purpose, domain-agnostic programming languages and libraries, which lack global consistency. This often leads to broken inference chains, requiring manual intervention by developers.

Modern software design emphasizes convention over configuration (`convention over configuration`), which essentially means preserving global knowledge while minimizing explicit configurations.

# Model Description

Model-provided knowledge typically serves a variety of purposes, often involving diverse deduction processes. For example, using data models, we can automatically generate database definitions, migration scripts, interface descriptions, data access code, and API documentation. Traditional programming approaches treat each line of code as isolated, often leading to repetitive, error-prone, and inefficient code.

# LowCode Platforms

LowCode platforms primarily aim to provide a "better" programming model by either embedding or integrating domain-specific services/templates. This so-called "better" approach aligns with the "wise man sees the same in everyone's court" principle. Some LowCode platforms act as supplements to existing programming languages, enhancing syntax features and enabling automatic encapsulation of React/Redux into a class-based Vue-like data-driven model. Others introduce more stringent assumptions, reducing the scope but enhancing control, such as simplifying frontend templating and automatically binding templates with backend models.

LowCode platforms generally include form models, rule models, workflow models, BI chart models, etc., along with common development models. They often provide rich front-end templates and big screen displays for material resources. Many also offer domain-specific service support, such as IoT data processing services.

## 3.3 Domain Integration

From a thermodynamic perspective, information is always dispersed. As information flows through system boundaries, it may be lost, distorted, or conflated. In routine work, a significant portion of our efforts isn't about innovation but rather about formatting, validating completeness, and adapting interfaces—essentially translational or convertive work.

Our technical environment itself is in a state of continuous evolution, making it a persistent source of chaos. Setting up build environments, ensuring continuous integration remains a manual process. New developers often download source code only to find compiling packaging unsuccessful. Complexities like mobile, cloud, and distribution multiply the non-functional requirements, elevating their importance while increasing the required knowledge base.

# Monitoring and Operations

Monitoring and operations have become indispensable components of online software, with data analysis being a core part. The need for monitoring and operations often forces us to bury points in the code (`埋点`) for tracking usage and performance. This is especially true for LowCode platforms trying to provide comprehensive solutions.

Current LowCode platforms typically offer an all-encompassing solution covering input/output points, the entire development and publishing lifecycle, and global knowledge integration. However, this one-size-fits-all approach can be a double-edged sword. While it simplifies implementation and reduces development costs, it may limit flexibility and require significant investment in maintaining a uniform structure.

# LowCode vs Model-Driven

## 四. LowCode与模型驱动有区别吗？
From the current technological trajectory, the LowCode platform's adopted strategy and principles are fundamentally similar to traditional model-driven approaches. The primary difference lies in the context of modern technological environments, where the emergence of new technologies simplifies certain aspects of development, while the widespread adoption of open-source technologies expands the range of accessible technical elements and positions available for manipulation.

In this new era, new demands have emerged, particularly in terms of application scope. Unlike traditional model-driven systems, LowCode platforms aim to transcend component-level, granular, or systemic reuse capabilities by offering a comprehensive practice that integrates multiple aspects of development.

From a programming paradigm perspective, traditional model-driven approaches generally align their overall development logic with object-oriented programming paradigms, focusing on adapting the entire development process to fit within such frameworks. In contrast, with the rise of functional programming, multi-paradigm programming has become inevitable, making multi-paradigm solutions a natural choice.

While the target vision for reusable components may be broad, the operational approach in traditional model-driven systems often lacks finesse. It tends to offer only rough operational pathways, relying heavily on basic parameterization without delving into more sophisticated parameterization strategies. This simplicity, however, can become a double-edged sword, as it may fail to account for intricate interdependencies between parameters.

From a reversible computation theory perspective, I believe that LowCode platforms differ from traditional model-driven approaches in their reliance on delta-based model driving and an evolutionary embrace of models. For any given model, we can pose simple questions: Can it be customized? How can it be customized? Another critical question is how to decompose the model into its constituent components for secondary abstraction. Furthermore, we must establish criteria for determining whether a model is reversible: Does it allow for inversion?

In terms of applicable methodologies, as translation technologies evolve and compilers become more widespread, performing modifications at the language level is no longer unimaginable. In many cases, developers have already transitioned to using traditional compilation techniques. By incorporating this method into our arsenal, we can address a variety of topological structure challenges related to information dissemination over broad domains.

The cornerstone of abstraction is parameterization. For example:

```
F(X1, X2, X3, ....)
```

From a rough perspective, the complexity of a model is often measured by the number of parameters required. If parameters are few, the system's complexity is low; if parameters are numerous, complexity increases. For instance, if parameters X1, X2, and X3 are independent, their interaction can be modeled as an exact cross-product corresponding to their count. However, real-world complexities often exceed this, necessitating interdependencies that cannot always be captured by simple parameterization.

A particularly interesting aspect emerges when considering the standardization of constraints for a given model (X1, X2, X3). If parameters become sufficiently complex, they may even form a cohesive whole, allowing us to describe and analyze them using a domain-specific model (M). This enables us to understand M without relying on F.

From a foundational logic perspective, our understanding operates at multiple levels. On one level, we can grasp the overall structure; on another, we delve into specific components. An ideal scenario is when these components are independent, forming a complete system that mirrors a domain-specific model (M). This allows us to describe M without F.

The ultimate goal of parameterization is `f(DSL)`, where all elements contribute to forming an organic whole, thereby reflecting the overall logic of the domain-specific language (DSL). Of course, an effective DSL can only describe specific business aspects. For example:

```
Biz = App - G(DSL1)
```

After abstracting DSL1, Biz becomes an independent research object. This mirrors the physical world, where we can model Gauss's model as a zero-level solution and then rework the original equation into a correction term.

From a foundational technical logic standpoint, many underlying structural issues resemble familiar challenges in traditional compilation technologies. However, with the advent of cloud-based infrastructure, outsourcing stability has become feasible for small teams. This allows development capabilities to adapt to traditional large-scale software development practices without compromising agility.

The new era has not only brought new demands but also introduced new opportunities. Unlike traditional model-driven systems, LowCode platforms aim to transcend component-level, granular, or systemic reuse capabilities by offering a comprehensive practice that integrates multiple aspects of development.

LowCode can be seen as an evolution following component technology, attempting to move beyond component-level, coarse-grained, and systemic reuse toward a holistic approach that encompasses all stages of development.

1. From a programming perspective, traditional model-driven systems generally align their overall development logic with object-oriented programming paradigms, focusing on adapting the entire development process to fit within such frameworks. In contrast, with the rise of functional programming, multi-paradigm programming has become inevitable, making multi-paradigm solutions a natural choice.
2. While the target vision for reusable components may be broad, the operational approach in traditional model-driven systems often lacks finesse. It tends to offer only rough operational pathways, relying heavily on basic parameterization without delving into more sophisticated parameterization strategies.

From a reversible computation theory perspective, I believe that LowCode platforms differ from traditional model-driven approaches in their reliance on delta-based model driving and an evolutionary embrace of models. For any given model, we can pose simple questions: Can it be customized? How can it be customized? Another critical question is how to decompose the model into its constituent components for secondary abstraction. Furthermore, we must establish criteria for determining whether a model is reversible: Does it allow for inversion?

In terms of applicable methodologies, as translation technologies evolve and compilers become more widespread, performing modifications at the language level is no longer unimaginable. In many cases, developers have already transitioned to using traditional compilation techniques. By incorporating this method into our arsenal, we can address a variety of topological structure challenges related to information dissemination over broad domains.

The cornerstone of abstraction is parameterization. For example:

```
F(X1, X2, X3, ....)
```

From a rough perspective, the complexity of a model is often measured by the number of parameters required. If parameters are few, the system's complexity is low; if parameters are numerous, complexity increases. For instance, if parameters X1, X2, and X3 are independent, their interaction can be modeled as an exact cross-product corresponding to their count. However, real-world complexities often exceed this, necessitating interdependencies that cannot always be captured by simple parameterization.

A particularly interesting aspect emerges when considering the standardization of constraints for a given model (X1, X2, X3). If parameters become sufficiently complex, they may even form a cohesive whole, allowing us to describe and analyze them using a domain-specific model (M). This enables us to understand M without relying on F.

From a foundational logic perspective, our understanding operates at multiple levels. On one level, we can grasp the overall structure; on another, we delve into specific components. An ideal scenario is when these components are independent, forming a complete system that mirrors a domain-specific model (M). This allows us to describe M without F.

The ultimate goal of parameterization is `f(DSL)`, where all elements contribute to forming an organic whole, thereby reflecting the overall logic of the domain-specific language (DSL). Of course, an effective DSL can only describe specific business aspects. For example:

```
Biz = App - G(DSL1)
```

After abstracting DSL1, Biz becomes an independent research object. This mirrors the physical world, where we can model Gauss's model as a zero-level solution and then rework the original equation into a correction term.

From a foundational technical logic standpoint, many underlying structural issues resemble familiar challenges in traditional compilation technologies. However, with the advent of cloud-based infrastructure, outsourcing stability has become feasible for small teams. This allows development capabilities to adapt to traditional large-scale software development practices without compromising agility.

The new era has not only brought new demands but also introduced new opportunities. Unlike traditional model-driven systems, LowCode platforms aim to transcend component-level, granular, or systemic reuse capabilities by offering a comprehensive practice that integrates multiple aspects of development.

LowCode can be seen as an evolution following component technology, attempting to move beyond component-level, coarse-grained, and systemic reuse toward a holistic approach that encompasses all stages of development.

# Programmer's Perspective on Software Systems
A programmer stands in a godlike perspective when viewing software systems. He does not passively receive facts from the system but actively shapes them.

## The Light of Knowledge
The gods said, "Let there be light," and there was light. Programmers can design their own rules (Rule/Law) to govern all elements' behavior.

## Misconceptions About LowCode
Some believe that LowCode is limited to prototyping from 0 to 1 and eventually requires traditional refactoring like DDD. This likely stems from the misconception that LowCode can only align business logic to a few built-in models, unable to adapt to domain-specific requirements. From the reversible computation perspective, DSLs should be customizable and extendable. Tools like JetBrains' MPS support this by providing a domain-specific workspace for development and execution.

## Neutral Technology?
LowCode aims for higher-level reuse but inadvertently creates ecological divides through unintended dependencies it attempts to conceal. The same logic applies: after using Java, TypeScript remains unusable as a direct implementation.

### Strategies for Neutral Technology

1. **Microservices**  
   Using neutral communication protocols to isolate components is a mature and standardized approach. With optimizations in the kernel network stack (e.g., RDMA, DPDK), Sidecar patterns enable inter-process communication with extreme performance.

2. **Virtual Machines**  
   GraalVM excels by compiling diverse tech stacks into a single environment. Its potential lies in bridging across technology boundaries for collaborative optimization. However, in microservices setups, Java and JavaScript remain incompatible for unified optimization.

3. **Domain Languages**  
   Domain-specific languages (DSLs) offer a cost-effective way for ordinary programmers to control their own development. Lowering the complexity of interpreters, for example, allows function calls to become straightforward implementations of daily operations.

## Reinterpreting Implementation
If each implementation technique is viewed as a distinct coordinate system, using multiple techniques to implement logic effectively becomes a matter of translating that logic into various systems. Mathematically, tensor representations handle coordinate-independent principles by linking different coordinate systems through reversible transformations.

Thus, neutral technology doesn't imply binding to a specific coordinate system but allows translation between them. Reversible computation ensures this without forcing a single unified design. Predefined reversible mechanisms can extract and transform information from the system as needed, whether using Protobuf, JSON-RPC, or GRPC.

## The Future of LowCode
Reversibility is the key to solving many challenges. If every part of a large model is locally reversible, the entire system becomes reversible. This aligns with LowCode's future, which shouldn't be limited to a single product or SAAS solution but should enable information exchange across ecosystems.

## Zero as a Coordinate
In neutral systems, zero remains zero across all coordinate systems. This simplifies operations like filtering and transformations, allowing many algorithms to function independently of the underlying system.

## The Power of Reversibility
Reversibility is the foundation for solving complex problems. If every local part of a model is reversible, the entire system becomes manageable. This principle extends beyond programming languages, enabling cross-system optimization without runtime dependencies.

## Graph Theory and LowCode
LowCode's future lies not in single-product solutions but in bridging across ecosystems through information exchange. This approach respects diverse needs while maintaining reversibility.

## Neutral Systems and Zero
Neutral systems introduce a zero that remains consistent across all coordinate systems. This simplifies operations like filtering and transformations, enabling many algorithms to function seamlessly regardless of the underlying system.

## The Future of Reversibility
Reversibility is the cornerstone for solving complex problems. If every part of a model is locally reversible, the entire system becomes manageable. This principle applies beyond programming languages, enabling cross-system optimization without runtime dependencies.

# LowCode for Wide-Ranging Work

LowCode is designed to handle a broad range of tasks, and it definitely retains the capability of being Turing complete. However, the core domain models provided by LowCode do not require Turing completeness. In fact, Turing completeness is more suited for edge cases.

Turing completeness can be achieved through embedded DSL languages. While this approach is cost-effective, especially when leveraging IDE support for language-specific enhancements, it often focuses solely on correct expression of ideas without enforcing structure adherence for deviations from the DSL's format. This means users can write DSLs in unconventional ways, leading to semantic consistency but not necessarily syntactic compliance with the original DSL requirements. Consequently, modeling transitions between DSL structures becomes challenging.

JSX presents an interesting extension approach because it allows obtaining virtual DOM nodes during execution, providing significant control over structure. However, TypeScript faces a clear issue: no concrete compilation phase exists. JSX's compilation process is highly complex, resulting in specific VDOM nodes at runtime. During the compilation phase, we need to analyze code structure, but due to the stoppage problem, this analysis is generally difficult. Therefore, Vue templates, which allow for compile-time analysis of code structure, are crucial.

Turing completeness does not equate to information completeness. Reverse analysis of information is lacking across all mainstream technologies. Traditional coding habits have conditioned developers to undervalue automatic analysis of program structures, as such tasks were historically the domain of compilers. However, LowCode's potential in applying this technology at higher levels (e.g., application layer) necessitates broader adoption and education.

In my view, traditional code uses a logic that matches the operation of machines. All logical expressions are adapted to fit either a Turing machine or a lambda calculus execution model. However, domain-specific logic has its own distinct representation. Its components have their own methods of operation, which can be understood directly without being forced to fit into a specific Turing machine's operational steps. A language is a world; different worlds have different worldviews. Low-Code does not mean that people who don't understand logic are writing code (even though many products attempt to create this illusion). In reality, many mathematicians may not write programs, but they are very familiar with logic. In 5–10 years, the market will see several 40+ year-old former programmers who may no longer be proficient in the latest development technologies. Will they still be able to develop business logic using a higher-level logical framework?

Low-Code platforms based on reversible computation theory, such as NopPlatform, have been open-sourced:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible computation principles and Nop platform introduction on Bilibili](https://www.bilibili.com/video/BV14u411T715/)

