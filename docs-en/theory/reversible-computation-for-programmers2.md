
# A Supplementary Analysis of Reversible Computation Theory for Programmers

Reversible Computation theory is a theory of software construction oriented toward evolution, discovered when the author tried to explain software development practices starting from the fundamental principles of physics. Its methodology thus comes not from computer science itself but from theoretical physics, and it describes a relatively abstract law of software construction unfamiliar to many programmers. In the article [The Methodological Source of Reversible Computation Theory](https://zhuanlan.zhihu.com/p/64007521) I initially introduced some inspiring ideas from theoretical physics and mathematics. Recently, I wrote another article [A Discriminative Analysis of Reversible Computation Theory for Programmers](https://zhuanlan.zhihu.com/p/632876361), which explains in detail, starting from concepts familiar to programmers, the concrete forms and practices of Delta and delta merging in programming. Based on reader feedback, in this article I will further supplement the conceptual analysis around Reversible Computation theory, clarifying some common misunderstandings.

## I. How do functions (business processing logic) achieve Delta-ization

Delta is a quantity of change defined within a model space that supports some delta-merge operation; different model spaces have different forms of delta. This means that for the same entity, if we observe it in different model spaces, we actually obtain different results.

First, all structures can be represented in a general binary bit space. For example, when function A is stored in a file it corresponds to binary data 10111..., and we want to convert it to function B, which corresponds to binary 010010.... At the abstract mathematical level, finding the delta X that transforms function A to B amounts to solving the equation `A⊕X=B`. Clearly, if we define ⊕ as bitwise XOR at the binary-bit level, then we can automatically solve for `X=A⊕B`.

```
 A ⊕ B = A ⊕ (A ⊕ X) = (A ⊕ A) ⊕ X = 0 ⊕ X = X
```

> In the proof we used the [annihilation law, associativity, and identity law](https://baike.baidu.com/item/%E5%BC%82%E6%88%96/10993677) of XOR.

Although in the binary bit space we can always solve for the function’s delta, that delta has little business value (it is still valuable at the binary level for compression software). We cannot intuitively understand it, nor do we have convenient means to manipulate it intuitively.

A second common representation space is the line-based text space in which source code resides: all source files can be described by code lines. We can relatively intuitively define code deltas in this space and manipulate them conveniently. For example, general IDE tools provide hotkeys for copying lines, deleting lines, duplicating lines. Further, all debugging tools and version control tools build their business value on the line-based text space. For instance, when source code version control tools compare differences between versions, they automatically compute the Diff in the line-based text space, and trained programmers can directly understand the Diff during code reviews. However, the line-based text space is a general representation space independent of specific business domains, which makes it lack stability when describing business logic code. Some transformations that are entirely equivalent at the business level produce large differences in the line-based text space. For example, code formatting may lead to huge line-level differences. In addition, reordering function definitions generally does not affect program semantics, but from the perspective of the line-based text space, it is an earth-shattering change.

> The Go language always formats the source code according to fixed formatting rules during compilation. On the surface, this causes programmers to lose control over source formatting, but interestingly, it doesn’t imply a lack of functionality; instead, it enables a more stable line-based delta representation.

To obtain a stable function delta representation with clear business semantics, we must define functions within a domain-specific model space. Specifically, we can decompose a function into multiple steps, assign a unique id to each step, and so on. In the Nop platform, we define two delta-ized logical expression forms that enable distributed asynchronous function invocation.

1. TaskFlow based on a stack structure. By default, after each step completes, the next sibling node is automatically executed; when all child nodes finish execution, control returns to the parent node to continue. At runtime, we can locate the state data currently held by each node according to the parent-child relationship, which amounts to forming a stack space. By introducing external persistent storage, TaskFlow can implement a [Continuation mechanism](https://www.zhihu.com/question/61222322/answer/564847803) similar to programming languages: a step can suspend the entire flow or a branch of the flow during execution, and external programs can call TaskFlow’s continueWith function to resume execution from the suspended step. See TaskFlow’s XDef metamodel at [task.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)

2. Workflow based on graph structures. The workflow model can describe common DAGs (directed acyclic graphs) in big data processing, as well as approval flowcharts with rollback and loops in office automation. Workflows rely entirely on to-next step migration rules to specify the next step to execute. Also, since workflow steps are completely peer-level without nesting (except for sub-flows), once the flow is suspended it can restart execution from any step; implementing the Continuation mechanism in Workflow is even simpler. See Workflow’s XDef metamodel at [wf.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef)

```xml
<task x:extends="send-order.task.xml">
  <steps>
     <step id="save-order" />
     <step id="send-email">
         ...
     </step>
  </steps>
</task>
```

The above definition means we apply a delta adjustment to the task definition send-order.task.xml, adding a send-email step after the save-order step. We can also use delta forms to adjust configuration parameters of a step or delete a step, etc.

## II. Can Reversible Computation be applied to runtime evolution?

In previous articles I emphasized many times that Reversible Computation can be implemented through compile-time metaprogramming mechanisms. Some readers may wonder whether this means Reversible Computation only describes static system evolution (applying deltas at compile-time). Can dynamic system evolution (applying deltas at runtime) also be covered by Reversible Computation theory?

This is a very interesting question. First, the conclusion: Yes, Reversible Computation can be applied to runtime evolution.

First, at runtime we can use lazy compilation and just-in-time compilation techniques. In the Nop platform, if we modify a DSL model file after the system goes online, all related models that depend on this DSL model automatically become invalid; the next time these models are accessed they are automatically reloaded and compiled. For example, modifying the NopAuthUser.xmeta file causes NopAuthUser.view.xml and all page models that use NopAuthUser.view.xml to be automatically updated. The automatic update process for DSL models does not require restarting the system—only the compiled results in the cache are updated.

From an abstract perspective, SaaS multi-tenant systems can be viewed as a Delta customization problem. Each tenant corresponds to a Delta, and the runtime state spaces of these Deltas are isolated. In fact, because the Nop platform systematically considers the construction and decomposition mechanisms of Delta, compared to other technical solutions it can handle runtime evolution more elegantly. Based on the Nop platform’s design patterns, we can achieve continuous software evolution without any downtime.

> The difference between runtime interpretation models and compile-time code generation models can be understood via [Currying](https://zhuanlan.zhihu.com/p/38917159) in functional programming. For example, the execution model of the [low-code front-end framework AMIS](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index) corresponds to renderAmis(pageJson, pageData). After currying, it corresponds to (renderAmis(pageJson))(pageData). If we can optimize the logic embedded in renderAmis(pageJson), it amounts to executing Component = CodeGenerator(pageJson). The compilation process is equivalent to generating optimized code via a code generator: many conditionals and loops are executed in advance at compile time, and at runtime only the necessary Component(pageData) logic remains.

A more complex question is whether runtime state space can also be brought under Delta management, since the complete description of an application system equals structure + state. The answer is likewise yes, because Reversible Computation is a wholly abstract theory; it can combine structure space and state space, define a complete high-dimensional space, and then consider delta-ized evolution within this high-dimensional space. (In physics, this high-dimensional space is called [phase space](https://baike.baidu.com/item/%E7%9B%B8%E7%A9%BA%E9%97%B4/8172498?fr=aladdin)). However, practically speaking, considering state drastically increases complexity, so in most cases we consider deltas in structure space and ignore state space.

For example, suppose we are going to make a Delta improvement to a robot. A complete description of the robot certainly includes its structure and the states of these structures. If the robot is moving at high speed, we need to describe relative velocities, accelerations, angular velocities, and even pressure and temperature, etc. But generally we do not modify it while in working mode; instead we switch it to a resting mode, i.e., some non-activated mode. State information in the working mode is irrelevant for modification and can be ignored. In rare extreme cases, if we need to change wheels on a moving train, the basic approach would be: first use a time-freeze technique to locally freeze the timeline, then serialize part of the states that need to be preserved to storage, modify the structure, reload the state, and once everything is ready, resume time evolution. During the application of Delta corrections, time is static, various states are frozen, and the state itself is also data that can be Delta-corrected. (For example, if we use some locking mechanism to directly block external operations, then from the external user’s perspective the system no longer proceeds, which is equivalent to time standing still. More complex techniques involve a multiverse: at some moment you split by snapshot replication into multiple universes, continually copy/apply Deltas to catch up in parallel universes until aligned to a given time point, then pause time briefly to switch timelines.)

> For an interesting application of time standstill, see my article [A Magic Study Report on Paxos](https://zhuanlan.zhihu.com/p/193117183)

The complexity of Delta correction for state lies in the fact that the state information associated with an object is not determined solely by the object itself. For example, if our robot is engaged in an intense battle with another robot, its state fundamentally results from interaction with its opponent. To reproduce this state, we must consider a series of constraints such as conservation of energy, momentum, and angular momentum. We cannot reproduce the state solely from the robot’s individual perspective; instead, we must consider all interacting entities as well as environmental interactions, which makes reproducing or modifying the state very complex. If we modify the object’s structure via Delta customization, then adjusting runtime state data to be consistent with the new object structure and external runtime environment becomes a very tricky matter.

Based on the above discussion, if we wish to evolve at runtime, there are essentially two approaches:

1. Separate structure and state—for example, microservices emphasize statelessness, keeping business state in shared storage, so they can be started and stopped at any time.
2. Define active and inactive modes, and perform structural revisions in the inactive mode.

> If we generalize to consider biological evolution: We can view DNA as a DSL carrying information, and an organism’s growth process corresponds to a Generator, which shapes the organism’s body based on DSL information and environmental information. Meanwhile, in responding to specific challenges, organisms can also use external Deltas. For example, humans can wear a diving suit Delta to gain the ability to operate in water, or add clothing of different thickness to operate in extremely cold or hot regions. Most animals and plants can add very few Deltas, and specialized physical organs increase adaptation to some environments while reducing it in others. For instance, polar bear fur is warm and suitable for the cold Arctic, which prevents adaptation to tropical climates.

## III. Does achieving Delta-ization in existing systems require a complete rewrite?

The Nop platform code base is somewhat large (currently around 200k lines of Java, including roughly 100k lines of auto-generated code). Some programmers seeing this may worry: does introducing the delta-ization calculation mode of Reversible Computation imply we must use the Nop platform? And does using Nop mean rewriting existing business code?

First, the Nop platform seems large because it does not use Spring directly; it rewrites a large number of underlying frameworks in the Java ecosystem, incorporating many innovative designs during the rewrite, increasing convenience at the application layer and improving system performance.

### Why rewrite underlying frameworks

Today’s popular underlying frameworks were designed long ago; their historically accumulated implementations have grown bloated. Hence they face challenges adapting to new technical environments such as asynchrony, GraalVM native compilation, and GraphQL programming models—a case of a big ship hard to turn. Take Hibernate as an example: it has at least 300k lines of code but has long-standing issues such as not supporting subqueries in the FROM clause, not supporting joins beyond association properties, and difficulty optimizing lazy property loading. The NopORM engine implements all core features of Hibernate + MyBatis; it supports most SQL join syntaxes, WITH clauses, LIMIT clauses, etc., and adds application-level common features such as soft delete, multi-tenancy, sharding, field encryption/decryption, field-change history tracking, supports asynchronous invocation, GraphQL-like batch loading optimizations, and generating GraphQL services directly from Excel models. To implement all these features, the hand-written effective code in NopORM is only about 10,000 lines. Likewise, in the Nop platform, we implemented a conditional wiring NopIoC container in around 4,000 lines, a distributed RPC framework supporting gray release in around 3,000 lines, and the NopReport Chinese-style reporting engine that uses Excel as the designer in around 3,000 lines. For details, see:

* [What Kind of ORM Engine Does a Low-Code Platform Need](https://zhuanlan.zhihu.com/p/543252423)
* [If We Rewrite SpringBoot, What Different Choices Would We Make](https://zhuanlan.zhihu.com/p/579847124)
* [An Open-Source Chinese-Style Reporting Engine Using Excel as Designer: NopReport](https://zhuanlan.zhihu.com/p/620250740)
* [Distributed RPC Framework in a Low-Code Platform](https://zhuanlan.zhihu.com/p/631686718)

One important reason we can rewrite these frameworks with small code bases is that they share many general mechanisms of the Nop platform. Any non-trivial underlying framework effectively provides a DSL—e.g., Hibernate’s hbm model files, Spring’s beans.xml object definition files, Report model files, interface definition files used by RPC, etc. The Nop platform provides a suite of technical supports for developing custom DSLs, avoiding having each framework implement model parsing, loading, and transformation on its own. Meanwhile, the Nop platform offers highly customizable expression and template engines, avoiding the need for each framework to reimplement similar scripting engines. Another point: every framework uses an IoC container to achieve dynamic wiring, avoiding reimplementing plugin extension mechanisms.

The Nop platform unifies the DSL descriptions used across underlying frameworks, so we can use a unified XDef metamodel definition language to define different DSLs; this lets us provide a unified IDE plugin for programming support across DSLs and achieve seamless embedding between multiple DSLs. In the future, we will also provide a unified visual designer generator that automatically generates visual design tools for DSLs based on the XDef metamodel. Leveraging XDSL’s built-in Delta customization mechanisms, we can achieve deep customization without modifying the base product’s source code. For details, see:

* [XDSL: General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300)
* [How to Achieve Custom Development Without Modifying Base Product Source Code](https://zhuanlan.zhihu.com/p/628770810)

### If we don’t rewrite underlying frameworks, how do we introduce a Delta mechanism?

The Nop platform rewrites underlying frameworks mainly to simplify programming and improve performance. If you stick with traditional open-source frameworks, you can still introduce a Delta mechanism; it will just be less convenient. In fact, implementing a delta-ized virtual file system and a Delta merge algorithm on top of JSON requires only a few thousand lines of code.

If you want to introduce Reversible Computation into a system at minimal cost, you can use two approaches:

#### 1. Use the Nop platform as an incremental code generator

The Nop platform’s code generator can be integrated into the Maven packaging tool; when executing mvn package it automatically runs all code generation scripts under the precompile and postcompile directories. The generated code can run independently of the Nop platform; you do not need to change the original runtime mode or add new runtime dependencies.

The Nop platform supports programmers defining their own domain models, and supports extending the platform’s built-in data models and API models. Moreover, such extensions are implemented by Delta customization, and adding model attributes does not require modifying Nop platform code. Some people already use the Nop platform’s code generator to generate model files needed by other low-code platforms.

The Nop platform’s code generator adopts a series of innovative designs, and its implementation and functionality are essentially different from common scaffold-style code generators. For details, see [A Data-Driven Delta-ized Code Generator](https://zhuanlan.zhihu.com/p/540022264)

Beyond integrating into Maven, we can also use the command line to perform continuous code generation.

```
java -jar nop-cli.jar run tasks/gen-web.xrun -t=1000
```

```xml
<!-- gen-web.xrun -->
<c:script><![CDATA[
    import io.nop.core.resource.component.ResourceComponentManager;
    import io.nop.core.resource.VirtualFileSystem;
    import io.nop.codegen.XCodeGenerator;
    import io.nop.xlang.xmeta.SchemaLoader;
    import io.nop.commons.util.FileHelper;

    assign("metaDir","/meta/test");

    let path = FileHelper.getRelativeFileUrl("./target/gen");
    let codegen = new XCodeGenerator('/nop/test/meta-web',path);
    codegen = codegen.withDependsCache();
    codegen.execute("/",$scope);
]]></c:script>
```

The above example indicates we execute the gen-web.xrun task every second, whose specific content is: for all meta files under the virtual path /meta/test, apply code generation templates under the virtual path /nop/test/meta-web, and save generated code to the target/gen directory. The code generator is set with withDependsCache, so each time it runs it checks whether the model files used by the code generator have changed; only upon changes will it regenerate; otherwise it skips. For example, if my.page.json is generated from the NopAuthUser.xmeta model and the web.xlib component library, then only when NopAuthUser.xmeta or web.xlib changes will it regenerate my.page.json.

This dependency-tracking mechanism is similar to the built-in hot-reload feature of the Vite front-end bundler: when it detects source changes, it automatically reloads and pushes updates to the browser.

#### 2. Use the Nop platform’s unified model loader

The Delta customization mechanism provided by the Nop platform is mainly used for dynamically generating and assembling various model files, and does not touch runtime framework knowledge. Therefore, it can naturally integrate with any well-designed runtime framework. The specific integration strategy is to replace the original function call that loads model files with a call to the unified model loader in the Nop platform.

```javascript
 Model = readModelFromJson("my.model.json");
 replaced with
 Model = (Model)ResourceComponentManager.instance()
                  .loadComponentModel("my.model.json");
```

ResourceComponentManager caches parsed results of model files and automatically tracks all model dependencies discovered during parsing. When a dependent model changes, it automatically invalidates all cached parsing results that depend on it.

The Reversible Computation theory’s Y = F(X) + Delta computation pattern can be encapsulated into an abstract Loader interface, which greatly reduces the cost of introducing Reversible Computation into third-party systems. For details, see [Low-Code Platform Design from the Perspective of Tensor Product](https://zhuanlan.zhihu.com/p/531474176)

In the Nop platform, our integration with Baidu’s AMIS framework uses this approach.

```yaml
type: page
x:gen-extends: |
   <web:GenPage view="MyEntity.view.xml" page="crud" />
body:
   - type: form
     "x:extends": "add.form.yaml"
     api:
       url: "/test/my-action"
```

By using ResourceComponentManager to load AMIS page files, we introduced delta decomposition/combination mechanisms such as `x:extends` and `x:gen-extends`, enabled using the XPL template language to dynamically generate JSON page content, allowed reusing already defined subpages via `x:extends` and fine-tuning them, and supported adding same-name files under the Delta directory to override existing system model files.

All JSON, YAML, or XML model files can directly use the Nop platform’s unified model loader for delta decomposition and merging.

Through the unified model loader, we can also easily transform model files in the original system into output template files. For example, by defining some special expression rules on top of Word files, we can directly convert Word files into XPL template language files for dynamic Word document generation. For details, see [How to Implement a Visual Word Template Similar to poi-tl in 800 Lines of Code](https://zhuanlan.zhihu.com/p/537439335).

## IV. What fundamental innovation does Delta have over Scala Traits?

In [the previous article](https://zhuanlan.zhihu.com/p/632876361) I clearly pointed out that Scala’s Traits can be seen as a way to define certain Deltas in class space. Some readers may wonder: is Delta in Reversible Computation essentially just a subtype definition problem and not particularly innovative?

Many programmers have only been exposed to the object-oriented discourse since they began learning to program, so they tend to equate writing code with creating classes, properties, and methods. Including when studying how ChatGPT generates business code, many people’s first thought is how to get GPT to decompose problems into multiple classes and then generate code for each class.

We must make clear that while Traits depend on the concept of class, class is not the best description for all logical structures. In Section I, I already pointed out that the same structure can be expressed in different model spaces, and each model space has its own delta definitions unique to that space. For example, a function can always be expressed in the general model spaces of binary bits and source code lines. Class space is essentially also a general model space: all logic implemented using object-oriented techniques has an expression in class space, and so can be Delta-corrected via class-space deltas.

Reversible Computation is not limited to any specific model space. As a theory, it first provides a unified theoretical explanation for many scattered practices and points out that the complete technical route should be Y = F(X) + Delta, organically combining [Generative Programming](https://zhuanlan.zhihu.com/p/399035868), delta-oriented programming, and multi-stage programming. From the perspective of Reversible Computation, innovative practices emerging in recent years—such as Docker in virtualization, React in front-end, and Kustomize in cloud computing—can be seen as concrete applications of Reversible Computation. In this way, we can identify commonalities among these technologies and abstract a unified technical architecture to promote them to more application domains.

Reversible Computation points out that we can establish more flexible domain model spaces and express deltas of logical structures in more stable domain coordinate systems (similar to using intrinsic coordinates in physics). Once domain coordinate systems are introduced, we find that the type system provides an incomplete coordinate system; some delta problems cannot be precisely defined in the type system. Since the concept of type starts from multiple instances sharing the same type, using it for location coordinates can easily introduce ambiguity.

Reversible Computation views the class concept at the structural level as a Map structure: class attributes correspond structurally to retrieving values by key from the Map. Class inheritance can be seen as an overlay relationship between Maps. Reversible Computation generalizes this overlay relationship to Tree structures and adds the concept of inverse elements. A Tree is structurally more complex than a Map; Tree = Map + Nested.

> Any operation applicable to Tree structures is necessarily applicable to Maps, so a Tree can be viewed as a generalization of a Map.

Adding inverse elements expands the problem’s solution space; some previously difficult problems then have general solutions in this new space. For example, Scala Traits do not support “cancel” operations: once object A mixes in Trait B, you cannot undo it via Scala syntax; you must manually modify source code. This makes system-level coarse-grained software reuse difficult. There is a fundamental difference between customizing by adding Deltas without modifying X and re-decomposing X into multiple components and reassembling: decomposition and assembly incur costs, and manual operations may introduce accidental errors. It’s like disassembling a precision watch and reassembling it, only to find a screw was tightened incorrectly.

After establishing a domain coordinate system, Delta can transcend traditional code-structure barriers and act directly at specified domain coordinates—like establishing ubiquitous portals—so we can teleport logical deltas to specified locations.

> In the magical world, you don’t have to use the door to leave the room—you can walk through walls!

### Why emphasize tree structures rather than graph structures

Tree structures have many advantages. First, they unify relative and absolute coordinates: from the root, there is only one unique path to any node, which can serve as the node’s absolute coordinate. On the other hand, within any subtree, every node has a unique path within the subtree, which can serve as its relative coordinate. Given a node’s relative coordinate and the subtree root’s absolute coordinate, we can easily compute the node’s absolute coordinate (by simple concatenation).

Another advantage of Tree structures is that they are easy to govern: every parent node can act as a control point, and some shared properties and operations can automatically propagate downward to each child node.

Conversely, for graph structures, if we choose a primary observation direction—selecting a fixed node as the root—then we can naturally convert a graph into a tree. For example, in Linux, everything is a file, and many logical relationships are incorporated into the file tree. But with file links, the file system essentially expresses graph structures. The so-called tree arises only because we chose an observation direction on the graph.

## V. After Delta-izing the system, do versioning mechanisms still matter?

Git versions are coarse-grained, non-structured deltas: they can name a large update and handle general program source code. Delta, by contrast, we want it to be highly structured and semantic; if all languages including Java implement suitable Delta-ization, they can play roles similar to Git.

On the other hand, many times we want to directly modify the Delta implementation itself without preserving semantic boundaries; then managing Delta code via Git’s non-structured methods is very suitable.

## VI. Is XML too complex? Can we consider JSON or even YAML?

Many programmers have never designed an XML-based DSL themselves. They’ve just heard legends from industry veterans about how XML from ancient times was eliminated by newcomers, forming a stereotype that XML is too verbose, suitable only for machine-to-machine communication and not for human-machine interaction. But this is a mistaken prejudice, stemming from the wrong use of XML by XML fundamentalism, and a series of international XML specifications that fan the flames of those wrong practices.

I have explained this in detail in a previous article: [Necessary Conditions for Using GPT in Complex Code Production](https://zhuanlan.zhihu.com/p/632876916).

In the Nop platform, we provide automatic bidirectional conversion between XML and JSON/YAML; the same DSL has both XML and JSON representations.

## VII. How to achieve reversible conversion across formats that are not strictly equivalent?

Some programmers have practiced conversions between DSL descriptions—for example, automatically converting a high-level DSL to a lower-level DSL—and often find it very difficult or even impossible to achieve reversible conversion. Crossing complexity levels or system boundaries often loses detail, leading to only approximate equivalence before and after conversion, not strict equivalence.

Some hope AI’s powerful guessing ability can reconstruct original information from residuals automatically, but this is inherently imprecise and error-prone.

$$
A \approx F(B), G(A) \approx B
$$

To turn the two equations from approximately equal to equal, Reversible Computation theory recommends adding Delta on both sides.

```
  A + dA = F(B + dB),  G(A + dA) = B + dB
```

That is, to satisfy reversible conversion requirements, each abstraction layer should provide a built-in mechanism to store extension information, accommodating some extra information currently unused.

> As the saying goes: there’s a kind of usefulness called the “use of the useless.” Some optional features support a gray design space, allowing unexpected evolutions to occur within it.

## VIII. How to understand Reversible Computation from the perspective of category theory

Among abstract mathematics, category theory is arguably the most abstract, and typical programmers have not received strong training in abstract math. Their understanding of category theory is often limited to type systems, even tied to syntax features of certain functional languages. But category theory is in essence very simple and does not involve type systems. Reversible Computation and many practices in the Nop platform can be understood within category theory’s framework.

A category is:

1. Some objects (“points”) and arrows (morphisms) between points,
2. Arrows can be composed to form new arrows, and composition is associative,
3. Each point has an identity arrow.

That is, as long as we can map concepts to points and arrows, we can naturally form a category.

For example, making a lemon pie can form a category.

![](reversible/make-lemon-pie.png)

Similarly, a database schema definition also forms a category.
![](reversible/database-schema.png)

Note that not every directed graph is a category, because categories require an identity arrow at every point and composability with associative composition. More rigorously, from any directed graph we can construct a category via Free Construction. Thus, the category constructed from the graph below with only two arrows actually contains 6 arrows.

![](reversible/free-category.png)

We must add 3 identity arrows at v1, v2, v3, and the composition of f1 and f2 must be defined as a new arrow, so there are 6 arrows altogether.

“Free” here means we add no new constraints; we only supplement the minimal elements required by category theory’s definitions. A non-free construction introduces constraints: e.g., stipulating that two paths with the same start and end points are equivalent.

![](reversible/commu-equations.png)

A simple example is adding business constraints to a database schema relationship graph:
![](reversible/my-schema.png)

* Every department’s secretary must work in that department,
* Every employee’s manager must be in the same department as the employee.

Adding equation constraints to the category easySchema yields a new category mySchema.

> All category diagrams above are from [Seven Sketches in Compositionality:
> An Invitation to Applied Category Theory](https://arxiv.org/pdf/1803.05316.pdf).

From the perspective of category theory, our current system can be viewed as a category formed by domain structures, and Delta customization acts at every domain coordinate, mapping each point in the domain structure to a new point, thereby transforming the overall domain structure into a new one. Therefore, Delta customization can be viewed as a functor between categories of domain structures.

A functor maps each point in category C to a point in category D, and each arrow in C to an arrow in D, while preserving associative composition of arrows.

Note the special nature of functors: they act on every point in category C and preserve certain structural relations in the mapping process.

In the Nop platform, we value solving problems in a general way, and this generality can be explained via category theory. For example, when parsing Excel model files, a common approach is to write a special parser for a specific Excel format to parse it into a specific Java object structure. In contrast, the Nop platform implements a general Excel parser that does not assume a specific Excel format; it allows relatively arbitrary Excel input (fields can be rearranged freely, arbitrarily complex nested fields are allowed), and without programming it can parse an Excel file into corresponding domain structure objects. From the category perspective, the Nop platform provides a functor from the Excel category to the domain object category, rather than a parser targeting a specific structure. In the other direction, for any domain object, without programming, the general report export mechanism can output it to an Excel file. This report functor and the Excel parsing functor can be seen as forming an adjoint pair.

## IX. What’s the use of these abstract theories? Can they help with GPT code generation?

First, Reversible Computation theory is itself very useful: it solves system-level coarse-grained software reuse. For example, if the system’s base layer follows Reversible Computation principles from the start, we would not need to invent Kustomize; Spring’s container implementation could be greatly simplified. If a bank core application product follows Reversible Computation principles, then during customization for different clients it need not modify the base product’s source code—significantly reducing the burden of maintaining multiple versions of base product code. Revolutionarily, Docker can be viewed, at the abstract structural level, as a standard application of Reversible Computation theory; Docker-like construction should be promoted to more technical areas.

Second, starting from Reversible Computation, we can offer the following suggestions for GPT code generation:

1. Code generation does not mean you must split code into classes and methods. Class space is just one general model space; we can use other domain model spaces to express logic.
2. If GPT can accurately generate code, it must be able to understand metamodels and delta models. Metamodels help AI precisely grasp model constraints with minimal input. Delta models enable incremental development.
3. During training, we can intentionally use metamodels and delta models, thereby rapidly deriving a large number of intrinsically consistent training samples from a small sample set. Delta-ized training is akin to gradient descent in numerical spaces.

The low-code platform NopPlatform, designed based on Reversible Computation theory, is open source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Introduction and Q&A on Reversible Computation Principles and the Nop Platform_Bilibili](https://www.bilibili.com/video/BV14u411T715/)

<!-- SOURCE_MD5:334c8922ae3b0c190e81a88e4e23d9ab-->
