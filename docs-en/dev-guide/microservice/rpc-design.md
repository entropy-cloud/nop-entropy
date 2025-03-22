# Distributed RPC Framework in a Low-Code Platform (Approximately 3000 Lines of Code)

RPC is an essential component in the design of distributed systems. Many open-source RPC frameworks in China are influenced by Dubbo, with their core abstract concepts similar to those of Dubbo. From today's perspective, Dubbo's design has become overly complicated and lengthy. If we were to reevaluate the positioning and design of RPC frameworks based on the current technological environment, we could develop simpler and more scalable implementation solutions. This document will introduce the design philosophy and specific implementation of NopPlatform's NopRPC framework. It leverages mature technologies such as the IoC container, JSON serialization, GraphQL engine, Nacos registry, and Sentinel circuit breaker. Approximately 3000 lines of code are required to implement a distributed RPC framework with practical value.

NopRPC possesses the following characteristics:

1. **Can encapsulate NopGraphQL as an ordinary RPC interface while retaining GraphQL's response field selection capability**
2. **Can encapsulate any message interface that supports one-way data transmission as an RPC interface for response messages**
3. **Http, Socket, WebSocket, messaging queues, and file processing are all merely interface forms; through configuration, a single service can be adapted to multiple interface forms**
4. **Supports canceling ongoing RPC calls, with the ability to call the remote service's cancelMethod when cancellation occurs**
5. **Supports encapsulating startTask and checkTaskStatus as an asynchronous RPC interface**
6. **Supports gray releases. Routing of header parameters can be configured at the gateway to control subsequent call routing in the service chain**
7. **Supports broadcast calls, including master election-based calls (to specific master servers) and explicit server calls (specifying the service's address and port)**
8. **Utilizes NopTcc for distributed transaction processing**
9. **Utilizes NopTask for service-side low-code model driving development**
10. **Supports end-to-end RPC timeout control**
11. **Supports internationalization of messages**
12. **Supports error code mapping (e.g., mapping multiple internal error codes to a single external error code or mapping a single error code based on different error parameters to distinct external error codes and messages)**
13. **Supports cloud-native service mesh**
14. **Supports GraalVM native compilation**

For detailed usage documentation of NopRPC, refer to [rpc.md](rpc.md).

## Section 1: Request and Response Message Design

The core functionality of RPC is sending requests and receiving responses. Therefore, the structure of request and response messages is a key design aspect in RPC. The message structure in NopRPC's framework is defined as follows:

```java
class ApiRequest<T> {
    Map<String, Object> headers;
    T data;
    FieldSelectionBean selection;
    Map<String, Object> properties;
}

class ApiResponse<T> {
    Map<String, Object> headers;
    int status;
    String code;
    String msg;
    T data;
}
```

1. **Based on the principle of reversible computation, all core data structures in the Nop platform must adopt the (data, metadata) pairing structure for design. Metadata can store extended data beyond what is included in data. Therefore, we added a headers field to the message object. During transmission, this field can be mapped to the supported headers field of the underlying channel (e.g., http headers or Kafka headers).**
2. **GraphQL has a unique feature: it allows the caller to specify which data fields to return. This reduces the amount of data returned and optimizes the server-side data processing. The selection field added to ApiRequest extends this capability to all RPC calls.**
3. **The headers are sent alongside the data to the remote endpoint as extended data. Beyond these extended data, we sometimes need additional extended objects that exist solely within the processing flow (e.g., responseNormalizer). For such cases, we added a properties field to ApiRequest to store temporary data that should not be transmitted to the remote endpoint.**
4. **NopRPC standardizes error code and error message handling. For example, front-end AJAX requests directly use ApiResponse for input and output formats, unifying the input/output specifications of RPC and Web requests. Detailed error codes are specified in [error-code.md](../error-code.md).**

5. **To support command-line invocation of RPC services, ApiResponse uses an integer status field to indicate whether a call was successful. If successful, it returns 0; otherwise, it maps the underlying channel's status to the response's return value.**

In general, the `Request` and `Response` messages in typical RPC frameworks often contain a large amount of implementation details, which limits their use to being internal classes within the framework implementation layer. In contrast, NopRPC's design standardizes `ApiRequest` and `ApiResponse`, making them generic and ensuring that a unified message structure is used throughout all places where message transmission and information retrieval occur, achieving seamless integration with various interfaces such as RPC, Web frameworks, message queues, batch processing services, and command-line applications.


## 2. RPC Decomposition

The core interface of NopRPC is `IRpcService`.

```java
interface IRpcService{
   CompletionStage<ApiResponse<?>> callAsync(String serviceMethod,
           ApiRequest<?> request, ICancelToken cancelToken);
}

interface ICancelToken{
   boolean isCancelled();
   String getCancelReason();
   void appendOnCancel(Consumer<String> task);
}
```

Through the definition of these interfaces, we can learn the following:

1. NopRPC is an asynchronous processing framework that supports cancellation mechanisms.
2. `ApiRequest` and `ApiResponse` are POJOs (Plain Old Java Objects) without any runtime assumptions, making them independent of Web or Socket environments.
3. Some modern RPC frameworks now use ReactiveStream design, where a single RPC request may generate multiple response messages. However, in the Nop platform, the core principle of RPC is one-to-one message exchange: sending a request message results in exactly one response message being received. While using ReactiveStream for RPC implementation can increase complexity on both the server and client sides, most practical scenarios do not require multiple response messages. Additionally, the messaging system abstracted by Nop inherently provides asynchronous message sending and receiving capabilities, rendering the duplication of such functionality via RPC unnecessary.

For large files, uploading/downloading is typically encapsulated as a separate file service, allowing specialized interfaces for cloud storage optimization to be defined, without needing to replicate such functionality within the RPC framework.


## 2.1 RPC over GraphQL

In typical RPC server implementations, messages are directly mapped to specific service methods, and all business logic is contained within the message service function. However, in the Nop platform, RPC calls on the server side delegate messages to the NopGraphQL engine, which is then managed by the `GraphQLExecutor`.

For example, the following BizModel and BizLoader implementations are defined on the server side:

```java
@BizModel("MyEntity")
public class MyEntityBizModel {
    @BizQuery
    public List<MyEntity> findList(@RequestBean MyRequestBean request,
            FieldSelectionBean selection) {
        //....
    }

    @BizLoader("children")
    @GraphQLReturn(bizObjName = "MyEntity")
    public List<MyEntity> loadChildren(@ContextSource MyEntity entity) {
        //....
        return children;
    }
}
```

On the client side, a simple `MyEntity` class is defined as follows:

```java
class MyEntity {
    private String name;
    private List<MyEntity> children;

    public String getName() {
        return name;
    }

    @LazyLoad
    public List<MyEntity> getChildren() {
        return children;
    }
}
```

This design allows the `MyEntity` business object to be exposed as a unified RPC interface, eliminating the need for multiple interfaces and ensuring that complex domain models can be efficiently transmitted without performance degradation. On the client side, the following interfaces are available for consumption:

```java
@BizModel("MyEntity")
interface MyEntityService{
   @BizQuery
   CompletionStage<ApiResponse<List<MyEntity>>> findListAsync(ApiRequest<MyRequestBean> request, ICancelToken cancelToken);

   @BizQuery
   List<MyEntity> findList(@RequestBean MyRequestBean request);
}
```

Multiple Java methods can be mapped to the same backend service call and support both synchronous and asynchronous calls. By convention, asynchronous method names end with "Async" and have a CompletionStage return type. If selection and headers are not required, you can pass regular Java objects as input parameters and receive regular Java objects as returns. In case of an error, the system will automatically wrap the response's error code and message into a NopRebuildException and throw it.

Java interfaces using AOP Proxy are converted into calls to the IRpcService interface. The method calls mentioned above will be converted into:

```java
  rpcService.callAsync("MyEntity__findList", apiRequest, cancelToken)
```

This corresponds to the REST request format on the frontend as follows:

```java
POST /r/MyEntity__findList?@selection=a,b,children{a,b}
{
    json body 
}
```

The built-in @selection parameter allows for response field selection in REST requests.

The NopGraphQL engine essentially adopts a framework-agnostic design. It operates on the Request object of POJOs as a pure logic handler without any runtime dependencies. This makes it suitable for integration with various message queues. For example, when processing batch files, you can configure each line to be converted into an ApiRequest object and then dispatched to the corresponding GraphQL service.

For further details about the NopGraphQL engine, refer to [graphql-java.md](../graphql/graphql-java.md).

## 2.2 RPC over Message Queue

Many RPC frameworks introduce numerous internal interfaces that are only meaningful within the framework and cannot be used as general-purpose interfaces outside of it. The NopRPC framework emphasizes concept-level abstraction and generality by providing default implementations for [MessageRpcClient](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcClient.java) and [MessageRpcServer](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcServer.java), which can be adapted to any message queue.

The core abstract interface for message queues in the Nop platform is:

```java
interface IMessageService{
     CompletionStage<Void> sendAsync(String topic, Object message,  
                   MessageSendOptions options);

    /**
     * Sends a message to a related topic.
     *
     * @param topic The topic that the request message belongs to
     * @return The reply queue for the corresponding topic
     */
    default String getReplyTopic(String topic) {
        return "reply-" + topic;
    }

    IMessageSubscription subscribe(String topic, IMessageConsumer listener, 
            MessageSubscribeOptions options);
}
```

Based on a message queue, the RPC client implementation follows this approach:

1. Add a unique `nop-id` identifier to the `header` of `ApiRequest`, setting `nop-svc-action` to the service method identifier.
2. Register the message to the `waiting` queue before actual transmission.
3. Send the message to `topic` and listen for `reply topic`.
4. Retrieve the response or timeout from `reply topic`, taking a `CompletableFuture` object from the `waiting` queue and setting the result.

Server implementation is straightforward:

1. Listen to `topic` and handle incoming `ApiRequest` messages by calling the local `IRpcService` implementation.
2. For `rpcService`'s returned `ApiReponse`, set the `nop-rel-id` header value to the `nop-id` from `ApiRequest`.
3. Send `ApiResponse` to `reply topic`.

This message queue implementation is highly generic. For example, the `[nop-rpc-simple](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-rpc/nop-rpc-simple)` module abstracts the socket channel into `IMessageService`, implementing a simple RPC mechanism over TCP. Additionally, other message queues like Kafka or Pulsar can be used for RPC, or Redis's PUB/SUB mechanism can be utilized.

**Again, emphasize that [IMessageService](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-api-core/src/main/java/io/nop/api/core/message/IMessageService.java) is the unified abstract interface provided by the Nop platform at the application level for message service abstraction. It is not designed as a specialized interface specifically for RPC internal implementation.**

The bidirectional interaction abstraction of NopRPC can be built on top of the unidirectional message flow abstraction, which is interesting because we can also reverse it using the `IRpcService` abstraction to provide `IMessageService` interfaces' implementations. Specifically, refer to `[RpcMessageSender,java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/RpcMessageSender.java)` and `[RpcMessageSubscriber.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/RpcMessageSubscriber.java)`. This phenomenon of "you" in the mathematics field is quite common, reflecting the `IRpcService` and `IMessageService` interfaces as certain similar mathematical concepts.

Based on this general abstraction, NopRPC's implementation is both concise and generic. Many RPC frameworks' implementations are deeply tied to the underlying Netty exchange, making it difficult to apply to new exchange channels.

## 2. Load Balancing Design

The core value of distributed RPC lies in providing a customizable client-side load balancing mechanism, which allows for scaling through cluster redundancy to expand system throughput. Other aspects of distributed RPC focus primarily on running load balancing algorithms.

NopRPC's client logic is implemented as follows (pseudocode):

```java
public class NopRpcClient {
    public <T> CompletableFuture<T> call(RpcRequest request) {
        return newCompletableFuture()
            .whenComplete((r) -> processResponse(r));
    }
}
```

Here, `newCompletableFuture()` creates a new CompletableFuture object that is completed when the response is received or a timeout occurs. The `processResponse` method then sets the result of this CompletableFuture based on the response.

**Note**: The above pseudocode is a simplified representation and does not reflect the actual implementation details. Please refer to the official implementation for precise logic.

The design of message queues in NopRPC allows for flexible configurations, enabling integration with various RPC mechanisms while maintaining a consistent API. This flexibility ensures that NopRPC can be adapted to different use cases and underlying infrastructure, such as Kafka or Pulsar for messaging or Redis's PUB/SUB mechanism.


```javascript
// Use service discovery to get all available service instances
List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);

List<ServiceInstance> filtered = new ArrayList<>(instances);
// Filter out any service instances that do not meet the matching criteria
for (IRequestServiceInstanceFilter filter : filters) {
    // First, apply strict filtering rules, such as selecting servers within the same zone
    filter.filter(filtered, request, false);
}

// If no matching service instances are found, try applying lenient filtering rules
if (filtered.isEmpty()) {
    filtered = new ArrayList<>(instances);
    for (IRequestServiceInstanceFilter filter : filters) {
        // Apply lenient filtering rules to select servers that meet at least one criteria
        filter.filter(filtered, request, true);
    }
}

// Use load balancing to randomly select the next available service instance
ServiceInstance selected = loadBalance.choose(filtered, request);

// Get the RPC client for the selected service instance and invoke the method asynchronously
IRpcService rpcService = rpcClientInstanceProvider.getRpcClientInstance(selected);
CompletionStage<ApiResponse> response = rpcService.callAsync(
    serviceMethod, 
    request, 
    cancelToken
);
```

In essence, this process first applies strict routing filtering logic to maintain only matching service entries and then applies load balancing to select the next available service instance.


## Failure Handling and Retries

If `nop.rpc.cluster-client-retry-count` is configured (with a default value of 2), when connecting to the backend service fails, the system will automatically remove the failed connection from the list of candidates and re-run the load balancing algorithm to select a new instance. This process repeats up to the specified retry count.

> Currently, only connection failures trigger retries (by throwing `NopConnectException`), other failure types do not result in retries.

The corresponding pseudocode is as follows:

```javascript
// Handle service connection failures and retries
Exception error = null;
for (int i = 0; i <= retryCount; i++) {
    ServiceInstance instance = loadBalance.choose(instances, request);
    try {
        return getRpcClient(instance, request).call(
            serviceMethod,
            request,
            cancelToken
        );
    } catch (Exception e) {
        error = e;

        if (!isAllowRetry(e)) {
            break;
        }

        if (instances.size() > 1) {
            // Remove the failed connection and try again
            instances.remove(instance);
        }
    }
}

if (error != null) {
    throw NopException.adapt(error);
}
```


## Gray Release


The gradual roll-out can be considered as a routing logic, where requests that meet certain conditions will only be routed to specific service instances. In NopRPC, we can leverage [TagServiceInstanceFilter](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/filter/TagServiceInstanceFilter.java) and [RouteServiceInstanceFilter](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/filter/RouteServiceInstanceFilter.java) to implement gradual roll-out.

* If the `ApiRequest` contains the `nop-tags` header, then only service instances with the specified tags will be selected. For example, if the header is set as `nop-tags=a,b`, the service instance must have both tags.
* The `nop-svc-route` header can directly specify the service version. For instance, `nop-svc-route=ServiceA:1.0.0,ServiceB:^2.0.3` means that ServiceA should use version 1.0.0 and ServiceB should use a version greater than 2.0.3. The format for `nop-svc-route` is `serviceName:versionDefinition,serviceName:versionDefinition`.


## Cancellation and Polling

The design of NopRPC does not utilize the `CompletableFuture` object's cancel method because, in practice, passing the `cancelToken` parameter is more straightforward and efficient than using a future that has a cancel function.

When executing cancellation:
- General RPC frameworks typically only disrupt the current request connection and do not actively send cancellation messages to the server.
- In NopRPC, however, we can configure it to actively call the server's cancel method by setting up `@RpcMethod(cancelMethod="Sys__cancel")`.

Example of service interface with cancellation support:
```java
@BizModel("MyEntity")
interface MyEntityService {
    @RpcMethod(cancelMethod="Sys__cancel")
    CompletionStage<ApiResponse<MyResponseBean>> myAction(
        ApiRequest<MyResponseBean> request,
        ICancelToken cancelToken
    );
}
```

The `@RpcMethod(cancelMethod="Sys__cancel")` annotation indicates that when cancellation is requested, the server will call its `Sys.cancel()` method. If you need to perform specific business logic during cancellation, you can implement the `cancel` method in your business model (`MyEntityBizModel`) and annotate it with `@RpcMethod(cancelMethod="cancel")`.

If the `cancelMethod` does not specify an object name, it will call the current business object's method.

For detailed cancellation logic, refer to [CancellableRpcClient](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/CancellableRpcClient.java).

In addition to `cancelMethod`, the `RpcMethod` annotation also supports configuring a polling method.

Example of service interface with polling support:
```java
interface MyEntityService {
    @RpcMethod(pollingMethod="checkTaskStatus")
    CompletionStage<ApiResponse<TaskResultBean>> startTask(
        ApiRequest<StartTaskRequestBean> request
    );
}
```

If `pollingMethod` is configured, after an RPC method is called, the server will not immediately return but will continuously call the corresponding polling method of the remote service until a result is received.



## Four. Context Propagation

In a microservices architecture, a single business operation may involve multiple related RPC calls, and thus an automatic context propagation mechanism is necessary to propagate shared information from upstream services to downstream services. In the specific implementation of NopRPC, the [ContextBinder](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ContextBinder.java) is responsible for copying certain header information from the APIRequest to the asynchronous context object (IContext), while the [ClientContextRpcServiceInterceptor](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/interceptors/ClientContextRpcServiceInterceptor.java) is responsible for propagating information from the IContext to the downstream APIRequest's headers.

By default, the following headers are automatically propagated across systems:

| Name            | Description                   |
|-----------------|------------------------------|
| nop-svc-tags    | Tags used during gray release for filtering          |
| nop-svc-route   | Routing information for gray release        |
| nop-tenant     | Tenant ID                        |
| nop-user-id    | Current logged-in user ID           |
| nop-locale     | Internationalization language used in responses|
| nop-timezone   | Time zone field corresponding to the time type in messages|
| nop-txn-id     | Transaction ID for distributed transactions  |
| nop-txn-branch-id | Transaction branch ID for distributed transactions    |
| nop-trace      | Trace ID assigned by the entry service, used to link related RPC calls        |
| nop-client-addr | Client's real IP and port           |
| nop-timeout     | Timeout parameter for end-to-end timeout control|


## End-to-End Timeout Control

The nop-timeout header in NopRPC indicates the timeout duration for an RPC call. When this header is propagated to the next RPC call, the current time is subtracted from it. For example, if service A receives a nop-timeout=1000ms and processes it with 200ms elapsed, it will then call the next downstream RPC service with nop-timeout=800ms.

Inside the service, all time-consuming operations (e.g., database queries) check IContext.getCallExpireTime() against the current time. If the timeout has expired, the operation is terminated immediately to avoid continued processing. This reduces the system's load during busy times by preventing excessive retries.

For instance, if downstream service B is still executing while service A believes it has exceeded the timeout, service A may initiate a retry. If service B hasn't detected its own timeout and continues executing, service A's retry will result in both services being executed simultaneously, significantly increasing system load.


## Five. Model-Driven Development

In the Nop platform, we provide an API model that can be defined in Excel to expose which external services are available, what request/response pairs exist for these services, etc. Specific examples are provided in [nop-wf.api.xlsx](https://gitee.com/canonical-entropy/nop-entropy/raw/master/nop-wf/model/nop-wf.api.xlsx).

![API Model](api-model.png)

Additionally, on the RPC implementation side, we can directly generate calls to either [TaskFlow](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef) or [Workflow](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef) models using a visual development approach.


## Six. About Dubbo's Design

The Dubbo framework is built on a large number of auxiliary components, which are considered outdated from today's perspective.

The SPI plugin loading mechanism is essentially an incomplete Bean loading and assembly engine. It can be directly replaced by using an IoC container.

2. Serialization Mechanism  
In REST scenarios, a generic JSON serialization can be used for implementation. In binary cases, existing protobuf packages can be utilized directly.

3. Message Transmission Channel  
The JDK's built-in Httpclient can be used directly, or the IMessageService message queue abstraction can be utilized instead.

4. Proxy Interface  
Essentially designed to enable bi-directional conversion between strong Java objects and universal IRpcService interfaces, providing an IRpcMessageTransformer interface is sufficient to isolate various transformation strategies.

5. Service Registration and Discovery  
Direct use of specialized service registration and discovery mechanisms like Nacos is feasible without encapsulating Zookeeper.

The internal interface design within the Dubbo framework also appears problematic, for example, the load balancing algorithm interface.

```java
interface LoadBalance {
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url,
        Invocation invocation) throws RpcException;
}
```

This design presents the following issues:

- **Invoker** causes unnecessary dependencies between the Loadbalance algorithm and the RPC executor.
- **Invocation** introduces unnecessary dependencies with the AOP wrapping process.
- **URL** is a custom structure introduced by Dubbo (different from JDK's URL), offering no advantages over standard JSON.

In contrast, the NopRPC framework defines its load balancing interface as follows:

```java
interface ILoadBalance<T,R> {
    T choose(List<T> candidates, R request);
}
```

To retrieve weight configurations or other candidate-related information, an Adapter adapter can be utilized:

```java
public interface ILoadBalanceAdapter<T> {
    int getWeight(T candidate);

    int getActiveCount(T candidate);
}
```

Through this abstraction, the load balancing algorithm becomes a pure logical function, fully decoupled from the RPC execution logic. It can thus be applied to any scenario requiring load balancing, not limited to RPC calls.


## Summary
NopRPC is designed from first principles, reevaluating the RPC concept completely and creating Yet Another PRC framework. Its design is concise, intuitive, and easily extendable, forming an organic part of the Nop platform.

Based on reversible computation theory, the low-code platform NopPlatform has been open-sourced:

- **Gitee**: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- **GitHub**: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- **Development Example**: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- **Reversible Computing Theory and Nop Platform Introduction (Bilibili)**: [视频ID：BV1u84y1w7kX/](https://www.bilibili.com/video/BV1u84y1w7kX/)

