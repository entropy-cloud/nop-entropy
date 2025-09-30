# How can business development be independent of frameworks

In the article [How to evaluate the merits of a framework technology](https://zhuanlan.zhihu.com/p/645412474), I introduced a concept, framework agnosticism, and pointed out that the most ideal framework is one that you are completely unaware of when writing business code. Some readers asked: As of now, no business development is completely independent of frameworks—what is the point of this concept? A helpful classmate in the discussion group replied:

> Software development depends on frameworks because we need the framework-provided input/output, event callbacks, dependency injection, external data I/O, context, etc.—most of which are side effects that the program’s execution relies on. If, when implementing a piece of business, we design it as a library, explicitly declare all side effects as interfaces and context objects, and have a middle layer that bridges the framework and the designed business library, then this business library can be independent of the framework.

Someone tried to refute: isn’t this still just setting an internal standard? If so, what’s wrong with using Spring’s standard? The answer to this is a bit subtle and requires some fine-grained conceptual discrimination to understand. I’ll briefly clarify the concepts here.

## I. Minimizing Information Expression

> framework agnosticism allows create technology solutions that are independent of any **predefined** frameworks or platforms.

First, we need to recognize that framework neutrality is a final manifested outcome, not necessarily a directly pursued goal. The first goal we should pursue is how to achieve minimal information expression.

Minimal expression is necessarily business-specific. Essentially, business logic is technically neutral; it can be expressed and implemented independently of any framework. If we strip away all extra information, the remaining concepts can only be those internal to the business domain. For example:

```
void activateCard(HttpServletRequest req){
    String cardNo = req.getParameter("cardNo");
    ...
}

void activateCard(CardActivateRequest req){
    ...
}
```

Comparing the two functions above, the first is not minimally expressed because it introduces extra HTTP context information, whereas the second relies only on a CardActivateRequest object customized for the current business.

How do we tell whether we have achieved minimal expression? The first test is unit testing. Are all preparations for unit tests directly related to the current business logic? For example, if a business function does not depend on a database, can we test it without starting a database? Can we test it without starting a web environment? Can’t the same business logic run in a batch-processing environment? Why does it have to depend on the existence of a web server?

On the Nop platform, not only is the information expression of business implementation code minimized, but the expression of the underlying engine is minimized as well. For example, the NopGraphQL engine can be tested away from the HTTP environment:

```java
 GraphQLRequestBean request = ,,,
 IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
 GraphQLResponsebean response = graphQLEngine.executeGraphQL(ctx);
```

When testing web framework-level functionality, we generally either provide a mocked web runtime environment or start a real web server (consuming server ports and thread pools). But the NopGraphQL engine’s information expression is minimized: it merely receives a POJO request object, dispatches the service request logic and performs result data trimming, and returns a POJO response object. It requires no knowledge of the web server and does not need a built-in thread pool. Precisely because of this minimal expression, the entire NopGraphQL engine and the business code implemented through it can run detached from the web runtime environment and can be published directly as batch services, message-queue processing services, GRpc services, etc.

## II. Minimal Expression Is Necessarily Declarative

Understanding “minimizing the current expression” from the opposite direction means “maximizing future possible expressions.” If an expression is minimal, we will inevitably only describe the goals we want to achieve while omitting the various execution details required to reach those goals—for example, what approach to use, in what execution order, etc. That is, we will delay making concrete technical decisions as much as possible and delay expressing information that is tied to specific execution as much as possible. Therefore, minimal information expression is necessarily declarative; execution-detail information should be specified at runtime or automatically derived by the underlying runtime engine according to some optimization strategy.

First, consider traditional MVC frameworks:

```java
public class MyController implements Controller {
    @Override
    public ModelAndView findAccount(HttpServletRequest request, HttpServletResponse response) {
       ...
    }
}
```

When writing business code with traditional frameworks, we always program around framework concepts. A large amount of information handling touches framework-built structures such as Controller/HttpServletRequest/ModelAndView, and the entanglement of business code with framework code is determined by the concrete execution path of the code.

Modern web frameworks generally inject framework-related information into programs through declarative metadata, so the framework no longer directly touches business code. For example:

```
@Path("/account/:id")
@GET
MyResponse findAccount(@PathParam("id") String id){
    ...
}
```

However, compared with the Nop platform’s implementation, the expression above is still not minimal.

```
@BizQuery
MyResponse findAccount(@Name("id")String id){
    ...
}
```

Annotations like @Path and @GET look declarative, but they still introduce assumptions irrelevant to the current business: information such as URL paths and HTTP methods is only useful to web frameworks. If we switch to a GRpc service protocol, that information becomes superfluous. In the Nop platform’s expression, all annotations point to business-domain information rather than external technical frameworks. @BizQuery is a supplemental mark of the domain information internal to the findAccount method, not something dedicated to an external technical framework (note the subtlety here: @BizQuery points to the business domain rather than an external technical framework). Likewise, @Name("id") merely supplements the parameter’s name; it is independent of any external usage and is useful information to any technical framework.

Precisely because of this minimal expression, all information is related to domain logic. Therefore, when we publish a service function as a GRpc service, the information conveyed by @BizQuery and @Name can be used directly. We need no extra configuration to expose the same service function via multiple protocol interfaces.

## III. Formal Transformations Between Declarative Information

When discussing framework agnosticism, I often mix in the term framework independence. Some may wonder: doesn’t “framework-independent” ultimately mean you write your own standard protocol? If you don’t couple to someone else’s framework, aren’t you coupling to your own? How can independent entities be coordinated without a framework?

To truly grasp the subtlety, a bit of modern mathematics (roughly from the late 19th century) helps. In modern mathematics, when we say A is B, it does not mean A and B are identical or literally the same thing; it means A and B can be converted into one another via some equivalence operation.

$$
A \cong B \Longrightarrow A = f(B), B = g(A)
\Rightarrow f\circ g = I_A, g\circ f = I_B
$$

Similarly, when we say business code is framework-independent, it does not mean the business code is unrelated to any framework; it means that it is independent of the runtime of any particular framework. Code expressed with framework A can, at compile time, be automatically transformed to adapt to another framework B—even a framework B that has not yet been written!

For example, in the Feign RPC framework, we can annotate service functions with either JAX-RS or SpringMVC annotations:

```java
@FeignClient(name = "springMvcExampleClient", url = "http://example.com")  
public interface SpringMvcExampleClient {  

    @GetMapping("/hello")  
    String hello(@RequestParam("message") String message);  
}

@FeignClient(name = "jaxRsExampleClient", url = "http://example.com")  
public interface JaxRsExampleClient {  

    @GET  
    @Path("/hello")  
    @Produces(MediaType.TEXT_PLAIN)  
    String hello(@QueryParam("message") String message);  
}
```

In principle, Feign’s internal implementation only needs to support one annotation form. For the other, it can perform a formal transformation to the already supported format. We do not need to implement separate runtime framework support for every annotation form.

Note that such transformations can be completed at compile time and are purely formal, involving no runtime state management. Therefore, they are essentially bidirectional reversible mathematical transformations.

To summarize: if, when implementing a framework, we always require minimal information expression and ultimately reach some global minimum, then the expressed informational content will necessarily have some uniqueness. If it is not unique, we can still compare which expression carries less information and choose the one with less information. If multiple different frameworks all achieve minimal information expression, this uniqueness guarantees that their minimal expressions are necessarily convertible via equivalence transformations (reversible transformations). If, in framework design, we always allow insertion of a formal transformation adapter layer and adhere to the principle of minimal information expression, the effect of framework agnosticism naturally follows.

We should also clarify that minimal information expression merely constrains the expression structure at the business layer; it does not imply that different frameworks have the same capabilities. In practice, different frameworks can adopt different implementation technologies and architectures, with substantive differences in performance and applicable scenarios. Yet business-layer code can either remain unchanged or require only a single pass of compile-time preprocessing to migrate to different runtime frameworks. Because minimal business expression is declarative, its concrete execution effects are determined by the runtime framework, and it may even happen that the same business expression has different actual semantics across different runtime frameworks. For instance, we can express a data-processing function that runs on an embedded local runtime framework, where it has simple data-processing semantics; we can also run it on a distributed big-data runtime framework, where the big-data framework automatically introduces a wealth of distributed execution semantics and state details.

It must be admitted that mainstream frameworks today have not clearly internalized the constructive principles above and therefore cannot achieve framework agnosticism via automated formal transformations. For example, although Feign RPC supports both JAX-RS and SpringMVC annotation forms, on the server side, SpringMVC implementations generally cannot introduce JAX-RS annotations in a simple way; we are typically locked into SpringMVC’s native annotations.

Only the Nop platform truly adheres to the principle of minimal information expression. Its implementation demonstrates how to freely convert and flow information among different forms—capabilities that far surpass mainstream frameworks.

Some may ask: minimal information expression only describes part of the information, so where are framework-related data such as optimization configurations expressed? On the Nop platform, information structures are well planned—it’s as if a ubiquitous domain coordinate system were established. Through various Delta mechanisms, arbitrary information can be inserted at designated coordinates, so we can store framework-related configurations separately and then locate them via the coordinate system to merge into the overall information structure.

## IV. How can framework agnosticism be implemented in practice?

The previous sections are mostly theoretical analysis. There are still some practical experiences and methods for achieving framework agnosticism. A classmate in the discussion group offered an excellent insight:

> Specifically, we need to handle the following in a framework-agnostic way:
> 
> 1. Data (data input/output and storage)
> 2. Control (commands, events, etc.)
> 3. Side effects
> 4. Context

## 1. Input and Output

From a mathematical standpoint, minimal information expression concentrates externally related information at the boundary layer. In formula form:

```
  output = biz_process(input)
```

Ideally, biz_process can be applied in all possible scenarios; that is, we only need to check local conditions and, upon finding that input and output structural requirements are met, we can insert the biz_process step into the pipeline. All dependencies and constraints of biz_process on the external world are manifested as adaptation conditions of the boundary elements (input/output).

For input and output to be framework-agnostic, they must not include specific runtime dependencies. The simplest approach is to implement them as POJO objects so that all frameworks manipulate them in the same way. But POJOs are not strictly necessary. For example, in big-data domains, we can choose Arrow as the input/output format, a binary columnar storage format. The Arrow standard defines a set of abstract operations whose concrete implementations are provided by SDKs in different programming languages.

The Nop platform provides bidirectional conversion among XML/JSON/Java/Excel representations and unifies data representations at the storage layer via NopORM, allowing us to write business code primarily against pure business objects.

> NopORM’s entity definitions are essentially framework-agnostic. That is, if JPA were well-designed, we could integrate JPA directly as the underlying runtime for persisting OrmEntity objects in the Nop platform.

## 2. Event Handling

Traditionally, event handling involves passing an event response function to a component and having the component invoke it as a callback. This process is essentially the same as handling asynchronous callbacks. In modern async handling, most frameworks have abandoned callbacks in favor of the Promise abstraction and async/await syntax. Similarly, for event handling, we can abstract event triggering as a Stream object and return this stream in the output.

```
Callback<E> ==> Promise<E>

EventListener<E> ==> Stream<E>
```

Thus, event handling can essentially be reduced to a special case of Output. It is necessary to introduce standardized stream-processing interfaces in the system, such as Java’s Flow interface.

## 3. Side Effects

Viewing the entanglement between business logic and the external world purely as input and output is often oversimplified. A finer description can be expressed as:

```
[output, side_effect] = biz_process(input, context)
```

The issue with side effects is that they generally carry execution semantics and thus easily introduce dependencies on specific runtime environments. For example, implementing file downloads in a typical web framework:

```javascript
 void download(HttpServletResponse response) {
     OutputStream out = response.getOutputStream();
     InputStream in = ...
     IoHelper.copy(in, out);
     out.flush();
 }
```

Because we need to use the response object provided by the runtime framework, our business code becomes tied to the Servlet interface. Consequently, the file-download business code we write cannot automatically be compatible with both Spring and Quarkus runtime frameworks.

> Quarkus generally uses RESTEasy for the web layer, which does not support the Servlet interface.

A standardized solution comes from functional programming: do not execute the side effect; instead, package the information corresponding to the side effect into a declarative object and return it as the result. For example, on the Nop platform, file downloads are implemented as follows:

```javascript
    @BizQuery
    public WebContentBean download(@Name("fileId") String fileId,
                                   @Name("contentType") String contentType,                                            IServiceContext ctx) {
        IFileRecord record = loadFileRecord(fileId, ctx);
        if (StringHelper.isEmpty(contentType))
            contentType = MediaType.APPLICATION_OCTET_STREAM;

        return new WebContentBean(contentType, record.getResource(),     
                  record.getFileName());
    }
```

The Nop platform does not actually perform the download; instead, it wraps the file to be downloaded in a WebContentBean and returns it. The framework layer then uniformly recognizes WebContentBean and uses the download mechanisms provided by different runtime frameworks to perform the actual download. In the business layer, we only need to express the intent that “a certain file needs to be downloaded”; there is no need to perform the download ourselves.

Another common side effect in application code is data persistence. Each call to dao.save generates database interaction. In many cases, this breaks the composability of business functions and can lead to potential update conflicts. For instance, during the execution of a service handler, suppose we modify attributes on an entity in three places. In principle, we only need one save; but if we call the DAO three times, we incur three database accesses.

The Nop platform’s solution is to use the NopORM engine to introduce the concept of OrmSession. In business logic code, we can remove all calls like dao.save/dao.update. Before committing the database transaction, the ORM framework checks whether any entity has been modified; if so, it automatically computes Delta update information and generates update/insert/delete statements to synchronize in-memory state with the database’s persistent state. Essentially, this leverages the UnitOfWork pattern to cache the actual execution of side effects via a session.

## 4. Context

Context objects in traditional frameworks are the easiest source of runtime-framework dependencies. Framework authors often find it hard to resist the temptation to add generic methods on the context object, which directly leads to framework lock-in. For example, the HttpServletRequest object provides a Dispatcher with execution semantics:

```
// Obtain a RequestDispatcher and specify the target resource path
        RequestDispatcher dispatcher =           request.getRequestDispatcher("/targetServlet");

        // Call forward to perform the dispatch
        dispatcher.forward(request, response);
    }
```

This directly binds the HttpServletRequest object to the Servlet-container environment, making it difficult to create a Request object for use in application code independently. If our business code inadvertently introduces a dependency on HttpServletRequest, it will be difficult to run it within other framework contexts.

The Nop platform’s approach is to weaken the behavioral semantics of context objects and degrade them into general data containers. Specifically, the Nop platform consistently uses IServiceContext as the service context object (the same context interface across different engines), but it has no special execution semantics; essentially, it is a Map that can be created and destroyed at will.

In addition, the Nop platform provides a unified dependency injection mechanism through the NopIoC container. Dependency injection can be viewed as on-demand usage of the context environment object, thereby avoiding acquiring the entirety of the context’s executable information and creating unnecessary dependencies. NopIoC uses Java standard annotations such as @Inject to implement framework-agnostic dependency injection.

It must be said that most current framework designs do not meet the requirements of framework-agnostic design. Take Spring, for example: although it initially touted declarative programming and POJOs, over time it has introduced too many special interface dependencies and implicit execution-order dependencies. Particularly frustrating is that dependency injection configuration written for Spring is not only useless when migrating to other IoC containers, it can actually block migration. For instance, when we annotate private variables with @Autowired to inject dependencies, it means that if we don’t use the Spring container, we simply have no way to configure this bean.

NopIoC returns to the original intent of declarative dependency injection containers. At the conceptual level, it ensures that beans managed by the NopIoC container can always be configured by other IoC containers.

## Q&A

1. Does this imply that business code should not directly express SQL logic?
   
   Answer: Yes. In the Nop platform, the NopORM engine defines an EQL syntax, shields the differences among multiple databases through a Dialect abstraction, and introduces an object-oriented property access syntax. In addition, at the IEntityDao layer, structures such as QueryBean are used to express complex filter conditions; the underlying storage can run on Redis/ElasticSearch/TDengine/MongoDB and other non-relational stores. We always try to operate at a higher level of abstraction; automated mathematical reasoning is easier at higher levels. For example, the front-end framework automatically wraps request data in the QueryBean format, and the rules engine can directly use the QueryBean format—none of which requires programming. If you use SQL directly, you must handle large amounts of structural conversion and implementation-layer adaptation yourself, which blocks automated reasoning paths and undermines the system’s internal conceptual consistency.

2. Is the approach in this article similar to something I saw in a DDD video?
   
   The solution presented here is guided by Reversible Computation theory. To realize its maximal effectiveness, it requires restructuring all underlying software structures. Therefore, the Nop platform does not use existing open-source frameworks; it is reimplemented from scratch under the guidance of Reversible Computation. Other visible practical schemes are, in principle, lacking mathematical guidance and are merely architects’ naive interpretations based on their work experience. They cannot guarantee broad, holistic conceptual consistency and thus cannot achieve systematic automated reasoning.

3. Doesn’t the burden of minimal expression become too heavy? For example, if I use Kafka, I’m too lazy to build a generic MQ abstraction and just call Kafka’s API directly.
   
   This is a misunderstanding. Minimal information expression does not mean you need to provide a generic MQ abstraction to obscure the differences among Kafka/RocketMQ/Pulsar, etc. Understanding all the differences among these queues, proposing appropriate abstractions to encapsulate common functionality while preserving each queue’s unique features, is very hard. Minimal information expression is oriented toward the business domain itself, not external technical frameworks. In other words, we may not fully grasp the complete functionality of multiple MQs or propose a suitable universal abstraction, but we do understand our own business needs, and the MQ features used by our business are certainly only a very small subset of all features. Minimal information expression requires defining an interface that includes only the functionality we need in the current business. This encapsulation need not be generic and can be tailored strictly to our own business. At the most basic level, it means writing all Kafka-specific content into a handful of classes and hiding Kafka-related knowledge behind business-facing interfaces. In actual programming practice, my observation is that projects that directly use Kafka’s API everywhere without appropriate isolation end up with higher maintenance costs, often lack proper configuration, and even misuse Kafka, causing business logic issues.
   
   On the Nop platform, our approach is to provide an interface such as IMessageService as a generic encapsulation, giving the Nop platform a mathematically meaningful unidirectional capability for sending and receiving messages. But that’s another story.
   
   If we aim for minimal information expression, we are definitely not trying to encapsulate the message queue itself, because each message queue embodies a distinct combination of technical decisions, and there is generally no unified, common interface across them. The feasible approach is to start from the business or from first-principles mathematics, propose a series of minimal functional requirements, and then, for each narrow functional interface, provide concrete implementations for each message queue.

<!-- SOURCE_MD5:c2cc24e9dcf096d2711077053963aadf-->
