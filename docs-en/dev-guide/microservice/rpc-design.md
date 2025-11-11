
# Distributed RPC Framework in a Low-Code Platform (~3,000 LOC)

RPC is an indispensable part of distributed system design. There are many open-source RPC frameworks in China, most of which are influenced by the Dubbo framework, with core abstractions similar to Dubbo. From today’s perspective, Dubbo’s design has become overly complicated and verbose. If we re-examine the positioning and design of an RPC framework based on the current technological landscape, we can produce a simpler and more extensible solution. This article introduces the design philosophy and concrete implementation of the NopRPC framework in the Nop platform. It fully leverages mature technical infrastructures such as IoC containers, JSON serialization, GraphQL engines, Nacos as a registry, and Sentinel as a circuit breaker and rate limiter. With roughly 3,000 lines of code, it implements a practical distributed RPC framework. NopRPC features the following:

1. Boldly wrap NopGraphQL services as regular strongly-typed RPC interfaces while retaining GraphQL’s ability to select response fields

2. Wrap any messaging interface that supports one-way send/receive into an RPC interface that awaits a response

3. HTTP, Socket, WebSocket, message queues, batch files, etc., are just interface forms; through configuration, the same service implementation can be adapted to multiple interface forms

4. Support canceling in-flight RPC calls, invoking a cancelMethod on the remote service upon cancellation

5. Support encapsulating paired startTask and checkTaskStatus calls into a single asynchronous RPC interface

6. Support gray release. You can set route-selection headers at the gateway to directly control service routing in subsequent call chains.

7. Support broadcast invocations, leader-only invocations (invoke only the elected primary server), and direct-to-instance invocations (specify the callee’s address and port)

8. Use the NopTcc engine to implement distributed transactions

9. Use the NopTask engine to enable low-code, model-driven development on the server side

10. Support end-to-end RPC timeout control

11. Support internationalized, multi-language messages

12. Support error code mapping (e.g., unify multiple internal error codes into a single external code, or map the same code to different external codes and messages depending on error parameters)

13. Support cloud-native service mesh

14. Support GraalVM native image compilation

For detailed usage of NopRPC, see [rpc.md](rpc.md)

## I. Request and Response Message Design

The core of RPC is sending request messages and receiving response messages, so the structure of request and response messages is a key design choice. The message structures in the NopRPC framework are defined as follows:

```java
class ApiRequest<T>{
    Map<String,Object> headers;
    T data;
    FieldSelectionBean selection;
    Map<String,Object> properties;
}

class ApiResponse<T>{
    Map<String,Object> headers;
    int status;
    String code;
    String msg;
    T data;
}
```

1. According to the principle of Reversible Computation, all core data structures in the Nop platform adopt a paired structure of (data, metadata), where metadata can store data’s extensions. Therefore, we add a headers field to the message object, which can be mapped to the headers supported by the underlying channel during transmission. For example, when using HTTP, headers correspond to HTTP headers; when using Kafka, they correspond to Kafka message headers.

2. A notable feature of GraphQL is that it allows clients to select which fields to return, which reduces response payloads and can optimize server-side processing. The selection field added to ApiRequest extends this capability to all RPC calls.

3. headers are extension data sent along with data to the remote end. Beyond these, we often need extension objects that only exist during the current processing flow, such as responseNormalizer. Therefore, ApiRequest defines a properties extension set that does not support JSON serialization, suitable for storing temporary data that does not need to be sent to the remote end.

4. NopRPC provides a unified, standardized approach to error codes and messages. Front-end Ajax requests directly use the ApiResponse format, unifying the I/O spec across RPC and Web requests. For error code specifications, see [error-code.md](../error-code.md)

5. To support invoking RPC services via the command line, ApiResponse uses an integer status field to indicate success. A successful call returns 0. When invoked as a command-line program, status can be directly mapped to the process exit code.

