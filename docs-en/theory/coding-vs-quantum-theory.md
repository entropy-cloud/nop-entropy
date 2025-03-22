```markdown
# Programming and Quantum Mechanics: A Surplus of Analogy

As [Li Hui](https://www.zhihu.com/people/li-hui-53-72) has pointed out in *The Loneliness of Lagrange*, the analogy between programming and quantum mechanics currently lacks a strict scientific foundation. These conceptual connections are more like similarities than true equivalences. However, within these analogies, I have discovered some interesting and highly inspirational ideas. These ideas have led me to propose a new software construction theory—reversible computing theory, which can overcome the limitations of traditional software construction theories such as object-oriented programming and component-based programming, enabling coarse-grained, system-level reuse.

**Note:** The reversible computing theory mentioned here is not aligned with the existing concept of reversible computers in physics, even though both emphasize the importance of reversibility.**

> Reversible computing and reversible computers share the same foundational ideas in physics, even though their specific technical content differs. Just as cloud computing aims to implement the cloudification of computation, **reversible computing aims to implement the reversibility of computation**. See [The Methodological Roots of Reversible Computing](https://zhuanlan.zhihu.com/p/64007521)

[Reversible Computing: The Next Generation Software Construction Theory](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA)

## Section 1. Two-Phase Commit and Quantum Entanglement

Ask DeepSeek:

> Briefly introduce quantum entanglement and two-phase commit, then explain their similarity using the superposition state |commit⟩ + |rollback⟩.

The following is the response from DeepSeek AI:

### **Quantum Entanglement (Quantum Entanglement)**

Quantum entanglement is one of the core phenomena in quantum mechanics. When particles become entangled, their quantum states are bound together in an inseparable way:

- **Superposition**: The system's state is a superposition of the individual particles' states (e.g., |↑↑⟩ + |↓↓⟩)
- **Non-separability**: Measuring one particle instantaneously determines the state of the others, regardless of the distance between them
- **Collapse of Probability**: Before measurement, the system is in a probabilistic superposition state; after measurement, it collapses into a definite state

### **Two-Phase Commit (2-Phase Commit, 2PC)**

2PC is a distributed transaction protocol that ensures atomicity through a coordinator. It works as follows:

1. **Preparation Phase**: The coordinator asks all participants if they are ready to commit
   - Participants lock resources and return "Ready" or "Abort"
2. **Commit Phase**: If all participants are in the "Ready" state, the coordinator broadcasts a commit instruction; otherwise, it broadcasts a rollback

### **Participant State Representation in Quantum Terms**

Assuming each participant's transaction status can be represented as a quantum state:

- **Ground State**:
  `|commit⟩ = |0⟩`
  `|rollback⟩ = |1⟩`
- **Superposition State During Preparation**:
  When participants respond "Ready," their state enters a superposition:
  `|state⟩ = α|0⟩ + β|1⟩`
  (where |α|² represents the commit probability and |β|² represents the rollback probability)

### **Formation of a Global Entangled State**

All participants and the coordinator form a **logical entangled state**:

```
|global state⟩ = (|0⟩₁ ⊗ |0⟩₂ ⊗ ... ⊗ |0⟩ₙ) + (|1⟩₁ ⊗ |1⟩₂ ⊗ ... ⊗ |1⟩ₙ)
```

- **Physical Meaning**:  
  All participants are either committed (|0⟩⊗|0⟩⊗...) or rolled back (|1⟩⊗|1⟩⊗...)
- **Entanglement Features**:
  - A single participant's state cannot be described independently; the global state must be defined collectively

### **Protocol Execution and Quantum Collapse Analogy**

#### **Stage Evolution Illustration**

```
        Preparation Phase                  Coordinator Decision               State Collapse
