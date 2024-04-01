Nop平台通过基于HttpClient实现了简单的分布式RPC机制。具体设计原理参见[rpc-design.md](rpc-design.md)

示例工程参见[nop-rpc-client-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-client-demo)
,它同时作为RPC的客户端和服务端。
采用SpringMVC实现的服务端参见[nop-rpc-server-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-server-demo)

# 一. 服务端配置

## 1.1 启用Nacos服务发现

服务端和客户端都需要引入nop-cluster-nacos和nop-rpc-cluster模块，使用nacos作为服务注册中心。配置参数如下：

|参数|缺省值|说明|
|---|---|---|
|nop.cluster.discovery.nacos.enabled|true|是否启用Nacos服务发现机制|
|nop.cluster.discovery.nacos.server-addr||nacos服务地址列表，使用逗号分隔，例如: localhost:8848|
|nop.cluster.discovery.nacos.username||用户名|
|nop.cluster.discovery.nacos.password||密码|
|nop.cluster.discovery.nacos.group|DEFAULT\_GROUP|分组|
|nop.cluster.discovery.nacos.namespace||名字空间|

## 1.2 启用自动注册

如果开启自动注册，则平台启动的时候将当前应用注册到服务注册中心。

|参数|缺省值|说明|
|---|---|---|
|nop.application.name||服务名，必须在bootstrap.yaml中配置|
|nop.cluster.registration.enabled|false|是否自动注册到注册中心|
|nop.server.addr||注册到注册中心的服务地址|
|nop.server.port||注册到注册中心的服务端口|
|nop.cluster.registration.tags||附加的服务标签|
|nop.application.version|1.0.0|注册到注册中心的服务版本号,采用语义版本号格式，必须是major.minor.patch三个部分|

在标准的sentinel.properties文件中增加sentinel内置变量配置，例如csp.sentinel.dashboard.server=localhost:
8080表示将sentinel监控信息
报送到sentinel可视化管理端。

* 如果要使用sentinel限流，需要引入nop-cluster-sentinel模块。
* Nop平台的REST服务实现依赖于spring或者quarkus框架，所以实际起作用的服务端口配置是quarkus.http.port，但是Nop平台中内部使用的是nop.server.port，
  所以需要通过参数别名机制关联一下。

```yaml
quarkus:
  http:
    host: 0.0.0.0
    port: ${nop.server.port}
```

## 1.3 实现服务

实现Nop平台中的BizModel，它会同时提供GraphQL和REST两种外部接口。也可以采用SpringMVC等普通的REST服务框架来实现。

```java

@BizModel("TestRpc")
public class TestRpcBizModel {

    /**
     * 调用Spring实现的REST服务
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

我们可以通过两种形式来调用以上服务

```
// GraphQL请求：
query{
   TestRpc__test(myArg: "333")
}

mutation{
  TestRpc__myMethod(name: "xxx",type:"bbb"){
     value1, value2
  }
}

//或者 REST请求

GET /r/TestRpc__test?myArg=333

POST /r/TestRpc__myMethod?@selection=value1,value2
{
   "name" : "xxx",
   "type" : "bbb"
}
```

详细介绍参见[graphql-java.md](../graphql/graphql-java.md)

## 1.4 启用熔断限流

引入nop-cluster-sentinel来实现熔断限流。配置参数如下：

|参数|缺省值|说明|
|---|---|---|
|nop.cluster.sentinel.enabled|true|是否启用sentinel限流机制|
|nop.cluster.sentinel.flow-rules||限流规则，通过配置中心可以动态更新|
|nop.cluster.sentinel.degrade-rules||降级规则，可以动态更新|
|nop.cluster.sentinel.sys-rules||系统限流规则, 可以动态更新|
|nop.cluster.sentinel.auth-rules||权限规则，可以动态更新|

## 二. 客户端配置

## 2.1 启用Nacos服务发现

具体设置与服务端类似，只是不需要启用自动注册

## 2.2 引入服务接口

### Nop平台发布的服务接口

如果服务端是Nop平台，可以定义如下接口

```java

@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);

    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req);
}
```

Nop服务总是使用POST方法，REST路径为`/r/{bizObjName}__{bizMethod}`，例如`/r/TestRpc__myMethod`，请求参数总是通过Request
Body传递，返回类型总是ApiResponse。

如果是异步调用，则约定方法名增加Async后缀，且返回类型为CompletionStage。

需要注意的是，与一般的RPC框架不同，客户端和服务端并不需要共享同一个API接口。如果不需要通过远程RPC方式访问后端服务，我们可以不提供客户端API接口定义。
这种做法与SpringCloud中的Feign框架的做法类似：采用任何http客户端都可以调用远程服务，客户端接口函数的定义并不需要和服务端实现函数的定义一致。
例如，通过以下的客户端接口函数都可以调用到服务端的同一个函数

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

### BizSelection支持

在客户端接口上可以增加`@BizSelection`注解，它会自动设置ApiRequest的selection段。如果指定了字段列表，以指定的列表为准，否则以函数返回类型的所有非lazy字段为准。

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

以上两个方法都会调用到后台的TestRpcBizModel对象上的myMethod方法，只是第二个方法会传入selection，对应于SubResponse，只要求返回SubResponse范围内的字段。

### 服务端实现

服务端的实现函数也可以根据需要采用以下几种不同的参数形式

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

在服务端, FieldSelection和IServiceContext都是可选参数，如果函数声明中没有对应参数，则表示忽略此参数。同时如果是异步执行，则一般约定方法名加上Async后缀，
同时返回值类型为CompletionStage。

### 一般REST服务接口

如果服务端是普通的REST服务，则可以采用JAXRS接口定义。

```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}
```

通过Path注解声明调用路径，支持QueryParam和PathParam注解引入参数

## 2.3 创建服务代理

在客户端需要引入 nop-cluster-rpc模块，为每个服务接口增加代理类配置

```xml