In typical RPC frameworks, Request and Response often contain many implementation details, limiting them to internal framework classes. By contrast, NopRPC generalizes ApiRequest and ApiResponse, adopting a unified message structure wherever messages are transmitted and returned. This enables seamless integration across RPC, Web frameworks, message queues, batch services, command-line apps, and more.

## II. Deconstructing RPC

The core interface of NopRPC is IRpcService

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

From the interfaces above, we can infer:

1. NopRPC is an asynchronous processing framework that supports cancellation

2. ApiRequest and ApiResponse are plain POJOs; the framework imposes no runtime assumptions, so it can be used outside of Web or Socket environments.

> Some RPC frameworks adopt a ReactiveStream design where a single RPC request can produce multiple response messages and support downloading large attachments via RPC. In the Nop platform, however, RPC is positioned strictly as one-to-one exchanges between applications: each request message yields exactly one response message. Implementing RPC in a ReactiveStream style complicates client and server management, and in practice the need for multiple return messages is rare. Moreover, messaging systems inherently provide streaming send/receive capabilities; exposing similar functionality again via RPC is redundant. As for large file upload/download, these are usually encapsulated into dedicated file services with interfaces optimized for cloud storage; it’s unnecessary to bundle such functionality into an RPC framework.

## 2.1 RPC over GraphQL

Typical RPC servers directly map message types to service methods and perform all business logic in those functions. In the Nop platform, RPC calls dispatch messages to the NopGraphQL engine on the server, where GraphQLExecutor coordinates multiple DataLoaders. For example, the following BizModel is implemented on the server:

```java
@BizModel("MyEntity")
public class MyEntityBizModel{
    @BizQuery
    public List<MyEntity> findList(@RequestBean MyRequestBean request,             
             FieldSelectionBean selection){
        //....
    }

    @BizLoader("children")
    @GraphQLReturn(bizObjName = "MyEntity")
    public List<MyEntity> loadChildren(@ContextSource MyEntity entity) {
        //...
        return children;
    }
}

class MyEntity{
   private String name;
   private List<MyEntity> children;

   public String getName(){
      return name;
   }

   @LazyLoad
   public List<MyEntity> getChildren(){
      return children;
   }
}
```

In the code above, we define a business object MyEntity on the server and expose a query service function MyEntity__findList that returns a list of MyEntity objects. The children property on MyEntity is lazy-loaded and will not be returned unless explicitly requested. Loading the children property triggers the loadChildren function on MyEntityBizModel. With this design, we can expose a large, complex domain model as a unified RPC service without worrying about performance impact from excessive irrelevant data.

On the client, we can use the following interface:

```java
@BizModel("MyEntity")
interface MyEntityService{
   @BizQuery
   CompletionStage<ApiResponse<List<MyEntity>>> findListAsync(ApiRequest<MyRequestBean> request, ICancelToken cancelToken);

   @BizQuery
   List<MyEntity> findList(@RequestBean MyRequestBean request);
}
```

Multiple Java methods can map to the same backend service call, supporting both synchronous and asynchronous invocation. By convention, asynchronous method names end with Async and return CompletionStage. If selection and headers are unnecessary, we can use regular Java objects as input parameters and return regular Java objects. On error, the error code and message in ApiResponse are wrapped and thrown as NopRebuildException.

The Java interface is proxied via AOP to calls to IRpcService; the invocation above is transformed into:

```
  rpcService.callAsync("MyEntity__findList", apiRequest, cancelToken)
```

The corresponding front-end REST request format is:

```
POST /r/MyEntity__findList?@selection=a,b,children{a,b}
{
    json body 
}
```

Using the built-in @selection parameter adds response field selection capability to REST requests.

The NopGraphQL engine is essentially framework-agnostic: it acts as a pure logical function over a POJO Request object with no specific runtime dependencies. Therefore, after wrapping as RPC interfaces, it can be adapted to various I/O channels. For example, it can serve as a batch file processor by configuring each line read from a batch file to construct an ApiRequest and invoking the corresponding GraphQL service.

For more on the NopGraphQL engine, see [graphql-java.md](../graphql/graphql-java.md)

