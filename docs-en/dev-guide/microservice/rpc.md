The Nop platform implements a simple distributed RPC mechanism using HttpClient.

Example projects can be found in [nop-rpc-client-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-client-demo), which acts as both an RPC client and service. The server-side implementation using SpringMVC is available in [nop-rpc-server-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-server-demo).

# Server Configuration

## 1.1 Enable Nacos Service Discovery

Both the server and client need to import the `nop-cluster-nacos` and `nop-rpc-cluster` modules, using Nacos as the service registry. The following configuration parameters are provided:

| Parameter | Default Value | Description |
|-----------|--------------|-------------|
| nop.cluster.discovery.nacos.enabled | true | Whether to enable the Nacos service discovery mechanism |
| nop.cluster.discovery.nacos.server-addr | - | List of Nacos service addresses, comma-separated, e.g.,: localhost:8848 |
| nop.cluster.discovery.nacos.username | - | Username for Nacos |
| nop.cluster.discovery.nacos.password | - | Password for Nacos |
| nop.cluster.discovery.nacos.group | DEFAULT_GROUP | Group name |
| nop.cluster.discovery.nacos.namespace | - | Namespaces |

## 1.2 Enable Automatic Registration

If automatic registration is enabled, the application will be automatically registered in the service registry when the platform starts.

| Parameter | Default Value | Description |
|-----------|--------------|-------------|
| nop.application.name | - | Service name, which must be configured in bootstrap.yaml |
| nop.cluster.registration.enabled | false | Whether to automatically register with the service registry |
| nop.server.addr | - | Address for registration in the service registry |
| nop.server.port | - | Port for registration in the service registry |
| nop.cluster.registration.tags | - | Additional tags for registration |
| nop.application.version | 1.0.0 | Version of the service to be registered, using semantic versioning (major.minor.patch) |

The standard `sentinel.properties` file should include additional Sentinel variables, such as `csp.sentinel.dashboard.server=localhost:8080`, which sends monitoring information to the Sentinel visualization dashboard.

- If Sentinel rate limiting is used, the `nop-cluster-sentinel` module must be imported.
- Nop platform's REST services rely on either Spring or Quarkus, so the effective port configuration is `quarkus.http.port`. However, internally, the `nop.server.port` parameter is used, which should be linked via parameter alias.

```yaml
quarkus:
  http:
    host: 0.0.0.0
    port: ${nop.server.port}
```

## 1.3 Implementing Services

Implement `BizModel` in the Nop platform, which provides both GraphQL and REST external interfaces. Regular REST services can be implemented using SpringMVC.

```java

@BizModel("TestRpc")
public class TestRpcBizModel {

    /**
     * Call Spring implementation of REST service
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

The above service can be called in two forms:

```java
// GraphQL request:
query{
  TestRpc__test(myArg: "333")
}

