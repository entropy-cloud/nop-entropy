The Nop platform implements a simple distributed RPC mechanism based on HttpClient. For the design principles, see [rpc-design.md](rpc-design.md)

For sample projects, see [nop-rpc-client-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-client-demo), which acts as both the RPC client and server.
For a server implemented with SpringMVC, see [nop-rpc-server-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-server-demo)

# 1. Server Configuration

## 1.1 Enable Nacos Service Discovery

Both the server and client need to include the nop-cluster-nacos and nop-rpc-cluster modules, using Nacos as the service registry. Configuration parameters are as follows:

|Parameter|Default|Description|
|---|---|---|
|nop.cluster.discovery.nacos.enabled|true|Whether to enable the Nacos service discovery mechanism|
|nop.cluster.discovery.nacos.server-addr||Nacos server address list, separated by commas, e.g.: localhost:8848|
|nop.cluster.discovery.nacos.username||Username|
|nop.cluster.discovery.nacos.password||Password|
|nop.cluster.discovery.nacos.group|DEFAULT\_GROUP|Group|
|nop.cluster.discovery.nacos.namespace||Namespace|

## 1.2 Enable Auto Registration

If auto registration is enabled, the platform will register the current application with the service registry on startup.

|Parameter|Default|Description|
|---|---|---|
|nop.application.name||Service name, must be configured in bootstrap.yaml|
|nop.cluster.registration.enabled|false|Whether to auto-register with the registry|
|nop.server.addr||Service address to register with the registry|
|nop.server.port||Service port to register with the registry|
|nop.cluster.registration.tags||Additional service tags|
|nop.application.version|1.0.0|Service version registered with the registry; follows semantic versioning and must have the three parts major.minor.patch|

Add built-in Sentinel variables to the standard sentinel.properties file. For example, csp.sentinel.dashboard.server=localhost:8080 means that Sentinel monitoring information will be reported to the Sentinel dashboard.

* To use Sentinel rate limiting, include the nop-cluster-sentinel module.
* The Nop platform’s REST services are implemented on top of Spring or Quarkus frameworks, so the effective server port is configured via quarkus.http.port. Internally, Nop uses nop.server.port, so you need to associate them via the parameter alias mechanism.

```yaml
quarkus:
  http:
    host: 0.0.0.0
    port: ${nop.server.port}
```

## 1.3 Implement the Service

Implement a BizModel in the Nop platform; it will expose both GraphQL and REST interfaces. You can also use standard REST frameworks such as SpringMVC.

```java

@BizModel("TestRpc")
public class TestRpcBizModel {

    /**
     * Call a REST service implemented by Spring
     */
    @BizQuery
    public String test(@Name("myArg") String myArg) {
        return echoService.echo(myArg, "aa");
    }

    @BizMutation
    public MyResponse myMethod(@RequestBean MyRequest req, FieldSelectionBean selection) {
        MyResponse res = new MyResponse();
        if (selection.hasField("value1")) {
            res.setValue1(value1);
        }
        //res.setValue2(value2);
        return res;
    }
}    
```

We can invoke the above service in two ways:

```
// GraphQL request:
query{
   TestRpc__test(myArg: "333")
}

mutation{
  TestRpc__myMethod(name: "xxx",type:"bbb"){
     value1, value2
  }
}

// Or REST request

GET /r/TestRpc__test?myArg=333

POST /r/TestRpc__myMethod?@selection=value1,value2
{
   "name" : "xxx",
   "type" : "bbb"
}
```

For details, see [graphql-java.md](../graphql/graphql-java.md)

## 1.4 Enable Circuit Breaking and Rate Limiting

Include nop-cluster-sentinel to enable circuit breaking and rate limiting. Configuration parameters are as follows:

|Parameter|Default|Description|
|---|---|---|
|nop.cluster.sentinel.enabled|true|Whether to enable the Sentinel rate limiting mechanism|
|nop.cluster.sentinel.flow-rules||Flow control rules; can be dynamically updated via the configuration center|
|nop.cluster.sentinel.degrade-rules||Degrade rules; can be dynamically updated|
|nop.cluster.sentinel.sys-rules||System rate limiting rules; can be dynamically updated|
|nop.cluster.sentinel.auth-rules||Authorization rules; can be dynamically updated|

## 2. Client Configuration

## 2.1 Enable Nacos Service Discovery

Settings are similar to the server, except you don’t need to enable auto registration.

## 2.2 Introduce Service Interfaces

### Service Interfaces Published by the Nop Platform

If the server is the Nop platform, you can define an interface as follows:

```java

@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);

    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req);
}
```

Nop services always use the POST method. The REST path is `/r/{bizObjName}__{bizMethod}`, e.g., `/r/TestRpc__myMethod`. Request parameters are always passed via the Request Body, and the return type is always ApiResponse.

For asynchronous calls, the convention is to append the Async suffix to the method name, with the return type being CompletionStage.

Note that unlike typical RPC frameworks, the client and server do not need to share the same API interface. If you don’t need to access backend services via remote RPC, you can omit client-side API interface definitions. This approach is similar to Feign in SpringCloud: any HTTP client can invoke remote services, and the client interface method definitions don’t need to match the server implementation functions. For example, the following client interface methods can all invoke the same function on the server:

```java
@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);

    @BizMutation 
    MyResponse myMethod(@RequestBean MyRequest req);

    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req);
}
```

### BizSelection Support

You can add the `@BizSelection` annotation to the client interface; it will automatically set the selection section of ApiRequest. If a field list is specified, that list is used; otherwise, all non-lazy fields of the function’s return type are used.