## 2.2 RPC over Message Queue

Many RPC frameworks introduce numerous internal interfaces meaningful only within that framework and unusable as general-purpose interfaces elsewhere. NopRPC emphasizes conceptual abstraction and generality, providing default implementations such as [MessageRpcClient](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcClient.java) and [MessageRpcServer](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcServer.java), enabling an RPC mechanism atop any message queue.
In the Nop platform, message queues are positioned as one-way message senders. The core abstraction is:

```java
interface IMessageService{
     CompletionStage<Void> sendAsync(String topic, Object message,  
                   MessageSendOptions options);

    /**
     * Reply messages are sent to a related topic
     *
     * @param topic The topic of the request message
     * @return The queue corresponding to the reply message
     */
    default String getReplyTopic(String topic) {
        return "reply-" + topic;
    }

    IMessageSubscription subscribe(String topic, IMessageConsumer listener, 
            MessageSubscribeOptions options);
}
```

The approach to implementing an RPC client on a message queue:

1. Add a unique nop-id in ApiRequest headers and set nop-svc-action to the service method identifier
2. Register the message in a waiting queue before actual sending
3. Send the message to the topic and listen on the reply topic
4. Upon receiving a response on the reply topic or timeout, fetch the CompletableFuture from the waiting queue and complete it with the result.

Server-side implementation is straightforward:

1. Listen on the topic and invoke the local IRpcService implementation for each received ApiRequest
2. For the ApiReponse returned by rpcService, set the nop-rel-id header to the nop-id from ApiRequest
3. Send the ApiResponse to the reply topic.

This message-queue-based implementation is highly general. For instance, the [nop-rpc-simple](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-rpc/nop-rpc-simple) module abstracts a Socket channel as an IMessageService, implementing simple RPC over TCP. We can also implement RPC over Kafka or Pulsar, or via Redis PUB/SUB.

Emphasizing again, [IMessageService](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/IMessageService.java) is the Nop platform’s unified application-layer abstraction for messaging services; it is not a bespoke interface designed only for the internals of RPC.

NopRPC’s two-way interaction abstraction can be built on top of a one-way message stream abstraction. Interestingly, the reverse is also possible: provide an IMessageService implementation atop the IRpcService abstraction. See [RpcMessageSender,java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/RpcMessageSender.java) and [RpcMessageSubscriber.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/RpcMessageSubscriber.java). This mutual embedding is common in mathematical reasoning, reflecting that IRpcService and IMessageService are general abstractions akin to mathematical concepts.

With such general abstractions, NopRPC’s implementation is concise and universal, whereas many RPC frameworks are tightly coupled to the underlying Netty transport and cannot be easily applied to new channels.

## II. Load Balancing Design

The core value of distributed RPC lies in providing a customizable client-side load-balancing mechanism to leverage cluster redundancy for throughput scaling. The other parts of distributed RPC primarily prepare for running load-balancing algorithms.

Pseudocode of the NopRPC client execution:

```javascript
// Use service discovery to obtain all available service instances
List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);

List<ServiceInstance> filtered = new ArrayList<>(instances);
// Filter out service instances that do not match the criteria
for(IRequestServiceInstanceFilter filter: filters){
    // First filter with stricter rules, e.g., choose only servers in the same zone
    filter.filter(filtered, request, false);
}

// If no instances match, try again with more relaxed rules
if(filtered.isEmpty()){
    filtered = new ArrayList<>(instances);
    for(IRequestServiceInstanceFilter filter: filters){
       filter.filter(filtered, request, true);
    }
}

// Use a load-balancing algorithm to choose one from the candidates
ServiceInstance selected = loadBalance.choose(filtered,request);
IRpcService rpcService = rpcClientInstanceProvider.getRpcClientInstance(selected);
CompletionStage<ApiResponse> response = rpcService.callAsync(
     serviceMethod, request, cancelToken);
```

Essentially, routing filters run first to retain only matching routes, followed by a load-balancing algorithm to make the final choice.

## Retry on Failure