|?> ──────────────> α|0⟩ + β|1⟩ ───[Commit/Abract]────> |0⟩ or |1⟩
(Initial Uncertainty)   (Superposition State)    (Observation Behavior)     (Definite State)
```

#### **Key Correspondences**

1. **Preparation Phase** → **Quantum Superposition State**
   
   - Participants entering the "Ready" state ≈ Quantum system in `|0⟩ + |1⟩` superposition state
   - The transaction result remains uncertain at this stage

2. **Coordinator Decision** → **Quantum Measurement**
   
   - The coordinator's final instruction (Commit/Abract) is analogous to measuring the system
   - Triggers synchronized collapse of all participants' states

3. **Log Persistency** → **Quantum Coherence Suppression**
   
   - Transaction logs ensure state traceability ≈ Quantum error correction maintains entanglement state

> Here, we emphasize the observed facts from the observer's perspective, regardless of whether they are in the classical or quantum domain.

## Section 2. Paxos Protocol and Quantum Entanglement

Paxos is a distributed consensus algorithm used to reach agreement among unreliable nodes. Its core phases include the preparation phase and the acceptance phase, ensuring consistency through a majority rule. Without a global clock or real-time communication, nodes cannot instantly know the system's overall state, leading to uncertainty periods. If we view the entire distributed system as a macro world, the system's state at any given time (simulated by a logical clock) is in an additive state of `|already reached consensus> + |not yet reached consensus>`.

For a detailed analysis of the Paxos protocol, see

* [A Simple Explanation of the Paxos/Raft Algorithms Even for Elementary Students](https://mp.weixin.qq.com/s/LD8fDbyPohJkA9sbWGjXkQ)

* [An Easy-to-Understand Explanation of the Paxos Algorithm for Ordinary Elementary Students](https://mp.weixin.qq.com/s/fo2iih0u0ECzQO3pgsKOhg)

Ask DeepSeek:

> First, briefly introduce the Paxos protocol. Then explain how in distributed systems, without global knowledge, nodes cannot determine whether consensus has been reached. This leads to uncertainty and forces active selection of a non-conflicting option, thereby reducing the decision space. Explain how this situation resembles quantum entanglement.

Here is DeepSeek's response:

### **Core Mechanisms of the Paxos Protocol**

#### **Three Phases of Consensus**

1. **Proposing Phase (Prepare)**  
   
   - The proposer generates an incrementally numbered proposal `n` and broadcasts a Prepare request to acceptors.
   - Acceptors' promise: They will no longer accept proposals with IDs ≤ `n` and will return the highest ID proposal they have accepted, if any.

2. **Committing Phase (Promise)**  
   
   - Once the proposer receives responses from the majority, it selects the highest existing proposal value `v` (or a new value) and sends an Accept request (`Accept(n, v)`).

3. **Accepting Phase (Accept)**  
   
   - If an acceptor accepts proposal `n` without violating its promise, it persistsently stores `(n, v)` in its ledger.
   - Once the majority of acceptors have accepted, value `v` becomes the system-wide consensus.

### **Cognitive Limitations in Distributed Systems**

#### **The Dilemma Without Global Knowledge**

1. **No Omnipresent View**  
   
   - Nodes only possess local information and cannot instantly perceive the entire network's state.
   - Different nodes may observe delays in determining whether consensus has been reached.

2. **Uncertainty in Additive States**  
   
   - Before consensus is finally determined, the system exists in a potential multi-value additive state.
   - For example, it might simultaneously contain:
     `|consensus value> = α|A> + β|B> + γ|C>`
     where `(α, β, γ)` represent the probabilities or weights of each value being accepted.

3. **Risk of Deadlock**  
   
   - Multiple proposers continuously generate higher-numbered proposals, preventing consensus from converging.
   - This is akin to a quantum system unable to stabilize and collapse into a single state.

### **Active Selection and Space Contraction**

#### **Triggering Mechanism in Paxos**

1. **Majority Observer Principle**  
   
   - Any majority of acceptors forms an equivalent observation system.
   - Once a proposal is accepted by the majority, its value becomes an objective fact, even if some nodes have not perceived it.

2. **Process of Space Contraction**  
   
   ```
   |A> + |B> + |C>  ——[majority Accept(n,v)]——> |v>
   (Additive state)          (Active selection trigger)    (Determined state)
   ```
   
   - By actively selecting a non-conflicting value (e.g., the highest proposal ID), it eliminates other possibilities.
   - This is similar to quantum measurement, which collapses the entangled state into a single basis state.

3. **Contagious Contraction**  
   
   - New nodes join by querying acceptors' historical records to synchronize their state.
   - Similar to quantum entanglement states (though limited by light-speed delays).

### **Comparison with Quantum Entanglement's Analogies**

#### **Similarities in Characteristics**

| Feature            | Paxos Protocol        | Quantum Entanglement System      |
|---------------------|----------------------|---------------------------------|
| **State Uncertainty** | Existence of multiple possible values before consensus is reached | Probabilistic superposition state before measurement |
| **Triggering Mechanism** | Majority acceptance behavior | Observation/measurement behavior |
| **State Propagation** | Synchronous determination through message passing | Non-domain-based instantaneous correlation (limited by light-speed constraints)|
| **Error Tolerance** | Tolerates Byzantine failures (Byzantine fault-tolerant) | Quantum error correction tolerates local superposition |

### **Thought Experiment: Schrödinger's Consensus Value**

Suppose an undecided proposal:

```
|consensus value> = 0.6|X> + 0.4|Y>
```

- **Engineering Perspective**: 60% of nodes lean toward X, 40% toward Y, but no majority has been reached.
- **Paxos Operation**:
  1. Proposer detects the highest proposal ID as X

  2. Collapse the system to |X> using Accept(n,X)  
- **Quantum analogy**:  
  Equivalent to measuring entangled particles and forcing the entire system into a consistent state  

## Three. Software Construction and Wave-Particle Duality

**Most people currently understand software structure construction based on reductionism, constantly decomposing components into atomic units before assembling them back together.** The original concept of virtualized components, which was unconsciously treated as real discrete entities in the mind, is analogous to particles in the material world. These particles construct the world through nested combinations. However, there's another way of construction in the physical world—through waves. Waves are continuously existing patterns constructed by interference and superposition.

An interesting question arises: Can we use a method similar to wave superposition to construct software, surpassing the whole-part structure?

Traditionally, after constructing X well, any modification requires altering X itself. However, if using an additive approach, **we can add an extra Delta without changing X directly to obtain a new Y**.

```
X = A + B + C
Y = A + B + D
  = X + (-C + D)
  = X + Delta
