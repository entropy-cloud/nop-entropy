# 1. Is the code generator low-code?

Traditional code generators are one-time tools that do not support continuous incremental improvement, so they are not truly low-code. Typically, once the code is generated, all customizations require modifications to the generated code, which limits its flexibility and restricts it to specific constraints. When we make incremental adjustments to the model, the regenerated code often overrides manual changes, leading to lost customization.

In principle, the generated code and JSON-based formats are functionally equivalent. For example, in the Amis framework:

```javascript
result = renderAmis(json, data);
```

The `renderAmis` function acts as an interpreter that takes Amis JSON files and external data inputs to produce the final result.

For multi-parameter functions, currying (rendering them as single-parameter functions) is a common approach. This process effectively transforms:

```javascript
result = renderAmis(json)(data);
```

into

```javascript
component = renderAmis(json); // Compiled phase
result = component(data);   // Execution phase
```

From a mathematical perspective, the compilation process can be viewed as dividing a previously runtime function into two parts: a compile-time generator and a runtime function. This mirrors how functions are typically split in programming languages.

In theory, generating code is not limited to "continuous visual compilation." Modern techniques like on-the-fly compilation and dynamic recompilation allow for incremental updates without restarting the application. These methods enable changes to be applied dynamically while maintaining performance.

To implement incremental code generation, refer to my article:

