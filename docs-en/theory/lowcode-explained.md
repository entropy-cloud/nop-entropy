# 1. Is a code generator low-code?

Traditional code generators are one-off scaffolding and do not support continuous incremental improvements, so they are not low-code. Typically, once code is generated, all customizations require changing the generated code, thereby departing from the original model constraints. When we make incremental adjustments to the model, regenerating code overwrites everything and loses the manual changes.

In principle, generating code is not fundamentally different from interpreting based on JSON. For example, formally, we can view the AMIS framework’s runtime as:

```
result = renderAmis(json, data)
```

renderAmis acts as an interpreter that accepts an AMIS JSON file and externally provided data, producing the final result at runtime.

We know that multi-argument functions can be turned into single-argument functions via currying, so the process is equivalent to

```
result = (renderAmis(json))(data)
```

That is, renderAmis combines with json to produce a function, and then we apply that function to data.

If the information in renderAmis(json) is already determined at compile time, we can optimize it during compilation to obtain a compiled component.

```
component = renderAmis(json) // executed at compile-time
result = component(data)   // executed at runtime
```

Mathematically, compilation can be viewed as splitting a function that originally ran at runtime into two parts: a compile-time function generator plus a runtime function. This process can be regarded as lifting a function to a functional—or, in physics parlance, an operator.

In theory, code generation is capable of “continuous visual compilation.” As long as we leverage Just-In-Time techniques to implement dynamic Delta compilation (Delta-oriented generative programming), anything an interpretive model can do can also be achieved by multi-stage compilation.

For how to implement incremental code generation, see my articles
[Reversible Computation]!https://zhuanlan.zhihu.com/p/64004026

[Technical Implementation of Reversible Computation]!https://zhuanlan.zhihu.com/p/163852896

Of course, there is currently no publicly available, out-of-the-box solution in the industry; there are many engineering challenges to solve when relying on code generation.

The Nop platform’s extensive structural transformations during the metaprogramming stage can be regarded as a compilation process, so overall it is a kind of multi-stage compilation, not just the single stage of generating general-purpose language source code.

Code generation may output DSL code rather than general-purpose language code. For example, the Nop platform currently outputs AMIS code on the frontend, where the AMIS engine is responsible for interpretive execution. Essentially, code is merely a carrier of information. Once business information is precisely expressed, it should be able to be carried by multiple forms—especially it should be possible to reverse-extract the original programming intent.

## 2. How should low-code define its core domain model?

Some low-code platforms appear very powerful in a small set of hard-coded scenarios and can rapidly implement certain businesses in a fixed pattern. However, we believe true power lies in the genes of inner evolution, not in piled-up features. Dinosaurs were powerful, yet were ruthlessly eliminated as the environment changed. Existing low-code products mainly still use brute-force enumeration, with a large number of built-in features, but technology evolves continuously—can they adapt quickly? Not to mention anything else, migrating to the latest foundational technology stack is a clear challenge. Many features are built with once-popular techniques that may be bound to a specific framework version.

Low-code’s capability comes from modeling, and a powerful low-code system should support instant creation of new models and free extension of existing ones. This requires the underlying architecture to at least have a metamodel definition.

All models in the Nop platform are uniformly defined using the xdef metamodel, and the xdef metamodel is constrained by the xdef metamodel itself—i.e., the meta-metamodel is still xdef. The platform’s core is essentially a general Tree transformation and loading mechanism, while a DSL is merely a Tree with semantics.

Concretely, our approach is to define the xdef metamodel, then immediately derive the designer automatically, and in IDEA provide a unified plugin that, based on the metamodel, automatically implements syntax hints and supports DSL breakpoint debugging, etc. So essentially it’s a language-oriented programming paradigm: before solving a business-domain problem, first establish a domain-specific DSL, then develop the business in that DSL; the platform’s role is to reduce the cost of developing and extending DSLs to something akin to writing a single function.

If low-code is to target a broad programming domain, not just a few CRUD scenarios, its core domain model should not be a small set of hard-coded business models, but rather the capability to rapidly create new domain models via a metamodel.

## 3. Does a DSL need to be Turing-complete?

A DSL can be Turing-complete or not, but for the subset that involves domain knowledge, it should support reverse information extraction; this subset is in principle declarative and not Turing-complete. A language is like a coordinate system; a general-purpose language is a universal coordinate system in which all information is expressed, whereas a DSL is a local coordinate system. Multiple local coordinate systems glued together form the overall coordinate system (akin to the concept of a differentiable manifold). So in practice we solve problems through a forest of DSLs. The fundamental reason a Turing machine achieves Turing-completeness is that it can be viewed as a virtual machine—it can simulate all other automatic computing machines. If we keep raising the abstraction level of the VM, we get a VM that can directly “run” domain-specific languages (DSLs). But because a DSL focuses on domain-specific concepts, it necessarily cannot express all general computation most conveniently (otherwise it becomes a general-purpose language), which inevitably leads to some information spilling over as the so-called Delta term.

