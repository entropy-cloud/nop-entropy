# The Ostensible Parallels between Programming and Quantum Mechanics

As [@拉格朗日的忧郁](https://www.zhihu.com/people/li-hui-53-72) points out, analogies between programming and quantum mechanics currently lack a rigorous scientific foundation; the conceptual connections are, at best, superficial. Nonetheless, within these superficial parallels, I’ve found some interesting and highly inspiring ideas. They led me to propose a new theory of software construction—Reversible Computation—which can transcend the limitations of traditional software construction theories like object orientation and component theory, enabling coarse-grained, system-level reuse.

Note that the Reversible Computation theory discussed here is not identical to the reversible computer concept already existing in physics, even though both emphasize the importance of reversibility.

> Reversible Computation and reversible computers share the same conceptual roots in physics. While their technical connotations differ, they converge on a common goal. Just as cloud computing seeks to “cloudify” computation, Reversible Computation and reversible computers both aim to make computation reversible. See [Methodological Origins of Reversible Computation](https://zhuanlan.zhihu.com/p/64007521)

[Reversible Computation: The Next-Generation Software Construction Theory](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA)

## I. Two-Phase Commit and Quantum Entanglement

Question to DeepSeek:

> First briefly introduce quantum entanglement and two-phase commit, then use superpositions like |commit> + |commit> and |rollback> + |rollback> to explain their similarity.

Below is DeepSeek AI’s answer:

### Quantum Entanglement

Quantum entanglement is a central phenomenon in quantum mechanics. When two or more particles form an entangled state, their quantum states become inseparably correlated:

- Superposition: The state of an entangled system is a superposition of the constituent particles’ states (e.g., |↑↑> + |↓↓>)
- Nonlocality: Measuring one particle instantaneously determines the state of the others (even if light-years apart)
- Collapse: Before measurement the system is in a probabilistic superposition; after measurement, it collapses to a definite state

### Two-Phase Commit (2PC)

2PC is a distributed transaction protocol ensuring atomicity and consistency across participants via a coordinator:

1. Prepare phase: The coordinator asks all participants if they can commit
   - Participants lock resources and reply “Ready” or “Abort”
2. Commit phase: If all are ready, the coordinator broadcasts Commit; otherwise, it broadcasts Rollback

### Quantum-Like Representation of Participant States

Assume each participant’s transaction state can be described by a quantum state:

- Basis states:
  `|Commit> = |0>`
  `|Rollback> = |1>`
- Superposition during prepare:
  When a participant responds “Ready,” its state enters a superposition:
  `|State> = α|0> + β|1>`
  (where |α|² is the commit probability and |β|² is the rollback probability)

### Formation of a Global Entangled State

All participants and the coordinator form a logically entangled state:

```
|Global State> = (|0>₁⊗|0>₂⊗...⊗|0>ₙ) + (|1>₁⊗|1>₂⊗...⊗|1>ₙ)
```

- Physical meaning:
  Either all participants commit (|0>⊗|0>⊗...) or all roll back (|1>⊗|1>⊗...)
- Entanglement feature:
  A single participant’s state cannot be described independently; it must be defined via the global state

### Analogy between Protocol Execution and Quantum Collapse

#### Stage evolution schematic

```
        Prepare Phase                 Coordinator Decision               Collapse
|?> ────────────> α|0> + β|1> ───[Commit/Abort]───> |0> or |1>
(Initial uncertainty)     (Superposition)              (Measurement)      (Definite state)
```

#### Key correspondences

1. Prepare phase → Quantum superposition
   
   - Participant enters “Ready” ≈ the quantum system is in a |0> + |1> superposition
   - The transaction outcome remains uncertain

2. Coordinator’s decision → Quantum measurement
   
   - The coordinator’s final directive (Commit/Abort) is analogous to measurement
   - It triggers simultaneous collapse of all participants’ states

3. Log persistence → Decoherence suppression
   
   - Transaction logs ensure state traceability ≈ quantum error correction sustaining entanglement

> The emphasis here is on observer-level facts; it is independent of whether the underlying world is classical or quantum.

## II. The Paxos Protocol and Quantum Entanglement

Paxos is a distributed consensus algorithm used to reach agreement among unreliable nodes. Its core stages include the prepare and accept phases, and it ensures consistency via a majority (quorum) principle. Without a global clock or instantaneous communication, nodes cannot know the global system state in real-time, hence a period of uncertainty exists. If we view the entire distributed system as a macroscopic world, then at any given “moment” (global time simulated by logical clocks) the system’s state is in a superposition of |consensus reached> + |consensus not reached>.

For detailed analyses of Paxos, see:

* [The Paxos/Raft Secrets Even Elementary School Students Can Grasp](https://mp.weixin.qq.com/s/LD8fDbyPohJkA9sbWGjXkQ)

* [Explaining Paxos in a Way Even Ordinary Elementary School Students Can Understand](https://mp.weixin.qq.com/s/fo2iih0u0ECzQO3pgsKOhg)

Question to DeepSeek:

> First briefly introduce the Paxos protocol, then explain how the lack of global knowledge in a distributed system prevents determination of whether consensus has been reached, and finally show how proactively choosing a non-conflicting option causes the choice space to collapse. Explain the similarity to quantum entanglement.

Below is DeepSeek’s answer:

### Core Mechanics of Paxos

#### Three stages to consensus

1. Proposal phase (Prepare)  
   
   - The Proposer generates a monotonically increasing proposal number n and broadcasts a Prepare request to Acceptors
   - Acceptors promise not to accept proposals numbered ≤ n and return the highest-numbered proposal they have accepted (if any)

2. Promise phase (Promise)  
   
   - After receiving a majority of responses, the Proposer chooses the value v of the highest-numbered existing proposal (or proposes a new value)
   - It sends Accept(n, v) to Acceptors

3. Accept phase (Accept)  
   
   - Acceptors accept proposal (n, v) if doing so does not violate their promises, and persist (n, v)
   - Once a majority accepts, v becomes the system’s consensus

### Cognitive Limitations in Distributed Systems

#### The predicament of missing global knowledge

1. No “God’s-eye” view:  
   
   - Nodes only have local information and cannot perceive the global state in real-time
   - Different nodes experience observation delays regarding whether consensus has been reached

2. Uncertain superposition:  
   
   - Before the final consensus is established, the system is in a potential multi-value superposition
   - For example, there may be:
     `|Consensus Value> = α|A> + β|B> + γ|C>`
     (α, β, γ are the probability weights for each value being accepted)

3. Risk of livelock:  
   
   - Multiple Proposers keep generating higher-numbered proposals, preventing consensus from converging
   - Analogous to a quantum system oscillating in superposition and failing to collapse

### Proactive Choice and Collapse of the Choice Space

#### Collapse trigger in Paxos

1. Majority-observer principle:  
   
   - Any majority of Acceptors forms an effective observation system
   - Once a majority accepts a proposal, its value becomes an objective fact (even if some nodes have not yet observed it)

2. Choice space collapse:
   
   ```
   |A>+|B>+|C>  ——[Majority Accept(n,v)]——> |v>
   (Superposition)      (Proactive choice)     (Definite state)
   ```
   
   - By forcibly choosing a non-conflicting value (e.g., adopting the value from the highest-numbered proposal), other possibilities are eliminated
   - Analogous to measurement breaking superposition and collapsing the system to a single basis state

3. Transitivity of collapse:  
   
   - Newly joined nodes synchronize by querying Acceptors’ histories
   - Similar to synchronization of information in entangled states (though limited by propagation delays)

### Metaphorical Comparison to Quantum Entanglement

#### Dimensions of similarity

| Feature           | Paxos Protocol                  | Quantum Entangled System           |
| ----------------- | -------------------------------- | ---------------------------------- |
| State uncertainty | Multiple potential values pre-consensus | Probabilistic superposition pre-measurement |
| Collapse trigger  | Majority acceptance of a proposal | Observation/measurement            |
| State propagation | Message-passing to sync the definite state | Nonlocal correlations with instantaneous sync |
| Fault tolerance   | Tolerates minority failures (Byzantine tolerance) | Quantum error correction tolerates local decoherence |

### Thought Experiment: Schrödinger’s Consensus Value

Assume an undecided proposal:

```
|Consensus> = 0.6|X> + 0.4|Y>
```

- Engineering perspective: 60% of nodes favor X, 40% favor Y, but no majority yet
- Paxos operation:
  1. The Proposer observes the highest-numbered proposal is X
  2. By issuing Accept(n, X), the system collapses to |X>
- Quantum metaphor:
  Equivalent to performing a correlated measurement on entangled particles, forcing the entire system into a consistent state

## III. Software Construction and Wave–Particle Duality

Most people’s foundational understanding of software structure is reductionist: continually decomposing downward to find atomic elements and then assembling using those atoms. The notionally virtual concept of components is subconsciously treated as real, discrete entities—analogous to particles in the physical world—composed via nested combination to build the world. But there is another constructive mode in physics: waves. Waves are continuous patterns that build the world through interference and superposition.

An intriguing question: can we construct software via wave-like superposition, thereby transcending the whole–part composition paradigm?

Traditionally, after constructing X, any change requires modifying X itself. Under a superposition-based approach, however, we can obtain a new Y by supplementing X with an additional Delta without directly changing X.

```
X = A + B + C
Y = A + B + D
  = X + (-C + D)
  = X + Delta
```

Within traditional software engineering, even when incremental development is possible, it often necessitates designing numerous extension points in advance, and not every change can be customized via a Delta approach. In particular, in traditional software engineering, increments generally imply adding new functionality, rarely enabling the inverse removal of existing features. Delta customization allows us to achieve reduction by addition.

> Note: Superposition-based construction requires the Delta to be a mixture of positive and negative elements; we must enlarge the software’s structural space to include inverses.

XLang is the world’s first programming language to explicitly define domain structural coordinates and to embed a general Delta calculus in the language, designed under the guidance of Reversible Computation.

Systems developed with XLang support Delta customization out of the box with no extra work. This dramatically reduces the cost of productizing products. For example, once a core banking system is packaged into a jar, you need not modify the base product’s code; all customer-specific customizations and secondary development can be stored as separate Deltas. Switching Deltas yields multiple customized variants. The same mechanism supports multi-tenant customization.

Delta customization enables precision down to a single attribute. Traditional software engineering offers only a handful of pre-defined extension points and struggles to support fine-grained customization everywhere. For example, to define a single property of a button, one often needs to add a new component or page. Every concept of interest at the business layer can be customized one by one—this is a manifestation of continuity.

The black-box model of components is akin to the high-school-level Newtonian worldview: a rigid body’s motion is fully described by a few parameters—its center-of-mass coordinates, size, shape, and orientation. Its internal structure is unobservable and irrelevant; rigid bodies interact via direct contact, and their shapes must precisely match to form a seamless whole. Even within classical mechanics, more advanced viewpoints adopt the Lagrangian or Hamiltonian formulations, which shift toward a field-theoretic worldview. A field establishes an omnipresent coordinate system, and at each point in that coordinate system a physical quantity can be specified. A field has infinite degrees of freedom, yet via coordinates it is describable, definable, and analyzable; we can precisely measure local variations at every point in the coordinate system. In the field worldview, the core image we face is that objects are always immersed in a field (an omnipresent coordinate system), rather than isolated pairwise interactions between objects.

For more on XLang:

* [Why Is XLang an Innovative Programming Language?](https://mp.weixin.qq.com/s/O4VeA7Dw8cRF7HTHxi6pNw)
* [Q&A on “Why XLang Is an Innovative Programming Language”](https://mp.weixin.qq.com/s/XtqjqoC8bhDSuCwGhrMbnw)
* [DeepSeek’s Layman’s Explanation: Why Is XLang an Innovative Programming Language?](https://mp.weixin.qq.com/s/GsGrmaXMqKmmrYW7EuAuig)
* [Further Explanation of “Why XLang Is an Innovative Programming Language”](https://mp.weixin.qq.com/s/TtTfcVqklEeZm-UdLBaaZA)
* [Third Round of Q&A on the XLang Language](https://mp.weixin.qq.com/s/uw88ceRGPcEDlb4W8cNC1g)

## IV. Reversible Computation and the Interaction Picture

Computer science offers two fundamental worldviews: the Turing machine and the lambda calculus—two technical routes to Turing completeness. Quantum mechanics, however, presents three: the Schrödinger picture, the Heisenberg picture, and the Dirac (interaction) picture. Why does computer science have two basic worldviews while quantum mechanics has three pictures? Is there a correspondence?

In fact, the fundamental technical routes to Turing completeness in computer science can also be viewed as three, corresponding to the pictures of quantum mechanics as follows:

- Turing machine            <=> Schrödinger picture
- Lambda calculus           <=> Heisenberg picture
- Reversible Computation    <=> Dirac (interaction) picture

Here is the analysis. First, quantum mechanics also has two foundational pictures; the Dirac picture naturally derives from the first two:

1. In the Schrödinger picture, operators are fixed and state vectors evolve
2. In the Heisenberg picture, state vectors are fixed and operators evolve
3. In the Dirac (interaction) picture, both state vectors and operators evolve in time

In the Dirac picture, we decompose the system’s Hamiltonian into a known part and a small perturbation to be studied:

$$
H = H_0 + H_1
$$

We then study how the system evolves away from the known model—i.e., we focus on the evolution of the Delta description. In the interaction picture, both state vectors and operators evolve with time.

$$
i\hbar \frac d {dt} |\psi_I(t)\rangle = H_1|\psi_I(t)\rangle \\
  i\hbar \frac d {dt} |A_I(t)\rangle = [A_I(t), H_0]
$$

All three pictures yield identical physical measurement results:

$$
\langle \psi_S(t)|A_S|\psi_S(t)\rangle = \langle \psi_H|A_H(t)|\psi_H\rangle
 = \langle \psi_I(t)|A_I(t)|\psi_I(t)\rangle
$$

Interestingly, the interaction picture is the one physicists most often use in practice. Indeed, mathematical physics has a dedicated branch—Perturbation Theory—which systematically studies how adding small perturbations to a known model drives the evolution of a new model. Most valuable calculations in theoretical physics take place within the framework of perturbation theory.

Comparing quantum mechanics and computer theory, we discover a compelling correspondence between the pictures of quantum mechanics and the worldviews of computer theory.

1. A Turing machine is a structurally fixed machine with a finite, enumerable set of states and a finite instruction set. It reads and writes data on an infinitely long tape. For example, your everyday computer ships with fixed hardware capabilities, but by installing different software and feeding different input files, it can automatically produce arbitrarily complex outputs. Formally, a Turing machine’s computation can be written as:

$$
\text{Target Output} = \text{Fixed Machine}(\text{Infinitely Complex Input})
$$

2. In contrast, the core concept of the lambda calculus is the function, with a function representing a small computational machine. Composition of functions yields another function—that is, machines composed recursively yield more complex machines. The lambda calculus is computationally equivalent to the Turing machine, implying that if we can keep creating ever more complex machines, then even with a constant input of 0 we can obtain arbitrarily complex target outputs.

$$
\text{Target Output} = \text{Infinitely Complex Machine}(\text{Fixed Input})
$$

Both foundational theories in computer science can be abstracted as Y = F(X). Following the derivation of the Dirac picture, recognizing that human cognition is limited and that all quantities must be separated into known and unknown parts, we perform the following decomposition:

$$
\begin{aligned}
 Y &= F(X) \\
   &= (F_0 + F_1) (X_0+X_1)\\
   &= F_0(X_0) + \Delta
\end{aligned}
$$

Re-arranging notation yields a more broadly applicable computational model:

$$
Y = F(X) \oplus \Delta
$$

Beyond the functional operation F(X), a new structural operator ⊕ appears, denoting a composition operation between two elements (not ordinary numerical addition) and introducing a new concept: Delta. The peculiarity of Delta is that it must contain some negative elements; combining F(X) with Δ does not necessarily “increase” the output—it may well “decrease” it.

In physics, the necessity of a Delta and the fact that Δ contains inverse elements are self-evident because physical modeling must address two basic facts:

1. The world is uncertain; noise always exists
2. Model complexity must match the intrinsic complexity of the problem, capturing stable, invariant trends and laws at its core

For example, given the following data:
![](https://pic4.zhimg.com/80/v2-91f19a10faa36653267ffbd4eab86b7f_1440w.webp)

Our model should be a simple curve like in (a). The model in (b), which attempts to fit every data point exactly, is mathematically overfitting and generalizes poorly to new data, while in (c) constraining the Delta to be only positive severely limits the model’s descriptive precision.

Reversible Computation proposes a new formula for software construction, materializing the above computational mode into a concrete technical route to Turing completeness.

```
   App = Delta x-extends Generator<DSL>
```

* App: The target application to be constructed
* DSL: Domain-Specific Language—business logic description tailored to a specific domain; a textual representation of the domain model
* Generator: Given the domain model, repeatedly applying generation rules derives a large amount of code. Implementations include standalone code generators and compile-time template expansion via metaprogramming
* Delta: The differences between logic generated from the known model and the target application’s logic are identified, collected, and expressed as an independent Delta
* x-extends: The Delta and the model-generated parts are combined using techniques akin to Aspect-Oriented Programming, involving a series of operations such as addition, modification, replacement, and deletion of the generated model parts

A DSL is a high-density expression of critical domain information and directly guides the Generator to produce code, analogous to a Turing computation driven by input data executing built-in instructions. If we regard the Generator as textual symbol substitution and expansion, then its execution and composition rules mirror the lambda calculus. Delta merging is novel in a sense because it demands a meticulous, all-pervasive ability to capture variations—to separate like-order small terms scattered across the system and combine them—so that the Delta possesses independent meaning and value. Simultaneously, the system must explicitly establish inverse elements and inverse operations; only then can the Delta, as a mixture of “existence” and “non-existence,” be expressed.

The fundamental reason a Turing machine achieves Turing completeness is that it can be viewed as a virtual machine capable of simulating all other automatic computing machines. If we keep raising the level of abstraction of the virtual machine, we eventually obtain a VM that can directly “run” a Domain-Specific Language (DSL). However, because a DSL focuses on domain-specific concepts, it cannot conveniently express all general-purpose logic (otherwise it would be a general-purpose language). Some information inevitably spills over and becomes the Delta term.

The Turing machine and the lambda calculus laid the conceptual foundation of universal computation, solving—in principle—the feasibility of computation: why a universal machine can mechanically perform actions and, with finite resources, accomplish all computations we can conceive. In an era where universal computers are ubiquitous, our foremost practical challenge is computational efficiency. Efficiency gains rely on discovering “shortcuts” in computation, which depend on insights into the essence of problems—and such insights are intimately tied to the form of representation. Representation transformations can themselves be a means of problem solving because a new representation may make the solution apparent. Via domain models and Delta descriptions, Reversible Computation provides a new, systematic way to transform representations, allowing us to focus attention on the new problems to be solved.

> For more on the role of representation transformation, see [Decoupling Is Not Just Dependency Injection](https://zhuanlan.zhihu.com/p/550923860)

From the basic formula of Reversible Computation, we naturally obtain the following generalized software construction method:

$$
\begin{aligned}
 App &= Biz \oplus G1(DSL1) \oplus G2(DSL2) + ...\\
   & \equiv (Biz, DSL1, DSL2, ...)
\end{aligned}
$$

If we treat Generators (or Translators/Transformers) as known background knowledge and elide them symbolically (akin to introducing Poisson brackets in physics), we can view App as a feature vector composed of a set of DSLs. That is, through the development of first-, second-, and third-generation programming languages, abstraction levels have continuously increased, yet they remain general-purpose languages. Upon reaching fourth-generation programming languages, however, we may not obtain another general-purpose language but rather a forest of Domain-Specific Languages (a DSL forest), through which we can form a new representation and understanding of existing program structures.

For further reading:

* [An Analysis of Reversible Computation Theory for Programmers](https://mp.weixin.qq.com/s/aT99VX6ecmZXdemBPnBcoQ)  
* [Addendum to “An Analysis of Reversible Computation Theory for Programmers”](https://mp.weixin.qq.com/s/zGfo7pvKjOCa11PYLJHzzA)  
* [Analyzing the Delta Concept in Reversible Computation via Git and Docker](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ)
* [What Exactly Is “Reversible” in Reversible Computation Theory?](https://mp.weixin.qq.com/s/Fngl7vYWhULn0VKeAEKPkQ)

## V. The DSL Forest and Differentiable Manifolds

The spacetime studied in modern physics cannot be described by a single global Euclidean coordinate system; it must be described as a differentiable manifold stitched together from multiple Euclidean patches.

In programming, business logic is traditionally expressed using general-purpose languages like Java or C. If we regard a language as a descriptive coordinate system, programming maps the business onto that coordinate space. The problem is that the coordinates of general-purpose languages are unstable at the business level. For example, adding a field at the data-model layer may require manual code changes in many places when using a general-purpose language. In a model-driven architecture, however, you might only need to change a single definition in the data-model DSL (Domain-Specific Language), from which all other changes are automatically derived. We can say that adding a field causes only a local perturbation in the coordinate system defined by the data model.

> Compare the description of a circle in a Euclidean coordinate system versus in the most suitable polar coordinate system.

Reversible Computation points to a systematic route for using multiple DSLs to express business logic.

$$
\begin{aligned}
& \text{// Lateral decomposition yielding multiple $DSL$} \\
App &= Delta +  G_1\langle DSL_1\rangle + G_2\langle DSL_2\rangle + ... \\
\\
&\text{// Deep decomposition yielding multiple $DSL$} \\
App &= Delta + G_1\langle Delta_2 + G_2\langle Delta_3 + G_3\langle DSL_3\rangle\rangle\rangle
\end{aligned}
$$

A large number of DSLs form a DSL forest, which then collaborates to solve problems.

It’s important to emphasize that each DSL automatically defines a coordinate system suited to its local domain. This may sound subtle, but if you’ve studied the method of moving frames in differential geometry, it becomes straightforward. Typically, we define motion within a chosen coordinate system; the method of moving frames leverages intrinsic properties of the motion to define a coordinate system attached to the trajectory—an intrinsic coordinate system. In other words, motion occurs within a coordinate system, and motion itself generates a coordinate system. Similarly, business logic is expressed using DSLs, and in expressing the business, the DSL’s abstract syntax tree and its node attributes automatically constitute a coordinate system. Concretely, each property on the abstract syntax tree has a unique XPath—for example, `/task/steps[@name=a]/@name` refers to the name attribute of step a. Because any logic must be expressed in a programming language, there is no case where this coordinate system fails to cover the domain: all business occurs within the coordinate space defined by this coordinate system.

> The essence of the method of moving frames is to replace externally imposed coordinates with the object’s intrinsic geometric properties. This aligns with “background independence” in modern physics (e.g., general relativity) and with the design philosophy of Domain-Specific Languages (DSLs) in computer science—defining the descriptive framework via the object’s own structure rather than relying on external abstractions.

The low-code platform NopPlatform, designed based on Reversible Computation, is open source:

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- gitcode: [https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- Development example: [https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- Principles of Reversible Computation and Nop platform intro and Q&A: [https://www.bilibili.com/video/BV14u411T715/](https://www.bilibili.com/video/BV14u411T715/)
- Official site (International): [https://nop-platform.github.io/](https://nop-platform.github.io/)
- Nop development practice site built by community member Crazydan Studio: [https://nop.crazydan.io/](https://nop.crazydan.io/)
<!-- SOURCE_MD5:265c5871cfec457e54aa0bd3e88dd82f-->
