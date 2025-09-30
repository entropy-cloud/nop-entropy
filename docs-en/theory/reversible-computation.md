
# Reversible Computation: The Next-Generation Theory of Software Construction

It is well known that the foundations on which computer science stands are two fundamental theories proposed in 1936: Turing’s theory of the Turing machine and Church’s earlier Lambda calculus in the same year. These two theories established the conceptual basis for so-called Universal Computation, delineating two technical routes that are Turing-complete with the same computational power yet formally poles apart. If we regard these two theories as two extremes of the primordial visage of the world as revealed by God, is there a more moderate and flexible middle path to reach the shores of universal computation?

Since 1936, software, as the core application of computer science, has undergone continuous conceptual transformations. Various programming languages/system architectures/design patterns/methodologies have emerged in an endless stream. Yet, when it comes to the fundamental principles of software construction, we have not truly escaped the range set by those two basic theories at the outset. If we define a new theory of software construction, what essential novelty could its new concepts bring? What thorny problems could it solve?

In this article, I propose that on the basis of Turing machines and the lambda calculus, a new core concept—reversibility—can be naturally introduced, thereby forming a new theory of software construction: Reversible Computation. Reversible Computation provides a higher level of abstraction distinct from mainstream methods in the industry, significantly reducing the inherent complexity of software and removing theoretical obstacles to coarse-grained software reuse.

The idea of Reversible Computation does not originate from computer science itself but from theoretical physics. It regards software as an abstract entity in constant evolution, described by different operational rules at different levels of complexity, focusing on how the small Deltas generated during evolution propagate in an orderly manner within the system and interact with each other.

Section 1 introduces the basic principles and core formulas of the theory of Reversible Computation. Section 2 analyzes its differences and connections with traditional software construction theories such as components and model-driven approaches, and discusses its applications in software reuse. Section 3 deconstructs innovative technologies like Docker and React from the perspective of Reversible Computation.

## I. Basic Principles of Reversible Computation

Reversible Computation can be seen as an inevitable result of modeling the world using Turing computation and the lambda calculus in a real world where information is finite. We can understand this through the following simple physical imagery.

First, a Turing machine is a structurally frozen machine: it has a finite enumerable set of states, can execute only a finite number of instruction types, yet can read and save data on an infinitely long tape. For example, our everyday computers have fixed hardware capabilities at the factory, but by installing different software and passing in different data files, they can automatically produce arbitrarily complex target outputs. Formally, the computation process of a Turing machine can be written as:

$$
Target\ Output = Fixed\ Machine\ (\text{infinitely complex input})
$$

In contrast, the core concept of the lambda calculus is the function: a function is a small computational machine, and the composition of functions is still a function. In other words, by recursively composing machines with machines, we can produce more complex machines. The computational power of the lambda calculus is equivalent to that of Turing machines, which means that if we are allowed to continually create ever more complex machines, even when the input is a constant 0, we can obtain arbitrarily complex target outputs. Formally, the computation process of the lambda calculus can be written as:

$$
Target\ Output = Infinitely\ Complex\ Machine\ (\text{fixed input})
$$

It can be seen that both computation processes above can be expressed in the abstract form Y = F(X). If we interpret Y = F(X) as a modeling process—i.e., we try to understand the structure of the input and the mapping between input and output, and reconstruct the output in the most economical way—we will find that both the Turing machine and the lambda calculus assume conditions that cannot be satisfied in the real world. In the real physical world, human cognition is always limited. All quantities must be distinguished into known parts and unknown parts. Therefore, we need the following decomposition:

$$
\begin{aligned}
 Y &= F(X) \\
   &= (F_0 + F_1) (X_0+X_1)\\
   &= F_0(X_0) + \Delta
\end{aligned}
$$

Rearranging the symbols, we obtain a computational pattern that adapts to a much broader range:

$$
Y = F(X) \oplus \Delta
$$

In addition to the functional operation F(X), a new structural operator ⊕ appears here, representing a composition operation between two elements rather than ordinary numerical addition, and introduces a new concept: Delta (Δ). What is special about Δ is that it must inherently contain some inverse element; the result of combining F(X) with Δ is not necessarily an “increase” in output—it could very well be a “decrease”.

In physics, the necessity of the existence of Delta and the fact that Δ contains inverse elements are self-evident, because physical modeling must take into account two basic facts:

1. The world is “uncertain”; noise is always present.
2. The complexity of the model must match the inherent complexity of the problem; it captures the stable, invariant trends and laws at the core of the problem.

For example, for the following data:
![](https://pic4.zhimg.com/80/v2-91f19a10faa36653267ffbd4eab86b7f_1440w.webp)

the model we build can only be a simple curve like in (a). The model in (b) attempts to fit every data point exactly; this is called overfitting in mathematics and is poor at describing new data. Meanwhile, in (c), restricting the Delta to only positive values severely limits the descriptive accuracy of the model.

The above is a heuristic explanation of the abstract computational pattern Y = F(X) ⊕ Δ. Below, we introduce a concrete technical implementation in the field of software construction that realizes this computational pattern, which I call Reversible Computation.

Reversible Computation refers to a technical route that systematically applies the following formula to guide software construction:

```
   App = Delta x-extends Generator<DSL>
```

* App: The target application to be built
* DSL: Domain Specific Language— a business logic description language tailored to a specific domain; this is the textual representation of the so-called domain model
* Generator: Based on the information provided by the domain model, repeatedly applying generation rules can derive a large amount of derivative code. Implementations include standalone code generation tools and compile-time template expansion based on metaprogramming
* Delta: The differences between logic generated by the known model and the target application logic are identified, collected, and organized into an independent Delta description
* x-extends: The Delta description and the model-generated part are combined using techniques similar to Aspect-Oriented Programming (AOP), involving a series of operations such as addition, modification, replacement, and deletion on the model-generated portion

A DSL is a high-density expression of critical domain information and directly guides the Generator to produce code, similar to how Turing computation drives a machine to execute built-in instructions through input data. If we view the Generator as symbol substitution generation, then its execution and composition rules are essentially a re-enactment of the lambda calculus. Delta merging is, in a sense, a novel operation because it requires a meticulous, ubiquitous capacity to collect changes, able to separate out and aggregate same-order small quantities scattered throughout the system, so that the Delta has an independent meaning and value. At the same time, the system must explicitly establish the concepts of inverse elements and inverse operations; only within such a conceptual system can a Delta, as a mixture of “existence” and “non-existence,” be expressible.

Existing software infrastructure, if not thoroughly transformed, cannot effectively implement Reversible Computation. Just as the Turing machine model gave birth to the C language and the lambda calculus gave rise to Lisp, to effectively support Reversible Computation, I propose a new programming language—the X language—which has built-in key features such as Delta definition, generation, merging, and splitting. It can rapidly establish domain models and implement Reversible Computation on top of them.

To implement Reversible Computation, we must establish the concept of Delta. Change produces Delta; Delta can be positive or negative, and it should satisfy the following three requirements:

1. Deltas exist independently.
2. Deltas interact with each other.
3. Deltas have structure.

In Section 3, I will use Docker as an example to illustrate the importance of these three requirements.

The core of Reversible Computation is “reversible,” a concept closely related to entropy in physics. Its importance far exceeds program construction itself. In a separate article on the methodological origins of Reversible Computation, I will elaborate further.

Just as the emergence of complex numbers expanded the solution space of algebraic equations, Reversible Computation supplements the existing software construction system with the key technique of “reversible Delta merging,” thereby greatly expanding the feasible scope of software reuse and making system-level, coarse-grained software reuse possible. Meanwhile, under this new perspective, many previously intractable model abstraction problems can find much simpler solutions, significantly reducing the inherent complexity of software construction. I will detail this in Section 2.

Although software development is reputedly knowledge-intensive, to this day the daily work of many frontline developers still involves large amounts of mechanical manual operations of copying/pasting/modifying code. In the theory of Reversible Computation, structural changes to code are abstracted into automatically executable Delta merging rules. Therefore, through Reversible Computation, we can create the foundational conditions for the automated production of software itself. Based on the theory of Reversible Computation, I propose a new industrialized software production paradigm called NOP (Nop is nOt Programming), to mass-produce software in a non-programming manner. NOP is not programming, but it is not non-programming either; it emphasizes separating logic that business personnel can intuitively understand from pure technical implementation logic, designing them with appropriate languages and tools, and then gluing them together seamlessly. I will introduce NOP in detail in a separate article.

Reversible Computation and reversible computers share the same origin in physics, though their specific technical connotations differ, they are unified in their goals. Just as cloud computing attempts to “cloudify” computation, Reversible Computation and reversible computers attempt to make computation reversible.

## II. The Inheritance and Development of Traditional Theories by Reversible Computation

## (1) Components

Software originated as a byproduct of mathematicians studying Hilbert’s tenth problem, and early software was mainly used for mathematical and physical calculations. At that time, concepts in software were undoubtedly abstract and mathematical. With the popularization of software, the proliferation of application software development gave rise to object-oriented and component-based methodologies. These attempted to downplay abstract thinking in favor of aligning with human common sense: extracting knowledge from daily experience, mapping intuitively perceivable concepts in the business domain to objects in software, and, by analogy with manufacturing in the physical world, constructing the final software product by incrementally assembling from nothingness to something, from small to large.

Familiar concepts in software development—frameworks, components, design patterns, architectural views—all come directly from the production experience of the construction industry. Component theory inherits the essence of object-oriented thinking. Leveraging the concept of reusable prefabricated parts, it has created a massive third-party component market and achieved unprecedented technical and commercial success, remaining the most mainstream guiding philosophy in software development even today. However, a fundamental defect within component theory has prevented it from advancing its success to a new height.

We know that reuse means repeated use of an existing finished product. To achieve component reuse, we need to find the common parts between two pieces of software, separate them, and organize them into a standard form according to component specifications. However, the common part of A and B is of a granularity smaller than both A and B. In many cases, the common part among a multitude of software systems is much smaller in granularity than any one of them. This limitation directly leads to the fact that the larger the granularity of a software functional module, the harder it is to reuse directly. Component reuse has a theoretical limit. One can reuse 60%-70% of the workload through component assembly, but few can exceed 80%, let alone achieve more than 90% reuse at the system level.

To overcome the limitations of component theory, we need to re-examine the abstract nature of software. Software is an information product that exists in an abstract logical world; information is not matter. The rules of construction and production in the abstract world are fundamentally different from those in the material world. The production of physical products always incurs cost, while the marginal cost of copying software can be zero. Removing a table from a room in the physical world requires going through the door or window, but in the abstract information space it only requires changing the table’s coordinate from x to -x. The operational relationships among abstract elements are not constrained by numerous physical limitations. Therefore, the most effective form of production in the information space is not assembly, but the mastery and formulation of operational rules.

If we reinterpret object-oriented and component technology from a mathematical perspective, we find that Reversible Computation can be regarded as a natural extension of component theory.

* Object-Oriented: Inequality A > B
* Components: Addition A = B + C
* Reversible Computation: Y = X + ΔY

A core concept in object-oriented design is inheritance: a derived class inherits from a base class and automatically possesses all its functionality. For example, tiger is a derived class of animal. Mathematically, we can say the concept tiger (A) contains more content than the concept animal (B), i.e., tiger > animal (A > B). Hence, any proposition satisfied by animal is naturally satisfied by tiger; for instance, if animals can run, then tigers must also run (P(B) -> P(A)). Anywhere in the program where the concept animal is used can be replaced with tiger (Liskov substitution principle). In this way, inheritance brings automatic reasoning into software. Mathematically, this corresponds to inequalities, i.e., a partial order.

The theoretical dilemma of object-oriented design lies in the limited expressive power of inequalities. For A > B, we know A has more than B, but we cannot explicitly express what that “more” is. Moreover, for A > B and D > E, even if the extra parts are exactly the same, we have no way to reuse that portion. Component technology explicitly states “composition over inheritance,” which is equivalent to introducing addition:

```
  A = B + C
  D = E + C
```

Thus, we can abstract out component C for reuse.

Following this direction, the next step is naturally to introduce “subtraction,” so that A = B + C becomes a true equation, from which we can solve by transposition:

```
  B = A - C = A + (-C)
```

The “negative component” introduced by subtraction is an entirely new concept that opens a new door for software reuse.

Suppose we have built a system X = D + E + F, and now we need to build Y = D + E + G. If we follow the component-based approach, we need to decompose X into multiple components, replace component F with G, and reassemble. If we follow the technical route of Reversible Computation and introduce the inverse element -F, we immediately obtain:

```
Y = X - F + G = X + (-F + G) = X +  DeltaY
```

Without decomposing X, by directly appending a Delta DeltaY, we can transform system X into system Y.

Component reuse requires “identicality for reuse.” But in the presence of inverse elements, a complete system X with maximum granularity can be reused directly without any change; the scope of software reuse is expanded to “relevant is reusable.” The granularity of reuse is no longer limited. The relationships among components also undergo a profound transformation—from monotonic compositional relationships to richer and more versatile transformational relationships.

The physical imagery Y = X + ΔY has very practical significance for the development of complex software products. X can be the base version, or the mainline version, of our software product. When deployed at different customers, a large number of customization requirements are isolated into an independent Delta ΔY. These customized Delta descriptions are stored separately and merged with the mainline code through compilation techniques. The architecture and code of the mainline need only consider stable, core requirements within the business domain and will not be impacted by contingent, client-specific needs, thereby effectively avoiding architectural decay. Mainline R&D and the implementation of multiple projects can proceed in parallel, with different implementation versions corresponding to different ΔY’s without affecting each other. Meanwhile, the mainline code and all customization code are mutually independent, enabling overall upgrades at any time.

## (2) Model Driven Architecture (MDA)

Model Driven Architecture (MDA) was proposed by the Object Management Group (OMG) in 2001 as a software architecture design and development methodology. It is regarded as a milestone in the shift from code-centric to model-centric development. The theoretical foundation of most so-called software development platforms today is related to MDA.

MDA seeks to raise the level of abstraction in software development, using modeling languages (e.g., Executable UML) directly as programming languages, and then translating high-level models into low-level executable code using compiler-like techniques. In MDA, application architecture and system architecture are clearly distinguished and described by a Platform Independent Model (PIM) and a Platform Specific Model (PSM), respectively. PIM reflects the functional model of an application system, independent of specific implementation technologies and runtime frameworks, while PSM focuses on using specific technologies (e.g., J2EE or .NET) to implement the functionalities described by the PIM, providing a runtime environment for the PIM.

The ideal scenario for MDA is that developers use visual tools to design the PIM, then select the target runtime platform. The tool automatically applies mapping rules for the specific platform and implementation language, transforms the PIM into the corresponding PSM, and finally generates executable application code. Program construction based on MDA can be expressed by the following formula:

```
App = Transformer(PIM)
```

MDA’s vision is to ultimately eliminate traditional programming languages altogether, just as C replaced assembly. However, after so many years of development, it has not shown an overwhelming competitive advantage over traditional programming in broad application domains.

In fact, current MDA-based development tools always struggle with inherent inflexibility in rapidly evolving business domains. As analyzed in Section 1, modeling must consider Deltas. In MDA’s construction formula, the left side App represents various unknown requirements, while on the right side, the designer of Transformer and PIM is mainly the tool vendor. Such an equation—unknown = known—cannot remain balanced indefinitely.

At present, tool vendors mostly offer a comprehensive and exhaustive set of models, attempting to predict all possible user scenarios in advance. But as we know, “there is no free lunch.” The value of a model lies in embodying essential constraints in the business domain. No model is optimal in all scenarios. Predicting requirements leads to a paradox: if a model has too few built-in assumptions, it cannot automatically generate a large amount of useful work from minimal user input, nor can it prevent user errors—the model’s value is not obvious. Conversely, if the model has too many assumptions, it becomes rigidified around a specific business scenario and cannot adapt to new situations.

When we open an MDA tool’s designer, the most common feeling is that most options aren’t needed or are inscrutable, while the needed options cannot be found anywhere.

Reversible Computation extends MDA in two ways:

1. In Reversible Computation, both the Generator and the DSL are encouraged to be extended and adjusted by users, similar to language-oriented programming.
2. There is an additional Delta customization phase that allows precise local corrections to the overall generated output.

In the NOP production paradigm I propose, a new critical component must be included: a “designer of designers.” Ordinary programmers can quickly design and develop their own DSLs and corresponding visual designers using this meta-designer. They can also use it to customize any designer in the system, freely adding or removing elements.

## (3) Aspect Oriented Programming (AOP)

Aspect-Oriented Programming is a programming paradigm complementary to Object-Oriented Programming (OOP). It enables encapsulation of so-called cross-cutting concerns that span multiple objects. For example, a specification might require that all business operations be logged and that all database modifications occur within transactions. Under traditional OOP, one sentence in the requirements could cause a sudden expansion of redundant code across many classes. With AOP, these common “decorative” operations can be stripped into independent aspect descriptions. This is the orthogonality between vertical and horizontal decomposition.

![](https://pic2.zhimg.com/80/v2-4a0da0bcc0165fb96db9db88d00af979_1440w.webp)

AOP is essentially a combination of two capabilities:

1. Locate target pointcuts in the program’s structural space.
2. Modify local program structures to weave the extension logic (Advice) into specified locations.

Location depends on a well-defined global structural coordinate system (how can you locate without coordinates?), while modification depends on well-defined local program semantic structures. The limitation of current mainstream AOP technologies is that they are all expressed within the OOP context, whereas domain structure and object implementation structure are not always consistent—or, put differently, expressing domain semantics using the coordinate system of objects is insufficient. For example, “applicant” and “approver” are distinct concepts that must be clearly distinguished in the domain model, but at the object level they might both correspond to the same Person class. Often, AOP cannot directly translate domain descriptions into pointcut definitions and advice implementations. Reflecting this limitation at the application level, we find that beyond a few “classic” cross-domain use cases—logging, transactions, lazy loading, caching—AOP often lacks an obvious fit.

Reversible Computation requires AOP-like location and structural rectification capabilities, but it defines these in the domain model space, greatly expanding AOP’s application scope. In particular, the structural Deltas generated by the self-evolution of domain models in Reversible Computation can be expressed in a form similar to AOP aspects.

We know that components can identify “identicalities” repeatedly appearing in programs, while Reversible Computation can capture “similarities” in program structure. Identicality is rare and requires keen discrimination, but in any system there is a readily available form of similarity: the similarity between the system at a later time and its own historical snapshots during dynamical evolution. In previous technical systems, this similarity had no dedicated technical expression.

Through vertical and horizontal decomposition, the web of concepts we establish exists in a design plane. As the design plane evolves along the time axis, it naturally produces a “3D” mapping: the design plane at a later time can be seen as obtained by adding a Delta mapping (customization) on the former plane, and the Delta is defined at every point on the plane. This imagery is similar to the concept of a functor in Category Theory, with Delta merging in Reversible Computation playing the role of functor mapping. Therefore, Reversible Computation effectively extends the original design space and finds a concrete technical expression for the concept of evolution.

## (4) Software Product Line (SPL)

The theory of Software Product Lines (SPL) originates from the insight that in a business domain, very few software systems are completely unique. Many software products have similarities in form and function and can be organized into a product family. By studying, developing, and evolving all products (existing and not yet existing) in a product family as a whole, and by scientifically extracting their commonalities while managing variability effectively, it becomes possible to realize large-scale, systematic software reuse, thereby industrializing software production.

SPL engineering adopts a two-phase lifecycle model, distinguishing between domain engineering and application engineering. Domain engineering analyzes the commonalities among software products in a business domain, establishes domain models and a common product-line architecture, and forms reusable core assets—development for reuse. Application engineering essentially uses reuse to develop—development with reuse—i.e., the production activity of building concrete application products using existing core assets such as architecture, requirements, tests, documents, and so on.

In 2008, researchers at CMU-SEI claimed that SPL can deliver the following benefits:

1. More than 10x productivity improvement
2. More than 10x product quality improvement
3. More than 60% cost reduction
4. More than 87% reduction in staffing needs
5. More than 98% reduction in time-to-market
6. Months, not years, to enter new markets

The ideal painted by SPL is very appealing: product-level reuse above 90%, agile customization on demand, domain architectures insulated from technological churn, and excellent, measurable economic benefits, etc. The only question is how to achieve this. Although SPL engineering attempts to comprehensively leverage all managerial and technical means to strategically reuse every technical asset at the organizational level (including documents, code, specifications, tools, etc.), under the current mainstream technical regime, building a successful SPL still faces numerous difficulties.

The philosophy of Reversible Computation aligns closely with SPL theory. Its technical approach brings new solutions to SPL’s core technical challenge—variability management. In SPL engineering, traditional variability management mainly comprises three approaches: adaptation, replacement, and extension:

![](https://pic2.zhimg.com/80/v2-3d835ae5c250e6bfa8744a695c9fdc65_1440w.webp)

These three can all be seen as adding functionality to the core architecture. However, obstacles to reusability do not always come from the inability to add new functionality; often they arise from the inability to mask existing functionality. Traditional adaptation requires interface-consistent matching, a rigid coupling that, once mismatched, transmits stress upwards and ultimately forces wholesale component replacement. Reversible Computation adds the key mechanism of “elimination” to variability management via Delta merging, enabling the construction of flexible adaptation interfaces in domain model space and effectively controlling the impact scope of variation points.

Although Deltas in Reversible Computation can be interpreted as extensions of the base model, they differ significantly from plugin-extension techniques. In a platform-plugin structure, the platform is the core principal body; plugins attach to the platform like patches and are conceptually secondary. In Reversible Computation, by applying certain formal transformations, we obtain a more symmetric formula:

$$
A = B \oplus G(D) \equiv (B,D)
$$

If we regard G as relatively invariant background knowledge, we can hide it formally and define a more advanced “bracket” operator, similar to the “inner product” in mathematics. In this form, B and D are dual: B complements D, and D complements B. Meanwhile, note that G(D) embodies model-driven architecture. The value of model-driven approaches lies in the fact that small changes in model D can be amplified by G into large derivative changes across the system. Therefore, G(D) is a nonlinear transformation, while B is what remains of the system after removing the nonlinear factors corresponding to D. When all complex nonlinear influencing factors are stripped away, the remaining part B may be simple, and may even form a new, independently understandable domain model structure (analogous to how sound waves are perturbations of air; we can describe sound waves with a sine-wave model without studying air itself).

The form A = (B, D) can be directly generalized to situations with more domain models:

$$
A = (B,D,E,F,...)
$$

Since B, D, E, etc. are domain models described by some DSL, they can be interpreted as components resulting from projecting A onto specific domain model subspaces. In other words, application A can be expressed as a “Feature Vector,” for example:

```
App = (Workflow, Reporting, Permissions, ...)
```

Compared with Feature-Oriented Programming commonly used in SPL, the feature decomposition scheme of Reversible Computation emphasizes domain-specific descriptions. Feature boundaries are clearer, and conceptual conflicts during feature composition are easier to handle.

Feature vectors themselves constitute a higher-dimensional domain model and can be further decomposed, forming a model-level chain. For example, define

$$
D' \equiv (B,D) \\G'(D') \equiv B \oplus G(D)
$$

and suppose D' can be further decomposed:

$$
D' = V\oplus M(U) = M'(U')
$$

Then we have

$$
\begin{aligned}
A &= B \oplus G(D)\\
  &= G'(D')\\
  &= G'(M'(U'))\\
  &= G'M'(V,U)
\end{aligned}

$$

Ultimately, we can describe D′ via a domain feature vector U′, and then describe the original model A via the domain feature vector D′.

This construction strategy of Reversible Computation is similar to deep neural networks. It no longer confines itself to a single model with a vast number of tunable parameters but builds a series of models across different abstraction and complexity levels, constructing the final application through progressive refinement.

From the perspective of Reversible Computation, the work of application engineering becomes describing software requirements using feature vectors, while domain engineering is responsible for generating the final software according to the feature vector descriptions.

## III. The Nascent Delta Revolution

## (1) Docker

Docker is an application container engine open-sourced in 2013 by the startup dotCloud. It can package any application and its dependencies into a lightweight, portable, self-contained container, thereby creating a new form of software development, deployment, and delivery using the container as a standardized unit.

Docker burst onto the scene and instantly outcompeted Google’s own container technology lmctfy (Let Me Contain That For You), while also propelling Google’s Go language to instant stardom. Docker’s development has been unstoppable since. Starting in 2014, a Docker storm swept the globe, driving unprecedented transformation in operating system kernels. Under the bandwagoning of numerous giants, it instantly ignited the container cloud market, fundamentally changing the technological form of the entire lifecycle of enterprise applications—from development and build to deployment and runtime.

![](https://pic2.zhimg.com/80/v2-d6ef1c89995987f99c69e2c9f2456985_1440w.webp)

Docker’s success stems from its essential reduction of runtime complexity, and its technical solution can be viewed as a special case of the theory of Reversible Computation. Docker’s core technical pattern can be summarized by the following:

Dockerfile is a DSL for building container images, for example:

```
FROM ubuntu:16.04
RUN useradd --user-group --create-home --shell /bin/bash work
RUN apt-get update -y && apt-get install -y python3-dev
COPY . /app
RUN make /app

ENV PYTHONPATH /FrameworkBenchmarks
CMD python /app/app.py

EXPOSE 8088
```

With a Dockerfile, one can accurately and quickly describe the base image, specific build steps, runtime environment variables, and system configuration required by a container.

The Docker application plays the role of the Generator in Reversible Computation: it interprets the Dockerfile and executes corresponding instructions to generate container images.

A particularly creative innovation of Docker is its use of Union FS (Union File System). This file system adopts a layered construction. Once a layer is built, it no longer changes. Any modification in a higher layer is recorded only in that layer. For example, when modifying a file in a lower layer, a copy-on-write action copies it to the current layer; when deleting a file from a lower layer, it is not actually deleted but is marked as deleted in the current layer. Docker uses Union FS to merge multiple container images into a complete application; the essence of this technique is exactly the aop_extends operation in Reversible Computation.

Docker is often compared to containers and shipping with standardized units similar to standardized containers: standardized containers allow free transport and composition without considering the internal content. However, this analogy is superficial and even misleading. Shipping containers are static, simple, and have no external interfaces, whereas application containers are dynamic, complex, and have extensive interactions with the external world. Encapsulating such dynamic complexity into so-called standard containers is vastly more challenging than packaging static objects. Without introducing a Delta-supporting file system, it is impossible to construct the flexible boundaries required for logical separation.

Virtual machines can also provide standardized encapsulation, and even Delta storage mechanisms have long been used in VMs to implement incremental backups. How, then, does Docker fundamentally differ from virtual machines? Recalling the three basic requirements for Delta in Section 1, we can clearly see Docker’s uniqueness:

1. Deltas exist independently: Docker’s most important value is that, through container encapsulation, it discards the operating system layer as a background presence (indispensable but generally not requiring comprehension) that accounts for 99% of size and complexity. Application containers become first-class entities that can be independently stored and operated. These lean containers comprehensively outperform bloated VMs in performance, resource usage, and manageability.

2. Deltas interact: Docker containers interact in precisely controlled ways, using the operating system’s namespace mechanism to selectively isolate or share resources. In contrast, there are no isolation mechanisms among Delta slices of VMs.

3. Deltas have structure: Although VMs support incremental backups, people lack suitable means to actively construct a specified Delta slice. Ultimately, this is because VMs define Deltas in the binary byte space, which is an impoverished space with few user-controllable construction patterns. Docker, on the other hand, defines Deltas in the Delta file system space, which inherits the richest historical resources from the Linux community. Every shell command’s result is ultimately reflected in the file system as adding/deleting/modifying certain files, so every shell command can be regarded as the definition of a Delta. Thus, Deltas constitute an extraordinarily rich structural space, serving both as transformation operators in this space (shell commands) and as the results of applying those operators. Delta meets Delta to create new Delta. This perpetual generativity is the source of Docker’s vitality.

## (2) React

In 2013—the same year Docker was released—Facebook open-sourced a revolutionary front-end framework: React. React’s technical philosophy is very distinctive. Based on functional programming ideas and a seemingly fanciful Virtual DOM concept, it introduced a whole new set of design patterns, launching a new age of exploration in front-end development.

```javascript
class HelloMessage extends React.Component {
  constructor(props) {
    super(props);
    this.state = { count: 0 };
    this.action = this.action.bind(this);
  }

  action(){
    this.setState(state => ({
      count: state.count + 1
    }));
  },

  render() {
    return (
      <button onClick={this.action}>
        Hello {this.props.name}:{this.state.count}
      </button>
    );
  }
}

ReactDOM.render(
  <HelloMessage name="Taylor" />,
  mountNode
);
```

The core of a React component is the render function, whose design references common back-end template rendering techniques. The main difference is that back-end templates output HTML text, while a React component’s render function uses JSX, an XML-like template syntax, and compiles to produce Virtual DOM node objects at runtime. For example, the render function of the HelloMessage component above translates roughly to:

```javascript
render(){
   return new VNode("button", {onClick: this.action,
          content: "Hello "+ this.props.name + ":" + this.state.count });
}
```

A React component can be described by the following formula:

```
VDOM = render(state)
```

When state changes, re-executing render produces a new Virtual DOM node. Virtual DOM nodes can be translated into real HTML DOM objects to update the UI. This strategy of regenerating the full view based on state greatly simplifies front-end development. For example, for a list view, traditional code requires writing separate DOM operations for adding/updating/deleting rows. In React, changing the state and re-executing the single render function suffices.

The only problem with regenerating the DOM on every change is performance—especially when front-end interactions are numerous and state changes are frequent. React’s masterstroke is the diff algorithm based on the Virtual DOM, which can automatically compute the Delta between two Virtual DOM trees. When state changes, we simply perform the DOM update operations corresponding to the Virtual DOM Delta (updating the real DOM triggers style and layout calculations, which are expensive, whereas manipulating the Virtual DOM in JavaScript is very fast). The overall strategy can be expressed as:

$$
state = state_0 \oplus state_1\\
\Delta VDom = render(state_1) - render(state_0)\\
\Delta Dom = Translator(\Delta VDom)
$$

Clearly, this strategy is also a special case of Reversible Computation.

With just a little attention, one can notice that in recent years, concepts expressing Delta operations such as merge/diff/residual/delta are appearing more and more often in software design. For example, in big data stream processing engines, the relationship between streams and tables can be expressed as:

$$
Table = \int Stream
$$

CRUD operations on tables can be encoded as event streams, and accumulating events that represent data changes yields the data tables.

Modern science originated with the invention of calculus. The essence of differentiation is the automatic computation of infinitesimal Deltas, while integration is its inverse operation—automatically accumulating and merging infinitesimals. In the 1870s, economics went through the marginal revolution, introducing calculus into economic analysis and reconstructing the entire edifice of economics on the concept of the margin. The theory of software construction has reached a bottleneck. It is time to re-appreciate the concept of Delta.

## IV. Conclusion

My academic background is in theoretical physics. Reversible Computation stems from my attempt to introduce ideas from physics and mathematics into the software domain, first proposed around 2007. Historically, the application of natural laws in software has generally been limited to “simulation”: for example, computational fluid dynamics software embeds some of the deepest laws of the world known to humankind. But these laws have not been used to guide and define the construction and evolution of the software world itself; their scope points outside the software world, not to the software world itself. In my view, within the software world we can assume the “God’s-eye view,” planning and defining a series of structural construction laws to help us build the software world. To do this, we must first establish a form of “calculus” within the program world.

Analogous to calculus, the core of Reversible Computation is to elevate “Delta” to a first-class concept and view the complete whole as a special case of Delta (whole = identity + whole). In the traditional program world, what we express is just “presence,” and indeed “all presence.” Delta can only be indirectly obtained via operations on whole states; its expression and manipulation require special handling. Based on the theory of Reversible Computation, we should first define the expression forms of all Delta concepts, and then build the entire domain conceptual system around them. To ensure the completeness of the mathematical space of Deltas (the result of operations among Deltas must still be a valid Delta), what a Delta expresses cannot be just “presence”; it must be a mixture of “presence” and “absence.” In other words, Delta must be “reversible.” Reversibility has a very profound physical connotation. Building this concept into the basic conceptual system can solve many thorny problems in software construction.

To handle distributed problems, modern software development has already embraced the concept of immutable data. To solve the problem of coarse-grained software reuse, we must also accept the concept of immutable logic (reuse can be seen as keeping the original logic unchanged and then adding Delta descriptions). At present, the industry has gradually seen creative practices that actively apply the concept of Delta, all of which can be unified and explained within the theoretical framework of Reversible Computation. I have proposed a new programming language—the X language—that can greatly simplify the technical implementation of Reversible Computation. Based on the X language, I have designed and implemented a series of software frameworks and production tools and, building on them, proposed a new software production paradigm (NOP).

The low-code platform NopPlatform, designed based on the theory of Reversible Computation, is open-sourced:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Introduction & Q&A about the Nop Platform_bilibili](https://www.bilibili.com/video/BV14u411T715/)

<!-- SOURCE_MD5:986725760db72e61ec2157d63dfa4558-->
