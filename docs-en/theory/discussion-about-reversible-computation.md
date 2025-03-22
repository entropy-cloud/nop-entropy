# Discussion on Reversible Computation—Answered by Qian Qian

In [Why does computer science have two worlds: the Turing machine and Lambda calculus, while quantum mechanics has three?](https://www.zhihu.com/question/614938288/answer/3147722439), I proposed an inspired perspective by mapping the Turing machine, lambda calculus, and reversible computation to the three distinct worlds of quantum mechanics—Schrödinger's picture, Heisenberg's picture, and Dirac's picture. This approach highlights that reversible computation represents a third pathway, inspired by physics, for achieving computational completeness while grounded in physical intuition.

For graduate-level students who have studied multiple models of Turing-complete computation, both intellectually and emotionally, it may be challenging to grasp these concepts due to their overwhelming complexity. Some might even joke: "Why does computer science have five letters, while quantum mechanics has four? Is there any relationship between them?"

At its core, reversible computation originates from a distinct set of physical intuitions. To truly understand it, one must break free from conventional computing science paradigms. As for the significance of reversible computation, it primarily lies in deriving concrete solutions—such as introducing specialized syntax into programming languages—to address fine-grained software issues that conventional models cannot resolve.

Secondly, numerous practical examples within the industry can be unified under the framework of reversible computation. For instance, Docker technology serves as a standard application of reversible computation principles.

In this text, I aim to address common questions and explain the underlying intuition behind reversible computation theory, particularly focusing on the problems it solves that conventional programming languages cannot.

## 1. The foundation of Turing-complete programming languages is diverse, yet graduate-level curricula often focus on teaching three or four primary computational models: Why only the Turing machine and Lambda calculus represent two worlds?

The text does not claim that there are only two Turing-complete programming languages; nor does it suggest that there are only two styles of Turing-complete programming languages. In reality, there are many varieties of Turing-complete computation models.

This raises an intriguing question: **Are there a finite or infinite number of Turing-complete computational models?** If the former, why do we allow only these specific models to exist in nature? If the latter, how can we possibly understand an infinite number of such models? Are there even more fundamental models that exist beyond these?

Many students may have learned about numerous Turing-complete models during their academic careers. However, they typically study these models as isolated entities. **How should one view these models collectively as a whole?** Beyond their shared property of being Turing-complete, what other commonalities exist among them?

This question may spark curiosity: **Is it not fascinating?**

From a computer science perspective, standing within its own domain makes it difficult to see the bigger picture. For instance, Lamport, the inventor of the Paxos algorithm and a Turing Award winner, revolutionized distributed systems by drawing parallels between relativity theory and computing science. **What deeper connection exists between the Turing machine and Lambda calculus beyond their computational completeness?** This question is challenging to answer within pure computer science. However, drawing parallels with quantum mechanics provides new insights.

Why does quantum mechanics have only three worlds? Could there be more? Quantum mechanics could, in principle, describe an infinite number of worlds. However, Schrödinger's picture, Heisenberg's picture, and Dirac's picture collectively represent the primary frameworks within which quantum mechanics operates.

Mathematically, this is expressed as:

$$
\langle \psi_S(t) | A_S |\psi_S(t)\rangle = \langle \psi_H | A_H(t) |\psi_H\rangle
$$

This equation indicates that both pictures yield consistent predictions for the final observable state. However, they represent two distinct ways of understanding quantum evolution.

Clearly, this corresponds to two extreme perspectives within quantum mechanics. Dirac's picture can be seen as a blend of these two frameworks—a compromise between them. Yet, its focus lies on the Delta operator: how an infinitesimally small perturbation to a known state generates a new state, and how we can utilize the solutions of known models to solve for this perturbation.

Just as quantum mechanics distinguishes between operators (quantum observables) and states (quantum systems), programming languages similarly distinguish between functions and data. Confronting new challenges, the Turing machine chooses fixed operations while accepting infinite inputs; Lambda calculus, on the other hand, fixes data and allows functions to vary. This duality reflects two fundamental concepts in computer science.

From a quantum mechanics perspective, operators evolve independently of states, whereas from a pure programming perspective, functions and data are fundamentally decoupled. This correspondence provides a fascinating bridge between the two domains.

The question arises: **Why does quantum mechanics have only three pictures? Could there be more?** In theory, yes—there could be an infinite number. However, Schrödinger's, Heisenberg's, and Dirac's pictures collectively represent the primary frameworks within which quantum mechanics operates.

In terms of operators and states:

$$
Y = F_0(X) + \Delta
$$

This equation encapsulates the essence of quantum evolution. It suggests that when both sides are known, the change (Delta) can be precisely calculated. This is a cornerstone of quantum mechanics.

From a programming perspective, functions and data remain distinct entities. When faced with new challenges, the Turing machine selects fixed operations while accepting infinite inputs; Lambda calculus fixes data and allows functions to vary. This duality mirrors the two worlds of computer science.

The question remains: **What deeper connection exists between these two worlds beyond their shared property of computational completeness?** This is a profound inquiry that quantum mechanics helps illuminate.

In summary, the Turing machine and Lambda calculus represent two distinct worlds within computer science—beyond mere computational power. Quantum mechanics offers a bridge to understand this duality through its three pictures, each providing unique insights into the nature of computation.

This discussion raises another intriguing question: **Are there more fundamental models beyond these?** And if so, how can we reconcile them?

For the concept of duality, please refer to this article on Zhihu: [How to Imaginatively Understand Dual Vector Space](https://www.zhihu.com/question/38464481/answer/2446175090)


## 2. Building a New Framework
The purpose of building a new framework is to discover new things that were not there before, rather than reinterpreting existing ones. Is reversible computation like a one-liner joke?

Docker technology can be summarized mathematically as follows:

> `App = DockerBuild<DockerFile> overlay-fs BaseImage`

* DockerFile is equivalent to a DSL for defining the contents of an image.
* The Docker Build tool acts as a Generator that parses and executes the DSL to generate slices of images.
* Multiple image slices are then composed using OverlayFS, a delta-based file system.

It's evident that Docker demonstrates the computational pattern proposed by reversible computation theory as a specific case of its software construction formula:

> `App = Delta x-extends Generator<DSL>`

Reversible computation provides a unified explanation for technologies like Docker, which includes delta-based concepts.


## 1. Reversible Computation Overview
I first introduced the concept of reversible computation in 2007, while Docker was released in 2013. Clearly, this is a case where theory precedes practice by many years and accurately predicts the structural needs of practice.

Reversible computation is not just about explaining isolated technological phenomena but maps them to abstract mathematical concepts, enabling rapid application across broader domains. Specifically, it points out that each DSL can define a delta model space with semantic-aware merge and generation mechanisms. Docker's approach cleverly selects OverlayFS as the underlying delta model space. Over the years, Linux has accumulated numerous command-line tools that automatically become delta generators, thus activating existing technical assets. This idea is easily extendable to other delta model spaces, such as k8s 1.14's Kustomize tool, which applies similar principles to YAML files, introducing a new customization scheme. Similar Delta schemes have been in use for years; see [From Reversible Computation to Kustomize](https://zhuanlan.zhihu.com/p/64153956).

Some might think Docker is merely an application-specific technology unrelated to foundational computational models, but I argue that all software-driven operations inherently reflect some form of computation. Docker's success lies in its theoretical abstraction, which can be extracted and applied to other scenarios.


Is reversible computation just a joke played on computer scientists? While it is a small part of computer science, it represents the foundation of computational models. Is it a base or a mistake?

The most common misunderstanding about my reversible computation theory is conflating it with hardware-related concepts like reversible computers. Reversible computing originates from Rolf Landauer's 1961 IBM paper, "Irreversibility and Heat Generation in the Computing Process." His research showed that erasing one bit of information requires at least kT ln2 energy to be dissipated into the environment. To minimize this energy dissipation, reversible logic gates are used. Reversible computing has always been a niche field focused on hardware implementation, similar to quantum computing research.

My reversible computation theory differs from reversible computers in that it focuses on high-level abstraction rather than low-level physical implementation. To avoid confusion with reversible computers, I use "Reversible Computation" in English, while the corresponding term for reversible computers is "Reversible Computing."

Inspired by both reversible computers and deep neural networks' backpropagation, Leo (a programming language I proposed) incorporates an abstract support structure called NiLang (see [NiLang - Reversible Computing, Differentiating Everything](https://zhuanlan.zhihu.com/p/191845544)). While NiLang's target is traditional computer hardware storage structures, the reversible computation theory emphasizes defining delta model spaces and reversible operations at a flexible level. However, not all programming can be reversed.


Some people might think Docker is just an application-level technology with no relation to foundational computational models. But everything driven by software inherently reflects some form of computation. Docker's success lies in its theoretical abstraction, which can be extracted and applied elsewhere.


Reversible computing theory can be considered as an advancement in the software engineering domain, building upon traditional object-oriented and component-based theories. It addresses inherent challenges in component theory at a theoretical level and resolves fine-grained software reuse issues.


## 1. Inheritance in Object-Oriented Programming
In object-oriented programming, inheritance is viewed as a partial order relation A > B, where derived class A is richer than base class B. However, the specifics of what is inherited remain ambiguous.

2. Component Theory's Combination Over Inheritance
Component theory defines combination as A = B + C, where component C can be reused multiple times through E = D + C.

3. From Inequality to Addition: The Natural Introduction of Subtraction

```
X = X + Y + Z
Y = X + Y + D = X + (D - Z) = X + Delta
```

The introduction of subtraction mirrors the addition of negative values in mathematics, effectively expanding the solution space for previously intractable problems.


Carnegie Mellon University's software engineering lab outlines the evolution of software reuse:
- Continuous refinement of grain size.
- Significant challenges remain in achieving true coarse-grained reuse at a theoretical level. For detailed insights, refer to [Reversed Computing: Delta Oriented Programming](https://zhuanlan.zhihu.com/p/377740576).


From the perspective of reversible computing:
- The principle shifts from component reuse (**相同可复用**) to related reusable components (**相关可复用**).
- Any Y and X can be connected via Delta, enabling reuse without traditional part-whole hierarchical relationships.


At a practical level, system-level reuse corresponds to incremental development of product Y without modifying the base product X's source code. This translates abstractly to Y = X + Delta.


Reversible computing theory specifically addresses:
- Studies on delta-based operations.
- Challenges faced by financial institutions in deeply customizing core banking applications without altering the base product's source code. Can we achieve class-like inheritance for software products?


To better understand reversible computing, I open-sourced a reversible computing reference implementation: Nop platform (https://www.gitee.com/canonical-entropy/nop-entropy).

The Nop platform introduces a specialized programming syntax for reversible delta operations. For details, refer to [XDSL: Generalized Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300). It enables coarse-grained reuse without modifying the base product's source code (refer to [Incremental Development Without Source Code Modification](https://zhuanlan.zhihu.com/p/628770810)).



Reversible computing introduces a new paradigm distinct from traditional computer science. Its methodology is rooted in physics' entropy increase principle and perturbation theory.

In calculus, Taylor series expansion is well-known:

$$
y = 3(x + dx)^2 + 2(x + dx) = 3x^2 + 2x + (6x + 2)dx + 3dx^2
$$

Mathematically, we can collect and assign meaning to scattered delta quantities. Physically, these deltas correspond to meaningful changes, while in software, such mechanisms are absent.



1. **Intuition 1: Delta's Independence**
   - In the Taylor series construction, deltas originate from all system points.
   - Independent storage and management of deltas are crucial for merging with the original system.

2. **Intuition 2: System's Response to Deltas**
   - The system's response to each delta is transparent.
   - Transparent application of deltas across the entire system hierarchy ensures consistent updates.

3. **Intuition 3: Coordinate System in Databases**
   - A coordinate system must be established for database rows and columns.

2. **Intuition 2: The Type System Is Not a Suitable Coordinate System**  
   Because the type's basic assumption is that multiple objects share the same type, it leads to ambiguity when used for identification. A typical issue is that we rely on `id` rather than the type for positioning in a list of buttons. If you understand group theory, you can see that the same physical fact can be projected into different spaces for observation, revealing different difference construction patterns. For example, for a function's definition, it can be projected into a binary space, where XOR operations can be applied between any two function representations. Similarly, it can be projected into a common text space using the diff algorithm, and Git's application is based on this difference relation. Projecting a function into a type system is also a universal approach, as some people use the type system to establish differences, such as in Compositional Programming (https://www.bilibili.com/video/BV1Ph4y1M7aB/). Generally, the type system acts like a two-level coordinate system, defining classes (modules) and methods.

3. **Intuition 3: We Turned to Field Theory's Worldview From Coordinate Concepts**  
   Component black-box models resemble the worldview of high school-level Newtonian mechanics, which is fully mechanized: a solid's motion is entirely described by its center-of-mass coordinates and size, shape, and orientation parameters. The internal structure of a solid cannot be observed or is irrelevant, but solids interact with each other through direct contact. For classical mechanics, even more advanced viewpoints will eventually shift to Lagrangian or Hamiltonian formulations, whose essence lies in field theory's worldview. A field (Field) essentially creates an omnipresent coordinate system, allowing a physical quantity to be defined at every point in the coordinate system. Fields have infinite degrees of freedom but are finite and definable within a coordinate system, enabling precise measurement of local changes at each point.

4. **Intuition 4: The Same Physical Fact Can Be Expressed in Different Coordinate Systems**  
   In specific domains, there exists an inherent coordinate system that best fits the domain's requirements. Programming languages can be seen as defining a semantic space, providing a coordinate system for expressing physical facts. A domain-specific language (DSL) offers a coordinate system tailored to its domain. For example, when requirements change, DSLs tend to show only localized changes, while general programming languages may reveal extensive code adjustments. This is akin to describing a circle in Cartesian coordinates by changing both x and y coordinates, whereas polar coordinates only alter the radius. In this way, using a suitable coordinate system can reduce dimensions.

5. **Intuition 5: DSL Models Both Define Entities Within a Coordinate System and Comprise the Coordinate System Itself**  
   There's no need to add extra descriptions to expand points within models; using the DSL's structure for identification suffices. This is similar to differential calculus' approach, where derivatives describe changes without needing additional terms. In the coordinate system, describing movement inherently involves its direction, which in turn defines the coordinate system itself.

6. **Intuition 6: The Same Physical Fact Can Have Multiple Coordinate Representations**  
   Coordinate transformations can serve as an effective construction method. For example:  
   ```
   Excel <==> DomainObject <==> UI
   ```
   Without programming, meta-models allow automatic derivation of visualization interfaces, enabling form-to-function mapping through meta-model representations. This transformation mirrors category theory's functor mappings, where focus shifts from specific objects to the structure of formal systems.

7. **Intuition 7: The Total Can Be Seen as a Difference's Special Case**  
   A = 0 + A, implying that total and difference are isomorphic under the same schema. In this case, differences of differences remain ordinary differences.

8. **Intuition 8: Differences Can Exist Independently of Base**  
   Many consider differences to be base + patch, emphasizing base's primacy. However, the base can also be seen as a patch, with base and patch forming an inverse relationship. Differences possess inherent value regardless of base considerations.

These intuitions can be summarized by Wittgenstein's words: "Language's boundary is our world's boundary." Reversed computation theory further clarifies this: "A language is a coordinate system; coordinate systems are natural extensions of languages."

Reversible computation's development of domain-specific coordinate systems, such as the Nop platform, implements these ideas:

1. Use DSL for all business logic. DSL employs XML syntax, resulting in a Tree structure for file systems and their Trees.
2. All DSLs adhere to XDSL standards, utilizing operations like `extends`, `override`, and `gen-extends` to establish difference algorithms essential for reversible computation.

3. Type systems, exemplified by Compositional Programming, define two-level coordinate systems, combining classes (modules) and methods.

4. Fields define an omnipresent coordinate system, allowing physical quantities to be measured across every point.

5. Domain-specific languages adapt to their domains, offering tailored coordinate systems for specific requirements.

6. Differences between forms can be mapped through meta-models, using functors like `Functor` to maintain structure across transformations.

7. Total and difference are isomorphic under the same schema, ensuring consistency in computations.

8. Differences exist independently of base, emphasizing their inherent value beyond mere summation.

3. There exists a special kind of XDSL called XDef. It serves as a meta-model to define the syntax structure for all other DSLs. Simultaneously, XDef also imposes constraints on itself. The most fundamental difference between XDef and a type system lies in its requirement that list structures must contain some form of unique identification attributes, such as `name`, `id`, etc., ensuring the existence of domain coordinates.

4. All systems implemented using XDSL inherently possess a characteristic: support for Delta customization. This allows for deep customization of both the logic and data structures within a system without altering the original source code. The reason traditional programming languages fail to achieve this is due to their lack of a concept for domain coordinate systems, preventing the implementation of such high-level, granular reuse.

Reversible computation is an operation that can be implemented during compilation, thus not affecting runtime performance. For framework-level solutions, refer to [A Low-Code Platform Design: From Tensor to Low-Code](https://zhuanlan.zhihu.com/p/531474176).

For detailed definitions of domain coordinates and proofs of associativity, refer to [Reversible Computation for Programmers](https://zhuanlan.zhihu.com/p/632876361).

