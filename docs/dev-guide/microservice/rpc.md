Nop平台通过基于HttpClient实现了简单的分布式RPC机制。


# 1. 启用Nacos服务发现

引入nop-cluster-nacos依赖，配置如下参数

| 参数                                      | 缺省值   | 说明                                    |
| --------------------------------------- | ----- | ------------------------------------- |
| nop.cluster.discovery.nacos.enabled     | false | 是否启用Nacos服务发现机制                       |
| nop.cluster.discovery.nacos.server-addr |       | nacos服务地址列表，使用逗号分隔，例如: localhost:8848 |

# 2. 启用自动注册

如果开启，则系统在启动的时候注册到注册中心。

| 参数                               | 缺省值   | 说明                       |
| -------------------------------- | ----- | ------------------------ |
| nop.application.name             |       | 服务名，必须在bootstrap.yaml中配置 |
| nop.cluster.registration.enabled | false | 是否自动注册到注册中心              |
| nop.server.addr                  |       | 注册到注册中心的服务地址             |
| nop.server.port                  |       | 注册到注册中心的服务端口             |
| nop.cluster.registration.tags    |       | 附加的服务标签                  |
| nop.application.version          | 1.0   | 注册到注册中心的服务版本号            |

# 3. 引入服务接口
## 3.1 Nop平台发布的服务接口
```java
@BizModel("TestRpc")
public interface TestRpc {
    @BizMutation
    ApiResponse<MyResponse> myMethod(ApiRequest<MyRequest> req);
    
    @BizMutation
    CompletionStage<ApiResponse<MyResponse>> myMethodAsync(ApiRequest<MyRequest> req);
}
```
Nop服务总是使用POST方法，REST路径为`/r/{bizObjName}__{bizMethod}`，例如`/r/TestRpc__myMethod`，请求参数总是通过Request Body传递，返回类型总是ApiResponse。

如果是异步调用，则约定方法名增加Async后缀，且返回类型为CompleationStage。

## 3.2 一般REST服务接口
```java
public interface EchoService {
    @Path("/echo/{id}")
    String echo(@QueryParam("msg") String msg, @PathParam("id") String id);
}
```
通过Path注解声明调用路径，支持QueryParam和PathParam注解引入参数

# 4. 根据服务接口创建调用代理
引入 nop-cluster-rpc依赖，为每个服务接口增加代理类配置
```xml
    <bean id="testGraphQLRpc" parent="AbstractHttpRpcProxyFactoryBean"
          ioc:type="io.nop.rpc.client.TestRpc">
        <property name="serviceName" value="rpc-demo-consumer"/>
    </bean>
```
* 从AbstractHttpRpcProxyFactoryBean继承interceptors、serverChooser等配置。
* ioc:type对应于需要创建的服务接口
* serviceName对应于注册中心中注册的服务名

在程序中可以通过依赖注入来使用代理接口
```
@Inject
TestRpc rpc;
```