If nop.rpc.cluster-client-retry-count is configured (default 2), when connecting to the server fails, the client automatically removes the failed server from the candidate list and reruns the load-balancing algorithm to pick a new instance and reconnect.

> Currently only connection failures (throwing NopConnectException) trigger retries; other failures do not.

Pseudocode:

```javascript
 Exception error = null;
 for (int i = 0; i <= retryCount; i++) {
     ServiceInstance instance = loadBalance.choose(instances, request);
     try {
         return getRpcClient(instance, request).call(serviceMethod, request, cancelToken);
     } catch (Exception e) {
         error = e;

         if (!isAllowRetry(e)) {
             break;
         }

         if (instances.size() > 1) {
            // Remove the failed instance and retry
            instances.remove(instance);
         }
     }
 }
 throw NopException.adapt(error);
```

## Gray Release

Gray release can be viewed as routing logic: requests that meet certain conditions are routed only to designated service instances. In NopRPC, we can leverage [TagServiceInstanceFilter](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/filter/TagServiceInstanceFilter.java) and [RouteServiceInstanceFilter](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/chooser/filter/RouteServiceInstanceFilter.java) to implement gray release.

* If ApiRequest includes the nop-tags header, only ServiceInstances with the specified tags are selected. For example, nop-tags=a,b requires the ServiceInstance to have both tags.
* The nop-svc-route header can specify versions directly, e.g., nop-svc-route=ServiceA:1.0.0,ServiceB:^2.0.3 means use version 1.0.0 for ServiceA and versions >=2.0.3 for ServiceB. The format is service-name:NPM-version,service-name:NPM-version, and version rules follow NPM’s semantic versioning.

## III. Cancellation and Status Polling

NopRPC does not rely on CompletableFuture.cancel. In practice, passing a cancelToken as an argument is easier to handle than returning a Future with a cancel function, and it is more amenable to performance optimizations.

When canceling, typical RPC frameworks only terminate the current request connection without proactively sending a cancel message to the server. In NopRPC, you can configure the invocation of a cancel method on the server upon cancellation:

```java
@BizModel("MyEntity")
interface MyEntityService{
    @RpcMethod(cancelMethod="Sys__cancel")
    CompletionStage<ApiResponse<MyResponseBean>> myAction(ApiRequest<MyRequestBean> request, ICancelToken cancelToken);
}
```

The @RpcMethod(cancelMethod="Sys__cancel") annotation indicates that a call to the server’s Sys.cancel method will be made when cancellation occurs. It is a system default cancel method that invokes cancel on the server’s cancelToken. If you need to run business-specific logic on cancel, implement a cancel method on the server’s MyEntityBizModel and use @RpcMethod(cancelMethod="cancel").

> If the cancelMethod does not include an object name, it calls a method on the current business object.

For the precise cancelMethod invocation logic, see [CancellableRpcClient.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/CancellableRpcClient.java)

In addition to cancelMethod, the RpcMethod annotation supports configuring a pollingMethod.

```java
interface MyEntityService{
   @RpcMethod(pollingMethod="checkTaskStatus")
   CompletionStage<ApiResponse<TaskResultBean>> startTask(
          ApiRequest<StartTaskRequestBean> request);
}
```

If pollingMethod is configured, the RPC method won’t return immediately; instead, it repeatedly invokes the remote service specified by pollingMethod until a result is returned.

For the exact pollingMethod handling, see [PollingRpcClient.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/PollingRpcClient.java)

## IV. Context Propagation

In a microservice architecture, a single business operation may produce multiple related RPC calls. An automatic context propagation mechanism is needed to pass shared information from upstream to downstream services. In NopRPC, [ContextBinder](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ContextBinder.java) copies selected ApiRequest headers into the asynchronous context object IContext, while [ClientContextRpcServiceInterceptor](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/interceptors/ClientContextRpcServiceInterceptor.java) propagates information from IContext into the headers of downstream ApiRequests.

By default, the following headers propagate automatically across systems:

