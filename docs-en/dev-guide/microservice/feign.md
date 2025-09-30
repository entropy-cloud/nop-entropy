# Feign Integration

In Spring Cloud, the Feign RPC framework is essentially built on REST service calls and, in principle, can call any third-party REST service. Therefore, NopRPC can interoperate with Feign RPC in mixed call scenarios.

NopRPC currently integrates with the Nacos service registry. As long as Spring Cloud also uses Nacos for service registration, interoperability between NopRPC and Spring Cloud can be achieved. For other registries, in principle, you only need to follow the approach used in the nop-cluster-nacos module and provide an implementation of the INamingService interface.

## 1. Feign client calling a NopRPC server

Using Feign annotations, you can write a standalone Feign interface to make calls; the NopRPC server does not require any special handling.

```java

@FeignClient(name="rpc-demo-service")
public interface MyService {
    @PostMapping("/r/MyService__myMethod")
    ApiResponse<MyRespontBean> myMethod(@RequestBody MyRequest request, @QueryParam("%40selection") String selection);
}
```

* The result type returned by NopRPC is fixed to `ApiResponse<T>`. The ApiResponse class is defined in the nop-api-core module.
* The REST interface URL exposed by NopRPC is fixed to the form `/r/{bizObjName}__{bizMethod}`, uses POST, and passes JSON data via the request body.
* You can implement a GraphQL-like field selection mechanism by passing a field selection expression such as `@selection=a,b,c{f,g}` in the URL.

**Note that the Feign framework requires the QueryParam name to be URL-encoded; otherwise it will not work properly. Therefore, @selection needs to be replaced with `%40selection`.**

## 2. Calling a Feign service from NopRPC

You only need to introduce a regular service interface and use JAX-RS annotations to mark the REST path and parameter names, etc.

```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}
```

NopRPC uses NopIoC to manage all service objects, and all beans in NopIoC must be registered in beans.xml. To register a service object, simply inherit from AbstractRpcProxyFactoryBean and specify the service name and service interface.

```xml
    <bean id="testEchoService" parent="AbstractRpcProxyFactoryBean"
          ioc:type="io.nop.rpc.client.EchoService">
        <property name="serviceName" value="rpc-demo-producer"/>
    </bean>
```

* serviceName is the service name registered by the service provider.
* `ioc:type` specifies the service interface class defined in the current project. This approach is similar to Feign: the interface used on the client side has no direct relationship with the one used on the server side and can be defined independently.

## 3. Calling a NopRPC server from NopRPC

You can define the API interface using an api.xlsx model file, and then use the code generation template at /nop/templates/api to generate the client interface definitions and server implementation scaffolding.

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

* Use the `@BizModel` annotation on the interface to specify the object name, and use `@BizMutation` and `@BizQuery` to mark GraphQL method types.
* The parameter is fixed to `ApiRequest<T>`. You can also split it into a RequestBean and pass an additional ICancelToken context.
* The return type is fixed to `ApiResponse<T>`. If you return T directly, the framework will internally check whether ApiResponse succeeded; if it failed, it will automatically convert the error code and message in ApiResponse into an Exception and throw it.
* For asynchronous calls, the return type is CompletionStage, and the method name is suffixed with Async. Note that an asynchronous call on the client interface does not require the server to implement an asynchronous method; the server can be a normal synchronous function.
<!-- SOURCE_MD5:8d84848621439f31d4bfe0137a083673-->
