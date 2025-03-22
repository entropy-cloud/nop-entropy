# Feign Integration

In SpringCloud, the Feign RPC framework is built on REST service calls and can essentially call any third-party REST services. Therefore, NopRPC can be used to call Feign RPC alongside other REST services.

Currently, NopRPC has integrated the Nacos service registry. As long as SpringCloud uses the Nacos service registry, NopRPC and SpringCloud can communicate effectively. For other registries, it is sufficient to follow the approach in the `nop-cluster-nacos` module by providing an implementation of the `INamingService` interface.

## 1. Feign Client Calling NopRPC Server

Using Feign annotations alone is enough to create a separate Feign interface for calling the NopRPC server, while the NopRPC server requires no special handling.

```java
@FeignClient(name="rpc-demo-service")
public interface MyService {
    @PostMapping("/r/MyService__myMethod")
    ApiResponse<MyResponse> myMethod(
        @RequestBody MyRequest request,
        @QueryParam("%40selection") String selection);
}
```

* NopRPC returns result types fixed as `ApiResponse<T>`, where `ApiResponse` is defined in the `nop-api-core` module.
* NopRPC provides REST interfaces with URLs fixed as `/r/{bizObjName}__{bizMethod}` using POST, sending JSON data via the body.
* Field selection can be implemented similarly to GraphQL by passing a field selection expression like `@selection=a,b,c{f,g}` in the URL.

**Note:** Feign requires the parameter names for `QueryParam` annotations to be encoded. Therefore, `@selection` must be replaced with `%40selection`.

## 2. Calling Feign Service in NopRPC

You can simply import a regular service interface and use JAX-RS annotations to mark REST paths and parameters.

```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(
        @QueryParam("msg") String msg,
        @PathParam("id") String id);
}
```

NopRPC uses NopIoC to manage all service objects. However, all beans must be registered in `beans.xml`. To register a service object, inherit from `AbstractRpcProxyFactoryBean`, specify the service name and interface.

```xml
<bean id="testEchoService" parent="AbstractRpcProxyFactoryBean"
      ioc:type="io.nop.rpc.client.EchoService">
    <property name="serviceName" value="rpc-demo-producer"/>
</bean>
```

* `serviceName` corresponds to the service name registered on the provider side.
* `ioc:type` specifies the interface in the current project.

This approach is similar to how Feign works, where the client uses its own interface while the server uses a different one, allowing independent definitions.

## 3. Calling NopRPC in NopRPC

You can use an api.xlsx model file to define API interfaces and then generate both client and server implementations using templates in `nop/templates/api`.

```java
@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(
        @RequestBean MyRequest req);

    @BizMutation 
    MyResponse myMethod(
        @RequestBean MyRequest req);

    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(
        @RequestBean MyRequest req);
}
```

* Use `@BizModel` to specify the object name.
* Use `@BizMutation` and `@BizQuery` to annotate methods similar to GraphQL.


* The parameter is fixed as `ApiRequest<T>` type, which can also be decomposed into `RequestBean` and an additional `ICancelToken` environment.
* The return type is fixed as `ApiResponse<T>`. If you directly return `T`, the system will internally check whether the response is successful. If it's unsuccessful, the system automatically converts the error codes and messages from `ApiResponse` into an `Exception` to be thrown.
* If the method is an asynchronous call, the return type becomes `CompletionStage`, and the method name is suffixed with 'Async'. Note that client-side interfaces for asynchronous calls do not require the server to implement them as asynchronous methods; the server can use regular synchronous functions.