## 4. Is visual editing indispensable for low-code?

For low-code products alone, visual editing may be essential, because it is a very important selling point. But in the broader sense, visualization should not be mandatory. Visualization means the same piece of information simultaneously has two representations: text and visual; moreover, these two representations can be converted reversibly. Declarative content does not necessarily allow reverse derivation from the rendered result back to source; only a reversible subset of declarative content is reversible.

Broadly speaking, the true direction is reversible forms of expression: the same information has multiple forms of expression that can be mutually transformed, ultimately freeing information from the constraints of a single form and enabling unobstructed, free flow (the First Industrial Revolution stemmed from the discovery that energy can be converted between different forms). Typically, AMIS’s backward compatibility advantage is unrelated to visualization. It merely means that AMIS’s information expression is, to some extent, self-complete: its semantics depend only on its own structural definition, so it can have multiple interpretations. At different times and in different technical environments, we can provide different execution-layer representations for AMIS’s logical representation.

Traditionally, when programming with code, we don’t pay much attention to the completeness of descriptive information. Much information lives in documents, in developers’ minds, or dispersed across external Turing-complete languages and frameworks, making reverse extraction difficult by simple means—and making it even harder to provide logically equivalent alternative forms of expression.

For further discussion, see my article
[LowCode Viewed Through Reversible Computation]!https://zhuanlan.zhihu.com/p/344845973

## 5. Does low-code have inherent limitations?

Is low-code only suitable for specific domains? Do programs built on low-code have a strong dependency on specific domain models, thereby essentially losing flexibility?

Low-code’s power lies in embedding many domain-related assumptions, thereby reducing unnecessary repeated expressions (the engine’s internal reasoning reduces various manually written association and transformation code). But once local domain assumptions are broken, will the entire program structure be destroyed?

For example, if the requirement is at the field level, how do we customize at the field level after low-code wrapping? How do we customize and modify the wrapped components? Or do we have to special-case the entire page, or even abandon the entire framework and start over?

Looking back at successful experiences in physics, we solve problems progressively: zeroth-order model, first-order model, second-order model... As more specialized information enters our cognition, we don’t overturn models previously built on limited general information; instead, we keep adding higher-order models to describe the differentiated parts.

Reversible Computation provides a complete solution at the theoretical level. It indicates that, in essence, low-code is not subject to these limitations.

## 6. Graphical expression of logic

Graphical approaches are indeed not suitable for expressing detailed logic, because their information density is low. The advantage of graphics is that they can fully leverage the human brain’s parallel pattern-recognition ability to quickly spot certain organizational patterns in two or three dimensions. But overall, human culture is not built around graphical expression; apart from mathematical symbols, graphical expression generally lacks cultural background, resulting in limited information transfer.

When we express logic with code, it implicitly leverages a lot of information:

1. Function calls, arithmetic operations, associativity of operators, etc., reuse the mathematical knowledge we spent years learning
2. Program syntax elements and writing form resemble our habitual natural language
3. The sequential order of code text implicitly expresses the execution order at runtime
4. Nesting via parentheses, indentation, and function calls corresponds directly to changes in the program’s runtime stack

Compared to flowcharts, what is actually closer to modern program runtime is some kind of Tree structure that directly supports the stack concept. Through Tree nesting, we can implicitly express the basic runtime structure of “goto” to some state and then necessarily return to the previous state. The behavior tree commonly seen in games can be regarded as an example of expressing logic through a Tree structure.

https://opsive.com/support/documentation/behavior-designer/what-is-a-behavior-tree/

## 7. Backend storage for low-code

Purely from storage needs, the backend storing all data as key-value pairs in K-V format is the most flexible approach. Because that’s logically how the underlying database works! In fact, the distributed database TiDB can be seen as a wrapper layer built on distributed TiKV.

Once we store data in KV format, we gain a kind of flexibility: the flexibility to reimplement and customize all database mechanisms. Everything we do on top of KV storage is basically implementing a simplified database.

Ideally, we can use an ORM engine to shield the dependency on the underlying storage structure. Whether it stores directly as database tables, reserves multiple generic-type columns, or stores key-value pairs in a vertical table, these changes should be imperceptible at the business layer. Conceptually, the ORM engine provides a business-oriented virtual database.
<!-- SOURCE_MD5:0a87ea73c95dae578127c29ff7538415-->
