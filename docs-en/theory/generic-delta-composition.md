
# General Delta Quantization Mechanism

**Any dynamic update (extension) technique essentially defines a Delta space and the feasible structural composition operations within that space.** On reflection, what most people call dynamic updates is nothing more than defining extension points on an already constructed structure and then inserting structures that conform to interface specifications into those points. The complexity of implementing dynamic Delta updates lies in the following three aspects:

1. How should extension points be designed to satisfy unknown Delta update requirements?

2. How can externally introduced Delta structures be seamlessly integrated with the original structure?

3. How can we ensure runtime state consistency before and after Delta updates?

Beyond the above three points, we can also ask ourselves: since all business logic structures have potential dynamic update needs, **why must each specific structure customize its own Delta update scheme**? Can a unified technical approach solve the three technical challenges in one fell swoop?

## Lessons from the Relational Model

To answer the questions above, we can look to the history of the relational model’s rise. Before relational databases came to dominate, data storage approaches were all over the map, with many competing techniques. The reason the relational database model was able to standardize the storage mechanism is that it chose to step back and see the bigger picture. In the relational model, we abandon the myriad natural associations among data, **focusing entirely on atomic, non-redundant data**, handling them according to norms, and then dynamically computing the final required data through various derived operations. In particular, all pointer-based strong associations among data are broken, all relationships are decomposed (the so-called **“no relationships” in relational databases**), retaining only association fields like id and ref_id. At runtime (when data are actually needed), we use join statements, **through just-in-time computation** (index lookups or table scans), to **reconstruct relationships among data** in memory. To reiterate, the power of the relational model lies in introducing just-in-time computation into the access of standardized structural data, reconstructing relationships at runtime, while ORM engines pick back up the relationships decomposed by the relational model and embed them in object models, avoiding the frequent repetition of primary/foreign-key associations in business expressions.

To achieve unambiguous, standardized Delta updates, Reversible Computation theory takes the same step back as the relational model: operate at the standardized, non-redundant structural layer, rather than at the type-differentiated object layer. In the Nop platform, the Delta merge mechanism is uniformly defined at the XDSL layer. XDSL is a standardized information structure that can be expressed in XML or JSON, with structural constraints enforced by the XDefinition metamodel.

|Relational Model Theory|Reversible Computation Theory|
|---|---|
|Schema definition|XDefinition metamodel specification|
|Non-redundant tabular data|Tree-structured information without redundancy: XDSL|
|Just-in-time computation on standardized data structures: SQL|Compile-time computation on the general XNode data structure: XLang|

In the Nop platform, XLang has developed a comprehensive toolkit for the definition, parsing, transformation, and analysis of Tree structures. The reason the Nop platform can implement standardized, business-agnostic Delta merge operations is that it defines Delta operations at the generic XDSL structural layer, rather than at the model object layer parsed from XDSL.

Review the model loading process in the Nop platform:

```javascript
DslModel = ResourceComponentManager.instance().loadComponentModel(modelPath)
```

If expressed in the abstraction of a functional language, it corresponds to:

```
   modelPath => XDSL => DslModel 
```

XDSL is a non-redundant, standardized structured information representation oriented toward storage and transmission, whereas DslModel is a compiled, optimized representation for external use that provides extensive derived data. The two are linked through a dynamic, reactive computation process: once a DSL file is modified, DslModel is automatically re-parsed.

## Lessons from Microservice Architecture

How do we ensure runtime state consistency before and after Delta updates? For this, we can refer to solutions from microservice architecture.

Microservice architecture achieves dynamic rolling updates thanks to stateless design. The traditional object-oriented approach encapsulates functions and the data they process into so-called objects; ultimately, functions and data become entangled, forming a spaghetti-like tangle that is hard to decompose, making local updates naturally difficult. Ideally, we want layered slices like a mille-feuille that can be stacked up or shaved off one layer at a time.

The essence of stateless design is the decoupling of logical processing structures from the runtime state space. Runtime dynamic state is pushed out of static, pure logic processing structures: part becomes passed-in parameter data, and another part becomes persisted data stored in shared storage. In this way, the microservice’s runtime structure no longer explicitly contains state data, and its dynamic updates are no longer entangled with state-space data migration, becoming a problem that can be solved independently.

When accessing a microservice, we first access the service registry, dynamically look up and obtain a service instance by service name, then send input parameters to it. The service instance automatically loads persisted state data based on the inputs. In the abstraction of a functional language, the microservice processing flow corresponds to:

```
  serviceName => Service => input => state => Result
```

Similarly, in the Nop platform, the processing of all models follows the same logical structure:

```
  modelPath => XDSL => DslModel => arguments => scope => Result
```

As with microservices, dynamic updates of DSL models can likewise be independent of data migration in the state space.

<!-- SOURCE_MD5:b53b6c9868483cb1e5172cc5b2fa65c5-->
