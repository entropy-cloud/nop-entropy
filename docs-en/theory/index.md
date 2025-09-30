# Reversible Computation Theory

## Zhihu Column
[Reversible Computation](https://www.zhihu.com/column/reversible-computation)

A centralized collection of the author's discussions on the theory of Reversible Computation and the implementation principles of the Nop platform.

## Explaining Reversible Computation in three sentences

1. Inheritance in object-oriented programming and traits in Rust do not include deletion semantics, and they only express the object–method single-level relationship; structurally they correspond only to a Map.
2. The strongest form of object-oriented programming is a generic object with template metaprogramming capabilities; at the structural level it can be viewed as `Map extends Map<Map>`.
3. If we extend Map into a Tree structure and extend the extends operator to include subtraction, then the Tree becomes a DeltaTree with inverse elements, and the overall structure upgrades to `Tree x-extends Tree<Tree>`.
   This abstraction can naturally bring abstract syntax trees and file systems into its category, becoming a general computation pattern with broad application domains that is not confined to any single programming language; when implemented concretely, it becomes the core software construction formula defined by the theory of Reversible Computation.

```
App = Delta x-extends Generator<DSL>
```

> Docker and k8s’s kustomize can both be regarded as concrete instances of Reversible Computation.

## [Reversible Computation: The Next-Generation Theory of Software Construction](reversible-computation.md)

An overview of the theory of Reversible Computation.

## [A Programmer’s Clarification of Reversible Computation Theory](reversible-computation-for-programmers.md)

Starting from concepts familiar to programmers, this article explains the concrete forms and practices of Delta and Delta merging in programming, and analyzes why some common understandings of Reversible Computation are incorrect.

## [Addendum to “A Programmer’s Clarification of Reversible Computation Theory”](reversible-computation-for-programmers2.md)

Further supplements on conceptual clarifications for Reversible Computation theory, addressing and dispelling common misconceptions.

## [What exactly does “Reversible” mean in Reversible Computation Theory?](what-does-reversible-mean.md)
The term “reversible” is closely related to the concept of entropy in physics. The direction of entropy increase determines the evolution direction of the arrow of time in the physical world. Reversible Computation theory studies the laws for constructing coarse-grained software structures oriented toward evolution, so “reversible” is the linchpin of this theory. For readers unfamiliar with thermodynamics and statistical physics, entropy may be an unknown, and the term “reversible” might be puzzling. Is reversibility important? How can software be reversible? Does it mean reverse execution? What is the significance? In this article, I briefly explain what “reversible” exactly refers to within Reversible Computation theory.

## [Delta Oriented Programming from the perspective of Reversible Computation](delta-oriented-programming.md)

This article compares Reversible Computation theory with related work in software engineering, such as Feature-Oriented Programming (FOP) and Delta-Oriented Programming (DOP), and points out where these theories remain insufficient from the Reversible Computation perspective.

## [Low-Code Platform Design through the lens of Tensor Products](tensor-product-lowcode.md)

Multi-dimensional extension in framework design can be viewed mathematically as linear mapping functions over tensor product spaces. This article explains at a theoretical level how the principles of Reversible Computation combine with the Loader abstraction.

## [Clarifying the concept of Delta for programmers, using Git and Docker as examples](explanation-of-delta.md)
Although both are called “Delta,” there are profound differences between one Delta and another. Broadly speaking, Git and Docker both fundamentally involve Delta computation, but the Deltas they correspond to are essentially different; the fine distinctions here require mathematical analysis to clarify. The common understanding of Delta tends to be superficial and ambiguous, so many debates stem from unclear definitions rather than inherent contradictions in the problem itself.
[Differences between “Extension” in Kingdee Cloud Galaxy and “Delta” in the Nop platform](delta-vs-extension.md)
[DeepSeek AI’s understanding of Delta customization—far beyond ordinary programmers](deepseek-understanding-of-delta.md)
[A general Delta composition mechanism](generic-delta-composition.md)

## [Design essentials of DSLs from the perspective of Reversible Computation](xdsl-design.md)

Based on the principles of Reversible Computation, the Nop platform proposes a systematic construction mechanism that simplifies DSL design and implementation, making it easy to add DSLs for your own business domain and to extend existing DSLs.

## [Necessary conditions for using GPT to produce complex code](nop-for-gpt.md)

Many people are attempting to generate code directly with GPT, trying to use natural language to guide GPT in completing traditional coding tasks. However, almost no one seriously considers how the generated code will be maintained over the long term. This article analyzes, at a theoretical level, the necessary conditions for using GPT to produce complex code and proposes a concrete strategy for combining the Nop platform with GPT.

## [How to evaluate the quality of a framework technology (e.g., an ORM framework)](props-and-cons-of-orm-framework.md)

For a new framework technology, statements like “it’s convenient and easy to use” express only a subjective feeling. Can we define objective standards that are not influenced by personal preferences?
[What is a good model?](good_design.md)
[How can business development become framework-agnostic](framework-agnostic.md)

## [How does Nop overcome the limitation that DSLs only apply to specific domains?](nop-for-dsl.md)
The Nop platform can be viewed as a Language Workbench. It provides complete theoretical support and underlying toolsets for the design and development of DSLs (Domain-Specific Languages). Using the Nop platform for development primarily means expressing business logic with DSLs rather than with general-purpose programming languages. Some may wonder: since DSLs are so-called domain-specific languages, doesn’t that imply they can only apply to a specific domain? Wouldn’t there be fundamental limitations in describing business? Back when the ROR (Ruby On Rails) framework was popular, the concept of DSLs was hyped for a while, but now it’s quiet—what makes Nop special? The answer is simple: the Nop platform is a next-generation low-code platform built from scratch on the theory of Reversible Computation, and Reversible Computation is a systematic theory for DSL design and construction that addresses the problems inherent in traditional DSL design and application at the theoretical level.
[How to build a platform that itself can build low-code platforms](meta-platform.md)
[Design essentials of DSLs from the perspective of Reversible Computation](xdsl-design.md)

## [Why is the Nop platform a unique open-source software development platform](technical-strategy.md)
The most fundamental difference between the Nop platform and other open-source software development platforms is that Nop starts from first principles in mathematics, deriving detailed designs at each level through rigorous mathematical reasoning. Its components exhibit intrinsic consistency in the mathematical sense. This directly results in Nop’s implementation being much more concise than other platforms and reaching a level of flexibility and extensibility unmatched by any known public technology, enabling system-level coarse-grained software reuse. Mainstream technologies are primarily designed around component assembly, and their theoretical foundations inherently impose an upper bound on overall software reuse.

## [Why is XLang an innovative programming language?](why-xlang-is-innovative.md)
XLang is an innovative programming language because it creates a new program-structure space in which the computation paradigm `Y = F(X) + Delta` proposed by Reversible Computation theory can be easily implemented.
[DeepSeek’s plain-language explanation: Why is XLang an innovative programming language?](deepseek-understanding-of-xlang.md)

For clarifications on this article, see [Q&A 1](xlang-explained.md), [Q&A 2](xlang-explained2.md)

## [If we rewrote SpringBoot from scratch, what different choices would we make?](lowcode-ioc.md)
If we were to rewrite SpringBoot entirely from scratch, which core problems would we clearly assign to the underlying framework to solve? What solutions would we propose? How do these solutions fundamentally differ from SpringBoot’s current approach? The dependency injection container in the Nop platform, NopIoC, is a model-driven DI container implemented from the ground up based on the principles of Reversible Computation. With roughly 5,000 lines of code, it implements all the dynamic auto-wiring and AOP interception mechanisms we use in SpringBoot, and integrates with GraalVM, making it easy to compile to native images. In this article, I will discuss analyses of IoC container design principles from the perspective of Reversible Computation, in conjunction with NopIoC’s implementation.

## [What kind of ORM engine does a low-code platform need?](lowcode-orm-1.md)
What is ORM? Why does ORM simplify writing code for the data access layer? Which common business semantics can be uniformly delegated to the ORM layer for expression? In the context of a low-code platform, data structures need to support user-defined adjustments, and the logical path from front-end presentation to back-end data storage should be minimized. What support can an ORM engine provide for this? If we are not satisfied with predefined low-code application scenarios but instead want a smooth upgrade path from low-code to pro-code, what requirements would we place on the ORM engine?
[What kind of ORM engine does a low-code platform need? (2)](lowcode-orm-2.md)

## [Why is GraphQL strictly superior to REST in the mathematical sense?](graphql-vs-rest.md)
Through rigorous mathematical reasoning, the Nop platform reinterprets the positioning of GraphQL, leading to new design ideas and technical implementations. Under this interpretation, the NopGraphQL engine achieves a comprehensive surpassing of REST—GraphQL is strictly superior to REST in the mathematical sense.

## [NopTaskFlow: a next-generation logic orchestration engine written from scratch](lowcode-task-flow.md)
NopTaskFlow is a logic orchestration engine implemented based on Reversible Computation theory.
[Why NopTaskFlow is a unique logic orchestration engine](why-nop-taskflow-is-special.md)

## [Why is NopReport a very unique reporting engine?](why-nop-report-is-special.md)
Unlike typical reporting engines, NopReport can use Excel and Word directly as templates and does not necessarily require a dedicated visual designer.

## [Why is SpringBatch a flawed design?](why-springbatch-is-bad.md)
SpringBatch’s design exhibits serious issues today. It is highly unfriendly to performance optimization and code reuse. This article analyzes SpringBatch’s design problems and introduces the design ideas of a next-generation batch processing framework by presenting the implementation of NopBatch.

## [Why does the Nop platform insist on XML rather than JSON or YAML](why-xml.md)
In the Nop platform, XML and JSON support automatic bidirectional conversion; essentially, the choice of notation does not affect the model’s semantics.

## [Low-code from the perspective of Reversible Computation](lowcode-explained.md)
[Reuse in the AI era from the perspective of Reversible Computation](reuse.md)

## [Q&A on the underlying theory of the Nop platform](faq-about-theory-of-nop.md)
[Discussion about Reversible Computation—reply to Rounded-Corner Knight Marisa](discussion-about-reversible-computation.md)

## [What is data-driven? How does it differ from model-driven, domain-driven, metadata-driven, and DSL-driven?](what-is-data-driven.md)
“XX Driven” is one of the common jargons in software engineering—it translates to “X-driven.” By substituting XX we get data-driven, model-driven, domain-driven, metadata-driven, DSL-driven, and a whole bunch of “driven.” A natural question is: what are the differences among these “driven” approaches? Is there really a need to manufacture so many concepts?

## [The arcana of the Paxos algorithm that even elementary schoolers can easily grasp](paxos-explained.md)
The Paxos algorithm is a fundamental algorithm in distributed systems, long known for being abstruse and brain-burning. The main reason it feels unintuitive is that it’s hard to grasp why it is designed that way. Although we can verify its correctness through concrete examples and even convince ourselves with rigorous mathematical proofs, we still struggle to answer: Why this choice? Is it the only feasible approach? Is there a way to find an explanation that makes Paxos intuitively self-evident without relying on mathematical derivations?
[A magical study report on Paxos](paxos.md), [Paxos explained for ordinary elementary school students](paxos-for-kids.md)

## [Why functional programming facilitates decoupling (Decouple)](functional-programming-for-decouple.md)
The ideas of functional programming, how to apply FP in daily programming to achieve logical decoupling, and in what respects functional programming provides a beneficial complement to object-oriented programming.

## [Understanding the essence of React through React Hooks](essence-of-react.md)

```
(props, @reactive state) => vdom
```

The render function is a hard-won information pipeline; using it once and discarding it is wasteful—why not reuse it repeatedly? By introducing state variables with reactivity and stipulating a global reactive rule: “whatever causes state to change, automatically trigger the local render function to re-execute,” the render function is successfully elevated, perfectly embedding microscopic interactivity into the macroscopic information-flow context.
<!-- SOURCE_MD5:0a94349017f723df9decaf43fb6309f5-->
