


In computer science, there exist two fundamental worldviews: the Turing machine and Lambda calculus. These correspond to two distinct technical routes for achieving universal computation. However, in quantum mechanics, there are three fundamental worlds: the Schrödinger world, the Heisenberg world, and the Dirac world. Why does computer science have two basic worldviews, while quantum mechanics has three? What is the relationship between them?



The conclusion is that the basic technical routes in computer science for achieving universal computation can also be viewed as having three pathways, which correspond to the three worlds of quantum mechanics:

1. **Turing machine** corresponds to the Schrödinger world.
2. **Lambda calculus** corresponds to the Heisenberg world.
3. **Reversible computation** corresponds to the Dirac world.

The specific analysis follows:



1. In the Schrödinger world, the Hamiltonian is fixed, and the state evolves.
   - The time evolution of the system is governed by the Schrödinger equation.
2. In the Heisenberg world, the states are fixed, and the operators evolve.
   - The time evolution of the system is described by the Heisenberg equation.
3. In the Dirac world (interaction picture), both the states and the operators are time-evolving.
   - The Dirac equation describes how the state and operator evolve simultaneously.

In the Dirac world, we decompose the Hamiltonian into the known part \( H_0 \) and the perturbation \( H_1 \):

$$
H = H_0 + H_1
$$

We then investigate how the system evolves away from the known model. The focus is on the evolution of the difference description in the interaction picture.

In the Dirac world, both the state and the operator evolve with time:

$$
i\hbar \frac{d}{dt} |\psi_I(t)\rangle = H_1 |\psi_I(t)\rangle \\
i\hbar \frac{d}{dt} |A_I(t)\rangle = [A_I(t), H_0]
$$

The measurement results in the Dirac world are consistent across all three worlds:

$$
\langle \psi_S(t) | A_S |\psi_S(t) \rangle = \langle \psi_H | A_H(t) | \psi_H \rangle = \langle \psi_I(t) | A_I(t) | \psi_I(t) \rangle
$$

Interestingly, the Dirac world is the most commonly used framework in practical physical computations. This is due to the existence of a specialized branch within mathematical physics: perturbation theory. It systematically explores how small disturbances affect the evolution of known models.

By comparing quantum mechanics and computer science, we observe an intriguing correspondence between their worlds:

1. The Turing machine corresponds to the Schrödinger world.
2. Lambda calculus corresponds to the Heisenberg world.
3. Reversible computation corresponds to the Dirac world.





The Turing machine is a structure-bound machine with an enumerable set of states and a finite number of operation commands. While it can only process a finite number of operations, it can read and store data from an infinitely long tape. For example:

- Our everyday computers have their hardware functionality fixed at the factory level.
- However, through software installations, we input different data files, allowing the computer to produce arbitrarily complex outputs.

The computation process can be expressed as:

$$
\text{Output} = \text{Fixed machine} \, (\text{Infinite input})
$$



Whereas the Turing machine is structure-bound with an enumerable set of states and finite operations, lambda calculus is function-based with a focus on composition. The core concept is that functions can be treated as machines.

- **Function composition** remains a function.
- This allows for recursive combinations, enabling the creation of increasingly complex machines.
- Even with a constant input (e.g., 0), we can generate arbitrarily complex outputs through recursive functions.

The computation process can be expressed as:

$$
\text{Output} = \text{Fixed machine} \, (\text{Constant input})
$$



Reversible computation is a framework where both the state and operator evolve with time, much like in quantum mechanics' interaction picture. It explores how systems can be reversed:

$$
\begin{aligned}
Y &= F(X) \\
&= (F_0 + F_1)(X_0 + X_1) \\
&= F_0(X_0) + \Delta
\end{aligned}
$$

By introducing a new operator \( \oplus \), which represents a different kind of composition, we can express the output as:

$$
Y = F(X) \oplus \Delta
$$

Here, \( \oplus \) does not represent simple addition but rather a specific form of composition that may involve negative elements. This leads to interesting outcomes where the result could be an increase or decrease depending on the context.



In physics, perturbation theory is a cornerstone of research. It systematically investigates how small disturbances affect known models:

$$
\begin{aligned}
Y &= F(X) \\
&= (F_0 + F_1)(X_0 + X_1) \\
&= F_0(X_0) + \Delta
\end{aligned}
$$

The introduction of \( \oplus \) and \( \Delta \) allows for a more flexible representation:

$$
Y = F(X) \oplus \Delta
$$

This can lead to outcomes where the result is not merely an addition but may involve subtraction, depending on the specific context.



The provided image serves as a visual aid for understanding the computational structures discussed. It is not directly translatable but helps in comprehending the relationships between different models.


**Reversible Computing Theory introduced a new software construction formula that translates the computational pattern into a line of concrete technical approaches.**

```
   App = Biz ⊕ G1(DSL1) ⊕ G2(DSL2) + ...
```

* **App**: The target application program  
* **DSL (Domain Specific Language)**: A specialized programming language tailored for specific business domains, serving as the textual representation of domain models  
* **Generator**: A component that generates derived code based on domain models. It includes independent code generation tools and meta-programming techniques for template expansion  

* **App**: The application to be built  
* **DSL**: Domain Specific Language, which is used to express domain knowledge in a structured manner  
* **Generator**: A tool or system that, based on the domain model provided by DSL, repeatedly applies generation rules to derive code  
* **Delta (Δ)**: The difference between the logic of the target application and the current application. It represents changes such as adding, modifying, replacing, or deleting functionalities  