- [Reversible Computing](https://zhuanlan.zhihu.com/p/64004026)
- [Implementation of Reversible Computing](https://zhuanlan.zhihu.com/p/163852896)

Currently, there is no widely accepted open-source solution for these challenges. The code generation approach still has many engineering issues to resolve.

The Nop platform's extensive metadata transformation can be seen as a form of multi-stage compilation. While it may not cover all aspects of traditional compilation, it demonstrates the potential for incremental and modular development approaches.

In conclusion, code generation is not limited to generating plain source code. It can also produce DSL (Domain-Specific Language) code or even general-purpose language code, depending on the framework's capabilities.


## 2. How is the core domain model of low-code defined?

Some low-code platforms excel in a small number of CRUD scenarios, but their domain models are often rigid and cannot handle complex, dynamic business processes. While they may be powerful for simple use cases, they lack the flexibility needed for evolving requirements.

In contrast, a good low-code platform should enable rapid, flexible development by defining a core domain model that supports:

- [Reversible Computing](https://zhuanlan.zhihu.com/p/64004026)
- [Implementation of Reversible Computing](https://zhuanlan.zhihu.com/p/163852896)

This means the platform should allow for continuous refinement and iteration without disrupting existing workflows.

Specifically, the Nop platform's metadata transformation capabilities can be viewed as a form of multi-stage compilation. Its ability to handle various data formats and transformations makes it a valuable tool for complex domain models.


## 3. Is DSL Turing complete?

A Domain-Specific Language (DSL) is not inherently more or less "Turing complete" than other programming languages. The real question is how the language is designed and implemented. For example:

- [Reversible Computing](https://zhuanlan.zhihu.com/p/64004026)
- [Implementation of Reversible Computing](https://zhuanlan.zhihu.com/p/163852896)

These concepts highlight that the choice of DSL should be based on specific requirements rather than a one-size-fits-all approach.

# DSL and Turing Completeness
A **DSL (Domain Specific Language)** can be **Turing complete**, meaning it can simulate any other Turing-complete language. However, a DSL is not necessarily required to be Turing complete. Its specific subset of knowledge and operations should support **reverse information extraction** while maintaining its descriptive nature.

A language is like a coordinate system, while a universal language is like an omnipotent coordinate system. Everything is expressed in this single coordinate system. A DSL, on the other hand, acts as a local coordinate system within a larger system (analogous to a differential geometry concept). By combining multiple local coordinate systems, we can form the entire coordinate system.

When solving practical problems, we use **DSL forests** to address them. A **Turing machine** is Turing complete because it can be considered as a virtual machine that can simulate all other automata. By continuously increasing the abstraction layers of this virtual machine, we obtain a **virtual machine** that can "run" a DSL. However, since a DSL focuses on domain-specific concepts, it inherently cannot express all possible universal logic efficiently without causing information overflow (Delta items).

# Visualization in Low-Code Development
## Is Visualization Essential for Low-Code?
For low-code platforms, **visualization** is often a crucial feature because it enhances the user experience and serves as a key differentiator. However, from a broader perspective, visualization is not inherently required. Visual representation implies that information exists in both textual (text-based) and visual forms, which are mutually convertible. While declarative languages cannot always reverse-engineer their output into source code, only a subset of declarative constructs can be reversed.

## The Broad Perspective
From a universal standpoint, the true development direction lies in reversible expressions: information should exist in multiple forms that can be interconverted. This allows information to break free from single-form constraints and facilitates unhindered flow (similar to the first industrial revolution, where energy transformed between different forms).

For example, the **AMIS framework** is self-contained and does not rely on visualization for its logic representation. Its meaning is solely based on its own structural definitions, allowing it to maintain multiple interpretations across different contexts and times.

In traditional text-based programming, we focus less on the completeness of information representation. Information resides in documentation, human brains, or external tools, making reverse extraction challenging without additional effort or specialized tools.

## Reverse Extraction
Further details can be found in my article:
\[From Reversible Computation to LowCode\]!https://zhuanlan.zhihu.com/p/344845973

# 5. Inherent Limitations of Low-Code?
Is low-code inherently limited by its nature?

Low-code platforms are often criticized for their reliance on domain-specific assumptions, which can restrict flexibility. While these platforms excel at reducing code duplication and leveraging internal engines for automation, they may struggle to adapt when underlying assumptions are challenged.

For example:
- A field-level requirement in a low-code application may become difficult to implement after encapsulation.
- Customizing individual components might require extensive manual adjustments or even rebuilding the entire framework from scratch.

## Overcoming Limitations
From a theoretical perspective, **reversible computation** offers a way forward. By continuously refining abstraction layers, we can develop higher-order models that better accommodate diverse domain requirements without losing the foundational structure.

# 6. Visual Logic Representation
Visualization in Logic Representation

Visualization is not suitable for detailed logic representation because it inherently limits information density. While visualization excels at leveraging human parallel processing capabilities for pattern recognition and organization, it often lacks the precision needed for intricate logical structures beyond basic flow diagrams.

When using code for logic representation:
1. Mathematical operations (function calls, arithmetic, operators) leverage deeply learned mathematical knowledge.
2. Programming constructs (syntax elements, control structures) mirror natural language patterns.
3. Code's textual structure naturally reflects execution order.
4. Parentheses, indentation, and function calls establish nested structures that directly correlate with the system's stack behavior.

In comparison, flow diagrams like decision trees or state machines better align with current runtime environments. For instance:
- A decision tree can be visualized as a hierarchical structure, mirroring the program's control flow.
- Behavior trees, commonly used in gaming and robotics, provide a clear mapping between logic and execution layers.

For example, **behavior trees** are often implemented using tools like Unity or Unreal Engine, as they offer a clear visualization of complex logic while maintaining performance efficiency.

# 7. Backend Storage for Low-Code

From the storage requirements perspective, using a Key-Value (K-V) format to store data as key-value pairs is the most flexible approach. This is because the underlying database operates logically in this manner. In reality, TiDB can be viewed as an abstraction layer built on top of TiKV, a distributed key-value store.

When storing data in a K-V format, we gain flexibility:
- The ability to reimplement and customize all database mechanisms.
- All operations are performed on the Key-Value storage layer, which essentially boils down to implementing a simplified database.

In an ideal scenario, we can abstract away the dependency on underlying storage structures using an ORM (Object-Relational Mapping) engine. This abstraction allows us to store data:
- Directly in database tables,
- As multiple generic columns, or
- In a Key-Value manner into a column.

These changes are transparent to the business layer, ensuring that the application logic remains unaffected by the storage mechanism.

