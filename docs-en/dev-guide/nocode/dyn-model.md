# Online Dynamic Modeling

The No-code (NoCode) development paradigm requires that users be able to adjust data models, process models, and other model objects online, without going through compilation, packaging, and deployment, and immediately see the runtime results after model adjustments.

Most Nop Platform demos currently design models during the development phase and then generate program source code via code generation. Many therefore mistakenly assume that Nop's technical approach is not suited for online dynamic modeling.

In this article, I will introduce dynamic model management in the Nop Platform, drawing on the implementation of the NopDyn module.

## I. Decoupling via DSL

To support runtime model design, traditional solutions tightly couple design tools and runtime engines. This leads to entangled logic between the two, making both design improvements and performance optimizations difficult.

$$
Design\text{-}time \Longrightarrow Runtime
$$

The Nop Platform emphasizes DSL-first rather than visual-design-first. (See [Design Principles of DSL from the Perspective of Reversible Computation](https://zhuanlan.zhihu.com/p/646144092))

$$
Design\text{-}time \overset {Code\ Generation} \Longrightarrow  Virtual\ File\ System \overset {Universal\ Loading} \Longrightarrow Runtime
$$

In the Nop Platform, design tools and the runtime engine are decoupled via DSL.

1. The output artifact of the design tool is a DSL model file

2. The input to the runtime engine is a DSL model file

3. **DSL model files are uniformly managed by the Virtual File System**

With the abstraction of the Virtual File System, the actual storage of DSL can be files on disk, a record in a database (or a set of associated records), or a text buffer in memory. The process of saving a model as a DSL file and then parsing it to obtain runtime model objects can be regarded as **an asymmetric extension of the JSON serialization process**: the text produced by serializing the design-time model is DSL, and **the deserialized runtime model need not match the design-time model**; it can be a model structure optimized for runtime.

$$
Model \Longrightarrow  JSON  \Longrightarrow Model \\
DesignModel \Longrightarrow DSL \Longrightarrow RuntimeModel
$$

Furthermore, **the process from design-time model to DSL is not unique**. We can propose multiple forms of design-time models tailored to different business scenarios as long as they can generate the required DSL text via a code generation process. For example, the underlying workflow engine in the Nop Platform is not customized for approval scenarios—it does not have built-in concepts like parallel or serial approvals. However, we can provide a customized visual designer similar to DingTalk’s workflow that produces a tree-structured model object, and then automatically compile-time transform it to generate the underlying graph-structured workflow DSL.

In other words, moving from DesignModel to DSL can be a code generation process implemented via a code generator. Mentioning code generation often gives the impression that it exists only in the development phase and is unsuitable for dynamic model design. But combined with the Virtual File System abstraction, code generation is essentially no different from JSON serialization: both generate a segment of text in memory.

The Nop Platform comes with a powerful template-driven code generator, enabling NoCode and ProCode to share part of the code generation templates. See [Data-driven Delta Code Generator](https://zhuanlan.zhihu.com/p/540022264).

In another direction, **the process of parsing DSL into model objects is standardized in the Nop Platform**: just define the XDef meta-model corresponding to the DSL to automatically implement model object parsing. See [A Unified Meta-model Definition Language as a Replacement for XSD: XDef](https://zhuanlan.zhihu.com/p/652191061).

In the Nop Platform, you can load any model object by virtual file path as follows:

```javascript
DslModel = ResourceComponentManager.instance().loadComponentModel(modelPath)
```

The loading process automatically implements model caching and tracks dependencies between model files: when a model file is modified, all model caches that depend on it are automatically invalidated. What the unified model loader does is essentially similar to frontend bundlers like webpack or vite that support dependency tracking. For a detailed introduction, see the video [Unified Model Loader in Low-Code Platforms](https://www.bilibili.com/video/BV1rH4y117hd/).

## II. Universal Delta Mechanism

**Any dynamic update (extension) technology is essentially defining a Delta space and feasible structural composition operations within that space.** Upon reflection, what most people perceive as dynamic updates is just defining extension points on an already constructed structure and then inserting structures that conform to interface specifications into those extension points. The complexity of implementing dynamic, Delta-based updates lies in the following three points:

1. How should extension points be designed to satisfy unknown Delta update requirements?

2. How can externally introduced Delta structures be seamlessly integrated with the original structure?

3. How can we ensure runtime state consistency before and after Delta-based updates?

Beyond the above three points, we can ask ourselves: Since all business logic structures have potential dynamic update needs, **why should every specific structure customize its own Delta update scheme?** Can we resolve the above three technical challenges once and for all with a unified technical approach?

## Lessons from the Relational Model

To answer the above question, we can look at the rise of the relational model. Before relational databases dominated, data storage approaches were diverse and ad hoc. The reason the relational database model could standardize storage mechanisms is that it chose to step back and broaden the perspective. In the relational model, we abandon various natural associations between data and **focus entirely on atomic, non-redundant data**. After normalizing them, we then dynamically compute the final required data via various derived operations. In particular, all original strong pointer-based associations between data are broken; all relations are decomposed (hence **“no relations” in a relational database**), retaining only association fields like id and ref_id. At runtime (when data is actually needed), we use JOIN statements, **compute on-the-fly** (via index lookups or table scans), and **reconstruct relationships between data** in memory. To emphasize again, the power of the relational model lies in introducing just-in-time computation into the standardized structured data access process—reconstructing relations at runtime; and the ORM engine’s role is to pull back the relations decomposed by the relational model, embedding them in the object model to avoid repeatedly expressing primary–foreign key associations in business logic.

To achieve unambiguous, standardized Delta updates, Reversible Computation makes the same choice as the relational model: step back and operate at the standardized, non-redundant structural layer, rather than the type-differentiated object layer. The Nop Platform’s Delta merge mechanism is uniformly defined at the XDSL layer. It is a standardized information structure expressible in XML or JSON and constrained by the XDefinition meta-model.

|Relational Model Theory|Reversible Computation Theory|
|---|---|
|Schema Definition|XDefinition Meta-model Specification|
|Non-redundant tabular data|Non-redundant tree-structured information: XDSL|
|On standardized data structures, just-in-time computation: SQL|Compile-time computation on the generic XNode data structure: XLang|

Nop’s XLang develops a comprehensive toolset for defining, parsing, transforming, and analyzing tree structures. The reason Nop can implement standardized, business-agnostic Delta merge operations is that it defines Delta operations at the generic XDSL structural layer rather than at the model object layer produced by parsing XDSL.

Recall the model loading process in the Nop Platform:

```javascript
DslModel = ResourceComponentManager.instance().loadComponentModel(modelPath)
```

In a functional abstraction, it corresponds to:

```
   modelPath => XDSL => DslModel 
```

XDSL is a non-redundant, standardized, structured information format oriented toward storage and transmission, whereas DslModel is a compiled, optimized format rich in derived data and oriented toward external consumption. They are linked via a dynamic, reactive computation: once a DSL file is modified, DslModel is automatically re-parsed.

## Lessons from Microservice Architecture

How do we ensure runtime state consistency before and after Delta-based updates? For this, we can look at solutions from microservice architecture.

Microservices achieve dynamic rolling updates by relying on stateless design. Traditional object-oriented practice encapsulates functions and the data they operate on into objects. Functions and data then get entangled, forming spaghetti-like, intertwined structures that are hard to decompose and thus hard to update locally. Ideally, we want a layered slice structure that can be stacked or trimmed one layer at a time.
The essence of stateless design is to decouple logic processing from the runtime state space. Runtime dynamic state data is pushed out of the static pure logic structure—part becomes passed parameters, and part becomes persisted data in shared storage. As a result, the microservice’s runtime structure no longer explicitly contains state data, so its dynamic updates do not get entangled with state migration and become an independently solvable problem.

When accessing a microservice, we first talk to the service registry, dynamically find the service instance by service name, then send input parameters. The service instance loads the persistent state data based on the inputs. In a functional abstraction, the process corresponds to:

```
  serviceName => Service => input => state => Result
```

Similarly, in the Nop Platform, all model processing adopts the same logical structure:

```
  modelPath => XDSL => DslModel => arguments => scope => Result
```

Like microservices, dynamic updates to DSL models can be independent of structural migrations in the state space.

## III. DSL

<!-- SOURCE_MD5:a8d345002083cf873da65c7a4dcda88c-->
