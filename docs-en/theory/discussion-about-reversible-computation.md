
# A Discussion on Reversible Computation — Response to Rounded-Corner Knight Marisa

In the article [Why do computer science have two worldviews—Turing machines and Lambda calculus—while quantum mechanics has three pictures?](https://www.zhihu.com/question/614938288/answer/3147722439), I put forward a heuristic viewpoint by establishing an interesting correspondence between Turing machines, lambda calculus, Reversible Computation, and the Schrödinger, Heisenberg, and Dirac pictures in quantum mechanics. I pointed out that Reversible Computation is a third, physics-inspired route to Turing completeness. For students who have studied various Turing-complete computational models at the graduate level, this can be hard to accept both intellectually and emotionally; it may be impossible to connect these concepts. Some even jokingly ask: Why does computer science have five Chinese characters while quantum mechanics has four? Is there any correspondence between them?

Essentially, Reversible Computation arises from a different set of physical intuitions, so one must step outside the conventional computer science mindset to understand it. As for the significance of Reversible Computation, first, concrete technical solutions can be derived from it: one can introduce syntax specific to Reversible Computation into programming languages to address—both theoretically and practically—the coarse-grained software reuse problem that component theory cannot solve. Second, numerous industry practices related to Delta can be unified and studied within the framework of Reversible Computation theory—for example, Docker technology is a standard application instance of Reversible Computation theory.

In this article, I will address common questions, mainly to explain the intuitions behind Reversible Computation and which problems it solves that current programming languages cannot.

## 1. There are many foundational Turing-complete languages; graduate courses teach at least three or four models. Why say there are only two worldviews—Turing machines and Lambda calculus?

What my article expressed is not that there are only two Turing-complete languages, nor that there are only two styles of Turing-complete languages. In fact, there are many Turing-complete computational models. This raises an interesting question: Are Turing-complete computational models finitely many or infinitely many? If finitely many, which ones, and why does nature allow only those? If infinitely many, how do we understand an infinity of computational models? Do these many models have more fundamental ones such that others are mixtures of these basics?

Although many people learned several Turing-complete models in class, they were recognized as isolated models. How should these Turing-complete models be viewed as a whole? Beyond the common property of Turing completeness, what conceptual connections do they share? Are you not curious about this?

From within computer science it is difficult to see the full picture of the world. Lamport (Turing Award winner, inventor of Paxos) made revolutionary innovations in distributed systems in large part due to his study of relativity. What connections exist between Turing machines and lambda calculus beyond equivalent computational power? Within pure computer science this question may be hard to answer. But by analogy with quantum mechanics, we can not only establish a conceptual connection between these two computational models, we can also naturally derive a new general computational model inspired by physical intuition.

Why are there only three pictures in quantum mechanics? Couldn't there be more? There can indeed be infinitely many. But the Schrödinger and Heisenberg pictures provide two basic cognitive perspectives. When the wind moves the banner, is it the wind or the banner that moves? Quantum mechanics studies how quantum operators act on quantum states to drive system evolution. Since we care only about observable results, different interpretations can exist for how operators and states participate in evolution. One approach attributes all evolution to the quantum state’s evolution (Schrödinger picture); another attributes it to the operator’s evolution (Heisenberg picture). These two pictures yield identical predictions for physical observables.

$$
\langle \psi_S(t)|A_S|\psi_S(t)\rangle = \langle \psi_H|A_H(t)|\psi_H\rangle
$$

Clearly, these represent two extreme worldviews. The Dirac picture can be seen as a mixture or compromise of the two, but its focus lies on Delta questions: given a known model, when we add a small perturbation to obtain a new model, how can we fully leverage the solution of the known model to simplify solving the perturbed model?

Analogous to operators and states in the quantum world, the program world also has two dual basic concepts: functions and data. Faced with a new problem, the Turing machine fixes a small set of operations but accepts unbounded input data, whereas lambda calculus fixes the constant state and expresses everything via new functions. In this sense, we can say that Turing machines (fixed functions + unboundedly varying data) and lambda calculus (fixed data + unboundedly varying functions) are two fundamental worldviews.

More plainly, in a pair of dual concepts, fixing one side and varying only the other corresponds to two extreme worldviews. Reversible Computation can be seen as exactly the middle ground emphasizing finiteness and layered cognition: when neither side can vary without bound, unknown variation must be captured by an additional Delta, Y = F0(X0) + Delta; hence it is the third worldview. There are certainly infinitely many other options between the three, but they are not the most extreme nor the most representative.

> For the concept of duality, see this Zhihu article: [How to intuitively understand the dual vector space](https://www.zhihu.com/question/38464481/answer/2446175090)

## 2. Building a new framework is to discover new things rather than reinterpret the old. Is Reversible Computation just hindsight?

Docker technology can be summarized mathematically as the following formula:

> `App = DockerBuild<DockerFile> overlay-fs BaseImage`

* DockerFile is essentially a DSL that defines image contents.

* Docker’s Build tool is a Generator that parses and executes the DSL to produce image slices.

* Multiple image slices are composed via a filesystem that supports Delta, OverlayFS.

It is clear that the computational pattern embodied by Docker is exactly a special case of the software construction formula proposed by Reversible Computation theory:

> `App = Delta x-extends Generator<DSL>`

Reversible Computation theory can provide a unified theoretical explanation for a series of Delta-based technologies including Docker—so is it just hindsight?

1. First, I proposed the idea of Reversible Computation as early as 2007, whereas Docker was publicly released in 2013. Clearly, this can only be seen as theory preceding practice by many years, with the theory precisely predicting the structural requirements of practice.

2. Reversible Computation not only explains an isolated technical phenomenon; it maps it to abstract mathematical concepts, allowing rapid generalization to broader application domains. In particular, Reversible Computation posits that every DSL can define a Delta model space, and within each such space one can define domain-semantic Delta merging mechanisms and Delta generation mechanisms. Docker cleverly chooses a Delta filesystem as the underlying Delta model space, and the many command-line programs accumulated within the Linux ecosystem automatically become Delta generation operators, thereby revitalizing existing technical assets. This idea can be readily extended to any other Delta model space. For example, after k8s 1.14, the Kustomize configuration tool applied similar ideas to YAML configuration to produce new configuration customization schemes. We have long employed similar Delta customization schemes. See [Kustomize through the lens of Reversible Computation](https://zhuanlan.zhihu.com/p/64153956)

Some may believe Docker is merely an application-layer technology unrelated to foundational computation models. But I would point out that anything done automatically by software is a form of computation, and behind it lies a computational model. Docker’s success can be distilled at the theoretical level into an abstract computational model and applied to other scenarios. We are already accustomed in practice to the concept of immutable data; considering the duality between data and functions, how do we define and implement the concept of immutable logic?

## 3. Reversible Computation and the so-called pebble game represent only a small part of computer science. Is it wrong to treat it as foundational?

A common misunderstanding of my Reversible Computation theory is to confuse it with the concept of reversible computers in hardware. The notion of reversible computers originates from IBM physicist Rolf Landauer’s 1961 paper, “Irreversibility and Heat Generation in the Computing Process.” His research showed that erasing one bit of information dissipates at least kT*ln 2 of energy as heat into the environment. To minimize energy dissipation, we must use reversible logic gates. The study of reversible computers has always been a niche field; it leans toward hardware implementation and is comparable to research on quantum computers.

The Reversible Computation theory I propose shares with reversible computers the goal of making computation reversible, but Reversible Computation emphasizes high-level abstract structures rather than low-level physical implementation. To avoid confusion with reversible computers, I use the English term Reversible Computation, whereas reversible computers generally correspond to Reversible Computing.

Recently, inspired by the concept of reversible computers and the backpropagation algorithm in deep neural networks, Leo proposed an abstract programming language supporting reversible execution: NiLang. See [NiLang — Reversible Computation, Differentiating Everything](https://zhuanlan.zhihu.com/p/191845544). This reversible language still targets the traditional structure space of computer hardware storage. In contrast, my Reversible Computation emphasizes flexible definition of Delta structure spaces and an explicit notion of structured Delta; reversible Delta operations may exist only at a particular DSL layer and need not be reversible at the level of the entire program.

Irreversibility at the physical level implies entropy increase. Reversible Computation theory points out that, with Delta-structured designs, even if we cannot control the system’s overall entropy increase, we can still control where entropy increases, confining it to the Delta, thereby preventing incidental requirements from continually eroding the foundational architecture of a product.

Reversible Computation theory can be seen as a further development of object-orientation and component theory in software engineering. It overcomes the inherent difficulties of component theory at the theoretical level and solves the problem of coarse-grained, system-level software reuse.

1. Inheritance in object orientation can be seen as a partial order A > B: the derived class A has more than the base class B, but what is added is not explicitly expressed.

2. The component theory dictum “composition over inheritance” amounts to A = B + C, with the same C reused multiple times, e.g., E = D + C.

3. From inequality to addition, the natural next step is to introduce subtraction:
   
   ```
   X = A + B + C
   Y = A + B + D = X + (-C+D) = X + Delta
   ```
   
   Introducing subtraction is analogous to introducing negative numbers in mathematics, expanding the solution space of the problem; previously unsolvable structural reuse problems now become solvable.

The Software Engineering Institute at Carnegie Mellon University traces software engineering as a trajectory of continually increasing reuse granularity. But truly coarse-grained, system-level reuse faces many theoretical challenges. For explorations in software engineering theory, see [Delta Oriented Programming through the lens of Reversible Computation](https://zhuanlan.zhihu.com/p/377740576).

From the perspective of Reversible Computation, the principles of software reuse undergo a fundamental shift: from component reuse’s same-can-be-reused to Reversible Computation’s related-can-be-reused. Arbitrary Y and arbitrary X can be connected via a Delta transformation, achieving reuse without requiring them to form a traditional part–whole composition relationship.

System-level reuse in practice corresponds to developing product Y incrementally without modifying the source code of the base product X. Translated to the abstract level, this amounts to establishing a transformation from X to Y, Y = X + Delta.

Thus, research on product extensibility can be transformed into the study of Delta forms and operations among Deltas—precisely the domain of Reversible Computation theory.

A very practical problem solved by Reversible Computation theory is: When deeply customizing a bank’s core application for different banks, how can we avoid modifying the base product’s source code entirely? That is, can we inherit an entire software product as we inherit a class? Base and customization products evolve in parallel, automatically inheriting new features and bug fixes from the base product without manual code merging.

To better illustrate Reversible Computation, I open-sourced a reference implementation: the Nop platform https://www.gitee.com/canonical-entropy/nop-entropy.

The Nop platform introduces syntax dedicated to reversible Delta merging; see [XDSL: A General Design for Domain-Specific Languages](https://zhuanlan.zhihu.com/p/612512300). It automatically supports coarse-grained software reuse; see [How to achieve customized development without modifying the base product’s source code](https://zhuanlan.zhihu.com/p/628770810).

## Physical intuitions underlying Reversible Computation

Reversible Computation brings a worldview different from traditional computer science. Its methodology is rooted in the second law of thermodynamics (entropy increase) and perturbation theory. With an open mind, it can at least give you different inspirations.

In calculus we all learned Taylor series expansion:

$$
y = 3(x+dx)^2 + 2(x+dx) = 3x^2 + 2x + (6x+2)dx + 3dx^2 
$$

Mathematically, we can collect Delta small quantities dispersed throughout the system. Physically, aggregated same-order small quantities can be given precise meaning (for example, collecting all first-order terms yields the derivative, corresponding to physical notions like velocity, acceleration, intensity, force). Yet in software there has never been such a systematic, hierarchical Delta decomposition mechanism. Think carefully and you’ll find this is truly odd.

From observing the Taylor series we can draw a series of physical intuitions.

1. Intuition 1: The independent existence of Deltas implicitly requires the original system to have a well-defined coordinate system. In constructing Taylor series, Deltas arise from various parts of the system and are collected. If we can store and manage Deltas separately, when ultimately merging them back into the original system we must know the source location of each local change. Deltas must transparently traverse all structural barriers and be applied at the coordinates where the perturbation actually occurred. Think carefully and you will realize that the first requirement for any Delta-capable design is a systematic, uniqueness-bearing positioning mechanism within the system—for example, the table–row–column coordinate system in databases.

2. Intuition 2: The type system is not an appropriate coordinate system. The basic assumption of types is that multiple objects share the same type; using type for positioning causes conceptual ambiguity. A typical scenario: in a list of buttons, we generally position by id rather than by type. If you studied group representation theory, you know the same physical fact can be projected into different spaces to reveal different Delta construction rules. For instance, for a function definition, we can project it to the binary space, using XOR to establish Delta relationships between arbitrary function representations. We can also project functions to the general line-text space and use diff algorithms to establish Delta relationships—git is built on such projections. Projecting functions onto a type system is also a general approach; some people establish Deltas over type systems, e.g., Compositional Programming, https://www.bilibili.com/video/BV1Ph4y1M7aB/. A typical type system merely defines a two-level coordinate system of class (module) and method. Importantly, for the same physical object we can define different Delta forms in different Delta spaces; the definition of Deltas and their operations is not unique.

3. Intuition 3: Based on coordinate systems, we shift to the worldview of field theory. The black-box model of components is akin to the worldview of high-school Newtonian mechanics—it is entirely mechanistic: a rigid body’s motion is described by a few parameters (center-of-mass coordinates, size, shape, orientation), its internal structure is unobservable and irrelevant, and rigid bodies interact via direct contact, with shapes requiring precise matching to form seamless wholes. Even in classical mechanics, slightly more advanced viewpoints switch to Lagrangian or Hamiltonian formulations, whose essence is a shift to the worldview of fields. A field is, in essence, a ubiquitous coordinate system where a physical quantity is specified at every point in that system. Fields have infinite degrees of freedom, but via coordinates they are describable, definable, and researchable; at every coordinate point we can precisely measure local changes. In the worldview of fields, the core image is that objects always soak in fields (ubiquitous coordinate systems), rather than isolated pairwise interactions among objects.

4. Intuition 4: The same physical fact can be expressed in different coordinate systems, but in specific domains there exist intrinsic coordinate systems best suited to that domain. A programming language can be viewed as defining a semantic space; it provides a coordinate system for expressing physical facts. A domain-specific language (DSL) provides a coordinate system best suited for expressing facts in its domain. Especially as requirements change, a DSL expression will exhibit only a few localized changes, whereas the code implemented in a general-purpose programming language may undergo extensive adjustments. This is akin to describing a circle: in Cartesian coordinates both axes change, whereas in polar coordinates only the angle changes, thereby reducing dimensionality.

5. Intuition 5: A DSL model is both an entity defined within a coordinate system and a contributor to the coordinate system itself. There is no need to add special descriptor information for extension points; we can use the DSL’s own structural markers. This is similar to the moving frame method in exterior calculus: we describe motion in a coordinate system, and the direction of motion in turn constitutes a naturally generated coordinate system.

6. Intuition 6: The same physical fact can have multiple coordinate representations. Coordinate system transformations can be effective structural construction techniques. For example:
   
   ```
   Excel <==> DomainObject <==> UI
   ```
   
   Without programming, the UI for visual editing can be automatically derived from the meta-model, enabling visual editing of model objects. Transforming from one form to another resembles a functor mapping in category theory; what we focus on is not a specific object but the need to define a general transformation strategy for every possible object in the formal system.

7. Intuition 7: A full model can be viewed as a special case of a Delta. A = 0 + A; hence full and Delta can be isomorphic and should be constrained by the same schema. In this case, the Delta of a Delta is still an ordinary Delta.

8. Intuition 8: A Delta can exist independently of the base. Many people view a Delta as base + patch, considering the base primary and the patch secondary. In fact, the base can be seen as the patch of the patch; base and patch are in a dual relationship, and theoretically there is no need to distinguish them. A Delta has value in its independent existence; it need not attach to a base to be understood.

These intuitions can be summarized by Wittgenstein’s dictum: The limits of my language mean the limits of my world. Reversible Computation theory further interprets it as: A language is a coordinate system; Deltas arise naturally from coordinates.

Reversible Computation systematically develops the concept of domain coordinate systems. In concretizing Reversible Computation theory, the Nop platform adopts the following approach:

1. Use DSLs to describe all business logic. DSLs adopt XML syntax, i.e., a Tree structure. The filesystem plus the Tree within files constitute a coordinate system refined down to individual attributes and functions.

2. All DSLs conform to the XDSL specification and share the same Delta merging syntax: x:extends, x:override, x:gen-extends. These implement the computational patterns required by Reversible Computation.

3. A special XDSL called XDef serves as the meta-model defining the syntax for all other DSLs, and XDef itself is constrained by XDef. Its most essential difference from a type system is that list structures must include a unique identifier attribute, such as name or id, thereby ensuring the existence of domain coordinates.

4. All systems implemented via XDSL automatically obtain one feature: support for Delta customization. Without modifying the original system’s source code, one can provide additional Delta to deeply customize all logic and data structures. Existing programming languages lack the concept of domain coordinate systems and therefore cannot achieve such system-level, coarse-grained reuse.

Reversible Computation operations are entirely implementable at compile time and thus do not affect runtime performance. For the framework-level technical approach, see [Design of Low-Code Platforms from the Perspective of Tensor Product](https://zhuanlan.zhihu.com/p/531474176).

For a detailed definition of domain coordinate systems and proofs of associativity, see [An Analysis of Reversible Computation Theory for Programmers](https://zhuanlan.zhihu.com/p/632876361)

<!-- SOURCE_MD5:b776e522ea39713872cdc6001f8c6273-->