mutation{
  TestRpc__myMethod(name: "xxx", type:"bbb") {
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

For detailed information, please refer to [graphql-java.md](../graphql/graphql-java.md).


## 1.4 Enable Circuit Breaker

Introduce nop-cluster-sentinel for circuit breaking. Configuration parameters as follows:

| Parameter           | Default Value | Explanation                  |
|--------------------|--------------|-----------------------------|
| nop.cluster.sentinel.enabled | true        | Whether to enable sentinel circuit breaker |
| nop.cluster.sentinel.flow-rules   ||          | Flow rules, which can be dynamically updated via config center |
| nop.cluster.sentinel.degrade-rules    ||          | Degradation rules, which can be dynamically updated |
| nop.cluster.sentinel.sys-rules     ||          | System flow rules, which can be dynamically updated |
| nop.cluster.sentinel.auth-rules    ||          | Authorization rules, which can be dynamically updated |


## 2. Client Configuration


## 2.1 Enable Nacos Service Discovery

Similar to server configuration, but auto-registration is not enabled.


## 2.2 Introduce Service Interfaces


### Nop Platform Published Services Interface

If the server is Nop platform, define the following interface:

```java

@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);

    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req);
}
```

Nop services always use POST method, with REST path `/r/{bizObjName}__{bizMethod}`, such as `/r/TestRpc__myMethod`. Request parameters are always passed via Request Body, and return type is always ApiResponse.

If it's an asynchronous call, the method name is suffixed with "Async", and the return type is CompletionStage.

Note: Unlike general RPC frameworks, client and server do not need to share the same API interface. If you don't want to access backend services via remote RPC, you can omit the client API interface definition. This approach is similar to SpringCloud's Feign framework: any HTTP client can call remote services, and the client interface does not need to match the server interface.

For example, the following client interfaces can call the same server method:

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

On the client interface, you can add the `@BizSelection` annotation. It will automatically set the `selection` property of `ApiRequest`. If a field list is specified, it will be used; otherwise, it will default to all non-lazy fields.

```java
@BizModel("TestRpc")
public interface TestRpc{
    @BizMutation 
    MyResponse myMethod(@RequestBean MyRequest req);
    
    @BizMutation("myMethod")
    @BizSelection
    SubResponse myMethodForSelected(@RequestBean MyRequest req);
}
```

Both methods will call the `myMethod` method on the backend `TestRpcBizModel` object. The second method will pass in the `selection`, corresponding to `SubResponse`, which only returns fields within the `SubResponse` range.


### Service Implementation

The implementation functions on the server can be adjusted based on the following parameter forms:

```javascript
@BizMutation
public CompletionStage<MyResponse> myMethodAsync(@RequestBean MyRequest req, FieldSelectionBean selection, IServiceContext context) {
    ...
    return res;
}
```

or

```javascript
@BizMutation
public MyResponse myMethod(@RequestBean MyRequest req, FieldSelectionBean selection) {
    ...
    return res;
}
```

On the server side, `FieldSelection` and `IServiceContext` are optional parameters. If the function definition does not include corresponding parameters, they will be ignored. Additionally, if the method is asynchronous, it is generally agreed that the method name will have an "Async" suffix, and the return type will be `CompletionStage`.


### General REST Service Interface

If the service on the server side is a standard REST service, it can use JAXRS interface definitions.

```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}
```

Using `@Path` declares the call path, while `@QueryParam` and `@PathParam` handle query and path parameters respectively.


## Creating Service Proxies

On the client side, you need to import the `nop-cluster-rpc` module and configure proxy classes for each service interface.

```xml
<bean id="testGraphQLRpc" parent="AbstractRpcProxyFactoryBean"
      ioc:type="io.nop.rpc.client.TestRpc">
    <property name="ServiceProviderName" value="rpc-demo-consumer"/>
</bean>
```

* Inherits `interceptors`, `serverChooser` etc. from `AbstractRpcProxyFactoryBean`.
* `ioc:type` corresponds to the service interface class to be created.
* `serviceName` corresponds to the service name registered in the registry.


## Using Service Interfaces

In the program, you can use the proxy interface through dependency injection.

```java
@Inject
TestRpc rpc;
```


## Service Interface Explanation

Under the NopGraphQL framework, similar to the implementation of Feign service, the interfaces used by the client and those used on the server do not need to be identical. In general, server-side interfaces allow additional parameters such as selection and context.

```java
@BizModel("MyService")
class MyServiceBizModel {
    @BizMutation
    public MyResponse myMethod(@RequestBean MyRequest request, FieldSelectionBean selection, IServiceContext context) {
        // ...
    }

    @BizMutation
    public MyResponse myMethod2(@Name("name") String name, @Name("value") String value) {
        // ...
    }
}
```

* If the number of parameters is large or if future expandability is considered, we will define a Request object and add the @RequestBean annotation to indicate it corresponds to all sent parameters.
* If only one or two parameters are needed, individual annotations like @Name can be used to mark each parameter.
* The selection corresponds to either GraphQL field selection or REST's @selection parameter, which tells the server to return specific result fields.
* The context corresponds to the server-side execution context, similar to a Map. When multiple backend services are called in a single GraphQL query, this context can cache shared data. Additionally, the client can actively cancel executions by triggering the server's IServiceContext.cancel method. The server can check if the client has canceled using context.isCancelled().
* Both selection and context are optional parameters and do not need to be present.
* The return type of server functions should be a regular JavaBean, not an ApiResponse. If the server throws an exception, the framework automatically captures it and returns it as either a GraphQLResponse or RESTResponse object.

## 2.6 Client Interface Explanation

The general format for client interfaces is as follows:

```java
@BizModel("MyService")
interface MyService {
    @BizMutation("myMethod")
    ApiResponse<MyResponse> api_myMethod(ApiResponse<MyRequest> request, ICancelToken cancelToken);

    @BizMutation("myMethod")
    ApiResponse<MyResponse> myMethod(@RequestBean MyRequest request, @QueryParam("@selection") String selection);
}
```

* The parameters can be either an ApiRequest type. Additional information can be sent via the request's headers or using the selection parameter.
* Additionally, parameters can be split into two parts: use @RequestBean to send the request body and use @selection to send field selection information.
* The return type is fixed as ApiResponse. If using the NopRPC client framework, it can directly return MyResponse. If the returned ApiResponse's status is not 0, it will throw a NopRebuildException with the error information from ApiResponse.
* If using Feign interfaces, the general configuration format is:

```java
@FeignClient
interface MyService {
    @PostMapping("/r/MyService__myMethod")
    ApiResponse<MyResponse> myMethod(@RequestBody MyRequest request, @QueryParam("%40selection") String selection);
}
```

* Because of the limitations in Feign's implementation, parameters like '@' in URLs need to be encoded as "%40" when using QueryParams. Using '@selection' would prevent proper parameter transmission.


The return type of the Feign interface must be `ApiResponse<T>`. After obtaining the response on the calling side, you can call `response.get()` to obtain the actual result object. At this point, if the status of `ApiResponse` is not 0, it will throw a `NopRebuildException`.


## Using Service Mesh

If using Kubernetes's service mesh, then nacos registry need not be enabled. The client still needs to be configured in the same way as before for interface proxying, with additional configurations added.

| Parameter | Default Value | Explanation |
|----------|--------------|-------------|
| nop.rpc.service-mesh.enabled | false | Whether to use service mesh |
| nop.rpc.service-mesh.base-url |  | Service mesh always accesses a fixed service address and port |

After setting `nop.rpc.service-mesh.enabled` to true, the implementation of `AbstractRpcProxyFactoryBean` will be automatically replaced with `AbstractHttpRpcProxyFactoryBean`.

