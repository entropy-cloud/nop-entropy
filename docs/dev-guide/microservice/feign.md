# Feign集成

springcloud中的Feign RPC框架本质上是架构在REST服务调用的基础上，原则上可以调用任何第三方的REST服务。因此NopRPC可以和Feign
RPC混合在一起调用。

NopRPC目前集成了Nacos服务注册中心，因此只要SpringCloud也使用Nacos服务注册，就可以实现NopRPC和SpringCloud的互联互通。对于其他注册中心，
原则上只要仿照nop-cluster-nacos模块中的做法，提供一个INamingService接口的实现即可。

## 1. Feign客户端调用 NopRPC服务端

使用Feign的注解，单独编写一个Feign接口即可调用，NopRPC服务端不需要做任何处理。

```java

@FeignClient(name="rpc-demo-service")
public interface MyService {
    @PostMapping("/r/MyService__myMethod")
    ApiResponse<MyRespontBean> myMethod(@RequestBody MyRequest request, @QueryParam("%40selection") String selection);
}
```

* NopRPC返回的结果类型固定为`ApiResponse<T>`类型, ApiResponse类定义在nop-api-core模块中。
* NopRPC对外提供的REST接口url固定为`/r/{bizObjName}__{bizMethod}`形式，采用POST方式提交，通过body传递JSON数据
* 可以通过在url中传递`@selection=a,b,c{f,g}`这种形式的字段选择表达式来实现类似GraphQL的字段选择机制

**需要注意的是，Feign框架要求QueryParam的参数名必须是编码后的参数名，否则无法正常工作，所以@selection需要被替换为`%40selection`**

## 2. 在NopRPC中调用Feign服务

只需要引入普通的服务接口，并使用jaxrs注解来标记REST路径和参数名等

```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}
```

NopRPC使用NopIoC管理所有的服务对象，而NopIoC中所有的bean都需要在beans.xml中注册。注册服务对象只需要从AbstractRpcProxyFactoryBean继承，并指定服务名和服务接口即可

```xml
    <bean id="testEchoService" parent="AbstractRpcProxyFactoryBean"
          ioc:type="io.nop.rpc.client.EchoService">
        <property name="serviceName" value="rpc-demo-producer"/>
    </bean>
```

* serviceName为服务提供端注册的服务名
* `ioc:type`指定在本工程中指定的服务接口类。这种做法与Feign框架类型，客户端使用的接口与服务端使用的接口并没有直接关系，可以独立定义。

## 3. 在NopRPC中调用NopRPC服务端

可以使用api.xlsx模型文件来定义API接口，然后通过代码生成模板/nop/templates/api来生成客户端接口定义以及服务端实现框架。

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

* 在接口上通过`@BizModel`注解来指定对象名，通过`@BizMutation`和`@BizQuery`等注解来标记GraphQL方法类型
* 参数固定为`ApiRequest<T>`类型，也可以拆开写 RequestBean，并且可以传递额外的ICancelToken环境
* 返回类型固定为`ApiResponse<T>`，如果直接返回T，则内部会判断ApiResponse是否成功，如果失败，自动把ApiResponse中的错误码和错误消息转换为Exception抛出。
* 如果是异步调用，则返回类型为CompletionStage，方法名后增加Async后缀。注意，客户端接口的异步调用并不要求服务端采用异步方法实现，服务端可以是普通的同步函数。
