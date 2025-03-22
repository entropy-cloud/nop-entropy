# How Does Nop Overcome the Limitation That DSL Can Only Be Applied to Specific Domains?

The **Nop platform** can be considered as a **Language Workbench**, providing comprehensive theoretical support and underlying tools for the design and development of **DSL (Domain Specific Language)**. When developing using the Nop platform, the primary focus is on expressing business logic using **DSL**, rather than using general programming languages. Some may wonder: if DSL is referred to as a domain-specific language, does that mean it can only be applied to specific domains? Does this inherently impose fundamental limitations when describing businesses?

In the past, frameworks like **ROR (Ruby On Rails)** were popular, and DSL concepts were briefly in vogue. However, these concepts have now largely faded from prominence. What sets Nop apart from other platforms? The answer is simple: **Nop is built on reversible computing theory from scratch**, making it the next-generation low-code platform. Reversible computing theory is a systematic approach to designing and constructing DSLs, addressing the theoretical issues inherent in traditional DSL design and application.

## 1. Horizontal DSL Decomposition: DSL Attribute Vector Space

A Turing machine can be viewed as a virtual machine that can simulate all other automatic machines. By continuously increasing the level of abstraction in virtual machines, we can obtain virtual machines that can run so-called domain-specific languages (DSL). However, since DSL focuses on specific domain concepts, it inherently cannot express general-purpose computing logic without becoming a general programming language, which would lead to information overflow and Delta items.

During the evolution of first-generation, second-generation, and third-generation programming languages, abstract layers were continuously added, but they remained general programming languages. By the fourth generation, we no longer end up with another general programming language but instead encounter an abundance of DSLs forming a **DSL forest**. Through these DSLs, we can represent and understand the structure of existing programs in a new way.

According to the core construction formula of reversible computing:  
`App = Delta x-extends Generator<DSL>`  
We can continuously apply Delta decomposition to obtain the following formula:

```
App = G1<DSL1> + G2<DSL2> + ... + Delta
App ~ [DSL1, DSL2, ..., Delta]
```

Using a series of DSL languages, we can glue together Generators and Delta items. If we treat Generator as background knowledge that does not need to be explicitly expressed when expressing business logic, each DSL can be viewed as a **Feature** dimension. Applications can then be projected onto a multi-dimensional Feature space. Each individual DSL indeed has limitations in expression, but multiple Features combined, along with additional Delta information, enable comprehensive descriptions.

## 2. Vertical DSL Decomposition: Multi-stage, Multi-level Software Production Line

The previous section discussed horizontal DSL decomposition within reversible computing theory. In this section, we introduce vertical DSL decomposition, which can be applied to traditional Model-Driven Architecture (MDA) and solve its inherent flaws.


When performing model derivation, we only derive an alternative result (generally stored in files with underscores as prefixes). We can then choose to inherit this alternative model, manually correct it, and add additional information through Delta reasoning (stored in files without underscores as prefixes). Each step of the inference relationship is a selectable component: **we can start from any step and begin, or completely discard all information derived from previous steps**.

For example, we can manually add an `xview` model without requiring it to have specific `xmeta` support, or directly create a new `page.yaml` file and write JSON code according to the AMIS component specifications. The AMIS framework's capabilities are not constrained by reasoning pipelines. Leveraging a pattern similar to deep learning's divide-and-conquer approach, we can fully harness model-driven power while optionally incorporating Delta information when necessary. This ensures that **the final product's capabilities are not limited by model expression capabilities**.

This also means that **during modeling, we no longer need to cover every detail; instead, we focus on the core and universal aspects**.

> `XORM = Generator<ExcelModel> + Delta`
>
> `XMeta = Generator<XORM> + Delta`
>
> `XView = Generator<XMeta> + Delta`
>
> `XPage = Generator<XView> + Delta`


## Non-Programming Means Non-Command Programming

Nop is the recursive abbreviation for **Not Operational Programming** (NOP). Non-programming refers to non-command programming, which aims to expand the scope of descriptive programming as much as possible. A DSL (Domain-Specific Language) can be viewed as a descriptive expression method tailored for business logic, focusing on how domain concepts describe the business itself rather than using general programming language terms to express step-by-step implementation of business functions.