```

Under the traditional software engineering paradigm, even if additive development is possible, it often requires special design of many extension points. It's not easy to perform tailored modifications at arbitrary locations using the Delta method. Especially in traditional software engineering, additive operations usually imply adding new functionalities rather than reducing existing features. The Delta customization approach allows us **to achieve reduction through addition**.

> Note that wave-like construction requires Delta to be a mixture of positive and negative elements, thereby expanding the software's structural space to include inverses.

The XLang language is the world's first programming language that explicitly defines domain structure coordinates and embeds general delta computation rules. Its design follows the guidelines of reversible computing theory.

**Systems developed with XLang automatically support Delta customization without any additional effort, significantly reducing productization development costs.** For example, a bank core system can be packaged into a jar file without modifying the underlying product code. All user-specific customizations and secondary developments can be stored as separate Delta differences, allowing switching between different customized versions through Deltas. The same mechanism applies to multi-tenant customization.

Delta customization enables precise customization down to individual attributes. Traditional software engineering typically provides only a limited number of predefined extension points, making it difficult to support fine-grained customization across all aspects. For example, changing a single property of a button often requires adding a new component or page. **All business-related concepts can be individually customized**, which is an expression of continuity.

The black-box model of components resembles the mechanical worldview from high school physics: rigid bodies' motion is fully described by their center-of-mass coordinates and a few shape-related parameters, while internal structure and interactions are irrelevant. Even in classical mechanics, more advanced perspectives eventually converge into Lagrangian or Hamiltonian formulations, reflecting a shift toward the field-based view of reality.

**In the realm of fields (Fields)**, an infinite coordinate system is established, allowing precise measurement of local changes at every point within this system. The core image in the field-based worldview is that objects are always embedded within this infinite coordinate system, no longer viewed as isolated entities interacting with one another.

For detailed information about XLang, please refer to:

* [Why is XLang considered an innovative programming language?](https://mp.weixin.qq.com/s/O4VeA7Dw8cRF7HTHxi6pNw)
* [Clarifications on "Why XLang is Considered an Innovative Programming Language"](https://mp.weixin.qq.com/s/XtqjqoC8bhDSuCwGhrMbnw)
* [DeepSeek's Simple Explanation: Why XLang is an Innovative Programming Language](https://mp.weixin.qq.com/s/GsGrmaXMqKmmrYW7EuAuig)
* [Further Explanations of "Why XLang is Considered an Innovative Programming Language"](https://mp.weixin.qq.com/s/TtTfcVqklEeZm-UdLBaaZA)
* [Third Round Q&A on the XLang Language](https://mp.weixin.qq.com/s/uw88ceRGPcEDlb4W8cNC1g)

## Four. Reversible Computing and Interactive Landscape

Computer science operates on two basic worldviews: Turing machines and Lambda calculus, which outline two distinct technical paths toward achieving universal computation. However, quantum mechanics presents three distinct landscapes: the Schrödinger picture, the Heisenberg picture, and the Dirac picture. Why does computer science have two fundamental worldviews, yet quantum mechanics offers three? What is their relationship?

In actuality, there are also three corresponding technical paths in computer science that aim for Turing completeness:

* **Turing machine**            \<=\> Schrödinger picture


* Lambda calculus  \<=\>  Heisenberg picture
* Reversible computation  \<=\>  Dirac picture

The following analysis provides specifics. In quantum mechanics, the most fundamental world pictures are two in number: the Schrödinger picture and the Heisenberg picture, with the Dirac picture emerging naturally as a result of their interaction.

1. **Schrödinger Picture**: Operators are fixed; the state function evolves over time.
2. **Heisenberg Picture**: The state function is fixed, while operators evolve over time.
3. **Dirac (Interaction) Picture**: Neither the state function nor the operators are fixed; both evolve with time.

In the Dirac picture, we decompose the Hamiltonian into a known part and a small perturbation:

$$
H = H_0 + H_1
$$

We then investigate how the system evolves when deviating from the known model. This is precisely what interests us in the context of interaction pictures, where both the state function and operators evolve with time.

$$
i\hbar \frac{d}{dt} |\psi_I(t)\rangle = H_1|\psi_I(t)\rangle \\
i\hbar \frac{d}{dt} |A_I(t)\rangle = [A_I(t), H_0]
$$

Interestingly, the Dirac picture is the one used most frequently in actual physical computations. In mathematical physics, there exists a specialized branch of study known as **Perturbation Theory** (Perturbation Theory). This theory systematically investigates how a system evolves when small perturbations are added to a known model. Moreover, the majority of valuable computations performed in theoretical physics operate under the framework of Perturbation Theory.

When comparing quantum mechanics with computer science, an intriguing correspondence exists between the world pictures in quantum mechanics and the computational models in computer science:

1. **Turing Machines**: These are machines with fixed structures, capable of processing a countable infinity of states through a finite number of operations. However, they can read and write data from infinitely long tapes. For instance, modern computers have their hardware capabilities fixed at the factory level, but by installing different software and inputting varying data files, they can produce arbitrary complex outputs. The computation process of a Turing machine can be formally expressed as:

$$
Target\ Output = Fixed\ Machine\ (Unbounded\ Input\ Complexity) 
$$

2. **Lambda Calculus**: The core concept here is the function, which can be thought of as a small computing machine. The composition of functions still results in a function, meaning that complex machines can be constructed by recursively combining simpler ones. Lambda calculus possesses computational power equivalent to that of Turing machines. This equivalence implies that even if we allow the creation of increasingly complex machines, feeding a fixed input (such as the number 0) can still yield arbitrarily complex outputs.

$$
Target\ Output = Unbounded\ Complexity\ Machine\ (Fixed\ Input)
$$

In computer science, two fundamental theories can be formally expressed in the abstract form \( Y = F(X) \). Drawing inspiration from the derivation of the Dirac picture, we recognize that human cognition in the real world is inherently limited. Therefore, we must distinguish between known and unknown components:

$$
\begin{aligned}
Y &= F(X) \\
&= (F_0 + F_1)(X_0 + X_1)\\
&= F_0(X_0) + \Delta
\end{aligned}
$$

By reorganizing the symbols, we obtain a more versatile computational pattern:

$$
Y = F(X) \oplus \Delta
$$

Here, \( \oplus \) represents a structural operation combining two elements, not to be confused with ordinary addition. The delta (\( \Delta \)) introduces an intriguing new concept: it necessarily contains an inverse element (negative), so the combination of \( F(X) \) and \( \Delta \) does not necessarily result in increased output but could also lead to decreased output.

In physics, the necessity of the delta and its inherent inverse are self-evident. Physical models must account for two fundamental facts:

1. The world is inherently "uncertain," with noise always present.
2. The complexity of a model should match the intrinsic complexity of the problem it aims to solve, capturing stable trends and patterns within the problem's core.

For example, given data as represented in the image below:

![](https://pic4.zhimg.com/80/v2-91f19a10faa36653267ffbd4eab86b7f_1440w.webp)

Our model can only resemble the simple curve depicted in figure (a). Figure (b) attempts to fit every data point perfectly, a practice known as overfitting. This approach struggles to describe new data effectively. Conversely, restricting the delta to positive values severely limits the model's descriptive accuracy, as seen in figure (c).

**Reversible Computation Theory proposes a novel software construction formula that implements the above computational pattern as a concrete technical route for achieving Turing completeness.**

```
   App = Delta x-extends Generator<DSL>
