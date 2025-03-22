# Online Dynamic Modeling

The **NoCode** development mode requires users to adjust data models, process models, and other model objects online without going through the compilation, packaging, or deployment process. Immediately after adjustments, users can see the updated runtime results.

The Nop platform's current demonstration is primarily conducted during the design phase, where models are designed and then generated into program source code via code generation. As a result, many people mistakenly believe that the Nop platform's technical solution does not apply to online dynamic modeling.

In this document, I will discuss the dynamic model management aspects of the Nop platform by combining its **NopDyn** module implementation.

## 1. Achieving Decoupling via DSL

To support dynamic design during runtime, traditional solutions tightly couple design tools with runtime engines, leading to intertwined logic that restricts both design and performance improvements. This coupling creates numerous limitations.

In the Nop platform, we prioritize **DSL** over visualization design. For details, see [Key Points in DSL Design from Reversible Computation](https://zhuanlan.zhihu.com/p/646144092).

The following equation demonstrates this decoupling:

```
State --> Code Generation --> Virtual File System --> Load --> Runtime State
```

In the Nop platform, design tools and runtime engines are decoupled via DSL. 

1. Design tool output is a **DSL model file**.
2. Runtime engine input is a **DSL model file**.
3. **DSL model files** are managed by the virtual file system.

Thanks to the abstraction provided by the virtual file system, DSL can be stored as files on disk, database records (or groups of related records), or in-memory text caches. The process of saving the model as a DSL file and then parsing it to create runtime models is akin to **non-symmetric extension**: during serialization, the design-time model becomes a DSL text, while deserialization may result in a runtime model that differs from the design-time model.

```
Model --> Serialization --> DSL Text --> Model \\
DesignModel --> Serialization --> JSON --> Model \\
DesignModel --> Code Generation --> DSL --> RuntimeModel
```

Furthermore, **the process of generating the DSL from the DesignModel is not unique**. We can propose multiple forms of design models depending on the specific business scenario. For example, in the Nop platform's underlying workflow engine, there is no built-in approval concept, but we can provide a customizable, DingDing-like workflow designer that generates tree-shaped model objects.

This means that from **DesignModel** to **DSL**, the generation process can be code generation. While many associate code generation with the development phase and thus assume it's not suitable for dynamic modeling, combining virtual file system abstraction, code generation, and JSON serialization results in processes that are essentially memory-text transformations: both generate text representations of runtime models.

The Nop platform is equipped with a powerful template-driven code generator that supports **NoCode** and **ProCode** development by sharing parts of the code templates. For details, see [Delta Mechanism for Code Generation](https://zhuanlan.zhihu.com/p/540022264).

From another perspective, **the parsing of DSL into model objects is standardized** in the Nop platform: define XDef meta-models and automatically parse them. For details, see [Replacement of XSD with XDef Meta-Modeling](https://zhuanlan.zhihu.com/p/652191061).

In the Nop platform, you can load any model object using the following method:

```javascript
DslModel = ResourceComponentManager.instance().loadComponentModel(modelPath)
```

During loading, the system automatically implements model caching and tracks dependencies between model files. When a model file is modified, all dependent cached models are invalidated. This behavior is similar to tools like webpack or vite, which track dependencies. For a detailed video explanation, see [Unified Model Loader in Low-Code Platforms](https://www.bilibili.com/video/BV1rH4y117hd/).

## 2. General Delta Mechanism

Any dynamic update (extension) technology fundamentally defines a delta space and enables structural composition operations within this space.

Carefully thinking about this, general people understand dynamic updates as modifying existing structures by defining extension points and inserting compliant structures into these points. This process is akin to **delta computation**: extending existing systems without fully reconstructing them each time.

The complexity of delta-based updates lies in three key aspects:

1. **Delta Space Definition**: Defining what constitutes a "change" (delta) in the system.
2. **Compositional Updates**: Applying changes through modular extensions rather than full system rebuilds.
3. **Incremental Validity**: Ensuring that incremental updates maintain system integrity.

The following equation illustrates this:

```
State --> Delta Space --> Extension Operation --> New State
```

In the Nop platform, we have successfully implemented a delta-based approach for managing dynamic models. This solution avoids the limitations of traditional approaches where runtime and design-time tools are tightly coupled. For more details, see [Delta Mechanism in Model Management](https://zhuanlan.zhihu.com/p/540022264).


1. How can extension points be designed to ensure that unknown incremental update requirements are met?
2. How can external introduced incremental structures be seamlessly integrated with existing structures?
3. How can incremental updates maintain consistency between the state before and after the update?

Additionally, we can ask ourselves another question: Since all business logic structures inherently have potential dynamic update requirements, **why do each specific structure require its own tailored incremental update solution**? Can a unified technical solution address the aforementioned three technical challenges simultaneously?

---


## Relationship Model's Success Stories

To address the above questions, we can look at the history of the relationship model. Before the rise of relational databases, data storage methods were as diverse as they come—from flat files to hierarchical databases and everything in between. However, it was the advent of the relational database model that standardized data storage mechanisms. In the relational model, we abandoned naturally occurring relationships between data entities and instead focused on atomic, non-redundant data. Through standard operations and derived functions, we derive the required data dynamically. Notably, all relationships were severed (known as **"relationships in a relational database are non-existent"**), leaving only identifiers like `id` and `ref_id` to maintain associations. At runtime, joins (`JOIN` statements) are used to re-establish these relationships through either index lookups or table scans, which perform immediate computations (indexed searches or full-table scans). Relationships are then rebuilt in memory.

The relationship model's strength lies in its ability to rely on immediate computations (index-based lookups or table scans) for data retrieval and re-relationships. ORMs (Object-Relational Mappings) play a crucial role here by translating the relational model back into objects, effectively managing the separation of concerns between the domain model and the storage layer.

To enable non-ambiguous, standardized incremental updates and reversible computations, the relationship model follows the same principles: it opts for operations on standardized, non-redundant data layers rather than divergent object layers. This avoids the pitfalls of object encapsulation, where objects often end up tightly coupled, making partial updates or decoupled reasoning difficult.

The relational approach achieves this by standardizing data storage and retrieval through a common set of operations (e.g., `SELECT`, `INSERT`, `UPDATE`, `DELETE`), eliminating unnecessary complexity. It's worth noting that while some relationships are lost in translation, modern databases and ORMs reconstruct them dynamically using index-based lookups or table scans.

---


## Tables Comparison

| **Relationship Model Theory** | **Reversible Computation Theory** |
|---------------------------------|---------------------------------|
| Schema Definition                | XDefinition Meta-model           |
| Non-redundant Table Data       | No Information Redundancy: XDSL |
| Standardized Data Structure    | Standardized Data Structure: XDSL |
| Immediate Computation (e.g., SQL)| Immediate Computation (e.g., SQL) |

---



```javascript
DslModel = ResourceComponentManager.instance().loadComponentModel(modelPath);
```

If we adopt a functional programming language's abstract representation, it corresponds to:

```plaintext
modelPath => XDSL => DslModel
```

Here, **XDSL** is a redundant-free, standardized, data storage and transmission-oriented structured information representation. In contrast, **DslModel** is an optimized, derived-data-focused, externally usable information representation. These two are interconnected through dynamic, responsive computations: once the DSL file is modified, the DslModel automatically re-parses and recompiles.

---



When it comes to incremental updates ensuring runtime state consistency, microservices architecture offers a solution. Unlike traditional monolithic architectures, where partial updates often lead to tight coupling and state management complexities, microservices decouple the runtime state from the application logic.

In a microservices setup:

1. **Stateless by Design**: Microservices are designed to be stateless, storing all persistent data outside the service. This avoids the need for complex synchronization mechanisms.
2. **Dynamic Rollbacks**: If a service fails, only its output is affected, not the entire system's state.
3. **Independent Services**: Each service operates independently, making it easier to roll out updates without affecting the overall system.

When updating a microservice:

1. **Input/Output Coupling**: Microservices receive inputs, process them, and produce outputs. The internal implementation remains hidden, simplifying partial updates.
2. **Event Driven**: Updates can be triggered by events, allowing services to respond without direct communication dependencies.
3. **Shared Data through Events**: Persistent data is stored outside the service, so state consistency is maintained through event handling.

The key advantage lies in the decoupling of runtime state from business logic. Microservices are like a "black box" that transforms inputs into outputs, making it easier to manage updates and maintain system-wide consistency.

---



```javascript
DslModel = ResourceComponentManager.instance().loadComponentModel(modelPath);
```

If we use a functional programming language's abstract representation, this corresponds to:

```plaintext
modelPath => XDSL => DslModel
```

Here, **XDSL** is a redundant-free, standardized, data storage and transmission-oriented structured information representation. In contrast, **DslModel** is an optimized, derived-data-focused, externally usable information representation. These two are interconnected through dynamic, responsive computations: once the DSL file is modified, the DslModel automatically re-parses and recompiles.

---



Incremental updates without runtime state inconsistencies are a hallmark of microservices architecture. Unlike traditional approaches where partial updates often lead to tightly coupled systems and complex state management, microservices decouple runtime states from business logic.

Key points:

1. **Stateless by Design**: Services store no state; data persistence is handled externally.
2. **Dynamic Rollbacks**: Failures in one service do not affect others.
3. **Independent Operations**: Services operate independently, simplifying updates and fault isolation.

When updating a microservice:
- Inputs are processed into outputs.
- Internal implementations remain hidden.
- Updates are event-driven, allowing services to respond without direct communication dependencies.

The decoupling of runtime state from business logic is the architecture's greatest strength. Microservices function like black boxes that transform inputs into outputs, facilitating consistent system-wide updates and maintaining state integrity through event handling.

---



| Relationship Model Theory | Reversible Computation Theory |
|---------------------------|-------------------------------|
| Schema Definition          | XDefinition Meta-model         |
| Non-redundant Table Data  | No Information Redundancy: XDSL |
| Standardized Data Structure| Standardized Data Structure: XDSL |
| Immediate Computation (e.g., SQL)| Immediate Computation (e.g., SQL) |

---