Conversely, if we can find a descriptive expression method suited to the current business, then simplify it into the most concise textual structure and save it, it naturally becomes a DSL. In traditional programming domains, when we aim to elevate programming's abstract level, enhance software flexibility and adaptability, we repeatedly emphasize the importance of domain concepts, such as those in Domain-Driven Design (DDD) where a unified language (Ubiquitous Language) is promoted. However, in traditional programming domains, domain concepts are ultimately carried by general programming structures within the Ubiquitous Language, limiting their expression's freedom and richness.

On the other hand, many excellent program frameworks inherently correspond to an implicit DSL, but we rarely make it explicit. For example, SpringBoot's condition-based bean assembly mechanism can be extended using limited conditional tags under Spring 1.0 syntax, but SpringBoot itself does not define a DSL for this, resulting in its loss of declarative assembly capability and global assembly results' intuitiveness. Detailed analysis: [If we rewrite SpringBoot, what different choices would we make?](https://mp.weixin.qq.com/s/_ZVXESRqjSbObmrkDZoGMQ)

Nop platform does not use Spring or Quarkus, the third-party frameworks; instead, it opts to write IoC/ORM/Workflow/BatchJob etc. foundational engines from scratch. The most critical reason is adherence to reversible computation principles for engine design. **Each engine that holds intrinsic value corresponds to an internal model, which inherently corresponds to a DSL language**. Nop platform's key points are:

> **For each engine, clearly define its own DSL;**
>
> Leverage Nop platform's infrastructure for automatic DSL analysis, validation, caching, decomposition, and merging, as well as meta-programming.
>
> In this scenario, each engine only needs to handle its specific runtime logic, with extensive expandability handled during compilation via the Nop platform. Thus, the engine's runtime structure is significantly simplified.

In the Nop platform, the code volume for each engine generally scales less than that of corresponding open-source implementations. Additionally, it provides richer functionality, better expandability, and superior performance. See [Nop Platform vs SpringCloud Functionality Comparison](https://mp.weixin.qq.com/s/Dra8yf2O5VMJyEPox4dGBw).

> Nop platform's application scope extends beyond low-code visualization to include numerous foundational engines, enabling it to compete with SpringCloud's ecosystem in certain aspects.


## Four. Universal Meta-Model and DSL Structure Rules

The most common objections to DSL are:

1. High construction and maintenance costs
2. Significant differences in DSL syntax, leading to high learning costs
3. Difficulties in interaction between DSL and general programming languages


Internal DSL (Internal Domain Specific Language) is often recommended as a way to embed domain-specific languages within general-purpose programming languages. An internal DSL constructs a domain-specific language using the syntax and structure of a host general-purpose programming language, rather than requiring its own separate parser or compiler. This approach allows leveraging existing programming language tools and ecosystems, such as code editing, debugging, packaging, and deployment, while minimizing learning costs for users who do not need to master an entirely new language.

However, common internal DSLs have issues. They often emphasize surface-level similarities between DSL syntax and natural language, which is misleading (they are more similar to English than to natural language). This introduces unnecessary complexity. Additionally, typical internal DSLs rely on the host programming language's built-in type systems for form constraints, which can lead to unstable DSL syntax if the underlying language changes. There may be multiple equivalent ways to express the same domain logic, and the so-called DSL syntax is little more than an implicit convention. The parsing of internal DSLs typically depends on the host language's parser, making it difficult to perform domain-specific analysis outside the host language's context. This tight coupling between DSL syntax and semantics and the underlying programming language leads to limited attention paid to the DSL's own concept completeness, expandability, and invertibility.

The Nop platform addresses these issues by introducing a unified XDef meta-model that standardizes all DSL syntax and semantic structures. It provides unified development and debugging tools to assist in DSL development. Once you grasp the meta-model, you can immediately understand all DSL syntaxes, including decomposition and merging, differential customization, etc., without needing to learn each DSL individually. When developing a new engine with the Nop platform, you can reference existing DSLs using `xdef:ref` and achieve seamless integration of multiple DSLs. The XPL template language is used to implement command-style automation based on descriptive logic. For more details, see [Meta Programming in Low-Code Platforms](https://mp.weixin.qq.com/s/LkTIVGSrK9zomPW4bNiqqA).

