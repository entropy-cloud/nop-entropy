# How to Enable Business Development Independence

In the article [How to Evaluate the Good and Bad of a Framework Technology](https://zhuanlan.zhihu.com/p/645412474), I mentioned a concept called **Framework Agnosticism** (framework independence), which refers to the ideal situation where you are completely unaware of its existence during business code development. Some readers have asked: In today's context, does the idea that no business development is independent from frameworks hold any significance? Let’s break it down:

> Software development depends on frameworks because they provide input/output, event handling, dependency injection, data access, and context, which often lead to side effects in program execution. When developing a business logic, if you use a specific library or framework, most of these dependencies are implicit. To achieve true independence, you need to explicitly declare all side effects as interfaces and contexts, and have a layer that bridges the framework and the designed library.

Some might object: That approach sounds good in theory, but doesn't it lead to inconsistencies? For example, if I use Spring, what are the potential issues? Understanding this requires careful analysis:

## 1. Minimizing Information Expression

> Framework Agnosticism enables technology solutions that are independent of any **predefined** frameworks or platforms.

Firstly, recognize that framework independence is a final outcome and not necessarily our primary goal. What we should prioritize is **how to minimize information expression**.

> Minimalized expression must be business-specific. Essentially, the business logic is inherently neutral in terms of technology. It can be expressed and implemented independently of any framework. For example:

```
void activateCard(HttpServletRequest req) {
    String cardNo = req.getParameter("cardNo");
    // ...
}

void activateCard(CardActivateRequest req) {
    // ...
}
```

The first function's expression isn't minimalized because it introduces additional HTTP context, whereas the second uses only business-specific `CardActivateRequest` information.

## How to Determine if Minimized Expression is Achieved?

1. **Unit Testing**: Test whether the implemented logic matches the expected business logic.
2. **Context Independence**: Check if the logic works without external dependencies like databases or web environments. For instance, does a business function depend on the database? Can it execute without HTTP communication?
3. **Framework Independence**: Verify if the implementation uses only framework-independent interfaces and contexts.

For example, in Nop's context:

```
GraphQLRequestBean request = // ...;
IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
GraphQLResponsebean response = graphQLEngine.executeGraphQL(ctx);
```

Here, Nop's GraphQL engine operates without HTTP dependencies, relying solely on POJOs for processing.

## 2. Minimalized Expression Must Be Descriptive

Minimalized expression implies descriptive logic. It means that the implementation should focus only on what’s essential for achieving the desired outcome. For example:

- Instead of relying on a library that handles database interactions and context management, design your own layer to encapsulate these functionalities.
- Use mechanisms like mocking to simulate external dependencies during testing.

For instance, with Nop's GraphQL engine:

```
GraphQLRequestBean request = // ...;
IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
GraphQLResponsebean response = graphQLEngine.executeGraphQL(ctx);
```

This approach eliminates the need for a web server or external context, allowing the business logic to execute independently.

## 3. Traditional MVC Frameworks

Let’s examine traditional MVC frameworks:

- **Model**: Manages data and business rules.
- **View**: Handles presentation logic.
- **Controller**: Coordinates between Model and View.

In this structure, each component operates within its designated role without direct dependency on external frameworks.

Here is the translation of the provided Chinese technical document fragment into English, maintaining the original Markdown format including headers, lists, and code blocks:

---

# Framework Independence in Business Logic Development

When writing business logic for traditional frameworks, we are always tied to the framework's concepts. A large portion of development involves working with built-in structures like `Controller/HttpServletRequest/ModelAndView`, and the business logic is tightly coupled with the framework's implementation.

In modern Web frameworks, metadata is used to inject framework-related information into the application. This decouples the framework from the business logic, meaning that the framework no longer directly interacts with the business code. Instead, it relies on the specific execution path defined by the framework.

For example:

```java
@Path("/account/:id")
@GET
public MyResponse findAccount(@PathParam("id") String id) {
    // ...
}
```

However, when comparing this to the Nop platform's implementation, it is not minimalized. Instead, we use:

```java
@BizQuery
public MyResponse findAccount(@Name("id") String id) {
    // ...
}
```

The `@Path` and `@GET` annotations appear to provide metadata about URL paths and HTTP methods. While these annotations are useful for Web frameworks, they become redundant when using GRPC, as the protocol already handles the communication details.

In the Nop platform's implementation, all annotations point to domain-specific information rather than external framework configurations. For instance:

- `@BizQuery` is a supplementary annotation that describes the method's domain logic, not an external framework.
- `@Name("id")` simply provides metadata for the parameter name and does not interact with any external framework.

The minimalized approach allows all annotations to remain relevant when exposing a service via GRPC without requiring additional configuration.

---

# Information Transformation Between Frameworks

When discussing framework independence in this context, I often use **framework-independent** as a concept. Some might wonder: If everything is framework-independent, does that mean we no longer rely on any external frameworks? How can we coordinate between different entities (classes, modules) without any framework to tie them together?

To fully understand this subtlety, one must have some knowledge of modern mathematics (from the late 19th century). In contemporary mathematics:

$$
A \cong B \Longrightarrow A = f(B),\ B = g(A)
$$

This implies an equivalence relation where transformations can convert between the two.

In the context of business logic and frameworks:
- **Framework-independent** does not imply that business code is entirely decoupled from any framework.
- Instead, it means that business code is only loosely coupled with a specific framework's runtime.

For example:

- If we use Framework A (`f(A)`), our business logic can be adapted to Framework B (`g(B)`) through transformation without direct dependency on `A` or `B`.

---

# Example: Minimalized Annotation in Nop Platform

Consider the following code in the Nop platform:

```java
@BizQuery
MyResponse findAccount(@Name("id") String id) {
    // ...
}
```

Here:
- `@BizQuery` is used to describe the method's domain logic.
- `@Name("id")` provides metadata for the parameter.

In this setup, annotations do not inject external framework configurations but rather serve as metadata within the domain model. This allows seamless exposure of services via GRPC without additional configuration.

---

# Mathematics Behind Framework Independence

The concept of framework independence in mathematics can be understood through equivalence relations:

$$
A \cong B \Longrightarrow A = f(B),\ B = g(A)
$$

This means:
- `f` and `g` are functions that transform between the two.
- The composition of these functions results in an identity transformation.

In the context of frameworks:
- Business logic can be transformed (`f`) to work with a specific framework's runtime.
- Without direct dependency on the original framework, it can be adapted to another framework's runtime (`g`).

---

# Example: Framework A vs. Framework B

Suppose we have two frameworks, A and B.

- Framework A:
  ```java
  @Path("/account/:id")
  @GET
  public MyResponse findAccount(@PathParam("id") String id) {
      // ...
  }
  ```

- Framework B (Nop):
  ```java
  @BizQuery
  public MyResponse findAccount(@Name("id") String id) {
      // ...
  }
  ```

While both frameworks achieve the same goal, their implementations are minimalized in Framework B. This allows for greater flexibility and less dependency on external configurations.

---

# Bridging Between Frameworks

The key to framework independence lies in the ability to transform between different frameworks' internal representations:

$$
f(B) = A,\ g(A) = B
$$

This transformation ensures that business logic remains consistent across frameworks, without requiring direct coupling to any specific runtime.

---

# Feign Client Configuration and Interface Definition

```java
@FeignClient(name = "springMvcExampleClient", url = "http://example.com")  
public interface SpringMvcExampleClient {  

    @GetMapping("/hello")  
    String hello(@RequestParam("message") String message);  
}
```

```java
@FeignClient(name = "jaxRsExampleClient", url = "http://example.com")  
public interface JaxRsExampleClient {  

    @GET  
    @Path("/hello")  
    @Produces(MediaType.TEXT_PLAIN)  
    String hello(@QueryParam("message") String message);  
}
```

## Feign Implementation Principle

In the internal implementation, Feign only requires writing a single type of annotation handler. For another type of annotation, simply convert it to a supported format. No need to separately support runtime frameworks for each annotation type.

Note that this transformation is completed during compilation and is purely a structural transformation at the interface level. It does not involve any runtime state management, making it **essentially a bidirectional mathematical transformation**.

In summary, if we consistently aim for minimal information expression in framework implementation and achieve a global minimal value, the expressed information will inherently possess uniqueness. If there's no uniqueness, further comparison can be made to select the smallest expression. If multiple frameworks have implemented minimal information expression, the uniqueness of this minimal expression ensures that different frameworks' expression forms are interchangeable via invertible transformations.

This design allows for framework-agnostic behavior by allowing an additional transformation layer in framework design, aligning with the principle of minimal information expression.

## Detailed Explanation

Minimal information expression only constrains the business logic's structural representation but does not imply identical capabilities across frameworks. Different frameworks can employ different implementation technologies and architectures, each excelling in specific aspects like performance or suitability for particular use cases. However, the business logic itself can be ported to any runtime framework without modification or with minimal preprocessing during compilation.

This minimal information expression is a descriptive form, where its actual execution semantics are determined by the chosen runtime framework. The same business expression may yield different semantic meanings when executed across different frameworks, even if the surface syntax remains identical.

For example, we can express a data processing function that works within an embedded local runtime framework, resulting in a simple semantic meaning. However, this same function can be deployed into a distributed big data runtime framework, where it assumes a different semantic context and may introduce additional processing details related to distribution.

Admit that current mainstream frameworks do not fully recognize these underlying principles. As a result, automatic transformation for achieving framework-agnostic behavior is not feasible. For instance, while Feign supports both JAX-RS and Spring MVC annotations, the Spring MVC implementation cannot easily incorporate JAX-RS annotations without significant changes.

Only the Nop platform's implementation strictly adheres to the minimal information expression principle, demonstrating how different expression forms can be freely converted and flowed between via transformation layers. This design surpasses the capabilities of mainstream frameworks.

## Questions and Answers

Someone might wonder: Minimal information expression only covers a part of the information—where is the rest expressed? In the Nop platform, information structure is well-organized and planned, functioning as a coordinate system that allows arbitrary information insertion at specified coordinates. This allows for configuration storage and retrieval in a structured manner, merging information into the overall information structure through specific mechanisms like Delta changes.

This structured approach enables framework-agnostic design by allowing configuration to be stored and managed independently of the runtime framework's specifics, ensuring that it can be seamlessly integrated into various execution environments while maintaining high-level coherence.

# Summary

## How to Achieve Framework-Agnostic Behavior

1. **Data (Input/Output/Storage)**  
   - Ensure data formats are standardized.
  2. **Control (Commands/Events)**  
   - Standardize command structures and event triggers.
3. **Side Effects**  
   - Minimize side effects or document them clearly.
4. **Context**  
   - Maintain consistent context handling across frameworks.

## Data Handling

In mathematical terms, minimal information expression ensures that all necessary data is provided without redundancy. This eliminates ambiguity in interpretation and allows for unidirectional transformation without loss of semantic meaning.

```java
output = biz_process(input)
```

# Business Process Considerations

In an ideal scenario, `biz_process` can be applied across all possible application scenarios. This means we only need to verify local conditions and ensure they meet the `input` and `output` structural requirements to implement the `biz_process` step in processing.

The `biz_process` should reflect all external dependencies and constraints as boundary elements (input/output) to ensure compatibility.

For the system to remain framework-agnostic, both `input` and `output` must not contain specific runtime dependencies. The simplest approach is to design them as POJOs (Plain Old Java Objects), allowing uniform handling across all frameworks. However, they don't necessarily need to be POJOs. For instance, in the big data domain, we can choose `input` and `output` as Arrow data formats, which are binary column-wise storage formats.

The Arrow standard defines a set of abstract operations, each implemented by corresponding SDKs for different programming languages.

# Data Interchange Mechanisms

The Nop platform supports various data interchange formats including XML/JSON/Java/Excel through a bidirectional conversion mechanism. Additionally, NopORM unifies the storage layer's data representation, enabling developers to write business logic primarily focused on pure business objects.

## Entity Definition in NopORM

NopORM entity definitions are also framework-agnostic. If JPA is well-designed, it can be integrated as the underlying runtime to retrieve `OrmEntity` from the Nop platform.

# Event Handling

Traditional event handling involves passing an event response function to components, which then invoke this function internally. This approach aligns with asynchronous callback mechanisms. Modern frameworks typically discard callbacks in favor of Promises and async/await syntax. Similarly, event handling can be abstracted as a `Stream` object returned from the `output`.

```
Callback<E> ==> Promise<E>

EventListener<E> ==> Stream<E>
```

Event handling can thus be reduced to processing an `Output` type. The system should implement standardized stream processing interfaces, such as Java's `Flow` interface.

# Side Effects

Isolating business logic from external dependencies is challenging due to side effects, which are often inherent in I/O operations. Treating side effects as `output` results can offer a functional approach.

```
[output, side_effect] = biz_process(input, context)
```

However, side effects inherently carry execution semantics, making them easy targets for runtime dependencies. For example, file downloads in traditional Web frameworks often require `HttpServletResponse`, causing business logic to couple with Servlet interfaces. This makes the code incompatible with other runtimes like Spring and Quarkus.

# Overcoming Runtime Dependencies

A standardized solution arises from functional programming: instead of executing side effects directly, encapsulate them within a result object returned as output. For instance, in the Nop platform, file downloads can be encapsulated using `@BizQuery` annotations.

```javascript
@BizQuery
public WebContentBean download(
    @Name("fileId") String fileId,
    @Name("contentType") String contentType,
    @ServiceContext void ctx) {
    IFileRecord record = loadFileRecord(fileId, ctx);
    if (StringHelper.isEmpty(contentType)) {
        contentType = MediaType.APPLICATION_OCTET_STREAM;
    }

    return new WebContentBean(
        contentType,
        record.getResource(),
        record.getFileName());
}
```

This approach isolates the implementation details within the Nop platform, allowing business logic to remain focused on core business objects.

# Context Handling in Nop Platform

The Nop platform's approach does not involve directly executing download actions. Instead, it wraps the pending files into a `WebContentBean` object and identifies `WebContentBean` instances at the framework level using different runtime frameworks' provided download mechanisms to execute the actual download process.

## Business Logic Simplification
In the business logic layer, we only need to express the intention of needing a specific file to be downloaded. There is no necessity to directly perform any download actions. The framework will handle the actual execution of the download process through its built-in mechanisms.

# Database Operations and Side Effects

Another common side effect in application code is database storage operations. Each `dao.save()` or `dao.update()` call results in a direct database interaction, which can negatively impact the business function's composability and may lead to potential update conflicts. For example, if three different parts of a service processing function modify the same entity's properties, saving it once should suffice. However, calling `dao.save()` three times would result in three separate database writes.

# Nop Platform's Solution: ORM Engine with Session Support

The Nop platform addresses this issue by introducing the concept of `OrmSession` through its NopORM engine. In business logic code, all `dao.save()` and `dao.update()` calls can be completely removed. The ORM framework automatically tracks any entity modifications during a transaction commit. It generates incremental update statements for only those entities that have been changed since the last commit, ensuring efficient data synchronization between memory and the database.

# Traditional Framework Context Handling

In traditional frameworks, the context object is often the first point of interaction with runtime framework components. For instance, `HttpServletRequest` provides a `RequestDispatcher` object that can be used to forward requests to other servlets or static resources. However, this tight coupling creates several issues:

```
// Obtain RequestDispatcher and specify the target resource path
RequestDispatcher dispatcher = request.getRequestDispatcher("/targetServlet");
dispatcher.forward(request, response);
```

This leads to:
1. Tight coupling of application code with specific runtime framework components.
2. Difficulty in maintaining compatibility across different frameworks due to framework-specific APIs.

# Nop Platform's Approach: Simplified Context Handling

The Nop platform simplifies context handling by abstracting it into a generic container (`IServiceContext`). This service context is uniformly implemented across different engines, allowing for framework-agnostic operations. Unlike traditional approaches where the context is tightly bound to specific runtime components like `HttpServletRequest`, Nop's container serves as a flexible and interchangeable mechanism.

# Dependency Injection in Nop Platform

NopIoC (Nop Dependency Injector) provides a framework-independent way to manage dependencies. It uses declarations like `@Inject` to perform dependency injection without being tied to any specific container implementation. This approach ensures that application code remains decoupled from runtime frameworks and can be easily migrated between containers.

## Frequently Asked Questions

1. Does this mean that business logic should never directly execute SQL logic?
   
   Answer: Yes. The Nop platform uses EQL (Entity Query Language) for database interactions, abstracting away differences between databases through the `Dialect` abstraction layer. Complex queries are handled at a higher level of abstraction, minimizing direct database interactions.

2. Is the approach described in the document similar to what I've seen in some video demonstrations?
   
   Answer: Yes and no. While the concept of managing context and dependencies is common across frameworks, Nop's specific implementation provides unique advantages, such as framework independence and simplified state management through `OrmSession`.


The proposed solution was developed under the guidance of reversible computation theory. To maximize its effectiveness, the underlying software architecture needed to be completely restructured. Consequently, the Nop platform did not leverage existing open-source frameworks but instead followed the principles of reversible computation theory to develop everything from scratch.

3. The current practical approaches are generally lacking in mathematical theoretical guidance. They are typically based on architects' individual experiences and simple understandings rather than a systematic approach. This lack of consistency makes it difficult to ensure broad and holistic conceptual alignment, which is essential for enabling automated reasoning at scale.

3. Is the burden of minimalized information representation too heavy? For example, if we use Kafka directly without creating a generic MQ abstraction layer, we simply call Kafka's API. This is a common misconception.

Minimalized information representation does not imply that we need to provide a universal MQ abstraction layer. Instead, it should help us mask the differences between various messaging queues like Kafka/RocketMQ/Pulsar. By understanding these differences and providing an appropriate abstraction, we can encapsulate generic functionality while preserving each queue's unique characteristics. This design is challenging.

Minimalized information representation is aimed at the business domain itself rather than external technical frameworks. This means that we might not fully understand all functionalities of multiple MQ systems and may struggle to create a universal abstraction layer encompassing all of them. However, we do understand our specific business requirements and the functionalities of the MQ systems we use. Thus, minimalized information representation essentially requires defining an interface that includes only the functionalities we currently need in our business.

This approach does not necessitate encapsulating generic functionality but allows us to define it specifically for our business needs. In essence, it means moving all Kafka-related content into a limited number of classes and hiding Kafka-specific knowledge behind domain-specific interfaces. In practical programming, this often leads to direct usage of Kafka's API without adequate isolation in our own code. This can result in missing appropriate configurations, leading to potential misuse of Kafka and issues with business logic.

In the Nop platform, we implemented a different approach by providing an `IMessageService` interface that encapsulates certain capabilities. This interface provides mathematical meaning for unidirectional information transmission within the Nop platform. However, this is another story.

If we aim for minimalized information representation, we must avoid encapsulating messaging queues themselves. Each messaging queue typically represents a set of unique technical decisions and operational requirements. Without a unified interface, it's difficult to ensure overall consistency across multiple MQ systems.

A feasible approach would be to start from the business domain or first principles based on mathematical theory. This would allow us to define a series of minimalized functional requirements and then implement them for each messaging queue individually. In practice, this means moving all Kafka-related content into a limited number of classes while keeping Kafka-specific knowledge hidden behind domain-specific interfaces.

In real-world programming practices, we often observe that Kafka's API is used directly without adequate isolation in our own code. This can result in missing appropriate configurations and potential misuse of Kafka, leading to issues with business logic.

The Nop platform took a different approach by providing an `IMessageService` interface that encapsulates certain capabilities. This interface provides mathematical meaning for unidirectional information transmission within the Nop platform. However, this is another story.

If we aim for minimalized information representation, we must avoid encapsulating messaging queues themselves. Each messaging queue typically represents a set of unique technical decisions and operational requirements. Without a unified interface, it's difficult to ensure overall consistency across multiple MQ systems.

A feasible approach would be to start from the business domain or first principles based on mathematical theory. This would allow us to define a series of minimalized functional requirements and then implement them for each messaging queue individually. In practice, this means moving all Kafka-related content into a limited number of classes while keeping Kafka-specific knowledge hidden behind domain-specific interfaces.

