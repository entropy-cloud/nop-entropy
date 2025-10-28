# Reversible Computation: A Next-Generation Theory for Software Construction

As we all know, computer science is built upon two foundational theories: the **Turing Machine**, proposed by Alan Turing in 1936, and the **Lambda Calculus**, published by Alonzo Church earlier that same year. These two theories laid the conceptual groundwork for what we call **Universal Computation**. They describe two technical paths that, while possessing the same computational power (Turing completeness), are poles apart in their form and structure. If we view these theories as two extremes of the world's fundamental nature as revealed by a higher power, might there be a more moderate and flexible middle path to the shores of universal computation?

Since 1936, software, the core application of computer science, has been in a state of continuous conceptual revolution. Countless programming languages, system architectures, design patterns, and methodologies have emerged. Yet, the fundamental principles of software construction have not escaped the boundaries originally set by these two foundational theories. If we were to define a new theory for software construction, what unique concepts could it introduce? What intractable problems could it solve?

In this article, I propose that by naturally introducing a new core concept—**reversibility**—to the foundations of the Turing Machine and Lambda Calculus, we can form a new theory of software construction: **Reversible Computation**. Reversible Computation provides a higher level of abstraction than the mainstream methods currently used in the industry. It can significantly reduce the intrinsic complexity of software and remove the theoretical barriers to coarse-grained software reuse.

The inspiration for Reversible Computation comes not from computer science itself, but from theoretical physics. It views software as an abstract entity in a continuous process of evolution, described by different operational rules at different levels of complexity. It focuses on how the minute deltas generated during this evolution propagate and interact in an orderly manner within the system.

Section I of this article will introduce the basic principles and core formula of Reversible Computation. Section II will analyze its connections to and distinctions from traditional software construction theories like components and model-driven architecture, and discuss its applications in software reuse. Section III will deconstruct innovative technologies like Docker and React from the perspective of Reversible Computation.

***

## I. The Basic Principles of Reversible Computation

Reversible Computation can be seen as an inevitable consequence of applying Turing computation and Lambda Calculus to model our real world of finite information. We can understand this through a simple physical analogy.

First, the Turing Machine is a machine with a fixed structure. It has a finite, enumerable set of states and can only execute a limited number of instructions, but it can read from and write to an infinitely long tape. Consider a personal computer: its hardware capabilities are fixed at the factory, but by installing different software and feeding it different data files, it can automatically produce arbitrarily complex outputs. The computational process of a Turing Machine can be formally written as:

> Target Output = Fixed Machine(Infinitely Complex Input)

In contrast, the core concept of Lambda Calculus is the function. A function is a small computing machine, and the composition of functions is still a function. This means that by recursively combining machines with other machines, we can create ever more complex machines. The computational power of Lambda Calculus is equivalent to that of a Turing Machine. This implies that if we are allowed to continuously create more complex machines, we can achieve any arbitrarily complex output, even with a constant input like 0. The computational process of Lambda Calculus can be formally written as:

> Target Output = Infinitely Complex Machine(Fixed Input)

As you can see, both computational processes can be expressed in the abstract form `Y = F(X)`. If we interpret `Y = F(X)` as a modeling process—where we try to understand the structure of the input and the mapping between input and output to reconstruct the output in the most economical way—we find that both the Turing Machine and Lambda Calculus make assumptions that are impossible to meet in reality. In the real physical world, human cognition is always limited. All quantities must be divided into known and unknown parts. Therefore, we need the following decomposition:

> Y = F(X)
> &nbsp;&nbsp;&nbsp;= (F₀ + F₁) (X₀ + X₁)
> &nbsp;&nbsp;&nbsp;= F₀(X₀) + Δ

By rearranging the symbols, we arrive at a more broadly applicable computational model:

> **Y = F(X) ⊕ Δ**

In addition to the function application `F(X)`, a new structural operator `⊕` appears. It represents a composition operation between two elements, not simple numerical addition. It also introduces a new concept: the **Delta (Δ)**. The peculiarity of a Delta is that it must contain some form of negative element. The result of combining `F(X)` with `Δ` is not necessarily an "increase" in the output; it could very well be a "decrease."

In physics, the necessity of the Delta and the fact that it contains inverse elements are self-evident. This is because physical modeling must account for two fundamental facts:
1.  The world is subject to uncertainty; noise is always present.
2.  The complexity of a model must match the intrinsic complexity of the problem it addresses. The model captures the stable, unchanging trends and laws at the core of the problem.

