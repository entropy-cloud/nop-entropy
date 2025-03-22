# How to evaluate the goodness of a framework technology?

An interesting question is: when a new framework technology appears, how do we evaluate its goodness? Since NopORM engine became open source last year, it has received some feedback. However, most people probably haven't understood the theoretical part of the NopORM engine, leading to widespread confusion about what specific advantages NopORM has compared to other ORM engines. In this article, I would like to discuss what objective standards can be used to evaluate a framework without relying on personal experience, preferences, or familiarity levels.

> Interestingly, some people never look at the theoretical part but still argue about the significant technological gaps between domestic and foreign software development in terms of practicality. This can only be described as a form of misleading them, akin to the "leaf public official" trick.

For the theoretical analysis of the NopORM engine, see [What ORM engine does a low-code platform need?(1)](https://zhuanlan.zhihu.com/p/543252423) and [What ORM engine does a low-code platform need?(2)](https://zhuanlan.zhihu.com/p/545063021).

## Simple evaluation criteria

Many people evaluate framework technologies using simple, even naive criteria, such as:

1. **Using XML, a technology that has been obsolete, as an information carrier: give it a bad review!**
   Evaluating a framework based on some localized technical performance is superficial and shortsighted. The essence of an innovative technology is to provide a new logic organization mechanism, and the specific form in which this mechanism is implemented as a surface-level carrier is a secondary concern.

2. **Are more people using it? Is the documentation detailed enough?**
   This is the most "pragmatist" approach. It evaluates whether the framework is easy to use for someone like me, who focuses on results, rather than assessing the framework's design itself.

3. **Is it convenient to use, and easy to understand?**
   This is essentially a subjective feeling rather than an objective criterion that can be measured. In different technical environments and backgrounds, people have varying technical preferences, leading to significant differences in individual perceptions.

4. **Is it lightweight and fast?**
   This reflects optimizations at the implementation level but may not represent the framework's conceptual design as a strength. A lightweight framework might be smaller because it includes fewer features, and its speed might result from fewer feature dependencies or even from using unconventional methods (e.g., extensive use of unstable, non-public APIs).

## Are there any objective evaluation criteria?

Software development, while highly practical, is not entirely dependent on accumulated experience and esoteric beliefs. From the perspective of computer science's fundamental principles—information theory—we can propose some objective evaluation criteria.

## 1. Can business development be independent of the framework?

Over the past twenty years, one of the most important advancements in software framework technology has been recognizing the neutral nature of frameworks (framework-agnostic). Business code development fundamentally expresses business information, which should theoretically be independent of any software framework or even any technical factors. **A framework's role is to help us express business information in the most natural and intuitive way while also meeting performance and other technical requirements.** The ideal framework is one that developers are unaware of when writing business code.

This approach has advantages: it prevents information leakage and contamination between the business domain and the technical domain, making business code testing, technical framework updates, or even framework changes extremely simple.

### POJO and lightweight frameworks

The concept of POJO (Plain Old Java Object) was first introduced by lightweight frameworks as a reaction against EJB, a heavy-weight container technology. Traditional frameworks often focus on providing specific features but inevitably introduce framework-specific objects and functions into business code, tightly coupling the implementation with the runtime environment or even specific versions of the framework. Lightweight frameworks were the first to promote POJOs, allowing business code to manipulate ordinary Java objects without any framework dependencies. For example, in Spring 1.0, a bean's implementation doesn't require knowledge of the Spring framework; it can be fully configured using beans.xml.

Under this perspective, the design of traditional SpringMVC frameworks, such as the following:

```javascript
  public void myMethod(HttpServletRequest request){
      ...
  }

  @RequestMapping("/test")
  public ModelAndView test(){
     ModelAndView mav=new ModelAndView("hello");
     mav.addObject("time", new Date());
     mav.getModel().put("name", "caoyc");

     return mav;
  }
```

is not suitable for modern web framework designs, which typically receive and return POJO objects without any framework-dependent traces, while annotations themselves are purely descriptive information.

```javascript
@PostMapping("/myMethod2")
ApiResponse<MyResponseBean> myMethod2(MyRequestBean requestBean) {
    ...
}
```

> Annotations can be introduced in a minimal, framework-agnostic manner to include the necessary information. For example, in the DAO layer, most ORM frameworks now support JPA annotations, allowing a single entity definition to be applicable across multiple ORM frameworks.

Relying on framework-specific interfaces is merely superficial. At its core, it causes business logic to become tightly coupled with a specific runtime environment through indirect dependencies via interfaces. For instance, it restricts code written in Controllers to handle Web requests only, preventing direct reuse in the binary RPC layer or as message queue handlers for processing functions.

In the Nop platform, the NopGraphQL framework normalizes request parameters into a JSON object without requiring traditional Web frameworks' multiple parameter transmission methods (e.g., via `param`, `restPath`, or `cookie`). This decouples business logic from the Web runtime environment, allowing the same service function to be directly registered as a Kafka message queue response handler or a batch processing file handler. Automation testing no longer requires mocking Web servers, significantly reducing testing costs. For example, an online account credit service can be configured without programming, creating a nightly batch process to handle file-based accounting automatically.

> Both Redux and Vuex frameworks fundamentally normalize actions into single-parameter functions acting on individual POJO objects.

### Virtual DOM and Hooks

In the front-end domain, increasing amounts of business logic are now expressed in framework-agnostic forms.

For example, the introduction of virtual DOM allows front-end frameworks to decouple from browser runtime environments, enabling technologies like React Native and various mini-programming frameworks. Virtual DOM is essentially a standard JavaScript object, with different runtimes capable of creating and translating the same virtual DOM structure.

The evolution of Hooks allows front-end interface logic to be expressed without being bound by component object forms. By introducing minimalistic Hooks assumptions, business logic can be abstracted into framework-agnostic pure logic functions. With such abstractions, Headless component libraries have become dominant and can be adapted across React/Vue/Angular and other frameworks.

> Traditionally, front-end programming has been tied to specific component frameworks, with code required as member properties and methods of Component classes. This coupling creates dependencies on specific component syntax and runtime environments.

## II. What does the framework automatically infer?

A framework possesses inherent superiority if it **more comprehensively leverages certain information and automatically derives extensive functionality compared to other available options**.

### Annotations vs. XML Configuration

In recent years, XML configuration has declined in favor of annotations. The most significant advantage of annotations over XML configuration is that they **align with the programmatic syntax structure**. In strongly typed languages, existing type information can be utilized, significantly reducing the amount of information that needs to be expressed.
Using XML configuration involves setting up basic object structures, which leads to repetitive work and synchronization issues between XML configuration structures and object structures, especially during code refactoring. Annotations, on the other hand, can leverage IDEs' built-in refactoring capabilities, while XML configurations often remain outside of refactoring tools.

Interestingly, in a model-driven, low-code environment, the situation changes again. When coding manually, code is the most reliable source of information, with all other information derived from it. **In model-driven scenarios, however, the model is the Ultimate Source of Truth**, with code merely representing one form of information derivation. When refactoring is needed, only the model needs to be modified, automatically triggering updates to related code and configurations. This eliminates the need for XML to repeatedly express the same information and to synchronize XML configuration structures with code.

XML is not without its merits; it has unique strengths that are amplified in model-driven scenarios within the Nop platform. Therefore, we adopt XML configuration as the primary approach, with annotations as a secondary supplement. I will elaborate on this in more detail later.

### ORM Automatic Inference

Hibernate, from its inception to peak dominance, established the fundamental shape of the ORM framework category (subsequent ORM frameworks are typically simplified versions of Hibernate). Its value lies in:

1. Automatically mapping database records to Java objects, converting field types, and generating entity primary keys.
2. Caching by primary key, ensuring that if two queries return the same entity object, the same Java object is always returned. This improves performance and, in a way, enhances transaction isolation levels: it enables a form of Repeatable Read effectiveness.


 3. Identify modifications to POJO properties through dirty checking and automatically generate insert/update statements without manually calling `dao.update(entity)`, enabling automatic utilization of the JDBC batch mechanism for performance optimization.

4. Utilize Spring's declarative transaction mechanism to implement automatic transaction submission and rollback upon failure.

5. Implement automatic lazy loading for associated objects.

6. Automatically infer multi-table association conditions based on foreign keys, such as generating a condition like `where a.b.c = 3` by leveraging the existing associations between tables, thereby automatically creating the necessary join conditions for multiple tables. If a single table's query is already implemented, modifying the field name to `a.b.c` will enable automatic multi-table joins.

The NopORM framework introduces further automatic inference:

1. Convert vertical tables into virtual horizontal tables by storing extended fields in a table like `(entityName, entityId, fieldName, fieldValue)`. While these can be used like native database fields, they also support queries and sorting on these fields using SQL syntax.

2. Automatically track changes to entity attributes, record modification logs, and integrate with automated testing frameworks for database-level recording and playback.

3. Combine with the NopGraphQL engine to automatically publish domain models as GraphQL services.

### Pitfalls of Automatic Inference

The ability to automatically infer information is underpinned by certain assumptions. If these assumptions do not align with actual scenarios, they may lead to unintended consequences. For example:

1. Hibernate allows for eager loading of association properties, but this does not mean every data request must load associated data, potentially leading to unnecessary performance overhead. The NopOrm framework opts to delay all association properties and uses the `BatchLoadQueue` mechanism during optimization.

2. Hibernate provides a `FlushMode` set to `auto`, allowing the framework to decide whether to automatically flush changes to the database. However, this mechanism often leads to unintended database operations, with minor code changes causing significant performance degradation. The NopOrm framework instead disables this automatic mechanism, requiring manual flushing.

## What Automatic Conversions Does the Framework Provide?

Here, "automatic conversion" refers to a transformation of information's core content without altering its essence, merely switching between different representation forms. While this is a specific case of information inference, its design is highly generic, making it worth highlighting separately.

The most typical example is JSON conversion in web frameworks: bidirectional conversion between Java objects and JSON text. Early web frameworks lacked standardized complex parameter encoding schemes, often requiring manual parsing of frontend-request parameters. Today, JSON serialization has evolved into a general-purpose structure conversion method independent of the web environment.

Since automatic bidirectional conversion preserves information quantity, it is generally unrelated to specific business logic. From a mathematical perspective, this means for every structure in space A, there exists an equivalent structure (or set thereof) in space B, and vice versa. This principle allows for the creation of more complex compound conversions:

```
  A <=> B <=> C
```

It's crucial to emphasize that these conversions are generic, meaning **every possible structure can be converted**. In contrast, manually written transformations for specific business scenarios often rely on ad hoc implementations, which cannot handle other scenarios automatically.

Based on the reversible computation principle, the Nop platform has incorporated this automatic conversion concept into its various aspects:

1. Implement automatic conversion between XML and JSON, enabling the use of multiple file formats (XML, JSON, YAML) for AMIS page coding (though AMIS itself supports only JSON format).

2. Implement bidirectional conversion between XML and domain objects by defining XDef meta-models, enabling automatic parsing, validation, debugging, and more.

3. Implement bidirectional conversion between Excel and domain objects, allowing the use of Excel-formatted model files to define all platform domain models without special parsing code. For example, an XML-based ORM model can automatically generate an Excel data model definition, or vice versa.

4. Implement bidirectional conversion between visual model editing and domain objects by defining them through simple descriptions. For instance, workflow designers and ORM designers can automatically derive domain models based on their definitions.

```
  YAML <=> JSON  <=> XML  <=> DomainObject <=> Excel <=> VisualModel
```

Compared to MyBatis, Hibernate, and Spring, which require manual implementation of model file parsing, Nop's approach allows for more efficient and maintainable development and visualization tools without the need for separate plugins or custom code.

```

Another type of bidirectional conversion isn't a complete information equivalent, and we can add some additional information during the conversion process. For example, submitted request data in the frontend can be automatically converted into database entity objects to save and modify main-subtable data. However, this conversion process requires validity checks and permission checks, as well as type conversion and format conversion operations. In the other direction, after extracting a database entity, it can be automatically converted into JSON data returned to the frontend. This process also requires permission checks and format conversions. The NopGraphQL engine introduces Meta objects to bridge information gaps, thereby automating CRUD operations on complex business objects.

```
JSON + Meta => Entity
Entity + Meta => JSON
```

### How to Use Related Information Outside the Framework?

Traditionally, framework design focuses only on its own functional characteristics and doesn't pay much attention to its position in external information networks or its interaction value. However, with the advancement of software intelligence, we aim to promote seamless data flow across all levels and components without any obstructions. In this case, it's essential to consider how the framework interacts with external information networks.

### Independent Model Information

A framework with a certain level of complexity will establish its own domain model and heavily use configurable model information internally, such as Hibernate's EntityModel and Spring's BeanDefinition model. However, in many frameworks, model information is only present in an internal form and tightly coupled with the framework's runtime, making it difficult for external systems to reuse these model信息 using simple methods.

Hibernate began progressively discontinuing hbm.xml configuration files starting from version 6.0, retaining only annotations as the entity model definition method. This can be seen as a step backward.

1. It confines the entity model defined by Hibernate within the framework itself. Without using Hibernate's internal implementations, it's difficult to obtain corresponding model信息. Additionally, model objects obtained through Hibernate's internal functions might not be purely descriptive information but could include other information related to Hibernate's runtime implementation.

2. If we choose to directly parse annotation信息 via reflection, we still depend on Java's built-in mechanisms and need to perform filtering and screening to exclude transient fields and unrelated annotations, etc. Essentially, we need to rediscover **model information** independently rather than obtaining a well-parsed and validated model object directly.

In contrast to Hibernate's approach, the Nop platform emphasizes the independence of model信息, storing it in XML files. This allows other languages or frameworks to use these model信息 without relying on the Nop platform at all. For example, code generators can directly read XML model files to generate frontend and backend codes, as well as documentation for Word or Excel.

> Model objects are serialized into model files, which necessarily follow a certain Schema structure. This can be viewed as a specific syntax definition, known as **domain-specific language (DSL)**.

### Independent Diagnosis and Debugging

Another common scenario is when a framework has a very complex model construction process, such as SpringBoot internally performing complex conditional judgments. The effective beans' definitions are not clearly presented, making it difficult to debug when issues arise. This naturally leads to the question: if we have no idea about the framework's execution details, **what information can we obtain from a purely external perspective**?

The Nop platform is based on reversible computing principles and extensively uses DSL to define and describe system functionality. It clearly distinguishes between compile-time and runtime phases, moving as much computation as possible that isn't tied to runtime into the compile phase. Using meta-programming (Meta Programming), it dynamically discovers and assembles model信息.

For example, the NopIoC framework performs dynamic condition judgments at startup, similar to SpringBoot, resulting in a unified BeansModel. In debug mode, the framework automatically outputs a merged model file to the `_dump` directory. Additionally, you can obtain corresponding model definitions via `/p/DevDoc__beans` REST service. The returned model definitions use Spring 1.0 syntax format, effectively converting the dynamically conditional model syntax into simple Spring 1.0 syntax. Through the merged beans.xml file, you can visually understand the current system's enabled bean definitions.

All model objects in the Nop platform can be automatically converted into corresponding DSL model files. These model files are free from runtime constraints and can be easily understood. When the system encounters an execution error, Nop will attempt to map the error to its corresponding DSL source code location.

### Information Pipeline

From a broader perspective of information transmission networks, if we step outside the framework, we'll realize that frameworks aren't just sources (Source) and sinks (Sink). Many times, they also act as information conduits, meaning some information isn't produced or consumed by the framework but may need to be transmitted through it, especially in layered architectures where the framework occupies a certain abstract layer. Ideally, cross-layer direct interaction should be avoided.


In the design of the Nop platform, all DSLs and all entity objects automatically support extension properties, which can store extension information that the current framework does not directly use. This ensures that there is always a channel for storing extension information throughout the framework. This is a global design, meaning additional storage space for information is required in every part of the system. For example, when sending information through a message queue, we require that the message must support a header collection to store additional information via headers.

Using extension properties, we can construct software production pipelines where upstream-specified information can be used downstream:

![](../tutorial/delta-pipeline.png)

In the Excel data model, we can specify related attributes, which are passed from the ORM layer's extension properties through Meta and then to the front-end page models.

### How complete is the design of the framework?

From a scientific perspective, a scientifically sound solution will never be a孤立的设计，而 must consist of a set of progressively evolving strategies from simple to complex. For different levels of complexity, we need corresponding solutions. Therefore, a framework that is designed to adapt to various usage scenarios must ensure a certain level of design completeness, and this completeness is often not achievable through exhaustive enumeration.

### Function abstraction and templatization

Consider a simple example. Suppose we are writing a workflow designer where nodes need to display icons and text. The simplest design is as follows:

```javascript
type Node {
    icon: string;
    label: string;
    ...
}
```

If we need to control the position of the text, we would add a `labelPosition` description field. If we require that the background color or an additional status identifier changes based on the process state, we would add properties like `statusIconMapping`. Clearly, it's impossible to enumerate all possible requirements through attribute enumeration to ensure design completeness. Therefore, we must introduce function abstraction, such as providing a rendering function at the node level called `render` that handles custom node rendering.

Once function abstraction is established, the next question is how to implement this function. An interesting solution is templatization:

```xml
<template>
  <span v-if="prop.label">{{ prop.label }}</span>
  <span :class="'status-' + prop.status" />
</template>
```

**Using a Turing-complete template language, we can achieve functional decomposition in a descriptive manner and even provide a visual designer to support custom function content design by users.**

The Nop platform systematizes the use of Xpl template language for fine-grained function decomposition. For example, in the report engine, we need to connect external data sources to retrieve data:

```xml
<beforeExecute>
    <report:UseSplDataSet src="/report.splx" var="ds1" xpl:lib="/report.xlib" />
</beforeExecute>
```

Typically, report engines are designed with a large number of built-in data source types. If a particular data source is not built into the report tool, users must wait for the tool manufacturer to support it or develop a custom plugin. In the NopReport engine, data sources are not a deeply embedded concept within the engine; instead, the engine only calls a `beforeExecute` function before generating reports to prepare data. The specific method of obtaining data is determined in the `beforeExecute` template function. The Nop platform provides Xpl template language, which is a Turing-complete programming language using XML syntax format. In addition to manually writing code, we can also design Xpl templates using a visual designer.

> The Xpl template language integrates the Nop IoC dependency injection container. Leveraging the IoC container's ability to dynamically find beans by name, prefix, annotation, or type and implement automatic injection, it can act like a plugin mechanism with a more straightforward and intuitive calling method.

### Layered and staged design

A complete solution must be highly structured, providing **only the information needed for processing at each layer and stage**. For example:

1. **IOrmTypeHandler**: Handles structural issues at the field level, such as encryption and type adaptation.
2. **IOrmComponent**: Manages structural issues at the multiple-field level, such as organizing fields into a reusable `Address` type.
3. **IOrmEntity**: Manages structural issues at the single entity level, such as adding entity extension properties and tracking entity attribute modifications.
4. **IOrmInterceptor**: Intercepts all key operations on entities, which have global knowledge, such as recording all read and modified data during a request to output into data files for automated unit testing initialization and result validation.


Except for the combination relationships, there can also be a variety of transformational relationships between structural elements. We need to carefully analyze the hierarchical structure, determine which are the most fundamental concepts, and which can become derived concepts. Instead of piling all attributes together in a flat design.

For example, in the NopORM framework, multiple-to-many relationships are not inherently built-in as a concept; the underlying relational database storage mechanism only handles one-to-many and many-to-one relationships. In Java at the entity level, some auxiliary functions are generated via code generation mechanisms to decompose multiple-to-many relationships into two one-to-many relationships. Similar handling is also implemented in NopWorkflow for the signature feature: the workflow runtime engine does not require built-in signature nodes; instead, using `x:gen-extends` this embedded code generator, a DSL code can be dynamically generated during the loading of the workflow model, expanding a signature node into an ordinary step node and a Join aggregation step node.

Nop platform is based on reversible computing theory, providing a systemized multi-stage compilation mechanism. Similar mechanisms can be applied to all custom DSL languages. See [XDSL: General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300)

### Asynchronous Handling

Synchronous and asynchronous handling may seem like just technical choices on the surface, but fundamentally, they represent different worldviews. A framework that does not consider asynchronous handling is fundamentally incomplete.

In current program practices, if asynchronous handling is not considered upfront, it becomes difficult to convert the overall framework into one that supports asynchronous handling later on. To systematically support asynchronous handling, we need to address how to pass an object in various threads and avoid complex technical issues like lock conflicts in different concurrency scenarios.

## Six. What differential mechanisms does the framework provide?

All frameworks must consider extensibility. In software development, "extensibility" refers to being able to add additional code or differential information without modifying the original source code to meet new requirements or implement new functions. From a completely abstract mathematical perspective, software development's extension mechanisms can be viewed as:

```
Y = X + Delta
```

* **X** corresponds to the foundational code we have already written, which does not change with evolving requirements
* **Delta** corresponds to additional configuration information or differential code added

From this perspective, research into extensibility aspects is equivalent to studying the definition and operations of Delta differences.

Existing framework technologies use extension mechanisms that suffer from the following issues:

1. **Need to predict in advance where extensions might occur and define extension interfaces and methods in the base code**
2. **Each component must have its own designed extension methods and capabilities, all different from one another**
3. **Extension mechanisms often impact performance; the more extension points, the worse the system performs**

For example, adding GIS support to Hibernate requires implementing the `ContributorImplementor` interface and providing functions like `contributionFunctions`, where GIS-related functions are registered.


        @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        HSMessageLogger.SPATIAL_MSG_LOGGER.functionContributions( this.getClass().getCanonicalName() );

        KeyedSqmFunctionDescriptors functionDescriptors;
        if ( useSTGeometry ) {
            functionDescriptors = new OracleSQLMMFunctionDescriptors( functionContributions );
        }
        else {
            functionDescriptors = new OracleSDOFunctionDescriptors( functionContributions );
        }
        SqmFunctionRegistry functionRegistry = functionContributions.getFunctionRegistry();
        functionDescriptors.asMap().forEach( (key, funcDescr) -> {
            functionRegistry.register( key.getName(), funcDescr );
            key.getAltName().ifPresent( altName -> functionRegistry.register( altName, funcDescr ) );
        } );

        } sdoGeometryType );
    }

Using Hibernate's standard pattern, operations are executed by traversing associated object collections. Each access to an associated collection may result in a separate database query operation. In scenarios where initial data volumes are relatively small, directly accessing properties through the collection can avoid additional DAO calls and seem straightforward and intuitive. However, if performance issues arise, the method of obtaining data needs to be modified, and property collections cannot be used for direct access anymore.

The NopORM framework addresses this issue by introducing a mechanism similar to GraphQL DataLoader's BatchLoadQueue. Specifically, the BatchLoad call can be inserted before the business code that requires database loading optimization.

```java
dao.batchLoadProps(entityList, Arrays.asList("relatedChildre.otherRelated", "relatedEntity"));
```

> In low-code development scenarios, even runtime data access information can be collected by the low-code platform and automatically inserted as performance optimization directives in appropriate locations.

Many frameworks suffer from poor performance due to unnecessary operations when executing large amounts of feature-related logic at runtime. The Nop platform leverages code generation, compile-time transformations, and macro functions to perform as much feature-related logic as possible during development or model initialization stages, ensuring that only necessary logic is executed at runtime.

## Summary

* A framework essentially creates a self-contained technological space where its various capabilities are defined as operational rules (mathematical theorems) within this space. 

* Similar to mathematical proofs, derivations that rely on fewer assumptions (independence from specific business context) can be applied across a broader range of scenarios.

* The results of automatic derivations can be compounded like mathematical theorems to yield new outcomes.

* Reversible computation theory provides a novel perspective for evaluating the completeness of design.

* Meta-models, meta-programming, and template languages should become essential tools in a framework's design toolkit.

Based on reversible computation theory, the low-code platform NopPlatform has been open-sourced:

- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- GitEE: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- Development Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computation Theory and Nop Platform Introduction and Q&A\_Bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
