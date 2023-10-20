Nop平台通过基于HttpClient实现了简单的分布式RPC机制。具体设计原理参见[rpc-design.md](rpc-design.md)

示例工程参见[nop-rpc-client-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-client-demo)
,它同时作为RPC的客户端和服务端。
采用SpringMVC实现的服务端参见[nop-rpc-server-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-rpc-server-demo)

# 一. 服务端配置

## 1.1 启用Nacos服务发现

服务端和客户端都需要引入nop-cluster-nacos模块，使用nacos作为服务注册中心。配置参数如下：

| 参数                                      | 缺省值           | 说明                                    |
|-----------------------------------------|---------------|---------------------------------------|
| nop.cluster.discovery.nacos.enabled     | true          | 是否启用Nacos服务发现机制                       |
| nop.cluster.discovery.nacos.server-addr |               | nacos服务地址列表，使用逗号分隔，例如: localhost:8848 |
| nop.cluster.discovery.nacos.username    |               | 用户名                                   |
| nop.cluster.discovery.nacos.password    |               | 密码                                    |
| nop.cluster.discovery.nacos.group       | DEFAULT_GROUP | 分组                                    |
| nop.cluster.discovery.nacos.namespace   |               | 名字空间                                  |

## 1.2 启用自动注册

如果开启自动注册，则平台启动的时候将当前应用注册到服务注册中心。

| 参数                               | 缺省值   | 说明                                               |
|----------------------------------|-------|--------------------------------------------------|
| nop.application.name             |       | 服务名，必须在bootstrap.yaml中配置                         |
| nop.cluster.registration.enabled | false | 是否自动注册到注册中心                                      |
| nop.server.addr                  |       | 注册到注册中心的服务地址                                     |
| nop.server.port                  |       | 注册到注册中心的服务端口                                     |
| nop.cluster.registration.tags    |       | 附加的服务标签                                          |
| nop.application.version          | 1.0.0 | 注册到注册中心的服务版本号,采用语义版本号格式，必须是major.minor.patch三个部分 |

在标准的sentinel.properties文件中增加sentinel内置变量配置，例如csp.sentinel.dashboard.server=localhost:
8080表示将sentinel监控信息
报送到sentinel可视化管理端。

## 1.3 实现服务

实现Nop平台中的BizModel，它会同时提供GraphQL和REST两种外部接口。也可以采用SpringMVC等普通的REST服务框架来实现。

````java

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
````

我们可以通过两种形式来调用以上服务

`````
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
`````

详细介绍参见[graphql-java.md](../graphql/graphql-java.md)

## 1.4 启用熔断限流

引入nop-cluster-sentinel来实现熔断限流。配置参数如下：

| 参数                                 | 缺省值  | 说明                |
|------------------------------------|------|-------------------|
| nop.cluster.sentinel.enabled       | true | 是否启用sentinel限流机制  |
| nop.cluster.sentinel.flow-rules    |      | 限流规则，通过配置中心可以动态更新 |
| nop.cluster.sentinel.degrade-rules |      | 降级规则，可以动态更新       |
| nop.cluster.sentinel.sys-rules     |      | 系统限流规则, 可以动态更新    |
| nop.cluster.sentinel.auth-rules    |      | 权限规则，可以动态更新       |

# 二. 客户端配置

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

````java
@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);

    @BizMutation 
    MyResponse myMethod(MyRequest req);

    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req);
}
````

### BizSelection支持
在客户端接口上可以增加`@BizSelection`注解，它会自动设置ApiRequest的selection段。如果指定了字段列表，以指定的列表为准，否则以函数返回类型的所有非lazy字段为准。
````
    @BizMutation 
    @BizSelection
    MyResponse myMethod(MyRequest req);
````

### 服务端实现

服务端的实现函数也可以根据需要采用以下几种不同的参数形式
````javascript

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
````

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

# 三. 使用服务网格

如果使用k8s的服务网格，则不需要启用nacos注册中心，在客户端仍然按照上面的方式配置接口代理，同时增加如下配置

| 参数                            | 缺省值   | 说明                           |
|-------------------------------|-------|------------------------------|
| nop.rpc.service-mesh.enabled  | false | 是否使用service mesh             |
| nop.rpc.service-mesh.base-url |       | service mesh总是访问某个固定的服务地址和端口 |

nop.rpc.service-mesh.enabled设置为true之后，AbstractRpcProxyFactoryBean的实现会被自动替换为AbstractHttpRpcProxyFactoryBean