For example, given the following data:
![](https://pic4.zhimg.com/80/v2-91f19a10faa36653267ffbd4eab86b7f_1440w.webp)

The model we build should be a simple curve like the one in figure (a). A model like figure (b), which attempts to fit every data point precisely, is known in mathematics as **overfitting** and struggles to describe new data. A model like figure (c), which restricts the delta to be only positive, would severely limit the model's descriptive accuracy.

The above is a heuristic explanation of the abstract computational model `Y = F(X) ⊕ Δ`. Now, we will introduce a concrete technical implementation for this model in the domain of software construction, which I have named **Reversible Computation**.

Reversible Computation is a technical path that systematically applies the following formula to guide software construction:
```
App = Delta x-extends Generator<DSL>
```
*   **App**: The target application to be built.
*   **DSL**: Domain-Specific Language, a language tailored to describe the business logic of a specific domain. It is also the textual representation of the domain model.
*   **Generator**: A tool that repeatedly applies generation rules based on information from the domain model to produce a large amount of derived code. Implementations include standalone code generators and compile-time template expansion based on metaprogramming.
*   **Delta**: The differences between the logic generated from the known model and the target application's logic are identified, collected, and organized into an independent delta description.
*   **x-extends**: The delta description is combined with the model-generated parts using techniques similar to Aspect-Oriented Programming (AOP). This involves operations like adding, modifying, replacing, and deleting parts of the generated code.

The DSL is a high-density representation of critical domain information that directly guides the Generator's code generation, similar to how a Turing Machine uses input data to drive its built-in instructions. If we view the Generator as a process of text symbol substitution and generation, its execution and composition rules are a perfect mirror of Lambda Calculus. Delta merging, in a sense, is a novel operation. It requires an intricate, all-encompassing ability to capture change, allowing us to isolate and merge small-order variations scattered throughout the system. This gives the delta its independent meaning and value. At the same time, the system must explicitly establish the concepts of **inverse elements** and **inverse operations**. Only within such a conceptual framework can a delta, as a mixture of "existence" and "non-existence," be properly expressed.

Existing software infrastructure cannot effectively implement Reversible Computation without a complete overhaul. Just as the Turing Machine model gave birth to the C language and Lambda Calculus led to Lisp, I propose a new programming language, the **X Language**, to effectively support Reversible Computation. It has built-in support for defining, generating, merging, and splitting deltas, allowing for the rapid construction of domain models and the implementation of Reversible Computation on top of them.

To implement Reversible Computation, we must establish the concept of the delta. Change produces deltas, deltas can be positive or negative, and they should satisfy three requirements:
1.  **Deltas exist independently.**
2.  **Deltas interact with each other.**
3.  **Deltas have structure.**

In Section III, I will use Docker as an example to illustrate the importance of these three requirements.

The core of Reversible Computation is "reversibility," a concept closely related to entropy in physics. Its importance actually extends far beyond program construction, a topic I will explore in more detail in a future article on the methodological origins of Reversible Computation.

Just as the advent of complex numbers expanded the solution space for algebraic equations, Reversible Computation adds the critical technique of "reversible delta merging" to the existing software construction toolkit. This vastly expands the feasible scope of software reuse, making system-level, coarse-grained reuse possible. Simultaneously, from this new perspective, many previously intractable problems in model abstraction find simpler solutions, significantly reducing the intrinsic complexity of software construction. I will elaborate on this in Section II.

Although software development is hailed as knowledge-intensive work, the daily routine of many frontline programmers still involves a great deal of mechanical, manual work like copying, pasting, and modifying code. In Reversible Computation theory, the modification of code structure is abstracted into automatically executable delta merging rules. Thus, through Reversible Computation, we can create the foundation for the automated production of software itself. Based on this theory, I have proposed a new industrial production model for software: **NOP (Nop is nOt Programming)**, which produces software in batches through non-programming means. NOP isn't "no programming," but it emphasizes separating the logic that business personnel can intuitively understand from the purely technical implementation logic. Each part is designed using appropriate languages and tools and then seamlessly stitched together. I will provide a detailed introduction to NOP in another article.

Reversible Computation and reversible computers share the same intellectual origins in physics. Although their technical substance differs, their goal is the same. Just as cloud computing aims to achieve the "cloudification of computation," Reversible Computation and reversible computers aim to achieve the **reversibilization of computation**.

***

## II. How Reversible Computation Inherits and Evolves Traditional Theories

### (a) Components

Software originated as a byproduct of mathematicians' research into Hilbert's tenth problem. Early software was primarily used for mathematical and physical calculations, and its concepts were undoubtedly abstract and mathematical. As software became more widespread, the development of more and more application software gave rise to **Object-Oriented** and **Component-based** methodologies. These approaches attempted to de-emphasize abstract thinking and align more closely with human common sense, drawing knowledge from everyday experience. They map concepts that people can intuitively perceive in a business domain to objects in software, mimicking the manufacturing processes of the physical world to assemble the final software product from scratch, from small to large.

Familiar concepts in software development like frameworks, components, design patterns, and architectural views are all directly inspired by the construction industry. Component theory inherited the essence of object-oriented thinking and, through the concept of reusable prefabricated parts, created a vast third-party component market, achieving unprecedented technical and commercial success. To this day, it remains the most mainstream guiding philosophy for software development. However, Component theory has an inherent flaw that prevents it from advancing its success to a new level.

We know that reuse is the repeated use of existing finished products. To achieve component reuse, we need to find the common parts between two pieces of software, isolate them, and standardize them according to component specifications. However, the common part of A and B is smaller in granularity than both A and B. The common part of a large number of software systems is much, much smaller in granularity than any single one of them. This limitation directly leads to the fact that the larger the granularity of a software module, the harder it is to reuse directly. There is a theoretical limit to component reuse. One can achieve 60-70% reuse by assembling components, but it is rare for anyone to exceed 80%, let alone achieve system-level reuse of over 90%.

To overcome the limitations of component theory, we need to re-examine the abstract nature of software. Software is an information product that exists in an abstract, logical world. Information is not matter. The laws of construction and production in the abstract world are fundamentally different from those in the material world. Producing a physical product always has a cost, but the marginal cost of copying software can be zero. Moving a table out of a room in the physical world requires passing through a door or window, but in an abstract information space, one might only need to change the table's coordinate from x to -x. The operational relationships between abstract elements are not bound by numerous physical constraints. Therefore, the most effective mode of production in the information space is not assembly, but mastering and formulating the rules of operation.

If we reinterpret Object-Oriented and Component technologies from a mathematical perspective, we find that Reversible Computation can be seen as a natural extension of component theory.
*   **Object-Oriented**: The inequality `A > B`
*   **Component**: Addition `A = B + C`
*   **Reversible Computation**: The delta `Y = X + ΔY`

A core concept in Object-Oriented Programming is inheritance: a derived class inherits from a base class, automatically gaining all its functionalities. For example, a Tiger is a derived class of Animal. Mathematically, we can say that the concept of a Tiger (A) contains more information than the concept of an Animal (B), i.e., `A > B`. From this, we know that any proposition true for Animal is also true for Tiger (e.g., if animals run, tigers must also run—`P(B) -> P(A)`). Any place in a program that uses the concept of Animal can be replaced by Tiger (Liskov Substitution Principle). Inheritance thus introduces automatic reasoning into the software domain, which mathematically corresponds to an inequality, a partial order relationship.

The theoretical predicament of OOP lies in the limited expressiveness of inequalities. For `A > B`, we know A has more than B, but we have no way to explicitly express *what* more it has. And for cases like `A > B` and `D > E`, even if the "extra" parts are identical, we cannot reuse that content. Component technology explicitly states that "composition is better than inheritance," which is equivalent to introducing addition:
```
A = B + C
D = E + C
```
This allows the component `C` to be abstracted and reused. Following this line of reasoning, the next logical step is to introduce "subtraction," which allows us to treat `A = B + C` as a true equation and solve for its terms by moving them across the equals sign:
> B = A - C = A + (-C)

The "negative component" introduced through subtraction is a brand new concept that opens a new door for software reuse.
Suppose we have already built a system `X = D + E + F`, and now we need to build `Y = D + E + G`. Following the component-based solution, we would need to disassemble X into multiple components, replace component F with G, and then reassemble. However, following the technical path of Reversible Computation, by introducing the inverse element `-F`, we immediately get:
> Y = X - F + G = X + (-F + G) = X + ΔY

Without disassembling X, we can transform system X into system Y by simply applying a delta `ΔY`.

The condition for component reuse is "reuse requires identity." But in a system with inverse elements, the complete system X, at its maximum granularity, can be directly reused without any modification. The scope of software reuse is expanded to "reuse requires relevance." There are no longer any limits on the granularity of software reuse. The relationship between components also undergoes a profound change, moving from a monotonous compositional relationship to a richer, more dynamic transformational relationship.

The physical picture of `Y = X + ΔY` has very practical significance for the development of complex software products. X can be the base or main version of a software product we are developing. When deployed at different customer sites, a large number of customization requirements can be isolated into independent deltas `ΔY`. These custom delta descriptions are stored separately and merged with the main version's code at compile time. The architecture and code of the main version only need to consider the stable, core requirements of the business domain, protecting it from the impact of incidental requirements from specific customers and effectively preventing architectural decay. The development of the main version and the implementation of multiple projects can proceed in parallel. Different implementation versions correspond to different `ΔY`s, without interfering with each other. Meanwhile, the main version's code remains independent of all custom code, allowing it to be upgraded as a whole at any time.

### (b) Model-Driven Architecture (MDA)

Model-Driven Architecture (MDA) is a software design and development method proposed by the Object Management Group (OMG) in 2001. It is seen as a milestone in the shift of software development paradigms from code-centric to model-centric. The theoretical basis for most of today's so-called software development platforms is related to MDA.

MDA aims to raise the level of abstraction in software development, using a modeling language (like Executable UML) directly as a programming language, and then using compiler-like technology to translate the high-level model into low-level executable code. In MDA, a clear distinction is made between application architecture and system architecture, described by the **Platform Independent Model (PIM)** and the **Platform Specific Model (PSM)**, respectively. The PIM reflects the functional model of the application system, independent of specific implementation technologies and runtime frameworks. The PSM, on the other hand, focuses on implementing the functionality described by the PIM using a specific technology (e.g., J2EE or .NET), providing a runtime environment for the PIM.

The ideal scenario for using MDA is that a developer designs the PIM using a visual tool, then selects a target runtime platform. The tool then automatically executes mapping rules for that specific platform and implementation language, transforming the PIM into the corresponding PSM, and finally generating executable application code. Program construction based on MDA can be expressed by the following formula:
```
App = Transformer(PIM)
```
MDA's vision was to eventually eliminate traditional programming languages, much like C replaced assembly. But after many years of development, it has yet to demonstrate a compelling competitive advantage over traditional programming in a wide range of application domains.

In fact, current MDA-based development tools often reveal an inherent inability to adapt when faced with diverse business domains. As analyzed in Section I of this article, we know that modeling must account for deltas. In the MDA construction formula, the `App` on the left represents all sorts of unknown requirements, while the `Transformer` and `PIM` designer on the right are actually provided mainly by the tool vendors. The equation `Unknown = Known` cannot remain balanced for long.

Currently, the main approach of tool vendors is to provide a massive, comprehensive set of models in an attempt to anticipate all possible user business scenarios. However, we know there's "no such thing as a free lunch." The value of a model lies in its embodiment of essential constraints within a business domain; no single model is optimal for all scenarios. Predicting requirements leads to a paradox: if a model has too few built-in assumptions, it cannot generate much useful work from the small amount of information a user inputs, nor can it prevent user errors, making the model's value less apparent. Conversely, if the model makes many assumptions, it becomes solidified into a specific business scenario and struggles to adapt to new situations.

When we open an MDA tool's designer, our most common feeling is that most of the options are unnecessary, and we don't know what they do, while the options we actually need are nowhere to be found.

Reversible Computation extends MDA in two ways:
1.  In Reversible Computation, both the `Generator` and the `DSL` are designed to be extended and adjusted by the user, similar to **Language-Oriented Programming**.
2.  There is an additional opportunity for customization through a delta, allowing for precise, local modifications to the overall generated result.

In the NOP production model I propose, a new key component is essential: a **designer for designers** (or a meta-designer). Ordinary programmers can use this meta-designer to quickly develop their own Domain-Specific Languages (DSLs) and their visual designers. At the same time, they can use it to customize any designer in the system, freely adding or removing elements.

### (c) Aspect-Oriented Programming (AOP)

Aspect-Oriented Programming (AOP) is a programming paradigm that complements Object-Oriented Programming (OOP). It enables the encapsulation of so-called **cross-cutting concerns** that span multiple objects. For example, a requirement specification might state that all business operations must be logged, and all database modification operations must be transactional. In a traditional OOP implementation, a single sentence in the requirements would lead to a sudden bloat of redundant code across numerous object classes. With AOP, these common, "decorative" operations can be separated into an independent aspect description. This is the orthogonality of vertical and horizontal decomposition.

![](https://pic2.zhimg.com/80/v2-4a0da0bcc0165fb96db9db88d00af979_1440w.webp)

AOP is essentially a combination of two capabilities:
1.  Locating a target **Pointcut** within the program's structural space.
2.  Modifying the local program structure by **weaving** extension logic (**Advice**) into the specified location.

Location depends on a well-defined global coordinate system for the structure (how can you locate something without coordinates?), and modification depends on a well-defined local semantic structure of the program. The limitation of mainstream AOP techniques is that they are all expressed in the context of OOP. However, the domain structure does not always align with the object implementation structure. In other words, using the coordinate system of the object hierarchy is insufficient to express domain semantics. For instance, an "applicant" and an "approver" are distinct concepts in a domain model, but at the object level, they might both correspond to the same `Person` class. In many cases, AOP cannot directly translate a domain description into a pointcut definition and an advice implementation. This limitation is reflected in its application: apart from a few "classic" uses unrelated to specific business domains, such as logging, transactions, lazy loading, and caching, we struggle to find other compelling use cases for AOP.

Reversible Computation requires AOP-like capabilities for location and structural modification, but it defines these capabilities in the **domain model space**, thereby greatly expanding AOP's range of application. In particular, the structural delta Δ generated by the self-evolution of the domain model in Reversible Computation can be expressed in a form similar to an AOP aspect.

We know that components can identify recurring "identity" in a program, whereas Reversible Computation can capture "similarity" in program structure. Identity is rare and requires keen discernment, but in any system, one type of similarity is readily available: the similarity between a system and its own historical snapshots during its dynamic evolution. This type of similarity previously lacked a dedicated technical form of expression.

Through vertical and horizontal decomposition, the conceptual web we build exists on a design plane. When this design plane evolves along the time axis, a "three-dimensional" mapping naturally arises: the design plane at a later moment can be seen as being derived from the plane at an earlier moment by adding a delta mapping (customization), where the delta is defined at every point on the plane. This image is similar to the concept of a **Functor** in Category Theory, where the delta merging in Reversible Computation plays the role of the functor mapping. Therefore, Reversible Computation effectively extends the original design space, providing a concrete technical representation for the concept of evolution.

### (d) Software Product Line (SPL)

Software Product Line theory originates from the insight that very few software systems in a business domain are entirely unique. A large number of software products exhibit similarities in form and function and can be grouped into a **product family**. By studying, developing, and evolving all products in a product family (both existing and not yet existing) as a whole, and by scientifically extracting their commonalities and combining them with effective variability management, it is possible to achieve large-scale, systematic software reuse, and ultimately, the industrial production of software products.

Software Product Line Engineering employs a two-phase lifecycle model, distinguishing between **Domain Engineering** and **Application Engineering**. Domain Engineering is the process of analyzing the commonalities of software products within a business domain, establishing a domain model and a common software product line architecture, and creating reusable core assets—in other words, **development for reuse**. Application Engineering, in essence, is **development with reuse**: the production activity of creating specific application products using the existing architecture, requirements, tests, documentation, and other core assets.

In a 2008 report, researchers at Carnegie Mellon University's Software Engineering Institute (CMU-SEI) claimed that Software Product Lines could bring the following benefits:
*   10x or more improvement in productivity
*   10x or more improvement in product quality
*   60% or more reduction in cost
*   87% or more reduction in labor needs
*   98% or more reduction in time to market
*   Entering new markets in months, not years

The ideal described by Software Product Lines is very appealing: product-level reuse of over 90%, agile on-demand customization, a domain architecture immune to technological changes, and excellent, tangible economic benefits. The only problem is: how can this be achieved? Although SPL engineering attempts to strategically reuse all technical assets at an organizational level (including documents, code, specifications, tools, etc.) by leveraging all available management and technical means, developing a successful Software Product Line under the current mainstream technical regime still faces numerous difficulties.

The philosophy of Reversible Computation is highly compatible with Software Product Line theory. Its technical solution offers a new approach to the core technical challenge of SPLs: **variability management**. In SPL engineering, traditional variability management primarily consists of three methods: adaptation, replacement, and extension.

![](https://pic2.zhimg.com/80/v2-3d835ae5c250e6bfa8744a695c9fdc65_1440w.webp)

All three of these methods can be seen as adding functionality to the core architecture. However, the barriers to reusability come not only from the inability to add new functionality but also, in many cases, from the inability to disable pre-existing functionality. Traditional adaptation techniques require exact interface matching, a rigid docking requirement. Any mismatch will inevitably propagate stress upwards, ultimately requiring the entire component to be replaced. Reversible Computation supplements variability management with the critical mechanism of **elimination**. It can construct flexible adaptation interfaces on-demand in the domain model space, thereby effectively controlling the scope of impact of a change point.

Although the delta in Reversible Computation can also be interpreted as an extension to a base model, it is distinctly different from plugin-based extension technologies. In a platform-plugin structure, the platform is the core entity, and the plugin is attached to it, more like a patch mechanism and conceptually secondary. In Reversible Computation, through some formal transformations, we can arrive at a more symmetric formula:
> A = B ⊕ G(D) ≡ (B,D)

If we consider `G` as a relatively constant background knowledge, we can formally hide it and define a higher-level "parenthesis" operator, similar to an "inner product" in mathematics. In this form, B and D are duals: B is a complement to D, and D is a complement to B. At the same time, we note that `G(D)` is an embodiment of model-driven architecture. The value of model-driven lies in the fact that a small change in model D can be amplified by `G` into a large number of derived changes throughout the system. Therefore, `G(D)` is a non-linear transformation, and `B` is the part of the system that remains after the non-linear factors corresponding to `D` have been removed. Once all complex non-linear influencing factors are stripped away, the remaining part `B` may be simple, and could even form a new, independently understandable domain model structure (analogous to the relationship between sound waves and air: a sound wave is a disturbance in the air, but we can describe the sound wave directly with a sine wave model without studying the air itself).

The form `A = (B,D)` can be directly generalized to cases with more domain models:
> A = (B, D, E, F, ...)

Since B, D, E, etc., are all domain models described by some DSL, they can be interpreted as components of A projected onto specific domain model subspaces. In other words, application A can be represented as a **"Feature Vector"**, for example:
```
App = (Workflow, Reports, Permissions, ...)
```
Compared to **Feature-Oriented Programming (FOP)**, which is commonly used in Software Product Lines, Reversible Computation's feature decomposition scheme emphasizes domain-specific descriptions, resulting in clearer feature boundaries and making conceptual conflicts that arise during feature composition easier to handle.

The feature vector itself constitutes a higher-dimensional domain model, which can be further decomposed, forming a model hierarchy. For example, by defining:
> D' ≡ (B,D)
>
> G'(D') ≡ B ⊕ G(D)

And assuming D' can be further decomposed:
> D' = V ⊕ M(U) = M'(U')

We can obtain:
> A = B ⊕ G(D)
> &nbsp;&nbsp;&nbsp;= G'(D')
> &nbsp;&nbsp;&nbsp;= G'(M'(U'))
> &nbsp;&nbsp;&nbsp;= G'M'(V,U)

Ultimately, we can describe the original model A through the domain feature vector D', which in turn is described by the domain feature vector U'.

This construction strategy of Reversible Computation is similar to a deep neural network. It is no longer limited to a single model with a vast number of tunable parameters but instead establishes a series of models at different levels of abstraction and complexity, constructing the final application through a process of gradual refinement.

From the perspective of Reversible Computation, the work of application engineering becomes describing software requirements using feature vectors, while domain engineering is responsible for generating the final software based on these feature vector descriptions.

***

## III. The Nascent Delta Revolution

### (a) Docker

Docker is an application container engine open-sourced in 2013 by the startup dotCloud. It can package any application and its dependencies into a lightweight, portable, self-contained **Container**. Based on this, it created a new form of software development, deployment, and delivery with the container as the standard unit.

Upon its release, Docker instantly killed Google's own `lmctfy` (Let Me Contain That For You) container technology and, at the same time, propelled another of Google's creations, the Go language, to stardom. Docker's development since then has been unstoppable. Starting in 2014, a Docker storm swept the globe, driving changes in operating system kernels with unprecedented force. With the hype from numerous tech giants, it instantly ignited the container cloud market, fundamentally changing the entire lifecycle of enterprise applications, from development and building to deployment and operation.

![](https://pic2.zhimg.com/80/v2-d6ef1c89995987f99c69e2c9f2456985_1440w.webp)

The success of Docker technology stems from its fundamental reduction of software runtime complexity, and its technical solution can be seen as a special case of Reversible Computation theory. Docker's core technical model can be summarized by the following formula:
```
App = DockerBuild<DockerFile> union-fs BaseImage
```
The `Dockerfile` is a domain-specific language for building container images. For example:
```dockerfile
FROM ubuntu:16.04
RUN useradd --user-group --create-home --shell /bin/bash work
RUN apt-get update -y && apt-get install -y python3-dev
COPY . /app
RUN make /app

ENV PYTHONPATH /FrameworkBenchmarks
CMD python /app/app.py

EXPOSE 8088
```
A `Dockerfile` allows for the quick and precise description of a container's dependencies, including the base image, build steps, runtime environment variables, and system configurations.
The `docker` application plays the role of the `Generator` in Reversible Computation, responsible for interpreting the `Dockerfile` and executing the corresponding instructions to generate the container image.

The creative use of a **Union File System (UnionFS)** is a particularly innovative aspect of Docker. This type of file system uses a layered construction. Once a layer is built, it never changes. Any modifications made on a subsequent layer are only recorded on that layer. For example, when modifying a file from a previous layer, a copy is made to the current layer via **Copy-On-Write (COW)**. Deleting a file from a previous layer doesn't actually perform a deletion; it merely marks the file as deleted in the current layer. Docker uses the UnionFS to merge multiple container images into a complete application. The essence of this technology is precisely the `x-extends` operation in Reversible Computation.

The name "Docker" refers to a dock worker, and the containers it handles are often compared to shipping containers: standard containers, like shipping containers, allow us to freely transport and combine them without regard for their specific contents. But this comparison is superficial, and even misleading. A shipping container is static, simple, and has no external interfaces. A Docker container, however, is dynamic, complex, and has extensive interactions with the outside world. Encapsulating such a dynamic, complex structure into a so-called standard container is a challenge of a completely different magnitude. Without introducing a delta-supporting file system, it would be impossible to construct a flexible boundary to achieve logical separation.

The standard encapsulation Docker provides can also be achieved by virtual machines. Even delta storage mechanisms were used early on in VMs for incremental backups. So, what is the fundamental difference between Docker and a VM? Recalling the three basic requirements for deltas from Section I, we can clearly see what makes Docker unique.

1.  **Deltas exist independently**: Docker's most significant value lies in its ability to discard the operating system layer, which, while essential, is usually just background noise that accounts for 99% of the volume and complexity. The application container becomes a first-class entity that can be independently stored and manipulated. These lightweight containers completely outperform bloated virtual machines in terms of performance, resource consumption, and manageability.
2.  **Deltas interact with each other**: Docker containers interact in a precisely controlled manner, selectively isolating or sharing resources through the operating system's namespace mechanism. In contrast, there is no isolation mechanism between the delta slices of a virtual machine.
3.  **Deltas have structure**: Although VMs support incremental backups, there are no suitable means for users to proactively construct a specific delta slice. This is ultimately because VM deltas are defined in a binary byte space, which is a very barren space with almost no user-controllable construction patterns. Docker's deltas, however, are defined in the space of a differencing file system, a space that inherits the rich history of the Linux community. The result of every shell command is ultimately reflected in the file system as adding, deleting, or modifying certain files. Therefore, every shell command can be seen as the definition of a delta. The deltas form an exceptionally rich structural space. A delta is both a transformation operator in this space (a shell command) and the result of that operator's execution. Deltas meet and produce new deltas; this perpetual generation is the source of Docker's vitality.

### (b) React

In 2013, the same year Docker was released, Facebook open-sourced a revolutionary web front-end framework: React. React's technical philosophy is very unique. Based on functional programming principles and combined with the seemingly fantastical concept of a **Virtual DOM**, it introduced a whole new set of design patterns, kicking off a new age of discovery in front-end development.
```javascript
class HelloMessage extends React.Component {
  constructor(props) {
    super(props);
    this.state = { count: 0 };
    this.action = this.action.bind(this);
  }

  action(){
    this.setState(state => ({
      count: state.count + 1
    }));
  }

  render() {
    return (
      <button onClick={this.action}>
        Hello {this.props.name}: {this.state.count}
      </button>
    );
  }
}

ReactDOM.render(
  <HelloMessage name="Taylor" />,
  mountNode
);
```
The core of a React component is the `render` function. Its design is inspired by common server-side template rendering technologies, with the main difference being that server-side templates output HTML text, whereas a React component's render function uses JSX, an XML-like template syntax, which is compiled and transformed at runtime to output Virtual DOM node objects. For example, the `render` function of the `HelloMessage` component above is translated into something like this:
```javascript
render(){
   return new VNode("button", {onClick: this.action,
          content: "Hello "+ this.props.name + ":" + this.state.count });
}
```
We can describe a React component with the following formula:
> VDOM = render(state)

When the state changes, re-executing the `render` function generates a new Virtual DOM tree. This tree can be translated into real HTML DOM objects, thereby updating the user interface. This rendering strategy—regenerating the entire view from the state—greatly simplifies front-end UI development. For instance, for a list interface, traditional programming would require writing multiple different DOM manipulation functions for adding, updating, and deleting rows. In React, you simply change the state and re-execute the single `render` function.

The only problem with regenerating the entire DOM view each time is poor performance, especially when front-end interactions are numerous and state changes are frequent. React's stroke of genius was to propose a **diff algorithm** based on the Virtual DOM, which can automatically calculate the difference (the delta) between two Virtual DOM trees. When the state changes, only the DOM modification operations corresponding to the Virtual DOM delta need to be executed. (Updating the real DOM is slow because it triggers style calculations and layout reflows, whereas manipulating the Virtual DOM in JavaScript is extremely fast). The overall strategy can be represented by the following formulas:
> state = state₀ ⊕ state₁
>
> ΔVDom = render(state₁) - render(state₀)
>
> ΔDom = Translator(ΔVDom)

Clearly, this strategy is also a special case of Reversible Computation.

If you pay close attention, you'll notice that concepts expressing delta operations like `merge`, `diff`, `residual`, and `delta` have been appearing more and more frequently in the software design field in recent years. For example, in the stream processing engines of the big data world, the relationship between a stream and a table can be expressed as:
> Table = ∫ Stream

Create, read, update, and delete operations on a table can be encoded as a stream of events. Accumulating these events, which represent data changes, forms the data table.

Modern science began with the invention of calculus. The essence of differentiation is the automatic calculation of infinitesimal deltas, while integration is its inverse operation, the automatic summation of these infinitesimals. In the 1870s, economics underwent a **Marginal Revolution**, introducing the ideas of calculus into economic analysis and rebuilding the entire edifice of economics upon the concept of the margin. Today, the theory of software construction has reached a bottleneck. It is time for us to re-examine the concept of the delta.

***

## IV. Conclusion

My professional background is in theoretical physics, and Reversible Computation originated from my attempt to introduce ideas from physics and mathematics into the software domain, first proposed by me around 2007. For a long time, the application of natural laws in the software field has generally been limited to the category of "simulation." For example, a fluid dynamics simulation software, although it incorporates some of the most profound laws of the universe known to man, does not use these laws to guide and define the construction and evolution of the software world itself. Their scope is directed outside the software world, not at the software world itself. In my view, within the software world, we can absolutely take a "God's-eye view" to plan and define a series of structural construction laws to assist us in building that world. To accomplish this, we first need to establish a **"calculus" for the world of programming**.

Similar to calculus, the core of Reversible Computation theory is to elevate the **"delta"** to a first-class concept, treating the whole as a special case of the delta (`whole = identity + whole`). In the traditional programming world, we only express "what is," and moreover, "all that is." Deltas can only be obtained indirectly through operations on wholes; their representation and manipulation require special handling. With Reversible Computation theory, we should first define the forms of expression for all delta concepts and then build the entire domain conceptual system around them. To ensure the completeness of the mathematical space where deltas reside (the result of an operation between deltas must still be a valid delta), a delta must express not just "what is" but a mixture of "what is" and "what is not." In other words, a delta must be **"reversible."** Reversibility has very profound physical meaning, and building this concept into the fundamental conceptual system can solve many very thorny problems in software construction.

To handle distributed problems, the modern software development ecosystem has already accepted the concept of immutable data. To solve the problem of large-grained software reuse, we also need to accept the concept of **immutable logic** (reuse can be seen as keeping the original logic unchanged and then adding a delta description). Currently, some creative practices that proactively apply the concept of the delta are gradually emerging in the industry, all of which can be uniformly interpreted under the theoretical framework of Reversible Computation. I have proposed a new programming language, the **X Language**, which can greatly simplify the technical implementation of Reversible Computation. I have already designed and implemented a series of software frameworks and production tools based on the X Language, and based on them, I have proposed a new software production paradigm (**NOP**).

The low-code platform NopPlatform, designed based on Reversible Computation theory, is now open source:
*   **Gitee**: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
*   **GitHub**: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
*   **Developer Tutorial**: [docs-en/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs-en/tutorial/tutorial.md)
*   **Video Introduction (Chinese)**: [Principles of Reversible Computation and Nop Platform Introduction & Q&A (Bilibili)](https://www.bilibili.com/video/BV14u411T715/)