|Name|Description|
|---|---|
|nop-svc-tags|Filter tags used for gray release|
|nop-svc-route|Routing info used for gray release|
|nop-tenant|Tenant ID|
|nop-user-id|Current logged-in user|
|nop-locale|Locale for internationalized response messages|
|nop-timezone|Time zone for date/time fields in responses|
|nop-txn-id|Transaction ID for distributed transactions|
|nop-txn-branch-id|Branch transaction ID for distributed transactions|
|nop-trace|traceId assigned by the entry service to correlate related RPC calls|
|nop-client-addr|Client’s real IP and port|
|nop-timeout|Timeout parameter needed for end-to-end timeout control|

## End-to-End Timeout Control

The nop-timeout header in NopRPC represents the timeout for the entire RPC call. As it propagates to the next RPC call, the time already consumed is subtracted. For example, Service A receives nop-timeout=1000; after 200ms of processing, it calls a downstream RPC with nop-timeout=800.
Within a service, all time-consuming operations (e.g., database queries) check whether IContext.getCallExpireTime() has passed. If so, they immediately stop. This reduces system pressure when the system is busy and clients may be retrying frequently.

For example, if Service B is still processing while Service A has timed out and retries, and if Service B does not realize it has timed out and keeps running the unfinished task, two business operations may run concurrently, multiplying system load.

## V. Model-Driven Development

The Nop platform provides an API model allowing the definition, in Excel, of which services a system exposes and their request/response messages. See [nop-wf.api.xlsx](https://gitee.com/canonical-entropy/nop-entropy/raw/master/nop-wf/model/nop-wf.api.xlsx) for an example.

![](api-model.png)

At the RPC implementation layer, we can also directly generate calls to [TaskFlow](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef) or [Workflow](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef) models and implement business functionality via visual orchestration.

## VI. On Dubbo’s Design

A large portion of Dubbo’s code implements auxiliary functions that, from today’s perspective, are outdated.

1. SPI plugin loading. Essentially a less capable bean loading and wiring engine; an IoC container can replace it directly

2. Serialization. In REST scenarios, generic JSON serialization suffices; in binary scenarios, existing libraries such as protostuff can be used

3. Transport channels. You can use the JDK’s built-in HttpClient, or directly use the IMessageService abstraction for message queues

4. Proxy interfaces. Essentially to convert between strongly-typed Java interfaces and a generic IRpcService; providing a single IRpcMessageTransformer is enough to isolate different conversion strategies

5. Service registry and discovery. Use specialized mechanisms like Nacos directly; no need to wrap Zookeeper again.

Dubbo’s internal interface designs are also less than ideal. For example, the load-balancing interface:

```java
interface LoadBalance {
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, 
        Invocation invocation) throws RpcException;
}
```

Issues with this design:

* Invoker couples LoadBalance unnecessarily to the RPC executor
* Invocation introduces unnecessary coupling to the AOP wrapping process
* URL is a custom Dubbo type (different from JDK’s URL) with no advantage over plain JSON objects.

In NopRPC, the load-balancing interface is defined as:

```java
interface ILoadBalance<T,R>{
     T choose(List<T> candidates, R request);
}
```

If you need to read weight settings etc. from candidate objects, use an Adapter:

```
public interface ILoadBalanceAdapter<T> {
    int getWeight(T candidate);

    int getActiveCount(T candidate);
}
```

With this abstraction, load-balancing algorithms become pure logic functions, fully decoupled from RPC execution, and applicable wherever load balancing is needed, not just in RPC scenarios.

## Summary

NopRPC rethinks RPC from first principles and is a completely redesigned “Yet Another RPC” framework. Its design is simple, intuitive, and easy to extend—an organic part of the Nop platform.

The low-code platform NopPlatform, designed based on Reversible Computation, is open source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development tutorial: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Introduction and Q&A on the Principles of Reversible Computation and the Nop Platform_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

<!-- SOURCE_MD5:e8d3840c98a5264b61d7464b0040d6b4-->