<bean id="testGraphQLRpc" parent="AbstractRpcProxyFactoryBean"
      ioc:type="io.nop.rpc.client.TestRpc">
    <property name="serviceName" value="rpc-demo-consumer"/>
</bean>
```

* 从AbstractRpcProxyFactoryBean继承interceptors、serverChooser等配置。
* ioc:type对应于需要创建的服务接口类
* serviceName对应于注册中心中注册的服务名

## 2.4 使用服务接口

在程序中可以通过依赖注入来使用代理接口

```
@Inject
TestRpc rpc;
```

## 2.5 服务接口说明

NopGraphQL体系下，类似于Feign服务的实现，在客户端调用时使用的接口，与服务端实现时使用的接口并不需要保持完全一样。一般情况下服务端的接口函数允许额外传入
selection和context参数。

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

* 如果参数个数较多，或者考虑到未来的可扩展性，我们会定义一个Request对象，然后增加`@RequestBean`注解，表示它对应所有前台发送的参数
* 如果参数个数只有一两个，也可以使用`@Name`注解来逐个标记传入的参数
* selection对应于GraphQL调用中的字段选择或者REST调用中的`@selection`参数，用于告诉服务端只要求返回哪些结果字段
* context对应于服务端执行上下文，它的作用类似于一个Map，当GraphQL一次性调用多个后台服务函数时，可以利用这个上下文来缓存一些共享数据。
  此外前台可以主动取消执行到一般的服务函数，前台取消调用时会触发服务端`IServiceContext.cancel`函数。服务端可以通过`context.isCancelled()`来判断客户端是否已经主动取消。
* selection和context都是可选参数，并不要求一定存在
* 服务端函数的返回类型应该就是普通的JavaBean，不需要用ApiResponse。服务端函数抛出异常时，框架会自动捕获异常，最终返回给前端ApiResponse或者GraphQLResponseBean对象。

## 2.6 客户端接口说明

客户端调用接口一般格式如下：

```java
@BizModel("MyService")
interface MyService{
   @BizMutation("myMethod")
   ApiResponse<MyResponse> api_myMethod(ApiRequest<MyRequest> request, ICancelToken cancelToken);
   
   @BizMutation("myMethod")
   ApiResponse<MyResponse> myMethod(@RequestBean MyRequest request, @QueryParam("@selection") String selection);
}
```

* 服务参数可以是ApiRequest类型，通过request的header可以传递额外信息，另外也可以通过request的selection传递字段选择信息
* 此外也可以拆分开，使用`@RequestBean`来传递请求body，而通过`@selection`参数来传递字段选择信息。
* 返回类型固定为ApiResponse。如果使用的是NopRPC客户端调用框架，则也可以直接返回MyResponse。这时如果返回的ApiResponse的status不是0，就会自动将ApiResponse中的错误信息作为NopRebuildException抛出。

如果是通过Feign接口调用，则一般配置格式如下：

```
@FeignClient
interface MyService{
  @PostMapping("/r/MyService__myMethod")
  ApiResponse<MyResponse> myMethod(@RequestBody MyRequest request, @QueryParam("%40selection") String selection);
}
```

* 因为Feign框架实现层面的问题，url参数中的特殊字符，如`@`等需要编码，因此QueryParam的参数名只能是`%40selection`，如果使用`@selection`，则无法正常传递参数。
* Feign接口的返回类型必须是`ApiResponse<T>`。调用端得到response之后，可以调用response.get()来获得实际的结果对象。此时如果ApiResponse的status不是0，则会抛出NopRebuildException异常。

## 三. 使用服务网格

如果使用k8s的服务网格，则不需要启用nacos注册中心，在客户端仍然按照上面的方式配置接口代理，同时增加如下配置

|参数|缺省值|说明|
|---|---|---|
|nop.rpc.service-mesh.enabled|false|是否使用service mesh|
|nop.rpc.service-mesh.base-url||service mesh总是访问某个固定的服务地址和端口|

nop.rpc.service-mesh.enabled设置为true之后，AbstractRpcProxyFactoryBean的实现会被自动替换为AbstractHttpRpcProxyFactoryBean