* **x-extends (X-Extends)**: A mechanism in Aspect-Oriented Programming (AOP) that allows a program to extend its behavior by attaching additional behaviors (advisors) to specific points in the code. In the context of Delta, it refers to operations like adding, modifying, replacing, or deleting components of the application  

**DSL (Domain Specific Language)** is a high-density representation of critical domain information. It directly drives the Generator to produce code by issuing instructions based on domain knowledge. This is analogous to Turing's computation model, where a machine processes input data through a series of predefined rules. If we treat the Generator as a symbol manipulation tool, its execution and rule composition are akin to lambda calculus (λ-calculus). The Delta operation, in this sense, represents a unique and fascinating operation because it requires a fine-grained ability to collect changes across all levels of the system, enabling the isolation and combination of differences.

**The Turing machine's ability to implement a complete computation model lies at the heart of Reversible Computing Theory. A Turing machine can simulate all other machines. By continuously enhancing the abstraction level of virtual machines, we aim to build a Turing-complete system. However, in today's era of widespread computing, the most pressing issue is how to effectively perform computations while managing resource constraints.**

**Computational efficiency depends on finding shortcuts (heuristics) to solve problems more quickly. These heuristics are rooted in an understanding of the problem's intrinsic characteristics. The representation (Representation) itself serves as a means to solve problems, as transforming the problem into a representable form often reveals its underlying structure. Reversible Computing leverages domain models and Delta differences to offer a systematic approach to problem-solving, making complex systems more tractable.

> For more information on transformation techniques, see [Beyond Simple Injections](https://zhuanlan.zhihu.com/p/550923860).

Based on the principles of Reversible Computing, we can derive a generalized software construction method:

```
$$
\begin{aligned}
App &= Biz \oplus G1(DSL1) \oplus G2(DSL2) + ...\\
&\equiv (Biz, DSL1, DSL2, ...)
\end{aligned}
$$

If we consider the Generator to include components like Translator or Transformer, which can handle complex transformations similar to the introduction of the Poisson bracket in physics, then App can be viewed as a set of characteristics (attributes) defined by various DSLs. This approach reflects the evolution of programming languages over generations: while first-generation languages focused on basic arithmetic operations, second-generation languages introduced logical constructs, and fourth-generation languages enabled high-level domain-specific modeling using natural language. However, **fourth-generation programming languages** are not truly general-purpose; they remain specialized for specific domains.


# Nop Platform

By integrating DSL construction with Delta differences, the Nop platform provides a novel framework for software development. It enables automatic execution of x-extends operations, allowing developers to build modular and scalable applications. As an implementation of Reversible Computing Theory, Nop represents the next generation in low-code development platforms, offering a flexible yet powerful environment for domain-specific problem-solving.

```
   App = Biz ⊕ G1(DSL1) ⊕ ... (x-extends)
```

The Nop platform is built on the principles of Reversible Computing. It internalizes the Delta differences and DSL constructs to automate the generation and execution of domain-specific solutions. As a reference implementation, it demonstrates how reversible computation can be applied practically.

> For a visual representation of the Delta pipeline, see **Delta Pipeline Visualization** at [../tutorial/delta-pipeline.png](../tutorial/delta-pipeline.png).

Based on the Excel data model, the Nop platform automatically generates a complete set of front-end and back-end code, including backend entities, GraphQL services, and frontend pages. Specifically, the logical reasoning chain from backend to frontend can be broken down into four main models:

1. **XORM**: Data model oriented towards the storage layer
2. **XMeta**: Data model oriented towards the GraphQL interface layer, which directly generates GraphQL type definitions
3. **XView**: Business logic-oriented front-end model using form, table, and button elements, independent of the front-end framework
4. **XPage**: Page model that specifically uses a certain front-end framework

The entire generation process can be expressed as follows:

$$
\begin{aligned}
XORM &= Generator\langle XExcel \rangle + \Delta XORM \\
XMeta &= Generator\langle XORM \rangle + \Delta XMeta \\
GraphQL &= Builder\langle XMeta \rangle + BizModel\\
XView &= Generator\langle XMeta \rangle + \Delta XView \\
XPage &= Generator\langle XView \rangle + \Delta XPage\\
\end{aligned}
$$

Each step in the reasoning process is an optional module: **You can start from any step or skip all previous steps and their inferred information**. For example, you can manually add an xview model without requiring specific xmeta support, or directly create a page.yaml file following AMIS component standards to write JSON code, as the AMIS framework's capabilities are not constrained by the reasoning pipeline.

In daily development, we often encounter some logical structures that have similarities and certain **imprecise derivational relationships**. For instance, there is a close relationship between backend data models and frontend pages. In simple cases, you can directly derive corresponding CRUD pages from data models or infer database storage structures from form fields. However, this imprecise derivational relationship is difficult to capture and utilize with existing technical tools. If you enforce certain association rules, they can only be applied in highly restricted specific scenarios and lead to incompatibilities with other technical methods, making it difficult to reuse existing tools while adapting to evolving demands.

The Nop platform provides a standard technical approach based on reversible computation theory for implementing this dynamic similarity:

1. **Using embedded meta-programming and code generation**: Any structure A and C can establish a reasoning pipeline
2. **Breaking down the reasoning pipeline into multiple steps**: A → B → C
3. **Further differentiating the reasoning pipeline with differencing**: A → `_B` → B → `_C` → C
4. **Allowing temporary storage and transmission of information for steps not requiring extension**

The NopPlatform, designed based on reversible computation theory, is open-source:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible computation principles and Nop platform introduction (Bilibili video)](https://www.bilibili.com/video/BV1u84y1w7kX/)

