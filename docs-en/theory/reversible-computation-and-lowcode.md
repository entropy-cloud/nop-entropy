# LowCode Through the Lens of Reversible Computation

In 2020, the buzzword LowCode frequently appeared in mainstream tech media, against the backdrop of tech giants such as Microsoft, Amazon, Alibaba, and Huawei entering the arena with their own products. Suddenly, technology factions large and small—whether originally focused on OA/ERP/IoT/AI—felt compelled, if not to change their banners entirely, at least to stitch on a LowCode trim to signal they were keeping up.

According to Gartner’s 2019 LowCode report, the financial outlook for LowCode is decidedly bright:

> By 2024, three-quarters of large enterprises will use at least four low-code development tools for IT application development and citizen development (citizen development refers to users without an IT background engaging in development, such as business users/product managers/business consultants, etc.). By 2024, low-code application development will account for more than 65% of application development activities.

Shifting to LowCode implies software will enter an industrialized production stage. Yet since the 1970s, such shifts have been attempted many times. What is special about this wave of LowCode? Why might it succeed where predecessors fell short? This article attempts to discuss several technical issues within LowCode from the perspective of Reversible Computation theory.

For an introduction to Reversible Computation theory, see [Reversible Computation: The Next-Generation Theory of Software Construction](https://zhuanlan.zhihu.com/p/64004026)

## I. What is the essence of LowCode?

LowCode, literally “Code Low,” seeks to drastically reduce the proportion of code-centric programming activities during software product construction. Clearly, LowCode is more a statement of aspiration: we hope to significantly reduce coding volume, implying less work, faster delivery, more stable systems, and from a business perspective, lower cost, higher profit, and broader markets. As a simple and appealing wish, LowCode’s essence is like FastCode/CleanCode—namely, it doesn’t have a unique essence. Or rather, its essence is a description of an idealized phenomenon. All else being equal, FastCode is surely better than SlowCode; likewise, for equivalent functionality, LowCode is naturally more desirable than HighCode.

Compared with professional concepts that sound lofty but often prove opaque in practice—like CASE tools, model-driven approaches, and generative programming—LowCode is more concrete and approachable. Especially when paired with commercial claims like replacing programmers with visual drag-and-drop or achieving end-to-end zero-ops, which promise instant economic benefits, it more easily resonates with non-specialists.

In reality, LowCode is more like a banner under which all practices and ideas that reduce code can gather. We can almost assert that LowCode is on the right path of technological evolution. Can you imagine people still hand-coding like today 500 years from now? If humanity hasn’t gone extinct by then, we’ll likely not need today’s kind of programmers to write business code. Those convinced that Code won’t be replaced by LowCode are clearly lacking a future perspective. As we move toward an intelligent society, we will inevitably experience a transfer of cognitive work to machines and a gradual reduction in code. Doesn’t that make LowCode a road to the future?

The concrete connotations and scope of LowCode remain unsettled. It’s difficult to deem current LowCode vendors’ approaches as best practices, and it’s fair to say that with current solutions it’s hard to deliver the miraculous outcomes touted in marketing. If LowCode is to profoundly transform technology and business, more revolutionary exploration is required.

## II. Does LowCode necessarily require visual design?

LowCode reduces coding work via two basic strategies:

1. Substantively reduce the amount of code required for specific business domains.
2. Transform traditionally code-centric tasks into non-coding activities.

Visual design corresponds to the second strategy. Compared to programming, it has two advantages:

1. Visual interactions are more intuitive and concrete, demanding less programming knowledge.
2. Visual interactions are more constrained and thus less error-prone.

Visual design is undoubtedly one of the core selling points of today’s LowCode platforms. But if the first kind of abstraction is made sufficiently simple, we may not need a visual designer. Those who can’t afford fancy design can still achieve LowCode. Often, we only need to define a dedicated domain language to address the majority of needs. For example, the Markdown syntax used across content platforms has far fewer features than complex word processors, yet it is “good enough for most everyday scenarios,” and it has an “intrinsic extension mechanism” allowing custom extensions for special cases (such as embedding formulas, charts, or arbitrary HTML). With such deliberate design bias, we can drastically simplify common scenarios, turning visual design into a secondary objective.

An interesting question: does visual design yield higher productivity? Many programmers would likely vehemently disagree. Given the state of current visual tools, the output from a designer may be more complex than direct handwritten code. Moreover, designers suffer several drawbacks compared to code:

1. They cannot be freely edited using diverse tools and methods like text code.
2. They lack simple mechanisms for secondary abstraction akin to function encapsulation; many designers don’t even offer rudimentary component composition or staging.
3. They cannot achieve local fixes and adjustments akin to inheritance and interface injection in code—either for the design tool or the design artifact.

Reversible Computation offers its own solutions to these issues, noting that visual design is a natural implication of reversibility:

```
      DSL <=> Visual interface
```

A domain-specific language (DSL) can have multiple forms of expression (textual and graphical), which can be reversibly transformed among each other. The DSL has built-in general mechanisms for secondary encapsulation and Delta-based extension. The designer itself is not a fixed product but is dynamically generated by a meta-designer using the DSL’s structural information, enabling co-evolution between the designer and the application description.

## III. How can LowCode become truly “low”?

If we regard software product development as information processing, the strategies mentioned in the previous section translate into:

1. Reduce information expression.
2. Use forms of expression other than code.

Examining today’s LowCode products reveals intriguing phenomena. Take ivx.cn: it targets non-professional programmers and enables designing interfaces and backend logic with no programming. This so-called NoCode approach indeed lowers technical requirements for users. Beyond the intuitiveness provided by visual designers, it also “actively restricts the design space users face,” discarding many conveniences and flexibilities of programming and “lowering the information density in the design space” to reduce the technical threshold.

For instance, in general programming, we deal with various variable scopes and their relationships; in ivx, these concepts don’t exist—everything is akin to global variables. In coding, we can write complex chained calls like `a.b(g(3)).f('abc')`; in ivx, no such concept exists—each line in the designer expresses only a single action. When it comes to simply getting the job done, many features programmers are used to aren’t strictly necessary. Constraining things such that “there is only one way to do one thing, and executing one action yields only one consequence” makes onboarding easier.

However, this way of “low-ing” also limits the platform’s upper bound of capability, making it hard to engage in more complex, higher-value production.

If LowCode is to assist professional development, it must, to some degree, “essentially” reduce information expression. Known strategies include:

1. Systematic component reuse
2. Model-driven methods based on domain abstractions
3. Integrated design across all ends, processes, and lifecycle stages

## 3.1 Component reuse

Component reuse is a general strategy for reducing system complexity: decompose complex objects and identify repeated parts. LowCode differs in that it advocates systematic component reuse—for example, providing complete ready-to-use component libraries without forcing programmers to gather them themselves, ensuring all components compose correctly, offering appropriate documentation and strong designer support, and including component production and consumption within the platform’s management scope (e.g., public material libraries and component marketplaces). What’s currently lacking in the LowCode field is a widely supported component description standard (akin to JetBrains’ web-types standard https://github.com/JetBrains/web-types), enabling unified management of component libraries from different sources.

> Component reuse is an application of philosophical reductionism. The most successful paradigm of reductionism is atomic theory. Beneath the manifold phenomena of the world lie only about a hundred kinds of atoms—an extraordinarily profound insight. But understanding atoms doesn’t mean understanding the world: vast information lives in how atoms combine. We still need multi-level, holistic understanding of the world.

Challenges for current component technologies include rising demands for dynamism, composability, and environmental adaptability, necessitating solutions beyond the perspective of single components and more systemic approaches. For example, we may want one component suite to adapt to desktop, mobile, and mini-program environments. A standard LowCode solution is transpilation: translate the same component description into different concrete component implementations for different runtime environments (rather than burying multi-environment details inside components themselves). When transpilation becomes routine, we begin to enter the world of models.

## 3.2 Model-driven

According to quantum mechanics, information is conserved in our world. If a small amount of input information yields a large amount of valuable output, there are only two possibilities:

1. A simple logical core underlies the apparent diversity of outcomes—the system’s essential complexity is lower than it appears.
2. The system “automatically derives” many results by leveraging other information sources and global knowledge.

To build such a “non-linear (input and output are not proportional)” production system, we first need domain abstractions, then a product factory that can be driven by small amounts of information.

Domain abstraction discovers the essential elements and their relationships within a domain—in other words, “trimming unnecessary requirements.” After years immersed in a domain, with ample domain knowledge, we find many requirements are unnecessary details. We can choose to do only the high ROI work. If we distill this domain knowledge into a domain-specific description language, we can dramatically reduce the quantity of information expressed. For example, the Bootstrap CSS framework is essentially a rethinking of front-end structure: colors are limited to a handful like success/info/warning/danger; sizes are constrained to sm/md/lg; and layout is restricted to 12 selectable positions per row. After using Bootstrap, we realize that most of the time we don’t need such rich color palettes or many size options. Through self-imposed constraints, we paradoxically gain a kind of freedom—fewer unnecessary decisions and demands, while achieving stylistic unity.

Model-based simplification of complexity is often multiplicative. Suppose 100 possible scenarios can be described via cross-combination of two basic factors, 100 = 10*10; then we only need to define 20 basic factors and one combination rule. Beyond that, models’ greater value lies in providing “global knowledge” about the system. For instance, in a finite automaton, `a->b->c->a`, after a sequence of state transitions, the system doesn’t diverge; it inevitably “returns to a known state.” In self-similar nested models, the whole is composed of parts, and magnifying part structures returns us to known descriptions.

Global knowledge greatly facilitates information propagation and derivation. For example, if all component instances have unique names and stable locating paths at runtime, we can leverage them for automatic data binding (auto-aligning with items in the Store), click tracking, etc. In traditional programming, we mainly rely on general, domain-agnostic languages and component libraries. Lacking unified global structural constraints, we easily break global assumptions, interrupting automated reasoning chains and forcing manual intervention by programmers. The modern software design paradigm “convention over configuration” emphasizes exactly the use and preservation of global knowledge.

Knowledge provided by model descriptions often has diverse uses and can drive multiple derivations. For example, from a data model, we can automatically generate database definitions, migration scripts, UI descriptions, data access code, and API documentation. In traditional programming, a piece of code typically has a single intended use.

Mainstream LowCode platforms attempt first to provide a “better” programming model, then embed or integrate many domain-specific services or code templates. What counts as “better” is subjective. Some supplement deficiencies in current languages with enhanced syntax—for example, automatically translating certain synchronous operations into asynchronous calls, auto-generating remote call proxies, or even distributed scheduling. Others codify best practices to reduce development difficulty—for example, wrapping React/Redux into a Vue-like reactive data-driven model. Still others introduce stronger assumptions, narrowing the programming model’s applicability while increasing platform control—for example, simplifying front-end template languages, auto-binding templates to frontend/backend model objects, and providing template rendering across multiple platforms.

LowCode platforms generally embed engines for common models in general development domains—form models, rule models, workflow/process models, BI chart models—and may provide rich front-end templates and large-screen display materials. Many also offer domain-specific services, such as streaming data processing for IoT.

## 3.3 Integrated design

According to thermodynamics, in the world we observe, information tends to dissipate. As information crosses system boundaries, it may be lost, distorted, or conflict. In everyday work, a significant portion of our effort is not creating new things but repeatedly performing format conversions, integrity validations, interface adaptations—tasks that are essentially about bridging and transformation.

Our technological environment is in constant flux, becoming a persistent source of chaos. Standing up a build environment and ensuring stable CI is no trivial matter. It’s common for newcomers to fail to compile and package the source for half a day. With mobile, cloud, and distributed systems, non-functional requirements are increasingly important, and the required knowledge is ballooning.

Monitoring/operations and operational analytics have become indispensable for online software, forcing functional development to support instrumentation for ops and data analysis.

The typical LowCode response to this series of challenges is a grand unified solution covering diverse endpoints for input/output, the full process of development/deployment/testing/release, and the full lifecycle of production/operation/retirement. Integrated design helps reduce information loss, avoid repeated expression, maintain model semantic consistency, and shield teams from the grim reality of bottom-layer tools riddled with pitfalls. Undoubtedly, this is a double-edged sword. Maintaining a stable, self-contained utopia requires enormous investment. Perhaps large companies can use LowCode to output their foundational capabilities—since they already need to reinvent many tools and prefer others to be tied to their ecosystem—while third parties accept dependence on giants as a given.

## IV. Is LowCode different from model-driven approaches?

In current practice, the strategy and tactics adopted by LowCode platforms are essentially no different from traditional model-driven approaches. It’s simply that in today’s tech context, new technologies make some things easier, and the widespread popularity of open source broadens the range of technologies individuals can wield. Indeed, if stability concerns can be outsourced to cloud infrastructure, small teams armed with suitable tools can tackle development traditionally reserved for large software projects.

New times bring new needs, especially in product application scope. There are some differences between LowCode and model-driven approaches.

LowCode can be seen as a grand synthesis of practices aiming for coarse-grained, system-level reuse beyond the granularity of components, following the component era.

1. From a programming perspective, traditional model-driven approaches largely adapt the overall development logic to object-oriented paradigms, whereas with the rise of functional programming, multi-paradigm development is inevitable.
2. Traditional reuse, despite lofty goals, often operates crudely—merely simple parameterization.

From the perspective of Reversible Computation, I believe a key difference is a Delta-model-driven, evolution-embracing design. For any model, we can ask: “Can it be customized, and how?” A follow-up: “How is the model decomposed, and how do we achieve secondary abstraction atop it?” We can add a new metric to models: reversibility. Many problems become clearer when we explicitly recognize the presence and necessity of reversibility.

Technically, with the development of transpilation and the spread of compiler technologies, modifying at the language level is no longer unthinkable. In practice, many library authors already operate in the realm of traditional compiler techniques. With this tool in our arsenal—especially when made available at the application level—many wide-ranging information propagation problems that are topologically difficult become easier to solve. Issues of form stability are often resolved once intrinsic representations are used.

The most basic method of abstraction is parameterization. For example:

```
​    F(X1, X2, X3, ....)
```

Very roughly, we can gauge a model’s complexity by the number of parameters it requires: fewer parameters, lower complexity; more parameters, higher complexity. If X1, X2, X3 are independent, then the model scenarios correspond to the cardinality of the cross product of X1, X2, X3. However, the real world is more complex: once parameters are numerous enough, they are “not mutually independent”!

To understand F(X1, X2, X3), we must understand F and how X1, X2, X3 propagate and are used within F. If they influence each other, things get worse—we must understand how these interactions occur in subtle ways.

Here’s the interesting part: what if we impose standardized structural constraints on X1, X2, X3? Once parameters become sufficiently complex, they may form an organically evolving whole. We can describe them using a domain model M, allowing us to understand M independently of F.

The underlying logic of our world is layered. We can form understanding at different levels based on different structures. Moving from one level to another, our understanding may be independent (a complete parameter system elevates into an independent conceptual space). When we introduce structure and actively manage conceptual structure, the complexity we face changes entirely.

The endpoint of parameterization is `f(DSL)`: all x form an organic whole, achieving a description of the overall domain logic, while f becomes the domain’s supporting capability. Of course, an effective DSL can only describe a specific business slice, so we need `f(DSL1) + g(DSL2）+ ...`, and in summary, `Y = G(DSL) + Delta`.

A common problem with abstraction is leakage. What if our abstracted DSL doesn’t fit reality? From a Reversible Delta perspective, we can solve it like this:

```
Biz = App - G(DSL1)
```

Treat the remainder Biz after peeling off the domain description DSL1 as an independent subject of study. This is like physics, where we can take a Gaussian model as the system’s zeroth-order solution, then rewrite the original equation as a first-order correction equation.

At a foundational technical level, the structures behind many business problems are familiar, but current mainstream techniques are insufficient for migrating implementations from one tech context to another. There’s too much tight coupling between implementations and business content, forcing programmers to copy, paste, and manually trim. Through DSL abstraction and Delta-based processing, we can achieve new splits across different layers of logic—for example, swapping engines without opening the hood.

Programmers view software systems from a god’s-eye perspective; they need not passively accept the facts within the system. “Let there be light,” and there was light. Programmers can design Rules/Laws and constrain all elements’ behavior. Some now believe LowCode can only do 0-to-1 prototyping and eventually requires refactoring with traditional techniques like DDD. This likely presumes LowCode only aligns business to a handful of built-in models and cannot offer the most appropriate logical decomposition for domain realities. Reversible Computation takes a different view: it emphasizes DSLs should be customizable, freely extensible as needs evolve. Similar to JetBrains’ MPS, the supporting technology for Reversible Computation should be a domain language workbench providing a complete solution for developing and running domain languages.

## V. Does LowCode need technological neutrality?

LowCode strives for higher-order reuse, so it must mask accidental dependencies caused by fragmented tech ecosystems. Why can’t the same logic implemented in Java be directly used in TypeScript?
Tech neutrality strategies include:

1. Microservices. Achieve separation via tech-neutral communication protocols—mature and standardized. With kernel network stack optimizations (e.g., RDMA and DPDK), sidecar-mode cross-process calls can be extremely optimized and comparable to in-process calls.
2. Virtual machines. GraalVM enables compilation mixing different stacks—a highly promising direction—with the benefit of cross-boundary co-optimization. In the microservices approach, Java calling JavaScript cannot be uniformly optimized.
3. Domain languages. A low-cost approach controlled by ordinary programmers. If writing interpreters becomes easier—e.g., mapping an interpretation rule to a simple function call—it can become a routine tool for everyday business problems.

If each concrete implementation is seen as a different coordinate system, then implementing the same logic in different technologies amounts to representing a fixed logic in different coordinate systems. How does mathematics treat coordinate-free quantities? All principles in physics are reference-frame invariant—coordinate-free—and are represented by tensors. The coordinate invariance of tensors means a physical quantity has different concrete representations under different coordinates, and these are related via reversible structural transformations. Coordinate neutrality doesn’t mean binding to one coordinate system, but allowing reversible conversion among different coordinate systems.
Therefore, according to Reversible Computation theory, we need not force a grand unified design where information is expressed in only one way everywhere. Multiple expressions can coexist so long as predefined reversible mechanisms exist to reverse-extract information and automatically convert it. For example, existing interface definitions have interface descriptors—whether protobuf, jsonrpc, or grpc—since they are descriptive information. In principle, we can freely convert among these structural descriptions by starting from one descriptor and supplementing some information. If our products can be automatically generated from interface descriptions at any time, they need not be fixed binary code. Deferred generation is essentially multi-stage compilation, which could be a future direction for LowCode.

Reversibility supports divide-and-conquer. When every part of a large model is reversible, the whole can become reversible. Reversibility can also span systems. The future of LowCode shouldn’t be a single product or single SaaS app, but an ecosystem where information flows freely inside and out, upstream and downstream, breaking bindings to particular technology forms.

Coordinate-neutral systems have a special case—zero—which brings essential simplification. “A coordinate-neutral zero remains zero in all coordinate systems.” This implies many operations can be performed in a coordinate-neutral, purely formal representation. Numerous judgments and structural conversions/transformations can be executed purely at the formal level, without involving complex runtime dependencies.
A core viewpoint of Reversible Computation is that coordinate neutrality is a matter of form, entirely discussable independent of runtime. Via compile-time metaprogramming, we can make runtime structures identical to handwritten code. LowCode need not introduce extra layers of indirection at runtime.

## VI. Does LowCode need Turing completeness?

Frankly, Turing completeness is jargon within the computer science community. Regarding models: does a model’s importance or effectiveness have anything to do with Turing completeness? For instance, chemical formulas are an effective DSL for describing molecular structure; chemical reaction equations model reaction processes—do they need to be Turing complete? Newton’s law of universal gravitation connects the stars above and apples below—an extraordinary modeling achievement—and its computation guides seasonal agriculture. Do differential equation models need Turing completeness to be established and solved?

Core knowledge within a domain doesn’t require Turing completeness. We need Turing completeness when doing “general-purpose” computations and processes—when machines must automate mundane actions, or when we need to handle things in unforeseen ways. Once we’ve encountered a situation, we often encapsulate complexity in algorithms so external users need not have Turing completeness.

For LowCode to cover a sufficiently broad range of tasks, it must retain Turing-complete capabilities. However, its core domain models need not be Turing complete. Turing completeness can remain a capability used for edge scenarios.
Turing-complete capability can be provided via embedded DSLs. This is very economical, especially leveraging IDE support for the host language to obtain DSL support “for free.” The issue is that embedded DSLs often focus on the intuitive form of correct expression and lack constraints for deviations in form. That is, users can write DSL code that is semantically equivalent but formally deviates from the original DSL’s requirements. Form-aware structural transformations then become difficult.
JSX is an interesting extension form. Since we obtain virtual DOM nodes at runtime, we gain considerable control over structure. But TypeScript’s issue is that it lacks an explicit compile phase. JSX’s compile-time processing is complex; at runtime we get concrete VDOM nodes, whereas compile-time requires code structure analysis. Due to the Halting Problem, code structure is generally hard to analyze. Therefore, compile-time analyzable structures like Vue templates are crucial.

Turing completeness is not information completeness. Reverse-parsing to obtain information is lacking in mainstream technologies. Turing completeness is actually detrimental to reverse information analysis. The tradition of handwritten code means programmers haven’t emphasized automatic program structure analysis—historically the compiler writer’s responsibility. If LowCode brings this technology to the application layer at scale, more education is needed. Today’s languages have strong runtime capabilities but weak compile-time reverse capabilities. Many prefer JSON, essentially to keep reverse analysis/conversion capabilities in their own hands.
DSLs needn’t imply special programming syntax. In our implementation of Reversible Computation, we use XML template technology as a unified structural description for frontend and backend—akin to directly writing the AST (abstract syntax tree). Compared to LISP, the structure is richer. Plainly, HTML formats are more readable for most people than Lisp.
Reversible Computation provides a method for accumulating extension capabilities atop domain-specific logic.

## VII. What will we lose for LowCode?

An interesting question: does LowCode bring us only benefits? What do we lose by using LowCode?
My answer: LowCode will cause programmers to lose monopolistic control. Traditionally, all software structures originate from programmers’ input, making programmers an unavoidable intermediary between applications and business stakeholders. LowCode transforms this by stratifying logic and enabling multiple information sources to independently participate in system construction. Business stakeholders can bypass programmers to directly instruct business model construction and autonomously complete the design-feedback loop with intelligent system support. Once these stakeholders gain direct control, they likely won’t want to return to the days of passive waiting.
LowCode’s strategy, albeit not novel, differs in its expression and emphasis. It explicitly highlights citizen programming, whereas model-driven approaches historically remain a matter internal to programmers.

Any genuine technological revolution results in changes to productive forces and production relations. We now stand at the edge of an era where multiple information holders—programmers, business people, AI systems—can collaborate through division of labor to build logical systems in parallel, letting information flow transcend forms, bypass human intermediaries, and cross system boundaries and temporal evolution. LowCode conveys this core idea ambiguously and sometimes misleadingly.

Based on Reversible Computation theory, I propose a new concept: [Next-Generation Software Production Paradigm—NOP (Not Programming)](https://zhuanlan.zhihu.com/p/66548896).

NOP emphasizes the essential changes brought by this paradigm shift. As desktop internet shifted to mobile internet, a new concept was born—mobile-first. Similarly, in the LowCode era, software production should consider description-first: use domain models to explicitly express the system’s logical structure in a form amenable to Delta corrections, making it an asset that gradually frees our system logic from constraints of specific, ephemeral technical implementations and clearly records our business intent.

The descriptive part should have a clear boundary from conventional code. Consider Antlr4: previously, we added action annotations to descriptive EBNF grammars to control parser behavior. Antlr4 now mandates that grammar files describe only syntax structures; specific processing is centralized in Listener and Visitor handlers. Once descriptive information is fully isolated, Antlr is freed from Java—it can generate parsers for Java/Go/TypeScript from the same grammar, and automatically generate IDE parser plugins, formatters, IDE auto-completion, etc., achieving multiple uses.

In my view, traditional code embodies machine-running logic, adapting all expression to forms suitable for Turing machines or lambda-calculus machines. Domain logic has its own representational forms—its constituent elements act in ways that can be directly understood without reducing them to steps for some Turing machine. A language is a world; different worlds have different worldviews. LowCode isn’t about code being “low,” but about a different direction of expression.

LowCode does not mean letting people who don’t understand logic write code (though many products cultivate that illusion to sell). In fact, many mathematicians don’t write programs, but they understand logic deeply. In 5–10 years, the market will have many former programmers over 40 who may not keep up with the latest dev tech. With higher-level logical descriptions, they can still develop business logic and benefit from cutting-edge advances.

The LowCode platform NopPlatform, designed under Reversible Computation theory, is open-source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Introduction & Q&A on the Nop Platform_bilibili](https://www.bilibili.com/video/BV14u411T715/)
<!-- SOURCE_MD5:827a5110d93129cde84c5ec7a5f5405c-->