```
@BizModel("TestRpc")
public interface TestRpc{
    @BizMutation 
    MyResponse myMethod(@RequestBean MyRequest req);
    
    @BizMutation("myMethod")
    @BizSelection
    SubResponse myMethodForSelected(@RequestBean MyRequest req);
}
```

Both methods above invoke the myMethod on the backend TestRpcBizModel object. The second method passes a selection corresponding to SubResponse, requesting only the fields within the scope of SubResponse.

### Server-side Implementation

On the server side, implementation functions can accept different parameter forms as needed:

```javascript

    @BizMutation
    public CompletionStage<MyResponse> myMethodAsync(@RequestBean MyRequest req, FieldSelectionBean selection, IServiceContext context) {
        ...
        return res;
    }
    或者

    @BizMutation
    public MyResponse myMethod(@RequestBean MyRequest req, FieldSelectionBean selection) {
        ...
        return res;
    }
```

On the server, FieldSelection and IServiceContext are optional parameters. If they are not declared in the function signature, they are ignored. Also, for asynchronous execution, the convention is to add the Async suffix to the method name, with the return type being CompletionStage.

### General REST Service Interfaces

If the server is a general REST service, you can use a JAX-RS interface definition.

```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}
```

Declare the invocation path with the @Path annotation; parameters are supported via @QueryParam and @PathParam.

## 2.3 Create Service Proxies

On the client side, include the nop-cluster-rpc module and add proxy class configuration for each service interface:

```xml

<bean id="testGraphQLRpc" parent="AbstractRpcProxyFactoryBean"
      ioc:type="io.nop.rpc.client.TestRpc">
    <property name="serviceName" value="rpc-demo-consumer"/>
</bean>
```

* Inherit configurations such as interceptors and serverChooser from AbstractRpcProxyFactoryBean.
* ioc:type corresponds to the service interface class to be created.
* serviceName corresponds to the service name registered in the registry.

## 2.4 Use the Service Interface

In your code, obtain the proxy interface via dependency injection:

```
@Inject
TestRpc rpc;
```

## 2.5 Notes on Service Interfaces

In the NopGraphQL system, similar to Feign-style services, the client-side interfaces used for invocation do not need to exactly match the interfaces used on the server side. Generally, server-side interface functions may accept additional selection and context parameters.

```java
@BizModel("MyService")
class MyServiceBizModel{
    @BizMutation
    public MyResponse myMethod(@RequestBean MyRequest request, FieldSelectionBean selection, IServiceContext context){
        //...
    }
    
    @BizMutation
    MyResponse myMethod2(@Name("name") String name, @Name("value")String value){
        
    }
}
```

* If there are many parameters, or to allow for future extensibility, define a Request object and annotate it with `@RequestBean`, indicating it corresponds to all parameters sent from the frontend.
* If there are only one or two parameters, you can use the `@Name` annotation to mark each parameter individually.
* selection corresponds to the field selection in a GraphQL call, or the `@selection` parameter in REST calls, used to tell the server which result fields are required.
* context corresponds to the server-side execution context. It works like a Map. When GraphQL invokes multiple backend service functions in one request, you can use this context to cache shared data. The frontend can also proactively cancel the execution of general service functions; when the frontend cancels, it triggers the server’s `IServiceContext.cancel` function. The server can check `context.isCancelled()` to see if the client has proactively canceled.
* selection and context are both optional parameters and are not required.
* The return type of server-side functions should be a regular JavaBean; there is no need to use ApiResponse. When a server-side function throws an exception, the framework will capture it and ultimately return an ApiResponse or GraphQLResponseBean to the frontend.

## 2.6 Notes on Client Interfaces

The general format for client invocation interfaces is as follows:

```java
@BizModel("MyService")
interface MyService{
   @BizMutation("myMethod")
   ApiResponse<MyResponse> api_myMethod(ApiRequest<MyRequest> request, ICancelToken cancelToken);
   
   @BizMutation("myMethod")
   ApiResponse<MyResponse> myMethod(@RequestBean MyRequest request, @QueryParam("@selection") String selection);
}
```

* Service parameters can be of type ApiRequest; additional information can be passed via the request header, and field selection via the request’s selection.
* Alternatively, you can split them: pass the request body via `@RequestBean` and the field selection via the `@selection` parameter.
* The return type is fixed as ApiResponse. If you use the NopRPC client invocation framework, you can also return MyResponse directly. In this case, if the status of the returned ApiResponse is not 0, the error information in ApiResponse will automatically be thrown as a NopRebuildException.

If calling via a Feign interface, the typical configuration is as follows:

```
@FeignClient
interface MyService{
  @PostMapping("/r/MyService__myMethod")
  ApiResponse<MyResponse> myMethod(@RequestBody MyRequest request, @QueryParam("%40selection") String selection);
}
```

* Due to implementation details of the Feign framework, special characters in URL parameters (such as `@`) must be encoded, so the QueryParam parameter name must be `%40selection`. If you use `@selection`, the parameter will not be passed correctly.
* A Feign interface must return `ApiResponse<T>`. After receiving the response, the caller can invoke response.get() to obtain the actual result object. If the ApiResponse status is not 0, a NopRebuildException will be thrown.

## 3. Using a Service Mesh

If you use a Kubernetes service mesh, you don’t need to enable the Nacos registry. On the client, configure the interface proxy as above and add the following:

|Parameter|Default|Description|
|---|---|---|
|nop.rpc.service-mesh.enabled|false|Whether to use a service mesh|
|nop.rpc.service-mesh.base-url||A service mesh always accesses a fixed service address and port|

After setting nop.rpc.service-mesh.enabled to true, the implementation of AbstractRpcProxyFactoryBean will be automatically replaced with AbstractHttpRpcProxyFactoryBean
<!-- SOURCE_MD5:e0a2003c2ea752f636ba2451060a5225-->
