# How to Evaluate the Quality of a Framework Technology?

An interesting question is: when a new framework technology emerges, how do we evaluate its quality? Since the NopORM engine was open-sourced this year, we’ve received some feedback, but most people likely didn’t grasp the theoretical part of NopORM, so the common confusion is: what concrete advantages does NopORM have over other ORM engines? In this article, I’d like to discuss objective criteria for evaluating a framework that do not hinge on personal experience, preferences, or familiarity.

> Interestingly, some people never read the theoretical parts themselves, but when discussing the gap between domestic and international software technology, their rhetoric is “domestic development emphasizes pragmatism, and the gap in software methodology is huge.” This can only be described as a “Lord Ye’s love of dragons” type of baffling behavior.

> For theoretical analysis of the NopORM engine, see [What kind of ORM engine does a low-code platform need? (1)](https://zhuanlan.zhihu.com/p/543252423) and [What kind of ORM engine does a low-code platform need? (2)](https://zhuanlan.zhihu.com/p/545063021)

## Naive evaluation criteria

Many people use rather naive criteria to judge framework technologies, such as:

1. **Using XML, a deprecated technology, as the information carrier? Thumbs down!**
   Judging a framework based on a localized technical presentation is undoubtedly one-sided and superficial. The essence of an innovative technology is that it provides a new logical organization mechanism; which specific technical form serves as its external carrier is secondary.

2. **Is it widely used? Is the documentation thorough?**
   This is the most pragmatic view. But it evaluates whether the framework is easy to use for a “pick-and-use” type of user like me, not the design merits of the framework itself.

3. **It’s convenient and easy to understand**
   This expresses a subjective feeling, not an objectively measurable criterion. In different technical environments and backgrounds, each person has different technical preferences, and subjective experiences can vary greatly.

4. **Small footprint, fast speed**
   This reflects optimization at the implementation level and does not necessarily showcase advantages in the framework’s conceptual design. A small footprint may result from a limited feature set; high speed may indirectly reflect fewer features or the use of certain hacks (e.g., heavy use of low-level, unstable, undocumented APIs).

## Do objective evaluation criteria exist?

Although software development is a highly practical activity, in this age of science, it clearly should not be a mysticism that relies entirely on accumulated experience. We can derive some objective evaluation criteria from basic principles of computer science—namely, information theory.

## I. Can business development be independent of the framework?

Over the past two decades, a very important development in software framework technology has been recognizing framework neutrality (framework agnostic). Business code development is essentially the expression of business information, and in principle this expression should be independent of any software framework and even independent of any technical factors. The role of a framework is to assist us in expressing business information in the most natural and intuitive way while meeting performance and other technical requirements. The ideal framework is one whose existence is completely unnoticeable when writing business code.

The benefit is that we can avoid information leakage and contamination between the business domain and the purely technical domain, making business code testing and framework upgrades or even replacements exceptionally easy.

### POJO and lightweight frameworks

The spark of lightweight frameworks began with opposition to heavy container technologies like EJB. Traditional frameworks often focus on providing features, and in the process of using them, you inevitably reference framework-specific objects and insert framework-specific function calls into business code, which often strongly binds business functions to a specific runtime environment—or even a specific framework version. Lightweight frameworks popularized the concept of POJO: when writing general business code, the objects manipulated are ordinary Java objects with no framework dependencies. For example, in Spring 1.0 configuration, beans can be wired in any complex way via the beans.xml configuration file without the implementation having any Spring-specific knowledge.

Under this view, the following design in traditional SpringMVC is inappropriate:

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

In new web framework designs, the norm is to accept POJO objects and return POJO objects. Aside from annotations on functions, there are no traces of framework dependencies; annotations themselves are purely descriptive information.

```javascript
    @POST
    @Path("/my-method2")
    ApiResponse<MyResponseBean> myMethod2(MyRequestBean requestBean){
        ...
    }
```

> Annotations can introduce required information in a minimal, framework-agnostic way. For example, in the DAO access layer, many ORM frameworks now recognize JPA annotations, allowing the same entity definition to be used across multiple ORM frameworks.

Framework-specific interface dependencies are only the surface; the essential problem is that interface dependencies indirectly couple business logic to a specific runtime technical environment. For example, code written in a Controller can only be used for web request processing—it cannot be directly reused in a binary RPC layer, nor can it be directly reused as a message queue handler.

In the Nop Platform, the NopGraphQL framework normalizes request parameters into a single JSON object without introducing the myriad parameter-passing methods typical of traditional web frameworks (passing via param, via restPath, via cookie, etc.). This decouples business code from the web runtime environment: the same service function can be directly registered as a Kafka message queue handler or a batch file processor. Automated testing does not require mocking a web server, greatly reducing testing costs. For example, once we provide an online posting service for a single account, a nightly batch posting service that reads batch files can be obtained through simple configuration without any coding.

> Frontend Redux and Vuex frameworks essentially normalize actions into single-parameter functions targeting a single POJO object.

### Virtual DOM and Hooks

In the frontend, more and more business logic is expressed in a framework-neutral form.

For example, the introduction of virtual DOM allows frontend frameworks to decouple from the browser runtime, which has given rise to multi-platform technologies like React Native and the flourishing of various mini-program frameworks. A virtual DOM is essentially a plain JavaScript object that different runtimes can create and translate in the same way.

The evolution of Hooks enables the expression of frontend UI logic to escape the constraints of component objects. With minimal Hooks assumptions, business logic can be abstracted into framework-agnostic pure functions. Leveraging this abstraction, headless component libraries have increasingly become mainstream, and the same core code can be adapted to React/Vue/Angular and other base frameworks.

> Traditionally, frontend programming is always tied to a component framework: code must exist as member properties and methods of a Component class. This couples code to a specific component syntax and a specific component runtime.

## II. What automatic inference does the framework perform?

If a framework has essential superiority, then it must be that it more fully utilizes certain information compared to other options and automatically infers a lot of work based on that information.

### Annotations vs. XML configuration

Over the years, XML configuration has been in decline, replaced generally by annotations. The most important advantage of annotations over XML configuration is that they are attached to the syntactic structure of the programming language; in strongly typed languages they can also leverage existing type information, significantly reducing the amount of information that needs to be expressed.
With XML configuration, a large amount of work is actually spent on constructing basic object structures, not only causing duplication but also introducing synchronization issues between XML configuration structures and object structures. Especially when refactoring code, annotations can leverage IDE refactoring capabilities, whereas XML often sits outside refactoring tools.

Interestingly, in heavily model-driven low-code scenarios, the situation changes. When writing code manually, code is the most reliable source of information, and everything else is derived from code. In model-driven scenarios, however, the model is the Unique Source of Truth; code is merely one expression of information derived from the model. When refactoring is needed, we only modify the model, and code plus related configurations are automatically updated. In such cases, XML does not redundantly express information, nor does any information need synchronization between XML and code.

XML is not inferior to annotations across the board; it has unique advantages that are amplified in model-driven scenarios. Therefore, in the Nop Platform we use XML configuration as the foundation, with annotations as a secondary complement. I’ll elaborate further later.

### Automatic inference in ORM

Hibernate debuted at the pinnacle, establishing the basic shape of the so-called ORM framework category (subsequent ORM frameworks are generally simplified versions of Hibernate). Its value lies in the following implicit inferences:

1. Automatically map records between the database and Java objects, perform field type conversion, and generate entity primary keys.

2. Cache by primary key. If the results of two queries include the same entity object, they will always return the same Java object. This both improves performance and, in a sense, increases the application’s transaction isolation level—achieving an effect similar to Repeatable Read.

3. Identify modifications to POJO properties via dirty checking, automatically generate insert/update statements, eliminating the need to manually call dao.update(entity), and can automatically leverage JDBC batch mechanisms for performance optimization.

4. Implement automatic transaction commit and rollback on failures using Spring’s declarative transaction mechanism.

5. Automatically implement lazy loading of associated objects.

6. Automatically derive multi-table join conditions from foreign key relationships. For example, where a.b.c = 3 will automatically use the association conditions between a and b and between b and c to generate join conditions for tables a, b, and c. If the frontend already implements single-table query display, changing the field name to a.b.c automatically enables multi-table join queries.

The NopORM framework introduces more automatic inference:

1. Automatically convert vertical tables into virtual horizontal tables: extended fields can be stored in a vertical table like (entityName, entityId, fieldName, fieldValue), but are used in code the same as native database fields, and SQL syntax can be used to query and sort extended fields.

2. Automatically track entity property modifications, record modification logs, and integrate with the automated testing framework to provide database-level record-and-replay.

3. Integrate with the NopGraphQL engine to automatically publish domain models as GraphQL services.

### The pitfalls of automatic inference

Automatic inference implies certain additional assumptions. If these assumptions deviate from reality, they can backfire. For example:

1. Hibernate can set associated properties to eager loading, but associated data are not necessarily needed for every request, which may cause unnecessary performance overhead. NopORM chooses to lazy-load all associated properties, and use BatchLoadQueue only when optimizing loading.

2. Hibernate can set a session’s FlushMode to auto and let the framework decide when to flush modifications to the database. But using this mechanism often leads to unexpected database operations, and very small changes can cause substantial performance degradation. NopORM cancels this automatic mechanism and only supports manual flush.

## III. What automatic conversions does the framework provide?

Automatic conversion refers to cases where the core content of the information remains unchanged, only the representation changes from one form to another. Automatic conversion is essentially a special case of automatic inference, but because of its highly generic design, it deserves separate emphasis.

A typical example is the JSON conversion commonly used in web frameworks—two-way conversion between Java objects and JSON text. Early web frameworks lacked standardized encoding for complex parameters, and often we needed to manually parse request parameters sent from the frontend. Today, JSON serialization has evolved into a general-purpose structural conversion scheme independent of the web environment.

Since two-way conversion implies that the amount of information remains unchanged, it is generally a generic mechanism that is unrelated to the specific business. Mathematically, this means that for every structure in structure space A, we can find a corresponding structure (or an equivalent set of corresponding structures) in structure space B; conversely, for every structure in structure space B, we can find a corresponding one in A.

Automatic conversions can naturally be chained together to form more complex composite conversions.

```
  A <=> B <=> C
```

It must be emphasized that this conversion is generic, meaning there are no exceptions—every structure that might be encountered can be converted. In contrast, conversions implemented manually for business scenarios are generally ad hoc special implementations that cannot automatically handle other business scenarios.

Based on the principles of Reversible Computation, the Nop Platform applies the design philosophy of automatic conversion across the platform:

1. Implement automatic conversion between XML and JSON. Therefore, we can write AMIS page code in XML, JSON, YAML, and other formats (the AMIS framework itself only supports JSON).

2. Implement two-way conversion between XML and domain objects. With an XDef metamodel, we can automatically implement domain model parsing, validation, breakpoint debugging, and more.

3. Implement two-way conversion between Excel and domain objects. Thus, we can design all domain models in the platform using Excel model files without specialized parsing code. For example, we can automatically derive Excel-format data model definitions from XML-format ORM model definitions, and vice versa.

4. Implement two-way conversion between visual editing models and domain objects. With simple descriptive input, domain objects can be automatically visualized for editing. For instance, workflow designers and ORM designers can be automatically inferred from domain model definitions.

```
  YAML <=> JSON  <=> XML  <=> DomainObject <=> Excel <=> VisualModel
```

      In contrast, frameworks like MyBatis, Hibernate, Spring, etc. all parse their model files manually; IDE plugins and visual editors must be separately written and maintained.

There are also two-way conversions that are not fully informationally equivalent: we may add extra information during conversion. For example, frontend-submitted request data can be automatically converted into database entity objects to implement save and modify operations for master-detail data, but this conversion requires validity checks and permission checks, as well as type conversions and formatting operations. Conversely, after fetching a database entity, we can automatically convert it to JSON to return to the frontend, which also requires permission checks and formatting conversions. The NopGraphQL engine introduces Meta objects to fill information gaps, thereby automating CRUD operations for complex business objects.

```
  JSON + Meta => Entity
  Entity + Meta => JSON
```

### IV. How can relevant information be used outside the framework?

Traditional framework designs focus only on their own features and often ignore the framework’s position and interactive value within the external information network. As software intelligence increases, we want to promote unobstructed free flow of information across layers and components, which requires considering how frameworks and external information networks interact.

### Model information as an independent artifact

Frameworks of a certain complexity will undoubtedly build their own domain models and heavily use configurable model information internally. For example, Hibernate internally uses an EntityModel, Spring internally uses a BeanDefinition model, etc. However, in many frameworks, model information exists only in internal form, closely entangled with the framework’s runtime; external systems cannot easily reuse this information.

Since Hibernate 6.0, hbm configuration files have been gradually deprecated with annotations retained as the sole way to define entity models. This is, to some extent, a step backward.

1. It confines the entity model defined by Hibernate within the Hibernate framework. If we do not use Hibernate’s internal implementation, it’s difficult to obtain the corresponding model information. Moreover, model objects obtained through Hibernate’s internal functions may not be purely descriptive; they may be mixed with other information related to Hibernate’s runtime implementation.

2. If we choose to directly parse annotation information via reflection, we still depend on Java’s built-in mechanisms and must filter and screen to shield irrelevant information on Java objects—notably we need to ignore transient fields and unrelated annotations. In other words, we must re-discover the model information, rather than directly obtain a parsed and validated model object.

Contrary to Hibernate’s approach, the Nop Platform emphasizes the independence of model information: model information uses XML files as the carrier. This enables other languages or frameworks to freely use this model information without any dependency on the Nop Platform. For example, a code generator can directly read XML model files and generate frontend and backend code, Word or Excel documents, etc.

> Model objects serialized as model files must adhere to a certain schema structure, which can be regarded as a specific syntactic definition—i.e., a domain-specific language (DSL).

### Independent diagnostics and debugging

Another common situation is that a framework has a very complex model construction process internally. For example, the SpringBoot framework performs complex conditional judgments internally, and which exact bean definitions ultimately take effect is not directly visible, making diagnosis difficult when issues arise. This naturally raises a question: from a purely external perspective—knowing nothing about the framework’s execution details—what information can we obtain?

Based on the principles of Reversible Computation, the Nop Platform extensively uses DSLs to define and describe system functionality. It clearly distinguishes compile time from runtime, pushes as much runtime-independent computation as possible to compile time, and performs dynamic discovery and assembly of model information via meta programming (Meta Programming).

For example, the NopIoC framework performs SpringBoot-like dynamic conditional judgments at startup and produces a unified BeansModel. In debug mode, the framework automatically outputs a merged model file to the `_dump` directory, and we can also obtain model definitions via REST services such as `/p/DevDoc__beans`. The returned model definition uses Spring 1.0 syntax, equivalent to NopIoC normalizing dynamic-conditional model syntax into simple Spring 1.0 syntax via meta programming. With the merged beans.xml file, we can intuitively understand which beans are actually enabled in the current system.

All model objects in the Nop Platform can be automatically converted into corresponding DSL model files. These model files have no runtime state constraints and can be intuitively understood. When the system throws exceptions, the Nop Platform also strives to map exceptions to the source location of the DSL.

### Information pipelines

Looking beyond the framework itself to the broader information transmission network, we find that frameworks are not only sources (Source) and sinks (Sink) of information; often they must serve as conduits—i.e., some information is neither produced nor consumed by the framework but needs to pass through it to other structures (especially in layered architectures where frameworks occupy an abstraction layer and should avoid cross-layer direct interactions).

In the Nop Platform’s design, all DSLs and all entity objects automatically support extension attributes for storing extra information that the current framework won’t directly use. This maintains an extension information passage throughout the framework. It is a global design, meaning that all parts of the system must have extra storage space for information. For example, when sending information via a message queue, messages must support a header collection to store extra information in the header.

Leveraging extension attributes, when constructing the following software production pipeline, we can specify upstream information for use downstream:

![](../tutorial/delta-pipeline.png)

In the Excel data model, we can specify display-related properties; they pass through ORM’s extension attributes to Meta, and then to the frontend page model.

### V. How complete is the framework’s design?

From a scientific standpoint, a scientific solution is never an isolated design; it must consist of a set of strategies that can gradually evolve from simple to complex, with corresponding solutions proposed for different complexities. Therefore, a foundational framework designed to adapt to various usage scenarios must ensure a certain degree of design completeness—something that can hardly be achieved by enumeration.

### Function abstraction and templating

A simple example: suppose we are building a workflow designer, where workflow nodes display an icon and text. The simplest design looks like:

```javascript
type Node{
    icon: string;
    label: string;
    ...
}
```

If we need to control the text’s display position, we add a labelPosition field. If we need to change the background color or show an extra status indicator based on the workflow state, we continue adding properties like statusIconMapping. Clearly, we cannot exhaust all possible requirements by enumerating properties. To ensure design completeness, we must introduce function abstractions—for instance, provide a render function at the node level to implement custom rendering of nodes.

Once function abstraction is established, the next question is how to implement the function. An interesting solution is templating.

```xml
<template>
  <span v-if="prop.label">{{prop.label}}</span>
  <span :class="'status-'+prop.status" />
</template>
```

With a Turing-complete templating language, we can implement functional decomposition in a declarative manner and even provide a visual designer to let customers design the function’s content themselves.

The Nop Platform systematically leverages the Xpl templating language for fine-grained function decomposition. For example, in the reporting engine we need to connect to external data sources to obtain data:

```xml
<beforeExecute>
    <report:UseSplDataSet src="/report.splx" var="ds1" xpl:lib="/report.xlib" />
</beforeExecute>
```

Typical reporting engines bake numerous data source types into the engine, trying to exhaust all external data connection methods. If a data source is not built in, we must wait for vendor support or write a plugin ourselves. In the NopReport engine, the concept of a data source is not baked deep into the engine; the engine simply calls a beforeExecute function when outputting a report to prepare data. How to obtain data is decided by us in the beforeExecute template function. The Nop Platform provides the so-called Xpl templating language—a Turing-complete programming language using XML syntax. Besides hand coding, we can also design Xpl templates via a visual designer.

> The Xpl templating language integrates the NopIoC dependency injection container. With IoC’s object discovery capabilities (dynamically finding all beans matching conditions by name, prefix, annotation, type, etc., and performing auto-injection), it can serve a plugin-like purpose, with a simpler and more intuitive calling form.

### Layered and staged design

A complete solution must be highly structured. At different granularity levels, we should provide mechanisms that only require information at that level. Take NopORM as an example:

1. IOrmTypeHandler: handles structure issues at the single-field level. For example, field encryption and field type adaptation.

2. IOrmComponent: handles structure issues across multiple fields. For example, organizing multiple fields into a reusable Address type.

3. IOrmEntity: handles structural issues at the single-entity level. For example, adding entity extension attributes and tracking property changes.

4. IOrmInterceptor: intercepts key operations of all entities with global knowledge. For example, recording all data reads and modifications during a request and outputting them to data files as initialization data and result verification data for automated unit tests.

Beyond composition, there can be rich transformation relationships among structures. We need to carefully整理 structural hierarchies to determine which are fundamental concepts and which are derived concepts—instead of piling all features together into a flat design.

For instance, in NopORM, many-to-many associations are not built-in. The underlying relational database storage only handles one-to-many and many-to-one. At the Java entity layer, code generation produces helper functions that break a many-to-many association into two one-to-many associations. A similar treatment exists in NopWorkflow for implementing countersignature: the workflow runtime engine does not need a built-in countersignature node. Through an embedded code generator like `x:gen-extends`, DSL code can be dynamically generated during the workflow model loading process, expanding a countersignature node into a normal step plus a Join aggregation step.

The Nop Platform, based on the principles of Reversible Computation, provides a systematic multi-stage compilation mechanism. Similar mechanisms can be applied to all custom DSL languages. See [XDSL: General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300)

### Asynchronous processing

Synchronous vs. asynchronous processing may look like a technical choice, but they actually reflect different worldviews. A framework design that does not consider asynchronous processing is incomplete.

In current programming practice, if asynchronous support is not considered upfront, it is often very difficult to convert the entire framework to support asynchronous processing later on. To systematically support async, we need to consider how to pass a context object across threads and how to avoid lock conflicts in various concurrency scenarios, among other complex technical issues.

## VI. What Delta mechanisms does the framework provide?

All frameworks must consider extensibility. In software development, extensibility means that without modifying original code, we can meet new requirements or implement new features by adding extra code or differential information. Abstractly, in mathematical terms, the extension mechanism corresponds to the following formula:

```
 Y = X + Delta
```

* X corresponds to the foundational code we’ve already written; it does not change with evolving requirements.
* Delta corresponds to the additional configuration information or differential code.

From this perspective, research into extensibility is equivalent to research into the definition and operational relationships of Delta.

Existing extension mechanisms in frameworks suffer from the following problems:

1. You must predict in advance where extensions might occur, and define extension interfaces and extension methods in the base code.

2. The extension methods and capabilities provided by each component must be designed individually, and differ from component to component.

3. Extension mechanisms often affect performance—the more extension points, the worse the system performance.

Take extending GIS in Hibernate as an example: you must implement the ContributorImplementor interface, implement functions like contributionFunctions, and register GIS-related functions.

```
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
```

In the Nop Platform, by contrast, we can implement extensions via unified Delta customization. For details, see [How to implement custom development without modifying the base product’s source code](https://zhuanlan.zhihu.com/p/628770810)

### VII. Is the overall code size smaller?

A measurable criterion is: after using the framework, does the code size decrease? Code size can be seen as a measure of system complexity (descriptive complexity). A valuable framework should reduce system complexity, resulting in a noticeable reduction in code size.

A common pitfall here is that we must consider the sum of model and application code:

```
 Overall complexity = Model complexity + Application complexity
```

It’s easy to imagine implementing an extremely complex model with switches for all common business needs (enumeration), where a small amount of application description yields a fully functional system. The other extreme is a model with very limited features, forcing us to rely entirely on application-layer coding for business needs. Clearly, we need balance—matching the model’s complexity to the specific business application’s complexity—so the model can operate in as many business scenarios as possible (enhancing the model’s generalization performance).

### VIII. Does the framework’s design hinder or facilitate performance optimization?

If a framework raises the level of abstraction for expressing business logic, then in principle it should expand the space for performance optimization, allowing more optimization techniques to be used.

For example, in ORM frameworks, unified session management of entity updates makes it easy to introduce JDBC batch optimization. When updating the database, operations can be automatically sorted according to table dependencies and primary key order to ensure updates are always performed in the same order, reducing the likelihood of deadlocks.

> Deadlocks typically occur when thread A updates record x before record y, while another thread updates record y before record x. The solution is to sort the updates and always perform them in the same order—for example, all threads consistently update x first, then y.

A common complaint about Hibernate’s performance is that once issues arise, it’s often difficult to optimize through local adjustments; many times you have to rewrite code, and the result looks very different from Hibernate’s standard usage patterns. This implies a conflict between Hibernate’s standard usage pattern and performance optimization, and it’s hard to remedy later. For example, the notorious N+1 problem in Hibernate:

```java
for(MyEntity entity: entityList){
    for(MyChild child: entity.relatedChildren){
        ...
    }
}
```

The standard Hibernate pattern is to iterate over associated collections to perform operations, but each access to an associated collection may trigger a separate database query. When data volume is small early on, fetching via property collections avoids extra DAO calls and appears simple and intuitive. However, once performance issues arise, you must change your data access method—you can’t keep fetching directly via property collections.

The NopORM framework solves this via a BatchLoadQueue mechanism similar to GraphQL DataLoader. Specifically, insert a BatchLoad call before business code that needs optimized database loading:

```java
dao.batchLoadProps(entityList,Arrays.asList("relatedChildre.otherRelated","relatedEntity"));
```

> In low-code scenarios, the platform can even collect runtime data access information and automatically insert performance optimization directives at appropriate places.

As frameworks add more features, a large volume of runtime feature selection (no-op) logic is executed, inevitably degrading performance. The Nop Platform leverages code generation, compile-time transformations, macro functions, and other mechanisms to push as much feature-selection logic as possible into development time or model initialization, ensuring only necessary logic executes at runtime.

## Summary

* A framework establishes a technical space with independent significance; the capabilities it provides are like the operational rules (mathematical theorems) defined in this space.

* Like mathematical derivations, inferences carried out with fewer assumptions (free from dependency on specific business contexts) apply to broader scenarios.

* Results of automatic inference can be composed—like mathematical theorems—to yield new results.

* The theory of Reversible Computation offers a new perspective for evaluating design completeness.

* Metamodels, meta programming, and templating languages should be essential tools in the framework design toolbox.

The low-code platform NopPlatform, designed based on Reversible Computation principles, is open-sourced:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development Examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computation Principles and Nop Platform Introduction & Q&A\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:8d4045931e2ef1d930bb3657d6c378d0-->