```

* **App**: The target application program to be constructed.

* DSL: Domain-Specific Language (DSL), a custom business logic description language tailored for specific business domains. It is also known as the textual representation of domain models.
* Generator: A tool that generates large amounts of derived code based on information provided by the domain model, using repeated application of generation rules. Implementation methods include independent code generation tools and metaprogramming-based compile-time template expansion.
* Delta: The differences between the logic generated from a known model and the target application's logic are identified and collected to form an independent delta description.
* x-extends: A technique that combines delta descriptions with model generation using aspects, allowing for operations such as adding, modifying, replacing, or deleting parts of the model generation process.

DSL provides a high-density expression of essential domain information, directly guiding the Generator in generating code. This is akin to Turing machines executing built-in instructions based on input data. If we view the Generator as a text symbol replacement tool, its execution and composition rules are essentially a replica of lambda calculus. The delta merging operation is unique in that it requires meticulous change collection capabilities across all levels of the system, allowing for the separation and merging of small quantities distributed throughout the system. This makes deltas meaningful and valuable on their own. Additionally, the system must establish inverse meta-models and inverse operations to express deltas as a mix of "existence" and "non-existence."

**The fundamental reason why Turing machines can implement Turing complete systems lies in their ability to be viewed as virtual machines**, capable of simulating all other automata. By continuously increasing the level of abstraction of virtual machines, we can obtain a virtual machine that can directly "run" so-called domain-specific languages (DSLs). However, since DSL focuses on specific domain concepts, it cannot conveniently express all general computation logic (otherwise, it would become a general language), leading to information overflow and resulting in what is known as the delta component.

Turing machines and lambda calculus established the theoretical foundation for general-purpose computers, resolving the question of why there exists a universal machine capable of performing mechanical operations using finite resources. In today's era of widely available general-purpose computers, the most pressing practical challenge is how to perform computations **efficiently**. The improvement of computational efficiency depends on our ability to find "shortcuts" within computations, which in turn relies on our understanding of the problem's essence. This understanding is closely tied to the problem's representation form. Representation transformation itself is a way to solve problems because it can make solutions clearer and more visible. Reversible computation, leveraging domain models and delta descriptions, provides a new, systematized form of representation transformation, allowing us to focus on solving new problems.

> For the role of representation transformation, see [Decoupled Methods Far Exceed Dependency Injection](https://zhuanlan.zhihu.com/p/550923860)

Based on reversible computation's basic formula, we can naturally derive the following generalized software construction method:

$$
\begin{aligned}
App &= Biz \oplus G1(DSL1) \oplus G2(DSL2) + ...\\
   & \equiv (Biz, DSL1, DSL2, ...)
\end{aligned}
$$

If we treat Generator (which can also be viewed as Translator or Transformer) as background knowledge and hide its existence at the symbol level (similar to introducing parentheses in physics), we can view App as a set of DSLs that form a characteristic vector. In other words, during the development of first-generation, second-generation, and third-generation programming languages, abstraction levels are continuously enhanced while still functioning as general-purpose program languages. **However, when we reach fourth-generation programming languages, what we are likely to obtain is not another general-purpose program language but rather a forest of DSLs constructed from numerous domain-specific languages**, enabling new representations and cognitive models for original program structures.

For further details, see:

* [Reversible Computation Theory for Programmers](https://mp.weixin.qq.com/s/aT99VX6ecmZXdemBPnBcoQ)  
* [Supplementary Explanation of Reversible Computation Theory for Programmers](https://mp.weixin.qq.com/s/zGfo7pvKjOCa11PYLJHzzA)  
* [Explanation of Delta Concepts with Git and Docker as Examples](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ)  
* [What Does "Reversible" Mean in Reversible Computation Theory?](https://mp.weixin.qq.com/s/Fngl7vYWhULn0VKeAEKPkQ)

## Five. DSL Forest and Differential Manifolds

Modern physics describes spacetime using multiple Euclidean coordinate systems rather than a single global one, requiring the use of so-called differential manifolds to describe it.

In the field of programming, traditionally, general-purpose programming languages such as Java and C are used to express business logic. If we view a language as a description of a coordinate system, programming essentially maps business operations into this coordinate space. The problem lies in that the coordinate system defined by general-purpose languages is unstable at the business level. For example, when an additional field is added at the data model layer, expressing this change using a general-purpose language may require manual modifications across multiple locations of the code. However, with a model-driven architecture, modifying the DSL (Domain-Specific Language) for the data model might only require changes in one place, automatically propagating the necessary modifications elsewhere. This change would result in only a localized disturbance within the coordinate system defined by the data model.

> The analogy is drawn between the description of a circle in Euclidean coordinates and its optimal representation in polar coordinates.

The reversible computing theory outlines a systematic approach to expressing business logic using multiple DSLs.

$$
\begin{aligned}
& \text{// Horizontal decomposition, generating multiple $DSL$s} \\
App &= Delta +  G_1\langle DSL_1\rangle + G_2\langle DSL_2\rangle + ... \\
\\
&\text{// Deep decomposition, generating multiple $DSL$s} \\
App &= Delta + G_1\langle Delta_2 + G_2\langle Delta_3 + G_3\langle DSL_3\rangle\rangle\rangle
\end{aligned}
$$

A vast number of DSLs form a DSL forest, which collectively addresses the problem.

It is crucial to emphasize that **each DSL language automatically defines a coordinate system suitable for a specific domain**. This concept may seem subtle, but understanding it involves recalling concepts from active differential geometry. Typically, we define motion within a pre-defined coordinate system, but the active differential geometry approach leverages the intrinsic properties of motion itself to automatically generate an inherent coordinate system attached to the trajectory of motion. In other words, **motion occurs within a coordinate system while the motion itself generates that coordinate system**. Similarly, business logic expressed using DSLs simultaneously defines a coordinate system through its abstract syntax tree (AST) and node attributes. Specifically, each attribute in the AST has a unique XPath path, such as `/task/steps[@name=a]/@name`, which refers to the `name` attribute of step `a`. **Since any logic must be expressed using a programming language, there is no gap in the coordinate system; business operations inherently occur within the coordinate space defined by this system**.

> The essence of active differential geometry is to **replace externally imposed coordinate systems with the intrinsic properties of geometric objects**, aligning with modern physics' "background independence" (e.g., general relativity) and computer science's "domain-specific language" (DSL) design principles—**defining descriptive frameworks based on the object's own structure rather than relying on external abstractions**.

A low-code platform designed based on reversible computing theory, NopPlatform, has been open-sourced:

- Gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Gitcode: [https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- Development Example: [https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- Reversible computing principles, NopPlatform introduction, and Q&A: [https://www.bilibili.com/video/BV14u411T715/](https://www.bilibili.com/video/BV14u411T715/)
- International Website: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- Community member Crazydan Studio's Nop development practice sharing website: [https://nop.crazydan.io/](https://nop.crazydan.io/)